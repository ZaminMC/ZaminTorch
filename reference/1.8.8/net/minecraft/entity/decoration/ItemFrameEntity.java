package net.minecraft.entity.decoration;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.map.SavedMapData;

public class ItemFrameEntity extends DecorationEntity {
    private float setDropChance = 1.0F;

    public ItemFrameEntity(World world) {
        super(world);
    }

    public ItemFrameEntity(World world, BlockPos pos, Direction facing) {
        super(world, pos);
        this.setDirection(facing);
    }

    @Override
    protected void registerSyncedData() {
        this.getSyncedData().add(8, 5);
        this.getSyncedData().register(9, (byte)0);
    }

    @Override
    public float getPickRadius() {
        return 0.0F;
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (!source.isExplosive() && this.getDisplayItem() != null) {
            if (!this.world.isClient) {
                this.dropItemOrItemFrame(source.getAttacker(), false);
                this.setDisplayItem(null);
            }

            return true;
        } else {
            return super.takeDamage(source, amount);
        }
    }

    @Override
    public int getWidth() {
        return 12;
    }

    @Override
    public int getHeight() {
        return 12;
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        double d0 = 16.0;
        d0 *= 64.0 * this.viewDistanceScaling;
        return squaredDistanceToCamera < d0 * d0;
    }

    @Override
    public void onAttack(Entity entity) {
        this.dropItemOrItemFrame(entity, true);
    }

    public void dropItemOrItemFrame(Entity entity, boolean shouldDropSelf) {
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            ItemStack itemstack = this.getDisplayItem();
            if (entity instanceof PlayerEntity) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                if (playerentity.abilities.creativeMode) {
                    this.removeItem(itemstack);
                    return;
                }
            }

            if (shouldDropSelf) {
                this.dropItem(new ItemStack(Items.ITEM_FRAME), 0.0F);
            }

            if (itemstack != null && this.random.nextFloat() < this.setDropChance) {
                itemstack = itemstack.copy();
                this.removeItem(itemstack);
                this.dropItem(itemstack, 0.0F);
            }
        }
    }

    private void removeItem(ItemStack item) {
        if (item != null) {
            if (item.getItem() == Items.FILLED_MAP) {
                SavedMapData savedmapdata = ((FilledMapItem)item.getItem()).getSavedMapData(item, this.world);
                savedmapdata.decorations.remove("frame-" + this.getNetworkId());
            }

            item.setItemFrame(null);
        }
    }

    public ItemStack getDisplayItem() {
        return this.getSyncedData().getItem(8);
    }

    public void setDisplayItem(ItemStack item) {
        this.setDisplayItem(item, true);
    }

    private void setDisplayItem(ItemStack item, boolean updateComparators) {
        if (item != null) {
            item = item.copy();
            item.size = 1;
            item.setItemFrame(this);
        }

        this.getSyncedData().update(8, item);
        this.getSyncedData().markDirty(8);
        if (updateComparators && this.pos != null) {
            this.world.updateNeighborComparators(this.pos, Blocks.AIR);
        }
    }

    public int rotation() {
        return this.getSyncedData().getByte(9);
    }

    public void setItemRotation(int rotation) {
        this.setItemRotation(rotation, true);
    }

    private void setItemRotation(int rotation, boolean updateComparators) {
        this.getSyncedData().update(9, (byte)(rotation % 8));
        if (updateComparators && this.pos != null) {
            this.world.updateNeighborComparators(this.pos, Blocks.AIR);
        }
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        if (this.getDisplayItem() != null) {
            nbt.put("Item", this.getDisplayItem().writeNbt(new NbtCompound()));
            nbt.putByte("ItemRotation", (byte)this.rotation());
            nbt.putFloat("ItemDropChance", this.setDropChance);
        }

        super.writeCustomNbt(nbt);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        NbtCompound nbtcompound = nbt.getCompound("Item");
        if (nbtcompound != null && !nbtcompound.isEmpty()) {
            this.setDisplayItem(ItemStack.fromNbt(nbtcompound), false);
            this.setItemRotation(nbt.getByte("ItemRotation"), false);
            if (nbt.contains("ItemDropChance", 99)) {
                this.setDropChance = nbt.getFloat("ItemDropChance");
            }

            if (nbt.contains("Direction")) {
                this.setItemRotation(this.rotation() * 2, false);
            }
        }

        super.readCustomNbt(nbt);
    }

    @Override
    public boolean interact(PlayerEntity player) {
        if (this.getDisplayItem() == null) {
            ItemStack itemstack = player.getDisplayItemInHand();
            if (itemstack != null && !this.world.isClient) {
                this.setDisplayItem(itemstack);
                if (!player.abilities.creativeMode && --itemstack.size <= 0) {
                    player.inventory.setItem(player.inventory.selectedSlot, null);
                }
            }
        } else if (!this.world.isClient) {
            this.setItemRotation(this.rotation() + 1);
        }

        return true;
    }

    public int getAnalogSignal() {
        return this.getDisplayItem() == null ? 0 : this.rotation() % 8 + 1;
    }
}
