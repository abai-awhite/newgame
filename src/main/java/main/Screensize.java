package main;

import java.awt.*;

/**
 * 屏幕尺寸与游戏逻辑尺寸的工具类。
 * 提供屏幕实际分辨率、以及按 tile 单位换算后的游戏区域尺寸。
 */
public class Screensize {

    /** 每个 tile（格子/图块）的像素大小，默认为 32px */
    public static int titlesize = 32;

    /** 获取当前屏幕的真实尺寸（像素单位） */
    static Dimension Screensize = Toolkit.getDefaultToolkit().getScreenSize();

    /**
     * 游戏区域高度（以 tile 为单位）。
     * 注意：Gameframe.getsizeh() 返回的是窗口内容区域的高度（像素），
     * 除以 titlesize 得到可容纳的 tile 行数。
     */
    private static final int Gameheight = Gameframe.getsizeh() / titlesize;

    /**
     * 游戏区域宽度（以 tile 为单位）。
     * Gameframe.getsizew() 返回窗口内容区域的宽度（像素）。
     */
    private static final int Gamewidth = Gameframe.getsizew() / titlesize;

    /**
     * 获取游戏区域高度（tile 数量）
     * @return 高度方向的 tile 个数
     */
    public static int getGameScreenh() {
        return Gameheight;
    }

    /**
     * 获取游戏区域宽度（tile 数量）
     * @return 宽度方向的 tile 个数
     */
    public static int getGameScreenw() {
        return Gamewidth;
    }

    /**
     * 获取屏幕实际宽度（像素）
     * @return 屏幕宽度（px）
     */
    public static int getScreenx() {
        return Screensize.width;
    }

    /**
     * 获取屏幕实际高度（像素）
     * @return 屏幕高度（px）
     */
    public static int getScreeny() {
        return Screensize.height;
    }
}