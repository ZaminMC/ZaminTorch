package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.block.ChestOpenEvent;
import net.zaminmc.torch.event.block.DispenseEvent;
import net.zaminmc.torch.event.block.SignChangeEvent;
import net.zaminmc.torch.event.world.ExplosionDestroyEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interaction-events wave (§27.7 — the ledger's named omissions
 * closing): the cancellable pre-commit dispense gate, the post-commit
 * chest-open observation, the cancellable/rewritable sign-edit gate,
 * and the per-block post-commit explosion-destruction observation —
 * every handler registered through the public bus, exercised over the
 * live engine's own walks.
 */
class InteractionEventAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "iea" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    @Test
    void theDispenseGateObservesAndCancels() throws Exception {
        Server api = server;
        BlockPosition dispenser = new BlockPosition(30, 40, 30);
        server.ticker().submit(() -> {
            server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(5, false));
            server.dispensers().getOrCreate(dispenser)
                    .setItem(0, net.zaminmc.torch.item.ItemStack.of(BuiltinItems.ARROW, 5));
        });
        await(() -> server.dispensers().peek(dispenser) != null
                        && !server.dispensers().peek(dispenser).getItem(0).isEmpty(),
                "the dispenser staged");

        // The cancelling handler: the gate fires BEFORE the behavior walk.
        AtomicInteger fired = new AtomicInteger();
        AtomicReference<DispenseEvent> seen = new AtomicReference<>();
        var sub = api.events().on(DispenseEvent.class, event -> {
            fired.incrementAndGet();
            seen.set(event);
            event.setCancelled(true);
        });

        server.ticker().submit(() -> server.dispensers().scheduledTick(dispenser));
        await(() -> fired.get() >= 1, "the gate fired");

        assertEquals(1, fired.get(), "the gate fired exactly once");
        DispenseEvent event = seen.get();
        assertEquals(dispenser, event.origin(), "the dispensing block");
        assertEquals(BuiltinItems.ARROW, event.stack().type(), "the picked stack");
        assertEquals(5, event.stack().count(), "the stack's count");
        assertEquals(dispenser.offset(1, 0, 0), event.facingCell(),
                "the faced cell (the east facing)");
        assertEquals(false, event.dropper(), "a dispenser, not a dropper");
        // The cancelled dispense: the stack stays, no projectile left.
        assertEquals(5, server.dispensers().peek(dispenser).getItem(0).count(),
                "the cancelled dispense keeps the stack");
        assertTrue(server.world().projectiles().isEmpty(),
                "no arrow left the dispenser");

        sub.unsubscribe();

        // The uncancelled twin: the arrow flies, the stack pays one unit.
        server.ticker().submit(() -> server.dispensers().scheduledTick(dispenser));
        await(() -> !server.world().projectiles().isEmpty(), "the arrow left");
        assertEquals(4, server.dispensers().peek(dispenser).getItem(0).count(),
                "the uncancelled dispense paid one unit");
    }

    @Test
    void theChestOpenObservationFiresAfterTheWindowCommits() throws Exception {
        Server api = server;
        BlockPosition chest = new BlockPosition(40, 40, 40);
        server.ticker().submit(() -> {
            server.world().setBlock(chest, BuiltinBlocks.CHEST);
            server.chestManager().getOrCreate(chest);
        });
        await(() -> server.chestManager().peek(chest) != null, "the chest staged");

        PlayerSession opener = join("Opener", new Position(40.5, 40.0, 41.5));

        AtomicInteger fired = new AtomicInteger();
        AtomicReference<ChestOpenEvent> seen = new AtomicReference<>();
        var sub = api.events().on(ChestOpenEvent.class, event -> {
            fired.incrementAndGet();
            seen.set(event);
        });

        server.useItemOnBlock(opener, chest, 1, Optional.empty(), windowId -> { });
        await(() -> fired.get() >= 1, "the chest-open observation fired");

        ChestOpenEvent event = seen.get();
        assertEquals(chest, event.position(), "the opened chest's position");
        assertEquals(opener.uuid(), event.openerId(), "the opener's uuid");
        assertEquals("Opener", event.openerName(), "the opener's name");
        assertEquals(server.world().name(), event.worldName(), "the world's name");
        sub.unsubscribe();
    }

    @Test
    void theSignChangeGateCancelsAndRewrites() throws Exception {
        Server api = server;
        BlockPosition sign = new BlockPosition(0, 40, 0);
        server.ticker().submit(() -> {
            server.world().setBlock(sign, BuiltinBlocks.SIGN_SOUTH);
            server.signs().set(sign, new String[] {"Old", "Text", "Stands", ""});
        });
        await(() -> server.signs().peek(sign) != null, "the sign staged");

        PlayerSession editor = join("Editor", new Position(0.5, 40.0, 1.5));

        // The cancelling handler: the old text stands.
        AtomicInteger fired = new AtomicInteger();
        var sub = api.events().on(SignChangeEvent.class, event -> {
            fired.incrementAndGet();
            event.setCancelled(true);
        });
        server.editSign(editor, sign, new String[] {"New", "Text", "Dies", "Here"});
        await(() -> fired.get() >= 1, "the sign gate fired");
        assertEquals("Old", server.signs().peek(sign)[0], "the cancelled edit keeps the old line");
        sub.unsubscribe();

        // The rewriting handler: the walk applies the handler's answer.
        AtomicReference<SignChangeEvent> seen = new AtomicReference<>();
        sub = api.events().on(SignChangeEvent.class, event -> {
            seen.set(event);
            event.setLines(new String[] {"Sanitized", event.lines()[1],
                    event.lines()[2], event.lines()[3]});
        });
        server.editSign(editor, sign, new String[] {"Bad", "Second", "Third", "Fourth"});
        await(() -> seen.get() != null, "the rewrite gate fired");
        assertEquals("Sanitized", server.signs().peek(sign)[0],
                "the handler's rewrite applied");
        assertEquals("Second", server.signs().peek(sign)[1], "the untouched lines carry");
        sub.unsubscribe();

        // The identity reads: the editor's own fields.
        SignChangeEvent event = seen.get();
        assertEquals(sign, event.position(), "the sign's position");
        assertEquals(editor.uuid(), event.editorId(), "the editor's uuid");
        assertEquals("Editor", event.editorName(), "the editor's name");
    }

    @Test
    void theExplosionDestroyObservationCarriesTheDestructionList() throws Exception {
        Server api = server;
        BlockPosition center = new BlockPosition(60, 60, 60);
        server.ticker().submit(() -> {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        server.world().setBlock(center.offset(dx, dy, dz), BuiltinBlocks.STONE);
                    }
                }
            }
        });
        await(() -> !server.world().getBlock(center.offset(1, 0, 0))
                        .identifier().toString().equals("minecraft:air"),
                "the stone cluster stands");

        AtomicInteger fired = new AtomicInteger();
        AtomicReference<ExplosionDestroyEvent> seen = new AtomicReference<>();
        var sub = api.events().on(ExplosionDestroyEvent.class, event -> {
            fired.incrementAndGet();
            seen.set(event);
        });

        // A short-fuse prime at the center: the detonation ~2 ticks out.
        server.ticker().submit(() -> server.tnt().ignite(server.world(), center, 2));
        await(() -> fired.get() >= 1, "the destruction observation fired");

        ExplosionDestroyEvent event = seen.get();
        assertEquals(4.0f, event.power(), 0.001f, "the vanilla TNT power rides the event");
        List<ExplosionDestroyEvent.DestroyedCell> cells = event.cells();
        assertTrue(!cells.isEmpty(), "the stone cluster's destruction is in the list");
        boolean sawStone = false;
        boolean sawNeighbor = false;
        for (var cell : cells) {
            if ("minecraft:stone".equals(cell.previousType())) {
                sawStone = true;
            }
            if (cell.position().equals(center.offset(1, 0, 0))) {
                sawNeighbor = true;
            }
        }
        assertTrue(sawStone, "the destroyed stone cells name their type");
        assertTrue(sawNeighbor, "the cluster's stone cell is in the list (the TNT's own seat is air by blast time — the prime consumed it)");
        // The post-commit truth: the listed cells are air now.
        for (var cell : cells) {
            assertEquals("minecraft:air",
                    server.world().getBlock(cell.position()).identifier().toString(),
                    "the listed cell is already air");
        }
        sub.unsubscribe();
    }
}
