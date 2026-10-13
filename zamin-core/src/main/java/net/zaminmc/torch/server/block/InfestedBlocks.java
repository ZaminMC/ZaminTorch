package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.Map;

/**
 * The infested family (Slice 18c — the reference's InfestedBlock): the
 * single MONSTER_EGG block 97 with the six host variants on the wire's
 * metadata nibble, mirrored as the engine's flattened six blocks.
 *
 * <p><b>The reference shapes:</b> {@code canBeInfested} gates the three
 * PLAIN host forms (the default stone, cobblestone, the default stone
 * brick — never the mossy/cracked/chiseled bricks); {@code Variant.
 * byHostState} maps a host to its egg (the fallback the STONE egg); and
 * {@code getSilkTouchDrop} answers the HOST's item form (the brick
 * variants ride the item's damage field: 1 mossy, 2 cracked,
 * 3 chiseled — the reference's own ItemStacks).</p>
 */
public final class InfestedBlocks {

    private InfestedBlocks() {
    }

    /** The egg identifier of each infestable host. */
    private static final Map<Identifier, Identifier> EGG_OF_HOST = Map.of(
            Identifier.parse("minecraft:stone"),
            Identifier.parse("minecraft:infested_stone"),
            Identifier.parse("minecraft:cobblestone"),
            Identifier.parse("minecraft:infested_cobblestone"),
            Identifier.parse("minecraft:stone_bricks"),
            Identifier.parse("minecraft:infested_stone_bricks"));

    /** The host identifier of each egg. */
    private static final Map<Identifier, Identifier> HOST_OF_EGG = Map.of(
            Identifier.parse("minecraft:infested_stone"),
            Identifier.parse("minecraft:stone"),
            Identifier.parse("minecraft:infested_cobblestone"),
            Identifier.parse("minecraft:cobblestone"),
            Identifier.parse("minecraft:infested_stone_bricks"),
            Identifier.parse("minecraft:stone_bricks"),
            Identifier.parse("minecraft:infested_mossy_stone_bricks"),
            Identifier.parse("minecraft:mossy_stone_bricks"),
            Identifier.parse("minecraft:infested_cracked_stone_bricks"),
            Identifier.parse("minecraft:cracked_stone_bricks"),
            Identifier.parse("minecraft:infested_chiseled_stone_bricks"),
            Identifier.parse("minecraft:chiseled_stone_bricks"));

    /** @return whether the identifier is one of the six eggs. */
    public static boolean isInfested(Identifier identifier) {
        return HOST_OF_EGG.containsKey(identifier);
    }

    /**
     * InfestedBlock.canBeInfested (lines 56-60): the three plain host
     * forms alone — the stone default state, the cobblestone, the
     * default stone brick (the mossy/cracked/chiseled bricks never
     * take the infestation).
     */
    public static boolean canBeInfested(Identifier identifier) {
        return EGG_OF_HOST.containsKey(identifier);
    }

    /**
     * @return the egg for the infestable host (Variant.byHostState's
     *         mapping), or null when the host never takes the egg.
     */
    public static Identifier eggFor(Identifier host) {
        return EGG_OF_HOST.get(host);
    }

    /**
     * @return the host of the egg (Variant.getHostState's mapping), or
     *         null when the identifier is not an egg.
     */
    public static Identifier hostOf(Identifier egg) {
        return HOST_OF_EGG.get(egg);
    }

    /**
     * The silk drop's item (the reference's getSilkTouchDrop
     * ItemStacks): the stone and cobblestone eggs drop their own host
     * items; ALL FOUR brick eggs drop the STONE BRICKS item — the
     * mossy/cracked/chiseled forms ride the item's damage field (1/2/3),
     * never their own items.
     */
    public static Identifier silkDropItem(Identifier egg) {
        if (egg.value().endsWith("infested_stone")) {
            return Identifier.parse("minecraft:stone");
        }
        if (egg.value().endsWith("infested_cobblestone")) {
            return Identifier.parse("minecraft:cobblestone");
        }
        return Identifier.parse("minecraft:stone_bricks");
    }

    /**
     * The silk drop's item damage (the reference's getSilkTouchDrop
     * ItemStacks): the brick variants ride the item's damage field —
     * mossy 1, cracked 2, chiseled 3; everything else 0.
     */
    public static int silkDropDamage(Identifier egg) {
        if (egg.value().endsWith("infested_mossy_stone_bricks")) {
            return 1;
        }
        if (egg.value().endsWith("infested_cracked_stone_bricks")) {
            return 2;
        }
        if (egg.value().endsWith("infested_chiseled_stone_bricks")) {
            return 3;
        }
        return 0;
    }
}
