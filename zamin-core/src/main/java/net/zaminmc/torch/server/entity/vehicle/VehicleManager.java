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
            () -> (x, y, z, power, primed) -> { };

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
            }
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
