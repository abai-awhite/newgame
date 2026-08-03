package client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.math.Rectangle;

import client.data.ZhName;

import java.util.ArrayList;
import java.util.List;

/**
 * UI 工具：中文字体（系统黑体 FreeType）、按钮、文本框、绘制辅助。
 * 坐标约定：全部使用屏幕像素坐标，y 向下（左上角为原点）。
 */
public class UiKit {

    /** 双字体（拉丁 + 中文），混排渲染。 */
    public static class UiFont {
        public final BitmapFont latin;  // Comic Sans MS（英文/数字）
        public final BitmapFont cjk;    // 潮小社小作文简体（中文）

        public UiFont(BitmapFont latin, BitmapFont cjk) {
            this.latin = latin;
            this.cjk = cjk;
        }
    }

    public static UiFont fontSmall;   // 12
    public static UiFont fontNormal;  // 16
    public static UiFont fontTitle;   // 28
    public static UiFont fontBig;     // 40

    /** 拉丁字体补充字符（默认字符集之外） */
    private static final String LATIN_EXTRA = "×→←↑↓…—°§№·";

    /** UI 文案中文字符（FreeType 预生成字形用，配合 ZhName.allChars() 覆盖全部中文）。 */
    private static final String UI_CHARS =
            "向前移动向左移动向后移动跳跃调试界面背包暂停菜单冲刺" +
            "已连接服务器连接断开，秒后重连...区块解码失败操作失败正在连接未连接世界已保存请输入服务器地址" +
            "位置朝向地面相机区块缓存掉落物天气在线断开其他玩家" +
            "上盔甲腰带下盔甲硬度堆叠不可破坏实体非实体透明不透明掉落非方块物品" +
            "暂停世界哈希继续游戏保存设置退出游戏2D沙盒游戏" +
            "单人游戏多人游戏连接返回选择世界暂无存档，请创建新世界（当前）删除" +
            "世界名称种子可修改将派生哈希随机创建新世界" +
            "按键设置自动跨步游戏设置恢复默认前方一格高方块直接走上开启关闭" +
            "自动选择鼠标指向空气时吸附附近方块天气雨天雷雨覆盖启用如空格左右中文字体加载失败";


    private static final GlyphLayout layout = new GlyphLayout();

    /** 全局透明度（ESC 淡入动画用），1 = 不透明 */
    public static float globalAlpha = 1f;

    /** UI 圆角半径（像素） */
    public static final float UI_RADIUS = 8f;

    private static Texture circleTex;   // 白色实心圆（圆角填充用）
    private static Texture ringTex;     // 白色圆角边框环（9-patch）
    private static NinePatch ringPatch;

    private UiKit() { }

    /** 加载字体：英文/数字用 Comic Sans MS，中文用潮小社小作文简体。
     *  预生成字形（不用 incremental：JDK25 下 gdx-freetype 原生崩溃）。 */
    public static void loadFonts() {
        String allChars = ZhName.allChars() + UI_CHARS;
        fontSmall = genPair(12, allChars);
        fontNormal = genPair(16, allChars);
        fontTitle = genPair(28, allChars);
        fontBig = genPair(40, allChars);
    }

    private static UiFont genPair(int size, String cjkChars) {
        BitmapFont latin = genFont(resolveFont("C:\\Windows\\Fonts\\comic.ttf"),
                FreeTypeFontGenerator.DEFAULT_CHARS + LATIN_EXTRA, size);
        BitmapFont cjk = genFont(resolveFont("fonts/chaoxiaoshe.ttf"),
                FreeTypeFontGenerator.DEFAULT_CHARS + cjkChars, size);
        return new UiFont(latin, cjk);
    }

    private static BitmapFont genFont(FileHandle file, String chars, int size) {
        FreeTypeFontGenerator gen = new FreeTypeFontGenerator(file);
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.characters = chars;
        p.size = size;
        p.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest;
        p.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest;
        BitmapFont f = gen.generateFont(p);
        gen.dispose();
        return f;
    }

    /** 字体文件解析：classpath -> assets/ -> 工作目录相对路径 兜底 */
    private static FileHandle resolveFont(String path) {
        if (Gdx.files.internal(path).exists()) return Gdx.files.internal(path);
        if (Gdx.files.internal("assets/" + path).exists()) return Gdx.files.internal("assets/" + path);
        for (String base : new String[]{"desktop/assets/", "assets/"}) {
            FileHandle f = Gdx.files.absolute(base + path);
            if (f.exists()) return f;
        }
        return Gdx.files.internal(path);
    }

    public static void disposeFonts() {
        dispose(fontSmall);
        dispose(fontNormal);
        dispose(fontTitle);
        dispose(fontBig);
    }

    private static void dispose(UiFont f) {
        if (f == null) return;
        if (f.latin != null) f.latin.dispose();
        if (f.cjk != null) f.cjk.dispose();
    }

    /** y 向下转 libGDX y 向上 */
    public static float up(int vh, float y) {
        return vh - y;
    }

    // ==================== 绘制辅助 ====================

    /** 屏幕坐标（y 向下）绘制实心矩形（默认圆角） */
    public static void rect(SpriteBatch batch, int vh, float x, float y, float w, float h, Color c) {
        rectR(batch, vh, x, y, w, h, c, UI_RADIUS);
    }

    /** 屏幕坐标绘制指定圆角半径的实心矩形；radius<=0 时纯直角 */
    public static void rectR(SpriteBatch batch, int vh, float x, float y, float w, float h, Color c, float radius) {
        if (radius <= 0.5f) {
            rectPlain(batch, vh, x, y, w, h, c);
            return;
        }
        float r = Math.min(radius, Math.min(w, h) / 2f);
        ensureShapes();
        // 中央十字（两段直角矩形，避免重复圆角）
        rectPlain(batch, vh, x + r, y, w - 2 * r, h, c);
        rectPlain(batch, vh, x, y + r, w, h - 2 * r, c);
        // 四角用实心圆补齐（圆半径 r，超出部分与矩形同色，覆盖无害）
        float cs = 2 * r;
        batch.setColor(eff(c));
        batch.draw(circleTex, x, up(vh, y + cs), cs, cs);
        batch.draw(circleTex, x + w - cs, up(vh, y + cs), cs, cs);
        batch.draw(circleTex, x, up(vh, y + h), cs, cs);
        batch.draw(circleTex, x + w - cs, up(vh, y + h), cs, cs);
        batch.setColor(Color.WHITE);
    }

    /** 直角实心矩形 */
    private static void rectPlain(SpriteBatch batch, int vh, float x, float y, float w, float h, Color c) {
        if (w <= 0 || h <= 0) return;
        batch.setColor(eff(c));
        batch.draw(PixmapTextureHelper.tex(), x, up(vh, y + h), w, h);
        batch.setColor(Color.WHITE);
    }

    /** 屏幕坐标绘制圆角边框 */
    public static void frame(SpriteBatch batch, int vh, float x, float y, float w, float h, float thickness, Color c) {
        frameR(batch, vh, x, y, w, h, c, UI_RADIUS);
    }

    /** 屏幕坐标绘制指定圆角半径的边框；radius<=0 时纯直角 */
    public static void frameR(SpriteBatch batch, int vh, float x, float y, float w, float h, Color c, float radius) {
        if (radius <= 0.5f) {
            rectPlain(batch, vh, x, y, w, 2, c);
            rectPlain(batch, vh, x, y + h - 2, w, 2, c);
            rectPlain(batch, vh, x, y, 2, h, c);
            rectPlain(batch, vh, x + w - 2, y, 2, h, c);
            return;
        }
        ensureShapes();
        batch.setColor(eff(c));
        ringPatch.draw(batch, x, up(vh, y + h), w, h);
        batch.setColor(Color.WHITE);
    }

    /** 全局透明度作用于颜色（globalAlpha>=1 时原样返回，避免分配） */
    private static Color eff(Color c) {
        return globalAlpha >= 1f ? c : new Color(c.r, c.g, c.b, c.a * globalAlpha);
    }

    private static void ensureShapes() {
        if (circleTex == null) {
            Pixmap pm = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
            pm.setColor(1, 1, 1, 1);
            pm.fillCircle(32, 32, 32);
            circleTex = new Texture(pm);
            circleTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            pm.dispose();
            tempTextures.add(circleTex);
        }
        if (ringTex == null) {
            Pixmap pm = ringPixmap();
            ringTex = new Texture(pm);
            ringTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            ringPatch = new NinePatch(ringTex, 8, 8, 8, 8);
            pm.dispose();
            tempTextures.add(ringTex);
        }
    }

    /** 圆角边框环（24×24，圆角半径 8，线宽 2） */
    private static Pixmap ringPixmap() {
        Pixmap pm = new Pixmap(24, 24, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        fillRound(pm, 0, 0, 24, 24, 8);
        // 镂空内部（Blending.None 直接覆盖为透明）
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        fillRound(pm, 2, 2, 20, 20, 6);
        pm.setBlending(Pixmap.Blending.SourceOver);
        return pm;
    }

    private static void fillRound(Pixmap pm, int x, int y, int w, int h, int r) {
        pm.fillRectangle(x + r, y, w - 2 * r, h);
        pm.fillRectangle(x, y + r, w, h - 2 * r);
        pm.fillCircle(x + r, y + r, r);
        pm.fillCircle(x + w - r, y + r, r);
        pm.fillCircle(x + r, y + h - r, r);
        pm.fillCircle(x + w - r, y + h - r, r);
    }

    /** 居中绘制文字，返回文字宽 */
    public static float text(SpriteBatch batch, int vh, UiFont f, String s, float cx, float y, Color c) {
        float w = textWidth(f, s);
        drawMixed(batch, vh, f, s, cx - w / 2, y, c);
        return w;
    }

    /** 左对齐绘制文字 */
    public static float textLeft(SpriteBatch batch, int vh, UiFont f, String s, float x, float y, Color c) {
        drawMixed(batch, vh, f, s, x, y, c);
        return 0;
    }

    /** 测量文字宽度 */
    public static float textWidth(UiFont f, String s) {
        if (s == null || s.isEmpty() || f == null) return 0;
        float w = 0;
        int n = s.length();
        int i = 0;
        while (i < n) {
            int j = i;
            BitmapFont cur = pickFont(f, s.charAt(i));
            while (j < n && pickFont(f, s.charAt(j)) == cur) j++;
            layout.setText(cur, s.substring(i, j));
            w += layout.width;
            i = j;
        }
        return w;
    }

    /** 混排绘制：按字符选择字体（拉丁优先，缺失时用中文），逐段绘制，共享基线 */
    private static void drawMixed(SpriteBatch batch, int vh, UiFont f, String s, float x, float y, Color c) {
        if (s == null || s.isEmpty() || f == null) return;
        float baseline = up(vh, y) + Math.max(f.latin.getCapHeight(), f.cjk.getCapHeight()) / 2f;
        int n = s.length();
        int i = 0;
        while (i < n) {
            int j = i;
            BitmapFont cur = pickFont(f, s.charAt(i));
            while (j < n && pickFont(f, s.charAt(j)) == cur) j++;
            layout.setText(cur, s.substring(i, j));
            float runW = layout.width;
            cur.setColor(eff(c));
            cur.draw(batch, s.substring(i, j), x, baseline);
            cur.setColor(Color.WHITE);
            x += runW;
            i = j;
        }
    }

    /** 选字体：拉丁含该字形用拉丁，否则用中文 */
    private static BitmapFont pickFont(UiFont f, char c) {
        return f.latin.getData().getGlyph(c) != null ? f.latin : f.cjk;
    }

    // ==================== 按钮 ====================

    public static class Button {
        public final float x, y, w, h;
        public final String label;
        public final Color bg = new Color(0.16f, 0.16f, 0.24f, 0.92f);
        public final Color hover = new Color(0.30f, 0.30f, 0.42f, 0.95f);
        public boolean visible = true;
        private boolean hovered;

        public Button(float x, float y, float w, float h, String label) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.label = label;
        }

        public boolean hit(float mx, float my) {
            return visible && mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        public void draw(SpriteBatch batch, int vh) {
            if (!visible) return;
            rect(batch, vh, x, y, w, h, hovered ? hover : bg);
            frame(batch, vh, x, y, w, h, 2, new Color(1, 1, 1, 0.45f));
            text(batch, vh, fontNormal, label, x + w / 2, y + (h - 16) / 2, Color.WHITE);
        }

        public void updateHover(float mx, float my) {
            hovered = visible && hit(mx, my);
        }
    }

    // ==================== 文本框 ====================

    public static class TextField {
        public final Rectangle bounds;
        public String text = "";
        public boolean focused;
        public String placeholder = "";

        public TextField(float x, float y, float w, float h) {
            this.bounds = new Rectangle(x, y, w, h);
        }

        public boolean hit(float mx, float my) {
            return bounds.contains(mx, my);
        }

        public void draw(SpriteBatch batch, int vh) {
            rect(batch, vh, bounds.x, bounds.y, bounds.width, bounds.height,
                    focused ? new Color(0.35f, 0.35f, 0.5f, 0.95f) : new Color(0.15f, 0.15f, 0.22f, 0.9f));
            frame(batch, vh, bounds.x, bounds.y, bounds.width, bounds.height, 2,
                    focused ? new Color(1, 1, 1, 0.9f) : new Color(1, 1, 1, 0.35f));
            String show = text.isEmpty() ? placeholder : text;
            textLeft(batch, vh, fontNormal, show, bounds.x + 8,
                    bounds.y + (bounds.height - 16) / 2, text.isEmpty() ? new Color(0.6f, 0.6f, 0.6f, 1) : Color.WHITE);
        }

        public void type(char c) {
            if (Character.isISOControl(c)) return;
            text += c;
        }

        public void backspace() {
            if (!text.isEmpty()) text = text.substring(0, text.length() - 1);
        }
    }

    // ==================== 一个像素白图（PixmapTextureHelper） ====================

    private static final List<com.badlogic.gdx.graphics.Texture> tempTextures = new ArrayList<>();

    public static com.badlogic.gdx.graphics.Texture whiteTex() {
        return PixmapTextureHelper.tex();
    }

    /** 释放所有临时资源 */
    public static void disposeAll() {
        for (com.badlogic.gdx.graphics.Texture t : tempTextures) t.dispose();
        tempTextures.clear();
    }
}
