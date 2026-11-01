package net.zaminmc.torch.server.border;

import net.zaminmc.torch.server.player.PlayerRegistry;

/**
 * The engine's world border (WorldBorder 1:1): the center, the size (default
 * 6.0E7 — the unconfigured border), the lerp walk toward a target size over
 * wall-clock time, the clamp at the max size, and the damage family (the
 * safe-zone buffer, the per-block rate) plus the warning pair. Every setter
 * broadcasts its wire verb through every online link — the reference's
 * WorldBorderListener/PlayerManager pair collapsed into the model itself.
 *
 * <p>Tick-lane mutations (the command walk); the lerp reads are wall-clock
 * (the reference's own System.currentTimeMillis walk).</p>
 *
 * <p>Extensible for the nether's read-only view ({@link NetherWorldBorderView}
 * — NetherDimension.createWorldBorder lines 75-86: the same rows with the
 * center reported at /8); the containment reads consult the overridable
 * center pair, the reference's own structure.</p>
 */
public class EngineWorldBorder {

    /** The reference's own clamp (29999984). */
    private static final int DEFAULT_MAX_SIZE = 29999984;
    /** The historical unconfigured size (6.0E7 blocks). */
    private static final double DEFAULT_SIZE = 6.0E7;

    private final PlayerRegistry players;

    private double centerX;
    private double centerZ;
    private double size = DEFAULT_SIZE;
    private double sizeLerpTarget = size;
    private long sizeChangeEnd;
    private long sizeChangeStart;
    private int maxSize = DEFAULT_MAX_SIZE;
    private double damagePerBlock = 0.2;
    private double safeZone = 5.0;
    private int warningTime = 15;
    private int warningDistance = 5;

    public EngineWorldBorder(PlayerRegistry players) {
        this.players = players;
    }

    // --- the containment reads (WorldBorder lines 24-51) ----------------------

    /** @return whether the cell-sized box at the block position overlaps the border. */
    public boolean contains(int x, int z) {
        return x + 1 > getMinX() && x < getMaxX() && z + 1 > getMinZ() && z < getMaxZ();
    }

    /** The player-box containment (contains(Box)): a 0.6-wide box at the feet. */
    public boolean containsBox(double minX, double minZ, double maxX, double maxZ) {
        return maxX > getMinX() && minX < getMaxX() && maxZ > getMinZ() && minZ < getMaxZ();
    }

    /** @return the distance from (x, z) to the nearest border edge (negative = outside). */
    public double getDistanceFrom(double x, double z) {
        double d0 = z - getMinZ();
        double d1 = getMaxZ() - z;
        double d2 = x - getMinX();
        double d3 = getMaxX() - x;
        double d4 = Math.min(d2, d3);
        d4 = Math.min(d4, d0);
        return Math.min(d4, d1);
    }

    // --- the geometry (lines 61-95) --------------------------------------------

    public double getMinX() {
        return Math.max(getCenterX() - getLerpSize() / 2.0, -maxSize);
    }

    public double getMinZ() {
        return Math.max(getCenterZ() - getLerpSize() / 2.0, -maxSize);
    }

    public double getMaxX() {
        return Math.min(getCenterX() + getLerpSize() / 2.0, maxSize);
    }

    public double getMaxZ() {
        return Math.min(getCenterZ() + getLerpSize() / 2.0, maxSize);
    }

    public double getCenterX() {
        return centerX;
    }

    public double getCenterZ() {
        return centerZ;
    }

    public void setCenter(double x, double z) {
        this.centerX = x;
        this.centerZ = z;
        for (var session : players.all()) {
            session.link().worldBorderCenter(x, z);
        }
    }

    // --- the lerp walk (lines 53-59 + 114-155) ----------------------------------

    /** @return true while the lerp is actively shrinking. */
    public boolean isShrinking() {
        return sizeLerpTarget < size;
    }

    /** @return true while the lerp is actively growing. */
    public boolean isGrowing() {
        return sizeLerpTarget > size;
    }

    /** The live size (the wall-clock lerp; completes and snaps when past the end). */
    public double getLerpSize() {
        if (isShrinking() || isGrowing()) {
            double progress = (double) (System.currentTimeMillis() - sizeChangeStart)
                    / (double) (sizeChangeEnd - sizeChangeStart);
            if (progress < 1.0) {
                return size + (sizeLerpTarget - size) * progress;
            }
            setSize(sizeLerpTarget);
        }
        return size;
    }

    /** The remaining lerp time in ms (0 when stationary). */
    public long getLerpTime() {
        return isShrinking() || isGrowing()
                ? sizeChangeEnd - System.currentTimeMillis() : 0L;
    }

    public double getSizeLerpTarget() {
        return sizeLerpTarget;
    }

    /** The immediate resize (the reference's two-arg setSize). */
    public void setSize(double newSize) {
        this.size = newSize;
        this.sizeLerpTarget = newSize;
        this.sizeChangeEnd = System.currentTimeMillis();
        this.sizeChangeStart = sizeChangeEnd;
        for (var session : players.all()) {
            session.link().worldBorderSize(newSize);
        }
    }

    /** The lerped resize (the reference's four-arg setSize). */
    public void setSize(double fromSize, double targetSize, long timeMs) {
        this.size = fromSize;
        this.sizeLerpTarget = targetSize;
        this.sizeChangeStart = System.currentTimeMillis();
        this.sizeChangeEnd = sizeChangeStart + timeMs;
        for (var session : players.all()) {
            session.link().worldBorderLerp(fromSize, targetSize, timeMs);
        }
    }

    // --- the damage family (lines 173-195) ---------------------------------------

    public double getSafeZone() {
        return safeZone;
    }

    /**
     * The safe-zone buffer. No wire broadcast: the reference's listener arms
     * for the damage family are EMPTY (the packet enum carries no type for
     * them — the client learns the buffer only through the join INITIALIZE).
     */
    public void setSafeZone(double zone) {
        this.safeZone = zone;
    }

    public double getDamagePerBlock() {
        return damagePerBlock;
    }

    /** No wire broadcast (the reference's own empty listener arm). */
    public void setDamagePerBlock(double damage) {
        this.damagePerBlock = damage;
    }

    // --- the warning pair (lines 201-223) ----------------------------------------

    public int getWarningTime() {
        return warningTime;
    }

    public void setWarningTime(int seconds) {
        this.warningTime = seconds;
        for (var session : players.all()) {
            session.link().worldBorderWarningTime(seconds);
        }
    }

    public int getWarningDistance() {
        return warningDistance;
    }

    public void setWarningDistance(int blocks) {
        this.warningDistance = blocks;
        for (var session : players.all()) {
            session.link().worldBorderWarningBlocks(blocks);
        }
    }

    public int maxSize() {
        return maxSize;
    }
}
