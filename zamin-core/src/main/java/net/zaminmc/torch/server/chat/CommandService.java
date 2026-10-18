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

    /**
     * The completion snapshot (the console's §30 surface): an immutable,
     * safely published copy of the registered names, taken under the same
     * lock as registration so a plugin registering mid-read can never tear
     * the view. Completion consumers must treat this as a point-in-time
     * snapshot — a later registration invalidates nothing but the next call.
     */
    public synchronized List<String> commandNames() {
        return List.copyOf(commands.keySet());
    }

    /** Executes a command intent (already identified by the leading slash). */
    public void dispatch(CommandSender sender, String rawInput) {
        dispatchCounted(sender, rawInput);
    }

    /**
     * The counted dispatch — CommandRegistry.run 1:1: the same walk as
     * {@link #dispatch} but answering the success count. The count is 1
     * when the command ran (the executor completed without throwing) and
     * 0 for the unknown-command refusal, the permission refusal, or a
     * broken executor. /execute consults the count for its own failure
     * line.
     *
     * <p>Known difference (ledgered): the engine's executors carry both
     * success and refusal text as their return string, where the
     * reference's commands report failure by throwing — a nested command
     * that "fails" with a feedback string still counts as one success
     * here. The throwing parse helpers (CommandParse's ladder) keep the
     * hard failures counted correctly.</p>
     */
    public int dispatchCounted(CommandSender sender, String rawInput) {
        String line = rawInput.trim();
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        if (line.isEmpty()) {
            sender.sendMessage("Usage: /<command>");
            sender.addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type.SUCCESS_COUNT, 0);
            return 0;
        }
        String[] parts = line.split("\\s+");
        Command command = commands.get(parts[0].toLowerCase());
        if (command == null || sender.opLevel() < command.requiredLevel()) {
            // Vanilla behavior: an unknown or unauthorized command does not
            // reveal itself; the refusal text is identical for both.
            sender.sendMessage("Unknown command. Type \"/help\" for help.");
            // The reference's run reports the zero on every path (the
            // trailing addResult(SUCCESS_COUNT, j) after the if/else).
            sender.addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type.SUCCESS_COUNT, 0);
            return 0;
        }
        // The registry-level result reporting (CommandRegistry.run's own
        // addResult pair): one affected entity for the plain arm (the
        // reference's multi-selector expansion reports its count — the
        // engine's commands carry their multi-targets internally; ledgered).
        sender.addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type.AFFECTED_ENTITIES, 1);
        String[] args = new String[Math.max(0, parts.length - 1)];
        System.arraycopy(parts, 1, args, 0, args.length);
        String result = null;
        int success;
        try {
            result = command.executor().apply(sender, args);
            success = 1;
        } catch (RuntimeException e) {
            // A broken command must not damage the engine (§54); report to the sender.
            sender.sendMessage("Command failed: " + e.getMessage());
            success = 0;
        }
        sender.addResult(net.zaminmc.torch.server.command.EngineCommandResults.Type.SUCCESS_COUNT, success);
        if (result != null && !result.isEmpty()) {
            sender.sendMessage(result);
        }
        return success;
    }
}
