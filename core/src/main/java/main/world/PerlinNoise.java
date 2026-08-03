package main.world;

import java.util.Random;

/**
 * 简化的 Perlin 噪声实现，用于地形生成。
 */
public class PerlinNoise {
    private final int[] perm;
    private final long seed;

    public PerlinNoise(long seed) {
        this.seed = seed;
        this.perm = new int[512];
        Random rand = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = rand.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        System.arraycopy(p, 0, perm, 0, 256);
        System.arraycopy(p, 0, perm, 256, 256);
    }

    private double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private double lerp(double a, double b, double t) { return a + t * (b - a); }
    private double grad(int hash, double x, double y) {
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        double x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    /**
     * 用于地形高度的噪声（多层叠加）。
     */
    public double terrainHeight(int worldX) {
        double val = 0;
        double amp = 1;
        double freq = 1;
        double maxVal = 0;
        for (int i = 0; i < 4; i++) {
            val += noise(worldX * freq * 0.01, 0) * amp;
            maxVal += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return val / maxVal;
    }
}
