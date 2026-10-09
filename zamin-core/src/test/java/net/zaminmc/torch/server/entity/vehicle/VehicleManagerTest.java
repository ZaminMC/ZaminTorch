package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vehicle bodies on deterministic stub worlds: the boat floats and
 * look-steers on water, grinds on land; the minecart rides its rail axis,
 * takes the L-corner, halts where the track ends; the seat bookkeeping and
 * the break drop honor the riding contract.
 */
class VehicleManagerTest {

    /** Water everywhere below y=5, ground below y=4 under it. */
    private static VehicleEntity.WorldQuery waterWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public boolean inFluid(double x, double y, double z) {
                return y >= 4.0 && y < 5.0;
            }
        };
    }

    /** Dry flat ground at y=4, no fluids. */
    private static VehicleEntity.WorldQuery dryWorld() {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
        };
    }

    /**
     * Flat ground at y=4 with an east-west rail along z=0 for x in
     * [xFrom, xTo): pure straight track (no corner leg).
     */
    private static VehicleEntity.WorldQuery straightRailWorld(int xFrom, int xTo) {
        return railWorld(xFrom, xTo, 0);
    }

    /**
     * Flat ground at y=4: east-west rails along z=0 for x in [xFrom, xTo-1),
     * then the corner cell at x=xTo-1 turning into a north-south leg that
     * runs +Z for zTo cells.
     */
    private static VehicleEntity.WorldQuery railWorld(int xFrom, int xTo, int zTo) {
        return new VehicleEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public int railAxisAt(double x, double y, double z) {
                if (y < 3.9 || y > 5.0) {
                    return -1;
                }
                int cellX = (int) Math.floor(x);
                int cellZ = (int) Math.floor(z);
                if (cellX == xTo - 1 && cellZ >= 0 && cellZ < zTo) {
                    return 0; // the corner cell and its north-south leg
                }
                if (cellZ == 0 && cellX >= xFrom && cellX < xTo - 1) {
                    return 1; // the east-west leg
                }
                return -1;
            }
        };
    }

    private record Events(List<Object> list) implements VehicleManager.Listener {
        @Override public void onVehicleSpawned(VehicleEntity vehicle) {
            list.add("spawn");
        }

        @Override public void onVehicleMoved(VehicleEntity vehicle) {
            list.add("moved");
        }

        @Override public void onVehicleHurt(VehicleEntity vehicle) {
            list.add("hurt");
        }

        @Override public void onVehicleMounted(VehicleEntity vehicle, int riderEngineId) {
            list.add("mount:" + riderEngineId);
        }

        @Override public void onVehicleDismounted(VehicleEntity vehicle, int riderEngineId,
                                                  Position riderExit) {
            list.add("dismount:" + riderEngineId);
        }

        @Override public void onVehicleBroken(VehicleEntity vehicle) {
            list.add("broken");
        }
    }

    @Test
    void theBoatFloatsOnWaterAndLookSteers() {
        BoatEntity boat = new BoatEntity(1, new Position(0.5, 4.5, 0.5), new Random(1));
        boat.followRiderLook(new Rotation(-90.0f, 0.0f)); // facing east
        var world = waterWorld();
        for (int i = 0; i < 40; i++) {
            boat.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false));
            boat.tick(world);
        }
        assertTrue(boat.position().x() > 1.5, "the boat steered east: " + boat.position());
        assertEquals(0.5, boat.position().z(), 0.01, "the run stays on the heading line");
        assertFalse(boat.onGround(), "a floating body is never grounded");
        double y = boat.position().y();
        assertTrue(y >= 3.9 && y < 5.0, "the boat rests in the water band: " + y);
    }

    @Test
    void theBoatOnLandSinksToTheGroundAndGrinds() {
        BoatEntity boat = new BoatEntity(1, new Position(0.5, 6.0, 0.5), new Random(1));
        var world = dryWorld();
        for (int i = 0; i < 120; i++) {
            boat.tick(world);
        }
        assertEquals(4.0 + BoatEntity.HALF_HEIGHT, boat.position().y(), 1e-6,
                "the boat rests on the ground");
        assertTrue(boat.onGround());
    }

    @Test
    void theMinecartRidesItsRailAxisAndCapsTheSpeed() {
        MinecartEntity cart = new MinecartEntity(2, new Position(0.5, 4.06, 0.5), new Random(1));
        cart.setRotation(new Rotation(-90.0f, 0.0f)); // the look points east
        var world = straightRailWorld(0, 6); // a straight east-west track
        for (int i = 0; i < 40; i++) {
            cart.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false));
            cart.tick(world);
        }
        assertTrue(cart.position().x() > 1.5, "the cart rolled east: " + cart.position());
        assertEquals(0.5, cart.position().z(), 0.01, "the ride centers on the rail line");
        assertEquals(4.0 + MinecartEntity.RIDE_Y_OFFSET, cart.position().y(), 0.01,
                "the cart rides just above the rail bed");
        double speed = Math.abs(cart.velocityX());
        assertTrue(speed <= MinecartEntity.RAIL_SPEED_CAP + 1e-9, "the cap holds: " + speed);
    }

    @Test
    void theMinecartTakesTheLCorner() {
        MinecartEntity cart = new MinecartEntity(2, new Position(0.5, 4.06, 0.5), new Random(1));
        cart.setRotation(new Rotation(-90.0f, 0.0f)); // the look points east
        var world = railWorld(0, 4, 4); // the east-west leg turns south at x=3
        for (int i = 0; i < 120; i++) {
            cart.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false));
            cart.tick(world);
        }
        assertTrue(cart.position().z() > 2.0, "the cart turned south: " + cart.position());
        assertEquals(3.5, cart.position().x(), 0.02, "the south leg centers on x=3.5");
    }

    @Test
    void theTrackEndStopsTheCartDead() {
        MinecartEntity cart = new MinecartEntity(2, new Position(0.5, 4.06, 0.5), new Random(1));
        cart.setRotation(new Rotation(-90.0f, 0.0f));
        var world = straightRailWorld(0, 3); // three rail cells, then nothing
        for (int i = 0; i < 200; i++) {
            cart.steer(new VehicleEntity.SteerInput(0.0f, 1.0f, false, false));
            cart.tick(world);
        }
        assertTrue(cart.position().x() < 3.0, "the cart never left the track: " + cart.position());
        assertEquals(0.0, cart.velocityX(), 1e-9, "the end-of-track halt zeroes the roll");
    }

    @Test
    void theSeatTheMountAndTheBreakDropFollowTheContract() {
        Events events = new Events(new ArrayList<>());
        List<ItemStack> drops = new ArrayList<>();
        VehicleManager manager = new VehicleManager(waterWorld(),
                vehicle -> drops.add(vehicle.kind() == VehicleEntity.Kind.BOAT
                        ? ItemStack.of(BuiltinItems.BOAT, 1)
                        : ItemStack.of(BuiltinItems.MINECART, 1)),
                new Random(1), 100);
        manager.addListener(events);

        VehicleEntity boat = manager.spawnBoat(new Position(0.5, 4.5, 0.5));
        assertTrue(manager.mount(boat, 7), "the open seat takes the rider");
        assertFalse(manager.mount(boat, 8), "the second rider is refused");
        assertEquals(7, boat.passengerId());

        manager.hurt(boat, 4.0f);
        assertTrue(boat.dead(), "four damage breaks the boat");
        manager.tick();
        assertEquals(0, manager.all().size(), "the broken boat left the world");
        assertEquals(1, drops.size(), "the break dropped the item form");
        assertTrue(events.list().contains("dismount:7"), "the seat opened on the break");
        assertTrue(events.list().contains("broken"));
    }

    @Test
    void theVoidConsumesTheVehicleLikeEveryBody() {
        List<ItemStack> drops = new ArrayList<>();
        VehicleManager manager = new VehicleManager(dryWorld(),
                vehicle -> drops.add(ItemStack.of(BuiltinItems.BOAT, 1)),
                new Random(1), 100);
        VehicleEntity boat = manager.spawnBoat(new Position(0.5, -65.0, 0.5));
        manager.tick();
        assertEquals(0, manager.all().size(), "the vehicle below the kill plane is gone");
        assertEquals(1, drops.size(), "the void drop fired (the boat item)");
        assertNull(manager.byId(boat.entityId()));
    }
}
