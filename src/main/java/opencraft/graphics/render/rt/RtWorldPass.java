package opencraft.graphics.render.rt;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_anyhit_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_closesthit_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_miss_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_raygen_shader;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_ANY_HIT_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_MISS_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_STAGE_RAYGEN_BIT_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_SHADER_UNUSED_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCmdTraceRaysKHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkCreateRayTracingPipelinesKHR;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.vkGetRayTracingShaderGroupHandlesKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_WHOLE_SIZE;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK12.VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_MEMORY_ALLOCATE_DEVICE_ADDRESS_BIT;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_FLAGS_INFO;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import opencraft.graphics.VulkanContext;
import opencraft.graphics.render.GpuRegionMesh;
import opencraft.graphics.render.ShaderCompiler;
import opencraft.player.Player;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateFlagsInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkRayTracingPipelineCreateInfoKHR;
import org.lwjgl.vulkan.VkRayTracingShaderGroupCreateInfoKHR;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkStridedDeviceAddressRegionKHR;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.lwjgl.vulkan.VkWriteDescriptorSetAccelerationStructureKHR;

/**
 * Hardware ray-tracing primary visibility pass: rebuilds TLAS from mesh BLASes, traces primary rays
 * into a storage image that replaces the raster offscreen color target.
 */
public final class RtWorldPass implements AutoCloseable {

  private static final int UBO_SIZE = 192; // 2×mat4 + 4×vec4 (incl. sunDir)

  /** Direction toward the sun (shared with raster sky). */
  private static final float SUN_X = 0.55f;

  private static final float SUN_Y = 0.82f;
  private static final float SUN_Z = 0.18f;

  /** Cosine of sun disc angular radius (~2.5°) for miss shader (avoids acos). */
  private static final float SUN_COS_ANGULAR_RADIUS = (float) Math.cos(Math.toRadians(2.5));

  // Groups: raygen, miss, shadowMiss, opaqueHit, waterHit
  private static final int GROUP_COUNT = 5;
  private static final int HIT_GROUP_COUNT = 2;

  private final VulkanContext vulkan;
  private final long commandPool;
  private final SceneTlas tlas = new SceneTlas();

  private long descriptorSetLayout = VK_NULL_HANDLE;
  private long pipelineLayout = VK_NULL_HANDLE;
  private long pipeline = VK_NULL_HANDLE;
  private long descriptorPool = VK_NULL_HANDLE;
  private long descriptorSet = VK_NULL_HANDLE;

  private long sbtBuffer = VK_NULL_HANDLE;
  private long sbtMemory = VK_NULL_HANDLE;
  private long sbtAddress;
  private int handleSizeAligned;
  private VkStridedDeviceAddressRegionKHR raygenRegion;
  private VkStridedDeviceAddressRegionKHR missRegion;
  private VkStridedDeviceAddressRegionKHR hitRegion;
  private VkStridedDeviceAddressRegionKHR callableRegion;

  private long uboBuffer = VK_NULL_HANDLE;
  private long uboMemory = VK_NULL_HANDLE;
  private ByteBuffer uboMapped;

  /** Half-res storage target written by raygen (Minecraft-style cheap RT). */
  private long traceImage = VK_NULL_HANDLE;

  private long traceMemory = VK_NULL_HANDLE;
  private long traceImageView = VK_NULL_HANDLE;
  private int traceWidth;
  private int traceHeight;

  /** Full-res present image (upscaled from trace). */
  private long outputImage = VK_NULL_HANDLE;

  private long outputMemory = VK_NULL_HANDLE;
  private long outputImageView = VK_NULL_HANDLE;
  private int outputWidth;
  private int outputHeight;

  /**
   * Trace at half resolution then bilinear upscale — same cost idea as Minecraft RTX (ray-trace
   * small, denoise/upscale). Without DLSS this is a linear blit.
   */
  private static final float TRACE_SCALE = 0.5f;

  private long textureImageView = VK_NULL_HANDLE;
  private long textureSampler = VK_NULL_HANDLE;
  private long cloudImageView = VK_NULL_HANDLE;
  private long cloudSampler = VK_NULL_HANDLE;

  private boolean sceneDirty = true;

  public RtWorldPass(VulkanContext vulkan, long frameCommandPoolUnused) {
    this.vulkan = vulkan;
    this.commandPool = createAsCommandPool(vulkan);
    ShaderCompiler.invalidateCache();
    createDescriptorLayout();
    createPipeline();
    createSbt();
    createUbo();
    createDescriptorPoolAndSet();
  }

  private static long createAsCommandPool(VulkanContext vulkan) {
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkCommandPoolCreateInfo info =
          org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack)
              .sType(org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO)
              .flags(org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
              .queueFamilyIndex(vulkan.getGraphicsQueueFamily());
      LongBuffer pool = stack.mallocLong(1);
      VulkanContext.checkVk(
          org.lwjgl.vulkan.VK10.vkCreateCommandPool(vulkan.getDevice(), info, null, pool),
          "create RT AS command pool");
      return pool.get(0);
    }
  }

  /**
   * Binds the block texture atlas used by opaque/water hit shaders.
   *
   * @param imageView atlas image view
   * @param sampler atlas sampler
   */
  public void setTexture(long imageView, long sampler) {
    this.textureImageView = imageView;
    this.textureSampler = sampler;
    updateDescriptors();
  }

  /**
   * Binds the scrolling sky cloud texture (shared with the raster sky pass).
   *
   * @param imageView cloud image view
   * @param sampler repeating cloud sampler
   */
  public void setCloudTexture(long imageView, long sampler) {
    this.cloudImageView = imageView;
    this.cloudSampler = sampler;
    updateDescriptors();
  }

  /** Marks the TLAS dirty so the next {@link #prepareFrame} rebuilds acceleration structures. */
  public void markSceneDirty() {
    sceneDirty = true;
  }

  /**
   * Ensures the half-res trace target and full-res present image match the window size.
   *
   * @param width present width in pixels
   * @param height present height in pixels
   */
  public void ensureOutputSize(int width, int height) {
    width = Math.max(1, width);
    height = Math.max(1, height);
    int tw = Math.max(1, Math.round(width * TRACE_SCALE));
    int th = Math.max(1, Math.round(height * TRACE_SCALE));
    if (outputImage != VK_NULL_HANDLE
        && outputWidth == width
        && outputHeight == height
        && traceWidth == tw
        && traceHeight == th) {
      return;
    }
    destroyOutputImage();
    outputWidth = width;
    outputHeight = height;
    traceWidth = tw;
    traceHeight = th;
    createOutputImage();
    updateDescriptors();
  }

  /**
   * Returns the full-resolution RT present image (TRANSFER_SRC after {@link #recordTrace}).
   *
   * @return Vulkan image handle
   */
  public long getOutputImage() {
    return outputImage;
  }

  /**
   * Returns the full-resolution RT present width in pixels.
   *
   * @return width
   */
  public int getOutputWidth() {
    return outputWidth;
  }

  /**
   * Returns the full-resolution RT present height in pixels.
   *
   * @return height
   */
  public int getOutputHeight() {
    return outputHeight;
  }

  /** Max new BLASes built per frame to avoid multi-second stalls when chunks stream in. */
  private static final int MAX_BLAS_BUILDS_PER_FRAME = 6;

  /**
   * Ensures BLASes exist for meshes, rebuilds TLAS if needed, updates camera UBO.
   *
   * @param meshes all resident region + LOD meshes
   * @param player camera
   * @param fogStart fog start distance
   * @param fogEnd fog end distance
   * @param fogR fog color red
   * @param fogG fog color green
   * @param fogB fog color blue
   * @param underwater underwater flag
   * @param tileSpanU atlas tile U span
   * @param tileSpanV atlas tile V span
   * @param cloudTimeSeconds scrolling cloud layer time in seconds
   */
  public void prepareFrame(
      Collection<GpuRegionMesh> meshes,
      Player player,
      float fogStart,
      float fogEnd,
      float fogR,
      float fogG,
      float fogB,
      boolean underwater,
      float tileSpanU,
      float tileSpanV,
      float cloudTimeSeconds) {
    List<GpuRegionMesh> list = new ArrayList<>();
    boolean builtAny = false;
    int buildsLeft = MAX_BLAS_BUILDS_PER_FRAME;
    boolean pendingBuilds = false;
    for (GpuRegionMesh mesh : meshes) {
      if (mesh == null) {
        continue;
      }
      if (mesh.hasOpaque() && mesh.getOpaqueBlas() == null) {
        if (buildsLeft <= 0) {
          pendingBuilds = true;
        } else {
          MeshBlas blas = MeshBlas.build(vulkan, commandPool, mesh);
          if (blas != null) {
            mesh.setOpaqueBlas(blas);
            builtAny = true;
            buildsLeft--;
          }
        }
      }
      if (mesh.hasTranslucent() && mesh.getTranslucentBlas() == null) {
        if (buildsLeft <= 0) {
          pendingBuilds = true;
        } else {
          MeshBlas water = MeshBlas.buildTranslucent(vulkan, commandPool, mesh);
          if (water != null) {
            mesh.setTranslucentBlas(water);
            builtAny = true;
            buildsLeft--;
          }
        }
      }
      if (mesh.getOpaqueBlas() != null || mesh.getTranslucentBlas() != null) {
        list.add(mesh);
      }
    }
    if (builtAny || sceneDirty) {
      tlas.rebuild(vulkan, commandPool, list);
      sceneDirty = pendingBuilds;
      updateDescriptors();
    } else if (pendingBuilds) {
      sceneDirty = true;
    }
    updateUbo(
        player,
        fogStart,
        fogEnd,
        fogR,
        fogG,
        fogB,
        underwater,
        tileSpanU,
        tileSpanV,
        cloudTimeSeconds);
  }

  public boolean hasGeometry() {
    return !tlas.isEmpty();
  }

  /**
   * Rebuilds the TLAS from the given live meshes only (call before freeing retired meshes).
   *
   * @param liveMeshes meshes still in the scene
   */
  public void rebuildSceneOnly(Collection<GpuRegionMesh> liveMeshes) {
    List<GpuRegionMesh> list = new ArrayList<>();
    for (GpuRegionMesh mesh : liveMeshes) {
      if (mesh != null && (mesh.getOpaqueBlas() != null || mesh.getTranslucentBlas() != null)) {
        list.add(mesh);
      }
    }
    tlas.rebuild(vulkan, commandPool, list);
    sceneDirty = false;
    updateDescriptors();
  }

  /**
   * Records iterative ray tracing at half-res, then bilinear-upscales to full-res present image
   * (TRANSFER_SRC_OPTIMAL).
   *
   * @param cmd command buffer
   */
  public void recordTrace(VkCommandBuffer cmd) {
    if (tlas.isEmpty() || traceImage == VK_NULL_HANDLE || outputImage == VK_NULL_HANDLE) {
      return;
    }
    try (MemoryStack stack = stackPush()) {
      // GENERAL for storage write (half-res)
      VkImageMemoryBarrier.Buffer toGeneral = VkImageMemoryBarrier.calloc(1, stack);
      toGeneral
          .get(0)
          .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
          .srcAccessMask(0)
          .dstAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
          .newLayout(VK_IMAGE_LAYOUT_GENERAL)
          .image(traceImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      toGeneral
          .get(0)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      toGeneral.position(0);
      vkCmdPipelineBarrier(
          cmd,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
          0,
          null,
          null,
          toGeneral);

      vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR, pipeline);
      vkCmdBindDescriptorSets(
          cmd,
          VK_PIPELINE_BIND_POINT_RAY_TRACING_KHR,
          pipelineLayout,
          0,
          stack.longs(descriptorSet),
          null);

      // Single dispatch: primary + shadow + water transmission + sky-GI all from raygen.
      vkCmdTraceRaysKHR(
          cmd, raygenRegion, missRegion, hitRegion, callableRegion, traceWidth, traceHeight, 1);

      // Trace → TRANSFER_SRC; present → TRANSFER_DST
      VkImageMemoryBarrier.Buffer prepBlit = VkImageMemoryBarrier.calloc(2, stack);
      prepBlit
          .get(0)
          .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
          .srcAccessMask(VK_ACCESS_SHADER_WRITE_BIT)
          .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_GENERAL)
          .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
          .image(traceImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      prepBlit
          .get(0)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      prepBlit
          .get(1)
          .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
          .srcAccessMask(0)
          .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
          .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
          .image(outputImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      prepBlit
          .get(1)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      prepBlit.position(0);
      vkCmdPipelineBarrier(
          cmd,
          VK_PIPELINE_STAGE_RAY_TRACING_SHADER_BIT_KHR,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          0,
          null,
          null,
          prepBlit);

      VkImageBlit.Buffer blit = VkImageBlit.calloc(1, stack);
      blit.get(0).srcSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
      blit.get(0).dstSubresource().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1);
      blit.get(0).srcOffsets(0).set(0, 0, 0);
      blit.get(0).srcOffsets(1).set(traceWidth, traceHeight, 1);
      blit.get(0).dstOffsets(0).set(0, 0, 0);
      blit.get(0).dstOffsets(1).set(outputWidth, outputHeight, 1);
      blit.position(0);
      vkCmdBlitImage(
          cmd,
          traceImage,
          VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
          outputImage,
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          blit,
          VK_FILTER_LINEAR);

      VkImageMemoryBarrier.Buffer toSrc = VkImageMemoryBarrier.calloc(1, stack);
      toSrc
          .get(0)
          .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
          .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
          .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
          .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
          .image(outputImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      toSrc
          .get(0)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      toSrc.position(0);
      vkCmdPipelineBarrier(
          cmd,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          0,
          null,
          null,
          toSrc);
    }
  }

  @Override
  public void close() {
    VkDevice device = vulkan.getDevice();
    vulkan.waitIdle();
    tlas.free(vulkan);
    destroyOutputImage();
    if (uboMapped != null) {
      vkUnmapMemory(device, uboMemory);
      uboMapped = null;
    }
    if (uboBuffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, uboBuffer, null);
      uboBuffer = VK_NULL_HANDLE;
    }
    if (uboMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, uboMemory, null);
      uboMemory = VK_NULL_HANDLE;
    }
    if (sbtBuffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, sbtBuffer, null);
      sbtBuffer = VK_NULL_HANDLE;
    }
    if (sbtMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, sbtMemory, null);
      sbtMemory = VK_NULL_HANDLE;
    }
    if (pipeline != VK_NULL_HANDLE) {
      vkDestroyPipeline(device, pipeline, null);
      pipeline = VK_NULL_HANDLE;
    }
    if (pipelineLayout != VK_NULL_HANDLE) {
      vkDestroyPipelineLayout(device, pipelineLayout, null);
      pipelineLayout = VK_NULL_HANDLE;
    }
    if (descriptorPool != VK_NULL_HANDLE) {
      vkDestroyDescriptorPool(device, descriptorPool, null);
      descriptorPool = VK_NULL_HANDLE;
    }
    if (descriptorSetLayout != VK_NULL_HANDLE) {
      vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
      descriptorSetLayout = VK_NULL_HANDLE;
    }
    if (raygenRegion != null) {
      raygenRegion.free();
      missRegion.free();
      hitRegion.free();
      callableRegion.free();
      raygenRegion = missRegion = hitRegion = callableRegion = null;
    }
    if (commandPool != VK_NULL_HANDLE) {
      org.lwjgl.vulkan.VK10.vkDestroyCommandPool(device, commandPool, null);
    }
  }

  private void createDescriptorLayout() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(6, stack);
      bindings
          .get(0)
          .binding(0)
          .descriptorType(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR)
          .descriptorCount(1)
          .stageFlags(
              VK_SHADER_STAGE_RAYGEN_BIT_KHR
                  | VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR
                  | VK_SHADER_STAGE_ANY_HIT_BIT_KHR
                  | VK_SHADER_STAGE_MISS_BIT_KHR);
      bindings
          .get(1)
          .binding(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_RAYGEN_BIT_KHR);
      bindings
          .get(2)
          .binding(2)
          .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
          .descriptorCount(1)
          .stageFlags(
              VK_SHADER_STAGE_RAYGEN_BIT_KHR
                  | VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR
                  | VK_SHADER_STAGE_ANY_HIT_BIT_KHR
                  | VK_SHADER_STAGE_MISS_BIT_KHR);
      bindings
          .get(3)
          .binding(3)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
      bindings
          .get(4)
          .binding(4)
          .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR | VK_SHADER_STAGE_ANY_HIT_BIT_KHR);
      bindings
          .get(5)
          .binding(5)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_RAYGEN_BIT_KHR | VK_SHADER_STAGE_MISS_BIT_KHR);
      bindings.position(0);

      VkDescriptorSetLayoutCreateInfo info =
          VkDescriptorSetLayoutCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
              .pBindings(bindings);
      LongBuffer out = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateDescriptorSetLayout(device, info, null, out), "create RT set layout");
      descriptorSetLayout = out.get(0);
    }
  }

  private void createPipeline() {
    VkDevice device = vulkan.getDevice();
    ByteBuffer rgen = ShaderCompiler.compileGlsl("shaders/rt/raygen.rgen", shaderc_raygen_shader);
    ByteBuffer rmiss = ShaderCompiler.compileGlsl("shaders/rt/miss.rmiss", shaderc_miss_shader);
    ByteBuffer rshadow = ShaderCompiler.compileGlsl("shaders/rt/shadow.rmiss", shaderc_miss_shader);
    ByteBuffer rchit =
        ShaderCompiler.compileGlsl("shaders/rt/closesthit.rchit", shaderc_closesthit_shader);
    ByteBuffer rwater =
        ShaderCompiler.compileGlsl("shaders/rt/water.rchit", shaderc_closesthit_shader);
    ByteBuffer rahit = ShaderCompiler.compileGlsl("shaders/rt/anyhit.rahit", shaderc_anyhit_shader);
    try (MemoryStack stack = stackPush()) {
      long modRgen = createModule(device, stack, rgen);
      long modMiss = createModule(device, stack, rmiss);
      long modShadow = createModule(device, stack, rshadow);
      long modHit = createModule(device, stack, rchit);
      long modWater = createModule(device, stack, rwater);
      long modAny = createModule(device, stack, rahit);

      VkPipelineShaderStageCreateInfo.Buffer stages =
          VkPipelineShaderStageCreateInfo.calloc(6, stack);
      stages
          .get(0)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_RAYGEN_BIT_KHR)
          .module(modRgen)
          .pName(stack.UTF8("main"));
      stages
          .get(1)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_MISS_BIT_KHR)
          .module(modMiss)
          .pName(stack.UTF8("main"));
      stages
          .get(2)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_MISS_BIT_KHR)
          .module(modShadow)
          .pName(stack.UTF8("main"));
      stages
          .get(3)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR)
          .module(modHit)
          .pName(stack.UTF8("main"));
      stages
          .get(4)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_CLOSEST_HIT_BIT_KHR)
          .module(modWater)
          .pName(stack.UTF8("main"));
      stages
          .get(5)
          .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
          .stage(VK_SHADER_STAGE_ANY_HIT_BIT_KHR)
          .module(modAny)
          .pName(stack.UTF8("main"));
      stages.position(0);

      VkRayTracingShaderGroupCreateInfoKHR.Buffer groups =
          VkRayTracingShaderGroupCreateInfoKHR.calloc(GROUP_COUNT, stack);
      // 0 raygen
      groups
          .get(0)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR)
          .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
          .generalShader(0)
          .closestHitShader(VK_SHADER_UNUSED_KHR)
          .anyHitShader(VK_SHADER_UNUSED_KHR)
          .intersectionShader(VK_SHADER_UNUSED_KHR);
      // 1 primary miss
      groups
          .get(1)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR)
          .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
          .generalShader(1)
          .closestHitShader(VK_SHADER_UNUSED_KHR)
          .anyHitShader(VK_SHADER_UNUSED_KHR)
          .intersectionShader(VK_SHADER_UNUSED_KHR);
      // 2 shadow miss
      groups
          .get(2)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR)
          .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_GENERAL_KHR)
          .generalShader(2)
          .closestHitShader(VK_SHADER_UNUSED_KHR)
          .anyHitShader(VK_SHADER_UNUSED_KHR)
          .intersectionShader(VK_SHADER_UNUSED_KHR);
      // 3 opaque hit (+ leaf cutout any-hit)
      groups
          .get(3)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR)
          .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR)
          .generalShader(VK_SHADER_UNUSED_KHR)
          .closestHitShader(3)
          .anyHitShader(5)
          .intersectionShader(VK_SHADER_UNUSED_KHR);
      // 4 water hit
      groups
          .get(4)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_SHADER_GROUP_CREATE_INFO_KHR)
          .type(VK_RAY_TRACING_SHADER_GROUP_TYPE_TRIANGLES_HIT_GROUP_KHR)
          .generalShader(VK_SHADER_UNUSED_KHR)
          .closestHitShader(4)
          .anyHitShader(VK_SHADER_UNUSED_KHR)
          .intersectionShader(VK_SHADER_UNUSED_KHR);
      groups.position(0);

      VkPipelineLayoutCreateInfo layoutInfo =
          VkPipelineLayoutCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
              .pSetLayouts(stack.longs(descriptorSetLayout));
      LongBuffer layoutPtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreatePipelineLayout(device, layoutInfo, null, layoutPtr), "create RT layout");
      pipelineLayout = layoutPtr.get(0);

      // Minecraft RTX style: iterative raygen only — no nested TraceRay in hit shaders.
      int recursion = 1;
      System.out.println(
          "[Opencraft] RT pipeline recursion="
              + recursion
              + " (iterative raygen; device max="
              + vulkan.getMaxRayRecursionDepth()
              + ", half-res scale="
              + TRACE_SCALE
              + ")");
      VkRayTracingPipelineCreateInfoKHR.Buffer pipeInfo =
          VkRayTracingPipelineCreateInfoKHR.calloc(1, stack);
      pipeInfo
          .get(0)
          .sType(VK_STRUCTURE_TYPE_RAY_TRACING_PIPELINE_CREATE_INFO_KHR)
          .pStages(stages)
          .pGroups(groups)
          .maxPipelineRayRecursionDepth(recursion)
          .layout(pipelineLayout);
      pipeInfo.position(0);

      LongBuffer pipePtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateRayTracingPipelinesKHR(
              device, VK_NULL_HANDLE, VK_NULL_HANDLE, pipeInfo, null, pipePtr),
          "create RT pipeline");
      pipeline = pipePtr.get(0);

      vkDestroyShaderModule(device, modRgen, null);
      vkDestroyShaderModule(device, modMiss, null);
      vkDestroyShaderModule(device, modShadow, null);
      vkDestroyShaderModule(device, modHit, null);
      vkDestroyShaderModule(device, modWater, null);
      vkDestroyShaderModule(device, modAny, null);
    } finally {
      MemoryUtil.memFree(rgen);
      MemoryUtil.memFree(rmiss);
      MemoryUtil.memFree(rshadow);
      MemoryUtil.memFree(rchit);
      MemoryUtil.memFree(rwater);
      MemoryUtil.memFree(rahit);
    }
  }

  private static long createModule(VkDevice device, MemoryStack stack, ByteBuffer spirv) {
    VkShaderModuleCreateInfo info =
        VkShaderModuleCreateInfo.calloc(stack)
            .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
            .pCode(spirv);
    LongBuffer out = stack.mallocLong(1);
    VulkanContext.checkVk(vkCreateShaderModule(device, info, null, out), "create RT module");
    return out.get(0);
  }

  private void createSbt() {
    VkDevice device = vulkan.getDevice();
    int handleSize = vulkan.getShaderGroupHandleSize();
    int handleAlign = vulkan.getShaderGroupHandleAlignment();
    handleSizeAligned = alignUp(handleSize, handleAlign);
    int baseAlign = Math.max(handleSizeAligned, vulkan.getShaderGroupBaseAlignment());

    // Each SBT region start must be a multiple of shaderGroupBaseAlignment.
    int raygenOff = 0;
    int missOff = alignUp(raygenOff + handleSizeAligned, baseAlign);
    int hitOff = alignUp(missOff + 2 * handleSizeAligned, baseAlign);
    int sbtSize = hitOff + HIT_GROUP_COUNT * handleSizeAligned;

    try (MemoryStack stack = stackPush()) {
      ByteBuffer handles = MemoryUtil.memAlloc(GROUP_COUNT * handleSize);
      VulkanContext.checkVk(
          vkGetRayTracingShaderGroupHandlesKHR(device, pipeline, 0, GROUP_COUNT, handles),
          "get SBT handles");

      ByteBuffer packed = MemoryUtil.memCalloc(sbtSize);
      // Group order in pipeline: 0 raygen, 1 miss, 2 shadowMiss, 3 opaqueHit, 4 waterHit
      copyHandle(handles, handleSize, 0, packed, raygenOff);
      copyHandle(handles, handleSize, 1, packed, missOff);
      copyHandle(handles, handleSize, 2, packed, missOff + handleSizeAligned);
      copyHandle(handles, handleSize, 3, packed, hitOff);
      copyHandle(handles, handleSize, 4, packed, hitOff + handleSizeAligned);
      packed.rewind();

      long usage =
          VK_BUFFER_USAGE_SHADER_BINDING_TABLE_BIT_KHR
              | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT
              | VK_BUFFER_USAGE_TRANSFER_SRC_BIT
              | VK_BUFFER_USAGE_STORAGE_BUFFER_BIT;

      VkBufferCreateInfo bufInfo =
          VkBufferCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
              .size(Math.max(sbtSize, baseAlign))
              .usage((int) usage)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      LongBuffer bufPtr = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateBuffer(device, bufInfo, null, bufPtr), "create SBT buffer");
      sbtBuffer = bufPtr.get(0);

      VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
      vkGetBufferMemoryRequirements(device, sbtBuffer, req);
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
      LongBuffer memPtr = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, alloc, null, memPtr), "alloc SBT memory");
      sbtMemory = memPtr.get(0);
      vkBindBufferMemory(device, sbtBuffer, sbtMemory, 0);

      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, sbtMemory, 0, sbtSize, 0, mapped);
      mapped.getByteBuffer(0, sbtSize).put(packed);
      vkUnmapMemory(device, sbtMemory);

      MemoryUtil.memFree(handles);
      MemoryUtil.memFree(packed);

      sbtAddress = vulkan.getBufferDeviceAddress(sbtBuffer);
      System.out.println(
          "[Opencraft] RT SBT baseAlign="
              + baseAlign
              + " handleAlign="
              + handleSizeAligned
              + " missOff="
              + missOff
              + " hitOff="
              + hitOff);

      raygenRegion =
          VkStridedDeviceAddressRegionKHR.calloc()
              .set(sbtAddress + raygenOff, handleSizeAligned, handleSizeAligned);
      missRegion =
          VkStridedDeviceAddressRegionKHR.calloc()
              .set(sbtAddress + missOff, handleSizeAligned, 2L * handleSizeAligned);
      hitRegion =
          VkStridedDeviceAddressRegionKHR.calloc()
              .set(
                  sbtAddress + hitOff,
                  handleSizeAligned,
                  (long) HIT_GROUP_COUNT * handleSizeAligned);
      callableRegion = VkStridedDeviceAddressRegionKHR.calloc().set(0L, 0L, 0L);
    }
  }

  private static void copyHandle(
      ByteBuffer handles, int handleSize, int groupIndex, ByteBuffer packed, int destOff) {
    handles.position(groupIndex * handleSize);
    packed.position(destOff);
    for (int b = 0; b < handleSize; b++) {
      packed.put(handles.get());
    }
  }

  private void createUbo() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkBufferCreateInfo info =
          VkBufferCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
              .size(UBO_SIZE)
              .usage(VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT | VK_BUFFER_USAGE_SHADER_DEVICE_ADDRESS_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      LongBuffer buf = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateBuffer(device, info, null, buf), "create RT UBO");
      uboBuffer = buf.get(0);
      VkMemoryRequirements req = VkMemoryRequirements.malloc(stack);
      vkGetBufferMemoryRequirements(device, uboBuffer, req);
      VkMemoryAllocateInfo alloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(req.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      req.memoryTypeBits(),
                      VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT));
      LongBuffer mem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, alloc, null, mem), "alloc RT UBO");
      uboMemory = mem.get(0);
      vkBindBufferMemory(device, uboBuffer, uboMemory, 0);
      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, uboMemory, 0, UBO_SIZE, 0, mapped);
      uboMapped = mapped.getByteBuffer(0, UBO_SIZE);
    }
  }

  private void createDescriptorPoolAndSet() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(5, stack);
      sizes.get(0).type(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR).descriptorCount(1);
      sizes.get(1).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE).descriptorCount(1);
      sizes.get(2).type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER).descriptorCount(1);
      sizes.get(3).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER).descriptorCount(2);
      sizes.get(4).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER).descriptorCount(1);
      sizes.position(0);
      VkDescriptorPoolCreateInfo poolInfo =
          VkDescriptorPoolCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
              .pPoolSizes(sizes)
              .maxSets(1);
      LongBuffer poolPtr = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateDescriptorPool(device, poolInfo, null, poolPtr), "RT desc pool");
      descriptorPool = poolPtr.get(0);

      VkDescriptorSetAllocateInfo alloc =
          VkDescriptorSetAllocateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
              .descriptorPool(descriptorPool)
              .pSetLayouts(stack.longs(descriptorSetLayout));
      LongBuffer setPtr = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateDescriptorSets(device, alloc, setPtr), "alloc RT set");
      descriptorSet = setPtr.get(0);
    }
  }

  private void createOutputImage() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      // Half-res storage image for raygen writes
      VkImageCreateInfo traceInfo =
          VkImageCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
              .imageType(VK_IMAGE_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM)
              .mipLevels(1)
              .arrayLayers(1)
              .samples(VK_SAMPLE_COUNT_1_BIT)
              .tiling(VK_IMAGE_TILING_OPTIMAL)
              .usage(VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
              .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
      traceInfo.extent().set(traceWidth, traceHeight, 1);
      LongBuffer img = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImage(device, traceInfo, null, img), "create RT trace");
      traceImage = img.get(0);

      VkMemoryRequirements treq = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device, traceImage, treq);
      VkMemoryAllocateInfo talloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(treq.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      treq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
      LongBuffer tmem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, talloc, null, tmem), "alloc RT trace");
      traceMemory = tmem.get(0);
      vkBindImageMemory(device, traceImage, traceMemory, 0);

      VkImageViewCreateInfo tviewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
              .image(traceImage)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM);
      tviewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      LongBuffer tview = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImageView(device, tviewInfo, null, tview), "RT trace view");
      traceImageView = tview.get(0);

      // Full-res present target (blit destination → swapchain source)
      VkImageCreateInfo presentInfo =
          VkImageCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
              .imageType(VK_IMAGE_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM)
              .mipLevels(1)
              .arrayLayers(1)
              .samples(VK_SAMPLE_COUNT_1_BIT)
              .tiling(VK_IMAGE_TILING_OPTIMAL)
              .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
              .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
      presentInfo.extent().set(outputWidth, outputHeight, 1);
      LongBuffer pimg = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImage(device, presentInfo, null, pimg), "create RT present");
      outputImage = pimg.get(0);

      VkMemoryRequirements preq = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device, outputImage, preq);
      VkMemoryAllocateInfo palloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(preq.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      preq.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
      LongBuffer pmem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, palloc, null, pmem), "alloc RT present");
      outputMemory = pmem.get(0);
      vkBindImageMemory(device, outputImage, outputMemory, 0);

      // View unused for present (blit only), but keep for symmetry / future denoise.
      VkImageViewCreateInfo pviewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
              .image(outputImage)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM);
      pviewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      LongBuffer pview = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImageView(device, pviewInfo, null, pview), "RT present view");
      outputImageView = pview.get(0);
    }
  }

  private void destroyOutputImage() {
    VkDevice device = vulkan.getDevice();
    if (outputImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, outputImageView, null);
      outputImageView = VK_NULL_HANDLE;
    }
    if (outputImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, outputImage, null);
      outputImage = VK_NULL_HANDLE;
    }
    if (outputMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, outputMemory, null);
      outputMemory = VK_NULL_HANDLE;
    }
    if (traceImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, traceImageView, null);
      traceImageView = VK_NULL_HANDLE;
    }
    if (traceImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, traceImage, null);
      traceImage = VK_NULL_HANDLE;
    }
    if (traceMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, traceMemory, null);
      traceMemory = VK_NULL_HANDLE;
    }
  }

  private void updateDescriptors() {
    if (descriptorSet == VK_NULL_HANDLE) {
      return;
    }
    // Skip until we have something to bind for AS / half-res storage / texture / clouds.
    if (traceImageView == VK_NULL_HANDLE
        || textureImageView == VK_NULL_HANDLE
        || cloudImageView == VK_NULL_HANDLE) {
      return;
    }
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(6, stack);
      int writeCount = 0;

      if (!tlas.isEmpty()) {
        VkWriteDescriptorSetAccelerationStructureKHR asWrite =
            VkWriteDescriptorSetAccelerationStructureKHR.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET_ACCELERATION_STRUCTURE_KHR)
                .pAccelerationStructures(stack.longs(tlas.getHandle()));
        writes
            .get(writeCount)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .pNext(asWrite.address())
            .dstSet(descriptorSet)
            .dstBinding(0)
            .descriptorCount(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_ACCELERATION_STRUCTURE_KHR);
        writeCount++;
      }

      VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
      imageInfo.get(0).imageView(traceImageView).imageLayout(VK_IMAGE_LAYOUT_GENERAL);
      imageInfo.position(0);
      writes
          .get(writeCount)
          .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
          .dstSet(descriptorSet)
          .dstBinding(1)
          .descriptorCount(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
          .pImageInfo(imageInfo);
      writeCount++;

      VkDescriptorBufferInfo.Buffer uboInfo = VkDescriptorBufferInfo.calloc(1, stack);
      uboInfo.get(0).buffer(uboBuffer).offset(0).range(UBO_SIZE);
      uboInfo.position(0);
      writes
          .get(writeCount)
          .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
          .dstSet(descriptorSet)
          .dstBinding(2)
          .descriptorCount(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
          .pBufferInfo(uboInfo);
      writeCount++;

      VkDescriptorImageInfo.Buffer texInfo = VkDescriptorImageInfo.calloc(1, stack);
      texInfo
          .get(0)
          .imageView(textureImageView)
          .sampler(textureSampler)
          .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
      texInfo.position(0);
      writes
          .get(writeCount)
          .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
          .dstSet(descriptorSet)
          .dstBinding(3)
          .descriptorCount(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .pImageInfo(texInfo);
      writeCount++;

      if (tlas.getMeshInfoBuffer() != VK_NULL_HANDLE) {
        VkDescriptorBufferInfo.Buffer meshInfo = VkDescriptorBufferInfo.calloc(1, stack);
        meshInfo.get(0).buffer(tlas.getMeshInfoBuffer()).offset(0).range(VK_WHOLE_SIZE);
        meshInfo.position(0);
        writes
            .get(writeCount)
            .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
            .dstSet(descriptorSet)
            .dstBinding(4)
            .descriptorCount(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
            .pBufferInfo(meshInfo);
        writeCount++;
      }

      VkDescriptorImageInfo.Buffer cloudInfo = VkDescriptorImageInfo.calloc(1, stack);
      cloudInfo
          .get(0)
          .imageView(cloudImageView)
          .sampler(cloudSampler)
          .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
      cloudInfo.position(0);
      writes
          .get(writeCount)
          .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
          .dstSet(descriptorSet)
          .dstBinding(5)
          .descriptorCount(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .pImageInfo(cloudInfo);
      writeCount++;

      writes.limit(writeCount).position(0);
      vkUpdateDescriptorSets(device, writes, null);
    }
  }

  private void updateUbo(
      Player player,
      float fogStart,
      float fogEnd,
      float fogR,
      float fogG,
      float fogB,
      boolean underwater,
      float tileSpanU,
      float tileSpanV,
      float cloudTimeSeconds) {
    float aspect = outputWidth / (float) Math.max(1, outputHeight);
    Matrix4f proj =
        new Matrix4f()
            .perspective((float) Math.toRadians(70.0), aspect, 0.05f, 20000f)
            .scale(1f, -1f, 1f);
    double[] eyeArr = player.getEyePosition();
    Vector3f eye = new Vector3f((float) eyeArr[0], (float) eyeArr[1], (float) eyeArr[2]);
    double yawRad = Math.toRadians(player.getYaw());
    double pitchRad = Math.toRadians(player.getPitch());
    Vector3f center =
        new Vector3f(
            eye.x - (float) (Math.sin(yawRad) * Math.cos(pitchRad)),
            eye.y - (float) Math.sin(pitchRad),
            eye.z + (float) (Math.cos(yawRad) * Math.cos(pitchRad)));
    Matrix4f view = new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
    Matrix4f viewInv = new Matrix4f(view).invert();
    Matrix4f projInv = new Matrix4f(proj).invert();

    FloatBuffer fb = uboMapped.asFloatBuffer();
    viewInv.get(0, fb);
    projInv.get(16, fb);
    fb.put(32, eye.x);
    fb.put(33, eye.y);
    fb.put(34, eye.z);
    fb.put(35, underwater ? 1f : 0f);
    fb.put(36, fogStart);
    fb.put(37, fogEnd);
    fb.put(38, tileSpanU);
    fb.put(39, tileSpanV);
    fb.put(40, fogR);
    fb.put(41, fogG);
    fb.put(42, fogB);
    fb.put(43, cloudTimeSeconds);
    float sunLen = (float) Math.sqrt(SUN_X * SUN_X + SUN_Y * SUN_Y + SUN_Z * SUN_Z);
    fb.put(44, SUN_X / sunLen);
    fb.put(45, SUN_Y / sunLen);
    fb.put(46, SUN_Z / sunLen);
    fb.put(47, SUN_COS_ANGULAR_RADIUS);
  }

  private static int alignUp(int value, int alignment) {
    return (value + alignment - 1) & ~(alignment - 1);
  }
}
