package entity;

/**
 * 掉落物实体（纯逻辑类）。
 */
public class DropItem {

    private double worldX;
    private double worldY;
    private final String itemName;
    private final int count;
    private boolean alive = true;
    private int lifeTicks = 0;

    private static final int MAX_LIFE_TICKS = 320;
    private static final double SUCK_RANGE = 150.0;
    private static final double SUCK_SPEED = 4.0;

    public DropItem(double worldX, double worldY, String itemName, int count) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.itemName = itemName;
        this.count = count;
    }

    /**
     * 每逻辑帧更新掉落物状态。
     * @return true 表示已被吸入背包
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

    public double getWorldX() { return worldX; }
    public double getWorldY() { return worldY; }
    public boolean isAlive() { return alive; }
    public String getItemName() { return itemName; }
    public int getCount() { return count; }
}
