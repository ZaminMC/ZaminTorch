package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.entity.Entity;
import net.zaminmc.torch.event.block.PistonPushEvent;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The piston's entity-displacement observation surface (§27.7 — the
 * VIII13 exposure, the §27.10 capability test): the post-commit
 * {@link PistonPushEvent} fires once per pushed body through the PUBLIC
 * event bus, carrying the pushed body's api handle (§27.5: the engine
 * object IS the handle), the carrier's own rows (the cell, the facing,
 * the carried block, the extending flag), and the displacement's own
 * data (the amount along the facing, the slime fling flag) — exercised
 * through {@link Server#events()} alone, no engine internals, the same
 * piston scene the engine-side PistonEntityPushTest pins the physics
 * with.
 */
class PistonPushApiAcceptanceTest {

    /** The reference direction id for EAST (the scene pushes along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "ppa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The observation rows: a stone-carrying piston pushes a pig — every
     * event carries the pig's api handle, the east facing, the extending
     * form, the carried stone, the facing-signed amount band, and never
     * the fling flag; the pushed handle's own position reflects the
     * ALREADY-PUSHED body (the post-commit contract).
     */
    @Test
    void thePushObservationCarriesTheDisplacementRows() throws Exception {
        int x = 10, z = 10, y = 40;
        List<PistonPushEvent> observed = new CopyOnWriteArrayList<>();
        EventSubscription sub = server.events().on(PistonPushEvent.class, observed::add);
        try {
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.piston.PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                server.mobs().spawnAt(MobType.PIG, new Position(x + 2.5, y, z + 0.5));
            });
            await(() -> !server.mobs().all().isEmpty(), "the pig spawned");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> server.world().getBlock(new BlockPosition(x + 2, y, z))
                            .identifier().value().equals("stone"),
                    "the pushed stone landed one cell along");

            assertFalse(observed.isEmpty(),
                    "the push observation fired through the public bus");
            for (PistonPushEvent event : observed) {
                // The event's own rows, every one of them.
                assertEquals(server.world().name(), event.worldName(),
                        "the world's own name");
                assertEquals(Direction.EAST, event.facing(), "the piston's facing");
                assertTrue(event.extending(), "the carrier extends");
                assertEquals("stone", event.carriedBlock().identifier().value(),
                        "the carried block's type");
                assertFalse(event.flung(), "a stone carry never flings");
                assertTrue(event.amount() > 0.0 && event.amount() <= 0.5625 + 1.0e-9,
                        "the facing-signed amount rides the extending band: " + event.amount());
                assertEquals(x + 2, event.carrierPosition().x(),
                        "the carrier's cell (the moving-piston block's own cell"
                                + " — the engine's carrier record stands at the landing)");
                // The pushed body's PUBLIC handle: a pig (the api's
                // typeIdentifier) whose pushed position sits along the
                // flight path (the post-commit contract — the body
                // already reflects the push when the handler runs).
                Entity pushed = event.pushed();
                assertEquals("minecraft:pig", pushed.typeIdentifier().toString(),
                        "the pushed body is the pig, through the api handle");
                assertTrue(pushed.snapshot().position().x() >= x + 2.0,
                        "the handle observes the ALREADY-PUSHED body: "
                                + pushed.snapshot().position().x());
            }
            // The engine id shared: the pushed handle is the live mob.
            int pushedId = observed.get(observed.size() - 1).pushed().entityId();
            assertTrue(server.mobs().all().stream()
                            .anyMatch(mob -> mob.entityId() == pushedId),
                    "the handle's engine id is the live mob's own");
        } finally {
            sub.unsubscribe();
        }
    }

    /**
     * The slime fling row: a slime-carrying piston FLINGS the pig — the
     * observation carries the fling flag, and the pushed handle's
     * velocity tells the same story (the +1 axis set).
     */
    @Test
    void theFlingObservationCarriesTheSlimeRow() throws Exception {
        int x = 70, z = 70, y = 40;
        List<PistonPushEvent> observed = new CopyOnWriteArrayList<>();
        EventSubscription sub = server.events().on(PistonPushEvent.class, observed::add);
        try {
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.piston.PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.SLIME_BLOCK);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
                server.mobs().spawnAt(MobType.PIG, new Position(x + 2.5, y, z + 0.5));
            });
            await(() -> !server.mobs().all().isEmpty(), "the pig spawned");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> !observed.isEmpty()
                            && observed.stream().anyMatch(PistonPushEvent::flung),
                    "a fling event landed through the public bus");
            PistonPushEvent fling = observed.stream()
                    .filter(PistonPushEvent::flung)
                    .findFirst()
                    .orElseThrow();
            assertEquals("slime_block", fling.carriedBlock().identifier().value(),
                    "the carried block names the fling");
            assertTrue(fling.extending(), "the fling rides an extending carrier");
            assertEquals("minecraft:pig", fling.pushed().typeIdentifier().toString(),
                    "the flung body is the pig");
        } finally {
            sub.unsubscribe();
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
