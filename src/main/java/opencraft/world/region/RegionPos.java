package opencraft.world.region;

/**
 * Identifies a region file covering a grid of chunks.
 *
 * @param rx region X index
 * @param rz region Z index
 */
public record RegionPos(int rx, int rz) {}
