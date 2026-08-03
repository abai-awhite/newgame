package main.world;

/**
 * 种子扩展器：将单个世界种子扩展为 4 个独立的二级种子。
 */
public class SeedExpander {
    public static long[] expand(long seed) {
        long s1 = seed ^ 0x9E3779B97F4A7C15L;
        long s2 = seed ^ 0xBF58476D1CE4E5B9L;
        long s3 = seed ^ 0x6A09E667F3BCC909L;
        long s4 = seed ^ 0x5BE0CD19137E2179L;

        s1 = mix(s1);
        s2 = mix(s2);
        s3 = mix(s3);
        s4 = mix(s4);

        return new long[]{s1, s2, s3, s4};
    }

    private static long mix(long x) {
        x = (x ^ (x >>> 30)) * 0xBF58476D1CE4E5B9L;
        x = (x ^ (x >>> 27)) * 0x94D049BB133111EBL;
        x = x ^ (x >>> 31);
        return x;
    }
}
