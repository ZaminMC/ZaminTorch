package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drop-band parity law (the follow-up audit to the tall-grass fix — the
 * same class of bug: a vanilla count band or item gate folded into a flat
 * table row, or a walk shape the reference never had). Every law below is
 * the reference's own, verified against reference/1.8.8:
 *
 * <ul>
 * <li>GlowstoneBlock lines 15-29: clamp(2 + nextInt(3) + nextInt(fortune +
 *     1), 1, 4) — the pre-fix three independent 1/3 rows dropped NOTHING
 *     ~30% of the time and up to NINE dust ~3.7% of the time.</li>
 * <li>GravelBlock.getDropItem: flint XOR gravel — the pre-fix walk dropped
 *     BOTH on a successful flint roll and even multiplied the flint count
 *     with Fortune (vanilla widens only the chance).</li>
 * <li>SeaLanternBlock lines 20-28 + the silk gate: the 2 + nextInt(2) band
 *     (never a flat 2) and silk takes the lantern itself.</li>
 * <li>NetherWartBlock.dropItems lines 51-63: age 3's 2 + nextInt(3) (+ the
 *     fortune bonus rolled only when fortune &gt; 0); the younger stages
 *     stay exactly one, fortune-free.</li>
 * <li>WheatBlock.dropItems lines 91-105 + the CarrotsBlock/PotatoesBlock
 *     overrides: one item at every age, the 3 + fortune seed-bonus walk at
 *     age 7 alone (each nextInt(15) &lt;= 7), the potatoes' 1/50 poisonous
 *     roll at age 7.</li>
 * <li>OreBlock.getDropCount lines 45-56: the fortune multiplier belongs to
 *     the ore family ALONE — the bookshelf's three books, clay's four
 *     balls and the double slabs' pair answer FLAT counts at every
 *     fortune level (the pre-fix walk multiplied every foreign row).</li>
 * <li>The plain ice (IceBlock) and the bookshelf (BookshelfBlock): rows
 *     the pre-fix table never carried — ice melts to nothing by hand and
 *     takes itself under silk; the shelf answers exactly three books.</li>
 * </ul>
 */
class DropBandParityAcceptanceTest {

    private static net.zaminmc.torch.block.BlockType block(String value) {
        return BuiltinBlocks.registerAll(new BlockRegistryBuilder())
                .freeze().lookup(net.zaminmc.torch.util.Identifier.parse(value)).orElseThrow();
    }

    /** @return the identifier every dropped stack of {@code drops} carries. */
    private static List<String> droppedItems(List<ItemStack> drops) {
        return drops.stream().map(stack -> stack.type().identifier().toString()).toList();
    }

    private static final String FLINT = "minecraft:flint";
    private static final String GRAVEL = "minecraft:gravel";

    @Test
    void glowstoneAlwaysAnswersInsideTheOneToFourBand() {
        // The reference band: clamp(2 + nextInt(3) + nextInt(fortune + 1), 1, 4)
        // — the pre-fix walk dropped NOTHING ~30% of the time (all three
        // independent rows failed) and NINE dust ~3.7% of the time (all
        // three hit).
        DropService fortune0 = new DropService(new Random(0x610L));
        int[] seen = new int[8];
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> yield = fortune0.dropsFor(block("minecraft:glowstone"), null, false, 0);
            assertEquals(1, yield.size(), "exactly one dust stack every break");
            assertEquals("minecraft:glowstone_dust", yield.get(0).type().identifier().toString());
            int count = yield.get(0).count();
            assertTrue(count >= 1 && count <= 4, "the count stays in the vanilla band: " + count);
            seen[count]++;
        }
        assertTrue(seen[2] > 0 && seen[3] > 0 && seen[4] > 0,
                "the 2/3/4 band all appear: " + java.util.Arrays.toString(seen));
        // Fortune never lifts past the clamp.
        DropService rich = new DropService(new Random(0x611L));
        for (int i = 0; i < 2048; i++) {
            List<ItemStack> yield = rich.dropsFor(block("minecraft:glowstone"), null, false, 3);
            assertTrue(yield.get(0).count() >= 1 && yield.get(0).count() <= 4,
                    "fortune III still clamps at 4");
        }
    }

    @Test
    void gravelAnswersFlintXorGravelNeverBothAndNeverMultiplied() {
        // The reference XOR: flint at nextInt(10 - fortune*3) == 0, ELSE the
        // block — exactly one stack, count one. The pre-fix walk dropped
        // BOTH stacks on a flint success and multiplied the flint count.
        DropService plain = new DropService(new Random(0x61AL));
        int flints = 0;
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> yield = plain.dropsFor(block("minecraft:gravel"), null, false, 0);
            assertEquals(1, yield.size(), "exactly ONE stack — the XOR law");
            assertEquals(1, yield.get(0).count(), "the count stays one");
            String item = yield.get(0).type().identifier().toString();
            assertTrue(item.equals(FLINT) || item.equals(GRAVEL), "only flint or gravel");
            flints += item.equals(FLINT) ? 1 : 0;
        }
        assertEquals(512, flints, 4096 / 16, "the 10% flint chance holds");
        // Fortune III: the ladder's 10 - 3*3 = 1 → every roll a flint, and
        // the count STILL one (the pre-fix walk multiplied it).
        DropService rich = new DropService(new Random(0x61BL));
        for (int i = 0; i < 256; i++) {
            List<ItemStack> yield = rich.dropsFor(block("minecraft:gravel"), null, false, 3);
            assertEquals(1, yield.size());
            assertEquals(1, yield.get(0).count(), "the flint count never multiplies");
            assertEquals(FLINT, yield.get(0).type().identifier().toString(),
                    "fortune III gravel is always flint");
        }
    }

    @Test
    void theSeaLanternWalksTheTwoToThreeBandAndSilkTakesTheLantern() {
        // SeaLanternBlock lines 20-28: clamp(2 + nextInt(2) + nextInt(f+1), 1, 5).
        DropService lanterns = new DropService(new Random(0x5EA1L));
        boolean sawTwo = false;
        boolean sawThree = false;
        for (int i = 0; i < 2048; i++) {
            List<ItemStack> yield = lanterns.dropsFor(block("minecraft:sea_lantern"), null, false, 0);
            assertEquals(1, yield.size());
            assertEquals("minecraft:prismarine_crystals",
                    yield.get(0).type().identifier().toString());
            int count = yield.get(0).count();
            assertTrue(count == 2 || count == 3, "the fortune-0 band is 2-3: " + count);
            sawTwo |= count == 2;
            sawThree |= count == 3;
        }
        assertTrue(sawTwo && sawThree, "both band members appear");
        // The silk gate (SeaLanternBlock line 41): the lantern itself.
        List<ItemStack> silk = lanterns.dropsFor(
                block("minecraft:sea_lantern"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:sea_lantern", silk.get(0).type().identifier().toString());
    }

    @Test
    void theMatureNetherWartWalksTheTwoToFourBandAndTheYoungStayOne() {
        // NetherWartBlock.dropItems: age 3 → 2 + nextInt(3) (+ nextInt(f+1)
        // only when fortune > 0); the younger stages exactly one wart,
        // never fortune-touched (the pre-fix flat-2 row and the walk's old
        // foreign-row multiplier both wrong).
        DropService warts = new DropService(new Random(0xDEFAL));
        for (int stage = 0; stage <= 2; stage++) {
            for (int i = 0; i < 64; i++) {
                List<ItemStack> young = warts.dropsFor(
                        block("minecraft:nether_wart_stage" + stage), null, false, 3);
                assertEquals(1, young.size(), "the young wart answers exactly one");
                assertEquals(1, young.get(0).count(), "fortune never widens the young stages");
            }
        }
        int[] seen = new int[12];
        for (int i = 0; i < 2048; i++) {
            List<ItemStack> mature = warts.dropsFor(
                    block("minecraft:nether_wart_stage3"), null, false, 0);
            assertEquals(1, mature.size());
            int count = mature.get(0).count();
            assertTrue(count >= 2 && count <= 4, "the mature band is 2-4: " + count);
            seen[count]++;
        }
        assertTrue(seen[2] > 0 && seen[3] > 0 && seen[4] > 0, "the whole band appears");
        // Fortune widens past 4 — the reference adds nextInt(fortune + 1).
        int maxSeen = 0;
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> rich = warts.dropsFor(
                    block("minecraft:nether_wart_stage3"), null, false, 3);
            maxSeen = Math.max(maxSeen, rich.get(0).count());
        }
        assertTrue(maxSeen >= 5, "fortune III widens the band past 4: " + maxSeen);
    }

    @Test
    void theMatureWheatAnswersTheWheatPlusTheSeedBonusWalk() {
        // WheatBlock.dropItems: exactly ONE wheat (the standard walk's
        // fortune dropped to 0) plus the 3 + fortune bonus rolls, each
        // nextInt(15) <= 7 → one seed. The pre-fix row folded the bonus to
        // a flat one seed.
        DropService wheat = new DropService(new Random(0x5EEDL));
        int[] bonusSeen = new int[8];
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> yield = wheat.dropsFor(
                    block("minecraft:wheat_stage7"), null, false, 0);
            assertEquals(1, yield.get(0).count(), "exactly one wheat, first");
            assertEquals("minecraft:wheat", yield.get(0).type().identifier().toString());
            int bonus = yield.size() == 2 ? yield.get(1).count() : 0;
            assertTrue(bonus >= 0 && bonus <= 3,
                    "the fortune-0 bonus stays 0-3: " + bonus);
            if (yield.size() == 2) {
                assertEquals("minecraft:wheat_seeds", yield.get(1).type().identifier().toString());
            }
            bonusSeen[bonus]++;
        }
        assertTrue(bonusSeen[0] > 0 && bonusSeen[3] > 0,
                "the bonus band's edges appear: " + java.util.Arrays.toString(bonusSeen));
        // The young stages stay the table's exact-one seed rows.
        for (int stage = 0; stage <= 6; stage++) {
            List<ItemStack> young = wheat.dropsFor(
                    block("minecraft:wheat_stage" + stage), null, false, 2);
            assertEquals(1, young.size());
            assertEquals("minecraft:wheat_seeds", young.get(0).type().identifier().toString());
            assertEquals(1, young.get(0).count(), "the young stage answers exactly one seed");
        }
    }

    @Test
    void theMatureCarrotAndPotatoMergeTheBonusIntoTheSingleCropStack() {
        // The carrots' and potatoes' seed IS the crop item (CarrotsBlock /
        // PotatoesBlock): one + the bonus merges into a single stack of
        // 1..(4 + fortune). The pre-fix rows folded each to a flat two.
        DropService crops = new DropService(new Random(0xCA6L));
        int[] carrotSeen = new int[10];
        for (int i = 0; i < 4096; i++) {
            List<ItemStack> carrots = crops.dropsFor(
                    block("minecraft:carrots_stage7"), null, false, 0);
            assertEquals(1, carrots.size(), "the carrot's crop and seed merge into one stack");
            assertEquals("minecraft:carrot", carrots.get(0).type().identifier().toString());
            int total = carrots.get(0).count();
            assertTrue(total >= 1 && total <= 4, "the fortune-0 total stays 1-4: " + total);
            carrotSeen[total]++;
        }
        assertTrue(carrotSeen[1] > 0 && carrotSeen[4] > 0,
                "the carrot band's edges appear: " + java.util.Arrays.toString(carrotSeen));
        for (int stage = 0; stage <= 6; stage++) {
            List<ItemStack> young = crops.dropsFor(
                    block("minecraft:carrots_stage" + stage), null, false, 3);
            assertEquals(1, young.size());
            assertEquals(1, young.get(0).count(), "the young carrot answers exactly one");
        }
        // The young potato rows keep their own exact-one law too.
        for (int stage = 0; stage <= 6; stage++) {
            List<ItemStack> young = crops.dropsFor(
                    block("minecraft:potatoes_stage" + stage), null, false, 3);
            assertEquals(1, young.size());
            assertEquals(1, young.get(0).count());
        }
    }

    @Test
    void theMaturePotatoRollsTheOneInFiftyPoisonousBonus() {
        // PotatoesBlock lines 25-31: at age 7 alone, nextInt(50) == 0 adds
        // ONE poisonous potato beside the crop (the item joined the
        // registry and the wire's legacy table with this slice).
        DropService potatoes = new DropService(new Random(0x0FFL));
        int poisoned = 0;
        for (int i = 0; i < 8192; i++) {
            List<ItemStack> yield = potatoes.dropsFor(
                    block("minecraft:potatoes_stage7"), null, false, 0);
            assertEquals("minecraft:potato", yield.get(0).type().identifier().toString());
            assertTrue(yield.size() == 1 || yield.size() == 2);
            if (yield.size() == 2) {
                assertEquals("minecraft:poisonous_potato",
                        yield.get(1).type().identifier().toString());
                assertEquals(1, yield.get(1).count());
                poisoned++;
            }
        }
        assertEquals(8192 / 50, poisoned, 8192 / 25,
                "the poison roll stays at the reference's 1/50");
        // The young stages never roll the poison.
        for (int i = 0; i < 2048; i++) {
            List<ItemStack> young = potatoes.dropsFor(
                    block("minecraft:potatoes_stage2"), null, false, 3);
            assertEquals(1, young.size(), "the young potato answers one stack only");
        }
    }

    @Test
    void theOreFamilyAloneTakesTheFortuneMultiplier() {
        // OreBlock.getDropCount belongs to the ore family alone: coal still
        // multiplies (1..4 at fortune III — behind its pickaxe harvest gate),
        // while the bookshelf's three books, clay's four balls and the
        // double slabs' pair answer FLAT counts at every fortune level.
        DropService ores = new DropService(new Random(0x0EAL));
        net.zaminmc.torch.item.ItemType pickaxe =
                net.zaminmc.torch.server.item.BuiltinItems.STONE_PICKAXE;
        int maxCoal = 0;
        for (int i = 0; i < 512; i++) {
            List<ItemStack> coal = ores.dropsFor(
                    block("minecraft:coal_ore"), pickaxe, false, 3);
            maxCoal = Math.max(maxCoal, coal.get(0).count());
        }
        assertTrue(maxCoal > 1, "fortune III coal multiplies past one: " + maxCoal);
        for (int i = 0; i < 128; i++) {
            assertEquals(3, ores.dropsFor(block("minecraft:bookshelf"), null, false, 3)
                    .get(0).count(), "the shelf's three books stay flat");
            assertEquals(4, ores.dropsFor(block("minecraft:clay"), null, false, 3)
                    .get(0).count(), "clay's four balls stay flat");
            assertEquals(2, ores.dropsFor(block("minecraft:double_stone_slab"), null, false, 3)
                    .get(0).count(), "the double slab's pair stays flat");
            assertEquals(4, ores.dropsFor(block("minecraft:snow"), null, false, 3)
                    .get(0).count(), "the snow block's four balls stay flat");
        }
    }

    @Test
    void theBookshelfAnswersThreeBooksAndSilkTakesTheShelf() {
        // BookshelfBlock: getDropItem the BOOK, getBaseDropCount 3 — exactly
        // three books by hand (the pre-fix table carried NO row at all); the
        // shelf itself under silk (the silk set already carried it).
        DropService shelves = new DropService(new Random(0xB00CL));
        for (int i = 0; i < 64; i++) {
            List<ItemStack> yield = shelves.dropsFor(block("minecraft:bookshelf"), null, false, 0);
            assertEquals(1, yield.size());
            assertEquals("minecraft:book", yield.get(0).type().identifier().toString());
            assertEquals(3, yield.get(0).count());
        }
        List<ItemStack> silk = shelves.dropsFor(block("minecraft:bookshelf"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:bookshelf", silk.get(0).type().identifier().toString());
    }

    @Test
    void thePlainIceMeltsToNothingByHandAndTakesItselfUnderSilk() {
        // IceBlock: getDropItem null / getBaseDropCount 0 — nothing by any
        // hand; hasSilkTouchDrops true — the cube itself under silk (the
        // pre-fix table carried NO row, so even silk answered nothing).
        DropService ice = new DropService(new Random(0x1CEL));
        for (int i = 0; i < 64; i++) {
            assertTrue(ice.dropsFor(block("minecraft:ice"), null, false, 0).isEmpty(),
                    "the bare hand melts the ice to nothing");
        }
        List<ItemStack> silk = ice.dropsFor(block("minecraft:ice"), null, true, 0);
        assertEquals(1, silk.size());
        assertEquals("minecraft:ice", silk.get(0).type().identifier().toString());
    }
}
