package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Flat grassland without trees. */
public final class PlainsBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Plains";
  }

  @Override
  public Block getSurfaceBlock() {
    return BLOCKS.get(BlockIds.GRASS);
  }

  @Override
  public Block getSubsurfaceBlock() {
    return BLOCKS.get(BlockIds.DIRT);
  }

  @Override
  public int baseHeight() {
    return 64;
  }

  @Override
  public int heightVariation() {
    return 6;
  }

  @Override
  public double treeChance() {
    return 0.0;
  }
}
