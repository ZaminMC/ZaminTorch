package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.entity.ProjectileKind;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.util.Position;

/**
 * A projectile is about to launch (§27.7's observe/cancel gate over the
 * projectile family — the bow shot, the snowball throw, the splash
 * potion, the dispenser's projectile arms, the blaze and ghast volleys
 * all ride the same chokepoint).
 *
 * <p>Fires on the world's simulation context BEFORE the body joins the
 * world. Cancelling prevents the launch entirely: no entity id is
 * consumed, no body spawns, no wire frame leaves — the shot is
 * swallowed.</p>
 */
public final class ProjectileSpawnEvent implements Event, Cancellable {

    private final ProjectileKind kind;
    private final int throwerEntityId;
    private final Position position;
    private boolean cancelled;

    public ProjectileSpawnEvent(ProjectileKind kind, int throwerEntityId, Position position) {
        this.kind = kind;
        this.throwerEntityId = throwerEntityId;
        this.position = position;
    }

    /** The projectile kind (the public family's own constant). */
    public ProjectileKind kind() {
        return kind;
    }

    /** The thrower's engine entity id (-1 when the shot has no body behind it). */
    public int throwerEntityId() {
        return throwerEntityId;
    }

    /** The launch position in world space. */
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
        return "ProjectileSpawn";
    }
}
