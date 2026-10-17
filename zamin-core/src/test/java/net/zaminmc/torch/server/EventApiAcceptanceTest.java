package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.EventPriority;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.block.BlockBreakEvent;
import net.zaminmc.torch.event.block.BlockPlaceEvent;
import net.zaminmc.torch.event.entity.MobSpawnEvent;
import net.zaminmc.torch.event.player.PlayerChatEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.chat.ChatService;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public event API (§27.7) over the live engine: a plugin-style consumer
 * registers through {@link Server#events()} — no engine internals — and
 * observes/cancels the four first-wave mechanics. The events fire on the
 * engine's own context; the assertions read the engine's observable state.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EventApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private BlockPosition loadedCell() throws InterruptedException {
        BlockPosition at = new BlockPosition(2, 5, 2);
        server.requestChunkLoad(at.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(at.chunkPosition()), "chunk loaded");
        return at;
    }

    @Test
    void theServerExposesItsEventBusThroughThePublicApi() throws Exception {
        server = boot();
        assertNotNull(server.events(), "the public bus must exist");
        assertNotNull(server.worlds(), "the world collection must exist");
        assertEquals(3, server.worlds().size(),
                "all three dimensions are public: the overworld, the nether, and the End");
        server.shutdown(null);
    }

    @Test
    void aCancelledBreakPreventsTheWorldMutation() throws Exception {
        server = boot();
        PlayerSession player = join("BreakCancel", new Position(0.5, 5, 0.5));
        BlockPosition target = loadedCell();
        server.ticker().submit(() -> {
            server.world().setBlock(target, BuiltinBlocks.STONE);
        });
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.STONE), "stone placed");

        // The plugin arm: cancel every break of stone at that cell.
        EventSubscription sub = server.events().on(BlockBreakEvent.class, event -> {
            if (event.position().equals(target)) {
                event.cancel();
            }
        });

        server.blockInteraction().submitCreativeBreak(player, target);
        Thread.sleep(300); // let the cancelled intent land

        assertTrue(server.world().getBlock(target).equals(BuiltinBlocks.STONE),
                "the cancelled break left the stone in place");
        sub.unsubscribe();

        // Without the handler the same break goes through.
        server.blockInteraction().submitCreativeBreak(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR), "stone broken after unsubscribe");

        server.shutdown(null);
    }

    @Test
    void aCancelledPlacePreventsTheWorldMutation() throws Exception {
        server = boot();
        PlayerSession player = join("PlaceCancel", new Position(0.5, 5, 0.5));
        // Deterministic ground near the spawn (the established acceptance
        // coordinates): (2,4,2)+(2,5,2) stone, the target cell air.
        server.requestChunkLoad(new BlockPosition(2, 5, 2).chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(new BlockPosition(2, 5, 2).chunkPosition()),
                "chunk loaded");
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(2, 4, 2), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(2, 5, 2), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(2, 6, 2), BuiltinBlocks.AIR);
        });
        await(() -> server.world().getBlock(new BlockPosition(2, 5, 2)).equals(BuiltinBlocks.STONE)
                && server.world().getBlock(new BlockPosition(2, 6, 2)).equals(BuiltinBlocks.AIR),
                "the deterministic ground is in place");

        BlockPosition above = new BlockPosition(2, 6, 2);

        EventSubscription sub = server.events().on(BlockPlaceEvent.class, event -> {
            if (event.position().equals(above)) {
                event.cancel();
            }
        });

        server.blockInteraction().submitPlace(player, new BlockPosition(2, 5, 2), 1,
                BuiltinBlocks.COBBLESTONE);
        Thread.sleep(300);

        assertTrue(!server.world().getBlock(above).equals(BuiltinBlocks.COBBLESTONE),
                "the cancelled place never mutated the cell");
        sub.unsubscribe();

        server.blockInteraction().submitPlace(player, new BlockPosition(2, 5, 2), 1,
                BuiltinBlocks.COBBLESTONE);
        await(() -> server.world().getBlock(above).equals(BuiltinBlocks.COBBLESTONE),
                "place lands after unsubscribe");

        server.shutdown(null);
    }

    @Test
    void aCancelledSpawnReturnsNoEntity() throws Exception {
        server = boot();
        List<String> seen = new ArrayList<>();
        // Block every chicken, record everything.
        EventSubscription sub = server.events().on(MobSpawnEvent.class, event -> {
            seen.add(event.mobType());
            if (event.mobType().endsWith("chicken")) {
                event.cancel();
            }
        });

        net.zaminmc.torch.util.Position at = new net.zaminmc.torch.util.Position(0.5, 8, 0.5);
        int before = server.mobs().size();
        var chicken = server.mobs().spawnAt(
                net.zaminmc.torch.server.entity.MobType.CHICKEN, at);

        assertEquals(null, chicken, "the cancelled spawn returns null");
        assertEquals(before, server.mobs().size(), "no entity joined the world");
        assertTrue(seen.contains("minecraft:chicken"), "the handler observed its own event");

        var pig = server.mobs().spawnAt(
                net.zaminmc.torch.server.entity.MobType.PIG, at);
        assertNotNull(pig, "the uncancelled spawn proceeds");
        sub.unsubscribe();

        server.shutdown(null);
    }

    @Test
    void aCancelledChatNeverReachesTheAudience() throws Exception {
        server = boot();
        PlayerSession speaker = join("Chatter", new Position(0.5, 5, 0.5));
        // A dedicated service instance with a counting publisher: the
        // audience observable, isolated from the engine's own chat wiring.
        AtomicInteger delivered = new AtomicInteger();
        ChatService local = new ChatService(
                server.ticker(),
                new net.zaminmc.torch.server.chat.CommandService(),
                chatEvent -> {
                    if (chatEvent instanceof ChatService.PublicChat) {
                        delivered.incrementAndGet();
                    }
                });
        local.setEventBus((net.zaminmc.torch.server.event.EngineEventBus) server.events());

        EventSubscription sub = server.events().on(PlayerChatEvent.class, event -> {
            if (event.message().contains("censored")) {
                event.cancel();
            }
        });

        local.submitChat(speaker, "hello world");
        local.submitChat(speaker, "this is censored text");

        await(() -> delivered.get() >= 1, "the uncensored message delivered");
        Thread.sleep(200); // the censored intent certainly landed too
        assertEquals(1, delivered.get(),
                "exactly one message reached the audience: the cancelled one dropped");
        sub.unsubscribe();

        server.shutdown(null);
    }

    @Test
    void chatEventsCarryTheSenderAndTheMessage() throws Exception {
        server = boot();
        PlayerSession speaker = join("Speaker", new Position(0.5, 5, 0.5));
        AtomicReference<PlayerChatEvent> received = new AtomicReference<>();
        EventSubscription sub = server.events().on(PlayerChatEvent.class,
                EventPriority.MONITOR, received::set);

        server.chatService().submitChat(speaker, "  observable message  ");
        await(() -> received.get() != null, "the chat event fired");
        assertEquals("observable message", received.get().message(),
                "the sanitized, trimmed text");
        assertEquals("Speaker", received.get().player().name());
        sub.unsubscribe();

        server.shutdown(null);
    }
}
