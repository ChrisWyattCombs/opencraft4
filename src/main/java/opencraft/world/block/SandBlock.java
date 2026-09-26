package opencraft.world.block;

/** Desert and beach surface block. */
public final class SandBlock extends Block {

  /** Creates a sand block. */
  public SandBlock() {
    super(BlockIds.SAND, "Sand");
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
    return "textures/blocks/sand.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/sand.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/sand.png";
  }
}
