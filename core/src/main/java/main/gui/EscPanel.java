package main.gui;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * ESC 暂停面板。
 */
public class EscPanel {

    private boolean visible = false;
    private final Runnable onSaveAndContinue;
    private final Runnable onOpenSettings;
    private final Runnable onQuitGame;

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 250;
    private static final int BUTTON_HEIGHT = 40;
    private static final int BUTTON_SPACING = 10;

    public EscPanel(Runnable onSaveAndContinue, Runnable onOpenSettings, Runnable onQuitGame) {
        this.onSaveAndContinue = onSaveAndContinue;
        this.onOpenSettings = onOpenSettings;
        this.onQuitGame = onQuitGame;
    }

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean v) { this.visible = v; }
    public int getPanelWidth() { return PANEL_WIDTH; }

    public boolean handleClick(int mouseX, int mouseY) {
        if (!visible) return false;

        int screenW = com.badlogic.gdx.Gdx.graphics.getWidth();
        int screenH = com.badlogic.gdx.Gdx.graphics.getHeight();
        int panelX = (screenW - PANEL_WIDTH) / 2;
        int panelY = (screenH - PANEL_HEIGHT) / 2;

        if (mouseX < panelX || mouseX > panelX + PANEL_WIDTH ||
            mouseY < panelY || mouseY > panelY + PANEL_HEIGHT) {
            return false;
        }

        int relY = mouseY - panelY;
        int buttonY = PANEL_HEIGHT - 60;

        // 继续按钮
        if (relY >= buttonY - BUTTON_HEIGHT && relY <= buttonY) {
            onSaveAndContinue.run();
            return true;
        }
        buttonY -= BUTTON_HEIGHT + BUTTON_SPACING;

        // 设置按钮
        if (relY >= buttonY - BUTTON_HEIGHT && relY <= buttonY) {
            onOpenSettings.run();
            return true;
        }
        buttonY -= BUTTON_HEIGHT + BUTTON_SPACING;

        // 退出按钮
        if (relY >= buttonY - BUTTON_HEIGHT && relY <= buttonY) {
            onQuitGame.run();
            return true;
        }

        return true;
    }

    public void handleMove(int mouseX, int mouseY) {
        // 暂不需要
    }

    public void render(SpriteBatch batch, BitmapFont font, int screenWidth, int screenHeight) {
        if (!visible) return;

        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;

        // 半透明背景
        com.badlogic.gdx.graphics.glutils.ShapeRenderer sr = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();
        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        sr.setColor(0, 0, 0, 0.5f);
        sr.rect(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);
        sr.end();
        sr.dispose();

        // 标题
        font.draw(batch, "暂停", panelX + PANEL_WIDTH / 2 - 20, panelY + PANEL_HEIGHT - 20);

        // 按钮
        int buttonY = panelY + PANEL_HEIGHT - 60;
        font.draw(batch, "继续游戏", panelX + 20, buttonY - 10);
        buttonY -= BUTTON_HEIGHT + BUTTON_SPACING;
        font.draw(batch, "设置", panelX + 20, buttonY - 10);
        buttonY -= BUTTON_HEIGHT + BUTTON_SPACING;
        font.draw(batch, "退出游戏", panelX + 20, buttonY - 10);
    }
}
