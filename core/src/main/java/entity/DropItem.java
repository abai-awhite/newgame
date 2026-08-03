package entity;

import main.world.Chunk;
import main.world.InfiniteMap;

/**
 * 掉落物实体（纯逻辑类，服务器权威物理）。
 *
 * <p>继承 {@link Entity}：碰撞箱 16×16（与 16px 视觉图标一致），分配 {@link Tags#DROP_ITEM} 标签。
 * 物理（重力 + 方块碰撞）由 {@link EntityPhysics} 驱动，固体判定用
 * {@link Entity#DEFAULT_SOLIDITY}（非空气且非液体即实心），可与沙/木/树叶等所有方块正确碰撞。</p>
 *
 * <p>坐标语义：位置为视觉中心（即服务器广播/客户端渲染中心）。
 * 支撑方块被挖掉后碰撞消失 → 自然继续下落。寿命满 5 分钟自动移除。</p>
 */
public class DropItem extends Entity {

    /** 存活时间（tick）：5 分钟（服务器 32Hz tick → 5*60*32），从生成时刻起计时 */
    private static final int MAX_LIFE_TICKS = 9600;
    /** 重力加速度（像素/tick²，与玩家一致） */
    private static final double GRAVITY = 1;
    /** 最大下落速度（像素/tick，与玩家一致） */
    private static final double MAX_FALL_SPEED = 16;

    private final String itemName;
    private final int count;
    private int lifeTicks = 0;
    private boolean grounded = false;

    public DropItem(InfiniteMap map, int tileSize, double x, double y, String itemName, int count) {
        super(map, tileSize, x, y);
        // 碰撞箱与视觉表现保持一致：掉落物图标 16px → 16×16
        setCollisionSize(16, 16);
        addTag(Tags.DROP_ITEM);
        this.itemName = itemName;
        this.count = count;
    }

    @Override
    public void update() {
        lifeTicks++;
        if (lifeTicks > MAX_LIFE_TICKS) {
            setAlive(false);
            return;
        }

        // 重力
        vy += GRAVITY;
        if (vy > MAX_FALL_SPEED) vy = MAX_FALL_SPEED;

        // 轴分离方块碰撞（水平初速度支持扔出效果；支撑被挖掉后无碰撞 → 自然继续下落）
        EntityPhysics.CollisionResult r =
                EntityPhysics.move(this, vx, vy, DEFAULT_SOLIDITY, Chunk.WORLD_HEIGHT);
        if (r.hitY && vy > 0) vy = 0;
        if (r.hitX) vx = 0;
        grounded = r.onGround;
        if (grounded) {
            // 落地后水平停止（简化，不反弹不滑动）
            vx = 0;
        } else {
            // 空中水平阻力（扔出的物品自然减速）
            vx *= 0.9;
            if (Math.abs(vx) < 0.1) vx = 0;
        }

        // 世界边界
        int maxY = Chunk.WORLD_HEIGHT * tileSize - tileSize;
        y = Math.min(Math.max(y, 0), maxY);
    }

    public String getItemName() { return itemName; }
    public int getCount() { return count; }
    public boolean isGrounded() { return grounded; }
}
