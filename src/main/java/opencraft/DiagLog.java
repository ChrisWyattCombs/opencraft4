package opencraft;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Durable diagnostic log for silent native kills (TDR / {@code ucrtbase} abort).
 *
 * <p>Writes are flushed and fsynced so the last breadcrumb survives process death. A small session
 * marker file tracks whether the previous run exited cleanly — silent kills skip Java shutdown
 * hooks, so a leftover {@code RUNNING} marker is the signal.
 */
public final class DiagLog {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
  private static final Object LOCK = new Object();
  private static final AtomicBoolean HEARTBEAT_STARTED = new AtomicBoolean(false);
  private static final AtomicLong HEARTBEAT_SEQ = new AtomicLong();
  private static final AtomicBoolean CLEAN_EXIT = new AtomicBoolean(false);

  private static Path logFile;
  private static Path sessionFile;
  private static Path quitLogFile;
  private static volatile String lastMark = "init";
  private static String lastSilentQuitNotice;

  private DiagLog() {}

  /**
   * Opens {@code run/diag.log} under the project root (truncates any previous run).
   *
   * @param projectRoot project working directory
   */
  public static void init(Path projectRoot) {
    synchronized (LOCK) {
      try {
        Path dir = projectRoot.resolve("run");
        Files.createDirectories(dir);
        logFile = dir.resolve("diag.log");
        sessionFile = dir.resolve("diag.session");
        quitLogFile = dir.resolve("quit.log");
        detectSilentQuitLocked();
        Files.writeString(
            logFile,
            "=== Opencraft diag " + LocalDateTime.now() + " ===\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
        if (lastSilentQuitNotice != null) {
          try (FileOutputStream fos = new FileOutputStream(logFile.toFile(), true)) {
            byte[] bytes = ("BOOT " + lastSilentQuitNotice + "\n").getBytes(StandardCharsets.UTF_8);
            fos.write(bytes);
            fos.flush();
            fos.getFD().sync();
          }
          lastSilentQuitNotice = null;
        }
        writeSessionLocked("RUNNING", "boot");
      } catch (IOException e) {
        System.err.println("[Opencraft] diag log init failed: " + e.getMessage());
        logFile = null;
      }
    }
    log("diag init ok path=" + logFile);
    startHeartbeat();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (CLEAN_EXIT.get()) {
                    write("shutdown-hook clean lastMark=" + lastMark, true, false);
                  } else {
                    // JVM exiting without markCleanExit — hook may still run for some deaths.
                    write(
                        "shutdown-hook UNCLEAN lastMark="
                            + lastMark
                            + " hb="
                            + HEARTBEAT_SEQ.get()
                            + " (native abort often skips this hook)",
                        true,
                        false);
                    writeSessionBestEffort("UNCLEAN_SHUTDOWN", lastMark);
                    appendQuitBestEffort("UNCLEAN_SHUTDOWN lastMark=" + lastMark);
                  }
                },
                "diag-shutdown"));
  }

  /**
   * Records that the process is leaving through a normal Java path (window closed, etc.).
   *
   * <p>Call this before returning from {@code main}. Silent native kills never reach this, so the
   * session file stays {@code RUNNING} for the next launch to report.
   */
  public static void markCleanExit() {
    CLEAN_EXIT.set(true);
    write("CLEAN_EXIT lastMark=" + lastMark + " hb=" + HEARTBEAT_SEQ.get(), true, false);
    writeSessionBestEffort("CLEAN_EXIT", lastMark);
    appendQuitBestEffort("CLEAN_EXIT lastMark=" + lastMark);
  }

  /**
   * Appends one line to the diag log (and stdout) and forces it to disk.
   *
   * @param message plain text (timestamp is added automatically)
   */
  public static void log(String message) {
    write(message, /* echo= */ true, /* updateMark= */ true);
  }

  /**
   * Appends one line to the diag log without printing to stdout.
   *
   * @param message plain text (timestamp is added automatically)
   */
  public static void logQuiet(String message) {
    write(message, /* echo= */ false, /* updateMark= */ true);
  }

  /**
   * Logs a Vulkan result code with a friendly name when known.
   *
   * @param action description of the Vulkan call
   * @param result Vulkan {@code VkResult} value
   */
  public static void logVk(String action, int result) {
    log("VK " + action + " -> " + describeVk(result) + " (" + result + ")");
  }

  /**
   * Returns a short name for common Vulkan result codes.
   *
   * @param result Vulkan {@code VkResult} value
   * @return human-readable result name
   */
  public static String describeVk(int result) {
    return switch (result) {
      case 0 -> "VK_SUCCESS";
      case 1 -> "VK_NOT_READY";
      case 2 -> "VK_TIMEOUT";
      case 5 -> "VK_INCOMPLETE";
      case -3 -> "VK_ERROR_INITIALIZATION_FAILED";
      case -4 -> "VK_ERROR_DEVICE_LOST";
      case -1000001004 -> "VK_ERROR_OUT_OF_DATE_KHR";
      case 1000001003 -> "VK_SUBOPTIMAL_KHR";
      default -> "VK_RESULT_" + result;
    };
  }

  /**
   * Writes a timestamped line to disk.
   *
   * @param message body text
   * @param echo whether to also print to stdout
   * @param updateMark whether this line becomes the heartbeat {@code lastMark}
   */
  private static void write(String message, boolean echo, boolean updateMark) {
    if (updateMark) {
      lastMark = message;
    }
    String line = TIME.format(LocalDateTime.now()) + " " + message + "\n";
    if (echo) {
      System.out.print("[diag] " + line);
    }
    Path file = logFile;
    if (file == null) {
      return;
    }
    synchronized (LOCK) {
      try (FileOutputStream fos = new FileOutputStream(file.toFile(), true)) {
        fos.write(line.getBytes(StandardCharsets.UTF_8));
        fos.flush();
        fos.getFD().sync();
      } catch (IOException e) {
        System.err.println("[Opencraft] diag write failed: " + e.getMessage());
      }
    }
  }

  /** On boot: if the previous session never cleared RUNNING, report a silent quit. */
  private static void detectSilentQuitLocked() {
    if (sessionFile == null || !Files.isRegularFile(sessionFile)) {
      return;
    }
    try {
      String prev = Files.readString(sessionFile, StandardCharsets.UTF_8).trim();
      if (prev.startsWith("RUNNING")) {
        lastSilentQuitNotice =
            "PREVIOUS RUN SILENT QUIT — session left RUNNING; last=" + prev.replace('\n', ' ');
        System.out.println("[diag] !!! " + lastSilentQuitNotice);
        appendQuitLocked("SILENT_QUIT " + prev.replace('\n', ' '));
        Files.writeString(
            sessionFile.resolveSibling("diag.last-silent-quit.txt"),
            LocalDateTime.now() + "\n" + prev + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
      }
    } catch (IOException e) {
      System.err.println("[Opencraft] silent-quit detect failed: " + e.getMessage());
    }
  }

  private static void writeSessionBestEffort(String state, String mark) {
    synchronized (LOCK) {
      writeSessionLocked(state, mark);
    }
  }

  private static void writeSessionLocked(String state, String mark) {
    if (sessionFile == null) {
      return;
    }
    try {
      String body =
          state
              + "\n"
              + "time="
              + LocalDateTime.now()
              + "\n"
              + "lastMark="
              + mark
              + "\n"
              + "hb="
              + HEARTBEAT_SEQ.get()
              + "\n";
      try (FileOutputStream fos = new FileOutputStream(sessionFile.toFile(), false)) {
        fos.write(body.getBytes(StandardCharsets.UTF_8));
        fos.flush();
        fos.getFD().sync();
      }
    } catch (IOException e) {
      System.err.println("[Opencraft] session write failed: " + e.getMessage());
    }
  }

  private static void appendQuitBestEffort(String message) {
    synchronized (LOCK) {
      appendQuitLocked(message);
    }
  }

  private static void appendQuitLocked(String message) {
    if (quitLogFile == null) {
      return;
    }
    try {
      String line = LocalDateTime.now() + " " + message + "\n";
      Files.writeString(
          quitLogFile,
          line,
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      System.err.println("[Opencraft] quit log write failed: " + e.getMessage());
    }
  }

  /** Starts a daemon heartbeat thread that records wall-clock pulses to the diag log. */
  private static void startHeartbeat() {
    if (!HEARTBEAT_STARTED.compareAndSet(false, true)) {
      return;
    }
    Thread heartbeat =
        new Thread(
            () -> {
              while (!Thread.currentThread().isInterrupted()) {
                long n = HEARTBEAT_SEQ.incrementAndGet();
                // Pulse so we know wall-clock vs last mark if abort is silent.
                if (n % 2 == 0) {
                  write("heartbeat n=" + n + " lastMark=" + lastMark, false, false);
                  if (!CLEAN_EXIT.get()) {
                    writeSessionBestEffort("RUNNING", lastMark);
                  }
                }
                try {
                  Thread.sleep(200);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            },
            "diag-heartbeat");
    heartbeat.setDaemon(true);
    heartbeat.start();
  }
}
