package client.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

/**
 * 武器/工具贴图加载器（src/main/resources/axe/*.png，32x32）。
 * 资源解析：classpath -> assets/ -> 工作目录（desktop/assets、src/main/resources）兜底，
 * 与 PlayerTextures 的 asset() 规则一致，只是额外支持源码资源目录。
 */
public class ToolTextures {

    private final Map<String, Texture> cache = new HashMap<>();

    private static FileHandle asset(String path) {
        if (Gdx.files.internal(path).exists()) return Gdx.files.internal(path);
        if (Gdx.files.internal("assets/" + path).exists()) return Gdx.files.internal("assets/" + path);
        for (String base : new String[]{"desktop/assets/", "assets/", "src/main/resources/"}) {
            FileHandle f = Gdx.files.absolute(base + path);
            if (f.exists()) return f;
        }
        return Gdx.files.internal(path);
    }

    /** 按物品名取贴图（sword/gun/pickaxe/axe）；不存在返回 null */
    public Texture get(String name) {
        if (name == null) return null;
        Texture t = cache.get(name);
        if (t != null) return t;
        FileHandle f = asset("axe/" + name + ".png");
        if (!f.exists()) return null;
        t = new Texture(f);
        cache.put(name, t);
        return t;
    }

    public void dispose() {
        for (Texture t : cache.values()) t.dispose();
        cache.clear();
    }
}
