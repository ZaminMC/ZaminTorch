package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.DoublePlantBlocks;
import net.zaminmc.torch.server.block.FlowerBlocks;
import net.zaminmc.torch.util.Identifier;

import java.util.Random;

/**
 * The flora placement features (the VIII14 decorators slice — the
 * reference's {@code net.minecraft.world.gen.feature} flora classes, 1:1):
 * the flower, the plant pair (the small mushrooms), the tall plant (the
 * grass/fern), the double plant, the dead bush, the cactus, the reeds,
 * the pumpkin and melon patches, the lily pad, the vine, and the fire
 * patch. Each carries its reference class's own attempt walk and survival
 * gates; the block reads and writes ride the populate-time view.
 */
final class FloraFeatures {

    private FloraFeatures() {
    }

    /** The placement contract (Feature.place — the flag-2 write form). */
    interface Feature {
        boolean place(FeatureWorld world, Random random, int x, int y, int z);
    }

    /** The plant soils (PlantBlock.canBePlacedOn lines 38-41): grass, dirt, the farmland pair. */
    static boolean isPlantSoil(FeatureWorld world, int x, int y, int z) {
        String value = world.blockAt(x, y, z).identifier().value();
        return value.equals("grass_block") || value.equals("dirt")
                || value.equals("farmland") || value.equals("farmland_wet");
    }

    /** The mushroom soils (BushBlock.canBePlacedOn): grass, dirt, the mycelium. */
    static boolean isMushroomSoil(FeatureWorld world, int x, int y, int z) {
        String value = world.blockAt(x, y, z).identifier().value();
        return value.equals("grass_block") || value.equals("dirt")
                || value.equals("mycelium");
    }

    /**
     * The sky-column light gate (the populate-time stand-in for the
     * mushroom's {@code world.getLight < 13} consult — the chunk is not
     * lit at generation: an open sky above refuses, a covered cell
     * passes; the named adaptation, ledgered in the slice's PR).
     */
    static boolean isSkyLit(FeatureWorld world, int x, int y, int z) {
        for (int above = y + 1; above < 256; above++) {
            if (world.isOpaqueAt(x, above, z)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ the flower

    /**
     * The flower (FlowerFeature 1:1, lines 13-32): the 64-attempt walk with
     * the ±7/±3 drift, the air + plant-soil gates, the picked state whole.
     */
    static final class FlowerFeature implements Feature {
        private BlockType flower;

        FlowerFeature(BlockType flower) {
            this.flower = flower;
        }

        void set(BlockType flower) {
            this.flower = flower;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                if (world.isAirAt(bx, by, bz) && isPlantSoil(world, bx, by - 1, bz)) {
                    world.setBlockAt(bx, by, bz, flower);
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------ the plant (mushroom)

    /**
     * The small mushroom (PlantFeature over a mushroom, lines 13-30): the
     * 64-attempt walk, the air gate, the mushroom soil + the sky-light law.
     */
    static final class MushroomFeature implements Feature {
        private final BlockType mushroom;

        MushroomFeature(BlockType mushroom) {
            this.mushroom = mushroom;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                if (world.isAirAt(bx, by, bz)
                        && !isSkyLit(world, bx, by, bz)
                        && isMushroomSoil(world, bx, by - 1, bz)) {
                    world.setBlockAt(bx, by, bz, mushroom);
                }
            }
            return true;
        }
    }

    // ----------------------------------------------------------- the tall plant

    /**
     * The tall plant (TallPlantFeature 1:1, lines 13-33): the air/leaf
     * drop-down to the ground, then the 128-attempt walk with the soil gate.
     */
    static final class TallPlantFeature implements Feature {
        private final BlockType plant;

        TallPlantFeature(BlockType plant) {
            this.plant = plant;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            // lines 20-24: the drop-down through air and leaves.
            int dy = y;
            BlockType at = world.blockAt(x, dy, z);
            while ((world.isAirAt(x, dy, z) || at.identifier().value().endsWith("_leaves"))
                    && dy > 0) {
                dy--;
                at = world.blockAt(x, dy, z);
            }
            for (int i = 0; i < 128; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = dy + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                if (world.isAirAt(bx, by, bz) && isPlantSoil(world, bx, by - 1, bz)) {
                    world.setBlockAt(bx, by, bz, plant);
                }
            }
            return true;
        }
    }

    // --------------------------------------------------------- the double plant

    /**
     * The double plant (DoublePlantFeature 1:1, lines 13-31): the 64-attempt
     * walk, the air + two-cell clearance + soil gates, the lower/upper pair
     * (the engine's flat lower/upper states over the one wire id 175).
     */
    static final class DoublePlantFeature implements Feature {
        private int variant;

        void setVariant(int variant) {
            this.variant = variant;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            boolean placed = false;
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                if (world.isAirAt(bx, by, bz) && world.isAirAt(bx, by + 1, bz)
                        && isPlantSoil(world, bx, by - 1, bz)) {
                    world.setBlockAt(bx, by, bz, DoublePlantBlocks.lower(variant));
                    world.setBlockAt(bx, by + 1, bz, DoublePlantBlocks.upper(variant));
                    placed = true;
                }
            }
            return placed;
        }
    }

    // ------------------------------------------------------------ the dead bush

    /**
     * The dead bush (DeadBushFeature 1:1, lines 13-33): the drop-down, the
     * four-attempt walk, the sand-family soil gate (the 1.8.8 band: the
     * sand family, the hardened clays, the grass, the dirt).
     */
    static final class DeadBushFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            int dy = y;
            while (world.isAirAt(x, dy, z) && dy > 0) {
                dy--;
            }
            for (int i = 0; i < 4; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = dy + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                String below = world.blockAt(bx, by - 1, bz).identifier().value();
                boolean soil = below.equals("sand") || below.equals("red_sand")
                        || below.equals("grass_block") || below.equals("dirt")
                        || below.equals("hardened_clay")
                        || below.equals("stained_hardened_clay");
                if (world.isAirAt(bx, by, bz) && soil) {
                    world.setBlockAt(bx, by, bz, BuiltinBlocks.DEAD_BUSH);
                }
            }
            return true;
        }
    }

    // --------------------------------------------------------------- the cactus

    /**
     * The cactus (CactusFeature 1:1, lines 13-33): the ten-attempt walk,
     * the air gate, the 1+rand(rand(3)) height, and the survival law (the
     * sand-or-cactus below, the four sides air — CactusBlock.canSurvive).
     */
    static final class CactusFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 10; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                if (world.isAirAt(bx, by, bz)) {
                    int height = 1 + random.nextInt(random.nextInt(3) + 1);
                    for (int k = 0; k < height; k++) {
                        if (canCactusSurvive(world, bx, by + k, bz)) {
                            world.setBlockAt(bx, by + k, bz, BuiltinBlocks.CACTUS);
                        }
                    }
                }
            }
            return true;
        }

        /** CactusBlock.canSurvive: the sand-or-cactus below, the four sides air. */
        private static boolean canCactusSurvive(FeatureWorld world, int x, int y, int z) {
            String below = world.blockAt(x, y - 1, z).identifier().value();
            if (!below.equals("sand") && !below.equals("cactus")) {
                return false;
            }
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (!world.isAirAt(x + dir[0], y, z + dir[1])) {
                    return false;
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------------- the reeds

    /**
     * The reeds (SugarcaneFeature 1:1, lines 13-36): the twenty-attempt
     * walk with the ±3/0/±3 drift, the water-adjacent law (one of the
     * below cell's four sides is water), and the 2+rand(rand(3)) stack.
     */
    static final class SugarcaneFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 20; i++) {
                int bx = x + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(4) - random.nextInt(4);
                if (world.isAirAt(bx, y, bz)) {
                    boolean water = false;
                    for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (world.isLiquidAt(bx + dir[0], y - 1, bz + dir[1])) {
                            water = true;
                            break;
                        }
                    }
                    if (water) {
                        int height = 2 + random.nextInt(random.nextInt(3) + 1);
                        for (int k = 0; k < height; k++) {
                            if (canReedsSurvive(world, bx, y + k, bz)) {
                                world.setBlockAt(bx, y + k, bz, BuiltinBlocks.SUGAR_CANE);
                            }
                        }
                    }
                }
            }
            return true;
        }

        /** ReedBlock.canBlockStay: a reed below, or the soil beside water. */
        private static boolean canReedsSurvive(FeatureWorld world, int x, int y, int z) {
            String below = world.blockAt(x, y - 1, z).identifier().value();
            if (below.equals("sugar_cane")) {
                return true;
            }
            if (!below.equals("grass_block") && !below.equals("dirt")
                    && !below.equals("sand")) {
                return false;
            }
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (world.isLiquidAt(x + dir[0], y - 1, z + dir[1])) {
                    return true;
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------ the pumpkin patch

    /**
     * The pumpkin patch (PumpkinPatchFeature 1:1, lines 13-30): the
     * 64-attempt walk, the air + grass-below gates (the facing roll rides
     * the engine's plain form — the metadata-direction slice's own
     * register-only law).
     */
    static final class PumpkinPatchFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                String below = world.blockAt(bx, by - 1, bz).identifier().value();
                if (world.isAirAt(bx, by, bz) && below.equals("grass_block")) {
                    // PumpkinPatchFeature line 16: defaultState().set(FACING,
                    // Direction.Plane.HORIZONTAL.pick(random)) — a random
                    // facing for every patch pumpkin (HORIZONTALS[nextInt(4)]).
                    world.setBlockAt(bx, by, bz, net.zaminmc.torch.server.block
                            .PumpkinBlocks.pumpkinOf(false, random.nextInt(4)));
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------- the melon patch

    /**
     * The melon patch (MelonPatchFeature 1:1, lines 13-29): the 64-attempt
     * walk over the grass, the jungle's own tail.
     */
    static final class MelonPatchFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                String below = world.blockAt(bx, by - 1, bz).identifier().value();
                if (world.isAirAt(bx, by, bz) && below.equals("grass_block")) {
                    world.setBlockAt(bx, by, bz, BuiltinBlocks.MELON_BLOCK);
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------- the lily pad

    /**
     * The lily pad (LilyPadFeature 1:1, lines 13-30): the ten-attempt walk,
     * the air cell over a water cell (the waterlily's own placement law).
     */
    static final class LilyPadFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 10; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                String below = world.blockAt(bx, by - 1, bz).identifier().value();
                if (world.isAirAt(bx, by, bz) && below.equals("water")) {
                    world.setBlockAt(bx, by, bz, BuiltinBlocks.WATERLILY);
                }
            }
            return true;
        }
    }

    // ----------------------------------------------------------------- the vine

    /**
     * The vine (VineFeature 1:1, lines 13-39): the climb to y 128 — an air
     * cell takes the first opaque neighbor's face (the engine's vine ships
     * the single plain form), a blocked cell re-rolls the column, and every
     * step walks up one.
     */
    static final class VineFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            int px = x;
            int py = y;
            int pz = z;
            while (py < 128) {
                if (world.isAirAt(px, py, pz)) {
                    for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        if (world.isOpaqueAt(px + dir[0], py, pz + dir[1])) {
                            world.setBlockAt(px, py, pz, BuiltinBlocks.VINE);
                            break;
                        }
                    }
                } else {
                    px += random.nextInt(4) - random.nextInt(4);
                    pz += random.nextInt(4) - random.nextInt(4);
                }
                py++;
            }
            return true;
        }
    }

    // ------------------------------------------------------------ the fire patch

    /**
     * The fire patch (FirePatchFeature 1:1, lines 13-28): the 64-attempt
     * walk over the netherrack, the air cell above it — the nether's own
     * populate stage (rides the nether generator's own lane, ported for
     * the family's completeness).
     */
    static final class FirePatchFeature implements Feature {
        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            for (int i = 0; i < 64; i++) {
                int bx = x + random.nextInt(8) - random.nextInt(8);
                int by = y + random.nextInt(4) - random.nextInt(4);
                int bz = z + random.nextInt(8) - random.nextInt(8);
                String below = world.blockAt(bx, by - 1, bz).identifier().value();
                if (world.isAirAt(bx, by, bz) && below.equals("netherrack")) {
                    world.setBlockAt(bx, by, bz, BuiltinBlocks.FIRE);
                }
            }
            return true;
        }
    }

    /** One flower state by the family's type id (the RED group's band). */
    static BlockType flower(int type) {
        return FlowerBlocks.state(type);
    }

    /** The YELLOW group's own member (the dandelion). */
    static final BlockType DANDELION = BuiltinBlocks.DANDELION;

    /** The tall-grass family's two members. */
    static final BlockType TALL_GRASS = BuiltinBlocks.TALL_GRASS;
    static final BlockType FERN = BuiltinBlocks.FERN;
}
