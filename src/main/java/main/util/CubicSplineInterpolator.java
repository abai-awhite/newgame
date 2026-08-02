package main.util;

import main.Gamepanel;
import entity.Player;

/**
 * 三次样条插值器，支持线性插值和 Catmull-Rom 三次样条插值切换。
 *
 * <h3>设计说明</h3>
 * <p>用于游戏渲染中的平滑位置插值，解决低逻辑帧率下的卡顿感。</p>
 *
 * <h3>插值模式</h3>
 * <ul>
 *   <li><b>LINEAR（线性插值）</b>：快速但有速度突变，适合快速响应的场景</li>
 *   <li><b>CATMULL_ROM（三次样条）</b>：通过4个控制点生成光滑曲线，保留连续性和平滑性</li>
 * </ul>
 *
 * <h3>Catmull-Rom 算法</h3>
 * <p>Catmull-Rom  spline 是一种局部三次插值方法，仅使用4个相邻控制点即可计算任意位置的插值值。
 * 它保证曲线通过所有控制点，且在控制点处具有连续的切向量（除非控制点共线）。</p>
 *
 * <h3>性能特性</h3>
 * <ul>
 *   <li>每次插值仅需 16 次浮点乘加运算</li>
 *   <li>无矩阵求解，适合实时系统</li>
 *   <li>内存占用极低（仅保存历史位置）</li>
 * </ul>
 *
 * @see Player
 */
public class CubicSplineInterpolator {

    /**
     * 插值模式枚举
     */
    public enum InterpolationMode {
        /** 线性插值，快速但有速度突变 */
        LINEAR,
        /** Catmull-Rom 三次样条，光滑连续 */
        CATMULL_ROM
    }

    /**
     * 插值模式选择开关。
     * 可通过 setInterpolationMode() 动态切换。
     */
    public static InterpolationMode mode = InterpolationMode.LINEAR;

    /**
     * Catmull-Rom 样条的张力参数。
     * 控制曲线在控制点之间的紧绷程度。
     * - 0.0: 标准 Catmull-Rom（完全通过控制点）
     * - 0.5: Centripetal Catmull-Rom（避免自交和速度异常）
     * - 1.0: 接近线性
     */
    private static final double TENSION = 0.5;

    /**
     * Catmull-Rom 样条基函数。
     * 使用矩阵形式计算：
     * P(t) = [t^3, t^2, t, 1] * M * [P0, P1, P2, P3]^T
     *
     * 其中 M 为 Catmull-Rom 矩阵（Centripetal 变体）：
     * [ -T   2-T  T-2   T  ]
     * [ 2T   T-3  3-2T -T  ]
     * [ -T   0    T     0  ]
     * [ 0    1    0     0  ]
     *
     * @param t      参数 t，范围 [0, 1]，0 为 P1 位置，1 为 P2 位置
     * @param p0     起始外推控制点
     * @param p1     插值段起始点（结果在 t=0 时精确等于 p1）
     * @param p2     插值段结束点（结果在 t=1 时精确等于 p2）
     * @param p3     结束外推控制点
     * @return 插值结果
     */
    public static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        double t2 = t * t;
        double t3 = t2 * t;

        double a = -TENSION;
        double b = 2.0 - TENSION;
        double c = TENSION - 2.0;
        double d = TENSION;
        double e = 2.0 * TENSION;
        double f = TENSION - 3.0;
        double g = 3.0 - 2.0 * TENSION;
        double h = -TENSION;

        return 0.5 * (
            (a * t3 + b * t2 + c * t + d) * p0 +
            (e * t3 + f * t2 + g * t + 1.0) * p1 +
            ((-e) * t3 + (-f + 1) * t2 + (-g + 1) * t) * p2 +
            (h * t3 + 0 * t2 + TENSION * t + 0) * p3
        );
    }

    /**
     * 使用 Catmull-Rom 样条进行二维位置插值。
     *
     * @param t   参数 t，范围 [0, 1]
     * @param x0  起始外推点 X
     * @param x1  控制点1 X
     * @param x2  控制点2 X
     * @param x3  结束外推点 X
     * @param y0  起始外推点 Y
     * @param y1  控制点1 Y
     * @param y2  控制点2 Y
     * @param y3  结束外推点 Y
     * @return double[2] = {interpolatedX, interpolatedY}
     */
    public static double[] catmullRom2D(double t, double x0, double x1, double x2, double x3,
                                        double y0, double y1, double y2, double y3) {
        return new double[] {
            catmullRom(t, x0, x1, x2, x3),
            catmullRom(t, y0, y1, y2, y3)
        };
    }

    /**
     * 线性插值（Linear Interpolation）。
     *
     * @param a  起始值
     * @param b  结束值
     * @param t  参数 t，范围 [0, 1]
     * @return 插值结果 a + (b - a) * t
     */
    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * 根据当前插值模式执行插值。
     *
     * @param t        参数 t，范围 [0, 1]
     * @param prevX    上一个逻辑位置 X
     * @param currX    当前逻辑位置 X
     * @param nextX    下一个（预测）逻辑位置 X
     * @param nextNextX 下下个逻辑位置 X（用于 Catmull-Rom）
     * @param prevY    上一个逻辑位置 Y
     * @param currY    当前逻辑位置 Y
     * @param nextY    下一个逻辑位置 Y
     * @param nextNextY 下下个逻辑位置 Y
     * @return double[2] = {interpolatedX, interpolatedY}
     */
    public static double[] interpolate(double t,
                                       double prevX, double currX, double nextX, double nextNextX,
                                       double prevY, double currY, double nextY, double nextNextY) {
        if (mode == InterpolationMode.CATMULL_ROM) {
            return catmullRom2D(t, prevX, currX, nextX, nextNextX,
                                prevY, currY, nextY, nextNextY);
        } else {
            return new double[] { lerp(currX, nextX, t), lerp(currY, nextY, t) };
        }
    }

    /**
     * 设置插值模式。
     *
     * @param newMode 新的插值模式
     */
    public static void setMode(InterpolationMode newMode) {
        mode = newMode;
    }

    /**
     * 获取当前插值模式。
     *
     * @return 当前 InterpolationMode
     */
    public static InterpolationMode getMode() {
        return mode;
    }
}