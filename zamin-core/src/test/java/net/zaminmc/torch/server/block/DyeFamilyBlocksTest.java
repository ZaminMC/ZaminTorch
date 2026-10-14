package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.noteblock.NoteBlockSystem;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dye-coloring world families (Slice 31 — the reference's DyeRecipes.java
 * + the Block registrations 35/95/159/160/171/172): the flattened color
 * members, the damage-domain item model, the drop walks (the color riding
 * the damage field — the reference's getDropItemMetadata), the silk-only
 * glass pair, the placement resolution, and the five crafting families.
 */
class DyeFamilyBlocksTest {

    private static final ItemStack EMPTY = ItemStack.EMPTY;

    private static BlockType block(String id) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze()
                .lookup(Identifier.parse(id)).orElseThrow();
    }

    private static ItemType item(String id) {
        return BuiltinItems.lookup(Identifier.parse(id)).orElseThrow();
    }

    private static ItemStack stack(String id, int count, int damage) {
        return ItemStack.of(item(id), count).withDamage(damage);
    }

    // ---- the family model ---------------------------------------------------

    @Test
    void theSixteenColorsFlattenIntoPerColorBlocks() {
        // wool: white keeps the plain id, the fifteen others flatten
        assertEquals(Identifier.parse("minecraft:wool"), DyeFamilyBlocks.blockOf("wool", 0));
        assertEquals(Identifier.parse("minecraft:orange_wool"), DyeFamilyBlocks.blockOf("wool", 1));
        assertEquals(Identifier.parse("minecraft:black_wool"), DyeFamilyBlocks.blockOf("wool", 15));
        // the DyeColor getId order (the reference's own table)
        assertEquals(Identifier.parse("minecraft:stained_glass_light_blue"),
                DyeFamilyBlocks.stainedGlassBlock(3));
        assertEquals(Identifier.parse("minecraft:stained_glass_pane_silver"),
                DyeFamilyBlocks.stainedGlassPaneBlock(8));
        assertEquals(Identifier.parse("minecraft:carpet_magenta"), DyeFamilyBlocks.carpetBlock(2));
        assertEquals(Identifier.parse("minecraft:stained_clay_brown"),
                DyeFamilyBlocks.stainedClayBlock(12));
        // the predicates
        assertTrue(DyeFamilyBlocks.isWool(Identifier.parse("minecraft:wool")));
        assertTrue(DyeFamilyBlocks.isWool(Identifier.parse("minecraft:lime_wool")));
        assertFalse(DyeFamilyBlocks.isWool(Identifier.parse("minecraft:carpet_lime")));
        assertTrue(DyeFamilyBlocks.isStainedGlass(Identifier.parse("minecraft:stained_glass_red")));
        assertFalse(DyeFamilyBlocks.isStainedGlass(Identifier.parse("minecraft:stained_glass_pane_red")),
                "the pane family is its own predicate");
        assertTrue(DyeFamilyBlocks.isStainedGlassPane(Identifier.parse("minecraft:stained_glass_pane_red")));
        assertTrue(DyeFamilyBlocks.isCarpet(Identifier.parse("minecraft:carpet_white")));
        assertTrue(DyeFamilyBlocks.isStainedClay(Identifier.parse("minecraft:stained_clay_orange")),
                "the temple slice's pair joins the family");
        // the color read-back
        assertEquals(5, DyeFamilyBlocks.colorOf(Identifier.parse("minecraft:lime_wool")));
        assertEquals(11, DyeFamilyBlocks.colorOf(Identifier.parse("minecraft:stained_glass_blue")));
        assertEquals(0, DyeFamilyBlocks.colorOf(Identifier.parse("minecraft:wool")));
        // the placement resolution (ItemBlock's getStateFromMeta walk)
        assertEquals(Identifier.parse("minecraft:pink_wool"),
                DyeFamilyBlocks.placementBlockOf(Identifier.parse("minecraft:wool"), 6).orElseThrow());
        assertEquals(Identifier.parse("minecraft:stained_glass_cyan"),
                DyeFamilyBlocks.placementBlockOf(Identifier.parse("minecraft:stained_glass"), 9).orElseThrow());
        assertEquals(Identifier.parse("minecraft:carpet_black"),
                DyeFamilyBlocks.placementBlockOf(Identifier.parse("minecraft:carpet"), 15).orElseThrow());
        assertEquals(Identifier.parse("minecraft:stained_clay_green"),
                DyeFamilyBlocks.placementBlockOf(
                        Identifier.parse("minecraft:stained_hardened_clay"), 13).orElseThrow());
        assertTrue(DyeFamilyBlocks.placementBlockOf(Identifier.parse("minecraft:stone"), 3).isEmpty(),
                "a non-family item never resolves here");
    }

    @Test
    void everyFamilyMemberIsRegisteredWithItsBehaviorAndLightRow() {
        BlockRegistryBuilder builder = new BlockRegistryBuilder();
        BuiltinBlocks.registerAll(builder);
        var registry = builder.freeze();
        for (int color = 0; color < 16; color++) {
            // wool
            Identifier wool = DyeFamilyBlocks.blockOf("wool", color);
            assertTrue(registry.lookup(wool).isPresent(), wool + " is registered");
            BlockBehavior woolBehavior = BlockBehaviorTable.of(wool).orElseThrow();
            assertEquals(0.8, woolBehavior.hardness(), wool + " hardness");
            assertEquals("wool", woolBehavior.material());
            var woolDrop = woolBehavior.drops().get(0);
            assertEquals(Identifier.parse("minecraft:wool"), woolDrop.item());
            assertEquals(color, woolDrop.damage(), wool + " drops its color's damage");
            // stained glass: the silk-only family
            Identifier glass = DyeFamilyBlocks.stainedGlassBlock(color);
            assertTrue(registry.lookup(glass).isPresent(), glass + " is registered");
            BlockBehavior glassBehavior = BlockBehaviorTable.of(glass).orElseThrow();
            assertEquals(0.3, glassBehavior.hardness());
            assertTrue(glassBehavior.drops().isEmpty(), glass + " never drops by hand");
            assertEquals(0, BlockLightTable.filter(registry.lookup(glass).orElseThrow()),
                    glass + " passes light");
            // the stained pane: the same silk-only envelope
            Identifier pane = DyeFamilyBlocks.stainedGlassPaneBlock(color);
            assertTrue(registry.lookup(pane).isPresent());
            assertTrue(BlockBehaviorTable.of(pane).orElseThrow().drops().isEmpty());
            // carpet: the 1/16 cloth cover
            Identifier carpet = DyeFamilyBlocks.carpetBlock(color);
            assertTrue(registry.lookup(carpet).isPresent());
            BlockBehavior carpetBehavior = BlockBehaviorTable.of(carpet).orElseThrow();
            assertEquals(0.1, carpetBehavior.hardness());
            assertEquals("carpet", carpetBehavior.material(),
                    carpet + " is its own material (shears accelerate WOOL, never CARPET)");
            assertEquals(color, carpetBehavior.drops().get(0).damage());
            assertEquals(0, BlockLightTable.filter(registry.lookup(carpet).orElseThrow()),
                    "the registration's own setOpacity(0)");
            // stained clay: the stone pickaxe family, opaque
            Identifier clay = DyeFamilyBlocks.stainedClayBlock(color);
            assertTrue(registry.lookup(clay).isPresent(), clay + " is registered");
            BlockBehavior clayBehavior = BlockBehaviorTable.of(clay).orElseThrow();
            assertEquals(1.25, clayBehavior.hardness());
            assertTrue(clayBehavior.requiresTool(), clay + " needs the pickaxe");
            assertEquals(Identifier.parse("minecraft:stained_hardened_clay"),
                    clayBehavior.drops().get(0).item());
            assertEquals(color, clayBehavior.drops().get(0).damage());
            assertEquals(15, BlockLightTable.filter(registry.lookup(clay).orElseThrow()));
        }
        // hardened clay: the parent block
        BlockBehavior plain = BlockBehaviorTable.of(
                Identifier.parse("minecraft:hardened_clay")).orElseThrow();
        assertEquals(1.25, plain.hardness());
        assertTrue(plain.requiresTool());
        assertEquals(Identifier.parse("minecraft:hardened_clay"), plain.drops().get(0).item());
    }

    // ---- the drop walks -----------------------------------------------------

    @Test
    void theColoredFamiliesDropTheirColorOnTheDamageField() {
        DropService drops = new DropService(new Random(1L));
        // lime wool -> one wool, damage 5 (getDropItemMetadata)
        var lime = drops.dropsFor(block("minecraft:lime_wool"), null);
        assertEquals(1, lime.size());
        assertEquals(Identifier.parse("minecraft:wool"), lime.get(0).type().identifier());
        assertEquals(5, lime.get(0).damage());
        // the black carpet -> carpet damage 15
        var carpet = drops.dropsFor(block("minecraft:carpet_black"), null);
        assertEquals(15, carpet.get(0).damage());
        // the clay family demands the pickaxe (requiresTool — the bare
        // hand breaks it but the drops gate refuses, the vanilla rule)
        ItemType pickaxe = item("minecraft:wooden_pickaxe");
        assertTrue(drops.dropsFor(block("minecraft:stained_clay_blue"), null).isEmpty(),
                "the bare hand never yields the clay");
        var clay = drops.dropsFor(block("minecraft:stained_clay_blue"), pickaxe);
        assertEquals(Identifier.parse("minecraft:stained_hardened_clay"),
                clay.get(0).type().identifier());
        assertEquals(11, clay.get(0).damage());
        // the temple pair's repair: the orange egg drops the family item
        // (the pre-31 row referenced an unregistered item — the latent
        // crash Slice 18c's own fix pattern closed for the bricks)
        var temple = drops.dropsFor(block("minecraft:stained_clay_orange"), pickaxe);
        assertEquals(Identifier.parse("minecraft:stained_hardened_clay"),
                temple.get(0).type().identifier());
        assertEquals(1, temple.get(0).damage());
        // hardened clay drops itself (the pickaxe again — the same tier 0)
        var hard = drops.dropsFor(block("minecraft:hardened_clay"), pickaxe);
        assertEquals(Identifier.parse("minecraft:hardened_clay"),
                hard.get(0).type().identifier());
    }

    @Test
    void theGlassPairIsSilkOnly() {
        DropService drops = new DropService(new Random(2L));
        // the bare hand gets nothing (getBaseDropCount 0 / hasDrops false)
        assertTrue(drops.dropsFor(block("minecraft:stained_glass_red"), null).isEmpty());
        assertTrue(drops.dropsFor(block("minecraft:stained_glass_pane_white"), null).isEmpty());
        // silk returns the family item with the color on the damage field
        var glass = drops.dropsFor(block("minecraft:stained_glass_red"), null, true, 0);
        assertEquals(1, glass.size());
        assertEquals(Identifier.parse("minecraft:stained_glass"), glass.get(0).type().identifier());
        assertEquals(14, glass.get(0).damage(), "red is DyeColor id 14");
        var pane = drops.dropsFor(block("minecraft:stained_glass_pane_silver"), null, true, 0);
        assertEquals(Identifier.parse("minecraft:stained_glass_pane"), pane.get(0).type().identifier());
        assertEquals(8, pane.get(0).damage(), "silver is DyeColor id 8");
    }

    // ---- the crafting families ----------------------------------------------

    private static ItemStack[] grid3x3(ItemStack... cells) {
        if (cells.length != 9) {
            throw new IllegalArgumentException("a 3x3 grid needs 9 cells");
        }
        return cells;
    }

    @Test
    void theWoolColoringRowsMatchTheInvertedDyeDomain() {
        CraftingService service = CraftingService.builtin();
        // dye 14 (orange dye — DyeColor.ORANGE's inverted item metadata)
        // plus a WHITE wool yields the orange wool (damage 1)
        var orange = service.resultOf3x3(grid3x3(
                EMPTY, EMPTY, EMPTY,
                EMPTY, stack("minecraft:dye", 1, 14), EMPTY,
                EMPTY, stack("minecraft:wool", 1, 0), EMPTY));
        assertTrue(orange.isPresent(), "orange dye + white wool");
        assertEquals(Identifier.parse("minecraft:wool"), orange.orElseThrow().type().identifier());
        assertEquals(1, orange.orElseThrow().damage());
        // bone meal (15) + white wool -> white wool: the reference's own
        // i = 0 row (a no-op craft that vanilla itself registers)
        var white = service.resultOf3x3(grid3x3(
                stack("minecraft:dye", 1, 15), EMPTY, EMPTY,
                EMPTY, stack("minecraft:wool", 1, 0), EMPTY,
                EMPTY, EMPTY, EMPTY));
        assertTrue(white.isPresent());
        assertEquals(0, white.orElseThrow().damage());
        // an off-color wool never takes the dye (the exact-damage gate):
        // dye 14 + a RED wool matches nothing
        var wrong = service.resultOf3x3(grid3x3(
                EMPTY, EMPTY, EMPTY,
                EMPTY, stack("minecraft:dye", 1, 14), EMPTY,
                EMPTY, stack("minecraft:wool", 1, 14), EMPTY));
        assertTrue(wrong.isEmpty(), "only the white wool takes the coloring row");
    }

    @Test
    void theRingRecipesCraftTheStainedFamilies() {
        CraftingService service = CraftingService.builtin();
        ItemStack clay = stack("minecraft:hardened_clay", 1, 0);
        // lapis (dye item 4 = DyeColor.BLUE 11) around eight hardened clay
        // -> eight BLUE stained clay (damage 11)
        var blue = service.resultOf3x3(grid3x3(
                clay, clay, clay,
                clay, stack("minecraft:dye", 1, 4), clay,
                clay, clay, clay));
        assertTrue(blue.isPresent(), "the lapis ring");
        assertEquals(Identifier.parse("minecraft:stained_hardened_clay"),
                blue.orElseThrow().type().identifier());
        assertEquals(8, blue.orElseThrow().count());
        assertEquals(11, blue.orElseThrow().damage(), "the dye's DyeColor lands whole");
        // the stained glass ring: the same dye over eight glass
        ItemStack glass = stack("minecraft:glass", 1, 0);
        var stained = service.resultOf3x3(grid3x3(
                glass, glass, glass,
                glass, stack("minecraft:dye", 1, 4), glass,
                glass, glass, glass));
        assertTrue(stained.isPresent());
        assertEquals(Identifier.parse("minecraft:stained_glass"),
                stained.orElseThrow().type().identifier());
        assertEquals(8, stained.orElseThrow().count());
        assertEquals(11, stained.orElseThrow().damage());
    }

    @Test
    void thePaneFoldAndTheCarpetRowsCraft() {
        CraftingService service = CraftingService.builtin();
        // six blue stained glass -> sixteen blue panes (the SAME damage)
        ItemStack blueGlass = stack("minecraft:stained_glass", 1, 11);
        var panes = service.resultOf3x3(grid3x3(
                blueGlass, blueGlass, blueGlass,
                blueGlass, blueGlass, blueGlass,
                EMPTY, EMPTY, EMPTY));
        assertTrue(panes.isPresent(), "the two-row fold matches at the bounding box");
        assertEquals(Identifier.parse("minecraft:stained_glass_pane"),
                panes.orElseThrow().type().identifier());
        assertEquals(16, panes.orElseThrow().count());
        assertEquals(11, panes.orElseThrow().damage());
        // two same-color wool -> three carpets of the color (the 2x1 pattern
        // fits the 2x2 inventory grid too)
        var carpet = service.resultOf(new ItemStack[] {
                stack("minecraft:wool", 1, 5), stack("minecraft:wool", 1, 5),
                EMPTY, EMPTY });
        assertTrue(carpet.isPresent(), "lime wool pair");
        assertEquals(Identifier.parse("minecraft:carpet"), carpet.orElseThrow().type().identifier());
        assertEquals(3, carpet.orElseThrow().count());
        assertEquals(5, carpet.orElseThrow().damage());
        // two DIFFERENT colors never fold (the exact-damage gate)
        var mixed = service.resultOf(new ItemStack[] {
                stack("minecraft:wool", 1, 5), stack("minecraft:wool", 1, 6),
                EMPTY, EMPTY });
        assertTrue(mixed.isEmpty(), "the carpet row demands one color");
    }

    @Test
    void theFlowerDyeRowsMatch() {
        CraftingService service = CraftingService.builtin();
        // the dandelion yields TWO yellow dye (the reference's own pair)
        var yellow = service.resultOf(new ItemStack[] {
                stack("minecraft:dandelion", 1, 0), EMPTY, EMPTY, EMPTY });
        assertTrue(yellow.isPresent());
        assertEquals(Identifier.parse("minecraft:dye"), yellow.orElseThrow().type().identifier());
        assertEquals(2, yellow.orElseThrow().count());
        assertEquals(11, yellow.orElseThrow().damage(), "DyeColor.YELLOW's item metadata 11");
        // the poppy yields one rose red (damage 1)
        var red = service.resultOf(new ItemStack[] {
                stack("minecraft:poppy", 1, 0), EMPTY, EMPTY, EMPTY });
        assertTrue(red.isPresent());
        assertEquals(1, red.orElseThrow().count());
        assertEquals(1, red.orElseThrow().damage());
    }

    // ---- the world surfaces -------------------------------------------------

    @Test
    void theSolidityAndInstrumentTablesCarryTheFamilies() {
        // carpets never block a body (the 1/16 shape — the snow layer's own
        // envelope); the panes and wool stay solid (their thin boxes still
        // stop a walk in the reference)
        assertTrue(WorldSolidity.isSolid(block("minecraft:wool")));
        assertTrue(WorldSolidity.isSolid(block("minecraft:black_wool")));
        assertTrue(WorldSolidity.isSolid(block("minecraft:stained_glass_pane_white")));
        assertFalse(WorldSolidity.isSolid(block("minecraft:carpet_red")));
        // the note block instruments: the stained clay family and hardened
        // clay are STONE (the bass drum), the stained glass pair GLASS (the
        // hat), the carpet's own Material.CARPET falls to the harp
        assertEquals(1, NoteBlockSystem.instrumentOf("stained_clay_blue"));
        assertEquals(1, NoteBlockSystem.instrumentOf("hardened_clay"));
        assertEquals(1, NoteBlockSystem.instrumentOf("stained_clay_orange"));
        assertEquals(3, NoteBlockSystem.instrumentOf("stained_glass_red"));
        assertEquals(3, NoteBlockSystem.instrumentOf("stained_glass_pane_lime"));
        assertEquals(0, NoteBlockSystem.instrumentOf("carpet_magenta"));
    }

    @Test
    void theFamilyItemsShareTheVanillaDamageDomain() {
        // the five family items are single damage-domain items (max stack 64)
        List<Identifier> items = DyeFamilyBlocks.FAMILY_ITEMS;
        assertEquals(5, items.size());
        for (Identifier id : items) {
            assertTrue(BuiltinItems.lookup(id).isPresent(), id + " is registered");
            assertEquals(64, BuiltinItems.lookup(id).orElseThrow().maxStackSize());
        }
        // the stained clay family item is the vanilla stained_hardened_clay
        // (the reference's own registry name over block 159)
        assertTrue(BuiltinItems.lookup(
                Identifier.parse("minecraft:stained_hardened_clay")).isPresent());
        // the family items resolve their placement members
        assertTrue(DyeFamilyBlocks.placementBlockOf(
                Identifier.parse("minecraft:stained_glass_pane"), 12).isPresent());
    }
}
