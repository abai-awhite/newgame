package main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.ScreenUtils;

import block.Block;
import entity.AutoJumpSystem;
import entity.DropItem;
import entity.Player;
import main.gui.DebugOverlay;
import main.gui.EscPanel;
import main.gui.InventoryPanel;
import main.world.InfiniteMap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏主屏幕，LibGDX ApplicationAdapter 实现。
 * 负责游戏循环、渲染和输入处理。
 */
public class GameScreen extends ApplicationAdapter {

    // ==================== 核心对象 ====================

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private BitmapFont font;

    private Player player;
    private InfiniteMap infiniteMap;
    private BlockInteraction blockInteraction;
    private DebugOverlay debugOverlay;
    private InventoryPanel inventoryPanel;
    private EscPanel escPanel;

    private final List<DropItem> dropItems = new CopyOnWriteArrayList<>();

    // ==================== 游戏配置 ====================

    public static int titlesize = 32;
    public static final int TICK_RATE = 32;
    public static final int FPS = 128;
    public static long seed = 0;
    public static String worldName = "block world";
    public static final boolean ENABLE_DEBUG_LOG = false;

    public static final int WORLD_HEIGHT_TILES = 1024;
    public static final int WORLD_HEIGHT_PX = WORLD_HEIGHT_TILES * titlesize;

    // ==================== 时间控制 ====================

    private float tickAccumulator = 0;
    private static final float TICK_INTERVAL = 1.0f / TICK_RATE;
    private long lastTickTime;

    // ==================== 摄像机 ====================

    private double cameraX = 0;
    private double cameraY = 0;
    private int viewportWidth;
    private int viewportHeight;

    // ==================== 输入状态 ====================

    private boolean inventoryWasOpen = false;
    private boolean escWasOpen = false;
    private boolean prevLeftPressed = false;
    private boolean prevRightPressed = false;

    // ==================== 平滑左移动画 ====================
    private double currentShiftX = 0;
    private double targetShiftX = 0;

    // ==================== 暂停 ====================
    public volatile boolean paused = false;

    // ==================== 区块变化跟踪 ====================
    private int lastPlayerChunkX = 0;
    private int lastPlayerChunkY = 0;

    // ==================== 纹理缓存 ====================
    private java.util.Map<String, Texture> textureCache = new java.util.HashMap<>();

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        camera = new OrthographicCamera();

        viewportWidth = Gdx.graphics.getWidth();
        viewportHeight = Gdx.graphics.getHeight();
        camera.setToOrtho(true, viewportWidth, viewportHeight);

        // 初始化游戏世界
        infiniteMap = new InfiniteMap(seed, worldName);
        Block.init();
        infiniteMap.loadWorld();

        // 初始化玩家
        player = new Player(infiniteMap, titlesize);

        // 初始化背包
        inventoryPanel = new InventoryPanel(viewportWidth, viewportHeight);

        // 加载玩家数据
        InfiniteMap.PlayerData playerData = infiniteMap.loadPlayerData();
        if (playerData != null) {
            player.setPosition(playerData.playerX, playerData.playerY);
            inventoryPanel.loadAllSlotData(playerData.inventoryData);
        }

        // 初始化区块跟踪
        lastPlayerChunkX = Math.floorDiv((int) player.currentX, main.world.Chunk.SIZE);
        lastPlayerChunkY = Math.floorDiv((int) player.currentY, main.world.Chunk.SIZE);

        // 初始化方块交互
        blockInteraction = new BlockInteraction(player, infiniteMap, new BlockInteraction.InventoryCallback() {
            @Override
            public String getSelectedItemName() {
                return inventoryPanel.getSelectedItemName();
            }

            @Override
            public void consumeSelectedItem(int count) {
                inventoryPanel.consumeSelectedItem(count);
            }
        }, titlesize);

        // 初始化调试覆盖层
        debugOverlay = new DebugOverlay(player, infiniteMap);

        // 初始化 ESC 面板
        escPanel = new EscPanel(
            () -> { infiniteMap.saveWorld(); escPanel.setVisible(false); paused = false; targetShiftX = 0; },
            () -> { /* 打开设置 - 暂不实现 */ },
            () -> { Gdx.app.exit(); }
        );

        lastTickTime = System.nanoTime();
    }

    @Override
    public void render() {
        // ---- 输入处理 ----
        handleInput();

        // ---- 逻辑更新（固定时间步长） ----
        float delta = Gdx.graphics.getDeltaTime();
        tickAccumulator += delta;

        while (tickAccumulator >= TICK_INTERVAL) {
            if (!paused) {
                tick();
            }
            tickAccumulator -= TICK_INTERVAL;
        }

        // ---- 插值 ----
        float alpha = tickAccumulator / TICK_INTERVAL;
        player.interpolate(alpha);

        // ---- 摄像机更新 ----
        if (!paused) {
            updateCamera();
            updateDrops();
        }

        // ---- 渲染 ----
        ScreenUtils.clear(0.5f, 0.7f, 1.0f, 1.0f);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // 绘制方块
        renderTiles();

        // 绘制玩家
        renderPlayer();

        // 绘制掉落物
        renderDrops();

        batch.end();

        // 绘制高亮框（使用 ShapeRenderer）
        renderTileHighlight();

        // 绘制 UI（使用新投影矩阵）
        batch.getProjectionMatrix().setToOrtho2D(0, 0, viewportWidth, viewportHeight);
        batch.begin();

        inventoryPanel.render(batch, font, viewportWidth, viewportHeight);
        inventoryPanel.renderHotbar(batch, font, viewportWidth, viewportHeight);
        escPanel.render(batch, font, viewportWidth, viewportHeight);
        debugOverlay.render(batch, font);

        batch.end();
    }

    private void handleInput() {
        // 键盘输入 -> 玩家
        player.keyW = Gdx.input.isKeyPressed(Input.Keys.W);
        player.keyA = Gdx.input.isKeyPressed(Input.Keys.A);
        player.keyS = Gdx.input.isKeyPressed(Input.Keys.S);
        player.keyD = Gdx.input.isKeyPressed(Input.Keys.D);
        player.keySpace = Gdx.input.isKeyPressed(Input.Keys.SPACE);
        player.keyAlt = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);

        // 方块交互输入
        blockInteraction.mouseLeftPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        blockInteraction.mouseRightPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        blockInteraction.mouseScreenX = Gdx.input.getX();
        blockInteraction.mouseScreenY = viewportHeight - Gdx.input.getY();
        blockInteraction.mouseInPanel = true;
        blockInteraction.cameraX = cameraX;
        blockInteraction.cameraY = cameraY;

        // E 键切换背包
        if (Gdx.input.isKeyJustPressed(Input.Keys.E) && !inventoryWasOpen) {
            inventoryPanel.toggle();
            inventoryWasOpen = true;
        } else if (!Gdx.input.isKeyPressed(Input.Keys.E)) {
            inventoryWasOpen = false;
        }

        // ESC 键
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !escWasOpen) {
            escPanel.toggle();
            if (escPanel.isVisible()) {
                paused = true;
                targetShiftX = escPanel.getPanelWidth() / 2;
            } else {
                paused = false;
                targetShiftX = 0;
            }
            escWasOpen = true;
        } else if (!Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
            escWasOpen = false;
        }

        // F3 调试
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            debugOverlay.toggle();
        }

        // 数字键选择快捷栏
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) inventoryPanel.selectHotbarSlotByKey(1);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) inventoryPanel.selectHotbarSlotByKey(2);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) inventoryPanel.selectHotbarSlotByKey(3);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) inventoryPanel.selectHotbarSlotByKey(4);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) inventoryPanel.selectHotbarSlotByKey(5);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_6)) inventoryPanel.selectHotbarSlotByKey(6);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_7)) inventoryPanel.selectHotbarSlotByKey(7);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_8)) inventoryPanel.selectHotbarSlotByKey(8);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_9)) inventoryPanel.selectHotbarSlotByKey(9);
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_0)) inventoryPanel.selectHotbarSlotByKey(0);

        // 鼠标点击处理
        boolean leftPressed = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean rightPressed = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);

        if (leftPressed && !prevLeftPressed) {
            boolean escClicked = escPanel.handleClick(Gdx.input.getX(), viewportHeight - Gdx.input.getY());
            if (!escClicked) {
                boolean inventoryClicked = inventoryPanel.handleLeftPress(Gdx.input.getX(), viewportHeight - Gdx.input.getY());
                if (!inventoryClicked) {
                    inventoryPanel.handleHotbarClick(Gdx.input.getX(), viewportHeight - Gdx.input.getY(), viewportWidth, viewportHeight);
                }
            }
        }

        if (!leftPressed && prevLeftPressed) {
            inventoryPanel.handleLeftRelease(Gdx.input.getX(), viewportHeight - Gdx.input.getY());
        }

        if (leftPressed) {
            inventoryPanel.updateDragPosition(Gdx.input.getX(), viewportHeight - Gdx.input.getY());
        }

        if (rightPressed && !prevRightPressed) {
            inventoryPanel.handleRightClick(Gdx.input.getX(), viewportHeight - Gdx.input.getY());
        }

        prevLeftPressed = leftPressed;
        prevRightPressed = rightPressed;

        escPanel.handleMove(Gdx.input.getX(), viewportHeight - Gdx.input.getY());

        currentShiftX += (targetShiftX - currentShiftX) * 0.12;
    }

    private void tick() {
        player.retick();
        player.update();
        blockInteraction.update();
        player.autoJumpSystem.update();

        // 区块管理：仅在玩家跨区块时触发保存/卸载
        int px = Math.floorDiv((int) player.currentX, main.world.Chunk.SIZE);
        int py = Math.floorDiv((int) player.currentY, main.world.Chunk.SIZE);
        if (px != lastPlayerChunkX || py != lastPlayerChunkY) {
            lastPlayerChunkX = px;
            lastPlayerChunkY = py;
            infiniteMap.saveAndUnloadOutsideRadius(px, py, 8);
        }

        debugOverlay.update();
    }

    private void updateCamera() {
        viewportWidth = Gdx.graphics.getWidth();
        viewportHeight = Gdx.graphics.getHeight();
        if (viewportWidth <= 0 || viewportHeight <= 0) return;

        double targetCamX = player.currentX - viewportWidth / 2.0;
        double targetCamY = player.currentY - viewportHeight / 2.0;

        double minCamY = 0;
        double maxCamY = WORLD_HEIGHT_PX - viewportHeight;
        if (maxCamY < minCamY) {
            targetCamY = (WORLD_HEIGHT_PX - viewportHeight) / 2.0;
        } else {
            targetCamY = Math.min(Math.max(targetCamY, minCamY), maxCamY);
        }

        cameraX += (targetCamX - cameraX) * 0.1;
        double cameraOffset = inventoryPanel.isVisible() ? viewportHeight * 0.15 : 0;
        cameraY += (targetCamY - cameraY + cameraOffset) * 0.1;

        camera.position.set((float) (cameraX + currentShiftX + viewportWidth / 2.0f),
                            (float) (cameraY + viewportHeight / 2.0f), 0);
        camera.update();
    }

    private void updateDrops() {
        for (DropItem drop : dropItems) {
            if (drop.update(player.currentX, player.currentY)) {
                int added = inventoryPanel.addItem(drop.getItemName(), drop.getCount());
                if (ENABLE_DEBUG_LOG && added > 0) {
                    System.out.println("吸入背包: " + drop.getItemName() + " x" + added);
                }
            }
        }
        dropItems.removeIf(drop -> !drop.isAlive());
    }

    // ==================== 渲染方法 ====================

    private void renderTiles() {
        int startCol = (int) Math.floor(cameraX / titlesize);
        int startRow = (int) Math.floor(cameraY / titlesize);
        int endCol = (int) Math.ceil((cameraX + viewportWidth) / titlesize);
        int endRow = (int) Math.ceil((cameraY + viewportHeight) / titlesize);

        startRow = Math.max(startRow, 0);
        endRow = Math.min(endRow, WORLD_HEIGHT_TILES - 1);

        for (int row = startRow; row <= endRow; row++) {
            for (int col = startCol; col <= endCol; col++) {
                int type = infiniteMap.getTileType(col, row);
                Block block = Block.fromId(type);
                if (block != null) {
                    Texture tex = getTexture(block.getTexturePath());
                    if (tex != null) {
                        batch.draw(tex, col * titlesize, row * titlesize, titlesize, titlesize);
                    }
                }
            }
        }
    }

    private void renderPlayer() {
        Texture tex = getPlayerTexture();
        if (tex != null) {
            batch.draw(tex, (float) player.renderX, (float) player.renderY, titlesize, titlesize);
        }
    }

    private Texture getPlayerTexture() {
        String path;
        switch (player.direction) {
            case "up": path = "player/player-up-1.png"; break;
            case "down": path = "player/player-down-1.png"; break;
            case "right": path = "player/player-r-1.png"; break;
            case "left": path = "player/player-l-1.png"; break;
            default:
                int c = player.counter;
                if (c == 1 || c == 3) path = "player/player-1.png";
                else if (c == 2) path = "player/player-2.png";
                else path = "player/player-3.png";
                break;
        }
        return getTexture(path);
    }

    private void renderDrops() {
        for (DropItem drop : dropItems) {
            String texPath = main.gui.Item.getTexturePath(drop.getItemName());
            Texture tex = texPath != null ? getTexture(texPath) : null;
            if (tex != null) {
                batch.draw(tex, (float) drop.getWorldX() - 8, (float) drop.getWorldY() - 8, 16, 16);
            }
        }
    }

    private void renderTileHighlight() {
        if (!Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) return;

        // 使用 BlockInteraction 的选中方块坐标
        int tileX = blockInteraction.getSelectedTileX();
        int tileY = blockInteraction.getSelectedTileY();
        if (tileX < 0 || tileY < 0) return;

        float x = tileX * titlesize;
        float y = tileY * titlesize;

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1, 1, 1, 0.7f);
        shapeRenderer.rect(x + 1.5f, y + 1.5f, titlesize - 3, titlesize - 3);
        shapeRenderer.end();
    }

    private Texture getTexture(String path) {
        if (path == null) return null;
        Texture tex = textureCache.get(path);
        if (tex == null) {
            try {
                tex = new Texture(path);
                textureCache.put(path, tex);
            } catch (Exception e) {
                System.err.println("加载纹理失败: " + path);
                return null;
            }
        }
        return tex;
    }

    public void spawnDropItem(DropItem drop) {
        dropItems.add(drop);
    }

    @Override
    public void resize(int width, int height) {
        viewportWidth = width;
        viewportHeight = height;
        camera.setToOrtho(true, width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        for (Texture tex : textureCache.values()) {
            tex.dispose();
        }
        infiniteMap.shutdown();
    }
}
