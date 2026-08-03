package main.world;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 无限地图：按区块加载/生成，按格子查询。
 */
public class InfiniteMap {
    private final Map<ChunkPos, Chunk> chunks = new ConcurrentHashMap<>();
    private final long worldSeed;
    private final String worldDirPath;

    /** 区块保存线程池 */
    private final ExecutorService saveExecutor;
    /** 正在进行的保存任务计数 */
    private final AtomicInteger pendingSaveTasks = new AtomicInteger(0);

    /** 4 个二级种子 */
    public final long terrainSeed, caveSeed, biomeSeed, resourceSeed;
    /** 4 个独立噪声实例 */
    private final PerlinNoise terrainNoise, caveNoise, biomeNoise, resourceNoise;

    public InfiniteMap(long seed, String worldName) {
        this.worldSeed = seed;
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

        // 创建单线程的保存线程池，确保保存操作按顺序执行
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
     * 获取或生成指定区块。
     */
    public Chunk getChunk(ChunkPos pos) {
        Chunk result = chunks.computeIfAbsent(pos, p -> {
            // 优先尝试从磁盘加载已保存区块文件，其次按噪声生成新区块
            Chunk loaded = loadChunkFile(p);
            if (loaded != null) return loaded;
            Chunk c = new Chunk();
            c.generate(worldSeed, p.cx, p.cy, terrainNoise, caveNoise, biomeNoise, resourceNoise);
            return c;
        });
        enforceChunkLimit();
        return result;
    }

    /**
     * 根据世界格子坐标获取 tile 类型。
     * @param tileX 世界列索引（格子坐标）
     * @param tileY 世界行索引（格子坐标）
     * @return tile 类型 ID
     */
    public int getTileType(int tileX, int tileY) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        return chunk.getTile(localX, localY);
    }

    /**
     * 设置世界指定格子坐标的方块类型。
     *
     * @param tileX 世界列索引（格子坐标）
     * @param tileY 世界行索引（格子坐标）
     * @param type  方块类型 ID
     */
    public void setTileType(int tileX, int tileY, int type) {
        int cx = Math.floorDiv(tileX, Chunk.SIZE);
        int cy = Math.floorDiv(tileY, Chunk.SIZE);
        int localX = Math.floorMod(tileX, Chunk.SIZE);
        int localY = Math.floorMod(tileY, Chunk.SIZE);

        Chunk chunk = getChunk(new ChunkPos(cx, cy));
        chunk.setTile(localX, localY, type);
    }

    /**
     * 卸载远离玩家的区块（可选，后期优化时使用）
     */
    public void unloadFarChunks(int playerChunkX, int playerChunkY, int viewDistance) {
        chunks.entrySet().removeIf(entry -> {
            ChunkPos pos = entry.getKey();
            int dx = Math.abs(pos.cx - playerChunkX);
            int dy = Math.abs(pos.cy - playerChunkY);
            return dx > viewDistance || dy > viewDistance;
        });
    }

    /**
     * 当内存区块超过 MAX_CHUNKS 时，强制驱逐已保存且未修改的区块。
     */
    private void enforceChunkLimit() {
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

    /**
     * 卸载「已保存到磁盘」且「未再次修改」且「距玩家超过 radius 个区块」的区块。
     * 应在每次 saveWorld() 之后调用，避免频繁重新生成。
     *
     * @param playerChunkX 玩家所在区块 X
     * @param playerChunkY 玩家所在区块 Y
     * @param radius       保留半径（区块单位），推荐 8
     */
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

    /**
     * 异步保存并卸载超出玩家半径的区块。
     * 保存规则：若区块已生成且被修改或未标记为已保存，则写入磁盘；随后从内存中移除该区块。
     * 该方法在玩家跨区块移动时调用，替代定时保存策略。
     * 
     * <p>该方法会立即返回，实际的保存和卸载操作在后台线程中执行。</p>
     */
    public void saveAndUnloadOutsideRadius(int playerChunkX, int playerChunkY, int radius) {
        pendingSaveTasks.incrementAndGet();
        
        // 创建需要保存的区块列表快照
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
        
        final int playerCX = playerChunkX;
        final int playerCY = playerChunkY;
        final int r = radius;
        
        saveExecutor.submit(() -> {
            try {
                // 执行保存操作
                for (Map.Entry<ChunkPos, Chunk> entry : toSave) {
                    saveChunkFile(entry.getKey(), entry.getValue());
                    entry.getValue().markSaved();
                }
                
                // 执行卸载操作
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

    /**
     * 尝试从 world 目录读取指定区块的保存文件并反序列化。
     * @return 若文件存在且可反序列化则返回 Chunk，否则返回 null
     */
    private Chunk loadChunkFile(ChunkPos pos) {
        File worldDir = new File(worldDirPath);
        if (!worldDir.exists()) return null;
        File file = new File(worldDir, pos.cx + "_" + pos.cy + ".txt");
        if (!file.exists()) return null;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
                                  .replace("\r\n", "")
                                  .replace("\n", "")
                                  .replace("\r", "");
            if (content.length() > 0) {
                Chunk chunk = Chunk.deserialize(content);
                return chunk;
            }
        } catch (IOException e) {
            System.err.println("加载区块失败 " + pos + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * 异步保存所有已加载区块到 world/ 目录。
     * 每个区块保存为 world/{cx}_{cy}.txt。
     * 全空气区块不保存。
     * 
     * <p>该方法会立即返回，实际的保存操作在后台线程中执行。</p>
     */
    public void saveWorld() {
        pendingSaveTasks.incrementAndGet();
        
        // 创建当前区块数据的快照，避免保存过程中数据被修改
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

    /**
     * 从 world/ 目录加载已保存的区块。
     * 覆盖同坐标区块，保留噪声生成器以便新区块按需生成。
     */
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
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8)
                                      .replace("\r\n", "")
                                      .replace("\n", "")
                                      .replace("\r", "");
                if (content.length() > 0) {
                    Chunk chunk = Chunk.deserialize(content);
                    chunks.put(new ChunkPos(cx, cy), chunk);
                    loaded++;
                }
            } catch (IOException e) {
                System.err.println("加载区块失败 " + baseName + ": " + e.getMessage());
            }
        }
        System.out.println("世界已加载: " + loaded + " 个区块");
    }

    /**
     * 等待所有待处理的保存任务完成。
     * 
     * <p>该方法会阻塞当前线程，直到所有异步保存任务都执行完毕。
     * 通常在游戏退出或保存世界后需要确保数据完整性时调用。</p>
     * 
     * @param timeoutMs 超时时间（毫秒），0表示无限等待
     * @return true 如果所有任务在超时前完成，false 如果超时
     */
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
                System.err.println("等待保存任务时被中断");
                return false;
            }
        }
        
        return true;
    }

    /**
     * 关闭保存线程池。
     * 
     * <p>在游戏退出时调用，确保所有待保存的数据都被写入磁盘。</p>
     */
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

    // ==================== 玩家数据保存/加载 ====================

    /**
     * 保存玩家数据到 player.txt。
     * 
     * <p>文件格式：</p>
     * <pre>
     * 第一行：玩家X坐标|玩家Y坐标
     * 第二行开始：物品名|数量（每行一个物品槽，空槽为空行）
     * </pre>
     * 
     * @param playerX 玩家X坐标（像素）
     * @param playerY 玩家Y坐标（像素）
     * @param inventoryData 背包数据，格式为 "物品名|数量" 的列表
     */
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

    /**
     * 从 player.txt 加载玩家数据。
     * 
     * @return 玩家数据对象，包含坐标和背包数据；若文件不存在返回 null
     */
    public PlayerData loadPlayerData() {
        File file = new File(worldDirPath, "player.txt");
        if (!file.exists()) {
            return null;
        }
        
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return null;
            }
            
            String[] coords = lines.get(0).split("\\|");
            if (coords.length < 2) {
                return null;
            }
            
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

    /**
     * 玩家数据容器类。
     */
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