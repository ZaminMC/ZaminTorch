package net.minecraft.entity.living.mob.monster;

import net.minecraft.block.Block;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EndermiteEntity extends MonsterEntity {
    private int lifeTime = 0;
    private boolean playerSpawned = false;

    public EndermiteEntity(World world) {
        super(world);
        this.xpDrop = 3;
        this.setSize(0.4F, 0.3F);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, PlayerEntity.class, 1.0, false));
        this.goalSelector.addGoal(3, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(7, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, true));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public float getEyeHeight() {
        return 0.1F;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(8.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(2.0);
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    protected String getAmbientSound() {
        return "mob.silverfish.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.silverfish.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.silverfish.kill";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.silverfish.step", 0.15F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return null;
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.lifeTime = nbt.getInt("Lifetime");
        this.playerSpawned = nbt.getBoolean("PlayerSpawned");
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("Lifetime", this.lifeTime);
        nbt.putBoolean("PlayerSpawned", this.playerSpawned);
    }

    @Override
    public void tick() {
        this.bodyYaw = this.yaw;
        super.tick();
    }

    public boolean isPlayerSpawned() {
        return this.playerSpawned;
    }

    public void setPlayerSpawned(boolean playerSpawned) {
        this.playerSpawned = playerSpawned;
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (this.world.isClient) {
            for (int i = 0; i < 2; i++) {
                this.world
                    .addParticle(
                        ParticleType.PORTAL,
                        this.x + (this.random.nextDouble() - 0.5) * this.width,
                        this.y + this.random.nextDouble() * this.height,
                        this.z + (this.random.nextDouble() - 0.5) * this.width,
                        (this.random.nextDouble() - 0.5) * 2.0,
                        -this.random.nextDouble(),
                        (this.random.nextDouble() - 0.5) * 2.0
                    );
            }
        } else {
            if (!this.isPersistent()) {
                this.lifeTime++;
            }

            if (this.lifeTime >= 2400) {
                this.remove();
            }
        }
    }

    @Override
    protected boolean canSpawnAtLightLevel() {
        return true;
    }

    @Override
    public boolean canSpawn() {
        if (super.canSpawn()) {
            PlayerEntity playerentity = this.world.getNearestPlayer(this, 5.0);
            return playerentity == null;
        } else {
            return false;
        }
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }
}
