package net.zaminmc.torch.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

/**
 * The public handle of one vehicle (the boat and the minecart —
 * architecture §27.3's vehicle/mount family).
 *
 * <p>Threading contract (§27.6): every method touches live authoritative
 * state and must run on the world's simulation context — the documented
 * owner path is {@code World.execute(runnable)}. For off-owner
 * observation use {@link #snapshot()}. Obtain handles through
 * {@code World.vehicles()} (a point-in-time population view).</p>
 */
public interface Vehicle extends Entity {

    /** The vehicle's kind. Simulation context. */
    VehicleKind vehicleKind();

    /** The vehicle's world position. Simulation context. */
    Position position();

    /** The vehicle's body rotation. Simulation context. */
    Rotation rotation();

    /** x velocity in blocks per tick. Simulation context. */
    double velocityX();

    /** y velocity in blocks per tick. Simulation context. */
    double velocityY();

    /** z velocity in blocks per tick. Simulation context. */
    double velocityZ();

    /** The remaining hull health (the historical 4.0 ceiling). Simulation context. */
    float health();

    /**
     * The mounted rider's engine entity id, or {@code -1} when the seat is
     * empty. Simulation context.
     */
    int riderEntityId();

    /** Whether the vehicle rests on the ground. Simulation context. */
    boolean onGround();

    /**
     * The cargo of a container vehicle (the chest minecart's rolling
     * 27-slot chest); empty for the boat and the plain minecart.
     * Simulation context. The cargo's write verbs run on the world's
     * authoritative simulation context (§27.6).
     */
    default java.util.Optional<Cargo> cargo() {
        return java.util.Optional.empty();
    }

    /** A container vehicle's cargo (the rolling chest grid). */
    interface Cargo {

        /** @return the slot count (the vanilla chest cart's 27). */
        int slotCount();

        /** @return a copy of the slots (empty cells as empty stacks). */
        net.zaminmc.torch.item.ItemStack[] slots();

        /** @return one slot's stack copy (empty when out of range). */
        net.zaminmc.torch.item.ItemStack slot(int index);

        /**
         * Writes one slot (an empty stack clears it; out-of-range writes
         * are ignored). Owner context (§27.6 — an off-lane write fails
         * diagnosably).
         */
        void setSlot(int index, net.zaminmc.torch.item.ItemStack stack);
    }
}
