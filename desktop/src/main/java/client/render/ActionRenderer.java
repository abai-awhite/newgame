package client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import client.data.BlocksData;
import client.entity.Projectile;
import client.tool.Tool;
import client.ui.UiKit;
import client.world.ClientWorld;
import client.world.LocalPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * 玩家动作渲染类：武器攻击动作的绘制与更新。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>挥砍（剑/镐/斧）：按住左键时刀身从下向上扫过，带残影与白色弧光</li>
 *   <li>射击（枪）：枪口朝向鼠标，后坐 + 枪口闪光，并生成飞行的子弹</li>
 *   <li>子弹：世界坐标运动，碰到实心方块或超时后消失</li>
 * </ul>
 *
 * <p>坐标约定：世界坐标 y 向下；屏幕坐标 y 向下，绘制时用 UiKit.up() 翻转。
 * 与 WorldRenderer 同一投影（物理窗口坐标）。</p>
 */
public class ActionRenderer {

    private static final float TILE = 32f;

    /** 武器/工具贴图（sword/gun/pickaxe/axe） */
    private final ToolTextures toolTex;

    /** 弹幕实体列表（继承 Entity，有物理碰撞） */
    private final List<Projectile> projectiles = new ArrayList<>();

    public ActionRenderer(ToolTextures toolTex) {
        this.toolTex = toolTex;
    }

    /** 清空所有弹幕（切换世界时调用） */
    public void clear() {
        projectiles.clear();
    }

    /** 从 (px,py)（世界坐标）向瞄准点发射一颗弹幕实体 */
    public void spawnBullet(float px, float py, float aimX, float aimY) {
        float dx = aimX - px, dy = aimY - py;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        float speed = 520f;   // 像素/秒
        float vx = dx / len * speed;
        float vy = dy / len * speed;
        projectiles.add(new Projectile(px, py, vx, vy, 1.4, 15));
    }

    /** 弹幕物理：Projectile.update（直线运动 + 方块碰撞） + 怪物命中 + 超时销毁。
     *  hitMob 回调：弹幕命中怪物时调用（传入 mobId），由 GdxGame 发送 attackMob 消息 */
    public void update(float delta, ClientWorld world, BlocksData blocks,
                       List<WorldRenderer.MobView> mobs, java.util.function.IntConsumer hitMob) {
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;
            p.update(delta, world, blocks);
            if (!p.isAlive()) continue;
            // 怪物命中检测
            if (mobs != null && hitMob != null) {
                for (WorldRenderer.MobView m : mobs) {
                    if (Math.abs((float) p.getX() - m.x) < TILE / 2f
                            && Math.abs((float) p.getY() - m.y) < TILE / 2f) {
                        p.setAlive(false);
                        hitMob.accept(m.id);
                        break;
                    }
                }
            }
        }
        projectiles.removeIf(p -> !p.isAlive());
    }

    /** 绘制子弹 + 本机玩家当前攻击动作（世界层，屏幕坐标）；heldName 为手持物品名 */
    public void draw(SpriteBatch batch, int vh, float camX, float camY, LocalPlayer player,
                     String heldName, boolean showHitboxes) {
        drawBullets(batch, vh, camX, camY);
        if (player == null || player.actionT <= 0 || player.actionType == null) return;
        float sx = player.renderX - camX;
        float sy = player.renderY - camY;
        if ("swing".equals(player.actionType)) {
            drawSwing(batch, vh, sx, sy, camX, camY, player, heldName, showHitboxes);
        } else if ("shoot".equals(player.actionType)) {
            drawShoot(batch, vh, sx, sy, camX, camY, player, showHitboxes);
        }
    }

    /** F3+B：绘制所有弹幕碰撞箱线框（弹幕是继承 Entity 的实体，有物理和 AABB 碰撞箱） */
    public void drawHitboxes(SpriteBatch batch, int vh, float camX, float camY) {
        Color c = new Color(1f, 1f, 0f, 0.9f);
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;
            entity.AABB aabb = p.getAABB();
            float sx = (float) aabb.x - camX;
            float sy = (float) aabb.y - camY;
            UiKit.frameR(batch, vh, sx, sy, (float) aabb.width, (float) aabb.height, c, 0);
        }
    }

    // ==================== 弹幕 ====================

    private void drawBullets(SpriteBatch batch, int vh, float camX, float camY) {
        for (Projectile p : projectiles) {
            if (!p.isAlive()) continue;
            float bx = (float) p.getX() - camX;
            float by = (float) p.getY() - camY;
            float vx = (float) p.getVX(), vy = (float) p.getVY();
            float spd = (float) Math.sqrt(vx * vx + vy * vy);
            if (spd > 1f) {
                float nx = vx / spd, ny = vy / spd;
                for (int i = 1; i <= 3; i++) {
                    float tx = bx - nx * i * 4f;
                    float ty = by - ny * i * 4f;
                    float s = 4f - i;
                    UiKit.rectR(batch, vh, tx - s / 2, ty - s / 2, s, s,
                            new Color(1f, 0.9f, 0.5f, 0.35f * (1f - i * 0.2f)), 0);
                }
            }
            // 弹头（黄色亮点）
            UiKit.rectR(batch, vh, bx - 3, by - 3, 6, 6, new Color(1f, 0.95f, 0.55f, 0.95f), 0);
        }
    }

    // ==================== 挥砍（剑/镐/斧） ====================

    private void drawSwing(SpriteBatch batch, int vh, float sx, float sy,
                           float camX, float camY, LocalPlayer p, String heldName, boolean showHitboxes) {
        float max = 8f;
        float t = 1f - Math.max(0, Math.min(1, p.actionT / max));   // 0→1
        float cx = sx + TILE / 2f, cy = sy + TILE / 2f;
        // 朝向瞄准点（世界坐标 aim → 屏幕坐标），剑始终朝鼠标挥砍
        float aimSx = p.aimX - camX, aimSy = p.aimY - camY;
        float base = (float) Math.atan2(aimSy - cy, aimSx - cx);
        float len = 26f;

        // 手持武器贴图（剑/镐/斧）；加载不到时退回程序化画法
        Texture tex = toolTex.get(heldName);
        Tool tool = Tool.byId(heldName);
        float pvx = tool != null ? tool.pivotFx : 0f;
        float pvy = tool != null ? tool.pivotFy : 0f;
        // 以鼠标相对玩家中心的水平位置区分左右：
        // 右侧 → 贴图不镜像，挥砍顺时针（从 base-0.9 扫到 base+0.9）
        // 左侧 → 贴图水平镜像，挥砍逆时针（从 base+0.9 扫到 base-0.9）
        boolean left = aimSx - cx < 0;
        boolean flip = tex != null && left;
        float a0, a1;
        if (left) {
            a0 = base + 0.9f;
            a1 = base - 0.9f;
        } else {
            a0 = base - 0.9f;
            a1 = base + 0.9f;
        }

        // 残影（此前几个角度的武器，透明度递减）
        for (int g = 3; g >= 1; g--) {
            float gt = t - g * 0.11f;
            if (gt < 0) continue;
            float ang = a0 + (a1 - a0) * gt;
            float a = Math.max(0, 0.3f - g * 0.07f);
            if (tex != null) {
                drawToolTex(batch, vh, tex, cx, cy, ang, 0.9f, a, pvx, pvy, flip);
            } else {
                drawSword(batch, vh, cx, cy, ang, len, new Color(0.8f, 0.84f, 0.9f, a));
            }
        }
        // 当前武器
        float ang = a0 + (a1 - a0) * t;
        if (tex != null) {
            drawToolTex(batch, vh, tex, cx, cy, ang, 1f, 0.95f, pvx, pvy, flip);
            if (showHitboxes) {
                drawToolHitbox(batch, vh, cx, cy, ang, 1f, pvx, pvy, flip,
                        tex.getWidth(), tex.getHeight(), new Color(1f, 0.75f, 0f, 0.95f));
            }
        } else {
            drawSword(batch, vh, cx, cy, ang, len, new Color(0.92f, 0.95f, 1f, 0.95f));
            if (showHitboxes) {
                // 程序化剑：刀身 26x4 的旋转矩形边框
                drawRotRect(batch, vh, cx, cy, ang, len, 1.5f, new Color(1f, 0.75f, 0f, 0.95f));
            }
        }
        // 白色弧光（已扫过的轨迹）
        drawArc(batch, vh, cx, cy, (len + 6f) * 2f, a0, ang, t);
    }

    /** 以玩家中心 (px,py)（屏幕 y 向下）为枢轴画旋转的武器贴图。
     * ang 为屏幕角度（0=右，π/2=下，π=左，-π/2=上）。
     * 贴图 pivotFx/pivotFy 指定的像素固定在玩家中心并作为旋转中心；
     * flipX=true 时水平镜像：枢轴 x 比例取 1-fx（同一物理像素留在玩家中心）。
     *
     * <p>旋转角推导（SpriteBatch rot 为 y-up 逆时针角度）：
     * 不镜像：贴图朝右，旋转到屏幕角度 ang → rot = -ang；
     * 镜像：贴图翻转后朝左（屏幕角度 π），要朝向 ang → 旋转 ang-π 屏幕角 → rot = -(ang-π) = π-ang。</p> */
    private void drawToolTex(SpriteBatch batch, int vh, Texture tex, float px, float py,
                             float ang, float scale, float alpha,
                             float pivotFx, float pivotFy, boolean flipX) {
        float w = tex.getWidth() * scale, h = tex.getHeight() * scale;
        float upY = UiKit.up(vh, py);
        float effFx = flipX ? 1f - pivotFx : pivotFx;
        float x = px - effFx * w;
        float y = upY - pivotFy * h;
        float rot = flipX
                ? (float) Math.toDegrees((float) Math.PI - ang)
                : (float) Math.toDegrees(-ang);
        batch.setColor(1, 1, 1, alpha);
        batch.draw(tex, x, y, effFx * w, pivotFy * h, w, h, 1, 1, rot,
                0, 0, tex.getWidth(), tex.getHeight(), flipX, false);
        batch.setColor(Color.WHITE);
    }

    /** 画一把剑：旋转矩形刀身 + 高光 + 垂直护手（锚点 handX/handY 为握点） */
    private void drawSword(SpriteBatch batch, int vh, float handX, float handY, float ang, float len, Color c) {
        // 刀身
        drawRotRect(batch, vh, handX, handY, ang, len, 4f, c);
        // 刀身高光（向法线方向偏移 0.8px）
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        drawRotRect(batch, vh, handX - sin * 0.8f, handY + cos * 0.8f, ang, len * 0.92f, 1.4f,
                new Color(1f, 1f, 1f, Math.min(1f, c.a * 1.2f)));
        // 护手（与刀身垂直的短横条）
        float gx = handX + cos * len * 0.18f, gy = handY + sin * len * 0.18f;
        drawRotRect(batch, vh, gx, gy, ang + (float) Math.PI / 2f, 9f, 2.5f,
                new Color(0.35f, 0.35f, 0.4f, c.a));
    }

    /** 以 (hx,hy)（屏幕 y 向下）为锚点画旋转矩形：宽 len、高 thick，角度 ang（弧度，屏幕系） */
    private void drawRotRect(SpriteBatch batch, int vh, float hx, float hy, float ang, float len,
                             float thick, Color c) {
        float upY = UiKit.up(vh, hy);
        float rot = (float) Math.toDegrees(-ang);   // 屏幕角 → y-up 旋转角
        batch.setColor(c);
        batch.draw(UiKit.whiteTex(), hx, upY - thick / 2f, 0, thick / 2f, len, thick, 1, 1, rot,
                0, 0, 1, 1, false, false);
        batch.setColor(Color.WHITE);
    }

    /** 挥砍弧光：沿从 a0 到当前角度 ang 的圆弧画一圈白色小方块（透明度渐隐） */
    private void drawArc(SpriteBatch batch, int vh, float hx, float hy, float radius,
                         float a0, float ang, float t) {
        int n = 10;
        for (int i = 0; i <= n; i++) {
            float f = i / (float) n;
            if (f > t) break;
            float a = a0 + (ang - a0) * f;
            float ax = hx + (float) Math.cos(a) * radius;
            float ay = hy + (float) Math.sin(a) * radius;
            float s = 3f;
            float alpha = 0.55f * (1f - Math.abs(f - t) * 0.9f);
            UiKit.rectR(batch, vh, ax - s / 2, ay - s / 2, s, s, new Color(1f, 1f, 1f, Math.max(0, alpha)), 0);
        }
    }

    // ==================== 射击（枪） ====================

    private void drawShoot(SpriteBatch batch, int vh, float sx, float sy,
                           float camX, float camY, LocalPlayer p, boolean showHitboxes) {
        float max = 6f;
        float t = 1f - Math.max(0, Math.min(1, p.actionT / max));   // 0→1
        float cx = sx + TILE / 2f, cy = sy + TILE / 2f;
        // 枪体锚点 = 玩家中心（旋转中心）
        float ax = cx, ay = cy;
        // 枪口朝向瞄准点
        float aimSx = p.aimX - camX, aimSy = p.aimY - camY;
        float ang = (float) Math.atan2(aimSy - ay, aimSx - ax);
        float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
        // 判断鼠标在玩家左侧还是右侧：左侧镜像翻转贴图
        boolean left = aimSx - cx < 0;
        // 枪体：有贴图则用真实枪械贴图绕玩家中心旋转，否则程序化枪管
        Texture tex = toolTex.get("gun");
        Tool gun = Tool.byId("gun");
        float pvx = gun != null ? gun.pivotFx : 0f;
        float pvy = gun != null ? gun.pivotFy : 0f;
        if (tex != null) {
            // 左右镜像：右侧不翻转 rot=-ang；左侧翻转 rot=π-ang（与挥砍工具一致）
            drawToolTex(batch, vh, tex, ax, ay, ang, 0.9f, 1, pvx, pvy, left);
            if (showHitboxes) {
                drawToolHitbox(batch, vh, ax, ay, ang, 0.9f, pvx, pvy, left,
                        tex.getWidth(), tex.getHeight(), new Color(1f, 0.75f, 0f, 0.95f));
            }
        } else {
            // 枪管
            drawRotRect(batch, vh, ax, ay, ang, 14f, 5f, new Color(0.22f, 0.22f, 0.26f, 1));
            drawRotRect(batch, vh, ax - sin * 0.6f, ay + cos * 0.6f, ang, 12f, 1.5f, new Color(0.5f, 0.5f, 0.55f, 1));
            if (showHitboxes) {
                drawRotRect(batch, vh, ax, ay, ang, 14f, 1.5f, new Color(1f, 0.75f, 0f, 0.95f));
            }
        }
        // 枪口闪光（前 40% 阶段）；贴图模式下枪口离枢轴更远，闪光点随之外移
        if (t < 0.4f) {
            float muzzleDist = (tex != null) ? 26f : 14f;
            float mx = ax + cos * muzzleDist, my = ay + sin * muzzleDist;
            float k = 1f - t / 0.4f;                 // 0→1 快速衰减
            float fs = 12f * k + 4f;
            Color fc = new Color(1f, 0.88f, 0.3f, 0.85f * k);
            UiKit.rectR(batch, vh, mx - fs / 2, my - fs / 2, fs, fs, fc, 0);
            // 星光（4 向小方块）
            float rs = fs * 0.55f, cs = 2.5f * k + 1f;
            UiKit.rectR(batch, vh, mx - cs / 2 - rs, my - cs / 2, cs, cs, fc, 0);
            UiKit.rectR(batch, vh, mx - cs / 2 + rs, my - cs / 2, cs, cs, fc, 0);
            UiKit.rectR(batch, vh, mx - cs / 2, my - cs / 2 - rs, cs, cs, fc, 0);
            UiKit.rectR(batch, vh, mx - cs / 2, my - cs / 2 + rs, cs, cs, fc, 0);
        }
    }

    /** 绘制工具贴图当前的旋转矩形边框（与 drawToolTex 使用完全相同的变换）。 */
    private void drawToolHitbox(SpriteBatch batch, int vh, float px, float py,
                                float ang, float scale,
                                float pivotFx, float pivotFy, boolean flipX,
                                int texW, int texH, Color c) {
        float w = texW * scale, h = texH * scale;
        float upY = UiKit.up(vh, py);
        float effFx = flipX ? 1f - pivotFx : pivotFx;
        // 与 drawToolTex 完全一致：纹理左下角（y-up 坐标）
        float x = px - effFx * w;
        float y = upY - pivotFy * h;
        // 四个角（y-up 坐标）
        float[][] corners = {
                { x, y },
                { x + w, y },
                { x + w, y + h },
                { x, y + h }
        };
        // 旋转中心（y-up）与旋转角（y-up 逆时针，弧度）
        float ox = px, oy = upY;
        float rot = flipX ? (float) Math.PI - ang : -ang;
        float cos = (float) Math.cos(rot), sin = (float) Math.sin(rot);
        float[] xs = new float[4], ys = new float[4];
        for (int i = 0; i < 4; i++) {
            float dx = corners[i][0] - ox;
            float dy = corners[i][1] - oy;
            float rx = dx * cos - dy * sin;
            float ry = dx * sin + dy * cos;
            xs[i] = ox + rx;
            ys[i] = vh - (oy + ry); // 转回屏幕坐标（y 向下）
        }
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            drawLine(batch, vh, xs[i], ys[i], xs[j], ys[j], c);
        }
    }

    /** 屏幕坐标（y 向下）绘制两点间线段 */
    private void drawLine(SpriteBatch batch, int vh, float x1, float y1, float x2, float y2, Color c) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        float ang = (float) Math.atan2(dy, dx);
        drawRotRect(batch, vh, x1, y1, ang, len, 1.5f, c);
    }
}
