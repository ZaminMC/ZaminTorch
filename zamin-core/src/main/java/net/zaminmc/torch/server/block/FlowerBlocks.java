package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The flower family (Slice 44 — FlowerBlock 1:1): the red flower 38's nine
 * types ({@code setStrength(0.0F).setSounds(GRASS_SOUNDS)} over
 * Material.PLANT — FlowerBlock lines 17-28's group model: the YELLOW group
 * is the dandelion 37's own family, the RED group is the poppy 0 + the
 * eight variants below it in the TYPE band).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the TYPE id whole in the metadata
 * nibble (FlowerBlock.getMetadataFromState: {@code state.get(TYPE).getId()}
 * — the damage domain of the single {@code red_flower} item carries the
 * same id, the poppy being the damage-0 form). The poppy keeps its shipped
 * plain block/item rows ({@code minecraft:poppy}); the eight new variants
 * are their own flattened types sharing the wire id 38 with the TYPE id in
 * the nibble.</p>
 */
public final class FlowerBlocks {

    private FlowerBlocks() {
    }

    /** The wire id (Block.java's own row {@code register(38, "red_flower", ...)}). */
    public static final int WIRE_ID = 38;

    /**
     * The RED group's TYPE band (FlowerBlock.Type's ids 0-8 in the
     * reference's declaration order).
     */
    static final List<String> TYPES = List.of(
            "poppy", "blue_orchid", "allium", "houstonia",
            "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
            "oxeye_daisy");

    /** The display names (the reference's own getName strings). */
    static final List<String> DISPLAY = List.of(
            "Poppy", "Blue Orchid", "Allium", "Houstonia",
            "Red Tulip", "Orange Tulip", "White Tulip", "Pink Tulip",
            "Oxeye Daisy");

    /** The family item (Item.java's own BlockItem row: the red flower 38). */
    public static final Identifier FAMILY_ITEM = Identifier.parse("minecraft:red_flower");

    /**
     * The plantable soils (PlantBlock.canBePlacedOn lines 38-41): the
     * grass, the dirt, the farmland — every flower's own gate.
     */
    static final List<Identifier> SOILS = List.of(
            Identifier.parse("minecraft:grass_block"),
            Identifier.parse("minecraft:dirt"),
            Identifier.parse("minecraft:farmland"),
            Identifier.parse("minecraft:farmland_wet"));

    /** One state's identifier: the poppy keeps the plain name. */
    static Identifier stateId(int type) {
        return Identifier.parse("minecraft:" + TYPES.get(type));
    }
    /** One state (the metadata band's exact member — the TYPE id whole). */
    public static EngineBlockType state(int type) {
        return new EngineBlockType(stateId(type), DISPLAY.get(type), type);
    }

    /**
     * Every registration this family owns (the eight NEW states — the
     * poppy 0 keeps its shipped plain rows, the melon slice's own model
     * for the already-registered bare form).
     */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int type = 1; type < TYPES.size(); type++) {
            blocks.add(state(type));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the type is one of the nine red-flower states. */
    public static boolean isFlower(net.zaminmc.torch.block.BlockType type) {
        return type != null && isFlowerIdentifier(type.identifier());
    }

    /** @return whether the identifier is one of the nine red-flower states. */
    public static boolean isFlowerIdentifier(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String flower : TYPES) {
            if (value.equals(flower)) {
                return true;
            }
        }
        return false;
    }

    /** @return the type's TYPE id (0-8), or -1 for a non-flower. */
    public static int typeOf(net.zaminmc.torch.block.BlockType type) {
        if (type == null) {
            return -1;
        }
        String value = type.identifier().value();
        for (int i = 0; i < TYPES.size(); i++) {
            if (value.equals(TYPES.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The placement consult (Slice 35's damage-domain model over the family
     * item): {@code minecraft:red_flower} with the stack's damage field
     * resolves the variant's block; the poppy's own item and the dandelion
     * resolve through the plain identifier path. Out-of-band damage clamps
     * to 0 exactly like the reference's {@code FlowerBlock.Type.byId} (an
     * id below 0 or past the band reads the poppy).
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (item.namespace().equals("minecraft") && item.value().equals("red_flower")) {
            int type = damage < 0 || damage >= TYPES.size() ? 0 : damage;
            return Optional.of(stateId(type));
        }
        return Optional.empty();
    }

    /**
     * The support read (PlantBlock.canSurvive + canBePlacedOn lines
     * 38-41/60-63): the soil below must be the grass, the dirt or the
     * farmland — the same gate the placement consult carries.
     */
    public static boolean isSupported(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        net.zaminmc.torch.block.BlockType soil = world.getBlock(pos.offset(0, -1, 0));
        for (Identifier soilId : SOILS) {
            if (soil.identifier().equals(soilId)) {
                return true;
            }
        }
        return false;
    }
}
