package client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * 玩家贴图（assets/player/*.png，32x32）。
 * 方向映射与原版 Player.java 一致：up/down/right/left + 站立动画帧。
 */
public class PlayerTextures {

    private final Map<String, Texture> cache = new HashMap<>();

    /** 资源解析：classpath -> assets/ -> 工作目录相对/绝对路径 兜底 */
    private static FileHandle asset(String path) {
        if (Gdx.files.internal(path).exists()) return Gdx.files.internal(path);
        if (Gdx.files.internal("assets/" + path).exists()) return Gdx.files.internal("assets/" + path);
        for (String base : new String[]{"desktop/assets/", "assets/"}) {
            FileHandle f = Gdx.files.absolute(base + path);
            if (f.exists()) return f;
        }
        return Gdx.files.internal(path);
    }

    /** 取贴图：direction + animFrame -> Texture（映射规则同 game.js paintPlayer） */
    public Texture get(String direction, int animFrame) {
        String key = (direction == null ? "null" : direction) + "_" + animFrame;
        Texture t = cache.get(key);
        if (t != null) return t;
        String file;
        if ("up".equals(direction)) file = "player/player-up-1.png";
        else if ("down".equals(direction)) file = "player/player-down-1.png";
        else if ("right".equals(direction)) file = "player/player-r-1.png";
        else if ("left".equals(direction)) file = "player/player-l-1.png";
        else {
            int f = (animFrame == 2 || animFrame == 4) ? 2 : (animFrame == 3 ? 3 : 1);
            file = "player/player-" + f + ".png";
        }
        t = new Texture(asset(file));
        cache.put(key, t);
        return t;
    }

    public void dispose() {
        for (Texture t : cache.values()) t.dispose();
        cache.clear();
    }
}
