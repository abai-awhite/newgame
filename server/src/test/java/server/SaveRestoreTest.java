package server;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 固定身份存档保存/恢复验证（迁移计划 B 阶段补充）。
 *
 * <p>流程：</p>
 * <ol>
 *   <li>连接 A：join 携带固定 playerId，上报位置与背包（slot0=Grass|64），随后断开（服务端应写存档）；</li>
 *   <li>检查存档文件 world/web-world/player_&lt;id&gt;.txt 是否存在；</li>
 *   <li>连接 B：join 携带相同 playerId，welcome 中 playerId 应一致，且 slots[0] 应恢复为 Grass|64。</li>
 * </ol>
 */
public class SaveRestoreTest {

    private static final String URL = "ws://localhost:8081";
    private static final String PID = "save_test_" + (System.currentTimeMillis() % 100000L);

    public static void main(String[] args) throws Exception {
        WelcomeInfo first = runSession(true);
        Path saveFile = Paths.get("world", "web-world", "player_" + PID + ".txt");
        boolean fileExists = Files.exists(saveFile);

        WelcomeInfo second = runSession(false);
        boolean idStable = PID.equals(first.playerId) && PID.equals(second.playerId);
        boolean slotsRestored = second.slots != null && second.slots.length > 0
                && second.slots[0] != null && second.slots[0].startsWith("Grass");
        boolean posRestored = second.x != null && Math.abs(second.x - 1234.0) < 0.01;

        System.out.println("[TEST] 固定身份一致 (welcome playerId)  : " + idStable);
        System.out.println("[TEST] 存档文件存在 player_" + PID + ".txt : " + fileExists);
        System.out.println("[TEST] 重连恢复背包 slots[0]            : "
                + (second.slots != null && second.slots.length > 0 ? second.slots[0] : "null"));
        System.out.println("[TEST] 重连恢复位置 x                  : "
                + (second.x == null ? "null" : second.x));

        boolean pass = idStable && fileExists && slotsRestored && posRestored;
        System.out.println("[TEST] 结果: " + (pass ? "PASS" : "FAIL"));
        System.exit(pass ? 0 : 1);
    }

    private static WelcomeInfo runSession(boolean first) throws Exception {
        CountDownLatch welcomeLatch = new CountDownLatch(1);
        AtomicReference<WelcomeInfo> result = new AtomicReference<>();
        WebSocketClient client = new WebSocketClient(new URI(URL)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                JSONObject join = new JSONObject()
                        .put("type", "join")
                        .put("name", "SaveTest")
                        .put("playerId", PID);
                send(join.toString());
                if (first) {
                    // 上报位置与背包
                    send(new JSONObject()
                            .put("type", "playerState")
                            .put("x", 1234.0).put("y", 16200.0)
                            .put("dir", "right").put("anim", 1).put("slot", 0)
                            .put("onGround", true).toString());
                    JSONArray slots = new JSONArray();
                    for (int i = 0; i < 45; i++) {
                        slots.put(i == 0 ? "Grass|64" : "");
                    }
                    send(new JSONObject().put("type", "inventory").put("slots", slots).toString());
                    send(new JSONObject().put("type", "saveRequest").toString());
                }
            }

            @Override
            public void onMessage(String message) {
                JSONObject msg = new JSONObject(message);
                if ("welcome".equals(msg.optString("type"))) {
                    JSONArray slots = msg.optJSONArray("slots");
                    WelcomeInfo info = new WelcomeInfo();
                    info.playerId = msg.optString("playerId");
                    if (slots != null) {
                        info.slots = new String[slots.length()];
                        for (int i = 0; i < slots.length(); i++) {
                            info.slots[i] = slots.optString(i, "");
                        }
                    }
                    info.x = msg.has("x") ? msg.optDouble("x") : null;
                    result.set(info);
                    welcomeLatch.countDown();
                } else if ("state".equals(msg.optString("type"))) {
                    // 广播里的自己位置（恢复验证用）
                    JSONArray players = msg.optJSONArray("players");
                    if (players != null) {
                        for (int i = 0; i < players.length(); i++) {
                            JSONObject p = players.getJSONObject(i);
                            if (PID.equals(p.optString("id"))) {
                                result.get().x = p.optDouble("x");
                            }
                        }
                    }
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                welcomeLatch.countDown();
            }

            @Override
            public void onError(Exception ex) {
                welcomeLatch.countDown();
            }
        };
        client.connect();
        welcomeLatch.await(5, TimeUnit.SECONDS);
        Thread.sleep(first ? 800 : 200); // 首次等待消息被服务器处理并写盘
        client.close();
        Thread.sleep(300);
        return result.get();
    }

    private static class WelcomeInfo {
        String playerId;
        String[] slots;
        Double x;
    }
}
