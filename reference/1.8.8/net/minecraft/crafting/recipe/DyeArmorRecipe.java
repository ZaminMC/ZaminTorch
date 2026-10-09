package net.minecraft.crafting.recipe;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class DyeArmorRecipe implements Recipe {
    @Override
    public boolean matches(CraftingInventory inventory, World world) {
        ItemStack itemstack = null;
        List<ItemStack> list = Lists.newArrayList();

        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack itemstack1 = inventory.getItem(i);
            if (itemstack1 != null) {
                if (itemstack1.getItem() instanceof ArmorItem) {
                    ArmorItem armoritem = (ArmorItem)itemstack1.getItem();
                    if (armoritem.getTier() != ArmorItem.Tier.CLOTH || itemstack != null) {
                        return false;
                    }

                    itemstack = itemstack1;
                } else {
                    if (itemstack1.getItem() != Items.DYE) {
                        return false;
                    }

                    list.add(itemstack1);
                }
            }
        }

        return itemstack != null && !list.isEmpty();
    }

    @Override
    public ItemStack getResult(CraftingInventory inventory) {
        ItemStack itemstack = null;
        int[] aint = new int[3];
        int i = 0;
        int j = 0;
        ArmorItem armoritem = null;

        for (int k = 0; k < inventory.getSize(); k++) {
            ItemStack itemstack1 = inventory.getItem(k);
            if (itemstack1 != null) {
                if (itemstack1.getItem() instanceof ArmorItem) {
                    armoritem = (ArmorItem)itemstack1.getItem();
                    if (armoritem.getTier() != ArmorItem.Tier.CLOTH || itemstack != null) {
                        return null;
                    }

                    itemstack = itemstack1.copy();
                    itemstack.size = 1;
                    if (armoritem.hasColor(itemstack1)) {
                        int l = armoritem.getColor(itemstack);
                        float f = (l >> 16 & 0xFF) / 255.0F;
                        float f1 = (l >> 8 & 0xFF) / 255.0F;
                        float f2 = (l & 0xFF) / 255.0F;
                        i = (int)(i + Math.max(f, Math.max(f1, f2)) * 255.0F);
                        aint[0] = (int)(aint[0] + f * 255.0F);
                        aint[1] = (int)(aint[1] + f1 * 255.0F);
                        aint[2] = (int)(aint[2] + f2 * 255.0F);
                        j++;
                    }
                } else {
                    if (itemstack1.getItem() != Items.DYE) {
                        return null;
                    }

                    float[] afloat = SheepEntity.getColorRgb(DyeColor.byMetadata(itemstack1.getMetadata()));
                    int l1 = (int)(afloat[0] * 255.0F);
                    int i2 = (int)(afloat[1] * 255.0F);
                    int j2 = (int)(afloat[2] * 255.0F);
                    i += Math.max(l1, Math.max(i2, j2));
                    aint[0] += l1;
                    aint[1] += i2;
                    aint[2] += j2;
                    j++;
                }
            }
        }

        if (armoritem == null) {
            return null;
        }

        int i1 = aint[0] / j;
        int j1 = aint[1] / j;
        int k1 = aint[2] / j;
        float f3 = (float)i / j;
        float f4 = Math.max(i1, Math.max(j1, k1));
        i1 = (int)(i1 * f3 / f4);
        j1 = (int)(j1 * f3 / f4);
        k1 = (int)(k1 * f3 / f4);
        int k2 = i1;
        k2 = (k2 << 8) + j1;
        k2 = (k2 << 8) + k1;
        armoritem.setColor(itemstack, k2);
        return itemstack;
    }

    @Override
    public int size() {
        return 10;
    }

    @Override
    public ItemStack getResult() {
        return null;
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
