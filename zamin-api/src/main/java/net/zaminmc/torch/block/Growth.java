package net.zaminmc.torch.block;

import net.zaminmc.torch.World;

/**
 * The growth-family outcome reads (§27.3's block-behavior family — the
 * VIII2/VIII7/VIII8 exposure): the stage/age consults a plugin needs to
 * answer "what will grow here?" — the crops' and stems' stage bands, the
 * sapling family's variant + readiness + the light gate the random tick
 * rolls under, the cocoa pod's age band, and the mushroom plant's
 * survival law — reached through {@link World#growth()}.
 *
 * <p>The engine behind it is the bonemeal/growth family the recent
 * waves landed: {@code RandomTickSystem}'s stage tables (the wheat/
 * carrot/potato/pumpkin/melon bands), {@code SaplingBlocks}'s two-stage
 * law (VIII2 — the light >= 9 gate at the cell above, the 1-in-7 roll,
 * the ready bit), {@code CocoaBlocks}'s AGE &lt; 2 band (VIII7 — the
 * mature pod's use never consumes the stack), and {@code
 * MushroomBlocks}'s survival law (VIII8 — the y band, the mycelium
 * bypass at any light, the light-under-13-and-opaque-below form). The
 * queries answer the same consults the bonemeal arms themselves run —
 * a plugin can predict the arms' outcomes (which use pays the stack,
 * which grow lands) without touching the engine's mutation arms.</p>
 *
 * <p><b>Threading (§27.6):</b> the type consults are pure
 * (deterministic over the {@link BlockType} identity) — safe from any
 * thread. The position consults read the live world's light and block
 * state through the documented best-effort read family (the same
 * consistency {@link World#blockLightAt} carries) — an off-lane answer
 * reflects some instant between ticks.</p>
 */
public interface Growth {

    /** The empty world's answer (every consult refuses). */
    Growth EMPTY = new Growth() {
        @Override public int cropStage(BlockType type) {
            return -1;
        }

        @Override public int saplingVariant(BlockType type) {
            return -1;
        }

        @Override public boolean saplingReady(BlockType type) {
            return false;
        }

        @Override public boolean saplingLightGatePasses(BlockPosition position) {
            return false;
        }

        @Override public int cocoaAge(BlockType type) {
            return -1;
        }

        @Override public boolean mushroomPlant(BlockType type) {
            return false;
        }

        @Override public boolean mushroomSurvivesAt(BlockPosition position) {
            return false;
        }
    };

    /**
     * The crop/stem stage consult (the engine's own stage tables — the
     * wheat, the carrots, the potatoes, the pumpkin and melon stems).
     *
     * @param type the block type to read
     * @return the growth stage 0..7, or -1 when the type is no crop or
     *         stem (the bonemeal arm's own family gate)
     */
    int cropStage(BlockType type);

    /**
     * The sapling family's variant consult (the engine's own table —
     * the oak, the spruce, the birch, the jungle, the acacia, the dark
     * oak).
     *
     * @param type the block type to read
     * @return the wood variant 0..5, or -1 when the type is no sapling
     */
    int saplingVariant(BlockType type);

    /**
     * The two-stage law's readiness read (VIII2: a ready sapling rolls
     * its tree on the next grow — the fresh form flips its stage bit
     * first).
     *
     * @param type the block type to read
     * @return whether the type is a sapling's READY (stage-1) form
     */
    boolean saplingReady(BlockType type);

    /**
     * The sapling's light gate (the random tick's own law: the brighter
     * of block and sky light at the cell ABOVE reads at least 9 — a
     * sapling under a lid never grows, exactly the gate the bonemeal
     * arm's canGrow-adjacent roll rides around).
     *
     * @param position the sapling's own cell
     * @return whether the gate passes for the cell
     */
    boolean saplingLightGatePasses(BlockPosition position);

    /**
     * The cocoa pod's age band (VIII7: the pod grows only under age 2 —
     * the mature pod's bonemeal use never consumes the stack).
     *
     * @param type the block type to read
     * @return the pod's age 0..2, or -1 when the type is no cocoa pod
     */
    int cocoaAge(BlockType type);

    /**
     * The mushroom-plant family consult (the brown and red plants — the
     * block forms the spread and the huge-growth arms roll over).
     *
     * @param type the block type to read
     * @return whether the type is a planted mushroom (brown or red)
     */
    boolean mushroomPlant(BlockType type);

    /**
     * The mushroom's survival law at a cell (VIII8 — the reference's
     * own {@code canSurvive}): the y band, the MYCELIUM bypass at any
     * light (the mushroom island's own law), else the combined light
     * under 13 with an opaque cell below.
     *
     * @param position the plant's own cell
     * @return whether a mushroom survives at the cell
     */
    boolean mushroomSurvivesAt(BlockPosition position);
}
