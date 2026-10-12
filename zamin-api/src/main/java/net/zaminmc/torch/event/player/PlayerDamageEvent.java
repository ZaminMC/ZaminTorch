package net.zaminmc.torch.event.player;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.DamageCause;
import net.zaminmc.torch.event.Event;

/**
 * The engine is about to apply damage to a player (the §27.7 damage
 * gate).
 *
 * <p>Fires on the engine context after the survival guards (the
 * already-dead and the invulnerable-mode checks) but before the health
 * moves. Cancelling prevents the damage entirely: no health loss, no
 * exhaustion charge, no death check. The amount is the post-armor,
 * post-enchantment value the engine computed — the same value the
 * health walk would apply — and is mutable: the modified amount drives
 * the hurt, the exhaustion and the death check.</p>
 */
public final class PlayerDamageEvent implements Event, Cancellable {

    private final Player player;
    private final DamageCause cause;
    private float amount;
    private boolean cancelled;

    public PlayerDamageEvent(Player player, float amount, DamageCause cause) {
        this.player = player;
        this.amount = amount;
        this.cause = cause;
    }

    /** The player about to take the damage. */
    public Player player() {
        return player;
    }

    /** The damage amount the engine is about to apply (mutable). */
    public float amount() {
        return amount;
    }

    /** Overrides the damage amount (the hurt, exhaustion and death check use it). */
    public void amount(float amount) {
        this.amount = amount;
    }

    /** The damage source classification (the armor/enchantment branch family). */
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
