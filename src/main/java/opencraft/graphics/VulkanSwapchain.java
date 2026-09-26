package opencraft.graphics;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.VK10.VK_COMPONENT_SWIZZLE_IDENTITY;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

/** Shared Vulkan swapchain and image views for UI and world rendering. */
public final class VulkanSwapchain implements AutoCloseable {

  private final GameWindow window;
  private final VulkanContext vulkan;

  private long swapchain;
  private long[] swapchainImages = new long[0];
  private long[] swapchainImageViews = new long[0];
  private int swapchainImageFormat;
  private VkExtent2D swapchainExtent;

  private VulkanSwapchain(GameWindow window, VulkanContext vulkan) {
    this.window = window;
    this.vulkan = vulkan;
  }

  /**
   * Creates a swapchain and image views for the window surface.
   *
   * @param window GLFW window
   * @param vulkan Vulkan context with surface and device
   * @return initialized swapchain holder
   */
  public static VulkanSwapchain create(GameWindow window, VulkanContext vulkan) {
    VulkanSwapchain chain = new VulkanSwapchain(window, vulkan);
    chain.createSwapchain();
    chain.createImageViews();
    return chain;
  }

  /**
   * Returns the Vulkan swapchain handle.
   *
   * @return swapchain
   */
  public long getSwapchain() {
    return swapchain;
  }

  /**
   * Returns swapchain image handles.
   *
   * @return image array
   */
  public long[] getImages() {
    return swapchainImages;
  }

  /**
   * Returns image views matching {@link #getImages()}.
   *
   * @return view array
   */
  public long[] getImageViews() {
    return swapchainImageViews;
  }

  /**
   * Returns the swapchain surface format.
   *
   * @return Vulkan format constant
   */
  public int getFormat() {
    return swapchainImageFormat;
  }

  /**
   * Returns the current swapchain extent.
   *
   * @return extent (caller must not free)
   */
  public VkExtent2D getExtent() {
    return swapchainExtent;
  }

  /**
   * Returns the number of swapchain images.
   *
   * @return image count
   */
  public int getImageCount() {
    return swapchainImages.length;
  }

  /**
   * Recreates the swapchain after resize or suboptimal present.
   *
   * <p>Blocks while the window is minimized.
   */
  public void recreate() {
    window.waitWhileMinimized();
    vulkan.waitIdle();
    cleanupSwapchain();
    createSwapchain();
    createImageViews();
  }

  /** Destroys image views and the swapchain. */
  @Override
  public void close() {
    vulkan.waitIdle();
    cleanupSwapchain();
  }

  private void createSwapchain() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      SwapchainSupportDetails support = querySwapchainSupport(stack);
      VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(support.formats);
      int presentMode = chooseSwapPresentMode(support.presentModes);
      System.out.println(
          "[Opencraft] swapchain presentMode="
              + presentModeName(presentMode)
              + " available="
              + presentModesSummary(support.presentModes));
      VkExtent2D extent = chooseSwapExtent(support.capabilities, stack);

      int imageCount = support.capabilities.minImageCount() + 1;
      if (support.capabilities.maxImageCount() > 0
          && imageCount > support.capabilities.maxImageCount()) {
        imageCount = support.capabilities.maxImageCount();
      }

      long oldSwapchain = swapchain;
      VkSwapchainCreateInfoKHR createInfo =
          VkSwapchainCreateInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR)
              .surface(vulkan.getSurface())
              .minImageCount(imageCount)
              .imageFormat(surfaceFormat.format())
              .imageColorSpace(surfaceFormat.colorSpace())
              .imageExtent(extent)
              .imageArrayLayers(1)
              .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
              .preTransform(support.capabilities.currentTransform())
              .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
              .presentMode(presentMode)
              .clipped(true)
              .oldSwapchain(oldSwapchain);

      int graphicsFamily = vulkan.getGraphicsQueueFamily();
      int presentFamily = vulkan.getPresentQueueFamily();
      if (graphicsFamily != presentFamily) {
        createInfo
            .imageSharingMode(VK_SHARING_MODE_CONCURRENT)
            .pQueueFamilyIndices(stack.ints(graphicsFamily, presentFamily));
      } else {
        createInfo.imageSharingMode(VK_SHARING_MODE_EXCLUSIVE);
      }

      LongBuffer swapchainPtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateSwapchainKHR(device, createInfo, null, swapchainPtr), "create swapchain");
      swapchain = swapchainPtr.get(0);
      if (oldSwapchain != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(device, oldSwapchain, null);
      }
      swapchainImageFormat = surfaceFormat.format();
      if (swapchainExtent != null) {
        swapchainExtent.free();
      }
      swapchainExtent = VkExtent2D.malloc().set(extent.width(), extent.height());

      IntBuffer imageCountBuf = stack.ints(0);
      vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, null);
      LongBuffer images = stack.mallocLong(imageCountBuf.get(0));
      vkGetSwapchainImagesKHR(device, swapchain, imageCountBuf, images);
      swapchainImages = new long[images.capacity()];
      for (int i = 0; i < images.capacity(); i++) {
        swapchainImages[i] = images.get(i);
      }
    }
  }

  private void createImageViews() {
    VkDevice device = vulkan.getDevice();
    swapchainImageViews = new long[swapchainImages.length];
    try (MemoryStack stack = stackPush()) {
      for (int i = 0; i < swapchainImages.length; i++) {
        VkImageViewCreateInfo createInfo =
            VkImageViewCreateInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                .image(swapchainImages[i])
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(swapchainImageFormat);
        createInfo
            .components()
            .r(VK_COMPONENT_SWIZZLE_IDENTITY)
            .g(VK_COMPONENT_SWIZZLE_IDENTITY)
            .b(VK_COMPONENT_SWIZZLE_IDENTITY)
            .a(VK_COMPONENT_SWIZZLE_IDENTITY);
        createInfo
            .subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);

        LongBuffer view = stack.mallocLong(1);
        VulkanContext.checkVk(
            vkCreateImageView(device, createInfo, null, view), "create image views");
        swapchainImageViews[i] = view.get(0);
      }
    }
  }

  private void cleanupSwapchain() {
    VkDevice device = vulkan.getDevice();
    for (long view : swapchainImageViews) {
      vkDestroyImageView(device, view, null);
    }
    swapchainImageViews = new long[0];
    if (swapchain != VK_NULL_HANDLE) {
      vkDestroySwapchainKHR(device, swapchain, null);
      swapchain = VK_NULL_HANDLE;
    }
    swapchainImages = new long[0];
    if (swapchainExtent != null) {
      swapchainExtent.free();
      swapchainExtent = null;
    }
  }

  private VkSurfaceFormatKHR chooseSwapSurfaceFormat(VkSurfaceFormatKHR.Buffer formats) {
    for (int i = 0; i < formats.capacity(); i++) {
      VkSurfaceFormatKHR format = formats.get(i);
      if ((format.format() == VK_FORMAT_B8G8R8A8_UNORM
              || format.format() == VK_FORMAT_B8G8R8A8_SRGB)
          && format.colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
        return format;
      }
    }
    return formats.get(0);
  }

  private int chooseSwapPresentMode(IntBuffer presentModes) {
    // Prefer IMMEDIATE for uncapped FPS (may tear). Fall back to MAILBOX, then FIFO (vsync).
    for (int i = 0; i < presentModes.capacity(); i++) {
      if (presentModes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR) {
        return VK_PRESENT_MODE_IMMEDIATE_KHR;
      }
    }
    for (int i = 0; i < presentModes.capacity(); i++) {
      if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
        return VK_PRESENT_MODE_MAILBOX_KHR;
      }
    }
    return VK_PRESENT_MODE_FIFO_KHR;
  }

  private static String presentModeName(int mode) {
    return switch (mode) {
      case VK_PRESENT_MODE_IMMEDIATE_KHR -> "IMMEDIATE";
      case VK_PRESENT_MODE_MAILBOX_KHR -> "MAILBOX";
      case VK_PRESENT_MODE_FIFO_KHR -> "FIFO";
      case VK_PRESENT_MODE_FIFO_RELAXED_KHR -> "FIFO_RELAXED";
      default -> "mode(" + mode + ")";
    };
  }

  private static String presentModesSummary(IntBuffer presentModes) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < presentModes.capacity(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(presentModeName(presentModes.get(i)));
    }
    return sb.append(']').toString();
  }

  private VkExtent2D chooseSwapExtent(VkSurfaceCapabilitiesKHR capabilities, MemoryStack stack) {
    if (capabilities.currentExtent().width() != 0xFFFFFFFF) {
      return capabilities.currentExtent();
    }
    IntBuffer width = stack.ints(0);
    IntBuffer height = stack.ints(0);
    glfwGetFramebufferSize(window.getHandle(), width, height);
    VkExtent2D actual = VkExtent2D.malloc(stack).set(width.get(0), height.get(0));
    actual.width(
        clamp(
            actual.width(),
            capabilities.minImageExtent().width(),
            capabilities.maxImageExtent().width()));
    actual.height(
        clamp(
            actual.height(),
            capabilities.minImageExtent().height(),
            capabilities.maxImageExtent().height()));
    return actual;
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private SwapchainSupportDetails querySwapchainSupport(MemoryStack stack) {
    SwapchainSupportDetails details = new SwapchainSupportDetails();
    details.capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(
        vulkan.getPhysicalDevice(), vulkan.getSurface(), details.capabilities);

    IntBuffer formatCount = stack.ints(0);
    vkGetPhysicalDeviceSurfaceFormatsKHR(
        vulkan.getPhysicalDevice(), vulkan.getSurface(), formatCount, null);
    details.formats = VkSurfaceFormatKHR.malloc(formatCount.get(0), stack);
    vkGetPhysicalDeviceSurfaceFormatsKHR(
        vulkan.getPhysicalDevice(), vulkan.getSurface(), formatCount, details.formats);

    IntBuffer presentModeCount = stack.ints(0);
    vkGetPhysicalDeviceSurfacePresentModesKHR(
        vulkan.getPhysicalDevice(), vulkan.getSurface(), presentModeCount, null);
    details.presentModes = stack.mallocInt(presentModeCount.get(0));
    vkGetPhysicalDeviceSurfacePresentModesKHR(
        vulkan.getPhysicalDevice(), vulkan.getSurface(), presentModeCount, details.presentModes);
    return details;
  }

  private static final class SwapchainSupportDetails {
    private VkSurfaceCapabilitiesKHR capabilities;
    private VkSurfaceFormatKHR.Buffer formats;
    private IntBuffer presentModes;
  }
}
