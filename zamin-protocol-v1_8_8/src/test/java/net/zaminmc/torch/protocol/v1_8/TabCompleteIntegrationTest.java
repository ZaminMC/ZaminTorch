package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tab-Complete (0x14 → 0x3A) end-to-end: command names complete against the
 * dispatcher with the op gate applied, and argument tokens complete against
 * online player names (the historical server-side completion set).
 */
class TabCompleteIntegrationTest extends ProtocolTestBase {

    @Test
    void commandNamesCompleteWithTheOpGate() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();

            // A non-operator asking for "he" sees only what they may run —
            // "/help" completes; the moderator-level "/heal" and the
            // op-gated "/gamemode" must not leak.
            alice.sendTabComplete("/he");
            List<String> completions = alice.readTabCompletions(5_000);
            assertTrue(completions.contains("/help"), completions.toString());
            assertFalse(completions.contains("/heal"), completions.toString());
            assertFalse(completions.contains("/gamemode"), completions.toString());
            assertFalse(completions.contains("/ban"), completions.toString());
        }
        awaitEmptyServer();
    }

    @Test
    void perCommandArgumentCompletionsAnswerTheReferenceArms() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            awaitPlayers("Alice");
            // The suggestion arms sit behind the same op gates as their commands.
            server.ticker().submit(() -> server.playerRegistry()
                    .byName("Alice").ifPresent(p -> p.setOpLevel(2)));
            // The grant rides the ticker: await it before the gated asks.
            awaitCondition(() -> server.playerRegistry().byName("Alice")
                    .map(p -> p.opLevel() >= 2).orElse(false), "Alice's op grant");

            // The gamemode arm (GameModeCommand 1:1): the mode names at arg 1.
            alice.sendTabComplete("/gamemode surv");
            List<String> modes = alice.readTabCompletions(5_000);
            assertTrue(modes.contains("survival") && modes.size() == 1, modes.toString());

            // An empty last token matches the whole candidate list.
            alice.sendTabComplete("/gamemode ");
            List<String> allModes = alice.readTabCompletions(5_000);
            assertTrue(allModes.contains("creative") && allModes.contains("spectator")
                    && allModes.contains("survival") && allModes.contains("adventure"),
                    allModes.toString());

            // The time arm (TimeCommand 1:1): set|add|query at arg 1, the set
            // literals at arg 2, daytime|gametime under query.
            alice.sendTabComplete("/time s");
            List<String> timeArms = alice.readTabCompletions(5_000);
            assertTrue(timeArms.contains("set") && timeArms.size() == 1, timeArms.toString());
            alice.sendTabComplete("/time set ");
            List<String> setLiterals = alice.readTabCompletions(5_000);
            assertTrue(setLiterals.contains("day") && setLiterals.contains("midnight"),
                    setLiterals.toString());
            alice.sendTabComplete("/time query ga");
            List<String> queryArms = alice.readTabCompletions(5_000);
            assertTrue(queryArms.contains("gametime") && !queryArms.contains("daytime"),
                    queryArms.toString());

            // The weather arm 1:1.
            alice.sendTabComplete("/weather th");
            List<String> weather = alice.readTabCompletions(5_000);
            assertTrue(weather.contains("thunder") && weather.size() == 1, weather.toString());

            // The give arm over the engine's own item registry: the
            // namespaced ids at the ITEM position (arg 2 — the vanilla
            // form's own order; the player names ride arg 1's fallback).
            alice.sendTabComplete("/give Alice minecraft:st");
            List<String> items = alice.readTabCompletions(5_000);
            assertTrue(items.contains("minecraft:stone"), items.toString());
            assertFalse(items.contains("minecraft:dirt"), items.toString());

            // The gamerule arm over the registered rule names.
            alice.sendTabComplete("/gamerule doFi");
            List<String> rules = alice.readTabCompletions(5_000);
            assertTrue(rules.contains("doFireTick") && rules.size() == 1, rules.toString());
            alice.sendTabComplete("/gamerule doFireTick ");
            List<String> ruleValues = alice.readTabCompletions(5_000);
            assertTrue(ruleValues.contains("true") && ruleValues.contains("false"),
                    ruleValues.toString());

            // The summon arm carries the LightningBolt special.
            alice.sendTabComplete("/summon Light");
            List<String> summons = alice.readTabCompletions(5_000);
            assertTrue(summons.contains("LightningBolt"), summons.toString());
        }
        awaitEmptyServer();
    }

    @Test
    void belowTheGateTheSuggesterIsNotConsulted() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            awaitPlayers("Alice");
            // A NON-operator asking "/gamemode s": the arm must not leak the
            // mode names — the fallback (the online player names) answers.
            alice.sendTabComplete("/gamemode s");
            List<String> completions = alice.readTabCompletions(5_000);
            assertFalse(completions.contains("survival"), completions.toString());
            assertFalse(completions.contains("creative"), completions.toString());
        }
        awaitEmptyServer();
    }

    @Test
    void argumentTokensCompleteOnlinePlayerNames() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bob");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            awaitPlayers("Alice", "Bob");

            alice.sendTabComplete("/tp Bo");
            List<String> completions = alice.readTabCompletions(5_000);
            assertTrue(completions.contains("Bob"), completions.toString());
            assertFalse(completions.contains("Alice"), completions.toString());
        }
        awaitEmptyServer();
    }
}
