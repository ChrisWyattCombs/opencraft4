package opencraft.graphics.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import opencraft.world.gen.TerrainGenerator;

/**
 * Background builder for distant LOD heightmap patches.
 *
 * <p>Unlike {@link ChunkMeshScheduler}, jobs never load voxel chunks — they only sample {@link
 * TerrainGenerator}.
 */
public final class LodMeshScheduler implements AutoCloseable {

  /**
   * Identifies one LOD patch (section grid + sample step).
   *
   * @param sectionX section coordinate X
   * @param sectionZ section coordinate Z
   * @param step sample spacing in blocks
   */
  public record LodKey(int sectionX, int sectionZ, int step) {}

  /**
   * Finished LOD mesh.
   *
   * @param key patch key
   * @param data mesh data
   */
  public record Completed(LodKey key, ChunkMesher.MeshData data) {}

  private final ExecutorService pool;
  private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();
  private final Set<LodKey> inFlight = ConcurrentHashMap.newKeySet();
  private final AtomicInteger inFlightCount = new AtomicInteger();

  /**
   * Creates a scheduler with {@code threads} workers.
   *
   * @param threads worker count
   */
  public LodMeshScheduler(int threads) {
    int n = Math.max(1, threads);
    pool =
        Executors.newFixedThreadPool(
            n,
            r -> {
              Thread t = new Thread(r, "lod-mesh");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * Queues a LOD patch build if not already in flight.
   *
   * @param key patch identity
   * @param generator terrain sampler
   * @param atlas texture atlas
   * @param originX world min X
   * @param originZ world min Z
   * @param sizeBlocks patch size in blocks
   * @return {@code true} if a new job was submitted
   */
  public boolean submit(
      LodKey key,
      TerrainGenerator generator,
      TextureAtlas atlas,
      int originX,
      int originZ,
      int sizeBlocks) {
    if (!inFlight.add(key)) {
      return false;
    }
    inFlightCount.incrementAndGet();
    int step = key.step();
    pool.execute(
        () -> {
          try {
            ChunkMesher.MeshData data =
                LodMesher.build(generator, atlas, originX, originZ, sizeBlocks, step);
            completed.add(new Completed(key, data));
          } catch (Throwable t) {
            System.err.println("[Opencraft] lod mesh failed " + key + ": " + t);
            t.printStackTrace();
            completed.add(
                new Completed(
                    key,
                    new ChunkMesher.MeshData(new float[0], new int[0], new float[0], new int[0])));
          } finally {
            inFlight.remove(key);
            inFlightCount.decrementAndGet();
          }
        });
    return true;
  }

  /**
   * Drains up to {@code max} completed LOD meshes.
   *
   * @param max max results
   * @return completed builds
   */
  public List<Completed> drain(int max) {
    List<Completed> out = new ArrayList<>(Math.min(max, 16));
    Completed c;
    while (out.size() < max && (c = completed.poll()) != null) {
      out.add(c);
    }
    return out;
  }

  /**
   * @return jobs currently running or queued
   */
  public int inFlight() {
    return inFlightCount.get();
  }

  @Override
  public void close() {
    pool.shutdownNow();
    try {
      pool.awaitTermination(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    completed.clear();
    inFlight.clear();
  }
}
