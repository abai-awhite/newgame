package client.data;

import com.badlogic.gdx.files.FileHandle;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * 方块/物品数据（由 blocks_data.js 加载，Minecraft 1.21.1 全量方块）。
 * 对应 game.js 的 TILE_META / ITEM_TILE / TILE_ITEM。
 */
public class BlocksData {

    public static final int T_AIR = 0;

    /** 方块 ID -> 元数据 */
    public final Map<Integer, BlockMeta> byId = new HashMap<>();
    /** 物品名 -> 方块 ID（放置用） */
    public final Map<String, Integer> itemTile = new HashMap<>();
    /** 方块 ID -> 物品名 */
    public final Map<Integer, String> tileItem = new HashMap<>();

    /** 从 blocks_data.js 文本加载（剥离 window.BLOCKS_DATA = 包装后解析 JSON）。 */
    public static BlocksData load(FileHandle fh) {
        String text = fh.readString("UTF-8");
        int i = text.indexOf('{');
        int j = text.lastIndexOf('}');
        if (i < 0 || j <= i) throw new IllegalStateException("blocks_data.js 格式异常");
        JSONObject root = new JSONObject(text.substring(i, j + 1));
        BlocksData bd = new BlocksData();
        for (String key : root.keySet()) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            JSONObject b = root.getJSONObject(key);
            String n = b.optString("n", "");
            BlockMeta m = new BlockMeta(
                    id,
                    n,
                    b.optString("d", n),
                    b.optBoolean("s", false),
                    b.optBoolean("tr", false),
                    b.optInt("st", 64),
                    (float) b.optDouble("h", 0),
                    (b.has("dr") && !b.isNull("dr")) ? b.optString("dr", null) : null,
                    (b.has("t") && !b.isNull("t")) ? b.optString("t", null) : null);
            bd.byId.put(id, m);
            if (!n.isEmpty() && !n.equals("air") && !n.equals("void_air") && !n.equals("cave_air")) {
                bd.itemTile.put(n, id);
                bd.tileItem.put(id, n);
            }
        }
        return bd;
    }

    public BlockMeta meta(int id) {
        return byId.get(id);
    }

    public boolean isSolid(int id) {
        BlockMeta m = byId.get(id);
        return m != null && m.solid;
    }

    public BlockMeta metaByName(String name) {
        Integer id = itemTile.get(name);
        return id == null ? null : byId.get(id);
    }

    /** 物品名 -> 方块 ID；非方块物品返回 -1。 */
    public int tileId(String name) {
        Integer id = itemTile.get(name);
        return id == null ? -1 : id;
    }
}
