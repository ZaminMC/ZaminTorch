package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The double chest's merged inventory — the eighteenth verifier pass's
 * F-DC1 finding, the four confirmed surfaces over ONE root (the reference's
 * {@code ChestBlock.getInventory}, lines 339-370):
 *
 * <ol>
 *  <li>THE JOIN ORDER — a WEST/NORTH twin comes FIRST in the merged view
 *      ({@code DoubleInventory(neighbor, this)}, lines 360-364): a hopper
 *      above the EAST half fills the WEST half first, exactly like the
 *      GUI's left half being the western one.</li>
 *  <li>THE TRANSFER SURFACE — {@code HopperBlockEntity.getInventoryAt}
 *      lines 460-463 rides getInventory: the hopper/dropper consult sees
 *      the MERGED 54 slots (a push through a full first half lands in the
 *      second), and a LOCKED view (a solid block or a sitting ocelot above
 *      either half) is no transfer surface at all.</li>
 *  <li>THE COMPARATOR — {@code ChestBlock.getAnalogSignal} line 430: the
 *      MERGED pair's fullness over 54 slots, read from EITHER half; and
 *      the pair's mark-dirty (DoubleInventory lines 99-102) wakes the
 *      comparators reading BOTH halves.</li>
 *  <li>THE PLACEMENT — {@code ChestBlock.canBePlaced} lines 244-284: a
 *      chest never places beside a chest that is itself already doubled
 *      (no third chest extends a pair).</li>
 * </ol>
 *
 * <p>Plus the GUI seat: the merged 54-slot window ({@code DoubleInventory}
 * over ChestMenu — 54 container slots, then main 27, then hotbar 9), pinned
 * at the open exactly like the reference's one-time DoubleInventory
 * construction, routing clicks into both halves.</p>
 */
class DoubleChestParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // Seat 1 — the join order: the WEST twin is the FIRST half
    // ------------------------------------------------------------------

    /**
     * A hopper above the EAST half of an east-west pair pushes into the
     * WEST half's slot 0: the east chest's twin scan finds the WEST
     * neighbour, and the reference constructs
     * {@code DoubleInventory(neighbor, this)} — the NEIGHBOUR (west) is
     * inventory1, its slot 0 the merged slot 0. The pre-fix engine
     * consulted the ONE clicked half: the item landed in the east chest.
     */
    @Test
    void theHopperAboveTheEastHalfFillsTheWestHalfFirst() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setItem(0, ItemStack.of(BuiltinItems.DIAMOND, 3));
            });
            await(() -> chestCount(server, x - 1, y, z, 0) == 1,
                    "the FIRST half (the WEST twin) took the item");
            await(() -> chestCount(server, x - 1, y, z, 0) == 2,
                    "the SECOND item followed into the west half too");
            assertEquals(0, chestCount(server, x, y, z, 0),
                    "the SECOND half (the clicked east chest) stayed empty");
            assertEquals(1, hopperCount(server, x, y + 1, z, 0),
                    "the hopper kept the last one");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the merged transfer surface spans both halves
    // ------------------------------------------------------------------

    /**
     * With the CLICKED half (east) FULL, a hopper above it pushes AROUND
     * into the FIRST half's slot 0 (the merged view's own slot 0 — the
     * west twin): the reference's insert walk runs the DoubleInventory's
     * whole 54-slot range, so a full clicked half never blocks the push.
     * The pre-fix engine saw the one clicked half alone: a full east meant
     * the item never moved.
     */
    @Test
    void theMergedTransferSurfaceSpansBothHalves() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                var east = server.chestManager().getOrCreate(new BlockPosition(x, y, z));
                for (int slot = 0; slot < ChestBlockEntity.SLOT_COUNT; slot++) {
                    east.setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
                }
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setItem(0, ItemStack.of(BuiltinItems.DIAMOND, 2));
            });
            await(() -> chestCount(server, x - 1, y, z, 0) == 1,
                    "the push went AROUND the full clicked half into the west twin");
            assertEquals(64, chestCount(server, x, y, z, 26),
                    "the full east half stayed whole");
            assertEquals(1, hopperCount(server, x, y + 1, z, 0),
                    "the hopper kept the second item");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the comparator reads the MERGED pair's fullness
    // ------------------------------------------------------------------

    /**
     * Thirteen full stacks in the WEST half, the east half empty, the
     * comparator on the EAST half: the merged fullness is
     * {@code floor(13/54 * 14) + 1 = 4} — the reference's
     * {@code InventoryMenu.getAnalogSignal} over the DoubleInventory's 54
     * slots. The pre-fix engine read the one half it faced: 0.
     */
    @Test
    void theComparatorReadsTheMergedPairFullness() throws Exception {
        EngineServer server = boot();
        try {
            int x = 50, z = 50, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x - 1, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                for (int dx = 1; dx <= 3; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x + 3, y, z),
                        RedstoneBlocks.wireOfPower(0));
                var west = server.chestManager().getOrCreate(new BlockPosition(x - 1, y, z));
                for (int slot = 0; slot < 13; slot++) {
                    west.setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
                }
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 1, y, z)),
                    "the comparator placed");
            await(() -> wirePower(server, x + 2, y, z) == 4,
                    "the comparator reads the MERGED pair's fullness 4 (13 stacks over 54)");
            await(() -> wirePower(server, x + 3, y, z) == 3,
                    "the wire chain decays: 4 - 1 = 3");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the pair's mark-dirty wakes BOTH halves' comparators
    // ------------------------------------------------------------------

    /**
     * The DoubleInventory's markDirty (lines 99-102) fires BOTH halves: a
     * write through the pair re-arms the comparators reading either half.
     * The west half holds 27 full stacks (the merged fullness 8); four
     * shift-clicks of full stacks into the merged window land in the EAST
     * half (the west is full) and lift the merged fullness to
     * {@code floor(31/54 * 14) + 1 = 9} — the comparator reading the WEST
     * half must wake and follow, though the writes landed in the east.
     */
    @Test
    void theComparatorOnTheFirstHalfWakesWhenTheSecondHalfReceives() throws Exception {
        EngineServer server = boot();
        try {
            int x = 70, z = 70, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.CHEST);
                for (int dx = -2; dx <= 0; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_EAST, false, false));
                server.world().setBlock(new BlockPosition(x - 2, y, z),
                        RedstoneBlocks.wireOfPower(0));
                var west = server.chestManager().getOrCreate(new BlockPosition(x, y, z));
                for (int slot = 0; slot < ChestBlockEntity.SLOT_COUNT; slot++) {
                    west.setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
                }
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x - 1, y, z)),
                    "the comparator placed");
            await(() -> wirePower(server, x - 2, y, z) == 8,
                    "the west half full reads the merged fullness 8 (27/54)");

            PlayerSession player = join(server, "Wakeful",
                    new Position(x + 3.5, y + 1, z + 0.5));
            await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
            // Four full stacks in the main inventory (engine slots 9-12 =
            // the merged window's wire slots 54-57).
            server.ticker().submit(() -> {
                for (int slot = 9; slot <= 12; slot++) {
                    player.inventory().setSlot(slot, ItemStack.of(BuiltinItems.STONE, 64));
                }
            });
            AtomicInteger windowId = new AtomicInteger(-1);
            server.useItemOnBlock(player, new BlockPosition(x + 1, y, z), 1,
                    java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the merged window opened");
            assertEquals(new BlockPosition(x, y, z), player.openContainerPairFirst(),
                    "the pinned FIRST half is the west chest");
            assertEquals(new BlockPosition(x + 1, y, z), player.openContainerPairSecond(),
                    "the pinned SECOND half is the clicked east chest");
            // Four shift-clicks: each lands a full stack in the east half.
            for (int wireSlot = 54; wireSlot <= 57; wireSlot++) {
                int slot = wireSlot;
                AtomicReference<Boolean> answered = new AtomicReference<>();
                server.windowClick(player, windowId.get(), slot, 0, 1, ok -> answered.set(ok));
                await(() -> answered.get() != null, "the shift-click at wire " + slot + " answered");
                assertTrue(Boolean.TRUE.equals(answered.get()),
                        "the shift-click at wire " + slot + " was accepted");
            }
            await(() -> wirePower(server, x - 2, y, z) == 9,
                    "the WEST half's comparator woke and followed the merged fullness 9");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 5 — the placement's no-triple gate
    // ------------------------------------------------------------------

    /**
     * ChestBlock.canBePlaced lines 244-284: a chest never places beside a
     * chest that is itself already doubled — the third chest beside a pair
     * (one same-type neighbour, that neighbour itself doubled) refuses.
     * The pre-fix engine had no chest consult: triples placed freely.
     */
    @Test
    void thePlacementRefusesTheThirdChestBesideAPair() throws Exception {
        EngineServer server = boot();
        try {
            int x = 90, z = 90, y = 5;
            PlayerSession player = join(server, "Placer",
                    new Position(x + 3.5, y, z + 0.5));
            await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.CHEST);
                player.inventory().setSlot(0, ItemStack.of(
                        BuiltinItems.lookup(
                                net.zaminmc.torch.util.Identifier.parse("minecraft:chest"))
                                .orElseThrow(), 3));
                player.inventory().selectHotbarSlot(0);
            });
            await(() -> server.world().getBlock(new BlockPosition(x + 1, y, z))
                    .equals(BuiltinBlocks.CHEST), "the pair placed");
            // The third chest beside the pair's east half: the placement
            // rides the GROUND below the target cell (a chest click would
            // OPEN the neighbour, never place) — face 1 on the ground at
            // (x+2, y-1, z) targets (x+2, y, z).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x + 2, y - 1, z), BuiltinBlocks.STONE));
            await(() -> server.world().getBlock(new BlockPosition(x + 2, y - 1, z))
                    .equals(BuiltinBlocks.STONE), "the ground seed landed");
            server.useItemOnBlock(player, new BlockPosition(x + 2, y - 1, z), 1,
                    java.util.Optional.empty(), windowId -> { });
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 8, "the placement walk settles");
            assertEquals(server.world().airType(),
                    server.world().getBlock(new BlockPosition(x + 2, y, z)),
                    "the third chest never placed beside the doubled neighbour");
            assertEquals(3, player.inventory().held().count(),
                    "the refused placement paid nothing");
            // The control: the SAME chest places beside a SINGLE chest one
            // row over (a fresh neighbour, not itself doubled).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z + 4), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5), BuiltinBlocks.STONE);
            });
            await(() -> server.world().getBlock(new BlockPosition(x, y, z + 4))
                    .equals(BuiltinBlocks.CHEST), "the lone chest placed");
            server.useItemOnBlock(player, new BlockPosition(x, y - 1, z + 5), 1,
                    java.util.Optional.empty(), windowId -> { });
            await(() -> {
                net.zaminmc.torch.block.BlockType placed = server.world().getBlock(
                        new BlockPosition(x, y, z + 5));
                return placed.equals(BuiltinBlocks.CHEST)
                        || ChestFacingBlocks.isPlainFamily(placed);
            }, "the control placement beside a SINGLE chest still places");
            assertEquals(2, player.inventory().held().count(),
                    "the control placement paid one chest");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 6 — the merged GUI window over the pair
    // ------------------------------------------------------------------

    /**
     * The merged window: Open Window's slot count 54, the halves pinned in
     * the reference's join order (a WEST twin first), and the click routing
     * into BOTH halves — wire slots 0-26 the first half, 27-53 the second.
     * The pre-fix engine opened the single 27-slot window over the clicked
     * half alone (wire slot 27 was the PLAYER's main inventory slot 9).
     */
    @Test
    void theMergedWindowOpensOverThePairAndRoutesClicksIntoBothHalves() throws Exception {
        EngineServer server = boot();
        try {
            int x = 110, z = 110, y = 5;
            PlayerSession player = join(server, "Clicker",
                    new Position(x + 2.5, y, z + 0.5));
            await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
            BlockPosition west = new BlockPosition(x, y, z);
            BlockPosition east = new BlockPosition(x + 1, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(west, BuiltinBlocks.CHEST);
                server.world().setBlock(east, BuiltinBlocks.CHEST);
                server.chestManager().getOrCreate(west).setSlot(0,
                        ItemStack.of(BuiltinItems.REDSTONE, 3));
                server.chestManager().getOrCreate(east).setSlot(0,
                        ItemStack.of(BuiltinItems.REDSTONE, 7));
            });
            await(() -> server.world().getBlock(east).equals(BuiltinBlocks.CHEST),
                    "the pair placed");

            AtomicInteger windowId = new AtomicInteger(-1);
            server.useItemOnBlock(player, east, 1, java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the merged window opened");
            assertEquals(PlayerSession.ContainerKind.CHEST, player.openContainerKind());
            assertEquals(west, player.openContainerPairFirst(),
                    "the pinned FIRST half is the WEST twin (the join order)");
            assertEquals(east, player.openContainerPairSecond(),
                    "the pinned SECOND half is the clicked chest");

            // A mode-0 click on wire slot 0 picks up the FIRST half's slot 0
            // (the west chest's 3 redstone).
            AtomicReference<Boolean> first = new AtomicReference<>();
            server.windowClick(player, windowId.get(), 0, 0, 0, ok -> first.set(ok));
            await(() -> first.get() != null, "the first click answered");
            assertTrue(Boolean.TRUE.equals(first.get()), "the first click was accepted");
            assertEquals(3, player.inventory().cursor().count(),
                    "the cursor holds the WEST half's 3 redstone");
            assertEquals(0, chestCount(server, x, y, z, 0),
                    "the west half's slot 0 emptied");

            // Put it back, then click wire slot 27 — the SECOND half's slot
            // 0 (the east chest's 7). The pre-fix layout routed 27 into the
            // PLAYER's main inventory slot 9.
            server.windowClick(player, windowId.get(), 0, 0, 0, ok -> { });
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 2, "the return settles");
            assertEquals(0, player.inventory().cursor().count(),
                    "the cursor returned the stack");
            AtomicReference<Boolean> second = new AtomicReference<>();
            server.windowClick(player, windowId.get(), 27, 0, 0, ok -> second.set(ok));
            await(() -> second.get() != null, "the second click answered");
            assertTrue(Boolean.TRUE.equals(second.get()), "the second click was accepted");
            assertEquals(7, player.inventory().cursor().count(),
                    "the cursor holds the EAST half's 7 redstone (wire slot 27)");
            assertEquals(0, chestCount(server, x + 1, y, z, 0),
                    "the east half's slot 0 emptied");
            assertEquals(3, chestCount(server, x, y, z, 0),
                    "the west half's returned stack stayed");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 7 — the lock: a solid block above, never glass
    // ------------------------------------------------------------------

    /**
     * getInventory's isLocked (lines 402-421): a solid block above refuses
     * the whole view (no GUI, the comparator 0) — the reference's OWN
     * isSolid law (material.isSolidBlocking && isCube && !isSignalSource),
     * so GLASS above never locks (GlassBlock.isCube false). The pre-fix
     * engine opened every chest unconditionally.
     */
    @Test
    void theSolidBlockAboveLocksTheViewButGlassDoesNot() throws Exception {
        EngineServer server = boot();
        try {
            int x = 130, z = 130, y = 5;
            PlayerSession player = join(server, "Locked",
                    new Position(x + 2.5, y, z + 0.5));
            await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
            BlockPosition chest = new BlockPosition(x, y, z);
            server.ticker().submit(() -> {
                server.world().setBlock(chest, BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y + 1, z), BuiltinBlocks.STONE);
                server.chestManager().getOrCreate(chest).setSlot(0,
                        ItemStack.of(BuiltinItems.STONE, 64));
                for (int dx = 1; dx <= 3; dx++) {
                    server.world().setBlock(new BlockPosition(x + dx, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.comparatorOf(RedstoneBlocks.FACING_WEST, false, false));
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isComparator(typeAt(server, x + 1, y, z)),
                    "the comparator placed");

            // The locked view: no window opens, the comparator reads 0.
            AtomicInteger opened = new AtomicInteger(0);
            server.useItemOnBlock(player, chest, 1, java.util.Optional.empty(),
                    id -> opened.incrementAndGet());
            net.zaminmc.torch.testing.EngineAwaits.awaitTicks(server.world(), 8, "the use settles");
            assertEquals(0, opened.get(), "the stone-locked chest never opened a window");
            assertEquals(PlayerSession.ContainerKind.NONE, player.openContainerKind(),
                    "no window state carried");
            await(() -> wirePower(server, x + 2, y, z) == 0,
                    "the locked view answers the comparator 0 (getInventory's null)");

            // GLASS above: not solid in the reference's law — the chest
            // opens, and the comparator (woken by the content poke — the
            // block-above change alone never wakes a non-adjacent
            // comparator, vanilla's own staleness) reads the fullness.
            server.ticker().submit(() -> {
                server.world().setBlock(
                        new BlockPosition(x, y + 1, z), BuiltinBlocks.GLASS);
                // The poke: one more stack (two over 27 -> fullness 2).
                server.chestManager().getOrCreate(chest).setSlot(1,
                        ItemStack.of(BuiltinItems.STONE, 64));
            });
            await(() -> server.world().getBlock(new BlockPosition(x, y + 1, z))
                    .equals(BuiltinBlocks.GLASS), "the glass landed");
            server.useItemOnBlock(player, chest, 1, java.util.Optional.empty(),
                    id -> opened.incrementAndGet());
            await(() -> opened.get() > 0, "the glass-topped chest opened");
            assertEquals(PlayerSession.ContainerKind.CHEST, player.openContainerKind());
            await(() -> wirePower(server, x + 2, y, z) == 2,
                    "the glass-topped chest reads its fullness 2 (two stacks over 27)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 8 — a locked chest is no transfer surface
    // ------------------------------------------------------------------

    /**
     * getInventoryAt's chest arm returns getInventory's NULL for a locked
     * view: a hopper BESIDE a stone-locked chest (facing into it) never
     * inserts — the item stays in the hopper. The pre-fix engine consulted
     * the chest's slots with no lock gate and transferred freely.
     */
    @Test
    void theLockedChestIsNotATransferSurface() throws Exception {
        EngineServer server = boot();
        try {
            int x = 150, z = 150, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y + 1, z), BuiltinBlocks.STONE);
                // The hopper beside the chest, facing EAST into it.
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        HopperBlocks.hopperOf(5, true));
                server.hopper().getOrCreate(new BlockPosition(x - 1, y, z))
                        .setItem(0, ItemStack.of(BuiltinItems.DIAMOND, 4));
            });
            // Three full seconds — many 8-tick transfer cycles: the locked
            // chest never takes the item.
            assertNeverWithin(3_000, () -> chestCount(server, x, y, z, 0) > 0,
                    "the stone-locked chest never received the hopper's item");
            assertEquals(4, hopperCount(server, x - 1, y, z, 0),
                    "the hopper kept its whole stack");
            // The control: the lock lifted, the transfer flows.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y + 1, z), server.world().airType()));
            await(() -> chestCount(server, x, y, z, 0) == 1,
                    "the unlocked chest received the first item");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 9 — the trapped pair's merged window forms (the pin)
    // ------------------------------------------------------------------

    /**
     * The merged window forms over a TRAPPED pair exactly like the plain
     * one (the same {@code block == this} twin scan over ChestBlock(1)):
     * the pinned halves land in the reference's join order. The
     * viewer-signal OBSERVATION (both halves' counts lift — the
     * DoubleInventory's onOpen walks both) is blocked by F-TC1, the
     * pre-existing isSignalSolid defect this pass's seat exposed: the
     * engine's signal consult routes the chest family into the
     * re-radiation arm (WorldSolidity.isSolid — the MOVEMENT consult)
     * where the reference's own law (World.getSignal line 2360 over
     * Block.isSolid = isSolidBlocking && isCube && !isSignalSource) reads
     * the chest's own getSignal — the viewer count. A wire beside an
     * OPEN single trapped chest reads 0 on develop (the scratch probe this
     * pass ran) where vanilla reads 1; F-TC1's own lane owns that fix.
     */
    @Test
    void theTrappedPairsMergedWindowForms() throws Exception {
        EngineServer server = boot();
        try {
            int x = 170, z = 170, y = 40;
            PlayerSession player = join(server, "Trapped",
                    new Position(x + 2.5, y + 1, z + 0.5));
            await(() -> player.state() == PlayerState.PLAYING, "session reaches PLAYING");
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        BuiltinBlocks.TRAPPED_CHEST);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        BuiltinBlocks.TRAPPED_CHEST);
            });
            await(() -> server.world().getBlock(new BlockPosition(x + 1, y, z))
                    .equals(BuiltinBlocks.TRAPPED_CHEST), "the trapped pair placed");

            // Open the EAST half: the merged window forms with the WEST
            // twin pinned FIRST (the join order over the trapped family).
            AtomicInteger windowId = new AtomicInteger(-1);
            server.useItemOnBlock(player, new BlockPosition(x + 1, y, z), 1,
                    java.util.Optional.empty(), windowId::set);
            await(() -> windowId.get() > 0, "the merged trapped window opened");
            assertEquals(new BlockPosition(x, y, z), player.openContainerPairFirst(),
                    "the pinned FIRST half is the west trapped chest");
            assertEquals(new BlockPosition(x + 1, y, z), player.openContainerPairSecond(),
                    "the pinned SECOND half is the clicked trapped chest");
            // The close clears the pin.
            server.closeWindow(player, windowId.get());
            await(() -> player.openContainerPairFirst() == null
                    && player.openContainerPairSecond() == null,
                    "the close cleared the pair pin");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dcp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        server.ticker().submit(() -> session.applyMovement(
                at, net.zaminmc.torch.util.Rotation.ZERO, true));
        return session;
    }

    private static int chestCount(EngineServer server, int x, int y, int z, int slot) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            ChestBlockEntity chest = server.chestManager().peek(new BlockPosition(x, y, z));
            result.set(chest == null ? 0 : chest.snapshotSlots()[slot].count());
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private static int hopperCount(EngineServer server, int x, int y, int z, int slot) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            var hopper = server.hopper().peek(new BlockPosition(x, y, z));
            result.set(hopper == null ? 0 : hopper.snapshotSlots()[slot].count());
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        assertNotNull(result.get(), "the type read answered");
        return result.get();
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Sleeps the full window, then asserts the condition NEVER became true. */
    private static void assertNeverWithin(long windowMillis, BooleanSupplier condition,
                                          String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMillis;
        while (System.currentTimeMillis() < deadline) {
            assertTrue(!condition.getAsBoolean(),
                    "FAIL: " + description + " (the condition became true within "
                            + windowMillis + "ms)");
            sleep(100);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
