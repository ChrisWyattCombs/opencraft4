package opencraft.graphics.render.rt;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_GEOMETRY_TYPE_INSTANCES_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCmdBuildAccelerationStructuresKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkCreateAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkDestroyAccelerationStructureKHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.vkGetAccelerationStructureBuildSizesKHR;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;
import opencraft.graphics.VulkanContext;
import opencraft.graphics.render.GpuRegionMesh;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkAccelerationStructureBuildGeometryInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildRangeInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureBuildSizesInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureCreateInfoKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryInstancesDataKHR;
import org.lwjgl.vulkan.VkAccelerationStructureGeometryKHR;
import org.lwjgl.vulkan.VkAccelerationStructureInstanceKHR;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateFlagsInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkTransformMatrixKHR;

/**
 * Top-level acceleration structure over opaque region/LOD BLAS instances. Also owns a mesh-info
 * SSBO for closest-hit attribute fetch.
 */
public final class SceneTlas {

  /** 16 bytes: vertexAddr, indexAddr (two uint64). */
  public static final int MESH_INFO_STRIDE = 16;

  private long accelerationStructure = VK_NULL_HANDLE;
  private long asBuffer = VK_NULL_HANDLE;
  private long asMemory = VK_NULL_HANDLE;
  private long instanceBuffer = VK_NULL_HANDLE;
  private long instanceMemory = VK_NULL_HANDLE;
  private long meshInfoBuffer = VK_NULL_HANDLE;
  private long meshInfoMemory = VK_NULL_HANDLE;
  private long meshInfoAddress;
  private int instanceCount;

  /**
   * Cull mask bit for near opaque terrain (voxel chunks).
   *
   * <p>Used by primary visibility and water mirror rays.
   */
  public static final int MASK_OPAQUE = 0x01;

  /** Cull mask bit for translucent water geometry. */
  public static final int MASK_WATER = 0x02;

  /**
   * Cull mask bit for distant LOD heightmaps.
   *
   * <p>Excluded from water mirror rays so reflections do not traverse the full LOD TLAS.
   */
  public static final int MASK_LOD = 0x04;

  /** Hit SBT record: opaque closest-hit. */
  public static final int SBT_OPAQUE = 0;

  /** Hit SBT record: water closest-hit. */
  public static final int SBT_WATER = 1;

  private static final class InstanceSpec {
    final MeshBlas blas;
    final long vertexAddress;
    final long indexAddress;
    final int mask;
    final int sbtOffset;

    InstanceSpec(MeshBlas blas, long vertexAddress, long indexAddress, int mask, int sbtOffset) {
      this.blas = blas;
      this.vertexAddress = vertexAddress;
      this.indexAddress = indexAddress;
      this.mask = mask;
      this.sbtOffset = sbtOffset;
    }
  }

  /**
   * Rebuilds the TLAS from meshes that already have opaque and/or translucent BLASes.
   *
   * @param vulkan RT-capable context
   * @param commandPool graphics pool
   * @param meshes region and LOD meshes
   */
  public void rebuild(VulkanContext vulkan, long commandPool, List<GpuRegionMesh> meshes) {
    // Previous frames may still be tracing against the old TLAS — must idle before destroy.
    vulkan.waitIdle();
    free(vulkan);
    List<InstanceSpec> specs = new ArrayList<>();
    for (GpuRegionMesh mesh : meshes) {
      if (mesh == null) {
        continue;
      }
      if (mesh.getOpaqueBlas() != null && mesh.hasOpaque()) {
        int opaqueMask = mesh.isDistantLod() ? MASK_LOD : MASK_OPAQUE;
        specs.add(
            new InstanceSpec(
                mesh.getOpaqueBlas(),
                mesh.getOpaqueVertexAddress(),
                mesh.getOpaqueIndexAddress(),
                opaqueMask,
                SBT_OPAQUE));
      }
      if (mesh.getTranslucentBlas() != null && mesh.hasTranslucent()) {
        specs.add(
            new InstanceSpec(
                mesh.getTranslucentBlas(),
                mesh.getTranslucentVertexAddress(),
                mesh.getTranslucentIndexAddress(),
                MASK_WATER,
                SBT_WATER));
      }
    }
    instanceCount = specs.size();
    if (instanceCount == 0) {
      return;
    }

    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      long instanceBytes = (long) instanceCount * VkAccelerationStructureInstanceKHR.SIZEOF;
      instanceBuffer =
          createBuffer(
              device,
              stack,
              instanceBytes,
              VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
                  | VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_BUILD_INPUT_READ_ONLY_BIT_KHR
                  | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT
                  | VK_BUFFER_USAGE_TRANSFER_DST_BIT);
      instanceMemory = allocateHostVisibleAddressable(vulkan, device, stack, instanceBuffer);

      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, instanceMemory, 0, instanceBytes, 0, mapped);
      ByteBuffer instanceBytesBuf = mapped.getByteBuffer(0, (int) instanceBytes);

      long meshInfoBytes = (long) instanceCount * MESH_INFO_STRIDE;
      meshInfoBuffer =
          createBuffer(
              device,
              stack,
              meshInfoBytes,
              VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
      meshInfoMemory = allocateHostVisibleAddressable(vulkan, device, stack, meshInfoBuffer);
      vkMapMemory(device, meshInfoMemory, 0, meshInfoBytes, 0, mapped);
      ByteBuffer meshInfoBytesBuf = mapped.getByteBuffer(0, (int) meshInfoBytes);

      float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

      for (int i = 0; i < instanceCount; i++) {
        InstanceSpec spec = specs.get(i);
        long offset = (long) i * VkAccelerationStructureInstanceKHR.SIZEOF;
        VkAccelerationStructureInstanceKHR inst =
            VkAccelerationStructureInstanceKHR.create(
                MemoryUtil.memAddress(instanceBytesBuf) + offset);
        VkTransformMatrixKHR xform = inst.transform();
        for (int m = 0; m < 12; m++) {
          xform.matrix(m, identity[m]);
        }
        inst.instanceCustomIndex(i);
        inst.mask(spec.mask);
        inst.instanceShaderBindingTableRecordOffset(spec.sbtOffset);
        inst.flags(VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR);
        inst.accelerationStructureReference(spec.blas.getDeviceAddress());

        meshInfoBytesBuf.putLong(i * MESH_INFO_STRIDE, spec.vertexAddress);
        meshInfoBytesBuf.putLong(i * MESH_INFO_STRIDE + 8, spec.indexAddress);
      }
      vkUnmapMemory(device, instanceMemory);
      vkUnmapMemory(device, meshInfoMemory);
      meshInfoAddress = vulkan.getBufferDeviceAddress(meshInfoBuffer);

      long instanceAddr = vulkan.getBufferDeviceAddress(instanceBuffer);

      VkAccelerationStructureGeometryInstancesDataKHR instancesData =
          VkAccelerationStructureGeometryInstancesDataKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_INSTANCES_DATA_KHR)
              .arrayOfPointers(false);
      instancesData.data().deviceAddress(instanceAddr);

      VkAccelerationStructureGeometryKHR.Buffer geometries =
          VkAccelerationStructureGeometryKHR.calloc(1, stack);
      geometries
          .get(0)
          .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_GEOMETRY_KHR)
          .geometryType(VK_GEOMETRY_TYPE_INSTANCES_KHR);
      geometries.get(0).geometry().instances(instancesData);
      geometries.position(0);

      VkAccelerationStructureBuildGeometryInfoKHR.Buffer buildInfo =
          VkAccelerationStructureBuildGeometryInfoKHR.calloc(1, stack);
      buildInfo
          .get(0)
          .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_GEOMETRY_INFO_KHR)
          .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR)
          .flags(VK_BUILD_ACCELERATION_STRUCTURE_PREFER_FAST_TRACE_BIT_KHR)
          .geometryCount(1)
          .pGeometries(geometries);
      buildInfo.position(0);

      IntBuffer primCounts = stack.ints(instanceCount);
      VkAccelerationStructureBuildSizesInfoKHR sizeInfo =
          VkAccelerationStructureBuildSizesInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_BUILD_SIZES_INFO_KHR);
      vkGetAccelerationStructureBuildSizesKHR(
          device,
          VK_ACCELERATION_STRUCTURE_BUILD_TYPE_DEVICE_KHR,
          buildInfo.get(0),
          primCounts,
          sizeInfo);

      MeshBlas.AllocatedBuffer asAlloc =
          MeshBlas.AllocatedBuffer.create(
              vulkan,
              device,
              stack,
              sizeInfo.accelerationStructureSize(),
              VK_BUFFER_USAGE_ACCELERATION_STRUCTURE_STORAGE_BIT_KHR
                  | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
                  | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT);
      asBuffer = asAlloc.buffer;
      asMemory = asAlloc.memory;

      VkAccelerationStructureCreateInfoKHR createInfo =
          VkAccelerationStructureCreateInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_ACCELERATION_STRUCTURE_CREATE_INFO_KHR)
              .buffer(asBuffer)
              .size(sizeInfo.accelerationStructureSize())
              .type(VK_ACCELERATION_STRUCTURE_TYPE_TOP_LEVEL_KHR);
      LongBuffer asPtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateAccelerationStructureKHR(device, createInfo, null, asPtr), "create TLAS");
      accelerationStructure = asPtr.get(0);

      MeshBlas.AllocatedBuffer scratch =
          MeshBlas.AllocatedBuffer.create(
              vulkan,
              device,
              stack,
              sizeInfo.buildScratchSize(),
              VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT);
      buildInfo.get(0).dstAccelerationStructure(accelerationStructure);
      buildInfo.get(0).scratchData().deviceAddress(vulkan.getBufferDeviceAddress(scratch.buffer));
      buildInfo.position(0);

      VkAccelerationStructureBuildRangeInfoKHR.Buffer ranges =
          VkAccelerationStructureBuildRangeInfoKHR.calloc(1, stack);
      ranges
          .get(0)
          .primitiveCount(instanceCount)
          .primitiveOffset(0)
          .firstVertex(0)
          .transformOffset(0);
      ranges.position(0);
      PointerBuffer pRanges = stack.pointers(ranges.address());

      MeshBlas.submitOneTime(
          vulkan, commandPool, cmd -> vkCmdBuildAccelerationStructuresKHR(cmd, buildInfo, pRanges));
      scratch.free(device);
    }
  }

  public long getHandle() {
    return accelerationStructure;
  }

  public long getMeshInfoBuffer() {
    return meshInfoBuffer;
  }

  public int getInstanceCount() {
    return instanceCount;
  }

  public boolean isEmpty() {
    return accelerationStructure == VK_NULL_HANDLE || instanceCount == 0;
  }

  public void free(VulkanContext vulkan) {
    VkDevice device = vulkan.getDevice();
    if (accelerationStructure != VK_NULL_HANDLE) {
      vkDestroyAccelerationStructureKHR(device, accelerationStructure, null);
      accelerationStructure = VK_NULL_HANDLE;
    }
    destroyBuf(device, asBuffer, asMemory);
    destroyBuf(device, instanceBuffer, instanceMemory);
    destroyBuf(device, meshInfoBuffer, meshInfoMemory);
    asBuffer = instanceBuffer = meshInfoBuffer = VK_NULL_HANDLE;
    asMemory = instanceMemory = meshInfoMemory = VK_NULL_HANDLE;
    meshInfoAddress = 0L;
    instanceCount = 0;
  }

  private static void destroyBuf(VkDevice device, long buffer, long memory) {
    if (buffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, buffer, null);
    }
    if (memory != VK_NULL_HANDLE) {
      vkFreeMemory(device, memory, null);
    }
  }

  private static long createBuffer(VkDevice device, MemoryStack stack, long size, int usage) {
    VkBufferCreateInfo info =
        VkBufferCreateInfo.calloc(stack)
            .size(Math.max(16L, size))
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    LongBuffer buf = stack.mallocLong(1);
    VulkanContext.checkVk(vkCreateBuffer(device, info, null, buf), "create TLAS buffer");
    return buf.get(0);
  }

  private static long allocateHostVisibleAddressable(
      VulkanContext vulkan, VkDevice device, MemoryStack stack, long buffer) {
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
                vulkan.findMemoryType(
                    req.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT))
            .pNext(flags.address());
    LongBuffer mem = stack.mallocLong(1);
    VulkanContext.checkVk(vkAllocateMemory(device, alloc, null, mem), "alloc TLAS host memory");
    vkBindBufferMemory(device, buffer, mem.get(0), 0);
    return mem.get(0);
  }
}
