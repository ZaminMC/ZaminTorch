package net.minecraft.inventory.menu;

import java.util.Map;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.ResultInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AnvilMenu extends InventoryMenu {
    private static final Logger LOGGER = LogManager.getLogger();
    private Inventory resultInventory = new ResultInventory();
    private Inventory repairInventory = new SimpleInventory("Repair", true, 2) {
        @Override
        public void markDirty() {
            super.markDirty();
            AnvilMenu.this.onContentsChanged(this);
        }
    };
    private World world;
    private BlockPos pos;
    public int repairCost;
    private int minRepairItemCount;
    private String itemName;
    private final PlayerEntity player;

    public AnvilMenu(PlayerInventory playerInventory, World world, PlayerEntity player) {
        this(playerInventory, world, BlockPos.ORIGIN, player);
    }

    public AnvilMenu(PlayerInventory playerInventory, World world, BlockPos pos, PlayerEntity player) {
        this.pos = pos;
        this.world = world;
        this.player = player;
        this.addSlot(new InventorySlot(this.repairInventory, 0, 27, 47));
        this.addSlot(new InventorySlot(this.repairInventory, 1, 76, 47));
        this.addSlot(new InventorySlot(this.resultInventory, 2, 134, 47) {
            @Override
            public boolean isItemAllowed(ItemStack item) {
                return false;
            }

            @Override
            public boolean canPickUp(PlayerEntity player) {
                return (player.abilities.creativeMode || player.xpLevel >= AnvilMenu.this.repairCost) && AnvilMenu.this.repairCost > 0 && this.hasItem();
            }

            @Override
            public void onItemRemoved(PlayerEntity player, ItemStack item) {
                if (!player.abilities.creativeMode) {
                    player.addXp(-AnvilMenu.this.repairCost);
                }

                AnvilMenu.this.repairInventory.setItem(0, null);
                if (AnvilMenu.this.minRepairItemCount > 0) {
                    ItemStack itemstack = AnvilMenu.this.repairInventory.getItem(1);
                    if (itemstack != null && itemstack.size > AnvilMenu.this.minRepairItemCount) {
                        itemstack.size = itemstack.size - AnvilMenu.this.minRepairItemCount;
                        AnvilMenu.this.repairInventory.setItem(1, itemstack);
                    } else {
                        AnvilMenu.this.repairInventory.setItem(1, null);
                    }
                } else {
                    AnvilMenu.this.repairInventory.setItem(1, null);
                }

                AnvilMenu.this.repairCost = 0;
                BlockState blockstate = world.getBlockState(pos);
                if (!player.abilities.creativeMode && !world.isClient && blockstate.getBlock() == Blocks.ANVIL && player.getRandom().nextFloat() < 0.12F) {
                    int l = blockstate.get(AnvilBlock.DAMAGE);
                    if (++l > 2) {
                        world.removeBlock(pos);
                        world.doEvent(1020, pos, 0);
                    } else {
                        world.setBlockState(pos, blockstate.set(AnvilBlock.DAMAGE, l), 2);
                        world.doEvent(1021, pos, 0);
                    }
                } else if (!world.isClient) {
                    world.doEvent(1021, pos, 0);
                }
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
    public void onContentsChanged(Inventory inventory) {
        super.onContentsChanged(inventory);
        if (inventory == this.repairInventory) {
            this.updateResult();
        }
    }

    public void updateResult() {
        int i = 0;
        int j = 1;
        int k = 1;
        int l = 1;
        int i1 = 2;
        int j1 = 1;
        int k1 = 1;
        ItemStack itemstack = this.repairInventory.getItem(0);
        this.repairCost = 1;
        int l1 = 0;
        int i2 = 0;
        int j2 = 0;
        if (itemstack == null) {
            this.resultInventory.setItem(0, null);
            this.repairCost = 0;
        } else {
            ItemStack itemstack1 = itemstack.copy();
            ItemStack itemstack2 = this.repairInventory.getItem(1);
            Map<Integer, Integer> map = EnchantmentHelper.getEnchantments(itemstack1);
            boolean flag = false;
            i2 += itemstack.getRepairCost() + (itemstack2 == null ? 0 : itemstack2.getRepairCost());
            this.minRepairItemCount = 0;
            if (itemstack2 != null) {
                flag = itemstack2.getItem() == Items.ENCHANTED_BOOK && Items.ENCHANTED_BOOK.getStoredEnchantments(itemstack2).size() > 0;
                if (itemstack1.isDamageable() && itemstack1.getItem().isRepairable(itemstack, itemstack2)) {
                    int j4 = Math.min(itemstack1.getDamage(), itemstack1.getMaxDamage() / 4);
                    if (j4 <= 0) {
                        this.resultInventory.setItem(0, null);
                        this.repairCost = 0;
                        return;
                    }

                    int l4;
                    for (l4 = 0; j4 > 0 && l4 < itemstack2.size; l4++) {
                        int j5 = itemstack1.getDamage() - j4;
                        itemstack1.setDamage(j5);
                        l1++;
                        j4 = Math.min(itemstack1.getDamage(), itemstack1.getMaxDamage() / 4);
                    }

                    this.minRepairItemCount = l4;
                } else {
                    if (!flag && (itemstack1.getItem() != itemstack2.getItem() || !itemstack1.isDamageable())) {
                        this.resultInventory.setItem(0, null);
                        this.repairCost = 0;
                        return;
                    }

                    if (itemstack1.isDamageable() && !flag) {
                        int k2 = itemstack.getMaxDamage() - itemstack.getDamage();
                        int l2 = itemstack2.getMaxDamage() - itemstack2.getDamage();
                        int i3 = l2 + itemstack1.getMaxDamage() * 12 / 100;
                        int j3 = k2 + i3;
                        int k3 = itemstack1.getMaxDamage() - j3;
                        if (k3 < 0) {
                            k3 = 0;
                        }

                        if (k3 < itemstack1.getMetadata()) {
                            itemstack1.setDamage(k3);
                            l1 += 2;
                        }
                    }

                    Map<Integer, Integer> map1 = EnchantmentHelper.getEnchantments(itemstack2);

                    for (int i5 : map1.keySet()) {
                        Enchantment enchantment = Enchantment.byId(i5);
                        if (enchantment != null) {
                            int k5 = map.containsKey(i5) ? map.get(i5) : 0;
                            int l3 = map1.get(i5);
                            l3 = k5 == l3 ? ++l3 : Math.max(l3, k5);
                            boolean flag1 = enchantment.canEnchant(itemstack);
                            if (this.player.abilities.creativeMode || itemstack.getItem() == Items.ENCHANTED_BOOK) {
                                flag1 = true;
                            }

                            for (int i4 : map.keySet()) {
                                if (i4 != i5 && !enchantment.isCompatible(Enchantment.byId(i4))) {
                                    flag1 = false;
                                    l1++;
                                }
                            }

                            if (flag1) {
                                if (l3 > enchantment.getMaxLevel()) {
                                    l3 = enchantment.getMaxLevel();
                                }

                                map.put(i5, l3);
                                int l5 = 0;
                                switch (enchantment.getType()) {
                                    case 1:
                                        l5 = 8;
                                        break;
                                    case 2:
                                        l5 = 4;
                                    case 3:
                                    case 4:
                                    case 6:
                                    case 7:
                                    case 8:
                                    case 9:
                                    default:
                                        break;
                                    case 5:
                                        l5 = 2;
                                        break;
                                    case 10:
                                        l5 = 1;
                                }

                                if (flag) {
                                    l5 = Math.max(1, l5 / 2);
                                }

                                l1 += l5 * l3;
                            }
                        }
                    }
                }
            }

            if (StringUtils.isBlank(this.itemName)) {
                if (itemstack.hasCustomHoverName()) {
                    j2 = 1;
                    l1 += j2;
                    itemstack1.resetHoverName();
                }
            } else if (!this.itemName.equals(itemstack.getHoverName())) {
                j2 = 1;
                l1 += j2;
                itemstack1.setHoverName(this.itemName);
            }

            this.repairCost = i2 + l1;
            if (l1 <= 0) {
                itemstack1 = null;
            }

            if (j2 == l1 && j2 > 0 && this.repairCost >= 40) {
                this.repairCost = 39;
            }

            if (this.repairCost >= 40 && !this.player.abilities.creativeMode) {
                itemstack1 = null;
            }

            if (itemstack1 != null) {
                int k4 = itemstack1.getRepairCost();
                if (itemstack2 != null && k4 < itemstack2.getRepairCost()) {
                    k4 = itemstack2.getRepairCost();
                }

                k4 = k4 * 2 + 1;
                itemstack1.setRepairCost(k4);
                EnchantmentHelper.setEnchantments(map, itemstack1);
            }

            this.resultInventory.setItem(0, itemstack1);
            this.updateListeners();
        }
    }

    @Override
    public void addListener(InventoryMenuListener listener) {
        super.addListener(listener);
        listener.onDataChanged(this, 0, this.repairCost);
    }

    @Override
    public void setData(int id, int value) {
        if (id == 0) {
            this.repairCost = value;
        }
    }

    @Override
    public void close(PlayerEntity player) {
        super.close(player);
        if (!this.world.isClient) {
            for (int i = 0; i < this.repairInventory.getSize(); i++) {
                ItemStack itemstack = this.repairInventory.removeItemQuietly(i);
                if (itemstack != null) {
                    player.dropItem(itemstack, false);
                }
            }
        }
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.world.getBlockState(this.pos).getBlock() == Blocks.ANVIL
            && !(player.squaredDistanceTo(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) > 64.0);
    }

    @Override
    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        ItemStack itemstack = null;
        InventorySlot inventoryslot = this.slots.get(slot);
        if (inventoryslot != null && inventoryslot.hasItem()) {
            ItemStack itemstack1 = inventoryslot.getItem();
            itemstack = itemstack1.copy();
            if (slot == 2) {
                if (!this.moveItem(itemstack1, 3, 39, true)) {
                    return null;
                }

                inventoryslot.onQuickMoved(itemstack1, itemstack);
            } else if (slot != 0 && slot != 1) {
                if (slot >= 3 && slot < 39 && !this.moveItem(itemstack1, 0, 2, false)) {
                    return null;
                }
            } else if (!this.moveItem(itemstack1, 3, 39, false)) {
                return null;
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

    public void setItemName(String name) {
        this.itemName = name;
        if (this.getSlot(2).hasItem()) {
            ItemStack itemstack = this.getSlot(2).getItem();
            if (StringUtils.isBlank(name)) {
                itemstack.resetHoverName();
            } else {
                itemstack.setHoverName(this.itemName);
            }
        }

        this.updateResult();
    }
}
