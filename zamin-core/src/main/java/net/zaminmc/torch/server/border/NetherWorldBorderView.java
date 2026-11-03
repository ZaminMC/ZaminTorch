package net.zaminmc.torch.server.border;

import net.zaminmc.torch.server.player.PlayerRegistry;

/**
 * The nether's border (NetherDimension.createWorldBorder lines 75-86, 1:1):
 * the overworld's synced rows with the CENTER REPORTED AT /8. The reference's
 * own anonymous-subclass shape — every containment read
 * (getMinX/getMinZ/getMaxX/getMaxZ, the contains pair, getDistanceFrom)
 * flows through the overridden center, so the nether's containment geometry
 * is the overworld's size wrapped around the projected center.
 *
 * <p>The synced rows (ReadOnlyServerWorld lines 17-37): the reference's
 * nether border is its OWN instance whose every row the overworld's listener
 * copies (size, lerp, center, warnings, damage). The engine's single shared
 * border plays the reference's {@code worlds[0]} rows; this view plays the
 * nether's own instance — the live delegation over the shared object IS the
 * listener copy, always in sync by construction. The End carries no
 * projection (TheEndDimension has no createWorldBorder override — the raw
 * synced copy), so the End and the overworld read the shared object
 * directly.</p>
 *
 * <p>Read-only by contract: the {@code /worldborder} command and the wire
 * both target {@code worlds[0]}'s border ALONE (WorldBorderCommand lines
 * 171-172, PlayerManager.sendWorldInfo lines 678-679), so no write ever
 * flows through this view — the mutating setters guard against it.</p>
 */
public final class NetherWorldBorderView extends EngineWorldBorder {

    /** The nether's center projection: the overworld's center reported at /8. */
    private static final double CENTER_PROJECTION = 8.0;

    private final EngineWorldBorder shared;

    public NetherWorldBorderView(EngineWorldBorder shared, PlayerRegistry players) {
        super(players); // never a write surface: the mutators below guard
        this.shared = shared;
    }

    // --- the reference's own overrides (createWorldBorder lines 75-86) --------

    @Override
    public double getCenterX() {
        return shared.getCenterX() / CENTER_PROJECTION;
    }

    @Override
    public double getCenterZ() {
        return shared.getCenterZ() / CENTER_PROJECTION;
    }

    // --- the synced rows (the listener's copy, live-delegated) ----------------

    @Override
    public double getLerpSize() {
        return shared.getLerpSize();
    }

    @Override
    public double getSizeLerpTarget() {
        return shared.getSizeLerpTarget();
    }

    @Override
    public boolean isShrinking() {
        return shared.isShrinking();
    }

    @Override
    public boolean isGrowing() {
        return shared.isGrowing();
    }

    @Override
    public long getLerpTime() {
        return shared.getLerpTime();
    }

    @Override
    public double getSafeZone() {
        return shared.getSafeZone();
    }

    @Override
    public double getDamagePerBlock() {
        return shared.getDamagePerBlock();
    }

    @Override
    public int getWarningTime() {
        return shared.getWarningTime();
    }

    @Override
    public int getWarningDistance() {
        return shared.getWarningDistance();
    }

    @Override
    public int maxSize() {
        return shared.maxSize();
    }

    // --- the write guard: worlds[0]'s border alone owns the mutations ---------

    @Override
    public void setCenter(double x, double z) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the /worldborder "
                        + "command targets worlds[0] alone (WorldBorderCommand "
                        + "lines 171-172)");
    }

    @Override
    public void setSize(double newSize) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the /worldborder "
                        + "command targets worlds[0] alone (WorldBorderCommand "
                        + "lines 171-172)");
    }

    @Override
    public void setSize(double fromSize, double targetSize, long timeMs) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the /worldborder "
                        + "command targets worlds[0] alone (WorldBorderCommand "
                        + "lines 171-172)");
    }

    @Override
    public void setSafeZone(double zone) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the damage rows "
                        + "copy from worlds[0] alone (ReadOnlyServerWorld "
                        + "lines 17-37)");
    }

    @Override
    public void setDamagePerBlock(double damage) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the damage rows "
                        + "copy from worlds[0] alone (ReadOnlyServerWorld "
                        + "lines 17-37)");
    }

    @Override
    public void setWarningTime(int seconds) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the warning rows "
                        + "copy from worlds[0] alone (ReadOnlyServerWorld "
                        + "lines 17-37)");
    }

    @Override
    public void setWarningDistance(int blocks) {
        throw new UnsupportedOperationException(
                "the nether's border is a read-only view — the warning rows "
                        + "copy from worlds[0] alone (ReadOnlyServerWorld "
                        + "lines 17-37)");
    }
}
