package net.minecraft.world.village.trade;

import java.io.IOException;
import java.util.ArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.PacketByteBuf;

public class TradeOffers extends ArrayList<TradeOffer> {
    public TradeOffers() {
    }

    public TradeOffers(NbtCompound nbt) {
        this.readNbt(nbt);
    }

    public TradeOffer get(ItemStack primaryPayment, ItemStack secondaryPayment, int index) {
        if (index > 0 && index < this.size()) {
            TradeOffer tradeoffer1 = this.get(index);
            return !this.acceptsPayment(primaryPayment, tradeoffer1.getPrimaryPayment())
                    || (secondaryPayment != null || tradeoffer1.hasSecondaryPayment())
                        && (!tradeoffer1.hasSecondaryPayment() || !this.acceptsPayment(secondaryPayment, tradeoffer1.getSecondaryPayment()))
                    || primaryPayment.size < tradeoffer1.getPrimaryPayment().size
                    || tradeoffer1.hasSecondaryPayment() && secondaryPayment.size < tradeoffer1.getSecondaryPayment().size
                ? null
                : tradeoffer1;
        }

        for (int i = 0; i < this.size(); i++) {
            TradeOffer tradeoffer = this.get(i);
            if (this.acceptsPayment(primaryPayment, tradeoffer.getPrimaryPayment())
                && primaryPayment.size >= tradeoffer.getPrimaryPayment().size
                && (
                    !tradeoffer.hasSecondaryPayment() && secondaryPayment == null
                        || tradeoffer.hasSecondaryPayment()
                            && this.acceptsPayment(secondaryPayment, tradeoffer.getSecondaryPayment())
                            && secondaryPayment.size >= tradeoffer.getSecondaryPayment().size
                )) {
                return tradeoffer;
            }
        }

        return null;
    }

    private boolean acceptsPayment(ItemStack payment, ItemStack requestedPayment) {
        return ItemStack.matchesItem(payment, requestedPayment)
            && (!requestedPayment.hasNbt() || payment.hasNbt() && NbtUtils.matches(requestedPayment.getNbt(), payment.getNbt(), false));
    }

    public void serialize(PacketByteBuf buffer) {
        buffer.writeByte((byte)(this.size() & 0xFF));

        for (int i = 0; i < this.size(); i++) {
            TradeOffer tradeoffer = this.get(i);
            buffer.writeItem(tradeoffer.getPrimaryPayment());
            buffer.writeItem(tradeoffer.getResult());
            ItemStack itemstack = tradeoffer.getSecondaryPayment();
            buffer.writeBoolean(itemstack != null);
            if (itemstack != null) {
                buffer.writeItem(itemstack);
            }

            buffer.writeBoolean(tradeoffer.isDisabled());
            buffer.writeInt(tradeoffer.getUses());
            buffer.writeInt(tradeoffer.getMaxUses());
        }
    }

    public static TradeOffers deserialize(PacketByteBuf buffer) throws IOException {
        TradeOffers tradeoffers = new TradeOffers();
        int i = buffer.readByte() & 255;

        for (int j = 0; j < i; j++) {
            ItemStack itemstack = buffer.readItem();
            ItemStack itemstack1 = buffer.readItem();
            ItemStack itemstack2 = null;
            if (buffer.readBoolean()) {
                itemstack2 = buffer.readItem();
            }

            boolean flag = buffer.readBoolean();
            int k = buffer.readInt();
            int l = buffer.readInt();
            TradeOffer tradeoffer = new TradeOffer(itemstack, itemstack2, itemstack1, k, l);
            if (flag) {
                tradeoffer.clearUses();
            }

            tradeoffers.add(tradeoffer);
        }

        return tradeoffers;
    }

    public void readNbt(NbtCompound nbt) {
        NbtList nbtlist = nbt.getList("Recipes", 10);

        for (int i = 0; i < nbtlist.size(); i++) {
            NbtCompound nbtcompound = nbtlist.getCompound(i);
            this.add(new TradeOffer(nbtcompound));
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();
        NbtList nbtlist = new NbtList();

        for (int i = 0; i < this.size(); i++) {
            TradeOffer tradeoffer = this.get(i);
            nbtlist.addElement(tradeoffer.toNbt());
        }

        nbtcompound.put("Recipes", nbtlist);
        return nbtcompound;
    }
}
