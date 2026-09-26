# RTX primary path, distant LOD, and related additions

This documents the major features landed in the Vulkan/RTX and world-rendering workstream.

## Hardware ray tracing (F8)

- Optional Vulkan ray-tracing primary path when the GPU supports
  `VK_KHR_acceleration_structure` + `VK_KHR_ray_tracing_pipeline`.
- Toggle with **F8** (raster fallback remains the default / unsupported path).
- Iterative **raygen** path tracer (Minecraft RTX–style): hit shaders return surface
  data only; bounce/shadow/mirror rays are fired from raygen with pipeline recursion
  depth **1** (avoids nested `TraceRay` in closest-hit).
- Acceleration structures: per-mesh BLAS (`MeshBlas`), scene TLAS (`SceneTlas`),
  mesh-info SSBO for attribute fetch.
- Shaders under `src/main/resources/shaders/rt/`:
  - `raygen.rgen` — camera rays, shadows, water transmission, sparse scene mirrors
  - `closesthit.rchit` / `water.rchit` / `anyhit.rahit` / `miss.rmiss` / `shadow.rmiss`
- Water: denser tint, fresnel sky + **near-only** sparse scene reflections (LOD culled
  from the mirror mask), transmission without stacking mirror+refract on one pixel.
- Leaves: any-hit alpha cutout; non-opaque BLAS flags.
- Half-res RT dispatch with bilinear upscale (stand-in for denoise/DLSS).
- Cull masks: near opaque `0x01`, water `0x02`, distant LOD `0x04`.

## Distant Horizons–style LOD

- `LodMesher` / `LodMeshScheduler` build coarse heightmap patches beyond near
  voxel distance (default LOD horizon **128** chunks).
- Sample spacing **2 / 4** blocks (near / far LOD sections).
- LOD meshes marked `distantLod` for cheaper RT mirror culling.

## Sky / sun (raster)

- Procedural sky shaders (`sky.vert` / `sky.frag`) and sun texture for the
  non-RT (and composite) path.

## Diagnostics

- `DiagLog` writes fsynced breadcrumbs to `run/diag.log` (RT frame stages,
  `DEVICE_LOST`, heartbeats) to diagnose silent native aborts.

## Assets / tooling

- Pixelated block textures (atlas tiles) and `tools/pixelate_block_textures.py`.
- Sun texture under `textures/sky/`.

## Build / style

- Spotless + Google Java Format; Javadoc on public APIs for new types.
- Vulkan 1.2 device path with optional RT features in `VulkanContext`.
