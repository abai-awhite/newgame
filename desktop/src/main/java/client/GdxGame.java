package client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;

import org.json.JSONArray;
import org.json.JSONObject;

import client.data.BlockMeta;
import client.data.BlocksData;
import client.data.ZhName;
import client.hud.HudRenderer;
import client.net.NetClient;
import client.render.ActionRenderer;
import client.render.PlayerTextures;
import client.render.TextureFactory;
import client.render.ToolTextures;
import client.render.WorldRenderer;
import client.render.WorldRenderer.DropView;
import client.render.WorldRenderer.RemotePlayer;
import client.render.WorldRenderer.Selection;
import client.tool.Tool;
import client.ui.UiKit;
import client.world.ClientWorld;
import client.world.LocalPlayer;
import client.world.LocalPlayer.Keys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * libGDX 客户端主类（移植 game.js 全部逻辑）。
 *
 * <p>坐标约定：世界坐标 y 向下；屏幕坐标 y 向下（左上角原点），绘制时用 UiKit.up() 翻转。</p>
 */
public class GdxGame extends ApplicationAdapter {

    // ==================== 常量（与 game.js 一致） ====================

    static final float TILE = 32f;
    static final int CHUNK_SIZE = 16;
    static final int WORLD_HEIGHT_TILES = 1024;
    static final float TICK_INTERVAL = 1f / 32f;
    static final int MAX_INTERACT_DISTANCE = 6;
    static final int COOLDOWN_BREAK = 5;
    static final int COOLDOWN_PLACE = 5;
    /** 枪连发射速（tick）：约 5 发/秒 */
    static final int GUN_FIRE_CD = 6;
    static final int AUTO_SELECT_RADIUS = 3;
    public static final int INVENTORY_TOTAL = 45;
    public static final int HOTBAR_SIZE = 9;
    static final int MAX_STACK = 256;

    static final String WS_DEFAULT = "ws://127.0.0.1:8081";

    /** 虚拟分辨率：所有绘制/UI 以它为基准，再等比缩放到窗口（窗口变化时整体缩放不溢出） */
    static final float VIEW_W = 1280f, VIEW_H = 720f;

    /** ESC 菜单动画时长（秒） */
    static final float ESC_ANIM_DUR = 0.25f;

    /** 背包开启动画时长（秒） */
    public static final float INV_ANIM_DUR = 0.25f;

    // ==================== 枚举与内部类 ====================

    enum Screen { MENU, WORLD_SELECT, GAME }

    /** 背包槽位 */
    public static class ItemSlot {
        public String name;
        public int count;

        public ItemSlot(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }

    /** 拖拽中的物品堆 */
    public static class Dragging {
        public ItemSlot item;
        public int source;

        public Dragging(ItemSlot item, int source) {
            this.item = item;
            this.source = source;
        }
    }

    /** 世界列表条目（服务器 worlds 消息） */
    static class WorldInfo {
        final String name;
        final String seedHash;

        WorldInfo(String name, String seedHash) {
            this.name = name;
            this.seedHash = seedHash;
        }
    }

    /** 世界选择卡片（绘制时构建位置） */
    static class WorldCard {
        String name;
        float x, y, w, h;
        boolean current;
        boolean hasDel;
        float delX, delY, delW, delH;

        WorldCard(String name, float x, float y, float w, float h, boolean current) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.current = current;
        }
    }

    /** 设置面板按键行 */
    static class KeyRow {
        final String actionId;
        final String label;
        final float x, y, w, h;

        KeyRow(String actionId, String label, float x, float y, float w, float h) {
            this.actionId = actionId;
            this.label = label;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    // ==================== 按键绑定定义 ====================

    static final String[][] BIND_ACTIONS = {
            {"w", "向前移动", "KeyW"},
            {"a", "向左移动", "KeyA"},
            {"s", "向后移动", "KeyS"},
            {"d", "向右移动", "KeyD"},
            {"space", "跳跃", "Space"},
            {"f3", "调试界面", "F3"},
            {"eKey", "背包", "KeyE"},
            {"q", "扔出物品", "KeyQ"},
            {"esc", "暂停菜单", "Escape"},
            {"alt", "冲刺", "Alt"},
    };

    // ==================== 渲染资源 ====================

    private SpriteBatch batch;
    private BlocksData blocks;
    private TextureFactory texFactory;
    private PlayerTextures playerTex;
    private ToolTextures toolTex;
    private WorldRenderer renderer;
    /** HUD 渲染（菱形/状态条/快捷栏/背包） */
    private HudRenderer hud;
    /** 玩家动作渲染（挥砍/射击/子弹） */
    private ActionRenderer actions;

    // ==================== 状态 ====================

    private Screen screen = Screen.MENU;
    private ClientWorld world = new ClientWorld();
    private LocalPlayer player = new LocalPlayer();
    private Keys keys = new Keys();

    private float camX, camY, acc;

    /** F3 位置基准（出生点=0）：welcome 时以玩家初始位置设为原点，左负右正、上正下负 */
    private float spawnX = 100f, spawnY = WORLD_HEIGHT_TILES / 2f * TILE - TILE;

    private boolean connected;
    private String myPlayerId;
    private String wsTargetUrl;
    private float reconnectIn;

    private String worldName = null;
    private String seedHash = "-";

    private boolean inventoryOpen;
    private boolean paused;
    private boolean escOpen;
    /** ESC 菜单开启动画进度（0→1，秒） */
    private float escAnimT;
    /** UI 缩放：虚拟分辨率 → 窗口（等比适配） */
    private float uiScale = 1f, uiOffX = 0f, uiOffY = 0f;
    /** 窗口物理尺寸（世界层用） */
    private float winW, winH;
    /** 世界层投影（全窗口，背景/地图铺满无黑边）与 UI 层投影（虚拟缩放居中） */
    private final Matrix4 worldProj = new Matrix4(), uiProj = new Matrix4();
    /** 鼠标：物理坐标（世界交互）+ 虚拟坐标（UI 命中） */
    private float mousePX, mousePY;
    private boolean settingsOpen;
    private boolean settingsFromGame;
    private boolean debugShown;
    /** F3+B：显示所有实体碰撞箱 */
    private boolean showHitboxes;

    private boolean multiOpen;
    private boolean autoSelectEnabled = true;
    private boolean autoStepEnabled = true;
    /** 血量显示位置：0=玩家头顶 1=物品栏上方左侧（Minecraft 式） */
    private int hpBarPos;
    /** 蓝条位置：0=玩家右侧竖条 1=物品栏上方血条下方 */
    private int manaBarPos;
    /** 饱食度位置：0=玩家左侧竖条 1=物品栏上方血条右侧 */
    private int hungerBarPos;

    // 鼠标（屏幕坐标 y 向下）
    private float mouseX, mouseY;
    private boolean mouseLeft, mouseRight;
    /** 攻击动作冷却（tick）：枪连发射速控制 */
    private int attackCd;

    private int breakCooldown, placeCooldown;
    /** Q 扔出：按住连续抛（keyDown 置 true / keyUp 置 false），qThrowCd 为抛出间隔（tick） */
    private boolean qHeld;
    private int qThrowCd;

    private ItemSlot[] inventory = new ItemSlot[INVENTORY_TOTAL];
    private Dragging draggingItem;
    private Texture invPlayerTexture;
    private int hoverIndex = -1;

    // 选中格
    private Selection selected;

    // 多人 / 掉落物
    private final Map<String, RemotePlayer> remotes = new HashMap<>();
    private List<DropView> drops = new ArrayList<>();
    private List<WorldRenderer.MobView> mobs = new ArrayList<>();

    // 冲刺粒子（世界坐标，向后拖尾）
    private static final int DASH_P_MAX = 90;
    private final float[] dPx = new float[DASH_P_MAX];
    private final float[] dPy = new float[DASH_P_MAX];
    private final float[] dPvx = new float[DASH_P_MAX];
    private final float[] dPvy = new float[DASH_P_MAX];
    private final float[] dPlife = new float[DASH_P_MAX];
    private final float[] dPmaxLife = new float[DASH_P_MAX];
    private int dPCount = 0;

    // 网络
    private NetClient net;
    private NetClient menuNet;
    /** 内嵌后端服务器（单人模式同 JVM 启动，避免拉子进程重复读 jar） */
    private server.GameServer embeddedServer;
    private String pendingWorld;
    private String pendingEnterWorld;
    private int menuWsSeq;

    // 世界列表（菜单）
    private final List<WorldInfo> worldsList = new ArrayList<>();
    private String currentWorldName = "";

    // 提示
    private String bannerText;
    private long bannerUntil;
    private String menuErrorText;
    private long menuErrorUntil;
    private String worldErrorText;
    private long worldErrorUntil;

    // 输入框 / 按键监听
    private UiKit.TextField worldNameField;
    private UiKit.TextField worldSeedField;
    private UiKit.TextField multiAddressField;
    private UiKit.TextField focusedField;
    private String listeningAction;

    // 设置面板状态
    private int settingsTab; // 0=按键 1=自动跨步 2=游戏

    // 按钮（每帧 draw 时重建位置）
    private final UiKit.Button[] menuButtons = new UiKit.Button[4];
    private final UiKit.Button[] escButtons = new UiKit.Button[4];
    private final UiKit.Button[] settingsTabs = new UiKit.Button[3];
    private UiKit.Button btnMultiConnect;
    private UiKit.Button btnMultiBack;
    private UiKit.Button btnSeedRandom;
    private UiKit.Button btnWorldCreate;
    private UiKit.Button btnWorldBack;
    private UiKit.Button btnSettingsClose;
    private UiKit.Button btnResetKeys;
    private UiKit.Button toggleAutoJump;
    private UiKit.Button chkAutoSelect;
    private UiKit.Button hpBarToggle;
    private UiKit.Button manaBarToggle;
    private UiKit.Button hungerBarToggle;
    private final List<KeyRow> keyRows = new ArrayList<>();
    private final List<WorldCard> worldCards = new ArrayList<>();

    // ==================== 生命周期 ====================

    @Override
    public void create() {
        autoSelectEnabled = ClientPrefs.getBoolean("autoSelect", true);
        autoStepEnabled = ClientPrefs.getBoolean("autoJump", true);
        hpBarPos = ClientPrefs.getInt("hpBarPos", 0);
        manaBarPos = ClientPrefs.getInt("manaBarPos", 0);
        hungerBarPos = ClientPrefs.getInt("hungerBarPos", 0);
        batch = new SpriteBatch();
        try {
            UiKit.loadFonts();
        } catch (Exception e) {
            System.err.println("中文字体加载失败: " + e.getMessage());
        }
        FileHandle fh = asset("blocks_data.js");
        blocks = BlocksData.load(fh);
        texFactory = new TextureFactory();
        playerTex = new PlayerTextures();
        toolTex = new ToolTextures();
        renderer = new WorldRenderer();
        hud = new HudRenderer(blocks, texFactory, toolTex);
        actions = new ActionRenderer(toolTex);

        net = new NetClient(new NetClient.Listener() {
            @Override
            public void onJson(JSONObject msg) {
                handleMessage(msg);
            }

            @Override
            public void onOpen() {
                onGameWsOpen();
            }

            @Override
            public void onClosed() {
                onGameWsClosed();
            }
        });
        menuNet = new NetClient(new NetClient.Listener() {
            @Override
            public void onJson(JSONObject msg) {
                handleMenuMessage(msg);
            }

            @Override
            public void onOpen() {
                if (screen == Screen.WORLD_SELECT) {
                    menuNet.send(new JSONObject().put("type", "listWorlds"));
                }
            }

            @Override
            public void onClosed() { /* 菜单连接关闭无需处理 */ }
        });

        Gdx.input.setInputProcessor(inputProcessor);
        loadKeyBinds();
    }

    /** 内部资源解析：classpath -> assets/ -> 工作目录相对路径 兜底 */
    private static FileHandle asset(String path) {
        if (Gdx.files.internal(path).exists()) return Gdx.files.internal(path);
        if (Gdx.files.internal("assets/" + path).exists()) return Gdx.files.internal("assets/" + path);
        for (String base : new String[]{"desktop/assets/", "assets/"}) {
            FileHandle f = Gdx.files.absolute(base + path);
            if (f.exists()) return f;
        }
        return Gdx.files.internal(path);
    }

    @Override
    public void render() {
        float delta = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        winW = Gdx.graphics.getWidth();
        winH = Gdx.graphics.getHeight();
        // UI 层：虚拟分辨率等比适配窗口（fit 居中）
        uiScale = Math.min(winW / VIEW_W, winH / VIEW_H);
        uiOffX = (winW - VIEW_W * uiScale) / 2f;
        uiOffY = (winH - VIEW_H * uiScale) / 2f;
        // 世界层：物理窗口坐标，背景/地图覆盖整个窗口（无黑边）
        worldProj.setToOrtho2D(0, 0, winW, winH);
        uiProj.setToOrtho2D(0, 0, winW, winH);
        uiProj.translate(uiOffX, uiOffY, 0);
        uiProj.scale(uiScale, uiScale, 1f);
        int vw = (int) VIEW_W;
        int vh = (int) VIEW_H;

        update(delta, vw, vh);

        Gdx.gl.glClearColor(0.10f, 0.10f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.begin();
        if (screen == Screen.GAME) {
            drawGame(vw, vh);
        } else if (screen == Screen.MENU) {
            drawMenu(vw, vh);
        } else if (screen == Screen.WORLD_SELECT) {
            drawWorldSelect(vw, vh);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        net.disconnect();
        menuNet.disconnect();
        if (embeddedServer != null) {
            embeddedServer.shutdown();
            embeddedServer = null;
        }
        if (playerTex != null) playerTex.dispose();
        if (toolTex != null) toolTex.dispose();
        if (texFactory != null) texFactory.dispose();
        if (batch != null) batch.dispose();
    }

    // ==================== 主更新 ====================

    private void update(float delta, int vw, int vh) {
        net.drain();
        menuNet.drain();

        if (screen == Screen.GAME && !connected && reconnectIn > 0) {
            reconnectIn -= delta;
            if (reconnectIn <= 0) {
                connectGame(wsTargetUrl);
            }
        }

        if (screen == Screen.GAME && !paused) {
            acc += delta;
            while (acc >= TICK_INTERVAL) {
                localTick();
                acc -= TICK_INTERVAL;
            }
            actions.update(delta, world, blocks, mobs, mobId -> send(new JSONObject()
                    .put("type", "attackMob").put("mobId", mobId).put("dmg", 15)));
        } else {
            acc = 0;
        }
        updateDashParticles(delta);
    }

    /** 本地模拟 32Hz（移植 game.js localTick） */
    private void localTick() {
        player.prevX = player.x;
        player.prevY = player.y;
        player.tick(keys, world, blocks, autoStepEnabled);
        if (player.dashFx > 0) spawnDashParticles();
        player.renderX = player.x;
        player.renderY = player.y;
        updateInteractions();
        updateCamera();
        // 按住 Q 连续抛出（间隔 10 tick ≈ 3.2 次/秒）
        if (qHeld) {
            if (qThrowCd > 0) qThrowCd--;
            if (qThrowCd == 0) {
                throwSelectedItem();
                qThrowCd = 10;
            }
        } else if (qThrowCd > 0) {
            qThrowCd--;
        }
        sendPlayerState();
    }

    // ==================== 相机 ====================

    private void updateCamera() {
        // 世界渲染铺满整个物理窗口：可见世界范围 = 窗口像素
        float targetCamX = player.x - winW / 2f;
        float targetCamY = player.y - winH / 2f;
        float maxCamY = WORLD_HEIGHT_TILES * TILE - winH;
        targetCamY = Math.max(0, Math.min(targetCamY, Math.max(maxCamY, 0)));
        float camOffset = inventoryOpen ? winH * 0.15f : 0;
        camX += (targetCamX - camX) * 0.1f;
        camY += (targetCamY - camY + camOffset) * 0.1f;
    }

    // ==================== 冲刺粒子 ====================

    /** 冲刺时从玩家身后生成向后飘散的粒子（每个 tick 生成数个） */
    private void spawnDashParticles() {
        int dir = player.dashFxDir;
        if (dir == 0) return;
        float cx = player.renderX + TILE / 2;
        float cy = player.renderY + TILE / 2;
        for (int i = 0; i < 3; i++) {
            if (dPCount >= DASH_P_MAX) break;
            dPx[dPCount] = cx - dir * (10 + (float) Math.random() * 18) + (float) (Math.random() - 0.5) * 10;
            dPy[dPCount] = cy + (float) (Math.random() - 0.5) * 18;
            dPvx[dPCount] = -dir * (20 + (float) Math.random() * 40);
            dPvy[dPCount] = (float) (Math.random() - 0.3) * 40 - 10;
            dPlife[dPCount] = dPmaxLife[dPCount] = 0.25f + (float) Math.random() * 0.25f;
            dPCount++;
        }
    }

    private void updateDashParticles(float delta) {
        for (int i = 0; i < dPCount; i++) {
            dPx[i] += dPvx[i] * delta;
            dPy[i] += dPvy[i] * delta;
            dPlife[i] -= delta;
        }
        int w = 0;
        for (int i = 0; i < dPCount; i++) {
            if (dPlife[i] > 0) {
                if (w != i) {
                    dPx[w] = dPx[i]; dPy[w] = dPy[i];
                    dPvx[w] = dPvx[i]; dPvy[w] = dPvy[i];
                    dPlife[w] = dPlife[i]; dPmaxLife[w] = dPmaxLife[i];
                }
                w++;
            }
        }
        dPCount = w;
    }

    /** 绘制冲刺粒子（世界层，renderer.draw 之后调用，用物理窗口尺寸） */
    private void drawDashParticles(int vw, int vh) {
        for (int i = 0; i < dPCount; i++) {
            float t = Math.max(0, Math.min(1, dPlife[i] / dPmaxLife[i]));
            Color c = new Color(0.55f, 0.85f, 1f, 0.85f * t);
            float size = 10 * (0.5f + 0.5f * t);
            float sx = dPx[i] - camX - size / 2;
            float sy = dPy[i] - camY - size / 2;
            UiKit.rectR(batch, vh, sx, sy, size, size, c, 0);
        }
    }

    // ==================== 交互（移植 game.js updateInteractions） ====================

    private void updateInteractions() {
        if (breakCooldown > 0) breakCooldown--;
        if (placeCooldown > 0) placeCooldown--;
        if (attackCd > 0) attackCd--;
        updateSelection();
        // 左键按住：武器连续攻击（枪按射速连发，剑/镐/斧挥砍动画结束后循环）
        if (mouseLeft) tryPlayerAction();
        handleBreaking();
        handlePlacing();
    }

    /** 手持武器/工具时的攻击动作：按 Tool 的工作方式执行（枪=射击，其余=挥砍） */
    private void tryPlayerAction() {
        if (paused || inventoryOpen) return;
        ItemSlot s = inventory[player.slot];
        if (s == null || s.count <= 0) return;
        Tool tool = Tool.byId(s.name);
        if (tool == null) return;   // 非工具不触发动作
        float mx = mousePX + camX;
        float my = mousePY + camY;
        if ("shoot".equals(tool.actionType)) {
            if (attackCd > 0) return;
            attackCd = GUN_FIRE_CD;
            player.startAction("shoot", mx, my);
            actions.spawnBullet(player.x + TILE / 2f, player.y + TILE / 2f, mx, my);
        } else {
            if (player.actionT > 0) return;   // 挥砍动画未结束不重开
            player.startAction("swing", mx, my);
            // 剑/镐/斧挥砍：检测附近怪物命中（玩家中心 48px 范围内）
            float pcx = player.x + TILE / 2f, pcy = player.y + TILE / 2f;
            for (WorldRenderer.MobView m : mobs) {
                if (Math.abs(m.x - pcx) < 48 && Math.abs(m.y - pcy) < 48) {
                    send(new JSONObject().put("type", "attackMob")
                            .put("mobId", m.id).put("dmg", 20));
                }
            }
        }
    }

    /** 是否为武器（不破坏方块的工具，如剑/枪）：手持时左键只做攻击，不破坏方块 */
    private boolean isWeapon(String name) {
        Tool tool = Tool.byId(name);
        return tool != null && !tool.breaksBlocks;
    }

    /** 当前手持物品名（空槽返回 null） */
    private String heldItemName() {
        ItemSlot s = inventory[player.slot];
        return (s != null && s.count > 0) ? s.name : null;
    }

    private void updateSelection() {
        if (inventoryOpen || paused) {
            selected = null;
            return;
        }
        // 世界渲染在物理窗口坐标：世界像素 = 物理鼠标 + 相机偏移
        float mwX = mousePX + camX;
        float mwY = mousePY + camY;
        int mouseTx = (int) Math.floor(mwX / TILE);
        int mouseTy = (int) Math.floor(mwY / TILE);

        Selection sel = new Selection();
        sel.x = mouseTx;
        sel.y = mouseTy;
        sel.solid = world.getTile(mouseTx, mouseTy) != BlocksData.T_AIR;

        if (!sel.solid && autoSelectEnabled && !mouseRight) {
            int[] auto = findAutoSelect(mouseTx, mouseTy);
            if (auto != null) {
                sel.x = auto[0];
                sel.y = auto[1];
                sel.solid = true;
            }
        }
        sel.inRange = isWithinRange(sel.x, sel.y);
        selected = sel;
    }

    private int[] findAutoSelect(int mx, int my) {
        for (int r = 1; r <= AUTO_SELECT_RADIUS; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int tx = mx + dx;
                    int ty = my + dy;
                    if (world.getTile(tx, ty) == BlocksData.T_AIR) continue;
                    if (!isWithinRange(tx, ty)) continue;
                    return new int[]{tx, ty};
                }
            }
        }
        return null;
    }

    private boolean isWithinRange(int tx, int ty) {
        int ptx = (int) Math.floor(player.x / TILE);
        int pty = (int) Math.floor(player.y / TILE);
        return Math.abs(tx - ptx) <= MAX_INTERACT_DISTANCE && Math.abs(ty - pty) <= MAX_INTERACT_DISTANCE;
    }

    private void handleBreaking() {
        if (!mouseLeft || breakCooldown > 0 || paused || inventoryOpen) return;
        // 手持武器（剑/枪）时不破坏方块：左键只做攻击动作
        ItemSlot held = inventory[player.slot];
        if (held != null && isWeapon(held.name)) return;
        Selection sel = selected;
        if (sel == null) return;
        if (!isWithinRange(sel.x, sel.y)) return;
        int tgt = world.getTile(sel.x, sel.y);
        if (tgt == BlocksData.T_AIR) return;
        // 液体不能左键挖掉（Terraria 式），只能用桶右键舀水
        if (tgt == BlocksData.T_WATER || tgt == BlocksData.T_LAVA) return;

        world.setLocalTile(sel.x, sel.y, BlocksData.T_AIR, 0);
        breakCooldown = COOLDOWN_BREAK;
        send(new JSONObject()
                .put("type", "blockAction")
                .put("x", sel.x).put("y", sel.y)
                .put("action", "break"));
    }

    private void handlePlacing() {
        if (!mouseRight || placeCooldown > 0 || paused || inventoryOpen) return;
        Selection sel = selected;
        if (sel == null) return;
        if (!isWithinRange(sel.x, sel.y)) return;
        ItemSlot item = inventory[player.slot];
        if (item == null || item.count <= 0) return;

        String name = item.name;
        int t = world.getTile(sel.x, sel.y);

        // —— 桶交互：空桶舀液体 / 满桶倒液体 ——
        if ("bucket".equals(name) && (t == BlocksData.T_WATER || t == BlocksData.T_LAVA)) {
            // 舀液体：消耗空桶 → 获得 water_bucket / lava_bucket（水 dr:null 不掉落物）。
            // 服务端 scoop 是"整片水域按比例缩水"（Terraria 式），目标格并不会变空气，
            // 因此本地不做任何预测（避免出现假坑），等服务端广播覆盖整片水域的水位。
            String filled = t == BlocksData.T_WATER ? "water_bucket" : "lava_bucket";
            if (!canAddToInventory(filled)) return;
            item.count--;
            if (item.count <= 0) inventory[player.slot] = null;
            addItemToInventory(filled, 1);
            placeCooldown = COOLDOWN_PLACE;
            send(new JSONObject()
                    .put("type", "blockAction")
                    .put("x", sel.x).put("y", sel.y)
                    .put("action", "break"));
            return;
        }
        if (("water_bucket".equals(name) || "lava_bucket".equals(name))
                && (t == BlocksData.T_AIR || blocks.isFluid(t))) {
            // 倒液体：消耗满桶 → 获得空桶。一桶 = 一整格（16 级系统 level 0 = 满格）。
            // 本地先显示一满格（孤立倒水时的正确结果），服务端 pour 会把它融入所属水域
            // 或保持满格，随后广播覆盖修正水位（level 48 是旧 64 级体系的残留，已废弃）。
            boolean isWater = "water_bucket".equals(name);
            int fluidType = isWater ? BlocksData.T_WATER : BlocksData.T_LAVA;
            if (!canAddToInventory("bucket")) return;
            item.count--;
            if (item.count <= 0) inventory[player.slot] = null;
            addItemToInventory("bucket", 1);
            world.setLocalTile(sel.x, sel.y, fluidType, 0);
            placeCooldown = COOLDOWN_PLACE;
            send(new JSONObject()
                    .put("type", "blockAction")
                    .put("x", sel.x).put("y", sel.y)
                    .put("action", "place")
                    .put("item", isWater ? "water" : "lava"));
            return;
        }

        // —— 普通方块放置（目标放宽为空气或液体） ——
        int blockType = blocks.tileId(name);
        if (blockType < 0) return;
        if (t != BlocksData.T_AIR && !blocks.isFluid(t)) return;
        // 玩家重叠保护
        int ptx = (int) Math.floor(player.x / TILE);
        int pty = (int) Math.floor(player.y / TILE);
        if (sel.x == ptx && sel.y == pty) return;

        world.setLocalTile(sel.x, sel.y, blockType, 0);
        item.count--;
        if (item.count <= 0) inventory[player.slot] = null;
        placeCooldown = COOLDOWN_PLACE;
        syncInventory();
        send(new JSONObject()
                .put("type", "blockAction")
                .put("x", sel.x).put("y", sel.y)
                .put("action", "place")
                .put("item", name));
    }

    /** 背包是否有空间容纳物品（同名未满堆或空槽） */
    private boolean canAddToInventory(String name) {
        for (ItemSlot s : inventory) {
            if (s != null && s.name.equals(name) && s.count < MAX_STACK) return true;
        }
        for (ItemSlot s : inventory) {
            if (s == null) return true;
        }
        return false;
    }

    // ==================== 背包系统 ====================

    private void addItemToInventory(String name, int count) {
        for (int i = 0; i < INVENTORY_TOTAL && count > 0; i++) {
            ItemSlot s = inventory[i];
            if (s != null && s.name.equals(name) && s.count < MAX_STACK) {
                int add = Math.min(count, MAX_STACK - s.count);
                s.count += add;
                count -= add;
            }
        }
        for (int i = 0; i < INVENTORY_TOTAL && count > 0; i++) {
            if (inventory[i] == null) {
                int add = Math.min(count, MAX_STACK);
                inventory[i] = new ItemSlot(name, add);
                count -= add;
            }
        }
        syncInventory();
    }

    /** 按 Q 扔出当前手持物品（消耗 1 个，方向朝鼠标，生成服务器掉落物，类 Minecraft） */
    private void throwSelectedItem() {
        if (screen != Screen.GAME || paused || inventoryOpen) return;
        ItemSlot s = inventory[player.slot];
        if (s == null || s.count <= 0) return;
        String name = s.name;
        s.count--;
        if (s.count <= 0) inventory[player.slot] = null;
        syncInventory();
        // 抛出方向朝鼠标（世界坐标 = 物理屏幕坐标 + 相机偏移；相机跟随玩家，漏加会整体拉偏方向）
        float cx = player.renderX + TILE / 2;
        float cy = player.renderY + TILE / 2;
        float mx = mousePX + camX;
        float my = mousePY + camY;
        float dx = mx - cx;
        float dy = my - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float vx, vy;
        if (len > 1f) {
            float sp = 7f;
            vx = dx / len * sp;
            vy = dy / len * sp;
        } else {
            float dir = "left".equals(player.direction) ? -1f : 1f;
            vx = dir * 6f;
            vy = -5f;
        }
        send(new JSONObject()
                .put("type", "throw")
                .put("item", name)
                .put("vx", (double) vx)
                .put("vy", (double) vy));
    }

    private void syncInventory() {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < INVENTORY_TOTAL; i++) {
            ItemSlot s = inventory[i];
            arr.put(s == null ? "" : s.name + "|" + s.count);
        }
        send(new JSONObject().put("type", "inventory").put("slots", arr));
    }

    private ItemSlot parseSlot(String s) {
        int idx = s.indexOf('|');
        if (idx < 0) return null;
        String name = s.substring(0, idx);
        int count;
        try {
            count = Integer.parseInt(s.substring(idx + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (name.isEmpty() || count <= 0) return null;
        return new ItemSlot(name, count);
    }

    private void initDefaultInventory() {
        // 初始物品：武器/工具在前，方块在后（小写名，与服务器 BlockTypeMapper 匹配）
        String[][] defaults = {
                {"sword", "1"}, {"gun", "1"}, {"pickaxe", "1"}, {"axe", "1"},
                {"grass_block", "64"}, {"dirt", "64"}, {"stone", "64"},
                {"sand", "64"}, {"oak_log", "64"}, {"bucket", "1"},
        };
        for (int i = 0; i < defaults.length; i++) {
            inventory[i] = new ItemSlot(defaults[i][0], Integer.parseInt(defaults[i][1]));
        }
        syncInventory();
    }

    private void toggleInventory() {
        inventoryOpen = !inventoryOpen;
        if (inventoryOpen) {
            hud.openInventory();
            invPlayerTexture = playerTex.get(player.direction, player.animFrame);
        } else {
            if (draggingItem != null) {
                inventory[draggingItem.source] = draggingItem.item;
                draggingItem = null;
                syncInventory();
            }
            hoverIndex = -1;
        }
    }

    /** 背包槽位点击（移植 game.js slotClick） */
    private void slotClick(int index, boolean isRight) {
        ItemSlot item = inventory[index];
        if (isRight) {
            if (draggingItem != null) {
                if (item == null) {
                    inventory[index] = new ItemSlot(draggingItem.item.name, 1);
                    draggingItem.item.count--;
                } else if (item.name.equals(draggingItem.item.name) && item.count < MAX_STACK) {
                    item.count++;
                    draggingItem.item.count--;
                } else {
                    return;
                }
                if (draggingItem.item.count <= 0) draggingItem = null;
            } else if (item != null) {
                if (item.count > 1) {
                    int half = (int) Math.ceil(item.count / 2.0);
                    item.count -= half;
                    draggingItem = new Dragging(new ItemSlot(item.name, half), index);
                } else {
                    draggingItem = new Dragging(new ItemSlot(item.name, 1), index);
                    inventory[index] = null;
                }
            }
        } else if (draggingItem != null) {
            if (index == draggingItem.source) {
                inventory[index] = draggingItem.item;
                draggingItem = null;
            } else if (item == null) {
                inventory[index] = draggingItem.item;
                draggingItem = null;
            } else if (item.name.equals(draggingItem.item.name)) {
                int add = Math.min(draggingItem.item.count, MAX_STACK - item.count);
                item.count += add;
                draggingItem.item.count -= add;
                if (draggingItem.item.count <= 0) draggingItem = null;
            } else {
                inventory[index] = draggingItem.item;
                draggingItem = new Dragging(item, index);
            }
        } else if (item != null) {
            draggingItem = new Dragging(new ItemSlot(item.name, item.count), index);
            inventory[index] = null;
        }
        syncInventory();
    }

    // ==================== 网络 ====================

    private void connectGame(String url) {
        wsTargetUrl = (url == null || url.isEmpty()) ? WS_DEFAULT : url;
        connected = false;
        reconnectIn = 0;
        net.connect(wsTargetUrl);
    }

    private void send(JSONObject msg) {
        net.send(msg);
    }

    private void sendPlayerState() {
        send(new JSONObject()
                .put("type", "playerState")
                .put("x", Math.round(player.x * 100) / 100f)
                .put("y", Math.round(player.y * 100) / 100f)
                .put("dir", player.direction)
                .put("anim", player.animFrame)
                .put("slot", player.slot)
                .put("onGround", player.onGround));
    }

    private void sendSlot() {
        send(new JSONObject().put("type", "playerState").put("slot", player.slot));
    }

    private void onGameWsOpen() {
        connected = true;
        showBanner("已连接服务器", 1500);
        JSONObject m = new JSONObject();
        if (pendingWorld != null) {
            m.put("type", "joinWorld").put("world", pendingWorld);
        } else {
            m.put("type", "join");
        }
        m.put("name", ClientPrefs.getPlayerName());
        m.put("playerId", ClientPrefs.getStablePlayerId());
        send(m);
    }

    private void onGameWsClosed() {
        connected = false;
        if (screen == Screen.GAME) {
            showBanner("连接断开，2 秒后重连...", Long.MAX_VALUE);
            reconnectIn = 2f;
        }
    }

    /** 游戏消息处理（移植 game.js handleMessage） */
    private void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        switch (type) {
            case "welcome": {
                myPlayerId = msg.optString("playerId", null);
                if (msg.has("world")) {
                    worldName = msg.optString("world", null);
                    seedHash = msg.optString("seedHash", "-");
                }
                pendingWorld = null;
                remotes.clear();
                JSONArray slots = msg.optJSONArray("slots");
                if (slots != null) {
                    Arrays.fill(inventory, null);
                    for (int i = 0; i < INVENTORY_TOTAL && i < slots.length(); i++) {
                        inventory[i] = parseSlot(slots.optString(i, ""));
                    }
                    syncInventory();
                }
                JSONArray players = msg.optJSONArray("players");
                if (players != null) {
                    for (int i = 0; i < players.length(); i++) {
                        JSONObject p = players.getJSONObject(i);
                        if (myPlayerId != null && myPlayerId.equals(p.optString("id"))) {
                            player.x = (float) p.optDouble("x", player.x);
                            player.y = (float) p.optDouble("y", player.y);
                            player.prevX = player.x;
                            player.prevY = player.y;
                            player.renderX = player.x;
                            player.renderY = player.y;
                            camX = player.x - winW / 2f;
                            camY = player.y - winH / 2f;
                            spawnX = player.x;   // F3 基准：进入世界时的位置 = (0,0)
                            spawnY = player.y;
                            if (p.has("slot")) player.slot = p.optInt("slot", player.slot);
                        }
                        remotes.put(p.optString("id"), toRemote(p));
                    }
                }
                break;
            }
            case "state": {
                JSONArray players = msg.optJSONArray("players");
                if (players != null) {
                    for (int i = 0; i < players.length(); i++) {
                        JSONObject p = players.getJSONObject(i);
                        remotes.put(p.optString("id"), toRemote(p));
                    }
                }
                JSONArray chunks = msg.optJSONArray("chunks");
                if (chunks != null) {
                    for (int i = 0; i < chunks.length(); i++) {
                        JSONObject c = chunks.getJSONObject(i);
                        try {
                            byte[] data = Base64.getDecoder().decode(c.optString("data", ""));
                            String lvStr = c.optString("lv", "");
                            byte[] lv = lvStr.isEmpty() ? null : Base64.getDecoder().decode(lvStr);
                            world.putChunk(c.optInt("cx", 0), c.optInt("cy", 0), data, lv);
                        } catch (Exception e) {
                            System.err.println("区块解码失败: " + e.getMessage());
                        }
                    }
                }
                JSONArray tiles = msg.optJSONArray("tiles");
                if (tiles != null) {
                    for (int i = 0; i < tiles.length(); i++) {
                        JSONObject t = tiles.getJSONObject(i);
                        world.applyRemoteTile(t.optInt("x", 0), t.optInt("y", 0), t.optInt("type", 0), t.optInt("lv", 0));
                    }
                }
                JSONArray dropList = msg.optJSONArray("drops");
                if (dropList != null) applyServerDrops(dropList);
                JSONArray mobList = msg.optJSONArray("mobs");
                if (mobList != null) applyServerMobs(mobList);
                break;
            }
            case "mobHit": {
                // 服务器权威：怪物接触玩家 → 扣减本地生命值
                int dmg = msg.optInt("dmg", 5);
                player.hp -= dmg;
                if (player.hp < 0) player.hp = 0;
                break;
            }
            case "dropPickup": {
                // 服务器权威：掉落物与玩家 AABB 重叠 → 被吸取。移除本地掉落物并加入背包
                int id = msg.optInt("id", -1);
                drops.removeIf(d -> d.id == id);
                String item = msg.optString("item", null);
                if (item != null && !item.isEmpty()) {
                    addItemToInventory(item, Math.max(1, msg.optInt("count", 1)));
                }
                break;
            }
            case "playerJoined": {
                JSONObject p = msg.optJSONObject("player");
                if (p != null) remotes.put(p.optString("id"), toRemote(p));
                break;
            }
            case "playerLeft": {
                remotes.remove(msg.optString("playerId"));
                break;
            }
            case "worldSwitch": {
                if (msg.has("name")) {
                    worldName = msg.optString("name", null);
                    seedHash = msg.optString("seedHash", "-");
                }
                world.clear();
                remotes.clear();
                drops = new ArrayList<>();
                if (pendingWorld == null) {
                    send(new JSONObject()
                            .put("type", "join")
                            .put("name", ClientPrefs.getPlayerName())
                            .put("playerId", ClientPrefs.getStablePlayerId()));
                }
                break;
            }
            default:
                break;
        }
    }

    /** 菜单连接消息（世界列表） */
    private void handleMenuMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        if ("worlds".equals(type)) {
            currentWorldName = msg.optString("world", "");
            worldsList.clear();
            JSONArray list = msg.optJSONArray("list");
            if (list != null) {
                for (int i = 0; i < list.length(); i++) {
                    JSONObject w = list.getJSONObject(i);
                    worldsList.add(new WorldInfo(w.optString("name", ""), w.optString("seedHash", "-")));
                }
            }
            if (pendingEnterWorld != null) {
                // 重名时服务器会改名（如 世界1_1），优先用服务器回传的实际创建名进入
                String name = msg.optString("created", "");
                if (name.isEmpty()) name = pendingEnterWorld;
                pendingEnterWorld = null;
                enterWorld(name);
            }
        } else if ("worldError".equals(type)) {
            showWorldError(msg.optString("msg", "操作失败"));
        }
    }

    private RemotePlayer toRemote(JSONObject p) {
        RemotePlayer rp = new RemotePlayer();
        rp.id = p.optString("id", "");
        rp.name = p.optString("name", "Player");
        rp.x = (float) p.optDouble("x", 0);
        rp.y = (float) p.optDouble("y", 0);
        rp.dir = p.optString("dir", "null");
        rp.anim = p.optInt("anim", 1);
        return rp;
    }

    private void applyServerDrops(JSONArray list) {
        List<DropView> next = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject d = list.getJSONObject(i);
            if (!d.has("id")) continue;
            int id = d.optInt("id");
            DropView existing = null;
            for (DropView dv : drops) {
                if (dv.id == id) {
                    existing = dv;
                    break;
                }
            }
            if (existing != null) {
                // 掉落物始终跟随服务器权威位置（服务器实体物理：重力 + 方块碰撞，支撑移除继续下落），无客户端远程磁吸
                existing.x = (float) d.optDouble("x", existing.x);
                existing.y = (float) d.optDouble("y", existing.y);
                next.add(existing);
            } else {
                DropView nv = new DropView();
                nv.id = id;
                nv.x = (float) d.optDouble("x", 0);
                nv.y = (float) d.optDouble("y", 0);
                nv.name = d.optString("name", "");
                nv.life = 0;
                nv.dead = false;
                next.add(nv);
            }
        }
        drops = next;
    }

    private void applyServerMobs(JSONArray list) {
        List<WorldRenderer.MobView> next = new ArrayList<>();
        for (int i = 0; i < list.length(); i++) {
            JSONObject m = list.getJSONObject(i);
            WorldRenderer.MobView mv = new WorldRenderer.MobView();
            mv.id = m.optInt("id");
            mv.x = (float) m.optDouble("x", 0);
            mv.y = (float) m.optDouble("y", 0);
            mv.hp = m.optInt("hp", 0);
            mv.maxHp = m.optInt("maxHp", 30);
            mv.hurt = m.optBoolean("hurt", false);
            next.add(mv);
        }
        mobs = next;
    }

    // ==================== 菜单 / 世界选择流程 ====================

    private void showMenu() {
        screen = Screen.MENU;
        net.disconnect();
        connected = false;
        pendingWorld = null;
        worldName = null;
        seedHash = "-";
        pendingEnterWorld = null;
        worldCards.clear();
        multiOpen = false;
        closeMenuConnection();
    }

    private void showWorldSelect() {
        screen = Screen.WORLD_SELECT;
        if (worldNameField == null) worldNameField = new UiKit.TextField(0, 0, 340, 42);
        if (worldSeedField == null) worldSeedField = new UiKit.TextField(0, 0, 240, 42);
        if (worldNameField.text.isEmpty()) worldNameField.text = ClientPrefs.getString("lastWorldName", "block world");
        if (worldSeedField.text.isEmpty()) worldSeedField.text = randomSeedText();
        // 直接从 world/ 目录读取世界列表，不依赖服务器
        refreshLocalWorldList();
    }

    /** 从 world/ 目录直接读取世界列表（不经过服务器）。 */
    private void refreshLocalWorldList() {
        worldsList.clear();
        try {
            java.util.List<server.WorldStore.WorldMeta> worlds = server.WorldStore.listWorlds();
            for (server.WorldStore.WorldMeta w : worlds) {
                worldsList.add(new WorldInfo(w.name, w.seedHash));
            }
        } catch (Exception e) {
            System.err.println("读取世界列表失败: " + e.getMessage());
        }
    }

    private void openMenuConnection() {
        closeMenuConnection();
        menuWsSeq++;
        menuNet.connect(WS_DEFAULT);
    }

    private void closeMenuConnection() {
        menuWsSeq++;
        menuNet.disconnect();
    }

    private void enterWorld(String name) {
        closeMenuConnection();
        pendingWorld = name;
        ClientPrefs.putString("lastWorldName", name);
        // 同 JVM 内启动后端（单人模式，端口空闲时；已占用则连接已有服务器）
        startEmbeddedServer(name);
        startGameSession(null);
    }

    /** 同 JVM 内启动后端服务器（不拉子进程，避免重复读 jar）。端口已占用则跳过。 */
    private void startEmbeddedServer(String worldName) {
        int port = 8081;
        if (isPortOpen(port)) {
            System.out.println("[内嵌服务器] 端口 " + port + " 已占用，连接已有服务器");
            return;
        }
        try {
            server.ServerConfig config = server.ServerConfig.load("server/config.json");
            server.WorldStore.WorldMeta meta = server.WorldStore.loadMeta(worldName);
            long seed = (meta != null) ? meta.seed : config.seed;
            embeddedServer = new server.GameServer(port, seed, worldName, config.chunkThreads, config.maxPlayers);
            embeddedServer.start();
            System.out.println("[内嵌服务器] 已启动 ws://localhost:" + port + " 世界=" + worldName + " 种子=" + seed);
        } catch (Exception e) {
            System.err.println("[内嵌服务器] 启动失败: " + e.getMessage());
            embeddedServer = null;
        }
    }

    private static boolean isPortOpen(int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", port), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String randomSeedText() {
        char[] chars = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(chars[(int) (Math.random() * chars.length)]);
        return sb.toString();
    }

    private void showWorldError(String text) {
        worldErrorText = text;
        worldErrorUntil = System.currentTimeMillis() + 2500;
    }

    private void showMenuError(String text) {
        menuErrorText = text;
        menuErrorUntil = System.currentTimeMillis() + 2500;
    }

    /** 进入游戏会话（重置状态并连接服务器） */
    private void startGameSession(String wsUrl) {
        screen = Screen.GAME;
        // 关键：清掉世界选择/设置里残留的输入框焦点与按键监听，
        // 否则 onKeyDown 会因 focusedField/listeningAction 非空而吞掉全部移动按键
        focusedField = null;
        listeningAction = null;
        world.clear();
        remotes.clear();
        drops = new ArrayList<>();
        player.reset();
        actions.clear();
        acc = 0;
        Arrays.fill(inventory, null);
        initDefaultInventory();
        draggingItem = null;
        inventoryOpen = false;
        paused = false;
        escOpen = false;
        settingsOpen = false;
        settingsFromGame = false;
        debugShown = false;
        showHitboxes = false;
        hoverIndex = -1;
        hud.setSlotToast(null);
        mouseLeft = false;
        mouseRight = false;
        attackCd = 0;
        breakCooldown = 0;
        placeCooldown = 0;
        showBanner("正在连接服务器...", Long.MAX_VALUE);
        connectGame(wsUrl);
    }

    private void showBanner(String text, long durationMs) {
        bannerText = text;
        bannerUntil = (durationMs == Long.MAX_VALUE) ? Long.MAX_VALUE : System.currentTimeMillis() + durationMs;
    }

    private void deleteWorld(String name) {
        // 直接在本地删除世界（不依赖服务器）
        try {
            server.WorldStore.deleteWorld(name);
            refreshLocalWorldList();
        } catch (Exception e) {
            showWorldError("删除世界失败: " + e.getMessage());
        }
    }

    private void createWorld() {
        String name = (worldNameField == null || worldNameField.text.trim().isEmpty())
                ? "block world" : worldNameField.text.trim();
        String seed = worldSeedField == null ? "" : worldSeedField.text.trim();
        ClientPrefs.putString("lastWorldName", name);
        // 直接在本地创建世界（不依赖服务器）
        try {
            server.WorldStore.WorldMeta meta = server.WorldStore.createWorld(name, seed);
            enterWorld(meta.name);
        } catch (Exception e) {
            showWorldError("创建世界失败: " + e.getMessage());
        }
    }

    private void closeSettings() {
        settingsOpen = false;
        listeningAction = null;
        if (settingsFromGame && screen == Screen.GAME) {
            escOpen = true;
            escAnimT = 0;
        }
    }

    private void toggleEsc() {
        if (inventoryOpen) {
            toggleInventory();
        } else if (!paused) {
            paused = true;
            escOpen = true;
            escAnimT = 0;
        } else {
            paused = false;
            escOpen = false;
        }
    }

    private void saveWorldRequest() {
        send(new JSONObject().put("type", "saveRequest"));
        showBanner("世界已保存", 1500);
    }

    // ==================== 按键绑定 ====================

    private String bound(String actionId) {
        return ClientPrefs.getString("bind_" + actionId, "");
    }

    private void loadKeyBinds() {
        for (String[] a : BIND_ACTIONS) {
            if (bound(a[0]).isEmpty()) {
                ClientPrefs.putString("bind_" + a[0], a[2]);
            }
        }
    }

    private boolean codeMatches(String bound, String code) {
        if ("Alt".equals(bound)) return "AltLeft".equals(code) || "AltRight".equals(code);
        return bound != null && bound.equals(code);
    }

    /** 按键 code 命中动作 id；未绑定返回 null */
    private String actionForCode(String code) {
        if (code == null) return null;
        for (String[] a : BIND_ACTIONS) {
            if (codeMatches(bound(a[0]), code)) return a[0];
        }
        return null;
    }

    private void bindKey(String actionId, String code) {
        if (code == null) return;
        for (String[] a : BIND_ACTIONS) {
            if (a[0].equals(actionId)) continue;
            String b = bound(a[0]);
            if (b.equals(code)
                    || ("AltLeft".equals(code) && b.equals("Alt"))
                    || ("AltRight".equals(code) && b.equals("Alt"))
                    || (b.equals("AltLeft") && "Alt".equals(code))
                    || (b.equals("AltRight") && "Alt".equals(code))) {
                ClientPrefs.putString("bind_" + a[0], a[2]);
            }
        }
        String store = ("AltLeft".equals(code) || "AltRight".equals(code)) ? "Alt" : code;
        ClientPrefs.putString("bind_" + actionId, store);
    }

    private void resetKeyBinds() {
        for (String[] a : BIND_ACTIONS) {
            ClientPrefs.putString("bind_" + a[0], a[2]);
        }
    }

    private String keyName(String b) {
        if ("Alt".equals(b)) return "Alt";
        switch (b) {
            case "KeyW": return "W";
            case "KeyA": return "A";
            case "KeyS": return "S";
            case "KeyD": return "D";
            case "KeyE": return "E";
            case "KeyQ": return "Q";
            case "KeyF": return "F";
            case "Space": return "空格";
            case "F3": return "F3";
            case "Escape": return "Esc";
            case "AltLeft": return "左Alt";
            case "AltRight": return "右Alt";
            case "ShiftLeft": return "左Shift";
            case "ShiftRight": return "右Shift";
            case "ControlLeft": return "左Ctrl";
            case "ControlRight": return "右Ctrl";
            case "Enter": return "Enter";
            case "Tab": return "Tab";
            case "Backquote": return "`";
            case "ArrowUp": return "↑";
            case "ArrowDown": return "↓";
            case "ArrowLeft": return "←";
            case "ArrowRight": return "→";
            case "Minus": return "-";
            case "Equal": return "=";
            case "Comma": return ",";
            case "Period": return ".";
            case "Slash": return "/";
            default:
                break;
        }
        if (b.startsWith("Key")) return b.substring(3);
        if (b.startsWith("Digit")) return b.substring(5);
        return b;
    }

    /** libGDX keycode -> 浏览器 e.code */
    private static String keycodeToCode(int keycode) {
        switch (keycode) {
            case Input.Keys.W: return "KeyW";
            case Input.Keys.A: return "KeyA";
            case Input.Keys.S: return "KeyS";
            case Input.Keys.D: return "KeyD";
            case Input.Keys.E: return "KeyE";
            case Input.Keys.Q: return "KeyQ";
            case Input.Keys.F: return "KeyF";
            case Input.Keys.SPACE: return "Space";
            case Input.Keys.F3: return "F3";
            case Input.Keys.ESCAPE: return "Escape";
            case Input.Keys.ALT_LEFT: return "AltLeft";
            case Input.Keys.ALT_RIGHT: return "AltRight";
            case Input.Keys.SHIFT_LEFT: return "ShiftLeft";
            case Input.Keys.SHIFT_RIGHT: return "ShiftRight";
            case Input.Keys.CONTROL_LEFT: return "ControlLeft";
            case Input.Keys.CONTROL_RIGHT: return "ControlRight";
            case Input.Keys.ENTER: return "Enter";
            case Input.Keys.TAB: return "Tab";
            case Input.Keys.GRAVE: return "Backquote";
            case Input.Keys.UP: return "ArrowUp";
            case Input.Keys.DOWN: return "ArrowDown";
            case Input.Keys.LEFT: return "ArrowLeft";
            case Input.Keys.RIGHT: return "ArrowRight";
            case Input.Keys.MINUS: return "Minus";
            case Input.Keys.EQUALS: return "Equal";
            case Input.Keys.COMMA: return "Comma";
            case Input.Keys.PERIOD: return "Period";
            case Input.Keys.SLASH: return "Slash";
            default:
                break;
        }
        if (keycode >= Input.Keys.NUM_0 && keycode <= Input.Keys.NUM_9) {
            return "Digit" + (keycode - Input.Keys.NUM_0);
        }
        return null;
    }

    private void setKey(String action, boolean down) {
        switch (action) {
            case "w": keys.w = down; break;
            case "a": keys.a = down; break;
            case "s": keys.s = down; break;
            case "d": keys.d = down; break;
            case "space": keys.space = down; break;
            case "alt": keys.alt = down; break;
            default: break;
        }
    }

    // ==================== 输入处理 ====================

    private final InputProcessor inputProcessor = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            onKeyDown(keycode);
            return true;
        }

        @Override
        public boolean keyUp(int keycode) {
            onKeyUp(keycode);
            return true;
        }

        @Override
        public boolean keyTyped(char ch) {
            if (focusedField != null) focusedField.type(ch);
            return true;
        }

        @Override
        public boolean touchDown(int sx, int sy, int pointer, int button) {
            onTouchDown(sx, sy, button);
            return true;
        }

        @Override
        public boolean touchUp(int sx, int sy, int pointer, int button) {
            onTouchUp(button);
            return true;
        }

        @Override
        public boolean touchDragged(int sx, int sy, int pointer) {
            // 按住拖动时坐标也实时更新：连续挖掘/放置、背包拖拽都要靠它
            mousePX = sx;
            mousePY = sy;
            mouseX = (sx - uiOffX) / uiScale;
            mouseY = (sy - uiOffY) / uiScale;
            if (screen == Screen.GAME && inventoryOpen) {
                hoverIndex = hud.invSlotAt(mouseX, mouseY);
            }
            return true;
        }

        @Override
        public boolean mouseMoved(int sx, int sy) {
            // 物理坐标（世界交互）→ 虚拟坐标（UI 命中，y 以屏幕顶端为 0，与 UI 绘制一致）
            mousePX = sx;
            mousePY = sy;
            mouseX = (sx - uiOffX) / uiScale;
            mouseY = (sy - uiOffY) / uiScale;
            if (screen == Screen.GAME && inventoryOpen) {
                hoverIndex = hud.invSlotAt(mouseX, mouseY);
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            onScrolled(amountY);
            return true;
        }
    };

    private void onKeyDown(int keycode) {
        String code = keycodeToCode(keycode);

        // 文本输入优先（菜单输入框聚焦时）
        if (focusedField != null) {
            if (keycode == Input.Keys.BACKSPACE) focusedField.backspace();
            else if (keycode == Input.Keys.ENTER || keycode == Input.Keys.ESCAPE) focusedField = null;
            return;
        }

        // 1. 按键绑定监听（设置面板）
        if (listeningAction != null) {
            if (keycode != Input.Keys.ESCAPE) bindKey(listeningAction, code);
            listeningAction = null;
            return;
        }

        // 2. 设置面板：仅响应 esc 动作关闭
        if (settingsOpen) {
            if (code != null && "esc".equals(actionForCode(code))) closeSettings();
            return;
        }

        // 世界选择：Esc 返回主菜单
        if (screen == Screen.WORLD_SELECT && keycode == Input.Keys.ESCAPE) {
            showMenu();
            return;
        }
        if (screen != Screen.GAME) return;

        // 3. 暂停菜单（esc 动作）
        if (code != null && "esc".equals(actionForCode(code))) {
            toggleEsc();
            return;
        }
        if (paused) return;

        // 4. 背包
        if (code != null && "eKey".equals(actionForCode(code))) {
            toggleInventory();
            return;
        }

        // 5. 调试界面
        if (code != null && "f3".equals(actionForCode(code))) {
            debugShown = !debugShown;
            return;
        }
        // F3+B：显示碰撞箱（仅在调试界面打开时生效）
        if (debugShown && keycode == Input.Keys.B) {
            showHitboxes = !showHitboxes;
            return;
        }

        // 6. 数字键选快捷栏
        if (keycode >= Input.Keys.NUM_1 && keycode <= Input.Keys.NUM_9) {
            player.slot = keycode - Input.Keys.NUM_1;
            showSlotName();
            sendSlot();
            return;
        }

        // 6.5 扔出物品（Q 动作：按住连续抛，方向朝鼠标）
        if (code != null && "q".equals(actionForCode(code))) {
            qHeld = true;
            if (qThrowCd <= 0) {
                throwSelectedItem();
                qThrowCd = 10;
            }
            return;
        }

        // 7. 移动/跳跃/冲刺动作
        String action = actionForCode(code);
        if (action != null) setKey(action, true);
    }

    private void onKeyUp(int keycode) {
        String code = keycodeToCode(keycode);
        if (code != null && "q".equals(actionForCode(code))) {
            qHeld = false;
        }
        if (screen != Screen.GAME) return;
        String action = actionForCode(code);
        if (action != null) setKey(action, false);
    }

    private void onTouchDown(int sx, int sy, int button) {
        // 窗口物理坐标 → 虚拟分辨率坐标（y 以屏幕顶端为 0，与 UI 绘制一致，勿翻转）
        int x = Math.round((sx - uiOffX) / uiScale);
        int y = Math.round((sy - uiOffY) / uiScale);
        switch (screen) {
            case MENU:
                handleMenuClick(x, y, button);
                break;
            case WORLD_SELECT:
                handleWorldSelectClick(x, y, button);
                break;
            case GAME:
                if (settingsOpen) {
                    handleSettingsClick(x, y, button);
                } else if (escOpen) {
                    handleEscClick(x, y, button);
                } else if (inventoryOpen) {
                    handleInventoryClick(x, y, button);
                } else {
                    if (button == 0) mouseLeft = true;
                    if (button == 1) mouseRight = true;
                }
                break;
            default:
                break;
        }
    }

    private void onTouchUp(int button) {
        if (screen != Screen.GAME || settingsOpen || escOpen || inventoryOpen) return;
        if (button == 0) mouseLeft = false;
        if (button == 1) mouseRight = false;
    }

    private void onScrolled(float amountY) {
        if (screen != Screen.GAME || paused || inventoryOpen || escOpen || settingsOpen) return;
        int dir = amountY > 0 ? -1 : 1;
        player.slot = Math.floorMod(player.slot + dir, HOTBAR_SIZE);
        showSlotName();
        sendSlot();
    }

    /** 快捷栏切换提示 */
    private void showSlotName() {
        ItemSlot item = inventory[player.slot];
        if (item == null) {
            hud.setSlotToast(null);
            return;
        }
        hud.setSlotToast(ZhName.zhBlockName(item.name, blocks));
    }

    // ==================== 点击处理 ====================

    private void handleMenuClick(float x, float y, int button) {
        if (multiOpen) {
            if (multiAddressField != null && multiAddressField.hit(x, y)) {
                focusedField = multiAddressField;
                return;
            }
            if (btnMultiConnect != null && btnMultiConnect.hit(x, y)) {
                String addr = multiAddressField.text.trim();
                if (addr.isEmpty()) {
                    showMenuError("请输入服务器地址");
                    return;
                }
                if (!addr.startsWith("ws://")) addr = "ws://" + addr;
                ClientPrefs.putString("lastServer", addr);
                startGameSession(addr);
                return;
            }
            if (btnMultiBack != null && btnMultiBack.hit(x, y)) {
                multiOpen = false;
            }
            return;
        }
        for (int i = 0; i < menuButtons.length; i++) {
            if (menuButtons[i] != null && menuButtons[i].hit(x, y)) {
                switch (i) {
                    case 0: showWorldSelect(); break;
                    case 1: multiOpen = true; break;
                    case 2:
                        settingsFromGame = false;
                        settingsOpen = true;
                        break;
                    case 3: Gdx.app.exit(); break;
                    default: break;
                }
                return;
            }
        }
    }

    private void handleWorldSelectClick(float x, float y, int button) {
        for (WorldCard c : worldCards) {
            if (c.hasDel && x >= c.delX && x <= c.delX + c.delW && y >= c.delY && y <= c.delY + c.delH) {
                deleteWorld(c.name);
                return;
            }
        }
        for (WorldCard c : worldCards) {
            if (x >= c.x && x <= c.x + c.w && y >= c.y && y <= c.y + c.h) {
                enterWorld(c.name);
                return;
            }
        }
        if (worldNameField != null && worldNameField.hit(x, y)) {
            focusedField = worldNameField;
            return;
        }
        if (worldSeedField != null && worldSeedField.hit(x, y)) {
            focusedField = worldSeedField;
            return;
        }
        if (btnSeedRandom != null && btnSeedRandom.hit(x, y)) {
            if (worldSeedField != null) worldSeedField.text = randomSeedText();
            return;
        }
        if (btnWorldCreate != null && btnWorldCreate.hit(x, y)) {
            createWorld();
            return;
        }
        if (btnWorldBack != null && btnWorldBack.hit(x, y)) {
            showMenu();
        }
    }

    private void handleInventoryClick(float x, float y, int button) {
        int idx = hud.invSlotAt(x, y);
        if (idx < 0) return;
        slotClick(idx, button == 1);
    }

    private void handleEscClick(float x, float y, int button) {
        // 面板外点击遮罩 = 继续游戏
        if (escButtons[0] != null) {
            float px = escButtons[0].x - 30;
            float py = escButtons[0].y - 70;
            float pw = 260, ph = 300;
            if (x < px || x > px + pw || y < py || y > py + ph) {
                paused = false;
                escOpen = false;
                return;
            }
        }
        for (int i = 0; i < escButtons.length; i++) {
            if (escButtons[i] != null && escButtons[i].hit(x, y)) {
                switch (i) {
                    case 0:
                        paused = false;
                        escOpen = false;
                        break;
                    case 1:
                        saveWorldRequest();
                        break;
                    case 2:
                        settingsFromGame = true;
                        settingsOpen = true;
                        escOpen = false;
                        break;
                    case 3:
                        send(new JSONObject().put("type", "saveRequest"));
                        showMenu();
                        break;
                    default:
                        break;
                }
                return;
            }
        }
    }

    private void handleSettingsClick(float x, float y, int button) {
        if (btnSettingsClose != null && btnSettingsClose.hit(x, y)) {
            closeSettings();
            return;
        }
        for (int i = 0; i < settingsTabs.length; i++) {
            if (settingsTabs[i] != null && settingsTabs[i].hit(x, y)) {
                settingsTab = i;
                listeningAction = null;
                return;
            }
        }
        if (settingsTab == 0) {
            for (KeyRow row : keyRows) {
                if (x >= row.x && x <= row.x + row.w && y >= row.y && y <= row.y + row.h) {
                    listeningAction = (row.actionId.equals(listeningAction)) ? null : row.actionId;
                    return;
                }
            }
            if (btnResetKeys != null && btnResetKeys.hit(x, y)) {
                resetKeyBinds();
            }
        } else if (settingsTab == 1) {
            if (toggleAutoJump != null && toggleAutoJump.hit(x, y)) {
                autoStepEnabled = !autoStepEnabled;
                ClientPrefs.putBoolean("autoJump", autoStepEnabled);
            }
        } else if (settingsTab == 2) {
            if (chkAutoSelect != null && chkAutoSelect.hit(x, y)) {
                autoSelectEnabled = !autoSelectEnabled;
                ClientPrefs.putBoolean("autoSelect", autoSelectEnabled);
            }
            if (hpBarToggle != null && hpBarToggle.hit(x, y)) {
                hpBarPos = (hpBarPos == 0) ? 1 : 0;
                ClientPrefs.putInt("hpBarPos", hpBarPos);
            }
            if (manaBarToggle != null && manaBarToggle.hit(x, y)) {
                manaBarPos = (manaBarPos == 0) ? 1 : 0;
                ClientPrefs.putInt("manaBarPos", manaBarPos);
            }
            if (hungerBarToggle != null && hungerBarToggle.hit(x, y)) {
                hungerBarPos = (hungerBarPos == 0) ? 1 : 0;
                ClientPrefs.putInt("hungerBarPos", hungerBarPos);
            }
        }
    }

    // ==================== 渲染 ====================

    private void drawGame(int vw, int vh) {
        // 世界层：物理窗口坐标（背景+地图铺满整个窗口，无黑边/空缺）
        batch.setProjectionMatrix(worldProj);
        renderer.draw(batch, (int) winW, (int) winH, world, blocks, texFactory, playerTex,
                camX, camY, player, drops, remotes, myPlayerId, mobs, selected, showHitboxes);
        hud.drawWorld(batch, (int) winW, (int) winH, camX, camY, player,
                hpBarPos == 0, manaBarPos, hungerBarPos);
        actions.draw(batch, (int) winH, camX, camY, player, heldItemName(), showHitboxes);
        if (showHitboxes) {
            actions.drawHitboxes(batch, (int) winH, camX, camY);
        }
        drawDashParticles((int) winW, (int) winH);
        // UI 层：虚拟坐标（缩放居中）
        batch.setProjectionMatrix(uiProj);
        if (inventoryOpen) {
            hud.drawInventory(batch, vw, vh, inventory, player, invPlayerTexture,
                    hoverIndex, draggingItem, mouseX, mouseY);
        } else {
            hud.drawStatusBars(batch, vw, vh, player, hpBarPos, manaBarPos, hungerBarPos);
            hud.drawHotbar(batch, vw, vh, inventory, player);
            hud.drawSlotToast(batch, vw, vh);
        }
        if (escOpen) drawEsc(vw, vh);
        if (settingsOpen) drawSettings(vw, vh);
        if (debugShown) drawDebug(vw, vh);
        if (bannerText != null && System.currentTimeMillis() < bannerUntil) {
            drawBanner(vw, vh);
        }
    }

    // ---------- 调试 ----------

    private void drawDebug(int vw, int vh) {
        String[] lines = {
                "FPS: " + Gdx.graphics.getFramesPerSecond(),
                "位置: X=" + (int) Math.floor((player.x - spawnX) / TILE)
                        + ", Y=" + (int) Math.floor((spawnY - player.y) / TILE)
                        + " (格子)",
                "朝向: " + player.direction + "  跳跃: " + player.jumpPhase,
                "Vy: " + String.format(Locale.ROOT, "%.1f", player.vy) + "  地面: " + player.onGround,
                "冲刺: " + player.dashCharges + "/" + player.dashMax,
                "相机: (" + (int) camX + ", " + (int) camY + ")",
                "区块缓存: " + world.chunkCount(),
                "掉落物: " + drops.size(),
                "连接: " + (connected ? "在线" : "断开"),
                "其他玩家: " + Math.max(0, remotes.size() - (myPlayerId != null ? 1 : 0)),
        };
        float bh = 11 * 16 + 8;
        UiKit.rect(batch, vh, 8, 8, 220, bh, new Color(0, 0, 0, 0.5f));
        for (int i = 0; i < lines.length; i++) {
            UiKit.textLeft(batch, vh, UiKit.fontSmall, lines[i], 16, 16 + i * 16, Color.WHITE);
        }
    }

    // ---------- 连接横幅 ----------

    private void drawBanner(int vw, int vh) {
        float tw = UiKit.textWidth(UiKit.fontNormal, bannerText);
        float w = tw + 48, h = 40;
        float x = vw / 2 - w / 2;
        float y = vh / 2 - h / 2;
        UiKit.rect(batch, vh, x, y, w, h, new Color(0, 0, 0, 0.72f));
        UiKit.frame(batch, vh, x, y, w, h, 1, new Color(1, 1, 1, 0.4f));
        UiKit.text(batch, vh, UiKit.fontNormal, bannerText, vw / 2, y + h / 2, Color.WHITE);
    }

    // ---------- ESC 菜单 ----------

    private void drawEsc(int vw, int vh) {
        // 开启动画：淡入 + 上滑（缓出）
        escAnimT = Math.min(escAnimT + Gdx.graphics.getDeltaTime(), ESC_ANIM_DUR);
        float p = escAnimT / ESC_ANIM_DUR;
        p = 1f - (1f - p) * (1f - p) * (1f - p);
        float slide = (1f - p) * 32f;
        UiKit.globalAlpha = p;
        // 遮罩铺满整个窗口（世界层），再切回 UI 层画面板
        batch.setProjectionMatrix(worldProj);
        UiKit.rectR(batch, (int) winH, 0, 0, winW, winH, new Color(0, 0, 0, 0.45f), 0);
        batch.setProjectionMatrix(uiProj);
        float w = 260, h = 300;
        float x = vw / 2 - w / 2;
        float y = vh / 2 - h / 2 + slide;
        UiKit.panel(batch, vh, x, y, w, h, new Color(0.12f, 0.12f, 0.16f, 0.95f));
        UiKit.text(batch, vh, UiKit.fontTitle, "暂停", vw / 2, y + 18, Color.WHITE);
        String worldInfo = "世界: " + (worldName == null ? "-" : worldName) + "  哈希: " + (seedHash == null ? "-" : seedHash);
        UiKit.text(batch, vh, UiKit.fontSmall, worldInfo, vw / 2, y + 52, new Color(0.78f, 0.78f, 0.83f, 1));
        String[] labels = {"继续游戏", "保存世界", "设置", "退出游戏"};
        float[] ys = {y + 70, y + 112, y + 154, y + 196};
        for (int i = 0; i < escButtons.length; i++) {
            escButtons[i] = new UiKit.Button(x + 30, ys[i], w - 60, 36, labels[i]);
            escButtons[i].updateHover(mouseX, mouseY);
            escButtons[i].draw(batch, vh);
        }
        UiKit.globalAlpha = 1f;
    }

    // ---------- 主菜单 ----------

    private void drawMenu(int vw, int vh) {
        // 背景铺满整个窗口（世界层），再切回 UI 层
        batch.setProjectionMatrix(worldProj);
        UiKit.rectR(batch, (int) winH, 0, 0, winW, winH, new Color(0x28 / 255f, 0x1e / 255f, 0x14 / 255f, 1), 0);
        UiKit.rectR(batch, (int) winH, 0, winH / 2, winW, winH / 2, new Color(0x19 / 255f, 0x12 / 255f, 0x09 / 255f, 1), 0);
        batch.setProjectionMatrix(uiProj);
        UiKit.text(batch, vh, UiKit.fontBig, "2D 沙盒游戏", vw / 2, (int) (vh * 0.24f),
                new Color(0xdcc8aa / 255f, 0xdcc8aa / 255f, 0xaa / 255f, 1));

        float btnW = 240, btnH = 56;
        float bx = (vw - btnW) / 2f;
        float by = vh * 0.42f;
        String[] labels = {"单人游戏", "多人游戏", "设置", "退出游戏"};
        for (int i = 0; i < menuButtons.length; i++) {
            menuButtons[i] = new UiKit.Button(bx, by + i * (btnH + 15), btnW, btnH, labels[i]);
            menuButtons[i].updateHover(mouseX, mouseY);
            menuButtons[i].draw(batch, vh);
        }

        if (multiOpen) {
            float my0 = by + menuButtons.length * (btnH + 15) + 4;
            float mw = 360;
            float mx = (vw - mw) / 2f;
            if (multiAddressField == null) {
                multiAddressField = new UiKit.TextField(mx, my0, mw, 42);
                multiAddressField.text = ClientPrefs.getString("lastServer", "");
                multiAddressField.placeholder = "ws://ip:port (如 ws://192.168.1.10:8081)";
            } else {
                multiAddressField.bounds.set(mx, my0, mw, 42);
            }
            multiAddressField.draw(batch, vh);
            btnMultiConnect = new UiKit.Button(mx, my0 + 48, 170, 42, "连接");
            btnMultiConnect.updateHover(mouseX, mouseY);
            btnMultiConnect.draw(batch, vh);
            btnMultiBack = new UiKit.Button(mx + 190, my0 + 48, 170, 42, "返回");
            btnMultiBack.updateHover(mouseX, mouseY);
            btnMultiBack.draw(batch, vh);
        }

        if (menuErrorText != null && System.currentTimeMillis() < menuErrorUntil) {
            UiKit.text(batch, vh, UiKit.fontSmall, menuErrorText, vw / 2, by + 4 * (btnH + 15) + 40,
                    new Color(1, 0.42f, 0.42f, 1));
        }
    }

    // ---------- 世界选择 ----------

    private void drawWorldSelect(int vw, int vh) {
        // 背景铺满整个窗口（世界层），再切回 UI 层
        batch.setProjectionMatrix(worldProj);
        UiKit.rectR(batch, (int) winH, 0, 0, winW, winH, new Color(0x28 / 255f, 0x1e / 255f, 0x14 / 255f, 1), 0);
        UiKit.rectR(batch, (int) winH, 0, winH / 2, winW, winH / 2, new Color(0x19 / 255f, 0x12 / 255f, 0x09 / 255f, 1), 0);
        batch.setProjectionMatrix(uiProj);
        UiKit.text(batch, vh, UiKit.fontTitle, "选择世界", vw / 2, 40, new Color(0xdcc8aa / 255f, 0xdcc8aa / 255f, 0xaa / 255f, 1));

        float listX = (vw - 340) / 2f;
        float listY = 90;
        worldCards.clear();
        if (worldsList.isEmpty()) {
            UiKit.text(batch, vh, UiKit.fontNormal, "暂无存档，请创建新世界", vw / 2, listY + 26,
                    new Color(0.63f, 0.55f, 0.47f, 1));
            listY += 40;
        } else {
            for (WorldInfo w : worldsList) {
                WorldCard card = new WorldCard(w.name, listX, listY, 340, 52, w.name.equals(currentWorldName));
                UiKit.panel(batch, vh, card.x, card.y, card.w, card.h, new Color(0.235f, 0.176f, 0.118f, 0.8f));
                if (card.current) {
                    UiKit.frame(batch, vh, card.x, card.y, card.w, card.h, 2,
                            new Color(0.42f, 0.79f, 0.42f, 1));
                }
                String title = w.name + (card.current ? "（当前）" : "");
                UiKit.textLeft(batch, vh, UiKit.fontNormal, title, card.x + 16, card.y + 12, Color.WHITE);
                UiKit.textLeft(batch, vh, UiKit.fontSmall, "哈希 " + (w.seedHash.isEmpty() ? "-" : w.seedHash),
                        card.x + 16, card.y + 32, new Color(0.86f, 0.78f, 0.67f, 0.85f));
                if (!card.current) {
                    card.hasDel = true;
                    card.delX = card.x + card.w - 44;
                    card.delY = card.y + 9;
                    card.delW = 34;
                    card.delH = 34;
                    UiKit.rect(batch, vh, card.delX, card.delY, 34, 34, new Color(0.78f, 0.55f, 0.47f, 0.25f));
                    UiKit.frame(batch, vh, card.delX, card.delY, 34, 34, 1.5f, new Color(0.71f, 0.55f, 0.47f, 0.7f));
                    UiKit.text(batch, vh, UiKit.fontSmall, "删", card.delX + 17, card.delY + 10, Color.WHITE);
                }
                worldCards.add(card);
                listY += 60;
            }
        }

        // 创建区
        float cy = listY + 12;
        if (worldNameField == null) worldNameField = new UiKit.TextField(listX, cy, 340, 42);
        worldNameField.bounds.set(listX, cy, 340, 42);
        worldNameField.placeholder = "世界名称";
        worldNameField.draw(batch, vh);
        if (worldSeedField == null) worldSeedField = new UiKit.TextField(listX, cy + 50, 240, 42);
        worldSeedField.bounds.set(listX, cy + 50, 240, 42);
        worldSeedField.placeholder = "种子（可修改，将派生哈希）";
        worldSeedField.draw(batch, vh);
        btnSeedRandom = new UiKit.Button(listX + 250, cy + 50, 90, 42, "随机");
        btnSeedRandom.updateHover(mouseX, mouseY);
        btnSeedRandom.draw(batch, vh);
        btnWorldCreate = new UiKit.Button(listX, cy + 100, 165, 44, "创建新世界");
        btnWorldCreate.updateHover(mouseX, mouseY);
        btnWorldCreate.draw(batch, vh);
        btnWorldBack = new UiKit.Button(listX + 175, cy + 100, 165, 44, "返回");
        btnWorldBack.updateHover(mouseX, mouseY);
        btnWorldBack.draw(batch, vh);

        if (worldErrorText != null && System.currentTimeMillis() < worldErrorUntil) {
            UiKit.text(batch, vh, UiKit.fontSmall, worldErrorText, vw / 2, cy + 152,
                    new Color(1, 0.42f, 0.42f, 1));
        }
    }

    // ---------- 设置面板 ----------

    private void drawSettings(int vw, int vh) {
        // 遮罩铺满整个窗口（世界层），再切回 UI 层
        batch.setProjectionMatrix(worldProj);
        UiKit.rect(batch, (int) winH, 0, 0, winW, winH, new Color(0, 0, 0, 0.4f));
        batch.setProjectionMatrix(uiProj);
        float w = 640, h = 420;
        float x = (vw - w) / 2f;
        float y = (vh - h) / 2f;
        UiKit.panel(batch, vh, x, y, w, h, new Color(0.09f, 0.09f, 0.12f, 0.97f));
        UiKit.text(batch, vh, UiKit.fontTitle, "设置", x + 70, y + 14, Color.WHITE);
        btnSettingsClose = new UiKit.Button(x + w - 54, y + 12, 40, 34, "×");
        btnSettingsClose.updateHover(mouseX, mouseY);
        btnSettingsClose.draw(batch, vh);

        // 左侧标签页
        float tabW = 150, tabH = 44;
        float tabX = x + 16;
        float tabY = y + 72;
        String[] tabLabels = {"按键设置", "自动跨步", "游戏设置"};
        for (int i = 0; i < settingsTabs.length; i++) {
            UiKit.Button tab = new UiKit.Button(tabX, tabY + i * (tabH + 8), tabW, tabH, tabLabels[i]);
            tab.bg.set(0.18f, 0.18f, 0.24f, 0.92f);
            tab.hover.set(0.30f, 0.30f, 0.42f, 0.95f);
            if (i == settingsTab) tab.bg.set(0.30f, 0.30f, 0.42f, 0.95f);
            tab.updateHover(mouseX, mouseY);
            tab.draw(batch, vh);
            settingsTabs[i] = tab;
        }

        float cx = x + 16 + tabW + 18;
        float cw = w - (cx - x) - 16;
        float cy = y + 72;

        if (settingsTab == 0) {
            // 按键设置：每行一个动作，点击进入监听
            float rowH = 40;
            keyRows.clear();
            for (int i = 0; i < BIND_ACTIONS.length; i++) {
                String[] a = BIND_ACTIONS[i];
                float ry = cy + i * (rowH + 6);
                UiKit.rect(batch, vh, cx, ry, cw, rowH, new Color(0.16f, 0.16f, 0.22f, 0.85f));
                UiKit.frame(batch, vh, cx, ry, cw, rowH, 1,
                        listeningAction != null && listeningAction.equals(a[0])
                                ? new Color(1, 0.85f, 0.4f, 0.9f) : new Color(1, 1, 1, 0.2f));
                UiKit.textLeft(batch, vh, UiKit.fontNormal, a[1], cx + 14, ry + (rowH - 16) / 2, Color.WHITE);
                String keyText = listeningAction != null && listeningAction.equals(a[0])
                        ? "..." : keyName(bound(a[0]));
                UiKit.text(batch, vh, UiKit.fontNormal, keyText, cx + cw - 70, ry + (rowH - 16) / 2,
                        new Color(0.86f, 0.82f, 0.72f, 1));
                keyRows.add(new KeyRow(a[0], a[1], cx, ry, cw, rowH));
            }
            btnResetKeys = new UiKit.Button(cx, cy + BIND_ACTIONS.length * (rowH + 6) + 6, 150, 38, "恢复默认");
            btnResetKeys.updateHover(mouseX, mouseY);
            btnResetKeys.draw(batch, vh);
        } else if (settingsTab == 1) {
            // 自动跨步
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "自动跨步：前方一格高方块直接走上", cx, cy + 6,
                    new Color(0.86f, 0.82f, 0.72f, 1));
            toggleAutoJump = new UiKit.Button(cx, cy + 48, 240, 46,
                    autoStepEnabled ? "自动跨步：开启" : "自动跨步：关闭");
            toggleAutoJump.updateHover(mouseX, mouseY);
            toggleAutoJump.draw(batch, vh);
        } else {
            // 游戏设置：自动选择 + 状态条位置
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "自动选择：鼠标指向空气时吸附附近方块", cx, cy + 6,
                    new Color(0.86f, 0.82f, 0.72f, 1));
            chkAutoSelect = new UiKit.Button(cx, cy + 44, 260, 36, "");
            drawCheckbox(chkAutoSelect, autoSelectEnabled, vh);
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "自动选择", cx + 48, cy + 52, Color.WHITE);
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "血量显示位置", cx, cy + 92,
                    new Color(0.86f, 0.82f, 0.72f, 1));
            hpBarToggle = new UiKit.Button(cx, cy + 124, 260, 36,
                    hpBarPos == 0 ? "玩家头顶" : "物品栏上方左侧");
            hpBarToggle.updateHover(mouseX, mouseY);
            hpBarToggle.draw(batch, vh);
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "蓝条位置", cx, cy + 172,
                    new Color(0.86f, 0.82f, 0.72f, 1));
            manaBarToggle = new UiKit.Button(cx, cy + 204, 260, 36,
                    manaBarPos == 0 ? "玩家右侧（竖条）" : "物品栏上方血条下方");
            manaBarToggle.updateHover(mouseX, mouseY);
            manaBarToggle.draw(batch, vh);
            UiKit.textLeft(batch, vh, UiKit.fontNormal, "饱食度位置", cx, cy + 252,
                    new Color(0.86f, 0.82f, 0.72f, 1));
            hungerBarToggle = new UiKit.Button(cx, cy + 284, 260, 36,
                    hungerBarPos == 0 ? "玩家左侧（竖条）" : "物品栏上方血条右侧");
            hungerBarToggle.updateHover(mouseX, mouseY);
            hungerBarToggle.draw(batch, vh);
        }
    }

    /** 复选框：按钮区域内左侧画一个方框，checked 时填充 */
    private void drawCheckbox(UiKit.Button box, boolean checked, int vh) {
        float s = box.h;
        UiKit.rect(batch, vh, box.x, box.y, s, s, new Color(0.2f, 0.2f, 0.28f, 0.95f));
        UiKit.frame(batch, vh, box.x, box.y, s, s, 2,
                checked ? new Color(0.5f, 0.9f, 0.5f, 1) : new Color(1, 1, 1, 0.4f));
        if (checked) {
            UiKit.rect(batch, vh, box.x + 8, box.y + 8, s - 16, s - 16, new Color(0.45f, 0.85f, 0.45f, 1));
        }
    }
}