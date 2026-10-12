package net.zaminmc.torch.event.player;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Event;

/**
 * A player finished joining (the §27.7 post-commit observation).
 *
 * <p>Fires on the joining connection's context after the session is
 * registered and fully restored (position, inventory, game mode, spawn
 * view) but before the vanilla yellow join broadcast leaves the server.
 * The event is not cancellable — the join is already authoritative —
 * but the broadcast line is mutable: setting {@link #joinMessage} to
 * {@code null} suppresses the broadcast entirely.</p>
 *
 * <p>The historical broadcast is the reference's
 * {@code multiplayer.player.joined} in yellow ("{@code <name> joined the
 * game}").</p>
 */
public final class PlayerJoinEvent implements Event {

    private final Player player;
    private String joinMessage;

    public PlayerJoinEvent(Player player, String joinMessage) {
        this.player = player;
        this.joinMessage = joinMessage;
    }

    /** The joining player (the session is fully constructed). */
    public Player player() {
        return player;
    }

    /**
     * The line that will broadcast to every online player (yellow), or
     * {@code null} to suppress the broadcast.
     */
    public String joinMessage() {
        return joinMessage;
    }

    /** Sets the broadcast line ({@code null} suppresses it). */
    public void joinMessage(String message) {
        this.joinMessage = message;
    }
}
