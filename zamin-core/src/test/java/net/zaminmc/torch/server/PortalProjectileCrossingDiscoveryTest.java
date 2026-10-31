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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-006 FAMILY SEAT — the PROJECTILE family (docs/
 * vanilla-conformance-audit.md, the twenty-eighth pass's queue head): the
 * projectile's portal crossing, the crossing families' last slice.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): every projectile kind's tick
 * chain carries the entity-level portal arm — {@code ArrowEntity.tick}
 * line 150, {@code ProjectileEntity.tick} line 82, {@code ThrownEntity.tick}
 * line 123, and {@code EnderPearlEntity.tick} line 82 all call
 * {@code super.tick()} into {@code Entity.baseTick}'s portal arm (lines
 * 282-313), and the arm runs BEFORE each kind's own {@code inGround}
 * branch (the arrow's ground clock, the shard's stick window). A
 * projectile whose box intersects portal cells arms {@code inPortal}
 * through the gated collision ({@code PortalBlock.onEntityCollision}
 * lines 158-162 — a projectile neither rides nor is ridden, so the gate
 * passes); the BASE threshold 0 (lines 354-355; no projectile kind
 * overrides {@code getMaxNetherPortalTime}) crosses on the FIRST armed
 * tick; the cooldown arms at the base 300 (lines 1457-1459);
 * {@code Entity.changeDimension} (1695-1730) walks the body across —
 * the 8:1 reposition, the forcer's placement, the body recreated in the
 * destination world whole.</p>
 *
 * <p>THE ENGINE'S SHAPE (pre-fix): {@code ProjectileManager}'s tick walk
 * carried zero portal consults (the census — the twenty-fourth pass's
 * independent verification; the twenty-fifth pass's authored-seat
 * deferral named the flight-path determinism care this seat's
 * {@code spawnRestored} at-rest construction resolves). A projectile
 * resting or stuck in portal cells never crossed. This seat is EXPECTED
 * RED on develop until the projectile family's arm rides the
 * {@code DimensionCrossing} seam. Do not re-pin it to the engine's
 * current answer; the finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalProjectileCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "projcross" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * VT-006 (the projectile family): a projectile at rest in the portal
     * cells crosses on the first armed tick — the reference's entity-level
     * portal arm over every kind's own super.tick() chain — and lands
     * inside the destination frame's interior through the same forcer walk
     * the player, the mobs, the items, the orbs, and the vehicles ride.
     * The at-rest construction ({@code spawnRestored}'s saved-row shape:
     * no gaussian tilt, no launch force) is the deferred flight-path
     * determinism answer: the crossing fires on the body's first tick
     * before any physics can move it.
     */
    @Test
    void theProjectileStandingInThePortalCellsCrosses() throws Exception {
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
        });
        await(() -> nether.getBlock(new BlockPosition(netherX + 1, netherY, netherZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the pre-built nether frame stands");

        // The body: an arrow at rest inside the frame's interior — the
        // saved-row launch (no gaussian tilt, no force), so the body's
        // first tick consults the portal cell before any physics moves it
        // (the flight-path determinism care the twenty-fifth pass's
        // deferral named). The spawn-time feet cell is captured ON THE
        // TICK LANE inside the same submit (before the body's first tick
        // can fall it through the non-solid portal cells) — the
        // synchronous precondition: the body's first armed consult names
        // a portal cell.
        var spawned = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        var spawnCell = new java.util.concurrent.atomic.AtomicReference<BlockPosition>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(overX + 1.5, overY, overZ + 0.5),
                    0.0, 0.0, 0.0);
            spawned.set(arrow);
            spawnCell.set(new BlockPosition(overX + 1, overY, overZ));
        });
        await(() -> spawned.get() != null, "the arrow spawned in the portal interior");
        ProjectileEntity arrow = spawned.get();
        BlockPosition cell = spawnCell.get();
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell)),
                "the seat's precondition: the arrow's spawn-time feet cell is a portal "
                        + "cell (" + cell + ", got " + overworld.getBlock(cell).identifier()
                        + ")");
        assertEquals(0, arrow.dimension(), "the body is in the overworld at the spawn");

        // THE CROSSING: the reference's law — the projectile crosses on
        // the FIRST armed tick (the base threshold 0). THE ENGINE'S SHAPE
        // (pre-fix): no portal walk exists for projectiles — the body
        // falls through the non-solid portal cells, sticks in the frame's
        // obsidian floor, and stands in the overworld until it expires.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> arrow.dimension() == -1, 100, "the projectile's portal crossing");
        assertTrue(crossed,
                "VT-006 (the projectile family): the projectile standing in the portal "
                        + "cells crosses on the first armed tick (the reference's "
                        + "entity-level portal arm over every kind's super.tick() "
                        + "chain — Entity lines 354-355's base threshold 0)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's interior band.
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(arrow.position().x()),
                (int) Math.floor(arrow.position().y()),
                (int) Math.floor(arrow.position().z()));
        assertTrue(arrival.x() >= netherX && arrival.x() <= netherX + 3
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 1 && arrival.z() <= netherZ + 1,
                "VT-006 (the projectile family): the arrival lands inside the pre-built "
                        + "nether frame's band (" + arrival + ", position "
                        + arrow.position() + ")");
        assertTrue(server.projectiles().all().stream().anyMatch(
                        p -> p.entityId() == arrow.entityId() && p.dimension() == -1),
                "VT-006 (the projectile family): the crossed body carries the nether's stamp");

        // The no-bounce pin (the family's own shape): the arrival lands
        // inside the destination frame's cells, where the cooldown's
        // re-arm law (onPortalCollision lines 1429-1430) holds the body —
        // the dimension stays -1 over the window, whatever the arrow's
        // own fall-and-stick does afterward (a stuck arrow in the frame's
        // obsidian floor is not touching; the crossing holds either way,
        // but a broken cooldown that never armed would bounce the body
        // back within ticks — the window catches it).
        for (int sample = 0; sample < 20; sample++) {
            EngineAwaits.awaitTicks(server.world(), 2, "the no-bounce window advances");
            assertEquals(-1, arrow.dimension(),
                    "VT-006 (the projectile family): the arrival never bounces back "
                            + "(the cooldown's re-arm law) — sample " + sample);
        }
    }
}
