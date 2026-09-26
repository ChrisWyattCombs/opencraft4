package opencraft.game;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import opencraft.AppDirectories;
import opencraft.graphics.Display;
import opencraft.graphics.render.ShaderCompiler;
import opencraft.graphics.render.TextureAtlas;
import opencraft.graphics.render.WorldRenderer;
import opencraft.ui.UltralightGui;
import opencraft.world.World;
import opencraft.world.WorldIO;
import opencraft.world.chunk.ChunkPos;

/** High-level application states and transitions between menu and gameplay. */
public final class GameController implements AutoCloseable {

  /** Application phase for menu, loading, and in-world play. */
  public enum Phase {
    /** Main menu and Ultralight UI. */
    MENU,
    /** Waiting to hand off to the world renderer. */
    STARTING,
    /** Active world rendering. */
    PLAYING
  }

  private final Display display;
  private final Path projectRoot;
  private final Path worldsRoot;
  private UltralightGui gui;
  private WorldRenderer worldRenderer;
  private Phase phase = Phase.MENU;
  private GameSession session;
  private volatile World pendingWorld;
  private volatile String startError;
  private boolean createUsed;
  private boolean loadingUiShown;
  private long startingSinceNanos;
  private long lastWaitLogNanos;
  private float fpsSmoothed = 60f;

  /**
   * Creates a controller for the given display and save root.
   *
   * @param display Vulkan display
   * @param projectRoot project directory (unused for saves; worlds go under {@link
   *     AppDirectories#worldsDirectory()})
   */
  public GameController(Display display, Path projectRoot) {
    this.display = display;
    this.projectRoot = projectRoot;
    this.worldsRoot = AppDirectories.worldsDirectory();
  }

  /**
   * Initializes Ultralight UI and wires menu callbacks.
   *
   * @throws Exception if UI setup fails
   */
  public void init() throws Exception {
    System.out.println("[Opencraft] Warming up atlas + shaders...");
    TextureAtlas.invalidate();
    TextureAtlas atlas = TextureAtlas.get();
    ShaderCompiler.warmup();
    System.out.println("[Opencraft] Prewarming world renderer...");
    worldRenderer =
        new WorldRenderer(
            display.getGameWindow(), display.getVulkanContext(), display.getSwapchain(), display);
    worldRenderer.init(atlas);
    System.out.println("[Opencraft] Warmup done");
    gui = new UltralightGui(display, projectRoot, worldsRoot);
    gui.init();
    gui.setCreateWorldHandler(this::beginCreateWorld);
    gui.setLoadWorldHandler(this::beginLoadWorld);
  }

  /**
   * Returns the current application phase.
   *
   * @return phase
   */
  public Phase getPhase() {
    return phase;
  }

  /**
   * Polls input and advances the current phase.
   *
   * @param deltaSeconds frame time in seconds
   */
  public void tick(float deltaSeconds) {
    if (phase == Phase.PLAYING && session != null) {
      try {
        if (deltaSeconds > 1e-4f) {
          float instant = 1f / deltaSeconds;
          fpsSmoothed = fpsSmoothed * 0.9f + instant * 0.1f;
        }
        gui.updateHud(fpsSmoothed, session.getMeshCount());
        ByteBuffer hud = gui.lockHudPixels();
        try {
          if (hud != null) {
            session.setHudOverlay(
                hud, gui.hudPixelWidth(), gui.hudPixelHeight(), gui.hudRowBytes());
          } else {
            session.setHudOverlay(null, 0, 0, 0);
          }
          session.update(deltaSeconds);
          session.render();
        } finally {
          gui.unlockHudPixels();
          session.setHudOverlay(null, 0, 0, 0);
        }
        if (session.consumeReturnToMenuRequest()) {
          returnToMainMenu();
        }
      } catch (Throwable e) {
        e.printStackTrace();
        restoreMenuAfterFailedStart(e.getMessage() == null ? "Render failed" : e.getMessage());
      }
      return;
    }

    if (phase == Phase.STARTING) {
      if (startError != null) {
        restoreMenuAfterFailedStart(startError);
        return;
      }
      World world = pendingWorld;
      if (world == null) {
        long now = System.nanoTime();
        if (now - lastWaitLogNanos > 2_000_000_000L) {
          lastWaitLogNanos = now;
          System.out.println(
              "[Opencraft] waiting for world gen... "
                  + ((now - startingSinceNanos) / 1_000_000L)
                  + "ms");
        }
        // Still generating — keep showing the static loading page.
        display.ensurePresenter();
        gui.update();
        if (!loadingUiShown) {
          gui.runScript("if(window.showLoading){showLoading();}");
          loadingUiShown = true;
        }
        gui.present();
        return;
      }
      enterPlaying(world);
      return;
    }

    // MENU
    display.ensurePresenter();
    gui.update();
    gui.present();
  }

  /**
   * Starts creating a world from menu input. May only be called once until play fails back to menu.
   *
   * @param name world name
   * @param seed seed string
   */
  public void beginCreateWorld(String name, String seed) {
    if (createUsed || phase != Phase.MENU) {
      System.out.println(
          "[Opencraft] createWorld ignored (createUsed=" + createUsed + " phase=" + phase + ")");
      return;
    }
    try {
      if (WorldIO.worldNameExists(worldsRoot, name)) {
        gui.runScript(
            "if(window.showCreateError){"
                + "showCreateError(\"A world with that name already exists\");}");
        return;
      }
    } catch (IOException e) {
      gui.runScript("if(window.showCreateError){showCreateError(\"Could not check world name\");}");
      return;
    }
    startWorldLoad(
        () -> {
          System.out.println("[Opencraft] gen: createWorld...");
          World world = WorldIO.createWorld(worldsRoot, name, seed);
          System.out.println("[Opencraft] gen: world dir=" + world.getWorldDir());
          prefetchSpawn(world);
          System.out.println("[Opencraft] gen: spawn ready");
          pendingWorld = world;
        },
        name);
  }

  /**
   * Starts loading an existing world from the singleplayer list.
   *
   * @param folder world directory name under the saves root
   */
  public void beginLoadWorld(String folder) {
    if (createUsed || phase != Phase.MENU) {
      System.out.println(
          "[Opencraft] loadWorld ignored (createUsed=" + createUsed + " phase=" + phase + ")");
      return;
    }
    startWorldLoad(
        () -> {
          System.out.println("[Opencraft] gen: loadWorld folder=" + folder);
          World world = WorldIO.loadWorldByFolder(worldsRoot, folder);
          System.out.println("[Opencraft] gen: world dir=" + world.getWorldDir());
          prefetchSpawn(world);
          System.out.println("[Opencraft] gen: spawn ready");
          pendingWorld = world;
        },
        folder);
  }

  private void startWorldLoad(ThrowingRunnable work, String label) {
    createUsed = true;
    loadingUiShown = false;
    startError = null;
    pendingWorld = null;
    phase = Phase.STARTING;
    startingSinceNanos = System.nanoTime();
    lastWaitLogNanos = startingSinceNanos;
    System.out.println("[Opencraft] beginWorldLoad label=" + label);
    Thread thread =
        new Thread(
            () -> {
              try {
                work.run();
              } catch (IOException | RuntimeException e) {
                startError = e.getMessage() == null ? "World load failed" : e.getMessage();
                e.printStackTrace();
              }
            },
            "world-generation");
    thread.setDaemon(true);
    thread.start();
  }

  private static void prefetchSpawn(World world) {
    world.ensureChunkLoaded(new ChunkPos(0, 0));
    for (int cz = -1; cz <= 1; cz++) {
      for (int cx = -1; cx <= 1; cx++) {
        if (cx == 0 && cz == 0) {
          continue;
        }
        world.ensureChunkLoaded(new ChunkPos(cx, cz));
      }
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws IOException;
  }

  /** Releases gameplay session and UI. */
  @Override
  public void close() {
    if (session != null) {
      try {
        session.saveAndScreenshot();
      } catch (IOException e) {
        System.err.println("Failed to save on exit: " + e.getMessage());
      }
      session.close();
      session = null;
    } else if (worldRenderer != null) {
      worldRenderer.close();
      worldRenderer = null;
    }
    if (gui != null) {
      gui.close();
      gui = null;
    }
  }

  private void enterPlaying(World world) {
    pendingWorld = null;
    System.out.println("[Opencraft] Handoff: attach prewarmed renderer...");
    try {
      display.ensurePresenter();
      gui.update();
      gui.present();

      session = GameSession.attach(display, world, worldRenderer);
      worldRenderer = null;

      System.out.println("[Opencraft] Handoff: release UI presenter for GPU blit...");
      display.releasePresenter();
      // Ensure no in-flight UI presents race the first world acquire.
      display.getVulkanContext().waitIdle();
      System.out.println("[Opencraft] First world frame...");
      session.update(0f);
      session.render();
      phase = Phase.PLAYING;
      System.out.println("[Opencraft] Entered PLAYING");
    } catch (Throwable e) {
      e.printStackTrace();
      if (session != null) {
        session = null;
      }
      restoreMenuAfterFailedStart(e.getMessage() == null ? "Failed to start" : e.getMessage());
    }
  }

  /** Saves the active world, captures a menu icon screenshot, and returns to the main menu. */
  private void returnToMainMenu() {
    System.out.println("[Opencraft] Escape → save + main menu");
    if (session != null) {
      try {
        session.saveAndScreenshot();
      } catch (IOException e) {
        System.err.println("[Opencraft] Failed to save on Escape: " + e.getMessage());
      }
      try {
        session.close();
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
      session = null;
    }
    phase = Phase.MENU;
    createUsed = false;
    startError = null;
    pendingWorld = null;
    loadingUiShown = false;
    if (worldRenderer == null) {
      try {
        worldRenderer =
            new WorldRenderer(
                display.getGameWindow(),
                display.getVulkanContext(),
                display.getSwapchain(),
                display);
        worldRenderer.init(TextureAtlas.get());
      } catch (RuntimeException e) {
        e.printStackTrace();
        display.requestClose();
        return;
      }
    }
    try {
      display.ensurePresenter();
      gui.loadPage("main.html");
      gui.update();
      gui.present();
    } catch (Throwable e) {
      e.printStackTrace();
      display.requestClose();
    }
  }

  private void restoreMenuAfterFailedStart(String message) {
    phase = Phase.MENU;
    createUsed = false;
    startError = null;
    pendingWorld = null;
    if (session != null) {
      worldRenderer = null;
      try {
        session.close();
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
      session = null;
    }
    // Device lost (-4) cannot recover into Ultralight present — exit cleanly instead.
    if (message != null && (message.contains("-4") || message.toLowerCase().contains("device"))) {
      System.err.println("[Opencraft] GPU device lost; closing. " + message);
      display.requestClose();
      return;
    }
    if (worldRenderer == null) {
      try {
        worldRenderer =
            new WorldRenderer(
                display.getGameWindow(),
                display.getVulkanContext(),
                display.getSwapchain(),
                display);
        worldRenderer.init(TextureAtlas.get());
      } catch (RuntimeException e) {
        e.printStackTrace();
        display.requestClose();
        return;
      }
    }
    try {
      display.ensurePresenter();
      gui.update();
      gui.runScript("alert(" + jsonString(message) + ");");
      gui.present();
    } catch (Throwable e) {
      e.printStackTrace();
      display.requestClose();
    }
  }

  private static String jsonString(String value) {
    if (value == null) {
      return "\"\"";
    }
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
