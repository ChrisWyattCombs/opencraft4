package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Inland lake basin filled to sea level. */
public final class LakeBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Lake";
  }

  @Override
  public Block getSurfaceBlock() {
    return BLOCKS.get(BlockIds.SAND);
  }

  @Override
  public Block getSubsurfaceBlock() {
    return BLOCKS.get(BlockIds.DIRT);
  }

  @Override
  public int baseHeight() {
    return 58;
  }

  @Override
  public int heightVariation() {
    return 4;
  }

  @Override
  public double treeChance() {
    return 0.02;
  }

  @Override
  public boolean isAquatic() {
    return true;
  }
}
