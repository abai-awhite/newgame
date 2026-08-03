package client.world;

import java.util.HashMap;
import java.util.Map;

/**
 * 客户端区块缓存（对应 game.js state.chunks）。
 * 服务器权威下发区块（Base64，每字节一个 tile，行优先 localY*16+localX）；
 * 流体水位同样以每字节一格（0=满格 ~ 15=最薄，一格最多 16 级）的独立数组下发（"lv" 字段）。
 */
public class ClientWorld {

    public static final int CHUNK_SIZE = 16;
    public static final int WORLD_HEIGHT_TILES = 1024;

    /** "cx,cy" -> byte[256] */
    private final Map<String, byte[]> chunks = new HashMap<>();
    /** "cx,cy" -> byte[256]（流体水位，0=满格源，1~7 流动） */
    private final Map<String, byte[]> levels = new HashMap<>();

    public int chunkCount() {
        return chunks.size();
    }

    public void clear() {
        chunks.clear();
        levels.clear();
    }

    public void putChunk(int cx, int cy, byte[] data) {
        putChunk(cx, cy, data, null);
    }

    public void putChunk(int cx, int cy, byte[] data, byte[] lv) {
        chunks.put(cx + "," + cy, data);
        if (lv == null) {
            levels.remove(cx + "," + cy);
        } else {
            levels.put(cx + "," + cy, lv);
        }
    }

    /** 取方块类型（越界或未加载返回空气）。 */
    public int getTile(int tx, int ty) {
        if (ty < 0 || ty >= WORLD_HEIGHT_TILES) return 0;
        int cx = Math.floorDiv(tx, CHUNK_SIZE);
        int cy = Math.floorDiv(ty, CHUNK_SIZE);
        byte[] data = chunks.get(cx + "," + cy);
        if (data == null) return 0;
        int lx = Math.floorMod(tx, CHUNK_SIZE);
        int ly = Math.floorMod(ty, CHUNK_SIZE);
        return data[ly * CHUNK_SIZE + lx] & 0xFF;
    }

    /** 取流体水位（越界/未加载/无 level 数据返回 0 = 满格）。 */
    public int getLevel(int tx, int ty) {
        if (ty < 0 || ty >= WORLD_HEIGHT_TILES) return 0;
        int cx = Math.floorDiv(tx, CHUNK_SIZE);
        int cy = Math.floorDiv(ty, CHUNK_SIZE);
        byte[] data = levels.get(cx + "," + cy);
        if (data == null) return 0;
        int lx = Math.floorMod(tx, CHUNK_SIZE);
        int ly = Math.floorMod(ty, CHUNK_SIZE);
        return data[ly * CHUNK_SIZE + lx] & 0xFF;
    }

    /** 应用服务器下发的方块变更到本地缓存（对应 applyRemoteTile）。 */
    public void applyRemoteTile(int tx, int ty, int type) {
        applyRemoteTile(tx, ty, type, 0);
    }

    /** 应用服务器下发的方块变更（含流体水位；非液体强制水位 0）。 */
    public void applyRemoteTile(int tx, int ty, int type, int lv) {
        int cx = Math.floorDiv(tx, CHUNK_SIZE);
        int cy = Math.floorDiv(ty, CHUNK_SIZE);
        String key = cx + "," + cy;
        byte[] data = chunks.get(key);
        if (data == null) return;
        int lx = Math.floorMod(tx, CHUNK_SIZE);
        int ly = Math.floorMod(ty, CHUNK_SIZE);
        data[ly * CHUNK_SIZE + lx] = (byte) type;
        if (type == 32 || type == 33) {
            byte[] lvs = levels.get(key);
            if (lvs == null) {
                lvs = new byte[CHUNK_SIZE * CHUNK_SIZE];
                levels.put(key, lvs);
            }
            lvs[ly * CHUNK_SIZE + lx] = (byte) lv;
        } else {
            byte[] lvs = levels.get(key);
            if (lvs != null) lvs[ly * CHUNK_SIZE + lx] = 0;
        }
    }

    /** 本地即时变更（破坏/放置），区块不存在时创建空白区块。 */
    public void setLocalTile(int tx, int ty, int type) {
        setLocalTile(tx, ty, type, 0);
    }

    /** 本地即时变更（含流体水位；非液体强制水位 0）。 */
    public void setLocalTile(int tx, int ty, int type, int lv) {
        int cx = Math.floorDiv(tx, CHUNK_SIZE);
        int cy = Math.floorDiv(ty, CHUNK_SIZE);
        String key = cx + "," + cy;
        byte[] data = chunks.get(key);
        if (data == null) {
            data = new byte[CHUNK_SIZE * CHUNK_SIZE];
            chunks.put(key, data);
        }
        int lx = Math.floorMod(tx, CHUNK_SIZE);
        int ly = Math.floorMod(ty, CHUNK_SIZE);
        data[ly * CHUNK_SIZE + lx] = (byte) type;
        if (type == 32 || type == 33) {
            byte[] lvs = levels.get(key);
            if (lvs == null) {
                lvs = new byte[CHUNK_SIZE * CHUNK_SIZE];
                levels.put(key, lvs);
            }
            lvs[ly * CHUNK_SIZE + lx] = (byte) lv;
        } else {
            byte[] lvs = levels.get(key);
            if (lvs != null) lvs[ly * CHUNK_SIZE + lx] = 0;
        }
    }
}
