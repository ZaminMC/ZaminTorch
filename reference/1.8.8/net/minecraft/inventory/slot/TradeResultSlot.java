package net.minecraft.inventory.slot;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.world.village.trade.TradeOffer;
import net.minecraft.world.village.trade.Trader;
import net.minecraft.world.village.trade.TraderInventory;

public class TradeResultSlot extends InventorySlot {
    private final TraderInventory traderInventory;
    private PlayerEntity player;
    private int removeAmount;
    private final Trader trader;

    public TradeResultSlot(PlayerEntity player, Trader trader, TraderInventory traderInventory, int slot, int x, int y) {
        super(traderInventory, slot, x, y);
        this.player = player;
        this.trader = trader;
        this.traderInventory = traderInventory;
    }

    @Override
    public boolean isItemAllowed(ItemStack item) {
        return false;
    }

    @Override
    public ItemStack removeItem(int amount) {
        if (this.hasItem()) {
            this.removeAmount = this.removeAmount + Math.min(amount, this.getItem().size);
        }

        return super.removeItem(amount);
    }

    @Override
    protected void onQuickMoved(ItemStack item, int amount) {
        this.removeAmount += amount;
        this.checkAchievements(item);
    }

    @Override
    protected void checkAchievements(ItemStack item) {
        item.onResult(this.player.world, this.player, this.removeAmount);
        this.removeAmount = 0;
    }

    @Override
    public void onItemRemoved(PlayerEntity player, ItemStack item) {
        this.checkAchievements(item);
        TradeOffer tradeoffer = this.traderInventory.getOffer();
        if (tradeoffer != null) {
            ItemStack itemstack = this.traderInventory.getItem(0);
            ItemStack itemstack1 = this.traderInventory.getItem(1);
            if (this.acceptPayment(tradeoffer, itemstack, itemstack1) || this.acceptPayment(tradeoffer, itemstack1, itemstack)) {
                this.trader.trade(tradeoffer);
                player.incrementStat(Stats.TRADED_WITH_VILLAGER);
                if (itemstack != null && itemstack.size <= 0) {
                    itemstack = null;
                }

                if (itemstack1 != null && itemstack1.size <= 0) {
                    itemstack1 = null;
                }

                this.traderInventory.setItem(0, itemstack);
                this.traderInventory.setItem(1, itemstack1);
            }
        }
    }

    private boolean acceptPayment(TradeOffer offer, ItemStack primaryPayment, ItemStack secondaryPayment) {
        ItemStack itemstack = offer.getPrimaryPayment();
        ItemStack itemstack1 = offer.getSecondaryPayment();
        if (primaryPayment != null && primaryPayment.getItem() == itemstack.getItem()) {
            if (itemstack1 != null && secondaryPayment != null && itemstack1.getItem() == secondaryPayment.getItem()) {
                primaryPayment.size = primaryPayment.size - itemstack.size;
                secondaryPayment.size = secondaryPayment.size - itemstack1.size;
                return true;
            }

            if (itemstack1 == null && secondaryPayment == null) {
                primaryPayment.size = primaryPayment.size - itemstack.size;
                return true;
            }
        }

        return false;
    }
}
