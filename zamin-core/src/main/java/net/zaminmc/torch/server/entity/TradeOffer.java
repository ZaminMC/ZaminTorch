package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;

/**
 * One villager trade offer (the historical MerchantRecipe shape): the buy
 * stacks, the result and the use budget. The offer itself is immutable —
 * the per-villager use counters live beside it in the villager body.
 *
 * @param buy1    the first (mandatory) buy stack
 * @param buy2    the optional second buy stack (EMPTY when the offer takes one)
 * @param result  the stack the trade pays out
 * @param maxUses the historical use budget (7 for most stock offers)
 */
public record TradeOffer(ItemStack buy1, ItemStack buy2, ItemStack result, int maxUses) {

    public TradeOffer {
        if (buy1 == null || buy1.isEmpty() || result == null || result.isEmpty()) {
            throw new IllegalArgumentException("A trade offer needs a buy and a result");
        }
        if (buy2 == null) {
            buy2 = ItemStack.EMPTY;
        }
        if (maxUses < 1) {
            throw new IllegalArgumentException("maxUses must be positive: " + maxUses);
        }
    }

    /** @return whether the offer takes a second buy stack. */
    public boolean hasSecondBuy() {
        return !buy2.isEmpty();
    }
}
