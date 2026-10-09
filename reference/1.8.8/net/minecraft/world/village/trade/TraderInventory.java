package net.minecraft.world.village.trade;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class TraderInventory implements Inventory {
    private final Trader trader;
    private ItemStack[] items = new ItemStack[3];
    private final PlayerEntity player;
    private TradeOffer offer;
    private int offerIndex;

    public TraderInventory(PlayerEntity player, Trader trader) {
        this.player = player;
        this.trader = trader;
    }

    @Override
    public int getSize() {
        return this.items.length;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (this.items[slot] != null) {
            if (slot == 2) {
                ItemStack itemstack2 = this.items[slot];
                this.items[slot] = null;
                return itemstack2;
            }

            if (this.items[slot].size <= amount) {
                ItemStack itemstack1 = this.items[slot];
                this.items[slot] = null;
                if (this.isInputSlot(slot)) {
                    this.updateOffer();
                }

                return itemstack1;
            } else {
                ItemStack itemstack = this.items[slot].split(amount);
                if (this.items[slot].size == 0) {
                    this.items[slot] = null;
                }

                if (this.isInputSlot(slot)) {
                    this.updateOffer();
                }

                return itemstack;
            }
        } else {
            return null;
        }
    }

    private boolean isInputSlot(int slot) {
        return slot == 0 || slot == 1;
    }

    @Override
    public ItemStack removeItemQuietly(int slot) {
        if (this.items[slot] != null) {
            ItemStack itemstack = this.items[slot];
            this.items[slot] = null;
            return itemstack;
        } else {
            return null;
        }
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        this.items[slot] = item;
        if (item != null && item.size > this.getMaxStackSize()) {
            item.size = this.getMaxStackSize();
        }

        if (this.isInputSlot(slot)) {
            this.updateOffer();
        }
    }

    @Override
    public String getName() {
        return "mob.villager";
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

    @Override
    public boolean isValid(PlayerEntity player) {
        return this.trader.getCustomer() == player;
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

    @Override
    public void markDirty() {
        this.updateOffer();
    }

    public void updateOffer() {
        this.offer = null;
        ItemStack itemstack = this.items[0];
        ItemStack itemstack1 = this.items[1];
        if (itemstack == null) {
            itemstack = itemstack1;
            itemstack1 = null;
        }

        if (itemstack == null) {
            this.setItem(2, null);
        } else {
            TradeOffers tradeoffers = this.trader.getOffers(this.player);
            if (tradeoffers != null) {
                TradeOffer tradeoffer = tradeoffers.get(itemstack, itemstack1, this.offerIndex);
                if (tradeoffer != null && !tradeoffer.isDisabled()) {
                    this.offer = tradeoffer;
                    this.setItem(2, tradeoffer.getResult().copy());
                } else if (itemstack1 != null) {
                    tradeoffer = tradeoffers.get(itemstack1, itemstack, this.offerIndex);
                    if (tradeoffer != null && !tradeoffer.isDisabled()) {
                        this.offer = tradeoffer;
                        this.setItem(2, tradeoffer.getResult().copy());
                    } else {
                        this.setItem(2, null);
                    }
                } else {
                    this.setItem(2, null);
                }
            }
        }

        this.trader.updateOffer(this.getItem(2));
    }

    public TradeOffer getOffer() {
        return this.offer;
    }

    public void setOffer(int index) {
        this.offerIndex = index;
        this.updateOffer();
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
    }
}
