package client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.Texture;

import client.data.BlocksData;
import client.data.BlockMeta;
import client.ui.UiKit;
import client.world.ClientWorld;
import client.world.LocalPlayer;

import java.util.List;
import java.util.Map;

/**
 * 世界渲染（移植 game.js render）。
 * 坐标：camX/camY 为相机左上角世界坐标（世界 y 向下）；屏幕坐标 y 向下。
 */
public class WorldRenderer {

    public static final float TILE = 32f;

    /** 水面线颜色（表面格顶部 2px 高光，让水体有清晰水面而非实心方块） */
    private static final Color WATER_LINE = new Color(0.72f, 0.88f, 1f, 0.95f);
    private static final Color LAVA_LINE = new Color(1f, 0.78f, 0.35f, 0.95f);

    /** 掉落物（服务器广播） */
    public static class DropView {
        public int id;
        public float x, y;
        public String name;
        public float life;
        public boolean dead;
    }

    /** 远程玩家（服务器广播） */
    public static class RemotePlayer {
        public String id;
        public String name;
        public float x, y;
        public String dir;
        public int anim;
        public int hp = 100, maxHp = 100;
    }

    /** 怪物（服务器广播） */
    public static class MobView {
        public int id;
        public float x, y;
        public int hp, maxHp;
        public boolean hurt;
    }

    /** 选中框 */
    public static class Selection {
        public int x, y;
        public boolean solid;
        public boolean inRange;
    }

    public void draw(SpriteBatch batch, int vw, int vh, ClientWorld world, BlocksData blocks,
                     TextureFactory texFactory, PlayerTextures playerTex,
                     float camX, float camY, LocalPlayer player,
                     List<DropView> drops, Map<String, RemotePlayer> remotes, String myId,
                     List<MobView> mobs, Selection sel, boolean showHitboxes) {
        // 背景
        batch.setColor(126 / 255f, 192 / 255f, 238 / 255f, 1);
        batch.draw(UiKit.whiteTex(), 0, 0, vw, vh);
        batch.setColor(Color.WHITE);

        // 方块
        int startTx = (int) Math.floor(camX / TILE);
        int startTy = Math.max(0, (int) Math.floor(camY / TILE));
        int endTx = (int) Math.ceil((camX + vw) / TILE);
        int endTy = Math.min(ClientWorld.WORLD_HEIGHT_TILES - 1, (int) Math.ceil((camY + vh) / TILE));
        for (int ty = startTy; ty <= endTy; ty++) {
            for (int tx = startTx; tx <= endTx; tx++) {
                int type = world.getTile(tx, ty);
                if (type == BlocksData.T_AIR) continue;
                BlockMeta meta = blocks.meta(type);
                if (meta == null) continue;
                Texture tex = texFactory.getTexture(type, meta);
                float sx = (float) Math.floor(tx * TILE - camX);
                float sy = (float) Math.floor(ty * TILE - camY);
                if (type == BlocksData.T_WATER || type == BlocksData.T_LAVA) {
                    // 流体按水位裁切：level = 欠满量（0 满格 ~ 15 最薄，一格最多 16 级），
                    // 只画格子底部 h 像素；旧档 level 可能超 15，钳制为不画（当作已干涸）
                    float h = Math.max(0, 16 - world.getLevel(tx, ty)) * TILE / 16f;
                    if (h <= 0) continue;
                    batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, h, 0, 0, (int) TILE, (int) h, false, false);
                    // 表面格（上方非同型流体）画水面线，让水体有清晰水面（Terraria 式）
                    if (world.getTile(tx, ty - 1) != type) {
                        float topY = sy + TILE - h; // 水面实际高度的屏幕 y
                        UiKit.rectR(batch, vh, sx, topY, TILE, 2,
                                type == BlocksData.T_WATER ? WATER_LINE : LAVA_LINE, 0);
                    }
                } else {
                    batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, TILE);
                }
            }
        }

        // 掉落物
        if (drops != null) {
            for (DropView d : drops) {
                int tileId = blocks.tileId(d.name);
                Texture tex = tileId >= 0 ? texFactory.getTexture(tileId, blocks.meta(tileId)) : null;
                float sx = d.x - camX - 8;
                float sy = d.y - camY - 8;
                if (tex != null) {
                    batch.draw(tex, sx, UiKit.up(vh, sy + 16), 16, 16);
                } else {
                    UiKit.rectR(batch, vh, sx, sy, 16, 16, TextureFactory.fallbackColor(d.name), 0);
                }
                UiKit.frameR(batch, vh, sx, sy, 16, 16, new Color(0, 0, 0, 0.3f), 0);
            }
        }

        // 怪物（史莱姆）
        if (mobs != null) {
            for (MobView m : mobs) {
                float sx = m.x - camX - TILE / 2f;
                float sy = m.y - camY - TILE / 2f;
                // 身体：绿色方块，受击时变红
                Color body = m.hurt ? new Color(1f, 0.4f, 0.4f, 1f) : new Color(0.4f, 0.8f, 0.4f, 1f);
                UiKit.rectR(batch, vh, sx + 2, sy + 2, TILE - 4, TILE - 4, body, 0);
                // 暗色边框
                UiKit.frameR(batch, vh, sx + 2, sy + 2, TILE - 4, TILE - 4, new Color(0.15f, 0.35f, 0.15f, 0.8f), 0);
                // 眼睛（白色 + 黑色瞳孔）
                float eyeY = sy + TILE * 0.35f;
                UiKit.rectR(batch, vh, sx + TILE * 0.25f, eyeY, 5, 5, Color.WHITE, 0);
                UiKit.rectR(batch, vh, sx + TILE * 0.6f, eyeY, 5, 5, Color.WHITE, 0);
                UiKit.rectR(batch, vh, sx + TILE * 0.27f, eyeY + 1, 2, 3, Color.BLACK, 0);
                UiKit.rectR(batch, vh, sx + TILE * 0.62f, eyeY + 1, 2, 3, Color.BLACK, 0);
                // 血条（头顶）
                drawHealthBar(batch, vh, sx, sy, m.hp, m.maxHp);
            }
        }

        // 其他玩家
        if (remotes != null) {
            for (RemotePlayer p : remotes.values()) {
                if (myId != null && p.id != null && p.id.equals(myId)) continue;
                Texture tex = playerTex.get(p.dir, p.anim);
                float sx = p.x - camX;
                float sy = p.y - camY;
                batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, TILE);
                drawHealthBar(batch, vh, sx, sy, p.hp, p.maxHp);
                UiKit.text(batch, vh, UiKit.fontSmall, p.name == null ? "Player" : p.name,
                        sx + TILE / 2, sy - 22, Color.WHITE);
            }
        }

        // 本机玩家（头顶血条/菱形/状态条已移至 HudRenderer）
        if (player != null) {
            Texture tex = playerTex.get(player.direction, player.animFrame);
            float sx = player.renderX - camX;
            float sy = player.renderY - camY;
            batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, TILE);
        }

        // 选中框
        if (sel != null) {
            float sx = sel.x * TILE - camX;
            float sy = sel.y * TILE - camY;
            Color c;
            if (!sel.inRange) c = new Color(1, 0.47f, 0.47f, 0.6f);
            else if (!sel.solid) c = new Color(1, 1, 1, 0.3f);
            else c = new Color(1, 1, 1, 0.9f);
            UiKit.frameR(batch, vh, sx + 1, sy + 1, TILE - 2, TILE - 2, c, 0);
        }

        // F3+B：绘制所有实体碰撞箱（线框）
        if (showHitboxes) {
            Color hitCol = new Color(0f, 1f, 0f, 0.9f);
            // 本机玩家：碰撞箱 inset=3, colW=colH=TILE-6=26
            if (player != null) {
                float inset = 3f;
                float colW = TILE - 2 * inset;
                drawHitbox(batch, vh, player.renderX + inset - camX, player.renderY + inset - camY,
                        colW, colW, new Color(1f, 0f, 0f, 0.9f));
            }
            // 远程玩家（同本机玩家碰撞模型）
            if (remotes != null) {
                for (RemotePlayer p : remotes.values()) {
                    if (myId != null && p.id != null && p.id.equals(myId)) continue;
                    float inset = 3f;
                    float colW = TILE - 2 * inset;
                    drawHitbox(batch, vh, p.x + inset - camX, p.y + inset - camY,
                            colW, colW, new Color(1f, 0.5f, 0f, 0.9f));
                }
            }
            // 怪物：中心坐标，1x1 格碰撞箱
            if (mobs != null) {
                for (MobView m : mobs) {
                    drawHitbox(batch, vh, m.x - TILE / 2f - camX, m.y - TILE / 2f - camY,
                            TILE, TILE, hitCol);
                }
            }
            // 掉落物：中心坐标，16x16 碰撞箱
            if (drops != null) {
                for (DropView d : drops) {
                    drawHitbox(batch, vh, d.x - 8 - camX, d.y - 8 - camY,
                            16, 16, new Color(0f, 0.8f, 1f, 0.9f));
                }
            }
        }
    }

    /** 绘制实体碰撞箱线框（屏幕坐标 y 向下，2px 边框） */
    private void drawHitbox(SpriteBatch batch, int vh, float sx, float sy, float w, float h, Color c) {
        UiKit.frameR(batch, vh, sx, sy, w, h, c, 0);
    }

    /** 玩家头顶血条（sx/sy 为玩家左上角，屏幕坐标 y 向下），供远程玩家使用 */
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
}
