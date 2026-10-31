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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-008's pass-2/pass-3 verification seat (this stream's independently
 * authored construction — the honest-convergence protocol over PR #102,
 * the other stream's F-EX6 carrier): the wrong-world bed's blast arm
 * exercised at the pre-fix tree's DEEPEST wrong — the overworld clock
 * held INSIDE the night window, so the unguarded sleep walk would run
 * all the way (the body entering the bed, the spawn anchor set) rather
 * than refusing at the day gate. The reference (BedBlock.use lines
 * 32-81): the gate {@code world.dimension.hasSpawnPoint() &&
 * world.getBiome(pos) != Biome.HELL} consults the HEAD cell after the
 * foot redirect, and the else arm removes the head, then the sibling
 * foot, then {@code world.explode(null, head+0.5, 5.0F, true, true)} —
 * the ACTING world, power 5, fire always, destructive always, no source.
 *
 * <p>THE THREE NEW ANGLES over #102's own acceptance trio: (1) the HEAD
 * cell clicked directly (their core seat clicks the foot — together the
 * pair covers both normalization branches); (2) the overworld clock at
 * night, the sharpest pre-fix discrimination (the sleep walk RUNS on the
 * unguarded tree, the spawn anchor lands); (3) the END dimension's arm —
 * the gate's third answer, TheEndDimension.hasSpawnPoint() false — which
 * #102's seats never drive. The spawn pins ({@code bedSpawn() == null})
 * are this seat's own law: the blast arm must never set a bed spawn, and
 * the unguarded tree always does.</p>
 */
class BedWrongWorldNightWalkVerificationTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;
    private EngineWorld end;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bednight" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        end = server.worldFor(1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
        assertEquals(1, end.dimension(), "the End world carries its dimension row");
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

    /** The nether bed pair, NORTH-facing: the foot at (64,101,64), the head north of it. */
    private static final BlockPosition NETHER_FOOT = new BlockPosition(64, 101, 64);
    private static final BlockPosition NETHER_HEAD = new BlockPosition(64, 101, 63);

    /**
     * THE CORE SEAT (the night walk's deepest wrong): the overworld clock
     * held at NIGHT_START+100 — the unguarded tree's sleep walk runs all
     * the way here (the body enters the bed, the spawn anchor lands), and
     * the gated tree blasts instead: the pair leaves whole, the blast
     * carries power 5 and the NETHER's dimension at the HEAD cell's exact
     * center, FIRE lands in the crater, the overworld's glass marker at
     * the head's coordinates stands, the body never sleeps, and the bed
     * spawn is NEVER set. The click lands on the HEAD cell directly (the
     * normalization's head branch — #102's own seat drives the foot
     * redirect).
     */
    @Test
    void theNetherBedClickAtNightBlastsAndNeverSetsSpawn() throws Exception {
        layNetherPlatformAndBed();
        // The overworld clock INSIDE the night window: the unguarded tree
        // would pass the night gate and enter the sleep walk.
        server.ticker().submit(() -> server.world()
                .setTimeOfDay(net.zaminmc.torch.server.entity.MobManager.NIGHT_START + 100));
        EngineAwaits.awaitCondition(server.world(),
                () -> server.world().timeOfDay()
                        >= net.zaminmc.torch.server.entity.MobManager.NIGHT_START,
                40, "night falls over the overworld");

        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("NightCamper",
                new Position(71.5, 101.0, 64.5), -1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire, the HEAD cell clicked directly.
        server.useItemOnBlock(camper, NETHER_HEAD, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(NETHER_HEAD).equals(nether.airType())
                        && nether.getBlock(NETHER_FOOT).equals(nether.airType()),
                40, "the wrong-world bed's pair left whole (the nether night walk never ran)");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(-1, blast.get().dimension(),
                "the blast ranges the player's own dimension");
        assertEquals(NETHER_HEAD.x() + 0.5, blast.get().x(), 1.0e-9,
                "the blast's epicenter x (the head cell's center)");
        assertEquals(NETHER_HEAD.y() + 0.5, blast.get().y(), 1.0e-9,
                "the blast's epicenter y (the head cell's center)");
        assertEquals(NETHER_HEAD.z() + 0.5, blast.get().z(), 1.0e-9,
                "the blast's epicenter z (the head cell's center)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(NETHER_HEAD)),
                "the overworld's glass marker at the head's coordinates survived");
        assertFalse(camper.sleeping(),
                "the wrong-world bed never entered the sleep walk");
        assertNull(camper.bedSpawn(),
                "the wrong-world bed never set a bed spawn (the unguarded tree's own wrong)");
        assertTrue(fireLandedNear(nether, 64, 100, 63),
                "the always-true createFire row placed FIRE in the crater");
    }

    /** The End bed pair: the foot at (200,61,200), the head north of it. */
    private static final BlockPosition END_FOOT = new BlockPosition(200, 61, 200);
    private static final BlockPosition END_HEAD = new BlockPosition(200, 61, 199);

    /**
     * THE END SEAT (the gate's third answer — #102's own trio never drove
     * dimension 1): TheEndDimension.hasSpawnPoint() answers FALSE
     * (TheEndDimension line 54), so a bed clicked in the End takes the
     * same else arm — the pair leaves, the blast carries power 5 and the
     * END's dimension at the head's center, FIRE lands, the body never
     * sleeps, the spawn is never set. The click lands on the FOOT (the
     * normalization's redirect branch — this seat's pair with the core
     * seat's head click).
     */
    @Test
    void theEndBedClickBlastsInTheEndDimension() throws Exception {
        layEndPlatformAndBed();
        AtomicReference<EngineServer.ExplosionEvent> blast =
                new AtomicReference<>();
        server.addExplosionListener(blast::set);
        PlayerSession camper = join("EndCamper",
                new Position(207.5, 61.0, 200.5), 1);
        EngineAwaits.awaitCondition(server.world(),
                () -> camper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");

        // The REAL use wire, the FOOT cell clicked (the redirect branch).
        server.useItemOnBlock(camper, END_FOOT, 1, java.util.Optional.empty(),
                windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> end.getBlock(END_HEAD).equals(end.airType())
                        && end.getBlock(END_FOOT).equals(end.airType()),
                40, "the End bed's pair left whole");
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                40, "the End bed's blast fired");
        assertEquals(5.0f, blast.get().radius(),
                "the End bed blast's power (BedBlock.use line 78's 5.0F)");
        assertEquals(1, blast.get().dimension(),
                "the blast ranges the player's own dimension");
        assertEquals(END_HEAD.x() + 0.5, blast.get().x(), 1.0e-9,
                "the blast's epicenter x (the head cell's center)");
        assertEquals(END_HEAD.y() + 0.5, blast.get().y(), 1.0e-9,
                "the blast's epicenter y (the head cell's center)");
        assertEquals(END_HEAD.z() + 0.5, blast.get().z(), 1.0e-9,
                "the blast's epicenter z (the head cell's center)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(END_HEAD)),
                "the overworld's glass marker at the head's coordinates survived");
        assertFalse(camper.sleeping(),
                "the End bed never entered the sleep walk");
        assertNull(camper.bedSpawn(),
                "the End bed never set a bed spawn");
        assertTrue(fireLandedNear(end, 200, 60, 199),
                "the always-true createFire row placed FIRE in the End crater");
    }

    /**
     * THE CONTROL (the same clock state as the core seat — the
     * discrimination is the DIMENSION, not the clock): an overworld bed
     * clicked at NIGHT_START+100 enters the ordinary sleep walk — the pair
     * stands whole, the body sleeps, the spawn anchor lands, and no blast
     * ever fires. The gate must not touch the overworld arm.
     */
    @Test
    void theOverworldBedAtNightStillSleeps() throws Exception {
        // The flat world's night would naturally seed hostiles beside the
        // bed (the refusal box spans ±8/±5/±8 and the spawner cycles every
        // tick — MultiplayerSleepAcceptanceTest's own race note): the
        // natural cycle stays off so the control's discrimination is the
        // DIMENSION alone, never a monster roll.
        server.ticker().submit(() -> ((net.zaminmc.torch.Server) server).gameRules()
                .set("doMobSpawning", "false"));
        EngineAwaits.awaitCondition(server.world(),
                () -> !((net.zaminmc.torch.Server) server).gameRules()
                        .getBoolean("doMobSpawning"),
                40, "the natural spawn cycle is off");
        PlayerSession sleeper = join("DawnCamper", new Position(71.5, 101.0, 64.5), 0);
        EngineAwaits.awaitCondition(server.world(),
                () -> sleeper.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player reaches PLAYING");
        // The same platform law as the nether seat, in the overworld: an
        // NORTH-facing pair one above a dirt floor.
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    server.world().setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        server.world().setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                server.world().airType());
                    }
                }
            }
            server.world().setBlock(NETHER_FOOT, BuiltinBlocks.BED_FOOT_NORTH);
            server.world().setBlock(NETHER_HEAD, BuiltinBlocks.BED_HEAD_NORTH);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(server.world().getBlock(NETHER_HEAD)),
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

        server.useItemOnBlock(sleeper, NETHER_HEAD, 1, java.util.Optional.empty(),
                windowId -> { });

        // The durable observable of the sleep walk: the spawn anchor lands
        // at the entry and survives every wake path (the single-player
        // morning jump may wake the body within the same tick — a transient
        // the external poll could miss; the anchor never leaves).
        EngineAwaits.awaitCondition(server.world(),
                () -> sleeper.bedSpawn() != null,
                40, "the overworld bed entered the sleep walk (the anchor landed)");
        assertTrue(EngineServer.isBedHalf(server.world().getBlock(NETHER_HEAD))
                        && EngineServer.isBedHalf(server.world().getBlock(NETHER_FOOT)),
                "the overworld bed's pair stands whole");
        assertTrue(sleeper.bedSpawn() != null,
                "the ordinary sleep walk set the spawn anchor (the control's own law)");
        assertFalse(blast.get() != null,
                "no blast ever fired in the overworld");
    }

    /**
     * Lays the nether scene: a 15x15 dirt platform at y=100 with headroom
     * carved to y=107, the NORTH-facing bed pair one above it, and the
     * OVERWORLD's GLASS marker at the HEAD cell's same coordinates (the
     * discriminator — a misrouted blast would tear it).
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
            nether.setBlock(NETHER_FOOT, BuiltinBlocks.BED_FOOT_NORTH);
            nether.setBlock(NETHER_HEAD, BuiltinBlocks.BED_HEAD_NORTH);
            server.world().setBlock(NETHER_HEAD, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(nether.getBlock(NETHER_HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(NETHER_HEAD)),
                40, "the nether bed and the overworld marker landed");
    }

    /**
     * Lays the End scene: the same platform law far off the island's
     * shoulder (chunks generate on demand) — dirt at y=60, headroom carved
     * 55..67, the NORTH-facing pair one above it, the overworld's GLASS
     * marker at the head's coordinates.
     */
    private void layEndPlatformAndBed() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    for (int y = 55; y <= 67; y++) {
                        end.setBlock(new BlockPosition(200 + dx, y, 200 + dz),
                                end.airType());
                    }
                    end.setBlock(new BlockPosition(200 + dx, 60, 200 + dz),
                            BuiltinBlocks.DIRT);
                }
            }
            end.setBlock(END_FOOT, BuiltinBlocks.BED_FOOT_NORTH);
            end.setBlock(END_HEAD, BuiltinBlocks.BED_HEAD_NORTH);
            server.world().setBlock(END_HEAD, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> EngineServer.isBedHalf(end.getBlock(END_HEAD))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(END_HEAD)),
                40, "the End bed and the overworld marker landed");
    }

    /** Whether FIRE landed anywhere in the blast's crater region. */
    private boolean fireLandedNear(EngineWorld world, int cx, int floorY, int cz) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int y = floorY; y <= floorY + 6; y++) {
                for (int dz = -7; dz <= 7; dz++) {
                    if (BuiltinBlocks.FIRE.equals(world.getBlock(
                            new BlockPosition(cx + dx, y, cz + dz)))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
