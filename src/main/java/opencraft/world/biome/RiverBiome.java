package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Shallow river channel at sea level. */
public final class RiverBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "River";
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
    return 60;
  }

  @Override
  public int heightVariation() {
    return 2;
  }

  @Override
  public double treeChance() {
    return 0.01;
  }

  @Override
  public boolean isAquatic() {
    return true;
  }
}
