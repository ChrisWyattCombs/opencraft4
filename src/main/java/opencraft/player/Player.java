package opencraft.player;

import opencraft.world.World;
import opencraft.world.block.Block;

/** First-person player with simple axis-aligned collision against solid blocks. */
public final class Player {

  /** Bit flag: move forward relative to yaw. */
  public static final int INPUT_FORWARD = 1 << 0;

  /** Bit flag: move backward relative to yaw. */
  public static final int INPUT_BACKWARD = 1 << 1;

  /** Bit flag: strafe left. */
  public static final int INPUT_LEFT = 1 << 2;

  /** Bit flag: strafe right. */
  public static final int INPUT_RIGHT = 1 << 3;

  /** Bit flag: jump, swim up, or ascend while flying. */
  public static final int INPUT_JUMP = 1 << 4;

  /** Bit flag: descend while flying or swim down. */
  public static final int INPUT_SNEAK = 1 << 5;

  private static final double WALK_SPEED = 4.3;
  private static final double SWIM_SPEED = 2.8;
  private static final double FLY_SPEED = 10.0;
  private static final double GRAVITY = 24.0;
  private static final double WATER_GRAVITY = 4.5;
  private static final double SWIM_ACCEL = 22.0;
  private static final double SWIM_MAX_UP = 4.2;
  private static final double JUMP_VELOCITY = 8.0;
  private static final double EYE_HEIGHT = 1.82;
  private static final double HALF_WIDTH = 0.3;
  private static final double HEIGHT = 2.0;

  private final World world;
  private double x;
  private double y;
  private double z;
  private float yaw;
  private float pitch;
  private boolean flying;
  private double velocityY;

  /**
   * Creates a player in the given world at the origin.
   *
   * @param world collision and block query world
   */
  public Player(World world) {
    this.world = world;
    this.y = 80.0;
  }

  /**
   * Returns world X position of the player feet.
   *
   * @return x coordinate
   */
  public double getX() {
    return x;
  }

  /**
   * Returns world Y position of the player feet.
   *
   * @return y coordinate
   */
  public double getY() {
    return y;
  }

  /**
   * Returns world Z position of the player feet.
   *
   * @return z coordinate
   */
  public double getZ() {
    return z;
  }

  /**
   * Sets the player feet position.
   *
   * @param x world X
   * @param y world Y
   * @param z world Z
   */
  public void setPosition(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * Returns horizontal look yaw in degrees.
   *
   * @return yaw
   */
  public float getYaw() {
    return yaw;
  }

  /**
   * Returns vertical look pitch in degrees.
   *
   * @return pitch
   */
  public float getPitch() {
    return pitch;
  }

  /**
   * Sets look direction.
   *
   * @param yaw horizontal degrees
   * @param pitch vertical degrees
   */
  public void setLook(float yaw, float pitch) {
    this.yaw = yaw;
    this.pitch = Math.max(-89f, Math.min(89f, pitch));
  }

  /**
   * Returns whether the player is in flight mode (no gravity; still collides with solids).
   *
   * @return flying mode
   */
  public boolean isFlying() {
    return flying;
  }

  /**
   * Enables or disables flight mode.
   *
   * @param flying {@code true} for creative-style flight with collision
   */
  public void setFlying(boolean flying) {
    this.flying = flying;
    if (flying) {
      velocityY = 0.0;
    }
  }

  /**
   * Returns the camera position at eye height.
   *
   * @return array {@code [eyeX, eyeY, eyeZ]}
   */
  public double[] getEyePosition() {
    return new double[] {x, y + EYE_HEIGHT, z};
  }

  /**
   * Returns whether the camera is currently inside a liquid block.
   *
   * @return {@code true} when eyes are underwater
   */
  public boolean isEyeInWater() {
    double[] eye = getEyePosition();
    return isLiquidAt(eye[0], eye[1], eye[2]);
  }

  /**
   * Returns whether any part of the body is in water (for swim physics).
   *
   * @return {@code true} when swimming / wading
   */
  public boolean isInWater() {
    return isLiquidAt(x, y + 0.4, z) || isLiquidAt(x, y + 1.2, z) || isEyeInWater();
  }

  /**
   * Advances simulation by {@code deltaSeconds} using keyboard input flags.
   *
   * @param deltaSeconds frame time in seconds
   * @param inputFlags bitmask of {@code INPUT_*} constants
   */
  public void update(double deltaSeconds, int inputFlags) {
    double yawRad = Math.toRadians(yaw);
    double forwardX = -Math.sin(yawRad);
    double forwardZ = Math.cos(yawRad);
    double rightX = Math.cos(yawRad);
    double rightZ = Math.sin(yawRad);

    double moveX = 0.0;
    double moveZ = 0.0;
    if ((inputFlags & INPUT_FORWARD) != 0) {
      moveX += forwardX;
      moveZ += forwardZ;
    }
    if ((inputFlags & INPUT_BACKWARD) != 0) {
      moveX -= forwardX;
      moveZ -= forwardZ;
    }
    if ((inputFlags & INPUT_LEFT) != 0) {
      moveX -= rightX;
      moveZ -= rightZ;
    }
    if ((inputFlags & INPUT_RIGHT) != 0) {
      moveX += rightX;
      moveZ += rightZ;
    }

    double length = Math.hypot(moveX, moveZ);
    if (length > 1e-6) {
      moveX /= length;
      moveZ /= length;
    }

    double speed = flying ? FLY_SPEED : WALK_SPEED;
    double dx = moveX * speed * deltaSeconds;
    double dz = moveZ * speed * deltaSeconds;
    double dy = 0.0;

    if (flying) {
      if ((inputFlags & INPUT_JUMP) != 0) {
        dy += speed * deltaSeconds;
      }
      if ((inputFlags & INPUT_SNEAK) != 0) {
        dy -= speed * deltaSeconds;
      }
      moveWithCollision(dx, dy, dz);
      return;
    }

    boolean inWater = isInWater();
    if (inWater) {
      speed = SWIM_SPEED;
      dx = moveX * speed * deltaSeconds;
      dz = moveZ * speed * deltaSeconds;
      velocityY -= WATER_GRAVITY * deltaSeconds;
      // Water drag so you don't keep falling like in air.
      velocityY *= Math.max(0.0, 1.0 - 3.5 * deltaSeconds);
      if ((inputFlags & INPUT_JUMP) != 0) {
        velocityY += SWIM_ACCEL * deltaSeconds;
        if (velocityY > SWIM_MAX_UP) {
          velocityY = SWIM_MAX_UP;
        }
      }
      if ((inputFlags & INPUT_SNEAK) != 0) {
        velocityY -= SWIM_ACCEL * 0.65 * deltaSeconds;
      }
    } else {
      if ((inputFlags & INPUT_JUMP) != 0 && onGround()) {
        velocityY = JUMP_VELOCITY;
      }
      velocityY -= GRAVITY * deltaSeconds;
    }
    dy = velocityY * deltaSeconds;

    moveWithCollision(dx, dy, dz);
  }

  private boolean isLiquidAt(double px, double py, double pz) {
    int bx = (int) Math.floor(px);
    int by = (int) Math.floor(py);
    int bz = (int) Math.floor(pz);
    return world.getBlock(bx, by, bz).isLiquid();
  }

  private void moveWithCollision(double dx, double dy, double dz) {
    double newX = x + dx;
    if (wouldOverlap(newX, y, z, HALF_WIDTH, HEIGHT)) {
      newX = x;
    }
    x = newX;

    double newZ = z + dz;
    if (wouldOverlap(x, y, newZ, HALF_WIDTH, HEIGHT)) {
      newZ = z;
    }
    z = newZ;

    double newY = y + dy;
    if (wouldOverlap(x, newY, z, HALF_WIDTH, HEIGHT)) {
      if (dy < 0) {
        velocityY = 0.0;
      }
      newY = y;
    }
    y = newY;
  }

  private boolean onGround() {
    return wouldOverlap(x, y - 0.05, z, HALF_WIDTH, HEIGHT);
  }

  private boolean wouldOverlap(double px, double py, double pz, double halfWidth, double height) {
    int minX = (int) Math.floor(px - halfWidth);
    int maxX = (int) Math.floor(px + halfWidth);
    int minY = (int) Math.floor(py);
    int maxY = (int) Math.floor(py + height - 1e-4);
    int minZ = (int) Math.floor(pz - halfWidth);
    int maxZ = (int) Math.floor(pz + halfWidth);

    for (int bx = minX; bx <= maxX; bx++) {
      for (int by = minY; by <= maxY; by++) {
        for (int bz = minZ; bz <= maxZ; bz++) {
          Block block = world.getBlock(bx, by, bz);
          if (block.isSolid()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
