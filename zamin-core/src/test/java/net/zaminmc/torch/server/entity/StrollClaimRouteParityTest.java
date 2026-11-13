package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave SR-2's law (WanderAroundGoal.start() lines 43-45, 1:1): the stroll's
 * route begins AT THE CLAIM — {@code navigation.moveTo(target, speed)} runs
 * in start() itself, never at the next tick's shared repath consult. The
 * engine carried the moveTo behind a bisect marker since commit 017d87b0
 * (whose message claimed it landed), so a claimed stroll stood a
 * repathCooldown's leftover window — up to ~30 ticks — before its route
 * began.
 *
 * <p>THE OBSERVER (the discrimination's own rig): the public scheduler's
 * per-tick task samples the body at the TICK HEAD — before the handler's
 * mind walk — so the first sample that sees the fresh WANDER claim reads
 * the state the CLAIM'S OWN TICK left behind: the route must already be
 * live there. On the pre-fix tree the claim tick wrote the mode alone (the
 * route began at the NEXT handler pass), so the same sample read a
 * route-less wanderer.</p>
 */
class StrollClaimRouteParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The platform's surface level (the body stands at y = 40). */
    private static final int FLOOR_Y = 39;
    private static final int CENTER_X = 20;
    private static final int CENTER_Z = 20;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "strollclaim" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                200, name + " playing");
        return session;
    }

    /**
     * The open platform: a 45x45 stone floor at y = 38/39 with air above
     * through y = 48 — every stroll pick runs unwalled.
     */
    private void buildPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int x = 2; x <= 44; x++) {
                for (int z = 2; z <= 44; z++) {
                    server.world().setBlock(new BlockPosition(x, 38, z),
                            BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, 39, z),
                            BuiltinBlocks.STONE);
                    for (int y = 40; y <= 48; y++) {
                        server.world().setBlock(new BlockPosition(x, y, z),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(
                                new BlockPosition(CENTER_X, FLOOR_Y, CENTER_Z))),
                200, "the platform stands");
    }

    /**
     * THE CLAIM'S OWN TICK: the per-tick observer samples at the tick head
     * (before the handler's mind walk), so the first WANDER-claiming sample
     * reads the claim tick's own residue — start()'s route must be live
     * there (the pre-fix claim wrote the mode alone and stood the
     * repathCooldown's leftover window before its route began).
     */
    @Test
    void theStrollClaimCarriesItsOwnRouteTheClaimTick() throws Exception {
        buildPlatform();
        PlayerSession near = join("Wanderer");
        MobEntity cow = server.mobs().spawnAt(MobType.COW,
                new Position(CENTER_X + 0.5, 40.0, CENTER_Z + 0.5), 0);
        // The player NEAR (the stroll's own gate: a body 100+ ticks without
        // a player within 32 blocks never strolls).
        server.teleportPlayer(near,
                new Position(CENTER_X + 3.5, 40.0, CENTER_Z + 0.5));
        EngineAwaits.awaitCondition(server.world(),
                () -> near.position().x() == CENTER_X + 3.5, 100,
                "the player stands near the cow");

        // The observer: samples at every tick head until the first WANDER
        // claim lands, recording the heading's truth at that very sample.
        AtomicBoolean sawClaim = new AtomicBoolean(false);
        AtomicBoolean routeAtClaim = new AtomicBoolean(false);
        AtomicInteger idleSamples = new AtomicInteger(0);
        net.zaminmc.torch.scheduler.Task observer = server.ticker()
                .scheduleTask(1, 1, () -> {
                    if (sawClaim.get()) {
                        return; // the verdict stands
                    }
                    if (cow.mode() == MobEntity.Mode.WANDER) {
                        sawClaim.set(true);
                        routeAtClaim.set(cow.navigationRouteLive());
                    } else if (cow.mode() == MobEntity.Mode.IDLE) {
                        idleSamples.incrementAndGet();
                    }
                });
        try {
            EngineAwaits.awaitCondition(server.world(), sawClaim::get, 800,
                    "the stroll claims (the idle ticks first: "
                            + idleSamples.get() + ")");
        } finally {
            observer.cancel();
        }
        assertTrue(routeAtClaim.get(),
                "the claim's own tick carries start()'s route (the "
                        + "navigation lives at the first WANDER sample — "
                        + "the pre-fix claim stood the repathCooldown's "
                        + "leftover window)");
    }
}
