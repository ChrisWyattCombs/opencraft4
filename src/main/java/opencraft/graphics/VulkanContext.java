package opencraft.graphics;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRAccelerationStructure.VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRBufferDeviceAddress.VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRDeferredHostOperations.VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRRayTracingPipeline.VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRShaderFloatControls.VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSpirv14.VK_KHR_SPIRV_1_4_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_MAKE_VERSION;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_GRAPHICS_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_APPLICATION_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TRUE;
import static org.lwjgl.vulkan.VK10.vkCreateDevice;
import static org.lwjgl.vulkan.VK10.vkCreateInstance;
import static org.lwjgl.vulkan.VK10.vkDestroyDevice;
import static org.lwjgl.vulkan.VK10.vkDestroyInstance;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkEnumerateDeviceExtensionProperties;
import static org.lwjgl.vulkan.VK10.vkEnumeratePhysicalDevices;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceProperties;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;
import static org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES;
import static org.lwjgl.vulkan.VK12.vkGetBufferDeviceAddress;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import opencraft.DiagLog;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferDeviceAddressInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceAccelerationStructureFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceBufferDeviceAddressFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceRayTracingPipelineFeaturesKHR;
import org.lwjgl.vulkan.VkPhysicalDeviceRayTracingPipelinePropertiesKHR;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;

/** Owns the Vulkan instance, window surface, physical/logical device, and presentation queues. */
public final class VulkanContext implements AutoCloseable {

  private final VkInstance instance;
  private final long surface;
  private final VkPhysicalDevice physicalDevice;
  private final VkDevice device;
  private final VkQueue graphicsQueue;
  private final VkQueue presentQueue;
  private final int graphicsQueueFamily;
  private final int presentQueueFamily;
  private final boolean rayTracingSupported;
  private final int shaderGroupHandleSize;
  private final int shaderGroupHandleAlignment;
  private final int shaderGroupBaseAlignment;
  private final int maxRayRecursionDepth;

  private VulkanContext(
      VkInstance instance,
      long surface,
      VkPhysicalDevice physicalDevice,
      VkDevice device,
      VkQueue graphicsQueue,
      VkQueue presentQueue,
      int graphicsQueueFamily,
      int presentQueueFamily,
      boolean rayTracingSupported,
      int shaderGroupHandleSize,
      int shaderGroupHandleAlignment,
      int shaderGroupBaseAlignment,
      int maxRayRecursionDepth) {
    this.instance = instance;
    this.surface = surface;
    this.physicalDevice = physicalDevice;
    this.device = device;
    this.graphicsQueue = graphicsQueue;
    this.presentQueue = presentQueue;
    this.graphicsQueueFamily = graphicsQueueFamily;
    this.presentQueueFamily = presentQueueFamily;
    this.rayTracingSupported = rayTracingSupported;
    this.shaderGroupHandleSize = shaderGroupHandleSize;
    this.shaderGroupHandleAlignment = shaderGroupHandleAlignment;
    this.shaderGroupBaseAlignment = shaderGroupBaseAlignment;
    this.maxRayRecursionDepth = maxRayRecursionDepth;
  }

  /**
   * Creates a Vulkan context bound to the given window.
   *
   * @param window GLFW window that will own the Vulkan surface
   * @return initialized Vulkan context
   */
  public static VulkanContext create(GameWindow window) {
    VkInstance instance = createInstance();
    long surface = createSurface(instance, window.getHandle());
    VkPhysicalDevice physicalDevice = pickPhysicalDevice(instance, surface);
    QueueFamilyIndices indices = findQueueFamilies(physicalDevice, surface);
    boolean wantRt = deviceSupportsRayTracing(physicalDevice);

    try (MemoryStack stack = stackPush()) {
      Set<Integer> uniqueFamilies = new HashSet<>();
      uniqueFamilies.add(indices.graphicsFamily);
      uniqueFamilies.add(indices.presentFamily);

      VkDeviceQueueCreateInfo.Buffer queueCreateInfos =
          VkDeviceQueueCreateInfo.calloc(uniqueFamilies.size(), stack);
      float[] queuePriority = {1.0f};
      int i = 0;
      for (int family : uniqueFamilies) {
        queueCreateInfos
            .get(i++)
            .sType(VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO)
            .queueFamilyIndex(family)
            .pQueuePriorities(stack.floats(queuePriority));
      }

      List<ByteBuffer> extNames = new ArrayList<>();
      extNames.add(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
      if (wantRt) {
        // Unique RT-related extensions (deferred host ops listed once).
        Set<String> unique = new HashSet<>();
        unique.add(VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME);
        unique.add(VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME);
        unique.add(VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME);
        unique.add(VK_KHR_BUFFER_DEVICE_ADDRESS_EXTENSION_NAME);
        unique.add(VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
        unique.add(VK_KHR_SPIRV_1_4_EXTENSION_NAME);
        unique.add(VK_KHR_SHADER_FLOAT_CONTROLS_EXTENSION_NAME);
        for (String name : unique) {
          if (hasExtension(physicalDevice, name)) {
            extNames.add(stack.UTF8(name));
          }
        }
      }
      PointerBuffer extensions = stack.pointers(extNames.toArray(new ByteBuffer[0]));

      VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);

      VkDeviceCreateInfo createInfo =
          VkDeviceCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
              .pQueueCreateInfos(queueCreateInfos)
              .pEnabledFeatures(features)
              .ppEnabledExtensionNames(extensions);

      boolean rtEnabled = false;
      int handleSize = 0;
      int handleAlign = 0;
      int baseAlign = 0;
      int maxRecursion = 1;

      if (wantRt) {
        VkPhysicalDeviceBufferDeviceAddressFeatures bda =
            VkPhysicalDeviceBufferDeviceAddressFeatures.calloc(stack)
                .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES)
                .bufferDeviceAddress(true);

        VkPhysicalDeviceAccelerationStructureFeaturesKHR asFeatures =
            VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
                .sType(
                    org.lwjgl.vulkan.KHRAccelerationStructure
                        .VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
                .accelerationStructure(true);

        VkPhysicalDeviceRayTracingPipelineFeaturesKHR rtFeatures =
            VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack)
                .sType(
                    org.lwjgl.vulkan.KHRRayTracingPipeline
                        .VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
                .rayTracingPipeline(true);

        asFeatures.pNext(bda.address());
        rtFeatures.pNext(asFeatures.address());
        createInfo.pNext(rtFeatures.address());

        VkPhysicalDeviceRayTracingPipelinePropertiesKHR rtProps =
            VkPhysicalDeviceRayTracingPipelinePropertiesKHR.calloc(stack)
                .sType(
                    org.lwjgl.vulkan.KHRRayTracingPipeline
                        .VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_PROPERTIES_KHR);
        org.lwjgl.vulkan.VkPhysicalDeviceProperties2 props2 =
            org.lwjgl.vulkan.VkPhysicalDeviceProperties2.calloc(stack)
                .sType(org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PROPERTIES_2)
                .pNext(rtProps.address());
        org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2(physicalDevice, props2);
        handleSize = rtProps.shaderGroupHandleSize();
        handleAlign = rtProps.shaderGroupHandleAlignment();
        baseAlign = rtProps.shaderGroupBaseAlignment();
        maxRecursion = Math.max(1, rtProps.maxRayRecursionDepth());
        rtEnabled = true;
      }

      PointerBuffer devicePtr = stack.mallocPointer(1);
      checkVk(vkCreateDevice(physicalDevice, createInfo, null, devicePtr), "create logical device");
      VkDevice device = new VkDevice(devicePtr.get(0), physicalDevice, createInfo);

      PointerBuffer queuePtr = stack.mallocPointer(1);
      vkGetDeviceQueue(device, indices.graphicsFamily, 0, queuePtr);
      VkQueue graphicsQueue = new VkQueue(queuePtr.get(0), device);
      vkGetDeviceQueue(device, indices.presentFamily, 0, queuePtr);
      VkQueue presentQueue = new VkQueue(queuePtr.get(0), device);

      VkPhysicalDeviceProperties props = VkPhysicalDeviceProperties.malloc(stack);
      vkGetPhysicalDeviceProperties(physicalDevice, props);
      String gpuName = props.deviceNameString();
      if (rtEnabled) {
        System.out.println("[Opencraft] Vulkan RT enabled on " + gpuName);
      } else {
        System.out.println("[Opencraft] Vulkan RT unavailable on " + gpuName + " (raster only)");
      }

      return new VulkanContext(
          instance,
          surface,
          physicalDevice,
          device,
          graphicsQueue,
          presentQueue,
          indices.graphicsFamily,
          indices.presentFamily,
          rtEnabled,
          handleSize,
          handleAlign,
          baseAlign,
          maxRecursion);
    }
  }

  /**
   * @return whether hardware ray tracing extensions were enabled on this device
   */
  public boolean isRayTracingSupported() {
    return rayTracingSupported;
  }

  public int getShaderGroupHandleSize() {
    return shaderGroupHandleSize;
  }

  public int getShaderGroupHandleAlignment() {
    return shaderGroupHandleAlignment;
  }

  public int getShaderGroupBaseAlignment() {
    return shaderGroupBaseAlignment;
  }

  public int getMaxRayRecursionDepth() {
    return maxRayRecursionDepth;
  }

  /**
   * Returns the device address of a buffer (requires buffer device address).
   *
   * @param buffer buffer handle
   * @return 64-bit device address
   */
  public long getBufferDeviceAddress(long buffer) {
    try (MemoryStack stack = stackPush()) {
      VkBufferDeviceAddressInfo info =
          VkBufferDeviceAddressInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_BUFFER_DEVICE_ADDRESS_INFO)
              .buffer(buffer);
      return vkGetBufferDeviceAddress(device, info);
    }
  }

  public VkInstance getInstance() {
    return instance;
  }

  public long getSurface() {
    return surface;
  }

  public VkPhysicalDevice getPhysicalDevice() {
    return physicalDevice;
  }

  public VkDevice getDevice() {
    return device;
  }

  public VkQueue getGraphicsQueue() {
    return graphicsQueue;
  }

  public VkQueue getPresentQueue() {
    return presentQueue;
  }

  public int getGraphicsQueueFamily() {
    return graphicsQueueFamily;
  }

  public int getPresentQueueFamily() {
    return presentQueueFamily;
  }

  public void waitIdle() {
    vkDeviceWaitIdle(device);
  }

  public int findMemoryType(int typeFilter, int properties) {
    try (MemoryStack stack = stackPush()) {
      VkPhysicalDeviceMemoryProperties memProperties =
          VkPhysicalDeviceMemoryProperties.malloc(stack);
      vkGetPhysicalDeviceMemoryProperties(physicalDevice, memProperties);
      for (int i = 0; i < memProperties.memoryTypeCount(); i++) {
        if ((typeFilter & (1 << i)) != 0
            && (memProperties.memoryTypes(i).propertyFlags() & properties) == properties) {
          return i;
        }
      }
    }
    throw new IllegalStateException("Failed to find suitable memory type");
  }

  @Override
  public void close() {
    if (device != null) {
      vkDeviceWaitIdle(device);
      vkDestroyDevice(device, null);
    }
    if (instance != null) {
      if (surface != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(instance, surface, null);
      }
      vkDestroyInstance(instance, null);
    }
  }

  private static VkInstance createInstance() {
    try (MemoryStack stack = stackPush()) {
      VkApplicationInfo appInfo =
          VkApplicationInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_APPLICATION_INFO)
              .pApplicationName(stack.UTF8("Opencraft4"))
              .applicationVersion(VK_MAKE_VERSION(0, 1, 0))
              .pEngineName(stack.UTF8("Opencraft"))
              .engineVersion(VK_MAKE_VERSION(0, 1, 0))
              .apiVersion(VK_API_VERSION_1_2);

      PointerBuffer glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
      if (glfwExtensions == null) {
        throw new IllegalStateException("Failed to find required GLFW Vulkan extensions");
      }

      VkInstanceCreateInfo createInfo =
          VkInstanceCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO)
              .pApplicationInfo(appInfo)
              .ppEnabledExtensionNames(glfwExtensions);

      PointerBuffer instancePtr = stack.mallocPointer(1);
      checkVk(vkCreateInstance(createInfo, null, instancePtr), "create Vulkan instance");
      return new VkInstance(instancePtr.get(0), createInfo);
    }
  }

  private static long createSurface(VkInstance instance, long windowHandle) {
    try (MemoryStack stack = stackPush()) {
      LongBuffer surfacePtr = stack.mallocLong(1);
      checkVk(
          GLFWVulkan.glfwCreateWindowSurface(instance, windowHandle, null, surfacePtr),
          "create window surface");
      return surfacePtr.get(0);
    }
  }

  private static VkPhysicalDevice pickPhysicalDevice(VkInstance instance, long surface) {
    try (MemoryStack stack = stackPush()) {
      IntBuffer deviceCount = stack.ints(0);
      vkEnumeratePhysicalDevices(instance, deviceCount, null);
      if (deviceCount.get(0) == 0) {
        throw new IllegalStateException("No Vulkan-compatible GPU found");
      }

      PointerBuffer devices = stack.mallocPointer(deviceCount.get(0));
      vkEnumeratePhysicalDevices(instance, deviceCount, devices);

      // Prefer a device that supports RT when available.
      VkPhysicalDevice fallback = null;
      for (int i = 0; i < devices.capacity(); i++) {
        VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
        if (!isDeviceSuitable(candidate, surface)) {
          continue;
        }
        if (deviceSupportsRayTracing(candidate)) {
          return candidate;
        }
        if (fallback == null) {
          fallback = candidate;
        }
      }
      if (fallback != null) {
        return fallback;
      }
    }
    throw new IllegalStateException("Failed to find a suitable Vulkan GPU");
  }

  private static boolean deviceSupportsRayTracing(VkPhysicalDevice device) {
    for (String ext :
        new String[] {
          VK_KHR_ACCELERATION_STRUCTURE_EXTENSION_NAME,
          VK_KHR_RAY_TRACING_PIPELINE_EXTENSION_NAME,
          VK_KHR_DEFERRED_HOST_OPERATIONS_EXTENSION_NAME
        }) {
      if (!hasExtension(device, ext)) {
        return false;
      }
    }
    try (MemoryStack stack = stackPush()) {
      VkPhysicalDeviceBufferDeviceAddressFeatures bda =
          VkPhysicalDeviceBufferDeviceAddressFeatures.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_BUFFER_DEVICE_ADDRESS_FEATURES);
      VkPhysicalDeviceAccelerationStructureFeaturesKHR as =
          VkPhysicalDeviceAccelerationStructureFeaturesKHR.calloc(stack)
              .sType(
                  org.lwjgl.vulkan.KHRAccelerationStructure
                      .VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_ACCELERATION_STRUCTURE_FEATURES_KHR)
              .pNext(bda.address());
      VkPhysicalDeviceRayTracingPipelineFeaturesKHR rt =
          VkPhysicalDeviceRayTracingPipelineFeaturesKHR.calloc(stack)
              .sType(
                  org.lwjgl.vulkan.KHRRayTracingPipeline
                      .VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_RAY_TRACING_PIPELINE_FEATURES_KHR)
              .pNext(as.address());
      VkPhysicalDeviceFeatures2 features2 =
          VkPhysicalDeviceFeatures2.calloc(stack)
              .sType(org.lwjgl.vulkan.VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2)
              .pNext(rt.address());
      vkGetPhysicalDeviceFeatures2(device, features2);
      return rt.rayTracingPipeline() && as.accelerationStructure() && bda.bufferDeviceAddress();
    }
  }

  private static boolean hasExtension(VkPhysicalDevice device, String name) {
    try (MemoryStack stack = stackPush()) {
      IntBuffer extensionCount = stack.ints(0);
      vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);
      VkExtensionProperties.Buffer available =
          VkExtensionProperties.malloc(extensionCount.get(0), stack);
      vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, available);
      for (int i = 0; i < available.capacity(); i++) {
        if (name.equals(available.get(i).extensionNameString())) {
          return true;
        }
      }
      return false;
    }
  }

  private static boolean isDeviceSuitable(VkPhysicalDevice device, long surface) {
    QueueFamilyIndices indices = findQueueFamilies(device, surface);
    if (!indices.isComplete() || !checkDeviceExtensionSupport(device)) {
      return false;
    }
    try (MemoryStack stack = stackPush()) {
      VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
      vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device, surface, capabilities);
      IntBuffer formatCount = stack.ints(0);
      vkGetPhysicalDeviceSurfaceFormatsKHR(device, surface, formatCount, null);
      IntBuffer presentModeCount = stack.ints(0);
      vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, presentModeCount, null);
      return formatCount.get(0) > 0 && presentModeCount.get(0) > 0;
    }
  }

  private static boolean checkDeviceExtensionSupport(VkPhysicalDevice device) {
    return hasExtension(device, VK_KHR_SWAPCHAIN_EXTENSION_NAME);
  }

  static QueueFamilyIndices findQueueFamilies(VkPhysicalDevice device, long surface) {
    QueueFamilyIndices indices = new QueueFamilyIndices();
    try (MemoryStack stack = stackPush()) {
      IntBuffer queueFamilyCount = stack.ints(0);
      vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, null);
      VkQueueFamilyProperties.Buffer queueFamilies =
          VkQueueFamilyProperties.malloc(queueFamilyCount.get(0), stack);
      vkGetPhysicalDeviceQueueFamilyProperties(device, queueFamilyCount, queueFamilies);

      IntBuffer presentSupport = stack.ints(0);
      for (int i = 0; i < queueFamilies.capacity(); i++) {
        if ((queueFamilies.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
          indices.graphicsFamily = i;
        }
        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, presentSupport);
        if (presentSupport.get(0) == VK_TRUE) {
          indices.presentFamily = i;
        }
        if (indices.isComplete()) {
          break;
        }
      }
    }
    return indices;
  }

  /**
   * Checks a Vulkan result code and throws if it is not {@code VK_SUCCESS}.
   *
   * <p>Failed results (including {@code VK_ERROR_DEVICE_LOST}) are written to {@link DiagLog}
   * before the exception is thrown so breadcrumbs survive a subsequent native abort.
   *
   * @param result Vulkan {@code VkResult}
   * @param action short description of the failing call
   * @throws IllegalStateException if {@code result} is not success
   */
  public static void checkVk(int result, String action) {
    if (result != VK_SUCCESS) {
      String detail = DiagLog.describeVk(result);
      DiagLog.logVk(action, result);
      if (result == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
        DiagLog.log("DEVICE_LOST during: " + action + " — GPU reset or driver abort imminent");
      }
      throw new IllegalStateException("Failed to " + action + ": " + detail + " (" + result + ")");
    }
  }

  /** Queue-family indices selected for graphics and present. */
  static final class QueueFamilyIndices {
    int graphicsFamily = -1;
    int presentFamily = -1;

    /**
     * Returns whether both graphics and present families were found.
     *
     * @return {@code true} if complete
     */
    boolean isComplete() {
      return graphicsFamily >= 0 && presentFamily >= 0;
    }
  }
}
