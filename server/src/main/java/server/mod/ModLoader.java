package server.mod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组加载器（骨架）：扫描/加载 mods/ 目录下的模组。
 *
 * <p>本期仅做目录扫描占位（记录发现的 jar/目录），不实际加载执行，
 * 避免引入类加载器复杂度。后期实现时：扫描 mods/ → 类加载 → 实例化 Mod → init。</p>
 */
public class ModLoader {

    /** 模组扫描目录（相对工作目录） */
    private static final String MODS_DIR = "mods";

    private final List<String> discovered = new ArrayList<>();

    /**
     * 扫描 mods/ 目录（占位实现：仅记录发现项，不加载）。
     *
     * @return 发现的模组条目列表
     */
    public List<String> scan() {
        discovered.clear();
        File dir = new File(MODS_DIR);
        if (!dir.exists() || !dir.isDirectory()) {
            return discovered;
        }
        File[] files = dir.listFiles();
        if (files == null) return discovered;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".jar")) {
                discovered.add(f.getAbsolutePath());
            } else if (f.isDirectory()) {
                discovered.add(f.getAbsolutePath());
            }
        }
        return new ArrayList<>(discovered);
    }
}
