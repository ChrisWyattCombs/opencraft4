package opencraft.graphics.render;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

import java.nio.LongBuffer;
import opencraft.graphics.VulkanContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

/** GPU vertex/index buffers for one chunk — opaque and translucent ranges. */
public final class GpuChunkMesh {

  private long opaqueVertexBuffer;
  private long opaqueVertexMemory;
  private long opaqueIndexBuffer;
  private long opaqueIndexMemory;
  private int opaqueIndexCount;

  private long translucentVertexBuffer;
  private long translucentVertexMemory;
  private long translucentIndexBuffer;
  private long translucentIndexMemory;
  private int translucentIndexCount;

  private GpuChunkMesh() {}

  /**
   * Uploads mesh data to host-visible GPU buffers.
   *
   * @param vulkan Vulkan context
   * @param commandPool unused; retained for call-site compatibility
   * @param data CPU mesh from {@link ChunkMesher}
   * @return GPU mesh ready for drawing
   */
  public static GpuChunkMesh upload(
      VulkanContext vulkan, long commandPool, ChunkMesher.MeshData data) {
    GpuChunkMesh mesh = new GpuChunkMesh();
    mesh.opaqueIndexCount = data.opaqueIndices().length;
    mesh.translucentIndexCount = data.translucentIndices().length;
    VkDevice device = vulkan.getDevice();
    int hostProps = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

    try (MemoryStack stack = stackPush()) {
      if (mesh.opaqueIndexCount > 0) {
        uploadRange(
            vulkan,
            device,
            stack,
            hostProps,
            data.opaqueVertices(),
            data.opaqueIndices(),
            mesh,
            true);
      }
      if (mesh.translucentIndexCount > 0) {
        uploadRange(
            vulkan,
            device,
            stack,
            hostProps,
            data.translucentVertices(),
            data.translucentIndices(),
            mesh,
            false);
      }
    }
    return mesh;
  }

  private static void uploadRange(
      VulkanContext vulkan,
      VkDevice device,
      MemoryStack stack,
      int hostProps,
      float[] vertices,
      int[] indices,
      GpuChunkMesh mesh,
      boolean opaque) {
    long vertexBytes = (long) vertices.length * Float.BYTES;
    long indexBytes = (long) indices.length * Integer.BYTES;
    long vbo = createBuffer(device, stack, vertexBytes, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
    long vmem = allocateMemory(vulkan, device, stack, vbo, hostProps);
    PointerBuffer mapped = stack.mallocPointer(1);
    vkMapMemory(device, vmem, 0, vertexBytes, 0, mapped);
    mapped.getByteBuffer(0, (int) vertexBytes).asFloatBuffer().put(vertices);
    vkUnmapMemory(device, vmem);

    long ibo = createBuffer(device, stack, indexBytes, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
    long imem = allocateMemory(vulkan, device, stack, ibo, hostProps);
    vkMapMemory(device, imem, 0, indexBytes, 0, mapped);
    mapped.getByteBuffer(0, (int) indexBytes).asIntBuffer().put(indices);
    vkUnmapMemory(device, imem);

    if (opaque) {
      mesh.opaqueVertexBuffer = vbo;
      mesh.opaqueVertexMemory = vmem;
      mesh.opaqueIndexBuffer = ibo;
      mesh.opaqueIndexMemory = imem;
    } else {
      mesh.translucentVertexBuffer = vbo;
      mesh.translucentVertexMemory = vmem;
      mesh.translucentIndexBuffer = ibo;
      mesh.translucentIndexMemory = imem;
    }
  }

  /**
   * Draws solid geometry (depth write on).
   *
   * @param cmd command buffer
   * @param device logical device
   */
  public void drawOpaque(VkCommandBuffer cmd, VkDevice device) {
    drawRange(cmd, opaqueVertexBuffer, opaqueIndexBuffer, opaqueIndexCount);
  }

  /**
   * Draws liquid geometry (depth write off).
   *
   * @param cmd command buffer
   * @param device logical device
   */
  public void drawTranslucent(VkCommandBuffer cmd, VkDevice device) {
    drawRange(cmd, translucentVertexBuffer, translucentIndexBuffer, translucentIndexCount);
  }

  private static void drawRange(VkCommandBuffer cmd, long vbo, long ibo, int indexCount) {
    if (indexCount == 0 || vbo == VK_NULL_HANDLE) {
      return;
    }
    try (MemoryStack stack = stackPush()) {
      vkCmdBindVertexBuffers(cmd, 0, stack.longs(vbo), stack.longs(0L));
      vkCmdBindIndexBuffer(cmd, ibo, 0, VK_INDEX_TYPE_UINT32);
      vkCmdDrawIndexed(cmd, indexCount, 1, 0, 0, 0);
    }
  }

  /**
   * Releases GPU buffers and memory.
   *
   * @param vulkan Vulkan context
   */
  public void free(VulkanContext vulkan) {
    VkDevice device = vulkan.getDevice();
    freeBuffer(device, opaqueVertexBuffer, opaqueVertexMemory);
    freeBuffer(device, opaqueIndexBuffer, opaqueIndexMemory);
    freeBuffer(device, translucentVertexBuffer, translucentVertexMemory);
    freeBuffer(device, translucentIndexBuffer, translucentIndexMemory);
    opaqueVertexBuffer =
        opaqueIndexBuffer = translucentVertexBuffer = translucentIndexBuffer = VK_NULL_HANDLE;
    opaqueVertexMemory =
        opaqueIndexMemory = translucentVertexMemory = translucentIndexMemory = VK_NULL_HANDLE;
    opaqueIndexCount = translucentIndexCount = 0;
  }

  private static void freeBuffer(VkDevice device, long buffer, long memory) {
    if (buffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, buffer, null);
    }
    if (memory != VK_NULL_HANDLE) {
      vkFreeMemory(device, memory, null);
    }
  }

  /**
   * Returns whether this mesh has drawable indices.
   *
   * @return {@code true} if empty
   */
  public boolean isEmpty() {
    return opaqueIndexCount == 0 && translucentIndexCount == 0;
  }

  /**
   * @return whether this mesh has translucent water faces
   */
  public boolean hasTranslucent() {
    return translucentIndexCount > 0;
  }

  private static long createBuffer(VkDevice device, MemoryStack stack, long size, int usage) {
    VkBufferCreateInfo bufferInfo =
        VkBufferCreateInfo.calloc(stack)
            .size(size)
            .usage(usage)
            .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
    LongBuffer buffer = stack.mallocLong(1);
    VulkanContext.checkVk(vkCreateBuffer(device, bufferInfo, null, buffer), "create buffer");
    return buffer.get(0);
  }

  private static long allocateMemory(
      VulkanContext vulkan, VkDevice device, MemoryStack stack, long buffer, int properties) {
    VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
    vkGetBufferMemoryRequirements(device, buffer, requirements);
    VkMemoryAllocateInfo allocInfo =
        VkMemoryAllocateInfo.calloc(stack)
            .allocationSize(requirements.size())
            .memoryTypeIndex(vulkan.findMemoryType(requirements.memoryTypeBits(), properties));
    LongBuffer memory = stack.mallocLong(1);
    VulkanContext.checkVk(vkAllocateMemory(device, allocInfo, null, memory), "allocate memory");
    vkBindBufferMemory(device, buffer, memory.get(0), 0);
    return memory.get(0);
  }
}
