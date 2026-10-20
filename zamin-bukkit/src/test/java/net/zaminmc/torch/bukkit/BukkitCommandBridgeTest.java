package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.player.PlayerSession;

import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy command surface over the live engine (§28.7's commands
 * inventory — the honest gap the first slice recorded, now bridged onto
 * the engine's ONE command path, §30.3): a plugin.yml command dispatched
 * from the console AND from a player, the permission gate with the
 * plugin.yml permission-message, the usage line (the plugin's own
 * business), alias + fallback-prefixed resolution, tab completion through
 * the Command record's suggester (behind the same gates the engine
 * applies), and the §28.5 retirement on plugin disable with the fresh
 * re-enable bringing the commands back.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(5)
class BukkitCommandBridgeTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void thePluginYmlCommandDispatchesFromTheConsoleThroughTheEnginePath() throws Exception {
        BukkitTestHarness harness = harness();
        // The legacy dispatch surface → the engine's ONE command path → the
        // plugin's own onCommand (PluginCommand's default executor IS the
        // plugin). The probe's log line is the cross-classloader observable.
        harness.adapter.bukkitServer().dispatchCommand(
                harness.adapter.consoleSender(), "hello Console");
        harness.await(() -> harness.probeLogContains("cmd:hello by=Console label=hello"
                + " name=Console primary=true"), "the console dispatch reached the plugin");
        // The direct engine route answers the same way (the one path — the
        // legacy surface and the engine surface are the SAME walk).
        harness.server.consoleCommand("hello Engine");
        harness.await(() -> harness.probeLogContains("cmd:hello by=Console label=hello"
                + " name=Engine"), "the engine's own console route reached the plugin");
    }

    @Test
    @Order(2)
    void thePermissionGateRefusesThePlayerAndTheMessageIsThePluginYmlOne() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession peon = harness.join("Peon");
        Queue<String> heard = captureSystemMessages(harness, peon);
        // A NON-OP player: hasPermission answers the 1.8.8 unregistered
        // default (operator-only) — the plugin's own gate refuses with the
        // plugin.yml permission-message.
        int dispatchedBefore = countOf(harness.probeLogLines(), "cmd:hello by=Peon");
        harness.adapter.player(peon).chat("/hello PeonWorld");
        harness.await(() -> heard.stream().anyMatch(
                line -> line.contains("You lack the greeting permit.")),
                "the plugin.yml permission-message reached the refused player");
        Thread.sleep(300); // the dispatch certainly settled
        assertEquals(dispatchedBefore, countOf(harness.probeLogLines(), "cmd:hello by=Peon"),
                "the refused dispatch never reached the plugin's executor");
        // The engine's own operator gate is untouched: an op-2 engine
        // command stays unknown to the non-op player (§30.3's refusal).
        harness.adapter.player(peon).chat("/gamemode");
        harness.await(() -> heard.stream().anyMatch(
                line -> line.contains("Unknown command. Type \"/help\" for help.")),
                "the engine's own operator gate refused the non-op player");
    }

    @Test
    @Order(3)
    void anOpPlayerPassesTheGateAndTheUsageLineIsThePluginsOwnBusiness() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession boss = harness.join("Boss");
        Queue<String> heard = captureSystemMessages(harness, boss);
        // The op grant rides the engine's own /op command (the ONE path).
        harness.server.consoleCommand("op Boss");
        harness.await(() -> boss.opLevel() >= 2, "the engine's /op granted the level");
        harness.adapter.player(boss).chat("/hello BossWorld");
        harness.await(() -> harness.probeLogContains("cmd:hello by=Boss label=hello"
                + " name=BossWorld primary=true"), "the op player passed the plugin's gate");
        // The usage arm: the probe's onCommand returns false on two args and
        // PluginCommand.execute delivers the plugin.yml usage line (the
        // plugin's own business — never invented engine behavior).
        harness.adapter.player(boss).chat("/hello too many args");
        harness.await(() -> heard.stream().anyMatch(line -> line.contains("/hello [name]")),
                "the plugin.yml usage line reached the player");
    }

    @Test
    @Order(4)
    void theAliasesAndFallbackPrefixedFormsResolveToTheSameCommand() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.Server server = harness.adapter.bukkitServer();
        PluginCommand byName = server.getPluginCommand("hello");
        PluginCommand byAlias = server.getPluginCommand("greet");
        PluginCommand byPrefixedName = server.getPluginCommand("probe:hello");
        PluginCommand byPrefixedAlias = server.getPluginCommand("probe:greet");
        PluginCommand byUpper = server.getPluginCommand("HELLO");
        assertNotNull(byName, "the name resolves");
        assertNotNull(byAlias, "the alias resolves");
        assertNotNull(byPrefixedName, "the fallback-prefixed name resolves");
        assertNotNull(byPrefixedAlias, "the fallback-prefixed alias resolves");
        assertNotNull(byUpper, "the lookup is case-insensitive");
        assertSame(byName, byAlias, "the alias answers the same PluginCommand");
        assertSame(byName, byPrefixedName, "the prefixed name answers the same command");
        assertSame(byName, byPrefixedAlias, "the prefixed alias answers the same command");
        assertNull(server.getPluginCommand("gamemode"),
                "an engine command is not a plugin command's to answer");
        assertEquals("probe.hello", byName.getPermission(), "the plugin.yml permission rides");
        assertEquals("You lack the greeting permit.", byName.getPermissionMessage(),
                "the plugin.yml permission-message rides");
        // The alias dispatch rides the SAME engine lookup (the registry key
        // IS the alias) — the label carried to the plugin is the resolved
        // key (the recorded lowercase translation).
        harness.server.consoleCommand("greet Aliased");
        harness.await(() -> harness.probeLogContains("cmd:hello by=Console label=greet"
                + " name=Aliased"), "the alias dispatch reached the plugin");
        harness.server.consoleCommand("probe:greet Prefixed");
        harness.await(() -> harness.probeLogContains(
                "cmd:hello by=Console label=probe:greet name=Prefixed"),
                "the fallback-prefixed alias dispatch reached the plugin");
    }

    @Test
    @Order(5)
    void theSuggesterBridgesTheCompleterBehindTheSameGates() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession boss = harness.join("Bossish");
        harness.server.consoleCommand("op Bossish");
        harness.await(() -> boss.opLevel() >= 2, "the second boss granted");
        PlayerSession nonOp = harness.join("NonOp");
        net.zaminmc.torch.server.chat.CommandService.Command gift =
                harness.server.commands().lookup("gift");
        assertNotNull(gift, "the gift command registered in the engine's registry");
        assertNotNull(gift.suggester(), "the Command record's suggester arm is the bridge");
        // The completer's own candidates (the probe logs its call too).
        int completedBefore = countOf(harness.probeLogLines(), "cmd:gift-complete by=Bossish");
        List<String> candidates = gift.suggester().apply(playerView(boss), new String[] {""});
        assertEquals(List.of("red", "green", "blue"), candidates,
                "the plugin's completer answered through the suggester bridge");
        harness.await(() -> {
            try {
                return countOf(harness.probeLogLines(), "cmd:gift-complete by=Bossish")
                        > completedBefore;
            } catch (java.io.IOException unreadable) {
                return false; // the await's own retry handles it
            }
        }, "the completer ran (the probe's own log)");
        // The engine's last-token matcher filters the same candidates the
        // wire consult consumes (the reference's own shape).
        assertEquals(List.of("green"),
                net.zaminmc.torch.server.chat.CommandService.matchLastToken(
                        new String[] {"gr"}, candidates),
                "the last-token match narrows the completer's answer");
        // The 1.8.8 tab gate: an unauthorized sender completes nothing (the
        // permission gate rides inside the bridge, past the engine's own
        // level-0 gate).
        assertTrue(harness.server.commands().lookup("hello").suggester()
                        .apply(playerView(nonOp), new String[] {""}).isEmpty(),
                "the non-op player completes nothing on the gated command");
        // An authorized sender on the SAME command falls to the API's own
        // default: the online player names (the 1.8.8 walk, not an invented
        // one — the harness's joined players answer).
        List<String> defaulted = harness.server.commands().lookup("hello").suggester()
                .apply(playerView(boss), new String[] {""});
        assertTrue(defaulted.stream().anyMatch(name -> name.equals("Bossish")),
                "the no-completer default answered the online player names: " + defaulted);
        // The console view (a non-player source) passes the same gates and
        // completes the player names through the console's total authority.
        assertTrue(harness.server.commands().lookup("gift").suggester()
                        .apply(consoleView(), new String[] {""})
                        .containsAll(List.of("red", "green", "blue")),
                "the console passes the tab gate through its own authority");
    }

    @Test
    @Order(6)
    void theConsoleCompletionSurfaceCarriesThePluginCommands() throws Exception {
        BukkitTestHarness harness = harness();
        List<String> names = harness.server.commands().commandNames();
        assertTrue(names.contains("hello"), "the plugin's command name rides the surface");
        assertTrue(names.contains("greet"), "the alias rides the surface (1.8.8's own shape)");
        assertTrue(names.contains("probe:hello"), "the fallback-prefixed form rides");
        assertTrue(names.contains("gift"), "the second plugin command rides");
        assertTrue(names.contains("gamemode"), "the engine's own commands still ride");
    }

    @Test
    @Order(7)
    void theDisableSweepRetiresTheCommandsAndTheReenableBringsThemBack() throws Exception {
        BukkitTestHarness harness = harness();
        org.bukkit.plugin.PluginManager manager = harness.adapter.bukkitServer()
                .getPluginManager();
        org.bukkit.plugin.Plugin probe = manager.getPlugin("Probe");
        manager.disablePlugin(probe);
        harness.await(() -> !probe.isEnabled(), "the probe disabled");
        // The §28.5 sweep retired every key: the engine's registry answers
        // unknown, the legacy lookup answers null.
        assertNull(harness.server.commands().lookup("hello"), "the name retired");
        assertNull(harness.server.commands().lookup("greet"), "the alias retired");
        assertNull(harness.server.commands().lookup("probe:hello"),
                "the fallback-prefixed key retired");
        assertNull(harness.server.commands().lookup("gift"), "the second command retired");
        assertNull(harness.adapter.bukkitServer().getPluginCommand("hello"),
                "the legacy lookup retired with it");
        assertFalse(harness.server.commands().commandNames().contains("hello"),
                "the console surface retired the name too");
        // A dispatch under the retired name is the engine's own unknown
        // refusal — the executor is unreachable.
        int dispatchedBefore = countOf(harness.probeLogLines(), "cmd:hello by=Console");
        harness.server.consoleCommand("hello Retired");
        Thread.sleep(300); // the dispatch certainly settled on the ticker
        assertEquals(dispatchedBefore, countOf(harness.probeLogLines(), "cmd:hello by=Console"),
                "the retired command never reached the plugin");
        // The re-enable re-parses and re-registers through the fresh scope
        // (the reload's own law) — and the probe's onEnable re-wires its
        // completer through the SAME getCommand resolution.
        manager.enablePlugin(probe);
        harness.await(() -> probe.isEnabled(), "the probe re-enabled");
        assertNotNull(harness.server.commands().lookup("hello"),
                "the command registered again through the fresh scope");
        assertNotNull(harness.adapter.bukkitServer().getPluginCommand("gift"),
                "the legacy lookup answered again");
        harness.server.consoleCommand("hello Back");
        harness.await(() -> harness.probeLogContains("cmd:hello by=Console label=hello"
                + " name=Back"), "the re-enabled command dispatches again");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The engine-sender view the wire walk itself builds (the same shape
     * V18Connection constructs for the suggester consult).
     */
    private static CommandSender playerView(PlayerSession session) {
        return new CommandSender() {
            @Override public String name() {
                return session.name();
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return session.opLevel();
            }

            @Override public PlayerSession player() {
                return session;
            }
        };
    }

    /** The console's own view (the non-player arm of the same bridge). */
    private static CommandSender consoleView() {
        return new CommandSender() {
            @Override public String name() {
                return "Server";
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return 4;
            }

            @Override public PlayerSession player() {
                return null;
            }
        };
    }

    /**
     * One player's system-message capture through the engine's own chat
     * listener (the §353 hook — the same path the permission-message and
     * the usage lines ride). The live queue: the later awaits poll it
     * while the tick delivers.
     */
    private static Queue<String> captureSystemMessages(BukkitTestHarness harness,
            PlayerSession who) {
        Queue<String> heard = new ConcurrentLinkedQueue<>();
        harness.server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override public void onChatMessage(PlayerSession sender, String content) {
            }

            @Override public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient == who) {
                    heard.add(content);
                }
            }
        });
        return heard;
    }

    private static int countOf(List<String> lines, String needle) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }
}
