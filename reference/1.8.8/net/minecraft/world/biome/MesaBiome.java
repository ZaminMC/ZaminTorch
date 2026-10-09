package net.minecraft.world.biome;

import java.util.Arrays;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.ColoredBlock;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.SandBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.noise.PerlinSimplexNoise;

public class MesaBiome extends Biome {
    private BlockState[] clayLayers;
    private long seed;
    private PerlinSimplexNoise clayPillarNoise;
    private PerlinSimplexNoise claySurfaceNoise;
    private PerlinSimplexNoise clayOffsetNoise;
    private boolean mutated;
    private boolean hasTrees;

    public MesaBiome(int id, boolean mutated, boolean hasTrees) {
        super(id);
        this.mutated = mutated;
        this.hasTrees = hasTrees;
        this.disableRain();
        this.setTemperatureAndDownfall(2.0F, 0.0F);
        this.passiveEntries.clear();
        this.surfaceBlock = Blocks.SAND.defaultState().set(SandBlock.VARIANT, SandBlock.Variant.RED_SAND);
        this.subsurfaceBlock = Blocks.STAINED_HARDENED_CLAY.defaultState();
        this.decorator.treeAttempts = -999;
        this.decorator.deadBushAttempts = 20;
        this.decorator.sugarcaneAttempts = 3;
        this.decorator.cactusAttempts = 5;
        this.decorator.flowerAttempts = 0;
        this.passiveEntries.clear();
        if (hasTrees) {
            this.decorator.treeAttempts = 5;
        }
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        return this.tree;
    }

    @Override
    public int getFoliageColor(BlockPos pos) {
        return 10387789;
    }

    @Override
    public int getGrassColor(BlockPos pos) {
        return 9470285;
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        super.decorate(world, random, pos);
    }

    @Override
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        if (this.clayLayers == null || this.seed != world.getSeed()) {
            this.placeClayLayers(world.getSeed());
        }

        if (this.clayPillarNoise == null || this.claySurfaceNoise == null || this.seed != world.getSeed()) {
            Random randomx = new Random(this.seed);
            this.clayPillarNoise = new PerlinSimplexNoise(randomx, 4);
            this.claySurfaceNoise = new PerlinSimplexNoise(randomx, 1);
        }

        this.seed = world.getSeed();
        double d4 = 0.0;
        if (this.mutated) {
            int i = (x & -16) + (z & 15);
            int j = (z & -16) + (x & 15);
            double d0 = Math.min(Math.abs(depth), this.clayPillarNoise.getValue(i * 0.25, j * 0.25));
            if (d0 > 0.0) {
                double d1 = 0.001953125;
                double d2 = Math.abs(this.claySurfaceNoise.getValue(i * d1, j * d1));
                d4 = d0 * d0 * 2.5;
                double d3 = Math.ceil(d2 * 50.0) + 14.0;
                if (d4 > d3) {
                    d4 = d3;
                }

                d4 += 64.0;
            }
        }

        int j1 = x & 15;
        int k1 = z & 15;
        int l1 = world.getSeaLevel();
        BlockState blockstate = Blocks.STAINED_HARDENED_CLAY.defaultState();
        BlockState blockstate3 = this.subsurfaceBlock;
        int k = (int)(depth / 3.0 + 3.0 + random.nextDouble() * 0.25);
        boolean flag = Math.cos(depth / 3.0 * Math.PI) > 0.0;
        int l = -1;
        boolean flag1 = false;

        for (int i1 = 255; i1 >= 0; i1--) {
            if (blocks.get(k1, i1, j1).getBlock().getMaterial() == Material.AIR && i1 < (int)d4) {
                blocks.set(k1, i1, j1, Blocks.STONE.defaultState());
            }

            if (i1 <= random.nextInt(5)) {
                blocks.set(k1, i1, j1, Blocks.BEDROCK.defaultState());
            } else {
                BlockState blockstate1 = blocks.get(k1, i1, j1);
                if (blockstate1.getBlock().getMaterial() == Material.AIR) {
                    l = -1;
                } else if (blockstate1.getBlock() == Blocks.STONE) {
                    if (l == -1) {
                        flag1 = false;
                        if (k <= 0) {
                            blockstate = null;
                            blockstate3 = Blocks.STONE.defaultState();
                        } else if (i1 >= l1 - 4 && i1 <= l1 + 1) {
                            blockstate = Blocks.STAINED_HARDENED_CLAY.defaultState();
                            blockstate3 = this.subsurfaceBlock;
                        }

                        if (i1 < l1 && (blockstate == null || blockstate.getBlock().getMaterial() == Material.AIR)) {
                            blockstate = Blocks.WATER.defaultState();
                        }

                        l = k + Math.max(0, i1 - l1);
                        if (i1 >= l1 - 1) {
                            if (!this.hasTrees || i1 <= 86 + k * 2) {
                                if (i1 > l1 + 3 + k) {
                                    BlockState blockstate2;
                                    if (i1 < 64 || i1 > 127) {
                                        blockstate2 = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.ORANGE);
                                    } else if (flag) {
                                        blockstate2 = Blocks.HARDENED_CLAY.defaultState();
                                    } else {
                                        blockstate2 = this.getClayLayer(x, i1, z);
                                    }

                                    blocks.set(k1, i1, j1, blockstate2);
                                } else {
                                    blocks.set(k1, i1, j1, this.surfaceBlock);
                                    flag1 = true;
                                }
                            } else if (flag) {
                                blocks.set(k1, i1, j1, Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.COARSE_DIRT));
                            } else {
                                blocks.set(k1, i1, j1, Blocks.GRASS.defaultState());
                            }
                        } else {
                            blocks.set(k1, i1, j1, blockstate3);
                            if (blockstate3.getBlock() == Blocks.STAINED_HARDENED_CLAY) {
                                blocks.set(k1, i1, j1, blockstate3.getBlock().defaultState().set(ColoredBlock.COLOR, DyeColor.ORANGE));
                            }
                        }
                    } else if (l > 0) {
                        l--;
                        if (flag1) {
                            blocks.set(k1, i1, j1, Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.ORANGE));
                        } else {
                            BlockState blockstate4 = this.getClayLayer(x, i1, z);
                            blocks.set(k1, i1, j1, blockstate4);
                        }
                    }
                }
            }
        }
    }

    private void placeClayLayers(long seed) {
        this.clayLayers = new BlockState[64];
        Arrays.fill(this.clayLayers, Blocks.HARDENED_CLAY.defaultState());
        Random random = new Random(seed);
        this.clayOffsetNoise = new PerlinSimplexNoise(random, 1);

        for (int l1 = 0; l1 < 64; l1++) {
            l1 += random.nextInt(5) + 1;
            if (l1 < 64) {
                this.clayLayers[l1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.ORANGE);
            }
        }

        int i2 = random.nextInt(4) + 2;

        for (int i = 0; i < i2; i++) {
            int j = random.nextInt(3) + 1;
            int k = random.nextInt(64);

            for (int l = 0; k + l < 64 && l < j; l++) {
                this.clayLayers[k + l] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.YELLOW);
            }
        }

        int j2 = random.nextInt(4) + 2;

        for (int k2 = 0; k2 < j2; k2++) {
            int i3 = random.nextInt(3) + 2;
            int l3 = random.nextInt(64);

            for (int i1 = 0; l3 + i1 < 64 && i1 < i3; i1++) {
                this.clayLayers[l3 + i1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.BROWN);
            }
        }

        int l2 = random.nextInt(4) + 2;

        for (int j3 = 0; j3 < l2; j3++) {
            int i4 = random.nextInt(3) + 1;
            int k4 = random.nextInt(64);

            for (int j1 = 0; k4 + j1 < 64 && j1 < i4; j1++) {
                this.clayLayers[k4 + j1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.RED);
            }
        }

        int k3 = random.nextInt(3) + 3;
        int j4 = 0;

        for (int l4 = 0; l4 < k3; l4++) {
            int i5 = 1;
            j4 += random.nextInt(16) + 4;

            for (int k1 = 0; j4 + k1 < 64 && k1 < i5; k1++) {
                this.clayLayers[j4 + k1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.WHITE);
                if (j4 + k1 > 1 && random.nextBoolean()) {
                    this.clayLayers[j4 + k1 - 1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.SILVER);
                }

                if (j4 + k1 < 63 && random.nextBoolean()) {
                    this.clayLayers[j4 + k1 + 1] = Blocks.STAINED_HARDENED_CLAY.defaultState().set(ColoredBlock.COLOR, DyeColor.SILVER);
                }
            }
        }
    }

    private BlockState getClayLayer(int x, int y, int z) {
        int i = (int)Math.round(this.clayOffsetNoise.getValue(x * 1.0 / 512.0, x * 1.0 / 512.0) * 2.0);
        return this.clayLayers[(y + i + 64) % 64];
    }

    @Override
    protected Biome mutate(int id) {
        boolean flag = this.id == Biome.MESA.id;
        MesaBiome mesabiome = new MesaBiome(id, flag, this.hasTrees);
        if (!flag) {
            mesabiome.setHeight(HILLS_HEIGHT);
            mesabiome.setName(this.name + " M");
        } else {
            mesabiome.setName(this.name + " (Bryce)");
        }

        mesabiome.setColor(this.biomeColor, true);
        return mesabiome;
    }
}
