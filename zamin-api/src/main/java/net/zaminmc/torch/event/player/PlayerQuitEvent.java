package net.zaminmc.torch.event.player;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Event;

/**
 * A player is leaving (the §27.7 observation at the disconnect walk).
 *
 * <p>Fires on the disconnecting connection's context right before the
 * registry drops the session — handlers still see live state through
 * the handle. The event is not cancellable; the disconnect is already
 * authoritative. The vanilla yellow quit broadcast ("{@code <name> left
 * the game}", the reference's {@code multiplayer.player.left}) is
 * mutable: {@code null} suppresses the broadcast.</p>
 */
public final class PlayerQuitEvent implements Event {

    private final Player player;
    private String quitMessage;

    public PlayerQuitEvent(Player player, String quitMessage) {
        this.player = player;
        this.quitMessage = quitMessage;
    }

    /** The leaving player (the session is still registered). */
    public Player player() {
        return player;
    }

    /**
     * The line that will broadcast to the remaining players (yellow),
     * or {@code null} to suppress the broadcast.
     */
    public String quitMessage() {
        return quitMessage;
    }

    /** Sets the broadcast line ({@code null} suppresses it). */
    public void quitMessage(String message) {
        this.quitMessage = message;
    }
}
