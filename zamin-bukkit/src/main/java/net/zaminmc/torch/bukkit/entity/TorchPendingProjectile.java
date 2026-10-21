package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.entity.ProjectileKind;
import net.zaminmc.torch.util.Position;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The pending-launch {@link org.bukkit.entity.Projectile} view — the
 * {@code ProjectileLaunchEvent} bridge's entity (the §28.7 entity
 * adapters — slice 3).
 *
 * <p><b>The pending law (the recorded §27.9 divergence, the {@link
 * TorchPendingMob} twin):</b> the engine's {@code
 * ProjectileSpawnEvent} fires BEFORE the projectile body joins the
 * world (a cancelled shot consumes no id — the engine's own
 * contract), so the legacy form's entity wraps the launch facts the
 * native event carries: the kind, the thrower's engine id and the
 * origin. {@code getShooter()} resolves the thrower id against the
 * adapter's own views (an online session answers the player view, a
 * live mob answers the mob view, -1 or an unknown body answers null —
 * never an invented shooter); {@code doesBounce()} answers false (the
 * engine's projectiles never bounce — no row exists to fake); every
 * body read or mutation fails loudly naming the engine's own
 * route.</p>
 *
 * <p><b>The world row:</b> the native event carries a bare position;
 * the engine's launch family is overworld-scoped at gate time — {@code
 * getWorld()} resolves the overworld view (the recorded note).</p>
 */
public final class TorchPendingProjectile extends TorchEntityView
        implements org.bukkit.entity.Projectile {

    private final ProjectileKind kind;
    private final int throwerEntityId;
    private final Position position;

    public TorchPendingProjectile(BukkitAdapter adapter, ProjectileKind kind,
            int throwerEntityId, Position position) {
        super(adapter);
        this.kind = kind;
        this.throwerEntityId = throwerEntityId;
        this.position = position;
    }

    // ------------------------------------------------------------------ the hooks (the launch facts)

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
        throw adapter.unbridged("ProjectileLaunch.getEntityId/getUniqueId"
                + " (the engine's launch gate fires before the body exists"
                + " — no id is consumed yet)");
    }

    // ------------------------------------------------------------------ the Projectile rows

    @Override
    public org.bukkit.projectiles.ProjectileSource getShooter() {
        // The thrower id the native event carries, resolved against the
        // adapter's own views — never an invented shooter.
        return adapter.projectileSourceByEngineId(throwerEntityId);
    }

    @Override
    public org.bukkit.entity.LivingEntity _INVALID_getShooter() {
        org.bukkit.entity.Entity source = adapter.entityByEngineId(throwerEntityId);
        return source instanceof org.bukkit.entity.LivingEntity living
                ? living : null;
    }

    @Override
    public void setShooter(org.bukkit.projectiles.ProjectileSource source) {
        throw adapter.unbridged("ProjectileLaunch view.setShooter (the"
                + " pending shot carries no thrower mutation arm — the"
                + " engine's launch walk owns the thrower)");
    }

    @Override
    public void _INVALID_setShooter(org.bukkit.entity.LivingEntity shooter) {
        throw adapter.unbridged("ProjectileLaunch view.setShooter");
    }

    @Override
    public boolean doesBounce() {
        // The engine's projectiles never bounce — no row exists (the
        // honest fresh-body answer, the reference's own default).
        return false;
    }

    @Override
    public void setBounce(boolean bounce) {
        throw adapter.unbridged("ProjectileLaunch view.setBounce (the"
                + " engine's projectile bodies carry no bounce row)");
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
        return null; // the engine's projectile bodies carry no name row
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
        return adapter.unbridged("ProjectileLaunch view." + what
                + " (the engine's launch gate fires before the body exists"
                + " — the launch facts alone are carried)");
    }

    private UnsupportedOperationException pendingMutation(String what) {
        return adapter.unbridged("ProjectileLaunch view." + what
                + " (nothing exists to mutate — the native"
                + " ProjectileSpawnEvent's own cancel arm is the gate's"
                + " one verb)");
    }
}
