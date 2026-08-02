package block;

import main.world.Chunk;

/**
 * 石头方块。
 *
 * <h3>特性</h3>
 * <ul>
 *   <li>ID: Chunk.STONE (值为 3)</li>
 *   <li>名称: "Stone"</li>
 *   <li>实心: true（阻挡玩家移动）</li>
 *   <li>资源路径: /block/stone.png</li>
 * </ul>
 *
 * <h3>层级位置</h3>
 * <p>Stone 位于 Dirt 下方（地面以下 5 格及更深）。
 * 在此深度范围内的石头可通过 addCaves() 方法挖出空洞（洞穴系统）。</p>
 *
 * <h3>设计说明</h3>
 * <p>使用单例模式，确保整个游戏进程中只有一个 Stone 实例。
 * 私有构造函数通过父类 Block 构造，传入固定参数。
 * 类加载时自动注册到父类的 REGISTRY 中。</p>
 *
 * @see Block
 * @see Grass
 * @see Dirt
 */
public class Stone extends Block {

    /**
     * 单例实例。
     * 使用 private static final 保证全局唯一。
     */
    private static final Stone instance = new Stone();

    /**
     * 私有构造函数，防止外部通过 new 创建实例。
     *
     * <p>参数说明：</p>
     * <ul>
     *   <li>id: Chunk.STONE (3)，与 Chunk 中深层地下 tile 值对应</li>
     *   <li>name: "Stone"，调试用名称</li>
     *   <li>imagePath: "/block/stone.png"，石头方块贴图</li>
     *   <li>solid: true，石头方块阻挡玩家移动</li>
     * </ul>
     */
    private Stone() {
        super(Chunk.STONE, "Stone", "/block/stone.png", true);
    }

    /**
     * 获取 Stone 单例实例。
     *
     * @return 全局唯一的 Stone 实例
     */
    public static Stone getInstance() {
        return instance;
    }
}