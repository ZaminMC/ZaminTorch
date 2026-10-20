package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The end portal family (Slice 54 — EndPortalBlock/EnderEyeItem 1:1): the
 * block 119 ({@code new EndPortalBlock(Material.PORTAL).setStrength(-1.0F)
 * .setBlastResistance(6000000.0F)} — unbreakable, no drops, no pick item)
 * and the frame-complete fill walk ({@code EnderEyeItem.useOn}'s own
 * geometry): the clicked frame's FACING into the interior, the 3-row scan
 * along the clockwise perpendicular (k=-2..2, the first and last eyed
 * frames must sit exactly two apart), the far row at FACING × 4, the two
 * side columns at the row's ends (each FACING × 1..3) — every one an eyed
 * frame — then the 3×3 interior fills with the portal block.
 *
 * <p>The portal block itself: the 1/16-tall film (updateShape's 0.0625),
 * walk-through ({@code addCollisions} empty), CUTOUT render with the
 * {@code setLight(1.0F)} emission 15, and the contact arm
 * ({@code onEntityCollision} → {@code changeDimension(1)}) that rides the
 * End dimension's own slice.</p>
 */
public final class EndPortalBlocks {

    private EndPortalBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 119;

    /** The portal block (the single meta-0 state). */
    public static final Identifier END_PORTAL = Identifier.parse("minecraft:end_portal");

    /** The engine's end portal type (the single state). */
    public static EngineBlockType endPortal() {
        return new EngineBlockType(END_PORTAL, "End Portal");
    }

    /** @return whether the identifier is the end portal. */
    public static boolean isEndPortal(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().equals("end_portal");
    }

    // ------------------------------------------------------------------
    // The frame reads
    // ------------------------------------------------------------------

    /** The frame's four facing states (the engine's flattened names). */
    private static final List<String> FRAME_FACINGS = List.of(
            "south", "west", "north", "east");

    /** @return the frame's facing id (0 S, 1 W, 2 N, 3 E), or -1. */
    public static int frameFacingOf(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        if (!value.startsWith("end_portal_frame")) {
            return -1;
        }
        boolean eye = value.startsWith("end_portal_frame_eye_");
        String suffix = value.substring(eye
                ? "end_portal_frame_eye_".length() : "end_portal_frame_".length());
        int facing = FRAME_FACINGS.indexOf(suffix);
        return facing >= 0 ? facing : -1;
    }

    /** @return whether the identifier is an EMPTY frame (no eye). */
    public static boolean isEmptyFrame(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().startsWith("end_portal_frame_")
                && !id.value().startsWith("end_portal_frame_eye_")
                && frameFacingOf(id) >= 0;
    }

    /** @return whether the identifier is an EYED frame. */
    public static boolean isEyedFrame(Identifier id) {
        return id != null && id.namespace().equals("minecraft")
                && id.value().startsWith("end_portal_frame_eye_")
                && frameFacingOf(id) >= 0;
    }

    /** The eyed state of an empty frame (the eye insertion's own write). */
    public static Optional<Identifier> eyedFrameOf(Identifier id) {
        if (!isEmptyFrame(id)) {
            return Optional.empty();
        }
        return Optional.of(Identifier.parse(
                "minecraft:end_portal_frame_eye_" + FRAME_FACINGS.get(frameFacingOf(id))));
    }

    // ------------------------------------------------------------------
    // The fill walk
    // ------------------------------------------------------------------

    /** The world surface the walk needs (tick-thread context). */
    public interface FrameWorld {
        net.zaminmc.torch.block.BlockType getBlock(net.zaminmc.torch.block.BlockPosition at);

        void setBlock(net.zaminmc.torch.block.BlockPosition at,
                net.zaminmc.torch.block.BlockType type);
    }

    /** The four horizontal facings' offsets (S=+Z, W=-X, N=-Z, E=+X). */
    private static final int[][] FACING_OFFSETS = {
            {0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};

    /** clockwiseY of a facing id (S->W, W->N, N->E, E->S). */
    private static int clockwise(int facing) {
        return (facing + 1) % 4;
    }

    /**
     * The frame-complete fill walk (EnderEyeItem.useOn lines 75-104, 1:1):
     * called after the eye's insertion into {@code pos}. The clicked
     * frame's FACING points into the interior; the 3-row scan runs along
     * the clockwise perpendicular (k=-2..2 — every frame cell must hold an
     * EYED frame; the first and last must sit exactly two apart), the far
     * row at FACING × 4 over the same band, and the two side columns at
     * the row's ends (each FACING × 1..3) — all eyed frames or the walk
     * gives up. On completion the 3×3 interior (the perpendicular band ×
     * FACING 1..3) fills with the end portal block.
     *
     * @return whether the portal was filled.
     */
    public static boolean tryFillPortal(FrameWorld world,
            net.zaminmc.torch.block.BlockPosition pos, int frameFacing) {
        int[] direction = FACING_OFFSETS[frameFacing];
        int[] perpendicular = FACING_OFFSETS[clockwise(frameFacing)];
        int first = 0;
        int last = 0;
        boolean foundAny = false;
        boolean complete = true;
        for (int k = -2; k <= 2; k++) {
            net.zaminmc.torch.block.BlockPosition at = offset(pos, perpendicular, k);
            net.zaminmc.torch.block.BlockType state = world.getBlock(at);
            if (isFrame(state.identifier())) {
                if (!isEyedFrame(state.identifier())) {
                    complete = false;
                    break;
                }
                last = k;
                if (!foundAny) {
                    first = k;
                    foundAny = true;
                }
            }
        }
        if (!complete || !foundAny || last != first + 2) {
            return false;
        }
        // The far row at FACING x 4 over the same band.
        net.zaminmc.torch.block.BlockPosition far = offset(pos, direction, 4);
        for (int k = first; k <= last; k++) {
            net.zaminmc.torch.block.BlockPosition at = offset(far, perpendicular, k);
            net.zaminmc.torch.block.BlockType state = world.getBlock(at);
            if (!isEyedFrame(state.identifier())) {
                return false;
            }
        }
        // The two side columns at the row's ends (l-1 and j+1), each
        // FACING x 1..3.
        for (int side = first - 1; side <= last + 1; side += 4) {
            net.zaminmc.torch.block.BlockPosition corner = offset(pos, perpendicular, side);
            for (int depth = 1; depth <= 3; depth++) {
                net.zaminmc.torch.block.BlockPosition at = offset(corner, direction, depth);
                net.zaminmc.torch.block.BlockType state = world.getBlock(at);
                if (!isEyedFrame(state.identifier())) {
                    return false;
                }
            }
        }
        // The 3x3 interior fills with the portal block.
        net.zaminmc.torch.block.BlockType portal = endPortal();
        for (int k = first; k <= last; k++) {
            net.zaminmc.torch.block.BlockPosition row = offset(pos, perpendicular, k);
            for (int depth = 1; depth <= 3; depth++) {
                world.setBlock(offset(row, direction, depth), portal);
            }
        }
        return true;
    }

    /** @return whether the identifier is any frame state (empty or eyed). */
    public static boolean isFrame(Identifier id) {
        return isEmptyFrame(id) || isEyedFrame(id);
    }

    private static net.zaminmc.torch.block.BlockPosition offset(
            net.zaminmc.torch.block.BlockPosition at, int[] direction, int units) {
        return at.offset(direction[0] * units, 0, direction[2] * units);
    }
}
