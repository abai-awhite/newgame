/**
 * 2D 沙盒游戏 - Web 前端（前后端一体版）
 *
 * 架构：大部分逻辑（玩家物理/交互/背包）在浏览器本地计算，
 * 服务器只做权威存储、同步广播与少量逻辑。
 *
 * 职责：
 * - 主菜单（单人/多人/设置）
 * - 本地物理模拟（移动/跳跃/二段跳/冲刺/自动跳跃 + AABB 碰撞）
 * - 本地方块交互（DDA 射线选中/放置/破坏，即时反馈 + 上报服务器）
 * - 45 槽背包（拖拽/右键分半/堆叠/数字键选槽）
 * - 渲染（方块/玩家贴图/掉落物/天气/其他玩家）+ Esc/F3
 */

(() => {
    'use strict';

    // ==================== 基础配置 ====================

    const TILE_SIZE = 32;
    const CHUNK_SIZE = 16;
    const WORLD_HEIGHT_TILES = 1024;
    const WORLD_HEIGHT_PX = WORLD_HEIGHT_TILES * TILE_SIZE;
    const TICK_INTERVAL = 1000 / 32;   // 本地模拟 32Hz
    const MAX_INTERACT_DISTANCE = 6;   // 交互距离（格）
    const COOLDOWN_BREAK = 5;
    const COOLDOWN_PLACE = 5;
    const AUTO_SELECT_RADIUS = 3;      // 自动选择搜索半径（格，鼠标指向空气时吸附附近方块）
    const INVENTORY_TOTAL = 45;
    const HOTBAR_SIZE = 9;
    const MAX_STACK = 256;

    // 方块类型常量（与 core Chunk 一致，ID = Minecraft 原版方块 ID，含 1.21 及更早版本）
    const T_AIR = 0, T_STONE = 1, T_GRASS = 8, T_DIRT = 9, T_WATER = 32, T_SAND = 34, T_FOREST = 46;
    const T_LAVA = 33;

    // ==================== 方块数据（blocks_data.js 提供，Minecraft 1.21.1 全量） ====================

    /** 物品名哈希生成 fallback 颜色（纹理缺失/非方块物品时兜底显示） */
    function fallbackColor(name) {
        let h = 0;
        for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
        return `hsl(${h % 360}, 55%, 55%)`;
    }

    /** 方块 ID -> {name, displayName, solid, transparent, stackSize, hardness, drops, texture, color} */
    const TILE_META = {};
    /** 物品名 -> 方块 ID（放置用） */
    const ITEM_TILE = {};
    /** 方块 ID -> 物品名 */
    const TILE_ITEM = {};

    function initBlockData() {
        const src = window.BLOCKS_DATA || {};
        for (const idStr of Object.keys(src)) {
            const id = Number(idStr);
            const b = src[idStr];
            TILE_META[id] = {
                name: b.n,
                displayName: b.d || b.n,
                solid: !!b.s,
                transparent: !!b.tr,
                stackSize: b.st || 64,
                hardness: b.h || 0,
                drops: b.dr || null,
                texture: b.t ? 'textures/block/' + b.t + '.png' : null,
                color: fallbackColor(b.n || ('#' + id)),
            };
            // 所有方块（非空气类）均可作为物品持有/放置，纹理程序化绘制
            if (b.n && b.n !== 'air' && b.n !== 'void_air' && b.n !== 'cave_air') {
                ITEM_TILE[b.n] = id;
                TILE_ITEM[id] = b.n;
            }
        }
    }
    initBlockData();

    function isSolid(type) {
        const m = TILE_META[type];
        return m ? m.solid : false;
    }

    // ==================== DOM ====================

    const $ = (id) => document.getElementById(id);
    const canvas = $('game-canvas');
    const ctx = canvas.getContext('2d');
    const hotbarEl = $('hotbar');
    const debugEl = $('debug-overlay');
    const bannerEl = $('connection-banner');
    const mainMenuEl = $('main-menu');
    const menuButtonsEl = document.querySelector('.menu-buttons');
    const worldSelectEl = $('world-select');
    const gameContainerEl = $('game-container');
    const invPanelEl = $('inventory-panel');
    const invGridEl = $('inventory-grid');
    const invPlayerImgEl = $('inv-player-img');
    // 合成 3×3 网格（仅界面布局，暂无合成逻辑）
    const craftGridEl = $('craft-slot-grid');
    for (let i = 0; i < 9; i++) {
        const s = document.createElement('div');
        s.className = 'craft-slot';
        craftGridEl.appendChild(s);
    }
    const escMenuEl = $('esc-menu');
    const settingsPanelEl = $('settings-panel');

    // ==================== 状态 ====================

    const state = {
        mode: 'menu',             // menu | game
        connected: false,
        myPlayerId: null,
        // 服务器广播的其他玩家（含自己，渲染时跳过自己）
        remotePlayers: new Map(),
        chunks: new Map(),        // "cx,cy" -> Uint8Array(256)
        // 服务器掉落物（带 id，前端磁吸/吸入；拾取上报服务器权威移除）
        serverDrops: [],
        // 本地相机
        camera: { x: 0, y: 0 },
        selected: null,           // {x,y} 选中方块格
        paused: false,
        inventoryOpen: false,
        // 当前世界信息（服务器 welcome/worldSwitch 下发：name/seed/seedHash）
        worldInfo: null,
        // 待进入的世界名（joinWorld 流程中置位，welcome 到达后清除）
        pendingWorld: null,
    };

    // 本地玩家（LocalSim 状态）
    const player = {
        x: 100, y: 1024 / 2 * TILE_SIZE - TILE_SIZE,
        prevX: 0, prevY: 0, renderX: 0, renderY: 0,
        vx: 0, vy: 0,
        onGround: false,
        direction: 'null',
        animFrame: 1, incrementer: 0,
        jumpCount: 0, jumpPhase: 'none', jumpCooldown: 0, jumpKeyHeld: false,
        dashCharges: 5, dashMax: 5, dashKeyHeld: false, dashVX: 0, dashRecharge: 0,
        slot: 0,
        // 自动跳跃
        autoJump: { active: false, lastDir: 0, recovery: 0, requiredVY: 0 },
        // 跨沟状态（一格宽沟直接滑过）：null 或 { dir, startEdge, endEdge }
        crossGap: null,
    };

    // 输入（字段名对应原版 Keyboard：w/a/s/d/space/alt）
    const input = {
        keys: { w: false, a: false, s: false, d: false, space: false, alt: false },
        mouse: { x: 0, y: 0, left: false, right: false },
        viewport: { w: window.innerWidth, h: window.innerHeight },
    };

    // 背包：45 槽，每槽 {name, count} 或 null
    const inventory = new Array(INVENTORY_TOTAL).fill(null);
    // 拖拽中的物品（模拟鼠标拿起的堆叠）
    let draggingItem = null;

    // 交互冷却
    let breakCooldown = 0;
    let placeCooldown = 0;

    // 自动选择方块（设置面板可开关，localStorage 持久化；放置时临时关闭以保证框跟鼠标）
    let autoSelectEnabled = localStorage.getItem('autoSelect') !== '0';
    // 自动跨步（设置面板可开关，localStorage 持久化；前方一格高方块直接走上）
    let autoStepEnabled = localStorage.getItem('autoJump') !== '0';

    // ==================== 按键绑定（原版 KeyBindingConfig：动作 -> 单个按键） ====================

    /** 动作列表：id=字段名 label=显示名 defaults=默认键 code。'Alt' 特殊：匹配左右 Alt。 */
    const BIND_ACTIONS = [
        { id: 'w', label: '向前移动', defaults: 'KeyW' },
        { id: 'a', label: '向左移动', defaults: 'KeyA' },
        { id: 's', label: '向后移动', defaults: 'KeyS' },
        { id: 'd', label: '向右移动', defaults: 'KeyD' },
        { id: 'space', label: '跳跃', defaults: 'Space' },
        { id: 'f3', label: '调试界面', defaults: 'F3' },
        { id: 'eKey', label: '背包', defaults: 'KeyE' },
        { id: 'esc', label: '暂停菜单', defaults: 'Escape' },
        { id: 'alt', label: '冲刺', defaults: 'Alt' },
    ];

    /** 当前按键绑定：动作 id -> e.code（'Alt' 表示左右 Alt 通用） */
    const keyBinds = loadKeyBinds();
    /** 正在监听按键的动作 id（null 表示未在监听，原版 listeningRow） */
    let listeningAction = null;

    function loadKeyBinds() {
        let saved = {};
        try { saved = JSON.parse(localStorage.getItem('keyBinds') || '{}'); } catch (e) { /* ignore */ }
        const binds = {};
        for (const a of BIND_ACTIONS) {
            binds[a.id] = typeof saved[a.id] === 'string' && saved[a.id] ? saved[a.id] : a.defaults;
        }
        return binds;
    }

    function saveKeyBinds() {
        localStorage.setItem('keyBinds', JSON.stringify(keyBinds));
    }

    /** 按键 code 是否匹配某动作（Alt 特殊处理左右键） */
    function codeMatches(bound, code) {
        if (bound === 'Alt') return code === 'AltLeft' || code === 'AltRight';
        return bound === code;
    }

    /** e.code 命中的动作 id（按绑定表查，未绑定返回 null） */
    function actionForCode(code) {
        for (const a of BIND_ACTIONS) {
            if (codeMatches(keyBinds[a.id], code)) return a.id;
        }
        return null;
    }

    /** e.code -> 显示名 */
    function keyName(bound) {
        if (bound === 'Alt') return 'Alt';
        const map = {
            KeyW: 'W', KeyA: 'A', KeyS: 'S', KeyD: 'D', KeyE: 'E', KeyQ: 'Q', KeyF: 'F',
            Space: '空格', F3: 'F3', Escape: 'Esc',
            AltLeft: '左Alt', AltRight: '右Alt',
            ShiftLeft: '左Shift', ShiftRight: '右Shift',
            ControlLeft: '左Ctrl', ControlRight: '右Ctrl',
            Enter: 'Enter', Tab: 'Tab', Backquote: '`',
            ArrowUp: '↑', ArrowDown: '↓', ArrowLeft: '←', ArrowRight: '→',
            Minus: '-', Equal: '=', Comma: ',', Period: '.', Slash: '/',
        };
        if (map[bound]) return map[bound];
        if (bound.startsWith('Key')) return bound.slice(3);
        if (bound.startsWith('Digit')) return bound.slice(5);
        return bound;
    }

    /** 渲染按键设置列表（原版 drawKeySettings：点击行进入监听） */
    function renderKeyBindings() {
        const list = $('keybind-list');
        if (!list) return;
        list.innerHTML = '';
        for (const a of BIND_ACTIONS) {
            const row = document.createElement('div');
            row.className = 'keybind-row' + (listeningAction === a.id ? ' listening' : '');

            const label = document.createElement('span');
            label.className = 'keybind-label';
            label.textContent = a.label;

            const key = document.createElement('span');
            key.className = 'keybind-key';
            key.textContent = listeningAction === a.id ? '...' : keyName(keyBinds[a.id]);

            row.appendChild(label);
            row.appendChild(key);
            row.addEventListener('click', () => {
                // 再次点击正在监听的行动作：取消监听
                listeningAction = listeningAction === a.id ? null : a.id;
                renderKeyBindings();
            });
            list.appendChild(row);
        }
    }

    /** 将按键绑定到动作（单键；自动从其他动作移除同键避免冲突） */
    function bindKey(actionId, code) {
        if (!code) return;
        for (const a of BIND_ACTIONS) {
            if (a.id === actionId) continue;
            // 同一 e.code 已被该动作占用时移除；Alt 与 AltLeft/AltRight 视为同键
            if (keyBinds[a.id] === code ||
                (code === 'AltLeft' && keyBinds[a.id] === 'Alt') ||
                (code === 'AltRight' && keyBinds[a.id] === 'Alt') ||
                (keyBinds[a.id] === 'AltLeft' && code === 'Alt') ||
                (keyBinds[a.id] === 'AltRight' && code === 'Alt')) {
                keyBinds[a.id] = a.defaults;
            }
        }
        keyBinds[actionId] = (code === 'AltLeft' || code === 'AltRight') ? 'Alt' : code;
        saveKeyBinds();
    }

    // 网络
    let ws = null;
    let wsTargetUrl = null;

    // 世界选择菜单用的临时连接（仅用于 listWorlds/createWorld/deleteWorld）
    let menuWs = null;
    let menuWsSeq = 0;
    // 创建世界后待进入的世界名（服务器回列表后自动进入）
    let pendingEnterWorld = null;

    // 本地模拟循环
    let simTimer = null;

    // ==================== 主菜单逻辑 ====================

    function showMenu() {
        state.mode = 'menu';
        mainMenuEl.classList.remove('hidden');
        gameContainerEl.classList.add('hidden');
        stopSimulation();
        if (ws) { ws.close(); ws = null; }
        // 世界选择状态复位（回到主菜单按钮）
        state.pendingWorld = null;
        state.worldInfo = null;
        pendingEnterWorld = null;
        worldSelectEl.classList.add('hidden');
        menuButtonsEl.classList.remove('hidden');
        closeMenuConnection();
    }

    // ==================== 世界选择（单人模式） ====================

    function showWorldSelect() {
        menuButtonsEl.classList.add('hidden');
        worldSelectEl.classList.remove('hidden');
        const nameInput = $('world-name');
        if (!nameInput.value) nameInput.value = localStorage.getItem('lastWorldName') || 'block world';
        if (!$('world-seed').value) $('world-seed').value = randomSeedText();
        openMenuConnection();
    }

    function hideWorldSelect() {
        worldSelectEl.classList.add('hidden');
        menuButtonsEl.classList.remove('hidden');
        pendingEnterWorld = null;
        closeMenuConnection();
    }

    /** 打开世界列表连接并请求世界列表（服务器权威）。 */
    function openMenuConnection() {
        closeMenuConnection();
        const seq = ++menuWsSeq;
        try {
            menuWs = new WebSocket(wsTargetUrl || `ws://${location.hostname}:8081`);
        } catch (e) {
            showWorldError('无法连接服务器');
            return;
        }
        menuWs.onopen = () => menuWs.send(JSON.stringify({ type: 'listWorlds' }));
        menuWs.onmessage = (e) => {
            let msg;
            try { msg = JSON.parse(e.data); } catch (err) { return; }
            if (msg.type === 'worlds') {
                renderWorldList(msg);
                // 创建世界成功后自动进入
                if (pendingEnterWorld) {
                    const name = pendingEnterWorld;
                    pendingEnterWorld = null;
                    enterWorld(name);
                }
            } else if (msg.type === 'worldError') {
                showWorldError(msg.msg);
            }
        };
        menuWs.onerror = () => showWorldError('无法连接服务器');
        menuWs.onclose = () => { if (seq === menuWsSeq) menuWs = null; };
    }

    function closeMenuConnection() {
        menuWsSeq++;
        if (menuWs) {
            try { menuWs.close(); } catch (e) { /* ignore */ }
            menuWs = null;
        }
    }

    function renderWorldList(msg) {
        const listEl = $('world-list');
        listEl.innerHTML = '';
        if (!msg.list || !msg.list.length) {
            listEl.innerHTML = '<div class="world-empty">暂无存档，请创建新世界</div>';
            return;
        }
        for (const w of msg.list) {
            const card = document.createElement('div');
            card.className = 'world-card' + (w.name === msg.world ? ' current' : '');
            card.innerHTML =
                `<div class="world-info">
                    <div class="world-name">${escapeHtml(w.name)}${w.name === msg.world ? '（当前）' : ''}</div>
                    <div class="world-seed">哈希 ${escapeHtml(w.seedHash || '-')}</div>
                </div>`;
            if (w.name !== msg.world) {
                const del = document.createElement('button');
                del.className = 'world-delete';
                del.title = '删除世界';
                const icon = document.createElement('img');
                icon.src = 'icon/trash.png';
                icon.alt = '删除';
                del.appendChild(icon);
                del.addEventListener('click', (e) => {
                    e.stopPropagation();
                    if (confirm(`确定删除世界「${w.name}」？此操作不可恢复！`)) {
                        if (menuWs && menuWs.readyState === WebSocket.OPEN) {
                            menuWs.send(JSON.stringify({ type: 'deleteWorld', name: w.name }));
                        }
                    }
                });
                card.appendChild(del);
            }
            card.addEventListener('click', () => enterWorld(w.name));
            listEl.appendChild(card);
        }
    }

    /** 进入指定世界：关闭菜单连接，建立游戏连接并发送 joinWorld。 */
    function enterWorld(name) {
        closeMenuConnection();
        state.pendingWorld = name;
        localStorage.setItem('lastWorldName', name);
        enterGame(null);
    }

    /** 生成随机种子文本（服务器会据此派生双层哈希） */
    function randomSeedText() {
        const chars = '0123456789abcdef';
        let s = '';
        for (let i = 0; i < 16; i++) s += chars[Math.floor(Math.random() * chars.length)];
        return s;
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, (c) => (
            { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
        ));
    }

    function showWorldError(text) {
        const el = $('world-error');
        el.textContent = text;
        el.classList.remove('hidden');
        setTimeout(() => el.classList.add('hidden'), 2500);
    }

    function enterGame(wsUrl) {
        state.mode = 'game';
        mainMenuEl.classList.add('hidden');
        worldSelectEl.classList.add('hidden');
        gameContainerEl.classList.remove('hidden');
        $('multi-connect').classList.add('hidden');
        $('menu-error').classList.add('hidden');
        state.connected = false;
        state.remotePlayers.clear();
        state.chunks.clear();
        state.serverDrops = [];
        resetPlayer();
        inventory.fill(null);
        initDefaultInventory();
        draggingItem = null;
        state.inventoryOpen = false;
        invPanelEl.classList.add('hidden');
        escMenuEl.classList.remove('esc-open');
        settingsPanelEl.classList.add('hidden');
        state.paused = false;
        debugEl.style.display = 'none';
        bannerEl.textContent = '正在连接服务器...';
        bannerEl.classList.add('visible');
        connect(wsUrl);
    }

    function initDefaultInventory() {
        const defaults = [
            { name: 'Grass', count: 64 }, { name: 'Dirt', count: 64 },
            { name: 'Stone', count: 64 }, { name: 'Sand', count: 64 },
            { name: 'Wood', count: 64 },
        ];
        for (let i = 0; i < defaults.length && i < HOTBAR_SIZE; i++) {
            inventory[i] = { ...defaults[i] };
        }
        renderHotbar();
        syncInventory();
    }

    function resetPlayer() {
        player.x = 100;
        player.y = 1024 / 2 * TILE_SIZE - TILE_SIZE;
        player.prevX = player.x; player.prevY = player.y;
        player.renderX = player.x; player.renderY = player.y;
        player.vy = 0; player.vx = 0;
        player.onGround = false;
        player.direction = 'null';
        player.jumpCount = 0; player.jumpPhase = 'none';
        player.dashCharges = player.dashMax = 5;
        player.animFrame = 1; player.incrementer = 0;
        player.slot = 0;
        state.camera = { x: player.x - input.viewport.w / 2, y: player.y - input.viewport.h / 2 };
    }

    // ==================== WebSocket ====================

    function connect(url) {
        wsTargetUrl = url || `ws://${location.hostname}:8081`;
        try {
            ws = new WebSocket(wsTargetUrl);
        } catch (e) {
            showBanner('连接地址无效');
            setTimeout(showMenu, 1500);
            return;
        }

        ws.onopen = () => {
            state.connected = true;
            showBanner('已连接服务器');
            const name = localStorage.getItem('playerName') || 'Player';
            const pid = getStablePlayerId();
            if (state.pendingWorld) {
                // 单人模式：先加入所选世界（服务器热切换 + 身份档案恢复）
                ws.send(JSON.stringify({ type: 'joinWorld', world: state.pendingWorld, name, playerId: pid }));
            } else {
                // 发送加入信息（固定身份：同一浏览器重连可恢复存档）
                ws.send(JSON.stringify({ type: 'join', name, playerId: pid }));
            }
        };

        ws.onclose = () => {
            state.connected = false;
            if (state.mode !== 'game') return;
            showBanner('连接断开，2 秒后重连...');
            setTimeout(() => connect(wsTargetUrl), 2000);
        };

        ws.onerror = () => { /* onclose 处理重连 */ };

        ws.onmessage = (event) => {
            try {
                handleMessage(JSON.parse(event.data));
            } catch (e) {
                console.error('状态解析失败:', e);
            }
        };
    }

    function send(msg) {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify(msg));
        }
    }

    /** 稳定玩家身份：首次生成后存入 localStorage，同一浏览器重连共用（服务器按此存档） */
    function getStablePlayerId() {
        let id = localStorage.getItem('playerId');
        if (!id) {
            id = 'p_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 8);
            localStorage.setItem('playerId', id);
        }
        return id;
    }

    // ==================== 消息处理 ====================

    function handleMessage(msg) {
        switch (msg.type) {
            case 'welcome':
                state.myPlayerId = msg.playerId;
                // 记录当前世界信息（服务器权威）
                state.worldInfo = msg.world ? { name: msg.world, seed: msg.seed, seedHash: msg.seedHash } : null;
                state.pendingWorld = null;
                // 身份可能迁移（join 后服务器换档案 ID），清空旧玩家列表按服务器重建
                state.remotePlayers.clear();
                // 从服务器恢复本机背包（存档）
                if (msg.slots) {
                    for (let i = 0; i < INVENTORY_TOTAL && i < msg.slots.length; i++) {
                        const s = msg.slots[i];
                        inventory[i] = s ? parseSlot(s) : null;
                    }
                    renderHotbar();
                    syncInventory();
                }
                // 从服务器恢复本机位置（若有存档）
                if (msg.players) {
                    for (const p of msg.players) {
                        if (p.id === state.myPlayerId) {
                            player.x = p.x; player.y = p.y;
                            player.prevX = p.x; player.prevY = p.y;
                            player.renderX = p.x; player.renderY = p.y;
                            state.camera = { x: p.x - input.viewport.w / 2, y: p.y - input.viewport.h / 2 };
                            if (p.slot !== undefined) player.slot = p.slot;
                        }
                        state.remotePlayers.set(p.id, p);
                    }
                }
                startSimulation();
                setTimeout(() => bannerEl.classList.remove('visible'), 1500);
                break;

            case 'state':
                if (msg.players) {
                    for (const p of msg.players) {
                        state.remotePlayers.set(p.id, p);
                    }
                }
                if (msg.chunks && msg.chunks.length) {
                    for (const chunk of msg.chunks) {
                        const data = base64ToBytes(chunk.data);
                        state.chunks.set(`${chunk.cx},${chunk.cy}`, data);
                    }
                }
                if (msg.tiles && msg.tiles.length) {
                    for (const t of msg.tiles) {
                        applyRemoteTile(t.x, t.y, t.type);
                    }
                }
                if (msg.drops) applyServerDrops(msg.drops);
                renderHotbar();
                break;

            case 'playerJoined':
                if (msg.player) state.remotePlayers.set(msg.player.id, msg.player);
                break;

            case 'playerLeft':
                state.remotePlayers.delete(msg.playerId);
                break;

            case 'worldSwitch':
                // 服务器切换了全局世界：清空本地世界状态重建（区块由服务器重新下发）
                if (msg.name) state.worldInfo = { name: msg.name, seed: msg.seed, seedHash: msg.seedHash };
                state.chunks.clear();
                state.remotePlayers.clear();
                state.serverDrops = [];
                if (!state.pendingWorld && ws && ws.readyState === WebSocket.OPEN) {
                    // 非主动切换方：重新加入以恢复该世界的个人存档
                    ws.send(JSON.stringify({
                        type: 'join',
                        name: localStorage.getItem('playerName') || 'Player',
                        playerId: getStablePlayerId(),
                    }));
                }
                break;
        }
    }

    /** 应用服务器下发的方块变更到本地缓存 */
    function applyRemoteTile(tx, ty, type) {
        const cx = Math.floor(tx / CHUNK_SIZE);
        const cy = Math.floor(ty / CHUNK_SIZE);
        const key = `${cx},${cy}`;
        const data = state.chunks.get(key);
        if (!data) return;
        const lx = ((tx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        const ly = ((ty % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        data[ly * CHUNK_SIZE + lx] = type;
    }

    /**
     * 应用服务器广播的掉落物列表：
     * - 按唯一 id 保留已有掉落物的本地磁吸位置（避免广播重置位置）；
     * - 拾取由服务器权威移除（pickup 上报），广播中消失即视为已清理。
     */
    function applyServerDrops(list) {
        const next = [];
        for (const d of list) {
            if (d.id === undefined) continue;
            const existing = state.serverDrops.find(o => o.id === d.id);
            next.push(existing || { id: d.id, x: d.x, y: d.y, name: d.name, life: 0, dead: false });
        }
        state.serverDrops = next;
    }

    function getTile(tx, ty) {
        if (ty < 0 || ty >= WORLD_HEIGHT_TILES) return T_AIR;
        const cx = Math.floor(tx / CHUNK_SIZE);
        const cy = Math.floor(ty / CHUNK_SIZE);
        const data = state.chunks.get(`${cx},${cy}`);
        if (!data) return T_AIR;
        const lx = ((tx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        const ly = ((ty % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        return data[ly * CHUNK_SIZE + lx];
    }

    function setLocalTile(tx, ty, type) {
        const cx = Math.floor(tx / CHUNK_SIZE);
        const cy = Math.floor(ty / CHUNK_SIZE);
        const key = `${cx},${cy}`;
        let data = state.chunks.get(key);
        if (!data) {
            data = new Uint8Array(CHUNK_SIZE * CHUNK_SIZE);
            state.chunks.set(key, data);
        }
        const lx = ((tx % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        const ly = ((ty % CHUNK_SIZE) + CHUNK_SIZE) % CHUNK_SIZE;
        data[ly * CHUNK_SIZE + lx] = type;
    }

    // ==================== 本地物理模拟（移植 core Player） ====================

    function startSimulation() {
        stopSimulation();
        simTimer = setInterval(localTick, TICK_INTERVAL);
    }

    function stopSimulation() {
        if (simTimer) { clearInterval(simTimer); simTimer = null; }
    }

    function localTick() {
        if (state.paused) return;
        player.prevX = player.x;
        player.prevY = player.y;
        updatePlayer();
        // 渲染位置跟随实际位置（此前未更新导致玩家贴图不随相机/世界移动）
        player.renderX = player.x;
        player.renderY = player.y;
        updateInteractions();
        updateLocalDrops();
        updateCamera();
        // 上报本机状态（32Hz）
        sendPlayerState();
    }

    function sendPlayerState() {
        send({
            type: 'playerState',
            x: Math.round(player.x * 100) / 100,
            y: Math.round(player.y * 100) / 100,
            dir: player.direction,
            anim: player.animFrame,
            slot: player.slot,
            onGround: player.onGround,
        });
    }

    /** 玩家物理更新（移植 core Player.update） */
    function updatePlayer() {
        // 方向与水平输入
        let dx = 0;
        player.direction = 'null';
        if (input.keys.a) { player.direction = 'left'; dx -= 16; }
        if (input.keys.d) { player.direction = 'right'; dx += 16; }

        // 冲刺
        if (input.keys.alt && !player.dashKeyHeld && player.dashCharges > 0) {
            tryDash();
        }
        player.dashKeyHeld = input.keys.alt;

        // 跳跃（一跳 + 二段跳）
        const jumpPressed = input.keys.space;
        if (jumpPressed && !player.jumpKeyHeld && player.jumpCooldown === 0 && player.jumpCount < 2) {
            player.autoJump.active = false;
            player.jumpCount++;
            if (player.jumpCount === 1) {
                player.vy = -14;
                player.jumpPhase = 'first';
            } else {
                player.vy = -11;
                player.jumpPhase = 'double';
            }
            player.jumpCooldown = 8;
            player.onGround = false;
        }
        player.jumpKeyHeld = jumpPressed;

        if (!input.keys.a && !input.keys.d && !jumpPressed) {
            player.autoJump.active = false;
        }
        if (player.jumpCooldown > 0) player.jumpCooldown--;

        // 冲刺充能回复
        if (player.dashCharges < player.dashMax && player.dashRecharge++ >= 100) {
            player.dashCharges++;
            player.dashRecharge = 0;
        }

        // 重力
        player.vy += 1;
        if (player.vy > 16) player.vy = 16;

        const inset = 3;
        const colW = TILE_SIZE - 2 * inset;
        const colH = TILE_SIZE - 2 * inset;

        // --- X 轴移动与碰撞 ---
        dx += player.dashVX;
        player.dashVX = 0;

        // 自动跨步（重写：前方一格高方块直接走上，不触发跳跃动作）
        if (dx !== 0) autoStep(dx);
        else player.crossGap = null;                // 停下时取消跨沟状态

        if (dx !== 0) {
            let newX = player.x + dx;
            let box = { x: newX + inset, y: player.y + inset, w: colW, h: colH };
            const tStartX = Math.floor(box.x / TILE_SIZE);
            const tEndX = Math.floor((box.x + box.w) / TILE_SIZE - 1e-6);
            const tStartY = Math.max(0, Math.floor(box.y / TILE_SIZE));
            const tEndY = Math.min(WORLD_HEIGHT_TILES - 1, Math.floor((box.y + box.h) / TILE_SIZE - 1e-6));

            let hit = false;
            for (let ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (let tx = tStartX; tx <= tEndX && !hit; tx++) {
                    if (!isSolid(getTile(tx, ty))) continue;
                    if (dx > 0) newX = tx * TILE_SIZE - inset - colW;
                    else newX = (tx + 1) * TILE_SIZE - inset;
                    newX = Math.round(newX);
                    hit = true;
                }
            }
            player.x = newX;
        }

        // --- Y 轴移动与碰撞 ---
        {
            // 跨沟中：玩家水平范围仍覆盖沟时保持高度滑过（不落体、不跳跃）
            let suppressY = false;
            if (player.crossGap) {
                const g = player.crossGap;
                const pLeft = player.x + inset;
                const pRight = player.x + inset + colW;
                if (pRight > g.startEdge && pLeft < g.endEdge) {
                    suppressY = true;
                } else {
                    player.crossGap = null;         // 已完全跨过沟
                }
            }

            if (suppressY) {
                player.vy = 0;
                player.onGround = true;
            } else {
            let newY = player.y + player.vy;
            let box = { x: player.x + inset, y: newY + inset, w: colW, h: colH };
            const tStartX = Math.floor(box.x / TILE_SIZE);
            const tEndX = Math.floor((box.x + box.w) / TILE_SIZE - 1e-6);
            const tStartY = Math.max(0, Math.floor(box.y / TILE_SIZE));
            const tEndY = Math.min(WORLD_HEIGHT_TILES - 1, Math.floor((box.y + box.h) / TILE_SIZE - 1e-6));

            let hit = false;
            for (let ty = tStartY; ty <= tEndY && !hit; ty++) {
                for (let tx = tStartX; tx <= tEndX && !hit; tx++) {
                    if (!isSolid(getTile(tx, ty))) continue;
                    if (player.vy > 0.001) {
                        if (player.autoJump.active && player.onGround) player.autoJump.active = false;
                        newY = ty * TILE_SIZE - inset - colH;
                    } else {
                        newY = (ty + 1) * TILE_SIZE - inset;
                    }
                    newY = Math.round(newY);
                    hit = true;
                }
            }
            player.y = newY;
            if (hit) player.vy = 0;
            }
        }

        // --- 地面检测 ---
        const wasOnGround = player.onGround;
        player.onGround = false;
        {
            const footY = player.y + inset + colH;
            const tileX = Math.floor((player.x + inset + colW / 2) / TILE_SIZE);
            const tileY = Math.floor(footY / TILE_SIZE);
            if (tileY >= 0 && tileY < WORLD_HEIGHT_TILES && isSolid(getTile(tileX, tileY))) {
                const blockTop = tileY * TILE_SIZE;
                if (Math.abs(footY - blockTop) < 1e-6 || footY < blockTop) {
                    player.onGround = true;
                }
            }
        }
        // 跨沟中：脚在沟上方（空气），保持"贴地"视觉与跨步判定
        if (player.crossGap) {
            const g = player.crossGap;
            const pLeft = player.x + inset;
            const pRight = player.x + inset + colW;
            if (pRight > g.startEdge && pLeft < g.endEdge) player.onGround = true;
        }
        if (player.onGround && !wasOnGround) {
            player.jumpCount = 0;
            player.jumpPhase = 'none';
        }

        // --- 世界边界 ---
        player.y = Math.min(Math.max(player.y, 0), WORLD_HEIGHT_TILES * TILE_SIZE - TILE_SIZE);

        // --- 站立动画 ---
        player.incrementer++;
        const a = 64, b = 2;
        if (player.incrementer > a - 3 * b && player.animFrame === 1) player.animFrame = 2;
        if (player.incrementer > a - 2 * b && player.animFrame === 2) player.animFrame = 3;
        if (player.incrementer > a - b && player.animFrame === 3) player.animFrame = 4;
        if (player.incrementer > a) {
            if (player.animFrame === 4) player.animFrame = 1;
            player.incrementer = 0;
        }
        if (player.autoJump.recovery > 0) player.autoJump.recovery--;
    }

    function tryDash() {
        if (player.dashCharges > 0) {
            player.dashCharges--;
            if (player.direction === 'left') player.dashVX = -64;
            else if (player.direction === 'right') player.dashVX = 64;
            else player.dashVX = 0;
        }
    }

    /**
     * 自动跨步：前方一格高的方块直接"走"上去，不触发跳跃动作。
     * 检测到墙顶（脚上方 1 格实心）+ 墙身（脚所在行实心）的一格高障碍时，
     * 直接把玩家脚抬到方块顶部（无滞空、无跳跃状态），水平移动不中断。
     * 超过一格高、头顶没空间、悬空时都不跨，保持正常阻挡。
     */
    function autoStep(dx) {
        if (!autoStepEnabled) return;
        if (input.keys.s === true) return;          // 按住后移键不跨
        if (!player.onGround) return;               // 仅贴地时触发

        const dir = dx > 0 ? 1 : -1;
        const inset = 3;
        const colW = TILE_SIZE - 2 * inset;
        const colH = TILE_SIZE - 2 * inset;

        const footY = player.y + inset + colH;
        const groundTileY = Math.floor(footY / TILE_SIZE);
        const wallTopY = groundTileY - 1;          // 墙顶行（脚上方 1 格）
        if (wallTopY < 0) return;

        const centerTileX = Math.floor((player.x + colW / 2) / TILE_SIZE);
        const frontEdge = dir > 0 ? player.x + TILE_SIZE - inset : player.x + inset;

        for (let offset = 1; offset <= 2; offset++) {
            const checkX = centerTileX + dir * offset;
            // 前方一格高障碍：墙顶实心 + 墙身(脚所在行)实心
            if (!isSolid(getTile(checkX, wallTopY))) continue;
            if (!isSolid(getTile(checkX, groundTileY))) continue;

            // 头顶空间：跨上后前方 2 格内头部不能被挡
            let headClear = true;
            for (let k = 0; k <= 2; k++) {
                if (isSolid(getTile(checkX + dir * k, wallTopY - 1))) { headClear = false; break; }
            }
            if (!headClear) return;

            // 水平触发距离：玩家前缘接近墙前缘时抬脚（允许轻微嵌入）
            const wallEdge = dir > 0 ? checkX * TILE_SIZE : (checkX + 1) * TILE_SIZE;
            const dist = dir > 0 ? wallEdge - frontEdge : frontEdge - wallEdge;
            if (dist <= TILE_SIZE + 2 && dist >= -4) {
                // 直接把脚抬到墙顶（无跳跃动作）
                player.y = wallTopY * TILE_SIZE - TILE_SIZE;
                player.prevY = player.y;
                player.vy = 0;
                player.onGround = true;             // 已站上墙顶
                return;
            }
        }

        // --- 一格宽的沟：不跳、直接滑过（无需初速度） ---
        checkGap(dir, groundTileY, centerTileX, frontEdge);
    }

    /**
     * 跨沟检测：前方 1 格是一格深的小沟（脚下行空气、沟底行实心）、
     * 对岸与当前地面同高时，标记跨沟状态；Y 轴阶段会保持高度滑过沟，
     * 不触发跳跃、不需要助跑。沟宽超过 1 格或沟太深则不跨。
     */
    function checkGap(dir, groundTileY, centerTileX, frontEdge) {
        const inset = 3;
        const colW = TILE_SIZE - 2 * inset;

        const gapX = centerTileX + dir;
        // 前方不是沟（脚下行仍实心）则不处理
        if (isSolid(getTile(gapX, groundTileY))) return;
        // 沟太深（沟底不实心）不跨，保持会掉下去的自然行为
        if (!isSolid(getTile(gapX, groundTileY + 1))) return;
        // 对岸必须是与当前地面同高的实地
        const farX = centerTileX + 2 * dir;
        if (!isSolid(getTile(farX, groundTileY))) return;
        // 头顶净空：滑过沟的上方 2 格内不能有方块
        for (let k = 0; k <= 2; k++) {
            if (isSolid(getTile(gapX + dir * k, groundTileY - 1))) return;
        }

        // 水平触发距离：玩家前缘接近沟前缘时开始滑过
        const gapEdge = dir > 0 ? gapX * TILE_SIZE : (gapX + 1) * TILE_SIZE;
        const dist = dir > 0 ? gapEdge - frontEdge : frontEdge - gapEdge;
        if (dist <= TILE_SIZE + 2 && dist >= -4) {
            player.crossGap = {
                dir,
                startEdge: gapX * TILE_SIZE,
                endEdge: (gapX + 1) * TILE_SIZE,
            };
        }
    }

    // ==================== 方块交互（本地 DDA） ====================

    function updateInteractions() {
        if (breakCooldown > 0) breakCooldown--;
        if (placeCooldown > 0) placeCooldown--;
        updateSelection();
        handleBreaking();
        handlePlacing();
    }

    function mouseWorldPos() {
        const camX = state.camera.x;
        const camY = state.camera.y;
        return {
            x: input.mouse.x + camX,
            y: input.mouse.y + camY,
        };
    }

    /**
     * 选择框重写：
     * 1. 选择框跟随鼠标 —— 直接选中鼠标所指的方块格（不再被射线沿途方块抢占）；
     * 2. 自动选择 —— 鼠标指向空气时，自动吸附到附近最近且可达的实心方块
     *    （设置面板可开关；右键放置时临时关闭，保证放置位置精确跟手）。
     * 超出交互距离时仍跟随鼠标，但以淡红色提示不可交互。
     */
    function updateSelection() {
        if (state.inventoryOpen || state.paused) {
            state.selected = null;
            return;
        }
        const m = mouseWorldPos();
        const mouseTx = Math.floor(m.x / TILE_SIZE);
        const mouseTy = Math.floor(m.y / TILE_SIZE);

        // 1) 选择框跟随鼠标
        let sel = { x: mouseTx, y: mouseTy, solid: getTile(mouseTx, mouseTy) !== T_AIR };

        // 2) 自动选择：仅当设置开启且未在放置（右键）时吸附附近可达方块
        if (!sel.solid && autoSelectEnabled && !input.mouse.right) {
            const auto = findAutoSelect(mouseTx, mouseTy);
            if (auto) sel = { x: auto.x, y: auto.y, solid: true };
        }

        sel.inRange = isWithinRange(sel.x, sel.y);
        state.selected = sel;
    }

    /**
     * 自动选择：从鼠标格由内向外逐层（方形环）搜索最近的实心方块，
     * 仅返回玩家交互距离内的方块，找不到返回 null。
     */
    function findAutoSelect(mx, my) {
        for (let r = 1; r <= AUTO_SELECT_RADIUS; r++) {
            for (let dy = -r; dy <= r; dy++) {
                for (let dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) !== r) continue; // 只扫当前层外圈
                    const tx = mx + dx;
                    const ty = my + dy;
                    if (getTile(tx, ty) === T_AIR) continue;
                    if (!isWithinRange(tx, ty)) continue;
                    return { x: tx, y: ty }; // 由内向外第一个即最近
                }
            }
        }
        return null;
    }

    function isWithinRange(tx, ty) {
        const ptx = Math.floor(player.x / TILE_SIZE);
        const pty = Math.floor(player.y / TILE_SIZE);
        return Math.abs(tx - ptx) <= MAX_INTERACT_DISTANCE && Math.abs(ty - pty) <= MAX_INTERACT_DISTANCE;
    }

    function handleBreaking() {
        if (!input.mouse.left || breakCooldown > 0 || state.paused || state.inventoryOpen) return;
        const sel = state.selected;
        if (!sel) return;
        if (!isWithinRange(sel.x, sel.y)) return;
        const type = getTile(sel.x, sel.y);
        if (type === T_AIR) return;

        // 本地即时破坏（掉落物由服务器广播产生，前端只做磁吸/吸入，避免重复副本）
        setLocalTile(sel.x, sel.y, T_AIR);
        breakCooldown = COOLDOWN_BREAK;
        // 上报服务器
        send({ type: 'blockAction', x: sel.x, y: sel.y, action: 'break' });
    }

    function handlePlacing() {
        if (!input.mouse.right || placeCooldown > 0 || state.paused || state.inventoryOpen) return;
        const sel = state.selected;
        if (!sel) return;
        if (!isWithinRange(sel.x, sel.y)) return;
        const item = inventory[player.slot];
        if (!item || item.count <= 0) return;
        const blockType = ITEM_TILE[item.name];
        if (!blockType) return;
        if (getTile(sel.x, sel.y) !== T_AIR) return;
        // 不能放在玩家脚下方块上（玩家重叠保护）
        const ptx = Math.floor(player.x / TILE_SIZE);
        const pty = Math.floor(player.y / TILE_SIZE);
        if (sel.x === ptx && sel.y === pty) return;

        // 本地即时放置 + 消耗
        setLocalTile(sel.x, sel.y, blockType);
        item.count--;
        if (item.count <= 0) inventory[player.slot] = null;
        placeCooldown = COOLDOWN_PLACE;
        renderHotbar();
        syncInventory();
        // 上报服务器
        send({ type: 'blockAction', x: sel.x, y: sel.y, action: 'place', item: item.name });
    }

    // ==================== 掉落物（服务器广播，前端磁吸/吸入） ====================

    function updateLocalDrops() {
        for (const d of state.serverDrops) {
            d.life++;
            const dx = player.x - d.x;
            const dy = player.y - d.y;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 150) {
                if (dist < 10) {
                    d.dead = true;
                    // 上报服务器权威移除（其他客户端同步消失）
                    send({ type: 'pickup', id: d.id });
                    addItemToInventory(d.name, 1);
                    continue;
                }
                const speed = 4 * (1 + (150 - dist) / 150 * 2);
                d.x += (dx / dist) * speed;
                d.y += (dy / dist) * speed;
            }
        }
        state.serverDrops = state.serverDrops.filter(d => !d.dead);
    }

    // ==================== 背包系统（45 槽，前端权威） ====================

    function addItemToInventory(name, count) {
        // 先堆叠已有同种物品
        for (let i = 0; i < INVENTORY_TOTAL && count > 0; i++) {
            const s = inventory[i];
            if (s && s.name === name && s.count < MAX_STACK) {
                const add = Math.min(count, MAX_STACK - s.count);
                s.count += add;
                count -= add;
            }
        }
        // 再找空槽
        for (let i = 0; i < INVENTORY_TOTAL && count > 0; i++) {
            if (!inventory[i]) {
                const add = Math.min(count, MAX_STACK);
                inventory[i] = { name, count: add };
                count -= add;
            }
        }
        renderHotbar();
        syncInventory();
    }

    function syncInventory() {
        const slots = inventory.map(s => s ? `${s.name}|${s.count}` : '');
        send({ type: 'inventory', slots });
    }

    /** 序列化背包（存本地缓存） */
    function serializeInventory() {
        return inventory.map(s => s ? `${s.name}|${s.count}` : '');
    }

    /** 解析单槽位 "name|count" -> {name, count}（无效返回 null） */
    function parseSlot(s) {
        const idx = s.indexOf('|');
        if (idx < 0) return null;
        const name = s.substring(0, idx);
        const count = parseInt(s.substring(idx + 1), 10);
        if (!name || isNaN(count) || count <= 0) return null;
        return { name, count };
    }

    // ==================== 渲染 ====================

    function resize() {
        const dpr = window.devicePixelRatio || 1;
        canvas.width = window.innerWidth * dpr;
        canvas.height = window.innerHeight * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        input.viewport = { w: window.innerWidth, h: window.innerHeight };
    }

    function updateCamera() {
        let targetCamX = player.x - input.viewport.w / 2;
        let targetCamY = player.y - input.viewport.h / 2;
        const maxCamY = WORLD_HEIGHT_PX - input.viewport.h;
        targetCamY = Math.min(Math.max(targetCamY, 0), Math.max(maxCamY, 0));
        const camOffset = state.inventoryOpen ? input.viewport.h * 0.15 : 0;
        state.camera.x += (targetCamX - state.camera.x) * 0.1;
        state.camera.y += (targetCamY - state.camera.y + camOffset) * 0.1;
    }

    function render() {
        const vw = input.viewport.w;
        const vh = input.viewport.h;
        const camX = state.camera.x;
        const camY = state.camera.y;

        // 关闭纹理平滑，保持像素风并消除边缘半透明缝隙
        ctx.imageSmoothingEnabled = false;

        // 背景
        ctx.fillStyle = '#7ec0ee';
        ctx.fillRect(0, 0, vw, vh);

        // 方块（坐标取整到像素网格，避免相机浮点偏移造成方块间缝隙）
        const startTx = Math.floor(camX / TILE_SIZE);
        const startTy = Math.max(0, Math.floor(camY / TILE_SIZE));
        const endTx = Math.ceil((camX + vw) / TILE_SIZE);
        const endTy = Math.min(WORLD_HEIGHT_TILES - 1, Math.ceil((camY + vh) / TILE_SIZE));
        for (let ty = startTy; ty <= endTy; ty++) {
            for (let tx = startTx; tx <= endTx; tx++) {
                const type = getTile(tx, ty);
                if (type === T_AIR) continue;
                const meta = TILE_META[type];
                if (!meta) continue;
                const sx = Math.floor(tx * TILE_SIZE - camX);
                const sy = Math.floor(ty * TILE_SIZE - camY);
                // 程序化贴图（水/岩浆的半透明已在纹理内处理）
                const tex = paintBlockTexture(type);
                ctx.drawImage(tex, sx, sy, TILE_SIZE, TILE_SIZE);
            }
        }

        // 掉落物（服务器广播，前端磁吸）
        for (const d of state.serverDrops) {
            drawDrop(d.x, d.y, d.name, camX, camY);
        }

        // 其他玩家（服务器广播）
        for (const [id, p] of state.remotePlayers) {
            if (id === state.myPlayerId) continue;
            drawRemotePlayer(p, camX, camY);
        }

        // 本机玩家（贴图）
        drawLocalPlayer(camX, camY);

        // 选中高亮（选择框跟鼠标：可达方块亮白 / 空气淡白 / 超距淡红）
        if (state.selected) {
            const s = state.selected;
            const sx = s.x * TILE_SIZE - camX;
            const sy = s.y * TILE_SIZE - camY;
            let color = 'rgba(255,255,255,0.9)';
            if (!s.inRange) color = 'rgba(255,120,120,0.6)';
            else if (!s.solid) color = 'rgba(255,255,255,0.3)';
            ctx.strokeStyle = color;
            ctx.lineWidth = 2;
            ctx.strokeRect(sx + 1, sy + 1, TILE_SIZE - 2, TILE_SIZE - 2);
        }

        // 切换快捷栏的方块名提示（5 秒后淡出）
        drawSlotNameToast(vw, vh);

        renderDebug();
    }

    function drawDrop(x, y, name, camX, camY) {
        const sx = x - camX - 8;
        const sy = y - camY - 8;
        const tileId = ITEM_TILE[name];
        const tex = tileId ? paintBlockTexture(tileId) : null;
        if (tex) ctx.drawImage(tex, sx, sy, 16, 16);
        else { ctx.fillStyle = fallbackColor(name); ctx.fillRect(sx, sy, 16, 16); }
        ctx.strokeStyle = 'rgba(0,0,0,0.3)';
        ctx.lineWidth = 1;
        ctx.strokeRect(sx, sy, 16, 16);
    }

    /** 玩家贴图：使用原版图片（web/player/*.png，32x32），按朝向与动画帧映射，映射规则与原版 Player.java 一致 */
    const playerCache = new Map();
    function paintPlayer(direction, animFrame) {
        const key = (direction || 'null') + '_' + (animFrame || 1);
        if (playerCache.has(key)) return playerCache.get(key);
        // 原版方向映射：up→player-up-1  down→player-down-1  right→player-r-1  left→player-l-1
        // 站立动画：帧1→player-1  帧2→player-2  帧3→player-3  帧4→player-2
        let src;
        if (direction === 'up') src = 'player/player-up-1.png';
        else if (direction === 'down') src = 'player/player-down-1.png';
        else if (direction === 'right') src = 'player/player-r-1.png';
        else if (direction === 'left') src = 'player/player-l-1.png';
        else {
            const f = (animFrame === 2 || animFrame === 4) ? 2 : (animFrame === 3 ? 3 : 1);
            src = 'player/player-' + f + '.png';
        }
        // 用 Image 直接缓存：未加载完成时 drawImage 为空操作，加载完成后下一帧自动显示
        const img = new Image();
        img.src = src;
        playerCache.set(key, img);
        return img;
    }

    function drawLocalPlayer(camX, camY) {
        const sx = player.renderX - camX;
        const sy = player.renderY - camY;
        const tex = paintPlayer(player.direction, player.animFrame);
        if (tex) ctx.drawImage(tex, sx, sy, TILE_SIZE, TILE_SIZE);
        else {
            ctx.fillStyle = '#ff6b6b';
            ctx.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);
        }
        // 玩家名
        ctx.fillStyle = '#fff';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(localStorage.getItem('playerName') || 'Player', sx + TILE_SIZE / 2, sy - 4);
    }

    function drawRemotePlayer(p, camX, camY) {
        const sx = p.x - camX;
        const sy = p.y - camY;
        const tex = paintPlayer(p.dir, p.anim || 1);
        if (tex) ctx.drawImage(tex, sx, sy, TILE_SIZE, TILE_SIZE);
        else {
            ctx.fillStyle = '#6b9fff';
            ctx.fillRect(sx, sy, TILE_SIZE, TILE_SIZE);
        }
        ctx.fillStyle = '#fff';
        ctx.font = '10px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText(p.name || 'Player', sx + TILE_SIZE / 2, sy - 4);
    }

    // ==================== 中文方块名（快捷栏切换提示 / 背包详情用） ====================

    /** 常见方块/物品的直接中译（按物品 key） */
    const CN_DIRECT = {
        stone: '石头', granite: '花岗岩', diorite: '闪长岩', andesite: '安山岩',
        grass_block: '草方块', dirt: '泥土', coarse_dirt: '砂土', podzol: '灰化土', mycelium: '菌丝土',
        cobblestone: '圆石', mossy_cobblestone: '苔石', bedrock: '基岩',
        sand: '沙子', red_sand: '红沙', gravel: '沙砾', sandstone: '砂岩', clay: '黏土', mud: '泥巴',
        water: '水', lava: '岩浆', ice: '冰', packed_ice: '浮冰', blue_ice: '蓝冰',
        snow_block: '雪块', snow: '雪', powder_snow: '细雪', glass: '玻璃', obsidian: '黑曜石',
        oak_log: '橡木原木', spruce_log: '云杉原木', birch_log: '白桦原木', jungle_log: '丛林原木',
        acacia_log: '金合欢原木', cherry_log: '樱花原木', dark_oak_log: '深色橡木原木', mangrove_log: '红树原木',
        oak_planks: '橡木木板', spruce_planks: '云杉木板', birch_planks: '白桦木板', jungle_planks: '丛林木板',
        acacia_planks: '金合欢木板', cherry_planks: '樱花木板', dark_oak_planks: '深色橡木木板', mangrove_planks: '红树木板',
        oak_leaves: '橡树树叶', spruce_leaves: '云杉树叶', birch_leaves: '白桦树叶', jungle_leaves: '丛林树叶',
        acacia_leaves: '金合欢树叶', cherry_leaves: '樱花树叶', dark_oak_leaves: '深色橡树树叶', mangrove_leaves: '红树树叶',
        oak_sapling: '橡树树苗', spruce_sapling: '云杉树苗', birch_sapling: '白桦树苗', jungle_sapling: '丛林树苗',
        acacia_sapling: '金合欢树苗', cherry_sapling: '樱花树苗', dark_oak_sapling: '深色橡树树苗',
        deepslate: '深板岩', cobbled_deepslate: '深板岩圆石', tuff: '凝灰岩', calcite: '方解石',
        coal_ore: '煤矿石', iron_ore: '铁矿石', copper_ore: '铜矿石', gold_ore: '金矿石',
        redstone_ore: '红石矿石', lapis_ore: '青金石矿石', diamond_ore: '钻石矿石', emerald_ore: '绿宝石矿石',
        nether_gold_ore: '下界金矿石', deepslate_coal_ore: '深板岩煤矿石', deepslate_iron_ore: '深板岩铁矿石',
        deepslate_copper_ore: '深板岩铜矿石', deepslate_gold_ore: '深板岩金矿石', deepslate_redstone_ore: '深板岩红石矿石',
        deepslate_lapis_ore: '深板岩青金石矿石', deepslate_diamond_ore: '深板岩钻石矿石', deepslate_emerald_ore: '深板岩绿宝石矿石',
        coal_block: '煤炭块', iron_block: '铁块', gold_block: '金块', diamond_block: '钻石块',
        emerald_block: '绿宝石块', redstone_block: '红石块', lapis_block: '青金石块', copper_block: '铜块',
        netherite_block: '下界合金块', raw_iron_block: '粗铁块', raw_copper_block: '粗铜块', raw_gold_block: '粗金块',
        crafting_table: '工作台', furnace: '熔炉', chest: '箱子', torch: '火把', tnt: 'TNT',
        bookshelf: '书架', cactus: '仙人掌', sugar_cane: '甘蔗', melon: '西瓜', pumpkin: '南瓜',
        carved_pumpkin: '雕刻南瓜', jack_o_lantern: '南瓜灯', lily_pad: '睡莲', vine: '藤蔓',
        short_grass: '矮草丛', tall_grass: '高草丛', fern: '蕨类植物',
        dandelion: '蒲公英', poppy: '虞美人', blue_orchid: '兰花', allium: '绒球葱',
        azure_bluet: '蓝花美耳草', red_tulip: '红色郁金香', orange_tulip: '橙色郁金香',
        white_tulip: '白色郁金香', pink_tulip: '粉色郁金香', oxeye_daisy: '滨菊', cornflower: '矢车菊',
        brown_mushroom: '棕色蘑菇', red_mushroom: '红色蘑菇', mushroom_stem: '蘑菇柄',
        wheat: '小麦', carrots: '胡萝卜', potatoes: '马铃薯', beetroots: '甜菜',
        seagrass: '海草', kelp: '海带', sponge: '海绵', wet_sponge: '湿海绵',
        hay_block: '干草块', barrel: '木桶', anvil: '铁砧', beacon: '信标',
        enchanting_table: '附魔台', brewing_stand: '酿造台', cauldron: '炼药锅', hopper: '漏斗',
        piston: '活塞', sticky_piston: '粘性活塞', rail: '铁轨', powered_rail: '充能铁轨',
        detector_rail: '探测铁轨', activator_rail: '激活铁轨', ladder: '梯子',
        stone_bricks: '石砖', mossy_stone_bricks: '苔石砖', cracked_stone_bricks: '裂纹石砖', chiseled_stone_bricks: '錾制石砖',
        bricks: '红砖', glowstone: '荧石', sea_lantern: '海晶灯', prismarine: '海晶石',
        netherrack: '下界岩', soul_sand: '灵魂沙', basalt: '玄武岩', blackstone: '黑石',
        ancient_debris: '远古残骸', crying_obsidian: '哭泣的黑曜石', quartz_block: '石英块',
        slime_block: '黏液块', honey_block: '蜂蜜块', honeycomb_block: '蜜脾块',
        sculk: '幽匿块', amethyst_block: '紫水晶块', budding_amethyst: '紫水晶母岩',
        copper_grate: '铜格栅', light: '光源方块', barrier: '屏障',
        lily_of_the_valley: '铃兰', lightning_rod: '避雷针', end_rod: '末地烛',
        frogspawn: '蛙卵', pitcher_plant: '瓶子草',
        blast_furnace: '高炉', daylight_detector: '阳光探测器', waxed_copper_block: '涂蜡铜块',
    };

    /** 英文单词 -> 中文（用于拼装未收录的方块名） */
    const CN_WORDS = {
        white: '白色', orange: '橙色', magenta: '品红色', light: '淡', blue: '蓝色',
        yellow: '黄色', lime: '黄绿色', pink: '粉色', gray: '灰色', cyan: '青色',
        purple: '紫色', brown: '棕色', green: '绿色', red: '红色', black: '黑色',
        oak: '橡木', spruce: '云杉', birch: '白桦', jungle: '丛林', acacia: '金合欢',
        cherry: '樱花', dark: '深色', mangrove: '红树', bamboo: '竹', crimson: '绯红', warped: '诡异',
        stripped: '去皮', log: '原木', wood: '木头', planks: '木板', leaves: '树叶', sapling: '树苗',
        stairs: '楼梯', slab: '台阶', fence: '栅栏', gate: '栅栏门', door: '门', trapdoor: '活板门',
        button: '按钮', plate: '压力板', wall: '墙', sign: '告示牌', ore: '矿石', block: '块',
        deepslate: '深板岩', cobblestone: '圆石', stone: '石头', bricks: '砖', brick: '砖',
        glass: '玻璃', pane: '玻璃板', wool: '羊毛', carpet: '地毯', concrete: '混凝土',
        terracotta: '陶瓦', glazed: '上釉', stained: '染色', powder: '粉末',
        smooth: '平滑', chiseled: '錾制', polished: '磨制', mossy: '苔藓', cracked: '裂纹',
        infested: '蛀蚀', cut: '切制', waxed: '涂蜡', oxidized: '氧化', exposed: '斑驳', weathered: '风化',
        raw: '粗', copper: '铜', iron: '铁', gold: '金', diamond: '钻石', emerald: '绿宝石',
        redstone: '红石', lapis: '青金石', quartz: '石英', amethyst: '紫水晶',
        nether: '下界', soul: '灵魂', end: '末地', netherite: '下界合金',
        prismarine: '海晶石', cobbled: '圆石', dripstone: '滴水石',
        sculk: '幽匿', shulker: '潜影', mushroom: '蘑菇', flower: '花', pot: '花盆',
        lantern: '灯笼', candle: '蜡烛', cake: '蛋糕', egg: '蛋',
        snow: '雪', ice: '冰', sand: '沙', gravel: '沙砾', dirt: '泥土', grass: '草',
        sea: '海', bed: '床', banner: '旗帜', skull: '头颅', head: '头', torch: '火把',
        water: '水', clay: '黏土', wheat: '小麦',
        apple: '苹果', bread: '面包', sword: '剑', pickaxe: '镐', axe: '斧', shovel: '锹', hoe: '锄',
        helmet: '头盔', chestplate: '胸甲', leggings: '护腿', boots: '靴子', bow: '弓',
        arrow: '箭', shield: '盾牌', book: '书', seed: '种子',
        carrot: '胡萝卜', potato: '马铃薯', beetroot: '甜菜', melon: '西瓜', pumpkin: '南瓜',
        sugar: '糖', cane: '甘蔗', paper: '纸', stick: '木棍', string: '线', feather: '羽毛',
        charcoal: '木炭', flint: '燧石', steel: '打火石', ingot: '锭',
        nugget: '粒', dust: '粉', rod: '棒', crystal: '水晶', shell: '贝壳', bone: '骨',
        rotten: '腐', flesh: '肉', gunpowder: '火药', bucket: '桶', milk: '奶',
        dye: '染料', ball: '球', slime: '黏液',
        snowball: '雪球', fishing: '钓鱼', name: '命名', tag: '标签',
        // —— 材料/矿石/建筑方块补充 ——
        andesite: '安山岩', diorite: '闪长岩', granite: '花岗岩', tuff: '凝灰岩',
        sandstone: '砂岩', blackstone: '黑石', basalt: '玄武岩', azalea: '杜鹃',
        moss: '苔藓', mud: '泥', coal: '煤', lazuli: '', purpur: '紫珀',
        coral: '珊瑚', brain: '脑', bubble: '气泡', horn: '角', tube: '管', fan: '扇',
        vein: '脉络', lichen: '地衣', glow: '发光', glowstone: '荧石',
        mosaic: '马赛克', ender: '末影', nylium: '菌岩', monster: '怪物',
        packed: '压实', gilded: '镶金', reinforced: '加固', rooted: '扎根',
        frosted: '霜', suspicious: '可疑', tinted: '遮光', chipped: '缺口',
        damaged: '损坏', decorated: '装饰', trapped: '陷阱', muddy: '泥泞',
        petrified: '石化', respawn: '重生', anchor: '锚', lodestone: '磁石',
        // —— 结构/部件补充 ——
        pillar: '柱', bars: '栏杆', shard: '碎片', bud: '芽', bulb: '灯',
        grate: '格栅', cluster: '簇', box: '盒', core: '核心', tiles: '瓦',
        stem: '菌柄', roots: '根', hyphae: '菌丝', fungus: '菌', wart: '疣',
        sensor: '传感器', catalyst: '催化器', shrieker: '尖啸体', spawner: '刷怪笼',
        tripwire: '绊线', hook: '钩', note: '音符', chain: '锁链', lever: '拉杆',
        bookshelf: '书架', hanging: '悬挂', pressure: '压力', weighted: '承重',
        sticky: '粘性', powered: '充能', detector: '探测', activator: '激活',
        campfire: '营火', piston: '活塞', lamp: '灯', magma: '岩浆',
        repeater: '中继器', comparator: '比较器', beacon: '信标',
        scaffolding: '脚手架', conduit: '潮涌核心', beehive: '蜂箱', nest: '巢',
        berry: '浆果', sweet: '甜', cocoa: '可可', beans: '豆', slice: '片',
        pitcher: '瓶子草', pod: '荚', sniffer: '嗅探兽', turtle: '海龟', dried: '干',
        // —— 生物/头/植物补充 ——
        skeleton: '骷髅', zombie: '僵尸', creeper: '苦力怕', dragon: '龙',
        wither: '凋灵', piglin: '猪灵', player: '玩家', lily: '百合',
        peony: '牡丹', lilac: '丁香', sunflower: '向日葵', rose: '玫瑰', bush: '丛',
        chorus: '紫颂', flowering: '开花', petals: '花瓣', dripleaf: '滴水叶',
        spore: '孢子', blossom: '花', cave: '洞穴', vines: '藤蔓',
        pickle: '泡菜', froglight: '蛙明灯', ochre: '赭色', verdant: '翠绿',
        pearlescent: '珠光', shroomlight: '菌光体', weeping: '垂泪', twisting: '缠怨',
        // —— 功能方块/杂项补充 ——
        composter: '堆肥桶', crafter: '合成器', dispenser: '发射器', dropper: '投掷器',
        observer: '侦测器', target: '标靶', bell: '钟', jukebox: '唱片机',
        loom: '织布机', lectern: '讲台', grindstone: '砂轮', stonecutter: '切石机',
        smoker: '烟熏炉', blast: '鼓风', cartography: '制图', fletching: '制箭',
        smithing: '锻造', soil: '土', calibrated: '校准', heavy: '重型',
        trial: '试炼', vault: '宝库', large: '大', medium: '中', small: '小',
        pointed: '尖', seeds: '种子', plant: '植物',
        bee: '蜂', big: '大', table: '台', anvil: '铁砧', dead: '死', tile: '瓦',
        kelp: '海带', crystals: '水晶', furnace: '熔炉', of: '',
        fire: '火', torchflower: '火把花', chest: '箱子',
    };

    /** 物品名 -> 中文显示名（直接映射优先，其次按英文显示名逐词翻译） */
    function zhBlockName(name) {
        if (CN_DIRECT[name]) return CN_DIRECT[name];
        const tileId = ITEM_TILE[name];
        const meta = tileId ? TILE_META[tileId] : null;
        const src = (meta && meta.displayName) || name;
        // 特殊句式 "Block of X" -> "X块"（如 Block of Iron -> 铁块、Block of Raw Iron -> 粗铁块）
        const of = /^Block of (.+)$/i.exec(src);
        if (of) {
            const zh = of[1].trim().split(/\s+/).map(w => {
                const t = CN_WORDS[w.toLowerCase()];
                return t !== undefined ? t : w;
            }).join('');
            return zh + '块';
        }
        const parts = src.split(/[\s_]+/);
        let zh = '';
        for (const p of parts) {
            const k = p.toLowerCase();
            zh += CN_WORDS[k] !== undefined ? CN_WORDS[k] : k;
        }
        return zh || name;
    }

    // ==================== 快捷栏方块名提示（切换槽位时显示，5 秒后淡出） ====================

    const slotNameToast = { text: '', until: 0 };

    /** 记录当前选中格子的方块名（数字键/滚轮切换时调用） */
    function showSlotName() {
        const item = inventory[player.slot];
        if (!item) { slotNameToast.text = ''; return; }
        slotNameToast.text = zhBlockName(item.name);
        slotNameToast.until = performance.now() + 5000;   // 5 秒
    }

    /** 在快捷栏上方绘制方块名（最后 1 秒淡出） */
    function drawSlotNameToast(vw, vh) {
        const now = performance.now();
        if (!slotNameToast.text || now >= slotNameToast.until) return;
        let alpha = 1;
        if (now >= slotNameToast.until - 1000) {
            alpha = Math.max(0, (slotNameToast.until - now) / 1000);
        }
        const y = vh - 108;                      // hotbar 上方
        ctx.font = 'bold 14px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const tw = ctx.measureText(slotNameToast.text).width;
        const w = tw + 30, h = 28, x = vw / 2 - w / 2;
        ctx.globalAlpha = alpha;
        ctx.fillStyle = 'rgba(0,0,0,0.62)';
        ctx.fillRect(x, y, w, h);
        ctx.strokeStyle = 'rgba(255,255,255,0.5)';
        ctx.lineWidth = 1;
        ctx.strokeRect(x + 0.5, y + 0.5, w - 1, h - 1);
        ctx.fillStyle = '#fff';
        ctx.fillText(slotNameToast.text, vw / 2, y + h / 2 + 1);
        ctx.globalAlpha = 1;
    }

    // ==================== 程序化方块贴图（不依赖图片资源） ====================

    /** 名称哈希（确定性） */
    function hashName(name) {
        let h = 0;
        for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0;
        return h;
    }

    /** 确定性伪随机序列（mulberry32） */
    function prng(seed) {
        let t = seed >>> 0;
        return () => {
            t += 0x6D2B79F5;
            let r = Math.imul(t ^ (t >>> 15), 1 | t);
            r ^= r + Math.imul(r ^ (r >>> 7), 61 | r);
            return ((r ^ (r >>> 14)) >>> 0) / 4294967296;
        };
    }

    /** 名称 -> 基础主色（关键词匹配；未命中用哈希 HSL 兜底） */
    function blockBaseColor(name) {
        if (!name) return '#808080';
        if (/water|seagrass|kelp/.test(name)) return '#3f76e4';
        if (/lava/.test(name)) return '#e25b2a';
        if (/sand/.test(name)) return '#e3d7a1';
        if (/grass_block/.test(name)) return '#9c6b3f';
        if (/dirt|mud/.test(name)) return '#9c6b3f';
        if (/log|wood|bark|stem/.test(name)) return '#6b4a2f';
        if (/planks/.test(name)) return '#a8814f';
        if (/leaves|foliage|roots/.test(name)) return '#4a9e3c';
        if (/stone|deepslate|cobble|andesite|granite|diorite|tuff|basalt|blackstone|calcite|sculk/.test(name)) return '#8a8a8a';
        if (/bedrock/.test(name)) return '#3a3a3a';
        if (/snow/.test(name)) return '#f2f5fa';
        if (/ice/.test(name)) return '#a9dcef';
        if (/coal/.test(name)) return '#3a3a3a';
        if (/iron/.test(name)) return '#d8d8d8';
        if (/copper/.test(name)) return '#c8733d';
        if (/gold/.test(name)) return '#f7d63a';
        if (/diamond/.test(name)) return '#4ae0e8';
        if (/emerald/.test(name)) return '#3ad662';
        if (/redstone/.test(name)) return '#e83232';
        if (/lapis/.test(name)) return '#2a4bd7';
        if (/quartz/.test(name)) return '#e8e6ea';
        if (/amethyst/.test(name)) return '#9d5fd6';
        if (/netherite/.test(name)) return '#3c3c3c';
        if (/glass/.test(name)) return '#e8f4f8';
        if (/pumpkin/.test(name)) return '#e08a2e';
        if (/melon/.test(name)) return '#7fbf4e';
        if (/cactus/.test(name)) return '#3f7f3f';
        if (/clay|terracotta/.test(name)) return '#a0876a';
        if (/moss/.test(name)) return '#6fae5a';
        if (/wool/.test(name)) return '#e8e8e8';
        if (/sponge/.test(name)) return '#e8d84a';
        if (/tnt/.test(name)) return '#d83232';
        if (/coral/.test(name)) return '#e8786a';
        if (/mushroom/.test(name)) return '#b0713f';
        if (/slime/.test(name)) return '#6fbf4a';
        if (/obsidian/.test(name)) return '#1f1f28';
        if (/sea_lantern|prismarine/.test(name)) return '#7fd8c8';
        if (/hay/.test(name)) return '#d8b84a';
        if (/bamboo/.test(name)) return '#5fa83f';
        if (/nether|soul/.test(name)) return '#6a3a3a';
        if (/concrete/.test(name)) return '#b0b0b0';
        if (/shulker/.test(name)) return '#a06a8a';
        const h = hashName(name) % 360;
        return `hsl(${h}, 40%, 55%)`;
    }

    function parseColor(c) {
        const m = /^#([0-9a-f]{6})$/i.exec(c);
        if (m) return { r: parseInt(m[1].slice(0, 2), 16), g: parseInt(m[1].slice(2, 4), 16), b: parseInt(m[1].slice(4, 6), 16) };
        return { r: 128, g: 128, b: 128 };
    }
    const clamp255 = (v) => Math.max(0, Math.min(255, v));
    function shade(col, f) {
        return `rgb(${clamp255(Math.round(col.r * f))},${clamp255(Math.round(col.g * f))},${clamp255(Math.round(col.b * f))})`;
    }

    /** 4x4 噪点填充 */
    function fillNoise(g, base, rand, size, range) {
        for (let y = 0; y < size; y += 4) {
            for (let x = 0; x < size; x += 4) {
                const d = (rand() - 0.5) * range;
                g.fillStyle = shade(base, 1 + d / 255);
                g.fillRect(x, y, 4, 4);
            }
        }
    }

    /** 方块立体感：顶部/左侧压暗，底部/右侧提亮 */
    function addBevel(g, size) {
        g.fillStyle = 'rgba(0,0,0,0.28)';
        g.fillRect(0, 0, size, 2);
        g.fillRect(0, 0, 2, size);
        g.fillStyle = 'rgba(255,255,255,0.14)';
        g.fillRect(0, size - 2, size, 2);
        g.fillRect(size - 2, 0, 2, size);
    }

    /** 已绘制的方块纹理缓存（id -> 32x32 canvas） */
    const paintedCache = new Map();

    /** 撒 n 个 1~2px 明暗噪点，模拟材质颗粒 */
    function pixelNoise(g, rand, size, n, base, lo, hi) {
        for (let i = 0; i < n; i++) {
            g.fillStyle = shade(base, lo + rand() * (hi - lo));
            g.fillRect(Math.floor(rand() * (size - 1)), Math.floor(rand() * (size - 1)),
                1 + (rand() * 2 | 0), 1 + (rand() * 2 | 0));
        }
    }

    /** 撒 n 个 3~6px 色块（风化/裂纹/杂质） */
    function stain(g, rand, size, n, color) {
        for (let i = 0; i < n; i++) {
            g.fillStyle = color;
            g.fillRect(Math.floor(rand() * (size - 5)), Math.floor(rand() * (size - 5)),
                3 + Math.floor(rand() * 4), 3 + Math.floor(rand() * 4));
        }
    }

    /** 程序化绘制方块纹理（32x32），按名称关键词选择画法 */
    function paintBlockTexture(id) {
        if (paintedCache.has(id)) return paintedCache.get(id);
        const meta = TILE_META[id];
        const name = meta ? meta.name : ('block' + id);
        const cv = document.createElement('canvas');
        cv.width = TILE_SIZE; cv.height = TILE_SIZE;
        const g = cv.getContext('2d');
        const rand = prng(hashName(name) ^ (id * 2654435761));
        const base = parseColor(blockBaseColor(name));
        const S = TILE_SIZE;

        if (/water|seagrass|kelp/.test(name)) {
            // 水：半透明 + 波浪 + 高光
            g.globalAlpha = 0.72;
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let y = 2; y < S; y += 6) {
                g.fillStyle = shade(base, 1.35);
                g.fillRect(0, y, S, 2);
                g.fillStyle = shade(base, 0.72);
                g.fillRect(2, y + 3, S - 4, 1);
            }
            g.globalAlpha = 0.85;
            for (let i = 0; i < 3; i++) {
                g.fillStyle = 'rgba(255,255,255,0.5)';
                g.fillRect(Math.floor(rand() * 26), Math.floor(rand() * 26), 5 + Math.floor(rand() * 6), 1);
            }
            g.globalAlpha = 1;
        } else if (/lava/.test(name)) {
            // 岩浆：流动纹 + 亮斑
            g.fillStyle = shade(base, 0.85);
            g.fillRect(0, 0, S, S);
            for (let y = 4; y < S; y += 8) {
                g.fillStyle = shade(base, 1.25);
                g.fillRect(0, y, S, 2);
                g.fillStyle = shade(base, 0.6);
                g.fillRect(0, y + 3, S, 1);
            }
            for (let i = 0; i < 8; i++) {
                g.fillStyle = shade(base, 1.4 + rand() * 0.3);
                g.fillRect(Math.floor(rand() * 27), Math.floor(rand() * 27), 3 + Math.floor(rand() * 4), 3 + Math.floor(rand() * 3));
            }
            g.fillStyle = '#ffd34a';
            for (let i = 0; i < 5; i++) g.fillRect(Math.floor(rand() * 29), Math.floor(rand() * 29), 2, 2);
        } else if (/grass_block/.test(name)) {
            // 草方块：泥土底 + 顶部草皮锯齿
            const dirt = parseColor('#9c6b3f');
            fillNoise(g, dirt, rand, S, 26);
            pixelNoise(g, rand, S, 26, dirt, 0.7, 1.35);
            stain(g, rand, S, 5, 'rgba(70,45,25,0.55)');
            const gs = parseColor('#6fae3c');
            g.fillStyle = shade(gs, 0.8);
            g.fillRect(0, 0, S, 8);
            for (let x = 0; x < S; x += 2) {
                const hgt = 3 + Math.floor(rand() * 3);
                g.fillStyle = shade(gs, 0.9 + rand() * 0.4);
                g.fillRect(x, 8 - hgt + 2, 2, hgt);
            }
            g.fillStyle = shade(gs, 0.7);
            g.fillRect(0, 8, S, 1);
        } else if (/dirt|mud|podzol|path/.test(name)) {
            // 泥土：颗粒 + 石子
            fillNoise(g, base, rand, S, 24);
            pixelNoise(g, rand, S, 22, base, 0.65, 1.4);
            stain(g, rand, S, 5, 'rgba(60,38,20,0.5)');
            for (let i = 0; i < 4; i++) {
                g.fillStyle = shade(base, 1.5);
                g.fillRect(Math.floor(rand() * 27), Math.floor(rand() * 27), 3, 3);
                g.fillStyle = 'rgba(0,0,0,0.25)';
                g.fillRect(Math.floor(rand() * 27), Math.floor(rand() * 27), 2, 2);
            }
        } else if (/_log$|^log_|_wood$|_bark$|_stem$/.test(name) && !/stripped/.test(name)) {
            // 原木：竖条纹 + 裂纹 + 节疤
            const d0 = shade(base, 0.62), d1 = shade(base, 0.9);
            g.fillStyle = d1; g.fillRect(0, 0, S, S);
            for (let x = 0; x < S; ) {
                const w = 3 + Math.floor(rand() * 4);
                g.fillStyle = shade(base, 0.7 + rand() * 0.6);
                g.fillRect(x, 0, w, S);
                g.fillStyle = shade(base, 1.1 + rand() * 0.3);
                g.fillRect(x, 0, 1, S);
                x += w;
            }
            for (let i = 0; i < 4; i++) {
                g.fillStyle = d0;
                g.fillRect(Math.floor(rand() * 29), Math.floor(rand() * 26), 1 + Math.floor(rand() * 2), 5 + Math.floor(rand() * 5));
            }
            for (let i = 0; i < 2; i++) {
                const nx = 5 + Math.floor(rand() * 22), ny = 4 + Math.floor(rand() * 24);
                g.fillStyle = d0; g.fillRect(nx, ny, 4, 4);
                g.fillStyle = shade(base, 0.45); g.fillRect(nx + 1, ny + 1, 2, 2);
            }
        } else if (/stripped/.test(name)) {
            // 去皮原木
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let x = 0; x < S; x += 8) {
                g.fillStyle = shade(base, 0.82);
                g.fillRect(x + 1, 0, 2, S);
                g.fillStyle = shade(base, 1.12);
                g.fillRect(x + 5, 0, 2, S);
            }
            pixelNoise(g, rand, S, 18, base, 0.8, 1.25);
        } else if (/planks|_top$/.test(name)) {
            // 木板：横板条 + 木纹 + 交错板端
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let y = 0; y < S; y += 8) {
                g.fillStyle = shade(base, 0.78);
                g.fillRect(0, y + 7, S, 1);
                g.fillStyle = shade(base, 1.22);
                g.fillRect(0, y, S, 2);
                for (let i = 0; i < 2; i++) {
                    g.fillStyle = shade(base, 0.86 + rand() * 0.14);
                    g.fillRect(Math.floor(rand() * 4), y + 2 + Math.floor(rand() * 4), 8 + Math.floor(rand() * 16), 1);
                }
            }
            for (let y = 0; y < S; y += 8) {
                const off = ((y / 8) % 2 === 0) ? 9 : 18;
                g.fillStyle = shade(base, 0.6);
                g.fillRect(off, y, 1, 7);
            }
        } else if (/leaves|foliage|roots/.test(name)) {
            // 树叶：大叶簇拼贴
            g.fillStyle = shade(base, 0.5);
            g.fillRect(0, 0, S, S);
            for (let i = 0; i < 26; i++) {
                const sz = 4 + Math.floor(rand() * 5);
                g.fillStyle = shade(base, 0.6 + rand() * 0.9);
                g.fillRect(Math.floor(rand() * (S - sz)), Math.floor(rand() * (S - sz)), sz, sz);
            }
            pixelNoise(g, rand, S, 40, base, 0.5, 1.6);
        } else if (/_ore$/.test(name)) {
            // 矿石：石底 + 矿物簇 + 高光
            const st = parseColor('#8a8a8a');
            fillNoise(g, st, rand, S, 30);
            pixelNoise(g, rand, S, 20, st, 0.75, 1.35);
            for (let i = 0; i < 5; i++) {
                const cx = 2 + Math.floor(rand() * 22), cy = 2 + Math.floor(rand() * 22);
                const sz = 4 + Math.floor(rand() * 3);
                g.fillStyle = shade(base, 0.85);
                g.fillRect(cx, cy, sz, sz);
                g.fillStyle = shade(base, 1.1);
                g.fillRect(cx, cy, sz, 2);
                g.fillStyle = shade(base, 1.35);
                g.fillRect(cx, cy, 2, sz);
                g.fillStyle = 'rgba(255,255,255,0.5)';
                g.fillRect(cx + 1, cy + 1, 1, 1);
            }
            pixelNoise(g, rand, S, 10, base, 0.6, 1.5);
        } else if (/bedrock/.test(name)) {
            // 基岩：深色大块纹理
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let i = 0; i < 16; i++) {
                g.fillStyle = shade(base, 0.55 + rand() * 0.95);
                g.fillRect(Math.floor(rand() * 24), Math.floor(rand() * 24), 4 + Math.floor(rand() * 8), 4 + Math.floor(rand() * 8));
            }
            stain(g, rand, S, 6, 'rgba(0,0,0,0.4)');
        } else if (/stone|cobble|andesite|granite|diorite|tuff|basalt|blackstone|calcite|deepslate|sculk|gravel|terracotta|bricks|obsidian/.test(name)) {
            // 石头类：石粒 + 裂纹 + 亮点
            fillNoise(g, base, rand, S, 34);
            pixelNoise(g, rand, S, 26, base, 0.6, 1.5);
            for (let i = 0; i < 3; i++) {
                g.fillStyle = shade(base, 0.5);
                const x = Math.floor(rand() * 27), y = Math.floor(rand() * 27);
                g.fillRect(x, y, 3 + Math.floor(rand() * 3), 1);
            }
            for (let i = 0; i < 4; i++) {
                g.fillStyle = shade(base, 1.7);
                g.fillRect(Math.floor(rand() * 30), Math.floor(rand() * 30), 1, 1);
            }
        } else if (/glass/.test(name)) {
            // 玻璃
            g.fillStyle = 'rgba(200,235,245,0.55)';
            g.fillRect(2, 2, S - 4, S - 4);
            g.strokeStyle = 'rgba(220,245,255,0.9)';
            g.lineWidth = 2;
            g.strokeRect(1, 1, S - 2, S - 2);
            g.fillStyle = 'rgba(255,255,255,0.55)';
            g.fillRect(3, 3, S - 6, 3);
            g.fillStyle = 'rgba(255,255,255,0.3)';
            g.fillRect(6, 8, 2, S - 14);
        } else if (/cactus/.test(name)) {
            // 仙人掌
            g.fillStyle = shade(base, 1);
            g.fillRect(4, 0, S - 8, S);
            for (let x = 6; x < S - 4; x += 4) {
                g.fillStyle = shade(base, 0.7);
                g.fillRect(x, 0, 1, S);
            }
            g.fillStyle = '#d8e8c8';
            for (let i = 0; i < 6; i++) g.fillRect(1 + (i % 3) * 3, 3 + Math.floor(i / 3) * 24, 1, 3);
        } else if (/ice/.test(name)) {
            // 冰
            g.globalAlpha = 0.7;
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            g.globalAlpha = 1;
            for (let i = 0; i < 8; i++) {
                g.fillStyle = 'rgba(255,255,255,0.45)';
                g.fillRect(Math.floor(rand() * 26), Math.floor(rand() * 26), 4 + Math.floor(rand() * 5), 2);
            }
        } else if (/snow/.test(name)) {
            // 雪
            fillNoise(g, base, rand, S, 12);
            pixelNoise(g, rand, S, 24, base, 0.75, 1.25);
        } else if (/sand/.test(name)) {
            // 沙：细颗粒
            fillNoise(g, base, rand, S, 20);
            pixelNoise(g, rand, S, 28, base, 0.55, 1.5);
        } else if (/sponge/.test(name)) {
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let i = 0; i < 16; i++) {
                g.fillStyle = shade(base, 0.7);
                g.fillRect(Math.floor(rand() * 28), Math.floor(rand() * 28), 3, 3);
            }
        } else if (/pumpkin|melon/.test(name)) {
            // 南瓜/西瓜：竖向条纹
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            for (let x = 0; x < S; x += 4) {
                g.fillStyle = shade(base, 0.78 + rand() * 0.2);
                g.fillRect(x, 0, 2, S);
                g.fillStyle = shade(base, 1.1);
                g.fillRect(x + 2, 0, 2, S);
            }
        } else if (/coral/.test(name)) {
            g.fillStyle = shade(base, 0.65);
            g.fillRect(0, 0, S, S);
            for (let x = 3; x < S; x += 7) {
                g.fillStyle = shade(base, 0.9 + rand() * 0.45);
                g.fillRect(x, 3, 4, S - 6);
            }
        } else if (/tnt/.test(name)) {
            g.fillStyle = shade(base, 1);
            g.fillRect(0, 0, S, S);
            g.fillStyle = '#e8e4da';
            g.fillRect(0, 12, S, 8);
            g.fillStyle = '#f8f5ee';
            g.fillRect(2, 14, S - 4, 4);
            g.fillStyle = shade(base, 0.7);
            g.font = 'bold 8px monospace';
            g.fillText('TNT', 7, 20);
        } else if (/metal|_block$|_iron|_gold|_diamond|_emerald|_lapis|_copper|_netherite|_quartz/.test(name)) {
            // 金属/矿物块：斜向光晕
            g.fillStyle = shade(base, 0.8);
            g.fillRect(0, 0, S, S);
            pixelNoise(g, rand, S, 22, base, 0.7, 1.45);
            g.fillStyle = shade(base, 1.4);
            g.fillRect(0, 0, S, 3);
            g.fillRect(0, 0, 3, S);
            g.fillStyle = shade(base, 0.55);
            g.fillRect(0, S - 3, S, 3);
            g.fillRect(S - 3, 0, 3, S);
            g.fillStyle = 'rgba(255,255,255,0.3)';
            g.fillRect(5, 5, 22, 22);
            g.fillStyle = 'rgba(0,0,0,0.12)';
            g.fillRect(9, 9, 14, 14);
        } else if (/sapling|flower|dandelion|poppy|tulip|orchid|allium|rose|cornflower|lily|mushroom|fern|tall_grass|sugar_cane|dead_bush|bamboo|wheat|carrot|potato|beetroot|vine|chorus|pink_petals|torchflower/.test(name)) {
            // 植物：透明底 + 茎 + 叶 + 花
            const stem = { r: 63, g: 143, b: 63 };
            g.fillStyle = shade(stem, 0.9 + rand() * 0.3);
            g.fillRect(15, 12, 2, 20);
            for (let i = 0; i < 3; i++) {
                g.fillStyle = shade(stem, 0.7 + rand() * 0.5);
                g.fillRect(13 + Math.floor(rand() * 6), 13 + i * 6, 2, 5);
                g.fillStyle = shade(stem, 1.15);
                g.fillRect(13 + Math.floor(rand() * 6), 13 + i * 6, 5, 2);
            }
            g.fillStyle = shade(base, 1);
            g.fillRect(12, 4, 8, 7);
            g.fillStyle = shade(base, 0.65);
            g.fillRect(14, 3, 4, 2);
            g.fillStyle = '#ffe98a';
            g.fillRect(14, 6, 4, 3);
        } else if (/rail/.test(name)) {
            // 铁轨：枕木 + 轨道
            g.fillStyle = '#5a5a5a';
            g.fillRect(0, 0, S, S);
            g.fillStyle = '#8a7a5a';
            g.fillRect(0, 10, S, 12);
            g.fillStyle = '#6a5c40';
            for (let x = 0; x < S; x += 5) g.fillRect(x, 10, 2, 2);
            g.fillStyle = '#b8a890';
            g.fillRect(0, 12, S, 3);
            g.fillStyle = '#e8e0c8';
            g.fillRect(0, 14, S, 1);
        } else if (/torch|lantern/.test(name)) {
            g.fillStyle = '#5a3a20';
            g.fillRect(13, 18, 6, 14);
            g.fillStyle = '#ff9a3a';
            g.fillRect(10, 6, 12, 12);
            g.fillStyle = '#ffe08a';
            g.fillRect(14, 10, 4, 4);
        } else if (/wool|moss/.test(name)) {
            // 羊毛：毛绒颗粒
            g.fillStyle = shade(base, 0.85);
            g.fillRect(0, 0, S, S);
            for (let i = 0; i < 22; i++) {
                g.fillStyle = shade(base, 0.7 + rand() * 0.7);
                g.fillRect(Math.floor(rand() * 28), Math.floor(rand() * 28), 3 + Math.floor(rand() * 3), 3 + Math.floor(rand() * 3));
            }
        } else {
            // 默认：多层噪点 + 风化
            fillNoise(g, base, rand, S, 40);
            pixelNoise(g, rand, S, 26, base, 0.55, 1.55);
            stain(g, rand, S, 3, 'rgba(0,0,0,0.18)');
        }
        addBevel(g, S);
        paintedCache.set(id, cv);
        return cv;
    }

    /** 物品图标（热栏/背包/拖拽层）：返回绘制纹理的 dataURL；非方块物品也返回程序化图标，避免损坏图 */
    const iconUrlCache = new Map();
    function itemIconUrl(name) {
        if (iconUrlCache.has(name)) return iconUrlCache.get(name);
        const tileId = ITEM_TILE[name];
        let url;
        if (tileId) {
            url = paintBlockTexture(tileId).toDataURL('image/png');
        } else {
            // 非方块物品（工具/食物等）：绘制色块 + 首字母，保证永远有图可显示
            const cv = document.createElement('canvas');
            cv.width = 32; cv.height = 32;
            const g = cv.getContext('2d');
            const b = parseColor(fallbackColor(name));
            const r = prng(hashName(name) ^ 0x9e3779b9);
            fillNoise(g, b, r, 32, 26);
            pixelNoise(g, r, 32, 18, b, 0.6, 1.5);
            addBevel(g, 32);
            g.font = 'bold 14px sans-serif';
            g.textAlign = 'center';
            g.textBaseline = 'middle';
            g.lineWidth = 3;
            g.strokeStyle = 'rgba(0,0,0,0.7)';
            const ch = (name.charAt(0) || '?').toUpperCase();
            g.strokeText(ch, 16, 17);
            g.fillStyle = 'rgba(255,255,255,0.9)';
            g.fillText(ch, 16, 17);
            url = cv.toDataURL('image/png');
        }
        iconUrlCache.set(name, url);
        return url;
    }

    // ==================== 热栏 ====================

    function renderHotbar() {
        let html = '';
        for (let i = 0; i < HOTBAR_SIZE; i++) {
            const item = inventory[i];
            const selected = i === player.slot ? 'selected' : '';
            html += `<div class="hotbar-slot ${selected}" data-index="${i}">`;
            if (item) {
                const tileId = ITEM_TILE[item.name];
                if (tileId) {
                    html += `<img class="item-icon" src="${itemIconUrl(item.name)}" alt="${item.name}">`;
                } else {
                    html += `<span style="display:block;width:28px;height:28px;background:${fallbackColor(item.name)};border:1px solid #000;"></span>`;
                }
                html += `<span class="count">${item.count}</span>`;
            }
            html += `</div>`;
        }
        hotbarEl.innerHTML = html;
        // 数字键快捷选择由键盘事件处理
    }

    // ==================== 背包面板（45 槽） ====================

    function renderInventory() {
        let html = '';
        for (let i = 0; i < INVENTORY_TOTAL; i++) {
            const item = inventory[i];
            const rowClass = i < HOTBAR_SIZE ? 'hotbar-row' : '';
            const selClass = i === player.slot ? 'selected' : '';
            const draggingClass = draggingItem && draggingItem.source === i ? 'dragging' : '';
            html += `<div class="inv-slot ${rowClass} ${selClass} ${draggingClass}" data-index="${i}">`;
            if (item) {
                const tileId = ITEM_TILE[item.name];
                if (tileId) {
                    html += `<img class="item-icon" src="${itemIconUrl(item.name)}" alt="${item.name}">`;
                } else {
                    html += `<span style="display:block;width:32px;height:32px;background:${fallbackColor(item.name)};border:1px solid #000;"></span>`;
                }
                html += `<span class="count">${item.count}</span>`;
            }
            html += `</div>`;
        }
        invGridEl.innerHTML = html;
    }

    function toggleInventory() {
        state.inventoryOpen = !state.inventoryOpen;
        if (state.inventoryOpen) {
            // 左侧玩家形象：使用当前朝向与动画帧的原图
            const tex = paintPlayer(player.direction, player.animFrame);
            if (invPlayerImgEl) invPlayerImgEl.src = tex.src;
            renderInventory();
            invPanelEl.classList.remove('hidden');
        } else {
            invPanelEl.classList.add('hidden');
            // 放下拖拽中的物品（若无目标槽则归还原位）
            if (draggingItem) {
                inventory[draggingItem.source] = draggingItem.item;
                draggingItem = null;
                hideDragLayer();
                renderInventory();
                renderHotbar();
                syncInventory();
            }
        }
    }

    // ==================== 背包拖拽（左键按住拖动，右键分半） ====================

    let dragLayerEl = null; // 拖拽跟随浮层

    function ensureDragLayer() {
        if (dragLayerEl) return;
        dragLayerEl = document.createElement('div');
        dragLayerEl.id = 'drag-item-layer';
        dragLayerEl.style.cssText = 'position:fixed;z-index:80;pointer-events:none;display:none;';
        document.body.appendChild(dragLayerEl);
    }

    function hideDragLayer() {
        if (dragLayerEl) dragLayerEl.style.display = 'none';
    }

    /** 显示拖拽浮层（物品图标跟随鼠标） */
    function showDragLayer(x, y) {
        ensureDragLayer();
        const item = draggingItem.item;
        const tileId = ITEM_TILE[item.name];
        let inner;
        if (tileId) {
            inner = `<img src="${itemIconUrl(item.name)}" style="width:40px;height:40px;display:block;">`;
        } else {
            inner = `<span style="display:block;width:40px;height:40px;background:${fallbackColor(item.name)};border:1px solid #000;"></span>`;
        }
        if (item.count > 1) {
            inner += `<span style="position:absolute;right:2px;bottom:0;color:#fff;font-weight:bold;text-shadow:1px 1px 0 #000;font-size:12px;">${item.count}</span>`;
        }
        dragLayerEl.innerHTML = `<div style="position:relative;width:40px;height:40px;background:rgba(255,255,255,0.6);border:1px solid #fff;border-radius:3px;padding:2px;">${inner}</div>`;
        dragLayerEl.style.display = 'block';
        dragLayerEl.style.left = (x - 22) + 'px';
        dragLayerEl.style.top = (y - 22) + 'px';
    }

    /**
     * 背包槽位点击（Minecraft 式）：
     * - 左键：无拖拽时整组拿起（物品粘在鼠标上跟随），有拖拽时整组放下
     *   （空位放入 / 同类堆叠 / 异类交换 / 点击原槽归位）
     * - 右键：无拖拽时分半拿起，有拖拽时从鼠标上放下 1 个
     */
    function slotClick(index, isRight) {
        const item = inventory[index];
        if (isRight) {
            if (draggingItem) {
                // 右键：从鼠标上放 1 个到槽位
                if (!item) {
                    inventory[index] = { name: draggingItem.item.name, count: 1 };
                    draggingItem.item.count--;
                } else if (item.name === draggingItem.item.name && item.count < MAX_STACK) {
                    item.count++;
                    draggingItem.item.count--;
                } else {
                    return;
                }
                if (draggingItem.item.count <= 0) {
                    draggingItem = null;
                    hideDragLayer();
                }
            } else if (item) {
                // 分半拿起
                if (item.count > 1) {
                    const half = Math.ceil(item.count / 2);
                    item.count -= half;
                    draggingItem = { item: { name: item.name, count: half }, source: index };
                } else {
                    draggingItem = { item: { name: item.name, count: 1 }, source: index };
                    inventory[index] = null;
                }
            }
        } else if (draggingItem) {
            // 左键：整组放下
            if (index === draggingItem.source) {
                inventory[index] = draggingItem.item;
                draggingItem = null;
                hideDragLayer();
            } else if (!item) {
                inventory[index] = draggingItem.item;
                draggingItem = null;
                hideDragLayer();
            } else if (item.name === draggingItem.item.name) {
                const add = Math.min(draggingItem.item.count, MAX_STACK - item.count);
                item.count += add;
                draggingItem.item.count -= add;
                if (draggingItem.item.count <= 0) {
                    draggingItem = null;
                    hideDragLayer();
                }
            } else {
                // 交换：鼠标上的变为槽位原物品，继续跟随
                inventory[index] = draggingItem.item;
                draggingItem = { item: item, source: index };
            }
        } else if (item) {
            // 拿起整组
            draggingItem = { item: { name: item.name, count: item.count }, source: index };
            inventory[index] = null;
        }
        renderInventory();
        renderHotbar();
        syncInventory();
    }

    // ==================== 输入 ====================

    window.addEventListener('keydown', (e) => {
        // 1. 按键监听（设置面板内绑定按键；主菜单/游戏内设置都可用）
        if (listeningAction) {
            if (e.code !== 'Escape') bindKey(listeningAction, e.code);
            listeningAction = null;
            renderKeyBindings();
            e.preventDefault();
            return;
        }

        // 2. 设置面板打开时：仅响应「暂停菜单」键关闭
        if (settingsPanelEl.classList.contains('hidden') === false) {
            if (actionForCode(e.code) === 'esc') closeSettings();
            e.preventDefault();
            return;
        }

        if (state.mode !== 'game') return;

        // 3. 暂停菜单（esc 动作）
        if (actionForCode(e.code) === 'esc') {
            if (state.inventoryOpen) {
                toggleInventory();
            } else if (!state.paused) {
                state.paused = true;
                escMenuEl.classList.add('esc-open');
                if (state.worldInfo) {
                    $('esc-world-info').textContent =
                        `世界: ${state.worldInfo.name}   哈希: ${state.worldInfo.seedHash || '-'}`;
                }
            } else {
                state.paused = false;
                escMenuEl.classList.remove('esc-open');
            }
            e.preventDefault();
            return;
        }
        if (state.paused) return;

        // 4. 背包（eKey 动作）
        if (actionForCode(e.code) === 'eKey') {
            toggleInventory();
            e.preventDefault();
            return;
        }

        // 5. 调试界面（f3 动作）
        if (actionForCode(e.code) === 'f3') {
            debugEl.style.display = debugEl.style.display === 'none' ? 'block' : 'none';
            e.preventDefault();
            return;
        }

        // 6. 移动/跳跃/冲刺动作（走绑定表）
        const action = actionForCode(e.code);
        if (action) {
            input.keys[action] = true;
            e.preventDefault();
        }

        // 7. 数字键选快捷栏
        const n = parseInt(e.key, 10);
        if (n >= 1 && n <= HOTBAR_SIZE) {
            player.slot = n - 1;
            renderHotbar();
            showSlotName();
            send({ type: 'playerState', slot: player.slot });
        }
    });

    window.addEventListener('keyup', (e) => {
        if (state.mode !== 'game') return;
        const action = actionForCode(e.code);
        if (action) {
            input.keys[action] = false;
            e.preventDefault();
        }
    });

    window.addEventListener('blur', () => {
        for (const k in input.keys) input.keys[k] = false;
    });

    canvas.addEventListener('mousemove', (e) => {
        if (state.mode !== 'game') return;
        const rect = canvas.getBoundingClientRect();
        input.mouse.x = Math.floor((e.clientX - rect.left) * (canvas.width / rect.width));
        input.mouse.y = Math.floor((e.clientY - rect.top) * (canvas.height / rect.height));
    });

    canvas.addEventListener('mousedown', (e) => {
        if (state.mode !== 'game' || state.paused || state.inventoryOpen) return;
        if (e.button === 0) input.mouse.left = true;
        if (e.button === 2) input.mouse.right = true;
        e.preventDefault();
    });

    window.addEventListener('mouseup', (e) => {
        if (state.mode !== 'game') return;
        if (e.button === 0) input.mouse.left = false;
        if (e.button === 2) input.mouse.right = false;
    });

    // 滚轮切换物品栏（向上上一格、向下下一格，循环）
    canvas.addEventListener('wheel', (e) => {
        if (state.mode !== 'game' || state.paused || state.inventoryOpen) return;
        e.preventDefault();
        const dir = e.deltaY > 0 ? 1 : -1;
        player.slot = (player.slot + dir + HOTBAR_SIZE) % HOTBAR_SIZE;
        renderHotbar();
        showSlotName();
        send({ type: 'playerState', slot: player.slot });
    }, { passive: false });

    // 全局禁用右键上下文菜单（游戏内右键用于放置方块）
    document.addEventListener('contextmenu', (e) => e.preventDefault());

    // 背包交互：点击拿起/放下（物品粘在鼠标上跟随，Minecraft 式）
    invGridEl.addEventListener('mousedown', (e) => {
        e.preventDefault();
        const slot = e.target.closest('.inv-slot');
        if (!slot) return;
        const index = parseInt(slot.dataset.index, 10);
        slotClick(index, e.button === 2);
        if (draggingItem) showDragLayer(e.clientX, e.clientY);
        else hideDragLayer();
    });

    // ==================== 背包悬停详情（悬停立即在右侧显示方块信息） ====================

    let invHoverIndex = -1;

    /** 显示右侧详情面板 */
    function showInvDetail(index) {
        const el = $('inv-detail');
        const item = inventory[index];
        if (!el || !item) { hideInvDetail(); return; }
        const tileId = ITEM_TILE[item.name];
        const meta = tileId ? TILE_META[tileId] : null;
        const name = zhBlockName(item.name);
        const img = itemIconUrl(item.name);
        let html = `<div class="inv-detail-icon"><img src="${img}" alt=""></div>`;
        html += `<div class="inv-detail-text">`;
        html += `<div class="inv-detail-name">${escapeHtml(name)}</div>`;
        if (meta) {
            const rows = [];
            rows.push(`硬度: ${meta.hardness != null ? meta.hardness : '不可破坏'}　堆叠: ${meta.stackSize}`);
            rows.push(`${meta.solid ? '实体方块' : '非实体'}　${meta.transparent ? '透明' : '不透明'}`);
            if (meta.drops) rows.push(`掉落: ${escapeHtml(zhBlockName(meta.drops))}`);
            html += `<div class="inv-detail-stats">${rows.map(r => `<div>${r}</div>`).join('')}</div>`;
        } else {
            html += `<div class="inv-detail-stats"><div>非方块物品</div></div>`;
        }
        html += `</div>`;
        el.innerHTML = html;
    }

    function hideInvDetail() {
        const el = $('inv-detail');
        if (el) el.innerHTML = '';
    }

    // 事件委托：悬停立即显示，同格内移动不刷新，移出网格立即隐藏
    invGridEl.addEventListener('mouseover', (e) => {
        const slot = e.target.closest('.inv-slot');
        if (!slot) return;
        const idx = parseInt(slot.dataset.index, 10);
        if (idx === invHoverIndex) return;
        invHoverIndex = idx;
        showInvDetail(idx);
    });
    invGridEl.addEventListener('mouseleave', () => {
        invHoverIndex = -1;
        hideInvDetail();
    });

    // 物品跟随鼠标移动（即使松开按键，物品也一直粘在鼠标上）
    document.addEventListener('mousemove', (e) => {
        if (draggingItem && dragLayerEl) {
            dragLayerEl.style.left = (e.clientX - 22) + 'px';
            dragLayerEl.style.top = (e.clientY - 22) + 'px';
        }
    });

    // ==================== 菜单事件 ====================

    // 设置面板从何处打开（原版 fromGame）：true=游戏内 ESC 打开，false=主菜单打开
    let settingsFromGame = false;

    /** 关闭设置面板（原版 SettingsPanel onClose：回游戏暂停 或 回主菜单） */
    function closeSettings() {
        settingsPanelEl.classList.add('hidden');
        listeningAction = null;
        if (settingsFromGame && state.mode === 'game') {
            escMenuEl.classList.add('esc-open');
        }
    }

    $('btn-single').addEventListener('click', () => {
        showWorldSelect();
    });

    $('btn-multi').addEventListener('click', () => {
        $('multi-connect').classList.remove('hidden');
        $('multi-address').value = localStorage.getItem('lastServer') || '';
        $('multi-address').focus();
    });

    $('btn-back-multi').addEventListener('click', () => {
        $('multi-connect').classList.add('hidden');
    });

    $('btn-connect').addEventListener('click', () => {
        let addr = $('multi-address').value.trim();
        if (!addr) {
            showMenuError('请输入服务器地址');
            return;
        }
        if (!addr.startsWith('ws://')) addr = 'ws://' + addr;
        localStorage.setItem('lastServer', addr);
        enterGame(addr);
    });

    $('btn-exit-game').addEventListener('click', () => {
        try { window.close(); } catch (e) { /* ignore */ }
        if (!window.closed) location.href = 'about:blank';
    });

    $('btn-settings').addEventListener('click', () => {
        settingsFromGame = false;
        settingsPanelEl.classList.remove('hidden');
    });

    $('btn-settings-close').addEventListener('click', closeSettings);

    // 设置侧边栏选项切换（原版 selectedOption）
    document.querySelectorAll('.settings-option').forEach((opt) => {
        opt.addEventListener('click', () => {
            document.querySelectorAll('.settings-option').forEach((o) => o.classList.remove('selected'));
            opt.classList.add('selected');
            const page = opt.dataset.option;
            $('settings-keys').classList.toggle('hidden', page !== 'keys');
            $('settings-autojump').classList.toggle('hidden', page !== 'autojump');
            $('settings-game').classList.toggle('hidden', page !== 'game');
            listeningAction = null;
            renderKeyBindings();
        });
    });

    // 自动选择方块（开关持久化到 localStorage）
    $('set-autoselect').checked = autoSelectEnabled;
    $('set-autoselect').addEventListener('change', (e) => {
        autoSelectEnabled = e.target.checked;
        localStorage.setItem('autoSelect', autoSelectEnabled ? '1' : '0');
    });

    // 自动跨步开关（前方一格高方块直接走上，原 localStorage key 兼容）
    const autoJumpToggleEl = $('toggle-autojump');
    function updateAutoJumpToggle() {
        autoJumpToggleEl.classList.toggle('on', autoStepEnabled);
    }
    updateAutoJumpToggle();
    autoJumpToggleEl.addEventListener('click', () => {
        autoStepEnabled = !autoStepEnabled;
        localStorage.setItem('autoJump', autoStepEnabled ? '1' : '0');
        updateAutoJumpToggle();
    });

    // 按键绑定：渲染列表 + 恢复默认
    renderKeyBindings();
    $('btn-keybinds-reset').addEventListener('click', () => {
        for (const a of BIND_ACTIONS) keyBinds[a.id] = a.defaults;
        saveKeyBinds();
        renderKeyBindings();
    });

    // ---- 世界选择 ----

    $('btn-world-back').addEventListener('click', () => {
        hideWorldSelect();
    });

    $('btn-seed-refresh').addEventListener('click', () => {
        $('world-seed').value = randomSeedText();
    });

    $('btn-world-create').addEventListener('click', () => {
        const name = $('world-name').value.trim() || 'block world';
        const seed = $('world-seed').value.trim();
        localStorage.setItem('lastWorldName', name);
        if (!menuWs || menuWs.readyState !== WebSocket.OPEN) {
            showWorldError('未连接服务器');
            return;
        }
        pendingEnterWorld = name;
        menuWs.send(JSON.stringify({ type: 'createWorld', name, seed }));
    });

    $('btn-continue').addEventListener('click', () => {
        state.paused = false;
        escMenuEl.classList.remove('esc-open');
    });

    $('btn-save').addEventListener('click', () => {
        send({ type: 'saveRequest' });
        showBanner('世界已保存');
    });

    $('btn-esc-overlay').addEventListener('click', () => {
        state.paused = false;
        escMenuEl.classList.remove('esc-open');
    });

    $('btn-esc-settings').addEventListener('click', () => {
        settingsFromGame = true;
        settingsPanelEl.classList.remove('hidden');
        escMenuEl.classList.remove('esc-open');
    });

    $('btn-exit').addEventListener('click', () => {
        send({ type: 'saveRequest' });
        setTimeout(() => showMenu(), 300);
    });

    // ==================== 调试 ====================

    function renderDebug() {
        if (debugEl.style.display === 'none') return;
        const p = player;
        debugEl.textContent = [
            `FPS: ${Math.round(1000 / Math.max(1, debugFrameTime))}`,
            `位置: (${Math.floor(p.x)}, ${Math.floor(p.y)})`,
            `朝向: ${p.direction}  跳跃: ${p.jumpPhase}`,
            `Vy: ${p.vy.toFixed(1)}  地面: ${p.onGround}`,
            `冲刺: ${p.dashCharges}/${p.dashMax}`,
            `相机: (${Math.floor(state.camera.x)}, ${Math.floor(state.camera.y)})`,
            `区块缓存: ${state.chunks.size}`,
            `掉落物: ${state.serverDrops.length}`,
            `连接: ${state.connected ? '在线' : '断开'}`,
            `其他玩家: ${state.remotePlayers.size - 1}`,
        ].join('\n');
    }

    let debugFrameTime = 16;

    // ==================== 工具 ====================

    function base64ToBytes(b64) {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        return bytes;
    }

    function showBanner(text) {
        bannerEl.textContent = text;
        bannerEl.classList.add('visible');
    }

    function showMenuError(text) {
        const el = $('menu-error');
        el.textContent = text;
        el.classList.remove('hidden');
        setTimeout(() => el.classList.add('hidden'), 2500);
    }

    // ==================== 主循环 ====================

    let lastFrame = performance.now();

    function gameLoop() {
        resize();
        if (state.mode === 'game') {
            const now = performance.now();
            debugFrameTime = now - lastFrame;
            lastFrame = now;
            render();
        }
        requestAnimationFrame(gameLoop);
    }

    // ==================== 启动 ====================

    showMenu();
    resize();
    gameLoop();
})();
