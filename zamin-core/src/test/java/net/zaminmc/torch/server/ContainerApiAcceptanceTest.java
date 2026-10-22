package net.zaminmc.torch.server;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The container-family API wave (§27.3's block-entity read surface over
 * {@link World#containers()}): the chest/furnace/hopper lookups and their
 * live views — the engine's own block entities behind the public handles
 * (§27.5), the reads exercised both on the owner lane and from a foreign
 * thread (the best-effort lookup contract).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContainerApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theChestReadsThroughThePublicView() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition at = new BlockPosition(12, 40, 12);
            server.ticker().submit(() -> {
                server.world().setBlock(at, BuiltinBlocks.CHEST);
                server.chestManager().getOrCreate(at)
                        .setSlot(3, ItemStack.of(BuiltinItems.DIAMOND, 5));
            });
            // The public lookup + reads (the owner-lane walk ran above).
            awaitSettled(server);
            World world = server.world();
            Optional<net.zaminmc.torch.container.Containers.Chest> chest =
                    world.containers().chestAt(at);
            assertTrue(chest.isPresent(), "the placed chest answers the lookup");
            assertEquals(27, chest.orElseThrow().slotCount(), "the vanilla chest grid");
            assertEquals(at, chest.orElseThrow().position());
            assertEquals(5, chest.orElseThrow().slot(3).count(), "the stack set through the engine");
            assertEquals(BuiltinItems.DIAMOND, chest.orElseThrow().slot(3).type());
            // The out-of-range slot answers empty, the empty position misses.
            assertTrue(chest.orElseThrow().slot(99).isEmpty());
            assertTrue(world.containers().chestAt(new BlockPosition(0, 90, 0)).isEmpty(),
                    "no chest at the empty position");
            // The foreign-thread lookup (the best-effort contract).
            AtomicReference<Optional<net.zaminmc.torch.container.Containers.Chest>> foreign =
                    new AtomicReference<>(Optional.empty());
            Thread offLane = new Thread(() -> foreign.set(world.containers().chestAt(at)));
            offLane.start();
            offLane.join();
            assertTrue(foreign.get().isPresent(), "the off-lane lookup finds the chest");
            assertEquals(27, foreign.get().orElseThrow().slotCount());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theFurnaceAndHopperReadThroughThePublicView() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition furnaceAt = new BlockPosition(14, 40, 14);
            BlockPosition hopperAt = new BlockPosition(14, 41, 15);
            server.ticker().submit(() -> {
                server.world().setBlock(furnaceAt, BuiltinBlocks.FURNACE);
                server.furnaceManager().getOrCreate(furnaceAt)
                        .setSlot(0, ItemStack.of(BuiltinItems.IRON_INGOT, 2));
                server.world().setBlock(hopperAt, HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(hopperAt)
                        .setItem(2, ItemStack.of(BuiltinItems.EMERALD, 7));
            });
            awaitSettled(server);
            World world = server.world();
            var furnace = world.containers().furnaceAt(furnaceAt);
            assertTrue(furnace.isPresent(), "the placed furnace answers");
            assertEquals(2, furnace.orElseThrow().input().count(), "the input slot");
            assertEquals(BuiltinItems.IRON_INGOT, furnace.orElseThrow().input().type());
            assertTrue(furnace.orElseThrow().fuel().isEmpty(), "the cold fuel slot");
            assertEquals(0, furnace.orElseThrow().burnTimeRemaining(), "a cold furnace");
            assertEquals(0, furnace.orElseThrow().cookTime(), "no cook progress");

            var hopper = world.containers().hopperAt(hopperAt);
            assertTrue(hopper.isPresent(), "the placed hopper answers");
            assertEquals(5, hopper.orElseThrow().slotCount(), "the vanilla hopper grid");
            assertEquals(7, hopper.orElseThrow().slot(2).count(), "the set stack");
            assertEquals(BuiltinItems.EMERALD, hopper.orElseThrow().slot(2).type());

            // The empty world's misses.
            assertTrue(world.containers().furnaceAt(new BlockPosition(0, 90, 0)).isEmpty());
            assertTrue(world.containers().hopperAt(new BlockPosition(0, 90, 0)).isEmpty());
        } finally {
            server.shutdown(null);
        }
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private void awaitSettled(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the submit drains at the head of the next tick");
    }
}
