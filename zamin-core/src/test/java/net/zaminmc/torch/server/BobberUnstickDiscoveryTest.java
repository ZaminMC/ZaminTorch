package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.fishing.Fishing;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-038's behavioral seat (pass 2): the fishing bobber's STUCK law —
 * the reference's un-stick cycle (FishingBobberEntity.tick lines
 * 214-232) against the engine's frozen stuck branch.
 *
 * <p><b>THE LAW (the reference, read verbatim in the pass-1 record):</b>
 * the stuck bobber consults its stamped cell every tick, and the live
 * stamp is NEVER written (the fields' only other touch is the NBT rows
 * at 424-447) — so the live consult ALWAYS mismatches and the live
 * bobber un-sticks EVERY tick: {@code inGround = false}, the per-axis
 * {@code velocity *= random.nextFloat() * 0.2F} damping, the
 * {@code inBlockTicks = 0} and {@code inAirTicks = 0} resets, and the
 * SAME tick falls through into the rayTrace, the move, the 5-band water
 * survey and the catchable cycle. The 1200-tick removal fires only on a
 * MATCHING consult — a bobber restored from the NBT rows — so the LIVE
 * bobber's clock resets to 0 every tick and the timeout is never
 * reached.</p>
 *
 * <p><b>ARM A — the broken-block fall (the lily-pad technique):</b> a
 * pool whose surface cell carries a DIRT lid (solid for the block
 * raytrace, soft for the dig wire), the cast grounds the bobber on the
 * lid, and the 3×3 lid neighborhood breaks through the REAL dig wire.
 * The reference: nothing holds the bobber up — the un-stick cycle falls
 * it with the damped velocity and the −0.04 gravity term into the water
 * below, the 5-band survey reads the fraction, and the catchable cycle
 * arms: the bite opens, and the retrieve inside the window lands the
 * catch. The engine today: the bobber is FROZEN at its stick position —
 * no fall, no cycle, no bite, and the retrieve answers the grounded 2
 * for a block that no longer exists.</p>
 *
 * <p><b>ARM B — the live 1200-tick timeout:</b> a dry cast onto the
 * natural terrain, grounded, then 1300 ticks with the rod held and the
 * angler still. The reference: the live clock resets every tick — the
 * bobber still sits at tick 1300 (the rod gate and the 32-block leash
 * alone govern its life). The engine today: the unconditional clock
 * removes the bobber at exactly 1200.</p>
 */
class BobberUnstickDiscoveryTest {

    @TempDir
    Path dataDir;

    /** The current test's engine — the tick clock the shared await reads. */
    private EngineServer activeServer;

    // ------------------------------------------------------------------
    // ARM A — the broken-block fall, the bite, the catch
    // ------------------------------------------------------------------

    @Test
    void theBrokenBlockFallsTheBobberAndTheBiteOpens() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "LilyAngler");
        Fishing fishing = server.fishing();

        // The pool: two cells deep, the corridor cleared (the
        // FishingApiAcceptance staging — the water-fraction consult's
        // own ground truth).
        Position pool = buildPoolAhead(server, angler, 2.0);
        int baseY = (int) Math.floor(pool.y());

        // The dirt lid: the pool's whole surface cell carries DIRT (the
        // raytrace's solid, the dig wire's soft target) — the water one
        // cell deep below it stays (the pool's second cell).
        var dirt = server.blockRegistry()
                .lookup(net.zaminmc.torch.util.Identifier.parse("minecraft:dirt"))
                .orElseThrow();
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = 0; dz <= 20; dz++) {
                    server.world().setBlock(new BlockPosition(
                            (int) Math.floor(pool.x()) + dx, baseY,
                            (int) Math.floor(pool.z()) + dz), dirt);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().getBlock(new BlockPosition(
                                (int) Math.floor(pool.x()), baseY,
                                (int) Math.floor(pool.z())))
                        .identifier().value().equals("dirt"),
                100, "the pool's surface carries the dirt lid");

        // The cast: the bobber's lob descends onto the lid and sticks.
        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);
        server.useItem(angler);
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                200, "the cast registered");
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId())
                        .map(Fishing.Bobber::grounded).orElse(false),
                200, "the bobber stuck on the dirt lid (the control)");
        Position rest = fishing.bobberOf(angler.engineEntityId()).orElseThrow().at();
        int lidX = (int) Math.floor(rest.x());
        int lidZ = (int) Math.floor(rest.z());

        // The angler walks to the lid's neighborhood (the dig wire's own
        // 4.5-block reach band) — the rod stays held (the bobber's gate).
        // The movement declares the ground state through the engine's own
        // verb (the applyMovement idiom — the teleported body carries no
        // server-side physics, and the dig's per-tick progress consult
        // reads the live posture: an airborne digger pays the ÷5 penalty).
        server.teleportPlayer(angler, new Position(
                lidX + 0.5, baseY + 1.0, lidZ + 0.5));
        EngineAwaits.awaitTicks(server.world(), 5, "the angler settles on the lid");
        angler.applyMovement(angler.position(), angler.rotation(), true);

        // The 3×3 lid neighborhood breaks through the REAL dig wire —
        // the block the bobber rests on (and its drift neighbors) gone,
        // the one-deep water below opened.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPosition target = new BlockPosition(lidX + dx, baseY, lidZ + dz);
                server.blockInteraction().submitMiningStart(angler, target);
                EngineAwaits.awaitTicks(server.world(), 16,
                        "the dig accumulates past the 0.7 accept band (dirt)");
                server.blockInteraction().submitMiningFinished(angler, target);
                EngineAwaits.awaitCondition(server.world(),
                        () -> server.world().getBlock(target)
                                .equals(server.world().airType()),
                        40, "the lid cell " + target + " broke");
            }
        }

        // PIN 1 — THE FALL (the reference's un-stick cycle: nothing holds
        // the bobber up once its block is gone; the damped fall plus the
        // −0.04 gravity term carries it into the water below — a drop of
        // more than half a block within the settle window). The engine
        // today: the frozen bobber never moves — RED here.
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId())
                        .map(bobber -> bobber.at().y() < rest.y() - 0.5)
                        .orElse(false),
                100, "the bobber fell off the broken lid (y below "
                        + (rest.y() - 0.5) + ", the rest was " + rest.y() + ")");

        // PIN 2 — THE BITE (the fall-through's own reach: the 5-band
        // water survey reads the fraction in the opened water, the
        // catchable cycle arms, the splash opens the 10-30-tick window —
        // the l-scaled cadence's own budget, the FishingApiAcceptance
        // seat's 2400-tick math). The engine today: the frozen bobber
        // never runs the cycle — the window never opens.
        boolean bit = EngineAwaits.awaitConditionWithin(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId())
                        .map(Fishing.Bobber::biting).orElse(false),
                2_400, "the bite window opened over the opened water");
        assertTrue(bit, "VT-038 ARM A: the broken-block bobber must fall into the"
                + " water and open the bite window (the reference's fall-through"
                + " reaches the catchable cycle); the engine's stuck branch froze"
                + " it — no fall, no cycle, no bite");

        // PIN 3 — THE CATCH (the retrieve inside the window lands the
        // roll's drop — the vanilla fish band's item entity in the world
        // census, the drop's own observable before any pickup). The
        // engine today: the frozen bobber's retrieve answers the grounded
        // 2 — no drop ever.
        server.useItem(angler);
        EngineAwaits.awaitCondition(server.world(),
                () -> server.itemEntities().all().stream().anyMatch(item ->
                        item.dimension() == angler.dimension()
                                && item.stack().type().identifier().value().equals("fish")),
                200, "the catch's fish item dropped at the retrieve");
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                100, "the reeled-in line left the census");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------
    // ARM B — the live 1200-tick timeout
    // ------------------------------------------------------------------

    @Test
    void theLiveStuckBobberOutlivesTheTimeout() throws Exception {
        EngineServer server = boot();
        PlayerSession angler = join(server, "DryAngler");
        Fishing fishing = server.fishing();

        // The dry cast onto the natural terrain — the bobber grounds
        // somewhere in the corridor (the FishingApiAcceptance dry-cast
        // seat's own law).
        grantHeld(angler, ItemStack.of(BuiltinItems.FISHING_ROD));
        aimHorizontally(server, angler, 2.0);
        server.useItem(angler);
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId()).isPresent(),
                200, "the cast registered");
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId())
                        .map(Fishing.Bobber::grounded).orElse(false),
                400, "the dry bobber grounded (the control)");

        // 1300 ticks with the rod held and the angler still — past the
        // engine's unconditional 1200-tick clock. The reference: the live
        // un-stick cycle resets the clock EVERY tick (the never-written
        // stamp's consult always mismatches), so the timeout belongs to
        // the NBT-restored bobber alone — the live bobber still sits at
        // tick 1300. The engine today: the bobber despawned at exactly
        // 1200 — RED here.
        EngineAwaits.awaitTicks(server.world(), 1_300,
                "the stuck window runs past the unconditional 1200 clock");

        assertTrue(fishing.bobberOf(angler.engineEntityId()).isPresent(),
                "VT-038 ARM B: the live stuck bobber must still sit at tick 1300"
                        + " (the reference's un-stick cycle resets the stuck clock"
                        + " every tick — the 1200 removal belongs to the"
                        + " NBT-restored bobber alone); the engine's unconditional"
                        + " clock despawned it");

        // The post-control: the long-stuck bobber still retrieves (the
        // census empties — the rod gate and the leash govern its life).
        server.useItem(angler);
        EngineAwaits.awaitCondition(server.world(),
                () -> fishing.bobberOf(angler.engineEntityId()).isEmpty(),
                100, "the long-stuck line still reeled in");
        assertEquals(0, fishing.bobbers().size(), "the census emptied");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness
    // (the FishingApiAcceptanceTest idiom, carried whole)

    /** A rod granted straight into the held hotbar slot. */
    private void grantHeld(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the rod granted into the held slot");
    }

    /** Aims the angler horizontally at the staging built `ahead` blocks out. */
    private void aimHorizontally(EngineServer server, PlayerSession player, double ahead)
            throws InterruptedException {
        Position at = player.position();
        double dx = 0.0;
        double dz = ahead;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        player.applyMovement(at, new Rotation(yaw, -12.0f),
                player.onGround());
    }

    /**
     * A 5×5 water pool (two cells deep) built {@code ahead} blocks out on
     * the +z axis — the FishingApiAcceptance staging verbatim (the
     * corridor cleared above so no terrain swallows the water or
     * intercepts the bobber).
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
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -1; dz <= 20; dz++) {
                    for (int dy = 1; dy <= 4; dy++) {
                        server.world().setBlock(new BlockPosition(
                                baseX + dx, baseY + dy, baseZ + dz), air);
                    }
                    if (dz >= 0) {
                        server.world().setBlock(new BlockPosition(
                                baseX + dx, baseY, baseZ + dz), water);
                        server.world().setBlock(new BlockPosition(
                                baseX + dx, baseY - 1, baseZ + dz), water);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.FluidBlocks.kindOf(
                                server.world().getBlock(new BlockPosition(
                                        baseX, baseY, baseZ)).identifier())
                        == net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER,
                100, "the pool's water reads through the block identifiers");
        return new Position(baseX + 0.5, baseY + 0.5, baseZ + 0.5);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "bobberunstick", "it",
                20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        activeServer = server;
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        EngineAwaits.awaitCondition(server.world(),
                () -> server.playerRegistry().byName(name).isPresent(),
                400, name + " registered");
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == PlayerState.PLAYING,
                400, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
    }
}
