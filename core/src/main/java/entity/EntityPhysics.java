package entity;

/**
 * 实体碰撞系统：轴分离 AABB 方块碰撞（实体 vs 方块网格）。
 *
 * <p>算法与玩家物理同构：逐轴移动，扫描碰撞盒覆盖的格子，命中实心方块则吸附到边界。
 * 方块固体判定通过 {@link Entity.Solidity} 注入，默认用 {@link Entity#DEFAULT_SOLIDITY}
 * （非空气且非液体即实心），使实体能与沙/木/树叶等所有方块正确碰撞。</p>
 *
 * <p>性能：每轴只扫碰撞盒覆盖的 1~3 个格子（O(1)），不会触发远处区块生成。</p>
 */
public final class EntityPhysics {

    private static final double EPSILON = 1e-6;

    /** 一次移动的碰撞结果 */
    public static class CollisionResult {
        public boolean hitX;
        public boolean hitY;
        /** 移动后是否贴地（脚底所在行有实心方块） */
        public boolean onGround;
    }

    private EntityPhysics() {}

    /**
     * 轴分离移动 + 方块碰撞：先 X 后 Y。
     *
     * @param e                 目标实体（位置中心语义，碰撞箱来自 getAABB）
     * @param dx                水平位移（像素/tick）
     * @param dy                垂直位移（像素/tick，y 向下为正）
     * @param solid             方块固体判定
     * @param worldHeightTiles  世界总高度（格）
     */
    public static CollisionResult move(Entity e, double dx, double dy,
                                       Entity.Solidity solid, int worldHeightTiles) {
        CollisionResult r = new CollisionResult();
        double tsize = e.getTileSize();

        // --- X 轴 ---
        if (dx != 0) {
            double newX = e.getX() + dx;
            AABB box = e.getAABB();
            AABB bx = new AABB(newX - box.width / 2, box.y, box.width, box.height);
            int tlx = (int) Math.floor(bx.x / tsize);
            int trx = (int) Math.floor((bx.x + bx.width) / tsize - EPSILON);
            int tly = Math.max(0, (int) Math.floor(bx.y / tsize));
            int try_ = Math.min(worldHeightTiles - 1, (int) Math.floor((bx.y + bx.height) / tsize - EPSILON));
            boolean hit = false;
            outerX:
            for (int ty = tly; ty <= try_; ty++) {
                for (int tx = tlx; tx <= trx; tx++) {
                    if (!solid.isSolid(e.getMap().getTileType(tx, ty))) continue;
                    if (dx > 0) newX = tx * tsize - box.width / 2;
                    else newX = (tx + 1) * tsize + box.width / 2;
                    newX = Math.round(newX);
                    hit = true;
                    break outerX;
                }
            }
            e.setX(newX);
            r.hitX = hit;
        }

        // --- Y 轴 ---
        if (dy != 0) {
            double newY = e.getY() + dy;
            AABB box = e.getAABB();
            AABB by = new AABB(box.x, newY - box.height / 2, box.width, box.height);
            int tlx = (int) Math.floor(by.x / tsize);
            int trx = (int) Math.floor((by.x + by.width) / tsize - EPSILON);
            int tly = Math.max(0, (int) Math.floor(by.y / tsize));
            int try_ = Math.min(worldHeightTiles - 1, (int) Math.floor((by.y + by.height) / tsize - EPSILON));
            boolean hit = false;
            outerY:
            for (int ty = tly; ty <= try_; ty++) {
                for (int tx = tlx; tx <= trx; tx++) {
                    if (!solid.isSolid(e.getMap().getTileType(tx, ty))) continue;
                    if (dy > 0) newY = ty * tsize - box.height / 2;      // 下落撞顶面
                    else newY = (ty + 1) * tsize + box.height / 2;        // 上移撞底面
                    newY = Math.round(newY);
                    hit = true;
                    break outerY;
                }
            }
            e.setY(newY);
            r.hitY = hit;
        }

        // --- 地面检测（移动后，检查脚底覆盖的所有列） ---
        AABB fb = e.getAABB();
        double footY = fb.y + fb.height;
        int tileY = (int) Math.floor(footY / tsize);
        int tlx = (int) Math.floor(fb.x / tsize);
        int trx = (int) Math.floor((fb.x + fb.width) / tsize - EPSILON);
        if (tileY >= 0 && tileY < worldHeightTiles) {
            for (int tx = tlx; tx <= trx; tx++) {
                if (solid.isSolid(e.getMap().getTileType(tx, tileY))) {
                    double blockTop = tileY * tsize;
                    if (Math.abs(footY - blockTop) < EPSILON || footY <= blockTop) {
                        r.onGround = true;
                        break;
                    }
                }
            }
        }
        return r;
    }
}
