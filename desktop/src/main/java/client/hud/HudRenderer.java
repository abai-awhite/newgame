package client.hud;

import client.GdxGame;
import client.GdxGame.ItemSlot;
import client.data.BlockMeta;
import client.data.BlocksData;
import client.data.ZhName;
import client.render.TextureFactory;
import client.render.ToolTextures;
import client.tool.Tool;
import client.ui.UiKit;
import client.world.LocalPlayer;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * HUD 渲染类：集中管理所有游戏内界面元素的绘制。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>世界层（跟随玩家）：菱形（冲刺）、蓝条/饱食度竖条、本机头顶血条</li>
 *   <li>UI 层（固定屏幕）：状态条（血条/蓝条/饱食度）、快捷栏、快捷栏切换提示、背包面板</li>
 * </ul>
 *
 * <p>坐标约定：世界坐标 y 向下；屏幕坐标 y 向下（左上角原点），绘制时用 UiKit.up() 翻转。</p>
 */
public class HudRenderer {

    /** 虚拟分辨率：HUD 绘制基准（与 GdxGame 一致） */
    private static final float VIEW_W = 1280f, VIEW_H = 720f;
    /** 格子像素尺寸（世界层跟随玩家元素用） */
    private static final float TILE = 32f;

    private final BlocksData blocks;
    private final TextureFactory texFactory;
    /** 武器/工具贴图（sword/gun/pickaxe/axe） */
    private final ToolTextures toolTex;

    // 背包开启动画状态
    private float invAnimT;
    private float invSlide;

    // 快捷栏切换提示
    private String slotToastText;
    private long slotToastUntil;

    public HudRenderer(BlocksData blocks, TextureFactory texFactory, ToolTextures toolTex) {
        this.blocks = blocks;
        this.texFactory = texFactory;
        this.toolTex = toolTex;
    }

    // ==================== 外部状态接口 ====================

    /** 打开背包时重置滑入动画 */
    public void openInventory() {
        invAnimT = 0;
    }

    /** 设置快捷栏切换提示（5 秒） */
    public void setSlotToast(String text) {
        slotToastText = text;
        slotToastUntil = System.currentTimeMillis() + 5000;
    }

    // ==================== 世界层（跟随玩家） ====================

    /**
     * 绘制本机玩家周围 HUD 元素（菱形/蓝条/饱食度竖条/头顶血条）。
     * 使用物理窗口坐标（winW/winH），与 WorldRenderer 同一投影。
     */
    public void drawWorld(SpriteBatch batch, int winW, int winH, float camX, float camY,
                          LocalPlayer player, boolean localHeadHp, int manaBarPos, int hungerBarPos) {
        if (player == null) return;
        float sx = player.renderX - camX;
        float sy = player.renderY - camY;
        float barH = 4;                       // 头顶血条高
        float barTop = sy - barH - 1;         // 血条顶：贴头 1px
        if (localHeadHp) drawHealthBar(batch, winH, sx, sy, player.hp, player.maxHp);
        // 蓝条/饱食竖条：高 = 头(TILE) + 血条(barH) + 间隔(1)，从血条顶延伸到玩家脚底，贴玩家 1px
        float vH = TILE + barH + 1;
        if (manaBarPos == 0) {
            drawVerticalBar(batch, winH, sx + TILE + 1, barTop, vH,
                    (float) player.mana / player.maxMana, new Color(0.3f, 0.6f, 0.95f, 1));
        }
        if (hungerBarPos == 0) {
            drawVerticalBar(batch, winH, sx - 1 - 5, barTop, vH,
                    (float) player.hunger / player.maxHunger, new Color(0.95f, 0.72f, 0.3f, 1));
        }
        if (player.dashGemsVisible) drawDashGems(batch, winH, sx, sy, player);
    }

    /** 玩家头顶血条（sx/sy 为玩家左上角，屏幕坐标 y 向下） */
    private void drawHealthBar(SpriteBatch batch, int vh, float sx, float sy, int hp, int maxHp) {
        float bw = TILE;                       // 血条宽 = 一个格子
        float bh = 4;                          // 高 4px
        float bx = sx + (TILE - bw) / 2f;
        float by = sy - bh - 1;                // 玩家头顶紧贴 1px
        UiKit.rectR(batch, vh, bx, by, bw, bh, new Color(0, 0, 0, 0.6f), 1);   // 背景
        float ratio = maxHp > 0 ? Math.max(0, Math.min(1, (float) hp / maxHp)) : 0;
        if (ratio > 0) {
            UiKit.rectR(batch, vh, bx, by, bw * ratio, bh, new Color(0.95f, 0.2f, 0.15f, 1), 1);  // 红色
        }
    }

    /** 玩家正下方的冲刺菱形：实心=可用，空心=已消耗（sx/sy 为玩家左上角，屏幕坐标 y 向下） */
    private void drawDashGems(SpriteBatch batch, int vh, float sx, float sy, LocalPlayer p) {
        int total = Math.max(1, p.dashMax);
        float size = 7, thick = 1.5f, gap = 4;
        float centerX = sx + TILE / 2f;
        float cyCenter = sy + TILE + 1 + size / 2f;      // 菱形顶角紧贴脚底下方 1px → 中心
        Color dash = new Color(0.25f, 0.85f, 0.95f, 1);  // 青色
        for (int i = 0; i < total; i++) {
            float cx = centerX + (i - (total - 1) / 2f) * (size + gap);
            drawDiamond(batch, vh, cx, cyCenter, size, dash);
            if (i >= p.dashCharges) {
                // 已消耗：内部挖空（与实心同中心，半透明暗色）
                drawDiamond(batch, vh, cx, cyCenter, size - 2 * thick, new Color(0, 0, 0, 0.45f));
            }
        }
    }

    /** 绘制中心 (cx,cyCenter)（屏幕 y 向下）、对角线 size 的菱形，旋转 45° */
    private void drawDiamond(SpriteBatch batch, int vh, float cx, float cyCenter, float size, Color c) {
        float h = size / 2f;
        int px = (int) size;
        batch.setColor(c);
        batch.draw(UiKit.whiteTex(), cx - h, UiKit.up(vh, cyCenter + h), h, h, size, size, 1, 1,
                45, 0, 0, px, px, false, false);
        batch.setColor(Color.WHITE);
    }

    /** 玩家侧竖条（屏幕坐标 y 向下）：从底部向上填充 ratio 比例 */
    private void drawVerticalBar(SpriteBatch batch, int vh, float x, float topY, float h, float ratio, Color c) {
        float w = 5;
        UiKit.rectR(batch, vh, x, topY, w, h, new Color(0, 0, 0, 0.6f), 1);
        if (ratio > 0) {
            float fh = h * Math.max(0, Math.min(1, ratio));
            UiKit.rectR(batch, vh, x, topY + h - fh, w, fh, c, 1);
        }
    }

    // ==================== UI 层（固定屏幕） ====================

    /**
     * HUD 状态：物品栏上方（Minecraft 式）。
     * 每个状态按位置设置二选一：设为"物品栏上方"时画横条；设为"跟随玩家"时画小方格+数值。
     */
    public void drawStatusBars(SpriteBatch batch, int vw, int vh, LocalPlayer player,
                               int hpBarPos, int manaBarPos, int hungerBarPos) {
        float hotbarW = 512;
        // 血条/蓝条/饱食统一为同一尺寸（240×16）
        float barW = 240, barH = 16, manaH = 16, hgW = 240, hgH = 16;
        float bx = (vw - hotbarW) / 2f;                 // 与快捷栏左缘对齐
        float hotbarTop = vh - 5 - 64;                  // 快捷栏顶边（贴屏底 5px）
        float healthY = hotbarTop - 2 - barH;           // 血条：紧贴快捷栏上方 2px
        float manaY = healthY - 3 - manaH;              // 蓝条：紧贴血条上方 3px
        // 血条：横条（物品栏上方）或 红方格+数值（玩家头顶）
        drawBarOrTile(batch, vh, bx, healthY, barW, barH, hpBarPos == 1,
                player.hp, player.maxHp, new Color(0.95f, 0.2f, 0.15f, 1), true);
        // 蓝条：横条（物品栏上方）或 蓝方格+数值（玩家右侧）
        drawBarOrTile(batch, vh, bx, manaY, barW, manaH, manaBarPos == 1,
                player.mana, player.maxMana, new Color(0.3f, 0.6f, 0.95f, 1), true);
        // 饱食：紧贴快捷栏上方 2px，右缘与快捷栏右缘对齐；或 金方格+数值（玩家左侧）
        if (hungerBarPos == 1) {
            float hgx = bx + hotbarW - hgW;
            float hgy = hotbarTop - 2 - hgH;
            drawBarOrTile(batch, vh, hgx, hgy, hgW, hgH, true,
                    player.hunger, player.maxHunger, new Color(0.95f, 0.72f, 0.3f, 1), true);
        } else {
            drawTileWithValue(batch, vh, bx + barW + 16, healthY, hgH,
                    player.hunger, new Color(0.95f, 0.72f, 0.3f, 1));
        }
    }

    /** 画一个 HUD 状态：bar=true 横条（含比例填充），bar=false 小方格；showText 时附数值 */
    private void drawBarOrTile(SpriteBatch batch, int vh, float x, float y, float w, float h,
                               boolean bar, int cur, int max, Color c, boolean showText) {
        if (bar) {
            UiKit.rectR(batch, vh, x, y, w, h, new Color(0, 0, 0, 0.6f), 2);
            float r = max > 0 ? Math.max(0, Math.min(1, (float) cur / max)) : 0;
            if (r > 0) UiKit.rectR(batch, vh, x, y, w * r, h, c, 2);
            if (showText) {
                UiKit.text(batch, vh, UiKit.fontSmall, cur + "/" + max,
                        x + w / 2, y + h / 2, Color.WHITE);
            }
        } else {
            drawTileWithValue(batch, vh, x, y, Math.min(w, h), cur, c);   // 方格尺寸跟随条高
        }
    }

    /** 小方格 + 右侧数值（文字垂直居中） */
    private void drawTileWithValue(SpriteBatch batch, int vh, float x, float y, float size,
                                   int value, Color c) {
        UiKit.rectR(batch, vh, x, y, size, size, c, 2);
        UiKit.textLeft(batch, vh, UiKit.fontSmall, String.valueOf(value),
                x + size + 6, y + size / 2 + 1, Color.WHITE);
    }

    /** 快捷栏（物品栏下方 9 格） */
    public void drawHotbar(SpriteBatch batch, int vw, int vh, ItemSlot[] inventory, LocalPlayer player) {
        float barW = 512, barH = 64;
        float bx = (vw - barW) / 2f;
        float by = vh - 5 - barH;                       // 快捷栏紧贴屏底 5px
        UiKit.rect(batch, vh, bx, by, barW, barH, new Color(0, 0, 0, 0.55f));
        UiKit.frame(batch, vh, bx, by, barW, barH, 1, new Color(1, 1, 1, 0.2f));
        for (int i = 0; i < GdxGame.HOTBAR_SIZE; i++) {
            float sx = bx + 6 + i * (52 + 4);
            float sy = by + 6;
            boolean sel = i == player.slot;
            UiKit.rect(batch, vh, sx, sy, 52, 52,
                    sel ? new Color(0.47f, 0.47f, 0.47f, 0.9f) : new Color(0.235f, 0.235f, 0.235f, 0.7f));
            UiKit.frame(batch, vh, sx, sy, 52, 52, 2,
                    sel ? new Color(1, 1, 1, 1) : new Color(1, 1, 1, 0.35f));
            ItemSlot item = inventory[i];
            if (item != null) {
                drawItemIcon(batch, item.name, item.count, sx + 10, sy + 10, 32, true, vh);
            }
        }
    }

    /** 快捷栏切换提示 */
    public void drawSlotToast(SpriteBatch batch, int vw, int vh) {
        long now = System.currentTimeMillis();
        if (slotToastText == null || now >= slotToastUntil) return;
        float alpha = 1f;
        if (now >= slotToastUntil - 1000) {
            alpha = Math.max(0, (slotToastUntil - now) / 1000f);
        }
        float tw = UiKit.textWidth(UiKit.fontNormal, slotToastText);
        float w = tw + 30, h = 28;
        float x = vw / 2 - w / 2;
        float y = vh - 108;
        Color bg = new Color(0, 0, 0, 0.62f * alpha);
        UiKit.rect(batch, vh, x, y, w, h, bg);
        UiKit.frame(batch, vh, x, y, w, h, 1, new Color(1, 1, 1, 0.5f * alpha));
        UiKit.text(batch, vh, UiKit.fontNormal, slotToastText, vw / 2, y + h / 2,
                new Color(1, 1, 1, alpha));
    }

    // ==================== 背包面板 ====================

    /**
     * 计算屏幕坐标对应背包槽索引；不在网格返回 -1。
     * mx/my 为虚拟坐标（UI 层），vw/vh 为虚拟分辨率。
     */
    public int invSlotAt(float mx, float my) {
        float gx = gridX((int) VIEW_W);
        float gy = gridY((int) VIEW_H) + invSlide;   // 面板滑入时命中位置同步下移
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                float sx = gx + col * (48 + 4);
                float sy = gy + row * (48 + 4);
                if (mx >= sx && mx <= sx + 48 && my >= sy && my <= sy + 48) {
                    return row * 9 + col;
                }
            }
        }
        return -1;
    }

    /** 背包面板（三栏：玩家形象/9×5 网格/合成+详情） */
    public void drawInventory(SpriteBatch batch, int vw, int vh, ItemSlot[] inventory,
                              LocalPlayer player, Texture invPlayerTexture, int hoverIndex,
                              GdxGame.Dragging draggingItem, float mouseX, float mouseY) {
        // 开启动画：面板从下方向上方滑入 + 淡入（缓出）
        invAnimT = Math.min(invAnimT + Gdx.graphics.getDeltaTime(), GdxGame.INV_ANIM_DUR);
        float p = invAnimT / GdxGame.INV_ANIM_DUR;
        p = 1f - (1f - p) * (1f - p) * (1f - p);
        invSlide = (1f - p) * vh * 0.3f;
        UiKit.globalAlpha = p;
        float px = panelX(vw);
        float py = panelY(vh) + invSlide;
        float panelW = 896, panelH = 284;
        UiKit.panel(batch, vh, px, py, panelW, panelH, new Color(0.118f, 0.118f, 0.157f, 0.92f));

        float pad = 14;

        // ---- 左侧：玩家形象（上 2/3）+ 盔甲（下 1/3） ----
        float lx = px + pad;
        float lw = 180;
        float contentH = 256;
        float playerH = contentH * 2f / 3f;
        float armorH = contentH - playerH - 6;
        float pvY = py + pad;
        UiKit.rect(batch, vh, lx, pvY, lw, playerH, new Color(0.078f, 0.078f, 0.11f, 0.85f));
        UiKit.frame(batch, vh, lx, pvY, lw, playerH, 1, new Color(1, 1, 1, 0.18f));
        if (invPlayerTexture != null) {
            float is = 160;
            float ix = lx + (lw - is) / 2;
            float iy = pvY + (playerH - is) / 2;
            batch.draw(invPlayerTexture, ix, UiKit.up(vh, iy + is), is, is);
        }
        float armY = pvY + playerH + 6;
        UiKit.rect(batch, vh, lx, armY, lw, armorH, new Color(0.078f, 0.078f, 0.11f, 0.85f));
        UiKit.frame(batch, vh, lx, armY, lw, armorH, 1, new Color(1, 1, 1, 0.18f));
        String[] armorLabels = {"上盔甲", "腰带", "下盔甲"};
        for (int i = 0; i < 3; i++) {
            float ay = armY + 4 + i * ((armorH - 8) / 3f);
            UiKit.rect(batch, vh, lx + 6, ay, 24, 24, new Color(0.235f, 0.235f, 0.275f, 0.7f));
            UiKit.frame(batch, vh, lx + 6, ay, 24, 24, 2, new Color(1, 1, 1, 0.25f));
            UiKit.textLeft(batch, vh, UiKit.fontSmall, armorLabels[i], lx + 36, ay + 6, new Color(0.78f, 0.78f, 0.83f, 1));
        }

        // ---- 中间：9×5 网格 ----
        float gx = gridX(vw);
        float gy = gridY(vh);
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                int idx = row * 9 + col;
                float sx = gx + col * (48 + 4);
                float sy = gy + row * (48 + 4);
                Color border = new Color(1, 1, 1, 0.25f);
                if (idx < GdxGame.HOTBAR_SIZE) border = new Color(0.71f, 0.71f, 1f, 0.5f);
                if (idx == player.slot) border = Color.WHITE;
                UiKit.rect(batch, vh, sx, sy, 48, 48, new Color(0.235f, 0.235f, 0.275f, 0.7f));
                UiKit.frame(batch, vh, sx, sy, 48, 48, 2, border);
                ItemSlot item = inventory[idx];
                if (item != null) {
                    drawItemIcon(batch, item.name, item.count, sx + 8, sy + 8, 32, true, vh);
                }
            }
        }

        // ---- 右侧：合成（上 1/2）+ 详情（下 1/2） ----
        float rx = px + pad + 180 + 12 + 464 + 12;
        float rw = 200;
        float craftH = 128;
        float craftY = py + pad;
        UiKit.rect(batch, vh, rx, craftY, rw, craftH, new Color(0.078f, 0.078f, 0.11f, 0.85f));
        UiKit.frame(batch, vh, rx, craftY, rw, craftH, 1, new Color(1, 1, 1, 0.18f));
        // 3×3 合成格 + 箭头 + 结果
        float cell = 28, cgap = 3;
        float grid3w = cell * 3 + cgap * 2;
        float grid3h = cell * 3 + cgap * 2;
        float arrowW = 20;
        float total = grid3w + 8 + arrowW + 8 + cell;
        float cx0 = rx + (rw - total) / 2;
        float cy0 = craftY + (craftH - grid3h) / 2;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float sx = cx0 + c * (cell + cgap);
                float sy = cy0 + r * (cell + cgap);
                UiKit.rect(batch, vh, sx, sy, cell, cell, new Color(0.235f, 0.235f, 0.275f, 0.7f));
                UiKit.frame(batch, vh, sx, sy, cell, cell, 2, new Color(1, 1, 1, 0.22f));
            }
        }
        // 合成箭头（矩形手动绘制，避免字体缺字形不可见）
        Color ac = new Color(0.67f, 0.67f, 0.67f, 1);
        float ax = cx0 + grid3w + 8;
        float ay = cy0 + cell / 2;
        UiKit.rect(batch, vh, ax, ay - 1, arrowW - 8, 3, ac);       // 箭杆
        float hx = ax + arrowW - 8;
        UiKit.rect(batch, vh, hx + 7, ay - 4, 2, 8, ac);            // 箭头（梯形近似）
        UiKit.rect(batch, vh, hx + 4, ay - 2.5f, 3, 5, ac);
        UiKit.rect(batch, vh, hx + 1, ay - 1, 3, 2, ac);
        float resX = cx0 + grid3w + 8 + arrowW + 8;
        UiKit.rect(batch, vh, resX, cy0, cell, cell, new Color(0.235f, 0.235f, 0.275f, 0.7f));
        UiKit.frame(batch, vh, resX, cy0, cell, cell, 2, new Color(1, 1, 1, 0.5f));

        // 详情区（下 1/2）：横向 左 2/3 图标 + 右 1/3 文字
        float detH = contentH - craftH - 8;
        float detY = craftY + craftH + 8;
        UiKit.rect(batch, vh, rx, detY, rw, detH, new Color(0.078f, 0.078f, 0.11f, 0.85f));
        UiKit.frame(batch, vh, rx, detY, rw, detH, 1, new Color(1, 1, 1, 0.18f));
        drawInvDetail(batch, rx, detY, rw, detH, vh, inventory, hoverIndex);

        // 拖拽中的物品跟随鼠标（最上层）
        if (draggingItem != null) {
            drawItemIcon(batch, draggingItem.item.name, draggingItem.item.count, mouseX - 16, mouseY - 16, 32, true, vh);
        }
        UiKit.globalAlpha = 1f;
    }

    /** 是否为武器/工具（注册在 Tool 体系中，图标特殊处理） */
    private boolean isTool(String name) {
        return Tool.byId(name) != null;
    }

    /** 背包悬停详情：左 图标 + 右 详情文字 */
    private void drawInvDetail(SpriteBatch batch, float rx, float detY, float rw, float detH, int vh,
                               ItemSlot[] inventory, int hoverIndex) {
        ItemSlot item = (hoverIndex >= 0 && hoverIndex < GdxGame.INVENTORY_TOTAL) ? inventory[hoverIndex] : null;
        if (item == null) return;
        String name = ZhName.zhBlockName(item.name, blocks);
        BlockMeta meta = blocks.metaByName(item.name);
        float iconAreaW = 84;   // 图标区窄些，文字区留出更多宽度
        float iconSize = 56;
        float ix = rx + (iconAreaW - iconSize) / 2;
        float iy = detY + (detH - iconSize) / 2 + 5;
        drawItemIcon(batch, item.name, 0, ix, iy, iconSize, false, vh);
        UiKit.frameR(batch, vh, ix, iy, iconSize, iconSize, new Color(1, 1, 1, 0.15f), 0);
        float tx = rx + iconAreaW + 4;
        float ty = detY + 15;
        UiKit.text(batch, vh, UiKit.fontSmall, name, rx + iconAreaW / 2, ty, Color.WHITE);
        if (meta != null) {
            String hardness = meta.hardness > 0 ? String.format(java.util.Locale.ROOT, "%.1f", meta.hardness) : "不可破坏";
            ty += 18;
            UiKit.textLeft(batch, vh, UiKit.fontSmall, "硬度: " + hardness + " 堆叠: " + meta.stackSize, tx, ty,
                    new Color(0.78f, 0.78f, 0.83f, 1));
            ty += 15;
            UiKit.textLeft(batch, vh, UiKit.fontSmall,
                    (meta.solid ? "实体" : "非实体") + " " + (meta.transparent ? "透明" : "不透明"), tx, ty,
                    new Color(0.78f, 0.78f, 0.83f, 1));
            if (meta.drops != null && !meta.drops.isEmpty()) {
                UiKit.textLeft(batch, vh, UiKit.fontSmall, "掉落: " + ZhName.zhBlockName(meta.drops, blocks), tx, ty,
                        new Color(0.78f, 0.78f, 0.83f, 1));
            }
        } else {
            UiKit.textLeft(batch, vh, UiKit.fontSmall, "非方块物品", tx, ty + 18, new Color(0.78f, 0.78f, 0.83f, 1));
        }
    }

    /** 物品图标（方块纹理 / 非方块兜底色+首字母），size 为显示尺寸 */
    private void drawItemIcon(SpriteBatch batch, String name, int count, float x, float y,
                              float size, boolean showCount, int vh) {
        int tileId = blocks.tileId(name);
        if ("bucket".equals(name) || "water_bucket".equals(name) || "lava_bucket".equals(name)) {
            // 桶：固定颜色（灰/蓝/橙），无方块纹理
            Color c = "water_bucket".equals(name) ? new Color(0.35f, 0.55f, 0.95f, 1)
                    : "lava_bucket".equals(name) ? new Color(0.95f, 0.5f, 0.15f, 1)
                    : new Color(0.62f, 0.62f, 0.66f, 1);
            UiKit.rectR(batch, vh, x, y, size, size, c, 0);
            UiKit.frameR(batch, vh, x, y, size, size, new Color(0, 0, 0, 0.6f), 0);
            char ch = Character.toUpperCase(name.charAt(0));
            UiKit.text(batch, vh, UiKit.fontSmall, String.valueOf(ch),
                    x + size / 2, y + size / 2, Color.WHITE);
        } else if (isTool(name)) {
            // 武器/工具：有贴图则用真实贴图（32x32 居中），否则固定色+首字母兜底
            Texture tt = toolTex.get(name);
            if (tt != null) {
                batch.draw(tt, x + (size - 32) / 2f, UiKit.up(vh, y + (size - 32) / 2f + 32), 32, 32);
            } else {
                Color c = "sword".equals(name) ? new Color(0.8f, 0.82f, 0.86f, 1)
                        : "gun".equals(name) ? new Color(0.28f, 0.28f, 0.32f, 1)
                        : "pickaxe".equals(name) ? new Color(0.62f, 0.47f, 0.3f, 1)
                        : new Color(0.66f, 0.68f, 0.72f, 1);
                UiKit.rectR(batch, vh, x, y, size, size, c, 0);
                UiKit.frameR(batch, vh, x, y, size, size, new Color(0, 0, 0, 0.6f), 0);
                char ch = Character.toUpperCase(name.charAt(0));
                UiKit.text(batch, vh, UiKit.fontSmall, String.valueOf(ch),
                        x + size / 2, y + size / 2, Color.WHITE);
            }
        } else if (tileId >= 0) {
            Texture t = texFactory.getTexture(tileId, blocks.meta(tileId));
            batch.draw(t, x + (size - 32) / 2f, UiKit.up(vh, y + (size - 32) / 2f + 32), 32, 32);
        } else {
            UiKit.rectR(batch, vh, x, y, size, size, TextureFactory.fallbackColor(name), 0);
            UiKit.frameR(batch, vh, x, y, size, size, new Color(0, 0, 0, 0.6f), 0);
            char ch = name.isEmpty() ? '?' : Character.toUpperCase(name.charAt(0));
            UiKit.text(batch, vh, UiKit.fontSmall, String.valueOf(ch),
                    x + size / 2, y + size / 2, Color.WHITE);
        }
        if (showCount && count > 1) {
            UiKit.text(batch, vh, UiKit.fontSmall, String.valueOf(count),
                    x + size - 8, y + size - 12, Color.WHITE);
        }
    }

    // ==================== 背包布局辅助 ====================

    private float panelX(int vw) {
        return (vw - 896) / 2f;
    }

    private float panelY(int vh) {
        // 背包底端与快捷栏底端对齐（快捷栏底端距屏底 5px）
        return vh - 5 - 284;
    }

    private float gridX(int vw) {
        return panelX(vw) + 14 + 180 + 12;
    }

    private float gridY(int vh) {
        return panelY(vh) + 14;
    }
}
