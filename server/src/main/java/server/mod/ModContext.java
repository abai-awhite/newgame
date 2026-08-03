package server.mod;

/**
 * 模组上下文（骨架）：向模组暴露注册表/事件/配置等扩展点句柄。
 *
 * <p>本期仅保留接口结构，后期扩展方法时只新增、不改签名。</p>
 */
public interface ModContext {

    /** 访问方块/物品/实体注册表。 */
    ModRegistry registry();

    /** 访问事件总线（订阅/发布游戏事件）。 */
    ModEventBus eventBus();
}
