package net.zaminmc.torch.server.scoreboard;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scoreboard model's own laws (Slice 85 — Scoreboard +
 * ServerScoreboard 1:1): the objective/score/team/display-slot model with
 * the reference's exact semantics — an objective reaches the wire only
 * when displayed (create + slot pointer + scores as the start walk), score
 * updates publish only for displayed objectives, the displaced-slot swap
 * walk, the forceUpdate first publish, the read-only gate, the 19 display
 * slots with the sidebar.team.&lt;color&gt; names, and the team join/leave
 * packets.
 */
class EngineScoreboardTest {

    /** The recording link: every scoreboard wire call. */
    private static final class RecordingLink implements ClientLink {
        record ObjectivePacket(String name, int action, String displayName, String renderType) {
        }
        record ScorePacket(String owner, int mode, String objective, int value) {
        }
        record DisplayPacket(int slot, String objective) {
        }
        record TeamPacket(String name, int action, String displayName, String prefix,
                          String suffix, int flags, String visibility, int color,
                          List<String> members) {
        }

        final List<ObjectivePacket> objectives = new ArrayList<>();
        final List<ScorePacket> scores = new ArrayList<>();
        final List<DisplayPacket> displays = new ArrayList<>();
        final List<TeamPacket> teams = new ArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void scoreboardObjective(ScoreboardObjectiveData data, int action) {
            objectives.add(new ObjectivePacket(data.name(), action, data.displayName(),
                    data.renderType()));
        }

        @Override
        public void scoreboardScore(String owner, int mode, String objective, int value) {
            scores.add(new ScorePacket(owner, mode, objective, value));
        }

        @Override
        public void scoreboardDisplay(int slot, String objectiveName) {
            displays.add(new DisplayPacket(slot, objectiveName));
        }

        @Override
        public void scoreboardTeam(ScoreboardTeamData team, int action, List<String> members) {
            teams.add(new TeamPacket(team.name(), action, team.displayName(), team.prefix(),
                    team.suffix(), team.friendlyFlags(), team.nameTagVisibility(),
                    team.colorId(), List.copyOf(members)));
        }
    }

    private static PlayerSession registered(PlayerRegistry registry, String name,
                                            RecordingLink link) {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), name, link);
        registry.register(session);
        return session;
    }

    @Test
    void anUndisplayedObjectiveNeverReachesTheWireButItsScoresStillTrack() {
        PlayerRegistry registry = new PlayerRegistry();
        RecordingLink alice = new RecordingLink();
        registered(registry, "Alice", alice);
        EngineScoreboard board = new EngineScoreboard(registry);

        ScoreboardObjective points = board.createObjective("points",
                ScoreboardCriterion.DUMMY);
        assertTrue(alice.objectives.isEmpty(), "create broadcasts nothing (the reference's own law)");
        board.getScore("Alice", points).set(5);
        board.getScore("Alice", points).increment();
        assertTrue(alice.scores.isEmpty(), "an undisplayed objective's scores stay off the wire");
        assertEquals(6, board.getScore("Alice", points).get(), "the model still tracks");

        // Displaying drives the full start walk: create + slot pointer + the score.
        board.setDisplayObjective(1, points);
        assertEquals(1, alice.objectives.size(), "the create packet (action 0)");
        assertEquals("points", alice.objectives.get(0).name());
        assertEquals(0, alice.objectives.get(0).action());
        assertEquals("points", alice.objectives.get(0).displayName(), "display name defaults to name");
        assertEquals("integer", alice.objectives.get(0).renderType());
        assertEquals(1, alice.displays.size(), "the slot pointer");
        assertEquals(1, alice.displays.get(0).slot());
        assertEquals("points", alice.displays.get(0).objective());
        assertEquals(1, alice.scores.size(), "one score packet (the forceUpdate publish)");
        assertEquals(new RecordingLink.ScorePacket("Alice", 0, "points", 6), alice.scores.get(0));

        // Now displayed: every update publishes.
        board.getScore("Alice", points).set(10);
        assertEquals(2, alice.scores.size());
        assertEquals(10, alice.scores.get(1).value());
    }

    @Test
    void theFreshCellPublishesOnceEvenAtZeroAndTheReadOnlyGateHolds() {
        PlayerRegistry registry = new PlayerRegistry();
        EngineScoreboard board = new EngineScoreboard(registry);
        ScoreboardObjective health = board.createObjective("hearts",
                ScoreboardCriterion.HEALTH);
        ScoreboardScore cell = board.getScore("Alice", health);
        // The forceUpdate law: set(0) on a fresh cell still fires the hook.
        List<Integer> published = new ArrayList<>();
        // displayedObjectives is private; publishing observed through a link instead
        RecordingLink observer = new RecordingLink();
        registered(registry, "Observer", observer);
        board.setDisplayObjective(2, health);
        cell.set(0);
        assertTrue(observer.scores.stream().anyMatch(s -> s.value() == 0),
                "the fresh cell published its 0");
        // The read-only gate: health is computed, never incremented.
        assertThrows(IllegalStateException.class, cell::increment);
        assertThrows(IllegalStateException.class, () -> cell.increase(1));
        // set() bypasses the gate exactly like the reference's health driver.
        cell.set((int) Math.ceil(13.4f));
        assertEquals(14, cell.get(), "the ceil law");
    }

    @Test
    void theDisplacedSlotSwapWalkAndStopDisplay() {
        PlayerRegistry registry = new PlayerRegistry();
        RecordingLink alice = new RecordingLink();
        registered(registry, "Alice", alice);
        EngineScoreboard board = new EngineScoreboard(registry);
        ScoreboardObjective first = board.createObjective("first", ScoreboardCriterion.DUMMY);
        ScoreboardObjective second = board.createObjective("second", ScoreboardCriterion.DUMMY);
        board.getScore("Alice", first).set(3);
        board.setDisplayObjective(1, first);
        alice.objectives.clear();
        alice.displays.clear();
        alice.scores.clear();

        // Swap the sidebar to the second objective: FIRST still shows nowhere
        // else → its stop walk (remove + cleared pointer); SECOND's start walk.
        board.setDisplayObjective(1, second);
        assertTrue(alice.objectives.stream().anyMatch(o -> o.name().equals("first") && o.action() == 1),
                "the displaced objective's remove packet");
        assertTrue(alice.objectives.stream().anyMatch(o -> o.name().equals("second") && o.action() == 0),
                "the new objective's create packet");
        assertTrue(alice.displays.stream().anyMatch(d -> d.slot() == 1 && d.objective().equals("second")),
                "the slot re-points");
        // Removing the objective clears its slots + sends the stop walk.
        // The reference's own shape: the objective-REMOVE packet is what
        // clears the client's sidebar — the stop walk sends NO cleared-slot
        // pointer for the slot that was already nulled inside the walk
        // (ServerScoreboard.setDisplayObjective's count-after-null branch).
        alice.objectives.clear();
        alice.displays.clear();
        board.removeObjective(second);
        assertTrue(alice.objectives.stream().anyMatch(o -> o.name().equals("second") && o.action() == 1),
                "the removed objective's remove packet");
        assertTrue(alice.displays.stream().noneMatch(d -> d.objective().isEmpty()),
                "no cleared-slot pointer in the remove-objective flow (the reference's own shape)");
        assertNull(board.getObjective("second"));
    }

    @Test
    void theDisplaySlotNameMappingAndTheColoredCriteriaTable() {
        assertEquals("list", EngineScoreboard.displayLocation(0));
        assertEquals("sidebar", EngineScoreboard.displayLocation(1));
        assertEquals("belowName", EngineScoreboard.displayLocation(2));
        assertEquals("sidebar.team.red", EngineScoreboard.displayLocation(3 + 12));
        assertNull(EngineScoreboard.displayLocation(19));
        assertEquals(0, EngineScoreboard.displaySlot("list"));
        assertEquals(1, EngineScoreboard.displaySlot("sidebar"));
        assertEquals(2, EngineScoreboard.displaySlot("belowName"));
        assertEquals(3 + 12, EngineScoreboard.displaySlot("sidebar.team.red"));
        assertEquals(-1, EngineScoreboard.displaySlot("sidebar.team.notacolor"));
        // The criterion families register exactly like the reference.
        assertEquals(38, ScoreboardCriterion.BY_NAME.size(),
                "6 named + 16 teamkill + 16 killedByTeam");
        assertEquals("teamkill.red", ScoreboardCriterion.TEAM_KILL_BY_COLOR[12].name());
        assertEquals("killedByTeam.white", ScoreboardCriterion.KILLED_BY_TEAM_BY_COLOR[15].name());
        assertTrue(ScoreboardCriterion.HEALTH.isReadOnly());
        assertFalse(ScoreboardCriterion.DUMMY.isReadOnly());
        assertEquals(ScoreboardCriterion.RenderType.HEARTS, ScoreboardCriterion.HEALTH.renderType());
        assertEquals(ScoreboardCriterion.RenderType.INTEGER, ScoreboardCriterion.DUMMY.renderType());
    }

    @Test
    void theTeamModelAndItsJoinLeaveWire() {
        PlayerRegistry registry = new PlayerRegistry();
        RecordingLink alice = new RecordingLink();
        registered(registry, "Alice", alice);
        EngineScoreboard board = new EngineScoreboard(registry);

        ScoreboardTeam red = board.addTeam("red");
        assertTrue(alice.teams.stream().anyMatch(t -> t.name().equals("red") && t.action() == 0),
                "the create packet with empty members");
        red.setColorId(12);
        red.setPrefix("[R]");
        board.addMemberToTeam("Alice", "red");
        assertTrue(alice.teams.stream().anyMatch(t -> t.action() == 3
                        && t.members().equals(List.of("Alice"))),
                "the join packet (action 3) carries the member");
        assertEquals(red, board.getTeamOfMember("Alice"));
        // A second team moves the member (the reference's re-join walk).
        ScoreboardTeam blue = board.addTeam("blue");
        board.addMemberToTeam("Alice", "blue");
        assertEquals(blue, board.getTeamOfMember("Alice"));
        assertTrue(red.members().isEmpty());
        assertTrue(alice.teams.stream().anyMatch(t -> t.action() == 4 && t.name().equals("red")),
                "the leave packet (action 4)");
        // The setters fire update packets (action 2).
        alice.teams.clear();
        blue.setAllowFriendlyFire(false);
        assertTrue(alice.teams.stream().anyMatch(t -> t.action() == 2 && (t.flags() & 1) == 0),
                "the update packet carries the packed flags");
        // The duplicate-name and too-long gates.
        assertThrows(IllegalArgumentException.class, () -> board.addTeam("red"));
        assertThrows(IllegalArgumentException.class, () -> board.addTeam("way-too-long-team-name"));
        assertThrows(IllegalArgumentException.class, () -> board.createObjective(
                "an-objective-name-longer-than-16", ScoreboardCriterion.DUMMY));
    }

    @Test
    void theJoinSyncSendsTeamsFirstThenEveryDistinctDisplayedObjective() {
        PlayerRegistry registry = new PlayerRegistry();
        RecordingLink alice = new RecordingLink();
        registered(registry, "Alice", alice);
        EngineScoreboard board = new EngineScoreboard(registry);
        ScoreboardTeam team = board.addTeam("sync");
        board.addMemberToTeam("Alice", "sync");
        ScoreboardObjective shown = board.createObjective("shown", ScoreboardCriterion.DUMMY);
        board.createObjective("hidden", ScoreboardCriterion.DUMMY);
        board.getScore("Alice", shown).set(7);
        board.setDisplayObjective(1, shown);
        // The same objective on a second slot must NOT re-start (the dedup set).
        board.setDisplayObjective(0, shown);

        RecordingLink joinerLink = new RecordingLink();
        PlayerSession joiner = registered(registry, "Joiner", joinerLink);
        board.syncTo(joiner);
        // Teams first.
        assertEquals("sync", joinerLink.teams.get(0).name());
        assertEquals(0, joinerLink.teams.get(0).action());
        assertEquals(List.of("Alice"), joinerLink.teams.get(0).members(),
                "the create packet carries the current members");
        // Then ONE create for 'shown' (not two — the second slot just re-points).
        assertEquals(1, joinerLink.objectives.stream()
                .filter(o -> o.name().equals("shown")).count());
        assertEquals(2, joinerLink.displays.size(), "both slot pointers");
        // The score rides the start walk.
        assertTrue(joinerLink.scores.contains(new RecordingLink.ScorePacket("Alice", 0, "shown", 7)));
        // The hidden objective never arrives.
        assertTrue(joinerLink.objectives.stream().noneMatch(o -> o.name().equals("hidden")));
    }

    @Test
    void theReferenceComparatorLaw() {
        PlayerRegistry registry = new PlayerRegistry();
        EngineScoreboard board = new EngineScoreboard(registry);
        ScoreboardObjective points = board.createObjective("points", ScoreboardCriterion.DUMMY);
        board.getScore("Zed", points).set(1);
        board.getScore("Amy", points).set(1);
        board.getScore("Mid", points).set(9);
        List<ScoreboardScore> ordered = new ArrayList<>(board.getScores(points));
        // The reference's COMPARATOR: value ASCENDING, ties by REVERSE name
        // (Zed before Amy) — the packet emission order, not the client's own
        // sidebar sort.
        assertEquals("Zed", ordered.get(0).owner());
        assertEquals("Amy", ordered.get(1).owner());
        assertEquals("Mid", ordered.get(2).owner());
    }
}
