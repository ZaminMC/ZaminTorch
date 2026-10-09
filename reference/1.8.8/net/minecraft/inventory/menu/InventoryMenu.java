package net.minecraft.inventory.menu;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

public abstract class InventoryMenu {
    public List<ItemStack> items = Lists.newArrayList();
    public List<InventorySlot> slots = Lists.newArrayList();
    public int networkId;
    private short nextInteractionId;
    private int clickDragMode = -1;
    private int clickDragStage;
    private final Set<InventorySlot> clickDragSlots = Sets.newHashSet();
    protected List<InventoryMenuListener> listeners = Lists.newArrayList();
    private Set<PlayerEntity> restrictedPlayers = Sets.newHashSet();

    protected InventorySlot addSlot(InventorySlot slot) {
        slot.index = this.slots.size();
        this.slots.add(slot);
        this.items.add(null);
        return slot;
    }

    public void addListener(InventoryMenuListener listener) {
        if (this.listeners.contains(listener)) {
            throw new IllegalArgumentException("Listener already listening");
        }

        this.listeners.add(listener);
        listener.onMenuChanged(this, this.getItems());
        this.updateListeners();
    }

    public void removeListener(InventoryMenuListener listener) {
        this.listeners.remove(listener);
    }

    public List<ItemStack> getItems() {
        List<ItemStack> list = Lists.newArrayList();

        for (int i = 0; i < this.slots.size(); i++) {
            list.add(this.slots.get(i).getItem());
        }

        return list;
    }

    public void updateListeners() {
        for (int i = 0; i < this.slots.size(); i++) {
            ItemStack itemstack = this.slots.get(i).getItem();
            ItemStack itemstack1 = this.items.get(i);
            if (!ItemStack.matches(itemstack1, itemstack)) {
                itemstack1 = itemstack == null ? null : itemstack.copy();
                this.items.set(i, itemstack1);

                for (int j = 0; j < this.listeners.size(); j++) {
                    this.listeners.get(j).onSlotChanged(this, i, itemstack1);
                }
            }
        }
    }

    public boolean onButtonClick(PlayerEntity player, int id) {
        return false;
    }

    public InventorySlot getSlot(Inventory inventory, int slot) {
        for (int i = 0; i < this.slots.size(); i++) {
            InventorySlot inventoryslot = this.slots.get(i);
            if (inventoryslot.equals(inventory, slot)) {
                return inventoryslot;
            }
        }

        return null;
    }

    public InventorySlot getSlot(int slot) {
        return this.slots.get(slot);
    }

    public ItemStack quickMoveItem(PlayerEntity player, int slot) {
        InventorySlot inventoryslot = this.slots.get(slot);
        return inventoryslot != null ? inventoryslot.getItem() : null;
    }

    /**
     * Handle an interaction with an inventory slot.
     * 
     * <p>
     * The following action types are in use:
     * <br>0: pickup      - move a stack between the inventory and the cursor
     * <br>1: quick move  - move a stack between the inventory and the hotbar
     * <br>2: swap        - swap an inventory stack and a hotbar stack
     * <br>3: clone       - use the pick item key on an inventory slot
     * <br>4: throw       - use the drop item key on an inventory slot
     * <br>5: quick craft - click-and-drag a stack over an inventory slot
     * <br>6: pickup all  - double click with a stack on an inventory slot
     */
    public ItemStack onClickSlot(int id, int clickData, int action, PlayerEntity player) {
        ItemStack itemstack = null;
        PlayerInventory playerinventory = player.inventory;
        if (action == 5) {
            int i = this.clickDragStage;
            this.clickDragStage = unpackClickDragStage(clickData);
            if ((i != 1 || this.clickDragStage != 2) && i != this.clickDragStage) {
                this.dropClickDragging();
            } else if (playerinventory.getCursorItem() == null) {
                this.dropClickDragging();
            } else if (this.clickDragStage == 0) {
                this.clickDragMode = unpackClickDragMode(clickData);
                if (isValidClickDragMode(this.clickDragMode, player)) {
                    this.clickDragStage = 1;
                    this.clickDragSlots.clear();
                } else {
                    this.dropClickDragging();
                }
            } else if (this.clickDragStage == 1) {
                InventorySlot inventoryslot = this.slots.get(id);
                if (inventoryslot != null
                    && canClickDragInto(inventoryslot, playerinventory.getCursorItem(), true)
                    && inventoryslot.isItemAllowed(playerinventory.getCursorItem())
                    && playerinventory.getCursorItem().size > this.clickDragSlots.size()
                    && this.canClickDragInto(inventoryslot)) {
                    this.clickDragSlots.add(inventoryslot);
                }
            } else if (this.clickDragStage == 2) {
                if (!this.clickDragSlots.isEmpty()) {
                    ItemStack itemstack3 = playerinventory.getCursorItem().copy();
                    int j = playerinventory.getCursorItem().size;

                    for (InventorySlot inventoryslot1 : this.clickDragSlots) {
                        if (inventoryslot1 != null
                            && canClickDragInto(inventoryslot1, playerinventory.getCursorItem(), true)
                            && inventoryslot1.isItemAllowed(playerinventory.getCursorItem())
                            && playerinventory.getCursorItem().size >= this.clickDragSlots.size()
                            && this.canClickDragInto(inventoryslot1)) {
                            ItemStack itemstack1 = itemstack3.copy();
                            int k = inventoryslot1.hasItem() ? inventoryslot1.getItem().size : 0;
                            updateClickDragStackSize(this.clickDragSlots, this.clickDragMode, itemstack1, k);
                            if (itemstack1.size > itemstack1.getMaxSize()) {
                                itemstack1.size = itemstack1.getMaxSize();
                            }

                            if (itemstack1.size > inventoryslot1.getMaxStackSize(itemstack1)) {
                                itemstack1.size = inventoryslot1.getMaxStackSize(itemstack1);
                            }

                            j -= itemstack1.size - k;
                            inventoryslot1.setItem(itemstack1);
                        }
                    }

                    itemstack3.size = j;
                    if (itemstack3.size <= 0) {
                        itemstack3 = null;
                    }

                    playerinventory.setCursorItem(itemstack3);
                }

                this.dropClickDragging();
            } else {
                this.dropClickDragging();
            }
        } else if (this.clickDragStage != 0) {
            this.dropClickDragging();
        } else if ((action == 0 || action == 1) && (clickData == 0 || clickData == 1)) {
            if (id == -999) {
                if (playerinventory.getCursorItem() != null) {
                    if (clickData == 0) {
                        player.dropItem(playerinventory.getCursorItem(), true);
                        playerinventory.setCursorItem(null);
                    }

                    if (clickData == 1) {
                        player.dropItem(playerinventory.getCursorItem().split(1), true);
                        if (playerinventory.getCursorItem().size == 0) {
                            playerinventory.setCursorItem(null);
                        }
                    }
                }
            } else if (action == 1) {
                if (id < 0) {
                    return null;
                }

                InventorySlot inventoryslot6 = this.slots.get(id);
                if (inventoryslot6 != null && inventoryslot6.canPickUp(player)) {
                    ItemStack itemstack8 = this.quickMoveItem(player, id);
                    if (itemstack8 != null) {
                        Item item = itemstack8.getItem();
                        itemstack = itemstack8.copy();
                        if (inventoryslot6.getItem() != null && inventoryslot6.getItem().getItem() == item) {
                            this.clickSlot(id, clickData, true, player);
                        }
                    }
                }
            } else {
                if (id < 0) {
                    return null;
                }

                InventorySlot inventoryslot7 = this.slots.get(id);
                if (inventoryslot7 != null) {
                    ItemStack itemstack9 = inventoryslot7.getItem();
                    ItemStack itemstack10 = playerinventory.getCursorItem();
                    if (itemstack9 != null) {
                        itemstack = itemstack9.copy();
                    }

                    if (itemstack9 == null) {
                        if (itemstack10 != null && inventoryslot7.isItemAllowed(itemstack10)) {
                            int k2 = clickData == 0 ? itemstack10.size : 1;
                            if (k2 > inventoryslot7.getMaxStackSize(itemstack10)) {
                                k2 = inventoryslot7.getMaxStackSize(itemstack10);
                            }

                            if (itemstack10.size >= k2) {
                                inventoryslot7.setItem(itemstack10.split(k2));
                            }

                            if (itemstack10.size == 0) {
                                playerinventory.setCursorItem(null);
                            }
                        }
                    } else if (inventoryslot7.canPickUp(player)) {
                        if (itemstack10 == null) {
                            int j2 = clickData == 0 ? itemstack9.size : (itemstack9.size + 1) / 2;
                            ItemStack itemstack12 = inventoryslot7.removeItem(j2);
                            playerinventory.setCursorItem(itemstack12);
                            if (itemstack9.size == 0) {
                                inventoryslot7.setItem(null);
                            }

                            inventoryslot7.onItemRemoved(player, playerinventory.getCursorItem());
                        } else if (inventoryslot7.isItemAllowed(itemstack10)) {
                            if (itemstack9.getItem() == itemstack10.getItem()
                                && itemstack9.getMetadata() == itemstack10.getMetadata()
                                && ItemStack.matchesNbt(itemstack9, itemstack10)) {
                                int i2 = clickData == 0 ? itemstack10.size : 1;
                                if (i2 > inventoryslot7.getMaxStackSize(itemstack10) - itemstack9.size) {
                                    i2 = inventoryslot7.getMaxStackSize(itemstack10) - itemstack9.size;
                                }

                                if (i2 > itemstack10.getMaxSize() - itemstack9.size) {
                                    i2 = itemstack10.getMaxSize() - itemstack9.size;
                                }

                                itemstack10.split(i2);
                                if (itemstack10.size == 0) {
                                    playerinventory.setCursorItem(null);
                                }

                                itemstack9.size += i2;
                            } else if (itemstack10.size <= inventoryslot7.getMaxStackSize(itemstack10)) {
                                inventoryslot7.setItem(itemstack10);
                                playerinventory.setCursorItem(itemstack9);
                            }
                        } else if (itemstack9.getItem() == itemstack10.getItem()
                            && itemstack10.getMaxSize() > 1
                            && (!itemstack9.hasCustomData() || itemstack9.getMetadata() == itemstack10.getMetadata())
                            && ItemStack.matchesNbt(itemstack9, itemstack10)) {
                            int l1 = itemstack9.size;
                            if (l1 > 0 && l1 + itemstack10.size <= itemstack10.getMaxSize()) {
                                itemstack10.size += l1;
                                itemstack9 = inventoryslot7.removeItem(l1);
                                if (itemstack9.size == 0) {
                                    inventoryslot7.setItem(null);
                                }

                                inventoryslot7.onItemRemoved(player, playerinventory.getCursorItem());
                            }
                        }
                    }

                    inventoryslot7.markDirty();
                }
            }
        } else if (action == 2 && clickData >= 0 && clickData < 9) {
            InventorySlot inventoryslot5 = this.slots.get(id);
            if (inventoryslot5.canPickUp(player)) {
                ItemStack itemstack7 = playerinventory.getItem(clickData);
                boolean flag = itemstack7 == null || inventoryslot5.inventory == playerinventory && inventoryslot5.isItemAllowed(itemstack7);
                int k1 = -1;
                if (!flag) {
                    k1 = playerinventory.getEmptySlot();
                    flag |= k1 > -1;
                }

                if (inventoryslot5.hasItem() && flag) {
                    ItemStack itemstack11 = inventoryslot5.getItem();
                    playerinventory.setItem(clickData, itemstack11.copy());
                    if ((inventoryslot5.inventory != playerinventory || !inventoryslot5.isItemAllowed(itemstack7)) && itemstack7 != null) {
                        if (k1 > -1) {
                            playerinventory.addItem(itemstack7);
                            inventoryslot5.removeItem(itemstack11.size);
                            inventoryslot5.setItem(null);
                            inventoryslot5.onItemRemoved(player, itemstack11);
                        }
                    } else {
                        inventoryslot5.removeItem(itemstack11.size);
                        inventoryslot5.setItem(itemstack7);
                        inventoryslot5.onItemRemoved(player, itemstack11);
                    }
                } else if (!inventoryslot5.hasItem() && itemstack7 != null && inventoryslot5.isItemAllowed(itemstack7)) {
                    playerinventory.setItem(clickData, null);
                    inventoryslot5.setItem(itemstack7);
                }
            }
        } else if (action == 3 && player.abilities.creativeMode && playerinventory.getCursorItem() == null && id >= 0) {
            InventorySlot inventoryslot4 = this.slots.get(id);
            if (inventoryslot4 != null && inventoryslot4.hasItem()) {
                ItemStack itemstack6 = inventoryslot4.getItem().copy();
                itemstack6.size = itemstack6.getMaxSize();
                playerinventory.setCursorItem(itemstack6);
            }
        } else if (action == 4 && playerinventory.getCursorItem() == null && id >= 0) {
            InventorySlot inventoryslot3 = this.slots.get(id);
            if (inventoryslot3 != null && inventoryslot3.hasItem() && inventoryslot3.canPickUp(player)) {
                ItemStack itemstack5 = inventoryslot3.removeItem(clickData == 0 ? 1 : inventoryslot3.getItem().size);
                inventoryslot3.onItemRemoved(player, itemstack5);
                player.dropItem(itemstack5, true);
            }
        } else if (action == 6 && id >= 0) {
            InventorySlot inventoryslot2 = this.slots.get(id);
            ItemStack itemstack4 = playerinventory.getCursorItem();
            if (itemstack4 != null && (inventoryslot2 == null || !inventoryslot2.hasItem() || !inventoryslot2.canPickUp(player))) {
                int i1 = clickData == 0 ? 0 : this.slots.size() - 1;
                int j1 = clickData == 0 ? 1 : -1;

                for (int l2 = 0; l2 < 2; l2++) {
                    for (int i3 = i1; i3 >= 0 && i3 < this.slots.size() && itemstack4.size < itemstack4.getMaxSize(); i3 += j1) {
                        InventorySlot inventoryslot8 = this.slots.get(i3);
                        if (inventoryslot8.hasItem()
                            && canClickDragInto(inventoryslot8, itemstack4, true)
                            && inventoryslot8.canPickUp(player)
                            && this.canRemoveForPickupAll(itemstack4, inventoryslot8)
                            && (l2 != 0 || inventoryslot8.getItem().size != inventoryslot8.getItem().getMaxSize())) {
                            int l = Math.min(itemstack4.getMaxSize() - itemstack4.size, inventoryslot8.getItem().size);
                            ItemStack itemstack2 = inventoryslot8.removeItem(l);
                            itemstack4.size += l;
                            if (itemstack2.size <= 0) {
                                inventoryslot8.setItem(null);
                            }

                            inventoryslot8.onItemRemoved(player, itemstack2);
                        }
                    }
                }
            }

            this.updateListeners();
        }

        return itemstack;
    }

    public boolean canRemoveForPickupAll(ItemStack item, InventorySlot slot) {
        return true;
    }

    protected void clickSlot(int slot, int button, boolean quickMove, PlayerEntity player) {
        this.onClickSlot(slot, button, 1, player);
    }

    public void close(PlayerEntity player) {
        PlayerInventory playerinventory = player.inventory;
        if (playerinventory.getCursorItem() != null) {
            player.dropItem(playerinventory.getCursorItem(), false);
            playerinventory.setCursorItem(null);
        }
    }

    public void onContentsChanged(Inventory inventory) {
        this.updateListeners();
    }

    public void setItem(int slot, ItemStack item) {
        this.getSlot(slot).setItem(item);
    }

    public void setItems(ItemStack[] items) {
        for (int i = 0; i < items.length; i++) {
            this.getSlot(i).setItem(items[i]);
        }
    }

    public void setData(int id, int value) {
    }

    public short nextInteractionId(PlayerInventory inventory) {
        this.nextInteractionId++;
        return this.nextInteractionId;
    }

    public boolean isSynced(PlayerEntity player) {
        return !this.restrictedPlayers.contains(player);
    }

    public void setSynced(PlayerEntity player, boolean synced) {
        if (synced) {
            this.restrictedPlayers.remove(player);
        } else {
            this.restrictedPlayers.add(player);
        }
    }

    public abstract boolean isValid(PlayerEntity player);

    protected boolean moveItem(ItemStack item, int from, int to, boolean reverse) {
        boolean flag = false;
        int i = from;
        if (reverse) {
            i = to - 1;
        }

        if (item.isStackable()) {
            while (item.size > 0 && (!reverse && i < to || reverse && i >= from)) {
                InventorySlot inventoryslot = this.slots.get(i);
                ItemStack itemstack = inventoryslot.getItem();
                if (itemstack != null
                    && itemstack.getItem() == item.getItem()
                    && (!item.hasCustomData() || item.getMetadata() == itemstack.getMetadata())
                    && ItemStack.matchesNbt(item, itemstack)) {
                    int j = itemstack.size + item.size;
                    if (j <= item.getMaxSize()) {
                        item.size = 0;
                        itemstack.size = j;
                        inventoryslot.markDirty();
                        flag = true;
                    } else if (itemstack.size < item.getMaxSize()) {
                        item.size = item.size - (item.getMaxSize() - itemstack.size);
                        itemstack.size = item.getMaxSize();
                        inventoryslot.markDirty();
                        flag = true;
                    }
                }

                if (reverse) {
                    i--;
                } else {
                    i++;
                }
            }
        }

        if (item.size > 0) {
            if (reverse) {
                i = to - 1;
            } else {
                i = from;
            }

            while (!reverse && i < to || reverse && i >= from) {
                InventorySlot inventoryslot1 = this.slots.get(i);
                ItemStack itemstack1 = inventoryslot1.getItem();
                if (itemstack1 == null) {
                    inventoryslot1.setItem(item.copy());
                    inventoryslot1.markDirty();
                    item.size = 0;
                    flag = true;
                    break;
                }

                if (reverse) {
                    i--;
                } else {
                    i++;
                }
            }
        }

        return flag;
    }

    public static int unpackClickDragMode(int clickData) {
        return clickData >> 2 & 3;
    }

    public static int unpackClickDragStage(int clickData) {
        return clickData & 3;
    }

    public static int packClickData(int clickDragStage, int clickDragMode) {
        return clickDragStage & 3 | (clickDragMode & 3) << 2;
    }

    public static boolean isValidClickDragMode(int clickDragMode, PlayerEntity player) {
        return clickDragMode == 0 || clickDragMode == 1 || clickDragMode == 2 && player.abilities.creativeMode;
    }

    protected void dropClickDragging() {
        this.clickDragStage = 0;
        this.clickDragSlots.clear();
    }

    public static boolean canClickDragInto(InventorySlot slot, ItemStack item, boolean stackable) {
        boolean flag = slot == null || !slot.hasItem();
        if (slot != null && slot.hasItem() && item != null && item.matchesItem(slot.getItem()) && ItemStack.matchesNbt(slot.getItem(), item)) {
            int i = stackable ? 0 : item.size;
            flag |= slot.getItem().size + i <= item.getMaxSize();
        }

        return flag;
    }

    public static void updateClickDragStackSize(Set<InventorySlot> slots, int clickDragMode, ItemStack item, int stackSize) {
        switch (clickDragMode) {
            case 0:
                item.size = MathHelper.floor((float)item.size / slots.size());
                break;
            case 1:
                item.size = 1;
                break;
            case 2:
                item.size = item.getItem().getMaxStackSize();
        }

        item.size += stackSize;
    }

    public boolean canClickDragInto(InventorySlot invSlot) {
        return true;
    }

    public static int getAnalogSignal(BlockEntity blockEntity) {
        return blockEntity instanceof Inventory ? getAnalogSignal((Inventory)blockEntity) : 0;
    }

    public static int getAnalogSignal(Inventory inventory) {
        if (inventory == null) {
            return 0;
        }

        int i = 0;
        float f = 0.0F;

        for (int j = 0; j < inventory.getSize(); j++) {
            ItemStack itemstack = inventory.getItem(j);
            if (itemstack != null) {
                f += (float)itemstack.size / Math.min(inventory.getMaxStackSize(), itemstack.getMaxSize());
                i++;
            }
        }

        f /= inventory.getSize();
        return MathHelper.floor(f * 14.0F) + (i > 0 ? 1 : 0);
    }
}
