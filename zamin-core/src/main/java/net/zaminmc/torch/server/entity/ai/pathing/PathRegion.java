package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The immutable region snapshot — the pathfinding workload's explicit input
 * contract (Phase 5's §6.5 gate: the search may leave the owner only when
 * its inputs are explicit). The material grid of the search's reachable
 * box, captured on the tick lane as one flat byte array (the
 * {@link CellMaterial} ordinals); the off-lane search reads ONLY this — no
 * world reference crosses the owner boundary (§6.4).
 *
 * <p>THE BOX BOUNDS (why the AIR answer outside the box is sound): the
 * search's g-cost cap ({@code f < range * 2.0F} in PathFinder) accumulates
 * the SQUARED per-step distances, so a node at displacement D from the
 * start carries {@code distanceFromStart >= D} — every reachable node sits
 * within {@code 2 * range} per axis of the mob. The evaluator's per-node
 * reads (the body box, the neighbor scan's column) add at most the body
 * half-width and one step — the capture box takes {@code 2 * range + 2}
 * per axis (clamped to the world's y 0..255), so a cell the search can
 * actually consult is always inside the box; the outside answer (AIR) is
 * consulted only by nodes the cost cap already refuses. The equivalence
 * test proves the bound empirically: the snapshot search and the live
 * search agree node-for-node, including the unreachable-target fallback
 * (the search that explores the whole reachable set).</p>
 */
public final class PathRegion implements PathWorld {

    private static final CellMaterial[] MATERIALS = CellMaterial.values();

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final byte[] grid;

    private PathRegion(int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                       byte[] grid) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.grid = grid;
    }

    /**
     * Captures the reachable box around the mob on the caller's (tick) lane.
     *
     * @param world the live path view — read ONLY here, on the owning lane
     * @param mobX the mob's X center
     * @param mobY the mob's Y (the box bottom, the feet)
     * @param mobZ the mob's Z center
     * @param range the search's follow range
     * @return the immutable snapshot the off-lane search reads
     */
    public static PathRegion capture(PathWorld world, double mobX, double mobY, double mobZ,
                                     float range) {
        int reach = (int) Math.ceil(range * 2.0F) + 2;
        int minX = (int) Math.floor(mobX) - reach;
        int maxX = (int) Math.ceil(mobX) + reach;
        int minZ = (int) Math.floor(mobZ) - reach;
        int maxZ = (int) Math.ceil(mobZ) + reach;
        int minY = Math.max(0, (int) Math.floor(mobY) - reach);
        int maxY = Math.min(255, (int) Math.ceil(mobY) + reach);
        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        byte[] grid = new byte[sizeX * sizeY * sizeZ];
        int index = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    grid[index++] = (byte) PathWorld.materialOrAir(
                            world, x, y, z).ordinal();
                }
            }
        }
        return new PathRegion(minX, minY, minZ, sizeX, sizeY, sizeZ, grid);
    }

    /** @return the captured box's min X (the snapshot's own bounds, for tests). */
    public int minX() {
        return minX;
    }

    /** @return the captured box's min Y. */
    public int minY() {
        return minY;
    }

    /** @return the captured box's min Z. */
    public int minZ() {
        return minZ;
    }

    /** @return the captured box's X size. */
    public int sizeX() {
        return sizeX;
    }

    /** @return the captured box's Y size. */
    public int sizeY() {
        return sizeY;
    }

    /** @return the captured box's Z size. */
    public int sizeZ() {
        return sizeZ;
    }

    /** @return the captured cell count (the snapshot's own footprint). */
    public int cellCount() {
        return grid.length;
    }

    @Override
    public CellMaterial materialAt(int x, int y, int z) {
        int ix = x - minX;
        if (ix < 0 || ix >= sizeX) {
            return CellMaterial.AIR;
        }
        int iy = y - minY;
        if (iy < 0 || iy >= sizeY) {
            return CellMaterial.AIR;
        }
        int iz = z - minZ;
        if (iz < 0 || iz >= sizeZ) {
            return CellMaterial.AIR;
        }
        // The capture loop's own layout: y outer, z middle, x fastest
        // (the x-inner walk the cache-friendly capture takes).
        return MATERIALS[grid[(iy * sizeZ + iz) * sizeX + ix] & 0xFF];
    }
}
