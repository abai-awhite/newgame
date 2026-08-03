package main.world;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 安全种子哈希工具类，用于处理游戏世界生成的种子数据。
 *
 * <h3>设计背景</h3>
 * <p>原始 PerlinNoise 使用 long 类型直接作为 Random 的种子，
 * 这种方式存在可预测性风险。SeedHasher 提供了：
 * <ul>
 *   <li>密码学安全的哈希处理</li>
 *   <li>盐值（salt）支持，防止彩虹表攻击</li>
 *   <li>多种输出格式（十六进制字符串、长整型、字节数组）</li>
 *   <li>确定性输出，相同输入必产生相同结果</li>
 * </ul>
 *
 * <h3>算法选择</h3>
 * <p>本实现选用 <b>SHA-256</b> 作为哈希算法，原因如下：</p>
 * <ul>
 *   <li><b>安全性</b>：256位输出，提供优秀的碰撞抵抗能力（2^128 攻击复杂度）</li>
 *   <li><b>性能</b>：比 SHA-3 系列更快，适合实时程序化生成场景</li>
 *   <li><b>确定性</b>：相同输入必产生相同输出，适合可重现的地形生成</li>
 *   <li><b>广泛支持</b>：所有主流平台原生支持，无需额外库</li>
 * </ul>
 *
 * <p><b>注意</b>：如果目标是密码存储，应使用 bcrypt、scrypt 或 Argon2；
 * 但对于游戏种子处理，SHA-256 的性能优势更为重要。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 直接哈希（无盐）
 * long seed = SeedHasher.hashToLong("my-world-seed");
 *
 * // 带盐哈希（更安全）
 * byte[] salt = SeedHasher.generateSalt();
 * long seed = SeedHasher.hashToLong("player-created-world", salt);
 *
 * // 哈希到字节数组（完整哈希值）
 * byte[] hash = SeedHasher.hashToBytes("deterministic-seed");
 * }</pre>
 *
 * @see PerlinNoise
 */
public class SeedHasher {

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;

    private static final char[] HEX_CHARS =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private SeedHasher() {
    }

    /**
     * 将种子字符串转换为 SHA-256 哈希值，并以十六进制字符串形式返回。
     *
     * @param seed 原始种子字符串
     * @return 64字符的十六进制哈希字符串
     */
    public static String hashToHex(String seed) {
        return hashToHex(seed, null);
    }

    /**
     * 将种子字符串与盐值结合后转换为 SHA-256 哈希值。
     *
     * @param seed 原始种子字符串
     * @param salt 盐值（可为 null，表示无盐）
     * @return 64字符的十六进制哈希字符串
     */
    public static String hashToHex(String seed, byte[] salt) {
        byte[] hashBytes = hashToBytes(seed, salt);
        return bytesToHex(hashBytes);
    }

    /**
     * 将种子字符串转换为长整型哈希值。
     *
     * <p>实现方式：取 SHA-256 哈希的前 8 字节，转换为有符号长整型。</p>
     * <p>此方法适用于直接作为 {@link PerlinNoise} 或 {@link java.util.Random} 的种子。</p>
     *
     * @param seed 原始种子字符串
     * @return 哈希后的长整型值
     */
    public static long hashToLong(String seed) {
        return hashToLong(seed, null);
    }

    /**
     * 将种子字符串与盐值结合后转换为长整型哈希值。
     *
     * @param seed 原始种子字符串
     * @param salt 盐值（可为 null，表示无盐）
     * @return 哈希后的长整型值
     */
    public static long hashToLong(String seed, byte[] salt) {
        byte[] hashBytes = hashToBytes(seed, salt);
        return bytesToLong(hashBytes);
    }

    /**
     * 将种子字符串转换为字节数组形式的 SHA-256 哈希值。
     *
     * @param seed 原始种子字符串
     * @return 32字节的哈希数组
     */
    public static byte[] hashToBytes(String seed) {
        return hashToBytes(seed, null);
    }

    /**
     * 将种子字符串与盐值结合后转换为字节数组形式的 SHA-256 哈希值。
     *
     * <h3>处理流程</h3>
     * <ol>
     *   <li>将种子字符串转换为 UTF-8 字节数组</li>
     *   <li>如有盐值，追加到种子字节数组后</li>
     *   <li>使用 SHA-256 算法计算摘要</li>
     *   <li>返回 32 字节的哈希结果</li>
     * </ol>
     *
     * @param seed 原始种子字符串
     * @param salt 盐值（可为 null，表示无盐）
     * @return 32字节的哈希数组
     */
    public static byte[] hashToBytes(String seed, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);

            byte[] seedBytes = seed.getBytes(StandardCharsets.UTF_8);
            digest.update(seedBytes);

            if (salt != null) {
                digest.update(salt);
            }

            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new SecurityException(
                "SHA-256 algorithm not available. This should never happen on modern JVMs.",
                e
            );
        }
    }

    /**
     * 生成密码学安全的随机盐值。
     *
     * <p>使用 {@link SecureRandom} 生成盐值，确保不可预测性。</p>
     * <p>盐值长度为 16 字节（128 位），提供足够的熵防止彩虹表攻击。</p>
     *
     * @return 随机盐值数组
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(salt);
        return salt;
    }

    /**
     * 生成随机盐值的十六进制字符串表示。
     *
     * @return 32字符的十六进制盐值字符串
     */
    public static String generateSaltHex() {
        return bytesToHex(generateSalt());
    }

    /**
     * 将字节数组转换为十六进制字符串。
     *
     * @param bytes 任意字节数组
     * @return 十六进制字符串（每字节两位）
     */
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_CHARS[v >>> 4];
            hexChars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 将字节数组的前 8 字节转换为有符号长整型。
     *
     * <p>字节数组长度不足 8 字节时行为未定义。</p>
     * <p>使用大端序（Big Endian）转换，保持跨平台一致性。</p>
     *
     * @param bytes 至少 8 字节的数组
     * @return 有符号长整型值
     */
    public static long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }
        return result;
    }

    /**
     * 将长整型转换为 8 字节数组（大端序）。
     *
     * @param value 长整型值
     * @return 8 字节数组
     */
    public static byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>>= 8;
        }
        return result;
    }

    /**
     * 比较两个字节数组是否相等（常量时间比较）。
     *
     * <p>使用常量时间比较防止时序攻击（timing attack）。</p>
     * <p>适用于安全敏感的字节序列比较（如盐值、MAC 值等）。</p>
     *
     * @param a 第一个字节数组
     * @param b 第二个字节数组
     * @return true 如果两数组完全相等
     */
    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    /**
     * 将长整型种子安全地混合到另一个种子中。
     *
     * <p>适用于需要组合多个种子值的场景（如世界种子 + 玩家ID）。</p>
     * <p>使用简单的异或和位移混合，确保结果不可逆。</p>
     *
     * @param baseSeed 基础种子
     * @param extraSeed 额外种子
     * @return 混合后的种子
     */
    public static long mixSeed(long baseSeed, long extraSeed) {
        long h1 = Long.hashCode(baseSeed);
        long h2 = Long.hashCode(extraSeed);
        h1 ^= (h2 >>> 16);
        h1 *= 0x85EBCA6B;
        h2 ^= (h1 >>> 13);
        h2 *= 0xC2B2AE35;
        h1 ^= (h2 >>> 16);
        h1 *= 0x85EBCA6B;
        return h1;
    }
}