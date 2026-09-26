package opencraft.game;

import static org.lwjgl.glfw.GLFW.GLFW_CURSOR;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_DISABLED;
import static org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_D;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F11;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_S;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_W;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static org.lwjgl.glfw.GLFW.glfwSetInputMode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import opencraft.graphics.Display;
import opencraft.graphics.render.TextureAtlas;
import opencraft.graphics.render.WorldRenderer;
import opencraft.player.Player;
import opencraft.world.World;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;

/** Active gameplay session with world rendering and player control. */
public final class GameSession implements AutoCloseable {

  private final Display display;
  private final World world;
  private final Player player;
  private final WorldRenderer worldRenderer;

  /** Start small so the first frames leave the loading screen immediately; grows while playing. */
  private int renderDistance = 4;

  private int targetRenderDistance = 16;

  private double lastMouseX;
  private double lastMouseY;
  private boolean mouseInitialized;
  private boolean flyKeyWasDown;
  private boolean fullscreenKeyWasDown;
  private boolean escapeKeyWasDown;
  private boolean returnToMenuRequested;

  private int unloadTimer;

  private GameSession(Display display, World world, Player player, WorldRenderer worldRenderer) {
    this.display = display;
    this.world = world;
    this.player = player;
    this.worldRenderer = worldRenderer;
  }

  /**
   * Creates a session and initializes the world renderer.
   *
   * @param display display with Vulkan resources
   * @param world loaded world
   * @return new session
   */
  public static GameSession create(Display display, World world) {
    return create(display, world, TextureAtlas.createFromBlocks());
  }

  /**
   * Creates a session using a prebuilt texture atlas (avoids hitching on the loading screen).
   *
   * @param display display with Vulkan resources
   * @param world loaded world
   * @param atlas block texture atlas
   * @return new session
   */
  public static GameSession create(Display display, World world, TextureAtlas atlas) {
    display.getVulkanContext().waitIdle();
    WorldRenderer renderer =
        new WorldRenderer(
            display.getGameWindow(), display.getVulkanContext(), display.getSwapchain(), display);
    renderer.init(atlas);
    return attach(display, world, renderer);
  }

  /**
   * Attaches a prewarmed world renderer to a newly generated world.
   *
   * @param display display with Vulkan resources
   * @param world loaded world
   * @param renderer already-initialized world renderer
   * @return new session
   */
  public static GameSession attach(Display display, World world, WorldRenderer renderer) {
    System.out.println("[Opencraft] session: attach world...");
    renderer.setWorld(world);
    Player player = new Player(world);
    placeOnSurface(world, player);
    System.out.println("[Opencraft] session: mesh spawn...");
    renderer.syncChunks(player, 2);
    // Async builders need a moment to finish the spawn ring.
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (renderer.getMeshCount() < 9 && System.nanoTime() < deadline) {
      try {
        Thread.sleep(15);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
      renderer.syncChunks(player, 2);
    }
    System.out.println("[Opencraft] session: meshes=" + renderer.getMeshCount());
    GameSession session = new GameSession(display, world, player, renderer);
    long window = display.getWindowHandle();
    glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    System.out.println("[Opencraft] session: ready");
    return session;
  }

  private static void placeOnSurface(World world, Player player) {
    world.ensureChunkLoaded(new ChunkPos(0, 0));
    int surface = 64;
    for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
      if (world.getBlock(0, y, 0).isSolid()) {
        surface = y + 1;
        break;
      }
    }
    player.setPosition(0.5, surface + 0.1, 0.5);
  }

  /**
   * Returns the world being played.
   *
   * @return world
   */
  public World getWorld() {
    return world;
  }

  /**
   * Returns the local player.
   *
   * @return player
   */
  public Player getPlayer() {
    return player;
  }

  /**
   * Returns the render distance in chunks.
   *
   * @return chunk radius
   */
  public int getRenderDistance() {
    return renderDistance;
  }

  /**
   * Loads every chunk within render distance of spawn without touching the GPU.
   *
   * @param world world to populate
   * @param renderDistance chunk Chebyshev radius
   * @param progress callback receiving 0–100
   */
  public static void loadChunksAroundSpawn(World world, int renderDistance, IntConsumer progress) {
    List<ChunkPos> positions = new ArrayList<>();
    for (int cx = -renderDistance; cx <= renderDistance; cx++) {
      for (int cz = -renderDistance; cz <= renderDistance; cz++) {
        if (Math.max(Math.abs(cx), Math.abs(cz)) <= renderDistance) {
          positions.add(new ChunkPos(cx, cz));
        }
      }
    }
    int total = positions.size();
    for (int i = 0; i < total; i++) {
      world.ensureChunkLoaded(positions.get(i));
      if (progress != null) {
        progress.accept((i + 1) * 100 / total);
      }
    }
  }

  /** No-op: meshes stream in during {@link #update}; keep create→play instant. */
  public void syncMeshesAroundPlayer() {}

  /**
   * Generates every chunk within render distance of spawn (0, 0).
   *
   * @param progress callback receiving 0–100 as generation proceeds
   */
  public void generateAroundSpawn(IntConsumer progress) {
    loadChunksAroundSpawn(world, renderDistance, progress);
    syncMeshesAroundPlayer();
  }

  /**
   * Updates player movement and chunk meshes.
   *
   * @param deltaSeconds frame delta in seconds
   */
  public void update(float deltaSeconds) {
    long window = display.getWindowHandle();
    int input = 0;
    if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
      input |= Player.INPUT_FORWARD;
    }
    if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
      input |= Player.INPUT_BACKWARD;
    }
    if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
      input |= Player.INPUT_LEFT;
    }
    if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
      input |= Player.INPUT_RIGHT;
    }
    if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
      input |= Player.INPUT_JUMP;
    }
    if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS
        || glfwGetKey(window, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS) {
      input |= Player.INPUT_SNEAK;
    }

    boolean flyDown = glfwGetKey(window, GLFW_KEY_F) == GLFW_PRESS;
    if (flyDown && !flyKeyWasDown) {
      player.setFlying(!player.isFlying());
    }
    flyKeyWasDown = flyDown;

    boolean escapeDown = glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS;
    if (escapeDown && !escapeKeyWasDown) {
      returnToMenuRequested = true;
    }
    escapeKeyWasDown = escapeDown;

    boolean fullscreenDown = glfwGetKey(window, GLFW_KEY_F11) == GLFW_PRESS;
    if (fullscreenDown && !fullscreenKeyWasDown) {
      display.getGameWindow().toggleFullscreen();
    }
    fullscreenKeyWasDown = fullscreenDown;

    double[] x = new double[1];
    double[] y = new double[1];
    glfwGetCursorPos(window, x, y);
    if (!mouseInitialized) {
      lastMouseX = x[0];
      lastMouseY = y[0];
      mouseInitialized = true;
    } else {
      float dx = (float) (x[0] - lastMouseX);
      float dy = (float) (y[0] - lastMouseY);
      lastMouseX = x[0];
      lastMouseY = y[0];
      player.setLook(player.getYaw() + dx * 0.15f, player.getPitch() + dy * 0.15f);
    }

    player.update(deltaSeconds, input);
    if (renderDistance < targetRenderDistance) {
      renderDistance++;
    }
    worldRenderer.syncChunks(player, renderDistance);

    // Periodically save + unload far chunks (hysteresis keeps border stable).
    if (++unloadTimer >= 20) {
      unloadTimer = 0;
      int pcx = Math.floorDiv((int) Math.floor(player.getX()), Chunk.SIZE_X);
      int pcz = Math.floorDiv((int) Math.floor(player.getZ()), Chunk.SIZE_Z);
      world.unloadFarChunks(new ChunkPos(pcx, pcz), renderDistance + 2);
    }
  }

  /** Renders the world from the player camera. */
  public void render() {
    worldRenderer.render(player);
  }

  /**
   * Forwards an Ultralight HUD bitmap to the world renderer.
   *
   * @param pixels BGRA pixels or null
   * @param width width
   * @param height height
   * @param rowBytes stride
   */
  public void setHudOverlay(ByteBuffer pixels, int width, int height, int rowBytes) {
    worldRenderer.setHudOverlay(pixels, width, height, rowBytes);
  }

  /**
   * Returns resident GPU chunk mesh count for the HUD.
   *
   * @return mesh count
   */
  public int getMeshCount() {
    return worldRenderer.getMeshCount();
  }

  /**
   * Returns and clears whether the player pressed Escape to leave to the main menu.
   *
   * @return {@code true} once per Escape press
   */
  public boolean consumeReturnToMenuRequest() {
    boolean requested = returnToMenuRequested;
    returnToMenuRequested = false;
    return requested;
  }

  /**
   * Saves the world and writes a screenshot next to world data.
   *
   * @throws IOException if saving fails
   */
  public void saveAndScreenshot() throws IOException {
    world.saveAll();
    Path screenshot = world.getWorldDir().resolve("screenshot.png");
    worldRenderer.captureScreenshot(screenshot);
  }

  /** Releases renderer resources and restores the cursor. */
  @Override
  public void close() {
    glfwSetInputMode(display.getWindowHandle(), GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    worldRenderer.close();
  }
}
