package main.gui;

import java.awt.*;

/**
 * 物品槽位，存放一个物品堆叠。
 */
public class ItemSlot {
    private final int index;
    private Item item;

    public ItemSlot(int index) {
        this.index = index;
    }

    public int getIndex() {
        return index;
    }

    public boolean hasItem() {
        return item != null;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Item getItem() {
        return item;
    }

    public void clear() {
        this.item = null;
    }

    /**
     * 渲染槽位中的物品。
     */
    public void render(Graphics2D g2, int x, int y, int size) {
        if (item == null) {
            return;
        }

        Image blockImg = Item.getImage(item.getName());
        if (blockImg != null) {
            int padding = 4;
            g2.drawImage(blockImg, x + padding, y + padding, size - padding * 2, size - padding * 2, null);
        }

        Font oldFont = g2.getFont();
        Font itemFont = new Font("微软雅黑", Font.BOLD, 11);
        g2.setFont(itemFont);
        g2.setColor(Color.WHITE);

        String countText = String.valueOf(item.getCount());
        int countWidth = g2.getFontMetrics().stringWidth(countText);
        g2.drawString(countText, x + size - countWidth - 3, y + size - 4);

        g2.setFont(oldFont);
    }
}