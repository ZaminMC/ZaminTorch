package net.zaminmc.torch.server.daylight;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The daylight detector family's block helpers (the reference's Block.java
 * lines 1323/1377 — {@code register(151, "daylight_detector", ...)} and
 * {@code register(178, "daylight_detector_inverted", ...)}): two forms
 * (the normal and the inverted) whose POWER property 0-15 rides the whole
 * metadata nibble ({@code getStateFromMetadata}: POWER = metadata — the
 * flattened {@code _pN} states share the id, the nibble carries the dial
 * the client renders).
 */
public final class DaylightDetectorBlocks {

    private DaylightDetectorBlocks() {
    }

    /** The POWER band (IntegerProperty.of("power", 0, 15)). */
    public static final int POWER_BAND = 15;

    private static final EngineBlockType[] DETECTOR = build(false);
    private static final EngineBlockType[] INVERTED = build(true);

    private static EngineBlockType[] build(boolean inverted) {
        EngineBlockType[] family = new EngineBlockType[POWER_BAND + 1];
        for (int power = 0; power <= POWER_BAND; power++) {
            String value = inverted
                    ? "daylight_detector_inverted_p" + power
                    : "daylight_detector_p" + power;
            family[power] = new EngineBlockType(Identifier.parse("minecraft:" + value),
                    inverted ? "Inverted Daylight Sensor" : "Daylight Sensor", power);
        }
        return family;
    }

    /** @return the normal form's variant at the power level. */
    public static BlockType ofPower(int power) {
        return DETECTOR[Math.floorMod(power, POWER_BAND + 1)];
    }

    /** @return the inverted form's variant at the power level. */
    public static BlockType invertedOfPower(int power) {
        return INVERTED[Math.floorMod(power, POWER_BAND + 1)];
    }

    /** @return whether the type is either form. */
    public static boolean isFamily(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && (type.identifier().value().startsWith("daylight_detector_p")
                        || type.identifier().value().startsWith("daylight_detector_inverted_p"));
    }

    /** @return whether the type is the inverted form (block 178). */
    public static boolean isInverted(BlockType type) {
        return type != null && type.identifier().namespace().equals("minecraft")
                && type.identifier().value().startsWith("daylight_detector_inverted_p");
    }

    /**
     * The POWER level the variant encodes (the flattened {@code _pN}
     * suffix — the metadata nibble's own value).
     */
    public static int powerOf(BlockType type) {
        String value = type.identifier().value();
        return Integer.parseInt(value.substring(value.lastIndexOf('p') + 1));
    }

    /** Registers every family member into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : DETECTOR) {
            builder.register(type);
        }
        for (EngineBlockType type : INVERTED) {
            builder.register(type);
        }
        return builder;
    }
}
