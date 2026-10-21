package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.bukkit.world.TorchWorld;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.UUID;

/**
 * The entity-view family's shared base (the §28.7 entity adapters —
 * slice 3): the {@link org.bukkit.entity.Entity} surface every view
 * carries, with the honest arms implemented once and every
 * not-yet-modeled arm failing loudly through the adapter's {@code
 * unbridged()} refusal (§27.9 — a recorded limitation with a deliberate
 * message, never a silent wrong answer).
 *
 * <p><b>The two view shapes (the slice's own law):</b> the LIVE views
 * wrap an engine body that exists (the {@link TorchMob} over a {@code
 * MobEntity} — the damage and death bridges); the PENDING views wrap
 * one spawn's facts — the engine's §27.7 spawn gates fire BEFORE the
 * body joins the world (no entity id is consumed — the engine's own,
 * cleaner-than-the-reference contract), so a legacy spawn event's
 * entity can only ever be the spawn facts (kind + position). The
 * pending views answer those facts honestly and refuse the body reads
 * loudly, each refusal naming the engine's own route. The reference
 * fires its spawn events over the constructed body — the divergence is
 * the recorded §27.9 note on every pending view.</p>
 *
 * <p><b>Wrapper identity (§27.5):</b> the live views are cached by the
 * adapter (one view per body — identity-stable for plugins); the
 * pending views are per-event snapshots (the spawn facts are immutable
 * — no identity semantics exist for a body that does not exist yet).</p>
 *
 * <p><b>The shared honest arms:</b> the context reads ({@code
 * getServer}), the command-sender family (an entity is not a chat
 * recipient — the reference's own 1.8.8 shape answers the sends with
 * the silent no-op and the permissions with false), the metadata
 * family (no adapter metadata store is modeled — the loud refusal), and
 * the {@code spigot()} chain (not modeled — the loud refusal).</p>
 */
public abstract class TorchEntityView implements Entity {

    /** The adapter every arm routes through (the composition root). */
    protected final BukkitAdapter adapter;

    protected TorchEntityView(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------ the per-view hooks

    /** The view's 1.8.8 type row (the EntityTypeMap's own answer). */
    protected abstract org.bukkit.entity.EntityType bukkitType();

    /**
     * The view's world (the live views resolve the body's dimension; the
     * pending views resolve the engine's spawn world — see each view's
     * own §27.9 note).
     */
    protected abstract TorchWorld viewWorld();

    /** The view's position (the live body's feet, or the spawn fact). */
    protected abstract net.zaminmc.torch.util.Position viewPosition();

    /**
     * The custom name the engine body carries, or null (the pending
     * views answer null — a body that does not exist carries no name).
     */
    protected String customName() {
        return null;
    }

    /** The body's name-visible row (the pending views answer false). */
    protected boolean customNameVisible() {
        return false;
    }

    /** The view's display name (the custom name, else the type's own name). */
    protected final String displayName() {
        String custom = customName();
        return custom != null ? custom : bukkitType().getName();
    }

    // ------------------------------------------------------------------ the shared Entity surface

    @Override
    public Server getServer() {
        return adapter.bukkitServer();
    }

    @Override
    public Location getLocation() {
        net.zaminmc.torch.util.Position at = viewPosition();
        return new Location(viewWorld(), at.x(), at.y(), at.z());
    }

    @Override
    public Location getLocation(Location loc) {
        if (loc != null) {
            net.zaminmc.torch.util.Position at = viewPosition();
            loc.setWorld(viewWorld());
            loc.setX(at.x());
            loc.setY(at.y());
            loc.setZ(at.z());
        }
        return loc;
    }

    @Override
    public org.bukkit.World getWorld() {
        return viewWorld();
    }

    @Override
    public org.bukkit.entity.EntityType getType() {
        return bukkitType();
    }

    @Override
    public String getCustomName() {
        return customName();
    }

    @Override
    public boolean isCustomNameVisible() {
        return customNameVisible();
    }

    // ------------------------------------------------------------------ the command-sender family (the reference's own entity shape)

    @Override
    public String getName() {
        // CraftBukkit's own CraftEntity.getName answers the custom name
        // when the body carries one, else the kind's name — the same law.
        return displayName();
    }

    @Override
    public void sendMessage(String message) {
        // The reference's own 1.8.8 shape: an entity is not a chat
        // recipient — CraftEntity's sends are silent no-ops.
    }

    @Override
    public void sendMessage(String[] messages) {
        // The array form of the same no-op (the reference's own shape).
    }

    @Override
    public boolean isPermissionSet(String name) {
        return false; // an entity carries no permission attachment in this slice
    }

    @Override
    public boolean isPermissionSet(org.bukkit.permissions.Permission perm) {
        return false;
    }

    @Override
    public boolean hasPermission(String name) {
        return false; // the 1.8.8 default: an entity holds no permission
    }

    @Override
    public boolean hasPermission(org.bukkit.permissions.Permission perm) {
        return false;
    }

    @Override
    public java.util.Set<org.bukkit.permissions.PermissionAttachmentInfo> getEffectivePermissions() {
        // An entity carries no permission attachments in this slice —
        // the honest empty view.
        return java.util.Set.of();
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, String name, boolean value) {
        throw adapter.unbridged("Permissible.addAttachment (an entity"
                + " carries no permission attachment in this slice)");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin) {
        throw adapter.unbridged("Permissible.addAttachment");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, String name, boolean value, int ticks) {
        throw adapter.unbridged("Permissible.addAttachment");
    }

    @Override
    public org.bukkit.permissions.PermissionAttachment addAttachment(
            org.bukkit.plugin.Plugin plugin, int ticks) {
        throw adapter.unbridged("Permissible.addAttachment");
    }

    @Override
    public void removeAttachment(
            org.bukkit.permissions.PermissionAttachment attachment) {
        throw adapter.unbridged("Permissible.removeAttachment");
    }

    @Override
    public void recalculatePermissions() {
        // Nothing to recalculate — the entity carries no attachment (the
        // reference's own no-op shape for an attachment-less permissible).
    }

    @Override
    public boolean isOp() {
        return false; // an entity is never an operator
    }

    @Override
    public void setOp(boolean value) {
        throw adapter.unbridged("Entity.setOp (an entity carries no operator row)");
    }

    // ------------------------------------------------------------------ the metadata family (the loud refusals)

    @Override
    public void setMetadata(String metadataKey, org.bukkit.metadata.MetadataValue newMetadataValue) {
        throw adapter.unbridged("Entity.setMetadata (no adapter metadata store is modeled)");
    }

    @Override
    public List<org.bukkit.metadata.MetadataValue> getMetadata(String metadataKey) {
        throw adapter.unbridged("Entity.getMetadata (no adapter metadata store is modeled)");
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        throw adapter.unbridged("Entity.hasMetadata (no adapter metadata store is modeled)");
    }

    @Override
    public void removeMetadata(String metadataKey, org.bukkit.plugin.Plugin owningPlugin) {
        throw adapter.unbridged("Entity.removeMetadata (no adapter metadata store is modeled)");
    }

    // ------------------------------------------------------------------ the not-yet-modeled family (the loud refusals)

    @Override
    public void playEffect(org.bukkit.EntityEffect type) {
        throw adapter.unbridged("Entity.playEffect");
    }

    @Override
    public Entity.Spigot spigot() {
        throw adapter.unbridged("Entity.spigot (the spigot() chain is not modeled)");
    }

    @Override
    public List<Entity> getNearbyEntities(double x, double y, double z) {
        // The engine exposes per-family population views (mobs, items,
        // projectiles, vehicles) — no unified box census exists, and a
        // partial census would be a wrong answer. The §27.9 note.
        throw adapter.unbridged("Entity.getNearbyEntities (the engine's"
                + " population views are family-scoped — no unified box"
                + " census exists)");
    }

    @Override
    public UUID getUniqueId() {
        // The per-view law: the live views answer the body's row, the
        // pending views refuse (no body exists to own one).
        return viewUniqueId();
    }

    /** The per-view unique-id answer (the live body's row, or the refusal). */
    protected abstract UUID viewUniqueId();
}
