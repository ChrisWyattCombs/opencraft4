package opencraft.world.chunk;

/**
 * Identifies a column of blocks in the horizontal plane.
 *
 * @param x chunk X index (world X divided by {@link Chunk#SIZE_X})
 * @param z chunk Z index (world Z divided by {@link Chunk#SIZE_Z})
 */
public record ChunkPos(int x, int z) {}
