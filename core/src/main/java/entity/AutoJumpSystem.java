package entity;

import block.Block;
import main.world.InfiniteMap;

/**
 * 角色自动跳跃系统（纯逻辑类）。
 */
public class AutoJumpSystem {

    private static final String TAG = "AutoJump";

    public final int tileSize;
    public final float triggerSensitivity;
    public static boolean globalEnabled = true;
    private static final double GRAVITY = 1;

    private boolean isAutoJumping = false;
    private int lastCheckDirection = 0;
    private int recoveryCounter = 0;
    private double requiredVelocityY = 0;
    private static final int JUMP_RECOVERY_TICKS = 10;

    public AutoJumpSystem(int tileSize) {
        this.tileSize = tileSize;
        this.triggerSensitivity = tileSize / 2.0f;
    }

    public int[] detectTargetBlock(double playerX, double playerY, int direction, InfiniteMap infiniteMap) {
        int inset = 3;
        int colW = tileSize - 2 * inset;

        double footY = playerY + inset + (tileSize - 2 * inset);
        int groundTileY = (int) Math.floor(footY / tileSize);
        int targetTileY = groundTileY - 1;
        if (targetTileY < 0) return null;

        int centerTileX = (int) Math.floor((playerX + colW / 2) / tileSize);
        int startScanOffset = 1;
        int endScanOffset = 6;

        for (int offset = startScanOffset; offset <= endScanOffset; offset++) {
            int checkTileX = direction > 0 ? centerTileX + offset : centerTileX - offset;

            int targetType = infiniteMap.getTileType(checkTileX, targetTileY);
            Block targetBlock = Block.fromId(targetType);
            if (targetBlock == null || !targetBlock.isSolid()) continue;

            int supportType = infiniteMap.getTileType(checkTileX, targetTileY + 1);
            Block supportBlock = Block.fromId(supportType);
            if (supportBlock == null || !supportBlock.isSolid()) continue;

            return new int[]{checkTileX, targetTileY};
        }
        return null;
    }

    public boolean handleCollisionTrigger(double playerX, double playerY, double movementDx,
                                         double velocityY, boolean onGround, boolean isInMidAir,
                                         boolean isInhibited, InfiniteMap infiniteMap) {
        if (!globalEnabled || isInhibited || recoveryCounter > 0) return false;

        if (isInMidAir && !onGround) {
            if (!isAutoJumping) isAutoJumping = true;
            return false;
        }

        if (!onGround) return false;
        if (Math.abs(movementDx) < 1.0) return false;

        int direction = movementDx > 0 ? 1 : -1;
        if (lastCheckDirection != 0 && lastCheckDirection != direction) {
            isAutoJumping = false;
        }
        if (isAutoJumping) return false;

        int[] target = detectTargetBlock(playerX, playerY, direction, infiniteMap);
        if (target == null) return false;

        int targetTileX = target[0];
        int targetTileY = target[1];

        double playerRightEdge = playerX + tileSize;
        double playerLeftEdge = playerX;
        double targetLeftX = targetTileX * tileSize;
        double targetRightX = (targetTileX + 1) * tileSize;

        double triggerRange = triggerSensitivity * 2.0;
        boolean isWithinHorizontalRange;

        if (direction > 0) {
            double distance = targetLeftX - playerRightEdge;
            isWithinHorizontalRange = distance > -triggerRange && distance < triggerRange;
        } else {
            double distance = playerLeftEdge - targetRightX;
            isWithinHorizontalRange = distance > -triggerRange && distance < triggerRange;
        }

        if (!isWithinHorizontalRange) return false;

        double playerTopY = playerY;
        double targetBottomY = (targetTileY + 1) * tileSize;
        double heightDiff = targetBottomY - playerTopY;
        if (heightDiff < tileSize * 0.5 || heightDiff > tileSize * 1.5) return false;

        requiredVelocityY = calculateRequiredVelocity(playerX, playerY, direction, infiniteMap);
        return requiredVelocityY != 0;
    }

    public double getRequiredVelocityY() { return requiredVelocityY; }

    public double calculateRequiredVelocity(double playerX, double playerY, int direction, InfiniteMap infiniteMap) {
        int inset = 3;
        int colH = tileSize - 2 * inset;

        double footY = playerY + inset + colH;
        int groundTileY = (int) Math.floor(footY / tileSize);
        int targetTileY = groundTileY - 1;
        int targetTileX = findNearestSolidTileX(playerX, playerY, direction, infiniteMap, targetTileY);

        if (targetTileX == Integer.MIN_VALUE) return 0;

        double playerCenterX = playerX + tileSize / 2.0;
        double targetCenterX = targetTileX * tileSize + tileSize / 2.0;
        double horizontalDistance = Math.abs(targetCenterX - playerCenterX);

        double horizontalSpeed = 6;
        double timeToReach = Math.max(horizontalDistance / horizontalSpeed, 0.3);

        double targetTopY = targetTileY * tileSize;
        double heightDiff = footY - targetTopY - colH;

        double requiredVY = (heightDiff - 0.5 * GRAVITY * timeToReach * timeToReach) / (-timeToReach);
        double calculatedVY = -Math.max(Math.abs(requiredVY), 9);

        if (calculatedVY < -14) calculatedVY = -14;
        else if (calculatedVY > -7) calculatedVY = -10;

        isAutoJumping = true;
        return calculatedVY;
    }

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
                if (support != null && support.isSolid()) return checkTileX;
            }
        }
        return Integer.MIN_VALUE;
    }

    public void update() {
        if (recoveryCounter > 0) recoveryCounter--;
    }

    public void resetState() {
        isAutoJumping = false;
    }

    public boolean isAutoJumping() { return isAutoJumping; }
}
