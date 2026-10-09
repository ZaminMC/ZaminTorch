package net.minecraft.world.village.trade;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public class TradeOffer {
    private ItemStack primaryPayment;
    private ItemStack secondaryPayment;
    private ItemStack result;
    private int uses;
    private int maxUses;
    private boolean rewardXp;

    public TradeOffer(NbtCompound nbt) {
        this.readNbt(nbt);
    }

    public TradeOffer(ItemStack primaryPayment, ItemStack secondaryPayment, ItemStack result) {
        this(primaryPayment, secondaryPayment, result, 0, 7);
    }

    public TradeOffer(ItemStack primaryPayment, ItemStack secondaryPayment, ItemStack result, int uses, int maxUses) {
        this.primaryPayment = primaryPayment;
        this.secondaryPayment = secondaryPayment;
        this.result = result;
        this.uses = uses;
        this.maxUses = maxUses;
        this.rewardXp = true;
    }

    public TradeOffer(ItemStack payment, ItemStack result) {
        this(payment, null, result);
    }

    public TradeOffer(ItemStack payment, Item result) {
        this(payment, new ItemStack(result));
    }

    public ItemStack getPrimaryPayment() {
        return this.primaryPayment;
    }

    public ItemStack getSecondaryPayment() {
        return this.secondaryPayment;
    }

    public boolean hasSecondaryPayment() {
        return this.secondaryPayment != null;
    }

    public ItemStack getResult() {
        return this.result;
    }

    public int getUses() {
        return this.uses;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void use() {
        this.uses++;
    }

    public void increaseMaxUses(int uses) {
        this.maxUses += uses;
    }

    public boolean isDisabled() {
        return this.uses >= this.maxUses;
    }

    public void clearUses() {
        this.uses = this.maxUses;
    }

    public boolean rewardXp() {
        return this.rewardXp;
    }

    public void readNbt(NbtCompound nbt) {
        NbtCompound nbtcompound = nbt.getCompound("buy");
        this.primaryPayment = ItemStack.fromNbt(nbtcompound);
        NbtCompound nbtcompound1 = nbt.getCompound("sell");
        this.result = ItemStack.fromNbt(nbtcompound1);
        if (nbt.contains("buyB", 10)) {
            this.secondaryPayment = ItemStack.fromNbt(nbt.getCompound("buyB"));
        }

        if (nbt.contains("uses", 99)) {
            this.uses = nbt.getInt("uses");
        }

        if (nbt.contains("maxUses", 99)) {
            this.maxUses = nbt.getInt("maxUses");
        } else {
            this.maxUses = 7;
        }

        if (nbt.contains("rewardExp", 1)) {
            this.rewardXp = nbt.getBoolean("rewardExp");
        } else {
            this.rewardXp = true;
        }
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();
        nbtcompound.put("buy", this.primaryPayment.writeNbt(new NbtCompound()));
        nbtcompound.put("sell", this.result.writeNbt(new NbtCompound()));
        if (this.secondaryPayment != null) {
            nbtcompound.put("buyB", this.secondaryPayment.writeNbt(new NbtCompound()));
        }

        nbtcompound.putInt("uses", this.uses);
        nbtcompound.putInt("maxUses", this.maxUses);
        nbtcompound.putBoolean("rewardExp", this.rewardXp);
        return nbtcompound;
    }
}
