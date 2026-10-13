package net.zaminmc.torch.server.cauldron;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The cauldron family's block types (the flattened state model,
 * reference/1.8.8 block/CauldronBlock.java): legacy 118 with the LEVEL
 * property (0-3, the water depth in thirds) riding the nibble
 * ({@code getStateFromMetadata} lines 198-200: the level IS the
 * metadata; {@code getMetadataFromState} lines 203-205 folds it back).
 *
 * <p>The cauldron is a partial-collision block ({@code isCube()} false
 * with the 5/16-floor + four 1/8-wall rim shape, lines 36-49): light
 * passes untouched ({@code isSolidRender()} false — Block line 216's
 * opacity 0, the same rule the hopper's funnel shape rides), the
 * comparator signal IS the level (lines 188-195), and the block drops
 * the cauldron item whatever the level ({@code getDropItem} lines
 * 178-180).</p>
 *
 * <p>Block.java line 1242's registration: legacy 118,
 * {@code setStrength(2.0F)} — hardness 2.0 with the blast resistance
 * 10.0 (strength × 5).</p>
 */
public final class CauldronBlocks {

    private static final EngineBlockType[] STATES = buildStates();

    private CauldronBlocks() {
    }

    /** @return whether the type is any cauldron state. */
    public static boolean isCauldron(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().equals("cauldron")
                || type.identifier().value().startsWith("cauldron_level"));
    }

    /**
     * The state's water level (0-3, the thirds — CauldronBlock's LEVEL
     * property; the bare name answers 0).
     */
    public static int levelOf(BlockType type) {
        if (type == null || !isCauldron(type)) {
            return 0;
        }
        String value = type.identifier().value();
        if (value.equals("cauldron")) {
            return 0;
        }
        // The suffix form: cauldron_level1..3.
        String suffix = value.substring("cauldron".length());
        if (suffix.startsWith("_level")) {
            try {
                return Math.max(0, Math.min(3,
                        Integer.parseInt(suffix.substring("_level".length()))));
            } catch (NumberFormatException malformed) {
                return 0;
            }
        }
        return 0;
    }

    /** The state for a water level (clamped to the property's 0-3 band). */
    public static EngineBlockType of(int level) {
        return STATES[Math.max(0, Math.min(3, level))];
    }

    private static EngineBlockType[] buildStates() {
        EngineBlockType[] all = new EngineBlockType[4];
        all[0] = new EngineBlockType(
                Identifier.parse("minecraft:cauldron"), "Cauldron", 0);
        for (int level = 1; level <= 3; level++) {
            all[level] = new EngineBlockType(
                    Identifier.parse("minecraft:cauldron_level" + level),
                    "Cauldron", level);
        }
        return all;
    }

    /** Registers every cauldron state into the registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : STATES) {
            builder.register(type);
        }
        return builder;
    }
}
