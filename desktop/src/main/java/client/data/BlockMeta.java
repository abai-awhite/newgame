package client.data;

/**
 * 方块元数据（对应 game.js 的 TILE_META，数据源 blocks_data.js）。
 */
public class BlockMeta {
    public final int id;
    public final String name;
    public final String displayName;
    public final boolean solid;
    public final boolean transparent;
    public final int stackSize;
    public final float hardness;
    /** 掉落物品名（可能为 null，如空气/水） */
    public final String drops;
    /** 纹理文件名（可能为 null） */
    public final String texture;

    public BlockMeta(int id, String name, String displayName, boolean solid, boolean transparent,
                     int stackSize, float hardness, String drops, String texture) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.solid = solid;
        this.transparent = transparent;
        this.stackSize = stackSize;
        this.hardness = hardness;
        this.drops = drops;
        this.texture = texture;
    }

    public boolean isWaterLike() {
        return name != null && (name.contains("water") || name.contains("seagrass") || name.contains("kelp"));
    }
}
