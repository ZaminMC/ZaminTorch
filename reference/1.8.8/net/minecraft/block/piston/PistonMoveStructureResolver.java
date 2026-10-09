package net.minecraft.block.piston;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBaseBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class PistonMoveStructureResolver {
    private final World world;
    private final BlockPos pistonPos;
    private final BlockPos startPos;
    private final Direction moveDir;
    private final List<BlockPos> toMove = Lists.newArrayList();
    private final List<BlockPos> toBreak = Lists.newArrayList();

    public PistonMoveStructureResolver(World world, BlockPos pistonPos, Direction pistonDir, boolean extend) {
        this.world = world;
        this.pistonPos = pistonPos;
        if (extend) {
            this.moveDir = pistonDir;
            this.startPos = pistonPos.offset(pistonDir);
        } else {
            this.moveDir = pistonDir.getOpposite();
            this.startPos = pistonPos.offset(pistonDir, 2);
        }
    }

    public boolean resolve() {
        this.toMove.clear();
        this.toBreak.clear();
        Block block = this.world.getBlockState(this.startPos).getBlock();
        if (!PistonBaseBlock.canMoveBlock(block, this.world, this.startPos, this.moveDir, false)) {
            if (block.getPistonMoveBehavior() != 1) {
                return false;
            }

            this.toBreak.add(this.startPos);
            return true;
        } else {
            if (!this.addColumn(this.startPos)) {
                return false;
            }

            for (int i = 0; i < this.toMove.size(); i++) {
                BlockPos blockpos = this.toMove.get(i);
                if (this.world.getBlockState(blockpos).getBlock() == Blocks.SLIME && !this.addNeighborColumns(blockpos)) {
                    return false;
                }
            }

            return true;
        }
    }

    private boolean addColumn(BlockPos pos) {
        Block block = this.world.getBlockState(pos).getBlock();
        if (block.getMaterial() == Material.AIR) {
            return true;
        }

        if (!PistonBaseBlock.canMoveBlock(block, this.world, pos, this.moveDir, false)) {
            return true;
        }

        if (pos.equals(this.pistonPos)) {
            return true;
        }

        if (this.toMove.contains(pos)) {
            return true;
        }

        int i = 1;
        if (i + this.toMove.size() > 12) {
            return false;
        }

        while (block == Blocks.SLIME) {
            BlockPos blockpos = pos.offset(this.moveDir.getOpposite(), i);
            block = this.world.getBlockState(blockpos).getBlock();
            if (block.getMaterial() == Material.AIR
                || !PistonBaseBlock.canMoveBlock(block, this.world, blockpos, this.moveDir, false)
                || blockpos.equals(this.pistonPos)) {
                break;
            }

            if (++i + this.toMove.size() > 12) {
                return false;
            }
        }

        int i1 = 0;

        for (int j = i - 1; j >= 0; j--) {
            this.toMove.add(pos.offset(this.moveDir.getOpposite(), j));
            i1++;
        }

        int j1 = 1;

        while (true) {
            BlockPos blockpos1 = pos.offset(this.moveDir, j1);
            int k = this.toMove.indexOf(blockpos1);
            if (k > -1) {
                this.insertColumn(i1, k);

                for (int l = 0; l <= k + i1; l++) {
                    BlockPos blockpos2 = this.toMove.get(l);
                    if (this.world.getBlockState(blockpos2).getBlock() == Blocks.SLIME && !this.addNeighborColumns(blockpos2)) {
                        return false;
                    }
                }

                return true;
            }

            block = this.world.getBlockState(blockpos1).getBlock();
            if (block.getMaterial() == Material.AIR) {
                return true;
            }

            if (!PistonBaseBlock.canMoveBlock(block, this.world, blockpos1, this.moveDir, true) || blockpos1.equals(this.pistonPos)) {
                return false;
            }

            if (block.getPistonMoveBehavior() == 1) {
                this.toBreak.add(blockpos1);
                return true;
            }

            if (this.toMove.size() >= 12) {
                return false;
            }

            this.toMove.add(blockpos1);
            i1++;
            j1++;
        }
    }

    private void insertColumn(int length, int index) {
        List<BlockPos> list = Lists.newArrayList();
        List<BlockPos> list1 = Lists.newArrayList();
        List<BlockPos> list2 = Lists.newArrayList();
        list.addAll(this.toMove.subList(0, index));
        list1.addAll(this.toMove.subList(this.toMove.size() - length, this.toMove.size()));
        list2.addAll(this.toMove.subList(index, this.toMove.size() - length));
        this.toMove.clear();
        this.toMove.addAll(list);
        this.toMove.addAll(list1);
        this.toMove.addAll(list2);
    }

    private boolean addNeighborColumns(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() != this.moveDir.getAxis() && !this.addColumn(pos.offset(direction))) {
                return false;
            }
        }

        return true;
    }

    public List<BlockPos> getToMove() {
        return this.toMove;
    }

    public List<BlockPos> getToBreak() {
        return this.toBreak;
    }
}
