package opencraft.graphics.render;

import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_length;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_result_release;
import static org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.lwjgl.system.MemoryUtil;

/** Compiles GLSL shader sources from the classpath into SPIR-V using Shaderc. */
public final class ShaderCompiler {

  private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

  private ShaderCompiler() {}

  /** Precompiles the world shaders so the first play session does not hitch. */
  public static void warmup() {
    CACHE.clear();
    compileGlsl("shaders/world.vert", shaderc_vertex_shader);
    compileGlsl("shaders/world.frag", shaderc_fragment_shader);
  }

  /**
   * Compiles a classpath GLSL resource to SPIR-V bytes.
   *
   * @param resourcePath classpath path such as {@code shaders/world.vert}
   * @param kind {@link org.lwjgl.util.shaderc.Shaderc#shaderc_vertex_shader} or fragment
   * @return heap {@link ByteBuffer} containing SPIR-V (caller must {@link MemoryUtil#memFree})
   */
  public static ByteBuffer compileGlsl(String resourcePath, int kind) {
    byte[] cached = CACHE.computeIfAbsent(resourcePath, path -> compileToBytes(path, kind));
    ByteBuffer copy = MemoryUtil.memAlloc(cached.length);
    copy.put(cached).flip();
    return copy;
  }

  private static byte[] compileToBytes(String resourcePath, int kind) {
    String source = readResource(resourcePath);
    long compiler = shaderc_compiler_initialize();
    if (compiler == NULL) {
      throw new IllegalStateException("Failed to initialize Shaderc compiler");
    }
    long result = shaderc_compile_into_spv(compiler, source, kind, resourcePath, "main", NULL);
    try {
      if (shaderc_result_get_compilation_status(result) != shaderc_compilation_status_success) {
        throw new IllegalStateException(
            "Shader compile failed for "
                + resourcePath
                + ": "
                + shaderc_result_get_error_message(result));
      }
      long size = shaderc_result_get_length(result);
      ByteBuffer spirv = shaderc_result_get_bytes(result);
      byte[] bytes = new byte[(int) size];
      spirv.get(bytes);
      return bytes;
    } finally {
      shaderc_result_release(result);
      shaderc_compiler_release(compiler);
    }
  }

  private static String readResource(String resourcePath) {
    try (InputStream in = ShaderCompiler.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Missing shader resource: " + resourcePath);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read shader " + resourcePath, e);
    }
  }
}
