package net.minecraft.block.entity;

import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Hopper;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.menu.HopperMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class HopperBlockEntity extends InventoryBlockEntity implements Hopper, Tickable {
    private ItemStack[] inventory = new ItemStack[5];
    private String customName;
    private int transferCooldown = -1;

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        NbtList nbtlist = nbt.getList("Items", 10);
        this.inventory = new ItemStack[this.getSize()];
        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }

        this.transferCooldown = nbt.getInt("TransferCooldown");

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            int j = nbtcompound.getByte("Slot");
            if (j >= 0 && j < this.inventory.length) {
                this.inventory[j] = ItemStack.fromNbt(nbtcompound);
            }
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.inventory.length; i++) {
            if (this.inventory[i] != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Slot", (byte)i);
                this.inventory[i].writeNbt(nbtcompound);
                nbtlist.addElement(nbtcompound);
            }
        }

        nbt.put("Items", nbtlist);
        nbt.putInt("TransferCooldown", this.transferCooldown);
        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.customName);
        }
    }

    @Override
    public void markDirty() {
        super.markDirty();
    }

    @Override
    public int getSize() {
        return this.inventory.length;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.inventory[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.inventory[slot] != null) {
            if (this.inventory[slot].size <= amount) {
                ItemStack itemstack1 = this.inventory[slot];
                this.inventory[slot] = null;
                return itemstack1;
            }

            ItemStack itemstack = this.inventory[slot].split(amount);
            if (this.inventory[slot].size == 0) {
                this.inventory[slot] = null;
            }

            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.inventory[slot] != null) {
            ItemStack itemstack = this.inventory[slot];
            this.inventory[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.inventory[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.hopper";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && this.customName.length() > 0;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockEntity(this.pos) == this
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public void onOpen(PlayerEntity player) {
    }

    @Override
    public void onClose(PlayerEntity player) {
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return true;
    }

    @Override
    public void tick() {
        if (this.world != null && !this.world.isClient) {
            this.transferCooldown--;
            if (!this.isOnCooldown()) {
                this.setCooldown(0);
                this.transferItems();
            }
        }
    }

    public boolean transferItems() {
        if (this.world != null && !this.world.isClient) {
            if (!this.isOnCooldown() && HopperBlock.isEnabled(this.getBlockMetadata())) {
                boolean flag = false;
                if (!this.isInventoryEmpty()) {
                    flag = this.pushItem();
                }

                if (!this.isFull()) {
                    flag = pullItem(this) || flag;
                }

                if (flag) {
                    this.setCooldown(8);
                    this.markDirty();
                    return true;
                }
            }

            return false;
        } else {
            return false;
        }
    }

    private boolean isInventoryEmpty() {
        for (ItemStack itemstack : this.inventory) {
            if (itemstack != null) {
                return false;
            }
        }

        return true;
    }

    private boolean isFull() {
        for (ItemStack itemstack : this.inventory) {
            if (itemstack == null || itemstack.size != itemstack.getMaxSize()) {
                return false;
            }
        }

        return true;
    }

    private boolean pushItem() {
        Inventory inventory = this.getTargetInventory();
        if (inventory == null) {
            return false;
        }

        Direction direction = HopperBlock.getFacing(this.getBlockMetadata()).getOpposite();
        if (this.isFullInventory(inventory, direction)) {
            return false;
        }

        for (int i = 0; i < this.getSize(); i++) {
            if (this.getItem(i) != null) {
                ItemStack itemstack = this.getItem(i).copy();
                ItemStack itemstack1 = pushItem(inventory, this.removeItem(i, 1), direction);
                if (itemstack1 == null || itemstack1.size == 0) {
                    inventory.markDirty();
                    return true;
                }

                this.setItem(i, itemstack);
            }
        }

        return false;
    }

    private boolean isFullInventory(Inventory inventory, Direction side) {
        if (inventory instanceof SidedInventory) {
            SidedInventory sidedinventory = (SidedInventory)inventory;
            int[] aint = sidedinventory.getSlots(side);

            for (int i = 0; i < aint.length; i++) {
                ItemStack itemstack = sidedinventory.getItem(aint[i]);
                if (itemstack == null || itemstack.size != itemstack.getMaxSize()) {
                    return false;
                }
            }
        } else {
            int j = inventory.getSize();

            for (int k = 0; k < j; k++) {
                ItemStack itemstack1 = inventory.getItem(k);
                if (itemstack1 == null || itemstack1.size != itemstack1.getMaxSize()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isEmptyInventory(Inventory inventory, Direction side) {
        if (inventory instanceof SidedInventory) {
            SidedInventory sidedinventory = (SidedInventory)inventory;
            int[] aint = sidedinventory.getSlots(side);

            for (int i = 0; i < aint.length; i++) {
                if (sidedinventory.getItem(aint[i]) != null) {
                    return false;
                }
            }
        } else {
            int j = inventory.getSize();

            for (int k = 0; k < j; k++) {
                if (inventory.getItem(k) != null) {
                    return false;
                }
            }
        }

        return true;
    }

    public static boolean pullItem(Hopper hopper) {
        Inventory inventory = getInventoryAbove(hopper);
        if (inventory != null) {
            Direction direction = Direction.DOWN;
            if (isEmptyInventory(inventory, direction)) {
                return false;
            }

            if (inventory instanceof SidedInventory) {
                SidedInventory sidedinventory = (SidedInventory)inventory;
                int[] aint = sidedinventory.getSlots(direction);

                for (int i = 0; i < aint.length; i++) {
                    if (pullItems(hopper, inventory, aint[i], direction)) {
                        return true;
                    }
                }
            } else {
                int j = inventory.getSize();

                for (int k = 0; k < j; k++) {
                    if (pullItems(hopper, inventory, k, direction)) {
                        return true;
                    }
                }
            }
        } else {
            for (ItemEntity itementity : getItem(hopper.getWorld(), hopper.getX(), hopper.getY() + 1.0, hopper.getZ())) {
                if (pickUpItem(hopper, itementity)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean pullItems(Hopper hopper, Inventory inventory, int slot, Direction side) {
        ItemStack itemstack = inventory.getItem(slot);
        if (itemstack != null && canPullItem(inventory, itemstack, slot, side)) {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = pushItem(hopper, inventory.removeItem(slot, 1), null);
            if (itemstack2 == null || itemstack2.size == 0) {
                inventory.markDirty();
                return true;
            }

            inventory.setItem(slot, itemstack1);
        }

        return false;
    }

    public static boolean pickUpItem(Inventory inventory, ItemEntity item) {
        boolean flag = false;
        if (item == null) {
            return false;
        }

        ItemStack itemstack = item.getItem().copy();
        ItemStack itemstack1 = pushItem(inventory, itemstack, null);
        if (itemstack1 != null && itemstack1.size != 0) {
            item.setItem(itemstack1);
        } else {
            flag = true;
            item.remove();
        }

        return flag;
    }

    public static ItemStack pushItem(Inventory inventory, ItemStack item, Direction side) {
        if (inventory instanceof SidedInventory && side != null) {
            SidedInventory sidedinventory = (SidedInventory)inventory;
            int[] aint = sidedinventory.getSlots(side);

            for (int k = 0; k < aint.length && item != null && item.size > 0; k++) {
                item = pushItem(inventory, item, aint[k], side);
            }
        } else {
            int i = inventory.getSize();

            for (int j = 0; j < i && item != null && item.size > 0; j++) {
                item = pushItem(inventory, item, j, side);
            }
        }

        if (item != null && item.size == 0) {
            item = null;
        }

        return item;
    }

    private static boolean canPushItem(Inventory inventory, ItemStack item, int slot, Direction side) {
        return inventory.isItemAllowed(slot, item) && (!(inventory instanceof SidedInventory) || ((SidedInventory)inventory).canPushItem(slot, item, side));
    }

    private static boolean canPullItem(Inventory inventory, ItemStack item, int slot, Direction side) {
        return !(inventory instanceof SidedInventory) || ((SidedInventory)inventory).canPullItem(slot, item, side);
    }

    private static ItemStack pushItem(Inventory inventory, ItemStack item, int slot, Direction side) {
        ItemStack itemstack = inventory.getItem(slot);
        if (canPushItem(inventory, item, slot, side)) {
            boolean flag = false;
            if (itemstack == null) {
                inventory.setItem(slot, item);
                item = null;
                flag = true;
            } else if (canMergeItems(itemstack, item)) {
                int i = item.getMaxSize() - itemstack.size;
                int j = Math.min(item.size, i);
                item.size -= j;
                itemstack.size += j;
                flag = j > 0;
            }

            if (flag) {
                if (inventory instanceof HopperBlockEntity) {
                    HopperBlockEntity hopperblockentity = (HopperBlockEntity)inventory;
                    if (hopperblockentity.canTransferNextTick()) {
                        hopperblockentity.setCooldown(8);
                    }

                    inventory.markDirty();
                }

                inventory.markDirty();
            }
        }

        return item;
    }

    /**
     * Return the inventory of the block this hopper is pointing into.
     */
    private Inventory getTargetInventory() {
        Direction direction = HopperBlock.getFacing(this.getBlockMetadata());
        return getInventoryAt(
            this.getWorld(), this.pos.getX() + direction.getOffsetX(), this.pos.getY() + direction.getOffsetY(), this.pos.getZ() + direction.getOffsetZ()
        );
    }

    public static Inventory getInventoryAbove(Hopper hopper) {
        return getInventoryAt(hopper.getWorld(), hopper.getX(), hopper.getY() + 1.0, hopper.getZ());
    }

    public static List<ItemEntity> getItem(World world, double x, double y, double z) {
        return world.getEntitiesOfType(ItemEntity.class, new Box(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), EntityFilter.ALIVE);
    }

    public static Inventory getInventoryAt(World world, double x, double y, double z) {
        Inventory inventory = null;
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        BlockPos blockpos = new BlockPos(i, j, k);
        Block block = world.getBlockState(blockpos).getBlock();
        if (block.hasBlockEntity()) {
            BlockEntity blockentity = world.getBlockEntity(blockpos);
            if (blockentity instanceof Inventory) {
                inventory = (Inventory)blockentity;
                if (inventory instanceof ChestBlockEntity && block instanceof ChestBlock) {
                    inventory = ((ChestBlock)block).getInventory(world, blockpos);
                }
            }
        }

        if (inventory == null) {
            List<Entity> list = world.getEntities(null, new Box(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5), EntityFilter.INVENTORY);
            if (list.size() > 0) {
                inventory = (Inventory)list.get(world.random.nextInt(list.size()));
            }
        }

        return inventory;
    }

    private static boolean canMergeItems(ItemStack item1, ItemStack item2) {
        return item1.getItem() == item2.getItem()
            && item1.getMetadata() == item2.getMetadata()
            && item1.size <= item1.getMaxSize()
            && ItemStack.matchesNbt(item1, item2);
    }

    @Override
    public double getX() {
        return this.pos.getX() + 0.5;
    }

    @Override
    public double getY() {
        return this.pos.getY() + 0.5;
    }

    @Override
    public double getZ() {
        return this.pos.getZ() + 0.5;
    }

    public void setCooldown(int cooldown) {
        this.transferCooldown = cooldown;
    }

    public boolean isOnCooldown() {
        return this.transferCooldown > 0;
    }

    public boolean canTransferNextTick() {
        return this.transferCooldown <= 1;
    }

    @Override
    public String getMenuType() {
        return "minecraft:hopper";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new HopperMenu(playerInventory, this, player);
    }

    @Override
    public int getData(int id) {
        return 0;
    }

    @Override
    public void setData(int id, int value) {
    }

    @Override
    public int getDataSize() {
        return 0;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
    }
}
