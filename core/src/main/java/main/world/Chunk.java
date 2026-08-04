package main.world;

/**
 * 横版 2D 地形区块，使用分形柏林噪声生成地表并填充固体/空气。
 * 世界总高度固定为 1024 格（0～1023），超出部分自动设为空气。
 */
public class Chunk {
    public static final int SIZE = 16;
    public static final int WORLD_HEIGHT = 1024;
    public static final int EMPTY_TILE = 0;

    public static final int AIR    = 0;
    /** 方块 ID 与 Minecraft 原版一致（1.13 扁平化后的数字 ID，含 1.21 及更早版本） */
    public static final int STONE  = 1;   // minecraft:stone
    public static final int GRANITE = 2;  // 石头层夹杂
    public static final int DIORITE = 4;
    public static final int ANDESITE = 6;
    public static final int BEDROCK = 31; // minecraft:bedrock
    public static final int GRASS  = 8;   // minecraft:grass_block
    public static final int DIRT   = 9;   // minecraft:dirt
    public static final int COBBLESTONE = 12;
    public static final int OBSIDIAN   = 170; // minecraft:obsidian
    public static final int GRAVEL = 37;
    public static final int WATER  = 32;  // minecraft:water
    public static final int LAVA   = 33;  // minecraft:lava
    public static final int SAND   = 34;  // minecraft:sand
    public static final int SANDSTONE = 99;
    public static final int CLAY   = 251;
    public static final int COAL_ORE   = 43;
    public static final int IRON_ORE   = 41;
    public static final int COPPER_ORE = 936;
    public static final int GOLD_ORE   = 39;
    public static final int REDSTONE_ORE = 242;
    public static final int LAPIS_ORE  = 95;
    public static final int DIAMOND_ORE = 179;
    public static final int EMERALD_ORE = 342;
    public static final int DEEPSLATE  = 1023; // 深层岩
    public static final int OAK_LOG    = 46;   // 原 oak_log
    public static final int OAK_LEAVES = 82;
    public static final int SPRUCE_LOG = 47;
    public static final int SPRUCE_LEAVES = 83;
    public static final int SNOW_BLOCK = 249;
    public static final int ICE        = 248;
    public static final int CACTUS     = 250;
    public static final int TUFF       = 909;
    public static final int TALL_GRASS = 501;
    public static final int FERN       = 124;
    public static final int DANDELION  = 147;
    public static final int POPPY      = 149;
    public static final int BROWN_MUSHROOM = 161;
    public static final int SUGAR_CANE = 252;
    public static final int FOREST = OAK_LOG; // 兼容旧引用：森林表面树木 = 橡木原木

    // ==================== 流体水位（Terraria 式 16 级，总量守恒） ====================
    /** 流体水位最大值：level = 欠满量，amount = 16 - level，0=满格（16 单位），15=最薄（1 单位）。一格水最多 16 级。 */
    public static final int MAX_FLUID_LEVEL = 15;
    /** 天然湖泊液面水位（液面格部分充盈 12/16，呈现 Terraria 式液面；低于液面格为满格） */
    public static final int WATER_SURFACE_LEVEL = 4;

    private final int[][] tiles = new int[SIZE][SIZE];
    /** 流体水位（Terraria 式 16 级）：0=满格（16 单位）~15=最薄（1 单位），amount=16-level。依附于 tiles 中的 WATER/LAVA 格。 */
    private final int[][] fluidLevel = new int[SIZE][SIZE];
    private boolean generated = false;
    private boolean modified = false;
    private boolean saved = false;

    /** 是否为流体方块（水/岩浆）。 */
    public static boolean isFluid(int type) {
        return type == WATER || type == LAVA;
    }

    public void generate(long worldSeed, int chunkX, int chunkY,
                      PerlinNoise terrainNoise, PerlinNoise caveNoise,
                      PerlinNoise biomeNoise, PerlinNoise resourceNoise) {
        int baseGroundLevel = WORLD_HEIGHT / 2;
        int amplitude = 110;
        // 全局水面线：Y 向下为正（越大越深），低于该线的凹坑会被水淹没
        int waterY = baseGroundLevel + 62;

        int[] groundY = new int[SIZE];
        int[] biome = new int[SIZE];
        // 每列水面线：默认全局 waterY，湖区设为左右边界较低一侧（水被较高一侧围住不外溢）
        int[] waterLine = new int[SIZE];
        boolean[] isLake = new boolean[SIZE];
        int[] origGy = new int[SIZE];   // 下压前的原地表高度
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            // 地形高度：先取 [-1,1] 分形噪声，再用 signedPow 压缩中间值
            // —— 小噪声区域（大部分地方）非常平缓，只有高噪声区域才隆起成山
            double raw = terrainNoise.terrainHeight(worldX);
            double v = Math.signum(raw) * Math.pow(Math.abs(raw), 1.7);
            int gy = baseGroundLevel + (int) (v * amplitude);
            gy = Math.clamp(gy, 1, WORLD_HEIGHT - 5);
            waterLine[localX] = waterY;  // 默认全局水面线
            origGy[localX] = gy;

            // 湖泊：X 轴用 lk 噪声判定湖区（lk>0.30），不限地形（山上也生成湖）
            double lk = biomeNoise.noise(worldX / 90.0, -0.3);
            if (lk > 0.30) {
                isLake[localX] = true;
            }
            groundY[localX] = gy;

            // 群系：低频噪声成片分布（平原/森林/沙漠/雪原），高地形强制为山地
            double bv = biomeNoise.noise(worldX / 130.0, 0.5);
            if (v > 0.42) {
                biome[localX] = 4;                       // 山地
            } else if (bv > 0.38) {
                biome[localX] = 1;                       // 森林
            } else if (bv < -0.42) {
                biome[localX] = 2;                       // 沙漠
            } else if (bv < -0.15) {
                biome[localX] = 3;                       // 雪原
            } else {
                biome[localX] = 0;                       // 平原
            }
            // 非湖区低洼处靠全局水线 -> 沙滩
            if (!isLake[localX] && gy >= waterY - 4 && biome[localX] != 2) biome[localX] = 5;
        }

        // ==================== 湖泊生成（整合全部判定） ====================
        // 判定1：X轴用 lk 噪声判定湖区（lk>0.30），不限地形（山上也生成湖）
        // 判定2：Y轴检查该列最高方块（地表 gy = origGy）
        // 判定3：相邻湖段（间隔≤2列）合并 + 跨区块边界扩展（噪声推算），整片湖统一水面
        // 判定4：湖面 = 左右边界较低一侧（max，y值大=视觉低），水被较高一侧围住不外溢
        // 判定5：湖底 = 湖面 + 随机深度(1~15)；原地表比湖底更深则保持原地表
        // 判定6：湖段内比湖面还高的方块全部挖掉（不留湖中岛），形成规整湖盆
        // 判定7：水面不超过两侧较低方块（湖面=较低一侧，天然满足）
        int seg = 0;
        while (seg < SIZE) {
            if (!isLake[seg]) { seg++; continue; }
            // 湖段 [seg, j)；与下一个湖段间隔 ≤ 2 列时合并（两湖刷到一块儿共用水面，避免高低不一）
            int j = seg;
            while (j < SIZE && isLake[j]) j++;
            while (j < SIZE) {
                int g2 = j;
                while (g2 < SIZE && !isLake[g2]) g2++;
                if (g2 == SIZE) break;
                if (g2 - j <= 2) {
                    j = g2;
                    while (j < SIZE && isLake[j]) j++;
                } else {
                    break;
                }
            }
            // 判定2+4：左右边界。区块内取原地表；触及区块边缘向区块外扩展（最多32列）
            int leftGy = (seg > 0) ? origGy[seg - 1]
                    : boundaryGy(baseGroundLevel, amplitude, terrainNoise, biomeNoise, chunkX * SIZE + seg - 1, -1);
            int rightGy = (j < SIZE) ? origGy[j]
                    : boundaryGy(baseGroundLevel, amplitude, terrainNoise, biomeNoise, chunkX * SIZE + j, 1);
            int wl;
            if (leftGy >= 0 && rightGy >= 0) wl = Math.max(leftGy, rightGy);
            else if (leftGy >= 0) wl = leftGy;
            else if (rightGy >= 0) wl = rightGy;
            else wl = origGy[seg];                       // 两侧全为湖：fallback
            for (int k = seg; k < j; k++) {
                waterLine[k] = wl;
                // 判定5+6：每列随机深度 1~15（确定性随机，基于该列 x），湖底波浪起伏
                double rd = (resourceNoise.noise(chunkX * SIZE + k, 4.1) + 1) / 2;  // [0,1)
                int depth = 1 + (int) (rd * 15);
                // 比湖面高的部分全部挖掉 → 湖底 = 湖面+该列随机深度；原本更深则保持
                groundY[k] = Math.max(origGy[k], wl + depth);
                if (biome[k] != 2) biome[k] = 5;         // 湖底沙底
            }
            seg = j;
        }

        // 1) 逐列填充：地表 -> 泥土/沙 -> 石头 -> 深层岩
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            int gy = groundY[localX];
            int wl = waterLine[localX];                 // 该列水面线
            int b = biome[localX];
            boolean underwater = gy > wl;
            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;
                int depth = worldY - gy;
                if (worldY < gy) {
                    if (underwater && worldY >= wl) {
                        tiles[localX][localY] = WATER;
                        // Terraria 式液面：水面线那一格部分充盈，其下为满格
                        fluidLevel[localX][localY] = worldY == wl ? WATER_SURFACE_LEVEL : 0;
                    } else {
                        tiles[localX][localY] = AIR;
                    }
                } else if (depth == 0) {
                    tiles[localX][localY] = surfaceBlock(b, underwater);
                } else if (depth <= 4) {
                    tiles[localX][localY] = subBlock(b, underwater, worldX, worldY, resourceNoise, depth);
                } else if (depth <= 70) {
                    tiles[localX][localY] = stoneVariation(worldX, worldY, resourceNoise);
                } else {
                    tiles[localX][localY] = DEEPSLATE;
                }
            }
        }

        // 2) 洞穴 + 深层岩浆湖
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;
                int t = tiles[localX][localY];
                if ((t == STONE || t == DEEPSLATE) && worldY > groundY[localX] + 3) {
                    double cave = caveNoise.noise(worldX / 12.0, worldY / 12.0);
                    if (cave > 0.42) {
                        if (worldY > baseGroundLevel + 260) {
                            tiles[localX][localY] = LAVA;      // 深层岩浆湖
                        } else {
                            tiles[localX][localY] = AIR;
                            // 洞穴里偶尔长蘑菇
                            double mush = resourceNoise.noise(worldX * 3.7, worldY * 3.7);
                            if (mush > 0.9 && worldY > groundY[localX] + 6) {
                                tiles[localX][localY] = BROWN_MUSHROOM;
                            }
                        }
                    }
                }
            }
        }

        // 3) 矿脉（石头/深层岩中按深度分布）
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;
                int t = tiles[localX][localY];
                if (t != STONE && t != DEEPSLATE) continue;
                int depth = worldY - groundY[localX];
                if (depth < 3) continue;
                double r = (resourceNoise.noise(worldX * 1.3, worldY * 2.9) + 1) / 2; // [0,1)
                int ore = pickOre(depth, biome[localX], r);
                if (ore != t) tiles[localX][localY] = ore;
            }
        }

        // 4) 地表装饰：树 / 仙人掌 / 草 / 花 / 甘蔗
        //    注意：地表在本区块内的位置是 localGy = gy - chunkY*SIZE，
        //    不能取 tiles[localX][0]（那是区块顶部，几乎永远是空气，导致树永远种不出来）
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            int gy = groundY[localX];
            int b = biome[localX];
            int localGy = gy - chunkY * SIZE;              // 地表在本区块内的纵向位置
            if (localGy < 0 || localGy >= SIZE) continue;  // 地表不在这块：跳过
            int surface = tiles[localX][localGy];

            if (b == 1 || b == 0) {                        // 森林 / 平原：种树
                if (surface == GRASS) {
                    double tr = resourceNoise.noise(worldX * 0.6, 13.7);
                    // 阈值越低密度越大（tr∈[-1,1]）：森林 >0.05 ≈ 48% 列种树，平原 >0.3 ≈ 35%
                    boolean hasTree = b == 1 ? tr > 0.05 : tr > 0.30;
                    if (hasTree) {
                        // 平原只长小/标准橡树，森林随机选橡树系 6 种结构之一
                        int idx = pickShape(worldX, resourceNoise, 0, b == 1 ? 5 : 1);
                        placeStructure(localX, localGy, TREE_SHAPES[idx], OAK_LOG, OAK_LEAVES);
                    }
                }
            } else if (b == 3 && surface == SNOW_BLOCK) {  // 雪原：云杉
                double tr = resourceNoise.noise(worldX * 0.7, 17.3);
                if (tr > 0.25) {
                    int idx = 6 + pickShape(worldX, resourceNoise, 0, 3); // 云杉系 4 种结构之一
                    placeStructure(localX, localGy, TREE_SHAPES[idx], SPRUCE_LOG, SPRUCE_LEAVES);
                }
            } else if (b == 2 && surface == SAND) {        // 沙漠：仙人掌
                double tr = resourceNoise.noise(worldX * 0.9, 5.3);
                if (tr > 0.15) {                           // 阈值越低密度越大（约 42% 列）
                    int h = 2 + (int) ((resourceNoise.noise(worldX * 2.1, 9.9) + 1) * 1.0);
                    for (int i = 1; i <= h; i++) setIfAir(localX, localGy - i, CACTUS);
                }
            } else if (b == 5 && surface == SAND) {        // 沙滩：甘蔗（水边）
                double tr = resourceNoise.noise(worldX * 1.5, 2.2);
                if (tr > 0.8) {
                    for (int i = 1; i <= 2; i++) setIfAir(localX, localGy - i, SUGAR_CANE);
                }
            } else if ((b == 0 || b == 1) && surface == GRASS) { // 平原/森林：草与花
                double g = resourceNoise.noise(worldX * 1.9, 3.1);
                if (g > 0.70) {
                    setIfAir(localX, localGy - 1, TALL_GRASS);
                } else if (g > 0.64) {
                    setIfAir(localX, localGy - 1, FERN);
                } else if (g > 0.585) {
                    setIfAir(localX, localGy - 1,
                        ((int) ((resourceNoise.noise(worldX * 2.7, 8.8) + 1) * 2) % 2) == 0 ? DANDELION : POPPY);
                }
            }
        }

        generated = true;
        modified = false;
        saved = false;
    }

    /** 区块外某列是否为湖区（与生成时同一噪声判定） */
    private static boolean isLakeAt(PerlinNoise biomeNoise, int worldX) {
        return biomeNoise.noise(worldX / 90.0, -0.3) > 0.30;
    }

    /** 区块外某列的原地表高度（与生成时同一公式） */
    private static int origGyAt(int baseGroundLevel, int amplitude, PerlinNoise terrainNoise, int worldX) {
        double raw = terrainNoise.terrainHeight(worldX);
        double v = Math.signum(raw) * Math.pow(Math.abs(raw), 1.7);
        int gy = baseGroundLevel + (int) (v * amplitude);
        return Math.clamp(gy, 1, WORLD_HEIGHT - 5);
    }

    /**
     * 从 startX 起向 dir 方向（-1 左 / 1 右）扩展，找最近的非湖区列作为湖边界，
     * 最多扩展 32 列；全部为湖则返回 -1（由调用方 fallback）。
     */
    private static int boundaryGy(int baseGroundLevel, int amplitude, PerlinNoise terrainNoise,
                                  PerlinNoise biomeNoise, int startX, int dir) {
        for (int d = 0; d < 32; d++) {
            int wx = startX + dir * d;
            if (!isLakeAt(biomeNoise, wx)) {
                return origGyAt(baseGroundLevel, amplitude, terrainNoise, wx);
            }
        }
        return -1;
    }

    /** 只在目标位置为空气且不越界时放置 */
    private void setIfAir(int localX, int localY, int type) {
        if (localX < 0 || localX >= SIZE || localY < 0 || localY >= SIZE) return;
        if (tiles[localX][localY] == AIR) {
            tiles[localX][localY] = type;
        }
    }

    /**
     * 树结构模板库（10 种）：每行从顶到底，'.'=空气 'T'=树干 'L'=树叶。
     * 每行等宽、中心列为树干基准；地形生成最后阶段按群系随机挑一个直接复制上去。
     * 0-5 橡树系，6-9 云杉系。
     */
    private static final String[][] TREE_SHAPES = {
            // 0 小橡树（圆顶）
            {"..L..", ".LLL.", ".LLL.", "..T..", "..T.."},
            // 1 标准橡树（圆顶）
            {"...L...", "..LLL..", ".LLLLL.", ".LLLLL.", "..LLL..", "...T...", "...T..."},
            // 2 大橡树（大圆顶）
            {"....L....", "...LLL...", "..LLLLL..", ".LLLLLLL.", ".LLLLLLL.", "..LLLLL..",
             "...LLL...", "....T....", "....T....", "....T...."},
            // 3 胖橡树（宽圆顶）
            {"....L....", "..LLLLL..", ".LLLLLLL.", ".LLLLLLL.", ".LLLLLLL.", "..LLLLL..",
             "....T....", "....T...."},
            // 4 歪橡树（树冠偏左）
            {"..L....", ".LLL...", ".LLLL..", ".LLL...", "...T...", "...T..."},
            // 5 高橡树（细高）
            {"..L..", ".LLL.", ".LLL.", "..L..", "..T..", "..T..", "..T..", "..T..", "..T.."},
            // 6 小云杉（塔形）
            {"..L..", ".LLL.", ".LLL.", ".LLL.", "..T..", "..T.."},
            // 7 标准云杉（塔形，顶窄底宽）
            {"...L...", "..LLL..", "..LLL..", ".LLLLL.", ".LLLLL.", ".LLLLL.", "...T...", "...T..."},
            // 8 大云杉（高塔）
            {"....L....", "...LLL...", "...LLL...", "..LLLLL..", "..LLLLL..", ".LLLLLLL.",
             ".LLLLLLL.", ".LLLLLLL.", "....T....", "....T...."},
            // 9 尖云杉（尖塔）
            {"..L..", "..L..", ".LLL.", ".LLL.", ".LLL.", ".LLL.", "..T..", "..T..", "..T.."},
    };

    /** 按模板在 (lx, localGy) 处复制种树：模板底行贴在距地表上一格，逐格 setIfAir */
    private void placeStructure(int lx, int localGy, String[] shape, int trunk, int leaf) {
        int rows = shape.length;
        if (localGy < rows) return;               // 顶部超出世界高度：跳过
        for (int dy = 0; dy < rows; dy++) {
            int y = localGy - rows + dy;          // 顶行 localGy-rows，底行 localGy-1（贴地）
            String row = shape[dy];
            int off = (row.length() - 1) / 2;     // 中心列
            for (int dx = 0; dx < row.length(); dx++) {
                char c = row.charAt(dx);
                int tile = c == 'T' ? trunk : (c == 'L' ? leaf : 0);
                if (tile != 0) setIfAir(lx + dx - off, y, tile);
            }
        }
    }

    /** 用确定性噪声从 [lo, hi] 选一个模板下标（同一列每次生成结果一致） */
    private int pickShape(int worldX, PerlinNoise resourceNoise, int lo, int hi) {
        double sel = (resourceNoise.noise(worldX * 3.3, 7.7) + 1) / 2;  // [0,1)
        int span = hi - lo + 1;
        int i = (int) (sel * span);
        return lo + Math.min(i, span - 1);
    }

    /** 地表方块（按群系/是否水下） */
    private int surfaceBlock(int biome, boolean underwater) {
        if (underwater) return SAND;               // 水下 -> 沙
        switch (biome) {
            case 2: return SAND;                   // 沙漠
            case 3: return SNOW_BLOCK;             // 雪原
            case 4: return STONE;                  // 山地
            case 5: return SAND;                   // 沙滩
            default: return GRASS;                 // 平原/森林
        }
    }

    /** 地表下 1~4 格（泥土/沙/砂石） */
    private int subBlock(int biome, boolean underwater, int worldX, int worldY,
                         PerlinNoise resourceNoise, int depth) {
        if (biome == 2) return depth <= 3 ? SAND : SANDSTONE;          // 沙漠
        if (biome == 4) return depth <= 2 ? STONE : STONE;              // 山地全是石头
        if (underwater) {
            double g = resourceNoise.noise(worldX * 2.3, worldY * 2.3);
            if (g > 0.62) return GRAVEL;
            if (g > 0.58) return CLAY;
            return SAND;
        }
        return DIRT;
    }

    /** 石头层夹杂岩石变种 */
    private int stoneVariation(int worldX, int worldY, PerlinNoise resourceNoise) {
        double v = resourceNoise.noise(worldX / 6.0, worldY / 6.0);
        if (v > 0.62) return GRANITE;
        if (v > 0.55) return DIORITE;
        if (v > 0.48) return ANDESITE;
        if (v > 0.42) return TUFF;
        return STONE;
    }

    /** 按深度与群系挑选矿石（r 为 [0,1) 随机数，稀有矿石优先） */
    private int pickOre(int depth, int biome, double r) {
        boolean mountain = biome == 4;
        if (mountain && r < 0.015 && depth > 8 && depth < 45) return EMERALD_ORE;
        if (r < 0.02 && depth > 55) return DIAMOND_ORE;
        if (r < 0.04 && depth > 35) return REDSTONE_ORE;
        if (r < 0.06 && depth > 18 && depth < 80) return GOLD_ORE;
        if (r < 0.08 && depth > 12 && depth < 50) return LAPIS_ORE;
        if (r < 0.13 && depth > 8 && depth < 130) return IRON_ORE;
        if (r < 0.20 && depth < 75) return COAL_ORE;
        if (r < 0.26 && depth < 60) return COPPER_ORE;
        return -1;
    }

    public boolean isGenerated() { return generated; }
    public boolean isSaved() { return saved; }
    public boolean isModified() { return modified; }
    public void markSaved() { this.saved = true; this.modified = false; }

    public int getTile(int localX, int localY) {
        if (localX < 0 || localX >= SIZE || localY < 0 || localY >= SIZE)
            return EMPTY_TILE;
        return tiles[localX][localY];
    }

    public void setTile(int localX, int localY, int type) {
        if (localX >= 0 && localX < SIZE && localY >= 0 && localY < SIZE) {
            tiles[localX][localY] = type;
            if (!isFluid(type)) fluidLevel[localX][localY] = 0;
            modified = true;
        }
    }

    /** 取流体水位（越界或非流体格返回 0）。 */
    public int getFluidLevel(int localX, int localY) {
        if (localX < 0 || localX >= SIZE || localY < 0 || localY >= SIZE)
            return 0;
        return fluidLevel[localX][localY];
    }

    /** 设置流体水位（越界忽略；钳制到 0~MAX_FLUID_LEVEL）。 */
    public void setFluidLevel(int localX, int localY, int level) {
        if (localX >= 0 && localX < SIZE && localY >= 0 && localY < SIZE) {
            fluidLevel[localX][localY] = Math.max(0, Math.min(level, MAX_FLUID_LEVEL));
            modified = true;
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        boolean allAir = true;
        for (int localX = 0; localX < SIZE; localX++) {
            for (int localY = 0; localY < SIZE; localY++) {
                if (localY > 0 || localX > 0) sb.append('|');
                if (tiles[localX][localY] == AIR) {
                    sb.append("null");
                } else {
                    sb.append(tiles[localX][localY]);
                    allAir = false;
                }
            }
            sb.append('\n');
        }
        if (allAir && !modified) return null;
        // 流体水位段：16 行（外层 localX），与 tiles 同结构（跨行连续 | 分隔，
        // 与 loadWorld 删除换行后的解析保持一致）
        for (int localX = 0; localX < SIZE; localX++) {
            for (int localY = 0; localY < SIZE; localY++) {
                if (localY > 0 || localX > 0) sb.append('|');
                sb.append(fluidLevel[localX][localY]);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static Chunk deserialize(String data) {
        return deserialize(data, false);
    }

    /**
     * 从存档文本恢复区块。
     *
     * @param migrateLegacy 是否为旧版存档（旧版方块 ID 1~6 需迁移到 Minecraft 原版 ID）
     * @return 解析成功返回区块；数据损坏（越界/非法数字）返回 null，由上层丢弃并重新生成
     */
    public static Chunk deserialize(String data, boolean migrateLegacy) {
        Chunk chunk = new Chunk();
        try {
            // 分隔符：| 或任意换行（存档可能：方块段前导 | 跨行、流体段无前导 | 仅换行分隔）
            String[] parts = data.split("[|\\r\\n]+");
            int idx = 0;
            for (int localX = 0; localX < SIZE; localX++) {
                for (int localY = 0; localY < SIZE; localY++) {
                    String part = parts[idx++];
                    if ("null".equals(part)) {
                        chunk.tiles[localX][localY] = AIR;
                    } else {
                        int v = Integer.parseInt(part);
                        chunk.tiles[localX][localY] = migrateLegacy ? migrateTile(v) : v;
                    }
                }
            }
            // 流体水位段（旧档无该段 -> 全 0 = 满格源）
            if (parts.length > SIZE * SIZE) {
                for (int localX = 0; localX < SIZE; localX++) {
                    for (int localY = 0; localY < SIZE; localY++) {
                        chunk.fluidLevel[localX][localY] = Integer.parseInt(parts[idx++]);
                    }
                }
            }
        } catch (RuntimeException e) {
            return null; // 存档损坏（旧版粘连/越界）：放弃该区块，交由上层重新生成
        }
        chunk.generated = true;
        chunk.modified = true;
        chunk.saved = true;
        return chunk;
    }

    /** 旧版方块 ID -> 新版（Minecraft 原版 ID）一次性迁移表（旧版 GRASS=1 ~ FOREST=6）。 */
    private static int migrateTile(int id) {
        switch (id) {
            case 1:  return GRASS;   // 旧 grass  -> grass_block(8)
            case 2:  return DIRT;    // 旧 dirt   -> dirt(9)
            case 3:  return STONE;   // 旧 stone  -> stone(1)
            case 4:  return WATER;   // 旧 water  -> water(32)
            case 5:  return SAND;    // 旧 sand   -> sand(34)
            case 6:  return FOREST;  // 旧 forest -> oak_log(46)
            default: return id;
        }
    }
}
