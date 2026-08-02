package entity;

import block.Block;
import main.Gamepanel;
import main.world.InfiniteMap;

/**
 * 角色自动跳跃系统。
 *
 * <h3>职责</h3>
 * <p>当角色碰到前方一格高的方块时，自动计算并执行精准跳跃，使角色恰好落在方块顶部。</p>
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li><b>碰撞检测</b>：在 X 轴移动过程中实时检测前方是否存在高一格或以上的实心方块</li>
 *   <li><b>轨迹预测</b>：使用物理公式精确计算跳跃抛物线，确保落点准确</li>
 *   <li><b>自动触发</b>：当检测到碰撞且满足条件时自动执行跳跃，无需玩家手动按键</li>
 *   <li><b>状态管理</b>：跟踪起跳、空中、落地各阶段，防止重复触发</li>
 * </ul>
 *
 * <h3>使用场景</h3>
 * <p>角色水平移动时自然碰到台阶状地形（高度差一格），系统自动识别并完成跳跃动作。</p>
 */
public class AutoJumpSystem {

    /**
     * 日志标签。
     */
    private static final String TAG = "AutoJump";

    // ==================== 可配置参数 ====================

    /**
     * 方块尺寸引用（像素）
     */
    public final int tileSize;

    /**
     * 碰撞触发灵敏度（像素）。当角色距离障碍物小于此值时触发跳跃。
     * 取值为 tilesize/2，即半个格子。
     */
    public final float triggerSensitivity;

    /**
     * 自动跳跃是否启用。默认为 true。
     */
    public static boolean globalEnabled = true;

    /**
     * 重力加速度（像素/tick²）
     */
    private static final double GRAVITY = 1;

    // ==================== 内部状态 ====================

    /**
     * 是否正在执行自动跳跃。
     * 用于防止在跳跃过程中重复触发。
     */
    private boolean isAutoJumping = false;

    /**
     * 最后一次检查的方向（左或右）。
     * -1 表示左侧，0 表示未检查，1 表示右侧。
     */
    private int lastCheckDirection = 0;

    /**
     * 等待恢复计数器。当跳跃失败时重置为 JUMP_RECOVERY_TICKS。
     * 在此期间禁用自动跳跃，避免立即重复尝试。
     */
    private int recoveryCounter = 0;

    /**
     * 计算好的所需垂直速度（由 handleCollisionTrigger 计算，供外部获取）。
     * 负值表示向上跳跃。
     */
    private double requiredVelocityY = 0;

    /**
     * 跳跃恢复时间（tick 数）。
     * 跳跃失败后需要等待的 tick 数量。
     */
    private static final int JUMP_RECOVERY_TICKS = 10;

    // ==================== 构造方法 ====================

    /**
     * 构造自动跳跃系统。
     *
     * @param panel 游戏主面板，用于获取上下文信息
     */
    public AutoJumpSystem(Gamepanel panel) {
        this.tileSize = Gamepanel.titlesize;
        this.triggerSensitivity = tileSize / 2.0f;
    }

    /**
     * 检查前方是否有高一格的方块。
     *
     * <p>从角色当前位置向前方扫描，寻找高度比地面高一格且有支撑的实心方块。</p>
     *
     * @param playerX    玩家 X 坐标（世界像素）
     * @param playerY    玩家 Y 坐标（世界像素）
     * @param direction  方向（-1 向左，1 向右）
     * @param infiniteMap 无限地图引用
     * @return 如果找到目标方块返回 [tileX, tileY]，否则返回 null
     */
    public int[] detectTargetBlock(double playerX, double playerY, int direction, InfiniteMap infiniteMap) {
        int inset = 3;
        int colW = tileSize - 2 * inset;

        // 角色脚下方块 Y 坐标（地面高度）
        double footY = playerY + inset + (tileSize - 2 * inset);
        int groundTileY = (int) Math.floor(footY / tileSize);

        // 目标方块 Y 坐标（高一格）
        int targetTileY = groundTileY - 1;
        if (targetTileY < 0) {
            return null;
        }

        // 计算角色中心的格子坐标
        int centerTileX = (int) Math.floor((playerX + colW / 2) / tileSize);

        // 扫描范围：向前最多 6 个格子
        int startScanOffset = 1;
        int endScanOffset = 6;

        for (int offset = startScanOffset; offset <= endScanOffset; offset++) {
            int checkTileX;
            if (direction > 0) {
                checkTileX = centerTileX + offset;
            } else {
                checkTileX = centerTileX - offset;
            }

            // 即使 X 为负数也应该能正常检测
            int targetType = infiniteMap.getTileType(checkTileX, targetTileY);
            Block targetBlock = Block.fromId(targetType);

            // 检查目标位置是否为实心方块
            if (targetBlock == null || !targetBlock.isSolid()) {
                continue;
            }

            // 检查目标方块下方是否有支撑（必须是实心）
            int supportType = infiniteMap.getTileType(checkTileX, targetTileY + 1);
            Block supportBlock = Block.fromId(supportType);
            if (supportBlock == null || !supportBlock.isSolid()) {
                continue;
            }

            if (Gamepanel.ENABLE_DEBUG_LOG) {
                System.out.println("[" + TAG + "] 检测到目标方块: tileX=" + checkTileX + ", tileY=" + targetTileY + 
                                 ", playerX=" + String.format("%.2f", playerX));
            }

            return new int[]{checkTileX, targetTileY};
        }

        return null;
    }

    /**
     * 处理碰撞触发逻辑。
     *
     * <p>在 X 轴移动发生碰撞时调用，判断是否需要自动跳跃。</p>
     *
     * @param playerX      玩家 X 坐标（世界像素）
     * @param playerY      玩家 Y 坐标（世界像素）
     * @param movementDx   移动方向及距离（>0 向右，<0 向左）
     * @param velocityY    当前 Y 轴速度
     * @param onGround     是否在地面上
     * @param isInMidAir   是否在跳跃动画中
     * @param isInhibited  是否被抑制（例如玩家手动向下移动）
     * @param infiniteMap  无限地图引用
     * @return 如果触发了自动跳跃返回 true，否则返回 false
     */
    public boolean handleCollisionTrigger(double playerX, double playerY, double movementDx,
                                         double velocityY, boolean onGround, boolean isInMidAir,
                                         boolean isInhibited, InfiniteMap infiniteMap) {
        if (!globalEnabled || isInhibited || recoveryCounter > 0) {
            return false;
        }

        if (isInMidAir && !onGround) {
            if (!isAutoJumping) {
                isAutoJumping = true;
            }
            return false;
        }

        if (!onGround) {
            return false;
        }

        if (Math.abs(movementDx) < 1.0) {
            return false;
        }

        int direction = movementDx > 0 ? 1 : -1;

        // 如果上一次检查的是相反方向，强制重新检测
        if (lastCheckDirection != 0 && lastCheckDirection != direction) {
            isAutoJumping = false;
        }

        if (isAutoJumping) {
            return false;
        }

        int[] target = detectTargetBlock(playerX, playerY, direction, infiniteMap);
        if (target == null) {
            return false;
        }

        int targetTileX = target[0];
        int targetTileY = target[1];

        double playerRightEdge = playerX + tileSize;
        double playerLeftEdge = playerX;
        double playerCenterX = playerX + tileSize / 2.0;

        double targetLeftX = targetTileX * tileSize;
        double targetRightX = (targetTileX + 1) * tileSize;

        // 检查玩家是否在目标的水平范围内（允许一定容差）
        // 修复：使用更大的触发范围和更宽松的条件，确保低速和刚好碰到时也能触发
        double triggerRange = triggerSensitivity * 2.0;  // 从 1.5 增加到 2.0（约 48 像素）
        boolean isWithinHorizontalRange = false;

        if (direction > 0) {
            // 向右移动：玩家的右边接近或碰到目标的左边
            // 修复：移除 distance >= 0 的限制，允许轻微重叠（玩家已经碰到方块的情况）
            double distance = targetLeftX - playerRightEdge;
            isWithinHorizontalRange = distance > -triggerRange && distance < triggerRange;
        } else {
            // 向左移动：玩家的左边接近或碰到目标的右边
            // 修复：移除 distance >= 0 的限制，允许轻微重叠
            double distance = playerLeftEdge - targetRightX;
            isWithinHorizontalRange = distance > -triggerRange && distance < triggerRange;
        }

        if (!isWithinHorizontalRange) {
            return false;
        }

        // 检查高度差是否合理（一格高）
        double playerTopY = playerY;
        double targetBottomY = (targetTileY + 1) * tileSize;
        double heightDiff = targetBottomY - playerTopY;

        if (heightDiff < tileSize * 0.5 || heightDiff > tileSize * 1.5) {
            return false;
        }

        // 计算所需的跳跃速度并存储
        requiredVelocityY = calculateRequiredVelocity(playerX, playerY, direction, infiniteMap);
        
        if (requiredVelocityY == 0) {
            return false;
        }

        return true;
    }

    /**
     * 获取计算好的所需垂直速度。
     *
     * @return 垂直初速度（负值表示向上），未计算返回 0
     */
    public double getRequiredVelocityY() {
        return requiredVelocityY;
    }

    /**
     * 计算并执行精准跳跃所需的垂直初速度。
     *
     * <p>使用物理公式计算初速度，确保抛物线轨迹恰好到达目标方块顶部。</p>
     *
     * @param playerX    玩家当前 X 坐标
     * @param playerY    玩家当前 Y 坐标
     * @param direction  移动方向（-1 左，1 右）
     * @param infiniteMap 地图引用
     * @return 计算出的垂直初速度（负值表示向上），如果无法跳跃返回 0
     */
    public double calculateRequiredVelocity(double playerX, double playerY, int direction, InfiniteMap infiniteMap) {
        int inset = 3;
        int colH = tileSize - 2 * inset;

        double footY = playerY + inset + colH;
        int groundTileY = (int) Math.floor(footY / tileSize);
        int targetTileY = groundTileY - 1;
        int targetTileX = findNearestSolidTileX(playerX, playerY, direction, infiniteMap, targetTileY);

        if (targetTileX == Integer.MIN_VALUE) {
            return 0;
        }

        // 计算到目标方块的水平距离
        double playerCenterX = playerX + tileSize / 2.0;
        double targetCenterX = targetTileX * tileSize + tileSize / 2.0;
        double horizontalDistance = Math.abs(targetCenterX - playerCenterX);

        // 设置水平速度和跳跃时间
        double horizontalSpeed = 6;
        double timeToReach = Math.max(horizontalDistance / horizontalSpeed, 0.3);

        // 目标方块顶部高度
        double targetTopY = targetTileY * tileSize;
        
        // 需要跳跃的高度差（从脚底到目标顶部）
        double heightDiff = footY - targetTopY - colH;

        // 物理公式: h = v0*t + 0.5*g*t^2
        // 求解 v0 = (h - 0.5*g*t^2) / (-t)
        double requiredVY = (heightDiff - 0.5 * GRAVITY * timeToReach * timeToReach) / (-timeToReach);

        // 限制速度范围
        double calculatedVY = -Math.max(Math.abs(requiredVY), 9);
        
        if (calculatedVY < -14) {
            calculatedVY = -14;
        } else if (calculatedVY > -7) {
            calculatedVY = -10;
        }

        isAutoJumping = true;

        if (Gamepanel.ENABLE_DEBUG_LOG) {
            System.out.println("[" + TAG + "] 自动跳跃计算: 距离=" + String.format("%.2f", horizontalDistance) + 
                             ", VY=" + String.format("%.2f", calculatedVY));
        }

        return calculatedVY;
    }

    /**
     * 查找最近的可到达方块 X 坐标。
     *
     * @param playerX    玩家 X 坐标
     * @param playerY    玩家 Y 坐标
     * @param direction  移动方向
     * @param targetTileY 目标方块 Y 坐标
     * @param infiniteMap 地图引用
     * @return 目标方块 X 坐标，未找到返回 Integer.MIN_VALUE
     */
    private int findNearestSolidTileX(double playerX, double playerY, int direction,
                                     InfiniteMap infiniteMap, int targetTileY) {
        int centerTileX = (int) Math.floor((playerX + tileSize / 2.0) / tileSize);

        for (int offset = 1; offset <= 4; offset++) {
            int checkTileX = centerTileX + direction * offset;

            int type = infiniteMap.getTileType(checkTileX, targetTileY);
            Block b = Block.fromId(type);
            if (b != null && b.isSolid()) {
                int supportType = infiniteMap.getTileType(checkTileX, targetTileY + 1);
                Block support = Block.fromId(supportType);
                if (support != null && support.isSolid()) {
                    return checkTileX;
                }
            }
        }

        return Integer.MIN_VALUE;  // 使用特殊值表示未找到
    }

    /**
     * 更新系统状态。
     *
     * <p>每 tick 调用一次，递减恢复计数器。</p>
     */
    public void update() {
        if (recoveryCounter > 0) {
            recoveryCounter--;
        }
    }

    /**
     * 标记跳跃失败，进入恢复状态。
     */
    private void triggerRecovery() {
        recoveryCounter = JUMP_RECOVERY_TICKS;
        isAutoJumping = false;
        requiredVelocityY = 0;
    }

    /**
     * 重置自动跳跃状态。
     *
     * <p>在玩家明确控制跳跃或停止移动时调用。</p>
     */
    public void resetState() {
        isAutoJumping = false;
    }

    /**
     * 检查是否正在执行自动跳跃。
     *
     * @return 是否正在自动跳跃
     */
    public boolean isAutoJumping() {
        return isAutoJumping;
    }
}
