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
                     Selection sel) {
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
                batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, TILE);
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

        // 其他玩家
        if (remotes != null) {
            for (RemotePlayer p : remotes.values()) {
                if (myId != null && p.id != null && p.id.equals(myId)) continue;
                Texture tex = playerTex.get(p.dir, p.anim);
                float sx = p.x - camX;
                float sy = p.y - camY;
                batch.draw(tex, sx, UiKit.up(vh, sy + TILE), TILE, TILE);
                UiKit.text(batch, vh, UiKit.fontSmall, p.name == null ? "Player" : p.name,
                        sx + TILE / 2, sy - 4, Color.WHITE);
            }
        }

        // 本机玩家
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
    }
}
