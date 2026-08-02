package entity;

import main.Gamepanel;
import main.gui.Item;

import java.awt.*;

/**
 * 掉落物实体，方块被破坏后出现在世界中，随后被吸入玩家背包。
 */
public class DropItem {

    /** 世界坐标 X（像素） */
    private double worldX;

    /** 世界坐标 Y（像素） */
    private double worldY;

    /** 物品名称 */
    private final String itemName;

    /** 物品数量 */
    private final int count;

    /** 是否存活（被吸入背包后标记为 false） */
    private boolean alive = true;

    /** 生命周期 tick 计数器，超过一定时间后强制消失 */
    private int lifeTicks = 0;

    /** 最大存活时间（tick 数），约 10 秒 @32Hz */
    private static final int MAX_LIFE_TICKS = 320;

    /** 吸入范围（像素），进入此范围后向玩家飞行 */
    private static final double SUCK_RANGE = 150.0;

    /** 吸入速度 */
    private static final double SUCK_SPEED = 4.0;

    /** 显示大小 */
    private static final int RENDER_SIZE = 16;

    public DropItem(double worldX, double worldY, String itemName, int count) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.itemName = itemName;
        this.count = count;
    }

    /**
     * 每逻辑帧更新掉落物状态。
     *
     * @param playerX 玩家世界坐标 X
     * @param playerY 玩家世界坐标 Y
     * @return true 表示已被吸入背包，调用方应处理添加物品逻辑
     */
    public boolean update(double playerX, double playerY) {
        if (!alive) return false;

        lifeTicks++;
        if (lifeTicks > MAX_LIFE_TICKS) {
            alive = false;
            return false;
        }

        double dx = playerX - worldX;
        double dy = playerY - worldY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < SUCK_RANGE) {
            if (dist < 10.0) {
                alive = false;
                return true;
            }

            double speed = SUCK_SPEED * (1.0 + (SUCK_RANGE - dist) / SUCK_RANGE * 2.0);
            worldX += (dx / dist) * speed;
            worldY += (dy / dist) * speed;
        }

        return false;
    }

    /**
     * 渲染掉落物。
     */
    public void render(Graphics2D g2, double cameraX, double cameraY) {
        if (!alive) return;

        int screenX = (int) (worldX - cameraX);
        int screenY = (int) (worldY - cameraY - 8);

        Image blockImg = Item.getImage(itemName);
        if (blockImg != null) {
            g2.drawImage(blockImg, screenX - RENDER_SIZE / 2, screenY - RENDER_SIZE / 2,
                RENDER_SIZE, RENDER_SIZE, null);
        } else {
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillRect(screenX - RENDER_SIZE / 2, screenY - RENDER_SIZE / 2, RENDER_SIZE, RENDER_SIZE);
            g2.setColor(Color.BLACK);
            g2.drawRect(screenX - RENDER_SIZE / 2, screenY - RENDER_SIZE / 2, RENDER_SIZE, RENDER_SIZE);
        }
    }

    public boolean isAlive() {
        return alive;
    }

    public String getItemName() {
        return itemName;
    }

    public int getCount() {
        return count;
    }
}