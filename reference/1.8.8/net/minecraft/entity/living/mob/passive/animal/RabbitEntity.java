package net.minecraft.entity.living.mob.passive.animal;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarrotsBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.GoToBlockGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class RabbitEntity extends AnimalEntity {
    private RabbitEntity.FleeEntityGoal<WolfEntity> fleeWolfGoal;
    private int jumpTicks = 0;
    private int jumpDuration = 0;
    private boolean jumping = false;
    private boolean lastOnGround = false;
    private int jumpDelayTicks = 0;
    private RabbitEntity.Action action = RabbitEntity.Action.HOP;
    private int ticksBeforeHungry = 0;
    private PlayerEntity attackTarget = null;

    public RabbitEntity(World world) {
        super(world);
        this.setSize(0.6F, 0.7F);
        this.jumpControl = new RabbitEntity.JumpControl(this);
        this.movementControl = new RabbitEntity.MovementControl(this);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.entityNavigation.setOffset(2.5F);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(1, new RabbitEntity.PanicGoal(this, 1.33));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.0, Items.CARROT, false));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.0, Items.GOLDEN_CARROT, false));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.0, Item.byBlock(Blocks.YELLOW_FLOWER), false));
        this.goalSelector.addGoal(3, new AnimalBreedGoal(this, 0.8));
        this.goalSelector.addGoal(5, new RabbitEntity.RaidFarmGoal(this));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 0.6));
        this.goalSelector.addGoal(11, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
        this.fleeWolfGoal = new RabbitEntity.FleeEntityGoal<>(this, WolfEntity.class, 16.0F, 1.33, 1.33);
        this.goalSelector.addGoal(4, this.fleeWolfGoal);
        this.setSpeed(0.0);
    }

    @Override
    protected float getJumpStrength() {
        return this.movementControl.isMoving() && this.movementControl.getY() > this.y + 0.5 ? 0.5F : this.action.getJumpStrength();
    }

    public void setAction(RabbitEntity.Action action) {
        this.action = action;
    }

    public float getJumpCompletion(float tickDelta) {
        return this.jumpDuration == 0 ? 0.0F : (this.jumpTicks + tickDelta) / this.jumpDuration;
    }

    public void setSpeed(double speed) {
        this.getNavigation().setSpeed(speed);
        this.movementControl.update(this.movementControl.getX(), this.movementControl.getY(), this.movementControl.getZ(), speed);
    }

    public void startAction(boolean jumping, RabbitEntity.Action action) {
        super.setJumping(jumping);
        if (!jumping) {
            if (this.action == RabbitEntity.Action.ATTACK) {
                this.action = RabbitEntity.Action.HOP;
            }
        } else {
            this.setSpeed(1.5 * action.getSpeed());
            this.playSound(this.getJumpSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
        }

        this.jumping = jumping;
    }

    public void startJumping(RabbitEntity.Action action) {
        this.startAction(true, action);
        this.jumpDuration = action.getJumpDuration();
        this.jumpTicks = 0;
    }

    public boolean isJumping() {
        return this.jumping;
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(18, (byte)0);
    }

    @Override
    public void mobAiTick() {
        if (this.movementControl.getSpeed() > 0.8) {
            this.setAction(RabbitEntity.Action.SPRINT);
        } else if (this.action != RabbitEntity.Action.ATTACK) {
            this.setAction(RabbitEntity.Action.HOP);
        }

        if (this.jumpDelayTicks > 0) {
            this.jumpDelayTicks--;
        }

        if (this.ticksBeforeHungry > 0) {
            this.ticksBeforeHungry = this.ticksBeforeHungry - this.random.nextInt(3);
            if (this.ticksBeforeHungry < 0) {
                this.ticksBeforeHungry = 0;
            }
        }

        if (this.onGround) {
            if (!this.lastOnGround) {
                this.startAction(false, RabbitEntity.Action.NONE);
                this.checkLandingDelay();
            }

            if (this.getSkin() == 99 && this.jumpDelayTicks == 0) {
                LivingEntity livingentity = this.getAttackTarget();
                if (livingentity != null && this.squaredDistanceTo(livingentity) < 16.0) {
                    this.lookTowards(livingentity.x, livingentity.z);
                    this.movementControl.update(livingentity.x, livingentity.y, livingentity.z, this.movementControl.getSpeed());
                    this.startJumping(RabbitEntity.Action.ATTACK);
                    this.lastOnGround = true;
                }
            }

            RabbitEntity.JumpControl rabbitentity$jumpcontrol = (RabbitEntity.JumpControl)this.jumpControl;
            if (!rabbitentity$jumpcontrol.isActive()) {
                if (this.movementControl.isMoving() && this.jumpDelayTicks == 0) {
                    Path path = this.entityNavigation.getPath();
                    Vec3d vec3d = new Vec3d(this.movementControl.getX(), this.movementControl.getY(), this.movementControl.getZ());
                    if (path != null && path.getCurrentIndex() < path.length()) {
                        vec3d = path.getCurrentPos(this);
                    }

                    this.lookTowards(vec3d.x, vec3d.z);
                    this.startJumping(this.action);
                }
            } else if (!rabbitentity$jumpcontrol.canJump()) {
                this.enableJumping();
            }
        }

        this.lastOnGround = this.onGround;
    }

    @Override
    public void tickSprintingEffect() {
    }

    private void lookTowards(double x, double z) {
        this.yaw = (float)(MathHelper.fastAtan2(z - this.z, x - this.x) * 180.0 / (float) Math.PI) - 90.0F;
    }

    private void enableJumping() {
        ((RabbitEntity.JumpControl)this.jumpControl).setCanJump(true);
    }

    private void disableJumping() {
        ((RabbitEntity.JumpControl)this.jumpControl).setCanJump(false);
    }

    private void setLandingDelay() {
        this.jumpDelayTicks = this.getJumpDelay();
    }

    private void checkLandingDelay() {
        this.setLandingDelay();
        this.disableJumping();
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (this.jumpTicks != this.jumpDuration) {
            if (this.jumpTicks == 0 && !this.world.isClient) {
                this.world.doEntityEvent(this, (byte)1);
            }

            this.jumpTicks++;
        } else if (this.jumpDuration != 0) {
            this.jumpTicks = 0;
            this.jumpDuration = 0;
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(10.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("RabbitType", this.getSkin());
        nbt.putInt("MoreCarrotTicks", this.ticksBeforeHungry);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setVariant(nbt.getInt("RabbitType"));
        this.ticksBeforeHungry = nbt.getInt("MoreCarrotTicks");
    }

    protected String getJumpSound() {
        return "mob.rabbit.hop";
    }

    @Override
    protected String getAmbientSound() {
        return "mob.rabbit.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.rabbit.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.rabbit.death";
    }

    @Override
    public boolean tryDamage(Entity target) {
        if (this.getSkin() == 99) {
            this.playSound("mob.attack", 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            return target.takeDamage(DamageSource.mob(this), 8.0F);
        } else {
            return target.takeDamage(DamageSource.mob(this), 3.0F);
        }
    }

    @Override
    public int getArmorProtection() {
        return this.getSkin() == 99 ? 8 : super.getArmorProtection();
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        return !this.isInvulnerable(source) && super.takeDamage(source, amount);
    }

    @Override
    protected void dropRareItem() {
        this.dropItem(new ItemStack(Items.RABBIT_FOOT, 1), 0.0F);
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(2) + this.random.nextInt(1 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.RABBIT_HIDE, 1);
        }

        i = this.random.nextInt(2);

        for (int k = 0; k < i; k++) {
            if (this.isOnFire()) {
                this.dropItem(Items.COOKED_RABBIT, 1);
            } else {
                this.dropItem(Items.RABBIT, 1);
            }
        }
    }

    private boolean isTempting(Item item) {
        return item == Items.CARROT || item == Items.GOLDEN_CARROT || item == Item.byBlock(Blocks.YELLOW_FLOWER);
    }

    public RabbitEntity makeChild(PassiveEntity passiveEntity) {
        RabbitEntity rabbitentity = new RabbitEntity(this.world);
        if (passiveEntity instanceof RabbitEntity) {
            rabbitentity.setVariant(this.random.nextBoolean() ? this.getSkin() : ((RabbitEntity)passiveEntity).getSkin());
        }

        return rabbitentity;
    }

    @Override
    public boolean isBreedingItem(ItemStack item) {
        return item != null && this.isTempting(item.getItem());
    }

    public int getSkin() {
        return this.syncedData.getByte(18);
    }

    public void setVariant(int rabbitType) {
        if (rabbitType == 99) {
            this.goalSelector.removeGoal(this.fleeWolfGoal);
            this.goalSelector.addGoal(4, new RabbitEntity.EvilAttackGoal(this));
            this.targetSelector.addGoal(1, new RevengeGoal(this, false));
            this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
            this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, WolfEntity.class, true));
            if (!this.hasCustomName()) {
                this.setCustomName(I18n.translate("entity.KillerBunny.name"));
            }
        }

        this.syncedData.update(18, (byte)rabbitType);
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        int i = this.random.nextInt(6);
        boolean flag = false;
        if (data instanceof RabbitEntity.Data) {
            i = ((RabbitEntity.Data)data).variant;
            flag = true;
        } else {
            data = new RabbitEntity.Data(i);
        }

        this.setVariant(i);
        if (flag) {
            this.setBreedingAge(-24000);
        }

        return data;
    }

    private boolean isHungry() {
        return this.ticksBeforeHungry == 0;
    }

    protected int getJumpDelay() {
        return this.action.getJumpDelay();
    }

    protected void onEatFromGarden() {
        this.world
            .addParticle(
                ParticleType.BLOCK_DUST,
                this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                this.y + 0.5 + this.random.nextFloat() * this.height,
                this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                0.0,
                0.0,
                0.0,
                Block.serialize(Blocks.CARROTS.getStateFromMetadata(7))
            );
        this.ticksBeforeHungry = 100;
    }

    @Override
    public void doEvent(byte event) {
        if (event == 1) {
            this.doSprintingEffect();
            this.jumpDuration = 10;
            this.jumpTicks = 0;
        } else {
            super.doEvent(event);
        }
    }

    enum Action {
        NONE(0.0F, 0.0F, 30, 1),
        HOP(0.8F, 0.2F, 20, 10),
        STEP(1.0F, 0.45F, 14, 14),
        SPRINT(1.75F, 0.4F, 1, 8),
        ATTACK(2.0F, 0.7F, 7, 8);

        private final float speed;
        private final float jumpStrength;
        private final int jumpDelay;
        private final int jumpDuration;

        Action(float speed, float jumpStrength, int jumpDelay, int jumpDuration) {
            this.speed = speed;
            this.jumpStrength = jumpStrength;
            this.jumpDelay = jumpDelay;
            this.jumpDuration = jumpDuration;
        }

        public float getSpeed() {
            return this.speed;
        }

        public float getJumpStrength() {
            return this.jumpStrength;
        }

        public int getJumpDelay() {
            return this.jumpDelay;
        }

        public int getJumpDuration() {
            return this.jumpDuration;
        }
    }

    public static class Data implements EntityData {
        public int variant;

        public Data(int variant) {
            this.variant = variant;
        }
    }

    static class EvilAttackGoal extends MeleeAttackGoal {
        public EvilAttackGoal(RabbitEntity rabbit) {
            super(rabbit, LivingEntity.class, 1.4, true);
        }

        @Override
        protected double getReach(LivingEntity target) {
            return 4.0F + target.width;
        }
    }

    static class FleeEntityGoal<T extends Entity> extends net.minecraft.entity.ai.goal.FleeEntityGoal<T> {
        private RabbitEntity rabbit;

        public FleeEntityGoal(RabbitEntity rabbit, Class<T> targetType, float distance, double speedWhenFar, double speedWhenClose) {
            super(rabbit, targetType, distance, speedWhenFar, speedWhenClose);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            super.tick();
        }
    }

    public class JumpControl extends net.minecraft.entity.ai.control.JumpControl {
        private RabbitEntity rabbit;
        private boolean canJump = false;

        public JumpControl(RabbitEntity rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        public boolean isActive() {
            return this.active;
        }

        public boolean canJump() {
            return this.canJump;
        }

        public void setCanJump(boolean canJump) {
            this.canJump = canJump;
        }

        @Override
        public void tick() {
            if (this.active) {
                this.rabbit.startJumping(RabbitEntity.Action.STEP);
                this.active = false;
            }
        }
    }

    static class MovementControl extends net.minecraft.entity.ai.control.MovementControl {
        private RabbitEntity rabbit;

        public MovementControl(RabbitEntity rabbit) {
            super(rabbit);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            if (this.rabbit.onGround && !this.rabbit.isJumping()) {
                this.rabbit.setSpeed(0.0);
            }

            super.tick();
        }
    }

    static class PanicGoal extends EscapeDangerGoal {
        private RabbitEntity rabbit;

        public PanicGoal(RabbitEntity rabbit, double speed) {
            super(rabbit, speed);
            this.rabbit = rabbit;
        }

        @Override
        public void tick() {
            super.tick();
            this.rabbit.setSpeed(this.speed);
        }
    }

    static class RaidFarmGoal extends GoToBlockGoal {
        private final RabbitEntity rabbit;
        private boolean hungry;
        private boolean hasTarget = false;

        public RaidFarmGoal(RabbitEntity rabbit) {
            super(rabbit, 0.7F, 16);
            this.rabbit = rabbit;
        }

        @Override
        public boolean canStart() {
            if (this.cooldown <= 0) {
                if (!this.rabbit.world.getGameRules().getBoolean("mobGriefing")) {
                    return false;
                }

                this.hasTarget = false;
                this.hungry = this.rabbit.isHungry();
            }

            return super.canStart();
        }

        @Override
        public boolean shouldContinue() {
            return this.hasTarget && super.shouldContinue();
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
            this.rabbit
                .getLookControl()
                .lookAt(this.target.getX() + 0.5, this.target.getY() + 1, this.target.getZ() + 0.5, 10.0F, this.rabbit.getLookPitchSpeed());
            if (this.hasReachedTarget()) {
                World world = this.rabbit.world;
                BlockPos blockpos = this.target.up();
                BlockState blockstate = world.getBlockState(blockpos);
                Block block = blockstate.getBlock();
                if (this.hasTarget && block instanceof CarrotsBlock && blockstate.get(CarrotsBlock.AGE) == 7) {
                    world.setBlockState(blockpos, Blocks.AIR.defaultState(), 2);
                    world.breakBlock(blockpos, true);
                    this.rabbit.onEatFromGarden();
                }

                this.hasTarget = false;
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
                if (block instanceof CarrotsBlock && blockstate.get(CarrotsBlock.AGE) == 7 && this.hungry && !this.hasTarget) {
                    this.hasTarget = true;
                    return true;
                }
            }

            return false;
        }
    }
}
