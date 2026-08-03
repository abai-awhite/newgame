package server;

import entity.DropItem;
import main.world.Chunk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 世界权威核心：持有地图、掉落物与全部玩家档案。
 *
 * <p>职责（服务器只做权威存储与同步，不跑逐玩家物理）：</p>
 * <ul>
 *   <li>区块数据权威（多线程生成 + 存档）</li>
 *   <li>方块变更应用与广播（接收前端意图，写入权威地图）</li>
 *   <li>玩家档案管理（位置/背包存储 + 持久化）</li>
 *   <li>掉落物权威列表</li>
 * </ul>
 */
public class WorldCore {

    public static final int TILE_SIZE = 32;

    /** 地图引用（权威区块数据） */
    public final ServerInfiniteMap map;

    /** 玩家档案：playerId -> PlayerProfile（多玩家） */
    private final Map<String, PlayerProfile> players = new ConcurrentHashMap<>();

    /** 掉落物（全局，破坏方块产生）：id -> DropItem（广播线程读，主线程写） */
    private final Map<Integer, DropItem> dropItems = new ConcurrentHashMap<>();
    private final AtomicInteger dropIdCounter = new AtomicInteger(0);

    /** 最近一次 tick 产生的方块变化（{x, y, oldType, newType}） */
    private volatile List<int[]> lastChanges = List.of();

    private final String worldDirPath;

    public WorldCore(long seed, String worldName, int chunkThreads) {
        boolean legacy = WorldStore.ensureMetaVersion(worldName);
        map = new ServerInfiniteMap(seed, worldName, chunkThreads, legacy);
        map.loadWorld();
        this.worldDirPath = map.getWorldDirPath();
    }

    // ==================== 玩家管理 ====================

    /** 添加玩家档案（连接进入时）。若存在历史存档则恢复。 */
    public PlayerProfile addPlayer(String playerId) {
        PlayerProfile profile = loadPlayerFile(playerId);
        if (profile == null) {
            profile = new PlayerProfile(playerId);
            profile.initDefaultInventory();
        }
        players.put(playerId, profile);
        System.out.println("玩家加入: " + playerId + " (" + profile.name + ")");
        return profile;
    }

    /** 移除玩家档案（连接断开时），返回被移除的玩家。 */
    public PlayerProfile removePlayer(String playerId) {
        PlayerProfile removed = players.remove(playerId);
        if (removed != null) {
            savePlayerFile(playerId, removed);
            System.out.println("玩家离开: " + playerId);
        }
        return removed;
    }

    public PlayerProfile getPlayer(String playerId) {
        return players.get(playerId);
    }

    public Map<String, PlayerProfile> getPlayers() {
        return players;
    }

    // ==================== 方块变更（权威应用） ====================

    /**
     * 应用客户端上报的方块意图（信任前端，仅做类型映射与范围基本校验）。
     *
     * @param playerX 玩家像素坐标 X（范围校验用）
     * @param playerY 玩家像素坐标 Y
     * @param tileX   目标格子 X
     * @param tileY   目标格子 Y
     * @param action  place / break
     * @param itemName 放置物品名（place 时用）
     * @return 是否已应用（写入权威地图）
     */
    public boolean applyBlockAction(double playerX, double playerY, int tileX, int tileY,
                                    String action, String itemName) {
        // 基本距离校验（与 core BlockInteraction 一致：6 格）
        int ptx = (int) (playerX / TILE_SIZE);
        int pty = (int) (playerY / TILE_SIZE);
        if (Math.abs(tileX - ptx) > 6 || Math.abs(tileY - pty) > 6) return false;

        int current = map.getTileType(tileX, tileY);
        if ("break".equals(action)) {
            if (current == Chunk.AIR) return false;
            map.setTileType(tileX, tileY, Chunk.AIR);
            spawnDrop(tileX, tileY, current);
            return true;
        } else if ("place".equals(action)) {
            if (current != Chunk.AIR) return false;
            Integer blockType = BlockTypeMapper.itemToBlock(itemName);
            if (blockType == null) return false;
            map.setTileType(tileX, tileY, blockType);
            return true;
        }
        return false;
    }

    private int spawnDrop(int tileX, int tileY, int tileType) {
        String itemName = BlockTypeMapper.dropName(tileType);
        if (itemName == null) return -1;
        double dropX = tileX * TILE_SIZE + TILE_SIZE / 2.0;
        double dropY = tileY * TILE_SIZE + TILE_SIZE / 2.0;
        int id = dropIdCounter.incrementAndGet();
        dropItems.put(id, new DropItem(dropX, dropY, itemName, 1));
        return id;
    }

    /** 玩家拾取掉落物：按 id 移除（客户端上报拾取，服务器权威移除并广播消失）。 */
    public boolean removeDrop(int id) {
        return dropItems.remove(id) != null;
    }

    /** 取走自上次调用以来的方块变更（主协调线程每 tick 调用）。 */
    public List<int[]> drainTileChanges() {
        List<int[]> changes = map.drainTileChanges();
        lastChanges = changes;
        return changes;
    }

    /** 获取最近一次 tick 的方块变化（广播用）。 */
    public List<int[]> getLastChanges() {
        return lastChanges;
    }

    /** 清理已死亡掉落物（寿命耗尽），返回是否清掉。 */
    public void cleanupDrops() {
        dropItems.values().removeIf(d -> !d.isAlive());
    }

    /** 存活掉落物（id -> DropItem，广播用）。 */
    public Map<Integer, DropItem> getDropItems() {
        return dropItems;
    }

    // ==================== 存档 ====================

    /** 保存整个世界（异步，复用 core 存档线程）。 */
    public void saveWorld() {
        map.saveWorld();
    }

    /** 等待保存完成（关闭前调用）。 */
    public void waitForSave() {
        map.waitForSaveCompletion(5000);
    }

    /** 保存单个玩家档案到 player_<id>.txt。 */
    public void savePlayerFile(String playerId, PlayerProfile profile) {
        try {
            Path dir = Paths.get(worldDirPath);
            Files.createDirectories(dir);
            Path file = dir.resolve("player_" + playerId + ".txt");
            Files.writeString(file, profile.serialize(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("保存玩家档案失败 " + playerId + ": " + e.getMessage());
        }
    }

    /** 加载玩家档案，不存在返回 null。 */
    public PlayerProfile loadPlayerFile(String playerId) {
        try {
            Path file = Paths.get(worldDirPath, "player_" + playerId + ".txt");
            if (!Files.exists(file)) return null;
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return PlayerProfile.deserialize(playerId, content);
        } catch (IOException e) {
            System.err.println("加载玩家档案失败 " + playerId + ": " + e.getMessage());
            return null;
        }
    }

    /** 关闭：保存世界并停止后台线程。 */
    public void shutdown() {
        map.saveWorld();
        waitForSave();
        map.shutdown();
    }
}
