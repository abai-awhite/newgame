package main.gui;

import main.Gameframe;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * 背包面板，4行16列共64个物品槽位。
 * 通过按E键切换显示/隐藏，支持槽位点击选择和拖拽。
 * 
 * <p>快捷栏：始终显示在屏幕底部，对应背包最下面一行（第4行，索引48-63）。</p>
 * 
 * <h3>交互方式</h3>
 * <ul>
 *   <li>左键拖拽：按住左键拖动物品到其他槽位</li>
 *   <li>右键选择：右键点击选中物品用于放置</li>
 * </ul>
 */
public class InventoryPanel {

    private static final Color BACKGROUND_COLOR = new Color(80, 60, 40, 220);
    private static final Color SLOT_COLOR = new Color(45, 35, 25, 200);
    private static final Color SLOT_BORDER_COLOR = new Color(100, 80, 60, 150);
    private static final Color SELECTED_SLOT_COLOR = new Color(255, 255, 255, 100);
    private static final Color SELECTED_BORDER_COLOR = Color.WHITE;
    private static final int SLOT_SIZE = 40;
    private static final int SLOT_GAP = 4;
    private static final int PADDING = 16;

    public static final int COLUMNS = 16;
    public static final int ROWS = 4;

    private final ItemSlot[] slots = new ItemSlot[COLUMNS * ROWS];
    private volatile boolean visible = false;
    private int selectedSlotIndex = -1;

    /** 快捷栏选中的槽位索引（48-63） */
    private int hotbarSelectedIndex = 48;

    /** 拖拽状态：是否正在拖拽物品 */
    private boolean isDragging = false;
    /** 拖拽状态：拖拽源槽位索引 */
    private int dragSourceIndex = -1;
    /** 拖拽状态：被拖拽的物品 */
    private Item draggedItem = null;
    /** 拖拽状态：鼠标位置（用于渲染拖拽物品） */
    private int dragMouseX = 0;
    private int dragMouseY = 0;

    private final int panelWidth;
    private final int panelHeight;
    private int panelX;
    private int panelY;

    public InventoryPanel(int screenWidth, int screenHeight) {
        panelWidth = COLUMNS * SLOT_SIZE + (COLUMNS - 1) * SLOT_GAP + PADDING * 2;
        panelHeight = ROWS * SLOT_SIZE + (ROWS - 1) * SLOT_GAP + PADDING * 2;

        panelX = (screenWidth - panelWidth) / 2;
        panelY = (screenHeight - panelHeight) / 2;

        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ItemSlot(i);
        }
    }

    public void toggle() {
        visible = !visible;
        if (!visible) {
            selectedSlotIndex = -1;
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public int getSelectedSlotIndex() {
        return selectedSlotIndex;
    }

    /**
     * 获取选中槽位的物品名，若无选中返回 null。
     * 优先返回快捷栏选中物品，其次返回背包选中物品。
     */
    public String getSelectedItemName() {
        // 优先使用快捷栏选中物品
        if (hotbarSelectedIndex >= 48 && hotbarSelectedIndex < 64) {
            ItemSlot slot = slots[hotbarSelectedIndex];
            if (slot.hasItem()) {
                return slot.getItem().getName();
            }
        }
        
        // 其次使用背包选中物品
        if (selectedSlotIndex >= 0 && selectedSlotIndex < slots.length) {
            ItemSlot slot = slots[selectedSlotIndex];
            return slot.hasItem() ? slot.getItem().getName() : null;
        }
        return null;
    }

    /**
     * 消耗选中槽位中的物品。
     *
     * @param count 消耗数量
     * @return true 消耗成功，false 物品不足或未选中
     */
    public synchronized boolean consumeSelectedItem(int count) {
        if (selectedSlotIndex < 0 || selectedSlotIndex >= slots.length) return false;
        ItemSlot slot = slots[selectedSlotIndex];
        if (!slot.hasItem()) return false;

        Item item = slot.getItem();
        if (item.getCount() < count) return false;

        int newCount = item.getCount() - count;
        if (newCount <= 0) {
            slot.clear();
            selectedSlotIndex = -1;
        } else {
            slot.setItem(new Item(item.getName(), newCount));
        }
        return true;
    }

    /**
     * 处理鼠标左键按下（开始拖拽）。
     * 
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     * @return true 如果点击到了背包区域
     */
    public synchronized boolean handleLeftPress(int screenX, int screenY) {
        if (!visible) return false;

        int slotIndex = getSlotIndexAt(screenX, screenY);
        if (slotIndex < 0) return false;

        if (slots[slotIndex].hasItem()) {
            isDragging = true;
            dragSourceIndex = slotIndex;
            draggedItem = slots[slotIndex].getItem();
            dragMouseX = screenX;
            dragMouseY = screenY;
            slots[slotIndex].clear();
        }
        return true;
    }

    /**
     * 处理鼠标左键释放（结束拖拽）。
     * 
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     * @return true 如果释放到了背包区域
     */
    public synchronized boolean handleLeftRelease(int screenX, int screenY) {
        if (!isDragging || draggedItem == null) {
            isDragging = false;
            draggedItem = null;
            dragSourceIndex = -1;
            return false;
        }

        int slotIndex = getSlotIndexAt(screenX, screenY);
        
        if (slotIndex >= 0 && slotIndex < slots.length) {
            if (slots[slotIndex].hasItem()) {
                Item targetItem = slots[slotIndex].getItem();
                slots[dragSourceIndex].setItem(targetItem);
            } else {
                slots[dragSourceIndex].clear();
            }
            slots[slotIndex].setItem(draggedItem);
        } else {
            slots[dragSourceIndex].setItem(draggedItem);
        }

        isDragging = false;
        draggedItem = null;
        dragSourceIndex = -1;
        return true;
    }

    /**
     * 处理鼠标右键点击（选择物品）。
     * 
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     * @return true 如果点击到了背包区域
     */
    public synchronized boolean handleRightClick(int screenX, int screenY) {
        if (!visible) return false;

        int slotIndex = getSlotIndexAt(screenX, screenY);
        if (slotIndex < 0) return false;

        if (slots[slotIndex].hasItem()) {
            selectedSlotIndex = (selectedSlotIndex == slotIndex) ? -1 : slotIndex;
        } else {
            selectedSlotIndex = -1;
        }
        return true;
    }

    /**
     * 更新拖拽时的鼠标位置。
     */
    public void updateDragPosition(int screenX, int screenY) {
        dragMouseX = screenX;
        dragMouseY = screenY;
    }

    /**
     * 获取指定屏幕坐标处的槽位索引。
     * 
     * @return 槽位索引，如果不在背包区域返回 -1
     */
    private int getSlotIndexAt(int screenX, int screenY) {
        int screenWidth = Gameframe.getsizew();
        int screenHeight = Gameframe.getsizeh();
        int px = (screenWidth - panelWidth) / 2;
        int py = (screenHeight - panelHeight) / 2 + screenHeight / 6;

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int slotIndex = row * COLUMNS + col;
                int slotX = px + PADDING + col * (SLOT_SIZE + SLOT_GAP);
                int slotY = py + PADDING + row * (SLOT_SIZE + SLOT_GAP);

                if (screenX >= slotX && screenX <= slotX + SLOT_SIZE &&
                    screenY >= slotY && screenY <= slotY + SLOT_SIZE) {
                    return slotIndex;
                }
            }
        }
        return -1;
    }

    /**
     * 处理鼠标点击背包槽位（已废弃，使用 handleLeftPress/handleRightClick）。
     *
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y
     * @return true 如果点击到了背包区域
     */
    @Deprecated
    public boolean handleClick(int screenX, int screenY) {
        return handleRightClick(screenX, screenY);
    }

    /**
     * 检查鼠标是否在背包面板区域内。
     */
    public boolean isMouseInPanel(int screenX, int screenY) {
        if (!visible) return false;
        int screenWidth = Gameframe.getsizew();
        int screenHeight = Gameframe.getsizeh();
        int px = (screenWidth - panelWidth) / 2;
        int py = (screenHeight - panelHeight) / 2 + screenHeight / 6;
        return screenX >= px && screenX <= px + panelWidth &&
               screenY >= py && screenY <= py + panelHeight;
    }

    /**
     * 添加物品到背包。
     */
    public synchronized int addItem(String itemName, int count) {
        int remaining = count;

        for (ItemSlot slot : slots) {
            if (remaining <= 0) break;
            if (!slot.hasItem()) continue;

            Item existing = slot.getItem();
            if (existing.getName().equals(itemName)) {
                int existingCount = existing.getCount();
                int canAdd = Item.MAX_COUNT - existingCount;
                if (canAdd > 0) {
                    int addAmount = Math.min(remaining, canAdd);
                    slot.setItem(new Item(itemName, existingCount + addAmount));
                    remaining -= addAmount;
                }
            }
        }

        if (remaining > 0) {
            for (ItemSlot slot : slots) {
                if (remaining <= 0) break;
                if (!slot.hasItem()) {
                    int addAmount = Math.min(remaining, Item.MAX_COUNT);
                    slot.setItem(new Item(itemName, addAmount));
                    remaining -= addAmount;
                }
            }
        }

        return count - remaining;
    }

    public void render(Graphics2D g2) {
        if (!visible) {
            return;
        }

        int screenWidth = Gameframe.getsizew();
        int screenHeight = Gameframe.getsizeh();

        panelX = (screenWidth - panelWidth) / 2;
        panelY = (screenHeight - panelHeight) / 2 + screenHeight / 6;

        AffineTransform oldTransform = g2.getTransform();
        Composite oldComposite = g2.getComposite();

        g2.setColor(BACKGROUND_COLOR);
        g2.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 12, 12);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int slotIndex = row * COLUMNS + col;
                int slotX = panelX + PADDING + col * (SLOT_SIZE + SLOT_GAP);
                int slotY = panelY + PADDING + row * (SLOT_SIZE + SLOT_GAP);

                boolean isSelected = (slotIndex == selectedSlotIndex);

                g2.setColor(isSelected ? SELECTED_SLOT_COLOR : SLOT_COLOR);
                g2.fillRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE);

                g2.setColor(isSelected ? SELECTED_BORDER_COLOR : SLOT_BORDER_COLOR);
                g2.setStroke(new BasicStroke(isSelected ? 3 : 2));
                g2.drawRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE);

                ItemSlot slot = slots[slotIndex];
                if (slot.hasItem()) {
                    slot.render(g2, slotX, slotY, SLOT_SIZE);
                }
            }
        }

        // 渲染拖拽中的物品（跟随鼠标）
        if (isDragging && draggedItem != null) {
            Image blockImg = Item.getImage(draggedItem.getName());
            int drawX = dragMouseX - SLOT_SIZE / 2;
            int drawY = dragMouseY - SLOT_SIZE / 2;
            
            g2.setColor(new Color(255, 255, 255, 150));
            g2.fillRect(drawX, drawY, SLOT_SIZE, SLOT_SIZE);
            
            if (blockImg != null) {
                g2.drawImage(blockImg, drawX + 4, drawY + 4, SLOT_SIZE - 8, SLOT_SIZE - 8, null);
            }

            Font oldFont = g2.getFont();
            Font countFont = new Font("微软雅黑", Font.BOLD, 11);
            g2.setFont(countFont);
            g2.setColor(Color.WHITE);
            String countText = String.valueOf(draggedItem.getCount());
            int countWidth = g2.getFontMetrics().stringWidth(countText);
            g2.drawString(countText, drawX + SLOT_SIZE - countWidth - 3, drawY + SLOT_SIZE - 4);
            g2.setFont(oldFont);
        }

        g2.setTransform(oldTransform);
        g2.setComposite(oldComposite);
    }

    public Point getSlotCenter(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.length) {
            return null;
        }
        int row = slotIndex / COLUMNS;
        int col = slotIndex % COLUMNS;
        int slotX = panelX + PADDING + col * (SLOT_SIZE + SLOT_GAP);
        int slotY = panelY + PADDING + row * (SLOT_SIZE + SLOT_GAP);
        return new Point(slotX + SLOT_SIZE / 2, slotY + SLOT_SIZE / 2);
    }

    /**
     * 获取所有物品槽的数据，用于保存。
     * 
     * @return 物品数据列表，每个元素格式为 "物品名|数量"，空槽为空字符串
     */
    public synchronized java.util.List<String> getAllSlotData() {
        java.util.List<String> data = new java.util.ArrayList<>();
        for (ItemSlot slot : slots) {
            if (slot.hasItem()) {
                Item item = slot.getItem();
                data.add(item.getName() + "|" + item.getCount());
            } else {
                data.add("");
            }
        }
        return data;
    }

    /**
     * 从数据加载所有物品槽，用于读取存档。
     * 
     * @param slotData 物品数据列表，每个元素格式为 "物品名|数量"，空字符串表示空槽
     */
    public synchronized void loadAllSlotData(java.util.List<String> slotData) {
        for (int i = 0; i < slots.length && i < slotData.size(); i++) {
            String data = slotData.get(i);
            if (data == null || data.isEmpty()) {
                slots[i].clear();
            } else {
                String[] parts = data.split("\\|");
                if (parts.length == 2) {
                    String itemName = parts[0];
                    int count = Integer.parseInt(parts[1]);
                    slots[i].setItem(new Item(itemName, count));
                } else {
                    slots[i].clear();
                }
            }
        }
    }

    // ==================== 快捷栏相关方法 ====================

    /**
     * 渲染快捷栏（始终显示在屏幕底部）。
     * 快捷栏对应背包最下面一行（索引48-63）。
     * 
     * @param g2 图形上下文
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     */
    public void renderHotbar(Graphics2D g2, int screenWidth, int screenHeight) {
        int hotbarWidth = COLUMNS * SLOT_SIZE + (COLUMNS - 1) * SLOT_GAP + PADDING * 2;
        int hotbarHeight = SLOT_SIZE + PADDING * 2;
        
        int hotbarX = (screenWidth - hotbarWidth) / 2;
        int hotbarY = screenHeight - hotbarHeight - 10;

        g2.setColor(BACKGROUND_COLOR);
        g2.fillRoundRect(hotbarX, hotbarY, hotbarWidth, hotbarHeight, 8, 8);

        Font keyFont = new Font("Arial", Font.BOLD, 10);
        Font oldFont = g2.getFont();

        for (int col = 0; col < COLUMNS; col++) {
            int slotIndex = (ROWS - 1) * COLUMNS + col;
            int slotX = hotbarX + PADDING + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = hotbarY + PADDING;

            boolean isSelected = (slotIndex == hotbarSelectedIndex);

            g2.setColor(isSelected ? SELECTED_SLOT_COLOR : SLOT_COLOR);
            g2.fillRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE);

            g2.setColor(isSelected ? SELECTED_BORDER_COLOR : SLOT_BORDER_COLOR);
            g2.setStroke(new BasicStroke(isSelected ? 3 : 2));
            g2.drawRect(slotX, slotY, SLOT_SIZE, SLOT_SIZE);

            ItemSlot slot = slots[slotIndex];
            if (slot.hasItem()) {
                slot.render(g2, slotX, slotY, SLOT_SIZE);
            }

            g2.setFont(keyFont);
            g2.setColor(new Color(200, 200, 200, 180));
            String keyText = (col < 9) ? String.valueOf(col + 1) : "0";
            g2.drawString(keyText, slotX + 3, slotY + 12);
        }

        g2.setFont(oldFont);
    }

    /**
     * 选择快捷栏槽位（通过数字键1-9, 0）。
     * 
     * @param keyNumber 数字键（1-9对应槽位0-8，0对应槽位9）
     */
    public void selectHotbarSlotByKey(int keyNumber) {
        if (keyNumber >= 1 && keyNumber <= 9) {
            hotbarSelectedIndex = 48 + (keyNumber - 1);
        } else if (keyNumber == 0) {
            hotbarSelectedIndex = 48 + 9;
        }
    }

    /**
     * 通过鼠标点击选择快捷栏槽位。
     * 
     * @param screenX 鼠标屏幕X
     * @param screenY 鼠标屏幕Y
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return true 如果点击到了快捷栏区域
     */
    public boolean handleHotbarClick(int screenX, int screenY, int screenWidth, int screenHeight) {
        int hotbarWidth = COLUMNS * SLOT_SIZE + (COLUMNS - 1) * SLOT_GAP + PADDING * 2;
        int hotbarHeight = SLOT_SIZE + PADDING * 2;
        
        int hotbarX = (screenWidth - hotbarWidth) / 2;
        int hotbarY = screenHeight - hotbarHeight - 10;

        for (int col = 0; col < COLUMNS; col++) {
            int slotIndex = (ROWS - 1) * COLUMNS + col;
            int slotX = hotbarX + PADDING + col * (SLOT_SIZE + SLOT_GAP);
            int slotY = hotbarY + PADDING;

            if (screenX >= slotX && screenX <= slotX + SLOT_SIZE &&
                screenY >= slotY && screenY <= slotY + SLOT_SIZE) {
                hotbarSelectedIndex = slotIndex;
                return true;
            }
        }
        return false;
    }

    /**
     * 获取快捷栏选中的槽位索引。
     * 
     * @return 槽位索引（48-63）
     */
    public int getHotbarSelectedIndex() {
        return hotbarSelectedIndex;
    }
}