#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inUvMin;
layout(location = 2) in float inShade;
layout(location = 3) in vec2 inLocalUv;

layout(binding = 0) uniform UniformBufferObject {
  mat4 mvp;
  vec4 cameraPosUnderwater; // xyz = camera, w = underwater
  vec4 fogParams;           // x = fogStart, y = fogEnd, z = tileSpanU, w = tileSpanV
  vec4 fogColor;            // rgb = fog, a = cloudTimeSeconds
} ubo;

layout(location = 0) out vec2 fragUvMin;
layout(location = 1) out float fragShade;
layout(location = 2) out vec2 fragLocalUv;
layout(location = 3) out vec3 fragWorldPos;

void main() {
  gl_Position = ubo.mvp * vec4(inPosition, 1.0);
  fragUvMin = inUvMin;
  fragShade = inShade;
  fragLocalUv = inLocalUv;
  fragWorldPos = inPosition;
}
