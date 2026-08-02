package main.gui;

import java.awt.*;

public class EscPanel {

    private static final Color OVERLAY_COLOR = new Color(0, 0, 0, 120);
    private static final Color PANEL_BG = new Color(35, 25, 15, 230);
    private static final Color PANEL_BORDER = new Color(100, 75, 50, 200);
    private static final Color BTN_BG = new Color(60, 45, 30, 200);
    private static final Color BTN_BG_HOVER = new Color(90, 70, 50, 220);
    private static final Color BTN_BORDER = new Color(140, 110, 80);
    private static final Color BTN_TEXT = new Color(220, 200, 170);
    private static final Color TITLE_COLOR = new Color(220, 200, 170);

    private static final int PANEL_WIDTH = 260;
    private static final int BTN_WIDTH = 200;
    private static final int BTN_HEIGHT = 50;
    private static final int BTN_ARC = 10;
    private static final int BTN_GAP = 16;

    private volatile boolean visible = false;
    private boolean resumeHovered;
    private boolean settingsHovered;
    private boolean quitHovered;

    private Rectangle resumeBtnRect;
    private Rectangle settingsBtnRect;
    private Rectangle quitBtnRect;

    private final Runnable onResume;
    private final Runnable onOpenSettings;
    private final Runnable onQuitGame;

    public EscPanel(Runnable onResume, Runnable onOpenSettings, Runnable onQuitGame) {
        this.onResume = onResume;
        this.onOpenSettings = onOpenSettings;
        this.onQuitGame = onQuitGame;
    }

    public void toggle() {
        visible = !visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean v) {
        visible = v;
    }

    public int getPanelWidth() {
        return PANEL_WIDTH;
    }

    public boolean handleClick(int screenX, int screenY) {
        if (!visible) return false;
        if (resumeBtnRect != null && resumeBtnRect.contains(screenX, screenY)) {
            if (onResume != null) onResume.run();
            return true;
        }
        if (settingsBtnRect != null && settingsBtnRect.contains(screenX, screenY)) {
            if (onOpenSettings != null) onOpenSettings.run();
            return true;
        }
        if (quitBtnRect != null && quitBtnRect.contains(screenX, screenY)) {
            if (onQuitGame != null) onQuitGame.run();
            return true;
        }
        return false;
    }

    public void handleMove(int screenX, int screenY) {
        if (!visible) return;
        resumeHovered = resumeBtnRect != null && resumeBtnRect.contains(screenX, screenY);
        settingsHovered = settingsBtnRect != null && settingsBtnRect.contains(screenX, screenY);
        quitHovered = quitBtnRect != null && quitBtnRect.contains(screenX, screenY);
    }

    public void render(Graphics2D g2, int screenWidth, int screenHeight) {
        if (!visible) return;

        Composite oldComposite = g2.getComposite();

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        g2.setColor(OVERLAY_COLOR);
        g2.fillRect(0, 0, screenWidth, screenHeight);

        int panelX = screenWidth - PANEL_WIDTH;
        int panelY = 0;

        g2.setColor(PANEL_BG);
        g2.fillRect(panelX, panelY, PANEL_WIDTH, screenHeight);

        g2.setColor(PANEL_BORDER);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(panelX, panelY, panelX, screenHeight);

        Font titleFont = new Font("微软雅黑", Font.BOLD, 26);
        g2.setFont(titleFont);
        g2.setColor(TITLE_COLOR);
        FontMetrics fm = g2.getFontMetrics();
        String title = "暂停";
        int titleX = panelX + (PANEL_WIDTH - fm.stringWidth(title)) / 2;
        int titleY = screenHeight / 4;
        g2.drawString(title, titleX, titleY);

        int btnStartY = screenHeight / 3 + 20;
        int btnX = panelX + (PANEL_WIDTH - BTN_WIDTH) / 2;

        resumeBtnRect = new Rectangle(btnX, btnStartY, BTN_WIDTH, BTN_HEIGHT);
        settingsBtnRect = new Rectangle(btnX, btnStartY + BTN_HEIGHT + BTN_GAP, BTN_WIDTH, BTN_HEIGHT);
        quitBtnRect = new Rectangle(btnX, btnStartY + (BTN_HEIGHT + BTN_GAP) * 2, BTN_WIDTH, BTN_HEIGHT);

        drawButton(g2, resumeBtnRect, resumeHovered, "继续游戏");
        drawButton(g2, settingsBtnRect, settingsHovered, "设置");
        drawButton(g2, quitBtnRect, quitHovered, "退出游戏");

        g2.setComposite(oldComposite);
    }

    private void drawButton(Graphics2D g2, Rectangle rect, boolean hovered, String text) {
        g2.setColor(hovered ? BTN_BG_HOVER : BTN_BG);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);

        if (hovered) {
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);
        }

        g2.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
        g2.setColor(hovered ? Color.WHITE : BTN_BORDER);
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);

        Font btnFont = new Font("微软雅黑", Font.BOLD, 18);
        g2.setFont(btnFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(BTN_TEXT);

        int strX = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int strY = rect.y + (rect.height + fm.getAscent()) / 2 - 2;
        g2.drawString(text, strX, strY);
    }
}