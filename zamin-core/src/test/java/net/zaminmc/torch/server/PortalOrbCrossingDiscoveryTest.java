package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.experience.ExperienceOrbEntity;
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
 * THE VERIFIER'S VT-006 FAMILY SEAT — the EXPERIENCE ORB family
 * (docs/vanilla-conformance-audit.md, the twenty-fifth pass's queue):
 * the experience orb's portal crossing.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the orb body runs the
 * entity-level portal arm — {@code ExperienceOrbEntity.tick} lines
 * 61-62 call {@code super.tick()} (Entity.tick → baseTick, the portal
 * arm lines 282-313). An orb whose box intersects portal cells arms
 * {@code inPortal} through the gated collision (an orb never rides);
 * the BASE threshold 0 (lines 354-355) crosses on the FIRST armed
 * tick; the cooldown arms at the base 300 (lines 1457-1459);
 * {@code Entity.changeDimension} (1695-1730) walks the orb across —
 * the 8:1 reposition, the forcer's placement, the body recreated in
 * the destination world with its experience whole.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code ExperienceOrbManager}'s tick walk
 * carries zero portal consults (the census — the twenty-fourth pass's
 * independent verification); an orb stands in portal cells forever.
 * This seat is EXPECTED RED on develop until the orb family's arm
 * rides the {@code DimensionCrossing} seam (PR #98's follow-up). Do
 * not re-pin it to the engine's current answer; the finding record
 * owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalOrbCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "orbcross" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * VT-006 (the orb family): an experience orb standing in the portal
     * cells crosses on the first armed tick — the reference's
     * entity-level portal arm over ExperienceOrbEntity's own super.tick()
     * chain — and lands inside the destination frame's interior.
     */
    @Test
    void theExperienceOrbStandingInThePortalCellsCrosses() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        int overX = 40;
        int overZ = 40;
        int overY = 20;
        server.ticker().submit(() -> buildPortal(overworld, overX, overY, overZ));
        await(() -> overworld.getBlock(new BlockPosition(overX + 1, overY, overZ))
                .equals(BuiltinBlocks.NETHER_PORTAL), "the overworld portal frame built");

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

        // The body: a single orb burst inside the frame's interior (the
        // burst's spread pins to one body — maxOrbs 1; no players online
        // — nothing collects it; the orb's own gravity grounds it).
        var spawned = new java.util.concurrent.atomic.AtomicReference<ExperienceOrbEntity>();
        server.ticker().submit(() -> {
            var burst = server.experienceOrbs().spawnBurst(
                    new Position(overX + 1.5, overY, overZ + 0.5), 3, 1, 0);
            if (!burst.isEmpty()) {
                spawned.set(burst.get(0));
            }
        });
        await(() -> spawned.get() != null, "the orb burst in the portal interior");
        ExperienceOrbEntity orb = spawned.get();

        await(() -> {
            BlockPosition cell = new BlockPosition(
                    (int) Math.floor(orb.position().x()),
                    (int) Math.floor(orb.position().y()),
                    (int) Math.floor(orb.position().z()));
            return BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                    || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell));
        }, "the orb rests inside a portal cell");
        BlockPosition cell = new BlockPosition(
                (int) Math.floor(orb.position().x()),
                (int) Math.floor(orb.position().y()),
                (int) Math.floor(orb.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell)),
                "the seat's precondition: the orb rests inside a portal cell ("
                        + cell + ", got " + overworld.getBlock(cell).identifier()
                        + ", position " + orb.position() + ")");
        assertEquals(0, orb.dimension(), "the body is in the overworld at the burst");

        // THE CROSSING: the reference's law — the orb crosses on the
        // FIRST armed tick. THE ENGINE'S SHAPE: no portal walk exists
        // for orbs — the body stands in the cells until it despawns.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> orb.dimension() == -1, 100, "the orb's portal crossing");
        assertTrue(crossed,
                "VT-006 (the orb family): the experience orb standing in the portal "
                        + "cells crosses on the first armed tick (the reference's "
                        + "entity-level portal arm over ExperienceOrbEntity.tick's "
                        + "super chain — Entity lines 354-355's base threshold 0)");

        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(orb.position().x()),
                (int) Math.floor(orb.position().y()),
                (int) Math.floor(orb.position().z()));
        assertTrue(arrival.x() >= netherX && arrival.x() <= netherX + 3
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 1 && arrival.z() <= netherZ + 1,
                "VT-006 (the orb family): the arrival lands inside the pre-built "
                        + "nether frame's band (" + arrival + ", position "
                        + orb.position() + ")");
    }
}
