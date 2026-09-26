package opencraft.world.block;

/** Tree foliage; non-opaque for simple transparency. */
public final class LeavesBlock extends Block {

  /** Creates a leaves block. */
  public LeavesBlock() {
    super(BlockIds.LEAVES, "Leaves");
  }

  @Override
  public boolean isOpaque() {
    return false;
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
    return "textures/blocks/leaves.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/leaves.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/leaves.png";
  }
}
