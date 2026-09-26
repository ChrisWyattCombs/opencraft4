package opencraft.world.biome;

/** Selects a {@link Biome} from procedural climate and elevation samples. */
public final class BiomeRegistry {

  private static final Biome OCEAN = new OceanBiome();
  private static final Biome RIVER = new RiverBiome();
  private static final Biome LAKE = new LakeBiome();
  private static final Biome PLAINS = new PlainsBiome();
  private static final Biome FOREST = new ForestBiome();
  private static final Biome DESERT = new DesertBiome();
  private static final Biome HILLS = new HillsBiome();

  /**
   * Wide plains band between desert and forest so the two never sit on adjacent moisture values.
   */
  private static final double DESERT_MAX_MOISTURE = -0.18;

  private static final double FOREST_MIN_MOISTURE = 0.42;

  private BiomeRegistry() {}

  /**
   * @return ocean biome singleton
   */
  public static Biome ocean() {
    return OCEAN;
  }

  /**
   * @return lake biome singleton
   */
  public static Biome lake() {
    return LAKE;
  }

  /**
   * @return river biome singleton
   */
  public static Biome river() {
    return RIVER;
  }

  /**
   * @return hills biome singleton
   */
  public static Biome hills() {
    return HILLS;
  }

  /**
   * @return plains biome singleton
   */
  public static Biome plains() {
    return PLAINS;
  }

  /**
   * @return forest biome singleton
   */
  public static Biome forest() {
    return FOREST;
  }

  /**
   * @return desert biome singleton
   */
  public static Biome desert() {
    return DESERT;
  }

  /**
   * Picks the most appropriate biome for the given climate and hill factor.
   *
   * <p>Desert and forest are mutually exclusive: dry/hot → desert, cool/wet → forest, everything
   * else → plains (buffer).
   *
   * @param temperature cold (-1) to hot (+1)
   * @param moisture dry (-1) to wet (+1)
   * @param continentalness oceanic (-1) to inland (+1)
   * @param hillsFactor normalized hill strength [0, 1]
   * @return selected biome
   */
  public static Biome pickBiome(
      double temperature, double moisture, double continentalness, double hillsFactor) {
    if (continentalness < -0.28) {
      return OCEAN;
    }
    if (moisture > 0.7 && continentalness < 0.2 && hillsFactor < 0.25) {
      return LAKE;
    }
    if (moisture > 0.55 && continentalness > -0.1 && continentalness < 0.35 && hillsFactor < 0.3) {
      return RIVER;
    }

    Biome climate = pickLandClimate(temperature, moisture);

    // Grassy hill biome over plains/forest — never desert.
    if (hillsFactor > 0.22 && climate != DESERT && climate != OCEAN) {
      if (climate == PLAINS || climate == FOREST) {
        return HILLS;
      }
    }
    return climate;
  }

  /**
   * Land climate with a plains buffer so desert and forest cannot share a moisture band.
   *
   * @param temperature cold (-1) to hot (+1)
   * @param moisture dry (-1) to wet (+1)
   * @return desert, forest, or plains
   */
  public static Biome pickLandClimate(double temperature, double moisture) {
    // Hot + dry always desert (blocks forest even if moisture is mildly positive).
    if (temperature > 0.35 && moisture < 0.15) {
      return DESERT;
    }
    // Dry tip → desert.
    if (moisture < DESERT_MAX_MOISTURE) {
      return DESERT;
    }
    // Forest needs both wet moisture and not-hot climate.
    if (moisture > FOREST_MIN_MOISTURE && temperature < 0.3) {
      return FOREST;
    }
    // Cool + moist tip can still be forest.
    if (temperature < -0.35 && moisture > 0.05) {
      return FOREST;
    }
    return PLAINS;
  }
}
