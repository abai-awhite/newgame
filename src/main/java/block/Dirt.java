package block;

import main.world.Chunk;

/**
 * 泥土方块。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>ID: Chunk.DIRT (值为 2)</li>
 *   <li>名称: "Dirt"</li>
 *   <li>实心: true（阻挡玩家移动）</li>
 *   <li>资源路径: /block/soil.png</li>
 * </ul>
 *
 * <h3>层级位置</h3>
 * <p>Dirt 位于 GRASS 下方（地面以下 1~4 格深度）。
 * 超过 4 格深度后变为 STONE。</p>
 *
 * <h3>设计说明</h3>
 * <p>使用单例模式，确保整个游戏进程中只有一个 Dirt 实例。
 * 私有构造函数通过父类 Block 构造，传入固定参数。
 * 类加载时自动注册到父类的 REGISTRY 中。</p>
 *
 * @see Block
 * @see Grass
 * @see Stone
 */
public class Dirt extends Block {

    /**
     * 单例实例。
     * 使用 private static final 保证全局唯一。
     */
    private static final Dirt instance = new Dirt();

    /**
     * 私有构造函数，防止外部通过 new 创建实例。
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>id: Chunk.DIRT (2)，与 Chunk 中地下 tile 值对应</li>
     *   <li>name: "Dirt"，调试用名称</li>
     *   <li>imagePath: "/block/soil.png"，泥土方块贴图</li>
     *   <li>solid: true，泥土方块阻挡玩家移动</li>
     * </ul>
     */
    private Dirt() {
        super(Chunk.DIRT, "Dirt", "/block/soil.png", true);
    }

    /**
     * 获取 Dirt 单例实例。
     *
     * @return 全局唯一的 Dirt 实例
     */
    public static Dirt getInstance() {
        return instance;
    }
}