package net.zaminmc.torch.server.beacon;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.util.Objects;

/**
 * One placed beacon's state (Slice 29b — the reference port of
 * block/entity/BeaconBlockEntity): the computed pyramid level, the chosen
 * primary/secondary effects, and the single payment slot (the beacon's own
 * 1-slot {@code Inventory} — emerald/diamond/gold ingot/iron ingot, max
 * stack 1, consumed by the MC|Beacon confirm).
 *
 * <p>The beam's color sections are CLIENT-side state (the client's own BE
 * walks its world); the server's model ends at {@code levels} — the pyramid
 * depth — and the two effect choices. The stained-glass color blending is
 * ledgered against the stained-glass family's own slice.</p>
 *
 * <p>Ownership: mutated only by the simulation context (the system's 80-tick
 * walk, the click routing, the MC|Beacon application).</p>
 */
public final class BeaconBlockEntity {

    /** The reference's own payment filter (isItemAllowed lines 315-317). */
    public static boolean isPaymentItem(net.zaminmc.torch.item.ItemType type) {
        return type == BuiltinItems.EMERALD || type == BuiltinItems.DIAMOND
                || type == BuiltinItems.GOLD_INGOT || type == BuiltinItems.IRON_INGOT;
    }

    /** The reference's effect gate (getBeaconEffect lines 207-221): the six
     *  beacon effects only, everything else collapses to 0 (none). */
    public static int beaconEffectOf(int id) {
        return switch (id) {
            case 1, 3, 5, 8, 10, 11 -> id; // speed, haste, strength, jump, regen, resistance
            default -> 0;
        };
    }

    private int levels;
    private int primary;
    private int secondary;
    private ItemStack payment = ItemStack.EMPTY;
    private int slotsSerial;
    private int propsSerial;

    /** @return the computed pyramid level (0 = inactive). */
    public int levels() {
        return levels;
    }

    /** @return the chosen primary effect id (0 = none). */
    public int primary() {
        return primary;
    }

    /** @return the chosen secondary effect id (0 = none). */
    public int secondary() {
        return secondary;
    }

    /** The computed-level write (updateLevels' own output). */
    public void setLevels(int levels) {
        if (this.levels != levels) {
            this.levels = levels;
            propsSerial++;
        }
    }

    /** The primary write through the reference's own effect gate. */
    public void setPrimary(int primary) {
        int gated = beaconEffectOf(primary);
        if (this.primary != gated) {
            this.primary = gated;
            propsSerial++;
        }
    }

    /** The secondary write through the reference's own effect gate. */
    public void setSecondary(int secondary) {
        int gated = beaconEffectOf(secondary);
        if (this.secondary != gated) {
            this.secondary = gated;
            propsSerial++;
        }
    }

    /** The restored primary (the ZBT load — already gated at save time). */
    public void restorePrimary(int primary) {
        this.primary = beaconEffectOf(primary);
        propsSerial++;
    }

    /** The restored secondary (the ZBT load). */
    public void restoreSecondary(int secondary) {
        this.secondary = beaconEffectOf(secondary);
        propsSerial++;
    }

    /** The restored level (the ZBT load). */
    public void restoreLevels(int levels) {
        this.levels = levels;
        propsSerial++;
    }

    /** @return the payment slot's stack (possibly empty). */
    public ItemStack payment() {
        return payment;
    }

    /** The payment slot write (the click routing's set arm). */
    public void setPayment(ItemStack stack) {
        this.payment = Objects.requireNonNull(stack, "stack");
        slotsSerial++;
    }

    /** The MC|Beacon consumption: one unit off the payment (the handler's
     *  {@code inventoryslot.removeItem(1)}). @return whether a unit left. */
    public boolean consumePaymentUnit() {
        if (payment.isEmpty()) {
            return false;
        }
        payment = payment.count() > 1
                ? ItemStack.of(payment.type(), payment.count() - 1).withDamage(payment.damage())
                : ItemStack.EMPTY;
        slotsSerial++;
        return true;
    }

    /** @return how many times the payment slot mutated (wire sync dedup). */
    public int slotsSerial() {
        return slotsSerial;
    }

    /** @return how many times the properties moved (the GUI sync dedup). */
    public int propsSerial() {
        return propsSerial;
    }
}
