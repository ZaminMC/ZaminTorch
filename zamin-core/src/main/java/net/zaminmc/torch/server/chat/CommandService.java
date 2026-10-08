package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Semantic command handling (§521): parsing produces a name + arguments;
 * executors never see the raw string. The first administrative commands are
 * minimal (§305); the registry grows with real requirements only.
 */
public final class CommandService {

    /** A command: name, description, executor returning feedback for the sender. */
    public record Command(String name, String description,
                          BiFunction<PlayerSession, String[], String> executor) {
    }

    private final Map<String, Command> commands = new TreeMap<>();

    public void register(Command command) {
        Command existing = commands.putIfAbsent(command.name(), command);
        if (existing != null) {
            throw new IllegalStateException("Duplicate command: " + command.name());
        }
    }

    /** @return the registered command, or null when unknown. */
    public Command lookup(String name) {
        return commands.get(name);
    }

    /** Executes a command intent (already identified by the leading slash). */
    public void dispatch(PlayerSession sender, String rawInput,
                         java.util.function.BiFunction<PlayerSession, String, Void> feedback) {
        String line = rawInput.substring(1).trim();
        if (line.isEmpty()) {
            feedback.apply(sender, "Usage: /<command>");
            return;
        }
        String[] parts = line.split("\\s+");
        Command command = commands.get(parts[0].toLowerCase());
        if (command == null) {
            feedback.apply(sender, "Unknown command: " + parts[0] + " (try /help)");
            return;
        }
        String[] args = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        String result;
        try {
            result = command.executor().apply(sender, args);
        } catch (RuntimeException e) {
            // A broken command must not damage the engine (§54); report to the sender.
            result = "Command failed: " + e.getMessage();
        }
        if (result != null && !result.isEmpty()) {
            feedback.apply(sender, result);
        }
    }

    /** @return all registered commands (for /help). */
    public List<Command> all() {
        return new ArrayList<>(commands.values());
    }
}
