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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral scenarios for the MULTIPLAYER sleep walk (ML-010 — the
 * reference's ServerWorld areAllPlayersAsleep semantics over the engine's
 * single-player contract): two players, one sleeping holds the night until
 * the other lies down; the morning walk then fires (the clock jump, the
 * weather clear, every sleeper woken with the leave-bed fan); the
 * leave-bed entity action restores a sleeper without touching the clock;
 * and the monster-proximity refusal (trySleep's NOT_SAFE box — x/z ±8,
 * y ±5) gates the entry. The single-player contract itself stays pinned by
 * SleepAcceptanceTest, unmodified.
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
        assertTrue(sleepPoses.contains("Alpha@2,5,1"),
                "the sleep pose carries the bed head cell: " + sleepPoses);
        long mark = server.world().totalTicks();
        await(() -> server.world().totalTicks() >= mark + 10,
                "ten ticks pass with one sleeper");
        assertTrue(nightHolds(), "the night holds until every player sleeps");

        // The second sleeper: the morning walk fires the very next tick —
        // the sleeping flag's lifetime is that one tick (the walk wakes
        // every sleeper), so the walk's own observables are the witness:
        // the clock jumps to dawn, both bodies wake, the leave-bed fan
        // rides both wakes, and the pose events recorded both entries.
        clickBed(beta, 8, 0);
        await(() -> server.world().timeOfDay() < MobManager.NIGHT_START,
                "the morning walk fires once both sleep");
        assertFalse(alpha.sleeping(), "the walk woke Alpha");
        assertFalse(beta.sleeping(), "the walk woke Beta");
        assertTrue(wakes.contains("Alpha") && wakes.contains("Beta"),
                "the leave-bed fan rode both wakes: " + wakes);
        assertTrue(sleepPoses.contains("Beta@8,5,1"),
                "Beta's pose carried their own head cell: " + sleepPoses);
        assertNotNull(alpha.bedSpawn(), "Alpha's bed spawn holds through the walk");
        assertNotNull(beta.bedSpawn(), "Beta's bed spawn holds through the walk");
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
