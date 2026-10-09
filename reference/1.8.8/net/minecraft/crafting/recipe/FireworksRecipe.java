package net.minecraft.crafting.recipe;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

public class FireworksRecipe implements Recipe {
    private ItemStack result;

    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        this.result = null;
        int i = 0;
        int j = 0;
        int k = 0;
        int l = 0;
        int i1 = 0;
        int j1 = 0;

        for (int k1 = 0; k1 < inventory.getSize(); k1++) {
            ItemStack itemstack = inventory.getItem(k1);
            if (itemstack != null) {
                if (itemstack.getItem() == Items.GUNPOWDER) {
                    j++;
                } else if (itemstack.getItem() == Items.FIREWORKS_CHARGE) {
                    l++;
                } else if (itemstack.getItem() == Items.DYE) {
                    k++;
                } else if (itemstack.getItem() == Items.PAPER) {
                    i++;
                } else if (itemstack.getItem() == Items.GLOWSTONE_DUST) {
                    i1++;
                } else if (itemstack.getItem() == Items.DIAMOND) {
                    i1++;
                } else if (itemstack.getItem() == Items.FIRE_CHARGE) {
                    j1++;
                } else if (itemstack.getItem() == Items.FEATHER) {
                    j1++;
                } else if (itemstack.getItem() == Items.GOLD_NUGGET) {
                    j1++;
                } else {
                    if (itemstack.getItem() != Items.SKULL) {
                        return false;
                    }

                    j1++;
                }
            }
        }

        i1 += k + j1;
        if (j > 3 || i > 1) {
            return false;
        }

        if (j >= 1 && i == 1 && i1 == 0) {
            this.result = new ItemStack(Items.FIREWORKS);
            if (l > 0) {
                NbtCompound nbtcompound1 = new NbtCompound();
                NbtCompound nbtcompound3 = new NbtCompound();
                NbtList nbtlist = new NbtList();

                for (int k2 = 0; k2 < inventory.getSize(); k2++) {
                    ItemStack itemstack3 = inventory.getItem(k2);
                    if (itemstack3 != null
                        && itemstack3.getItem() == Items.FIREWORKS_CHARGE
                        && itemstack3.hasNbt()
                        && itemstack3.getNbt().contains("Explosion", 10)) {
                        nbtlist.addElement(itemstack3.getNbt().getCompound("Explosion"));
                    }
                }

                nbtcompound3.put("Explosions", nbtlist);
                nbtcompound3.putByte("Flight", (byte)j);
                nbtcompound1.put("Fireworks", nbtcompound3);
                this.result.setNbt(nbtcompound1);
            }

            return true;
        } else if (j == 1 && i == 0 && l == 0 && k > 0 && j1 <= 1) {
            this.result = new ItemStack(Items.FIREWORKS_CHARGE);
            NbtCompound nbtcompound = new NbtCompound();
            NbtCompound nbtcompound2 = new NbtCompound();
            byte b0 = 0;
            List<Integer> list = Lists.newArrayList();

            for (int l1 = 0; l1 < inventory.getSize(); l1++) {
                ItemStack itemstack2 = inventory.getItem(l1);
                if (itemstack2 != null) {
                    if (itemstack2.getItem() == Items.DYE) {
                        list.add(DyeItem.COLORS[itemstack2.getMetadata() & 15]);
                    } else if (itemstack2.getItem() == Items.GLOWSTONE_DUST) {
                        nbtcompound2.putBoolean("Flicker", true);
                    } else if (itemstack2.getItem() == Items.DIAMOND) {
                        nbtcompound2.putBoolean("Trail", true);
                    } else if (itemstack2.getItem() == Items.FIRE_CHARGE) {
                        b0 = 1;
                    } else if (itemstack2.getItem() == Items.FEATHER) {
                        b0 = 4;
                    } else if (itemstack2.getItem() == Items.GOLD_NUGGET) {
                        b0 = 2;
                    } else if (itemstack2.getItem() == Items.SKULL) {
                        b0 = 3;
                    }
                }
            }

            int[] aint1 = new int[list.size()];

            for (int l2 = 0; l2 < aint1.length; l2++) {
                aint1[l2] = list.get(l2);
            }

            nbtcompound2.putIntArray("Colors", aint1);
            nbtcompound2.putByte("Type", b0);
            nbtcompound.put("Explosion", nbtcompound2);
            this.result.setNbt(nbtcompound);
            return true;
        } else if (j == 0 && i == 0 && l == 1 && k > 0 && k == i1) {
            List<Integer> list1 = Lists.newArrayList();

            for (int i2 = 0; i2 < inventory.getSize(); i2++) {
                ItemStack itemstack1 = inventory.getItem(i2);
                if (itemstack1 != null) {
                    if (itemstack1.getItem() == Items.DYE) {
                        list1.add(DyeItem.COLORS[itemstack1.getMetadata() & 15]);
                    } else if (itemstack1.getItem() == Items.FIREWORKS_CHARGE) {
                        this.result = itemstack1.copy();
                        this.result.size = 1;
                    }
                }
            }

            int[] aint = new int[list1.size()];

            for (int j2 = 0; j2 < aint.length; j2++) {
                aint[j2] = list1.get(j2);
            }

            if (this.result != null && this.result.hasNbt()) {
                NbtCompound nbtcompound4 = this.result.getNbt().getCompound("Explosion");
                if (nbtcompound4 == null) {
                    return false;
                }

                nbtcompound4.putIntArray("FadeColors", aint);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        return this.result.copy();
    }

    @Override
    public int size() {
        return 10;
    }

    @Override
    public ItemStack getResult() {
        return this.result;
    }

    @Override
    public ItemStack[] getRemainder(CraftingInventory inventory) {
        ItemStack[] aitemstack = new ItemStack[inventory.getSize()];

        for (int i = 0; i < aitemstack.length; i++) {
            ItemStack itemstack = inventory.getItem(i);
            if (itemstack != null && itemstack.getItem().hasRecipeRemainder()) {
                aitemstack[i] = new ItemStack(itemstack.getItem().getRecipeRemainder());
            }
        }

        return aitemstack;
    }
}
