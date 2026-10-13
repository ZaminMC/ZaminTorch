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

        /**
         * The chest minecart's death extras (Slice 21a — the reference's
         * dropItems chain): the contents spill first (InventoryMinecart's
         * InventoryUtils.dropItems), then the CHEST block item
         * (ChestMinecart's own dropItem). The plain minecart item already
         * landed through {@link #dropVehicleItem} (MinecartEntity's own
         * dropItems — the cart item, never the chest-minecart item).
         */
        default void dropChestMinecartExtras(VehicleEntity vehicle,
                java.util.List<net.zaminmc.torch.item.ItemStack> contents) {
        }
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

    /**
     * The public event bus (§27.7's spawn gate); null until the engine
     * wires it — the unwired window admits every spawn.
     */
    private volatile net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /** Wires the §27.7 spawn gate over every spawn family member. */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
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
        // §27.7's spawn gate: a cancelled boat consumes no id, spawns no
        // body, and withholds the placing item (the caller's contract).
        if (!fireSpawnGate(VehicleEntity.Kind.BOAT.apiKind, at)) {
            return null;
        }
        return spawn(new BoatEntity(nextEntityId++, at, random));
    }

    public VehicleEntity spawnMinecart(Position at) {
        if (!fireSpawnGate(VehicleEntity.Kind.MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new MinecartEntity(nextEntityId++, at, random));
    }

    /** The chest minecart (the 27-slot rolling container). */
    public VehicleEntity spawnChestMinecart(Position at) {
        if (!fireSpawnGate(VehicleEntity.Kind.CHEST_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new ChestMinecartEntity(nextEntityId++, at, random));
    }

    /**
     * The §27.7 pre-commit gate over every spawn family member: the
     * boat/minecart item placements and the structure-seeded chest
     * carts. A null bus (the unwired window) admits everything.
     */
    private boolean fireSpawnGate(net.zaminmc.torch.entity.VehicleKind kind, Position at) {
        var bus = eventBus;
        if (bus == null) {
            return true;
        }
        var event = new net.zaminmc.torch.event.entity.VehicleSpawnEvent(kind, at);
        bus.fire(event);
        return !event.cancelled();
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
                // The void consumes without a drop (the reference's
                // outOfWorld -> setDead: no dropItems call ever runs —
                // Slice 21a's correction of the pre-21a always-drop arm).
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
        boolean broke = "broke".equals(reason);
        if (broke) {
            // The death drops (takeDamage's dropItems chain): the plain
            // minecart/boat item first, then the chest variant's extras.
            drops.dropVehicleItem(vehicle);
            if (vehicle instanceof ChestMinecartEntity cart) {
                java.util.List<net.zaminmc.torch.item.ItemStack> contents =
                        new java.util.ArrayList<>();
                for (net.zaminmc.torch.item.ItemStack stack : cart.chest().snapshotSlots()) {
                    if (stack != null && !stack.isEmpty()) {
                        contents.add(stack);
                    }
                }
                drops.dropChestMinecartExtras(vehicle, contents);
            }
        }
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
