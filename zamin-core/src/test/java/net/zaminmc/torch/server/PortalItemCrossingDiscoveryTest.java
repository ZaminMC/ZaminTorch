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
 * <p>THE CONSTRUCTION (the twenty-eighth pass's root-cause re-pin): the
 * seat's original drop rode {@code spawnDropAtBlock} with a precise
 * position — but that verb treats its argument as a BLOCK CELL and
 * centers it (+0.5 on every axis) with the ±0.1 pop jitter, landing the
 * body on the 41.0 z boundary of the one-block-thick portal plane (the
 * recorded precondition flake: "the item rests inside a portal cell"
 * timed out once in two runs on the UNFIXED tree). The re-pinned
 * construction rides {@code spawnThrown} at the exact interior position
 * — zero initial velocity, no jitter: the body falls STRAIGHT onto the
 * bottom row, its centre cell inside the plane from the first tick.
 * Deterministic on both trees.</p>
 *
 * <p>THE PRECONDITION'S SHAPE: the one-shot read the mob seat
 * (PR #98's own) established — the crossing fires on the FIRST armed
 * tick, so an awaited "rests in a portal cell" precondition can never
 * survive the fixed tree (the body is already in the nether when the
 * poll lands). The one-shot read right after the spawn await carries
 * the in-cells state; the arrival asserts carry the rest.</p>
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

        // The body: a thrown stone INSIDE the frame's interior, falling
        // straight onto the bottom obsidian row (spawnThrown's exact
        // position + zero initial velocity — the deterministic
        // construction; no players online — nothing collects it; the
        // 6000-tick despawn window outlives the seat).
        var spawned = new java.util.concurrent.atomic.AtomicReference<ItemEntity>();
        server.ticker().submit(() -> {
            ItemEntity item = server.itemEntities().spawnThrown(
                    new Position(overX + 1.5, overY + 0.5, overZ + 0.5),
                    ItemStack.of(BuiltinItems.STONE, 1), 0);
            spawned.set(item);
        });
        await(() -> spawned.get() != null, "the item dropped in the portal interior");
        ItemEntity item = spawned.get();

        // The seat's precondition (the mob seat's one-shot shape): the
        // body's centre cell is a portal cell — the exact interior
        // position guarantees it on both trees (the pre-fix tree holds
        // the body there forever; the fixed tree crosses it on the first
        // armed tick, within the poll window).
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
        // FIRST armed tick (the base threshold 0). THE ENGINE'S PRE-FIX
        // SHAPE: no portal walk exists for items — the stack stands in
        // the cells until it despawns (the seat's original RED-by-
        // contract record, the twenty-fifth and twenty-seventh passes).
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
