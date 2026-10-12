package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A mob is about to spawn.
 *
 * <p>Fires on the world's simulation context before the entity joins the
 * world — from every spawn source: the natural cycles, the structure
 * populators, the spawner blocks and engine API calls alike. Cancelling
 * prevents the spawn entirely; no entity id is consumed. This event
 * covers engine mobs ({@code MobType}); the projectile, item-entity and
 * vehicle families carry their own lifecycle events as they gain public
 * handles.</p>
 */
public final class MobSpawnEvent implements Event, Cancellable {

    private final String mobType;
    private final Position position;
    private boolean cancelled;

    public MobSpawnEvent(String mobType, Position position) {
        this.mobType = mobType;
        this.position = position;
    }

    /** The engine mob type identifier (for example {@code minecraft:zombie}). */
    public String mobType() {
        return mobType;
    }

    /** The spawn position in world space. */
    public Position position() {
        return position;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public String eventName() {
        return "MobSpawn";
    }
}
