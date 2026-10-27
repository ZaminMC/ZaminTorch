package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave TF-11 — the vehicle family's dimension reads (the reference's
 * Entity.world, the TF-9 shape's third generalization): the bodies carry
 * their own dimension (the field the ZVD store already persisted — the
 * spawn walks now stamp it), the shared world query flips per body before
 * every tick walk, and the ZVD round trip lands the body in its own world.
 */
class VehicleDimensionReadsTest {

    /**
     * The recording query: every read resolves through the ACTIVE dimension
     * the manager last flipped to (the surface below y=4 exists in the
     * OVERWORLD alone — the nether reads empty space).
     */
    private static final class DimensionQuery implements VehicleEntity.WorldQuery {
        int lastDimension = Integer.MIN_VALUE;
        int switches;

        @Override
        public void setActiveDimension(int dimension) {
            this.lastDimension = dimension;
            this.switches++;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return lastDimension == 0 && y < 4.0;
        }

        @Override
        public net.zaminmc.torch.server.physics.EntityCollisionWorld collisionWorld() {
            return null;
        }
    }

    @Test
    void theSpawnWalksStampTheDimension() {
        DimensionQuery query = new DimensionQuery();
        VehicleManager manager = new VehicleManager(query,
                vehicle -> { }, new Random(41), 1);
        VehicleEntity boat = manager.spawnBoat(new Position(0.5, 6.0, 0.5), -1);
        VehicleEntity cart = manager.spawnMinecart(new Position(2.5, 6.0, 2.5), 0);
        VehicleEntity chest = manager.spawnChestMinecart(new Position(4.5, 6.0, 4.5), 1);
        assertEquals(-1, boat.dimension(), "the stamped boat lives in the nether");
        assertEquals(0, cart.dimension(), "the unstamped cart keeps the overworld 0");
        assertEquals(1, chest.dimension(), "the stamped chest cart lives in the end");
    }

    @Test
    void theTickWalkFlipsTheQueryPerBody() {
        DimensionQuery query = new DimensionQuery();
        VehicleManager manager = new VehicleManager(query,
                vehicle -> { }, new Random(42), 1);
        VehicleEntity overworld = manager.spawnBoat(new Position(0.5, 6.0, 0.5), 0);
        VehicleEntity nether = manager.spawnBoat(new Position(2.5, 6.0, 2.5), -1);

        query.switches = 0;
        manager.tick();
        assertTrue(query.switches >= 2,
                "both bodies' tick walks flipped the shared query (got " + query.switches + ")");
        assertEquals(-1, query.lastDimension,
                "the walk ends on the last body's dimension (the list's own order)");
        assertFalse(overworld.dead(), "the overworld boat survives its own tick");
        assertFalse(nether.dead(), "the nether boat survives its own tick");
    }

    @Test
    void theRestoreLandsTheBodyInItsOwnWorld() {
        // The ZVD round trip: the BaseRows dimension row (the store's own
        // v4 row, never stamped on the body before this wave) now rides
        // the spawn — a saved nether cart rejoins the nether.
        DimensionQuery query = new DimensionQuery();
        VehicleManager manager = new VehicleManager(query,
                vehicle -> { }, new Random(43), 1);
        java.util.List<VehicleManager.VehicleSnapshot> saved = java.util.List.of(
                new VehicleManager.VehicleSnapshot("BOAT", 1.5, 8.0, 1.5, 90f,
                        4.0f, null, true, -1, null, -1, null, 0, true, null,
                        -VehicleEntity.FIRE_SAFE_TICKS,
                        new VehicleManager.BaseRows(0L, 0L, false, null, false, false,
                                0.0f, 300, false, false, -1, 0), null),
                new VehicleManager.VehicleSnapshot("MINECART", 2.5, 8.0, 2.5, 90f,
                        4.0f, null, true, -1, null, -1, null, 0, true, null,
                        -VehicleEntity.FIRE_SAFE_TICKS,
                        new VehicleManager.BaseRows(0L, 0L, false, null, false, false,
                                0.0f, 300, false, false, 0, 0), null));
        assertTrue(manager.restoreAll(saved));
        VehicleEntity boat = manager.all().stream()
                .filter(v -> v.kind() == VehicleEntity.Kind.BOAT).findFirst().orElseThrow();
        VehicleEntity cart = manager.all().stream()
                .filter(v -> v.kind() == VehicleEntity.Kind.MINECART).findFirst().orElseThrow();
        assertEquals(-1, boat.dimension(), "the saved nether boat restores into the nether");
        assertEquals(0, cart.dimension(), "the overworld cart restores into the overworld");
    }
}
