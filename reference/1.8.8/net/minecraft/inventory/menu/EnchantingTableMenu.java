package net.minecraft.inventory.menu;

import java.util.List;
import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentEntry;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EnchantingTableMenu extends InventoryMenu {
    public Inventory inventory = new SimpleInventory("Enchant", true, 2) {
        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public void markDirty() {
            super.markDirty();
            EnchantingTableMenu.this.onContentsChanged(this);
        }
    };
    private World world;
    private BlockPos pos;
    private Random random = new Random();
    public int seed;
    public int[] enchantingCosts = new int[3];
    public int[] enchantmentClues = new int[]{-1, -1, -1};

    public EnchantingTableMenu(PlayerInventory playerInventory, World world) {
        this(playerInventory, world, BlockPos.ORIGIN);
    }

    public EnchantingTableMenu(PlayerInventory playerInventory, World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;
        this.seed = playerInventory.player.getEnchantingSeed();
        this.addSlot(new InventorySlot(this.inventory, 0, 15, 47) {
            @Override
            public boolean isItemAllowed(ItemStack item) {
                return true;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        this.addSlot(new InventorySlot(this.inventory, 1, 35, 47) {
            @Override
            public boolean isItemAllowed(ItemStack item) {
                return item.getItem() == Items.DYE && DyeColor.byMetadata(item.getMetadata()) == DyeColor.BLUE;
            }
        });

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new InventorySlot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; k++) {
            this.addSlot(new InventorySlot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public void addListener(InventoryMenuListener listener) {
        super.addListener(listener);
        listener.onDataChanged(this, 0, this.enchantingCosts[0]);
        listener.onDataChanged(this, 1, this.enchantingCosts[1]);
        listener.onDataChanged(this, 2, this.enchantingCosts[2]);
        listener.onDataChanged(this, 3, this.seed & -16);
        listener.onDataChanged(this, 4, this.enchantmentClues[0]);
        listener.onDataChanged(this, 5, this.enchantmentClues[1]);
        listener.onDataChanged(this, 6, this.enchantmentClues[2]);
    }

    @Override
    public void updateListeners() {
        super.updateListeners();

        for (int i = 0; i < this.listeners.size(); i++) {
            InventoryMenuListener inventorymenulistener = this.listeners.get(i);
            inventorymenulistener.onDataChanged(this, 0, this.enchantingCosts[0]);
            inventorymenulistener.onDataChanged(this, 1, this.enchantingCosts[1]);
            inventorymenulistener.onDataChanged(this, 2, this.enchantingCosts[2]);
            inventorymenulistener.onDataChanged(this, 3, this.seed & -16);
            inventorymenulistener.onDataChanged(this, 4, this.enchantmentClues[0]);
            inventorymenulistener.onDataChanged(this, 5, this.enchantmentClues[1]);
            inventorymenulistener.onDataChanged(this, 6, this.enchantmentClues[2]);
        }
    }

    @Override
    public void setData(int id, int value) {
        if (id >= 0 && id <= 2) {
            this.enchantingCosts[id] = value;
        } else if (id == 3) {
            this.seed = value;
        } else if (id >= 4 && id <= 6) {
            this.enchantmentClues[id - 4] = value;
        } else {
            super.setData(id, value);
        }
    }

    @Override
    public void onContentsChanged(Inventory inventory) {
        if (inventory == this.inventory) {
            ItemStack itemstack = inventory.getItem(0);
            if (itemstack != null && itemstack.isEnchantable()) {
                if (!this.world.isClient) {
                    int l = 0;

                    for (int j = -1; j <= 1; j++) {
                        for (int k = -1; k <= 1; k++) {
                            if ((j != 0 || k != 0) && this.world.isAir(this.pos.add(k, 0, j)) && this.world.isAir(this.pos.add(k, 1, j))) {
                                if (this.world.getBlockState(this.pos.add(k * 2, 0, j * 2)).getBlock() == Blocks.BOOKSHELF) {
                                    l++;
                                }

                                if (this.world.getBlockState(this.pos.add(k * 2, 1, j * 2)).getBlock() == Blocks.BOOKSHELF) {
                                    l++;
                                }

                                if (k != 0 && j != 0) {
                                    if (this.world.getBlockState(this.pos.add(k * 2, 0, j)).getBlock() == Blocks.BOOKSHELF) {
                                        l++;
                                    }

                                    if (this.world.getBlockState(this.pos.add(k * 2, 1, j)).getBlock() == Blocks.BOOKSHELF) {
                                        l++;
                                    }

                                    if (this.world.getBlockState(this.pos.add(k, 0, j * 2)).getBlock() == Blocks.BOOKSHELF) {
                                        l++;
                                    }

                                    if (this.world.getBlockState(this.pos.add(k, 1, j * 2)).getBlock() == Blocks.BOOKSHELF) {
                                        l++;
                                    }
                                }
                            }
                        }
                    }

                    this.random.setSeed(this.seed);

                    for (int i1 = 0; i1 < 3; i1++) {
                        this.enchantingCosts[i1] = EnchantmentHelper.getRequiredXpLevel(this.random, i1, l, itemstack);
                        this.enchantmentClues[i1] = -1;
                        if (this.enchantingCosts[i1] < i1 + 1) {
                            this.enchantingCosts[i1] = 0;
                        }
                    }

                    for (int j1 = 0; j1 < 3; j1++) {
                        if (this.enchantingCosts[j1] > 0) {
                            List<EnchantmentEntry> list = this.getEnchantments(itemstack, j1, this.enchantingCosts[j1]);
                            if (list != null && !list.isEmpty()) {
                                EnchantmentEntry enchantmententry = list.get(this.random.nextInt(list.size()));
                                this.enchantmentClues[j1] = enchantmententry.enchantment.id | enchantmententry.level << 8;
                            }
                        }
                    }

                    this.updateListeners();
                }
            } else {
                for (int i = 0; i < 3; i++) {
                    this.enchantingCosts[i] = 0;
                    this.enchantmentClues[i] = -1;
                }
            }
        }
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        ItemStack itemstack = this.inventory.getItem(0);
        ItemStack itemstack1 = this.inventory.getItem(1);
        int i = id + 1;
        if ((itemstack1 == null || itemstack1.size < i) && !player.abilities.creativeMode) {
            return false;
        }

        if (this.enchantingCosts[id] > 0
            && itemstack != null
            && (player.xpLevel >= i && player.xpLevel >= this.enchantingCosts[id] || player.abilities.creativeMode)) {
            if (!this.world.isClient) {
                List<EnchantmentEntry> list = this.getEnchantments(itemstack, id, this.enchantingCosts[id]);
                boolean flag = itemstack.getItem() == Items.BOOK;
                if (list != null) {
                    player.applyEnchantmentCosts(i);
                    if (flag) {
                        itemstack.setItem(Items.ENCHANTED_BOOK);
                    }

                    for (int j = 0; j < list.size(); j++) {
                        EnchantmentEntry enchantmententry = list.get(j);
                        if (flag) {
                            Items.ENCHANTED_BOOK.addEnchantment(itemstack, enchantmententry);
                        } else {
                            itemstack.addEnchantment(enchantmententry.enchantment, enchantmententry.level);
                        }
                    }

                    if (!player.abilities.creativeMode) {
                        itemstack1.size -= i;
                        if (itemstack1.size <= 0) {
                            this.inventory.setItem(1, null);
                        }
                    }

                    player.incrementStat(Stats.ITEMS_ENCHANTED);
                    this.inventory.markDirty();
                    this.seed = player.getEnchantingSeed();
                    this.onContentsChanged(this.inventory);
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private List<EnchantmentEntry> getEnchantments(ItemStack item, int id, int level) {
        this.random.setSeed(this.seed + id);
        List<EnchantmentEntry> list = EnchantmentHelper.getEnchantmentEntries(this.random, item, level);
        if (item.getItem() == Items.BOOK && list != null && list.size() > 1) {
            list.remove(this.random.nextInt(list.size()));
        }

        return list;
    }

    public int getLapisCount() {
        ItemStack itemstack = this.inventory.getItem(1);
        return itemstack == null ? 0 : itemstack.size;
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (!this.world.isClient) {
            for (int i = 0; i < this.inventory.getSize(); i++) {
                ItemStack itemstack = this.inventory.removeItemQuietly(i);
                if (itemstack != null) {
                    player.dropItem(itemstack, false);
                }
            }
        }
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockState(this.pos).getBlock() == Blocks.ENCHANTING_TABLE
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        ItemStack itemstack = null;
        InventorySlot inventoryslot = this.slots.get(slot);
        if (inventoryslot != null && inventoryslot.hasItem()) {
            ItemStack itemstack1 = inventoryslot.getItem();
            itemstack = itemstack1.copy();
            if (slot == 0) {
                if (!this.moveItem(itemstack1, 2, 38, true)) {
                    return null;
                }
            } else if (slot == 1) {
                if (!this.moveItem(itemstack1, 2, 38, true)) {
                    return null;
                }
            } else if (itemstack1.getItem() == Items.DYE && DyeColor.byMetadata(itemstack1.getMetadata()) == DyeColor.BLUE) {
                if (!this.moveItem(itemstack1, 1, 2, true)) {
                    return null;
                }
            } else {
                if (this.slots.get(0).hasItem() || !this.slots.get(0).isItemAllowed(itemstack1)) {
                    return null;
                }

                if (itemstack1.hasNbt() && itemstack1.size == 1) {
                    this.slots.get(0).setItem(itemstack1.copy());
                    itemstack1.size = 0;
                } else if (itemstack1.size >= 1) {
                    this.slots.get(0).setItem(new ItemStack(itemstack1.getItem(), 1, itemstack1.getMetadata()));
                    itemstack1.size--;
                }
            }

            if (itemstack1.size == 0) {
                inventoryslot.setItem(null);
            } else {
                inventoryslot.markDirty();
            }

            if (itemstack1.size == itemstack.size) {
                return null;
            }

            inventoryslot.onItemRemoved(player, itemstack1);
        }

        return itemstack;
    }
}
