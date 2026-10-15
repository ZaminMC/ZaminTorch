package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The wood-variant foundation (the reference's {@code PlanksBlock.Variant}
 * domain — PlanksBlock.java lines 56-61: oak 0, spruce 1, birch 2, jungle 3,
 * acacia 4, dark_oak 5): the six woods' planks, logs, leaves, and saplings
 * over the reference's own split — one vanilla item per family whose damage
 * field carries the variant, expanded on the engine side into flattened
 * per-wood block ids (the dye families' own model).
 *
 * <p>Families and their wire ids (the reference's registrations):</p>
 * <ul>
 *   <li><b>planks</b> (5 — the item {@code minecraft:planks}, damage =
 *       variant 0-5; oak keeps the shipped {@code minecraft:oak_planks}
 *       block, the other five flatten to {@code <wood>_planks});</li>
 *   <li><b>logs</b> (17 oak/spruce/birch/jungle + 162 the Log2 pair — the
 *       item's damage = variant | axis bits; the engine ships the upright
 *       axis-Y form, metadata = variant & 3, the axis band ledgered to the
 *       metadata-direction slice);</li>
 *   <li><b>leaves</b> (18 oak/spruce/birch/jungle + 161 the Leaves2 pair —
 *       the fresh decayable form, metadata = variant & 3, the decay bits
 *       ledgered to the tree-decay slice);</li>
 *   <li><b>saplings</b> (6 — the item {@code minecraft:sapling}, damage =
 *       variant, the growth-stage bit 8 ledgered to the tree-growth slice;
 *       the oak keeps the shipped {@code minecraft:oak_sapling} item).</li>
 * </ul>
 *
 * <p>The drop walks ({@code AbstractLeavesBlock.dropItems} lines 178-203):
 * shears or silk takes the leaf itself; a hand break rolls the sapling at
 * the reference's own chance band — 1/20 (jungle 1/40,
 * {@code LeavesBlock.getSaplingDropChance}), the fortune relief
 * {@code -2<<level} clamped at 10 — then the oak-only apple at 1/200
 * ({@code dropAppleWithChance}). The logs and planks drop themselves
 * (Material.WOOD's own tool-not-required).</p>
 */
public final class WoodVariantBlocks {

    private WoodVariantBlocks() {
    }

    /** The reference's PlanksBlock.Variant order (the metadata domain). */
    public static final List<String> WOODS =
            List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak");

    /** The display names (PlanksBlock.Variant's own capitalization). */
    private static final List<String> DISPLAY = List.of(
            "Oak", "Spruce", "Birch", "Jungle", "Acacia", "Dark Oak");

    // ------------------------------------------------------------- the items

    public static final Identifier PLANKS_ITEM = Identifier.parse("minecraft:planks");
    public static final Identifier LOG_ITEM = Identifier.parse("minecraft:log");
    public static final Identifier LOG2_ITEM = Identifier.parse("minecraft:log2");
    public static final Identifier LEAVES_ITEM = Identifier.parse("minecraft:leaves");
    public static final Identifier LEAVES2_ITEM = Identifier.parse("minecraft:leaves2");
    public static final Identifier SAPLING_ITEM = Identifier.parse("minecraft:sapling");

    /** The damage-domain family items (the placement consult). */
    public static final List<Identifier> FAMILY_ITEMS = List.of(
            PLANKS_ITEM, LOG_ITEM, LOG2_ITEM, LEAVES_ITEM, LEAVES2_ITEM, SAPLING_ITEM);

    // ------------------------------------------------------------- the blocks

    /** The planks block of a wood (oak keeps the shipped plain id). */
    public static Identifier planksBlock(int variant) {
        return variant == 0
                ? Identifier.parse("minecraft:oak_planks")
                : Identifier.parse("minecraft:" + WOODS.get(variant) + "_planks");
    }

    /** The log block of a wood (the Log2 pair carries wire id 162). */
    public static Identifier logBlock(int variant) {
        return Identifier.parse("minecraft:" + WOODS.get(variant) + "_log");
    }

    /** The leaves block of a wood (the Leaves2 pair carries wire id 161). */
    public static Identifier leavesBlock(int variant) {
        return Identifier.parse("minecraft:" + WOODS.get(variant) + "_leaves");
    }

    /** The sapling block of a wood. */
    public static Identifier saplingBlock(int variant) {
        return Identifier.parse("minecraft:" + WOODS.get(variant) + "_sapling");
    }

    /** The wire id of a family member's block. */
    public static int wireIdOf(String family, int variant) {
        return switch (family) {
            case "planks" -> 5;
            case "log" -> variant < 4 ? 17 : 162;
            case "leaves" -> variant < 4 ? 18 : 161;
            case "sapling" -> 6;
            default -> 0;
        };
    }

    /** The wire metadata nibble of a family member (the reference's own
     *  encoding: planks/saplings carry the bare variant, logs and leaves
     *  the & 3 band of their domain — the Log2/Leaves2 pair re-bases
     *  acacia/dark_oak to 0/1). */
    public static int wireMetaOf(String family, int variant) {
        return switch (family) {
            case "log", "leaves" -> variant & 3;
            default -> variant;
        };
    }

    // ------------------------------------------------------- the block states

    /** Every family block as an engine type (the registration vocabulary).
     *  The shipped oak planks/log/leaves keep their own constants; the
     *  rest build fresh here. */
    public static List<EngineBlockType> familyBlocks() {
        java.util.ArrayList<EngineBlockType> blocks = new java.util.ArrayList<>();
        for (int variant = 0; variant < WOODS.size(); variant++) {
            String wood = WOODS.get(variant);
            String display = DISPLAY.get(variant);
            if (variant != 0) {
                blocks.add(new EngineBlockType(planksBlock(variant),
                        display + " Planks", wireMetaOf("planks", variant)));
            }
            if (variant != 0) {
                blocks.add(new EngineBlockType(logBlock(variant),
                        display + " Log", wireMetaOf("log", variant)));
            }
            if (variant != 0) {
                blocks.add(new EngineBlockType(leavesBlock(variant),
                        display + " Leaves", wireMetaOf("leaves", variant)));
            }
            blocks.add(new EngineBlockType(saplingBlock(variant),
                    display + " Sapling", wireMetaOf("sapling", variant)));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------ resolution

    /**
     * The block a family item's stack places ({@code ItemBlock.useOn's
     * getStateFromMeta}): the planks/sapling domains read the damage
     * directly, the log/leaves domains split on their two ids (the second
     * pair re-bases acacia/dark_oak to 4/5 of the shared variant domain).
     */
    public static Optional<Identifier> placementBlockOf(Identifier item, int damage) {
        if (!item.namespace().equals("minecraft")) {
            return Optional.empty();
        }
        String value = item.value();
        if (value.equals("planks") || value.equals("sapling")) {
            return Optional.of(value.equals("planks")
                    ? planksBlock(clampVariant(damage))
                    : saplingBlock(clampVariant(damage)));
        }
        if (value.equals("log") || value.equals("log2")) {
            int variant = value.equals("log")
                    ? (damage & 3)                    // the 17 domain: oak..jungle
                    : 4 + ((damage & 3) & 1);         // the 162 domain: acacia/dark_oak
            return Optional.of(logBlock(clampVariant(variant)));
        }
        if (value.equals("leaves") || value.equals("leaves2")) {
            int variant = value.equals("leaves")
                    ? (damage & 3)
                    : 4 + ((damage & 3) & 1);
            return Optional.of(leavesBlock(clampVariant(variant)));
        }
        return Optional.empty();
    }

    private static int clampVariant(int variant) {
        return Math.max(0, Math.min(5, variant));
    }

    /**
     * The variant index of a family block id ({@code PlanksBlock.Variant}'s
     * own order), or -1 for a non-family identifier.
     */
    public static int variantOf(Identifier block) {
        if (!block.namespace().equals("minecraft")) {
            return -1;
        }
        String value = block.value();
        for (int variant = 0; variant < WOODS.size(); variant++) {
            String wood = WOODS.get(variant);
            if (value.equals(wood + "_planks") || value.equals(wood + "_log")
                    || value.equals(wood + "_leaves") || value.equals(wood + "_sapling")) {
                return variant;
            }
        }
        return -1;
    }

    /** Whether the identifier is any family's leaf block (the drop arm). */
    public static boolean isLeaves(Identifier block) {
        return variantOf(block) >= 0 && block.value().endsWith("_leaves");
    }

    /** Whether the identifier is any family's sapling block. */
    public static boolean isSapling(Identifier block) {
        return variantOf(block) >= 0 && block.value().endsWith("_sapling");
    }

    /** Whether the identifier is any family's log or planks block. */
    public static boolean isWoodBlock(Identifier block) {
        return variantOf(block) >= 0
                && (block.value().endsWith("_log") || block.value().endsWith("_planks"));
    }

    /**
     * The sapling chance band of a leaf break ({@code getSaplingDropChance}:
     * 20, the jungle leaf's own 40 override; the fortune relief
     * {@code -2<<level} clamped at 10).
     */
    public static int saplingChanceOf(Identifier leafBlock, int fortuneLevel) {
        int chance = leafBlock.value().equals("jungle_leaves") ? 40 : 20;
        if (fortuneLevel > 0) {
            chance -= 2 << fortuneLevel;
            if (chance < 10) {
                chance = 10;
            }
        }
        return chance;
    }

    /**
     * The apple chance band ({@code dropAppleWithChance}'s recomputed 200
     * with the {@code -10<<level} relief clamped at 40) — the OAK leaf's
     * own arm only.
     */
    public static int appleChanceOf(Identifier leafBlock, int fortuneLevel) {
        if (!leafBlock.value().equals("oak_leaves")) {
            return 0; // the other five never roll the apple
        }
        int chance = 200;
        if (fortuneLevel > 0) {
            chance -= 10 << fortuneLevel;
            if (chance < 40) {
                chance = 40;
            }
        }
        return chance;
    }
}
