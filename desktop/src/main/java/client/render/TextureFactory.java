package client.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

import client.data.BlockMeta;

import java.util.HashMap;
import java.util.Map;

/**
 * 程序化方块纹理（移植 game.js 的 paintBlockTexture，Pixmap 绘制，32x32）。
 * 按方块名关键词选择画法，确定性伪随机（mulberry32），按 ID 缓存。
 */
public class TextureFactory {

    public static final int SIZE = 32;

    private final Map<Integer, Texture> cache = new HashMap<>();

    /** 名称哈希（确定性，同 JS hashName，无符号 32 位） */
    public static int hashName(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = (h * 31 + name.charAt(i));
        return h;
    }

    /** 确定性伪随机序列（mulberry32，同 JS prng） */
    public static class Prng {
        private int t;

        public Prng(int seed) {
            this.t = seed;
        }

        public float next() {
            t += 0x6D2B79F5;
            int r = (t ^ (t >>> 15)) * (1 | t);
            r ^= r + (r ^ (r >>> 7)) * (61 | r);
            long u = (r ^ (r >>> 14)) & 0xFFFFFFFFL;
            return (float) (u / 4294967296.0);
        }
    }

    /** 名称 -> 基础主色（同 JS blockBaseColor，返回 0-255 RGB 数组） */
    public static float[] blockBaseColor(String name) {
        if (name == null || name.isEmpty()) return rgb(0x80, 0x80, 0x80);
        if (name.contains("water") || name.contains("seagrass") || name.contains("kelp")) return rgb(0x3f, 0x76, 0xe4);
        if (name.contains("lava")) return rgb(0xe2, 0x5b, 0x2a);
        if (name.contains("sand")) return rgb(0xe3, 0xd7, 0xa1);
        if (name.contains("grass_block")) return rgb(0x9c, 0x6b, 0x3f);
        if (name.contains("dirt") || name.contains("mud")) return rgb(0x9c, 0x6b, 0x3f);
        if (name.contains("log") || name.contains("wood") || name.contains("bark") || name.contains("stem")) return rgb(0x6b, 0x4a, 0x2f);
        if (name.contains("planks")) return rgb(0xa8, 0x81, 0x4f);
        if (name.contains("leaves") || name.contains("foliage") || name.contains("roots")) return rgb(0x4a, 0x9e, 0x3c);
        if (name.contains("stone") || name.contains("deepslate") || name.contains("cobble") || name.contains("andesite")
                || name.contains("granite") || name.contains("diorite") || name.contains("tuff") || name.contains("basalt")
                || name.contains("blackstone") || name.contains("calcite") || name.contains("sculk")) return rgb(0x8a, 0x8a, 0x8a);
        if (name.contains("bedrock")) return rgb(0x3a, 0x3a, 0x3a);
        if (name.contains("snow")) return rgb(0xf2, 0xf5, 0xfa);
        if (name.contains("ice")) return rgb(0xa9, 0xdc, 0xef);
        if (name.contains("coal")) return rgb(0x3a, 0x3a, 0x3a);
        if (name.contains("iron")) return rgb(0xd8, 0xd8, 0xd8);
        if (name.contains("copper")) return rgb(0xc8, 0x73, 0x3d);
        if (name.contains("gold")) return rgb(0xf7, 0xd6, 0x3a);
        if (name.contains("diamond")) return rgb(0x4a, 0xe0, 0xe8);
        if (name.contains("emerald")) return rgb(0x3a, 0xd6, 0x62);
        if (name.contains("redstone")) return rgb(0xe8, 0x32, 0x32);
        if (name.contains("lapis")) return rgb(0x2a, 0x4b, 0xd7);
        if (name.contains("quartz")) return rgb(0xe8, 0xe6, 0xea);
        if (name.contains("amethyst")) return rgb(0x9d, 0x5f, 0xd6);
        if (name.contains("netherite")) return rgb(0x3c, 0x3c, 0x3c);
        if (name.contains("glass")) return rgb(0xe8, 0xf4, 0xf8);
        if (name.contains("pumpkin")) return rgb(0xe0, 0x8a, 0x2e);
        if (name.contains("melon")) return rgb(0x7f, 0xbf, 0x4e);
        if (name.contains("cactus")) return rgb(0x3f, 0x7f, 0x3f);
        if (name.contains("clay") || name.contains("terracotta")) return rgb(0xa0, 0x87, 0x6a);
        if (name.contains("moss")) return rgb(0x6f, 0xae, 0x5a);
        if (name.contains("wool")) return rgb(0xe8, 0xe8, 0xe8);
        if (name.contains("sponge")) return rgb(0xe8, 0xd8, 0x4a);
        if (name.contains("tnt")) return rgb(0xd8, 0x32, 0x32);
        if (name.contains("coral")) return rgb(0xe8, 0x78, 0x6a);
        if (name.contains("mushroom")) return rgb(0xb0, 0x71, 0x3f);
        if (name.contains("slime")) return rgb(0x6f, 0xbf, 0x4a);
        if (name.contains("obsidian")) return rgb(0x1f, 0x1f, 0x28);
        if (name.contains("sea_lantern") || name.contains("prismarine")) return rgb(0x7f, 0xd8, 0xc8);
        if (name.contains("hay")) return rgb(0xd8, 0xb8, 0x4a);
        if (name.contains("bamboo")) return rgb(0x5f, 0xa8, 0x3f);
        if (name.contains("nether") || name.contains("soul")) return rgb(0x6a, 0x3a, 0x3a);
        if (name.contains("concrete")) return rgb(0xb0, 0xb0, 0xb0);
        if (name.contains("shulker")) return rgb(0xa0, 0x6a, 0x8a);
        int h = Math.floorMod(hashName(name), 360);
        return hslToRgb(h, 0.40f, 0.55f);
    }

    private static float[] rgb(int r, int g, int b) {
        return new float[]{r, g, b};
    }

    /** HSL(0-360, 0-1, 0-1) -> RGB 0-255（标准转换） */
    private static float[] hslToRgb(int h, float s, float l) {
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float hp = ((h % 360) / 60f);
        float x = c * (1 - Math.abs(hp % 2 - 1));
        float r = 0, g = 0, b = 0;
        if (hp < 1) { r = c; g = x; }
        else if (hp < 2) { r = x; g = c; }
        else if (hp < 3) { g = c; b = x; }
        else if (hp < 4) { g = x; b = c; }
        else if (hp < 5) { r = x; b = c; }
        else { r = c; b = x; }
        float m = l - c / 2;
        return new float[]{(r + m) * 255, (g + m) * 255, (b + m) * 255};
    }

    /** 颜色数组(0-255) -> libGDX Color */
    public static Color toColor(float[] rgb) {
        return new Color(rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f, 1f);
    }

    private static int clamp255(float v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    private static Color shade(float[] base, float f) {
        return new Color(clamp255(base[0] * f) / 255f, clamp255(base[1] * f) / 255f, clamp255(base[2] * f) / 255f, 1f);
    }

    /** 物品名哈希兜底色（同 JS fallbackColor，hsl 55% 55%） */
    public static Color fallbackColor(String name) {
        int h = 0;
        for (int i = 0; i < name.length(); i++) h = (h * 31 + name.charAt(i));
        return toColor(hslToRgb(Math.floorMod(h, 360), 0.55f, 0.55f));
    }

    /** 获取方块纹理（缓存） */
    public Texture getTexture(int id, BlockMeta meta) {
        Texture t = cache.get(id);
        if (t != null) return t;
        String name = meta != null && meta.name != null ? meta.name : ("block" + id);
        t = paintBlock(id, name, meta);
        cache.put(id, t);
        return t;
    }

    private Texture paintBlock(int id, String name, BlockMeta meta) {
        Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.SourceOver);
        Prng rand = new Prng(hashName(name) ^ (int) (id * 2654435761L));
        float[] base = blockBaseColor(name);
        int S = SIZE;

        if (name.contains("water") || name.contains("seagrass") || name.contains("kelp")) {
            pm.setColor(0.72f, 0.72f, 0.72f, 0.72f);
            pm.setColor(shade(base, 1).r, shade(base, 1).g, shade(base, 1).b, 0.72f);
            pm.fillRectangle(0, 0, S, S);
            for (int y = 2; y < S; y += 6) {
                Color hi = shade(base, 1.35f);
                pm.setColor(hi);
                pm.fillRectangle(0, y, S, 2);
                Color lo = shade(base, 0.72f);
                pm.setColor(lo);
                pm.fillRectangle(2, y + 3, S - 4, 1);
            }
            pm.setColor(0.85f, 0.85f, 0.85f, 0.85f);
            pm.setColor(1, 1, 1, 0.5f);
            for (int i = 0; i < 3; i++) {
                pm.fillRectangle((int) (rand.next() * 26), (int) (rand.next() * 26), 5 + (int) (rand.next() * 6), 1);
            }
        } else if (name.contains("lava")) {
            pm.setColor(shade(base, 0.85f));
            pm.fillRectangle(0, 0, S, S);
            for (int y = 4; y < S; y += 8) {
                pm.setColor(shade(base, 1.25f));
                pm.fillRectangle(0, y, S, 2);
                pm.setColor(shade(base, 0.6f));
                pm.fillRectangle(0, y + 3, S, 1);
            }
            for (int i = 0; i < 8; i++) {
                pm.setColor(shade(base, 1.4f + rand.next() * 0.3f));
                pm.fillRectangle((int) (rand.next() * 27), (int) (rand.next() * 27), 3 + (int) (rand.next() * 4), 3 + (int) (rand.next() * 3));
            }
            pm.setColor(1f, 0.83f, 0.29f, 1f);
            for (int i = 0; i < 5; i++) pm.fillRectangle((int) (rand.next() * 29), (int) (rand.next() * 29), 2, 2);
        } else if (name.contains("grass_block")) {
            float[] dirt = rgb(0x9c, 0x6b, 0x3f);
            fillNoise(pm, dirt, rand, S, 26);
            pixelNoise(pm, rand, S, 26, dirt, 0.7f, 1.35f);
            stain(pm, rand, S, 5, new Color(70 / 255f, 45 / 255f, 25 / 255f, 0.55f));
            float[] gs = rgb(0x6f, 0xae, 0x3c);
            pm.setColor(shade(gs, 0.8f));
            pm.fillRectangle(0, 0, S, 8);
            for (int x = 0; x < S; x += 2) {
                int hgt = 3 + (int) (rand.next() * 3);
                pm.setColor(shade(gs, 0.9f + rand.next() * 0.4f));
                pm.fillRectangle(x, 8 - hgt + 2, 2, hgt);
            }
            pm.setColor(shade(gs, 0.7f));
            pm.fillRectangle(0, 8, S, 1);
        } else if (name.contains("dirt") || name.contains("mud") || name.contains("podzol") || name.contains("path")) {
            fillNoise(pm, base, rand, S, 24);
            pixelNoise(pm, rand, S, 22, base, 0.65f, 1.4f);
            stain(pm, rand, S, 5, new Color(60 / 255f, 38 / 255f, 20 / 255f, 0.5f));
            for (int i = 0; i < 4; i++) {
                pm.setColor(shade(base, 1.5f));
                pm.fillRectangle((int) (rand.next() * 27), (int) (rand.next() * 27), 3, 3);
                pm.setColor(0f, 0f, 0f, 0.25f);
                pm.fillRectangle((int) (rand.next() * 27), (int) (rand.next() * 27), 2, 2);
            }
        } else if ((name.endsWith("_log") || name.startsWith("log_") || name.endsWith("_wood")
                || name.endsWith("_bark") || name.endsWith("_stem")) && !name.contains("stripped")) {
            Color d0 = shade(base, 0.62f), d1 = shade(base, 0.9f);
            pm.setColor(d1);
            pm.fillRectangle(0, 0, S, S);
            int x = 0;
            while (x < S) {
                int w = 3 + (int) (rand.next() * 4);
                pm.setColor(shade(base, 0.7f + rand.next() * 0.6f));
                pm.fillRectangle(x, 0, w, S);
                pm.setColor(shade(base, 1.1f + rand.next() * 0.3f));
                pm.fillRectangle(x, 0, 1, S);
                x += w;
            }
            for (int i = 0; i < 4; i++) {
                pm.setColor(d0);
                pm.fillRectangle((int) (rand.next() * 29), (int) (rand.next() * 26), 1 + (int) (rand.next() * 2), 5 + (int) (rand.next() * 5));
            }
            for (int i = 0; i < 2; i++) {
                int nx = 5 + (int) (rand.next() * 22), ny = 4 + (int) (rand.next() * 24);
                pm.setColor(d0);
                pm.fillRectangle(nx, ny, 4, 4);
                pm.setColor(shade(base, 0.45f));
                pm.fillRectangle(nx + 1, ny + 1, 2, 2);
            }
        } else if (name.contains("stripped")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            for (int sx = 0; sx < S; sx += 8) {
                pm.setColor(shade(base, 0.82f));
                pm.fillRectangle(sx + 1, 0, 2, S);
                pm.setColor(shade(base, 1.12f));
                pm.fillRectangle(sx + 5, 0, 2, S);
            }
            pixelNoise(pm, rand, S, 18, base, 0.8f, 1.25f);
        } else if (name.contains("planks") || name.endsWith("_top")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            for (int y = 0; y < S; y += 8) {
                pm.setColor(shade(base, 0.78f));
                pm.fillRectangle(0, y + 7, S, 1);
                pm.setColor(shade(base, 1.22f));
                pm.fillRectangle(0, y, S, 2);
                for (int i = 0; i < 2; i++) {
                    pm.setColor(shade(base, 0.86f + rand.next() * 0.14f));
                    pm.fillRectangle((int) (rand.next() * 4), y + 2 + (int) (rand.next() * 4), 8 + (int) (rand.next() * 16), 1);
                }
            }
            for (int y = 0; y < S; y += 8) {
                int off = ((y / 8) % 2 == 0) ? 9 : 18;
                pm.setColor(shade(base, 0.6f));
                pm.fillRectangle(off, y, 1, 7);
            }
        } else if (name.contains("leaves") || name.contains("foliage") || name.contains("roots")) {
            pm.setColor(shade(base, 0.5f));
            pm.fillRectangle(0, 0, S, S);
            for (int i = 0; i < 26; i++) {
                int sz = 4 + (int) (rand.next() * 5);
                pm.setColor(shade(base, 0.6f + rand.next() * 0.9f));
                pm.fillRectangle((int) (rand.next() * (S - sz)), (int) (rand.next() * (S - sz)), sz, sz);
            }
            pixelNoise(pm, rand, S, 40, base, 0.5f, 1.6f);
        } else if (name.endsWith("_ore")) {
            float[] st = rgb(0x8a, 0x8a, 0x8a);
            fillNoise(pm, st, rand, S, 30);
            pixelNoise(pm, rand, S, 20, st, 0.75f, 1.35f);
            for (int i = 0; i < 5; i++) {
                int cx = 2 + (int) (rand.next() * 22), cy = 2 + (int) (rand.next() * 22);
                int sz = 4 + (int) (rand.next() * 3);
                pm.setColor(shade(base, 0.85f));
                pm.fillRectangle(cx, cy, sz, sz);
                pm.setColor(shade(base, 1.1f));
                pm.fillRectangle(cx, cy, sz, 2);
                pm.setColor(shade(base, 1.35f));
                pm.fillRectangle(cx, cy, 2, sz);
                pm.setColor(1f, 1f, 1f, 0.5f);
                pm.fillRectangle(cx + 1, cy + 1, 1, 1);
            }
            pixelNoise(pm, rand, S, 10, base, 0.6f, 1.5f);
        } else if (name.contains("bedrock")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            for (int i = 0; i < 16; i++) {
                pm.setColor(shade(base, 0.55f + rand.next() * 0.95f));
                pm.fillRectangle((int) (rand.next() * 24), (int) (rand.next() * 24), 4 + (int) (rand.next() * 8), 4 + (int) (rand.next() * 8));
            }
            stain(pm, rand, S, 6, new Color(0f, 0f, 0f, 0.4f));
        } else if (name.contains("stone") || name.contains("cobble") || name.contains("andesite") || name.contains("granite")
                || name.contains("diorite") || name.contains("tuff") || name.contains("basalt") || name.contains("blackstone")
                || name.contains("calcite") || name.contains("deepslate") || name.contains("sculk") || name.contains("gravel")
                || name.contains("terracotta") || name.contains("bricks") || name.contains("obsidian")) {
            fillNoise(pm, base, rand, S, 34);
            pixelNoise(pm, rand, S, 26, base, 0.6f, 1.5f);
            for (int i = 0; i < 3; i++) {
                pm.setColor(shade(base, 0.5f));
                int px = (int) (rand.next() * 27), py = (int) (rand.next() * 27);
                pm.fillRectangle(px, py, 3 + (int) (rand.next() * 3), 1);
            }
            for (int i = 0; i < 4; i++) {
                pm.setColor(shade(base, 1.7f));
                pm.fillRectangle((int) (rand.next() * 30), (int) (rand.next() * 30), 1, 1);
            }
        } else if (name.contains("glass")) {
            pm.setColor(200 / 255f, 235 / 255f, 245 / 255f, 0.55f);
            pm.fillRectangle(2, 2, S - 4, S - 4);
            pm.setColor(220 / 255f, 245 / 255f, 255 / 255f, 0.9f);
            pm.drawRectangle(1, 1, S - 2, S - 2);
            pm.setColor(1f, 1f, 1f, 0.55f);
            pm.fillRectangle(3, 3, S - 6, 3);
            pm.setColor(1f, 1f, 1f, 0.3f);
            pm.fillRectangle(6, 8, 2, S - 14);
        } else if (name.contains("cactus")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(4, 0, S - 8, S);
            for (int cx2 = 6; cx2 < S - 4; cx2 += 4) {
                pm.setColor(shade(base, 0.7f));
                pm.fillRectangle(cx2, 0, 1, S);
            }
            pm.setColor(0xd8 / 255f, 0xe8 / 255f, 0xc8 / 255f, 1f);
            for (int i = 0; i < 6; i++) pm.fillRectangle(1 + (i % 3) * 3, 3 + (i / 3) * 24, 1, 3);
        } else if (name.contains("ice")) {
            pm.setColor(0.7f, 0.7f, 0.7f, 0.7f);
            pm.setColor(shade(base, 1f).r, shade(base, 1f).g, shade(base, 1f).b, 0.7f);
            pm.fillRectangle(0, 0, S, S);
            pm.setColor(1f, 1f, 1f, 0.45f);
            for (int i = 0; i < 8; i++) pm.fillRectangle((int) (rand.next() * 26), (int) (rand.next() * 26), 4 + (int) (rand.next() * 5), 2);
        } else if (name.contains("snow")) {
            fillNoise(pm, base, rand, S, 12);
            pixelNoise(pm, rand, S, 24, base, 0.75f, 1.25f);
        } else if (name.contains("sand")) {
            fillNoise(pm, base, rand, S, 20);
            pixelNoise(pm, rand, S, 28, base, 0.55f, 1.5f);
        } else if (name.contains("sponge")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            for (int i = 0; i < 16; i++) {
                pm.setColor(shade(base, 0.7f));
                pm.fillRectangle((int) (rand.next() * 28), (int) (rand.next() * 28), 3, 3);
            }
        } else if (name.contains("pumpkin") || name.contains("melon")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            for (int x2 = 0; x2 < S; x2 += 4) {
                pm.setColor(shade(base, 0.78f + rand.next() * 0.2f));
                pm.fillRectangle(x2, 0, 2, S);
                pm.setColor(shade(base, 1.1f));
                pm.fillRectangle(x2 + 2, 0, 2, S);
            }
        } else if (name.contains("coral")) {
            pm.setColor(shade(base, 0.65f));
            pm.fillRectangle(0, 0, S, S);
            for (int x2 = 3; x2 < S; x2 += 7) {
                pm.setColor(shade(base, 0.9f + rand.next() * 0.45f));
                pm.fillRectangle(x2, 3, 4, S - 6);
            }
        } else if (name.contains("tnt")) {
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(0, 0, S, S);
            pm.setColor(0xe8 / 255f, 0xe4 / 255f, 0xda / 255f, 1f);
            pm.fillRectangle(0, 12, S, 8);
            pm.setColor(0xf8 / 255f, 0xf5 / 255f, 0xee / 255f, 1f);
            pm.fillRectangle(2, 14, S - 4, 4);
            pm.setColor(shade(base, 0.7f));
            pm.fillRectangle(0, 12, S, 1);
            pm.fillRectangle(0, 19, S, 1);
        } else if (name.endsWith("_block") || name.contains("_iron") || name.contains("_gold") || name.contains("_diamond")
                || name.contains("_emerald") || name.contains("_lapis") || name.contains("_copper") || name.contains("_netherite")
                || name.contains("_quartz")) {
            pm.setColor(shade(base, 0.8f));
            pm.fillRectangle(0, 0, S, S);
            pixelNoise(pm, rand, S, 22, base, 0.7f, 1.45f);
            pm.setColor(shade(base, 1.4f));
            pm.fillRectangle(0, 0, S, 3);
            pm.fillRectangle(0, 0, 3, S);
            pm.setColor(shade(base, 0.55f));
            pm.fillRectangle(0, S - 3, S, 3);
            pm.fillRectangle(S - 3, 0, 3, S);
            pm.setColor(1f, 1f, 1f, 0.3f);
            pm.fillRectangle(5, 5, 22, 22);
            pm.setColor(0f, 0f, 0f, 0.12f);
            pm.fillRectangle(9, 9, 14, 14);
        } else if (isPlant(name)) {
            float[] stem = rgb(0x3f, 0x8f, 0x3f);
            pm.setColor(shade(stem, 0.9f + rand.next() * 0.3f));
            pm.fillRectangle(15, 12, 2, 20);
            for (int i = 0; i < 3; i++) {
                pm.setColor(shade(stem, 0.7f + rand.next() * 0.5f));
                pm.fillRectangle(13 + (int) (rand.next() * 6), 13 + i * 6, 2, 5);
                pm.setColor(shade(stem, 1.15f));
                pm.fillRectangle(13 + (int) (rand.next() * 6), 13 + i * 6, 5, 2);
            }
            pm.setColor(shade(base, 1f));
            pm.fillRectangle(12, 4, 8, 7);
            pm.setColor(shade(base, 0.65f));
            pm.fillRectangle(14, 3, 4, 2);
            pm.setColor(0xff / 255f, 0xe9 / 255f, 0x8a / 255f, 1f);
            pm.fillRectangle(14, 6, 4, 3);
        } else if (name.contains("rail")) {
            pm.setColor(0x5a / 255f, 0x5a / 255f, 0x5a / 255f, 1f);
            pm.fillRectangle(0, 0, S, S);
            pm.setColor(0x8a / 255f, 0x7a / 255f, 0x5a / 255f, 1f);
            pm.fillRectangle(0, 10, S, 12);
            pm.setColor(0x6a / 255f, 0x5c / 255f, 0x40 / 255f, 1f);
            for (int x2 = 0; x2 < S; x2 += 5) pm.fillRectangle(x2, 10, 2, 2);
            pm.setColor(0xb8 / 255f, 0xa8 / 255f, 0x90 / 255f, 1f);
            pm.fillRectangle(0, 12, S, 3);
            pm.setColor(0xe8 / 255f, 0xe0 / 255f, 0xc8 / 255f, 1f);
            pm.fillRectangle(0, 14, S, 1);
        } else if (name.contains("torch") || name.contains("lantern")) {
            pm.setColor(0x5a / 255f, 0x3a / 255f, 0x20 / 255f, 1f);
            pm.fillRectangle(13, 18, 6, 14);
            pm.setColor(0xff / 255f, 0x9a / 255f, 0x3a / 255f, 1f);
            pm.fillRectangle(10, 6, 12, 12);
            pm.setColor(0xff / 255f, 0xe0 / 255f, 0x8a / 255f, 1f);
            pm.fillRectangle(14, 10, 4, 4);
        } else if (name.contains("wool") || name.contains("moss")) {
            pm.setColor(shade(base, 0.85f));
            pm.fillRectangle(0, 0, S, S);
            for (int i = 0; i < 22; i++) {
                pm.setColor(shade(base, 0.7f + rand.next() * 0.7f));
                pm.fillRectangle((int) (rand.next() * 28), (int) (rand.next() * 28), 3 + (int) (rand.next() * 3), 3 + (int) (rand.next() * 3));
            }
        } else {
            fillNoise(pm, base, rand, S, 40);
            pixelNoise(pm, rand, S, 26, base, 0.55f, 1.55f);
            stain(pm, rand, S, 3, new Color(0f, 0f, 0f, 0.18f));
        }
        addBevel(pm, S);
        Texture tex = new Texture(pm);
        pm.dispose();
        return tex;
    }

    /** 植物类方块（透明底 + 茎叶花） */
    private static boolean isPlant(String name) {
        return name.contains("sapling") || name.contains("flower") || name.contains("dandelion") || name.contains("poppy")
                || name.contains("tulip") || name.contains("orchid") || name.contains("allium") || name.contains("rose")
                || name.contains("cornflower") || name.contains("lily") || name.contains("mushroom") || name.contains("fern")
                || name.contains("tall_grass") || name.contains("sugar_cane") || name.contains("dead_bush") || name.contains("bamboo")
                || name.contains("wheat") || name.contains("carrot") || name.contains("potato") || name.contains("beetroot")
                || name.contains("vine") || name.contains("chorus") || name.contains("pink_petals") || name.contains("torchflower");
    }

    private static void fillNoise(Pixmap pm, float[] base, Prng rand, int size, float range) {
        for (int y = 0; y < size; y += 4) {
            for (int x = 0; x < size; x += 4) {
                float d = (rand.next() - 0.5f) * range;
                pm.setColor(shade(base, 1 + d / 255));
                pm.fillRectangle(x, y, 4, 4);
            }
        }
    }

    private static void addBevel(Pixmap pm, int size) {
        pm.setColor(0f, 0f, 0f, 0.28f);
        pm.fillRectangle(0, 0, size, 2);
        pm.fillRectangle(0, 0, 2, size);
        pm.setColor(1f, 1f, 1f, 0.14f);
        pm.fillRectangle(0, size - 2, size, 2);
        pm.fillRectangle(size - 2, 0, 2, size);
    }

    private static void pixelNoise(Pixmap pm, Prng rand, int size, int n, float[] base, float lo, float hi) {
        for (int i = 0; i < n; i++) {
            pm.setColor(shade(base, lo + rand.next() * (hi - lo)));
            pm.fillRectangle((int) (rand.next() * (size - 1)), (int) (rand.next() * (size - 1)),
                    1 + ((int) (rand.next() * 2)), 1 + ((int) (rand.next() * 2)));
        }
    }

    private static void stain(Pixmap pm, Prng rand, int size, int n, Color color) {
        for (int i = 0; i < n; i++) {
            pm.setColor(color);
            pm.fillRectangle((int) (rand.next() * (size - 5)), (int) (rand.next() * (size - 5)),
                    3 + (int) (rand.next() * 4), 3 + (int) (rand.next() * 4));
        }
    }

    public void dispose() {
        for (Texture t : cache.values()) t.dispose();
        cache.clear();
    }
}
