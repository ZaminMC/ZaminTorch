package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-007 FAMILY SEAT — the blast family's dimension
 * routing (docs/vanilla-conformance-audit.md, the twenty-sixth pass):
 * a detonation in the nether carves the NETHER, never the overworld.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): every detonation site calls
 * {@code this.world.explode(...)} — the entity's OWN world instance:
 * {@code FireballEntity.onHit} line 33, {@code WitherSkullEntity.onHit}
 * line 83, {@code CreeperEntity.explode} line 210, {@code WitherEntity.
 * mobAiTick} line 217, {@code TntMinecartEntity.explode} line 92.
 * {@code World.explode} (lines 1601-1607) constructs {@code new
 * Explosion(this, ...)} over that same world — every ray-fan read, every
 * commit, every drop, and every wound runs through the dimension the
 * detonating body lives in. A ghast fireball in the nether craters
 * netherrack; the overworld at the same coordinates never learns of it.</p>
 *
 * <p>THE ENGINE'S SHAPE (develop, the twenty-sixth pass's census): the
 * ghast fireball and wither skull sinks pass the ROUTED world but the
 * OVERWORLD-built {@code explosionService} (EngineServer 2543/2553); the
 * creeper, wither-birth, and TNT-cart sites route through the
 * overworld-bound wrappers outright ({@code detonateAtFromMob}/
 * {@code detonateAtWithSource}, 24328/24292); and the shared drop roll
 * stamps dimension 0 ({@code detonateAtIn} 24514's three-arg form). A
 * nether detonation therefore carves the OVERWORLD at the same
 * coordinates while the nether stands whole — cross-dimension world
 * corruption.</p>
 *
 * <p>These seats are EXPECTED RED on develop until the family's arms
 * consult {@code explosionServiceFor(dimension)} (the FL4-F7 idiom the
 * primed TNT already rides). Do not re-pin them to the engine's current
 * answer; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExplosionDimensionRoutingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "blastdim" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** The blast-probe coordinates: a clear column, away from the spawn pad. */
    private static final int PROBE_X = 60;
    private static final int PROBE_Y = 24;
    private static final int PROBE_Z = 60;

    /**
     * Builds the DIRT construction BOTH dimensions carry at the SAME
     * coordinates: a 9&times;9 platform at PROBE_Y with a 3&times;3&times;3
     * box standing on its center (the fireball's target). DIRT's blast
     * resistance (0.5) pays {@code (0.5 + 0.3) * 0.3 = 0.24} per crossing —
     * a power-1 fireball's rays (strength 0.7-1.3) carve it; the engine's
     * flat overworld and the nether both generate empty air around the
     * probe, so any holed cell is the blast's own work.
     */
    private static void buildProbe(EngineWorld world) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.setBlock(new BlockPosition(PROBE_X + dx, PROBE_Y, PROBE_Z + dz),
                        BuiltinBlocks.DIRT);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    world.setBlock(new BlockPosition(PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz),
                            BuiltinBlocks.DIRT);
                }
            }
        }
    }

    /**
     * Clears the nether's GENERATED terrain along the fireball's flight
     * line (the launch pad 4 blocks out on X through the box's near
     * face): the nether's own caves and walls would otherwise swallow
     * the shot before the box — the fireball's collision consults the
     * nether world (its own dimension), so only the NETHER side needs
     * the corridor; the overworld's flat sky is already clear at the
     * probe band.
     */
    private static void carveNetherFlightCorridor(EngineWorld nether) {
        for (int dx = -5; dx <= -2; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    nether.setBlock(new BlockPosition(
                            PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz),
                            BuiltinBlocks.AIR);
                }
            }
        }
    }

    /**
     * The fireball's target: a DIRT wall spanning the flight corridor's
     * WHOLE cross-section (x PROBE_X-1..PROBE_X+1, the corridor's own
     * y/z extents), built in BOTH dimensions at the same coordinates.
     * The launch's gaussian spread (&sigma; 0.4 per axis) then cannot
     * miss: whatever the draw, the fireball's flight through the carved
     * corridor meets the wall's face. The wall's own dirt (resistance
     * 0.5, {@code 0.24} per crossing) pays to a power-1 fan; the hit
     * cell itself is always collected (the inward rays cross it
     * immediately with strength &ge; 0.7 - 0.24 &gt; 0).
     */
    private static void buildWall(EngineWorld world) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.setBlock(new BlockPosition(
                            PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz),
                            BuiltinBlocks.DIRT);
                }
            }
        }
    }

    /** Counts the carved (air) cells inside the fireball wall's band. */
    private int holedWallCells(EngineWorld world) {
        int holes = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -2; dy <= 4; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (world.getBlock(new BlockPosition(
                            PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz))
                            .equals(BuiltinBlocks.AIR)) {
                        holes++;
                    }
                }
            }
        }
        return holes;
    }

    /** Counts the carved (air) cells inside the probe's box band (y+1..y+3). */
    private int holedBoxCells(EngineWorld world) {
        int holes = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (world.getBlock(new BlockPosition(
                            PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz))
                            .equals(BuiltinBlocks.AIR)) {
                        holes++;
                    }
                }
            }
        }
        return holes;
    }

    private void loadNetherProbeChunks(EngineWorld nether) throws InterruptedException {
        for (int cx = (PROBE_X - 8) >> 4; cx <= (PROBE_X + 8) >> 4; cx++) {
            for (int cz = (PROBE_Z - 8) >> 4; cz <= (PROBE_Z + 8) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(PROBE_X >> 4, PROBE_Z >> 4)),
                200, "the nether probe chunk loaded");
    }

    /**
     * VT-007 (the fireball family): a ghast fireball detonating against
     * the nether wall carves the NETHER wall — the reference's own-world
     * law — and the overworld wall at the same coordinates stands whole.
     * RED on develop BY CONTRACT: the engine's sink reads and commits
     * the overworld service, so the overworld wall takes the hole and the
     * nether wall stands.
     */
    @Test
    void theNetherGhastFireballBlastCarvesTheNetherBoxNeverTheOverworld() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);
        loadNetherProbeChunks(nether);

        server.ticker().submit(() -> {
            buildWall(overworld);
            buildWall(nether);
            carveNetherFlightCorridor(nether);
        });
        await(() -> BuiltinBlocks.DIRT.equals(overworld.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y + 2, PROBE_Z)))
                        && BuiltinBlocks.DIRT.equals(nether.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y + 2, PROBE_Z))),
                "both dimensions' walls stand");

        // The ghast's shot: 3 blocks out on X, aimed dead-on at the wall's
        // mid-height band, dimension -1 — the launch seam the ghast AI
        // itself rides (onMobGhastFireball's launchGhastFireball). The
        // aim baseline is 10 (the direction is normalized inside the
        // launch, so the magnitude is a pure spread damper): the launch's
        // gaussian jitter (sigma 0.4 per axis) shrinks to a ~2-degree
        // tilt, keeping the flight horizontal and the strike on the wall
        // deterministic. The nether's own generated terrain is cleared
        // along the flight line (the corridor above), and the wall spans
        // the corridor's whole cross-section.
        Position origin = new Position(PROBE_X - 3.5, PROBE_Y + 2, PROBE_Z + 0.5);
        server.ticker().submit(() -> server.projectiles()
                .launchGhastFireball(0, origin, 10.0, 0.0, 0.0, -1));

        // The launch lands first (a POSITIVE await — the empty list is
        // also the pre-launch state, so an isEmpty await alone would race
        // past the strike), then the hit removes the fireball; the blast
        // walk runs synchronously inside the same tick (the sink is the
        // collision walk's own call).
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.projectiles().all().isEmpty(),
                100, "the fireball launched");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.projectiles().all().isEmpty(),
                300, "the fireball flew and struck the wall");

        // THE VANILLA ANSWER: the nether wall holed, the overworld wall whole.
        int netherHoles = holedWallCells(nether);
        int overworldHoles = holedWallCells(overworld);
        assertTrue(netherHoles > 0,
                "VT-007 (the fireball family): the nether wall takes the blast"
                        + " hole (the reference carves the fireball's OWN world);"
                        + " observed nether=" + netherHoles + " overworld="
                        + overworldHoles + " holed cells");
        assertTrue(overworldHoles == 0,
                "VT-007 (the fireball family): the overworld wall at the same"
                        + " coordinates stands whole (the overworld never learns"
                        + " of a nether blast); observed " + overworldHoles
                        + " holed cells — the cross-dimension corruption");
    }

    /**
     * VT-007 (the creeper family): a creeper standing on the nether
     * platform detonates its power-3 blast into the NETHER — the
     * reference's {@code this.world.explode(this, ...)} — and the
     * overworld platform at the same coordinates stands whole. RED on
     * develop BY CONTRACT: the engine's {@code detonateAtFromMob} wrapper
     * hardcodes the overworld binding (world, explosionService, 0), so
     * the whole blast — fan, commits, wounds, drops — lands in the
     * overworld wherever the creeper stands.
     */
    @Test
    void theNetherCreeperBlastCarvesTheNetherPlatformNeverTheOverworld() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);
        loadNetherProbeChunks(nether);

        server.ticker().submit(() -> {
            buildProbe(overworld);
            buildProbe(nether);
            // The creeper's standing pocket in the nether (the review
            // correction carried with the PR #105 merge): the body flips
            // to dimension -1 at the box's top (y PROBE_Y+4), and the
            // NETHER's own generated terrain at that band is a per-boot
            // lottery (the random world seed) — a rock cell inside the
            // body's box embeds the flipped creeper (the suffocation /
            // unstuck-ejection hazard class, one no-blast observed in 17
            // runs on the unmodified seat, the ejected body detonating
            // away from the measured box). The pocket clears the three
            // layers above the box in the nether ALONE — the overworld's
            // flat sky is already air there, and the measured box cells
            // (dy 1..3) are never touched.
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 4; dy <= 6; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        nether.setBlock(new BlockPosition(
                                PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz),
                                BuiltinBlocks.AIR);
                    }
                }
            }
        });
        await(() -> BuiltinBlocks.DIRT.equals(overworld.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y + 2, PROBE_Z)))
                        && BuiltinBlocks.DIRT.equals(nether.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y + 2, PROBE_Z)))
                        && BuiltinBlocks.AIR.equals(nether.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y + 5, PROBE_Z))),
                "both dimensions' probes stand and the pocket is clear");

        // The nether creeper: spawned at the box's center (the probe
        // stands in BOTH dimensions at the same coordinates, so the body
        // rests identically wherever its physics consult), dimension -1
        // stamped, the 5-tick fuse forcing the detonation without a
        // player — the gather test's own construction.
        var creeper = server.mobs().spawnAt(
                net.zaminmc.torch.server.entity.MobType.CREEPER,
                new Position(PROBE_X + 0.5, PROBE_Y + 4, PROBE_Z + 0.5));
        assertTrue(creeper != null, "the creeper spawned");
        server.ticker().submit(() -> {
            creeper.setDimension(-1);
            creeper.setCreeperIgnited(true);
            creeper.setCreeperFuseTime(5);
        });

        // The fuse's end detonates (the mob leaves the manager's list
        // through the explode arm's own removal — no dead flag, so the
        // WORLD is the probe): the blast hole appears in exactly one of
        // the two dimensions' boxes.
        EngineAwaits.awaitCondition(server.world(),
                () -> holedBoxCells(nether) > 0 || holedBoxCells(overworld) > 0,
                300, "the creeper's fuse burned down and the blast landed");

        // THE VANILLA ANSWER: the nether platform holed, the overworld
        // platform whole. A creeper power 3 against dirt (0.24/cell)
        // carves the standing surface around the feet.
        int netherHoles = holedBoxCells(nether);
        int overworldHoles = holedBoxCells(overworld);
        assertTrue(netherHoles > 0,
                "VT-007 (the creeper family): the nether platform takes the"
                        + " blast hole (the reference detonates in the creeper's"
                        + " OWN world); observed nether=" + netherHoles
                        + " overworld=" + overworldHoles + " holed cells");
        assertTrue(overworldHoles == 0,
                "VT-007 (the creeper family): the overworld platform at the"
                        + " same coordinates stands whole; observed "
                        + overworldHoles + " holed cells — the cross-dimension"
                        + " corruption");
    }
}
