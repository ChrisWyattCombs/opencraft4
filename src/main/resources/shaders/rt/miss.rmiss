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
  vec4 fogColor; // rgb fog, a = cloudTimeSeconds
  vec4 sunDir;
} ubo;

layout(binding = 5) uniform sampler2D cloudSampler;

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
    // Sky disc only — keep in sync with raygen.rgen evalSkyEx.
    float sunDot = max(dot(d, sun), 0.0);
    col += vec3(1.0, 0.90, 0.70) * pow(sunDot, 32.0) * 0.55;
    col += vec3(1.0, 0.96, 0.82) * pow(sunDot, 256.0) * 3.2;
    col += vec3(1.0, 0.99, 0.92) * pow(sunDot, 2048.0) * 10.0;
  }
  // Clouds are composited in raygen along the camera ray (supports looking down).
  return col;
}

void main() {
  hit.albedo = vec4(evalSky(gl_WorldRayDirectionEXT), 1.0);
  hit.hit = vec4(0.0, 0.0, 0.0, 0.0);
  hit.normal = vec4(0.0);
}
