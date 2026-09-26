package opencraft.world.block;

/** Deep underground stone. */
public final class StoneBlock extends Block {

  /** Creates a stone block. */
  public StoneBlock() {
    super(BlockIds.STONE, "Stone");
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
    return "textures/blocks/stone.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/stone.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/stone.png";
  }
}
