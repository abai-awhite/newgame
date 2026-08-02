package main.gui;

import java.util.HashMap;
import java.util.Map;

/**
 * 物品类，表示背包中的一个物品堆叠。
 * 纯逻辑类，不依赖渲染。
 */
public class Item {
    public static final int MAX_COUNT = 256;

    /** 物品名 → 纹理路径缓存 */
    private static final Map<String, String> TEXTURE_PATHS = new HashMap<>();

    static {
        TEXTURE_PATHS.put("Grass", "block/grass.png");
        TEXTURE_PATHS.put("Dirt", "block/soil.png");
        TEXTURE_PATHS.put("Stone", "block/stone.png");
    }

    public static String getTexturePath(String itemName) {
        return TEXTURE_PATHS.get(itemName);
    }

    private final String name;
    private final int count;

    public Item(String name, int count) {
        this.name = name;
        this.count = Math.min(count, MAX_COUNT);
    }

    public String getName() { return name; }
    public int getCount() { return count; }
}
