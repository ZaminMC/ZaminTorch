package net.minecraft.entity.ai.goal;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class OcelotSitOnBlockGoal extends GoToBlockGoal {
    private final OcelotEntity ocelot;

    public OcelotSitOnBlockGoal(OcelotEntity ocelot, double speed) {
        super(ocelot, speed, 8);
        this.ocelot = ocelot;
    }

    @Override
    public boolean canStart() {
        return this.ocelot.isTamed() && !this.ocelot.isSitting() && super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return super.shouldContinue();
    }

    @Override
    public void start() {
        super.start();
        this.ocelot.getSitGoal().setEnabledWithOwner(false);
    }

    @Override
    public void stop() {
        super.stop();
        this.ocelot.setSitting(false);
    }

    @Override
    public void tick() {
        super.tick();
        this.ocelot.getSitGoal().setEnabledWithOwner(false);
        if (!this.hasReachedTarget()) {
            this.ocelot.setSitting(false);
        } else if (!this.ocelot.isSitting()) {
            this.ocelot.setSitting(true);
        }
    }

    @Override
    protected boolean isValidTarget(World world, BlockPos pos) {
        if (!world.isAir(pos.up())) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block == Blocks.CHEST) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof ChestBlockEntity && ((ChestBlockEntity)blockentity).viewerCount < 1) {
                return true;
            }
        } else {
            if (block == Blocks.LIT_FURNACE) {
                return true;
            }

            if (block == Blocks.BED && blockstate.get(BedBlock.PART) != BedBlock.Part.HEAD) {
                return true;
            }
        }

        return false;
    }
}
