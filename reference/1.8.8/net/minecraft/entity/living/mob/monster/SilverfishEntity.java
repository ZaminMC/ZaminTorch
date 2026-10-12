package net.minecraft.entity.living.mob.monster;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SilverfishEntity extends MonsterEntity {
    private SilverfishEntity.WakeUpFriendsGoal wakeUpFriendsGoal;

    public SilverfishEntity(World world) {
        super(world);
        this.setSize(0.4F, 0.3F);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(3, this.wakeUpFriendsGoal = new SilverfishEntity.WakeUpFriendsGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, PlayerEntity.class, 1.0, false));
        this.goalSelector.addGoal(5, new SilverfishEntity.InfestStoneGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, true));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public double getRideHeight() {
        return 0.2;
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
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(1.0);
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
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (source instanceof EntityDamageSource || source == DamageSource.MAGIC) {
            this.wakeUpFriendsGoal.onDamaged();
        }

        return super.takeDamage(source, amount);
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
    public void tick() {
        this.bodyYaw = this.yaw;
        super.tick();
    }

    @Override
    public float getPathfindingFavor(BlockPos pos) {
        return this.world.getBlockState(pos.down()).getBlock() == Blocks.STONE ? 10.0F : super.getPathfindingFavor(pos);
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

    static class InfestStoneGoal extends WanderAroundGoal {
        private final SilverfishEntity silverfish;
        private Direction dir;
        private boolean infest;

        public InfestStoneGoal(SilverfishEntity silverfish) {
            super(silverfish, 1.0, 10);
            this.silverfish = silverfish;
            this.setControls(1);
        }

        @Override
        public boolean canStart() {
            if (this.silverfish.getAttackTarget() != null) {
                return false;
            }

            if (!this.silverfish.getNavigation().isDone()) {
                return false;
            }

            Random random = this.silverfish.getRandom();
            if (random.nextInt(10) == 0) {
                this.dir = Direction.pick(random);
                BlockPos blockpos = new BlockPos(this.silverfish.x, this.silverfish.y + 0.5, this.silverfish.z).offset(this.dir);
                BlockState blockstate = this.silverfish.world.getBlockState(blockpos);
                if (InfestedBlock.canBeInfested(blockstate)) {
                    this.infest = true;
                    return true;
                }
            }

            this.infest = false;
            return super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return !this.infest && super.shouldContinue();
        }

        @Override
        public void start() {
            if (!this.infest) {
                super.start();
            } else {
                World world = this.silverfish.world;
                BlockPos blockpos = new BlockPos(this.silverfish.x, this.silverfish.y + 0.5, this.silverfish.z).offset(this.dir);
                BlockState blockstate = world.getBlockState(blockpos);
                if (InfestedBlock.canBeInfested(blockstate)) {
                    world.setBlockState(
                        blockpos, Blocks.MONSTER_EGG.defaultState().set(InfestedBlock.VARIANT, InfestedBlock.Variant.byHostState(blockstate)), 3
                    );
                    this.silverfish.animateSpawn();
                    this.silverfish.remove();
                }
            }
        }
    }

    static class WakeUpFriendsGoal extends Goal {
        private SilverfishEntity silverfish;
        private int cooldown;

        public WakeUpFriendsGoal(SilverfishEntity silverfish) {
            this.silverfish = silverfish;
        }

        public void onDamaged() {
            if (this.cooldown == 0) {
                this.cooldown = 20;
            }
        }

        @Override
        public boolean canStart() {
            return this.cooldown > 0;
        }

        @Override
        public void tick() {
            this.cooldown--;
            if (this.cooldown <= 0) {
                World world = this.silverfish.world;
                Random random = this.silverfish.getRandom();
                BlockPos blockpos = new BlockPos(this.silverfish);

                for (int i = 0; i <= 5 && i >= -5; i = i <= 0 ? 1 - i : 0 - i) {
                    for (int j = 0; j <= 10 && j >= -10; j = j <= 0 ? 1 - j : 0 - j) {
                        for (int k = 0; k <= 10 && k >= -10; k = k <= 0 ? 1 - k : 0 - k) {
                            BlockPos blockpos1 = blockpos.add(j, i, k);
                            BlockState blockstate = world.getBlockState(blockpos1);
                            if (blockstate.getBlock() == Blocks.MONSTER_EGG) {
                                if (world.getGameRules().getBoolean("mobGriefing")) {
                                    world.breakBlock(blockpos1, true);
                                } else {
                                    world.setBlockState(blockpos1, blockstate.get(InfestedBlock.VARIANT).getHostState(), 3);
                                }

                                if (random.nextBoolean()) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
