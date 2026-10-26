package net.zaminmc.torch.server.entity.ai.pathing;

/**
 * The world slice the path scanner reads — the vanilla {@code WorldView} of
 * the pathing stack, narrowed to the one question the scan asks: the
 * material category of a cell. Out-of-column reads answer AIR (the vanilla
 * world's out-of-bounds behavior; the engine's chunk sections would throw).
 */
public interface PathWorld {

    /** @return the path-material category of the cell. */
    CellMaterial materialAt(int x, int y, int z);

    /**
     * The cell's sky access (World.hasSkyAccess line 413 — the
     * sun-avoidance trim's own read, GroundPathNavigation line 64/70):
     * whether the open sky stands above the cell. The stub worlds
     * answer sky-open (the no-shade default — the trim's own body-open
     * arm returns, nothing cuts).
     */
    default boolean hasSkyAccess(int x, int y, int z) {
        return true;
    }

    /** The bounds-safe read: outside y 0..255 the vanilla world answers air. */
    static CellMaterial materialOrAir(PathWorld world, int x, int y, int z) {
        if (y < 0 || y >= 256) {
            return CellMaterial.AIR;
        }
        return world.materialAt(x, y, z);
    }
}
