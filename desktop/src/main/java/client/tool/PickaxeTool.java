package client.tool;

/** 镐：旋转中心 = 左下角 (0, 0)，工作方式 = 挥砍，可破坏方块 */
public class PickaxeTool extends Tool {

    public PickaxeTool() {
        super("pickaxe", 0f, 0f, "swing", true, false);
    }
}
