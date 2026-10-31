package net.zaminmc.torch.server;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlastResistanceTable;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The blast-resistance table's census and spot pins: every registered block
 * resolves a READ value (the fold never leaves a registered family behind —
 * the same census convention the registry's own 896-row pin carries), and
 * the known vanilla values stand (the extraction's verified set).
 */
class BlastResistanceTableTest {

    private static FrozenBlockRegistry registry() {
        // F-EX3's re-pin: the CENSUS must enumerate the ENGINE'S BOOT
        // REGISTRY whole — EngineServer's boot wraps ELEVEN registerAll
        // families on top of BuiltinBlocks (cauldron, ender chest, beacon,
        // brewing, dispenser, hopper, piston, tripwire, redstone, daylight
        // detector, fluids — 345 extra identifiers). The pre-F-EX3 census
        // enumerated BuiltinBlocks alone (957): the 107 flattened STATE ids
        // the families added (the redstone clock's parts, the daylight
        // bands, the locked hopper, the moving piston, the falling fluids)
        // fell to blastResistance's fallback — the repeater and the
        // comparator answered MAX_VALUE (blast-proof) where the reference's
        // own registrations carry setStrength(0.0F) → READ 0.
        BlockRegistryBuilder builder =
                net.zaminmc.torch.server.cauldron.CauldronBlocks.registerAll(
                net.zaminmc.torch.server.chest.EnderChestBlocks.registerAll(
                net.zaminmc.torch.server.beacon.BeaconSystem.registerBlocks(
                net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new BlockRegistryBuilder()))))))))))));
        return builder.freeze();
    }

    @Test
    void everyRegisteredBlockResolvesABlastValue() {
        FrozenBlockRegistry registry = registry();
        int covered = 0;
        StringBuilder missing = new StringBuilder();
        for (var type : registry.all()) {
            Float value = BlastResistanceTable.read(type.identifier());
            if (value == null) {
                missing.append(' ').append(type.identifier().value());
            } else {
                assertTrue(value >= 0.0f && value < 4000000.1f,
                        "the value band: " + type.identifier().value() + " -> " + value);
                covered++;
            }
        }
        assertEquals("", missing.toString(),
                "the fold must cover every registered identifier (missing:" + missing + ")");
        assertEquals(1302, covered, "the FULL BOOT REGISTRY's census (F-EX3: 1302 = the 957 builtin rows + the boot families' 345 extra identifiers — cauldron/ender-chest/beacon/brewing/dispenser/hopper/piston/tripwire/redstone/daylight/fluids — every one resolving through the table, the fallback never answering for a registered block)");
    }

    @Test
    void theKnownVanillaValuesStand() {
        // The extraction's verified set (scripts/extract-blast-resistance.py
        // — the reference's Block.init() chains): the READ values the
        // explosion pays per crossing.
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:stone")), 1e-6f);
        assertEquals(3600000.0f, BlastResistanceTable.read(Identifier.parse("minecraft:bedrock")), 1e-3f);
        assertEquals(1200.0f, BlastResistanceTable.read(Identifier.parse("minecraft:obsidian")), 1e-3f);
        assertEquals(3.0f, BlastResistanceTable.read(Identifier.parse("minecraft:oak_planks")), 1e-6f);
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:cobblestone")), 1e-6f);
        assertEquals(0.5f, BlastResistanceTable.read(Identifier.parse("minecraft:dirt")), 1e-6f);
        assertEquals(0.8f, BlastResistanceTable.read(Identifier.parse("minecraft:sandstone")), 1e-6f);
        assertEquals(2.0f, BlastResistanceTable.read(Identifier.parse("minecraft:log")), 1e-6f);
        assertEquals(0.2f, BlastResistanceTable.read(Identifier.parse("minecraft:leaves")), 1e-6f);
        assertEquals(9.0f, BlastResistanceTable.read(Identifier.parse("minecraft:end_stone")), 1e-6f);
        assertEquals(3.5f, BlastResistanceTable.read(Identifier.parse("minecraft:lit_furnace")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:water")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:flowing_lava")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:fire")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:tnt")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:torch")), 1e-6f);
        // The state fold: the flattened forms resolve onto their families.
        assertEquals(3.5f, BlastResistanceTable.read(Identifier.parse("minecraft:furnace_lit_east")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:still_water_15")), 1e-6f);
        assertEquals(6.0f, BlastResistanceTable.read(Identifier.parse("minecraft:stone_slab_top")), 1e-6f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:fire_age7")), 1e-6f);
        // The renames: the engine's pre-flattening bases.
        assertEquals(0.6f, BlastResistanceTable.read(Identifier.parse("minecraft:grass_block")), 1e-6f);
        assertEquals(1200.0f, BlastResistanceTable.read(Identifier.parse("minecraft:anvil")), 1e-3f);
        // F-EX3's families — the boot registry's flattened state ids that
        // fell to the fallback before the fold's extension (each pinned to
        // the reference's own registration value):
        // repeater/comparator: setStrength(0.0F) → READ 0 (Block.java
        // lines 1319-1321, 1195-1196) — NEVER blast-proof.
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:repeater_east_2")), 1e-9f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:powered_repeater_north_4")), 1e-9f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:comparator_south_subtract")), 1e-9f);
        // lever: setStrength(0.5F) → READ 0.5 (line 1135) — the axis forms
        // (down_x/up_x) fold through the new _x suffix.
        assertEquals(0.5f, BlastResistanceTable.read(Identifier.parse("minecraft:lever_down_x_powered")), 1e-9f);
        // tripwire: no setStrength → READ 0 (line 1285).
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:tripwire_powered_suspended_attached_disarmed")), 1e-9f);
        // daylight detector: setStrength(0.2F) → READ 0.2 (the ctor line 32;
        // both the plain and inverted forms).
        assertEquals(0.2f, BlastResistanceTable.read(Identifier.parse("minecraft:daylight_detector_p15")), 1e-9f);
        assertEquals(0.2f, BlastResistanceTable.read(Identifier.parse("minecraft:daylight_detector_inverted_p7")), 1e-9f);
        // hopper: setStrength(3.0F).setBlastResistance(8.0F) → READ 4.8
        // (line 1335) — the LOCKED (disabled) forms the same.
        assertEquals(4.8f, BlastResistanceTable.read(Identifier.parse("minecraft:hopper_east_disabled")), 1e-6f);
        // the piston family: head 0.5 (PistonHeadBlock's ctor), the MOVING
        // carrier 0 (MovingBlock's setStrength(-1.0F) floors the field at
        // 0 — line 1055's registration; NOT blast-immune).
        assertEquals(0.5f, BlastResistanceTable.read(Identifier.parse("minecraft:piston_head_up_sticky")), 1e-9f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:moving_piston_west_sticky")), 1e-9f);
        assertEquals(0.0f, BlastResistanceTable.read(Identifier.parse("minecraft:moving_piston_down")), 1e-9f);
        // the fluid family's falling-flag states: the same water/lava READ
        // 100 as their still/flowing forms (lines 1017-1020).
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:falling_water_7")), 1e-6f);
        assertEquals(100.0f, BlastResistanceTable.read(Identifier.parse("minecraft:falling_lava")), 1e-6f);
    }
}
