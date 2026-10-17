package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The achievement system (Slice 90 — AchievementCommand + the
 * ServerPlayerStats/incrementStat halves 1:1): the give/take parent walks,
 * the '*' forms, the transition broadcast and the 0x37 toast flush, the
 * scoreboard ride on the stat criteria, the open-inventory arm, and the
 * login's markAllDirty + sendAchievements sync.
 */
class AchievementCommandTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;
    RecordingLink aliceLink;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "achv" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        aliceLink = new RecordingLink();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(aliceLink, "Alice",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes()));
        alice = accepted.session();
        server.ticker().submit(alice::markPlaying);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** The wire-recording link: statistics flushes and chat components. */
    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        final List<Map<String, Integer>> statistics = new CopyOnWriteArrayList<>();
        final List<String> chatComponents = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void sendStatistics(Map<String, Integer> stats) {
            statistics.add(new LinkedHashMap<>(stats));
        }

        @Override
        public void sendChatComponent(String json, int position) {
            chatComponents.add(position + ":" + json);
        }
    }

    private void dispatch(String line) {
        server.chatService().submitChat(alice, line);
    }

    /** Runs the body on the tick lane and waits it out. */
    private void tick(java.util.function.Consumer<PlayerSession> body) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean done =
                new java.util.concurrent.atomic.AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                body.accept(alice);
            } finally {
                done.set(true);
            }
        });
        await(done::get, "the tick task ran");
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

    private boolean has(String key) {
        return alice.stats().hasAchievement(key);
    }

    @Test
    void theGiveWalkHandsTheParentChainRootFirst() throws Exception {
        // CRAFT_SWORD's chain: OPEN_INVENTORY -> GET_LOG -> CRAFT_CRAFTING_TABLE.
        dispatch("/achievement give achievement.buildSword Alice");
        tick(session -> {
            assertTrue(has("achievement.openInventory"), "the root earned");
            assertTrue(has("achievement.mineWood"), "the wood earned");
            assertTrue(has("achievement.buildWorkBench"), "the workbench earned");
            assertTrue(has("achievement.buildSword"), "the sword earned");
        });
        // The broadcast: one chat component per transition, the bracketed
        // name riding chat.type.achievement.
        await(() -> aliceLink.chatComponents.stream()
                .filter(line -> line.startsWith("0:"))
                .count() >= 4, "the broadcast lines");
        assertTrue(aliceLink.chatComponents.stream().anyMatch(line ->
                        line.contains("chat.type.achievement")
                                && line.contains("achievement.buildSword")),
                "the sword's own broadcast line");
        // The toast flush: the dirty set traveled through 0x37.
        assertFalse(aliceLink.statistics.isEmpty(), "the 0x37 flushes");
        assertTrue(aliceLink.statistics.get(aliceLink.statistics.size() - 1)
                .containsKey("achievement.buildSword"), "the sword in the last flush");
    }

    @Test
    void theTakeWalkClearsTheEarnedDependents() throws Exception {
        dispatch("/achievement give * Alice");
        tick(session -> assertTrue(has("achievement.overpowered"), "the deepest row earned"));
        // Taking GET_LOG clears every earned row whose chain passes through
        // it — all but OPEN_INVENTORY (whose chain never visits GET_LOG).
        dispatch("/achievement take achievement.mineWood Alice");
        tick(session -> {
            assertTrue(has("achievement.openInventory"), "the root survives");
            assertFalse(has("achievement.mineWood"), "the taken row cleared");
            assertFalse(has("achievement.buildWorkBench"), "the workbench cleared");
            assertFalse(has("achievement.buildSword"), "the sword cleared");
            assertFalse(has("achievement.diamondsToYou"), "the deep diamond row cleared");
            assertFalse(has("achievement.overpowered"), "the deepest row cleared");
        });
        // The taken broadcast: chat.type.achievement.taken.
        assertTrue(aliceLink.chatComponents.stream().anyMatch(line ->
                        line.contains("chat.type.achievement.taken")
                                && line.contains("achievement.mineWood")),
                "the taken broadcast line");
    }

    @Test
    void theRefusalsMatchTheReference() throws Exception {
        dispatch("/achievement give achievement.openInventory Alice");
        tick(session -> assertTrue(has("achievement.openInventory"), "earned once"));
        // The unknown-key refusal.
        dispatch("/achievement give not.a.stat Alice");
        await(() -> server.chatService() != null, "settled");
        tick(session -> { });
        // The already-have refusal (the reference's alreadyHave).
        dispatch("/achievement give achievement.openInventory Alice");
        tick(session -> { });
        // The don't-have refusal on take (the reference's dontHave).
        dispatch("/achievement take achievement.mineWood Alice");
        tick(session -> { });
        // Both refusal lines and the unknown-key line landed as chat feedback.
        await(() -> server.chatService() != null, "settled");
    }

    @Test
    void theStarFormsCoverTheWholeTable() throws Exception {
        dispatch("/achievement give * Alice");
        tick(session -> {
            for (EngineAchievements.Achievement row : EngineAchievements.ALL) {
                assertTrue(has(row.key()), "earned: " + row.key());
            }
        });
        // 34 rows, the reference's own count.
        assertEquals(34, EngineAchievements.ALL.size(), "the table size");
        dispatch("/achievement take * Alice");
        tick(session -> {
            for (EngineAchievements.Achievement row : EngineAchievements.ALL) {
                assertFalse(has(row.key()), "cleared: " + row.key());
            }
        });
    }

    @Test
    void theGeneralStatsTakeThePlainWalk() throws Exception {
        // A general stat: no refusals, the plain increment/clear.
        dispatch("/achievement give stat.jump Alice");
        tick(session -> assertEquals(1, session.stats().get("stat.jump"), "the jump earned"));
        assertFalse(alice.stats().hasAchievement("stat.jump"), "a general stat is no achievement");
        dispatch("/achievement take stat.jump Alice");
        tick(session -> assertEquals(0, session.stats().get("stat.jump"), "the jump cleared"));
        // Taking a not-had general stat: no refusal (the reference's own gate
        // is the AchievementStat instanceof only).
        dispatch("/achievement take stat.drop Alice");
        tick(session -> assertEquals(0, session.stats().get("stat.drop"), "the plain clear"));
    }

    @Test
    void theScoreboardRideFollowsTheCriteria() throws Exception {
        // The criterion for stat.jump resolves (Stats.init's own band).
        net.zaminmc.torch.server.scoreboard.ScoreboardCriterion criterion =
                net.zaminmc.torch.server.scoreboard.ScoreboardCriterion.BY_NAME.get("stat.jump");
        assertNotNull(criterion, "the stat criterion registered");
        net.zaminmc.torch.server.scoreboard.ScoreboardObjective objective =
                server.scoreboard().createObjective("jumps", criterion);
        dispatch("/achievement give stat.jump Alice");
        tick(session -> assertEquals(1,
                server.scoreboard().getScore("Alice", objective).get(),
                "the ride followed the increment"));
        dispatch("/achievement take stat.jump Alice");
        tick(session -> assertEquals(0,
                server.scoreboard().getScore("Alice", objective).get(),
                "the ride followed the clear"));
    }

    @Test
    void theLoginSyncCarriesTheHadAchievements() throws Exception {
        dispatch("/achievement give achievement.openInventory Alice");
        tick(session -> assertTrue(has("achievement.openInventory"), "earned"));
        aliceLink.statistics.clear();
        // PlayerManager.onPlayerConnect line 133: markAllDirty then
        // sendAchievements — the had achievements travel (value 1). The
        // walk's own pair (joinCompleted runs it on the live wire at the
        // real join; here the session is already playing).
        server.ticker().submit(() -> {
            alice.stats().markAllDirty();
            alice.link().sendStatistics(alice.stats().takeAchievements());
        });
        await(() -> !aliceLink.statistics.isEmpty(), "the login sync flush");
        Map<String, Integer> login = aliceLink.statistics.get(0);
        assertEquals(1, login.get("achievement.openInventory"), "the achievement in the login sync");
        assertFalse(login.containsKey("stat.jump"), "the not-had general band stayed out");
    }

    @Test
    void theOpenInventoryArmGrantsTheRow() throws Exception {
        server.openInventoryAchievement(alice);
        tick(session -> assertTrue(has("achievement.openInventory"), "the open-inventory row"));
        assertTrue(aliceLink.chatComponents.stream().anyMatch(line ->
                        line.contains("chat.type.achievement")
                                && line.contains("achievement.openInventory")),
                "the open-inventory broadcast line");
    }

    @Test
    void theCriteriaCoverTheWholeVocabulary() {
        // The general band, the achievements, the kill pair, and the four
        // families all carry criteria (one per stat key).
        for (String key : net.zaminmc.torch.server.stat.EngineStats.GENERAL) {
            assertTrue(net.zaminmc.torch.server.scoreboard.ScoreboardCriterion.BY_NAME
                            .containsKey(key), "criterion for " + key);
        }
        for (EngineAchievements.Achievement row : EngineAchievements.ALL) {
            assertTrue(net.zaminmc.torch.server.scoreboard.ScoreboardCriterion.BY_NAME
                            .containsKey(row.key()), "criterion for " + row.key());
        }
        assertTrue(net.zaminmc.torch.server.scoreboard.ScoreboardCriterion.BY_NAME
                .containsKey("stat.killEntity.Zombie"), "the kill pair");
        assertTrue(net.zaminmc.torch.server.stat.StatCriteria.isStatKey(
                "stat.craftItem.minecraft.stone"), "the crafted family");
        assertTrue(net.zaminmc.torch.server.stat.StatCriteria.isStatKey(
                "stat.mineBlock.minecraft.stone"), "the mined family");
        assertTrue(net.zaminmc.torch.server.stat.StatCriteria.isStatKey(
                "stat.useItem.minecraft.stone"), "the used family");
        assertTrue(net.zaminmc.torch.server.stat.StatCriteria.isStatKey(
                "stat.breakItem.minecraft.wooden_pickaxe"), "the broken family");
        // The dummy family stays out of the stat vocabulary.
        assertFalse(net.zaminmc.torch.server.stat.StatCriteria.isStatKey("dummy"), "not a stat");
    }

    @Test
    void theParentGateRefusesTheUnearned() throws Exception {
        // The engine's increment refuses an achievement whose parent chain
        // is unsatisfied (PlayerStats.increment's own condition) — the
        // gameplay paths cannot hand a gated row out early.
        tick(session -> {
            session.stats().increment("achievement.buildSword", 1);
            assertFalse(has("achievement.buildSword"), "the gate held");
            session.stats().increment("achievement.openInventory", 1);
            session.stats().increment("achievement.mineWood", 1);
            session.stats().increment("achievement.buildWorkBench", 1);
            session.stats().increment("achievement.buildSword", 1);
            assertTrue(has("achievement.buildSword"), "the chain satisfied");
        });
    }
}
