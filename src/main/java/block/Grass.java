package block;

import main.world.Chunk;

/**
 * 草地方块。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>ID: Chunk.GRASS (值为 1)</li>
 *   <li>名称: "Grass"</li>
 *   <li>实心: true（阻挡玩家移动）</li>
 *   <li>资源路径: /block/grass.png</li>
 * </ul>
 *
 * <h3>设计说明</h3>
 * <p>使用单例模式，确保整个游戏进程中只有一个 Grass 实例。
 * 私有构造函数通过父类 Block 构造，传入固定参数。
 * 类加载时自动注册到父类的 REGISTRY 中。</p>
 *
 * @see Block
 * @see Dirt
 * @see Stone
 */
public class Grass extends Block {

    /**
     * 单例实例。
     * 使用 private static final 保证全局唯一。
     */
    private static final Grass instance = new Grass();

    /**
     * 私有构造函数，防止外部通过 new 创建实例。
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>id: Chunk.GRASS (1)，与 Chunk 中地表 tile 值对应</li>
     *   <li>name: "Grass"，调试用名称</li>
     *   <li>imagePath: "/block/grass.png"，草地方块贴图</li>
     *   <li>solid: true，草地方块阻挡玩家移动</li>
     * </ul>
     */
    private Grass() {
        super(Chunk.GRASS, "Grass", "/block/grass.png", true);
    }

    /**
     * 获取 Grass 单例实例。
     *
     * @return 全局唯一的 Grass 实例
     */
    public static Grass getInstance() {
        return instance;
    }
}