/**
 * 统一入口：{@code java -jar game.jar -server} 启动服务端，
 * 不加参数启动桌面客户端（需 classpath 含 libGDX/LWJGL）。
 */
public class Main {
    public static void main(String[] args) throws Exception {
        boolean isServer = false;
        for (String arg : args) {
            if ("-server".equals(arg)) isServer = true;
        }
        if (isServer) {
            server.ServerMain.main(args);
        } else {
            main.DesktopLauncher.main(args);
        }
    }
}
