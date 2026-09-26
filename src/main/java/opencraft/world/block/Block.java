package opencraft.world.block;

/**
 * Base type for all placeable voxel blocks.
 *
 * <p>Subclasses define physical and rendering properties; texture path getters may return {@code
 * null} when a face reuses another atlas entry or has no texture.
 */
public abstract class Block {

  private final byte id;
  private final String name;

  /**
   * Creates a block with the given id and display name.
   *
   * @param id unique block id in the range used by {@link BlockRegistry}
   * @param name human-readable block name
   */
  protected Block(byte id, String name) {
    this.id = id;
    this.name = name;
  }

  /**
   * Returns this block's registry id.
   *
   * @return block id byte
   */
  public byte getId() {
    return id;
  }

  /**
   * Returns the display name of this block.
   *
   * @return block name
   */
  public String getName() {
    return name;
  }

  /**
   * Whether adjacent faces should be culled against this block.
   *
   * @return {@code true} if opaque for mesh culling
   */
  public abstract boolean isOpaque();

  /**
   * Whether entities and the player collide with this block.
   *
   * @return {@code true} if solid for collision
   */
  public abstract boolean isSolid();

  /**
   * Whether this block behaves as a fluid.
   *
   * @return {@code true} for liquids such as water
   */
  public abstract boolean isLiquid();

  /**
   * Resource path for the top face texture, or {@code null} if unused.
   *
   * @return texture path or {@code null}
   */
  public abstract String getTextureTop();

  /**
   * Resource path for side face textures, or {@code null} if unused.
   *
   * @return texture path or {@code null}
   */
  public abstract String getTextureSide();

  /**
   * Resource path for the bottom face texture, or {@code null} if unused.
   *
   * @return texture path or {@code null}
   */
  public abstract String getTextureBottom();
}
