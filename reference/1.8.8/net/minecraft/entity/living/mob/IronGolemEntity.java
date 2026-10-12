package net.minecraft.entity.living.mob;

import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.GoToEntityTargetGoal;
import net.minecraft.entity.ai.goal.IronGolemLookGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.TrackIronGolemTargetGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageAtNightGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.village.Village;

public class IronGolemEntity extends GolemEntity {
    private int mobTickCooldown;
    Village village;
    private int attackTicksLeft;
    private int lookingAtVillagerTicksLeft;

    public IronGolemEntity(World world) {
        super(world);
        this.setSize(1.4F, 2.9F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(2, new GoToEntityTargetGoal(this, 0.9, 32.0F));
        this.goalSelector.addGoal(3, new WanderThroughVillageAtNightGoal(this, 0.6, true));
        this.goalSelector.addGoal(4, new WanderThroughVillageGoal(this, 1.0));
        this.goalSelector.addGoal(5, new IronGolemLookGoal(this));
        this.goalSelector.addGoal(6, new WanderAroundGoal(this, 0.6));
        this.goalSelector.addGoal(7, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new TrackIronGolemTargetGoal(this));
        this.targetSelector.addGoal(2, new RevengeGoal(this, false));
        this.targetSelector.addGoal(3, new IronGolemEntity.ActiveTargetGoal<>(this, MobEntity.class, 10, false, true, Monster.VISIBLE_MONSTER_FILTER));
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)0);
    }

    @Override
    protected void mobAiTick() {
        if (--this.mobTickCooldown <= 0) {
            this.mobTickCooldown = 70 + this.random.nextInt(50);
            this.village = this.world.getVillages().getNearestVillage(new BlockPos(this), 32);
            if (this.village == null) {
                this.resetVillageRadius();
            } else {
                BlockPos blockpos = this.village.getCenter();
                this.setVillagePosAndRadius(blockpos, (int)(this.village.getRadius() * 0.6F));
            }
        }

        super.mobAiTick();
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(100.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
    }

    @Override
    protected int updateBreathUnderwater(int breath) {
        return breath;
    }

    @Override
    protected void pushAway(Entity entity) {
        if (entity instanceof Monster && !(entity instanceof CreeperEntity) && this.getRandom().nextInt(20) == 0) {
            this.setAttackTarget((LivingEntity)entity);
        }

        super.pushAway(entity);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (this.attackTicksLeft > 0) {
            this.attackTicksLeft--;
        }

        if (this.lookingAtVillagerTicksLeft > 0) {
            this.lookingAtVillagerTicksLeft--;
        }

        if (this.velocityX * this.velocityX + this.velocityZ * this.velocityZ > 2.5000003E-7F && this.random.nextInt(5) == 0) {
            int i = MathHelper.floor(this.x);
            int j = MathHelper.floor(this.y - 0.2F);
            int k = MathHelper.floor(this.z);
            BlockState blockstate = this.world.getBlockState(new BlockPos(i, j, k));
            Block block = blockstate.getBlock();
            if (block.getMaterial() != Material.AIR) {
                this.world
                    .addParticle(
                        ParticleType.BLOCK_CRACK,
                        this.x + (this.random.nextFloat() - 0.5) * this.width,
                        this.getShape().minY + 0.1,
                        this.z + (this.random.nextFloat() - 0.5) * this.width,
                        4.0 * (this.random.nextFloat() - 0.5),
                        0.5,
                        (this.random.nextFloat() - 0.5) * 4.0,
                        Block.serialize(blockstate)
                    );
            }
        }
    }

    @Override
    public boolean canAttack(Class<? extends LivingEntity> type) {
        return (!this.isPlayerCreated() || !PlayerEntity.class.isAssignableFrom(type)) && type != CreeperEntity.class && super.canAttack(type);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("PlayerCreated", this.isPlayerCreated());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setPlayerCreated(nbt.getBoolean("PlayerCreated"));
    }

    @Override
    public boolean tryDamage(Entity target) {
        this.attackTicksLeft = 10;
        this.world.doEntityEvent(this, (byte)4);
        boolean flag = target.takeDamage(DamageSource.mob(this), 7 + this.random.nextInt(15));
        if (flag) {
            target.velocityY += 0.4F;
            this.damageEntity(this, target);
        }

        this.playSound("mob.irongolem.throw", 1.0F, 1.0F);
        return flag;
    }

    @Override
    public void doEvent(byte event) {
        if (event == 4) {
            this.attackTicksLeft = 10;
            this.playSound("mob.irongolem.throw", 1.0F, 1.0F);
        } else if (event == 11) {
            this.lookingAtVillagerTicksLeft = 400;
        } else {
            super.doEvent(event);
        }
    }

    public Village getVillage() {
        return this.village;
    }

    public int getAttackTicksLeft() {
        return this.attackTicksLeft;
    }

    public void setLookingAtVillager(boolean lookingAtVillager) {
        this.lookingAtVillagerTicksLeft = lookingAtVillager ? 400 : 0;
        this.world.doEntityEvent(this, (byte)11);
    }

    @Override
    protected String getHurtSound() {
        return "mob.irongolem.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.irongolem.death";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.irongolem.walk", 1.0F, 1.0F);
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3);

        for (int j = 0; j < i; j++) {
            this.dropItem(Item.byBlock(Blocks.RED_FLOWER), 1, FlowerBlock.Type.POPPY.getId());
        }

        int l = 3 + this.random.nextInt(3);

        for (int k = 0; k < l; k++) {
            this.dropItem(Items.IRON_INGOT, 1);
        }
    }

    public int getLookingAtVillagerTicks() {
        return this.lookingAtVillagerTicksLeft;
    }

    public boolean isPlayerCreated() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    public void setPlayerCreated(boolean playerCreated) {
        byte b0 = this.syncedData.getByte(16);
        if (playerCreated) {
            this.syncedData.update(16, (byte)(b0 | 1));
        } else {
            this.syncedData.update(16, (byte)(b0 & -2));
        }
    }

    @Override
    public void die(DamageSource source) {
        if (!this.isPlayerCreated() && this.attackingPlayer != null && this.village != null) {
            this.village.updateReputation(this.attackingPlayer.getName(), -5);
        }

        super.die(source);
    }

    static class ActiveTargetGoal<T extends LivingEntity> extends net.minecraft.entity.ai.goal.ActiveTargetGoal<T> {
        public ActiveTargetGoal(PathFinderMobEntity pathFinderMobEntity, Class<T> class_, int i, boolean bl, boolean bl2, Predicate<? super T> predicate) {
            super(pathFinderMobEntity, class_, i, bl, bl2, predicate);
            this.targetFilter = new Predicate<T>() {
                public boolean apply(T livingEntity) {
                    if (predicate != null && !predicate.apply(livingEntity)) {
                        return false;
                    }

                    if (livingEntity instanceof CreeperEntity) {
                        return false;
                    }

                    if (livingEntity instanceof PlayerEntity) {
                        double d0 = ActiveTargetGoal.this.getFollowRange();
                        if (livingEntity.isSneaking()) {
                            d0 *= 0.8F;
                        }

                        if (livingEntity.isInvisible()) {
                            float f = ((PlayerEntity)livingEntity).getArmorEquippedRatio();
                            if (f < 0.1F) {
                                f = 0.1F;
                            }

                            d0 *= 0.7F * f;
                        }

                        if (livingEntity.distanceTo(pathFinderMobEntity) > d0) {
                            return false;
                        }
                    }

                    return ActiveTargetGoal.this.canTarget(livingEntity, false);
                }
            };
        }
    }
}
