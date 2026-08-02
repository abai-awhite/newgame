package block;

/**
 * 方块抽象基类，使用注册表模式管理所有方块类型。
 */
public abstract class Block {

    private static final java.util.Map<Integer, Block> REGISTRY = new java.util.HashMap<>();

    protected final int id;
    protected final String name;
    protected final String texturePath;
    protected boolean solid;

    protected Block(int id, String name, String texturePath, boolean solid) {
        this.id = id;
        this.name = name;
        this.texturePath = texturePath;
        this.solid = solid;
        REGISTRY.put(id, this);
    }

    public String getTexturePath() {
        return texturePath;
    }

    public static Block fromId(int id) {
        return REGISTRY.get(id);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public boolean isSolid() { return solid; }

    public static void init() {
        try {
            Class.forName("block.Grass");
            Class.forName("block.Dirt");
            Class.forName("block.Stone");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
