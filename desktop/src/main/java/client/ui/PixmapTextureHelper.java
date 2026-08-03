package client.ui;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

/** 一个像素白色纹理（UI 矩形填充用）。 */
public final class PixmapTextureHelper {

    private static Texture tex;

    private PixmapTextureHelper() { }

    public static synchronized Texture tex() {
        if (tex == null) {
            Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
            pm.setColor(1, 1, 1, 1);
            pm.fillRectangle(0, 0, 1, 1);
            tex = new Texture(pm);
            pm.dispose();
        }
        return tex;
    }
}
