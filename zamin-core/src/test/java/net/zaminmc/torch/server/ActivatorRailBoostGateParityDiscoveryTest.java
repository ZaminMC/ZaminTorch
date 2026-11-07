package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RailBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.MinecartEntity;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave RD — THE GOLDEN-ALONE BOOST GATE over the PRODUCTION consult
 * (EngineServer.railPowerAt): the reference's flag pair arms ONLY over
 * Blocks.POWERED_RAIL (MinecartEntity.moveOnRail lines 345-349 —
 * {@code if (abstractrailblock == Blocks.POWERED_RAIL) { flag = ...;
 * flag1 = !flag; }}) — the ACTIVATOR rail never boosts and never
 * brakes, its powered bit feeding the onActivatorRail consult alone
 * (the PE-14 family gate — the command run, the TNT prime, the hopper
 * disable).
 *
 * <p>The pre-fix engine fed the boost walk the golden/activator pair
 * whole: a powered ACTIVATOR rail BOOSTED a rolling cart (+0.06/tick,
 * never in vanilla) and a dark one BRAKED it (the halving walk, never
 * in vanilla — a cart rolling over unpowered activator track stopped
 * dead within two ticks).</p>
 */
class ActivatorRailBoostGateParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "actboostgate" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, true);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The rig: a 7-long east-west rail line of the given family on a
     * stone bed, the redstone feed torch at the west end when asked
     * (the propagation walk carrying the POWERED bit along the run),
     * and a plain minecart at mid-line rolling east at 0.05.
     *
     * @return the spawned cart (the velocity the seats read)
     */
    private MinecartEntity rigRollingCart(String family, boolean feedTor)
            throws InterruptedException {
        int y = 40;
        int z = 10;
        int fromX = 10;
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 8; dx++) {
                server.world().setBlock(new BlockPosition(fromX + dx, y - 1, z),
                        BuiltinBlocks.STONE);
            }
            for (int dx = 0; dx <= 6; dx++) {
                server.world().setBlock(new BlockPosition(fromX + dx, y, z),
                        RailBlocks.state(family, 1, false)); // east-west, dark
            }
            if (feedTor) {
                server.world().setBlock(new BlockPosition(fromX - 1, y, z),
                        net.zaminmc.torch.server.redstone.RedstoneBlocks.torchStandingLit());
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> RailBlocks.isTrioRail(server.world().getBlock(
                        new BlockPosition(fromX + 6, y, z))),
                100, "the " + family + " line placed");
        if (feedTor) {
            EngineAwaits.awaitCondition(server.world(),
                    () -> RailBlocks.isPowered(server.world().getBlock(
                            new BlockPosition(fromX + 6, y, z))),
                    100, "the whole " + family + " run powered by the feed");
        }
        AtomicReference<MinecartEntity> cart = new AtomicReference<>();
        server.ticker().submit(() -> {
            var spawned = server.vehicles().spawnMinecart(
                    new Position(fromX + 3.5, y + 0.35, z + 0.5), 0);
            if (spawned instanceof MinecartEntity plain) {
                plain.setVelocity(0.05, 0.0, 0.0); // rolling east
                cart.set(plain);
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> cart.get() != null,
                100, "the plain cart spawned");
        return cart.get();
    }

    // ------------------------------------------------------------------
    // Seat 0 — THE GOLDEN CONTROL: the rig's own positive proof
    // ------------------------------------------------------------------

    /**
     * A powered GOLDEN rail boosts the rolling cart (the boost walk
     * reads the golden family — the rig's own proof that the feed and
     * the walk both run): 0.05 + the 0.06-per-tick boost climbs past
     * 0.25 within six ticks. Green by design on both engines.
     */
    @Test
    void thePoweredGoldenRailBoostsTheControl() throws Exception {
        MinecartEntity cart = rigRollingCart("golden_rail", true);
        EngineAwaits.awaitTicks(server.world(), 6,
                "the golden boost window");
        assertTrue(Math.abs(cart.velocityX()) > 0.25,
                "the powered golden rail boosts the rolling cart (the"
                        + " rig's positive control): " + cart.velocityX());
    }

    // ------------------------------------------------------------------
    // Seat 1 — the powered activator rail never boosts
    // ------------------------------------------------------------------

    /**
     * A cart rolling at 0.05 over a POWERED activator line NEVER gains
     * the boost — the state rides the plain 0.96 decay alone (0.05 ×
     * 0.96^10 ≈ 0.033 after ten ticks). RED on the pre-fix engine: the
     * golden-inclusive consult boosted the cart (+0.06/tick — the
     * state past 0.4 within ten ticks).
     */
    @Test
    void thePoweredActivatorRailNeverBoosts() throws Exception {
        MinecartEntity cart = rigRollingCart("activator_rail", true);
        EngineAwaits.awaitTicks(server.world(), 10,
                "the powered-activator never-boost window");
        double speed = Math.abs(cart.velocityX());
        assertTrue(speed < 0.05,
                "the powered ACTIVATOR rail never boosts (the golden"
                        + " family alone — moveOnRail lines 345-349): "
                        + speed);
        assertTrue(speed > 0.02,
                "the cart still rolls (the plain 0.96 decay alone, never"
                        + " a halt): " + speed);
    }

    // ------------------------------------------------------------------
    // Seat 2 — the dark activator rail never brakes
    // ------------------------------------------------------------------

    /**
     * A cart rolling at 0.05 over a DARK activator line NEVER takes the
     * brake — the unpowered golden rail's halving walk is the golden
     * family's own (flag1 arms only over Blocks.POWERED_RAIL). RED on
     * the pre-fix engine: the dark activator line stopped the cart dead
     * within two ticks (0.05 → the halving → the 0.03 full stop).
     */
    @Test
    void theDarkActivatorRailNeverBrakes() throws Exception {
        MinecartEntity cart = rigRollingCart("activator_rail", false);
        EngineAwaits.awaitTicks(server.world(), 10,
                "the dark-activator never-brake window");
        double speed = Math.abs(cart.velocityX());
        assertTrue(speed > 0.02,
                "the dark ACTIVATOR rail never brakes (the cart still"
                        + " rolls — the plain decay alone): " + speed);
    }
}
