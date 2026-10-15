package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weighted pressure-plate pair (Slice 49a —
 * WeightedPressurePlateBlock 1:1): the sixteen POWER states each with the
 * reference's exact metadata band, the count walk, and the family tables.
 */
class WeightedPlateBlocksTest {

    @Test
    void theThirtyTwoStatesCarryTheReferenceBand() {
        for (int family = 0; family <= 1; family++) {
            for (int power = 0; power <= 15; power++) {
                var state = WeightedPlateBlocks.state(family, power);
                assertEquals(power, state.legacyMetadata());
                assertEquals(power, WeightedPlateBlocks.powerOf(state.identifier()));
                assertEquals(family, WeightedPlateBlocks.familyOf(state.identifier()));
                assertTrue(WeightedPlateBlocks.isWeightedPlate(state.identifier()));
                // The behavior rows: the 0.5 plate dropping its family item.
                var behavior = net.zaminmc.torch.server.block.BlockBehaviorTable
                        .of(state.identifier()).orElseThrow();
                assertEquals(0.5, behavior.hardness());
                assertEquals(family == 0
                                ? Identifier.parse("minecraft:light_weighted_pressure_plate")
                                : Identifier.parse("minecraft:heavy_weighted_pressure_plate"),
                        behavior.drops().get(0).item());
                // The light rows: the thin plate passes.
                assertEquals(0, net.zaminmc.torch.server.block.BlockLightTable
                        .lookup(state.identifier()).orElseThrow().filter());
            }
        }
        // The bare form IS the POWER-0 state.
        assertEquals("minecraft:light_weighted_pressure_plate",
                WeightedPlateBlocks.stateId(0, 0).toString());
        assertEquals("minecraft:heavy_weighted_pressure_plate_power7",
                WeightedPlateBlocks.stateId(1, 7).toString());
        // The count walk (calculateOutputSignal lines 25-36): the light
        // plate reads the entity count itself (capped 15), the heavy every
        // ten (ceil(count/10) capped at 150 entities).
        assertEquals(0, WeightedPlateBlocks.outputSignal(0, 0));
        assertEquals(1, WeightedPlateBlocks.outputSignal(0, 1));
        assertEquals(8, WeightedPlateBlocks.outputSignal(0, 8));
        assertEquals(15, WeightedPlateBlocks.outputSignal(0, 15));
        assertEquals(15, WeightedPlateBlocks.outputSignal(0, 40));
        assertEquals(1, WeightedPlateBlocks.outputSignal(1, 1)); // ceil(1*15/150) = 1
        assertEquals(1, WeightedPlateBlocks.outputSignal(1, 10)); // ceil(10*15/150) = ceil(1.0) = 1
        assertEquals(2, WeightedPlateBlocks.outputSignal(1, 20)); // ceil(2.0)
        assertEquals(3, WeightedPlateBlocks.outputSignal(1, 25)); // ceil(2.5) = 3
        assertEquals(15, WeightedPlateBlocks.outputSignal(1, 150));
        assertEquals(15, WeightedPlateBlocks.outputSignal(1, 400));
        // The wire ids.
        assertEquals(147, WeightedPlateBlocks.LIGHT_WIRE_ID);
        assertEquals(148, WeightedPlateBlocks.HEAVY_WIRE_ID);
        // The state write.
        assertEquals("minecraft:light_weighted_pressure_plate_power12",
                WeightedPlateBlocks.withPower(0, 12).identifier().toString());
    }
}
