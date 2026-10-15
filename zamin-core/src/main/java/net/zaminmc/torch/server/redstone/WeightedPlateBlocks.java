package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The weighted pressure-plate pair (Slice 49a —
 * WeightedPressurePlateBlock 1:1): the light (gold) 147 over the weight 15
 * and the heavy (iron) 148 over the weight 150 — both
 * {@code setStrength(0.5F).setSounds(WOOD_SOUNDS)} over Material.IRON.
 *
 * <p>The state vocabulary is the reference's own
 * {@code getStateFromMetadata} band: the POWER value 0-15 whole in the
 * metadata nibble. The output signal (calculateOutputSignal lines 25-36):
 * {@code ceil(min(weight, entities) / weight * 15)} — the light plate reads
 * the entity count itself (capped 15), the heavy reads
 * {@code ceil(count / 10)} (every 10 entities a step, 150 for the full 15).</p>
 */
public final class WeightedPlateBlocks {

    private WeightedPlateBlocks() {
    }

    /** The light plate's wire id. */
    public static final int LIGHT_WIRE_ID = 147;

    /** The heavy plate's wire id. */
    public static final int HEAVY_WIRE_ID = 148;

    /** The two families' keys (the LIGHT 0, the HEAVY 1). */
    static final List<String> FAMILIES = List.of(
            "light_weighted_pressure_plate", "heavy_weighted_pressure_plate");

    /** The display names. */
    static final List<String> DISPLAY = List.of(
            "Weighted Pressure Plate (Light)", "Weighted Pressure Plate (Heavy)");

    /** The weights (the reference's own ctor numbers). */
    static final List<Integer> WEIGHTS = List.of(15, 150);

    /** One state's identifier: the POWER 0 form keeps the plain name. */
    static Identifier stateId(int family, int power) {
        return power == 0
                ? Identifier.parse("minecraft:" + FAMILIES.get(family))
                : Identifier.parse("minecraft:" + FAMILIES.get(family) + "_power" + power);
    }

    /** One state (the POWER value whole in the nibble). */
    public static net.zaminmc.torch.server.block.EngineBlockType state(int family, int power) {
        return new net.zaminmc.torch.server.block.EngineBlockType(
                stateId(family, power), DISPLAY.get(family), power);
    }

    /** Every registration this pair owns (the thirty-two states). */
    public static List<net.zaminmc.torch.server.block.EngineBlockType> familyBlocks() {
        List<net.zaminmc.torch.server.block.EngineBlockType> blocks = new ArrayList<>();
        for (int family = 0; family < FAMILIES.size(); family++) {
            for (int power = 0; power <= 15; power++) {
                blocks.add(state(family, power));
            }
        }
        return List.copyOf(blocks);
    }

    // ------------------------------------------------------------------
    // The reads
    // ------------------------------------------------------------------

    /** @return whether the identifier is one of the pair's states. */
    public static boolean isWeightedPlate(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        for (String family : FAMILIES) {
            if (value.equals(family) || value.startsWith(family + "_power")) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the family index (0 light, 1 heavy), or -1 for a non-member.
     */
    public static int familyOf(Identifier id) {
        if (id == null) {
            return -1;
        }
        String value = id.value();
        for (int i = 0; i < FAMILIES.size(); i++) {
            if (value.equals(FAMILIES.get(i)) || value.startsWith(FAMILIES.get(i) + "_power")) {
                return i;
            }
        }
        return -1;
    }

    /** @return the state's POWER value (0-15), or 0 for a non-member. */
    public static int powerOf(Identifier id) {
        if (id == null) {
            return 0;
        }
        String value = id.value();
        for (String family : FAMILIES) {
            if (value.equals(family)) {
                return 0;
            }
            if (value.startsWith(family + "_power")) {
                String tail = value.substring((family + "_power").length());
                try {
                    return Math.max(0, Math.min(15, Integer.parseInt(tail)));
                } catch (NumberFormatException bad) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /**
     * The output signal (calculateOutputSignal lines 25-36): the entity
     * count capped at the weight, scaled — {@code ceil(min(weight, count)
     * / weight * 15)}.
     */
    public static int outputSignal(int family, int entityCount) {
        if (entityCount <= 0) {
            return 0;
        }
        int weight = WEIGHTS.get(family);
        int capped = Math.min(weight, entityCount);
        return (int) Math.ceil((float) capped / weight * 15.0F);
    }

    /** The state write (setOutputSignal: the POWER value). */
    public static net.zaminmc.torch.server.block.EngineBlockType withPower(
            int family, int power) {
        return state(family, Math.max(0, Math.min(15, power)));
    }
}
