#version 450

layout(binding = 0) uniform UniformBufferObject {
  mat4 mvp;
  vec4 cameraPosUnderwater; // xyz = camera, w = underwater
  vec4 fogParams;           // x = fogStart, y = fogEnd, z = tileSpanU, w = tileSpanV
  vec4 fogColor;            // rgb = fog
} ubo;

layout(binding = 1) uniform sampler2D texSampler;

layout(location = 0) in vec2 fragUvMin;
layout(location = 1) in float fragShade;
layout(location = 2) in vec2 fragLocalUv;
layout(location = 3) in vec3 fragWorldPos;

layout(location = 0) out vec4 outColor;

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

  outColor = vec4(color, sampled.a);
}
