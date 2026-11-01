package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX6 — the wrong-world bed (BedBlock.use lines 48 + 68-79): the
 * reference's sleep gate is {@code world.dimension.hasSpawnPoint() &&
 * world.getBiome(pos) != Biome.HELL} — the NETHER and the END answer
 * {@code hasSpawnPoint()} FALSE (NetherDimension line 55,
 * TheEndDimension line 54), so the else arm runs: the pair leaves whole
 * (the head, then the sibling foot) and {@code world.explode(null,
 * pos.getX() + 0.5, ..., 5.0F, true, true)} tears the ACTING world — power
 * 5, createFire ALWAYS true (the only unconditional fire site in the
 * reference's call census), destructive true (never the mobGriefing
 * consult), source null.
 *
 * <p>THE PRE-FIX ENGINE routed every bed use straight to the sleep walk:
 * a bed clicked in the nether read the OVERWORLD's clock and tried to
 * SLEEP — no dimension gate, no pair removal, no power-5 fire blast. The
 * fire arm itself is F-EX4's row (this branch's substrate); the bed's
 * blast arm is this one.</p>
 *
 * <p>THE SEATS drive the REAL use wire ({@code useItemOnBlock} → the bed
 * dispatch arm): the nether bed click EXPLODES (the halves gone, the
 * platform torn, FIRE placed, the overworld's same-coordinate marker
 * standing) while the OVERWORLD bed still sleeps at night — the guard.</p>
 */
class BedWrongWorldBlastAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bedblast" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        // The natural spawn cycle stays OFF (run 571's overworld seat): the
        // night the seats themselves set is the hostile cycle's own hunting
        // ground — a zombie landing inside the sleep gate's ±8/±5 refusal
        // box beside the bed makes the engine's own 1:1 monster-proximity
        // law refuse the use, and the sleep-walk await starves. Neither seat
        // needs a natural population (the nether blast is the bed's own, the
        // overworld control sleeps alone) — the protocol module's sleep rigs
        // carry the same rule off.
        server.ticker().submit(() -> server.gameRules().set("doMobSpawning", "false"));
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.gameRules().getBoolean("doMobSpawning"),
                40, "the natural spawn cycle is off");
        // THE BOOT-SEATED SWEEP (run 583's member — the same class the
        // rule-off above retired going FORWARD): the rule lands only after
        // start() returns, so whatever hostiles the boot window's own
        // cycle already seated (the boot-time dawn light's randomized
        // rule) are still standing — and a boot-seated zombie inside the
        // sleep gate's ±8/±5 refusal box beside the bed makes the same
        // 1:1 monster-proximity law refuse the use (the sleep-walk await
        // starves, exactly run 571's member). The mob-integration seat's
        // own guard sweeps them; this one now does too.
        server.ticker().submit(() -> {
            for (var mob : server.mobs().all()
                    .toArray(new net.zaminmc.torch.server.entity.MobEntity[0])) {
                if (mob.type().hostile) {
                    server.mobs().remove(mob, "the boot-seated sweep");
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> server.mobs().all().stream().noneMatch(m -> m.type().hostile),
                40, "the boot-seated hostiles swept");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** The nether bed pair: the foot at (64, 101, 64), the head south. */
    private static final BlockPosition FOOT = new BlockPosition(64, 101, 64);
    private static final BlockPosition HEAD = new BlockPosition(64, 101, 65);

    /**
     * THE CORE SEAT: the nether bed click explodes — the pair leaves, the
     * platform tears, FIRE lands in the crater (the createFire row), the
     * blast's own event carries power 5 and the NETHER's dimension, and
     * the OVERWORLD's glass marker at the head's same coordinates stands.
     * The pre-fix click ran the sleep walk against the overworld's clock
     * and never removed a single half.
     */
    @Test
    void theNetherBedClickExplodesInsteadOfSleeping() throws Exception {
        layNetherPlatformAndBed();
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession sleeper = join("NetherCamper",
                new Position(71.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> sleeper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire: the right-click on the FOOT half (the
        // reference's own foot redirect exercises the head normalization).
        server.useItemOnBlock(sleeper, FOOT, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(HEAD).equals(nether.airType())
                        && nether.getBlock(FOOT).equals(nether.airType()),
                40, "the wrong-world bed's pair left whole");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(-1, blast.get().dimension(),
                "the blast ranges the player's own dimension");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD)),
                "the overworld's glass marker at the head's coordinates survived");
        assertFalse(sleeper.sleeping(),
                "the wrong-world bed never entered the sleep walk");
        assertTrue(fireLandedNearTheBlast(),
                "the always-true createFire row placed FIRE in the crater");
    }

    /**
     * THE GUARD: the overworld bed still sleeps — the pair stands, the
     * player enters the bed, no blast fires. The dimension gate must not
     * touch the overworld arm.
     */
    @Test
    void theOverworldBedStillSleepsAtNight() throws Exception {
        PlayerSession sleeper = join("Camper", new Position(0.5, 5, 0.5), 0);
        EngineAwaits.awaitCondition(server.world(),
                () -> sleeper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");
        BlockPosition foot = new BlockPosition(0, 5, 0);
        BlockPosition head = new BlockPosition(0, 5, 1);
        server.ticker().submit(() -> {
            server.world().setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            server.world().setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(server.world().getBlock(head)),
                40, "the overworld bed seeded");
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(net.zaminmc.torch.server.entity.MobManager.NIGHT_START + 100));
        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().timeOfDay()
                        >= net.zaminmc.torch.server.entity.MobManager.NIGHT_START,
                40, "night falls");

        server.useItemOnBlock(sleeper, foot, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(), sleeper::sleeping,
                40, "the overworld bed entered the sleep walk");
        assertTrue(EngineServer.isBedHalf(server.world().getBlock(head))
                        && EngineServer.isBedHalf(server.world().getBlock(foot)),
                "the overworld bed's pair stands whole");
        assertFalse(blast.get() != null,
                "no blast ever fired in the overworld");
    }

    /**
     * THE ORPHAN-FOOT GUARD (use lines 40-43): a foot whose head is gone
     * consumes the use with NEITHER arm — no blast, no sleep — even in the
     * wrong world. Passes on both trees; pins the null-head edge against
     * future regressions.
     */
    @Test
    void theOrphanFootConsumesTheUseWithNoArm() throws Exception {
        layNetherPlatformAndBed();
        // The head leaves: the foot is an orphan.
        server.ticker().submit(() -> nether.setBlock(HEAD, nether.airType()));
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(HEAD).equals(nether.airType()),
                40, "the head left");
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("OrphanClicker",
                new Position(71.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        server.useItemOnBlock(camper, FOOT, 1, java.util.Optional.empty(),
                windowId -> { });
        EngineAwaits.awaitTicks(server.world(), 10, "the click settled");

        assertTrue(EngineServer.isBedHalf(nether.getBlock(FOOT)),
                "the orphan foot still stands — no arm ran");
        assertFalse(blast.get() != null, "no blast fired");
        assertFalse(camper.sleeping(), "no sleep walk ran");
    }

    /**
     * Lays the nether scene: an 11x11 dirt platform at y=100, the bed pair
     * one above it, and the OVERWORLD's GLASS marker at the HEAD cell's
     * same coordinates (the discriminator — a misrouted blast would tear
     * it).
     */
    private void layNetherPlatformAndBed() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(FOOT, BuiltinBlocks.BED_FOOT_SOUTH);
            nether.setBlock(HEAD, BuiltinBlocks.BED_HEAD_SOUTH);
            server.world().setBlock(HEAD, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(nether.getBlock(HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(HEAD)),
                40, "the nether bed and the overworld marker landed");
    }

    /** Whether FIRE landed anywhere in the blast's crater region. */
    private boolean fireLandedNearTheBlast() {
        for (int dx = -7; dx <= 7; dx++) {
            for (int y = 100; y <= 106; y++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (BuiltinBlocks.FIRE.equals(nether.getBlock(
                            new BlockPosition(64 + dx, y, 64 + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
