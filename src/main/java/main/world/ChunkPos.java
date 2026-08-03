package main.world;

import java.util.Objects;

/**
 * 表示区块在区块坐标系中的位置（非格子坐标）。
 * 作为 HashMap 的键使用，必须实现 equals 和 hashCode。
 */
public class ChunkPos {
    public final int cx;
    public final int cy;

    public ChunkPos(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    // ====== 基础方法（保持不变） ======
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos chunkPos = (ChunkPos) o;
        return cx == chunkPos.cx && cy == chunkPos.cy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cx, cy);
    }

    @Override
    public String toString() {
        return "Chunk(" + cx + ", " + cy + ")";
    }

    // ====== 辅助方法（可选，便于地图管理） ======

    /**
     * 获取相邻区块（上下左右）
     */
    public ChunkPos getNeighbor(int dx, int dy) {
        return new ChunkPos(cx + dx, cy + dy);
    }

    /**
     * 计算与另一个区块的切比雪夫距离（用于卸载判断）
     */
    public int chebyshevDistance(ChunkPos other) {
        return Math.max(Math.abs(cx - other.cx), Math.abs(cy - other.cy));
    }

    /**
     * 计算与另一个区块的曼哈顿距离
     */
    public int manhattanDistance(ChunkPos other) {
        return Math.abs(cx - other.cx) + Math.abs(cy - other.cy);
    }

    /**
     * 将区块坐标转换为该区块左上角格子的世界坐标
     */
    public int toWorldX() {
        return cx * Chunk.SIZE;
    }

    public int toWorldY() {
        return cy * Chunk.SIZE;
    }
}