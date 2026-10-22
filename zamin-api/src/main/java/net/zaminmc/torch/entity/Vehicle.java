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

    /**
     * One riding seat row (the exotic pairs' api read beside
     * {@link #riderEntityId()}'s vehicle-family read — §27.3's riding
     * family, the §29 VII5/VIII1 exposure): the mount's engine id
     * paired with the seated rider's engine id — the same row the
     * engine's own join view ({@code genericMountSeatPairs}) walks for
     * the generic-mount registry, extended past it to the mob mounts'
     * rows and the vehicle seats' rows. An immutable value — freely
     * consumable from any thread once obtained.
     */
    record SeatRow(int mountEntityId, int riderEntityId) {
    }

    /**
     * The seat-row query surface (§27.3's riding family — the §29 VII5/
     * VIII1 exposure): the riding reads a plugin needs for the exotic
     * pairs — a rider seated on a NON-vehicle mount (an imported mob on
     * another mob, a mob on a dropped item, an XP orb riding a zombie, a
     * boat riding a minecart) — beside the vehicle family's own typed
     * {@code Vehicle.riderEntityId()} read. Reached through
     * {@code Server.seatRows()}.
     *
     * <p><b>The reference (1.8.8):</b> the engine implements the base
     * Entity's riding model — {@code updateRidden} (the reference's
     * Entity.java lines 1297-1349) plus the base {@code rideTick} follow
     * law (line 1353) and the seat-height law {@code getMountHeight}
     * (lines 1367-1369, the family's own height * 0.75), with the
     * anvil import's riding chain ({@code AnvilChunkStorage} line 335's
     * startRiding-at-load) as the origin of the exotic pairs. Recorded
     * at §29 VII5 (the generic-mount seat — the mount side) and VIII1
     * (the rider side of the exotic mounts).</p>
     *
     * <p><b>Threading (§27.6):</b> the per-id consults are live reads of
     * the engine's tick-lane-confined seat stores (the generic-mount
     * registry, the mob mounts' rider rows, the vehicle seats) — the
     * same stores the follow arms consult each tick; the resolver is
     * consulted on the tick lane today, so the reads carry the sibling
     * rows' law: <b>simulation context</b> (the documented owner path is
     * {@code World.execute(runnable)}). The census
     * ({@link #seatRows()}) answers a point-in-time copy — an off-lane
     * read reflects some instant between ticks, never a half-written
     * walk; the copy itself is freely consumable from any thread.</p>
     *
     * <p><b>The handle law (§27.5):</b> every consult resolves the live
     * engine stores fresh — never a second copy; a dismount changes the
     * next answer immediately.</p>
     */
    interface SeatRows {

        /** The empty answer (no seats live — an unbooted world's view). */
        SeatRows EMPTY = new SeatRows() {
            @Override
            public int seatedRiderEntityId(int mountEngineId) {
                return -1;
            }

            @Override
            public int mountEntityId(int riderEngineId) {
                return -1;
            }

            @Override
            public java.util.List<SeatRow> seatRows() {
                return java.util.List.of();
            }
        };

        /**
         * The mount's rider row — the exotic pairs' read beside the
         * vehicle family's own typed {@code Vehicle.riderEntityId()}:
         * the engine id of the rider seated on the given mount,
         * whichever store carries the seat (the generic-mount registry
         * first, then the mob mounts' rows, then the vehicle seats).
         * {@code -1} when the seat is empty or the id names no live
         * mount. Simulation context.
         *
         * @param mountEngineId the mount's engine entity id
         * @return the seated rider's engine id, or {@code -1}
         */
        int seatedRiderEntityId(int mountEngineId);

        /**
         * The rider's mount row (VIII1's rider side — the same seat row
         * answered from the other end): the engine id of the mount the
         * given rider is seated on, whichever store carries the seat.
         * {@code -1} when the rider rides nothing. Simulation context.
         *
         * @param riderEngineId the rider's engine entity id
         * @return the mount's engine id, or {@code -1}
         */
        int mountEntityId(int riderEngineId);

        /**
         * Every live seat row across the three stores (the census — the
         * join view's own walk extended past the generic-mount registry
         * to the mob mounts' rows and the vehicle seats). A
         * point-in-time copy (§27.6): best-effort off the simulation
         * context, freely consumable from any thread once obtained.
         *
         * @return an immutable list of every live (mount id, rider id) row
         */
        java.util.List<SeatRow> seatRows();
    }

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

    /**
     * The variant-specific state view of a special minecart (the
     * furnace's fuel and push, the TNT's fuse, the spawner's type and
     * delay, the command cart's executor rows); empty for the boat, the
     * plain minecart, and the chest cart (the chest's container rides
     * {@link #cargo()}). Simulation context (§27.6 — the documented
     * owner path is {@code World.execute(runnable)}).
     */
    default java.util.Optional<VariantState> variantState() {
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
