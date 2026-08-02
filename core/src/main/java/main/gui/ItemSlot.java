package main.gui;

/**
 * 物品槽位（纯逻辑类）。
 */
public class ItemSlot {
    private final int index;
    private Item item;

    public ItemSlot(int index) {
        this.index = index;
    }

    public int getIndex() { return index; }
    public boolean hasItem() { return item != null; }
    public void setItem(Item item) { this.item = item; }
    public Item getItem() { return item; }
    public void clear() { this.item = null; }
}
