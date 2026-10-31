package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
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
 * THE VERIFIER'S VT-006 FAMILY SEATS — the VEHICLE families (docs/
 * vanilla-conformance-audit.md, the twenty-fifth pass's queue): the
 * boat's and the minecart's portal crossings.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the boat body runs the
 * entity-level portal arm — {@code BoatEntity.tick} line 177 calls
 * {@code super.tick()} (Entity.tick → baseTick, the portal arm lines
 * 282-313). The minecart carries its OWN COPY of the whole arm
 * (MinecartEntity lines 199-232 — byte-identical law: the same
 * vehicle-null gate, the same base threshold 0, the same 300
 * cooldown, the same -1/0 direction). A vehicle whose box intersects
 * portal cells arms {@code inPortal} through the gated collision (an
 * EMPTY vehicle — a rider aboard holds the collision, VT-005's law);
 * the crossing fires on the FIRST armed tick;
 * {@code Entity.changeDimension} (1695-1730) walks the body across —
 * the 8:1 reposition, the forcer's placement, the body recreated in
 * the destination world.</p>
 *
 * <p>THE ENGINE'S SHAPE: {@code VehicleManager}'s four "portal"
 * mentions are the PortalCooldown NBT persistence alone (the census —
 * the twenty-fourth pass's independent verification); a vehicle
 * stands in portal cells forever. These seats are EXPECTED RED on
 * develop until the vehicle families' arms ride the
 * {@code DimensionCrossing} seam (PR #98's follow-up). Do not re-pin
 * them to the engine's current answer; the finding record owns the
 * mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PortalVehicleCrossingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "vehiclecross" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /** The shared construction: both frames stand, the body placed inside. */
    private VehicleEntity placeInsidePortal(
            java.util.function.BiFunction<Position, Integer, VehicleEntity> spawner,
            String description) throws InterruptedException {
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

        var spawned = new java.util.concurrent.atomic.AtomicReference<VehicleEntity>();
        server.ticker().submit(() ->
                spawned.set(spawner.apply(new Position(overX + 1.5, overY, overZ + 0.5), 0)));
        await(() -> spawned.get() != null, description);
        return spawned.get();
    }

    /** The body's cell rests inside the portal interior (the shared precondition). */
    private BlockPosition assertRestsInPortalCells(VehicleEntity body) throws InterruptedException {
        EngineWorld overworld = server.world();
        await(() -> {
            BlockPosition cell = new BlockPosition(
                    (int) Math.floor(body.position().x()),
                    (int) Math.floor(body.position().y()),
                    (int) Math.floor(body.position().z()));
            return BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                    || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell));
        }, "the vehicle rests inside a portal cell");
        BlockPosition cell = new BlockPosition(
                (int) Math.floor(body.position().x()),
                (int) Math.floor(body.position().y()),
                (int) Math.floor(body.position().z()));
        assertTrue(BuiltinBlocks.NETHER_PORTAL.equals(overworld.getBlock(cell))
                        || BuiltinBlocks.NETHER_PORTAL_Z.equals(overworld.getBlock(cell)),
                "the seat's precondition: the vehicle rests inside a portal cell ("
                        + cell + ", got " + overworld.getBlock(cell).identifier()
                        + ", position " + body.position() + ")");
        assertEquals(0, body.dimension(), "the body is in the overworld at the spawn");
        return cell;
    }

    /**
     * VT-006 (the boat family): an empty boat standing in the portal
     * cells crosses on the first armed tick — BoatEntity.tick's own
     * super.tick() chain.
     */
    @Test
    void theBoatStandingInThePortalCellsCrosses() throws Exception {
        VehicleEntity boat = placeInsidePortal(
                (at, dimension) -> server.vehicles().spawnBoat(at, dimension),
                "the boat spawned in the portal interior");
        assertRestsInPortalCells(boat);

        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> boat.dimension() == -1, 100, "the boat's portal crossing");
        assertTrue(crossed,
                "VT-006 (the boat family): the empty boat standing in the portal cells "
                        + "crosses on the first armed tick (the reference's entity-level "
                        + "portal arm over BoatEntity.tick's super chain — Entity lines "
                        + "354-355's base threshold 0)");

        int netherX = 5;
        int netherZ = 5;
        int netherY = 20;
        BlockPosition arrival = new BlockPosition(
                (int) Math.floor(boat.position().x()),
                (int) Math.floor(boat.position().y()),
                (int) Math.floor(boat.position().z()));
        assertTrue(arrival.x() >= netherX && arrival.x() <= netherX + 3
                        && arrival.y() >= netherY - 1 && arrival.y() <= netherY + 3
                        && arrival.z() >= netherZ - 1 && arrival.z() <= netherZ + 1,
                "VT-006 (the boat family): the arrival lands inside the pre-built "
                        + "nether frame's band (" + arrival + ", position "
                        + boat.position() + ")");
    }

    /**
     * VT-006 (the minecart family): an empty minecart standing in the
     * portal cells crosses on the first armed tick — MinecartEntity's
     * own copy of the whole arm (lines 199-232, the same law).
     */
    @Test
    void theMinecartStandingInThePortalCellsCrosses() throws Exception {
        VehicleEntity cart = placeInsidePortal(
                (at, dimension) -> server.vehicles().spawnMinecart(at, dimension),
                "the minecart spawned in the portal interior");
        assertRestsInPortalCells(cart);

        boolean crossed = EngineAwaits.awaitConditionWithin(server.world(),
                () -> cart.dimension() == -1, 100, "the minecart's portal crossing");
        assertTrue(crossed,
                "VT-006 (the minecart family): the empty minecart standing in the portal "
                        + "cells crosses on the first armed tick (MinecartEntity's own "
                        + "copy of the arm, lines 199-232 — the same base threshold 0)");
    }
}
