package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.util.Position;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The legacy {@link org.bukkit.entity.LivingEntity} view over one LIVE
 * engine {@link MobEntity} (the §28.7 entity adapters — slice 3): the
 * §27.5 handle law — every read resolves to the CURRENT authoritative
 * body state, never a copied mob. The damage and death bridges carry
 * this view (the engine's {@code MobDamageEvent}/{@code MobDeathEvent}
 * fire over the live body); the pending projectile view also resolves
 * a mob thrower through it.
 *
 * <p><b>The bridged subset (what the five families' listeners actually
 * touch, plus the body's own public rows):</b> identity (the engine id,
 * the deterministic UUID, the kind's {@link org.bukkit.entity.EntityType}
 * row through {@link EntityTypeMap}), the live state reads (position,
 * world-by-dimension, velocity, health, max health, fire clock, air,
 * fall distance, age, ground, dead/valid, the custom-name pair, the
 * rider seat, the killer, the leash pair, the despawn pair, the pickup
 * flag), the §27.6-routed mutations the engine owns public verbs for
 * (velocity, teleport, fire clock, custom names, health, air, fall
 * distance, the despawn/remove, the persistence pair), and the
 * last-damage rows as adapter-local presentation state (the engine
 * carries no last-damage row — the native damage events are the
 * observation surface; the same law as the player view's display
 * name).</p>
 *
 * <p><b>Every unbridged arm fails loudly</b> (§27.9) with the message
 * naming the engine's own route — the potion family (the engine's
 * effect store is the live potion system), the sight family (the AI's
 * walks), the equipment view (the adapter's inventory family is the
 * parallel slice), the eye heights, the launch verbs (the engine's own
 * projectile launch surface), and every row the engine simply does not
 * carry (the i-frame window, the max-air write, the max-health
 * writes).</p>
 */
public final class TorchMob extends TorchEntityView
        implements org.bukkit.entity.LivingEntity {

    private final MobEntity mob;

    /** The adapter-local last-damage rows (the engine carries none — see class javadoc). */
    private EntityDamageEvent lastDamageCause;
    private double lastDamage;

    public TorchMob(BukkitAdapter adapter, MobEntity mob) {
        super(adapter);
        this.mob = mob;
    }

    /** The engine body this view resolves to (§27.5's handle law). */
    public MobEntity mob() {
        return mob;
    }

    // ------------------------------------------------------------------ the hooks

    @Override
    protected org.bukkit.entity.EntityType bukkitType() {
        return EntityTypeMap.of(mob.type());
    }

    @Override
    protected TorchWorld viewWorld() {
        // The body carries the dimension row (0 overworld, -1 nether, 1
        // end) — the adapter's world views resolve it.
        return switch (mob.dimension()) {
            case -1 -> adapter.worldView(adapter.engine().netherWorld());
            case 1 -> adapter.worldView(adapter.engine().endWorld());
            default -> adapter.worldView(adapter.engine().world());
        };
    }

    @Override
    protected Position viewPosition() {
        return mob.position();
    }

    @Override
    protected String customName() {
        return mob.customName();
    }

    @Override
    protected UUID viewUniqueId() {
        return mob.getUuid();
    }

    // ------------------------------------------------------------------ identity + the live state reads

    @Override
    public int getEntityId() {
        return mob.entityId();
    }

    @Override
    public org.bukkit.World getWorld() {
        return viewWorld();
    }

    @Override
    public boolean isDead() {
        return mob.dead();
    }

    @Override
    public boolean isValid() {
        return !mob.dead(); // the body's own alive read
    }

    @Override
    public boolean isOnGround() {
        return mob.onGround();
    }

    @Override
    public int getTicksLived() {
        return mob.age();
    }

    @Override
    public Vector getVelocity() {
        return new Vector(mob.velocityX(), mob.velocityY(), mob.velocityZ());
    }

    @Override
    public int getFireTicks() {
        return mob.fireTicks();
    }

    @Override
    public int getMaxFireTicks() {
        // The engine's own ignite window (the burn clock's ceiling).
        return MobEntity.FIRE_TICKS;
    }

    @Override
    public float getFallDistance() {
        return mob.fallDistance();
    }

    @Override
    public Entity getPassenger() {
        // The seat read: the rider's engine id resolved against the
        // adapter's own views (a session or another mob).
        int rider = mob.riderId();
        if (rider < 0) {
            return null;
        }
        Entity session = adapter.entityByEngineId(rider);
        return session != null ? session : null;
    }

    @Override
    public boolean isEmpty() {
        return !mob.hasRider();
    }

    // ------------------------------------------------------------------ the §27.6-routed mutations (the engine's own public verbs)

    @Override
    public void setVelocity(Vector velocity) {
        route(() -> mob.setVelocity(velocity.getX(), velocity.getY(), velocity.getZ()));
    }

    @Override
    public void setFireTicks(int ticks) {
        route(() -> mob.setFireTicks(ticks));
    }

    @Override
    public void setFallDistance(float distance) {
        route(() -> mob.setFallDistance(distance));
    }

    @Override
    public boolean teleport(Location location) {
        return teleportTo(location);
    }

    @Override
    public boolean teleport(Location location, PlayerTeleportEvent.TeleportCause cause) {
        // The engine's teleport verb carries no cause row — the legacy
        // cause is the annotation the reference's event family owns; the
        // verb applies either way (the §27.9 note).
        return teleportTo(location);
    }

    @Override
    public boolean teleport(Entity destination) {
        return teleportTo(destination.getLocation());
    }

    @Override
    public boolean teleport(Entity destination, PlayerTeleportEvent.TeleportCause cause) {
        return teleportTo(destination.getLocation());
    }

    @Override
    public void remove() {
        route(mob::despawn); // the engine's public despawn path
    }

    @Override
    public void setCustomName(String name) {
        route(() -> mob.setCustomName(name));
    }

    @Override
    public void setCustomNameVisible(boolean visible) {
        route(() -> mob.setCustomNameVisible(visible));
    }

    @Override
    public boolean isCustomNameVisible() {
        return mob.customNameVisible();
    }

    @Override
    public void damage(double amount) {
        route(() -> hurt(amount, 0.0));
    }

    @Override
    public void damage(double amount, Entity source) {
        // The attacker's yaw drives the knockback direction — the view
        // knows its own body's yaw; a foreign view's call rides flat.
        double yaw = source instanceof TorchMob other ? other.mob().yaw() : 0.0;
        route(() -> hurt(amount, yaw));
    }

    @Override
    public void setHealth(double health) {
        double clamped = Math.max(0.0, Math.min(health, mob.maxHealth()));
        route(() -> mob.setHealthCarried((float) clamped));
    }

    @Override
    public void setRemainingAir(int ticks) {
        route(() -> mob.setAir(ticks));
    }

    @Override
    public void setRemoveWhenFarAway(boolean remove) {
        route(() -> mob.setPersistent(!remove)); // the engine's persistence row
    }

    @Override
    public void setCanPickupItems(boolean pickup) {
        route(() -> mob.setCanPickUpLoot(pickup));
    }

    // ------------------------------------------------------------------ Damageable reads

    @Override
    public double getHealth() {
        return mob.health();
    }

    @Override
    public double getMaxHealth() {
        return mob.maxHealth();
    }

    @Override
    public int getRemainingAir() {
        return mob.air();
    }

    @Override
    public int getMaximumAir() {
        // The engine's own breath ceiling (the restore row's constant).
        return 300;
    }

    @Override
    public boolean getRemoveWhenFarAway() {
        return mob.canDespawn();
    }

    @Override
    public boolean getCanPickupItems() {
        return mob.canPickUpLoot();
    }

    @Override
    public Player getKiller() {
        var session = mob.playerKiller();
        return session == null ? null : adapter.player(session);
    }

    // ------------------------------------------------------------------ the last-damage rows (adapter-local presentation state)

    @Override
    public void setLastDamageCause(EntityDamageEvent event) {
        // Wrapper-local presentation state (the engine carries no
        // last-damage row — the native MobDamageEvent family is the
        // observation surface; the player view's displayName precedent).
        this.lastDamageCause = event;
    }

    @Override
    public EntityDamageEvent getLastDamageCause() {
        return lastDamageCause;
    }

    @Override
    public double getLastDamage() {
        return lastDamage;
    }

    @Override
    public void setLastDamage(double damage) {
        this.lastDamage = damage; // the same adapter-local law
    }

    /** The bridge's own stash (the dispatch walk files the legacy form here). */
    void lastDamageCause(EntityDamageEvent event) {
        this.lastDamageCause = event;
        this.lastDamage = event.getDamage();
    }

    // ------------------------------------------------------------------ the leash pair

    @Override
    public boolean isLeashed() {
        return mob.isLeashed();
    }

    @Override
    public Entity getLeashHolder() {
        var session = mob.leashHolderPlayer();
        if (session != null) {
            return adapter.player(session);
        }
        if (mob.leashHolderKnot() != null) {
            // The engine's leash knots are the leash system's own bodies
            // — no legacy view is modeled for them in this slice.
            throw adapter.unbridged("LivingEntity.getLeashHolder (the"
                    + " holder is an engine leash knot — the leash"
                    + " system's own body)");
        }
        throw new IllegalStateException("not leashed");
    }

    // ------------------------------------------------------------------ the unbridged family (§27.9's loud refusals)

    @Override
    public void setMaxHealth(double health) {
        throw adapter.unbridged("LivingEntity.setMaxHealth (the kind's"
                + " attribute is engine-owned)");
    }

    @Override
    public void resetMaxHealth() {
        throw adapter.unbridged("LivingEntity.resetMaxHealth");
    }

    @Override
    public void setMaximumAir(int ticks) {
        throw adapter.unbridged("LivingEntity.setMaximumAir (the breath"
                + " ceiling is the engine's own constant)");
    }

    @Override
    public int getMaximumNoDamageTicks() {
        throw adapter.unbridged("LivingEntity.getMaximumNoDamageTicks"
                + " (the engine's damage walk carries no general i-frame"
                + " window — the per-source cadence clocks are the"
                + " engine's own law)");
    }

    @Override
    public void setMaximumNoDamageTicks(int ticks) {
        throw adapter.unbridged("LivingEntity.setMaximumNoDamageTicks");
    }

    @Override
    public int getNoDamageTicks() {
        throw adapter.unbridged("LivingEntity.getNoDamageTicks (the"
                + " engine's damage walk carries no general i-frame window)");
    }

    @Override
    public void setNoDamageTicks(int ticks) {
        throw adapter.unbridged("LivingEntity.setNoDamageTicks");
    }

    @Override
    public double getEyeHeight() {
        throw adapter.unbridged("LivingEntity.getEyeHeight (the engine"
                + " carries no eye-height row — the body box alone is public)");
    }

    @Override
    public double getEyeHeight(boolean ignoreSneaking) {
        throw adapter.unbridged("LivingEntity.getEyeHeight");
    }

    @Override
    public Location getEyeLocation() {
        throw adapter.unbridged("LivingEntity.getEyeLocation (no eye-height row)");
    }

    @Override
    public List<org.bukkit.block.Block> getLineOfSight(java.util.HashSet<Byte> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getLineOfSight (the AI's"
                + " sight walks are not public reads)");
    }

    @Override
    public List<org.bukkit.block.Block> getLineOfSight(java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getLineOfSight");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(java.util.HashSet<Byte> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getTargetBlock");
    }

    @Override
    public org.bukkit.block.Block getTargetBlock(java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getTargetBlock");
    }

    @Override
    public List<org.bukkit.block.Block> getLastTwoTargetBlocks(java.util.HashSet<Byte> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getLastTwoTargetBlocks");
    }

    @Override
    public List<org.bukkit.block.Block> getLastTwoTargetBlocks(java.util.Set<org.bukkit.Material> transparent, int maxDistance) {
        throw adapter.unbridged("LivingEntity.getLastTwoTargetBlocks");
    }

    @Override
    public boolean hasLineOfSight(Entity other) {
        throw adapter.unbridged("LivingEntity.hasLineOfSight");
    }

    @Override
    public org.bukkit.entity.Egg throwEgg() {
        throw adapter.unbridged("LivingEntity.throwEgg (the engine's own"
                + " projectiles().launch surface is the route)");
    }

    @Override
    public org.bukkit.entity.Snowball throwSnowball() {
        throw adapter.unbridged("LivingEntity.throwSnowball (the engine's"
                + " own projectiles().launch surface is the route)");
    }

    @Override
    public org.bukkit.entity.Arrow shootArrow() {
        throw adapter.unbridged("LivingEntity.shootArrow (the engine's"
                + " own projectiles().launch surface is the route)");
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect) {
        throw adapter.unbridged("LivingEntity.addPotionEffect (the"
                + " engine's effect store is the live potion system)");
    }

    @Override
    public boolean addPotionEffect(org.bukkit.potion.PotionEffect effect, boolean force) {
        throw adapter.unbridged("LivingEntity.addPotionEffect");
    }

    @Override
    public boolean addPotionEffects(Collection<org.bukkit.potion.PotionEffect> effects) {
        throw adapter.unbridged("LivingEntity.addPotionEffects");
    }

    @Override
    public boolean hasPotionEffect(org.bukkit.potion.PotionEffectType type) {
        throw adapter.unbridged("LivingEntity.hasPotionEffect");
    }

    @Override
    public void removePotionEffect(org.bukkit.potion.PotionEffectType type) {
        throw adapter.unbridged("LivingEntity.removePotionEffect");
    }

    @Override
    public Collection<org.bukkit.potion.PotionEffect> getActivePotionEffects() {
        throw adapter.unbridged("LivingEntity.getActivePotionEffects");
    }

    @Override
    public org.bukkit.inventory.EntityEquipment getEquipment() {
        // The adapter's inventory family is the parallel slice — the
        // equipment view rides it.
        throw adapter.unbridged("LivingEntity.getEquipment (the adapter's"
                + " inventory views are the parallel slice)");
    }

    @Override
    public boolean setLeashHolder(Entity holder) {
        throw adapter.unbridged("LivingEntity.setLeashHolder (the"
                + " engine's leash attach is the leash system's own walk)");
    }

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile) {
        throw adapter.unbridged("LivingEntity.launchProjectile (the"
                + " engine's own projectiles().launch surface is the route)");
    }

    @Override
    public <T extends org.bukkit.entity.Projectile> T launchProjectile(
            Class<? extends T> projectile, Vector velocity) {
        throw adapter.unbridged("LivingEntity.launchProjectile");
    }

    @Override
    public boolean setPassenger(Entity passenger) {
        throw adapter.unbridged("Entity.setPassenger (the engine's"
                + " mounting walk owns the seat)");
    }

    @Override
    public boolean eject() {
        throw adapter.unbridged("Entity.eject (the engine's dismount walk"
                + " owns the seat)");
    }

    @Override
    public boolean isInsideVehicle() {
        throw adapter.unbridged("Entity.isInsideVehicle (the body carries"
                + " no ridden-vehicle row — only its own rider seat is public)");
    }

    @Override
    public boolean leaveVehicle() {
        throw adapter.unbridged("Entity.leaveVehicle");
    }

    @Override
    public Entity getVehicle() {
        throw adapter.unbridged("Entity.getVehicle (the body carries no"
                + " ridden-vehicle row)");
    }

    @Override
    public void setTicksLived(int value) {
        throw adapter.unbridged("Entity.setTicksLived (the age clock is engine-owned)");
    }

    // ------------------------------------------------------------------ the deprecated _INVALID_ rows (the 1.8.8 binary-compat surface)

    @Override
    public void _INVALID_damage(int amount) {
        damage((double) amount);
    }

    @Override
    public void _INVALID_damage(int amount, Entity source) {
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

    // ------------------------------------------------------------------ the routing (§27.6 — the adapter's own law)

    /**
     * The §27.6 route for one body-scoped mutation: on the owner's own
     * context it applies directly (the event handlers' context); from
     * anywhere else it travels as a nonblocking cross-owner intent to
     * the world's owner (§28.2 — never a hidden blocking wait).
     */
    private void route(Runnable action) {
        adapter.mutateWorld(adapter.engine().world(), action);
    }

    /** The engine's own semantic damage walk (the one public path). */
    private void hurt(double amount, double attackerYaw) {
        mobs().hurt(mob, (float) amount, attackerYaw,
                net.zaminmc.torch.event.DamageCause.MELEE);
    }

    private MobManager mobs() {
        return adapter.engine().mobs();
    }

    /** The teleport application (the position's own owner-routed verb). */
    private boolean teleportTo(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        Position to = new Position(location.getX(), location.getY(), location.getZ());
        // The §27.6 route: the owner context applies now (the listener
        // case); any other context travels as the cross-owner intent.
        route(() -> mob.teleport(to));
        return true;
    }
}
