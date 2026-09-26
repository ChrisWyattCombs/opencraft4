package opencraft.world.region;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import opencraft.world.chunk.Chunk;

/**
 * In-memory container for up to {@link #REGION_SIZE}×{@link #REGION_SIZE} chunks stored in a dense
 * array (null = not loaded).
 */
public final class Region {

  /** Number of chunks along each horizontal axis in one region. */
  public static final int REGION_SIZE = 32;

  /** Total chunk slots in the region array. */
  public static final int CHUNK_COUNT = REGION_SIZE * REGION_SIZE;

  private static final byte[] MAGIC = new byte[] {'O', 'C', 'R', '1'};
  private static final int FORMAT_VERSION = 1;

  private final RegionPos position;

  /** Indexed by {@code localX + localZ * REGION_SIZE}; null means absent. */
  private final Chunk[] chunks = new Chunk[CHUNK_COUNT];

  /**
   * Creates an empty region at the given coordinates.
   *
   * @param position region index
   */
  public Region(RegionPos position) {
    this.position = position;
  }

  /**
   * Returns this region's index.
   *
   * @return region position
   */
  public RegionPos getPosition() {
    return position;
  }

  /**
   * Returns the chunk at local coordinates within this region, if loaded.
   *
   * @param localX local chunk X in [0, {@link #REGION_SIZE})
   * @param localZ local chunk Z in [0, {@link #REGION_SIZE})
   * @return chunk or {@code null}
   */
  public Chunk getChunk(int localX, int localZ) {
    return chunks[index(localX, localZ)];
  }

  /**
   * Stores a chunk at its local coordinates within this region.
   *
   * @param localX local chunk X
   * @param localZ local chunk Z
   * @param chunk chunk data
   */
  public void putChunk(int localX, int localZ, Chunk chunk) {
    chunks[index(localX, localZ)] = chunk;
  }

  /**
   * Removes a chunk from this region's array.
   *
   * @param localX local chunk X
   * @param localZ local chunk Z
   * @return removed chunk or {@code null}
   */
  public Chunk removeChunk(int localX, int localZ) {
    int i = index(localX, localZ);
    Chunk previous = chunks[i];
    chunks[i] = null;
    return previous;
  }

  /**
   * @return number of non-null chunk slots currently held in memory
   */
  public int loadedCount() {
    int count = 0;
    for (Chunk chunk : chunks) {
      if (chunk != null) {
        count++;
      }
    }
    return count;
  }

  /**
   * Writes this region to disk, merging with any existing file so unloaded chunks are not lost.
   *
   * @param path output file
   * @throws IOException if writing fails
   */
  public void serialize(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Chunk[] merged = new Chunk[CHUNK_COUNT];
    if (Files.isRegularFile(path)) {
      try {
        Region onDisk = deserialize(path, position);
        System.arraycopy(onDisk.chunks, 0, merged, 0, CHUNK_COUNT);
      } catch (IOException ignored) {
        // Corrupt/partial file — overwrite with what we have in memory.
      }
    }
    for (int i = 0; i < CHUNK_COUNT; i++) {
      if (chunks[i] != null) {
        merged[i] = chunks[i];
      }
    }

    try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(path))) {
      out.write(MAGIC);
      out.writeInt(FORMAT_VERSION);
      for (int lz = 0; lz < REGION_SIZE; lz++) {
        for (int lx = 0; lx < REGION_SIZE; lx++) {
          Chunk chunk = merged[index(lx, lz)];
          if (chunk == null || chunk.isEmpty()) {
            out.writeBoolean(false);
          } else {
            out.writeBoolean(true);
            out.write(chunk.getBlockData());
          }
        }
      }
    }
  }

  /**
   * Reads a region from a binary file at {@code path}.
   *
   * @param path input file
   * @param regionPos region index stored in the file
   * @return deserialized region
   * @throws IOException if reading fails or the format is invalid
   */
  public static Region deserialize(Path path, RegionPos regionPos) throws IOException {
    Region region = new Region(regionPos);
    try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
      byte[] magic = in.readNBytes(4);
      if (magic.length != 4
          || magic[0] != MAGIC[0]
          || magic[1] != MAGIC[1]
          || magic[2] != MAGIC[2]
          || magic[3] != MAGIC[3]) {
        throw new IOException("Invalid region magic");
      }
      int version = in.readInt();
      if (version != FORMAT_VERSION) {
        throw new IOException("Unsupported region version: " + version);
      }
      byte[] blockBuffer = new byte[Chunk.BLOCK_COUNT];
      for (int lz = 0; lz < REGION_SIZE; lz++) {
        for (int lx = 0; lx < REGION_SIZE; lx++) {
          boolean present = in.readBoolean();
          if (present) {
            in.readFully(blockBuffer);
            region.putChunk(lx, lz, new Chunk(blockBuffer.clone()));
          }
        }
      }
    }
    return region;
  }

  private static int index(int localX, int localZ) {
    return localX + localZ * REGION_SIZE;
  }
}
