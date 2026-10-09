package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.world.color.BiomeColors;
import net.minecraft.client.world.color.FoliageColors;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class AbstractLeavesBlock extends TranslucentBlock {
    public static final BooleanProperty DECAYABLE = BooleanProperty.of("decayable");
    public static final BooleanProperty CHECK_DECAY = BooleanProperty.of("check_decay");
    int[] decayRegion;
    protected int spriteIndex;
    protected boolean renderCutout;

    public AbstractLeavesBlock() {
        super(Material.LEAVES, false);
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
        this.setStrength(0.2F);
        this.setOpacity(1);
        this.setSounds(GRASS_SOUNDS);
    }

    @Override
    public int getColor() {
        return FoliageColors.get(0.5, 1.0);
    }

    @Override
    public int getColor(BlockState state) {
        return FoliageColors.getDefaultColor();
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return BiomeColors.getFoliageColor(world, pos);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        int i = 1;
        int j = i + 1;
        int k = pos.getX();
        int l = pos.getY();
        int i1 = pos.getZ();
        if (world.isAreaLoaded(new BlockPos(k - j, l - j, i1 - j), new BlockPos(k + j, l + j, i1 + j))) {
            for (int j1 = -i; j1 <= i; j1++) {
                for (int k1 = -i; k1 <= i; k1++) {
                    for (int l1 = -i; l1 <= i; l1++) {
                        BlockPos blockpos = pos.add(j1, k1, l1);
                        BlockState blockstate = world.getBlockState(blockpos);
                        if (blockstate.getBlock().getMaterial() == Material.LEAVES && !blockstate.get(CHECK_DECAY)) {
                            world.setBlockState(blockpos, blockstate.set(CHECK_DECAY, true), 4);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (state.get(CHECK_DECAY) && state.get(DECAYABLE)) {
                int i = 4;
                int j = i + 1;
                int k = pos.getX();
                int l = pos.getY();
                int i1 = pos.getZ();
                int j1 = 32;
                int k1 = j1 * j1;
                int l1 = j1 / 2;
                if (this.decayRegion == null) {
                    this.decayRegion = new int[j1 * j1 * j1];
                }

                if (world.isAreaLoaded(new BlockPos(k - j, l - j, i1 - j), new BlockPos(k + j, l + j, i1 + j))) {
                    BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

                    for (int i2 = -i; i2 <= i; i2++) {
                        for (int j2 = -i; j2 <= i; j2++) {
                            for (int k2 = -i; k2 <= i; k2++) {
                                Block block = world.getBlockState(blockpos$mutable.set(k + i2, l + j2, i1 + k2)).getBlock();
                                if (block != Blocks.LOG && block != Blocks.LOG2) {
                                    if (block.getMaterial() == Material.LEAVES) {
                                        this.decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = -2;
                                    } else {
                                        this.decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = -1;
                                    }
                                } else {
                                    this.decayRegion[(i2 + l1) * k1 + (j2 + l1) * j1 + k2 + l1] = 0;
                                }
                            }
                        }
                    }

                    for (int i3 = 1; i3 <= 4; i3++) {
                        for (int j3 = -i; j3 <= i; j3++) {
                            for (int k3 = -i; k3 <= i; k3++) {
                                for (int l3 = -i; l3 <= i; l3++) {
                                    if (this.decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1] == i3 - 1) {
                                        if (this.decayRegion[(j3 + l1 - 1) * k1 + (k3 + l1) * j1 + l3 + l1] == -2) {
                                            this.decayRegion[(j3 + l1 - 1) * k1 + (k3 + l1) * j1 + l3 + l1] = i3;
                                        }

                                        if (this.decayRegion[(j3 + l1 + 1) * k1 + (k3 + l1) * j1 + l3 + l1] == -2) {
                                            this.decayRegion[(j3 + l1 + 1) * k1 + (k3 + l1) * j1 + l3 + l1] = i3;
                                        }

                                        if (this.decayRegion[(j3 + l1) * k1 + (k3 + l1 - 1) * j1 + l3 + l1] == -2) {
                                            this.decayRegion[(j3 + l1) * k1 + (k3 + l1 - 1) * j1 + l3 + l1] = i3;
                                        }

                                        if (this.decayRegion[(j3 + l1) * k1 + (k3 + l1 + 1) * j1 + l3 + l1] == -2) {
                                            this.decayRegion[(j3 + l1) * k1 + (k3 + l1 + 1) * j1 + l3 + l1] = i3;
                                        }

                                        if (this.decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + (l3 + l1 - 1)] == -2) {
                                            this.decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + (l3 + l1 - 1)] = i3;
                                        }

                                        if (this.decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 + 1] == -2) {
                                            this.decayRegion[(j3 + l1) * k1 + (k3 + l1) * j1 + l3 + l1 + 1] = i3;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                int l2 = this.decayRegion[l1 * k1 + l1 * j1 + l1];
                if (l2 >= 0) {
                    world.setBlockState(pos, state.set(CHECK_DECAY, false), 4);
                } else {
                    this.breakLeaves(world, pos);
                }
            }
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.isRaining(pos.up()) && !World.hasSolidTop(world, pos.down()) && random.nextInt(15) == 1) {
            double d0 = pos.getX() + random.nextFloat();
            double d1 = pos.getY() - 0.05;
            double d2 = pos.getZ() + random.nextFloat();
            world.addParticle(ParticleType.DRIP_WATER, d0, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    private void breakLeaves(World world, BlockPos pos) {
        this.dropItems(world, pos, world.getBlockState(pos), 0);
        world.removeBlock(pos);
    }

    @Override
    public int getBaseDropCount(Random random) {
        return random.nextInt(20) == 0 ? 1 : 0;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.SAPLING);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (!world.isClient) {
            int i = this.getSaplingDropChance(state);
            if (fortuneLevel > 0) {
                i -= 2 << fortuneLevel;
                if (i < 10) {
                    i = 10;
                }
            }

            if (world.random.nextInt(i) == 0) {
                Item item = this.getDropItem(state, world.random, fortuneLevel);
                dropItem(world, pos, new ItemStack(item, 1, this.getDropItemMetadata(state)));
            }

            i = 200;
            if (fortuneLevel > 0) {
                i -= 10 << fortuneLevel;
                if (i < 40) {
                    i = 40;
                }
            }

            this.dropAppleWithChance(world, pos, state, i);
        }
    }

    /**
     * If leaf block has correct metadata, then drop an apple item with 1/chance probability
     */
    protected void dropAppleWithChance(World world, BlockPos pos, BlockState state, int chance) {
    }

    /**
     * Returns the base chance for the block to drop a sapling when broken
     */
    protected int getSaplingDropChance(BlockState state) {
        return 20;
    }

    @Override
    public boolean isSolidRender() {
        return !this.culling;
    }

    public void setCulling(boolean culling) {
        this.renderCutout = culling;
        this.culling = culling;
        this.spriteIndex = culling ? 0 : 1;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return this.renderCutout ? BlockLayer.CUTOUT_MIPPED : BlockLayer.SOLID;
    }

    @Override
    public boolean isViewBlocking() {
        return false;
    }

    public abstract PlanksBlock.Variant getVariant(int metadata);
}
