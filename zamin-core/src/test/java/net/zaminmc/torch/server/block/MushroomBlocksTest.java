package net.zaminmc.torch.server.block;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The huge-mushroom pair (Slice 40 — MushroomBlock 1:1): the fourteen
 * VARIANT states per family with the reference's exact metadata band (the
 * bare form carrying the all_outside 14 default), the family tables
 * (behavior, light, silk), the drop walk's plant roll (getBaseDropCount's
 * max(0, nextInt(10)-7) with the silk arm's own item), and the wire ids.
 */
class MushroomBlocksTest {

    @Test
    void theFourteenVariantsCarryTheReferenceBand() {
        for (String family : MushroomBlocks.MUSHROOMS.keySet()) {
            for (MushroomBlocks.Variant variant : MushroomBlocks.VARIANTS) {
                EngineBlockType state = MushroomBlocks.state(family, variant.id());
                assertEquals(variant.id(), state.legacyMetadata(),
                        family + " " + state.identifier().value());
                assertEquals(family, MushroomBlocks.familyOf(state));
                // The behavior rows: the 0.2 wood family, the plant drop.
                BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
                assertEquals(0.2, behavior.hardness());
                assertEquals(MushroomBlocks.plantOf(family), behavior.drops().get(0).item());
                // The light rows: the full opaque cube.
                assertEquals(15, BlockLightTable.lookup(state.identifier())
                        .orElseThrow().filter());
            }
            // The bare form IS the all_outside state (the placement default).
            assertEquals("minecraft:" + family,
                    MushroomBlocks.state(family, 14).identifier().toString());
            assertEquals(14, MushroomBlocks.state(family, 14).legacyMetadata());
        }
        // The wire ids: the reference's own rows.
        assertEquals(99, MushroomBlocks.MUSHROOMS.get("brown_mushroom_block"));
        assertEquals(100, MushroomBlocks.MUSHROOMS.get("red_mushroom_block"));
        // The plant walk: the count band 0-2 over many rolls.
        java.util.Random random = new java.util.Random(1234L);
        int maxSeen = 0;
        for (int i = 0; i < 1000; i++) {
            int count = MushroomBlocks.baseDropCount(random);
            assertTrue(count >= 0 && count <= 2, "the count band 0-2, saw " + count);
            maxSeen = Math.max(maxSeen, count);
        }
        assertTrue(maxSeen == 2, "the band's top rolls (max(0, nextInt(10)-7))");
        // The items exist.
        for (String family : MushroomBlocks.MUSHROOMS.keySet()) {
            Optional<?> item = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                    Identifier.parse("minecraft:" + family));
            assertTrue(item.isPresent(), family + " item registered");
        }
        // The plant items resolve (the drop walk's target).
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:brown_mushroom")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:red_mushroom")).isPresent());
        // The drop walk's hand roll shape: the service over the family.
        var service = new net.zaminmc.torch.server.interaction.DropService(new java.util.Random(42L));
        var drops = service.dropsFor(MushroomBlocks.state("brown_mushroom_block", 14),
                null, false, 0);
        for (ItemStack drop : drops) {
            assertEquals("minecraft:brown_mushroom",
                    drop.type().identifier().toString());
            assertTrue(drop.count() >= 1 && drop.count() <= 2);
        }
        // The silk arm: the block itself.
        var silkDrops = service.dropsFor(MushroomBlocks.state("red_mushroom_block", 10),
                null, true, 0);
        assertEquals(1, silkDrops.size());
        assertEquals("minecraft:red_mushroom_block",
                silkDrops.get(0).type().identifier().toString());
    }
}
