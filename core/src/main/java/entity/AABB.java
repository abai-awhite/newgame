package entity;

/**
 * 轴对齐包围盒（Axis-Aligned Bounding Box）。
 */
public class AABB {
    private static final double EPSILON = 1e-9;

    public double x;
    public double y;
    public double width;
    public double height;

    public AABB(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean intersects(AABB other) {
        return x < other.x + other.width + EPSILON &&
               x + width - EPSILON > other.x &&
               y < other.y + other.height + EPSILON &&
               y + height - EPSILON > other.y;
    }
}
