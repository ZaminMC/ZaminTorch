package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The tree feature family (the 2026-10-21 audit's VIII2 — the reference's
 * {@code net.minecraft.world.gen.feature} tree shapes, the sapling-grow
 * surface's own vocabulary): the oak, the fancy large oak, the spruce, the
 * giant spruce, the birch, the jungle pair, the acacia, and the dark oak —
 * each 1:1 with its reference class, placed through the engine's own
 * publish commit (the reference's flag-3 form: the sapling path constructs
 * every feature notify-true).
 *
 * <p><b>The shared base</b> ({@code AbstractTreeFeature}): the
 * {@link #canReplace} law (air, any leaf state, grass, dirt, any log, any
 * sapling, the vine) and {@code placeDirt} (a grass or farmland host
 * becomes dirt under the trunk).</p>
 *
 * <p><b>The counted ledger notes</b>: the reference's branch logs carry
 * LOG_AXIS (the fancy oak's placeBranch picks X/Y/Z by the branch angle,
 * DarkOak's arms likewise) — the engine's log model ships the axis-Y form
 * (WoodVariantBlocks' own ledger row, the metadata-direction slice); the
 * giant jungle's vine arms carry the face bits (the engine's vine ships
 * the single plain form). Both land as the plain form with the note here.</p>
 */
public final class TreeFeatures {

    private TreeFeatures() {
    }

    /** The placement contract (Feature.place — the notify-true form). */
    public interface Feature {
        boolean place(EngineWorld world, Random random, BlockPosition pos);
    }

    // ------------------------------------------------------- the shared base

    /** @return the reference's canReplace law (AbstractTreeFeature lines 11-22). */
    static boolean canReplace(EngineWorld world, BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        Identifier id = type.identifier();
        if (type.equals(world.airType())) {
            return true;
        }
        if (LeafDecayBlocks.isLeafState(id) || WoodVariantBlocks.isLog(id)
                || WoodVariantBlocks.isSapling(id)) {
            return true;
        }
        return id.equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                || id.equals(BuiltinBlocks.DIRT.identifier())
                || id.equals(Identifier.parse("minecraft:vine"));
    }

    /** @return the AIR-or-LEAVES gate (the leaf/trunk placement band). */
    static boolean isAirOrLeaves(EngineWorld world, BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        return type.equals(world.airType()) || LeafDecayBlocks.isLeafState(type.identifier());
    }

    /** @return AIR, LEAVES, or REPLACEABLE_PLANT (the oak's wider band —
     *  Material.REPLACEABLE_PLANT is the engine's flora consult). */
    static boolean isAirOrLeavesOrPlant(EngineWorld world, BlockPosition pos) {
        return isAirOrLeaves(world, pos)
                || WorldSolidity.isReplaceable(world.getBlock(pos).identifier());
    }

    /** @return the opaque consult (the spruce's leaf gate — a full cube's filter). */
    static boolean isOpaque(EngineWorld world, BlockPosition pos) {
        return BlockLightTable.filter(world.getBlock(pos)) >= 15;
    }

    /** @return the soil gate's grass/dirt/farmland band. */
    static boolean isSoil(EngineWorld world, BlockPosition pos) {
        Identifier id = world.getBlock(pos).identifier();
        return id.equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                || id.equals(BuiltinBlocks.DIRT.identifier())
                || id.equals(BuiltinBlocks.FARMLAND.identifier())
                || id.equals(BuiltinBlocks.FARMLAND_WET.identifier());
    }

    /** @return the grass/dirt band alone (the acacia/dark-oak/giant forms). */
    static boolean isGrassOrDirt(EngineWorld world, BlockPosition pos) {
        Identifier id = world.getBlock(pos).identifier();
        return id.equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                || id.equals(BuiltinBlocks.DIRT.identifier());
    }

    /** The reference's placeDirt (AbstractTreeFeature lines 24-28): a
     *  non-dirt host becomes dirt. */
    static void placeDirt(EngineWorld world, BlockPosition pos) {
        if (!world.getBlock(pos).identifier().equals(BuiltinBlocks.DIRT.identifier())) {
            world.setBlock(pos, BuiltinBlocks.DIRT);
        }
    }

    /** The leaf type of a wood (the fresh DECAYABLE + !CHECK_DECAY form the features place). */
    static BlockType leaves(int variant) {
        return WoodVariantBlocks.leafType(variant);
    }

    /** The log type of a wood (the axis-Y form — the counted note in the class javadoc). */
    static BlockType log(int variant) {
        return WoodVariantBlocks.logType(variant);
    }

    // ------------------------------------------------------------ the oak

    /**
     * The oak (TreeFeature 1:1 — lines 35-167, the sapling form
     * {@code new TreeFeature(true)}): the 4+rand(3) trunk, the two-band
     * leaf shell with the rounded-corner roll, and the wide
     * AIR/LEAVES/REPLACEABLE_PLANT placement band.
     */
    public static Feature oak(Random unused) {
        return new OakFeature(0, 4, false);
    }

    /** The jungle small form (the grow's {@code TreeFeature(true, 4+rand(7), ...)}) — the roll rides the caller's random. */
    public static Feature jungleSmall(Random random) {
        return new OakFeature(3, 4 + random.nextInt(7), false);
    }

    /** The oak family's shape (the jungle small form is the same law with the jungle states). */
    static final class OakFeature implements Feature {
        private final int variant;
        private final int baseHeight;

        OakFeature(int variant, int baseHeight, boolean placeVines) {
            this.variant = variant;
            this.baseHeight = baseHeight;
        }

        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + baseHeight;
            if (pos.y() < 1 || pos.y() + i + 1 > 256) {
                return false;
            }
            // The clearance (lines 51-71): k = 0 at the base cell, 1 through
            // the shaft, 2 in the top band — the (2k+1)² column must canReplace.
            boolean clear = true;
            for (int y = pos.y(); y <= pos.y() + 1 + i; y++) {
                int k = 1;
                if (y == pos.y()) {
                    k = 0;
                }
                if (y >= pos.y() + 1 + i - 2) {
                    k = 2;
                }
                for (int x = pos.x() - k; x <= pos.x() + k && clear; x++) {
                    for (int z = pos.z() - k; z <= pos.z() + k && clear; z++) {
                        if (y < 0 || y > 255) {
                            clear = false;
                        } else if (!canReplace(world, new BlockPosition(x, y, z))) {
                            clear = false;
                        }
                    }
                }
            }
            if (!clear) {
                return false;
            }
            if (!isSoil(world, pos.offset(0, -1, 0)) || pos.y() >= 256 - i - 1) {
                return false;
            }
            placeDirt(world, pos.offset(0, -1, 0));
            // The leaf shell (lines 83-103): the four-layer band, the radius
            // 1+1-i4/2, the corner trim roll (|dx| != r || |dz| != r || the
            // top layer keeps its corners).
            for (int y = pos.y() - 3 + i; y <= pos.y() + i; y++) {
                int dy = y - (pos.y() + i);
                int radius = 1 - dy / 2;
                for (int x = pos.x() - radius; x <= pos.x() + radius; x++) {
                    int dx = x - pos.x();
                    for (int z = pos.z() - radius; z <= pos.z() + radius; z++) {
                        int dz = z - pos.z();
                        if (Math.abs(dx) != radius || Math.abs(dz) != radius
                                || random.nextInt(2) != 0 && dy != 0) {
                            if (isAirOrLeavesOrPlant(world, new BlockPosition(x, y, z))) {
                                world.setBlock(new BlockPosition(x, y, z), leaves(variant));
                            }
                        }
                    }
                }
            }
            // The trunk (lines 105-114).
            for (int t = 0; t < i; t++) {
                BlockPosition at = pos.offset(0, t, 0);
                if (isAirOrLeavesOrPlant(world, at)) {
                    world.setBlock(at, log(variant));
                }
            }
            return true;
        }
    }

    // -------------------------------------------------------- the large oak

    /**
     * The fancy oak (LargeOakTreeFeature 1:1 — the branch machinery): the
     * 5+rand(12) height, the 0.618 trunk scale, the branch rolls through
     * the getTreeShape band, the 4-layer foliage clusters with the
     * circle-law shape, and the branch trunks along their angles. The
     * feature's own seeded re-roll ({@code new Random(random.nextLong())})
     * rides the reference's exact shape.
     */
    public static Feature largeOak(Random unused) {
        return new LargeOakFeature();
    }

    static final class LargeOakFeature implements Feature {
        private static final int MAX_TRUNK_HEIGHT = 12;
        private static final int FOLIAGE_CLUSTER_HEIGHT = 4;

        private EngineWorld world;
        private BlockPosition origin;
        private Random random;
        private int height;
        private int trunkHeight;
        private final List<int[]> branches = new ArrayList<>();

        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            this.world = world;
            this.origin = pos;
            this.random = new Random(random.nextLong());
            this.height = 5 + this.random.nextInt(MAX_TRUNK_HEIGHT);
            this.branches.clear();
            if (!canPlace()) {
                return false;
            }
            makeBranches();
            placeFoliage();
            placeTrunk();
            placeBranches();
            return true;
        }

        private boolean canPlace() {
            if (!isSoil(world, origin.offset(0, -1, 0))) {
                return false;
            }
            int run = tryBranch(origin, origin.offset(0, height - 1, 0));
            if (run == -1) {
                return true;
            }
            if (run < 6) {
                return false;
            }
            height = run;
            return true;
        }

        /** makeBranches (lines 55-105): the trunk scale, the branch count, the angle rolls. */
        private void makeBranches() {
            trunkHeight = (int) (height * 0.618);
            if (trunkHeight >= height) {
                trunkHeight = height - 1;
            }
            int count = (int) (1.382 + Math.pow(1.0 * height / 13.0, 2.0));
            if (count < 1) {
                count = 1;
            }
            int baseY = origin.y() + trunkHeight;
            int k = height - FOLIAGE_CLUSTER_HEIGHT;
            branches.clear();
            branches.add(new int[]{origin.x(), origin.y() + k, origin.z(), baseY});
            for (; k >= 0; k--) {
                float shape = treeShape(k);
                if (!(shape < 0.0f)) {
                    for (int l = 0; l < count; l++) {
                        double d0 = 1.0 * shape * (random.nextFloat() + 0.328);
                        double d1 = random.nextFloat() * 2.0f * Math.PI;
                        double d2 = d0 * Math.sin(d1) + 0.5;
                        double d3 = d0 * Math.cos(d1) + 0.5;
                        BlockPosition tip = new BlockPosition(
                                origin.x() + (int) d2, 0, origin.z() + (int) d3);
                        int tipY = origin.y() + k - 1;
                        BlockPosition tipCell = new BlockPosition(tip.x(), tipY, tip.z());
                        BlockPosition tipTop = tipCell.offset(0, FOLIAGE_CLUSTER_HEIGHT, 0);
                        if (tryBranch(tipCell, tipTop) == -1) {
                            int dx = origin.x() - tipCell.x();
                            int dz = origin.z() - tipCell.z();
                            double d4 = tipCell.y() - Math.sqrt(dx * dx + dz * dz) * 0.381;
                            int k1 = d4 > baseY ? baseY : (int) d4;
                            BlockPosition branchBase = new BlockPosition(origin.x(), k1, origin.z());
                            if (tryBranch(branchBase, tipCell) == -1) {
                                branches.add(new int[]{tipCell.x(), tipCell.y(), tipCell.z(),
                                        branchBase.y()});
                            }
                        }
                    }
                }
            }
        }

        /** getTreeShape (lines 122-140). */
        private float treeShape(int atHeight) {
            if (atHeight < height * 0.3f) {
                return -1.0f;
            }
            float f = height / 2.0f;
            float f1 = f - atHeight;
            float f2 = (float) Math.sqrt(f * f - f1 * f1);
            if (f1 == 0.0f) {
                f2 = f;
            } else if (Math.abs(f1) >= f) {
                return 0.0f;
            }
            return f2 * 0.5f;
        }

        /** getClusterShape (lines 142-150). */
        private float clusterShape(int layer) {
            if (layer < 0 || layer >= FOLIAGE_CLUSTER_HEIGHT) {
                return -1.0f;
            }
            return layer != 0 && layer != FOLIAGE_CLUSTER_HEIGHT - 1 ? 3.0f : 2.0f;
        }

        /** placeFoliageCluster + placeCluster (lines 76-100). */
        private void placeFoliage() {
            for (int[] branch : branches) {
                for (int layer = 0; layer < FOLIAGE_CLUSTER_HEIGHT; layer++) {
                    placeCluster(new BlockPosition(branch[0], branch[1] + layer, branch[2]),
                            clusterShape(layer));
                }
            }
        }

        private void placeCluster(BlockPosition center, float shape) {
            int bound = (int) (shape + 0.618);
            for (int dx = -bound; dx <= bound; dx++) {
                for (int dz = -bound; dz <= bound; dz++) {
                    if (Math.pow(Math.abs(dx) + 0.5, 2.0) + Math.pow(Math.abs(dz) + 0.5, 2.0)
                            <= shape * shape) {
                        BlockPosition at = center.offset(dx, 0, dz);
                        if (isAirOrLeaves(world, at)) {
                            world.setBlock(at, leaves(0));
                        }
                    }
                }
            }
        }

        /** placeTrunk (lines 200-211). */
        private void placeTrunk() {
            placeBranchLine(origin, origin.offset(0, trunkHeight, 0));
        }

        /** placeBranches (lines 213-224). */
        private void placeBranches() {
            for (int[] branch : branches) {
                BlockPosition base = new BlockPosition(origin.x(), branch[3], origin.z());
                if (!(base.x() == branch[0] && base.y() == branch[1] && base.z() == branch[2])
                        && branch[3] - origin.y() >= height * 0.2) {
                    placeBranchLine(base, new BlockPosition(branch[0], branch[1], branch[2]));
                }
            }
        }

        /** placeBranch (lines 102-119): the straight-line walk at half-cell steps. */
        private void placeBranchLine(BlockPosition from, BlockPosition to) {
            int dx = to.x() - from.x();
            int dy = to.y() - from.y();
            int dz = to.z() - from.z();
            int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            float fx = (float) dx / steps;
            float fy = (float) dy / steps;
            float fz = (float) dz / steps;
            for (int step = 0; step <= steps; step++) {
                world.setBlock(new BlockPosition(
                        (int) (from.x() + 0.5f + step * fx),
                        (int) (from.y() + 0.5f + step * fy),
                        (int) (from.z() + 0.5f + step * fz)), log(0));
            }
        }

        /** tryBranch (lines 249-265): the clearance probe along the line, -1 clear. */
        private int tryBranch(BlockPosition from, BlockPosition to) {
            int dx = to.x() - from.x();
            int dy = to.y() - from.y();
            int dz = to.z() - from.z();
            int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
            if (steps == 0) {
                return -1;
            }
            float fx = (float) dx / steps;
            float fy = (float) dy / steps;
            float fz = (float) dz / steps;
            for (int step = 0; step <= steps; step++) {
                if (!canReplace(world, new BlockPosition(
                        (int) (from.x() + 0.5f + step * fx),
                        (int) (from.y() + 0.5f + step * fy),
                        (int) (from.z() + 0.5f + step * fz)))) {
                    return step;
                }
            }
            return -1;
        }
    }

    // ---------------------------------------------------------- the spruce

    /**
     * The spruce (SpruceTreeFeature 1:1): the 6+rand(4) height, the j/k/l
     * band, the strict AIR/LEAVES clearance, the ring ladder with its
     * radius walk, and the trimmed trunk.
     */
    public static Feature spruce(Random unused) {
        return new SpruceFeature();
    }

    static final class SpruceFeature implements Feature {
        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(4) + 6;
            int j = 1 + random.nextInt(2);
            int k = i - j;
            int l = 2 + random.nextInt(2);
            if (pos.y() < 1 || pos.y() + i + 1 > 256) {
                return false;
            }
            // The clearance (lines 44-73): the strict AIR/LEAVES band — a
            // stricter gate than the oak's canReplace.
            boolean clear = true;
            for (int y = pos.y(); y <= pos.y() + 1 + i && clear; y++) {
                int r = 1;
                if (y - pos.y() < j) {
                    r = 0;
                } else {
                    r = l;
                }
                for (int x = pos.x() - r; x <= pos.x() + r && clear; x++) {
                    for (int z = pos.z() - r; z <= pos.z() + r && clear; z++) {
                        if (y >= 0 && y < 256) {
                            if (!isAirOrLeaves(world, new BlockPosition(x, y, z))) {
                                clear = false;
                            }
                        } else {
                            clear = false;
                        }
                    }
                }
            }
            if (!clear) {
                return false;
            }
            if (!isSoil(world, pos.offset(0, -1, 0)) || pos.y() >= 256 - i - 1) {
                return false;
            }
            placeDirt(world, pos.offset(0, -1, 0));
            // The ring ladder (lines 86-125): the radius walk from the
            // random start, the reset pattern, the !isOpaque placement gate.
            int radius = random.nextInt(2);
            int j3 = 1;
            int k3 = 0;
            for (int layer = 0; layer <= k; layer++) {
                int y = pos.y() + i - layer;
                for (int x = pos.x() - radius; x <= pos.x() + radius; x++) {
                    int dx = x - pos.x();
                    for (int z = pos.z() - radius; z <= pos.z() + radius; z++) {
                        int dz = z - pos.z();
                        if (Math.abs(dx) != radius || Math.abs(dz) != radius || radius <= 0) {
                            BlockPosition at = new BlockPosition(x, y, z);
                            if (!isOpaque(world, at)) {
                                world.setBlock(at, leaves(1));
                            }
                        }
                    }
                }
                if (radius >= j3) {
                    radius = k3;
                    k3 = 1;
                    if (++j3 > l) {
                        j3 = l;
                    }
                } else {
                    radius++;
                }
            }
            // The trimmed trunk (lines 127-135).
            int trim = random.nextInt(3);
            for (int t = 0; t < i - trim; t++) {
                BlockPosition at = pos.offset(0, t, 0);
                if (isAirOrLeaves(world, at)) {
                    world.setBlock(at, log(1));
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------ the giant spruce

    /**
     * The giant spruce (GiantSpruceTreeFeature 1:1 — the 2×2 form): the
     * 13+rand(3)+rand(15) height, the giant clearance with the dirt patch
     * under the trunk, the top-down leaf rings with the odd-layer +1, and
     * the 2×2 trunk. The podzol soil patch rides the worldgen decorator's
     * own placeSoil — the sapling path never calls it.
     */
    public static Feature giantSpruce(Random unused, boolean sparseLeaves) {
        return new GiantSpruceFeature(sparseLeaves);
    }

    static final class GiantSpruceFeature implements Feature {
        private static final int BASE_HEIGHT = 13;
        private static final int BONUS_HEIGHT = 15;
        private final boolean sparseLeaves;

        GiantSpruceFeature(boolean sparseLeaves) {
            this.sparseLeaves = sparseLeaves;
        }

        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + BASE_HEIGHT;
            if (BONUS_HEIGHT > 1) {
                i += random.nextInt(BONUS_HEIGHT);
            }
            if (!canGrow(world, random, pos, i)) {
                return false;
            }
            placeLeavesColumn(world, pos.x(), pos.z(), pos.y() + i, 0, random);
            for (int t = 0; t < i; t++) {
                BlockPosition at = pos.offset(0, t, 0);
                if (isAirOrLeaves(world, at)) {
                    world.setBlock(at, log(1));
                }
                if (t < i - 1) {
                    BlockPosition east = pos.offset(1, t, 0);
                    if (isAirOrLeaves(world, east)) {
                        world.setBlock(east, log(1));
                    }
                    BlockPosition southEast = pos.offset(1, t, 1);
                    if (isAirOrLeaves(world, southEast)) {
                        world.setBlock(southEast, log(1));
                    }
                    BlockPosition south = pos.offset(0, t, 1);
                    if (isAirOrLeaves(world, south)) {
                        world.setBlock(south, log(1));
                    }
                }
            }
            return true;
        }

        /** The giant clearance (GiantTreeFeature.canGrow + the dirt patch). */
        private boolean canGrow(EngineWorld world, Random random, BlockPosition pos, int height) {
            if (pos.y() < 1 || pos.y() + height + 1 > 256) {
                return false;
            }
            for (int y = 0; y <= 1 + height; y++) {
                int r = 2;
                if (y == 0) {
                    r = 1;
                } else if (y >= 1 + height - 2) {
                    r = 2;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (pos.y() + y < 0 || pos.y() + y > 255
                                || !canReplace(world, pos.offset(dx, y, dz))) {
                            return false;
                        }
                    }
                }
            }
            // checkAndPlaceDirtUnderneath: the grass/dirt gate under the 2×2.
            BlockPosition below = pos.offset(0, -1, 0);
            if (!isGrassOrDirt(world, below) || pos.y() < 2) {
                return false;
            }
            placeDirt(world, below);
            placeDirt(world, below.offset(1, 0, 0));
            placeDirt(world, below.offset(0, 0, 1));
            placeDirt(world, below.offset(1, 0, 1));
            return true;
        }

        /** placeLeaves (lines 76-87): the top-down rings. */
        private void placeLeavesColumn(EngineWorld world, int x, int z, int topY,
                                       int baseRadius, Random random) {
            int layers = random.nextInt(5) + (sparseLeaves ? BASE_HEIGHT : 3);
            int previous = 0;
            for (int y = topY - layers; y <= topY; y++) {
                int fromTop = topY - y;
                int radius = baseRadius + (int) ((float) fromTop / layers * 3.5f);
                placeLeavesRingStrict(world, new BlockPosition(x, y, z),
                        radius + (fromTop > 0 && radius == previous && (y & 1) == 0 ? 1 : 0));
                previous = radius;
            }
        }

        /** placeLeavesRingStrict (GiantTreeFeature lines 95-112): the four-corner circle law. */
        private void placeLeavesRingStrict(EngineWorld world, BlockPosition center, int radius) {
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius + 1; dx++) {
                for (int dz = -radius; dz <= radius + 1; dz++) {
                    int ax = dx - 1;
                    int az = dz - 1;
                    if (dx * dx + dz * dz <= r2 || ax * ax + az * az <= r2
                            || dx * dx + az * az <= r2 || ax * ax + dz * dz <= r2) {
                        BlockPosition at = center.offset(dx, 0, dz);
                        if (isAirOrLeaves(world, at)) {
                            world.setBlock(at, leaves(1));
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- the birch

    /**
     * The birch (BirchTreeFeature 1:1 — the sapling form
     * {@code new BirchTreeFeature(true, false)}): the 5+rand(3) height,
     * the tight leaf shell, the AIR/LEAVES placement band.
     */
    public static Feature birch(Random unused) {
        return new BirchFeature(false);
    }

    static final class BirchFeature implements Feature {
        private final boolean tall;

        BirchFeature(boolean tall) {
            this.tall = tall;
        }

        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + 5;
            if (tall) {
                i += random.nextInt(7);
            }
            if (pos.y() < 1 || pos.y() + i + 1 > 256) {
                return false;
            }
            boolean clear = true;
            for (int y = pos.y(); y <= pos.y() + 1 + i; y++) {
                int k = 1;
                if (y == pos.y()) {
                    k = 0;
                }
                if (y >= pos.y() + 1 + i - 2) {
                    k = 2;
                }
                for (int x = pos.x() - k; x <= pos.x() + k && clear; x++) {
                    for (int z = pos.z() - k; z <= pos.z() + k && clear; z++) {
                        if (y < 0 || y > 255) {
                            clear = false;
                        } else if (!canReplace(world, new BlockPosition(x, y, z))) {
                            clear = false;
                        }
                    }
                }
            }
            if (!clear) {
                return false;
            }
            if (!isSoil(world, pos.offset(0, -1, 0)) || pos.y() >= 256 - i - 1) {
                return false;
            }
            placeDirt(world, pos.offset(0, -1, 0));
            for (int y = pos.y() - 3 + i; y <= pos.y() + i; y++) {
                int dy = y - (pos.y() + i);
                int radius = 1 - dy / 2;
                for (int x = pos.x() - radius; x <= pos.x() + radius; x++) {
                    int dx = x - pos.x();
                    for (int z = pos.z() - radius; z <= pos.z() + radius; z++) {
                        int dz = z - pos.z();
                        if (Math.abs(dx) != radius || Math.abs(dz) != radius
                                || random.nextInt(2) != 0 && dy != 0) {
                            BlockPosition at = new BlockPosition(x, y, z);
                            if (isAirOrLeaves(world, at)) {
                                world.setBlock(at, leaves(2));
                            }
                        }
                    }
                }
            }
            for (int t = 0; t < i; t++) {
                BlockPosition at = pos.offset(0, t, 0);
                if (isAirOrLeaves(world, at)) {
                    world.setBlock(at, log(2));
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------ the giant jungle

    /**
     * The giant jungle (GiantJungleTreeFeature 1:1 — the 2×2 form, the
     * grow's {@code (true, 10, 20, jungle)}): the 10+rand(3)+rand(20)
     * height, the top canopy, the branch rings with their angle rolls,
     * the 2×2 trunk, and the vine arms (the plain vine form — the counted
     * face-band note).
     */
    public static Feature giantJungle(Random unused) {
        return new GiantJungleFeature();
    }

    static final class GiantJungleFeature implements Feature {
        private static final int BASE_HEIGHT = 10;
        private static final int BONUS_HEIGHT = 20;

        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + BASE_HEIGHT;
            if (BONUS_HEIGHT > 1) {
                i += random.nextInt(BONUS_HEIGHT);
            }
            if (!canGrow(world, pos, i)) {
                return false;
            }
            // The top canopy (placeLeaves lines 112-118): the strict rings
            // 3/4/5 from the top down.
            for (int dy = -2; dy <= 0; dy++) {
                placeLeavesRingStrict(world, pos.offset(0, i + dy, 0), 3 - dy);
            }
            // The branch rings (lines 40-60).
            for (int y = pos.y() + i - 2 - random.nextInt(4); y > pos.y() + i / 2;
                    y -= 2 + random.nextInt(4)) {
                float angle = random.nextFloat() * (float) Math.PI * 2.0f;
                int bx = pos.x() + (int) (1.5f + Math.cos(angle) * 4.0f);
                int bz = pos.z() + (int) (1.5f + Math.sin(angle) * 4.0f);
                for (int arm = 0; arm < 5; arm++) {
                    bx = pos.x() + (int) (1.5f + Math.cos(angle) * arm);
                    bz = pos.z() + (int) (1.5f + Math.sin(angle) * arm);
                    world.setBlock(new BlockPosition(bx, y - 3 + arm / 2, bz), log(3));
                }
                int depth = 1 + random.nextInt(2);
                for (int ly = y - depth; ly <= y; ly++) {
                    placeLeavesRing(world, new BlockPosition(bx, ly, bz), 1 - (ly - y));
                }
            }
            // The trunk (lines 62-108): the base cell + the 2×2 with the
            // vine arms.
            for (int t = 0; t < i; t++) {
                BlockPosition at = pos.offset(0, t, 0);
                if (canReplace(world, at)) {
                    world.setBlock(at, log(3));
                    if (t > 0) {
                        placeVine(world, random, at.offset(-1, 0, 0));
                        placeVine(world, random, at.offset(0, 0, -1));
                    }
                }
                if (t < i - 1) {
                    BlockPosition east = at.offset(1, 0, 0);
                    if (canReplace(world, east)) {
                        world.setBlock(east, log(3));
                        if (t > 0) {
                            placeVine(world, random, east.offset(1, 0, 0));
                            placeVine(world, random, east.offset(0, 0, -1));
                        }
                    }
                    BlockPosition southEast = at.offset(1, 0, 1);
                    if (canReplace(world, southEast)) {
                        world.setBlock(southEast, log(3));
                        if (t > 0) {
                            placeVine(world, random, southEast.offset(1, 0, 0));
                            placeVine(world, random, southEast.offset(0, 0, 1));
                        }
                    }
                    BlockPosition south = at.offset(0, 0, 1);
                    if (canReplace(world, south)) {
                        world.setBlock(south, log(3));
                        if (t > 0) {
                            placeVine(world, random, south.offset(-1, 0, 0));
                            placeVine(world, random, south.offset(0, 0, 1));
                        }
                    }
                }
            }
            return true;
        }

        /** The giant clearance (GiantTreeFeature.canGrow + the dirt patch). */
        private boolean canGrow(EngineWorld world, BlockPosition pos, int height) {
            if (pos.y() < 1 || pos.y() + height + 1 > 256) {
                return false;
            }
            for (int y = 0; y <= 1 + height; y++) {
                int r = 2;
                if (y == 0) {
                    r = 1;
                } else if (y >= 1 + height - 2) {
                    r = 2;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (pos.y() + y < 0 || pos.y() + y > 255
                                || !canReplace(world, pos.offset(dx, y, dz))) {
                            return false;
                        }
                    }
                }
            }
            BlockPosition below = pos.offset(0, -1, 0);
            if (!isGrassOrDirt(world, below) || pos.y() < 2) {
                return false;
            }
            placeDirt(world, below);
            placeDirt(world, below.offset(1, 0, 0));
            placeDirt(world, below.offset(0, 0, 1));
            placeDirt(world, below.offset(1, 0, 1));
            return true;
        }

        /** placeVine (lines 110-114): the 2-in-3 roll at an air cell. */
        private void placeVine(EngineWorld world, Random random, BlockPosition pos) {
            if (random.nextInt(3) > 0 && world.getBlock(pos).equals(world.airType())) {
                world.setBlock(pos, BuiltinBlocks.VINE);
            }
        }

        /** placeLeavesRingStrict (the giant base's own copy). */
        private void placeLeavesRingStrict(EngineWorld world, BlockPosition center, int radius) {
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius + 1; dx++) {
                for (int dz = -radius; dz <= radius + 1; dz++) {
                    int ax = dx - 1;
                    int az = dz - 1;
                    if (dx * dx + dz * dz <= r2 || ax * ax + az * az <= r2
                            || dx * dx + az * az <= r2 || ax * ax + dz * dz <= r2) {
                        BlockPosition at = center.offset(dx, 0, dz);
                        if (isAirOrLeaves(world, at)) {
                            world.setBlock(at, leaves(3));
                        }
                    }
                }
            }
        }

        /** placeLeavesRing (the soft circle). */
        private void placeLeavesRing(EngineWorld world, BlockPosition center, int radius) {
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= r2) {
                        BlockPosition at = center.offset(dx, 0, dz);
                        if (isAirOrLeaves(world, at)) {
                            world.setBlock(at, leaves(3));
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- the acacia

    /**
     * The acacia (AcaciaTreeFeature 1:1): the 5+rand(3)+rand(3) height,
     * the diagonal trunk with the offset band, the canopy discs, and the
     * second branch's own roll.
     */
    public static Feature acacia(Random unused) {
        return new AcaciaFeature();
    }

    static final class AcaciaFeature implements Feature {
        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + random.nextInt(3) + 5;
            if (pos.y() < 1 || pos.y() + i + 1 > 256) {
                return false;
            }
            boolean clear = true;
            for (int y = pos.y(); y <= pos.y() + 1 + i; y++) {
                int k = 1;
                if (y == pos.y()) {
                    k = 0;
                }
                if (y >= pos.y() + 1 + i - 2) {
                    k = 2;
                }
                for (int x = pos.x() - k; x <= pos.x() + k && clear; x++) {
                    for (int z = pos.z() - k; z <= pos.z() + k && clear; z++) {
                        if (y < 0 || y > 255) {
                            clear = false;
                        } else if (!canReplace(world, new BlockPosition(x, y, z))) {
                            clear = false;
                        }
                    }
                }
            }
            if (!clear) {
                return false;
            }
            if (!isGrassOrDirt(world, pos.offset(0, -1, 0)) || pos.y() >= 256 - i - 1) {
                return false;
            }
            placeDirt(world, pos.offset(0, -1, 0));
            // The main diagonal (lines 77-108).
            int[] horizontal = pickHorizontal(random);
            int diagonalStart = i - random.nextInt(4) - 1;
            int diagonalSteps = 3 - random.nextInt(3);
            int x = pos.x();
            int z = pos.z();
            int topPlacedY = 0;
            for (int t = 0; t < i; t++) {
                int y = pos.y() + t;
                if (t >= diagonalStart && diagonalSteps > 0) {
                    x += horizontal[0];
                    z += horizontal[1];
                    diagonalSteps--;
                }
                BlockPosition at = new BlockPosition(x, y, z);
                if (isAirOrLeaves(world, at)) {
                    world.setBlock(at, log(4));
                    topPlacedY = y;
                }
            }
            // The canopy (lines 110-133): the 7×7 disc with the corner
            // trim, the +1 3×3, and the four arm cells.
            BlockPosition canopy = new BlockPosition(x, topPlacedY, z);
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) != 3 || Math.abs(dz) != 3) {
                        placeAcaciaLeaf(world, canopy.offset(dx, 0, dz));
                    }
                }
            }
            canopy = canopy.offset(0, 1, 0);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    placeAcaciaLeaf(world, canopy.offset(dx, 0, dz));
                }
            }
            placeAcaciaLeaf(world, canopy.offset(2, 0, 0));
            placeAcaciaLeaf(world, canopy.offset(-2, 0, 0));
            placeAcaciaLeaf(world, canopy.offset(0, 0, 2));
            placeAcaciaLeaf(world, canopy.offset(0, 0, -2));
            // The second branch (lines 135-171): a DIFFERENT direction, or
            // the arm never places. The x/z CONTINUE from the main diagonal's
            // end (the reference never resets them) — only the last-placed
            // height resets to the 0 sentinel.
            int[] second = pickHorizontal(random);
            if (second[0] != horizontal[0] || second[1] != horizontal[1]) {
                int start = diagonalStart - random.nextInt(2) - 1;
                int steps = 1 + random.nextInt(3);
                topPlacedY = 0;
                for (int t = start; t < i && steps > 0; steps--) {
                    if (t >= 1) {
                        int y = pos.y() + t;
                        x += second[0];
                        z += second[1];
                        BlockPosition at = new BlockPosition(x, y, z);
                        if (isAirOrLeaves(world, at)) {
                            world.setBlock(at, log(4));
                            topPlacedY = y;
                        }
                    }
                    t++;
                }
                if (topPlacedY > 0) {
                    BlockPosition secondCanopy = new BlockPosition(x, topPlacedY, z);
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (Math.abs(dx) != 2 || Math.abs(dz) != 2) {
                                placeAcaciaLeaf(world, secondCanopy.offset(dx, 0, dz));
                            }
                        }
                    }
                    secondCanopy = secondCanopy.offset(0, 1, 0);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            placeAcaciaLeaf(world, secondCanopy.offset(dx, 0, dz));
                        }
                    }
                }
            }
            return true;
        }

        /** placeLeaves (lines 173-179): the AIR|LEAVES gate. */
        private void placeAcaciaLeaf(EngineWorld world, BlockPosition pos) {
            if (isAirOrLeaves(world, pos)) {
                world.setBlock(pos, leaves(4));
            }
        }

        /** Direction.Plane.HORIZONTAL.pick (a random cardinal). */
        private static int[] pickHorizontal(Random random) {
            return switch (random.nextInt(4)) {
                case 0 -> new int[]{0, 1}; // south
                case 1 -> new int[]{-1, 0}; // west
                case 2 -> new int[]{0, -1}; // north
                default -> new int[]{1, 0}; // east
            };
        }
    }

    // -------------------------------------------------------- the dark oak

    /**
     * The dark oak (DarkOakTreeFeature 1:1 — the 2×2 form): the
     * 6+rand(2)+rand(3) height, the strict isValidHeight clearance, the
     * 2×2 trunk with the diagonal offset, the mirrored canopy patches,
     * and the root arms.
     */
    public static Feature darkOak(Random unused) {
        return new DarkOakFeature();
    }

    static final class DarkOakFeature implements Feature {
        @Override
        public boolean place(EngineWorld world, Random random, BlockPosition pos) {
            int i = random.nextInt(3) + random.nextInt(2) + 6;
            int baseX = pos.x();
            int baseY = pos.y();
            int baseZ = pos.z();
            if (baseY < 1 || baseY + i + 1 >= 256) {
                return false;
            }
            BlockPosition below = pos.offset(0, -1, 0);
            if (!isGrassOrDirt(world, below)) {
                return false;
            }
            if (!isValidHeight(world, pos, i)) {
                return false;
            }
            placeDirt(world, below);
            placeDirt(world, below.offset(1, 0, 0));
            placeDirt(world, below.offset(0, 0, 1));
            placeDirt(world, below.offset(1, 0, 1));
            int[] horizontal = AcaciaFeature.pickHorizontal(random);
            int diagonalStart = i - random.nextInt(4);
            int diagonalSteps = 2 - random.nextInt(3);
            int x = baseX;
            int z = baseZ;
            int topY = baseY + i - 1;
            // The trunk (lines 74-96): the 2×2 column with the diagonal offset.
            for (int t = 0; t < i; t++) {
                if (t >= diagonalStart && diagonalSteps > 0) {
                    x += horizontal[0];
                    z += horizontal[1];
                    diagonalSteps--;
                }
                int y = baseY + t;
                BlockPosition at = new BlockPosition(x, y, z);
                if (isAirOrLeaves(world, at)) {
                    placeDarkLog(world, at);
                    placeDarkLog(world, at.offset(1, 0, 0));
                    placeDarkLog(world, at.offset(0, 0, 1));
                    placeDarkLog(world, at.offset(1, 0, 1));
                }
            }
            // The canopy (lines 98-130): the mirrored patches around the
            // top of the trunk.
            for (int dx = -2; dx <= 0; dx++) {
                for (int dz = -2; dz <= 0; dz++) {
                    placeDarkLeaf(world, x + dx, topY - 1, z + dz);
                    placeDarkLeaf(world, 1 + x - dx, topY - 1, z + dz);
                    placeDarkLeaf(world, x + dx, topY - 1, 1 + z - dz);
                    placeDarkLeaf(world, 1 + x - dx, topY - 1, 1 + z - dz);
                    if ((dx > -2 || dz > -1) && (dx != -1 || dz != -2)) {
                        placeDarkLeaf(world, x + dx, topY, z + dz);
                        placeDarkLeaf(world, 1 + x - dx, topY, z + dz);
                        placeDarkLeaf(world, x + dx, topY, 1 + z - dz);
                        placeDarkLeaf(world, 1 + x - dx, topY, 1 + z - dz);
                    }
                }
            }
            if (random.nextBoolean()) {
                placeDarkLeaf(world, x, topY + 2, z);
                placeDarkLeaf(world, x + 1, topY + 2, z);
                placeDarkLeaf(world, x + 1, topY + 2, z + 1);
                placeDarkLeaf(world, x, topY + 2, z + 1);
            }
            for (int dx = -3; dx <= 4; dx++) {
                for (int dz = -3; dz <= 4; dz++) {
                    if ((dx != -3 || dz != -3)
                            && (dx != -3 || dz != 4)
                            && (dx != 4 || dz != -3)
                            && (dx != 4 || dz != 4)
                            && (Math.abs(dx) < 3 || Math.abs(dz) < 3)) {
                        placeDarkLeaf(world, x + dx, topY, z + dz);
                    }
                }
            }
            // The root arms (lines 132-161): the outside ring's 2-in-3
            // roll, the downward log column, and the root's leaf discs.
            for (int dx = -1; dx <= 2; dx++) {
                for (int dz = -1; dz <= 2; dz++) {
                    if ((dx < 0 || dx > 1 || dz < 0 || dz > 1) && random.nextInt(3) <= 0) {
                        int depth = random.nextInt(3) + 2;
                        for (int d = 0; d < depth; d++) {
                            placeDarkLog(world, new BlockPosition(baseX + dx, topY - d - 1, baseZ + dz));
                        }
                        for (int lx = -1; lx <= 1; lx++) {
                            for (int lz = -1; lz <= 1; lz++) {
                                placeDarkLeaf(world, x + dx + lx, topY, z + dz + lz);
                            }
                        }
                        for (int lx = -2; lx <= 2; lx++) {
                            for (int lz = -2; lz <= 2; lz++) {
                                if (Math.abs(lx) != 2 || Math.abs(lz) != 2) {
                                    placeDarkLeaf(world, x + dx + lx, topY - 1, z + dz + lz);
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        /** isValidHeight (lines 143-174): the 0/1/2 radius column over the height. */
        private boolean isValidHeight(EngineWorld world, BlockPosition pos, int height) {
            for (int y = 0; y <= height + 1; y++) {
                int r = 1;
                if (y == 0) {
                    r = 0;
                }
                if (y >= height - 1) {
                    r = 2;
                }
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (!canReplace(world, pos.offset(dx, y, dz))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /** placeLog (lines 176-181): the canReplace gate. */
        private void placeDarkLog(EngineWorld world, BlockPosition pos) {
            if (canReplace(world, pos)) {
                world.setBlock(pos, log(5));
            }
        }

        /** placeLeaves (lines 183-189): the AIR-only gate. */
        private void placeDarkLeaf(EngineWorld world, int x, int y, int z) {
            BlockPosition at = new BlockPosition(x, y, z);
            if (world.getBlock(at).equals(world.airType())) {
                world.setBlock(at, leaves(5));
            }
        }
    }
}
