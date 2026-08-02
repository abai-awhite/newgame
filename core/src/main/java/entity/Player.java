package entity;

import block.Block;
import main.world.InfiniteMap;

/**
 * 玩家类（纯逻辑类，不含渲染代码）。
 * <p>
 * 负责玩家的位置管理、移动逻辑、碰撞检测、跳跃、冲刺以及插值渲染坐标计算。
 * 所有逻辑由 update() 驱动，渲染坐标由 interpolate() 计算。
 * </p>
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>逻辑更新（update）修改 currentX/currentY（真实逻辑位置）</li>
 *   <li>渲染时根据时间比例 alpha 计算出 renderX/renderY（平滑绘制位置）</li>
 *   <li>通过线性插值消除因逻辑帧率低而产生的卡顿感</li>
 * </ul>
 */
public class Player {

    /** 世界总高度（格子数）。 */
    public static final int WORLD_HEIGHT_TILES = 1024;

    private static final double EPSILON = 1e-6;
    private static final double VELOCITY_EPSILON = 0.001;

    /** 地图引用，用于碰撞检测中的方块查询。 */
    private final InfiniteMap infiniteMap;

    /** 方块大小（像素）。 */
    private final int tileSize;

    // ==================== 输入状态（由外部设置） ====================
    /** W 键按下状态。 */
    public boolean keyW;
    /** A 键按下状态。 */
    public boolean keyA;
    /** S 键按下状态。 */
    public boolean keyS;
    /** D 键按下状态。 */
    public boolean keyD;
    /** 空格键按下状态。 */
    public boolean keySpace;
    /** Alt 键按下状态。 */
    public boolean keyAlt;

    // ==================== 位置与插值字段 ====================
    /** 当前逻辑位置 X（真实游戏坐标），由 update() 更新。 */
    public double currentX;
    /** 当前逻辑位置 Y（真实游戏坐标），由 update() 更新。 */
    public double currentY;
    /** 上一次逻辑更新时的位置 X，用于线性插值的起点。 */
    double previousX;
    /** 上一次逻辑更新时的位置 Y，用于线性插值的起点。 */
    double previousY;
    /** 最终绘制位置 X，由 interpolate() 计算。 */
    public double renderX;
    /** 最终绘制位置 Y，由 interpolate() 计算。 */
    public double renderY;

    // ==================== 跳跃与重力字段 ====================
    public double velocityY = 0;
    public boolean onGround = false;
    final double jumpSpeed = 14;
    final double gravity = 1;
    final double maxFallSpeed = 16;

    // ==================== 二段跳相关字段 ====================
    private static final int MAX_JUMPS = 2;
    private static final double DOUBLE_JUMP_SPEED = 11;
    private static final int JUMP_COOLDOWN = 8;
    private int jumpCount = 0;
    private int jumpCooldownCounter = 0;
    private boolean jumpKeyHeld = false;
    public String jumpPhase = "none";

    // ==================== 冲刺相关字段 ====================
    private static final int DASH_MAX = 5;
    private static final double DASH_SPEED = 64;
    private int dashCharges = DASH_MAX;
    private boolean dashKeyHeld = false;
    private double dashVelocityX = 0;
    private int dashRechargeCounter = 0;

    // ==================== 移动与动画字段 ====================
    double speed = 16;
    public String direction = "null";
    public int incrementer = 0;
    public int counter = 1;

    /** 自动跳跃系统。 */
    public AutoJumpSystem autoJumpSystem;

    // ==================== 构造与初始化 ====================

    /**
     * 构造玩家对象。
     *
     * @param infiniteMap 地图引用，用于碰撞检测
     * @param tileSize    方块大小（像素）
     */
    public Player(InfiniteMap infiniteMap, int tileSize) {
        this.infiniteMap = infiniteMap;
        this.tileSize = tileSize;

        // 出生点 X：靠左一些
        currentX = 100;

        // 出生点 Y：让玩家站在地面基准高度（世界中部）减去一个 tile 的高度
        currentY = ((double) WORLD_HEIGHT_TILES / 2) * tileSize - tileSize;

        previousX = currentX;
        previousY = currentY;
        renderX = currentX;
        renderY = currentY;

        // 初始化自动跳跃系统
        autoJumpSystem = new AutoJumpSystem(tileSize);
    }

    // ==================== 公共方法 ====================

    /**
     * 获取冲刺最大充能数。
     *
     * @return 最大冲刺充能数
     */
    public int getDashMax() {
        return DASH_MAX;
    }

    /**
     * 获取当前冲刺充能数。
     *
     * @return 当前冲刺充能数
     */
    public int getDashCharges() {
        return dashCharges;
    }

    /**
     * 在每次逻辑更新之前调用，将当前逻辑位置保存到 previousX/previousY。
     * 调用顺序：retick() → update()
     */
    public void retick() {
        previousX = currentX;
        previousY = currentY;
    }

    /**
     * 玩家核心逻辑更新：读取输入状态，修改当前逻辑位置（currentX/currentY），
     * 并更新动画计数器。
     * 采用轴分离碰撞检测（先 X 后 Y），与实心方块发生碰撞时自动吸附到方块边界。
     */
    public void update() {
        double dx = 0;
        direction = "null";

        if (keyA) { direction = "left";  dx -= speed; }
        if (keyD) { direction = "right"; dx += speed; }

        // --- 冲刺：Alt 按下触发，有充能即可突进 ---
        if (keyAlt && !dashKeyHeld && dashCharges > 0) {
            tryDash();
        }
        dashKeyHeld = keyAlt;

        // --- 跳跃：地面上可一跳，空中可二段跳 ---
        boolean jumpPressed = keySpace || keyW;
        if (jumpPressed && !jumpKeyHeld && jumpCooldownCounter == 0 && jumpCount < MAX_JUMPS) {
            autoJumpSystem.resetState();

            jumpCount++;
            if (jumpCount == 1) {
                velocityY = -jumpSpeed;
                jumpPhase = "first";
            } else if (jumpCount == 2) {
                velocityY = -DOUBLE_JUMP_SPEED;
                jumpPhase = "double";
            }
            jumpCooldownCounter = JUMP_COOLDOWN;
            onGround = false;
        }
        jumpKeyHeld = jumpPressed;

        // --- 玩家主动控制时重置自动跳跃 ---
        if ((keyA || keyD) && onGround) {
            // 正常水平移动，不重置
        }
        if (!keyA && !keyD && !jumpPressed) {
            autoJumpSystem.resetState();
        }

        // --- 跳跃冷却递减 ---
        if (jumpCooldownCounter > 0) {
            jumpCooldownCounter--;
        }

        // --- 冲刺充能回复（每100tick回复1点，最多5点）---
        if (dashCharges < DASH_MAX && dashRechargeCounter++ >= 100) {
            dashCharges++;
            dashRechargeCounter = 0;
        }

        // --- 重力 ---
        velocityY += gravity;
        if (velocityY > maxFallSpeed) velocityY = maxFallSpeed;

        double inset = 3;
        double colW = tileSize - 2 * inset;
        double colH = tileSize - 2 * inset;

        // --- X 轴移动与碰撞 ---
        dx += dashVelocityX;
        dashVelocityX = 0;
        if (dx != 0) {
            double newX = currentX + dx;
            AABB box = new AABB(newX + inset, currentY + inset, colW, colH);
            int tStartX = (int) Math.floor(box.x / tileSize);
            int tEndX   = (int) Math.floor((box.x + box.width) / tileSize - EPSILON);
            int tStartY = Math.max(0, (int) Math.floor(box.y / tileSize));
            int tEndY   = Math.min(WORLD_HEIGHT_TILES - 1, (int) Math.floor((box.y + box.height) / tileSize - EPSILON));

            boolean hit = false;
            boolean triggeredAutoJump = false;
            for (int ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (int tx = tStartX; tx <= tEndX && !hit; tx++) {
                    int type = infiniteMap.getTileType(tx, ty);
                    Block b = Block.fromId(type);
                    if (b != null && b.isSolid()) {
                        // 检测碰撞时是否需要自动跳跃
                        boolean shouldAutoJump = autoJumpSystem.handleCollisionTrigger(
                            currentX, currentY, dx, velocityY, onGround,
                            jumpPhase.equals("first") || jumpPhase.equals("double"),
                            keyS, infiniteMap
                        );

                        if (shouldAutoJump) {
                            // 执行自动跳跃：使用 AutoJumpSystem 计算好的速度
                            double requiredVY = autoJumpSystem.getRequiredVelocityY();

                            if (requiredVY != 0) {
                                velocityY = requiredVY;

                                if (!onGround) {
                                    jumpCount = 1;
                                    jumpPhase = "auto-first";
                                } else {
                                    onGround = false;
                                }
                            }
                            hit = true;
                            triggeredAutoJump = true;
                            // 触发跳跃时保持 currentX 不变，避免卡入方块
                        } else {
                            if (dx > 0) {
                                newX = tx * tileSize - inset - colW;
                            } else {
                                newX = (tx + 1) * tileSize - inset;
                            }
                            newX = Math.round(newX);
                            hit = true;
                        }
                    }
                }
            }
            // 只有当没有触发自动跳跃时才更新 currentX
            if (!triggeredAutoJump) {
                currentX = newX;
            }
        }

        // --- Y 轴移动与碰撞（重力+跳跃） ---
        {
            double newY = currentY + velocityY;
            AABB box = new AABB(currentX + inset, newY + inset, colW, colH);
            int tStartX = (int) Math.floor(box.x / tileSize);
            int tEndX   = (int) Math.floor((box.x + box.width) / tileSize - EPSILON);
            int tStartY = Math.max(0, (int) Math.floor(box.y / tileSize));
            int tEndY   = Math.min(WORLD_HEIGHT_TILES - 1, (int) Math.floor((box.y + box.height) / tileSize - EPSILON));

            boolean hit = false;
            for (int ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (int tx = tStartX; tx <= tEndX && !hit; tx++) {
                    int type = infiniteMap.getTileType(tx, ty);
                    Block b = Block.fromId(type);
                    if (b != null && b.isSolid()) {
                        if (velocityY > VELOCITY_EPSILON) {
                            // 下落时检测到碰撞，检查是否需要着陆优化
                            if (autoJumpSystem.isAutoJumping() && onGround) {
                                autoJumpSystem.resetState();
                            }
                            newY = ty * tileSize - inset - colH;
                        } else {
                            newY = (ty + 1) * tileSize - inset;
                        }
                        newY = Math.round(newY);
                        hit = true;
                    }
                }
            }
            currentY = newY;
            if (hit) velocityY = 0;
        }

        // --- 地面检测：检查脚下方块是否实心 ---
        boolean wasOnGround = onGround;
        onGround = false;
        {
            double footY = currentY + inset + colH;
            int tileX = (int) Math.floor((currentX + inset + colW / 2) / tileSize);
            int tileY = (int) Math.floor(footY / tileSize);
            if (tileY >= 0 && tileY < WORLD_HEIGHT_TILES) {
                int type = infiniteMap.getTileType(tileX, tileY);
                Block b = Block.fromId(type);
                if (b != null && b.isSolid()) {
                    double blockTop = tileY * tileSize;
                    if (Math.abs(footY - blockTop) < EPSILON || footY < blockTop) {
                        onGround = true;
                    }
                }
            }
        }
        if (onGround && !wasOnGround) {
            jumpCount = 0;
            jumpPhase = "none";
        }

        // --- 世界边界限制 ---
        int maxY = WORLD_HEIGHT_TILES * tileSize - tileSize;
        currentY = Math.min(Math.max(currentY, 0), maxY);

        // --- 站立动画计数器 ---
        incrementer++;
        final int a = 2 * 32; // 使用默认 tick=32
        final int b = 32 / 16;
        if (incrementer > a - 3 * b) { if (counter == 1) counter = 2; }
        if (incrementer > a - 2 * b) { if (counter == 2) counter = 3; }
        if (incrementer > a - b)     { if (counter == 3) counter = 4; }
        if (incrementer > a) {
            if (counter == 4) counter = 1;
            incrementer = 0;
        }
    }

    /**
     * 根据插值比例 alpha，计算本次渲染应该使用的平滑位置（renderX/renderY）。
     * <p>
     * 使用线性插值公式：render = previous + (current - previous) * alpha
     * </p>
     *
     * @param alpha 时间比例，范围 0~1
     *              0 表示逻辑尚未更新（绘制在上一次逻辑位置）
     *              1 表示正好处于下一次逻辑更新时刻（绘制在当前逻辑位置）
     *              中间值表示平滑过渡
     */
    public void interpolate(float alpha) {
        renderX = previousX + (currentX - previousX) * alpha;
        renderY = previousY + (currentY - previousY) * alpha;
    }

    /**
     * 设置玩家的位置（用于加载存档）。
     * 同时更新 currentX/Y、previousX/Y 和 renderX/Y，确保插值正确。
     *
     * @param x 玩家 X 坐标（像素）
     * @param y 玩家 Y 坐标（像素）
     */
    public void setPosition(double x, double y) {
        this.currentX = x;
        this.currentY = y;
        this.previousX = x;
        this.previousY = y;
        this.renderX = x;
        this.renderY = y;
    }

    // ==================== 私有方法 ====================

    /**
     * 尝试冲刺：消耗一次充能，向当前朝向方向突进。
     * 条件：有剩余充能且不在冷却中。
     */
    private void tryDash() {
        if (dashCharges > 0) {
            dashCharges--;
            if ("left".equals(direction)) {
                dashVelocityX = -DASH_SPEED;
            } else if ("right".equals(direction)) {
                dashVelocityX = DASH_SPEED;
            } else {
                dashVelocityX = 0;
            }
        }
    }
}
