package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;

import java.util.Random;

/**
 * The grass family's bone-meal arm (VIII7 — GrassBlock 1:1 over the
 * reference's {@code Fertilizable} pair): the 128-attempt {@code grow} walk
 * (GrassBlock.grow lines 91-122) — each attempt walks outward from the
 * clicked cell's top at a radius-growing step count ({@code i / 16} steps),
 * every step requiring grass below and a non-solid host, and a surviving
 * attempt plants the biome's flower roll (1-in-8) or the tall grass (the
 * other 7-in-8) when the host cell is air and the soil gate holds.
 *
 * <p>THE FLOWER PICK (the reference's {@code getBiome(pos).getRandomFlower}):
 * the base law (Biome.java lines 328-330 — dandelion at 2-in-3, poppy at
 * 1-in-3) carries every biome the engine shades except the plains, whose own
 * override (PlainsBiome lines 24-46) adds the tulip band — the fixed-seed
 * {@code FOLIAGE_NOISE} field (Biome.java line 595's {@code new
 * PerlinSimplexNoise(new Random(2345L), 1)}, world-seed independent) reading
 * below -0.8 picks the four tulips, else the poppy/houstonia/oxeye-daisy
 * ladder at 2-in-3 with the dandelion tail. The forest's own override is the
 * BIRCH-forest variant's blue-orchid noise walk (ForestBiome lines 81-89,
 * {@code variant == 1}) — the engine's FOREST is the oak forest (the
 * reference's variant 0), which answers the base law, and the swamp's
 * always-blue-orchid (SwampBiome lines 47-49) rides the biome family's own
 * slice (VIII14 — the engine shades no swamp today); both named as the
 * counted rows they are.</p>
 */
public final class GrassBlocks {

    private GrassBlocks() {
    }

    /**
     * The reference's own FOLIAGE_NOISE (Biome.java line 595): the fixed
     * 2345 seed, one level — identical across every world.
     */
    static final PerlinSimplexNoise FOLIAGE_NOISE =
            new PerlinSimplexNoise(new Random(2345L), 1);

    /**
     * The bone-meal grow (GrassBlock.grow lines 91-122): 128 attempts, the
     * walk's step count growing with the attempt index ({@code i / 16} —
     * the reference's own radius ladder), each step offset ±1 x/z with the
     * {@code (nextInt(3) - 1) * nextInt(3) / 2} y form, every step requiring
     * grass below and a non-solid host (an abort skips the WHOLE attempt —
     * the reference's {@code continue label38}), and a surviving attempt
     * planting the flower roll (1-in-8) or the tall grass on an air host
     * over the soil gate.
     */
    public static void growGrass(EngineWorld world, BlockPosition pos, Random random) {
        BlockPosition start = pos.offset(0, 1, 0);

        walk:
        for (int i = 0; i < 128; i++) {
            BlockPosition at = start;

            for (int j = 0; j < i / 16; j++) {
                at = at.offset(
                        random.nextInt(3) - 1,
                        (random.nextInt(3) - 1) * random.nextInt(3) / 2,
                        random.nextInt(3) - 1);
                // The step's own gates (grow lines 103-106): grass below the
                // host cell, the host itself not solid — a failure aborts the
                // attempt whole (label38).
                if (!world.getBlock(at.offset(0, -1, 0))
                        .equals(BuiltinBlocks.GRASS_BLOCK)
                        || WorldSolidity.isSolid(world.getBlock(at))) {
                    continue walk;
                }
            }

            if (world.getBlock(at).equals(world.airType())) {
                // The flower roll (grow lines 108-118): 1-in-8 picks the
                // biome's own flower with its canSurvive gate (PlantBlock's
                // soil consult — the engine's FlowerBlocks read), the other
                // 7-in-8 the tall grass with the same gate (grow lines
                // 119-122).
                if (random.nextInt(8) == 0) {
                    BlockType flower = randomFlowerAt(world, at, random);
                    if (FlowerBlocks.isSupported(world, at)) {
                        world.setBlock(at, flower);
                    }
                } else {
                    if (FlowerBlocks.isSupported(world, at)) {
                        world.setBlock(at, BuiltinBlocks.TALL_GRASS);
                    }
                }
            }
        }
    }

    /**
     * The biome's own flower pick ({@code getBiome(pos).getRandomFlower}):
     * the plains' override (the tulip band + the ladder) or the base law —
     * the YELLOW group's pick answers the dandelion's own block, the RED
     * group's the family state at the TYPE id (the reference's
     * {@code type.getGroup().getBlock()} + {@code getTypeProperty()} pair).
     */
    static BlockType randomFlowerAt(EngineWorld world, BlockPosition pos, Random random) {
        String biome = world.biomeAt(pos);
        if (biome.equals("plains")) {
            // The plains' override (PlainsBiome lines 24-46): the tulip band
            // first — the fixed-seed noise field reading below -0.8 picks
            // the four tulips (ORANGE, RED, PINK, WHITE — the reference's
            // own j ladder) — else the poppy/houstonia/oxeye-daisy ladder at
            // 2-in-3 with the dandelion tail.
            double d0 = FOLIAGE_NOISE.getValue(pos.x() / 200.0, pos.z() / 200.0);
            if (d0 < -0.8) {
                int j = random.nextInt(4);
                switch (j) {
                    case 0:
                        return FlowerBlocks.state(5); // orange_tulip
                    case 1:
                        return FlowerBlocks.state(4); // red_tulip
                    case 2:
                        return FlowerBlocks.state(7); // pink_tulip
                    default:
                        return FlowerBlocks.state(6); // white_tulip
                }
            } else if (random.nextInt(3) > 0) {
                int i = random.nextInt(3);
                return FlowerBlocks.state(i == 0 ? 0 : i == 1 ? 3 : 8);
            }
            return BuiltinBlocks.DANDELION;
        }
        // The base law (Biome.java lines 328-330): dandelion at 2-in-3,
        // poppy at 1-in-3 — the law every non-plains biome the engine shades
        // answers (the oak FOREST's own override is the birch variant's, and
        // the JUNGLE defines none).
        return random.nextInt(3) > 0 ? BuiltinBlocks.DANDELION : FlowerBlocks.state(0);
    }
}
