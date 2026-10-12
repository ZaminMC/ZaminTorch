package net.zaminmc.torch.server;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fire system, end to end: flint and steel ignites the aimed cell, fire
 * consumes the flammable block it sits on and dies without fuel, burning
 * bodies take the historical fire damage and water douses them, and the
 * rain kills exposed flames.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FireAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "fire", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private ItemType item(String id) {
        return net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow();
    }

    private void setBlock(BlockPosition at, BlockType type) throws InterruptedException {
        // The write executes on the tick thread; the latch observes the
        // commit itself instead of polling the block — a burn-out block
        // (floating fire dies in a tick or two) can be GONE before a
        // loaded-box poll ever sees it, which would read as a failed seed.
        java.util.concurrent.CountDownLatch placed = new java.util.concurrent.CountDownLatch(1);
        server.ticker().submit(() -> {
            server.world().setBlock(at, type);
            placed.countDown();
        });
        if (!placed.await(8, java.util.concurrent.TimeUnit.SECONDS)) {
            throw new AssertionError("seed never committed: " + at);
        }
    }

    private void useOn(PlayerSession player, BlockPosition at, int face) throws InterruptedException {
        server.useItemOnBlock(player, at, face, java.util.Optional.empty(),
                windowId -> { });
        Thread.sleep(150);
    }

    @Test
    void flintAndSteelIgnitesTheAimedCellAndWears() throws Exception {
        server = boot();
        PlayerSession igniter = join("Igniter");
        BlockPosition ground = new BlockPosition(0, 64, 0);
        setBlock(ground, BuiltinBlocks.STONE);
        igniter.inventory().setSlot(0, ItemStack.of(item("minecraft:flint_and_steel"), 1));
        igniter.inventory().selectHotbarSlot(0);
        int before = igniter.inventory().held().damage();

        useOn(igniter, ground, 1); // ignite the top face's cell

        assertEquals(BuiltinBlocks.FIRE, server.world().getBlock(ground.offset(0, 1, 0)),
                "the steel lit a fire against the top face");
        assertTrue(igniter.inventory().held().damage() > before,
                "the steel wore one use");
        server.shutdown(null);
    }

    @Test
    void fireEatsThePlankFloorBeneathIt() throws Exception {
        server = boot();
        join("Bystander");
        // Deterministic fire rolls: every consume/spread check hits, so the
        // first scheduled update burns the support and the second burns out.
        server.blockUpdates().setFireEnvironment(() -> false, alwaysZeroRandom());
        BlockPosition plank = new BlockPosition(0, 64, 0);
        setBlock(plank, BuiltinBlocks.OAK_PLANKS);
        BlockPosition flame = plank.offset(0, 1, 0);
        setBlock(flame, BuiltinBlocks.FIRE);

        // The scheduled fire clock consumes the flammable support on the
        // first update.
        await(() -> !server.world().getBlock(plank).equals(BuiltinBlocks.OAK_PLANKS),
                "the fire ate the plank under it");

        // With the fuel gone the flame burns out (the scheduled support
        // check extinguishes it).
        await(() -> server.world().getBlock(flame).equals(BuiltinBlocks.AIR),
                "the flame burned out without fuel");
        server.shutdown(null);
    }

    /** A random source that always rolls the first option (determinism). */
    private static java.util.Random alwaysZeroRandom() {
        return new java.util.Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    @Test
    void floatingFireBurnsOutWithoutSupport() throws Exception {
        server = boot();
        join("Watcher");
        BlockPosition midair = new BlockPosition(4, 70, 4);
        setBlock(midair, BuiltinBlocks.FIRE); // nothing below, nothing flammable
        await(() -> server.world().getBlock(midair).equals(BuiltinBlocks.AIR),
                "the floating flame burned itself out");
        server.shutdown(null);
    }

    @Test
    void bodiesIgniteInFireAndWaterDousesThem() throws Exception {
        server = boot();
        PlayerSession victim = join("Torchbear");
        BlockPosition ground = new BlockPosition(0, 64, 0);
        setBlock(ground, BuiltinBlocks.STONE);
        server.ticker().submit(() -> victim.applyMovement(
                new Position(0.5, 65.0, 0.5), net.zaminmc.torch.util.Rotation.ZERO, true));
        setBlock(new BlockPosition(0, 65, 0), BuiltinBlocks.FIRE);

        await(() -> victim.burning(), "the flame set the body on fire");
        float before = victim.health();
        await(() -> victim.health() < before, "the fire damage landed");

        // Water douses: pour a source at the feet.
        server.ticker().submit(() -> server.world().setBlock(
                new BlockPosition(0, 65, 0),
                net.zaminmc.torch.server.block.FluidBlocks.sourceOf(
                        net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER)));
        await(() -> !victim.burning(), "the water doused the burn");
        server.shutdown(null);
    }

    @Test
    void rainExtinguishesExposedFire() throws Exception {
        server = boot();
        join("Forecaster");
        BlockPosition ground = new BlockPosition(2, 64, 2);
        setBlock(ground, BuiltinBlocks.STONE);
        BlockPosition flame = ground.offset(0, 1, 0);
        setBlock(flame, BuiltinBlocks.FIRE);
        // The weather commit runs on the tick thread like /weather does.
        server.ticker().submit(() -> server.setWeather(true, -1));
        await(() -> server.world().getBlock(flame).equals(BuiltinBlocks.AIR),
                "the rain doused the exposed flame");
        server.shutdown(null);
    }
}
