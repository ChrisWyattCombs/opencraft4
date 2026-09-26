#version 450

layout(location = 0) out vec2 outUv;

// Fullscreen triangle; UV in [-1,1] clip space mapped later in frag via inverse VP.
void main() {
  vec2 positions[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
  outUv = positions[gl_VertexIndex];
  gl_Position = vec4(positions[gl_VertexIndex], 1.0, 1.0);
}
