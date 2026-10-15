package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The cobblestone wall family (Slice 47 — WallBlock 1:1): the wall 139
 * over the cobblestone's own strength ({@code new WallBlock(block)} with
 * {@code setStrength(block.miningTime)} — the 2.0 hardness and the
 * stone sounds; {@code setBlastResistance(block.blastResistance / 3.0F)}).
 *
 * <p>The wire state is the reference's own band: the VARIANT id whole in
 * the metadata nibble (getStateFromMetadata lines 133-136) — the NORMAL
 * cobblestone form 0 and the MOSSY form 1. The UP/N/E/S/W connections are
 * VIRTUAL properties in the reference (resolveVirtualProperties lines
 * 141-147 — computed from the neighbors, never stored): the wire never
 * carries them, the client renders its own connections from the
 * neighborhood exactly like the reference.</p>
 */
public final class WallBlocks {

    private WallBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 139;

    /**
     * The VARIANT band (WallBlock.Variant's ids — the reference's own keys
     * cobblestone/mossy_cobblestone over the engine's flat names).
     */
    static final List<String> VARIANTS = List.of(
            "cobblestone_wall", "mossy_cobblestone_wall");

    /** The display names (the reference's own normal/mossy names). */
    static final List<String> DISPLAY = List.of(
            "Cobblestone Wall", "Mossy Cobblestone Wall");

    /** The family item (Item.java's own VariantBlockItem row over 139). */
    public static final Identifier FAMILY_ITEM = Identifier.parse("minecraft:cobblestone_wall");

    /** One state's identifier. */
    static Identifier stateId(int variant) {
        return Identifier.parse("minecraft:" + VARIANTS.get(variant));
    }

    /** One state (the metadata band's exact member — the VARIANT id whole). */
    public static EngineBlockType state(int variant) {
        return new EngineBlockType(stateId(variant), DISPLAY.get(variant), variant);
    }

    /** Every registration this family owns (the two states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int variant = 0; variant < VARIANTS.size(); variant++) {
            blocks.add(state(variant));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the two wall states. */
    public static boolean isWall(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String variant : VARIANTS) {
            if (value.equals(variant)) {
                return true;
            }
        }
        return false;
    }

    /** @return the state's VARIANT id (0 cobble, 1 mossy), or -1. */
    public static int variantOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (int i = 0; i < VARIANTS.size(); i++) {
            if (value.equals(VARIANTS.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The placement consult (the family item's damage domain over the
     * VariantBlockItem): the stack's damage field resolves the variant's
     * block. Out-of-band damage clamps to the NORMAL form exactly like the
     * reference's {@code WallBlock.Variant.byId}.
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (item.namespace().equals("minecraft") && item.value().equals("cobblestone_wall")) {
            int variant = damage < 0 || damage >= VARIANTS.size() ? 0 : damage;
            return Optional.of(stateId(variant));
        }
        return Optional.empty();
    }
}
