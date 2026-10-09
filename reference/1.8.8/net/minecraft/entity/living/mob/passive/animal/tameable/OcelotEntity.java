package net.minecraft.entity.living.mob.passive.animal.tameable;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.AttackGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.OcelotSitOnBlockGoal;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.UntamedActiveTargetGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class OcelotEntity extends TameableEntity {
    private FleeEntityGoal<PlayerEntity> fleeGoal;
    private TemptGoal temptGoal;

    public OcelotEntity(World world) {
        super(world);
        this.setSize(0.6F, 0.7F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, this.sitGoal);
        this.goalSelector.addGoal(3, this.temptGoal = new TemptGoal(this, 0.6, Items.FISH, true));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 10.0F, 5.0F));
        this.goalSelector.addGoal(6, new OcelotSitOnBlockGoal(this, 0.8));
        this.goalSelector.addGoal(7, new PounceAtTargetGoal(this, 0.3F));
        this.goalSelector.addGoal(8, new AttackGoal(this));
        this.goalSelector.addGoal(9, new AnimalBreedGoal(this, 0.8));
        this.goalSelector.addGoal(10, new WanderAroundGoal(this, 0.8));
        this.goalSelector.addGoal(11, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
        this.targetSelector.addGoal(1, new UntamedActiveTargetGoal<>(this, ChickenEntity.class, false, null));
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(18, (byte)0);
    }

    @Override
    public void mobAiTick() {
        if (this.getMovementControl().isMoving()) {
            double d0 = this.getMovementControl().getSpeed();
            if (d0 == 0.6) {
                this.setSneaking(true);
                this.setSprinting(false);
            } else if (d0 == 1.33) {
                this.setSneaking(false);
                this.setSprinting(true);
            } else {
                this.setSneaking(false);
                this.setSprinting(false);
            }
        } else {
            this.setSneaking(false);
            this.setSprinting(false);
        }
    }

    @Override
    protected boolean canDespawn() {
        return !this.isTamed() && this.ticks > 2400;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(10.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("CatType", this.getVariant());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setCatVariant(nbt.getInt("CatType"));
    }

    @Override
    protected String getAmbientSound() {
        if (this.isTamed()) {
            if (this.isInLove()) {
                return "mob.cat.purr";
            } else {
                return this.random.nextInt(4) == 0 ? "mob.cat.purreow" : "mob.cat.meow";
            }
        } else {
            return "";
        }
    }

    @Override
    protected String getHurtSound() {
        return "mob.cat.hitt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.cat.hitt";
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
    public boolean tryDamage(Entity target) {
        return target.takeDamage(DamageSource.mob(this), 3.0F);
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        this.sitGoal.setEnabledWithOwner(false);
        return super.takeDamage(source, amount);
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (this.isTamed()) {
            if (this.isOwner(player) && !this.world.isClient && !this.isBreedingItem(itemstack)) {
                this.sitGoal.setEnabledWithOwner(!this.isSitting());
            }
        } else if (this.temptGoal.isActive() && itemstack != null && itemstack.getItem() == Items.FISH && player.squaredDistanceTo(this) < 9.0) {
            if (!player.abilities.creativeMode) {
                itemstack.size--;
            }

            if (itemstack.size <= 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }

            if (!this.world.isClient) {
                if (this.random.nextInt(3) == 0) {
                    this.setTamed(true);
                    this.setCatVariant(1 + this.world.random.nextInt(3));
                    this.setOwnerName(player.getUuid().toString());
                    this.addTamingParticles(true);
                    this.sitGoal.setEnabledWithOwner(true);
                    this.world.doEntityEvent(this, (byte)7);
                } else {
                    this.addTamingParticles(false);
                    this.world.doEntityEvent(this, (byte)6);
                }
            }

            return true;
        }

        return super.interactMob(player);
    }

    public OcelotEntity makeChild(PassiveEntity passiveEntity) {
        OcelotEntity ocelotentity = new OcelotEntity(this.world);
        if (this.isTamed()) {
            ocelotentity.setOwnerName(this.getOwnerName());
            ocelotentity.setTamed(true);
            ocelotentity.setCatVariant(this.getVariant());
        }

        return ocelotentity;
    }

    @Override
    public boolean isBreedingItem(ItemStack item) {
        return item != null && item.getItem() == Items.FISH;
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        if (other == this) {
            return false;
        }

        if (!this.isTamed()) {
            return false;
        }

        if (!(other instanceof OcelotEntity)) {
            return false;
        }

        OcelotEntity ocelotentity = (OcelotEntity)other;
        return ocelotentity.isTamed() && this.isInLove() && ocelotentity.isInLove();
    }

    public int getVariant() {
        return this.syncedData.getByte(18);
    }

    public void setCatVariant(int variant) {
        this.syncedData.update(18, (byte)variant);
    }

    @Override
    public boolean canSpawn() {
        return this.world.random.nextInt(3) != 0;
    }

    @Override
    public boolean isUnobstructed() {
        if (this.world.isUnobstructed(this.getShape(), this)
            && this.world.getCollisions(this, this.getShape()).isEmpty()
            && !this.world.containsLiquid(this.getShape())) {
            BlockPos blockpos = new BlockPos(this.x, this.getShape().minY, this.z);
            if (blockpos.getY() < this.world.getSeaLevel()) {
                return false;
            }

            Block block = this.world.getBlockState(blockpos.down()).getBlock();
            if (block == Blocks.GRASS || block.getMaterial() == Material.LEAVES) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getName() {
        if (this.hasCustomName()) {
            return this.getCustomName();
        } else {
            return this.isTamed() ? I18n.translate("entity.Cat.name") : super.getName();
        }
    }

    @Override
    public void setTamed(boolean tamed) {
        super.setTamed(tamed);
    }

    @Override
    protected void onTamedChanged() {
        if (this.fleeGoal == null) {
            this.fleeGoal = new FleeEntityGoal<>(this, PlayerEntity.class, 16.0F, 0.8, 1.33);
        }

        this.goalSelector.removeGoal(this.fleeGoal);
        if (!this.isTamed()) {
            this.goalSelector.addGoal(4, this.fleeGoal);
        }
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        if (this.world.random.nextInt(7) == 0) {
            for (int i = 0; i < 2; i++) {
                OcelotEntity ocelotentity = new OcelotEntity(this.world);
                ocelotentity.setPositionAndAngles(this.x, this.y, this.z, this.yaw, 0.0F);
                ocelotentity.setBreedingAge(-24000);
                this.world.addEntity(ocelotentity);
            }
        }

        return data;
    }
}
