package main;

import block.Block;
import entity.DropItem;
import entity.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import main.gui.DebugOverlay;
import main.gui.EscPanel;
import main.gui.InventoryPanel;
import main.world.InfiniteMap;

/**
 * 游戏主面板，负责图形渲染和游戏循环。
 *
 * <h3>核心设计</h3>
 * <ul>
 *   <li><b>双线程分离</b>：逻辑更新（Tick 线程）以固定 32 Hz 运行，渲染线程以最高 128 Hz 运行。
 *       通过线性插值在两次逻辑更新之间平滑玩家位置，消除低帧率带来的卡顿感。</li>
 *   <li><b>无限地图（水平） + 固定高度</b>：使用 {@link InfiniteMap} 按需生成区块，水平无限，
 *       垂直方向限制在 0～1023 格内。</li>
 *   <li><b>视锥裁剪</b>：每帧仅绘制当前摄像机视野内的 tile，大幅减少不必要的绘制调用。</li>
 *   <li><b>弹性摄像机跟随（含边界）</b>：摄像机以固定系数追赶玩家，并限制在合法世界区域内。</li>
 * </ul>
 *
 * @see Player
// * @see Tick
 * @see InfiniteMap
 */
public class Gamepanel extends JPanel implements Runnable {

    // ==================== 核心对象 ====================

    Thread gamedrawthread = new Thread(this);
    Keyboard VK = new Keyboard();
    Mouse mouse = new Mouse();
    BlockInteraction blockInteraction;
    Player player = new Player(this, VK);
    Tick tickupdate;
    Thread tickthread;
    DebugOverlay debugOverlay;
    InventoryPanel inventoryPanel;
    EscPanel escPanel;

    /** 掉落物列表 */
    private final List<DropItem> dropItems = new CopyOnWriteArrayList<>();

    /** 是否暂停（ESC 菜单打开时暂停逻辑更新） */
    public volatile boolean paused = false;

    private volatile boolean inventoryWasOpen = false;
    private boolean prevLeftPressed = false;
    private boolean prevRightPressed = false;
    private boolean escWasOpen = false;

    /** 平滑左移动画 */
    private double currentShiftX = 0;
    private double targetShiftX = 0;

    /** ESC 面板回调：打开设置 */
    public Runnable onOpenSettings;
    /** ESC 面板回调：退出到菜单 */
    public Runnable onQuitGame;

    // ==================== 插值与时间控制变量 ====================

    private volatile long lastTickTime;
    private static final long TICK_INTERVAL_NANO = 1_000_000_000L / Tick.tick;

    // ==================== 游戏配置 ====================

    public final InfiniteMap infiniteMap;

    public static int titlesize = 32;
    public static final int tick = 32;
    public static int fps = 128;
    public static long seed = 0;
    public static String worldName = "block world";
    public static final boolean ENABLE_DEBUG_LOG = false;

    // ---- 世界尺寸 ----
    /** 世界垂直方向总格数（固定） */
    public static final int WORLD_HEIGHT_TILES = 1024;
    /** 世界垂直方向像素高度 */
    public static final int WORLD_HEIGHT_PX = WORLD_HEIGHT_TILES * titlesize;

    // ==================== 摄像机 ====================

    double cameraX = 0;
    double cameraY = 0;
    private int viewportWidth;
    private int viewportHeight;

    // ==================== 构造方法 ====================

    public Gamepanel(String worldName) {
        Gamepanel.worldName = worldName;
        setPreferredSize(new Dimension(Gameframe.getsizew(), Gameframe.getsizeh()));
        setBackground(new Color(255, 255, 255, 255));
        setDoubleBuffered(true);
        addKeyListener(VK);
        setFocusTraversalKeysEnabled(false);
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setFocusable(true);

        infiniteMap = new InfiniteMap(seed, worldName);
        Block.init();
        infiniteMap.loadWorld();

        blockInteraction = new BlockInteraction(this, player, infiniteMap, mouse);
        debugOverlay = new DebugOverlay(this, player);
        inventoryPanel = new InventoryPanel(getPreferredSize().width, getPreferredSize().height);

        // 加载玩家数据
        InfiniteMap.PlayerData playerData = infiniteMap.loadPlayerData();
        if (playerData != null) {
            player.setPosition(playerData.playerX, playerData.playerY);
            inventoryPanel.loadAllSlotData(playerData.inventoryData);
        }

        escPanel = new EscPanel(
            () -> { infiniteMap.saveWorld(); escPanel.setVisible(false); paused = false; targetShiftX = 0; },
            () -> { if (onOpenSettings != null) onOpenSettings.run(); },
            () -> { if (onQuitGame != null) onQuitGame.run(); }
        );
        tickupdate = new Tick(player, this, blockInteraction, debugOverlay);
        tickthread = new Thread(tickupdate);

        viewportWidth = getPreferredSize().width;
        viewportHeight = getPreferredSize().height;

        lastTickTime = System.nanoTime();

        tickthread.start();
        gamedrawthread.start();
    }

    // ==================== 掉落物管理 ====================

    /**
     * 生成一个掉落物。
     */
    public void spawnDropItem(DropItem drop) {
        dropItems.add(drop);
    }

    /**
     * 更新所有掉落物（在渲染线程中调用）。
     */
    private void updateDrops() {
        for (DropItem drop : dropItems) {
            if (drop.update(player.currentX, player.currentY)) {
                int added = inventoryPanel.addItem(drop.getItemName(), drop.getCount());
                if (Gamepanel.ENABLE_DEBUG_LOG && added > 0) {
                    System.out.println("吸入背包: " + drop.getItemName() + " x" + added);
                }
            }
        }
        dropItems.removeIf(drop -> !drop.isAlive());
    }

    // ==================== 摄像机更新 ====================

    /**
     * 更新摄像机位置，使玩家大致居中，并受世界高度边界限制。
     */
    private void updateCamera() {
        viewportWidth = getWidth();
        viewportHeight = getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        // 理想摄像机左边缘 = 玩家世界位置 - 半屏
        double targetCamX = player.currentX - viewportWidth / 2.0;
        double targetCamY = player.currentY - viewportHeight / 2.0;

        // ---- 垂直边界限制（不允许看到世界外的部分） ----
        // 最小摄像机 Y（世界顶部完全显示时，cameraY = 0）
        double minCamY = 0;
        // 最大摄像机 Y（世界底部刚好贴住屏幕下边缘）
        double maxCamY = WORLD_HEIGHT_PX - viewportHeight;
        if (maxCamY < minCamY) {
            // 如果视口高度大于世界总像素，让摄像机居中
            targetCamY = (WORLD_HEIGHT_PX - viewportHeight) / 2.0;
        } else {
            targetCamY = Math.min(Math.max(targetCamY, minCamY), maxCamY);
        }

        // 水平方向：无限地图，不限制
        cameraX += (targetCamX - cameraX) * 0.1;

        double cameraOffset = inventoryPanel.isVisible() ? viewportHeight * 0.15 : 0;
        cameraY += (targetCamY - cameraY + cameraOffset) * 0.1;
    }

    // ==================== 地图绘制辅助 ====================

    /**
     * 根据世界格子坐标获取该 tile 对应的图片。
     * 超出世界高度范围的格子返回 null（不绘制）。
     */
    private Image getTileImage(int col, int row) {
        if (row < 0 || row >= WORLD_HEIGHT_TILES) {
            return null;
        }
        int type = infiniteMap.getTileType(col, row);
        Block block = Block.fromId(type);
        if (block != null) {
            return block.getImage();
        }
        return null;
    }

    // ==================== 渲染线程主循环 ====================

    @Override
    public void run() {
        long frameIntervalMs = 1000 / fps;
        long nextFrameTime = System.currentTimeMillis() + frameIntervalMs;

        while (gamedrawthread != null) {
            long now = System.nanoTime();
            long delta = now - lastTickTime;
            float alpha = (float) delta / TICK_INTERVAL_NANO;
            if (alpha > 1.0f) alpha = 1.0f;

            player.interpolate(alpha);
            if (!paused) {
                updateCamera();
                updateDrops();
            }

            if (VK.eKey && !inventoryWasOpen) {
                inventoryPanel.toggle();
                inventoryWasOpen = true;
            } else if (!VK.eKey) {
                inventoryWasOpen = false;
            }

            if (VK.esc && !escWasOpen) {
                escPanel.toggle();
                if (escPanel.isVisible()) {
                    paused = true;
                    targetShiftX = escPanel.getPanelWidth() / 2;
                } else {
                    paused = false;
                    targetShiftX = 0;
                }
                escWasOpen = true;
            } else if (!VK.esc) {
                escWasOpen = false;
            }

            // 处理背包拖拽和选择
            if (mouse.leftPressed && !prevLeftPressed) {
                boolean escClicked = escPanel.handleClick(mouse.mouseX, mouse.mouseY);
                if (!escClicked) {
                    boolean inventoryClicked = inventoryPanel.handleLeftPress(mouse.mouseX, mouse.mouseY);
                    if (!inventoryClicked) {
                        inventoryPanel.handleHotbarClick(mouse.mouseX, mouse.mouseY, getWidth(), getHeight());
                    }
                }
            }
            
            if (!mouse.leftPressed && prevLeftPressed) {
                inventoryPanel.handleLeftRelease(mouse.mouseX, mouse.mouseY);
            }
            
            if (mouse.leftPressed) {
                inventoryPanel.updateDragPosition(mouse.mouseX, mouse.mouseY);
            }

            // 右键选择物品
            if (mouse.rightPressed && !prevRightPressed) {
                inventoryPanel.handleRightClick(mouse.mouseX, mouse.mouseY);
            }
            
            prevLeftPressed = mouse.leftPressed;
            prevRightPressed = mouse.rightPressed;

            // 数字键选择快捷栏
            if (VK.num1) inventoryPanel.selectHotbarSlotByKey(1);
            if (VK.num2) inventoryPanel.selectHotbarSlotByKey(2);
            if (VK.num3) inventoryPanel.selectHotbarSlotByKey(3);
            if (VK.num4) inventoryPanel.selectHotbarSlotByKey(4);
            if (VK.num5) inventoryPanel.selectHotbarSlotByKey(5);
            if (VK.num6) inventoryPanel.selectHotbarSlotByKey(6);
            if (VK.num7) inventoryPanel.selectHotbarSlotByKey(7);
            if (VK.num8) inventoryPanel.selectHotbarSlotByKey(8);
            if (VK.num9) inventoryPanel.selectHotbarSlotByKey(9);
            if (VK.num0) inventoryPanel.selectHotbarSlotByKey(0);

            escPanel.handleMove(mouse.mouseX, mouse.mouseY);

            currentShiftX += (targetShiftX - currentShiftX) * 0.12;

            repaint();

            try {
                long remaining = nextFrameTime - System.currentTimeMillis();
                if (remaining > 0) {
                    Thread.sleep(remaining);
                } else {
                    nextFrameTime = System.currentTimeMillis(); // 防止追赶
                }
                nextFrameTime += frameIntervalMs;
            } catch (InterruptedException e) {
                System.out.println("渲染线程被中断");
                break;
            }
        }
    }

    public void onTickComplete() {
        lastTickTime = System.nanoTime();
    }

    // ==================== 绘制方法 ====================

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        AffineTransform old = g2.getTransform();

        double shiftX = currentShiftX;
        g2.translate(-cameraX - shiftX, -cameraY);

        // 计算可见 tile 范围，并钳制在世界高度内
        int startCol = (int) Math.floor(cameraX / titlesize);
        int startRow = (int) Math.floor(cameraY / titlesize);
        int endCol   = (int) Math.ceil((cameraX + viewportWidth)  / titlesize);
        int endRow   = (int) Math.ceil((cameraY + viewportHeight) / titlesize);

        // 限制行范围在世界高度内，避免绘制越界
        startRow = Math.max(startRow, 0);
        endRow   = Math.min(endRow, WORLD_HEIGHT_TILES - 1);

        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                Image tileImg = getTileImage(col, row);
                if (tileImg != null) {
                    int drawX = col * titlesize;
                    int drawY = row * titlesize;
                    g2.drawImage(tileImg, drawX, drawY, titlesize, titlesize, null);
                }
            }
        }

        player.paintComponent(g2);

        blockInteraction.renderTileHighlight(g2);

        for (DropItem drop : dropItems) {
            drop.render(g2, cameraX, cameraY);
        }

        g2.setTransform(old);

        inventoryPanel.render(g2);
        
        // 始终渲染快捷栏
        inventoryPanel.renderHotbar(g2, getWidth(), getHeight());

        escPanel.render(g2, getWidth(), getHeight());

        debugOverlay.render(g2);
    }
}