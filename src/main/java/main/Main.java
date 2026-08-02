package main;

/**
 * 游戏的主入口类。
 * 负责启动游戏窗口（Gameframe），从而初始化所有游戏组件、线程和循环。
 */
public class Main {

    /**
     * 程序入口方法（非标准写法，缺少 String[] args 参数和 public 修饰符）。
     * 注意：标准的 Java 入口应为 public static void main(String[] args)，
     * 此处为简化写法，实际运行可能依赖 IDE 或特定配置。
     *
     * 主要工作：
     * - 创建 Gameframe 实例（游戏主窗口）
     * - Gameframe 构造函数内部会进一步创建 Gamepanel、启动逻辑/渲染线程等
     * - 最终显示游戏界面并开始游戏循环
     */
    static void main() {
        // 实例化游戏窗口，游戏自此开始
        Gameframe gameframe = new Gameframe();
    }
}