package block;

import main.Gamepanel;
import main.world.Chunk;

import javax.imageio.ImageIO;
import java.awt.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 方块抽象基类，定义所有游戏内方块共有的属性和行为。
 *
 * <h3>设计模式：注册表模式</h3>
 * <p>所有具体方块类（Grass、Dirt、Stone）在类加载时向 REGISTRY 注册自己。
 * Block.fromId(id) 通过 ID 快速查找对应的方块实例，避免大量的 if-else 或 switch 判断。</p>
 *
 * <h3>方块属性</h3>
 * <ul>
 *   <li><b>id</b>：方块的唯一标识符，与 Chunk 中的 tile 类型值对应</li>
 *   <li><b>name</b>：方块的名称（调试用）</li>
 *   <li><b>image</b>：方块的贴图（从资源目录加载）</li>
 *   <li><b>solid</b>：是否为实心（影响碰撞检测）</li>
 * </ul>
 *
 * <h3>初始化流程</h3>
 * <ol>
 *   <li>Gamepanel 构造时调用 Block.init()</li>
 *   <li>init() 通过 Class.forName() 触发 Grass、Dirt、Stone 的类加载</li>
 *   <li>子类静态构造块执行，将自身注册到 REGISTRY</li>
 * </ol>
 *
 * @see Grass
 * @see Dirt
 * @see Stone
 * @see Chunk
 */
public abstract class Block {

    /**
     * 方块类型注册表，以 id 为键存储方块实例。
     * 使用 HashMap 实现 O(1) 查找。
     */
    private static final Map<Integer, Block> REGISTRY = new HashMap<>();

    /**
     * 方块唯一标识符，与 Chunk 中存储的 tile 类型值对应。
     * 例如：GRASS=1, DIRT=2, STONE=3
     */
    protected final int id;

    /**
     * 方块名称，用于调试输出或日志记录。
     */
    protected final String name;

    /**
     * 方块的贴图图片，从 /block/ 资源目录加载。
     */
    protected Image image;

    /**
     * 方块是否为实心。
     * 实心方块会阻挡玩家移动（触发碰撞检测）；
     * 非实心方块（如空气、水）则允许玩家通过。
     */
    protected boolean solid;

    /**
     * 构造一个方块实例。
     *
     * <p>注意：构造函数受 protected 限制，强制通过子类单例模式创建。
     * 子类应提供 private 构造函数，传入固定参数。</p>
     *
     * @param id        方块唯一标识符
     * @param name      方块名称
     * @param imagePath 方块贴图资源路径（如 "/block/grass.png"）
     * @param solid     是否为实心
     */
    protected Block(int id, String name, String imagePath, boolean solid) {
        this.id = id;
        this.name = name;
        this.solid = solid;
        loadImage(imagePath);
        REGISTRY.put(id, this);
    }

    /**
     * 从资源路径加载方块贴图。
     *
     * @param path 资源路径，应以 "/" 开头（如 "/block/grass.png"）
     */
    private void loadImage(String path) {
        try {
            image = ImageIO.read(getClass().getResourceAsStream(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据方块 ID 查找对应的方块实例。
     *
     * <p>这是注册表模式的核心方法，提供 O(1) 时间复杂度的查找。</p>
     *
     * @param id 方块标识符
     * @return 对应的方块实例，若未注册则返回 null
     */
    public static Block fromId(int id) {
        return REGISTRY.get(id);
    }

    /**
     * 获取方块的贴图图片。
     *
     * @return 方块的 Image 对象，用于渲染
     */
    public Image getImage() {
        return image;
    }

    /**
     * 获取方块的唯一标识符。
     *
     * @return 方块 id
     */
    public int getId() {
        return id;
    }

    /**
     * 获取方块的名称。
     *
     * @return 方块名称字符串
     */
    public String getName() {
        return name;
    }

    /**
     * 判断方块是否为实心。
     *
     * @return 实心返回 true，否则返回 false
     */
    public boolean isSolid() {
        return solid;
    }

    /**
     * 获取游戏默认 tile 尺寸（像素）。
     *
     * @return titlesize，通常为 32px
     */
    public int getTileSize() {
        return Gamepanel.titlesize;
    }

    /**
     * 初始化所有方块类型。
     *
     * <p>通过 Class.forName() 触发子类的类加载，
     * 从而执行子类的静态初始化代码（注册到 REGISTRY）。</p>
     *
     * <p>调用时机：Gamepanel 构造函数中调用。</p>
     */
    public static void init() {
        try {
            Class.forName("block.Grass");
            Class.forName("block.Dirt");
            Class.forName("block.Stone");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}