package net.minecraft.world.gen.feature;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.AbstractLogBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class LargeOakTreeFeature extends AbstractTreeFeature {
    private Random random;
    private World world;
    /**
     * The position of the base of the tree.
     */
    private BlockPos origin = BlockPos.ORIGIN;
    /**
     * Total height of the tree, including the canopy.
     */
    int height;
    /**
     * Height of just the trunk of the tree.
     */
    int trunkHeight;
    /**
     * The portion of the total height of the tree that is taken up by the trunk.
     */
    double trunkScale = 0.618;
    double branchSlope = 0.381;
    double branchLengthScale = 1.0;
    double foliageDensity = 1.0;
    /**
     * The width of the trunk, can be 1 or 2.
     */
    int trunkWidth = 1;
    int maxTrunkHeight = 12;
    int foliageClusterHeight = 4;
    /**
     * Base and tip coordinates for each branch in the tree. The base of a branch is
     * placed at some height on the trunk, so the base x- and z-coordinates match
     * those of the trunk, and do not need to be stored.
     */
    List<LargeOakTreeFeature.BranchPos> branches;

    public LargeOakTreeFeature(boolean bl) {
        super(bl);
    }

    /**
     * Compile an array of branches to place for this tree.
     */
    void makeBranches() {
        this.trunkHeight = (int)(this.height * this.trunkScale);
        if (this.trunkHeight >= this.height) {
            this.trunkHeight = this.height - 1;
        }

        int i = (int)(1.382 + Math.pow(this.foliageDensity * this.height / 13.0, 2.0));
        if (i < 1) {
            i = 1;
        }

        int j = this.origin.getY() + this.trunkHeight;
        int k = this.height - this.foliageClusterHeight;
        this.branches = Lists.newArrayList();
        this.branches.add(new LargeOakTreeFeature.BranchPos(this.origin.up(k), j));

        for (; k >= 0; k--) {
            float f = this.getTreeShape(k);
            if (!(f < 0.0F)) {
                for (int l = 0; l < i; l++) {
                    double d0 = this.branchLengthScale * f * (this.random.nextFloat() + 0.328);
                    double d1 = this.random.nextFloat() * 2.0F * Math.PI;
                    double d2 = d0 * Math.sin(d1) + 0.5;
                    double d3 = d0 * Math.cos(d1) + 0.5;
                    BlockPos blockpos = this.origin.add(d2, k - 1, d3);
                    BlockPos blockpos1 = blockpos.up(this.foliageClusterHeight);
                    if (this.tryBranch(blockpos, blockpos1) == -1) {
                        int i1 = this.origin.getX() - blockpos.getX();
                        int j1 = this.origin.getZ() - blockpos.getZ();
                        double d4 = blockpos.getY() - Math.sqrt(i1 * i1 + j1 * j1) * this.branchSlope;
                        int k1 = d4 > j ? j : (int)d4;
                        BlockPos blockpos2 = new BlockPos(this.origin.getX(), k1, this.origin.getZ());
                        if (this.tryBranch(blockpos2, blockpos) == -1) {
                            this.branches.add(new LargeOakTreeFeature.BranchPos(blockpos, blockpos2.getY()));
                        }
                    }
                }
            }
        }
    }

    /**
     * Place a cluster layer centered at the given coordinates.
     */
    void placeCluster(BlockPos pos, float shape, BlockState clusterBlock) {
        int i = (int)(shape + 0.618);

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k <= i; k++) {
                if (Math.pow(Math.abs(j) + 0.5, 2.0) + Math.pow(Math.abs(k) + 0.5, 2.0) <= shape * shape) {
                    BlockPos blockpos = pos.add(j, 0, k);
                    Material material = this.world.getBlockState(blockpos).getBlock().getMaterial();
                    if (material == Material.AIR || material == Material.LEAVES) {
                        this.setBlockState(this.world, blockpos, clusterBlock);
                    }
                }
            }
        }
    }

    /**
     * Returns the shape of the tree at the given height. The shape controls the
     * size of branches at that height.
     */
    float getTreeShape(int height) {
        if (height < this.height * 0.3F) {
            return -1.0F;
        }

        float f = this.height / 2.0F;
        float f1 = f - height;
        float f2 = MathHelper.sqrt(f * f - f1 * f1);
        if (f1 == 0.0F) {
            f2 = f;
        } else if (Math.abs(f1) >= f) {
            return 0.0F;
        }

        return f2 * 0.5F;
    }

    /**
     * Returns the shape of the cluster at the given layer. The shape controls the
     * size and roundness of the layer.
     */
    float getClusterShape(int layer) {
        if (layer < 0 || layer >= this.foliageClusterHeight) {
            return -1.0F;
        } else {
            return layer != 0 && layer != this.foliageClusterHeight - 1 ? 3.0F : 2.0F;
        }
    }

    /**
     * Place a foliage cluster above the given coordinates.
     */
    void placeFoliageCluster(BlockPos pos) {
        for (int i = 0; i < this.foliageClusterHeight; i++) {
            this.placeCluster(pos.up(i), this.getClusterShape(i), Blocks.LEAVES.defaultState().set(AbstractLeavesBlock.CHECK_DECAY, false));
        }
    }

    void placeBranch(BlockPos from, BlockPos to, Block log) {
        BlockPos blockpos = to.add(-from.getX(), -from.getY(), -from.getZ());
        int i = this.max(blockpos);
        float f = (float)blockpos.getX() / i;
        float f1 = (float)blockpos.getY() / i;
        float f2 = (float)blockpos.getZ() / i;

        for (int j = 0; j <= i; j++) {
            BlockPos blockpos1 = from.add(0.5F + j * f, 0.5F + j * f1, 0.5F + j * f2);
            AbstractLogBlock.LogAxis abstractlogblock$logaxis = this.getLogAxis(from, blockpos1);
            this.setBlockState(this.world, blockpos1, log.defaultState().set(AbstractLogBlock.LOG_AXIS, abstractlogblock$logaxis));
        }
    }

    /**
     * Returns the largest of the absolute values of the given coordinates.
     */
    private int max(BlockPos coordinates) {
        int i = MathHelper.abs(coordinates.getX());
        int j = MathHelper.abs(coordinates.getY());
        int k = MathHelper.abs(coordinates.getZ());
        if (k > i && k > j) {
            return k;
        } else {
            return j > i ? j : i;
        }
    }

    /**
     * Returns an axis based on the angle between the two given positions.
     */
    private AbstractLogBlock.LogAxis getLogAxis(BlockPos from, BlockPos to) {
        AbstractLogBlock.LogAxis abstractlogblock$logaxis = AbstractLogBlock.LogAxis.Y;
        int i = Math.abs(to.getX() - from.getX());
        int j = Math.abs(to.getZ() - from.getZ());
        int k = Math.max(i, j);
        if (k > 0) {
            if (i == k) {
                abstractlogblock$logaxis = AbstractLogBlock.LogAxis.X;
            } else if (j == k) {
                abstractlogblock$logaxis = AbstractLogBlock.LogAxis.Z;
            }
        }

        return abstractlogblock$logaxis;
    }

    void placeFoliage() {
        for (LargeOakTreeFeature.BranchPos largeoaktreefeature$branchpos : this.branches) {
            this.placeFoliageCluster(largeoaktreefeature$branchpos);
        }
    }

    /**
     * Check if branches should be placed at the given height above the base of the tree.
     */
    boolean shouldPlaceBranch(int height) {
        return height >= this.height * 0.2;
    }

    void placeTrunk() {
        BlockPos blockpos = this.origin;
        BlockPos blockpos1 = this.origin.up(this.trunkHeight);
        Block block = Blocks.LOG;
        this.placeBranch(blockpos, blockpos1, block);
        if (this.trunkWidth == 2) {
            this.placeBranch(blockpos.east(), blockpos1.east(), block);
            this.placeBranch(blockpos.east().south(), blockpos1.east().south(), block);
            this.placeBranch(blockpos.south(), blockpos1.south(), block);
        }
    }

    void placeBranches() {
        for (LargeOakTreeFeature.BranchPos largeoaktreefeature$branchpos : this.branches) {
            int i = largeoaktreefeature$branchpos.getBaseY();
            BlockPos blockpos = new BlockPos(this.origin.getX(), i, this.origin.getZ());
            if (!blockpos.equals(largeoaktreefeature$branchpos) && this.shouldPlaceBranch(i - this.origin.getY())) {
                this.placeBranch(blockpos, largeoaktreefeature$branchpos, Blocks.LOG);
            }
        }
    }

    /**
     * Check if a branch can be placed between the two given positions.
     * 
     * @return -1 if the branch can be placed, or the maximum possible length of the
     *         branch along its major axis.
     */
    int tryBranch(BlockPos from, BlockPos to) {
        BlockPos blockpos = to.add(-from.getX(), -from.getY(), -from.getZ());
        int i = this.max(blockpos);
        float f = (float)blockpos.getX() / i;
        float f1 = (float)blockpos.getY() / i;
        float f2 = (float)blockpos.getZ() / i;
        if (i == 0) {
            return -1;
        }

        for (int j = 0; j <= i; j++) {
            BlockPos blockpos1 = from.add(0.5F + j * f, 0.5F + j * f1, 0.5F + j * f2);
            if (!this.canReplace(this.world.getBlockState(blockpos1).getBlock())) {
                return j;
            }
        }

        return -1;
    }

    @Override
    public void prepare() {
        this.foliageClusterHeight = 5;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        this.world = world;
        this.origin = pos;
        this.random = new Random(random.nextLong());
        if (this.height == 0) {
            this.height = 5 + this.random.nextInt(this.maxTrunkHeight);
        }

        if (!this.canPlace()) {
            return false;
        }

        this.makeBranches();
        this.placeFoliage();
        this.placeTrunk();
        this.placeBranches();
        return true;
    }

    private boolean canPlace() {
        Block block = this.world.getBlockState(this.origin.down()).getBlock();
        if (block != Blocks.DIRT && block != Blocks.GRASS && block != Blocks.FARMLAND) {
            return false;
        }

        int i = this.tryBranch(this.origin, this.origin.up(this.height - 1));
        if (i == -1) {
            return true;
        }

        if (i < 6) {
            return false;
        }

        this.height = i;
        return true;
    }

    static class BranchPos extends BlockPos {
        private final int baseY;

        public BranchPos(BlockPos tip, int baseY) {
            super(tip.getX(), tip.getY(), tip.getZ());
            this.baseY = baseY;
        }

        public int getBaseY() {
            return this.baseY;
        }
    }
}
