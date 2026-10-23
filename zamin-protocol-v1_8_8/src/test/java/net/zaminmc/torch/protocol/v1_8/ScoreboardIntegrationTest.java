package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.scoreboard.ScoreboardCriterion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The scoreboard wire family over the real socket (Slice 85 — 0x3B/0x3C/
 * 0x3D/0x3E): the start-display walk when an objective lands in a display
 * slot (create + slot pointer + every score), the score updates for
 * displayed objectives only, the team create/join/leave packets, and the
 * JOIN SYNC (every team first, then every distinct displayed objective —
 * a joiner mid-session learns the full board).
 */
class ScoreboardIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000; // the scripted clients answer only when the test drives it
    }

    @Test
    void theObjectiveDisplayWalkAndScoreUpdatesReachTheWire() throws Exception {
        try (TestClient18 player = new TestClient18("127.0.0.1", adapter.boundPort())) {
            player.sendHandshake(47, 2);
            player.sendLoginStart("Boarder");
            player.readLoginSuccess();
            player.readUntilPositionAndLook();
            awaitPlayers("Boarder");

            // An objective alone: nothing on the wire (the reference's own law).
            server.ticker().submit(() -> server.scoreboard().createObjective(
                    "wirePoints", ScoreboardCriterion.DUMMY));
            awaitCondition(() -> server.scoreboard().getObjective("wirePoints") != null,
                    "the objective exists");
            EngineAwaits.awaitTicks(server.world(), 4, "engine progress (a 200 wall settle)");
            player.setReadTimeout(600);
            boolean silent;
            try {
                player.readScoreboardObjective(500);
                silent = false;
            } catch (java.io.IOException expectedTimeout) {
                silent = true; // the socket read timed out with no packet: nothing was sent
            }
            assertTrue(silent, "an undisplayed objective never reaches the wire");

            // Displaying it: create + slot pointer + the fresh score publish.
            server.ticker().submit(() -> {
                var board = server.scoreboard();
                board.getScore("Boarder", board.getObjective("wirePoints")).set(3);
                board.setDisplayObjective(1, board.getObjective("wirePoints"));
            });
            awaitCondition(() -> server.scoreboard().getDisplayObjective(1) != null,
                    "the slot is set");
            TestClient18.ObjectiveWire create = player.readScoreboardObjective(5_000);
            assertEquals("wirePoints", create.name());
            assertEquals(0, create.action());
            assertEquals("wirePoints", create.displayName());
            assertEquals("integer", create.renderType());
            TestClient18.DisplayWire display = player.readDisplayScoreboard(5_000);
            assertEquals(1, display.slot());
            assertEquals("wirePoints", display.objective());
            TestClient18.ScoreWire score = player.readUpdateScore(5_000);
            assertEquals("Boarder", score.owner());
            assertEquals(0, score.mode());
            assertEquals("wirePoints", score.objective());
            assertEquals(3, score.value());
        }
        awaitEmptyServer();
    }

    @Test
    void theHealthCriterionPublishesHeartsOnTheWire() throws Exception {
        try (TestClient18 patient = new TestClient18("127.0.0.1", adapter.boundPort())) {
            patient.sendHandshake(47, 2);
            patient.sendLoginStart("Patient");
            patient.readLoginSuccess();
            patient.readUntilPositionAndLook();
            awaitPlayers("Patient");

            AtomicReference<net.zaminmc.torch.server.player.PlayerSession> session =
                    new AtomicReference<>();
            awaitCondition(() -> {
                session.set(server.playerRegistry().byName("Patient").orElse(null));
                return session.get() != null;
            }, "session present");
            server.ticker().submit(() -> {
                var board = server.scoreboard();
                board.createObjective("hearts", ScoreboardCriterion.HEALTH);
                board.setDisplayObjective(2, board.getObjective("hearts"));
            });
            awaitCondition(() -> server.scoreboard().getDisplayObjective(2) != null,
                    "the health objective displays");
            TestClient18.ObjectiveWire create = patient.readScoreboardObjective(5_000);
            assertEquals("hearts", create.name());
            assertEquals("hearts", create.renderType(), "the health criterion renders hearts");
            patient.readDisplayScoreboard(5_000);
            TestClient18.ScoreWire full = patient.readUpdateScore(5_000);
            assertEquals("Patient", full.owner());
            assertEquals(20, full.value(), "full health publishes ceil(20)");

            // A wound: the next body tick re-publishes the health objective.
            server.ticker().submit(() -> session.get().setBody(13.2f,
                    session.get().food(), session.get().saturation()));
            awaitCondition(() -> session.get().health() == 13.2f, "the wound landed");
            TestClient18.ScoreWire hurt = patient.readUpdateScore(5_000);
            assertEquals(14, hurt.value(), "ceil(13.2) — the criterion's own rounding");
        }
        awaitEmptyServer();
    }

    @Test
    void theTeamPacketsAndTheJoinSync() throws Exception {
        try (TestClient18 first = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 second = new TestClient18("127.0.0.1", adapter.boundPort())) {
            first.sendHandshake(47, 2);
            first.sendLoginStart("Captain");
            first.readLoginSuccess();
            first.readUntilPositionAndLook();
            awaitPlayers("Captain");

            // A team with a member while Captain is connected: the create,
            // then the join (action 3) — one packet each.
            server.ticker().submit(() -> {
                var board = server.scoreboard();
                var team = board.addTeam("wire");
                team.setColorId(12);
                team.setPrefix("[W]");
                board.addMemberToTeam("Captain", "wire");
            });
            awaitCondition(() -> server.scoreboard().getTeamOfMember("Captain") != null,
                    "the member joined");
            TestClient18.TeamWire created = first.readTeamPacket(5_000);
            assertEquals("wire", created.name());
            assertEquals(0, created.action());
            assertTrue(created.members().isEmpty(), "the create carries no members yet");
            // The prefix setter fires the update broadcast (action 2) — the
            // reference's own Team.setPrefix -> onTeamUpdated walk.
            TestClient18.TeamWire updated = first.readTeamPacket(5_000);
            assertEquals(2, updated.action(), "the prefix update");
            assertEquals("[W]", updated.prefix());
            assertEquals(12, updated.color(), "the color rides the update packet's payload");
            TestClient18.TeamWire join = first.readTeamPacket(5_000);
            assertEquals(3, join.action(), "the join action");
            assertEquals(List.of("Captain"), join.members());

            // A displayed objective with a score, then a JOINER mid-session:
            // the join sync sends the team first, then the objective walk.
            server.ticker().submit(() -> {
                var board = server.scoreboard();
                board.createObjective("joined", ScoreboardCriterion.DUMMY);
                board.getScore("Captain", board.getObjective("joined")).set(11);
                board.setDisplayObjective(1, board.getObjective("joined"));
            });
            awaitCondition(() -> server.scoreboard().getDisplayObjective(1) != null
                    && server.scoreboard().getDisplayObjective(1).name().equals("joined"),
                    "the joined objective displays");

            second.sendHandshake(47, 2);
            second.sendLoginStart("Rookie");
            second.readLoginSuccess();
            // The join sync rides BEFORE the position packet (the reference's
            // own place in the join sequence), so the join collector grabs it.
            TestClient18.JoinCollector joinBurst = second.new JoinCollector();
            joinBurst.readUntilPosition();
            awaitPlayers("Captain", "Rookie");

            assertTrue(joinBurst.teams.stream().anyMatch(t -> t.action() == 0
                            && t.name().equals("wire") && t.prefix().equals("[W]")
                            && t.color() == 12
                            && t.members().equals(List.of("Captain"))),
                    "the join sync's team create with the current members + decoration");
            assertTrue(joinBurst.objectives.stream().anyMatch(o -> o.action() == 0
                            && o.name().equals("joined")),
                    "the join sync's objective create");
            assertTrue(joinBurst.displays.stream().anyMatch(d -> d.slot() == 1
                            && d.objective().equals("joined")),
                    "the join sync's slot pointer");
            assertTrue(joinBurst.scores.stream().anyMatch(s -> s.owner().equals("Captain")
                            && s.objective().equals("joined") && s.value() == 11),
                    "the join sync's score");
        }
        awaitEmptyServer();
    }
}
