package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.ChunkPosition;

/**
 * The state view every light computation runs over (§6.5: parallel
 * computation's inputs are explicit; §6.4: no live reference crosses the
 * owner casually).
 *
 * <p>THE REASON THIS SEAM EXISTS: the initial per-chunk compute's offload
 * gate (the §22 three-part bar) needs the SAME algorithm to run twice —
 * once over the live routing (the unpublished chunk plus the world, on the
 * owning lane) and once over an immutable captured region (off-lane, the
 * random-tick picks' own compute-then-apply shape). The algorithm is
 * specified ONCE, in {@code LightEngine}'s static computations; this
 * interface is the only thing that differs between the two runs, so the
 * behavioral equivalence holds by construction and is proven empirically by
 * the gate's own test.</p>
 *
 * <p>The algorithm never consults block IDENTITY — only the two numbers of
 * {@code BlockLightTable}'s dataset (the emission a source radiates, the
 * filter a ray entering the cell pays) — so the seam exposes exactly those
 * two ints. A detached run serves them from packed nibbles; the live run
 * looks them up through the block table.</p>
 *
 * <p>Semantics every implementation must mirror exactly (the live routing is
 * the specification): filter/emission answer 0 for unloaded space; block
 * light reads answer {@link LightSection#DEFAULT_BLOCK} and sky reads
 * {@link LightSection#DEFAULT_SKY} for absent/unloaded storage; writes into
 * unloaded space are dropped; writes that land in a loaded neighbor column
 * mark that column relight, while writes inside the fresh chunk's own
 * bounds never mark it (its first packet carries its light).</p>
 */
interface LightAccess {

    /** The block's light filter at the position; 0 for unloaded space. */
    int filterAt(int x, int y, int z);

    /** The block's light emission at the position; 0 for unloaded space. */
    int emissionAt(int x, int y, int z);

    /** The stored block light; {@link LightSection#DEFAULT_BLOCK} for absent/unloaded storage. */
    int blockLightAt(int x, int y, int z);

    /** The stored sky light; {@link LightSection#DEFAULT_SKY} for absent/unloaded storage. */
    int skyLightAt(int x, int y, int z);

    /** Stores block light. Droppable (unloaded space); must mark the column relight when it lands in a loaded neighbor. */
    void setBlockLight(int x, int y, int z, int level);

    /** Stores sky light. Droppable (unloaded space); must mark the column relight when it lands in a loaded neighbor. */
    void setSkyLight(int x, int y, int z, int level);

    /**
     * Marks the column's chunk for relight, excluding the fresh chunk's own
     * bounds (its first packet carries its light). Live: the relight queue;
     * a detached run: the result's ordered column list.
     */
    void markRelight(int x, int z);

    /** Clears the fresh chunk's light storage — the initial compute's step zero. */
    void clearFreshLight();

    /** The fresh chunk's highest light-relevant Y (the compute's working span). */
    int highestRelevantY();

    /** The fresh chunk's base X (its min block X). */
    int freshBaseX();

    /** The fresh chunk's base Z (its min block Z). */
    int freshBaseZ();

    /**
     * The already-loaded side neighbor at the given chunk offset, or null
     * when none is loaded (peek, never generate — the re-entrancy guard).
     */
    ChunkPosition neighborAt(int dx, int dz);
}
