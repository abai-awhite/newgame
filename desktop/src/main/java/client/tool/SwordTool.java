package client.tool;

/** 剑：旋转中心 = 左下角 (0, 0)，工作方式 = 挥砍，不破坏方块 */
public class SwordTool extends Tool {

    public SwordTool() {
        super("sword", 0f, 0f, "swing", false, false);
    }
}
