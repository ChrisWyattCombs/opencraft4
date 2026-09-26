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
 * <p>Writes are flushed and fsynced so the last breadcrumb survives process death.
 */
public final class DiagLog {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
  private static final Object LOCK = new Object();
  private static final AtomicBoolean HEARTBEAT_STARTED = new AtomicBoolean(false);
  private static final AtomicLong HEARTBEAT_SEQ = new AtomicLong();

  private static Path logFile;
  private static volatile String lastMark = "init";

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
        Files.writeString(
            logFile,
            "=== Opencraft diag " + LocalDateTime.now() + " ===\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING);
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
                () -> log("shutdown-hook lastMark=" + lastMark + " hb=" + HEARTBEAT_SEQ.get()),
                "diag-shutdown"));
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
                if (n % 4 == 0) {
                  write("heartbeat n=" + n + " lastMark=" + lastMark, false, false);
                }
                try {
                  Thread.sleep(250);
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
