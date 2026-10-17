package net.zaminmc.torch.server;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.scoreboard.EngineScoreboard;
import net.zaminmc.torch.server.scoreboard.ScoreboardCriterion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /scoreboard command (Slice 85 — ScoreboardCommand 1:1, op-2) and the
 * scoreboard persistence: the objectives family (add with a display name,
 * list, setdisplay, remove), the players family (set/add/remove/reset/
 * enable/test/operation with the read-only gate), the teams family (add,
 * option color's prefix/suffix decoration, join/leave/empty, list), and
 * the save/load round trip through world/scoreboard.json.
 */
class ScoreboardCommandTest {

    private java.nio.file.Path dataDir;

    /** A recording console sender: captures every command reply. */
    private static final class RecordingConsole implements CommandSender {
        final List<String> replies = new CopyOnWriteArrayList<>();

        @Override
        public String name() {
            return "Console";
        }

        @Override
        public void sendMessage(String message) {
            replies.add(message);
        }

        @Override
        public int opLevel() {
            return 4;
        }

        @Override
        public PlayerSession player() {
            return null;
        }
    }

    private static final class NoopLink implements ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private EngineServer server;
    private RecordingConsole console;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "sbcmd", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        console = new RecordingConsole();
        return server;
    }

    /** Runs a console command on the tick lane and awaits the reply. */
    private String command(String line) throws InterruptedException {
        console.replies.clear();
        server.ticker().submit(() -> server.chatService().dispatchCommand(console, line));
        await(() -> !console.replies.isEmpty(), "the command answered: " + line);
        return console.replies.get(0);
    }

    @Test
    void theObjectivesFamilyEndToEnd() throws Exception {
        dataDir = Files.createTempDirectory("sbcmd-obj");
        boot();
        try {
            String added = command("/scoreboard objectives add points dummy KillsToday");
            assertTrue(added.contains("Added new objective 'points'"), added);
            assertEquals("KillsToday", server.scoreboard().getObjective("points").displayName());
            // The 1.8 command splitter has NO quote handling (Brigadier is
            // 1.13+): a quoted display name keeps its quote marks — the
            // reference's own naive-split quirk, preserved.
            command("/scoreboard objectives remove points");
            command("/scoreboard objectives add points dummy \"Kills Today\"");
            assertEquals("\"Kills Today\"",
                    server.scoreboard().getObjective("points").displayName(),
                    "the 1.8 split keeps the quotes (parseText's own LiteralText walk)");

            String listed = command("/scoreboard objectives list");
            assertTrue(listed.contains("points") && listed.contains("KillsToday")
                    || listed.contains("\"Kills Today\""), listed);

            String displayed = command("/scoreboard objectives setdisplay sidebar points");
            assertTrue(displayed.contains("Set the display objective in slot 'sidebar' to 'points'"),
                    displayed);
            assertEquals("points",
                    server.scoreboard().getDisplayObjective(1).name());

            String cleared = command("/scoreboard objectives setdisplay sidebar");
            assertTrue(cleared.contains("Cleared display objective in slot 'sidebar'"), cleared);
            assertNull(server.scoreboard().getDisplayObjective(1));

            String removed = command("/scoreboard objectives remove points");
            assertTrue(removed.contains("Removed objective 'points'"), removed);
            assertNull(server.scoreboard().getObjective("points"));

            // The unknown-criterion and duplicate gates.
            String badCriterion = command("/scoreboard objectives add broken notACriterion");
            assertTrue(badCriterion.contains("Command failed"), badCriterion);
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePlayersFamilyAndTheReadOnlyGate() throws Exception {
        dataDir = Files.createTempDirectory("sbcmd-players");
        boot();
        try {
            command("/scoreboard objectives add points dummy");
            command("/scoreboard objectives add hearts health");

            String set = command("/scoreboard players set Alice points 7");
            assertTrue(set.contains("Set points of Alice to 7"), set);
            assertEquals(7, server.scoreboard().getScore("Alice",
                    server.scoreboard().getObjective("points")).get());

            String add = command("/scoreboard players add Alice points 3");
            assertTrue(add.contains("Set points of Alice to 10"), add);

            String remove = command("/scoreboard players remove Alice points 4");
            assertTrue(remove.contains("Set points of Alice to 6"), remove);

            String test = command("/scoreboard players test Alice points 5 8");
            assertTrue(test.contains("Score 6"), test);
            String testFail = command("/scoreboard players test Alice points 9");
            assertTrue(testFail.contains("Command failed"), testFail);

            // The operation family over a second owner.
            command("/scoreboard players set Bob points 100");
            String op = command("/scoreboard players operation Alice points += Bob points");
            assertTrue(op.contains("Operation applied"), op);
            assertEquals(106, server.scoreboard().getScore("Alice",
                    server.scoreboard().getObjective("points")).get());
            command("/scoreboard players operation Alice points >< Bob points");
            assertEquals(100, server.scoreboard().getScore("Alice",
                    server.scoreboard().getObjective("points")).get());
            assertEquals(106, server.scoreboard().getScore("Bob",
                    server.scoreboard().getObjective("points")).get(),
                    "the swap arm trades both values");

            // The trigger enable gate.
            command("/scoreboard objectives add trig trigger");
            String enabled = command("/scoreboard players enable Alice trig");
            assertTrue(enabled.contains("Enabled trigger objective trig for Alice"), enabled);
            String wrongEnable = command("/scoreboard players enable Alice points");
            assertTrue(wrongEnable.contains("not a trigger"), wrongEnable);

            // The read-only gate: health rejects the write.
            String readOnly = command("/scoreboard players set Alice hearts 20");
            assertTrue(readOnly.contains("read-only"), readOnly);

            // reset both shapes.
            String resetOne = command("/scoreboard players reset Alice points");
            assertTrue(resetOne.contains("Reset score points of Alice"), resetOne);
            command("/scoreboard players set Alice points 1");
            String resetAll = command("/scoreboard players reset Alice");
            assertTrue(resetAll.contains("Reset all scores of Alice"), resetAll);

            String list = command("/scoreboard players list Bob");
            assertTrue(list.contains("Bob") && list.contains("points"), list);
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theTeamsFamilyAndTheColorDecoration() throws Exception {
        dataDir = Files.createTempDirectory("sbcmd-teams");
        boot();
        try {
            String added = command("/scoreboard teams add red TheReds");
            assertTrue(added.contains("Added new team 'red'"), added);
            assertEquals("TheReds", server.scoreboard().getTeam("red").displayName());

            String colored = command("/scoreboard teams option red color red");
            assertTrue(colored.contains("Value red is now set for option color for team red"),
                    colored);
            // The reference's own walk: the color decorates prefix/suffix.
            assertEquals("\u00a7c", server.scoreboard().getTeam("red").prefix());
            assertEquals("\u00a7r", server.scoreboard().getTeam("red").suffix());
            assertEquals(12, server.scoreboard().getTeam("red").colorId());

            String friendly = command("/scoreboard teams option red friendlyfire false");
            assertTrue(friendly.contains("friendlyfire"), friendly);
            assertTrue(!server.scoreboard().getTeam("red").allowFriendlyFire());

            String visibility = command(
                    "/scoreboard teams option red nametagVisibility hideForOtherTeams");
            // The reference's reply echoes the LOWERCASED option spelling
            // (updateTeam lowercases before the message).
            assertTrue(visibility.contains("nametagvisibility")
                    && visibility.contains("hideForOtherTeams"), visibility);
            assertEquals(net.zaminmc.torch.server.scoreboard.ScoreboardTeam.Visibility
                            .HIDE_FOR_OTHER_TEAMS,
                    server.scoreboard().getTeam("red").nameTagVisibility(),
                    "reply was: " + visibility);

            String joined = command("/scoreboard teams join red Alice Bob");
            assertTrue(joined.contains("Added 2 players to team red"), joined);
            assertEquals("red", server.scoreboard().getTeamOfMember("Alice").name());
            assertEquals("red", server.scoreboard().getTeamOfMember("Bob").name());

            String listed = command("/scoreboard teams list red");
            assertTrue(listed.contains("Alice") && listed.contains("Bob"), listed);

            String left = command("/scoreboard teams leave Bob");
            assertTrue(left.contains("Removed 1 player"), left);
            assertNull(server.scoreboard().getTeamOfMember("Bob"));

            String emptied = command("/scoreboard teams empty red");
            assertTrue(emptied.contains("Removed 1 player from team red"), emptied);
            assertTrue(server.scoreboard().getTeam("red").members().isEmpty());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePersistenceRoundTrip() throws Exception {
        dataDir = Files.createTempDirectory("sbcmd-persist");
        boot();
        try {
            command("/scoreboard objectives add points dummy MyPoints");
            command("/scoreboard objectives add hearts health");
            command("/scoreboard players set Alice points 42");
            command("/scoreboard players set Bob points 10");
            command("/scoreboard objectives setdisplay sidebar points");
            command("/scoreboard teams add red");
            command("/scoreboard teams option red color aqua");
            command("/scoreboard teams join red Alice");
        } finally {
            server.shutdown(null); // the shutdown walk saves the board
        }
        assertTrue(java.nio.file.Path.of(dataDir.toString(), "scoreboard.json").toFile().exists(),
                "the shutdown walk wrote scoreboard.json");

        // The reload: a fresh engine over the same data dir restores it all.
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "sbcmd", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer reloaded = new EngineServer(config);
        reloaded.start();
        try {
            EngineScoreboard board = reloaded.scoreboard();
            assertEquals("MyPoints", board.getObjective("points").displayName());
            assertEquals(42, board.getScore("Alice", board.getObjective("points")).get());
            assertEquals(10, board.getScore("Bob", board.getObjective("points")).get());
            assertEquals("points", board.getDisplayObjective(1).name());
            assertEquals("hearts", board.getObjective("hearts").renderType().key());
            assertEquals(11, board.getTeam("red").colorId(), "aqua = id 11");
            assertEquals("red", board.getTeamOfMember("Alice").name());
            assertEquals("\u00a7b", board.getTeam("red").prefix(), "aqua's chat code");
        } finally {
            reloaded.shutdown(null);
        }
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(50);
        }
    }
}
