package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.server.hopper.HopperBlockEntity;
import net.zaminmc.torch.util.Position;

import java.util.Random;

/**
 * The hopper minecart (Slice 66 — the reference port of
 * vehicle/HopperMinecartEntity.java): the 5-slot rolling pickup whose
 * mouth sits at the cart's own center height.
 *
 * <p><b>The transfer walk (tick lines 99-117, 1:1):</b> the enabled cart
 * tracks its block cell; a tick at the SAME cell burns the cooldown down,
 * a tick anywhere else zeroes it — then an off-cooldown cart attempts the
 * transfer, and a successful pull arms the 4-tick cooldown (the cart's
 * own cadence, half the hopper block's 8). The reference's own quirk
 * rides verbatim: {@code f_0016592} — the "last position" — is never
 * reassigned after its {@code BlockPos.ORIGIN} declaration, so every tick
 * away from the world-origin cell takes the changed-cell arm and zeroes
 * the cooldown: a hopper minecart away from (0,0,0) attempts its transfer
 * EVERY tick, and the armed 4-tick cooldown only ever holds at the origin
 * cell. Ported exactly — the architecture's own "preserve observable
 * quirks" rule.</p>
 *
 * <p><b>The transfer itself (transferItems lines 119-130 through
 * HopperBlockEntity.pullItem/pickUpItem):</b> the container-above pull
 * first (the mouth's cell: {@code floor(y + 1.5)} — a chest, hopper,
 * furnace, dispenser, brewing stand, or a chest cart parked there); with
 * no container above, the item-entity suck from the 1-box centered on the
 * mouth-plus-one; a FULL suck arms the cooldown like the pull. The
 * fallback arm — the first item entity inside the cart's own shape grown
 * 0.25 horizontally — pushes its whole stack but NEVER arms the cooldown
 * (the reference's own {@code return false} after the pickup arm, the
 * every-tick suck the cart is known for).</p>
 *
 * <p><b>The activator disable (onActivatorRail lines 63-68):</b> a
 * POWERED activator rail disables the cart ({@code flag = !powered}),
 * an unpowered one re-enables it — the inverse sense of the TNT cart's
 * prime. There is no wire metadata for the flag in 1.8.8: the disable is
 * server-side only.</p>
 */
public class HopperMinecartEntity extends MinecartEntity {

    /** The transfer cooldown after a successful pull (tick line 112: 4). */
    public static final int TRANSFER_COOLDOWN = 4;

    /**
     * The world-side transfer (the engine's container adapter + the item
     * entities — the reference's HopperBlockEntity statics over the
     * cart's own hopper mouth). Wired at spawn by the engine like the
     * TNT cart's blast sink.
     */
    public interface TransferHost {
        /**
         * One transfer attempt (transferItems lines 119-130, 1:1).
         * @return whether the cooldown-arming pull succeeded (the
         *         own-shape fallback never does).
         */
        boolean transferItems(HopperMinecartEntity cart);
    }

    /** The 5-slot container (the hopper's own click semantics, 1:1). */
    private final HopperBlockEntity hopper = new HopperBlockEntity();
    private final TransferHost host;
    private boolean enabled = true;
    /** The reference's initial -1: a freshly placed cart transfers on its first tick. */
    private int transferCooldown = -1;

    public HopperMinecartEntity(int entityId, Position position, Random random,
                                TransferHost host) {
        super(entityId, Kind.HOPPER_MINECART, position, random);
        this.host = java.util.Objects.requireNonNull(host, "host");
    }

    /** The wire's minecart type index: 5 = hopper. */
    @Override
    public int minecartTypeIndex() {
        return 5;
    }

    /** The container (the 5 slots — the window, the transfers, the spill). */
    public HopperBlockEntity hopper() {
        return hopper;
    }

    /** @return whether the cart sucks (the activator rail's disable flag). */
    public boolean enabled() {
        return enabled;
    }

    /** The disable verb (setEnabled line 74). */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return the remaining cooldown, -1 before the first tick. */
    public int transferCooldown() {
        return transferCooldown;
    }

    /** The cooldown verb (setTransferCooldown line 152). */
    public void setTransferCooldown(int transferCooldown) {
        this.transferCooldown = transferCooldown;
    }

    /** @return whether the cart waits out a cooldown (hasTransferCooldown line 156). */
    public boolean hasTransferCooldown() {
        return transferCooldown > 0;
    }

    /**
     * The activator rail's arm (onActivatorRail lines 63-68): a powered
     * rail DISABLES the cart, an unpowered one re-enables it.
     */
    public void onActivatorRail(boolean powered) {
        boolean flag = !powered;
        if (flag != enabled) {
            enabled = flag;
        }
    }

    /**
     * The kind's tick (tick lines 99-117, 1:1 with the never-reassigned
     * {@code f_0016592} quirk): the cart body moves first, then the
     * enabled cart runs the cooldown walk and the transfer attempt.
     */
    @Override
    protected void tickKind(net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery world) {
        super.tickKind(world);
        if (!enabled || dead()) {
            return;
        }
        Position at = position();
        int cellX = (int) Math.floor(at.x());
        int cellY = (int) Math.floor(at.y());
        int cellZ = (int) Math.floor(at.z());
        // blockpos.equals(f_0016592) — f_0016592 stays BlockPos.ORIGIN
        // forever (the reference never reassigns it), so this arm holds
        // only at the world-origin cell.
        if (cellX == 0 && cellY == 0 && cellZ == 0) {
            transferCooldown--;
        } else {
            setTransferCooldown(0);
        }
        if (!hasTransferCooldown()) {
            setTransferCooldown(0);
            if (host.transferItems(this)) {
                setTransferCooldown(TRANSFER_COOLDOWN);
            }
        }
    }
}
