package main.world;

import java.util.Random;

/**
 * 2D 柏林噪声（Perlin Noise）生成器，用于程序化地形、纹理等自然效果。
 *
 * <h3>算法简介</h3>
 * 柏林噪声通过在多维网格上随机生成梯度向量，并对周围格点进行平滑插值，
 * 产生连续且伪随机的数值序列。其输出范围大致在 {@code [-1, 1]} 之间。
 *
 * <h3>本实现特点</h3>
 * <ul>
 *   <li>固定种子（seed），保证相同输入永远得到相同的地形。</li>
 *   <li>使用长度为 512 的排列表（permutation table）避免数组越界并提高索引效率。</li>
 *   <li>实现了 {@link Runnable} 接口，可在后台线程中执行预计算任务。</li>
 *   <li>提供专为横版 2D 游戏优化的辅助方法（如 {@link #heightNoise(double)}, {@link #terrainHeight(double)}）。</li>
 *   <li>内置分形布朗运动（FBM）方法，可生成多层次细节的噪声。</li>
 *   <li>支持字符串种子输入，通过 {@link SeedHasher} 进行安全哈希处理。</li>
 * </ul>
 *
 * <h3>种子安全说明</h3>
 * <p>推荐使用 {@link #PerlinNoise(String)} 构造函数，
 * 种子字符串会经过 SHA-256 哈希处理，输出不可预测的长整型种子。
 * 如需更高安全性，可使用带盐构造函数 {@link #PerlinNoise(String, byte[])}。</p>
 *
 * @see SeedHasher
 */
public class PerlinNoise implements Runnable {

    private final long seed;
    private final Random random;
    private final int[] permutation = new int[512];

    /**
     * 使用长整型种子构造柏林噪声生成器。
     *
     * @param seed 长整型种子
     */
    public PerlinNoise(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        initPermutation();
    }

    /**
     * 使用字符串种子构造柏林噪声生成器。
     *
     * <p>字符串种子会经过 SHA-256 哈希处理，转换为长整型后用于初始化。</p>
     * <p>相同字符串必产生相同地形，适合可重现的世界生成。</p>
     *
     * @param seedString 种子字符串（如 "my-world-2024"）
     */
    public PerlinNoise(String seedString) {
        this(SeedHasher.hashToLong(seedString));
    }

    /**
     * 使用字符串种子和盐值构造柏林噪声生成器。
     *
     * <p>盐值会增加哈希结果的随机性，防止种子被彩虹表反推。</p>
     * <p>适用于用户输入种子的场景（如玩家创建的世界）。</p>
     *
     * @param seedString 种子字符串
     * @param salt       盐值（可通过 {@link SeedHasher#generateSalt()} 生成）
     */
    public PerlinNoise(String seedString, byte[] salt) {
        this(SeedHasher.hashToLong(seedString, salt));
    }

    private void initPermutation() {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        random.setSeed(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < 512; i++) {
            permutation[i] = p[i & 255];
        }
    }

    @Override
    public void run() {
        // 预计算任务占位
    }

    /**
     * 计算二维空间中某一点的柏林噪声值。
     *
     * @param x 世界 X 坐标（任意浮点数）
     * @param y 世界 Y 坐标（任意浮点数）
     * @return 噪声值，范围约 {@code [-1, 1]}
     */
    public double noise(double x, double y) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);

        double u = fade(xf);
        double v = fade(yf);

        int a = (permutation[xi] + yi) & 255;
        int b = (permutation[xi + 1] + yi) & 255;

        double x1 = lerp(grad(permutation[a], xf, yf),
                grad(permutation[b], xf - 1, yf), u);
        double x2 = lerp(grad(permutation[a + 1], xf, yf - 1),
                grad(permutation[b + 1], xf - 1, yf - 1), u);

        return lerp(x1, x2, v);
    }

    /**
     * 横版 2D 专用：仅依赖 X 坐标的高度噪声（固定 Y=0）。
     *
     * @param x 世界 X 坐标
     * @return 噪声值，范围约 {@code [-1, 1]}
     */
    public double heightNoise(double x) {
        return noise(x, 0.0);
    }

    /**
     * 归一化噪声值，从 {@code [-1, 1]} 映射到 {@code [0, 1]}。
     */
    public double normalizedNoise(double x, double y) {
        return (noise(x, y) + 1.0) * 0.5;
    }

    /**
     * 横版 2D 专用：归一化的高度噪声。
     */
    public double normalizedHeightNoise(double x) {
        return normalizedNoise(x, 0.0);
    }

    /**
     * 分形布朗运动（Fractal Brownian Motion）。
     * 通过叠加多个频率和幅度的噪声层，生成更丰富、自然的纹理或地形。
     *
     * @param x            X 坐标
     * @param y            Y 坐标
     * @param octaves      叠加次数（层级），通常 3~6
     * @param persistence  每次叠加的振幅衰减因子，范围 (0,1)
     * @param lacunarity   每次叠加的频率倍增因子，通常 2.0
     * @return 分形噪声值，范围约 {@code [-1, 1]}（理论振幅因叠加可能略超）
     */
    public double fbm(double x, double y, int octaves, double persistence, double lacunarity) {
        double value = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0.0;  // 用于归一化

        for (int i = 0; i < octaves; i++) {
            value += amplitude * noise(x * frequency, y * frequency);
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }
        // 归一化到 [-1, 1]（maxValue 此时是等比数列求和）
        return value / maxValue;
    }

    /**
     * 脊状分形噪声（Ridged Multi-fractal Noise）。
     * 通过取绝对值后反转，在原始噪声的零交叉处产生尖锐的"脊"，形成山峰般陡峭的地形。
     * 再将结果平方以收窄峰顶，使山脉轮廓更分明。
     *
     * @param x            X 坐标
     * @param y            Y 坐标
     * @param octaves      叠加次数
     * @param persistence  每层振幅衰减因子
     * @param lacunarity   每层频率倍增因子
     * @return 脊状噪声值，范围约 [-1, 1]
     */
    public double ridgedFbm(double x, double y, int octaves, double persistence, double lacunarity) {
        double value = 0.0;
        double amplitude = 1.0;
        double frequency = 1.0;
        double maxValue = 0.0;

        for (int i = 0; i < octaves; i++) {
            double n = 1.0 - Math.abs(noise(x * frequency, y * frequency));
            n = n * n;
            value += n * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        value = value / maxValue;
        return value * 2.0 - 1.0;
    }

    /**
     * 横版 2D 地形专用高度生成器（平原、丘陵、山脉三层混合）。
     *
     * <h3>地形类型</h3>
     * <ul>
     *   <li><b>平原（Plains）</b>：极其平坦的区域，噪声振幅极低，地面几乎水平</li>
     *   <li><b>丘陵（Hills）</b>：中等起伏，标准 FBM 叠加，产生平缓连绵的丘陵</li>
     *   <li><b>山脉（Mountains）</b>：平缓起伏的山丘，使用标准 FBM 而非 ridged，避免陡峭悬崖</li>
     * </ul>
     *
     * <h3>算法设计</h3>
     * <ol>
     *   <li><b>生物群落蒙版</b>：极低频噪声（频率 1/250），产生大范围连续地形分区</li>
     *   <li><b>平原计算</b>：极低频低幅 FBM，产生平坦均一的地形</li>
     *   <li><b>丘陵计算</b>：标准 FBM，产生平缓起伏的丘陵</li>
     *   <li><b>山脉计算</b>：标准 FBM（低频率），产生平缓的山丘而非陡峭悬崖</li>
     *   <li><b>最终混合</b>：根据蒙版权重在三种地形间平滑过渡</li>
     * </ol>
     *
     * <h3>悬崖问题修复（v3）</h3>
     * <ul>
     *   <li>移除 ridgedFbm，改用标准 FBM，避免尖锐山脊产生悬崖</li>
     *   <li>大幅降低山脉振幅（0.4 vs 0.75），产生平缓起伏</li>
     *   <li>增加蒙版频率（250 vs 180），地形类型变化更缓慢</li>
     *   <li>加宽过渡带（0.5 vs 0.45），三种地形平滑渐变</li>
     *   <li>减少山脉层数（3 vs 5），减少高频细节，避免突变</li>
     * </ul>
     *
     * @param worldX 世界 X 坐标（格子坐标）
     * @return 高度噪声值，范围约 [-1, 1]
     */
    public double terrainHeight(double worldX) {
        // === 生物群落蒙版：极低频大尺度噪声 ===
        // 频率 1/250 产生非常大的连续区域，减少地形类型切换频率
        double biomeMask = noise(worldX / 250.0, 50.0);

        // === 平原：极低频低幅 FBM ===
        // 频率 1/100 产生大波长平缓变化
        // 振幅 0.015 产生几乎察觉不到的微小起伏
        double plains = fbm(worldX / 100.0, 0.0, 2, 0.5, 2.0) * 0.015;

        // === 丘陵：标准 FBM ===
        // 频率 1/60 产生中等波长舒缓起伏
        // 3层层叠 + persistence 0.4 = 平滑但有细节的丘陵
        double hills = fbm(worldX / 60.0, 0.0, 3, 0.4, 2.0) * 0.35;

        // === 山脉：标准 FBM（替代 ridgedFbm） ===
        // 使用标准 FBM 而非 ridgedFbm，避免尖锐山脊产生悬崖
        // 低频（1/80）= 大范围平缓山丘
        // 低振幅（0.4）= 起伏程度适中
        // 3层层叠 = 减少高频细节，避免突变
        double mountains = fbm(worldX / 80.0, 50.0, 3, 0.45, 2.0) * 0.4;

        // === 权重计算与平滑过渡 ===
        // 蒙版阈值：-0.25 到 0.25，过渡带加宽至 0.5 宽度
        // 使用平滑步进函数减少边界突变
        double t = (biomeMask + 0.25) / 0.5;
        t = Math.max(0, Math.min(1, t));

        // Sigmoid 平滑曲线，使过渡更加自然
        double plainsWeight = Math.sin(t * Math.PI * 0.5) * Math.sin(t * Math.PI * 0.5);
        double mountainsWeight = Math.sin((1 - t) * Math.PI * 0.5) * Math.sin((1 - t) * Math.PI * 0.5);
        double hillsWeight = 1.0 - plainsWeight - mountainsWeight;

        // 确保权重为正且总和为 1
        double totalWeight = plainsWeight + hillsWeight + mountainsWeight;
        if (totalWeight > 0) {
            plainsWeight /= totalWeight;
            hillsWeight /= totalWeight;
            mountainsWeight /= totalWeight;
        }

        return plains * plainsWeight + hills * hillsWeight + mountains * mountainsWeight;
    }

    // ---- 内部辅助函数 ----
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private double grad(int hash, double x, double y) {
        int h = hash & 3;
        double u = h < 2 ? x : y;
        double v = h < 2 ? y : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}