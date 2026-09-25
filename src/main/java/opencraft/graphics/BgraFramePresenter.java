package opencraft.graphics;

import static org.lwjgl.glfw.GLFW.glfwGetFramebufferSize;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkCreateSwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkDestroySwapchainKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkGetSwapchainImagesKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMPONENT_SWIZZLE_IDENTITY;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_SRGB;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_CONCURRENT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

/** Presents CPU-side BGRA8 frames by copying them into the Vulkan swapchain each frame. */
public final class BgraFramePresenter implements AutoCloseable {
  private final GameWindow window;
  private final VulkanContext vulkan;

  private long swapchain;
  private long[] swapchainImages = new long[0];
  private long[] swapchainImageViews = new long[0];
  private int swapchainImageFormat;
  private VkExtent2D swapchainExtent;

  private long commandPool;
  private VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
  private long[] imageAvailableSemaphores = new long[0];
  private long[] renderFinishedSemaphores = new long[0];
  private long[] inFlightFences = new long[0];
  private int currentFrame;

  private long stagingBuffer;
  private long stagingMemory;
  private long stagingCapacity;
  private ByteBuffer stagingMapped;

  private BgraFramePresenter(GameWindow window, VulkanContext vulkan) {
    this.window = window;
    this.vulkan = vulkan;
  }

  /**
   * Creates a presenter for the given window and Vulkan context.
   *
   * @param window render window
   * @param vulkan Vulkan device/surface context
   * @return initialized presenter
   */
  public static BgraFramePresenter create(GameWindow window, VulkanContext vulkan) {
    BgraFramePresenter presenter = new BgraFramePresenter(window, vulkan);
    presenter.createSwapchain();
    presenter.createImageViews();
    presenter.createCommandPool();
    presenter.createCommandBuffers();
    presenter.createSyncObjects();
    return presenter;
  }

  /**
   * Uploads a BGRA8 frame (optionally row-strided) and presents it to the swapchain.
   *
   * @param pixels BGRA pixel buffer; position advances by {@code rowBytes * imageHeight}
   * @param imageWidth image width in pixels
   * @param imageHeight image height in pixels
   * @param rowBytes number of bytes per row (may be greater than {@code imageWidth * 4})
   */
  public void present(ByteBuffer pixels, int imageWidth, int imageHeight, int rowBytes) {
    if (window.wasFramebufferResized()) {
      recreateSwapchain();
      window.clearFramebufferResized();
    }

    int required = rowBytes * imageHeight;
    ensureStagingCapacity(required);
    stagingMapped.clear();
    pixels.limit(pixels.position() + required);
    stagingMapped.put(pixels);
    stagingMapped.flip();

    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      IntBuffer imageIndex = stack.ints(0);
      int result =
          vkAcquireNextImageKHR(
              device,
              swapchain,
              -1L,
              imageAvailableSemaphores[currentFrame],
              VK_NULL_HANDLE,
              imageIndex);

      if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapchain();
        return;
      }
      if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
        throw new IllegalStateException("Failed to acquire swapchain image: " + result);
      }

      int index = imageIndex.get(0);
      vkWaitForFences(device, inFlightFences[currentFrame], true, -1L);
      vkResetFences(device, inFlightFences[currentFrame]);

      VkCommandBuffer cmd = commandBuffers[currentFrame];
      vkResetCommandBuffer(cmd, 0);

      VkCommandBufferBeginInfo beginInfo =
          VkCommandBufferBeginInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
              .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      vkBeginCommandBuffer(cmd, beginInfo);

      transitionImageLayout(
          stack,
          cmd,
          swapchainImages[index],
          VK_IMAGE_LAYOUT_UNDEFINED,
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          0,
          VK_ACCESS_TRANSFER_WRITE_BIT,
          VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
          VK_PIPELINE_STAGE_TRANSFER_BIT);

      VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
      region
          .get(0)
          .bufferOffset(0)
          .bufferRowLength(rowBytes / 4)
          .bufferImageHeight(imageHeight)
          .imageSubresource()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .mipLevel(0)
          .baseArrayLayer(0)
          .layerCount(1);
      region.get(0).imageOffset().set(0, 0, 0);
      region
          .get(0)
          .imageExtent()
          .set(
              Math.min(imageWidth, swapchainExtent.width()),
              Math.min(imageHeight, swapchainExtent.height()),
              1);

      vkCmdCopyBufferToImage(
          cmd, stagingBuffer, swapchainImages[index], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

      transitionImageLayout(
          stack,
          cmd,
          swapchainImages[index],
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
          VK_ACCESS_TRANSFER_WRITE_BIT,
          0,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);

      vkEndCommandBuffer(cmd);

      VkSubmitInfo submitInfo =
          VkSubmitInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
              .waitSemaphoreCount(1)
              .pWaitSemaphores(stack.longs(imageAvailableSemaphores[currentFrame]))
              .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
              .pCommandBuffers(stack.pointers(cmd.address()))
              .pSignalSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));

      VulkanContext.checkVk(
          vkQueueSubmit(vulkan.getGraphicsQueue(), submitInfo, inFlightFences[currentFrame]),
          "submit present commands");

      VkPresentInfoKHR presentInfo =
          VkPresentInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
              .pWaitSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]))
              .swapchainCount(1)
              .pSwapchains(stack.longs(swapchain))
              .pImageIndices(stack.ints(index));

      result = vkQueuePresentKHR(vulkan.getPresentQueue(), presentInfo);
      if (result == VK_ERROR_OUT_OF_DATE_KHR
          || result == VK_SUBOPTIMAL_KHR
          || window.wasFramebufferResized()) {
        window.clearFramebufferResized();
        recreateSwapchain();
      } else if (result != VK_SUCCESS) {
        throw new IllegalStateException("Failed to present swapchain image: " + result);
      }

      currentFrame = (currentFrame + 1) % inFlightFences.length;
    }
  }

  /** Releases swapchain, staging, and synchronization objects. */
  @Override
  public void close() {
    vulkan.waitIdle();
    destroyStaging();
    VkDevice device = vulkan.getDevice();
    for (long fence : inFlightFences) {
      vkDestroyFence(device, fence, null);
    }
    for (long semaphore : imageAvailableSemaphores) {
      vkDestroySemaphore(device, semaphore, null);
    }
    for (long semaphore : renderFinishedSemaphores) {
      vkDestroySemaphore(device, semaphore, null);
    }
    if (commandPool != VK_NULL_HANDLE) {
      vkDestroyCommandPool(device, commandPool, null);
      commandPool = VK_NULL_HANDLE;
    }
    cleanupSwapchain();
  }

  private void createSwapchain() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      SwapchainSupportDetails support = querySwapchainSupport(stack);
      VkSurfaceFormatKHR surfaceFormat = chooseSwapSurfaceFormat(support.formats);
      int presentMode = chooseSwapPresentMode(support.presentModes);
      VkExtent2D extent = chooseSwapExtent(support.capabilities, stack);

      int imageCount = support.capabilities.minImageCount() + 1;
      if (support.capabilities.maxImageCount() > 0
          && imageCount > support.capabilities.maxImageCount()) {
        imageCount = support.capabilities.maxImageCount();
      }

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
              .oldSwapchain(VK_NULL_HANDLE);

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
    for (int i = 0; i < presentModes.capacity(); i++) {
      if (presentModes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
        return VK_PRESENT_MODE_MAILBOX_KHR;
      }
    }
    return VK_PRESENT_MODE_FIFO_KHR;
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

  private void createCommandPool() {
    try (MemoryStack stack = stackPush()) {
      VkCommandPoolCreateInfo poolInfo =
          VkCommandPoolCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
              .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
              .queueFamilyIndex(vulkan.getGraphicsQueueFamily());
      LongBuffer pool = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateCommandPool(vulkan.getDevice(), poolInfo, null, pool), "create command pool");
      commandPool = pool.get(0);
    }
  }

  private void createCommandBuffers() {
    int count = Math.max(2, swapchainImages.length);
    commandBuffers = new VkCommandBuffer[count];
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo allocInfo =
          VkCommandBufferAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(count);
      PointerBuffer buffers = stack.mallocPointer(count);
      VulkanContext.checkVk(
          vkAllocateCommandBuffers(vulkan.getDevice(), allocInfo, buffers),
          "allocate command buffers");
      for (int i = 0; i < count; i++) {
        commandBuffers[i] = new VkCommandBuffer(buffers.get(i), vulkan.getDevice());
      }
    }
  }

  private void createSyncObjects() {
    int frames = commandBuffers.length;
    imageAvailableSemaphores = new long[frames];
    renderFinishedSemaphores = new long[frames];
    inFlightFences = new long[frames];
    VkDevice device = vulkan.getDevice();

    try (MemoryStack stack = stackPush()) {
      VkSemaphoreCreateInfo semaphoreInfo =
          VkSemaphoreCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);
      VkFenceCreateInfo fenceInfo =
          VkFenceCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
              .flags(VK_FENCE_CREATE_SIGNALED_BIT);

      LongBuffer pointer = stack.mallocLong(1);
      for (int i = 0; i < frames; i++) {
        VulkanContext.checkVk(
            vkCreateSemaphore(device, semaphoreInfo, null, pointer),
            "create image available semaphore");
        imageAvailableSemaphores[i] = pointer.get(0);
        VulkanContext.checkVk(
            vkCreateSemaphore(device, semaphoreInfo, null, pointer),
            "create render finished semaphore");
        renderFinishedSemaphores[i] = pointer.get(0);
        VulkanContext.checkVk(vkCreateFence(device, fenceInfo, null, pointer), "create fence");
        inFlightFences[i] = pointer.get(0);
      }
    }
  }

  private void ensureStagingCapacity(long required) {
    if (stagingBuffer != VK_NULL_HANDLE && stagingCapacity >= required) {
      return;
    }
    destroyStaging();

    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkBufferCreateInfo bufferInfo =
          VkBufferCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
              .size(required)
              .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);

      LongBuffer buffer = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateBuffer(device, bufferInfo, null, buffer), "create staging buffer");
      stagingBuffer = buffer.get(0);

      VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
      vkGetBufferMemoryRequirements(device, stagingBuffer, memRequirements);

      VkMemoryAllocateInfo allocInfo =
          VkMemoryAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
              .allocationSize(memRequirements.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      memRequirements.memoryTypeBits(),
                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));

      LongBuffer memory = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkAllocateMemory(device, allocInfo, null, memory), "allocate staging memory");
      stagingMemory = memory.get(0);
      vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0);

      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, stagingMemory, 0, required, 0, mapped);
      stagingMapped = mapped.getByteBuffer(0, (int) required);
      stagingCapacity = required;
    }
  }

  private void transitionImageLayout(
      MemoryStack stack,
      VkCommandBuffer cmd,
      long image,
      int oldLayout,
      int newLayout,
      int srcAccess,
      int dstAccess,
      int srcStage,
      int dstStage) {
    VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
    barrier
        .get(0)
        .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
        .oldLayout(oldLayout)
        .newLayout(newLayout)
        .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
        .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
        .image(image)
        .srcAccessMask(srcAccess)
        .dstAccessMask(dstAccess);
    barrier
        .get(0)
        .subresourceRange()
        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
        .baseMipLevel(0)
        .levelCount(1)
        .baseArrayLayer(0)
        .layerCount(1);

    vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, null, null, barrier);
  }

  private void recreateSwapchain() {
    window.waitWhileMinimized();
    vulkan.waitIdle();
    cleanupSwapchain();
    createSwapchain();
    createImageViews();
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
    if (swapchainExtent != null) {
      swapchainExtent.free();
      swapchainExtent = null;
    }
  }

  private void destroyStaging() {
    VkDevice device = vulkan.getDevice();
    if (stagingMapped != null) {
      vkUnmapMemory(device, stagingMemory);
      stagingMapped = null;
    }
    if (stagingBuffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, stagingBuffer, null);
      stagingBuffer = VK_NULL_HANDLE;
    }
    if (stagingMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, stagingMemory, null);
      stagingMemory = VK_NULL_HANDLE;
    }
    stagingCapacity = 0;
  }

  private static final class SwapchainSupportDetails {
    private VkSurfaceCapabilitiesKHR capabilities;
    private VkSurfaceFormatKHR.Buffer formats;
    private IntBuffer presentModes;
  }
}
