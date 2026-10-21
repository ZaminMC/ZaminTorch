package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;

import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * The pending-spawn {@link org.bukkit.entity.LivingEntity} view — the
 * {@code CreatureSpawnEvent} bridge's entity (the §28.7 entity
 * adapters — slice 3).
 *
 * <p><b>Why a pending shape (the recorded §27.9 divergence):</b> the
 * engine's {@code MobSpawnEvent} fires BEFORE the body joins the world
 * — no entity id is consumed, no {@code MobEntity} exists (the engine's
 * own, cleaner-than-the-reference contract: a cancelled spawn consumes
 * nothing). The reference's event family fires over the constructed
 * body instead. The legacy form demands a {@code LivingEntity}
 * nonetheless, so this view wraps the spawn facts the native event
 * carries — the kind and the position — and answers them honestly:
 * {@code getType()}, {@code getLocation()}, {@code getWorld()}, the
 * name rows. Every BODY read fails loudly (§27.9) with the message
 * naming the engine's own route — the id row ("no id is consumed yet —
 * the engine's own gate contract"), the health rows, the velocity
 * rows, and every mutation ("nothing exists to mutate — spawn through
 * the engine's own surface and read the live view"). Never a fake
 * body, never an invented row.</p>
 *
 * <p><b>The world row (the second recorded note):</b> the native event
 * carries a bare position — no world row. The engine's one shared
 * spawn-manager family is overworld-scoped at gate time (a nether body
 * receives its dimension only after the gate admits it), so the view
 * resolves {@code getWorld()} to the adapter's view of the overworld.
 * The position itself is carried exactly as the engine stated it.</p>
 *
 * <p><b>Per-event snapshot:</b> the spawn facts are immutable — a new
 * view per dispatch, no identity semantics (§27.5: no body exists to
 * hold identity).</p>
 */
public final class TorchPendingMob extends TorchEntityView
        implements org.bukkit.entity.LivingEntity {

    private final MobType type;
    private final Position position;

    public TorchPendingMob(BukkitAdapter adapter, MobType type, Position position) {
        super(adapter);
        this.type = type;
        this.position = position;
    }

    // ------------------------------------------------------------------ the hooks (the spawn facts)

    @Override
    protected org.bukkit.entity.EntityType bukkitType() {
        return EntityTypeMap.of(type);
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
        throw adapter.unbridged("CreatureSpawn.getEntityId/getUniqueId"
                + " (the engine's spawn gate fires before the body exists"
                + " — no id is consumed yet; spawn through the engine's"
                + " own surface and read the live view)");
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

    // ------------------------------------------------------------------ Damageable (the pending law)

    @Override
    public void damage(double amount) {
        throw pendingMutation("damage");
    }

    @Override
    public void damage(double amount, org.bukkit.entity.Entity source) {
        throw pendingMutation("damage");
    }

    @Override
    public double getHealth() {
        throw pendingBodyRead("getHealth");
    }

    @Override
    public void setHealth(double health) {
        throw pendingMutation("setHealth");
    }

    @Override
    public double getMaxHealth() {
        throw pendingBodyRead("getMaxHealth");
    }

    @Override
    public void setMaxHealth(double health) {
        throw pendingMutation("setMaxHealth");
    }

    @Override
    public void resetMaxHealth() {
        throw pendingMutation("resetMaxHealth");
    }

    @Override
    public void setLastDamageCause(org.bukkit.event.entity.EntityDamageEvent event) {
        throw pendingMutation("setLastDamageCause");
    }

    @Override
    public org.bukkit.event.entity.EntityDamageEvent getLastDamageCause() {
        throw pendingBodyRead("getLastDamageCause");
    }

    // ------------------------------------------------------------------ LivingEntity (the pending law)

    @Override
    public double getEyeHeight() {
        throw pendingBodyRead("getEyeHeight");
    }

    @Override
    public double getEyeHeight(boolean ignoreSneaking) {
        throw pendingBodyRead("getEyeHeight");
    }

    @Override
    public Location getEyeLocation() {
        throw pendingBodyRead("getEyeLocation");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLineOfSight(
            java.util.HashSet<java.lang.Byte> transparent, int maxDistance) {
        throw pendingBodyRead("getLineOfSight");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLineOfSight(
            java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw pendingBodyRead("getLineOfSight");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(
            java.util.HashSet<java.lang.Byte> transparent, int maxDistance) {
        throw pendingBodyRead("getTargetBlock");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(
            java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw pendingBodyRead("getTargetBlock");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLastTwoTargetBlocks(
            java.util.HashSet<java.lang.Byte> transparent, int maxDistance) {
        throw pendingBodyRead("getLastTwoTargetBlocks");
    }

    @Override
    public java.util.List<org.bukkit.block.Block> getLastTwoTargetBlocks(
            java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw pendingBodyRead("getLastTwoTargetBlocks");
    }

    @Override
    public org.bukkit.entity.Egg throwEgg() {
        throw pendingMutation("throwEgg");
    }

    @Override
    public org.bukkit.entity.Snowball throwSnowball() {
        throw pendingMutation("throwSnowball");
    }

    @Override
    public org.bukkit.entity.Arrow shootArrow() {
        throw pendingMutation("shootArrow");
    }

    @Override
    public int getRemainingAir() {
        throw pendingBodyRead("getRemainingAir");
    }

    @Override
    public void setRemainingAir(int ticks) {
        throw pendingMutation("setRemainingAir");
    }

    @Override
    public int getMaximumAir() {
        throw pendingBodyRead("getMaximumAir");
    }

    @Override
    public void setMaximumAir(int ticks) {
        throw pendingMutation("setMaximumAir");
    }

    @Override
    public int getMaximumNoDamageTicks() {
        throw pendingBodyRead("getMaximumNoDamageTicks");
    }

    @Override
    public void setMaximumNoDamageTicks(int ticks) {
        throw pendingMutation("setMaximumNoDamageTicks");
    }

    @Override
    public double getLastDamage() {
        throw pendingBodyRead("getLastDamage");
    }

    @Override
    public void setLastDamage(double damage) {
        throw pendingMutation("setLastDamage");
    }

    @Override
    public int getNoDamageTicks() {
        throw pendingBodyRead("getNoDamageTicks");
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        throw pendingMutation("setNoDamageTicks");
    }

    @Override
    public org.bukkit.entity.Player getKiller() {
        throw pendingBodyRead("getKiller");
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect) {
        throw pendingMutation("addPotionEffect");
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect, boolean force) {
        throw pendingMutation("addPotionEffect");
    }

    @Override
    public boolean addPotionEffects(
            java.util.Collection<org.bukkit.potion.PotionEffect> effects) {
        throw pendingMutation("addPotionEffects");
    }

    @Override
    public boolean hasPotionEffect(org.bukkit.potion.PotionEffectType type) {
        throw pendingBodyRead("hasPotionEffect");
    }

    @Override
    public void removePotionEffect(org.bukkit.potion.PotionEffectType type) {
        throw pendingMutation("removePotionEffect");
    }

    @Override
    public java.util.Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects() {
        throw pendingBodyRead("getActivePotionEffects");
    }

    @Override
    public boolean hasLineOfSight(org.bukkit.entity.Entity other) {
        throw pendingBodyRead("hasLineOfSight");
    }

    @Override
    public boolean getRemoveWhenFarAway() {
        throw pendingBodyRead("getRemoveWhenFarAway");
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        throw pendingMutation("setRemoveWhenFarAway");
    }

    @Override
    public org.bukkit.inventory.EntityEquipment getEquipment() {
        throw pendingBodyRead("getEquipment");
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        throw pendingMutation("setCanPickupItems");
    }

    @Override
    public boolean getCanPickupItems() {
        throw pendingBodyRead("getCanPickupItems");
    }

    @Override
    public boolean isLeashed() {
        throw pendingBodyRead("isLeashed");
    }

    @Override
    public org.bukkit.entity.Entity getLeashHolder() {
        throw pendingBodyRead("getLeashHolder");
    }

    @Override
    public boolean setLeashHolder(org.bukkit.entity.Entity holder) {
        throw pendingMutation("setLeashHolder");
    }

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile) {
        throw pendingMutation("launchProjectile");
    }

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile, Vector velocity) {
        throw pendingMutation("launchProjectile");
    }

    // ------------------------------------------------------------------ the deprecated _INVALID_ rows

    @Override
    public void _INVALID_damage(int amount) {
        damage((double) amount);
    }

    @Override
    public void _INVALID_damage(int amount, org.bukkit.entity.Entity source) {
        damage((double) amount, source);
    }

    @Override
    public int _INVALID_getHealth() {
        return (int) getHealth();
    }

    @Override
    public void _INVALID_setHealth(int health) {
        setHealth((double) health);
    }

    @Override
    public int _INVALID_getMaxHealth() {
        return (int) getMaxHealth();
    }

    @Override
    public void _INVALID_setMaxHealth(int health) {
        setMaxHealth((double) health);
    }

    @Override
    public int _INVALID_getLastDamage() {
        return (int) getLastDamage();
    }

    @Override
    public void _INVALID_setLastDamage(int damage) {
        setLastDamage((double) damage);
    }

    @Override
    public void setCustomName(String name) {
        throw pendingMutation("setCustomName");
    }

    @Override
    public String getCustomName() {
        return null; // no body exists — no name row
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
        return adapter.unbridged("CreatureSpawn view." + what
                + " (the engine's spawn gate fires before the body exists"
                + " — the spawn facts alone are carried; spawn through the"
                + " engine's own surface and read the live view)");
    }

    private UnsupportedOperationException pendingMutation(String what) {
        return adapter.unbridged("CreatureSpawn view." + what
                + " (nothing exists to mutate — the engine's spawn gate"
                + " fires before the body exists; the native MobSpawnEvent's"
                + " own cancel arm is the gate's one verb)");
    }
}
