package net.minecraft.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FurnaceBlock;
import net.minecraft.block.material.Material;
import net.minecraft.crafting.SmeltingManager;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.menu.FurnaceMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.slot.FurnaceFuelSlot;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

public class FurnaceBlockEntity extends InventoryBlockEntity implements Tickable, SidedInventory {
    private static final int[] INVENTORY_SLOTS_TOP = new int[]{0};
    private static final int[] INVENTORY_SLOTS_BOTTOM = new int[]{2, 1};
    private static final int[] INVENTORY_SLOTS_SIDES = new int[]{1};
    private ItemStack[] inventory = new ItemStack[3];
    private int fuelTime;
    private int totalFuelTime;
    private int cookTime;
    private int totalCookTime;
    private String customName;

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
        boolean flag = item != null && item.matchesItem(this.inventory[slot]) && ItemStack.matchesNbt(item, this.inventory[slot]);
        this.inventory[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }

        if (slot == 0 && !flag) {
            this.totalCookTime = this.getTotalCookTime(item);
            this.cookTime = 0;
            this.markDirty();
        }
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.furnace";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && this.customName.length() > 0;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        NbtList nbtlist = nbt.getList("Items", 10);
        this.inventory = new ItemStack[this.getSize()];

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            int j = nbtcompound.getByte("Slot");
            if (j >= 0 && j < this.inventory.length) {
                this.inventory[j] = ItemStack.fromNbt(nbtcompound);
            }
        }

        this.fuelTime = nbt.getShort("BurnTime");
        this.cookTime = nbt.getShort("CookTime");
        this.totalCookTime = nbt.getShort("CookTimeTotal");
        this.totalFuelTime = getFuelTime(this.inventory[1]);
        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putShort("BurnTime", (short)this.fuelTime);
        nbt.putShort("CookTime", (short)this.cookTime);
        nbt.putShort("CookTimeTotal", (short)this.totalCookTime);
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
        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.customName);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    public boolean hasFuel() {
        return this.fuelTime > 0;
    }

    public static boolean isLit(Inventory furnace) {
        return furnace.getData(0) > 0;
    }

    @Override
    public void tick() {
        boolean flag = this.hasFuel();
        boolean flag1 = false;
        if (this.hasFuel()) {
            this.fuelTime--;
        }

        if (!this.world.isClient) {
            if (this.hasFuel() || this.inventory[1] != null && this.inventory[0] != null) {
                if (!this.hasFuel() && this.canCook()) {
                    this.totalFuelTime = this.fuelTime = getFuelTime(this.inventory[1]);
                    if (this.hasFuel()) {
                        flag1 = true;
                        if (this.inventory[1] != null) {
                            this.inventory[1].size--;
                            if (this.inventory[1].size == 0) {
                                Item item = this.inventory[1].getItem().getRecipeRemainder();
                                this.inventory[1] = item != null ? new ItemStack(item) : null;
                            }
                        }
                    }
                }

                if (this.hasFuel() && this.canCook()) {
                    this.cookTime++;
                    if (this.cookTime == this.totalCookTime) {
                        this.cookTime = 0;
                        this.totalCookTime = this.getTotalCookTime(this.inventory[0]);
                        this.finishCooking();
                        flag1 = true;
                    }
                } else {
                    this.cookTime = 0;
                }
            } else if (!this.hasFuel() && this.cookTime > 0) {
                this.cookTime = MathHelper.clamp(this.cookTime - 2, 0, this.totalCookTime);
            }

            if (flag != this.hasFuel()) {
                flag1 = true;
                FurnaceBlock.updateLitState(this.hasFuel(), this.world, this.pos);
            }
        }

        if (flag1) {
            this.markDirty();
        }
    }

    public int getTotalCookTime(ItemStack item) {
        return 200;
    }

    private boolean canCook() {
        if (this.inventory[0] == null) {
            return false;
        }

        ItemStack itemstack = SmeltingManager.getInstance().getResult(this.inventory[0]);
        return itemstack != null
            && (
                this.inventory[2] == null
                    || this.inventory[2].matchesItem(itemstack)
                        && (
                            this.inventory[2].size < this.getMaxStackSize() && this.inventory[2].size < this.inventory[2].getMaxSize()
                                || this.inventory[2].size < itemstack.getMaxSize()
                        )
            );
    }

    public void finishCooking() {
        if (this.canCook()) {
            ItemStack itemstack = SmeltingManager.getInstance().getResult(this.inventory[0]);
            if (this.inventory[2] == null) {
                this.inventory[2] = itemstack.copy();
            } else if (this.inventory[2].getItem() == itemstack.getItem()) {
                this.inventory[2].size++;
            }

            if (this.inventory[0].getItem() == Item.byBlock(Blocks.SPONGE)
                && this.inventory[0].getMetadata() == 1
                && this.inventory[1] != null
                && this.inventory[1].getItem() == Items.BUCKET) {
                this.inventory[1] = new ItemStack(Items.WATER_BUCKET);
            }

            this.inventory[0].size--;
            if (this.inventory[0].size <= 0) {
                this.inventory[0] = null;
            }
        }
    }

    public static int getFuelTime(ItemStack item) {
        if (item == null) {
            return 0;
        }

        Item itemx = item.getItem();
        if (itemx instanceof BlockItem && Block.byItem(itemx) != Blocks.AIR) {
            Block block = Block.byItem(itemx);
            if (block == Blocks.WOODEN_SLAB) {
                return 150;
            }

            if (block.getMaterial() == Material.WOOD) {
                return 300;
            }

            if (block == Blocks.COAL_BLOCK) {
                return 16000;
            }
        }

        if (itemx instanceof ToolItem && ((ToolItem)itemx).getTierName().equals("WOOD")) {
            return 200;
        } else if (itemx instanceof SwordItem && ((SwordItem)itemx).getTierName().equals("WOOD")) {
            return 200;
        } else if (itemx instanceof HoeItem && ((HoeItem)itemx).getTierName().equals("WOOD")) {
            return 200;
        } else if (itemx == Items.STICK) {
            return 100;
        } else if (itemx == Items.COAL) {
            return 1600;
        } else if (itemx == Items.LAVA_BUCKET) {
            return 20000;
        } else if (itemx == Item.byBlock(Blocks.SAPLING)) {
            return 100;
        } else {
            return itemx == Items.BLAZE_ROD ? 2400 : 0;
        }
    }

    public static boolean isFuel(ItemStack item) {
        return getFuelTime(item) > 0;
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
        return slot != 2 && (slot != 1 || isFuel(item) || FurnaceFuelSlot.isBucket(item));
    }

    @Override
    public int[] getSlots(Direction side) {
        if (side == Direction.DOWN) {
            return INVENTORY_SLOTS_BOTTOM;
        } else {
            return side == Direction.UP ? INVENTORY_SLOTS_TOP : INVENTORY_SLOTS_SIDES;
        }
    }

    @Override
    public boolean canPushItem(int slot, ItemStack item, Direction side) {
        return this.isItemAllowed(slot, item);
    }

    @Override
    public boolean canPullItem(int slot, ItemStack item, Direction side) {
        if (side == Direction.DOWN && slot == 1) {
            Item itemx = item.getItem();
            if (itemx != Items.WATER_BUCKET && itemx != Items.BUCKET) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getMenuType() {
        return "minecraft:furnace";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new FurnaceMenu(playerInventory, this);
    }

    @Override
    public int getData(int id) {
        switch (id) {
            case 0:
                return this.fuelTime;
            case 1:
                return this.totalFuelTime;
            case 2:
                return this.cookTime;
            case 3:
                return this.totalCookTime;
            default:
                return 0;
        }
    }

    @Override
    public void setData(int id, int value) {
        switch (id) {
            case 0:
                this.fuelTime = value;
                break;
            case 1:
                this.totalFuelTime = value;
                break;
            case 2:
                this.cookTime = value;
                break;
            case 3:
                this.totalCookTime = value;
        }
    }

    @Override
    public int getDataSize() {
        return 4;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
    }
}
