package main.world;

/**
 * 区块坐标（不可变值对象）。
 */
public class ChunkPos {
    public final int cx;
    public final int cy;

    public ChunkPos(int cx, int cy) {
        this.cx = cx;
        this.cy = cy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos pos = (ChunkPos) o;
        return cx == pos.cx && cy == pos.cy;
    }

    @Override
    public int hashCode() {
        return 31 * cx + cy;
    }

    @Override
    public String toString() {
        return "ChunkPos{" + cx + "," + cy + "}";
    }
}
