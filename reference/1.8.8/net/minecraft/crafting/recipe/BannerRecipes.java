package net.minecraft.crafting.recipe;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.crafting.CraftingManager;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.world.World;

public class BannerRecipes {
    void register(CraftingManager manager) {
        for (DyeColor dyecolor : DyeColor.values()) {
            manager.registerShaped(
                new ItemStack(Items.BANNER, 1, dyecolor.getMetadata()),
                "###",
                "###",
                " | ",
                '#',
                new ItemStack(Blocks.WOOL, 1, dyecolor.getId()),
                '|',
                Items.STICK
            );
        }

        manager.register(new BannerRecipes.DuplicatePattern());
        manager.register(new BannerRecipes.AddPattern());
    }

    static class AddPattern implements Recipe {
        private AddPattern() {
        }

        @Override
        public boolean matches(CraftingInventory inventory, World world) {
            boolean flag = false;

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack = inventory.getItem(i);
                if (itemstack != null && itemstack.getItem() == Items.BANNER) {
                    if (flag) {
                        return false;
                    }

                    if (BannerBlockEntity.getPatternCount(itemstack) >= 6) {
                        return false;
                    }

                    flag = true;
                }
            }

            return flag && this.getPattern(inventory) != null;
        }

        @Override
        public ItemStack getResult(CraftingInventory inventory) {
            ItemStack itemstack = null;

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack1 = inventory.getItem(i);
                if (itemstack1 != null && itemstack1.getItem() == Items.BANNER) {
                    itemstack = itemstack1.copy();
                    itemstack.size = 1;
                    break;
                }
            }

            BannerBlockEntity.Pattern bannerblockentity$pattern = this.getPattern(inventory);
            if (bannerblockentity$pattern != null) {
                int k = 0;

                for (int j = 0; j < inventory.getSize(); j++) {
                    ItemStack itemstack2 = inventory.getItem(j);
                    if (itemstack2 != null && itemstack2.getItem() == Items.DYE) {
                        k = itemstack2.getMetadata();
                        break;
                    }
                }

                NbtCompound nbtcompound1 = itemstack.getNbt("BlockEntityTag", true);
                NbtList nbtlist = null;
                if (nbtcompound1.contains("Patterns", 9)) {
                    nbtlist = nbtcompound1.getList("Patterns", 10);
                } else {
                    nbtlist = new NbtList();
                    nbtcompound1.put("Patterns", nbtlist);
                }

                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putString("Pattern", bannerblockentity$pattern.getHash());
                nbtcompound.putInt("Color", k);
                nbtlist.addElement(nbtcompound);
            }

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

        private BannerBlockEntity.Pattern getPattern(CraftingInventory inventory) {
            for (BannerBlockEntity.Pattern bannerblockentity$pattern : BannerBlockEntity.Pattern.values()) {
                if (bannerblockentity$pattern.hasPattern()) {
                    boolean flag = true;
                    if (bannerblockentity$pattern.hasItem()) {
                        boolean flag1 = false;
                        boolean flag2 = false;

                        for (int i = 0; i < inventory.getSize() && flag; i++) {
                            ItemStack itemstack = inventory.getItem(i);
                            if (itemstack != null && itemstack.getItem() != Items.BANNER) {
                                if (itemstack.getItem() == Items.DYE) {
                                    if (flag2) {
                                        flag = false;
                                        break;
                                    }

                                    flag2 = true;
                                } else {
                                    if (flag1 || !itemstack.matchesItem(bannerblockentity$pattern.getItem())) {
                                        flag = false;
                                        break;
                                    }

                                    flag1 = true;
                                }
                            }
                        }

                        if (!flag1) {
                            flag = false;
                        }
                    } else if (inventory.getSize() == bannerblockentity$pattern.getPatterns().length * bannerblockentity$pattern.getPatterns()[0].length()) {
                        int j = -1;

                        for (int k = 0; k < inventory.getSize() && flag; k++) {
                            int l = k / 3;
                            int i1 = k % 3;
                            ItemStack itemstack1 = inventory.getItem(k);
                            if (itemstack1 != null && itemstack1.getItem() != Items.BANNER) {
                                if (itemstack1.getItem() != Items.DYE) {
                                    flag = false;
                                    break;
                                }

                                if (j != -1 && j != itemstack1.getMetadata()) {
                                    flag = false;
                                    break;
                                }

                                if (bannerblockentity$pattern.getPatterns()[l].charAt(i1) == ' ') {
                                    flag = false;
                                    break;
                                }

                                j = itemstack1.getMetadata();
                            } else if (bannerblockentity$pattern.getPatterns()[l].charAt(i1) != ' ') {
                                flag = false;
                                break;
                            }
                        }
                    } else {
                        flag = false;
                    }

                    if (flag) {
                        return bannerblockentity$pattern;
                    }
                }
            }

            return null;
        }
    }

    static class DuplicatePattern implements Recipe {
        private DuplicatePattern() {
        }

        @Override
        public boolean matches(CraftingInventory inventory, World world) {
            ItemStack itemstack = null;
            ItemStack itemstack1 = null;

            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack2 = inventory.getItem(i);
                if (itemstack2 != null) {
                    if (itemstack2.getItem() != Items.BANNER) {
                        return false;
                    }

                    if (itemstack != null && itemstack1 != null) {
                        return false;
                    }

                    int j = BannerBlockEntity.getBaseColor(itemstack2);
                    boolean flag = BannerBlockEntity.getPatternCount(itemstack2) > 0;
                    if (itemstack != null) {
                        if (flag) {
                            return false;
                        }

                        if (j != BannerBlockEntity.getBaseColor(itemstack)) {
                            return false;
                        }

                        itemstack1 = itemstack2;
                    } else if (itemstack1 != null) {
                        if (!flag) {
                            return false;
                        }

                        if (j != BannerBlockEntity.getBaseColor(itemstack1)) {
                            return false;
                        }

                        itemstack = itemstack2;
                    } else if (flag) {
                        itemstack = itemstack2;
                    } else {
                        itemstack1 = itemstack2;
                    }
                }
            }

            return itemstack != null && itemstack1 != null;
        }

        @Override
        public ItemStack getResult(CraftingInventory inventory) {
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack itemstack = inventory.getItem(i);
                if (itemstack != null && BannerBlockEntity.getPatternCount(itemstack) > 0) {
                    ItemStack itemstack1 = itemstack.copy();
                    itemstack1.size = 1;
                    return itemstack1;
                }
            }

            return null;
        }

        @Override
        public int size() {
            return 2;
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
                if (itemstack != null) {
                    if (itemstack.getItem().hasRecipeRemainder()) {
                        aitemstack[i] = new ItemStack(itemstack.getItem().getRecipeRemainder());
                    } else if (itemstack.hasNbt() && BannerBlockEntity.getPatternCount(itemstack) > 0) {
                        aitemstack[i] = itemstack.copy();
                        aitemstack[i].size = 1;
                    }
                }
            }

            return aitemstack;
        }
    }
}
