package server;

import entity.DropItem;
import main.world.ChunkPos;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 游戏 WebSocket 服务器（多玩家）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>每个连接对应一个 PlayerProfile（位置/背包），物理计算在前端</li>
 *   <li>主协调线程 32Hz：预加载区块、应用方块意图、广播状态</li>
 *   <li>广播线程：批量推送状态包（不阻塞主线程）</li>
 * </ul>
 */
public class GameServer extends WebSocketServer {

    private static final double TICK_INTERVAL_MS = 1000.0 / 32.0;

    /** 区块预加载半径（区块数） */
    private static final int PRELOAD_RADIUS = 4;
    /** 区块同步半径（区块数） */
    private static final int SYNC_RADIUS = 4;
    /** 客户端心跳超时（毫秒）：超过未上报视为掉线，强制断开清理 */
    private static final long KEEPALIVE_TIMEOUT_MS = 15_000L;

    /** 世界权威核心（joinWorld 时热切换，故 volatile） */
    private volatile WorldCore world;

    /** 当前世界名（切换时更新） */
    private volatile String worldName;

    /** 当前世界元数据（种子/哈希，welcome 时下发） */
    private volatile WorldStore.WorldMeta currentMeta;

    /** 新世界创建参数（世界切换时复用） */
    private final int chunkThreads;

    /** 最大玩家数（超过则拒绝新连接） */
    private final int maxPlayers;

    /** 会话：WebSocket -> 客户端状态 */
    private final Map<WebSocket, ClientState> clients = new ConcurrentHashMap<>();

    /** 玩家 ID 分配器 */
    private final AtomicInteger playerIdCounter = new AtomicInteger(0);

    /** 主协调线程 */
    private volatile Thread tickThread;
    private volatile boolean running = false;

    /** 待应用的方块意图队列（消息线程写入，主线程消费） */
    private final LinkedBlockingQueue<Runnable> actionQueue = new LinkedBlockingQueue<>();

    /** 广播线程：每 tick 接收一次状态快照，批量推送所有客户端（不阻塞主线程） */
    private final BroadcastService broadcastService;

    public GameServer(int port, long seed, String worldName, int chunkThreads, int maxPlayers) {
        super(new InetSocketAddress(port));
        this.chunkThreads = chunkThreads;
        this.maxPlayers = maxPlayers;
        // 初始世界：已存在则读存档种子（保持区块一致），否则用配置种子并生成元数据
        this.currentMeta = WorldStore.ensureMeta(worldName, seed);
        this.worldName = currentMeta.name;
        this.world = new WorldCore(currentMeta.seed, this.worldName, chunkThreads);
        this.broadcastService = new BroadcastService("BroadcastThread", this::broadcastState);
    }

    // ==================== WebSocket 生命周期 ====================

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        if (clients.size() >= maxPlayers) {
            conn.close(1013, "服务器已满（上限 " + maxPlayers + " 人）");
            return;
        }
        String playerId = "p" + playerIdCounter.incrementAndGet();
        PlayerProfile profile = world.addPlayer(playerId);
        ClientState state = new ClientState(profile);
        clients.put(conn, state);

        // 广播新玩家加入
        broadcast(new JSONObject()
                .put("type", "playerJoined")
                .put("player", profileToJson(profile)));

        System.out.println("客户端连接: " + conn.getRemoteSocketAddress() + " -> " + playerId);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        ClientState state = clients.remove(conn);
        if (state != null) {
            world.removePlayer(state.profile.playerId);
            broadcast(new JSONObject()
                    .put("type", "playerLeft")
                    .put("playerId", state.profile.playerId));
        }
        System.out.println("客户端断开: " + conn.getRemoteSocketAddress() + " (" + reason + ")");
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        ClientState state = clients.get(conn);
        if (state == null) return;
        state.lastActive = System.currentTimeMillis();
        try {
            JSONObject obj = new JSONObject(message);
            String type = obj.optString("type", "playerState");
            switch (type) {
                case "join" -> handleJoin(state, conn, obj);
                case "playerState" -> applyPlayerState(state, obj);
                case "blockAction" -> enqueueBlockAction(state, obj);
                case "inventory" -> applyInventory(state, obj);
                case "pickup" -> {
                    // 玩家拾取掉落物：服务器权威移除（id 来自广播），并广播消失
                    world.removeDrop(obj.optInt("id", -1));
                }
                case "throw" -> {
                    // 玩家按 Q 扔出当前手持物品：服务器在玩家面前生成带初速度的掉落物
                    String item = obj.optString("item", "");
                    if (!item.isEmpty()) {
                        double vx = obj.optDouble("vx", 0);
                        double vy = obj.optDouble("vy", 0);
                        world.spawnDropForPlayer(state.profile, item, vx, vy);
                    }
                }
                case "saveRequest" -> {
                    world.saveWorld();
                    world.savePlayerFile(state.profile.playerId, state.profile);
                }
                // ==================== 世界管理（单人模式世界选择） ====================
                case "listWorlds" -> conn.send(new JSONObject()
                        .put("type", "worlds")
                        .put("world", worldName)
                        .put("list", WorldStore.listToJson()).toString());
                case "createWorld" -> {
                    WorldStore.WorldMeta meta = WorldStore.createWorld(obj.optString("name", ""), obj.optString("seed", ""));
                    conn.send(new JSONObject()
                            .put("type", "worlds")
                            .put("world", worldName)
                            .put("created", meta.name)   // 实际创建的世界名（重名时已自动加后缀）
                            .put("list", WorldStore.listToJson()).toString());
                }
                case "deleteWorld" -> {
                    String name = obj.optString("name", "").trim();
                    if (name.equals(worldName)) {
                        conn.send(new JSONObject()
                                .put("type", "worldError")
                                .put("msg", "当前世界不可删除").toString());
                    } else {
                        WorldStore.deleteWorld(name);
                        conn.send(new JSONObject()
                                .put("type", "worlds")
                                .put("world", worldName)
                                .put("list", WorldStore.listToJson()).toString());
                    }
                }
                case "joinWorld" -> enqueueWorldJoin(conn, state, obj);
                case "attackMob" -> {
                    // 客户端上报攻击怪物（子弹/剑命中）：服务器权威扣血
                    int mobId = obj.optInt("mobId", -1);
                    int dmg = obj.optInt("dmg", 10);
                    if (mobId >= 0) {
                        world.damageMob(mobId, dmg);
                    }
                }
                default -> { /* 忽略未知类型 */ }
            }
        } catch (Exception e) {
            System.err.println("消息解析失败: " + e.getMessage());
        }
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // 二进制消息暂不处理
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("WebSocket 错误: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("WebSocket 服务器已启动: ws://localhost:" + getPort());
        broadcastService.start();
        startTickLoop();
    }

    // ==================== 消息应用 ====================

    /** 应用前端上报的玩家位置/朝向（权威存储，直接采信） */
    private void applyPlayerState(ClientState state, JSONObject obj) {
        PlayerProfile p = state.profile;
        p.x = obj.optDouble("x", p.x);
        p.y = obj.optDouble("y", p.y);
        p.direction = obj.optString("dir", p.direction);
        p.animFrame = obj.optInt("anim", p.animFrame);
        p.slot = obj.optInt("slot", p.slot);
        p.onGround = obj.optBoolean("onGround", p.onGround);
    }

    /** 方块意图入队（主协调线程消费，保证权威地图线程安全） */
    private void enqueueBlockAction(ClientState state, JSONObject obj) {
        int tileX = obj.optInt("x");
        int tileY = obj.optInt("y");
        String action = obj.optString("action");
        String item = obj.optString("item");
        actionQueue.offer(() -> world.applyBlockAction(state.profile.x, state.profile.y, tileX, tileY, action, item));
    }

    /** 前端上报背包整体（权威存储用于存档/同步） */
    private void applyInventory(ClientState state, JSONObject obj) {
        JSONArray arr = obj.optJSONArray("slots");
        if (arr == null) return;
        String[] slots = new String[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            slots[i] = arr.optString(i, "");
        }
        state.profile.setSlots(slots);
    }

    // ==================== 身份加入与多世界切换 ====================

    /**
     * 身份加入：客户端带 playerId 时切换到该身份档案（同一浏览器重连可恢复存档），
     * 并回复欢迎包（玩家 ID + 世界信息 + 玩家列表 + 本玩家背包/位置存档）。
     */
    private void handleJoin(ClientState state, WebSocket conn, JSONObject obj) {
        String name = obj.optString("name", "Player");
        String requestedId = obj.optString("playerId", "").trim();
        if (!requestedId.isEmpty() && !requestedId.equals(state.profile.playerId)) {
            String oldId = state.profile.playerId;
            world.removePlayer(oldId);
            PlayerProfile profile = world.addPlayer(requestedId);
            profile.name = name;
            state.profile = profile;
            // 通知其他玩家身份迁移（旧身份离开、新身份加入）
            broadcast(new JSONObject()
                    .put("type", "playerLeft")
                    .put("playerId", oldId));
            broadcast(new JSONObject()
                    .put("type", "playerJoined")
                    .put("player", profileToJson(profile)));
        } else {
            state.profile.name = name;
        }
        JSONObject welcome = new JSONObject()
                .put("type", "welcome")
                .put("playerId", state.profile.playerId)
                .put("world", worldName)
                .put("seed", currentMeta.seed)
                .put("seedHash", currentMeta.seedHash);
        JSONArray players = new JSONArray();
        for (PlayerProfile p : world.getPlayers().values()) {
            players.put(profileToJson(p));
        }
        welcome.put("players", players);
        JSONArray slots = new JSONArray();
        for (String s : state.profile.getSlotsCopy()) {
            slots.put(s == null ? "" : s);
        }
        welcome.put("slots", slots);
        conn.send(welcome.toString());
    }

    /** 加入世界请求入队（主协调线程执行，保证世界切换与地图访问线程安全）。 */
    private void enqueueWorldJoin(WebSocket conn, ClientState state, JSONObject obj) {
        String name = obj.optString("world", "").trim();
        if (name.isEmpty()) return;
        String playerName = obj.optString("name", "Player");
        String playerId = obj.optString("playerId", "").trim();
        actionQueue.offer(() -> switchWorld(conn, state, name, playerName, playerId));
    }

    /**
     * 世界热切换（主协调线程执行）：
     * 保存并关闭旧世界 → 加载目标世界（种子取自元数据）→ 迁移全部客户端档案 →
     * 广播 worldSwitch（所有前端重建状态）→ 请求者执行身份加入并回复欢迎包。
     */
    private void switchWorld(WebSocket conn, ClientState requester, String newName,
                             String playerName, String requestedId) {
        try {
            if (newName.equals(worldName)) {
                // 已在目标世界：仅执行身份加入（重连恢复）
                handleJoin(requester, conn, joinObj(playerName, requestedId));
                return;
            }

            // 1. 保存并关闭当前世界（所有玩家档案写入旧世界目录）
            WorldCore oldWorld = world;
            for (ClientState cs : clients.values()) {
                oldWorld.removePlayer(cs.profile.playerId);
            }
            oldWorld.saveWorld();
            oldWorld.waitForSave();
            oldWorld.shutdown();

            // 2. 加载目标世界（双层种子已持久化在元数据中）
            WorldStore.WorldMeta meta = WorldStore.ensureMeta(newName, 0L);
            WorldCore newWorld = new WorldCore(meta.seed, meta.name, chunkThreads);
            world = newWorld;
            worldName = meta.name;
            currentMeta = meta;

            // 3. 迁移所有客户端档案到新世界（从新世界存档恢复各自状态）
            for (ClientState cs : clients.values()) {
                String pid = cs.profile.playerId;
                PlayerProfile np = newWorld.addPlayer(pid);
                np.name = cs.profile.name;
                cs.profile = np;
                cs.sentChunks.clear();
            }

            // 4. 广播世界切换：所有客户端清空本地世界状态并重建
            broadcast(new JSONObject()
                    .put("type", "worldSwitch")
                    .put("name", worldName)
                    .put("seed", meta.seed)
                    .put("seedHash", meta.seedHash));

            // 5. 请求者执行身份加入（该世界存档恢复）
            handleJoin(requester, conn, joinObj(playerName, requestedId));
        } catch (Exception e) {
            System.err.println("世界切换失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static JSONObject joinObj(String playerName, String playerId) {
        return new JSONObject().put("name", playerName).put("playerId", playerId);
    }

    // ==================== 逻辑循环 ====================

    private void startTickLoop() {
        running = true;
        tickThread = new Thread(() -> {
            long nextTick = System.nanoTime();
            long intervalNanos = (long) (TICK_INTERVAL_MS * 1_000_000L);
            while (running) {
                try {
                    // 1. 消费方块意图（应用权威变更）
                    Runnable action;
                    while ((action = actionQueue.poll()) != null) {
                        try {
                            action.run();
                        } catch (Exception e) {
                            System.err.println("方块意图应用失败: " + e.getMessage());
                        }
                    }

                    // 2. 收集方块变更 + 流体模拟 + 掉落物物理（重力下落/落地/磁吸）
                    world.drainTileChanges();
                    world.tickFluid();
                    List<int[]> changes = world.getLastChanges();
                    world.tickDrops(this::onDropPickup);
                    world.tickMobs(this::onMobHitPlayer);

                    // 2.5 掉线检测：超过阈值未上报的客户端强制断开（清理幽灵连接）
                    long now = System.currentTimeMillis();
                    for (Map.Entry<WebSocket, ClientState> e : clients.entrySet()) {
                        if (now - e.getValue().lastActive > KEEPALIVE_TIMEOUT_MS) {
                            System.out.println("心跳超时，断开: " + e.getKey().getRemoteSocketAddress()
                                    + " (" + e.getValue().profile.playerId + ")");
                            e.getKey().close();
                        }
                    }

                    // 3. 预加载所有玩家周围区块（多线程生成）
                    for (PlayerProfile p : world.getPlayers().values()) {
                        int pcx = Math.floorDiv((int) (p.x / WorldCore.TILE_SIZE), main.world.Chunk.SIZE);
                        int pcy = Math.floorDiv((int) (p.y / WorldCore.TILE_SIZE), main.world.Chunk.SIZE);
                        world.map.preloadChunks(pcx, pcy, PRELOAD_RADIUS);
                    }
                    List<ChunkPos> readyChunks = world.map.drainReadyChunks();

                    // 4. 发布状态快照到广播线程（每 tick 一次幂等序列化，广播线程逐个 send）
                    broadcastService.publish(new BroadcastService.Snapshot(buildBaseStateJson(changes), readyChunks));

                    // 按固定步长休眠
                    nextTick += intervalNanos;
                    long sleepMs = (nextTick - System.nanoTime()) / 1_000_000L;
                    if (sleepMs > 0) {
                        Thread.sleep(sleepMs);
                    } else {
                        nextTick = System.nanoTime();
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("tick 异常: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "GameCoordinatorThread");
        tickThread.setDaemon(true);
        tickThread.start();
    }

    // ==================== 状态序列化 ====================

    /**
     * 构建公共状态 JSON（不含新区块）：players/tiles/drops。
     * 由主协调线程每 tick 调用一次，幂等，避免对每个客户端重复序列化。
     */
    private JSONObject buildBaseStateJson(List<int[]> changes) {
        JSONObject msg = new JSONObject();
        msg.put("type", "state");

        // 所有玩家位置
        JSONArray players = new JSONArray();
        for (PlayerProfile p : world.getPlayers().values()) {
            players.put(profileToJson(p));
        }
        msg.put("players", players);

        // 方块变化增量（含流体水位 lv，非液体恒为 0）
        JSONArray tileArray = new JSONArray();
        for (int[] change : changes) {
            JSONObject tile = new JSONObject();
            tile.put("x", change[0]);
            tile.put("y", change[1]);
            tile.put("type", change[3]);
            tile.put("lv", change[5]);
            tileArray.put(tile);
        }
        msg.put("tiles", tileArray);

        // 掉落物（带唯一 id，客户端拾取后上报移除）
        JSONArray drops = new JSONArray();
        for (Map.Entry<Integer, DropItem> e : world.getDropItems().entrySet()) {
            DropItem drop = e.getValue();
            JSONObject d = new JSONObject();
            d.put("id", e.getKey());
            d.put("x", drop.getX());
            d.put("y", drop.getY());
            d.put("name", drop.getItemName());
            drops.put(d);
        }
        msg.put("drops", drops);

        // 怪物（id + 位置 + 生命值）
        JSONArray mobs = new JSONArray();
        for (Map.Entry<Integer, entity.Slime> e : world.getMobs().entrySet()) {
            entity.Slime m = e.getValue();
            JSONObject mo = new JSONObject();
            mo.put("id", e.getKey());
            mo.put("x", Math.round(m.getX() * 100.0) / 100.0);
            mo.put("y", Math.round(m.getY() * 100.0) / 100.0);
            mo.put("hp", m.getHp());
            mo.put("maxHp", m.getMaxHp());
            mo.put("hurt", m.isHurtFlashing());
            mobs.put(mo);
        }
        msg.put("mobs", mobs);

        return msg;
    }

    /**
     * 掉落物被玩家碰撞拾取（服务器权威检测）：向该玩家发送 dropPickup 事件，
     * 客户端收到后从本地掉落物列表移除并把物品加入背包（背包权威在客户端）。
     */
    private void onDropPickup(String playerId, int dropId, String itemName, int count) {
        for (Map.Entry<WebSocket, ClientState> e : clients.entrySet()) {
            ClientState st = e.getValue();
            if (st.profile != null && playerId.equals(st.profile.playerId)) {
                e.getKey().send(new JSONObject()
                        .put("type", "dropPickup")
                        .put("id", dropId)
                        .put("item", itemName)
                        .put("count", count).toString());
                return;
            }
        }
    }

    /**
     * 怪物接触玩家（服务器权威检测）：向该玩家发送 mobHit 事件，
     * 客户端收到后扣减本地生命值。
     */
    private void onMobHitPlayer(String playerId, int mobId, int damage) {
        for (Map.Entry<WebSocket, ClientState> e : clients.entrySet()) {
            ClientState st = e.getValue();
            if (st.profile != null && playerId.equals(st.profile.playerId)) {
                e.getKey().send(new JSONObject()
                        .put("type", "mobHit")
                        .put("mobId", mobId)
                        .put("dmg", damage).toString());
                return;
            }
        }
    }

    /**
     * 广播线程回调：为每个客户端在公共状态上追加个性化新区块（按已发送集合去重）并发送。
     * 注意：本方法运行在 BroadcastThread，sentChunks 集合仅本线程访问。
     */
    private void broadcastState(BroadcastService.Snapshot snapshot) {
        JSONObject base = snapshot.base;
        String[] names = JSONObject.getNames(base);

        for (Map.Entry<WebSocket, ClientState> entry : clients.entrySet()) {
            WebSocket conn = entry.getKey();
            if (!conn.isOpen()) continue;
            ClientState state = entry.getValue();

            JSONObject msg = new JSONObject(base, names);

            // 新区块（按客户端已发送去重）
            JSONArray chunkArray = new JSONArray();
            PlayerProfile me = state.profile;
            int pcx = Math.floorDiv((int) (me.x / WorldCore.TILE_SIZE), main.world.Chunk.SIZE);
            int pcy = Math.floorDiv((int) (me.y / WorldCore.TILE_SIZE), main.world.Chunk.SIZE);
            for (int dy = -SYNC_RADIUS; dy <= SYNC_RADIUS; dy++) {
                for (int dx = -SYNC_RADIUS; dx <= SYNC_RADIUS; dx++) {
                    ChunkPos pos = new ChunkPos(pcx + dx, pcy + dy);
                    String key = pos.cx + "," + pos.cy;
                    if (state.sentChunks.add(key)) {
                        chunkArray.put(serializeChunk(pos));
                    }
                }
            }
            // 后台生成完成且未发送过的区块
            for (ChunkPos pos : snapshot.readyChunks) {
                String key = pos.cx + "," + pos.cy;
                if (state.sentChunks.add(key)) {
                    chunkArray.put(serializeChunk(pos));
                }
            }
            msg.put("chunks", chunkArray);

            conn.send(msg.toString());
        }
    }

    private JSONObject profileToJson(PlayerProfile p) {
        JSONObject j = new JSONObject();
        j.put("id", p.playerId);
        j.put("name", p.name);
        j.put("x", Math.round(p.x * 100.0) / 100.0);
        j.put("y", Math.round(p.y * 100.0) / 100.0);
        j.put("dir", p.direction);
        j.put("anim", p.animFrame);
        j.put("slot", p.slot);
        j.put("onGround", p.onGround);
        return j;
    }

    /** 序列化单个区块为紧凑格式（Base64，每字节一个 tile 类型；lv 为同序水位字节） */
    private JSONObject serializeChunk(ChunkPos pos) {
        byte[] data = new byte[main.world.Chunk.SIZE * main.world.Chunk.SIZE];
        byte[] levels = new byte[main.world.Chunk.SIZE * main.world.Chunk.SIZE];
        int idx = 0;
        for (int localY = 0; localY < main.world.Chunk.SIZE; localY++) {
            for (int localX = 0; localX < main.world.Chunk.SIZE; localX++) {
                data[idx] = (byte) world.map.getChunkTile(localX, localY, pos);
                levels[idx] = (byte) world.map.getChunkFluidLevel(localX, localY, pos);
                idx++;
            }
        }
        JSONObject obj = new JSONObject();
        obj.put("cx", pos.cx);
        obj.put("cy", pos.cy);
        obj.put("data", Base64.getEncoder().encodeToString(data));
        obj.put("lv", Base64.getEncoder().encodeToString(levels));
        return obj;
    }

    /** 向所有客户端广播 JSON 消息。 */
    private void broadcast(JSONObject msg) {
        String payload = msg.toString();
        for (WebSocket conn : clients.keySet()) {
            if (conn.isOpen()) {
                conn.send(payload);
            }
        }
    }

    /** 关闭服务器：停止逻辑循环并保存世界与玩家档案。 */
    public void shutdown() {
        running = false;
        if (tickThread != null) {
            tickThread.interrupt();
        }
        broadcastService.shutdown();
        try {
            world.saveWorld();
            for (PlayerProfile p : world.getPlayers().values()) {
                world.savePlayerFile(p.playerId, p);
            }
            world.waitForSave();
        } catch (Exception e) {
            System.err.println("关闭时保存世界失败: " + e.getMessage());
        }
        try {
            world.shutdown();
        } catch (Exception e) {
            // ignore
        }
        try {
            this.stop();
        } catch (Exception e) {
            // ignore
        }
    }

    /** 每个客户端的状态：玩家档案 + 已发送区块集合（多线程访问需注意可见性） */
    private static class ClientState {
        /** 玩家档案（join 后可能迁移到固定身份，故非 final） */
        volatile PlayerProfile profile;
        final Set<String> sentChunks = new HashSet<>();
        /** 最后收到客户端消息的时间戳，用于掉线检测 */
        volatile long lastActive;

        ClientState(PlayerProfile profile) {
            this.profile = profile;
            this.lastActive = System.currentTimeMillis();
        }
    }
}
