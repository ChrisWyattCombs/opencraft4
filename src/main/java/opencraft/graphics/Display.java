package opencraft.graphics;

import java.nio.ByteBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;

/**
 * High-level display façade for Opencraft.
 *
 * <p>Composes {@link GameWindow}, {@link VulkanContext}, and {@link BgraFramePresenter} while
 * preserving a simple API for the main loop and UI layer.
 */
public final class Display implements AutoCloseable {
  /** Default window width in pixels. */
  public static final int DEFAULT_WIDTH = GameWindow.DEFAULT_WIDTH;

  /** Default window height in pixels. */
  public static final int DEFAULT_HEIGHT = GameWindow.DEFAULT_HEIGHT;

  private GameWindow window;
  private VulkanContext vulkan;
  private BgraFramePresenter presenter;

  /**
   * Creates a centered window using {@link #DEFAULT_WIDTH} and {@link #DEFAULT_HEIGHT}.
   *
   * @param title window title text
   */
  public void createWindow(String title) {
    createWindow(DEFAULT_WIDTH, DEFAULT_HEIGHT, title);
  }

  /**
   * Creates a centered, resizable GLFW window configured for Vulkan ({@code GLFW_NO_API}).
   *
   * @param width initial client width in pixels
   * @param height initial client height in pixels
   * @param title window title text
   */
  public void createWindow(int width, int height, String title) {
    if (window != null) {
      throw new IllegalStateException("Window already created");
    }
    window = GameWindow.create(width, height, title);
  }

  /**
   * Initializes the Vulkan instance, surface, device, swapchain, and presentation helpers.
   *
   * <p>Must be called after {@link #createWindow(int, int, String)}.
   */
  public void initVulkan() {
    requireWindow();
    if (vulkan != null) {
      throw new IllegalStateException("Vulkan already initialized");
    }
    vulkan = VulkanContext.create(window);
    presenter = BgraFramePresenter.create(window, vulkan);
  }

  /**
   * Returns whether the user has requested the window to close.
   *
   * @return {@code true} if the window should close
   */
  public boolean shouldClose() {
    return requireWindow().shouldClose();
  }

  /** Polls and dispatches pending GLFW window/input events. */
  public void pollEvents() {
    requireWindow().pollEvents();
  }

  /** Requests that the window close on the next main-loop check. */
  public void requestClose() {
    requireWindow().requestClose();
  }

  /**
   * Returns the native GLFW window handle.
   *
   * @return GLFW window pointer
   */
  public long getWindowHandle() {
    return requireWindow().getHandle();
  }

  /**
   * Returns the current framebuffer width in pixels.
   *
   * @return width in pixels
   */
  public int getWidth() {
    return requireWindow().getWidth();
  }

  /**
   * Returns the current framebuffer height in pixels.
   *
   * @return height in pixels
   */
  public int getHeight() {
    return requireWindow().getHeight();
  }

  /**
   * Returns the logical Vulkan device.
   *
   * @return Vulkan device, or {@code null} before {@link #initVulkan()}
   */
  public VkDevice getDevice() {
    return vulkan == null ? null : vulkan.getDevice();
  }

  /**
   * Returns the selected physical Vulkan device.
   *
   * @return physical device, or {@code null} before {@link #initVulkan()}
   */
  public VkPhysicalDevice getPhysicalDevice() {
    return vulkan == null ? null : vulkan.getPhysicalDevice();
  }

  /**
   * Returns the Vulkan instance.
   *
   * @return instance, or {@code null} before {@link #initVulkan()}
   */
  public VkInstance getInstance() {
    return vulkan == null ? null : vulkan.getInstance();
  }

  /**
   * Returns the Vulkan window surface handle.
   *
   * @return surface handle, or {@code 0} before {@link #initVulkan()}
   */
  public long getSurface() {
    return vulkan == null ? 0L : vulkan.getSurface();
  }

  /**
   * Returns whether the framebuffer size changed since the last present/recreate.
   *
   * @return {@code true} if a resize was reported by GLFW
   */
  public boolean wasFramebufferResized() {
    return requireWindow().wasFramebufferResized();
  }

  /**
   * Uploads a BGRA8 frame (optionally row-strided) and presents it to the swapchain.
   *
   * @param pixels BGRA pixel buffer; position advances by {@code rowBytes * imageHeight}
   * @param imageWidth image width in pixels
   * @param imageHeight image height in pixels
   * @param rowBytes number of bytes per row (may be greater than {@code imageWidth * 4})
   */
  public void presentBGRA(ByteBuffer pixels, int imageWidth, int imageHeight, int rowBytes) {
    requirePresenter().present(pixels, imageWidth, imageHeight, rowBytes);
  }

  /** Releases Vulkan objects and destroys the GLFW window. */
  @Override
  public void close() {
    if (presenter != null) {
      presenter.close();
      presenter = null;
    }
    if (vulkan != null) {
      vulkan.close();
      vulkan = null;
    }
    if (window != null) {
      window.close();
      window = null;
    }
  }

  private GameWindow requireWindow() {
    if (window == null) {
      throw new IllegalStateException("Window has not been created");
    }
    return window;
  }

  private BgraFramePresenter requirePresenter() {
    if (presenter == null) {
      throw new IllegalStateException("Vulkan presenter has not been initialized");
    }
    return presenter;
  }
}
