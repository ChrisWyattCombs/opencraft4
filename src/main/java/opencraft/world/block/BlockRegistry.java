package opencraft.world.block;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Global registry mapping block ids to {@link Block} instances. */
public final class BlockRegistry {

  private static final BlockRegistry INSTANCE = new BlockRegistry();

  private final Map<Byte, Block> byId = new HashMap<>();

  static {
    BlockRegistry registry = INSTANCE;
    registry.register(new AirBlock());
    registry.register(new GrassBlock());
    registry.register(new DirtBlock());
    registry.register(new StoneBlock());
    registry.register(new SandBlock());
    registry.register(new WoodBlock());
    registry.register(new LeavesBlock());
    registry.register(new WaterBlock());
  }

  private BlockRegistry() {}

  /**
   * Returns the shared block registry.
   *
   * @return singleton instance
   */
  public static BlockRegistry getInstance() {
    return INSTANCE;
  }

  /**
   * Looks up a block by id, falling back to air when unknown.
   *
   * @param id block id
   * @return registered block or air
   */
  public Block get(byte id) {
    return byId.getOrDefault(id, getAir());
  }

  /**
   * Returns the air block instance.
   *
   * @return air block
   */
  public Block getAir() {
    return byId.get(BlockIds.AIR);
  }

  /**
   * Returns all registered blocks.
   *
   * @return unmodifiable collection of blocks
   */
  public Collection<Block> all() {
    return Collections.unmodifiableCollection(byId.values());
  }

  private void register(Block block) {
    byId.put(block.getId(), block);
  }
}
