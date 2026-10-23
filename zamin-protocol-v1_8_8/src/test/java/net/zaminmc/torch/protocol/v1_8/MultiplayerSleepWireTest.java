package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-010's wire proof — the multiplayer sleep walk over the real 1.8.8
 * stack: the other player observes the Use Bed (0x0A) pose packet at the
 * bed head cell; the leave-bed entity action (Entity Action ordinal 2)
 * renders the Animation (0x0B) code 2 on every observer; and the
 * monster-proximity refusal reaches the clicker as the vanilla chat line.
 * The all-sleepers walk itself (the clock jump) is MultiplayerSleep
 * AcceptanceTest's engine-side scene — here it is the wire's Time Update
 * that carries the morning.
 */
class MultiplayerSleepWireTest extends ProtocolTestBase {

    /** The scripted flow never answers keep-alives; a long interval must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    private static final int BED_A_FOOT_X = 2;
    private static final int BED_A_Z = 0;
    private static final int BED_B_FOOT_X = 2;
    private static final int BED_B_Z = 3; // the second pair two cells south

    /** One joined, positioned client. */
    private TestClient18 login(String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        return client;
    }

    /** Seeds a bed pair engine-side on the flat spawn surface. */
    private void seedBed(int x, int z) throws InterruptedException {
        BlockPosition foot = new BlockPosition(x, 5, z);
        BlockPosition head = foot.offset(0, 0, 1);
        server.ticker().submit(() -> {
            server.world().setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            server.world().setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        awaitCondition(() -> net.zaminmc.torch.server.EngineServer
                .isBedHalf(server.world().getBlock(head)), "bed seeded");
    }

    private void nightFalls() throws InterruptedException {
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(MobManager.NIGHT_START + 100));
        awaitCondition(() -> {
            long time = server.world().timeOfDay();
            return time >= MobManager.NIGHT_START && time < MobManager.NIGHT_END;
        }, "night falls");
    }

    /**
     * Echoes the session's authoritative position — the movement packet the
     * scripted client never sends on its own. The wake fan's position
     * anchor (the reference's trailing networkHandler.teleport) opens the
     * teleport window, and uses refuse until the client's movement
     * acknowledges the anchor (handlePlayerUse line 475's own first
     * consult) — a real client sends its position every tick, the script
     * sends it here.
     */
    private void acknowledgeAnchor(TestClient18 client, String name) throws Exception {
        var session = server.playerRegistry().byName(name).orElseThrow();
        var at = session.position();
        client.sendPosition(at.x(), at.y(), at.z(), true);
    }

    /** Decodes a Use Bed (0x0A) body: {entityId, x, y, z}. */
    private static long[] parseUseBed(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        long entityId = ByteBufOps.readVarInt(buffer);
        long packed = buffer.readLong();
        int[] cell = ByteBufOps.decodePackedBlockPosition(packed);
        return new long[]{entityId, cell[0], cell[1], cell[2]};
    }

    /** Decodes an Animation (0x0B) body: {entityId, animation}. */
    private static long[] parseAnimation(byte[] payload) {
        ByteBuf buffer = Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // packet id
        long entityId = ByteBufOps.readVarInt(buffer);
        long animation = buffer.readUnsignedByte();
        return new long[]{entityId, animation};
    }

    /**
     * Reads Time Update (0x03) packets until one shows the day band — the
     * periodic cycle sync keeps sending night clocks while the walk has not
     * fired, so the day-band copy is the morning's own witness.
     */
    private long awaitMorningTimeUpdate(TestClient18 client) throws Exception {
        for (int reads = 0; reads < 20; reads++) {
            byte[] payload = client.readPacketOfType(Protocol18.S2C_TIME_UPDATE, 5_000);
            ByteBuf buffer = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buffer); // packet id
            buffer.readLong(); // totalTicks
            long timeOfDay = buffer.readLong();
            if (timeOfDay < MobManager.NIGHT_START) {
                return timeOfDay;
            }
        }
        throw new AssertionError("no day-band Time Update arrived");
    }

    @Test
    void theOtherPlayerObservesThePoseTheMorningAndTheLeaveBed() throws Exception {
        // The natural spawn cycle stays off (the night would seed hostiles
        // inside the refusal box beside the beds); the refusal scene's own
        // zombie is a direct engine spawn, unaffected by the rule.
        server.ticker().submit(() -> ((net.zaminmc.torch.Server) server).gameRules()
                .set("doMobSpawning", "false"));
        awaitCondition(() -> !((net.zaminmc.torch.Server) server).gameRules()
                .getBoolean("doMobSpawning"), "the natural spawn cycle is off");
        try (TestClient18 sleeper = login("Sleeper");
             TestClient18 watcher = login("Watcher")) {
            awaitPlayers("Sleeper", "Watcher");
            // The sleep entry gates on PLAYING (the join's completing arm);
            // await it before the first click.
            awaitCondition(() -> server.playerRegistry().byName("Sleeper")
                            .map(s -> s.state() == net.zaminmc.torch.entity.PlayerState.PLAYING)
                            .orElse(false)
                            && server.playerRegistry().byName("Watcher")
                            .map(w -> w.state() == net.zaminmc.torch.entity.PlayerState.PLAYING)
                            .orElse(false),
                    "both clients reach PLAYING");
            seedBed(BED_A_FOOT_X, BED_A_Z);
            seedBed(BED_B_FOOT_X, BED_B_Z);

            nightFalls();

            // --- one sleeper: the other player's wire observes the pose ---
            sleeper.sendBlockPlacement(BED_A_FOOT_X, 5, BED_A_Z, 1, -1);
            long[] pose = parseUseBed(watcher.readPacketOfType(
                    Protocol18.S2C_USE_BED, 5_000));
            assertTrue(pose[0] > 0, "the pose carries the sleeper's wire entity id");
            assertEquals(BED_A_FOOT_X, (int) pose[1], "the pose's bed cell x");
            assertEquals(5, (int) pose[2], "the pose's bed cell y");
            assertEquals(BED_A_Z + 1, (int) pose[3],
                    "the pose carries the bed HEAD cell (the foot redirect)");
            // The sleeper's own wire carries the same packet (trySleep's
            // self arm).
            long[] ownPose = parseUseBed(sleeper.readPacketOfType(
                    Protocol18.S2C_USE_BED, 5_000));
            assertEquals(BED_A_Z + 1, (int) ownPose[3], "the self pose's head cell");
            assertTrue(sleeper.readChatLineSkippingBroadcasts(5_000)
                    .contains("spawn point"), "the entry's spawn line");

            // The night holds with the watcher standing (the engine-side
            // walk gate, witnessed through the clock's own advance).
            long mark = server.world().totalTicks();
            awaitCondition(() -> server.world().totalTicks() >= mark + 10,
                    "ten ticks pass with one sleeper");
            long held = server.world().timeOfDay();
            assertTrue(held >= MobManager.NIGHT_START && held < MobManager.NIGHT_END,
                    "the night holds until both sleep");

            // --- both sleeping: the morning walk's wire — the day clock ---
            watcher.sendBlockPlacement(BED_B_FOOT_X, 5, BED_B_Z, 1, -1);
            // The watcher's own pose and the sleeper's observation of it.
            long[] watcherOwnPose = parseUseBed(watcher.readPacketOfType(
                    Protocol18.S2C_USE_BED, 5_000));
            assertEquals(BED_B_Z + 1, (int) watcherOwnPose[3], "the watcher's own head cell");
            long[] sleeperSeesWatcher = parseUseBed(sleeper.readPacketOfType(
                    Protocol18.S2C_USE_BED, 5_000));
            assertEquals(BED_B_Z + 1, (int) sleeperSeesWatcher[3],
                    "the sleeper observed the watcher's pose");
            assertTrue(watcher.readChatLineSkippingBroadcasts(5_000)
                    .contains("spawn point"), "the watcher's spawn line");

            long morning = awaitMorningTimeUpdate(watcher);
            assertTrue(morning < MobManager.NIGHT_START, "the morning reached the wire");

            // The wake renders on both wires: Animation 0x0B code 2 (the
            // leave-bed pose — sendPacketToAll includes each sleeper).
            long[] sleeperWake = parseAnimation(sleeper.readPacketOfType(
                    Protocol18.S2C_ANIMATION, 5_000));
            assertEquals(2L, sleeperWake[1], "the sleeper's leave-bed animation");
            long[] watcherWake = parseAnimation(watcher.readPacketOfType(
                    Protocol18.S2C_ANIMATION, 5_000));
            assertEquals(2L, watcherWake[1], "the watcher's leave-bed animation");

            // --- the leave-bed entity action over the wire ---
            nightFalls();
            // The walk's wake anchor opened the teleport window: the echo
            // acknowledges it (a real client's every-tick position packet).
            acknowledgeAnchor(sleeper, "Sleeper");
            acknowledgeAnchor(watcher, "Watcher");
            sleeper.sendBlockPlacement(BED_A_FOOT_X, 5, BED_A_Z, 1, -1);
            // Drain this round's pose + spawn line before the wake reads.
            parseUseBed(sleeper.readPacketOfType(Protocol18.S2C_USE_BED, 5_000));
            parseUseBed(watcher.readPacketOfType(Protocol18.S2C_USE_BED, 5_000));
            assertTrue(sleeper.readChatLineSkippingBroadcasts(5_000)
                    .contains("spawn point"), "the re-entry's spawn line");
            awaitCondition(() -> server.playerRegistry().byName("Sleeper")
                    .map(s -> s.sleeping()).orElse(false), "the sleeper re-entered");

            // Entity Action ordinal 2 (STOP_SLEEPING): both wires see the
            // leave-bed animation; the clock keeps the night.
            sleeper.sendEntityAction(2);
            long[] otherLeave = parseAnimation(watcher.readPacketOfType(
                    Protocol18.S2C_ANIMATION, 5_000));
            assertEquals(2L, otherLeave[1], "the watcher observed the leave-bed");
            long[] selfLeave = parseAnimation(sleeper.readPacketOfType(
                    Protocol18.S2C_ANIMATION, 5_000));
            assertEquals(2L, selfLeave[1], "the sleeper's own leave-bed");
            awaitCondition(() -> !server.playerRegistry().byName("Sleeper")
                    .map(s -> s.sleeping()).orElse(true), "the sleeper left the bed");
            long afterLeave = server.world().timeOfDay();
            assertTrue(afterLeave >= MobManager.NIGHT_START
                    && afterLeave < MobManager.NIGHT_END,
                    "the leave-bed never jumps the clock");

            // --- the monster-proximity refusal reaches the clicker ---
            // The leave-bed's own anchor needs its echo before the use.
            acknowledgeAnchor(sleeper, "Sleeper");
            var zombie = server.mobs().spawnAt(MobType.ZOMBIE,
                    new Position(BED_A_FOOT_X + 3.5, 5, BED_A_Z + 1.5));
            assertNotNull(zombie, "the zombie spawned inside the refusal box");
            sleeper.sendBlockPlacement(BED_A_FOOT_X, 5, BED_A_Z, 1, -1);
            assertTrue(sleeper.readChatLineSkippingBroadcasts(5_000)
                            .contains("You may not rest now, there are monsters nearby"),
                    "the vanilla refusal line reached the clicker");
            awaitCondition(() -> !server.playerRegistry().byName("Sleeper")
                    .map(s -> s.sleeping()).orElse(false), "the entry was refused");
            server.ticker().submit(zombie::despawn);
        }
        awaitEmptyServer();
    }
}
