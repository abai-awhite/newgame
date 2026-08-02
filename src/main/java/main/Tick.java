package main;

import entity.Player;
import main.gui.DebugOverlay;

/**
 * 游戏逻辑更新线程（Tick 线程）。
 * 负责以固定的频率（通常较低，如 32 次/秒）更新游戏世界状态，
 * 例如玩家的位置、动画、碰撞检测等。
 *
 * 与渲染线程（Gamepanel 的 gamedrawthread）分离，保证逻辑更新不受帧率影响。
 * 通过保存逻辑帧之间的位置差，为渲染线程提供线性插值所需的数据。
 */
public class Tick extends Thread {
    
    /** 玩家上次所在区块（用于检测区块变化以触发即时保存/卸载） */
    private int lastPlayerChunkX;
    private int lastPlayerChunkY;
     /** 逻辑更新频率（次/秒）。
     * 从 Gamepanel 中获取，保持全局一致。
     * 典型值：32，即每 31.25 毫秒更新一次逻辑。
     **/
    public static int tick = Gamepanel.tick;

    /** 玩家对象引用，用于更新其逻辑位置和动画 */
    Player player;

    /** 游戏主面板引用，用于通知逻辑更新完成（重置计时器） */
    Gamepanel gamepanel;

    /** 方块交互管理器引用 */
    BlockInteraction blockInteraction;

    /** 调试覆盖层引用 */
    DebugOverlay debugOverlay;

    /** 上一次 F3 键状态（用于检测按下瞬间） */
    private boolean lastF3State = false;

    /** 自动保存计数器（每 2400 tick = 约75秒保存一次） */
    private int saveCounter = 0;
    private static final int SAVE_INTERVAL = 2400;

    /**
     * 构造逻辑更新线程
     * @param player           玩家实体
     * @param gamepanel        游戏面板（用于回调 onTickComplete）
     * @param blockInteraction 方块交互管理器
     * @param debugOverlay     调试覆盖层
     */
    public Tick(Player player, Gamepanel gamepanel, BlockInteraction blockInteraction, DebugOverlay debugOverlay) {
        this.player = player;
        this.gamepanel = gamepanel;
        this.blockInteraction = blockInteraction;
        this.debugOverlay = debugOverlay;
        this.lastPlayerChunkX = Math.floorDiv((int) player.currentX, main.world.Chunk.SIZE);
        this.lastPlayerChunkY = Math.floorDiv((int) player.currentY, main.world.Chunk.SIZE);
    }

    /**
     * 线程主循环。
     * 以固定频率（tick Hz）调用 update() 更新游戏逻辑。
     * 使用 Thread.sleep 精确控制时间间隔，尽可能保持均匀。
     *
     * 循环条件：gamepanel.gamedrawthread != null
     * 注意：gamedrawthread 是 Gamepanel 中的渲染线程，当它被置为 null 时表示游戏结束。
     * 要求 Gamepanel 将 gamedrawthread 设为 public 或提供 getter，但通常应设计为 private 并提供 isRunning() 方法。
     */
    @Override
    public void run() {
        // 每次逻辑更新的间隔时间（毫秒）
        long re = 1000 / tick;
        // 下一次逻辑更新的目标时间点
        long retime = System.currentTimeMillis() + re;

        // 游戏运行期间持续执行
        while (gamepanel.gamedrawthread != null) {
            // 执行单次逻辑更新（位置、动画、输入响应等）
            update();

            try {
                // 计算需要睡眠的时间，以保持固定的更新频率
                long remainingTime = retime - System.currentTimeMillis();
                remainingTime = remainingTime < 0 ? 0 : remainingTime;
                Thread.sleep(remainingTime);
                // 设定下一次更新的目标时间（累加间隔，避免误差累积）
                retime += re;
            } catch (InterruptedException e) {
                System.out.println("The thread has stopped for many ms");
                System.out.println("线程已经停止很多毫秒了");
            }
        }
    }

    /**
     * 执行单次逻辑更新。
     * 调用顺序严格为：
     * 1. player.retick()  —— 保存当前逻辑位置到 previousX/previousY（为插值做准备）
     * 2. player.update()  —— 根据键盘输入等更新 currentX/currentY
     * 3. blockInteraction.update() —— 处理方块交互逻辑
     * 4. gamepanel.onTickComplete() —— 通知渲染线程"逻辑已更新"，重置时间基准
     *
     * 这种顺序保证了插值的正确性：
     * - retick 记录了旧位置（插值起点）
     * - update 产生了新位置（插值终点）
     * - onTickComplete 标记了这个瞬间，渲染线程据此计算 alpha 比例
     */
    private void update() {
        if (!gamepanel.paused) {
            player.retick();
            player.update();
            blockInteraction.update();
            player.autoJumpSystem.update();

            int px = Math.floorDiv((int) player.currentX, main.world.Chunk.SIZE);
            int py = Math.floorDiv((int) player.currentY, main.world.Chunk.SIZE);
            if (px != lastPlayerChunkX || py != lastPlayerChunkY) {
                lastPlayerChunkX = px;
                lastPlayerChunkY = py;
                // 玩家跨区块：即时保存并卸载 8 区块外的区块
                gamepanel.infiniteMap.saveAndUnloadOutsideRadius(px, py, 8);
            }
        }

        if (gamepanel.VK.f3 && !lastF3State) {
            debugOverlay.toggle();
        }
        lastF3State = gamepanel.VK.f3;

        debugOverlay.update();

        gamepanel.onTickComplete();
    }}
