package client.tool;

/** 斧：旋转中心 = 左下角 (0, 0)，工作方式 = 挥砍，可破坏方块。
 * 镜像逻辑（朝左挥砍时翻转贴图保持刀面朝向一致）由基类 Tool 统一提供。 */
public class AxeTool extends Tool {

    public AxeTool() {
        super("axe", 0f, 0f, "swing", true, false);
    }
}
