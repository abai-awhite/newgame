package main;

import block.Block;
import entity.Player;
import main.world.Chunk;
import main.world.InfiniteMap;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块交互管理器（纯逻辑类）。
 *
 * <p>处理玩家与方块的交互操作，包括左键破坏方块和右键放置方块。
 * 不包含任何渲染代码，所有输入状态由外部通过公有字段设置。</p>
 */
public class BlockInteraction {

    // ==================== 交互配置 ====================

    /** 最大交互距离（格子数） */
    private static final int MAX_INTERACT_DISTANCE = 6;

    /** 方块破坏冷却时间（tick 数） */
    private static final int BREAK_COOLDOWN = 5;

    /** 方块放置冷却时间（tick 数） */
    private static final int PLACE_COOLDOWN = 5;

    // ==================== 依赖对象 ====================

    private final Player player;
    private final InfiniteMap infiniteMap;
    private final InventoryCallback inventoryCallback;
    private final int tileSize;

    // ==================== 冷却计数器 ====================

    private int breakCooldown = 0;
    private int placeCooldown = 0;

    // ==================== 输入状态（由外部每帧设置） ====================

    /** 鼠标左键是否按下 */
    public boolean mouseLeftPressed;

    /** 鼠标右键是否按下 */
    public boolean mouseRightPressed;

    /** 鼠标在屏幕上的 X 坐标（像素） */
    public int mouseScreenX;

    /** 鼠标在屏幕上的 Y 坐标（像素） */
    public int mouseScreenY;

    /** 鼠标是否在游戏面板区域内 */
    public boolean mouseInPanel;

    /** 摄像机 X 位置（世界坐标像素） */
    public double cameraX;

    /** 摄像机 Y 位置（世界坐标像素） */
    public double cameraY;

    // ==================== 选中方块 ====================

    /** 当前选中的方块 X 坐标（格子） */
    private int selectedTileX = -1;

    /** 当前选中的方块 Y 坐标（格子） */
    private int selectedTileY = -1;

    // ==================== 物品名 → 方块类型映射 ====================

    private static final Map<String, Integer> ITEM_TO_BLOCK = new HashMap<>();

    static {
        ITEM_TO_BLOCK.put("Grass", Chunk.GRASS);
        ITEM_TO_BLOCK.put("Dirt", Chunk.DIRT);
        ITEM_TO_BLOCK.put("Stone", Chunk.STONE);
        ITEM_TO_BLOCK.put("Sand", Chunk.SAND);
        ITEM_TO_BLOCK.put("Wood", Chunk.FOREST);
        ITEM_TO_BLOCK.put("Leaves", Chunk.FOREST);
    }

    // ==================== 回调接口 ====================

    /**
     * 背包回调接口，用于与背包系统解耦。
     */
    public interface InventoryCallback {
        /** 返回当前选中物品的名称，无选中物品时返回 null */
        String getSelectedItemName();
        /** 消耗指定数量的当前选中物品 */
        void consumeSelectedItem(int count);
    }

    // ==================== 构造方法 ====================

    /**
     * 构造方块交互管理器。
     *
     * @param player            玩家对象
     * @param infiniteMap       无限地图
     * @param inventoryCallback 背包回调
     * @param tileSize          每个方块的像素尺寸
     */
    public BlockInteraction(Player player, InfiniteMap infiniteMap,
                            InventoryCallback inventoryCallback, int tileSize) {
        this.player = player;
        this.infiniteMap = infiniteMap;
        this.inventoryCallback = inventoryCallback;
        this.tileSize = tileSize;
    }

    // ==================== 主更新方法 ====================

    /**
     * 每逻辑帧调用，处理方块交互逻辑。
     *
     * <p>调用顺序：</p>
     * <ol>
     *   <li>更新冷却计数器</li>
     *   <li>更新方块选择（射线检测）</li>
     *   <li>处理鼠标左键（破坏方块）</li>
     *   <li>处理鼠标右键（放置方块）</li>
     * </ol>
     */
    public void update() {
        if (breakCooldown > 0) breakCooldown--;
        if (placeCooldown > 0) placeCooldown--;

        updateTileSelection();

        handleBlockBreaking();
        handleBlockPlacing();
    }

    // ==================== 方块选择 ====================

    /**
     * 更新当前选中的方块。
     *
     * <p>使用 2D DDA 射线检测算法，从玩家位置向鼠标指向的世界位置发射射线，
     * 找到第一个非空气方块作为选中目标。</p>
     */
    private void updateTileSelection() {
        if (!mouseInPanel) {
            selectedTileX = -1;
            selectedTileY = -1;
            return;
        }

        int mouseTileX = (int) Math.floor((mouseScreenX + cameraX) / tileSize);
        int mouseTileY = (int) Math.floor((mouseScreenY + cameraY) / tileSize);

        int playerTileX = (int) (player.currentX / tileSize);
        int playerTileY = (int) (player.currentY / tileSize);

        int[] hit = raycast(playerTileX, playerTileY, mouseTileX, mouseTileY, MAX_INTERACT_DISTANCE);
        if (hit != null) {
            selectedTileX = hit[0];
            selectedTileY = hit[1];
        } else {
            selectedTileX = mouseTileX;
            selectedTileY = mouseTileY;
        }
    }

    /**
     * 2D DDA 射线检测算法。
     *
     * <p>从起点向终点发射射线，遍历经过的格子，返回第一个非空气方块的位置。</p>
     *
     * @param startX  起点 X（格子坐标）
     * @param startY  起点 Y（格子坐标）
     * @param endX    终点 X（格子坐标）
     * @param endY    终点 Y（格子坐标）
     * @param maxDist 最大检测距离（格子数）
     * @return 命中的方块坐标 [x, y]，如果未命中返回 null
     */
    private int[] raycast(int startX, int startY, int endX, int endY, int maxDist) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < 0.001) {
            return null;
        }

        double dirX = dx / dist;
        double dirY = dy / dist;

        int stepX = dirX > 0 ? 1 : -1;
        int stepY = dirY > 0 ? 1 : -1;

        double tMaxX = dirX != 0
            ? ((dirX > 0 ? Math.ceil(startX) : Math.floor(startX)) - startX) / Math.abs(dirX)
            : Double.MAX_VALUE;
        double tMaxY = dirY != 0
            ? ((dirY > 0 ? Math.ceil(startY) : Math.floor(startY)) - startY) / Math.abs(dirY)
            : Double.MAX_VALUE;

        double tDeltaX = dirX != 0 ? Math.abs(1.0 / dirX) : Double.MAX_VALUE;
        double tDeltaY = dirY != 0 ? Math.abs(1.0 / dirY) : Double.MAX_VALUE;

        int currentX = (int) Math.floor(startX);
        int currentY = (int) Math.floor(startY);

        for (int i = 0; i < maxDist * 2; i++) {
            int tileType = infiniteMap.getTileType(currentX, currentY);
            if (tileType != Chunk.AIR) {
                return new int[]{currentX, currentY};
            }

            if (tMaxX < tMaxY) {
                currentX += stepX;
                tMaxX += tDeltaX;
            } else {
                currentY += stepY;
                tMaxY += tDeltaY;
            }

            double traveled = Math.sqrt(
                (currentX - startX) * (currentX - startX) +
                (currentY - startY) * (currentY - startY)
            );
            if (traveled > maxDist) {
                break;
            }
        }

        return null;
    }

    // ==================== 方块破坏 ====================

    /**
     * 处理方块破坏逻辑。
     *
     * <p>当鼠标左键按下且冷却结束时，对选中的非空气方块执行破坏操作。</p>
     */
    private void handleBlockBreaking() {
        if (!mouseLeftPressed || breakCooldown > 0) {
            return;
        }

        int tileX = selectedTileX;
        int tileY = selectedTileY;

        if (tileX < 0 || tileY < 0) {
            return;
        }

        if (!isWithinInteractRange(tileX, tileY)) {
            return;
        }

        int tileType = infiniteMap.getTileType(tileX, tileY);
        if (tileType == Chunk.AIR) {
            return;
        }

        breakBlock(tileX, tileY, tileType);
        breakCooldown = BREAK_COOLDOWN;
    }

    /**
     * 破坏指定位置的方块并生成掉落物。
     *
     * @param tileX    世界格子 X 坐标
     * @param tileY    世界格子 Y 坐标
     * @param tileType 方块类型
     */
    public void breakBlock(int tileX, int tileY, int tileType) {
        infiniteMap.setTileType(tileX, tileY, Chunk.AIR);

        Block block = Block.fromId(tileType);
        String itemName = (block != null) ? block.getName() : "未知";
        double dropWorldX = tileX * tileSize + tileSize / 2.0;
        double dropWorldY = tileY * tileSize + tileSize / 2.0;

        // 掉落物由外部系统管理，此处仅打印日志
        System.out.println("[BlockInteraction] 破坏方块: (" + tileX + ", " + tileY
            + "), 类型: " + tileType + "，掉落: " + itemName);
    }

    // ==================== 方块放置 ====================

    /**
     * 处理方块放置逻辑。
     *
     * <p>当鼠标右键按下且冷却结束时，从背包获取当前选中物品，
     * 映射为方块类型后在目标位置放置。</p>
     */
    private void handleBlockPlacing() {
        if (!mouseRightPressed || placeCooldown > 0) {
            return;
        }

        String itemName = inventoryCallback.getSelectedItemName();
        if (itemName == null) {
            return;
        }

        Integer blockType = ITEM_TO_BLOCK.get(itemName);
        if (blockType == null) {
            return;
        }

        int tileX = selectedTileX;
        int tileY = selectedTileY;

        if (tileX < 0 || tileY < 0) {
            return;
        }

        if (!isWithinInteractRange(tileX, tileY)) {
            return;
        }

        if (!isValidPlacePosition(tileX, tileY)) {
            return;
        }

        placeBlock(tileX, tileY, blockType);
        inventoryCallback.consumeSelectedItem(1);
        placeCooldown = PLACE_COOLDOWN;
    }

    /**
     * 在指定位置放置方块。
     *
     * @param tileX    世界格子 X 坐标
     * @param tileY    世界格子 Y 坐标
     * @param tileType 方块类型
     */
    public void placeBlock(int tileX, int tileY, int tileType) {
        infiniteMap.setTileType(tileX, tileY, tileType);
        System.out.println("[BlockInteraction] 放置方块: (" + tileX + ", " + tileY
            + "), 类型: " + tileType);
    }

    // ==================== 位置验证 ====================

    /**
     * 检查目标格子是否在交互范围内。
     *
     * @param tileX 世界格子 X 坐标
     * @param tileY 世界格子 Y 坐标
     * @return 如果在范围内返回 true
     */
    public boolean isWithinInteractRange(int tileX, int tileY) {
        int playerTileX = (int) (player.currentX / tileSize);
        int playerTileY = (int) (player.currentY / tileSize);

        int dx = Math.abs(tileX - playerTileX);
        int dy = Math.abs(tileY - playerTileY);

        return dx <= MAX_INTERACT_DISTANCE && dy <= MAX_INTERACT_DISTANCE;
    }

    /**
     * 验证放置位置是否合法。
     *
     * <p>合法条件：</p>
     * <ul>
     *   <li>目标位置当前为空气</li>
     *   <li>目标位置不与玩家重叠</li>
     * </ul>
     *
     * @param tileX 世界格子 X 坐标
     * @param tileY 世界格子 Y 坐标
     * @return 如果位置合法返回 true
     */
    public boolean isValidPlacePosition(int tileX, int tileY) {
        if (infiniteMap.getTileType(tileX, tileY) != Chunk.AIR) {
            return false;
        }

        return !isOverlappingWithPlayer(tileX, tileY);
    }

    /**
     * 检查目标位置是否与玩家重叠。
     *
     * @param tileX 世界格子 X 坐标
     * @param tileY 世界格子 Y 坐标
     * @return 如果重叠返回 true
     */
    public boolean isOverlappingWithPlayer(int tileX, int tileY) {
        int playerTileX = (int) (player.currentX / tileSize);
        int playerTileY = (int) (player.currentY / tileSize);

        return tileX == playerTileX && tileY == playerTileY;
    }

    // ==================== 公共访问方法 ====================

    /**
     * 获取当前选中的方块 X 坐标（格子）。
     */
    public int getSelectedTileX() {
        return selectedTileX;
    }

    /**
     * 获取当前选中的方块 Y 坐标（格子）。
     */
    public int getSelectedTileY() {
        return selectedTileY;
    }
}
