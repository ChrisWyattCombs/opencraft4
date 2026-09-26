package opencraft.world.gen;

import opencraft.world.biome.Biome;
import opencraft.world.biome.BiomeRegistry;
import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;

/**
 * Fills chunks with terrain columns driven by Perlin noise and biomes.
 *
 * <p>Height is continuous: continental shelves and lake basins slope gently under sea level so
 * shores flow into water instead of forming flat cliffs. Climate (temperature / moisture) is pure
 * Perlin from the world seed — domain-warped for organic edges, then lightly majority-filtered so
 * tiny speckles do not appear.
 */
public final class TerrainGenerator {

  /** Lower = larger climate regions (desert / forest / plains). */
  private static final double CLIMATE_SCALE = 0.0009;

  /** Continent shelf frequency (slightly larger than climate patches). */
  private static final double CONTINENT_SCALE = 0.00075;

  /** Half-extent of the majority vote window (blocks). */
  private static final int BIOME_BLEND_RADIUS = 80;

  /** Step between majority-vote samples (blocks). */
  private static final int BIOME_BLEND_STEP = 32;

  /** Domain-warp strength in blocks — bends climate so edges are not square. */
  private static final double CLIMATE_WARP = 96.0;

  /** Shared water surface height for oceans, lakes, and rivers. */
  private static final int SEA_LEVEL = 62;

  private final long seed;
  private final PerlinNoise heightNoise;
  private final PerlinNoise detailNoise;
  private final PerlinNoise ridgeNoise;
  private final PerlinNoise hillMaskNoise;
  private final PerlinNoise temperatureNoise;
  private final PerlinNoise moistureNoise;
  private final PerlinNoise continentalNoise;
  private final PerlinNoise riverNoise;
  private final PerlinNoise climateWarpX;
  private final PerlinNoise climateWarpZ;

  /**
   * Creates a terrain generator for the given world seed.
   *
   * @param seed world seed
   */
  public TerrainGenerator(long seed) {
    this.seed = seed;
    heightNoise = new PerlinNoise(seed);
    detailNoise = new PerlinNoise(seed + 314159L);
    ridgeNoise = new PerlinNoise(seed + 271828L);
    hillMaskNoise = new PerlinNoise(seed + 424242L);
    temperatureNoise = new PerlinNoise(seed + 1013904223L);
    moistureNoise = new PerlinNoise(seed + 1664525L);
    continentalNoise = new PerlinNoise(seed + 22695477L);
    riverNoise = new PerlinNoise(seed + 777011L);
    climateWarpX = new PerlinNoise(seed + 9082741L);
    climateWarpZ = new PerlinNoise(seed + 5738291L);
  }

  /**
   * Generates voxel data for one chunk.
   *
   * @param chunk chunk to fill
   * @param pos global chunk position
   */
  public void generateChunk(Chunk chunk, ChunkPos pos) {
    int baseWorldX = pos.x() * Chunk.SIZE_X;
    int baseWorldZ = pos.z() * Chunk.SIZE_Z;

    for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
      for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
        int worldX = baseWorldX + lx;
        int worldZ = baseWorldZ + lz;
        double[] climateXZ = warpClimateCoords(worldX, worldZ);
        double continentalness =
            sampleClimate(continentalNoise, climateXZ[0], climateXZ[1], CONTINENT_SCALE);
        double moisture =
            stretchClimate(
                sampleClimate(moistureNoise, climateXZ[0], climateXZ[1], CLIMATE_SCALE));

        double hillRise = sampleHillRise(worldX, worldZ);

        // Continuous height: land rolls into the sea; hills ease in/out with the mask.
        double height =
            sampleGround(worldX, worldZ, continentalness)
                + hillRise * 30.0
                - coastDrop(continentalness)
                - lakeBasin(moisture, continentalness)
                - riverCarve(worldX, worldZ, moisture, continentalness);

        int surfaceY = clamp((int) Math.round(height), 1, Chunk.SIZE_Y - 2);
        boolean flooded = surfaceY < SEA_LEVEL;

        Biome biome = pickSurfaceBiome(worldX, worldZ);

        Block surface = biome.getSurfaceBlock();
        Block subsurface = biome.getSubsurfaceBlock();
        // Beaches where land meets the waterline.
        if (!flooded && surfaceY <= SEA_LEVEL + 2 && continentalness < 0.12) {
          surface = BlockRegistry.getInstance().get(BlockIds.SAND);
          subsurface = surface;
        } else if (flooded && !biome.isAquatic()) {
          surface = BlockRegistry.getInstance().get(BlockIds.SAND);
          subsurface = BlockRegistry.getInstance().get(BlockIds.DIRT);
        }

        byte stoneId = BlockIds.STONE;
        byte waterId = BlockIds.WATER;

        for (int y = 0; y < Chunk.SIZE_Y; y++) {
          byte id;
          if (y == 0) {
            id = stoneId;
          } else if (y > surfaceY) {
            if (flooded && y <= SEA_LEVEL) {
              id = waterId;
            } else {
              id = BlockIds.AIR;
            }
          } else if (y == surfaceY) {
            id = surface.getId();
          } else if (y >= surfaceY - 3) {
            id = subsurface.getId();
          } else {
            id = stoneId;
          }
          chunk.setBlock(lx, y, lz, id);
        }

        boolean canFitTree =
            lx >= 2 && lx < Chunk.SIZE_X - 2 && lz >= 2 && lz < Chunk.SIZE_Z - 2;
        boolean onTreeCell =
            Math.floorMod(worldX, 4) == 1 && Math.floorMod(worldZ, 4) == 1;
        // Trees only in grassy forests — never desert, beaches, plains, or hills.
        if (!flooded
            && "Forest".equals(biome.getName())
            && surface.getId() == BlockIds.GRASS
            && biome.treeChance() > 0.0
            && canFitTree
            && onTreeCell
            && surfaceY >= SEA_LEVEL
            && shouldPlaceTree(worldX, worldZ, biome.treeChance())) {
          placeTree(chunk, lx, surfaceY + 1, lz);
        }
      }
    }
    chunk.setDirty(true);
  }

  /**
   * Picks a land/ocean biome from continuous Perlin climate (same world seed), with a light
   * majority filter so desert/forest stay large without square cell edges.
   */
  private Biome pickSurfaceBiome(int worldX, int worldZ) {
    int desert = 0;
    int forest = 0;
    int plains = 0;
    int hills = 0;
    int ocean = 0;
    int lake = 0;
    int river = 0;

    for (int dz = -BIOME_BLEND_RADIUS; dz <= BIOME_BLEND_RADIUS; dz += BIOME_BLEND_STEP) {
      for (int dx = -BIOME_BLEND_RADIUS; dx <= BIOME_BLEND_RADIUS; dx += BIOME_BLEND_STEP) {
        Biome b = rawBiomeAt(worldX + dx, worldZ + dz);
        switch (b.getName()) {
          case "Desert" -> desert++;
          case "Forest" -> forest++;
          case "Hills" -> hills++;
          case "Ocean" -> ocean++;
          case "Lake" -> lake++;
          case "River" -> river++;
          default -> plains++;
        }
      }
    }

    int land = desert + forest + plains + hills;
    if (ocean > land && ocean >= lake && ocean >= river) {
      return BiomeRegistry.ocean();
    }
    if (lake > land && lake >= river) {
      return BiomeRegistry.lake();
    }
    if (river > land) {
      return BiomeRegistry.river();
    }

    int totalLand = Math.max(1, desert + forest + plains + hills);
    int need = Math.max(4, (totalLand * 2) / 3);

    if (desert >= need && desert >= plains && desert >= hills && desert >= forest) {
      return BiomeRegistry.desert();
    }
    if (desert > forest && desert >= plains && desert * 2 >= need) {
      return BiomeRegistry.desert();
    }
    if (forest >= need && forest >= plains && forest >= hills && desert * 3 <= forest) {
      return BiomeRegistry.forest();
    }
    if (hills >= need && hills >= plains && desert * 2 <= hills) {
      return BiomeRegistry.hills();
    }
    // Prefer hills when they are a solid plurality (even if plains still has votes).
    if (hills > plains && hills > forest && hills > desert && hills * 2 >= need) {
      return BiomeRegistry.hills();
    }

    Biome local = rawBiomeAt(worldX, worldZ);
    if ("Desert".equals(local.getName())) {
      return BiomeRegistry.desert();
    }
    if ("Hills".equals(local.getName())) {
      return BiomeRegistry.hills();
    }
    if ("Forest".equals(local.getName()) && desert == 0) {
      return BiomeRegistry.forest();
    }
    return BiomeRegistry.plains();
  }

  /**
   * Continuous Perlin climate at this column (domain-warped). All noises are seeded from the world
   * seed so the same seed always yields the same biomes.
   */
  private Biome rawBiomeAt(int worldX, int worldZ) {
    double[] warped = warpClimateCoords(worldX, worldZ);
    double temperature = sampleClimate(temperatureNoise, warped[0], warped[1], CLIMATE_SCALE);
    double moisture =
        stretchClimate(sampleClimate(moistureNoise, warped[0], warped[1], CLIMATE_SCALE));
    double continentalness =
        sampleClimate(continentalNoise, warped[0], warped[1], CONTINENT_SCALE);
    double hillsFactor = clamp01(sampleHillRise(worldX, worldZ) / 0.28);
    return BiomeRegistry.pickBiome(temperature, moisture, continentalness, hillsFactor);
  }

  /** Offsets sample position with seeded Perlin so biome borders look organic, not grid-aligned. */
  private double[] warpClimateCoords(int worldX, int worldZ) {
    double wx =
        worldX
            + climateWarpX.octaveNoise(worldX * 0.0025, worldZ * 0.0025, 3, 0.5) * CLIMATE_WARP;
    double wz =
        worldZ
            + climateWarpZ.octaveNoise(worldX * 0.0025 + 19.7, worldZ * 0.0025 - 7.3, 3, 0.5)
                * CLIMATE_WARP;
    return new double[] {wx, wz};
  }

  /**
   * Soft continental shelf: 0 inland, rising toward deep ocean so the bed slopes under the waves.
   */
  private static double coastDrop(double continentalness) {
    double t = clamp((-0.02 - continentalness) / 0.72, 0.0, 1.0);
    t = t * t * (3.0 - 2.0 * t);
    return t * 16.0;
  }

  /** Soft inland basins for lakes — deepest at high moisture, never a hard cliff. */
  private static double lakeBasin(double moisture, double continentalness) {
    if (continentalness < -0.15) {
      return 0.0;
    }
    double wet = clamp((moisture - 0.42) / 0.45, 0.0, 1.0);
    wet = wet * wet;
    return wet * 7.5;
  }

  /** Narrow winding river channels carved into moist lowlands. */
  private double riverCarve(
      int worldX, int worldZ, double moisture, double continentalness) {
    if (continentalness < -0.2 || moisture < 0.2) {
      return 0.0;
    }
    double ridge = Math.abs(riverNoise.octaveNoise(worldX * 0.012, worldZ * 0.012, 2, 0.5));
    double channel = clamp(1.0 - ridge / 0.14, 0.0, 1.0);
    channel = channel * channel;
    double wet = clamp((moisture + 0.2) / 0.8, 0.0, 1.0);
    return channel * wet * 5.0;
  }

  /** Shared ground height — gentle rolls; shelf plunge is applied separately. */
  private double sampleGround(int worldX, int worldZ, double continentalness) {
    double roll = heightNoise.octaveNoise(worldX * 0.007, worldZ * 0.007, 3, 0.5);
    double detail = detailNoise.octaveNoise(worldX * 0.022, worldZ * 0.022, 2, 0.5);
    return 64.0 + continentalness * 2.5 + roll * 4.0 + detail * 2.0;
  }

  /**
   * Soft hill rise that eases to zero at the edges of hill clusters so slopes meet the surrounding
   * plains/desert continuously (no floating mesa lips).
   */
  private double sampleHillRise(int worldX, int worldZ) {
    // Low-frequency mask: wider hill ranges so they show up often while exploring.
    double mask = hillMaskNoise.octaveNoise(worldX * 0.0022, worldZ * 0.0022, 3, 0.5);
    double maskWeight = clamp01((mask + 0.12) / 0.45);
    if (maskWeight <= 0.0) {
      return 0.0;
    }
    maskWeight = maskWeight * maskWeight * (3.0 - 2.0 * maskWeight);

    // Mid-frequency ridges inside the mask — rounded, not cliffy.
    double ridge = ridgeNoise.octaveNoise(worldX * 0.007, worldZ * 0.007, 4, 0.5);
    double above = Math.max(0.0, ridge + 0.05);
    double rise = above * above * 1.15;
    return rise * maskWeight;
  }

  private static double sampleClimate(
      PerlinNoise noise, double worldX, double worldZ, double scale) {
    return noise.octaveNoise(worldX * scale, worldZ * scale, 3, 0.5);
  }

  /** Mild stretch so desert/forest get area without wiping out plains. */
  private static double stretchClimate(double value) {
    double signed = Math.copySign(Math.pow(Math.abs(value), 0.75), value);
    return clamp(signed * 1.12, -1.0, 1.0);
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /**
   * Deterministic [0,1) roll per column so {@code chance} is an actual probability. Avoids Perlin
   * (bell-shaped around 0.5) and large-seed float precision issues.
   */
  private boolean shouldPlaceTree(int worldX, int worldZ, double chance) {
    if (chance <= 0.0) {
      return false;
    }
    long h = seed ^ 0x9E3779B97F4A7C15L;
    h ^= (long) worldX * 341873128712L;
    h ^= (long) worldZ * 132897987541L;
    h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
    h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
    h = h ^ (h >>> 31);
    double roll = (h & 0xFFFFFFFL) / (double) 0x10000000L;
    return roll < chance;
  }

  /** Places a 1-wide trunk under the exact center of a 3×3 leaf canopy. */
  private static void placeTree(Chunk chunk, int lx, int baseY, int lz) {
    byte wood = BlockIds.WOOD;
    byte leaves = BlockIds.LEAVES;
    int trunkHeight = 5;
    int leafBottom = baseY + trunkHeight - 2;
    for (int dy = 0; dy < 3; dy++) {
      int y = leafBottom + dy;
      if (y >= Chunk.SIZE_Y) {
        break;
      }
      for (int dz = -1; dz <= 1; dz++) {
        for (int dx = -1; dx <= 1; dx++) {
          int x = lx + dx;
          int z = lz + dz;
          if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z) {
            continue;
          }
          byte existing = chunk.getBlock(x, y, z);
          if (existing == BlockIds.AIR || existing == BlockIds.LEAVES) {
            chunk.setBlock(x, y, z, leaves);
          }
        }
      }
    }
    for (int dy = 0; dy < trunkHeight && baseY + dy < Chunk.SIZE_Y; dy++) {
      chunk.setBlock(lx, baseY + dy, lz, wood);
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
