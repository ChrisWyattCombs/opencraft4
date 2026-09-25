package opencraft.graphics;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;
import static org.lwjgl.glfw.GLFW.GLFW_RESIZABLE;
import static org.lwjgl.glfw.GLFW.GLFW_TRUE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDefaultWindowHints;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor;
import static org.lwjgl.glfw.GLFW.glfwGetVideoMode;
import static org.lwjgl.glfw.GLFW.glfwGetWindowSize;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSetFramebufferSizeCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowPos;
import static org.lwjgl.glfw.GLFW.glfwSetWindowShouldClose;
import static org.lwjgl.glfw.GLFW.glfwShowWindow;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWaitEvents;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.glfw.GLFW.glfwWindowShouldClose;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.IntBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;

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
  private boolean framebufferResized;

  private GameWindow(long handle, int width, int height) {
    this.handle = handle;
    this.width = width;
    this.height = height;
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

    long handle = glfwCreateWindow(width, height, title, NULL, NULL);
    if (handle == NULL) {
      glfwTerminate();
      throw new IllegalStateException("Failed to create GLFW window");
    }

    GameWindow window = new GameWindow(handle, width, height);
    glfwSetFramebufferSizeCallback(
        handle,
        (win, newWidth, newHeight) -> {
          window.width = Math.max(newWidth, 1);
          window.height = Math.max(newHeight, 1);
          window.framebufferResized = true;
        });

    try (MemoryStack stack = stackPush()) {
      IntBuffer pWidth = stack.mallocInt(1);
      IntBuffer pHeight = stack.mallocInt(1);
      glfwGetWindowSize(handle, pWidth, pHeight);
      var vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
      if (vidmode != null) {
        glfwSetWindowPos(
            handle, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
      }
    }

    glfwShowWindow(handle);
    return window;
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
   * Returns the current framebuffer width in pixels.
   *
   * @return width in pixels
   */
  public int getWidth() {
    return width;
  }

  /**
   * Returns the current framebuffer height in pixels.
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
