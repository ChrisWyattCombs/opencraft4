package opencraft.graphics.render;

import static org.lwjgl.stb.STBImageWrite.stbi_write_png;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkAcquireNextImageKHR;
import static org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_COMPARE_OP_LESS;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_B8G8R8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_D32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_CONTENTS_INLINE;
import static org.lwjgl.vulkan.VK10.VK_SUBPASS_EXTERNAL;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;
import static org.lwjgl.vulkan.VK10.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBeginRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdDraw;
import static org.lwjgl.vulkan.VK10.vkCmdEndRenderPass;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.vkCmdSetViewport;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateFence;
import static org.lwjgl.vulkan.VK10.vkCreateFramebuffer;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateRenderPass;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyFence;
import static org.lwjgl.vulkan.VK10.vkDestroyFramebuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyRenderPass;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeCommandBuffers;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkResetCommandBuffer;
import static org.lwjgl.vulkan.VK10.vkResetCommandPool;
import static org.lwjgl.vulkan.VK10.vkResetFences;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import opencraft.DiagLog;
import opencraft.graphics.Display;
import opencraft.graphics.GameWindow;
import opencraft.graphics.VulkanContext;
import opencraft.graphics.VulkanSwapchain;
import opencraft.graphics.render.rt.RtWorldPass;
import opencraft.player.Player;
import opencraft.world.World;
import opencraft.world.chunk.Chunk;
import opencraft.world.chunk.ChunkPos;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

/** Renders loaded chunk meshes offscreen, then presents via the shared BGRA presenter. */
public final class WorldRenderer implements AutoCloseable {

  private static final int MAX_FRAMES_IN_FLIGHT = 2;
  private static final int UNIFORM_BUFFER_SIZE = 256;
  private static final int SKY_UNIFORM_BUFFER_SIZE = 160; // 2×mat4 + 2×vec4
  private static final int VERTEX_STRIDE = 32;
  private static final int COLOR_FORMAT = VK_FORMAT_B8G8R8A8_UNORM;

  /** Chunks per side of a merged GPU region mesh (4×4 = 16 chunks / draw). */
  private static final int MESH_REGION = 4;

  /** Chunks per side of one distant LOD heightmap patch. */
  private static final int LOD_SECTION_CHUNKS = 8;

  /** Shared sun direction (toward sun) for raster sky + RT. */
  private static final float SUN_X = 0.55f;

  private static final float SUN_Y = 0.82f;
  private static final float SUN_Z = 0.18f;
  private static final float SUN_ANGULAR_RADIUS = 0.045f;

  private final GameWindow window;
  private final VulkanContext vulkan;
  private final VulkanSwapchain swapchain;
  private final Display display;

  private World world;
  private TextureAtlas atlas;
  private final Map<ChunkPos, ChunkMesher.MeshData> chunkMeshes = new HashMap<>();
  private final Map<Long, GpuRegionMesh> regionMeshes = new HashMap<>();
  private final Set<Long> dirtyRegions = new HashSet<>();
  private final java.util.ArrayList<GpuRegionMesh> pendingMeshFree = new java.util.ArrayList<>();
  private ChunkMeshScheduler meshScheduler;
  private LodMeshScheduler lodMeshScheduler;
  private final Map<LodMeshScheduler.LodKey, GpuRegionMesh> lodMeshes = new HashMap<>();
  private final Set<LodMeshScheduler.LodKey> lodDesired = new HashSet<>();

  private ByteBuffer hudPixels;
  private int hudWidth;
  private int hudHeight;
  private int hudRowBytes;
  private long hudStagingBuffer;
  private long hudStagingMemory;
  private ByteBuffer hudStagingMapped;
  private long hudStagingCapacity;

  private long renderPass;
  private long pipelineLayout;
  private long graphicsPipeline;
  private long translucentPipeline;
  private long skyPipelineLayout;
  private long skyPipeline;
  private long skyDescriptorSetLayout;
  private long descriptorSetLayout;
  private long descriptorPool;
  private long[] descriptorSets = new long[0];
  private long[] skyDescriptorSets = new long[0];

  private long colorImage;
  private long colorImageMemory;
  private long colorImageView;
  private long depthImage;
  private long depthImageMemory;
  private long depthImageView;
  private long framebuffer;
  private int targetWidth;
  private int targetHeight;

  private long readbackBuffer;
  private long readbackMemory;
  private ByteBuffer readbackMapped;

  /** Last frame's present source (offscreen color or RT output) for menu screenshots. */
  private long lastPresentSourceImage;

  private long textureImage;
  private long textureImageMemory;
  private long textureImageView;
  private long textureSampler;

  private long sunImage;
  private long sunImageMemory;
  private long sunImageView;
  private long sunSampler;

  private long cloudImage;
  private long cloudImageMemory;
  private long cloudImageView;
  private long cloudSampler;

  /** Wall-clock origin for scrolling cloud UVs. */
  private final long cloudTimeOriginNanos = System.nanoTime();

  private long[] uniformBuffers = new long[0];
  private long[] uniformBuffersMemory = new long[0];
  private ByteBuffer[] uniformBuffersMapped = new ByteBuffer[0];
  private long[] skyUniformBuffers = new long[0];
  private long[] skyUniformBuffersMemory = new long[0];
  private ByteBuffer[] skyUniformBuffersMapped = new ByteBuffer[0];

  private long commandPool;
  private VkCommandBuffer[] commandBuffers = new VkCommandBuffer[0];
  private long[] imageAvailableSemaphores = new long[0];
  private long[] renderFinishedSemaphores = new long[0];
  private long[] inFlightFences = new long[0];
  private int currentFrame;
  private boolean loggedFirstPresent;
  private final ChunkFrustum chunkFrustum = new ChunkFrustum();
  private final Matrix4f projViewScratch = new Matrix4f();
  private int activeRenderDistance = 8;
  private int activeLodDistance = 256;
  private RtWorldPass rtPass;
  private boolean rayTracingEnabled;

  /** Frame counter used by {@link DiagLog} breadcrumbs while RTX primary mode is active. */
  private int diagRtFrames;

  /**
   * Creates a renderer that draws offscreen and presents through {@link Display#presentBGRA}.
   *
   * @param window GLFW window
   * @param vulkan Vulkan device context
   * @param swapchain shared swapchain (size reference only)
   * @param display display used for BGRA present
   */
  public WorldRenderer(
      GameWindow window, VulkanContext vulkan, VulkanSwapchain swapchain, Display display) {
    this.window = window;
    this.vulkan = vulkan;
    this.swapchain = swapchain;
    this.display = display;
  }

  /**
   * Creates a renderer bound to the shared window swapchain.
   *
   * @param window GLFW window
   * @param vulkan Vulkan device context
   * @param swapchain shared swapchain
   * @deprecated use {@link #WorldRenderer(GameWindow, VulkanContext, VulkanSwapchain, Display)}
   */
  @Deprecated
  public WorldRenderer(GameWindow window, VulkanContext vulkan, VulkanSwapchain swapchain) {
    this(window, vulkan, swapchain, null);
  }

  /** Compiles shaders, creates pipelines, depth buffer, framebuffers, and sync objects. */
  public void init() {
    init(TextureAtlas.createFromBlocks());
  }

  /**
   * Initializes GPU resources using a prebuilt texture atlas.
   *
   * @param atlas block texture atlas
   */
  public void init(TextureAtlas atlas) {
    this.atlas = atlas;
    if (meshScheduler != null) {
      meshScheduler.close();
    }
    if (lodMeshScheduler != null) {
      lodMeshScheduler.close();
    }
    int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    meshScheduler = new ChunkMeshScheduler(workers);
    lodMeshScheduler = new LodMeshScheduler(Math.max(1, workers / 2));
    createCommandPool();
    createRenderPass();
    createDescriptorSetLayout();
    createSkyDescriptorSetLayout();
    createGraphicsPipeline();
    createSkyPipeline();
    createOffscreenTargets();
    createTextureResources();
    createSunTextureResources();
    createCloudTextureResources();
    createUniformBuffers();
    createSkyUniformBuffers();
    createDescriptorPool();
    createDescriptorSets();
    createSkyDescriptorSets();
    createCommandBuffers();
    createSyncObjects();
    if (vulkan.isRayTracingSupported()) {
      try {
        rtPass = new RtWorldPass(vulkan, commandPool);
        rtPass.setTexture(textureImageView, textureSampler);
        rtPass.setCloudTexture(cloudImageView, cloudSampler);
        System.out.println("[Opencraft] RTX primary path ready (toggle with F8)");
      } catch (RuntimeException e) {
        System.err.println("[Opencraft] RT init failed, raster only: " + e.getMessage());
        e.printStackTrace();
        if (rtPass != null) {
          rtPass.close();
          rtPass = null;
        }
      }
    }
  }

  /**
   * @return whether the GPU/device enabled Vulkan ray tracing
   */
  public boolean isRayTracingSupported() {
    return rtPass != null;
  }

  /**
   * @return whether F8 RTX primary mode is currently on
   */
  public boolean isRayTracingEnabled() {
    return rayTracingEnabled && rtPass != null;
  }

  /**
   * Enables or disables hardware ray-traced primary visibility.
   *
   * @param enabled desired state
   * @return actual state after the call
   */
  public boolean setRayTracingEnabled(boolean enabled) {
    if (rtPass == null) {
      rayTracingEnabled = false;
      DiagLog.log("RTX toggle ignored (no rtPass)");
      return false;
    }
    rayTracingEnabled = enabled;
    if (rayTracingEnabled) {
      DiagLog.log("RTX ON begin waitIdle+clear AS");
      vulkan.waitIdle();
      for (GpuRegionMesh mesh : regionMeshes.values()) {
        mesh.clearAccelerationStructures(vulkan);
      }
      for (GpuRegionMesh mesh : lodMeshes.values()) {
        mesh.clearAccelerationStructures(vulkan);
      }
      rtPass.markSceneDirty();
      DiagLog.log("RTX ON ready regions=" + regionMeshes.size() + " lod=" + lodMeshes.size());
      System.out.println("[Opencraft] RTX mode ON");
    } else {
      DiagLog.log("RTX OFF");
      System.out.println("[Opencraft] RTX mode OFF (raster)");
    }
    return rayTracingEnabled;
  }

  /**
   * Sets the active world to render.
   *
   * @param world voxel world
   */
  public void setWorld(World world) {
    this.world = world;
    clearMeshes();
    if (meshScheduler != null) {
      meshScheduler.close();
    }
    if (lodMeshScheduler != null) {
      lodMeshScheduler.close();
    }
    int workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    meshScheduler = new ChunkMeshScheduler(workers);
    lodMeshScheduler = new LodMeshScheduler(Math.max(1, workers / 2));
  }

  /**
   * Returns how many near chunk meshes are currently resident.
   *
   * @return mesh count
   */
  public int getMeshCount() {
    return chunkMeshes.size();
  }

  /**
   * Returns how many distant LOD patches are resident on the GPU.
   *
   * @return LOD mesh count
   */
  public int getLodMeshCount() {
    return lodMeshes.size();
  }

  /**
   * Sets the Ultralight HUD bitmap to stamp into the top-left corner this frame.
   *
   * @param pixels BGRA pixels (may be null to skip)
   * @param width HUD width
   * @param height HUD height
   * @param rowBytes row stride in bytes
   */
  public void setHudOverlay(ByteBuffer pixels, int width, int height, int rowBytes) {
    this.hudPixels = pixels;
    this.hudWidth = width;
    this.hudHeight = height;
    this.hudRowBytes = rowBytes;
  }

  /**
   * Loads nearby full-detail chunks and syncs distant LOD heightmap patches.
   *
   * @param player viewer
   * @param renderDistance near chunk Chebyshev radius (full voxels)
   */
  public void syncChunks(Player player, int renderDistance) {
    syncChunks(player, renderDistance, Math.max(renderDistance * 6, 64));
  }

  /**
   * Syncs near voxel meshes and far low-detail LOD terrain.
   *
   * @param player viewer
   * @param nearDistance full-detail chunk radius
   * @param lodDistance distant LOD horizon in chunk units (visual only; no voxel load)
   */
  public void syncChunks(Player player, int nearDistance, int lodDistance) {
    if (world == null || atlas == null || meshScheduler == null) {
      return;
    }
    this.activeRenderDistance = Math.max(1, nearDistance);
    this.activeLodDistance = Math.max(this.activeRenderDistance, lodDistance);
    flushPendingMeshFrees();
    int pcx = Math.floorDiv((int) Math.floor(player.getX()), Chunk.SIZE_X);
    int pcz = Math.floorDiv((int) Math.floor(player.getZ()), Chunk.SIZE_Z);
    float aspect = targetWidth / (float) Math.max(1, targetHeight);
    if (targetWidth <= 0) {
      aspect = window.getWidth() / (float) Math.max(1, window.getHeight());
    }
    float farPlane = Math.max(512f, activeLodDistance * Chunk.SIZE_X * 1.6f);
    ChunkFrustum.buildProjView(player, aspect, farPlane, projViewScratch);
    chunkFrustum.update(projViewScratch);

    for (ChunkMeshScheduler.Completed done : meshScheduler.drain(48)) {
      chunkMeshes.put(done.pos(), done.data());
      dirtyRegions.add(regionKey(done.pos()));
    }

    // Claim whole LOD sections that touch the near radius — load every chunk in those sections
    // (no frustum skip), so we never leave half-drawn rings.
    int sectionSpan = LOD_SECTION_CHUNKS;
    int psx = Math.floorDiv(pcx, sectionSpan);
    int psz = Math.floorDiv(pcz, sectionSpan);
    int nearSectionRadius = Math.max(1, (activeRenderDistance + sectionSpan - 1) / sectionSpan) + 1;
    // Keep claimed sections until the whole section is outside near + one section of hysteresis.
    int keepSectionRadius = nearSectionRadius + 1;

    int submits = 0;
    final int maxSubmitsPerFrame = 48;
    java.util.HashSet<Long> desiredNearSections = new java.util.HashSet<>();
    outer:
    for (int sx = psx - nearSectionRadius; sx <= psx + nearSectionRadius; sx++) {
      for (int sz = psz - nearSectionRadius; sz <= psz + nearSectionRadius; sz++) {
        int minCx = sx * sectionSpan;
        int minCz = sz * sectionSpan;
        int maxCx = minCx + sectionSpan - 1;
        int maxCz = minCz + sectionSpan - 1;
        int nearCorner = sectionNearCorner(pcx, pcz, minCx, minCz, maxCx, maxCz);
        if (nearCorner > activeRenderDistance) {
          continue;
        }
        desiredNearSections.add(sectionKey(sx, sz));
        for (int cz = minCz; cz <= maxCz; cz++) {
          for (int cx = minCx; cx <= maxCx; cx++) {
            ChunkPos pos = new ChunkPos(cx, cz);
            ChunkMesher.MeshData existing = chunkMeshes.get(pos);
            Chunk loaded = world.getLoadedChunk(pos);
            boolean needsBuild = existing == null || (loaded != null && loaded.isDirty());
            if (!needsBuild) {
              continue;
            }
            if (submits >= maxSubmitsPerFrame) {
              break outer;
            }
            if (meshScheduler.submit(world, pos, atlas)) {
              submits++;
            }
          }
        }
      }
    }

    // Also retain sections still within keep radius (hysteresis) so we don't peel them
    // chunk-by-chunk.
    for (int sx = psx - keepSectionRadius; sx <= psx + keepSectionRadius; sx++) {
      for (int sz = psz - keepSectionRadius; sz <= psz + keepSectionRadius; sz++) {
        int minCx = sx * sectionSpan;
        int minCz = sz * sectionSpan;
        int maxCx = minCx + sectionSpan - 1;
        int maxCz = minCz + sectionSpan - 1;
        int nearCorner = sectionNearCorner(pcx, pcz, minCx, minCz, maxCx, maxCz);
        if (nearCorner <= activeRenderDistance + sectionSpan) {
          desiredNearSections.add(sectionKey(sx, sz));
        }
      }
    }

    // Unload whole sections only — never drop individual chunks from a still-desired section.
    Iterator<Map.Entry<ChunkPos, ChunkMesher.MeshData>> it = chunkMeshes.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<ChunkPos, ChunkMesher.MeshData> entry = it.next();
      ChunkPos pos = entry.getKey();
      int sx = Math.floorDiv(pos.x(), sectionSpan);
      int sz = Math.floorDiv(pos.z(), sectionSpan);
      if (!desiredNearSections.contains(sectionKey(sx, sz))) {
        dirtyRegions.add(regionKey(pos));
        it.remove();
      }
    }

    int rebuilds = 0;
    final int maxRebuilds = 8;
    Iterator<Long> dirtyIt = dirtyRegions.iterator();
    while (dirtyIt.hasNext() && rebuilds < maxRebuilds) {
      long key = dirtyIt.next();
      dirtyIt.remove();
      rebuildRegion(key);
      rebuilds++;
    }

    syncLodPatches(pcx, pcz);
  }

  private static long sectionKey(int sx, int sz) {
    return (((long) sx) << 32) ^ (sz & 0xffffffffL);
  }

  private static int sectionNearCorner(
      int pcx, int pcz, int minCx, int minCz, int maxCx, int maxCz) {
    return Math.min(
        Math.min(
            Math.max(Math.abs(minCx - pcx), Math.abs(minCz - pcz)),
            Math.max(Math.abs(maxCx - pcx), Math.abs(minCz - pcz))),
        Math.min(
            Math.max(Math.abs(minCx - pcx), Math.abs(maxCz - pcz)),
            Math.max(Math.abs(maxCx - pcx), Math.abs(maxCz - pcz))));
  }

  private boolean sectionHasNearMesh(int minCx, int minCz, int sectionSpan) {
    for (int cz = minCz; cz < minCz + sectionSpan; cz++) {
      for (int cx = minCx; cx < minCx + sectionSpan; cx++) {
        if (chunkMeshes.containsKey(new ChunkPos(cx, cz))) {
          return true;
        }
      }
    }
    return false;
  }

  private void syncLodPatches(int pcx, int pcz) {
    if (lodMeshScheduler == null) {
      return;
    }
    for (LodMeshScheduler.Completed done : lodMeshScheduler.drain(8)) {
      // Drop if a near chunk already claimed this section while we were building.
      int minCx = done.key().sectionX() * LOD_SECTION_CHUNKS;
      int minCz = done.key().sectionZ() * LOD_SECTION_CHUNKS;
      if (sectionHasNearMesh(minCx, minCz, LOD_SECTION_CHUNKS)) {
        continue;
      }
      GpuRegionMesh old = lodMeshes.remove(done.key());
      if (old != null) {
        pendingMeshFree.add(old);
      }
      if (done.data() != null && !done.data().isEmpty()) {
        lodMeshes.put(
            done.key(),
            GpuRegionMesh.upload(vulkan, java.util.List.of(done.data())).setDistantLod(true));
        if (rtPass != null) {
          rtPass.markSceneDirty();
        }
      }
    }

    lodDesired.clear();
    int sectionSpan = LOD_SECTION_CHUNKS;
    int sizeBlocks = sectionSpan * Chunk.SIZE_X;
    int psx = Math.floorDiv(pcx, sectionSpan);
    int psz = Math.floorDiv(pcz, sectionSpan);
    int sectionRadius = Math.max(1, (activeLodDistance + sectionSpan - 1) / sectionSpan);

    int lodSubmits = 0;
    final int maxLodSubmits = 12;
    for (int sx = psx - sectionRadius; sx <= psx + sectionRadius; sx++) {
      for (int sz = psz - sectionRadius; sz <= psz + sectionRadius; sz++) {
        int minCx = sx * sectionSpan;
        int minCz = sz * sectionSpan;
        int maxCx = minCx + sectionSpan - 1;
        int maxCz = minCz + sectionSpan - 1;
        int nearCorner = sectionNearCorner(pcx, pcz, minCx, minCz, maxCx, maxCz);
        if (nearCorner > activeLodDistance) {
          continue;
        }
        // Near owns this section once any full-detail chunk is present — unload LOD.
        if (sectionHasNearMesh(minCx, minCz, sectionSpan)) {
          continue;
        }
        // Also skip if the section already intersects the near radius (about to load wholes).
        if (nearCorner <= activeRenderDistance) {
          continue;
        }

        // Sample spacing in blocks (must divide section size). Smaller = sharper distant hills.
        int step = nearCorner < activeRenderDistance + 24 ? 2 : 4;
        LodMeshScheduler.LodKey key = new LodMeshScheduler.LodKey(sx, sz, step);
        lodDesired.add(key);
        if (lodMeshes.containsKey(key) || lodSubmits >= maxLodSubmits) {
          continue;
        }
        boolean onScreen = lodSectionInFrustum(minCx, minCz, sectionSpan);
        if (!onScreen && lodSubmits >= Math.max(2, maxLodSubmits / 2)) {
          continue;
        }
        int originX = minCx * Chunk.SIZE_X;
        int originZ = minCz * Chunk.SIZE_Z;
        if (lodMeshScheduler.submit(
            key, world.getTerrainGenerator(), atlas, originX, originZ, sizeBlocks)) {
          lodSubmits++;
        }
      }
    }

    Iterator<Map.Entry<LodMeshScheduler.LodKey, GpuRegionMesh>> lodIt =
        lodMeshes.entrySet().iterator();
    while (lodIt.hasNext()) {
      Map.Entry<LodMeshScheduler.LodKey, GpuRegionMesh> entry = lodIt.next();
      LodMeshScheduler.LodKey key = entry.getKey();
      int minCx = key.sectionX() * sectionSpan;
      int minCz = key.sectionZ() * sectionSpan;
      if (!lodDesired.contains(key) || sectionHasNearMesh(minCx, minCz, sectionSpan)) {
        pendingMeshFree.add(entry.getValue());
        lodIt.remove();
      }
    }
  }

  private boolean lodSectionInFrustum(int minCx, int minCz, int sectionSpan) {
    float minX = minCx * Chunk.SIZE_X;
    float minZ = minCz * Chunk.SIZE_Z;
    float maxX = (minCx + sectionSpan) * Chunk.SIZE_X;
    float maxZ = (minCz + sectionSpan) * Chunk.SIZE_Z;
    return chunkFrustum.testAab(minX, 0f, minZ, maxX, (float) Chunk.SIZE_Y, maxZ);
  }

  private boolean lodKeyInFrustum(LodMeshScheduler.LodKey key) {
    int minCx = key.sectionX() * LOD_SECTION_CHUNKS;
    int minCz = key.sectionZ() * LOD_SECTION_CHUNKS;
    return lodSectionInFrustum(minCx, minCz, LOD_SECTION_CHUNKS);
  }

  private void rebuildRegion(long key) {
    int rx = (int) (key >> 32);
    int rz = (int) key;
    int x0 = rx * MESH_REGION;
    int z0 = rz * MESH_REGION;
    java.util.ArrayList<ChunkMesher.MeshData> parts =
        new java.util.ArrayList<>(MESH_REGION * MESH_REGION);
    boolean any = false;
    for (int cz = z0; cz < z0 + MESH_REGION; cz++) {
      for (int cx = x0; cx < x0 + MESH_REGION; cx++) {
        ChunkMesher.MeshData data = chunkMeshes.get(new ChunkPos(cx, cz));
        if (data != null && !data.isEmpty()) {
          parts.add(data);
          any = true;
        }
      }
    }
    GpuRegionMesh old = regionMeshes.remove(key);
    if (old != null) {
      pendingMeshFree.add(old);
    }
    if (!any) {
      return;
    }
    regionMeshes.put(key, GpuRegionMesh.upload(vulkan, parts));
    if (rtPass != null) {
      rtPass.markSceneDirty();
    }
  }

  private static long regionKey(ChunkPos pos) {
    int rx = Math.floorDiv(pos.x(), MESH_REGION);
    int rz = Math.floorDiv(pos.z(), MESH_REGION);
    return (((long) rx) << 32) ^ (rz & 0xffffffffL);
  }

  /** Tests whether any chunk column in this mesh region may be visible. */
  private boolean regionInFrustum(long key) {
    int rx = (int) (key >> 32);
    int rz = (int) key;
    int x0 = rx * MESH_REGION;
    int z0 = rz * MESH_REGION;
    for (int cz = z0; cz < z0 + MESH_REGION; cz++) {
      for (int cx = x0; cx < x0 + MESH_REGION; cx++) {
        if (chunkFrustum.testChunk(new ChunkPos(cx, cz))) {
          return true;
        }
      }
    }
    return false;
  }

  private void flushPendingMeshFrees() {
    if (pendingMeshFree.isEmpty()) {
      return;
    }
    // Freeing BLASes while TLAS still references them causes DEVICE_LOST — rebuild first.
    if (rtPass != null && isRayTracingEnabled()) {
      rtPass.markSceneDirty();
      java.util.ArrayList<GpuRegionMesh> live = new java.util.ArrayList<>();
      live.addAll(regionMeshes.values());
      live.addAll(lodMeshes.values());
      rtPass.rebuildSceneOnly(live);
    }
    vulkan.waitIdle();
    for (GpuRegionMesh mesh : pendingMeshFree) {
      mesh.free(vulkan);
    }
    pendingMeshFree.clear();
  }

  /**
   * Renders the world offscreen, blits to the swapchain on the GPU, and presents.
   *
   * @param player camera and movement source
   */
  public void render(Player player) {
    int width = Math.max(1, window.getWidth());
    int height = Math.max(1, window.getHeight());
    if (width != targetWidth || height != targetHeight || window.wasFramebufferResized()) {
      recreateSwapchainResources();
      window.clearFramebufferResized();
    }

    VkDevice device = vulkan.getDevice();
    boolean rt = isRayTracingEnabled();
    int frameId = diagRtFrames;
    boolean verboseRt = rt && (frameId < 45 || frameId % 20 == 0);
    try (MemoryStack stack = stackPush()) {
      if (verboseRt) {
        DiagLog.log("rt#" + frameId + " waitFence");
      }
      int wait = vkWaitForFences(device, inFlightFences[currentFrame], true, -1L);
      if (wait != VK_SUCCESS) {
        DiagLog.logVk("waitFence frame=" + currentFrame + " rt=" + rt, wait);
        VulkanContext.checkVk(wait, "waitFence");
      }

      IntBuffer imageIndex = stack.ints(0);
      int acquire =
          vkAcquireNextImageKHR(
              device,
              swapchain.getSwapchain(),
              -1L,
              imageAvailableSemaphores[currentFrame],
              VK_NULL_HANDLE,
              imageIndex);
      if (acquire == VK_ERROR_OUT_OF_DATE_KHR) {
        recreateSwapchainResources();
        window.clearFramebufferResized();
        return;
      }
      if (acquire != VK_SUCCESS && acquire != VK_SUBOPTIMAL_KHR) {
        DiagLog.logVk("acquireImage", acquire);
        throw new IllegalStateException("Failed to acquire swapchain image: " + acquire);
      }
      int swapIndex = imageIndex.get(0);
      long swapImage = swapchain.getImages()[swapIndex];

      vkResetFences(device, inFlightFences[currentFrame]);
      updateUniformBuffer(player, currentFrame);
      updateSkyUniformBuffer(player, currentFrame);

      boolean underwater = player.isEyeInWater();
      float fogR = underwater ? 0.04f : 0.53f;
      float fogG = underwater ? 0.18f : 0.81f;
      float fogB = underwater ? 0.32f : 0.92f;
      float fogEnd = activeLodDistance * Chunk.SIZE_X * 0.92f;
      float fogStart = Math.max(activeRenderDistance * Chunk.SIZE_X * 0.45f, fogEnd * 0.35f);
      float[] span = atlas != null ? atlas.tileUvSpan() : new float[] {1f, 1f};

      // Build/rebuild AS before recording — never submit AS cmds while a primary CB is open.
      boolean useRt = false;
      if (isRayTracingEnabled()) {
        if (verboseRt) {
          DiagLog.log("rt#" + frameId + " prepareFrame");
        }
        rtPass.ensureOutputSize(targetWidth, targetHeight);
        java.util.ArrayList<GpuRegionMesh> allMeshes = new java.util.ArrayList<>();
        allMeshes.addAll(regionMeshes.values());
        allMeshes.addAll(lodMeshes.values());
        float cloudTime = (System.nanoTime() - cloudTimeOriginNanos) * 1e-9f;
        rtPass.prepareFrame(
            allMeshes,
            player,
            fogStart,
            fogEnd,
            fogR,
            fogG,
            fogB,
            underwater,
            span[0],
            span[1],
            cloudTime);
        useRt = rtPass.hasGeometry();
        if (verboseRt) {
          DiagLog.log("rt#" + frameId + " prepare done hasGeom=" + useRt);
        }
      }

      VkCommandBuffer cmd = commandBuffers[currentFrame];
      vkResetCommandBuffer(cmd, 0);
      VkCommandBufferBeginInfo beginInfo =
          VkCommandBufferBeginInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO)
              .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
      VulkanContext.checkVk(vkBeginCommandBuffer(cmd, beginInfo), "begin world cmd");

      int drawn = 0;
      long presentSourceImage = colorImage;

      if (useRt) {
        if (verboseRt) {
          DiagLog.log("rt#" + frameId + " recordTrace");
        }
        rtPass.recordTrace(cmd);
        if (verboseRt) {
          DiagLog.log("rt#" + frameId + " recordTrace done");
        }
        presentSourceImage = rtPass.getOutputImage();
        drawn = regionMeshes.size() + lodMeshes.size();
      }
      lastPresentSourceImage = presentSourceImage;
      if (presentSourceImage == colorImage) {
        VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
        viewport.get(0).x(0).y(0).width(targetWidth).height(targetHeight).minDepth(0f).maxDepth(1f);
        viewport.position(0);
        vkCmdSetViewport(cmd, 0, viewport);

        VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
        scissor.get(0).offset().set(0, 0);
        scissor.get(0).extent().set(targetWidth, targetHeight);
        scissor.position(0);
        vkCmdSetScissor(cmd, 0, scissor);

        VkClearValue.Buffer clearValues = VkClearValue.calloc(2, stack);
        clearValues
            .get(0)
            .color()
            .float32(0, fogR)
            .float32(1, fogG)
            .float32(2, fogB)
            .float32(3, 1f);
        clearValues.get(1).depthStencil().depth(1f).stencil(0);
        clearValues.position(0);

        VkRenderPassBeginInfo renderPassInfo =
            VkRenderPassBeginInfo.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO)
                .renderPass(renderPass)
                .framebuffer(framebuffer)
                .clearValueCount(2)
                .pClearValues(clearValues);
        renderPassInfo.renderArea().offset().set(0, 0);
        renderPassInfo.renderArea().extent().set(targetWidth, targetHeight);

        vkCmdBeginRenderPass(cmd, renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        // Procedural sky + voxel sun disc (fullscreen triangle, no depth write).
        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, skyPipeline);
        vkCmdBindDescriptorSets(
            cmd,
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            skyPipelineLayout,
            0,
            stack.longs(skyDescriptorSets[currentFrame]),
            null);
        vkCmdDraw(cmd, 3, 1, 0, 0);

        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
        vkCmdBindDescriptorSets(
            cmd,
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipelineLayout,
            0,
            stack.longs(descriptorSets[currentFrame]),
            null);

        float aspect = targetWidth / (float) Math.max(1, targetHeight);
        float farPlane = Math.max(512f, activeLodDistance * Chunk.SIZE_X * 1.6f);
        ChunkFrustum.buildProjView(player, aspect, farPlane, projViewScratch);
        chunkFrustum.update(projViewScratch);

        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, graphicsPipeline);
        for (Map.Entry<LodMeshScheduler.LodKey, GpuRegionMesh> entry : lodMeshes.entrySet()) {
          GpuRegionMesh mesh = entry.getValue();
          if (mesh.isEmpty() || !lodKeyInFrustum(entry.getKey())) {
            continue;
          }
          mesh.drawOpaque(cmd);
          drawn++;
        }

        for (Map.Entry<Long, GpuRegionMesh> entry : regionMeshes.entrySet()) {
          GpuRegionMesh mesh = entry.getValue();
          if (mesh.isEmpty() || !regionInFrustum(entry.getKey())) {
            continue;
          }
          mesh.drawOpaque(cmd);
          drawn++;
        }

        vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, translucentPipeline);
        for (Map.Entry<LodMeshScheduler.LodKey, GpuRegionMesh> entry : lodMeshes.entrySet()) {
          GpuRegionMesh mesh = entry.getValue();
          if (!mesh.hasTranslucent() || !lodKeyInFrustum(entry.getKey())) {
            continue;
          }
          mesh.drawTranslucent(cmd);
        }
        for (Map.Entry<Long, GpuRegionMesh> entry : regionMeshes.entrySet()) {
          GpuRegionMesh mesh = entry.getValue();
          if (!mesh.hasTranslucent() || !regionInFrustum(entry.getKey())) {
            continue;
          }
          mesh.drawTranslucent(cmd);
        }
        vkCmdEndRenderPass(cmd);
      }

      // Stamp Ultralight FPS HUD into the top-left of the offscreen color target.
      if (hudPixels != null && hudWidth > 0 && hudHeight > 0) {
        ensureHudStaging(hudRowBytes * hudHeight);
        hudStagingMapped.clear();
        int oldLim = hudPixels.limit();
        int oldPos = hudPixels.position();
        hudPixels.limit(oldPos + hudRowBytes * hudHeight);
        hudStagingMapped.put(hudPixels);
        hudPixels.position(oldPos).limit(oldLim);
        hudStagingMapped.flip();

        int hudSrcStage =
            useRt ? VK_PIPELINE_STAGE_TRANSFER_BIT : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        int hudSrcAccess =
            useRt ? VK_ACCESS_TRANSFER_WRITE_BIT : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;

        VkImageMemoryBarrier.Buffer toDst = VkImageMemoryBarrier.calloc(1, stack);
        toDst
            .get(0)
            .srcAccessMask(hudSrcAccess)
            .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .image(presentSourceImage)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        toDst
            .get(0)
            .subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        toDst.position(0);
        vkCmdPipelineBarrier(
            cmd, hudSrcStage, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toDst);

        org.lwjgl.vulkan.VkBufferImageCopy.Buffer region =
            org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
        int copyW = Math.min(hudWidth, targetWidth - 12);
        int copyH = Math.min(hudHeight, targetHeight - 12);
        region
            .get(0)
            .bufferOffset(0)
            .bufferRowLength(hudRowBytes / 4)
            .bufferImageHeight(hudHeight)
            .imageSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1);
        region.get(0).imageOffset().set(12, 12, 0);
        region.get(0).imageExtent().set(Math.max(1, copyW), Math.max(1, copyH), 1);
        region.position(0);
        org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage(
            cmd,
            hudStagingBuffer,
            presentSourceImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            region);

        VkImageMemoryBarrier.Buffer toSrc = VkImageMemoryBarrier.calloc(1, stack);
        toSrc
            .get(0)
            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
            .image(presentSourceImage)
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

      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(2, stack);
      int blitSrcStage =
          useRt ? VK_PIPELINE_STAGE_TRANSFER_BIT : VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
      int blitSrcAccess =
          useRt ? VK_ACCESS_TRANSFER_WRITE_BIT : VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
      // After HUD stamp (if any) the image is TRANSFER_SRC; RT upscale also leaves TRANSFER_SRC.
      barrier
          .get(0)
          .srcAccessMask(blitSrcAccess)
          .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
          .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
          .image(presentSourceImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      barrier
          .get(0)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      barrier
          .get(1)
          .srcAccessMask(0)
          .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
          .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
          .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
          .image(swapImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      barrier
          .get(1)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      barrier.position(0);
      vkCmdPipelineBarrier(
          cmd, blitSrcStage, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barrier);

      int srcW = targetWidth;
      int srcH = targetHeight;
      int dstW = swapchain.getExtent().width();
      int dstH = swapchain.getExtent().height();
      org.lwjgl.vulkan.VkImageBlit.Buffer blit = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
      blit.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
      blit.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
      blit.get(0).srcOffsets(0).set(0, 0, 0);
      blit.get(0).srcOffsets(1).set(srcW, srcH, 1);
      blit.get(0).dstOffsets(0).set(0, 0, 0);
      blit.get(0).dstOffsets(1).set(dstW, dstH, 1);
      blit.position(0);
      vkCmdBlitImage(
          cmd,
          presentSourceImage,
          VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
          swapImage,
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          blit,
          VK_FILTER_LINEAR);

      VkImageMemoryBarrier.Buffer toPresent = VkImageMemoryBarrier.calloc(1, stack);
      toPresent
          .get(0)
          .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
          .dstAccessMask(0)
          .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
          .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
          .image(swapImage)
          .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
          .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
      toPresent
          .get(0)
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      toPresent.position(0);
      vkCmdPipelineBarrier(
          cmd,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT,
          0,
          null,
          null,
          toPresent);

      VulkanContext.checkVk(vkEndCommandBuffer(cmd), "end world cmd");

      if (useRt && verboseRt) {
        DiagLog.log("rt#" + frameId + " submit");
      }
      VkSubmitInfo submitInfo =
          VkSubmitInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_SUBMIT_INFO)
              .waitSemaphoreCount(1)
              .pWaitSemaphores(stack.longs(imageAvailableSemaphores[currentFrame]))
              .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
              .pCommandBuffers(stack.pointers(cmd.address()))
              .pSignalSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]));
      int submit =
          vkQueueSubmit(vulkan.getGraphicsQueue(), submitInfo, inFlightFences[currentFrame]);
      if (submit != VK_SUCCESS) {
        DiagLog.logVk("submit world render rt=" + useRt, submit);
      }
      VulkanContext.checkVk(submit, "submit world render");
      // Always breadcrumb submit — crash often happens before next waitFence.
      if (useRt) {
        DiagLog.logQuiet("rt#" + frameId + " submit ok");
      }

      org.lwjgl.vulkan.VkPresentInfoKHR presentInfo =
          org.lwjgl.vulkan.VkPresentInfoKHR.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
              .pWaitSemaphores(stack.longs(renderFinishedSemaphores[currentFrame]))
              .swapchainCount(1)
              .pSwapchains(stack.longs(swapchain.getSwapchain()))
              .pImageIndices(stack.ints(swapIndex));
      int present = vkQueuePresentKHR(vulkan.getPresentQueue(), presentInfo);
      if (present == VK_ERROR_DEVICE_LOST) {
        DiagLog.logVk("present", present);
        throw new IllegalStateException("DEVICE_LOST on present");
      }
      if (present == VK_ERROR_OUT_OF_DATE_KHR
          || present == VK_SUBOPTIMAL_KHR
          || window.wasFramebufferResized()) {
        window.clearFramebufferResized();
        recreateSwapchainResources();
      } else if (present != VK_SUCCESS) {
        DiagLog.logVk("present", present);
        throw new IllegalStateException("Failed to present: " + present);
      }
      if (useRt) {
        DiagLog.logQuiet("rt#" + frameId + " present ok");
        diagRtFrames++;
      }

      currentFrame = (currentFrame + 1) % MAX_FRAMES_IN_FLIGHT;
      if (!loggedFirstPresent) {
        loggedFirstPresent = true;
        System.out.println(
            "[Opencraft] world present ok drawn="
                + drawn
                + "/"
                + chunkMeshes.size()
                + " regions="
                + regionMeshes.size()
                + " extent="
                + targetWidth
                + "x"
                + targetHeight);
      }
    } catch (RuntimeException e) {
      DiagLog.log("render EXCEPTION rt=" + isRayTracingEnabled() + " " + e);
      throw e;
    }
  }

  /**
   * Copies the last presented world image to a PNG (BGRA → RGBA). Used for menu world icons.
   *
   * @param path output path
   */
  public void captureScreenshot(Path path) {
    if (readbackMapped == null || targetWidth <= 0 || targetHeight <= 0) {
      System.err.println("[Opencraft] Screenshot skipped: no readback buffer");
      return;
    }
    long srcImage = lastPresentSourceImage != VK_NULL_HANDLE ? lastPresentSourceImage : colorImage;
    if (srcImage == VK_NULL_HANDLE) {
      System.err.println("[Opencraft] Screenshot skipped: no source image");
      return;
    }
    try {
      vulkan.waitIdle();
      copyImageToReadback(srcImage, targetWidth, targetHeight);
      vulkan.waitIdle();

      ByteBuffer src = readbackMapped.duplicate().clear();
      ByteBuffer rgba = BufferUtils.createByteBuffer(targetWidth * targetHeight * 4);
      // Offscreen / RT output is B8G8R8A8 — stbi expects RGBA.
      for (int i = 0, n = targetWidth * targetHeight; i < n; i++) {
        int o = i * 4;
        byte b = src.get(o);
        byte g = src.get(o + 1);
        byte r = src.get(o + 2);
        byte a = src.get(o + 3);
        rgba.put(o, r);
        rgba.put(o + 1, g);
        rgba.put(o + 2, b);
        rgba.put(o + 3, a != 0 ? a : (byte) 255);
      }
      Files.createDirectories(path.getParent());
      if (!stbi_write_png(path.toString(), targetWidth, targetHeight, 4, rgba, targetWidth * 4)) {
        System.err.println("[Opencraft] Screenshot write failed: " + path);
      } else {
        System.out.println("[Opencraft] Screenshot saved " + path);
      }
    } catch (Exception e) {
      System.err.println("Screenshot failed: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /** GPU copy of a TRANSFER_SRC color image into the host-visible readback buffer. */
  private void copyImageToReadback(long image, int width, int height) {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo allocInfo =
          VkCommandBufferAllocateInfo.calloc(stack)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(1);
      PointerBuffer cmdPtr = stack.mallocPointer(1);
      VulkanContext.checkVk(vkAllocateCommandBuffers(device, allocInfo, cmdPtr), "screenshot cmd");
      VkCommandBuffer cmd = new VkCommandBuffer(cmdPtr.get(0), device);
      try {
        vkBeginCommandBuffer(
            cmd,
            VkCommandBufferBeginInfo.calloc(stack)
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT));

        // Ensure TRANSFER_SRC (last frame already left it there; UNDEFINED→SRC is unsafe, keep
        // SRC).
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier
            .get(0)
            .srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
            .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
            .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
            .image(image)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED);
        barrier
            .get(0)
            .subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        barrier.position(0);
        vkCmdPipelineBarrier(
            cmd,
            VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
            VK_PIPELINE_STAGE_TRANSFER_BIT,
            0,
            null,
            null,
            barrier);

        org.lwjgl.vulkan.VkBufferImageCopy.Buffer region =
            org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
        region
            .get(0)
            .bufferOffset(0)
            .bufferRowLength(0)
            .bufferImageHeight(0)
            .imageSubresource()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .mipLevel(0)
            .baseArrayLayer(0)
            .layerCount(1);
        region.get(0).imageOffset().set(0, 0, 0);
        region.get(0).imageExtent().set(width, height, 1);
        region.position(0);
        vkCmdCopyImageToBuffer(
            cmd, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readbackBuffer, region);

        VulkanContext.checkVk(vkEndCommandBuffer(cmd), "end screenshot cmd");
        VkSubmitInfo submit =
            VkSubmitInfo.calloc(stack).sType(VK_STRUCTURE_TYPE_SUBMIT_INFO).pCommandBuffers(cmdPtr);
        VulkanContext.checkVk(
            vkQueueSubmit(vulkan.getGraphicsQueue(), submit, VK_NULL_HANDLE), "screenshot submit");
      } finally {
        vulkan.waitIdle();
        vkFreeCommandBuffers(device, commandPool, cmdPtr);
      }
    }
  }

  /** Releases GPU resources and chunk meshes. */
  @Override
  public void close() {
    vulkan.waitIdle();
    clearMeshes();
    if (meshScheduler != null) {
      meshScheduler.close();
      meshScheduler = null;
    }
    if (lodMeshScheduler != null) {
      lodMeshScheduler.close();
      lodMeshScheduler = null;
    }
    destroyHudStaging();
    if (rtPass != null) {
      rtPass.close();
      rtPass = null;
    }
    destroySyncObjects();
    if (commandPool != VK_NULL_HANDLE) {
      vkDestroyCommandPool(vulkan.getDevice(), commandPool, null);
      commandPool = VK_NULL_HANDLE;
    }
    destroyUniformBuffers();
    destroySkyUniformBuffers();
    if (descriptorPool != VK_NULL_HANDLE) {
      vkDestroyDescriptorPool(vulkan.getDevice(), descriptorPool, null);
      descriptorPool = VK_NULL_HANDLE;
    }
    destroyTextureResources();
    destroySunTextureResources();
    destroyOffscreenTargets();
    if (translucentPipeline != VK_NULL_HANDLE) {
      vkDestroyPipeline(vulkan.getDevice(), translucentPipeline, null);
      translucentPipeline = VK_NULL_HANDLE;
    }
    if (graphicsPipeline != VK_NULL_HANDLE) {
      vkDestroyPipeline(vulkan.getDevice(), graphicsPipeline, null);
      graphicsPipeline = VK_NULL_HANDLE;
    }
    if (skyPipeline != VK_NULL_HANDLE) {
      vkDestroyPipeline(vulkan.getDevice(), skyPipeline, null);
      skyPipeline = VK_NULL_HANDLE;
    }
    if (pipelineLayout != VK_NULL_HANDLE) {
      vkDestroyPipelineLayout(vulkan.getDevice(), pipelineLayout, null);
      pipelineLayout = VK_NULL_HANDLE;
    }
    if (skyPipelineLayout != VK_NULL_HANDLE) {
      vkDestroyPipelineLayout(vulkan.getDevice(), skyPipelineLayout, null);
      skyPipelineLayout = VK_NULL_HANDLE;
    }
    if (renderPass != VK_NULL_HANDLE) {
      vkDestroyRenderPass(vulkan.getDevice(), renderPass, null);
      renderPass = VK_NULL_HANDLE;
    }
    if (descriptorSetLayout != VK_NULL_HANDLE) {
      vkDestroyDescriptorSetLayout(vulkan.getDevice(), descriptorSetLayout, null);
      descriptorSetLayout = VK_NULL_HANDLE;
    }
    if (skyDescriptorSetLayout != VK_NULL_HANDLE) {
      vkDestroyDescriptorSetLayout(vulkan.getDevice(), skyDescriptorSetLayout, null);
      skyDescriptorSetLayout = VK_NULL_HANDLE;
    }
  }

  /** Rebuilds offscreen color/depth targets after a window resize. */
  public void recreateSwapchainResources() {
    vulkan.waitIdle();
    swapchain.recreate();
    recreateOffscreenTargets();
  }

  private void recreateOffscreenTargets() {
    vulkan.waitIdle();
    destroyOffscreenTargets();
    createOffscreenTargets();
  }

  private void clearMeshes() {
    flushPendingMeshFrees();
    for (GpuRegionMesh mesh : regionMeshes.values()) {
      mesh.free(vulkan);
    }
    regionMeshes.clear();
    chunkMeshes.clear();
    dirtyRegions.clear();
    for (GpuRegionMesh mesh : lodMeshes.values()) {
      mesh.free(vulkan);
    }
    lodMeshes.clear();
    lodDesired.clear();
  }

  private void ensureHudStaging(int bytes) {
    if (hudStagingMapped != null && hudStagingCapacity >= bytes) {
      return;
    }
    destroyHudStaging();
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      hudStagingBuffer = createBuffer(device, stack, bytes, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
      hudStagingMemory =
          allocateBufferMemory(
              vulkan,
              device,
              stack,
              hudStagingBuffer,
              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, hudStagingMemory, 0, bytes, 0, mapped);
      hudStagingMapped = mapped.getByteBuffer(0, bytes);
      hudStagingCapacity = bytes;
    }
  }

  private void destroyHudStaging() {
    VkDevice device = vulkan.getDevice();
    if (hudStagingMapped != null) {
      vkUnmapMemory(device, hudStagingMemory);
      hudStagingMapped = null;
    }
    if (hudStagingBuffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, hudStagingBuffer, null);
      hudStagingBuffer = VK_NULL_HANDLE;
    }
    if (hudStagingMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, hudStagingMemory, null);
      hudStagingMemory = VK_NULL_HANDLE;
    }
    hudStagingCapacity = 0;
  }

  private void updateUniformBuffer(Player player, int frame) {
    Matrix4f mvp = buildMvp(player);
    FloatBuffer buffer = uniformBuffersMapped[frame].asFloatBuffer();
    buffer.position(0);
    mvp.get(buffer);
    double[] eye = player.getEyePosition();
    boolean underwater = player.isEyeInWater();
    float[] span = atlas != null ? atlas.tileUvSpan() : new float[] {1f, 1f};
    // Fog ends near the LOD horizon so distant patches fade into the sky.
    float fogEnd = activeLodDistance * Chunk.SIZE_X * 0.92f;
    float fogStart = Math.max(activeRenderDistance * Chunk.SIZE_X * 0.45f, fogEnd * 0.35f);
    float fogR = underwater ? 0.04f : 0.53f;
    float fogG = underwater ? 0.18f : 0.81f;
    float fogB = underwater ? 0.32f : 0.92f;
    // std140: mat4 @0, vec4 cameraPosUnderwater @64, fogParams @80, fogColor @96
    buffer.put(16, (float) eye[0]);
    buffer.put(17, (float) eye[1]);
    buffer.put(18, (float) eye[2]);
    buffer.put(19, underwater ? 1f : 0f);
    buffer.put(20, fogStart);
    buffer.put(21, fogEnd);
    buffer.put(22, span[0]);
    buffer.put(23, span[1]);
    buffer.put(24, fogR);
    buffer.put(25, fogG);
    buffer.put(26, fogB);
    float cloudTime = (System.nanoTime() - cloudTimeOriginNanos) * 1e-9f;
    buffer.put(27, cloudTime);
  }

  private void updateSkyUniformBuffer(Player player, int frame) {
    float aspect = targetWidth / (float) Math.max(1, targetHeight);
    Matrix4f proj =
        new Matrix4f()
            .perspective(
                (float) Math.toRadians(70.0),
                aspect,
                0.05f,
                Math.max(512f, activeLodDistance * Chunk.SIZE_X * 1.6f))
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

    FloatBuffer buffer = skyUniformBuffersMapped[frame].asFloatBuffer();
    viewInv.get(0, buffer);
    projInv.get(16, buffer);
    float sunLen = (float) Math.sqrt(SUN_X * SUN_X + SUN_Y * SUN_Y + SUN_Z * SUN_Z);
    buffer.put(32, SUN_X / sunLen);
    buffer.put(33, SUN_Y / sunLen);
    buffer.put(34, SUN_Z / sunLen);
    buffer.put(35, SUN_ANGULAR_RADIUS);
    float cloudTime = (System.nanoTime() - cloudTimeOriginNanos) * 1e-9f;
    buffer.put(36, cloudTime);
    buffer.put(37, 0f);
    buffer.put(38, 0f);
    buffer.put(39, player.isEyeInWater() ? 1f : 0f);
  }

  private Matrix4f buildMvp(Player player) {
    float aspect = targetWidth / (float) Math.max(1, targetHeight);
    Matrix4f proj =
        new Matrix4f()
            .perspective(
                (float) Math.toRadians(70.0),
                aspect,
                0.05f,
                Math.max(512f, activeLodDistance * Chunk.SIZE_X * 1.6f))
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
    return proj.mul(view);
  }

  private void createRenderPass() {
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkAttachmentDescription.Buffer attachments =
          org.lwjgl.vulkan.VkAttachmentDescription.calloc(2, stack);
      attachments
          .get(0)
          .format(COLOR_FORMAT)
          .samples(VK_SAMPLE_COUNT_1_BIT)
          .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
          .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
          .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
          .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
          .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
          .finalLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
      attachments
          .get(1)
          .format(VK_FORMAT_D32_SFLOAT)
          .samples(VK_SAMPLE_COUNT_1_BIT)
          .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR)
          .storeOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
          .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
          .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
          .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
          .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
      attachments.position(0);

      org.lwjgl.vulkan.VkAttachmentReference.Buffer colorRef =
          org.lwjgl.vulkan.VkAttachmentReference.calloc(1, stack);
      colorRef.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
      colorRef.position(0);
      org.lwjgl.vulkan.VkAttachmentReference depthRef =
          org.lwjgl.vulkan.VkAttachmentReference.calloc(stack)
              .attachment(1)
              .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

      org.lwjgl.vulkan.VkSubpassDescription.Buffer subpass =
          org.lwjgl.vulkan.VkSubpassDescription.calloc(1, stack);
      subpass
          .get(0)
          .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
          .colorAttachmentCount(1)
          .pColorAttachments(colorRef)
          .pDepthStencilAttachment(depthRef);
      subpass.position(0);

      org.lwjgl.vulkan.VkSubpassDependency.Buffer dependency =
          org.lwjgl.vulkan.VkSubpassDependency.calloc(2, stack);
      dependency
          .get(0)
          .srcSubpass(VK_SUBPASS_EXTERNAL)
          .dstSubpass(0)
          .srcStageMask(
              VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                  | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
          .dstStageMask(
              VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                  | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT)
          .srcAccessMask(0)
          .dstAccessMask(
              VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT);
      dependency
          .get(1)
          .srcSubpass(0)
          .dstSubpass(VK_SUBPASS_EXTERNAL)
          .srcStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
          .dstStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT)
          .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
          .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
      dependency.position(0);

      VkRenderPassCreateInfo renderPassInfo =
          VkRenderPassCreateInfo.calloc(stack)
              .pAttachments(attachments)
              .pSubpasses(subpass)
              .pDependencies(dependency);

      LongBuffer pass = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateRenderPass(device, renderPassInfo, null, pass), "render pass");
      renderPass = pass.get(0);
    }
  }

  private void createDescriptorSetLayout() {
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
          org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(3, stack);
      bindings
          .get(0)
          .binding(0)
          .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings
          .get(1)
          .binding(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings
          .get(2)
          .binding(2)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings.position(0);

      org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
          org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).pBindings(bindings);
      LongBuffer layout = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateDescriptorSetLayout(vulkan.getDevice(), layoutInfo, null, layout),
          "descriptor set layout");
      descriptorSetLayout = layout.get(0);
    }
  }

  private void createGraphicsPipeline() {
    VkDevice device = vulkan.getDevice();
    long vertModule = createShaderModule("shaders/world.vert", shaderc_vertex_shader);
    long fragModule = createShaderModule("shaders/world.frag", shaderc_fragment_shader);
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer shaderStages =
          org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(2, stack);
      shaderStages
          .get(0)
          .stage(VK_SHADER_STAGE_VERTEX_BIT)
          .module(vertModule)
          .pName(stack.UTF8("main"));
      shaderStages
          .get(1)
          .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
          .module(fragModule)
          .pName(stack.UTF8("main"));
      shaderStages.position(0);

      org.lwjgl.vulkan.VkVertexInputBindingDescription.Buffer binding =
          org.lwjgl.vulkan.VkVertexInputBindingDescription.calloc(1, stack);
      binding.get(0).binding(0).stride(VERTEX_STRIDE).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
      binding.position(0);

      org.lwjgl.vulkan.VkVertexInputAttributeDescription.Buffer attrs =
          org.lwjgl.vulkan.VkVertexInputAttributeDescription.calloc(4, stack);
      attrs.get(0).binding(0).location(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
      attrs.get(1).binding(0).location(1).format(VK_FORMAT_R32G32_SFLOAT).offset(12);
      attrs.get(2).binding(0).location(2).format(VK_FORMAT_R32_SFLOAT).offset(20);
      attrs.get(3).binding(0).location(3).format(VK_FORMAT_R32G32_SFLOAT).offset(24);
      attrs.position(0);

      org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo vertexInput =
          org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo.calloc(stack)
              .pVertexBindingDescriptions(binding)
              .pVertexAttributeDescriptions(attrs);

      org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo inputAssembly =
          org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
              .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

      org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo viewportState =
          org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
              .viewportCount(1)
              .scissorCount(1);

      org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo rasterizer =
          org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
              .polygonMode(VK_POLYGON_MODE_FILL)
              .cullMode(VK_CULL_MODE_BACK_BIT)
              .frontFace(VK_FRONT_FACE_CLOCKWISE)
              .lineWidth(1f);

      org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo multisampling =
          org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
              .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

      org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamicState =
          org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
              .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

      org.lwjgl.vulkan.VkPipelineLayoutCreateInfo pipelineLayoutInfo =
          org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
              .pSetLayouts(stack.longs(descriptorSetLayout));
      LongBuffer layout = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreatePipelineLayout(device, pipelineLayoutInfo, null, layout), "pipeline layout");
      pipelineLayout = layout.get(0);

      // Opaque: depth write on, no blending (solid terrain).
      org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo opaqueDepth =
          org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo.calloc(stack)
              .depthTestEnable(true)
              .depthWriteEnable(true)
              .depthCompareOp(VK_COMPARE_OP_LESS);
      org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.Buffer opaqueBlendAtt =
          org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack);
      opaqueBlendAtt
          .get(0)
          .blendEnable(false)
          .colorWriteMask(
              VK_COLOR_COMPONENT_R_BIT
                  | VK_COLOR_COMPONENT_G_BIT
                  | VK_COLOR_COMPONENT_B_BIT
                  | VK_COLOR_COMPONENT_A_BIT);
      opaqueBlendAtt.position(0);
      org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo opaqueBlend =
          org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack)
              .pAttachments(opaqueBlendAtt);

      VkGraphicsPipelineCreateInfo.Buffer opaqueInfo =
          VkGraphicsPipelineCreateInfo.calloc(1, stack);
      opaqueInfo
          .get(0)
          .pStages(shaderStages)
          .pVertexInputState(vertexInput)
          .pInputAssemblyState(inputAssembly)
          .pViewportState(viewportState)
          .pRasterizationState(rasterizer)
          .pMultisampleState(multisampling)
          .pDepthStencilState(opaqueDepth)
          .pColorBlendState(opaqueBlend)
          .pDynamicState(dynamicState)
          .layout(pipelineLayout)
          .renderPass(renderPass)
          .subpass(0);
      opaqueInfo.position(0);
      LongBuffer opaquePipe = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, opaqueInfo, null, opaquePipe),
          "opaque pipeline");
      graphicsPipeline = opaquePipe.get(0);

      // Translucent water: depth test on, depth write off, alpha blend.
      org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo translucentDepth =
          org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo.calloc(stack)
              .depthTestEnable(true)
              .depthWriteEnable(false)
              .depthCompareOp(VK_COMPARE_OP_LESS);
      org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.Buffer translucentBlendAtt =
          org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack);
      translucentBlendAtt
          .get(0)
          .blendEnable(true)
          .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
          .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
          .colorBlendOp(VK_BLEND_OP_ADD)
          .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
          .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
          .alphaBlendOp(VK_BLEND_OP_ADD)
          .colorWriteMask(
              VK_COLOR_COMPONENT_R_BIT
                  | VK_COLOR_COMPONENT_G_BIT
                  | VK_COLOR_COMPONENT_B_BIT
                  | VK_COLOR_COMPONENT_A_BIT);
      translucentBlendAtt.position(0);
      org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo translucentBlend =
          org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack)
              .pAttachments(translucentBlendAtt);

      VkGraphicsPipelineCreateInfo.Buffer translucentInfo =
          VkGraphicsPipelineCreateInfo.calloc(1, stack);
      translucentInfo
          .get(0)
          .pStages(shaderStages)
          .pVertexInputState(vertexInput)
          .pInputAssemblyState(inputAssembly)
          .pViewportState(viewportState)
          .pRasterizationState(rasterizer)
          .pMultisampleState(multisampling)
          .pDepthStencilState(translucentDepth)
          .pColorBlendState(translucentBlend)
          .pDynamicState(dynamicState)
          .layout(pipelineLayout)
          .renderPass(renderPass)
          .subpass(0);
      translucentInfo.position(0);
      LongBuffer translucentPipe = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, translucentInfo, null, translucentPipe),
          "translucent pipeline");
      translucentPipeline = translucentPipe.get(0);
    } finally {
      vkDestroyShaderModule(device, vertModule, null);
      vkDestroyShaderModule(device, fragModule, null);
    }
  }

  private void createSkyDescriptorSetLayout() {
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.Buffer bindings =
          org.lwjgl.vulkan.VkDescriptorSetLayoutBinding.calloc(3, stack);
      bindings
          .get(0)
          .binding(0)
          .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings
          .get(1)
          .binding(1)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings
          .get(2)
          .binding(2)
          .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(1)
          .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
      bindings.position(0);
      org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo layoutInfo =
          org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo.calloc(stack).pBindings(bindings);
      LongBuffer layout = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateDescriptorSetLayout(vulkan.getDevice(), layoutInfo, null, layout),
          "sky descriptor set layout");
      skyDescriptorSetLayout = layout.get(0);
    }
  }

  private void createSkyPipeline() {
    VkDevice device = vulkan.getDevice();
    long vertModule = createShaderModule("shaders/sky.vert", shaderc_vertex_shader);
    long fragModule = createShaderModule("shaders/sky.frag", shaderc_fragment_shader);
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.Buffer shaderStages =
          org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo.calloc(2, stack);
      shaderStages
          .get(0)
          .stage(VK_SHADER_STAGE_VERTEX_BIT)
          .module(vertModule)
          .pName(stack.UTF8("main"));
      shaderStages
          .get(1)
          .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
          .module(fragModule)
          .pName(stack.UTF8("main"));
      shaderStages.position(0);

      org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo vertexInput =
          org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo.calloc(stack);

      org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo inputAssembly =
          org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
              .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

      org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo viewportState =
          org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo.calloc(stack)
              .viewportCount(1)
              .scissorCount(1);

      org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo rasterizer =
          org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo.calloc(stack)
              .polygonMode(VK_POLYGON_MODE_FILL)
              .cullMode(VK_CULL_MODE_NONE)
              .frontFace(VK_FRONT_FACE_CLOCKWISE)
              .lineWidth(1f);

      org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo multisampling =
          org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo.calloc(stack)
              .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

      org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo depthStencil =
          org.lwjgl.vulkan.VkPipelineDepthStencilStateCreateInfo.calloc(stack)
              .depthTestEnable(false)
              .depthWriteEnable(false)
              .depthCompareOp(VK_COMPARE_OP_LESS);

      org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.Buffer blendAtt =
          org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState.calloc(1, stack);
      blendAtt
          .get(0)
          .blendEnable(false)
          .colorWriteMask(
              VK_COLOR_COMPONENT_R_BIT
                  | VK_COLOR_COMPONENT_G_BIT
                  | VK_COLOR_COMPONENT_B_BIT
                  | VK_COLOR_COMPONENT_A_BIT);
      blendAtt.position(0);
      org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo blend =
          org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo.calloc(stack).pAttachments(blendAtt);

      org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo dynamicState =
          org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo.calloc(stack)
              .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

      org.lwjgl.vulkan.VkPipelineLayoutCreateInfo pipelineLayoutInfo =
          org.lwjgl.vulkan.VkPipelineLayoutCreateInfo.calloc(stack)
              .pSetLayouts(stack.longs(skyDescriptorSetLayout));
      LongBuffer layout = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreatePipelineLayout(device, pipelineLayoutInfo, null, layout), "sky pipeline layout");
      skyPipelineLayout = layout.get(0);

      VkGraphicsPipelineCreateInfo.Buffer pipeInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
      pipeInfo
          .get(0)
          .pStages(shaderStages)
          .pVertexInputState(vertexInput)
          .pInputAssemblyState(inputAssembly)
          .pViewportState(viewportState)
          .pRasterizationState(rasterizer)
          .pMultisampleState(multisampling)
          .pDepthStencilState(depthStencil)
          .pColorBlendState(blend)
          .pDynamicState(dynamicState)
          .layout(skyPipelineLayout)
          .renderPass(renderPass)
          .subpass(0);
      pipeInfo.position(0);
      LongBuffer pipe = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, pipeInfo, null, pipe), "sky pipeline");
      skyPipeline = pipe.get(0);
    } finally {
      vkDestroyShaderModule(device, vertModule, null);
      vkDestroyShaderModule(device, fragModule, null);
    }
  }

  private long createShaderModule(String path, int kind) {
    ByteBuffer spirv = ShaderCompiler.compileGlsl(path, kind);
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkShaderModuleCreateInfo createInfo =
          org.lwjgl.vulkan.VkShaderModuleCreateInfo.calloc(stack).pCode(spirv);
      LongBuffer module = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateShaderModule(vulkan.getDevice(), createInfo, null, module), "shader module");
      return module.get(0);
    } finally {
      memFree(spirv);
    }
  }

  private void createOffscreenTargets() {
    targetWidth = Math.max(1, window.getWidth());
    targetHeight = Math.max(1, window.getHeight());
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      VkImageCreateInfo colorInfo =
          VkImageCreateInfo.calloc(stack)
              .imageType(VK_IMAGE_TYPE_2D)
              .format(COLOR_FORMAT)
              .arrayLayers(1)
              .samples(VK_SAMPLE_COUNT_1_BIT)
              .tiling(VK_IMAGE_TILING_OPTIMAL)
              .usage(
                  VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT
                      | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                      | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      colorInfo.extent().set(targetWidth, targetHeight, 1);
      colorInfo.mipLevels(1);
      LongBuffer image = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImage(device, colorInfo, null, image), "color image");
      colorImage = image.get(0);

      VkMemoryRequirements colorReqs = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device, colorImage, colorReqs);
      VkMemoryAllocateInfo colorAlloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(colorReqs.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      colorReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
      LongBuffer colorMem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, colorAlloc, null, colorMem), "color memory");
      colorImageMemory = colorMem.get(0);
      org.lwjgl.vulkan.VK10.vkBindImageMemory(device, colorImage, colorImageMemory, 0);

      VkImageViewCreateInfo colorViewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .image(colorImage)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(COLOR_FORMAT);
      colorViewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      LongBuffer colorView = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateImageView(device, colorViewInfo, null, colorView), "color view");
      colorImageView = colorView.get(0);

      VkImageCreateInfo depthInfo =
          VkImageCreateInfo.calloc(stack)
              .imageType(VK_IMAGE_TYPE_2D)
              .format(VK_FORMAT_D32_SFLOAT)
              .arrayLayers(1)
              .samples(VK_SAMPLE_COUNT_1_BIT)
              .tiling(VK_IMAGE_TILING_OPTIMAL)
              .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      depthInfo.extent().set(targetWidth, targetHeight, 1);
      depthInfo.mipLevels(1);
      VulkanContext.checkVk(vkCreateImage(device, depthInfo, null, image), "depth image");
      depthImage = image.get(0);

      VkMemoryRequirements depthReqs = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device, depthImage, depthReqs);
      VkMemoryAllocateInfo depthAlloc =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(depthReqs.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      depthReqs.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
      LongBuffer depthMem = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, depthAlloc, null, depthMem), "depth memory");
      depthImageMemory = depthMem.get(0);
      org.lwjgl.vulkan.VK10.vkBindImageMemory(device, depthImage, depthImageMemory, 0);

      VkImageViewCreateInfo depthViewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .image(depthImage)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(VK_FORMAT_D32_SFLOAT);
      depthViewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_DEPTH_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      LongBuffer depthView = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateImageView(device, depthViewInfo, null, depthView), "depth view");
      depthImageView = depthView.get(0);

      LongBuffer attachments = stack.mallocLong(2);
      attachments.put(0, colorImageView).put(1, depthImageView);
      VkFramebufferCreateInfo framebufferInfo =
          VkFramebufferCreateInfo.calloc(stack)
              .renderPass(renderPass)
              .pAttachments(attachments)
              .width(targetWidth)
              .height(targetHeight)
              .layers(1);
      LongBuffer fb = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateFramebuffer(device, framebufferInfo, null, fb), "framebuffer");
      framebuffer = fb.get(0);

      long bytes = (long) targetWidth * targetHeight * 4;
      readbackBuffer = createBuffer(device, stack, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
      readbackMemory =
          allocateBufferMemory(
              vulkan,
              device,
              stack,
              readbackBuffer,
              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, readbackMemory, 0, bytes, 0, mapped);
      readbackMapped = mapped.getByteBuffer(0, (int) bytes);
    }
  }

  private void destroyOffscreenTargets() {
    VkDevice device = vulkan.getDevice();
    if (readbackMapped != null) {
      vkUnmapMemory(device, readbackMemory);
      readbackMapped = null;
    }
    if (readbackBuffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, readbackBuffer, null);
      readbackBuffer = VK_NULL_HANDLE;
    }
    if (readbackMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, readbackMemory, null);
      readbackMemory = VK_NULL_HANDLE;
    }
    if (framebuffer != VK_NULL_HANDLE) {
      vkDestroyFramebuffer(device, framebuffer, null);
      framebuffer = VK_NULL_HANDLE;
    }
    if (colorImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, colorImageView, null);
      colorImageView = VK_NULL_HANDLE;
    }
    if (colorImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, colorImage, null);
      colorImage = VK_NULL_HANDLE;
    }
    if (colorImageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, colorImageMemory, null);
      colorImageMemory = VK_NULL_HANDLE;
    }
    if (depthImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, depthImageView, null);
      depthImageView = VK_NULL_HANDLE;
    }
    if (depthImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, depthImage, null);
      depthImage = VK_NULL_HANDLE;
    }
    if (depthImageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, depthImageMemory, null);
      depthImageMemory = VK_NULL_HANDLE;
    }
    targetWidth = 0;
    targetHeight = 0;
  }

  private void createTextureResources() {
    int width = atlas.getWidth();
    int height = atlas.getHeight();
    VkDevice device = vulkan.getDevice();
    long stagingBuffer = VK_NULL_HANDLE;
    long stagingMemory = VK_NULL_HANDLE;
    try (MemoryStack stack = stackPush()) {
      long imageSize = (long) width * height * 4;
      stagingBuffer = createBuffer(device, stack, imageSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
      stagingMemory =
          allocateBufferMemory(
              vulkan,
              device,
              stack,
              stagingBuffer,
              VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
      PointerBuffer mapped = stack.mallocPointer(1);
      vkMapMemory(device, stagingMemory, 0, imageSize, 0, mapped);
      ByteBuffer pixels = atlas.getPixels().duplicate();
      pixels.rewind();
      mapped.getByteBuffer(0, (int) imageSize).put(pixels);
      vkUnmapMemory(device, stagingMemory);

      VkImageCreateInfo imageInfo =
          VkImageCreateInfo.calloc(stack)
              .imageType(VK_IMAGE_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM)
              .arrayLayers(1)
              .samples(VK_SAMPLE_COUNT_1_BIT)
              .tiling(VK_IMAGE_TILING_OPTIMAL)
              .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
              .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
      imageInfo.extent().set(width, height, 1);
      imageInfo.mipLevels(1);
      LongBuffer image = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImage(device, imageInfo, null, image), "texture image");
      textureImage = image.get(0);

      VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
      vkGetImageMemoryRequirements(device, textureImage, memRequirements);
      VkMemoryAllocateInfo allocInfo =
          VkMemoryAllocateInfo.calloc(stack)
              .allocationSize(memRequirements.size())
              .memoryTypeIndex(
                  vulkan.findMemoryType(
                      memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
      LongBuffer memory = stack.mallocLong(1);
      VulkanContext.checkVk(vkAllocateMemory(device, allocInfo, null, memory), "texture memory");
      textureImageMemory = memory.get(0);
      org.lwjgl.vulkan.VK10.vkBindImageMemory(device, textureImage, textureImageMemory, 0);

      transitionImageLayout(
          textureImage,
          VK_IMAGE_LAYOUT_UNDEFINED,
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          VK_IMAGE_ASPECT_COLOR_BIT);
      copyBufferToImage(stagingBuffer, textureImage, width, height);
      transitionImageLayout(
          textureImage,
          VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
          VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
          VK_IMAGE_ASPECT_COLOR_BIT);

      VkImageViewCreateInfo viewInfo =
          VkImageViewCreateInfo.calloc(stack)
              .image(textureImage)
              .viewType(VK_IMAGE_VIEW_TYPE_2D)
              .format(VK_FORMAT_R8G8B8A8_UNORM);
      viewInfo
          .subresourceRange()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      LongBuffer view = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateImageView(device, viewInfo, null, view), "texture view");
      textureImageView = view.get(0);

      org.lwjgl.vulkan.VkSamplerCreateInfo samplerInfo =
          org.lwjgl.vulkan.VkSamplerCreateInfo.calloc(stack)
              .magFilter(VK_FILTER_NEAREST)
              .minFilter(VK_FILTER_NEAREST)
              .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
              .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
              .maxAnisotropy(1f)
              .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);
      LongBuffer sampler = stack.mallocLong(1);
      VulkanContext.checkVk(vkCreateSampler(device, samplerInfo, null, sampler), "sampler");
      textureSampler = sampler.get(0);
    } finally {
      if (stagingBuffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(device, stagingBuffer, null);
      }
      if (stagingMemory != VK_NULL_HANDLE) {
        vkFreeMemory(device, stagingMemory, null);
      }
    }
  }

  private void createSunTextureResources() {
    final int width = 32;
    final int height = 32;
    ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
    try {
      float cx = (width - 1) * 0.5f;
      float cy = (height - 1) * 0.5f;
      float r = 11f;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          float d = (float) Math.hypot(x - cx, y - cy);
          int i = (y * width + x) * 4;
          int rr;
          int gg;
          int bb;
          int aa;
          if (d <= r * 0.55f) {
            rr = 255;
            gg = 236;
            bb = 140;
            aa = 255;
          } else if (d <= r * 0.85f) {
            rr = 255;
            gg = 200;
            bb = 64;
            aa = 255;
          } else if (d <= r) {
            rr = 255;
            gg = 150;
            bb = 32;
            aa = 230;
          } else if (d <= r + 2.5f) {
            float t = Math.max(0f, 1f - (d - r) / 2.5f);
            rr = 255;
            gg = 180;
            bb = 80;
            aa = (int) (160 * t);
          } else {
            rr = 0;
            gg = 0;
            bb = 0;
            aa = 0;
          }
          pixels.put(i, (byte) rr);
          pixels.put(i + 1, (byte) gg);
          pixels.put(i + 2, (byte) bb);
          pixels.put(i + 3, (byte) aa);
        }
      }
      overlaySunPngIfPresent(pixels, width, height);

      VkDevice device = vulkan.getDevice();
      long stagingBuffer = VK_NULL_HANDLE;
      long stagingMemory = VK_NULL_HANDLE;
      try (MemoryStack stack = stackPush()) {
        long imageSize = (long) width * height * 4;
        stagingBuffer = createBuffer(device, stack, imageSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        stagingMemory =
            allocateBufferMemory(
                vulkan,
                device,
                stack,
                stagingBuffer,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        PointerBuffer mapped = stack.mallocPointer(1);
        vkMapMemory(device, stagingMemory, 0, imageSize, 0, mapped);
        ByteBuffer dst = mapped.getByteBuffer(0, (int) imageSize);
        pixels.rewind();
        dst.put(pixels);
        vkUnmapMemory(device, stagingMemory);

        VkImageCreateInfo imageInfo =
            VkImageCreateInfo.calloc(stack)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        imageInfo.extent().set(width, height, 1);
        imageInfo.mipLevels(1);
        LongBuffer image = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateImage(device, imageInfo, null, image), "sun image");
        sunImage = image.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
        vkGetImageMemoryRequirements(device, sunImage, memRequirements);
        VkMemoryAllocateInfo allocInfo =
            VkMemoryAllocateInfo.calloc(stack)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    vulkan.findMemoryType(
                        memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        LongBuffer memory = stack.mallocLong(1);
        VulkanContext.checkVk(vkAllocateMemory(device, allocInfo, null, memory), "sun memory");
        sunImageMemory = memory.get(0);
        org.lwjgl.vulkan.VK10.vkBindImageMemory(device, sunImage, sunImageMemory, 0);

        transitionImageLayout(
            sunImage,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_ASPECT_COLOR_BIT);
        copyBufferToImage(stagingBuffer, sunImage, width, height);
        transitionImageLayout(
            sunImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_IMAGE_ASPECT_COLOR_BIT);

        VkImageViewCreateInfo viewInfo =
            VkImageViewCreateInfo.calloc(stack)
                .image(sunImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM);
        viewInfo
            .subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        LongBuffer view = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateImageView(device, viewInfo, null, view), "sun view");
        sunImageView = view.get(0);

        org.lwjgl.vulkan.VkSamplerCreateInfo samplerInfo =
            org.lwjgl.vulkan.VkSamplerCreateInfo.calloc(stack)
                .magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .maxAnisotropy(1f)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);
        LongBuffer sampler = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateSampler(device, samplerInfo, null, sampler), "sun sampler");
        sunSampler = sampler.get(0);
      } finally {
        if (stagingBuffer != VK_NULL_HANDLE) {
          vkDestroyBuffer(device, stagingBuffer, null);
        }
        if (stagingMemory != VK_NULL_HANDLE) {
          vkFreeMemory(device, stagingMemory, null);
        }
      }
    } finally {
      memFree(pixels);
    }
  }

  /** Optionally replaces procedural sun pixels with {@code textures/sky/sun.png} via ImageIO. */
  private static void overlaySunPngIfPresent(ByteBuffer pixels, int width, int height) {
    try (InputStream in =
        WorldRenderer.class.getClassLoader().getResourceAsStream("textures/sky/sun.png")) {
      if (in == null) {
        return;
      }
      java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
      if (img == null || img.getWidth() != width || img.getHeight() != height) {
        return;
      }
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int argb = img.getRGB(x, y);
          int i = (y * width + x) * 4;
          pixels.put(i, (byte) ((argb >> 16) & 0xff));
          pixels.put(i + 1, (byte) ((argb >> 8) & 0xff));
          pixels.put(i + 2, (byte) (argb & 0xff));
          pixels.put(i + 3, (byte) ((argb >> 24) & 0xff));
        }
      }
    } catch (Exception ignored) {
      // Keep procedural disc.
    }
  }

  /** Loads {@code textures/sky/clouds.png} (RGBA, tileable) for the scrolling sky cloud layer. */
  private void createCloudTextureResources() {
    java.awt.image.BufferedImage img = null;
    try (InputStream in =
        WorldRenderer.class.getClassLoader().getResourceAsStream("textures/sky/clouds.png")) {
      if (in != null) {
        img = javax.imageio.ImageIO.read(in);
      }
    } catch (Exception ignored) {
      img = null;
    }
    final int width = img != null ? img.getWidth() : 64;
    final int height = img != null ? img.getHeight() : 64;
    ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
    try {
      if (img != null) {
        for (int y = 0; y < height; y++) {
          for (int x = 0; x < width; x++) {
            int argb = img.getRGB(x, y);
            int i = (y * width + x) * 4;
            pixels.put(i, (byte) ((argb >> 16) & 0xff));
            pixels.put(i + 1, (byte) ((argb >> 8) & 0xff));
            pixels.put(i + 2, (byte) (argb & 0xff));
            pixels.put(i + 3, (byte) ((argb >> 24) & 0xff));
          }
        }
      } else {
        // Soft procedural fallback if the PNG is missing.
        for (int y = 0; y < height; y++) {
          for (int x = 0; x < width; x++) {
            float nx = x / (float) width;
            float ny = y / (float) height;
            float n =
                (float)
                    (0.5 + 0.5 * Math.sin(nx * 12.0 + ny * 3.0) * Math.cos(ny * 10.0 - nx * 2.0));
            float dens = Math.max(0f, n - 0.55f) * 2.2f;
            int a = dens < 0.08f ? 0 : (int) Math.min(200, dens * 220);
            int i = (y * width + x) * 4;
            pixels.put(i, (byte) 245);
            pixels.put(i + 1, (byte) 248);
            pixels.put(i + 2, (byte) 252);
            pixels.put(i + 3, (byte) a);
          }
        }
      }

      VkDevice device = vulkan.getDevice();
      long stagingBuffer = VK_NULL_HANDLE;
      long stagingMemory = VK_NULL_HANDLE;
      try (MemoryStack stack = stackPush()) {
        long imageSize = (long) width * height * 4;
        stagingBuffer = createBuffer(device, stack, imageSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
        stagingMemory =
            allocateBufferMemory(
                vulkan,
                device,
                stack,
                stagingBuffer,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        PointerBuffer mapped = stack.mallocPointer(1);
        vkMapMemory(device, stagingMemory, 0, imageSize, 0, mapped);
        ByteBuffer dst = mapped.getByteBuffer(0, (int) imageSize);
        pixels.rewind();
        dst.put(pixels);
        vkUnmapMemory(device, stagingMemory);

        VkImageCreateInfo imageInfo =
            VkImageCreateInfo.calloc(stack)
                .imageType(VK_IMAGE_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM)
                .arrayLayers(1)
                .samples(VK_SAMPLE_COUNT_1_BIT)
                .tiling(VK_IMAGE_TILING_OPTIMAL)
                .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
        imageInfo.extent().set(width, height, 1);
        imageInfo.mipLevels(1);
        LongBuffer image = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateImage(device, imageInfo, null, image), "cloud image");
        cloudImage = image.get(0);

        VkMemoryRequirements memRequirements = VkMemoryRequirements.malloc(stack);
        vkGetImageMemoryRequirements(device, cloudImage, memRequirements);
        VkMemoryAllocateInfo allocInfo =
            VkMemoryAllocateInfo.calloc(stack)
                .allocationSize(memRequirements.size())
                .memoryTypeIndex(
                    vulkan.findMemoryType(
                        memRequirements.memoryTypeBits(), VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
        LongBuffer memory = stack.mallocLong(1);
        VulkanContext.checkVk(vkAllocateMemory(device, allocInfo, null, memory), "cloud memory");
        cloudImageMemory = memory.get(0);
        org.lwjgl.vulkan.VK10.vkBindImageMemory(device, cloudImage, cloudImageMemory, 0);

        transitionImageLayout(
            cloudImage,
            VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_ASPECT_COLOR_BIT);
        copyBufferToImage(stagingBuffer, cloudImage, width, height);
        transitionImageLayout(
            cloudImage,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            VK_IMAGE_ASPECT_COLOR_BIT);

        VkImageViewCreateInfo viewInfo =
            VkImageViewCreateInfo.calloc(stack)
                .image(cloudImage)
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(VK_FORMAT_R8G8B8A8_UNORM);
        viewInfo
            .subresourceRange()
            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
            .baseMipLevel(0)
            .levelCount(1)
            .baseArrayLayer(0)
            .layerCount(1);
        LongBuffer view = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateImageView(device, viewInfo, null, view), "cloud view");
        cloudImageView = view.get(0);

        org.lwjgl.vulkan.VkSamplerCreateInfo samplerInfo =
            org.lwjgl.vulkan.VkSamplerCreateInfo.calloc(stack)
                .magFilter(VK_FILTER_NEAREST)
                .minFilter(VK_FILTER_NEAREST)
                .addressModeU(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .addressModeW(VK_SAMPLER_ADDRESS_MODE_REPEAT)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                .maxAnisotropy(1f)
                .borderColor(VK_BORDER_COLOR_INT_OPAQUE_BLACK);
        LongBuffer sampler = stack.mallocLong(1);
        VulkanContext.checkVk(vkCreateSampler(device, samplerInfo, null, sampler), "cloud sampler");
        cloudSampler = sampler.get(0);
      } finally {
        if (stagingBuffer != VK_NULL_HANDLE) {
          vkDestroyBuffer(device, stagingBuffer, null);
        }
        if (stagingMemory != VK_NULL_HANDLE) {
          vkFreeMemory(device, stagingMemory, null);
        }
      }
    } finally {
      memFree(pixels);
    }
  }

  private void createUniformBuffers() {
    uniformBuffers = new long[MAX_FRAMES_IN_FLIGHT];
    uniformBuffersMemory = new long[MAX_FRAMES_IN_FLIGHT];
    uniformBuffersMapped = new ByteBuffer[MAX_FRAMES_IN_FLIGHT];
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        uniformBuffers[i] =
            createBuffer(device, stack, UNIFORM_BUFFER_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        uniformBuffersMemory[i] =
            allocateBufferMemory(
                vulkan,
                device,
                stack,
                uniformBuffers[i],
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        PointerBuffer mapped = stack.mallocPointer(1);
        vkMapMemory(device, uniformBuffersMemory[i], 0, UNIFORM_BUFFER_SIZE, 0, mapped);
        uniformBuffersMapped[i] = mapped.getByteBuffer(0, UNIFORM_BUFFER_SIZE);
      }
    }
  }

  private void createDescriptorPool() {
    try (MemoryStack stack = stackPush()) {
      // World sets (1 UBO + 2 samplers) + sky sets (1 UBO + 2 samplers).
      org.lwjgl.vulkan.VkDescriptorPoolSize.Buffer poolSizes =
          org.lwjgl.vulkan.VkDescriptorPoolSize.calloc(2, stack);
      poolSizes
          .get(0)
          .type(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
          .descriptorCount(MAX_FRAMES_IN_FLIGHT * 2);
      poolSizes
          .get(1)
          .type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
          .descriptorCount(MAX_FRAMES_IN_FLIGHT * 4);
      poolSizes.position(0);
      org.lwjgl.vulkan.VkDescriptorPoolCreateInfo poolInfo =
          org.lwjgl.vulkan.VkDescriptorPoolCreateInfo.calloc(stack)
              .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
              .maxSets(MAX_FRAMES_IN_FLIGHT * 2)
              .pPoolSizes(poolSizes);
      LongBuffer pool = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateDescriptorPool(vulkan.getDevice(), poolInfo, null, pool), "descriptor pool");
      descriptorPool = pool.get(0);
    }
  }

  private void createDescriptorSets() {
    descriptorSets = new long[MAX_FRAMES_IN_FLIGHT];
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      LongBuffer layouts = stack.mallocLong(MAX_FRAMES_IN_FLIGHT);
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        layouts.put(i, descriptorSetLayout);
      }
      layouts.rewind();
      org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocInfo =
          org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack)
              .descriptorPool(descriptorPool)
              .pSetLayouts(layouts);
      LongBuffer sets = stack.mallocLong(MAX_FRAMES_IN_FLIGHT);
      VulkanContext.checkVk(vkAllocateDescriptorSets(device, allocInfo, sets), "descriptor sets");
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        descriptorSets[i] = sets.get(i);
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufferInfo =
            org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(1, stack);
        bufferInfo.get(0).buffer(uniformBuffers[i]).offset(0).range(UNIFORM_BUFFER_SIZE);
        bufferInfo.position(0);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfo =
            org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
        imageInfo
            .get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(textureImageView)
            .sampler(textureSampler);
        imageInfo.position(0);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer cloudInfo =
            org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
        cloudInfo
            .get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(cloudImageView)
            .sampler(cloudSampler);
        cloudInfo.position(0);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
        writes
            .get(0)
            .dstSet(descriptorSets[i])
            .dstBinding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .pBufferInfo(bufferInfo);
        writes
            .get(1)
            .dstSet(descriptorSets[i])
            .dstBinding(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .pImageInfo(imageInfo);
        writes
            .get(2)
            .dstSet(descriptorSets[i])
            .dstBinding(2)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .pImageInfo(cloudInfo);
        writes.position(0);
        vkUpdateDescriptorSets(device, writes, null);
      }
    }
  }

  private void createSkyUniformBuffers() {
    skyUniformBuffers = new long[MAX_FRAMES_IN_FLIGHT];
    skyUniformBuffersMemory = new long[MAX_FRAMES_IN_FLIGHT];
    skyUniformBuffersMapped = new ByteBuffer[MAX_FRAMES_IN_FLIGHT];
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        skyUniformBuffers[i] =
            createBuffer(
                device, stack, SKY_UNIFORM_BUFFER_SIZE, VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT);
        skyUniformBuffersMemory[i] =
            allocateBufferMemory(
                vulkan,
                device,
                stack,
                skyUniformBuffers[i],
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        PointerBuffer mapped = stack.mallocPointer(1);
        vkMapMemory(device, skyUniformBuffersMemory[i], 0, SKY_UNIFORM_BUFFER_SIZE, 0, mapped);
        skyUniformBuffersMapped[i] = mapped.getByteBuffer(0, SKY_UNIFORM_BUFFER_SIZE);
      }
    }
  }

  private void createSkyDescriptorSets() {
    skyDescriptorSets = new long[MAX_FRAMES_IN_FLIGHT];
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      LongBuffer layouts = stack.mallocLong(MAX_FRAMES_IN_FLIGHT);
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        layouts.put(i, skyDescriptorSetLayout);
      }
      layouts.rewind();
      org.lwjgl.vulkan.VkDescriptorSetAllocateInfo allocInfo =
          org.lwjgl.vulkan.VkDescriptorSetAllocateInfo.calloc(stack)
              .descriptorPool(descriptorPool)
              .pSetLayouts(layouts);
      LongBuffer sets = stack.mallocLong(MAX_FRAMES_IN_FLIGHT);
      VulkanContext.checkVk(
          vkAllocateDescriptorSets(device, allocInfo, sets), "sky descriptor sets");
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        skyDescriptorSets[i] = sets.get(i);
        org.lwjgl.vulkan.VkDescriptorBufferInfo.Buffer bufferInfo =
            org.lwjgl.vulkan.VkDescriptorBufferInfo.calloc(1, stack);
        bufferInfo.get(0).buffer(skyUniformBuffers[i]).offset(0).range(SKY_UNIFORM_BUFFER_SIZE);
        bufferInfo.position(0);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer imageInfo =
            org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(2, stack);
        imageInfo
            .get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(sunImageView)
            .sampler(sunSampler);
        imageInfo
            .get(1)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(cloudImageView)
            .sampler(cloudSampler);
        imageInfo.position(0);
        VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(3, stack);
        writes
            .get(0)
            .dstSet(skyDescriptorSets[i])
            .dstBinding(0)
            .descriptorType(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER)
            .descriptorCount(1)
            .pBufferInfo(bufferInfo);
        writes
            .get(1)
            .dstSet(skyDescriptorSets[i])
            .dstBinding(1)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .pImageInfo(imageInfo);
        org.lwjgl.vulkan.VkDescriptorImageInfo.Buffer cloudInfo =
            org.lwjgl.vulkan.VkDescriptorImageInfo.calloc(1, stack);
        cloudInfo
            .get(0)
            .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
            .imageView(cloudImageView)
            .sampler(cloudSampler);
        cloudInfo.position(0);
        writes
            .get(2)
            .dstSet(skyDescriptorSets[i])
            .dstBinding(2)
            .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
            .descriptorCount(1)
            .pImageInfo(cloudInfo);
        writes.position(0);
        vkUpdateDescriptorSets(device, writes, null);
      }
    }
  }

  private void createCommandPool() {
    try (MemoryStack stack = stackPush()) {
      VkCommandPoolCreateInfo poolInfo =
          VkCommandPoolCreateInfo.calloc(stack)
              .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
              .queueFamilyIndex(vulkan.getGraphicsQueueFamily());
      LongBuffer pool = stack.mallocLong(1);
      VulkanContext.checkVk(
          vkCreateCommandPool(vulkan.getDevice(), poolInfo, null, pool), "command pool");
      commandPool = pool.get(0);
    }
  }

  private void createCommandBuffers() {
    VkDevice device = vulkan.getDevice();
    if (commandPool != VK_NULL_HANDLE) {
      vkResetCommandPool(device, commandPool, 0);
    }
    commandBuffers = new VkCommandBuffer[MAX_FRAMES_IN_FLIGHT];
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo allocInfo =
          VkCommandBufferAllocateInfo.calloc(stack)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(MAX_FRAMES_IN_FLIGHT);
      PointerBuffer buffers = stack.mallocPointer(MAX_FRAMES_IN_FLIGHT);
      VulkanContext.checkVk(
          vkAllocateCommandBuffers(device, allocInfo, buffers), "command buffers");
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        commandBuffers[i] = new VkCommandBuffer(buffers.get(i), device);
      }
    }
  }

  private void createSyncObjects() {
    imageAvailableSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
    renderFinishedSemaphores = new long[MAX_FRAMES_IN_FLIGHT];
    inFlightFences = new long[MAX_FRAMES_IN_FLIGHT];
    VkDevice device = vulkan.getDevice();
    try (MemoryStack stack = stackPush()) {
      org.lwjgl.vulkan.VkSemaphoreCreateInfo semaphoreInfo =
          org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack);
      VkFenceCreateInfo fenceInfo =
          VkFenceCreateInfo.calloc(stack).flags(VK_FENCE_CREATE_SIGNALED_BIT);
      LongBuffer handle = stack.mallocLong(1);
      for (int i = 0; i < MAX_FRAMES_IN_FLIGHT; i++) {
        VulkanContext.checkVk(
            vkCreateSemaphore(device, semaphoreInfo, null, handle), "image semaphore");
        imageAvailableSemaphores[i] = handle.get(0);
        VulkanContext.checkVk(
            vkCreateSemaphore(device, semaphoreInfo, null, handle), "render semaphore");
        renderFinishedSemaphores[i] = handle.get(0);
        VulkanContext.checkVk(vkCreateFence(device, fenceInfo, null, handle), "fence");
        inFlightFences[i] = handle.get(0);
      }
    }
  }

  private void destroySyncObjects() {
    VkDevice device = vulkan.getDevice();
    for (long fence : inFlightFences) {
      vkDestroyFence(device, fence, null);
    }
    for (long sem : imageAvailableSemaphores) {
      vkDestroySemaphore(device, sem, null);
    }
    for (long sem : renderFinishedSemaphores) {
      vkDestroySemaphore(device, sem, null);
    }
    inFlightFences = new long[0];
    imageAvailableSemaphores = new long[0];
    renderFinishedSemaphores = new long[0];
  }

  private void destroyUniformBuffers() {
    VkDevice device = vulkan.getDevice();
    for (int i = 0; i < uniformBuffers.length; i++) {
      if (uniformBuffersMapped[i] != null) {
        vkUnmapMemory(device, uniformBuffersMemory[i]);
      }
      if (uniformBuffers[i] != VK_NULL_HANDLE) {
        vkDestroyBuffer(device, uniformBuffers[i], null);
      }
      if (uniformBuffersMemory[i] != VK_NULL_HANDLE) {
        vkFreeMemory(device, uniformBuffersMemory[i], null);
      }
    }
    uniformBuffers = new long[0];
    uniformBuffersMemory = new long[0];
    uniformBuffersMapped = new ByteBuffer[0];
  }

  private void destroySkyUniformBuffers() {
    VkDevice device = vulkan.getDevice();
    for (int i = 0; i < skyUniformBuffers.length; i++) {
      if (skyUniformBuffersMapped[i] != null) {
        vkUnmapMemory(device, skyUniformBuffersMemory[i]);
      }
      if (skyUniformBuffers[i] != VK_NULL_HANDLE) {
        vkDestroyBuffer(device, skyUniformBuffers[i], null);
      }
      if (skyUniformBuffersMemory[i] != VK_NULL_HANDLE) {
        vkFreeMemory(device, skyUniformBuffersMemory[i], null);
      }
    }
    skyUniformBuffers = new long[0];
    skyUniformBuffersMemory = new long[0];
    skyUniformBuffersMapped = new ByteBuffer[0];
    skyDescriptorSets = new long[0];
  }

  private void destroyTextureResources() {
    VkDevice device = vulkan.getDevice();
    if (textureSampler != VK_NULL_HANDLE) {
      vkDestroySampler(device, textureSampler, null);
      textureSampler = VK_NULL_HANDLE;
    }
    if (textureImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, textureImageView, null);
      textureImageView = VK_NULL_HANDLE;
    }
    if (textureImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, textureImage, null);
      textureImage = VK_NULL_HANDLE;
    }
    if (textureImageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, textureImageMemory, null);
      textureImageMemory = VK_NULL_HANDLE;
    }
  }

  private void destroySunTextureResources() {
    VkDevice device = vulkan.getDevice();
    if (sunSampler != VK_NULL_HANDLE) {
      vkDestroySampler(device, sunSampler, null);
      sunSampler = VK_NULL_HANDLE;
    }
    if (sunImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, sunImageView, null);
      sunImageView = VK_NULL_HANDLE;
    }
    if (sunImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, sunImage, null);
      sunImage = VK_NULL_HANDLE;
    }
    if (sunImageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, sunImageMemory, null);
      sunImageMemory = VK_NULL_HANDLE;
    }
    destroyCloudTextureResources();
  }

  private void destroyCloudTextureResources() {
    VkDevice device = vulkan.getDevice();
    if (cloudSampler != VK_NULL_HANDLE) {
      vkDestroySampler(device, cloudSampler, null);
      cloudSampler = VK_NULL_HANDLE;
    }
    if (cloudImageView != VK_NULL_HANDLE) {
      vkDestroyImageView(device, cloudImageView, null);
      cloudImageView = VK_NULL_HANDLE;
    }
    if (cloudImage != VK_NULL_HANDLE) {
      vkDestroyImage(device, cloudImage, null);
      cloudImage = VK_NULL_HANDLE;
    }
    if (cloudImageMemory != VK_NULL_HANDLE) {
      vkFreeMemory(device, cloudImageMemory, null);
      cloudImageMemory = VK_NULL_HANDLE;
    }
  }

  private void transitionImageLayout(long image, int oldLayout, int newLayout, int aspect) {
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo allocInfo =
          VkCommandBufferAllocateInfo.calloc(stack)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(1);
      PointerBuffer cmdPtr = stack.mallocPointer(1);
      vkAllocateCommandBuffers(vulkan.getDevice(), allocInfo, cmdPtr);
      VkCommandBuffer cmd = new VkCommandBuffer(cmdPtr.get(0), vulkan.getDevice());
      vkBeginCommandBuffer(
          cmd,
          VkCommandBufferBeginInfo.calloc(stack)
              .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT));
      VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
      barrier.get(0).oldLayout(oldLayout).newLayout(newLayout).image(image);
      barrier
          .get(0)
          .subresourceRange()
          .aspectMask(aspect)
          .baseMipLevel(0)
          .levelCount(1)
          .baseArrayLayer(0)
          .layerCount(1);
      vkCmdPipelineBarrier(
          cmd,
          VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
          VK_PIPELINE_STAGE_TRANSFER_BIT,
          0,
          null,
          null,
          barrier);
      vkEndCommandBuffer(cmd);
      vkQueueSubmit(
          vulkan.getGraphicsQueue(),
          VkSubmitInfo.calloc(stack).pCommandBuffers(cmdPtr),
          VK_NULL_HANDLE);
      vulkan.waitIdle();
    }
  }

  private void copyBufferToImage(long buffer, long image, int width, int height) {
    try (MemoryStack stack = stackPush()) {
      VkCommandBufferAllocateInfo allocInfo =
          VkCommandBufferAllocateInfo.calloc(stack)
              .commandPool(commandPool)
              .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
              .commandBufferCount(1);
      PointerBuffer cmdPtr = stack.mallocPointer(1);
      vkAllocateCommandBuffers(vulkan.getDevice(), allocInfo, cmdPtr);
      VkCommandBuffer cmd = new VkCommandBuffer(cmdPtr.get(0), vulkan.getDevice());
      vkBeginCommandBuffer(
          cmd,
          VkCommandBufferBeginInfo.calloc(stack)
              .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT));
      org.lwjgl.vulkan.VkBufferImageCopy.Buffer copyRegion =
          org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
      copyRegion
          .get(0)
          .bufferOffset(0)
          .bufferRowLength(0)
          .bufferImageHeight(0)
          .imageSubresource()
          .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
          .mipLevel(0)
          .baseArrayLayer(0)
          .layerCount(1);
      copyRegion.get(0).imageOffset().set(0, 0, 0);
      copyRegion.get(0).imageExtent().set(width, height, 1);
      org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage(
          cmd, buffer, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, copyRegion);
      vkEndCommandBuffer(cmd);
      vkQueueSubmit(
          vulkan.getGraphicsQueue(),
          VkSubmitInfo.calloc(stack).pCommandBuffers(cmdPtr),
          VK_NULL_HANDLE);
      vulkan.waitIdle();
    }
  }

  private static long createBuffer(VkDevice device, MemoryStack stack, long size, int usage) {
    VkBufferCreateInfo bufferInfo =
        VkBufferCreateInfo.calloc(stack)
            .size(size)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    LongBuffer buffer = stack.mallocLong(1);
    VulkanContext.checkVk(vkCreateBuffer(device, bufferInfo, null, buffer), "buffer");
    return buffer.get(0);
  }

  private static long allocateBufferMemory(
      VulkanContext vulkan, VkDevice device, MemoryStack stack, long buffer, int properties) {
    VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
    vkGetBufferMemoryRequirements(device, buffer, requirements);
    VkMemoryAllocateInfo allocInfo =
        VkMemoryAllocateInfo.calloc(stack)
            .allocationSize(requirements.size())
            .memoryTypeIndex(vulkan.findMemoryType(requirements.memoryTypeBits(), properties));
    LongBuffer memory = stack.mallocLong(1);
    VulkanContext.checkVk(vkAllocateMemory(device, allocInfo, null, memory), "buffer memory");
    vkBindBufferMemory(device, buffer, memory.get(0), 0);
    return memory.get(0);
  }
}
