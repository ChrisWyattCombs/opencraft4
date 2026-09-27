#version 450

layout(binding = 0) uniform SkyUBO {
  mat4 viewInverse;
  mat4 projInverse;
  vec4 sunDir;      // xyz = direction toward sun, w = disc angular radius
  vec4 cameraUnder; // x = cloudTimeSeconds, w = underwater
} ubo;

layout(binding = 1) uniform sampler2D sunSampler;
layout(binding = 2) uniform sampler2D cloudSampler;

layout(location = 0) in vec2 inNdc;
layout(location = 0) out vec4 outColor;

vec3 skyColor(vec3 dir) {
  float h = clamp(dir.y * 0.5 + 0.5, 0.0, 1.0);
  vec3 zenith = vec3(0.28, 0.52, 0.92);
  vec3 horizon = vec3(0.62, 0.78, 0.95);
  vec3 groundGlow = vec3(0.75, 0.82, 0.88);
  vec3 col = mix(horizon, zenith, pow(h, 1.15));
  if (dir.y < 0.05) {
    col = mix(groundGlow, col, clamp(dir.y / 0.05, 0.0, 1.0));
  }
  // Soft sun bloom in the sky gradient.
  float toward = max(dot(normalize(dir), normalize(ubo.sunDir.xyz)), 0.0);
  col += vec3(1.0, 0.85, 0.55) * pow(toward, 32.0) * 0.35;
  col += vec3(1.0, 0.7, 0.3) * pow(toward, 8.0) * 0.12;
  return col;
}

/**
 * Fixed-altitude cloud boxes: wide/long prisms, fixed height.
 * {@code maxT} clips so clouds appear when looking down onto the world.
 */
vec3 applyClouds(vec3 color, vec3 camPos, vec3 dir, float time, float maxT) {
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
  vec4 target = ubo.projInverse * vec4(inNdc, 1.0, 1.0);
  vec3 dir = normalize(mat3(ubo.viewInverse) * normalize(target.xyz));
  vec3 camPos = ubo.viewInverse[3].xyz;

  vec3 color = skyColor(dir);
  color = applyClouds(color, camPos, dir, ubo.cameraUnder.x, 1.0e6);

  // Voxel sun disc billboarded in direction space via angular map.
  vec3 sun = normalize(ubo.sunDir.xyz);
  float ang = acos(clamp(dot(dir, sun), -1.0, 1.0));
  float radius = max(ubo.sunDir.w, 0.02);
  if (ang < radius * 2.2) {
    // Map angle to sun texture UV (pixelated disc).
    vec3 east = normalize(cross(vec3(0.0, 1.0, 0.0), sun));
    if (dot(east, east) < 0.01) {
      east = vec3(1.0, 0.0, 0.0);
    }
    vec3 north = normalize(cross(sun, east));
    vec3 offset = dir - sun * dot(dir, sun);
    vec2 uv = vec2(dot(offset, east), dot(offset, north)) / (radius * 1.15);
    uv = uv * 0.5 + 0.5;
    if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0) {
      vec4 sunSample = texture(sunSampler, uv);
      color = mix(color, sunSample.rgb, sunSample.a);
    }
  }

  if (ubo.cameraUnder.w > 0.5) {
    color = mix(color, vec3(0.02, 0.18, 0.35), 0.65);
  }

  outColor = vec4(color, 1.0);
}
