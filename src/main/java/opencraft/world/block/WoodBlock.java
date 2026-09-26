package opencraft.world.block;

/** Tree trunk wood. */
public final class WoodBlock extends Block {

  /** Creates a wood block. */
  public WoodBlock() {
    super(BlockIds.WOOD, "Wood");
  }

  @Override
  public boolean isOpaque() {
    return true;
  }

  @Override
  public boolean isSolid() {
    return true;
  }

  @Override
  public boolean isLiquid() {
    return false;
  }

  @Override
  public String getTextureTop() {
    return "textures/blocks/wood_top.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/wood_side.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/wood_top.png";
  }
}
