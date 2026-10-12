package net.minecraft.entity.living.mob.monster;

import java.util.Calendar;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AvoidSunlightGoal;
import net.minecraft.entity.ai.goal.EscapeSunlightGoal;
import net.minecraft.entity.ai.goal.FleeEntityGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.dimension.NetherDimension;

public class SkeletonEntity extends MonsterEntity implements RangedAttackMob {
    private ProjectileAttackGoal rangeAttackGoal = new ProjectileAttackGoal(this, 1.0, 20, 60, 15.0F);
    private MeleeAttackGoal meleeAttackGoal = new MeleeAttackGoal(this, PlayerEntity.class, 1.2, false);

    public SkeletonEntity(World world) {
        super(world);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, new AvoidSunlightGoal(this));
        this.goalSelector.addGoal(3, new EscapeSunlightGoal(this, 1.0));
        this.goalSelector.addGoal(3, new FleeEntityGoal<>(this, WolfEntity.class, 6.0F, 1.0, 1.2));
        this.goalSelector.addGoal(4, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(6, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, false));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.addGoal(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
        if (world != null && !world.isClient) {
            this.updateAttackType();
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(13, new Byte((byte)0));
    }

    @Override
    protected String getAmbientSound() {
        return "mob.skeleton.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.skeleton.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.skeleton.death";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.skeleton.step", 0.15F, 1.0F);
    }

    @Override
    public boolean tryDamage(Entity target) {
        if (super.tryDamage(target)) {
            if (this.getType() == 1 && target instanceof LivingEntity) {
                ((LivingEntity)target).addStatusEffect(new StatusEffectInstance(StatusEffect.WITHER.id, 200));
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public void mobTick() {
        if (this.world.isSunny() && !this.world.isClient) {
            float f = this.getBrightness(1.0F);
            BlockPos blockpos = new BlockPos(this.x, Math.round(this.y), this.z);
            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && this.world.hasSkyAccess(blockpos)) {
                boolean flag = true;
                ItemStack itemstack = this.getEquipment(4);
                if (itemstack != null) {
                    if (itemstack.isDamageable()) {
                        itemstack.setDamage(itemstack.getDamage() + this.random.nextInt(2));
                        if (itemstack.getDamage() >= itemstack.getMaxDamage()) {
                            this.onBrokenItem(itemstack);
                            this.setEquipment(4, null);
                        }
                    }

                    flag = false;
                }

                if (flag) {
                    this.setOnFireFor(8);
                }
            }
        }

        if (this.world.isClient && this.getType() == 1) {
            this.setSize(0.72F, 2.535F);
        }

        super.mobTick();
    }

    @Override
    public void rideTick() {
        super.rideTick();
        if (this.vehicle instanceof PathFinderMobEntity) {
            PathFinderMobEntity pathfindermobentity = (PathFinderMobEntity)this.vehicle;
            this.bodyYaw = pathfindermobentity.bodyYaw;
        }
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getSource() instanceof ArrowEntity && source.getAttacker() instanceof PlayerEntity) {
            PlayerEntity playerentity = (PlayerEntity)source.getAttacker();
            double d0 = playerentity.x - this.x;
            double d1 = playerentity.z - this.z;
            if (d0 * d0 + d1 * d1 >= 2500.0) {
                playerentity.incrementStat(Achievements.KILL_SKELETON_FROM_DISTANCE);
            }
        } else if (source.getAttacker() instanceof CreeperEntity
            && ((CreeperEntity)source.getAttacker()).isCharged()
            && ((CreeperEntity)source.getAttacker()).shouldDropMobHead()) {
            ((CreeperEntity)source.getAttacker()).addMobHeadDrop();
            this.dropItem(new ItemStack(Items.SKULL, 1, this.getType() == 1 ? 1 : 0), 0.0F);
        }
    }

    @Override
    protected Item getDropItem() {
        return Items.ARROW;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        if (this.getType() == 1) {
            int i = this.random.nextInt(3 + lootingMultiplier) - 1;

            for (int j = 0; j < i; j++) {
                this.dropItem(Items.COAL, 1);
            }
        } else {
            int k = this.random.nextInt(3 + lootingMultiplier);

            for (int i1 = 0; i1 < k; i1++) {
                this.dropItem(Items.ARROW, 1);
            }
        }

        int l = this.random.nextInt(3 + lootingMultiplier);

        for (int j1 = 0; j1 < l; j1++) {
            this.dropItem(Items.BONE, 1);
        }
    }

    @Override
    protected void dropRareItem() {
        if (this.getType() == 1) {
            this.dropItem(new ItemStack(Items.SKULL, 1, 1), 0.0F);
        }
    }

    @Override
    protected void addRandomEquipment(LocalDifficulty difficulty) {
        super.addRandomEquipment(difficulty);
        this.setEquipment(0, new ItemStack(Items.BOW));
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        if (this.world.dimension instanceof NetherDimension && this.getRandom().nextInt(5) > 0) {
            this.goalSelector.addGoal(4, this.meleeAttackGoal);
            this.setType(1);
            this.setEquipment(0, new ItemStack(Items.STONE_SWORD));
            this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(4.0);
        } else {
            this.goalSelector.addGoal(4, this.rangeAttackGoal);
            this.addRandomEquipment(localDifficulty);
            this.enchantEquipment(localDifficulty);
        }

        this.setCanPickupLoot(this.random.nextFloat() < 0.55F * localDifficulty.getMultiplier());
        if (this.getEquipment(4) == null) {
            Calendar calendar = this.world.getCalendar();
            if (calendar.get(2) + 1 == 10 && calendar.get(5) == 31 && this.random.nextFloat() < 0.25F) {
                this.setEquipment(4, new ItemStack(this.random.nextFloat() < 0.1F ? Blocks.LIT_PUMPKIN : Blocks.PUMPKIN));
                this.inventoryDropChances[4] = 0.0F;
            }
        }

        return data;
    }

    public void updateAttackType() {
        this.goalSelector.removeGoal(this.meleeAttackGoal);
        this.goalSelector.removeGoal(this.rangeAttackGoal);
        ItemStack itemstack = this.getDisplayItemInHand();
        if (itemstack != null && itemstack.getItem() == Items.BOW) {
            this.goalSelector.addGoal(4, this.rangeAttackGoal);
        } else {
            this.goalSelector.addGoal(4, this.meleeAttackGoal);
        }
    }

    @Override
    public void doRangedAttack(LivingEntity target, float range) {
        ArrowEntity arrowentity = new ArrowEntity(this.world, this, target, 1.6F, 14 - this.world.getDifficulty().getId() * 4);
        int i = EnchantmentHelper.getLevel(Enchantment.POWER.id, this.getDisplayItemInHand());
        int j = EnchantmentHelper.getLevel(Enchantment.PUNCH.id, this.getDisplayItemInHand());
        arrowentity.setDamage(range * 2.0F + (this.random.nextGaussian() * 0.25 + this.world.getDifficulty().getId() * 0.11F));
        if (i > 0) {
            arrowentity.setDamage(arrowentity.getDamage() + i * 0.5 + 0.5);
        }

        if (j > 0) {
            arrowentity.setPunchLevel(j);
        }

        if (EnchantmentHelper.getLevel(Enchantment.FLAME.id, this.getDisplayItemInHand()) > 0 || this.getType() == 1) {
            arrowentity.setOnFireFor(100);
        }

        this.playSound("random.bow", 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.world.addEntity(arrowentity);
    }

    public int getType() {
        return this.syncedData.getByte(13);
    }

    public void setType(int type) {
        this.syncedData.update(13, (byte)type);
        this.immuneToFire = type == 1;
        if (type == 1) {
            this.setSize(0.72F, 2.535F);
        } else {
            this.setSize(0.6F, 1.95F);
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("SkeletonType", 99)) {
            int i = nbt.getByte("SkeletonType");
            this.setType(i);
        }

        this.updateAttackType();
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putByte("SkeletonType", (byte)this.getType());
    }

    @Override
    public void setEquipment(int slot, ItemStack item) {
        super.setEquipment(slot, item);
        if (!this.world.isClient && slot == 0) {
            this.updateAttackType();
        }
    }

    @Override
    public float getEyeHeight() {
        return this.getType() == 1 ? super.getEyeHeight() : 1.74F;
    }

    @Override
    public double getRideHeight() {
        return this.isBaby() ? 0.0 : -0.35;
    }
}
