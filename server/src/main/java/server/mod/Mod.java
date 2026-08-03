package server.mod;

/**
 * 模组主入口接口（骨架）。
 *
 * <p>模组通过实现本接口并在 mods/ 目录提供实现类来接入游戏。
 * 本期仅保留接口结构，不接入主流程、不测试，后期加载 .jar 模组时实现。</p>
 *
 * <p>兼容性约定：方法签名固定，只增不改。</p>
 */
public interface Mod {

    /** 模组唯一 ID（如 "minecraft:example"）。 */
    String id();

    /** 模组初始化入口：获取注册表/事件/配置等扩展点。 */
    void init(ModContext context);
}
