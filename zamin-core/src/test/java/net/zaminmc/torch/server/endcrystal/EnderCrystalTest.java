package net.zaminmc.torch.server.endcrystal;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.TheEndWorldGenerator;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ender-crystal system's contracts (Slice 62, ported from
 * reference/1.8.8 entity/EnderCrystalEntity.java + EndPillarFeature lines
 * 47-51): the tick's feet-cell fire write (the not-fire gate — the bedrock
 * cap of a fresh pillar eats it on the first tick), the any-damage death
 * (the wound's slot-8 zero, the removal, the 6.0F destructive blast, the
 * destroyed-pillar mark), the 2x2x2 box the arrow consult reads, the
 * generation drain's idempotence, and the destroyed-set persistence.
 */
class EnderCrystalTest {

    /** The recording surface: the End world's reads/writes captured. */
    private static final class RecordingSurface implements EnderCrystalSystem.Surface {
        final java.util.Map<Long, String> blocks = new java.util.HashMap<>();
        final List<long[]> fireWrites = new ArrayList<>();
        final List<double[]> explosions = new ArrayList<>();
        long clock;

        void put(int x, int y, int z, String value) {
            blocks.put(((long) x << 42) | ((long) z << 21) | y, value);
        }

        @Override
        public String blockAt(int x, int y, int z) {
            return blocks.getOrDefault(((long) x << 42) | ((long) z << 21) | y, "air");
        }

        @Override
        public void setFire(int x, int y, int z) {
            fireWrites.add(new long[]{x, y, z});
            put(x, y, z, "fire"); // the write lands: the next read answers fire
        }

        @Override
        public void explode(double x, double y, double z) {
            explosions.add(new double[]{x, y, z});
        }

        @Override
        public long now() {
            return clock;
        }
    }

    /** The recording listener: the wire events captured. */
    private static final class RecordingListener implements EnderCrystalSystem.Listener {
        int spawns;
        int removals;
        int healthChanges;
        final List<Integer> healths = new ArrayList<>();

        @Override
        public void onCrystalSpawned(EnderCrystal crystal) {
            spawns++;
        }

        @Override
        public void onCrystalRemoved(EnderCrystal crystal) {
            removals++;
        }

        @Override
        public void onCrystalHealthChanged(EnderCrystal crystal) {
            healthChanges++;
            healths.add(crystal.health());
        }
    }

    @Test
    void constantsPinTheReferenceNumbers() {
        // takeDamage line 77 (the 6.0F blast) and the Explosion wound
        // ceiling (16 * power + 1 — the (d10^2 + d10)/2 * 8 * f3 + 1 shape
        // at point-blank, f3 = power * 2).
        assertEquals(6.0f, EnderCrystalSystem.BLAST_POWER);
        assertEquals(97.0f, EnderCrystalSystem.BLAST_MAX_DAMAGE);
        // registerSyncedData's birth value (the countdown 5).
        assertEquals(5, EnderCrystal.DEFAULT_HEALTH);
    }

    @Test
    void theTickWritesFireAtTheFeetCellOnce() {
        // EnderCrystalEntity.tick lines 45-50: the feet cell takes FIRE
        // whenever it holds anything else — the first tick's write, then
        // the not-fire gate holds the second.
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        system.spawn(100.5, 60.0, 0.5, 90.0f);
        surface.put(100, 60, 0, "bedrock"); // the generator's cap cell
        system.tick();
        assertEquals(1, surface.fireWrites.size(), "the first tick eats the cap");
        assertEquals(100, surface.fireWrites.get(0)[0]);
        assertEquals(60, surface.fireWrites.get(0)[1]);
        assertEquals(0, surface.fireWrites.get(0)[2]);
        system.tick();
        assertEquals(1, surface.fireWrites.size(), "the fire cell never rewrites");
    }

    @Test
    void theTickAdvancesTheSpin() {
        // tick line 43: the render counter advances every tick.
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        system.spawn(0.5, 64.0, 0.5, 0.0f);
        surface.put(0, 64, 0, "fire"); // already burning: no write arms
        EnderCrystal crystal = system.all().get(0);
        int born = crystal.renderTicks();
        system.tick();
        system.tick();
        assertEquals(born + 2, crystal.renderTicks());
        assertTrue(surface.fireWrites.isEmpty(), "the burning cell never rewrites");
    }

    @Test
    void anyDamagePopsTheCrystal() {
        // takeDamage lines 67-83: any wound zeroes the countdown, removes
        // the body, and detonates the 6.0F blast at the position.
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        RecordingListener listener = new RecordingListener();
        system.addListener(listener);
        system.spawnFromGeneration(50, 70, -20, 180.0f);
        EnderCrystal crystal = system.all().get(0);
        assertEquals(1, listener.spawns);
        assertTrue(system.damage(crystal));
        assertEquals(1, listener.healthChanges, "the wound broadcasts the slot-8 zero");
        assertEquals(0, listener.healths.get(0));
        assertEquals(1, listener.removals);
        assertTrue(system.all().isEmpty());
        assertEquals(1, surface.explosions.size(), "the death detonates");
        assertEquals(50.5, surface.explosions.get(0)[0], 1e-9);
        assertEquals(70.0, surface.explosions.get(0)[1], 1e-9);
        assertEquals(-19.5, surface.explosions.get(0)[2], 1e-9);
        // The destroyed-pillar mark (the persistence law's key).
        assertTrue(system.destroyedCellsSnapshot().contains(
                EnderCrystalSystem.pack(50, 70, -20)));
        // A second wound on the gone body refuses.
        assertFalse(system.damage(crystal));
    }

    @Test
    void theBoxHoldsTheTwoByTwoBody() {
        // The reference's setSize(2.0F, 2.0F): x/z within one block of the
        // center, y from the feet to two blocks up.
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        system.spawn(10.5, 40.0, -5.5, 0.0f);
        assertNotNull(system.at(10.5, 40.0, -5.5)); // the center
        assertNotNull(system.at(11.4, 41.9, -4.7)); // inside the corner
        assertNull(system.at(12.6, 40.0, -5.5)); // beyond the x flank
        assertNull(system.at(10.5, 42.1, -5.5)); // above the body
        assertNull(system.at(10.5, 39.9, -5.5)); // below the feet
    }

    @Test
    void destroyedPillarsNeverRespawn() {
        // The persistence law: a popped pillar's re-generated record
        // refuses the spawn (the ZEC store's destroyed marks).
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        assertTrue(system.spawnFromGeneration(0, 60, 0, 45.0f));
        EnderCrystal crystal = system.all().get(0);
        system.damage(crystal);
        assertFalse(system.spawnFromGeneration(0, 60, 0, 45.0f),
                "the destroyed pillar stays empty");
        assertTrue(system.all().isEmpty());
        // A neighbor pillar still lands.
        assertTrue(system.spawnFromGeneration(64, 60, 64, 90.0f));
    }

    @Test
    void theGenerationDrainIsIdempotent() {
        // The chest-minecart shape: a record twice-drained spawns once.
        RecordingSurface surface = new RecordingSurface();
        EnderCrystalSystem system = new EnderCrystalSystem(surface, new Random(1L), 900_000);
        assertTrue(system.spawnFromGeneration(8, 55, 8, 0.0f));
        assertFalse(system.spawnFromGeneration(8, 55, 8, 0.0f),
                "the same cell never double-spawns");
        assertEquals(1, system.all().size());
    }

    @Test
    void thePackDistinguishesCells() {
        // The persistence key: nearby cells pack distinctly.
        Set<Long> packed = new HashSet<>();
        for (int x = -2; x <= 2; x++) {
            for (int y = 59; y <= 61; y++) {
                for (int z = -2; z <= 2; z++) {
                    assertTrue(packed.add(EnderCrystalSystem.pack(x, y, z)),
                            "cell " + x + "," + y + "," + z + " must pack distinctly");
                }
            }
        }
    }

    @Test
    void theStoreRoundTrips(@TempDir Path dir) {
        // The ZEC form: the destroyed set survives the save/load pair.
        EnderCrystalDataStore store = new EnderCrystalDataStore(dir.resolve("endercrystals.bin"));
        Set<Long> saved = new HashSet<>();
        saved.add(EnderCrystalSystem.pack(0, 60, 0));
        saved.add(EnderCrystalSystem.pack(-100, 77, 100));
        store.save(saved);
        assertEquals(saved, store.load());
        // The absent file answers empty.
        assertEquals(Set.of(), new EnderCrystalDataStore(dir.resolve("absent.bin")).load());
    }

    @Test
    void thePillarRecordRidesTheBedrockCap() {
        // EndPillarFeature lines 47-50: the crystal's birth cell IS the
        // bedrock cap's cell (the reference's own write order — the entity
        // at pos + i, the cap at pos.up(i)); the record's yaw is the
        // nextFloat() * 360 roll.
        BlockRegistry registry = registry();
        TheEndWorldGenerator generator = new TheEndWorldGenerator(registry, 77L);
        BlockType bedrock = registry.require(Identifier.parse("minecraft:bedrock"));
        boolean sawPillar = false;
        for (int cx = -4; cx <= 4 && !sawPillar; cx++) {
            for (int cz = -4; cz <= 4 && !sawPillar; cz++) {
                EngineChunk chunk = new EngineChunk(new ChunkPosition(cx, cz),
                        registry.require(Identifier.parse("minecraft:air")));
                generator.generate(chunk);
                List<EngineChunk.PendingEnderCrystal> records = chunk.pendingEnderCrystals();
                if (records.isEmpty()) {
                    continue;
                }
                sawPillar = true;
                assertEquals(1, records.size(), "one pillar, one crystal");
                EngineChunk.PendingEnderCrystal record = records.get(0);
                assertTrue(record.yaw() >= 0.0f && record.yaw() < 360.0f,
                        "the nextFloat() * 360 roll");
                // The world cell the record names carries the bedrock cap
                // (the record's own chunk — the clip contract keeps the
                // pillar in-chunk).
                int lx = record.x() & 0xF;
                int lz = record.z() & 0xF;
                assertTrue(record.y() >= 0 && record.y() < 256, "the cell in bounds");
                assertEquals(bedrock, chunk.getBlock(lx, record.y(), lz),
                        "the crystal's feet cell IS the cap cell");
            }
        }
        assertTrue(sawPillar, "the 9x9 ring over seed 77 carries a pillar (the family test's own band)");
    }

    private static BlockRegistry registry() {
        // The boot shape (the End test's own chain).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }
}
