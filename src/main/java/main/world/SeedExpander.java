package main.world;

/**
 * 种子扩展器：将一级种子（long）通过 SplitMix64 确定性哈希扩展为 4 个二级种子（long）。
 *
 * <p>SplitMix64 是一种快速、高质量的非加密哈希，专为 64 位整数设计。
 * 相同一级种子永远产生完全相同的 4 个二级种子序列，保证可重现性。</p>
 *
 * <h3>二级种子用途</h3>
 * <ul>
 *   <li>{@code subSeeds[0]} — 地形种子（地表高度）</li>
 *   <li>{@code subSeeds[1]} — 洞穴种子（洞穴挖掘）</li>
 *   <li>{@code subSeeds[2]} — 生物群系种子（群系分布）</li>
 *   <li>{@code subSeeds[3]} — 资源种子（矿物/植被分布）</li>
 * </ul>
 */
public class SeedExpander {

    private SeedExpander() {}

    /**
     * 将一级种子扩展为 4 个二级种子。
     *
     * @param primarySeed 一级种子（用户输入或随机生成）
     * @return 长度为 4 的 long 数组，每个元素均为一个独立的二级种子
     */
    public static long[] expand(long primarySeed) {
        long[] seeds = new long[4];
        for (int i = 0; i < 4; i++) {
            seeds[i] = mix(primarySeed, i);
        }
        return seeds;
    }

    /**
     * SplitMix64 核心混合函数：对 (seed + index) 进行 3 轮非线性变换，
     * 确保任何微小的输入差异都会扩散到整个 64 位输出空间。
     */
    private static long mix(long seed, long index) {
        long h = seed + index;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        h = h ^ (h >>> 31);
        return h;
    }
}