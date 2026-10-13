package net.zaminmc.torch.event.world;

import net.zaminmc.torch.World;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.util.Position;

/**
 * An explosion is about to tear the world (§27.7's observe/cancel gate
 * over the detonation family — the creeper blast, the primed TNT, the
 * chain reaction, and every engine-driven detonation ride the same
 * walk).
 *
 * <p>Fires on the world's simulation context BEFORE any destruction:
 * the blocks are still in place, no player has been wounded, no
 * knockback has been computed. Cancelling prevents the whole
 * detonation — nothing is destroyed, nobody is hurt, no Explosion
 * packet leaves the wire (the priming body itself still leaves — the
 * fuse's end is not undone, only its consequence).</p>
 */
public final class ExplosionEvent implements Event, Cancellable {

    private final World world;
    private final Position center;
    private final float power;
    private boolean cancelled;

    public ExplosionEvent(World world, Position center, float power) {
        this.world = world;
        this.center = center;
        this.power = power;
    }

    /** The world the blast tears. */
    public World world() {
        return world;
    }

    /** The blast's center (the body's own position at the fuse's end). */
    public Position center() {
        return center;
    }

    /**
     * The blast's power — the destruction radius in blocks (the vanilla
     * creeper 3.0, the TNT 4.0).
     */
    public float power() {
        return power;
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
        return "Explosion";
    }
}
