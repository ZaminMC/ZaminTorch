package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.player.ContainerOpenEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.chest.EnderChestBlocks;
import net.zaminmc.torch.server.dispenser.DispenserBlocks;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Identifier;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The event bridge's inventory family over the live engine (§28.7 — the
 * slice-4 lane): the three container families the slice-2 record named as
 * the honest gaps, every legacy object built over the adapter's own
 * org.bukkit.inventory views. The chest open (the views' data lines — the
 * type, the size, the title, the position-keyed holder, the bottom player
 * inventory, the viewers, the cursor — plus the cancellation mapped onto
 * the engine's OWN close walk: the window state clears and the lid closes
 * through the engine's own listener surface), the hopper transfers (the
 * PUSH arm with the hopper as source-and-initiator, the PULL arm with the
 * container above as source, the both-ways cancellation keeping the item
 * where it was through the engine's own per-item refusal), the dispense
 * bridge (the block, the item, the zero-vector velocity record, the
 * cancellation keeping the stack untouched, and the readable-not-applied
 * rewrite arms — the engine's own stack still launches), the write path
 * (the legacy setItem through the view landing in the engine's own block
 * entity), the container-kind map (the hopper, the dispenser, the dropper,
 * the furnace, the brewing stand, the ender chest — each open carrying its
 * own view shape), and the honest-gap tripwires (the crafting-table open
 * fires the NATIVE shared event but never the legacy form, the hopper's
 * SUCK arm never bridges, no legacy close event ever fires).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(7)
class BukkitInventoryBridgeTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void theChestOpenBridgeCarriesTheViewsAndCancelsThroughTheCloseWalk() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession opener = joinPlaying(harness, "ChestProbe");
        // The plain chest with a known stack: the views' data lines ride
        // the open event (the type, the size, the title, the holder's own
        // position identity, the bottom player inventory, the viewer).
        BlockPosition chest = new BlockPosition(70, 40, 70);
        setCell(harness, chest, "minecraft:chest");
        loadChestSlot(harness, chest, 0, "minecraft:diamond", 3);
        moveBeside(harness, opener, chest);
        harness.server.useItemOnBlock(opener, chest, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=ChestProbe type=CHEST size=27 title=Chest"
                        + " holder=TorchContainerHolder{world=itest,"
                        + " position=BlockPosition[x=70, y=40, z=70]}"
                        + " bottom=PLAYER bottomSize=36 viewers=1 first=DIAMONDx3"),
                "the bridged open carried the whole view family");
        assertTrue(harness.probeLogContains("cursor=null primary=true"),
                "the cursor read bridged (null when empty) and the dispatch ran"
                        + " on the owner context");
        assertTrue(opener.openContainerWindowId() >= 0,
                "the uncancelled open kept the engine's window state");
        // The cancel twin: the gold-block marker names the cancel chest; the
        // legacy cancellation maps onto the engine's OWN close walk — the
        // window state clears and the chest's lid closes for the other
        // observers through the engine's own listener surface.
        List<String> lidRows = new CopyOnWriteArrayList<>();
        harness.server.addChestLidListener((position, session, open) ->
                lidRows.add(position + "=" + open));
        BlockPosition cancelChest = new BlockPosition(72, 40, 72);
        setCell(harness, cancelChest, "minecraft:chest");
        loadChestSlot(harness, cancelChest, 0, "minecraft:gold_block", 1);
        moveBeside(harness, opener, cancelChest);
        harness.server.useItemOnBlock(opener, cancelChest, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains("cancel:open"),
                "the legacy listener cancelled the open");
        harness.await(() -> opener.openContainerWindowId() < 0,
                "the engine's own close walk cleared the just-opened window"
                        + " (the cancellation's mapped target)");
        harness.await(() -> lidRows.contains("BlockPosition[x=72, y=40, z=72]=false"),
                "the close walk closed the chest's lid through the engine's"
                        + " own listener surface");
        // The write arm: the wool marker names the write chest — the legacy
        // setItem through the view lands in the engine's own block entity.
        BlockPosition writeChest = new BlockPosition(94, 40, 94);
        setCell(harness, writeChest, "minecraft:chest");
        loadChestSlot(harness, writeChest, 0, "minecraft:wool", 1);
        moveBeside(harness, opener, writeChest);
        harness.server.useItemOnBlock(opener, writeChest, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains("open:inv-write"),
                "the probe's write arm ran through the open view");
        harness.await(() -> {
            var entity = harness.server.chests().peek(writeChest);
            return entity != null && entity.getItem(1).count() == 2
                    && entity.getItem(1).type().identifier().value().equals("gold_ingot");
        }, "the legacy setItem through the view landed in the engine's chest");
        harness.server.closeWindow(opener, opener.openContainerWindowId() >= 0
                ? opener.openContainerWindowId() : 0);
    }

    @Test
    @Order(2)
    void theHopperPushBridgeCarriesTheTwoEndsAndTheItemLands() throws Exception {
        BukkitTestHarness harness = harness();
        // The push: the hopper facing down into the chest below — the
        // legacy move event carries the hopper as BOTH the source and the
        // initiator (didSourceInitiate=true), the chest as the
        // destination, and the engine's own one-item law (x1 per event).
        BlockPosition hopper = new BlockPosition(75, 40, 75);
        BlockPosition below = new BlockPosition(75, 39, 75);
        setCell(harness, below, "minecraft:chest");
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                hopper, HopperBlocks.hopperOf(0, true)));
        harness.await(() -> HopperBlocks.isHopper(blockType(harness, hopper)),
                "the hopper placed");
        loadHopperSlot(harness, hopper, 0, "minecraft:diamond", 3);
        harness.await(() -> harness.probeLogContains(
                "move:item source=HOPPER/5 dest=CHEST/27 initiator=HOPPER"
                        + " item=DIAMONDx1 primary=true"),
                "the bridged push carried both ends' views and the initiator");
        harness.await(() -> {
            var entity = harness.server.chests().peek(below);
            return entity != null && entity.getItem(0).count() >= 1;
        }, "the uncancelled push landed the item in the chest");
    }

    @Test
    @Order(3)
    void theHopperTransferCancellationKeepsTheItemAtHome() throws Exception {
        BukkitTestHarness harness = harness();
        // The cancelled twin: the emerald marker names the blocked
        // transfer — the legacy cancel maps onto the engine's own per-item
        // refusal (THIS item stays, the walk's own rules continue around
        // it, the cadence retries and cancels again — the emeralds never
        // leave).
        BlockPosition hopper = new BlockPosition(77, 40, 77);
        BlockPosition below = new BlockPosition(77, 39, 77);
        setCell(harness, below, "minecraft:chest");
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                hopper, HopperBlocks.hopperOf(0, true)));
        harness.await(() -> HopperBlocks.isHopper(blockType(harness, hopper)),
                "the cancel hopper placed");
        loadHopperSlot(harness, hopper, 0, "minecraft:emerald", 5);
        harness.await(() -> harness.probeLogContains(
                "move:item source=HOPPER/5 dest=CHEST/27 initiator=HOPPER"
                        + " item=EMERALDx1"),
                "the emerald push event dispatched");
        harness.await(() -> harness.probeLogContains("cancel:move-item"),
                "the legacy listener cancelled the emerald transfer");
        Thread.sleep(400); // several retry cadences, all cancelled
        assertEquals(5, hopperSlot(harness, hopper, 0).count(),
                "the cancelled transfer kept the emeralds in the hopper");
        var chest = harness.server.chests().peek(below);
        assertTrue(chest == null || chest.getItem(0).isEmpty(),
                "the cancelled transfer never landed anything in the chest");
    }

    @Test
    @Order(4)
    void theHopperPullBridgeCarriesTheContainerAboveAsSource() throws Exception {
        BukkitTestHarness harness = harness();
        // The pull: the chest ABOVE the hopper, the hopper facing down into
        // the floor (no push target) — the legacy move event carries the
        // container above as the SOURCE, the hopper as the destination,
        // and the initiator answers the hopper (didSourceInitiate=false:
        // the legacy row's own law).
        BlockPosition hopper = new BlockPosition(79, 40, 79);
        BlockPosition above = new BlockPosition(79, 41, 79);
        setCell(harness, above, "minecraft:chest");
        loadChestSlot(harness, above, 0, "minecraft:diamond", 3);
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                hopper, HopperBlocks.hopperOf(0, true)));
        harness.await(() -> HopperBlocks.isHopper(blockType(harness, hopper)),
                "the pull hopper placed");
        harness.await(() -> harness.probeLogContains(
                "move:item source=CHEST/27 dest=HOPPER/5 initiator=HOPPER"
                        + " item=DIAMONDx1 primary=true"),
                "the bridged pull carried the container above as the source");
        harness.await(() -> hopperSlot(harness, hopper, 0).count() >= 1,
                "the uncancelled pull landed the item in the hopper");
    }

    @Test
    @Order(5)
    void theDispenseBridgeCarriesTheItemAndTheZeroVelocityRecord() throws Exception {
        BukkitTestHarness harness = harness();
        // The powered dispenser (the slice-2 lever pattern): the legacy
        // event carries the block, the item value view, and the
        // zero-vector velocity record (the engine's native event carries
        // no velocity row — the reference computes its launch inside the
        // behavior walk).
        dispenseFixture(harness, new BlockPosition(82, 40, 82), "minecraft:diamond", 3);
        harness.await(() -> harness.probeLogContains(
                "dispense: at=(82,40,82) item=DIAMONDx3 velocity=0.0,0.0,0.0"
                        + " primary=true"),
                "the bridged dispense carried the item and the zero velocity");
        harness.await(() -> itemEntityNear(harness, 82, 41, 82, "diamond"),
                "the default drop launched the item entity through the engine's"
                        + " own behavior walk");
        // The cancelled twin: the emerald marker names the blocked dispense
        // — the stack stays in its slot untouched and nothing spawns.
        dispenseFixture(harness, new BlockPosition(86, 40, 86), "minecraft:emerald", 2);
        harness.await(() -> harness.probeLogContains("cancel:dispense"),
                "the legacy listener cancelled the emerald dispense");
        Thread.sleep(300); // the reaction settled
        assertEquals(2, dispenserSlot(harness, new BlockPosition(86, 40, 86), 0).count(),
                "the cancelled dispense kept the stack in its slot");
        assertFalse(itemEntityNear(harness, 86, 41, 86, "emerald"),
                "the cancelled dispense spawned nothing");
        // The rewrite twin (the readable-not-applied evidence): the probe
        // rewrites the item AND the velocity — the engine's own stack still
        // launches (the original material, never the rewrite).
        dispenseFixture(harness, new BlockPosition(88, 40, 88), "minecraft:diamond", 1);
        harness.await(() -> harness.probeLogContains("dispense:rewrite"),
                "the probe's rewrite arms ran");
        harness.await(() -> itemEntityNear(harness, 88, 41, 88, "diamond"),
                "the engine's own stack launched (the rewrite was not applied)");
        assertFalse(itemEntityNear(harness, 88, 41, 88, "stone"),
                "the rewritten item never launched (readable-not-applied)");
    }

    @Test
    @Order(6)
    void theContainerKindMapCarriesEveryModeledFamily() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession opener = joinPlaying(harness, "KindProbe");
        // The hopper's own window: 5 slots, the hopper title.
        BlockPosition hopper = new BlockPosition(76, 40, 76);
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                hopper, HopperBlocks.hopperOf(0, true)));
        harness.await(() -> HopperBlocks.isHopper(blockType(harness, hopper)),
                "the hopper placed");
        moveBeside(harness, opener, hopper);
        harness.server.useItemOnBlock(opener, hopper, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=HOPPER size=5 title=Hopper"),
                "the hopper open carried its own view shape");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
        // The dispenser's own window: 9 slots (the block names the family
        // member — the dispenser type, not the dropper's).
        BlockPosition dispenser = new BlockPosition(82, 40, 82);
        moveBeside(harness, opener, dispenser);
        harness.server.useItemOnBlock(opener, dispenser, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=DISPENSER size=9 title=Dispenser"),
                "the dispenser open carried its own view shape");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
        // The dropper's own window: the SAME container, its OWN type.
        BlockPosition dropper = new BlockPosition(89, 40, 89);
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                dropper, DispenserBlocks.dropperOf(0, false)));
        harness.await(() -> DispenserBlocks.isDropper(blockType(harness, dropper)),
                "the dropper placed");
        moveBeside(harness, opener, dropper);
        harness.server.useItemOnBlock(opener, dropper, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=DROPPER size=9 title=Dropper"),
                "the dropper open carried its own family member's type");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
        // The furnace's own window: 3 slots (input, fuel, output).
        BlockPosition furnace = new BlockPosition(85, 40, 85);
        setCell(harness, furnace, "minecraft:furnace");
        moveBeside(harness, opener, furnace);
        harness.server.useItemOnBlock(opener, furnace, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=FURNACE size=3 title=Furnace"),
                "the furnace open carried its own view shape");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
        // The brewing stand's own window: 4 slots (three bottles + the
        // ingredient).
        BlockPosition stand = new BlockPosition(87, 40, 87);
        setCell(harness, stand, "minecraft:brewing_stand");
        moveBeside(harness, opener, stand);
        harness.server.useItemOnBlock(opener, stand, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=BREWING size=4 title=Brewing"),
                "the brewing stand open carried its own view shape");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
        // The ender chest's own window: the session's PRIVATE 27-slot grid,
        // the holder the player (the ender form's own law), the door cell
        // naming the window.
        BlockPosition door = new BlockPosition(91, 40, 91);
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                door, EnderChestBlocks.enderChestOf(3)));
        harness.await(() -> EnderChestBlocks.isFamily(blockType(harness, door)),
                "the ender chest door placed");
        moveBeside(harness, opener, door);
        harness.server.useItemOnBlock(opener, door, 1,
                java.util.Optional.empty(), id -> { });
        harness.await(() -> harness.probeLogContains(
                "open:inv player=KindProbe type=ENDER_CHEST size=27"
                        + " title=Ender Chest holder=TorchPlayer{name=KindProbe}"),
                "the ender chest open carried the private-grid view (the holder"
                        + " the player)");
        harness.server.closeWindow(opener, opener.openContainerWindowId());
    }

    @Test
    @Order(7)
    void theUnbridgedFormsStayHonestGaps() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession opener = joinPlaying(harness, "GapProbe");
        // The crafting table: the engine's own open walk fires the NATIVE
        // shared event (the kind names the family) — the bridge models no
        // view of the session's crafting grid, so the legacy form never
        // dispatches (the recorded §27.9 gap).
        AtomicReference<String> nativeKind = new AtomicReference<>();
        EventSubscription nativeSub = harness.server.events().on(
                ContainerOpenEvent.class, event -> nativeKind.set(event.kind()));
        try {
            BlockPosition table = new BlockPosition(90, 40, 90);
            setCell(harness, table, "minecraft:crafting_table");
            moveBeside(harness, opener, table);
            harness.server.useItemOnBlock(opener, table, 1,
                    java.util.Optional.empty(), id -> { });
            harness.await(() -> "CRAFTING_TABLE".equals(nativeKind.get()),
                    "the engine's own shared open event fired for the table");
        } finally {
            nativeSub.unsubscribe();
        }
        Thread.sleep(400); // any wrongly-bridged dispatch would have landed
        assertFalse(harness.probeLogContains("type=WORKBENCH"),
                "the crafting-table open never dispatched the legacy form");
        // The hopper's SUCK arm: the engine's own surface works (the whole
        // item stack lands in the hopper), but the bridge models no
        // Inventory view of an item ENTITY — the legacy move event never
        // fires for it (the whole-stack law: a wrongly-bridged suck would
        // carry x10, the push/pull arms always carry x1).
        BlockPosition hopper = new BlockPosition(93, 40, 93);
        harness.server.ticker().submit(() -> harness.server.world().setBlock(
                hopper, HopperBlocks.hopperOf(0, true)));
        harness.await(() -> HopperBlocks.isHopper(blockType(harness, hopper)),
                "the suck hopper placed");
        harness.server.ticker().submit(() ->
                harness.server.itemEntities().spawnDropAtBlock(
                        new net.zaminmc.torch.util.Position(93, 41, 93),
                        net.zaminmc.torch.item.ItemStack.of(
                                BuiltinItems.lookup(Identifier.parse("minecraft:diamond"))
                                        .orElseThrow(), 10),
                        net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS));
        harness.await(() -> hopperSlot(harness, hopper, 0).count() == 10,
                "the engine's own suck surface swallowed the whole stack");
        Thread.sleep(300); // the wrongly-bridged dispatch would have landed
        assertFalse(harness.probeLogContains("item=DIAMONDx10"),
                "the SUCK arm never dispatched the legacy move form (the"
                        + " item-entity source has no Inventory view)");
        // The global tripwire: no unbridged family faked a dispatch
        // anywhere in the whole run (this lane's additions included — the
        // close form, the unmodeled open kinds).
        for (String line : harness.probeLogLines()) {
            assertFalse(line.contains("UNBRIDGED-FIRED:"),
                    "no unbridged family faked a dispatch: " + line);
        }
        // The engine kept its own window law through the whole run.
        harness.server.closeWindow(opener, opener.openContainerWindowId() >= 0
                ? opener.openContainerWindowId() : 0);
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The full join walk (the slice-2 helper's own shape): the joinRequest
     * PLUS the wire-handshake completion — the use-on-block walks need the
     * PLAYING state.
     */
    private static PlayerSession joinPlaying(BukkitTestHarness harness, String name)
            throws InterruptedException {
        PlayerSession session = harness.join(name);
        harness.server.joinCompleted(session);
        harness.await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the session reaches PLAYING (the interact gate's own law)");
        return session;
    }

    private static void setCell(BukkitTestHarness harness, BlockPosition at, String id)
            throws InterruptedException {
        var type = harness.server.blocks().byKey(id).orElseThrow();
        harness.server.ticker().submit(() -> harness.server.world().setBlock(at, type));
        harness.await(() -> id.equals(blockId(harness, at)), "the cell is " + id);
    }

    /**
     * Moves the player beside the cell (the core suites' own pattern): the
     * use walk's 8-block body gate reads the session's live position — a
     * player at the world spawn cannot use a cell forty blocks out.
     */
    private static void moveBeside(BukkitTestHarness harness, PlayerSession session,
            BlockPosition at) {
        harness.server.ticker().submit(() -> session.applyMovement(
                new net.zaminmc.torch.util.Position(at.x() + 0.5, at.y(), at.z() + 2.5),
                net.zaminmc.torch.util.Rotation.ZERO, true));
    }

    private static String blockId(BukkitTestHarness harness, BlockPosition at) {
        var type = blockType(harness, at);
        return type == null ? null : type.identifier().toString();
    }

    private static net.zaminmc.torch.block.BlockType blockType(
            BukkitTestHarness harness, BlockPosition at) {
        return harness.server.world().getBlock(at);
    }

    private static void loadChestSlot(BukkitTestHarness harness, BlockPosition at,
            int slot, String itemId, int count) {
        harness.server.ticker().submit(() ->
                harness.server.chests().getOrCreate(at).setSlot(slot,
                        net.zaminmc.torch.item.ItemStack.of(
                                BuiltinItems.lookup(Identifier.parse(itemId)).orElseThrow(),
                                count)));
    }

    private static void loadHopperSlot(BukkitTestHarness harness, BlockPosition at,
            int slot, String itemId, int count) {
        harness.server.ticker().submit(() ->
                harness.server.hopper().getOrCreate(at).setItem(slot,
                        net.zaminmc.torch.item.ItemStack.of(
                                BuiltinItems.lookup(Identifier.parse(itemId)).orElseThrow(),
                                count)));
    }

    private static net.zaminmc.torch.item.ItemStack hopperSlot(
            BukkitTestHarness harness, BlockPosition at, int slot) {
        var entity = harness.server.hopper().peek(at);
        return entity == null ? net.zaminmc.torch.item.ItemStack.EMPTY : entity.getItem(slot);
    }

    private static net.zaminmc.torch.item.ItemStack dispenserSlot(
            BukkitTestHarness harness, BlockPosition at, int slot) {
        var entity = harness.server.dispensers().peek(at);
        return entity == null ? net.zaminmc.torch.item.ItemStack.EMPTY : entity.getItem(slot);
    }

    /** The powered-dispenser fixture (the engine test's own lever shape). */
    private static void dispenseFixture(BukkitTestHarness harness, BlockPosition at,
            String itemId, int count) throws InterruptedException {
        harness.server.ticker().submit(() -> {
            harness.server.world().setBlock(at, DispenserBlocks.dispenserOf(1, false));
            harness.server.dispensers().getOrCreate(at).setItem(0,
                    net.zaminmc.torch.item.ItemStack.of(
                            BuiltinItems.lookup(Identifier.parse(itemId)).orElseThrow(),
                            count));
            // The lever's stone mounts beside the dispenser: a powered
            // lever strongly drives the stone into it.
            harness.server.world().setBlock(at.offset(1, 0, 0), BuiltinBlocks.STONE);
            harness.server.world().setBlock(at.offset(2, 0, 0),
                    RedstoneBlocks.leverOf(1, false));
        });
        harness.await(() -> DispenserBlocks.isFamily(blockType(harness, at)),
                "the dispenser placed at " + at);
        harness.server.ticker().submit(() ->
                harness.server.redstone().useLever(at.offset(2, 0, 0)));
    }

    /** Whether an item entity of the material stands in the cell's box (the engine's own all() scan). */
    private static boolean itemEntityNear(BukkitTestHarness harness, int x, int y, int z,
            String itemPath) {
        for (var entity : harness.server.itemEntities().all()) {
            var position = entity.position();
            if (Math.abs(position.x() - (x + 0.5)) <= 1.0
                    && Math.abs(position.y() - (y + 0.5)) <= 1.0
                    && Math.abs(position.z() - (z + 0.5)) <= 1.0
                    && entity.stack().type().identifier().value().equals(itemPath)) {
                return true;
            }
        }
        return false;
    }
}
