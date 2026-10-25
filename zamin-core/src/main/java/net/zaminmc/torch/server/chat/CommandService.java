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
     * A counted failure (VT-7f — the reference's CommandException model
     * for the commands whose SUCCESS COUNT is the gameplay surface): the
     * carried text lands in the sender's output exactly like the
     * reference's {@code sendFailure} walk (the command block's last
     * output row), but the dispatch counts ZERO — the shape /testfor's
     * comparator circuits read (a mismatched dataTag throws in the
     * reference, TestForCommand line 51: the count 0).
     */
    public static final class CommandFailure extends RuntimeException {
        /** The failure's own feedback text (the sender's output row). */
        private final String feedback;

        public CommandFailure(String feedback) {
            super(feedback, null, false, false);
            this.feedback = feedback;
        }

        /** @return the failure's feedback text. */
        public String feedback() {
            return feedback;
        }
    }

    /**
     * A command: name, description, required operator level, executor
     * returning the feedback text for the sender (null = silent), and the
     * optional per-command ARGUMENT completion (the reference's
     * getSuggestions/addTabCompletionOptions arm — null where the reference
     * defines none). The suggester receives the tokens AFTER the command
     * name (the being-typed last token included) and answers the candidate
     * list for the last position — UNMATCHED; the caller matches the last
     * token through {@link #matchLastToken} (the reference's
     * suggestMatching/getListOfStringsMatchingLastWord shape). A null or
     * null-returning suggester falls back to the online player names (the
     * engine's standing stand-in for the reference's getPlayerNames arms).
     */
    public record Command(String name, String description, int requiredLevel,
                          BiFunction<CommandSender, String[], String> executor,
                          BiFunction<CommandSender, String[], List<String>> suggester) {

        /** The full shape. */
        public Command(String name, String description, int requiredLevel,
                       BiFunction<CommandSender, String[], String> executor,
                       BiFunction<CommandSender, String[], List<String>> suggester) {
            this.name = name;
            this.description = description;
            this.requiredLevel = requiredLevel;
            this.executor = executor;
            this.suggester = suggester;
        }

        /** The no-suggester shape (the pre-completion signature). */
        public Command(String name, String description, int requiredLevel,
                       BiFunction<CommandSender, String[], String> executor) {
            this(name, description, requiredLevel, executor, null);
        }

        /** The no-permission, no-suggester shape for simple gameplay commands. */
        public Command(String name, String description,
                       BiFunction<CommandSender, String[], String> executor) {
            this(name, description, 0, executor, null);
        }

        /** The no-permission shape with a suggester. */
        public Command(String name, String description,
                       BiFunction<CommandSender, String[], String> executor,
                       BiFunction<CommandSender, String[], List<String>> suggester) {
            this(name, description, 0, executor, suggester);
        }
    }

    /**
     * The reference's suggestMatching (getListOfStringsMatchingLastWord):
     * the LAST token's case-insensitive prefix over the candidates, in the
     * candidates' own order and case; an empty or absent last token matches
     * everything. Used by the tab-complete consumer.
     */
    public static List<String> matchLastToken(String[] args, List<String> candidates) {
        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
        List<String> out = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isEmpty()
                    && candidate.toLowerCase(java.util.Locale.ROOT).startsWith(last)) {
                out.add(candidate);
            }
        }
        return out;
    }

    private final Map<String, Command> commands = new TreeMap<>();

    public void register(Command command) {
        Command existing = commands.putIfAbsent(command.name(), command);
        if (existing != null) {
            throw new IllegalStateException("Duplicate command: " + command.name());
        }
    }

    /**
     * Removes a registered command (§28.5's plugin-owned registration law):
     * the retirement verb for registrations owned through a plugin resource
     * scope — the compatibility adapter's command bridge registers every
     * plugin command through {@link #register} and owns its removal through
     * the owning plugin's scope, so the disable sweep retires that plugin's
     * commands automatically. A no-op when the name is not registered (the
     * verb must be idempotent — the scope's own close contract). Case
     * matches {@link #lookup}: the engine's registry resolves lowercase.
     */
    public synchronized void unregister(String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        commands.remove(name.toLowerCase(java.util.Locale.ROOT));
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
        } catch (CommandFailure failure) {
            // The counted failure (VT-7f — the reference's CommandException
            // shape): the carried text lands in the output (the sendFailure
            // walk), the count answers 0 — the comparator's own row.
            sender.sendMessage(failure.feedback());
            success = 0;
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
