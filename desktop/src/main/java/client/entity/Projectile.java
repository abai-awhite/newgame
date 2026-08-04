package client.entity;

import entity.Entity;
import main.world.Chunk;

/**
 * 弹幕（投射物）实体：继承 {@link Entity}，有物理（直线运动 + 方块碰撞）。
 *
 * <p>由枪等武器发射，客户端权威管理（创建、tick、渲染、命中检测）。
 * 碰到实心方块或超时后销毁。</p>
 *
 * <p>注意：{@code map} 传 null（不使用 {@link entity.EntityPhysics}），
 * 碰撞检测通过 {@link #update(float, client.world.ClientWorld, client.data.BlocksData)} 手动完成。</p>
 */
public class Projectile extends Entity {

    /** 寿命（秒），超时自动销毁 */
    private double life;
    /** 伤害值（命中怪物时上报服务器） */
    private final int damage;

    public Projectile(double x, double y, double vx, double vy, double life, int damage) {
        super(null, 32, x, y);
        setCollisionSize(6, 6);
        this.vx = vx;
        this.vy = vy;
        this.life = life;
        this.damage = damage;
    }

    /**
     * 客户端帧更新：直线运动 + 方块碰撞 + 超时销毁。
     *
     * @param delta  帧间隔（秒）
     * @param world  客户端世界（方块碰撞检测）
     * @param blocks 方块数据（固体判定）
     */
    public void update(float delta, client.world.ClientWorld world, client.data.BlocksData blocks) {
        x += vx * delta;
        y += vy * delta;
        life -= delta;
        if (life <= 0) {
            setAlive(false);
            return;
        }
        int tx = (int) Math.floor(x / tileSize);
        int ty = (int) Math.floor(y / tileSize);
        if (blocks.isSolid(world.getTile(tx, ty))) {
            setAlive(false);
        }
    }

    /** Entity 抽象方法（不使用 EntityPhysics，空实现） */
    @Override
    public void update() { }

    public double getLife() { return life; }
    public int getDamage() { return damage; }
}
