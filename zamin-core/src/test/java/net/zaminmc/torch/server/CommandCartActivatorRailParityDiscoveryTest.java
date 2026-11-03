package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RailBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Wave PE-14 — the ACTIVATOR RAIL FAMILY GATE over the command cart's
 * run path: the reference's call site (MinecartEntity.tick lines 269-271
 * — {@code if (blockstate.getBlock() == Blocks.ACTIVATOR_RAIL) {
 * this.onActivatorRail(k, l, i1, blockstate.get(PoweredRailBlock.POWERED)) }}
 * over CommandBlockMinecartEntity.onActivatorRail lines 99-104 — the
 * powered rail runs the stored command at most once every 4 ticks, the
 * success count the run's own source).
 *
 * <p>The onActivatorRail consult fires ONLY over an ACTIVATOR rail's
 * cell — NEVER a golden one, however powered (the golden rail's powered
 * states feed the BOOST walk alone). The pre-fix engine fed the command
 * cart's consult the golden-inclusive {@code railPowerAt > 0} — a
 * powered GOLDEN rail FIRED THE COMMAND in the engine, never in
 * vanilla. The same gate carries the TNT cart's prime and the hopper
 * cart's disable (the one consult, three kinds).</p>
 */
class CommandCartActivatorRailParityDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        // The command-blocks-enabled server (the run's own gate — the
        // 7-arg seat default answers the vanilla false and the cart
        // never runs, whatever the rail says).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cmdrailexact" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
     * The rig: a 7-long UNPOWERED rail line east-west on a stone bed, a
     * standing redstone torch at the west end feeding it (the direct
     * feed at the torch's neighbor, the propagation walk carrying the
     * POWERED bit along the run — the placed-powered state would decay
     * on the first neighbor update, the reference's own updateState law,
     * so the seat powers the line the vanilla way), and the command
     * cart at mid-line carrying {@code say seat} — the counted command
     * whose every run adds one to the success count.
     *
     * @param family  the trio family ("activator_rail" or "golden_rail")
     * @param feedTor whether the redstone feed torch stands
     */
    private CommandBlockMinecartEntity rigCommandCart(String family, boolean feedTor)
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
                    () -> net.zaminmc.torch.server.block.RailBlocks.isPowered(
                            server.world().getBlock(new BlockPosition(fromX + 6, y, z))),
                    100, "the whole " + family + " run powered by the feed");
        }
        AtomicReference<CommandBlockMinecartEntity> cart =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            var spawned = server.vehicles().spawnCommandBlockMinecart(
                    new Position(fromX + 3.5, y + 0.35, z + 0.5),
                    server::runCommandCart, 0);
            if (spawned instanceof CommandBlockMinecartEntity commandCart) {
                commandCart.setCommand("say seat");
                cart.set(commandCart);
            }
        });
        EngineAwaits.awaitCondition(server.world(), () -> cart.get() != null,
                100, "the command cart spawned");
        return cart.get();
    }

    // ------------------------------------------------------------------
    // Seat 1 — THE GOLDEN CONTROL: the powered golden rail never fires
    // ------------------------------------------------------------------

    /**
     * A powered GOLDEN rail boosts the cart (the boost walk reads both
     * families) but NEVER fires the command — the reference's family
     * gate names the ACTIVATOR block alone. The count stays 0 however
     * long the cart rolls over the powered golden cells. Pre-fix RED:
     * the golden-inclusive consult fired the command every 4 ticks.
     */
    @Test
    void thePoweredGoldenRailNeverFiresTheCommand() throws Exception {
        CommandBlockMinecartEntity cart = rigCommandCart("golden_rail", true);
        EngineAwaits.awaitTicks(server.world(), 60,
                "the golden-rail never window");
        assertEquals(0, cart.successCount(),
                "the powered GOLDEN rail never fires the command cart's run"
                        + " (the ACTIVATOR family alone) — the count stood at "
                        + cart.successCount());
    }
    // ------------------------------------------------------------------
    // Seat 2 — THE ACTIVATOR RUN: the powered activator rail runs it
    // ------------------------------------------------------------------

    /**
     * A powered ACTIVATOR rail runs the stored command, at most once
     * every 4 ticks — the success count the comparator's own tier reads
     * (PE-8's ledger). The count is the LAST run's own (CommandExecutor
     * .run resets it per run — a single /say answers 1 every time, never
     * an accumulation; /say's broadcast is its own feedback, the
     * executor answers null silently), so the run's proof is the count
     * leaving 0. Green by design on both engines: the run path itself,
     * the count's own source.
     */
    @Test
    void thePoweredActivatorRailRunsTheCommand() throws Exception {
        CommandBlockMinecartEntity cart = rigCommandCart("activator_rail", true);
        EngineAwaits.awaitCondition(server.world(),
                () -> cart.successCount() >= 1, 120,
                "the powered activator rail runs the command (the count leaves 0)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — THE UNPOWERED CONTROL: the dark activator rail is silent
    // ------------------------------------------------------------------

    /**
     * An unpowered activator rail never runs the command — the POWERED
     * bit the reference passes. Green by design on both engines.
     */
    @Test
    void theUnpoweredActivatorRailNeverRunsTheCommand() throws Exception {
        CommandBlockMinecartEntity cart = rigCommandCart("activator_rail", false);
        EngineAwaits.awaitTicks(server.world(), 60,
                "the unpowered-rail never window");
        assertEquals(0, cart.successCount(),
                "the unpowered activator rail never runs the command — the"
                        + " count stood at " + cart.successCount());
    }
}
