package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.entity.VehicleKind;
import net.zaminmc.torch.util.Position;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The pending-spawn {@link org.bukkit.entity.Vehicle} view — the {@code
 * VehicleCreateEvent} bridge's entity (the §28.7 entity adapters —
 * slice 3).
 *
 * <p><b>The pending law (the recorded §27.9 divergence, the {@link
 * TorchPendingMob} twin):</b> the engine's {@code VehicleSpawnEvent}
 * fires BEFORE the vehicle body joins the world (a cancelled spawn
 * consumes no id and does not consume the placing item — the engine's
 * own withholding semantics), so the legacy form's entity wraps the
 * spawn facts the native event carries: the kind and the position.
 * The type, location and world rows answer honestly; every body read
 * or mutation fails loudly naming the engine's own route — including
 * the velocity pair the 1.8.8 {@code Vehicle} interface redeclares
 * (the native event carries no velocity row; the engine's body takes
 * its first velocity only after it exists).</p>
 *
 * <p><b>The non-cancellable legacy shape (the reference's own):</b>
 * the 1.8.8 {@code VehicleCreateEvent} is NOT cancellable — the
 * reference's own type shape, kept honestly. A plugin cannot withhold
 * a vehicle spawn through the legacy event; the engine's native {@code
 * VehicleSpawnEvent} keeps the capability (§28.10: the compatibility
 * limit stays in the adapter, the native API loses nothing).</p>
 *
 * <p><b>The world row:</b> the native event carries a bare position;
 * the engine's vehicle-spawn family is overworld-scoped at gate time —
 * {@code getWorld()} resolves the overworld view (the recorded
 * note).</p>
 */
public final class TorchPendingVehicle extends TorchEntityView
        implements org.bukkit.entity.Vehicle {

    private final VehicleKind kind;
    private final Position position;

    public TorchPendingVehicle(BukkitAdapter adapter, VehicleKind kind, Position position) {
        super(adapter);
        this.kind = kind;
        this.position = position;
    }

    // ------------------------------------------------------------------ the hooks (the spawn facts)

    @Override
    protected org.bukkit.entity.EntityType bukkitType() {
        return EntityTypeMap.of(kind);
    }

    @Override
    protected TorchWorld viewWorld() {
        return adapter.worldView(adapter.engine().world()); // the §27.9 note above
    }

    @Override
    protected Position viewPosition() {
        return position;
    }

    @Override
    protected UUID viewUniqueId() {
        throw adapter.unbridged("VehicleCreate.getEntityId/getUniqueId (the"
                + " engine's spawn gate fires before the body exists — no"
                + " id is consumed yet)");
    }

    // ------------------------------------------------------------------ the Vehicle rows (the velocity pair the interface redeclares)

    @Override
    public Vector getVelocity() {
        throw adapter.unbridged("VehicleCreate view.getVelocity (the"
                + " native event carries no velocity row — the body takes"
                + " its first velocity only after it exists)");
    }

    @Override
    public void setVelocity(Vector velocity) {
        throw adapter.unbridged("VehicleCreate view.setVelocity (nothing"
                + " exists to mutate — the native VehicleSpawnEvent's own"
                + " cancel arm is the gate's one verb)");
    }

    // ------------------------------------------------------------------ the pending law (the loud refusals)

    @Override
    public int getEntityId() {
        throw pendingBodyRead("getEntityId");
    }

    @Override
    public boolean isDead() {
        throw pendingBodyRead("isDead");
    }

    @Override
    public boolean isValid() {
        throw pendingBodyRead("isValid");
    }

    @Override
    public boolean isOnGround() {
        throw pendingBodyRead("isOnGround");
    }

    @Override
    public int getTicksLived() {
        throw pendingBodyRead("getTicksLived");
    }

    @Override
    public int getFireTicks() {
        throw pendingBodyRead("getFireTicks");
    }

    @Override
    public int getMaxFireTicks() {
        throw pendingBodyRead("getMaxFireTicks");
    }

    @Override
    public void setFireTicks(int ticks) {
        throw pendingMutation("setFireTicks");
    }

    @Override
    public void remove() {
        throw pendingMutation("remove");
    }

    @Override
    public boolean teleport(Location location) {
        throw pendingMutation("teleport");
    }

    @Override
    public boolean teleport(Location location,
            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        throw pendingMutation("teleport");
    }

    @Override
    public boolean teleport(org.bukkit.entity.Entity destination) {
        throw pendingMutation("teleport");
    }

    @Override
    public boolean teleport(org.bukkit.entity.Entity destination,
            org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause) {
        throw pendingMutation("teleport");
    }

    @Override
    public float getFallDistance() {
        throw pendingBodyRead("getFallDistance");
    }

    @Override
    public void setFallDistance(float distance) {
        throw pendingMutation("setFallDistance");
    }

    @Override
    public void setLastDamageCause(org.bukkit.event.entity.EntityDamageEvent event) {
        throw pendingMutation("setLastDamageCause");
    }

    @Override
    public org.bukkit.event.entity.EntityDamageEvent getLastDamageCause() {
        throw pendingBodyRead("getLastDamageCause");
    }

    @Override
    public org.bukkit.entity.Entity getPassenger() {
        throw pendingBodyRead("getPassenger");
    }

    @Override
    public boolean setPassenger(org.bukkit.entity.Entity passenger) {
        throw pendingMutation("setPassenger");
    }

    @Override
    public boolean isEmpty() {
        throw pendingBodyRead("isEmpty");
    }

    @Override
    public boolean eject() {
        throw pendingMutation("eject");
    }

    @Override
    public boolean isInsideVehicle() {
        throw pendingBodyRead("isInsideVehicle");
    }

    @Override
    public boolean leaveVehicle() {
        throw pendingMutation("leaveVehicle");
    }

    @Override
    public org.bukkit.entity.Entity getVehicle() {
        throw pendingBodyRead("getVehicle");
    }

    @Override
    public void setTicksLived(int value) {
        throw pendingMutation("setTicksLived");
    }

    @Override
    public void setCustomName(String name) {
        throw pendingMutation("setCustomName");
    }

    @Override
    public String getCustomName() {
        return null; // the engine's vehicle bodies carry no name row at spawn
    }

    @Override
    public void setCustomNameVisible(boolean visible) {
        throw pendingMutation("setCustomNameVisible");
    }

    @Override
    public boolean isCustomNameVisible() {
        return false;
    }

    // ------------------------------------------------------------------ the refusal builders

    private UnsupportedOperationException pendingBodyRead(String what) {
        return adapter.unbridged("VehicleCreate view." + what
                + " (the engine's spawn gate fires before the body exists"
                + " — the spawn facts alone are carried)");
    }

    private UnsupportedOperationException pendingMutation(String what) {
        return adapter.unbridged("VehicleCreate view." + what
                + " (nothing exists to mutate — the native"
                + " VehicleSpawnEvent's own cancel arm is the gate's one"
                + " verb)");
    }
}
