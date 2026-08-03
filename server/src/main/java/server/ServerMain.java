package server;

/**
 * 游戏服务器入口（桌面客户端连接用）。
 *
 * <p>启动 WebSocket 游戏服务（默认端口 8081，libGDX 桌面客户端连接用）。</p>
 *
 * <p>单人模式：直接启动即可连本地存档；多人开服：修改 server/config.json
 * （端口/最大玩家数等）后重启即生效，其他玩家连接本机 IP。</p>
 */
public class ServerMain {

    public static void main(String[] args) throws Exception {
        // 读取配置文件（支持命令行覆盖：java ... server.ServerMain [wsPort]）
        ServerConfig config = ServerConfig.load("server/config.json");
        int wsPort = args.length > 0 ? Integer.parseInt(args[0]) : config.wsPort;

        System.out.println("服务器配置: " + config);

        // 启动 WebSocket 游戏服务器（多玩家）
        GameServer gameServer = new GameServer(wsPort, config.seed, config.worldName,
                config.chunkThreads);
        gameServer.start();

        System.out.println("==========================================");
        System.out.println("游戏服务器已启动");
        System.out.println("  游戏服务:  ws://localhost:" + wsPort);
        System.out.println("  世界名称:  " + config.worldName + " (种子 " + config.seed + ")");
        System.out.println("  最大玩家:  " + config.maxPlayers);
        System.out.println("  多人开服:  修改 server/config.json 后重启");
        System.out.println("==========================================");

        // 注册关闭钩子：退出时保存世界与玩家档案
        Runtime.getRuntime().addShutdownHook(new Thread(gameServer::shutdown));
    }
}
