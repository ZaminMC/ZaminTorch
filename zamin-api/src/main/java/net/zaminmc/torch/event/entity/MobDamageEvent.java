package net.zaminmc.torch.event.entity;

import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.DamageCause;
import net.zaminmc.torch.event.Event;

/**
 * The engine is about to apply damage to a living entity — a mob (the
 * §27.7 damage gate).
 *
 * <p>Fires on the engine context after the dead/positive guards but
 * before the health moves, the knockback and the hurt fan-out.
 * Cancelling prevents the damage entirely: no health loss, no
 * knockback, no hurt listeners, no death. The amount is mutable — the
 * modified amount drives the whole walk. The handle is the live engine
 * entity (§27.5): reads resolve to authoritative state on the firing
 * context.</p>
 */
public final class MobDamageEvent implements Event, Cancellable {

    private final LivingEntity entity;
    private final DamageCause cause;
    private float amount;
    private boolean cancelled;

    public MobDamageEvent(LivingEntity entity, float amount, DamageCause cause) {
        this.entity = entity;
        this.amount = amount;
        this.cause = cause;
    }

    /** The mob about to take the damage (the live handle). */
    public LivingEntity entity() {
        return entity;
    }

    /** The damage amount the engine is about to apply (mutable). */
    public float amount() {
        return amount;
    }

    /** Overrides the damage amount (the hurt, knockback and death check use it). */
    public void amount(float amount) {
        this.amount = amount;
    }

    /** The damage source classification. */
    public DamageCause cause() {
        return cause;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
