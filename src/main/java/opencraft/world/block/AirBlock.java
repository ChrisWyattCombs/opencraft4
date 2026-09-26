package opencraft.world.block;

/** Empty space; default fill for unloaded or generated air. */
public final class AirBlock extends Block {

  /** Creates the singleton air block instance. */
  public AirBlock() {
    super(BlockIds.AIR, "Air");
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
    return false;
  }

  @Override
  public String getTextureTop() {
    return null;
  }

  @Override
  public String getTextureSide() {
    return null;
  }

  @Override
  public String getTextureBottom() {
    return null;
  }
}
