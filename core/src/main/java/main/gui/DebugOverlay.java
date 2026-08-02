package main.gui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * 调试覆盖层，显示 FPS、玩家坐标等信息。
 */
public class DebugOverlay {

    private boolean visible = false;
    private final entity.Player player;
    private final main.world.InfiniteMap infiniteMap;
    private int fps = 0;
    private int chunkCount = 0;

    public DebugOverlay(entity.Player player, main.world.InfiniteMap infiniteMap) {
        this.player = player;
        this.infiniteMap = infiniteMap;
    }

    public void toggle() {
        visible = !visible;
    }

    public void update() {
        if (visible) {
            chunkCount = infiniteMap.hashCode(); // 简化
        }
    }

    public void render(SpriteBatch batch, BitmapFont font) {
        if (!visible) return;

        font.draw(batch, "FPS: " + com.badlogic.gdx.Gdx.graphics.getFramesPerSecond(), 10, 20);
        font.draw(batch, "Player: (" + String.format("%.1f", player.currentX) + ", " + String.format("%.1f", player.currentY) + ")", 10, 40);
        font.draw(batch, "Direction: " + player.direction, 10, 60);
        font.draw(batch, "OnGround: " + player.onGround, 10, 80);
        font.draw(batch, "VelocityY: " + String.format("%.1f", player.velocityY), 10, 100);
        font.draw(batch, "JumpPhase: " + player.jumpPhase, 10, 120);
        font.draw(batch, "Dash: " + player.getDashCharges() + "/" + player.getDashMax(), 10, 140);
    }
}
