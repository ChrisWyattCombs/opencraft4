package opencraft.world.chunk;

import opencraft.world.block.BlockIds;

/** Fixed-size voxel column storage for one horizontal chunk. */
public final class Chunk {

  /** Chunk width along X. */
  public static final int SIZE_X = 16;

  /** Chunk height along Y. */
  public static final int SIZE_Y = 256;

  /** Chunk depth along Z. */
  public static final int SIZE_Z = 16;

  /** Total block count per chunk. */
  public static final int BLOCK_COUNT = SIZE_X * SIZE_Y * SIZE_Z;

  private final byte[] blocks;
  private boolean dirty;

  /** Creates an empty chunk filled with air. */
  public Chunk() {
    blocks = new byte[BLOCK_COUNT];
    dirty = true;
  }

  /**
   * Creates a chunk wrapping existing block data.
   *
   * @param blocks block id array of length {@link #BLOCK_COUNT}
   */
  public Chunk(byte[] blocks) {
    if (blocks.length != BLOCK_COUNT) {
      throw new IllegalArgumentException(
          "Expected " + BLOCK_COUNT + " bytes, got " + blocks.length);
    }
    this.blocks = blocks;
    dirty = true;
  }

  /**
   * Returns a copy of the raw block id array.
   *
   * @return block bytes
   */
  public byte[] getBlockData() {
    return blocks.clone();
  }

  /**
   * Returns the block id at local chunk coordinates.
   *
   * @param lx local X in [0, {@link #SIZE_X})
   * @param y world Y in [0, {@link #SIZE_Y})
   * @param lz local Z in [0, {@link #SIZE_Z})
   * @return block id byte
   */
  public byte getBlock(int lx, int y, int lz) {
    return blocks[index(lx, y, lz)];
  }

  /**
   * Sets the block id at local chunk coordinates.
   *
   * @param lx local X
   * @param y world Y
   * @param lz local Z
   * @param id block id
   */
  public void setBlock(int lx, int y, int lz, byte id) {
    int idx = index(lx, y, lz);
    if (blocks[idx] != id) {
      blocks[idx] = id;
      dirty = true;
    }
  }

  /**
   * Returns whether every block in this chunk is air.
   *
   * @return {@code true} if the chunk contains only air
   */
  public boolean isEmpty() {
    for (byte block : blocks) {
      if (block != BlockIds.AIR) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns whether the mesh needs rebuilding.
   *
   * @return dirty flag
   */
  public boolean isDirty() {
    return dirty;
  }

  /**
   * Clears the mesh dirty flag after a successful rebuild.
   *
   * @param dirty new dirty state
   */
  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  /** Block-array index for local chunk coordinates. */
  public static int index(int lx, int y, int lz) {
    return lx + SIZE_X * (lz + SIZE_Z * y);
  }
}
