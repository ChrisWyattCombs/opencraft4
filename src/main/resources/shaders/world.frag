#version 450

layout(binding = 0) uniform UniformBufferObject {
  mat4 mvp;
  vec4 cameraPosUnderwater; // xyz = camera, w = underwater
  vec4 fogParams;           // x = fogStart, y = fogEnd, z = tileSpanU, w = tileSpanV
  vec4 fogColor;            // rgb = fog, a = cloudTimeSeconds
} ubo;

layout(binding = 1) uniform sampler2D texSampler;
layout(binding = 2) uniform sampler2D cloudSampler;

layout(location = 0) in vec2 fragUvMin;
layout(location = 1) in float fragShade;
layout(location = 2) in vec2 fragLocalUv;
layout(location = 3) in vec3 fragWorldPos;

layout(location = 0) out vec4 outColor;

/**
 * Fixed-altitude cloud boxes along the camera ray (same as RT) so looking down
 * from above still composites clouds over terrain.
 */
vec3 applyClouds(vec3 color, vec3 camPos, vec3 dir, float maxT) {
  const float cloudBottom = 128.0;
  const float cloudTop = 136.0;
  const float invCell = 1.0 / 768.0;
  const int steps = 14;

  vec3 d = normalize(dir);
  float tBot = (cloudBottom - camPos.y) / d.y;
  float tTop = (cloudTop - camPos.y) / d.y;
  if (abs(d.y) < 1e-4) {
    if (camPos.y < cloudBottom || camPos.y > cloudTop) {
      return color;
    }
    tBot = 0.0;
    tTop = min(maxT, 2000.0);
  }
  float tNear = min(tBot, tTop);
  float tFar = max(tBot, tTop);
  if (tFar < 0.0) {
    return color;
  }
  tNear = max(tNear, 0.0);
  tFar = min(tFar, max(maxT, 0.0));
  tFar = min(tFar, 4000.0);
  if (tNear >= tFar) {
    return color;
  }

  float dt = (tFar - tNear) / float(steps);
  float transm = 1.0;
  float time = ubo.fogColor.a;
  vec3 cloudRgb = vec3(0.96, 0.97, 0.99);
  for (int i = 0; i < steps; i++) {
    float t = tNear + (float(i) + 0.5) * dt;
    vec3 p = camPos + d * t;
    vec2 uv = p.xz * invCell + time * vec2(0.0022, 0.0007);
    float dens = step(0.2, texture(cloudSampler, uv).a);
    float yNorm = (p.y - cloudBottom) / (cloudTop - cloudBottom);
    float face =
        mix(0.78, 1.0, smoothstep(0.0, 0.15, yNorm) * (1.0 - smoothstep(0.85, 1.0, yNorm)));
    float absorb = dens * 0.09 * face;
    color = color * (1.0 - absorb * transm) + cloudRgb * (absorb * transm);
    transm *= (1.0 - absorb * 0.8);
    if (transm < 0.1) {
      break;
    }
  }
  return color;
}

void main() {
  vec2 uv = fragUvMin + vec2(ubo.fogParams.z, ubo.fogParams.w) * fract(fragLocalUv);
  vec4 sampled = texture(texSampler, uv);
  if (sampled.a < 0.05) {
    discard;
  }
  float lit = clamp(fragShade, 0.16, 1.35);
  vec3 color = sampled.rgb * lit;
  color = max(color, vec3(0.03));

  if (ubo.cameraPosUnderwater.w > 0.5) {
    color = mix(color, vec3(0.04, 0.22, 0.42), 0.52);
    color *= vec3(0.55, 0.8, 1.05);
  }

  float dist = length(fragWorldPos - ubo.cameraPosUnderwater.xyz);
  float fogStart = ubo.fogParams.x;
  float fogEnd = max(ubo.fogParams.y, fogStart + 1.0);
  float fog = clamp((dist - fogStart) / (fogEnd - fogStart), 0.0, 1.0);
  // Smoothstep for a softer horizon fade into the sky/fog color.
  fog = fog * fog * (3.0 - 2.0 * fog);
  color = mix(color, ubo.fogColor.rgb, fog);

  vec3 camPos = ubo.cameraPosUnderwater.xyz;
  vec3 viewDir = fragWorldPos - camPos;
  float viewLen = length(viewDir);
  if (viewLen > 1e-3) {
    color = applyClouds(color, camPos, viewDir / viewLen, viewLen);
  }

  outColor = vec4(color, sampled.a);
}
