package main.world;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无限地图：按区块加载/生成，按格子查询。
 * 纯逻辑类，不依赖任何渲染库。
 */
public class InfiniteMap {
    protected final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final long worldSeed;
    private final String worldDirPath;

    private final ExecutorService saveExecutor;
    private final AtomicInteger pendingSaveTasks = new AtomicInteger(0);

    public final long terrainSeed, caveSeed, biomeSeed, resourceSeed;
    private final PerlinNoise terrainNoise, caveNoise, biomeNoise, resourceNoise;
    /** 旧版存档（方块 ID 1~6）迁移标记 */
    protected final boolean migrateLegacy;

    public InfiniteMap(long seed, String worldName) {
        this(seed, worldName, false);
    }

    /**
     * @param migrateLegacy 该世界是否为旧版存档（旧版方块 ID 需迁移到 Minecraft 原版 ID）
     */
    public InfiniteMap(long seed, String worldName, boolean migrateLegacy) {
        this.worldSeed = seed;
        this.migrateLegacy = migrateLegacy;
        long[] subSeeds = SeedExpander.expand(seed);
        this.terrainSeed  = subSeeds[0];
        this.caveSeed     = subSeeds[1];
        this.biomeSeed    = subSeeds[2];
        this.resourceSeed = subSeeds[3];

        this.terrainNoise  = new PerlinNoise(terrainSeed);
        this.caveNoise     = new PerlinNoise(caveSeed);
        this.biomeNoise    = new PerlinNoise(biomeSeed);
        this.resourceNoise = new PerlinNoise(resourceSeed);

        this.worldDirPath = "world/" + sanitizeName(worldName);

        this.saveExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ChunkSaveThread");
            t.setDaemon(true);
            return t;
        });
    }

    private static String sanitizeName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static final int MAX_CHUNKS = 256;

    /**
     * 获取世界保存路径。
     */
    public String getWorldDirPath() {
        return worldDirPath;
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public Chunk getChunk(ChunkPos pos) {
        Chunk result = chunks.computeIfAbsent(pos, this::loadOrGenerateChunk);
        enforceChunkLimit();
        return result;
    }

    /**
     * 加载或生成单个区块（不放入缓存，由调用方负责）。
     * 子类可覆写此方法以接入异步/多线程生成。
     */
    protected Chunk loadOrGenerateChunk(ChunkPos pos) {
        Chunk loaded = loadChunkFile(pos);
        if (loaded != null) return loaded;
        Chunk c = new Chunk();
        c.generate(worldSeed, pos.cx, pos.cy, terrainNoise, caveNoise, biomeNoise, resourceNoise);
        return c;
    }

    public int getTileType(int tileX, int tileY) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        return chunk.getTile(localX, localY);
    }

    public void setTileType(int tileX, int tileY, int type) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        chunk.setTile(localX, localY, type);
    }

    /** 取流体水位（格子缺失/越界返回 0）。 */
    public int getFluidLevel(int tileX, int tileY) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        return chunk.getFluidLevel(localX, localY);
    }

    /** 设置流体水位（非流体方块上无效）。 */
    public void setFluidLevel(int tileX, int tileY, int level) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        chunk.setFluidLevel(localX, localY, level);
    }

    /** 同时设置方块类型与流体水位（非流体类型强制水位 0）。 */
    public void setTileTypeAndLevel(int tileX, int tileY, int type, int level) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        chunk.setTile(localX, localY, type);
        chunk.setFluidLevel(localX, localY, Chunk.isFluid(type) ? level : 0);
    }

    /** 区块是否已加载在缓存中（未加载返回 false，读取不会触发生成）。 */
    public boolean isChunkLoaded(int cx, int cy) {
        return chunks.containsKey(new ChunkPos(cx, cy));
    }

    /** 已加载区块位置快照（供流体模拟等只遍历已加载区块的逻辑使用）。 */
    public Set<ChunkPos> loadedChunkKeys() {
        return new java.util.HashSet<>(chunks.keySet());
    }

    public void unloadFarChunks(int playerChunkX, int playerChunkY, int viewDistance) {
        chunks.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            int dx = Math.abs(pos.cx - playerChunkX);
            int dy = Math.abs(pos.cy - playerChunkY);
            return dx > viewDistance || dy > viewDistance;
        });
    }

    protected void enforceChunkLimit() {
        int over = chunks.size() - MAX_CHUNKS;
        if (over <= 0) return;
        int before = chunks.size();

        List<Map.Entry<ChunkPos, Chunk>> evictable = chunks.entrySet().stream()
                .filter(e -> e.getValue().isSaved() && !e.getValue().isModified())
                .limit(over)
                .toList();

        for (Map.Entry<ChunkPos, Chunk> entry : evictable) {
            Chunk chunk = entry.getValue();
            if (chunk.isModified()) {
                saveChunkFile(entry.getKey(), chunk);
                chunk.markSaved();
            }
        }

        chunks.entrySet().removeAll(evictable);
        int removed = before - chunks.size();
        if (removed > 0) {
            System.out.println("内存上限: 已驱逐 " + removed + " 个区块 (" + chunks.size() + "/" + MAX_CHUNKS + ")");
        }
    }

    public void unloadDistantSavedChunks(int playerChunkX, int playerChunkY, int radius) {
        int before = chunks.size();
        chunks.entrySet().removeIf(entry -> {
            Chunk chunk = entry.getValue();
            ChunkPos pos = entry.getKey();
            int dx = Math.abs(pos.cx - playerChunkX);
            int dy = Math.abs(pos.cy - playerChunkY);
            return chunk.isSaved() && !chunk.isModified()
                    && (dx > radius || dy > radius);
        });
        int removed = before - chunks.size();
        if (removed > 0) {
            System.out.println("内存卸载: " + removed + " 个区块 (剩余 " + chunks.size() + ")");
        }
    }

    public void saveAndUnloadOutsideRadius(int playerChunkX, int playerChunkY, int radius) {
        pendingSaveTasks.incrementAndGet();

        final List<Map.Entry<ChunkPos, Chunk>> toSave = new ArrayList<>();
        final List<ChunkPos> toRemove = new ArrayList<>();

        for (Map.Entry<ChunkPos, Chunk> entry : chunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            Chunk chunk = entry.getValue();
            int dx = Math.abs(pos.cx - playerChunkX);
            int dy = Math.abs(pos.cy - playerChunkY);
            if (dx > radius || dy > radius) {
                if (chunk.isGenerated() && (chunk.isModified() || !chunk.isSaved())) {
                    toSave.add(entry);
                }
                toRemove.add(pos);
            }
        }

        saveExecutor.submit(() -> {
            try {
                for (Map.Entry<ChunkPos, Chunk> entry : toSave) {
                    saveChunkFile(entry.getKey(), entry.getValue());
                    entry.getValue().markSaved();
                }
                for (ChunkPos pos : toRemove) {
                    chunks.remove(pos);
                }
                if (!toRemove.isEmpty()) {
                    System.out.println("即时保存并卸载区块: " + toRemove.size() + " 个 (剩余 " + chunks.size() + ")");
                }
            } finally {
                pendingSaveTasks.decrementAndGet();
            }
        });
    }

    private Chunk loadChunkFile(ChunkPos pos) {
        File worldDir = new File(worldDirPath);
        if (!worldDir.exists()) return null;
        File file = new File(worldDir, pos.cx + "_" + pos.cy + ".txt");
        if (!file.exists()) return null;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (content.length() > 0) {
                return Chunk.deserialize(content, migrateLegacy);
            }
        } catch (IOException e) {
            System.err.println("加载区块失败 " + pos + ": " + e.getMessage());
        }
        return null;
    }

    public void saveWorld() {
        pendingSaveTasks.incrementAndGet();

        final List<Map.Entry<ChunkPos, Chunk>> snapshot = new ArrayList<>(chunks.entrySet());
        final String saveDirPath = worldDirPath;

        saveExecutor.submit(() -> {
            try {
                File worldDir = new File(saveDirPath);
                if (!worldDir.exists()) {
                    worldDir.mkdirs();
                }
                int saved = 0;
                for (Map.Entry<ChunkPos, Chunk> entry : snapshot) {
                    ChunkPos pos = entry.getKey();
                    Chunk chunk = entry.getValue();
                    if (!chunk.isGenerated()) continue;
                    String data = chunk.serialize();
                    if (data == null) continue;
                    File file = new File(worldDir, pos.cx + "_" + pos.cy + ".txt");
                    try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(file), StandardCharsets.UTF_8))) {
                        writer.write(data);
                        writer.flush();
                        chunk.markSaved();
                        saved++;
                    } catch (IOException e) {
                        System.err.println("保存区块失败 " + pos + ": " + e.getMessage());
                    }
                }
                System.out.println("世界已保存: " + saved + "/" + snapshot.size() + " 个区块 → " + saveDirPath);
            } finally {
                pendingSaveTasks.decrementAndGet();
            }
        });
    }

    private void saveChunkFile(ChunkPos pos, Chunk chunk) {
        File worldDir = new File(worldDirPath);
        if (!worldDir.exists()) worldDir.mkdirs();
        String data = chunk.serialize();
        if (data == null) return;
        File file = new File(worldDir, pos.cx + "_" + pos.cy + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(data);
            writer.flush();
        } catch (IOException e) {
            System.err.println("保存区块失败 " + pos + ": " + e.getMessage());
        }
    }

    public void loadWorld() {
        File worldDir = new File(worldDirPath);
        if (!worldDir.exists() || !worldDir.isDirectory()) return;
        File[] files = worldDir.listFiles((dir, name) -> name.matches("-?\\d+_-?\\d+\\.txt"));
        if (files == null) return;
        int loaded = 0;
        for (File file : files) {
            String name = file.getName();
            String baseName = name.substring(0, name.length() - 4);
            String[] parts = baseName.split("_");
            try {
                int cx = Integer.parseInt(parts[0]);
                int cy = Integer.parseInt(parts[1]);
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                if (content.length() > 0) {
                    Chunk chunk = Chunk.deserialize(content, migrateLegacy);
                    if (chunk == null) {
                        System.out.println("区块数据损坏，重新生成: " + baseName);
                        continue;
                    }
                    chunks.put(new ChunkPos(cx, cy), chunk);
                    loaded++;
                }
            } catch (IOException e) {
                System.err.println("加载区块失败 " + baseName + ": " + e.getMessage());
            }
        }
        System.out.println("世界已加载: " + loaded + " 个区块");
    }

    public boolean waitForSaveCompletion(long timeoutMs) {
        long startTime = System.currentTimeMillis();
        while (pendingSaveTasks.get() > 0) {
            if (timeoutMs > 0) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= timeoutMs) {
                    System.out.println("等待保存任务超时: 已等待 " + elapsed + "ms, 剩余任务: " + pendingSaveTasks.get());
                    return false;
                }
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    public void shutdown() {
        saveExecutor.shutdown();
        try {
            if (!saveExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("保存线程池未能在5秒内关闭，强制关闭");
                saveExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            saveExecutor.shutdownNow();
        }
    }

    public void savePlayerData(double playerX, double playerY, List<String> inventoryData) {
        File worldDir = new File(worldDirPath);
        if (!worldDir.exists()) {
            worldDir.mkdirs();
        }
        File file = new File(worldDir, "player.txt");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(playerX + "|" + playerY);
            writer.newLine();
            for (String item : inventoryData) {
                writer.write(item);
                writer.newLine();
            }
            writer.flush();
            System.out.println("玩家数据已保存: (" + playerX + ", " + playerY + "), 背包物品: " + inventoryData.size());
        } catch (IOException e) {
            System.err.println("保存玩家数据失败: " + e.getMessage());
        }
    }

    public PlayerData loadPlayerData() {
        File file = new File(worldDirPath, "player.txt");
        if (!file.exists()) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) return null;
            String[] coords = lines.get(0).split("\\|");
            if (coords.length < 2) return null;
            double playerX = Double.parseDouble(coords[0]);
            double playerY = Double.parseDouble(coords[1]);
            List<String> inventoryData = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                inventoryData.add(lines.get(i));
            }
            System.out.println("玩家数据已加载: (" + playerX + ", " + playerY + "), 背包物品: " + inventoryData.size());
            return new PlayerData(playerX, playerY, inventoryData);
        } catch (IOException e) {
            System.err.println("加载玩家数据失败: " + e.getMessage());
            return null;
        }
    }

    public static class PlayerData {
        public final double playerX;
        public final double playerY;
        public final List<String> inventoryData;

        public PlayerData(double playerX, double playerY, List<String> inventoryData) {
            this.playerX = playerX;
            this.playerY = playerY;
            this.inventoryData = inventoryData;
        }
    }
}
