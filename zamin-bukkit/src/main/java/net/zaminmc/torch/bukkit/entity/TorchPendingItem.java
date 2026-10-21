package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.util.Position;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The pending-spawn {@link org.bukkit.entity.Item} view — the {@code
 * ItemSpawnEvent} bridge's entity (the §28.7 entity adapters — slice
 * 3).
 *
 * <p><b>The pending law (the recorded §27.9 divergence, the {@link
 * TorchPendingMob} twin):</b> the engine's {@code ItemSpawnEvent}
 * fires BEFORE the item body joins the world (a cancelled drop consumes
 * no id and withholds the stack — the engine's own withholding
 * semantics), so the legacy form's entity wraps the spawn facts the
 * native event carries: the stack and the position. {@code
 * getItemStack()} answers the stack's legacy VALUE view (a fresh
 * snapshot — the native event's own copy, the MaterialMap law over the
 * identifier's bare path, never a live view); the type, location and
 * world rows answer honestly; every BODY read or mutation fails loudly
 * naming the engine's own route.</p>
 *
 * <p><b>The world row:</b> the native event carries a bare position;
 * the engine's item-spawn family is overworld-scoped at gate time —
 * {@code getWorld()} resolves the overworld view (the recorded
 * note).</p>
 */
public final class TorchPendingItem extends TorchEntityView
        implements org.bukkit.entity.Item {

    private final org.bukkit.inventory.ItemStack stack;
    private final Position position;

    public TorchPendingItem(BukkitAdapter adapter,
            org.bukkit.inventory.ItemStack legacyStack, Position position) {
        super(adapter);
        this.stack = legacyStack;
        this.position = position;
    }

    // ------------------------------------------------------------------ the hooks (the spawn facts)

    @Override
    protected org.bukkit.entity.EntityType bukkitType() {
        return EntityTypeMap.droppedItem();
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
        throw adapter.unbridged("ItemSpawn.getEntityId/getUniqueId (the"
                + " engine's drop gate fires before the body exists — no"
                + " id is consumed yet)");
    }

    // ------------------------------------------------------------------ the Item rows

    @Override
    public org.bukkit.inventory.ItemStack getItemStack() {
        // The value view the bridge built from the native event's own
        // copy — a fresh snapshot per read (the legacy stack contract:
        // plugins may freely mutate what they get; the mutation never
        // reaches the engine's stack).
        return stack.clone();
    }

    @Override
    public void setItemStack(org.bukkit.inventory.ItemStack stack) {
        throw adapter.unbridged("ItemSpawn view.setItemStack (the engine's"
                + " drop gate fires before the body exists — the native"
                + " event's stack is the event's own copy; the engine's"
                + " spawn surface owns the drop)");
    }

    @Override
    public int getPickupDelay() {
        throw adapter.unbridged("ItemSpawn view.getPickupDelay (the native"
                + " event carries no delay row — the caller sets the delay"
                + " after the gate admits the drop)");
    }

    @Override
    public void setPickupDelay(int delay) {
        throw adapter.unbridged("ItemSpawn view.setPickupDelay (no body"
                + " exists to carry the delay)");
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
    public Vector getVelocity() {
        throw pendingBodyRead("getVelocity");
    }

    @Override
    public void setVelocity(Vector velocity) {
        throw pendingMutation("setVelocity");
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
        return null; // the engine's dropped bodies carry no name row
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
        return adapter.unbridged("ItemSpawn view." + what
                + " (the engine's drop gate fires before the body exists"
                + " — the spawn facts alone are carried)");
    }

    private UnsupportedOperationException pendingMutation(String what) {
        return adapter.unbridged("ItemSpawn view." + what
                + " (nothing exists to mutate — the native ItemSpawnEvent's"
                + " own cancel arm is the gate's one verb)");
    }
}
