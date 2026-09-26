package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Elevated grassy hills without trees. */
public final class HillsBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Hills";
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
    return 78;
  }

  @Override
  public int heightVariation() {
    return 18;
  }

  @Override
  public double treeChance() {
    return 0.0;
  }
}
