package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.entity.MobType;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The worldgen animal seeding (NaturalSpawner.populateChunk 1:1 — Slice 12h):
 * the while-chance group roll, the weighted biome table, the drift walk with
 * the leash, the horse group's shared dice, and the deterministic
 * regeneration contract.
 */
class WorldgenAnimalSeedingTest {

    private static BlockRegistry REGISTRY = registry();

    private static BlockRegistry registry() {
        // The full boot chain (EngineServer's freeze order — the generator
        // resolves the terrain family from it).
        return net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new net.zaminmc.torch.server.block.BlockRegistryBuilder()))))))))
                .freeze();
    }

    private NormalWorldGenerator generator() {
        return new NormalWorldGenerator(REGISTRY, "seedtest-world");
    }

    /** A land chunk's anchor (a plains/forest family column, spiral-sought). */
    private int[] landChunkAnchor(NormalWorldGenerator generator, int minRing, int maxRing) {
        for (int ring = minRing; ring <= maxRing; ring++) {
            for (int cx = -ring; cx <= ring; cx++) {
                for (int cz = -ring; cz <= ring; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != ring) {
                        continue; // ring walk only
                    }
                    Biome biome = generator.biomeAt(cx * 16 + 16, cz * 16 + 16);
                    if (biome == Biome.PLAINS || biome == Biome.FOREST || biome == Biome.JUNGLE) {
                        return new int[]{cx, cz};
                    }
                }
            }
        }
        return null;
    }

    private EngineChunk generateAt(NormalWorldGenerator generator, int chunkX, int chunkZ) {
        EngineChunk chunk = new EngineChunk(
                new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ), BuiltinBlocks.AIR);
        generator.generate(chunk);
        return chunk;
    }

    @Test
    void theSeedingIsDeterministicAcrossRegenerations() {
        NormalWorldGenerator generator = generator();
        int[] anchor = landChunkAnchor(generator, 0, 64);
        assertNotNull(anchor, "a land chunk exists within 64 rings");
        EngineChunk first = generateAt(generator, anchor[0], anchor[1]);
        EngineChunk second = generateAt(generator, anchor[0], anchor[1]);
        assertEquals(first.pendingAnimals(), second.pendingAnimals(),
                "the same chunk seeds the identical animal records");
    }

    @Test
    void theSeedingSpreadsAcrossTheArea() {
        NormalWorldGenerator generator = generator();
        int[] anchor = landChunkAnchor(generator, 0, 64);
        assertNotNull(anchor, "a land chunk exists within 64 rings");
        // 12x12 chunks around the land anchor: with the flat 10% chance per
        // chunk, at least one animal lands.
        int animals = 0;
        Set<MobType> kinds = new HashSet<>();
        for (int cx = anchor[0] - 6; cx <= anchor[0] + 6; cx++) {
            for (int cz = anchor[1] - 6; cz <= anchor[1] + 6; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                animals += chunk.pendingAnimals().size();
                for (EngineChunk.PendingAnimal animal : chunk.pendingAnimals()) {
                    kinds.add(animal.type());
                }
            }
        }
        assertTrue(animals > 0, "the 144-chunk land area seeded animals (found " + animals + ")");
        assertTrue(kinds.stream().allMatch(kind -> !kind.hostile),
                "only passive kinds seed through the worldgen pass");
    }

    @Test
    void thePlacementReadsTheGround() {
        NormalWorldGenerator generator = generator();
        int[] anchor = landChunkAnchor(generator, 0, 64);
        assertNotNull(anchor, "a land chunk exists within 64 rings");
        for (int cx = anchor[0] - 4; cx <= anchor[0] + 4; cx++) {
            for (int cz = anchor[1] - 4; cz <= anchor[1] + 4; cz++) {
                EngineChunk chunk = generateAt(generator, cx, cz);
                for (EngineChunk.PendingAnimal animal : chunk.pendingAnimals()) {
                    assertTrue(animal.y() >= 1 && animal.y() <= 255,
                            "the placement stays in the world");
                    // The +0.5 centering: the reference's own
                    // setPositionAndAngles(j + 0.5F, y, k + 0.5F).
                    assertEquals(0.5, animal.x() % 1.0, 1e-9,
                            "the x centering");
                    assertEquals(0.5, animal.z() % 1.0, 1e-9,
                            "the z centering");
                    assertTrue(animal.yaw() >= 0.0f && animal.yaw() < 360.0f,
                            "the yaw stays in the reference's 0..360 roll");
                }
            }
        }
    }

    @Test
    void theHorseGroupSharesItsDice() {
        NormalWorldGenerator generator = generator();
        // Horses only seed in plains chunks; walk rings around the land
        // anchor until a chunk carries 3+ horses (necessarily a group of
        // 3+, or two groups — either way some consecutive pair shares the
        // reference's group data).
        int[] anchor = landChunkAnchor(generator, 0, 64);
        assertNotNull(anchor, "a land chunk exists within 64 rings");
        List<EngineChunk.PendingAnimal> group = null;
        outer:
        for (int ring = 0; ring <= 40; ring++) {
            for (int cx = anchor[0] - ring; cx <= anchor[0] + ring; cx++) {
                for (int cz = anchor[1] - ring; cz <= anchor[1] + ring; cz++) {
                    if (Math.max(Math.abs(cx - anchor[0]), Math.abs(cz - anchor[1])) != ring) {
                        continue; // ring walk only
                    }
                    if (generator.biomeAt(cx * 16 + 16, cz * 16 + 16) != Biome.PLAINS) {
                        continue; // the horse rows live in the plains table
                    }
                    EngineChunk chunk = generateAt(generator, cx, cz);
                    List<EngineChunk.PendingAnimal> horses = new java.util.ArrayList<>();
                    for (EngineChunk.PendingAnimal animal : chunk.pendingAnimals()) {
                        if (animal.type() == MobType.HORSE) {
                            horses.add(animal);
                        }
                    }
                    if (horses.size() >= 3) {
                        group = horses;
                        break outer;
                    }
                }
            }
        }
        if (group == null) {
            return; // no plains horse pack at this seed within 40 rings — the soft gate
        }
        boolean sharedPair = false;
        for (int i = 0; i + 1 < group.size(); i++) {
            boolean shares = (group.get(i).horseVariant() & 0xFF)
                    == (group.get(i + 1).horseVariant() & 0xFF)
                    && group.get(i).horseSubtype() == group.get(i + 1).horseSubtype();
            sharedPair |= shares;
        }
        assertTrue(sharedPair,
                "a horse pack shares the reference's group data (the color byte + the subtype)");
        for (EngineChunk.PendingAnimal horse : group) {
            assertTrue((horse.horseVariant() >> 8) < 5,
                    "the marking stays in the reference's nextInt(5) band");
        }
    }

    @Test
    void differentChunksSeedDifferentSpots() {
        NormalWorldGenerator generator = generator();
        // Not every chunk seeds animals (the 10% chance), and the records
        // differ across chunks (the hash-seeded randomness, not a constant).
        int[] anchor = landChunkAnchor(generator, 0, 64);
        assertNotNull(anchor, "a land chunk exists within 64 rings");
        EngineChunk a = generateAt(generator, anchor[0], anchor[1]);
        EngineChunk b = generateAt(generator, anchor[0] + 9, anchor[1] + 9);
        // Both may be empty; when either seeded, the positions differ.
        if (!a.pendingAnimals().isEmpty() && !b.pendingAnimals().isEmpty()) {
            assertNotEquals(a.pendingAnimals(), b.pendingAnimals(),
                    "the per-chunk seeds walk their own streams");
        }
    }
}
