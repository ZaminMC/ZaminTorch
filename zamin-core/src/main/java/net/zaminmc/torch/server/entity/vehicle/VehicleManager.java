package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The vehicle simulation: the boats and minecarts tick on the world owner
 * thread, publish their lifecycle to the listeners (spawn, move, mount,
 * dismount, break) and die into their item drops. Engine-global ids; the
 * manager owns the seat bookkeeping the riding contract rides on.
 */
public final class VehicleManager {

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        /** A vehicle spawned: Spawn Object to every observer. */
        void onVehicleSpawned(VehicleEntity vehicle);

        /** A vehicle moved this tick: Entity Teleport (the fast-path sync). */
        void onVehicleMoved(VehicleEntity vehicle);

        /** A vehicle took a hit: the hurt status flash to every observer. */
        void onVehicleHurt(VehicleEntity vehicle);

        /** A player took the seat: Attach Entity + the riding flags. */
        void onVehicleMounted(VehicleEntity vehicle, int riderEngineId);

        /** A player left the seat: Attach Entity clear + the flags reset. */
        void onVehicleDismounted(VehicleEntity vehicle, int riderEngineId,
                                 Position riderExit);

        /** A vehicle broke: Destroy + the item drop at its position. */
        void onVehicleBroken(VehicleEntity vehicle);
    }

    /** The break drop sink (the engine's item-entity spawner). */
    public interface DropSink {
        void dropVehicleItem(VehicleEntity vehicle);
    }

    private final VehicleEntity.WorldQuery world;
    private final DropSink drops;
    private final Random random;
    private final List<VehicleEntity> vehicles = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final int firstEntityId;
    private int nextEntityId;

    public VehicleManager(VehicleEntity.WorldQuery world, DropSink drops,
                          Random random, int firstEntityId) {
        this.world = Objects.requireNonNull(world, "world");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.random = Objects.requireNonNull(random, "random");
        this.firstEntityId = firstEntityId;
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** @return every live vehicle (the join replay and diagnostics). */
    public List<VehicleEntity> all() {
        return List.copyOf(vehicles);
    }

    public VehicleEntity byId(int entityId) {
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.entityId() == entityId) {
                return vehicle;
            }
        }
        return null;
    }

    public VehicleEntity boatAt(double x, double y, double z) {
        return nearestOfKind(VehicleEntity.Kind.BOAT, x, y, z);
    }

    public VehicleEntity minecartAt(double x, double y, double z) {
        return nearestOfKind(VehicleEntity.Kind.MINECART, x, y, z);
    }

    /** @return the vehicle whose body contains the point, or null. */
    public VehicleEntity vehicleContaining(double x, double y, double z) {
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.contains(x, y, z)) {
                return vehicle;
            }
        }
        return null;
    }

    private VehicleEntity nearestOfKind(VehicleEntity.Kind kind, double x, double y, double z) {
        VehicleEntity best = null;
        double bestDistance = 4.0; // the interact reach's generous band
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.kind() != kind) {
                continue;
            }
            Position at = vehicle.position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = vehicle;
            }
        }
        return best;
    }

    public VehicleEntity spawnBoat(Position at) {
        return spawn(new BoatEntity(nextEntityId++, at, random));
    }

    public VehicleEntity spawnMinecart(Position at) {
        return spawn(new MinecartEntity(nextEntityId++, at, random));
    }

    private VehicleEntity spawn(VehicleEntity vehicle) {
        vehicles.add(vehicle);
        for (Listener listener : listeners) {
            listener.onVehicleSpawned(vehicle);
        }
        return vehicle;
    }

    /**
     * The tick: steering rides the passenger's input, the body follows its
     * kind's rules, the mounted rider's state rides along (the engine owns
     * the player position while the seat is taken), and the void consumes.
     */
    public void tick() {
        Iterator<VehicleEntity> cursor = vehicles.iterator();
        while (cursor.hasNext()) {
            VehicleEntity vehicle = cursor.next();
            vehicle.tickHurtCooldown();
            if (vehicle.inVoid()) {
                removeVehicle(cursor, vehicle, "fell out of world");
                continue;
            }
            if (vehicle.dead()) {
                removeVehicle(cursor, vehicle, "broke");
                continue;
            }
            long before = fixedPoint(vehicle.position());
            vehicle.tick(world);
            if (fixedPoint(vehicle.position()) != before) {
                for (Listener listener : listeners) {
                    listener.onVehicleMoved(vehicle);
                }
            }
        }
    }

    private void removeVehicle(Iterator<VehicleEntity> cursor, VehicleEntity vehicle, String reason) {
        cursor.remove();
        if (vehicle.hasPassenger()) {
            dismount(vehicle);
        }
        drops.dropVehicleItem(vehicle);
        for (Listener listener : listeners) {
            listener.onVehicleBroken(vehicle);
        }
    }

    /** The mount bookkeeping; the listener fans the wire out. */
    public boolean mount(VehicleEntity vehicle, int riderEngineId) {
        if (!vehicle.mount(riderEngineId)) {
            return false;
        }
        for (Listener listener : listeners) {
            listener.onVehicleMounted(vehicle, riderEngineId);
        }
        return true;
    }

    /** The dismount bookkeeping; the exit point sits beside the vehicle. */
    public void dismount(VehicleEntity vehicle) {
        int rider = vehicle.passengerId();
        if (rider < 0) {
            return;
        }
        vehicle.dismount();
        Position at = vehicle.position();
        Position exit = new Position(at.x() + 1.2, at.y() + 0.4, at.z());
        for (Listener listener : listeners) {
            listener.onVehicleDismounted(vehicle, rider, exit);
        }
    }

    /** One attack hit: the i-frame rhythm the body model shares. */
    public void hurt(VehicleEntity vehicle, float amount) {
        float before = vehicle.health();
        vehicle.hurt(amount);
        if (vehicle.health() != before) {
            for (Listener listener : listeners) {
                listener.onVehicleHurt(vehicle);
            }
        }
    }

    private static long fixedPoint(Position position) {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }
}
