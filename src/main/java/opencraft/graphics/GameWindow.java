package opencraft.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_COCOA_RETINA_FRAMEBUFFER;
import static org.lwjgl.glfw.GLFW.GLFW_DONT_CARE;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_SCALE_FRAMEBUFFER;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowMonitor;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.macosx.ObjCRuntime.sel_getUid;

import java.nio.IntBuffer;
import java.util.Locale;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.macosx.ObjCRuntime;

/**
 * GLFW window used as the Vulkan render surface.
 *
 * <p>Created without an OpenGL context ({@code GLFW_NO_API}) so a Vulkan surface can be attached.
 */
public final class GameWindow implements AutoCloseable {
  /** Default window width in pixels. */
  public static final int DEFAULT_WIDTH = 1280;

  /** Default window height in pixels. */
  public static final int DEFAULT_HEIGHT = 720;

  private final long handle;
  private int width;
  private int height;
  private int framebufferWidth;
  private int framebufferHeight;
  private boolean framebufferResized;
  private boolean fullscreen;
  private int windowedX;
  private int windowedY;
  private int windowedWidth = DEFAULT_WIDTH;
  private int windowedHeight = DEFAULT_HEIGHT;

  private GameWindow(long handle, int width, int height) {
    this.handle = handle;
    this.width = width;
    this.height = height;
    this.framebufferWidth = width;
    this.framebufferHeight = height;
    this.windowedWidth = width;
    this.windowedHeight = height;
  }

  /**
   * Creates a centered window using {@link #DEFAULT_WIDTH} and {@link #DEFAULT_HEIGHT}.
   *
   * @param title window title text
   * @return a new window instance
   */
  public static GameWindow create(String title) {
    return create(DEFAULT_WIDTH, DEFAULT_HEIGHT, title);
  }

  /**
   * Creates a centered, resizable GLFW window configured for Vulkan.
   *
   * @param width initial client width in pixels
   * @param height initial client height in pixels
   * @param title window title text
   * @return a new window instance
   */
  public static GameWindow create(int width, int height, String title) {
    if (!glfwInit()) {
      throw new IllegalStateException("Unable to initialize GLFW");
    }
    if (!GLFWVulkan.glfwVulkanSupported()) {
      throw new IllegalStateException("Vulkan is not supported on this machine");
    }

    glfwDefaultWindowHints();
    glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API);
    glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
    glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
    // Keep framebuffer/render pixels equal to the window size (including on Retina).
    glfwWindowHint(GLFW_SCALE_FRAMEBUFFER, GLFW_FALSE);
    glfwWindowHint(GLFW_COCOA_RETINA_FRAMEBUFFER, GLFW_FALSE);

    long handle = glfwCreateWindow(width, height, title, NULL, NULL);
    if (handle == NULL) {
      glfwTerminate();
      throw new IllegalStateException("Failed to create GLFW window");
    }

    GameWindow window = new GameWindow(handle, width, height);
    glfwSetFramebufferSizeCallback(
        handle,
        (win, newWidth, newHeight) -> {
          window.syncSizes();
          window.framebufferResized = true;
        });
    window.syncSizes();
    disableRetinaFramebufferScaling(handle);
    window.syncSizes();
    System.out.println(
        "[Opencraft] render "
            + window.width
            + "x"
            + window.height
            + " framebuffer "
            + window.framebufferWidth
            + "x"
            + window.framebufferHeight);

    try (MemoryStack stack = stackPush()) {
      IntBuffer pWidth = stack.mallocInt(1);
      IntBuffer pHeight = stack.mallocInt(1);
      glfwGetWindowSize(handle, pWidth, pHeight);
      long monitor = glfwGetPrimaryMonitor();
      if (monitor != NULL) {
        var vidmode = glfwGetVideoMode(monitor);
        if (vidmode != null) {
          glfwSetWindowPos(
              handle,
              (vidmode.width() - pWidth.get(0)) / 2,
              (vidmode.height() - pHeight.get(0)) / 2);
        }
      }
    }

    glfwShowWindow(handle);
    return window;
  }

  /**
   * Refreshes window client size (used for rendering) and framebuffer size (swapchain) from GLFW.
   */
  private void syncSizes() {
    try (MemoryStack stack = stackPush()) {
      IntBuffer winW = stack.mallocInt(1);
      IntBuffer winH = stack.mallocInt(1);
      IntBuffer fbW = stack.mallocInt(1);
      IntBuffer fbH = stack.mallocInt(1);
      glfwGetWindowSize(handle, winW, winH);
      glfwGetFramebufferSize(handle, fbW, fbH);
      // Render at window client size so the game resolution matches the window.
      width = Math.max(winW.get(0), 1);
      height = Math.max(winH.get(0), 1);
      framebufferWidth = Math.max(fbW.get(0), 1);
      framebufferHeight = Math.max(fbH.get(0), 1);
    }
  }

  /**
   * On macOS Retina, forces the Cocoa view layer to {@code 1x} so the Vulkan drawable matches the
   * window when GLFW's scale hints are ignored for Metal surfaces.
   *
   * @param windowHandle GLFW window
   */
  private static void disableRetinaFramebufferScaling(long windowHandle) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (!os.contains("mac")) {
      return;
    }
    try {
      long view = GLFWNativeCocoa.glfwGetCocoaView(windowHandle);
      if (view == NULL) {
        return;
      }
      long objcMsgSend = ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
      long layer = JNI.invokePPP(view, sel_getUid("layer"), objcMsgSend);
      if (layer == NULL) {
        return;
      }
      // CGFloat is float-compatible via the float msgSend binding on Apple platforms.
      JNI.invokePPV(layer, sel_getUid("setContentsScale:"), 1.0f, objcMsgSend);
    } catch (Throwable ignored) {
      // Best-effort only; render size still follows window client size.
    }
  }

  /**
   * Horizontal scale from render/window coordinates to swapchain framebuffer pixels.
   *
   * @return typically {@code 1} or {@code 2} on Retina displays
   */
  public float getContentScaleX() {
    return framebufferWidth / (float) width;
  }

  /**
   * Vertical scale from render/window coordinates to swapchain framebuffer pixels.
   *
   * @return typically {@code 1} or {@code 2} on Retina displays
   */
  public float getContentScaleY() {
    return framebufferHeight / (float) height;
  }

  /**
   * Returns the Vulkan swapchain framebuffer width in pixels.
   *
   * @return framebuffer width
   */
  public int getFramebufferWidth() {
    return framebufferWidth;
  }

  /**
   * Returns the Vulkan swapchain framebuffer height in pixels.
   *
   * @return framebuffer height
   */
  public int getFramebufferHeight() {
    return framebufferHeight;
  }

  /**
   * Returns whether the user has requested the window to close.
   *
   * @return {@code true} if the window should close
   */
  public boolean shouldClose() {
    return glfwWindowShouldClose(handle);
  }

  /** Polls and dispatches pending GLFW window/input events. */
  public void pollEvents() {
    glfwPollEvents();
  }

  /**
   * Toggles exclusive fullscreen on the primary monitor. Borderless (monitor=null) still goes
   * through DWM and often stays locked at 60; exclusive fullscreen can use Independent Flip.
   */
  public void toggleFullscreen() {
    long monitor = glfwGetPrimaryMonitor();
    var vidmode = glfwGetVideoMode(monitor);
    if (vidmode == null) {
      return;
    }
    if (!fullscreen) {
      try (MemoryStack stack = stackPush()) {
        IntBuffer x = stack.mallocInt(1);
        IntBuffer y = stack.mallocInt(1);
        glfwGetWindowPos(handle, x, y);
        windowedX = x.get(0);
        windowedY = y.get(0);
        IntBuffer w = stack.mallocInt(1);
        IntBuffer h = stack.mallocInt(1);
        glfwGetWindowSize(handle, w, h);
        windowedWidth = w.get(0);
        windowedHeight = h.get(0);
      }
      glfwSetWindowMonitor(
          handle, monitor, 0, 0, vidmode.width(), vidmode.height(), vidmode.refreshRate());
      fullscreen = true;
      System.out.println(
          "[Opencraft] exclusive fullscreen "
              + vidmode.width()
              + "x"
              + vidmode.height()
              + "@"
              + vidmode.refreshRate());
    } else {
      glfwSetWindowMonitor(
          handle, NULL, windowedX, windowedY, windowedWidth, windowedHeight, GLFW_DONT_CARE);
      fullscreen = false;
      System.out.println("[Opencraft] windowed " + windowedWidth + "x" + windowedHeight);
    }
    syncSizes();
    framebufferResized = true;
  }

  /**
   * Returns whether the window is currently exclusive fullscreen.
   *
   * @return {@code true} if exclusive fullscreen is active
   */
  public boolean isFullscreen() {
    return fullscreen;
  }

  /** Requests that the window close on the next main-loop check. */
  public void requestClose() {
    glfwSetWindowShouldClose(handle, true);
  }

  /** Blocks while the framebuffer is minimized (zero sized), waiting for restore. */
  public void waitWhileMinimized() {
    try (MemoryStack stack = stackPush()) {
      IntBuffer widthBuf = stack.ints(0);
      IntBuffer heightBuf = stack.ints(0);
      glfwGetFramebufferSize(handle, widthBuf, heightBuf);
      while (widthBuf.get(0) == 0 || heightBuf.get(0) == 0) {
        glfwGetFramebufferSize(handle, widthBuf, heightBuf);
        glfwWaitEvents();
      }
    }
  }

  /**
   * Returns the native GLFW window handle.
   *
   * @return GLFW window pointer
   */
  public long getHandle() {
    return handle;
  }

  /**
   * Returns the current render width in pixels (window client size).
   *
   * @return width in pixels
   */
  public int getWidth() {
    return width;
  }

  /**
   * Returns the current render height in pixels (window client size).
   *
   * @return height in pixels
   */
  public int getHeight() {
    return height;
  }

  /**
   * Returns whether the framebuffer size changed since the last consume.
   *
   * @return {@code true} if a resize was reported by GLFW
   */
  public boolean wasFramebufferResized() {
    return framebufferResized;
  }

  /** Clears the framebuffer-resized flag after the swapchain has been recreated. */
  public void clearFramebufferResized() {
    framebufferResized = false;
  }

  /** Destroys the GLFW window and terminates GLFW. */
  @Override
  public void close() {
    if (handle != NULL) {
      glfwDestroyWindow(handle);
    }
    glfwTerminate();
  }
}
