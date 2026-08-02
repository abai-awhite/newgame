package main;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Objects;

/**
 * 图像资源加载器。
 *
 * <h3>职责</h3>
 * <p>负责从文件系统加载所有游戏图片资源（如玩家立绘、方块贴图等），
 * 并将加载后的 BufferedImage 存储为 public 字段，供其他类在渲染时使用。</p>
 *
 * <h3>资源路径</h3>
 * <p>图片资源位于源码目录下的 {@code src/main/resources/} 目录。
 * 加载时使用 {@code getClass().getResourceAsStream()} 方法从 classpath 中读取。</p>
 *
 * <h3>错误处理</h3>
 * <p>如果资源文件不存在或读取失败，异常会被捕获并打印堆栈跟踪。
 * 运行游戏前需确保所有资源文件存在于正确路径。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * Resources res = new Resources();
 * g2.drawImage(res.player1, x, y, null);
 * }</pre>
 *
 * @see Player#getplayerimage()
 */
public class Resources {

    /**
     * 玩家站立动画帧图片（顺序播放：player1 → player2 → player3 → player4）。
     */
    public BufferedImage player1, player2, player3, player4;

    /**
     * 玩家向下看的图片（当前未使用，保留用于俯视视角功能）。
     */
    public BufferedImage playerdown;

    /**
     * 玩家向上看的图片（跳起时显示）。
     */
    public BufferedImage playerup;

    /**
     * 玩家向右看的图片。
     */
    public BufferedImage playerright;

    /**
     * 玩家向左看的图片。
     */
    public BufferedImage playerleft;

    /**
     * 构造资源加载器，立即加载所有图片。
     *
     * <p>加载顺序：</p>
     * <ol>
     *   <li>player1~4: 站立动画帧</li>
     *   <li>playerdown: 向下看</li>
     *   <li>playerup: 向上看（资源路径误写为 player-l）</li>
     *   <li>playerright: 向右看</li>
     *   <li>playerleft: 向左看（资源路径误写为 player-up）</li>
     * </ol>
     *
     * @see ImageIO#read(java.io.InputStream)
     */
    public Resources() {
        try {
            player1 = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-1.png")));
            player2 = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-2.png")));
            player3 = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-3.png")));
            player4 = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-4.png")));
            playerdown = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-down-1.png")));
            playerup = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-l-1.png")));
            playerright = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-r-1.png")));
            playerleft = ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/player/player-up-1.png")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}