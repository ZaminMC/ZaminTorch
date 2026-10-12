package net.minecraft.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.DoubleInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.ChestMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

public class ChestBlockEntity extends InventoryBlockEntity implements Tickable, Inventory {
    private ItemStack[] inventory = new ItemStack[27];
    public boolean doubleChest;
    public ChestBlockEntity northNeighbor;
    public ChestBlockEntity eastNeighbor;
    public ChestBlockEntity westNeighbor;
    public ChestBlockEntity southNeighbor;
    public float animationProgress;
    public float lastAnimationProgress;
    public int viewerCount;
    private int ticks;
    private int chestType;
    private String customName;

    public ChestBlockEntity() {
        this.chestType = -1;
    }

    public ChestBlockEntity(int type) {
        this.chestType = type;
    }

    @Override
    public int getSize() {
        return 27;
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
                this.markDirty();
                return itemstack1;
            }

            ItemStack itemstack = this.inventory[slot].split(amount);
            if (this.inventory[slot].size == 0) {
                this.inventory[slot] = null;
            }

            this.markDirty();
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

        this.markDirty();
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.chest";
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
        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            int j = nbtcompound.getByte("Slot") & 255;
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
        if (this.hasCustomName()) {
            nbt.putString("CustomName", this.customName);
        }
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
    public void clearBlockCache() {
        super.clearBlockCache();
        this.doubleChest = false;
    }

    private void combineWithNeighbor(ChestBlockEntity neighborChest, Direction dir) {
        if (neighborChest.isRemoved()) {
            this.doubleChest = false;
        } else if (this.doubleChest) {
            switch (dir) {
                case NORTH:
                    if (this.northNeighbor != neighborChest) {
                        this.doubleChest = false;
                    }
                    break;
                case SOUTH:
                    if (this.southNeighbor != neighborChest) {
                        this.doubleChest = false;
                    }
                    break;
                case EAST:
                    if (this.eastNeighbor != neighborChest) {
                        this.doubleChest = false;
                    }
                    break;
                case WEST:
                    if (this.westNeighbor != neighborChest) {
                        this.doubleChest = false;
                    }
            }
        }
    }

    public void updateShape() {
        if (!this.doubleChest) {
            this.doubleChest = true;
            this.westNeighbor = this.findNeighborChest(Direction.WEST);
            this.eastNeighbor = this.findNeighborChest(Direction.EAST);
            this.northNeighbor = this.findNeighborChest(Direction.NORTH);
            this.southNeighbor = this.findNeighborChest(Direction.SOUTH);
        }
    }

    protected ChestBlockEntity findNeighborChest(Direction dir) {
        BlockPos blockpos = this.pos.offset(dir);
        if (this.isChestOfSameType(blockpos)) {
            BlockEntity blockentity = this.world.getBlockEntity(blockpos);
            if (blockentity instanceof ChestBlockEntity) {
                ChestBlockEntity chestblockentity = (ChestBlockEntity)blockentity;
                chestblockentity.combineWithNeighbor(this, dir.getOpposite());
                return chestblockentity;
            }
        }

        return null;
    }

    private boolean isChestOfSameType(BlockPos pos) {
        if (this.world == null) {
            return false;
        }

        Block block = this.world.getBlockState(pos).getBlock();
        return block instanceof ChestBlock && ((ChestBlock)block).type == this.getChestType();
    }

    @Override
    public void tick() {
        this.updateShape();
        int i = this.pos.getX();
        int j = this.pos.getY();
        int k = this.pos.getZ();
        this.ticks++;
        if (!this.world.isClient && this.viewerCount != 0 && (this.ticks + i + j + k) % 200 == 0) {
            this.viewerCount = 0;
            float f = 5.0F;

            for (PlayerEntity playerentity : this.world.getEntitiesOfType(PlayerEntity.class, new Box(i - f, j - f, k - f, i + 1 + f, j + 1 + f, k + 1 + f))) {
                if (playerentity.menu instanceof ChestMenu) {
                    Inventory inventory = ((ChestMenu)playerentity.menu).getChest();
                    if (inventory == this || inventory instanceof DoubleInventory && ((DoubleInventory)inventory).contains(this)) {
                        this.viewerCount++;
                    }
                }
            }
        }

        this.lastAnimationProgress = this.animationProgress;
        float f1 = 0.1F;
        if (this.viewerCount > 0 && this.animationProgress == 0.0F && this.northNeighbor == null && this.westNeighbor == null) {
            double d1 = i + 0.5;
            double d2 = k + 0.5;
            if (this.southNeighbor != null) {
                d2 += 0.5;
            }

            if (this.eastNeighbor != null) {
                d1 += 0.5;
            }

            this.world.playSound(d1, j + 0.5, d2, "random.chestopen", 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
        }

        if (this.viewerCount == 0 && this.animationProgress > 0.0F || this.viewerCount > 0 && this.animationProgress < 1.0F) {
            float f2 = this.animationProgress;
            if (this.viewerCount > 0) {
                this.animationProgress += f1;
            } else {
                this.animationProgress -= f1;
            }

            if (this.animationProgress > 1.0F) {
                this.animationProgress = 1.0F;
            }

            float f3 = 0.5F;
            if (this.animationProgress < f3 && f2 >= f3 && this.northNeighbor == null && this.westNeighbor == null) {
                double d3 = i + 0.5;
                double d0 = k + 0.5;
                if (this.southNeighbor != null) {
                    d0 += 0.5;
                }

                if (this.eastNeighbor != null) {
                    d3 += 0.5;
                }

                this.world.playSound(d3, j + 0.5, d0, "random.chestclosed", 0.5F, this.world.random.nextFloat() * 0.1F + 0.9F);
            }

            if (this.animationProgress < 0.0F) {
                this.animationProgress = 0.0F;
            }
        }
    }

    @Override
    public boolean doEvent(int type, int data) {
        if (type == 1) {
            this.viewerCount = data;
            return true;
        } else {
            return super.doEvent(type, data);
        }
    }

    @Override
    public void onOpen(PlayerEntity player) {
        if (!player.isSpectator()) {
            if (this.viewerCount < 0) {
                this.viewerCount = 0;
            }

            this.viewerCount++;
            this.world.addBlockEvent(this.pos, this.getBlock(), 1, this.viewerCount);
            this.world.updateNeighbors(this.pos, this.getBlock());
            this.world.updateNeighbors(this.pos.down(), this.getBlock());
        }
    }

    @Override
    public void onClose(PlayerEntity player) {
        if (!player.isSpectator() && this.getBlock() instanceof ChestBlock) {
            this.viewerCount--;
            this.world.addBlockEvent(this.pos, this.getBlock(), 1, this.viewerCount);
            this.world.updateNeighbors(this.pos, this.getBlock());
            this.world.updateNeighbors(this.pos.down(), this.getBlock());
        }
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return true;
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        this.clearBlockCache();
        this.updateShape();
    }

    public int getChestType() {
        if (this.chestType == -1) {
            if (this.world == null || !(this.getBlock() instanceof ChestBlock)) {
                return 0;
            }

            this.chestType = ((ChestBlock)this.getBlock()).type;
        }

        return this.chestType;
    }

    @Override
    public String getMenuType() {
        return "minecraft:chest";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new ChestMenu(playerInventory, this, player);
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
