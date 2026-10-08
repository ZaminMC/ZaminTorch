package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * Semantic command handling (§521): parsing produces a name + arguments;
 * executors never see the raw string. Every command carries the operator
 * level it needs (0 = everyone, 4 = owner); a sender below the level sees
 * the vanilla "unknown command" refusal, never the command's existence.
 */
public final class CommandService {

    /**
     * A command: name, description, required operator level, executor
     * returning the feedback text for the sender (null = silent).
     */
    public record Command(String name, String description, int requiredLevel,
                          BiFunction<CommandSender, String[], String> executor) {

        /** The no-permission shape for simple gameplay commands. */
        public Command(String name, String description,
                       BiFunction<CommandSender, String[], String> executor) {
            this(name, description, 0, executor);
        }
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
        return commands.get(name.toLowerCase());
    }

    /** @return all registered commands (for /help and tab completion). */
    public List<Command> all() {
        return new ArrayList<>(commands.values());
    }

    /** Executes a command intent (already identified by the leading slash). */
    public void dispatch(CommandSender sender, String rawInput) {
        String line = rawInput.substring(1).trim();
        if (line.isEmpty()) {
            sender.sendMessage("Usage: /<command>");
            return;
        }
        String[] parts = line.split("\\s+");
        Command command = commands.get(parts[0].toLowerCase());
        if (command == null || sender.opLevel() < command.requiredLevel()) {
            // Vanilla behavior: an unknown or unauthorized command does not
            // reveal itself; the refusal text is identical for both.
            sender.sendMessage("Unknown command. Type \"/help\" for help.");
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
            sender.sendMessage(result);
        }
    }
}
