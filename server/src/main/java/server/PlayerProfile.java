package server;

import java.util.Arrays;

/**
 * 玩家档案：每个连接对应一个，保存该玩家的世界状态。
 *
 * <p>物理/交互/背包计算在前端本地完成，服务端仅保存并同步必要状态：
 * 位置、朝向、动画帧、选中槽位、背包（45 槽）。</p>
 */
public class PlayerProfile {

    public static final int HOTBAR_SIZE = 9;
    public static final int BACKPACK_SIZE = 36;
    public static final int TOTAL_SLOTS = HOTBAR_SIZE + BACKPACK_SIZE;

    /** 出生点（像素坐标），单人存档时恢复位置 */
    private static final double DEFAULT_SPAWN_X = 100;
    private static final double DEFAULT_SPAWN_Y = 1024.0 / 2 * 32 - 32;

    /** 玩家唯一 ID（服务端分配，客户端广播用） */
    public final String playerId;

    /** 玩家显示名 */
    public volatile String name = "Player";

    /** 当前位置（像素） */
    public volatile double x = DEFAULT_SPAWN_X;
    public volatile double y = DEFAULT_SPAWN_Y;

    /** 朝向与动画 */
    public volatile String direction = "null";
    public volatile int animFrame = 1;

    /** 是否在地面（前端上报，供其他客户端渲染） */
    public volatile boolean onGround = true;

    /** 当前选中快捷栏槽位（0-8） */
    public volatile int slot = 0;

    /** 背包 45 槽：每槽 "name|count" 或 ""（空槽） */
    private final String[] slots = new String[TOTAL_SLOTS];

    /** 出生位置（用于无存档时的复位） */
    public final double spawnX = DEFAULT_SPAWN_X;
    public final double spawnY = DEFAULT_SPAWN_Y;

    public PlayerProfile(String playerId) {
        this.playerId = playerId;
        Arrays.fill(slots, "");
    }

    /** 设置位置（同步 x/y/出生点） */
    public void setPosition(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /** 返回背包槽位数组的拷贝（网络序列化用） */
    public String[] getSlotsCopy() {
        return slots.clone();
    }

    /** 用客户端上报的背包整体覆盖本地（前端为权威计算方） */
    public void setSlots(String[] newSlots) {
        if (newSlots == null) return;
        int n = Math.min(newSlots.length, slots.length);
        for (int i = 0; i < n; i++) {
            slots[i] = newSlots[i] == null ? "" : newSlots[i];
        }
    }

    /** 获取某槽位内容 */
    public String getSlot(int index) {
        if (index < 0 || index >= slots.length) return "";
        return slots[index];
    }

    /** 初始化默认背包：快捷栏给 5 种基础方块各 64 个 + 1 个空桶（流体系统） */
    public void initDefaultInventory() {
        String[] defaults = {"grass_block|64", "dirt|64", "stone|64", "sand|64", "oak_log|64", "bucket|1"};
        for (int i = 0; i < defaults.length && i < HOTBAR_SIZE; i++) {
            slots[i] = defaults[i];
        }
    }

    /** 检查是否已初始化过背包（非全部空槽） */
    public boolean hasInventory() {
        for (String s : slots) {
            if (s != null && !s.isEmpty()) return true;
        }
        return false;
    }

    /** 玩家档案持久化行（供 player_<id>.txt） */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(x).append('|').append(y).append('\n');
        sb.append(name).append('\n');
        for (String s : slots) {
            sb.append(s == null ? "" : s).append('\n');
        }
        return sb.toString();
    }

    /** 从持久化行恢复玩家档案（返回 null 表示解析失败） */
    public static PlayerProfile deserialize(String playerId, String content) {
        String[] lines = content.split("\n");
        if (lines.length < 2) return null;
        PlayerProfile p = new PlayerProfile(playerId);
        try {
            String[] coords = lines[0].split("\\|");
            p.x = Double.parseDouble(coords[0]);
            p.y = Double.parseDouble(coords[1]);
            p.name = lines[1];
            int start = 2;
            for (int i = 0; i < TOTAL_SLOTS && start + i < lines.length; i++) {
                p.slots[i] = migrateItemName(lines[start + i]);
            }
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    /** 旧版物品名 -> Minecraft 原版物品名（旧存档迁移）。 */
    private static String migrateItemName(String slot) {
        if (slot == null || slot.isEmpty()) return slot;
        int idx = slot.indexOf('|');
        String name = idx >= 0 ? slot.substring(0, idx) : slot;
        String count = idx >= 0 ? slot.substring(idx) : "";
        switch (name) {
            case "Grass":  return "grass_block" + count;
            case "Dirt":   return "dirt" + count;
            case "Stone":  return "stone" + count;
            case "Sand":   return "sand" + count;
            case "Wood":   return "oak_log" + count;
            case "Leaves": return "oak_leaves" + count;
            case "Water":  return "water" + count;
            default:       return slot;
        }
    }
}
