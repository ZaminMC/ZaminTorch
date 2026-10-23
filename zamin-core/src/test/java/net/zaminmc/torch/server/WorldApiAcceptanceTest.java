package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.block.BlockChangedEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
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
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The world-family API wave (Slice 14a — the §27.3 "world, chunk, block,
 * block-state" family over the live engine): the time set on the owner
 * lane, the light reads, the biome name, the difficulty read, and the
 * post-commit block-change observation — all through the public
 * interfaces, no engine internals.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorldApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "worldapi", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private BlockPosition loadedCell() throws InterruptedException {
        BlockPosition at = new BlockPosition(2, 40, 2);
        server.requestChunkLoad(at.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(at.chunkPosition()), "chunk loaded");
        return at;
    }

    @Test
    void theTimeSetWalksTheClockOnTheOwnerLane() throws Exception {
        server = boot();
        World overworld = server.world();
        long before = overworld.timeOfDay();

        // The owner lane: the plugin-style mutation lands through the
        // world's own execute path (§27.6). The clock keeps ticking
        // forward after the set (the reference's own behavior), so the
        // assertion reads the landing BAND, not a frozen value.
        overworld.execute(() -> overworld.setTimeOfDay(6_000L));
        await(() -> overworld.timeOfDay() >= 6_000L && overworld.timeOfDay() < 6_100L,
                "the clock walked to noon");

        // The wrap: 25_000 walks to 1_000 (the floorMod clock).
        overworld.execute(() -> overworld.setTimeOfDay(25_000L));
        await(() -> overworld.timeOfDay() >= 1_000L && overworld.timeOfDay() < 1_100L,
                "the clock wraps on the day length");

        // Each world owns its clock: the overworld's jump never lands on
        // the nether's dial (the nether only walks its own tick pace).
        World nether = server.worlds().stream()
                .filter(w -> !w.name().equals(overworld.name()))
                .findFirst().orElseThrow();
        long netherBefore = nether.timeOfDay();
        overworld.execute(() -> overworld.setTimeOfDay(18_000L));
        await(() -> overworld.timeOfDay() >= 18_000L && overworld.timeOfDay() < 18_100L,
                "the overworld moved again");
        assertTrue(nether.timeOfDay() <= netherBefore + 600L,
                "the nether walked only its own ticks (was " + nether.timeOfDay()
                        + " from " + netherBefore + ")");
        server.shutdown(null);
    }

    @Test
    void theLightReadsExposeTheCommittedState() throws Exception {
        server = boot();
        World world = server.world();
        BlockPosition cell = loadedCell();

        // The surface sky read: the open sky at the surface cell.
        int sky = world.skyLightAt(cell);
        assertTrue(sky >= 0 && sky <= 15, "the sky light stays in band (was " + sky + ")");

        // A glowstone commit raises the block light at its own cell (the
        // light engine relights on the commit).
        world.execute(() -> world.setBlock(cell, BuiltinBlocks.GLOWSTONE));
        await(() -> world.getBlock(cell).equals(BuiltinBlocks.GLOWSTONE), "glowstone placed");
        await(() -> world.blockLightAt(cell) == 15, "the source cell carries the full light");

        world.execute(() -> world.setBlock(cell, BuiltinBlocks.AIR));
        await(() -> world.getBlock(cell).equals(BuiltinBlocks.AIR), "the source cleared");
        server.shutdown(null);
    }

    @Test
    void theBiomeReadCarriesTheColumn() throws Exception {
        server = boot();
        World overworld = server.world();
        BlockPosition cell = loadedCell();

        // The stable name form: one of the engine's families, lowercase.
        String biome = overworld.biomeAt(cell);
        assertNotNull(biome);
        List<String> families = List.of("ocean", "deep_ocean", "plains", "desert",
                "forest", "jungle", "hell");
        assertTrue(families.contains(biome), "a known family name (was " + biome + ")");

        // Outside loaded chunks the implicit default answers (the
        // engine's own rule — the nether's columns load with their
        // players): the read stays total, never null.
        assertEquals("plains", overworld.biomeAt(new BlockPosition(3_000, 40, 3_000)),
                "the unloaded column answers the implicit default");
        server.shutdown(null);
    }

    @Test
    void theDifficultyReadMatchesTheRuntime() throws Exception {
        server = boot();
        Server handle = server;
        assertTrue(handle.difficulty() >= 0 && handle.difficulty() <= 3,
                "the 1.8.8 difficulty id band (was " + handle.difficulty() + ")");
        server.shutdown(null);
    }

    @Test
    void theBlockChangedEventObservesEveryCommit() throws Exception {
        server = boot();
        World world = server.world();
        BlockPosition cell = loadedCell();

        List<BlockChangedEvent> observed = new ArrayList<>();
        AtomicReference<String> firstOld = new AtomicReference<>();
        AtomicInteger count = new AtomicInteger();
        EventSubscription sub = server.events().on(BlockChangedEvent.class, event -> {
            if (event.position().equals(cell)) {
                observed.add(event);
                firstOld.compareAndSet(null, event.previousType());
                count.incrementAndGet();
            }
        });

        // A plugin-style commit: stone into the cell.
        world.execute(() -> world.setBlock(cell, BuiltinBlocks.STONE));
        await(() -> !observed.isEmpty(), "the placement was observed");
        BlockChangedEvent first = observed.get(0);
        assertEquals(world.name(), first.worldName(), "the world's own name rides the event");
        assertEquals("minecraft:stone", first.newType(), "the committed type");
        assertEquals("minecraft:air", firstOld.get(), "the departed type");

        // The flip back: the pair inverts.
        world.execute(() -> world.setBlock(cell, BuiltinBlocks.AIR));
        await(() -> observed.size() >= 2, "the removal was observed");
        assertEquals("minecraft:air", observed.get(1).newType());
        assertEquals("minecraft:stone", observed.get(1).previousType());

        // Unsubscribe: the observation stops (the bus's own contract).
        sub.unsubscribe();
        int after = count.get();
        world.execute(() -> world.setBlock(cell, BuiltinBlocks.DIRT));
        await(() -> world.getBlock(cell).equals(BuiltinBlocks.DIRT), "dirt placed");
        EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
        assertEquals(after, count.get(), "no observation after the unsubscribe");
        server.shutdown(null);
    }
}
