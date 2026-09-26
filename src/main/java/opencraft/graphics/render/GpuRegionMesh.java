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
import java.util.Collection;
import opencraft.graphics.VulkanContext;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

/**
 * Merged GPU mesh for a block of chunks (one bind + one draw per pass instead of per-chunk draws).
 */
public final class GpuRegionMesh {

  private static final int FLOATS_PER_VERT = 8;

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

  private GpuRegionMesh() {}

  /**
   * Concatenates chunk meshes into one opaque and one translucent draw range.
   *
   * @param vulkan Vulkan context
   * @param parts per-chunk mesh data
   * @return uploaded region mesh (may be empty)
   */
  public static GpuRegionMesh upload(VulkanContext vulkan, Collection<ChunkMesher.MeshData> parts) {
    int oVertFloats = 0;
    int oIndCount = 0;
    int tVertFloats = 0;
    int tIndCount = 0;
    for (ChunkMesher.MeshData p : parts) {
      if (p == null || p.isEmpty()) {
        continue;
      }
      oVertFloats += p.opaqueVertices().length;
      oIndCount += p.opaqueIndices().length;
      tVertFloats += p.translucentVertices().length;
      tIndCount += p.translucentIndices().length;
    }

    float[] oVerts = new float[oVertFloats];
    int[] oInds = new int[oIndCount];
    float[] tVerts = new float[tVertFloats];
    int[] tInds = new int[tIndCount];
    int oV = 0;
    int oI = 0;
    int tV = 0;
    int tI = 0;
    int oBase = 0;
    int tBase = 0;
    for (ChunkMesher.MeshData p : parts) {
      if (p == null || p.isEmpty()) {
        continue;
      }
      float[] ov = p.opaqueVertices();
      int[] oi = p.opaqueIndices();
      System.arraycopy(ov, 0, oVerts, oV, ov.length);
      for (int idx : oi) {
        oInds[oI++] = idx + oBase;
      }
      oV += ov.length;
      oBase += ov.length / FLOATS_PER_VERT;

      float[] tv = p.translucentVertices();
      int[] ti = p.translucentIndices();
      System.arraycopy(tv, 0, tVerts, tV, tv.length);
      for (int idx : ti) {
        tInds[tI++] = idx + tBase;
      }
      tV += tv.length;
      tBase += tv.length / FLOATS_PER_VERT;
    }

    GpuRegionMesh mesh = new GpuRegionMesh();
    mesh.opaqueIndexCount = oI;
    mesh.translucentIndexCount = tI;
    VkDevice device = vulkan.getDevice();
    int hostProps = VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
    try (MemoryStack stack = stackPush()) {
      if (oI > 0) {
        uploadRange(vulkan, device, stack, hostProps, oVerts, oInds, mesh, true);
      }
      if (tI > 0) {
        uploadRange(vulkan, device, stack, hostProps, tVerts, tInds, mesh, false);
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
      GpuRegionMesh mesh,
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

  /** Draws merged opaque geometry (one draw call). */
  public void drawOpaque(VkCommandBuffer cmd) {
    drawRange(cmd, opaqueVertexBuffer, opaqueIndexBuffer, opaqueIndexCount);
  }

  /** Draws merged translucent geometry (one draw call). */
  public void drawTranslucent(VkCommandBuffer cmd) {
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

  /** Releases GPU buffers. */
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

  public boolean isEmpty() {
    return opaqueIndexCount == 0 && translucentIndexCount == 0;
  }

  public boolean hasTranslucent() {
    return translucentIndexCount > 0;
  }

  private static void freeBuffer(VkDevice device, long buffer, long memory) {
    if (buffer != VK_NULL_HANDLE) {
      vkDestroyBuffer(device, buffer, null);
    }
    if (memory != VK_NULL_HANDLE) {
      vkFreeMemory(device, memory, null);
    }
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
