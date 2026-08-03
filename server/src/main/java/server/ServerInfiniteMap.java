package server;

import main.world.Chunk;
import main.world.ChunkPos;
import main.world.InfiniteMap;
import server.world.ChunkGenWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * 服务端地图：继承 core 的 InfiniteMap 纯逻辑实现，接入多线程区块生成。
 *
 * <p>额外维护一个"方块变更日志"，供网络同步模块在每 tick 结束后
 * 提取增量（放置/破坏）推送给前端，避免每次全量发送区块。</p>
 *
 * <p>变更记录格式：{tileX, tileY, oldType, newType, oldLevel, newLevel}，
 * 便于服务端逻辑判断破坏（old!=AIR 且 new==AIR）与放置（old==AIR 且 new!=AIR），
 * 并携带流体水位（level 0=满格 ~ 15=最薄，一格最多 16 级）供客户端增量同步。</p>
 */
public class ServerInfiniteMap extends InfiniteMap {

    /** 单格方块变更记录：{tileX, tileY, oldType, newType, oldLevel, newLevel} */
    private final List<int[]> tileChanges = Collections.synchronizedList(new ArrayList<>());

    /** 多线程区块生成器 */
    private final ChunkGenWorker chunkGen;

    public ServerInfiniteMap(long seed, String worldName, int chunkThreads) {
        this(seed, worldName, chunkThreads, false);
    }

    public ServerInfiniteMap(long seed, String worldName, int chunkThreads, boolean migrateLegacy) {
        super(seed, worldName, migrateLegacy);
        this.chunkGen = new ChunkGenWorker(chunkThreads);
    }

    /**
     * 覆写 getChunk：优先从缓存取；未生成时先尝试同步生成（保证读取正确），
     * 若区块正在后台生成中则等待其结果，避免重复生成。
     */
    @Override
    public Chunk getChunk(ChunkPos pos) {
        Chunk cached = chunks.get(pos);
        if (cached != null) return cached;

        // 区块正在后台生成 -> 等待其结果（通常很快）
        Future<Chunk> future = chunkGen.getFuture(pos);
        if (future != null) {
            try {
                Chunk c = future.get();
                chunks.put(pos, c);
                enforceChunkLimit();
                return c;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                System.err.println("后台生成区块失败 " + pos + ": " + e.getCause());
            }
        }

        // 兜底：同步生成
        Chunk c = loadOrGenerateChunk(pos);
        chunks.put(pos, c);
        enforceChunkLimit();
        return c;
    }

    /**
     * 预加载玩家周围区块：将缺失区块提交到生成线程池（异步并行生成）。
     * 由主协调线程周期性调用，区块完成后经 drainReadyChunks 回收入缓存。
     */
    public void preloadChunks(int pcx, int pcy, int radius) {
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                ChunkPos pos = new ChunkPos(pcx + dx, pcy + dy);
                if (chunks.containsKey(pos) || chunkGen.isGenerating(pos)) continue;
                chunkGen.request(pos, () -> loadOrGenerateChunk(pos));
            }
        }
    }

    /**
     * 取回所有已生成完成的区块并放入缓存。返回本次新就绪的区块列表，
     * 供主协调线程广播给客户端。
     */
    public List<ChunkPos> drainReadyChunks() {
        List<Map.Entry<ChunkPos, Chunk>> readyEntries = chunkGen.drainReady();
        if (readyEntries.isEmpty()) return List.of();
        List<ChunkPos> ready = new ArrayList<>();
        for (Map.Entry<ChunkPos, Chunk> e : readyEntries) {
            chunks.put(e.getKey(), e.getValue());
            ready.add(e.getKey());
        }
        enforceChunkLimit();
        return ready;
    }

    @Override
    public void setTileType(int tileX, int tileY, int type) {
        int oldType = getTileType(tileX, tileY);
        if (oldType == type) return;
        int oldLevel = getFluidLevel(tileX, tileY);
        super.setTileType(tileX, tileY, type);
        tileChanges.add(new int[]{tileX, tileY, oldType, type, oldLevel, Chunk.isFluid(type) ? getFluidLevel(tileX, tileY) : 0});
    }

    @Override
    public void setFluidLevel(int tileX, int tileY, int level) {
        int oldLevel = getFluidLevel(tileX, tileY);
        if (oldLevel == level) return;
        int type = getTileType(tileX, tileY);
        super.setFluidLevel(tileX, tileY, level);
        tileChanges.add(new int[]{tileX, tileY, type, type, oldLevel, level});
    }

    @Override
    public void setTileTypeAndLevel(int tileX, int tileY, int type, int level) {
        int oldType = getTileType(tileX, tileY);
        int oldLevel = getFluidLevel(tileX, tileY);
        if (oldType == type && oldLevel == level) return;
        super.setTileTypeAndLevel(tileX, tileY, type, level);
        int newLevel = Chunk.isFluid(type) ? getFluidLevel(tileX, tileY) : 0;
        tileChanges.add(new int[]{tileX, tileY, oldType, type, oldLevel, newLevel});
    }

    /**
     * 取出并清空自上次调用以来的所有方块变更。
     * 线程安全：与 tick 线程调用 setTileType 配合使用。
     */
    public List<int[]> drainTileChanges() {
        synchronized (tileChanges) {
            if (tileChanges.isEmpty()) return List.of();
            List<int[]> copy = new ArrayList<>(tileChanges);
            tileChanges.clear();
            return copy;
        }
    }

    /** 供序列化使用：按区块访问原始 tile 数据。 */
    public int getChunkTile(int localX, int localY, ChunkPos pos) {
        return getChunk(pos).getTile(localX, localY);
    }

    /** 供序列化使用：按区块访问流体水位数据。 */
    public int getChunkFluidLevel(int localX, int localY, ChunkPos pos) {
        return getChunk(pos).getFluidLevel(localX, localY);
    }

    public boolean isAir(int type) {
        return type == Chunk.AIR;
    }

    @Override
    public void shutdown() {
        chunkGen.shutdown();
        super.shutdown();
    }
}
