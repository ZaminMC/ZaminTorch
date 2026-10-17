package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.block.FurnaceBurnEvent;
import net.zaminmc.torch.event.block.FurnaceSmeltEvent;
import net.zaminmc.torch.event.block.HopperTransferEvent;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §27.7 interaction-events wave's furnace + hopper arms (Slice 82):
 * the public pre-commit gates over the engine's own walks. The
 * FurnaceBurnEvent gates the ignite (cancel keeps the fuel and the
 * furnace cold); the FurnaceSmeltEvent gates the smelt completion
 * (cancel keeps the input and the output — the cycle's progress is
 * spent, the reference's own reset); the HopperTransferEvent gates
 * every prospective item move (push/pull/suck — cancel keeps THIS item,
 * the walk's own rules continue around it).
 */
class FurnaceHopperEventAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void theCancelledIgniteKeepsTheFurnaceCold() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition at = new BlockPosition(0, 64, 0);
            setBlock(server, at, net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE);

            // The cancel subscribes BEFORE the load: the very first
            // ignite attempt is already refused (otherwise the stick
            // burns before the subscription lands).
            EventSubscription sub = server.events().on(FurnaceBurnEvent.class,
                    event -> {
                        if (event.position().equals(at)) {
                            event.cancel();
                        }
                    });

            loadFurnace(server, at, "minecraft:iron_ore", "minecraft:stick");
            Thread.sleep(600); // several ignite attempts refused

            FurnaceBlockEntity furnace = server.furnaceManager().at(at);
            assertFalse(furnace.burning(), "the cancelled ignite kept the furnace cold");
            assertEquals(1, furnace.fuel().count(), "the fuel was never consumed");
            assertEquals(1, furnace.input().count(), "the input keeps waiting");
            sub.unsubscribe();

            // Without the handler the same furnace ignites.
            await(() -> server.furnaceManager().at(at).burning(),
                    "the ignite goes through after unsubscribe");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSmeltObservationCarriesInputAndResultAndTheCancelKeepsBoth() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition smeltAt = new BlockPosition(0, 64, 0);
            BlockPosition heldAt = new BlockPosition(3, 64, 0);
            setBlock(server, smeltAt, net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE);
            setBlock(server, heldAt, net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE);
            loadFurnace(server, smeltAt, "minecraft:iron_ore", "minecraft:coal");
            loadFurnace(server, heldAt, "minecraft:iron_ore", "minecraft:coal");

            CopyOnWriteArrayList<FurnaceSmeltEvent> seen = new CopyOnWriteArrayList<>();
            EventSubscription sub = server.events().on(FurnaceSmeltEvent.class, event -> {
                seen.add(event);
                if (event.position().equals(heldAt)) {
                    event.cancel(); // the held furnace never commits a smelt
                }
            });

            await(() -> !seen.isEmpty(), "the smelt observation arrived");
            FurnaceSmeltEvent first = seen.get(0);
            assertEquals(server.world().name(), first.worldName());
            assertEquals("minecraft:iron_ore",
                    first.input().type().identifier().toString());
            assertEquals("minecraft:iron_ingot",
                    first.result().type().identifier().toString());
            assertEquals(1, first.result().count());

            // The uncancelled twin completed its smelt: the output holds
            // the ingot, the input is spent.
            FurnaceBlockEntity smelter = server.furnaceManager().at(smeltAt);
            await(() -> !smelter.output().isEmpty(), "the uncancelled smelt produced");
            assertEquals("minecraft:iron_ingot",
                    smelter.output().type().identifier().toString());

            // The cancelled twin held everything: the input survives, no
            // output ever appears (the reference's own reset spends the
            // progress; the furnace keeps burning its coal).
            FurnaceBlockEntity held = server.furnaceManager().at(heldAt);
            assertTrue(!held.output().isEmpty() == false
                            || held.output().isEmpty(),
                    "the held furnace never produced");
            assertTrue(held.input().count() >= 1,
                    "the held furnace kept its input");
            sub.unsubscribe();
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperPushObservationCarriesTheItemAndTheCancelKeepsIt() throws Exception {
        EngineServer server = boot();
        try {
            // The hopper faces down into the chest below it.
            BlockPosition hopperAt = new BlockPosition(0, 64, 0);
            BlockPosition chestAt = new BlockPosition(0, 63, 0);
            setBlock(server, chestAt, net.zaminmc.torch.server.block.BuiltinBlocks.CHEST);
            setBlock(server, hopperAt, HopperBlocks.hopperOf(0 /* DOWN */, true));
            await(() -> server.hopper().peek(hopperAt) != null, "the hopper state live");
            server.ticker().submit(() -> server.hopper().getOrCreate(hopperAt)
                    .setItem(0, itemStack("minecraft:diamond", 3)));

            AtomicReference<HopperTransferEvent> seen = new AtomicReference<>();
            EventSubscription sub = server.events().on(HopperTransferEvent.class, event -> {
                if (event.direction() == HopperTransferEvent.Direction.PUSH
                        && event.hopper().equals(hopperAt)) {
                    seen.set(event);
                    event.cancel();
                }
            });

            Thread.sleep(600); // several transfer cadences refused

            assertTrue(seen.get() != null, "the push observation arrived");
            assertEquals("minecraft:diamond",
                    seen.get().item().type().identifier().toString());
            assertEquals(1, seen.get().item().count(), "one item per transfer");
            assertEquals(chestAt, seen.get().counterpart(), "the faced chest");
            assertEquals(server.world().name(), seen.get().worldName());
            assertEquals(3, server.hopper().getOrCreate(hopperAt).getItem(0).count(),
                    "the cancelled push kept the whole stack");
            sub.unsubscribe();

            // Without the handler the items flow into the chest.
            ChestBlockEntity chest = server.chestManager().getOrCreate(chestAt);
            await(() -> chestTotal(chest) > 0, "the push goes through after unsubscribe");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperPullObservationCarriesTheItem() throws Exception {
        EngineServer server = boot();
        try {
            // The chest sits above the hopper: the pull arm.
            BlockPosition hopperAt = new BlockPosition(0, 64, 0);
            BlockPosition chestAt = new BlockPosition(0, 65, 0);
            setBlock(server, hopperAt, HopperBlocks.hopperOf(0 /* DOWN */, true));
            setBlock(server, chestAt, net.zaminmc.torch.server.block.BuiltinBlocks.CHEST);
            await(() -> server.hopper().peek(hopperAt) != null, "the hopper state live");
            server.ticker().submit(() -> server.chestManager().getOrCreate(chestAt)
                    .setSlot(0, itemStack("minecraft:gold_ingot", 2)));

            AtomicReference<HopperTransferEvent> seen = new AtomicReference<>();
            EventSubscription sub = server.events().on(HopperTransferEvent.class, event -> {
                if (event.direction() == HopperTransferEvent.Direction.PULL
                        && event.hopper().equals(hopperAt)) {
                    seen.set(event);
                }
            });

            await(() -> seen.get() != null, "the pull observation arrived");
            assertEquals("minecraft:gold_ingot",
                    seen.get().item().type().identifier().toString());
            assertEquals(chestAt, seen.get().counterpart(), "the container above");
            sub.unsubscribe();
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCancelledSuckKeepsTheItemEntity() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition hopperAt = new BlockPosition(0, 64, 0);
            setBlock(server, hopperAt, HopperBlocks.hopperOf(0 /* DOWN */, true));
            await(() -> server.hopper().peek(hopperAt) != null, "the hopper state live");

            // The item entity in the hopper's collection box (the cell above).
            server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                    new Position(hopperAt.x(), hopperAt.y() + 1, hopperAt.z()),
                    itemStack("minecraft:emerald", 1), 10));

            AtomicReference<HopperTransferEvent> seen = new AtomicReference<>();
            EventSubscription sub = server.events().on(HopperTransferEvent.class, event -> {
                if (event.direction() == HopperTransferEvent.Direction.SUCK
                        && event.hopper().equals(hopperAt)) {
                    seen.set(event);
                    event.cancel();
                }
            });

            // The cancelled window: the item passes THROUGH the
            // collection band (this engine's item physics falls through
            // the funnel's partial shape — a known vanilla gap, ledgered
            // with the interaction wave); the in-band cadences fire and
            // are refused while it is there.
            await(() -> seen.get() != null, "the suck observation arrived");
            HopperBlockEntity hopper = server.hopper().getOrCreate(hopperAt);
            int total = 0;
            for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT; slot++) {
                total += hopper.getItem(slot).count();
            }
            assertEquals(0, total, "the cancelled suck kept the hopper empty");
            sub.unsubscribe();

            // The uncancelled path: a FRESH item in the band is sucked on
            // its first in-band cadence.
            server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                    new Position(hopperAt.x(), hopperAt.y() + 1, hopperAt.z()),
                    itemStack("minecraft:emerald", 1), 10));
            await(() -> {
                int count = 0;
                for (int slot = 0; slot < HopperBlockEntity.SLOT_COUNT; slot++) {
                    count += server.hopper()
                            .getOrCreate(hopperAt).getItem(slot).count();
                }
                return count > 0;
            }, "the uncancelled suck lands its first in-band cadence");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static net.zaminmc.torch.item.ItemStack itemStack(String id, int count) {
        return net.zaminmc.torch.item.ItemStack.of(
                BuiltinItems.lookup(net.zaminmc.torch.util.Identifier.parse(id))
                        .orElseThrow(), count);
    }

    private static int chestTotal(ChestBlockEntity chest) {
        int total = 0;
        for (int slot = 0; slot < 27; slot++) {
            total += chest.getItem(slot).count();
        }
        return total;
    }

    private static void loadFurnace(EngineServer server, BlockPosition at,
                                    String inputId, String fuelId) throws InterruptedException {
        server.ticker().submit(() -> {
            FurnaceBlockEntity furnace = server.furnaceManager().getOrCreate(at);
            furnace.quickMoveIn(itemStack(inputId, 1));
            furnace.quickMoveIn(itemStack(fuelId, 1));
        });
        // The fuel lands but the ignite consumes it within a tick — the
        // INPUT is the stable load signal (it survives until the smelt).
        await(() -> server.furnaceManager().at(at) != null
                        && !server.furnaceManager().at(at).input().isEmpty(),
                "the furnace loaded (input " + inputId + ", fuel " + fuelId + ")");
    }

    private static void setBlock(EngineServer server, BlockPosition at,
                                 net.zaminmc.torch.block.BlockType type)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(at, type));
        await(() -> server.world().getBlock(at).equals(type), "the block placed at " + at);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "fhe" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static void await(BooleanSupplier condition, String description)
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
