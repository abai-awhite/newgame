package server;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 通过 Chrome DevTools Protocol (CDP) 检查游戏页面状态。
 * 用法: java server.CdpProbe <pageId> <evaluateJs>
 */
public class CdpProbe {

    public static void main(String[] args) throws Exception {
        String pageId = args[0];
        String js = args[1];
        String listUrl = "http://localhost:9222/json/list";
        String json = httpGet(listUrl);
        // 找到目标页面条目：截取包含该 id 的花括号块
        String idToken = "\"id\": \"" + pageId + "\"";
        int idx = json.indexOf(idToken);
        if (idx < 0) {
            System.out.println("未找到页面: " + pageId);
            System.out.println("列表片段: " + json.substring(0, Math.min(500, json.length())));
            return;
        }
        int blockStart = json.lastIndexOf("{", idx);
        int blockEnd = json.indexOf("}", idx);
        String block = json.substring(blockStart, blockEnd);
        String wsUrl = null;
        int u = block.indexOf("\"webSocketDebuggerUrl\"");
        if (u >= 0) {
            int colon = block.indexOf(":", u);
            int start = block.indexOf("\"", colon + 1);
            if (start >= 0) {
                start++;
                int end = block.indexOf("\"", start);
                if (end > start) wsUrl = block.substring(start, end);
            }
        }
        if (wsUrl == null) {
            System.out.println("无法获取 WS URL: " + block);
            return;
        }
        System.out.println("连接到: " + wsUrl);

        // 发送 Runtime.evaluate
        String payload = "{\"id\":1,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":"
            + "\"" + escape(js) + "\",\"returnByValue\":true,\"awaitPromise\":true}}";
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();
        org.java_websocket.client.WebSocketClient ws = new org.java_websocket.client.WebSocketClient(new URI(wsUrl)) {
            @Override
            public void onOpen(org.java_websocket.handshake.ServerHandshake handshake) {
                send(payload);
            }
            @Override
            public void onMessage(String message) {
                queue.offer(message);
            }
            @Override
            public void onClose(int code, String reason, boolean remote) { }
            @Override
            public void onError(Exception ex) { }
        };
        ws.connect();
        String result = queue.poll(10, java.util.concurrent.TimeUnit.SECONDS);
        if (result != null) {
            // 提取 result.value
            System.out.println("结果: " + result);
        } else {
            System.out.println("超时");
        }
        ws.close();
        System.exit(0);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(3000);
        try (Scanner sc = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8).useDelimiter("\\A")) {
            return sc.hasNext() ? sc.next() : "";
        }
    }
}
