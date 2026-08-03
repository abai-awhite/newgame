package server;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 服务器配置：从 config.json 加载。
 *
 * <p>单人模式使用默认值；多人开服时修改 config.json 后重启生效。</p>
 */
public class ServerConfig {

    public final int wsPort;
    public final int httpPort;
    public final long seed;
    public final String worldName;
    public final int maxPlayers;
    public final int chunkThreads;
    public final double broadcastHz;
    public final String gameTitle;

    private ServerConfig(int wsPort, int httpPort, long seed, String worldName,
                         int maxPlayers, int chunkThreads,
                         double broadcastHz, String gameTitle) {
        this.wsPort = wsPort;
        this.httpPort = httpPort;
        this.seed = seed;
        this.worldName = worldName;
        this.maxPlayers = maxPlayers;
        this.chunkThreads = chunkThreads;
        this.broadcastHz = broadcastHz;
        this.gameTitle = gameTitle;
    }

    public static ServerConfig load(String path) {
        JSONObject obj;
        try {
            Path p = Paths.get(path);
            if (!Files.exists(p)) {
                System.out.println("配置文件不存在: " + path + "，使用默认配置");
                obj = new JSONObject();
            } else {
                obj = new JSONObject(Files.readString(p, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            System.err.println("读取配置文件失败: " + e.getMessage() + "，使用默认配置");
            obj = new JSONObject();
        }

        int wsPort = obj.optInt("wsPort", 8081);
        int httpPort = obj.optInt("httpPort", 8080);
        long seed = obj.optLong("seed", 0L);
        String worldName = obj.optString("worldName", "web-world");
        int maxPlayers = obj.optInt("maxPlayers", 16);
        int chunkThreads = obj.optInt("chunkThreads", 2);
        double broadcastHz = obj.optDouble("broadcastHz", 32.0);
        String gameTitle = obj.optString("gameTitle", "2D 沙盒游戏");

        return new ServerConfig(wsPort, httpPort, seed, worldName, maxPlayers, chunkThreads,
                broadcastHz, gameTitle);
    }

    @Override
    public String toString() {
        return "ServerConfig{wsPort=" + wsPort + ", httpPort=" + httpPort
                + ", seed=" + seed + ", worldName='" + worldName + '\''
                + ", maxPlayers=" + maxPlayers + ", chunkThreads=" + chunkThreads
                + ", broadcastHz=" + broadcastHz + '}';
    }
}
