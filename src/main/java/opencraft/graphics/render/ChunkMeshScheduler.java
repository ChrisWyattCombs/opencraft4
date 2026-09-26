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
import opencraft.world.World;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;

/** Background chunk meshing pool. Snapshots voxels on the main thread, builds off-thread. */
public final class ChunkMeshScheduler implements AutoCloseable {

  private final ExecutorService pool;
  private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();
  private final Set<ChunkPos> inFlight = ConcurrentHashMap.newKeySet();
  private final AtomicInteger inFlightCount = new AtomicInteger();

  /** Creates a scheduler with {@code threads} worker threads. */
  public ChunkMeshScheduler(int threads) {
    int n = Math.max(1, threads);
    pool =
        Executors.newFixedThreadPool(
            n,
            r -> {
              Thread t = new Thread(r, "chunk-mesh");
              t.setDaemon(true);
              return t;
            });
  }

  /**
   * Snapshots a chunk (+ neighbors) and queues a background mesh build if not already in flight.
   *
   * @param world world for neighbor loads
   * @param pos chunk to mesh
   * @param atlas texture atlas (immutable after init)
   * @return {@code true} if a new job was submitted
   */
  public boolean submit(World world, ChunkPos pos, TextureAtlas atlas) {
    if (!inFlight.add(pos)) {
      return false;
    }
    Chunk chunk = world.ensureChunkLoaded(pos);
    byte[] blocks = chunk.getBlockData();
    chunk.setDirty(false);
    byte[] negX = copyNeighbor(world, new ChunkPos(pos.x() - 1, pos.z()));
    byte[] posX = copyNeighbor(world, new ChunkPos(pos.x() + 1, pos.z()));
    byte[] negZ = copyNeighbor(world, new ChunkPos(pos.x(), pos.z() - 1));
    byte[] posZ = copyNeighbor(world, new ChunkPos(pos.x(), pos.z() + 1));
    ChunkMesher.ChunkSnapshot snap =
        new ChunkMesher.ChunkSnapshot(pos, blocks, negX, posX, negZ, posZ);
    inFlightCount.incrementAndGet();
    pool.execute(
        () -> {
          try {
            completed.add(new Completed(pos, ChunkMesher.build(snap, atlas)));
          } catch (Throwable t) {
            System.err.println("[Opencraft] mesh failed " + pos + ": " + t);
            t.printStackTrace();
            completed.add(
                new Completed(pos, new ChunkMesher.MeshData(new float[0], new int[0], new float[0], new int[0])));
          } finally {
            inFlight.remove(pos);
            inFlightCount.decrementAndGet();
          }
        });
    return true;
  }

  /**
   * Drains up to {@code max} completed meshes.
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

  /** @return number of builds currently running or queued in the pool sense */
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

  private static byte[] copyNeighbor(World world, ChunkPos pos) {
    Chunk c = world.getLoadedChunk(pos);
    return c == null ? null : c.getBlockData();
  }

  /**
   * A finished mesh build.
   *
   * @param pos chunk position
   * @param data mesh data
   */
  public record Completed(ChunkPos pos, ChunkMesher.MeshData data) {}
}
