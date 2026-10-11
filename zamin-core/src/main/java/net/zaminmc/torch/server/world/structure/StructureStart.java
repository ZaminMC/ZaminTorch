package net.zaminmc.torch.server.world.structure;

import java.util.LinkedList;
import java.util.Random;

/**
 * The structure start (Slice 12a — the reference port of
 * gen/structure/StructureStart.java): the piece list with its union
 * bounds, the vertical re-base (moveBelowSeaLevel — the reference's own
 * spread roll) and the clipped per-chunk walk (postProcess).
 *
 * <p><b>The engine's deterministic contract:</b> the reference caches
 * starts and removes a piece the first time its clipped shell touches a
 * liquid — a chunk-load-order-dependent removal. The engine recomputes
 * the start deterministically per chunk instead (no shared cache, no
 * cross-owner state — the parallel chunk pipeline stays lock-free), so
 * a piece refuses only in the chunks whose own clipped shell is wet and
 * stays dry elsewhere. The ledger records the envelope difference.</p>
 */
public abstract class StructureStart {
    protected final LinkedList<StructurePiece> pieces = new LinkedList<>();
    protected StructureBox bounds;
    private final int chunkX;
    private final int chunkZ;

    protected StructureStart(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public StructureBox getBounds() {
        return this.bounds;
    }

    /** The reference's own valid gate (the village's override carries the piece-count rule). */
    public boolean isValid() {
        return true;
    }

    public LinkedList<StructurePiece> getPieces() {
        return this.pieces;
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    /**
     * The per-chunk write walk (postProcess lines 33-42): every piece the
     * chunk's clip touches writes its own cells.
     */
    public void postProcess(StructureWorld world, Random random, StructureBox clip) {
        for (StructurePiece piece : this.pieces) {
            if (piece.getBounds().intersects(clip)) {
                piece.postProcess(world, random, clip);
            }
        }
    }

    /** The union of every piece's box (findBounds lines 44-50). */
    protected void findBounds() {
        this.bounds = StructureBox.infinite();
        for (StructurePiece piece : this.pieces) {
            this.bounds.union(piece.getBounds());
        }
    }

    /**
     * The vertical re-base (moveBelowSeaLevel lines 91-104): the whole
     * structure moves down so its top lands at {@code seaLevel - amount}
     * minus a spread roll.
     */
    protected void moveBelowSeaLevel(StructureWorld world, Random random, int amount) {
        int top = world.seaLevel() - amount;
        int span = this.bounds.getSpanY() + 1;
        if (span < top) {
            span += random.nextInt(top - span);
        }
        int dy = span - this.bounds.maxY;
        this.bounds.move(0, dy, 0);
        for (StructurePiece piece : this.pieces) {
            piece.move(0, dy, 0);
        }
    }

    /**
     * The band re-base (moveBetweenYCoords lines 106-121, the fortress's
     * own): the whole structure moves so its floor lands inside the
     * {@code [minY, maxY]} band — {@code minY + nextInt(band - span)} when
     * the band is wider than the structure, the floor pinned to
     * {@code minY} otherwise.
     */
    protected void moveBetweenYCoords(Random random, int minY, int maxY) {
        int band = maxY - minY + 1 - this.bounds.getSpanY();
        int target;
        if (band > 1) {
            target = minY + random.nextInt(band);
        } else {
            target = minY;
        }
        int dy = target - this.bounds.minY;
        this.bounds.move(0, dy, 0);
        for (StructurePiece piece : this.pieces) {
            piece.move(0, dy, 0);
        }
    }
}
