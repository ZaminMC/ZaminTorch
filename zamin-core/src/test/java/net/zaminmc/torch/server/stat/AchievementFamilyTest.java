package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.brewing.BrewingStandBlocks;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.Biome;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The achievement family's award sites (VIII6 — the twenty-eight rows the
 * engine's stat store now earns through gameplay): the craft ladder
 * (CraftingResultSlot.checkAchievements over both take paths, the parent
 * gate refusing the unearned), the smelt pair (FurnaceResultSlot's iron
 * and fish), the brew row (the potion slots' brewed-form gate), the
 * overkill (the pre-armor f &gt;= 18), the kill rows (the Monster gate and
 * the wither's death box), the summon box, the beacon's full power, the
 * breed cause, the pig's rider fall, and the cart-ride kilometre.
 */
class AchievementFamilyTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------- the craft ladder

    @Test
    void theCraftLadderAwardsThroughTheParentGate() throws Exception {
        EngineServer server = boot();
        PlayerSession crafter = join(server, "Crafter");
        try {
            // Four planks in the 2x2 player grid = the crafting table; the
            // row's parent (GET_LOG) is NOT earned yet — PlayerStats
            // .increment's own gate refuses the award.
            server.ticker().submit(() -> {
                for (int i = 0; i < 4; i++) {
                    crafter.crafting().setCell(i,
                            ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
                }
            });
            await(() -> !crafter.crafting().cell(0).isEmpty(), "the grid loaded");
            server.windowClick(crafter, 0, 0, 0, 0, accepted -> { });
            await(() -> crafter.crafting().cell(0).isEmpty(), "the craft consumed");
            assertFalse(crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_CRAFTING_TABLE.key()),
                    "the unearned parent chain refuses the craft row");
            assertFalse(crafter.stats().hasAchievement(
                            EngineAchievements.GET_LOG.key()),
                    "no log was ever picked up");

            // The chain lands (the give walk hands the parents out first):
            // OPEN_INVENTORY -> GET_LOG — then the same craft awards.
            server.chatService().submitChat(crafter, "/achievement give achievement.mineWood Crafter");
            await(() -> crafter.stats().hasAchievement(EngineAchievements.GET_LOG.key()),
                    "the log row given");
            server.ticker().submit(() -> {
                for (int i = 0; i < 4; i++) {
                    crafter.crafting().setCell(i,
                            ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
                }
            });
            await(() -> !crafter.crafting().cell(0).isEmpty(), "the grid reloaded");
            server.windowClick(crafter, 0, 0, 0, 0, accepted -> { });
            await(() -> crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_CRAFTING_TABLE.key()),
                    "the earned chain carries the craft row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    @Test
    void thePickaxePairRidesTheTierAndTheQuirkAwardsTheApple() throws Exception {
        EngineServer server = boot();
        PlayerSession crafter = join(server, "Crafter");
        try {
            // The whole pickaxe chain (OPEN_INVENTORY -> ... -> CRAFT_PICKAXE
            // -> CRAFT_BETTER_PICKAXE's own parent): give the pickaxe row.
            server.chatService().submitChat(crafter, "/achievement give achievement.buildPickaxe Crafter");
            await(() -> crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_PICKAXE.key()),
                    "the pickaxe chain given");

            // The table window (the 3x3 grid lives behind the open
            // container, the reference's CraftingInventory over the
            // ContainerWorkbench).
            server.blockInteraction().submitPlace(crafter, new BlockPosition(2, 4, 2), 1,
                    BuiltinBlocks.CRAFTING_TABLE);
            await(() -> server.world().getBlock(new BlockPosition(2, 5, 2))
                    .equals(BuiltinBlocks.CRAFTING_TABLE), "the table placed");
            AtomicBoolean opened = new AtomicBoolean();
            server.useItemOnBlock(crafter, new BlockPosition(2, 5, 2), -1,
                    java.util.Optional.empty(), windowId -> opened.set(windowId > 0));
            await(opened::get, "the table window opened");

            // A WOODEN pickaxe in the 3x3 grid: the plain row only — the
            // better-than-wood gate refuses (PickaxeItem && tier != WOOD).
            craftOnce3x3(server, crafter, () -> {
                crafter.tableCrafting().setCell(0, ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
                crafter.tableCrafting().setCell(1, ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
                crafter.tableCrafting().setCell(2, ItemStack.of(BuiltinItems.OAK_PLANKS, 1));
                crafter.tableCrafting().setCell(4, ItemStack.of(BuiltinItems.STICK, 1));
                crafter.tableCrafting().setCell(7, ItemStack.of(BuiltinItems.STICK, 1));
            }, net.zaminmc.torch.server.item.BuiltinItems.WOODEN_PICKAXE);
            assertTrue(crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_PICKAXE.key()),
                    "any pickaxe earns the plain row");
            assertFalse(crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_BETTER_PICKAXE.key()),
                    "the wooden tier never earns the better row");

            // A STONE pickaxe: both rows (the parent is already earned).
            craftOnce3x3(server, crafter, () -> {
                crafter.tableCrafting().setCell(0, ItemStack.of(BuiltinItems.COBBLESTONE, 1));
                crafter.tableCrafting().setCell(1, ItemStack.of(BuiltinItems.COBBLESTONE, 1));
                crafter.tableCrafting().setCell(2, ItemStack.of(BuiltinItems.COBBLESTONE, 1));
                crafter.tableCrafting().setCell(4, ItemStack.of(BuiltinItems.STICK, 1));
                crafter.tableCrafting().setCell(7, ItemStack.of(BuiltinItems.STICK, 1));
            }, net.zaminmc.torch.server.item.BuiltinItems.STONE_PICKAXE);
            assertTrue(crafter.stats().hasAchievement(
                            EngineAchievements.CRAFT_BETTER_PICKAXE.key()),
                    "the stone tier earns the better row");

            // The famous quirk (CraftingResultSlot lines 93-95): crafting
            // the ENCHANTED golden apple (8 gold blocks + apple, meta 1)
            // awards the "overpowered" row named EAT — the reference's own
            // bug, kept 1:1. The parents: EAT's chain rides
            // CRAFT_BETTER_PICKAXE (earned above).
            craftOnce3x3(server, crafter, () -> {
                for (int i = 0; i < 9; i++) {
                    if (i != 4) {
                        crafter.tableCrafting().setCell(i,
                                ItemStack.of(BuiltinItems.GOLD_BLOCK, 1));
                    }
                }
                crafter.tableCrafting().setCell(4, ItemStack.of(BuiltinItems.APPLE, 1));
            }, net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_APPLE);
            AtomicBoolean enchantedCrafted = new AtomicBoolean();
            server.ticker().submit(() -> enchantedCrafted.set(
                    crafter.inventory().snapshot().stream().anyMatch(stack -> !stack.isEmpty()
                            && stack.type().equals(BuiltinItems.GOLDEN_APPLE)
                            && stack.damage() == 1)));
            await(enchantedCrafted::get, "the enchanted apple crafted");
            assertTrue(crafter.stats().hasAchievement(
                            EngineAchievements.EAT_ENCHANTED_GOLDEN_APPLE.key()),
                    "the craft-site quirk awards the eat-named row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    /**
     * Loads the 3x3 grid through the open table window, shift-crafts the
     * result into the inventory (the quick-move take — checkAchievements'
     * onQuickMoved call site), checks the item landed.
     */
    private void craftOnce3x3(EngineServer server, PlayerSession crafter,
                              Runnable loadGrid,
                              net.zaminmc.torch.item.ItemType expect)
            throws Exception {
        server.ticker().submit(loadGrid);
        await(() -> !crafter.tableCrafting().cell(0).isEmpty(), "the grid loaded");
        server.windowClick(crafter, crafter.openContainerWindowId(), 0, 0, 1,
                accepted -> { });
        AtomicBoolean landed = new AtomicBoolean();
        server.ticker().submit(() -> landed.set(
                crafter.inventory().snapshot().stream().anyMatch(stack -> !stack.isEmpty()
                        && stack.type().equals(expect))));
        await(landed::get, "the " + expect.identifier().value() + " crafted");
    }

    // ------------------------------------------------------------- the smelt pair

    @Test
    void theFurnaceTakeAwardsTheSmeltRows() throws Exception {
        EngineServer server = boot();
        PlayerSession smelter = join(server, "Smelter");
        try {
            // The smelt chain up to CRAFT_FURNACE (GET_IRON_INGOT's own
            // parent): given, not the ingot row itself.
            server.chatService().submitChat(smelter, "/achievement give achievement.buildFurnace Smelter");
            await(() -> smelter.stats().hasAchievement(
                            EngineAchievements.CRAFT_FURNACE.key()),
                    "the furnace chain given");

            server.blockInteraction().submitPlace(smelter, new BlockPosition(2, 4, 2), 1,
                    BuiltinBlocks.FURNACE);
            await(() -> server.world().getBlock(new BlockPosition(2, 5, 2))
                    .equals(BuiltinBlocks.FURNACE), "furnace placed");
            AtomicBoolean opened = new AtomicBoolean();
            server.useItemOnBlock(smelter, new BlockPosition(2, 5, 2), -1,
                    java.util.Optional.empty(), windowId -> opened.set(windowId > 0));
            await(opened::get, "furnace window opened");

            // The output loaded directly (slot 2 — the wire's output slot):
            // the iron ingot take awards the acquire row.
            var furnaceAt = new BlockPosition(2, 5, 2);
            server.ticker().submit(() -> server.furnaces()
                    .peek(furnaceAt).setSlot(2, ItemStack.of(BuiltinItems.IRON_INGOT, 3)));
            await(() -> {
                var furnace = server.furnaces().peek(furnaceAt);
                return furnace != null && furnace.output().count() == 3;
            }, "the output loaded");
            server.windowClick(smelter, smelter.openContainerWindowId(), 2, 0, 1,
                    accepted -> { });
            await(() -> smelter.stats().hasAchievement(
                            EngineAchievements.GET_IRON_INGOT.key()),
                    "the iron take awards the acquire row");

            // The cooked fish (BOTH fish metas — the item id alone, the
            // reference's own Item equality): COOK_FISH rides the same seam.
            server.ticker().submit(() -> server.furnaces()
                    .peek(furnaceAt).setSlot(2, ItemStack.of(BuiltinItems.COOKED_FISH, 2)));
            await(() -> {
                var furnace = server.furnaces().peek(furnaceAt);
                return furnace != null && furnace.output().count() == 2;
            }, "the fish loaded");
            server.windowClick(smelter, smelter.openContainerWindowId(), 2, 0, 1,
                    accepted -> { });
            await(() -> smelter.stats().hasAchievement(
                            EngineAchievements.COOK_FISH.key()),
                    "the fish take awards the cook row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the brew row

    @Test
    void theBrewingTakeAwardsOnlyTheBrewedForm() throws Exception {
        EngineServer server = boot();
        PlayerSession brewer = join(server, "Brewer");
        try {
            // BREW_POTION's chain: up to GET_BLAZE_ROD.
            server.chatService().submitChat(brewer, "/achievement give achievement.blazeRod Brewer");
            await(() -> brewer.stats().hasAchievement(
                            EngineAchievements.GET_BLAZE_ROD.key()),
                    "the blaze chain given");

            server.blockInteraction().submitPlace(brewer, new BlockPosition(2, 4, 2), 1,
                    BrewingStandBlocks.of(false, false, false));
            BlockPosition standAt = new BlockPosition(2, 5, 2);
            await(() -> server.world().getBlock(standAt)
                    .equals(BrewingStandBlocks.of(false, false, false)), "the stand placed");
            AtomicBoolean opened = new AtomicBoolean();
            server.useItemOnBlock(brewer, standAt, -1, java.util.Optional.empty(),
                    windowId -> opened.set(windowId > 0));
            await(opened::get, "the stand window opened");

            // The water bottle (damage 0) never awards — the reference's
            // getMetadata() > 0 gate.
            server.ticker().submit(() -> server.brewingStands()
                    .peek(standAt).setItem(0, ItemStack.of(BuiltinItems.POTION, 1)));
            await(() -> {
                var stand = server.brewingStands().peek(standAt);
                return stand != null && !stand.getItem(0).isEmpty();
            }, "the bottle loaded");
            server.windowClick(brewer, brewer.openContainerWindowId(), 0, 0, 1,
                    accepted -> { });
            await(() -> {
                var stand = server.brewingStands().peek(standAt);
                return stand == null || stand.getItem(0).isEmpty();
            }, "the water bottle taken");
            assertFalse(brewer.stats().hasAchievement(
                            EngineAchievements.BREW_POTION.key()),
                    "the water bottle never awards the brew row");

            // The brewed form (damage past the water bottle): awarded.
            server.ticker().submit(() -> server.brewingStands().peek(standAt)
                    .setItem(1, ItemStack.of(BuiltinItems.POTION, 1).withDamage(8204)));
            await(() -> {
                var stand = server.brewingStands().peek(standAt);
                return stand != null && !stand.getItem(1).isEmpty();
            }, "the potion loaded");
            server.windowClick(brewer, brewer.openContainerWindowId(), 1, 0, 1,
                    accepted -> { });
            await(() -> brewer.stats().hasAchievement(
                            EngineAchievements.BREW_POTION.key()),
                    "the brewed form awards the row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the combat rows

    @Test
    void theOverkillAndTheKillRowsRideTheCombatWalks() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");
        try {
            // The overkill's parent (CRAFT_ENCHANTING_TABLE) and the kill
            // row's own chain (CRAFT_SWORD).
            server.chatService().submitChat(hunter, "/achievement give achievement.enchantments Hunter");
            server.chatService().submitChat(hunter, "/achievement give achievement.buildSword Hunter");
            await(() -> hunter.stats().hasAchievement(
                            EngineAchievements.CRAFT_ENCHANTING_TABLE.key())
                            && hunter.stats().hasAchievement(
                            EngineAchievements.CRAFT_SWORD.key()),
                    "the chains given");

            grantHeld(hunter, ItemStack.of(BuiltinItems.DIAMOND_SWORD)
                    .withEnchantment(Enchantments.SMITE.id, 5));
            server.chatService().submitChat(hunter, "/time set night");
            server.chatService().submitChat(hunter, "/spawnmob zombie 1");
            MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
            standNextTo(server, hunter, zombie.position());

            // Smite V on the undead body: 7 + 12.5 = 19.5 >= 18 — the
            // pre-armor overkill row lands on the first hit; the zombie
            // survives at 0.5 health, so a second swing carries the kill
            // (past the hurt window).
            server.attackEntity(hunter, zombie.entityId());
            await(() -> hunter.stats().hasAchievement(
                            EngineAchievements.DEAL_OVERKILL_DAMAGE.key()),
                    "the 19.5-hit earns the overkill row");
            EngineAwaits.awaitTicks(server.world(), 14, "the hurt window's 10-tick cadence");
            // The first swing's knockback carried the body away: close the
            // distance again before the finishing swing.
            standNextTo(server, hunter, zombie.position());
            EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
            server.attackEntity(hunter, zombie.entityId());
            await(() -> zombie.dead(), "the zombie died");
            assertTrue(hunter.stats().hasAchievement(
                            EngineAchievements.KILL_ENEMY.key()),
                    "the hostile kill earns the kill row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    @Test
    void theWitherDeathBoxAwardsEveryWatcher() throws Exception {
        EngineServer server = boot();
        PlayerSession watcher = join(server, "Watcher");
        try {
            // KILL_WITHER's parent (SUMMON_WITHER, whose own chain rides
            // LEAVE_THE_END) — given, then the wither dies near the player.
            server.chatService().submitChat(watcher, "/achievement give achievement.spawnWither Watcher");
            await(() -> watcher.stats().hasAchievement(
                            EngineAchievements.SUMMON_WITHER.key()),
                    "the summon chain given");

            server.chatService().submitChat(watcher, "/summon Wither ~ ~ ~");
            MobEntity wither = awaitMob(server, MobType.WITHER);
            standNextTo(server, watcher, wither.position());
            // The wither's spawn window (Slice 59): the summon's own
            // invulnerability clears before the wound.
            await(() -> !wither.witherInvulnerable(), "the spawn window closed");
            server.ticker().submit(() -> server.mobs().hurt(wither, 1000.0f, 0.0));
            await(() -> wither.dead(), "the wither died");
            assertTrue(watcher.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key()),
                    "the death box carries every nearby player");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the summon box

    @Test
    void theWitherSummonAwardsTheBuildersAroundThePattern() throws Exception {
        EngineServer server = boot();
        PlayerSession builder = join(server, "Builder");
        try {
            // SUMMON_WITHER's chain: LEAVE_THE_END and its ancestors.
            server.chatService().submitChat(builder, "/achievement give achievement.theEnd2 Builder");
            await(() -> builder.stats().hasAchievement(
                            EngineAchievements.LEAVE_THE_END.key()),
                    "the chain given");

            // The soul-sand T with three wither skulls (SkullSummonTest's
            // own build): the live engine's summon host awards the builders
            // in the grown box.
            AtomicBoolean summoned = new AtomicBoolean();
            server.ticker().submit(() -> {
                int baseX = 40, baseY = 40, baseZ = 40;
                for (int i = -1; i <= 1; i++) {
                    server.world().setBlock(new BlockPosition(baseX + i, baseY + 1, baseZ),
                            BuiltinBlocks.SOUL_SAND);
                    server.world().setBlock(new BlockPosition(baseX + i, baseY + 2, baseZ),
                            BuiltinBlocks.SKULL);
                    server.skulls().place(new BlockPosition(baseX + i, baseY + 2, baseZ), 1, 0);
                }
                server.world().setBlock(new BlockPosition(baseX, baseY, baseZ),
                        BuiltinBlocks.SOUL_SAND);
                summoned.set(server.skulls().trySummonWither(
                        new BlockPosition(baseX, baseY + 2, baseZ), 0));
            });
            await(summoned::get, "the summon fired");
            assertTrue(builder.stats().hasAchievement(
                            EngineAchievements.SUMMON_WITHER.key()),
                    "the builder in the box earns the summon row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the beacon row

    @Test
    void theBeaconFullPowerAwardsThePyramidBystanders() throws Exception {
        EngineServer server = boot();
        PlayerSession architect = join(server, "Architect");
        try {
            // ACTIVATE_MAX_BEACON's parent (KILL_WITHER) — given.
            server.chatService().submitChat(architect, "/achievement give achievement.killWither Architect");
            await(() -> architect.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key()),
                    "the chain given");

            // The four-level iron pyramid (9 + 25 + 49 + 81) under the
            // beacon — built ABOVE the surface at the BeaconSystemTest's
            // own band (y 60) so the beam walk reads open sky. The player
            // stands in the box FIRST: the beacon block lands last, and its
            // own registration update fires the transition census.
            standNextTo(server, architect, new Position(61, 60, 61));
            await(() -> Math.abs(architect.position().x() - 60.0) <= 10.0
                            && Math.abs(architect.position().z() - 60.0) <= 10.0,
                    "the architect in place (inside the grown box)");
            AtomicBoolean built = new AtomicBoolean();
            server.ticker().submit(() -> {
                int x = 60, y = 60, z = 60;
                for (int level = 1; level <= 4; level++) {
                    int ringY = y - level;
                    for (int dx = -level; dx <= level; dx++) {
                        for (int dz = -level; dz <= level; dz++) {
                            server.world().setBlock(new BlockPosition(x + dx, ringY, z + dz),
                                    BuiltinBlocks.IRON_BLOCK);
                        }
                    }
                }
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
                built.set(true);
            });
            await(built::get, "the pyramid built");
            await(() -> architect.stats().hasAchievement(
                            EngineAchievements.ACTIVATE_MAX_BEACON.key()),
                    "the full pyramid's first update earns the row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the breed row

    @Test
    void theBreedRowsAwardTheCausePlayer() throws Exception {
        EngineServer server = boot();
        PlayerSession farmer = join(server, "Farmer");
        try {
            // BREED_COW's parent (KILL_COW) — given.
            server.chatService().submitChat(farmer, "/achievement give achievement.killCow Farmer");
            await(() -> farmer.stats().hasAchievement(
                            EngineAchievements.KILL_COW.key()),
                    "the chain given");

            server.chatService().submitChat(farmer, "/spawnmob cow 2");
            MobEntity first = awaitMob(server, MobType.COW);
            MobEntity second = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.COW && mob != first)
                    .findFirst().orElseThrow();
            // The pair in love (the feeding's own effect): the breed goal
            // walks them together and the cause player earns the rows.
            Position between = new Position(
                    (first.position().x() + second.position().x()) / 2.0,
                    Math.max(first.position().y(), second.position().y()),
                    (first.position().z() + second.position().z()) / 2.0);
            server.ticker().submit(() -> {
                first.teleport(between);
                second.teleport(between);
                first.enterLove(farmer.engineEntityId());
                second.enterLove(farmer.engineEntityId());
            });
            await(() -> farmer.stats().get(
                            net.zaminmc.torch.server.stat.EngineStats.ANIMALS_BRED) > 0,
                    "the breed landed (the animals-bred counter)");
            assertTrue(farmer.stats().hasAchievement(
                            EngineAchievements.BREED_COW.key()),
                    "the cow pair earns the breed row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the pig fall

    @Test
    void thePigFallAwardsTheRiderPastFiveBlocks() throws Exception {
        EngineServer server = boot();
        PlayerSession rider = join(server, "Rider");
        try {
            // RIDE_PIG_OFF_CLIFF's parent (KILL_COW) — given.
            server.chatService().submitChat(rider, "/achievement give achievement.killCow Rider");
            await(() -> rider.stats().hasAchievement(
                            EngineAchievements.KILL_COW.key()),
                    "the chain given");

            server.chatService().submitChat(rider, "/spawnmob pig 1");
            MobEntity pig = awaitMob(server, MobType.PIG);
            // The seat + the fall (PigEntity.takeFallDamage lines 162-168:
            // distance > 5.0F with a rider aboard).
            server.ticker().submit(() -> {
                pig.setRider(rider.engineEntityId());
                pig.takeFallDamage(6.0f, 1.0f);
            });
            await(() -> rider.stats().hasAchievement(
                            EngineAchievements.RIDE_PIG_OFF_CLIFF.key()),
                    "the six-block fall with a rider earns the row");
            // A five-block fall exactly: the reference's strict > refuses.
            server.chatService().submitChat(rider, "/achievement take achievement.flyPig Rider");
            await(() -> !rider.stats().hasAchievement(
                            EngineAchievements.RIDE_PIG_OFF_CLIFF.key()),
                    "the row taken back");
            server.ticker().submit(() -> pig.takeFallDamage(5.0f, 1.0f));
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
            assertFalse(rider.stats().hasAchievement(
                            EngineAchievements.RIDE_PIG_OFF_CLIFF.key()),
                    "the five-block fall exactly never earns the row");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the cart kilometre

    @Test
    void theCartRideAwardsTheThousandBlockDisplacement() throws Exception {
        EngineServer server = boot();
        PlayerSession passenger = join(server, "Passenger");
        try {
            // TRAVEL_KILOMETER_BY_MINECART's parent (GET_IRON_INGOT).
            server.chatService().submitChat(passenger, "/achievement give achievement.acquireIron Passenger");
            await(() -> passenger.stats().hasAchievement(
                            EngineAchievements.GET_IRON_INGOT.key()),
                    "the chain given");

            var cart = server.vehicles().spawnMinecart(
                    new Position(8.5, 40.0, 8.5), 0);
            await(() -> server.vehicles().all().stream().anyMatch(
                    vehicle -> vehicle == cart), "the cart spawned");
            server.ticker().submit(() ->
                    server.vehicles().mount(cart, passenger.engineEntityId()));
            await(() -> passenger.ridingVehicleId() >= 0, "the seat taken");
            // The ride begins (the first move arms the start cell), then the
            // thousand-block displacement (squared 1,000,000): the cart
            // carried whole.
            server.ticker().submit(() ->
                    cart.setPosition(new Position(9.5, 40.0, 8.5)));
            await(() -> passenger.minecartTravelStart() != null, "the start cell armed");
            server.ticker().submit(() ->
                    cart.setPosition(new Position(1010.5, 40.0, 8.5)));
            await(() -> passenger.stats().hasAchievement(
                            EngineAchievements.TRAVEL_KILOMETER_BY_MINECART.key()),
                    "the thousand-block ride earns the row");
            assertTrue(passenger.stats().get(
                            net.zaminmc.torch.server.stat.EngineStats.CM_MINECART) > 0,
                    "the ride counted its centimetres");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- the all-biomes row

    @Test
    void theAllBiomesRowRidesTheProgressCover() throws Exception {
        EngineServer server = boot();
        PlayerSession explorer = join(server, "Explorer");
        try {
            // ENTER_ALL_BIOMES's parent (ENTER_THE_END) — given.
            server.chatService().submitChat(explorer, "/achievement give achievement.theEnd Explorer");
            await(() -> explorer.stats().hasAchievement(
                            EngineAchievements.ENTER_THE_END.key()),
                    "the chain given");

            // The progress set one biome short of the cover: the census
            // never awards while a biome is missing.
            server.ticker().submit(() -> {
                java.util.Set<String> progress = explorer.stats()
                        .progressOf(EngineAchievements.ENTER_ALL_BIOMES.key());
                int i = 0;
                for (Biome biome : Biome.explorable()) {
                    if (i++ < Biome.explorable().length - 1) {
                        progress.add(biome.displayName());
                    }
                }
            });
            await(() -> explorer.stats().getProgress(
                            EngineAchievements.ENTER_ALL_BIOMES.key()) != null
                            && explorer.stats().getProgress(
                            EngineAchievements.ENTER_ALL_BIOMES.key()).size()
                            == Biome.explorable().length - 1,
                    "the progress one short");
            // The census runs every 20 ticks over the player's column: give
            // it a couple of windows to observe the short set.
            EngineAwaits.awaitTicks(server.world(), 30, "engine progress (a 1_500 wall settle)");
            assertFalse(explorer.stats().hasAchievement(
                            EngineAchievements.ENTER_ALL_BIOMES.key()),
                    "the missing biome refuses the award");

            // The cover completes (the missing biome joins): the next
            // census window awards the row.
            server.ticker().submit(() -> explorer.stats()
                    .progressOf(EngineAchievements.ENTER_ALL_BIOMES.key())
                    .addAll(java.util.Arrays.stream(Biome.explorable())
                            .map(Biome::displayName)
                            .collect(java.util.stream.Collectors.toSet())));
            await(() -> explorer.stats().hasAchievement(
                            EngineAchievements.ENTER_ALL_BIOMES.key()),
                    "the covered set earns the row on the next census");
        } finally {
            server.shutdown(null);
            await(() -> server.state() == ServerState.STOPPED, "server stopped");
        }
    }

    // ------------------------------------------------------------- harness

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "achv" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        // The achievement command's own permission band (level 2, the
        // reference's getRequiredPermissionLevel).
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.opLevel() >= 2, name + " opped");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void grantHeld(PlayerSession player, ItemStack stack) {
        assertTrue(player.inventory().pickUp(stack).isEmpty(),
                "the test grants the stack into the held hotbar slot");
    }

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(mob -> mob.type() == type),
                type + " present");
        return server.mobs().all().stream()
                .filter(mob -> mob.type() == type).findFirst().orElseThrow();
    }

    private void standNextTo(EngineServer server, PlayerSession player, Position at) {
        server.ticker().submit(() -> player.applyMovement(
                new Position(at.x() + 1.0, at.y(), at.z()),
                player.rotation(), true));
    }

    private void await(BooleanSupplier condition, String description)
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
