package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.redstone.TripwireBlocks;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-036 DISCOVERY SEATS (docs/vanilla-conformance-audit.md,
 * the forty-sixth pass) — the FL4-GAP2 "downstream families" the traced
 * Slice D plan does NOT name: the TRIPWIRE family whole and the TNT's
 * REDSTONE prime have no nether/End machinery.
 *
 * <p><b>The reference's law</b> (1.8.8): every neighborChanged walk runs
 * through the ACTING world's dispatch — the hook's mount-gone break
 * (TripwireHookBlock.neighborChanged lines 108-119: the facing's support
 * consult on the acting world, the hook leaves with its drop), the wire's
 * suspended flip break (TripwireBlock.neighborChanged lines 91-98: the
 * floor consult, the string drop), and the TNT's signal prime
 * (TntBlock.neighborChanged lines 51-56: {@code world.hasNeighborSignal}
 * on the acting world). A nether hook/wire/TNT behaves exactly like an
 * overworld one — the machinery is per-world.</p>
 *
 * <p><b>The engine's shape</b> (develop {@code efcd0c4f}): all three of
 * the tripwire family's buses bind the OVERWORLD alone —
 * {@code world.addChangeListener(tripwireSystem)} (EngineServer 2155),
 * {@code redstoneSystem.addNeighborListener(tripwireSystem::neighborHeard)}
 * (2157), {@code redstoneSystem.setTripwireTicker(...)} (2156) — and the
 * TNT's redstone arm double-locks to the overworld:
 * {@code redstoneSystem.addNeighborListener(tntSystem::neighborHeard)}
 * (2111) plus checkIgnite's own {@code actingWorld == world} gate
 * (TntSystem 349-353). A nether hook FLOATS over its removed wall, a
 * nether wire floats over its removed floor, a nether TNT never primes
 * on redstone.</p>
 *
 * <p>Every seat asserts the REFERENCE behavior; the OVERWORLD twin of
 * each construction is the control (the machinery exists — the binding
 * alone is wrong). EXPECTED RED on develop at the nether asserts; the
 * VT-036 fix (the Slice D pattern per family) lifts them green. Do not
 * re-pin a seat to the engine's current answer.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetherDownstreamFamilyInertDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "downstream" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** A flat stone stage with air above, in the given world. */
    private void stage(EngineWorld world, int x, int y, int z) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    world.setBlock(new BlockPosition(x + dx, y - 1, z + dz), BuiltinBlocks.STONE);
                    for (int dy = 0; dy <= 4; dy++) {
                        world.setBlock(new BlockPosition(x + dx, y + dy, z + dz), world.airType());
                    }
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(x, y - 1, z))),
                "the stage landed in dimension " + world.dimension());
    }

    /**
     * VT-036 member (a) — the HOOK: a tripwire hook whose wall block is
     * removed must break (the reference's neighborChanged mount consult).
     * The nether twin FLOATS on develop: the tripwire family's neighbor
     * bus never hears a nether change.
     */
    @Test
    void theNetherHookBreaksWhenItsWallIsRemoved() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int x = 400, y = 40, z = 400;
        stage(nether, x, y, z);
        // The wall at (x, y, z), the hook at (x+1) facing EAST (5) — the
        // mount is the wall behind it (the facing's opposite).
        BlockPosition wall = new BlockPosition(x, y, z);
        BlockPosition hook = new BlockPosition(x + 1, y, z);
        server.ticker().submit(() -> {
            nether.setBlock(wall, BuiltinBlocks.STONE);
            nether.setBlock(hook, TripwireBlocks.hookOf(5, false, false));
        });
        await(() -> TripwireBlocks.isHook(nether.getBlock(hook)),
                "the nether hook placed against its wall");

        // The wall leaves: the hook MUST break with its drop
        // (TripwireHookBlock.neighborChanged 108-119).
        server.ticker().submit(() -> nether.setBlock(wall, nether.airType()));
        EngineAwaits.awaitTicks(server.world(), 4, "the neighbor dispatch window");
        assertEquals(true, nether.getBlock(hook).equals(nether.airType()),
                "VT-036(a): the nether hook breaks when its wall is removed (the"
                        + " reference's per-world neighborChanged; observed the hook"
                        + " still standing: " + nether.getBlock(hook).identifier() + ")");
    }

    /** The overworld control for VT-036(a): the same hook DOES break. */
    @Test
    void theOverworldHookControlBreaksWhenItsWallIsRemoved() throws Exception {
        int x = 410, y = 40, z = 410;
        stage(server.world(), x, y, z);
        BlockPosition wall = new BlockPosition(x, y, z);
        BlockPosition hook = new BlockPosition(x + 1, y, z);
        server.ticker().submit(() -> {
            server.world().setBlock(wall, BuiltinBlocks.STONE);
            server.world().setBlock(hook, TripwireBlocks.hookOf(5, false, false));
        });
        await(() -> TripwireBlocks.isHook(server.world().getBlock(hook)),
                "the overworld hook placed against its wall");

        server.ticker().submit(() -> server.world().setBlock(wall, server.world().airType()));
        await(() -> server.world().getBlock(hook).equals(server.world().airType()),
                "the overworld hook breaks when its wall is removed (the control)");
    }

    /**
     * VT-036 member (a) — the WIRE: a tripwire wire whose floor is
     * removed must break (the reference's suspended-flip consult). The
     * nether twin FLOATS on develop.
     */
    @Test
    void theNetherWireBreaksWhenItsFloorIsRemoved() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int x = 420, y = 40, z = 420;
        stage(nether, x, y, z);
        BlockPosition wire = new BlockPosition(x, y, z);
        BlockPosition floor = new BlockPosition(x, y - 1, z);
        server.ticker().submit(() ->
                nether.setBlock(wire, TripwireBlocks.wireOf(false, false, false, false)));
        await(() -> TripwireBlocks.isTripwire(nether.getBlock(wire)),
                "the nether wire placed on its floor");

        // The floor leaves: the wire MUST break (TripwireBlock.
        // neighborChanged lines 91-98 — the suspended flip).
        server.ticker().submit(() -> nether.setBlock(floor, nether.airType()));
        EngineAwaits.awaitTicks(server.world(), 4, "the neighbor dispatch window");
        assertEquals(true, nether.getBlock(wire).equals(nether.airType()),
                "VT-036(a): the nether wire breaks when its floor is removed (the"
                        + " reference's per-world neighborChanged; observed the wire"
                        + " still standing: " + nether.getBlock(wire).identifier() + ")");
    }

    /** The overworld control for VT-036(a): the same wire DOES break. */
    @Test
    void theOverworldWireControlBreaksWhenItsFloorIsRemoved() throws Exception {
        int x = 430, y = 40, z = 430;
        stage(server.world(), x, y, z);
        BlockPosition wire = new BlockPosition(x, y, z);
        BlockPosition floor = new BlockPosition(x, y - 1, z);
        server.ticker().submit(() ->
                server.world().setBlock(wire, TripwireBlocks.wireOf(false, false, false, false)));
        await(() -> TripwireBlocks.isTripwire(server.world().getBlock(wire)),
                "the overworld wire placed on its floor");

        server.ticker().submit(() -> server.world().setBlock(floor, server.world().airType()));
        await(() -> server.world().getBlock(wire).equals(server.world().airType()),
                "the overworld wire breaks when its floor is removed (the control)");
    }

    /**
     * VT-036 member (b) — the TNT's REDSTONE prime: a TNT block whose
     * neighbor lever powers must prime (TntBlock.neighborChanged lines
     * 51-56: {@code world.hasNeighborSignal} on the acting world — the
     * TntSystemTest's own overworld construction, the nether twin). On
     * develop the nether TNT NEVER primes: the tntSystem's neighbor bus
     * binds the overworld redstone and checkIgnite's own
     * {@code actingWorld == world} gate refuses the nether outright.
     */
    @Test
    void theNetherTntPrimesOnThePoweredLever() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int x = 440, y = 40, z = 440;
        stage(nether, x, y, z);
        BlockPosition tnt = new BlockPosition(x, y, z);
        BlockPosition leverStone = new BlockPosition(x + 1, y, z);
        BlockPosition lever = new BlockPosition(x + 2, y, z);
        server.ticker().submit(() -> {
            nether.setBlock(tnt, BuiltinBlocks.TNT);
            nether.setBlock(leverStone, BuiltinBlocks.STONE);
            nether.setBlock(lever, RedstoneBlocks.leverOf(1, false));
        });
        await(() -> nether.getBlock(tnt).identifier().value().equals("tnt")
                        && nether.getBlock(lever).identifier().value().startsWith("lever"),
                "the nether TNT and its lever placed");

        // The lever powers: the neighbor signal MUST prime the block —
        // the cell empties and the primed body takes over.
        server.ticker().submit(() -> server.redstoneFor(-1).useLever(lever));
        EngineAwaits.awaitTicks(server.world(), 8, "the prime's window");
        assertEquals(true, nether.getBlock(tnt).equals(nether.airType()),
                "VT-036(b): the nether TNT primes on the powered lever (the"
                        + " reference's per-world neighborChanged signal consult;"
                        + " observed the block still standing: "
                        + nether.getBlock(tnt).identifier() + ")");
    }

    /** The overworld control for VT-036(b): the same lever DOES prime. */
    @Test
    void theOverworldTntControlPrimesOnThePoweredLever() throws Exception {
        int x = 450, y = 40, z = 450;
        stage(server.world(), x, y, z);
        BlockPosition tnt = new BlockPosition(x, y, z);
        BlockPosition leverStone = new BlockPosition(x + 1, y, z);
        BlockPosition lever = new BlockPosition(x + 2, y, z);
        server.ticker().submit(() -> {
            server.world().setBlock(tnt, BuiltinBlocks.TNT);
            server.world().setBlock(leverStone, BuiltinBlocks.STONE);
            server.world().setBlock(lever, RedstoneBlocks.leverOf(1, false));
        });
        await(() -> server.world().getBlock(tnt).identifier().value().equals("tnt"),
                "the overworld TNT and its lever placed");

        server.ticker().submit(() -> server.redstone().useLever(lever));
        await(() -> server.world().getBlock(tnt).equals(server.world().airType()),
                "the overworld TNT primes on the powered lever (the control)");
        assertTrue(true, "the control completed");
    }

    /**
     * VT-036 member (c) — THE PROBE'S CROSS-DIMENSION PHANTOM: the
     * occupancy probe (EngineServer's setOccupancyProbe closure) walks
     * {@code mobs.all()} with NO dimension filter, so a NETHER body at
     * the same coordinates TRIPS AN OVERWORLD WIRE. The reference's law
     * (TripwireBlock.updateOutputState lines 163-168):
     * {@code world.getEntities(null, box)} — the ACTING world's entity
     * list alone; another dimension's body at the same coordinates is
     * absent from the walk (the FL4-F10 plate-probe precedent, the
     * per-world entity-list law). On develop the overworld wire POWERS
     * on the nether pig's position — the phantom trip.
     */
    @Test
    void theOverworldWireNeverTripsOnANetherBodyAtTheSameCoordinates() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int x = 460, y = 40, z = 460;
        // The overworld stage carrying the wire.
        stage(server.world(), x, y, z);
        BlockPosition wire = new BlockPosition(x, y, z);
        server.ticker().submit(() ->
                server.world().setBlock(wire, TripwireBlocks.wireOf(false, false, false, false)));
        await(() -> TripwireBlocks.isTripwire(server.world().getBlock(wire)),
                "the overworld wire placed on its floor");

        // The nether stage at the SAME coordinates (so the pig stands
        // still on solid nether ground inside the wire's cell).
        stage(nether, x, y, z);

        // The nether pig standing in the wire's cell: the wire MUST
        // stay cold (another world's body is absent from the reference's
        // per-world getEntities walk).
        var mob = new java.util.concurrent.atomic.AtomicReference<
                net.zaminmc.torch.server.entity.MobEntity>();
        server.ticker().submit(() -> {
            mob.set(server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.PIG,
                    new net.zaminmc.torch.util.Position(x + 0.5, y, z + 0.5)));
            mob.get().setDimension(-1); // the body lives in the nether
        });
        await(() -> mob.get() != null, "the nether pig spawned");
        EngineAwaits.awaitTicks(server.world(), 10, "the tripwire scan's window");
        assertEquals(true, !TripwireBlocks.wirePowered(server.world().getBlock(wire)),
                "VT-036(c): the overworld wire never trips on a NETHER body at"
                        + " the same coordinates (the reference's per-world"
                        + " getEntities; observed the wire POWERED on the nether"
                        + " pig — the probe's cross-dimension phantom)");
    }

    /**
     * The control for VT-036(c): an OVERWORLD body in the wire's cell
     * DOES trip the overworld wire — the probe and the scan work; the
     * dimension filter alone is missing.
     */
    @Test
    void theOverworldWireControlTripsOnAnOverworldBody() throws Exception {
        int x = 470, y = 40, z = 470;
        stage(server.world(), x, y, z);
        BlockPosition wire = new BlockPosition(x, y, z);
        server.ticker().submit(() ->
                server.world().setBlock(wire, TripwireBlocks.wireOf(false, false, false, false)));
        await(() -> TripwireBlocks.isTripwire(server.world().getBlock(wire)),
                "the overworld wire placed on its floor");

        PlayerSession body = join("WireWalker", 0);
        body.applyMovement(new net.zaminmc.torch.util.Position(x + 0.5, y, z + 0.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 10, "the tripwire scan's window");
        assertEquals(true, TripwireBlocks.wirePowered(server.world().getBlock(wire)),
                "the overworld body trips the overworld wire (the control)");
    }

    /** A session joined at the given dimension (the plate seat's form). */
    private PlayerSession join(String name, int dimension) {
        net.zaminmc.torch.server.net.ClientLink link =
                new net.zaminmc.torch.server.net.ClientLink() {
                    @Override public boolean isActive() {
                        return true;
                    }

                    @Override public void kick(String reason) {
                    }
                };
        var result = server.joinRequest(link, name,
                java.util.UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        return session;
    }
}
