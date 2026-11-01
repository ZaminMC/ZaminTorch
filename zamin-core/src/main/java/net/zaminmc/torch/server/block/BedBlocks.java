package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The bed family's FACING law (VT-010 — BedBlock 1:1): the engine's eight
 * bed ids ARE the reference's metadata space — `BuiltinBlocks` registers
 * the feet at metas 0-3 and the heads at 8-11 with the facing in the
 * suffix (`bed`/`bed_west`/`bed_north`/`bed_east` and the `bed_head`
 * forms; the suffix-less ids are the SOUTH variants, vanilla metas 0 and
 * 8), the anvil import's 26-rows and the wire's `metadataOf` carry the
 * same numbers — so every registered bed half answers a band, and the
 * suffix always names the pair's own geometry.
 *
 * <p>The reference never scans for a pair sibling: every walk reads the
 * half's OWN facing property — the foot's head at
 * `pos.offset(state.get(FACING))` (the `use` redirect, lines 38-41), the
 * head's foot at the facing-OPPOSITE (`neighborChanged` lines 109-121,
 * `beforeMinedByPlayer` lines 190-197) — with the block-CLASS membership
 * test (`getBlock() != this`: any bed half passes, never a part-specific
 * search). Two adjacent bed pairs are freely placeable in vanilla; a
 * foreign half at a scan-won cell must never win the consult.</p>
 */
public final class BedBlocks {

    private BedBlocks() {
    }

    /**
     * The four look bands' horizontal offsets (the placement's own
     * S/W/N/E law, `Direction` SOUTH/WEST/NORTH/EAST's normal vectors:
     * S=+z, W=-x, N=-z, E=+x), indexed by `Direction.getIdHorizontal`.
     */
    private static final int[][] FACING_OFFSETS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    /**
     * The family's block-CLASS membership test (the reference's
     * `getBlock() != this` over Blocks.BED): any bed half, head or foot,
     * in any facing.
     */
    public static boolean isHalf(BlockType type) {
        Identifier id = type.identifier();
        return id.namespace().equals("minecraft")
                && (id.value().equals("bed") || id.value().startsWith("bed_"));
    }

    /** @return true for the four HEAD ids (the `bed_head` prefix). */
    public static boolean isHead(BlockType type) {
        Identifier id = type.identifier();
        return id.namespace().equals("minecraft") && id.value().startsWith("bed_head");
    }

    /**
     * The facing band a bed half's OWN identifier names (S=0, W=1, N=2,
     * E=3 — `Direction.getIdHorizontal`'s law, the reference's enum lines
     * 14-17). Answers -1 for any id outside the eight (unreachable
     * through placement, import, or /setblock — the family is total).
     */
    private static int facingBand(BlockType type) {
        String id = type.identifier().value();
        if (!type.identifier().namespace().equals("minecraft")) {
            return -1;
        }
        if (id.equals("bed") || id.equals("bed_head")) {
            return 0; // south — the suffix-less variants (vanilla metas 0 and 8)
        }
        if (id.equals("bed_west") || id.equals("bed_head_west")) {
            return 1; // west
        }
        if (id.equals("bed_north") || id.equals("bed_head_north")) {
            return 2; // north
        }
        if (id.equals("bed_east") || id.equals("bed_head_east")) {
            return 3; // east
        }
        return -1;
    }

    /**
     * The cell the reference's FACING law names for a bed half's PAIR
     * SIBLING: a foot's HEAD cell (`BedBlock.use` lines 38-41 — the
     * redirect: `pos.offset(state.get(FACING))`), a head's FOOT cell (the
     * `use` else arm lines 71-75 and `beforeMinedByPlayer` lines
     * 190-197 — `pos.offset(state.get(FACING).getOpposite())`).
     *
     * @return the sibling cell, or null for an id outside the family
     *         (nothing to consult)
     */
    public static BlockPosition facingPartner(BlockPosition pos, BlockType type) {
        int band = facingBand(type);
        if (band < 0) {
            return null;
        }
        int[] offset = FACING_OFFSETS[band];
        // The head's partner sits at the facing-OPPOSITE cell; the
        // foot's at the facing cell.
        return isHead(type)
                ? pos.offset(-offset[0], 0, -offset[1])
                : pos.offset(offset[0], 0, offset[1]);
    }

    /**
     * The cell BEHIND a bed half — the facing-OPPOSITE cell whatever the
     * half's class: the `use` else arm's own removal law (lines 72-76
     * read the ANCHOR's state — `pos.offset(state.get(FACING).
     * getOpposite())` — from whatever half the redirect landed on).
     *
     * @return the backward cell, or null for an id outside the family
     */
    public static BlockPosition facingBackward(BlockPosition pos, BlockType type) {
        int band = facingBand(type);
        if (band < 0) {
            return null;
        }
        int[] offset = FACING_OFFSETS[band];
        return pos.offset(-offset[0], 0, -offset[1]);
    }
}
