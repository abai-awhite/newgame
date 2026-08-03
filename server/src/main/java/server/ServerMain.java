package server;

/**
 * 前后端一体游戏服务器入口。
 *
 * <p>启动两个服务：</p>
 * <ul>
 *   <li>WebSocket 游戏服务（默认端口 8081，前端连接用）</li>
 *   <li>HTTP 静态服务（默认端口 8080，托管前端页面与纹理）</li>
 * </ul>
 *
 * <p>单人模式：直接启动即可连本地存档；多人开服：修改 server/config.json
 * （端口/最大玩家数等）后重启即生效，其他玩家连接本机 IP。</p>
 */
public class ServerMain {

    public static void main(String[] args) throws Exception {
        // 读取配置文件（支持命令行覆盖：java ... server.ServerMain [wsPort] [httpPort]）
        ServerConfig config = ServerConfig.load("server/config.json");
        int wsPort = args.length > 0 ? Integer.parseInt(args[0]) : config.wsPort;
        int httpPort = args.length > 1 ? Integer.parseInt(args[1]) : config.httpPort;

        System.out.println("服务器配置: " + config);

        // 启动静态文件服务器（前端页面 + 纹理，纹理支持多个资源目录回退）
        String webDir = System.getProperty("user.dir") + "/server/src/main/web";
        String textureDir = System.getProperty("user.dir") + "/core/src/main/resources";
        String extraTextureDir = System.getProperty("user.dir") + "/src/main/resources";
        WebStaticServer staticServer = new WebStaticServer(httpPort, webDir, textureDir, extraTextureDir);
        staticServer.start();

        // 启动 WebSocket 游戏服务器（多玩家）
        GameServer gameServer = new GameServer(wsPort, config.seed, config.worldName,
                config.chunkThreads);
        gameServer.start();

        System.out.println("==========================================");
        System.out.println("游戏服务器已启动");
        System.out.println("  前端页面:  http://localhost:" + httpPort);
        System.out.println("  游戏服务:  ws://localhost:" + wsPort);
        System.out.println("  世界名称:  " + config.worldName + " (种子 " + config.seed + ")");
        System.out.println("  最大玩家:  " + config.maxPlayers);
        System.out.println("  多人开服:  修改 server/config.json 后重启");
        System.out.println("==========================================");

        // 注册关闭钩子：退出时保存世界与玩家档案
        Runtime.getRuntime().addShutdownHook(new Thread(gameServer::shutdown));
    }
}
