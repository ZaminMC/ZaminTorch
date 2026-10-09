package net.minecraft.entity.vehicle;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Hopper;
import net.minecraft.inventory.menu.HopperMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HopperMinecartEntity extends InventoryMinecartEntity implements Hopper {
    private boolean enabled = true;
    private int transferCooldown = -1;
    private BlockPos f_0016592 = BlockPos.ORIGIN;

    public HopperMinecartEntity(World world) {
        super(world);
    }

    public HopperMinecartEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    public MinecartEntity.Type getMinecartType() {
        return MinecartEntity.Type.HOPPER;
    }

    @Override
    public BlockState getDefaultDisplayBlock() {
        return Blocks.HOPPER.defaultState();
    }

    @Override
    public int getDefaultDisplayBlockOffset() {
        return 1;
    }

    @Override
    public int getSize() {
        return 5;
    }

    @Override
    public boolean interact(PlayerEntity player) {
        if (!this.world.isClient) {
            player.openChestMenu(this);
        }

        return true;
    }

    @Override
    public void onActivatorRail(int x, int y, int z, boolean powered) {
        boolean flag = !powered;
        if (flag != this.isEnabled()) {
            this.setEnabled(flag);
        }
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public World getWorld() {
        return this.world;
    }

    @Override
    public double getX() {
        return this.x;
    }

    @Override
    public double getY() {
        return this.y + 0.5;
    }

    @Override
    public double getZ() {
        return this.z;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient && this.isAlive() && this.isEnabled()) {
            BlockPos blockpos = new BlockPos(this);
            if (blockpos.equals(this.f_0016592)) {
                this.transferCooldown--;
            } else {
                this.setTransferCooldown(0);
            }

            if (!this.hasTransferCooldown()) {
                this.setTransferCooldown(0);
                if (this.transferItems()) {
                    this.setTransferCooldown(4);
                    this.markDirty();
                }
            }
        }
    }

    public boolean transferItems() {
        if (HopperBlockEntity.pullItem(this)) {
            return true;
        }

        List<ItemEntity> list = this.world.getEntitiesOfType(ItemEntity.class, this.getShape().grown(0.25, 0.0, 0.25), EntityFilter.ALIVE);
        if (list.size() > 0) {
            HopperBlockEntity.pickUpItem(this, list.get(0));
        }

        return false;
    }

    @Override
    public void dropItems(DamageSource damageSource) {
        super.dropItems(damageSource);
        if (this.world.getGameRules().getBoolean("doEntityDrops")) {
            this.dropItem(Item.byBlock(Blocks.HOPPER), 1, 0.0F);
        }
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("TransferCooldown", this.transferCooldown);
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.transferCooldown = nbt.getInt("TransferCooldown");
    }

    public void setTransferCooldown(int transferCooldown) {
        this.transferCooldown = transferCooldown;
    }

    public boolean hasTransferCooldown() {
        return this.transferCooldown > 0;
    }

    @Override
    public String getMenuType() {
        return "minecraft:hopper";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new HopperMenu(playerInventory, this, player);
    }
}
