package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command-block minecart's own law (Slice 66 — the reference port of
 * CommandBlockMinecartEntity over CommandExecutor): the wire's type index,
 * the run-on-rail cadence (once every 4 ticks under power), the output
 * tracking, the edit verb (the MC|AdvCdm arm), and the persistence
 * payload.
 */
class CommandBlockMinecartTest {

    @TempDir
    Path dataDir;

    /** The flat ground world. */
    private static final VehicleEntity.WorldQuery GROUND_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return y < 0.0;
                }
            };

    /** The recording host: counts the runs, answers the caller's count. */
    private static final class RecordingHost implements CommandBlockMinecartEntity.CommandHost {
        final AtomicInteger runs = new AtomicInteger();
        volatile int nextCount = 1;

        @Override
        public int run(CommandBlockMinecartEntity cart, String command) {
            runs.incrementAndGet();
            if (nextCount > 0) {
                cart.recordOutput("ran " + command);
            }
            return nextCount;
        }
    }

    @Test
    void theWireTypeIndexAndTheExecutorState() {
        RecordingHost host = new RecordingHost();
        CommandBlockMinecartEntity cart = new CommandBlockMinecartEntity(9950,
                new Position(10.5, 0.35, 10.5), new Random(95L), host);
        assertEquals(6, cart.minecartTypeIndex(), "the command cart carries objectData 6");
        assertEquals(VehicleEntity.Kind.COMMAND_BLOCK_MINECART, cart.kind());
        assertEquals(net.zaminmc.torch.entity.VehicleKind.COMMAND_BLOCK_MINECART,
                cart.vehicleKind());
        assertEquals("", cart.command(), "the empty command (CommandExecutor's own initial)");
        assertEquals(0, cart.successCount());
        assertTrue(cart.trackOutput(), "trackOutput defaults true");
        assertNull(cart.lastOutput());
    }

    @Test
    void thePoweredRailRunsAtMostOnceEveryFourTicks() {
        // onActivatorRail lines 99-104: a powered rail fires the command,
        // at most once every 4 ticks (the age clock advances one per tick
        // — a FRESH cart cannot run until it has lived 4 ticks, the
        // reference's own ticks - lastExecuted >= 4 gate with both at 0).
        RecordingHost host = new RecordingHost();
        CommandBlockMinecartEntity cart = new CommandBlockMinecartEntity(9951,
                new Position(10.5, 0.35, 10.5), new Random(96L), host);
        cart.setCommand("say hi");
        cart.onActivatorRail(true); // tick 0: the age gate refuses
        assertEquals(0, host.runs.get(), "a fresh cart has not lived 4 ticks");
        for (int i = 0; i < 4; i++) {
            cart.tick(GROUND_WORLD);
        }
        cart.onActivatorRail(true); // the age gate passed
        assertEquals(1, host.runs.get());
        assertEquals(1, cart.successCount(), "the run's count landed");
        assertTrue(cart.lastOutput().contains("ran say hi"),
                "the output tracked: " + cart.lastOutput());
        // The cooldown window: 3 ticks after the last run, a powered rail
        // does NOT re-run.
        for (int i = 0; i < 3; i++) {
            cart.tick(GROUND_WORLD);
        }
        cart.onActivatorRail(true);
        assertEquals(1, host.runs.get(), "the 4-tick floor held");
        cart.tick(GROUND_WORLD); // the 4th tick
        cart.onActivatorRail(true);
        assertEquals(2, host.runs.get(), "the floor passed: the run fired");
        // An unpowered rail never runs.
        cart.onActivatorRail(false);
        assertEquals(2, host.runs.get());
    }

    @Test
    void theUntrackedCartDropsItsOutput() {
        // sendMessage's trackOutput gate + setLastOutput's write arm.
        RecordingHost host = new RecordingHost();
        CommandBlockMinecartEntity cart = new CommandBlockMinecartEntity(9952,
                new Position(10.5, 0.35, 10.5), new Random(97L), host);
        cart.setTrackOutput(false);
        cart.recordOutput("hidden");
        assertNull(cart.lastOutput(), "the untracked cart records nothing");
        cart.setTrackOutput(true);
        cart.recordOutput("visible");
        assertTrue(cart.lastOutput().contains("visible"));
        assertTrue(cart.lastOutput().startsWith("["), "the [HH:mm:ss] timestamp");
        // setCommand resets the success count (setCommand line 107).
        cart.setSuccessCount(4);
        cart.setCommand("tp @p 0 0 0");
        assertEquals(0, cart.successCount(), "a new command resets the count");
    }

    @Test
    void thePersistencePayloadCarriesTheExecutorRows() {
        // The ZVD v2 roundtrip: Command, SuccessCount, TrackOutput,
        // LastOutput (CommandExecutor.writeNbt lines 37-51).
        java.nio.file.Path file = dataDir.resolve("vehicles.bin");
        net.zaminmc.torch.server.entity.vehicle.VehicleDataStore store =
                new net.zaminmc.torch.server.entity.vehicle.VehicleDataStore(file);
        VehicleManager manager = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(7), 9700);
        var spawned = (CommandBlockMinecartEntity) manager.spawnCommandBlockMinecart(
                new Position(12.5, 0.35, 12.5), new RecordingHost(), 0);
        spawned.setCommand("say persisted");
        spawned.setSuccessCount(3);
        spawned.setTrackOutput(false);
        spawned.setLastOutput("[10:00:00] old");
        store.save(manager.snapshot());
        List<VehicleManager.VehicleSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        VehicleManager.VehicleSnapshot row = loaded.get(0);
        assertEquals(VehicleEntity.Kind.COMMAND_BLOCK_MINECART.name(), row.kind());
        assertEquals("say persisted", row.command());
        assertEquals(3, row.commandSuccessCount());
        assertFalse(row.commandTrackOutput());
        assertEquals("[10:00:00] old", row.commandLastOutput());
        // The restore: the rows land on the fresh body.
        VehicleManager restored = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(8), 9800);
        restored.restoreAll(store.load(), null, null, () -> new RecordingHost());
        var back = (CommandBlockMinecartEntity) restored.byId(restored.all().get(0).entityId());
        assertEquals("say persisted", back.command());
        assertEquals(3, back.successCount());
        assertFalse(back.trackOutput());
        assertEquals("[10:00:00] old", back.lastOutput());
    }

    @Test
    void theLiveEngineRunsTheStoredCommandOnThePoweredRail() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession session = join(server, "commander");
            await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");
            AtomicReference<CommandBlockMinecartEntity> cartRef = new AtomicReference<>();
            var at = session.position();
            int railX = (int) Math.floor(at.x()) + 2;
            server.ticker().submit(() -> {
                server.world().setBlock(new net.zaminmc.torch.block.BlockPosition(railX, 40, 10),
                        BuiltinBlocks.RAIL);
                var cart = server.vehicles().spawnCommandBlockMinecart(
                        new Position(railX + 0.5, 40.06, 10.5), server::runCommandCart, 0);
                cartRef.set((CommandBlockMinecartEntity) cart);
            });
            await(() -> cartRef.get() != null, "the cart spawned");
            CommandBlockMinecartEntity cart = cartRef.get();
            // The edit verb (the MC|AdvCdm arm's landing): the command + the
            // track flag.
            server.ticker().submit(() ->
                    server.updateCommandBlockMinecart(session, cart.entityId(), "help", true));
            await(() -> cart.command().equals("help"), "the edit landed");
            // The age gate: the cart must live 4 ticks before the powered
            // rail consult can fire (the reference's own floor).
            await(() -> cart.ticks() >= 5, "the cart aged past the 4-tick floor");
            server.ticker().submit(() -> cart.onActivatorRail(true));
            await(() -> cart.lastOutput() != null || cart.successCount() != 0,
                    "the stored command ran through the engine's command service");
        } finally {
            server.shutdown(null);
        }
    }

    private EngineServer boot() throws Exception {
        // VT-7e — the run gate (CommandExecutor.run lines 84-108): the
        // live-engine run needs enable-command-block=true exactly like
        // the reference's dedicated default-off law (the run test's own
        // boot now carries the flag).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cmct" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(), GameMode.SURVIVAL,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE, true);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
