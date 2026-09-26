package opencraft.graphics.render;

import opencraft.player.Player;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;

/** Frustum tests for chunk AABBs so off-screen columns are not drawn or meshed eagerly. */
public final class ChunkFrustum {

  private final FrustumIntersection frustum = new FrustumIntersection();
  private final Matrix4f mvp = new Matrix4f();

  /**
   * Updates the frustum from the current camera matrices.
   *
   * @param projView projection × view matrix
   */
  public void update(Matrix4f projView) {
    mvp.set(projView);
    frustum.set(mvp);
  }

  /**
   * Returns whether a chunk column may intersect the view frustum.
   *
   * @param pos chunk position
   * @return {@code true} if the chunk AABB is not fully outside the frustum
   */
  public boolean testChunk(ChunkPos pos) {
    float minX = pos.x() * Chunk.SIZE_X;
    float minZ = pos.z() * Chunk.SIZE_Z;
    float maxX = minX + Chunk.SIZE_X;
    float maxZ = minZ + Chunk.SIZE_Z;
    // Full column height — cheap and conservative.
    return frustum.testAab(minX, 0f, minZ, maxX, (float) Chunk.SIZE_Y, maxZ);
  }

  /**
   * Builds a projection-view matrix matching {@link WorldRenderer} camera conventions.
   *
   * @param player camera
   * @param aspect width/height
   * @param out destination matrix
   * @return {@code out}
   */
  public static Matrix4f buildProjView(Player player, float aspect, Matrix4f out) {
    Matrix4f proj =
        new Matrix4f()
            .perspective((float) Math.toRadians(70.0), aspect, 0.05f, 512f)
            .scale(1f, -1f, 1f);
    double[] eyeArr = player.getEyePosition();
    float ex = (float) eyeArr[0];
    float ey = (float) eyeArr[1];
    float ez = (float) eyeArr[2];
    double yawRad = Math.toRadians(player.getYaw());
    double pitchRad = Math.toRadians(player.getPitch());
    float cx = ex - (float) (Math.sin(yawRad) * Math.cos(pitchRad));
    float cy = ey - (float) Math.sin(pitchRad);
    float cz = ez + (float) (Math.cos(yawRad) * Math.cos(pitchRad));
    Matrix4f view = new Matrix4f().lookAt(ex, ey, ez, cx, cy, cz, 0f, 1f, 0f);
    return proj.mul(view, out);
  }
}
