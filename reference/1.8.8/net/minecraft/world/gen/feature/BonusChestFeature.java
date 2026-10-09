package net.minecraft.world.gen.feature;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.LootEntry;

public class BonusChestFeature extends Feature {
    private final List<LootEntry> lootEntries;
    private final int amountOfLoot;

    public BonusChestFeature(List<LootEntry> lootEntries, int amountOfLoot) {
        this.lootEntries = lootEntries;
        this.amountOfLoot = amountOfLoot;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        Block block;
        while (((block = world.getBlockState(pos).getBlock()).getMaterial() == Material.AIR || block.getMaterial() == Material.LEAVES) && pos.getY() > 1) {
            pos = pos.down();
        }

        if (pos.getY() < 1) {
            return false;
        }

        pos = pos.up();

        for (int i = 0; i < 4; i++) {
            BlockPos blockpos = pos.add(random.nextInt(4) - random.nextInt(4), random.nextInt(3) - random.nextInt(3), random.nextInt(4) - random.nextInt(4));
            if (world.isAir(blockpos) && World.hasSolidTop(world, blockpos.down())) {
                world.setBlockState(blockpos, Blocks.CHEST.defaultState(), 2);
                BlockEntity blockentity = world.getBlockEntity(blockpos);
                if (blockentity instanceof ChestBlockEntity) {
                    LootEntry.addLoot(random, this.lootEntries, (ChestBlockEntity)blockentity, this.amountOfLoot);
                }

                BlockPos blockpos1 = blockpos.east();
                BlockPos blockpos2 = blockpos.west();
                BlockPos blockpos3 = blockpos.north();
                BlockPos blockpos4 = blockpos.south();
                if (world.isAir(blockpos2) && World.hasSolidTop(world, blockpos2.down())) {
                    world.setBlockState(blockpos2, Blocks.TORCH.defaultState(), 2);
                }

                if (world.isAir(blockpos1) && World.hasSolidTop(world, blockpos1.down())) {
                    world.setBlockState(blockpos1, Blocks.TORCH.defaultState(), 2);
                }

                if (world.isAir(blockpos3) && World.hasSolidTop(world, blockpos3.down())) {
                    world.setBlockState(blockpos3, Blocks.TORCH.defaultState(), 2);
                }

                if (world.isAir(blockpos4) && World.hasSolidTop(world, blockpos4.down())) {
                    world.setBlockState(blockpos4, Blocks.TORCH.defaultState(), 2);
                }

                return true;
            }
        }

        return false;
    }
}
