package opencraft.world;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Creates and loads world folders with {@code world.json} metadata. */
public final class WorldIO {

  private static final Pattern NAME_PATTERN = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]*)\"");
  private static final Pattern SEED_PATTERN = Pattern.compile("\"seed\"\\s*:\\s*(-?\\d+)");

  private WorldIO() {}

  /**
   * Summary of a saved world for the singleplayer menu.
   *
   * @param folder directory name under the worlds root
   * @param name display name from {@code world.json}
   * @param seed world seed
   */
  public record WorldInfo(String folder, String name, long seed) {}

  /**
   * Lists all worlds under {@code worldsRoot} that have valid {@code world.json} metadata.
   *
   * @param worldsRoot parent folder for all saves
   * @return worlds sorted by display name (case-insensitive)
   * @throws IOException if the root cannot be read
   */
  public static List<WorldInfo> listWorlds(Path worldsRoot) throws IOException {
    List<WorldInfo> out = new ArrayList<>();
    if (!Files.isDirectory(worldsRoot)) {
      return out;
    }
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldsRoot)) {
      for (Path dir : stream) {
        if (!Files.isDirectory(dir)) {
          continue;
        }
        Path meta = dir.resolve("world.json");
        if (!Files.isRegularFile(meta)) {
          continue;
        }
        try {
          String json = Files.readString(meta);
          Matcher nameMatcher = NAME_PATTERN.matcher(json);
          Matcher seedMatcher = SEED_PATTERN.matcher(json);
          if (!nameMatcher.find() || !seedMatcher.find()) {
            continue;
          }
          String name = unescapeJson(nameMatcher.group(1));
          long seed = Long.parseLong(seedMatcher.group(1));
          out.add(new WorldInfo(dir.getFileName().toString(), name, seed));
        } catch (IOException | NumberFormatException ignored) {
          // Skip corrupt saves.
        }
      }
    }
    out.sort(Comparator.comparing(WorldInfo::name, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  /**
   * Serializes {@link #listWorlds(Path)} as a JSON array for the Ultralight menu.
   *
   * <p>Copies each world's {@code screenshot.png} into {@code iconCacheDir} (under the UI root) so
   * Ultralight can load icons via relative paths.
   *
   * @param worldsRoot parent folder for all saves
   * @param iconCacheDir directory for menu thumbnails (typically {@code run/ui/world-icons})
   * @return JSON array of {@code {folder,name,seed,icon}}
   * @throws IOException if listing fails
   */
  public static String listWorldsJson(Path worldsRoot, Path iconCacheDir) throws IOException {
    Files.createDirectories(iconCacheDir);
    StringBuilder sb = new StringBuilder("[");
    List<WorldInfo> worlds = listWorlds(worldsRoot);
    for (int i = 0; i < worlds.size(); i++) {
      WorldInfo w = worlds.get(i);
      if (i > 0) {
        sb.append(',');
      }
      String iconRel = syncWorldIcon(worldsRoot, iconCacheDir, w.folder());
      sb.append("{\"folder\":\"")
          .append(escapeJson(w.folder()))
          .append("\",\"name\":\"")
          .append(escapeJson(w.name()))
          .append("\",\"seed\":")
          .append(w.seed())
          .append(",\"icon\":\"")
          .append(escapeJson(iconRel))
          .append("\"}");
    }
    return sb.append(']').toString();
  }

  /**
   * Serializes worlds with empty icon fields (no thumbnail sync).
   *
   * @param worldsRoot parent folder for all saves
   * @return JSON array of {@code {folder,name,seed,icon}} with empty {@code icon}
   * @throws IOException if listing fails
   */
  public static String listWorldsJson(Path worldsRoot) throws IOException {
    StringBuilder sb = new StringBuilder("[");
    List<WorldInfo> worlds = listWorlds(worldsRoot);
    for (int i = 0; i < worlds.size(); i++) {
      WorldInfo w = worlds.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append("{\"folder\":\"")
          .append(escapeJson(w.folder()))
          .append("\",\"name\":\"")
          .append(escapeJson(w.name()))
          .append("\",\"seed\":")
          .append(w.seed())
          .append(",\"icon\":\"\"}");
    }
    return sb.append(']').toString();
  }

  /**
   * Copies {@code screenshot.png} into the UI icon cache when present.
   *
   * @return relative path {@code world-icons/&lt;folder&gt;.png}, or empty if no screenshot
   */
  private static String syncWorldIcon(Path worldsRoot, Path iconCacheDir, String folder)
      throws IOException {
    Path src = worldsRoot.resolve(folder).resolve("screenshot.png");
    if (!Files.isRegularFile(src)) {
      return "";
    }
    String safe = folder.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    Path dest = iconCacheDir.resolve(safe + ".png");
    boolean needsCopy =
        !Files.isRegularFile(dest)
            || Files.getLastModifiedTime(src).compareTo(Files.getLastModifiedTime(dest)) > 0;
    if (needsCopy) {
      Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
    }
    return "world-icons/" + safe + ".png";
  }

  /**
   * Returns whether a world with this display name (or sanitized folder) already exists.
   *
   * @param worldsRoot parent folder for all saves
   * @param name desired world name
   * @return {@code true} if the name is taken
   * @throws IOException if listing fails
   */
  public static boolean worldNameExists(Path worldsRoot, String name) throws IOException {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      trimmed = "New World";
    }
    String safe = sanitizeName(trimmed);
    if (Files.isDirectory(worldsRoot.resolve(safe))) {
      return true;
    }
    for (WorldInfo info : listWorlds(worldsRoot)) {
      if (info.name().equalsIgnoreCase(trimmed) || info.folder().equalsIgnoreCase(safe)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a new world directory under {@code worldsRoot}.
   *
   * <p>The folder name is the sanitized display name. Fails if that name is already taken.
   *
   * @param worldsRoot parent folder for all saves
   * @param name desired world name
   * @param seedString seed text; empty picks a random seed
   * @return opened {@link World} instance
   * @throws IOException if the name is taken or the directory cannot be created
   */
  public static World createWorld(Path worldsRoot, String name, String seedString)
      throws IOException {
    String safeName = sanitizeName(name);
    if (worldNameExists(worldsRoot, name)) {
      throw new IOException("A world named \"" + safeName + "\" already exists");
    }
    long seed = parseSeed(seedString);
    Path worldDir = worldsRoot.resolve(safeName);
    Files.createDirectories(worldDir.resolve("regions"));
    String displayName = name == null || name.isBlank() ? safeName : name.trim();
    Files.writeString(worldDir.resolve("world.json"), formatWorldJson(displayName, seed));
    return new World(displayName, seed, worldDir);
  }

  /**
   * Loads an existing world from {@code worldDir}.
   *
   * @param worldDir directory containing {@code world.json}
   * @return loaded world
   * @throws IOException if metadata is missing or invalid
   */
  public static World loadWorld(Path worldDir) throws IOException {
    Path meta = worldDir.resolve("world.json");
    String json = Files.readString(meta);
    Matcher nameMatcher = NAME_PATTERN.matcher(json);
    Matcher seedMatcher = SEED_PATTERN.matcher(json);
    if (!nameMatcher.find() || !seedMatcher.find()) {
      throw new IOException("Invalid world.json in " + worldDir);
    }
    String name = unescapeJson(nameMatcher.group(1));
    long seed = Long.parseLong(seedMatcher.group(1));
    return new World(name, seed, worldDir);
  }

  /**
   * Loads a world by folder name under {@code worldsRoot}.
   *
   * @param worldsRoot parent folder for all saves
   * @param folder directory name
   * @return loaded world
   * @throws IOException if the folder is missing or invalid
   */
  public static World loadWorldByFolder(Path worldsRoot, String folder) throws IOException {
    if (folder == null
        || folder.isBlank()
        || folder.contains("..")
        || folder.contains("/")
        || folder.contains("\\")) {
      throw new IOException("Invalid world folder");
    }
    Path rootAbs = worldsRoot.toAbsolutePath().normalize();
    Path worldDir = rootAbs.resolve(folder).normalize();
    if (!worldDir.startsWith(rootAbs) || !Files.isDirectory(worldDir)) {
      throw new IOException("World not found: " + folder);
    }
    return loadWorld(worldDir);
  }

  /**
   * Sanitizes a world name for use as a folder name.
   *
   * @param name raw user input
   * @return filesystem-safe name
   */
  public static String sanitizeName(String name) {
    String trimmed = name == null ? "" : name.trim();
    if (trimmed.isEmpty()) {
      trimmed = "World";
    }
    String safe = trimmed.replaceAll("[^a-zA-Z0-9_\\- ]", "_").replaceAll("\\s+", "_");
    if (safe.isEmpty()) {
      return "World";
    }
    return safe;
  }

  /**
   * Parses a seed string; blank input yields a random seed.
   *
   * @param seedString textual seed
   * @return numeric seed
   */
  public static long parseSeed(String seedString) {
    if (seedString == null || seedString.isBlank()) {
      return ThreadLocalRandom.current().nextLong();
    }
    try {
      return Long.parseLong(seedString.trim());
    } catch (NumberFormatException e) {
      long hash = 0L;
      for (char c : seedString.trim().toCharArray()) {
        hash = 31 * hash + c;
      }
      return hash;
    }
  }

  private static String formatWorldJson(String name, long seed) {
    return "{\"name\":\"" + escapeJson(name) + "\",\"seed\":" + seed + "}";
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String unescapeJson(String value) {
    return value.replace("\\\"", "\"").replace("\\\\", "\\");
  }
}
