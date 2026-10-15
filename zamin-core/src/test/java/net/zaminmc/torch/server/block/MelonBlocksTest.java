package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The melon family (Slice 41 — MelonBlock/StemBlock 1:1): the stems' AGE
 * band over the crop model, the seeds' planting, the melon's slice drop
 * walk, the stems' seed roll, the fruit arm at AGE 7, and the family
 * tables (behavior, light, wire ids).
 */
class MelonBlocksTest {

    @Test
    void theStemsCarryTheAgeBandAndTheTablesAnswer() {
        for (String family : MelonBlocks.STEMS.keySet()) {
            for (int stage = 0; stage <= 7; stage++) {
                EngineBlockType state = MelonBlocks.stemStage(family, stage);
                assertEquals(stage, state.legacyMetadata(),
                        family + " stage " + stage);
                assertEquals(family, MelonBlocks.stemFamilyOf(state.identifier()));
                assertEquals(stage, WorldSolidity.cropStageOf(state.identifier()));
                // The behavior rows: the 0.0 plant, the seed drop.
                BlockBehavior behavior = BlockBehaviorTable.of(state.identifier()).orElseThrow();
                assertEquals(0.0, behavior.hardness());
                assertEquals(MelonBlocks.seedsOf(family), behavior.drops().get(0).item());
                // The light rows: the walk-through plant.
                assertEquals(0, BlockLightTable.lookup(state.identifier())
                        .orElseThrow().filter());
                // The solidity: walk-through flora.
                assertTrue(WorldSolidity.isCropStage(state.identifier()));
            }
        }
        // The wire ids: the reference's own rows.
        assertEquals(104, MelonBlocks.STEMS.get("pumpkin_stem"));
        assertEquals(105, MelonBlocks.STEMS.get("melon_stem"));
        // The melon block: the full opaque cube, the 1.0 wood row.
        assertEquals(15, BlockLightTable.lookup(
                Identifier.parse("minecraft:melon_block")).orElseThrow().filter());
        assertEquals(1.0, BlockBehaviorTable.of(
                Identifier.parse("minecraft:melon_block")).orElseThrow().hardness());
        // The item exists and the seeds resolve.
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:melon_block")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:pumpkin_seeds")).isPresent());
        assertTrue(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                Identifier.parse("minecraft:melon_seeds")).isPresent());
    }

    @Test
    void theDropWalksCarryTheReferenceShapes() {
        var service = new net.zaminmc.torch.server.interaction.DropService(
                new java.util.Random(7L));
        // The melon: 3-9 slices (min(9, 3 + nextInt(5) + nextInt(1+fortune))).
        for (int i = 0; i < 100; i++) {
            var drops = service.dropsFor(BuiltinBlocks.MELON_BLOCK, null, false, 0);
            assertEquals(1, drops.size());
            assertEquals("minecraft:melon", drops.get(0).type().identifier().toString());
            assertTrue(drops.get(0).count() >= 3 && drops.get(0).count() <= 7,
                    "the band 3-7 without fortune, saw " + drops.get(0).count());
        }
        // The stage-7 stem: up to 3 seeds (three nextInt(15) <= 7 rolls —
        // some roll over many draws).
        int sawSeed = 0;
        for (int i = 0; i < 200; i++) {
            var drops = service.dropsFor(
                    MelonBlocks.stemStage("pumpkin_stem", 7), null, false, 0);
            for (ItemStack drop : drops) {
                assertEquals("minecraft:pumpkin_seeds",
                        drop.type().identifier().toString());
                sawSeed++;
            }
        }
        assertTrue(sawSeed > 0, "the grown stem's rolls yield seeds");
        // The stage-0 stem: nextInt(15) <= 0 only on the 0 roll (1/15 each).
        int youngSeedDrops = 0;
        for (int i = 0; i < 200; i++) {
            var drops = service.dropsFor(
                    MelonBlocks.stemStage("melon_stem", 0), null, false, 0);
            youngSeedDrops += drops.size();
        }
        assertTrue(youngSeedDrops > 0 && youngSeedDrops < 200,
                "the young stem's rare rolls (the 1/15 band)");
    }

    @Test
    void theFruitArmSpawnsBesideTheGrownStem() {
        // A pure-world walk (no live engine needed): the stem at the center
        // on farmland, a dirt cell east, air above it — the fruit lands.
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new net.zaminmc.torch.server.world.EngineWorld("melon-it", registry,
                new net.zaminmc.torch.server.world.FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        BlockPosition stem = new BlockPosition(10, 40, 10);
        world.setBlock(stem, MelonBlocks.stemStage("melon_stem", 7));
        world.setBlock(stem.offset(0, -1, 0), BuiltinBlocks.FARMLAND);
        world.setBlock(stem.offset(1, -1, 0), BuiltinBlocks.DIRT);
        // The four horizontal neighbors: only the east cell has soil — the
        // walk must find it (the random pick retries across the directions
        // over enough draws).
        boolean spawned = false;
        java.util.Random random = new java.util.Random(99L);
        for (int i = 0; i < 200 && !spawned; i++) {
            // reset any prior fruit to keep the walk eligible
            for (int[] step : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                world.setBlock(stem.offset(step[0], 0, step[1]), world.airType());
            }
            spawned = MelonBlocks.trySpawnFruit(world, stem, "melon_stem", random);
        }
        assertTrue(spawned, "the grown stem spawned its fruit");
        BlockType east = world.getBlock(stem.offset(1, 0, 0));
        assertEquals("minecraft:melon_block", east.identifier().toString());
        // The already-fruiting gate: a fruit beside the stem ends the walk.
        assertTrue(!MelonBlocks.trySpawnFruit(world, stem, "melon_stem", random));
    }

    @Test
    void theCropStageResolveCoversTheStems() {
        assertEquals(MelonBlocks.stemStage("pumpkin_stem", 3),
                RandomTickSystem.cropStage(
                        Identifier.parse("minecraft:pumpkin_stem_stage0"), 3));
        assertEquals(MelonBlocks.stemStage("melon_stem", 5),
                RandomTickSystem.cropStage(
                        Identifier.parse("minecraft:melon_stem_stage2"), 5));
        assertEquals("pumpkin_stem", WorldSolidity.cropFamilyOf(
                Identifier.parse("minecraft:pumpkin_stem_stage4")));
        assertEquals("melon_stem", WorldSolidity.cropFamilyOf(
                Identifier.parse("minecraft:melon_stem_stage6")));
    }

}
