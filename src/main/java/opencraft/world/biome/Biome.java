package opencraft.world.biome;

import opencraft.world.block.Block;

/** Describes surface composition and terrain shaping for a geographic biome. */
public abstract class Biome {

  /**
   * Returns the display name of this biome.
   *
   * @return biome name
   */
  public abstract String getName();

  /**
   * Returns the block used for the top surface layer.
   *
   * @return surface block
   */
  public abstract Block getSurfaceBlock();

  /**
   * Returns the block used beneath the surface layer.
   *
   * @return subsurface block
   */
  public abstract Block getSubsurfaceBlock();

  /**
   * Returns the sea level used when filling this biome with water.
   *
   * @return water surface height in blocks
   */
  public int waterLevel() {
    return 62;
  }

  /**
   * Returns whether terrain below sea level should be filled with water.
   *
   * @return {@code true} for oceans, rivers, and lakes
   */
  public boolean isAquatic() {
    return false;
  }

  /**
   * Returns the average terrain height offset for this biome.
   *
   * @return base height in blocks
   */
  public abstract int baseHeight();

  /**
   * Returns the vertical noise amplitude for terrain in this biome.
   *
   * @return height variation in blocks
   */
  public abstract int heightVariation();

  /**
   * Returns the probability [0,1] of placing a tree at a suitable surface column.
   *
   * @return tree spawn chance
   */
  public abstract double treeChance();
}
