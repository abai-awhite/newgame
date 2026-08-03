package server;

import entity.AABB;
import entity.DropItem;
import main.world.Chunk;
import server.world.FluidSim;

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

    /** 掉落物拾取磁吸半径（像素，玩家中心到掉落物中心），略大于半格，防高速隧穿 */
    private static final double PICKUP_RADIUS = 22.0;

    /** 地图引用（权威区块数据） */
    public final ServerInfiniteMap map;

    /** 玩家档案：playerId -> PlayerProfile（多玩家） */
    private final Map<String, PlayerProfile> players = new ConcurrentHashMap<>();

    /** 掉落物（全局，破坏方块产生）：id -> DropItem（广播线程读，主线程写） */
    private final Map<Integer, DropItem> dropItems = new ConcurrentHashMap<>();
    private final AtomicInteger dropIdCounter = new AtomicInteger(0);

    /** 最近一次 tick 产生的方块变化（{x, y, oldType, newType, oldLevel, newLevel}） */
    private volatile List<int[]> lastChanges = List.of();

    /** 服务器权威流体模拟（水/岩浆流动，由主协调线程 tick 驱动） */
    private final FluidSim fluidSim;

    private final String worldDirPath;

    public WorldCore(long seed, String worldName, int chunkThreads) {
        boolean legacy = WorldStore.ensureMetaVersion(worldName);
        map = new ServerInfiniteMap(seed, worldName, chunkThreads, legacy);
        map.loadWorld();
        this.worldDirPath = map.getWorldDirPath();
        this.fluidSim = new FluidSim(map);
        // Terraria "Settling Liquids"：进世界时强制计算并稳定所有液体（含薄水蒸发），
        // 让存档/生成的水在玩家进入前就处于平衡状态
        this.fluidSim.settleAll();
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
            if (Chunk.isFluid(current)) {
                // Terraria 式：舀液体 = 从所属连通水域总量扣一桶（16 单位 = 一整格），非清空单格；
                // 水域按比例缩水、浅格先干涸，总量精确守恒，水面整体下降
                fluidSim.scoop(tileX, tileY, current);
                fluidSim.markBlockUpdate(tileX, tileY);
                return true;
            }
            map.setTileTypeAndLevel(tileX, tileY, Chunk.AIR, 0);
            spawnDrop(tileX, tileY, current);
            fluidSim.markBlockUpdate(tileX, tileY);
            return true;
        } else if ("place".equals(action)) {
            // 允许放在空气或液体格内（替换液体时流体被排挤位移，不凭空消失）
            if (current != Chunk.AIR && !Chunk.isFluid(current)) return false;
            Integer blockType = BlockTypeMapper.itemToBlock(itemName);
            if (blockType == null) return false;
            if (Chunk.isFluid(blockType)) {
                // Terraria 式倒液体：一桶（16 单位 = 一整格）融入所属水域，水面微升而非整格方块
                fluidSim.pour(tileX, tileY, blockType);
            } else {
                if (Chunk.isFluid(current)) {
                    // 固体替换流体：把被排挤的液体位移（融入相邻水域或挤到上方格）
                    int amount = FluidSim.FULL_AMOUNT - map.getFluidLevel(tileX, tileY);
                    map.setTileTypeAndLevel(tileX, tileY, blockType, 0);
                    fluidSim.displace(tileX, tileY, current, amount);
                } else {
                    map.setTileTypeAndLevel(tileX, tileY, blockType, 0);
                }
            }
            fluidSim.markBlockUpdate(tileX, tileY);
            return true;
        }
        return false;
    }

    /**
     * 每 tick 驱动流体模拟（内部按 8Hz 节奏运行）。由服务器主协调线程调用，
     * 流体变更写入权威地图并进入方块变更日志（随本帧增量广播）。
     */
    public void tickFluid() {
        fluidSim.tick();
    }

    /**
     * 玩家按 Q 扔出物品（类 Minecraft）：从玩家中心朝抛出速度方向生成掉落物。
     * 物品入包/消耗由客户端完成（背包权威在客户端），服务器只生成权威掉落物。
     * vx/vy 为客户端按鼠标方向计算的速度；全 0 时退回"面向方向"默认抛速。
     */
    public int spawnDropForPlayer(PlayerProfile p, String itemName, double vx, double vy) {
        double cx = p.x + TILE_SIZE / 2.0;
        double cy = p.y + TILE_SIZE / 2.0;
        double dir = "left".equals(p.direction) ? -1 : 1;
        double startX, startY;
        if (vx != 0 || vy != 0) {
            double len = Math.hypot(vx, vy);
            startX = cx + vx / len * 20;
            startY = cy + vy / len * 20;
        } else {
            startX = cx + dir * 20;
            startY = cy - 4;
        }
        int id = dropIdCounter.incrementAndGet();
        DropItem d = new DropItem(map, TILE_SIZE, startX, startY, itemName, 1);
        d.setVX(vx != 0 || vy != 0 ? vx : dir * 6);
        d.setVY(vx != 0 || vy != 0 ? vy : -5);
        dropItems.put(id, d);
        return id;
    }

    private int spawnDrop(int tileX, int tileY, int tileType) {
        String itemName = BlockTypeMapper.dropName(tileType);
        if (itemName == null) return -1;
        double dropX = tileX * TILE_SIZE + TILE_SIZE / 2.0;
        double dropY = tileY * TILE_SIZE + TILE_SIZE / 2.0;
        int id = dropIdCounter.incrementAndGet();
        // 掉落物作为独立实体接入与玩家一致的 AABB 物理（重力 + 地面碰撞）
        dropItems.put(id, new DropItem(map, TILE_SIZE, dropX, dropY, itemName, 1));
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

    /**
     * 每 tick 更新所有掉落物：实体物理（重力 + AABB 方块碰撞，支撑被挖掉自然下落），
     * 随后检测掉落物与玩家的实体间碰撞（AABB 重叠 → 触发拾取回调），最后清理死亡掉落物。
     * 由服务器主协调线程调用（权威物理，广播读 ConcurrentHashMap 安全）。
     */
    public void tickDrops(DropPickupHandler handler) {
        for (Map.Entry<Integer, DropItem> entry : dropItems.entrySet()) {
            DropItem d = entry.getValue();
            d.update();
            if (!d.isAlive()) continue;

            // 磁吸拾取：玩家中心与掉落物中心距离 < PICKUP_RADIUS 即被吸入。
            // 比 AABB 相交（26×26 ∩ 16×16，横向容差仅 21px）更宽容，且防高速隧穿。
            for (PlayerProfile p : players.values()) {
                if (Math.hypot(d.getX() - (p.x + TILE_SIZE / 2.0),
                        d.getY() - (p.y + TILE_SIZE / 2.0)) < PICKUP_RADIUS) {
                    if (handler != null) {
                        handler.onPickup(p.playerId, entry.getKey(), d.getItemName(), d.getCount());
                    }
                    dropItems.remove(entry.getKey());
                    break;
                }
            }
        }
        cleanupDrops();
    }

    /** 掉落物拾取回调：玩家碰撞到掉落物（服务器权威检测，客户端收到事件后入包） */
    public interface DropPickupHandler {
        void onPickup(String playerId, int dropId, String itemName, int count);
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
