package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.concurrent.RandomTickPicks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-G — THE RANDOM-TICK APPLY'S ONE-SLOT CHUNK CACHE: the
 * equivalence seat (the exact-algorithmic class's own proof
 * obligation): the cached apply walk and the verbatim per-pick
 * {@code getBlock} walk must produce IDENTICAL world states over
 * identically-built worlds — the cache serves the same chunk the
 * per-cell lookup would find, at the pick's own turn, and a miss is
 * never cached (a mid-walk chunk generation is seen by the very next
 * pick, exactly like the per-cell walk).
 *
 * <p>THE LOCKSTEP DESIGN (the P6 lane test's own shape): two
 * identically-built worlds — same registry, same flat generator, same
 * light engine, same chunk generation order — driven pick-for-pick
 * over MANY seeded tick bands: one world applies through the cached
 * walk ({@link RandomTickSystem#applyPicks}), the other through a
 * verbatim copy of the pre-KO-G per-pick walk. Every divergence — a
 * stale chunk, a missed generation, a wrong cell — would show as a
 * state difference. STRICT equality, not a convergence fixed point.</p>
 */
class RandomTickChunkCacheEquivalenceTest {

    /**
     * THE LOCKSTEP LAW: 400 seeded tick bands over the grass/dirt
     * patch scene (the decay/spread rules — the walk's real writes),
     * both worlds driven tick by tick, the cached walk against the
     * verbatim per-pick walk — the states must match EXACTLY while
     * both drift from their initial snapshot.
     */
    @Test
    void theCachedApplyAnswersThePerPickWalkExactly() {
        FrozenBlockRegistry registry =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld cachedWorld = buildWorld("kog-cache", registry);
        EngineWorld plainWorld = buildWorld("kog-plain", registry);
        // The patch above the surface: covered grass decays under it,
        // the patch's edge dirt grows grass from the open grass beside
        // it — the rules' own writes flow through both walks.
        for (int x = -3; x <= 2; x++) {
            for (int z = -3; z <= 2; z++) {
                cachedWorld.setBlock(new BlockPosition(x, 5, z), BuiltinBlocks.DIRT);
                plainWorld.setBlock(new BlockPosition(x, 5, z), BuiltinBlocks.DIRT);
            }
        }
        String initial = snapshot(cachedWorld);
        assertEquals(initial, snapshot(plainWorld), "the two worlds start identical");

        RandomTickSystem cachedSystem =
                new RandomTickSystem(cachedWorld, new Random(99));
        RandomTickSystem plainSystem =
                new RandomTickSystem(plainWorld, new Random(99));
        for (long tick = 1; tick <= 400; tick++) {
            List<long[]> picks = picksFor(cachedWorld, tick);
            assertEquals(picks.size(), picksFor(plainWorld, tick).size(),
                    "the identically-built worlds answer the same pick count");
            cachedSystem.applyPicks(picks);
            applyPicksVerbatim(plainSystem, plainWorld, picks);
        }
        String cachedState = snapshot(cachedWorld);
        String plainState = snapshot(plainWorld);
        assertEquals(plainState, cachedState,
                "the cached walk and the per-pick walk produce identical states");
        assertNotEquals(initial, cachedState,
                "non-trivial: the drives produced random-tick writes");
        assertNotEquals(initial, plainState,
                "non-trivial: the verbatim walk's writes flowed too");
    }

    /**
     * THE MID-WALK GENERATION SEAT (the miss-is-never-cached law): a
     * pick band where the FIRST pick reads an UNLOADED chunk (the
     * miss), a LATER pick's rule GENERATES that chunk mid-walk (the
     * ready sapling's tree placing canopy cells across the border —
     * {@code setBlock}'s own {@code getOrGenerate}), and picks AFTER
     * the growth read the SAME chunk again — the cached walk must see
     * the fresh chunk at the very next pick, exactly like the per-pick
     * walk. THE OBSERVABLE: the fresh chunk's surface reads GRASS (a
     * live rule that DRAWS from the shared stream) where a stale miss
     * would read air (no draw) — the two walks' streams would diverge
     * and every later rule would follow; the lockstep equality is the
     * guaranteed witness, never a probabilistic one.
     */
    @Test
    void aMidWalkGenerationIsSeenByTheNextPick() {
        // The seed search: the growth's own 1-in-7 roll is deterministic
        // per seed — find the seed where the tree actually grew (the
        // seat's own precondition, asserted by the search).
        long seed = -1;
        for (int candidate = 0; candidate < 100; candidate++) {
            if (midWalkRunGrewTheTree(candidate)) {
                seed = candidate;
                break;
            }
        }
        assertTrue(seed >= 0, "the seed search found a growth seed (the law's precondition)");

        // The lockstep run itself: both worlds driven by the identical
        // 3-pick band under the found seed.
        FrozenBlockRegistry registry =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld cachedWorld = buildWorld("kog-gen-cache", registry);
        EngineWorld plainWorld = buildWorld("kog-gen-plain", registry);
        ChunkPosition far = new ChunkPosition(3, 0);
        assertTrue(cachedWorld.peek(far) == null && plainWorld.peek(far) == null,
                "the far chunk starts unloaded in both worlds");
        plantBorderSapling(cachedWorld);
        plantBorderSapling(plainWorld);
        RandomTickSystem cachedSystem = new RandomTickSystem(cachedWorld, new Random(seed));
        RandomTickSystem plainSystem = new RandomTickSystem(plainWorld, new Random(seed));

        int baseX = 3 << 4;
        List<long[]> picks = List.of(
                new long[]{baseX, 4, 0},   // the miss: the unloaded chunk reads air
                new long[]{47, 5, 0},      // the ready sapling: grows, canopy crosses
                new long[]{baseX, 4, 0});  // the same chunk again: MUST read fresh
        cachedSystem.applyPicks(picks);
        applyPicksVerbatim(plainSystem, plainWorld, picks);

        assertTrue(cachedWorld.peek(far) != null && plainWorld.peek(far) != null,
                "the tree's canopy generated the far chunk in both worlds");
        // The trunk's own cell (the sapling's column, deterministic): the
        // growth landed in both.
        assertTrue(!cachedWorld.getBlock(new BlockPosition(47, 5, 0))
                        .identifier().value().endsWith("sapling_ready"),
                "the sapling grew in the cached world");
        assertTrue(!plainWorld.getBlock(new BlockPosition(47, 5, 0))
                        .identifier().value().endsWith("sapling_ready"),
                "the sapling grew in the plain world");
        // THE LAW: the post-growth pick read the FRESH chunk in both
        // walks — the grass rule fired (its stream draws) identically.
        assertEquals(snapshotBand(plainWorld, 40, 55), snapshotBand(cachedWorld, 40, 55),
                "the mid-walk generation is seen by the next pick (no stale miss)");
    }

    /** One search run: did the tree grow and generate the far chunk? */
    private boolean midWalkRunGrewTheTree(int seed) {
        FrozenBlockRegistry registry =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        EngineWorld world = buildWorld("kog-search-" + seed, registry);
        ChunkPosition far = new ChunkPosition(3, 0);
        if (world.peek(far) != null) {
            return false;
        }
        plantBorderSapling(world);
        RandomTickSystem system = new RandomTickSystem(world, new Random(seed));
        int baseX = 3 << 4;
        List<long[]> picks = List.of(
                new long[]{baseX, 4, 0},
                new long[]{47, 5, 0},
                new long[]{baseX, 4, 0});
        system.applyPicks(picks);
        return world.peek(far) != null;
    }

    /** The ready oak sapling at the loaded band's east border column. */
    private static void plantBorderSapling(EngineWorld world) {
        world.setBlock(new BlockPosition(47, 5, 0), SaplingBlocks.stateOf(0, true));
    }

    /** The snapshot over the border band (the tree's cells both sides). */
    private static String snapshotBand(EngineWorld world, int minX, int maxX) {
        StringBuilder state = new StringBuilder();
        for (int x = minX; x <= maxX; x++) {
            for (int z = -6; z <= 6; z++) {
                for (int y = 4; y <= 11; y++) {
                    var type = world.getBlock(new BlockPosition(x, y, z));
                    if (!type.equals(BuiltinBlocks.AIR)) {
                        state.append(x).append(',').append(y).append(',').append(z)
                                .append(':').append(type.identifier()).append(';');
                    }
                }
            }
        }
        return state.toString();
    }

    // ------------------------------------------------------------------
    // The rig
    // ------------------------------------------------------------------

    /**
     * The verbatim pre-KO-G apply walk (the per-pick {@code getBlock}
     * head read) — the reference law the cached walk must reproduce.
     */
    private static void applyPicksVerbatim(RandomTickSystem system, EngineWorld world,
            List<long[]> picks) {
        for (long[] cell : picks) {
            BlockPosition position = new BlockPosition(
                    (int) cell[0], (int) cell[1], (int) cell[2]);
            system.randomTick(position, world.getBlock(position));
        }
    }

    /** The pick band of one tick over the world's loaded chunks (region 0). */
    private static List<long[]> picksFor(EngineWorld world, long tick) {
        List<long[]> chunks = new java.util.ArrayList<>();
        for (var chunk : world.loadedChunks()) {
            chunks.add(new long[]{chunk.position().x(), chunk.position().z()});
        }
        // The stable order the lane's own grouping rides (sorted by
        // chunk x then z — the TreeMap region grouping's own order).
        chunks.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0])
                : Long.compare(a[1], b[1]));
        return RandomTickPicks.picksForRegion(42L, tick, 0L, chunks,
                RandomTickSystem.PICKS_PER_SECTION);
    }

    /** Two identically-built worlds (the lockstep's own precondition). */
    private static EngineWorld buildWorld(String name, FrozenBlockRegistry registry) {
        EngineWorld world = new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.attachRegionPartition(
                new net.zaminmc.torch.server.concurrent.RegionPartition(name, 2));
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getOrGenerate(new ChunkPosition(x, z));
            }
        }
        return world;
    }

    /** The state snapshot over the band the random ticks write. */
    private static String snapshot(EngineWorld world) {
        StringBuilder state = new StringBuilder();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 4; y <= 6; y++) {
                    var type = world.getBlock(new BlockPosition(x, y, z));
                    if (!type.equals(BuiltinBlocks.AIR)) {
                        state.append(x).append(',').append(y).append(',').append(z)
                                .append(':').append(type.identifier()).append(';');
                    }
                }
            }
        }
        return state.toString();
    }
}
