package entity;

/**
 * 轴对齐包围盒（Axis-Aligned Bounding Box）。
 *
 * <h3>类描述</h3>
 * <p>表示一个矩形碰撞区域，其边与坐标轴平行（不旋转）。
 * 用于 2D 碰撞检测，判断两个矩形是否发生重叠。</p>
 *
 * <h3>设计目的</h3>
 * <ul>
 *   <li>简化碰撞检测逻辑，无需处理旋转情况</li>
 *   <li>为玩家、敌人、子弹等实体提供统一的碰撞边界</li>
 *   <li>支持浮点数坐标，兼容高精度的物理计算</li>
 * </ul>
 *
 * <h3>精度处理</h3>
 * <p>intersects 方法中使用 EPSILON = 1e-9 容差，
 * 用于处理浮点数运算中因精度误差导致的本应碰撞但检测失败的情况。
 * 该容差仅在比较时向内收缩盒体，避免让本不相邻的盒子变成"碰撞"。</p>
 *
 * @see Player
 */
public class AABB {

    /**
     * 浮点数比较容差，用于碰撞检测中补偿浮点精度误差。
     * 取值 1e-9 是一个极小值，不会影响正常碰撞判定，仅在边界情况起作用。
     */
    private static final double EPSILON = 1e-9;

    /**
     * 包围盒左上角 X 坐标（世界像素坐标）。
     */
    public double x;

    /**
     * 包围盒左上角 Y 坐标（世界像素坐标）。
     */
    public double y;

    /**
     * 包围盒宽度（像素）。
     */
    public double width;

    /**
     * 包围盒高度（像素）。
     */
    public double height;

    /**
     * 构造一个 AABB 实例。
     *
     * @param x      左上角 X 坐标（世界像素坐标）
     * @param y      左上角 Y 坐标（世界像素坐标）
     * @param width  宽度（像素）
     * @param height 高度（像素）
     */
    public AABB(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * 判断两个 AABB 是否发生碰撞（重叠）。
     *
     * <p>算法：轴分离定理（SAT）的简化形式。
     * 对于轴对齐矩形，只需检查四个方向是否存在间隙即可。</p>
     *
     * <p>精度处理：</p>
     * <ul>
     *   <li>左边界判断：x < other.x + other.width + EPSILON
     *       （含容差，避免因浮点误差导致本应碰撞的盒子检测失败）</li>
     *   <li>右边界判断：x + width - EPSILON > other.x
     *       （-EPSILON 使右侧向内收缩，防止本不相邻的盒子误判为碰撞）</li>
     *   <li>上下边界同理</li>
     * </ul>
     *
     * @param other 另一个 AABB 实例
     * @return 如果两个盒子重叠则返回 true，否则返回 false
     */
    public boolean intersects(AABB other) {
        return x < other.x + other.width + EPSILON &&
               x + width - EPSILON > other.x &&
               y < other.y + other.height + EPSILON &&
               y + height - EPSILON > other.y;
    }
}