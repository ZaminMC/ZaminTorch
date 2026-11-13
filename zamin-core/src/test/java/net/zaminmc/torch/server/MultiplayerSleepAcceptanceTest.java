package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import net.zaminmc.torch.testing.EngineAwaits;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral scenarios for the MULTIPLAYER sleep walk (ML-010 — the
 * reference's ServerWorld canSkipNight semantics, the hundred-tick lie
 * and all): two players, one sleeping holds the night until the other
 * lies down AND every body has lain the reference's own hundred ticks
 * (isSleepingLongEnough, PlayerEntity line 1236 — the instant-jump
 * identity clause retired with the sleeping clock); the morning walk
 * then fires (the clock jump, the weather clear, every sleeper woken
 * with the leave-bed fan); the leave-bed entity action restores a
 * sleeper without touching the clock; and the monster-proximity refusal
 * (trySleep's NOT_SAFE box — x/z ±8, y ±5) gates the entry.
 */
class MultiplayerSleepAcceptanceTest {

    private EngineServer server;

    private final List<String> sleepPoses = new CopyOnWriteArrayList<>();
    private final List<String> wakes = new CopyOnWriteArrayList<>();
    private final List<String> systemLines = new CopyOnWriteArrayList<>();

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "mpsleep" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        // The flat world's night would naturally seed hostiles beside the
        // beds (the refusal box spans ±8 and the players stand two cells
        // from their beds): the walk's own scenes pin their monster box
        // deliberately — the natural cycle stays off, the direct spawnAt
        // fixture (the refusal scene's own zombie) is unaffected.
        server.ticker().submit(() -> ((net.zaminmc.torch.Server) server).gameRules()
                .set("doMobSpawning", "false"));
        await(() -> !((net.zaminmc.torch.Server) server).gameRules()
                .getBoolean("doMobSpawning"), "the natural spawn cycle is off");
        sleepPoses.clear();
        wakes.clear();
        systemLines.clear();
        // The wire's own observation surfaces: the sleep-pose fan (Use Bed
        // 0x0A) and the leave-bed fan (Animation 0x0B code 2) ride the
        // survival listeners; the refusal lines ride the chat listeners.
        server.addSurvivalListener(new EngineServer.SurvivalListener() {
            @Override
            public void onBodyChanged(PlayerSession player) {
            }

            @Override
            public void onDied(PlayerSession player) {
            }

            @Override
            public void onRespawned(PlayerSession player, Position spawn) {
            }

            @Override
            public void onPlayerHurt(PlayerSession player) {
            }

            @Override
            public void onKnockback(PlayerSession player, double vx, double vy, double vz) {
            }

            @Override
            public void onPlayerSleeping(PlayerSession player, BlockPosition bedHead) {
                sleepPoses.add(player.name() + "@" + bedHead.x() + "," + bedHead.y()
                        + "," + bedHead.z());
            }

            @Override
            public void onPlayerWake(PlayerSession player) {
                wakes.add(player.name());
            }
        });
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override
            public void onChatMessage(PlayerSession sender, String content) {
            }

            @Override
            public void onSystemMessage(PlayerSession recipient, String content) {
                if (recipient != null) {
                    systemLines.add(recipient.name() + ": " + content);
                }
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The night band [NIGHT_START, NIGHT_END) — the walk's own gate. */
    private boolean nightHolds() {
        long time = server.world().timeOfDay();
        return time >= MobManager.NIGHT_START && time < MobManager.NIGHT_END;
    }

    /** Advances the clock into the night band and awaits it. */
    private void nightFalls() throws InterruptedException {
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        await(this::nightHolds, "night falls");
    }

    /**
     * Seeds a bed pair engine-side on the flat spawn surface (the placement
     * path itself is SleepAcceptanceTest's own scene): the foot with the
     * head cell south of it, both halves one above the surface.
     */
    private void seedBed(int x, int z) throws InterruptedException {
        BlockPosition foot = new BlockPosition(x, 5, z);
        BlockPosition head = foot.offset(0, 0, 1);
        server.ticker().submit(() -> {
            server.world().setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            server.world().setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        await(() -> EngineServer.isBedHalf(server.world().getBlock(head)), "bed seeded");
    }

    /** The right-click on the bed's foot half — the sleep-entry use. */
    private void clickBed(PlayerSession player, int x, int z) {
        server.useItemOnBlock(player, new BlockPosition(x, 5, z), 1,
                java.util.Optional.empty(), windowId -> { });
    }

    @Test
    void theMorningWaitsForEverySleeperThenWakesThemAll() throws Exception {
        PlayerSession alpha = join("Alpha", new Position(0.5, 5, 0.5));
        PlayerSession beta = join("Beta", new Position(6.5, 5, 0.5));
        await(() -> alpha.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                && beta.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both players reach PLAYING");
        seedBed(2, 0);
        seedBed(8, 0);

        nightFalls();

        // One sleeper: the pose lands (the 0x0A fan's own datum — the bed
        // HEAD cell, BedBlock.use's foot redirect), the night holds.
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "Alpha enters the bed");
        // The pose fan rides the entry's own tick — the settled await (the
        // immediate read can race the fan's add inside the entry tick).
        await(() -> sleepPoses.contains("Alpha@2,5,1"),
                "the sleep pose carries the bed head cell");
        long mark = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= mark + 10,
                "ten ticks pass with one sleeper");
        assertTrue(nightHolds(), "the night holds until every player sleeps");

        // The second sleeper: every body must lie the reference's own
        // hundred ticks (canSkipNight's isSleepingLongEnough gate) — the
        // night holds through Beta's lie, then the morning walk fires and
        // its own observables are the witness: the clock jumps to dawn,
        // both bodies wake, the leave-bed fan rides both wakes, and the
        // pose events recorded both entries. The sleeping flag's lifetime
        // is the ~100-tick lie itself (the CI forensics' one-tick
        // transient retired with the clock — a poll can no longer miss
        // the whole window under load).
        clickBed(beta, 8, 0);
        await(beta::sleeping, "Beta enters the bed");
        await(() -> sleepPoses.contains("Beta@8,5,1"),
                "Beta's pose carried their own head cell");
        // THE GATE'S OWN BAND: at Beta's +40 ticks neither body has lain
        // its hundred — the night holds.
        long betaMark = server.world().totalTicks();
        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().totalTicks() >= betaMark + 40,
                80, "forty ticks of the shared lie pass");
        assertTrue(nightHolds(), "the night holds until every body has lain its hundred");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().timeOfDay() < MobManager.NIGHT_START,
                140, "the morning walk fires once every body has lain long enough");
        assertFalse(alpha.sleeping(), "the walk woke Alpha");
        assertFalse(beta.sleeping(), "the walk woke Beta");
        assertTrue(wakes.contains("Alpha") && wakes.contains("Beta"),
                "the leave-bed fan rode both wakes: " + wakes);
        assertNotNull(alpha.bedSpawn(), "Alpha's bed spawn holds through the walk");
        assertNotNull(beta.bedSpawn(), "Beta's bed spawn holds through the walk");
    }

    @Test
    void theHundredTickLieGatesTheDawnAndTheReEntryRearms() throws Exception {
        PlayerSession alpha = join("LieCounter", new Position(0.5, 5, 0.5));
        PlayerSession bystander = join("Watchman", new Position(6.5, 5, 0.5));
        await(() -> alpha.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                && bystander.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both players reach PLAYING");
        seedBed(2, 0);

        nightFalls();

        // THE SLEEPING CLOCK'S OWN LAW (PlayerEntity.tick lines 213-231 —
        // the bystander stands, so the dawn is doubly gated and this seat
        // pins the CLOCK itself, the dawn gate's own fuel): the lie counts
        // toward the 100 cap while the body lies, and the ninety-nine-edge
        // is safe: the count is observed at a mid band far from the gate.
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "the sleeper enters the bed");
        // The entry tick's own clock advance may have landed before this
        // poll's read (the submit drains at the tick head, the clock walks
        // in the same tick's player-body pass) — the fresh arm is the 0/1
        // pair, never a previous lie's remainder.
        assertTrue(alpha.sleepingTime() <= 1,
                "trySleep's tail arms the clock from zero (line 1142): read "
                        + alpha.sleepingTime());
        EngineAwaits.awaitCondition(server.world(),
                () -> alpha.sleepingTime() >= 40,
                80, "the lie's first forty ticks pass");
        assertTrue(alpha.sleeping(), "the body still lies at forty ticks");
        assertTrue(nightHolds(), "the night holds through the lie's first band");
        EngineAwaits.awaitCondition(server.world(),
                () -> alpha.sleepingTime() >= 60,
                80, "the lie passes its sixtieth tick");
        assertTrue(nightHolds(), "the night holds through the lie's mid band");
        assertTrue(!alpha.isSleepingLongEnough(),
                "the mid-band lie is not long enough (the 100 gate)");

        // THE LEAVE-BED RE-ARM: the body leaves mid-lie — the
        // STOP_SLEEPING wake takes the clock to the capped wake form
        // (wakeUp(false, true, true) sets the 100 whatever the lie
        // reached, line 1188's else arm), the 110-walk drains it to
        // zero, and a RE-ENTRY arms a fresh lie from zero — never the
        // first one's remainder.
        server.entityAction(alpha, 2);
        await(() -> !alpha.sleeping(), "the sleeper leaves mid-lie");
        // The wake form (line 1188's else arm): the capped 100, with the
        // 110-walk's first advance possibly landed before this read (the
        // same-tick phase pair — 100 or 101, never the reset form's 0).
        assertTrue(alpha.sleepingTime() >= 100,
                "the natural wake sets the capped clock (line 1188's else form): read "
                        + alpha.sleepingTime());
        EngineAwaits.awaitCondition(server.world(),
                () -> alpha.sleepingTime() == 0,
                80, "the 110-walk drains the wake window to zero");
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "the sleeper re-enters the bed");
        assertTrue(alpha.sleepingTime() <= 1,
                "the re-entry arms a fresh lie from zero (line 1142 again): read "
                        + alpha.sleepingTime());
        EngineAwaits.awaitCondition(server.world(),
                () -> alpha.sleepingTime() >= 40,
                80, "the second lie's first forty ticks pass");
        assertTrue(nightHolds(),
                "the night holds through the second lie — the re-arm is whole");
    }

    @Test
    void theLeaveBedEntityActionRestoresTheSleeperWithoutTheJump() throws Exception {
        PlayerSession alpha = join("Waker", new Position(0.5, 5, 0.5));
        PlayerSession bystander = join("Bystander", new Position(6.5, 5, 0.5));
        await(() -> alpha.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                && bystander.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both players reach PLAYING");
        seedBed(2, 0);

        nightFalls();
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "the sleeper enters the bed");
        long mark = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= mark + 10,
                "ten ticks pass with the bystander standing");
        assertTrue(nightHolds(), "the night holds while the bystander stands");

        // STOP_SLEEPING (the Entity Action ordinal 2 — the reference's own
        // handlePlayerMovementAction arm): the sleeper stands, the night
        // keeps its clock, the spawn set at entry survives.
        server.entityAction(alpha, 2);
        await(() -> !alpha.sleeping(), "the leave-bed action woke the sleeper");
        assertTrue(wakes.contains("Waker"), "the leave-bed fan fired: " + wakes);
        assertTrue(nightHolds(), "the leave-bed never jumps the clock");
        assertNotNull(alpha.bedSpawn(), "the bed spawn survives the leave-bed");

        // The re-entry after the wake still holds the night (the bystander
        // stands) — the walk counts bodies, not clicks.
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "the sleeper re-enters the bed");
        long secondMark = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= secondMark + 10,
                "ten more ticks pass");
        assertTrue(nightHolds(), "the night still holds with one of two sleeping");
        server.entityAction(alpha, 2);
        await(() -> !alpha.sleeping(), "the sleeper leaves the bed again");
    }

    @Test
    void theMonsterProximityRefusalGatesTheEntry() throws Exception {
        PlayerSession alpha = join("Refused", new Position(0.5, 5, 0.5));
        PlayerSession bystander = join("Sentry", new Position(6.5, 5, 0.5));
        await(() -> alpha.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                && bystander.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both players reach PLAYING");
        seedBed(2, 0);

        nightFalls();
        // A zombie three cells east of the bed head — inside trySleep's own
        // NOT_SAFE box (x/z ±8, y ±5 around the bed).
        var zombie = server.mobs().spawnAt(MobType.ZOMBIE, new Position(5.5, 5, 1.5));
        assertNotNull(zombie, "the zombie spawned");

        clickBed(alpha, 2, 0);
        await(() -> systemLines.stream().anyMatch(line ->
                        line.startsWith("Refused: You may not rest now, there are monsters nearby")),
                "the refusal line reached the clicker: " + systemLines);
        assertFalse(alpha.sleeping(), "the monster box refused the entry");
        long mark = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= mark + 10, "ten ticks pass");
        assertTrue(nightHolds(), "nobody slept: the night holds");

        // The gate is the monster, not a stuck state: the box empty, the
        // entry lands.
        server.ticker().submit(zombie::despawn);
        await(() -> server.mobs().all().stream().noneMatch(mob -> mob == zombie),
                "the zombie left the box");
        clickBed(alpha, 2, 0);
        await(alpha::sleeping, "the entry lands once the box is clear");
        server.entityAction(alpha, 2);
        await(() -> !alpha.sleeping(), "the sleeper leaves the bed");
    }
}
