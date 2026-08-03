package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 静态文件服务器：托管前端页面（server/src/main/web）和
 * 游戏纹理（映射为 /textures/，支持多个资源目录按顺序回退，
 * 例如 core/src/main/resources 与项目根 src/main/resources）。
 */
public class WebStaticServer {

    private final HttpServer httpServer;

    public WebStaticServer(int port, String webDir, String... textureDirs) throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        Path webPath = Path.of(webDir);
        List<Path> texturePaths = new ArrayList<>();
        for (String dir : textureDirs) {
            texturePaths.add(Path.of(dir));
        }

        httpServer.createContext("/", exchange -> serveFile(exchange, webPath));
        httpServer.createContext("/textures/", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            String rel = requestPath.substring("/textures/".length());
            serveTexture(exchange, texturePaths, rel);
        });

        httpServer.setExecutor(null);
    }

    /** 依次在多个纹理目录中查找文件，命中即返回。 */
    private void serveTexture(HttpExchange exchange, List<Path> bases, String relative) throws IOException {
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        for (Path base : bases) {
            Path file = base.resolve(relative).normalize();
            if (!file.startsWith(base)) {
                send(exchange, 403, "Forbidden", "text/plain");
                return;
            }
            if (Files.isRegularFile(file)) {
                serveFile(exchange, base, relative);
                return;
            }
        }
        send(exchange, 404, "Not Found", "text/plain");
    }

    public void start() {
        httpServer.start();
        System.out.println("静态服务器已启动: http://localhost:" + httpServer.getAddress().getPort());
    }

    public int getPort() {
        return httpServer.getAddress().getPort();
    }

    private void serveFile(HttpExchange exchange, Path base) throws IOException {
        serveFile(exchange, base, "");
    }

    private void serveFile(HttpExchange exchange, Path base, String relative) throws IOException {
        String requestPath = relative.isEmpty()
            ? exchange.getRequestURI().getPath()
            : relative;

        if (requestPath.equals("/") || requestPath.isEmpty()) {
            requestPath = "index.html";
        }

        // 去掉开头的斜杠，避免被 Path.resolve 当作绝对路径
        while (requestPath.startsWith("/")) {
            requestPath = requestPath.substring(1);
        }

        Path file = base.resolve(requestPath).normalize();
        if (!file.startsWith(base)) {
            send(exchange, 403, "Forbidden", "text/plain");
            return;
        }

        if (!Files.isRegularFile(file)) {
            send(exchange, 404, "Not Found", "text/plain");
            return;
        }

        String contentType = guessContentType(file.getFileName().toString());
        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }

    private String guessContentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".txt")) return "text/plain; charset=utf-8";
        return "application/octet-stream";
    }

    private void send(HttpExchange exchange, int code, String body, String contentType) throws IOException {
        byte[] data = body.getBytes("UTF-8");
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(data);
        }
    }
}
