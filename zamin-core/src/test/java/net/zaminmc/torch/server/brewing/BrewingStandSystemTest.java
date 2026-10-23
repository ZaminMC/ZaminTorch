package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.potion.PotionHelper;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The brewing stand on the live engine (Slice 11e — the reference port of
 * BrewingStandBlock + BrewingStandBlockEntity + BrewingStandMenu): the
 * 400-tick brew cycle (the arming, the countdown, the completion), the
 * cancellation gates (the ingredient leaves, a different item appears), the
 * reference's careful write gates (the same-effect brew only rewrites on
 * the splash conversion), the bottle-bits block sync, the stand's spill
 * on break, the comparator's fullness, the hopper's sided slots (the UP
 * face feeds the ingredient alone), and the window's content-based
 * shift-click arms.
 */
class BrewingStandSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The brew cycle (tick lines 58-92)
    // ------------------------------------------------------------------

    @Test
    void waterBottlesBecomeAwkwardAfterTheFourHundredTicks() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition stand = new BlockPosition(20, 40, 20);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(stand);
                // Three water bottles (the plain damage 0) and the nether
                // wart ("+4" — the awkward's bit).
                for (int slot = 0; slot < 3; slot++) {
                    entity.setItem(slot, new ItemStack(
                            net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 0));
                }
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.NETHER_WART, 2));
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            await(() -> entity.timer() > 300, "the brew armed the 400-tick window");
            // The fresh cycle starts at (or a tick or two under) the full
            // 400 — the await's poll may land after the first decrement.
            assertTrue(entity.timer() <= BrewingStandBlockEntity.BREW_TICKS
                            && entity.timer() > 300,
                    "the fresh cycle starts at the full window: " + entity.timer());
            await(() -> entity.timer() < BrewingStandBlockEntity.BREW_TICKS - 10,
                    "the timer counts down");
            // The completion: every bottle turned awkward (metadata 16).
            await(() -> entity.getItem(0).damage() == 16
                            && entity.getItem(1).damage() == 16
                            && entity.getItem(2).damage() == 16,
                    "the water bottles brewed into the awkward potions");
            // The wart paid one unit.
            assertEquals(1, entity.getItem(BrewingStandBlockEntity.SLOT_INGREDIENT).count(),
                    "the ingredient paid one unit");
            // The bottle bits: the block state carries the three occupied
            // slots (the reference's findHasPotion sync).
            var block = server.world().getBlock(stand);
            assertTrue(BrewingStandBlocks.hasBottle(block, 0)
                    && BrewingStandBlocks.hasBottle(block, 1)
                    && BrewingStandBlocks.hasBottle(block, 2),
                    "the block state carries the three bottle bits");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCycleCancelsWhenTheIngredientLeavesOrChanges() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition stand = new BlockPosition(60, 40, 60);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(stand);
                entity.setItem(0, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 0));
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.SUGAR, 3));
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            await(() -> entity.timer() > 0, "the sugar armed the cycle");
            // The ingredient leaves mid-brew: the timer resets at once.
            server.ticker().submit(() -> entity.setItem(
                    BrewingStandBlockEntity.SLOT_INGREDIENT, ItemStack.EMPTY));
            await(() -> entity.timer() == 0, "the missing ingredient cancelled the cycle");
            // The re-arm with a fresh ingredient.
            server.ticker().submit(() -> entity.setItem(
                    BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                            net.zaminmc.torch.server.item.BuiltinItems.SUGAR, 3)));
            await(() -> entity.timer() > 0, "the sugar re-armed the cycle");
            await(() -> entity.timer() > 0 && entity.timer() < BrewingStandBlockEntity.BREW_TICKS,
                    "the timer runs down");
            // A DIFFERENT item under the running timer: the cancellation
            // (the reference's ingredient-identity gate).
            server.ticker().submit(() -> entity.setItem(
                    BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                            net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 3)));
            await(() -> entity.timer() == 0,
                    "the changed item cancelled the running cycle");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSplashConversionAlwaysBrewsAndTheNoOpNeverArms() throws Exception {
        EngineServer server = boot();
        try {
            // The no-op: redstone on an already-extended potion never arms
            // (the same-effect gate — canBrew's change rule).
            BlockPosition idle = new BlockPosition(100, 40, 100);
            server.ticker().submit(() -> {
                server.world().setBlock(idle, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(idle);
                // The extended speed potion (speed + bit 6, no
                // amplifier) with redstone (clears 5, sets 6, clears 7) —
                // nothing changes.
                entity.setItem(0, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.POTION, 1,
                        PotionHelper.applyIngredient(8194, "-5+6-7")));
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 3));
            });
            await(() -> server.brewingStands().peek(idle) != null,
                    "the idle stand's state materialized");
            var idleEntity = server.brewingStands().peek(idle);
            EngineAwaits.awaitTicks(server.world(), 8, "engine progress (a 400 wall settle)");
            assertEquals(0, idleEntity.timer(),
                    "the no-op brew never arms (the reference's change gate)");

            // The splash conversion: gunpowder on the mundane (a drinkable
            // with no effects — the write gate's same-effect arm still
            // rewrites the splash bit).
            BlockPosition splash = new BlockPosition(140, 40, 140);
            server.ticker().submit(() -> {
                server.world().setBlock(splash, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(splash);
                entity.setItem(0, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 8192));
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.GUNPOWDER, 1));
            });
            await(() -> server.brewingStands().peek(splash) != null,
                    "the splash stand's state materialized");
            var splashEntity = server.brewingStands().peek(splash);
            await(() -> splashEntity.timer() > 0, "the gunpowder armed the splash brew");
            // The gunpowder's "+14&13-13" clears the carrier bit: the
            // mundane (8192) turns the plain splash (16384).
            await(() -> splashEntity.getItem(0).damage() == 16384,
                    "the mundane turned splash (the always-brews gate)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The container surface
    // ------------------------------------------------------------------

    @Test
    void theStandSpillsItsSlotsOnBreakAndReadsTheFullness() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition stand = new BlockPosition(180, 40, 180);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(stand);
                entity.setItem(0, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 0));
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.SUGAR, 5));
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            // The fullness: 2 of 4 slots occupied (the reference's
            // InventoryMenu.getAnalogSignal walk over the stand's slots).
            int fullness = RedstoneSystem.containerFullness(entity.snapshotSlots(), 64);
            assertTrue(fullness > 0 && fullness <= 15,
                    "the stand reads as an analog source: " + fullness);
            // The survival break spills the two stacks (the engine's
            // break hook drives onBlockBroken with its item manager).
            server.ticker().submit(() -> {
                server.world().setBlock(stand, server.world().airType());
                server.brewingStands().onBlockBroken(stand, server.itemEntities());
            });
            await(() -> server.brewingStands().peek(stand) == null,
                    "the broken stand discarded its state");
            assertTrue(itemDropNear(server, 180, 40, 180, "potion")
                            || itemDropNear(server, 180, 40, 180, "sugar"),
                    "the break spilled the slots");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperFeedsTheIngredientFromAbove() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition stand = new BlockPosition(220, 40, 220);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                server.brewingStands().getOrCreate(stand);
                // A hopper above the stand, holding sugar.
                server.world().setBlock(new BlockPosition(220, 41, 220),
                        net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(220, 41, 220))
                        .setItem(0, new ItemStack(
                                net.zaminmc.torch.server.item.BuiltinItems.SUGAR, 7));
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            await(() -> entity.getItem(BrewingStandBlockEntity.SLOT_INGREDIENT).count() > 0,
                    "the hopper above fed the ingredient slot");
            assertEquals(0, entity.getItem(0).count(),
                    "the ingredient never rides a bottle slot");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theShiftClickArmsFollowTheContentRouting() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition stand = new BlockPosition(260, 40, 260);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                server.brewingStands().getOrCreate(stand);
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            // A bottle stack of three: one per empty potion slot, the rest
            // stays (the PotionSlot's maxStack-1 walk).
            ItemStack bottles = new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.GLASS_BOTTLE, 3);
            ItemStack remainder = entity.quickMoveIn(bottles);
            assertEquals(1, entity.getItem(0).count());
            assertEquals(1, entity.getItem(1).count());
            assertEquals(1, entity.getItem(2).count());
            assertEquals(0, remainder.count(), "one bottle stayed behind");
            // The ingredient arm: sugar into the empty slot 3 — the whole
            // stack (the ingredient slot's 64).
            ItemStack sugar = new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.SUGAR, 12);
            remainder = entity.quickMoveIn(sugar);
            assertEquals(0, remainder.count());
            assertEquals(12, entity.getItem(BrewingStandBlockEntity.SLOT_INGREDIENT).count());
            // A non-brewable never leaves (the reference's unmoved null).
            ItemStack cane = new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.SUGAR_CANE, 5);
            remainder = entity.quickMoveIn(cane);
            assertEquals(5, remainder.count(), "the non-brewable stays in the inventory");
            // The occupied ingredient slot refuses more through the arm:
            // redstone is not a bottle — nothing moves.
            ItemStack more = new ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 5);
            remainder = entity.quickMoveIn(more);
            assertEquals(5, remainder.count());
            // The bottle bits followed the occupancy (the block state
            // rides the next tick's sync).
            await(() -> BrewingStandBlocks.hasBottle(server.world().getBlock(stand), 0)
                    && BrewingStandBlocks.hasBottle(server.world().getBlock(stand), 1)
                    && BrewingStandBlocks.hasBottle(server.world().getBlock(stand), 2),
                    "the three bottles raised their bits");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The helpers
    // ------------------------------------------------------------------

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "brt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    @Test
    void thePufferfishBrewsTheWaterBreathing() throws Exception {
        EngineServer server = boot();
        try {
            // The pufferfish's flag recipe (FishItem.asPotionIngredient
            // lines 33-35 → PotionHelper.PUFFERFISH = "+0-1+2+3+13&4-4"):
            // the awkward potion (16) turns into the water breathing —
            // metadata 8205, the 3:00 base (bits 0,2,3,13).
            BlockPosition stand = new BlockPosition(140, 40, 140);
            server.ticker().submit(() -> {
                server.world().setBlock(stand, BrewingStandBlocks.of(
                        false, false, false));
                var entity = server.brewingStands().getOrCreate(stand);
                entity.setItem(0, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.POTION, 1, 16));
                // The pufferfish: the fish item's metadata-3 arm.
                entity.setItem(BrewingStandBlockEntity.SLOT_INGREDIENT, new ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.FISH, 1, 3));
            });
            await(() -> server.brewingStands().peek(stand) != null,
                    "the stand's state materialized");
            var entity = server.brewingStands().peek(stand);
            await(() -> entity.timer() > 0, "the pufferfish armed the cycle");
            await(() -> entity.getItem(0).damage() == 8205,
                    "the awkward potion brewed into the water breathing");
            // The pufferfish paid its single unit (the slot emptied).
            assertTrue(entity.getItem(BrewingStandBlockEntity.SLOT_INGREDIENT).isEmpty(),
                    "the pufferfish paid its unit");
            // The brewed effect set: water breathing at the 3:00 base.
            var effects = PotionHelper.getStatusEffects(8205);
            assertEquals(1, effects.size(), "one effect");
            assertEquals(net.zaminmc.torch.server.potion.StatusEffects.WATER_BREATHING,
                    effects.get(0).id());
            assertEquals(3600, effects.get(0).duration(), "3:00 = 3600 ticks");
        } finally {
            server.shutdown(() -> { });
        }
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

    private static boolean itemDropNear(EngineServer server, int x, int y, int z, String itemId) {
        for (var drop : server.itemEntities().all()) {
            var at = drop.position();
            if (Math.abs(at.x() - (x + 0.5)) < 2.0 && Math.abs(at.y() - y) < 2.0
                    && Math.abs(at.z() - (z + 0.5)) < 2.0
                    && drop.stack().type().identifier().value().equals(itemId)) {
                return true;
            }
        }
        return false;
    }
}
