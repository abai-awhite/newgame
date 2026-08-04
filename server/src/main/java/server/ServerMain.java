package server;

import server.WorldStore.WorldMeta;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

/**
 * 游戏服务器入口。
 *
 * <p>用法：</p>
 * <ul>
 *   <li>{@code java -jar game.jar -server} — 交互选择世界 + 配置后启动</li>
 *   <li>{@code java -jar game.jar -server <世界名>} — 跳过世界选择，交互配置后启动</li>
 * </ul>
 *
 * <p>启动后会交互询问端口、最大玩家数、区块线程数（回车=用默认值），确认后启动。
 * 无需修改 config.json。</p>
 *
 * <p>不传 -server 参数时由 {@link Main} 启动桌面客户端。</p>
 */
public class ServerMain {

    public static void main(String[] args) throws Exception {
        // 解析参数：跳过 -server，第一个非 -server 参数为 worldName（可省略，省略则交互选择）
        String worldName = null;
        for (String arg : args) {
            if ("-server".equals(arg)) continue;
            worldName = arg;
            break;
        }

        ServerConfig config = ServerConfig.load("server/config.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        // 未指定世界名 → 控制台交互选择
        if (worldName == null) {
            worldName = interactiveSelectWorld(reader);
            if (worldName == null) {
                System.out.println("未选择世界，服务器退出。");
                return;
            }
        }

        // 交互配置：端口、最大玩家、区块线程（回车=用默认值）
        System.out.println("\n========== 服务器配置 ==========");
        int wsPort = promptInt(reader, "WebSocket 端口", config.wsPort);
        int maxPlayers = promptInt(reader, "最大玩家数", config.maxPlayers);
        int chunkThreads = promptInt(reader, "区块生成线程数", config.chunkThreads);

        // 确认
        System.out.println("\n========== 确认启动 ==========");
        System.out.println("  世界:     " + worldName);
        System.out.println("  端口:     " + wsPort);
        System.out.println("  最大玩家: " + maxPlayers);
        System.out.println("  区块线程: " + chunkThreads);
        System.out.print("确认启动？ (Y/n): ");
        String confirm = reader.readLine();
        if (confirm != null && !confirm.trim().isEmpty()
                && !confirm.trim().equalsIgnoreCase("y")) {
            System.out.println("已取消，服务器退出。");
            return;
        }

        // 从世界元数据读取种子（保证旧存档区块一致）
        WorldMeta meta = WorldStore.loadMeta(worldName);
        long seed = (meta != null) ? meta.seed : config.seed;

        System.out.println("\n启动世界: " + worldName + " (种子 " + seed + ")");
        GameServer gameServer = new GameServer(wsPort, seed, worldName, chunkThreads, maxPlayers);
        gameServer.start();

        System.out.println("==========================================");
        System.out.println("游戏服务器已启动");
        System.out.println("  游戏服务:  ws://localhost:" + wsPort);
        System.out.println("  世界名称:  " + worldName + " (种子 " + seed + ")");
        System.out.println("  最大玩家:  " + maxPlayers);
        System.out.println("==========================================");

        Runtime.getRuntime().addShutdownHook(new Thread(gameServer::shutdown));
    }

    /** 交互读取整数，回车=用默认值，输入无效则重复询问。 */
    private static int promptInt(BufferedReader reader, String label, int defaultValue) throws Exception {
        while (true) {
            System.out.print(label + " (默认 " + defaultValue + "): ");
            String line = reader.readLine();
            if (line == null || line.trim().isEmpty()) return defaultValue;
            try {
                int v = Integer.parseInt(line.trim());
                if (v > 0) return v;
                System.out.println("必须大于 0。");
            } catch (NumberFormatException e) {
                System.out.println("输入无效，请输入数字。");
            }
        }
    }

    /** 控制台交互选择世界：列出 world/ 下所有世界，用户输入序号选择。 */
    private static String interactiveSelectWorld(BufferedReader reader) {
        List<WorldMeta> worlds = WorldStore.listWorlds();
        if (worlds.isEmpty()) {
            System.out.println("未找到任何世界存档（world/ 目录为空）。");
            System.out.println("请先通过客户端创建世界。");
            return null;
        }

        System.out.println("\n========== 选择世界 ==========");
        for (int i = 0; i < worlds.size(); i++) {
            WorldMeta w = worlds.get(i);
            System.out.printf("  %d. %s  (种子哈希: %s)%n", i + 1, w.name, w.seedHash);
        }
        System.out.println("  0. 退出");
        System.out.print("请输入序号: ");

        try {
            String line = reader.readLine();
            if (line == null) return null;
            int idx = Integer.parseInt(line.trim());
            if (idx == 0) return null;
            if (idx < 1 || idx > worlds.size()) {
                System.out.println("无效序号。");
                return null;
            }
            return worlds.get(idx - 1).name;
        } catch (Exception e) {
            System.out.println("输入无效: " + e.getMessage());
            return null;
        }
    }
}
