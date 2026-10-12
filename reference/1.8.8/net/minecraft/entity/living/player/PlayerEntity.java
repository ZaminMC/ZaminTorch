package net.minecraft.entity.living.player;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.monster.MonsterEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragon;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonPart;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.InventoryLock;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.MenuProvider;
import net.minecraft.inventory.menu.PlayerMenu;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.UseAction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.EntityVelocityS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.village.trade.Trader;

public abstract class PlayerEntity extends LivingEntity {
    public PlayerInventory inventory = new PlayerInventory(this);
    private EnderChestInventory enderchest = new EnderChestInventory();
    public InventoryMenu playerMenu;
    public InventoryMenu menu;
    protected HungerManager hungerManager = new HungerManager();
    /**
     * A timer to check if the jump button was pressed twice. You need to press the jump button twice
     * in a 7 tick interval for it to activate. Is used to make players fly in creative.
     */
    protected int pressedJumpTwiceTimer;
    public float lastBob;
    public float bob;
    public int xpCooldown;
    public double lastCapeX;
    public double lastCapeY;
    public double lastCapeZ;
    public double capeX;
    public double capeY;
    public double capeZ;
    protected boolean sleeping;
    public BlockPos sleepingPos;
    private int sleepingTime;
    public float sleepingCameraOffsetX;
    public float sleepingCameraOffsetY;
    public float sleepingCameraOffsetZ;
    private BlockPos spawnPoint;
    private boolean respawnForced;
    private BlockPos minecartTravelStartPos;
    public PlayerAbilities abilities = new PlayerAbilities();
    public int xpLevel;
    public int xp;
    public float xpProgress;
    private int enchantmentTableSeed;
    private ItemStack itemInUse;
    /**
     * The amount of ticks the current item being used has been in use for.
     */
    private int itemUseTimer;
    protected float speed = 0.1F;
    protected float flyingSpeed = 0.02F;
    private int lastXpSoundTime;
    private final GameProfile profile;
    private boolean reducedDebugInfo = false;
    public FishingBobberEntity fishingBobber;

    public PlayerEntity(World world, GameProfile profile) {
        super(world);
        this.uuid = getUuid(profile);
        this.profile = profile;
        this.playerMenu = new PlayerMenu(this.inventory, !world.isClient, this);
        this.menu = this.playerMenu;
        BlockPos blockpos = world.getSpawnPoint();
        this.setPositionAndAngles(blockpos.getX() + 0.5, blockpos.getY() + 1, blockpos.getZ() + 0.5, 0.0F, 0.0F);
        this.rotationOffset = 180.0F;
        this.safeOnFireTime = 20;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributes().register(EntityAttributes.ATTACK_DAMAGE).setBase(1.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.1F);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)0);
        this.syncedData.register(17, 0.0F);
        this.syncedData.register(18, 0);
        this.syncedData.register(10, (byte)0);
    }

    public ItemStack getItemInUse() {
        return this.itemInUse;
    }

    public int getItemUseTimer() {
        return this.itemUseTimer;
    }

    public boolean hasItemInUse() {
        return this.itemInUse != null;
    }

    public int getRemainingItemUseDuration() {
        return this.hasItemInUse() ? this.itemInUse.getUseDuration() - this.itemUseTimer : 0;
    }

    public void stopUsingItem() {
        if (this.itemInUse != null) {
            this.itemInUse.stopUsing(this.world, this, this.itemUseTimer);
        }

        this.clearItemInUse();
    }

    public void clearItemInUse() {
        this.itemInUse = null;
        this.itemUseTimer = 0;
        if (!this.world.isClient) {
            this.setUsingItem(false);
        }
    }

    public boolean isSwordBlocking() {
        return this.hasItemInUse() && this.itemInUse.getItem().getUseAction(this.itemInUse) == UseAction.BLOCK;
    }

    @Override
    public void tick() {
        this.noClip = this.isSpectator();
        if (this.isSpectator()) {
            this.onGround = false;
        }

        if (this.itemInUse != null) {
            ItemStack itemstack = this.inventory.getSelectedItem();
            if (itemstack == this.itemInUse) {
                if (this.itemUseTimer <= 25 && this.itemUseTimer % 4 == 0) {
                    this.onConsumeItem(itemstack, 5);
                }

                if (--this.itemUseTimer == 0 && !this.world.isClient) {
                    this.finishUsingItem();
                }
            } else {
                this.clearItemInUse();
            }
        }

        if (this.xpCooldown > 0) {
            this.xpCooldown--;
        }

        if (this.isSleeping()) {
            this.sleepingTime++;
            if (this.sleepingTime > 100) {
                this.sleepingTime = 100;
            }

            if (!this.world.isClient) {
                if (!this.checkSleepingPosition()) {
                    this.wakeUp(true, true, false);
                } else if (this.world.isSunny()) {
                    this.wakeUp(false, true, true);
                }
            }
        } else if (this.sleepingTime > 0) {
            this.sleepingTime++;
            if (this.sleepingTime >= 110) {
                this.sleepingTime = 0;
            }
        }

        super.tick();
        if (!this.world.isClient && this.menu != null && !this.menu.isValid(this)) {
            this.closeMenu();
            this.menu = this.playerMenu;
        }

        if (this.isOnFire() && this.abilities.invulnerable) {
            this.extinguish();
        }

        this.lastCapeX = this.capeX;
        this.lastCapeY = this.capeY;
        this.lastCapeZ = this.capeZ;
        double d5 = this.x - this.capeX;
        double d0 = this.y - this.capeY;
        double d1 = this.z - this.capeZ;
        double d2 = 10.0;
        if (d5 > d2) {
            this.lastCapeX = this.capeX = this.x;
        }

        if (d1 > d2) {
            this.lastCapeZ = this.capeZ = this.z;
        }

        if (d0 > d2) {
            this.lastCapeY = this.capeY = this.y;
        }

        if (d5 < -d2) {
            this.lastCapeX = this.capeX = this.x;
        }

        if (d1 < -d2) {
            this.lastCapeZ = this.capeZ = this.z;
        }

        if (d0 < -d2) {
            this.lastCapeY = this.capeY = this.y;
        }

        this.capeX += d5 * 0.25;
        this.capeZ += d1 * 0.25;
        this.capeY += d0 * 0.25;
        if (this.vehicle == null) {
            this.minecartTravelStartPos = null;
        }

        if (!this.world.isClient) {
            this.hungerManager.tick(this);
            this.incrementStat(Stats.MINUTES_PLAYED);
            if (this.isAlive()) {
                this.incrementStat(Stats.TIME_SINCE_DEATH);
            }
        }

        int i = 29999999;
        double d3 = MathHelper.clamp(this.x, -2.9999999E7, 2.9999999E7);
        double d4 = MathHelper.clamp(this.z, -2.9999999E7, 2.9999999E7);
        if (d3 != this.x || d4 != this.z) {
            this.setPosition(d3, this.y, d4);
        }
    }

    @Override
    public int getMaxNetherPortalTime() {
        return this.abilities.invulnerable ? 0 : 80;
    }

    @Override
    protected String getSwimSound() {
        return "game.player.swim";
    }

    @Override
    protected String getSplashSound() {
        return "game.player.swim.splash";
    }

    @Override
    public int getPortalCooldown() {
        return 10;
    }

    @Override
    public void playSound(String id, float volume, float pitch) {
        this.world.playSound(this, id, volume, pitch);
    }

    protected void onConsumeItem(ItemStack item, int particleCount) {
        if (item.getUseAction() == UseAction.DRINK) {
            this.playSound("random.drink", 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
        }

        if (item.getUseAction() == UseAction.EAT) {
            for (int i = 0; i < particleCount; i++) {
                Vec3d vec3d = new Vec3d((this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
                vec3d = vec3d.rotateX(-this.pitch * (float) Math.PI / 180.0F);
                vec3d = vec3d.rotateY(-this.yaw * (float) Math.PI / 180.0F);
                double d0 = -this.random.nextFloat() * 0.6 - 0.3;
                Vec3d vec3d1 = new Vec3d((this.random.nextFloat() - 0.5) * 0.3, d0, 0.6);
                vec3d1 = vec3d1.rotateX(-this.pitch * (float) Math.PI / 180.0F);
                vec3d1 = vec3d1.rotateY(-this.yaw * (float) Math.PI / 180.0F);
                vec3d1 = vec3d1.add(this.x, this.y + this.getEyeHeight(), this.z);
                if (item.hasCustomData()) {
                    this.world
                        .addParticle(
                            ParticleType.ITEM_CRACK,
                            vec3d1.x,
                            vec3d1.y,
                            vec3d1.z,
                            vec3d.x,
                            vec3d.y + 0.05,
                            vec3d.z,
                            Item.getId(item.getItem()),
                            item.getMetadata()
                        );
                } else {
                    this.world.addParticle(ParticleType.ITEM_CRACK, vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05, vec3d.z, Item.getId(item.getItem()));
                }
            }

            this.playSound("random.eat", 0.5F + 0.5F * this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }
    }

    protected void finishUsingItem() {
        if (this.itemInUse != null) {
            this.onConsumeItem(this.itemInUse, 16);
            int i = this.itemInUse.size;
            ItemStack itemstack = this.itemInUse.finishUsing(this.world, this);
            if (itemstack != this.itemInUse || itemstack != null && itemstack.size != i) {
                this.inventory.items[this.inventory.selectedSlot] = itemstack;
                if (itemstack.size == 0) {
                    this.inventory.items[this.inventory.selectedSlot] = null;
                }
            }

            this.clearItemInUse();
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 9) {
            this.finishUsingItem();
        } else if (event == 23) {
            this.reducedDebugInfo = false;
        } else if (event == 22) {
            this.reducedDebugInfo = true;
        } else {
            super.doEvent(event);
        }
    }

    @Override
    protected boolean isDead() {
        return this.getHealth() <= 0.0F || this.isSleeping();
    }

    protected void closeMenu() {
        this.menu = this.playerMenu;
    }

    @Override
    public void rideTick() {
        if (!this.world.isClient && this.isSneaking()) {
            this.startRiding(null);
            this.setSneaking(false);
        } else {
            double d0 = this.x;
            double d1 = this.y;
            double d2 = this.z;
            float f = this.yaw;
            float f1 = this.pitch;
            super.rideTick();
            this.lastBob = this.bob;
            this.bob = 0.0F;
            this.tickRidingRelatedStats(this.x - d0, this.y - d1, this.z - d2);
            if (this.vehicle instanceof PigEntity) {
                this.pitch = f1;
                this.yaw = f;
                this.bodyYaw = ((PigEntity)this.vehicle).bodyYaw;
            }
        }
    }

    @Override
    public void resetPos() {
        this.setSize(0.6F, 1.8F);
        super.resetPos();
        this.setHealth(this.getMaxHealth());
        this.deathTicks = 0;
    }

    @Override
    protected void serverTickAi() {
        super.serverTickAi();
        this.updateArmSwing();
        this.headYaw = this.yaw;
    }

    @Override
    public void mobTick() {
        if (this.pressedJumpTwiceTimer > 0) {
            this.pressedJumpTwiceTimer--;
        }

        if (this.world.getDifficulty() == Difficulty.PEACEFUL && this.world.getGameRules().getBoolean("naturalRegeneration")) {
            if (this.getHealth() < this.getMaxHealth() && this.ticks % 20 == 0) {
                this.heal(1.0F);
            }

            if (this.hungerManager.needsFood() && this.ticks % 10 == 0) {
                this.hungerManager.setFoodLevel(this.hungerManager.getFoodLevel() + 1);
            }
        }

        this.inventory.tick();
        this.lastBob = this.bob;
        super.mobTick();
        EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
        if (!this.world.isClient) {
            entityattributeinstance.setBase(this.abilities.getWalkSpeed());
        }

        this.flyingSpeed = this.flyingSpeed;
        if (this.isSprinting()) {
            this.flyingSpeed = (float)(this.flyingSpeed + this.flyingSpeed * 0.3);
        }

        this.setSpeed((float)entityattributeinstance.get());
        float f = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        float f1 = (float)(Math.atan(-this.velocityY * 0.2F) * 15.0);
        if (f > 0.1F) {
            f = 0.1F;
        }

        if (!this.onGround || this.getHealth() <= 0.0F) {
            f = 0.0F;
        }

        if (this.onGround || this.getHealth() <= 0.0F) {
            f1 = 0.0F;
        }

        this.bob = this.bob + (f - this.bob) * 0.4F;
        this.tilt = this.tilt + (f1 - this.tilt) * 0.8F;
        if (this.getHealth() > 0.0F && !this.isSpectator()) {
            Box box = null;
            if (this.vehicle != null && !this.vehicle.removed) {
                box = this.getShape().union(this.vehicle.getShape()).grown(1.0, 0.0, 1.0);
            } else {
                box = this.getShape().grown(1.0, 0.5, 1.0);
            }

            List<Entity> list = this.world.getEntities(this, box);

            for (int i = 0; i < list.size(); i++) {
                Entity entity = list.get(i);
                if (!entity.removed) {
                    this.onEntityCollision(entity);
                }
            }
        }
    }

    private void onEntityCollision(Entity entity) {
        entity.onPlayerCollision(this);
    }

    public int getScore() {
        return this.syncedData.getInt(18);
    }

    public void setScore(int score) {
        this.syncedData.update(18, score);
    }

    public void addToScore(int amount) {
        int i = this.getScore();
        this.syncedData.update(18, i + amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        this.setSize(0.2F, 0.2F);
        this.setPosition(this.x, this.y, this.z);
        this.velocityY = 0.1F;
        if (this.getName().equals("Notch")) {
            this.dropItem(new ItemStack(Items.APPLE, 1), true, false);
        }

        if (!this.world.getGameRules().getBoolean("keepInventory")) {
            this.inventory.dropAll();
        }

        if (source != null) {
            this.velocityX = -MathHelper.cos((this.damagedSwingDir + this.yaw) * (float) Math.PI / 180.0F) * 0.1F;
            this.velocityZ = -MathHelper.sin((this.damagedSwingDir + this.yaw) * (float) Math.PI / 180.0F) * 0.1F;
        } else {
            this.velocityX = this.velocityZ = 0.0;
        }

        this.incrementStat(Stats.DEATHS);
        this.clearStat(Stats.TIME_SINCE_DEATH);
    }

    @Override
    protected String getHurtSound() {
        return "game.player.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "game.player.die";
    }

    @Override
    public void takeKillScore(Entity victim, int score) {
        this.addToScore(score);
        Collection<ScoreboardObjective> collection = this.getScoreboard().getObjectives(ScoreboardCriterion.TOTAL_KILL_COUNT);
        if (victim instanceof PlayerEntity) {
            this.incrementStat(Stats.PLAYERS_KILLED);
            collection.addAll(this.getScoreboard().getObjectives(ScoreboardCriterion.PLAYER_KILL_COUNT));
            collection.addAll(this.handleTeamKill(victim));
        } else {
            this.incrementStat(Stats.MOBS_KILLED);
        }

        for (ScoreboardObjective scoreboardobjective : collection) {
            ScoreboardScore scoreboardscore = this.getScoreboard().getScore(this.getName(), scoreboardobjective);
            scoreboardscore.increment();
        }
    }

    private Collection<ScoreboardObjective> handleTeamKill(Entity entity) {
        Team team = this.getScoreboard().getTeamOfMember(this.getName());
        if (team != null) {
            int i = team.getColor().getId();
            if (i >= 0 && i < ScoreboardCriterion.KILLED_BY_TEAM_BY_COLOR.length) {
                for (ScoreboardObjective scoreboardobjective : this.getScoreboard().getObjectives(ScoreboardCriterion.KILLED_BY_TEAM_BY_COLOR[i])) {
                    ScoreboardScore scoreboardscore = this.getScoreboard().getScore(entity.getName(), scoreboardobjective);
                    scoreboardscore.increment();
                }
            }
        }

        Team team1 = this.getScoreboard().getTeamOfMember(entity.getName());
        if (team1 != null) {
            int j = team1.getColor().getId();
            if (j >= 0 && j < ScoreboardCriterion.TEAM_KILL_BY_COLOR.length) {
                return this.getScoreboard().getObjectives(ScoreboardCriterion.TEAM_KILL_BY_COLOR[j]);
            }
        }

        return Lists.newArrayList();
    }

    public ItemEntity dropItem(boolean whole) {
        return this.dropItem(
            this.inventory
                .removeItem(this.inventory.selectedSlot, whole && this.inventory.getSelectedItem() != null ? this.inventory.getSelectedItem().size : 1),
            false,
            true
        );
    }

    public ItemEntity dropItem(ItemStack item, boolean dead) {
        return this.dropItem(item, false, false);
    }

    public ItemEntity dropItem(ItemStack item, boolean velocityFromPlayerDirection, boolean thrownByPlayer) {
        if (item == null) {
            return null;
        }

        if (item.size == 0) {
            return null;
        }

        double d0 = this.y - 0.3F + this.getEyeHeight();
        ItemEntity itementity = new ItemEntity(this.world, this.x, d0, this.z, item);
        itementity.setPickUpDelay(40);
        if (thrownByPlayer) {
            itementity.setThrower(this.getName());
        }

        if (velocityFromPlayerDirection) {
            float f = this.random.nextFloat() * 0.5F;
            float f1 = this.random.nextFloat() * (float) Math.PI * 2.0F;
            itementity.velocityX = -MathHelper.sin(f1) * f;
            itementity.velocityZ = MathHelper.cos(f1) * f;
            itementity.velocityY = 0.2F;
        } else {
            float f2 = 0.3F;
            itementity.velocityX = -MathHelper.sin(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f2;
            itementity.velocityZ = MathHelper.cos(this.yaw / 180.0F * (float) Math.PI) * MathHelper.cos(this.pitch / 180.0F * (float) Math.PI) * f2;
            itementity.velocityY = -MathHelper.sin(this.pitch / 180.0F * (float) Math.PI) * f2 + 0.1F;
            float f3 = this.random.nextFloat() * (float) Math.PI * 2.0F;
            f2 = 0.02F * this.random.nextFloat();
            itementity.velocityX = itementity.velocityX + Math.cos(f3) * f2;
            itementity.velocityY = itementity.velocityY + (this.random.nextFloat() - this.random.nextFloat()) * 0.1F;
            itementity.velocityZ = itementity.velocityZ + Math.sin(f3) * f2;
        }

        this.spawnItem(itementity);
        if (thrownByPlayer) {
            this.incrementStat(Stats.DROPS);
        }

        return itementity;
    }

    protected void spawnItem(ItemEntity item) {
        this.world.addEntity(item);
    }

    public float getMiningSpeed(Block block) {
        float f = this.inventory.getMiningSpeed(block);
        if (f > 1.0F) {
            int i = EnchantmentHelper.getEfficiencyLevel(this);
            ItemStack itemstack = this.inventory.getSelectedItem();
            if (i > 0 && itemstack != null) {
                f += i * i + 1;
            }
        }

        if (this.hasStatusEffect(StatusEffect.HASTE)) {
            f *= 1.0F + (this.getEffectInstance(StatusEffect.HASTE).getAmplifier() + 1) * 0.2F;
        }

        if (this.hasStatusEffect(StatusEffect.MINING_FATIGUE)) {
            float f1 = 1.0F;
            switch (this.getEffectInstance(StatusEffect.MINING_FATIGUE).getAmplifier()) {
                case 0:
                    f1 = 0.3F;
                    break;
                case 1:
                    f1 = 0.09F;
                    break;
                case 2:
                    f1 = 0.0027F;
                    break;
                case 3:
                default:
                    f1 = 8.1E-4F;
            }

            f *= f1;
        }

        if (this.isSubmergedIn(Material.WATER) && !EnchantmentHelper.getAquaAffinityLevel(this)) {
            f /= 5.0F;
        }

        if (!this.onGround) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean canMineBlock(Block block) {
        return this.inventory.canMineBlock(block);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.uuid = getUuid(this.profile);
        NbtList nbtlist = nbt.getList("Inventory", 10);
        this.inventory.readNbt(nbtlist);
        this.inventory.selectedSlot = nbt.getInt("SelectedItemSlot");
        this.sleeping = nbt.getBoolean("Sleeping");
        this.sleepingTime = nbt.getShort("SleepTimer");
        this.xpProgress = nbt.getFloat("XpP");
        this.xpLevel = nbt.getInt("XpLevel");
        this.xp = nbt.getInt("XpTotal");
        this.enchantmentTableSeed = nbt.getInt("XpSeed");
        if (this.enchantmentTableSeed == 0) {
            this.enchantmentTableSeed = this.random.nextInt();
        }

        this.setScore(nbt.getInt("Score"));
        if (this.sleeping) {
            this.sleepingPos = new BlockPos(this);
            this.wakeUp(true, true, false);
        }

        if (nbt.contains("SpawnX", 99) && nbt.contains("SpawnY", 99) && nbt.contains("SpawnZ", 99)) {
            this.spawnPoint = new BlockPos(nbt.getInt("SpawnX"), nbt.getInt("SpawnY"), nbt.getInt("SpawnZ"));
            this.respawnForced = nbt.getBoolean("SpawnForced");
        }

        this.hungerManager.readNbt(nbt);
        this.abilities.readNbt(nbt);
        if (nbt.contains("EnderItems", 9)) {
            NbtList nbtlist1 = nbt.getList("EnderItems", 10);
            this.enderchest.readNbt(nbtlist1);
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.put("Inventory", this.inventory.writeNbt(new NbtList()));
        nbt.putInt("SelectedItemSlot", this.inventory.selectedSlot);
        nbt.putBoolean("Sleeping", this.sleeping);
        nbt.putShort("SleepTimer", (short)this.sleepingTime);
        nbt.putFloat("XpP", this.xpProgress);
        nbt.putInt("XpLevel", this.xpLevel);
        nbt.putInt("XpTotal", this.xp);
        nbt.putInt("XpSeed", this.enchantmentTableSeed);
        nbt.putInt("Score", this.getScore());
        if (this.spawnPoint != null) {
            nbt.putInt("SpawnX", this.spawnPoint.getX());
            nbt.putInt("SpawnY", this.spawnPoint.getY());
            nbt.putInt("SpawnZ", this.spawnPoint.getZ());
            nbt.putBoolean("SpawnForced", this.respawnForced);
        }

        this.hungerManager.writeNbt(nbt);
        this.abilities.writeNbt(nbt);
        nbt.put("EnderItems", this.enderchest.toNbt());
        ItemStack itemstack = this.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() != null) {
            nbt.put("SelectedItem", itemstack.writeNbt(new NbtCompound()));
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (this.abilities.invulnerable && !source.isOutOfWorld()) {
            return false;
        }

        this.farFromPlayerTicks = 0;
        if (this.getHealth() <= 0.0F) {
            return false;
        }

        if (this.isSleeping() && !this.world.isClient) {
            this.wakeUp(true, true, false);
        }

        if (source.isScaledWithDifficulty()) {
            if (this.world.getDifficulty() == Difficulty.PEACEFUL) {
                amount = 0.0F;
            }

            if (this.world.getDifficulty() == Difficulty.EASY) {
                amount = amount / 2.0F + 1.0F;
            }

            if (this.world.getDifficulty() == Difficulty.HARD) {
                amount = amount * 3.0F / 2.0F;
            }
        }

        if (amount == 0.0F) {
            return false;
        }

        Entity entity = source.getAttacker();
        if (entity instanceof ArrowEntity && ((ArrowEntity)entity).shooter != null) {
            entity = ((ArrowEntity)entity).shooter;
        }

        return super.takeDamage(source, amount);
    }

    public boolean canAttack(PlayerEntity player) {
        AbstractTeam abstractteam = this.getScoreboardTeam();
        AbstractTeam abstractteam1 = player.getScoreboardTeam();
        return abstractteam == null || !abstractteam.isAlliedTo(abstractteam1) || abstractteam.allowFriendlyFire();
    }

    @Override
    protected void damageArmor(float damage) {
        this.inventory.damageArmor(damage);
    }

    @Override
    public int getArmorProtection() {
        return this.inventory.getArmorProtection();
    }

    /**
     * @return the ratio of the number of the amount of armor equipped to the total armor slots
     */
    public float getArmorEquippedRatio() {
        int i = 0;

        for (ItemStack itemstack : this.inventory.armor) {
            if (itemstack != null) {
                i++;
            }
        }

        return (float)i / this.inventory.armor.length;
    }

    @Override
    protected void applyDamage(DamageSource source, float damage) {
        if (!this.isInvulnerable(source)) {
            if (!source.bypassesArmor() && this.isSwordBlocking() && damage > 0.0F) {
                damage = (1.0F + damage) * 0.5F;
            }

            damage = this.getDamageAfterArmor(source, damage);
            damage = this.getDamageAfterEffectsAndEnchantments(source, damage);
            float f = damage;
            damage = Math.max(damage - this.getAbsorption(), 0.0F);
            this.setAbsorption(this.getAbsorption() - (f - damage));
            if (damage != 0.0F) {
                this.addFatigue(source.getExhaustion());
                float f1 = this.getHealth();
                this.setHealth(this.getHealth() - damage);
                this.getDamageTracker().recordDamage(source, f1, damage);
                if (damage < 3.4028235E37F) {
                    this.incrementStat(Stats.DAMAGE_TAKEN, Math.round(damage * 10.0F));
                }
            }
        }
    }

    public void openSignEditor(SignBlockEntity sign) {
    }

    public void openCommandBlockMenu(CommandExecutor commandBlock) {
    }

    public void openTraderMenu(Trader trader) {
    }

    public void openChestMenu(Inventory inventory) {
    }

    public void openHorseMenu(HorseBaseEntity horse, Inventory inventory) {
    }

    public void openMenu(MenuProvider menuProvider) {
    }

    public void openEditBookScreen(ItemStack book) {
    }

    public boolean interact(Entity target) {
        if (this.isSpectator()) {
            if (target instanceof Inventory) {
                this.openChestMenu((Inventory)target);
            }

            return false;
        } else {
            ItemStack itemstack = this.getItemInHand();
            ItemStack itemstack1 = itemstack != null ? itemstack.copy() : null;
            if (!target.interact(this)) {
                if (itemstack != null && target instanceof LivingEntity) {
                    if (this.abilities.creativeMode) {
                        itemstack = itemstack1;
                    }

                    if (itemstack.interact(this, (LivingEntity)target)) {
                        if (itemstack.size <= 0 && !this.abilities.creativeMode) {
                            this.clearItemInHand();
                        }

                        return true;
                    }
                }

                return false;
            } else {
                if (itemstack != null && itemstack == this.getItemInHand()) {
                    if (itemstack.size <= 0 && !this.abilities.creativeMode) {
                        this.clearItemInHand();
                    } else if (itemstack.size < itemstack1.size && this.abilities.creativeMode) {
                        itemstack.size = itemstack1.size;
                    }
                }

                return true;
            }
        }
    }

    public ItemStack getItemInHand() {
        return this.inventory.getSelectedItem();
    }

    public void clearItemInHand() {
        this.inventory.setItem(this.inventory.selectedSlot, null);
    }

    @Override
    public double getRideHeight() {
        return -0.35;
    }

    public void attack(Entity target) {
        if (target.canBePunched()) {
            if (!target.onPunched(this)) {
                float f = (float)this.getAttribute(EntityAttributes.ATTACK_DAMAGE).get();
                int i = 0;
                float f1 = 0.0F;
                if (target instanceof LivingEntity) {
                    f1 = EnchantmentHelper.modifyDamage(this.getDisplayItemInHand(), ((LivingEntity)target).getMobType());
                } else {
                    f1 = EnchantmentHelper.modifyDamage(this.getDisplayItemInHand(), MobType.UNDEFINED);
                }

                i += EnchantmentHelper.getKnockbackLevel(this);
                if (this.isSprinting()) {
                    i++;
                }

                if (f > 0.0F || f1 > 0.0F) {
                    boolean flag = this.fallDistance > 0.0F
                        && !this.onGround
                        && !this.isClimbing()
                        && !this.isInWater()
                        && !this.hasStatusEffect(StatusEffect.BLINDNESS)
                        && this.vehicle == null
                        && target instanceof LivingEntity;
                    if (flag && f > 0.0F) {
                        f *= 1.5F;
                    }

                    f += f1;
                    boolean flag1 = false;
                    int j = EnchantmentHelper.getFireAspectLevel(this);
                    if (target instanceof LivingEntity && j > 0 && !target.isOnFire()) {
                        flag1 = true;
                        target.setOnFireFor(1);
                    }

                    double d0 = target.velocityX;
                    double d1 = target.velocityY;
                    double d2 = target.velocityZ;
                    boolean flag2 = target.takeDamage(DamageSource.player(this), f);
                    if (flag2) {
                        if (i > 0) {
                            target.addVelocity(
                                -MathHelper.sin(this.yaw * (float) Math.PI / 180.0F) * i * 0.5F,
                                0.1,
                                MathHelper.cos(this.yaw * (float) Math.PI / 180.0F) * i * 0.5F
                            );
                            this.velocityX *= 0.6;
                            this.velocityZ *= 0.6;
                            this.setSprinting(false);
                        }

                        if (target instanceof ServerPlayerEntity && target.damaged) {
                            ((ServerPlayerEntity)target).networkHandler.sendPacket(new EntityVelocityS2CPacket(target));
                            target.damaged = false;
                            target.velocityX = d0;
                            target.velocityY = d1;
                            target.velocityZ = d2;
                        }

                        if (flag) {
                            this.addCritParticles(target);
                        }

                        if (f1 > 0.0F) {
                            this.addEnchantedCritParticles(target);
                        }

                        if (f >= 18.0F) {
                            this.incrementStat(Achievements.DEAL_OVERKILL_DAMAGE);
                        }

                        this.setLastAttackedMob(target);
                        if (target instanceof LivingEntity) {
                            EnchantmentHelper.applyProtectionWildcard((LivingEntity)target, this);
                        }

                        EnchantmentHelper.applyDamageWildcard(this, target);
                        ItemStack itemstack = this.getItemInHand();
                        Entity entity = target;
                        if (target instanceof EnderDragonPart) {
                            EnderDragon enderdragon = ((EnderDragonPart)target).dragon;
                            if (enderdragon instanceof LivingEntity) {
                                entity = (LivingEntity)enderdragon;
                            }
                        }

                        if (itemstack != null && entity instanceof LivingEntity) {
                            itemstack.attack((LivingEntity)entity, this);
                            if (itemstack.size <= 0) {
                                this.clearItemInHand();
                            }
                        }

                        if (target instanceof LivingEntity) {
                            this.incrementStat(Stats.DAMAGE_DEALT, Math.round(f * 10.0F));
                            if (j > 0) {
                                target.setOnFireFor(j * 4);
                            }
                        }

                        this.addFatigue(0.3F);
                    } else if (flag1) {
                        target.extinguish();
                    }
                }
            }
        }
    }

    public void addCritParticles(Entity entity) {
    }

    public void addEnchantedCritParticles(Entity entity) {
    }

    public void respawn() {
    }

    @Override
    public void remove() {
        super.remove();
        this.playerMenu.close(this);
        if (this.menu != null) {
            this.menu.close(this);
        }
    }

    @Override
    public boolean isInWall() {
        return !this.sleeping && super.isInWall();
    }

    public boolean isLocal() {
        return false;
    }

    public GameProfile getGameProfile() {
        return this.profile;
    }

    public PlayerEntity.SleepAllowedStatus trySleep(BlockPos pos) {
        if (!this.world.isClient) {
            if (this.isSleeping() || !this.isAlive()) {
                return PlayerEntity.SleepAllowedStatus.OTHER_PROBLEM;
            }

            if (!this.world.dimension.isNatural()) {
                return PlayerEntity.SleepAllowedStatus.NOT_POSSIBLE_HERE;
            }

            if (this.world.isSunny()) {
                return PlayerEntity.SleepAllowedStatus.NOT_POSSIBLE_NOW;
            }

            if (Math.abs(this.x - pos.getX()) > 3.0 || Math.abs(this.y - pos.getY()) > 2.0 || Math.abs(this.z - pos.getZ()) > 3.0) {
                return PlayerEntity.SleepAllowedStatus.TOO_FAR_AWAY;
            }

            double d0 = 8.0;
            double d1 = 5.0;
            List<MonsterEntity> list = this.world
                .getEntitiesOfType(
                    MonsterEntity.class, new Box(pos.getX() - d0, pos.getY() - d1, pos.getZ() - d0, pos.getX() + d0, pos.getY() + d1, pos.getZ() + d0)
                );
            if (!list.isEmpty()) {
                return PlayerEntity.SleepAllowedStatus.NOT_SAFE;
            }
        }

        if (this.isRiding()) {
            this.startRiding(null);
        }

        this.setSize(0.2F, 0.2F);
        if (this.world.isChunkLoaded(pos)) {
            Direction direction = this.world.getBlockState(pos).get(HorizontalFacingBlock.FACING);
            float f = 0.5F;
            float f1 = 0.5F;
            switch (direction) {
                case SOUTH:
                    f1 = 0.9F;
                    break;
                case NORTH:
                    f1 = 0.1F;
                    break;
                case WEST:
                    f = 0.1F;
                    break;
                case EAST:
                    f = 0.9F;
            }

            this.setSleepingCameraOffset(direction);
            this.setPosition(pos.getX() + f, pos.getY() + 0.6875F, pos.getZ() + f1);
        } else {
            this.setPosition(pos.getX() + 0.5F, pos.getY() + 0.6875F, pos.getZ() + 0.5F);
        }

        this.sleeping = true;
        this.sleepingTime = 0;
        this.sleepingPos = pos;
        this.velocityX = this.velocityZ = this.velocityY = 0.0;
        if (!this.world.isClient) {
            this.world.updatePlayersSleepingStatus();
        }

        return PlayerEntity.SleepAllowedStatus.OK;
    }

    private void setSleepingCameraOffset(Direction facing) {
        this.sleepingCameraOffsetX = 0.0F;
        this.sleepingCameraOffsetZ = 0.0F;
        switch (facing) {
            case SOUTH:
                this.sleepingCameraOffsetZ = -1.8F;
                break;
            case NORTH:
                this.sleepingCameraOffsetZ = 1.8F;
                break;
            case WEST:
                this.sleepingCameraOffsetX = 1.8F;
                break;
            case EAST:
                this.sleepingCameraOffsetX = -1.8F;
        }
    }

    public void wakeUp(boolean resetSleepTimer, boolean updateAllPlayersSleeping, boolean setSpawnPoint) {
        this.setSize(0.6F, 1.8F);
        BlockState blockstate = this.world.getBlockState(this.sleepingPos);
        if (this.sleepingPos != null && blockstate.getBlock() == Blocks.BED) {
            this.world.setBlockState(this.sleepingPos, blockstate.set(BedBlock.OCCUPIED, false), 4);
            BlockPos blockpos = BedBlock.getSpawnPoint(this.world, this.sleepingPos, 0);
            if (blockpos == null) {
                blockpos = this.sleepingPos.up();
            }

            this.setPosition(blockpos.getX() + 0.5F, blockpos.getY() + 0.1F, blockpos.getZ() + 0.5F);
        }

        this.sleeping = false;
        if (!this.world.isClient && updateAllPlayersSleeping) {
            this.world.updatePlayersSleepingStatus();
        }

        this.sleepingTime = resetSleepTimer ? 0 : 100;
        if (setSpawnPoint) {
            this.setSpawnPoint(this.sleepingPos, false);
        }
    }

    private boolean checkSleepingPosition() {
        return this.world.getBlockState(this.sleepingPos).getBlock() == Blocks.BED;
    }

    public static BlockPos loadSpawnPoint(World world, BlockPos pos, boolean respawnForced) {
        Block block = world.getBlockState(pos).getBlock();
        if (block != Blocks.BED) {
            if (!respawnForced) {
                return null;
            }

            boolean flag = block.canRespawnIn();
            boolean flag1 = world.getBlockState(pos.up()).getBlock().canRespawnIn();
            return flag && flag1 ? pos : null;
        } else {
            return BedBlock.getSpawnPoint(world, pos, 0);
        }
    }

    public float getSleepingCameraAngle() {
        if (this.sleepingPos != null) {
            Direction direction = this.world.getBlockState(this.sleepingPos).get(HorizontalFacingBlock.FACING);
            switch (direction) {
                case SOUTH:
                    return 90.0F;
                case NORTH:
                    return 270.0F;
                case WEST:
                    return 0.0F;
                case EAST:
                    return 180.0F;
            }
        }

        return 0.0F;
    }

    @Override
    public boolean isSleeping() {
        return this.sleeping;
    }

    public boolean isSleepingLongEnough() {
        return this.sleeping && this.sleepingTime >= 100;
    }

    public int getSleepingTime() {
        return this.sleepingTime;
    }

    public void addMessage(Text message) {
    }

    public BlockPos getSpawnPoint() {
        return this.spawnPoint;
    }

    public boolean isRespawnForced() {
        return this.respawnForced;
    }

    public void setSpawnPoint(BlockPos pos, boolean respawnForced) {
        if (pos != null) {
            this.spawnPoint = pos;
            this.respawnForced = respawnForced;
        } else {
            this.spawnPoint = null;
            this.respawnForced = false;
        }
    }

    public void incrementStat(Stat stat) {
        this.incrementStat(stat, 1);
    }

    public void incrementStat(Stat stat, int amount) {
    }

    public void clearStat(Stat stat) {
    }

    @Override
    public void jump() {
        super.jump();
        this.incrementStat(Stats.JUMPS);
        if (this.isSprinting()) {
            this.addFatigue(0.8F);
        } else {
            this.addFatigue(0.2F);
        }
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        double d0 = this.x;
        double d1 = this.y;
        double d2 = this.z;
        if (this.abilities.flying && this.vehicle == null) {
            double d3 = this.velocityY;
            float f = this.flyingSpeed;
            this.flyingSpeed = this.abilities.getFlySpeed() * (this.isSprinting() ? 2 : 1);
            super.moveRelative(sideways, forwards);
            this.velocityY = d3 * 0.6;
            this.flyingSpeed = f;
        } else {
            super.moveRelative(sideways, forwards);
        }

        this.tickNonRidingMovementRelatedStats(this.x - d0, this.y - d1, this.z - d2);
    }

    @Override
    public float getSpeed() {
        return (float)this.getAttribute(EntityAttributes.MOVEMENT_SPEED).get();
    }

    public void tickNonRidingMovementRelatedStats(double x, double y, double z) {
        if (this.vehicle == null) {
            if (this.isSubmergedIn(Material.WATER)) {
                int i = Math.round(MathHelper.sqrt(x * x + y * y + z * z) * 100.0F);
                if (i > 0) {
                    this.incrementStat(Stats.CM_DIVEN, i);
                    this.addFatigue(0.015F * i * 0.01F);
                }
            } else if (this.isInWater()) {
                int j = Math.round(MathHelper.sqrt(x * x + z * z) * 100.0F);
                if (j > 0) {
                    this.incrementStat(Stats.CM_SWUM, j);
                    this.addFatigue(0.015F * j * 0.01F);
                }
            } else if (this.isClimbing()) {
                if (y > 0.0) {
                    this.incrementStat(Stats.CM_CLIMB, (int)Math.round(y * 100.0));
                }
            } else if (this.onGround) {
                int k = Math.round(MathHelper.sqrt(x * x + z * z) * 100.0F);
                if (k > 0) {
                    this.incrementStat(Stats.CM_WALKED, k);
                    if (this.isSprinting()) {
                        this.incrementStat(Stats.SPRINT_ONE_CM, k);
                        this.addFatigue(0.099999994F * k * 0.01F);
                    } else {
                        if (this.isSneaking()) {
                            this.incrementStat(Stats.CROUCH_ONE_CM, k);
                        }

                        this.addFatigue(0.01F * k * 0.01F);
                    }
                }
            } else {
                int l = Math.round(MathHelper.sqrt(x * x + z * z) * 100.0F);
                if (l > 25) {
                    this.incrementStat(Stats.CM_FLOWN, l);
                }
            }
        }
    }

    private void tickRidingRelatedStats(double x, double y, double z) {
        if (this.vehicle != null) {
            int i = Math.round(MathHelper.sqrt(x * x + y * y + z * z) * 100.0F);
            if (i > 0) {
                if (this.vehicle instanceof MinecartEntity) {
                    this.incrementStat(Stats.CM_MINECART, i);
                    if (this.minecartTravelStartPos == null) {
                        this.minecartTravelStartPos = new BlockPos(this);
                    } else if (this.minecartTravelStartPos.squaredDistanceTo(MathHelper.floor(this.x), MathHelper.floor(this.y), MathHelper.floor(this.z))
                        >= 1000000.0) {
                        this.incrementStat(Achievements.TRAVEL_KILOMETER_BY_MINECART);
                    }
                } else if (this.vehicle instanceof BoatEntity) {
                    this.incrementStat(Stats.CM_SAILED, i);
                } else if (this.vehicle instanceof PigEntity) {
                    this.incrementStat(Stats.CM_PIG, i);
                } else if (this.vehicle instanceof HorseBaseEntity) {
                    this.incrementStat(Stats.CM_HORSE, i);
                }
            }
        }
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        if (!this.abilities.canFly) {
            if (distance >= 2.0F) {
                this.incrementStat(Stats.CM_FALLEN, (int)Math.round(distance * 100.0));
            }

            super.takeFallDamage(distance, damageMultiplier);
        }
    }

    @Override
    protected void doSplashEffect() {
        if (!this.isSpectator()) {
            super.doSplashEffect();
        }
    }

    @Override
    protected String getFallSound(int distance) {
        return distance > 4 ? "game.player.hurt.fall.big" : "game.player.hurt.fall.small";
    }

    @Override
    public void onKill(LivingEntity victim) {
        if (victim instanceof Monster) {
            this.incrementStat(Achievements.KILL_ENEMY);
        }

        Entities.SpawnEggData entities$spawneggdata = Entities.SPAWN_EGG_DATA.get(Entities.getId(victim));
        if (entities$spawneggdata != null) {
            this.incrementStat(entities$spawneggdata.killEntityStat);
        }
    }

    @Override
    public void onCobwebCollision() {
        if (!this.abilities.flying) {
            super.onCobwebCollision();
        }
    }

    @Override
    public ItemStack getArmor(int slot) {
        return this.inventory.getArmor(slot);
    }

    public void increaseXp(int levels) {
        this.addToScore(levels);
        int i = Integer.MAX_VALUE - this.xp;
        if (levels > i) {
            levels = i;
        }

        this.xpProgress = this.xpProgress + (float)levels / this.getNextLevelExperience();

        for (this.xp += levels; this.xpProgress >= 1.0F; this.xpProgress = this.xpProgress / this.getNextLevelExperience()) {
            this.xpProgress = (this.xpProgress - 1.0F) * this.getNextLevelExperience();
            this.addXp(1);
        }
    }

    public int getEnchantingSeed() {
        return this.enchantmentTableSeed;
    }

    public void applyEnchantmentCosts(int cost) {
        this.xpLevel -= cost;
        if (this.xpLevel < 0) {
            this.xpLevel = 0;
            this.xpProgress = 0.0F;
            this.xp = 0;
        }

        this.enchantmentTableSeed = this.random.nextInt();
    }

    public void addXp(int levels) {
        this.xpLevel += levels;
        if (this.xpLevel < 0) {
            this.xpLevel = 0;
            this.xpProgress = 0.0F;
            this.xp = 0;
        }

        if (levels > 0 && this.xpLevel % 5 == 0 && this.lastXpSoundTime < this.ticks - 100.0F) {
            float f = this.xpLevel > 30 ? 1.0F : this.xpLevel / 30.0F;
            this.world.playSound((Entity)this, "random.levelup", f * 0.75F, 1.0F);
            this.lastXpSoundTime = this.ticks;
        }
    }

    public int getNextLevelExperience() {
        if (this.xpLevel >= 30) {
            return 112 + (this.xpLevel - 30) * 9;
        } else {
            return this.xpLevel >= 15 ? 37 + (this.xpLevel - 15) * 5 : 7 + this.xpLevel * 2;
        }
    }

    public void addFatigue(float amount) {
        if (!this.abilities.invulnerable) {
            if (!this.world.isClient) {
                this.hungerManager.addExhaustion(amount);
            }
        }
    }

    public HungerManager getHungerManager() {
        return this.hungerManager;
    }

    public boolean canEat(boolean ignoreHunger) {
        return (ignoreHunger || this.hungerManager.needsFood()) && !this.abilities.invulnerable;
    }

    public boolean needsHealing() {
        return this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth();
    }

    public void setItemInUse(ItemStack item, int maxUseTime) {
        if (item != this.itemInUse) {
            this.itemInUse = item;
            this.itemUseTimer = maxUseTime;
            if (!this.world.isClient) {
                this.setUsingItem(true);
            }
        }
    }

    public boolean canModifyWorld() {
        return this.abilities.canModifyWorld;
    }

    public boolean canUseItemOn(BlockPos pos, Direction face, ItemStack itemInHand) {
        if (this.abilities.canModifyWorld) {
            return true;
        }

        if (itemInHand == null) {
            return false;
        }

        BlockPos blockpos = pos.offset(face.getOpposite());
        Block block = this.world.getBlockState(blockpos).getBlock();
        return itemInHand.hasPlaceOnBlockOverride(block) || itemInHand.canUseOnBlockInAdventureMode();
    }

    @Override
    protected int getXpDrop(PlayerEntity playerEntity) {
        if (this.world.getGameRules().getBoolean("keepInventory")) {
            return 0;
        }

        int i = this.xpLevel * 7;
        return i > 100 ? 100 : i;
    }

    @Override
    protected boolean shouldDropXp() {
        return true;
    }

    @Override
    public boolean shouldShowNameTag() {
        return true;
    }

    public void copyFrom(PlayerEntity player, boolean comesFromTheEnd) {
        if (comesFromTheEnd) {
            this.inventory.copy(player.inventory);
            this.setHealth(player.getHealth());
            this.hungerManager = player.hungerManager;
            this.xpLevel = player.xpLevel;
            this.xp = player.xp;
            this.xpProgress = player.xpProgress;
            this.setScore(player.getScore());
            this.lastPortalPos = player.lastPortalPos;
            this.lastPortalOffset = player.lastPortalOffset;
            this.lastPortalFacing = player.lastPortalFacing;
        } else if (this.world.getGameRules().getBoolean("keepInventory")) {
            this.inventory.copy(player.inventory);
            this.xpLevel = player.xpLevel;
            this.xp = player.xp;
            this.xpProgress = player.xpProgress;
            this.setScore(player.getScore());
        }

        this.enchantmentTableSeed = player.enchantmentTableSeed;
        this.enderchest = player.enderchest;
        this.getSyncedData().update(10, player.getSyncedData().getByte(10));
    }

    @Override
    protected boolean makesSteps() {
        return !this.abilities.flying;
    }

    public void syncAbilities() {
    }

    public void setGameMode(WorldSettings.GameMode gameMode) {
    }

    @Override
    public String getName() {
        return this.profile.getName();
    }

    public EnderChestInventory getEnderChestInventory() {
        return this.enderchest;
    }

    @Override
    public ItemStack getEquipment(int slot) {
        return slot == 0 ? this.inventory.getSelectedItem() : this.inventory.armor[slot - 1];
    }

    @Override
    public ItemStack getDisplayItemInHand() {
        return this.inventory.getSelectedItem();
    }

    @Override
    public void setEquipment(int slot, ItemStack item) {
        this.inventory.armor[slot] = item;
    }

    @Override
    public boolean isInvisibleTo(PlayerEntity player) {
        if (!this.isInvisible()) {
            return false;
        }

        if (player.isSpectator()) {
            return false;
        }

        AbstractTeam abstractteam = this.getScoreboardTeam();
        return abstractteam == null || player == null || player.getScoreboardTeam() != abstractteam || !abstractteam.showFriendlyInvisibles();
    }

    public abstract boolean isSpectator();

    @Override
    public ItemStack[] getEquipment() {
        return this.inventory.armor;
    }

    @Override
    public boolean hasLiquidCollision() {
        return !this.abilities.flying;
    }

    public Scoreboard getScoreboard() {
        return this.world.getScoreboard();
    }

    @Override
    public AbstractTeam getScoreboardTeam() {
        return this.getScoreboard().getTeamOfMember(this.getName());
    }

    @Override
    public Text getDisplayName() {
        Text text = new LiteralText(Team.getMemberDisplayName(this.getScoreboardTeam(), this.getName()));
        text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + this.getName() + " "));
        text.getStyle().setHoverEvent(this.getHoverEvent());
        text.getStyle().setInsertion(this.getName());
        return text;
    }

    @Override
    public float getEyeHeight() {
        float f = 1.62F;
        if (this.isSleeping()) {
            f = 0.2F;
        }

        if (this.isSneaking()) {
            f -= 0.08F;
        }

        return f;
    }

    @Override
    public void setAbsorption(float absorption) {
        if (absorption < 0.0F) {
            absorption = 0.0F;
        }

        this.getSyncedData().update(17, absorption);
    }

    @Override
    public float getAbsorption() {
        return this.getSyncedData().getFloat(17);
    }

    public static UUID getUuid(GameProfile profile) {
        UUID uuid = profile.getId();
        if (uuid == null) {
            uuid = getUuidForOffline(profile.getName());
        }

        return uuid;
    }

    public static UUID getUuidForOffline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(Charsets.UTF_8));
    }

    public boolean canUnlockInventory(InventoryLock lock) {
        if (lock.isEmpty()) {
            return true;
        }

        ItemStack itemstack = this.getItemInHand();
        return itemstack != null && itemstack.hasCustomHoverName() && itemstack.getHoverName().equals(lock.getKey());
    }

    public boolean isModelPartVisible(PlayerModelPart part) {
        return (this.getSyncedData().getByte(10) & part.getFlag()) == part.getFlag();
    }

    @Override
    public boolean sendCommandSuccessToOps() {
        return MinecraftServer.getInstance().worlds[0].getGameRules().getBoolean("sendCommandFeedback");
    }

    @Override
    public boolean replaceItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < this.inventory.items.length) {
            this.inventory.setItem(slot, item);
            return true;
        }

        int i = slot - 100;
        if (i >= 0 && i < this.inventory.armor.length) {
            int k = i + 1;
            if (item != null && item.getItem() != null) {
                if (item.getItem() instanceof ArmorItem) {
                    if (MobEntity.getEquipmentSlot(item) != k) {
                        return false;
                    }
                } else if (k != 4 || item.getItem() != Items.SKULL && !(item.getItem() instanceof BlockItem)) {
                    return false;
                }
            }

            this.inventory.setItem(i + this.inventory.items.length, item);
            return true;
        } else {
            int j = slot - 200;
            if (j >= 0 && j < this.enderchest.getSize()) {
                this.enderchest.setItem(j, item);
                return true;
            } else {
                return false;
            }
        }
    }

    public boolean hasReducedDebugInfo() {
        return this.reducedDebugInfo;
    }

    public void setReducedDebugInfo(boolean reducedDebugInfo) {
        this.reducedDebugInfo = reducedDebugInfo;
    }

    public enum ChatVisibility {
        FULL(0, "options.chat.visibility.full"),
        SYSTEM(1, "options.chat.visibility.system"),
        HIDDEN(2, "options.chat.visibility.hidden");

        private static final PlayerEntity.ChatVisibility[] ALL = new PlayerEntity.ChatVisibility[values().length];
        private final int index;
        private final String id;

        ChatVisibility(int index, String id) {
            this.index = index;
            this.id = id;
        }

        public int getIndex() {
            return this.index;
        }

        public static PlayerEntity.ChatVisibility byIndex(int index) {
            return ALL[index % ALL.length];
        }

        public String getId() {
            return this.id;
        }

        static {
            for (PlayerEntity.ChatVisibility playerentity$chatvisibility : values()) {
                ALL[playerentity$chatvisibility.index] = playerentity$chatvisibility;
            }
        }
    }

    public enum SleepAllowedStatus {
        OK,
        NOT_POSSIBLE_HERE,
        NOT_POSSIBLE_NOW,
        TOO_FAR_AWAY,
        OTHER_PROBLEM,
        NOT_SAFE;
    }
}
