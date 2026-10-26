package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dispenser's world-event pair (Slice TF-2 — the ML-100 census's
 * standing tail): the base class's closing 2000 smoke on EVERY registry
 * dispense (DispenseItemBehavior.dispense's final wrapper, the data
 * carrying the facing index the client orients its puffs by) and the
 * bone meal's 2005 fertilize burst (DyeItem.useOn's own event at the
 * fertilized cell), with the white-dye arm's stay-in-slot failure law
 * (the dyed=false quirk: no drop, the 1001 failure click alone).
 */
class DispenserWorldEventTest {

    @TempDir
    Path dataDir;

    /** The recorded world events (position, id, data). */
    private record Seen(Position position, int eventId, int data) {
    }

    private final List<Seen> events = new CopyOnWriteArrayList<>();

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dpt" + Math.abs(java.util.UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        server.fx().addListener(new FxManager.Listener() {
            @Override
            public void onFx(FxManager.FxEvent event) {
                if (event instanceof FxManager.WorldEvent worldEvent) {
                    events.add(new Seen(worldEvent.position(), worldEvent.eventId(),
                            worldEvent.data()));
                }
            }
        });
        return server;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError(description);
    }

    /** Powers the dispenser at (x, y, z) through the lever pair. */
    private void power(EngineServer server, int x, int y, int z) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
            server.world().setBlock(new BlockPosition(x + 2, y, z),
                    RedstoneBlocks.leverOf(1, false));
        });
        server.ticker().submit(() -> server.redstone().useLever(
                new BlockPosition(x + 2, y, z)));
    }

    @Test
    void everyRegistryDispenseFiresTheSmokeWithTheFacingData() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // Facing UP (the offset pair {0, 1, 0}): the data packs as
                // 0 + 1 + (0 + 1) * 3 = 4 — the vertical collapse the
                // reference's toInt carries.
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 3));
            });
            power(server, x, y, z);
            await(() -> server.dispensers().peek(dispenser) != null
                            && server.dispensers().peek(dispenser).getItem(0).count() == 2,
                    "the dispense consumed one item");

            Seen smoke = null;
            for (Seen seen : events) {
                if (seen.eventId() == 2000) {
                    smoke = seen;
                }
            }
            assertTrue(smoke != null, "the dispense fired the 2000 smoke");
            assertEquals(4, smoke.data(), "the UP facing packs as 4 (toInt's vertical collapse)");
            assertEquals(x, (int) Math.floor(smoke.position().x()), "the smoke at the dispenser");
            assertEquals(y, (int) Math.floor(smoke.position().y()));
            assertEquals(z, (int) Math.floor(smoke.position().z()));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theBoneMealGrowFiresThe2005BurstAndTheFailureStaysInTheSlot() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            BlockPosition crop = new BlockPosition(x, y + 1, z);
            server.ticker().submit(() -> {
                // Facing UP at a stage-3 wheat: the grow takes the crop to
                // 5..7 and the burst lands at the fertilized cell.
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(1, false));
                server.world().setBlock(crop, RandomTickSystem.cropStage(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:wheat_stage3"), 3));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.BONE_MEAL, 2));
            });
            power(server, x, y, z);
            await(() -> server.dispensers().peek(dispenser) != null
                            && server.dispensers().peek(dispenser).getItem(0).count() == 1,
                    "the fertilize paid one unit");

            boolean burst = false;
            boolean smoke = false;
            for (Seen seen : events) {
                if (seen.eventId() == 2005
                        && (int) Math.floor(seen.position().y()) == y + 1) {
                    burst = true;
                    assertEquals(0, seen.data(), "the burst's data is the client's 15-particle form");
                }
                if (seen.eventId() == 2000) {
                    smoke = true;
                }
            }
            assertTrue(burst, "the 2005 burst landed at the fertilized cell");
            assertTrue(smoke, "the base class's 2000 smoke fired with the burst");
            String grownId = server.world().getBlock(crop).identifier().value();
            int grown = grownId.charAt("wheat_stage".length()) - '0';
            assertTrue(grown >= 5 && grown <= 7, "the crop grew the 2..5 roll: " + grownId);
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theNonFertilizableTargetKeepsTheStackInTheReservoir() throws Exception {
        EngineServer server = boot();
        try {
            int x = 70, z = 70, y = 40;
            BlockPosition dispenser = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                // Facing UP at plain air: the white-dye arm's dyed=false
                // quirk — the stack STAYS (no drop, no 2005).
                server.world().setBlock(dispenser, DispenserBlocks.dispenserOf(1, false));
                server.dispensers().getOrCreate(dispenser).setItem(0, ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.BONE_MEAL, 2));
            });
            power(server, x, y, z);
            await(() -> !events.isEmpty(), "the dispense attempt fired");
            // The stack never leaves the reservoir (the reference's own
            // return-item arm — only the NON-white dyes drop).
            assertEquals(2, server.dispensers().peek(dispenser).getItem(0).count(),
                    "the non-fertilizable target kept the stack");
            for (Seen seen : events) {
                assertFalse(seen.eventId() == 2005, "no burst on the failed fertilize");
            }
        } finally {
            server.shutdown(() -> { });
        }
    }
}
