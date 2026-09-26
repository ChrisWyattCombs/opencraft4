package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Wooded biome with moderate tree cover. */
public final class ForestBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Forest";
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
    return 66;
  }

  @Override
  public int heightVariation() {
    return 10;
  }

  @Override
  public double treeChance() {
    // Only one candidate column per 4×4 cell; keep forests filled.
    return 0.7;
  }
}
