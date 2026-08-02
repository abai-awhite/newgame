package main.gui;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包面板（含快捷栏）。
 */
public class InventoryPanel {

    private boolean visible = false;
    private final List<ItemSlot> slots = new ArrayList<>();
    private int selectedSlot = 0;

    private static final int HOTBAR_SLOTS = 9;
    private static final int INVENTORY_SLOTS = 36;
    private static final int TOTAL_SLOTS = HOTBAR_SLOTS + INVENTORY_SLOTS;
    private static final int SLOT_SIZE = 40;
    private static final int HOTBAR_Y_OFFSET = 30;

    // 拖拽状态
    private ItemSlot dragSource = null;
    private int dragMouseX = 0;
    private int dragMouseY = 0;
    private boolean isDragging = false;

    public InventoryPanel(int screenWidth, int screenHeight) {
        for (int i = 0; i < TOTAL_SLOTS; i++) {
            slots.add(new ItemSlot(i));
        }
    }

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() { return visible; }

    public String getSelectedItemName() {
        ItemSlot slot = slots.get(selectedSlot);
        if (slot.hasItem()) {
            return slot.getItem().getName();
        }
        return null;
    }

    public void consumeSelectedItem(int count) {
        ItemSlot slot = slots.get(selectedSlot);
        if (slot.hasItem()) {
            Item item = slot.getItem();
            int newCount = item.getCount() - count;
            if (newCount <= 0) {
                slot.clear();
            } else {
                slot.setItem(new Item(item.getName(), newCount));
            }
        }
    }

    public int addItem(String itemName, int count) {
        // 先尝试堆叠到已有物品
        for (ItemSlot slot : slots) {
            if (slot.hasItem() && slot.getItem().getName().equals(itemName)) {
                int existing = slot.getItem().getCount();
                int canAdd = Item.MAX_COUNT - existing;
                int toAdd = Math.min(canAdd, count);
                if (toAdd > 0) {
                    slot.setItem(new Item(itemName, existing + toAdd));
                    count -= toAdd;
                    if (count <= 0) return toAdd;
                }
            }
        }
        // 放入空槽
        for (ItemSlot slot : slots) {
            if (!slot.hasItem()) {
                int toAdd = Math.min(Item.MAX_COUNT, count);
                slot.setItem(new Item(itemName, toAdd));
                count -= toAdd;
                if (count <= 0) return toAdd;
            }
        }
        return count; // 剩余未放入的数量
    }

    public void selectHotbarSlotByKey(int key) {
        if (key >= 1 && key <= 9) {
            selectedSlot = key - 1;
        } else if (key == 0) {
            selectedSlot = 9;
        }
    }

    public boolean handleLeftPress(int mouseX, int mouseY) {
        if (!visible) return false;
        // 检查背包区域点击
        int slotIndex = getSlotAt(mouseX, mouseY);
        if (slotIndex >= 0) {
            ItemSlot clicked = slots.get(slotIndex);
            if (clicked.hasItem()) {
                dragSource = clicked;
                isDragging = true;
                dragMouseX = mouseX;
                dragMouseY = mouseY;
            }
            return true;
        }
        return false;
    }

    public void handleLeftRelease(int mouseX, int mouseY) {
        if (!isDragging || dragSource == null) return;

        int slotIndex = getSlotAt(mouseX, mouseY);
        if (slotIndex >= 0 && slotIndex != dragSource.getIndex()) {
            ItemSlot target = slots.get(slotIndex);
            Item temp = dragSource.getItem();
            dragSource.setItem(target.getItem());
            target.setItem(temp);
        }

        isDragging = false;
        dragSource = null;
    }

    public void updateDragPosition(int mouseX, int mouseY) {
        if (isDragging) {
            dragMouseX = mouseX;
            dragMouseY = mouseY;
        }
    }

    public void handleRightClick(int mouseX, int mouseY) {
        if (!visible) return;
        int slotIndex = getSlotAt(mouseX, mouseY);
        if (slotIndex >= 0) {
            ItemSlot slot = slots.get(slotIndex);
            if (slot.hasItem()) {
                Item item = slot.getItem();
                int count = item.getCount();
                if (count > 1) {
                    int half = count / 2;
                    slot.setItem(new Item(item.getName(), count - half));
                    // 尝试放入其他槽
                    addItem(item.getName(), half);
                }
            }
        }
    }

    public void handleHotbarClick(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int hotbarY = HOTBAR_Y_OFFSET;
        int totalWidth = HOTBAR_SLOTS * SLOT_SIZE;
        int startX = (screenWidth - totalWidth) / 2;

        if (mouseY >= hotbarY && mouseY <= hotbarY + SLOT_SIZE) {
            int relX = mouseX - startX;
            if (relX >= 0 && relX < totalWidth) {
                selectedSlot = relX / SLOT_SIZE;
            }
        }
    }

    public boolean isMouseInPanel(int mouseX, int mouseY) {
        if (!visible) return false;
        return getSlotAt(mouseX, mouseY) >= 0;
    }

    private int getSlotAt(int mouseX, int mouseY) {
        if (!visible) return -1;
        // 简化：检查背包区域
        int invStartX = 50;
        int invStartY = 100;
        int cols = 9;
        int rows = INVENTORY_SLOTS / cols;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = invStartX + col * SLOT_SIZE;
                int sy = invStartY + row * SLOT_SIZE;
                if (mouseX >= sx && mouseX <= sx + SLOT_SIZE &&
                    mouseY >= sy && mouseY <= sy + SLOT_SIZE) {
                    return HOTBAR_SLOTS + row * cols + col;
                }
            }
        }
        return -1;
    }

    public void render(SpriteBatch batch, BitmapFont font, int screenWidth, int screenHeight) {
        if (!visible) return;

        int invStartX = 50;
        int invStartY = 100;
        int cols = 9;
        int rows = INVENTORY_SLOTS / cols;

        // 绘制背包背景
        com.badlogic.gdx.graphics.glutils.ShapeRenderer sr = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.3f, 0.3f, 0.3f, 0.8f);
        sr.rect(invStartX - 5, invStartY - 5, cols * SLOT_SIZE + 10, rows * SLOT_SIZE + 10);
        sr.end();
        sr.dispose();

        // 绘制物品槽
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = HOTBAR_SLOTS + row * cols + col;
                int sx = invStartX + col * SLOT_SIZE;
                int sy = invStartY + row * SLOT_SIZE;
                renderSlot(batch, font, slots.get(idx), sx, sy);
            }
        }
    }

    public void renderHotbar(SpriteBatch batch, BitmapFont font, int screenWidth, int screenHeight) {
        int hotbarY = HOTBAR_Y_OFFSET;
        int totalWidth = HOTBAR_SLOTS * SLOT_SIZE;
        int startX = (screenWidth - totalWidth) / 2;

        // 绘制快捷栏背景
        com.badlogic.gdx.graphics.glutils.ShapeRenderer sr = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.3f, 0.3f, 0.3f, 0.8f);
        sr.rect(startX - 2, hotbarY - 2, totalWidth + 4, SLOT_SIZE + 4);
        sr.end();
        sr.dispose();

        for (int i = 0; i < HOTBAR_SLOTS; i++) {
            int sx = startX + i * SLOT_SIZE;
            renderSlot(batch, font, slots.get(i), sx, hotbarY);

            // 选中高亮
            if (i == selectedSlot) {
                sr = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
                sr.setProjectionMatrix(batch.getProjectionMatrix());
                sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
                sr.setColor(1, 1, 1, 1);
                sr.rect(sx - 2, hotbarY - 2, SLOT_SIZE + 4, SLOT_SIZE + 4);
                sr.end();
                sr.dispose();
            }
        }
    }

    private void renderSlot(SpriteBatch batch, BitmapFont font, ItemSlot slot, int x, int y) {
        // 绘制槽位背景
        com.badlogic.gdx.graphics.glutils.ShapeRenderer sr = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        sr.setColor(0.5f, 0.5f, 0.5f, 0.5f);
        sr.rect(x, y, SLOT_SIZE, SLOT_SIZE);
        sr.end();
        sr.dispose();

        if (!slot.hasItem()) return;

        // 绘制物品图标
        String texPath = Item.getTexturePath(slot.getItem().getName());
        if (texPath != null) {
            try {
                Texture tex = new Texture(texPath);
                batch.draw(tex, x + 4, y + 4, SLOT_SIZE - 8, SLOT_SIZE - 8);
            } catch (Exception e) {
                // 纹理加载失败
            }
        }

        // 绘制数量
        font.draw(batch, String.valueOf(slot.getItem().getCount()), x + SLOT_SIZE - 20, y + 12);
    }

    public List<String> getAllSlotData() {
        List<String> data = new ArrayList<>();
        for (ItemSlot slot : slots) {
            if (slot.hasItem()) {
                data.add(slot.getItem().getName() + "|" + slot.getItem().getCount());
            } else {
                data.add("");
            }
        }
        return data;
    }

    public void loadAllSlotData(List<String> data) {
        for (int i = 0; i < data.size() && i < slots.size(); i++) {
            String entry = data.get(i);
            if (entry != null && !entry.isEmpty()) {
                String[] parts = entry.split("\\|");
                if (parts.length >= 2) {
                    try {
                        String name = parts[0];
                        int count = Integer.parseInt(parts[1]);
                        slots.get(i).setItem(new Item(name, count));
                    } catch (NumberFormatException e) {
                        // 跳过无效数据
                    }
                }
            }
        }
    }
}
