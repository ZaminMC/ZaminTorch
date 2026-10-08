package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.player.PlayerSession;

/**
 * The origin of a command: an online player or the console (the historical
 * ICommandSender). Commands receive this, never raw wires; feedback flows back
 * through {@link #sendMessage}.
 */
public interface CommandSender {

    /** The display name of the sender ("Server" for the console). */
    String name();

    /** Delivers a command feedback line to this sender. */
    void sendMessage(String message);

    /** The permission level (0 = player, 4 = console-equivalent owner). */
    int opLevel();

    /** The sending player, or null when the command came from the console. */
    PlayerSession player();
}
