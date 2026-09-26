#version 460
#extension GL_EXT_ray_tracing : require

struct HitPayload {
  vec4 albedo;
  vec4 hit;
  vec4 normal;
};

layout(location = 0) rayPayloadInEXT HitPayload hit;

layout(binding = 2) uniform UniformBufferObject {
  mat4 viewInverse;
  mat4 projInverse;
  vec4 cameraPosUnderwater;
  vec4 fogParams;
  vec4 fogColor;
  vec4 sunDir;
} ubo;

vec3 evalSky(vec3 dir) {
  vec3 d = normalize(dir);
  float h = clamp(d.y * 0.5 + 0.5, 0.0, 1.0);
  vec3 col = mix(vec3(0.62, 0.78, 0.95), vec3(0.28, 0.52, 0.92), pow(h, 1.15));
  if (d.y < 0.05) {
    col = mix(vec3(0.75, 0.82, 0.88), col, clamp(d.y / 0.05, 0.0, 1.0));
  }
  vec3 sun = ubo.sunDir.xyz;
  float sunLen2 = dot(sun, sun);
  if (sunLen2 > 1e-6) {
    sun *= inversesqrt(sunLen2);
    col += vec3(1.0, 0.88, 0.65) * pow(max(dot(d, sun), 0.0), 12.0) * 0.18;
  }
  if (ubo.cameraPosUnderwater.w > 0.5) {
    col = mix(col, vec3(0.02, 0.18, 0.35), 0.65);
  }
  return col;
}

void main() {
  hit.albedo = vec4(evalSky(gl_WorldRayDirectionEXT), 1.0);
  hit.hit = vec4(0.0, 0.0, 0.0, 0.0);
  hit.normal = vec4(0.0);
}
