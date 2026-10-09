package net.minecraft.entity;

import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ItemEntity extends Entity {
    private static final Logger LOGGER = LogManager.getLogger();
    private int age;
    private int pickUpDelay;
    private int health = 5;
    private String thrower;
    private String owner;
    public float bobOffset = (float)(Math.random() * Math.PI * 2.0);

    public ItemEntity(World world, double x, double y, double z) {
        super(world);
        this.setSize(0.25F, 0.25F);
        this.setPosition(x, y, z);
        this.yaw = (float)(Math.random() * 360.0);
        this.velocityX = (float)(Math.random() * 0.2F - 0.1F);
        this.velocityY = 0.2F;
        this.velocityZ = (float)(Math.random() * 0.2F - 0.1F);
    }

    public ItemEntity(World world, double x, double y, double z, ItemStack item) {
        this(world, x, y, z);
        this.setItem(item);
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    public ItemEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
        this.setItem(new ItemStack(Blocks.AIR, 0));
    }

    @Override
    protected void registerSyncedData() {
        this.getSyncedData().add(10, 5);
    }

    @Override
    public void tick() {
        if (this.getItem() == null) {
            this.remove();
        } else {
            super.tick();
            if (this.pickUpDelay > 0 && this.pickUpDelay != 32767) {
                this.pickUpDelay--;
            }

            this.lastX = this.x;
            this.lastY = this.y;
            this.lastZ = this.z;
            this.velocityY -= 0.04F;
            this.noClip = this.pushAwayFrom(this.x, (this.getShape().minY + this.getShape().maxY) / 2.0, this.z);
            this.move(this.velocityX, this.velocityY, this.velocityZ);
            boolean flag = (int)this.lastX != (int)this.x || (int)this.lastY != (int)this.y || (int)this.lastZ != (int)this.z;
            if (flag || this.ticks % 25 == 0) {
                if (this.world.getBlockState(new BlockPos(this)).getBlock().getMaterial() == Material.LAVA) {
                    this.velocityY = 0.2F;
                    this.velocityX = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
                    this.velocityZ = (this.random.nextFloat() - this.random.nextFloat()) * 0.2F;
                    this.playSound("random.fizz", 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
                }

                if (!this.world.isClient) {
                    this.mergeWithNearbyItems();
                }
            }

            float f = 0.98F;
            if (this.onGround) {
                f = this.world
                        .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                        .getBlock()
                        .slipperiness
                    * 0.98F;
            }

            this.velocityX *= f;
            this.velocityY *= 0.98F;
            this.velocityZ *= f;
            if (this.onGround) {
                this.velocityY *= -0.5;
            }

            if (this.age != -32768) {
                this.age++;
            }

            this.checkWaterCollisions();
            if (!this.world.isClient && this.age >= 6000) {
                this.remove();
            }
        }
    }

    private void mergeWithNearbyItems() {
        for (ItemEntity itementity : this.world.getEntitiesOfType(ItemEntity.class, this.getShape().grown(0.5, 0.0, 0.5))) {
            this.mergeWith(itementity);
        }
    }

    private boolean mergeWith(ItemEntity item) {
        if (item == this) {
            return false;
        }

        if (item.isAlive() && this.isAlive()) {
            ItemStack itemstack = this.getItem();
            ItemStack itemstack1 = item.getItem();
            if (this.pickUpDelay == 32767 || item.pickUpDelay == 32767) {
                return false;
            }

            if (this.age != -32768 && item.age != -32768) {
                if (itemstack1.getItem() != itemstack.getItem()) {
                    return false;
                }

                if (itemstack1.hasNbt() ^ itemstack.hasNbt()) {
                    return false;
                }

                if (itemstack1.hasNbt() && !itemstack1.getNbt().equals(itemstack.getNbt())) {
                    return false;
                }

                if (itemstack1.getItem() == null) {
                    return false;
                }

                if (itemstack1.getItem().hasCustomData() && itemstack1.getMetadata() != itemstack.getMetadata()) {
                    return false;
                }

                if (itemstack1.size < itemstack.size) {
                    return item.mergeWith(this);
                }

                if (itemstack1.size + itemstack.size > itemstack1.getMaxSize()) {
                    return false;
                }

                itemstack1.size = itemstack1.size + itemstack.size;
                item.pickUpDelay = Math.max(item.pickUpDelay, this.pickUpDelay);
                item.age = Math.min(item.age, this.age);
                item.setItem(itemstack1);
                this.remove();
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void resetAge() {
        this.age = 4800;
    }

    @Override
    public boolean checkWaterCollisions() {
        if (this.world.applyLiquidDrag(this.getShape(), Material.WATER, this)) {
            if (!this.inWater && !this.firstTick) {
                this.doSplashEffect();
            }

            this.inWater = true;
        } else {
            this.inWater = false;
        }

        return this.inWater;
    }

    @Override
    protected void takeFireDamage(int amount) {
        this.takeDamage(DamageSource.FIRE, amount);
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (this.getItem() != null && this.getItem().getItem() == Items.NETHER_STAR && source.isExplosive()) {
            return false;
        }

        this.markDamaged();
        this.health = (int)(this.health - amount);
        if (this.health <= 0) {
            this.remove();
        }

        return false;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putShort("Health", (byte)this.health);
        nbt.putShort("Age", (short)this.age);
        nbt.putShort("PickupDelay", (short)this.pickUpDelay);
        if (this.getThrower() != null) {
            nbt.putString("Thrower", this.thrower);
        }

        if (this.getOwner() != null) {
            nbt.putString("Owner", this.owner);
        }

        if (this.getItem() != null) {
            nbt.put("Item", this.getItem().writeNbt(new NbtCompound()));
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.health = nbt.getShort("Health") & 255;
        this.age = nbt.getShort("Age");
        if (nbt.contains("PickupDelay")) {
            this.pickUpDelay = nbt.getShort("PickupDelay");
        }

        if (nbt.contains("Owner")) {
            this.owner = nbt.getString("Owner");
        }

        if (nbt.contains("Thrower")) {
            this.thrower = nbt.getString("Thrower");
        }

        NbtCompound nbtcompound = nbt.getCompound("Item");
        this.setItem(ItemStack.fromNbt(nbtcompound));
        if (this.getItem() == null) {
            this.remove();
        }
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (!this.world.isClient) {
            ItemStack itemstack = this.getItem();
            int i = itemstack.size;
            if (this.pickUpDelay == 0
                && (this.owner == null || 6000 - this.age <= 200 || this.owner.equals(player.getName()))
                && player.inventory.addItem(itemstack)) {
                if (itemstack.getItem() == Item.byBlock(Blocks.LOG)) {
                    player.incrementStat(Achievements.GET_LOG);
                }

                if (itemstack.getItem() == Item.byBlock(Blocks.LOG2)) {
                    player.incrementStat(Achievements.GET_LOG);
                }

                if (itemstack.getItem() == Items.LEATHER) {
                    player.incrementStat(Achievements.KILL_COW);
                }

                if (itemstack.getItem() == Items.DIAMOND) {
                    player.incrementStat(Achievements.GET_DIAMOND);
                }

                if (itemstack.getItem() == Items.BLAZE_ROD) {
                    player.incrementStat(Achievements.GET_BLAZE_ROD);
                }

                if (itemstack.getItem() == Items.DIAMOND && this.getThrower() != null) {
                    PlayerEntity playerentity = this.world.getPlayer(this.getThrower());
                    if (playerentity != null && playerentity != player) {
                        playerentity.incrementStat(Achievements.GIVE_DIAMOND);
                    }
                }

                if (!this.isSilent()) {
                    this.world.playSound((Entity)player, "random.pop", 0.2F, ((this.random.nextFloat() - this.random.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                }

                player.sendPickup(this, i);
                if (itemstack.size <= 0) {
                    this.remove();
                }
            }
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getCustomName() : I18n.translate("item." + this.getItem().getTranslationKey());
    }

    @Override
    public boolean canBePunched() {
        return false;
    }

    @Override
    public void changeDimension(int dimension) {
        super.changeDimension(dimension);
        if (!this.world.isClient) {
            this.mergeWithNearbyItems();
        }
    }

    public ItemStack getItem() {
        ItemStack itemstack = this.getSyncedData().getItem(10);
        if (itemstack == null) {
            if (this.world != null) {
                LOGGER.error("Item entity " + this.getNetworkId() + " has no item?!");
            }

            return new ItemStack(Blocks.STONE);
        } else {
            return itemstack;
        }
    }

    public void setItem(ItemStack item) {
        this.getSyncedData().update(10, item);
        this.getSyncedData().markDirty(10);
    }

    public String getOwner() {
        return this.owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getThrower() {
        return this.thrower;
    }

    public void setThrower(String thrower) {
        this.thrower = thrower;
    }

    public int getAge() {
        return this.age;
    }

    public void setDefaultPickUpDelay() {
        this.pickUpDelay = 10;
    }

    public void setNoPickUpDelay() {
        this.pickUpDelay = 0;
    }

    public void setNeverPickUp() {
        this.pickUpDelay = 32767;
    }

    public void setPickUpDelay(int delay) {
        this.pickUpDelay = delay;
    }

    public boolean hasPickUpDelay() {
        return this.pickUpDelay > 0;
    }

    public void setNeverDespawn() {
        this.age = -6000;
    }

    public void makeFakeItem() {
        this.setNeverPickUp();
        this.age = 5999;
    }
}
