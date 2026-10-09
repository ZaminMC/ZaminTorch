package net.minecraft.entity.living.mob.passive.animal;

import net.minecraft.block.Block;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class CowEntity extends AnimalEntity {
    public CowEntity(World world) {
        super(world);
        this.setSize(0.9F, 1.3F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new EscapeDangerGoal(this, 2.0));
        this.goalSelector.addGoal(2, new AnimalBreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25, Items.WHEAT, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(7, new LookAroundGoal(this));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(10.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.2F);
    }

    @Override
    protected String getAmbientSound() {
        return "mob.cow.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.cow.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.cow.hurt";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.cow.step", 0.15F, 1.0F);
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected Item getDropItem() {
        return Items.LEATHER;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3) + this.random.nextInt(1 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.LEATHER, 1);
        }

        i = this.random.nextInt(3) + 1 + this.random.nextInt(1 + lootingMultiplier);

        for (int k = 0; k < i; k++) {
            if (this.isOnFire()) {
                this.dropItem(Items.COOKED_BEEF, 1);
            } else {
                this.dropItem(Items.BEEF, 1);
            }
        }
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.BUCKET && !player.abilities.creativeMode && !this.isBaby()) {
            if (itemstack.size-- == 1) {
                player.inventory.setItem(player.inventory.selectedSlot, new ItemStack(Items.MILK_BUCKET));
            } else if (!player.inventory.addItem(new ItemStack(Items.MILK_BUCKET))) {
                player.dropItem(new ItemStack(Items.MILK_BUCKET, 1, 0), false);
            }

            return true;
        } else {
            return super.interactMob(player);
        }
    }

    public CowEntity makeChild(PassiveEntity passiveEntity) {
        return new CowEntity(this.world);
    }

    @Override
    public float getEyeHeight() {
        return this.height;
    }
}
