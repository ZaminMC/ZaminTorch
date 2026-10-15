package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The cake family (Slice 46 — CakeBlock 1:1): the block 92 (Material.CAKE,
 * the seven BITES states 0-6 — Block.java's own row
 * {@code register(92, "cake", new CakeBlock().setKey("cake"))}).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the BITES id whole in the metadata
 * nibble. The bare form ({@code minecraft:cake}) IS the bites-0 state —
 * the placed block the {@code minecraft:cake} item (id 354, stack 1) and
 * the crafting result write.</p>
 */
public final class CakeBlocks {

    private CakeBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 92;

    /**
     * The BITES band (CakeBlock.BITES 0-6 — the seven states; the whole
     * cake is bites 0, the last visible crumb bites 6).
     */
    public static final int MAX_BITES = 6;

    /** One state's identifier: the bites-0 form keeps the plain name. */
    public static Identifier stateId(int bites) {
        return bites == 0
                ? Identifier.parse("minecraft:cake")
                : Identifier.parse("minecraft:cake_bite" + bites);
    }

    /** One state (the metadata band's exact member — the BITES id whole). */
    public static EngineBlockType state(int bites) {
        return new EngineBlockType(stateId(bites), "Cake", bites);
    }

    /** Every registration this family owns (the seven states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int bites = 0; bites <= MAX_BITES; bites++) {
            blocks.add(state(bites));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the seven cake states. */
    public static boolean isCake(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        if (value.equals("cake")) {
            return true;
        }
        for (int bites = 1; bites <= MAX_BITES; bites++) {
            if (value.equals("cake_bite" + bites)) {
                return true;
            }
        }
        return false;
    }

    /** @return the state's BITES id (0-6), or -1 for a non-member. */
    public static int bitesOf(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        if (value.equals("cake")) {
            return 0;
        }
        for (int bites = 1; bites <= MAX_BITES; bites++) {
            if (value.equals("cake_bite" + bites)) {
                return bites;
            }
        }
        return -1;
    }

    /**
     * The next bite's state (the eat's own write — bites + 1, or air at
     * the seventh: CakeBlock.eat lines 75-84).
     */
    public static Identifier afterBite(int bites) {
        return bites + 1 <= MAX_BITES ? stateId(bites + 1) : null;
    }

    /**
     * The support read (CakeBlock.canSurvive lines 92-94 +
     * neighborChanged lines 88-91): the block below must be SOLID — the
     * reference's own material consult.
     */
    public static boolean isSupported(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        return WorldSolidity.isSolid(world.getBlock(pos.offset(0, -1, 0)));
    }

    /**
     * The analog read (CakeBlock.getAnalogSignal lines 133-135): the whole
     * cake's 14 down to the last crumb's 2 — {@code (7 - bites) * 2}.
     */
    public static int analogSignal(Identifier id) {
        return (7 - bitesOf(id)) * 2;
    }
}
