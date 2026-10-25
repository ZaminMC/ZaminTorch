package net.zaminmc.torch.server.chat;

import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-6 of the vanilla parity remainder: the per-command surface diff of the
 * engine's registered commands against the reference 1.8.8 dedicated-server
 * registration walk, pinned so the surface cannot drift silently.
 *
 * <p><b>The reference walk (CommandManager.java, 59 registrations covering
 * 62 distinct names)</b>: 44 common commands over
 * {@code reference/1.8.8/net/minecraft/server/command/} + 15 dedicated-side
 * admin commands ({@code .../dedicated/command/}) + the integrated-side
 * PublishCommand the dedicated manager also registers; TellCommand carries
 * the w/msg aliases and HelpCommand the {@code ?} alias (vanilla's {@code /?});
 * AbstractCommand's own default level is 4 (the classes that do not
 * override it: defaultgamemode, save-all/off/on, stop, publish).</p>
 *
 * <p><b>The R-6 diff (this cycle, evidence in docs/VANILLA_PARITY_MATRIX.md):
 * 74 engine verbs vs the reference's 62 names</b> — intersection 56
 * (51 level-matched), 5 level-divergent rows RECORDED as standing
 * divergences (time 0 vs 2, seed 0 vs 2, kick 2 vs 3,
 * say 2 vs 1, defaultgamemode 2 vs 4), 18 engine extras (the collapsed
 * save, the gamemode shortcuts gms/gmc/gma/gmsp, the tell aliases as
 * first-class verbs msg/w, the admin QoL family heal/feed/fly/spawn/
 * setspawn/rename/tps/ping/plugins/version/anvilimport, spawnmob), and
 * 6 reference names with no engine verb (the {@code ?} help alias,
 * publish, save-all/save-on/save-off as separate verbs, setidletimeout).
 * The deeper form divergences stay ledgered where they
 * live: /summon + /spawnmob player-only where the reference accepts the console
 * (the cycle-3 papercut, standing), /save collapsed without the on/off
 * arms, /setspawn the standing-form verb the reference folds into
 * setworldspawn's no-args form. /give was ML-008 — RESOLVED: aligned to
 * the vanilla form and level (GiveCommand 1:1, the <player> <item>
 * [count] [data] [dataTag] shape). /op's offline-profile walk was ML-005,
 * resolved (wave 3); /ban-ip and /pardon-ip are reference-matched at 3
 * (the player-to-IP resolution form).</p>
 *
 * <p><b>The pin</b>: the exact 74-name set and every verb's level, so any
 * addition, removal, or level change fails this test naming the row —
 * the same law ItemRegistryParityTest pins the item table with. The
 * divergent rows are pinned at their CURRENT recorded values with their
 * reference level in the comment; changing one is a deliberate act that
 * must also update the parity matrix's R-6 table.</p>
 */
class RegisteredCommandSurfaceParityTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "r6cmd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The exact registered surface: 72 verbs. The set comparison names
     * every added or removed row (TreeSet diff), the way the registry
     * actually answers — never a subset check that would pass while
     * hiding drift.
     */
    @Test
    void theRegisteredCommandSurfaceIsExactlyTheDiffedSet() {
        Set<String> expected = new TreeSet<>(Set.of(
                "achievement", "anvilimport", "ban", "ban-ip", "banlist", "blockdata",
                "clear", "clone", "debug", "defaultgamemode", "deop", "difficulty",
                "effect", "enchant", "entitydata", "execute", "feed", "fill", "fly",
                "gamemode", "gamerule", "give", "gma", "gmc", "gms", "gmsp", "heal",
                "help", "kick", "kill", "list", "me", "msg", "op", "pardon",
                "pardon-ip", "particle", "ping", "playsound", "plugins", "rename",
                "replaceitem", "reply", "save", "say", "scoreboard", "seed", "setblock",
                "setspawn", "setworldspawn", "spawn", "spawnmob", "spawnpoint",
                "spreadplayers", "stats", "stop", "summon", "tell", "tellraw", "testfor",
                "testforblock", "testforblocks", "time", "title", "toggledownfall", "tp",
                "tps", "trigger", "version", "w", "weather", "whitelist", "worldborder",
                "xp"));
        Set<String> actual = new TreeSet<>();
        for (CommandService.Command command : server.commands().all()) {
            actual.add(command.name());
        }
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(expected);
        Set<String> removed = new TreeSet<>(expected);
        removed.removeAll(actual);
        assertTrue(added.isEmpty() && removed.isEmpty(),
                "the command surface drifted — added: " + added + ", removed: " + removed);
        assertEquals(74, actual.size(), "the diffed surface is 74 verbs");
    }

    /** Every verb's level, matched rows and recorded divergences alike. */
    @Test
    void everyVerbSitsAtItsRecordedLevel() {
        // reference-matched rows: the reference's own level (AbstractCommand
        // walk + per-class overrides; the dedicated set's ban family at 3)
        assertLevel("help", 0);
        assertLevel("ping", 0); // extra (no reference verb)
        assertLevel("give", 2); // reference-matched (ML-008 resolved: the vanilla form, GiveCommand 1:1)
        assertLevel("gamerule", 2); // reference-matched
        assertLevel("scoreboard", 2); // reference-matched
        assertLevel("worldborder", 2); // reference-matched
        assertLevel("title", 2); // reference-matched
        assertLevel("tellraw", 2); // reference-matched
        assertLevel("testfor", 2); // reference-matched
        assertLevel("toggledownfall", 2); // reference-matched
        assertLevel("spawnpoint", 2); // reference-matched
        assertLevel("setworldspawn", 2); // reference-matched
        assertLevel("effect", 2); // reference-matched
        assertLevel("enchant", 2); // reference-matched
        assertLevel("playsound", 2); // reference-matched
        assertLevel("particle", 2); // reference-matched
        assertLevel("ban-ip", 3); // reference-matched (the player-to-IP resolution form)
        assertLevel("pardon-ip", 3); // reference-matched
        assertLevel("trigger", 0); // reference-matched
        assertLevel("debug", 3); // reference-matched
        assertLevel("achievement", 2); // reference-matched
        assertLevel("setblock", 2); // reference-matched
        assertLevel("fill", 2); // reference-matched
        assertLevel("clone", 2); // reference-matched
        assertLevel("execute", 2); // reference-matched
        assertLevel("spreadplayers", 2); // reference-matched
        assertLevel("stats", 2); // reference-matched
        assertLevel("replaceitem", 2); // reference-matched
        assertLevel("testforblock", 2); // reference-matched
        assertLevel("testforblocks", 2); // reference-matched
        assertLevel("blockdata", 2); // reference-matched
        assertLevel("entitydata", 2); // reference-matched
        assertLevel("time", 0); // DIVERGENT-RECORDED: reference 2 (R-6 table)
        assertLevel("rename", 0); // extra (no reference verb)
        assertLevel("spawnmob", 0); // extra, player-only (the cycle-3 console-form papercut, standing)
        assertLevel("summon", 2); // reference-matched level; player-only form (the cycle-3 papercut, standing)
        assertLevel("gamemode", 2); // reference-matched
        assertLevel("op", 3); // reference-matched (ML-005's offline walk resolved)
        assertLevel("deop", 3); // reference-matched
        assertLevel("list", 0); // reference-matched
        assertLevel("kick", 2); // DIVERGENT-RECORDED: reference 3 (R-6 table)
        assertLevel("say", 2); // DIVERGENT-RECORDED: reference 1 (R-6 table)
        assertLevel("save", 4); // extra-as-collapsed save-all (reference 4; the on/off arms absent)
        assertLevel("anvilimport", 4); // extra (no reference verb)
        assertLevel("stop", 4); // reference-matched
        assertLevel("gms", 2); // extra (the reference's gamemode 2)
        assertLevel("gmc", 2); // extra (the reference's gamemode 2)
        assertLevel("gma", 2); // extra (the reference's gamemode 2)
        assertLevel("gmsp", 2); // extra (the reference's gamemode 2)
        assertLevel("tp", 2); // reference-matched
        assertLevel("kill", 2); // reference-matched
        assertLevel("heal", 2); // extra (no reference verb)
        assertLevel("feed", 2); // extra (no reference verb)
        assertLevel("spawn", 0); // extra (no reference verb)
        assertLevel("setspawn", 2); // extra (the reference folds the standing form into setworldspawn's no-args)
        assertLevel("fly", 2); // extra (no reference verb)
        assertLevel("clear", 2); // reference-matched
        assertLevel("ban", 3); // reference-matched
        assertLevel("pardon", 3); // reference-matched
        assertLevel("banlist", 3); // reference-matched
        assertLevel("whitelist", 3); // reference-matched
        assertLevel("weather", 2); // reference-matched
        assertLevel("me", 0); // reference-matched
        assertLevel("tell", 0); // reference-matched
        assertLevel("msg", 0); // extra-as-alias (the reference's tell alias at its own level)
        assertLevel("w", 0); // extra-as-alias (the reference's tell alias at its own level)
        assertLevel("reply", 0); // extra (no reference verb)
        assertLevel("xp", 2); // reference-matched
        assertLevel("difficulty", 2); // reference-matched
        assertLevel("defaultgamemode", 2); // DIVERGENT-RECORDED: reference 4 (R-6 table)
        assertLevel("seed", 0); // DIVERGENT-RECORDED: reference 2 (R-6 table)
        assertLevel("tps", 2); // extra (no reference verb)
        assertLevel("plugins", 0); // extra (no reference verb)
        assertLevel("version", 0); // extra (no reference verb)
    }

    private void assertLevel(String name, int expected) {
        CommandService.Command command = server.commands().lookup(name);
        assertTrue(command != null, "'" + name + "' must be registered");
        assertEquals(expected, command.requiredLevel(),
                "/" + name + "'s level (the R-6 recorded surface)");
    }
}
