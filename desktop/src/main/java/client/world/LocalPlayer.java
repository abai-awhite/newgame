package client.world;

import client.data.BlocksData;

/**
 * 本地玩家物理（移植 game.js 的 player 对象 + updatePlayer/tryDash/checkGap）。
 * 自动跨上（一格高台阶）在 tick 的 X 碰撞中内联处理，不提前半步预测。
 * 所有逻辑由 tick() 驱动（32Hz）。
 */
public class LocalPlayer {

    public static final float TILE = 32f;
    public static final int WORLD_HEIGHT_TILES = 1024;

    // 位置（逻辑）
    public float x, y, prevX, prevY, renderX, renderY;
    public float vx, vy;
    public boolean onGround;
    public String direction = "null";
    public int animFrame = 1, incrementer = 0;

    // 跳跃
    public int jumpCount = 0;
    public String jumpPhase = "none";
    public int jumpCooldown = 0;
    public boolean jumpKeyHeld = false;

    // 冲刺
    public int dashCharges = 5, dashMax = 5;
    public boolean dashKeyHeld = false;
    public float dashVX = 0;
    public int dashRecharge = 0;

    // 快捷栏槽位
    public int slot = 0;

    // 自动跨步
    public boolean autoJumpActive = false;
    public int autoJumpLastDir = 0;
    public int autoJumpRecovery = 0;
    public float autoJumpRequiredVY = 0;

    // 跨沟状态
    public CrossGap crossGap = null;

    public static class CrossGap {
        public int dir;
        public float startEdge, endEdge;

        public CrossGap(int dir, float startEdge, float endEdge) {
            this.dir = dir;
            this.startEdge = startEdge;
            this.endEdge = endEdge;
        }
    }

    public void reset() {
        x = 100;
        y = WORLD_HEIGHT_TILES / 2f * TILE - TILE;
        prevX = x; prevY = y; renderX = x; renderY = y;
        vy = 0; vx = 0;
        onGround = false;
        direction = "null";
        jumpCount = 0; jumpPhase = "none";
        dashCharges = dashMax = 5;
        animFrame = 1; incrementer = 0;
        slot = 0;
        crossGap = null;
        autoJumpActive = false;
    }

    /** 输入状态（true=按住） */
    public static class Keys {
        public boolean w, a, s, d, space, alt;
    }

    /** 物理更新（移植 game.js updatePlayer） */
    public void tick(Keys keys, ClientWorld world, BlocksData blocks, boolean autoStepEnabled) {
        // 水中判定：玩家中心格或脚底格为水（水=非实心，可穿行；水中减速/上浮/禁跳跃冲刺）
        boolean inWater = world.getTile((int) Math.floor((x + TILE / 2) / TILE),
                (int) Math.floor((y + TILE / 2) / TILE)) == BlocksData.T_WATER
                || world.getTile((int) Math.floor((x + TILE / 2) / TILE),
                (int) Math.floor((y + TILE - 1) / TILE)) == BlocksData.T_WATER;

        // 方向与水平输入（水中减速）
        float dx = 0;
        direction = "null";
        float walkSpeed = inWater ? 8 : 16;
        if (keys.a) { direction = "left"; dx -= walkSpeed; }
        if (keys.d) { direction = "right"; dx += walkSpeed; }

        // 冲刺（水中禁用：不发起新冲刺，已进入水中立即清除冲刺惯性，避免从岸上冲刺滑入水里继续滑行）
        if (inWater) {
            dashVX = 0;
            dashFx = 0;
        } else if (keys.alt && !dashKeyHeld && dashCharges > 0) {
            tryDash();
        }
        dashKeyHeld = keys.alt;

        // 跳跃（水中禁用一跳/二段跳，空格改为上浮）
        boolean jumpPressed = keys.space;
        if (inWater) {
            if (jumpPressed) {
                vy -= 1.2f;
                if (vy < -8) vy = -8;
            }
            jumpPhase = "none";
        } else if (jumpPressed && !jumpKeyHeld && jumpCooldown == 0 && jumpCount < 2) {
            autoJumpActive = false;
            jumpCount++;
            if (jumpCount == 1) {
                vy = -14;
                jumpPhase = "first";
            } else {
                vy = -11;
                jumpPhase = "double";
            }
            jumpCooldown = 8;
            onGround = false;
        }
        jumpKeyHeld = jumpPressed;

        if (!keys.a && !keys.d && !jumpPressed) {
            autoJumpActive = false;
        }
        if (jumpCooldown > 0) jumpCooldown--;

        // 冲刺充能回复
        if (dashCharges < dashMax && dashRecharge++ >= 100) {
            dashCharges++;
            dashRecharge = 0;
        }

        // 重力（水中减半、落速上限降低；浮力抵消部分下沉）
        if (inWater) {
            vy += 0.5f;
            if (vy > 6) vy = 6;
        } else {
            vy += 1;
            if (vy > 16) vy = 16;
        }

        final int inset = 3;
        final float colW = TILE - 2 * inset;
        final float colH = TILE - 2 * inset;

        // --- X 轴移动与碰撞 ---
        dx += dashVX;
        dashVX = 0;

        // 跨沟检测（独立于自动跨步；跨上在下方 X 碰撞时内联处理）
        if (dx != 0) checkGap(dx, world, blocks);
        else crossGap = null;

        if (dx != 0) {
            float newX = x + dx;
            float boxX = newX + inset;
            float boxY = y + inset;
            int tStartX = (int) Math.floor(boxX / TILE);
            int tEndX = (int) Math.floor((boxX + colW) / TILE - 1e-6);
            int tStartY = Math.max(0, (int) Math.floor(boxY / TILE));
            int tEndY = Math.min(WORLD_HEIGHT_TILES - 1, (int) Math.floor((boxY + colH) / TILE - 1e-6));
            int groundTileY = (int) Math.floor((y + inset + colH) / TILE);
            float footY = y + inset + colH;

            // 自动跨上（重写：不提前半步预测，只在水平移动真正撞到低台阶时抬脚跨上）
            outer:
            for (int ty = tStartY; ty <= tEndY; ty++) {
                for (int tx = tStartX; tx <= tEndX; tx++) {
                    if (!blocks.isSolid(world.getTile(tx, ty))) continue;
                    int dir = dx > 0 ? 1 : -1;
                    // 撞到的台阶顶面在脚上方 1.2 格内（ty 须在脚所在行之上）+ 头顶空间足够 → 直接跨上，水平移动不中断。
                    // 净空只查台阶正上方 (tx, ty-1)：连续阶梯中前方一格头顶 (tx+dir, ty-1) 正是下一级台阶，
                    // 若也检查会让第一级台阶永远无法跨上（被挡在阶梯前）。
                    float lift = footY - ty * TILE;
                    if (autoStepEnabled && !keys.s && onGround
                            && ty < groundTileY && lift > 0 && lift <= 1.2f * TILE
                            && !blocks.isSolid(world.getTile(tx, ty - 1))) {
                        // 脚底正好贴台阶顶面（colH = TILE-2*inset，不能用整格抬升，否则悬空 3px、onGround 被重置为 false，无法连续跨步）
                        y = ty * TILE - inset - colH;
                        prevY = y;
                        vy = 0;
                        onGround = true;
                        autoJumpActive = false;
                        break outer;
                    }
                    if (dx > 0) newX = tx * TILE - inset - colW;
                    else newX = (tx + 1) * TILE - inset;
                    newX = Math.round(newX);
                    break outer;
                }
            }
            x = newX;
        }

        // --- Y 轴移动与碰撞 ---
        {
            boolean suppressY = false;
            if (crossGap != null) {
                float pLeft = x + inset;
                float pRight = x + inset + colW;
                if (pRight > crossGap.startEdge && pLeft < crossGap.endEdge) {
                    suppressY = true;
                } else {
                    crossGap = null;
                }
            }

            if (suppressY) {
                vy = 0;
                onGround = true;
            } else {
                float newY = y + vy;
                float boxX = x + inset;
                float boxY = newY + inset;
                int tStartX = (int) Math.floor(boxX / TILE);
                int tEndX = (int) Math.floor((boxX + colW) / TILE - 1e-6);
                int tStartY = Math.max(0, (int) Math.floor(boxY / TILE));
                int tEndY = Math.min(WORLD_HEIGHT_TILES - 1, (int) Math.floor((boxY + colH) / TILE - 1e-6));

                boolean hit = false;
                for (int ty = tStartY; ty <= tEndY && !hit; ty++) {
                    for (int tx = tStartX; tx <= tEndX && !hit; tx++) {
                        if (!blocks.isSolid(world.getTile(tx, ty))) continue;
                        if (vy > 0.001f) {
                            if (autoJumpActive && onGround) autoJumpActive = false;
                            newY = ty * TILE - inset - colH;
                        } else {
                            newY = (ty + 1) * TILE - inset;
                        }
                        newY = Math.round(newY);
                        hit = true;
                    }
                }
                y = newY;
                if (hit) vy = 0;
            }
        }

        // --- 地面检测 ---
        boolean wasOnGround = onGround;
        onGround = false;
        {
            // 检查脚底覆盖的所有列（不只中心列）：跨上台阶瞬间中心可能还没进台阶列，
            // 但右端已踩上台阶，否则 onGround 被置 false 导致下一级台阶无法连续跨上
            float footY = y + inset + colH;
            int tileY = (int) Math.floor(footY / TILE);
            int tlx = (int) Math.floor((x + inset) / TILE);
            int trx = (int) Math.floor((x + inset + colW) / TILE - 1e-6);
            for (int tx = tlx; tx <= trx; tx++) {
                if (tileY >= 0 && tileY < WORLD_HEIGHT_TILES && blocks.isSolid(world.getTile(tx, tileY))) {
                    float blockTop = tileY * TILE;
                    if (Math.abs(footY - blockTop) < 1e-6f || footY < blockTop) {
                        onGround = true;
                        break;
                    }
                }
            }
        }
        // 跨沟中：脚在沟上方（空气），保持"贴地"
        if (crossGap != null) {
            float pLeft = x + inset;
            float pRight = x + inset + colW;
            if (pRight > crossGap.startEdge && pLeft < crossGap.endEdge) onGround = true;
        }
        if (onGround && !wasOnGround) {
            jumpCount = 0;
            jumpPhase = "none";
        }

        // --- 世界边界 ---
        y = Math.min(Math.max(y, 0), WORLD_HEIGHT_TILES * TILE - TILE);

        // --- 站立动画 ---
        incrementer++;
        final int a = 64, b = 2;
        if (incrementer > a - 3 * b && animFrame == 1) animFrame = 2;
        if (incrementer > a - 2 * b && animFrame == 2) animFrame = 3;
        if (incrementer > a - b && animFrame == 3) animFrame = 4;
        if (incrementer > a) {
            if (animFrame == 4) animFrame = 1;
            incrementer = 0;
        }
        if (autoJumpRecovery > 0) autoJumpRecovery--;
        if (dashFx > 0) dashFx--;
    }

    /** 冲刺特效状态：剩余 tick（32Hz 递减），dashFxDir=-1 左 / 1 右 */
    public int dashFx = 0;
    public int dashFxDir = 0;

    private void tryDash() {
        // 只在有水平方向时冲刺（否则白耗充能且无特效）
        if (dashCharges > 0) {
            if ("left".equals(direction)) {
                dashCharges--;
                dashVX = -64;
                dashFx = 10;
                dashFxDir = -1;
            } else if ("right".equals(direction)) {
                dashCharges--;
                dashVX = 64;
                dashFx = 10;
                dashFxDir = 1;
            }
        }
    }

    /**
     * 跨沟检测（移植 game.js checkGap）：
     * 前方 1 格是一格深的小沟、对岸同高时标记跨沟状态，Y 轴阶段保持高度滑过。
     */
    private void checkGap(float dx, ClientWorld world, BlocksData blocks) {
        final int dir = dx > 0 ? 1 : -1;
        final int inset = 3;
        final float colW = TILE - 2 * inset;

        float footY = y + inset + colW;
        int groundTileY = (int) Math.floor(footY / TILE);
        int centerTileX = (int) Math.floor((x + colW / 2) / TILE);
        float frontEdge = dir > 0 ? x + TILE - inset : x + inset;

        int gapX = centerTileX + dir;
        if (blocks.isSolid(world.getTile(gapX, groundTileY))) return;
        if (!blocks.isSolid(world.getTile(gapX, groundTileY + 1))) return;
        int farX = centerTileX + 2 * dir;
        if (!blocks.isSolid(world.getTile(farX, groundTileY))) return;
        for (int k = 0; k <= 2; k++) {
            if (blocks.isSolid(world.getTile(gapX + dir * k, groundTileY - 1))) return;
        }

        float gapEdge = dir > 0 ? gapX * TILE : (gapX + 1) * TILE;
        float dist = dir > 0 ? gapEdge - frontEdge : frontEdge - gapEdge;
        if (dist <= TILE + 2 && dist >= -4) {
            crossGap = new CrossGap(dir, gapX * TILE, (gapX + 1) * TILE);
        }
    }
}
