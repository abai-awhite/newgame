package main.gui;

import main.Gameframe;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class StartMenuPanel extends JPanel {

    private static final Color BG_TOP = new Color(40, 30, 20);
    private static final Color BG_BOTTOM = new Color(25, 18, 10);
    private static final Color TITLE_COLOR = new Color(220, 200, 170);
    private static final Color TITLE_SHADOW = new Color(15, 10, 5, 120);
    private static final Color BTN_BG = new Color(60, 45, 30, 200);
    private static final Color BTN_BG_HOVER = new Color(90, 70, 50, 220);
    private static final Color BTN_BORDER = new Color(140, 110, 80);
    private static final Color BTN_TEXT = Color.WHITE;

    private static final int BTN_WIDTH = 220;
    private static final int BTN_HEIGHT = 60;
    private static final int BTN_ARC = 12;

    private Image startIcon;
    private Image exitIcon;
    private Image settingsIcon;
    private Image backgroundImg;

    private Rectangle startBtnRect;
    private Rectangle settingsBtnRect;
    private Rectangle exitBtnRect;
    private boolean startHovered;
    private boolean settingsHovered;
    private boolean exitHovered;

    private final Runnable onStartGame;
    private final Runnable onOpenSettings;

    public StartMenuPanel(Runnable onStartGame, Runnable onOpenSettings) {
        this.onStartGame = onStartGame;
        this.onOpenSettings = onOpenSettings;
        loadResources();
        setupMouse();
        setFocusable(true);
    }

    private void loadResources() {
        try {
            startIcon = ImageIO.read(getClass().getResourceAsStream("/gui/start-block.png"));
        } catch (IOException e) {
            startIcon = null;
        }
        try {
            exitIcon = ImageIO.read(getClass().getResourceAsStream("/gui/exit-block.png"));
        } catch (IOException e) {
            exitIcon = null;
        }
        try {
            settingsIcon = ImageIO.read(getClass().getResourceAsStream("/gui/setting-block.png"));
        } catch (IOException e) {
            settingsIcon = null;
        }
        try {
            backgroundImg = ImageIO.read(getClass().getResourceAsStream("/gui/game-start.png"));
        } catch (IOException e) {
            backgroundImg = null;
        }
    }

    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (startBtnRect != null && startBtnRect.contains(e.getPoint())) {
                    if (onStartGame != null) onStartGame.run();
                } else if (settingsBtnRect != null && settingsBtnRect.contains(e.getPoint())) {
                    if (onOpenSettings != null) onOpenSettings.run();
                } else if (exitBtnRect != null && exitBtnRect.contains(e.getPoint())) {
                    System.exit(0);
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean prevStart = startHovered;
                boolean prevSettings = settingsHovered;
                boolean prevExit = exitHovered;
                startHovered = startBtnRect != null && startBtnRect.contains(e.getPoint());
                settingsHovered = settingsBtnRect != null && settingsBtnRect.contains(e.getPoint());
                exitHovered = exitBtnRect != null && exitBtnRect.contains(e.getPoint());
                if (prevStart != startHovered || prevSettings != settingsHovered || prevExit != exitHovered) {
                    setCursor(Cursor.getPredefinedCursor(
                        (startHovered || settingsHovered || exitHovered) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        drawBackground(g2, w, h);
        drawTitle(g2, w);
        drawButtons(g2, w, h);
    }

    private void drawBackground(Graphics2D g2, int w, int h) {
        if (backgroundImg != null) {
            g2.drawImage(backgroundImg, 0, 0, w, h, null);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
        }

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRect(0, 0, w, h);
    }

    private void drawTitle(Graphics2D g2, int w) {
        Font titleFont = new Font("微软雅黑", Font.BOLD, 56);
        g2.setFont(titleFont);

        String title = "";
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title);
        int tx = (w - tw) / 2;
        int ty = getHeight() / 3;

        g2.setColor(TITLE_SHADOW);
        g2.drawString(title, tx + 3, ty + 3);

        g2.setColor(TITLE_COLOR);
        g2.drawString(title, tx, ty);
    }

    private void drawButtons(Graphics2D g2, int w, int h) {
        int centerX = (w - BTN_WIDTH) / 2;
        int baseY = h / 2 - 10;
        int gap = 15;

        int startY = baseY - (BTN_HEIGHT + gap);
        int settingsY = baseY;
        int exitY = baseY + (BTN_HEIGHT + gap);

        startBtnRect = new Rectangle(centerX, startY, BTN_WIDTH, BTN_HEIGHT);
        settingsBtnRect = new Rectangle(centerX, settingsY, BTN_WIDTH, BTN_HEIGHT);
        exitBtnRect = new Rectangle(centerX, exitY, BTN_WIDTH, BTN_HEIGHT);

        drawButton(g2, startBtnRect, startHovered, "", startIcon);
        drawButton(g2, settingsBtnRect, settingsHovered, "", settingsIcon);
        drawButton(g2, exitBtnRect, exitHovered, "", exitIcon);
    }

    private void drawButton(Graphics2D g2, Rectangle rect, boolean hovered,
                            String text, Image bgImg) {
        if (bgImg != null) {
            g2.drawImage(bgImg, rect.x, rect.y, rect.width, rect.height, null);
        } else {
            g2.setColor(hovered ? BTN_BG_HOVER : BTN_BG);
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);
        }

        if (hovered) {
            g2.setColor(new Color(255, 255, 255, 60));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);
        }

        g2.setStroke(new BasicStroke(hovered ? 3 : 2));
        g2.setColor(hovered ? Color.WHITE : BTN_BORDER);
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, BTN_ARC, BTN_ARC);

        Font btnFont = new Font("微软雅黑", Font.BOLD, 20);
        g2.setFont(btnFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(BTN_TEXT);

        int strX = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int strY = rect.y + (rect.height + fm.getAscent()) / 2 - 2;
        g2.drawString(text, strX, strY);
    }
}