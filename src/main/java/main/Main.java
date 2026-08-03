package main;

/**
 * 游戏的主入口类。
 * 负责启动游戏窗口（Gameframe），从而初始化所有游戏组件、线程和循环。
 */
public class Main {

    /**
     * 程序入口方法（标准写法）。
     *
     * 主要工作：
     * - 创建 Gameframe 实例（游戏主窗口）
     * - Gameframe 构造函数内部会进一步创建 Gamepanel、启动逻辑/渲染线程等
     * - 最终显示游戏界面并开始游戏循环
     */
    public static void main(String[] args) {
        // 实例化游戏窗口，游戏自此开始
        Gameframe gameframe = new Gameframe();
    }
}