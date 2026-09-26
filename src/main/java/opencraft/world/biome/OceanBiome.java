package opencraft.world.biome;

import opencraft.world.block.Block;
import opencraft.world.block.BlockIds;
import opencraft.world.block.BlockRegistry;

/** Deep ocean with sand floor below sea level. */
public final class OceanBiome extends Biome {

  private static final BlockRegistry BLOCKS = BlockRegistry.getInstance();

  @Override
  public String getName() {
    return "Ocean";
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
    return 48;
  }

  @Override
  public int heightVariation() {
    return 8;
  }

  @Override
  public double treeChance() {
    return 0.0;
  }

  @Override
  public boolean isAquatic() {
    return true;
  }
}
