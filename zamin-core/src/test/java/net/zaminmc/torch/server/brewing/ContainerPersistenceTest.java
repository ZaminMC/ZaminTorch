package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container persistence round trip (Slice 11f — the ledgered ZDD/ZBD
 * slot stores): a dispenser's nine slots and a brewing stand's four slots
 * + timer survive the shutdown-restart cycle exactly like the reference's
 * own block-entity NBT (the Items list; the brewing stand's BrewTime —
 * whose restored cycle cancels on the first post-load tick, the
 * reference's own null-ingredient quirk, re-arming fresh at the full
 * 400).
 */
class ContainerPersistenceTest {

    @TempDir
    Path dataDir;

    @Test
    void theDispenserAndBrewingStandSlotsSurviveTheRestart() throws Exception {
        String world = "ctpr" + Math.abs(UUID.randomUUID().hashCode() % 100000);

        // --- first life: place the containers, fill the slots, save --------
        EngineConfig first = new EngineConfig("127.0.0.1", 0, world, "it", 20, 2, 20,
                dataDir.toString());
        EngineServer one = new EngineServer(first);
        one.start();
        BlockPosition dispenserAt = new BlockPosition(10, 40, 10);
        BlockPosition standAt = new BlockPosition(12, 40, 12);
        one.ticker().submit(() -> {
            one.world().setBlock(dispenserAt, DispenserBlocks.dispenserOf(1, false));
            var dispenser = one.dispensers().getOrCreate(dispenserAt);
            dispenser.setItem(0, new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.ARROW, 12));
            dispenser.setItem(8, new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.FIRE_CHARGE, 3));

            one.world().setBlock(standAt, BrewingStandBlocks.of(false, false, false));
            var stand = one.brewingStands().getOrCreate(standAt);
            stand.setItem(0, new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 0));
            stand.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.NETHER_WART, 4));
        });
        await(() -> one.dispensers().peek(dispenserAt) != null
                        && one.brewingStands().peek(standAt) != null,
                "the containers' state materialized");
        // Let a few ticks run (the brewing cycle arms), then save.
        Thread.sleep(300);
        int armed = one.brewingStands().peek(standAt).timer();
        assertTrue(armed > 0, "the brew cycle armed in the first life: " + armed);
        one.saveAllNow();
        one.shutdown(null);
        awaitState(one);

        // --- second life: fresh process state, same data -------------------
        EngineConfig second = new EngineConfig("127.0.0.1", 0, world, "it", 20, 2, 20,
                dataDir.toString());
        EngineServer two = new EngineServer(second);
        two.start();
        try {
            // The dispenser's slots restored.
            var dispenser = two.dispensers().peek(dispenserAt);
            assertTrue(dispenser != null, "the dispenser state restored");
            assertEquals(12, dispenser.getItem(0).count(), "the arrow stack survived");
            assertEquals(3, dispenser.getItem(8).count(), "the fire charge stack survived");
            // The brewing stand's slots restored.
            var stand = two.brewingStands().peek(standAt);
            assertTrue(stand != null, "the brewing stand state restored");
            assertEquals(0, stand.getItem(0).damage(), "the water bottle survived");
            assertEquals(4, stand.getItem(BrewingStandBlockEntity.SLOT_INGREDIENT).count(),
                    "the nether wart stack survived");
            // The restored cycle: the reference's null-ingredient quirk —
            // the first post-load tick cancels the carried BrewTime and the
            // cycle re-arms fresh (the change gate finds the same brewing
            // setup).
            await(() -> stand.timer() > 300,
                    "the restored cycle re-armed fresh (the reference's quirk)");
            // The bottles' block bits republished on the first tick.
            await(() -> BrewingStandBlocks.hasBottle(two.world().getBlock(standAt), 0),
                    "the bottle bit republished after the restart");
            // The blocks themselves persisted through the world deltas.
            assertEquals(DispenserBlocks.dispenserOf(1, false),
                    two.world().getBlock(dispenserAt));
            assertTrue(BrewingStandBlocks.isBrewingStand(two.world().getBlock(standAt)),
                    "the brewing stand block survived");
        } finally {
            two.shutdown(null);
        }
    }

    private static void awaitState(EngineServer server) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (server.state() == net.zaminmc.torch.ServerState.STOPPED) {
                return;
            }
            Thread.sleep(25);
        }
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
