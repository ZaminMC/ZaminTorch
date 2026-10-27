package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

/**
 * The tall-grass family's drop identity (the reference's
 * {@code TallPlantBlock} 31/1 + 31/2 beside the block-32 dead bush): which
 * family member a broken block is, and what its shears arm drops.
 *
 * <p>THE LAW (TallPlantBlock lines 63-93 + DeadBushBlock lines 39-51): the
 * bare hand runs {@code getDropItem}'s 1/8 wheat-seeds walk alone (the dead
 * bush's own {@code null} arm yields nothing); the shears arm of
 * {@code afterMinedByPlayer} drops the family's own item with the variant in
 * the damage field — the shared tallgrass item carrying the block's TYPE id
 * (the grass's 1, the fern's 2; the 0-damage shrub is creative-only), the
 * dead bush its own item at damage 0. The engine's flattened
 * {@code minecraft:tall_grass} (wire 31/1), {@code minecraft:fern} (wire
 * 31/2) and {@code minecraft:dead_bush} (wire 32/0) map onto those arms
 * exactly, so the self-drop law lives here and the DropService family arm
 * consults it.</p>
 */
public final class TallGrassFamily {

    private TallGrassFamily() {
    }

    /** The family's shared item (the historical tallgrass item, wire 31). */
    public static final Identifier TALLGRASS_ITEM =
            Identifier.parse("minecraft:tall_grass");

    /** The dead bush's own item (wire 32). */
    public static final Identifier DEADBUSH_ITEM =
            Identifier.parse("minecraft:dead_bush");

    /**
     * @return the family variant of a broken block's identifier —
     * {@code "tall_grass"} (block 31/1), {@code "fern"} (31/2) or
     * {@code "dead_bush"} (32/0) — or null when the block is not a member.
     */
    public static String variantOf(Identifier identifier) {
        if (identifier == null) {
            return null;
        }
        String value = identifier.value();
        if (value.equals("tall_grass")) {
            return "tall_grass";
        }
        if (value.equals("fern")) {
            return "fern";
        }
        if (value.equals("dead_bush")) {
            return "dead_bush";
        }
        return null;
    }

    /**
     * @return the item the shears arm drops for the variant — the shared
     * tallgrass item for the grass and the fern, the dead bush its own
     * (the reference's {@code new ItemStack(Blocks.TALLGRASS, 1, type)} /
     * {@code new ItemStack(Blocks.DEADBUSH, 1, 0)}).
     */
    public static Identifier shearsItemOf(String variant) {
        return variant.equals("dead_bush") ? DEADBUSH_ITEM : TALLGRASS_ITEM;
    }

    /**
     * @return the damage field the shears arm's stack carries — the block's
     * TYPE id (the grass's 1, the fern's 2; the reference's
     * {@code state.get(TYPE).getId()}), the dead bush's plain 0.
     */
    public static int shearsDamageOf(String variant) {
        return switch (variant) {
            case "tall_grass" -> 1; // TallPlantBlock.Type.GRASS id
            case "fern" -> 2;       // TallPlantBlock.Type.FERN id
            default -> 0;           // Blocks.DEADBUSH's metadata 0
        };
    }
}
