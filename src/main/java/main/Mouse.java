package main;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * 鼠标输入状态管理器。
 *
 * <h3>职责</h3>
 * <p>实现 MouseListener 和 MouseMotionListener 接口，监听鼠标按下、释放、点击、
 * 移动、拖拽、进入和退出事件，并将鼠标状态存储为 volatile 字段。
 * 这些字段可被其他线程（尤其是逻辑线程 Tick）安全读取。</p>
 *
 * <h3>支持的鼠标事件</h3>
 * <ul>
 *   <li>鼠标按下：记录按下的按键（左键/中键/右键）</li>
 *   <li>鼠标释放：记录释放的按键</li>
 *   <li>鼠标点击：记录点击事件</li>
 *   <li>鼠标移动：实时更新鼠标坐标</li>
 *   <li>鼠标拖拽：记录拖拽状态和坐标</li>
 *   <li>鼠标进入/退出：记录鼠标是否在面板内</li>
 * </ul>
 *
 * <h3>线程安全说明</h3>
 * <p>字段使用 volatile 修饰，保证多线程间的可见性。
 * 鼠标事件在 AWT 事件分发线程中触发，逻辑线程读取状态。
 * volatile 能确保逻辑线程看到最新的鼠标状态。</p>
 *
 * <h3>坐标系统</h3>
 * <p>提供两种坐标：</p>
 * <ul>
 *   <li>屏幕坐标：相对于 Gamepanel 的像素坐标</li>
 *   <li>世界坐标：相对于游戏世界的格子坐标（需结合摄像机位置转换）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * if (mouse.leftPressed) {
 *     // 处理左键按下逻辑
 *     int worldX = mouse.getWorldX(cameraX);
 *     int worldY = mouse.getWorldY(cameraY);
 * }
 * }</pre>
 *
 * @see MouseListener
 * @see MouseMotionListener
 * @see Tick
 * @see Gamepanel
 */
public class Mouse implements MouseListener, MouseMotionListener {

    /**
     * 日志标签，用于日志输出。
     */
    private static final String TAG = "Mouse";

    // ==================== 鼠标按键状态 ====================

    /** 鼠标左键是否被按下 */
    public volatile boolean leftPressed;

    /** 鼠标中键是否被按下 */
    public volatile boolean middlePressed;

    /** 鼠标右键是否被按下 */
    public volatile boolean rightPressed;

    /** 鼠标是否正在拖拽（按下后移动） */
    public volatile boolean dragging;

    // ==================== 鼠标坐标 ====================

    /** 鼠标当前 X 坐标（相对于面板的像素坐标） */
    public volatile int mouseX;

    /** 鼠标当前 Y 坐标（相对于面板的像素坐标） */
    public volatile int mouseY;

    /** 鼠标按下时的 X 坐标（像素坐标） */
    public volatile int pressX;

    /** 鼠标按下时的 Y 坐标（像素坐标） */
    public volatile int pressY;

    /** 鼠标上次点击的 X 坐标（像素坐标） */
    public volatile int clickX;

    /** 鼠标上次点击的 Y 坐标（像素坐标） */
    public volatile int clickY;

    // ==================== 鼠标状态 ====================

    /** 鼠标是否在面板内 */
    public volatile boolean isInPanel;

    /** 鼠标点击次数（用于双击检测） */
    public volatile int clickCount;

    /** 上次点击的时间戳（毫秒） */
    private long lastClickTime;

    /** 双击时间间隔阈值（毫秒） */
    private static final long DOUBLE_CLICK_INTERVAL = 300;

    /** 是否检测到双击 */
    public volatile boolean doubleClicked;

    // ==================== 事件日志 ====================

    /** 是否启用详细日志 */
    private static final boolean ENABLE_DEBUG_LOG = false;

    // ==================== MouseListener 实现 ====================

    /**
     * 鼠标按键按下事件。
     *
     * <p>在 AWT 事件分发线程中被调用。
     * 记录按下的按键类型和按下位置。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mousePressed(MouseEvent e) {
        try {
            int button = e.getButton();
            pressX = e.getX();
            pressY = e.getY();
            mouseX = pressX;
            mouseY = pressY;

            switch (button) {
                case MouseEvent.BUTTON1:
                    leftPressed = true;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 左键按下: (" + pressX + ", " + pressY + ")");
                    }
                    break;
                case MouseEvent.BUTTON2:
                    middlePressed = true;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 中键按下: (" + pressX + ", " + pressY + ")");
                    }
                    break;
                case MouseEvent.BUTTON3:
                    rightPressed = true;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 右键按下: (" + pressX + ", " + pressY + ")");
                    }
                    break;
                default:
                    System.err.println("[" + TAG + "] 未知按键: " + button);
                    break;
            }

            dragging = true;
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标按下事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 鼠标按键释放事件。
     *
     * <p>在 AWT 事件分发线程中被调用。
     * 记录释放的按键类型和释放位置。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        try {
            int button = e.getButton();

            switch (button) {
                case MouseEvent.BUTTON1:
                    leftPressed = false;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 左键释放: (" + e.getX() + ", " + e.getY() + ")");
                    }
                    break;
                case MouseEvent.BUTTON2:
                    middlePressed = false;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 中键释放: (" + e.getX() + ", " + e.getY() + ")");
                    }
                    break;
                case MouseEvent.BUTTON3:
                    rightPressed = false;
                    if (ENABLE_DEBUG_LOG) {
                        System.out.println("[" + TAG + "] 右键释放: (" + e.getX() + ", " + e.getY() + ")");
                    }
                    break;
                default:
                    System.err.println("[" + TAG + "] 未知按键释放: " + button);
                    break;
            }

            dragging = false;
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标释放事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 鼠标点击事件（按下并释放）。
     *
     * <p>在 AWT 事件分发线程中被调用。
     * 记录点击位置和点击次数，支持双击检测。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseClicked(MouseEvent e) {
        try {
            clickX = e.getX();
            clickY = e.getY();
            clickCount = e.getClickCount();

            long currentTime = System.currentTimeMillis();
            doubleClicked = (currentTime - lastClickTime) < DOUBLE_CLICK_INTERVAL && clickCount >= 2;
            lastClickTime = currentTime;

            if (ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 鼠标点击: (" + clickX + ", " + clickY + "), 次数: " + clickCount +
                    (doubleClicked ? " (双击)" : ""));
            }
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标点击事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 鼠标进入面板事件。
     *
     * <p>在 AWT 事件分发线程中被调用。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseEntered(MouseEvent e) {
        try {
            isInPanel = true;
            mouseX = e.getX();
            mouseY = e.getY();
            if (ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 鼠标进入面板: (" + mouseX + ", " + mouseY + ")");
            }
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标进入事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 鼠标离开面板事件。
     *
     * <p>在 AWT 事件分发线程中被调用。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseExited(MouseEvent e) {
        try {
            isInPanel = false;
            if (ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 鼠标离开面板");
            }
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标离开事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ==================== MouseMotionListener 实现 ====================

    /**
     * 鼠标移动事件（未按下按键时）。
     *
     * <p>在 AWT 事件分发线程中被调用。
     * 实时更新鼠标坐标。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        try {
            mouseX = e.getX();
            mouseY = e.getY();
            if (ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 鼠标移动: (" + mouseX + ", " + mouseY + ")");
            }
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标移动事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 鼠标拖拽事件（按下按键后移动）。
     *
     * <p>在 AWT 事件分发线程中被调用。
     * 实时更新鼠标坐标和拖拽状态。</p>
     *
     * @param e 鼠标事件
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        try {
            mouseX = e.getX();
            mouseY = e.getY();
            dragging = true;
            if (ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 鼠标拖拽: (" + mouseX + ", " + mouseY + ")");
            }
        } catch (Exception ex) {
            System.err.println("[" + TAG + "] 处理鼠标拖拽事件时发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    // ==================== 坐标转换工具方法 ====================

    /**
     * 将屏幕 X 坐标转换为世界 X 坐标（像素）。
     *
     * @param cameraX 摄像机 X 坐标（像素）
     * @return 世界 X 坐标（像素）
     */
    public int getWorldX(double cameraX) {
        return mouseX + (int) Math.floor(cameraX);
    }

    /**
     * 将屏幕 Y 坐标转换为世界 Y 坐标（像素）。
     *
     * @param cameraY 摄像机 Y 坐标（像素）
     * @return 世界 Y 坐标（像素）
     */
    public int getWorldY(double cameraY) {
        return mouseY + (int) Math.floor(cameraY);
    }

    /**
     * 将鼠标按下位置的屏幕 X 坐标转换为世界 X 坐标（像素）。
     *
     * @param cameraX 摄像机 X 坐标（像素）
     * @return 世界 X 坐标（像素）
     */
    public int getPressWorldX(double cameraX) {
        return pressX + (int) Math.floor(cameraX);
    }

    /**
     * 将鼠标按下位置的屏幕 Y 坐标转换为世界 Y 坐标（像素）。
     *
     * @param cameraY 摄像机 Y 坐标（像素）
     * @return 世界 Y 坐标（像素）
     */
    public int getPressWorldY(double cameraY) {
        return pressY + (int) Math.floor(cameraY);
    }

    /**
     * 将屏幕 X 坐标转换为世界格子列索引。
     *
     * @param cameraX 摄像机 X 坐标（像素）
     * @return 世界格子列索引
     */
    public int getTileColumn(double cameraX) {
        return Math.floorDiv(getWorldX(cameraX), Gamepanel.titlesize);
    }

    /**
     * 将屏幕 Y 坐标转换为世界格子行索引。
     *
     * @param cameraY 摄像机 Y 坐标（像素）
     * @return 世界格子行索引
     */
    public int getTileRow(double cameraY) {
        return Math.floorDiv(getWorldY(cameraY), Gamepanel.titlesize);
    }

    /**
     * 将鼠标按下位置的屏幕 X 坐标转换为世界格子列索引。
     *
     * @param cameraX 摄像机 X 坐标（像素）
     * @return 世界格子列索引
     */
    public int getPressTileColumn(double cameraX) {
        return Math.floorDiv(getPressWorldX(cameraX), Gamepanel.titlesize);
    }

    /**
     * 将鼠标按下位置的屏幕 Y 坐标转换为世界格子行索引。
     *
     * @param cameraY 摄像机 Y 坐标（像素）
     * @return 世界格子行索引
     */
    public int getPressTileRow(double cameraY) {
        return Math.floorDiv(getPressWorldY(cameraY), Gamepanel.titlesize);
    }

    /**
     * 计算鼠标从按下到当前位置的拖拽距离（X 轴）。
     *
     * @return 拖拽距离（像素），正值表示向右拖拽，负值表示向左拖拽
     */
    public int getDragDistanceX() {
        return mouseX - pressX;
    }

    /**
     * 计算鼠标从按下到当前位置的拖拽距离（Y 轴）。
     *
     * @return 拖拽距离（像素），正值表示向下拖拽，负值表示向上拖拽
     */
    public int getDragDistanceY() {
        return mouseY - pressY;
    }

    /**
     * 重置所有鼠标状态。
     *
     * <p>通常在游戏暂停或切换场景时调用。</p>
     */
    public void reset() {
        leftPressed = false;
        middlePressed = false;
        rightPressed = false;
        dragging = false;
        clickCount = 0;
        doubleClicked = false;
        lastClickTime = 0;
        if (ENABLE_DEBUG_LOG) {
            System.out.println("[" + TAG + "] 鼠标状态已重置");
        }
    }
}
