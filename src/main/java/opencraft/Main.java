package opencraft;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import opencraft.game.GameController;
import opencraft.graphics.Display;
import org.lwjgl.system.Configuration;

/** Application entry point and main render/input loop for Opencraft4. */
public final class Main {

  /**
   * Starts the game window, initializes Vulkan and Ultralight UI, then runs until the window
   * closes.
   *
   * @param args command-line arguments (unused)
   * @throws Exception if window, Vulkan, or Ultralight setup fails
   */
  public static void main(String[] args) throws Exception {
    Configuration.STACK_SIZE.set(1024 * 1024);
    Path projectRoot = Path.of("").toAbsolutePath();
    Path crashLog = projectRoot.resolve("run").resolve("crash.log");
    Thread.setDefaultUncaughtExceptionHandler(
        (thread, error) -> {
          error.printStackTrace();
          try {
            Files.createDirectories(crashLog.getParent());
            try (PrintWriter out =
                new PrintWriter(
                    Files.newBufferedWriter(
                        crashLog,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING))) {
              out.println("Uncaught on " + thread.getName());
              error.printStackTrace(out);
            }
          } catch (Exception ignored) {
            // Best-effort crash log only.
          }
        });

    try (Display display = new Display()) {
      display.createWindow("Opencraft4");
      display.initVulkan();

      try (GameController controller = new GameController(display, projectRoot)) {
        controller.init();
        long lastFrame = System.nanoTime();
        while (!display.shouldClose()) {
          display.pollEvents();
          float dt = (System.nanoTime() - lastFrame) / 1_000_000_000f;
          lastFrame = System.nanoTime();
          controller.tick(dt);
        }
      }
    }
  }

  private Main() {}
}
