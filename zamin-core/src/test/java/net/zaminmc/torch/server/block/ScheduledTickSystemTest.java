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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduled block-tick queue — the 1:1 port of the reference's
 * ServerWorld machinery (the tickUpdates/scheduleUpdate family): the
 * three structures, the no-merge rule, the air time-zero quirk, the
 * thousand-entry pass cap, the horizon break, the dispatch gate, the
 * loaded-area gates, and the box query — each pinned against the
 * reference's own lines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduledTickSystemTest {

    private static final Identifier FIRE = Identifier.parse("minecraft:fire");
    private static final Identifier STONE_FAMILY = Identifier.parse("minecraft:stone");
    private static final Identifier FLOWING_WATER = Identifier.parse("minecraft:flowing_water");
    private static final Identifier AIR_FAMILY = Identifier.parse("minecraft:air");

    private FrozenBlockRegistry registry;
    private EngineWorld world;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("test", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        world.getOrGenerate(new ChunkPosition(0, 0));
    }

    /** Advances the world clock and runs one system's pass (the engine's rhythm). */
    private void advance(ScheduledTickSystem target, int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.tickTime(true);
            target.tick();
        }
    }

    @Test
    void theDrainOrderFollowsTimeThenPriorityThenInsertion() {
        ScheduledTickSystem fresh = freshSystem();
        BlockPosition late = new BlockPosition(1, 20, 1);
        BlockPosition earlySameTickLowerPriority = new BlockPosition(2, 20, 1);
        BlockPosition earlySameTickHigherPriority = new BlockPosition(3, 20, 1);
        fresh.scheduleTick(late, FIRE, 4);                       // due +4
        fresh.scheduleTick(earlySameTickLowerPriority, FIRE, 2); // due +2, priority 0 (id first)
        fresh.scheduleTick(earlySameTickHigherPriority, FIRE, 2, -1); // due +2, priority -1 (first)

        List<ScheduledTickEntry> pending = fresh.pending();
        assertEquals(3, pending.size(), "all three entries pending");
        // The TreeSet order (ServerWorld's drain): time, then priority,
        // then insertion id — the -1 priority row precedes the 0 row at
        // the same time, the +4 row sits last.
        assertEquals(earlySameTickHigherPriority, pending.get(0).pos());
        assertEquals(earlySameTickLowerPriority, pending.get(1).pos());
        assertEquals(late, pending.get(2).pos());
        assertEquals(pending.get(0).time(), pending.get(1).time(),
                "the two +2 rows share a time — the priority split them");
    }

    @Test
    void theNoMergeRuleKeepsTheFirstEntry() {
        ScheduledTickSystem fresh = freshSystem();
        BlockPosition at = new BlockPosition(4, 20, 4);
        fresh.scheduleTick(at, FIRE, 5);
        fresh.scheduleTick(at, FIRE, 1); // the reference's !contains guard: refused
        ScheduledTickEntry entry = fresh.pendingAt(at, FIRE);
        assertEquals(world.totalTicks() + 5, entry.time(),
                "the second schedule changed nothing — the first entry keeps its due time");
        assertEquals(0, entry.priority(), "the first entry keeps its priority");

        // The same position under a different family coexists (equality
        // is position + BLOCK, the reference's areEqual).
        fresh.scheduleTick(at, FLOWING_WATER, 3);
        assertEquals(2, fresh.pending().size(), "two families at one position coexist");
        assertEquals(world.totalTicks() + 3, fresh.pendingAt(at, FLOWING_WATER).time());
    }

    @Test
    void theAirQuirkCarriesTimeZero() {
        ScheduledTickSystem fresh = freshSystem();
        BlockPosition at = new BlockPosition(5, 20, 5);
        // ServerWorld lines 412-416: the material gate around
        // setTime/setPriority — an AIR-family row lands with time 0 and
        // (scheduleTick) priority 0.
        fresh.scheduleTick(at, AIR_FAMILY, 10);
        ScheduledTickEntry scheduled = fresh.pendingAt(at, AIR_FAMILY);
        assertEquals(0, scheduled.time(), "the air row carries time zero");
        assertEquals(0, scheduled.priority(), "the air row carries priority zero");
        // loadScheduledTick (lines 426-431): the priority ALWAYS, the
        // time still only for non-air.
        fresh.loadScheduledTick(at.offset(0, 1, 0), AIR_FAMILY, 10, 3);
        ScheduledTickEntry loaded = fresh.pendingAt(at.offset(0, 1, 0), AIR_FAMILY);
        assertEquals(0, loaded.time(), "the loaded air row still carries time zero");
        assertEquals(3, loaded.priority(), "the loaded air row carries its given priority");
    }

    @Test
    void futureEntriesHoldAtTheHorizon() {
        ScheduledTickSystem fresh = freshSystem();
        List<BlockPosition> fired = new ArrayList<>();
        fresh.registerTicker(FIRE, (w, pos, state, random) -> fired.add(pos));
        BlockPosition at = new BlockPosition(6, 20, 6);
        // The dispatch gate (ServerWorld lines 493-496) reads the block
        // AT the position: the fire family must occupy the cell.
        world.setBlock(at, BuiltinBlocks.FIRE);
        fresh.scheduleTick(at, FIRE, 5);
        advance(fresh, 4);
        assertTrue(fired.isEmpty(), "four ticks in, the entry holds (the horizon break)");
        assertEquals(1, fresh.size());
        advance(fresh, 1);
        assertEquals(List.of(at), fired, "the entry fires exactly at its due tick");
        assertEquals(0, fresh.size(), "the queue is empty after the fire");
        // The scene clears behind the test (no listener wired in the test world).
        world.setBlock(at, world.airType());
    }

    @Test
    void thePassCapDrainsOneThousandPerPass() {
        ScheduledTickSystem fresh = freshSystem();
        int[] fires = {0};
        fresh.registerTicker(FIRE, (w, pos, state, random) -> fires[0]++);
        for (int i = 0; i < 1200; i++) {
            // 75 rows of 16 above the flat ground: y 5..79 — all inside
            // chunk (0,0), all distinct, all carrying the fire family for
            // the dispatch gate.
            BlockPosition at = new BlockPosition(i & 0xF, 5 + (i >> 4), 1);
            world.setBlock(at, BuiltinBlocks.FIRE);
            fresh.scheduleTick(at, FIRE, 0);
        }
        assertEquals(1200, fresh.size());
        world.tickTime(true);
        fresh.tick();
        assertEquals(1000, fires[0], "the pass cap: one thousand per pass (ServerWorld lines 467-469)");
        assertEquals(200, fresh.size(), "the remaining two hundred hold for the next pass");
        world.tickTime(true);
        fresh.tick();
        assertEquals(1200, fires[0], "the next pass drains the rest");
        assertEquals(0, fresh.size());
        // The scene clears behind the test.
        for (int i = 0; i < 1200; i++) {
            world.setBlock(new BlockPosition(i & 0xF, 5 + (i >> 4), 1), world.airType());
        }
    }

    @Test
    void theDispatchGateDropsReplacedBlocks() {
        ScheduledTickSystem fresh = freshSystem();
        List<BlockPosition> fired = new ArrayList<>();
        fresh.registerTicker(FIRE, (w, pos, state, random) -> fired.add(pos));
        BlockPosition at = new BlockPosition(7, 20, 7);
        world.setBlock(at, BuiltinBlocks.FIRE);
        fresh.scheduleTick(at, FIRE, 2);
        // The family replaced between schedule and fire: the entry pops,
        // the gate refuses (ServerWorld lines 493-496), nothing re-arms.
        world.setBlock(at, BuiltinBlocks.STONE);
        advance(fresh, 3);
        assertTrue(fired.isEmpty(), "a replaced block never fires its pending tick");
        assertEquals(0, fresh.size(), "the entry dropped at the gate — no re-arm");
        world.setBlock(at, world.airType());
    }

    @Test
    void theTickerSeesThePassListMinusItsOwnRow() {
        ScheduledTickSystem fresh = freshSystem();
        List<String> answers = new ArrayList<>();
        // A ticker that consults willTickThisTick during its own fire
        // (the reference's iterator.remove() precedes the tick call —
        // the firing row is GONE from the this-tick list, later rows
        // still answer true).
        fresh.registerTicker(FIRE, (w, pos, state, random) -> {
            answers.add("own=" + fresh.willTickThisTick(pos, FIRE)
                    + ",other=" + fresh.willTickThisTick(pos.offset(1, 0, 0), STONE_FAMILY));
        });
        fresh.registerTicker(STONE_FAMILY, (w, pos, state, random) -> {
            answers.add("stone fired");
        });
        BlockPosition fireAt = new BlockPosition(8, 20, 8);
        BlockPosition stoneAt = fireAt.offset(1, 0, 0);
        world.setBlock(fireAt, BuiltinBlocks.FIRE);
        world.setBlock(stoneAt, BuiltinBlocks.STONE);
        fresh.scheduleTick(fireAt, FIRE, 1);
        fresh.scheduleTick(stoneAt, STONE_FAMILY, 1);
        advance(fresh, 1);
        assertEquals(List.of("own=false,other=true", "stone fired"), answers,
                "the fire's own row is removed before its tick; the stone's row still answers");
        world.setBlock(fireAt, world.airType());
        world.setBlock(stoneAt, world.airType());
    }

    @Test
    void theLoadedGatesRefuseAndLoad() {
        ScheduledTickSystem fresh = freshSystem();
        // A position in a chunk that was never generated: scheduleTick's
        // isAreaLoaded gate (ServerWorld line 412 at radius 0) refuses.
        BlockPosition unloaded = new BlockPosition(100, 20, 100); // chunk (6,6) — never generated
        fresh.scheduleTick(unloaded, FIRE, 2);
        assertNull(fresh.pendingAt(unloaded, FIRE),
                "scheduleTick refuses an unloaded area (no entry added)");
        // loadScheduledTick (the anvil/import arm) has no loaded gate:
        // the row lands regardless.
        fresh.loadScheduledTick(unloaded, FIRE, 2, 1);
        assertEquals(world.totalTicks() + 2, fresh.pendingAt(unloaded, FIRE).time(),
                "the import arm loads the row without a loaded gate");
    }

    @Test
    void flushIgnoresTheHorizon() {
        ScheduledTickSystem fresh = freshSystem();
        List<BlockPosition> fired = new ArrayList<>();
        fresh.registerTicker(FIRE, (w, pos, state, random) -> fired.add(pos));
        BlockPosition at = new BlockPosition(9, 20, 9);
        world.setBlock(at, BuiltinBlocks.FIRE);
        fresh.scheduleTick(at, FIRE, 50);
        fresh.doScheduledTicks(true);
        assertEquals(List.of(at), fired,
                "flush=true pops future-due entries (the reference's flush arm, line 475)");
        assertEquals(0, fresh.size());
        world.setBlock(at, world.airType());
    }

    @Test
    void theBoxQueryCollectsInOrderAndRemoves() {
        ScheduledTickSystem fresh = freshSystem();
        // The grown box of chunk (0,0) (ServerWorld lines 515-522):
        // minX = (0<<4)-2 = -2, maxX = -2+16+2 = 16 — the x/z band is
        // [-2, 16). Y is ignored by the reference's own bounds walk
        // (line 539 checks x and z alone).
        fresh.loadScheduledTick(new BlockPosition(-2, 10, -2), FIRE, 3, 0);   // inside
        fresh.loadScheduledTick(new BlockPosition(15, 200, 15), FIRE, 3, 0);  // inside, y ignored
        // Outside the box (x = 16 is not < 16):
        fresh.loadScheduledTick(new BlockPosition(16, 10, 0), FIRE, 3, 0);
        List<ScheduledTickEntry> box = fresh.getScheduledTicks(new ChunkPosition(0, 0), true);
        assertEquals(2, box.size(), "the grown box collected the two inside rows (y ignored)");
        assertEquals(1, fresh.size(), "the outside row survives the remove");
        assertNull(fresh.pendingAt(new BlockPosition(-2, 10, -2), FIRE),
                "the removed row is gone from the queue");
    }

    @Test
    void theReturnAnswersWhetherMoreRemain() {
        ScheduledTickSystem fresh = freshSystem();
        assertFalse(fresh.doScheduledTicks(false),
                "the empty queue answers false (the reference's !isEmpty return)");
        fresh.scheduleTick(new BlockPosition(10, 20, 10), FIRE, 1);
        assertTrue(fresh.doScheduledTicks(false),
                "the future-due entry holds in the queue — more remain");
        // The tick() fan arm returns void; the boolean surface stays for
        // the flush callers (the reference's own shape).
    }

    /** A fresh system over the shared world (per-test isolation). */
    private ScheduledTickSystem freshSystem() {
        return new ScheduledTickSystem(world, new Random(1));
    }
}
