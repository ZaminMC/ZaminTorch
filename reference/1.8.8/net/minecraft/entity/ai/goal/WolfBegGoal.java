package net.minecraft.entity.ai.goal;

import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class WolfBegGoal extends Goal {
    private WolfEntity wolfEntity;
    private PlayerEntity playerEntity;
    private World world;
    private float begDistance;
    private int timer;

    public WolfBegGoal(WolfEntity wolfEntity, float f) {
        this.wolfEntity = wolfEntity;
        this.world = wolfEntity.world;
        this.begDistance = f;
        this.setControls(2);
    }

    @Override
    public boolean canStart() {
        this.playerEntity = this.world.getNearestPlayer(this.wolfEntity, this.begDistance);
        return this.playerEntity != null && this.isAttractive(this.playerEntity);
    }

    @Override
    public boolean shouldContinue() {
        return this.playerEntity.isAlive()
            && !(this.wolfEntity.squaredDistanceTo(this.playerEntity) > this.begDistance * this.begDistance)
            && this.timer > 0
            && this.isAttractive(this.playerEntity);
    }

    @Override
    public void start() {
        this.wolfEntity.setBegging(true);
        this.timer = 40 + this.wolfEntity.getRandom().nextInt(40);
    }

    @Override
    public void stop() {
        this.wolfEntity.setBegging(false);
        this.playerEntity = null;
    }

    @Override
    public void tick() {
        this.wolfEntity
            .getLookControl()
            .lookAt(
                this.playerEntity.x, this.playerEntity.y + this.playerEntity.getEyeHeight(), this.playerEntity.z, 10.0F, this.wolfEntity.getLookPitchSpeed()
            );
        this.timer--;
    }

    private boolean isAttractive(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        return itemstack != null && (!this.wolfEntity.isTamed() && itemstack.getItem() == Items.BONE || this.wolfEntity.isBreedingItem(itemstack));
    }
}
