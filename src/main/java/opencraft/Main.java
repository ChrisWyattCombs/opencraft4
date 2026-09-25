package opencraft;

import java.nio.file.Path;
import opencraft.graphics.Display;
import opencraft.ui.UltralightGui;
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
    try (Display display = new Display()) {
      display.createWindow("Opencraft4");
      display.initVulkan();

      try (UltralightGui gui = new UltralightGui(display, projectRoot)) {
        gui.init();

        while (!display.shouldClose()) {
          display.pollEvents();
          gui.update();
          gui.present();
        }
      }
    }
  }

  private Main() {}
}
