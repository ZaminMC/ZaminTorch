package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tree-growth + leaf-decay family (the 2026-10-21 audit's VIII2/VIII3 —
 * reference/1.8.8 SaplingBlock/AbstractLeavesBlock/AbstractLogBlock and the
 * net.minecraft.world.gen.feature tree family): the decay-bit band and its
 * wire nibbles, the drop normalization over the band, the decay poll's
 * distance BFS, the two onRemoved fans (the tree-chop trigger), the
 * sapling's light-gated roll and two-stage grow, every variant's feature
 * placement, the 2×2 cluster laws, the refused-placement restore, and the
 * import table's new rows — each pinned against the reference's own lines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreeGrowthFamilyTest {

    private static FrozenBlockRegistry registry;
    private EngineWorld world;
    private ItemEntityManager items;
    private DropService drops;
    /** The decay ticks' rolls (the fan's writes and the poll's verdicts ride the world's own state). */
    private Random random;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    // ------------------------------------------------------------------
    // The scene harness: a fresh flat world + the fan + the drop seams
    // ------------------------------------------------------------------

    private void scene() {
        world = new EngineWorld("tree-scene", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        // The light engine FIRST (the boot's own order — generated chunks
        // publish already lit): the light gates read the real stored light.
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new ChunkPosition(0, 0));
        world.getOrGenerate(new ChunkPosition(-1, 0));
        world.getOrGenerate(new ChunkPosition(0, -1));
        world.getOrGenerate(new ChunkPosition(-1, -1));
        world.addChangeListener(new LeafDecayBlocks.RemovalFan());
        items = new ItemEntityManager((x, y, z) -> false, new Random(1), 1000);
        drops = new DropService(new Random(1));
        random = new Random(1);
    }

    private BlockPosition at(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    /** The flat generator's surface height (the grass top). */
    private static final int SURFACE = 4;

    private void placeLeaf(int x, int y, int z, boolean persistent, boolean check) {
        int variant = 0; // the oak
        world.setBlock(at(x, y, z), LeafDecayBlocks.stateOf(variant, persistent, check));
    }

    // ------------------------------------------------------------------
    // The bands
    // ------------------------------------------------------------------

    @Test
    void theDecayBandRoundTrips() {
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            for (int bits = 0; bits < 4; bits++) {
                boolean persistent = (bits & 1) != 0;
                boolean check = (bits & 2) != 0;
                Identifier id = LeafDecayBlocks.stateId(variant, persistent, check);
                assertEquals(bits, LeafDecayBlocks.decayBitsOf(id),
                        WoodVariantBlocks.WOODS.get(variant) + " bits " + bits);
                assertTrue(LeafDecayBlocks.isLeafState(id), id + " is a leaf state");
                assertEquals(variant, WoodVariantBlocks.variantOfLeaf(id),
                        id + " resolves its wood");
                assertTrue(WoodVariantBlocks.isLeaves(id), id + " rides the family consult");
                int expectedMeta = WoodVariantBlocks.wireMetaOf("leaves", variant)
                        | (persistent ? 4 : 0) | (check ? 8 : 0);
                assertEquals(expectedMeta,
                        ((EngineBlockType) LeafDecayBlocks.stateOf(variant, persistent, check))
                                .legacyMetadata(),
                        id + " rides the wire nibble");
            }
        }
        assertFalse(LeafDecayBlocks.isLeafState(Identifier.parse("minecraft:oak_log")));
        assertFalse(LeafDecayBlocks.isLeafState(Identifier.parse("minecraft:oak_leaves_cheshire")));
        assertEquals(-1, LeafDecayBlocks.decayBitsOf(Identifier.parse("minecraft:stone")));
    }

    @Test
    void theReadyBandRoundTrips() {
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            Identifier ready = SaplingBlocks.stateId(variant, true);
            assertTrue(SaplingBlocks.isReady(ready));
            assertFalse(SaplingBlocks.isReady(SaplingBlocks.stateId(variant, false)));
            assertEquals(variant, WoodVariantBlocks.variantOfSapling(ready),
                    ready + " resolves its wood");
            assertTrue(WoodVariantBlocks.isSapling(ready), ready + " rides the family consult");
            assertEquals(WoodVariantBlocks.wireMetaOf("sapling", variant) | 8,
                    ((EngineBlockType) SaplingBlocks.stateOf(variant, true)).legacyMetadata(),
                    ready + " rides the wire nibble (variant | stage<<3)");
        }
    }

    @Test
    void theBandMembersBreakLikeTheirFreshForm() {
        // The jungle check state rolls the 1/40 band (not the plain 1/20),
        // the oak check state rolls the apple — the decay bits never reach
        // the drop consults (WoodVariantBlocks' own normalization).
        assertEquals(40, WoodVariantBlocks.saplingChanceOf(
                Identifier.parse("minecraft:jungle_leaves_check"), 0));
        assertEquals(20, WoodVariantBlocks.saplingChanceOf(
                Identifier.parse("minecraft:spruce_leaves_persistent"), 0));
        assertEquals(200, WoodVariantBlocks.appleChanceOf(
                Identifier.parse("minecraft:oak_leaves_persistent_check"), 0));
        assertEquals(0, WoodVariantBlocks.appleChanceOf(
                Identifier.parse("minecraft:birch_leaves_check"), 0));
    }

    // ------------------------------------------------------------------
    // The decay poll
    // ------------------------------------------------------------------

    @Test
    void anArmedLeafNearItsLogClearsTheFlag() {
        scene();
        // A trunk beside the leaf: the BFS finds distance 1 — the flag
        // clears, the leaf stays.
        world.setBlock(at(0, SURFACE + 1, 0), WoodVariantBlocks.logType(0));
        placeLeaf(1, SURFACE + 1, 0, false, true);
        LeafDecayBlocks.decayTick(world, at(1, SURFACE + 1, 0),
                world.getBlock(at(1, SURFACE + 1, 0)), random, items, drops);
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(1, SURFACE + 1, 0)).identifier(),
                "the reachable leaf clears its CHECK_DECAY flag");
    }

    @Test
    void anArmedLeafTooFarFromAnyLogDecays() {
        scene();
        // A lone armed leaf: no log in the 9³ box — the center stays -2
        // after the four-wave spread, breakLeaves runs (the drop walk + the
        // removal).
        int far = 40;
        placeLeaf(far, SURFACE + 5, 40, false, true);
        LeafDecayBlocks.decayTick(world, at(far, SURFACE + 5, 40),
                world.getBlock(at(far, SURFACE + 5, 40)), random, items, drops);
        assertEquals(world.airType(), world.getBlock(at(far, SURFACE + 5, 40)),
                "the unreachable leaf decays to air");
    }

    @Test
    void theDistanceFourBandIsExact() {
        scene();
        // A leaf at exactly four leaf-cells from the trunk: the fifth wave
        // never runs — the four-wave spread reaches distance 4 exactly.
        world.setBlock(at(0, SURFACE + 1, 0), WoodVariantBlocks.logType(0));
        placeLeaf(1, SURFACE + 1, 0, false, false);
        placeLeaf(2, SURFACE + 1, 0, false, false);
        placeLeaf(3, SURFACE + 1, 0, false, false);
        placeLeaf(4, SURFACE + 1, 0, false, true);
        LeafDecayBlocks.decayTick(world, at(4, SURFACE + 1, 0),
                world.getBlock(at(4, SURFACE + 1, 0)), random, items, drops);
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(4, SURFACE + 1, 0)).identifier(),
                "distance 4 through leaves is reachable (the wave count)");
        // Distance 5: the BFS never reaches — the leaf decays.
        placeLeaf(5, SURFACE + 1, 0, false, true);
        LeafDecayBlocks.decayTick(world, at(5, SURFACE + 1, 0),
                world.getBlock(at(5, SURFACE + 1, 0)), random, items, drops);
        assertEquals(world.airType(), world.getBlock(at(5, SURFACE + 1, 0)),
                "distance 5 through leaves is unreachable");
    }

    @Test
    void thePersistentAndUnarmedFormsNeverPoll() {
        scene();
        placeLeaf(40, SURFACE + 1, 40, true, true); // persistent + check: never decays
        LeafDecayBlocks.decayTick(world, at(40, SURFACE + 1, 40),
                world.getBlock(at(40, SURFACE + 1, 40)), random, items, drops);
        assertEquals(Identifier.parse("minecraft:oak_leaves_persistent_check"),
                world.getBlock(at(40, SURFACE + 1, 40)).identifier(),
                "the persistent form never polls");
        placeLeaf(42, SURFACE + 1, 42, false, false); // fresh: unarmed
        LeafDecayBlocks.decayTick(world, at(42, SURFACE + 1, 42),
                world.getBlock(at(42, SURFACE + 1, 42)), random, items, drops);
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(42, SURFACE + 1, 42)).identifier(),
                "the unarmed fresh form never polls");
    }

    // ------------------------------------------------------------------
    // The fans
    // ------------------------------------------------------------------

    @Test
    void aDepartingLeafArmsItsBox() {
        scene();
        placeLeaf(0, SURFACE + 1, 0, false, false);
        placeLeaf(1, SURFACE + 1, 0, false, false); // in the 3³ box
        placeLeaf(2, SURFACE + 1, 0, false, false); // OUTSIDE the box
        // The leaf departs (the break).
        world.setBlock(at(0, SURFACE + 1, 0), world.airType());
        assertEquals(Identifier.parse("minecraft:oak_leaves_check"),
                world.getBlock(at(1, SURFACE + 1, 0)).identifier(),
                "the 3³ neighbor armed");
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(2, SURFACE + 1, 0)).identifier(),
                "the cell beyond the box never armed");
    }

    @Test
    void aDepartingLogArmsTheTreeChopBox() {
        scene();
        // The trunk with its canopy: removing the trunk arms every leaf in
        // the 5³ box (AbstractLogBlock.onRemoved — the tree-chop trigger).
        world.setBlock(at(0, SURFACE + 1, 0), WoodVariantBlocks.logType(0));
        placeLeaf(0, SURFACE + 2, 0, false, false);
        placeLeaf(4, SURFACE + 1, 0, false, false); // the 5³ edge
        placeLeaf(5, SURFACE + 1, 0, false, false); // beyond
        world.setBlock(at(0, SURFACE + 1, 0), world.airType());
        assertEquals(Identifier.parse("minecraft:oak_leaves_check"),
                world.getBlock(at(0, SURFACE + 2, 0)).identifier(),
                "the canopy armed");
        assertEquals(Identifier.parse("minecraft:oak_leaves_check"),
                world.getBlock(at(4, SURFACE + 1, 0)).identifier(),
                "the 5³ edge armed");
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(5, SURFACE + 1, 0)).identifier(),
                "beyond the box never armed");
    }

    @Test
    void theFanNeverReFansItsOwnWrites() {
        scene();
        // A 3x3 leaf patch: the center departs, the eight neighbors arm —
        // and the fan's own writes (each an onRemoved of the fresh form)
        // never cascade further (the re-entrancy guard, the reference's
        // flag-4 semantics). The far probe stays fresh.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                placeLeaf(20 + dx, SURFACE + 1, 20 + dz, false, false);
            }
        }
        placeLeaf(24, SURFACE + 1, 24, false, false); // beyond every box
        world.setBlock(at(20, SURFACE + 1, 20), world.airType());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                assertEquals(Identifier.parse("minecraft:oak_leaves_check"),
                        world.getBlock(at(20 + dx, SURFACE + 1, 20 + dz)).identifier(),
                        "the box member armed");
            }
        }
        assertEquals(Identifier.parse("minecraft:oak_leaves"),
                world.getBlock(at(24, SURFACE + 1, 24)).identifier(),
                "the fan's own writes never cascade past the box");
    }

    // ------------------------------------------------------------------
    // The sapling's roll and the two-stage grow
    // ------------------------------------------------------------------

    @Test
    void theRandomTickRollStagesThenGrows() {
        scene();
        BlockPosition saplingAt = at(10, SURFACE + 1, 10);
        world.setBlock(saplingAt, SaplingBlocks.saplingType(0));
        // The 1-in-7 roll lands (a random answering 0 at the 7-bound).
        SaplingBlocks.randomTick(world, saplingAt, world.getBlock(saplingAt),
                rollRandom(7, 0));
        assertEquals(Identifier.parse("minecraft:oak_sapling_ready"),
                world.getBlock(saplingAt).identifier(),
                "the fresh sapling staged");
        // The next roll grows the tree (the same scripted 1-in-7 land; the
        // feature's own rolls fall through to the backing random).
        int logs = countLogs(10, 10);
        SaplingBlocks.randomTick(world, saplingAt, world.getBlock(saplingAt),
                rollRandom(7, 0));
        assertTrue(countLogs(10, 10) > logs + 3, "the ready sapling grew its trunk");
        assertTrue(world.getBlock(saplingAt).identifier().equals(
                        Identifier.parse("minecraft:oak_log")),
                "the sapling cell became the trunk base");
    }

    @Test
    void theLightGateOwnsTheRoll() {
        scene();
        // A sapling in a GENERATION-DARK pocket (the stone below the flat
        // grass — the chunk's stored sky light there is 0, the light engine
        // absent in the test scene): the cell above reads dark, the roll
        // never arms even at the scripted land.
        BlockPosition dark = at(12, 2, 12);
        world.setBlock(dark, SaplingBlocks.saplingType(0));
        assertTrue(Math.max(world.blockLightAt(dark.offset(0, 1, 0)),
                world.skyLightAt(dark.offset(0, 1, 0))) < SaplingBlocks.GROW_LIGHT,
                "the scene's pocket reads dark");
        SaplingBlocks.randomTick(world, dark, world.getBlock(dark), rollRandom(7, 0));
        assertEquals(Identifier.parse("minecraft:oak_sapling"),
                world.getBlock(dark).identifier(),
                "a dark sapling never rolls");
    }

    @Test
    void everyVariantsFeaturePlaces() {
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            scene();
            int x = 10;
            int z = 10;
            // The cluster-forming woods (spruce, jungle, dark oak) plant
            // their 2×2 cluster — the giant dispatch; the plain woods
            // (oak, birch, acacia) plant the single sapling.
            boolean clustered = variant == 1 || variant == 3 || variant == 5;
            if (clustered) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        world.setBlock(at(x + dx, SURFACE + 1, z + dz),
                                SaplingBlocks.stateOf(variant, true));
                    }
                }
            } else {
                world.setBlock(at(x, SURFACE + 1, z), SaplingBlocks.stateOf(variant, true));
            }
            SaplingBlocks.grow(world, at(x, SURFACE + 1, z), variant, new Random(7 + variant));
            assertTrue(countLogs(x, z) >= 4,
                    WoodVariantBlocks.WOODS.get(variant) + " grew a trunk");
            assertTrue(WoodVariantBlocks.isLog(world.getBlock(at(x, SURFACE + 1, z)).identifier()),
                    WoodVariantBlocks.WOODS.get(variant) + " grew its OWN log at the base");
            assertTrue(countLeaves(x, z) >= 4,
                    WoodVariantBlocks.WOODS.get(variant) + " grew its canopy");
        }
    }

    @Test
    void theClusterFormsGrowTheGiants() {
        scene();
        // A 2×2 spruce cluster: the giant form — the 2×2 trunk (four logs
        // at the first level).
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                world.setBlock(at(30 + dx, SURFACE + 1, 30 + dz), SaplingBlocks.stateOf(1, true));
            }
        }
        SaplingBlocks.grow(world, at(30, SURFACE + 1, 30), 1, new Random(11));
        int firstLevel = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (world.getBlock(at(30 + dx, SURFACE + 1, 30 + dz)).identifier()
                        .equals(Identifier.parse("minecraft:spruce_log"))) {
                    firstLevel++;
                }
            }
        }
        assertEquals(4, firstLevel, "the giant spruce's 2×2 trunk");
    }

    @Test
    void theDarkOakRequiresItsCluster() {
        scene();
        BlockPosition lone = at(50, SURFACE + 1, 50);
        world.setBlock(lone, SaplingBlocks.stateOf(5, true));
        SaplingBlocks.grow(world, lone, 5, new Random(3));
        assertEquals(Identifier.parse("minecraft:dark_oak_sapling_ready"),
                world.getBlock(lone).identifier(),
                "a lone dark-oak sapling never grows (grow line 118's bare return)");
    }

    @Test
    void aRefusedPlacementRestoresTheCluster() {
        scene();
        // The 2×2 cluster under a stone ceiling: the giant spruce's
        // clearance refuses, and the four saplings restore exactly (grow
        // lines 141-158).
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                world.setBlock(at(60 + dx, SURFACE + 1, 60 + dz), SaplingBlocks.stateOf(1, true));
            }
        }
        // The ceiling: low enough to refuse the 13+ trunk.
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                world.setBlock(at(60 + dx, SURFACE + 12, 60 + dz), BuiltinBlocks.STONE);
            }
        }
        SaplingBlocks.grow(world, at(60, SURFACE + 1, 60), 1, new Random(2));
        int restored = 0;
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (SaplingBlocks.isReady(world.getBlock(at(60 + dx, SURFACE + 1, 60 + dz))
                        .identifier())) {
                    restored++;
                }
            }
        }
        assertEquals(4, restored, "the refused placement restored the cluster");
    }

    // ------------------------------------------------------------------
    // The import table
    // ------------------------------------------------------------------

    @Test
    void theImportTableCarriesTheBands() {
        // The wire's (id, metadata) resolves the band members (the Anvil
        // canvas's own state table).
        assertEquals(java.util.Optional.of(Identifier.parse("minecraft:oak_leaves_check")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(18, 8));
        assertEquals(java.util.Optional.of(Identifier.parse("minecraft:jungle_leaves_persistent")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(18, 7));
        assertEquals(java.util.Optional.of(
                        Identifier.parse("minecraft:birch_leaves_persistent_check")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(18, 14));
        assertEquals(java.util.Optional.of(Identifier.parse("minecraft:acacia_leaves_check")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(161, 8));
        assertEquals(java.util.Optional.of(
                        Identifier.parse("minecraft:dark_oak_leaves_persistent_check")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(161, 13));
        assertEquals(java.util.Optional.of(Identifier.parse("minecraft:spruce_sapling_ready")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(6, 9));
        assertEquals(java.util.Optional.of(Identifier.parse("minecraft:dark_oak_sapling_ready")),
                net.zaminmc.torch.server.world.anvil.AnvilBlockIds.resolve(6, 13));
    }

    // ------------------------------------------------------------------
    // The helpers
    // ------------------------------------------------------------------

    /** A random whose nextInt(bound) answers the scripted value once, then falls back. */
    private static Random rollRandom(int bound, int value) {
        return new Random() {
            @Override
            public int nextInt(int b) {
                if (b == bound) {
                    return value;
                }
                return super.nextInt(b);
            }
        };
    }

    /** The trunk-column log count around a base cell (the 3×3 column, full height). */
    private int countLogs(int x, int z) {
        int logs = 0;
        for (int y = SURFACE + 1; y < SURFACE + 20; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (WoodVariantBlocks.isLog(world.getBlock(at(x + dx, y, z + dz))
                            .identifier())) {
                        logs++;
                    }
                }
            }
        }
        return logs;
    }

    /** The leaf count in the canopy column around a base cell. */
    private int countLeaves(int x, int z) {
        int leaves = 0;
        for (int y = SURFACE + 1; y < SURFACE + 24; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (LeafDecayBlocks.isLeafState(world.getBlock(at(x + dx, y, z + dz))
                            .identifier())) {
                        leaves++;
                    }
                }
            }
        }
        return leaves;
    }

    /** The exact log id at a cell (the variant's own assertion). */
    private Identifier trunkLogAt(int x, int y, int z) {
        return world.getBlock(at(x, y, z)).identifier();
    }
}
