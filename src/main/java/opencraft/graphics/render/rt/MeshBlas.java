package opencraft.graphics.render.rt;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_GEOMETRY_TYPE_TRIANGLES_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCmdBuildAccelerationStructuresKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCreateAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkDestroyAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkGetAccelerationStructureBuildSizesKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkGetAccelerationStructureDeviceAddressKHR;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import opencraft.graphics.VulkanContext;
import opencraft.graphics.render.GpuRegionMesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAccelerationStructureBuildGeometryInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildRangeInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildSizesInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureCreateInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureDeviceAddressInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryTrianglesDataKHR;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateFlagsInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkSubmitInfo;

/** Bottom-level acceleration structure for one opaque region mesh. */
public final class MeshBlas {

  private long accelerationStructure = VK_NULL_HANDLE;
  private long buffer = VK_NULL_HANDLE;
  private long memory = VK_NULL_HANDLE;
  private long deviceAddress;

  private MeshBlas() {}

  /**
   * Builds a BLAS for the opaque triangles of {@code mesh}.
   *
   * @param vulkan Vulkan context with RT enabled
   * @param commandPool graphics command pool
   * @param mesh uploaded region mesh with opaque geometry
   * @return BLAS or {@code null} if mesh has no opaque triangles
   */
  public static MeshBlas build(VulkanContext vulkan, long commandPool, GpuRegionMesh mesh) {
    if (mesh == null || !mesh.hasOpaque() || mesh.getOpaqueVertexAddress() == 0L) {
      return null;
    }
    return build(
        vulkan,
        commandPool,
        mesh.getOpaqueVertexAddress(),
        mesh.getOpaqueIndexAddress(),
        mesh.getOpaqueVertexCount(),
        mesh.getOpaqueIndexCount(),
        true);
  }

  /**
   * Builds a BLAS for translucent (water) triangles of {@code mesh}.
   *
   * @param vulkan Vulkan context with RT enabled
   * @param commandPool graphics command pool
   * @param mesh uploaded region mesh with translucent geometry
   * @return BLAS or {@code null} if empty
   */
  public static MeshBlas buildTranslucent(
      VulkanContext vulkan, long commandPool, GpuRegionMesh mesh) {
    if (mesh == null || !mesh.hasTranslucent() || mesh.getTranslucentVertexAddress() == 0L) {
      return null;
    }
    return build(
        vulkan,
        commandPool,
        mesh.getTranslucentVertexAddress(),
        mesh.getTranslucentIndexAddress(),
        mesh.getTranslucentVertexCount(),
        mesh.getTranslucentIndexCount(),
        false);
  }

  /**
   * Builds a triangle BLAS from device addresses.
   *
   * @param opaqueGeometry if {@code true}, mark triangles opaque for RT
   */
  public static MeshBlas build(
      VulkanContext vulkan,
      long commandPool,
      long vertexAddress,
      long indexAddress,
      int vertexCount,
      int indexCount,
      boolean opaqueGeometry) {
    int primitiveCount = indexCount / 3;
    if (primitiveCount <= 0 || vertexAddress == 0L || indexAddress == 0L) {
      return null;
    }

    MeshBlas blas = new MeshBlas();
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkAccelerationStructureGeometryTrianglesDataKHR triangles =
          VkAccelerationStructureGeometryTrianglesDataKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_TRIANGLES_DATA_KHR)
              .vertexFormat(VK_FORMAT_R32G32B32_SFLOAT)
              .vertexStride(GpuRegionMesh.VERTEX_STRIDE)
              .maxVertex(Math.max(0, vertexCount - 1))
              .indexType(VK_INDEX_TYPE_UINT32);
      triangles.vertexData().deviceAddress(vertexAddress);
      triangles.indexData().deviceAddress(indexAddress);

      // Cutout leaves need any-hit — never mark terrain geometry opaque.
      int geomFlags = 0;
      VkAccelerationStructureGeometryKHR.Buffer geometries =
          VkAccelerationStructureGeometryKHR.calloc(1, stack);
      geometries
          .get(0)
          .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR)
          .geometryType(VK_GEOMETRY_TYPE_TRIANGLES_KHR)
          .flags(geomFlags);
      geometries.get(0).geometry().triangles(triangles);
      geometries.position(0);

      VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfo =
          VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);
      buildInfo
          .get(0)
          .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
          .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR)
          .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
          .geometryCount(1)
          .pGeometries(geometries);
      buildInfo.position(0);

      IntBuffer primCounts = stack.ints(primitiveCount);
      VkAccelerationStructureBuildSizesInfoKHR sizeInfo =
          VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR);
      vkGetAccelerationStructureBuildSizesKHR(
          device,
          VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
          buildInfo.get(0),
          primCounts,
          sizeInfo);

      AllocatedBuffer asBuf =
          AllocatedBuffer.create(
              vulkan,
              device,
              stack,
              sizeInfo.accelerationStructureSize(),
              VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR
                  | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
                  | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
      blas.buffer = asBuf.buffer;
      blas.memory = asBuf.memory;

      VkAccelerationStructureCreateInfoKHR createInfo =
          VkAccelerationStructureCreateInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
              .buffer(blas.buffer)
              .size(sizeInfo.accelerationStructureSize())
              .type(VK_ACCELERATION_STRUCTURE_TYPE_BOTTOM_LEVEL_KHR);
      LongBuffer asPtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateAccelerationStructureKHR(device, createInfo, null, asPtr), "create BLAS");
      blas.accelerationStructure = asPtr.get(0);

      AllocatedBuffer scratch =
          AllocatedBuffer.create(
              vulkan,
              device,
              stack,
              sizeInfo.buildScratchSize(),
              VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
      long scratchAddr = vulkan.getBufferDeviceAddress(scratch.buffer);

      buildInfo.get(0).dstAccelerationStructure(blas.accelerationStructure);
      buildInfo.get(0).scratchData().deviceAddress(scratchAddr);
      buildInfo.position(0);

      VkAccelerationStructureBuildRangeInfoKHR.Buffer ranges =
          VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack);
      ranges
          .get(0)
          .primitiveCount(primitiveCount)
          .primitiveOffset(0)
          .firstVertex(0)
          .transformOffset(0);
      ranges.position(0);
      PointerBuffer pRanges = stack.pointers(ranges.address());

      submitOneTime(
          vulkan, commandPool, cmd -> vkCmdBuildAccelerationStructuresKHR(cmd, buildInfo, pRanges));

      scratch.free(device);

      VkAccelerationStructureDeviceAddressInfoKHR addrInfo =
          VkAccelerationStructureDeviceAddressInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_DEVICE_ADDRESS_INFO_KHR)
              .accelerationStructure(blas.accelerationStructure);
      blas.deviceAddress = vkGetAccelerationStructureDeviceAddressKHR(device, addrInfo);
    }
    return blas;
  }

  public long getHandle() {
    return accelerationStructure;
  }

  public long getDeviceAddress() {
    return deviceAddress;
  }

  public void free(VulkanContext vulkan) {
    VkDevice device = vulkan.getDevice();
    if (accelerationStructure != VK_NULL_HANDLE) {
      vkDestroyAccelerationStructureKHR(device, accelerationStructure, null);
      accelerationStructure = VK_NULL_HANDLE;
    }
    if (buffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, buffer, null);
      buffer = VK_NULL_HANDLE;
    }
    if (memory != VK_NULL_HANDLE) {
      vkFreeMemory(device, memory, null);
      memory = VK_NULL_HANDLE;
    }
    deviceAddress = 0L;
  }

  @FunctionalInterface
  interface CmdRecorder {
    void record(VkCommandBuffer cmd);
  }

  static void submitOneTime(VulkanContext vulkan, long commandPool, CmdRecorder recorder) {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo alloc =
          VkCommandBufferAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(1);
      PointerBuffer ptr = stack.mallocPointer(1);
      VulkanContext.checkVk(vkAllocateCommandBuffers(device, alloc, ptr), "alloc AS cmd");
      VkCommandBuffer cmd = new VkCommandBuffer(ptr.get(0), device);
      VkCommandBufferBeginInfo begin =
          VkCommandBufferBeginInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
              .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      VulkanContext.checkVk(vkBeginCommandBuffer(cmd, begin), "begin AS cmd");
      recorder.record(cmd);
      VulkanContext.checkVk(vkEndCommandBuffer(cmd), "end AS cmd");

      LongBuffer fencePtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateFence(
              device,
              VkFenceCreateInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO),
              null,
              fencePtr),
          "create AS fence");
      long fence = fencePtr.get(0);
      VkSubmitInfo submit =
          VkSubmitInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
              .pCommandBuffers(stack.pointers(cmd.address()));
      VulkanContext.checkVk(vkQueueSubmit(vulkan.getGraphicsQueue(), submit, fence), "submit AS");
      vkWaitForFences(device, fencePtr, true, -1L);
      vkDestroyFence(device, fence, null);
      vkFreeCommandBuffers(device, commandPool, cmd);
      vkQueueWaitIdle(vulkan.getGraphicsQueue());
    }
  }

  static final class AllocatedBuffer {
    final long buffer;
    final long memory;

    AllocatedBuffer(long buffer, long memory) {
      this.buffer = buffer;
      this.memory = memory;
    }

    static AllocatedBuffer create(
        VulkanContext vulkan, VkDevice device, MemoryStack stack, long size, int usage) {
      VkBufferCreateInfo info =
          VkBufferCreateInfo.calloc(stack)
              .size(Math.max(256L, size))
              .usage(usage)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      LongBuffer buf = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateBuffer(device, info, null, buf), "create AS buffer");
      long buffer = buf.get(0);

      VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
      vkGetBufferMemoryRequirements(device, buffer, req);
      VkMemoryAllocateFlagsInfo flags =
          VkMemoryAllocateFlagsInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO)
              .flags(VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT);
      VkMemoryAllocateInfo alloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(req.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(req.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT))
              .pNext(flags.address());
      LongBuffer mem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, alloc, null, mem), "alloc AS memory");
      vkBindBufferMemory(device, buffer, mem.get(0), 0);
      return new AllocatedBuffer(buffer, mem.get(0));
    }

    void free(VkDevice device) {
      vkDestroyBuffer(device, buffer, null);
      vkFreeMemory(device, memory, null);
    }
  }
}
