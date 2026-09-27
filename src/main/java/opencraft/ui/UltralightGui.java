package opencraft.ui;

import com.labymedia.ultralight.UltralightJava;
import com.labymedia.ultralight.UltralightLoadException;
import com.labymedia.ultralight.UltralightPlatform;
import com.labymedia.ultralight.UltralightRenderer;
import com.labymedia.ultralight.UltralightView;
import com.labymedia.ultralight.bitmap.UltralightBitmap;
import com.labymedia.ultralight.bitmap.UltralightBitmapSurface;
import com.labymedia.ultralight.config.FontHinting;
import com.labymedia.ultralight.config.UltralightConfig;
import com.labymedia.ultralight.config.UltralightViewConfig;
import com.labymedia.ultralight.javascript.JavascriptEvaluationException;
import com.labymedia.ultralight.plugin.clipboard.UltralightClipboard;
import com.labymedia.ultralight.plugin.logging.UltralightLogLevel;
import com.labymedia.ultralight.plugin.logging.UltralightLogger;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import opencraft.graphics.Display;

/**
 * Ultralight-powered HTML UI rendered to a CPU bitmap and presented through Vulkan.
 *
 * <p>Loads menu pages from classpath resources under {@code ui/}, bridges JavaScript calls via
 * {@link MenuBridge}, and uploads each frame to {@link Display#presentBGRA}.
 */
public final class UltralightGui implements AutoCloseable {

  /** In-game HUD overlay width in pixels. */
  public static final int HUD_WIDTH = 240;

  /** In-game HUD overlay height in pixels. */
  public static final int HUD_HEIGHT = 56;

  private final Display display;
  private final Path runDir;
  private final Path uiDir;
  private final Path nativesDir;
  private final Path sdkDir;
  private final Path worldsRoot;

  private UltralightRenderer renderer;
  private UltralightView view;
  private UltralightView hudView;
  private MenuBridge bridge;
  private GuiInput input;
  private String currentPage = "main.html";
  private BiConsumer<String, String> createWorldHandler;
  private Consumer<String> loadWorldHandler;
  private int lastHudFps = -1;
  private int lastHudChunks = -1;
  private long lastHudPaintNanos;

  /** When true, skip Ultralight update/render/input — gameplay uses Vulkan only. */
  private boolean gameplayPaused;

  /**
   * Creates a GUI host bound to the given display and project root.
   *
   * @param display Vulkan/GLFW display used for presentation and input
   * @param projectRoot project root containing {@code natives/} and writable {@code run/}
   * @param worldsRoot folder containing saved worlds ({@link
   *     opencraft.AppDirectories#worldsDirectory()})
   */
  public UltralightGui(Display display, Path projectRoot, Path worldsRoot) {
    this.display = display;
    this.worldsRoot = worldsRoot;
    this.runDir = projectRoot.resolve("run");
    this.uiDir = runDir.resolve("ui");
    this.nativesDir = runDir.resolve("natives");
    Path legacy = projectRoot.resolve("natives").resolve("ultralight-legacy");
    Path modern = projectRoot.resolve("natives").resolve("ultralight");
    this.sdkDir = Files.isDirectory(legacy.resolve("bin")) ? legacy : modern;
  }

  /**
   * Extracts UI assets and Ultralight natives, configures the platform, and loads the main menu.
   *
   * @throws UltralightLoadException if the Ultralight native library fails to load
   * @throws IOException if UI assets or SDK binaries cannot be prepared
   */
  public void init() throws UltralightLoadException, IOException {
    Files.createDirectories(runDir);
    Files.createDirectories(nativesDir);
    extractUiResources();
    prepareNatives();

    String existing = System.getProperty("java.library.path");
    String nativesAbs = nativesDir.toAbsolutePath().toString();
    System.setProperty(
        "java.library.path",
        existing == null || existing.isBlank()
            ? nativesAbs
            : existing + java.io.File.pathSeparator + nativesAbs);

    UltralightJava.extractNativeLibrary(nativesDir);
    UltralightJava.load(nativesDir);

    UltralightPlatform platform = UltralightPlatform.instance();
    Path resourcesPath = sdkDir.resolve("resources");
    UltralightConfig config =
        new UltralightConfig()
            .cachePath(runDir.resolve("ultralight-cache").toAbsolutePath().toString())
            .fontHinting(FontHinting.SMOOTH)
            .forceRepaint(false);
    if (Files.isDirectory(resourcesPath)) {
      config.resourcePath(resourcesPath.toAbsolutePath().toString());
    }
    platform.setConfig(config);
    platform.usePlatformFontLoader();
    platform.usePlatformFileSystem(runDir.toAbsolutePath().toString());
    platform.setLogger(
        (UltralightLogger)
            (level, message) -> {
              if (level == UltralightLogLevel.ERROR) {
                System.err.println("[Ultralight] " + message);
              } else {
                System.out.println("[Ultralight] " + message);
              }
            });
    platform.setClipboard(
        new UltralightClipboard() {
          private String content = "";

          @Override
          public void clear() {
            content = "";
          }

          @Override
          public String readPlainText() {
            return content;
          }

          @Override
          public void writePlainText(String text) {
            content = text == null ? "" : text;
          }
        });

    renderer = UltralightRenderer.create();
    view =
        renderer.createView(
            display.getWidth(),
            display.getHeight(),
            new UltralightViewConfig()
                .isAccelerated(false)
                .isTransparent(false)
                .initialDeviceScale(1.0)
                .initialFocus(true));

    bridge = new MenuBridge(this, worldsRoot);
    view.setLoadListener(new GuiLoadListener(view, bridge));
    input = new GuiInput(display);
    input.bind(view);

    loadPage(currentPage);
    view.focus();

    hudView =
        renderer.createView(
            HUD_WIDTH,
            HUD_HEIGHT,
            new UltralightViewConfig()
                .isAccelerated(false)
                .isTransparent(false)
                .initialDeviceScale(1.0)
                .initialFocus(false));
    hudView.loadURL(uiFileUrl("hud.html"));
  }

  /**
   * Sets the handler invoked when the user confirms world creation.
   *
   * @param handler receives world name and seed text
   */
  public void setCreateWorldHandler(BiConsumer<String, String> handler) {
    this.createWorldHandler = handler;
  }

  /**
   * Sets the handler invoked when the user opens an existing world.
   *
   * @param handler receives the world folder name
   */
  public void setLoadWorldHandler(Consumer<String> handler) {
    this.loadWorldHandler = handler;
  }

  /**
   * Runs JavaScript in the active Ultralight view.
   *
   * @param script JavaScript source
   * @throws IllegalStateException if script evaluation fails
   */
  public void runScript(String script) {
    if (view != null) {
      try {
        view.evaluateScript(script);
      } catch (JavascriptEvaluationException e) {
        throw new IllegalStateException("Script evaluation failed", e);
      }
    }
  }

  /**
   * Returns the create-world handler for the menu bridge.
   *
   * @return handler or {@code null}
   */
  BiConsumer<String, String> getCreateWorldHandler() {
    return createWorldHandler;
  }

  /**
   * Returns the load-world handler for the menu bridge.
   *
   * @return handler or {@code null}
   */
  Consumer<String> getLoadWorldHandler() {
    return loadWorldHandler;
  }

  /**
   * Loads an HTML page from the extracted UI directory.
   *
   * @param page file name relative to the UI root (for example {@code main.html})
   */
  public void loadPage(String page) {
    currentPage = page;
    view.loadURL(uiFileUrl(page));
  }

  /**
   * Builds a {@code file://} URL under the platform filesystem root ({@code run/}).
   *
   * @param page file name relative to the UI root
   * @return URL such as {@code file:///ui/main.html}
   */
  private static String uiFileUrl(String page) {
    return "file:///ui/" + page;
  }

  /**
   * Returns the directory under the extracted UI root for singleplayer world thumbnails.
   *
   * @return {@code run/ui/world-icons}
   */
  Path worldIconsDir() {
    return uiDir.resolve("world-icons");
  }

  /** Updates Ultralight timers and paints dirty views for the current frame. */
  public void update() {
    if (gameplayPaused) {
      return;
    }
    if (view.width() != display.getWidth() || view.height() != display.getHeight()) {
      view.resize(display.getWidth(), display.getHeight());
    }
    renderer.update();
    renderer.render();
  }

  /**
   * Stops Ultralight input and painting while in-world.
   *
   * <p>Ultralight + Vulkan concurrent use has been observed to abort the JVM via {@code
   * ucrtbase.dll} ({@code 0xc0000409} / BEX64) with no Java stack. Menu resumes via {@link
   * #resumeForMenu()}.
   */
  public void pauseForGameplay() {
    gameplayPaused = true;
    if (input != null) {
      input.setTargetView(null);
    }
    if (view != null) {
      try {
        view.unfocus();
      } catch (RuntimeException ignored) {
        // Best-effort; view may already be idle.
      }
    }
  }

  /**
   * Re-enables Ultralight menu input and painting after returning from PLAYING.
   *
   * <p>Pairs with {@link #pauseForGameplay()}.
   */
  public void resumeForMenu() {
    gameplayPaused = false;
    if (input != null && view != null) {
      input.setTargetView(view);
      try {
        view.focus();
      } catch (RuntimeException ignored) {
        // Best-effort.
      }
    }
  }

  /**
   * Updates the in-game HUD FPS / chunk counter (Ultralight) when values change.
   *
   * @param fps frames per second
   * @param chunkMeshes resident chunk mesh count
   */
  public void updateHud(float fps, int chunkMeshes) {
    if (gameplayPaused || hudView == null) {
      return;
    }
    int fpsInt = Math.max(0, Math.round(fps));
    long now = System.nanoTime();
    // Avoid re-painting Ultralight every frame when the FPS integer jitters — that alone can
    // pin the game near display refresh even with IMMEDIATE present.
    boolean unchanged = fpsInt == lastHudFps && chunkMeshes == lastHudChunks;
    if (unchanged || (now - lastHudPaintNanos < 200_000_000L && lastHudPaintNanos != 0L)) {
      return;
    }
    lastHudFps = fpsInt;
    lastHudChunks = chunkMeshes;
    lastHudPaintNanos = now;
    try {
      hudView.evaluateScript("if(window.setHud){setHud(" + fpsInt + "," + chunkMeshes + ");}");
    } catch (com.labymedia.ultralight.javascript.JavascriptEvaluationException e) {
      // HUD is best-effort; ignore script races during load.
    }
    renderer.update();
    renderer.render();
  }

  /**
   * Locks HUD bitmap pixels for GPU upload. Caller must {@link #unlockHudPixels()}.
   *
   * @return BGRA pixels or {@code null}
   */
  public ByteBuffer lockHudPixels() {
    if (gameplayPaused || hudView == null) {
      return null;
    }
    UltralightBitmapSurface surface = (UltralightBitmapSurface) hudView.surface();
    if (surface == null) {
      return null;
    }
    return surface.bitmap().lockPixels();
  }

  /** Unlocks HUD bitmap pixels after {@link #lockHudPixels()}. */
  public void unlockHudPixels() {
    if (hudView == null) {
      return;
    }
    UltralightBitmapSurface surface = (UltralightBitmapSurface) hudView.surface();
    if (surface != null) {
      surface.bitmap().unlockPixels();
    }
  }

  /**
   * Returns the HUD bitmap width in pixels.
   *
   * @return HUD surface width, or {@link #HUD_WIDTH} if the HUD view is unavailable
   */
  public int hudPixelWidth() {
    return hudView != null ? (int) hudView.width() : HUD_WIDTH;
  }

  /**
   * Returns the HUD bitmap height in pixels.
   *
   * @return HUD surface height, or {@link #HUD_HEIGHT} if the HUD view is unavailable
   */
  public int hudPixelHeight() {
    return hudView != null ? (int) hudView.height() : HUD_HEIGHT;
  }

  /**
   * Returns the HUD bitmap row stride in bytes.
   *
   * @return bytes per HUD row
   */
  public int hudRowBytes() {
    if (hudView == null) {
      return HUD_WIDTH * 4;
    }
    UltralightBitmapSurface surface = (UltralightBitmapSurface) hudView.surface();
    if (surface == null) {
      return HUD_WIDTH * 4;
    }
    return (int) surface.bitmap().rowBytes();
  }

  /** Copies the current Ultralight surface into the Vulkan swapchain and presents it. */
  public void present() {
    UltralightBitmapSurface surface = (UltralightBitmapSurface) view.surface();
    if (surface == null) {
      return;
    }
    UltralightBitmap bitmap = surface.bitmap();
    ByteBuffer pixels = bitmap.lockPixels();
    try {
      display.presentBGRA(
          pixels, (int) bitmap.width(), (int) bitmap.height(), (int) bitmap.rowBytes());
    } finally {
      bitmap.unlockPixels();
    }
  }

  /** Releases UI references. Native Ultralight cleanup is owned by process exit for now. */
  @Override
  public void close() {
    hudView = null;
    view = null;
    renderer = null;
  }

  private void extractUiResources() throws IOException {
    if (Files.exists(uiDir)) {
      try (Stream<Path> walk = Files.walk(uiDir)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (IOException ignored) {
                    // Best-effort cleanup before re-extracting UI assets.
                  }
                });
      }
    }
    Files.createDirectories(uiDir);
    copyResourceTree("ui", uiDir);
  }

  private void copyResourceTree(String resourceRoot, Path targetRoot) throws IOException {
    String[] files = {
      "ui/main.html",
      "ui/singleplayer.html",
      "ui/create_world.html",
      "ui/hud.html",
      "ui/menu.css",
      "ui/menu.js",
      "ui/img/dirt.png",
      "ui/img/button.png",
      "ui/img/button_hover.png",
      "ui/img/input_field.png",
      "ui/img/panel.png",
      "ui/img/logo.png"
    };
    for (String resource : files) {
      String relative = resource.substring(resourceRoot.length() + 1);
      Path out = targetRoot.resolve(relative);
      Files.createDirectories(out.getParent());
      try (InputStream in = UltralightGui.class.getClassLoader().getResourceAsStream(resource)) {
        if (in == null) {
          throw new IOException("Missing classpath resource: " + resource);
        }
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  private void prepareNatives() throws IOException {
    Path sdkBin = sdkDir.resolve("bin");
    if (!Files.isDirectory(sdkBin)) {
      throw new IOException(
          "Ultralight SDK bin folder not found at "
              + sdkBin.toAbsolutePath()
              + ". Expected natives under natives/ultralight/bin");
    }
    try (Stream<Path> files = Files.list(sdkBin)) {
      files
          .filter(
              path -> {
                String name = path.getFileName().toString().toLowerCase();
                return name.endsWith(".dll")
                    || name.endsWith(".so")
                    || name.endsWith(".dylib")
                    || name.endsWith(".dat")
                    || name.endsWith(".pak");
              })
          .forEach(
              path -> {
                try {
                  Path dest = nativesDir.resolve(path.getFileName());
                  // Never REPLACE_EXISTING over a DLL still mapped by a half-killed process —
                  // that throws AccessDeniedException and aborts relaunch.
                  if (Files.isRegularFile(dest) && Files.size(dest) == Files.size(path)) {
                    return;
                  }
                  Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (java.nio.file.AccessDeniedException e) {
                  Path dest = nativesDir.resolve(path.getFileName());
                  if (!Files.isRegularFile(dest)) {
                    throw new RuntimeException(e);
                  }
                  System.err.println(
                      "[Opencraft] natives skip locked "
                          + path.getFileName()
                          + " (using existing copy)");
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    }
    Path resources = sdkDir.resolve("resources");
    if (Files.isDirectory(resources)) {
      Path destResources = runDir.resolve("resources");
      Files.createDirectories(destResources);
      Path assetsResources = runDir.resolve("assets").resolve("resources");
      Files.createDirectories(assetsResources);
      try (Stream<Path> walk = Files.walk(resources)) {
        walk.filter(Files::isRegularFile)
            .forEach(
                source -> {
                  try {
                    Path relative = resources.relativize(source);
                    Path target = destResources.resolve(relative);
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    Path assetsTarget = assetsResources.resolve(relative);
                    Files.createDirectories(assetsTarget.getParent());
                    Files.copy(source, assetsTarget, StandardCopyOption.REPLACE_EXISTING);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
      }
    }
  }
}
