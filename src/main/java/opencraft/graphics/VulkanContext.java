package opencraft.graphics;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.KHRSurface.vkDestroySurfaceKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfacePresentModesKHR;
import static org.lwjgl.vulkan.KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0;
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
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceQueueFamilyProperties;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Set;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
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

  private VulkanContext(
      VkInstance instance,
      long surface,
      VkPhysicalDevice physicalDevice,
      VkDevice device,
      VkQueue graphicsQueue,
      VkQueue presentQueue,
      int graphicsQueueFamily,
      int presentQueueFamily) {
    this.instance = instance;
    this.surface = surface;
    this.physicalDevice = physicalDevice;
    this.device = device;
    this.graphicsQueue = graphicsQueue;
    this.presentQueue = presentQueue;
    this.graphicsQueueFamily = graphicsQueueFamily;
    this.presentQueueFamily = presentQueueFamily;
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

      VkPhysicalDeviceFeatures features = VkPhysicalDeviceFeatures.calloc(stack);
      PointerBuffer extensions = stack.pointers(stack.UTF8(VK_KHR_SWAPCHAIN_EXTENSION_NAME));
      VkDeviceCreateInfo createInfo =
          VkDeviceCreateInfo.calloc(stack)
              .sType(VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO)
              .pQueueCreateInfos(queueCreateInfos)
              .pEnabledFeatures(features)
              .ppEnabledExtensionNames(extensions);

      PointerBuffer devicePtr = stack.mallocPointer(1);
      checkVk(vkCreateDevice(physicalDevice, createInfo, null, devicePtr), "create logical device");
      VkDevice device = new VkDevice(devicePtr.get(0), physicalDevice, createInfo);

      PointerBuffer queuePtr = stack.mallocPointer(1);
      vkGetDeviceQueue(device, indices.graphicsFamily, 0, queuePtr);
      VkQueue graphicsQueue = new VkQueue(queuePtr.get(0), device);
      vkGetDeviceQueue(device, indices.presentFamily, 0, queuePtr);
      VkQueue presentQueue = new VkQueue(queuePtr.get(0), device);

      return new VulkanContext(
          instance,
          surface,
          physicalDevice,
          device,
          graphicsQueue,
          presentQueue,
          indices.graphicsFamily,
          indices.presentFamily);
    }
  }

  /**
   * Returns the Vulkan instance.
   *
   * @return instance
   */
  public VkInstance getInstance() {
    return instance;
  }

  /**
   * Returns the window surface handle.
   *
   * @return surface handle
   */
  public long getSurface() {
    return surface;
  }

  /**
   * Returns the selected physical device.
   *
   * @return physical device
   */
  public VkPhysicalDevice getPhysicalDevice() {
    return physicalDevice;
  }

  /**
   * Returns the logical device.
   *
   * @return logical device
   */
  public VkDevice getDevice() {
    return device;
  }

  /**
   * Returns the graphics queue.
   *
   * @return graphics queue
   */
  public VkQueue getGraphicsQueue() {
    return graphicsQueue;
  }

  /**
   * Returns the present queue.
   *
   * @return present queue
   */
  public VkQueue getPresentQueue() {
    return presentQueue;
  }

  /**
   * Returns the graphics queue family index.
   *
   * @return queue family index
   */
  public int getGraphicsQueueFamily() {
    return graphicsQueueFamily;
  }

  /**
   * Returns the present queue family index.
   *
   * @return queue family index
   */
  public int getPresentQueueFamily() {
    return presentQueueFamily;
  }

  /** Blocks until the logical device is idle. */
  public void waitIdle() {
    vkDeviceWaitIdle(device);
  }

  /**
   * Finds a memory type index matching the given filter and property flags.
   *
   * @param typeFilter bitfield of allowed memory types
   * @param properties required {@code VkMemoryPropertyFlagBits}
   * @return matching memory type index
   */
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

  /** Releases the logical device, surface, and instance. */
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
              .apiVersion(VK_API_VERSION_1_0);

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

      for (int i = 0; i < devices.capacity(); i++) {
        VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), instance);
        if (isDeviceSuitable(candidate, surface)) {
          return candidate;
        }
      }
    }
    throw new IllegalStateException("Failed to find a suitable Vulkan GPU");
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
    try (MemoryStack stack = stackPush()) {
      IntBuffer extensionCount = stack.ints(0);
      vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, null);
      VkExtensionProperties.Buffer available =
          VkExtensionProperties.malloc(extensionCount.get(0), stack);
      vkEnumerateDeviceExtensionProperties(device, (ByteBuffer) null, extensionCount, available);

      Set<String> required = new HashSet<>();
      required.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
      for (int i = 0; i < available.capacity(); i++) {
        required.remove(available.get(i).extensionNameString());
      }
      return required.isEmpty();
    }
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
   * Throws if a Vulkan call failed.
   *
   * @param result Vulkan result code
   * @param action description used in the error message
   */
  public static void checkVk(int result, String action) {
    if (result != VK_SUCCESS) {
      throw new IllegalStateException("Failed to " + action + ": " + result);
    }
  }

  static final class QueueFamilyIndices {
    int graphicsFamily = -1;
    int presentFamily = -1;

    boolean isComplete() {
      return graphicsFamily >= 0 && presentFamily >= 0;
    }
  }
}
