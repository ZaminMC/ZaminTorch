package net.zaminmc.torch.event.player;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A player's health reached zero (the §27.7 death observation with the
 * keep-inventory extension).
 *
 * <p>Fires on the engine context after the body marks dead, the effects
 * clear and the seat opens, but BEFORE the experience scatter and the
 * inventory scatter — cancelling skips both: the body still dies (the
 * respawn walk is unaffected) and keeps its items and experience, the
 * historical keepInventory rule.</p>
 *
 * <p>The death line that broadcasts to every online player is mutable;
 * {@code null} suppresses the broadcast. The vanilla line carries the
 * cause ("{@code <name> was slain by Zombie}", "{@code <name> drowned}",
 * …).</p>
 */
public final class PlayerDeathEvent implements Event, Cancellable {

    private final Player player;
    private String deathMessage;
    private boolean cancelled;

    public PlayerDeathEvent(Player player, String deathMessage) {
        this.player = player;
        this.deathMessage = deathMessage;
    }

    /** The dying player (the body is already marked dead). */
    public Player player() {
        return player;
    }

    /**
     * The line that will broadcast to every online player, or {@code null}
     * to suppress the broadcast.
     */
    public String deathMessage() {
        return deathMessage;
    }

    /** Sets the broadcast line ({@code null} suppresses it). */
    public void deathMessage(String message) {
        this.deathMessage = message;
    }

    @Override
    public boolean cancelled() {
        return cancelled;
    }

    /** Cancelling keeps the inventory: the experience and the items stay
     * on the body (no scatter, no reset). The death itself still resolves. */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
