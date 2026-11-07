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

    /**
     * The rider's fall payment (F-RIDER-SPARE — the vehicles' landing
     * delegate): the engine resolves the seated player's session by id
     * and pays the landing with the landed cell's multiplier. Injected
     * into every spawned body (the spawn walk); null keeps the payment
     * a no-op.
     */
    private volatile VehicleEntity.RiderFall riderFall;

    /** Wires the rider-fall seam (the engine's own resolution walk). */
    public void setRiderFall(VehicleEntity.RiderFall fall) {
        this.riderFall = java.util.Objects.requireNonNull(fall, "fall");
    }

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
         * The wet extinguish's own arm (Slice 78 — Entity.java lines
         * 648-651: the random.fizz at 0.7F / 1.6F±0.2 the adapter plays).
         * Default no-op so older listeners stay source-compatible.
         */
        default void onVehicleFizz(VehicleEntity vehicle) {
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
    /**
     * The TNT carts' blast sink factory (Slice 74): every spawned cart
     * arms the engine's detonation walk — the item-use path, the restore,
     * the test spawns. The default answers nothing until the engine wires
     * the real walk.
     */
    private java.util.function.Supplier<TntMinecartEntity.BlastSink> blastSinkFactory =
            () -> (x, y, z, power, primed, dimension) -> { };

    /** Wires the blast sink factory (the engine's detonation walk). */
    public void setBlastSinkFactory(
            java.util.function.Supplier<TntMinecartEntity.BlastSink> factory) {
        this.blastSinkFactory = java.util.Objects.requireNonNull(factory, "factory");
    }

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

    public VehicleEntity spawnBoat(Position at, int dimension) {
        // §27.7's spawn gate: a cancelled boat consumes no id, spawns no
        // body, and withholds the placing item (the caller's contract).
        if (!fireSpawnGate(VehicleEntity.Kind.BOAT.apiKind, at)) {
            return null;
        }
        return spawn(new BoatEntity(nextEntityId++, at, random), dimension);
    }

    public VehicleEntity spawnMinecart(Position at, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new MinecartEntity(nextEntityId++, at, random), dimension);
    }

    /** The chest minecart (the 27-slot rolling container). */
    public VehicleEntity spawnChestMinecart(Position at, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.CHEST_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new ChestMinecartEntity(nextEntityId++, at, random), dimension);
    }

    /** The furnace minecart (Slice 66 — the coal-fed pusher). */
    public VehicleEntity spawnFurnaceMinecart(Position at, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.FURNACE_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new FurnaceMinecartEntity(nextEntityId++, at, random), dimension);
    }

    /** The TNT minecart (Slice 66 — the 80-tick fuse). */
    public VehicleEntity spawnTntMinecart(Position at, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.TNT_MINECART.apiKind, at)) {
            return null;
        }
        TntMinecartEntity cart = new TntMinecartEntity(nextEntityId++, at, random);
        // The blast sink's factory arming (Slice 74 — the pre-74 gap: only
        // the item-use spawn path wired the sink, so a RESTORED cart's
        // fuse detonation and its death detonation burst nothing after a
        // restart). Every spawn arms the same engine detonation walk.
        cart.setBlastSink(blastSinkFactory.get());
        return spawn(cart, dimension);
    }

    /**
     * The hopper minecart (Slice 66 — the 5-slot rolling pickup): the
     * transfer host is the engine's hopper system (the reference's
     * HopperBlockEntity statics over the cart's mouth).
     */
    public VehicleEntity spawnHopperMinecart(Position at,
            HopperMinecartEntity.TransferHost host, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.HOPPER_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new HopperMinecartEntity(nextEntityId++, at, random, host), dimension);
    }

    /**
     * The spawner minecart (Slice 66 — the rolling spawner): the spawn
     * host is the engine's spawner surfaces (the player gate, the nearby
     * count, the unobstructed spawn).
     */
    public VehicleEntity spawnSpawnerMinecart(Position at,
            SpawnerMinecartEntity.SpawnHost host, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.SPAWNER_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new SpawnerMinecartEntity(nextEntityId++, at, random, host), dimension);
    }

    /**
     * The command-block minecart (Slice 66 — the rolling executor): the
     * command host is the engine's command service with the executor's
     * own sender shape (op level 2, the name "@", the output landing in
     * the cart's last output).
     */
    public VehicleEntity spawnCommandBlockMinecart(Position at,
            CommandBlockMinecartEntity.CommandHost host, int dimension) {
        if (!fireSpawnGate(VehicleEntity.Kind.COMMAND_BLOCK_MINECART.apiKind, at)) {
            return null;
        }
        return spawn(new CommandBlockMinecartEntity(nextEntityId++, at, random, host), dimension);
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

    private VehicleEntity spawn(VehicleEntity vehicle, int dimension) {
        // Wave TF-11 — the dimension stamps the body BEFORE the listener
        // fan (the spawn publications read it; the TF-9 lesson).
        vehicle.setDimension(dimension);
        var fall = riderFall;
        if (fall != null) {
            // F-RIDER-SPARE: the vehicle's landing delegate (the seated
            // player pays the fall; the vehicle pays nothing itself).
            vehicle.setRiderFall(fall);
        }
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
    /**
     * The base rows (the reference's Entity.readNbt base band —
     * FallDistance, Air, OnGround, Dimension, Invulnerable,
     * PortalCooldown, the UUID pair, CustomName + Visible, Silent): the
     * snapshot's own form, the store's v4 rows.
     */
    public record BaseRows(long uuidMost, long uuidLeast, boolean hasUuid,
                           String customName, boolean customNameVisible, boolean silent,
                           float fallDistance, int breath, boolean onGround,
                           boolean invulnerable, int dimension, int portalCooldown) {

        /** The fresh-body default (the model's own field defaults). */
        public static final BaseRows DEFAULT = new BaseRows(0L, 0L, false,
                null, false, false, 0.0f, 300, false, false, 0, 0);

        /** The live form: the vehicle's own fields. */
        public static BaseRows of(VehicleEntity vehicle) {
            java.util.UUID uuid = vehicle.uuid();
            return new BaseRows(
                    uuid == null ? 0L : uuid.getMostSignificantBits(),
                    uuid == null ? 0L : uuid.getLeastSignificantBits(),
                    uuid != null,
                    vehicle.customName(), vehicle.customNameVisible(), vehicle.silent(),
                    vehicle.fallDistance(), vehicle.breath(), vehicle.onGround(),
                    vehicle.invulnerable(), vehicle.dimension(), vehicle.portalCooldown());
        }

        /** The UUID reconstruction (null when the row was absent). */
        public java.util.UUID uuid() {
            return hasUuid ? new java.util.UUID(uuidMost, uuidLeast) : null;
        }
    }

    public record VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                                  float health,
                                  net.zaminmc.torch.item.ItemStack[] cargo,
                                  boolean hopperEnabled, int hopperCooldown,
                                  String spawnerType, int spawnerDelay,
                                  String command, int commandSuccessCount,
                                  boolean commandTrackOutput, String commandLastOutput,
                                  int fire, BaseRows base, CadenceRows cadence) {

        /** The pre-Slice-66 shape (the chest cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo) {
            this(kind, x, y, z, yaw, health, cargo, true, -1, null, -1, null, 0, true, null,
                    -VehicleEntity.FIRE_SAFE_TICKS, BaseRows.DEFAULT, null);
        }

        /** The part-three shape (the hopper cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown, null, -1,
                    null, 0, true, null, -VehicleEntity.FIRE_SAFE_TICKS, BaseRows.DEFAULT, null);
        }

        /** The part-four shape (the spawner cart's own). */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown,
                               String spawnerType, int spawnerDelay) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                    spawnerType, spawnerDelay, null, 0, true, null,
                    -VehicleEntity.FIRE_SAFE_TICKS, BaseRows.DEFAULT, null);
        }

        /**
         * The pre-Slice-78 full shape (the command cart's own): the fire
         * clock loads its fresh-body default.
         */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown,
                               String spawnerType, int spawnerDelay,
                               String command, int commandSuccessCount,
                               boolean commandTrackOutput, String commandLastOutput) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                    spawnerType, spawnerDelay, command, commandSuccessCount,
                    commandTrackOutput, commandLastOutput, -VehicleEntity.FIRE_SAFE_TICKS,
                    BaseRows.DEFAULT);
        }

        /**
         * The pre-base-rows shape (the import-era store's own): the base
         * rows load their fresh-body default.
         */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown,
                               String spawnerType, int spawnerDelay,
                               String command, int commandSuccessCount,
                               boolean commandTrackOutput, String commandLastOutput,
                               int fire) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                    spawnerType, spawnerDelay, command, commandSuccessCount,
                    commandTrackOutput, commandLastOutput, fire, BaseRows.DEFAULT, null);
        }

        /**
         * The pre-cadence-rows shape (the Wave VI per-cart surface's own
         * v5 prefix): the cadence rows load the reference's own defaults.
         */
        public VehicleSnapshot(String kind, double x, double y, double z, float yaw,
                               float health, net.zaminmc.torch.item.ItemStack[] cargo,
                               boolean hopperEnabled, int hopperCooldown,
                               String spawnerType, int spawnerDelay,
                               String command, int commandSuccessCount,
                               boolean commandTrackOutput, String commandLastOutput,
                               int fire, BaseRows base) {
            this(kind, x, y, z, yaw, health, cargo, hopperEnabled, hopperCooldown,
                    spawnerType, spawnerDelay, command, commandSuccessCount,
                    commandTrackOutput, commandLastOutput, fire, base, null);
        }
    }

    /**
     * The spawner cart's cadence rows (the Wave VI demand-named per-cart
     * surface — MobSpawnerBaseLogic's own six fields, writeNbt's rows):
     * null keeps the reference's own defaults (the absent-row shape).
     */
    public record CadenceRows(int minSpawnDelay, int maxSpawnDelay, int spawnCount,
                              int maxNearbyEntities, int requiredPlayerRange, int spawnRange) {

        /** The capture (the live cart's own rows). */
        public static CadenceRows of(SpawnerMinecartEntity cart) {
            return new CadenceRows(cart.minSpawnDelay(), cart.maxSpawnDelay(),
                    cart.spawnCount(), cart.maxNearbyEntities(),
                    cart.requiredPlayerRange(), cart.spawnRange());
        }

        /** The landing (readNbt's rows — the restore arm). */
        public void land(SpawnerMinecartEntity cart) {
            cart.setMinSpawnDelay(minSpawnDelay);
            cart.setMaxSpawnDelay(maxSpawnDelay);
            cart.setSpawnCount(spawnCount);
            cart.setMaxNearbyEntities(maxNearbyEntities);
            cart.setRequiredPlayerRange(requiredPlayerRange);
            cart.setSpawnRange(spawnRange);
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
                            ? commandCart.lastOutput() : null,
                    vehicle.onFireTimer(), BaseRows.of(vehicle),
                    vehicle instanceof SpawnerMinecartEntity spawnerCart
                            ? CadenceRows.of(spawnerCart) : null));
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
            // Wave TF-11 — the stored dimension rides the spawn stamp (the
            // body joins ITS OWN world; the restoreBaseRows call below
            // re-lands the whole base band anyway).
            VehicleManager.BaseRows base = snapshot.base();
            VehicleEntity restored;
            switch (kind) {
                case BOAT -> restored = spawnBoat(at, base.dimension());
                case MINECART -> restored = spawnMinecart(at, base.dimension());
                case CHEST_MINECART -> restored = spawnChestMinecart(at, base.dimension());
                case FURNACE_MINECART -> restored = spawnFurnaceMinecart(at, base.dimension());
                case TNT_MINECART -> restored = spawnTntMinecart(at, base.dimension());
                case HOPPER_MINECART -> {
                    if (hopperHost == null) {
                        continue; // no host wired: the cart cannot suck
                    }
                    VehicleEntity cart = spawnHopperMinecart(at, hopperHost.get(), base.dimension());
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
                    VehicleEntity cart = spawnSpawnerMinecart(at, spawnerHost.get(), base.dimension());
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
                        // The cadence rows (the Wave VI per-cart surface):
                        // the saved band, attempts, cap, gate, and roll
                        // range land whole — a null keeps the defaults.
                        if (snapshot.cadence() != null) {
                            snapshot.cadence().land(spawnerCart);
                        }
                    }
                    restored = cart;
                }
                case COMMAND_BLOCK_MINECART -> {
                    if (commandHost == null) {
                        continue; // no host wired: the cart cannot run
                    }
                    VehicleEntity cart = spawnCommandBlockMinecart(at, commandHost.get(), base.dimension());
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
                // The burn clock's own row (Entity.java line 1176's Fire
                // short: the raw signed value — a burning cart keeps its
                // burn across the restart).
                restored.restoreFireTimer(snapshot.fire());
                // The base rows (Entity.readNbt's base band — the vehicle
                // store's v4 round trip).
                restored.restoreBaseRows(base.uuid(), base.customName(),
                        base.customNameVisible(), base.silent(), base.fallDistance(),
                        base.breath(), base.onGround(), base.invulnerable(),
                        base.dimension(), base.portalCooldown());
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
            // Wave TF-11 — the reference's Entity.world: the shared query
            // flips to the body's own dimension before every tick walk (the
            // TF-9 item family's own per-body flip, generalized). A nether
            // cart rides the nether's rails, never the overworld's
            // same-coordinate track.
            world.setActiveDimension(vehicle.dimension());
            long before = fixedPoint(vehicle.position());
            // The burn clock's own arm (Slice 78 — Entity.java lines
            // 320-335, the onUpdate fire arm): the 1.0 ON_FIRE wound every
            // full second of burning (the % 20 == 0 cadence), the clock
            // ticking down regardless of the i-frame's verdict — exactly
            // the reference's attempted-damage shape).
            boolean wasOnFire = vehicle.onFire();
            if (vehicle.health() > 0.0f && vehicle.tickOnFire()) {
                hurt(vehicle, 1.0f, VehicleEntity.DamageSource.FIRE);
            }
            // The lava walk (Slice 77 — the reference's isInLava arm over
            // Entity.tick: takeLavaDamage's flat 4.0 with the FIRE source —
            // the wound cadence rides the body's own i-frame rhythm, and a
            // dying TNT cart reads the FIRE source through the death law
            // (the fire-death detonation). The lava also halves the fall
            // (the reference's fallDistance *= 0.5 rides the physics walk).
            // Slice 78 closes the walk's own gap: takeLavaDamage's second
            // arm — setOnFireFor(15), the 15-second lava burn.
            if (vehicle.health() > 0.0f && world.inLava(
                    vehicle.position().x(), vehicle.position().y() + 0.4,
                    vehicle.position().z())) {
                hurt(vehicle, 4.0f, VehicleEntity.DamageSource.FIRE);
                vehicle.setOnFireFor(15);
                // The fall's lava halving (the reference's fallDistance
                // *= 0.5 in the lava walk — the note the pre-wave comment
                // carried, made real by F-RIDER-SPARE's fall walk).
                vehicle.halveFall();
            }
            // The state flips (Slice 66): the TNT cart's prime and the
            // furnace cart's lit byte — the wire's own tracked data.
            boolean wasPrimed = vehicle instanceof TntMinecartEntity tnt && tnt.primed();
            boolean wasLit = vehicle instanceof FurnaceMinecartEntity furnace && furnace.lit();
            // The TNT cart's activator-rail consult (Slice 66 — the
            // reference's moveAlongTrack fires onActivatorRail per rail
            // cell, the ACTIVATOR family alone — Wave PE-14's family
            // gate): the powered activator cell primes the fuse.
            if (vehicle instanceof TntMinecartEntity tntCart) {
                tntCart.onActivatorRail(world.activatorRailPoweredAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()));
            }
            // The hopper cart's own consult (Slice 66 —
            // HopperMinecartEntity.onActivatorRail lines 63-68): a POWERED
            // activator rail disables the transfer walk, an unpowered one
            // re-enables it (the inverse sense of the TNT prime — the
            // ACTIVATOR family alone, PE-14's gate).
            if (vehicle instanceof HopperMinecartEntity hopperCart) {
                hopperCart.onActivatorRail(world.activatorRailPoweredAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()));
            }
            // The command cart's own consult (Slice 66 —
            // CommandBlockMinecartEntity.onActivatorRail lines 99-104): a
            // powered ACTIVATOR rail fires the command, at most once every
            // 4 ticks — the ACTIVATOR family alone (PE-14's gate: a powered
            // GOLDEN rail never fires it, whatever it boosts).
            String commandBefore = vehicle instanceof CommandBlockMinecartEntity commandCart
                    ? commandCart.command() : null;
            String outputBefore = vehicle instanceof CommandBlockMinecartEntity commandCart
                    ? commandCart.lastOutput() : null;
            if (vehicle instanceof CommandBlockMinecartEntity commandCart) {
                commandCart.onActivatorRail(world.activatorRailPoweredAt(
                        vehicle.position().x(),
                        vehicle.position().y() + 0.4,
                        vehicle.position().z()));
            }
            boolean wasGrounded = vehicle.onGround();
            double beforeTickY = vehicle.position().y();
            vehicle.tick(world);
            // THE FALL WALK (F-RIDER-SPARE — the reference's
            // Entity.checkFallDamage lines 719-733, the vehicles' own arm
            // the engine lacked whole): the descent accumulates only
            // while airborne (line 728-731's {@code else if (dy < 0.0)
            // fallDistance -= dy}); the landing (a fresh ground after a
            // fall, the store positive) pays ONCE through the landed
            // cell's consult then spends the store (line 733). The
            // payment is the base Entity.takeFallDamage ALONE (lines
            // 748-752 — the non-living base is the RIDER DELEGATE: the
            // seated player pays, the vehicle itself pays nothing) with
            // the cell's own multiplier: the pad's ZERO (a vehicle never
            // sneaks — the override's other arm is vacuous here), the
            // common blocks' 1.0. The farmland's instanceof LivingEntity
            // gate (FarmlandBlock.onFallenOn lines 57-71) skips vehicles
            // WHOLE — no trample, no payment, the store still spent.
            if (!vehicle.onGround()) {
                if (vehicle.position().y() < beforeTickY) {
                    vehicle.accumulateFall(beforeTickY - vehicle.position().y());
                }
            } else if (!wasGrounded && vehicle.fallDistance() > 0.0f) {
                float fallen = vehicle.fallDistance();
                // The landed-cell consult (Entity.move lines 569-581 —
                // the -0.2 feet cell with the air-cell fence fallback,
                // the mob landing's own derivation).
                int landX = (int) Math.floor(vehicle.position().x());
                int landY = (int) Math.floor(vehicle.position().y()
                        - vehicle.boxHalfHeight() - 0.2);
                int landZ = (int) Math.floor(vehicle.position().z());
                net.zaminmc.torch.util.Identifier landed =
                        world.blockIdAt(landX, landY, landZ);
                if (landed == null || landed.value().equals("air")) {
                    net.zaminmc.torch.util.Identifier under =
                            world.blockIdAt(landX, landY - 1, landZ);
                    if (under != null && (under.value().equals("oak_fence")
                            || under.value().equals("spruce_fence")
                            || under.value().equals("birch_fence")
                            || under.value().equals("jungle_fence")
                            || under.value().equals("dark_oak_fence")
                            || under.value().equals("acacia_fence")
                            || under.value().equals("nether_brick_fence"))) {
                        landed = under;
                    }
                }
                String landedName = landed == null ? "" : landed.value();
                if (landedName.equals("farmland") || landedName.equals("farmland_wet")) {
                    // The instanceof LivingEntity gate: a vehicle lands
                    // soft — nothing owed, nothing trampled.
                } else {
                    float multiplier = landedName.equals("slime_block") ? 0.0f : 1.0f;
                    vehicle.payRiderFall(fallen, multiplier);
                }
                vehicle.spendFall();
            }
            // Wave VC — THE VEHICLE FAMILY'S OWN COLLISION WALK (the
            // reference's in-tick placement: the walk closes the travel
            // section of the body's own tick — MinecartEntity.tick lines
            // 295-299 / BoatEntity.tick lines 347-356, after the move and
            // the yaw, before the rider consult): each vehicle gathers
            // the bodies in its OWN shape grown (0.2, 0.0, 0.2) and
            // pushes the members of its own family alone — the cart
            // pushes CARTS (the blend), the boat pushes BOATS (the
            // ordinary mutual law), never the living bodies (the living
            // gather pushes the vehicles — the PX wave's own law, the
            // reverse direction).
            tickVehicleCollisions(vehicle);
            // The fire-source contact walk (Slice 78 — Entity.java lines
            // 635-651, the move() fire arm): the body inside a fire-source
            // cell (the fire block, the lava cells — containsFireSource's
            // own family) takes its 1.0 IN_FIRE wound per i-frame window
            // and counts the grace up to the 8-second ignition; a dry body
            // away from the sources re-arms its one-tick grace; a wet body
            // drops the burn entirely (the fizz listener's own arm).
            if (vehicle.health() > 0.0f) {
                boolean wet = world.inWaterOrRain(
                        vehicle.position().x(), vehicle.position().y() + 0.4,
                        vehicle.position().z());
                if (world.inFireSource(
                        vehicle.position().x(), vehicle.position().y() + 0.4,
                        vehicle.position().z())) {
                    // takeFireDamage(1) — the IN_FIRE wound (the engine's
                    // one-wound-per-window body model shares the window
                    // with the burn clock's own ON_FIRE arm, the lava walk
                    // ahead of it).
                    hurt(vehicle, 1.0f, VehicleEntity.DamageSource.FIRE);
                    vehicle.contactFire(wet);
                } else {
                    vehicle.clearFireGrace();
                }
                if (wet && vehicle.onFire()) {
                    // The reference's own order: the fizz plays, then the
                    // grace re-arms (Entity.java lines 648-651).
                    for (Listener listener : listeners) {
                        listener.onVehicleFizz(vehicle);
                    }
                    vehicle.extinguish();
                }
            }
            // The flags-byte flip (Entity.java line 347's setFlag(0,
            // onFireTimer > 0) — the wire's dirty-entry send rides the
            // state-change listener exactly like the furnace's lit byte).
            if (vehicle.onFire() != wasOnFire) {
                for (Listener listener : listeners) {
                    listener.onVehicleStateChanged(vehicle);
                }
            }
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

    /**
     * Wave VC — THE VEHICLE FAMILY'S OWN COLLISION WALK (the re-pin's
     * named row, the reference's per-family tick arms read 1:1):
     * MinecartEntity.tick lines 295-299 — the cart gathers the entities
     * in {@code this.getShape().grown(0.2F, 0.0, 0.2F)} and calls
     * {@code entity.push(this)} on every {@code entity != this.rider}
     * that isPushable and {@code instanceof MinecartEntity} — CARTS
     * ALONE; BoatEntity.tick lines 347-356 — the same gather over the
     * boat's own grown shape with {@code instanceof BoatEntity} — BOATS
     * ALONE. The living bodies NEVER join these walks: in the reference
     * the vehicles never push the living gather's members from their
     * own tick (the living bodies push the vehicles through THEIR walk —
     * the PX wave's own direction; the AUTO-MOUNT consult aside, held
     * NOT-REACHABLE below).
     *
     * <p>The gather box: the body's own reference shape — the cart's
     * {@code setSize(0.98F, 0.7F)} (line 59), the boat's
     * {@code setSize(1.5F, 0.6F)} (BoatEntity line 37) — grown
     * (0.2, 0.0, 0.2) horizontally, NEVER vertically (the grown-0.0
     * vertical band: two carts on the same rail line always overlap;
     * the vertically stacked corner — a cart falling past another —
     * rides the engine's centered-box anchor, the documented
     * adaptation), the candidate's own box against it STRICT both sides
     * (Box.intersects — exactly touching never intersecting, the PX
     * gather's own form). Same dimension alone (each vanilla World is
     * one dimension — the gather never crosses). The own-rider
     * exclusion ({@code entity != this.rider}) carried at the gather:
     * the walker's own passenger never joins (vacuous for the family
     * pair — a passenger is a living body — but the reference's own
     * gate, kept verbatim). isPushable answers true for both families
     * (MinecartEntity line 107 / BoatEntity line 63) — the consult is a
     * formality, documented.</p>
     *
     * <p>Each cart's walk invokes the OTHER cart's push with itself the
     * parameter ({@code entity.push(this)} — the callee/pusher split
     * the blend's own laws read); the pair interacts TWICE per tick
     * (the walker's walk and the candidate's own walk — the vanilla
     * doubling, the same shape the PX seats pinned for the living
     * pair).</p>
     */
    void tickVehicleCollisions(VehicleEntity vehicle) {
        boolean cart = vehicle instanceof MinecartEntity;
        // The reference's own shapes: the cart 0.98x0.7, the boat
        // 1.5x0.6 — the half-widths and heights the gather carries.
        double halfWidth = cart ? 0.49 : 0.75;
        double height = cart ? 0.7 : 0.6;
        Position at = vehicle.position();
        double minX = at.x() - halfWidth - 0.2;
        double maxX = at.x() + halfWidth + 0.2;
        double minY = at.y();
        double maxY = at.y() + height;
        double minZ = at.z() - halfWidth - 0.2;
        double maxZ = at.z() + halfWidth + 0.2;
        for (VehicleEntity other : vehicles) {
            if (other == vehicle || other.dead()) {
                continue; // never self; a removed body never gathers
            }
            if (other.dimension() != vehicle.dimension()) {
                continue; // this world's bodies alone
            }
            if ((other instanceof MinecartEntity) != cart) {
                continue; // the family membership gate (the instanceof consult)
            }
            if (other.entityId() == vehicle.passengerId()) {
                continue; // the walker's own rider (line 296's own gate)
            }
            // The candidate's own box against the grown box, STRICT both
            // sides (Box.intersects).
            double oHalf = cart ? 0.49 : 0.75;
            double oHeight = cart ? 0.7 : 0.6;
            Position b = other.position();
            if (!(b.x() + oHalf > minX && b.x() - oHalf < maxX
                    && b.y() + oHeight > minY && b.y() < maxY
                    && b.z() + oHalf > minZ && b.z() - oHalf < maxZ)) {
                continue;
            }
            if (cart) {
                pushCartPair((MinecartEntity) other, (MinecartEntity) vehicle);
            } else {
                pushBoatPair((BoatEntity) other, (BoatEntity) vehicle);
            }
        }
    }

    /**
     * Wave VC — THE CART-CART BLEND (MinecartEntity.push lines 652-734,
     * the callee = the pushed cart {@code this}, the initiator = the
     * walking cart {@code entity} — the {@code entity.push(this)} call
     * shape): the reference's WHOLESALE override, the arm the PX wave
     * left named. Each law read from the source:
     *
     * <p>(1) THE COMMON AXIS (lines 667-686): {@code d0/d1} the
     * initiator-minus-callee horizontal offset, the SQUARED-distance
     * form with the 1.0E-4 floor (never the absMax pair the living law
     * rides), the euclidean re-normalization, the 1/d2 cap at 1.0, the
     * 0.1 scale, the pushSpeedReduction multiplier (0.0 for every body
     * in 1.8.8 — the field no family sets, the no-op kept out, the PX
     * wave's own form), then the 0.5 halving.</p>
     *
     * <p>(2) THE 0.8 DOT-PRODUCT GATE (lines 687-698): the horizontal
     * offset from callee to initiator, normalized, must sit within
     * ~37 degrees of the CALLEE'S OWN YAW AXIS — {@code |dot| >= 0.8}
     * passes, anything less RETURNS WHOLE (the side-by-side pair on
     * parallel tracks never touches, whatever the box overlap). The
     * reference's yaw is the atan2 math angle of the reversed travel
     * ({@code fastAtan2(-dz, -dx)}, line 281 — east 180, south -90);
     * the engine's cart yaw is the look convention (east -90) — the two
     * differ by exactly 270 degrees, and the reference's axis form
     * {@code (cos(yaw), sin(yaw))} over its convention is the NEGATION
     * of the engine's look axis {@code (-sin(yaw), cos(yaw))} over
     * ours, so under the absolute value the two forms give the IDENTICAL
     * verdict — the engine's look axis carried here, the equivalence
     * proven, never a re-derivation.</p>
     *
     * <p>(3) THE FURNACE ASYMMETRY (lines 700-715): a FURNACE cart
     * never blends — it RAMS. The furnace initiator hands its whole
     * velocity to the damped callee (0.2) and keeps 0.95 of its own;
     * the furnace callee hands its whole velocity to the damped
     * initiator and keeps 0.95. The furnace cart is the coal-fed pusher
     * the vanilla rail yards ride: it shunts the dead carts along,
     * barely slowing.</p>
     *
     * <p>(4) THE MOMENTUM BLEND (lines 716-725): same-furnace-ness
     * pairs (both or neither) — the momentum sum halved, both bodies
     * damped to 0.2, the callee handed the average MINUS the separation
     * impulse, the initiator the average PLUS it: the trailing cart
     * pays, the leading cart gains, the pair converging on the shared
     * momentum while it separates.</p>
     *
     * <p>The AUTO-MOUNT consult (lines 657-665) sits at the head of the
     * reference's override — a moving rideable empty cart scooping a
     * non-player non-golem living body onto the seat — and stays HELD
     * NOT-REACHABLE: the engine's mobs never ride (the seated-mob model
     * the makesSteps gate's own javadoc names as absent — the
     * mob-rides-vehicle body state, the seat-follow's AI freeze, the
     * dismount-on-break), the re-pin's own named tail. NO rider gates
     * on the blend itself (the reference writes both bodies' velocities
     * outright — an occupied cart still blends, the vanilla quirk).</p>
     */
    private void pushCartPair(MinecartEntity callee, MinecartEntity initiator) {
        // The common axis (lines 667-686).
        double d0 = initiator.position().x() - callee.position().x();
        double d1 = initiator.position().z() - callee.position().z();
        double d2 = d0 * d0 + d1 * d1;
        if (d2 < 1.0E-4) {
            return; // the squared-distance floor (line 670's >= 1.0E-4F gate)
        }
        d2 = Math.sqrt(d2);
        d0 /= d2;
        d1 /= d2;
        double d3 = 1.0 / d2;
        if (d3 > 1.0) {
            d3 = 1.0;
        }
        d0 *= d3 * 0.1 * 0.5;
        d1 *= d3 * 0.1 * 0.5;
        // The 0.8 dot gate (lines 687-698) — the callee's own yaw axis
        // (the engine's look form; the equivalence proven in the
        // javadoc above).
        double ox = initiator.position().x() - callee.position().x();
        double oz = initiator.position().z() - callee.position().z();
        double oLen = Math.sqrt(ox * ox + oz * oz);
        if (oLen > 0.0) {
            ox /= oLen;
            oz /= oLen;
        }
        double yawRad = Math.toRadians(callee.rotation().yaw());
        double ax = -Math.sin(yawRad);
        double az = Math.cos(yawRad);
        if (Math.abs(ox * ax + oz * az) < 0.8) {
            return; // the crosswise pair never touches (parallel tracks)
        }
        // The velocity reads BEFORE any write (the reference's arms read
        // the other body's velocity un-scaled — the order the whole
        // blend's arithmetic rides).
        double calleeVx = callee.velocityX();
        double calleeVz = callee.velocityZ();
        double initVx = initiator.velocityX();
        double initVz = initiator.velocityZ();
        boolean initFurnace = initiator instanceof FurnaceMinecartEntity;
        boolean calleeFurnace = callee instanceof FurnaceMinecartEntity;
        if (initFurnace && !calleeFurnace) {
            // Lines 702-708: the FURNACE INITIATOR rams — the callee
            // damped to 0.2 then handed the initiator's WHOLE velocity
            // minus the separation, the initiator keeping 0.95.
            callee.setVelocity(calleeVx * 0.2 + (initVx - d0),
                    callee.velocityY(), calleeVz * 0.2 + (initVz - d1));
            initiator.setVelocity(initVx * 0.95, initiator.velocityY(),
                    initVz * 0.95);
        } else if (calleeFurnace && !initFurnace) {
            // Lines 709-715: the FURNACE CALLEE deflects — the initiator
            // damped to 0.2 then handed the callee's WHOLE velocity plus
            // the separation, the callee keeping 0.95.
            initiator.setVelocity(initVx * 0.2 + (calleeVx + d0),
                    initiator.velocityY(), initVz * 0.2 + (calleeVz + d1));
            callee.setVelocity(calleeVx * 0.95, callee.velocityY(),
                    calleeVz * 0.95);
        } else {
            // Lines 716-725: THE MOMENTUM BLEND — the sum halved, both
            // damped to 0.2, the callee minus the separation, the
            // initiator plus it.
            double avgVx = (initVx + calleeVx) / 2.0;
            double avgVz = (initVz + calleeVz) / 2.0;
            callee.setVelocity(calleeVx * 0.2 + (avgVx - d0),
                    callee.velocityY(), calleeVz * 0.2 + (avgVz - d1));
            initiator.setVelocity(initVx * 0.2 + (avgVx + d0),
                    initiator.velocityY(), initVz * 0.2 + (avgVz + d1));
        }
    }

    /**
     * Wave VC — THE BOAT-BOAT PUSH (BoatEntity.tick lines 347-356 →
     * {@code entity.push(this)} → Entity.push lines 969-1005, BoatEntity
     * overriding nothing): the ordinary mutual law the living family
     * rides — the ABSOLUTE-MAX form (never the cart's squared pair),
     * the 0.01 floor, the sqrt re-normalization over the absMax (the
     * vector NOT unit after it — the vanilla quirk: a pure-axis 0.2
     * offset normalizes to sqrt(0.2), giving the sqrt(0.2)*0.05
     * impulse), the 1/d2 cap at 1.0, the 0.05 scale — and EACH SIDE'S
     * OWN RIDER GATE (lines 995-1001): a boat with a passenger never
     * moves out from under it, however crowded (the line-995 gate the
     * PX wave pinned from the living side; here the boats' own walk).
     * The riding-pair gate (line 971) is vacuous for a boat pair (a
     * boat's rider is never a boat); the noClip gates (line 972) never
     * arm for the engine's vehicles.
     */
    private void pushBoatPair(BoatEntity callee, BoatEntity initiator) {
        double d0 = initiator.position().x() - callee.position().x();
        double d1 = initiator.position().z() - callee.position().z();
        double d2 = Math.abs(d0) > Math.abs(d1) ? Math.abs(d0) : Math.abs(d1);
        if (d2 < 0.01) {
            return; // the absMax floor (line 975)
        }
        d2 = Math.sqrt(d2);
        d0 /= d2;
        d1 /= d2;
        double d3 = 1.0 / d2;
        if (d3 > 1.0) {
            d3 = 1.0;
        }
        d0 *= d3 * 0.05;
        d1 *= d3 * 0.05;
        // Each side's own rider gate: a boat with a passenger never
        // moves (lines 995-1001).
        if (!callee.hasPassenger()) {
            callee.setVelocity(callee.velocityX() - d0, callee.velocityY(),
                    callee.velocityZ() - d1);
        }
        if (!initiator.hasPassenger()) {
            initiator.setVelocity(initiator.velocityX() + d0,
                    initiator.velocityY(), initiator.velocityZ() + d1);
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
        hurt(vehicle, amount, VehicleEntity.DamageSource.MELEE);
    }

    /**
     * One hit with its source (Slice 74 — the reference's
     * {@code damage(DamageSource, float)} signature): the source lands on
     * the body and the death law reads it (the furnace cart's furnace
     * block and the TNT cart's TNT block both skip an explosive death;
     * the TNT cart's fire/explosive/fast death detonates).
     */
    public void hurt(VehicleEntity vehicle, float amount, VehicleEntity.DamageSource source) {
        float before = vehicle.health();
        vehicle.hurt(amount, source);
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
