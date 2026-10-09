package net.minecraft.entity.living.mob.monster;

import com.google.common.base.Predicate;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.EntityDamageSource;
import net.minecraft.entity.damage.ProjectileDamageSource;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class EndermanEntity extends MonsterEntity {
    private static final UUID ATTACK_SPEED_BOOST_UUID = UUID.fromString("020E0DFB-87AE-4653-9556-831010E291A0");
    private static final AttributeModifier ATTACK_SPEED_BOOST = new AttributeModifier(ATTACK_SPEED_BOOST_UUID, "Attacking speed boost", 0.15F, 0)
        .setSerialized(false);
    private static final Set<Block> HOLDABLE_BLOCKS = Sets.newIdentityHashSet();
    private boolean attackedByPlayer;

    public EndermanEntity(World world) {
        super(world);
        this.setSize(0.6F, 2.9F);
        this.stepHeight = 1.0F;
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.goalSelector.addGoal(10, new EndermanEntity.PlaceBlockGoal(this));
        this.goalSelector.addGoal(11, new EndermanEntity.PickUpBlockGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, false));
        this.targetSelector.addGoal(2, new EndermanEntity.LookAtPlayerGoal(this));
        this.targetSelector.addGoal(3, new ActiveTargetGoal<>(this, EndermiteEntity.class, 10, true, false, new Predicate<EndermiteEntity>() {
            public boolean apply(EndermiteEntity endermiteEntity) {
                return endermiteEntity.isPlayerSpawned();
            }
        }));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(40.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(7.0);
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(64.0);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Short((short)0));
        this.syncedData.register(17, new Byte((byte)0));
        this.syncedData.register(18, new Byte((byte)0));
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        BlockState blockstate = this.getCarriedBlock();
        nbt.putShort("carried", (short)Block.getId(blockstate.getBlock()));
        nbt.putShort("carriedData", (short)blockstate.getBlock().getMetadataFromState(blockstate));
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        BlockState blockstate;
        if (nbt.contains("carried", 8)) {
            blockstate = Block.byKey(nbt.getString("carried")).getStateFromMetadata(nbt.getShort("carriedData") & 65535);
        } else {
            blockstate = Block.byId(nbt.getShort("carried")).getStateFromMetadata(nbt.getShort("carriedData") & 65535);
        }

        this.setCarriedBlock(blockstate);
    }

    private boolean isLookedAtBy(PlayerEntity player) {
        ItemStack itemstack = player.inventory.armor[3];
        if (itemstack != null && itemstack.getItem() == Item.byBlock(Blocks.PUMPKIN)) {
            return false;
        }

        Vec3d vec3d = player.getRotationVec(1.0F).normalize();
        Vec3d vec3d1 = new Vec3d(this.x - player.x, this.getShape().minY + this.height / 2.0F - (player.y + player.getEyeHeight()), this.z - player.z);
        double d0 = vec3d1.length();
        vec3d1 = vec3d1.normalize();
        double d1 = vec3d.dot(vec3d1);
        return d1 > 1.0 - 0.025 / d0 && player.canSee(this);
    }

    @Override
    public float getEyeHeight() {
        return 2.55F;
    }

    @Override
    public void mobTick() {
        if (this.world.isClient) {
            for (int i = 0; i < 2; i++) {
                this.world
                    .addParticle(
                        ParticleType.PORTAL,
                        this.x + (this.random.nextDouble() - 0.5) * this.width,
                        this.y + this.random.nextDouble() * this.height - 0.25,
                        this.z + (this.random.nextDouble() - 0.5) * this.width,
                        (this.random.nextDouble() - 0.5) * 2.0,
                        -this.random.nextDouble(),
                        (this.random.nextDouble() - 0.5) * 2.0
                    );
            }
        }

        this.jumping = false;
        super.mobTick();
    }

    @Override
    protected void mobAiTick() {
        if (this.isInWaterOrRain()) {
            this.takeDamage(DamageSource.DROWN, 1.0F);
        }

        if (this.isAngry() && !this.attackedByPlayer && this.random.nextInt(100) == 0) {
            this.setAngry(false);
        }

        if (this.world.isSunny()) {
            float f = this.getBrightness(1.0F);
            if (f > 0.5F && this.world.hasSkyAccess(new BlockPos(this)) && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F) {
                this.setAttackTarget(null);
                this.setAngry(false);
                this.attackedByPlayer = false;
                this.teleport();
            }
        }

        super.mobAiTick();
    }

    protected boolean teleport() {
        double d0 = this.x + (this.random.nextDouble() - 0.5) * 64.0;
        double d1 = this.y + (this.random.nextInt(64) - 32);
        double d2 = this.z + (this.random.nextDouble() - 0.5) * 64.0;
        return this.teleportTo(d0, d1, d2);
    }

    protected boolean teleportTo(Entity target) {
        Vec3d vec3d = new Vec3d(this.x - target.x, this.getShape().minY + this.height / 2.0F - target.y + target.getEyeHeight(), this.z - target.z);
        vec3d = vec3d.normalize();
        double d0 = 16.0;
        double d1 = this.x + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.x * d0;
        double d2 = this.y + (this.random.nextInt(16) - 8) - vec3d.y * d0;
        double d3 = this.z + (this.random.nextDouble() - 0.5) * 8.0 - vec3d.z * d0;
        return this.teleportTo(d1, d2, d3);
    }

    protected boolean teleportTo(double x, double y, double z) {
        double d0 = this.x;
        double d1 = this.y;
        double d2 = this.z;
        this.x = x;
        this.y = y;
        this.z = z;
        boolean flag = false;
        BlockPos blockpos = new BlockPos(this.x, this.y, this.z);
        if (this.world.isChunkLoaded(blockpos)) {
            boolean flag1 = false;

            while (!flag1 && blockpos.getY() > 0) {
                BlockPos blockpos1 = blockpos.down();
                Block block = this.world.getBlockState(blockpos1).getBlock();
                if (block.getMaterial().blocksMovement()) {
                    flag1 = true;
                } else {
                    this.y--;
                    blockpos = blockpos1;
                }
            }

            if (flag1) {
                super.teleport(this.x, this.y, this.z);
                if (this.world.getCollisions(this, this.getShape()).isEmpty() && !this.world.containsLiquid(this.getShape())) {
                    flag = true;
                }
            }
        }

        if (!flag) {
            this.setPosition(d0, d1, d2);
            return false;
        }

        int i = 128;

        for (int j = 0; j < i; j++) {
            double d6 = j / (i - 1.0);
            float f = (this.random.nextFloat() - 0.5F) * 0.2F;
            float f1 = (this.random.nextFloat() - 0.5F) * 0.2F;
            float f2 = (this.random.nextFloat() - 0.5F) * 0.2F;
            double d3 = d0 + (this.x - d0) * d6 + (this.random.nextDouble() - 0.5) * this.width * 2.0;
            double d4 = d1 + (this.y - d1) * d6 + this.random.nextDouble() * this.height;
            double d5 = d2 + (this.z - d2) * d6 + (this.random.nextDouble() - 0.5) * this.width * 2.0;
            this.world.addParticle(ParticleType.PORTAL, d3, d4, d5, f, f1, f2);
        }

        this.world.playSound(d0, d1, d2, "mob.endermen.portal", 1.0F, 1.0F);
        this.playSound("mob.endermen.portal", 1.0F, 1.0F);
        return true;
    }

    @Override
    protected String getAmbientSound() {
        return this.isAngry() ? "mob.endermen.scream" : "mob.endermen.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.endermen.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.endermen.death";
    }

    @Override
    protected Item getDropItem() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        Item item = this.getDropItem();
        if (item != null) {
            int i = this.random.nextInt(2 + lootingMultiplier);

            for (int j = 0; j < i; j++) {
                this.dropItem(item, 1);
            }
        }
    }

    public void setCarriedBlock(BlockState state) {
        this.syncedData.update(16, (short)(Block.serialize(state) & 65535));
    }

    public BlockState getCarriedBlock() {
        return Block.deserialize(this.syncedData.getShort(16) & 65535);
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (source.getAttacker() == null || !(source.getAttacker() instanceof EndermiteEntity)) {
            if (!this.world.isClient) {
                this.setAngry(true);
            }

            if (source instanceof EntityDamageSource && source.getAttacker() instanceof PlayerEntity) {
                if (source.getAttacker() instanceof ServerPlayerEntity && ((ServerPlayerEntity)source.getAttacker()).interactionManager.isCreative()) {
                    this.setAngry(false);
                } else {
                    this.attackedByPlayer = true;
                }
            }

            if (source instanceof ProjectileDamageSource) {
                this.attackedByPlayer = false;

                for (int i = 0; i < 64; i++) {
                    if (this.teleport()) {
                        return true;
                    }
                }

                return false;
            }
        }

        boolean flag = super.takeDamage(source, amount);
        if (source.bypassesArmor() && this.random.nextInt(10) != 0) {
            this.teleport();
        }

        return flag;
    }

    public boolean isAngry() {
        return this.syncedData.getByte(18) > 0;
    }

    public void setAngry(boolean angry) {
        this.syncedData.update(18, (byte)(angry ? 1 : 0));
    }

    static {
        HOLDABLE_BLOCKS.add(Blocks.GRASS);
        HOLDABLE_BLOCKS.add(Blocks.DIRT);
        HOLDABLE_BLOCKS.add(Blocks.SAND);
        HOLDABLE_BLOCKS.add(Blocks.GRAVEL);
        HOLDABLE_BLOCKS.add(Blocks.YELLOW_FLOWER);
        HOLDABLE_BLOCKS.add(Blocks.RED_FLOWER);
        HOLDABLE_BLOCKS.add(Blocks.BROWN_MUSHROOM);
        HOLDABLE_BLOCKS.add(Blocks.RED_MUSHROOM);
        HOLDABLE_BLOCKS.add(Blocks.TNT);
        HOLDABLE_BLOCKS.add(Blocks.CACTUS);
        HOLDABLE_BLOCKS.add(Blocks.CLAY);
        HOLDABLE_BLOCKS.add(Blocks.PUMPKIN);
        HOLDABLE_BLOCKS.add(Blocks.MELON_BLOCK);
        HOLDABLE_BLOCKS.add(Blocks.MYCELIUM);
    }

    static class LookAtPlayerGoal extends ActiveTargetGoal {
        private PlayerEntity pendingTarget;
        /**
         * A timer that counts down while the enderman has a target, after which a creepy sound will be played.
         */
        private int creepySoundTimer;
        /**
         * A counter that increments each tick where its target is more than 16 blocks away from this enderman.
         */
        private int targetFarAwayTicks;
        private EndermanEntity enderman;

        public LookAtPlayerGoal(EndermanEntity enderman) {
            super(enderman, PlayerEntity.class, true);
            this.enderman = enderman;
        }

        @Override
        public boolean canStart() {
            double d0 = this.getFollowRange();
            List<PlayerEntity> list = this.mob.world.getEntitiesOfType(PlayerEntity.class, this.mob.getShape().grown(d0, 4.0, d0), this.targetFilter);
            Collections.sort(list, this.comparator);
            if (list.isEmpty()) {
                return false;
            }

            this.pendingTarget = list.get(0);
            return true;
        }

        @Override
        public void start() {
            this.creepySoundTimer = 5;
            this.targetFarAwayTicks = 0;
        }

        @Override
        public void stop() {
            this.pendingTarget = null;
            this.enderman.setAngry(false);
            EntityAttributeInstance entityattributeinstance = this.enderman.getAttribute(EntityAttributes.MOVEMENT_SPEED);
            entityattributeinstance.removeModifier(EndermanEntity.ATTACK_SPEED_BOOST);
            super.stop();
        }

        @Override
        public boolean shouldContinue() {
            if (this.pendingTarget != null) {
                if (!this.enderman.isLookedAtBy(this.pendingTarget)) {
                    return false;
                }

                this.enderman.attackedByPlayer = true;
                this.enderman.lookAt(this.pendingTarget, 10.0F, 10.0F);
                return true;
            } else {
                return super.shouldContinue();
            }
        }

        @Override
        public void tick() {
            if (this.pendingTarget != null) {
                if (--this.creepySoundTimer <= 0) {
                    this.target = this.pendingTarget;
                    this.pendingTarget = null;
                    super.start();
                    this.enderman.playSound("mob.endermen.stare", 1.0F, 1.0F);
                    this.enderman.setAngry(true);
                    EntityAttributeInstance entityattributeinstance = this.enderman.getAttribute(EntityAttributes.MOVEMENT_SPEED);
                    entityattributeinstance.addModifier(EndermanEntity.ATTACK_SPEED_BOOST);
                }
            } else {
                if (this.target != null) {
                    if (this.target instanceof PlayerEntity && this.enderman.isLookedAtBy((PlayerEntity)this.target)) {
                        if (this.target.squaredDistanceTo(this.enderman) < 16.0) {
                            this.enderman.teleport();
                        }

                        this.targetFarAwayTicks = 0;
                    } else if (this.target.squaredDistanceTo(this.enderman) > 256.0 && this.targetFarAwayTicks++ >= 30 && this.enderman.teleportTo(this.target)
                        )
                     {
                        this.targetFarAwayTicks = 0;
                    }
                }

                super.tick();
            }
        }
    }

    static class PickUpBlockGoal extends Goal {
        private EndermanEntity enderman;

        public PickUpBlockGoal(EndermanEntity enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canStart() {
            return this.enderman.world.getGameRules().getBoolean("mobGriefing")
                && this.enderman.getCarriedBlock().getBlock().getMaterial() == Material.AIR
                && this.enderman.getRandom().nextInt(20) == 0;
        }

        @Override
        public void tick() {
            Random random = this.enderman.getRandom();
            World world = this.enderman.world;
            int i = MathHelper.floor(this.enderman.x - 2.0 + random.nextDouble() * 4.0);
            int j = MathHelper.floor(this.enderman.y + random.nextDouble() * 3.0);
            int k = MathHelper.floor(this.enderman.z - 2.0 + random.nextDouble() * 4.0);
            BlockPos blockpos = new BlockPos(i, j, k);
            BlockState blockstate = world.getBlockState(blockpos);
            Block block = blockstate.getBlock();
            if (EndermanEntity.HOLDABLE_BLOCKS.contains(block)) {
                this.enderman.setCarriedBlock(blockstate);
                world.setBlockState(blockpos, Blocks.AIR.defaultState());
            }
        }
    }

    static class PlaceBlockGoal extends Goal {
        private EndermanEntity enderman;

        public PlaceBlockGoal(EndermanEntity enderman) {
            this.enderman = enderman;
        }

        @Override
        public boolean canStart() {
            return this.enderman.world.getGameRules().getBoolean("mobGriefing")
                && this.enderman.getCarriedBlock().getBlock().getMaterial() != Material.AIR
                && this.enderman.getRandom().nextInt(2000) == 0;
        }

        @Override
        public void tick() {
            Random random = this.enderman.getRandom();
            World world = this.enderman.world;
            int i = MathHelper.floor(this.enderman.x - 1.0 + random.nextDouble() * 2.0);
            int j = MathHelper.floor(this.enderman.y + random.nextDouble() * 2.0);
            int k = MathHelper.floor(this.enderman.z - 1.0 + random.nextDouble() * 2.0);
            BlockPos blockpos = new BlockPos(i, j, k);
            Block block = world.getBlockState(blockpos).getBlock();
            Block block1 = world.getBlockState(blockpos.down()).getBlock();
            if (this.canPlaceBlock(world, blockpos, this.enderman.getCarriedBlock().getBlock(), block, block1)) {
                world.setBlockState(blockpos, this.enderman.getCarriedBlock(), 3);
                this.enderman.setCarriedBlock(Blocks.AIR.defaultState());
            }
        }

        private boolean canPlaceBlock(World world, BlockPos pos, Block carriedBlock, Block blockInWorld, Block blockBelowInWorld) {
            return carriedBlock.canBePlaced(world, pos)
                && blockInWorld.getMaterial() == Material.AIR
                && blockBelowInWorld.getMaterial() != Material.AIR
                && blockBelowInWorld.isCube();
        }
    }
}
