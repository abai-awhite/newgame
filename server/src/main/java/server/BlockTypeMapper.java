package server;

/**
 * 物品名 ↔ 方块类型映射。
 *
 * <p>全部数据来自 {@link BlockData}（由 .tmp_assets/build.js 从
 * Minecraft 1.21.1 数据自动生成，包含 1.21 及更早版本全部方块）。</p>
 */
public final class BlockTypeMapper {

    private BlockTypeMapper() { }

    /** 物品名 -> 方块类型；未知物品返回 null。 */
    public static Integer itemToBlock(String itemName) {
        return itemName == null ? null : BlockData.ITEM_TO_BLOCK.get(itemName);
    }

    /** 方块类型 -> 方块名；未知方块返回 "未知"。 */
    public static String blockName(int blockType) {
        if (blockType >= 0 && blockType < BlockData.NAMES.length) {
            return BlockData.NAMES[blockType];
        }
        return "未知";
    }

    /** 方块类型 -> 破坏后的掉落物品名（无掉落返回 null）。 */
    public static String dropName(int blockType) {
        if (blockType >= 0 && blockType < BlockData.DROPS.length) {
            return BlockData.DROPS[blockType];
        }
        return null;
    }
}
