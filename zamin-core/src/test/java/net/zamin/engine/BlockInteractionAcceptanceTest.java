package net.zamin.engine;

import net.zamin.api.BlockPosition;
import net.zamin.api.Identifier;
import net.zamin.api.Position;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Behavioral scenarios for the block interaction path (§93/§614):
 * initial state -> input -> observable result, on the real tick thread.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockInteractionAcceptanceTest {

    private EngineServer server;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20);
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

    @Test
    void breakingStoneProducesAir() throws Exception {
        server = boot();
        PlayerSession player = join("Breaker", new Position(0.5, 5, 0.5));
        // Place a stone first (engine-side, authoritative path).
        BlockPosition target = new BlockPosition(2, 5, 2);
        server.requestChunkLoad(target.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(target.chunkPosition()), "chunk loaded");
        server.blockInteraction().submitPlace(player, new BlockPosition(2, 4, 2), 1, BuiltinBlocks.STONE);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.STONE), "stone placed");

        server.blockInteraction().submitBreak(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR), "stone broken");

        server.shutdown(null);
    }

    @Test
    void placementIntoPlayerIsRejected() throws Exception {
        server = boot();
        // Player stands at y=5 on the surface; attempt to place at the player's own feet block.
        PlayerSession player = join("Inside", new Position(0.5, 5, 0.5));
        BlockPosition feetBlock = new BlockPosition(0, 5, 0);
        server.blockInteraction().submitPlace(player, new BlockPosition(0, 4, 0), 1, BuiltinBlocks.STONE);
        await(() -> server.world().getBlock(feetBlock).equals(BuiltinBlocks.AIR), "nothing placed into player");
        server.shutdown(null);
    }

    @Test
    void outOfReachInteractionIsRejected() throws Exception {
        server = boot();
        PlayerSession player = join("Far", new Position(0.5, 5, 0.5));
        BlockPosition far = new BlockPosition(30, 90, 30);
        server.requestChunkLoad(far.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(far.chunkPosition()), "chunk loaded");
        // Breaking something far outside reach must be rejected (no crash, no change).
        server.blockInteraction().submitBreak(player, far);
        await(() -> server.world().getBlock(far).equals(BuiltinBlocks.AIR), "far air block stays air");
        server.shutdown(null);
    }

    @Test
    void committedChangePublishesToListener() throws Exception {
        server = boot();
        var received = new java.util.concurrent.atomic.AtomicReference<BlockPosition>();
        server.addWorldListener((world, position, type) -> received.compareAndSet(null, position));
        PlayerSession player = join("Listener", new Position(0.5, 5, 0.5));
        server.blockInteraction().submitPlace(player, new BlockPosition(1, 4, 1), 1, BuiltinBlocks.STONE);
        await(() -> server.world().getBlock(new BlockPosition(1, 5, 1)).equals(BuiltinBlocks.STONE),
                "stone placed");
        await(() -> received.get() != null, "listener notified");
        assertEquals(new BlockPosition(1, 5, 1), received.get());
        server.shutdown(null);
    }

    @Test
    void registryResolvesAllPlaceableBuiltins() {
        assertNotNull(BuiltinBlocks.STONE);
        assertNotNull(BuiltinBlocks.DIRT);
        assertEquals("minecraft:stone",
                Identifier.parse("minecraft:stone").toString());
    }
}
