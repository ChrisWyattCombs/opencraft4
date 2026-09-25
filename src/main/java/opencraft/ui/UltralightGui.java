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
import java.util.stream.Stream;
import opencraft.graphics.Display;

/**
 * Ultralight-powered HTML UI rendered to a CPU bitmap and presented through Vulkan.
 *
 * <p>Loads menu pages from classpath resources under {@code ui/}, bridges JavaScript calls via
 * {@link MenuBridge}, and uploads each frame to {@link Display#presentBGRA}.
 */
public final class UltralightGui implements AutoCloseable {
  private final Display display;
  private final Path runDir;
  private final Path uiDir;
  private final Path nativesDir;
  private final Path sdkDir;

  private UltralightRenderer renderer;
  private UltralightView view;
  private MenuBridge bridge;
  private GuiInput input;
  private String currentPage = "main.html";

  /**
   * Creates a GUI host bound to the given display and project root.
   *
   * @param display Vulkan/GLFW display used for presentation and input
   * @param projectRoot project root containing {@code natives/} and writable {@code run/}
   */
  public UltralightGui(Display display, Path projectRoot) {
    this.display = display;
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

    bridge = new MenuBridge(this);
    view.setLoadListener(new GuiLoadListener(view, bridge));
    input = new GuiInput(display);
    input.bind(view);

    loadPage(currentPage);
    view.focus();
  }

  /**
   * Loads an HTML page from the extracted UI directory.
   *
   * @param page file name relative to the UI root (for example {@code main.html})
   */
  public void loadPage(String page) {
    currentPage = page;
    Path pagePath = uiDir.resolve(page).toAbsolutePath().normalize();
    view.loadURL(pagePath.toUri().toString());
  }

  /** Updates Ultralight timers and paints dirty views for the current frame. */
  public void update() {
    if (view.width() != display.getWidth() || view.height() != display.getHeight()) {
      view.resize(display.getWidth(), display.getHeight());
    }
    renderer.update();
    renderer.render();
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
                  Files.copy(
                      path,
                      nativesDir.resolve(path.getFileName()),
                      StandardCopyOption.REPLACE_EXISTING);
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
