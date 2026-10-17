package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command target selector (TargetSelector 1:1) over the live engine's
 * installed universe: the p/a/e/r walks, the type predicates (including the
 * p/a-type-ignored quirk and the !type matches-players quirk), the score_
 * and team predicates through the real board, the distance/rotation/count
 * arms, the positional short form, and the invalid-type refusal.
 */
class TargetSelectorTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice;
    PlayerSession bob;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "selector" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice");
        bob = join("Bob");
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && bob.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both sessions promoted to PLAYING");
        // Alice ops to 1 (the selector permission gate), Bob stays 0.
        server.ticker().submit(() -> alice.setOpLevel(1));
        await(() -> alice.opLevel() >= 1, "Alice opped");
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    private PlayerSession join(String name) {
        var link = new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private CommandSender senderOf(PlayerSession session) {
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

    private List<TargetSelector.Candidate> select(PlayerSession by, String selector) {
        return TargetSelector.select(TargetSelector.sourceViewOf(senderOf(by)), selector);
    }

    @Test
    void thePlayerWalks() {
        // @p: the nearest player to the source — the self-bias when the
        // source's own body is in the c==1 match.
        List<TargetSelector.Candidate> p = select(alice, "@p");
        assertEquals(1, p.size());
        assertTrue(p.get(0).player());
        assertEquals("Alice", p.get(0).name());

        // @a: every fully-joined player.
        List<TargetSelector.Candidate> a = select(alice, "@a");
        assertEquals(2, a.size());
        assertTrue(a.stream().allMatch(TargetSelector.Candidate::player));

        // The permission gate: Bob (op 0) gets the empty walk.
        assertTrue(select(bob, "@a").isEmpty());
    }

    @Test
    void theTypeWalks() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + 3, at.y(), at.z()));
            server.mobs().spawnAt(MobType.COW, new Position(at.x() - 3, at.y(), at.z()));
        });
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == MobType.ZOMBIE)
                        && server.mobs().all().stream().anyMatch(m -> m.type() == MobType.COW),
                "both mobs spawned");

        // @e: every kind — both players, the zombie, and the cow are in the
        // walk (the fresh world's own animal population rides the same list).
        List<TargetSelector.Candidate> e = select(alice, "@e");
        assertTrue(e.stream().anyMatch(c -> c.player() && "Alice".equals(c.name())));
        assertTrue(e.stream().anyMatch(c -> c.player() && "Bob".equals(c.name())));
        assertTrue(e.stream().anyMatch(c -> "Zombie".equals(c.name())));
        assertTrue(e.stream().anyMatch(c -> "Cow".equals(c.name())));

        // type=Zombie: the mob only.
        List<TargetSelector.Candidate> zombie = select(alice, "@e[type=Zombie]");
        assertEquals(1, zombie.size());
        assertFalse(zombie.get(0).player());
        assertEquals("Zombie", zombie.get(0).name());

        // type=!Zombie: nothing that is a zombie — the players match (never
        // being a zombie is matching the negation).
        List<TargetSelector.Candidate> notZombie = select(alice, "@e[type=!Zombie]");
        assertTrue(notZombie.stream().noneMatch(c -> "Zombie".equals(c.name())));
        assertTrue(notZombie.stream().anyMatch(c -> c.player() && "Alice".equals(c.name())));

        // The p/a-type-ignored quirk: @p[type=Zombie] still answers a PLAYER.
        List<TargetSelector.Candidate> pTyped = select(alice, "@p[type=Zombie]");
        assertEquals(1, pTyped.size());
        assertTrue(pTyped.get(0).player(), "the type arg is ignored on p/a in 1.8.8");

        // r with a type: the type predicate replaces the players-only one.
        List<TargetSelector.Candidate> rTyped = select(alice, "@r[type=Zombie]");
        assertEquals(1, rTyped.size());
        assertFalse(rTyped.get(0).player());

        // The invalid type: the refusal line + the empty walk.
        List<TargetSelector.Candidate> invalid = select(alice, "@e[type=NotAThing]");
        assertTrue(invalid.isEmpty());

        // The case-sensitivity: the registry keys are the capitalized
        // spellings (isKey is exact).
        assertTrue(select(alice, "@e[type=zombie]").isEmpty());
    }

    @Test
    void theBoardPredicates() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + 3, at.y(), at.z()));
        });
        await(() -> server.mobs().all().size() >= 1, "the zombie spawned");
        server.ticker().submit(() -> {
            var board = server.scoreboard();
            var objective = board.createObjective("points",
                    net.zaminmc.torch.server.scoreboard.ScoreboardCriterion.DUMMY);
            board.getScore("Alice", objective).set(5);
            board.getScore("Bob", objective).set(3);
            var red = board.addTeam("red");
            board.addMemberToTeam("Alice", "red");
        });
        await(() -> server.scoreboard().getTeamOfMember("Alice") != null, "the team registered");

        // score_points_min=4: Alice only (Bob's 3 is below).
        List<TargetSelector.Candidate> minScore = select(alice, "@a[score_points_min=4]");
        assertEquals(1, minScore.size());
        assertEquals("Alice", minScore.get(0).name());

        // score_points=4 (the non-min arm: at most 4): Bob only.
        List<TargetSelector.Candidate> maxScore = select(alice, "@a[score_points=4]");
        assertEquals(1, maxScore.size());
        assertEquals("Bob", maxScore.get(0).name());

        // team=red: Alice; team=!red: Bob.
        assertEquals(1, select(alice, "@a[team=red]").size());
        List<TargetSelector.Candidate> notRed = select(alice, "@a[team=!red]");
        assertEquals(1, notRed.size());
        assertEquals("Bob", notRed.get(0).name());

        // name=!Alice: Bob.
        List<TargetSelector.Candidate> notAlice = select(alice, "@a[name=!Alice]");
        assertEquals(1, notAlice.size());
        assertEquals("Bob", notAlice.get(0).name());
    }

    @Test
    void theDistanceAndCountWalks() throws Exception {
        Position at = alice.position();
        server.ticker().submit(() -> {
            server.mobs().spawnAt(MobType.ZOMBIE, new Position(at.x() + 30, at.y(), at.z()));
        });
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == MobType.ZOMBIE),
                "the far zombie spawned");

        // r=2: nothing within 2 of Alice's feet (the zombie is 30 out).
        assertTrue(select(alice, "@e[type=Zombie,r=2]").isEmpty());
        // r=40: the zombie lands.
        assertEquals(1, select(alice, "@e[type=Zombie,r=40]").size());
        // rm=10 + r=40: the band.
        assertEquals(1, select(alice, "@e[type=Zombie,rm=10,r=40]").size());
        // rm=50: nothing (the zombie is nearer).
        assertTrue(select(alice, "@e[type=Zombie,rm=50]").isEmpty());

        // c on a/e defaults 0 (all); c=1 on p defaults 1.
        assertTrue(select(alice, "@e").size() >= 4, "players + the fresh world's animals");
        List<TargetSelector.Candidate> nearestOne = select(alice, "@a[c=1]");
        assertEquals(1, nearestOne.size(), "c=1 keeps the nearest player only");

        // The positional short form pins the world AND the coordinates:
        // "@e[x=...,y=...,z=...,r=...]" spelled positionally.
        int x = (int) Math.floor(at.x());
        int y = (int) Math.floor(at.y());
        int z = (int) Math.floor(at.z());
        List<TargetSelector.Candidate> positional = select(alice, "@e[" + x + "," + y + "," + z + ",40]");
        assertTrue(positional.stream().anyMatch(c -> "Zombie".equals(c.name())),
                "the positional form feeds x/y/z/r");
    }

    @Test
    void theFormWalks() {
        assertTrue(TargetSelector.isValid("@p"));
        assertTrue(TargetSelector.isValid("@e[type=Zombie,r=10]"));
        assertFalse(TargetSelector.isValid("@x"));
        assertFalse(TargetSelector.matchesMultiple("@p"));
        assertTrue(TargetSelector.matchesMultiple("@a"));
        assertTrue(TargetSelector.matchesMultiple("@e"));
        assertTrue(TargetSelector.matchesMultiple("@p[c=2]"));
        assertEquals(359, TargetSelector.wrapDegrees(-1));
        assertEquals(0, TargetSelector.wrapDegrees(0));
        assertEquals(180, TargetSelector.wrapDegrees(180));
        assertEquals(179, TargetSelector.wrapDegrees(539));
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
