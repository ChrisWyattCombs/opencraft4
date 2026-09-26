#version 460
#extension GL_EXT_ray_tracing : require
#extension GL_EXT_buffer_reference2 : require
#extension GL_EXT_scalar_block_layout : require
#extension GL_EXT_shader_explicit_arithmetic_types_int64 : require

struct HitPayload {
  vec4 albedo; // rgb + faceShade
  vec4 hit;    // xyz hitPos, w = 1 opaque
  vec4 normal;
};

layout(location = 0) rayPayloadInEXT HitPayload hit;
hitAttributeEXT vec2 attribs;

layout(binding = 2) uniform UniformBufferObject {
  mat4 viewInverse;
  mat4 projInverse;
  vec4 cameraPosUnderwater;
  vec4 fogParams;
  vec4 fogColor;
  vec4 sunDir;
} ubo;

layout(binding = 3) uniform sampler2D texSampler;

struct MeshInfo {
  uint64_t vertexAddress;
  uint64_t indexAddress;
};

layout(binding = 4, scalar) readonly buffer MeshInfos {
  MeshInfo infos[];
} meshInfos;

struct Vertex {
  float px, py, pz;
  float u0, v0;
  float shade;
  float lu, lv;
};

layout(buffer_reference, scalar) buffer Vertices { Vertex v[]; };
layout(buffer_reference, scalar) buffer Indices { uint i[]; };

void main() {
  MeshInfo info = meshInfos.infos[gl_InstanceCustomIndexEXT];
  Indices indices = Indices(info.indexAddress);
  Vertices vertices = Vertices(info.vertexAddress);

  uint i0 = indices.i[gl_PrimitiveID * 3 + 0];
  uint i1 = indices.i[gl_PrimitiveID * 3 + 1];
  uint i2 = indices.i[gl_PrimitiveID * 3 + 2];

  Vertex v0 = vertices.v[i0];
  Vertex v1 = vertices.v[i1];
  Vertex v2 = vertices.v[i2];

  const vec3 bary = vec3(1.0 - attribs.x - attribs.y, attribs.x, attribs.y);
  float faceShade = v0.shade * bary.x + v1.shade * bary.y + v2.shade * bary.z;
  vec2 uvMin =
      vec2(v0.u0, v0.v0) * bary.x + vec2(v1.u0, v1.v0) * bary.y + vec2(v2.u0, v2.v0) * bary.z;
  vec2 localUv =
      vec2(v0.lu, v0.lv) * bary.x + vec2(v1.lu, v1.lv) * bary.y + vec2(v2.lu, v2.lv) * bary.z;
  vec2 uv = uvMin + vec2(ubo.fogParams.z, ubo.fogParams.w) * fract(localUv);
  vec4 sampled = texture(texSampler, uv);

  if (sampled.a < 0.05) {
    hit.albedo = vec4(ubo.fogColor.rgb, 0.0);
    hit.hit = vec4(0.0, 0.0, 0.0, 0.0);
    hit.normal = vec4(0.0);
    return;
  }

  vec3 hitPos = gl_WorldRayOriginEXT + gl_WorldRayDirectionEXT * gl_HitTEXT;
  hit.albedo = vec4(sampled.rgb, clamp(faceShade, 0.2, 1.25));
  hit.hit = vec4(hitPos, 1.0);
  hit.normal = vec4(0.0);
}
