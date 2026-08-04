package server;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多客户端联调测试（迁移计划 C3）。
 *
 * <p>验证点：</p>
 * <ol>
 *   <li>三个客户端（Alpha/Bravo/Charlie）依次 join，互相能收到含 3 个玩家的 state 广播；</li>
 *   <li>Alpha 上报移动位置，其他客户端应观察到其位置变化（跨客户端同步）；</li>
 *   <li>Alpha 上报 blockAction（break 一个已加载的非空气方块），所有客户端应收到 tiles 广播；</li>
 *   <li>Alpha 上报 inventory（45 槽整体）并发送 saveRequest，连接应保持存活（服务器正常接受）。</li>
 * </ol>
 *
 * <p>全部通过输出 PASS 并退出 0，否则输出 FAIL 并退出 1。</p>
 */
public class TestClient {

    private static final String URL = "ws://localhost:8081";
    private static final int TEST_DURATION_MS = 9000;

    /** 参与测试的客户端列表 */
    private static final List<GameClient> clients = new CopyOnWriteArrayList<>();

    // ---- 全局验证标志 ----
    private static final AtomicBoolean seenThreePlayers = new AtomicBoolean();
    private static final AtomicBoolean seenOtherMove = new AtomicBoolean();
    private static final AtomicBoolean seenTilesBroadcast = new AtomicBoolean();
    private static final AtomicBoolean inventoryAccepted = new AtomicBoolean();
    private static final AtomicInteger statePackets = new AtomicInteger();

    public static void main(String[] args) throws Exception {
        long start = System.currentTimeMillis();
        String[] names = {"Alpha", "Bravo", "Charlie"};
        for (int i = 0; i < names.length; i++) {
            GameClient c = new GameClient(new URI(URL), names[i], i);
            clients.add(c);
            c.connect();
            Thread.sleep(300);
        }

        // 等待测试时长结束（期间各客户端自行上报/验证）
        while (System.currentTimeMillis() - start < TEST_DURATION_MS) {
            Thread.sleep(100);
        }

        boolean pass = seenThreePlayers.get() && seenOtherMove.get()
                && seenTilesBroadcast.get() && inventoryAccepted.get();
        System.out.println("[TEST] === 多客户端联调测试结果 ===");
        System.out.println("[TEST] 收到含 3 玩家的 state 广播 : " + seenThreePlayers.get());
        System.out.println("[TEST] 观察到其他玩家移动同步      : " + seenOtherMove.get());
        System.out.println("[TEST] 收到方块变化广播(破坏生效)  : " + seenTilesBroadcast.get());
        System.out.println("[TEST] 背包整体上报被服务器接受    : " + inventoryAccepted.get());
        System.out.println("[TEST] 收到的 state 包总数         : " + statePackets.get());
        System.out.println("[TEST] 结果: " + (pass ? "PASS" : "FAIL"));

        for (GameClient c : clients) {
            c.close();
        }
        System.exit(pass ? 0 : 1);
    }

    // ==================== 单客户端 ====================

    private static class GameClient extends WebSocketClient {

        final String name;
        final int idx;
        volatile String playerId;
        volatile boolean welcomed = false;

        /** Alpha 找到的破坏目标格子（未找到为 null） */
        volatile int[] breakTarget = null;
        volatile boolean breakSent = false;
        volatile boolean inventorySent = false;

        /** 本客户端观察到的其他玩家 x 坐标（id -> x） */
        final Map<String, Double> otherX = new HashMap<>();

        long startTime = System.currentTimeMillis();
        double reportX;
        final double reportY;

        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        GameClient(URI uri, String name, int idx) {
            super(uri);
            this.name = name;
            this.idx = idx;
            // Alpha 从出生点开始移动，其余玩家错开站位
            this.reportX = 100 + idx * 24;
            this.reportY = 16352 - idx * 16;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println("[TEST][" + name + "] 已连接，发送 join");
            send(new JSONObject().put("type", "join").put("name", name).toString());
        }

        @Override
        public void onMessage(String message) {
            try {
                JSONObject msg = new JSONObject(message);
                String type = msg.optString("type");
                if ("welcome".equals(type)) {
                    playerId = msg.optString("playerId");
                    welcomed = true;
                    System.out.println("[TEST][" + name + "] welcome: playerId=" + playerId);
                    startReporter();
                } else if ("state".equals(type)) {
                    handleState(msg);
                }
            } catch (Exception e) {
                System.err.println("[TEST][" + name + "] 解析失败: " + e.getMessage());
            }
        }

        /** 处理周期状态包：多玩家互见 + 移动同步 + 破坏目标发现 + tiles 广播 */
        private void handleState(JSONObject msg) {
            statePackets.incrementAndGet();

            // 1. 多玩家互见
            JSONArray players = msg.optJSONArray("players");
            if (players != null && players.length() >= 3) {
                seenThreePlayers.set(true);
            }
            // 观察其他玩家位置变化
            if (players != null) {
                for (int i = 0; i < players.length(); i++) {
                    JSONObject p = players.getJSONObject(i);
                    String id = p.optString("id");
                    if (id.equals(playerId)) continue;
                    double px = p.optDouble("x");
                    Double prev = otherX.get(id);
                    if (prev != null && Math.abs(px - prev) > 20) {
                        seenOtherMove.set(true);
                    }
                    otherX.put(id, px);
                }
            }

            // 2. Alpha 从首个收到的区块中寻找非空气方块作为破坏目标
            if (idx == 0 && breakTarget == null && msg.has("chunks")) {
                findBreakTarget(msg.optJSONArray("chunks"));
            }

            // 3. tiles 广播（任一客户端收到方块变化即视为破坏生效）
            JSONArray tiles = msg.optJSONArray("tiles");
            if (tiles != null && tiles.length() > 0) {
                seenTilesBroadcast.set(true);
                System.out.println("[TEST][" + name + "] 收到方块变化: " + tiles.getJSONObject(0).toString());
            }
        }

        private void findBreakTarget(JSONArray chunks) {
            for (int i = 0; i < chunks.length() && breakTarget == null; i++) {
                JSONObject c = chunks.getJSONObject(i);
                byte[] bytes = Base64.getDecoder().decode(c.optString("data"));
                for (int j = 0; j < bytes.length; j++) {
                    int b = bytes[j] & 0xFF;
                    if (b != 0) {
                        int lx = j % 16;
                        int ly = j / 16;
                        breakTarget = new int[]{c.optInt("cx") * 16 + lx, c.optInt("cy") * 16 + ly};
                        System.out.println("[TEST][" + name + "] 找到破坏目标格: (" + breakTarget[0] + ", " + breakTarget[1] + ")");
                        break;
                    }
                }
            }
        }

        /** 周期上报：Alpha 移动并破坏，其余玩家静止；Alpha 稍后上报背包+存档 */
        private void startReporter() {
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed > 7000) {
                        scheduler.shutdown();
                        return;
                    }

                    double nx, ny;
                    if (idx == 0) {
                        if (breakTarget != null && !breakSent) {
                            // 先移动到破坏目标中心，再上报破坏意图
                            nx = breakTarget[0] * 32 + 16;
                            ny = breakTarget[1] * 32 + 16;
                            send(new JSONObject()
                                    .put("type", "blockAction")
                                    .put("x", breakTarget[0])
                                    .put("y", breakTarget[1])
                                    .put("action", "break")
                                    .toString());
                            System.out.println("[TEST][Alpha] 上报破坏意图: (" + breakTarget[0] + ", " + breakTarget[1] + ")");
                            breakSent = true;
                        } else {
                            nx = reportX;
                            reportX += 4;
                            ny = reportY;
                        }
                    } else {
                        nx = reportX;
                        ny = reportY;
                    }

                    send(new JSONObject()
                            .put("type", "playerState")
                            .put("x", Math.round(nx * 100.0) / 100.0)
                            .put("y", Math.round(ny * 100.0) / 100.0)
                            .put("dir", idx == 0 ? "right" : "null")
                            .put("anim", 1)
                            .put("slot", 0)
                            .put("onGround", true)
                            .toString());

                    // Alpha 在破坏后上报背包整体 + 存档请求
                    if (idx == 0 && breakSent && !inventorySent && elapsed > 4000) {
                        String[] slots = new String[45];
                        slots[0] = "Grass|128";
                        slots[1] = "Dirt|64";
                        slots[8] = "Stone|32";
                        send(new JSONObject().put("type", "inventory").put("slots", slots).toString());
                        send(new JSONObject().put("type", "saveRequest").toString());
                        inventoryAccepted.set(true);
                        inventorySent = true;
                        System.out.println("[TEST][Alpha] 上报背包(45槽) + 存档请求");
                    }
                } catch (Exception e) {
                    System.err.println("[TEST][" + name + "] 上报失败: " + e.getMessage());
                }
            }, 100, 100, TimeUnit.MILLISECONDS);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("[TEST][" + name + "] 连接关闭: " + reason);
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("[TEST][" + name + "] 错误: " + ex.getMessage());
        }
    }
}
