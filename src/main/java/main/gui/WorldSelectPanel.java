package main.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.BiConsumer;
import javax.imageio.ImageIO;

public class WorldSelectPanel extends JPanel {

    private static final Color BG_TOP = new Color(40, 30, 20);
    private static final Color BG_BOTTOM = new Color(25, 18, 10);
    private static final Color TITLE_COLOR = new Color(220, 200, 170);
    private static final Color TITLE_SHADOW = new Color(15, 10, 5, 120);
    private static final Color CARD_BG = new Color(60, 45, 30, 200);
    private static final Color CARD_BG_HOVER = new Color(90, 70, 50, 220);
    private static final Color CARD_BORDER = new Color(140, 110, 80);
    private static final Color BTN_TEXT = Color.WHITE;
    private static final Color INPUT_BG = new Color(50, 38, 25, 220);
    private static final Color INPUT_BORDER = new Color(140, 110, 80);

    private static final int CARD_WIDTH = 300;
    private static final int CARD_HEIGHT = 50;
    private static final int CARD_ARC = 10;

    private final List<String> worldList = new ArrayList<>();
    private final List<Rectangle> worldRects = new ArrayList<>();
    private final List<Rectangle> trashRects = new ArrayList<>();
    private int hoveredIndex = -1;
    private int trashHoveredIndex = -1;

    private Rectangle createBtnRect;
    private Rectangle backBtnRect;
    private boolean createHovered;
    private boolean backHovered;

    private final JTextField nameField;
    private final JTextField seedField;
    private Rectangle refreshBtnRect;
    private boolean refreshHovered;
    private BufferedImage refreshIcon;
    private BufferedImage trashIcon;
    private BufferedImage backgroundImg;
    private int scrollOffset;

    private final BiConsumer<String, Long> onWorldSelected;
    private final Runnable onBack;

    public WorldSelectPanel(BiConsumer<String, Long> onWorldSelected, Runnable onBack) {
        this.onWorldSelected = onWorldSelected;
        this.onBack = onBack;
        setLayout(null);
        setFocusable(true);

        nameField = new JTextField("block world");
        nameField.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        nameField.setForeground(BTN_TEXT);
        nameField.setBackground(INPUT_BG);
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(INPUT_BORDER, 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        nameField.setCaretColor(BTN_TEXT);
        nameField.setSelectedTextColor(Color.BLACK);
        nameField.setSelectionColor(new Color(180, 150, 120));
        add(nameField);

        seedField = new JTextField(String.valueOf(generateRandomSeed()));
        seedField.setFont(new Font("微软雅黑", Font.PLAIN, 18));
        seedField.setForeground(BTN_TEXT);
        seedField.setBackground(INPUT_BG);
        seedField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(INPUT_BORDER, 2),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        seedField.setCaretColor(BTN_TEXT);
        seedField.setSelectedTextColor(Color.BLACK);
        seedField.setSelectionColor(new Color(180, 150, 120));
        add(seedField);

        try {
            refreshIcon = ImageIO.read(getClass().getResourceAsStream("/icon/ui/refresh_256px.png"));
        } catch (Exception e) {
            System.err.println("加载刷新图标失败: " + e.getMessage());
        }
        try {
            trashIcon = ImageIO.read(getClass().getResourceAsStream("/icon/media/trash_256px.png"));
        } catch (Exception e) {
            System.err.println("加载删除图标失败: " + e.getMessage());
        }
        try {
            backgroundImg = ImageIO.read(getClass().getResourceAsStream("/gui/world-start.png"));
        } catch (Exception e) {
            System.err.println("加载世界背景失败: " + e.getMessage());
        }

        refreshWorldList();
        setupMouse();
    }

    private long generateRandomSeed() {
        return new Random().nextLong() & Long.MAX_VALUE;
    }

    private void refreshWorldList() {
        worldList.clear();
        File worldDir = new File("world");
        if (worldDir.exists() && worldDir.isDirectory()) {
            File[] dirs = worldDir.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File dir : dirs) {
                    if (!dir.getName().startsWith(".")) {
                        worldList.add(dir.getName());
                    }
                }
            }
        }
        worldRects.clear();
        trashRects.clear();
        repaint();
    }

    private String resolveUniqueName(String name) {
        File worldDir = new File("world");
        if (!worldDir.exists() || !worldDir.isDirectory()) return name;
        String[] existing = worldDir.list((dir, n) -> !n.startsWith("."));
        if (existing == null) return name;
        for (String e : existing) {
            if (e.equalsIgnoreCase(name)) {
                int suffix = 1;
                while (true) {
                    String candidate = name + " " + suffix;
                    boolean found = false;
                    for (String e2 : existing) {
                        if (e2.equalsIgnoreCase(candidate)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) return candidate;
                    suffix++;
                }
            }
        }
        return name;
    }

    private void deleteWorld(String name) {
        File dir = new File("world/" + name);
        if (dir.exists() && dir.isDirectory()) {
            deleteRecursively(dir);
        }
        refreshWorldList();
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                for (int i = 0; i < trashRects.size(); i++) {
                    Rectangle t = trashRects.get(i);
                    if (t != null && t.contains(e.getPoint())) {
                        deleteWorld(worldList.get(i));
                        return;
                    }
                }
                for (int i = 0; i < worldRects.size(); i++) {
                    Rectangle r = worldRects.get(i);
                    if (r != null && r.contains(e.getX(), e.getY())) {
                        if (onWorldSelected != null) onWorldSelected.accept(worldList.get(i), 0L);
                        return;
                    }
                }
                if (refreshBtnRect != null && refreshBtnRect.contains(e.getPoint())) {
                    seedField.setText(String.valueOf(generateRandomSeed()));
                    return;
                }
                if (createBtnRect != null && createBtnRect.contains(e.getPoint())) {
                    String name = nameField.getText().trim();
                    if (name.isEmpty()) name = "block world";
                    name = resolveUniqueName(name);
                    long seed;
                    try {
                        seed = Long.parseLong(seedField.getText().trim());
                    } catch (NumberFormatException ex) {
                        seed = generateRandomSeed();
                    }
                    File dir = new File("world/" + name);
                    if (!dir.exists()) dir.mkdirs();
                    if (onWorldSelected != null) onWorldSelected.accept(name, seed);
                } else if (backBtnRect != null && backBtnRect.contains(e.getPoint())) {
                    if (onBack != null) onBack.run();
                }
            }
        });

        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int prevHover = hoveredIndex;
                int prevTrash = trashHoveredIndex;
                hoveredIndex = -1;
                trashHoveredIndex = -1;
                for (int i = 0; i < worldRects.size(); i++) {
                    Rectangle r = worldRects.get(i);
                    if (r != null && r.contains(e.getX(), e.getY())) {
                        hoveredIndex = i;
                        break;
                    }
                }
                for (int i = 0; i < trashRects.size(); i++) {
                    Rectangle t = trashRects.get(i);
                    if (t != null && t.contains(e.getPoint())) {
                        trashHoveredIndex = i;
                        break;
                    }
                }
                boolean prevCreate = createHovered;
                boolean prevBack = backHovered;
                boolean prevRefresh = refreshHovered;
                createHovered = createBtnRect != null && createBtnRect.contains(e.getPoint());
                backHovered = backBtnRect != null && backBtnRect.contains(e.getPoint());
                refreshHovered = refreshBtnRect != null && refreshBtnRect.contains(e.getPoint());
                if (prevHover != hoveredIndex || prevTrash != trashHoveredIndex || prevCreate != createHovered || prevBack != backHovered || prevRefresh != refreshHovered) {
                    setCursor(Cursor.getPredefinedCursor(
                        (hoveredIndex >= 0 || trashHoveredIndex >= 0 || createHovered || backHovered || refreshHovered) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
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

        if (backgroundImg != null) {
            g2.drawImage(backgroundImg, 0, 0, w, h, this);
        } else {
            GradientPaint gp = new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM);
            g2.setPaint(gp);
            g2.fillRect(0, 0, w, h);
        }

        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillRect(0, 0, w, h);

        drawTitle(g2, w);
        drawWorldList(g2, w, h);
        drawInputArea(g2, w, h);
    }

    private void drawTitle(Graphics2D g2, int w) {
        Font titleFont = new Font("微软雅黑", Font.BOLD, 42);
        g2.setFont(titleFont);

        String title = "选择世界";
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(title);
        int tx = (w - tw) / 2;
        int ty = 70;

        g2.setColor(TITLE_SHADOW);
        g2.drawString(title, tx + 3, ty + 3);
        g2.setColor(TITLE_COLOR);
        g2.drawString(title, tx, ty);
    }

    private void drawWorldList(Graphics2D g2, int w, int h) {
        worldRects.clear();
        trashRects.clear();

        if (worldList.isEmpty()) {
            g2.setFont(new Font("微软雅黑", Font.PLAIN, 18));
            g2.setColor(new Color(160, 140, 120));
            String msg = "暂无存档，请创建新世界";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, 140);
            return;
        }

        int startY = 120;
        int gap = 8;
        int trashSize = 36;
        int trashMargin = 6;

        for (int i = 0; i < worldList.size(); i++) {
            int cy = startY + i * (CARD_HEIGHT + gap);
            if (cy + CARD_HEIGHT < 0 || cy > h) continue;

            Rectangle rect = new Rectangle((w - CARD_WIDTH) / 2, cy, CARD_WIDTH, CARD_HEIGHT);
            worldRects.add(rect);

            boolean hovered = (i == hoveredIndex);
            g2.setColor(hovered ? CARD_BG_HOVER : CARD_BG);
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, CARD_ARC, CARD_ARC);

            if (hovered && trashHoveredIndex != i) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, CARD_ARC, CARD_ARC);
            }

            g2.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
            g2.setColor(hovered ? Color.WHITE : CARD_BORDER);
            g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, CARD_ARC, CARD_ARC);

            int nameX = rect.x + 14;
            int nameWidth = rect.width - trashSize - trashMargin * 2 - 14;
            String name = worldList.get(i);
            g2.setFont(new Font("微软雅黑", Font.BOLD, 18));
            g2.setColor(BTN_TEXT);
            FontMetrics fm = g2.getFontMetrics();
            if (fm.stringWidth(name) > nameWidth) {
                int maxChars = Math.max(1, nameWidth / fm.charWidth('W'));
                name = name.substring(0, Math.min(maxChars - 1, name.length())) + "…";
            }
            int ty = rect.y + (rect.height + fm.getAscent()) / 2 - 2;
            g2.drawString(name, nameX, ty);

            int trashX = rect.x + rect.width - trashSize - trashMargin;
            int trashY = rect.y + (rect.height - trashSize) / 2;
            Rectangle trashRect = new Rectangle(trashX, trashY, trashSize, trashSize);
            trashRects.add(trashRect);

            boolean trashHovered = (i == trashHoveredIndex);
            if (trashHovered) {
                g2.setColor(new Color(200, 60, 60, 80));
                g2.fillRoundRect(trashX, trashY, trashSize, trashSize, 7, 7);
            }
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(trashHovered ? new Color(255, 100, 100) : new Color(180, 140, 120, 180));
            g2.drawRoundRect(trashX, trashY, trashSize, trashSize, 7, 7);

            if (trashIcon != null) {
                int iconSize = (int)(trashSize * 0.65);
                int iconX = trashX + (trashSize - iconSize) / 2;
                int iconY = trashY + (trashSize - iconSize) / 2;
                g2.drawImage(trashIcon, iconX, iconY, iconSize, iconSize, null);
            }
        }
    }

    private void drawInputArea(Graphics2D g2, int w, int h) {
        int fieldWidth = 300;
        int fieldHeight = 42;
        int centerX = (w - fieldWidth) / 2;
        int nameY = h - 200;
        int gap = 10;

        nameField.setBounds(centerX, nameY, fieldWidth, fieldHeight);

        int seedY = nameY + fieldHeight + gap;
        int seedFieldWidth = fieldWidth - fieldHeight - gap;
        seedField.setBounds(centerX, seedY, seedFieldWidth, fieldHeight);

        int refreshSize = fieldHeight;
        int refreshX = centerX + seedFieldWidth + gap;
        refreshBtnRect = new Rectangle(refreshX, seedY, refreshSize, refreshSize);

        if (refreshIcon != null) {
            g2.setColor(refreshHovered ? CARD_BG_HOVER : CARD_BG);
            g2.fillRoundRect(refreshX, seedY, refreshSize, refreshSize, 8, 8);
            if (refreshHovered) {
                g2.setColor(new Color(255, 255, 255, 40));
                g2.fillRoundRect(refreshX, seedY, refreshSize, refreshSize, 8, 8);
            }
            int iconSize = (int)(refreshSize * 0.7);
            int iconX = refreshX + (refreshSize - iconSize) / 2;
            int iconY = seedY + (refreshSize - iconSize) / 2;
            g2.drawImage(refreshIcon, iconX, iconY, iconSize, iconSize, null);
        } else {
            g2.setColor(refreshHovered ? CARD_BG_HOVER : CARD_BG);
            g2.fillRoundRect(refreshX, seedY, refreshSize, refreshSize, 8, 8);
            g2.setColor(INPUT_BORDER);
            g2.drawRoundRect(refreshX, seedY, refreshSize, refreshSize, 8, 8);
        }

        int btnY = seedY + fieldHeight + gap + 5;
        int btnWidth = 140;
        int btnHeight = 45;
        int btnArc = 10;
        int totalBtnWidth = btnWidth * 2 + gap;
        int btnCenterX = (w - totalBtnWidth) / 2;

        createBtnRect = new Rectangle(btnCenterX, btnY, btnWidth, btnHeight);
        backBtnRect = new Rectangle(btnCenterX + btnWidth + gap, btnY, btnWidth, btnHeight);

        drawButton(g2, createBtnRect, createHovered, "创建新世界");
        drawButton(g2, backBtnRect, backHovered, "返回");
    }

    private void drawButton(Graphics2D g2, Rectangle rect, boolean hovered, String text) {
        g2.setColor(hovered ? CARD_BG_HOVER : CARD_BG);
        g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);

        if (hovered) {
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        }

        g2.setStroke(new BasicStroke(hovered ? 2.5f : 1.5f));
        g2.setColor(hovered ? Color.WHITE : CARD_BORDER);
        g2.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);

        g2.setFont(new Font("微软雅黑", Font.BOLD, 18));
        g2.setColor(BTN_TEXT);
        FontMetrics fm = g2.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int ty = rect.y + (rect.height + fm.getAscent()) / 2 - 2;
        g2.drawString(text, tx, ty);
    }
}