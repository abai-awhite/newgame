package main.gui;

import entity.Player;
import main.Gamepanel;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * 调试信息覆盖层。
 *
 * <h3>职责</h3>
 * <p>在游戏画面上方绘制调试信息面板，包括玩家坐标等实时数据。
 * 通过 F3 键控制显示/隐藏。</p>
 *
 * <h3>渲染方式</h3>
 * <p>作为 Gamepanel.paintComponent() 中的最后一个绘制步骤，
 * 使用世界坐标变换后的 Graphics2D 进行绘制，确保面板始终固定在屏幕左上角。</p>
 *
 * <h3>线程安全</h3>
 * <p>visible 字段使用 volatile 修饰，保证 AWT 事件线程和逻辑线程间的可见性。
 * 坐标数据从 Player 对象读取（渲染线程只读字段）。</p>
 */
public class DebugOverlay {

    /** 面板背景颜色（半透明黑色） */
    private static final Color BACKGROUND_COLOR = new Color(0, 0, 0, 180);

    /** 文字颜色（白色） */
    private static final Color TEXT_COLOR = Color.WHITE;

    /** 边框颜色（半透明白色） */
    private static final Color BORDER_COLOR = new Color(255, 255, 255, 100);

    /** 面板内边距（像素） */
    private static final int PADDING = 8;

    /** 行间距（像素） */
    private static final int LINE_GAP = 4;

    /** 字体大小 */
    private static final int FONT_SIZE = 14;

    /** 面板左上角 X 偏移（相对于屏幕左上角，像素） */
    private static final int OFFSET_X = 10;

    /** 面板左上角 Y 偏移（相对于屏幕左上角，像素） */
    private static final int OFFSET_Y = 10;

    /** 显示/隐藏过渡动画时长（毫秒） */
    private static final int TRANSITION_DURATION = 200;

    /** 字体 */
    private static final Font DEBUG_FONT = new Font("微软雅黑", Font.PLAIN, FONT_SIZE);

    /** 是否可见（由 F3 键控制） */
    private volatile boolean visible = false;

    /** 当前透明度（0.0 = 完全隐藏, 1.0 = 完全显示） */
    private float currentAlpha = 0.0f;

    /** 上一次状态切换时间戳（毫秒） */
    private long lastToggleTime = 0;

    /** 目标透明度 */
    private float targetAlpha = 0.0f;

    /** 玩家引用 */
    private final Player player;

    /** 游戏主面板引用 */
    private final Gamepanel panel;

    /**
     * 构造调试覆盖层。
     *
     * @param panel  游戏主面板
     * @param player 玩家对象
     */
    public DebugOverlay(Gamepanel panel, Player player) {
        this.panel = panel;
        this.player = player;
    }

    /**
     * 切换调试界面的显示/隐藏状态。
     *
     * <p>在键盘事件处理中调用，由 F3 键触发。</p>
     */
    public void toggle() {
        visible = !visible;
        targetAlpha = visible ? 1.0f : 0.0f;
        lastToggleTime = System.currentTimeMillis();
    }

    /**
     * 更新过渡动画状态。
     *
     * <p>每帧调用，根据当前时间和目标透明度计算实际透明度。</p>
     */
    public void update() {
        if (currentAlpha == targetAlpha) {
            return;
        }

        long elapsed = System.currentTimeMillis() - lastToggleTime;
        float progress = Math.min((float) elapsed / TRANSITION_DURATION, 1.0f);

        if (targetAlpha > currentAlpha) {
            currentAlpha = progress;
        } else {
            currentAlpha = 1.0f - progress;
        }

        if (progress >= 1.0f) {
            currentAlpha = targetAlpha;
        }
    }

    /**
     * 绘制调试信息面板。
     *
     * <p>在 Gamepanel.paintComponent() 的最后调用，
     * 此时 Graphics2D 已经恢复到屏幕坐标系（通过 g2.setTransform(old) 还原）。</p>
     *
     * @param g2 图形上下文（屏幕坐标系）
     */
    public void render(Graphics2D g2) {
        if (currentAlpha <= 0.0f) {
            return;
        }

        // 保存当前变换
        AffineTransform oldTransform = g2.getTransform();
        Composite oldComposite = g2.getComposite();

        // 构建面板内容
        String[] lines = buildDebugLines();

        // 计算面板尺寸
        FontMetrics fm = g2.getFontMetrics(DEBUG_FONT);
        int maxWidth = 0;
        for (String line : lines) {
            int width = fm.stringWidth(line);
            if (width > maxWidth) {
                maxWidth = width;
            }
        }
        int panelHeight = lines.length * fm.getHeight() - LINE_GAP + PADDING * 2;
        int panelWidth = maxWidth + PADDING * 2;

        // 设置透明度
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, currentAlpha));

        // 绘制背景
        g2.setColor(BACKGROUND_COLOR);
        g2.fillRoundRect(OFFSET_X, OFFSET_Y, panelWidth, panelHeight, 8, 8);

        // 绘制边框
        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(OFFSET_X, OFFSET_Y, panelWidth - 1, panelHeight - 1, 8, 8);

        // 绘制文字
        g2.setColor(TEXT_COLOR);
        g2.setFont(DEBUG_FONT);
        int y = OFFSET_Y + PADDING + fm.getAscent();
        for (String line : lines) {
            g2.drawString(line, OFFSET_X + PADDING, y);
            y += fm.getHeight();
        }

        // 恢复状态
        g2.setComposite(oldComposite);
        g2.setTransform(oldTransform);
    }

    /**
     * 构建调试信息行。
     *
     * @return 调试信息字符串数组
     */
    private String[] buildDebugLines() {
        double worldX = player.currentX / Gamepanel.titlesize;
        double worldY = player.currentY / Gamepanel.titlesize;
        double pixelX = player.currentX;
        double pixelY = player.currentY;

        return new String[]{
            String.format("坐标: (%.2f, %.2f)", worldX, 1024-worldY),
            String.format("像素: (%.1f, %.1f)", pixelX, pixelY)
        };
    }

    /**
     * 获取当前可见状态。
     *
     * @return 是否可见
     */
    public boolean isVisible() {
        return visible;
    }
}
