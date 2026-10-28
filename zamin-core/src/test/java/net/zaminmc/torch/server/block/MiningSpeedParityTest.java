package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.interaction.MiningRules;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mining-speed parity law (the verifier's fifth pass — the speed
 * family, each law read from reference/1.8.8 before the fix):
 *
 * <ul>
 * <li>The per-item speed rows (the five {@code getMiningSpeed} overrides):
 *     the PICKAXE's material branch plus its explicit effective-blocks set
 *     (PickaxeItem lines 24-28 — the ICE and the RAIL pair the engine's
 *     material branch alone never covered), the AXE's material branch over
 *     the whole plant family plus its set (AxeItem lines 21-24 — an iron
 *     axe digs tall grass at the full 6.0 tier), the SHOVEL's pure set
 *     (ShovelItem lines 13-15), the SWORD's 15.0 web and 1.5 plant band
 *     (SwordItem lines 33-42 — the pre-fix table had NO sword row at
 *     all), and the SHEARS' 15.0 web/leaves, 5.0 wool, 1.0 else band
 *     (ShearsItem lines 39-44 — the pre-fix class match paid the shears'
 *     IRON tier 6.0 on leaves and wool both).</li>
 * <li>The harvest gate (PlayerInventory.canMineBlock lines 458-465, the
 *     same flag Block.getMiningSpeed line 490 splits the /30 divisor on):
 *     the COBWEB material requires the tool (Material lines 36-41) and its
 *     own breakers are the SWORD and the SHEARS together (SwordItem line
 *     84, ShearsItem lines 35-37 — the pre-fix single-class match could
 *     not answer a two-class gate); the SNOW pair requires the SHOVEL
 *     alone (Material line 29, ShovelItem lines 22-24 — the pre-fix rows
 *     left both snow shapes hand-harvestable, so a bare hand took the
 *     snow block's four balls); and PACKED_ICE requires NO tool at all
 *     (Material line 27 — the pre-fix row's requires-tool had every
 *     non-pickaxe hand digging at the /100 divisor).</li>
 * <li>The snow layer's own drop law (SnowLayerBlock.afterMinedByPlayer
 *     lines 102-106: LAYERS + 1 snowballs on the player-dig completion,
 *     the caller gating on the same shovel verdict): the engine's
 *     single-layer state answers TWO — the pre-fix row carried the
 *     Slice 17d misread ("drops nothing") that had folded the player arm
 *     into the generic arm's empty walk.</li>
 * <li>The status-effect multipliers (PlayerEntity.getMiningSpeed lines
 *     663-695): Haste {@code 1 + 0.2*(amplifier+1)} and mining fatigue
 *     {@code 0.3 / 0.09 / 0.0027 / 8.1e-4} by amplifier, both between the
 *     Efficiency add and the environment divisors — the five-arg shape's
 *     own javadoc had promised them "once the engine grows a status-effect
 *     system", and the system (potions, beacons, the elder aura) had since
 *     grown without the multipliers landing.</li>
 * <li>The material-string re-pins the speed table exposed: the CACTUS is
 *     its own material (CactusBlock line 30 — the "plants" string would
 *     have paid both the axe branch and the sword band), the PUMPKIN trio
 *     is Material.PUMPKIN (PumpkinBlock line 33, MelonBlock line 13 — the
 *     sword's 1.5 band members), and the BOOKSHELF is WOOD
 *     (BookshelfBlock line 12 — the pre-fix null material left the axe at
 *     1.0 on a block the reference digs at the tier).</li>
 * </ul>
 */
class MiningSpeedParityTest {

    private static final DropService DROPS = new DropService(new Random(0x5EEDL));

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(Identifier.parse(value)).orElseThrow();
    }

    private static BlockBehavior behavior(String value) {
        return BlockBehaviorTable.of(Identifier.parse(value)).orElseThrow();
    }

    private static double speed(String blockValue, net.zaminmc.torch.item.ItemType held) {
        net.zaminmc.torch.block.BlockType type = block(blockValue);
        return BlockBehaviorTable.speedMultiplier(behavior(blockValue), type, held);
    }

    @Test
    void theSwordOwnsTheWebAndThePlantBand() {
        // SwordItem.getMiningSpeed lines 33-42: the WEB block identity 15.0
        // first, then the 1.5 band over PLANT/REPLACEABLE_PLANT/CORAL/
        // LEAVES/PUMPKIN materials, 1.0 for everything else. The pre-fix
        // table had no sword speed row at all: every sword answered 1.0.
        assertEquals(15.0, speed("minecraft:cobweb", BuiltinItems.IRON_SWORD),
                "the web pays the sword's own 15.0 (SwordItem line 35)");
        assertEquals(1.5, speed("minecraft:pumpkin", BuiltinItems.IRON_SWORD),
                "the PUMPKIN material rides the 1.5 band");
        assertEquals(1.5, speed("minecraft:lit_pumpkin", BuiltinItems.IRON_SWORD));
        assertEquals(1.5, speed("minecraft:melon_block", BuiltinItems.IRON_SWORD),
                "the melon rides the same PUMPKIN material (MelonBlock line 13)");
        assertEquals(1.5, speed("minecraft:vine", BuiltinItems.IRON_SWORD),
                "REPLACEABLE_PLANT rides the band");
        assertEquals(1.5, speed("minecraft:tall_grass", BuiltinItems.IRON_SWORD));
        assertEquals(1.5, speed("minecraft:oak_leaves", BuiltinItems.IRON_SWORD),
                "every leaves material rides the band");
        assertEquals(1.0, speed("minecraft:stone", BuiltinItems.IRON_SWORD),
                "stone stays the bare 1.0");
        assertEquals(1.0, speed("minecraft:wool", BuiltinItems.IRON_SWORD),
                "wool sits outside the band");
        assertEquals(1.0, speed("minecraft:crafting_table", BuiltinItems.IRON_SWORD),
                "WOOD is not in the band (SwordItem's own list)");
        assertEquals(1.0, speed("minecraft:cactus", BuiltinItems.IRON_SWORD),
                "the CACTUS material is its own — outside the band (line 30)");
    }

    @Test
    void theShearsSpeedBandSplitsTheWebLeavesWool() {
        // ShearsItem.getMiningSpeed lines 39-44: 15.0 on the WEB and every
        // LEAVES material, 5.0 on the WOOL, 1.0 for everything else. The
        // pre-fix class match paid the shears' IRON tier 6.0 on leaves and
        // wool both, and the web row's null material answered 1.0.
        assertEquals(15.0, speed("minecraft:cobweb", BuiltinItems.SHEARS),
                "the web pays the shears' own 15.0 (line 41)");
        assertEquals(15.0, speed("minecraft:oak_leaves", BuiltinItems.SHEARS),
                "every leaves material pays 15.0 — the pre-fix table answered the 6.0 tier");
        assertEquals(5.0, speed("minecraft:wool", BuiltinItems.SHEARS),
                "the wool family pays exactly 5.0 — the pre-fix table answered 6.0");
        assertEquals(1.0, speed("minecraft:tall_grass", BuiltinItems.SHEARS),
                "the plants stay the bare 1.0 (the speed band, unlike the "
                        + "shears WEAR band, never carried the tallgrass)");
        assertEquals(1.0, speed("minecraft:stone", BuiltinItems.SHEARS));
        assertEquals(1.0, speed("minecraft:dirt", BuiltinItems.SHEARS));
    }

    @Test
    void thePickaxeEffectiveSetPaysTheTierBeyondTheRockBranch() {
        // PickaxeItem.getMiningSpeed lines 24-28: the material branch pays
        // the tier on STONE/IRON/ANVIL, and the explicit EFFECTIVE_BLOCKS
        // set pays it on the members whose material sits outside the branch
        // — the ICE (Material.ICE) and the rail pair (DECORATION). The
        // pre-fix material-string match answered 1.0 on all three.
        assertEquals(8.0, speed("minecraft:ice", BuiltinItems.DIAMOND_PICKAXE),
                "the ice rides the effective set (PICKAXE_EFFECTIVE_ON's ICE member)");
        assertEquals(12.0, speed("minecraft:ice", BuiltinItems.GOLDEN_PICKAXE));
        assertEquals(12.0, speed("minecraft:rail", BuiltinItems.GOLDEN_PICKAXE),
                "the rail rides the effective set — the pre-fix \"stone\" "
                        + "material string answered 1.0");
        assertEquals(2.0, speed("minecraft:rail_ew", BuiltinItems.WOODEN_PICKAXE));
        assertEquals(1.0, speed("minecraft:dirt", BuiltinItems.DIAMOND_PICKAXE));
        assertEquals(1.0, speed("minecraft:ice", BuiltinItems.DIAMOND_SHOVEL),
                "no other class accelerates the ice");
        assertEquals(8.0, speed("minecraft:packed_ice", BuiltinItems.DIAMOND_PICKAXE),
                "the packed ice keeps the tier (the set member the \"rock\" "
                        + "branch already answered)");
    }

    @Test
    void theAxePaysThePlantBranchAndItsOwnSet() {
        // AxeItem.getMiningSpeed lines 21-24: the tier on the WOOD/PLANT/
        // REPLACEABLE_PLANT material branch — an iron axe digs the tall
        // grass at the FULL 6.0 — plus the explicit EFFECTIVE_BLOCKS set
        // (the pumpkin trio, the ladder). The pre-fix string match never
        // carried the plant family at all: the whole flora answered 1.0.
        assertEquals(6.0, speed("minecraft:tall_grass", BuiltinItems.IRON_AXE),
                "REPLACEABLE_PLANT rides the material branch at the tier");
        assertEquals(6.0, speed("minecraft:vine", BuiltinItems.IRON_AXE));
        assertEquals(6.0, speed("minecraft:dead_bush", BuiltinItems.IRON_AXE));
        assertEquals(6.0, speed("minecraft:sugar_cane", BuiltinItems.IRON_AXE),
                "the PLANT material rides the branch");
        assertEquals(6.0, speed("minecraft:dandelion", BuiltinItems.IRON_AXE));
        assertEquals(6.0, speed("minecraft:bookshelf", BuiltinItems.IRON_AXE),
                "the re-pinned WOOD material pays the branch — the pre-fix "
                        + "null material answered 1.0");
        assertEquals(8.0, speed("minecraft:pumpkin", BuiltinItems.DIAMOND_AXE),
                "the pumpkin rides the EFFECTIVE_BLOCKS set");
        assertEquals(8.0, speed("minecraft:melon_block", BuiltinItems.DIAMOND_AXE));
        assertEquals(2.0, speed("minecraft:ladder", BuiltinItems.WOODEN_AXE),
                "the ladder rides the set (its DECORATION material never "
                        + "matched a branch)");
        assertEquals(1.0, speed("minecraft:cactus", BuiltinItems.DIAMOND_AXE),
                "the CACTUS material sits outside the branch (line 30)");
        assertEquals(1.0, speed("minecraft:stone", BuiltinItems.DIAMOND_AXE));
        assertEquals(1.0, speed("minecraft:oak_leaves", BuiltinItems.DIAMOND_AXE),
                "LEAVES is not in the axe's branch — the reference's own list");
    }

    @Test
    void theShovelSetStaysWhole() {
        // ShovelItem lines 13-15: the pure effective set — clay, dirt,
        // farmland, grass, gravel, mycelium, sand, snow, snow_layer,
        // soul_sand. The engine's five dirt-family strings cover the ten
        // members exactly; the seat pins the family whole.
        assertEquals(12.0, speed("minecraft:snow_layer", BuiltinItems.GOLDEN_SHOVEL));
        assertEquals(12.0, speed("minecraft:snow", BuiltinItems.GOLDEN_SHOVEL));
        assertEquals(12.0, speed("minecraft:gravel", BuiltinItems.GOLDEN_SHOVEL));
        assertEquals(12.0, speed("minecraft:farmland", BuiltinItems.GOLDEN_SHOVEL));
        assertEquals(12.0, speed("minecraft:soul_sand", BuiltinItems.GOLDEN_SHOVEL));
        assertEquals(8.0, speed("minecraft:mycelium", BuiltinItems.DIAMOND_SHOVEL));
        assertEquals(8.0, speed("minecraft:clay", BuiltinItems.DIAMOND_SHOVEL));
        assertEquals(1.0, speed("minecraft:stone", BuiltinItems.DIAMOND_SHOVEL));
        assertEquals(1.0, speed("minecraft:cobweb", BuiltinItems.DIAMOND_SHOVEL),
                "the web is the sword-and-shears pair's own — never the shovel's");
    }

    @Test
    void theHoeNeverAccelerates() {
        // HoeItem extends Item with no getMiningSpeed override: the bare
        // 1.0 against everything — the reference's own default row.
        assertEquals(1.0, speed("minecraft:stone", BuiltinItems.DIAMOND_HOE));
        assertEquals(1.0, speed("minecraft:dirt", BuiltinItems.DIAMOND_HOE));
        assertEquals(1.0, speed("minecraft:oak_leaves", BuiltinItems.DIAMOND_HOE));
        assertEquals(1.0, speed("minecraft:cobweb", BuiltinItems.DIAMOND_HOE));
        assertEquals(1.0, speed("minecraft:wool", BuiltinItems.DIAMOND_HOE));
    }

    @Test
    void theWebHarvestGatesOnTheSwordAndTheShearsTogether() {
        // The COBWEB material requires the tool (Material lines 36-41), and
        // the web's own canMineBlock rows are the SWORD (SwordItem line 84)
        // and the SHEARS (ShearsItem lines 35-37): every other hand breaks
        // at the /100 divisor and harvests NOTHING. The pre-fix row was
        // tool-free — a bare hand took the string at the /30 divisor.
        BlockBehavior web = behavior("minecraft:cobweb");
        assertTrue(BlockBehaviorTable.canHarvest(web, BuiltinItems.WOODEN_SWORD));
        assertTrue(BlockBehaviorTable.canHarvest(web, BuiltinItems.SHEARS));
        assertFalse(BlockBehaviorTable.canHarvest(web, BuiltinItems.DIAMOND_PICKAXE),
                "the pickaxe's own canMineBlock never carried the web");
        assertFalse(BlockBehaviorTable.canHarvest(web, BuiltinItems.DIAMOND_SHOVEL));
        assertFalse(BlockBehaviorTable.canHarvest(web, BuiltinItems.DIAMOND_AXE));
        assertFalse(BlockBehaviorTable.canHarvest(web, null), "the bare hand harvests nothing");

        // The divisor split the same flag carries (Block.getMiningSpeed
        // line 490): 4.0 * 30 / 15 = 8 ticks under the sword, 4.0 * 100 / 1
        // = 400 under the hand or the pickaxe.
        assertEquals(8, MiningRules.breakTicks(4.0, 15.0, true),
                "the sword's web dig: the reference's own 0.4 seconds");
        assertEquals(400, MiningRules.breakTicks(4.0, 1.0, false),
                "the bare-hand web dig: the reference's own 20 seconds");
        assertEquals(400, MiningRules.breakTicks(4.0, 1.0,
                        BlockBehaviorTable.canHarvest(web, BuiltinItems.DIAMOND_PICKAXE)),
                "the pickaxe digs the web at the hand's own slow divisor");

        // The drops ride the same verdict: the string belongs to the
        // sword-and-shears pair alone.
        assertEquals(List.of("minecraft:string"),
                dropped(DROPS.dropsFor(block("minecraft:cobweb"), BuiltinItems.IRON_SWORD, false, 0)));
        assertEquals(List.of("minecraft:string"),
                dropped(DROPS.dropsFor(block("minecraft:cobweb"), BuiltinItems.SHEARS, false, 0)));
        assertTrue(DROPS.dropsFor(block("minecraft:cobweb"), BuiltinItems.DIAMOND_PICKAXE, false, 0).isEmpty(),
                "the pickaxe harvests nothing from the web");
        assertTrue(DROPS.dropsFor(block("minecraft:cobweb"), null, false, 0).isEmpty(),
                "the bare hand harvests nothing from the web");
    }

    @Test
    void theSnowPairGatesOnTheShovelAlone() {
        // The SNOW material requires the tool (Material line 29) and the
        // ShovelItem.canMineBlock row is the snow pair's own gate (lines
        // 22-24): the bare hand breaks at the /100 divisor and harvests
        // nothing — the pre-fix rows left both hand-harvestable, so a bare
        // hand took the snow block's four snowballs.
        BlockBehavior snow = behavior("minecraft:snow");
        BlockBehavior snowLayer = behavior("minecraft:snow_layer");
        assertTrue(BlockBehaviorTable.canHarvest(snow, BuiltinItems.WOODEN_SHOVEL));
        assertTrue(BlockBehaviorTable.canHarvest(snowLayer, BuiltinItems.GOLDEN_SHOVEL));
        assertFalse(BlockBehaviorTable.canHarvest(snow, BuiltinItems.DIAMOND_PICKAXE));
        assertFalse(BlockBehaviorTable.canHarvest(snow, null));
        assertFalse(BlockBehaviorTable.canHarvest(snowLayer, null));

        // The divisor split: the layer's 0.1 hardness at the hand's own
        // /100 divisor is the reference's 10-tick dig; the gold shovel's
        // 12.0 tier at /30 completes the tick it starts.
        assertEquals(10, MiningRules.breakTicks(0.1, 1.0, false),
                "the bare-hand layer dig: 0.1 * 100 / 1");
        assertEquals(1, MiningRules.breakTicks(0.1, 12.0, true),
                "the gold shovel's layer dig completes the tick it starts "
                        + "(12.0 / 0.1 / 30 = 40 progress a tick)");

        // The snow block's four balls belong to the shovel alone; the
        // layer's own drop law is the player-dig completion's LAYERS + 1
        // (afterMinedByPlayer lines 102-106 — the engine's single-layer
        // state answers TWO).
        assertEquals(List.of("minecraft:snowball"),
                dropped(DROPS.dropsFor(block("minecraft:snow"), BuiltinItems.DIAMOND_SHOVEL, false, 0)));
        assertEquals(4, DROPS.dropsFor(block("minecraft:snow"), BuiltinItems.DIAMOND_SHOVEL, false, 0)
                .get(0).count(), "the shovel's four snowballs stay flat");
        assertTrue(DROPS.dropsFor(block("minecraft:snow"), null, false, 0).isEmpty(),
                "the bare hand yields nothing (the SNOW material's own gate)");
        assertEquals(List.of("minecraft:snowball"),
                dropped(DROPS.dropsFor(block("minecraft:snow_layer"), BuiltinItems.WOODEN_SHOVEL, false, 0)));
        assertEquals(2, DROPS.dropsFor(block("minecraft:snow_layer"), BuiltinItems.WOODEN_SHOVEL, false, 0)
                .get(0).count(), "the single layer's LAYERS + 1 snowballs");
        assertTrue(DROPS.dropsFor(block("minecraft:snow_layer"), null, false, 0).isEmpty());
        // The wash-out keeps the reference's own split (FlowingLiquidBlock's
        // generic walk carries no player, no gate): the water stream pops
        // the snow block's four balls exactly.
        assertEquals(4, DROPS.washedDrops(block("minecraft:snow")).get(0).count(),
                "the wash-out drops the generic walk's four balls");
        assertTrue(DROPS.washedDrops(block("minecraft:snow_layer")).isEmpty(),
                "the layer's generic walk stays empty (getBaseDropCount 0)");
    }

    @Test
    void packedIceDigsAtTheHarvestableDivisorForEveryHand() {
        // The PACKED_ICE material carries NO setRequiresTool (Material line
        // 27): every hand digs at the /30 divisor — the pre-fix row's
        // requires-tool had every non-pickaxe at /100. The pickaxe keeps
        // the effective set's tier and the block never drops (either way).
        BlockBehavior packedIce = behavior("minecraft:packed_ice");
        assertTrue(BlockBehaviorTable.canHarvest(packedIce, null),
                "the PACKED_ICE material requires no tool");
        assertTrue(BlockBehaviorTable.canHarvest(packedIce, BuiltinItems.DIAMOND_PICKAXE));
        assertTrue(BlockBehaviorTable.canHarvest(packedIce, BuiltinItems.DIAMOND_SWORD));
        assertEquals(15, MiningRules.breakTicks(0.5, 1.0, true),
                "the bare-hand packed ice: 0.5 * 30 / 1 — the reference's own 0.75 seconds");
        assertEquals(2, MiningRules.breakTicks(0.5, 8.0, true),
                "the diamond pickaxe's packed ice: 2 ticks, the reference's own 0.1 seconds");
        assertEquals(8.0, speed("minecraft:packed_ice", BuiltinItems.DIAMOND_PICKAXE));
        assertTrue(DROPS.dropsFor(block("minecraft:packed_ice"), BuiltinItems.DIAMOND_PICKAXE, false, 0)
                .isEmpty(), "the block never drops without silk (getBaseDropCount 0)");
        assertTrue(DROPS.dropsFor(block("minecraft:packed_ice"), null, false, 0).isEmpty());
    }

    @Test
    void theStatusEffectMultipliersRideTheReferenceOrder() {
        // PlayerEntity.getMiningSpeed lines 663-695: Haste multiplies by
        // 1 + 0.2*(amplifier+1) and mining fatigue by the amplifier's own
        // band, both BETWEEN the Efficiency add and the environment
        // divisors. The pre-fix five-arg shape carried neither.
        assertEquals(2.4, MiningRules.miningSpeed(2.0, 0, false, false, true, 0, -1),
                "Haste I: 2.0 * 1.2");
        assertEquals(2.8, MiningRules.miningSpeed(2.0, 0, false, false, true, 1, -1),
                "Haste II: 2.0 * 1.4");
        assertEquals(0.6, MiningRules.miningSpeed(2.0, 0, false, false, true, -1, 0),
                "fatigue I: 2.0 * 0.3");
        assertEquals(0.18, MiningRules.miningSpeed(2.0, 0, false, false, true, -1, 1),
                "fatigue II: 2.0 * 0.09");
        assertEquals(0.0054, MiningRules.miningSpeed(2.0, 0, false, false, true, -1, 2),
                "fatigue III: 2.0 * 0.0027");
        assertEquals(2.0 * 8.1e-4, MiningRules.miningSpeed(2.0, 0, false, false, true, -1, 3),
                "fatigue IV: 2.0 * 8.1e-4");
        assertEquals(2.0 * 8.1e-4, MiningRules.miningSpeed(2.0, 0, false, false, true, -1, 7),
                "the amplifier-3-and-up arm folds every deeper row (line 691)");
        assertEquals(2.8 * 0.3, MiningRules.miningSpeed(2.0, 0, false, false, true, 1, 0),
                "both effects multiply in the reference's own order");
        // The no-effect walk is the five-arg overload's own answer.
        assertEquals(MiningRules.miningSpeed(2.0, 0, false, false, true),
                MiningRules.miningSpeed(2.0, 0, false, false, true, -1, -1));
        // The effects sit AFTER the Efficiency add: the bonus joins the
        // base first, then Haste scales the sum (lines 655-661 -> 663).
        assertEquals((2.0 + 5.0) * 1.2,
                MiningRules.miningSpeed(2.0, 2, false, false, true, 0, -1),
                "Efficiency II's +5 joins before Haste I's 1.2");
        // The bare fist never gains from Efficiency (the f > 1.0 gate),
        // but Haste still scales it (the gate reads the BASE alone).
        assertEquals(1.2, MiningRules.miningSpeed(1.0, 5, false, false, true, 0, -1),
                "an enchanted bare fist stays 1.0 — then Haste scales it");
        // The environment divisors close the walk, after the effects.
        assertEquals(0.48, MiningRules.miningSpeed(2.0, 0, true, false, true, 0, -1),
                "Haste I in water without Aqua Affinity: (2.0 * 1.2) / 5");
    }

    @Test
    void thePlantFamilyPaysTheAxeTierAndTheSwordBandExactly() {
        // The reference's plant laws differ per item class over the SAME
        // block: an iron axe digs the tall grass at the full 6.0 tier
        // (AxeItem's REPLACEABLE_PLANT branch), the sword answers 1.5
        // (SwordItem's band), the shears answer 1.0 (ShearsItem's else
        // arm) while STILL wearing one unit (ShearsItem.mineBlock's
        // TALLGRASS member — the tool-wear audit's own band), and the
        // pickaxe and shovel answer 1.0. The pre-fix table answered 1.0
        // for every class (the "plants" string sat outside every map).
        assertEquals(6.0, speed("minecraft:tall_grass", BuiltinItems.IRON_AXE));
        assertEquals(6.0, speed("minecraft:fern", BuiltinItems.IRON_AXE));
        assertEquals(1.5, speed("minecraft:tall_grass", BuiltinItems.DIAMOND_SWORD));
        assertEquals(1.0, speed("minecraft:tall_grass", BuiltinItems.SHEARS));
        assertEquals(1.0, speed("minecraft:tall_grass", BuiltinItems.DIAMOND_PICKAXE));
        assertEquals(1.0, speed("minecraft:tall_grass", BuiltinItems.DIAMOND_SHOVEL));
        assertTrue(BlockBehaviorTable.inShearsWearBand(block("minecraft:tall_grass")),
                "the shears still wear one unit — the wear band the speed band never rode");
        // The waterlily's own "plant" string (the family's second
        // spelling) rides the same laws.
        assertEquals(6.0, speed("minecraft:waterlily", BuiltinItems.IRON_AXE));
        assertEquals(1.5, speed("minecraft:waterlily", BuiltinItems.DIAMOND_SWORD));
    }

    private static List<String> dropped(List<net.zaminmc.torch.item.ItemStack> stacks) {
        return stacks.stream().map(stack -> stack.type().identifier().toString()).toList();
    }
}
