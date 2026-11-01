package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
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
 * THE VERIFIER'S FAMILY-WIDE SEAT — the thrown family's crossing (the
 * twenty-eighth pass's census: the portal arm rides EVERY projectile
 * family's tick chain, not the arrows alone): a snowball thrown through
 * the portal face crosses — the {@code ThrownEntity} super-chain arm.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): {@code ThrownEntity.tick}
 * line 123 calls {@code super.tick()} (Entity.tick → baseTick, the
 * portal arm lines 282-313) — the snowball, the egg, the potion, and
 * the experience bottle all ride the same arm (the egg's
 * {@code EggEntity.tick} and the potion's {@code PotionEntity.tick}
 * delegate to it); a thrown body whose box intersects portal cells arms
 * {@code inPortal} through the gated collision, the BASE threshold 0
 * (lines 354-355) crosses on the FIRST armed tick, and
 * {@code Entity.changeDimension} walks the body across with its whole
 * state. The engine's {@code ProjectileManager} loop walks every
 * {@code Kind} body through the same per-body arm — the family-wide
 * claim this seat verifies for a NON-ARROW kind.</p>
 *
 * <p>THE INDEPENDENT CONSTRUCTION: the FAR-side approach — from BELOW
 * the plane (z = plane-1.4), yaw 0, the +Z flight — at the interior's
 * SECOND column (w=2), the mirror of the face-shot seats' near-side
 * form.</p>
 *
 * <p>THE ENGINE'S SHAPE: no portal walk exists for the projectile
 * family on develop — the snowball sails through the plane and dies on
 * the overworld terrain beyond it. EXPECTED RED on develop; the seat
 * verifies the #113 arm's family-wide coverage (the whole {@code Kind}
 * enum walks the same arm).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalThrownCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "throwncross" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /** The 4x5 X-plane frame (the DimensionWalk test's own build shape). */
    private static void buildPortal(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frameCell = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frameCell ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    /**
     * VT-006 (the family-wide arm): a snowball thrown through the portal
     * face crosses — the ThrownEntity super-chain arm — and its arrival
     * lands inside the destination frame's band before its flight ends
     * on the catchers.
     */
    @Test
    void theSnowballThrownThroughTheFaceCrosses() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

        // The PRE-BUILT nether frame AT THE SCALED DESTINATION (40/8 = 5)
        // so the forcer walk lands the body deterministically inside its
        // cells.
        int netherX = 5;
        int netherZ = 5;
        int netherY = 20;
        for (int cx = (netherX - 4) >> 4; cx <= (netherX + 4) >> 4; cx++) {
            for (int cz = (netherZ - 4) >> 4; cz <= (netherZ + 4) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(netherX >> 4, netherZ >> 4)),
                200, "the nether frame chunk loaded");
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 6; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    for (int dy = -2; dy <= 5; dy++) {
                        nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + dz),
                                nether.airType());
                    }
                }
            }
            buildPortal(nether, netherX, netherY, netherZ);
            // The catchers (this seat's own construction): stone walls
            // two past either open face — the snowball's rotated arrival
            // flight ends on one of them within a few ticks.
            for (int dx = -3; dx <= 6; dx++) {
                for (int dy = -2; dy <= 5; dy++) {
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ - 2),
                            BuiltinBlocks.STONE);
                    nether.setBlock(new BlockPosition(netherX + dx, netherY + dy, netherZ + 2),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: a snowball thrown from BELOW the plane (the far-side
        // approach — the mirror of the face-shot seats), at the
        // interior's SECOND column (w=2), flat and straight: the second
        // tick's walk lands the position cell inside the portal plane.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(overX + 2.5, overY + 1.5, overZ - 1.4),
                0.0, 0.0, 1.0, 0)));
        await(() -> launched.get() != null, "the snowball thrown at the far face");
        ProjectileEntity snowball = launched.get();

        // THE CROSSING: the reference's law — the ThrownEntity super
        // chain's arm crosses the snowball on the first armed tick after
        // its flight enters the cells. THE ENGINE'S SHAPE: no portal
        // walk exists for the projectile family — the snowball sails
        // through the plane and dies on the overworld terrain beyond.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> snowball.dimension() == -1, 100, "the snowball's portal crossing");
        assertTrue(crossed,
                "VT-006 (the family-wide arm): the snowball thrown through the portal "
                        + "face crosses (the reference's entity-level portal arm over "
                        + "ThrownEntity.tick line 123's super chain — the same arm every "
                        + "projectile family rides, not the arrows alone)");

        // The arrival: sampled within the first ticks after the crossing
        // — the forcer's placement inside the destination frame's band,
        // the body still in the world with the nether's stamp (the
        // snowball's flight continues until the catchers end it).
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(snowball.position().x()),
                (int) Math.floor(snowball.position().y()),
                (int) Math.floor(snowball.position().z()));
        assertTrue(arrival.x() >= netherX - 1 && arrival.x() <= netherX + 4
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 2 && arrival.z() <= netherZ + 2,
                "VT-006 (the family-wide arm): the snowball's arrival lands inside the "
                        + "pre-built nether frame's band (" + arrival + ", position "
                        + snowball.position() + ")");
        assertTrue(server.projectiles().all().stream().anyMatch(
                        p -> p.entityId() == snowball.entityId() && p.dimension() == -1),
                "VT-006 (the family-wide arm): the crossed snowball carries the nether's "
                        + "stamp while its flight runs");
    }
}
