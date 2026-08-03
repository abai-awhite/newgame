package server;

import main.world.ChunkPos;
import org.json.JSONObject;

import java.util.List;

/**
 * 广播线程（迁移计划 C2）：批量推送状态包，不阻塞主协调线程。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>主协调线程每 tick 生成一次"公共状态 JSON"（players/tiles/drops），
 *       避免对每个客户端重复序列化；</li>
 *   <li>广播线程取最新快照，为每个客户端组装个性化部分（新区块按已发送集合去重）并 send；</li>
 *   <li>快照覆盖策略：若上一快照尚未发完则直接丢弃，保证广播不积压、速率稳定（约 32Hz）。</li>
 * </ul>
 */
public class BroadcastService {

    /** 状态快照：公共 JSON（不含 chunks）+ 后台生成就绪的区块列表 */
    public static final class Snapshot {
        public final JSONObject base;
        public final List<ChunkPos> readyChunks;

        public Snapshot(JSONObject base, List<ChunkPos> readyChunks) {
            this.base = base;
            this.readyChunks = readyChunks;
        }
    }

    /** 发送回调：在广播线程中为所有客户端组装并发送消息（由 GameServer 实现） */
    public interface Sender {
        void sendAll(Snapshot snapshot);
    }

    private final Sender sender;
    private final Object lock = new Object();
    private Snapshot latest = null;
    private final Thread thread;
    private volatile boolean running = true;

    public BroadcastService(String name, Sender sender) {
        this.sender = sender;
        this.thread = new Thread(this::loop, name);
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    /** 主协调线程调用：发布最新状态快照（覆盖未发送的旧快照）。 */
    public void publish(Snapshot snapshot) {
        synchronized (lock) {
            latest = snapshot;
            lock.notifyAll();
        }
    }

    private void loop() {
        while (running) {
            Snapshot snap;
            synchronized (lock) {
                while (latest == null && running) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                snap = latest;
                latest = null;
            }
            if (snap == null) continue;

            try {
                sender.sendAll(snap);
            } catch (Exception e) {
                System.err.println("广播失败: " + e.getMessage());
            }

            // 控制广播速率（约 32Hz，与主协调 tick 同频）
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    public void shutdown() {
        running = false;
        synchronized (lock) {
            lock.notifyAll();
        }
        thread.interrupt();
    }
}
