package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.event.world.ExplosionEvent;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX8 — the wither birth blast's destructive flag (WitherEntity.mobAiTick
 * line 217): the reference detonates the 7.0F birth blast with
 * {@code this.world.explode(this, ..., 7.0F, false, mobGriefing)} — the
 * SEVENTH argument (the block-breaking flag) reads the mobGriefing game
 * rule, exactly the creeper's own form (CreeperEntity.explode line 210
 * consults the rule for its five-arg overload's flag). The pre-fix engine
 * passed {@code false} WHOLE: a griefing world's wither birth wounded and
 * knocked but never tore a single block.
 *
 * <p>THE SEATS drive the REAL summon wire (a wither spawned onto a stone
 * platform, {@code witherSummoned()}'s 220-tick blue window running on the
 * engine's own clock, the birth consumed by the manager's tick and
 * dispatched through {@code onWitherBirth} into the engine's detonation
 * walk): under the DEFAULT rules (mobGriefing true) the birth blast tears
 * the platform under the wither; with the rule OFF the platform stands
 * while the blast itself still fires (the event, the wounds, the packet —
 * the reference's no-grief shape).</p>
 */
class WitherBirthGriefingParityAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "wbgrief" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
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
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** The platform cell the birth blast's strongest rays reach: directly
     *  under the wither's feet. */
    private static final BlockPosition GROUND_ZERO = new BlockPosition(64, 80, 64);

    /** Lays a floating stone platform at y=80 centered on the wither's
     *  cell (stone carries no gravity — the platform stands on nothing and
     *  never pops, whatever the blast does around it). */
    private void layPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    server.world().setBlock(new BlockPosition(64 + dx, 80, 64 + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(GROUND_ZERO)),
                40, "the platform landed");
    }

    /**
     * THE CORE SEAT: the default rules (mobGriefing true) — the birth
     * blast tears the platform under the wither. The pre-fix engine's
     * hardcoded {@code false} left the whole platform standing while the
     * event, the wounds, and the packet all fired.
     */
    @Test
    void theBirthBlastTearsThePlatformUnderMobGriefing() throws Exception {
        layPlatform();
        AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
        var sub = ((Server) server).events().on(ExplosionEvent.class, blast::set);

        MobEntity wither = server.mobs().spawnAt(MobType.WITHER,
                new Position(64.5, 81.0, 64.5));
        assertNotNull(wither, "the wither spawned onto the platform");
        server.ticker().submit(wither::witherSummoned);

        // The 220-tick blue window, then the birth: the blast fires (the
        // event) AND the platform's ground-zero cell opens.
        EngineAwaits.awaitCondition(server.world(),
                () -> blast.get() != null
                        && server.world().getBlock(GROUND_ZERO)
                                .equals(server.world().airType()),
                300, "the birth blast tore the platform under the wither");
        assertEquals(7.0f, blast.get().power(),
                "the birth blast's power (WitherEntity line 217's 7.0F)");
        sub.unsubscribe();
    }

    /**
     * THE GUARD: the rule OFF — the birth blast still fires (the event,
     * the wounds, the packet) but the platform stands whole, exactly the
     * reference's {@code explode(..., false, mobGriefing)} no-grief shape.
     */
    @Test
    void theBirthBlastNeverTearsWithoutGriefing() throws Exception {
        layPlatform();
        AtomicReference<ExplosionEvent> blast = new AtomicReference<>();
        var sub = ((Server) server).events().on(ExplosionEvent.class, blast::set);

        server.ticker().submit(() ->
                server.gameRules().set("mobGriefing", "false"));
        EngineAwaits.awaitTicks(server.world(), 2, "the rule write landed");

        MobEntity wither = server.mobs().spawnAt(MobType.WITHER,
                new Position(64.5, 81.0, 64.5));
        assertNotNull(wither, "the wither spawned onto the platform");
        server.ticker().submit(wither::witherSummoned);

        EngineAwaits.awaitCondition(server.world(),
                () -> blast.get() != null, 300, "the birth blast still fired");
        // A few ticks past the birth: the platform never tore.
        EngineAwaits.awaitTicks(server.world(), 5, "the blast settled");
        assertTrue(BuiltinBlocks.STONE.equals(server.world().getBlock(GROUND_ZERO)),
                "the no-grief birth blast never tore the platform");
        sub.unsubscribe();
    }
}
