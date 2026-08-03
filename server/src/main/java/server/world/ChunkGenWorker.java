package server.world;

import main.world.Chunk;
import main.world.ChunkPos;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * 区块生成线程池（多核并行）。
 *
 * <p>将"区块噪声生成"这一 CPU 密集任务分发到多个线程并行执行，
 * 生成完成的区块进入就绪队列，由主协调线程批量取回放入世界缓存。
 * 使用 ConcurrentHashMap + 幂等提交保证同一区块只生成一次。</p>
 *
 * <p>线程数由配置 chunkThreads 控制（默认 2，上限 4），避免线程过多导致延迟。</p>
 */
public class ChunkGenWorker {

    private final ExecutorService executor;
    private final Map<ChunkPos, Future<Chunk>> generating = new ConcurrentHashMap<>();
    private final Queue<Map.Entry<ChunkPos, Chunk>> readyQueue = new ConcurrentLinkedQueue<>();

    public ChunkGenWorker(int threads) {
        int n = Math.max(1, Math.min(threads, 4));
        this.executor = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "ChunkGen-" + Math.abs(r.hashCode() % 100));
            t.setDaemon(true);
            return t;
        });
        System.out.println("区块生成线程池已创建: " + n + " 线程");
    }

    /**
     * 提交区块生成任务（幂等：同一区块已提交/生成中则忽略）。
     *
     * @param pos       区块坐标
     * @param generator 区块生成逻辑（loadOrGenerateChunk）
     * @return 是否新提交
     */
    public boolean request(ChunkPos pos, Supplier<Chunk> generator) {
        Future<Chunk> existing = generating.get(pos);
        if (existing != null) return false;
        Future<Chunk> future = executor.submit(() -> {
            Chunk c = generator.get();
            readyQueue.offer(new AbstractMap.SimpleEntry<>(pos, c));
            return c;
        });
        Future<Chunk> raced = generating.putIfAbsent(pos, future);
        if (raced != null) {
            future.cancel(false);
            return false;
        }
        return true;
    }

    /** 该区块是否正在生成中。 */
    public boolean isGenerating(ChunkPos pos) {
        return generating.containsKey(pos);
    }

    /** 若该区块正在生成，返回其 Future；否则返回 null。 */
    public Future<Chunk> getFuture(ChunkPos pos) {
        return generating.get(pos);
    }

    /**
     * 取回所有已生成就绪的区块（并从未完成集合中移除）。
     * 线程安全，由主协调线程调用。
     */
    public List<Map.Entry<ChunkPos, Chunk>> drainReady() {
        if (readyQueue.isEmpty()) return List.of();
        List<Map.Entry<ChunkPos, Chunk>> out = new ArrayList<>();
        Map.Entry<ChunkPos, Chunk> e;
        while ((e = readyQueue.poll()) != null) {
            generating.remove(e.getKey());
            out.add(e);
        }
        return out;
    }

    /** 关闭线程池（等待已提交任务完成）。 */
    public void shutdown() {
        executor.shutdown();
    }
}
