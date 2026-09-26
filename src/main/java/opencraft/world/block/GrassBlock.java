package opencraft.world.block;

/** Surface grass with dirt underneath in most overworld biomes. */
public final class GrassBlock extends Block {

  /** Creates a grass block. */
  public GrassBlock() {
    super(BlockIds.GRASS, "Grass");
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
    return "textures/blocks/grass_top.png";
  }

  @Override
  public String getTextureSide() {
    return "textures/blocks/grass_side.png";
  }

  @Override
  public String getTextureBottom() {
    return "textures/blocks/dirt.png";
  }
}
