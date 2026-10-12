package net.minecraft.entity.ai.goal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.WheatBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class VillagerFarmGoal extends GoToBlockGoal {
    private final VillagerEntity villager;
    private boolean hasPlantableItems;
    private boolean hasRoomForHarvest;
    private int harvestDelay;

    public VillagerFarmGoal(VillagerEntity villager, double speed) {
        super(villager, speed, 16);
        this.villager = villager;
    }

    @Override
    public boolean canStart() {
        if (this.cooldown <= 0) {
            if (!this.villager.world.getGameRules().getBoolean("mobGriefing")) {
                return false;
            }

            this.harvestDelay = -1;
            this.hasPlantableItems = this.villager.hasPlantableItems();
            this.hasRoomForHarvest = this.villager.hasRoomForHarvest();
        }

        return super.canStart();
    }

    @Override
    public boolean shouldContinue() {
        return this.harvestDelay >= 0 && super.shouldContinue();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public void tick() {
        super.tick();
        this.villager
            .getLookControl()
            .lookAt(this.target.getX() + 0.5, this.target.getY() + 1, this.target.getZ() + 0.5, 10.0F, this.villager.getLookPitchSpeed());
        if (this.hasReachedTarget()) {
            World world = this.villager.world;
            BlockPos blockpos = this.target.up();
            BlockState blockstate = world.getBlockState(blockpos);
            Block block = blockstate.getBlock();
            if (this.harvestDelay == 0 && block instanceof WheatBlock && blockstate.get(WheatBlock.AGE) == 7) {
                world.breakBlock(blockpos, true);
            } else if (this.harvestDelay == 1 && block == Blocks.AIR) {
                SimpleInventory simpleinventory = this.villager.getVillagerInventory();

                for (int i = 0; i < simpleinventory.getSize(); i++) {
                    ItemStack itemstack = simpleinventory.getItem(i);
                    boolean flag = false;
                    if (itemstack != null) {
                        if (itemstack.getItem() == Items.WHEAT_SEEDS) {
                            world.setBlockState(blockpos, Blocks.WHEAT.defaultState(), 3);
                            flag = true;
                        } else if (itemstack.getItem() == Items.POTATO) {
                            world.setBlockState(blockpos, Blocks.POTATOES.defaultState(), 3);
                            flag = true;
                        } else if (itemstack.getItem() == Items.CARROT) {
                            world.setBlockState(blockpos, Blocks.CARROTS.defaultState(), 3);
                            flag = true;
                        }
                    }

                    if (flag) {
                        itemstack.size--;
                        if (itemstack.size <= 0) {
                            simpleinventory.setItem(i, null);
                        }
                        break;
                    }
                }
            }

            this.harvestDelay = -1;
            this.cooldown = 10;
        }
    }

    @Override
    protected boolean isValidTarget(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (block == Blocks.FARMLAND) {
            pos = pos.up();
            BlockState blockstate = world.getBlockState(pos);
            block = blockstate.getBlock();
            if (block instanceof WheatBlock
                && blockstate.get(WheatBlock.AGE) == 7
                && this.hasRoomForHarvest
                && (this.harvestDelay == 0 || this.harvestDelay < 0)) {
                this.harvestDelay = 0;
                return true;
            }

            if (block == Blocks.AIR && this.hasPlantableItems && (this.harvestDelay == 1 || this.harvestDelay < 0)) {
                this.harvestDelay = 1;
                return true;
            }
        }

        return false;
    }
}
