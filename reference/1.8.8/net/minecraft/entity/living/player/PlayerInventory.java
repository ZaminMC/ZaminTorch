package net.minecraft.entity.living.player;

import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;

public class PlayerInventory implements Inventory {
    public ItemStack[] items = new ItemStack[36];
    public ItemStack[] armor = new ItemStack[4];
    public int selectedSlot;
    public PlayerEntity player;
    private ItemStack cursorItem;
    public boolean dirty;

    public PlayerInventory(PlayerEntity player) {
        this.player = player;
    }

    public ItemStack getSelectedItem() {
        return this.selectedSlot < 9 && this.selectedSlot >= 0 ? this.items[this.selectedSlot] : null;
    }

    public static int getHotbarSize() {
        return 9;
    }

    private int getSlot(Item item) {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null && this.items[i].getItem() == item) {
                return i;
            }
        }

        return -1;
    }

    private int getSlot(Item item, int metadata) {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null && this.items[i].getItem() == item && this.items[i].getMetadata() == metadata) {
                return i;
            }
        }

        return -1;
    }

    private int getSlotWithSpace(ItemStack item) {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null
                && this.items[i].getItem() == item.getItem()
                && this.items[i].isStackable()
                && this.items[i].size < this.items[i].getMaxSize()
                && this.items[i].size < this.getMaxStackSize()
                && (!this.items[i].hasCustomData() || this.items[i].getMetadata() == item.getMetadata())
                && ItemStack.matchesNbt(this.items[i], item)) {
                return i;
            }
        }

        return -1;
    }

    public int getEmptySlot() {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] == null) {
                return i;
            }
        }

        return -1;
    }

    public void selectSlot(Item item, int metadata, boolean damageable, boolean creativeMode) {
        ItemStack itemstack = this.getSelectedItem();
        int i = damageable ? this.getSlot(item, metadata) : this.getSlot(item);
        if (i >= 0 && i < 9) {
            this.selectedSlot = i;
        } else if (creativeMode && item != null) {
            int j = this.getEmptySlot();
            if (j >= 0 && j < 9) {
                this.selectedSlot = j;
            }

            if (itemstack == null || !itemstack.isEnchantable() || this.getSlot(itemstack.getItem(), itemstack.getDamage()) != this.selectedSlot) {
                int k = this.getSlot(item, metadata);
                int l;
                if (k >= 0) {
                    l = this.items[k].size;
                    this.items[k] = this.items[this.selectedSlot];
                } else {
                    l = 1;
                }

                this.items[this.selectedSlot] = new ItemStack(item, l, metadata);
            }
        }
    }

    public void scrollInHotbar(int amount) {
        if (amount > 0) {
            amount = 1;
        }

        if (amount < 0) {
            amount = -1;
        }

        this.selectedSlot -= amount;

        while (this.selectedSlot < 0) {
            this.selectedSlot += 9;
        }

        while (this.selectedSlot >= 9) {
            this.selectedSlot -= 9;
        }
    }

    public int removeAll(Item item, int metadata, int maxAmount, NbtCompound nbt) {
        int i = 0;

        for (int j = 0; j < this.items.length; j++) {
            ItemStack itemstack = this.items[j];
            if (itemstack != null
                && (item == null || itemstack.getItem() == item)
                && (metadata <= -1 || itemstack.getMetadata() == metadata)
                && (nbt == null || NbtUtils.matches(nbt, itemstack.getNbt(), true))) {
                int k = maxAmount <= 0 ? itemstack.size : Math.min(maxAmount - i, itemstack.size);
                i += k;
                if (maxAmount != 0) {
                    this.items[j].size -= k;
                    if (this.items[j].size == 0) {
                        this.items[j] = null;
                    }

                    if (maxAmount > 0 && i >= maxAmount) {
                        return i;
                    }
                }
            }
        }

        for (int l = 0; l < this.armor.length; l++) {
            ItemStack itemstack1 = this.armor[l];
            if (itemstack1 != null
                && (item == null || itemstack1.getItem() == item)
                && (metadata <= -1 || itemstack1.getMetadata() == metadata)
                && (nbt == null || NbtUtils.matches(nbt, itemstack1.getNbt(), false))) {
                int j1 = maxAmount <= 0 ? itemstack1.size : Math.min(maxAmount - i, itemstack1.size);
                i += j1;
                if (maxAmount != 0) {
                    this.armor[l].size -= j1;
                    if (this.armor[l].size == 0) {
                        this.armor[l] = null;
                    }

                    if (maxAmount > 0 && i >= maxAmount) {
                        return i;
                    }
                }
            }
        }

        if (this.cursorItem != null) {
            if (item != null && this.cursorItem.getItem() != item) {
                return i;
            }

            if (metadata > -1 && this.cursorItem.getMetadata() != metadata) {
                return i;
            }

            if (nbt != null && !NbtUtils.matches(nbt, this.cursorItem.getNbt(), false)) {
                return i;
            }

            int i1 = maxAmount <= 0 ? this.cursorItem.size : Math.min(maxAmount - i, this.cursorItem.size);
            i += i1;
            if (maxAmount != 0) {
                this.cursorItem.size -= i1;
                if (this.cursorItem.size == 0) {
                    this.cursorItem = null;
                }

                if (maxAmount > 0 && i >= maxAmount) {
                    return i;
                }
            }
        }

        return i;
    }

    private int addItemStack(ItemStack item) {
        Item itemx = item.getItem();
        int i = item.size;
        int j = this.getSlotWithSpace(item);
        if (j < 0) {
            j = this.getEmptySlot();
        }

        if (j < 0) {
            return i;
        }

        if (this.items[j] == null) {
            this.items[j] = new ItemStack(itemx, 0, item.getMetadata());
            if (item.hasNbt()) {
                this.items[j].setNbt((NbtCompound)item.getNbt().copy());
            }
        }

        int k = i;
        if (k > this.items[j].getMaxSize() - this.items[j].size) {
            k = this.items[j].getMaxSize() - this.items[j].size;
        }

        if (k > this.getMaxStackSize() - this.items[j].size) {
            k = this.getMaxStackSize() - this.items[j].size;
        }

        if (k == 0) {
            return i;
        }

        i -= k;
        this.items[j].size += k;
        this.items[j].popAnimationTime = 5;
        return i;
    }

    public void tick() {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                this.items[i].tick(this.player.world, this.player, i, this.selectedSlot == i);
            }
        }
    }

    public boolean removeOne(Item item) {
        int i = this.getSlot(item);
        if (i < 0) {
            return false;
        }

        if (--this.items[i].size <= 0) {
            this.items[i] = null;
        }

        return true;
    }

    public boolean contains(Item item) {
        int i = this.getSlot(item);
        return i >= 0;
    }

    public boolean addItem(ItemStack item) {
        if (item != null && item.size != 0 && item.getItem() != null) {
            try {
                if (item.isDamaged()) {
                    int j = this.getEmptySlot();
                    if (j >= 0) {
                        this.items[j] = ItemStack.copyOf(item);
                        this.items[j].popAnimationTime = 5;
                        item.size = 0;
                        return true;
                    } else if (this.player.abilities.creativeMode) {
                        item.size = 0;
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    int i;
                    do {
                        i = item.size;
                        item.size = this.addItemStack(item);
                    } while (item.size > 0 && item.size < i);

                    if (item.size == i && this.player.abilities.creativeMode) {
                        item.size = 0;
                        return true;
                    } else {
                        return item.size < i;
                    }
                }
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Adding item to inventory");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being added");
                crashreportcategory.add("Item ID", Item.getId(item.getItem()));
                crashreportcategory.add("Item data", item.getMetadata());
                crashreportcategory.add("Item name", new Callable<String>() {
                    public String call() throws Exception {
                        return item.getHoverName();
                    }
                });
                throw new CrashException(crashreport);
            }
        } else {
            return false;
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack[] aitemstack = this.items;
        if (slot >= this.items.length) {
            aitemstack = this.armor;
            slot -= this.items.length;
        }

        if (aitemstack[slot] != null) {
            if (aitemstack[slot].size <= amount) {
                ItemStack itemstack1 = aitemstack[slot];
                aitemstack[slot] = null;
                return itemstack1;
            }

            ItemStack itemstack = aitemstack[slot].split(amount);
            if (aitemstack[slot].size == 0) {
                aitemstack[slot] = null;
            }

            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        ItemStack[] aitemstack = this.items;
        if (slot >= this.items.length) {
            aitemstack = this.armor;
            slot -= this.items.length;
        }

        if (aitemstack[slot] != null) {
            ItemStack itemstack = aitemstack[slot];
            aitemstack[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        ItemStack[] aitemstack = this.items;
        if (slot >= aitemstack.length) {
            slot -= aitemstack.length;
            aitemstack = this.armor;
        }

        aitemstack[slot] = item;
    }

    public float getMiningSpeed(Block block) {
        float f = 1.0F;
        if (this.items[this.selectedSlot] != null) {
            f *= this.items[this.selectedSlot].getMiningSpeed(block);
        }

        return f;
    }

    public NbtList writeNbt(NbtList list) {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                NbtCompound nbtcompound = new NbtCompound();
                nbtcompound.putByte("Slot", (byte)i);
                this.items[i].writeNbt(nbtcompound);
                list.addElement(nbtcompound);
            }
        }

        for (int j = 0; j < this.armor.length; j++) {
            if (this.armor[j] != null) {
                NbtCompound nbtcompound1 = new NbtCompound();
                nbtcompound1.putByte("Slot", (byte)(j + 100));
                this.armor[j].writeNbt(nbtcompound1);
                list.addElement(nbtcompound1);
            }
        }

        return list;
    }

    public void readNbt(NbtList list) {
        this.items = new ItemStack[36];
        this.armor = new ItemStack[4];

        for (int i = 0; i < list.size(); i++) {
            NbtCompound nbtcompound = list.getCompound(i);
            int j = nbtcompound.getByte("Slot") & 255;
            ItemStack itemstack = ItemStack.fromNbt(nbtcompound);
            if (itemstack != null) {
                if (j >= 0 && j < this.items.length) {
                    this.items[j] = itemstack;
                }

                if (j >= 100 && j < this.armor.length + 100) {
                    this.armor[j - 100] = itemstack;
                }
            }
        }
    }

    @Override
    public int getSize() {
        return this.items.length + 4;
    }

    @Override
    public ItemStack getItem(int slot) {
        ItemStack[] aitemstack = this.items;
        if (slot >= aitemstack.length) {
            slot -= aitemstack.length;
            aitemstack = this.armor;
        }

        return aitemstack[slot];
    }

    @Override
    public String getName() {
        return "container.inventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public Text getDisplayName() {
        return this.hasCustomName() ? new LiteralText(this.getName()) : new TranslatableText(this.getName());
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    public boolean canMineBlock(Block block) {
        if (block.getMaterial().isToolNotRequired()) {
            return true;
        }

        ItemStack itemstack = this.getItem(this.selectedSlot);
        return itemstack != null && itemstack.canMineBlock(block);
    }

    public ItemStack getArmor(int slot) {
        return this.armor[slot];
    }

    public int getArmorProtection() {
        int i = 0;

        for (int j = 0; j < this.armor.length; j++) {
            if (this.armor[j] != null && this.armor[j].getItem() instanceof ArmorItem) {
                int k = ((ArmorItem)this.armor[j].getItem()).protection;
                i += k;
            }
        }

        return i;
    }

    public void damageArmor(float armor) {
        armor /= 4.0F;
        if (armor < 1.0F) {
            armor = 1.0F;
        }

        for (int i = 0; i < this.armor.length; i++) {
            if (this.armor[i] != null && this.armor[i].getItem() instanceof ArmorItem) {
                this.armor[i].takeDamageAndBreak((int)armor, this.player);
                if (this.armor[i].size == 0) {
                    this.armor[i] = null;
                }
            }
        }
    }

    public void dropAll() {
        for (int i = 0; i < this.items.length; i++) {
            if (this.items[i] != null) {
                this.player.dropItem(this.items[i], true, false);
                this.items[i] = null;
            }
        }

        for (int j = 0; j < this.armor.length; j++) {
            if (this.armor[j] != null) {
                this.player.dropItem(this.armor[j], true, false);
                this.armor[j] = null;
            }
        }
    }

    @Override
    public void markDirty() {
        this.dirty = true;
    }

    public void setCursorItem(ItemStack item) {
        this.cursorItem = item;
    }

    public ItemStack getCursorItem() {
        return this.cursorItem;
    }

    @Override
    public boolean isValid(PlayerEntity player) {
        return !this.player.removed && !(player.squaredDistanceTo(this.player) > 64.0);
    }

    public boolean contains(ItemStack item) {
        for (int i = 0; i < this.armor.length; i++) {
            if (this.armor[i] != null && this.armor[i].matchesItem(item)) {
                return true;
            }
        }

        for (int j = 0; j < this.items.length; j++) {
            if (this.items[j] != null && this.items[j].matchesItem(item)) {
                return true;
            }
        }

        return false;
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

    public void copy(PlayerInventory inventory) {
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = ItemStack.copyOf(inventory.items[i]);
        }

        for (int j = 0; j < this.armor.length; j++) {
            this.armor[j] = ItemStack.copyOf(inventory.armor[j]);
        }

        this.selectedSlot = inventory.selectedSlot;
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
        for (int i = 0; i < this.items.length; i++) {
            this.items[i] = null;
        }

        for (int j = 0; j < this.armor.length; j++) {
            this.armor[j] = null;
        }
    }
}
