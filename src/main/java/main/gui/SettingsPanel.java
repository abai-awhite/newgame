package main.gui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class SettingsPanel extends JPanel {

    private static final Color BG_COLOR = new Color(35, 25, 15);
    private static final Color PANEL_BG = new Color(50, 38, 25, 220);
    private static final Color SIDEBAR_BG = new Color(40, 30, 18, 220);
    private static final Color TEXT_COLOR = new Color(220, 200, 170);
    private static final Color TEXT_DIM = new Color(160, 140, 120);
    private static final Color ACCENT = new Color(200, 170, 110);
    private static final Color SELECTED_BG = new Color(80, 60, 40, 200);
    private static final Color HOVER_BG = new Color(65, 48, 32, 200);
    private static final Color BORDER_COLOR = new Color(100, 75, 50);

    private static final int SIDEBAR_WIDTH = 200;
    private static final int CLOSE_SIZE = 36;

    private final Runnable onClose;

    private String[] optionNames = {"按键设置", "自动跳跃"};
    private int selectedOption = 0;

    private Image backgroundImg;

    private Rectangle closeRect;
    private Rectangle[] optionRects = new Rectangle[1];
    private boolean closeHovered;
    private int[] optionHovered = new int[optionNames.length];

    private boolean listeningForKey;
    private int listeningRow = -1;

    public SettingsPanel(Runnable onClose) {
        this.onClose = onClose;
        setFocusable(true);
        loadResources();
        setupMouse();
        setupKeyboard();
    }

    private void loadResources() {
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
                if (closeRect != null && closeRect.contains(e.getPoint())) {
                    if (onClose != null) onClose.run();
                    return;
                }
                for (int i = 0; i < optionRects.length; i++) {
                    if (optionRects[i] != null && optionRects[i].contains(e.getPoint())) {
                        selectedOption = i;
                        listeningForKey = false;
                        listeningRow = -1;
                        repaint();
                        return;
                    }
                }
                if (selectedOption == 0) {
                    handleKeyBindingClick(e.getPoint());
                } else if (selectedOption == 1) {
                    handleAutoJumpToggleClick(e.getPoint());
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                boolean changed = false;
                closeHovered = closeRect != null && closeRect.contains(e.getPoint());

                for (int i = 0; i < optionRects.length; i++) {
                    boolean h = optionRects[i] != null && optionRects[i].contains(e.getPoint());
                    if (h != (optionHovered[i] == 1)) {
                        optionHovered[i] = h ? 1 : 0;
                        changed = true;
                    }
                }

                int oldCursor = getCursor().getType();
                boolean anyOptionHovered = false;
                for (int i = 0; i < optionRects.length; i++) {
                    if (optionHovered[i] == 1) { anyOptionHovered = true; break; }
                }
                boolean hover = closeHovered || anyOptionHovered
                    || (selectedOption == 0 && isKeyBindingRowHovered(e.getPoint()))
                    || (selectedOption == 1 && isAutoJumpToggleHovered(e.getPoint()));
                int newCursor = hover ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR;
                if (oldCursor != newCursor) {
                    setCursor(Cursor.getPredefinedCursor(newCursor));
                }

                if (changed) repaint();
            }
        });
    }

    private void setupKeyboard() {
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE && !listeningForKey) {
                    if (onClose != null) onClose.run();
                    return;
                }
                if (listeningForKey && listeningRow >= 0) {
                    int code = e.getKeyCode();
                    if (code != KeyEvent.VK_ESCAPE) {
                        String[] actions = KeyBindingConfig.getActionNames();
                        if (listeningRow < actions.length) {
                            KeyBindingConfig.setKeyCode(actions[listeningRow], code);
                        }
                    }
                    listeningForKey = false;
                    listeningRow = -1;
                    repaint();
                }
            }
        });
    }

    private boolean isKeyBindingRowHovered(Point p) {
        if (selectedOption != 0) return false;
        String[] actions = KeyBindingConfig.getActionNames();
        int panelX = SIDEBAR_WIDTH + 30;
        int panelW = getWidth() - SIDEBAR_WIDTH - 50;
        int contentY = 110;
        int rowH = 36;
        int rowGap = 4;

        for (int i = 0; i < actions.length; i++) {
            int ry = contentY + i * (rowH + rowGap);
            Rectangle rowRect = new Rectangle(panelX, ry, panelW, rowH);
            if (rowRect.contains(p)) return true;
        }
        return false;
    }

    private void handleKeyBindingClick(Point p) {
        String[] actions = KeyBindingConfig.getActionNames();
        int panelX = SIDEBAR_WIDTH + 30;
        int panelW = getWidth() - SIDEBAR_WIDTH - 50;
        int contentY = 110;
        int rowH = 36;
        int rowGap = 4;

        for (int i = 0; i < actions.length; i++) {
            int ry = contentY + i * (rowH + rowGap);
            Rectangle rowRect = new Rectangle(panelX, ry, panelW, rowH);
            if (rowRect.contains(p)) {
                listeningRow = i;
                listeningForKey = true;
                requestFocusInWindow();
                repaint();
                return;
            }
        }
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
        drawTitleBar(g2, w);
        drawSidebar(g2, w, h);
        drawContent(g2, w, h);
    }

    private void drawBackground(Graphics2D g2, int w, int h) {
        if (backgroundImg != null) {
            g2.drawImage(backgroundImg, 0, 0, w, h, null);
        } else {
            g2.setColor(BG_COLOR);
            g2.fillRect(0, 0, w, h);
        }
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fillRect(0, 0, w, h);
    }

    private void drawTitleBar(Graphics2D g2, int w) {
        g2.setColor(new Color(25, 18, 10, 220));
        g2.fillRect(0, 0, w, 50);

        g2.setColor(new Color(100, 75, 50, 180));
        g2.fillRect(0, 48, w, 2);

        Font titleFont = new Font("微软雅黑", Font.BOLD, 22);
        g2.setFont(titleFont);
        g2.setColor(TEXT_COLOR);
        g2.drawString("设置", 20, 34);

        closeRect = new Rectangle(w - CLOSE_SIZE - 12, 7, CLOSE_SIZE, CLOSE_SIZE);
        g2.setColor(closeHovered ? new Color(200, 60, 60) : TEXT_DIM);
        g2.setStroke(new BasicStroke(2));
        int cx = closeRect.x + closeRect.width / 2;
        int cy = closeRect.y + closeRect.height / 2;
        int off = 8;
        g2.drawLine(cx - off, cy - off, cx + off, cy + off);
        g2.drawLine(cx + off, cy - off, cx - off, cy + off);
    }

    private void drawSidebar(Graphics2D g2, int w, int h) {
        int sx = 0;
        int sy = 50;
        int sw = SIDEBAR_WIDTH;
        int sh = h - 50;

        g2.setColor(SIDEBAR_BG);
        g2.fillRect(sx, sy, sw, sh);

        g2.setColor(new Color(100, 75, 50, 180));
        g2.fillRect(sw - 2, sy, 2, sh);

        Font optFont = new Font("微软雅黑", Font.PLAIN, 15);
        g2.setFont(optFont);
        FontMetrics fm = g2.getFontMetrics();

        int startY = sy + 20;
        optionRects = new Rectangle[optionNames.length];

        for (int i = 0; i < optionNames.length; i++) {
            int oy = startY + i * 44;
            int oh = 40;
            Rectangle optRect = new Rectangle(10, oy, sw - 20, oh);
            optionRects[i] = optRect;

            if (i == selectedOption) {
                g2.setColor(SELECTED_BG);
                g2.fillRoundRect(optRect.x, optRect.y, optRect.width, optRect.height, 8, 8);
                g2.setColor(ACCENT);
                g2.fillRect(optRect.x, optRect.y, 3, oh);
            } else if (optionHovered[i] == 1) {
                g2.setColor(HOVER_BG);
                g2.fillRoundRect(optRect.x, optRect.y, optRect.width, optRect.height, 8, 8);
            }

            g2.setColor(i == selectedOption ? ACCENT : TEXT_COLOR);
            int tx = optRect.x + 16;
            int ty = optRect.y + (oh + fm.getAscent()) / 2 - 2;
            g2.drawString(optionNames[i], tx, ty);
        }
    }

    private void drawContent(Graphics2D g2, int w, int h) {
        int cx = SIDEBAR_WIDTH + 30;
        int cw = w - SIDEBAR_WIDTH - 50;
        int cy = 70;

        g2.setColor(PANEL_BG);
        g2.fillRoundRect(cx, cy, cw, h - cy - 20, 12, 12);
        g2.setColor(BORDER_COLOR);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(cx, cy, cw, h - cy - 20, 12, 12);

        if (selectedOption == 0) {
            drawKeySettings(g2, cx, cy, cw, h - cy - 20);
        } else if (selectedOption == 1) {
            drawAutoJumpToggle(g2, cx, cy, cw, h - cy - 20);
        }
    }

    private void drawKeySettings(Graphics2D g2, int x, int y, int w, int h) {
        Font titleFont = new Font("微软雅黑", Font.BOLD, 20);
        g2.setFont(titleFont);
        g2.setColor(TEXT_COLOR);
        g2.drawString("按键设置", x + 20, y + 30);

        Font labelFont = new Font("微软雅黑", Font.PLAIN, 12);
        g2.setFont(labelFont);
        g2.setColor(TEXT_DIM);
        g2.drawString("点击按键可重新绑定", x + 20, y + 50);

        String[] actions = KeyBindingConfig.getActionNames();
        int rowH = 36;
        int rowGap = 4;
        int contentX = x + 20;
        int contentW = w - 40;
        int contentY = y + 65;

        Font rowFont = new Font("微软雅黑", Font.PLAIN, 15);
        g2.setFont(rowFont);
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < actions.length; i++) {
            int ry = contentY + i * (rowH + rowGap);

            if (i % 2 == 0) {
                g2.setColor(new Color(255, 255, 255, 15));
                g2.fillRect(contentX, ry, contentW, rowH);
            }

            g2.setColor(TEXT_COLOR);
            g2.drawString(actions[i], contentX + 12, ry + (rowH + fm.getAscent()) / 2 - 2);

            String keyText;
            boolean isListening = listeningForKey && listeningRow == i;
            if (isListening) {
                keyText = "...";
                g2.setColor(new Color(255, 220, 80));
            } else {
                keyText = KeyBindingConfig.getKeyText(actions[i]);
                g2.setColor(ACCENT);
            }

            int keyX = contentX + contentW - fm.stringWidth(keyText) - 12;
            g2.drawString(keyText, keyX, ry + (rowH + fm.getAscent()) / 2 - 2);

            if (isListening) {
                g2.setColor(new Color(200, 170, 110, 100));
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f}, 0f));
                g2.drawRoundRect(contentX, ry, contentW, rowH, 6, 6);
            }
        }
    }

    private boolean isAutoJumpToggleHovered(Point p) {
        int panelX = SIDEBAR_WIDTH + 30;
        int panelW = getWidth() - SIDEBAR_WIDTH - 50;
        int contentY = 110;
        Rectangle toggleRect = new Rectangle(panelX, contentY, panelW, 40);
        return toggleRect.contains(p);
    }

    private void handleAutoJumpToggleClick(Point p) {
        if (isAutoJumpToggleHovered(p)) {
            entity.AutoJumpSystem.globalEnabled = !entity.AutoJumpSystem.globalEnabled;
            repaint();
        }
    }

    private void drawAutoJumpToggle(Graphics2D g2, int x, int y, int w, int h) {
        Font labelFont = new Font("微软雅黑", Font.PLAIN, 16);
        g2.setFont(labelFont);
        g2.setColor(TEXT_COLOR);
        g2.drawString("自动跳跃", x + 16, y + 32);

        boolean enabled = entity.AutoJumpSystem.globalEnabled;
        int toggleW = 50;
        int toggleH = 24;
        int tx = x + w - toggleW - 20;
        int ty = y + 20;

        g2.setColor(enabled ? new Color(80, 180, 80) : new Color(100, 100, 100));
        g2.fillRoundRect(tx, ty, toggleW, toggleH, 12, 12);

        int knobX = enabled ? tx + toggleW - 18 : tx + 4;
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, ty + 4, 16, 16);

        Font statusFont = new Font("微软雅黑", Font.PLAIN, 13);
        g2.setFont(statusFont);
        g2.setColor(enabled ? new Color(120, 220, 120) : TEXT_DIM);
        g2.drawString(enabled ? "开" : "关", tx + (toggleW - g2.getFontMetrics().stringWidth(enabled ? "开" : "关")) / 2, ty + toggleH + 18);
    }
}