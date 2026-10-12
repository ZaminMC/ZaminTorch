package net.zaminmc.torch.event.player;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.event.Cancellable;
import net.zaminmc.torch.event.Event;

/**
 * A player submitted a chat message.
 *
 * <p>Fires on the engine context before the broadcast and before any chat
 * logging. Cancelling prevents the delivery: no other player sees the
 * message. A cancelled command (a message starting with {@code /} never
 * reaches this event — commands dispatch through their own path).</p>
 */
public final class PlayerChatEvent implements Event, Cancellable {

    private final Player player;
    private final String message;
    private boolean cancelled;

    public PlayerChatEvent(Player player, String message) {
        this.player = player;
        this.message = message;
    }

    /** The sending player. */
    public Player player() {
        return player;
    }

    /** The raw submitted text, trimmed of the wire framing. */
    public String message() {
        return message;
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
        return "PlayerChat";
    }
}
