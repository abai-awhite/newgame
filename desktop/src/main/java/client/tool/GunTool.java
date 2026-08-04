package client.tool;

/** 枪：旋转中心 = 左下角 (0, 0)，工作方式 = 射击，不破坏方块 */
public class GunTool extends Tool {

    public GunTool() {
        super("gun", 0f, 0f, "shoot", false, false);
    }
}
