package opencraft.ui;

/**
 * JavaScript bridge exposed to Ultralight pages as {@code opencraft}.
 *
 * <p>Menu HTML calls these methods for navigation. Multiplayer, options, and world creation are
 * stubs until those systems exist.
 */
public final class MenuBridge {
  private final UltralightGui gui;

  /**
   * Creates a bridge bound to the given Ultralight GUI host.
   *
   * @param gui GUI controller used to load HTML pages
   */
  public MenuBridge(UltralightGui gui) {
    this.gui = gui;
  }

  /** Navigates to the main menu. */
  public void goMain() {
    gui.loadPage("main.html");
  }

  /** Navigates to the singleplayer menu. */
  public void goSingleplayer() {
    gui.loadPage("singleplayer.html");
  }

  /** Navigates to the create-world screen. */
  public void goCreateWorld() {
    gui.loadPage("create_world.html");
  }

  /** Placeholder for the multiplayer menu. Currently does nothing. */
  public void goMultiplayer() {
    // Reserved for multiplayer menu.
  }

  /** Placeholder for the options menu. Currently does nothing. */
  public void goOptions() {
    // Reserved for options menu.
  }

  /**
   * Placeholder for world creation. Currently does not generate or load a world.
   *
   * @param name world display name
   * @param seed world seed string (may be empty for a random seed later)
   */
  public void createWorld(String name, String seed) {
    // World generation will be implemented later.
  }
}
