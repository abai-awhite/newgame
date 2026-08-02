package main.gui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 物品类，表示背包中的一个物品堆叠。
 */
public class Item {
    /** 物品最大堆叠数量 */
    public static final int MAX_COUNT = 256;

    /** 物品名 → 方块图片缓存 */
    private static final Map<String, Image> IMAGE_CACHE = new HashMap<>();

    static {
        loadImage("Grass", "/block/grass.png");
        loadImage("Dirt", "/block/soil.png");
        loadImage("Stone", "/block/stone.png");
    }

    private static void loadImage(String name, String path) {
        try {
            java.io.InputStream is = Item.class.getResourceAsStream(path);
            if (is == null) return;
            Image img = ImageIO.read(is);
            if (img != null) {
                IMAGE_CACHE.put(name, img);
            }
        } catch (Exception e) {
            // 图片加载失败则跳过
        }
    }

    /**
     * 获取物品对应的方块图片。
     *
     * @param itemName 物品名称
     * @return 方块图片，未找到返回 null
     */
    public static Image getImage(String itemName) {
        return IMAGE_CACHE.get(itemName);
    }

    private final String name;
    private final int count;

    /**
     * 构造一个物品。
     *
     * @param name  物品名称
     * @param count 物品数量（自动钳制到 MAX_COUNT）
     */
    public Item(String name, int count) {
        this.name = name;
        this.count = Math.min(count, MAX_COUNT);
    }

    public String getName() {
        return name;
    }

    public int getCount() {
        return count;
    }
}