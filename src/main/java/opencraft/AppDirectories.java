package opencraft;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Platform directories for persistent Opencraft data.
 *
 * <p>Windows stores worlds under {@code %APPDATA%/opencraft/worlds}. macOS stores them under {@code
 * ~/Library/Application Support/opencraft/worlds}. Other systems use {@code ~/opencraft/worlds}.
 */
public final class AppDirectories {

  private static final String APP_FOLDER = "opencraft";
  private static final String WORLDS_FOLDER = "worlds";

  private AppDirectories() {}

  /**
   * Returns the directory that holds saved worlds on this machine.
   *
   * @return worlds directory for the current operating system
   */
  public static Path worldsDirectory() {
    return worldsDirectory(
        System.getProperty("os.name", ""),
        System.getenv("APPDATA"),
        System.getProperty("user.home", ""));
  }

  /**
   * Returns the worlds directory for the supplied platform values.
   *
   * @param osName operating system name, as in {@code os.name}
   * @param appData Windows roaming app-data directory, or {@code null} when unset
   * @param userHome user home directory
   * @return worlds directory
   */
  static Path worldsDirectory(String osName, String appData, String userHome) {
    Path home = Path.of(userHome == null ? "" : userHome);
    if (isMac(osName)) {
      return home.resolve("Library")
          .resolve("Application Support")
          .resolve(APP_FOLDER)
          .resolve(WORLDS_FOLDER);
    }
    Path base = appData != null && !appData.isBlank() ? Path.of(appData) : home;
    return base.resolve(APP_FOLDER).resolve(WORLDS_FOLDER);
  }

  private static boolean isMac(String osName) {
    return osName != null && osName.toLowerCase(Locale.ROOT).contains("mac");
  }
}
