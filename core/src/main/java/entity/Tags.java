package entity;

/**
 * 标准实体标签常量（字符串格式，可扩展自定义标签）。
 *
 * <p>所有实体通过 {@link Entity} 的标签 API（addTag/removeTag/hasTag/getTags）
 * 增删查标签，多标签可共存。</p>
 */
public final class Tags {

    /** 掉落物实体标签（用户需求："掉落物"标签） */
    public static final String DROP_ITEM = "drop_item";

    /** 玩家实体标签 */
    public static final String PLAYER = "player";

    /** 怪物实体标签 */
    public static final String MOB = "mob";

    private Tags() {}
}
