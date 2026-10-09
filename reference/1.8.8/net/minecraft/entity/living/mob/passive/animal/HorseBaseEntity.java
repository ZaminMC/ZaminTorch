package net.minecraft.entity.living.mob.passive.animal;

import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.EscapeDangerGoal;
import net.minecraft.entity.ai.goal.FollowParentGoal;
import net.minecraft.entity.ai.goal.HorseBondWithPlayerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.attribute.RangedEntityAttribute;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.inventory.AnimalInventory;
import net.minecraft.inventory.InventoryListener;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.UserConverter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class HorseBaseEntity extends AnimalEntity implements InventoryListener {
    private static final Predicate<Entity> BREED_FILTER = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity instanceof HorseBaseEntity && ((HorseBaseEntity)entity).isBred();
        }
    };
    private static final EntityAttribute JUMP_STRENGTH_ATTRIBUTE = new RangedEntityAttribute(null, "horse.jumpStrength", 0.7, 0.0, 2.0)
        .setDisplayName("Jump Strength")
        .setTrackable(true);
    private static final String[] HORSE_ARMOR_TEXTURE_PATHS = new String[]{
        null,
        "textures/entity/horse/armor/horse_armor_iron.png",
        "textures/entity/horse/armor/horse_armor_gold.png",
        "textures/entity/horse/armor/horse_armor_diamond.png"
    };
    private static final String[] HORSE_ARMOR_HASHES = new String[]{"", "meo", "goo", "dio"};
    private static final int[] HORSE_ARMOR_PROTECTION = new int[]{0, 5, 7, 11};
    private static final String[] HORSE_VARIANT_TEXTURE_PATHS = new String[]{
        "textures/entity/horse/horse_white.png",
        "textures/entity/horse/horse_creamy.png",
        "textures/entity/horse/horse_chestnut.png",
        "textures/entity/horse/horse_brown.png",
        "textures/entity/horse/horse_black.png",
        "textures/entity/horse/horse_gray.png",
        "textures/entity/horse/horse_darkbrown.png"
    };
    private static final String[] HORSE_VARIANT_HASHES = new String[]{"hwh", "hcr", "hch", "hbr", "hbl", "hgr", "hdb"};
    private static final String[] HORSE_MARKINGS_TEXTURE_PATHS = new String[]{
        null,
        "textures/entity/horse/horse_markings_white.png",
        "textures/entity/horse/horse_markings_whitefield.png",
        "textures/entity/horse/horse_markings_whitedots.png",
        "textures/entity/horse/horse_markings_blackdots.png"
    };
    private static final String[] HORSE_MARKINGS_HASHES = new String[]{"", "wo_", "wmo", "wdo", "bdo"};
    private int eatingGrassTicks;
    private int eatingTicks;
    private int angryTicks;
    public int type;
    public int cooldown;
    protected boolean inAir;
    private AnimalInventory inventory;
    private boolean hasBred;
    protected int temper;
    protected float jumpStrength;
    private boolean jumping;
    private float eatingGrassAnimationProgress;
    private float lastEatingGrassAnimationProgress;
    private float angryAnimationProgress;
    private float lastAngryAnimationProgress;
    private float eatingAnimationProgress;
    private float lastEatingAnimationProgress;
    private int soundTicks;
    private String baseTexturePath;
    private String[] armoredTexturePaths = new String[3];
    private boolean hasArmoredTexture = false;

    public HorseBaseEntity(World world) {
        super(world);
        this.setSize(1.4F, 1.6F);
        this.immuneToFire = false;
        this.setHasChest(false);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(1, new EscapeDangerGoal(this, 1.2));
        this.goalSelector.addGoal(1, new HorseBondWithPlayerGoal(this, 1.2));
        this.goalSelector.addGoal(2, new AnimalBreedGoal(this, 1.0));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.0));
        this.goalSelector.addGoal(6, new WanderAroundGoal(this, 0.7));
        this.goalSelector.addGoal(7, new LookAtEntityGoal(this, PlayerEntity.class, 6.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.updateInventory();
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, 0);
        this.syncedData.register(19, (byte)0);
        this.syncedData.register(20, 0);
        this.syncedData.register(21, String.valueOf(""));
        this.syncedData.register(22, 0);
    }

    public void setType(int type) {
        this.syncedData.update(19, (byte)type);
        this.deleteName();
    }

    public int getType() {
        return this.syncedData.getByte(19);
    }

    public void setVariant(int variant) {
        this.syncedData.update(20, variant);
        this.deleteName();
    }

    public int getVariant() {
        return this.syncedData.getInt(20);
    }

    @Override
    public String getName() {
        if (this.hasCustomName()) {
            return this.getCustomName();
        }

        int i = this.getType();
        switch (i) {
            case 0:
            default:
                return I18n.translate("entity.horse.name");
            case 1:
                return I18n.translate("entity.donkey.name");
            case 2:
                return I18n.translate("entity.mule.name");
            case 3:
                return I18n.translate("entity.zombiehorse.name");
            case 4:
                return I18n.translate("entity.skeletonhorse.name");
        }
    }

    private boolean getHorseFlag(int flag) {
        return (this.syncedData.getInt(16) & flag) != 0;
    }

    private void setHorseFlag(int flag, boolean value) {
        int i = this.syncedData.getInt(16);
        if (value) {
            this.syncedData.update(16, i | flag);
        } else {
            this.syncedData.update(16, i & ~flag);
        }
    }

    public boolean isOldEnoughForBreeding() {
        return !this.isBaby();
    }

    public boolean isTame() {
        return this.getHorseFlag(2);
    }

    public boolean acceptsFoodFromPassenger() {
        return this.isOldEnoughForBreeding();
    }

    public String getOwnerUuid() {
        return this.syncedData.getString(21);
    }

    public void setOwnerName(String name) {
        this.syncedData.update(21, name);
    }

    public float getSize() {
        return 0.5F;
    }

    @Override
    public void setAgeSize(boolean isBaby) {
        if (isBaby) {
            this.resizeBounds(this.getSize());
        } else {
            this.resizeBounds(1.0F);
        }
    }

    public boolean isInAir() {
        return this.inAir;
    }

    public void setTame(boolean tame) {
        this.setHorseFlag(2, tame);
    }

    public void setInAir(boolean inAir) {
        this.inAir = inAir;
    }

    @Override
    public boolean isTameable() {
        return !this.isAngryHorse() && super.isTameable();
    }

    @Override
    protected void updateForLeashLength(float leashLength) {
        if (leashLength > 6.0F && this.isEatingHay()) {
            this.setEatingHay(false);
        }
    }

    public boolean hasChest() {
        return this.getHorseFlag(8);
    }

    public int getArmorTier() {
        return this.syncedData.getInt(22);
    }

    /**
     * horse armor tiers:
     * <br>0: iron
     * <br>1: gold
     * <br>2: diamond
     */
    private int getArmorTier(ItemStack item) {
        if (item == null) {
            return 0;
        } else {
            Item itemx = item.getItem();
            if (itemx == Items.IRON_HORSE_ARMOR) {
                return 1;
            } else if (itemx == Items.GOLDEN_HORSE_ARMOR) {
                return 2;
            } else {
                return itemx == Items.DIAMOND_HORSE_ARMOR ? 3 : 0;
            }
        }
    }

    public boolean isEatingHay() {
        return this.getHorseFlag(32);
    }

    public boolean isAngry() {
        return this.getHorseFlag(64);
    }

    public boolean isBred() {
        return this.getHorseFlag(16);
    }

    public boolean hasBred() {
        return this.hasBred;
    }

    public void setArmor(ItemStack item) {
        this.syncedData.update(22, this.getArmorTier(item));
        this.deleteName();
    }

    public void setBred(boolean bred) {
        this.setHorseFlag(16, bred);
    }

    public void setHasChest(boolean hasChest) {
        this.setHorseFlag(8, hasChest);
    }

    public void setHasBred(boolean hasBred) {
        this.hasBred = hasBred;
    }

    public void setSaddled(boolean saddled) {
        this.setHorseFlag(4, saddled);
    }

    public int getTemper() {
        return this.temper;
    }

    public void setTemper(int temper) {
        this.temper = temper;
    }

    public int addTemper(int amount) {
        int i = MathHelper.clamp(this.getTemper() + amount, 0, this.getMaxTemper());
        this.setTemper(i);
        return i;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        Entity entity = source.getAttacker();
        return (this.rider == null || !this.rider.equals(entity)) && super.takeDamage(source, amount);
    }

    @Override
    public int getArmorProtection() {
        return HORSE_ARMOR_PROTECTION[this.getArmorTier()];
    }

    @Override
    public boolean isPushable() {
        return this.rider == null;
    }

    public boolean getSpawnBiome() {
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.z);
        this.world.getBiome(new BlockPos(i, 0, j));
        return true;
    }

    public void dropChest() {
        if (!this.world.isClient && this.hasChest()) {
            this.dropItem(Item.byBlock(Blocks.CHEST), 1);
            this.setHasChest(false);
        }
    }

    private void playEatingAnimation() {
        this.setEating();
        if (!this.isSilent()) {
            this.world.playSound(this, "eating", 1.0F, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F);
        }
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        if (distance > 1.0F) {
            this.playSound("mob.horse.land", 0.4F, 1.0F);
        }

        int i = MathHelper.ceil((distance * 0.5F - 3.0F) * damageMultiplier);
        if (i > 0) {
            this.takeDamage(DamageSource.FALL, i);
            if (this.rider != null) {
                this.rider.takeDamage(DamageSource.FALL, i);
            }

            Block block = this.world.getBlockState(new BlockPos(this.x, this.y - 0.2 - this.lastYaw, this.z)).getBlock();
            if (block.getMaterial() != Material.AIR && !this.isSilent()) {
                Block.Sounds block$sounds = block.sounds;
                this.world.playSound(this, block$sounds.getStepping(), block$sounds.getVolume() * 0.5F, block$sounds.getPitch() * 0.75F);
            }
        }
    }

    private int getInventorySize() {
        int i = this.getType();
        return !this.hasChest() || i != 1 && i != 2 ? 2 : 17;
    }

    private void updateInventory() {
        AnimalInventory animalinventory = this.inventory;
        this.inventory = new AnimalInventory("HorseChest", this.getInventorySize());
        this.inventory.setCustomName(this.getName());
        if (animalinventory != null) {
            animalinventory.removeListener(this);
            int i = Math.min(animalinventory.getSize(), this.inventory.getSize());

            for (int j = 0; j < i; j++) {
                ItemStack itemstack = animalinventory.getItem(j);
                if (itemstack != null) {
                    this.inventory.setItem(j, itemstack.copy());
                }
            }
        }

        this.inventory.addListener(this);
        this.updateSaddle();
    }

    private void updateSaddle() {
        if (!this.world.isClient) {
            this.setSaddled(this.inventory.getItem(0) != null);
            if (this.canHaveArmor()) {
                this.setArmor(this.inventory.getItem(1));
            }
        }
    }

    @Override
    public void onInventoryChanged(SimpleInventory inventory) {
        int i = this.getArmorTier();
        boolean flag = this.isSaddled();
        this.updateSaddle();
        if (this.ticks > 20) {
            if (i == 0 && i != this.getArmorTier()) {
                this.playSound("mob.horse.armor", 0.5F, 1.0F);
            } else if (i != this.getArmorTier()) {
                this.playSound("mob.horse.armor", 0.5F, 1.0F);
            }

            if (!flag && this.isSaddled()) {
                this.playSound("mob.horse.leather", 0.5F, 1.0F);
            }
        }
    }

    @Override
    public boolean canSpawn() {
        this.getSpawnBiome();
        return super.canSpawn();
    }

    protected HorseBaseEntity findNearestEntity(Entity entity, double stretch) {
        double d0 = Double.MAX_VALUE;
        Entity entityx = null;

        for (Entity entity1 : this.world.getEntities(entity, entity.getShape().expanded(stretch, stretch, stretch), BREED_FILTER)) {
            double d1 = entity1.squaredDistanceTo(entity.x, entity.y, entity.z);
            if (d1 < d0) {
                entityx = entity1;
                d0 = d1;
            }
        }

        return (HorseBaseEntity)entityx;
    }

    public double getCustomJumpStrength() {
        return this.getAttribute(JUMP_STRENGTH_ATTRIBUTE).get();
    }

    @Override
    protected String getDeathSound() {
        this.setEating();
        int i = this.getType();
        if (i == 3) {
            return "mob.horse.zombie.death";
        } else if (i == 4) {
            return "mob.horse.skeleton.death";
        } else {
            return i != 1 && i != 2 ? "mob.horse.death" : "mob.horse.donkey.death";
        }
    }

    @Override
    protected Item getDropItem() {
        boolean flag = this.random.nextInt(4) == 0;
        int i = this.getType();
        if (i == 4) {
            return Items.BONE;
        } else if (i == 3) {
            return flag ? null : Items.ROTTEN_FLESH;
        } else {
            return Items.LEATHER;
        }
    }

    @Override
    protected String getHurtSound() {
        this.setEating();
        if (this.random.nextInt(3) == 0) {
            this.updateAnger();
        }

        int i = this.getType();
        if (i == 3) {
            return "mob.horse.zombie.hit";
        } else if (i == 4) {
            return "mob.horse.skeleton.hit";
        } else {
            return i != 1 && i != 2 ? "mob.horse.hit" : "mob.horse.donkey.hit";
        }
    }

    public boolean isSaddled() {
        return this.getHorseFlag(4);
    }

    @Override
    protected String getAmbientSound() {
        this.setEating();
        if (this.random.nextInt(10) == 0 && !this.isDead()) {
            this.updateAnger();
        }

        int i = this.getType();
        if (i == 3) {
            return "mob.horse.zombie.idle";
        } else if (i == 4) {
            return "mob.horse.skeleton.idle";
        } else {
            return i != 1 && i != 2 ? "mob.horse.idle" : "mob.horse.donkey.idle";
        }
    }

    protected String getAngreType() {
        this.setEating();
        this.updateAnger();
        int i = this.getType();
        if (i == 3 || i == 4) {
            return null;
        } else {
            return i != 1 && i != 2 ? "mob.horse.angry" : "mob.horse.donkey.angry";
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        Block.Sounds block$sounds = block.sounds;
        if (this.world.getBlockState(pos.up()).getBlock() == Blocks.SNOW_LAYER) {
            block$sounds = Blocks.SNOW_LAYER.sounds;
        }

        if (!block.getMaterial().isLiquid()) {
            int i = this.getType();
            if (this.rider != null && i != 1 && i != 2) {
                this.soundTicks++;
                if (this.soundTicks > 5 && this.soundTicks % 3 == 0) {
                    this.playSound("mob.horse.gallop", block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
                    if (i == 0 && this.random.nextInt(10) == 0) {
                        this.playSound("mob.horse.breathe", block$sounds.getVolume() * 0.6F, block$sounds.getPitch());
                    }
                } else if (this.soundTicks <= 5) {
                    this.playSound("mob.horse.wood", block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
                }
            } else if (block$sounds == Block.WOOD_SOUNDS) {
                this.playSound("mob.horse.wood", block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
            } else {
                this.playSound("mob.horse.soft", block$sounds.getVolume() * 0.15F, block$sounds.getPitch());
            }
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributes().register(JUMP_STRENGTH_ATTRIBUTE);
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(53.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.225F);
    }

    @Override
    public int getLimitPerChunk() {
        return 6;
    }

    public int getMaxTemper() {
        return 100;
    }

    @Override
    protected float getSoundVolume() {
        return 0.8F;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 400;
    }

    public boolean hasArmor() {
        return this.getType() == 0 || this.getArmorTier() > 0;
    }

    private void deleteName() {
        this.baseTexturePath = null;
    }

    public boolean hasArmoredTexture() {
        return this.hasArmoredTexture;
    }

    private void updateTexturePaths() {
        this.baseTexturePath = "horse/";
        this.armoredTexturePaths[0] = null;
        this.armoredTexturePaths[1] = null;
        this.armoredTexturePaths[2] = null;
        int i = this.getType();
        int j = this.getVariant();
        if (i == 0) {
            int k = j & 0xFF;
            int l = (j & 0xFF00) >> 8;
            if (k >= HORSE_VARIANT_TEXTURE_PATHS.length) {
                this.hasArmoredTexture = false;
                return;
            }

            this.armoredTexturePaths[0] = HORSE_VARIANT_TEXTURE_PATHS[k];
            this.baseTexturePath = this.baseTexturePath + HORSE_VARIANT_HASHES[k];
            if (l >= HORSE_MARKINGS_TEXTURE_PATHS.length) {
                this.hasArmoredTexture = false;
                return;
            }

            this.armoredTexturePaths[1] = HORSE_MARKINGS_TEXTURE_PATHS[l];
            this.baseTexturePath = this.baseTexturePath + HORSE_MARKINGS_HASHES[l];
        } else {
            this.armoredTexturePaths[0] = "";
            this.baseTexturePath = this.baseTexturePath + "_" + i + "_";
        }

        int i1 = this.getArmorTier();
        if (i1 >= HORSE_ARMOR_TEXTURE_PATHS.length) {
            this.hasArmoredTexture = false;
        } else {
            this.armoredTexturePaths[2] = HORSE_ARMOR_TEXTURE_PATHS[i1];
            this.baseTexturePath = this.baseTexturePath + HORSE_ARMOR_HASHES[i1];
            this.hasArmoredTexture = true;
        }
    }

    public String getBaseTexturePath() {
        if (this.baseTexturePath == null) {
            this.updateTexturePaths();
        }

        return this.baseTexturePath;
    }

    public String[] getArmoredTexturePaths() {
        if (this.baseTexturePath == null) {
            this.updateTexturePaths();
        }

        return this.armoredTexturePaths;
    }

    public void openInventory(PlayerEntity player) {
        if (!this.world.isClient && (this.rider == null || this.rider == player) && this.isTame()) {
            this.inventory.setCustomName(this.getName());
            player.openHorseMenu(this, this.inventory);
        }
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.getItem() == Items.SPAWN_EGG) {
            return super.interactMob(player);
        }

        if (!this.isTame() && this.isAngryHorse()) {
            return false;
        }

        if (this.isTame() && this.isOldEnoughForBreeding() && player.isSneaking()) {
            this.openInventory(player);
            return true;
        }

        if (this.acceptsFoodFromPassenger() && this.rider != null) {
            return super.interactMob(player);
        }

        if (itemstack != null) {
            boolean flag = false;
            if (this.canHaveArmor()) {
                int i = -1;
                if (itemstack.getItem() == Items.IRON_HORSE_ARMOR) {
                    i = 1;
                } else if (itemstack.getItem() == Items.GOLDEN_HORSE_ARMOR) {
                    i = 2;
                } else if (itemstack.getItem() == Items.DIAMOND_HORSE_ARMOR) {
                    i = 3;
                }

                if (i >= 0) {
                    if (!this.isTame()) {
                        this.playAngrySound();
                        return true;
                    }

                    this.openInventory(player);
                    return true;
                }
            }

            if (!flag && !this.isAngryHorse()) {
                float f = 0.0F;
                int j = 0;
                int k = 0;
                if (itemstack.getItem() == Items.WHEAT) {
                    f = 2.0F;
                    j = 20;
                    k = 3;
                } else if (itemstack.getItem() == Items.SUGAR) {
                    f = 1.0F;
                    j = 30;
                    k = 3;
                } else if (Block.byItem(itemstack.getItem()) == Blocks.HAY) {
                    f = 20.0F;
                    j = 180;
                } else if (itemstack.getItem() == Items.APPLE) {
                    f = 3.0F;
                    j = 60;
                    k = 3;
                } else if (itemstack.getItem() == Items.GOLDEN_CARROT) {
                    f = 4.0F;
                    j = 60;
                    k = 5;
                    if (this.isTame() && this.getBreedingAge() == 0) {
                        flag = true;
                        this.lovePlayer(player);
                    }
                } else if (itemstack.getItem() == Items.GOLDEN_APPLE) {
                    f = 10.0F;
                    j = 240;
                    k = 10;
                    if (this.isTame() && this.getBreedingAge() == 0) {
                        flag = true;
                        this.lovePlayer(player);
                    }
                }

                if (this.getHealth() < this.getMaxHealth() && f > 0.0F) {
                    this.heal(f);
                    flag = true;
                }

                if (!this.isOldEnoughForBreeding() && j > 0) {
                    this.growUp(j);
                    flag = true;
                }

                if (k > 0 && (flag || !this.isTame()) && k < this.getMaxTemper()) {
                    flag = true;
                    this.addTemper(k);
                }

                if (flag) {
                    this.playEatingAnimation();
                }
            }

            if (!this.isTame() && !flag) {
                if (itemstack != null && itemstack.interact(player, this)) {
                    return true;
                }

                this.playAngrySound();
                return true;
            }

            if (!flag && this.canHaveChest() && !this.hasChest() && itemstack.getItem() == Item.byBlock(Blocks.CHEST)) {
                this.setHasChest(true);
                this.playSound("mob.chickenplop", 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                flag = true;
                this.updateInventory();
            }

            if (!flag && this.acceptsFoodFromPassenger() && !this.isSaddled() && itemstack.getItem() == Items.SADDLE) {
                this.openInventory(player);
                return true;
            }

            if (flag) {
                if (!player.abilities.creativeMode && --itemstack.size == 0) {
                    player.inventory.setItem(player.inventory.selectedSlot, null);
                }

                return true;
            }
        }

        if (!this.acceptsFoodFromPassenger() || this.rider != null) {
            return super.interactMob(player);
        }

        if (itemstack != null && itemstack.interact(player, this)) {
            return true;
        }

        this.putPlayerOnBack(player);
        return true;
    }

    private void putPlayerOnBack(PlayerEntity player) {
        player.yaw = this.yaw;
        player.pitch = this.pitch;
        this.setEatingHay(false);
        this.setAngry(false);
        if (!this.world.isClient) {
            player.startRiding(this);
        }
    }

    public boolean canHaveArmor() {
        return this.getType() == 0;
    }

    public boolean canHaveChest() {
        int i = this.getType();
        return i == 2 || i == 1;
    }

    @Override
    protected boolean isDead() {
        return this.rider != null && this.isSaddled() || this.isEatingHay() || this.isAngry();
    }

    public boolean isAngryHorse() {
        int i = this.getType();
        return i == 3 || i == 4;
    }

    public boolean noLove() {
        return this.isAngryHorse() || this.getType() == 2;
    }

    @Override
    public boolean isBreedingItem(ItemStack item) {
        return false;
    }

    private void setType() {
        this.type = 1;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!this.world.isClient) {
            this.dropInventoryAndChest();
        }
    }

    @Override
    public void mobTick() {
        if (this.random.nextInt(200) == 0) {
            this.setType();
        }

        super.mobTick();
        if (!this.world.isClient) {
            if (this.random.nextInt(900) == 0 && this.deathTicks == 0) {
                this.heal(1.0F);
            }

            if (!this.isEatingHay()
                && this.rider == null
                && this.random.nextInt(300) == 0
                && this.world.getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.y) - 1, MathHelper.floor(this.z))).getBlock()
                    == Blocks.GRASS) {
                this.setEatingHay(true);
            }

            if (this.isEatingHay() && ++this.eatingGrassTicks > 50) {
                this.eatingGrassTicks = 0;
                this.setEatingHay(false);
            }

            if (this.isBred() && !this.isOldEnoughForBreeding() && !this.isEatingHay()) {
                HorseBaseEntity horsebaseentity = this.findNearestEntity(this, 16.0);
                if (horsebaseentity != null && this.squaredDistanceTo(horsebaseentity) > 4.0) {
                    this.entityNavigation.findPath(horsebaseentity);
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.world.isClient && this.syncedData.isDirty()) {
            this.syncedData.markClean();
            this.deleteName();
        }

        if (this.eatingTicks > 0 && ++this.eatingTicks > 30) {
            this.eatingTicks = 0;
            this.setHorseFlag(128, false);
        }

        if (!this.world.isClient && this.angryTicks > 0 && ++this.angryTicks > 20) {
            this.angryTicks = 0;
            this.setAngry(false);
        }

        if (this.type > 0 && ++this.type > 8) {
            this.type = 0;
        }

        if (this.cooldown > 0) {
            this.cooldown++;
            if (this.cooldown > 300) {
                this.cooldown = 0;
            }
        }

        this.lastEatingGrassAnimationProgress = this.eatingGrassAnimationProgress;
        if (this.isEatingHay()) {
            this.eatingGrassAnimationProgress = this.eatingGrassAnimationProgress + ((1.0F - this.eatingGrassAnimationProgress) * 0.4F + 0.05F);
            if (this.eatingGrassAnimationProgress > 1.0F) {
                this.eatingGrassAnimationProgress = 1.0F;
            }
        } else {
            this.eatingGrassAnimationProgress = this.eatingGrassAnimationProgress + ((0.0F - this.eatingGrassAnimationProgress) * 0.4F - 0.05F);
            if (this.eatingGrassAnimationProgress < 0.0F) {
                this.eatingGrassAnimationProgress = 0.0F;
            }
        }

        this.lastAngryAnimationProgress = this.angryAnimationProgress;
        if (this.isAngry()) {
            this.lastEatingGrassAnimationProgress = this.eatingGrassAnimationProgress = 0.0F;
            this.angryAnimationProgress = this.angryAnimationProgress + ((1.0F - this.angryAnimationProgress) * 0.4F + 0.05F);
            if (this.angryAnimationProgress > 1.0F) {
                this.angryAnimationProgress = 1.0F;
            }
        } else {
            this.jumping = false;
            this.angryAnimationProgress = this.angryAnimationProgress
                + (
                    (0.8F * this.angryAnimationProgress * this.angryAnimationProgress * this.angryAnimationProgress - this.angryAnimationProgress) * 0.6F
                        - 0.05F
                );
            if (this.angryAnimationProgress < 0.0F) {
                this.angryAnimationProgress = 0.0F;
            }
        }

        this.lastEatingAnimationProgress = this.eatingAnimationProgress;
        if (this.getHorseFlag(128)) {
            this.eatingAnimationProgress = this.eatingAnimationProgress + ((1.0F - this.eatingAnimationProgress) * 0.7F + 0.05F);
            if (this.eatingAnimationProgress > 1.0F) {
                this.eatingAnimationProgress = 1.0F;
            }
        } else {
            this.eatingAnimationProgress = this.eatingAnimationProgress + ((0.0F - this.eatingAnimationProgress) * 0.7F - 0.05F);
            if (this.eatingAnimationProgress < 0.0F) {
                this.eatingAnimationProgress = 0.0F;
            }
        }
    }

    private void setEating() {
        if (!this.world.isClient) {
            this.eatingTicks = 1;
            this.setHorseFlag(128, true);
        }
    }

    private boolean canBreed() {
        return this.rider == null
            && this.vehicle == null
            && this.isTame()
            && this.isOldEnoughForBreeding()
            && !this.noLove()
            && this.getHealth() >= this.getMaxHealth()
            && this.isInLove();
    }

    @Override
    public void setUsingItem(boolean usingItem) {
        this.setHorseFlag(32, usingItem);
    }

    public void setEatingHay(boolean eatingHay) {
        this.setUsingItem(eatingHay);
    }

    public void setAngry(boolean angry) {
        if (angry) {
            this.setEatingHay(false);
        }

        this.setHorseFlag(64, angry);
    }

    private void updateAnger() {
        if (!this.world.isClient) {
            this.angryTicks = 1;
            this.setAngry(true);
        }
    }

    public void playAngrySound() {
        this.updateAnger();
        String s = this.getAngreType();
        if (s != null) {
            this.playSound(s, this.getSoundVolume(), this.getSoundPitch());
        }
    }

    public void dropInventoryAndChest() {
        this.dropInventory(this, this.inventory);
        this.dropChest();
    }

    private void dropInventory(Entity entity, AnimalInventory inventory) {
        if (inventory != null && !this.world.isClient) {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack = inventory.getItem(i);
                if (itemstack != null) {
                    this.dropItem(itemstack, 0.0F);
                }
            }
        }
    }

    public boolean bondWithPlayer(PlayerEntity player) {
        this.setOwnerName(player.getUuid().toString());
        this.setTame(true);
        return true;
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        if (this.rider != null && this.rider instanceof LivingEntity && this.isSaddled()) {
            this.lastYaw = this.yaw = this.rider.yaw;
            this.pitch = this.rider.pitch * 0.5F;
            this.setRotation(this.yaw, this.pitch);
            this.headYaw = this.bodyYaw = this.yaw;
            sideways = ((LivingEntity)this.rider).sidewaysSpeed * 0.5F;
            forwards = ((LivingEntity)this.rider).forwardSpeed;
            if (forwards <= 0.0F) {
                forwards *= 0.25F;
                this.soundTicks = 0;
            }

            if (this.onGround && this.jumpStrength == 0.0F && this.isAngry() && !this.jumping) {
                sideways = 0.0F;
                forwards = 0.0F;
            }

            if (this.jumpStrength > 0.0F && !this.isInAir() && this.onGround) {
                this.velocityY = this.getCustomJumpStrength() * this.jumpStrength;
                if (this.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
                    this.velocityY = this.velocityY + (this.getEffectInstance(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F;
                }

                this.setInAir(true);
                this.velocityDirty = true;
                if (forwards > 0.0F) {
                    float f = MathHelper.sin(this.yaw * (float) Math.PI / 180.0F);
                    float f1 = MathHelper.cos(this.yaw * (float) Math.PI / 180.0F);
                    this.velocityX = this.velocityX + -0.4F * f * this.jumpStrength;
                    this.velocityZ = this.velocityZ + 0.4F * f1 * this.jumpStrength;
                    this.playSound("mob.horse.jump", 0.4F, 1.0F);
                }

                this.jumpStrength = 0.0F;
            }

            this.stepHeight = 1.0F;
            this.flyingSpeed = this.getSpeed() * 0.1F;
            if (!this.world.isClient) {
                this.setSpeed((float)this.getAttribute(EntityAttributes.MOVEMENT_SPEED).get());
                super.moveRelative(sideways, forwards);
            }

            if (this.onGround) {
                this.jumpStrength = 0.0F;
                this.setInAir(false);
            }

            this.lastWalkAnimationSpeed = this.walkAnimationSpeed;
            double d1 = this.x - this.lastX;
            double d0 = this.z - this.lastZ;
            float f2 = MathHelper.sqrt(d1 * d1 + d0 * d0) * 4.0F;
            if (f2 > 1.0F) {
                f2 = 1.0F;
            }

            this.walkAnimationSpeed = this.walkAnimationSpeed + (f2 - this.walkAnimationSpeed) * 0.4F;
            this.walkAnimationProgress = this.walkAnimationProgress + this.walkAnimationSpeed;
        } else {
            this.stepHeight = 0.5F;
            this.flyingSpeed = 0.02F;
            super.moveRelative(sideways, forwards);
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("EatingHaystack", this.isEatingHay());
        nbt.putBoolean("ChestedHorse", this.hasChest());
        nbt.putBoolean("HasReproduced", this.hasBred());
        nbt.putBoolean("Bred", this.isBred());
        nbt.putInt("Type", this.getType());
        nbt.putInt("Variant", this.getVariant());
        nbt.putInt("Temper", this.getTemper());
        nbt.putBoolean("Tame", this.isTame());
        nbt.putString("OwnerUUID", this.getOwnerUuid());
        if (this.hasChest()) {
            NbtList nbtlist = new NbtList();

            for (int i = 2; i < this.inventory.getSize(); i++) {
                ItemStack itemstack = this.inventory.getItem(i);
                if (itemstack != null) {
                    NbtCompound nbtcompound = new NbtCompound();
                    nbtcompound.putByte("Slot", (byte)i);
                    itemstack.writeNbt(nbtcompound);
                    nbtlist.addElement(nbtcompound);
                }
            }

            nbt.put("Items", nbtlist);
        }

        if (this.inventory.getItem(1) != null) {
            nbt.put("ArmorItem", this.inventory.getItem(1).writeNbt(new NbtCompound()));
        }

        if (this.inventory.getItem(0) != null) {
            nbt.put("SaddleItem", this.inventory.getItem(0).writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setEatingHay(nbt.getBoolean("EatingHaystack"));
        this.setBred(nbt.getBoolean("Bred"));
        this.setHasChest(nbt.getBoolean("ChestedHorse"));
        this.setHasBred(nbt.getBoolean("HasReproduced"));
        this.setType(nbt.getInt("Type"));
        this.setVariant(nbt.getInt("Variant"));
        this.setTemper(nbt.getInt("Temper"));
        this.setTame(nbt.getBoolean("Tame"));
        String s = "";
        if (nbt.contains("OwnerUUID", 8)) {
            s = nbt.getString("OwnerUUID");
        } else {
            String s1 = nbt.getString("Owner");
            s = UserConverter.convertMobOwner(s1);
        }

        if (s.length() > 0) {
            this.setOwnerName(s);
        }

        EntityAttributeInstance entityattributeinstance = this.getAttributes().get("Speed");
        if (entityattributeinstance != null) {
            this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(entityattributeinstance.getBase() * 0.25);
        }

        if (this.hasChest()) {
            NbtList nbtlist = nbt.getList("Items", 10);
            this.updateInventory();

            for (int i = 0; i < nbtlist.size(); i++) {
                NbtCompound nbtcompound = nbtlist.getCompound(i);
                int j = nbtcompound.getByte("Slot") & 255;
                if (j >= 2 && j < this.inventory.getSize()) {
                    this.inventory.setItem(j, ItemStack.fromNbt(nbtcompound));
                }
            }
        }

        if (nbt.contains("ArmorItem", 10)) {
            ItemStack itemstack = ItemStack.fromNbt(nbt.getCompound("ArmorItem"));
            if (itemstack != null && isHorseArmor(itemstack.getItem())) {
                this.inventory.setItem(1, itemstack);
            }
        }

        if (nbt.contains("SaddleItem", 10)) {
            ItemStack itemstack1 = ItemStack.fromNbt(nbt.getCompound("SaddleItem"));
            if (itemstack1 != null && itemstack1.getItem() == Items.SADDLE) {
                this.inventory.setItem(0, itemstack1);
            }
        } else if (nbt.getBoolean("Saddle")) {
            this.inventory.setItem(0, new ItemStack(Items.SADDLE));
        }

        this.updateSaddle();
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        if (other == this) {
            return false;
        } else if (other.getClass() != this.getClass()) {
            return false;
        } else {
            HorseBaseEntity horsebaseentity = (HorseBaseEntity)other;
            if (this.canBreed() && horsebaseentity.canBreed()) {
                int i = this.getType();
                int j = horsebaseentity.getType();
                return i == j || i == 0 && j == 1 || i == 1 && j == 0;
            } else {
                return false;
            }
        }
    }

    @Override
    public PassiveEntity makeChild(PassiveEntity mate) {
        HorseBaseEntity horsebaseentity = (HorseBaseEntity)mate;
        HorseBaseEntity horsebaseentity1 = new HorseBaseEntity(this.world);
        int i = this.getType();
        int j = horsebaseentity.getType();
        int k = 0;
        if (i == j) {
            k = i;
        } else if (i == 0 && j == 1 || i == 1 && j == 0) {
            k = 2;
        }

        if (k == 0) {
            int i1 = this.random.nextInt(9);
            int l;
            if (i1 < 4) {
                l = this.getVariant() & 0xFF;
            } else if (i1 < 8) {
                l = horsebaseentity.getVariant() & 0xFF;
            } else {
                l = this.random.nextInt(7);
            }

            int j1 = this.random.nextInt(5);
            if (j1 < 2) {
                l |= this.getVariant() & 0xFF00;
            } else if (j1 < 4) {
                l |= horsebaseentity.getVariant() & 0xFF00;
            } else {
                l |= this.random.nextInt(5) << 8 & 0xFF00;
            }

            horsebaseentity1.setVariant(l);
        }

        horsebaseentity1.setType(k);
        double d1 = this.getAttribute(EntityAttributes.MAX_HEALTH).getBase()
            + mate.getAttribute(EntityAttributes.MAX_HEALTH).getBase()
            + this.getChildHealthBonus();
        horsebaseentity1.getAttribute(EntityAttributes.MAX_HEALTH).setBase(d1 / 3.0);
        double d2 = this.getAttribute(JUMP_STRENGTH_ATTRIBUTE).getBase()
            + mate.getAttribute(JUMP_STRENGTH_ATTRIBUTE).getBase()
            + this.getChildJumpStrengthBonus();
        horsebaseentity1.getAttribute(JUMP_STRENGTH_ATTRIBUTE).setBase(d2 / 3.0);
        double d0 = this.getAttribute(EntityAttributes.MOVEMENT_SPEED).getBase()
            + mate.getAttribute(EntityAttributes.MOVEMENT_SPEED).getBase()
            + this.getMovementSpeedBonus();
        horsebaseentity1.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(d0 / 3.0);
        return horsebaseentity1;
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        int i = 0;
        int j = 0;
        if (data instanceof HorseBaseEntity.Data) {
            i = ((HorseBaseEntity.Data)data).type;
            j = ((HorseBaseEntity.Data)data).variant & 0xFF | this.random.nextInt(5) << 8;
        } else {
            if (this.random.nextInt(10) == 0) {
                i = 1;
            } else {
                int k = this.random.nextInt(7);
                int l = this.random.nextInt(5);
                i = 0;
                j = k | l << 8;
            }

            data = new HorseBaseEntity.Data(i, j);
        }

        this.setType(i);
        this.setVariant(j);
        if (this.random.nextInt(5) == 0) {
            this.setBreedingAge(-24000);
        }

        if (i != 4 && i != 3) {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(this.getChildHealthBonus());
            if (i == 0) {
                this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(this.getMovementSpeedBonus());
            } else {
                this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.175F);
            }
        } else {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(15.0);
            this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.2F);
        }

        if (i != 2 && i != 1) {
            this.getAttribute(JUMP_STRENGTH_ATTRIBUTE).setBase(this.getChildJumpStrengthBonus());
        } else {
            this.getAttribute(JUMP_STRENGTH_ATTRIBUTE).setBase(0.5);
        }

        this.setHealth(this.getMaxHealth());
        return data;
    }

    public float getGrassAnimationProgress(float tickDelta) {
        return this.lastEatingGrassAnimationProgress + (this.eatingGrassAnimationProgress - this.lastEatingGrassAnimationProgress) * tickDelta;
    }

    public float getAngryAnimationProgress(float tickDelta) {
        return this.lastAngryAnimationProgress + (this.angryAnimationProgress - this.lastAngryAnimationProgress) * tickDelta;
    }

    public float getEatingAnimationProgress(float tickDelta) {
        return this.lastEatingAnimationProgress + (this.eatingAnimationProgress - this.lastEatingAnimationProgress) * tickDelta;
    }

    public void setJumpStrength(int strength) {
        if (this.isSaddled()) {
            if (strength < 0) {
                strength = 0;
            } else {
                this.jumping = true;
                this.updateAnger();
            }

            if (strength >= 90) {
                this.jumpStrength = 1.0F;
            } else {
                this.jumpStrength = 0.4F + 0.4F * strength / 90.0F;
            }
        }
    }

    protected void addTamingParticles(boolean success) {
        ParticleType particletype = success ? ParticleType.HEART : ParticleType.SMOKE_NORMAL;

        for (int i = 0; i < 7; i++) {
            double d0 = this.random.nextGaussian() * 0.02;
            double d1 = this.random.nextGaussian() * 0.02;
            double d2 = this.random.nextGaussian() * 0.02;
            this.world
                .addParticle(
                    particletype,
                    this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                    this.y + 0.5 + this.random.nextFloat() * this.height,
                    this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                    d0,
                    d1,
                    d2
                );
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 7) {
            this.addTamingParticles(true);
        } else if (event == 6) {
            this.addTamingParticles(false);
        } else {
            super.doEvent(event);
        }
    }

    @Override
    public void updateRiderPositon() {
        super.updateRiderPositon();
        if (this.lastAngryAnimationProgress > 0.0F) {
            float f = MathHelper.sin(this.bodyYaw * (float) Math.PI / 180.0F);
            float f1 = MathHelper.cos(this.bodyYaw * (float) Math.PI / 180.0F);
            float f2 = 0.7F * this.lastAngryAnimationProgress;
            float f3 = 0.15F * this.lastAngryAnimationProgress;
            this.rider.setPosition(this.x + f2 * f, this.y + this.getMountHeight() + this.rider.getRideHeight() + f3, this.z - f2 * f1);
            if (this.rider instanceof LivingEntity) {
                ((LivingEntity)this.rider).bodyYaw = this.bodyYaw;
            }
        }
    }

    private float getChildHealthBonus() {
        return 15.0F + this.random.nextInt(8) + this.random.nextInt(9);
    }

    private double getChildJumpStrengthBonus() {
        return 0.4F + this.random.nextDouble() * 0.2 + this.random.nextDouble() * 0.2 + this.random.nextDouble() * 0.2;
    }

    private double getMovementSpeedBonus() {
        return (0.45F + this.random.nextDouble() * 0.3 + this.random.nextDouble() * 0.3 + this.random.nextDouble() * 0.3) * 0.25;
    }

    public static boolean isHorseArmor(Item item) {
        return item == Items.IRON_HORSE_ARMOR || item == Items.GOLDEN_HORSE_ARMOR || item == Items.DIAMOND_HORSE_ARMOR;
    }

    @Override
    public boolean isClimbing() {
        return false;
    }

    @Override
    public float getEyeHeight() {
        return this.height;
    }

    @Override
    public boolean replaceItem(int slot, ItemStack item) {
        if (slot == 499 && this.canHaveChest()) {
            if (item == null && this.hasChest()) {
                this.setHasChest(false);
                this.updateInventory();
                return true;
            }

            if (item != null && item.getItem() == Item.byBlock(Blocks.CHEST) && !this.hasChest()) {
                this.setHasChest(true);
                this.updateInventory();
                return true;
            }
        }

        int i = slot - 400;
        if (i >= 0 && i < 2 && i < this.inventory.getSize()) {
            if (i == 0 && item != null && item.getItem() != Items.SADDLE) {
                return false;
            } else if (i != 1 || (item == null || isHorseArmor(item.getItem())) && this.canHaveArmor()) {
                this.inventory.setItem(i, item);
                this.updateSaddle();
                return true;
            } else {
                return false;
            }
        } else {
            int j = slot - 500 + 2;
            if (j >= 2 && j < this.inventory.getSize()) {
                this.inventory.setItem(j, item);
                return true;
            } else {
                return false;
            }
        }
    }

    public static class Data implements EntityData {
        public int type;
        public int variant;

        public Data(int type, int variant) {
            this.type = type;
            this.variant = variant;
        }
    }
}
