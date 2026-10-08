package net.zaminmc.torch.server.chat;

import java.util.logging.Logger;

/**
 * The console as a command sender: full operator level, feedback to the log.
 */
public final class ConsoleSender implements CommandSender {

    private static final Logger LOGGER = Logger.getLogger(ConsoleSender.class.getName());

    @Override
    public String name() {
        return "Server";
    }

    @Override
    public void sendMessage(String message) {
        LOGGER.info(message);
    }

    @Override
    public int opLevel() {
        return 4;
    }

    @Override
    public net.zaminmc.torch.server.player.PlayerSession player() {
        return null;
    }
}
