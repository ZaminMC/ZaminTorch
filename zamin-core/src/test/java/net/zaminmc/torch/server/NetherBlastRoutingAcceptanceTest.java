package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.EngineServer.ExplosionEvent;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-EX11 + F-EX12 — the blast walk's remaining dimension routing (Wave
 * FL4-F7's form completed over the call sites it never reached): the
 * reference detonates EVERY mob- and projectile-sourced blast through the
 * ACTING world — {@code this.world.explode(this, ...)} (CreeperEntity.explode
 * line 210, WitherEntity line 217, FireballEntity.onHit line 33,
 * WitherSkullEntity line 83) — so the ray fan, the destruction commit, the
 * wounds and the packet all range the dimension the source body lives in.
 *
 * <p>THE PRE-FIX ENGINE left two seams on the overworld binding after
 * FL4-F7 routed the TNT's: (1) {@code detonateAtFromMob} — the creeper's
 * AND the wither birth's helper — hardcodes the overworld world, the
 * overworld explosion service and dimension 0, so a NETHER creeper's blast
 * flew its rays through the OVERWORLD's same-coordinate cells (tearing
 * them, wounding the overworld's bodies, and never touching the nether);
 * (2) the projectile explosion sinks route the blast WORLD per dimension
 * but pass the OVERWORLD's {@code explosionService} — the service whose
 * ray fan reads and commits its OWN bound world — so a nether ghast
 * fireball's blast wounded nether bodies yet tore the OVERWORLD's
 * same-coordinate cells, exactly FL4-F7's pre-fix TNT signature ("the
 * blast wounded but never tore").</p>
 *
 * <p>THE SEATS drive both seams over the REAL wires with the FL4-F7
 * discriminator: a nether platform, an OVERWORLD glass marker at the SAME
 * coordinates, and a blast — the nether platform tears, the overworld
 * marker SURVIVES.</p>
 */
class NetherBlastRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "nblast" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** The discriminator cell: the platform's center, where both worlds
     *  hold a block at the SAME coordinates — the nether DIRT platform and
     *  the OVERWORLD's GLASS marker. */
    private static final BlockPosition CENTER = new BlockPosition(64, 100, 64);

    /**
     * THE CREEPER SEAT (F-EX11 — the mob-sourced helper): a nether creeper
     * detonating on its nether dirt platform. The pre-fix blast tore the
     * OVERWORLD's glass marker at the same coordinates and never opened
     * the nether's platform cell.
     */
    @Test
    void theNetherCreeperBlastTearsTheNetherNotTheOverworld() throws Exception {
        layPlatform();
        java.util.concurrent.atomic.AtomicReference<ExplosionEvent> blast =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.addExplosionListener(blast::set);
        MobEntity creeper = server.mobs().spawnAt(MobType.CREEPER,
                new Position(64.5, 101.0, 64.5));
        assertNotNull(creeper, "the creeper spawned on the nether platform");
        server.ticker().submit(() -> {
            creeper.setDimension(-1); // the body lives in the nether
            creeper.setCreeperIgnited(true); // the swell runs targetless
            creeper.setCreeperFuseTime(5);
        });

        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(CENTER).equals(nether.airType()),
                60, "the nether creeper's blast tore the nether's platform cell");
        // The event fires at the walk's END (the cells torn, the destroy
        // event, then the listener fan) — a test-thread poll can observe
        // the torn cell inside that same-walk window, so the AWAITED
        // observable for the event rows is the listener's own observation,
        // never the cell alone (run 571's NPE at the dimension read).
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                60, "the creeper blast's event fired");
        assertEquals(-1, blast.get().dimension(),
                "the blast's own dimension row (the pre-fix event carried the overworld's 0)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(CENTER)),
                "the overworld's glass marker at the same coordinates survived the blast");
    }

    /**
     * THE GHAST FIREBALL SEAT (F-EX12 — the projectile explosion sink): a
     * ghast shot fired straight down onto the nether dirt platform. The
     * pre-fix sink's overworld service read the OVERWORLD's cells — the
     * marker tore, the nether never did. Three shots ride the seat: the
     * launch's own 0.4-sigma gaussian tilt (the reference's "the ghast
     * misses at range" spread) can walk a single impact off the
     * platform's edge, and the discrimination wants ANY platform cell
     * torn — which the pre-fix engine can never produce (the overworld
     * service's world never writes a nether cell).
     */
    @Test
    void theNetherGhastFireballBlastTearsTheNetherNotTheOverworld() throws Exception {
        layPlatform();
        java.util.concurrent.atomic.AtomicReference<ExplosionEvent> blast =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.addExplosionListener(blast::set);
        // The shots from five cells above the platform, straight down
        // (the fireball's own acceleration walk carries them in).
        server.ticker().submit(() -> {
            for (int shot = 0; shot < 3; shot++) {
                server.projectiles().launchGhastFireball(
                        -1, new Position(64.5, 105.5, 64.5), 0.0, -1.0, 0.0, -1);
            }
        });

        EngineAwaits.awaitCondition(server.world(), this::anyPlatformCellTorn,
                120, "the nether fireball's blast tore the nether's platform");
        // The listener fan runs at the blast walk's end — the torn cell
        // alone can be observed before the event reference is set (run
        // 571's NPE class; the creeper seat's own note above).
        EngineAwaits.awaitCondition(server.world(), () -> blast.get() != null,
                120, "the fireball blast's event fired");
        assertEquals(-1, blast.get().dimension(),
                "the fireball blast's own dimension row (the pre-fix event carried the overworld's 0)");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(CENTER)),
                "the overworld's glass marker at the same coordinates survived the blast");
    }

    /** Whether any cell of the 9x9 nether platform opened to air. */
    private boolean anyPlatformCellTorn() {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                if (nether.getBlock(new BlockPosition(64 + dx, 100, 64 + dz))
                        .equals(nether.airType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Lays the discriminator pair: a 9x9 DIRT platform at y=100 in the
     * NETHER (dirt's 0.5 resistance is soft enough for a power-1 fireball
     * ray to tear) and the OVERWORLD's GLASS marker at the center cell.
     * Stone is deliberately avoided — a power-1 ray cannot pay stone's 6.0
     * resistance charge, and the fireball seat would be vacuous.
     */
    private void layPlatform() throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            server.world().setBlock(CENTER, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.DIRT.equals(nether.getBlock(CENTER))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(CENTER)),
                40, "the nether platform and the overworld marker landed");
    }
}
