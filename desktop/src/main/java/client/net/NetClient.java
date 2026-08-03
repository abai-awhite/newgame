package client.net;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import org.json.JSONObject;

import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WebSocket 客户端封装（org.java_websocket）。
 * 消息在库的接收线程回调，统一入队，由 GdxGame 渲染线程轮询消费，避免跨线程操作。
 */
public class NetClient {

    public interface Listener {
        /** 收到服务器消息（渲染线程） */
        void onJson(JSONObject msg);

        void onOpen();

        void onClosed();
    }

    private final Listener listener;
    private WebSocketClient ws;
    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
    private volatile boolean connected;

    public NetClient(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    /** 连接服务器（异步；成功后回调 onOpen）。 */
    public void connect(String url) {
        disconnect();
        try {
            ws = new WebSocketClient(URI.create(url)) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    connected = true;
                    queue.add(OPEN);
                }

                @Override
                public void onMessage(String message) {
                    try {
                        queue.add(new JSONObject(message));
                    } catch (Exception e) {
                        System.err.println("JSON 解析失败: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    connected = false;
                    queue.add(CLOSE);
                }

                @Override
                public void onError(Exception ex) {
                    // 错误后通常跟随 onClose
                }
            };
            ws.connect();
        } catch (Exception e) {
            System.err.println("连接失败: " + e.getMessage());
            connected = false;
            queue.add(CLOSE);
        }
    }

    private static final Object OPEN = new Object();
    private static final Object CLOSE = new Object();

    /** 渲染线程每帧调用：处理队列中的事件/消息。 */
    public void drain() {
        Object o;
        while ((o = queue.poll()) != null) {
            if (o == OPEN) {
                listener.onOpen();
            } else if (o == CLOSE) {
                listener.onClosed();
            } else if (o instanceof JSONObject) {
                listener.onJson((JSONObject) o);
            }
        }
    }

    public void send(JSONObject msg) {
        WebSocketClient w = ws;
        if (w != null && w.isOpen()) {
            w.send(msg.toString());
        }
    }

    public void disconnect() {
        WebSocketClient w = ws;
        ws = null;
        connected = false;
        if (w != null) {
            try {
                w.close();
            } catch (Exception ignored) { /* ignore */ }
        }
    }
}
