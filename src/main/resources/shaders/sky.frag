#version 450

layout(binding = 0) uniform SkyUBO {
  mat4 viewInverse;
  mat4 projInverse;
  vec4 sunDir;      // xyz = direction toward sun, w = disc angular radius
  vec4 cameraUnder; // w = underwater
} ubo;

layout(binding = 1) uniform sampler2D sunSampler;

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

void main() {
  vec4 target = ubo.projInverse * vec4(inNdc, 1.0, 1.0);
  vec3 dir = normalize(mat3(ubo.viewInverse) * normalize(target.xyz));

  vec3 color = skyColor(dir);

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
