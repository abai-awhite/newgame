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
 * <p>启动流程：直接打开游戏窗口（不自动拉起后端）。
 * 当用户在客户端选择世界后，调用 {@link #launchServerForWorld(String)} 以子进程
 * 拉起 {@code java -jar game.jar -server <world>}（或 classpath 模式），
 * 然后客户端连接该服务器。</p>
 */
public class DesktopLauncher {

    public static final int WS_PORT = 8081;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("2D 沙盒游戏");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(true);
        if (DesktopLauncher.class.getClassLoader().getResource("player/player-1.png") != null) {
            config.setWindowIcon("player/player-1.png");
        }
        new Lwjgl3Application(new GdxGame(), config);
    }

    /**
     * 为指定世界拉起后端服务器（如果尚未运行）。
     * 从 jar 运行时用 {@code java -jar game.jar -server <world>}，
     * 从 classpath 运行时用 {@code java -cp ... Main -server <world>}。
     * 等待最多 20 秒服务器就绪。
     *
     * @return true 如果服务器已运行或成功启动
     */
    public static boolean launchServerForWorld(String worldName) {
        if (portOpen(WS_PORT, 600)) {
            System.out.println("游戏服务器已运行 (ws://localhost:" + WS_PORT + ")");
            return true;
        }

        System.out.println("正在启动服务器（世界: " + worldName + "）...");

        try {
            String[] cmd;
            String classPath = System.getProperty("java.class.path");
            String jarPath = findSelfJar();

            if (jarPath != null) {
                // 从 jar 运行
                cmd = new String[]{"java", "-jar", jarPath, "-server", worldName};
            } else {
                // 从 classpath 运行（开发模式）
                cmd = new String[]{"java", "-cp", classPath, "Main", "-server", worldName};
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            Process p = pb.start();

            // 读取子进程输出（避免管道阻塞）
            Thread outThread = new Thread(() -> {
                try (java.io.BufferedReader r = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) System.out.println("[server] " + line);
                } catch (IOException ignored) { }
            });
            outThread.setDaemon(true);
            outThread.start();

            // 等待服务器就绪
            for (int i = 0; i < 40; i++) {
                if (portOpen(WS_PORT, 400)) {
                    System.out.println("服务器已就绪");
                    return true;
                }
                try { Thread.sleep(500); } catch (InterruptedException e) { break; }
            }
            System.out.println("等待服务器超时，仍将尝试连接");
            return false;
        } catch (IOException e) {
            System.err.println("启动服务器失败: " + e.getMessage());
            return false;
        }
    }

    /** 查找当前运行的 jar 文件路径（如果不是从 jar 运行则返回 null）。 */
    private static String findSelfJar() {
        try {
            java.net.URL url = DesktopLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            if ("file".equals(url.getProtocol())) {
                File f = new File(url.toURI());
                if (f.isFile() && f.getName().endsWith(".jar")) {
                    return f.getAbsolutePath();
                }
            }
        } catch (Exception ignored) { }
        return null;
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
