package main.world;

/**
 * 横版 2D 地形区块，使用分形柏林噪声生成地表并填充固体/空气。
 * 世界总高度固定为 1024 格（0～1023），超出部分自动设为空气。
 */
public class Chunk {
    public static final int SIZE = 16;               // 区块边长（tile 数）
    public static final int WORLD_HEIGHT = 1024;     // 世界最大高度（tile 数）
    public static final int EMPTY_TILE = 0;

    // 地形类型常量
    public static final int AIR    = 0;
    public static final int GRASS  = 1;
    public static final int DIRT   = 2;
    public static final int STONE  = 3;
    public static final int WATER  = 4;
    public static final int SAND   = 5;
    public static final int FOREST = 6;

    private final int[][] tiles = new int[SIZE][SIZE];
    private boolean generated = false;
    private boolean modified = false;
    private boolean saved = false;

    /**
     * 使用柏林噪声生成地表，并填充空气、草地、泥土和石头。
     *
     * @param worldSeed     世界种子
     * @param chunkX        区块 X 坐标（区块单位）
     * @param chunkY        区块 Y 坐标（区块单位）
     * @param terrainNoise  地形高度噪声
     * @param caveNoise     洞穴噪声
     * @param biomeNoise    生物群系噪声
     * @param resourceNoise 资源分布噪声
     */
    public void generate(long worldSeed, int chunkX, int chunkY,
                      PerlinNoise terrainNoise, PerlinNoise caveNoise,
                      PerlinNoise biomeNoise, PerlinNoise resourceNoise) {
        int baseGroundLevel = WORLD_HEIGHT / 2;
        int amplitude = 180;

        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;

            double noiseVal = terrainNoise.terrainHeight(worldX);
            int groundY = baseGroundLevel + (int)(noiseVal * amplitude);
            groundY = Math.clamp(groundY, 1, WORLD_HEIGHT - 5);

            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;

                if (worldY < 0 || worldY >= WORLD_HEIGHT) {
                    tiles[localX][localY] = AIR;
                    continue;
                }

                if (worldY > groundY) {
                    int depth = worldY - groundY;
                    tiles[localX][localY] = depth <= 4 ? DIRT : STONE;
                } else if (worldY == groundY) {
                    tiles[localX][localY] = GRASS;
                } else {
                    tiles[localX][localY] = AIR;
                }
            }
        }

        addCaves(chunkX, chunkY, caveNoise);
        applyBiomeSurface(chunkX, chunkY, biomeNoise);
        placeResources(chunkX, chunkY, resourceNoise);
        generated = true;
        modified = false;
        saved = false;
    }

    /**
     * 在石头层中根据噪声挖出洞穴。
     */
    private void addCaves(int chunkX, int chunkY, PerlinNoise caveNoise) {
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;
                if (worldY >= 0 && worldY < WORLD_HEIGHT && tiles[localX][localY] == STONE) {
                    double caveNoiseVal = caveNoise.noise(worldX / 12.0, worldY / 12.0);
                    if (caveNoiseVal > 0.4) {
                        tiles[localX][localY] = AIR;
                    }
                }
            }
        }
    }

    /**
     * 根据生物群系噪声调整地表方块：在特定区域将草地替换为沙地或森林。
     */
    private void applyBiomeSurface(int chunkX, int chunkY, PerlinNoise biomeNoise) {
        for (int localX = 0; localX < SIZE; localX++) {
            int worldX = chunkX * SIZE + localX;
            for (int localY = 0; localY < SIZE; localY++) {
                int worldY = chunkY * SIZE + localY;
                if (worldY < 0 || worldY >= WORLD_HEIGHT) continue;
                if (tiles[localX][localY] != GRASS) continue;

                double biomeVal = biomeNoise.noise(worldX / 80.0, worldY / 40.0);
                if (biomeVal > 0.5) {
                    tiles[localX][localY] = FOREST;
                } else if (biomeVal < -0.5) {
                    tiles[localX][localY] = SAND;
                }
            }
        }
    }

    /**
     * 资源分布占位：后续在此处用 resourceNoise 放置矿石。
     * 4 个二级种子架构已就绪，待定义 ORE 类型后激活替换逻辑。
     */
    private void placeResources(int chunkX, int chunkY, PerlinNoise resourceNoise) {
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

    /**
     * 设置区块内指定位置的方块类型。
     *
     * @param localX 区块内 X 坐标（0~SIZE-1）
     * @param localY 区块内 Y 坐标（0~SIZE-1）
     * @param type   方块类型 ID
     */
    public void setTile(int localX, int localY, int type) {
        if (localX >= 0 && localX < SIZE && localY >= 0 && localY < SIZE) {
            tiles[localX][localY] = type;
            modified = true;
        }
    }

    /**
     * 序列化当前区块数据为字符串。
     * 格式：按 localX 优先、localY 其次的顺序，每格方块类型数字以 | 分隔，空气写为 null。
     * 若全为空气则返回 null。
     */
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
        return sb.toString();
    }

    /**
     * 从字符串反序列化恢复区块数据。
     *
     * @param data serialize() 输出的字符串，格式为 "1|null|2|..."
     * @return 恢复后的区块实例
     */
    public static Chunk deserialize(String data) {
        Chunk chunk = new Chunk();
        String[] parts = data.split("\\|");
        int idx = 0;
        for (int localX = 0; localX < SIZE; localX++) {
            for (int localY = 0; localY < SIZE; localY++) {
                String part = parts[idx++];
                if ("null".equals(part)) {
                    chunk.tiles[localX][localY] = AIR;
                } else {
                    chunk.tiles[localX][localY] = Integer.parseInt(part);
                }
            }
        }
        chunk.generated = true;
        chunk.modified = true;
        chunk.saved = true;
        return chunk;
    }
}