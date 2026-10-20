package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fire family's migration onto the scheduled block-tick surface —
 * FireBlock 1:1 (reference/1.8.8 net/minecraft/block/FireBlock.java): the
 * AGE band, the per-block flammability/burn tables, hasSolidTop, the
 * onAdded placement arm, the doFireTick freeze, the no-fuel death, the
 * rain douse, the eternal flames, the age bump + re-arm cadence, and the
 * scripted burn/spread walks (the destroy roll, the TNT prime, the spread
 * box) — each pinned against the reference's own lines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FireBlocksTest {

    private static FrozenBlockRegistry registry;

    /** The recording TNT prime seam (the burn walk's onBroken arm). */
    private final List<BlockPosition> primes = new ArrayList<>();

    /** The rain flag the seams read (per-scene toggling). */
    private boolean raining;
    /** The doFireTick rule the seams read. */
    private boolean fireTickRule = true;

    private EngineWorld world;
    private ScheduledTickSystem system;

    @BeforeAll
    void setUp() {
        net.zaminmc.torch.server.block.BlockRegistryBuilder builder =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder());
        // The hopper family (EngineServer's own registry chain includes it;
        // the hasSolidTop arm answers its states).
        builder = net.zaminmc.torch.server.hopper.HopperBlocks.registerAll(builder);
        registry = builder.freeze();
    }

    // ------------------------------------------------------------------
    // The scene harness: a fresh world + system + listener per scene
    // ------------------------------------------------------------------

    private void scene() {
        scene(new Random());
    }

    private void scene(Random rolls) {
        world = new EngineWorld("fire-scene", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        world.getOrGenerate(new ChunkPosition(0, 0));
        system = new ScheduledTickSystem(world, rolls);
        primes.clear();
        raining = false;
        fireTickRule = true;
        FireBlocks.wire(() -> raining, () -> fireTickRule, () -> 1,
                pos -> primes.add(pos),
                world, null, system, new Random());
        for (int age = 0; age <= FireBlocks.MAX_AGE; age++) {
            system.registerTicker(FireBlocks.stateId(age), FireBlocks::tick);
        }
        world.addChangeListener(new FireBlocks.PlacementListener());
    }

    /** A random that replays scripted nextInt values, then falls back. */
    private static final class ScriptedRandom extends Random {
        private final int[] queue;
        private int at;

        ScriptedRandom(int... values) {
            this.queue = values;
        }

        @Override
        public int nextInt(int bound) {
            if (at < queue.length) {
                return queue[at++];
            }
            return super.nextInt(bound);
        }
    }

    /** Advances the world clock and runs one system's pass. */
    private void advance(int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tickTime(true);
            system.tick();
        }
    }

    private BlockPosition at(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    // ------------------------------------------------------------------
    // The AGE band
    // ------------------------------------------------------------------

    @Test
    void theAgeBandRoundTrips() {
        for (int age = 0; age <= 15; age++) {
            Identifier id = FireBlocks.stateId(age);
            assertTrue(FireBlocks.isFire(id), "age " + age + " is fire");
            assertEquals(age, FireBlocks.ageOf(id), "age " + age + " round-trips");
            assertEquals(age,
                    ((EngineBlockType) FireBlocks.stateOf(age)).legacyMetadata(),
                    "age " + age + " rides the wire nibble");
        }
        assertFalse(FireBlocks.isFire(Identifier.parse("minecraft:stone")));
        assertFalse(FireBlocks.isFire(Identifier.parse("minecraft:fire_age16")));
        assertEquals(-1, FireBlocks.ageOf(Identifier.parse("minecraft:firework")));
        assertEquals(-1, FireBlocks.ageOf(Identifier.parse("minecraft:fire_age")));
    }

    // ------------------------------------------------------------------
    // The flammability/burn tables (init lines 75-111)
    // ------------------------------------------------------------------

    @Test
    void theFlammabilityTablesMatchTheReferenceRows() {
        // The positives — every row the reference's init registers.
        assertRow("minecraft:oak_planks", 5, 20);
        assertRow("minecraft:double_wooden_slab", 5, 20);
        assertRow("minecraft:oak_slab", 5, 20);
        assertRow("minecraft:oak_slab_top", 5, 20);
        assertRow("minecraft:oak_fence", 5, 20);
        assertRow("minecraft:spruce_fence", 5, 20);
        assertRow("minecraft:oak_stairs_east", 5, 20);
        assertRow("minecraft:oak_stairs_north_top", 5, 20);
        assertRow("minecraft:spruce_stairs_east", 5, 20);
        assertRow("minecraft:oak_log", 5, 5);
        assertRow("minecraft:spruce_log", 5, 5);
        assertRow("minecraft:oak_leaves", 30, 60);
        assertRow("minecraft:bookshelf", 30, 20);
        assertRow("minecraft:tnt", 15, 100);
        assertRow("minecraft:tall_grass", 60, 100);
        assertRow("minecraft:sunflower", 60, 100);
        assertRow("minecraft:dandelion", 60, 100);
        assertRow("minecraft:poppy", 60, 100);
        assertRow("minecraft:dead_bush", 60, 100);
        assertRow("minecraft:wool", 30, 60);
        assertRow("minecraft:vine", 15, 100);
        assertRow("minecraft:coal_block", 5, 5);
        assertRow("minecraft:hay_block", 60, 20);
        assertRow("minecraft:carpet_white", 60, 20);
        assertRow("minecraft:carpet_red", 60, 20);
        // The negatives — the reference's table carries no rows for these
        // (the engine's earlier invented set is retired: crafting table,
        // chest, ladder and sign are NOT flammable any more).
        assertRow("minecraft:stone", 0, 0);
        assertRow("minecraft:crafting_table", 0, 0);
        assertRow("minecraft:chest", 0, 0);
        assertRow("minecraft:ladder", 0, 0);
        assertRow("minecraft:sign", 0, 0);
        assertRow("minecraft:nether_brick_fence", 0, 0);
        assertRow("minecraft:fire", 0, 0);
        assertRow("minecraft:fire_age5", 0, 0);
    }

    private void assertRow(String name, int flammability, int burn) {
        Identifier id = Identifier.parse(name);
        assertEquals(flammability, FireBlocks.flammabilityOf(id),
                name + " flammability");
        assertEquals(burn, FireBlocks.burnChanceOf(id), name + " burnChance");
        assertEquals(flammability > 0, FireBlocks.isFlammable(id), name + " membership");
    }

    // ------------------------------------------------------------------
    // hasSolidTop (World.java lines 1740-1750)
    // ------------------------------------------------------------------

    @Test
    void hasSolidTopFollowsTheReferenceRule() {
        scene();
        // The flat world's own surface.
        assertTrue(FireBlocks.hasSolidTop(world, at(3, 4, 3)));
        // The stairs TOP half and the slab TOP half.
        world.setBlock(at(3, 5, 3), registry.require(Identifier.parse("minecraft:oak_stairs_east_top")));
        assertTrue(FireBlocks.hasSolidTop(world, at(3, 5, 3)));
        world.setBlock(at(4, 5, 3), registry.require(Identifier.parse("minecraft:oak_stairs_east")));
        assertFalse(FireBlocks.hasSolidTop(world, at(4, 5, 3)));
        world.setBlock(at(5, 5, 3), registry.require(Identifier.parse("minecraft:oak_slab_top")));
        assertTrue(FireBlocks.hasSolidTop(world, at(5, 5, 3)));
        // The hopper (any facing/enabled state).
        world.setBlock(at(6, 5, 3), registry.require(Identifier.parse("minecraft:hopper_down")));
        assertTrue(FireBlocks.hasSolidTop(world, at(6, 5, 3)));
        // Fire itself never holds anything up.
        world.setBlock(at(7, 5, 3), FireBlocks.stateOf(7));
        assertFalse(FireBlocks.hasSolidTop(world, at(7, 5, 3)));
    }

    // ------------------------------------------------------------------
    // The placement arm (onAdded lines 318-326)
    // ------------------------------------------------------------------

    @Test
    void theSupportedFlameSchedulesItsFirstTick() {
        scene();
        BlockPosition flame = at(3, 5, 3);
        world.setBlock(flame, FireBlocks.stateOf(0));
        assertNotNull(system.pendingAt(flame, FireBlocks.stateId(0)),
                "the placement scheduled the first tick");
        assertNull(system.pendingAt(flame, FireBlocks.stateId(5)),
                "the entry names the age-0 family");
        long due = system.pendingAt(flame, FireBlocks.stateId(0)).time();
        assertTrue(due - world.totalTicks() >= 30 && due - world.totalTicks() <= 39,
                "the first tick lands 30-39 out (getTickRate 30 + rand(10))");
    }

    @Test
    void theUnsupportedFlameLeavesOnPlacement() {
        scene();
        // A flame two cells above the ground with no fuel anywhere: the
        // onAdded support gate removes it immediately.
        BlockPosition flame = at(3, 7, 3);
        world.setBlock(flame, FireBlocks.stateOf(0));
        assertTrue(world.getBlock(flame).identifier().equals(world.airType().identifier()),
                "the unsupported flame never stayed");
        assertNull(system.pendingAt(flame, FireBlocks.stateId(0)),
                "nothing was scheduled for it");
    }

    @Test
    void theNeighborChangedDeathIsImmediate() {
        scene();
        BlockPosition flame = at(3, 5, 3);
        world.setBlock(flame, FireBlocks.stateOf(0));
        // The support under it vanishes (the neighbor update path — the
        // reference's FireBlock.neighborChanged, not the fire's own tick).
        world.setBlock(at(3, 4, 3), world.airType());
        world.setBlock(at(3, 5, 3), world.getBlock(flame)); // no-op commit
        assertTrue(FireBlocks.neighborChanged(world, flame),
                "the flame with neither support nor fuel dies now");
        assertTrue(world.getBlock(flame).identifier().equals(world.airType().identifier()));
    }

    // ------------------------------------------------------------------
    // The tick's own life
    // ------------------------------------------------------------------

    @Test
    void theDoFireTickGateFreezesTheFlameAfterOnePop() {
        scene();
        fireTickRule = false;
        BlockPosition flame = at(4, 5, 4);
        world.setBlock(flame, FireBlocks.stateOf(0));
        advance(45);
        // The gate wraps everything: no age bump, no death, and crucially
        // no re-schedule — the flame froze inert exactly as the reference
        // leaves it (a new placement is what re-arms it).
        assertTrue(FireBlocks.isFire(world.getBlock(flame).identifier()),
                "the flame still stands");
        assertEquals(0, FireBlocks.ageOf(world.getBlock(flame).identifier()));
        assertNull(system.pendingAt(flame, FireBlocks.stateId(0)),
                "nothing was re-armed");
    }

    @Test
    void theNoFuelDeath() {
        scene();
        // Age 5 on the bare grass floor: i > 3 dies at its first tick —
        // after the re-arm (line 165 precedes the death check, so the
        // dead flame leaves one popping entry the dispatch gate drops).
        BlockPosition old = at(5, 5, 5);
        world.setBlock(old, FireBlocks.stateOf(5));
        advance(45);
        assertTrue(world.getBlock(old).identifier().equals(world.airType().identifier()),
                "the old flame on a bare floor died");
        // The re-arm fired before the death return: the dead flame's entry
        // rides under its post-bump age family (5 or 6) until its own pop.
        assertTrue(system.pendingAt(old, FireBlocks.stateId(5)) != null
                        || system.pendingAt(old, FireBlocks.stateId(6)) != null,
                "the dead flame's entry rides until its own pop");
        advance(45);
        assertTrue(system.pendingAt(old, FireBlocks.stateId(5)) == null
                        && system.pendingAt(old, FireBlocks.stateId(6)) == null,
                "the pop dropped it at the dispatch gate");
        // Age 3 on the same floor survives (i <= 3).
        BlockPosition young = at(6, 5, 5);
        world.setBlock(young, FireBlocks.stateOf(3));
        advance(45);
        assertTrue(FireBlocks.isFire(world.getBlock(young).identifier()),
                "the young flame on a bare floor survives its first tick");
    }

    @Test
    void theRainDouseUnderOpenSky() {
        scene();
        raining = true;
        BlockPosition open = at(7, 5, 7);
        world.setBlock(open, FireBlocks.stateOf(0));
        BlockPosition roofed = at(9, 5, 7);
        world.setBlock(roofed, FireBlocks.stateOf(0));
        // A 3x3 roof: the reference's shouldExtinguish consults the cell
        // AND the four horizontal neighbors' columns — a one-block roof
        // leaves the neighbors' columns open and the flame still douses.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(at(9 + dx, 8, 7 + dz),
                        registry.require(Identifier.parse("minecraft:stone")));
            }
        }
        advance(45);
        assertTrue(world.getBlock(open).identifier().equals(world.airType().identifier()),
                "the open-sky flame was doused");
        assertTrue(FireBlocks.isFire(world.getBlock(roofed).identifier()),
                "the roofed flame was not");
    }

    @Test
    void theEternalNetherrackFlameSkipsTheFuelConsults() {
        scene();
        BlockPosition under = at(11, 4, 11);
        world.setBlock(under, registry.require(Identifier.parse("minecraft:netherrack")));
        BlockPosition flame = at(11, 5, 11);
        world.setBlock(flame, FireBlocks.stateOf(15));
        advance(45);
        // An eternal flame never consults its fuel — the max-age death
        // roll never runs, the flame stands at 15 forever.
        assertTrue(FireBlocks.isFire(world.getBlock(flame).identifier()),
                "the eternal flame stands");
        assertEquals(15, FireBlocks.ageOf(world.getBlock(flame).identifier()));
    }

    @Test
    void theAgeBumpAndReArm() {
        scene(new Random(20261020L));
        BlockPosition flame = at(12, 5, 12);
        world.setBlock(flame, FireBlocks.stateOf(0));
        advance(45);
        int age = FireBlocks.ageOf(world.getBlock(flame).identifier());
        assertTrue(age == 0 || age == 1, "the bump is i + rand(3)/2 — 0 or 1, was " + age);
        Identifier current = world.getBlock(flame).identifier();
        assertNotNull(system.pendingAt(flame, current), "the re-arm names the current state");
        long due = system.pendingAt(flame, current).time();
        assertTrue(due - world.totalTicks() >= 15,
                "the re-arm is at least 15 ticks out (30-39 from the pop)");
    }

    // ------------------------------------------------------------------
    // The scripted burn/spread walks
    // ------------------------------------------------------------------

    @Test
    void theBurnWalkDestroysTheFuel() {
        // Fire at age 0 east of a planks block; the east roll succeeds
        // (0 < 20), the young-flame arm refuses (9 >= 5): the fuel is
        // consumed outright — the reference's removeBlock arm.
        scene(new ScriptedRandom(0, 5, 0, 9, 0, 0, 0, 0, 0));
        BlockPosition fuel = at(14, 5, 14);
        world.setBlock(fuel, registry.require(Identifier.parse("minecraft:oak_planks")));
        BlockPosition flame = at(13, 5, 14);
        world.setBlock(flame, FireBlocks.stateOf(0));
        advance(45);
        assertTrue(world.getBlock(fuel).identifier().equals(world.airType().identifier()),
                "the planks burned away");
        assertTrue(FireBlocks.isFire(world.getBlock(flame).identifier()), "the flame stands");
        assertTrue(primes.isEmpty(), "no TNT was involved");
    }

    @Test
    void theBurnWalkPrimesTntOnTheFullFuse() {
        // Fire at age 0 on a TNT block; the down roll succeeds (0 < 100),
        // the young-flame arm accepts (0 < 5): the TNT cell becomes fire
        // AND the onBroken prime fires — the full-fuse primed body.
        scene(new ScriptedRandom(0, 5, 0, 0, 0, 0, 0, 0, 0, 0));
        BlockPosition tnt = at(16, 4, 16);
        world.setBlock(tnt, registry.require(Identifier.parse("minecraft:tnt")));
        BlockPosition flame = at(16, 5, 16);
        world.setBlock(flame, FireBlocks.stateOf(0));
        advance(45);
        assertTrue(FireBlocks.isFire(world.getBlock(tnt).identifier()),
                "the TNT cell became fire");
        assertEquals(1, primes.size(), "exactly one prime");
        assertEquals(tnt, primes.get(0), "the prime sits on the TNT cell");
    }

    @Test
    void theSpreadWalkLightsTheAirAboveTheFuel() {
        // Fire at age 15 east of a planks block; the east burn refuses
        // (250 >= 20), the max-age roll refuses (1 != 0), the spread box's
        // one promising cell (above the planks) accepts (0 <= 1): the air
        // above the fuel lights at the spread age 15.
        scene(new ScriptedRandom(5, 1, 250, 0, 0, 0, 0, 0, 0, 0, 0));
        BlockPosition fuel = at(18, 5, 18);
        world.setBlock(fuel, registry.require(Identifier.parse("minecraft:oak_planks")));
        BlockPosition flame = at(17, 5, 18);
        world.setBlock(flame, FireBlocks.stateOf(15));
        advance(45);
        BlockPosition above = at(18, 6, 18);
        assertEquals(FireBlocks.stateId(15), world.getBlock(above).identifier(),
                "the spread lit the air above the fuel");
        assertEquals(Identifier.parse("minecraft:oak_planks"), world.getBlock(fuel).identifier(),
                "the planks survived the refusing burn roll");
    }
}
