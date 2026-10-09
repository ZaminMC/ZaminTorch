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
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class ChickenEntity extends AnimalEntity {
    public float flapProgress;
    public float flapSpeed;
    public float lastFlapSpeed;
    public float lastFlapProgress;
    public float flapping = 1.0F;
    public int layEggCooldown;
    public boolean hasJockey;

    public ChickenEntity(World world) {
        super(world);
        this.setSize(0.4F, 0.7F);
        this.layEggCooldown = this.random.nextInt(6000) + 6000;
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new EscapeDangerGoal(this, 1.4));
        this.goalSelector.addGoal(2, new AnimalBreedGoal(this, 1.0));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.0, Items.WHEAT_SEEDS, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(7, new LookAroundGoal(this));
    }

    @Override
    public float getEyeHeight() {
        return this.height;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(4.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        this.lastFlapProgress = this.flapProgress;
        this.lastFlapSpeed = this.flapSpeed;
        this.flapSpeed = (float)(this.flapSpeed + (this.onGround ? -1 : 4) * 0.3);
        this.flapSpeed = MathHelper.clamp(this.flapSpeed, 0.0F, 1.0F);
        if (!this.onGround && this.flapping < 1.0F) {
            this.flapping = 1.0F;
        }

        this.flapping = (float)(this.flapping * 0.9);
        if (!this.onGround && this.velocityY < 0.0) {
            this.velocityY *= 0.6;
        }

        this.flapProgress = this.flapProgress + this.flapping * 2.0F;
        if (!this.world.isClient && !this.isBaby() && !this.hasJockey() && --this.layEggCooldown <= 0) {
            this.playSound("mob.chicken.plop", 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.dropItem(Items.EGG, 1);
            this.layEggCooldown = this.random.nextInt(6000) + 6000;
        }
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected String getAmbientSound() {
        return "mob.chicken.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.chicken.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.chicken.hurt";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.chicken.step", 0.15F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return Items.FEATHER;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3) + this.random.nextInt(1 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.FEATHER, 1);
        }

        if (this.isOnFire()) {
            this.dropItem(Items.COOKED_CHICKEN, 1);
        } else {
            this.dropItem(Items.CHICKEN, 1);
        }
    }

    public ChickenEntity makeChild(PassiveEntity passiveEntity) {
        return new ChickenEntity(this.world);
    }

    @Override
    public boolean isBreedingItem(ItemStack item) {
        return item != null && item.getItem() == Items.WHEAT_SEEDS;
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.hasJockey = nbt.getBoolean("IsChickenJockey");
        if (nbt.contains("EggLayTime")) {
            this.layEggCooldown = nbt.getInt("EggLayTime");
        }
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        return this.hasJockey() ? 10 : super.getXpDrop(playerEntity);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("IsChickenJockey", this.hasJockey);
        nbt.putInt("EggLayTime", this.layEggCooldown);
    }

    @Override
    protected boolean canDespawn() {
        return this.hasJockey() && this.rider == null;
    }

    @Override
    public void updateRiderPositon() {
        super.updateRiderPositon();
        float f = MathHelper.sin(this.bodyYaw * (float) Math.PI / 180.0F);
        float f1 = MathHelper.cos(this.bodyYaw * (float) Math.PI / 180.0F);
        float f2 = 0.1F;
        float f3 = 0.0F;
        this.rider.setPosition(this.x + f2 * f, this.y + this.height * 0.5F + this.rider.getRideHeight() + f3, this.z - f2 * f1);
        if (this.rider instanceof LivingEntity) {
            ((LivingEntity)this.rider).bodyYaw = this.bodyYaw;
        }
    }

    public boolean hasJockey() {
        return this.hasJockey;
    }

    public void setHasJockey(boolean hasJockey) {
        this.hasJockey = hasJockey;
    }
}
