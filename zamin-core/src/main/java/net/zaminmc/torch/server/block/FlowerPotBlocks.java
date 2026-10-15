package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The flower-pot family (Slice 48 — FlowerPotBlock 1:1): the pot 140
 * ({@code setStrength(0.0F).setSounds(DEFAULT_SOUNDS)} over
 * Material.DECORATION — Block.java line 1302's own row) with its thirteen
 * potted contents over the reference's LEGACY_DATA band
 * (createBlockEntity lines 176-223's own mapping).
 *
 * <p>The state vocabulary is the reference's own wire band: the empty pot
 * 0 and the thirteen potted forms 1-13. The potted plant lives in the
 * reference's block entity (the TE's item + metadata); the engine's
 * flattened model carries the content in the block state — the same
 * thirteen forms the reference's LEGACY_DATA nibble distinguishes on the
 * wire.</p>
 */
public final class FlowerPotBlocks {

    private FlowerPotBlocks() {
    }

    /** The wire id (Block.java's own row). */
    public static final int WIRE_ID = 140;

    /**
     * The CONTENTS band (createBlockEntity's own mapping — the LEGACY_DATA
     * values 1-13): the poppy, the dandelion, the six saplings, the two
     * mushrooms, the cactus, the dead bush, the fern.
     */
    static final List<String> CONTENTS = List.of(
            "flower_pot", "potted_poppy", "potted_dandelion",
            "potted_oak_sapling", "potted_spruce_sapling", "potted_birch_sapling",
            "potted_jungle_sapling", "potted_red_mushroom", "potted_brown_mushroom",
            "potted_cactus", "potted_dead_bush", "potted_fern",
            "potted_acacia_sapling", "potted_dark_oak_sapling");

    /** The display names. */
    static final List<String> DISPLAY = List.of(
            "Flower Pot", "Potted Poppy", "Potted Dandelion",
            "Potted Oak Sapling", "Potted Spruce Sapling", "Potted Birch Sapling",
            "Potted Jungle Sapling", "Potted Red Mushroom", "Potted Brown Mushroom",
            "Potted Cactus", "Potted Dead Bush", "Potted Fern",
            "Potted Acacia Sapling", "Potted Dark Oak Sapling");

    /** The pot item (Item.java's own PlaceableItem row over 390). */
    public static final Identifier POT_ITEM = Identifier.parse("minecraft:flower_pot");

    /** One state's identifier (the index IS the LEGACY_DATA value). */
    static Identifier stateId(int content) {
        return Identifier.parse("minecraft:" + CONTENTS.get(content));
    }

    /** One state (the LEGACY_DATA nibble whole). */
    public static EngineBlockType state(int content) {
        return new EngineBlockType(stateId(content), DISPLAY.get(content), content);
    }

    /** Every registration this family owns (the fourteen states). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int content = 0; content < CONTENTS.size(); content++) {
            blocks.add(state(content));
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the fourteen pot states. */
    public static boolean isPot(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String content : CONTENTS) {
            if (value.equals(content)) {
                return true;
            }
        }
        return false;
    }

    /** @return the state's LEGACY_DATA id (0-13), or -1 for a non-member. */
    public static int contentOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (int i = 0; i < CONTENTS.size(); i++) {
            if (value.equals(CONTENTS.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /** @return whether the state is the empty pot (LEGACY_DATA 0). */
    public static boolean isEmptyPot(Identifier id) {
        return contentOf(id) == 0;
    }

    /**
     * The pottable consult (FlowerPotBlock.isPottablePlant lines 84-93):
     * the dandelion, the poppy (the red flower's TYPE-0 form — the exotic
     * eight ride the block-entity family below), the cactus, the two
     * mushrooms, the six saplings, the dead bush, and the tall grass's
     * FERN damage alone.
     */
    public static boolean isPottable(Identifier held, int damage) {
        if (held == null || !held.namespace().equals("minecraft")) {
            return false;
        }
        String name = held.value();
        return name.equals("dandelion")
                || name.equals("poppy")
                || (name.equals("red_flower") && damage == 0)
                || name.equals("cactus")
                || name.equals("brown_mushroom")
                || name.equals("red_mushroom")
                || name.equals("sapling")
                || name.equals("dead_bush")
                || (name.equals("tall_grass") && damage == 2);
    }

    /**
     * The potted state a pottable held item writes (createBlockEntity's
     * own mapping): the poppy 1, the dandelion 2, the saplings 3-6 + 12-13
     * over the wood damage, the red mushroom 7, the brown 8, the cactus 9,
     * the dead bush 10, the fern 11.
     */
    public static Optional<Identifier> pottedStateOf(Identifier held, int damage) {
        if (held == null || !held.namespace().equals("minecraft")) {
            return Optional.empty();
        }
        String name = held.value();
        int wood = damage < 0 || damage > 5 ? 0 : damage;
        int content = switch (name) {
            case "poppy", "red_flower" -> 1; // the TYPE-0 form alone (the poppy)
            case "dandelion" -> 2;
            case "sapling" -> switch (wood) {
                case 0 -> 3; case 1 -> 4; case 2 -> 5; case 3 -> 6;
                case 4 -> 12; default -> 13;
            };
            case "red_mushroom" -> 7;
            case "brown_mushroom" -> 8;
            case "cactus" -> 9;
            case "dead_bush" -> 10;
            case "tall_grass" -> damage == 2 ? 11 : -1;
            default -> -1;
        };
        return content > 0 ? Optional.of(stateId(content)) : Optional.empty();
    }

    /**
     * The plant's item a potted state drops on its break (onRemoved lines
     * 149-155's own drop: the TE's item + metadata — the saplings over the
     * wood damage, the fern the tall grass's damage 2).
     */
    public static Identifier plantItemOf(int content) {
        return switch (content) {
            case 1 -> Identifier.parse("minecraft:poppy");
            case 2 -> Identifier.parse("minecraft:dandelion");
            case 3, 4, 5, 6, 12, 13 -> Identifier.parse("minecraft:sapling");
            case 7 -> Identifier.parse("minecraft:red_mushroom");
            case 8 -> Identifier.parse("minecraft:brown_mushroom");
            case 9 -> Identifier.parse("minecraft:cactus");
            case 10 -> Identifier.parse("minecraft:dead_bush");
            case 11 -> Identifier.parse("minecraft:tall_grass");
            default -> null;
        };
    }

    /** The plant item's damage (the wood band 0-5, the fern's 2). */
    public static int plantDamageOf(int content) {
        return switch (content) {
            case 3 -> 0; case 4 -> 1; case 5 -> 2; case 6 -> 3;
            case 12 -> 4; case 13 -> 5; case 11 -> 2;
            default -> 0;
        };
    }

    /**
     * The support read (FlowerPotBlock.neighborChanged lines 122-128 +
     * canBePlaced lines 118-120): the block below must have a SOLID TOP
     * — the engine's solid stand-in.
     */
    public static boolean isSupported(net.zaminmc.torch.server.world.EngineWorld world,
            net.zaminmc.torch.block.BlockPosition pos) {
        return WorldSolidity.isSolid(world.getBlock(pos.offset(0, -1, 0)));
    }
}
