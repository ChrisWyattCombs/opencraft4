package opencraft.graphics.render;

import java.util.Arrays;
import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;

/**
 * Builds CPU meshes with greedy meshing for opaque faces and simple quads for water.
 *
 * <p>Vertex layout (8 floats): pos(3) + uvMin(2) + shade(1) + localUv(2). Local UVs are fract()'d
 * in the fragment shader so greedy quads tile atlas textures correctly.
 */
public final class ChunkMesher {

  private static final float[] FACE_LIGHT = {0.65f, 0.65f, 0.75f, 0.75f, 0.45f, 1.05f};
  private static final int[] DX = {-1, 1, 0, 0, 0, 0};
  private static final int[] DY = {0, 0, 0, 0, -1, 1};
  private static final int[] DZ = {0, 0, -1, 1, 0, 0};
  private static final float MIN_LIGHT = 0.18f;
  private static final int FLOATS_PER_VERT = 8;

  private static final MeshData EMPTY =
      new MeshData(new float[0], new int[0], new float[0], new int[0]);

  private ChunkMesher() {}

  /**
   * Mesh data split into opaque and translucent draws.
   *
   * @param opaqueVertices solid / leaf geometry
   * @param opaqueIndices opaque indices
   * @param translucentVertices liquid geometry
   * @param translucentIndices translucent indices
   */
  public record MeshData(
      float[] opaqueVertices,
      int[] opaqueIndices,
      float[] translucentVertices,
      int[] translucentIndices) {
    /** @return {@code true} if both passes are empty */
    public boolean isEmpty() {
      return opaqueIndices.length == 0 && translucentIndices.length == 0;
    }
  }

  /**
   * Snapshot of one chunk plus four edge neighbors for thread-safe meshing.
   *
   * @param pos chunk position
   * @param blocks this chunk's block ids
   * @param negX neighbor at (x-1) or null
   * @param posX neighbor at (x+1) or null
   * @param negZ neighbor at (z-1) or null
   * @param posZ neighbor at (z+1) or null
   */
  public record ChunkSnapshot(
      ChunkPos pos, byte[] blocks, byte[] negX, byte[] posX, byte[] negZ, byte[] posZ) {}

  /**
   * Builds a mesh from a thread-safe snapshot.
   *
   * @param snap block copies
   * @param atlas texture atlas
   * @return mesh data
   */
  public static MeshData build(ChunkSnapshot snap, TextureAtlas atlas) {
    return build(
        snap.pos(), snap.blocks(), snap.negX(), snap.posX(), snap.negZ(), snap.posZ(), atlas);
  }

  /**
   * Builds opaque (greedy) + translucent meshes for one chunk.
   *
   * @param pos chunk position
   * @param blocks chunk block data
   * @param negX west neighbor blocks or null
   * @param posX east neighbor blocks or null
   * @param negZ north neighbor blocks or null
   * @param posZ south neighbor blocks or null
   * @param atlas texture atlas
   * @return mesh data
   */
  public static MeshData build(
      ChunkPos pos,
      byte[] blocks,
      byte[] negX,
      byte[] posX,
      byte[] negZ,
      byte[] posZ,
      TextureAtlas atlas) {
    int minY = Chunk.SIZE_Y;
    int maxY = -1;
    for (int y = 0; y < Chunk.SIZE_Y; y++) {
      if (rowHasSolid(blocks, y)) {
        if (y < minY) {
          minY = y;
        }
        maxY = y;
      }
    }
    if (maxY < minY) {
      return EMPTY;
    }
    minY = Math.max(0, minY - 1);
    maxY = Math.min(Chunk.SIZE_Y - 1, maxY + 1);

    BlockRegistry registry = BlockRegistry.getInstance();
    int baseX = pos.x() * Chunk.SIZE_X;
    int baseZ = pos.z() * Chunk.SIZE_Z;

    int[] topSolid = new int[Chunk.SIZE_X * Chunk.SIZE_Z];
    Arrays.fill(topSolid, -1);
    for (int y = maxY; y >= minY; y--) {
      for (int z = 0; z < Chunk.SIZE_Z; z++) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
          int i = x + z * Chunk.SIZE_X;
          if (topSolid[i] >= 0) {
            continue;
          }
          Block b = registry.get(blockAt(blocks, negX, posX, negZ, posZ, x, y, z));
          if (b.isSolid()) {
            topSolid[i] = y;
          }
        }
      }
    }

    FloatIntBuf opaque = new FloatIntBuf(16384, 8192);
    FloatIntBuf translucent = new FloatIntBuf(4096, 2048);

    // Greedy opaque faces per direction.
    for (int face = 0; face < 6; face++) {
      greedyOpaqueFace(
          face,
          minY,
          maxY,
          baseX,
          baseZ,
          blocks,
          negX,
          posX,
          negZ,
          posZ,
          registry,
          atlas,
          topSolid,
          opaque);
    }

    // Water surfaces (non-greedy — few faces).
    for (int y = minY; y <= maxY; y++) {
      for (int z = 0; z < Chunk.SIZE_Z; z++) {
        for (int x = 0; x < Chunk.SIZE_X; x++) {
          byte id = blockAt(blocks, negX, posX, negZ, posZ, x, y, z);
          if (id == BlockIds.AIR) {
            continue;
          }
          Block block = registry.get(id);
          if (!block.isLiquid()) {
            continue;
          }
          for (int face = 4; face < 6; face++) {
            Block neighbor =
                registry.get(
                    blockAt(
                        blocks,
                        negX,
                        posX,
                        negZ,
                        posZ,
                        x + DX[face],
                        y + DY[face],
                        z + DZ[face]));
            if (neighbor.isLiquid() || neighbor.isOpaque()) {
              continue;
            }
            String path = textureForFace(block, face);
            if (path == null) {
              continue;
            }
            float[] uv = atlas.uvFor(path);
            float shade =
                faceShade(
                    face,
                    x + DX[face],
                    y + DY[face],
                    z + DZ[face],
                    blocks,
                    negX,
                    posX,
                    negZ,
                    posZ,
                    registry,
                    topSolid)
                * 0.95f;
            emitUnitQuad(
                translucent,
                face,
                baseX + x,
                y,
                baseZ + z,
                1,
                1,
                uv[0],
                uv[1],
                shade);
          }
        }
      }
    }

    if (opaque.ic == 0 && translucent.ic == 0) {
      return EMPTY;
    }
    return new MeshData(
        Arrays.copyOf(opaque.verts, opaque.vc),
        Arrays.copyOf(opaque.inds, opaque.ic),
        Arrays.copyOf(translucent.verts, translucent.vc),
        Arrays.copyOf(translucent.inds, translucent.ic));
  }

  private static void greedyOpaqueFace(
      int face,
      int minY,
      int maxY,
      int baseX,
      int baseZ,
      byte[] blocks,
      byte[] negX,
      byte[] posX,
      byte[] negZ,
      byte[] posZ,
      BlockRegistry registry,
      TextureAtlas atlas,
      int[] topSolid,
      FloatIntBuf out) {
    // Slice axis = face normal; u,v = the two axes spanning the face.
    int[] dims = sliceDims(face, minY, maxY);
    int sliceCount = dims[0];
    int uSize = dims[1];
    int vSize = dims[2];
    int slice0 = dims[3];

    int[] mask = new int[uSize * vSize];
    float[] shadeMask = new float[uSize * vSize];

    for (int slice = 0; slice < sliceCount; slice++) {
      int s = slice0 + slice;
      Arrays.fill(mask, 0);
      for (int v = 0; v < vSize; v++) {
        for (int u = 0; u < uSize; u++) {
          int[] xyz = fromSlice(face, s, u, v, minY);
          int x = xyz[0];
          int y = xyz[1];
          int z = xyz[2];
          if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) {
            continue;
          }
          byte id = blockAt(blocks, negX, posX, negZ, posZ, x, y, z);
          if (id == BlockIds.AIR) {
            continue;
          }
          Block block = registry.get(id);
          if (block.isLiquid()) {
            continue;
          }
          Block neighbor =
              registry.get(
                  blockAt(
                      blocks, negX, posX, negZ, posZ, x + DX[face], y + DY[face], z + DZ[face]));
          if (neighbor.isOpaque()) {
            continue;
          }
          String path = textureForFace(block, face);
          if (path == null) {
            continue;
          }
          int mi = u + v * uSize;
          // Pack texture index into mask via path hash bucket — use block id (texture tied to id).
          mask[mi] = id & 0xff;
          shadeMask[mi] =
              faceShade(
                  face,
                  x + DX[face],
                  y + DY[face],
                  z + DZ[face],
                  blocks,
                  negX,
                  posX,
                  negZ,
                  posZ,
                  registry,
                  topSolid);
        }
      }

      for (int v = 0; v < vSize; v++) {
        for (int u = 0; u < uSize; ) {
          int mi = u + v * uSize;
          int type = mask[mi];
          if (type == 0) {
            u++;
            continue;
          }
          float shade = shadeMask[mi];
          int width = 1;
          while (u + width < uSize
              && mask[mi + width] == type
              && Math.abs(shadeMask[mi + width] - shade) < 0.04f) {
            width++;
          }
          int height = 1;
          heightLoop:
          while (v + height < vSize) {
            for (int du = 0; du < width; du++) {
              int idx = (u + du) + (v + height) * uSize;
              if (mask[idx] != type || Math.abs(shadeMask[idx] - shade) >= 0.04f) {
                break heightLoop;
              }
            }
            height++;
          }

          for (int dv = 0; dv < height; dv++) {
            for (int du = 0; du < width; du++) {
              mask[(u + du) + (v + dv) * uSize] = 0;
            }
          }

          Block block = registry.get((byte) type);
          String path = textureForFace(block, face);
          float[] uv = atlas.uvFor(path);
          int[] origin = fromSlice(face, s, u, v, minY);
          emitGreedyQuad(
              out, face, baseX, baseZ, origin[0], origin[1], origin[2], width, height, uv[0], uv[1],
              shade);
          u += width;
        }
      }
    }
  }

  /** @return {sliceCount, uSize, vSize, sliceStart} */
  private static int[] sliceDims(int face, int minY, int maxY) {
    int ySpan = maxY - minY + 1;
    return switch (face) {
      case 0, 1 -> new int[] {Chunk.SIZE_X, Chunk.SIZE_Z, ySpan, 0}; // slice=x, u=z, v=y
      case 2, 3 -> new int[] {Chunk.SIZE_Z, Chunk.SIZE_X, ySpan, 0}; // slice=z, u=x, v=y
      default -> new int[] {ySpan, Chunk.SIZE_X, Chunk.SIZE_Z, minY}; // slice=y, u=x, v=z
    };
  }

  private static int[] fromSlice(int face, int slice, int u, int v, int minY) {
    return switch (face) {
      case 0, 1 -> new int[] {slice, minY + v, u};
      case 2, 3 -> new int[] {u, minY + v, slice};
      default -> new int[] {u, slice, v};
    };
  }

  private static void emitGreedyQuad(
      FloatIntBuf out,
      int face,
      int baseX,
      int baseZ,
      int lx,
      int y,
      int lz,
      int w,
      int h,
      float u0,
      float v0,
      float shade) {
    float x = baseX + lx;
    float z = baseZ + lz;
    // w along first face axis, h along second — match fromSlice u/v.
    emitQuad(out, face, x, y, z, w, h, u0, v0, shade);
  }

  private static void emitUnitQuad(
      FloatIntBuf out,
      int face,
      float x,
      float y,
      float z,
      int w,
      int h,
      float u0,
      float v0,
      float shade) {
    emitQuad(out, face, x, y, z, w, h, u0, v0, shade);
  }

  private static void emitQuad(
      FloatIntBuf out,
      int face,
      float x,
      float y,
      float z,
      int w,
      int h,
      float u0,
      float v0,
      float shade) {
    out.ensure(FLOATS_PER_VERT * 4, 6);
    int base = out.vc / FLOATS_PER_VERT;
    float[] cx = new float[4];
    float[] cy = new float[4];
    float[] cz = new float[4];
    float[] lu = new float[4];
    float[] lv = new float[4];
    switch (face) {
      case 0 -> { // -X : u=z extent w, v=y extent h
        float xf = x;
        cx[0] = xf;
        cy[0] = y;
        cz[0] = z + w;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = xf;
        cy[1] = y;
        cz[1] = z;
        lu[1] = w;
        lv[1] = h;
        cx[2] = xf;
        cy[2] = y + h;
        cz[2] = z;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = xf;
        cy[3] = y + h;
        cz[3] = z + w;
        lu[3] = 0;
        lv[3] = 0;
      }
      case 1 -> { // +X
        float xf = x + 1;
        cx[0] = xf;
        cy[0] = y;
        cz[0] = z;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = xf;
        cy[1] = y;
        cz[1] = z + w;
        lu[1] = w;
        lv[1] = h;
        cx[2] = xf;
        cy[2] = y + h;
        cz[2] = z + w;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = xf;
        cy[3] = y + h;
        cz[3] = z;
        lu[3] = 0;
        lv[3] = 0;
      }
      case 2 -> { // -Z : u=x extent w, v=y extent h
        float zf = z;
        cx[0] = x;
        cy[0] = y;
        cz[0] = zf;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = x + w;
        cy[1] = y;
        cz[1] = zf;
        lu[1] = w;
        lv[1] = h;
        cx[2] = x + w;
        cy[2] = y + h;
        cz[2] = zf;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = x;
        cy[3] = y + h;
        cz[3] = zf;
        lu[3] = 0;
        lv[3] = 0;
      }
      case 3 -> { // +Z
        float zf = z + 1;
        cx[0] = x + w;
        cy[0] = y;
        cz[0] = zf;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = x;
        cy[1] = y;
        cz[1] = zf;
        lu[1] = w;
        lv[1] = h;
        cx[2] = x;
        cy[2] = y + h;
        cz[2] = zf;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = x + w;
        cy[3] = y + h;
        cz[3] = zf;
        lu[3] = 0;
        lv[3] = 0;
      }
      case 4 -> { // -Y : u=x extent w, v=z extent h
        float yf = y;
        cx[0] = x;
        cy[0] = yf;
        cz[0] = z + h;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = x + w;
        cy[1] = yf;
        cz[1] = z + h;
        lu[1] = w;
        lv[1] = h;
        cx[2] = x + w;
        cy[2] = yf;
        cz[2] = z;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = x;
        cy[3] = yf;
        cz[3] = z;
        lu[3] = 0;
        lv[3] = 0;
      }
      default -> { // +Y
        float yf = y + 1;
        cx[0] = x;
        cy[0] = yf;
        cz[0] = z;
        lu[0] = 0;
        lv[0] = h;
        cx[1] = x + w;
        cy[1] = yf;
        cz[1] = z;
        lu[1] = w;
        lv[1] = h;
        cx[2] = x + w;
        cy[2] = yf;
        cz[2] = z + h;
        lu[2] = w;
        lv[2] = 0;
        cx[3] = x;
        cy[3] = yf;
        cz[3] = z + h;
        lu[3] = 0;
        lv[3] = 0;
      }
    }
    for (int i = 0; i < 4; i++) {
      int o = out.vc + i * FLOATS_PER_VERT;
      out.verts[o] = cx[i];
      out.verts[o + 1] = cy[i];
      out.verts[o + 2] = cz[i];
      out.verts[o + 3] = u0;
      out.verts[o + 4] = v0;
      out.verts[o + 5] = Math.max(MIN_LIGHT, Math.min(1.35f, shade));
      out.verts[o + 6] = lu[i];
      out.verts[o + 7] = lv[i];
    }
    out.vc += FLOATS_PER_VERT * 4;
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 1;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base;
    out.inds[out.ic++] = base + 2;
    out.inds[out.ic++] = base + 3;
  }

  private static float faceShade(
      int face,
      int ox,
      int oy,
      int oz,
      byte[] blocks,
      byte[] negX,
      byte[] posX,
      byte[] negZ,
      byte[] posZ,
      BlockRegistry registry,
      int[] topSolid) {
    float faceLight = FACE_LIGHT[face];
    float sky = skylightAt(ox, oy, oz, blocks, negX, posX, negZ, posZ, registry, topSolid);
    float enclosed = enclosureDarkening(ox, oy, oz, blocks, negX, posX, negZ, posZ, registry);
    return faceLight * sky * enclosed;
  }

  private static float skylightAt(
      int x,
      int y,
      int z,
      byte[] blocks,
      byte[] negX,
      byte[] posX,
      byte[] negZ,
      byte[] posZ,
      BlockRegistry registry,
      int[] topSolid) {
    if (y < 0) {
      return MIN_LIGHT;
    }
    if (x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z) {
      int top = topSolid[x + z * Chunk.SIZE_X];
      if (top < 0 || y >= top) {
        return 1.0f;
      }
      return Math.max(0.25f, 1.0f - (top - y) * 0.08f);
    }
    int opaqueAbove = 0;
    int limit = Math.min(Chunk.SIZE_Y - 1, y + 24);
    for (int ay = Math.max(y + 1, 0); ay <= limit; ay++) {
      Block b = registry.get(blockAt(blocks, negX, posX, negZ, posZ, x, ay, z));
      if (b.isSolid()) {
        opaqueAbove++;
        if (opaqueAbove >= 8) {
          break;
        }
      }
    }
    return Math.max(0.25f, 1.0f - opaqueAbove * 0.12f);
  }

  private static float enclosureDarkening(
      int x,
      int y,
      int z,
      byte[] blocks,
      byte[] negX,
      byte[] posX,
      byte[] negZ,
      byte[] posZ,
      BlockRegistry registry) {
    int solid = 0;
    for (int f = 0; f < 6; f++) {
      Block b =
          registry.get(blockAt(blocks, negX, posX, negZ, posZ, x + DX[f], y + DY[f], z + DZ[f]));
      if (b.isSolid()) {
        solid++;
      }
    }
    return Math.max(0.3f, 1.0f - solid * 0.14f);
  }

  private static boolean rowHasSolid(byte[] blocks, int y) {
    for (int z = 0; z < Chunk.SIZE_Z; z++) {
      for (int x = 0; x < Chunk.SIZE_X; x++) {
        if (blocks[Chunk.index(x, y, z)] != BlockIds.AIR) {
          return true;
        }
      }
    }
    return false;
  }

  private static String textureForFace(Block block, int face) {
    return switch (face) {
      case 5 -> block.getTextureTop() != null ? block.getTextureTop() : block.getTextureSide();
      case 4 ->
          block.getTextureBottom() != null ? block.getTextureBottom() : block.getTextureSide();
      default -> block.getTextureSide() != null ? block.getTextureSide() : block.getTextureTop();
    };
  }

  private static byte blockAt(
      byte[] blocks, byte[] negX, byte[] posX, byte[] negZ, byte[] posZ, int x, int y, int z) {
    if (y < 0 || y >= Chunk.SIZE_Y) {
      return BlockIds.AIR;
    }
    if (x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z) {
      return blocks[Chunk.index(x, y, z)];
    }
    // Snapshots only include face neighbors — anything diagonal/far is air.
    if (x < 0 && z >= 0 && z < Chunk.SIZE_Z) {
      return negX == null ? BlockIds.AIR : negX[Chunk.index(x + Chunk.SIZE_X, y, z)];
    }
    if (x >= Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z) {
      return posX == null ? BlockIds.AIR : posX[Chunk.index(x - Chunk.SIZE_X, y, z)];
    }
    if (z < 0 && x >= 0 && x < Chunk.SIZE_X) {
      return negZ == null ? BlockIds.AIR : negZ[Chunk.index(x, y, z + Chunk.SIZE_Z)];
    }
    if (z >= Chunk.SIZE_Z && x >= 0 && x < Chunk.SIZE_X) {
      return posZ == null ? BlockIds.AIR : posZ[Chunk.index(x, y, z - Chunk.SIZE_Z)];
    }
    return BlockIds.AIR;
  }

  private static final class FloatIntBuf {
    float[] verts;
    int[] inds;
    int vc;
    int ic;

    FloatIntBuf(int vertCap, int indCap) {
      verts = new float[vertCap];
      inds = new int[indCap];
    }

    void ensure(int moreV, int moreI) {
      if (vc + moreV > verts.length) {
        verts = Arrays.copyOf(verts, Math.max(verts.length * 2, vc + moreV));
      }
      if (ic + moreI > inds.length) {
        inds = Arrays.copyOf(inds, Math.max(inds.length * 2, ic + moreI));
      }
    }
  }
}
