package opencraft.world.block;

/** Common subsurface and underground fill. */
public final class DirtBlock extends Block {

  /** Creates a dirt block. */
  public DirtBlock() {
    super(BlockIds.DIRT, "Dirt");
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
    return "textures/blocks/dirt.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/dirt.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/dirt.png";
  }
}
