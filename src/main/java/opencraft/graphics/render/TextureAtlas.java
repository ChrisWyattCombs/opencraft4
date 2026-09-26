package opencraft.graphics.render;

import static org.lwjgl.stb.STBImage.stbi_failure_reason;
import static org.lwjgl.stb.STBImage.stbi_image_free;
import static org.lwjgl.stb.STBImage.stbi_load_from_memory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import opencraft.world.block.Block;
import opencraft.world.block.BlockRegistry;
import org.lwjgl.BufferUtils;

/**
 * Packs block textures into a single RGBA atlas for Vulkan sampling.
 *
 * <p>Loads classpath PNGs when present (scaled into atlas tiles); falls back to a small procedural
 * tile so missing assets never block world creation.
 */
public final class TextureAtlas {

  private static final int TILE = 32;
  private static volatile TextureAtlas cached;

  private final Map<String, Integer> pathToIndex = new LinkedHashMap<>();
  private final int tilesPerRow;
  private final int width;
  private final int height;
  private final ByteBuffer pixels;

  private TextureAtlas(int tilesPerRow, int width, int height, ByteBuffer pixels) {
    this.tilesPerRow = tilesPerRow;
    this.width = width;
    this.height = height;
    this.pixels = pixels;
  }

  /**
   * Returns a shared atlas, building it once on first use.
   *
   * @return atlas instance
   */
  public static TextureAtlas get() {
    TextureAtlas existing = cached;
    if (existing != null) {
      return existing;
    }
    synchronized (TextureAtlas.class) {
      if (cached == null) {
        cached = createFromBlocks();
      }
      return cached;
    }
  }

  /** Drops the cached atlas so the next {@link #get()} rebuilds from disk/classpath. */
  public static void invalidate() {
    synchronized (TextureAtlas.class) {
      cached = null;
    }
  }

  /**
   * Builds an atlas from all registered block texture paths.
   *
   * @return atlas instance
   */
  public static TextureAtlas createFromBlocks() {
    invalidate();
    Map<String, ByteBuffer> images = new LinkedHashMap<>();
    BlockRegistry registry = BlockRegistry.getInstance();
    for (Block block : registry.all()) {
      addPath(images, block.getTextureTop());
      addPath(images, block.getTextureSide());
      addPath(images, block.getTextureBottom());
    }
    if (images.isEmpty()) {
      throw new IllegalStateException("No block textures found");
    }

    int count = images.size();
    int tilesPerRow = (int) Math.ceil(Math.sqrt(count));
    int width = tilesPerRow * TILE;
    int height = tilesPerRow * TILE;
    ByteBuffer atlas = BufferUtils.createByteBuffer(width * height * 4);

    int index = 0;
    TextureAtlas result = new TextureAtlas(tilesPerRow, width, height, atlas);
    for (Map.Entry<String, ByteBuffer> entry : images.entrySet()) {
      result.pathToIndex.put(entry.getKey(), index);
      int tx = (index % tilesPerRow) * TILE;
      int ty = (index / tilesPerRow) * TILE;
      blitTile(atlas, width, entry.getValue(), tx, ty);
      index++;
    }
    System.out.println("[Opencraft] texture atlas " + width + "x" + height + " tiles=" + count);
    return result;
  }

  /**
   * Returns atlas pixel width.
   *
   * @return width in pixels
   */
  public int getWidth() {
    return width;
  }

  /**
   * Returns atlas pixel height.
   *
   * @return height in pixels
   */
  public int getHeight() {
    return height;
  }

  /**
   * Returns tightly packed RGBA8 atlas pixels.
   *
   * @return pixel buffer
   */
  public ByteBuffer getPixels() {
    return pixels;
  }

  /**
   * Returns UV span of one atlas tile (same for every tile).
   *
   * @return {@code {spanU, spanV}}
   */
  public float[] tileUvSpan() {
    float spanU = (TILE - 1f) / width;
    float spanV = (TILE - 1f) / height;
    return new float[] {spanU, spanV};
  }

  /**
   * Looks up UV bounds for a texture resource path.
   *
   * @param path classpath texture path
   * @return array {@code {u0, v0, u1, v1}}
   */
  public float[] uvFor(String path) {
    Integer index = pathToIndex.get(path);
    if (index == null) {
      return new float[] {0, 0, 1f / tilesPerRow, 1f / tilesPerRow};
    }
    int tx = index % tilesPerRow;
    int ty = index / tilesPerRow;
    float u0 = (tx * TILE + 0.5f) / width;
    float v0 = (ty * TILE + 0.5f) / height;
    float u1 = ((tx + 1) * TILE - 0.5f) / width;
    float v1 = ((ty + 1) * TILE - 0.5f) / height;
    return new float[] {u0, v0, u1, v1};
  }

  private static void addPath(Map<String, ByteBuffer> images, String path) {
    if (path == null || images.containsKey(path)) {
      return;
    }
    images.put(path, loadTile(path));
  }

  private static ByteBuffer loadTile(String path) {
    try (InputStream in = TextureAtlas.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) {
        System.out.println("[Opencraft] missing texture " + path + " — procedural fallback");
        return proceduralTile(path);
      }
      byte[] bytes = in.readAllBytes();
      ByteBuffer file = BufferUtils.createByteBuffer(bytes.length);
      file.put(bytes).flip();
      IntBuffer w = BufferUtils.createIntBuffer(1);
      IntBuffer h = BufferUtils.createIntBuffer(1);
      IntBuffer comp = BufferUtils.createIntBuffer(1);
      ByteBuffer rgba = stbi_load_from_memory(file, w, h, comp, 4);
      if (rgba == null) {
        System.out.println(
            "[Opencraft] decode failed "
                + path
                + ": "
                + stbi_failure_reason()
                + " — procedural fallback");
        return proceduralTile(path);
      }
      try {
        ByteBuffer tile = resizeNearest(rgba, w.get(0), h.get(0), TILE, TILE);
        // Water PNGs are often opaque RGB — force translucency for see-through water.
        if (path.toLowerCase().contains("water")) {
          forceAlpha(tile, 200);
        }
        return tile;
      } finally {
        stbi_image_free(rgba);
      }
    } catch (IOException e) {
      System.out.println("[Opencraft] texture IO " + path + ": " + e.getMessage());
      return proceduralTile(path);
    }
  }

  private static ByteBuffer resizeNearest(ByteBuffer src, int sw, int sh, int dw, int dh) {
    ByteBuffer dst = BufferUtils.createByteBuffer(dw * dh * 4);
    for (int y = 0; y < dh; y++) {
      int sy = y * sh / dh;
      for (int x = 0; x < dw; x++) {
        int sx = x * sw / dw;
        int si = (sy * sw + sx) * 4;
        int di = (y * dw + x) * 4;
        dst.put(di, src.get(si));
        dst.put(di + 1, src.get(si + 1));
        dst.put(di + 2, src.get(si + 2));
        dst.put(di + 3, src.get(si + 3));
      }
    }
    return dst;
  }

  /** Fast tile tinted by path name (used only when a PNG is missing). */
  private static ByteBuffer proceduralTile(String path) {
    int hash = path.hashCode();
    int r;
    int g;
    int b;
    int a = 255;
    String lower = path.toLowerCase();
    boolean grassSide = lower.contains("grass_side");
    if (lower.contains("grass_top")) {
      r = 70;
      g = 160;
      b = 55;
    } else if (grassSide) {
      r = 110;
      g = 85;
      b = 45;
    } else if (lower.contains("dirt")) {
      r = 120;
      g = 75;
      b = 40;
    } else if (lower.contains("stone")) {
      r = 120;
      g = 120;
      b = 120;
    } else if (lower.contains("sand")) {
      r = 210;
      g = 195;
      b = 120;
    } else if (lower.contains("wood_top")) {
      r = 150;
      g = 110;
      b = 60;
    } else if (lower.contains("wood")) {
      r = 130;
      g = 90;
      b = 45;
    } else if (lower.contains("leaves")) {
      r = 50;
      g = 130;
      b = 40;
      a = 220;
    } else if (lower.contains("water")) {
      r = 40;
      g = 90;
      b = 200;
      a = 180;
    } else {
      r = 80 + (hash & 0x7f);
      g = 80 + ((hash >> 7) & 0x7f);
      b = 80 + ((hash >> 14) & 0x7f);
    }

    ByteBuffer tile = BufferUtils.createByteBuffer(TILE * TILE * 4);
    for (int y = 0; y < TILE; y++) {
      for (int x = 0; x < TILE; x++) {
        int pr = r;
        int pg = g;
        int pb = b;
        if (grassSide && y < TILE / 4) {
          pr = 70;
          pg = 160;
          pb = 55;
        }
        int n = ((x * 13 + y * 7 + hash) & 7) - 3;
        int i = (y * TILE + x) * 4;
        tile.put(i, (byte) clamp(pr + n));
        tile.put(i + 1, (byte) clamp(pg + n));
        tile.put(i + 2, (byte) clamp(pb + n));
        tile.put(i + 3, (byte) a);
      }
    }
    return tile;
  }

  private static void forceAlpha(ByteBuffer tile, int alpha) {
    byte a = (byte) Math.max(0, Math.min(255, alpha));
    for (int i = 3; i < tile.capacity(); i += 4) {
      tile.put(i, a);
    }
  }

  private static int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static void blitTile(ByteBuffer atlas, int atlasW, ByteBuffer tile, int tx, int ty) {
    for (int y = 0; y < TILE; y++) {
      for (int x = 0; x < TILE; x++) {
        int si = (y * TILE + x) * 4;
        int di = ((ty + y) * atlasW + (tx + x)) * 4;
        atlas.put(di, tile.get(si));
        atlas.put(di + 1, tile.get(si + 1));
        atlas.put(di + 2, tile.get(si + 2));
        atlas.put(di + 3, tile.get(si + 3));
      }
    }
  }
}
