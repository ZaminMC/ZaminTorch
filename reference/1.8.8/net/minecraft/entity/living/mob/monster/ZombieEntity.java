package net.minecraft.entity.living.mob.monster;

import java.util.Calendar;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageAtNightGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.attribute.RangedEntityAttribute;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class ZombieEntity extends MonsterEntity {
    protected static final EntityAttribute REINFORCEMENTS_ATTRIBUTE = new RangedEntityAttribute(null, "zombie.spawnReinforcements", 0.0, 0.0, 1.0)
        .setDisplayName("Spawn Reinforcements Chance");
    private static final UUID BABY_SPEED_ID = UUID.fromString("B9766B59-9566-4402-BC1F-2EE2A276D836");
    private static final AttributeModifier BABY_SPEED_BOOST_MODIFIER = new AttributeModifier(BABY_SPEED_ID, "Baby speed boost", 0.5, 1);
    private final BreakDoorGoal breakDoorGoal = new BreakDoorGoal(this);
    private int ticksUntilConversion;
    private boolean canBreakDoors = false;
    private float zombieWidth = -1.0F;
    private float zombieHeight;

    public ZombieEntity(World world) {
        super(world);
        ((GroundPathNavigation)this.getNavigation()).setCanOpenDoors(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, PlayerEntity.class, 1.0, false));
        this.goalSelector.addGoal(5, new WanderThroughVillageGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.initMoveGoals();
        this.setSize(0.6F, 1.95F);
    }

    protected void initMoveGoals() {
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, VillagerEntity.class, 1.0, true));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, IronGolemEntity.class, 1.0, true));
        this.goalSelector.addGoal(6, new WanderThroughVillageAtNightGoal(this, 1.0, false));
        this.targetSelector.addGoal(1, new RevengeGoal(this, true, ZombiePigmanEntity.class));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, VillagerEntity.class, false));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(35.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.23F);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(3.0);
        this.getAttributes().register(REINFORCEMENTS_ATTRIBUTE).setBase(this.random.nextDouble() * 0.1F);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.getSyncedData().register(12, (byte)0);
        this.getSyncedData().register(13, (byte)0);
        this.getSyncedData().register(14, (byte)0);
    }

    @Override
    public int getArmorProtection() {
        int i = super.getArmorProtection() + 2;
        if (i > 20) {
            i = 20;
        }

        return i;
    }

    public boolean canBreakDoors() {
        return this.canBreakDoors;
    }

    public void setCanBreakDoors(boolean canBreakDoors) {
        if (this.canBreakDoors != canBreakDoors) {
            this.canBreakDoors = canBreakDoors;
            if (canBreakDoors) {
                this.goalSelector.addGoal(1, this.breakDoorGoal);
            } else {
                this.goalSelector.removeGoal(this.breakDoorGoal);
            }
        }
    }

    @Override
    public boolean isBaby() {
        return this.getSyncedData().getByte(12) == 1;
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        if (this.isBaby()) {
            this.xpDrop = (int)(this.xpDrop * 2.5F);
        }

        return super.getXpDrop(playerEntity);
    }

    public void setBaby(boolean isBaby) {
        this.getSyncedData().update(12, (byte)(isBaby ? 1 : 0));
        if (this.world != null && !this.world.isClient) {
            EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
            entityattributeinstance.removeModifier(BABY_SPEED_BOOST_MODIFIER);
            if (isBaby) {
                entityattributeinstance.addModifier(BABY_SPEED_BOOST_MODIFIER);
            }
        }

        this.updateDimensions(isBaby);
    }

    public boolean isVillager() {
        return this.getSyncedData().getByte(13) == 1;
    }

    public void setType(boolean isVillager) {
        this.getSyncedData().update(13, (byte)(isVillager ? 1 : 0));
    }

    @Override
    public void mobTick() {
        if (this.world.isSunny() && !this.world.isClient && !this.isBaby()) {
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

        if (this.isRiding() && this.getAttackTarget() != null && this.vehicle instanceof ChickenEntity) {
            ((MobEntity)this.vehicle).getNavigation().moveAlong(this.getNavigation().getPath(), 1.5);
        }

        super.mobTick();
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (super.takeDamage(source, amount)) {
            LivingEntity livingentity = this.getAttackTarget();
            if (livingentity == null && source.getAttacker() instanceof LivingEntity) {
                livingentity = (LivingEntity)source.getAttacker();
            }

            if (livingentity != null
                && this.world.getDifficulty() == Difficulty.HARD
                && this.random.nextFloat() < this.getAttribute(REINFORCEMENTS_ATTRIBUTE).get()) {
                int i = MathHelper.floor(this.x);
                int j = MathHelper.floor(this.y);
                int k = MathHelper.floor(this.z);
                ZombieEntity zombieentity = new ZombieEntity(this.world);

                for (int l = 0; l < 50; l++) {
                    int i1 = i + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    int j1 = j + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    int k1 = k + MathHelper.nextInt(this.random, 7, 40) * MathHelper.nextInt(this.random, -1, 1);
                    if (World.hasSolidTop(this.world, new BlockPos(i1, j1 - 1, k1)) && this.world.getRawBrightness(new BlockPos(i1, j1, k1)) < 10) {
                        zombieentity.setPosition(i1, j1, k1);
                        if (!this.world.isPlayerWithinRange(i1, j1, k1, 7.0)
                            && this.world.isUnobstructed(zombieentity.getShape(), zombieentity)
                            && this.world.getCollisions(zombieentity, zombieentity.getShape()).isEmpty()
                            && !this.world.containsLiquid(zombieentity.getShape())) {
                            this.world.addEntity(zombieentity);
                            zombieentity.setAttackTarget(livingentity);
                            zombieentity.initialize(this.world.getLocalDifficulty(new BlockPos(zombieentity)), null);
                            this.getAttribute(REINFORCEMENTS_ATTRIBUTE).addModifier(new AttributeModifier("Zombie reinforcement caller charge", -0.05F, 0));
                            zombieentity.getAttribute(REINFORCEMENTS_ATTRIBUTE)
                                .addModifier(new AttributeModifier("Zombie reinforcement callee charge", -0.05F, 0));
                            break;
                        }
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void tick() {
        if (!this.world.isClient && this.isConverting()) {
            int i = this.getTicksUntilConversionDecrement();
            this.ticksUntilConversion -= i;
            if (this.ticksUntilConversion <= 0) {
                this.convertToVillager();
            }
        }

        super.tick();
    }

    @Override
    public boolean tryDamage(Entity target) {
        boolean flag = super.tryDamage(target);
        if (flag) {
            int i = this.world.getDifficulty().getId();
            if (this.getDisplayItemInHand() == null && this.isOnFire() && this.random.nextFloat() < i * 0.3F) {
                target.setOnFireFor(2 * i);
            }
        }

        return flag;
    }

    @Override
    protected String getAmbientSound() {
        return "mob.zombie.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.zombie.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.zombie.death";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.zombie.step", 0.15F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return Items.ROTTEN_FLESH;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    protected void dropRareItem() {
        switch (this.random.nextInt(3)) {
            case 0:
                this.dropItem(Items.IRON_INGOT, 1);
                break;
            case 1:
                this.dropItem(Items.CARROT, 1);
                break;
            case 2:
                this.dropItem(Items.POTATO, 1);
        }
    }

    @Override
    protected void addRandomEquipment(LocalDifficulty difficulty) {
        super.addRandomEquipment(difficulty);
        if (this.random.nextFloat() < (this.world.getDifficulty() == Difficulty.HARD ? 0.05F : 0.01F)) {
            int i = this.random.nextInt(3);
            if (i == 0) {
                this.setEquipment(0, new ItemStack(Items.IRON_SWORD));
            } else {
                this.setEquipment(0, new ItemStack(Items.IRON_SHOVEL));
            }
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        if (this.isBaby()) {
            nbt.putBoolean("IsBaby", true);
        }

        if (this.isVillager()) {
            nbt.putBoolean("IsVillager", true);
        }

        nbt.putInt("ConversionTime", this.isConverting() ? this.ticksUntilConversion : -1);
        nbt.putBoolean("CanBreakDoors", this.canBreakDoors());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.getBoolean("IsBaby")) {
            this.setBaby(true);
        }

        if (nbt.getBoolean("IsVillager")) {
            this.setType(true);
        }

        if (nbt.contains("ConversionTime", 99) && nbt.getInt("ConversionTime") > -1) {
            this.setConversionTime(nbt.getInt("ConversionTime"));
        }

        this.setCanBreakDoors(nbt.getBoolean("CanBreakDoors"));
    }

    @Override
    public void onKill(LivingEntity victim) {
        super.onKill(victim);
        if ((this.world.getDifficulty() == Difficulty.NORMAL || this.world.getDifficulty() == Difficulty.HARD) && victim instanceof VillagerEntity) {
            if (this.world.getDifficulty() != Difficulty.HARD && this.random.nextBoolean()) {
                return;
            }

            MobEntity mobentity = (MobEntity)victim;
            ZombieEntity zombieentity = new ZombieEntity(this.world);
            zombieentity.copyPositionAndRotationFrom(victim);
            this.world.removeEntity(victim);
            zombieentity.initialize(this.world.getLocalDifficulty(new BlockPos(zombieentity)), null);
            zombieentity.setType(true);
            if (victim.isBaby()) {
                zombieentity.setBaby(true);
            }

            zombieentity.setNoAi(mobentity.isNoAi());
            if (mobentity.hasCustomName()) {
                zombieentity.setCustomName(mobentity.getCustomName());
                zombieentity.setCustomNameVisible(mobentity.isCustomNameVisible());
            }

            this.world.addEntity(zombieentity);
            this.world.doEvent(null, 1016, new BlockPos((int)this.x, (int)this.y, (int)this.z), 0);
        }
    }

    @Override
    public float getEyeHeight() {
        float f = 1.74F;
        if (this.isBaby()) {
            f = (float)(f - 0.81);
        }

        return f;
    }

    @Override
    protected boolean canHoldItem(ItemStack item) {
        return (item.getItem() != Items.EGG || !this.isBaby() || !this.isRiding()) && super.canHoldItem(item);
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        float f = localDifficulty.getMultiplier();
        this.setCanPickupLoot(this.random.nextFloat() < 0.55F * f);
        if (data == null) {
            data = new ZombieEntity.Data(this.world.random.nextFloat() < 0.05F, this.world.random.nextFloat() < 0.05F);
        }

        if (data instanceof ZombieEntity.Data) {
            ZombieEntity.Data zombieentity$data = (ZombieEntity.Data)data;
            if (zombieentity$data.villager) {
                this.setType(true);
            }

            if (zombieentity$data.baby) {
                this.setBaby(true);
                if (this.world.random.nextFloat() < 0.05) {
                    List<ChickenEntity> list = this.world.getEntitiesOfType(ChickenEntity.class, this.getShape().grown(5.0, 3.0, 5.0), EntityFilter.NOT_RIDING);
                    if (!list.isEmpty()) {
                        ChickenEntity chickenentity = list.get(0);
                        chickenentity.setHasJockey(true);
                        this.startRiding(chickenentity);
                    }
                } else if (this.world.random.nextFloat() < 0.05) {
                    ChickenEntity chickenentity1 = new ChickenEntity(this.world);
                    chickenentity1.setPositionAndAngles(this.x, this.y, this.z, this.yaw, 0.0F);
                    chickenentity1.initialize(localDifficulty, null);
                    chickenentity1.setHasJockey(true);
                    this.world.addEntity(chickenentity1);
                    this.startRiding(chickenentity1);
                }
            }
        }

        this.setCanBreakDoors(this.random.nextFloat() < f * 0.1F);
        this.addRandomEquipment(localDifficulty);
        this.enchantEquipment(localDifficulty);
        if (this.getEquipment(4) == null) {
            Calendar calendar = this.world.getCalendar();
            if (calendar.get(2) + 1 == 10 && calendar.get(5) == 31 && this.random.nextFloat() < 0.25F) {
                this.setEquipment(4, new ItemStack(this.random.nextFloat() < 0.1F ? Blocks.LIT_PUMPKIN : Blocks.PUMPKIN));
                this.inventoryDropChances[4] = 0.0F;
            }
        }

        this.getAttribute(EntityAttributes.KNOCKBACK_RESISTANCE).addModifier(new AttributeModifier("Random spawn bonus", this.random.nextDouble() * 0.05F, 0));
        double d0 = this.random.nextDouble() * 1.5 * f;
        if (d0 > 1.0) {
            this.getAttribute(EntityAttributes.FOLLOW_RANGE).addModifier(new AttributeModifier("Random zombie-spawn bonus", d0, 2));
        }

        if (this.random.nextFloat() < f * 0.05F) {
            this.getAttribute(REINFORCEMENTS_ATTRIBUTE).addModifier(new AttributeModifier("Leader zombie bonus", this.random.nextDouble() * 0.25 + 0.5, 0));
            this.getAttribute(EntityAttributes.MAX_HEALTH).addModifier(new AttributeModifier("Leader zombie bonus", this.random.nextDouble() * 3.0 + 1.0, 2));
            this.setCanBreakDoors(true);
        }

        return data;
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.getItemInHand();
        if (itemstack != null
            && itemstack.getItem() == Items.GOLDEN_APPLE
            && itemstack.getMetadata() == 0
            && this.isVillager()
            && this.hasStatusEffect(StatusEffect.WEAKNESS)) {
            if (!player.abilities.creativeMode) {
                itemstack.size--;
            }

            if (itemstack.size <= 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }

            if (!this.world.isClient) {
                this.setConversionTime(this.random.nextInt(2401) + 3600);
            }

            return true;
        } else {
            return false;
        }
    }

    protected void setConversionTime(int time) {
        this.ticksUntilConversion = time;
        this.getSyncedData().update(14, (byte)1);
        this.removeStatusEffect(StatusEffect.WEAKNESS.id);
        this.addStatusEffect(new StatusEffectInstance(StatusEffect.STRENGTH.id, time, Math.min(this.world.getDifficulty().getId() - 1, 0)));
        this.world.doEntityEvent(this, (byte)16);
    }

    @Override
    public void doEvent(byte event) {
        if (event == 16) {
            if (!this.isSilent()) {
                this.world
                    .playSound(
                        this.x + 0.5,
                        this.y + 0.5,
                        this.z + 0.5,
                        "mob.zombie.remedy",
                        1.0F + this.random.nextFloat(),
                        this.random.nextFloat() * 0.7F + 0.3F,
                        false
                    );
            }
        } else {
            super.doEvent(event);
        }
    }

    @Override
    protected boolean canDespawn() {
        return !this.isConverting();
    }

    public boolean isConverting() {
        return this.getSyncedData().getByte(14) == 1;
    }

    protected void convertToVillager() {
        VillagerEntity villagerentity = new VillagerEntity(this.world);
        villagerentity.copyPositionAndRotationFrom(this);
        villagerentity.initialize(this.world.getLocalDifficulty(new BlockPos(villagerentity)), null);
        villagerentity.setConvertedZombie();
        if (this.isBaby()) {
            villagerentity.setBreedingAge(-24000);
        }

        this.world.removeEntity(this);
        villagerentity.setNoAi(this.isNoAi());
        if (this.hasCustomName()) {
            villagerentity.setCustomName(this.getCustomName());
            villagerentity.setCustomNameVisible(this.isCustomNameVisible());
        }

        this.world.addEntity(villagerentity);
        villagerentity.addStatusEffect(new StatusEffectInstance(StatusEffect.NAUSEA.id, 200, 0));
        this.world.doEvent(null, 1017, new BlockPos((int)this.x, (int)this.y, (int)this.z), 0);
    }

    protected int getTicksUntilConversionDecrement() {
        int i = 1;
        if (this.random.nextFloat() < 0.01F) {
            int j = 0;
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k = (int)this.x - 4; k < (int)this.x + 4 && j < 14; k++) {
                for (int l = (int)this.y - 4; l < (int)this.y + 4 && j < 14; l++) {
                    for (int i1 = (int)this.z - 4; i1 < (int)this.z + 4 && j < 14; i1++) {
                        Block block = this.world.getBlockState(blockpos$mutable.set(k, l, i1)).getBlock();
                        if (block == Blocks.IRON_BARS || block == Blocks.BED) {
                            if (this.random.nextFloat() < 0.3F) {
                                i++;
                            }

                            j++;
                        }
                    }
                }
            }
        }

        return i;
    }

    public void updateDimensions(boolean isBaby) {
        this.setDimensions(isBaby ? 0.5F : 1.0F);
    }

    @Override
    protected final void setSize(float width, float height) {
        boolean flag = this.zombieWidth > 0.0F && this.zombieHeight > 0.0F;
        this.zombieWidth = width;
        this.zombieHeight = height;
        if (!flag) {
            this.setDimensions(1.0F);
        }
    }

    protected final void setDimensions(float scale) {
        super.setSize(this.zombieWidth * scale, this.zombieHeight * scale);
    }

    @Override
    public double getRideHeight() {
        return this.isBaby() ? 0.0 : -0.35;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (source.getAttacker() instanceof CreeperEntity
            && !(this instanceof ZombiePigmanEntity)
            && ((CreeperEntity)source.getAttacker()).isCharged()
            && ((CreeperEntity)source.getAttacker()).shouldDropMobHead()) {
            ((CreeperEntity)source.getAttacker()).addMobHeadDrop();
            this.dropItem(new ItemStack(Items.SKULL, 1, 2), 0.0F);
        }
    }

    class Data implements EntityData {
        public boolean baby = false;
        public boolean villager = false;

        private Data(boolean baby, boolean villager) {
            this.baby = baby;
            this.villager = villager;
        }
    }
}
