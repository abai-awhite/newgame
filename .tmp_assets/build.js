/**
 * 一次性构建脚本（任务专用，非游戏代码）：
 * 1. 读取 minecraft-data 1.21.1 的 blocks/items + minecraft-assets 1.21.1 的方块纹理名
 * 2. 生成前端 server/src/main/web/blocks_data.js（window.BLOCKS_DATA）
 * 3. 生成服务端 server/src/main/java/server/BlockData.java
 * 4. 并发下载全部方块纹理到 server/src/main/web/textures/block/
 *
 * 运行：node .tmp_assets/build.js
 */
'use strict';
const fs = require('fs');
const path = require('path');
const https = require('https');

const ROOT = path.resolve(__dirname, '..');
const TMP = path.resolve(__dirname);
const WEB_DIR = path.join(ROOT, 'server', 'src', 'main', 'web');
const TEX_DIR = path.join(WEB_DIR, 'textures', 'block');
const DATA_VER = '1.21.1';

const bt = JSON.parse(fs.readFileSync(path.join(TMP, 'bt_1211.json'), 'utf8'));      // 数组，索引=方块ID
const blocks = JSON.parse(fs.readFileSync(path.join(TMP, 'blocks_1211.json'), 'utf8')); // 数组，含 id
const items = JSON.parse(fs.readFileSync(path.join(TMP, 'items_1211.json'), 'utf8'));   // 数组，含 id

const itemById = new Map(items.map(i => [i.id, i.name]));
const blockById = new Map(blocks.map(b => [b.id, b]));

// 2D 侧视覆盖：这些方块默认给了顶面/主面纹理，横版游戏用侧面更合适
const SIDE = {
    grass_block: 'grass_block_side',
    crafting_table: 'crafting_table_front',
    furnace: 'furnace_front_off',
    blast_furnace: 'blast_furnace_front_off',
    smoker: 'smoker_front_off',
    bookshelf: 'bookshelf',
    barrel: 'barrel_side',
    dispenser: 'dispenser_front_vertical',
    dropper: 'dropper_front_vertical',
    observer: 'observer_front',
    piston: 'piston_side',
    sticky_piston: 'piston_side',
};

function texName(t) {
    if (!t) return null;
    // "minecraft:blocks/stone" / "blocks/dirt" / "minecraft:item/barrier" -> 取最后一段
    const s = t.replace(/^minecraft:/, '');
    const i = s.lastIndexOf('/');
    const name = i >= 0 ? s.slice(i + 1) : s;
    return /^[a-z0-9_]+$/.test(name) ? name : null;
}

/** 纹理候选列表（按优先级，下载取第一个成功者） */
function candidatesFor(name, tex) {
    const list = [];
    const push = (n) => { if (n && !list.includes(n)) list.push(n); };
    push(SIDE[name]);
    // log / stripped log：数据默认给了 *_top，2D 用侧面（= 方块名本身）
    if (/^(\w+)_log$/.test(name) && !name.startsWith('stripped')) push(name);
    if (/^stripped_(\w+)_log$/.test(name)) push(name);
    push(tex);
    push(name);
    push(name + '_side');
    return list;
}

// ==================== 生成数据 ====================

const NAMES = new Array(bt.length).fill(null);
bt.forEach((x, id) => { NAMES[id] = x.name; });

const DROPS = new Array(bt.length).fill(null); // 方块ID -> 掉落物品名（无则 null）
const ITEM_TO_BLOCK = new Map();               // 物品名 -> 方块ID

const candidates = {}; // id -> [候选纹理名]
const texSet = new Set();

for (let id = 0; id < bt.length; id++) {
    const b = blockById.get(id) || blocks[id];
    const name = bt[id].name;
    const tex = texName(bt[id].texture);
    const cand = candidatesFor(name, tex);
    candidates[id] = cand;
    cand.forEach(c => texSet.add(c));

    // 掉落：drops(物品id) -> 物品名；空且可挖 -> 掉自身
    let drops = [];
    if (b && b.drops && b.drops.length) {
        drops = b.drops.map(d => itemById.get(d)).filter(Boolean);
    } else if (b && b.diggable && b.boundingBox === 'block') {
        drops = [name];
    }
    DROPS[id] = drops[0] || null;

    // 可放置物品：任何有名称的非空气方块都注册（含流体/植物/装饰）
    if (name && name !== 'air') {
        if (!ITEM_TO_BLOCK.has(name)) ITEM_TO_BLOCK.set(name, id);
    }
}

// ==================== 下载纹理 ====================

const BASE = `https://raw.githubusercontent.com/PrismarineJS/minecraft-assets/master/data/${DATA_VER}/blocks/`;
const downloaded = new Set();
const { execFile } = require('child_process');

// 本机直连 GitHub 不可用：改用 curl.exe（走系统代理）
function dl(file, retry) {
    return new Promise((resolve) => {
        const dest = path.join(TEX_DIR, file + '.png');
        if (fs.existsSync(dest) && fs.statSync(dest).size > 0) { resolve(true); return; } // 已下载过（非空文件）直接算成功
        const url = BASE + encodeURIComponent(file) + '.png';
        execFile('curl.exe', ['--ssl-no-revoke', '-s', '-o', dest, url], { timeout: 30000 }, (err) => {
            if (err) {
                try { fs.rmSync(dest, { force: true }); } catch (e) { /* ignore */ }
                resolve(false);
            } else {
                resolve(fs.existsSync(dest) && fs.statSync(dest).size > 0);
            }
        });
    });
}

async function downloadAll() {
    fs.mkdirSync(TEX_DIR, { recursive: true });
    const files = [...texSet];
    let idx = 0;
    const pool = 12;
    let failed = [];

    async function worker() {
        while (idx < files.length) {
            const f = files[idx++];
            let ok = false;
            for (let t = 0; t < 3 && !ok; t++) {
                ok = await dl(f, t);
            }
            if (ok) downloaded.add(f);
            else failed.push(f);
        }
    }

    const workers = [];
    for (let i = 0; i < pool; i++) workers.push(worker());
    await Promise.all(workers);

    // 决定每个方块的最终纹理：第一个成功候选
    const finalTex = {};
    for (let id = 0; id < bt.length; id++) {
        let chosen = null;
        for (const c of candidates[id]) {
            if (downloaded.has(c)) { chosen = c; break; }
        }
        finalTex[id] = chosen;
    }
    return { failed, finalTex };
}

(async () => {
    const { failed, finalTex } = await downloadAll();
    console.log('纹理下载完成: 成功 ' + downloaded.size + ', 失败 ' + failed.length);

    // ==================== 前端 blocks_data.js ====================

    const out = {};
    for (let id = 0; id < bt.length; id++) {
        const b = blockById.get(id) || blocks[id];
        out[id] = {
            n: bt[id].name,
            d: (b && b.displayName) || bt[id].name,
            t: finalTex[id],                          // 纹理文件名（可能 null）
            s: !!(b && b.boundingBox === 'block'),    // 实体（可站立/碰撞）
            tr: !!(b && b.transparent),               // 透明（渲染用）
            st: (b && b.stackSize) || 64,
            h: (b && b.hardness) || 0,
            dr: DROPS[id] || null,                    // 掉落物品名
        };
    }
    const js = '// 由 .tmp_assets/build.js 自动生成（Minecraft 1.21.1 方块数据，含 1.21 及更早版本）——请勿手改\n' +
        'window.BLOCKS_DATA = ' + JSON.stringify(out) + ';\n';
    fs.writeFileSync(path.join(WEB_DIR, 'blocks_data.js'), js, 'utf8');
    console.log('已生成 blocks_data.js (' + Object.keys(out).length + ' 方块)');

    // ==================== 服务端 BlockData.java ====================

    const esc = (s) => s.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    const lines = [];
    lines.push('package server;');
    lines.push('');
    lines.push('/**');
    lines.push(' * 由 .tmp_assets/build.js 自动生成 —— 请勿手改。');
    lines.push(' * Minecraft 1.21.1 方块数据（包含 1.21 及更早版本全部方块）。');
    lines.push(' */');
    lines.push('public final class BlockData {');
    lines.push('    private BlockData() {}');
    lines.push('');
    lines.push('    /** 方块 ID -> 方块名（索引即 ID，null 表示无） */');
    lines.push('    public static final String[] NAMES = {');
    lines.push('        ' + NAMES.map(n => n == null ? 'null' : '"' + esc(n) + '"').join(', '));
    lines.push('    };');
    lines.push('');
    lines.push('    /** 方块 ID -> 掉落物品名（无掉落 null） */');
    lines.push('    public static final String[] DROPS = {');
    lines.push('        ' + DROPS.map(d => d == null ? 'null' : '"' + esc(d) + '"').join(', '));
    lines.push('    };');
    lines.push('');
    lines.push('    /** 物品名 -> 方块 ID（放置用） */');
    lines.push('    public static final java.util.Map<String, Integer> ITEM_TO_BLOCK = new java.util.HashMap<>();');
    lines.push('    static {');
    for (const [name, id] of ITEM_TO_BLOCK) {
        lines.push(`        ITEM_TO_BLOCK.put("${esc(name)}", ${id});`);
    }
    lines.push('    }');
    lines.push('}');
    fs.writeFileSync(path.join(ROOT, 'server', 'src', 'main', 'java', 'server', 'BlockData.java'), lines.join('\n'), 'utf8');
    console.log('已生成 BlockData.java (' + ITEM_TO_BLOCK.size + ' 可放置物品)');

    if (failed.length) {
        fs.writeFileSync(path.join(TMP, 'missing.txt'), failed.join('\n'), 'utf8');
        console.log('缺失纹理 ' + failed.length + ' 个，清单见 .tmp_assets/missing.txt');
    }
})();
