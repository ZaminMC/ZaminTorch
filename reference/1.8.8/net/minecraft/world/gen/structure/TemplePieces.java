package net.minecraft.world.gen.structure;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.TripwireHookBlock;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class TemplePieces {
    public static void register() {
        StructureRegistry.registerPiece(TemplePieces.DesertPyramid.class, "TeDP");
        StructureRegistry.registerPiece(TemplePieces.JungleTemple.class, "TeJP");
        StructureRegistry.registerPiece(TemplePieces.WitchHut.class, "TeSH");
    }

    public static class DesertPyramid extends TemplePieces.ScatteredStructurePiece {
        private boolean[] hasChest = new boolean[4];
        private static final List<LootEntry> LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.DIAMOND, 0, 1, 3, 3),
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
            new LootEntry(Items.GOLD_INGOT, 0, 2, 7, 15),
            new LootEntry(Items.EMERALD, 0, 1, 3, 2),
            new LootEntry(Items.BONE, 0, 4, 6, 20),
            new LootEntry(Items.ROTTEN_FLESH, 0, 3, 7, 16),
            new LootEntry(Items.SADDLE, 0, 1, 1, 3),
            new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 1)
        );

        public DesertPyramid() {
        }

        public DesertPyramid(Random random, int x, int z) {
            super(random, x, 64, z, 21, 15, 21);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("hasPlacedChest0", this.hasChest[0]);
            nbt.putBoolean("hasPlacedChest1", this.hasChest[1]);
            nbt.putBoolean("hasPlacedChest2", this.hasChest[2]);
            nbt.putBoolean("hasPlacedChest3", this.hasChest[3]);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasChest[0] = nbt.getBoolean("hasPlacedChest0");
            this.hasChest[1] = nbt.getBoolean("hasPlacedChest1");
            this.hasChest[2] = nbt.getBoolean("hasPlacedChest2");
            this.hasChest[3] = nbt.getBoolean("hasPlacedChest3");
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            this.fillWithOutline(
                world, bounds, 0, -4, 0, this.width - 1, 0, this.depth - 1, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );

            for (int i = 1; i <= 9; i++) {
                this.fillWithOutline(
                    world, bounds, i, i, i, this.width - 1 - i, i, this.depth - 1 - i, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
                );
                this.fillWithOutline(
                    world, bounds, i + 1, i, i + 1, this.width - 2 - i, i, this.depth - 2 - i, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false
                );
            }

            for (int j2 = 0; j2 < this.width; j2++) {
                for (int j = 0; j < this.depth; j++) {
                    int k = -5;
                    this.fillColumnDown(world, Blocks.SANDSTONE.defaultState(), j2, k, j, bounds);
                }
            }

            int k2 = this.postProcessBlockMetadata(Blocks.SANDSTONE_STAIRS, 3);
            int l2 = this.postProcessBlockMetadata(Blocks.SANDSTONE_STAIRS, 2);
            int i3 = this.postProcessBlockMetadata(Blocks.SANDSTONE_STAIRS, 0);
            int l = this.postProcessBlockMetadata(Blocks.SANDSTONE_STAIRS, 1);
            int i1 = ~DyeColor.ORANGE.getMetadata() & 15;
            int j1 = ~DyeColor.BLUE.getMetadata() & 15;
            this.fillWithOutline(world, bounds, 0, 0, 0, 4, 9, 4, Blocks.SANDSTONE.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 10, 1, 3, 10, 3, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), 2, 10, 0, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(l2), 2, 10, 4, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(i3), 0, 10, 2, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(l), 4, 10, 2, bounds);
            this.fillWithOutline(world, bounds, this.width - 5, 0, 0, this.width - 1, 9, 4, Blocks.SANDSTONE.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(
                world, bounds, this.width - 4, 10, 1, this.width - 2, 10, 3, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), this.width - 3, 10, 0, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(l2), this.width - 3, 10, 4, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(i3), this.width - 5, 10, 2, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(l), this.width - 1, 10, 2, bounds);
            this.fillWithOutline(world, bounds, 8, 0, 0, 12, 4, 4, Blocks.SANDSTONE.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, 1, 0, 11, 3, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 9, 1, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 9, 2, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 9, 3, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 10, 3, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 11, 3, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 11, 2, 1, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 11, 1, 1, bounds);
            this.fillWithOutline(world, bounds, 4, 1, 1, 8, 3, 3, Blocks.SANDSTONE.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 4, 1, 2, 8, 2, 2, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 12, 1, 1, 16, 3, 3, Blocks.SANDSTONE.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, 12, 1, 2, 16, 2, 2, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(
                world, bounds, 5, 4, 5, this.width - 6, 4, this.depth - 6, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.fillWithOutline(world, bounds, 9, 4, 9, 11, 4, 11, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(
                world,
                bounds,
                8,
                1,
                8,
                8,
                3,
                8,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                12,
                1,
                8,
                12,
                3,
                8,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                8,
                1,
                12,
                8,
                3,
                12,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                12,
                1,
                12,
                12,
                3,
                12,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(world, bounds, 1, 1, 5, 4, 4, 11, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.fillWithOutline(
                world, bounds, this.width - 5, 1, 5, this.width - 2, 4, 11, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.fillWithOutline(world, bounds, 6, 7, 9, 6, 7, 11, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.fillWithOutline(
                world, bounds, this.width - 7, 7, 9, this.width - 7, 7, 11, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.fillWithOutline(
                world,
                bounds,
                5,
                5,
                9,
                5,
                7,
                11,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                this.width - 6,
                5,
                9,
                this.width - 6,
                7,
                11,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.setBlockState(world, Blocks.AIR.defaultState(), 5, 5, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 5, 6, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 6, 6, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), this.width - 6, 5, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), this.width - 6, 6, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), this.width - 7, 6, 10, bounds);
            this.fillWithOutline(world, bounds, 2, 4, 4, 2, 6, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, this.width - 3, 4, 4, this.width - 3, 6, 4, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), 2, 4, 5, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), 2, 3, 4, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), this.width - 3, 4, 5, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(k2), this.width - 3, 3, 4, bounds);
            this.fillWithOutline(world, bounds, 1, 1, 3, 2, 2, 3, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.fillWithOutline(
                world, bounds, this.width - 3, 1, 3, this.width - 2, 2, 3, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.defaultState(), 1, 1, 2, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.defaultState(), this.width - 2, 1, 2, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SAND.getId()), 1, 2, 2, bounds);
            this.setBlockState(world, Blocks.STONE_SLAB.getStateFromMetadata(StoneSlabBlock.Variant.SAND.getId()), this.width - 2, 2, 2, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(l), 2, 1, 2, bounds);
            this.setBlockState(world, Blocks.SANDSTONE_STAIRS.getStateFromMetadata(i3), this.width - 3, 1, 2, bounds);
            this.fillWithOutline(world, bounds, 4, 3, 5, 4, 3, 18, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.fillWithOutline(
                world, bounds, this.width - 5, 3, 5, this.width - 5, 3, 17, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false
            );
            this.fillWithOutline(world, bounds, 3, 1, 5, 4, 2, 16, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.fillWithOutline(world, bounds, this.width - 6, 1, 5, this.width - 5, 2, 16, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);

            for (int k1 = 5; k1 <= 17; k1 += 2) {
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 4, 1, k1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 4, 2, k1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), this.width - 5, 1, k1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), this.width - 5, 2, k1, bounds);
            }

            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 10, 0, 7, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 10, 0, 8, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 9, 0, 9, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 11, 0, 9, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 8, 0, 10, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 12, 0, 10, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 7, 0, 10, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 13, 0, 10, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 9, 0, 11, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 11, 0, 11, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 10, 0, 12, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 10, 0, 13, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(j1), 10, 0, 10, bounds);

            for (int j3 = 0; j3 <= this.width - 1; j3 += this.width - 1) {
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 2, 1, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 2, 2, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 2, 3, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 3, 1, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 3, 2, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 3, 3, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 4, 1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), j3, 4, 2, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 4, 3, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 5, 1, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 5, 2, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 5, 3, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 6, 1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), j3, 6, 2, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 6, 3, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 7, 1, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 7, 2, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), j3, 7, 3, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 8, 1, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 8, 2, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), j3, 8, 3, bounds);
            }

            for (int k3 = 2; k3 <= this.width - 3; k3 += this.width - 3 - 2) {
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 - 1, 2, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3, 2, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 + 1, 2, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 - 1, 3, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3, 3, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 + 1, 3, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 - 1, 4, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), k3, 4, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 + 1, 4, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 - 1, 5, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3, 5, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 + 1, 5, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 - 1, 6, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), k3, 6, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 + 1, 6, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 - 1, 7, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3, 7, 0, bounds);
                this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), k3 + 1, 7, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 - 1, 8, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3, 8, 0, bounds);
                this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), k3 + 1, 8, 0, bounds);
            }

            this.fillWithOutline(
                world,
                bounds,
                8,
                4,
                0,
                12,
                6,
                0,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.setBlockState(world, Blocks.AIR.defaultState(), 8, 6, 0, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 12, 6, 0, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 9, 5, 0, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 10, 5, 0, bounds);
            this.setBlockState(world, Blocks.STAINED_HARDENED_CLAY.getStateFromMetadata(i1), 11, 5, 0, bounds);
            this.fillWithOutline(
                world,
                bounds,
                8,
                -14,
                8,
                12,
                -11,
                12,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                8,
                -10,
                8,
                12,
                -10,
                12,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                8,
                -9,
                8,
                12,
                -9,
                12,
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()),
                false
            );
            this.fillWithOutline(world, bounds, 8, -8, 8, 12, -1, 12, Blocks.SANDSTONE.defaultState(), Blocks.SANDSTONE.defaultState(), false);
            this.fillWithOutline(world, bounds, 9, -11, 9, 11, -1, 11, Blocks.AIR.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.STONE_PRESSURE_PLATE.defaultState(), 10, -11, 10, bounds);
            this.fillWithOutline(world, bounds, 9, -13, 9, 11, -13, 11, Blocks.TNT.defaultState(), Blocks.AIR.defaultState(), false);
            this.setBlockState(world, Blocks.AIR.defaultState(), 8, -11, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 8, -10, 10, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 7, -10, 10, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 7, -11, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 12, -11, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 12, -10, 10, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 13, -10, 10, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 13, -11, 10, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, -11, 8, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, -10, 8, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 10, -10, 7, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 10, -11, 7, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, -11, 12, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, -10, 12, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.CHISELED.getId()), 10, -10, 13, bounds);
            this.setBlockState(world, Blocks.SANDSTONE.getStateFromMetadata(SandstoneBlock.Type.SMOOTH.getId()), 10, -11, 13, bounds);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (!this.hasChest[direction.getIdHorizontal()]) {
                    int l1 = direction.getOffsetX() * 2;
                    int i2 = direction.getOffsetZ() * 2;
                    this.hasChest[direction.getIdHorizontal()] = this.placeChestWithLoot(
                        world,
                        bounds,
                        random,
                        10 + l1,
                        -11,
                        10 + i2,
                        LootEntry.addAll(LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)),
                        2 + random.nextInt(5)
                    );
                }
            }

            return true;
        }
    }

    public static class JungleTemple extends TemplePieces.ScatteredStructurePiece {
        private boolean hasMainChest;
        private boolean hasHiddenChest;
        private boolean hasPrimaryTrap;
        private boolean hasSecondaryTrap;
        private static final List<LootEntry> TREASURE_LOOT_ENTRIES = Lists.newArrayList(
            new LootEntry(Items.DIAMOND, 0, 1, 3, 3),
            new LootEntry(Items.IRON_INGOT, 0, 1, 5, 10),
            new LootEntry(Items.GOLD_INGOT, 0, 2, 7, 15),
            new LootEntry(Items.EMERALD, 0, 1, 3, 2),
            new LootEntry(Items.BONE, 0, 4, 6, 20),
            new LootEntry(Items.ROTTEN_FLESH, 0, 3, 7, 16),
            new LootEntry(Items.SADDLE, 0, 1, 1, 3),
            new LootEntry(Items.IRON_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.GOLDEN_HORSE_ARMOR, 0, 1, 1, 1),
            new LootEntry(Items.DIAMOND_HORSE_ARMOR, 0, 1, 1, 1)
        );
        private static final List<LootEntry> TRAP_LOOT_ENTRIES = Lists.newArrayList(new LootEntry(Items.ARROW, 0, 2, 7, 30));
        private static TemplePieces.JungleTemple.CobblestonePicker COBBLESTONE_PICKER = new TemplePieces.JungleTemple.CobblestonePicker();

        public JungleTemple() {
        }

        public JungleTemple(Random random, int x, int z) {
            super(random, x, 64, z, 12, 10, 15);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("placedMainChest", this.hasMainChest);
            nbt.putBoolean("placedHiddenChest", this.hasHiddenChest);
            nbt.putBoolean("placedTrap1", this.hasPrimaryTrap);
            nbt.putBoolean("placedTrap2", this.hasSecondaryTrap);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasMainChest = nbt.getBoolean("placedMainChest");
            this.hasHiddenChest = nbt.getBoolean("placedHiddenChest");
            this.hasPrimaryTrap = nbt.getBoolean("placedTrap1");
            this.hasSecondaryTrap = nbt.getBoolean("placedTrap2");
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (!this.updateHPos(world, bounds, 0)) {
                return false;
            }

            int i = this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 3);
            int j = this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 2);
            int k = this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 0);
            int l = this.postProcessBlockMetadata(Blocks.STONE_STAIRS, 1);
            this.fill(world, bounds, 0, -4, 0, this.width - 1, 0, this.depth - 1, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 1, 2, 9, 2, 2, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 1, 12, 9, 2, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 1, 3, 2, 2, 11, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 9, 1, 3, 9, 2, 11, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 1, 3, 1, 10, 6, 1, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 1, 3, 13, 10, 6, 13, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 1, 3, 2, 1, 6, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 10, 3, 2, 10, 6, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 3, 2, 9, 3, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 6, 2, 9, 6, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 3, 7, 3, 8, 7, 11, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 4, 8, 4, 7, 8, 10, false, random, COBBLESTONE_PICKER);
            this.fillAir(world, bounds, 3, 1, 3, 8, 2, 11);
            this.fillAir(world, bounds, 4, 3, 6, 7, 3, 9);
            this.fillAir(world, bounds, 2, 4, 2, 9, 5, 12);
            this.fillAir(world, bounds, 4, 6, 5, 7, 6, 9);
            this.fillAir(world, bounds, 5, 7, 6, 6, 7, 8);
            this.fillAir(world, bounds, 5, 1, 2, 6, 2, 2);
            this.fillAir(world, bounds, 5, 2, 12, 6, 2, 12);
            this.fillAir(world, bounds, 5, 5, 1, 6, 5, 1);
            this.fillAir(world, bounds, 5, 5, 13, 6, 5, 13);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 5, 5, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, 5, 5, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 5, 9, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 10, 5, 9, bounds);

            for (int i1 = 0; i1 <= 14; i1 += 14) {
                this.fill(world, bounds, 2, 4, i1, 2, 5, i1, false, random, COBBLESTONE_PICKER);
                this.fill(world, bounds, 4, 4, i1, 4, 5, i1, false, random, COBBLESTONE_PICKER);
                this.fill(world, bounds, 7, 4, i1, 7, 5, i1, false, random, COBBLESTONE_PICKER);
                this.fill(world, bounds, 9, 4, i1, 9, 5, i1, false, random, COBBLESTONE_PICKER);
            }

            this.fill(world, bounds, 5, 6, 0, 6, 6, 0, false, random, COBBLESTONE_PICKER);

            for (int k1 = 0; k1 <= 11; k1 += 11) {
                for (int j1 = 2; j1 <= 12; j1 += 2) {
                    this.fill(world, bounds, k1, 4, j1, k1, 5, j1, false, random, COBBLESTONE_PICKER);
                }

                this.fill(world, bounds, k1, 6, 5, k1, 6, 5, false, random, COBBLESTONE_PICKER);
                this.fill(world, bounds, k1, 6, 9, k1, 6, 9, false, random, COBBLESTONE_PICKER);
            }

            this.fill(world, bounds, 2, 7, 2, 2, 9, 2, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 9, 7, 2, 9, 9, 2, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 2, 7, 12, 2, 9, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 9, 7, 12, 9, 9, 12, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 4, 9, 4, 4, 9, 4, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 7, 9, 4, 7, 9, 4, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 4, 9, 10, 4, 9, 10, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 7, 9, 10, 7, 9, 10, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 5, 9, 7, 6, 9, 7, false, random, COBBLESTONE_PICKER);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 5, 9, 6, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 6, 9, 6, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(j), 5, 9, 8, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(j), 6, 9, 8, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 4, 0, 0, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 5, 0, 0, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 6, 0, 0, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 7, 0, 0, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 4, 1, 8, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 4, 2, 9, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 4, 3, 10, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 7, 1, 8, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 7, 2, 9, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(i), 7, 3, 10, bounds);
            this.fill(world, bounds, 4, 1, 9, 4, 1, 9, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 7, 1, 9, 7, 1, 9, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 4, 1, 10, 7, 2, 10, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 5, 4, 5, 6, 4, 5, false, random, COBBLESTONE_PICKER);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(k), 4, 4, 5, bounds);
            this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(l), 7, 4, 5, bounds);

            for (int l1 = 0; l1 < 4; l1++) {
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(j), 5, 0 - l1, 6 + l1, bounds);
                this.setBlockState(world, Blocks.STONE_STAIRS.getStateFromMetadata(j), 6, 0 - l1, 6 + l1, bounds);
                this.fillAir(world, bounds, 5, 0 - l1, 7 + l1, 6, 0 - l1, 9 + l1);
            }

            this.fillAir(world, bounds, 1, -3, 12, 10, -1, 13);
            this.fillAir(world, bounds, 1, -3, 1, 3, -1, 13);
            this.fillAir(world, bounds, 1, -3, 1, 9, -1, 5);

            for (int i2 = 1; i2 <= 13; i2 += 2) {
                this.fill(world, bounds, 1, -3, i2, 1, -2, i2, false, random, COBBLESTONE_PICKER);
            }

            for (int j2 = 2; j2 <= 12; j2 += 2) {
                this.fill(world, bounds, 1, -1, j2, 3, -1, j2, false, random, COBBLESTONE_PICKER);
            }

            this.fill(world, bounds, 2, -2, 1, 5, -2, 1, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 7, -2, 1, 9, -2, 1, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 6, -3, 1, 6, -3, 1, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 6, -1, 1, 6, -1, 1, false, random, COBBLESTONE_PICKER);
            this.setBlockState(
                world,
                Blocks.TRIPWIRE_HOOK
                    .getStateFromMetadata(this.postProcessBlockMetadata(Blocks.TRIPWIRE_HOOK, Direction.EAST.getIdHorizontal()))
                    .set(TripwireHookBlock.ATTACHED, true),
                1,
                -3,
                8,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.TRIPWIRE_HOOK
                    .getStateFromMetadata(this.postProcessBlockMetadata(Blocks.TRIPWIRE_HOOK, Direction.WEST.getIdHorizontal()))
                    .set(TripwireHookBlock.ATTACHED, true),
                4,
                -3,
                8,
                bounds
            );
            this.setBlockState(world, Blocks.TRIPWIRE.defaultState().set(TripwireBlock.ATTACHED, true), 2, -3, 8, bounds);
            this.setBlockState(world, Blocks.TRIPWIRE.defaultState().set(TripwireBlock.ATTACHED, true), 3, -3, 8, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 7, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 6, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 5, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 4, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 3, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 2, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 5, -3, 1, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 4, -3, 1, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 3, -3, 1, bounds);
            if (!this.hasPrimaryTrap) {
                this.hasPrimaryTrap = this.placeDispenserWithLoot(world, bounds, random, 3, -2, 1, Direction.NORTH.getId(), TRAP_LOOT_ENTRIES, 2);
            }

            this.setBlockState(world, Blocks.VINE.getStateFromMetadata(15), 3, -2, 2, bounds);
            this.setBlockState(
                world,
                Blocks.TRIPWIRE_HOOK
                    .getStateFromMetadata(this.postProcessBlockMetadata(Blocks.TRIPWIRE_HOOK, Direction.NORTH.getIdHorizontal()))
                    .set(TripwireHookBlock.ATTACHED, true),
                7,
                -3,
                1,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.TRIPWIRE_HOOK
                    .getStateFromMetadata(this.postProcessBlockMetadata(Blocks.TRIPWIRE_HOOK, Direction.SOUTH.getIdHorizontal()))
                    .set(TripwireHookBlock.ATTACHED, true),
                7,
                -3,
                5,
                bounds
            );
            this.setBlockState(world, Blocks.TRIPWIRE.defaultState().set(TripwireBlock.ATTACHED, true), 7, -3, 2, bounds);
            this.setBlockState(world, Blocks.TRIPWIRE.defaultState().set(TripwireBlock.ATTACHED, true), 7, -3, 3, bounds);
            this.setBlockState(world, Blocks.TRIPWIRE.defaultState().set(TripwireBlock.ATTACHED, true), 7, -3, 4, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 8, -3, 6, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 9, -3, 6, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 9, -3, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 9, -3, 4, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 9, -2, 4, bounds);
            if (!this.hasSecondaryTrap) {
                this.hasSecondaryTrap = this.placeDispenserWithLoot(world, bounds, random, 9, -2, 3, Direction.WEST.getId(), TRAP_LOOT_ENTRIES, 2);
            }

            this.setBlockState(world, Blocks.VINE.getStateFromMetadata(15), 8, -1, 3, bounds);
            this.setBlockState(world, Blocks.VINE.getStateFromMetadata(15), 8, -2, 3, bounds);
            if (!this.hasMainChest) {
                this.hasMainChest = this.placeChestWithLoot(
                    world, bounds, random, 8, -3, 3, LootEntry.addAll(TREASURE_LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)), 2 + random.nextInt(5)
                );
            }

            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 9, -3, 2, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 8, -3, 1, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 4, -3, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 5, -2, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 5, -1, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 6, -3, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 7, -2, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 7, -1, 5, bounds);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 8, -3, 5, bounds);
            this.fill(world, bounds, 9, -1, 1, 9, -1, 5, false, random, COBBLESTONE_PICKER);
            this.fillAir(world, bounds, 8, -3, 8, 10, -1, 10);
            this.setBlockState(world, Blocks.STONE_BRICKS.getStateFromMetadata(StonebrickBlock.CHISELED_ID), 8, -2, 11, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.getStateFromMetadata(StonebrickBlock.CHISELED_ID), 9, -2, 11, bounds);
            this.setBlockState(world, Blocks.STONE_BRICKS.getStateFromMetadata(StonebrickBlock.CHISELED_ID), 10, -2, 11, bounds);
            this.setBlockState(
                world,
                Blocks.LEVER
                    .getStateFromMetadata(LeverBlock.getMetadataUnpowered(Direction.byId(this.postProcessBlockMetadata(Blocks.LEVER, Direction.NORTH.getId())))),
                8,
                -2,
                12,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.LEVER
                    .getStateFromMetadata(LeverBlock.getMetadataUnpowered(Direction.byId(this.postProcessBlockMetadata(Blocks.LEVER, Direction.NORTH.getId())))),
                9,
                -2,
                12,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.LEVER
                    .getStateFromMetadata(LeverBlock.getMetadataUnpowered(Direction.byId(this.postProcessBlockMetadata(Blocks.LEVER, Direction.NORTH.getId())))),
                10,
                -2,
                12,
                bounds
            );
            this.fill(world, bounds, 8, -3, 8, 8, -3, 10, false, random, COBBLESTONE_PICKER);
            this.fill(world, bounds, 10, -3, 8, 10, -3, 10, false, random, COBBLESTONE_PICKER);
            this.setBlockState(world, Blocks.MOSSY_COBBLESTONE.defaultState(), 10, -2, 9, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 8, -2, 9, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 8, -2, 10, bounds);
            this.setBlockState(world, Blocks.REDSTONE_WIRE.defaultState(), 10, -1, 9, bounds);
            this.setBlockState(world, Blocks.STICKY_PISTON.getStateFromMetadata(Direction.UP.getId()), 9, -2, 8, bounds);
            this.setBlockState(
                world,
                Blocks.STICKY_PISTON.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STICKY_PISTON, Direction.WEST.getId())),
                10,
                -2,
                8,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.STICKY_PISTON.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.STICKY_PISTON, Direction.WEST.getId())),
                10,
                -1,
                8,
                bounds
            );
            this.setBlockState(
                world,
                Blocks.REPEATER.getStateFromMetadata(this.postProcessBlockMetadata(Blocks.REPEATER, Direction.NORTH.getIdHorizontal())),
                10,
                -2,
                10,
                bounds
            );
            if (!this.hasHiddenChest) {
                this.hasHiddenChest = this.placeChestWithLoot(
                    world, bounds, random, 9, -3, 10, LootEntry.addAll(TREASURE_LOOT_ENTRIES, Items.ENCHANTED_BOOK.createLoot(random)), 2 + random.nextInt(5)
                );
            }

            return true;
        }

        static class CobblestonePicker extends StructurePiece.BlockPicker {
            private CobblestonePicker() {
            }

            @Override
            public void pick(Random randomm, int x, int y, int z, boolean isNonAir) {
                if (randomm.nextFloat() < 0.4F) {
                    this.state = Blocks.COBBLESTONE.defaultState();
                } else {
                    this.state = Blocks.MOSSY_COBBLESTONE.defaultState();
                }
            }
        }
    }

    abstract static class ScatteredStructurePiece extends StructurePiece {
        protected int width;
        protected int height;
        protected int depth;
        protected int hPos = -1;

        public ScatteredStructurePiece() {
        }

        protected ScatteredStructurePiece(Random random, int x, int y, int z, int width, int height, int depth) {
            super(0);
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.facing = Direction.Plane.HORIZONTAL.pick(random);
            switch (this.facing) {
                case NORTH:
                case SOUTH:
                    this.bounds = new StructureBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1);
                    break;
                default:
                    this.bounds = new StructureBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
            }
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            nbt.putInt("Width", this.width);
            nbt.putInt("Height", this.height);
            nbt.putInt("Depth", this.depth);
            nbt.putInt("HPos", this.hPos);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            this.width = nbt.getInt("Width");
            this.height = nbt.getInt("Height");
            this.depth = nbt.getInt("Depth");
            this.hPos = nbt.getInt("HPos");
        }

        protected boolean updateHPos(World world, StructureBox bounds, int offset) {
            if (this.hPos >= 0) {
                return true;
            }

            int i = 0;
            int j = 0;
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k = this.bounds.minZ; k <= this.bounds.maxZ; k++) {
                for (int l = this.bounds.minX; l <= this.bounds.maxX; l++) {
                    blockpos$mutable.set(l, 64, k);
                    if (bounds.contains(blockpos$mutable)) {
                        i += Math.max(world.getSurfaceHeight(blockpos$mutable).getY(), world.dimension.getMinSpawnY());
                        j++;
                    }
                }
            }

            if (j == 0) {
                return false;
            }

            this.hPos = i / j;
            this.bounds.move(0, this.hPos - this.bounds.minY + offset, 0);
            return true;
        }
    }

    public static class WitchHut extends TemplePieces.ScatteredStructurePiece {
        private boolean hasWitch;

        public WitchHut() {
        }

        public WitchHut(Random random, int x, int z) {
            super(random, x, 64, z, 7, 7, 9);
        }

        @Override
        protected void writeNbt(NbtCompound nbt) {
            super.writeNbt(nbt);
            nbt.putBoolean("Witch", this.hasWitch);
        }

        @Override
        protected void readNbt(NbtCompound nbt) {
            super.readNbt(nbt);
            this.hasWitch = nbt.getBoolean("Witch");
        }

        @Override
        public boolean postProcess(World world, Random random, StructureBox bounds) {
            if (!this.updateHPos(world, bounds, 0)) {
                return false;
            }

            this.fillWithOutline(
                world,
                bounds,
                1,
                1,
                1,
                5,
                1,
                7,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                1,
                4,
                2,
                5,
                4,
                7,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                2,
                1,
                0,
                4,
                1,
                0,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                2,
                2,
                2,
                3,
                3,
                2,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                1,
                2,
                3,
                1,
                3,
                6,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                5,
                2,
                3,
                5,
                3,
                6,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(
                world,
                bounds,
                2,
                2,
                7,
                4,
                3,
                7,
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                Blocks.PLANKS.getStateFromMetadata(PlanksBlock.Variant.SPRUCE.getId()),
                false
            );
            this.fillWithOutline(world, bounds, 1, 0, 2, 1, 3, 2, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 0, 2, 5, 3, 2, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 1, 0, 7, 1, 3, 7, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.fillWithOutline(world, bounds, 5, 0, 7, 5, 3, 7, Blocks.LOG.defaultState(), Blocks.LOG.defaultState(), false);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 2, 3, 2, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 3, 3, 7, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 1, 3, 4, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 5, 3, 4, bounds);
            this.setBlockState(world, Blocks.AIR.defaultState(), 5, 3, 5, bounds);
            this.setBlockState(world, Blocks.FLOWER_POT.defaultState().set(FlowerPotBlock.CONTENTS, FlowerPotBlock.Contents.MUSHROOM_RED), 1, 3, 5, bounds);
            this.setBlockState(world, Blocks.CRAFTING_TABLE.defaultState(), 3, 2, 6, bounds);
            this.setBlockState(world, Blocks.CAULDRON.defaultState(), 4, 2, 6, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 1, 2, 1, bounds);
            this.setBlockState(world, Blocks.FENCE.defaultState(), 5, 2, 1, bounds);
            int i = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 3);
            int j = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 1);
            int k = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 0);
            int l = this.postProcessBlockMetadata(Blocks.OAK_STAIRS, 2);
            this.fillWithOutline(
                world, bounds, 0, 4, 1, 6, 4, 1, Blocks.SPRUCE_STAIRS.getStateFromMetadata(i), Blocks.SPRUCE_STAIRS.getStateFromMetadata(i), false
            );
            this.fillWithOutline(
                world, bounds, 0, 4, 2, 0, 4, 7, Blocks.SPRUCE_STAIRS.getStateFromMetadata(k), Blocks.SPRUCE_STAIRS.getStateFromMetadata(k), false
            );
            this.fillWithOutline(
                world, bounds, 6, 4, 2, 6, 4, 7, Blocks.SPRUCE_STAIRS.getStateFromMetadata(j), Blocks.SPRUCE_STAIRS.getStateFromMetadata(j), false
            );
            this.fillWithOutline(
                world, bounds, 0, 4, 8, 6, 4, 8, Blocks.SPRUCE_STAIRS.getStateFromMetadata(l), Blocks.SPRUCE_STAIRS.getStateFromMetadata(l), false
            );

            for (int i1 = 2; i1 <= 7; i1 += 5) {
                for (int j1 = 1; j1 <= 5; j1 += 4) {
                    this.fillColumnDown(world, Blocks.LOG.defaultState(), j1, -1, i1, bounds);
                }
            }

            if (!this.hasWitch) {
                int l1 = this.transformX(2, 5);
                int i2 = this.transformY(2);
                int k1 = this.transformZ(2, 5);
                if (bounds.contains(new BlockPos(l1, i2, k1))) {
                    this.hasWitch = true;
                    WitchEntity witchentity = new WitchEntity(world);
                    witchentity.setPositionAndAngles(l1 + 0.5, i2, k1 + 0.5, 0.0F, 0.0F);
                    witchentity.initialize(world.getLocalDifficulty(new BlockPos(l1, i2, k1)), null);
                    world.addEntity(witchentity);
                }
            }

            return true;
        }
    }
}
