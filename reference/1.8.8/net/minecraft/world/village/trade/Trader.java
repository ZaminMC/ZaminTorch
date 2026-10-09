package net.minecraft.world.village.trade;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public interface Trader {
    void setCustomer(PlayerEntity player);

    PlayerEntity getCustomer();

    TradeOffers getOffers(PlayerEntity player);

    void setOffers(TradeOffers offers);

    void trade(TradeOffer offer);

    void updateOffer(ItemStack item);

    Text getDisplayName();
}
