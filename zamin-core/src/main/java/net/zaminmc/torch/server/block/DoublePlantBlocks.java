package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The double-plant family (Slice 45 — DoublePlantBlock 1:1): the six
 * two-cell plants 175 ({@code setStrength(0.0F).setSounds(GRASS_SOUNDS)}
 * over Material.REPLACEABLE_PLANT — the sunflower 0, the lilac 1 (the
 * reference's own "syringa" key), the double tall grass 2, the large fern
 * 3, the rose 4, the peony 5).
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata}/{@code getMetadataFromState} band: the
 * LOWER half carries the VARIANT id whole (0-5); the UPPER half carries
 * {@code 8 | FACING.getIdHorizontal()} — the reference's own default
 * NORTH write (horizontal id 2), so every placed upper reads meta 10 on
 * the wire. The upper's variant is virtual in the reference
 * (resolveVirtualProperties reads it from the lower); the engine's
 * flattened family carries it in the identifier
 * ({@code <variant>_upper}).</p>
 */
public final class DoublePlantBlocks {

    private DoublePlantBlocks() {
    }

    /** The wire id (Block.java's own row {@code register(175, "double_plant", ...)}). */
    public static final int WIRE_ID = 175;

    /**
     * The VARIANT band (DoublePlantBlock.Variant's ids 0-5 in the
     * reference's declaration order — the reference's own keys
     * sunflower/syringa/double_grass/double_fern/double_rose/paeonia over
     * the engine's flat names).
     */
    static final List<String> VARIANTS = List.of(
            "sunflower", "lilac", "double_grass", "double_fern",
            "rose", "peony");

    /** The display names (the reference's own getName strings). */
    static final List<String> DISPLAY = List.of(
            "Sunflower", "Lilac", "Double Tallgrass", "Large Fern",
            "Rose", "Peony");

    /** The family item (Item.java's own BlockItem row: the double plant 175). */
    public static final Identifier FAMILY_ITEM = Identifier.parse("minecraft:double_plant");

    /** The upper's wire metadata (8 | NORTH's horizontal id 2 — the reference's default write). */
    public static final int UPPER_META = 8 | 2;

    /**
     * The plantable soils (PlantBlock.canBePlacedOn lines 38-41 — the
     * double plant inherits the grass/dirt/farmland band).
     */
    static final List<Identifier> SOILS = List.of(
            Identifier.parse("minecraft:grass_block"),
            Identifier.parse("minecraft:dirt"),
            Identifier.parse("minecraft:farmland"),
            Identifier.parse("minecraft:farmland_wet"));

    /** One LOWER state's identifier. */
    public static Identifier lowerId(int variant) {
        return Identifier.parse("minecraft:" + VARIANTS.get(variant));
    }

    /** One UPPER state's identifier. */
    public static Identifier upperId(int variant) {
        return Identifier.parse("minecraft:" + VARIANTS.get(variant) + "_upper");
    }

    /** One LOWER state (the VARIANT id whole in the metadata nibble). */
    public static EngineBlockType lower(int variant) {
        return new EngineBlockType(lowerId(variant), DISPLAY.get(variant), variant);
    }

    /** One UPPER state (8 | the default NORTH horizontal in the nibble). */
    public static EngineBlockType upper(int variant) {
        return new EngineBlockType(upperId(variant), DISPLAY.get(variant), UPPER_META);
    }

    /** Every registration this family owns (the twelve states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int variant = 0; variant < VARIANTS.size(); variant++) {
            blocks.add(lower(variant));
            blocks.add(upper(variant));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the type is one of the family's twelve states. */
    public static boolean isDoublePlant(net.zaminmc.torch.block.BlockType type) {
        return isDoublePlantIdentifier(type == null ? null : type.identifier());
    }

    /** @return whether the identifier is one of the family's twelve states. */
    public static boolean isDoublePlantIdentifier(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String variant : VARIANTS) {
            if (value.equals(variant) || value.equals(variant + "_upper")) {
                return true;
            }
        }
        return false;
    }

    /** @return whether the identifier is one of the six UPPER states. */
    public static boolean isUpper(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String variant : VARIANTS) {
            if (value.equals(variant + "_upper")) {
                return true;
            }
        }
        return false;
    }

    /** @return the state's VARIANT id (0-5), or -1 for a non-member. */
    public static int variantOf(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        for (int i = 0; i < VARIANTS.size(); i++) {
            String variant = VARIANTS.get(i);
            if (value.equals(variant) || value.equals(variant + "_upper")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The placement consult (the family item's damage domain over the
     * double_plant 175): the stack's damage field resolves the LOWER of
     * that variant. Out-of-band damage clamps to 0 exactly like the
     * reference's {@code DoublePlantBlock.Variant.byId} (the sunflower).
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (item.namespace().equals("minecraft") && item.value().equals("double_plant")) {
            int variant = damage < 0 || damage >= VARIANTS.size() ? 0 : damage;
            return Optional.of(lowerId(variant));
        }
        return Optional.empty();
    }

    /**
     * The headroom read (DoublePlantBlock.canBePlaced lines 75-78): the
     * cell above the lower must be air for the pair to place.
     */
    public static boolean hasHeadroom(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        return world.getBlock(pos.offset(0, 1, 0)).equals(world.airType());
    }

    /**
     * The soil read (PlantBlock.canBePlacedOn lines 38-41 — the grass,
     * the dirt or the farmland under the lower).
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

    /**
     * The LOWER's survival read (DoublePlantBlock.canSurvive lines 96-102):
     * the cell above must be the family's own UPPER and the soil must
     * carry — both cells of the pair validate together.
     */
    public static boolean lowerSurvives(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        if (!isSupported(world, pos)) {
            return false;
        }
        return isUpper(world.getBlock(pos.offset(0, 1, 0)).identifier());
    }

    /**
     * The LOWER's drop walk (DoublePlantBlock.getDropItem lines 80-95 +
     * getDropItemMetadata lines 97-101): the flower variants yield the
     * family item with the VARIANT damage; the double grass rolls the 1/8
     * seed walk; the large fern and every UPPER yield nothing.
     */
    public static net.zaminmc.torch.item.ItemStack lowerDrop(
            net.zaminmc.torch.block.BlockType lowerType, java.util.Random random) {
        int variant = variantOf(lowerType.identifier());
        return switch (variant) {
            case 0, 1, 4, 5 -> new net.zaminmc.torch.item.ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.DOUBLE_PLANT, 1, variant);
            // DoublePlantBlock.getDropItem lines 86-89: the grass's 1/8 seed
            // roll (getDropItemMetadata's GRASS -> 0 consult rides the
            // variant band's own row).
            case 2 -> random.nextInt(8) == 0
                    ? net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.WHEAT_SEEDS, 1)
                    : null;
            default -> null; // the large fern (3) and the uppers: nothing
        };
    }
}
