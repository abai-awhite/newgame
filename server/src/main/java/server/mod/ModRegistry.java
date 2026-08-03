package server.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 方块/物品/实体注册表（骨架）。
 *
 * <p>基于 core Block.REGISTRY 的注册表模式扩展，使用字符串命名空间 ID
 * （如 "minecraft:grass"）避免数值 ID 冲突。本期仅保留结构，不接入主流程。</p>
 */
public class ModRegistry {

    private final List<String> blockIds = new ArrayList<>();
    private final List<String> itemIds = new ArrayList<>();
    private final List<String> entityIds = new ArrayList<>();

    /** 注册自定义方块。 */
    public void registerBlock(String id, String name) {
        blockIds.add(id);
    }

    /** 注册自定义物品。 */
    public void registerItem(String id, String name) {
        itemIds.add(id);
    }

    /** 注册自定义实体。 */
    public void registerEntity(String id) {
        entityIds.add(id);
    }

    public List<String> getBlockIds() { return new ArrayList<>(blockIds); }
    public List<String> getItemIds() { return new ArrayList<>(itemIds); }
    public List<String> getEntityIds() { return new ArrayList<>(entityIds); }
}
