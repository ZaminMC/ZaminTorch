package net.minecraft.block.entity;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.BrewingStandBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.effect.PotionHelper;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.inventory.menu.BrewingStandMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Tickable;
import net.minecraft.util.math.Direction;

public class BrewingStandBlockEntity extends InventoryBlockEntity implements Tickable, SidedInventory {
    private static final int[] INGREDIENT_SLOTS = new int[]{3};
    private static final int[] POTION_SLOTS = new int[]{0, 1, 2};
    /**
     * Brewing stand inventory slots. 0-2 are potion slots, 3 is the ingredient slot.
     */
    private ItemStack[] inventory = new ItemStack[4];
    private int timer;
    /**
     * For each potion slot, whether that slot has a potion in it.
     */
    private boolean[] hasPotion;
    private Item ingredient;
    private String customName;

    @Override
    public String getName() {
        return this.hasCustomName() ? this.customName : "container.brewing";
    }

    @Override
    public boolean hasCustomName() {
        return this.customName != null && this.customName.length() > 0;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    @Override
    public int getSize() {
        return this.inventory.length;
    }

    @Override
    public void tick() {
        if (this.timer > 0) {
            this.timer--;
            if (this.timer == 0) {
                this.brew();
                this.markDirty();
            } else if (!this.canBrew()) {
                this.timer = 0;
                this.markDirty();
            } else if (this.ingredient != this.inventory[3].getItem()) {
                this.timer = 0;
                this.markDirty();
            }
        } else if (this.canBrew()) {
            this.timer = 400;
            this.ingredient = this.inventory[3].getItem();
        }

        if (!this.world.isClient) {
            boolean[] aboolean = this.findHasPotion();
            if (!Arrays.equals(aboolean, this.hasPotion)) {
                this.hasPotion = aboolean;
                BlockState blockstate = this.world.getBlockState(this.getPos());
                if (!(blockstate.getBlock() instanceof BrewingStandBlock)) {
                    return;
                }

                for (int i = 0; i < BrewingStandBlock.HAS_BOTTLE.length; i++) {
                    blockstate = blockstate.set(BrewingStandBlock.HAS_BOTTLE[i], aboolean[i]);
                }

                this.world.setBlockState(this.pos, blockstate, 2);
            }
        }
    }

    private boolean canBrew() {
        if (this.inventory[3] != null && this.inventory[3].size > 0) {
            ItemStack itemstack = this.inventory[3];
            if (!itemstack.getItem().isPotionIngredient(itemstack)) {
                return false;
            }

            boolean flag = false;

            for (int i = 0; i < 3; i++) {
                if (this.inventory[i] != null && this.inventory[i].getItem() == Items.POTION) {
                    int j = this.inventory[i].getMetadata();
                    int k = this.applyIngredient(j, itemstack);
                    if (!PotionItem.isSplashPotion(j) && PotionItem.isSplashPotion(k)) {
                        flag = true;
                        break;
                    }

                    List<StatusEffectInstance> list = Items.POTION.getPotionEffects(j);
                    List<StatusEffectInstance> list1 = Items.POTION.getPotionEffects(k);
                    if ((j <= 0 || list != list1) && (list == null || !list.equals(list1) && list1 != null) && j != k) {
                        flag = true;
                        break;
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    private void brew() {
        if (this.canBrew()) {
            ItemStack itemstack = this.inventory[3];

            for (int i = 0; i < 3; i++) {
                if (this.inventory[i] != null && this.inventory[i].getItem() == Items.POTION) {
                    int j = this.inventory[i].getMetadata();
                    int k = this.applyIngredient(j, itemstack);
                    List<StatusEffectInstance> list = Items.POTION.getPotionEffects(j);
                    List<StatusEffectInstance> list1 = Items.POTION.getPotionEffects(k);
                    if (j > 0 && list == list1 || list != null && (list.equals(list1) || list1 == null)) {
                        if (!PotionItem.isSplashPotion(j) && PotionItem.isSplashPotion(k)) {
                            this.inventory[i].setDamage(k);
                        }
                    } else if (j != k) {
                        this.inventory[i].setDamage(k);
                    }
                }
            }

            if (itemstack.getItem().hasRecipeRemainder()) {
                this.inventory[3] = new ItemStack(itemstack.getItem().getRecipeRemainder());
            } else {
                this.inventory[3].size--;
                if (this.inventory[3].size <= 0) {
                    this.inventory[3] = null;
                }
            }
        }
    }

    private int applyIngredient(int metadata, ItemStack ingredient) {
        if (ingredient == null) {
            return metadata;
        } else {
            return ingredient.getItem().isPotionIngredient(ingredient)
                ? PotionHelper.applyIngredient(metadata, ingredient.getItem().asPotionIngredient(ingredient))
                : metadata;
        }
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

        this.timer = nbt.getShort("BrewTime");
        if (nbt.contains("CustomName", 8)) {
            this.customName = nbt.getString("CustomName");
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putShort("BrewTime", (short)this.timer);
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
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < this.inventory.length ? this.inventory[slot] : null;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot >= 0 && slot < this.inventory.length) {
            ItemStack itemstack = this.inventory[slot];
            this.inventory[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (slot >= 0 && slot < this.inventory.length) {
            ItemStack itemstack = this.inventory[slot];
            this.inventory[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        if (slot >= 0 && slot < this.inventory.length) {
            this.inventory[slot] = item;
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
    public void onOpen(PlayerEntity player) {
    }

    @Override
    public void onClose(PlayerEntity player) {
    }

    @Override
    public boolean isItemAllowed(int slot, ItemStack item) {
        return slot == 3 ? item.getItem().isPotionIngredient(item) : item.getItem() == Items.POTION || item.getItem() == Items.GLASS_BOTTLE;
    }

    public boolean[] findHasPotion() {
        boolean[] aboolean = new boolean[3];

        for (int i = 0; i < 3; i++) {
            if (this.inventory[i] != null) {
                aboolean[i] = true;
            }
        }

        return aboolean;
    }

    @Override
    public int[] getSlots(Direction side) {
        return side == Direction.UP ? INGREDIENT_SLOTS : POTION_SLOTS;
    }

    @Override
    public boolean canPushItem(int slot, ItemStack item, Direction side) {
        return this.isItemAllowed(slot, item);
    }

    @Override
    public boolean canPullItem(int slot, ItemStack item, Direction side) {
        return true;
    }

    @Override
    public String getMenuType() {
        return "minecraft:brewing_stand";
    }

    @Override
    public InventoryMenu createMenu(PlayerInventory playerInventory, PlayerEntity player) {
        return new BrewingStandMenu(playerInventory, this);
    }

    @Override
    public int getData(int id) {
        switch (id) {
            case 0:
                return this.timer;
            default:
                return 0;
        }
    }

    @Override
    public void setData(int id, int value) {
        switch (id) {
            case 0:
                this.timer = value;
        }
    }

    @Override
    public int getDataSize() {
        return 1;
    }

    @Override
    public void clear() {
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
    }
}
