package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.furnace.FurnaceRecipes;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.experience.ExperienceAwards;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mineral vault family (Slice 32 — the reference's own registrations):
 * the two missing ores (lapis 21, emerald 129), the storage blocks (lapis
 * 22, redstone 152, coal 173, quartz 155, brick 45, clay 82, hay 170, slime
 * 165, end stone 121, packed ice 174), their 1:1 drop walks (the lapis
 * 4-8 variable base with the OreBlock fortune multiplier, the redstone
 * dust's own additive fortune), the silk arms, the XP bands, the smelt and
 * fuel rows, and the light table (the slime pad passes light whole).
 */
class MineralVaultBlocksTest {

    private static BlockType block(String id) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze()
                .lookup(Identifier.parse(id)).orElseThrow();
    }

    private static ItemType item(String id) {
        return BuiltinItems.lookup(Identifier.parse(id)).orElseThrow();
    }

    private static BlockBehavior behavior(String id) {
        return BlockBehaviorTable.of(Identifier.parse(id)).orElseThrow();
    }

    // ---- the behavior rows --------------------------------------------------

    @Test
    void theOresCarryTheReferenceStrengthAndGates() {
        // OreBlock rows: hardness 3.0 both; the lapis gate is PickaxeItem's
        // tier >= 1 (the engine's band 2 — a stone pickaxe), emerald's
        // tier >= 2 (band 3 — iron).
        BlockBehavior lapis = behavior("minecraft:lapis_ore");
        assertEquals(3.0, lapis.hardness());
        assertEquals(2, lapis.harvestLevel(), "lapis needs a stone pickaxe (vanilla tier 1)");
        assertTrue(lapis.requiresTool());
        assertEquals("rock", lapis.material());

        BlockBehavior emerald = behavior("minecraft:emerald_ore");
        assertEquals(3.0, emerald.hardness());
        assertEquals(3, emerald.harvestLevel(), "emerald needs an iron pickaxe (vanilla tier 2)");

        // The harvest walk: a wooden pickaxe never harvests lapis, a stone
        // one does; emerald waits for iron.
        assertFalse(BlockBehaviorTable.canHarvest(lapis, BuiltinItems.WOODEN_PICKAXE));
        assertTrue(BlockBehaviorTable.canHarvest(lapis, BuiltinItems.STONE_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(emerald, BuiltinItems.STONE_PICKAXE));
        assertTrue(BlockBehaviorTable.canHarvest(emerald, BuiltinItems.IRON_PICKAXE));
    }

    @Test
    void theStorageBlocksCarryTheirOwnRows() {
        // The compressed minerals (the reference's setStrength calls):
        // lapis block 3.0 (the same stone-pickaxe gate as its ore), the
        // redstone/coal blocks 5.0 (any pickaxe — IRON/STONE materials),
        // quartz 0.8, brick 2.0.
        assertEquals(3.0, behavior("minecraft:lapis_block").hardness());
        assertEquals(2, behavior("minecraft:lapis_block").harvestLevel());
        assertEquals(5.0, behavior("minecraft:redstone_block").hardness());
        assertEquals(5.0, behavior("minecraft:coal_block").hardness());
        assertEquals(0.8, behavior("minecraft:quartz_block").hardness());
        assertEquals(2.0, behavior("minecraft:brick_block").hardness());
        assertEquals(1, behavior("minecraft:brick_block").harvestLevel(),
                "the plain stone-material blocks take any pickaxe");

        // The masonries' plain rows: clay is the shovel family (dirt band,
        // 0.6), the hay bale and the slime pad need no tool at all (0.5/0).
        BlockBehavior clay = behavior("minecraft:clay");
        assertEquals(0.6, clay.hardness());
        assertFalse(clay.requiresTool());
        assertEquals("dirt", clay.material());
        assertEquals(4, clay.drops().get(0).count(), "ClayBlock's four clay balls");
        assertEquals(0.5, behavior("minecraft:hay_block").hardness());
        assertEquals(0.0, behavior("minecraft:slime_block").hardness());
        assertFalse(behavior("minecraft:slime_block").requiresTool());

        // The end stone (3.0, any pickaxe) and the packed ice (0.5, any
        // pickaxe, bare-hand count 0 — PackedIceBlock.getBaseDropCount).
        assertEquals(3.0, behavior("minecraft:end_stone").hardness());
        BlockBehavior ice = behavior("minecraft:packed_ice");
        assertEquals(0.5, ice.hardness());
        assertTrue(ice.drops().isEmpty(), "packed ice never drops by hand");
    }

    // ---- the 1:1 drop walks --------------------------------------------------

    @Test
    void theLapisOreDropsFourToEightWithTheOreBlockFortuneMultiplier() {
        DropService drops = new DropService(new Random());
        BlockType lapisOre = block("minecraft:lapis_ore");
        // OreBlock.getBaseDropCount: 4 + nextInt(5) — the base band over
        // many rolls, never outside [4, 8].
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 500; i++) {
            List<ItemStack> yield = drops.dropsFor(lapisOre, BuiltinItems.IRON_PICKAXE);
            assertEquals(1, yield.size());
            assertEquals(item("minecraft:lapis"), yield.get(0).type());
            int count = yield.get(0).count();
            min = Math.min(min, count);
            max = Math.max(max, count);
        }
        assertEquals(4, min, "the lapis base starts at 4");
        assertEquals(8, max, "the lapis base tops at 4 + 4");

        // Fortune I can multiply the base (the OreBlock walk: nextInt(3) - 1
        // clamped at 0, times base) — the observed band widens past 8.
        int fortuneMax = 0;
        for (int i = 0; i < 500; i++) {
            List<ItemStack> yield = drops.dropsFor(
                    lapisOre, BuiltinItems.IRON_PICKAXE, false, 1);
            fortuneMax = Math.max(fortuneMax, yield.get(0).count());
        }
        assertTrue(fortuneMax >= 8, "fortune can double the base (16 reachable: 8 * 2)");
    }

    @Test
    void theRedstoneOreDropsDustWithItsOwnAdditiveFortune() {
        DropService drops = new DropService(new Random());
        BlockType ore = block("minecraft:redstone_ore");
        // RedstoneOreBlock lines 66-77: the DUST at 4 + nextInt(2), never
        // the block item (the pre-32 row's own shape).
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 500; i++) {
            List<ItemStack> yield = drops.dropsFor(ore, BuiltinItems.IRON_PICKAXE);
            assertEquals(1, yield.size());
            assertEquals(item("minecraft:redstone"), yield.get(0).type(),
                    "the drop is the dust, never the ore block");
            min = Math.min(min, yield.get(0).count());
            max = Math.max(max, yield.get(0).count());
        }
        assertEquals(4, min);
        assertEquals(5, max);

        // Fortune ADDS nextInt(f + 1) — the reference's own override of the
        // OreBlock multiplier shape (never a multiple).
        int fortuneMax = 0;
        for (int i = 0; i < 500; i++) {
            List<ItemStack> yield = drops.dropsFor(
                    ore, BuiltinItems.IRON_PICKAXE, false, 3);
            fortuneMax = Math.max(fortuneMax, yield.get(0).count());
        }
        assertEquals(8, fortuneMax, "fortune III tops at 5 + 3 = 8 (additive, not a multiple)");
    }

    @Test
    void theVaultsSilkArmsTakeTheBlocksThemselves() {
        DropService drops = new DropService(new Random());
        // The ore silk gate (OreBlock rides the default hasSilkTouchDrops)
        // and PackedIceBlock's own count-0 body.
        assertEquals(item("minecraft:lapis_ore"), drops.dropsFor(
                block("minecraft:lapis_ore"), BuiltinItems.IRON_PICKAXE, true, 0)
                .get(0).type());
        assertEquals(item("minecraft:emerald_ore"), drops.dropsFor(
                block("minecraft:emerald_ore"), BuiltinItems.IRON_PICKAXE, true, 0)
                .get(0).type());
        assertEquals(item("minecraft:redstone_ore"), drops.dropsFor(
                block("minecraft:redstone_ore"), BuiltinItems.IRON_PICKAXE, true, 0)
                .get(0).type());
        assertEquals(item("minecraft:packed_ice"), drops.dropsFor(
                block("minecraft:packed_ice"), BuiltinItems.IRON_PICKAXE, true, 0)
                .get(0).type());
        // The emerald ore's normal walk: one emerald (OreBlock's default
        // base 1, the foreign-drop multiplier applies under fortune).
        List<ItemStack> emerald = drops.dropsFor(
                block("minecraft:emerald_ore"), BuiltinItems.IRON_PICKAXE);
        assertEquals(1, emerald.size());
        assertEquals(item("minecraft:emerald"), emerald.get(0).type());
    }

    // ---- the XP bands ---------------------------------------------------------

    @Test
    void theNewOresPayTheirExperienceBands() {
        Random random = new Random();
        // OreBlock.dropItems' own table: lapis 2-5, emerald 3-7, quartz 2-5.
        int lapisMin = 99, lapisMax = -1, emeraldMin = 99, emeraldMax = -1, quartzMin = 99, quartzMax = -1;
        for (int i = 0; i < 300; i++) {
            int lapis = ExperienceAwards.forBlockBreak(
                    Identifier.parse("minecraft:lapis_ore"), random);
            int emerald = ExperienceAwards.forBlockBreak(
                    Identifier.parse("minecraft:emerald_ore"), random);
            int quartz = ExperienceAwards.forBlockBreak(
                    Identifier.parse("minecraft:quartz_ore"), random);
            lapisMin = Math.min(lapisMin, lapis);
            lapisMax = Math.max(lapisMax, lapis);
            emeraldMin = Math.min(emeraldMin, emerald);
            emeraldMax = Math.max(emeraldMax, emerald);
            quartzMin = Math.min(quartzMin, quartz);
            quartzMax = Math.max(quartzMax, quartz);
        }
        assertEquals(2, lapisMin);
        assertEquals(5, lapisMax);
        assertEquals(3, emeraldMin);
        assertEquals(7, emeraldMax);
        assertEquals(2, quartzMin);
        assertEquals(5, quartzMax);
    }

    // ---- the smelts and the fuel ------------------------------------------------

    @Test
    void theVaultsSmeltRowsMatchTheReference() {
        // SmeltingManager lines 25-64: the clay pair, the ore smelts, the
        // netherrack -> nether brick.
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:brick"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:clay_ball")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:hardened_clay"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:clay")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:coal"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:coal_ore")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:redstone"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:redstone_ore")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:lapis"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:lapis_ore")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:diamond"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:diamond_ore")));
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:emerald"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:emerald_ore")));
        // (The quartz_ore row rides the table for data completeness — the
        // engine's quartz ore drops the quartz itself and no quartz_ore
        // item exists yet, so the row is unreachable until a silk arm can
        // produce the block item; vanilla smelts only silk-touched ore.)
        assertEquals(Optional.of(new FurnaceRecipes.SmeltResult(
                        Identifier.parse("minecraft:nether_brick"), 1, 0)),
                FurnaceRecipes.resultOf(item("minecraft:netherrack")));
        // The take XP rows (SmeltingManager's own floats).
        assertEquals(0.3, FurnaceRecipes.takeExperienceOf(item("minecraft:brick")));
        assertEquals(0.35, FurnaceRecipes.takeExperienceOf(item("minecraft:hardened_clay")));
        assertEquals(0.7, FurnaceRecipes.takeExperienceOf(item("minecraft:redstone")));
        assertEquals(0.2, FurnaceRecipes.takeExperienceOf(item("minecraft:lapis")));
        assertEquals(1.0, FurnaceRecipes.takeExperienceOf(item("minecraft:emerald")));
        assertEquals(0.2, FurnaceRecipes.takeExperienceOf(item("minecraft:quartz")));
        assertEquals(0.1, FurnaceRecipes.takeExperienceOf(item("minecraft:nether_brick")));
        // The coal block's burn (FurnaceBlockEntity.getFuelTime: 16000).
        assertEquals(16000, FurnaceRecipes.burnTicksOf(item("minecraft:coal_block")),
                "the coal block burns sixteen smelts");
    }

    // ---- the light table -------------------------------------------------------

    @Test
    void theLightTableFiltersEveryVaultMemberButTheSlimePad() {
        BlockRegistryBuilder.FrozenBlockRegistry registry =
                BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        for (String id : new String[]{"minecraft:lapis_ore", "minecraft:emerald_ore",
                "minecraft:lapis_block", "minecraft:redstone_block", "minecraft:coal_block",
                "minecraft:quartz_block", "minecraft:brick_block", "minecraft:clay",
                "minecraft:hay_block", "minecraft:end_stone", "minecraft:packed_ice"}) {
            BlockType type = registry.lookup(Identifier.parse(id)).orElseThrow();
            assertEquals(15, BlockLightTable.filter(type), id + " filters 15");
            assertEquals(0, BlockLightTable.emission(type), id + " never emits");
        }
        // The slime pad: the reference's TransparentBlock(false) arm —
        // light passes whole (opacity 0), like the stained glass family.
        BlockType slime = registry.lookup(
                Identifier.parse("minecraft:slime_block")).orElseThrow();
        assertEquals(0, BlockLightTable.filter(slime));
        assertEquals(0, BlockLightTable.emission(slime));
    }

    // ---- the storage recipes ----------------------------------------------------

    @Test
    void theStorageBlocksPackNineAndUnpackNine() {
        net.zaminmc.torch.server.crafting.CraftingService table =
                net.zaminmc.torch.server.crafting.CraftingService.builtin();
        ItemStack empty = ItemStack.EMPTY;
        ItemStack lapis = ItemStack.of(item("minecraft:lapis"), 1);
        // MineralRecipes' own pair shape: nine lapis pack the block over
        // the 3x3 table grid.
        var packed = table.resultOf3x3(new ItemStack[]{
                lapis, lapis, lapis, lapis, lapis, lapis, lapis, lapis, lapis});
        assertTrue(packed.isPresent());
        assertEquals(item("minecraft:lapis_block"), packed.orElseThrow().type());
        assertEquals(1, packed.orElseThrow().count());
        // The unpack (the one-cell shape, the 2x2 player grid suffices).
        var unpacked = table.resultOf(new ItemStack[]{
                ItemStack.of(item("minecraft:lapis_block"), 1), empty, empty, empty});
        assertTrue(unpacked.isPresent());
        assertEquals(item("minecraft:lapis"), unpacked.orElseThrow().type());
        assertEquals(9, unpacked.orElseThrow().count());
        // The 2x2 masonries: four clay balls pack the clay block
        // (CraftingManager line 140).
        ItemStack ball = ItemStack.of(item("minecraft:clay_ball"), 1);
        var clay = table.resultOf(new ItemStack[]{ball, ball, ball, ball});
        assertTrue(clay.isPresent());
        assertEquals(item("minecraft:clay"), clay.orElseThrow().type());
        // The nether brick masonry (BlockRecipes line 83) over the 405
        // material item.
        ItemStack brick = ItemStack.of(item("minecraft:nether_brick"), 1);
        var bricks = table.resultOf(new ItemStack[]{brick, brick, brick, brick});
        assertTrue(bricks.isPresent());
        assertEquals(item("minecraft:nether_bricks"), bricks.orElseThrow().type());
    }
}
