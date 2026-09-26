package opencraft.world.gen;

import java.util.Random;

/** Classic gradient Perlin noise for procedural terrain. */
public final class PerlinNoise {

  private static final int PERMUTATION_SIZE = 256;

  private final int[] perm = new int[PERMUTATION_SIZE * 2];

  /**
   * Builds a Perlin noise generator from the given seed.
   *
   * @param seed random seed
   */
  public PerlinNoise(long seed) {
    Random random = new Random(seed);
    int[] source = new int[PERMUTATION_SIZE];
    for (int i = 0; i < PERMUTATION_SIZE; i++) {
      source[i] = i;
    }
    for (int i = PERMUTATION_SIZE - 1; i >= 0; i--) {
      int j = random.nextInt(i + 1);
      int tmp = source[i];
      source[i] = source[j];
      source[j] = tmp;
    }
    for (int i = 0; i < PERMUTATION_SIZE * 2; i++) {
      perm[i] = source[i & 255];
    }
  }

  /**
   * Samples 2D Perlin noise at the given coordinates.
   *
   * @param x sample X
   * @param y sample Y
   * @return noise value in approximately [-1, 1]
   */
  public double noise(double x, double y) {
    int xi = floorToInt(x) & 255;
    int yi = floorToInt(y) & 255;
    double xf = x - Math.floor(x);
    double yf = y - Math.floor(y);

    double u = fade(xf);
    double v = fade(yf);

    int aa = perm[perm[xi] + yi];
    int ab = perm[perm[xi] + yi + 1];
    int ba = perm[perm[xi + 1] + yi];
    int bb = perm[perm[xi + 1] + yi + 1];

    double x1 = lerp(u, grad(aa, xf, yf), grad(ba, xf - 1, yf));
    double x2 = lerp(u, grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1));
    return lerp(v, x1, x2);
  }

  /**
   * Samples fractal Brownian motion built from multiple octaves of {@link #noise}.
   *
   * @param x sample X
   * @param y sample Y
   * @param octaves number of layers
   * @param persistence amplitude falloff per octave
   * @return combined noise value
   */
  public double octaveNoise(double x, double y, int octaves, double persistence) {
    double total = 0.0;
    double frequency = 1.0;
    double amplitude = 1.0;
    double maxValue = 0.0;
    for (int i = 0; i < octaves; i++) {
      total += noise(x * frequency, y * frequency) * amplitude;
      maxValue += amplitude;
      amplitude *= persistence;
      frequency *= 2.0;
    }
    return total / maxValue;
  }

  private static int floorToInt(double value) {
    int i = (int) value;
    return value < i ? i - 1 : i;
  }

  private static double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private static double lerp(double t, double a, double b) {
    return a + t * (b - a);
  }

  private static double grad(int hash, double x, double y) {
    int h = hash & 7;
    double u = h < 4 ? x : y;
    double v = h < 4 ? y : x;
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }
}
