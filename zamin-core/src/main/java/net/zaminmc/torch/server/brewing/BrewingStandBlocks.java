package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The brewing stand family's block types (Slice 11e — the flattened state
 * model, reference/1.8.8 block/BrewingStandBlock.java): legacy 117 with
 * the three HAS_BOTTLE properties riding the nibble
 * ({@code getStateFromMetadata} lines 145-153: bit i = has_bottle_i; the
 * same three bits fold back in {@code getMetadataFromState} lines 156-166).
 *
 * <p>The stand is a partial-collision block ({@code isCube() false} with
 * the 7/16-pole + the 1/8 base-plate collision pair, lines 64-74): light
 * passes ({@code setLight(0.125F)} = the mushroom-family filter 1, which
 * the engine's max(1, filter) step absorbs into the standard cost), it
 * never re-radiates strong power as weak, and the placement has no facing
 * — the plain block-item use lands the base state; the bottle bits arrive
 * through the stand's own inventory walk (the {@code findHasPotion} sync,
 * the reference's flag-2 setBlockState with the neighbors told
 * explicitly).</p>
 */
public final class BrewingStandBlocks {

    private static final EngineBlockType[] STATES = buildStates();

    private BrewingStandBlocks() {
    }

    /** @return whether the type is any brewing stand state. */
    public static boolean isBrewingStand(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("brewing_stand")
                || type.identifier().value().startsWith("brewing_stand_"));
    }

    /** The HAS_BOTTLE bit i (the slot i's occupancy). */
    public static boolean hasBottle(BlockType type, int slot) {
        return (metadataOf(type) & (1 << slot)) != 0;
    }

    /** The state for a bottle-bit combination. */
    public static EngineBlockType of(boolean bottle0, boolean bottle1, boolean bottle2) {
        return STATES[(bottle0 ? 1 : 0) | (bottle1 ? 2 : 0) | (bottle2 ? 4 : 0)];
    }

    /** The same state with one bottle bit swapped (the inventory sync). */
    public static EngineBlockType withBottle(BlockType type, int slot, boolean occupied) {
        boolean[] bottles = {hasBottle(type, 0), hasBottle(type, 1), hasBottle(type, 2)};
        bottles[slot] = occupied;
        return of(bottles[0], bottles[1], bottles[2]);
    }

    private static int metadataOf(BlockType type) {
        String value = type.identifier().value();
        if (value.equals("brewing_stand")) {
            return 0;
        }
        int bits = 0;
        if (value.contains("_bottle0")) {
            bits |= 1;
        }
        if (value.contains("_bottle1")) {
            bits |= 2;
        }
        if (value.contains("_bottle2")) {
            bits |= 4;
        }
        return bits;
    }

    private static EngineBlockType[] buildStates() {
        EngineBlockType[] all = new EngineBlockType[8];
        for (int bits = 0; bits < 8; bits++) {
            StringBuilder name = new StringBuilder("brewing_stand");
            for (int slot = 0; slot < 3; slot++) {
                if ((bits & (1 << slot)) != 0) {
                    name.append("_bottle").append(slot);
                }
            }
            all[bits] = new EngineBlockType(
                    Identifier.parse("minecraft:" + name), "Brewing Stand", bits);
        }
        return all;
    }

    /** Registers every brewing stand state into the registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : STATES) {
            builder.register(type);
        }
        return builder;
    }
}
