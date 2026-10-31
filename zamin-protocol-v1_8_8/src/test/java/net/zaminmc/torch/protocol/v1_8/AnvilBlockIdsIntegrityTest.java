package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Anvil import table's drift guard (the Anvil canvas' cross-module
 * contract): {@code AnvilBlockIds} — the import's numeric state table in
 * zamin-core — must agree with THIS module's wire table for every block
 * the engine registers. The two tables live on opposite sides of the
 * module boundary (the protocol module owns identifier -> id + metadata;
 * the import owns id + metadata -> identifier), so this test is the only
 * place both are visible — the one authority that can prove the pair
 * never drifts when the registry grows. The derivation rebuilds the
 * table exactly the way the class documents it (the engine's own
 * {@code legacyMetadata} first, the wire's computed {@code metadataOf}
 * as the fallback, the reference-derived gap fills on top), and the
 * failure report prints the missing rows to paste into the class.
 */
class AnvilBlockIdsIntegrityTest {

    /** The EngineServer.start() registration chain, replicated exactly. */
    private static BlockRegistryBuilder.FrozenBlockRegistry fullRegistry() {
        var builder = new BlockRegistryBuilder();
        builder = net.zaminmc.torch.server.block.BuiltinBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.block.FluidBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.beacon.BeaconSystem.registerBlocks(builder);
        builder = net.zaminmc.torch.server.chest.EnderChestBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.cauldron.CauldronBlocks.registerAll(builder);
        return builder.freeze();
    }

    @Test
    void theImportTableAgreesWithTheWireTableForEveryRegisteredBlock() {
        var registry = fullRegistry();
        // The derivation (the AnvilBlockIds class's own documented rule):
        // the engine's variant nibble first, the wire's computed nibble as
        // the fallback.
        Map<Long, String> expected = new TreeMap<>();
        List<String> collisions = new ArrayList<>();
        for (var type : registry.all()) {
            Identifier id = type.identifier();
            Optional<Integer> legacy = LegacyBlockIds.legacyId(id);
            if (legacy.isEmpty()) {
                continue; // the wire cannot spell it (the oak sapling's known gap)
            }
            int engineMeta = type instanceof EngineBlockType engineType
                    ? engineType.legacyMetadata() : 0;
            int wireMeta = LegacyBlockIds.metadataOf(id);
            int meta = engineMeta != 0 ? engineMeta : wireMeta;
            long key = ((long) legacy.get() << 4) | meta;
            String previous = expected.put(key, id.toString());
            if (previous != null && !previous.equals(id.toString())) {
                collisions.add(previous + " vs " + id + " @ id=" + (key >> 4)
                        + " meta=" + (key & 15));
            }
        }
        // The documented gap fills (derived from the reference, carried in
        // the class's own javadoc): the oak sapling's missing wire row, the
        // farmland moisture band, the double-plant upper band (the species
        // is virtual in 1.8.8 — the class refines it per-cell from the
        // lower, so the upper family's own collapse at 175|10 is expected,
        // not drift).
        expected.put(key(6, 0), "minecraft:oak_sapling");
        for (int meta = 2; meta <= 7; meta++) {
            expected.put(key(60, meta), "minecraft:farmland_wet");
        }
        for (int meta = 8; meta <= 11; meta++) {
            expected.put(key(175, meta), "minecraft:double_grass_upper");
        }
        // The AGE-15 bands (the farming growth pass): the reference's
        // SugarCaneBlock/CactusBlock AGE 0-15 accumulators flattened to the
        // engine's single types — the nibble is client-invisible but a
        // lived-in vanilla world carries every value, and the nibble-0-only
        // rows isolated the rest AS AIR.
        for (int meta = 1; meta <= 15; meta++) {
            expected.put(key(81, meta), "minecraft:cactus");
            expected.put(key(83, meta), "minecraft:sugar_cane");
        }
        // The bed's OCCUPIED nibbles (VT-011, BedBlock lines 200-231
        // derived): the reference writes bit 2 for an occupied HEAD
        // alone (12-15 — a world saved mid-sleep) and reads it on the
        // head arm only; metas 4-7 (bit 2 on a foot — unwritable by the
        // reference's own save path, the editor-corruption band) load
        // as ordinary feet with the bit ignored. The occupied bit is
        // above the engine's sleep model, so the facing bits alone are
        // the answer — every one of the sixteen nibbles loads a bed
        // state in the reference.
        String[] bedFeet = {"minecraft:bed", "minecraft:bed_west",
                "minecraft:bed_north", "minecraft:bed_east"};
        String[] bedHeads = {"minecraft:bed_head", "minecraft:bed_head_west",
                "minecraft:bed_head_north", "minecraft:bed_head_east"};
        for (int band = 0; band <= 3; band++) {
            expected.put(key(26, 4 + band), bedFeet[band]);
            expected.put(key(26, 12 + band), bedHeads[band]);
        }
        collisions.removeIf(collision -> collision.contains("@ id=175 meta=10"));

        // Every expected row must resolve to exactly that identifier.
        List<String> missing = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
        expected.forEach((packed, name) -> {
            Optional<Identifier> resolved = net.zaminmc.torch.server.world.anvil.AnvilBlockIds
                    .resolve((int) (packed >> 4), (int) (packed & 15));
            if (resolved.isEmpty()) {
                missing.add("state(" + (packed >> 4) + ", " + (packed & 15) + ", \"" + name
                        + "\")");
            } else if (!resolved.get().toString().equals(name)) {
                wrong.add("(" + (packed >> 4) + "," + (packed & 15) + ") expected " + name
                        + " but the table answers " + resolved.get());
            }
        });
        assertTrue(missing.isEmpty() && wrong.isEmpty() && collisions.isEmpty(),
                "the import table drifted from the wire table.\nMissing rows (paste into"
                        + " AnvilBlockIds):\n" + String.join(",\n", missing)
                        + "\nWrong rows:\n" + String.join(";\n", wrong)
                        + "\nUndocumented collisions:\n" + String.join(";\n", collisions));
        assertEquals(1329, expected.size(), // The bed's OCCUPIED nibble re-pin (VT-011: 1329 = 1321 + the bed's eight rows over 26's residual band 4-7/12-15 — the reference's read law covers every one of the sixteen nibbles, the occupied bit above the engine's sleep model so the facing bits alone are the answer) // The farming growth pass's re-pin (1321 = 1291 + the thirty cane/cactus AGE rows over legacy 81/83 — the accumulators' nibbles flattened to the single types, the farmland moisture band's own precedent) // The rail-rotation pass's re-pin (1291 = 1283 + the plain rail's eight slope/curve rows over wire 66's nibble band 2-9) // The banner/sign rotation re-pin (1283 = 1251 + the banner family's twenty rows over 176/177 (Wave BN-A: the sixteen standing rotations + the wall pair's byId band) + the standing sign's twelve intermediate rotations (the twelfth verifier pass)) // The metadata slice's re-pin (1251 = 1245 + the pumpkin pair's six rotated states over legacy 86/91, the HORIZONTAL-id nibble band 1-3) // The VIII14 decorators slice's fern row (block 31's variant 2) + the surface slice's podzol/coarse_dirt pair (registered at 11577c52 without the pin moving)
                "the derived table's size (the class's own pinned count; a change means the"
                        + " registry grew — regenerate the class and re-pin this number)."
                        + " The Wave VI fluids band added the 46 still/flowing-0/falling-level"
                        + " members over the two wire pairs; this re-pin ALSO repairs the stale"
                        + " row the fire migration left behind (a93ff6a's fifteen AGE states"
                        + " joined the derivation without the pin moving — the test had been"
                        + " failing on develop since)");
    }

    @Test
    void everyImportRowResolvesInTheEngineRegistry() {
        // The reverse direction: every (id, meta) the table answers must be
        // a block the engine actually registers — an importable id can
        // never point at a name the registry does not hold.
        var registry = fullRegistry();
        int checked = 0;
        for (int id = 0; id <= 255; id++) {
            for (int meta = 0; meta <= 15; meta++) {
                Optional<Identifier> resolved = net.zaminmc.torch.server.world.anvil.AnvilBlockIds
                        .resolve(id, meta);
                if (resolved.isPresent()) {
                    assertTrue(registry.lookup(resolved.get()).isPresent(),
                            "the table answers " + resolved.get() + " for (" + id + "," + meta
                                    + ") but the engine does not register it");
                    checked++;
                }
            }
        }
        assertTrue(checked > 1100, "the whole 0..255 x 0..15 lattice was walked: " + checked);
    }

    private static long key(int id, int meta) {
        return ((long) id << 4) | meta;
    }
}
