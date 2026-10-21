package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;

import java.util.Random;
import java.util.function.Predicate;

/**
 * The patch and vein features (the VIII14 decorators slice — the
 * reference's patch classes, 1:1): the disk patches (the sand, gravel,
 * clay and packed-ice circles the decorate walk's own patch stages
 * place), the mossy boulder (the mega taiga's), and the block vein (the
 * extreme hills' monster-egg veins; the ore vein stage itself is the
 * ledgered standing adaptation — the engine's generation-side ore
 * ladder).
 */
final class PatchFeatures {

    private PatchFeatures() {
    }

    // ------------------------------------------------------------- the disk patch

    /**
     * The disk patch (BlockPatchFeature 1:1, lines 13-46): the
     * underwater-only gate, the 2..size radius circle, and the
     * dirt-or-grass (or the family's own band) replacement over the
     * y&plusmn;j column.
     */
    static final class DiskPatchFeature implements FloraFeatures.Feature {
        private final BlockType block;
        private final int size;
        private final Predicate<BlockType> replaceable;

        DiskPatchFeature(BlockType block, int size) {
            this(block, size,
                    type -> type.equals(BuiltinBlocks.DIRT)
                            || type.equals(BuiltinBlocks.GRASS_BLOCK));
        }

        DiskPatchFeature(BlockType block, int size, Predicate<BlockType> replaceable) {
            this.block = block;
            this.size = size;
            this.replaceable = replaceable;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            if (!world.isLiquidAt(x, y, z)) {
                return false;
            }
            int radius = random.nextInt(size - 2) + 2;
            int j = block.equals(BuiltinBlocks.CLAY) ? 1 : 2;
            for (int k = x - radius; k <= x + radius; k++) {
                for (int l = z - radius; l <= z + radius; l++) {
                    int dx = k - x;
                    int dz = l - z;
                    if (dx * dx + dz * dz <= radius * radius) {
                        for (int y1 = y - j; y1 <= y + j; y1++) {
                            if (replaceable.test(world.blockAt(k, y1, l))) {
                                world.setBlockAt(k, y1, l, block);
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    /**
     * The ice patch (IcePatchFeature 1:1, lines 13-50): the drop-down, the
     * snow-block gate, the radius circle, and the dirt/snow/ice band over
     * the y&plusmn;1 column — the ice plains spikes' own companion.
     */
    static final class IcePatchFeature implements FloraFeatures.Feature {
        private final int size;

        IcePatchFeature(int size) {
            this.size = size;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            int dy = y;
            while (world.isAirAt(x, dy, z) && dy > 2) {
                dy--;
            }
            if (!world.blockAt(x, dy, z).equals(BuiltinBlocks.SNOW)) {
                return false;
            }
            int radius = random.nextInt(size - 2) + 2;
            for (int k = x - radius; k <= x + radius; k++) {
                for (int l = z - radius; l <= z + radius; l++) {
                    int dx = k - x;
                    int dz = l - z;
                    if (dx * dx + dz * dz <= radius * radius) {
                        for (int y1 = dy - 1; y1 <= dy + 1; y1++) {
                            BlockType at = world.blockAt(k, y1, l);
                            if (at.equals(BuiltinBlocks.DIRT) || at.equals(BuiltinBlocks.SNOW)
                                    || at.identifier().value().equals("ice")) {
                                world.setBlockAt(k, y1, l, BuiltinBlocks.PACKED_ICE);
                            }
                        }
                    }
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------- the mossy boulder

    /**
     * The boulder (BlockVeinFeature 1:1, lines 13-55): the drop-down to
     * the grass/dirt/stone floor, then the three-iteration mossy
     * cobblestone cluster walk (the growing &plusmn;(size+rand(2)) radii
     * with the squared-distance circle, the center walking away each
     * iteration).
     */
    static final class BoulderFeature implements FloraFeatures.Feature {
        private final BlockType block;
        private final int size;

        BoulderFeature(BlockType block, int size) {
            this.block = block;
            this.size = size;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            int dy = y;
            while (dy > 3) {
                if (!world.isAirAt(x, dy - 1, z)) {
                    String below = world.blockAt(x, dy - 1, z).identifier().value();
                    if (below.equals("grass_block") || below.equals("dirt")
                            || below.equals("stone")) {
                        break;
                    }
                }
                dy--;
            }
            if (dy <= 3) {
                return false;
            }
            int remaining = size;
            for (int i = 0; remaining >= 0 && i < 3; i++) {
                int rx = remaining + random.nextInt(2);
                int ry = remaining + random.nextInt(2);
                int rz = remaining + random.nextInt(2);
                float f = (rx + ry + rz) * 0.333F + 0.5F;
                for (int bx = x - rx; bx <= x + rx; bx++) {
                    for (int by = dy - ry; by <= dy + ry; by++) {
                        for (int bz = z - rz; bz <= z + rz; bz++) {
                            double distance = (bx - x) * (double) (bx - x)
                                    + (by - dy) * (double) (by - dy)
                                    + (bz - z) * (double) (bz - z);
                            if (distance <= f * f) {
                                world.setBlockAt(bx, by, bz, block);
                            }
                        }
                    }
                }
                x += -(remaining + 1) + random.nextInt(2 + remaining * 2);
                dy += -random.nextInt(2);
                z += -(remaining + 1) + random.nextInt(2 + remaining * 2);
            }
            return true;
        }
    }

    // ------------------------------------------------------------- the vein

    /**
     * The vein (VeinFeature 1:1, lines 15-75): the two seeded ellipse ends
     * over the size band, the interpolating center walk with the
     * sin-scaled radius, and the replaceable band's replacement — the
     * extreme hills' monster-egg veins' own shape (the ore veins'
     * generation-side ladder is the slice's named standing adaptation).
     */
    static final class VeinFeature implements FloraFeatures.Feature {
        private final BlockType block;
        private final int size;
        private final Predicate<BlockType> replaceable;

        VeinFeature(BlockType block, int size) {
            this(block, size, type -> type.equals(BuiltinBlocks.STONE));
        }

        VeinFeature(BlockType block, int size, Predicate<BlockType> replaceable) {
            this.block = block;
            this.size = size;
            this.replaceable = replaceable;
        }

        @Override
        public boolean place(FeatureWorld world, Random random, int x, int y, int z) {
            float f = random.nextFloat() * (float) Math.PI;
            double d0 = x + 8 + Math.sin(f) * size / 8.0F;
            double d1 = x + 8 - Math.sin(f) * size / 8.0F;
            double d2 = z + 8 + Math.cos(f) * size / 8.0F;
            double d3 = z + 8 - Math.cos(f) * size / 8.0F;
            double d4 = y + random.nextInt(3) - 2;
            double d5 = y + random.nextInt(3) - 2;
            for (int i = 0; i < size; i++) {
                float f1 = (float) i / size;
                double d6 = d0 + (d1 - d0) * f1;
                double d7 = d4 + (d5 - d4) * f1;
                double d8 = d2 + (d3 - d2) * f1;
                double d9 = random.nextDouble() * size / 16.0;
                double d10 = (Math.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
                double d11 = (Math.sin((float) Math.PI * f1) + 1.0F) * d9 + 1.0;
                int j = floor(d6 - d10 / 2.0);
                int k = floor(d7 - d11 / 2.0);
                int l = floor(d8 - d10 / 2.0);
                int i1 = floor(d6 + d10 / 2.0);
                int j1 = floor(d7 + d11 / 2.0);
                int k1 = floor(d8 + d10 / 2.0);
                for (int l1 = j; l1 <= i1; l1++) {
                    double d12 = (l1 + 0.5 - d6) / (d10 / 2.0);
                    if (d12 * d12 < 1.0) {
                        for (int i2 = k; i2 <= j1; i2++) {
                            double d13 = (i2 + 0.5 - d7) / (d11 / 2.0);
                            if (d12 * d12 + d13 * d13 < 1.0) {
                                for (int j2 = l; j2 <= k1; j2++) {
                                    double d14 = (j2 + 0.5 - d8) / (d10 / 2.0);
                                    if (d12 * d12 + d13 * d13 + d14 * d14 < 1.0
                                            && replaceable.test(world.blockAt(l1, i2, j2))) {
                                        world.setBlockAt(l1, i2, j2, block);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        private static int floor(double value) {
            int i = (int) value;
            return value < i ? i - 1 : i;
        }
    }

    /** The monster egg's registered form (block 97's stone variant). */
    static final BlockType INFESTED_STONE = BuiltinBlocks.INFESTED_STONE;

    /** One constant's helper for the patch band consults. */
    static Identifier id(String value) {
        return Identifier.parse("minecraft:" + value);
    }
}
