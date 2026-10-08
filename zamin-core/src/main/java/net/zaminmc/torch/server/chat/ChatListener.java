package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.player.PlayerSession;

/**
 * Engine-internal chat delivery hook. The engine decides what is said to whom;
 * the adapter decides how the client renders it (§353).
 */
public interface ChatListener {

    /** A validated public chat message from a player. Simulation thread. */
    void onChatMessage(PlayerSession sender, String content);

    /** A directed system/feedback message to one player. Simulation thread. */
    void onSystemMessage(PlayerSession recipient, String content);
}
