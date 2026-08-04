package client.tool;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具/武器抽象类：统一描述一个工具的身份（物品 ID）、旋转中心与工作方式。
 *
 * <p>旋转中心：贴图坐标（pivotFx = 左起比例 0~1，pivotFy = 底部起比例 0~1），
 * 挥砍/射击时该像素固定在玩家中心并作为旋转轴。</p>
 *
 * <p>工作方式：actionType（"swing" 挥砍 / "shoot" 射击）+ breaksBlocks（是否可破坏方块）。
 * 剑/枪为武器（不破坏方块，左键只做攻击），镐/斧为工具（挥砍的同时可挖方块）。</p>
 */
public abstract class Tool {

    /** 物品 ID（与物品名一致，如 "sword"） */
    public final String id;

    /** 旋转中心：贴图左起比例 0~1 */
    public final float pivotFx;

    /** 旋转中心：贴图底部起比例 0~1 */
    public final float pivotFy;

    /** 工作方式："swing"（挥砍）/ "shoot"（射击） */
    public final String actionType;

    /** 是否可破坏方块 */
    public final boolean breaksBlocks;

    /** 是否始终跟随鼠标角度（360° 指向鼠标）。
     * 普通近战工具=false：仅在左右两个方向挥砍并镜像；枪=true：枪口直接指向鼠标。 */
    public final boolean aimFollowsMouse;

    protected Tool(String id, float pivotFx, float pivotFy, String actionType,
                   boolean breaksBlocks, boolean aimFollowsMouse) {
        this.id = id;
        this.pivotFx = pivotFx;
        this.pivotFy = pivotFy;
        this.actionType = actionType;
        this.breaksBlocks = breaksBlocks;
        this.aimFollowsMouse = aimFollowsMouse;
    }

    // ==================== 注册表 ====================

    private static final Map<String, Tool> REGISTRY = new HashMap<>();

    static {
        register(new SwordTool());
        register(new GunTool());
        register(new PickaxeTool());
        register(new AxeTool());
    }

    private static void register(Tool t) {
        REGISTRY.put(t.id, t);
    }

    /** 按物品 ID 查找工具；非工具返回 null */
    public static Tool byId(String id) {
        return id == null ? null : REGISTRY.get(id);
    }
}
