package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.EngineTicker;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Validates and routes player chat (§351): input -> validation -> message ->
 * audience -> (adapter: protocol representation). Runs on the simulation thread
 * through the ticker so ordering matches gameplay.
 *
 * <p>Input is untrusted (§520): control characters are stripped, length is
 * capped, and commands (leading slash) are dispatched semantically instead of
 * being broadcast.</p>
 */
public final class ChatService {

    private static final Logger LOGGER = Logger.getLogger(ChatService.class.getName());
    private static final int MAX_LENGTH = 256;

    private final EngineTicker ticker;
    private final CommandService commands;
    private final Consumer<ChatEvent> publisher;

    /** Sealed chat outcome for the adapter to deliver. */
    public sealed interface ChatEvent permits PublicChat, SystemToPlayer {
    }

    public record PublicChat(PlayerSession sender, String content) implements ChatEvent {
    }

    public record SystemToPlayer(PlayerSession recipient, String content) implements ChatEvent {
    }

    public ChatService(EngineTicker ticker, CommandService commands, Consumer<ChatEvent> publisher) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.commands = Objects.requireNonNull(commands, "commands");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /** Submits raw client chat input. Safe from any thread. */
    public void submitChat(PlayerSession sender, String raw) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(raw, "raw");
        ticker.submit(() -> handleOnTick(sender, raw));
    }

    private void handleOnTick(PlayerSession sender, String raw) {
        String message = sanitize(raw);
        if (message.isEmpty()) {
            return;
        }
        if (message.startsWith("/")) {
            commands.dispatch(sender, message, (recipient, content) -> {
                deliverSystem(recipient, content);
                return null;
            });
            return;
        }
        publisher.accept(new PublicChat(sender, message));
    }

    private void deliverSystem(PlayerSession recipient, String content) {
        publisher.accept(new SystemToPlayer(recipient, content));
    }

    private static String sanitize(String raw) {
        StringBuilder clean = new StringBuilder(Math.min(raw.length(), MAX_LENGTH));
        for (int i = 0; i < raw.length() && clean.length() < MAX_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c >= 0x20 && c != 0x7F) { // strip control characters, keep everything else
                clean.append(c);
            }
        }
        return clean.toString().trim();
    }
}
