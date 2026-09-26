package opencraft.world.block;

/** Still water fill for oceans, rivers, and lakes. */
public final class WaterBlock extends Block {

  /** Creates a water block. */
  public WaterBlock() {
    super(BlockIds.WATER, "Water");
  }

  @Override
  public boolean isOpaque() {
    return false;
  }

  @Override
  public boolean isSolid() {
    return false;
  }

  @Override
  public boolean isLiquid() {
    return true;
  }

  @Override
  public String getTextureTop() {
    return "textures/blocks/water.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/water.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/water.png";
  }
}
