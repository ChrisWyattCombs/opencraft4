package opencraft.graphics.render;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;
import opencraft.world.gen.TerrainGenerator;

/**
 * Builds low-detail heightmap meshes for distant terrain (Distant Horizons–style LOD).
 *
 * <p>Samples {@link TerrainGenerator#sampleColumn} on a coarse grid — no voxel chunks are loaded —
 * and emits shaded heightmap tops, cliff skirts, and simplified tree proxies. Lighting uses the
 * same {@code faceLight × sky × enclosure} product as {@link ChunkMesher}. Vertex layout matches
 * near meshes.
 */
public final class LodMesher {

  private static final int FLOATS_PER_VERT = 8;

  /** Same face lighting as {@link ChunkMesher}. */
  private static final float[] FACE_LIGHT = {0.65f, 0.65f, 0.75f, 0.75f, 0.45f, 1.05f};

  private static final float MIN_LIGHT = 0.18f;
  private static final float MAX_LIGHT = 1.35f;

  private LodMesher() {}

  /**
   * Builds an LOD patch covering {@code [originX, originX+size)} × {@code [originZ, originZ+size)}.
   *
   * @param generator terrain sampler (same seed as the world)
   * @param atlas texture atlas
   * @param originX world block X of patch min corner
   * @param originZ world block Z of patch min corner
   * @param sizeBlocks patch edge length in blocks (must be divisible by {@code step})
   * @param step sample spacing in blocks (larger = coarser)
   * @return mesh data compatible with {@link GpuRegionMesh}
   */
  public static ChunkMesher.MeshData build(
      TerrainGenerator generator,
      TextureAtlas atlas,
      int originX,
      int originZ,
      int sizeBlocks,
      int step) {
    if (step < 1 || sizeBlocks < step || sizeBlocks % step != 0) {
      return new ChunkMesher.MeshData(new float[0], new int[0], new float[0], new int[0]);
    }
    int cells = sizeBlocks / step;
    int samples = cells + 1;
    int[] heights = new int[samples * samples];
    byte[] surfaceIds = new byte[samples * samples];
    boolean[] flooded = new boolean[samples * samples];

    for (int jz = 0; jz < samples; jz++) {
      for (int jx = 0; jx < samples; jx++) {
        int wx = originX + jx * step;
        int wz = originZ + jz * step;
        TerrainGenerator.ColumnSample col = generator.sampleColumn(wx, wz);
        int i = jz * samples + jx;
        heights[i] = col.surfaceY();
        surfaceIds[i] = col.surfaceId();
        flooded[i] = col.flooded();
      }
    }

    VertBuf opaque = new VertBuf(cells * cells * 64);
    VertBuf translucent = new VertBuf(cells * cells * 12);
    BlockRegistry blocks = BlockRegistry.getInstance();
    int sea = TerrainGenerator.seaLevel();

    for (int cz = 0; cz < cells; cz++) {
      for (int cx = 0; cx < cells; cx++) {
        int i00 = cz * samples + cx;
        int i10 = cz * samples + (cx + 1);
        int i01 = (cz + 1) * samples + cx;
        int i11 = (cz + 1) * samples + (cx + 1);

        float h00 = heights[i00] + 1f;
        float h10 = heights[i10] + 1f;
        float h01 = heights[i01] + 1f;
        float h11 = heights[i11] + 1f;
        int h = heights[i00];

        byte sid = surfaceIds[i00];
        boolean wet = flooded[i00];
        float x0 = originX + cx * step;
        float z0 = originZ + cz * step;
        float s = step;

        Block surface = blocks.get(sid);
        float[] topUv = atlas.uvFor(surface.getTextureTop());
        float[] sideUv = atlas.uvFor(surface.getTextureSide());

        // Match ChunkMesher faceShade: faceLight * sky * enclosure (not raw FACE_LIGHT).
        int cellWx = originX + cx * step + step / 2;
        int cellWz = originZ + cz * step + step / 2;
        float canopySky = canopySkyAt(generator, cellWx, cellWz, step);
        float topShade =
            exposedFaceShade(5, enclosureAtTop(heights, samples, cx, cz, h), canopySky);
        emitTopSloped(opaque, x0, z0, s, h00, h10, h11, h01, topUv[0], topUv[1], topShade);

        if (cx + 1 < cells) {
          int nh = heights[i10];
          if (nh < h) {
            emitSide(
                opaque,
                1,
                x0 + s,
                nh + 1,
                z0,
                s,
                h - nh,
                sideUv[0],
                sideUv[1],
                skirtShade(1, h, nh, canopySky));
          }
        }
        if (cx > 0) {
          int nh = heights[cz * samples + (cx - 1)];
          if (nh < h) {
            emitSide(
                opaque,
                0,
                x0,
                nh + 1,
                z0,
                s,
                h - nh,
                sideUv[0],
                sideUv[1],
                skirtShade(0, h, nh, canopySky));
          }
        }
        if (cz + 1 < cells) {
          int nh = heights[i01];
          if (nh < h) {
            emitSide(
                opaque,
                3,
                x0,
                nh + 1,
                z0 + s,
                s,
                h - nh,
                sideUv[0],
                sideUv[1],
                skirtShade(3, h, nh, canopySky));
          }
        }
        if (cz > 0) {
          int nh = heights[(cz - 1) * samples + cx];
          if (nh < h) {
            emitSide(
                opaque,
                2,
                x0,
                nh + 1,
                z0,
                s,
                h - nh,
                sideUv[0],
                sideUv[1],
                skirtShade(2, h, nh, canopySky));
          }
        }

        if (wet || h < sea) {
          float[] waterUv = atlas.uvFor(blocks.get(BlockIds.WATER).getTextureTop());
          float waterY = sea + 0.1f;
          // Liquids in ChunkMesher also ×0.95 after faceShade.
          float waterShade = clampShade(exposedFaceShade(5, OPEN_TOP_ENCLOSURE, 1f) * 0.95f);
          emitTopSloped(
              translucent,
              x0,
              z0,
              s,
              waterY,
              waterY,
              waterY,
              waterY,
              waterUv[0],
              waterUv[1],
              waterShade);
        }
      }
    }

    emitTreeProxies(generator, atlas, opaque, originX, originZ, sizeBlocks);

    return new ChunkMesher.MeshData(
        opaque.trimVerts(), opaque.trimInds(), translucent.trimVerts(), translucent.trimInds());
  }

  /** Places simplified trunk + canopy boxes where full generation would plant trees. */
  private static void emitTreeProxies(
      TerrainGenerator generator,
      TextureAtlas atlas,
      VertBuf opaque,
      int originX,
      int originZ,
      int sizeBlocks) {
    BlockRegistry blocks = BlockRegistry.getInstance();
    float[] woodUv = atlas.uvFor(blocks.get(BlockIds.WOOD).getTextureSide());
    float[] leafUv = atlas.uvFor(blocks.get(BlockIds.LEAVES).getTextureTop());
    int trunkH = TerrainGenerator.treeTrunkHeight();

    // Same 4-block tree cell grid as TerrainGenerator.generateChunk.
    int xStart = originX;
    while (Math.floorMod(xStart, 4) != 1) {
      xStart++;
    }
    int zStart = originZ;
    while (Math.floorMod(zStart, 4) != 1) {
      zStart++;
    }

    for (int wz = zStart; wz < originZ + sizeBlocks; wz += 4) {
      for (int wx = xStart; wx < originX + sizeBlocks; wx += 4) {
        if (!generator.shouldPlaceTreeAt(wx, wz)) {
          continue;
        }
        TerrainGenerator.ColumnSample col = generator.sampleColumn(wx, wz);
        float baseY = col.surfaceY() + 1f;
        // Exterior faces only (same as culled near meshes): one solid behind the face.
        float sideShade = exposedFaceShade(1, OPEN_SIDE_ENCLOSURE, 1f);
        float topShade = exposedFaceShade(5, OPEN_TOP_ENCLOSURE, 1f);
        emitAxisAlignedBox(
            opaque, wx, baseY, wz, 1, trunkH, 1, woodUv[0], woodUv[1], sideShade, topShade);
        float leafY = baseY + trunkH - 2;
        emitAxisAlignedBox(
            opaque, wx - 1, leafY, wz - 1, 3, 3, 3, leafUv[0], leafUv[1], sideShade, topShade);
      }
    }
  }

  /**
   * Enclosure for an open top face (air above ground): one solid neighbor below → {@code 1 - 0.14}.
   * Matches {@link ChunkMesher} enclosureDarkening for flat exposed tops.
   */
  private static final float OPEN_TOP_ENCLOSURE = 1.0f - 0.14f;

  /** Cliff / block side with the solid behind the face only. */
  private static final float OPEN_SIDE_ENCLOSURE = 1.0f - 0.14f;

  /**
   * Same product as ChunkMesher.faceShade for exposed LOD faces: {@code FACE_LIGHT * sky *
   * enclosure}.
   */
  private static float exposedFaceShade(int face, float enclosure, float sky) {
    return clampShade(FACE_LIGHT[face] * sky * enclosure);
  }

  /**
   * Counts solid neighbors of the air cell above the surface (matches ChunkMesher enclosure for
   * tops). Taller adjacent columns count as occluders.
   */
  private static float enclosureAtTop(int[] heights, int samples, int cx, int cz, int h) {
    int solid = 1; // ground under the top face
    int i00 = cz * samples + cx;
    // East / west / south / north sample points (cell corners + edge neighbors).
    if (cx + 1 < samples && heights[i00 + 1] > h) {
      solid++;
    }
    if (cx > 0 && heights[i00 - 1] > h) {
      solid++;
    }
    if (cz + 1 < samples && heights[i00 + samples] > h) {
      solid++;
    }
    if (cz > 0 && heights[i00 - samples] > h) {
      solid++;
    }
    return Math.max(0.3f, 1.0f - solid * 0.14f);
  }

  /**
   * Skylight attenuation under nearby tree canopies (leaves are solid occluders in ChunkMesher).
   */
  private static float canopySkyAt(TerrainGenerator generator, int wx, int wz, int step) {
    int radius = Math.max(2, step);
    int opaque = 0;
    for (int dz = -radius; dz <= radius && opaque < 8; dz += Math.max(1, step / 2)) {
      for (int dx = -radius; dx <= radius && opaque < 8; dx += Math.max(1, step / 2)) {
        if (generator.shouldPlaceTreeAt(wx + dx, wz + dz)) {
          opaque += 3;
        }
      }
    }
    if (opaque <= 0) {
      return 1.0f;
    }
    return Math.max(0.25f, 1.0f - opaque * 0.12f);
  }

  /** Vertical skirt shade: open-side enclosure + depth-based skylight like buried faces. */
  private static float skirtShade(int face, int h, int nh, float canopySky) {
    int depth = Math.max(0, (h - nh) / 2);
    float sky = Math.max(0.25f, canopySky - depth * 0.08f);
    return exposedFaceShade(face, OPEN_SIDE_ENCLOSURE, sky);
  }

  private static float clampShade(float shade) {
    return Math.max(MIN_LIGHT, Math.min(MAX_LIGHT, shade));
  }

  private static void emitTopSloped(
      VertBuf out,
      float x,
      float z,
      float s,
      float y00,
      float y10,
      float y11,
      float y01,
      float u0,
      float v0,
      float shade) {
    out.ensure(4, 6);
    int base = out.vc / FLOATS_PER_VERT;
    put(out, x, y00, z, u0, v0, shade, 0, s);
    put(out, x + s, y10, z, u0, v0, shade, s, s);
    put(out, x + s, y11, z + s, u0, v0, shade, s, 0);
    put(out, x, y01, z + s, u0, v0, shade, 0, 0);
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 1;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base + 3;
  }

  private static void emitSide(
      VertBuf out,
      int face,
      float x,
      float y,
      float z,
      float extent,
      int height,
      float u0,
      float v0,
      float shade) {
    if (height <= 0) {
      return;
    }
    out.ensure(4, 6);
    int base = out.vc / FLOATS_PER_VERT;
    float h = height;
    switch (face) {
      case 0 -> {
        put(out, x, y, z + extent, u0, v0, shade, 0, h);
        put(out, x, y, z, u0, v0, shade, extent, h);
        put(out, x, y + h, z, u0, v0, shade, extent, 0);
        put(out, x, y + h, z + extent, u0, v0, shade, 0, 0);
      }
      case 1 -> {
        put(out, x, y, z, u0, v0, shade, 0, h);
        put(out, x, y, z + extent, u0, v0, shade, extent, h);
        put(out, x, y + h, z + extent, u0, v0, shade, extent, 0);
        put(out, x, y + h, z, u0, v0, shade, 0, 0);
      }
      case 2 -> {
        put(out, x, y, z, u0, v0, shade, 0, h);
        put(out, x + extent, y, z, u0, v0, shade, extent, h);
        put(out, x + extent, y + h, z, u0, v0, shade, extent, 0);
        put(out, x, y + h, z, u0, v0, shade, 0, 0);
      }
      default -> {
        put(out, x + extent, y, z, u0, v0, shade, 0, h);
        put(out, x, y, z, u0, v0, shade, extent, h);
        put(out, x, y + h, z, u0, v0, shade, extent, 0);
        put(out, x + extent, y + h, z, u0, v0, shade, 0, 0);
      }
    }
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 1;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base + 3;
  }

  private static void emitAxisAlignedBox(
      VertBuf out,
      float x,
      float y,
      float z,
      float w,
      float h,
      float d,
      float u0,
      float v0,
      float sideShade,
      float topShade) {
    // Top (+Y)
    out.ensure(4, 6);
    int base = out.vc / FLOATS_PER_VERT;
    put(out, x, y + h, z, u0, v0, topShade, 0, d);
    put(out, x + w, y + h, z, u0, v0, topShade, w, d);
    put(out, x + w, y + h, z + d, u0, v0, topShade, w, 0);
    put(out, x, y + h, z + d, u0, v0, topShade, 0, 0);
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 1;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base + 3;

    int ih = Math.max(1, Math.round(h));
    emitSide(out, 0, x, y, z, d, ih, u0, v0, sideShade);
    emitSide(out, 1, x + w, y, z, d, ih, u0, v0, sideShade);
    emitSide(out, 2, x, y, z, w, ih, u0, v0, sideShade);
    emitSide(out, 3, x, y, z + d, w, ih, u0, v0, sideShade);
  }

  private static void put(
      VertBuf out, float x, float y, float z, float u0, float v0, float shade, float lu, float lv) {
    int o = out.vc;
    out.verts[o] = x;
    out.verts[o + 1] = y;
    out.verts[o + 2] = z;
    out.verts[o + 3] = u0;
    out.verts[o + 4] = v0;
    out.verts[o + 5] = shade;
    out.verts[o + 6] = lu;
    out.verts[o + 7] = lv;
    out.vc += FLOATS_PER_VERT;
  }

  private static final class VertBuf {
    float[] verts;
    int[] inds;
    int vc;
    int ic;

    VertBuf(int estimateVerts) {
      verts = new float[Math.max(64, estimateVerts) * FLOATS_PER_VERT];
      inds = new int[Math.max(64, estimateVerts) * 6 / 4];
    }

    void ensure(int moreVerts, int moreInds) {
      int needV = vc + moreVerts * FLOATS_PER_VERT;
      if (needV > verts.length) {
        float[] n = new float[Math.max(needV, verts.length * 2)];
        System.arraycopy(verts, 0, n, 0, vc);
        verts = n;
      }
      int needI = ic + moreInds;
      if (needI > inds.length) {
        int[] n = new int[Math.max(needI, inds.length * 2)];
        System.arraycopy(inds, 0, n, 0, ic);
        inds = n;
      }
    }

    float[] trimVerts() {
      float[] n = new float[vc];
      System.arraycopy(verts, 0, n, 0, vc);
      return n;
    }

    int[] trimInds() {
      int[] n = new int[ic];
      System.arraycopy(inds, 0, n, 0, ic);
      return n;
    }
  }
}
