package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.CreeperIgniteGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class CreeperEntity extends MonsterEntity {
    private int lastFuse;
    private int fuse;
    private int fuseTime = 30;
    private int explosionRadius = 3;
    private int mobHeadDropCount = 0;

    public CreeperEntity(World world) {
        super(world);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, new CreeperIgniteGoal(this));
        this.goalSelector.addGoal(3, new FleeEntityGoal<>(this, OcelotEntity.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(6, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.addGoal(2, new RevengeGoal(this, false));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
    }

    @Override
    public int getSafeFallDistance() {
        return this.getAttackTarget() == null ? 3 : 3 + (int)(this.getHealth() - 1.0F);
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        super.takeFallDamage(distance, damageMultiplier);
        this.fuse = (int)(this.fuse + distance * 1.5F);
        if (this.fuse > this.fuseTime - 5) {
            this.fuse = this.fuseTime - 5;
        }
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)-1);
        this.syncedData.register(17, (byte)0);
        this.syncedData.register(18, (byte)0);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        if (this.syncedData.getByte(17) == 1) {
            nbt.putBoolean("powered", true);
        }

        nbt.putShort("Fuse", (short)this.fuseTime);
        nbt.putByte("ExplosionRadius", (byte)this.explosionRadius);
        nbt.putBoolean("ignited", this.getIgnited());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.syncedData.update(17, (byte)(nbt.getBoolean("powered") ? 1 : 0));
        if (nbt.contains("Fuse", 99)) {
            this.fuseTime = nbt.getShort("Fuse");
        }

        if (nbt.contains("ExplosionRadius", 99)) {
            this.explosionRadius = nbt.getByte("ExplosionRadius");
        }

        if (nbt.getBoolean("ignited")) {
            this.setIgnited();
        }
    }

    @Override
    public void tick() {
        if (this.isAlive()) {
            this.lastFuse = this.fuse;
            if (this.getIgnited()) {
                this.setFuseDirection(1);
            }

            int i = this.getFuseDirection();
            if (i > 0 && this.fuse == 0) {
                this.playSound("creeper.primed", 1.0F, 0.5F);
            }

            this.fuse += i;
            if (this.fuse < 0) {
                this.fuse = 0;
            }

            if (this.fuse >= this.fuseTime) {
                this.fuse = this.fuseTime;
                this.explode();
            }
        }

        super.tick();
    }

    @Override
    protected String getHurtSound() {
        return "mob.creeper.say";
    }

    @Override
    protected String getDeathSound() {
        return "mob.creeper.death";
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getAttacker() instanceof SkeletonEntity) {
            int i = Item.getId(Items.RECORD_13);
            int j = Item.getId(Items.RECORD_WAIT);
            int k = i + this.random.nextInt(j - i + 1);
            this.dropItem(Item.byId(k), 1);
        } else if (source.getAttacker() instanceof CreeperEntity
            && source.getAttacker() != this
            && ((CreeperEntity)source.getAttacker()).isCharged()
            && ((CreeperEntity)source.getAttacker()).shouldDropMobHead()) {
            ((CreeperEntity)source.getAttacker()).addMobHeadDrop();
            this.dropItem(new ItemStack(Items.SKULL, 1, 4), 0.0F);
        }
    }

    @Override
    public boolean tryDamage(Entity target) {
        return true;
    }

    public boolean isCharged() {
        return this.syncedData.getByte(17) == 1;
    }

    public float getFuse(float tickDelta) {
        return (this.lastFuse + (this.fuse - this.lastFuse) * tickDelta) / (this.fuseTime - 2);
    }

    @Override
    protected Item getDropItem() {
        return Items.GUNPOWDER;
    }

    /**
     * Returns the direction of fuse movement, -1 for decreasing, 1 for increasing.
     */
    public int getFuseDirection() {
        return this.syncedData.getByte(16);
    }

    public void setFuseDirection(int dir) {
        this.syncedData.update(16, (byte)dir);
    }

    @Override
    public void struckByLightning(LightningBoltEntity lightning) {
        super.struckByLightning(lightning);
        this.syncedData.update(17, (byte)1);
    }

    @Override
    protected boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.FLINT_AND_STEEL) {
            this.world.playSound(this.x + 0.5, this.y + 0.5, this.z + 0.5, "fire.ignite", 1.0F, this.random.nextFloat() * 0.4F + 0.8F);
            player.swingArm();
            if (!this.world.isClient) {
                this.setIgnited();
                itemstack.takeDamageAndBreak(1, player);
                return true;
            }
        }

        return super.interactMob(player);
    }

    private void explode() {
        if (!this.world.isClient) {
            boolean flag = this.world.getGameRules().getBoolean("mobGriefing");
            float f = this.isCharged() ? 2.0F : 1.0F;
            this.world.explode(this, this.x, this.y, this.z, this.explosionRadius * f, flag);
            this.remove();
        }
    }

    public boolean getIgnited() {
        return this.syncedData.getByte(18) != 0;
    }

    public void setIgnited() {
        this.syncedData.update(18, (byte)1);
    }

    public boolean shouldDropMobHead() {
        return this.mobHeadDropCount < 1 && this.world.getGameRules().getBoolean("doMobLoot");
    }

    public void addMobHeadDrop() {
        this.mobHeadDropCount++;
    }
}
