package opencraft.ui;

import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import opencraft.world.WorldIO;

/**
 * JavaScript bridge exposed to Ultralight pages as {@code opencraft}.
 *
 * <p>Menu HTML calls these methods for navigation and world creation. Databind-bound methods with
 * multiple arguments are unreliable from JS, so create-world uses staged setters plus a no-arg
 * commit.
 */
public final class MenuBridge {
  private final UltralightGui gui;
  private final Path worldsRoot;
  private String pendingWorldName = "New World";
  private String pendingWorldSeed = "";
  private String pendingWorldFolder = "";

  /**
   * Creates a bridge bound to the given Ultralight GUI host.
   *
   * @param gui GUI controller used to load HTML pages
   * @param worldsRoot folder containing saved worlds
   */
  public MenuBridge(UltralightGui gui, Path worldsRoot) {
    this.gui = gui;
    this.worldsRoot = worldsRoot;
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
   * Returns JSON listing of saved worlds for the singleplayer menu.
   *
   * <p>On failure, logs to stderr and returns {@code "[]"}.
   *
   * @return JSON array string
   */
  public String listWorldsJson() {
    try {
      return WorldIO.listWorldsJson(worldsRoot, gui.worldIconsDir());
    } catch (Exception e) {
      System.err.println("[Opencraft] listWorlds failed: " + e.getMessage());
      return "[]";
    }
  }

  /**
   * Returns whether a world name is already taken.
   *
   * @param name proposed display name
   * @return {@code true} if taken; {@code false} if the name is free or listing fails
   */
  public boolean worldNameTaken(String name) {
    try {
      return WorldIO.worldNameExists(worldsRoot, name);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Stores the world name for the next {@link #createWorld()} call.
   *
   * @param name world display name
   */
  public void setWorldName(String name) {
    pendingWorldName = name == null || name.isBlank() ? "New World" : name.trim();
  }

  /**
   * Stores the seed text for the next {@link #createWorld()} call.
   *
   * @param seed seed string; blank means random
   */
  public void setWorldSeed(String seed) {
    pendingWorldSeed = seed == null ? "" : seed.trim();
  }

  /**
   * Stores the folder name for the next {@link #loadWorld()} call.
   *
   * @param folder world directory name under the saves root
   */
  public void setWorldFolder(String folder) {
    pendingWorldFolder = folder == null ? "" : folder.trim();
  }

  /** Starts world creation using the pending name and seed. */
  public void createWorld() {
    System.out.println(
        "[Opencraft] createWorld name="
            + pendingWorldName
            + " seed="
            + (pendingWorldSeed.isEmpty() ? "<random>" : pendingWorldSeed));
    BiConsumer<String, String> handler = gui.getCreateWorldHandler();
    if (handler == null) {
      System.err.println("[Opencraft] createWorld handler is not set");
      return;
    }
    handler.accept(pendingWorldName, pendingWorldSeed);
  }

  /** Loads the pending world folder. */
  public void loadWorld() {
    System.out.println("[Opencraft] loadWorld folder=" + pendingWorldFolder);
    Consumer<String> handler = gui.getLoadWorldHandler();
    if (handler == null) {
      System.err.println("[Opencraft] loadWorld handler is not set");
      return;
    }
    handler.accept(pendingWorldFolder);
  }
}
