package main;

import main.gui.KeyBindingConfig;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * 键盘输入状态管理器。
 *
 * <h3>职责</h3>
 * <p>实现 KeyListener 接口，监听键盘按下和释放事件，
 * 并将按键状态（是否按下）存储为 volatile boolean 字段。
 * 按键到动作的映射通过 {@link KeyBindingConfig} 动态配置。</p>
 *
 * <h3>支持的按键</h3>
 * <ul>
 *   <li>向前移动 (默认 W)</li>
 *   <li>向左移动 (默认 A)</li>
 *   <li>向后移动 (默认 S)</li>
 *   <li>向右移动 (默认 D)</li>
 *   <li>跳跃 (默认 SPACE)</li>
 * </ul>
 *
 * <h3>线程安全说明</h3>
 * <p>字段使用 volatile 修饰，保证多线程间的可见性。
 * 键盘事件在 AWT 事件分发线程中触发，逻辑线程读取状态。
 * volatile 能确保逻辑线程看到最新的按键状态。</p>
 *
 * @see KeyListener
 * @see Tick
 * @see KeyBindingConfig
 */
public class Keyboard implements KeyListener {

    @Override
    public void keyTyped(KeyEvent e) {}

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyBindingConfig.getKeyCode("向前移动")) w = true;
        if (key == KeyBindingConfig.getKeyCode("向左移动")) a = true;
        if (key == KeyBindingConfig.getKeyCode("向后移动")) s = true;
        if (key == KeyBindingConfig.getKeyCode("向右移动")) d = true;
        if (key == KeyBindingConfig.getKeyCode("跳跃")) space = true;
        if (key == KeyBindingConfig.getKeyCode("调试界面")) f3 = true;
        if (key == KeyBindingConfig.getKeyCode("背包")) eKey = true;
        if (key == KeyBindingConfig.getKeyCode("暂停菜单")) esc = true;
        if (key == KeyBindingConfig.getKeyCode("冲刺")) {
            alt = true;
            e.consume();
        }

        // 数字键处理
        if (key == KeyEvent.VK_1) num1 = true;
        if (key == KeyEvent.VK_2) num2 = true;
        if (key == KeyEvent.VK_3) num3 = true;
        if (key == KeyEvent.VK_4) num4 = true;
        if (key == KeyEvent.VK_5) num5 = true;
        if (key == KeyEvent.VK_6) num6 = true;
        if (key == KeyEvent.VK_7) num7 = true;
        if (key == KeyEvent.VK_8) num8 = true;
        if (key == KeyEvent.VK_9) num9 = true;
        if (key == KeyEvent.VK_0) num0 = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();

        if (key == KeyBindingConfig.getKeyCode("向前移动")) w = false;
        if (key == KeyBindingConfig.getKeyCode("向左移动")) a = false;
        if (key == KeyBindingConfig.getKeyCode("向后移动")) s = false;
        if (key == KeyBindingConfig.getKeyCode("向右移动")) d = false;
        if (key == KeyBindingConfig.getKeyCode("跳跃")) space = false;
        if (key == KeyBindingConfig.getKeyCode("调试界面")) f3 = false;
        if (key == KeyBindingConfig.getKeyCode("背包")) eKey = false;
        if (key == KeyBindingConfig.getKeyCode("暂停菜单")) esc = false;
        if (key == KeyBindingConfig.getKeyCode("冲刺")) {
            alt = false;
            e.consume();
        }

        // 数字键处理
        if (key == KeyEvent.VK_1) num1 = false;
        if (key == KeyEvent.VK_2) num2 = false;
        if (key == KeyEvent.VK_3) num3 = false;
        if (key == KeyEvent.VK_4) num4 = false;
        if (key == KeyEvent.VK_5) num5 = false;
        if (key == KeyEvent.VK_6) num6 = false;
        if (key == KeyEvent.VK_7) num7 = false;
        if (key == KeyEvent.VK_8) num8 = false;
        if (key == KeyEvent.VK_9) num9 = false;
        if (key == KeyEvent.VK_0) num0 = false;
    }

    /** 向前移动 */
    public volatile boolean w;

    /** 向左移动 */
    public volatile boolean a;

    /** 向后移动 */
    public volatile boolean s;

    /** 向右移动 */
    public volatile boolean d;

    /** 跳跃 */
    public volatile boolean space;

    /** 调试界面开关 */
    public volatile boolean f3;

    /** 背包开关 */
    public volatile boolean eKey;

    /** 暂停菜单开关 */
    public volatile boolean esc;

    /** 冲刺 */
    public volatile boolean alt;

    // ==================== 数字键（快捷栏选择） ====================

    /** 数字键1-9 */
    public volatile boolean num1, num2, num3, num4, num5, num6, num7, num8, num9;
    /** 数字键0 */
    public volatile boolean num0;

}