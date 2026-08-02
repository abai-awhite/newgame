package entity;

import block.Block;
import main.*;
import main.util.CubicSplineInterpolator;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * 玩家类，继承自实体（entity）。
 * 负责玩家的位置管理、移动逻辑、动画以及插值渲染。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>逻辑线程（Tick）更新 currentX/currentY（真实逻辑位置）</li>
 *   <li>渲染线程（Gamepanel）根据时间比例 alpha 计算出 renderX/renderY（平滑绘制位置）</li>
 *   <li>通过插值消除因逻辑帧率低而产生的卡顿感</li>
 * </ul>
 *
 * <h3>插值模式</h3>
 * <p>支持两种插值模式（通过 CubicSplineInterpolator.setMode() 切换）：</p>
 * <ul>
 *   <li><b>LINEAR</b>：线性插值，快速但有速度突变</li>
 *   <li><b>CATMULL_ROM</b>：三次样条插值，光滑连续，避免速度突变</li>
 * </ul>
 *
 * <h3>位置历史缓冲区</h3>
 * <p>Catmull-Rom 样条需要4个控制点，使用 HISTORY_SIZE=4 的循环缓冲区存储历史位置。
 * 在渲染时，从缓冲区中取出 P1~P3，并基于速度外推 P4。</p>
 *
 * @see CubicSplineInterpolator
 */
public class Player extends entity {

    private static final double EPSILON = 1e-6;
    private static final double VELOCITY_EPSILON = 0.001;

    Gamepanel panel;      // 游戏主面板引用
    Keyboard VK;          // 键盘输入引用
    public AutoJumpSystem autoJumpSystem;   // 自动跳跃系统

    // ==================== 插值相关字段 ====================

    /**
     * 位置历史缓冲区，用于 Catmull-Rom 三次样条插值。
     * 索引结构：[0]=最旧, [1]=旧, [2]=当前, [3]=预测下一位置
     * 采用循环缓冲区的设计，避免频繁的数组元素移动。
     */
    private static final int HISTORY_SIZE = 4;
    private double[] posHistoryX = new double[HISTORY_SIZE];
    private double[] posHistoryY = new double[HISTORY_SIZE];
    private int historyIndex = 2;

    /**
     * 当前逻辑位置（真实游戏坐标），由逻辑线程（Tick）更新，渲染线程只读
     */
    public double currentX, currentY;

    /**
     * 上一次逻辑更新时的位置，用于线性插值的起点，由逻辑线程在每帧更新前保存
     */
    double previousX, previousY;

    /**
     * 最终绘制位置，由渲染线程根据插值比例计算出，仅用于 paintComponent
     */
    public double renderX;
    public double renderY;

    // ==================== 跳跃与重力字段 ====================
    double velocityY = 0;
    boolean onGround = false;
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

    public int getDashCharges() { return dashCharges; }
    public int getDashMax() { return DASH_MAX; }

    /**
     * 尝试冲刺：消耗一次充能，向当前朝向方向突进。
     * 条件：有剩余充能且不在冷却中。
     */
    private void tryDash() {
        if (dashCharges > 0) {
            dashCharges--;
            if (direction.equals("left")) {
                dashVelocityX = -DASH_SPEED;
            } else if (direction.equals("right")) {
                dashVelocityX = DASH_SPEED;
            } else {
                dashVelocityX = 0;
            }
        }
    }

    // ==================== 冲刺相关字段 ====================
    private static final int DASH_MAX = 5;
    private static final double DASH_SPEED = 64;
    private int dashCharges = DASH_MAX;
    private boolean dashKeyHeld = false;
    private double dashVelocityX = 0;
    private int dashRechargeCounter = 0;

    // ==================== 构造与初始化 ====================
    /**
     * 构造玩家对象
     * @param panel 游戏主面板，用于获取上下文信息
     * @param VK    键盘输入状态
     */
    public Player(Gamepanel panel, Keyboard VK) {
        this.panel = panel;
        this.VK = VK;
        move();            // 设置初始位置和速度
        getplayerimage();  // 加载所有动作图片
    }

    /**
     * 初始化玩家的位置和速度
     * 调用时机：构造方法中执行一次
     */
    public void move() {
        // 出生点 X：靠左一些
        currentX = 100;

        // 出生点 Y：让玩家站在地面基准高度（世界中部）减去一个 tile 的高度，
        // 这样玩家的脚刚好踩在草地上（假设玩家图片高度为 tilesize）
        currentY = ((double) Gamepanel.WORLD_HEIGHT_TILES / 2) * Gamepanel.titlesize - Gamepanel.titlesize;

        previousX = currentX;
        previousY = currentY;

        // 初始化位置历史缓冲区（全部设为初始位置）
        for (int i = 0; i < HISTORY_SIZE; i++) {
            posHistoryX[i] = currentX;
            posHistoryY[i] = currentY;
        }

        // 每逻辑帧移动像素数
        speed = 16;

        // 初始化自动跳跃系统
        autoJumpSystem = new AutoJumpSystem(panel);
    }

    // ==================== 逻辑线程调用的方法 ====================
    /**
     * 在每次逻辑更新（Tick）之前调用，将当前逻辑位置保存到 previousX/previousY
     * 这样 interpolate() 才能拥有正确的插值起点。
     * 调用顺序：retick() → update()
     */
    public void retick() {
        // 保存当前位置到历史缓冲区（循环写入）
        historyIndex = (historyIndex + 1) % HISTORY_SIZE;
        posHistoryX[historyIndex] = currentX;
        posHistoryY[historyIndex] = currentY;

        previousX = currentX;
        previousY = currentY;
    }

    /**
     * 玩家核心逻辑更新：读取键盘输入，修改当前逻辑位置（currentX/currentY），并更新动画计数器。
     * 采用轴分离碰撞检测（先 X 后 Y），与实心方块发生碰撞时自动吸附到方块边界。
     * 该函数仅由逻辑线程（Tick）调用，频率固定为 Tick.tick Hz。
     */
    public void update() {
        double dx = 0;
        direction = "null";

        if (VK.a) { direction = "left";  dx -= speed; }
        if (VK.d) { direction = "right"; dx += speed; }

        // --- 冲刺：Alt 按下触发，有充能即可突进 ---
        if (VK.alt && !dashKeyHeld && dashCharges > 0) {
            tryDash();
        }
        dashKeyHeld = VK.alt;

        // --- 跳跃：地面上可一跳，空中可二段跳 ---
        boolean jumpPressed = VK.space || VK.w;
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
        if ((VK.a || VK.d) && onGround) {
            if (!autoJumpSystem.isAutoJumping()) {
                // 正常水平移动，不重置
            }
        }
        if (!VK.a && !VK.d && !jumpPressed) {
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
        double colW = Gamepanel.titlesize - 2 * inset;
        double colH = Gamepanel.titlesize - 2 * inset;

        // --- X 轴移动与碰撞 ---
        dx += dashVelocityX;
        dashVelocityX = 0;
        if (dx != 0) {
            double newX = currentX + dx;
            AABB box = new AABB(newX + inset, currentY + inset, colW, colH);
            int tStartX = (int) Math.floor(box.x / Gamepanel.titlesize);
            int tEndX   = (int) Math.floor((box.x + box.width) / Gamepanel.titlesize - EPSILON);
            int tStartY = Math.max(0, (int) Math.floor(box.y / Gamepanel.titlesize));
            int tEndY   = Math.min(Gamepanel.WORLD_HEIGHT_TILES - 1, (int) Math.floor((box.y + box.height) / Gamepanel.titlesize - EPSILON));

            boolean hit = false;
            boolean triggeredAutoJump = false;
            for (int ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (int tx = tStartX; tx <= tEndX && !hit; tx++) {
                    int type = panel.infiniteMap.getTileType(tx, ty);
                    Block b = Block.fromId(type);
                    if (b != null && b.isSolid()) {
                        // 检测碰撞时是否需要自动跳跃
                        boolean shouldAutoJump = autoJumpSystem.handleCollisionTrigger(
                            currentX, currentY, dx, velocityY, onGround,
                            jumpPhase.equals("first") || jumpPhase.equals("double"),
                            VK.s, panel.infiniteMap
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
                                newX = tx * Gamepanel.titlesize - inset - colW;
                            } else {
                                newX = (tx + 1) * Gamepanel.titlesize - inset;
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
            int tStartX = (int) Math.floor(box.x / Gamepanel.titlesize);
            int tEndX   = (int) Math.floor((box.x + box.width) / Gamepanel.titlesize - EPSILON);
            int tStartY = Math.max(0, (int) Math.floor(box.y / Gamepanel.titlesize));
            int tEndY   = Math.min(Gamepanel.WORLD_HEIGHT_TILES - 1, (int) Math.floor((box.y + box.height) / Gamepanel.titlesize - EPSILON));

            boolean hit = false;
            for (int ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (int tx = tStartX; tx <= tEndX && !hit; tx++) {
                    int type = panel.infiniteMap.getTileType(tx, ty);
                    Block b = Block.fromId(type);
                    if (b != null && b.isSolid()) {
                        if (velocityY > VELOCITY_EPSILON) {
                            // 下落时检测到碰撞，检查是否需要着陆优化
                            if (autoJumpSystem.isAutoJumping() && onGround) {
                                autoJumpSystem.resetState();
                            }
                            newY = ty * Gamepanel.titlesize - inset - colH;
                        } else {
                            newY = (ty + 1) * Gamepanel.titlesize - inset;
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
            int tileX = (int) Math.floor((currentX + inset + colW / 2) / Gamepanel.titlesize);
            int tileY = (int) Math.floor(footY / Gamepanel.titlesize);
            if (tileY >= 0 && tileY < Gamepanel.WORLD_HEIGHT_TILES) {
                int type = panel.infiniteMap.getTileType(tileX, tileY);
                Block b = Block.fromId(type);
                if (b != null && b.isSolid()) {
                    double blockTop = tileY * Gamepanel.titlesize;
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
        int maxY = Gamepanel.WORLD_HEIGHT_TILES * Gamepanel.titlesize - Gamepanel.titlesize;
        currentY = Math.min(Math.max(currentY, 0), maxY);

        // --- 站立动画计数器 ---
        incrementer++;
        final int a = 2 * Tick.tick;
        final int b = Tick.tick / 16;
        if (incrementer > a - 3 * b) { if (counter == 1) counter = 2; }
        if (incrementer > a - 2 * b) { if (counter == 2) counter = 3; }
        if (incrementer > a - b)     { if (counter == 3) counter = 4; }
        if (incrementer > a) {
            if (counter == 4) counter = 1;
            incrementer = 0;
        }
    }

    // ==================== 渲染线程调用的方法 ====================
    /**
     * 根据插值比例 alpha，计算本次渲染应该绘制的平滑位置（renderX/renderY）
     *
     * <h3>线性插值公式</h3>
     * render = previous + (current - previous) * alpha
     *
     * <h3>三次样条插值（Catmull-Rom）</h3>
     * 使用4个控制点生成光滑曲线，在游戏运动中能更好地保持速度连续性。
     *
     * @param alpha 时间比例，范围 0~1
     *              0 表示逻辑尚未更新（绘制在上一次逻辑位置）
     *              1 表示正好处于下一次逻辑更新时刻（绘制在当前逻辑位置）
     *              中间值表示平滑过渡
     */
    public void interpolate(float alpha) {
        if (CubicSplineInterpolator.getMode() == CubicSplineInterpolator.InterpolationMode.CATMULL_ROM) {
            // 计算历史缓冲区中各点的索引
            // historyIndex 指向最新写入的位置（即 currentX/currentY）
            int idx0 = (historyIndex - 3 + HISTORY_SIZE) % HISTORY_SIZE; // P0: 最旧
            int idx1 = (historyIndex - 2 + HISTORY_SIZE) % HISTORY_SIZE; // P1: 旧
            int idx2 = (historyIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE; // P2: 当前(previous)
            int idx3 = historyIndex;                                      // P3: 最新(current)

            double p0x = posHistoryX[idx0], p0y = posHistoryY[idx0];
            double p1x = posHistoryX[idx1], p1y = posHistoryY[idx1];
            double p2x = posHistoryX[idx2], p2y = posHistoryY[idx2];
            double p3x = posHistoryX[idx3], p3y = posHistoryY[idx3];

            // 使用 Catmull-Rom 样条插值
            // alpha 0.0 → p2 (previous), alpha 1.0 → p3 (current)
            // 但 Catmull-Rom 的自然参数是 0→1 对应 P1→P2
            // 所以我们实际使用 p1, p2, p3, 外推 p4
            double p4x = p3x + (p3x - p2x); // 预测下一位置（基于速度外推）
            double p4y = p3y + (p3y - p2y);

            double[] result = CubicSplineInterpolator.catmullRom2D(
                alpha, p1x, p2x, p3x, p4x, p1y, p2y, p3y, p4y
            );
            renderX = result[0];
            renderY = result[1];
        } else {
            // 线性插值（默认）
            renderX = previousX + (currentX - previousX) * alpha;
            renderY = previousY + (currentY - previousY) * alpha;
        }
    }

    // ==================== 图片资源加载 ====================
    /**
     * 加载玩家不同方向、不同帧的 PNG 图片
     * 图片放在资源目录 /player/ 下
     */
    public void getplayerimage() {
        try {
            none1 = ImageIO.read(getClass().getResourceAsStream("/player/player-1.png"));
            none2 = ImageIO.read(getClass().getResourceAsStream("/player/player-2.png"));
            none3 = ImageIO.read(getClass().getResourceAsStream("/player/player-3.png"));
            down1 = ImageIO.read(getClass().getResourceAsStream("/player/player-down-1.png"));
            up1 = ImageIO.read(getClass().getResourceAsStream("/player/player-up-1.png"));
            right1 = ImageIO.read(getClass().getResourceAsStream("/player/player-r-1.png"));
            left1 = ImageIO.read(getClass().getResourceAsStream("/player/player-l-1.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ==================== 绘制方法 ====================
    /**
     * 绘制玩家到屏幕
     * 根据当前方向（direction）和动画计数器（counter）选择对应的图片，
     * 并使用插值后的 renderX/renderY 作为绘制坐标。
     *
     * @param g2 Graphics2D 图形上下文
     */
    public void paintComponent(Graphics2D g2) {
        BufferedImage image = null;

        switch (direction) {
            case "up":
                image = up1;
                break;
            case "down":
                image = down1;
                break;
            case "right":
                image = right1;
                break;
            case "left":
                image = left1;
                break;
            case "null":
                if (counter == 1) image = none1;
                if (counter == 2) image = none2;
                if (counter == 3) image = none3;
                if (counter == 4) image = none2;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + direction);
        }

        g2.drawImage(image, (int) renderX, (int) renderY, Gamepanel.titlesize, Gamepanel.titlesize, null);

        int tileSize = Gamepanel.titlesize;
        int barWidth = tileSize;
        int barHeight = 4;
        int barX = (int) renderX;
        int barY = (int) renderY + tileSize + 2;
        int filled = (int) ((double) dashCharges / DASH_MAX * barWidth);

        g2.setColor(new Color(30, 30, 30, 200));
        g2.fillRect(barX, barY, barWidth, barHeight);
        g2.setColor(new Color(80, 160, 255));
        g2.fillRect(barX, barY, filled, barHeight);
        g2.setColor(new Color(120, 200, 255));
        g2.drawRect(barX, barY, barWidth, barHeight);
    }

    /**
     * 设置玩家的位置（用于加载存档）。
     * 同时更新 currentX/Y 和 previousX/Y，确保插值正确。
     * 
     * @param x 玩家X坐标（像素）
     * @param y 玩家Y坐标（像素）
     */
    public void setPosition(double x, double y) {
        this.currentX = x;
        this.currentY = y;
        this.previousX = x;
        this.previousY = y;
        this.renderX = x;
        this.renderY = y;
        
        // 更新位置历史缓冲区
        for (int i = 0; i < HISTORY_SIZE; i++) {
            posHistoryX[i] = x;
            posHistoryY[i] = y;
        }
    }
}