package entity;

import main.world.Chunk;

/**
 * 史莱姆怪物（纯逻辑类，服务器权威）。
 *
 * <p>继承 {@link Entity}：1 格大小（32×32 碰撞箱），分配 {@link Tags#MOB} 标签。
 * AI：定时跳跃追踪最近玩家，接触造成伤害。物理由 {@link EntityPhysics} 驱动。</p>
 */
public class Slime extends Entity {

    private static final double GRAVITY = 1;
    private static final double MAX_FALL_SPEED = 16;
    private static final double JUMP_SPEED = 9;
    private static final double MOVE_SPEED = 4;
    private static final int JUMP_INTERVAL = 40;   // 每 40 tick（~1.25秒）跳一次

    private int hp;
    private final int maxHp;
    private int jumpTimer = (int) (Math.random() * JUMP_INTERVAL);
    private boolean onGround;
    private int hurtFlash = 0;     // 受击闪烁计时（tick）
    private double targetDir = 0;  // -1 左 / 1 右 / 0 静止

    public Slime(main.world.InfiniteMap map, int tileSize, double x, double y) {
        super(map, tileSize, x, y);
        setCollisionSize(tileSize - 4, tileSize - 4);
        addTag(Tags.MOB);
        this.maxHp = 30;
        this.hp = 30;
    }

    @Override
    public void update() {
        // 重力
        vy += GRAVITY;
        if (vy > MAX_FALL_SPEED) vy = MAX_FALL_SPEED;

        // 定时跳跃追踪
        jumpTimer++;
        if (jumpTimer >= JUMP_INTERVAL && onGround) {
            jumpTimer = 0;
            if (targetDir != 0) {
                vy = -JUMP_SPEED;
                vx = targetDir * MOVE_SPEED;
                onGround = false;
            }
        }

        // 物理（轴分离方块碰撞）
        EntityPhysics.CollisionResult r =
                EntityPhysics.move(this, vx, vy, DEFAULT_SOLIDITY, Chunk.WORLD_HEIGHT);
        if (r.hitY && vy > 0) vy = 0;
        if (r.hitY && vy < 0) vy = 0;
        if (r.hitX) vx = 0;
        onGround = r.onGround;

        // 空中水平阻力
        if (!onGround) {
            vx *= 0.92;
            if (Math.abs(vx) < 0.1) vx = 0;
        }

        // 受击闪烁递减
        if (hurtFlash > 0) hurtFlash--;

        // 世界边界
        int maxY = Chunk.WORLD_HEIGHT * tileSize - tileSize;
        y = Math.min(Math.max(y, 0), maxY);
    }

    /** 设置追踪方向（由服务器根据最近玩家位置设定） */
    public void setTargetDirection(double dir) {
        this.targetDir = dir;
    }

    /** 受到伤害，返回是否死亡 */
    public boolean damage(int amount) {
        hp -= amount;
        hurtFlash = 8;
        if (hp <= 0) {
            hp = 0;
            setAlive(false);
            return true;
        }
        return false;
    }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isHurtFlashing() { return hurtFlash > 0; }
}
