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

        /**
         * The TNT cart's fuse armed (Slice 66 — prime lines 141-153:
         * the doEntityEvent 10 the client's fuse visual rides + the
         * game.tnt.primed sound).
         */
        default void onVehiclePrimed(TntMinecartEntity cart) {
        }

        /**
         * A vehicle's tracked state flipped (Slice 66 — the furnace cart's
         * lit byte at the synced slot 16: the client swaps the furnace
         * texture). Default no-op so older listeners stay source-compatible.
         */
        default void onVehicleStateChanged(VehicleEntity vehicle) {
        }

        /**
         * The spawner cart's cycle fired a spawn (Slice 66 — MobSpawner
         * line 108's doEvent(2004, blockpos, 0) at the cart's cell: the
         * flame burst the client draws).
         */
        default void onSpawnerCartFlames(SpawnerMinecartEntity cart,
                double x, double y, double z) {
        }

        /**
         * The spawner cart's delay re-rolled (Slice 66 — delay line 187's
         * broacastEvent(1) over the cart's own doEntityEvent: the Entity
         * Status 1 spin reset).
         */
        default void onSpawnerCartSpinReset(SpawnerMinecartEntity cart) {
        }
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

    /** The furnace minecart (Slice 66 — the coal-fed pusher). */
    public VehicleEntity spawnFurnaceMinecart(Position at) {
        if (!fireSpawnGate(VehicleEntity.Kind.FURNACE_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new FurnaceMinecartEntity(nextEntityId++, at, random));
    }

    /** The TNT minecart (Slice 66 — the 80-tick fuse). */
    public VehicleEntity spawnTntMinecart(Position at) {
        if (!fireSpawnGate(VehicleEntity.Kind.TNT_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new TntMinecartEntity(nextEntityId++, at, random));
    }

    /**
     * The hopper minecart (Slice 66 — the 5-slot rolling pickup): the
     * transfer host is the engine's hopper system (the reference's
     * HopperBlockEntity statics over the cart's mouth).
     */
    public VehicleEntity spawnHopperMinecart(Position at,
            HopperMinecartEntity.TransferHost host) {
        if (!fireSpawnGate(VehicleEntity.Kind.HOPPER_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new HopperMinecartEntity(nextEntityId++, at, random, host));
    }

    /**
     * The spawner minecart (Slice 66 — the rolling spawner): the spawn
     * host is the engine's spawner surfaces (the player gate, the nearby
     * count, the unobstructed spawn).
     */
    public VehicleEntity spawnSpawnerMinecart(Position at,
            SpawnerMinecartEntity.SpawnHost host) {
        if (!fireSpawnGate(VehicleEntity.Kind.SPAWNER_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new SpawnerMinecartEntity(nextEntityId++, at, random, host));
    }

    /**
     * The command-block minecart (Slice 66 — the rolling executor): the
     * command host is the engine's command service with the executor's
     * own sender shape (op level 2, the name "@", the output landing in
     * the cart's last output).
     */
    public VehicleEntity spawnCommandBlockMinecart(Position at,
            CommandBlockMinecartEntity.CommandHost host) {
        if (!fireSpawnGate(VehicleEntity.Kind.COMMAND_BLOCK_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new CommandBlockMinecartEntity(nextEntityId++, at, random, host));
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
     * The persistable view of one live vehicle (Slice 25g's ZVD payload):
     * kind, position, facing, remaining health, and the container cart's
     * cargo (27 slots for the chest variant, 5 for the hopper variant,
     * null for the other kinds) plus the variant's own state (the
     * hopper's disable flag + transfer cooldown — Slice 66's variant
     * payload). Broken bodies are not captured.
     */
    public record VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                                  float health,
                                  net.zaminmc.torch.item.ItemStack[] cargo,
                                  boolean hopperEnabled, int hopperCooldown,
                                  String spawnerType, int spawnerDelay,
                                  String command, int commandSuccessCount,
                                  boolean commandTrackOutput, String commandLastOutput) {

        /** The pre-Slice-66 shape (the chest cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo) {
            this(kind, x, y, z, yaw, health, cargo, true, -1, null, -1, null, 0, true, null);
        }

        /** The part-three shape (the hopper cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown, null, -1,
                    null, 0, true, null);
        }

        /** The part-four shape (the spawner cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown,
                               String spawnerType, int spawnerDelay) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                    spawnerType, spawnerDelay, null, 0, true, null);
        }
    }

    /** @return the persistable snapshot of every live vehicle. Tick-thread context. */
    public List<VehicleSnapshot> snapshot() {
        List<VehicleSnapshot> saved = new ArrayList<>();
        for (VehicleEntity vehicle : vehicles) {
            if (vehicle.dead()) {
                continue;
            }
            Position at = vehicle.position();
            net.zaminmc.torch.item.ItemStack[] cargo = null;
            if (vehicle instanceof ChestMinecartEntity cart) {
                cargo = cart.chest().snapshotSlots();
            } else if (vehicle instanceof HopperMinecartEntity hopperCart) {
                cargo = hopperCart.hopper().snapshotSlots();
            }
            saved.add(new VehicleSnapshot(vehicle.kind().name(), at.x(), at.y(), at.z(),
                    vehicle.rotation().yaw(), vehicle.health(), cargo,
                    !(vehicle instanceof HopperMinecartEntity hopperCart)
                            || hopperCart.enabled(),
                    vehicle instanceof HopperMinecartEntity hopperCart
                            ? hopperCart.transferCooldown() : -1,
                    vehicle instanceof SpawnerMinecartEntity spawnerCart
                            ? spawnerCart.type().name() : null,
                    vehicle instanceof SpawnerMinecartEntity spawnerCart
                            ? spawnerCart.delay() : -1,
                    vehicle instanceof CommandBlockMinecartEntity commandCart
                            ? commandCart.command() : null,
                    vehicle instanceof CommandBlockMinecartEntity commandCart
                            ? commandCart.successCount() : 0,
                    !(vehicle instanceof CommandBlockMinecartEntity commandCart)
                            || commandCart.trackOutput(),
                    vehicle instanceof CommandBlockMinecartEntity commandCart
                            ? commandCart.lastOutput() : null));
        }
        return saved;
    }

    /**
     * Restores the saved population (Slice 25g — the whole vehicle family's
     * pre-25g gap closes: the carts never carried a store). Unknown kinds
     * drop with a warning; the spawn gate rides the spawn verbs like every
     * arrival. Returns whether any vehicle was restored. The hopper cart's
     * transfer host arrives as a supplier (the engine's hopper system —
     * Slice 66's variant payload carries the disable flag + cooldown).
     */
    public boolean restoreAll(List<VehicleSnapshot> saved) {
        return restoreAll(saved, null);
    }

    public boolean restoreAll(List<VehicleSnapshot> saved,
            java.util.function.Supplier<HopperMinecartEntity.TransferHost> hopperHost) {
        return restoreAll(saved, hopperHost, null);
    }

    public boolean restoreAll(List<VehicleSnapshot> saved,
            java.util.function.Supplier<HopperMinecartEntity.TransferHost> hopperHost,
            java.util.function.Supplier<SpawnerMinecartEntity.SpawnHost> spawnerHost) {
        return restoreAll(saved, hopperHost, spawnerHost, null);
    }

    /** The full restore (every variant host armed when given). */
    public boolean restoreAll(List<VehicleSnapshot> saved,
            java.util.function.Supplier<HopperMinecartEntity.TransferHost> hopperHost,
            java.util.function.Supplier<SpawnerMinecartEntity.SpawnHost> spawnerHost,
            java.util.function.Supplier<CommandBlockMinecartEntity.CommandHost> commandHost) {
        Objects.requireNonNull(saved, "saved");
        boolean any = false;
        for (VehicleSnapshot snapshot : saved) {
            VehicleEntity.Kind kind;
            try {
                kind = VehicleEntity.Kind.valueOf(snapshot.kind());
            } catch (IllegalArgumentException unknownKind) {
                continue;
            }
            Position at = new Position(snapshot.x(), snapshot.y(), snapshot.z());
            VehicleEntity restored;
            switch (kind) {
                case BOAT -> restored = spawnBoat(at);
                case MINECART -> restored = spawnMinecart(at);
                case CHEST_MINECART -> restored = spawnChestMinecart(at);
                case FURNACE_MINECART -> restored = spawnFurnaceMinecart(at);
                case TNT_MINECART -> restored = spawnTntMinecart(at);
                case HOPPER_MINECART -> {
                    if (hopperHost == null) {
                        continue; // no host wired: the cart cannot suck
                    }
                    VehicleEntity cart = spawnHopperMinecart(at, hopperHost.get());
                    if (cart instanceof HopperMinecartEntity hopperCart) {
                        hopperCart.setEnabled(snapshot.hopperEnabled());
                        hopperCart.setTransferCooldown(snapshot.hopperCooldown());
                    }
                    restored = cart;
                }
                case SPAWNER_MINECART -> {
                    if (spawnerHost == null) {
                        continue; // no host wired: the cart cannot spawn
                    }
                    VehicleEntity cart = spawnSpawnerMinecart(at, spawnerHost.get());
                    if (cart instanceof SpawnerMinecartEntity spawnerCart) {
                        // The NBT rows' landing (readNbt's EntityId/Delay):
                        // a null type keeps the reference's own "Pig"
                        // field default.
                        if (snapshot.spawnerType() != null) {
                            try {
                                spawnerCart.setType(net.zaminmc.torch.server.entity.MobType
                                        .valueOf(snapshot.spawnerType()));
                            } catch (IllegalArgumentException unknownType) {
                                // a saved kind the engine no longer knows:
                                // the field default holds
                            }
                        }
                        if (snapshot.spawnerDelay() >= 0) {
                            spawnerCart.setDelay(snapshot.spawnerDelay());
                        }
                    }
                    restored = cart;
                }
                case COMMAND_BLOCK_MINECART -> {
                    if (commandHost == null) {
                        continue; // no host wired: the cart cannot run
                    }
                    VehicleEntity cart = spawnCommandBlockMinecart(at, commandHost.get());
                    if (cart instanceof CommandBlockMinecartEntity commandCart) {
                        // readNbt's rows (executor.readNbt lines 53-74):
                        // Command, SuccessCount, TrackOutput, LastOutput.
                        if (snapshot.command() != null) {
                            commandCart.setCommand(snapshot.command());
                        }
                        commandCart.setSuccessCount(snapshot.commandSuccessCount());
                        commandCart.setTrackOutput(snapshot.commandTrackOutput());
                        if (snapshot.commandLastOutput() != null) {
                            commandCart.setLastOutput(snapshot.commandLastOutput());
                        }
                    }
                    restored = cart;
                }
                default -> {
                    continue;
                }
            }
            if (restored != null) {
                restored.setHealthCarried(snapshot.health());
                if (snapshot.cargo() != null) {
                    if (restored instanceof ChestMinecartEntity cart) {
                        for (int slot = 0; slot < snapshot.cargo().length
                                && slot < net.zaminmc.torch.server.chest.ChestBlockEntity.SLOT_COUNT;
                                slot++) {
                            net.zaminmc.torch.item.ItemStack stack = snapshot.cargo()[slot];
                            if (stack != null && !stack.isEmpty()) {
                                cart.chest().setSlot(slot, stack);
                            }
                        }
                    } else if (restored instanceof HopperMinecartEntity hopperCart) {
                        for (int slot = 0; slot < snapshot.cargo().length
                                && slot < net.zaminmc.torch.server.hopper.HopperBlockEntity.SLOT_COUNT;
                                slot++) {
                            net.zaminmc.torch.item.ItemStack stack = snapshot.cargo()[slot];
                            if (stack != null && !stack.isEmpty()) {
                                hopperCart.hopper().setItem(slot, stack);
                            }
                        }
                    }
                }
                any = true;
            }
        }
        return any;
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
            // The state flips (Slice 66): the TNT cart's prime and the
            // furnace cart's lit byte — the wire's own tracked data.
            boolean wasPrimed = vehicle instanceof TntMinecartEntity tnt && tnt.primed();
            boolean wasLit = vehicle instanceof FurnaceMinecartEntity furnace && furnace.lit();
            // The TNT cart's activator-rail consult (Slice 66 — the
            // reference's moveAlongTrack fires onActivatorRail per rail
            // cell): the powered cell primes the fuse.
            if (vehicle instanceof TntMinecartEntity tntCart) {
                tntCart.onActivatorRail(world.railPowerAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()) > 0);
            }
            // The hopper cart's own consult (Slice 66 —
            // HopperMinecartEntity.onActivatorRail lines 63-68): a POWERED
            // activator rail disables the transfer walk, an unpowered one
            // re-enables it (the inverse sense of the TNT prime).
            if (vehicle instanceof HopperMinecartEntity hopperCart) {
                hopperCart.onActivatorRail(world.railPowerAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()) > 0);
            }
            // The command cart's own consult (Slice 66 —
            // CommandBlockMinecartEntity.onActivatorRail lines 99-104): a
            // powered activator rail fires the command, at most once every
            // 4 ticks.
            String commandBefore = vehicle instanceof CommandBlockMinecartEntity commandCart
                    ? commandCart.command() : null;
            String outputBefore = vehicle instanceof CommandBlockMinecartEntity commandCart
                    ? commandCart.lastOutput() : null;
            if (vehicle instanceof CommandBlockMinecartEntity commandCart) {
                commandCart.onActivatorRail(world.railPowerAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()) > 0);
            }
            vehicle.tick(world);
            if (!wasPrimed && vehicle instanceof TntMinecartEntity tnt && tnt.primed()) {
                for (Listener listener : listeners) {
                    listener.onVehiclePrimed(tnt);
                }
            }
            if (wasLit != (vehicle instanceof FurnaceMinecartEntity furnace && furnace.lit())) {
                for (Listener listener : listeners) {
                    listener.onVehicleStateChanged(vehicle);
                }
            }
            // The spawner cart's two broadcasts (Slice 66): the per-spawn
            // flame burst (the 2004 world event at the cart's cell) and
            // the delay reroll's spin reset (the Entity Status 1).
            if (vehicle instanceof SpawnerMinecartEntity spawnerCart) {
                int flames = spawnerCart.consumeFlameBursts();
                if (flames > 0) {
                    for (Listener listener : listeners) {
                        listener.onSpawnerCartFlames(spawnerCart,
                                Math.floor(spawnerCart.position().x()),
                                Math.floor(spawnerCart.position().y()),
                                Math.floor(spawnerCart.position().z()));
                    }
                }
                if (spawnerCart.consumeSpinResets() > 0) {
                    for (Listener listener : listeners) {
                        listener.onSpawnerCartSpinReset(spawnerCart);
                    }
                }
            }
            // The command cart's synced strings (Slice 66 — the slots 23
            // and 24): a changed command or last output re-sends the
            // metadata (the client's edit GUI reads them).
            if (vehicle instanceof CommandBlockMinecartEntity commandCart) {
                boolean commandChanged = !java.util.Objects.equals(commandBefore,
                        commandCart.command());
                boolean outputChanged = !java.util.Objects.equals(outputBefore,
                        commandCart.lastOutput());
                if (commandChanged || outputChanged) {
                    for (Listener listener : listeners) {
                        listener.onVehicleStateChanged(vehicle);
                    }
                }
            }
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
