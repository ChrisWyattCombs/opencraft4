package opencraft.world;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import opencraft.world.block.Block;
import opencraft.world.block.BlockRegistry;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;
import opencraft.world.gen.TerrainGenerator;
import opencraft.world.region.Region;
import opencraft.world.region.RegionPos;

/** Loaded voxel world with chunk caching, generation, and region persistence. */
public final class World {

  private final String name;
  private final long seed;
  private final Path worldDir;
  private final ConcurrentHashMap<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<RegionPos, Region> regions = new ConcurrentHashMap<>();
  private final TerrainGenerator terrainGenerator;
  private final BlockRegistry blockRegistry = BlockRegistry.getInstance();

  /**
   * Opens a world with the given metadata and storage directory.
   *
   * @param name display name
   * @param seed generation seed
   * @param worldDir folder containing world.json and region files
   */
  public World(String name, long seed, Path worldDir) {
    this.name = name;
    this.seed = seed;
    this.worldDir = worldDir;
    terrainGenerator = new TerrainGenerator(seed);
  }

  /**
   * Returns the world display name.
   *
   * @return world name
   */
  public String getName() {
    return name;
  }

  /**
   * Returns the world generation seed.
   *
   * @return seed
   */
  public long getSeed() {
    return seed;
  }

  /**
   * Returns the on-disk world directory.
   *
   * @return world path
   */
  public Path getWorldDir() {
    return worldDir;
  }

  /**
   * Returns the block at world coordinates.
   *
   * @param x world block X
   * @param y world block Y
   * @param z world block Z
   * @return block instance (never {@code null})
   */
  public Block getBlock(int x, int y, int z) {
    if (y < 0 || y >= Chunk.SIZE_Y) {
      return blockRegistry.getAir();
    }
    ChunkPos chunkPos = toChunkPos(x, z);
    Chunk chunk = getChunk(chunkPos);
    int lx = Math.floorMod(x, Chunk.SIZE_X);
    int lz = Math.floorMod(z, Chunk.SIZE_Z);
    return blockRegistry.get(chunk.getBlock(lx, y, lz));
  }

  /**
   * Sets the block at world coordinates.
   *
   * @param x world block X
   * @param y world block Y
   * @param z world block Z
   * @param block block to place
   */
  public void setBlock(int x, int y, int z, Block block) {
    if (y < 0 || y >= Chunk.SIZE_Y) {
      return;
    }
    ChunkPos chunkPos = toChunkPos(x, z);
    Chunk chunk = getChunk(chunkPos);
    int lx = Math.floorMod(x, Chunk.SIZE_X);
    int lz = Math.floorMod(z, Chunk.SIZE_Z);
    chunk.setBlock(lx, y, lz, block.getId());
  }

  /**
   * Returns a chunk, loading from disk or generating it when missing.
   *
   * @param pos global chunk position
   * @return chunk instance
   */
  public Chunk getChunk(ChunkPos pos) {
    Chunk cached = chunks.get(pos);
    if (cached != null) {
      return cached;
    }
    return ensureChunkLoaded(pos);
  }

  /**
   * Returns a loaded chunk without generating a new one.
   *
   * @param pos global chunk position
   * @return chunk or {@code null} if not loaded
   */
  public Chunk getLoadedChunk(ChunkPos pos) {
    return chunks.get(pos);
  }

  /**
   * Ensures the chunk at {@code pos} is loaded into memory.
   *
   * @param pos global chunk position
   * @return loaded chunk
   */
  public Chunk ensureChunkLoaded(ChunkPos pos) {
    return chunks.computeIfAbsent(pos, this::loadOrGenerateChunk);
  }

  /**
   * Saves every loaded region to disk (merging with existing region files).
   *
   * @throws IOException if a region file cannot be written
   */
  public void saveAll() throws IOException {
    for (Region region : regions.values()) {
      region.serialize(regionFilePath(region.getPosition()));
    }
  }

  /**
   * Saves and unloads chunks farther than {@code keepDistance} from the player chunk. When the
   * player returns inside render distance, {@link #ensureChunkLoaded} reloads them from disk.
   *
   * @param playerChunk chunk containing the player
   * @param keepDistance Chebyshev radius to keep in memory (usually renderDistance + hysteresis)
   */
  public void unloadFarChunks(ChunkPos playerChunk, int keepDistance) {
    List<ChunkPos> toRemove = new ArrayList<>();
    for (ChunkPos pos : chunks.keySet()) {
      int dx = Math.abs(pos.x() - playerChunk.x());
      int dz = Math.abs(pos.z() - playerChunk.z());
      if (dx > keepDistance || dz > keepDistance) {
        toRemove.add(pos);
      }
    }
    if (toRemove.isEmpty()) {
      return;
    }

    Set<RegionPos> touched = new HashSet<>();
    for (ChunkPos pos : toRemove) {
      Chunk removed = chunks.remove(pos);
      if (removed == null) {
        continue;
      }
      RegionPos regionPos = toRegionPos(pos);
      Region region = regions.computeIfAbsent(regionPos, this::loadRegion);
      int localX = Math.floorMod(pos.x(), Region.REGION_SIZE);
      int localZ = Math.floorMod(pos.z(), Region.REGION_SIZE);
      region.putChunk(localX, localZ, removed);
      touched.add(regionPos);
    }

    for (RegionPos regionPos : touched) {
      Region region = regions.get(regionPos);
      if (region == null) {
        continue;
      }
      try {
        region.serialize(regionFilePath(regionPos));
      } catch (IOException e) {
        System.err.println(
            "[Opencraft] Failed to save region " + regionPos + ": " + e.getMessage());
        continue;
      }
      // Drop unloaded chunks from the region RAM cache.
      for (ChunkPos pos : toRemove) {
        if (toRegionPos(pos).equals(regionPos)) {
          int localX = Math.floorMod(pos.x(), Region.REGION_SIZE);
          int localZ = Math.floorMod(pos.z(), Region.REGION_SIZE);
          region.removeChunk(localX, localZ);
        }
      }
      if (region.loadedCount() == 0 && !regionHasActiveChunks(regionPos)) {
        regions.remove(regionPos);
      }
    }
  }

  private boolean regionHasActiveChunks(RegionPos regionPos) {
    for (ChunkPos pos : chunks.keySet()) {
      if (toRegionPos(pos).equals(regionPos)) {
        return true;
      }
    }
    return false;
  }

  private Chunk loadOrGenerateChunk(ChunkPos globalPos) {
    RegionPos regionPos = toRegionPos(globalPos);
    Region region = regions.computeIfAbsent(regionPos, this::loadRegion);
    int localX = Math.floorMod(globalPos.x(), Region.REGION_SIZE);
    int localZ = Math.floorMod(globalPos.z(), Region.REGION_SIZE);
    Chunk chunk = region.getChunk(localX, localZ);
    if (chunk == null) {
      chunk = new Chunk();
      terrainGenerator.generateChunk(chunk, globalPos);
      region.putChunk(localX, localZ, chunk);
    } else {
      // Reloaded from disk / region cache — force a mesh rebuild.
      chunk.setDirty(true);
    }
    return chunk;
  }

  private Region loadRegion(RegionPos regionPos) {
    Path path = regionFilePath(regionPos);
    if (Files.isRegularFile(path)) {
      try {
        return Region.deserialize(path, regionPos);
      } catch (IOException e) {
        System.err.println(
            "[Opencraft] Failed to load region " + regionPos + ": " + e.getMessage());
      }
    }
    return new Region(regionPos);
  }

  private Path regionFilePath(RegionPos regionPos) {
    return worldDir
        .resolve("regions")
        .resolve("r_" + regionPos.rx() + "_" + regionPos.rz() + ".dat");
  }

  private static ChunkPos toChunkPos(int blockX, int blockZ) {
    return new ChunkPos(Math.floorDiv(blockX, Chunk.SIZE_X), Math.floorDiv(blockZ, Chunk.SIZE_Z));
  }

  private static RegionPos toRegionPos(ChunkPos chunkPos) {
    return new RegionPos(
        Math.floorDiv(chunkPos.x(), Region.REGION_SIZE),
        Math.floorDiv(chunkPos.z(), Region.REGION_SIZE));
  }
}
