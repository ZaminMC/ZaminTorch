package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FireBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S TWENTY-FIRST-PASS DISCOVERY SEATS (findings VT-001/VT-002/
 * VT-003 of docs/vanilla-conformance-audit.md — the FL4 wave's three
 * demand-named dimension tails, plus the ice family's melt/break arms).
 *
 * <p>Every seat asserts the REFERENCE behavior (reference/1.8.8
 * net/minecraft/block/*.java), NOT the engine's current behavior: these
 * seats are EXPECTED RED on develop until their per-dimension/ice-family
 * passes land. They are committed on the audit branch as the executable
 * statement of each finding — the fix branch lifts them green and rides
 * them as the regression seats. Do not delete or re-pin a seat to the
 * engine's current answer to make it pass; the finding record owns the
 * mismatch.</p>
 *
 * <p>Seats in this file:</p>
 * <ul>
 *   <li>VT-002 — {@code theNetherDigBreaksTheNetherBlockNotTheOverworldCell}:
 *       a survival dig submitted by a player STANDING IN THE NETHER must
 *       read and write the NETHER cell (the reference's per-world
 *       ServerPlayerInteractionManager); the engine's single
 *       BlockInteractionService consults the overworld field, so the
 *       nether block never breaks and an overworld cell at the same
 *       coordinates is corrupted instead.</li>
 *   <li>VT-001 — {@code theNetherFireOnStoneDiesOnItsFirstScheduledTick}:
 *       an age-5 flame on a bare stone floor with no flammable neighbor
 *       dies on its first scheduled tick (FireBlock.tick lines 166-170:
 *       {@code i > 3} with no fuel); scheduled in the world the flame
 *       stands in (the reference's per-world tickUpdates). The engine
 *       wires the fire family's schedule seam to the OVERWORLD's queue
 *       alone, so a nether flame never ticks — frozen forever.</li>
 *   <li>VT-003 — {@code theBrokenIceBecomesWaterOverASolidFloor}: the
 *       survival break of ice with a movement-blocking block below turns
 *       the cell to FLOWING_WATER (IceBlock.afterMinedByPlayer lines
 *       42-55); the engine's break walk commits plain air.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DimensionTailsDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    /** The nether chamber's floor Y — high above the nether's natural sea (the FL4 test's own shape). */
    private static final int FLOOR_Y = 100;
    /** The chamber spans x/z 58..72 — the FL4 fixture's own window. */
    private static final int FROM = 58;
    private static final int TO = 72;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dimtails" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var accepted = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /**
     * Carves the deterministic chamber in the named dimension (the FL4
     * test's own shape): cleared air for the working volume, then the
     * full stone plate floor.
     */
    private void carveChamber(EngineWorld world) throws Exception {
        ensureLoaded(world);
        server.ticker().submit(() -> {
            for (int x = FROM; x <= TO; x++) {
                for (int z = FROM; z <= TO; z++) {
                    for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 4; y++) {
                        world.setBlock(new BlockPosition(x, y, z), world.airType());
                    }
                    world.setBlock(new BlockPosition(x, FLOOR_Y, z), BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(world,
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(64, FLOOR_Y, 64))),
                80, "the chamber floor landed");
    }

    private void ensureLoaded(EngineWorld world) throws Exception {
        for (int cx = FROM >> 4; cx <= TO >> 4; cx++) {
            for (int cz = FROM >> 4; cz <= TO >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!world.isChunkLoaded(at)) {
                    server.requestChunkLoad(world.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(world,
                () -> world.isChunkLoaded(new ChunkPosition(FROM >> 4, FROM >> 4))
                        && world.isChunkLoaded(new ChunkPosition(TO >> 4, TO >> 4)),
                200, "the chamber chunks loaded");
    }

    /**
     * Builds the overworld portal (the DimensionWalk test's own shape) and
     * stands the body inside it until the crossing lands the body in the
     * nether.
     */
    private PlayerSession crossToNether(String name) throws Exception {
        PlayerSession walker = join(name);
        int frameX = 40;
        int frameZ = 40;
        int frameY = 20;
        // The 4x5 X-plane frame: obsidian columns at x and x+3, portal
        // interior between (the PortalForcer's build shape).
        server.ticker().submit(() -> {
            for (int w = 0; w < 4; w++) {
                for (int h = -1; h < 4; h++) {
                    boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                    server.world().setBlock(new BlockPosition(frameX + w, frameY + h, frameZ),
                            frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
                }
            }
        });
        await(() -> server.world().getBlock(new BlockPosition(frameX + 1, frameY, frameZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        Position portalStand = new Position(frameX + 1.5, frameY + 0.1, frameZ + 0.5);
        server.teleportPlayer(walker, portalStand);
        await(() -> walker.position().distanceSquared(portalStand) < 4.0,
                "the body stands in the portal");
        await(() -> walker.dimension() == -1, "the body crossed to the nether");

        // Kill the arrival frame's portal cells at once: the stand clock
        // stays at its max after a crossing and the reference's own
        // 10-tick cooldown (PlayerEntity line 315) is the only thing
        // holding the return — a body that lingers in the arrival cells
        // re-crosses the moment the cooldown expires. The seat's dig must
        // be driven by a body STABLY in the nether.
        Position arrival = walker.position();
        int ax = (int) Math.floor(arrival.x());
        int ay = (int) Math.floor(arrival.y());
        int az = (int) Math.floor(arrival.z());
        EngineWorld netherArrival = server.worldFor(-1);
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dy = -1; dy <= 4; dy++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPosition cell = new BlockPosition(ax + dx, ay + dy, az + dz);
                        var cellId = netherArrival.getBlock(cell).identifier();
                        if (cellId.equals(BuiltinBlocks.NETHER_PORTAL.identifier())
                                || cellId.equals(BuiltinBlocks.NETHER_PORTAL_Z.identifier())) {
                            netherArrival.setBlock(cell, netherArrival.airType());
                        }
                    }
                }
            }
        });
        await(() -> !netherArrival.getBlock(new BlockPosition(ax, ay, az)).identifier()
                .equals(BuiltinBlocks.NETHER_PORTAL.identifier()), "the arrival cells cleared");
        return walker;
    }

    /**
     * VT-002: a survival dig submitted by a nether-standing player reads
     * and writes the NETHER cell (the reference's per-world
     * ServerPlayerInteractionManager — handlePlayerDig's world is the
     * player's own). The engine's BlockInteractionService holds the single
     * overworld field, so the nether dirt never breaks and the OVERWORLD
     * cell at the same coordinates is destroyed instead — cross-dimension
     * world corruption through the ordinary dig packet.
     */
    @Test
    void theNetherDigBreaksTheNetherBlockNotTheOverworldCell() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        EngineWorld over = server.world();
        // The whole stage lands BEFORE the crossing: the chamber, the probe
        // cells — so the body can leave the arrival portal within the
        // return window (a body that lingers in the arrival frame's portal
        // cells arms the return crossing and bounces back to the overworld,
        // dragging the seat's dig out of the reach band for reasons that
        // have nothing to do with the finding).
        carveChamber(nether);
        ensureLoaded(over);

        // The dig target: dirt standing on the nether chamber's stone floor.
        BlockPosition target = new BlockPosition(64, FLOOR_Y + 1, 64);
        // The corruption probe: overworld DIRT at the SAME coordinates — the
        // same block the dig target holds, so the misrouted dig's gates
        // (digability, progress, the finish's 0.7 floor) all pass against
        // the OVERWORLD cell exactly as they would against the nether cell,
        // isolating the WORLD CHOICE as the only variable.
        server.ticker().submit(() -> {
            nether.setBlock(target, BuiltinBlocks.DIRT);
            over.setBlock(target, BuiltinBlocks.DIRT);
        });
        await(() -> nether.getBlock(target).equals(BuiltinBlocks.DIRT)
                && over.getBlock(target).equals(BuiltinBlocks.DIRT),
                "both cells landed (nether dirt + the overworld probe dirt)");

        PlayerSession walker = crossToNether("NetherDigger");

        // Stand the body ON the chamber floor beside the target. The stand
        // rides applyMovement's own onGround=true (the dig-handler test's
        // own pattern — teleportPlayer writes onGround=false and nothing
        // re-arms it without client movement packets, leaving the dig's
        // airborne 5x divisor parked: the reference's PlayerEntity
        // .getMiningSpeed line 695).
        Position stand = new Position(64.5, FLOOR_Y + 1.0, 66.5);
        server.ticker().submit(() -> walker.applyMovement(stand,
                net.zaminmc.torch.util.Rotation.ZERO, true));
        EngineAwaits.awaitTicks(nether, 30, "the stand lands");
        assertTrue(walker.dimension() == -1
                        && walker.position().distanceSquared(stand) < 4.0,
                "the body stands beside the nether target (dimension "
                        + walker.dimension() + ", position " + walker.position() + ")");

        server.blockInteraction().submitMiningStart(walker, target, 1);
        EngineAwaits.awaitTicks(nether, 40,
                "dirt by hand: the 15-tick nominal dig plus margin");
        server.blockInteraction().submitMiningFinished(walker, target);
        EngineAwaits.awaitTicks(nether, 12, "the finish walk lands");

        // THE REFERENCE LAWS: the nether cell breaks; the overworld probe
        // (a different dimension, never touched by a nether dig) survives.
        // assertAll so BOTH halves of the divergence report in one run:
        // the engine leaves the nether cell standing AND consumes the
        // overworld cell — the cross-dimension corruption.
        net.zaminmc.torch.block.BlockType netherCell = nether.getBlock(target);
        net.zaminmc.torch.block.BlockType overworldCell = over.getBlock(target);
        org.junit.jupiter.api.Assertions.assertAll(
                () -> assertEquals(BuiltinBlocks.AIR, netherCell,
                        "VT-002: the nether dig breaks the NETHER block (the reference's per-world manager)"),
                () -> assertEquals(BuiltinBlocks.DIRT, overworldCell,
                        "VT-002: the overworld cell at the same coordinates is NOT part of a nether dig"),
                () -> assertEquals(-1, walker.dimension(),
                        "the digger still stands in the nether"));
    }

    /**
     * VT-001: an age-5 flame on a bare stone floor with no flammable
     * neighbor dies on its FIRST scheduled tick — FireBlock.tick lines
     * 166-170 ({@code !hasFlammableNeighbor && i > 3 -> extinguish}),
     * scheduled in the world the flame stands in (the reference's
     * per-world tickUpdates; FireBlock.onAdded lines 318-326 schedules in
     * {@code worldIn}). The engine's fire seam schedules into the
     * OVERWORLD's queue alone (EngineServer wires FireBlocks with the
     * overworld's ScheduledTickSystem and registers the sixteen AGE
     * tickers there only; the nether/End systems carry the fluid families
     * alone) — so a nether flame never ticks at all: no age, no fuel
     * death, no spread, forever.
     */
    @Test
    void theNetherFireOnStoneDiesOnItsFirstScheduledTick() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        carveChamber(nether);

        BlockPosition flame = new BlockPosition(64, FLOOR_Y + 1, 64);
        server.ticker().submit(() ->
                nether.setBlock(flame, FireBlocks.stateOf(5)));
        await(() -> nether.getBlock(flame).equals(FireBlocks.stateOf(5)),
                "the age-5 flame landed on the nether stone floor");

        // The first scheduled tick fires at 30 + rand(10) = 30..39 ticks;
        // 240 completed ticks covers six fire ticks with wide margin.
        EngineAwaits.awaitTicks(nether, 240, "six scheduled fire ticks");

        // THE REFERENCE LAW: the fuel-less age-5 flame is gone (the first
        // tick's lines 166-170 death — no flammable neighbor, i > 3).
        assertEquals(nether.airType(), nether.getBlock(flame),
                "VT-001: the nether flame ticks in the nether and dies its fuel-less death");
    }

    /**
     * VT-003: the survival break of an ice cell with a movement-blocking
     * block below turns the cell to FLOWING_WATER — IceBlock
     * .afterMinedByPlayer lines 42-55 (the else arm: {@code material
     * .blocksMovement() || material.isLiquid() -> FLOWING_WATER}). The
     * engine's break walk commits plain air (no ice arm in tryMineBlock),
     * so breaking ice never yields water — the ice-fishing and
     * ice-refill mechanics cannot happen.
     */
    @Test
    void theBrokenIceBecomesWaterOverASolidFloor() throws Exception {
        PlayerSession digger = join("IceDigger");
        EngineWorld over = server.world();

        BlockPosition floor = new BlockPosition(2, 4, 2);
        BlockPosition target = new BlockPosition(2, 5, 2);
        server.ticker().submit(() -> {
            over.setBlock(floor, BuiltinBlocks.STONE);
            over.setBlock(target, BuiltinBlocks.ICE);
        });
        await(() -> over.getBlock(target).equals(BuiltinBlocks.ICE),
                "the ice cell landed over the stone floor");

        Position stand = new Position(8.0, 5.0, 2.5);
        server.teleportPlayer(digger, stand);
        await(() -> digger.position().distanceSquared(stand) < 4.0,
                "the digger stands inside the dig band");
        server.blockInteraction().submitMiningStart(digger, target, 1);
        EngineAwaits.awaitTicks(over, 80,
                "ice by hand: the dig progresses past the finish floor");
        server.blockInteraction().submitMiningFinished(digger, target);
        EngineAwaits.awaitTicks(over, 12, "the finish walk lands");

        // THE REFERENCE LAW: the broken cell holds WATER (the flowing form
        // the reference writes over a movement-blocking floor).
        assertTrue(FluidBlocks.kindOf(over.getBlock(target).identifier())
                        == FluidBlocks.Kind.WATER,
                "VT-003: the broken ice cell becomes water over the solid floor (got "
                        + over.getBlock(target).identifier() + ")");
    }
}
