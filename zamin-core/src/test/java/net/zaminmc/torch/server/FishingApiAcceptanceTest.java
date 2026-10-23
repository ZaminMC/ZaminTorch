package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.fishing.Fishing;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The fishing api's §27.10 capability proof (the coordinator's PR-#32
 * correction — the lane's landing commit cited this family but never
 * committed it): every row through {@link Server#fishing()} alone — the
 * booted facade answering the live view, the census carrying the
 * reference's own state model (the cast's flight, the grounded stick,
 * the water float's bite window), the §27.7 interception family (the
 * context rows, the replacement, the idempotent add, the removal
 * restore, the cancel's bare retrieve), the loot override's band answer
 * and restore, and the §27.6 foreign-thread census — the staging rides
 * the engine's own public verbs (the rod's useItem press, the water
 * pool through setBlock, the teleport), never an engine cast.
 */
class FishingApiAcceptanceTest {

    @TempDir
    Path dataDir;

    /** The current test's engine — the tick clock the shared await reads. */
    private EngineServer activeServer;

    @Test
    void theBootedFacadeAnswersTheLiveView() throws Exception {
        EngineServer server = boot();
        Server api = server;
        Fishing fishing = api.fishing();
        assertNotNull(fishing, "the booted server answers the fishing view");
        assertTrue(fishing != Fishing.EMPTY,
                "the booted facade is the live view, not the EMPTY default");
        assertTrue(fishing.bobbers().isEmpty(),
                "a fresh world carries no bobbers");
        assertTrue(fishing.bobberOf(1).isEmpty(),
                "an unknown angler answers empty");
        server.shutdown(null);
    }

    @Test
    void theDryCastRegistersInTheCensusAndGrounds() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "DryAngler");
        Fishing fishing = server.fishing();

        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 1.0);
        server.useItem(angler); // the empty hand casts

        // The census row lands within the flight: the api's own angler
        // consult (§27.5 — the live row, the angler's own engine id).
        await(() -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                "the cast registered in the api census");
        Fishing.Bobber row = fishing.bobberOf(angler.engineEntityId()).orElseThrow();
        assertEquals(angler.engineEntityId(), row.anglerId(),
                "the census row carries the angler's own engine id");
        assertFalse(row.hooked(), "the dry cast hooks nothing");
        assertFalse(row.biting(), "the dry cast never opens the bite window");

        // The flight ends stuck in the ground (the block raytrace's own
        // state — the 1200-tick timeout's arm).
        await(() -> fishing.bobberOf(angler.engineEntityId())
                        .map(Fishing.Bobber::grounded).orElse(false),
                "the dry bobber grounded");
        assertEquals(1, fishing.bobbers().size(),
                "the census carries exactly the one bobber");

        // The retrieve empties the census (the leash end).
        server.useItem(angler); // the live bobber retrieves
        await(() -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                "the reeled-in line left the census");
        assertTrue(fishing.bobbers().isEmpty(), "the census emptied");
        server.shutdown(null);
    }

    @Test
    void theWaterFloatOpensTheBiteWindowAndTheCatchLands() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "Angler");
        Fishing fishing = server.fishing();
        Position pool = buildPoolAhead(server, angler, 2.0);
        Position home = angler.position();

        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);
        server.useItem(angler); // the cast toward the pool

        await(() -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                "the cast registered");
        // The coordinator's review correction (this lane's own catch, the
        // form that actually bit): the cadence is NOT 930 ticks worst-case.
        // The arming AND the approach countdowns are l-scaled
        // (FishingSystem.tickWater: catchTimer -= l, fishTravelTimer -= l)
        // and l collapses to 0 on the no-sky-access ticks (the 50% roll),
        // so a float whose pool sits under blocking terrain spends up to
        // 2x the nominal on both walks — whether that happens varies with
        // the worldgen seed's terrain above the pool, which is exactly the
        // 50/50 flake this lane observed across machines (two green passes
        // there, two deterministic failures + one lucky green on the
        // coordinator's). The honest worst case is ~2000 ticks (degraded
        // arming 1800 + approach 160 + the flight); 2400 covers it with
        // margin. Detection is unchanged — a genuine bite is the only pass.
        awaitBite(server, fishing, angler, "the splash opened the bite window");
        Fishing.Bobber biting = fishing.bobberOf(angler.engineEntityId()).orElseThrow();
        assertFalse(biting.grounded(), "the water float never grounds");
        assertEquals(angler.engineEntityId(), biting.anglerId(),
                "the biting row stays anchored to the angler");

        // The retrieve INSIDE the window lands the catch: the vanilla
        // fish band's drop flies to the angler — the api inventory
        // answers the pickup (the player teleported onto the bobber's
        // own rest). The retry helper re-casts when a loaded runner's
        // submit lag closes the 10-30-tick window first (the bare water
        // retrieve never wears the rod, so retries are honest).
        retrieveInWindow(server, fishing, angler, home,
                () -> angler.inventory().countOf(BuiltinItems.FISH) > 0);
        await(() -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                "the reeled-in line left the census");
        server.shutdown(null);
    }

    @Test
    void theInterceptorSeesTheContextAndReplacesTheCatch() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "HookAngler");
        Fishing fishing = server.fishing();
        buildPoolAhead(server, angler, 2.0);

        Position home = angler.position();
        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);

        // The recording + replacing interceptor: the context rows land
        // (the angler, the bobber's position, the band, the bare rod's
        // 0/0 levels) and the diamond replaces the roll.
        AtomicReference<Fishing.CatchContext> seen = new AtomicReference<>();
        Fishing.CatchInterceptor replacing = context -> {
            seen.set(context);
            return ItemStack.of(BuiltinItems.DIAMOND);
        };
        fishing.addCatchInterceptor(replacing);
        fishing.addCatchInterceptor(replacing); // the idempotent add

        server.useItem(angler); // the cast
        retrieveInWindow(server, fishing, angler, home, () -> seen.get() != null);
        await(() -> angler.inventory().countOf(BuiltinItems.DIAMOND) > 0,
                "the replaced catch (a diamond) reached the angler");
        Fishing.CatchContext context = seen.get();
        assertEquals(angler.engineEntityId(), context.anglerId(),
                "the context carries the angler's own id");
        assertNotNull(context.bobberAt(), "the context carries the bobber's position");
        assertNotNull(context.category(), "the context carries the loot band");
        assertEquals(0, context.luckLevel(), "the bare rod's luck level is 0");
        assertEquals(0, context.lureLevel(), "the bare rod's lure level is 0");

        // The removal restores the vanilla roll: the next catch passes
        // through untouched (a fish band drop, never the replacement).
        assertTrue(fishing.removeCatchInterceptor(replacing),
                "the removal answered registered");
        assertFalse(fishing.removeCatchInterceptor(replacing),
                "the second removal answers unregistered");
        server.teleportPlayer(angler, home); // back to the pool's near edge
        retrieveInWindow(server, fishing, angler, home,
                () -> angler.inventory().countOf(BuiltinItems.FISH) > 0);
        server.shutdown(null);
    }

    @Test
    void theInterceptorCancelBaresTheRetrieve() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "CancelAngler");
        Fishing fishing = server.fishing();
        buildPoolAhead(server, angler, 2.0);

        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);
        int rodDamageBefore = angler.inventory().held().damage();

        fishing.addCatchInterceptor(context -> ItemStack.EMPTY); // the cancel
        server.useItem(angler); // the cast
        awaitBite(server, fishing, angler, "the bite window opened");
        server.teleportPlayer(angler, poolFloor(
                fishing.bobberOf(angler.engineEntityId()).orElseThrow().at()));
        server.useItem(angler); // the retrieve inside the window

        // The cancelled catch drops nothing and the bare-0 retrieve
        // wears nothing (the reference's own result > 0 gate).
        await(() -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                "the cancelled retrieve still reeled the line in");
        EngineAwaits.awaitTicks(server.world(), 10, "the pickup's own grace");
        assertEquals(0, angler.inventory().countOf(BuiltinItems.FISH),
                "the cancelled catch dropped no fish");
        assertEquals(rodDamageBefore, angler.inventory().held().damage(),
                "the bare-0 retrieve never wears the rod");
        server.shutdown(null);
    }

    @Test
    void theLootOverrideAnswersTheBandAndTheRestore() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "LootAngler");
        Fishing fishing = server.fishing();
        buildPoolAhead(server, angler, 2.0);

        Position home = angler.position();
        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);

        // The fixed-source override: every band answers the same
        // emerald; the null per-band fall-through and the null restore
        // are the family's own rows.
        AtomicInteger rolls = new AtomicInteger();
        Fishing.LootSource fixed = (category, luck, lure, random) -> {
            rolls.incrementAndGet();
            return ItemStack.of(BuiltinItems.EMERALD);
        };
        fishing.setLootSource(fixed);
        retrieveInWindow(server, fishing, angler, home,
                () -> angler.inventory().countOf(BuiltinItems.EMERALD) > 0);
        assertTrue(rolls.get() >= 1, "the override was consulted");

        // The restore: null hands the walk back to the vanilla tables.
        fishing.setLootSource(null);
        server.teleportPlayer(angler, home); // back to the pool's near edge
        int emeraldsBefore = angler.inventory().countOf(BuiltinItems.EMERALD);
        retrieveInWindow(server, fishing, angler, home,
                () -> angler.inventory().countOf(BuiltinItems.FISH) > 0);
        assertEquals(emeraldsBefore, angler.inventory().countOf(BuiltinItems.EMERALD),
                "the restored walk never consults the retired override");
        server.shutdown(null);
    }

    @Test
    void theForeignThreadCensusNeverTears() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "HammerAngler");
        Fishing fishing = server.fishing();
        buildPoolAhead(server, angler, 2.0);

        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);
        server.useItem(angler); // the cast

        // The §27.6 hammer: a foreign thread hammers the census while
        // the tick lane walks the flight — every row a complete
        // snapshot, never a half-written walk, never a throw.
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread hammer = new Thread(() -> {
            try {
                for (int i = 0; i < 2_000; i++) {
                    List<Fishing.Bobber> rows = fishing.bobbers();
                    for (Fishing.Bobber row : rows) {
                        assertNotNull(row.at());
                        row.velocityX();
                        row.grounded();
                    }
                    fishing.bobberOf(angler.engineEntityId());
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        hammer.start();
        long deadline = System.currentTimeMillis() + 2_000;
        while (hammer.isAlive() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        hammer.join(1_000);
        assertEquals(null, failure.get(),
                "the foreign-thread census never threw (the monitor's snapshot protocol)");
        await(() -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                "the census still answers after the hammer");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    /** A rod granted straight into the held hotbar slot. */
    private void grantHeld(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the rod granted into the held slot");
    }

    /** Aims the angler horizontally at the pool built `ahead` blocks out. */
    private void aimHorizontally(EngineServer server, PlayerSession player, double ahead)
            throws InterruptedException {
        Position at = player.position();
        // The pool sits at +z from the angler (yaw 0's own facing); the
        // look rides the engine's yaw convention (atan2(-dx, dz)).
        double dx = 0.0;
        double dz = ahead;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.applyMovement(at, new net.zaminmc.torch.util.Rotation(yaw, -12.0f),
                player.onGround());
    }

    /**
     * A 5×5 water pool (two cells deep) built {@code ahead} blocks out
     * on the +z axis at the angler's own ground level, through the
     * engine's own setBlock ladder — the staging the water-fraction
     * consult reads (the 5-band walk over the block identifiers).
     */
    private Position buildPoolAhead(EngineServer server, PlayerSession player, double ahead)
            throws InterruptedException {
        Position at = player.position();
        int baseX = (int) Math.floor(at.x());
        int baseY = (int) Math.floor(at.y());
        int baseZ = (int) Math.floor(at.z()) + (int) ahead;
        var water = server.blockRegistry()
                .lookup(net.zaminmc.torch.server.block.FluidBlocks.WATER_SOURCE)
                .orElseThrow();
        var air = server.world().airType();
        // The pool: two cells deep, spanning the cast's whole lob range
        // (the near edge two blocks out to the far edge eight out); the
        // column above AND the flight corridor cleared so no terrain
        // swallows the water or intercepts the bobber (the world's own
        // ground level at the angler's feet).
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -1; dz <= 20; dz++) {
                    // the corridor above the pool: air up to head height
                    for (int dy = 1; dy <= 4; dy++) {
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(
                                        baseX + dx, baseY + dy, baseZ + dz), air);
                    }
                    if (dz >= 0) {
                        // the pool itself: two deep, flush with the ground,
                        // spanning the lob's WHOLE range (a short throw
                        // floats at the near edge instead of grounding on
                        // the dry corridor floor)
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(
                                        baseX + dx, baseY, baseZ + dz), water);
                        server.world().setBlock(
                                new net.zaminmc.torch.block.BlockPosition(
                                        baseX + dx, baseY - 1, baseZ + dz), water);
                    }
                }
            }
        });
        await(() -> net.zaminmc.torch.server.block.FluidBlocks.kindOf(
                        server.world().getBlock(new net.zaminmc.torch.block.BlockPosition(
                                baseX, baseY, baseZ)).identifier())
                == net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER,
                "the pool's water reads through the block identifiers");
        return new Position(baseX + 0.5, baseY + 0.5, baseZ + 0.5);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "fishingapi", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        activeServer = server;
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
    }

    /**
     * The caught item sinks to the pool floor (the two-cell water
     * column): the angler teleports to the floor so the box-grown pickup
     * range (1.0 horizontal, 0.5 vertical) holds the item wherever it
     * rests between the floor and the surface.
     */
    private Position poolFloor(Position bobberAt) {
        return new Position(bobberAt.x(), bobberAt.y() - 1.6, bobberAt.z());
    }

    /**
     * The bite window's own await: the catch arm rolls 100-900 ticks
     * (the Lure-cut cadence — up to 45 seconds bare) before the approach
     * (20-80) opens the 10-30-tick window. The bound is TICK-SCALED, not
     * wall: the 90s wall form (PR #37's raise) still tripped under a
     * loaded runner in this lane's own verification batch — the cadence
     * is 930 ticks worst-case, and a slow engine spends more wall time
     * per tick, so the budget counts ENGINE ticks (2400 = the l-scaled
     * cadence's worst case + margin) and never expires by wall time alone
     * (EngineAwaits keeps a stalled-engine backstop).
     */
    private void awaitBite(EngineServer server, Fishing fishing, PlayerSession angler,
            String description) throws InterruptedException {
        // 2400 ticks: the l-scaled cadence's honest worst case (see the
        // first test's comment — the coordinator's review correction over
        // this lane's original 1500).
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId())
                        .map(Fishing.Bobber::biting).orElse(false),
                2_400, description);
    }

    /**
     * The retrieve inside the bite window, robust to a loaded runner: the
     * window is only 10-30 ticks wide, and the teleport + use submits can
     * land after it closes under CI load — a missed window answers the
     * bare water retrieve (result 0, no catch, the rod never wears), so
     * the cycle simply re-casts from the angler's home cell (the pool's
     * near edge — a re-cast from the pool floor would aim at dry land
     * and never open a window). Up to four attempts; each attempt awaits
     * the bite window first, and the settle poll watches the caller's own
     * success probe (the catch's pickup flight included).
     */
    private void retrieveInWindow(EngineServer server, Fishing fishing, PlayerSession angler,
            Position home, BooleanSupplier successProbe) throws InterruptedException {
        for (int attempt = 1; attempt <= 4; attempt++) {
            if (fishing.bobberOf(angler.engineEntityId()).isEmpty()) {
                // A cast may still be in flight (the caller's own useItem
                // submits to the tick lane): give it a grace window
                // BEFORE re-casting — an immediate second useItem would
                // retrieve the fresh bobber instead of casting.
                long grace = System.currentTimeMillis() + 3_000;
                while (fishing.bobberOf(angler.engineEntityId()).isEmpty()
                        && System.currentTimeMillis() < grace) {
                    Thread.sleep(50);
                }
                if (fishing.bobberOf(angler.engineEntityId()).isEmpty()) {
                    server.teleportPlayer(angler, home); // back to the pool's near edge
                    server.useItem(angler); // the cast (none in flight)
                }
                await(() -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                        "the cast registered (attempt " + attempt + ")");
            }
            // The bite await — the EXPECTED-MISS form inside the retry
            // structure (the run #375/#376 lesson: BOTH observed fishing
            // failures died here, the throw defeating the re-cast design
            // the loop itself exists for): a budget exhaustion is a failed
            // ATTEMPT, never a dead test — the next attempt re-casts from
            // the pool's near edge, and the final assert below carries the
            // story if all four attempts miss.
            if (!net.zaminmc.torch.testing.EngineAwaits.awaitConditionWithin(
                    server.world(),
                    () -> fishing.bobberOf(angler.engineEntityId())
                            .map(Fishing.Bobber::biting).orElse(false),
                    2_400, "the bite window opened (attempt " + attempt + ")")) {
                // The dead attempt's line comes home first: a bobber left
                // out owns the census seat, and the re-cast path below only
                // fires on an empty one — a dry-land or cycle-stuck bobber
                // would otherwise eat every remaining attempt's budget.
                server.useItem(angler); // the retrieve (the bare-water answer)
                await(() -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                        "the dead attempt's line left the census");
                continue;
            }
            server.teleportPlayer(angler, poolFloor(
                    fishing.bobberOf(angler.engineEntityId()).orElseThrow().at()));
            server.useItem(angler); // the retrieve inside the window
            long settle = System.currentTimeMillis() + 8_000;
            while (System.currentTimeMillis() < settle) {
                if (successProbe.getAsBoolean()) {
                    return;
                }
                Thread.sleep(50);
            }
            // The window closed first (or the catch is still settling):
            // the loop re-casts — the bare retrieve left no debt behind.
        }
        throw new AssertionError(
                "Condition not met in time: the bite window never opened nor the catch"
                        + " land across the retrieve retries");
    }

    /**
     * The engine-tick await (the run #375/#376 conversion — the census's
     * own rule extended to this class's local helper): the old 20s wall
     * deadline expired by wall time on a loaded runner even while the
     * engine was alive and progressing. The budget is the wall form's
     * nominal tick equivalent (20s = 400 ticks at 20 TPS).
     */
    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        net.zaminmc.torch.testing.EngineAwaits.awaitCondition(
                activeServer.world(), condition, 400, description);
    }
}
