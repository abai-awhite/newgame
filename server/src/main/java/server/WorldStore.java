package server;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 世界注册表：管理 world/ 下的多世界元数据（名称 / 哈希 / 种子）。
 *
 * <p><b>双层种子</b>（每个世界都有）:</p>
 * <ol>
 *   <li>第一层（世界哈希，菜单中展示）：SHA-256(name + "|" + seedText) 取前 16 位十六进制；</li>
 *   <li>第二层（真实地形种子）：对第一层哈希再做一次 SHA-256，取前 8 字节解析为 long
 *       —— 即"给哈希再套一层哈希"，由它驱动 InfiniteMap 的地形生成。</li>
 * </ol>
 *
 * <p>每个世界的元数据写入 world/&lt;name&gt;/world.json：
 * {"name": ..., "seed": &lt;long&gt;, "seedHash": "&lt;16hex&gt;"}。
 * 服务器启动时的初始世界沿用配置种子（保证旧存档区块一致），新创建的世界一律双层派生。</p>
 */
public class WorldStore {

    /** 世界根目录 */
    public static final String WORLD_ROOT = "world";

    /** 世界元数据 */
    public static class WorldMeta {
        public final String name;
        /** 第二层：真实地形种子（传给 InfiniteMap） */
        public final long seed;
        /** 第一层：世界哈希（菜单展示用） */
        public final String seedHash;
        public final long lastModified;

        WorldMeta(String name, long seed, String seedHash, long lastModified) {
            this.name = name;
            this.seed = seed;
            this.seedHash = seedHash;
            this.lastModified = lastModified;
        }

        JSONObject toJson() {
            return new JSONObject()
                    .put("name", name)
                    .put("seed", seed)
                    .put("seedHash", seedHash)
                    .put("lastModified", lastModified);
        }
    }

    // ==================== 双层种子 ====================

    /** SHA-256 摘要，取前 8 字节输出大写十六进制（16 位）。 */
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 必然存在；兜底用简单散列
            long h = 1125899906842597L;
            for (char c : input.toCharArray()) h = h * 31 + c;
            return Long.toHexString(h).toUpperCase();
        }
    }

    // ==================== 元数据读写 ====================

    private static String sanitizeName(String name) {
        String s = (name == null ? "" : name).replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return s.isEmpty() ? "新世界" : s;
    }

    private static Path worldDir(String name) {
        return Paths.get(WORLD_ROOT, sanitizeName(name));
    }

    private static Path metaFile(String name) {
        return worldDir(name).resolve("world.json");
    }

    /**
     * 创建新世界：目录不存在则创建并写入元数据（双层种子派生）；
     * 名字已存在时自动在末尾追加 _1/_2/... 直到唯一（如 "世界1" -> "世界1_1"），
     * 返回实际创建的世界元数据。
     */
    public static WorldMeta createWorld(String name, String seedText) {
        String safe = sanitizeName(name);
        String base = safe;
        for (int i = 1; loadMeta(safe) != null; i++) {
            safe = base + "_" + i;
        }

        String seedStr = (seedText == null || seedText.isBlank()) ? "0" : seedText.trim();
        // 第一层：世界哈希
        String h1 = sha256Hex(safe + "|" + seedStr);
        // 第二层：对第一层哈希再哈希得到真实地形种子（哈希套哈希）
        long seed = Long.parseUnsignedLong(sha256Hex(h1), 16);

        try {
            Files.createDirectories(worldDir(safe));
        } catch (IOException e) {
            System.err.println("创建世界目录失败 " + safe + ": " + e.getMessage());
        }
        WorldMeta meta = new WorldMeta(safe, seed, h1, System.currentTimeMillis());
        writeMeta(meta);
        System.out.println("创建新世界: " + safe + " (地形种子 " + seed + ", 哈希 " + h1 + ")");
        return meta;
    }

    /**
     * 确保世界元数据存在：缺失时按 fallbackSeed 创建（仅用于服务器启动的初始世界，
     * 保持旧存档区块的种子一致；哈希仍按双层规则展示）。
     */
    public static WorldMeta ensureMeta(String name, long fallbackSeed) {
        String safe = sanitizeName(name);
        WorldMeta existing = loadMeta(safe);
        if (existing != null) return existing;

        try {
            Files.createDirectories(worldDir(safe));
        } catch (IOException e) {
            System.err.println("创建世界目录失败 " + safe + ": " + e.getMessage());
        }
        String h1 = sha256Hex(safe + "|" + fallbackSeed);
        WorldMeta meta = new WorldMeta(safe, fallbackSeed, h1, System.currentTimeMillis());
        writeMeta(meta);
        System.out.println("初始化世界元数据: " + safe + " (种子 " + fallbackSeed + ", 哈希 " + h1 + ")");
        return meta;
    }

    private static void writeMeta(WorldMeta meta) {
        try {
            JSONObject obj = new JSONObject()
                    .put("name", meta.name)
                    .put("seed", meta.seed)
                    .put("seedHash", meta.seedHash);
            Files.writeString(metaFile(meta.name), obj.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("写入世界元数据失败 " + meta.name + ": " + e.getMessage());
        }
    }

    /** 加载世界元数据，不存在返回 null。 */
    public static WorldMeta loadMeta(String name) {
        Path file = metaFile(name);
        if (!Files.exists(file)) return null;
        try {
            JSONObject obj = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            return new WorldMeta(
                    obj.optString("name", sanitizeName(name)),
                    obj.optLong("seed", 0L),
                    obj.optString("seedHash", ""),
                    Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            System.err.println("读取世界元数据失败 " + name + ": " + e.getMessage());
            return null;
        }
    }

    /** 存档数据版本。v1 = 旧方块 ID（1~6）；v2 = Minecraft 原版方块 ID。 */
    private static final int WORLD_DATA_VERSION = 2;

    /**
     * 世界存档版本检查（进入世界前调用）：
     * 旧版世界（world.json 无 version 字段）返回 true 并在元数据中打上版本标记，
     * 供地图加载时执行旧方块 ID -> Minecraft 原版 ID 的一次性迁移；新版返回 false。
     */
    public static boolean ensureMetaVersion(String name) {
        String safe = sanitizeName(name);
        Path file = metaFile(safe);
        if (!Files.exists(file)) return false;
        try {
            JSONObject obj = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            boolean legacy = !obj.has("version");
            if (legacy) {
                obj.put("version", WORLD_DATA_VERSION);
                Files.writeString(file, obj.toString(), StandardCharsets.UTF_8);
                System.out.println("世界「" + safe + "」为旧版存档，已标记升级到数据版本 " + WORLD_DATA_VERSION);
            }
            return legacy;
        } catch (Exception e) {
            System.err.println("检查世界数据版本失败 " + safe + ": " + e.getMessage());
            return false;
        }
    }

    /** 列出所有世界（按最近修改时间倒序），缺失元数据的旧目录兜底创建。 */
    public static List<WorldMeta> listWorlds() {
        List<WorldMeta> list = new ArrayList<>();
        Path root = Paths.get(WORLD_ROOT);
        if (!Files.isDirectory(root)) return list;
        try (var stream = Files.newDirectoryStream(root)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                String name = dir.getFileName().toString();
                if (name.startsWith(".")) continue;
                WorldMeta meta = loadMeta(name);
                if (meta == null) {
                    meta = ensureMeta(name, 0L);
                }
                list.add(meta);
            }
        } catch (IOException e) {
            System.err.println("扫描世界目录失败: " + e.getMessage());
        }
        list.sort(Comparator.comparingLong((WorldMeta m) -> m.lastModified).reversed());
        return list;
    }

    /** 删除世界（递归删除整个目录），返回是否删除。 */
    public static boolean deleteWorld(String name) {
        String safe = sanitizeName(name);
        Path dir = worldDir(safe);
        if (!Files.isDirectory(dir)) return false;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    System.err.println("删除世界文件失败 " + p + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            System.err.println("删除世界失败 " + safe + ": " + e.getMessage());
            return false;
        }
        System.out.println("删除世界: " + safe);
        return true;
    }

    /** 世界列表 JSON（回复给前端）。 */
    public static JSONArray listToJson() {
        JSONArray arr = new JSONArray();
        for (WorldMeta m : listWorlds()) {
            arr.put(m.toJson());
        }
        return arr;
    }
}
