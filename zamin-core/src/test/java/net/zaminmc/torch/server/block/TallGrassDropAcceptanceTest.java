package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tall-grass family's exact drop law (TallPlantBlock lines 63-93 +
 * DeadBushBlock lines 39-51, the flora slices' shared shears arm) — the
 * regression seat for the by-hand self-drop bug: the pre-fix behavior rows
 * listed the plant item at chance 1.0, so every by-hand break yielded the
 * tallgrass item (wire item 31 damage 0, the "Shrub" a real client shows)
 * beside the 1/8 wheat-seed roll, and the fern row even referenced the
 * never-registered {@code minecraft:fern} item (an IllegalStateException
 * on every fern break). The law below is the reference's own: the bare
 * hand rolls ONLY the 1/8 seeds (the dead bush nothing at all); shears
 * take the family's item with the variant damage (grass 1, fern 2, the
 * dead bush's own 0) and never roll the seeds.
 */
class TallGrassDropAcceptanceTest {

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(Identifier.parse(value)).orElseThrow();
    }

    /** @return the identifier every dropped stack of {@code drops} carries. */
    private static List<String> droppedItems(List<ItemStack> drops) {
        return drops.stream().map(stack -> stack.type().identifier().toString()).toList();
    }

    @Test
    void ordinaryGrassBlockDropsDirtAndNothingElse() {
        DropService drops = new DropService(new Random(20261028L));
        // The grass BLOCK (legacy 2, GrassBlock.getDropItem lines 72-74 ->
        // the dirt item): dirt only, every break, never seeds or plants.
        for (int i = 0; i < 64; i++) {
            List<ItemStack> yield = drops.dropsFor(block("minecraft:grass_block"), null, false, 0);
            assertEquals(List.of("minecraft:dirt"), droppedItems(yield), "break " + i);
        }
    }

    @Test
    void tallGrassByHandYieldsOnlyTheOneInEightSeedRoll() {
        // The wide sample: over 4096 breaks the seed appears ~12.5% of the
        // time — and NEVER once the plant item (the pre-fix guaranteed row).
        // A fixed Random makes the whole band deterministic.
        DropService wide = new DropService(new Random(88L));
        int seeded = 0;
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> yield = wide.dropsFor(
                    block("minecraft:tall_grass"), null, false, 0);
            assertTrue(droppedItems(yield).stream()
                            .allMatch(item -> item.equals("minecraft:wheat_seeds")),
                    "only wheat seeds ever drop: " + droppedItems(yield));
            assertTrue(yield.size() <= 1 && (yield.isEmpty()
                            || yield.get(0).count() == 1),
                    "fortune 0 drops at most one single seed: " + yield);
            if (!yield.isEmpty()) {
                seeded++;
            }
        }
        assertEquals(512, seeded, 4096 / 16,
                "the seed chance stays at the reference's 1/8");
    }

    @Test
    void tallGrassByHandNeverYieldsThePlantItemOverEverySeed() {
        // The incorrect-mapping guard: across a fully sampled Random, the
        // tall-grass item (the wire's "Shrub") and the dead-bush item NEVER
        // appear in a by-hand tall-grass drop.
        DropService wide = new DropService(new Random(0xF1F1L));
        for (int i = 0; i < 8192; i++) {
            List<ItemStack> yield = wide.dropsFor(
                    block("minecraft:tall_grass"), null, false, 0);
            assertTrue(droppedItems(yield).stream()
                            .noneMatch(item -> item.equals("minecraft:tall_grass")
                                    || item.equals("minecraft:dead_bush")),
                    "the plant item leaked into the by-hand walk: " + droppedItems(yield));
        }
    }

    @Test
    void fernByHandYieldsOnlyTheSeedRollAndNeverThrows() {
        // The pre-fix fern row referenced the unregistered minecraft:fern
        // item — any fern break threw the DropService's ISE; the law now
        // rides the family walk: seeds at 1/8, nothing else, no throw.
        DropService ferns = new DropService(new Random(0xFE2A5L));
        for (int i = 0; i < 8192; i++) {
            List<ItemStack> yield = ferns.dropsFor(block("minecraft:fern"), null, false, 0);
            assertTrue(droppedItems(yield).stream()
                            .allMatch(item -> item.equals("minecraft:wheat_seeds")),
                    "the fern by hand rolls only the seed walk: " + droppedItems(yield));
            assertTrue(droppedItems(yield).stream()
                            .noneMatch(item -> item.equals("minecraft:fern")),
                    "the never-registered fern item must not drop");
        }
    }

    @Test
    void theShearsArmDropsTheFamilyItemWithTheVariantDamage() {
        DropService drops = new DropService(new Random(31L));
        // TallPlantBlock.afterMinedByPlayer lines 73-80: the tallgrass item
        // with the TYPE damage — the grass's 1 — and no seed roll, exactly one.
        for (int i = 0; i < 16; i++) {
            List<ItemStack> grass = drops.dropsFor(
                    block("minecraft:tall_grass"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, grass.size());
            assertEquals("minecraft:tall_grass", grass.get(0).type().identifier().toString());
            assertEquals(1, grass.get(0).damage(), "the grass variant rides the damage field");
            assertEquals(1, grass.get(0).count());
        }
        // The fern's shears arm: the same tallgrass item carrying TYPE 2.
        for (int i = 0; i < 16; i++) {
            List<ItemStack> fern = drops.dropsFor(
                    block("minecraft:fern"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, fern.size());
            assertEquals("minecraft:tall_grass", fern.get(0).type().identifier().toString());
            assertEquals(2, fern.get(0).damage(), "the fern variant rides the damage field");
        }
        // DeadBushBlock.afterMinedByPlayer lines 44-51: the dead bush's own
        // item at damage 0 — and by hand nothing at all.
        for (int i = 0; i < 16; i++) {
            List<ItemStack> sheared = drops.dropsFor(
                    block("minecraft:dead_bush"), BuiltinItems.SHEARS, false, 0);
            assertEquals(1, sheared.size());
            assertEquals("minecraft:dead_bush", sheared.get(0).type().identifier().toString());
            assertEquals(0, sheared.get(0).damage());
        }
        for (int i = 0; i < 256; i++) {
            assertTrue(drops.dropsFor(block("minecraft:dead_bush"), null, false, 0).isEmpty(),
                    "the dead bush by hand yields nothing");
        }
    }

    @Test
    void fortuneWidensTheSeedIterationsAndNeverMintsThePlantItem() {
        // TallPlantBlock.getDropCount lines 68-70: 1 + nextInt(fortune * 2 +
        // 1) iterations, each rolling its own 1/8 seed — bounded by the walk,
        // never the plant item.
        DropService rich = new DropService(new Random(77L));
        int maxSeen = 0;
        for (int i = 0; i < 512; i++) {
            List<ItemStack> yield = rich.dropsFor(
                    block("minecraft:tall_grass"), null, false, 3);
            assertTrue(droppedItems(yield).stream()
                            .allMatch(item -> item.equals("minecraft:wheat_seeds")),
                    "fortune never changes the item: " + droppedItems(yield));
            if (!yield.isEmpty()) {
                maxSeen = Math.max(maxSeen, yield.get(0).count());
            }
        }
        assertTrue(maxSeen >= 1, "some Fortune III break drops at least one seed");
        assertTrue(maxSeen <= 7, "1 + nextInt(7) iterations bounds the count");
    }

    @Test
    void theWashOutPathFollowsTheSameNoToolLaw() {
        // The fluid spread's no-tool walk (FlowingLiquidBlock.spreadTo's
        // displaced-block arm): seeds at 1/8, never the plant item, and the
        // dead bush washes away with nothing.
        DropService drops = new DropService(new Random(0xA7A7L));
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> washed = drops.washedDrops(block("minecraft:tall_grass"));
            assertTrue(droppedItems(washed).stream()
                            .allMatch(item -> item.equals("minecraft:wheat_seeds")),
                    "the wash rolls only the seed walk: " + droppedItems(washed));
            assertTrue(drops.washedDrops(block("minecraft:dead_bush")).isEmpty(),
                    "the washed dead bush yields nothing");
        }
    }

    @Test
    void theNeighboringFloraKeepTheirOwnDrops() {
        // The family fix must not bleed into the neighbors: the flowers keep
        // their self-drops, the double grass keeps its 1/8 seed arm, and the
        // table rows still answer the data read.
        DropService drops = new DropService(new Random(11L));
        for (int i = 0; i < 32; i++) {
            List<ItemStack> flower = drops.dropsFor(
                    block("minecraft:dandelion"), null, false, 0);
            assertEquals(List.of("minecraft:dandelion"), droppedItems(flower));
        }
        BlockBehavior tall = BlockBehaviorTable.of(
                Identifier.parse("minecraft:tall_grass")).orElseThrow();
        assertEquals(0.0, tall.hardness());
        assertEquals(1, tall.drops().size(), "the no-tool table row carries the seed roll");
        assertEquals("minecraft:wheat_seeds", tall.drops().get(0).item().toString());
        assertEquals(0.125, tall.drops().get(0).chance());
        assertFalse(tall.requiresTool());
        // The behavior data read (§27.3) carries the same law.
        BlockBehavior fern = BlockBehaviorTable.of(
                Identifier.parse("minecraft:fern")).orElseThrow();
        assertEquals(1, fern.drops().size(), "the fern row lost its invalid self-drop");
        assertEquals("minecraft:wheat_seeds", fern.drops().get(0).item().toString());
    }

    @Test
    void repeatedBreaksNeverDuplicateTheDropWalk() {
        // The purity guard: the same (block, tool, fortune) walk rolls fresh
        // per invocation and never accumulates — one break, one bounded walk.
        DropService drops = new DropService(new Random(0xD00DL));
        int totalSeeds = 0;
        for (int i = 0; i < 256; i++) {
            List<ItemStack> yield = drops.dropsFor(
                    block("minecraft:tall_grass"), null, false, 0);
            assertTrue(yield.size() <= 1, "a single break never duplicates the walk");
            totalSeeds += yield.isEmpty() ? 0 : yield.get(0).count();
        }
        assertTrue(totalSeeds > 0 && totalSeeds <= 256,
                "the total stays inside the per-break bound (no accumulation)");
    }
}
