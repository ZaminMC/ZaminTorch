package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.experience.ExperienceAwards;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The silk-touch and shears parity law (the silk-arm parity audit — the
 * second follow-up to the tall-grass fix, the same class of bug: a vanilla
 * silk or shears gate the engine never carried, each read from
 * reference/1.8.8 before the fix):
 *
 * <ul>
 * <li>The default silk gate is {@code isCube() && !hasBlockEntity} (Block
 *     lines 784-787) — so every full cube silk-harvests ITSELF unless the
 *     row says otherwise: the glowstone's dust band, the melon's slices,
 *     the clay's four balls, the snow block's four snowballs, the
 *     mycelium's dirt, the quartz ore's gem. The pre-fix walk answered
 *     the by-hand drops under silk for every one of them.</li>
 * <li>The redstone ore's own silk override: {@code getSilkTouchDrop}
 *     (RedstoneOreBlock lines 134-137) answers
 *     {@code new ItemStack(Blocks.REDSTONE_ORE)} — the PLAIN ORE's item
 *     — for BOTH forms, so a silk break of the LIT form (the one the
 *     dig-start light swap actually leaves under the pickaxe) answers
 *     the ORE, never nothing. The pre-fix walk answered NOTHING for the
 *     lit form (the default {@code Item.byBlock} walk's null, read
 *     without the override).</li>
 * <li>PaneBlock line 144 and StainedGlassBlock line 55 override the silk
 *     gate TRUE (the non-cube override): the plain pane takes itself, the
 *     stained pair take the FAMILY item with the COLOR in the damage
 *     field. The pre-fix rows were empty and the silk set carried
 *     neither family.</li>
 * <li>CobwebBlock lines 41-45 + line 47: one STRING by hand (the pre-fix
 *     table carried NO row at all — every break answered nothing) and the
 *     web itself under silk.</li>
 * <li>VineBlock lines 303-318: the by-hand walk answers nothing at all;
 *     the SHEARS arm of afterMinedByPlayer drops the vine itself. The
 *     pre-fix rows were empty with no shears branch.</li>
 * <li>DoublePlantBlock lines 141-191: SHEARS on the LOWER half of the
 *     grass/fern pair answers TWO of the tallgrass item (TYPE damage 1
 *     grass / 2 fern), replacing the seed roll; the upper-half pair arm
 *     carries the same two. The pre-fix walk had no lower shears arm and
 *     the pair arm answered one.</li>
 * <li>DirtBlock lines 72-79 + Block lines 791-797: the podzol's by-hand
 *     stack downgrades to plain DIRT (damage 0) and the silk stack keeps
 *     the PODZOL metadata (damage 2); the coarse dirt answers the dirt
 *     item damage 1 under either arm. The pre-fix table carried no row
 *     for either block — every break answered nothing.</li>
 * <li>The mining XP gate: the silk arm REPLACES the drop walk wholesale
 *     (Block lines 772-781 — dropItems, the OreBlock XP's own carrier,
 *     never runs), so a silk harvest pays NO mining XP; and the lit form
 *     (the one actually broken) walks the same 1 + nextInt(5) band the
 *     unlit row carries (RedstoneOreBlock line 84).</li>
 * </ul>
 */
class SilkAndShearsParityAcceptanceTest {

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(Identifier.parse(value)).orElseThrow();
    }

    /** @return the identifier every dropped stack of {@code drops} carries. */
    private static List<String> droppedItems(List<ItemStack> drops) {
        return drops.stream().map(stack -> stack.type().identifier().toString()).toList();
    }

    @Test
    void glowstoneSilkTakesTheBlockAndTheHandKeepsTheBand() {
        // The full cube's own gate (Block lines 784-787): silk answers the
        // glowstone itself — the pre-fix band branch answered the dust
        // under silk too. The hand keeps the reference's 1-4 band.
        DropService drops = new DropService(new Random(0x51L));
        List<ItemStack> silk = drops.dropsFor(block("minecraft:glowstone"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:glowstone", silk.get(0).type().identifier().toString());
        assertEquals(1, silk.get(0).count());
        for (int i = 0; i < 512; i++) {
            List<ItemStack> hand = drops.dropsFor(block("minecraft:glowstone"), null, false, 0);
            assertEquals(List.of("minecraft:glowstone_dust"), droppedItems(hand));
            assertTrue(hand.get(0).count() >= 1 && hand.get(0).count() <= 4,
                    "the band stays 1-4: " + hand.get(0).count());
        }
    }

    @Test
    void melonSilkTakesTheBlockTheHandKeepsTheSlices() {
        // MelonBlock: full cube, no block entity — the default gate is
        // TRUE, so silk takes the melon BLOCK (the pre-fix branch answered
        // the 3-9 slice walk under every tool).
        DropService drops = new DropService(new Random(0x31L));
        List<ItemStack> silk = drops.dropsFor(block("minecraft:melon_block"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:melon_block", silk.get(0).type().identifier().toString());
        for (int i = 0; i < 256; i++) {
            List<ItemStack> hand = drops.dropsFor(block("minecraft:melon_block"), null, false, 0);
            assertEquals(List.of("minecraft:melon"), droppedItems(hand));
            assertTrue(hand.get(0).count() >= 3 && hand.get(0).count() <= 9,
                    "the slice band stays 3-9: " + hand.get(0).count());
        }
    }

    @Test
    void redstoneOreSilkAnswersTheOreForBothForms() {
        // The class's OWN silk override (RedstoneOreBlock.getSilkTouchDrop
        // lines 134-137): {@code new ItemStack(Blocks.REDSTONE_ORE)} — the
        // PLAIN ORE's registered item, answered for BOTH forms (the
        // override rides the class, and the dig-start light swap means
        // the lit form is the one actually broken under a silk pickaxe:
        // silk-touching a redstone ore returns the ORE, never nothing).
        // The pre-fix walk answered NOTHING for the lit form — the
        // audit that pinned the empty list had read the DEFAULT
        // Item.byBlock walk ({@code Item.byBlock(LIT)} → null, no item
        // registered for the lit block) and never saw the override; a
        // silk pick lost the block ENTIRELY. (The ore is pickaxe-gated:
        // the held tool rides the walk like the reference's own
        // afterMinedByPlayer.)
        DropService drops = new DropService(new Random(0xED5L));
        List<ItemStack> unlitSilk = drops.dropsFor(
                block("minecraft:redstone_ore"), BuiltinItems.DIAMOND_PICKAXE, true, 0);
        assertEquals(1, unlitSilk.size());
        assertEquals("minecraft:redstone_ore",
                unlitSilk.get(0).type().identifier().toString());
        List<ItemStack> litSilk = drops.dropsFor(
                block("minecraft:lit_redstone_ore"), BuiltinItems.DIAMOND_PICKAXE, true, 0);
        assertEquals(1, litSilk.size(),
                "the lit form's silk answers the plain ore item");
        assertEquals("minecraft:redstone_ore",
                litSilk.get(0).type().identifier().toString());
        assertEquals(1, litSilk.get(0).count());
        for (int i = 0; i < 256; i++) {
            List<ItemStack> litHand = drops.dropsFor(
                    block("minecraft:lit_redstone_ore"), BuiltinItems.DIAMOND_PICKAXE, false, 0);
            assertEquals(List.of("minecraft:redstone"), droppedItems(litHand));
            assertTrue(litHand.get(0).count() >= 4 && litHand.get(0).count() <= 5,
                    "the dust band stays 4-5: " + litHand.get(0).count());
        }
    }

    @Test
    void claySnowMyceliumAndQuartzOreTakeThemselvesUnderSilk() {
        // Four full cubes whose by-hand rows DIVERGE from the block: the
        // clay's four balls, the snow block's four snowballs, the
        // mycelium's dirt, the quartz ore's gem — silk takes the BLOCK
        // itself for each (the pre-fix walk answered the by-hand rows
        // under silk too). The mining-speed parity audit re-pinned the
        // snow seat: the SNOW material requires the tool (Material line
        // 29) with the ShovelItem's own canMineBlock as the gate — the
        // silk and the by-hand reads move to a SHOVEL, the bare hand
        // answering the gate's own nothing.
        DropService drops = new DropService(new Random(0xF11L));
        List<ItemStack> clay = drops.dropsFor(block("minecraft:clay"), null, true, 0);
        assertEquals(List.of("minecraft:clay"), droppedItems(clay));
        assertEquals(4, drops.dropsFor(block("minecraft:clay"), null, false, 0)
                .get(0).count(), "the by-hand clay keeps four balls");
        List<ItemStack> snow = drops.dropsFor(block("minecraft:snow"),
                BuiltinItems.DIAMOND_SHOVEL, true, 0);
        assertEquals(List.of("minecraft:snow"), droppedItems(snow));
        assertEquals(4, drops.dropsFor(block("minecraft:snow"),
                        BuiltinItems.DIAMOND_SHOVEL, false, 0)
                .get(0).count(), "the shovel's four snowballs stay flat");
        assertTrue(drops.dropsFor(block("minecraft:snow"), null, false, 0).isEmpty(),
                "the bare hand harvests nothing (the SNOW material's own gate)");
        List<ItemStack> mycelium = drops.dropsFor(block("minecraft:mycelium"), null, true, 0);
        assertEquals(List.of("minecraft:mycelium"), droppedItems(mycelium));
        assertEquals(List.of("minecraft:dirt"),
                droppedItems(drops.dropsFor(block("minecraft:mycelium"), null, false, 0)));
        List<ItemStack> quartzOre = drops.dropsFor(
                block("minecraft:quartz_ore"), BuiltinItems.DIAMOND_PICKAXE, true, 0);
        assertEquals(List.of("minecraft:quartz_ore"), droppedItems(quartzOre));
        assertEquals(List.of("minecraft:quartz"),
                droppedItems(drops.dropsFor(
                        block("minecraft:quartz_ore"), BuiltinItems.DIAMOND_PICKAXE, false, 0)));
    }

    @Test
    void theGlassPairTakesItselfUnderSilkAndNothingByHand() {
        // PaneBlock line 144: the pane's own override — silk takes the pane
        // item 102; the by-hand row stays empty. StainedGlassBlock line 55
        // + the pane's own gate: the FAMILY item with the COLOR in the
        // damage field (red 14) — the pre-fix rows were empty and no silk
        // arm existed for either family.
        DropService drops = new DropService(new Random(0xFA5L));
        List<ItemStack> pane = drops.dropsFor(block("minecraft:glass_pane"), null, true, 0);
        assertEquals(1, pane.size());
        assertEquals("minecraft:glass_pane", pane.get(0).type().identifier().toString());
        assertTrue(drops.dropsFor(block("minecraft:glass_pane"), null, false, 0).isEmpty());
        List<ItemStack> stained = drops.dropsFor(
                block("minecraft:stained_glass_red"), null, true, 0);
        assertEquals(1, stained.size());
        assertEquals("minecraft:stained_glass",
                stained.get(0).type().identifier().toString());
        assertEquals(14, stained.get(0).damage(), "the red color rides the damage field");
        assertTrue(drops.dropsFor(
                block("minecraft:stained_glass_red"), null, false, 0).isEmpty());
        List<ItemStack> stainedPane = drops.dropsFor(
                block("minecraft:stained_glass_pane_red"), null, true, 0);
        assertEquals(1, stainedPane.size());
        assertEquals("minecraft:stained_glass_pane",
                stainedPane.get(0).type().identifier().toString());
        assertEquals(14, stainedPane.get(0).damage());
        assertTrue(drops.dropsFor(
                block("minecraft:stained_glass_pane_red"), null, false, 0).isEmpty());
    }

    @Test
    void theCobwebAnswersOneStringByHandAndItselfUnderSilk() {
        // CobwebBlock lines 41-45: getDropItem STRING, base ONE — the
        // pre-fix table carried NO row, so every break answered nothing;
        // line 47's own gate: silk takes the web itself. The mining-speed
        // parity audit re-pinned the seat's held tools: the COBWEB
        // material requires the tool (Material lines 36-41) and the web's
        // breakers are the SWORD and the SHEARS (SwordItem line 84,
        // ShearsItem lines 35-37), so the string and the silk web belong
        // to that pair — the bare hand and every other tool answer the
        // gate's own nothing (the pre-fix row had left the web tool-free,
        // a bare hand taking the string).
        DropService drops = new DropService(new Random(0x3E8L));
        for (int i = 0; i < 64; i++) {
            List<ItemStack> sword = drops.dropsFor(block("minecraft:cobweb"),
                    BuiltinItems.IRON_SWORD, false, 0);
            assertEquals(List.of("minecraft:string"), droppedItems(sword));
            assertEquals(1, sword.get(0).count());
            List<ItemStack> shears = drops.dropsFor(block("minecraft:cobweb"),
                    BuiltinItems.SHEARS, false, 0);
            assertEquals(List.of("minecraft:string"), droppedItems(shears));
            assertTrue(drops.dropsFor(block("minecraft:cobweb"), null, false, 0).isEmpty(),
                    "the bare hand harvests nothing from the web");
        }
        List<ItemStack> silk = drops.dropsFor(block("minecraft:cobweb"),
                BuiltinItems.IRON_SWORD, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:cobweb", silk.get(0).type().identifier().toString());
        assertTrue(drops.dropsFor(block("minecraft:cobweb"),
                        BuiltinItems.DIAMOND_PICKAXE, true, 0).isEmpty(),
                "the pickaxe never harvests the web, even under silk");
    }

    @Test
    void vinesAnswerTheShearsItemAlone() {
        // VineBlock lines 303-318: getDropItem NULL, base ZERO — the
        // by-hand break yields nothing; the SHEARS arm of
        // afterMinedByPlayer drops the vine itself. The pre-fix rows
        // were empty with no shears branch anywhere.
        DropService drops = new DropService(new Random(0x41AL));
        for (int i = 0; i < 64; i++) {
            assertTrue(drops.dropsFor(block("minecraft:vine"), null, false, 0).isEmpty(),
                    "the bare hand yields nothing");
            List<ItemStack> shears = drops.dropsFor(
                    block("minecraft:vine"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, shears.size());
            assertEquals("minecraft:vine", shears.get(0).type().identifier().toString());
        }
    }

    @Test
    void doublePlantShearsAnswersTwoTallGrassOnTheLowerBreak() {
        // DoublePlantBlock.onMinedByPlayer lines 181-191: the shears break
        // of the LOWER half answers TWO of the tallgrass item — the grass
        // TYPE 1, the fern TYPE 2 — REPLACING the by-hand walk (the
        // pre-fix walk had no shears arm, so the double grass rolled the
        // 1/8 seed roll and the fern answered nothing). The flowers'
        // variants answer false from onMinedByPlayer, so they keep their
        // own rows under shears.
        DropService drops = new DropService(new Random(0x0B5L));
        for (int i = 0; i < 32; i++) {
            List<ItemStack> grass = drops.dropsFor(
                    block("minecraft:double_grass"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, grass.size());
            assertEquals("minecraft:tall_grass", grass.get(0).type().identifier().toString());
            assertEquals(2, grass.get(0).count(), "the reference drops TWO, never one");
            assertEquals(1, grass.get(0).damage());
            List<ItemStack> fern = drops.dropsFor(
                    block("minecraft:double_fern"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, fern.size());
            assertEquals("minecraft:tall_grass", fern.get(0).type().identifier().toString());
            assertEquals(2, fern.get(0).count());
            assertEquals(2, fern.get(0).damage());
            // The flowers keep their own rows under shears (themselves).
            List<ItemStack> sunflower = drops.dropsFor(
                    block("minecraft:sunflower"), BuiltinItems.SHEARS, false, 0);
            assertEquals(List.of("minecraft:double_plant"), droppedItems(sunflower));
            assertEquals(1, sunflower.get(0).count());
        }
        // The bare hand keeps the reference's walks: the double grass's
        // 1/8 seed roll, the fern's nothing.
        boolean sawSeeds = false;
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> hand = drops.dropsFor(
                    block("minecraft:double_grass"), null, false, 0);
            assertTrue(hand.isEmpty()
                            || hand.stream().allMatch(stack -> stack.type().identifier()
                                    .equals(Identifier.parse("minecraft:wheat_seeds"))),
                    "the by-hand walk rolls only seeds");
            sawSeeds |= !hand.isEmpty();
        }
        assertTrue(sawSeeds, "the wide sample sees the seed roll");
        assertTrue(drops.dropsFor(
                block("minecraft:double_fern"), null, false, 0).isEmpty());
    }

    @Test
    void podzolDropsPlainDirtByHandAndItselfUnderSilk() {
        // DirtBlock lines 72-79: getDropItemMetadata downgrades PODZOL to
        // plain DIRT for the by-hand stack; Block lines 791-797: the silk
        // stack keeps the state's metadata (PODZOL 2). The coarse dirt
        // answers the dirt item damage 1 under either arm. The pre-fix
        // table carried NO row for either block — every break answered
        // nothing at all.
        DropService drops = new DropService(new Random(0x0D1L));
        List<ItemStack> hand = drops.dropsFor(block("minecraft:podzol"), null, false, 0);
        assertEquals(1, hand.size());
        assertEquals("minecraft:dirt", hand.get(0).type().identifier().toString());
        assertEquals(0, hand.get(0).damage(), "the by-hand podzol downgrades to plain dirt");
        List<ItemStack> silk = drops.dropsFor(block("minecraft:podzol"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:dirt", silk.get(0).type().identifier().toString());
        assertEquals(2, silk.get(0).damage(), "the silk stack keeps the podzol metadata");
        for (boolean silkArm : new boolean[] {false, true}) {
            List<ItemStack> coarse = drops.dropsFor(
                    block("minecraft:coarse_dirt"), null, silkArm, 0);
            assertEquals(1, coarse.size());
            assertEquals("minecraft:dirt", coarse.get(0).type().identifier().toString());
            assertEquals(1, coarse.get(0).damage(), "the coarse dirt IS the dirt item 1");
        }
    }

    @Test
    void theLitRedstoneOreRollsTheOneToFiveXpBand() {
        // RedstoneOreBlock lines 81-87: the ore pays 1 + nextInt(5) when
        // the dust drops — the lit form carries the same band (the same
        // class), and the dig-start light swap means the LIT form is the
        // one the break path sees; the pre-fix award table carried no lit
        // row, so every redstone-ore break paid NOTHING. The silk arm
        // replaces the whole walk (the XP carrier never runs) — the gate
        // itself rides the interaction service's own call site.
        Random random = new Random(0x3F5L);
        boolean[] seen = new boolean[6];
        for (int i = 0; i < 4096; i++) {
            int amount = ExperienceAwards.forBlockBreak(
                    Identifier.parse("minecraft:lit_redstone_ore"), random);
            assertTrue(amount >= 1 && amount <= 5, "the band stays 1-5: " + amount);
            seen[amount] = true;
        }
        for (int band = 1; band <= 5; band++) {
            assertTrue(seen[band], "band member " + band + " appears");
        }
        // The unlit row keeps its own band (the pair stay identical).
        for (int i = 0; i < 64; i++) {
            int amount = ExperienceAwards.forBlockBreak(
                    Identifier.parse("minecraft:redstone_ore"), random);
            assertTrue(amount >= 1 && amount <= 5);
        }
    }
}
