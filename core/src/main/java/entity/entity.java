package entity;

import main.world.Chunk;
import main.world.InfiniteMap;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 实体基类：所有实体（掉落物、玩家等）继承本类。
 *
 * <p>统一提供：中心坐标位置、速度、可配置/可动态调整的碰撞箱（AABB）、
 * 标签集合（增删查 API）、存活状态。物理由 {@link EntityPhysics} 驱动。</p>
 *
 * <p>坐标语义：位置为实体视觉中心点；碰撞箱由 getAABB() 从中心按尺寸派生。</p>
 */
public abstract class Entity {

    /** 方块固体判定接口（实体系统独立判定，不依赖 Block 注册表） */
    public interface Solidity {
        boolean isSolid(int tileType);
    }

    /** 默认固体判定：非空气且非液体即实心（掉落物等环境实体使用，可与方块正确碰撞） */
    public static final Solidity DEFAULT_SOLIDITY = type ->
            type != Chunk.AIR && type != Chunk.WATER && type != Chunk.LAVA;

    /** 地图引用（碰撞检测） */
    protected final InfiniteMap map;
    /** 方块大小（像素） */
    protected final int tileSize;

    /** 位置（视觉中心，y 向下为正） */
    protected double x, y;
    /** 速度（像素/tick） */
    protected double vx, vy;

    private boolean alive = true;

    /** 碰撞箱尺寸（像素，默认满格；可 setCollisionSize 动态调整） */
    private double collW, collH;

    /** 标签集合（字符串格式，多标签共存） */
    private final Set<String> tags = new HashSet<>();

    protected Entity(InfiniteMap map, int tileSize, double x, double y) {
        this.map = map;
        this.tileSize = tileSize;
        this.x = x;
        this.y = y;
        this.collW = tileSize;
        this.collH = tileSize;
    }

    // ==================== 位置 ====================

    public double getX() { return x; }
    public double getY() { return y; }
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    // ==================== 速度 ====================

    public double getVX() { return vx; }
    public double getVY() { return vy; }
    public void setVX(double vx) { this.vx = vx; }
    public void setVY(double vy) { this.vy = vy; }

    // ==================== 碰撞箱（可配置 / 动态调整） ====================

    public void setCollisionSize(double width, double height) {
        this.collW = width;
        this.collH = height;
    }

    public double getCollisionWidth() { return collW; }
    public double getCollisionHeight() { return collH; }

    /** 当前碰撞箱（AABB，中心派生） */
    public AABB getAABB() {
        return new AABB(x - collW / 2, y - collH / 2, collW, collH);
    }

    // ==================== 标签系统 ====================

    /** 添加标签（重复添加无副作用） */
    public void addTag(String tag) {
        if (tag != null) tags.add(tag);
    }

    /** 移除标签，返回是否真的存在 */
    public boolean removeTag(String tag) {
        return tags.remove(tag);
    }

    /** 查询是否持有某标签 */
    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    /** 返回全部标签（只读视图） */
    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    // ==================== 存活 ====================

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }

    // ==================== 其它 ====================

    public InfiniteMap getMap() { return map; }
    public int getTileSize() { return tileSize; }

    /** 每逻辑帧更新（由实体系统驱动） */
    public abstract void update();
}
