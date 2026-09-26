package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Hot dry biome with sand surfaces. */
public final class DesertBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Desert";
  }

  @Override
  public Block getSurfaceBlock() {
    return BLOCKS.get(BlockIds.SAND);
  }

  @Override
  public Block getSubsurfaceBlock() {
    return BLOCKS.get(BlockIds.SAND);
  }

  @Override
  public int baseHeight() {
    return 62;
  }

  @Override
  public int heightVariation() {
    return 5;
  }

  @Override
  public double treeChance() {
    return 0.0;
  }
}
