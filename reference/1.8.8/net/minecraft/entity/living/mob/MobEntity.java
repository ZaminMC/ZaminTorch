package net.minecraft.entity.living.mob;

import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.control.BodyControl;
import net.minecraft.entity.ai.control.JumpControl;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.control.MovementControl;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.ai.pathing.PathNavigation;
import net.minecraft.entity.decoration.DecorationEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.passive.animal.tameable.TameableEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.AttachEntityS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public abstract class MobEntity extends LivingEntity {
    public int ambientSoundDelay;
    /**
     * The amount of experience points (XP) this mob drops on death.
     */
    protected int xpDrop;
    private LookControl lookControl;
    protected MovementControl movementControl;
    protected JumpControl jumpControl;
    private BodyControl bodyControl;
    protected PathNavigation entityNavigation;
    protected final GoalSelector goalSelector;
    protected final GoalSelector targetSelector;
    private LivingEntity attackTarget;
    private MobVisibilityCache mobVisibilityCache;
    private ItemStack[] equipment = new ItemStack[5];
    /**
     * This array holds the value for both armor drop chances and hand-held item drop chances.
     */
    protected float[] inventoryDropChances = new float[5];
    private boolean canPickupLoot;
    private boolean persistent;
    private boolean leashed;
    private Entity leashHolder;
    private NbtCompound leashNbt;

    public MobEntity(World world) {
        super(world);
        this.goalSelector = new GoalSelector(world != null && world.profiler != null ? world.profiler : null);
        this.targetSelector = new GoalSelector(world != null && world.profiler != null ? world.profiler : null);
        this.lookControl = new LookControl(this);
        this.movementControl = new MovementControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyControl = new BodyControl(this);
        this.entityNavigation = this.createNavigation(world);
        this.mobVisibilityCache = new MobVisibilityCache(this);

        for (int i = 0; i < this.inventoryDropChances.length; i++) {
            this.inventoryDropChances[i] = 0.085F;
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributes().register(EntityAttributes.FOLLOW_RANGE).setBase(16.0);
    }

    protected PathNavigation createNavigation(World world) {
        return new GroundPathNavigation(this, world);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public MovementControl getMovementControl() {
        return this.movementControl;
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        return this.entityNavigation;
    }

    public MobVisibilityCache getMobVisibilityCache() {
        return this.mobVisibilityCache;
    }

    public LivingEntity getAttackTarget() {
        return this.attackTarget;
    }

    public void setAttackTarget(LivingEntity target) {
        this.attackTarget = target;
    }

    public boolean canAttack(Class<? extends LivingEntity> type) {
        return type != GhastEntity.class;
    }

    public void onEatingGrass() {
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(15, (byte)0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        String s = this.getAmbientSound();
        if (s != null) {
            this.playSound(s, this.getSoundVolume(), this.getSoundPitch());
        }
    }

    @Override
    public void baseTick() {
        super.baseTick();
        this.world.profiler.push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundDelay++) {
            this.ambientSoundDelay = -this.getAmbientSoundInterval();
            this.playAmbientSound();
        }

        this.world.profiler.pop();
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        if (this.xpDrop > 0) {
            int i = this.xpDrop;
            ItemStack[] aitemstack = this.getEquipment();

            for (int j = 0; j < aitemstack.length; j++) {
                if (aitemstack[j] != null && this.inventoryDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            return i;
        } else {
            return this.xpDrop;
        }
    }

    public void animateSpawn() {
        if (this.world.isClient) {
            for (int i = 0; i < 20; i++) {
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                double d2 = this.random.nextGaussian() * 0.02;
                double d3 = 10.0;
                this.world
                    .addParticle(
                        ParticleType.EXPLOSION_NORMAL,
                        this.x + this.random.nextFloat() * this.width * 2.0F - this.width - d0 * d3,
                        this.y + this.random.nextFloat() * this.height - d1 * d3,
                        this.z + this.random.nextFloat() * this.width * 2.0F - this.width - d2 * d3,
                        d0,
                        d1,
                        d2
                    );
            }
        } else {
            this.world.doEntityEvent(this, (byte)20);
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 20) {
            this.animateSpawn();
        } else {
            super.doEvent(event);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient) {
            this.updateLeashStatus();
        }
    }

    @Override
    protected float bodyMovement(float yaw, float movement) {
        this.bodyControl.tick();
        return movement;
    }

    protected String getAmbientSound() {
        return null;
    }

    protected Item getDropItem() {
        return null;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        Item item = this.getDropItem();
        if (item != null) {
            int i = this.random.nextInt(3);
            if (lootingMultiplier > 0) {
                i += this.random.nextInt(lootingMultiplier + 1);
            }

            for (int j = 0; j < i; j++) {
                this.dropItem(item, 1);
            }
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("CanPickUpLoot", this.canPickupLoot());
        nbt.putBoolean("PersistenceRequired", this.persistent);
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.equipment.length; i++) {
            NbtCompound nbtcompound = new NbtCompound();
            if (this.equipment[i] != null) {
                this.equipment[i].writeNbt(nbtcompound);
            }

            nbtlist.addElement(nbtcompound);
        }

        nbt.put("Equipment", nbtlist);
        NbtList nbtlist1 = new NbtList();

        for (int j = 0; j < this.inventoryDropChances.length; j++) {
            nbtlist1.addElement(new NbtFloat(this.inventoryDropChances[j]));
        }

        nbt.put("DropChances", nbtlist1);
        nbt.putBoolean("Leashed", this.leashed);
        if (this.leashHolder != null) {
            NbtCompound nbtcompound1 = new NbtCompound();
            if (this.leashHolder instanceof LivingEntity) {
                nbtcompound1.putLong("UUIDMost", this.leashHolder.getUuid().getMostSignificantBits());
                nbtcompound1.putLong("UUIDLeast", this.leashHolder.getUuid().getLeastSignificantBits());
            } else if (this.leashHolder instanceof DecorationEntity) {
                BlockPos blockpos = ((DecorationEntity)this.leashHolder).getBlockPos();
                nbtcompound1.putInt("X", blockpos.getX());
                nbtcompound1.putInt("Y", blockpos.getY());
                nbtcompound1.putInt("Z", blockpos.getZ());
            }

            nbt.put("Leash", nbtcompound1);
        }

        if (this.isNoAi()) {
            nbt.putBoolean("NoAI", this.isNoAi());
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("CanPickUpLoot", 1)) {
            this.setCanPickupLoot(nbt.getBoolean("CanPickUpLoot"));
        }

        this.persistent = nbt.getBoolean("PersistenceRequired");
        if (nbt.contains("Equipment", 9)) {
            NbtList nbtlist = nbt.getList("Equipment", 10);

            for (int i = 0; i < this.equipment.length; i++) {
                this.equipment[i] = ItemStack.fromNbt(nbtlist.getCompound(i));
            }
        }

        if (nbt.contains("DropChances", 9)) {
            NbtList nbtlist1 = nbt.getList("DropChances", 5);

            for (int j = 0; j < nbtlist1.size(); j++) {
                this.inventoryDropChances[j] = nbtlist1.getFloat(j);
            }
        }

        this.leashed = nbt.getBoolean("Leashed");
        if (this.leashed && nbt.contains("Leash", 10)) {
            this.leashNbt = nbt.getCompound("Leash");
        }

        this.setNoAi(nbt.getBoolean("NoAI"));
    }

    public void setForwardsSpeed(float speed) {
        this.forwardSpeed = speed;
    }

    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setForwardsSpeed(speed);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        this.world.profiler.push("looting");
        if (!this.world.isClient && this.canPickupLoot() && !this.dead && this.world.getGameRules().getBoolean("mobGriefing")) {
            for (ItemEntity itementity : this.world.getEntitiesOfType(ItemEntity.class, this.getShape().grown(1.0, 0.0, 1.0))) {
                if (!itementity.removed && itementity.getItem() != null && !itementity.hasPickUpDelay()) {
                    this.updateInventory(itementity);
                }
            }
        }

        this.world.profiler.pop();
    }

    protected void updateInventory(ItemEntity item) {
        ItemStack itemstack = item.getItem();
        int i = getEquipmentSlot(itemstack);
        if (i > -1) {
            boolean flag = true;
            ItemStack itemstack1 = this.getEquipment(i);
            if (itemstack1 != null) {
                if (i == 0) {
                    if (itemstack.getItem() instanceof SwordItem && !(itemstack1.getItem() instanceof SwordItem)) {
                        flag = true;
                    } else if (itemstack.getItem() instanceof SwordItem && itemstack1.getItem() instanceof SwordItem) {
                        SwordItem sworditem = (SwordItem)itemstack.getItem();
                        SwordItem sworditem1 = (SwordItem)itemstack1.getItem();
                        if (sworditem.getAttackDamage() != sworditem1.getAttackDamage()) {
                            flag = sworditem.getAttackDamage() > sworditem1.getAttackDamage();
                        } else {
                            flag = itemstack.getMetadata() > itemstack1.getMetadata() || itemstack.hasNbt() && !itemstack1.hasNbt();
                        }
                    } else if (itemstack.getItem() instanceof BowItem && itemstack1.getItem() instanceof BowItem) {
                        flag = itemstack.hasNbt() && !itemstack1.hasNbt();
                    } else {
                        flag = false;
                    }
                } else if (itemstack.getItem() instanceof ArmorItem && !(itemstack1.getItem() instanceof ArmorItem)) {
                    flag = true;
                } else if (itemstack.getItem() instanceof ArmorItem && itemstack1.getItem() instanceof ArmorItem) {
                    ArmorItem armoritem = (ArmorItem)itemstack.getItem();
                    ArmorItem armoritem1 = (ArmorItem)itemstack1.getItem();
                    if (armoritem.protection != armoritem1.protection) {
                        flag = armoritem.protection > armoritem1.protection;
                    } else {
                        flag = itemstack.getMetadata() > itemstack1.getMetadata() || itemstack.hasNbt() && !itemstack1.hasNbt();
                    }
                } else {
                    flag = false;
                }
            }

            if (flag && this.canHoldItem(itemstack)) {
                if (itemstack1 != null && this.random.nextFloat() - 0.1F < this.inventoryDropChances[i]) {
                    this.dropItem(itemstack1, 0.0F);
                }

                if (itemstack.getItem() == Items.DIAMOND && item.getThrower() != null) {
                    PlayerEntity playerentity = this.world.getPlayer(item.getThrower());
                    if (playerentity != null) {
                        playerentity.incrementStat(Achievements.GIVE_DIAMOND);
                    }
                }

                this.setEquipment(i, itemstack);
                this.inventoryDropChances[i] = 2.0F;
                this.persistent = true;
                this.sendPickup(item, 1);
                item.remove();
            }
        }
    }

    protected boolean canHoldItem(ItemStack item) {
        return true;
    }

    protected boolean canDespawn() {
        return true;
    }

    protected void checkDespawn() {
        if (this.persistent) {
            this.farFromPlayerTicks = 0;
        } else {
            Entity entity = this.world.getNearestPlayer(this, -1.0);
            if (entity != null) {
                double d0 = entity.x - this.x;
                double d1 = entity.y - this.y;
                double d2 = entity.z - this.z;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (this.canDespawn() && d3 > 16384.0) {
                    this.remove();
                }

                if (this.farFromPlayerTicks > 600 && this.random.nextInt(800) == 0 && d3 > 1024.0 && this.canDespawn()) {
                    this.remove();
                } else if (d3 < 1024.0) {
                    this.farFromPlayerTicks = 0;
                }
            }
        }
    }

    @Override
    protected final void serverTickAi() {
        this.farFromPlayerTicks++;
        this.world.profiler.push("checkDespawn");
        this.checkDespawn();
        this.world.profiler.pop();
        this.world.profiler.push("sensing");
        this.mobVisibilityCache.clear();
        this.world.profiler.pop();
        this.world.profiler.push("targetSelector");
        this.targetSelector.tick();
        this.world.profiler.pop();
        this.world.profiler.push("goalSelector");
        this.goalSelector.tick();
        this.world.profiler.pop();
        this.world.profiler.push("navigation");
        this.entityNavigation.tick();
        this.world.profiler.pop();
        this.world.profiler.push("mob tick");
        this.mobAiTick();
        this.world.profiler.pop();
        this.world.profiler.push("controls");
        this.world.profiler.push("move");
        this.movementControl.tick();
        this.world.profiler.swap("look");
        this.lookControl.tick();
        this.world.profiler.swap("jump");
        this.jumpControl.tick();
        this.world.profiler.pop();
        this.world.profiler.pop();
    }

    protected void mobAiTick() {
    }

    public int getLookPitchSpeed() {
        return 40;
    }

    public void lookAt(Entity target, float maxYawChange, float maxPitchChange) {
        double d0 = target.x - this.x;
        double d2 = target.z - this.z;
        double d1;
        if (target instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)target;
            d1 = livingentity.y + livingentity.getEyeHeight() - (this.y + this.getEyeHeight());
        } else {
            d1 = (target.getShape().minY + target.getShape().maxY) / 2.0 - (this.y + this.getEyeHeight());
        }

        double d3 = MathHelper.sqrt(d0 * d0 + d2 * d2);
        float f = (float)(MathHelper.fastAtan2(d2, d0) * 180.0 / (float) Math.PI) - 90.0F;
        float f1 = (float)(-(MathHelper.fastAtan2(d1, d3) * 180.0 / (float) Math.PI));
        this.pitch = this.changeAngle(this.pitch, f1, maxPitchChange);
        this.yaw = this.changeAngle(this.yaw, f, maxYawChange);
    }

    private float changeAngle(float oldAngle, float newAngle, float maxChangeInAngle) {
        float f = MathHelper.wrapDegrees(newAngle - oldAngle);
        if (f > maxChangeInAngle) {
            f = maxChangeInAngle;
        }

        if (f < -maxChangeInAngle) {
            f = -maxChangeInAngle;
        }

        return oldAngle + f;
    }

    public boolean canSpawn() {
        return true;
    }

    public boolean isUnobstructed() {
        return this.world.isUnobstructed(this.getShape(), this)
            && this.world.getCollisions(this, this.getShape()).isEmpty()
            && !this.world.containsLiquid(this.getShape());
    }

    public float getShadowScale() {
        return 1.0F;
    }

    public int getLimitPerChunk() {
        return 4;
    }

    @Override
    public int getSafeFallDistance() {
        if (this.getAttackTarget() == null) {
            return 3;
        }

        int i = (int)(this.getHealth() - this.getMaxHealth() * 0.33F);
        i -= (3 - this.world.getDifficulty().getId()) * 4;
        if (i < 0) {
            i = 0;
        }

        return i + 3;
    }

    @Override
    public ItemStack getDisplayItemInHand() {
        return this.equipment[0];
    }

    @Override
    public ItemStack getEquipment(int slot) {
        return this.equipment[slot];
    }

    @Override
    public ItemStack getArmor(int slot) {
        return this.equipment[slot + 1];
    }

    @Override
    public void setEquipment(int slot, ItemStack item) {
        this.equipment[slot] = item;
    }

    @Override
    public ItemStack[] getEquipment() {
        return this.equipment;
    }

    @Override
    protected void dropEquipment(boolean hitByPlayer, int lootingLevel) {
        for (int i = 0; i < this.getEquipment().length; i++) {
            ItemStack itemstack = this.getEquipment(i);
            boolean flag = this.inventoryDropChances[i] > 1.0F;
            if (itemstack != null && (hitByPlayer || flag) && this.random.nextFloat() - lootingLevel * 0.01F < this.inventoryDropChances[i]) {
                if (!flag && itemstack.isDamageable()) {
                    int j = Math.max(itemstack.getMaxDamage() - 25, 1);
                    int k = itemstack.getMaxDamage() - this.random.nextInt(this.random.nextInt(j) + 1);
                    if (k > j) {
                        k = j;
                    }

                    if (k < 1) {
                        k = 1;
                    }

                    itemstack.setDamage(k);
                }

                this.dropItem(itemstack, 0.0F);
            }
        }
    }

    protected void addRandomEquipment(LocalDifficulty difficulty) {
        if (this.random.nextFloat() < 0.15F * difficulty.getMultiplier()) {
            int i = this.random.nextInt(2);
            float f = this.world.getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            if (this.random.nextFloat() < 0.095F) {
                i++;
            }

            if (this.random.nextFloat() < 0.095F) {
                i++;
            }

            if (this.random.nextFloat() < 0.095F) {
                i++;
            }

            for (int j = 3; j >= 0; j--) {
                ItemStack itemstack = this.getArmor(j);
                if (j < 3 && this.random.nextFloat() < f) {
                    break;
                }

                if (itemstack == null) {
                    Item item = getEquipmentForSlot(j + 1, i);
                    if (item != null) {
                        this.setEquipment(j + 1, new ItemStack(item));
                    }
                }
            }
        }
    }

    public static int getEquipmentSlot(ItemStack equipment) {
        if (equipment.getItem() == Item.byBlock(Blocks.PUMPKIN) || equipment.getItem() == Items.SKULL) {
            return 4;
        }

        if (equipment.getItem() instanceof ArmorItem) {
            switch (((ArmorItem)equipment.getItem()).slot) {
                case 0:
                    return 4;
                case 1:
                    return 3;
                case 2:
                    return 2;
                case 3:
                    return 1;
            }
        }

        return 0;
    }

    public static Item getEquipmentForSlot(int slot, int equipmentLevel) {
        switch (slot) {
            case 4:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_HELMET;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_HELMET;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case 3:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case 2:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case 1:
                if (equipmentLevel == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (equipmentLevel == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (equipmentLevel == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (equipmentLevel == 3) {
                    return Items.IRON_BOOTS;
                } else if (equipmentLevel == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void enchantEquipment(LocalDifficulty difficulty) {
        float f = difficulty.getMultiplier();
        if (this.getDisplayItemInHand() != null && this.random.nextFloat() < 0.25F * f) {
            EnchantmentHelper.addRandomEnchantment(this.random, this.getDisplayItemInHand(), (int)(5.0F + f * this.random.nextInt(18)));
        }

        for (int i = 0; i < 4; i++) {
            ItemStack itemstack = this.getArmor(i);
            if (itemstack != null && this.random.nextFloat() < 0.5F * f) {
                EnchantmentHelper.addRandomEnchantment(this.random, itemstack, (int)(5.0F + f * this.random.nextInt(18)));
            }
        }
    }

    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).addModifier(new AttributeModifier("Random spawn bonus", this.random.nextGaussian() * 0.05, 1));
        return data;
    }

    public boolean canBeControlledByRider() {
        return false;
    }

    public void setPersistent() {
        this.persistent = true;
    }

    public void setInventoryDropChances(int arrayIndex, float value) {
        this.inventoryDropChances[arrayIndex] = value;
    }

    public boolean canPickupLoot() {
        return this.canPickupLoot;
    }

    public void setCanPickupLoot(boolean blValue) {
        this.canPickupLoot = blValue;
    }

    public boolean isPersistent() {
        return this.persistent;
    }

    @Override
    public final boolean interact(PlayerEntity player) {
        if (this.isLeashed() && this.getLeashHolder() == player) {
            this.detachLeash(true, !player.abilities.creativeMode);
            return true;
        }

        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.LEAD && this.isTameable()) {
            if (!(this instanceof TameableEntity) || !((TameableEntity)this).isTamed()) {
                this.attachLeash(player, true);
                itemstack.size--;
                return true;
            }

            if (((TameableEntity)this).isOwner(player)) {
                this.attachLeash(player, true);
                itemstack.size--;
                return true;
            }
        }

        return this.interactMob(player) || super.interact(player);
    }

    protected boolean interactMob(PlayerEntity player) {
        return false;
    }

    protected void updateLeashStatus() {
        if (this.leashNbt != null) {
            this.readLeashNbt();
        }

        if (this.leashed) {
            if (!this.isAlive()) {
                this.detachLeash(true, true);
            }

            if (this.leashHolder == null || this.leashHolder.removed) {
                this.detachLeash(true, true);
            }
        }
    }

    public void detachLeash(boolean sync, boolean dropItem) {
        if (this.leashed) {
            this.leashed = false;
            this.leashHolder = null;
            if (!this.world.isClient && dropItem) {
                this.dropItem(Items.LEAD, 1);
            }

            if (!this.world.isClient && sync && this.world instanceof ServerWorld) {
                ((ServerWorld)this.world).getEntityMap().sendPacket(this, new AttachEntityS2CPacket(1, this, null));
            }
        }
    }

    public boolean isTameable() {
        return !this.isLeashed() && !(this instanceof Monster);
    }

    public boolean isLeashed() {
        return this.leashed;
    }

    public Entity getLeashHolder() {
        return this.leashHolder;
    }

    public void attachLeash(Entity leashHolder, boolean sync) {
        this.leashed = true;
        this.leashHolder = leashHolder;
        if (!this.world.isClient && sync && this.world instanceof ServerWorld) {
            ((ServerWorld)this.world).getEntityMap().sendPacket(this, new AttachEntityS2CPacket(1, this, this.leashHolder));
        }
    }

    private void readLeashNbt() {
        if (this.leashed && this.leashNbt != null) {
            if (this.leashNbt.contains("UUIDMost", 4) && this.leashNbt.contains("UUIDLeast", 4)) {
                UUID uuid = new UUID(this.leashNbt.getLong("UUIDMost"), this.leashNbt.getLong("UUIDLeast"));

                for (LivingEntity livingentity : this.world.getEntitiesOfType(LivingEntity.class, this.getShape().grown(10.0, 10.0, 10.0))) {
                    if (livingentity.getUuid().equals(uuid)) {
                        this.leashHolder = livingentity;
                        break;
                    }
                }
            } else if (this.leashNbt.contains("X", 99) && this.leashNbt.contains("Y", 99) && this.leashNbt.contains("Z", 99)) {
                BlockPos blockpos = new BlockPos(this.leashNbt.getInt("X"), this.leashNbt.getInt("Y"), this.leashNbt.getInt("Z"));
                LeadKnotEntity leadknotentity = LeadKnotEntity.getOrCreate(this.world, blockpos);
                if (leadknotentity == null) {
                    leadknotentity = LeadKnotEntity.attatch(this.world, blockpos);
                }

                this.leashHolder = leadknotentity;
            } else {
                this.detachLeash(false, true);
            }
        }

        this.leashNbt = null;
    }

    @Override
    public boolean replaceItem(int slot, ItemStack item) {
        int i;
        if (slot == 99) {
            i = 0;
        } else {
            i = slot - 100 + 1;
            if (i < 0 || i >= this.equipment.length) {
                return false;
            }
        }

        if (item != null && getEquipmentSlot(item) != i && (i != 4 || !(item.getItem() instanceof BlockItem))) {
            return false;
        }

        this.setEquipment(i, item);
        return true;
    }

    @Override
    public boolean isLocallyControlled() {
        return super.isLocallyControlled() && !this.isNoAi();
    }

    public void setNoAi(boolean noAi) {
        this.syncedData.update(15, Byte.valueOf((byte)(noAi ? 1 : 0)));
    }

    public boolean isNoAi() {
        return this.syncedData.getByte(15) != 0;
    }

    public enum SpawnEnvironment {
        ON_GROUND,
        IN_AIR,
        IN_WATER;
    }
}
