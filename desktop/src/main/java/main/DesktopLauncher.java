package main;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import client.GdxGame;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Desktop 启动器（libGDX）。
 *
 * <p>启动流程：检测本地 8081 WebSocket 服务，若未启动则以子进程自动拉起
 * {@code server.ServerMain}（同一目录的 out/classes + lib/*），然后打开游戏窗口。</p>
 */
public class DesktopLauncher {

    public static final int WS_PORT = 8081;

    public static void main(String[] args) {
        ensureServerRunning();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("2D 沙盒游戏");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(true);
        // 图标资源若在 classpath 可达才设置，避免启动崩溃
        if (DesktopLauncher.class.getClassLoader().getResource("player/player-1.png") != null) {
            config.setWindowIcon("player/player-1.png");
        }
        new Lwjgl3Application(new GdxGame(), config);
    }

    /** 检测 8081 端口，未就绪则自动拉起后端服务器（等待最多 20 秒）。 */
    private static void ensureServerRunning() {
        if (portOpen(WS_PORT, 600)) {
            System.out.println("游戏服务器已运行 (ws://localhost:" + WS_PORT + ")");
            return;
        }
        System.out.println("检测到游戏服务器未启动，正在自动启动 server.ServerMain ...");
        try {
            String cp = new File("out/classes").getAbsolutePath() + File.pathSeparator + System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", cp, "server.ServerMain");
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // 读取子进程输出（避免管道阻塞）并打印
            Thread outThread = new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) System.out.println("[server] " + line);
                } catch (IOException ignored) { /* 进程退出后流关闭 */ }
            });
            outThread.setDaemon(true);
            outThread.start();

            for (int i = 0; i < 40; i++) {
                if (portOpen(WS_PORT, 400)) {
                    System.out.println("服务器已就绪");
                    return;
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            System.out.println("等待服务器超时，仍将尝试连接（请确认已编译 out/classes）");
        } catch (IOException e) {
            System.err.println("自动启动服务器失败: " + e.getMessage());
        }
    }

    private static boolean portOpen(int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
