package opencraft.graphics;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
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
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
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
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;

/** Presents CPU-side BGRA8 frames by copying them into the Vulkan swapchain each frame. */
public final class BgraFramePresenter implements AutoCloseable {
  private final GameWindow window;
  private final VulkanContext vulkan;
  private final VulkanSwapchain swapchain;

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

  private BgraFramePresenter(GameWindow window, VulkanContext vulkan, VulkanSwapchain swapchain) {
    this.window = window;
    this.vulkan = vulkan;
    this.swapchain = swapchain;
  }

  /**
   * Creates a presenter for the given window and Vulkan context.
   *
   * @param window render window
   * @param vulkan Vulkan device/surface context
   * @param swapchain shared swapchain used for presentation
   * @return initialized presenter
   */
  public static BgraFramePresenter create(
      GameWindow window, VulkanContext vulkan, VulkanSwapchain swapchain) {
    BgraFramePresenter presenter = new BgraFramePresenter(window, vulkan, swapchain);
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
      swapchain.recreate();
      window.clearFramebufferResized();
      createCommandBuffers();
    }

    VkExtent2D swapchainExtent = swapchain.getExtent();
    int destWidth = Math.max(1, swapchainExtent.width());
    int destHeight = Math.max(1, swapchainExtent.height());
    int destRowBytes = destWidth * 4;
    int required = destRowBytes * destHeight;
    ensureStagingCapacity(required);
    stagingMapped.clear();
    int srcLimit = pixels.position() + rowBytes * imageHeight;
    if (imageWidth == destWidth && imageHeight == destHeight && rowBytes == destRowBytes) {
      pixels.limit(srcLimit);
      stagingMapped.put(pixels);
    } else {
      scaleBgraIntoStaging(pixels, imageWidth, imageHeight, rowBytes, destWidth, destHeight);
    }
    stagingMapped.flip();

    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      IntBuffer imageIndex = stack.ints(0);
      int result =
          vkAcquireNextImageKHR(
              device,
              swapchain.getSwapchain(),
              -1L,
              imageAvailableSemaphores[currentFrame],
              VK_NULL_HANDLE,
              imageIndex);

      if (result == VK_ERROR_OUT_OF_DATE_KHR) {
        swapchain.recreate();
        createCommandBuffers();
        // Retry once so a resize during play does not leave a black frame.
        result =
            vkAcquireNextImageKHR(
                device,
                swapchain.getSwapchain(),
                -1L,
                imageAvailableSemaphores[currentFrame],
                VK_NULL_HANDLE,
                imageIndex);
        if (result == VK_ERROR_OUT_OF_DATE_KHR) {
          return;
        }
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
          swapchain.getImages()[index],
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
          .bufferRowLength(destWidth)
          .bufferImageHeight(destHeight)
          .imageSubresource()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .mipLevel(0)
          .baseArrayLayer(0)
          .layerCount(1);
      region.get(0).imageOffset().set(0, 0, 0);
      region.get(0).imageExtent().set(destWidth, destHeight, 1);

      vkCmdCopyBufferToImage(
          cmd,
          stagingBuffer,
          swapchain.getImages()[index],
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          region);

      transitionImageLayout(
          stack,
          cmd,
          swapchain.getImages()[index],
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
              .pSwapchains(stack.longs(swapchain.getSwapchain()))
              .pImageIndices(stack.ints(index));

      result = vkQueuePresentKHR(vulkan.getPresentQueue(), presentInfo);
      if (result == VK_ERROR_OUT_OF_DATE_KHR
          || result == VK_SUBOPTIMAL_KHR
          || window.wasFramebufferResized()) {
        window.clearFramebufferResized();
        swapchain.recreate();
        createCommandBuffers();
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
    int count = Math.max(2, swapchain.getImageCount());
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

  /**
   * Nearest-neighbor scales a BGRA frame into {@link #stagingMapped} at the swapchain size.
   *
   * @param pixels source BGRA buffer
   * @param imageWidth source width
   * @param imageHeight source height
   * @param rowBytes source row stride in bytes
   * @param destWidth destination width
   * @param destHeight destination height
   */
  private void scaleBgraIntoStaging(
      ByteBuffer pixels,
      int imageWidth,
      int imageHeight,
      int rowBytes,
      int destWidth,
      int destHeight) {
    int destRowBytes = destWidth * 4;
    int required = destRowBytes * destHeight;
    byte[] src = new byte[rowBytes * imageHeight];
    int oldPos = pixels.position();
    int srcLimit = oldPos + rowBytes * imageHeight;
    pixels.limit(srcLimit);
    pixels.get(src);
    for (int y = 0; y < destHeight; y++) {
      int srcY = y * imageHeight / destHeight;
      int srcRow = srcY * rowBytes;
      int destRow = y * destRowBytes;
      for (int x = 0; x < destWidth; x++) {
        int srcX = x * imageWidth / destWidth;
        int srcIndex = srcRow + srcX * 4;
        int destIndex = destRow + x * 4;
        stagingMapped.put(destIndex, src[srcIndex]);
        stagingMapped.put(destIndex + 1, src[srcIndex + 1]);
        stagingMapped.put(destIndex + 2, src[srcIndex + 2]);
        stagingMapped.put(destIndex + 3, src[srcIndex + 3]);
      }
    }
    stagingMapped.position(required);
    if (oldPos + rowBytes * imageHeight <= pixels.capacity()) {
      pixels.position(oldPos + rowBytes * imageHeight);
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
}
