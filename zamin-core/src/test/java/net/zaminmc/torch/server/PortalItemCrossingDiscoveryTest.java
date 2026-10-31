package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
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
 * THE VERIFIER'S VT-006 FAMILY SEAT — the ITEM family (docs/
 * vanilla-conformance-audit.md, the twenty-fifth pass's queue): the
 * dropped item's portal crossing.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the item body runs the
 * entity-level portal arm — {@code ItemEntity.tick} line 61 calls
 * {@code super.tick()} (Entity.tick → baseTick, the portal arm lines
 * 282-313). A dropped item whose box intersects portal cells arms
 * {@code inPortal} through the same gated collision
 * ({@code PortalBlock.onEntityCollision} lines 158-162 — an item never
 * rides, so the gate passes); the BASE threshold 0 (lines 354-355)
 * crosses on the FIRST armed tick; the cooldown arms at the base 300
 * (lines 1457-1459); {@code Entity.changeDimension} (1695-1730) walks
 * the stack across — the 8:1 reposition, the forcer's placement, the
 * body recreated in the destination world with its stack whole.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code ItemEntityManager}'s tick walk carries
 * zero portal consults (the census — the twenty-fourth pass's
 * independent verification); a dropped item stands in portal cells
 * forever. This seat is EXPECTED RED on develop until the item family's
 * arm rides the {@code DimensionCrossing} seam (PR #98's follow-up).
 * Do not re-pin it to the engine's current answer; the finding record
 * owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalItemCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "itemcross" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * VT-006 (the item family): a dropped item standing in the portal
     * cells crosses on the first armed tick — the reference's
     * entity-level portal arm over ItemEntity's own super.tick() chain —
     * and lands inside the destination frame's interior through the same
     * forcer walk the player and the mobs ride.
     */
    @Test
    void theDroppedItemStandingInThePortalCellsCrosses() throws Exception {
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

        // The body: a dropped stone inside the frame's interior, resting
        // on the bottom obsidian row (the item's own gravity grounds it;
        // no players online — nothing collects it; the 6000-tick despawn
        // window outlives the seat).
        //
        // THE SEAT'S DETERMINISM (the flake root-caused): spawnDropAtBlock
        // stamps the vanilla drop scatter — a random horizontal velocity
        // up to ±0.1/tick — and the frame's floor strip is ONE cell thick
        // in z (the bottom row spans the frame's plane alone), so a strong
        // z-drift carries the 0.25-box past the strip and the item falls
        // to the world surface, never resting in a portal cell (the
        // precondition poll then times out — the pass-27 lane's observed
        // "once in two runs" flake, exactly this race). The scatter is
        // CORRECT vanilla behavior (the reference's drop pop); the SEAT
        // neutralizes it for determinism — the law under test is the
        // crossing, not the drop scatter.
        var spawned = new java.util.concurrent.atomic.AtomicReference<ItemEntity>();
        server.ticker().submit(() -> {
            // THE CORNER FORM (the root-cause completion): spawnDropAtBlock
            // treats its input as the block CORNER - it centers (+0.5) and
            // then applies the vanilla position scatter (+-0.1). The center
            // form (overX + 1.5, overY, overZ + 0.5) landed at z = 41.0 +- 0.1
            // - OUTSIDE the frame's one-cell strip half the runs (the 50%
            // precondition flake the velocity zeroing alone cannot retire;
            // the position scatter is the second half of the root cause).
            // The corner form lands at z = 40.5 +- 0.1 - ALWAYS inside the
            // strip: the scatter stays within the cell.
            ItemEntity item = server.itemEntities().spawnDropAtBlock(
                    new Position(overX + 1, overY, overZ),
                    ItemStack.of(BuiltinItems.STONE, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS, 0);
            if (item != null) {
                item.setVelocity(0.0, 0.0, 0.0); // the seat's determinism row
            }
            spawned.set(item);
        });
        await(() -> spawned.get() != null, "the item dropped in the portal interior");
        ItemEntity item = spawned.get();

        // The seat's precondition: the body's cell is a portal cell (the
        // item's position IS its center; the resting box's center cell
        // sits inside the interior).
        await(() -> {
            BlockPosition cell = new BlockPosition(
                    (int) Math.floor(item.position().x()),
                    (int) Math.floor(item.position().y()),
                    (int) Math.floor(item.position().z()));
            return BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                    || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell));
        }, "the item rests inside a portal cell");
        BlockPosition cell = new BlockPosition(
                (int) Math.floor(item.position().x()),
                (int) Math.floor(item.position().y()),
                (int) Math.floor(item.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell)),
                "the seat's precondition: the item rests inside a portal cell ("
                        + cell + ", got " + overworld.getBlock(cell).identifier()
                        + ", position " + item.position() + ")");
        assertEquals(0, item.dimension(), "the body is in the overworld at the drop");

        // THE CROSSING: the reference's law — the item crosses on the
        // FIRST armed tick (the base threshold 0). THE ENGINE'S SHAPE:
        // no portal walk exists for items — the stack stands in the
        // cells until it despawns.
        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> item.dimension() == -1, 100, "the item's portal crossing");
        assertTrue(crossed,
                "VT-006 (the item family): the dropped item standing in the portal "
                        + "cells crosses on the first armed tick (the reference's "
                        + "entity-level portal arm over ItemEntity.tick's super "
                        + "chain — Entity lines 354-355's base threshold 0)");

        // The arrival: the forcer's placement lands the body inside the
        // pre-built nether frame's interior band, the stack whole.
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(item.position().x()),
                (int) Math.floor(item.position().y()),
                (int) Math.floor(item.position().z()));
        assertTrue(arrival.x() >= netherX && arrival.x() <= netherX + 3
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 1 && arrival.z() <= netherZ + 1,
                "VT-006 (the item family): the arrival lands inside the pre-built "
                        + "nether frame's band (" + arrival + ", position "
                        + item.position() + ")");
        assertEquals(1, item.stack().count(), "the stack carries whole across");
        assertTrue(server.itemEntities().all().stream().anyMatch(
                        e -> e.entityId() == item.entityId() && e.dimension() == -1),
                "VT-006 (the item family): the crossed body carries the nether's stamp");
    }
}
