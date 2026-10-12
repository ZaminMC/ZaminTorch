package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-dimension Time Update sync (the 8c slice — the reference's
 * MinecraftServer lines 547-558 walk): every dimension's clock publishes to
 * the players standing in that dimension only, the /time set and /time add
 * arms land on EVERY world server (the reference's TimeCommand lines 86-95),
 * and the sleep skip stays the overworld's own arm (canSkipNight — the
 * nether's clock keeps its own value while the overworld jumps to morning).
 *
 * <p>The assertions are lockstep-relative: both worlds advance on the same
 * tick and the listener snapshots them inside one publish, so two clocks the
 * command moved together publish EQUAL day-of-times, and a move that lands
 * on one world only breaks the equality.
 */
class DimensionTimeSyncTest {

    @TempDir
    Path dataDir;

    @Test
    void everyDimensionPublishesItsOwnClock() throws Exception {
        EngineServer server = boot();
        try {
            // The captured wire view: dimension -> the last (total, tod) the
            // listener carried for it (one publish's same-tick snapshots).
            Map<Integer, long[]> heard = new ConcurrentHashMap<>();
            server.addTimeListener((dimension, totalTicks, timeOfDay) ->
                    heard.put(dimension, new long[]{totalTicks, timeOfDay}));

            // The /time set arm (the reference's TimeCommand.setTimeOfDay):
            // the clock lands on EVERY world server — both dimensions'
            // publishes carry the SAME moved day clock (they then advance in
            // lockstep; the >= band absorbs the live clock's drift).
            server.chatService().submitChat(join(server, "Walker"), "/time set night");
            await(() -> lockstepAtOrAfter(heard, 13_000, 2_000),
                    "the /time set night landed on both worlds' publishes");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSleepJumpStaysTheOverworldsArm() throws Exception {
        EngineServer server = boot();
        try {
            Map<Integer, long[]> heard = new ConcurrentHashMap<>();
            server.addTimeListener((dimension, totalTicks, timeOfDay) ->
                    heard.put(dimension, new long[]{totalTicks, timeOfDay}));

            // Both clocks at night, publishing in lockstep.
            server.chatService().submitChat(join(server, "Sleeper"), "/time set night");
            await(() -> lockstepAtOrAfter(heard, 13_000, 2_000),
                    "the /time set night landed on both worlds' publishes");

            // The sleep jump's arm (the reference's canSkipNight: overworld
            // only): the overworld jumps to morning while the nether's clock
            // keeps its own value — the per-dimension publish then carries
            // DIFFERENT day clocks to the two dimensions' players.
            server.ticker().submit(() -> server.world().setTimeOfDay(0));
            await(() -> heard.containsKey(-1) && heard.get(-1)[1] >= 13_000
                            && heard.containsKey(0) && heard.get(0)[1] < 2_000,
                    "the overworld published the morning jump while the nether kept its own clock");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void timeAddWalksBothWorldsClocksAndWrapsForward() throws Exception {
        EngineServer server = boot();
        try {
            Map<Integer, long[]> heard = new ConcurrentHashMap<>();
            server.addTimeListener((dimension, totalTicks, timeOfDay) ->
                    heard.put(dimension, new long[]{totalTicks, timeOfDay}));

            PlayerSession walker = join(server, "Adder");
            // Anchor both clocks at day, then /time add 500 (the reference's
            // addToTimeOfDay arm): both worlds' clocks shift together.
            server.chatService().submitChat(walker, "/time set day");
            await(() -> lockstepAtOrAfter(heard, 1_000, 400), "the anchor set landed on both clocks");
            server.chatService().submitChat(walker, "/time add 500");
            await(() -> lockstepAtOrAfter(heard, 1_500, 400), "the /time add landed on both clocks");

            // The negative sum wraps forward through the day band (floorMod,
            // never a negative clock): 1000 - 2000 lands at 23000.
            server.chatService().submitChat(walker, "/time set day");
            await(() -> lockstepAtOrAfter(heard, 1_000, 400), "the wrap anchor landed on both clocks");
            server.chatService().submitChat(walker, "/time add -2000");
            await(() -> lockstepAtOrAfter(heard, 23_000, 600),
                    "the negative add wrapped both clocks forward to the band's end");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** Both dimensions published the same day-of-time, at or after the anchor. */
    private static boolean lockstepAtOrAfter(Map<Integer, long[]> heard, long anchor, long band) {
        long[] over = heard.get(0);
        long[] nether = heard.get(-1);
        return over != null && nether != null
                && over[1] >= anchor && over[1] - anchor < band
                && nether[1] == over[1];
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "dimsync", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
