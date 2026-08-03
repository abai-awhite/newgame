package server.mod;

/**
 * 模组事件基类（骨架）：事件数据载体。
 *
 * <p>具体事件类（方块破坏/放置、玩家加入/离开、tick、存档前后）后期继承本类添加数据字段。</p>
 */
public abstract class ModEvent {

    private final String type;

    protected ModEvent(String type) {
        this.type = type;
    }

    /** 事件类型标识。 */
    public String getType() {
        return type;
    }
}
