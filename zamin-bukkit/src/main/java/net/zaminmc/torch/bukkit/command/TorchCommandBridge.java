package net.zaminmc.torch.bukkit.command;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.plugin.PluginHandle;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.PluginCommandYamlParser;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The legacy command surface (§28.7's commands inventory) — the honest gap
 * the first slice recorded ("no legacy command registry in this slice"),
 * now bridged onto the engine's ONE command path (§30.3: no second
 * dispatcher anywhere in this class — every plugin command registers
 * through {@link net.zaminmc.torch.server.chat.CommandService}'s own
 * public {@code register()} surface and dispatches through its own
 * {@code dispatch()} walk).
 *
 * <p><b>The load wave rides the API artifact's OWN parsing</b> (§27.2: API
 * only, never a server implementation — the same law the plugin loader
 * bridge follows): {@link PluginCommandYamlParser#parse} is the 1.8.8
 * reference's own plugin.yml {@code commands:} walker. It builds the
 * {@link PluginCommand}s (owned by the plugin, description/usage/aliases/
 * permission/permission-message straight from the yaml), and THIS class
 * registers each one into the engine's registry under every resolvable
 * key — the name, each alias, and the 1.8.8 fallback-prefixed forms
 * ({@code PluginName:name}, the collision family's own law) — so an
 * alias dispatch rides the SAME engine lookup the primary name rides.</p>
 *
 * <p><b>The Command record's two arms (the engine's own shape):</b></p>
 * <ul>
 *   <li>the {@code executor} bridges the plugin's
 *       {@link CommandExecutor#onCommand} through
 *       {@link PluginCommand#execute} — the API's own walk, which carries
 *       the whole 1.8.8 semantics: the disabled-plugin no-op, the
 *       permission gate with the plugin.yml permission-message (the
 *       plugin's own business, never invented engine behavior), the
 *       CommandException attribution wrap, and the usage lines on a
 *       {@code false} return (the plugin's business too). The required
 *       operator level is 0: the engine's own gate never refuses a plugin
 *       command — the Bukkit permission gate rides INSIDE the bridge,
 *       exactly where the 1.8.8 dispatcher carries it.</li>
 *   <li>the {@code suggester} bridges the plugin's
 *       {@link TabCompleter} through {@link PluginCommand#tabComplete} —
 *       the API's own walk again (the completer when the plugin set one,
 *       the online-player-names default when it did not) — behind the
 *       same operator/permission gating the engine applies (the engine
 *       consults the suggester only past its own required-level gate,
 *       which is 0 here, and the bridge adds the 1.8.8 dispatcher's own
 *       {@code testPermissionSilent} gate: an unauthorized sender
 *       completes nothing).</li>
 * </ul>
 *
 * <p><b>Conflicts (the never-a-second-dispatcher law):</b> the engine's
 * registry carries each name once, and a key an engine command (or an
 * earlier plugin) already owns stays ITS owner — a conflicting plugin
 * registration is logged loudly and skipped, never overriding (§30.3's
 * one-path law; the 1.8.8 dispatcher's own first-registration-wins
 * behavior).</p>
 *
 * <p><b>Retirement (§28.5's scope law):</b> every registration's removal
 * verb is owned through the plugin's CURRENT engine resource scope, so
 * the disable sweep retires the plugin's commands automatically however
 * the plugin ends. A re-enable re-parses and re-registers through the
 * fresh scope (the documented reload translation: a PluginCommand
 * reference held across the plugin's own disable answers a dead object
 * — the fresh-listener-scope law's command twin).</p>
 *
 * <p><b>The label translation (the §27.9 recorded note):</b> the engine's
 * dispatch hands the executor no typed-form label, so the bridge labels
 * each dispatch with the registry key the engine resolved (the typed name
 * lowercased, the alias's own form). The 1.8.8 usage-line
 * {@code <command>} replacement therefore substitutes the lowercase
 * resolved key — the case of the typed form is the recorded divergence.</p>
 */
public final class TorchCommandBridge {

    private final BukkitAdapter adapter;

    /**
     * The bridge's own registry: every resolvable key (the name, each
     * alias, each fallback-prefixed form) → the PluginCommand it answers.
     * The 1.8.8 command-map shape ({@code Server.getPluginCommand}
     * resolves through it, aliases included).
     */
    private final Map<String, PluginCommand> byKey = new ConcurrentHashMap<>();

    /** The per-plugin registrations (the retirement walk's own ledger). */
    private final Map<Plugin, List<Registration>> byPlugin = new ConcurrentHashMap<>();

    /** One plugin command's registration: the key and the command under it. */
    private record Registration(String key, PluginCommand command) {
    }

    public TorchCommandBridge(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------ the load wave (the enable walk's own first act)

    /**
     * Registers one plugin's plugin.yml commands into the engine's registry
     * — the 1.8.8 enable walk's own order (commands registered BEFORE the
     * plugin's {@code onEnable} runs, so {@code getCommand(name)} resolves
     * inside it), every removal verb owned through the plugin's CURRENT
     * scope (the disable sweep retires the commands, §28.5).
     *
     * @return how many engine keys registered (parsed fresh every enable)
     */
    public int registerPluginCommands(Plugin plugin, PluginHandle handle) {
        List<Command> parsed = PluginCommandYamlParser.parse(plugin);
        List<Registration> mine = new ArrayList<>();
        String fallbackPrefix = plugin.getName().toLowerCase(Locale.ROOT);
        for (Command candidate : parsed) {
            if (!(candidate instanceof PluginCommand command)) {
                continue; // the parser's own output is PluginCommands; a
                // foreign Command is not the bridge's to register
            }
            registerOne(plugin, command, command.getName(), mine);
            for (String alias : command.getAliases()) {
                registerOne(plugin, command, alias, mine);
            }
            // The 1.8.8 fallback-prefix family: the collision-proof keys
            // ({@code Probe:hello} resolves even when another plugin owns
            // {@code hello}).
            registerOne(plugin, command, fallbackPrefix + ":" + command.getName(), mine);
            for (String alias : command.getAliases()) {
                registerOne(plugin, command, fallbackPrefix + ":" + alias, mine);
            }
        }
        byPlugin.put(plugin, List.copyOf(mine));
        if (handle != null) {
            // §28.5: the disable sweep runs the retirement verb — however
            // the plugin ends, its commands leave the registry.
            handle.resources().own(() -> retire(plugin));
        }
        if (!mine.isEmpty()) {
            adapter.logger().info(() -> "Registered " + mine.size() + " engine command key(s)"
                    + " for " + plugin.getName() + " (the plugin.yml command surface, aliases"
                    + " and fallback-prefixed keys included)");
        }
        return mine.size();
    }

    /**
     * One key's registration: the engine's own {@code register()} surface
     * (the one path), a conflict logged loudly and skipped when the key is
     * already owned — never an override.
     */
    private void registerOne(Plugin plugin, PluginCommand command, String key,
            List<Registration> mine) {
        String lower = key.toLowerCase(Locale.ROOT);
        net.zaminmc.torch.server.chat.CommandService.Command engineCommand =
                new net.zaminmc.torch.server.chat.CommandService.Command(
                        lower,
                        command.getDescription() == null ? "" : command.getDescription(),
                        0, // the engine's operator gate never refuses a plugin
                        // command — the Bukkit permission gate rides inside the
                        // executor bridge (the 1.8.8 dispatcher's own law)
                        (sender, args) -> execute(command, lower, sender, args),
                        (sender, args) -> suggest(command, lower, sender, args));
        try {
            adapter.engine().commands().register(engineCommand);
        } catch (IllegalStateException conflict) {
            // The never-a-second-dispatcher law: the standing command keeps
            // the key (the engine's own refusal, logged through).
            adapter.logger().log(Level.SEVERE, "Plugin command '" + lower + "' of "
                    + plugin.getName() + " conflicts with a registered command"
                    + " — the key stays with its owner (the engine's one path)");
            return;
        }
        mine.add(new Registration(lower, command));
        byKey.put(lower, command);
    }

    // ------------------------------------------------------------------ the dispatch (the engine's executor arm)

    /**
     * The executor bridge: the engine's dispatch reaches the plugin
     * executor through {@link PluginCommand#execute} — the API's own walk
     * (the permission gate with the permission-message, the usage lines,
     * the disabled-plugin no-op, the CommandException attribution).
     * Feedback flows through the legacy sender; the engine arm answers
     * null (silent) — every plugin-visible line was the walk's own.
     */
    private String execute(PluginCommand command, String label,
            CommandSender sender, String[] args) {
        org.bukkit.command.CommandSender legacy = legacyView(sender);
        boolean success;
        try {
            success = command.execute(legacy, label, args);
        } catch (CommandException pluginFailure) {
            // The 1.8.8 wording already lives inside the wrap; the stack
            // trace is the attribution the engine's own catch never logs.
            adapter.logger().log(Level.SEVERE, pluginFailure.getMessage(), pluginFailure);
            throw pluginFailure; // the engine's §54 broken-command walk:
            // reported to the sender, counted as a failure, the tick safe
        }
        return null; // PluginCommand.execute already delivered every line
        // through the legacy sender — the usage message (a false return)
        // and the permission-message (the gate) are the plugin's own arms
    }

    /**
     * The suggester bridge: {@link PluginCommand#tabComplete} behind the
     * same operator/permission gating the engine applies (the engine's own
     * required-level gate consulted first — it passes at 0 — then the 1.8.8
     * dispatcher's {@code testPermissionSilent}: an unauthorized sender
     * completes nothing). A plugin without a completer falls back to the
     * online player names (the 1.8.8 default — the API's own walk, not an
     * invented one); the engine's last-token matcher filters the answer.
     */
    private List<String> suggest(PluginCommand command, String label,
            CommandSender sender, String[] args) {
        org.bukkit.command.CommandSender legacy = legacyView(sender);
        if (!command.testPermissionSilent(legacy)) {
            return List.of(); // the 1.8.8 tab gate: nothing for the refused
        }
        try {
            return command.tabComplete(legacy, label, args);
        } catch (RuntimeException broken) {
            // A broken completer never breaks the wire walk (§54): logged
            // with the attribution, answered empty.
            adapter.logger().log(Level.SEVERE, "Could not complete command '"
                    + label + "' in plugin "
                    + command.getPlugin().getDescription().getFullName(), broken);
            return List.of();
        }
    }

    /**
     * The engine sender → the legacy sender: a player session through the
     * adapter's own player view; every non-player source (the console, and
     * the {@code /execute} proxied sources the engine carries — the §27.9
     * recorded translation: a proxied non-player source dispatches through
     * the console's own total-authority view) through the console sender.
     */
    private org.bukkit.command.CommandSender legacyView(CommandSender sender) {
        PlayerSession session = sender.player();
        if (session != null) {
            return adapter.player(session);
        }
        return adapter.consoleSender();
    }

    // ------------------------------------------------------------------ the registry surface

    /**
     * The legacy command-map lookup (the 1.8.8
     * {@code Server.getPluginCommand} shape): the name, an alias, or a
     * fallback-prefixed form, case-insensitive — or null when the key is
     * no plugin command's.
     */
    public PluginCommand pluginCommand(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        return byKey.get(name.toLowerCase(Locale.ROOT));
    }

    /** The engine registry's own view: the plugin-command keys (diagnostics). */
    public int registeredKeys() {
        return byKey.size();
    }

    // ------------------------------------------------------------------ the retirement (§28.5's scope-owned verb)

    /**
     * Retires one plugin's commands: every key the plugin's registration
     * owns leaves the engine registry (through the engine's own
     * {@code unregister} arm — the §28.5 retirement verb) and the bridge's
     * own map. Idempotent (the scope's close contract); a plugin whose
     * registration was never admitted (the empty list) retires nothing.
     */
    private void retire(Plugin plugin) {
        List<Registration> mine = byPlugin.remove(plugin);
        if (mine == null || mine.isEmpty()) {
            return;
        }
        for (Registration registration : mine) {
            adapter.engine().commands().unregister(registration.key());
            byKey.remove(registration.key());
        }
        adapter.logger().info(() -> plugin.getName() + "'s commands retired from the"
                + " engine registry (" + mine.size() + " key(s) — the §28.5 scope sweep)");
    }
}
