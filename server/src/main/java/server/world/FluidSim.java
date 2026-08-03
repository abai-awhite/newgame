package server.world;

import main.world.Chunk;
import main.world.ChunkPos;
import server.ServerInfiniteMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 服务器权威流体模拟（Terraria 式，16 级水位，总量守恒）。
 *
 * <p>每 {@link #FLUID_TICK_EVERY} 个 tick（8Hz）由服务器主协调线程调用一次
 * {@link #step()}，只遍历已加载区块；未加载区块/世界外视为实心边界（水不会凭空
 * 流失，邻居区块加载后自然续流）。所有变更经 {@code ServerInfiniteMap.setTileTypeAndLevel}
 * 写入，自动进入方块变更日志随增量广播。</p>
 *
 * <p>水位语义：level = 欠满量，amount = {@link #FULL_AMOUNT} - level；
 * 0=满格（16 单位）、15=最薄（1 单位），一格水最多 16 级，amount 归零即变为空气。
 * 没有永续"源"，总量守恒——模拟不创造、不消灭水（除水×岩浆反应消耗）。因此挖沟排水、
 * 用水桶舀水后湖面会自然下降（消退），这正是 Terraria 的核心行为。</p>
 *
 * <p>流动规则：先向下坠落/下渗——下方是空气时整格下落，下方有水且不满时上方水量
 * <b>尽量全部下沉填满下方</b>（重力聚合，底部先满，杜绝"水悬空"）；落地后向两侧铺开
 * 找平（限速）。静止时连通水域表面趋于平面。岩浆同规则但侧向铺开更慢。</p>
 *
 * <p>方块更新：任何方块变更应调用 {@link #markBlockUpdate(int, int)}，把变更点周围
 * 9×9 区域（半径 {@link #BLOCK_UPDATE_RADIUS}）标记为待更新，下一步模拟优先处理该区域；
 * 固体替换流体时调用 {@link #displace(int, int, int, int)} 把被排挤的液体位移到相邻
 * 水域或上方格（不凭空消失）。</p>
 *
 * <p>调试：加 JVM 参数 {@code -Dfluid.debug=true}，每 50 步打印水/岩浆总量与活动格数，
 * 用于验证总量守恒与消退效果。</p>
 */
public class FluidSim {

    /** 流体模拟间隔（tick）：每 4 tick = 8Hz 跑一步 */
    public static final int FLUID_TICK_EVERY = 4;
    /** 满格水量：一格水最多 16 级（amount = 16 - level，level 见 Chunk.MAX_FLUID_LEVEL） */
    public static final int FULL_AMOUNT = 16;
    /** 一格水最多 16 级（FULL_AMOUNT=16 单位）；水桶 = 一整格：舀水/倒水一次取/放一整格（Terraria 标准） */
    public static final int BUCKET_AMOUNT = FULL_AMOUNT;
    /** 水每步侧向铺开最大转移量（整格：16 单位一步到位，水面快速流平无梯度） */
    public static final int WATER_SPREAD_CAP = FULL_AMOUNT;
    /** 岩浆每步侧向铺开最大转移量（更慢，半格） */
    public static final int LAVA_SPREAD_CAP = FULL_AMOUNT / 2;
    /** 向下坠落每步最大转移量（整格）：下方是空气时直接整格下落；下方有水且不满时上方尽量下沉合并 */
    public static final int FALL_CAP = FULL_AMOUNT;
    /** 方块变更触发的更新半径（格）：变更点周围 9×9 区域全部触发更新 */
    public static final int BLOCK_UPDATE_RADIUS = 4;
    /** 最小深度：低于该水量（amount < MIN_DEPTH）的薄水直接消失（Terraria：铺太薄 → 蒸发）。
     *  16 级体系下 MIN_DEPTH=1 表示最薄的 1 单位水蒸发，一格水最少保留 1 单位。
     *  只蒸发孤立水滴（同型流体邻居=0），连片水域完全不蒸发。 */
    public static final int MIN_DEPTH = 1;
    /** 全图 Settling 最大步数上限（加载世界时强制稳定液体，防极端情况跑不完） */
    public static final int SETTLE_MAX_STEPS = 500;
    /** 水×岩浆反应产物（Terraria：黑曜石） */
    public static final int OBSIDIAN = Chunk.OBSIDIAN;

    private static final boolean DEBUG = Boolean.getBoolean("fluid.debug");
    private static final int DEBUG_EVERY = 50;

    private final ServerInfiniteMap map;
    private int tickCounter = 0;
    private long stepCounter = 0;
    /** 本步发生流体转移（写入）的格数：>0 表示水域仍在流动 */
    private int activeCells = 0;
    /** 正在执行全图 Settling（进世界时 settleAll 循环中），此阶段不蒸发薄水 */
    private boolean settling = false;
    /** 稳定态（Terraria Settled）：全图无任何流动/蒸发，处于平衡 */
    private boolean settled = false;

    /** 本步已处理格（已作为水源流出，或已收到水），保证每格每步最多移动一次 */
    private final Set<Long> processed = new HashSet<>();
    /** 方块变更触发的待更新格（9×9 区域），下一步模拟优先处理 */
    private final Set<Long> dirty = new HashSet<>();

    public FluidSim(ServerInfiniteMap map) {
        this.map = map;
    }

    /** 由服务器主 tick 线程每 tick 调用；按节奏驱动 step。 */
    public void tick() {
        if (++tickCounter >= FLUID_TICK_EVERY) {
            tickCounter = 0;
            step();
        }
    }

    private static long key(int tx, int ty) {
        return ((long) tx << 32) | (ty & 0xFFFFFFFFL);
    }

    /** 读取方块（世界外/未加载区块视为实心边界，不触发生成）。 */
    private int tileAt(int tx, int ty) {
        if (ty < 0 || ty >= Chunk.WORLD_HEIGHT) return Chunk.STONE;
        int cx = Math.floorDiv(tx, Chunk.SIZE);
        int cy = Math.floorDiv(ty, Chunk.SIZE);
        if (!map.isChunkLoaded(cx, cy)) return Chunk.STONE;
        return map.getTileType(tx, ty);
    }

    /** 读取水位（世界外/未加载返回 0）。 */
    private int levelAt(int tx, int ty) {
        if (ty < 0 || ty >= Chunk.WORLD_HEIGHT) return 0;
        int cx = Math.floorDiv(tx, Chunk.SIZE);
        int cy = Math.floorDiv(ty, Chunk.SIZE);
        if (!map.isChunkLoaded(cx, cy)) return 0;
        return map.getFluidLevel(tx, ty);
    }

    private boolean isProcessed(int tx, int ty) {
        return processed.contains(key(tx, ty));
    }

    /** 目标格能否接收流体：必须位于已加载区块、本步未处理、且为空气或同型流体。 */
    private boolean canReceive(int tx, int ty, int type) {
        if (ty < 0 || ty >= Chunk.WORLD_HEIGHT) return false;
        int cx = Math.floorDiv(tx, Chunk.SIZE);
        int cy = Math.floorDiv(ty, Chunk.SIZE);
        if (!map.isChunkLoaded(cx, cy)) return false;
        if (isProcessed(tx, ty)) return false;
        int t = map.getTileType(tx, ty);
        return t == Chunk.AIR || t == type;
    }

    /** amount -> level（钳制到 0~MAX_FLUID_LEVEL）。 */
    private static int levelOf(int amount) {
        return Math.max(0, Math.min(FULL_AMOUNT - amount, Chunk.MAX_FLUID_LEVEL));
    }

    /** 写入流体结果（amount<=0 写入空气），并标记本步已处理。 */
    private void write(int tx, int ty, int type, int amount) {
        if (amount <= 0) {
            map.setTileTypeAndLevel(tx, ty, Chunk.AIR, 0);
        } else {
            map.setTileTypeAndLevel(tx, ty, type, levelOf(amount));
        }
        processed.add(key(tx, ty));
        activeCells++;   // 仅流体流动（stepCell 内）经过 write，用于静止判定
    }

    /** 方块变更触发更新：把 (tx,ty) 周围 9×9 区域（半径 4）全部标记为待更新，
     *  并让水域恢复"流动态"（不再处于 Settled 稳定态）。 */
    public void markBlockUpdate(int tx, int ty) {
        settled = false;
        for (int dy = -BLOCK_UPDATE_RADIUS; dy <= BLOCK_UPDATE_RADIUS; dy++) {
            for (int dx = -BLOCK_UPDATE_RADIUS; dx <= BLOCK_UPDATE_RADIUS; dx++) {
                dirty.add(key(tx + dx, ty + dy));
            }
        }
    }

    /** 读取单格流体并执行一步计算（脏区优先处理用）；本步已处理过的格跳过，保证每格每步最多动一次。 */
    private void stepCellAt(int tx, int ty) {
        if (isProcessed(tx, ty)) return;
        int type = map.getTileType(tx, ty);
        if (!Chunk.isFluid(type)) return;
        stepCell(type, map.getFluidLevel(tx, ty), tx, ty);
    }

    /** 执行一步流体模拟（总量守恒 + 找平）。 */
    public void step() {
        stepCounter++;
        processed.clear();
        activeCells = 0;

        // 0) 方块变更触发的 9×9 区域优先处理（保证变化立刻响应，本步内先于全量扫描）。
        //    按 (ty, tx) 自上而下排序，避免 HashSet 随机顺序打乱"上方水先下沉合并"的顺序。
        if (!dirty.isEmpty()) {
            List<Long> dirtyKeys = new ArrayList<>(dirty);
            dirty.clear();
            dirtyKeys.sort(java.util.Comparator
                    .comparingInt((Long k) -> (int) k.longValue())          // 先 ty（上→下）
                    .thenComparingInt(k -> (int) (k >> 32)));                // 再 tx（左→右）
            for (long k : dirtyKeys) {
                int tx = (int) (k >> 32);
                int ty = (int) k;
                if (ty < 0 || ty >= Chunk.WORLD_HEIGHT) continue;
                int cx = Math.floorDiv(tx, Chunk.SIZE), cy = Math.floorDiv(ty, Chunk.SIZE);
                if (!map.isChunkLoaded(cx, cy)) continue;
                stepCellAt(tx, ty);
            }
        }

        // 1) 扫描已加载区块：按区块 (cy, cx) 排序 → 全局自上而下（上方格先处理），
        //    保证"上方水下沉合并到下方水"在同一大步内必然成功（跨区块也不被 processed 阻断）。
        //    本步写入的格都会被标记 processed，扫描跳过，保证每格最多动一次。
        List<ChunkPos> sortedChunks = map.loadedChunkKeys().stream()
                .sorted(java.util.Comparator.comparingInt((ChunkPos p) -> p.cy).thenComparingInt(p -> p.cx))
                .toList();
        for (ChunkPos pos : sortedChunks) {
            Chunk chunk = map.getChunk(pos);
            int bx = pos.cx * Chunk.SIZE, by = pos.cy * Chunk.SIZE;
            for (int ly = 0; ly < Chunk.SIZE; ly++) {
                for (int lx = 0; lx < Chunk.SIZE; lx++) {
                    int type = chunk.getTile(lx, ly);
                    if (!Chunk.isFluid(type)) continue;
                    int tx = bx + lx, ty = by + ly;
                    if (isProcessed(tx, ty)) continue;
                    stepCell(type, chunk.getFluidLevel(lx, ly), tx, ty);
                }
            }
        }

        applyReactions();

        // 2) 蒸发：低于最小深度的孤立水滴直接消失（Terraria：铺太薄 → 蒸发）。
        //    Settling 阶段不蒸发——先让水流动到位，稳定后再由常规 step 蒸发。
        if (!settling) evaporateThin();

        // 3) 表层整平：流动稳定后（无转移），对连通表层格水量均分。
        //    只动表层（正上方是空气的格），底层完全不动——杜绝"底层抽水补表层→重力拉回"
        //    的上下抽风振荡。表层均分后水平 diff=0，侧向不触发；下方满格，下沉不触发。
        //    开放边界：边缘被拉高→扩散排水→总量减→avg 单调降→最终静止（水只出不进，不振荡）。
        if (activeCells == 0) levelizeSurfaces();

        // 4) 稳定态标记：全图无任何流动/蒸发时标记 Settled（供 settleAll 循环判断收敛）
        settled = activeCells == 0;

        if (DEBUG && stepCounter % DEBUG_EVERY == 0) logState();
    }

    /** Terraria "Settling Liquids"：全图液体强制稳定（进世界时调用）。
     *  持续跑 step 直到所有液体静止（Settled），或达到 {@link #SETTLE_MAX_STEPS} 上限。
     *  Settling 期间不蒸发薄水——先让水流动到位，稳定后再由常规 step 蒸发。 */
    public void settleAll() {
        settling = true;
        int steps = 0;
        while (!settled && steps < SETTLE_MAX_STEPS) {
            step();
            steps++;
        }
        settling = false;
        System.out.println("[Fluid] Settling liquids: " + steps + " 步后 settled=" + settled);
    }

    /** 当前是否处于稳定态（全图无流动/蒸发）。 */
    public boolean isSettled() {
        return settled;
    }

    /** 蒸发扫描：低于最小深度（amount < MIN_DEPTH）且同型流体邻居 = 0 的"孤立水滴"
     *  直接消失（Terraria：铺太薄 → 蒸发）。
     *  <p>只蒸发孤立水滴（四周无任何同型流体），连片水域完全不蒸发。
     *  因此密封水池/湖内部水量守恒完全不受影响，只有溅出的孤立水滴会蒸发消失。</p> */
    private void evaporateThin() {
        for (ChunkPos pos : map.loadedChunkKeys()) {
            Chunk chunk = map.getChunk(pos);
            int bx = pos.cx * Chunk.SIZE, by = pos.cy * Chunk.SIZE;
            for (int ly = 0; ly < Chunk.SIZE; ly++) {
                for (int lx = 0; lx < Chunk.SIZE; lx++) {
                    int t = chunk.getTile(lx, ly);
                    if (!Chunk.isFluid(t)) continue;
                    if (FULL_AMOUNT - chunk.getFluidLevel(lx, ly) >= MIN_DEPTH) continue;
                    int tx = bx + lx, ty = by + ly;
                    if (tileAt(tx - 1, ty) == t) continue;
                    if (tileAt(tx + 1, ty) == t) continue;
                    if (tileAt(tx, ty - 1) == t) continue;
                    if (tileAt(tx, ty + 1) == t) continue;
                    map.setTileTypeAndLevel(tx, ty, Chunk.AIR, 0);
                    activeCells++;
                }
            }
        }
    }

    /** 表层整平（用户方案 / "连通区域压力均衡"）：
     *  <p>对所有连通的<strong>表层格</strong>（正上方是空气的流体格）水量求和 ÷ 格数 → 均分。
     *  <strong>底层水量完全不动</strong>——杜绝"底层抽水补表层 → 重力拉回底层"的上下抽风振荡。</p>
     *
     *  <p>为什么不振荡：</p>
     *  <ul>
     *    <li>表层均分后水平 diff=0，侧向扩散不触发；</li>
     *    <li>底层不动，下方满格（稳定态），下沉不触发；</li>
     *    <li>开放边界：边缘被拉高 → 下一步侧向扩散排水 → 总量减 → 下次整平 avg 更低
     *        → 单调收敛（水只出不进，不反向流动，无周期振荡）。</li>
     *  </ul>
     *
     *  <p>已平（差 ≤1）时跳过，防无谓写入/广播。通过 setTileTypeAndLevel 直接写（不走 write()），
     *  不增加 activeCells，不影响 settled 判定。</p>
     */
    private void levelizeSurfaces() {
        Set<Long> done = new HashSet<>();
        for (ChunkPos pos : map.loadedChunkKeys()) {
            Chunk chunk = map.getChunk(pos);
            int bx = pos.cx * Chunk.SIZE, by = pos.cy * Chunk.SIZE;
            for (int ly = 0; ly < Chunk.SIZE; ly++) {
                for (int lx = 0; lx < Chunk.SIZE; lx++) {
                    int type = chunk.getTile(lx, ly);
                    if (!Chunk.isFluid(type)) continue;
                    int tx = bx + lx, ty = by + ly;
                    long k = key(tx, ty);
                    if (done.contains(k)) continue;
                    // 表层格：正上方是空气
                    if (tileAt(tx, ty - 1) != Chunk.AIR) continue;

                    // BFS 找水平连通的同型表层格
                    List<int[]> group = new ArrayList<>();
                    java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
                    queue.add(new int[]{tx, ty});
                    done.add(k);
                    while (!queue.isEmpty()) {
                        int[] c = queue.poll();
                        group.add(c);
                        for (int dir = -1; dir <= 1; dir += 2) {
                            int nx = c[0] + dir, ny = c[1];
                            long nk = key(nx, ny);
                            if (done.contains(nk)) continue;
                            if (tileAt(nx, ny) != type) continue;
                            if (tileAt(nx, ny - 1) != Chunk.AIR) continue; // 也是表层
                            done.add(nk);
                            queue.add(new int[]{nx, ny});
                        }
                    }
                    if (group.size() < 2) continue;

                    // 检查是否已平（差 ≤1），已平跳过
                    int minA = FULL_AMOUNT, maxA = 0;
                    long sum = 0;
                    for (int[] c : group) {
                        int a = FULL_AMOUNT - map.getFluidLevel(c[0], c[1]);
                        minA = Math.min(minA, a);
                        maxA = Math.max(maxA, a);
                        sum += a;
                    }
                    if (sum <= 0 || maxA - minA <= 1) continue;

                    // 均分（余数逐个 +1）
                    int n = group.size();
                    long base = sum / n;
                    int rem = (int) (sum % n);
                    for (int i = 0; i < n; i++) {
                        int a = (int) base + (i < rem ? 1 : 0);
                        map.setTileTypeAndLevel(group.get(i)[0], group.get(i)[1],
                                a <= 0 ? Chunk.AIR : type, levelOf(a));
                    }
                }
            }
        }
    }

    /**
     * 单格流体计算（Terraria 式）：向下坠落/下渗优先（上方尽量全部下沉填满下方）；
     * 落地后向两侧铺开找平。每次转移都保持总量守恒（转出量 = 转入量），
     * 本格转入目标被标记后不再参与本步移动。
     */
    private void stepCell(int type, int lv, int tx, int ty) {
        int amount = FULL_AMOUNT - lv;
        if (amount <= 0) return;

        // —— 向下：下方是空气时整格坠落；下方有同型水且未满时，上方水量尽量全部下沉合并 ——
        int below = tileAt(tx, ty + 1);
        if (below == Chunk.AIR) {
            if (canReceive(tx, ty + 1, type)) {
                int t = Math.min(amount, FALL_CAP);
                write(tx, ty + 1, type, t);
                write(tx, ty, type, amount - t);
                return;
            }
        } else if (below == type) {
            if (canReceive(tx, ty + 1, type)) {
                int bAmount = FULL_AMOUNT - levelAt(tx, ty + 1);
                int cap = FULL_AMOUNT - bAmount;          // 下方剩余容量
                if (cap > 0) {
                    int t = Math.min(Math.min(amount, cap), FALL_CAP);
                    write(tx, ty + 1, type, bAmount + t);
                    write(tx, ty, type, amount - t);
                    return;
                }
            }
        }

        // —— 侧向：落地/下方受阻时向两侧铺开找平（每步交替优先方向，避免固定偏置） ——
        int spreadCap = type == Chunk.WATER ? WATER_SPREAD_CAP : LAVA_SPREAD_CAP;
        int remaining = amount;
        boolean leftFirst = (stepCounter & 1) == 0;
        for (int pass = 0; pass < 2; pass++) {
            int dir = (pass == 0) == leftFirst ? -1 : 1;
            int nx = tx + dir;
            if (!canReceive(nx, ty, type)) continue;
            int nType = map.getTileType(nx, ty);
            int nAmount = nType == type ? FULL_AMOUNT - levelAt(nx, ty) : 0;
            if (nAmount >= remaining) continue;
            int diff = remaining - nAmount;
            if (diff <= 1) continue;          // 差 ≤ 1 单位不再转移，16 级下差 1 = 2px，视觉不可见
            int t = Math.min(diff / 2, spreadCap);
            if (t <= 0) continue;
            write(nx, ty, type, nAmount + t);
            remaining -= t;
        }
        if (remaining != amount) {
            write(tx, ty, type, remaining);
        }
    }

    /** 水×岩浆反应（Terraria：相邻即生成黑曜石，两侧液体同时消耗）。基于当前网格。 */
    private void applyReactions() {
        List<int[]> lavas = new ArrayList<>();
        for (ChunkPos pos : map.loadedChunkKeys()) {
            Chunk chunk = map.getChunk(pos);
            int bx = pos.cx * Chunk.SIZE, by = pos.cy * Chunk.SIZE;
            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                for (int ly = 0; ly < Chunk.SIZE; ly++) {
                    if (chunk.getTile(lx, ly) == Chunk.LAVA) {
                        lavas.add(new int[]{bx + lx, by + ly});
                    }
                }
            }
        }
        for (int[] l : lavas) {
            int tx = l[0], ty = l[1];
            if (map.getTileType(tx, ty) != Chunk.LAVA) continue;
            for (int d = 0; d < 4; d++) {
                int nx = tx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int ny = ty + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                if (tileAt(nx, ny) == Chunk.WATER) {
                    map.setTileTypeAndLevel(tx, ty, OBSIDIAN, 0);
                    map.setTileTypeAndLevel(nx, ny, Chunk.AIR, 0);
                    break;
                }
            }
        }
    }

    /**
     * 检测 (tx,ty) 所属的连通水域（4 邻域、同型流体）。
     * 边界：世界外 / 未加载区块视为实心墙（不连通），与流动规则一致。
     * 返回水域全部格子坐标；该格不是流体或不在已加载区块时返回空列表。
     */
    public List<int[]> findBody(int tx, int ty, int type) {
        if (ty < 0 || ty >= Chunk.WORLD_HEIGHT) return List.of();
        int cx = Math.floorDiv(tx, Chunk.SIZE), cy = Math.floorDiv(ty, Chunk.SIZE);
        if (!map.isChunkLoaded(cx, cy)) return List.of();
        if (map.getTileType(tx, ty) != type) return List.of();

        List<int[]> cells = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        visited.add(key(tx, ty));
        queue.add(new int[]{tx, ty});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            cells.add(c);
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + (d == 0 ? 1 : (d == 1 ? -1 : 0));
                int ny = c[1] + (d == 2 ? 1 : (d == 3 ? -1 : 0));
                long k = key(nx, ny);
                if (visited.contains(k)) continue;
                if (ny < 0 || ny >= Chunk.WORLD_HEIGHT) continue;
                int ncx = Math.floorDiv(nx, Chunk.SIZE), ncy = Math.floorDiv(ny, Chunk.SIZE);
                if (!map.isChunkLoaded(ncx, ncy)) continue;
                if (map.getTileType(nx, ny) != type) continue;
                visited.add(k);
                queue.add(new int[]{nx, ny});
            }
        }
        return cells;
    }

    /**
     * Terraria 式舀液体：从 (tx,ty) 所属连通水域的总水量中扣除一桶（BUCKET_AMOUNT 单位）。
     * 各格按"现水量 × 剩余/总量"比例缩水（浅格先干涸），整数除法余数精确补足，
     * 总量精确守恒（扣减量 = 舀走量）；缩水后 amount<=0 的格子变空气。
     * 水域总量不足一桶时全部舀走（区域清空）。模拟后续 tick 会自然找平水面。
     *
     * @return 实际舀走单位数（0 表示未舀到液体）
     */
    public int scoop(int tx, int ty, int type) {
        List<int[]> cells = findBody(tx, ty, type);
        if (cells.isEmpty()) return 0;
        long total = 0;
        for (int[] c : cells) total += FULL_AMOUNT - map.getFluidLevel(c[0], c[1]);
        if (total <= 0) return 0;
        int removed = (int) Math.min(BUCKET_AMOUNT, total);
        long remain = total - removed;

        int n = cells.size();
        long[] news = new long[n];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            long a = FULL_AMOUNT - map.getFluidLevel(c[0], c[1]);
            news[i] = a * remain / total;
            sum += news[i];
        }
        long diff = remain - sum;                 // 整数除法损失，逐格 +1 精确补回
        for (int i = 0; i < diff; i++) news[i % n]++;
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            map.setTileTypeAndLevel(c[0], c[1], news[i] <= 0 ? Chunk.AIR : type, levelOf((int) news[i]));
        }
        return removed;
    }

    /**
     * Terraria 式倒液体：把一桶（BUCKET_AMOUNT 单位）融入 (tx,ty) 所属连通水域。
     * 目标格为空气且不与任何水域相邻时，直接放置一桶（满格）。
     *
     * @return 实际倒入单位数
     */
    public int pour(int tx, int ty, int type) {
        List<int[]> cells = findBodyForPour(tx, ty, type);
        if (cells.isEmpty()) {
            map.setTileTypeAndLevel(tx, ty, type, levelOf(BUCKET_AMOUNT));
            return BUCKET_AMOUNT;
        }
        addToBody(cells, type, BUCKET_AMOUNT);
        return BUCKET_AMOUNT;
    }

    /**
     * 固体方块替换流体时的位移（Terraria）：被排挤的液体优先被顶到目标格上方
     * （上方为空气时水位上升）；否则融入相邻同型水域（水面微升）。保证水不凭空消失。
     * 调用方应先完成固体放置（目标格已不是流体）。
     */
    public void displace(int tx, int ty, int type, int amount) {
        if (amount <= 0) return;
        // 1) 上方为空气 → 液体被向上顶（固体入水，水位上升一格）
        int above = ty - 1;
        if (above >= 0 && map.getTileType(tx, above) == Chunk.AIR) {
            map.setTileTypeAndLevel(tx, above, type, levelOf(amount));
            return;
        }
        // 2) 否则融入相邻同型水域（含上方流体格）
        for (int d = 0; d < 4; d++) {
            int nx = tx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
            int ny = ty + (d == 2 ? 1 : (d == 3 ? -1 : 0));
            if (ny < 0 || ny >= Chunk.WORLD_HEIGHT) continue;
            if (map.getTileType(nx, ny) != type) continue;
            List<int[]> body = findBody(nx, ny, type);
            if (!body.isEmpty()) {
                addToBody(body, type, amount);
                return;
            }
        }
        // 3) 无处可去（上方固体且四周无水域）：放弃（边缘情况）
    }

    /**
     * 把 amount 单位流体按比例融入给定水域格集：各格按"现水量 × 新总量/旧总量"增水
     * 并封顶满格；整数除法余数<b>优先补给最深的格</b>（水下沉聚合，浅格/目标格最后补，
     * 杜绝液面上方浮水）；水域全满仍溢出时，多余的水放到水域最浅格上方一格（水面上升）。
     * 总量精确守恒（增量 = 倒入量）。
     */
    private void addToBody(List<int[]> cells, int type, long amount) {
        int n = cells.size();
        long[] a = new long[n];
        long oldTotal = 0;
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            // 只计同型流体格的实际水量；空气扩展格（倒水目标）当前水量为 0，防止被当作满水格
            a[i] = map.getTileType(c[0], c[1]) == type ? FULL_AMOUNT - map.getFluidLevel(c[0], c[1]) : 0;
            oldTotal += a[i];
        }
        if (oldTotal <= 0) return;          // 防御：水域理论上有水，防除零/无意义缩放
        long newTotal = oldTotal + amount;

        long[] news = new long[n];
        long sum = 0;
        for (int i = 0; i < n; i++) {
            news[i] = Math.min(FULL_AMOUNT, a[i] * newTotal / oldTotal);
            sum += news[i];
        }
        // 余数精确补足：优先补最深的格（y 大 = 深），浅格最后补
        long diff = newTotal - sum;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort((i1, i2) -> Integer.compare(cells.get(i2)[1], cells.get(i1)[1])); // 深→浅
        for (int idx : order) {
            if (diff <= 0) break;
            if (news[idx] < FULL_AMOUNT) { news[idx]++; diff--; }
        }
        // 水域全满仍溢出（倒入导致水面上升）：多余放到水域最浅格上方一格
        if (diff > 0) {
            int topY = Integer.MAX_VALUE, topX = 0;
            for (int[] c : cells) if (c[1] < topY) { topY = c[1]; topX = c[0]; }
            int ny = topY - 1;
            if (ny >= 0 && map.getTileType(topX, ny) == Chunk.AIR) {
                map.setTileTypeAndLevel(topX, ny, type, levelOf((int) Math.min(diff, FULL_AMOUNT)));
            }
        }
        for (int i = 0; i < n; i++) {
            int[] c = cells.get(i);
            map.setTileTypeAndLevel(c[0], c[1], news[i] <= 0 ? Chunk.AIR : type, levelOf((int) news[i]));
        }
    }

    /**
     * 找倒入目标水域：目标格为液体时取其所属水域；否则找 4 邻域同型液体的水域，
     * 存在时把目标格（空气）作为扩展格一并纳入（倒入的水有明确去向）。
     * 返回空表示孤立（不与任何水域相邻）。
     */
    private List<int[]> findBodyForPour(int tx, int ty, int type) {
        if (map.getTileType(tx, ty) == type) return findBody(tx, ty, type);
        for (int d = 0; d < 4; d++) {
            int nx = tx + (d == 0 ? 1 : (d == 1 ? -1 : 0));
            int ny = ty + (d == 2 ? 1 : (d == 3 ? -1 : 0));
            if (ny < 0 || ny >= Chunk.WORLD_HEIGHT) continue;
            if (map.getTileType(nx, ny) == type) {
                List<int[]> body = findBody(nx, ny, type);
                if (!body.isEmpty()) {
                    body.add(new int[]{tx, ty});   // 目标格（空气）作为扩展格
                    return body;
                }
            }
        }
        return List.of();
    }

    /** 调试日志：每 DEBUG_EVERY 步打印水/岩浆总量与活动格数（总量守恒观察点）。 */
    private void logState() {
        long waterAmount = 0, lavaAmount = 0;
        int waterCells = 0, lavaCells = 0;
        for (ChunkPos pos : map.loadedChunkKeys()) {
            Chunk chunk = map.getChunk(pos);
            for (int lx = 0; lx < Chunk.SIZE; lx++) {
                for (int ly = 0; ly < Chunk.SIZE; ly++) {
                    int t = chunk.getTile(lx, ly);
                    if (t == Chunk.WATER) {
                        waterAmount += FULL_AMOUNT - chunk.getFluidLevel(lx, ly);
                        waterCells++;
                    } else if (t == Chunk.LAVA) {
                        lavaAmount += FULL_AMOUNT - chunk.getFluidLevel(lx, ly);
                        lavaCells++;
                    }
                }
            }
        }
        System.out.printf("[fluid] step=%d 水: %d格/%d单位  岩浆: %d格/%d单位%n",
                stepCounter, waterCells, waterAmount, lavaCells, lavaAmount);
    }
}
