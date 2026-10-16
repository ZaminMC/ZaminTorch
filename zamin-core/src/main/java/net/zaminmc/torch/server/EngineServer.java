package net.zaminmc.torch.server;

import net.zaminmc.torch.util.Identifier;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BlockUpdateSystem;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.chest.ChestDataStore;
import net.zaminmc.torch.server.chest.ChestManager;
import net.zaminmc.torch.server.container.ContainerViews;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.FallingBlockEntity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.MobDataStore;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.entity.TradeOffer;
import net.zaminmc.torch.server.entity.damage.DamageKind;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.enchantment.EnchantmentHelper;
import net.zaminmc.torch.server.experience.ExperienceAwards;
import net.zaminmc.torch.server.experience.ExperienceMath;
import net.zaminmc.torch.server.experience.ExperienceOrbEntity;
import net.zaminmc.torch.server.experience.ExperienceOrbManager;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceDataStore;
import net.zaminmc.torch.server.furnace.FurnaceManager;
import net.zaminmc.torch.server.fx.BlockSoundMap;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.Foods;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.player.WindowClicks;
import net.zaminmc.torch.server.chat.CommandService;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.chat.ConsoleSender;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.ops.BanStore;
import net.zaminmc.torch.server.ops.IpBanStore;
import net.zaminmc.torch.server.ops.OpStore;
import net.zaminmc.torch.server.ops.WhitelistStore;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.sign.SignDataStore;
import net.zaminmc.torch.server.sign.SignManager;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.MovementGuard;
import net.zaminmc.torch.server.player.PlayerDataStore;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.player.PlayerSnapshot;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.entity.vehicle.BoatEntity;
import net.zaminmc.torch.server.entity.vehicle.MinecartEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleEntity;
import net.zaminmc.torch.server.entity.vehicle.VehicleManager;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.DeltaWorldStorage;
import net.zaminmc.torch.server.world.NormalWorldGenerator;
import net.zaminmc.torch.server.world.NetherWorldGenerator;
import net.zaminmc.torch.server.world.TheEndWorldGenerator;
import net.zaminmc.torch.server.world.PortalForcer;
import net.zaminmc.torch.server.world.PortalFrameBuilder;
import net.zaminmc.torch.server.world.WorldGenerator;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.WorldStorage;
import net.zaminmc.torch.server.world.WorldDeltaSnapshot;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.CollisionShapes;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.ExplosionService;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.FluidSystem;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.block.WoodShapeBlocks;
import net.zaminmc.torch.server.block.WoodVariantBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.chat.ChatListener;
import net.zaminmc.torch.server.chat.ChatService;
import net.zaminmc.torch.server.chat.CommandService;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.interaction.BlockInteractionService;
import net.zaminmc.torch.server.world.WorldChangeListener;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The engine server: owns the lifecycle state machine, the world, the player
 * registry and the bridge through which protocol adapters connect clients.
 *
 * <p>Ownership model (Slice #1): world state is owned by the tick thread;
 * identity-critical session operations (join/reject/disconnect) run on the
 * caller's network event loop and are guarded by the registry's atomicity plus
 * per-channel packet ordering guarantees of the transport. Player position is
 * per-player state written by the owning channel loop only, published volatile.
 * A general command queue arrives with cross-entity gameplay.</p>
 */
public final class EngineServer implements Server, EngineBridge {

    private static final Logger LOGGER = Logger.getLogger(EngineServer.class.getName());
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final long BOOT_TIMEOUT_MS = 30_000;

    /** Player-window wire slots for the crafting area (window 0, protocol 47). */
    private static final int WIRE_SLOT_RESULT = 0;
    private static final int WIRE_SLOT_CRAFT_FIRST = 1;
    private static final int WIRE_SLOT_CRAFT_LAST = 4;
    /** Player-window armor row (wire order: head, chest, legs, feet). */
    private static final int WIRE_SLOT_ARMOR_FIRST = 5;
    private static final int WIRE_SLOT_ARMOR_LAST = 8;
    /** Crafting-table container window wire slots (protocol 47, 10-slot GUI). */
    private static final int TABLE_WIRE_SLOT_RESULT = 0;
    private static final int TABLE_WIRE_SLOT_GRID_FIRST = 1;
    private static final int TABLE_WIRE_SLOT_GRID_LAST = 9;
    private static final int TABLE_WIRE_SLOT_HOTBAR_FIRST = 37;
    private static final int TABLE_WIRE_SLOT_HOTBAR_LAST = 45;
    /** Furnace container window wire slots (protocol 47, community-verified GUI). */
    private static final int FURNACE_WIRE_SLOT_LAST = 2; // 0 input, 1 fuel, 2 output
    private static final int FURNACE_WIRE_SLOT_MAIN_FIRST = 3;
    private static final int FURNACE_WIRE_SLOT_MAIN_LAST = 29;
    private static final int FURNACE_WIRE_SLOT_HOTBAR_FIRST = 30;
    private static final int FURNACE_WIRE_SLOT_HOTBAR_LAST = 38;
    /** Chest container window wire slots (protocol 47, community-verified GUI). */
    private static final int CHEST_WIRE_SLOT_LAST = 26; // 3x9 chest slots
    private static final int CHEST_WIRE_SLOT_MAIN_FIRST = 27;
    private static final int CHEST_WIRE_SLOT_MAIN_LAST = 53;
    private static final int CHEST_WIRE_SLOT_HOTBAR_FIRST = 54;
    private static final int CHEST_WIRE_SLOT_HOTBAR_LAST = 62;

    /** The hopper window's wire layout (the reference's HopperMenu): 5
     *  container slots, then the main inventory 5-31, then the hotbar 32-40. */
    private static final int HOPPER_WIRE_SLOT_LAST = 4;
    private static final int HOPPER_WIRE_SLOT_MAIN_FIRST = 5;
    private static final int HOPPER_WIRE_SLOT_MAIN_LAST = 31;
    private static final int HOPPER_WIRE_SLOT_HOTBAR_FIRST = 32;
    private static final int HOPPER_WIRE_SLOT_HOTBAR_LAST = 40;
    // The dispenser/dropper window (Slice 11a — DispenserMenu): 3x3
    // container slots then the same inventory layout the chest uses.
    private static final int DISPENSER_WIRE_SLOT_LAST = 8;
    private static final int DISPENSER_WIRE_SLOT_MAIN_FIRST = 9;
    private static final int DISPENSER_WIRE_SLOT_MAIN_LAST = 35;
    private static final int DISPENSER_WIRE_SLOT_HOTBAR_FIRST = 36;
    private static final int DISPENSER_WIRE_SLOT_HOTBAR_LAST = 44;
    /** Craft-all guard: even a 3x3 grid cannot chain more crafts than this. */
    private static final int VANILLA_CRAFT_ALL_BOUND = 1024;
    /** Wire window ids: 0 = player inventory; containers get ids from this counter (u8). */
    private static final int WIRE_WINDOW_PLAYER = 0;
    private static final int FIRST_CONTAINER_WINDOW_ID = 1;
    private static final int LAST_CONTAINER_WINDOW_ID = 100;

    /** Tick-thread confined; ids are handed out on the simulation context only. */
    private int nextContainerWindowId = FIRST_CONTAINER_WINDOW_ID;

    private final EngineConfig config;
    private final PlayerRegistry players = new PlayerRegistry();
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.NEW);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    /**
     * The public event bus (§27.7): the plugin-facing observe/cancel surface.
     * Created eagerly — available to every subsystem's wiring pass regardless
     * of construction order, and returned by {@link #events()}.
     */
    private final net.zaminmc.torch.server.event.EngineEventBus eventBus =
            new net.zaminmc.torch.server.event.EngineEventBus();

    /** The outbound packet hooks (§27.3) — the registry the protocol adapter's walk consults. */
    private final net.zaminmc.torch.server.net.PacketHookRegistry packetHooks =
            new net.zaminmc.torch.server.net.PacketHookRegistry();

    private FrozenBlockRegistry blockRegistry;
    private EngineWorld world;
    /**
     * The second dimension (8b-ii): vanilla's DIM-1 — the nether world, its
     * own chunk map, its own delta persistence directory, its own ownership
     * domain. Booted alongside the overworld on the tick thread.
     */
    private EngineWorld netherWorld;
    private EngineTicker ticker;
    /**
     * The simulation ownership domain (the permanent architecture's Phase 1
     * primitive): the single-writer authority the world's mutations assert
     * through and the generation source the compute tickets validate
     * against. Bound to the boot/tick thread for the loop's whole run.
     */
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain simulationDomain;
    /**
     * The nether dimension's ownership domain (8b-ii, "simulation:nether"):
     * the second EngineWorld's mutations assert through it. The boot binds
     * it alongside the simulation domain — the loop's single-writer walk
     * covers both worlds until the region activation splits them.
     */
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain netherDomain;
    /**
     * The End dimension's ownership domain ("simulation:end", Slice 61):
     * the third EngineWorld's mutations assert through it. The boot binds
     * it alongside the simulation + nether domains — the loop's
     * single-writer walk covers all three worlds until the region
     * activation splits them. (Slice 64: the End world was created with
     * this domain but the loop never entered it — every tick-lane End
     * write (the arrival platform, the crystal fire, the exit fountain)
     * threw OwnershipViolationException on a real booted server; the
     * unit-harness slices never noticed.)
     */
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain endDomain;
    /** The Phase 2 scheduler: the tick's deferred-work substrate. */
    private volatile net.zaminmc.torch.server.concurrent.SimulationScheduler simulationScheduler;
    /** The Phase 3 compute lane: bounded workers over immutable snapshots. */
    private volatile net.zaminmc.torch.server.concurrent.ComputeSubsystem computeSubsystem;
    /** The §28.1 fourth-category blocking-I/O lane: the storage lane's own bounded pool. */
    private volatile net.zaminmc.torch.server.concurrent.BlockingIoLane blockingIoLane;
    /** The Phase 4 domain worker pool: the independent domains' shared execution resources. */
    private volatile net.zaminmc.torch.server.concurrent.DomainWorkerPool domainWorkerPool;
    /** The Phase 4 cross-owner router: the §7 intent protocol's live surface. */
    private volatile net.zaminmc.torch.server.concurrent.CrossOwnerRouter crossOwnerRouter;
    /** The overworld's spatial ownership partition (the Phase 4 activation). */
    private volatile net.zaminmc.torch.server.concurrent.RegionPartition worldRegionPartition;
    /** The nether's spatial ownership partition (the same granularity). */
    private volatile net.zaminmc.torch.server.concurrent.RegionPartition netherRegionPartition;
    /** The in-flight detached chunk generations (dedupe; the never-overwrite guard). */
    private final java.util.Set<Long> pendingChunkGenerations =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** The callbacks waiting for each in-flight chunk generation. */
    private final java.util.Map<Long, java.util.Queue<java.util.function.Consumer<EngineChunk>>>
            pendingChunkLoads = new java.util.concurrent.ConcurrentHashMap<>();
    private BlockInteractionService blockInteraction;
    private ChatService chatService;
    private final CraftingService crafting = CraftingService.builtin();
    private WorldStorage worldStorage;
    /** The nether dimension's delta persistence (DIM-1/zamin-delta.bin). */
    private WorldStorage netherStorage;
    /** The End dimension (Slice 61): vanilla's DIM1 — the island world. */
    private EngineWorld endWorld;
    /** The End dimension's delta persistence (DIM1/zamin-delta.bin). */
    private WorldStorage endStorage;
    /** The destination-side portal walks (the reference's per-ServerWorld PortalForcer). */
    private PortalForcer worldPortalForcer;
    private PortalForcer netherPortalForcer;
    private PlayerDataStore playerStore;
    private FurnaceManager furnaceManager;
    private FurnaceDataStore furnaceStore;

    /** The dispenser/dropper state store (ZDD v1, Slice 11f). */
    private net.zaminmc.torch.server.dispenser.DispenserDataStore dispenserStore;

    /** The brewing stand state store (ZBD v1, Slice 11f). */
    private net.zaminmc.torch.server.brewing.BrewingStandDataStore brewingStore;
    private ChestManager chestManager;
    private ChestDataStore chestStore;
    private net.zaminmc.torch.server.hopper.HopperDataStore hopperStore;
    /** The beacon system (Slice 29b — the pyramid + the effect spread). */
    private net.zaminmc.torch.server.beacon.BeaconSystem beaconSystem;
    private net.zaminmc.torch.server.beacon.BeaconDataStore beaconStore;
    /** The mob spawner state (Slice 12g — the structure spawners). */
    private net.zaminmc.torch.server.spawner.SpawnerManager spawnerManager;
    private net.zaminmc.torch.server.spawner.SpawnerManager spawners;
    private SignManager signManager;
    private SignDataStore signStore;
    private volatile ItemEntityManager itemEntities;
    private volatile MobManager mobManager;
    private volatile FallingBlockEntityManager fallingEntities;
    /** The command dispatcher (tab completion reads it from the wire layer). */
    private volatile CommandService commands;
    private BlockUpdateSystem blockUpdateSystem;
    /** The redstone signal engine (Slice 9a): the wire cascade + the family dispatch + the delayed reactions. */
    private net.zaminmc.torch.server.redstone.RedstoneSystem redstoneSystem;

    /** The piston subsystem (Slice 9e): the block events + the moving carriers. */
    private net.zaminmc.torch.server.piston.PistonSystem pistonSystem;

    /** The TNT subsystem (Slice 9f): the ignition arms + the primed bodies. */
    private net.zaminmc.torch.server.tnt.TntSystem tntSystem;
    /** The note block system (Slice 27a): the note tiles + the three play arms. */
    private net.zaminmc.torch.server.noteblock.NoteBlockSystem noteBlocks;

    private net.zaminmc.torch.server.jukebox.JukeboxBlockSystem jukeboxes;

    private net.zaminmc.torch.server.jukebox.JukeboxDataStore jukeboxStore;

    private net.zaminmc.torch.server.daylight.DaylightDetectorSystem daylightDetectors;
    /** The decoration family (Slice 28a/28b): the item frames + the paintings. */
    private net.zaminmc.torch.server.decoration.DecorationSystem decorations;
    /** The decorations' ZED persistence. */
    private net.zaminmc.torch.server.decoration.DecorationDataStore decorationStore;
    /** The armor stands (Slice 28c). */
    private net.zaminmc.torch.server.armorstand.ArmorStandSystem armorStands;
    /** The armor stands' ZAD persistence. */
    private net.zaminmc.torch.server.armorstand.ArmorStandDataStore armorStandStore;
    /** The ender crystals (Slice 62 — the End pillar tops). */
    private net.zaminmc.torch.server.endcrystal.EnderCrystalSystem enderCrystals;
    /** The firework rockets (Slice 65 — the right-click launch + the burst). */
    private net.zaminmc.torch.server.fireworks.FireworksSystem fireworks;
    /** The ender crystals' ZEC persistence (the destroyed-pillar set). */
    private net.zaminmc.torch.server.endcrystal.EnderCrystalDataStore enderCrystalStore;
    /** The dragon fight's ZDR persistence (the defeated mark). */
    private net.zaminmc.torch.server.endcrystal.DragonDataStore dragonStore;
    /** Whether the End's dragon was already defeated (the no-respawn mark). */
    private volatile boolean dragonDefeated;
    /** The End world's own blast walk (the crystal's 6.0F death — dimension 1). */
    private ExplosionService endExplosionService;
    /** The note tiles' ZND persistence. */
    private net.zaminmc.torch.server.noteblock.NoteDataStore noteStore;
    /** The hopper subsystem (Slice 10b): the 8-tick transfer cadence. */
    private net.zaminmc.torch.server.hopper.HopperSystem hopperSystem;
    /** The dispenser subsystem (Slice 11a): the 4-tick reaction + the behaviors. */
    private net.zaminmc.torch.server.dispenser.DispenserSystem dispenserSystem;

    /** The potion subsystem (Slice 11b): the effect stores + the splash. */
    private net.zaminmc.torch.server.potion.PotionSystem potionSystem;
    /** The leash subsystem (Slice 11c): the knots + the leash physics. */
    private net.zaminmc.torch.server.leash.LeadSystem leadSystem;
    /** The natural spawn cycle (Slice 11d): the 1:1 NaturalSpawner port. */
    private net.zaminmc.torch.server.spawn.NaturalSpawnSystem naturalSpawns;
    /** The nether's own cycle (Slice 16d — the HellBiome rows). */
    private net.zaminmc.torch.server.spawn.NaturalSpawnSystem naturalSpawnsNether;
    /** The brewing stands (Slice 11e): the 1:1 brew-cycle port. */
    private net.zaminmc.torch.server.brewing.BrewingStandSystem brewingStands;
    /** The tripwire subsystem (Slice 10c): the occupancy scan + the hook walk. */
    private net.zaminmc.torch.server.redstone.TripwireSystem tripwireSystem;
    /** The observers of the TNT system (the protocol adapter's wire sync). */
    private final java.util.List<net.zaminmc.torch.server.tnt.TntSystem.Listener> tntListeners =
            new java.util.ArrayList<>();
    private FluidSystem fluidSystem;
    private ExplosionService explosionService;
    private RandomTickSystem randomTicks;
    private ExperienceOrbManager experienceOrbs;
    private net.zaminmc.torch.server.world.light.LightEngine lightEngine;
    private MobDataStore mobStore;
    /** The vehicles' persistence store (Slice 25g — the ZVD format). */
    private net.zaminmc.torch.server.entity.vehicle.VehicleDataStore vehicleStore;
    /** The operator registry (ops.json); loaded at boot, rewritten on /op and /deop. */
    private OpStore opStore = OpStore.load(java.nio.file.Path.of("ops.json"));
    /** The ban registry (banned-players.json) and the whitelist roster. */
    private BanStore banStore = BanStore.load(java.nio.file.Path.of("banned-players.json"));
    private WhitelistStore whitelistStore =
            WhitelistStore.load(java.nio.file.Path.of("whitelist.json"));
    /** The IP ban registry (banned-ips.json), the /ban-ip gate's store. */
    private IpBanStore ipBanStore = IpBanStore.load(java.nio.file.Path.of("banned-ips.json"));
    /** The runtime difficulty id (0 peaceful .. 3 hard; /difficulty drives it). */
    private volatile int difficulty = 1;
    /** The default game mode id new joins start in (world/data/defaultgamemode.json). */
    private volatile int defaultGamemodeId = 0;
    /** The generator's deterministic seed (the /seed report). */
    private volatile long worldSeed;
    /** Runtime whitelist enforcement (server.properties boot value; /whitelist on|off). */
    private volatile boolean whitelistEnforced;
    /** The weather state (Slice 20b — the 1.8.8 cycle; the pre-20b boolean
     * model replaced). */
    private volatile net.zaminmc.torch.server.weather.WeatherSystem weather;
    private net.zaminmc.torch.server.weather.WeatherDataStore weatherStore;
    /**
     * The game-rule table (Slice 24a — the 1.8.8 GameRules port): one
     * shared instance per server — every dimension's derived data
     * delegates to the overworld's table (the reference's own
     * ReadOnlyWorldData.getGameRules).
     */
    private volatile net.zaminmc.torch.server.gamerule.GameRules gameRules;
    private net.zaminmc.torch.server.gamerule.GameRuleDataStore gameRuleStore;
    /** The lightning family (Slice 20c — the strike roll + the bolt life). */
    private volatile net.zaminmc.torch.server.weather.LightningSystem lightning;
    /** The ice/snow precipitation arm (Slice 21c — tickChunks lines 325-341). */
    private volatile net.zaminmc.torch.server.weather.PrecipitationSystem precipitation;
    private final java.util.List<ChatListener> chatListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ItemEntityManager.Listener> itemListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<MobManager.Listener> mobListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<MobSwingObserver> swingObservers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ExplosionListener> explosionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<FallingBlockEntityManager.Listener> fallingListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<InventoryListener> inventoryListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<FurnaceViewListener> furnaceViewListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<SurvivalListener> survivalListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<TimeListener> timeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<SignListener> signListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<RelightListener> relightListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ExperienceListener> experienceListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ExperienceOrbManager.Listener> orbListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Entity ids for engine-global entities (items); player wire ids stay adapter-local. */
    private static final int ENTITY_ID_BASE = 100_000;
    /** Mob ids live in a disjoint band above the item ids (one id space, no collision). */
    private static final int MOB_ID_BASE = ENTITY_ID_BASE + 1_000_000;
    /** Falling blocks get their own band above the mobs. */
    private static final int FALLING_ID_BASE = MOB_ID_BASE + 1_000_000;
    /** Projectiles (arrows, shards) get the next band. */
    private static final int PROJECTILE_ID_BASE = FALLING_ID_BASE + 1_000_000;
    /** Player engine-global ids live above the projectiles (engine-side identity). */
    /** The band the adapter recognizes as player ids (public: wire translation). */
    public static final int PLAYER_ID_BASE = PROJECTILE_ID_BASE + 1_000_000;
    /** Experience orbs get the next band above the players. */
    private static final int ORB_ID_BASE = PLAYER_ID_BASE + 1_000_000;
    /** Vehicles (boats, minecarts) get the band above the orbs. */
    private static final int VEHICLE_ID_BASE = ORB_ID_BASE + 1_000_000;
    private static final int TNT_ID_BASE = VEHICLE_ID_BASE + 1_000_000;
    /** Leash knots get the band above the TNT bodies. */
    private static final int KNOT_ID_BASE = TNT_ID_BASE + 1_000_000;
    /** Lightning bolts get the band above the leash knots. */
    private static final int LIGHTNING_ID_BASE = KNOT_ID_BASE + 1_000_000;
    /** Decorations (item frames, paintings) get the band above the lightning. */
    private static final int DECORATION_ID_BASE = LIGHTNING_ID_BASE + 1_000_000;
    /** Armor stands get the band above the decorations. */
    private static final int ARMOR_STAND_ID_BASE = DECORATION_ID_BASE + 1_000_000;
    private static final int ENDER_CRYSTAL_ID_BASE = ARMOR_STAND_ID_BASE + 1_000_000;
    /** Firework rockets get the band above the ender crystals (Slice 65). */
    private static final int FIREWORKS_ID_BASE = ENDER_CRYSTAL_ID_BASE + 1_000_000;
    /** The fishing bobbers' own id band (Slice 67). */
    private static final int FISHING_ID_BASE = FIREWORKS_ID_BASE + 1_000_000;

    /** The shared shape-aware physics query (items, falling blocks, projectiles). */
    private final ShapeGround shapeGround = new ShapeGround();

    /** The game-feedback bus (sounds, particles); created at boot, read-only after. */
    private final FxManager fxManager = new FxManager();
    /** The engine's gameplay rolls (tick-thread confined; drops, spawns, floods). */
    private final java.util.Random gameplayRandom = new java.util.Random();
    /** The airborne projectiles; constructed at boot after the world exists. */
    private volatile ProjectileManager projectileManager;
    /** The engine-side break walk's drop table (the wither's sweep). */
    private volatile DropService witherSweepDrops;
    /** The skull store (Slice 60 — the tiles + the wither's summon walk). */
    private volatile net.zaminmc.torch.server.skull.SkullManager skulls;
    /** The vehicles (boats, minecarts); created at boot, tick-thread owned. */
    private volatile net.zaminmc.torch.server.entity.vehicle.VehicleManager vehicleManager;
    /** The fishing bobbers (Slice 67); created at boot, tick-thread owned. */
    private volatile net.zaminmc.torch.server.fishing.FishingSystem fishing;

    /** The FX pitch/jitter source (cosmetic rolls only, never gameplay rules). */
    private final java.util.Random fxRandom = new java.util.Random();
    /** The next engine-global player entity id (the projectile thrower band). */
    private int nextPlayerEntityId = PLAYER_ID_BASE;

    public EngineServer(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // ------------------------------------------------------------------ lifecycle

    public EngineConfig engineConfig() {
        return config;
    }

    /** The game-feedback bus (sounds, particles) the adapter subscribes to. */
    public FxManager fx() {
        return fxManager;
    }

    /** The airborne projectiles (engine-side simulation state). */
    /** The skull store (Slice 60 — the tiles + the summon walk). */
    public net.zaminmc.torch.server.skull.SkullManager skulls() {
        return skulls;
    }

    public ProjectileManager projectiles() {
        return projectileManager;
    }

    /**
     * Boots the engine. The adapter is started by the launcher after this returns,
     * once the world is guaranteed to exist.
     */
    public void start() throws InterruptedException {
        if (!state.compareAndSet(ServerState.NEW, ServerState.INITIALIZING)) {
            throw new IllegalStateException("Server already started or starting (state " + state.get() + ")");
        }
        LOGGER.info(() -> "ZaminTorch engine starting (world=" + config.worldName()
                + ", port=" + config.port() + ", view-distance=" + config.viewDistance() + ")");

        // Registry freeze: built-ins registered during boot preparation must be frozen
        // before any world exists (registry lifecycle: create -> register -> freeze).
        if (blockRegistry == null) {
            blockRegistry = net.zaminmc.torch.server.cauldron.CauldronBlocks.registerAll(
                    net.zaminmc.torch.server.chest.EnderChestBlocks.registerAll(
                    net.zaminmc.torch.server.beacon.BeaconSystem.registerBlocks(
                    net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                    net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                    net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                    net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                    net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                    net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                    net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.registerAll(
                            FluidBlocks.registerAll(
                                    BuiltinBlocks.registerAll(new BlockRegistryBuilder())))))))))))).freeze();
        }

        state.set(ServerState.STARTING);
        CountDownLatch worldReady = new CountDownLatch(1);
        AtomicReference<Throwable> bootFailure = new AtomicReference<>();

        Thread boot = new Thread(() -> {
            try {
                ticker = new EngineTicker(config.tickRateHz());
                // The Paper stores: ops.json, banned-players.json, whitelist.json
                // at the server root (Paper layout); whitelist enforcement reads
                // the server.properties flag until /whitelist on|off overrides.
                opStore = OpStore.load(java.nio.file.Path.of(config.dataDir(), "ops.json"));
                banStore = BanStore.load(java.nio.file.Path.of(config.dataDir(), "banned-players.json"));
                ipBanStore = IpBanStore.load(java.nio.file.Path.of(config.dataDir(), "banned-ips.json"));
                whitelistStore = WhitelistStore.load(java.nio.file.Path.of(config.dataDir(), "whitelist.json"));
                whitelistEnforced = config.whiteList();
                defaultGamemodeId = loadDefaultGamemode();
                // Player persistence: one ZPD file per identity under world/playerdata.
                playerStore = new PlayerDataStore(
                        config.playerDataDir());
                // Ticker thread constructs the world so it is the owner from the start.
                Thread owner = Thread.currentThread();
                // level-type decides the terrain: flat keeps the slice fixture,
                // normal runs the full world (seas, caves, ores, forests).
                WorldGenerator generator = config.levelType().equals("flat")
                        ? new FlatWorldGenerator(blockRegistry, 4)
                        : new NormalWorldGenerator(blockRegistry, config.worldName());
                world = new EngineWorld(config.worldName(), blockRegistry, generator, owner);
                worldSeed = generator.seed();
                // The spatial ownership partition (the Phase 4 spatial
                // activation — ADR-6's conservative knob): region-size chunks
                // per axis. The default is the whole world one region, so the
                // live engine keeps today's single-domain behavior until the
                // operator configures a real split; the boundary tests ride
                // the small sizes.
                worldRegionPartition = new net.zaminmc.torch.server.concurrent.RegionPartition(
                        config.worldName(), config.regionSizeChunks());
                world.attachRegionPartition(worldRegionPartition);
                // The persisted spawn anchor (/setspawn): world/data/spawn.json
                // rides above the generator's deterministic spawn when present.
                loadSpawnAnchor().ifPresent(world::setSpawnPosition);
                // Persistence: load saved deltas before any chunk generates so the
                // spawn area is already the survived world (§407 restart proof).
                worldStorage = new DeltaWorldStorage(
                        config.worldDataDir().resolve("zamin-delta.bin"),
                        identifier -> blockRegistry.require(identifier));
                worldStorage.load().ifPresent(world::applyDeltas);
                // The nether dimension (8b-ii): the second EngineWorld —
                // vanilla's DIM-1. Same world seed (the reference's shared
                // worldSeed through the hell provider), its own chunk map,
                // its own delta persistence directory, its own ownership
                // domain ("simulation:nether"). No spawn-area pregeneration
                // (the nether's chunks arrive on demand through the portal
                // walk) and no light engine yet (the nether's relight flush
                // publishes to clients in that dimension — it lands with the
                // 8b-iii player walk).
                NetherWorldGenerator netherGenerator =
                        new NetherWorldGenerator(blockRegistry, worldSeed);
                netherWorld = new EngineWorld(config.worldName() + "_nether",
                        blockRegistry, netherGenerator, owner);
                // The nether's own spatial partition (the same granularity —
                // each dimension partitions its own chunk space).
                netherRegionPartition = new net.zaminmc.torch.server.concurrent.RegionPartition(
                        config.worldName() + "_nether", config.regionSizeChunks());
                netherWorld.attachRegionPartition(netherRegionPartition);
                netherStorage = new DeltaWorldStorage(
                        config.worldDataDir().resolve("DIM-1/zamin-delta.bin"),
                        identifier -> blockRegistry.require(identifier));
                netherStorage.load().ifPresent(netherWorld::applyDeltas);
                netherDomain = net.zaminmc.torch.server.concurrent.OwnershipDomain
                        .create("simulation:nether");
                netherWorld.attachDomain(netherDomain);
                // The destination-side portal walks (the reference's
                // ServerWorld.getPortalForcer per world: the seed-shared
                // Random, the dimension's own height).
                worldPortalForcer = new PortalForcer(world, worldSeed, 256);
                netherPortalForcer = new PortalForcer(netherWorld, worldSeed, 128);
                LOGGER.info(() -> "Nether dimension '"
                        + netherWorld.name() + "' ready (seed " + worldSeed + ")");
                // The public block-change observation covers the nether's
                // commits too (the event carries the world's own name).
                netherWorld.addChangeListener(new PublicBlockChangeBridge());
                // The End dimension (Slice 61): the third EngineWorld —
                // vanilla's DIM1. The reference's TheEndDimension: same
                // world seed, its own chunk map, its own delta persistence
                // directory, its own ownership domain ("simulation:end"),
                // the fixed THE_END biome (wire id 9), and no PortalForcer
                // (the reference's End arrival skips the placing walk —
                // PlayerManager.changeDimension's dimension != 1 gate).
                TheEndWorldGenerator endGenerator =
                        new TheEndWorldGenerator(blockRegistry, worldSeed);
                endWorld = new EngineWorld(config.worldName() + "_end",
                        blockRegistry, endGenerator, owner);
                net.zaminmc.torch.server.concurrent.RegionPartition endRegionPartition =
                        new net.zaminmc.torch.server.concurrent.RegionPartition(
                                config.worldName() + "_end", config.regionSizeChunks());
                endWorld.attachRegionPartition(endRegionPartition);
                endStorage = new DeltaWorldStorage(
                        config.worldDataDir().resolve("DIM1/zamin-delta.bin"),
                        identifier -> blockRegistry.require(identifier));
                endStorage.load().ifPresent(endWorld::applyDeltas);
                endDomain = net.zaminmc.torch.server.concurrent.OwnershipDomain
                        .create("simulation:end");
                endWorld.attachDomain(endDomain);
                LOGGER.info(() -> "End dimension '"
                        + endWorld.name() + "' ready (seed " + worldSeed + ")");
                endWorld.addChangeListener(new PublicBlockChangeBridge());
                // Light (§475/§476): derived world state, recomputed on every
                // committed change and on chunk generation. Registered FIRST
                // (before spawn pregeneration) so every generated chunk — boot
                // area included — publishes already lit (§344: publication
                // stays atomic), and as the first change listener so its
                // relight queue batches whole cascades per tick.
                lightEngine = new net.zaminmc.torch.server.world.light.LightEngine(world);
                world.addChangeListener(lightEngine);
                world.addChunkLoadListener(lightEngine);
                // The public §27.7 post-commit observation: every committed
                // block change — player, explosion, piston, fluid, growth —
                // fires BlockChangedEvent through the engine bus (the
                // bridge implements the combined pair).
                world.addChangeListener(new PublicBlockChangeBridge());
                pregenerateSpawnArea(world);
                ticker.attachWorld(world);
                // The public §27.6 owner path: plugins execute on the
                // authoritative lane through World.execute (the ticker's
                // between-ticks submission).
                world.attachOwnerExecutor(ticker::submit);
                // The public §27.5 entity view: the mob population capture.
                world.attachEntityView(() -> mobManager != null ? mobManager.all()
                        : java.util.List.<net.zaminmc.torch.server.entity.MobEntity>of());
                // Item entities and drops: simulation-owned systems on the world owner.
                ItemEntityManager itemEntities = new ItemEntityManager(
                        shapeGround,
                        new java.util.Random(),
                        ENTITY_ID_BASE);
                this.itemEntities = itemEntities;
                itemEntities.setEventBus(eventBus); // §27.7: the public item-spawn gate
                itemEntities.addListener(new ItemEventDispatch());
                // Furnace block entities: world-state simulation, ZFD persistence.
                furnaceManager = new FurnaceManager();
                furnaceStore = new FurnaceDataStore(config.worldDataDir().resolve("furnaces.bin"));
                furnaceManager.restoreAll(furnaceStore.load());
                // Chest block entities: world-state containers, ZCD persistence.
                chestManager = new ChestManager();
                chestStore = new ChestDataStore(config.worldDataDir().resolve("chests.bin"));
                chestManager.restoreAll(chestStore.load());
                // Sign block entities: world text state, ZSD persistence.
                signManager = new SignManager();
                signStore = new SignDataStore(config.worldDataDir().resolve("signs.bin"));
                signManager.restoreAll(signStore.load());
                // Living mobs: simulation-owned population, loot flows into items.
                MobManager mobs = new MobManager(
                        new MobWorldQuery(),
                        new java.util.Random(),
                        (position, stack) -> itemEntities.spawnDropAtBlock(position, stack,
                                ItemEntity.PICKUP_DELAY_DROP_TICKS),
                        MOB_ID_BASE,
                        (x, z) -> surfaceY(x, z));
                this.mobManager = mobs;
                mobs.addListener(new MobEventDispatch());
                mobs.setEventBus(eventBus); // §27.7: the public spawn gate
                // §27.6's admission protocol for the mind: goal registrations
                // (the public goals() surface) land on the world's owner lane,
                // between ticks, from any thread.
                mobs.setGoalAdmission(world::execute);
                // The crafting consult the sheep's offspring color rides
                // (Slice 30b — getColorForOffspring's CraftingManager walk).
                mobs.setCraftingConsult(crafting);
                // Mob persistence (ZMD v1): the population survives restarts;
                // a fresh world's animals arrive through the vanilla worldgen
                // seeding (NaturalSpawner.populateChunk — the seedAnimals pass
                // + the populate drain), replacing the old synthetic spawn
                // packs (Slice 12h).
                mobStore = new MobDataStore(config.worldDataDir().resolve("mobs.bin"));
                boolean mobsRestored = mobs.restoreAll(mobStore.load());
                if (!mobsRestored) {
                    // Fresh world: nothing to restore — the pregenerated
                    // spawn chunks' pending animals drain on the first tick.
                }
                // Mob spawners (Slice 12g): the structure-placed spawner
                // blocks' state + the spawn walk — dimension-aware since
                // 18e (the cell gate + the cleanup read the spawner's own
                // world; the fortress's blaze spawners no longer die on
                // their first tick).
                spawnerManager = new net.zaminmc.torch.server.spawner.SpawnerManager(
                        new java.util.Random(),
                        () -> players.all().stream()
                                .map(net.zaminmc.torch.server.player.PlayerSession::position)
                                .toList(),
                        mobs,
                        dimension -> dimension == -1 && netherWorld != null
                                ? netherWorld : world);
                this.spawners = spawnerManager;
                // Falling blocks (§470): the block→entity→block transition for
                // gravity blocks; occupied landings drop as items.
                FallingBlockEntityManager falling = new FallingBlockEntityManager(
                        shapeGround,
                        world,
                        (position, stack) -> itemEntities.spawnDropAtBlock(
                                new Position(position.x(), position.y(), position.z()), stack,
                                ItemEntity.PICKUP_DELAY_DROP_TICKS),
                        new java.util.Random(),
                        FALLING_ID_BASE);
                this.fallingEntities = falling;
                falling.addListener(new FallingEventDispatch());
                // The anvil's landing walk (Slice 53 — FallingBlockEntity.
                // takeFallDamage 1:1): the hurt over the accumulated fall
                // (ceil(distance - 1), every body in the 0.98 box takes
                // min(floor(i * 2), 40) — the fallHurtAmount 2.0F/maxFallHurt
                // 40 pair), then the progression roll (random.nextFloat() <
                // 0.05F + i * 0.05 walks the DAMAGE field forward — past 2 the
                // anvil breaks, the brokeAnvil path: nothing placed, nothing
                // dropped), and the land sound on the successful placement
                // (the reference's onTickFallingBlockEntity — world event
                // 1022, the client's random.anvil_land).
                falling.setAnvilLanding((entity, landing) -> {
                    double distance = entity.fallDistance();
                    int i = (int) Math.ceil(distance - 1.0F);
                    int damage = net.zaminmc.torch.server.block.AnvilBlocks
                            .damageOf(entity.blockType().identifier());
                    if (i > 0) {
                        // Every body in the 0.98 box at the landing cell:
                        // mobs, players (the playing non-spectators). The
                        // reference's box is 0.98 centered; the position is
                        // the box bottom, so the band is the cell's own.
                        double minX = landing.x() + 0.01, maxX = landing.x() + 1 - 0.01;
                        double minY = landing.y(), maxY = landing.y() + 0.98;
                        double minZ = landing.z() + 0.01, maxZ = landing.z() + 1 - 0.01;
                        for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                            net.zaminmc.torch.util.Position at = mob.position();
                            if (at.x() >= minX && at.x() <= maxX && at.y() >= minY
                                    && at.y() <= maxY && at.z() >= minZ && at.z() <= maxZ) {
                                mob.hurt(Math.min(i * 2, 40));
                            }
                        }
                        for (PlayerSession body : players.all()) {
                            if (body.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                                    || body.gamemode() == GameMode.SPECTATOR) {
                                continue; // canAvoidTraps
                            }
                            net.zaminmc.torch.util.Position at = body.position();
                            if (at.x() >= minX && at.x() <= maxX && at.y() >= minY
                                    && at.y() <= maxY && at.z() >= minZ && at.z() <= maxZ) {
                                body.hurt(Math.min(i * 2, 40));
                            }
                        }
                        // The progression roll (the reference's own band:
                        // 0.05 + i * 0.05).
                        if (gameplayRandom.nextFloat() < 0.05F + i * 0.05F) {
                            damage = net.zaminmc.torch.server.block.AnvilBlocks
                                    .progressedDamage(damage);
                        }
                    }
                    if (damage < 0) {
                        return java.util.Optional.empty(); // brokeAnvil
                    }
                    if (damage == net.zaminmc.torch.server.block.AnvilBlocks
                            .damageOf(entity.blockType().identifier())) {
                        return java.util.Optional.of(entity.blockType());
                    }
                    // The progressed state: the same facing, the walked DAMAGE.
                    int facing = net.zaminmc.torch.server.block.AnvilBlocks
                            .facingOf(entity.blockType().identifier());
                    return java.util.Optional.of(blockRegistry.require(
                            net.zaminmc.torch.server.block.AnvilBlocks.stateId(facing, damage)));
                });
                // Scheduled block updates (§466): neighbor notifications drive the
                // gravity/torch/grass/fire rules. Registered as a world listener before
                // the adapter, so engine-side rules observe every commit first.
                blockUpdateSystem = new BlockUpdateSystem(world, itemEntities, falling);
                blockUpdateSystem.setFireEnvironment(this::raining, gameplayRandom);
                // The game-rule reads (Slice 24a): the fire's own freeze
                // gate (doFireTick — FireBlock.tick line 145).
                blockUpdateSystem.setFireTickRead(() -> gameRules.getBoolean("doFireTick"));
                // The weather cycle (Slice 20b — World.tickWeather 1:1): the
                // overworld's own system (the nether has no sky). The wire
                // fan is ServerWorld.tickWeather's own publishes — the
                // gradient pushes (7/8) and the raining flip pair (1/2),
                // the overworld's players only.
                weather = new net.zaminmc.torch.server.weather.WeatherSystem(
                        new java.util.Random(worldSeed));
                // The game-rule table (Slice 24a — the 1.8.8 GameRules
                // port): one shared instance, both dimensions delegating
                // (the reference's derived-data model). Loaded before the
                // systems that read it wire up.
                gameRules = new net.zaminmc.torch.server.gamerule.GameRules();
                gameRuleStore = new net.zaminmc.torch.server.gamerule.GameRuleDataStore(
                        config.worldDataDir().resolve("gamerules.bin"));
                var persistedRules = gameRuleStore.load();
                if (persistedRules != null) {
                    gameRules.restore(persistedRules);
                }
                // The day-clock gate (Slice 24a): doDaylightCycle answers
                // whether tickTime advances the time-of-day (both
                // dimensions — one shared table).
                ticker.setDaylightCycleRead(() -> gameRules.getBoolean("doDaylightCycle"));
                // The public set route's ownership gate (§27.6): the same
                // mutation context the world's own writes assert through.
                gameRules.setOwnershipCheck(() -> {
                    try {
                        world.requireMutationContext("gamerule set");
                        return true;
                    } catch (IllegalStateException offLane) {
                        return false;
                    }
                });
                // The weather persistence (Slice 21b — WorldData's own save
                // fields): the five flags/clocks survive restarts; the
                // gradients never persist (the reference's fresh fields
                // walk from 0 on load).
                weatherStore = new net.zaminmc.torch.server.weather.WeatherDataStore(
                        config.worldDataDir().resolve("weather.bin"));
                weather.restore(weatherStore.load());
                weather.addListener(new net.zaminmc.torch.server.weather.WeatherSystem.Listener() {
                    @Override
                    public void onRainGradientChanged(float gradient) {
                        for (PlayerSession player : players.all()) {
                            if (player.dimension() == 0) {
                                player.link().playGameState(7, gradient);
                            }
                        }
                    }

                    @Override
                    public void onThunderGradientChanged(float gradient) {
                        for (PlayerSession player : players.all()) {
                            if (player.dimension() == 0) {
                                player.link().playGameState(8, gradient);
                            }
                        }
                    }

                    @Override
                    public void onRainingFlipped(boolean rainingNow) {
                        // The reference's closing fan (lines 873-878): the
                        // 1/2 flip pair + the gradient re-push in order.
                        for (PlayerSession player : players.all()) {
                            if (player.dimension() == 0) {
                                player.link().updateWeather(rainingNow);
                            }
                        }
                        onRainGradientChanged(weather.rainGradient());
                        onThunderGradientChanged(weather.thunderGradient());
                    }
                });
                // The lightning family (Slice 20c — ServerWorld.tickChunks
                // lines 313-321 + LightningBoltEntity): the strike roll,
                // the target resolution, and the bolt's own two-tick life.
                lightning = new net.zaminmc.torch.server.weather.LightningSystem(
                        new net.zaminmc.torch.server.weather.LightningSystem.Surface() {
                            @Override
                            public int precipitationHeight(int x, int z) {
                                // getPrecipitationHeight: the first FREE cell
                                // above the surface (the bolt's own seat,
                                // findLightningTarget's blockpos).
                                return surfaceY(x, z) + 1;
                            }

                            @Override
                            public boolean isAirAt(int x, int y, int z) {
                                return world.getBlock(new BlockPosition(x, y, z))
                                        .equals(world.airType());
                            }

                            @Override
                            public boolean isSolidAt(int x, int y, int z) {
                                return net.zaminmc.torch.server.block.WorldSolidity
                                        .isSolid(world.getBlock(new BlockPosition(x, y, z)));
                            }

                            @Override
                            public void placeFireAt(int x, int y, int z) {
                                world.setBlock(new BlockPosition(x, y, z),
                                        BuiltinBlocks.FIRE);
                            }

                            @Override
                            public java.util.List<net.zaminmc.torch.server.weather.LightningSystem.StruckBody> skyVisibleBodies(
                                    double minX, double minY, double minZ,
                                    double maxX, double maxY, double maxZ) {
                                java.util.List<net.zaminmc.torch.server.weather.LightningSystem.StruckBody> bodies =
                                        new java.util.ArrayList<>();
                                for (PlayerSession player : players.all()) {
                                    if (player.state() != PlayerState.PLAYING
                                            || player.dead() || player.dimension() != 0) {
                                        continue;
                                    }
                                    Position at = player.position();
                                    if (at.x() >= minX && at.x() <= maxX
                                            && at.y() >= minY && at.y() <= maxY
                                            && at.z() >= minZ && at.z() <= maxZ
                                            && at.y() >= surfaceY(
                                                    (int) Math.floor(at.x()), (int) Math.floor(at.z()))) {
                                        bodies.add(new net.zaminmc.torch.server.weather.LightningSystem.StruckBody() {
                                            @Override
                                            public Position position() {
                                                return at;
                                            }

                                            @Override
                                            public void strike() {
                                                // Entity.struckByLightning lines
                                                // 1546-1552: 5.0 + the 8-second
                                                // burn through the player arms.
                                                damageOnTick(player, 5.0f, 0.0f,
                                                        "was struck by lightning",
                                                        DamageKind.LIGHTNING);
                                                igniteWithFireProtection(player, 8);
                                            }
                                        });
                                    }
                                }
                                for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                                    Position at = mob.position();
                                    if (at.x() >= minX && at.x() <= maxX
                                            && at.y() >= minY && at.y() <= maxY
                                            && at.z() >= minZ && at.z() <= maxZ
                                            && at.y() >= surfaceY(
                                                    (int) Math.floor(at.x()), (int) Math.floor(at.z()))) {
                                        bodies.add(new net.zaminmc.torch.server.weather.LightningSystem.StruckBody() {
                                            @Override
                                            public Position position() {
                                                return at;
                                            }

                                            @Override
                                            public void strike() {
                                                // The mob arm: the manager's
                                                // hurt walk + the 8-second ignite.
                                                mobs.hurt(mob, 5.0f, 0.0,
                                                        net.zaminmc.torch.event.DamageCause.LIGHTNING);
                                                mob.ignite(160);
                                            }
                                        });
                                    }
                                }
                                return bodies;
                            }
                        }, new java.util.Random(), LIGHTNING_ID_BASE);
                lightning.setDifficultyRead(() -> difficulty);
                // The ice/snow precipitation arm (Slice 21c —
                // ServerWorld.tickChunks lines 325-341): the freeze runs
                // without the rain gate (the cold freezes dry), the snow
                // rides it. The temperature gate (Biome's registration
                // table) keeps both arms no-ops on the engine's warm biome
                // set — the vanilla-plains envelope, faithfully.
                precipitation = new net.zaminmc.torch.server.weather.PrecipitationSystem(
                        new net.zaminmc.torch.server.weather.PrecipitationSystem.Surface() {
                            @Override
                            public int precipitationHeight(int x, int z) {
                                // The reference's getPrecipitationHeight: the
                                // FIRST FREE CELL above the surface — the cell
                                // the snow lands on, one above the topmost
                                // block (the cauldron fill's dispatch reads
                                // height-1 = the surface block itself). The
                                // pre-23i shape answered the surface block
                                // directly — an off-by-one the warm-biome
                                // temperature gate masked (both frozen arms
                                // stayed no-ops); the cauldron's third arm
                                // exposed it.
                                return surfaceY(x, z) + 1;
                            }

                            @Override
                            public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                                return world.getBlock(new BlockPosition(x, y, z));
                            }

                            @Override
                            public int blockLightAt(int x, int y, int z) {
                                return world.blockLightAt(new BlockPosition(x, y, z));
                            }

                            @Override
                            public boolean isWaterSource(net.zaminmc.torch.block.BlockType type) {
                                // canFreeze's level-0 gate (the reference's
                                // FLOWING_WATER.LEVEL == 0 — the source).
                                return type.identifier()
                                        .equals(net.zaminmc.torch.server.block.FluidBlocks.WATER_SOURCE);
                            }

                            @Override
                            public boolean isAirAt(int x, int y, int z) {
                                return world.getBlock(new BlockPosition(x, y, z))
                                        .equals(world.airType());
                            }

                            @Override
                            public boolean isSolidAt(int x, int y, int z) {
                                return net.zaminmc.torch.server.block.WorldSolidity
                                        .isSolid(world.getBlock(new BlockPosition(x, y, z)));
                            }

                            @Override
                            public float temperatureAt(int x, int z) {
                                String name = world.biomeAt(new BlockPosition(x, 64, z));
                                try {
                                    return net.zaminmc.torch.server.world.Biome
                                            .valueOf(name.toUpperCase(java.util.Locale.ROOT))
                                            .temperature();
                                } catch (IllegalArgumentException unknown) {
                                    return net.zaminmc.torch.server.world.Biome.PLAINS
                                            .temperature();
                                }
                            }

                            @Override
                            public void placeIceAt(int x, int y, int z) {
                                world.setBlock(new BlockPosition(x, y, z), BuiltinBlocks.ICE);
                            }

                            @Override
                            public void placeSnowAt(int x, int y, int z) {
                                world.setBlock(new BlockPosition(x, y, z),
                                        BuiltinBlocks.SNOW_LAYER);
                            }

                            @Override
                            public boolean isRainyBiomeAt(int x, int z) {
                                // Biome.isRainy: the dry desert/hell columns
                                // refuse the fill; the temperate set allows
                                // it (the plains' own answer).
                                String name = world.biomeAt(new BlockPosition(x, 64, z));
                                try {
                                    return net.zaminmc.torch.server.world.Biome
                                            .valueOf(name.toUpperCase(java.util.Locale.ROOT))
                                            .rainy();
                                } catch (IllegalArgumentException unknown) {
                                    return net.zaminmc.torch.server.world.Biome.PLAINS
                                            .rainy();
                                }
                            }

                            @Override
                            public void randomPrecipitationTick(int x, int y, int z,
                                                                java.util.Random random) {
                                // The block's own precipitation tick — the
                                // cauldron's rain fill today (CauldronBlock
                                // lines 168-175: the 1-in-20 roll, the
                                // level+1 write under the 3 cap).
                                BlockType block = world.getBlock(new BlockPosition(x, y, z));
                                if (net.zaminmc.torch.server.cauldron.CauldronBlocks
                                        .isCauldron(block)) {
                                    if (random.nextInt(20) == 1) {
                                        int level = net.zaminmc.torch.server.cauldron
                                                .CauldronBlocks.levelOf(block);
                                        if (level < 3) {
                                            setCauldronLevel(new BlockPosition(x, y, z),
                                                    level + 1);
                                        }
                                    }
                                }
                            }
                        }, new java.util.Random(worldSeed ^ 0x21C));
                lightning.addListener(new net.zaminmc.torch.server.weather.LightningSystem.Listener() {
                    @Override
                    public void onBoltStruck(int entityId, double x, double y, double z) {
                        // The Spawn Global Entity fan: the overworld's
                        // players only (the bolt is a global body of that
                        // dimension).
                        for (PlayerSession player : players.all()) {
                            if (player.dimension() == 0) {
                                player.link().spawnLightning(entityId, x, y, z);
                            }
                        }
                    }

                    @Override
                    public void onThunderPair(double x, double y, double z,
                                              float thunderPitch, float explodePitch) {
                        // tick lines 87-90: the thunder at the global
                        // volume (10000 — every client hears it) and the
                        // explode pair at 2.
                        fxManager.sound(new Position(x, y, z),
                                "ambient.weather.thunder", 10000.0f, thunderPitch);
                        fxManager.sound(new Position(x, y, z),
                                "random.explode", 2.0f, explodePitch);
                    }
                });
                // The world itself dispatches every committed change (§208): the
                // neighbor-update system observes player- AND engine-driven changes.
                world.addChangeListener(blockUpdateSystem);
                // Redstone (Slice 9a — the reference's signal model): the wire
                // cascade and the family dispatch ride the commit synchronously
                // (vanilla's flag-1 walk inside setBlockState), so the propagation
                // is same-tick instant; the scheduled-tick queue (the torch's
                // 2-tick reaction, the repeater's delay) drains per tick.
                redstoneSystem = new net.zaminmc.torch.server.redstone.RedstoneSystem(
                        world, itemEntities);
                world.addChangeListener(redstoneSystem);
                // The door family's signal arm plays the same open/close
                // pair the use arm does (DoorBlock.doEvent 1003/1006 — the
                // reference's own tones at the swinging lower half).
                redstoneSystem.setDoorSoundCallback((at, opening) -> fxManager.sound(
                        new Position(at.x() + 0.5, at.y() + 0.5, at.z() + 0.5),
                        opening ? "random.door_open" : "random.door_close", 0.8f, 1.0f));
                // The plate's occupancy probe (the engine's bodies): any mob
                // or playing body inside the 0.125-inset, 0.25-tall box, the
                // wooden plates adding the item entities (the reference's
                // MOBS vs EVERYTHING activation rules).
                redstoneSystem.setPlateProbe((plate, includeItems) -> {
                    double minX = plate.x() + 0.125, maxX = plate.x() + 1 - 0.125;
                    double minY = plate.y(), maxY = plate.y() + 0.25;
                    double minZ = plate.z() + 0.125, maxZ = plate.z() + 1 - 0.125;
                    for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                        net.zaminmc.torch.util.Position at = mob.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            return true;
                        }
                    }
                    for (PlayerSession body : players.all()) {
                        if (body.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                                || body.gamemode() == GameMode.SPECTATOR) {
                            continue; // canAvoidTraps
                        }
                        net.zaminmc.torch.util.Position at = body.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            return true;
                        }
                    }
                    if (includeItems) {
                        for (ItemEntity item : itemEntities.all()) {
                            net.zaminmc.torch.util.Position at = item.position();
                            if (at.x() >= minX && at.x() <= maxX && at.y() >= minY
                                    && at.y() <= maxY && at.z() >= minZ && at.z() <= maxZ) {
                                return true;
                            }
                        }
                    }
                    return false;
                });
                // The weighted plates' counting probe (Slice 49a —
                // WeightedPressurePlateBlock.calculateOutputSignal's
                // getEntitiesOfType(Entity.class): EVERY entity in the
                // 0.125-inset box counts — mobs, players and dropped items
                // alike).
                redstoneSystem.setPlateCountProbe(plate -> {
                    double minX = plate.x() + 0.125, maxX = plate.x() + 1 - 0.125;
                    double minY = plate.y(), maxY = plate.y() + 0.25;
                    double minZ = plate.z() + 0.125, maxZ = plate.z() + 1 - 0.125;
                    int count = 0;
                    for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                        net.zaminmc.torch.util.Position at = mob.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            count++;
                        }
                    }
                    for (PlayerSession body : players.all()) {
                        if (body.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                                || body.gamemode() == GameMode.SPECTATOR) {
                            continue; // canAvoidTraps
                        }
                        net.zaminmc.torch.util.Position at = body.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            count++;
                        }
                    }
                    for (ItemEntity item : itemEntities.all()) {
                        net.zaminmc.torch.util.Position at = item.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY
                                && at.y() <= maxY && at.z() >= minZ && at.z() <= maxZ) {
                            count++;
                        }
                    }
                    return count;
                });
                // The trapped chest's viewer count (Slice 49b —
                // ChestBlock.getSignal lines 379-392: the sessions holding
                // this chest's window open, clamped 0-15 on the wire).
                redstoneSystem.setTrappedChestViewers(position -> {
                    int viewers = 0;
                    for (PlayerSession body : players.all()) {
                        if (body.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                                || body.openContainerKind() != PlayerSession.ContainerKind.CHEST
                                || body.openContainerPosition() == null) {
                            continue;
                        }
                        if (body.openContainerPosition().equals(position)
                                && world.getBlock(position).identifier()
                                        .equals(net.zaminmc.torch.server.block.BuiltinBlocks
                                                .TRAPPED_CHEST.identifier())) {
                            viewers++;
                        }
                    }
                    return viewers;
                });
                // The detector rail's cart probe (Slice 38 —
                // DetectorRailBlock.updateOutputState's getMinecarts over
                // the rail's own cell: any minecart body inside the box).
                redstoneSystem.setCartProbe(rail -> {
                    for (var vehicle : vehicleManager.all()) {
                        if (vehicle.kind() != net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.MINECART
                                && vehicle.kind() != net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.CHEST_MINECART) {
                            continue;
                        }
                        net.zaminmc.torch.util.Position at = vehicle.position();
                        if (at.x() >= rail.x() && at.x() <= rail.x() + 1
                                && at.y() >= rail.y() && at.y() <= rail.y() + 1
                                && at.z() >= rail.z() && at.z() <= rail.z() + 1) {
                            return true;
                        }
                    }
                    return false;
                });
                // The comparator's analog source table (Slice 9d — the
                // reference's isAnalogSignalSource family over the engine's
                // containers): the chest and furnace fullness through their
                // managers, -1 for every non-source block (the reader's
                // not-a-source answer).
                redstoneSystem.setAnalogReader(position -> {
                    BlockType block = world.getBlock(position);
                    if (block.identifier().equals(BuiltinBlocks.CHEST.identifier())) {
                        net.zaminmc.torch.server.chest.ChestBlockEntity chest = chestManager.peek(position);
                        return chest == null ? 0
                                : net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                                        chest.snapshotSlots(), 64);
                    }
                    if (net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(block)) {
                        net.zaminmc.torch.server.hopper.HopperBlockEntity hopper =
                                hopperSystem.peek(position);
                        return hopper == null ? 0
                                : net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                                        hopper.snapshotSlots(), 64);
                    }
                    if (net.zaminmc.torch.server.dispenser.DispenserBlocks.isFamily(block)) {
                        // The dispenser family's nine slots (the same fullness
                        // walk the reference runs on every Inventory —
                        // DispenserBlock.getAnalogSignal lines 186-189).
                        net.zaminmc.torch.server.dispenser.DispenserBlockEntity container =
                                dispenserSystem == null ? null : dispenserSystem.peek(position);
                        return container == null ? 0
                                : net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                                        container.snapshotSlots(), 64);
                    }
                    if (net.zaminmc.torch.server.brewing.BrewingStandBlocks.isBrewingStand(block)) {
                        // The brewing stand's four slots (BrewingStandBlock
                        // lines 130-137: isAnalogSignalSource over
                        // InventoryMenu.getAnalogSignal — the same fullness
                        // walk every container runs).
                        net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand =
                                brewingStands == null ? null : brewingStands.peek(position);
                        return stand == null ? 0
                                : net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                                        stand.snapshotSlots(), 64);
                    }
                    if (block.identifier().equals(BuiltinBlocks.FURNACE.identifier())
                            || block.identifier().equals(BuiltinBlocks.FURNACE_LIT.identifier())) {
                        net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace =
                                furnaceManager.peek(position);
                        return furnace == null ? 0
                                : net.zaminmc.torch.server.redstone.RedstoneSystem.containerFullness(
                                        new net.zaminmc.torch.item.ItemStack[]{
                                                furnace.input(), furnace.fuel(), furnace.output()}, 64);
                    }
                    if (net.zaminmc.torch.server.cauldron.CauldronBlocks.isCauldron(block)) {
                        // The cauldron's level IS the signal (CauldronBlock
                        // lines 188-195: getAnalogSignal = LEVEL).
                        return net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(block);
                    }
                    if (net.zaminmc.torch.server.jukebox.JukeboxBlocks.isFamily(block)) {
                        // The jukebox's disc read (JukeboxBlock lines 97-108:
                        // Item.getId(record) + 1 - Item.getId(RECORD_13) —
                        // the disc's table index + 1, 0 with no record).
                        return jukeboxes.analogSignal(position);
                    }
                    if (net.zaminmc.torch.server.block.CakeBlocks.isCake(block.identifier())) {
                        // The cake's bite read (CakeBlock.getAnalogSignal
                        // lines 133-135): (7 - bites) * 2 — the whole cake's
                        // 14 down to the last crumb's 2.
                        return net.zaminmc.torch.server.block.CakeBlocks
                                .analogSignal(block.identifier());
                    }
                    return -1;
                });
                // The container-content wake (the reference's
                // TileEntity.markDirty -> updateNeighborComparators): every
                // chest/furnace slot mutation re-arms the comparators reading
                // that container.
                chestManager.setContentsChanged(position -> redstoneSystem.wakeComparators(position));
                furnaceManager.setContentsChanged(position -> redstoneSystem.wakeComparators(position));
                // The pistons (Slice 9e): the block-event state machine + the
                // two-tick moving carriers, hearing every committed change
                // like the redstone system does (the reference's flag-1 walk).
                pistonSystem = new net.zaminmc.torch.server.piston.PistonSystem(
                        world, redstoneSystem, itemEntities, fxManager, gameplayRandom);
                world.addChangeListener(pistonSystem);
                // The TNT (Slice 9f): the ignition system (the redstone arm,
                // the flint-and-steel use, the blast chain-reaction) + the
                // primed bodies' fuse and physics, riding the generalized
                // detonation walk.
                tntSystem = new net.zaminmc.torch.server.tnt.TntSystem(
                        world, redstoneSystem,
                        position -> solidAt(position.x(), position.y() - 0.1, position.z()),
                        (center, power) -> detonateAt(center.x(), center.y(), center.z(), power,
                                net.zaminmc.torch.server.tnt.TntSystem.MAX_DAMAGE, true),
                        fxManager, gameplayRandom, TNT_ID_BASE);
                world.addChangeListener(tntSystem);
                tntSystem.addListener(new TntEventDispatch());
                // The note blocks (Slice 27a): the note tiles + the three play
                // arms (the use's tune-and-play, the survival attack's play,
                // the power-edge's play), the reference's material table for
                // the instrument selection, and the two-packet broadcast
                // (the doEvent sound + the Block Action 0x24 relay).
                noteBlocks = new net.zaminmc.torch.server.noteblock.NoteBlockSystem(
                        world, redstoneSystem, fxManager);
                world.addChangeListener(noteBlocks);
                redstoneSystem.addNeighborListener(noteBlocks::neighborHeard);
                // The note tiles' own ZND persistence (the hopper store's
                // own durability rules).
                noteStore = new net.zaminmc.torch.server.noteblock.NoteDataStore(
                        config.worldDataDir().resolve("noteblocks.bin"));
                noteBlocks.restoreAll(noteStore.load());
                // The jukeboxes (Slice 27b): the record slots + the four
                // reference arms (the disc's insert through the useOn gate,
                // the HAS_RECORD eject, the break's own record drop, the
                // comparator's disc-index read) + the ZJD persistence.
                jukeboxes = new net.zaminmc.torch.server.jukebox.JukeboxBlockSystem(
                        world, itemEntities);
                world.addChangeListener(jukeboxes);
                jukeboxStore = new net.zaminmc.torch.server.jukebox.JukeboxDataStore(
                        config.worldDataDir().resolve("jukeboxes.bin"));
                jukeboxes.restoreAll(jukeboxStore.load());
                // The daylight detectors (Slice 27c): the sky-light power
                // band — the reference's own math chain (the time fraction,
                // the ambient darkness, the sun-angle cosine) over the
                // 20-tick BE gate, the use arm's invert toggle, and the
                // weak emission consult the redstone system reads.
                daylightDetectors = new net.zaminmc.torch.server.daylight.DaylightDetectorSystem(
                        world, weather);
                world.addChangeListener(daylightDetectors);
                // The decorations (Slice 28a/28b): the item frames + the
                // paintings — the shared survival poll, the placement/
                // interact/attack ladders, the through-the-wall comparator
                // consult, and the ZED persistence.
                decorations = new net.zaminmc.torch.server.decoration.DecorationSystem(
                        new DecorationSurface(), DECORATION_ID_BASE, gameplayRandom);
                decorationStore = new net.zaminmc.torch.server.decoration.DecorationDataStore(
                        config.worldDataDir().resolve("decorations.bin"));
                net.zaminmc.torch.server.decoration.DecorationDataStore.DecorationSnapshot
                        decorationSnapshot = decorationStore.load();
                decorations.restore(decorationSnapshot.frames(), decorationSnapshot.paintings());
                // The comparator's through-the-wall frame read (Slice 28a
                // — ComparatorBlock.getInputSignal lines 118-126): the
                // reader consults the frame registry at the air cell one
                // beyond the input block.
                redstoneSystem.setFrameReader(decorations::frameComparatorRead);
                // The armor stands (Slice 28c): the placement/swap/break
                // ladders + the ZAD persistence.
                armorStands = new net.zaminmc.torch.server.armorstand.ArmorStandSystem(
                        new ArmorStandSurface(), ARMOR_STAND_ID_BASE);
                armorStandStore = new net.zaminmc.torch.server.armorstand.ArmorStandDataStore(
                        config.worldDataDir().resolve("armorstands.bin"));
                armorStands.restore(armorStandStore.load());
                // The ender crystals (Slice 62 — the End pillar tops): the
                // End-only registry + the any-damage death + the tick's
                // fire write, over the End world's own read/write surface.
                // The crystals themselves rebuild from the generator's
                // deterministic decorate on every boot; the ZEC store
                // keeps only the destroyed-pillar marks.
                enderCrystals = new net.zaminmc.torch.server.endcrystal.EnderCrystalSystem(
                        new EnderCrystalSurface(), gameplayRandom, ENDER_CRYSTAL_ID_BASE);
                enderCrystalStore = new net.zaminmc.torch.server.endcrystal.EnderCrystalDataStore(
                        config.worldDataDir().resolve("DIM1/endercrystals.bin"));
                enderCrystals.restoreDestroyedCells(enderCrystalStore.load());
                // The firework rockets (Slice 65): the launch's near-zero
                // drift, the 10..22-tick fuse, the burst event 17 — the
                // entity family the fireworks item's own useOn arm births.
                fireworks = new net.zaminmc.torch.server.fireworks.FireworksSystem(
                        new FireworksSurface(), gameplayRandom, FIREWORKS_ID_BASE);
                // The dragon fight's defeated mark (Slice 63): the End's
                // chunks regenerate deterministically — the chunk-(0,0)
                // decorate would re-spawn the dragon every boot; the ZDR
                // store keeps the kill.
                dragonStore = new net.zaminmc.torch.server.endcrystal.DragonDataStore(
                        config.worldDataDir().resolve("DIM1/dragon.bin"));
                dragonDefeated = dragonStore.load();
                // The End's own blast walk (the crystal's 6.0F death): the
                // explosion service bound to the End world — the same ray
                // fan, committing through the End's own listener path.
                endExplosionService = new ExplosionService(new BlastWorld(endWorld),
                        gameplayRandom);
                // The cross-system neighbor bus (Slice 9f): the pistons and
                // the TNT ride the redstone family's neighbor dispatch —
                // the reference's world-level neighborChanged.
                redstoneSystem.addNeighborListener(pistonSystem::neighborHeard);
                redstoneSystem.addNeighborListener(tntSystem::neighborHeard);
                // The hoppers (Slice 10b): the transfer system (the 8-tick
                // cooldown, the push toward the FACING container, the pull
                // from above + the item-entity suck, the powered disable
                // through the neighbor bus, the break spill) — the §11
                // boundary hard case, every transfer one same-tick unit
                // under the boundary scope.
                hopperSystem = new net.zaminmc.torch.server.hopper.HopperSystem(
                        world, redstoneSystem, itemEntities, chestManager, furnaceManager);
                world.addChangeListener(hopperSystem); // the placement creates the BE state
                redstoneSystem.addNeighborListener(hopperSystem::neighborHeard);
                // The hopper contents' own ZHD persistence (the chest store's
                // own durability rules).
                hopperStore = new net.zaminmc.torch.server.hopper.HopperDataStore(
                        config.worldDataDir().resolve("hoppers.bin"));
                hopperSystem.restoreAll(hopperStore.load());
                // The tripwire (Slice 10c): the occupancy scan + the hook's
                // chain walk, riding the redstone family's scheduled-tick
                // queue + the cross-system neighbor bus.
                tripwireSystem = new net.zaminmc.torch.server.redstone.TripwireSystem(
                        world, redstoneSystem, fxManager);
                world.addChangeListener(tripwireSystem);
                redstoneSystem.setTripwireTicker(tripwireSystem::scheduledTripwireTick);
                redstoneSystem.addNeighborListener(tripwireSystem::neighborHeard);
                // The occupancy probe (the engine's per-tick box test —
                // the wire's slim shape, any body, items included like the
                // reference's getEntities walk).
                tripwireSystem.setOccupancyProbe(wire -> {
                    double minX = wire.x(), maxX = wire.x() + 1;
                    double minY = wire.y(), maxY = wire.y() + 0.15625;
                    double minZ = wire.z(), maxZ = wire.z() + 1;
                    for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                        net.zaminmc.torch.util.Position at = mob.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            return true;
                        }
                    }
                    for (PlayerSession body : players.all()) {
                        if (body.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                                || body.gamemode() == GameMode.SPECTATOR) {
                            continue; // canAvoidTraps
                        }
                        net.zaminmc.torch.util.Position at = body.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            return true;
                        }
                    }
                    for (ItemEntity item : itemEntities.all()) {
                        net.zaminmc.torch.util.Position at = item.position();
                        if (at.x() >= minX && at.x() <= maxX && at.y() >= minY && at.y() <= maxY
                                && at.z() >= minZ && at.z() <= maxZ) {
                            return true;
                        }
                    }
                    return false;
                });
                // Fluids (§472 pattern): the scheduled pour/dry/contact system,
                // waking on every committed change like the neighbor rules do.
                fluidSystem = new FluidSystem(new FluidWorld(), new FluidSink(itemEntities));
                world.addChangeListener(fluidSystem);
                // Explosions: the ray-fan destructor (the creeper's demolition).
                explosionService = new ExplosionService(new BlastWorld(world), new java.util.Random());
                // Experience orbs (the historical EntityXPOrb band): the small
                // physics bodies mining and kills release, simulation-owned.
                experienceOrbs = new ExperienceOrbManager(this::solidAt,
                        new java.util.Random(), ORB_ID_BASE);
                experienceOrbs.addListener(new OrbEventDispatch());
                // Random ticks (§471 pattern): grass growth and decay.
                randomTicks = new RandomTickSystem(world, new java.util.Random());
                // The game-rule read (Slice 24a): randomTickSpeed answers
                // the per-section pick band (ServerWorld.tickChunks 343).
                randomTicks.setRandomTickSpeedRead(() -> gameRules.getInt("randomTickSpeed"));
                // Projectiles (arrows, shards): the tick-thread physics system,
                // with damage semantics staying here in the combat callbacks.
                ProjectileManager projectiles = new ProjectileManager(
                        shapeGround,
                        new ProjectileHitResolver(),
                        new ProjectileCombatSink(),
                        fxManager,
                        new java.util.Random(),
                        PROJECTILE_ID_BASE);
                this.projectileManager = projectiles;
                projectiles.setEventBus(eventBus); // §27.7: the public projectile-spawn gate
                projectiles.addListener(new ProjectileEventDispatch());
                // The fireball's landing arm (SmallFireballEntity.onHit): the
                // fire block at the air cell the fireball crossed before the
                // solid one.
                projectiles.setFireSink((x, y, z) -> {
                    BlockPosition at = new BlockPosition(x, y, z);
                    if (at.y() >= BlockPosition.MIN_Y && at.y() <= BlockPosition.MAX_Y
                            && world.getBlock(at).equals(world.airType())) {
                        world.setBlock(at, BuiltinBlocks.FIRE);
                    }
                });
                // The dispensers (Slice 11a): the 4-tick reaction + the
                // behavior registry (the dropper's container push walks the
                // hopper system's adapter, the dispenser family's own
                // container state joins it the other way).
                dispenserSystem = new net.zaminmc.torch.server.dispenser.DispenserSystem(
                        world, redstoneSystem, itemEntities, projectiles, tntSystem,
                        hopperSystem, fxManager, new java.util.Random());
                dispenserSystem.setEventBus(eventBus); // §27.7: the public dispense gate
                // The dispenser family's slot persistence (Slice 11f, the
                // ledgered ZDD): the nine slots survive restarts.
                dispenserStore = new net.zaminmc.torch.server.dispenser.DispenserDataStore(
                        config.worldDataDir().resolve("dispensers.bin"));
                dispenserSystem.restoreAll(dispenserStore.load());
                world.addChangeListener(dispenserSystem); // the placement creates the BE state
                redstoneSystem.addNeighborListener(dispenserSystem::neighborHeard);
                redstoneSystem.setDispenserTicker(dispenserSystem::scheduledTick);
                hopperSystem.setDispenserLookup(dispenserSystem::getOrCreate);
                // The brewing stands (Slice 11e): the per-tick brew walk;
                // the state creation rides the open path + the hopper
                // lookups (the placement never writes slots).
                brewingStands = new net.zaminmc.torch.server.brewing.BrewingStandSystem();
                brewingStands.setContentsChanged(
                        position -> redstoneSystem.wakeComparators(position));
                // The brewing stands' persistence (Slice 11f, the ZBD): the
                // four slots + the timer survive restarts (the reference's
                // own Items + BrewTime NBT pair).
                brewingStore = new net.zaminmc.torch.server.brewing.BrewingStandDataStore(
                        config.worldDataDir().resolve("brewing_stands.bin"));
                brewingStands.restoreAll(brewingStore.load());
                // The beacons (Slice 29b — the reference port of
                // BeaconBlock + BeaconBlockEntity): the 80-tick update walk
                // (the pyramid recompute + the effect spread), the neighbor
                // recompute, the payment GUI, and the ZBT persistence.
                beaconSystem = new net.zaminmc.torch.server.beacon.BeaconSystem(
                        world, itemEntities);
                world.addChangeListener(beaconSystem);
                redstoneSystem.addNeighborListener(beaconSystem::neighborHeard);
                beaconSystem.setPlayers(() -> new java.util.ArrayList<>(players.all()));
                beaconStore = new net.zaminmc.torch.server.beacon.BeaconDataStore(
                        config.worldDataDir().resolve("beacons.bin"));
                beaconSystem.restoreAll(beaconStore.load());
                hopperSystem.setBrewingLookup(brewingStands::getOrCreate);
                // The rolling containers' scan (Slice 25e — getInventoryAt's
                // entity fallback): the chest minecarts inside the cell's
                // 1x1x1 box, the hopper's cart arm.
                hopperSystem.setCartLookup(cell -> {
                    java.util.List<net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity>
                            found = new java.util.ArrayList<>();
                    double cx = cell.x() + 0.5;
                    double cy = cell.y() + 0.5;
                    double cz = cell.z() + 0.5;
                    for (net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle
                            : vehicleManager.all()) {
                        if (!(vehicle
                                instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart)) {
                            continue;
                        }
                        Position at = vehicle.position();
                        if (Math.abs(at.x() - cx) <= 0.5 && Math.abs(at.y() - cy) <= 0.5
                                && Math.abs(at.z() - cz) <= 0.5) {
                            found.add(cart);
                        }
                    }
                    return found;
                });
                // The potions (Slice 11b): the per-body effect stores, the
                // cadenced periodic application, and the splash's area walk.
                potionSystem = new net.zaminmc.torch.server.potion.PotionSystem(
                        new PotionBodies());
                beaconSystem.setPotions(potionSystem); // the beacon's spread rides the potion family
                projectiles.setPotionSink((x, y, z, damage, directHit, throwerId) -> {
                    potionSystem.splash(x, y, z, damage, directHit,
                            resolveSplashThrower(throwerId));
                    // The world event 2002 (PotionEntity.onCollision's own
                    // closing doEvent): the color-carrying burst particles.
                    fxManager.potionSplash(new Position(x, y, z), damage);
                });
                // The ghast fireball's detonation (Slice 16c —
                // FireballEntity.onHit's explosion, the reference's own
                // 8*power+1 center wound): the same boundary-scoped walk the
                // TNT rides, at the fireball's power 1. The mobGriefing rule
                // answers the destructive flag (Slice 24a — FireballEntity
                // line 33's own consult): a no-grief world wounds and
                // knocks but never breaks blocks.
                projectiles.setExplosionSink((at, power) -> detonateAt(
                        at.x(), at.y(), at.z(), power, 8.0f * power + 1.0f,
                        gameRules.getBoolean("mobGriefing")));
                // The ender pearl's teleport arm (Slice 17c —
                // EnderPearlEntity.onCollision lines 45-71): the 5%
                // player-spawned endermite at the thrower's pre-teleport
                // feet, the position carry with the fall-distance reset,
                // and the 5.0 fall toll.
                projectiles.setPearlSink((throwerId, at) -> {
                    PlayerSession thrower = playerByEngineId(throwerId);
                    if (thrower == null || thrower.state() != PlayerState.PLAYING
                            || thrower.dead()) {
                        return; // the reference's connection-alive gate
                    }
                    // The 5% passenger (line 53): the endermite spawns at the
                    // thrower's own pre-teleport feet, not the landing. The
                    // doMobSpawning rule gates the spawn (Slice 24a —
                    // EnderPearlEntity.onCollision line 52's own consult);
                    // the teleport itself always runs.
                    if (gameRules.getBoolean("doMobSpawning")
                            && gameplayRandom.nextFloat() < ENDER_PEARL_ENDERMITE_CHANCE) {
                        MobEntity endermite = mobManager.spawnAt(
                                net.zaminmc.torch.server.entity.MobType.ENDERMITE,
                                new Position(thrower.position().x(), thrower.position().y(),
                                        thrower.position().z()));
                        // setPlayerSpawned(true) + the lifetime clock — the
                        // engine's endermite despawns at its own 2400 wall.
                        if (endermite != null) {
                            endermite.setBodyYaw(thrower.rotation().yaw());
                        }
                    }
                    // The teleport (line 63): the position carry, the
                    // fall-distance reset, then the flat 5.0 fall toll
                    // (line 65 — the armor's enchantment walk only).
                    thrower.setGraceTicks(10); // the knockback window's own
                    thrower.applyMovement(at, thrower.rotation(), false);
                    thrower.resetFallDistance();
                    damageOnTick(thrower,
                            applyEnchantProtection(thrower, ENDER_PEARL_FALL_DAMAGE,
                                    DamageKind.FALL),
                            0.0f, "hit the ground too hard", DamageKind.FALL);
                    thrower.link().resyncPosition();
                });
                // The ender crystal's pop (Slice 62 — any projectile that
                // crosses a crystal's box wounds it: EnderCrystalEntity
                // .takeDamage has no source gate, the arrow's pop is the
                // canonical one).
                projectiles.setCrystalSink((x, y, z) -> enderCrystals != null
                        && enderCrystals.damageAt(x, y, z));
                // The dragon part's wound (Slice 63 — EnderDragonPart
                // .takeDamage → the part walk): the hull's holder takes the
                // projectile's damage through the gates (the non-head /4+1,
                // the player-or-explosive source — mob shooters' arrows
                // glance off, the reference's own law).
                projectiles.setDragonSink((x, y, z, damage, explosive, shooterId) -> {
                    PlayerSession shooter = playerByEngineId(shooterId);
                    for (MobEntity dragon : mobManager.all()) {
                        if (dragon.type() != net.zaminmc.torch.server.entity.MobType.ENDER_DRAGON
                                || dragon.dead() || dragon.dimension() != 1) {
                            continue;
                        }
                        var part = dragon.dragonPartAt(x, y, z);
                        if (part == null) {
                            continue;
                        }
                        float wound = dragon.dragonDamagePart(part, damage,
                                shooter != null, explosive);
                        if (wound > 0.0f) {
                            mobManager.hurt(dragon, wound,
                                    Math.toDegrees(Math.atan2(-(x - dragon.position().x()),
                                            z - dragon.position().z())),
                                    net.zaminmc.torch.event.DamageCause.PROJECTILE);
                        }
                        return true; // the part ate the projectile either way
                    }
                    return false;
                });
                // The bottle o' enchanting's burst (Slice 65 —
                // ExperienceBottleEntity.onCollision lines 28-43): the
                // 2002 splash event at the impact plus the experience
                // burst — 3 + rand(5) + rand(5) points through the
                // roundSize ladder. No damage ever lands.
                projectiles.setXpBottleSink((x, y, z) -> {
                    for (ProjectileListener listener : projectileListeners) {
                        listener.onWorldEventAt(new Position(x, y, z),
                                WORLD_EVENT_BOTTLE_SPLASH);
                    }
                    if (experienceOrbs != null) {
                        int points = 3 + gameplayRandom.nextInt(5)
                                + gameplayRandom.nextInt(5);
                        experienceOrbs.spawnSplitBurst(new Position(x, y, z), points);
                    }
                });
                mobs.setPotionSystem(potionSystem); // the witch's drink arm (12i)
                // The difficulty reads (Slice 19e): the peaceful sweep
                // (MonsterEntity.tick) + the spawn gate (canSpawn).
                mobs.setDifficultyRead(() -> difficulty);
                // The game-rule read (Slice 24a): doMobLoot gates the
                // death loot walk (LivingEntity.die).
                mobs.setMobLootRead(() -> gameRules.getBoolean("doMobLoot"));
                // The leashes (Slice 11c): the knots + the per-tick walk.
                leadSystem = new net.zaminmc.torch.server.leash.LeadSystem(
                        new LeadSurface(), KNOT_ID_BASE);
                leadSystem.setSurvivalRead(world, BuiltinBlocks.FENCE.identifier());
                // The public §27.3 unleash path: the handle's verb routes
                // the system's own detach walk (the clear + the wire + the
                // drop) behind the §27.6 owner-context assert — the setter
                // retro-wires the restored mobs too.
                mobs.setLeashDetacher((mob, dropLead) -> {
                    world.requireMutationContext("unleash");
                    leadSystem.detachLeash(mob, dropLead);
                });
                // The natural spawn cycle (Slice 11d): the 1:1 walk.
                naturalSpawns = new net.zaminmc.torch.server.spawn.NaturalSpawnSystem(
                        new SpawnSurface(mobs, false),
                        new SpawnWorldQuery(world, false),
                        new java.util.Random());
                // The nether's own cycle (Slice 16d): the HellBiome rows over
                // the nether's chunks — the ghast, the pigman, the magma cube;
                // the skeleton's wither conversion rides the nether's own
                // spawn arm (Slice 18d).
                naturalSpawnsNether = new net.zaminmc.torch.server.spawn.NaturalSpawnSystem(
                        new SpawnSurface(mobs, true),
                        new SpawnWorldQuery(netherWorld, true),
                        new java.util.Random(), true);
                // The peaceful spawn gates (both dimensions — the
                // reference's canSpawn consult).
                naturalSpawns.setDifficultyRead(() -> difficulty);
                naturalSpawnsNether.setDifficultyRead(() -> difficulty);
                // The game-rule read (Slice 24a): doMobSpawning gates the
                // whole natural cycle (ServerWorld.tick line 176) — both
                // dimensions share the one table.
                naturalSpawns.setMobSpawningRead(() -> gameRules.getBoolean("doMobSpawning"));
                naturalSpawnsNether.setMobSpawningRead(() -> gameRules.getBoolean("doMobSpawning"));
                // Vehicles (§ vehicles slice): boats and minecarts on the
                // shared body model; the engine owns the mounted rider.
                VehicleManager vehicles = new VehicleManager(
                        new VehicleWorldQuery(),
                        vehicle -> {
                            // The detonation's own death (Slice 66 — explode
                            // line 129's remove()): the annihilated body
                            // drops nothing at all.
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity tnt
                                    && tnt.annihilated()) {
                                return;
                            }
                            // doEntityDrops (Slice 24a — MinecartEntity's
                            // own dropItems gate): the cart item and the
                            // chest extras both skip when the rule is off;
                            // the body still breaks.
                            if (!gameRules.getBoolean("doEntityDrops")) {
                                return;
                            }
                            // The death drops (Slice 21a — the reference's
                            // dropItems chain): the plain cart/boat item first
                            // (MinecartEntity.dropItems), then the chest
                            // variant's extras (the contents spill, then a
                            // CHEST block — never the chest-minecart item).
                            itemEntities.spawnDropAtBlock(
                                    new Position(vehicle.position().x(), vehicle.position().y() + 0.3,
                                            vehicle.position().z()),
                                    vehicleDropStack(vehicle),
                                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart) {
                                for (ItemStack stack : cart.chest().snapshotSlots()) {
                                    if (stack == null || stack.isEmpty()) {
                                        continue;
                                    }
                                    itemEntities.spawnDropAtBlock(
                                            new Position(vehicle.position().x(),
                                                    vehicle.position().y() + 0.3,
                                                    vehicle.position().z()),
                                            stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
                                }
                                itemEntities.spawnDropAtBlock(
                                        new Position(vehicle.position().x(), vehicle.position().y() + 0.3,
                                                vehicle.position().z()),
                                        ItemStack.of(BuiltinItems.CHEST, 1),
                                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
                            }
                            // The TNT variant's own extra (Slice 66 —
                            // TntMinecartEntity.dropItems lines 101-105: the
                            // TNT block item rides the plain-break chain).
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity) {
                                itemEntities.spawnDropAtBlock(
                                        new Position(vehicle.position().x(), vehicle.position().y() + 0.3,
                                                vehicle.position().z()),
                                        ItemStack.of(BuiltinItems.TNT, 1),
                                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
                            }
                            // The furnace variant's own extra (Slice 66 —
                            // FurnaceMinecartEntity.dropItems lines 63-68: the
                            // FURNACE block item rides the plain-break chain
                            // under doEntityDrops; the reference's own
                            // non-explosive-death gate rides the damage-source
                            // surface the engine's vehicle death path does
                            // not yet carry — ledgered).
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.FurnaceMinecartEntity) {
                                itemEntities.spawnDropAtBlock(
                                        new Position(vehicle.position().x(), vehicle.position().y() + 0.3,
                                                vehicle.position().z()),
                                        ItemStack.of(BuiltinItems.FURNACE, 1),
                                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
                            }
                            // The hopper variant's own extras (Slice 66 —
                            // HopperMinecartEntity.dropItems lines 132-138:
                            // the InventoryMinecart contents spill, then the
                            // HOPPER block item — never the hopper-minecart
                            // item).
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity hopperCart) {
                                for (ItemStack stack : hopperCart.hopper().snapshotSlots()) {
                                    if (stack == null || stack.isEmpty()) {
                                        continue;
                                    }
                                    itemEntities.spawnDropAtBlock(
                                            new Position(vehicle.position().x(),
                                                    vehicle.position().y() + 0.3,
                                                    vehicle.position().z()),
                                            stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
                                }
                                itemEntities.spawnDropAtBlock(
                                        new Position(vehicle.position().x(), vehicle.position().y() + 0.3,
                                                vehicle.position().z()),
                                        ItemStack.of(BuiltinItems.HOPPER, 1),
                                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
                            }
                        },
                        new java.util.Random(),
                        VEHICLE_ID_BASE);
                this.vehicleManager = vehicles;
                vehicles.setEventBus(eventBus); // §27.7: the public vehicle spawn gate
                vehicles.addListener(new VehicleEventDispatch());
                // Vehicle persistence (Slice 25g — the ZVD store): the
                // whole family's pre-25g gap closes — the carts (and the
                // chest minecart's cargo) survive restarts like the mobs,
                // the chests, and the furnaces.
                vehicleStore = new net.zaminmc.torch.server.entity.vehicle.VehicleDataStore(
                        config.worldDataDir().resolve("vehicles.bin"));
                vehicles.restoreAll(vehicleStore.load(),
                        () -> hopperSystem::hopperCartTransfer,
                        () -> new net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SpawnHost() {
                            /** The 16-block player gate (isNearPlayer's own). */
                            @Override public boolean playerNearby(double x, double y, double z) {
                                for (PlayerSession player : players.all()) {
                                    if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING) {
                                        continue;
                                    }
                                    double dx = player.position().x() - x;
                                    double dy = player.position().y() - y;
                                    double dz = player.position().z() - z;
                                    if (dx * dx + dy * dy + dz * dz <= 16.0 * 16.0) {
                                        return true;
                                    }
                                }
                                return false;
                            }

                            /** The nearby cap's same-kind count. */
                            @Override public int nearbyOfKind(
                                    net.zaminmc.torch.server.entity.MobType type,
                                    int cellX, int cellY, int cellZ) {
                                int nearby = 0;
                                for (var mob : mobManager.all()) {
                                    if (mob.type() != type) {
                                        continue;
                                    }
                                    net.zaminmc.torch.util.Position at = mob.position();
                                    if (Math.abs(at.x() - (cellX + 0.5))
                                            <= net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SPAWN_RANGE
                                            && Math.abs(at.y() - (cellY + 0.5))
                                            <= net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SPAWN_RANGE
                                            && Math.abs(at.z() - (cellZ + 0.5))
                                            <= net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity.SPAWN_RANGE) {
                                        nearby++;
                                    }
                                }
                                return nearby;
                            }

                            /** The unobstructed spawn (the engine's cell gate). */
                            @Override public boolean spawnAt(
                                    net.zaminmc.torch.server.entity.MobType type,
                                    double x, double y, double z) {
                                if (!net.zaminmc.torch.server.spawner.SpawnerManager
                                        .canSpawnAt(world, type, x, y, z)) {
                                    return false;
                                }
                                var spawned = mobManager.spawnAt(type,
                                        new net.zaminmc.torch.util.Position(x, y, z));
                                return spawned != null;
                            }
                        },
                        this::commandCartHost);
                // The fishing (Slice 67 — FishingBobberEntity +
                // FishingRodItem): the cast/float/bite-window/retrieve walk
                // over the engine's own surfaces.
                net.zaminmc.torch.server.fishing.FishingSystem fishingSystem =
                        new net.zaminmc.torch.server.fishing.FishingSystem(
                                new net.zaminmc.torch.server.fishing.FishingSystem.WorldQuery() {
                                    /** The raytrace stand-in: the first solid sample along the segment. */
                                    @Override public Position blockHit(Position from, Position to) {
                                        double dx = to.x() - from.x();
                                        double dy = to.y() - from.y();
                                        double dz = to.z() - from.z();
                                        int steps = Math.max(1,
                                                (int) Math.ceil(Math.max(Math.abs(dx),
                                                        Math.max(Math.abs(dy), Math.abs(dz))) / 0.05));
                                        for (int i = 1; i <= steps; i++) {
                                            double t = (double) i / steps;
                                            double px = from.x() + dx * t;
                                            double py = from.y() + dy * t;
                                            double pz = from.z() + dz * t;
                                            if (solidAt(px, py, pz)) {
                                                return new Position(px, py, pz);
                                            }
                                        }
                                        return null;
                                    }

                                    /** The 5-band water fraction (containsLiquid's own walk). */
                                    @Override public double waterFraction(
                                            double minX, double minY, double minZ,
                                            double maxX, double maxY, double maxZ) {
                                        double fraction = 0.0;
                                        for (int band = 0; band < 5; band++) {
                                            double y = minY + (maxY - minY) * band / 5.0
                                                    + 1.0E-7;
                                            var kind = net.zaminmc.torch.server.block.FluidBlocks
                                                    .kindOf(world.getBlock(blockAt(
                                                            (minX + maxX) / 2.0, y,
                                                            (minZ + maxZ) / 2.0)).identifier());
                                            if (kind == net.zaminmc.torch.server.block.FluidBlocks.Kind.WATER) {
                                                fraction += 0.2;
                                            }
                                        }
                                        return fraction;
                                    }

                                    /** The sky read: any solid block above closes the sky. */
                                    @Override public boolean hasSkyAccess(int x, int y, int z) {
                                        for (int above = y + 1;
                                                above <= net.zaminmc.torch.block.BlockPosition.MAX_Y;
                                                above++) {
                                            if (WorldSolidity.isSolid(world.getBlock(
                                                    new BlockPosition(x, above, z)))) {
                                                return false;
                                            }
                                        }
                                        return true;
                                    }

                                    @Override public boolean raining() {
                                        return weather != null && weather.raining();
                                    }
                                },
                                new net.zaminmc.torch.server.fishing.FishingSystem.HookResolver() {
                                    /** The segment clip stand-in: the first body along the samples. */
                                    @Override public Object hookAlong(Position from, Position to,
                                            int throwerId, int airTicks) {
                                        double dx = to.x() - from.x();
                                        double dy = to.y() - from.y();
                                        double dz = to.z() - from.z();
                                        int steps = Math.max(1,
                                                (int) Math.ceil(Math.max(Math.abs(dx),
                                                        Math.max(Math.abs(dy), Math.abs(dz))) / 0.1));
                                        for (int i = 1; i <= steps; i++) {
                                            double t = (double) i / steps;
                                            Position at = new Position(from.x() + dx * t,
                                                    from.y() + dy * t, from.z() + dz * t);
                                            MobEntity mob = mobManager.mobAt(at);
                                            if (mob != null && !mob.dead()) {
                                                return mob;
                                            }
                                            PlayerSession player = playerBodyAt(at);
                                            if (player != null) {
                                                if (player.engineEntityId() == throwerId
                                                        && airTicks < 5) {
                                                    continue; // the angler's own body: 5-tick gate
                                                }
                                                return player;
                                            }
                                        }
                                        return null;
                                    }

                                    @Override public boolean hookedAlive(Object hooked) {
                                        if (hooked instanceof MobEntity mob) {
                                            return !mob.dead();
                                        }
                                        return hooked instanceof PlayerSession player
                                                && player.state() == PlayerState.PLAYING
                                                && !player.dead();
                                    }

                                    @Override public Position hookedAnchor(Object hooked) {
                                        if (hooked instanceof MobEntity mob) {
                                            return new Position(mob.position().x(),
                                                    mob.position().y() + mob.type().height * 0.8,
                                                    mob.position().z());
                                        }
                                        if (hooked instanceof PlayerSession player) {
                                            return new Position(player.position().x(),
                                                    player.position().y() + 1.8 * 0.8,
                                                    player.position().z());
                                        }
                                        return null;
                                    }

                                    /** The retrieve's pull (the 0.1 + sqrt(d)*0.08 law). */
                                    @Override public void pullToward(Object hooked,
                                            double dx, double dy, double dz) {
                                        if (hooked instanceof MobEntity mob) {
                                            mob.addVelocity(dx, dy, dz);
                                        }
                                        // The player's client-authoritative body: the
                                        // pull rides the knockback surface's own slice
                                        // (ledgered — the engine's movement protocol
                                        // has no server-side player velocity write).
                                    }
                                },
                                new net.zaminmc.torch.server.fishing.FishingSystem.CatchSink() {
                                    /** The catch flies toward the angler (the 0.1 + sqrt law). */
                                    @Override public void dropCatch(Position at,
                                            net.zaminmc.torch.item.ItemStack stack,
                                            double anglerX, double anglerY, double anglerZ) {
                                        ItemEntity caught = itemEntities.spawnThrown(at, stack);
                                        if (caught == null) {
                                            return; // the §27.7 gate withheld the catch
                                        }
                                        double dx = anglerX - at.x();
                                        double dy = anglerY - at.y();
                                        double dz = anglerZ - at.z();
                                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                                        caught.setVelocity(dx * 0.1,
                                                dy * 0.1 + Math.sqrt(distance) * 0.08, dz * 0.1);
                                    }

                                    @Override public void dropXp(Position at, int amount) {
                                        experienceOrbs.spawnOrb(at, amount);
                                    }

                                    @Override public void sound(Position at, String key,
                                            float volume, float pitch) {
                                        fxManager.sound(at, key, volume, pitch);
                                    }
                                },
                                new java.util.Random(), FISHING_ID_BASE);
                this.fishing = fishingSystem;
                // The held-rod enchant reads (getResult's own tables).
                fishingSystem.setEnchantReads(
                        throwerId -> heldRodLevel(throwerId, Enchantments.LUCK_OF_THE_SEA.id),
                        throwerId -> heldRodLevel(throwerId, Enchantments.LURE.id));
                // The public §27.3 entity-family views: the five engine
                // populations captured as stable point-in-time copies (the
                // managers' all() contract) behind the World interface.
                // The nether keeps the default empty views (its drops/
                // projectiles/vehicles route through the overworld's
                // managers today — the same envelope as its mob view).
                world.attachEntityFamilyViews(
                        itemEntities::all,
                        projectiles::all,
                        falling::all,
                        vehicles::all,
                        experienceOrbs::all);
                // The public §27.3 container views: the position-keyed
                // chest/furnace/hopper/brewing-stand reads over the four
                // managers (the engine objects dressed as live handles,
                // §27.5) — the write arms assert this world's
                // authoritative context.
                world.attachContainerView(new ContainerViews(
                        chestManager, furnaceManager, hopperSystem,
                        brewingStands, world));
                // The public §27.3 block-entity views beyond the containers:
                // the spawner/sign reads — the spawners filtered to this
                // world's own dimension (the manager is global).
                world.attachBlockEntityView(
                        new net.zaminmc.torch.server.blockentity.BlockEntityViews(
                                spawnerManager, 0, signManager));
                netherWorld.attachBlockEntityView(
                        new net.zaminmc.torch.server.blockentity.BlockEntityViews(
                                spawnerManager, -1, signManager));
                // The public §27.3 scheduled-update views: the neighbor-update
                // queue + the gravity fuses — the queries copy, the mutation
                // arms assert the world's own context.
                world.attachScheduledUpdateView(
                        new net.zaminmc.torch.server.block.ScheduledUpdateViews(
                                blockUpdateSystem, world));
                // The public §27.3 redstone views: the engine's own power walks
                // (owner-context consistent reads).
                world.attachRedstoneView(
                        new net.zaminmc.torch.server.redstone.RedstoneSignalViews(
                                redstoneSystem));
                // The public §27.3 primed-TNT view: the live fuse bodies.
                world.attachPrimedTntView(tntSystem::primedBodies);
                ticker.setTickHandler(() -> {
                    // The per-region boundary walk FIRST (the §7 protocol's
                    // target-side apply): every live region domain drains its
                    // inbound cross-owner intents at its own boundary, so an
                    // applied intent is visible to the same tick's systems.
                    // The ambient router drain already ran at the tick edge.
                    world.drainRegionIntents(crossOwnerRouter);
                    netherWorld.drainRegionIntents(crossOwnerRouter);
                    blockUpdateSystem.tick(); // §466: scheduled updates (falls start here)
                    redstoneSystem.tick(); // the torch/repeater scheduled reactions
                    pistonSystem.tick(); // the piston block events + the moving carriers
                    hopperSystem.tick(); // the hoppers' 8-tick transfer cadence
                    daylightDetectors.tick(); // the 20-tick sky-light power band (Slice 27c)
                    dispenserSystem.tick(); // the stale-state sweep (Slice 11a)
                    tripwireSystem.tick(); // the tripwire occupancy scan
                    tntSystem.tick(); // the primed TNT fuses + physics
                    blockInteraction.tickMining(); // the vanilla dig accumulator + 0x28 stages
                    fluidSystem.tick();       // §472 pattern: pours, streams, contact
                    falling.tick();           // §470: falling physics + landings
                    projectileManager.tick(); // ranged combat physics
                    vehicles.tick();          // the boats and minecarts
                    tickVehicleRiders(vehicles);
                    tickMobRiders();          // the horse/pig seats
                    tickFishing();            // the bobbers' float + bite window (Slice 67)
                    furnaceManager.tick(world, itemEntities);
                    chestManager.tick(world);
                    signManager.tick(world);
                    if (spawnerManager != null) {
                        spawnerManager.tick(); // Slice 12g: the spawner walk
                    }
                    itemEntities.tick(players.all());
                    experienceOrbs.tick(players.all()); // the XP bodies pay out
                    mobs.tick(players.all(), world.timeOfDay());
                    // The weather cycle (Slice 20b — the tick walk before
                    // the spawns: the reference's own tick order, the
                    // gradient shaving the same tick's ambient darkness).
                    tickWeather();
                    // The lightning family (Slice 20c — ServerWorld.
                    // tickChunks lines 313-321): the per-chunk strike roll
                    // over the loaded window, then the live bolts' walk.
                    // The ice/snow arm (Slice 21c) runs second in the same
                    // per-chunk loop — the reference's own arm order.
                    if (lightning != null) {
                        for (net.zaminmc.torch.server.world.EngineChunk chunk
                                : world.loadedChunks()) {
                            lightning.tryStrikeAtChunk(chunk.position().x(),
                                    chunk.position().z(),
                                    weather.raining(), weather.thundering());
                            if (precipitation != null) {
                                precipitation.tryIceAndSnowAtChunk(chunk.position().x(),
                                        chunk.position().z(), weather.raining());
                            }
                        }
                        lightning.tick();
                    }
                    // The natural cycle (Slice 11d — ServerWorld.tick line
                    // 180: the spawner walk every tick, the caps scaled by
                    // the spawning-chunk count). The gradients feed the
                    // ambient-darkness shaves (calculateAmbientDarkness
                    // lines 1013-1015).
                    naturalSpawns.tick(players.all(), world.timeOfDay(),
                            weather.rainGradient(), weather.thunderGradient());
                    // The nether's own cycle (Slice 16d): only the players
                    // standing in the nether feed its spawning-chunk set.
                    java.util.List<PlayerSession> netherPlayers = new java.util.ArrayList<>();
                    for (PlayerSession player : players.all()) {
                        if (player.dimension() == -1
                                && player.state() == PlayerState.PLAYING) {
                            netherPlayers.add(player);
                        }
                    }
                    naturalSpawnsNether.tick(netherPlayers, netherWorld.timeOfDay());
                    drainGenerationAttachments(); // the populate-time villagers + loot chests (Slice 12b)
                    tickMobPotions(); // the mobs' effect stores (Slice 11b)
                    tickLeashes(); // the leash physics + the knots (Slice 11c)
                    tickDecorations(); // the frames' + paintings' survival poll (Slice 28a/28b)
                    randomTicks.tick(players.all()); // §471: grass growth/decay
                    brewingStands.tick(world);
                beaconSystem.tick(world.totalTicks()); // the brew cycles (Slice 11e)
                    tickFurnaceViewers();
                    tickBrewingViewers();
                    tickBeaconViewers();
                    tickEnchantingViewers();
                    tickPlayerBodies();
                    // The portal caches' eviction walk (the reference's
                    // PortalForcer.tick per world).
                    worldPortalForcer.tick(world.totalTicks());
                    netherPortalForcer.tick(netherWorld.totalTicks());
                    // Relight transport (§475): protocol 47 has no light-only
                    // packet, so every chunk column the light touched this tick
                    // re-sends once, deduplicated across the whole cascade.
                    lightEngine.flushRelight(this::publishChunkRelit);
                    if (world.totalTicks() % 20 == 0) {
                        // The reference's timeSync cadence (MinecraftServer
                        // lines 551-558: ticks % 20 == 0, per world, the
                        // players in that dimension only).
                        publishTimeChanged(); // smooth day cycle on every client
                    }
                });
                witherSweepDrops = new DropService(new java.util.Random()); // Slice 59: the engine-side break walk's own table
                // The skull store (Slice 60): the tiles + the summon
                // walk's own world consults (the tick lane owns both).
                skulls = new net.zaminmc.torch.server.skull.SkullManager(
                        new net.zaminmc.torch.server.skull.SkullManager.Host() {
                            @Override
                            public net.zaminmc.torch.block.BlockType blockAt(
                                    BlockPosition position) {
                                return world.getBlock(position);
                            }

                            @Override
                            public void setBlock(BlockPosition position,
                                    net.zaminmc.torch.block.BlockType type) {
                                world.setBlock(position, type);
                            }

                            @Override
                            public net.zaminmc.torch.block.BlockType airType() {
                                return world.airType();
                            }

                            @Override
                            public int difficulty() {
                                return EngineServer.this.difficulty;
                            }

                            @Override
                            public void summonWither(Position at, float bodyYawDegrees) {
                                // trySpawn lines 184-201: the birth at the
                                // top-center cell — the spawn, the axis yaw,
                                // onSummoned's window + third health, and
                                // the snowball burst stand-in at the base
                                // (the reference's own 120-particle column
                                // rides the fx bus's Poof family).
                                MobEntity wither = mobManager.spawnAt(
                                        net.zaminmc.torch.server.entity.MobType.WITHER, at);
                                if (wither != null) {
                                    wither.setBodyYaw(bodyYawDegrees);
                                    wither.witherSummoned();
                                }
                                fxManager.poof(new Position(at.x(), at.y() - 1.5, at.z()));
                            }
                        });
                blockInteraction = new BlockInteractionService(world, ticker,
                        change -> world.republish(change.position()), // the resync path (§441)
                        session -> session.gamemode(), new DropService(new java.util.Random()), itemEntities,
                        // The damage-aware block-item resolution (Slice 31 —
                        // ItemBlock.onItemUse's getStateFromMeta): the wood
                        // families and the five dye-family items place their
                        // variant's block via the stack's damage field
                        // (Slice 35's planks/log/log2/leaves/leaves2/sapling
                        // domain first), everything else resolves by the
                        // plain identifier.
                        (type, damage) -> net.zaminmc.torch.server.block.WoodVariantBlocks
                                .placementBlockOf(type.identifier(), damage)
                                .flatMap(id -> blockRegistry.lookup(id))
                                .or(() -> net.zaminmc.torch.server.block.DyeFamilyBlocks
                                        .placementBlockOf(type.identifier(), damage)
                                        .flatMap(id -> blockRegistry.lookup(id)))
                                .or(() -> net.zaminmc.torch.server.block.FlowerBlocks
                                        .placementBlockOf(type.identifier(), damage)
                                        .flatMap(id -> blockRegistry.lookup(id)))
                                .or(() -> net.zaminmc.torch.server.block.DoublePlantBlocks
                                        .placementBlockOf(type.identifier(), damage)
                                        .flatMap(id -> blockRegistry.lookup(id)))
                                .or(() -> net.zaminmc.torch.server.block.WallBlocks
                                        .placementBlockOf(type.identifier(), damage)
                                        .flatMap(id -> blockRegistry.lookup(id)))
                                .or(() -> net.zaminmc.torch.server.block.RedSandstoneBlocks
                                        .placementBlockOf(type.identifier(), damage)
                                        .flatMap(id -> blockRegistry.lookup(id)))
                                .or(() -> blockRegistry.lookup(type.identifier())),
                        this::publishInventoryChanged);
                blockInteraction.setEventBus(eventBus); // §27.7: the public break/place gates
                // The game-rule read (Slice 24a): doTileDrops gates the
                // break's drop walk (Block.java's own gate).
                blockInteraction.setTileDropsRead(() -> gameRules.getBoolean("doTileDrops"));
                // The placement-support gate (Slice 44 — PlantBlock.canBePlaced
                // lines 36-38 over canBePlacedOn): the flora consult refusing
                // a placement whose soil is wrong — the flowers (all nine) and
                // the tall grass on the grass/dirt/farmland band, the dead
                // bush on the sand/hardened-clay/dirt band. Every other kind
                // passes (the historical behavior).
                blockInteraction.setPlacementSupportGate((target, held) -> {
                    if (held == null) {
                        return true;
                    }
                    net.zaminmc.torch.util.Identifier id = held.identifier();
                    String name = id.value();
                    if (net.zaminmc.torch.server.block.FlowerBlocks.isFlowerIdentifier(id)
                            || name.equals("tall_grass")) {
                        return net.zaminmc.torch.server.block.FlowerBlocks
                                .isSupported(world, target);
                    }
                    if (name.equals("dead_bush")) {
                        net.zaminmc.torch.block.BlockType soil =
                                world.getBlock(target.offset(0, -1, 0));
                        String soilName = soil.identifier().value();
                        return soilName.equals("sand")
                                || soilName.equals("hardened_clay")
                                || soilName.equals("stained_hardened_clay")
                                || soilName.startsWith("stained_hardened_clay_")
                                || soilName.equals("dirt");
                    }
                    if (name.equals("cake")) {
                        // CakeBlock.canBePlaced lines 96-98 + canSurvive
                        // lines 92-94: the block below must be SOLID.
                        return net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                                world.getBlock(target.offset(0, -1, 0)));
                    }
                    if (name.equals("flower_pot")) {
                        // FlowerPotBlock.canBePlaced lines 118-120: the
                        // block below must have a SOLID TOP.
                        return net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                                world.getBlock(target.offset(0, -1, 0)));
                    }
                    return true;
                });
                // The double-plant pair arm (Slice 45 —
                // DoublePlantBlock.beforeMinedByPlayer lines 124-153): the
                // UPPER's break takes its lower with it — creative silently
                // (removeBlock's own write), survival with the lower's drop
                // walk and the shears arm's tall-grass item over the grass
                // and fern variants.
                blockInteraction.setBreakReport((session, at, previous, creative) ->
                        doublePlantBreakReport(session, at, previous, creative));
                // The cake's startMining eat (Slice 46 —
                // CakeBlock.startMining lines 62-66: the left click eats a
                // slice too, the same canEat(false) gate as the use arm).
                blockInteraction.setMiningStartReport((session, at) -> {
                    // The cake's startMining eat (Slice 46).
                    eatCakeSlice(session, at);
                    // The dragon egg's startMining teleport (Slice 55 —
                    // DragonEggBlock.startMining lines 81-83: the punch
                    // teleports the egg away before any dig accumulates).
                    teleportDragonEgg(at);
                });
                // The dig-start hook (Slice 27a — the reference's
                // block.startMining arm, ServerPlayerInteractionManager
                // line 133): the note block plays on the survival start.
                blockInteraction.setMiningStartListener(position -> {
                    if (noteBlocks != null) {
                        noteBlocks.attackFromTick(position);
                    }
                    // RedstoneOreBlock.startMining lines 33-37 -> interact:
                    // the mining start lights the plain ore too (the same
                    // swap the use arm carries — the reference's own pair).
                    if (world.getBlock(position).identifier().equals(
                            net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE.identifier())) {
                        world.setBlock(position,
                                net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE);
                    }
                });
                // A survival-broken furnace spills its slots, and a survival-
                // broken chest spills its 27, into the world first. The same
                // hook emits the break/place feedback through the FX bus.
                blockInteraction.setBlockBrokenListener((position, brokenType) -> {
                    // The skull's typed drop (Slice 60 — SkullBlock
                    // .onRemoved lines 132-152): the tile's TYPE rides the
                    // dropped stack's damage (the block itself carries no
                    // drop row — the reference's empty dropItems); the
                    // survival break pays it, the creative break never
                    // reaches this listener (the NODROP law).
                    if (skulls != null
                            && net.zaminmc.torch.server.block.SkullBlocks.isSkull(
                                    brokenType.identifier())) {
                        var skullTile = skulls.remove(position);
                        if (skullTile != null) {
                            itemEntities.spawnDropAtBlock(new Position(
                                    position.x() + 0.5, position.y() + 0.5, position.z() + 0.5),
                                    new net.zaminmc.torch.item.ItemStack(
                                            BuiltinItems.SKULL, 1, skullTile.type(), null),
                                    net.zaminmc.torch.server.entity.ItemEntity
                                            .PICKUP_DELAY_DROP_TICKS);
                        }
                    }
                    furnaceManager.onBlockBroken(position, itemEntities);
                    chestManager.onBlockBroken(position, itemEntities);
                    hopperSystem.onBlockBroken(position);
                    dispenserSystem.onBlockBroken(position);
                    brewingStands.onBlockBroken(position, itemEntities);
                    beaconSystem.onBlockBroken(position);
                    noteBlocks.onBlockBroken(position);
                    jukeboxes.onBlockBroken(position);
                    daylightDetectors.onBlockBroken(position);
                    doorSiblingCleanup(position);
                    bedSiblingCleanup(position, brokenType);
                    bedSpawnCleanup(position);
                });
                // The mining award (the historical dropXp rolls): a survival
                // break of an XP ore releases the roll as orbs at the block.
                // Creative breaks never fire the hook (the no-XP rule).
                blockInteraction.setSurvivalXpListener((player, brokenType, at) -> {
                    int amount = ExperienceAwards.forBlockBreak(brokenType.identifier(),
                            gameplayRandom);
                    if (amount <= 0) {
                        return;
                    }
                    experienceOrbs.spawnBurst(new Position(at.x() + 0.5, at.y() + 0.5,
                            at.z() + 0.5), amount, 3);
                });
                // The infested egg's burst (Slice 18c —
                // InfestedBlock.dropItems lines 79-88): the survival break
                // spawns the silverfish at the block's own center (the
                // reference's setPositionAndAngles at pos + 0.5 on x/z, yaw
                // 0 — the pre-tick engine spawn).
                blockInteraction.setInfestedBurstHook(position ->
                        spawnSilverfishAt(new Position(position.x() + 0.5,
                                position.y(), position.z() + 0.5)));
                blockInteraction.setCommitFeedbackListener(commit -> {
                    if (commit.now().equals(world.airType())) {
                        // A break: the shatter burst plus the dig family sound.
                        Position center = new Position(commit.position().x() + 0.5,
                                commit.position().y() + 0.5, commit.position().z() + 0.5);
                        fxManager.blockShatter(center, commit.previous());
                        fxManager.sound(center,
                                BlockSoundMap.digSound(commit.previous().identifier())
                                        .orElse("dig.stone"), 1.0f,
                                0.75f + fxRandom.nextFloat() * 0.2f);
                    } else if (!commit.previous().equals(commit.now())) {
                        // A place (or replace): the dig family sound at the softer
                        // historical place volume.
                        Position center = new Position(commit.position().x() + 0.5,
                                commit.position().y() + 0.5, commit.position().z() + 0.5);
                        fxManager.sound(center,
                                BlockSoundMap.digSound(commit.now().identifier())
                                        .orElse("dig.stone"), 0.8f,
                                0.8f + fxRandom.nextFloat() * 0.2f);
                    }
                });
                CommandService dispatcher = new CommandService();
                registerBuiltinCommands(dispatcher);
                commands = dispatcher;
                chatService = new ChatService(ticker, dispatcher, this::publishChat);
                chatService.setEventBus(eventBus); // §27.7: the public chat gate
                // The permanent architecture's Phase 1-3 wiring completes
                // BEFORE the readiness latch: the simulation domain (Phase
                // 1 — every EngineWorld mutation asserts through it), the
                // scheduler substrate (Phase 2 — the tick's deferred work),
                // and the compute lane (Phase 3 — the offload pool). The
                // engine's public surface is fully concurrent-ready the
                // moment start() returns.
                simulationDomain = net.zaminmc.torch.server.concurrent.OwnershipDomain
                        .create("simulation:" + config.worldName());
                world.attachDomain(simulationDomain);
                ticker.attachNetherWorld(netherWorld);
                // The nether's public §27.6 owner path (same authoritative
                // lane) + its §27.5 entity view (empty until its own
                // population lands — the engine's mobs live in the overworld).
                netherWorld.attachOwnerExecutor(ticker::submit);
                netherWorld.attachEntityView(() -> java.util.List.of());
                simulationScheduler = new net.zaminmc.torch.server.concurrent.SimulationScheduler();
                ticker.attachScheduler(simulationScheduler, simulationDomain);
                computeSubsystem = new net.zaminmc.torch.server.concurrent.ComputeSubsystem();
                ticker.attachCompute(computeSubsystem, simulationDomain);
                // The public §27.3 compute view over the real subsystem + domain.
                computeView = new net.zaminmc.torch.server.concurrent.AsyncComputeView(
                        computeSubsystem, simulationDomain);
                // The §28.1 fourth-category storage lane + its public view.
                blockingIoLane = new net.zaminmc.torch.server.concurrent.BlockingIoLane();
                ticker.attachIoLane(blockingIoLane, simulationDomain);
                blockingIoView = new net.zaminmc.torch.server.concurrent.BlockingIoView(
                        blockingIoLane, simulationDomain);
                domainWorkerPool = new net.zaminmc.torch.server.concurrent.DomainWorkerPool();
                ticker.attachDomainPool(domainWorkerPool);
                crossOwnerRouter = new net.zaminmc.torch.server.concurrent.CrossOwnerRouter();
                ticker.attachCrossOwnerRouter(crossOwnerRouter, simulationDomain);
                worldReady.countDown();
                simulationDomain.enter();
                netherDomain.enter(); // the second dimension rides the same loop
                endDomain.enter(); // the third dimension rides it too (Slice 64)
                try {
                    ticker.runLoop(); // blocks until stop
                } finally {
                    endDomain.exit();
                    netherDomain.exit();
                    simulationDomain.exit();
                }
            } catch (Throwable t) {
                bootFailure.set(t);
                worldReady.countDown();
            }
        }, "zamin-boot");
        boot.start();

        if (!worldReady.await(BOOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            state.set(ServerState.FAILED);
            throw new IllegalStateException("World boot timed out");
        }
        if (bootFailure.get() != null) {
            state.set(ServerState.FAILED);
            throw new IllegalStateException("World boot failed", bootFailure.get());
        }
        state.set(ServerState.RUNNING);
        LOGGER.info(() -> "World '" + world.name() + "' ready (spawn "
                + world.spawnPosition() + ", chunks=" + world.loadedChunkCount() + ")");
    }

    private void pregenerateSpawnArea(EngineWorld engineWorld) {
        // Pregenerate the full initial view: the join sequence can then send every
        // chunk synchronously, so a client never waits past its position packet for
        // spawn-adjacent chunks. Larger play areas load on demand via requestChunkLoad.
        Position spawn = engineWorld.spawnPosition();
        var center = spawn.toBlockPosition().chunkPosition();
        int radius = config.viewDistance();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                engineWorld.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(center.x() + dx, center.z() + dz));
            }
        }
    }

    @Override
    public void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates graceful shutdown: stop intake, disconnect players, stop the
     * simulation, release resources. Idempotent; safe from any thread.
     */
    public void shutdown(Runnable adapterShutdown) {
        ServerState previous = state.getAndUpdate(s ->
                s == ServerState.RUNNING || s == ServerState.STARTING
                        ? ServerState.STOPPING
                        : (s == ServerState.STOPPING ? ServerState.STOPPING : s));
        if (previous != ServerState.RUNNING && previous != ServerState.STARTING
                && previous != ServerState.STOPPING) {
            return; // never ran, already stopped, or failed: nothing to stop
        }
        LOGGER.info("Server stopping");

        // 1. stop accepting work / disconnect clients (adapter first: it feeds us joins)
        if (adapterShutdown != null) {
            try {
                adapterShutdown.run();
            } catch (RuntimeException e) {
                LOGGER.warning("Protocol adapter shutdown problem: " + e);
            }
        }

        // 2. engine-side player cleanup (each leaving player's state is persisted
        //    so a restart continues their survival exactly where it stopped)
        for (PlayerSession session : players.all()) {
            try {
                session.markDisconnecting();
                persistPlayerNow(session);
                players.unregister(session);
                session.link().kick("Server closed");
            } catch (RuntimeException e) {
                LOGGER.warning("Player cleanup problem for " + session.name() + ": " + e);
            }
        }

        // 3. flush world state while the tick thread is still alive (consistent snapshot)
        if (ticker != null && world != null) {
            try {
                saveAllNow();
            } catch (RuntimeException e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "World save during shutdown failed", e);
            }
        }

        // 4. stop simulation
        if (ticker != null) {
            if (computeSubsystem != null) {
                computeSubsystem.shutdown();
            }
            if (blockingIoLane != null) {
                blockingIoLane.shutdown();
            }
            if (domainWorkerPool != null) {
                domainWorkerPool.shutdown();
            }
            if (crossOwnerRouter != null) {
                crossOwnerRouter.shutdown();
            }
            ticker.stop();
            try {
                ticker.awaitStop(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        state.set(ServerState.STOPPED);
        shutdownLatch.countDown();
        LOGGER.info("Server stopped");
    }

    // ------------------------------------------------------------------ Server API

    @Override
    public ServerState state() {
        return state.get();
    }

    @Override
    public Collection<World> worlds() {
        // Both live dimensions (the nether boots alongside the overworld — 8b).
        if (world == null) {
            return java.util.List.of();
        }
        return netherWorld == null
                ? java.util.List.of((World) world)
                : java.util.List.of((World) world, (World) netherWorld);
    }

    @Override
    public Collection<Player> players() {
        return players.publicView();
    }

    @Override
    public net.zaminmc.torch.event.EventBus events() {
        return eventBus;
    }

    public EngineWorld world() {
        return world;
    }

    /**
     * The nether dimension (8b-ii), or null before the boot constructs it —
     * the portal walk (8b-iii) and the acceptance tests read it.
     */
    public EngineWorld netherWorld() {
        return netherWorld;
    }

    /**
     * The mob spawner system's public surface (Slice 18e — the wire's tile
     * read + the protocol adapter's listener registration): null before
     * the boot's construction window.
     */
    public net.zaminmc.torch.server.spawner.SpawnerManager spawnerManager() {
        return spawnerManager;
    }

    /** The nether dimension's ownership domain ("simulation:nether"). */
    public net.zaminmc.torch.server.concurrent.OwnershipDomain netherDomain() {
        return netherDomain;
    }

    public FrozenBlockRegistry blockRegistry() {
        return blockRegistry;
    }

    @Override
    public net.zaminmc.torch.block.BlockRegistry blocks() {
        return blockRegistry;
    }

    /** The public item registry view (the frozen BuiltinItems set; §27.3). */
    @Override
    public net.zaminmc.torch.item.ItemRegistry items() {
        return itemRegistryView;
    }

    private final net.zaminmc.torch.item.ItemRegistry itemRegistryView =
            new net.zaminmc.torch.server.item.ItemRegistryView();

    /** The public async-compute view (§27.3's compute family; wired at boot). */
    @Override
    public net.zaminmc.torch.concurrent.AsyncCompute compute() {
        net.zaminmc.torch.concurrent.AsyncCompute view = computeView;
        if (view == null) {
            throw new IllegalStateException("compute is unavailable before the server boots");
        }
        return view;
    }

    /** The public blocking-I/O view (§28.1's fourth category; wired at boot). */
    @Override
    public net.zaminmc.torch.concurrent.BlockingIo blockingIo() {
        net.zaminmc.torch.concurrent.BlockingIo view = blockingIoView;
        if (view == null) {
            throw new IllegalStateException("blocking I/O is unavailable before the server boots");
        }
        return view;
    }

    /**
     * The public game-rule view (§27.3 — Slice 24a): the shared table
     * itself (§27.5 — the engine object IS the handle); the reads are
     * snapshot-safe from any thread, the {@code set} route carries the
     * ownership assert.
     */
    @Override
    public net.zaminmc.torch.gamerule.GameRules gameRules() {
        net.zaminmc.torch.server.gamerule.GameRules rules = gameRules;
        if (rules == null) {
            throw new IllegalStateException("game rules are unavailable before the server boots");
        }
        return rules;
    }

    private volatile net.zaminmc.torch.concurrent.AsyncCompute computeView;
    private volatile net.zaminmc.torch.concurrent.BlockingIo blockingIoView;

    /** The public scheduler view (built on first use; the ticker exists from boot). */
    @Override
    public net.zaminmc.torch.scheduler.Scheduler scheduler() {
        net.zaminmc.torch.scheduler.Scheduler view = schedulerView;
        if (view == null) {
            synchronized (this) {
                view = schedulerView;
                if (view == null) {
                    view = new net.zaminmc.torch.server.scheduler.EngineScheduler(ticker);
                    schedulerView = view;
                }
            }
        }
        return view;
    }

    /** The lazily-built public scheduler (§27.3) — see {@link #scheduler()}. */
    private volatile net.zaminmc.torch.scheduler.Scheduler schedulerView;

    /** The public dispenser behavior registry (§27.3) — the system's own surface. */
    @Override
    public net.zaminmc.torch.dispenser.DispenseBehaviors dispenserBehaviors() {
        return dispenserSystem.behaviors();
    }

    /** The outbound packet hooks (§27.3) — registration safe from any thread, firing on the wire threads. */
    @Override
    public net.zaminmc.torch.protocol.ProtocolHooks protocolHooks() {
        return packetHooks;
    }

    /** The public recipe registry (§27.3) — the pure any-thread read over the frozen set. */
    @Override
    public net.zaminmc.torch.crafting.Recipes recipes() {
        return new net.zaminmc.torch.crafting.Recipes() {
            @Override
            public java.util.Collection<RecipeView> all() {
                java.util.List<RecipeView> views = new java.util.ArrayList<>();
                for (var recipe : craftingRecipes().recipes()) {
                    views.add(RecipesFacade.viewOf(recipe));
                }
                return java.util.List.copyOf(views);
            }

            @Override
            public java.util.Optional<RecipeView> match(
                    net.zaminmc.torch.item.ItemStack[] grid2x2) {
                return craftingRecipes().match(grid2x2, 2).map(RecipesFacade::viewOf);
            }

            @Override
            public java.util.Optional<RecipeView> match3x3(
                    net.zaminmc.torch.item.ItemStack[] grid3x3) {
                return craftingRecipes().match(grid3x3, 3).map(RecipesFacade::viewOf);
            }
        };
    }

    /** The public enchantment registry (§27.3) — the pure any-thread read. */
    @Override
    public net.zaminmc.torch.enchantment.EnchantmentRegistry enchantments() {
        return new net.zaminmc.torch.enchantment.EnchantmentRegistry() {
            @Override
            public java.util.Collection<EnchantmentView> all() {
                java.util.List<EnchantmentView> views = new java.util.ArrayList<>();
                for (var entry : net.zaminmc.torch.server.enchantment.Enchantments.all()) {
                    views.add(new EnchantmentView(entry.id, entry.key,
                            entry.weight, entry.maxLevel));
                }
                return java.util.List.copyOf(views);
            }

            @Override
            public java.util.Optional<EnchantmentView> byId(int id) {
                for (var entry : net.zaminmc.torch.server.enchantment.Enchantments.all()) {
                    if (entry.id == id) {
                        return java.util.Optional.of(new EnchantmentView(
                                entry.id, entry.key, entry.weight, entry.maxLevel));
                    }
                }
                return java.util.Optional.empty();
            }

            @Override
            public java.util.Optional<EnchantmentView> byKey(String key) {
                for (var entry : net.zaminmc.torch.server.enchantment.Enchantments.all()) {
                    if (entry.key.equals(key)) {
                        return java.util.Optional.of(new EnchantmentView(
                                entry.id, entry.key, entry.weight, entry.maxLevel));
                    }
                }
                return java.util.Optional.empty();
            }

            // The enchanting data predicates (§27.3's registry wave — the
            // reference's own tables over the pure any-thread read).
            @Override
            public boolean canEnchant(int id, net.zaminmc.torch.item.ItemStack stack) {
                var entry = net.zaminmc.torch.server.enchantment.Enchantments.byId(id);
                return entry != null && entry.canEnchant(stack, true);
            }

            @Override
            public boolean isCompatible(int id, int otherId) {
                var entry = net.zaminmc.torch.server.enchantment.Enchantments.byId(id);
                var other = net.zaminmc.torch.server.enchantment.Enchantments.byId(otherId);
                if (entry == null || other == null) {
                    return true; // no known conflict
                }
                return entry.isCompatible(other);
            }

            @Override
            public int enchantabilityOf(net.zaminmc.torch.item.ItemStack stack) {
                return net.zaminmc.torch.server.enchantment.Enchantments.enchantabilityOf(stack);
            }

            @Override
            public java.util.OptionalInt minXp(int id, int level) {
                var entry = net.zaminmc.torch.server.enchantment.Enchantments.byId(id);
                return entry == null ? java.util.OptionalInt.empty()
                        : java.util.OptionalInt.of(entry.minXP(level));
            }

            @Override
            public java.util.OptionalInt maxXp(int id, int level) {
                var entry = net.zaminmc.torch.server.enchantment.Enchantments.byId(id);
                return entry == null ? java.util.OptionalInt.empty()
                        : java.util.OptionalInt.of(entry.maxXP(level));
            }
        };
    }

    /** The public configuration read (§27.3) — the frozen boot-time value. */
    @Override
    public net.zaminmc.torch.config.ServerConfig config() {
        return new net.zaminmc.torch.config.ServerConfig(
                config.host(), config.port(), config.worldName(), config.motd(),
                config.maxPlayers(), config.viewDistance(), config.tickRateHz(),
                config.dataDir(), config.gamemode(), config.pvp(), config.levelType(),
                config.whiteList(), config.regionSizeChunks());
    }

    /** The engine's own recipe service (the crafting paths' chokepoint). */
    private CraftingService craftingRecipes() {
        return crafting;
    }

    /** The recipe view mapping (§27.5: the engine object dressed as a value). */
    private static final class RecipesFacade {
        static net.zaminmc.torch.crafting.Recipes.RecipeView viewOf(
                net.zaminmc.torch.server.crafting.CraftingRecipe recipe) {
            return new net.zaminmc.torch.crafting.Recipes.RecipeView(
                    recipe.result().item(), recipe.result().count(),
                    recipe.result().damage(), recipe.isShaped(), recipe.size());
        }
    }

    @Override
    public double tps(int windowSeconds) {
        EngineTicker running = ticker;
        return running == null ? 20.0 : running.averageTps(windowSeconds);
    }

    /** The command dispatcher (public: the wire layer's tab completion reads it). */
    public CommandService commands() {
        return commands;
    }

    /**
     * The simulation ticker (public: semantic entries like {@link #damage}
     * and the acceptance tests probe tick-confined state through it).
     * Work submitted here runs on the world owner thread, in tick order.
     */
    public EngineTicker ticker() {
        return ticker;
    }

    /** The Phase 3 compute lane (public: the offload tests read its telemetry). */
    public net.zaminmc.torch.server.concurrent.ComputeSubsystem computeSubsystem() {
        return computeSubsystem;
    }

    /** The §28.1 blocking-I/O lane (public: the storage-lane tests read its telemetry). */
    public net.zaminmc.torch.server.concurrent.BlockingIoLane blockingIoLane() {
        return blockingIoLane;
    }

    /** The Phase 4 cross-owner router (public: the boundary tests read its telemetry). */
    public net.zaminmc.torch.server.concurrent.CrossOwnerRouter crossOwnerRouter() {
        return crossOwnerRouter;
    }

    /** The overworld's spatial partition (public: the boundary tests walk its regions). */
    public net.zaminmc.torch.server.concurrent.RegionPartition worldRegionPartition() {
        return worldRegionPartition;
    }

    /** The world's sign text registry (public: the wire's chunk replay reads it). */
    public SignManager signs() {
        return signManager;
    }

    /**
     * A client submitted sign text (Update Sign 0x12): the block must be a
     * sign within reach of the sender. Safe from any thread; the application
     * runs on the tick thread and fans the new text out to viewers.
     */
    public void editSign(PlayerSession player, BlockPosition position, String[] lines) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(position, "position");
        ticker.submit(() -> editSignOnTick(player, position, lines));
    }

    private void editSignOnTick(PlayerSession player, BlockPosition position, String[] lines) {
        if (player.state() != PlayerState.PLAYING || player.dead()) {
            return;
        }
        if (!SignManager.isSignType(world.getBlock(position))) {
            return; // not a sign: the historical silent refusal
        }
        double dx = position.x() + 0.5 - player.position().x();
        double dy = position.y() + 0.5 - (player.position().y() + 1.0);
        double dz = position.z() + 0.5 - player.position().z();
        if (dx * dx + dy * dy + dz * dz > 8.0 * 8.0) {
            return; // too far from the sender: the anti-grief guard
        }
        // §27.7: the sign-edit gate BEFORE the text commits — the sign
        // still carries its old lines. A cancelled edit keeps them; a
        // handler's rewritten lines ride the walk when not cancelled.
        net.zaminmc.torch.event.block.SignChangeEvent gate =
                new net.zaminmc.torch.event.block.SignChangeEvent(
                        world.name(), position, player.uuid(), player.name(), lines);
        eventBus.fire(gate);
        if (gate.cancelled()) {
            return; // the old text stands, no viewer replay
        }
        String[] applied = gate.lines();
        signManager.set(position, applied);
        for (SignListener listener : signListeners) {
            listener.onSignChanged(position, signManager.peek(position));
        }
        LOGGER.fine(() -> player.name() + " wrote a sign at " + position);
    }

    /**
     * Sign placement (the historical ItemSign): the standing sign faces its
     * placer (the opposite of the look direction, the 45-degree rotation
     * band folded to the four cardinals), and the survival path consumes one
     * item. Creative placements pass the client-claimed block like the
     * generic path. Tick-thread context.
     */
    private void placeSignOnTick(PlayerSession session, BlockPosition clicked, int face,
                                 java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return; // the generic placement gates (bad face / clicked air / occupied)
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return; // the placement reach gate (the historical survival reach)
        }
        if (creativeHeld.isEmpty()
                && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return; // nothing to consume
        }
        // Facing: a side-face use hangs a wall sign facing away from the
        // wall (the 1.8 wall-sign rule, metadata 2-5); a top-face use
        // plants a standing sign fronting the placer.
        if (face >= 2 && face <= 5) {
            BlockType wallSign = switch (face) {
                case 2 -> BuiltinBlocks.WALL_SIGN_NORTH;
                case 3 -> BuiltinBlocks.WALL_SIGN_SOUTH;
                case 4 -> BuiltinBlocks.WALL_SIGN_WEST;
                default -> BuiltinBlocks.WALL_SIGN_EAST;
            };
            world.setBlock(target, wallSign);
            if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
                session.inventory().consumeHeld(1);
                publishInventoryChanged(session);
            }
            return;
        }
        // Standing facing: the sign fronts the placer. Yaw 0 = south (+Z),
        // the sign north; the cardinals at 90-degree bands, metadata 0/4/8/12.
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int oppositeBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the LOOK
        BlockType[] facings = {
                BuiltinBlocks.SIGN_NORTH,   // looking south: sign faces north
                BuiltinBlocks.SIGN_EAST,    // looking west: sign faces east
                BuiltinBlocks.SIGN_SOUTH,   // looking north: sign faces south
                BuiltinBlocks.SIGN_WEST};   // looking east: sign faces west
        world.setBlock(target, facings[oppositeBand]);
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    public PlayerRegistry playerRegistry() {
        return players;
    }

    /**
     * The skull's placement (Slice 60 — SkullItem.useOn lines 55-81): the
     * block lands at the clicked face's offset cell carrying the FACE as
     * its FACING (the item's own rule — the reference's
     * getPlacementState is dead code here), the tile writes the stack's
     * TYPE (the damage 0-5), the floor mount takes the placer's yaw as
     * the 16-step rotation (line 58's {@code floor(yaw * 16 / 360 + 0.5)
     * & 15}), and a wither skull (type 1) consults the summon walk
     * (trySpawn) — the soul-sand T found, the whole build vanishes for
     * the boss's birth.
     */
    private void placeSkullOnTick(PlayerSession session, BlockPosition clicked, int face,
                                  java.util.Optional<BlockType> creativeHeld) {
        net.zaminmc.torch.server.skull.SkullManager skullStore = skulls;
        if (skullStore == null) {
            return; // the store is not wired (the test rig)
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return; // the generic placement gates (bad face / clicked air / occupied)
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return; // the placement reach gate (the historical survival reach)
        }
        net.zaminmc.torch.item.ItemStack held = session.inventory().held();
        int type = 0;
        if (creativeHeld.isPresent()) {
            // The creative middle-click carry: the plain form (the engine's
            // carry model carries no damage).
            type = 0;
        } else if (held.isEmpty()) {
            return; // nothing to place
        } else {
            type = held.damage() & 0xFF;
        }
        // The block with the FACE as its FACING (line 55's own rule).
        world.setBlock(target,
                net.zaminmc.torch.server.block.SkullBlocks.forState(face));
        // The rotation: only the floor mount reads the yaw (line 57-59).
        int rotation = 0;
        if (face == 1) {
            rotation = (int) Math.floor(
                    session.rotation().yaw() * 16.0f / 360.0f + 0.5f) & 15;
        }
        // The tile write + the summon consult (lines 60-81).
        skullStore.place(target, type, rotation);
        if (type == 1) {
            skullStore.trySummonWither(target);
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /** The semantic entry point for player-driven block changes. */
    public BlockInteractionService blockInteraction() {
        return blockInteraction;
    }

    /** The semantic chat entry point: validation, commands, audience. */
    public ChatService chatService() {
        return chatService;
    }

    /** The simulation-owned item entity manager (present once the world is up). */
    public ItemEntityManager itemEntities() {
        return itemEntities;
    }

    /** Registers an internal item-entity observer (e.g. the protocol adapter's sync). */
    public void addItemListener(ItemEntityManager.Listener listener) {
        itemListeners.add(listener);
    }

    /** @return the runtime difficulty id (0 peaceful .. 3 hard). */
    @Override
    public int difficulty() {
        return difficulty;
    }

    /** The simulation-owned mob manager (present once the world is up). */
    public MobManager mobs() {
        return mobManager;
    }

    /** Registers an internal mob observer (e.g. the protocol adapter's sync). */
    public void addMobListener(MobManager.Listener listener) {
        mobListeners.add(listener);
    }

    /** The simulation-owned falling-block system (present once the world is up). */
    public FallingBlockEntityManager fallingEntities() {
        return fallingEntities;
    }

    /** Registers an internal falling-block observer (e.g. the protocol adapter's sync). */
    public void addFallingListener(FallingBlockEntityManager.Listener listener) {
        fallingListeners.add(listener);
    }

    /** The scheduled block-update system (exposed for behavioral tests). */
    public BlockUpdateSystem blockUpdates() {
        return blockUpdateSystem;
    }

    /** The redstone signal engine (exposed for behavioral tests). */
    public net.zaminmc.torch.server.redstone.RedstoneSystem redstone() {
        return redstoneSystem;
    }

    /** The potion effect system (exposed for behavioral tests). */
    public net.zaminmc.torch.server.potion.PotionSystem potions() {
        return potionSystem;
    }

    /** The leash system (exposed for the protocol adapter + tests). */
    public net.zaminmc.torch.server.leash.LeadSystem leads() {
        return leadSystem;
    }

    /** The random-tick system (exposed for deterministic growth probes). */
    public RandomTickSystem randomTicks() {
        return randomTicks;
    }

    /** The crafting matcher (exposed for recipe tests). */
    public CraftingService crafting() {
        return crafting;
    }

    /** The furnace block entities (exposed for behavioral tests). */
    public FurnaceManager furnaceManager() {
        return furnaceManager;
    }

    /** The chest block entities (exposed for behavioral tests). */
    public ChestManager chestManager() {
        return chestManager;
    }

    /** The hopper subsystem (public: the hopper tests read/write its state). */
    public net.zaminmc.torch.server.hopper.HopperSystem hopper() {
        return hopperSystem;
    }

    /** @return the beacon system (Slice 29b), or null before boot. */
    public net.zaminmc.torch.server.beacon.BeaconSystem beacons() {
        return beaconSystem;
    }

    /** The dispenser subsystem (public: the dispenser tests read/write its state). */
    public net.zaminmc.torch.server.dispenser.DispenserSystem dispensers() {
        return dispenserSystem;
    }

    /** The piston subsystem (exposed for behavioral tests). */
    public net.zaminmc.torch.server.piston.PistonSystem pistons() {
        return pistonSystem;
    }

    /** The TNT subsystem (exposed for behavioral tests). */
    public net.zaminmc.torch.server.tnt.TntSystem tnt() {
        return tntSystem;
    }

    /** The note block subsystem (Slice 27a — exposed for behavioral tests
     *  and the protocol adapter's listener registration). */
    public net.zaminmc.torch.server.noteblock.NoteBlockSystem noteBlocks() {
        return noteBlocks;
    }

    /** The jukebox system (Slice 27b): the record slots + the four arms. */
    public net.zaminmc.torch.server.jukebox.JukeboxBlockSystem jukeboxes() {
        return jukeboxes;
    }

    /** The daylight detector system (Slice 27c): the sky-light power band. */
    public net.zaminmc.torch.server.daylight.DaylightDetectorSystem daylightDetectors() {
        return daylightDetectors;
    }

    /** Registers a TNT wire-sync observer (the protocol adapter). */
    public void addTntListener(net.zaminmc.torch.server.tnt.TntSystem.Listener listener) {
        tntListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    /** The TNT events' fan-out to the registered observers. */
    private final class TntEventDispatch implements net.zaminmc.torch.server.tnt.TntSystem.Listener {
        @Override
        public void onTntPrimed(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
            for (net.zaminmc.torch.server.tnt.TntSystem.Listener listener : tntListeners) {
                listener.onTntPrimed(tnt);
            }
        }

        @Override
        public void onTntMoved(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
            for (net.zaminmc.torch.server.tnt.TntSystem.Listener listener : tntListeners) {
                listener.onTntMoved(tnt);
            }
        }

        @Override
        public void onTntRemoved(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
            for (net.zaminmc.torch.server.tnt.TntSystem.Listener listener : tntListeners) {
                listener.onTntRemoved(tnt);
            }
        }
    }

    /**
     * A world-time change (the /time command, the periodic cycle sync) in one
     * dimension. The reference (MinecraftServer lines 547-558) walks every
     * world server and, every 20 ticks, sends its WorldTimeS2CPacket to the
     * players in that dimension only — the dimension tag rides so the wire
     * fan-out can filter per connection.
     */
    public interface TimeListener {
        void onTimeChanged(int dimension, long totalTicks, long timeOfDay);
    }

    /** A sign's text changed (the Update Sign 0x33 fan-out to viewers). */
    public interface SignListener {
        void onSignChanged(BlockPosition position, String[] lines);
    }

    /** Registers the sign-text observer (the protocol adapter's wire fan-out). */
    public void addSignListener(SignListener listener) {
        signListeners.add(listener);
    }

    /**
     * A chunk column's light changed this tick (§475): the wire transport is a
     * chunk re-send (protocol 47 has no light-only packet), deduplicated per
     * tick by the light engine's queue. Positions are chunk columns.
     */
    public interface RelightListener {
        void onChunkRelit(ChunkPosition position);
    }

    /** Registers an internal time observer (e.g. the protocol adapter's sync). */
    public void addTimeListener(TimeListener listener) {
        timeListeners.add(listener);
    }

    /** Registers the chunk-relight observer (the protocol adapter's resend path). */
    public void addRelightListener(RelightListener listener) {
        relightListeners.add(listener);
    }

    /** The simulation-owned experience-orb system (present once the world is up). */
    public ExperienceOrbManager experienceOrbs() {
        return experienceOrbs;
    }

    /** A player's experience state changed (orb pickup, award, death reset). */
    public interface ExperienceListener {
        void onExperienceChanged(PlayerSession player);
    }

    /** Registers the experience observer (the protocol adapter's Set Experience sync). */
    public void addExperienceListener(ExperienceListener listener) {
        experienceListeners.add(listener);
    }

    private void publishExperienceChanged(PlayerSession player) {
        for (ExperienceListener listener : experienceListeners) {
            listener.onExperienceChanged(player);
        }
    }

    /** Registers an internal orb observer (e.g. the protocol adapter's sync). */
    public void addOrbListener(ExperienceOrbManager.Listener listener) {
        orbListeners.add(listener);
    }

    /** The orb lifecycle fan-out (the adapter renders Spawn Orb / Collect / Destroy). */
    private final class OrbEventDispatch implements ExperienceOrbManager.Listener {
        @Override
        public void onOrbSpawned(ExperienceOrbEntity orb) {
            for (ExperienceOrbManager.Listener listener : orbListeners) {
                listener.onOrbSpawned(orb);
            }
        }

        @Override
        public void onOrbMoved(ExperienceOrbEntity orb) {
            for (ExperienceOrbManager.Listener listener : orbListeners) {
                listener.onOrbMoved(orb);
            }
        }

        @Override
        public void onOrbCollected(ExperienceOrbEntity orb, PlayerSession collector) {
            for (ExperienceOrbManager.Listener listener : orbListeners) {
                listener.onOrbCollected(orb, collector);
            }
            publishExperienceChanged(collector); // the bar moves with the chime
        }

        @Override
        public void onOrbRemoved(ExperienceOrbEntity orb, String reason) {
            for (ExperienceOrbManager.Listener listener : orbListeners) {
                listener.onOrbRemoved(orb, reason);
            }
        }
    }

    private void publishChunkRelit(ChunkPosition position) {
        for (RelightListener listener : relightListeners) {
            listener.onChunkRelit(position);
        }
    }

    private void publishTimeChanged() {
        // The reference's per-world walk: every dimension's players hear their
        // own world's clock (the nether's clocks advance independently — the
        // sleep skip and the doDaylightCycle walk are overworld-only arms).
        for (TimeListener listener : timeListeners) {
            listener.onTimeChanged(0, world.totalTicks(), world.timeOfDay());
        }
        EngineWorld nether = netherWorld;
        if (nether != null) {
            for (TimeListener listener : timeListeners) {
                listener.onTimeChanged(-1, nether.totalTicks(), nether.timeOfDay());
            }
        }
    }

    /** An inventory content change that observers must re-sync to the client. */
    public interface InventoryListener {
        void onInventoryChanged(PlayerSession player);
    }

    /** Registers an internal inventory observer (e.g. the protocol adapter's sync). */
    public void addInventoryListener(InventoryListener listener) {
        inventoryListeners.add(listener);
    }

    /** A per-tick furnace-window view: the adapter diffs serials and syncs the wire. */
    public interface FurnaceViewListener {
        void onFurnaceViewTick(PlayerSession viewer, net.zaminmc.torch.block.BlockPosition position,
                               FurnaceBlockEntity furnace);
    }

    /** Registers an internal furnace-view observer (e.g. the protocol adapter's sync). */
    public void addFurnaceViewListener(FurnaceViewListener listener) {
        furnaceViewListeners.add(listener);
    }

    /** A per-tick brewing-window view: the adapter diffs the timer + serials. */
    public interface BrewingViewListener {
        void onBrewingViewTick(PlayerSession viewer,
                               net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand);
    }

    private final java.util.List<BrewingViewListener> brewingViewListeners = new java.util.ArrayList<>();

    /** Registers an internal brewing-view observer (the protocol adapter's sync). */
    public void addBrewingViewListener(BrewingViewListener listener) {
        brewingViewListeners.add(listener);
    }

    /**
     * Fans per-tick brewing-window views out to the open viewer(s): the
     * adapter diffs the stand's timer (the window's property 0) and the
     * slots serial, syncing the wire. Tick-thread context.
     */
    private void tickBrewingViewers() {
        for (PlayerSession session : players.all()) {
            if (session.openContainerKind() != PlayerSession.ContainerKind.BREWING_STAND
                    || session.openContainerWindowId() < 0) {
                continue;
            }
            var position = session.openContainerPosition();
            var stand = position == null ? null : brewingStands.peek(position);
            if (stand == null) {
                continue;
            }
            for (BrewingViewListener listener : brewingViewListeners) {
                listener.onBrewingViewTick(session, stand);
            }
        }
    }

    /** A per-tick beacon-window view: the adapter diffs the properties. */
    public interface BeaconViewListener {
        void onBeaconViewTick(PlayerSession viewer,
                              net.zaminmc.torch.server.beacon.BeaconBlockEntity beacon);
    }

    private final java.util.List<BeaconViewListener> beaconViewListeners =
            new java.util.ArrayList<>();

    public void addBeaconViewListener(BeaconViewListener listener) {
        beaconViewListeners.add(listener);
    }

    /** Fans the beacon GUI view out to the open viewer(s). Tick-thread context. */
    private void tickBeaconViewers() {
        for (PlayerSession session : players.all()) {
            if (session.openContainerKind() != PlayerSession.ContainerKind.BEACON
                    || session.openContainerWindowId() < 0) {
                continue;
            }
            var position = session.openContainerPosition();
            var beacon = position == null ? null : beaconSystem.peek(position);
            if (beacon == null) {
                continue;
            }
            for (BeaconViewListener listener : beaconViewListeners) {
                listener.onBeaconViewTick(session, beacon);
            }
        }
    }

    /** @return the brewing stand subsystem (the protocol adapter's wire sync). */
    public net.zaminmc.torch.server.brewing.BrewingStandSystem brewingStands() {
        return brewingStands;
    }

    /** A per-tick enchanting-window view: the adapter diffs revision + properties. */
    public interface EnchantingViewListener {
        void onEnchantingViewTick(PlayerSession viewer,
                                  net.zaminmc.torch.server.enchantment.EnchantingMenu menu);
    }

    private final java.util.List<EnchantingViewListener> enchantingViewListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an internal enchanting-view observer (the protocol adapter's sync). */
    public void addEnchantingViewListener(EnchantingViewListener listener) {
        enchantingViewListeners.add(listener);
    }

    /** Fans per-tick furnace-window views out to the open viewer(s). Tick-thread context. */
    private void tickFurnaceViewers() {
        for (PlayerSession session : players.all()) {
            if (session.openContainerKind() != PlayerSession.ContainerKind.FURNACE
                    || session.openContainerWindowId() < 0) {
                continue;
            }
            var position = session.openContainerPosition();
            FurnaceBlockEntity furnace = position == null ? null : furnaceManager.peek(position);
            if (furnace == null) {
                continue;
            }
            for (FurnaceViewListener listener : furnaceViewListeners) {
                listener.onFurnaceViewTick(session, position, furnace);
            }
        }
    }

    /**
     * Fans per-tick enchanting-window views out to the open viewer, after the
     * reference {@code isValid} walk: the table must still stand and the
     * player must remain within squared distance 64 of its center — a stale
     * window closes with the vanilla drop (the tick the gate turns false).
     * Tick-thread context.
     */
    private void tickEnchantingViewers() {
        for (PlayerSession session : players.all()) {
            if (session.openContainerKind() != PlayerSession.ContainerKind.ENCHANTING_TABLE
                    || session.openContainerWindowId() < 0) {
                continue;
            }
            var menu = session.enchantingMenu();
            if (menu == null) {
                continue;
            }
            var position = session.position();
            if (!menu.stillValid(world::getBlock, position.x(), position.y(), position.z())) {
                closeEnchantingMenuOnTick(session);
                publishInventoryChanged(session);
                continue;
            }
            for (EnchantingViewListener listener : enchantingViewListeners) {
                listener.onEnchantingViewTick(session, menu);
            }
        }
    }

    /**
     * The vanilla close of an enchanting window: the menu's two slots DROP to
     * the world at the player (the reference {@code close} walk), then the
     * window state clears. Tick-thread context.
     */
    private void closeEnchantingMenuOnTick(PlayerSession session) {
        var menu = session.enchantingMenu();
        if (menu != null) {
            for (net.zaminmc.torch.item.ItemStack stack : menu.closeDrop()) {
                if (!stack.isEmpty()) {
                    throwFromPlayer(session, stack);
                }
            }
            session.setEnchantingMenu(null);
        }
        session.closeContainerWindow();
    }

    /** The simulation-owned furnace block entities (present once the world is up). */
    public FurnaceManager furnaces() {
        return furnaceManager;
    }

    /** The simulation-owned spawner block entities (present once the world is up). */
    public net.zaminmc.torch.server.spawner.SpawnerManager spawners() {
        return spawners;
    }

    /** The simulation-owned chest block entities (present once the world is up). */
    public ChestManager chests() {
        return chestManager;
    }

    /** Observers of the survival body (health sync, death, respawn anchors). */
    public interface SurvivalListener {
        /** A body value changed (health, food, saturation) — re-sync the client. */
        void onBodyChanged(PlayerSession player);

        /** The player died: the adapter tells the client (combat event). */
        void onDied(PlayerSession player);

        /** The player respawned at spawn: the adapter re-anchors the wire. */
        void onRespawned(PlayerSession player, net.zaminmc.torch.util.Position spawn);

        /**
         * The player crossed dimensions (the changeDimension walk's wire
         * arm): the adapter sends the respawn sequence re-pointed at the
         * destination dimension (0 the overworld, -1 the nether).
         */
        default void onDimensionChanged(PlayerSession player,
                                        net.zaminmc.torch.util.Position destination,
                                        int dimension) {
        }

        /** The player took a melee hit: the hurt flash rides the entity status. */
        void onPlayerHurt(PlayerSession player);

        /** The player was knocked back: the victim's client simulates the impulse. */
        void onKnockback(PlayerSession player, double vx, double vy, double vz);

        /**
         * The player's posture (sneak/sprint) changed: observers need the
         * living-flags metadata re-broadcast (crouch 0x02, sprint 0x10).
         * Default no-op so older listeners stay source-compatible.
         */
        default void onPostureChanged(PlayerSession player) {
        }

        /**
         * The exit fountain's arm (Slice 64 — the reference's
         * ServerPlayerEntity.changeDimension lines 446-449: the LEAVE_THE_END
         * walk removes the body from the End world before the GameEvent-4
         * credits): the adapter makes the mover invisible to every other
         * observer (the removeEntity wire) — the body stays "removed from
         * the world" until the client's respawn confirmation.
         */
        default void onEndCredits(PlayerSession player) {
        }
    }

    /** Registers an internal survival observer (e.g. the protocol adapter's sync). */
    public void addSurvivalListener(SurvivalListener listener) {
        survivalListeners.add(listener);
    }

    /**
     * Registers the potion-effect observer (the protocol adapter's Entity
     * Effect 0x1D / Remove Entity Effect 0x1E fan-out — the reference's
     * ServerPlayerEntity overrides: only the affected player's own client).
     */
    public void addPotionListener(net.zaminmc.torch.server.potion.PotionSystem.Listener listener) {
        if (potionSystem == null) {
            throw new IllegalStateException("the potion system boots with the world");
        }
        potionSystem.addListener(listener);
    }

    private void publishPostureChanged(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onPostureChanged(player);
        }
    }

    private void publishEndCredits(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onEndCredits(player);
        }
    }

    private void publishBodyChanged(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onBodyChanged(player);
        }
    }

    private void publishDied(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onDied(player);
        }
    }

    private void publishRespawned(PlayerSession player) {
        publishRespawned(player, world.spawnPosition());
    }

    /** The re-anchor event with an explicit destination (teleports, respawns). */
    private void publishRespawned(PlayerSession player, Position destination) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onRespawned(player, destination);
        }
    }

    // ------------------------------------------------------------------ survival body

    /**
     * The per-tick survival body simulation for every playing player: the
     * server-side eat timer, the historical food economy (exhaustion points
     * drain saturation then hunger; regen at food &gt;= 18 costs exhaustion;
     * starvation on easy floors at 10 hearts) and landing fall damage.
     * Tick-thread context.
     */
    private void tickPlayerBodies() {
        for (PlayerSession session : players.all()) {
            if (session.state() != PlayerState.PLAYING || session.dead()) {
                continue;
            }
            // The credits body is removed from the world (Slice 64 — the
            // reference's removeEntity before the GameEvent-4 walk): no
            // simulation at all until the client's respawn confirmation —
            // no food economy, no breath, no portal arms, no stats clock.
            if (session.leavingTheEnd()) {
                continue;
            }
            session.tickHurtInvulnerability();
            session.tickGrace();
            // The stat clocks (the stats slice — ServerPlayerEntity.tick's
            // own pair): the play time counts ticks (the client's formatter
            // divides by 20) and the since-death clock accumulates until
            // the next death resets it.
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.MINUTES_PLAYED, 1);
            session.stats().tickTimeSinceDeath();
            // The vanilla void: below the kill plane the out-of-world damage
            // lands every tick (the historical outOfWorld rate) until death —
            // and it pierces creative invulnerability, the historical rule.
            if (session.position().y() < MobEntity.VOID_KILL_Y) {
                damageOnTick(session, MobEntity.VOID_DAMAGE_PER_TICK, true,
                        "fell out of the world", 0.0f,
                        net.zaminmc.torch.event.DamageCause.OUT_OF_WORLD);
            }
            tickEating(session);
            tickDrinking(session); // the potion's 32-tick drink (Slice 11b)
            potionSystem.tick(session); // the effect stores' countdown (Slice 11b)
            tickBowCharge(session);
            tickFoodEconomy(session);
            tickLanding(session);
            tickBreath(session);
            tickPortalStand(session);
            tickFireBody(session);
            tickCactusContact(session);
            // The ladder catch: a body on a ladder accumulates no fall
            // distance, so the descent never rounds into landing damage.
            if (ladderAt(session.position().x(), session.position().y(),
                    session.position().z())) {
                session.resetFallDistance();
            }
            // The fluid catch: a body in water accumulates no fall distance
            // (the historical rule — vanilla zeroes fallDistance while the
            // bounding box touches water). Without it the whole in-water
            // descent is spent as landing damage the moment the body steps
            // onto the shore — the "unknown damage coming out of water"
            // real-client regression class.
            if (fluidAt(session.position().x(), session.position().y(),
                    session.position().z())
                    || fluidAt(session.position().x(),
                            session.position().y() + 1.0, session.position().z())) {
                session.resetFallDistance();
            }
            // The movement-exhaustion ledger (the vanilla per-meter rates,
            // reference/1.8.8 PlayerEntity.tickNonRidingMovementRelatedStats):
            // swim 0.015/m, sprint 0.1/m, walk 0.01/m — the cm-rounded meter
            // count times the rate — plus the jump charges (0.2, 0.8
            // sprinting). Climbing charges nothing (the historical arm).
            tickMovementExhaustion(session);
        }
    }

    /**
     * The per-meter exhaustion charges from the body's real displacement
     * (the vanilla addFatigue sites); the invulnerable modes are exempt
     * (the vanilla abilities.invulnerable gate).
     * Tick-thread context.
     */
    private void tickMovementExhaustion(PlayerSession session) {
        if (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR) {
            return;
        }
        Position at = session.position();
        if (session.bodyPrevValid()) {
            double dx = at.x() - session.bodyPrevX();
            double dy = at.y() - session.bodyPrevY();
            double dz = at.z() - session.bodyPrevZ();
            int cm = (int) Math.round(Math.sqrt(dx * dx + dz * dz) * 100.0);
            if (cm > 0) {
                if (fluidAt(at.x(), at.y(), at.z())) {
                    session.addExhaustion(0.015F * cm * 0.01F); // the swim arm
                } else if (session.onGround()) {
                    // The walk/sprint split; the ladder arm charges nothing
                    // and neither does the airborne band (the flown arm).
                    boolean onLadder = ladderAt(at.x(), at.y(), at.z());
                    if (!onLadder && session.sprinting()) {
                        session.addExhaustion(0.099999994F * cm * 0.01F);
                    } else if (!onLadder) {
                        session.addExhaustion(0.01F * cm * 0.01F);
                    }
                }
            }
            // The jump arm: the ground left under rising motion.
            if (dy > 0 && session.bodyPrevOnGround() && !session.onGround()) {
                session.addExhaustion(session.sprinting() ? 0.8F : 0.2F);
            }
        }
        session.captureBodyDelta(at.x(), at.y(), at.z(), session.onGround());
    }

    /** The bow's draw clock: charge advances while the use gesture holds
     * (the test freeze skips the advance — a load-slowed suite cannot slip
     * extra ticks into a pinned draw). */
    private void tickBowCharge(PlayerSession session) {
        if (session.bowCharging() && !session.bowChargeFrozenForTest()) {
            session.advanceBowCharge();
        }
    }

    /** The 32-tick server-side eat timer: completion consumes and nourishes. */
    private void tickEating(PlayerSession session) {
        if (!session.eating()) {
            return;
        }
        var held = session.inventory().held();
        if (held.isEmpty() || Foods.nutritionOf(held.type(), held.damage()).isEmpty()) {
            session.cancelEating(); // the held item changed mid-eat
            return;
        }
        int ticks = session.advanceEating();
        if (ticks < Foods.EAT_TICKS) {
            // The historical eating feedback: crumbs + the eating sound every
            // four ticks, from the mouth height.
            if (ticks % 4 == 1) {
                Position mouth = mouthPosition(session);
                fxManager.itemShatter(mouth, held);
                fxManager.sound(mouth, "random.eat", 0.5f,
                        0.8f + fxRandom.nextFloat() * 0.2f);
            }
            return;
        }
        session.cancelEating();
        var nutrition = Foods.nutritionOf(held.type(), held.damage()).orElseThrow();
        if (session.food() >= net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD) {
            return; // already full (the start check cannot fully guard the window)
        }
        session.inventory().consumeHeld(1);
        int newFood = Math.min(net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD,
                session.food() + nutrition.foodPoints());
        float newSaturation = Math.min(newFood,
                session.saturation() + nutrition.saturation());
        session.setBody(session.health(), newFood, newSaturation);
        fxManager.sound(mouthPosition(session), "random.burp", 0.5f,
                fxRandom.nextFloat() * 0.1f + 0.9f);
        // The golden apple's own set (GoldenAppleItem.addEatEffects lines
        // 26-40, installed after the burp exactly like the reference's
        // finishUsing order): the absorption on BOTH apples; the notch pair
        // (damage 1) replaces the FoodItem's own pair with Regeneration V
        // 600 + Resistance I and Fire Resistance I 6000; the PLAIN apple
        // falls through to the super call — the FoodItem pair table's own
        // Regeneration II 100 (the reference's setStatusEffect pair).
        boolean goldenApple = held.type().identifier().toString()
                .equals("minecraft:golden_apple");
        boolean notchApple = goldenApple && held.damage() > 0;
        if (goldenApple) {
            potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                    net.zaminmc.torch.server.potion.StatusEffects.ABSORPTION, 2400, 0));
            if (notchApple) {
                potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                        net.zaminmc.torch.server.potion.StatusEffects.REGENERATION, 600, 4));
                potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                        net.zaminmc.torch.server.potion.StatusEffects.RESISTANCE, 6000, 0));
                potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                        net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE, 6000, 0));
            }
        }
        // The pufferfish's eat triple (FishItem.addEatEffects lines 38-47,
        // BEFORE the super call — the reference's own order): the
        // metadata-3 variant on either fish item — Type.byItem reads the
        // metadata of any FishItem, cooked or raw — always installs
        // Poison IV 1200, Hunger III 300, Nausea II 300. The fish items
        // carry no FoodItem pair (no setStatusEffect registration), so the
        // super call below adds nothing on top.
        if (net.zaminmc.torch.server.item.FishItems.isPufferfish(
                held.type().identifier(), held.damage())) {
            potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                    net.zaminmc.torch.server.potion.StatusEffects.POISON, 1200, 3));
            potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                    net.zaminmc.torch.server.potion.StatusEffects.HUNGER, 300, 2));
            potionSystem.add(session, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                    net.zaminmc.torch.server.potion.StatusEffects.NAUSEA, 300, 1));
        }
        // The FoodItem's own pair (Item.java's setStatusEffect family —
        // FoodItem.addEatEffects lines 41-45): the chance roll, then the
        // duration × 20 install. The notch apple skips it (the reference's
        // own branch structure); the plain golden apple rides it exactly
        // like every other food.
        if (!notchApple) {
            Foods.effectOf(held.type()).ifPresent(pair -> {
                if (fxRandom.nextFloat() < pair.chance()) {
                    potionSystem.add(session,
                            new net.zaminmc.torch.server.potion.StatusEffectInstance(
                                    pair.effect(), pair.durationSeconds() * 20, pair.amplifier()));
                }
            });
        }
        publishInventoryChanged(session);
        publishBodyChanged(session);
    }

    /**
     * The eye height (1.62) — where eat sounds and throw gestures originate.
     */
    private static Position mouthPosition(PlayerSession session) {
        Position p = session.position();
        return new Position(p.x(), p.y() + 1.62, p.z());
    }

    /**
     * The player body's fire clock (the historical EntityPlayer burn rules):
     * standing in fire re-arms the burn and hurts every half second; while
     * merely on fire the damage lands every second; water douses the flame.
     * Creative and spectator bodies are fire-proof (the mode guard).
     * Tick-thread context.
     */
    private void tickFireBody(PlayerSession session) {
        if (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR) {
            return;
        }
        // Fire resistance (Slice 11b — the reference's isOnFireResistant
        // gate over the fire family): the body never arms the burn and
        // takes no fire damage while the effect holds.
        boolean fireResistant = potionSystem.hasEffect(session,
                net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE);
        if (fireResistant) {
            session.extinguish();
            session.resetFireDamageTimer();
            session.tickFire();
            return;
        }
        Position feet = session.position();
        var feetBlock = world.getBlock(new BlockPosition(
                (int) Math.floor(feet.x()), (int) Math.floor(feet.y()),
                (int) Math.floor(feet.z())));
        var bodyBlock = world.getBlock(new BlockPosition(
                (int) Math.floor(feet.x()), (int) Math.floor(feet.y() + 1),
                (int) Math.floor(feet.z())));
        boolean inFire = WorldSolidity.isFire(feetBlock.identifier())
                || WorldSolidity.isFire(bodyBlock.identifier());
        if (inFire) {
            // Standing in the flame re-arms the burn and hurts every half
            // second (the historical in-fire cadence). The enchantment-
            // protection step rides the fire kind (the reference inFire
            // source is a fire source, not an armor bypass).
            session.ignite(net.zaminmc.torch.server.entity.MobEntity.FIRE_TICKS);
            if (session.advanceFireDamageTimer() % 10 == 0) {
                damageOnTick(session,
                        applyEnchantProtection(session, 1.0f, DamageKind.IN_FIRE),
                        "went up in flames", DamageKind.IN_FIRE);
            }
        } else if (session.burning()) {
            // After leaving the flame the residual burn hurts every second;
            // the onFire source carries no exhaustion (the bypass family)
            // but stays a fire source for the protection step.
            if (session.advanceFireDamageTimer() % 20 == 0) {
                damageOnTick(session,
                        applyEnchantProtection(session, 1.0f, DamageKind.ON_FIRE),
                        0.0f, "went up in flames", DamageKind.ON_FIRE);
            }
        }
        // Fluid contact douses the burn (the historical rule).
        if (FluidBlocks.kindOf(feetBlock.identifier()) != null && session.burning()) {
            session.extinguish();
            session.resetFireDamageTimer();
        }
        // The cauldron's douse (CauldronBlock.onEntityCollision lines 67-74):
        // a burning body dipping to the water surface extinguishes and the
        // cauldron spends one level — `minY <= y + (6 + 3*level)/16`.
        if (session.burning()
                && net.zaminmc.torch.server.cauldron.CauldronBlocks.isCauldron(feetBlock)) {
            int level = net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(feetBlock);
            double waterSurface = Math.floor(session.position().y())
                    + (6.0 + 3.0 * level) / 16.0;
            if (level > 0 && session.position().y() <= waterSurface) {
                session.extinguish();
                session.resetFireDamageTimer();
                setCauldronLevel(new BlockPosition(
                        (int) Math.floor(session.position().x()),
                        (int) Math.floor(session.position().y()),
                        (int) Math.floor(session.position().z())), level - 1);
            }
        }
        session.tickFire();
    }

    /**
     * The cactus's contact damage (the historical BlockCactus collision
     * hurt): a body standing on or beside the cactus cell takes the
     * historical 1 damage at the hurt-invulnerability rhythm (10 ticks).
     * Creative and spectator bodies are immune (the mode guard).
     * Tick-thread context.
     */
    private void tickCactusContact(PlayerSession session) {
        if (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR) {
            return;
        }
        Position feet = session.position();
        if (session.advanceCactusTimer() % 10 != 0) {
            return; // the half-second cadence the hurt i-frames impose
        }
        for (int dy = 0; dy <= 1; dy++) {
            var at = world.getBlock(new BlockPosition(
                    (int) Math.floor(feet.x()), (int) Math.floor(feet.y()) + dy,
                    (int) Math.floor(feet.z())));
            if (WorldSolidity.isCactus(at.identifier())
                    || WorldSolidity.isCactus(world.getBlock(new BlockPosition(
                            (int) Math.floor(feet.x()), (int) Math.floor(feet.y()) - 1,
                            (int) Math.floor(feet.z()))).identifier())) {
                damageOnTick(session,
                        applyEnchantProtection(session, 1.0f, DamageKind.CACTUS),
                        "was pricked to death", DamageKind.CACTUS);
                return;
            }
        }
    }

    /**
     * The historical FoodStats loop (reference/1.8.8 HungerManager.tick)
     * across the difficulty floors: the exhaustion point drains saturation
     * first (never the food level on peaceful), regeneration runs at food
     * >= 18 and costs 3 exhaustion per heart, and starvation bites per the
     * difficulty (easy floors at 10 hearts, normal at 1, hard never floors).
     * Tick-thread context.
     */
    private void tickFoodEconomy(PlayerSession session) {
        if (session.gamemode() != GameMode.SURVIVAL
                && session.gamemode() != GameMode.ADVENTURE) {
            return; // creative and spectator bodies carry no hunger
        }
        // The peaceful regen family (Slice 19e — PlayerEntity.tick lines
        // 443-450): a heart a second and a food point a half-second —
        // the difficulty's own mercy, before the exhaustion walk. The
        // naturalRegeneration rule gates BOTH the peaceful pair (Slice
        // 24a — PlayerEntity.tick line 443's own consult).
        if (difficulty == 0 && gameRules.getBoolean("naturalRegeneration")) {
            session.advancePeacefulTick();
            boolean changed = false;
            if (session.peacefulTick() % 20 == 0
                    && session.health() < PlayerSession.MAX_HEALTH) {
                session.setBody(Math.min(PlayerSession.MAX_HEALTH, session.health() + 1),
                        session.food(), session.saturation());
                changed = true;
            }
            if (session.peacefulTick() % 10 == 0 && session.food() < PlayerSession.MAX_FOOD) {
                session.setBody(session.health(),
                        Math.min(PlayerSession.MAX_FOOD, session.food() + 1),
                        session.saturation());
                changed = true;
            }
            if (changed) {
                publishBodyChanged(session);
            }
        }
        if (session.exhaustion() > PlayerSession.EXHAUSTION_COST) {
            session.setExhaustion(session.exhaustion() - PlayerSession.EXHAUSTION_COST);
            if (session.saturation() > 0) {
                session.setBody(session.health(), session.food(),
                        Math.max(0.0f, session.saturation() - 1));
            } else if (difficulty != 0) { // the peaceful arm never drains food
                session.setBody(session.health(), Math.max(0, session.food() - 1),
                        session.saturation());
            }
            publishBodyChanged(session);
        }
        // naturalRegeneration (Slice 24a — HungerManager.tick line 41's
        // own gate): the food>=18 regen arm skips when the rule is off;
        // the starvation arm below still walks.
        if (gameRules.getBoolean("naturalRegeneration")
                && session.food() >= 18 && session.health() < PlayerSession.MAX_HEALTH) {
            session.advanceBodyTimer();
            if (session.bodyTimer() >= PlayerSession.BODY_TIMER_PERIOD) {
                session.resetBodyTimer();
                session.setBody(Math.min(PlayerSession.MAX_HEALTH, session.health() + 1),
                        session.food(), session.saturation());
                session.addExhaustion(3.0f); // the historical regen cost
                publishBodyChanged(session);
            }
        } else if (session.food() <= 0) {
            session.advanceBodyTimer();
            if (session.bodyTimer() >= PlayerSession.BODY_TIMER_PERIOD) {
                session.resetBodyTimer();
                // The vanilla bite condition: easy floors at 10 hearts,
                // normal at 1, hard never floors.
                if (session.health() > 10.0f || difficulty == 3
                        || session.health() > 1.0f && difficulty == 2) {
                    damageOnTick(session, 1.0f, 0.0f, "starved to death",
                            DamageKind.STARVE);
                }
            }
        } else {
            session.resetBodyTimer();
        }
    }

    /** Landing damage: falls beyond three blocks hurt ceil(distance - 3). */
    private void tickLanding(PlayerSession session) {
        if (!session.onGround()) {
            return;
        }
        float distance = session.consumeFallDistance();
        if (distance > PlayerSession.SAFE_FALL_DISTANCE) {
            // A body that lands in fluid takes no fall damage (the water
            // break), and neither does one that lands on a ladder column
            // (the historical ladder catch).
            var feet = session.position().toBlockPosition();
            var at = world.getBlock(feet);
            if (FluidBlocks.kindOf(at.identifier()) == null
                    && !WorldSolidity.isLadder(at.identifier())) {
                // The fall damage carries no exhaustion (the vanilla FALL
                // source's setBypassesArmor zeroes it).
                damageOnTick(session,
                        applyEnchantProtection(session,
                                (float) Math.ceil(distance - PlayerSession.SAFE_FALL_DISTANCE),
                                DamageKind.FALL),
                        0.0f, "hit the ground too hard", DamageKind.FALL);
            }
        }
    }

    /**
     * The portal stand clock (the reference's Entity portal tick,
     * reference/1.8.8 entity/Entity.java lines 282-313): a body whose
     * position block is a nether portal cell accumulates the stand time —
     * the threshold crossing reports the teleport arm (the changeDimension
     * walk lands with the dimension slice, 8b; until then the crossing
     * arms the cooldown and surfaces in the logs). The clock advances for
     * every body every tick — the decay arm rides the same walk.
     * Tick-thread context.
     */
    private void tickPortalStand(PlayerSession session) {
        // The body's OWN dimension decides the world read (the walk moves
        // bodies between worlds; the stand clock reads where the body lives).
        EngineWorld bodyWorld = worldFor(session.dimension());
        BlockType at = bodyWorld.getBlock(new BlockPosition(
                (int) Math.floor(session.position().x()),
                (int) Math.floor(session.position().y()),
                (int) Math.floor(session.position().z())));
        // The end portal's INSTANT arm (Slice 61 — EndPortalBlock
        // .onEntityCollision lines 65-71): no stand clock, no cooldown —
        // the collision itself crosses the body (the rider/vehicle gate:
        // a seated body never crosses).
        if (at.identifier().value().equals("end_portal")
                && session.ridingVehicleId() < 0 && session.ridingMobId() < 0) {
            if (session.dimension() == 1) {
                // The End's own end_portal cell (the exit fountain's — the
                // reference's Entity.changeDimension lines 1703-1706: old 1
                // + new 1 redirects to the overworld; the player overload
                // plays the credits first — ServerPlayerEntity
                // .changeDimension lines 446-449).
                leaveEndOnTick(session);
                return;
            }
            enterEndOnTick(session);
            return;
        }
        boolean inPortal = at.identifier().equals(BuiltinBlocks.NETHER_PORTAL.identifier())
                || at.identifier().equals(BuiltinBlocks.NETHER_PORTAL_Z.identifier());
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (inPortal) {
            // The entry memory (the reference's onPortalCollision derive —
            // a NEW portal cell re-derives the frame offsets and facing).
            var match = PortalFrameBuilder.matchAt(bodyWorld, new BlockPosition(
                    (int) Math.floor(session.position().x()),
                    (int) Math.floor(session.position().y()),
                    (int) Math.floor(session.position().z())));
            session.notePortalEntry(new BlockPosition(
                    (int) Math.floor(session.position().x()),
                    (int) Math.floor(session.position().y()),
                    (int) Math.floor(session.position().z())), match,
                    session.position().x(), session.position().y(), session.position().z());
        }
        if (session.advancePortalClock(inPortal, creative)) {
            // The teleport arm (the reference's Entity.updatePortal →
            // changeDimension): the destination walk is the 8b-iii-b slice —
            // the 8:1 walk, the PortalForcer, the respawn sequence.
            changeDimensionOnTick(session);
        }
    }

    /**
     * The underwater breath clock: air drains while the eye sits in fluid;
     * after the historical 15 seconds the body drowns at 2 damage per second.
     * Creative and spectator bodies hold no breath.
     */
    private void tickBreath(PlayerSession session) {
        if (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR) {
            return;
        }
        Position eye = mouthPosition(session);
        var at = world.getBlock(new BlockPosition(
                (int) Math.floor(eye.x()), (int) Math.floor(eye.y()), (int) Math.floor(eye.z())));
        boolean underwater = FluidBlocks.kindOf(at.identifier()) != null;
        // The Respiration roll (the reference's getRespirationLevel highest
        // over the equipment; the helmet is the only enchantable slot).
        int respiration = EnchantmentHelper.highestLevel(
                Enchantments.RESPIRATION.id, session.inventory().armorStacks());
        if (session.advanceBreath(underwater, respiration, gameplayRandom)) {
            // the DROWN arm: no exhaustion; drown bypasses armor but stays
            // inside the protection step (only "all" applies off-fire)
            damageOnTick(session,
                    applyEnchantProtection(session, 2.0f, DamageKind.DROWN),
                    0.0f, "drowned", DamageKind.DROWN);
        }
    }

    /**
     * The semantic damage entry (fall, starvation, mob melee; player melee is
     * {@link #attackEntity}). Safe from any thread: the application runs on the
     * tick thread.
     */
    public void damage(PlayerSession session, float amount) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> damageOnTick(session, amount));
    }

    /** Historical 1.8 melee reach from the eyes (the survival attack range). */
    static final double MELEE_REACH = 3.5;
    /** Historical attack exhaustion (one swing). */
    private static final float ATTACK_EXHAUSTION = 0.3f;
    /** Fire Aspect pre-set: the reference's {@code setOnFireFor(1)} — one second. */
    private static final int FIRE_ASPECT_PRESET_TICKS = 20;
    /** Fire Aspect burn seconds per level (the reference's {@code setOnFireFor(j * 4)}). */
    private static final int FIRE_ASPECT_SECONDS_PER_LEVEL = 4;
    /** The tick clock the fire seconds ride ({@code setOnFireFor} converts × 20). */
    private static final int TICKS_PER_SECOND = 20;
    // The guardian family (Slice 17a): the hard-difficulty magic bump on the
    // laser (the reference's own +2.0F arm) and the elder's aura constants
    // (mobAiTick's 50-block reach, the Fatigue III / 6000-tick curse, the
    // 1200-tick weaker-row refresh floor, the ghost-curse game state 10).
    private static final float GUARDIAN_MAGIC_HARD_BONUS = 2.0f;
    private static final double ELDER_AURA_RADIUS_SQUARED = 2500.0;
    private static final int ELDER_AURA_DURATION = 6000;
    private static final int ELDER_AURA_AMPLIFIER = 2;
    private static final int ELDER_AURA_REFRESH_BELOW = 1200;
    private static final int GAME_STATE_ELDER_GUARDIAN_CURSE = 10;
    // The End exit's win screen (Slice 64 — GameEventS2CPacket(4, 0.0F),
    // ServerPlayerEntity.changeDimension line 449): the credits the client
    // plays while the body waits removed-from-the-world for the respawn
    // confirmation.
    private static final int GAME_STATE_END_CREDITS = 4;
    // The ender pearl's family (Slice 17c): the 5% endermite passenger
    // (EnderPearlEntity.onCollision line 53) and the flat 5.0 fall toll
    // (line 65 — the enchantment walk only, the vanilla FALL source's own
    // bypass-armor exemption).
    private static final float ENDER_PEARL_ENDERMITE_CHANCE = 0.05f;
    private static final float ENDER_PEARL_FALL_DAMAGE = 5.0f;
    // The bottle o' enchanting's splash event (Slice 65 —
    // ExperienceBottleEntity.onCollision line 30: doEvent(2002, pos, 0)
    // — the splash-particle Effect with the value 0).
    private static final int WORLD_EVENT_BOTTLE_SPLASH = 2002;

    /**
     * A player attacked an entity (Use Entity 0x02, mouse=1): validates reach,
     * computes damage from the held item (historical values), applies
     * knockback along the attacker's look, and charges attack exhaustion.
     * Safe from any thread; the application runs on the tick thread.
     */
    public void attackEntity(PlayerSession attacker, int targetEntityId) {
        Objects.requireNonNull(attacker, "attacker");
        ticker.submit(() -> {
            if (attacker.state() != PlayerState.PLAYING || attacker.dead()
                    || mobManager == null) {
                return;
            }
            // Vehicles take the punch first (the breakable bodies).
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle =
                    vehicleManager != null ? vehicleManager.byId(targetEntityId) : null;
            if (vehicle != null) {
                Position eye = attacker.position();
                Position target = vehicle.position();
                double dx = target.x() - eye.x();
                double dy = target.y() - eye.y();
                double dz = target.z() - eye.z();
                if (Math.sqrt(dx * dx + dz * dz) > MELEE_REACH + 0.7
                        || dy < -2.0 || dy > 4.0) {
                    return; // out of reach: the server-side refusal
                }
                vehicleManager.hurt(vehicle, 1.0f);
                attacker.addExhaustion(ATTACK_EXHAUSTION);
                return;
            }
            // The fireball deflection (Slice 25b — ProjectileEntity.takeDamage
            // lines 248-273): the player's punch on a fireball redirects it —
            // the velocity becomes the attacker's look vector, the
            // acceleration look*0.1, and the puncher becomes the new shooter
            // (the deflected blast credits the player; a fireball past its
            // 25-tick window can now strike the ghast that loosed it).
            if (projectileManager != null) {
                net.zaminmc.torch.server.entity.projectile.ProjectileEntity fireball =
                        projectileManager.byId(targetEntityId);
                if (fireball != null) {
                    Position eye = attacker.position();
                    Position target = fireball.position();
                    double dx = target.x() - eye.x();
                    double dy = target.y() - eye.y();
                    double dz = target.z() - eye.z();
                    if (Math.sqrt(dx * dx + dz * dz) > MELEE_REACH + 1.0
                            || dy < -2.0 || dy > 4.0) {
                        return; // out of reach (the 1.0 pick-radius window)
                    }
                    // getLookVector: the normalized look from yaw/pitch (the
                    // same swapped-trig walk the launches carry).
                    double yawRadians = Math.toRadians(attacker.rotation().yaw());
                    double pitchRadians = Math.toRadians(attacker.rotation().pitch());
                    double lookX = -Math.sin(yawRadians) * Math.cos(pitchRadians);
                    double lookY = -Math.sin(pitchRadians);
                    double lookZ = Math.cos(yawRadians) * Math.cos(pitchRadians);
                    if (projectileManager.deflect(fireball, attacker.engineEntityId(),
                            lookX, lookY, lookZ)) {
                        attacker.addExhaustion(ATTACK_EXHAUSTION); // the landed attack's own cost
                    }
                    return; // the fireball family takes no further attack arms
                }
            }
            MobEntity mob = mobManager.byId(targetEntityId);
            if (mob == null || mob.dead()) {
                // The decoration family (Slice 28a/28b): the frame's
                // attack ladder (the item eject on a loaded frame, the
                // break otherwise) and the painting's break drop.
                if (decorations != null) {
                    net.zaminmc.torch.server.decoration.ItemFrame frame =
                            decorations.frameById(targetEntityId);
                    if (frame != null) {
                        double ddx = frame.hangingPosition().x() - attacker.position().x();
                        double ddy = frame.hangingPosition().y() - attacker.position().y();
                        double ddz = frame.hangingPosition().z() - attacker.position().z();
                        double dHorizontal = Math.sqrt(ddx * ddx + ddz * ddz);
                        if (dHorizontal <= MELEE_REACH + 1.0 && ddy > -2.0 && ddy < 4.0) {
                            decorations.attackFrame(frame,
                                    attacker.gamemode() == net.zaminmc.torch.GameMode.CREATIVE,
                                    false);
                            attacker.addExhaustion(ATTACK_EXHAUSTION);
                        }
                        return;
                    }
                    net.zaminmc.torch.server.decoration.Painting painting =
                            decorations.paintingById(targetEntityId);
                    if (painting != null) {
                        double pdx = painting.pos().x() + 0.5 - attacker.position().x();
                        double pdy = painting.pos().y() + 0.5 - attacker.position().y();
                        double pdz = painting.pos().z() + 0.5 - attacker.position().z();
                        double pHorizontal = Math.sqrt(pdx * pdx + pdz * pdz);
                        if (pHorizontal <= MELEE_REACH + 1.0 && pdy > -2.0 && pdy < 4.0) {
                            decorations.attackPainting(painting,
                                    attacker.gamemode() == net.zaminmc.torch.GameMode.CREATIVE);
                            attacker.addExhaustion(ATTACK_EXHAUSTION);
                        }
                        return;
                    }
                }
                // The armor stands (Slice 28c): the melee break ladder —
                // the first hit's five-tick stamp (the wobble), the
                // double-hit break with the stand item + the equipment
                // drops, the creative instant remove.
                if (armorStands != null) {
                    net.zaminmc.torch.server.armorstand.ArmorStand stand =
                            armorStands.byId(targetEntityId);
                    if (stand != null) {
                        double sdx = stand.x() - attacker.position().x();
                        double sdy = stand.y() + 0.9 - attacker.position().y();
                        double sdz = stand.z() - attacker.position().z();
                        double sHorizontal = Math.sqrt(sdx * sdx + sdz * sdz);
                        if (sHorizontal <= MELEE_REACH + 1.0 && sdy > -2.0 && sdy < 4.0) {
                            armorStands.attack(stand,
                                    attacker.gamemode() == net.zaminmc.torch.GameMode.CREATIVE);
                            attacker.addExhaustion(ATTACK_EXHAUSTION);
                        }
                        return;
                    }
                }
                // The ender crystals (Slice 62 — EnderCrystalEntity
                // .takeDamage lines 67-83): ANY landed hit pops the body —
                // no ladder, no i-frames of its own; the melee arm's reach
                // band follows the armor stand's own shape (the 2-wide box
                // at the +1.0 flank).
                if (enderCrystals != null && attacker.dimension() == 1) {
                    net.zaminmc.torch.server.endcrystal.EnderCrystal crystal =
                            enderCrystals.byId(targetEntityId);
                    if (crystal != null) {
                        double cdx = crystal.x() - attacker.position().x();
                        double cdy = crystal.y() + 1.0 - attacker.position().y();
                        double cdz = crystal.z() - attacker.position().z();
                        double cHorizontal = Math.sqrt(cdx * cdx + cdz * cdz);
                        if (cHorizontal <= MELEE_REACH + 1.0 && cdy > -2.0 && cdy < 4.0) {
                            enderCrystals.damage(crystal);
                            attacker.addExhaustion(ATTACK_EXHAUSTION);
                        }
                        return;
                    }
                }
                // The dragon's parts (Slice 63 — the wire's id convention:
                // the client names the PART id, dragonId+1..+7; the part
                // walk is the ONLY way the body takes damage — the plain
                // mob id never answers a hit). takeDamage(EnderDragonPart,
                // ...) lines 458-476: the non-head /4+1, the head-front
                // target reset, and the PLAYER source (melee is always a
                // player's hand here) damaging the body.
                if (attacker.dimension() == 1) {
                    for (MobEntity dragon : mobManager.all()) {
                        if (dragon.type() != net.zaminmc.torch.server.entity.MobType.ENDER_DRAGON
                                || dragon.dimension() != 1) {
                            continue;
                        }
                        var part = dragon.dragonPartById(targetEntityId);
                        if (part == null) {
                            continue;
                        }
                        double pdx = part.x() - attacker.position().x();
                        double pdy = part.y() + part.height / 2.0 - attacker.position().y();
                        double pdz = part.z() - attacker.position().z();
                        double pHorizontal = Math.sqrt(pdx * pdx + pdz * pdz);
                        if (pHorizontal <= MELEE_REACH + part.width * 0.5
                                && pdy > -2.0 && pdy < 4.0) {
                            // PlayerEntity.attack lines 941-1044 over the
                            // part: the sword's damage, the crit gate, then
                            // the part walk.
                            ItemStack held = attacker.inventory().held();
                            float base = net.zaminmc.torch.server.item.Tools.attackDamageOf(
                                    held.type());
                            float wound = dragon.dragonDamagePart(part, base, true, false);
                            if (wound > 0.0f) {
                                mobManager.hurt(dragon, wound,
                                        Math.toDegrees(Math.atan2(-pdx, pdz)),
                                        net.zaminmc.torch.event.DamageCause.MELEE);
                                attacker.addExhaustion(ATTACK_EXHAUSTION);
                            } else {
                                // The refused source still resets the
                                // steering (the walk's own shape) — the
                                // swing exhausts.
                                attacker.addExhaustion(ATTACK_EXHAUSTION);
                            }
                        }
                        return;
                    }
                }
                return; // already gone: nothing to hit
            }
            Position eye = attacker.position();
            Position target = mob.position();
            double dx = target.x() - eye.x();
            double dy = target.y() - eye.y();
            double dz = target.z() - eye.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            // The reach check (the Acid-adopted shape) runs BEFORE the
            // refusal gates so far hits flag (the ladder kicks at 20); the
            // refusal itself still follows for the hard cap.
            boolean creative = attacker.gamemode() == net.zaminmc.torch.GameMode.CREATIVE;
            Position eyePoint = new Position(eye.x(),
                    eye.y() + (attacker.sneaking()
                            ? net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_SNEAKING
                            : net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_STANDING),
                    eye.z());
            double reachDistance = net.zaminmc.torch.server.player.AntiCheat.eyeToBoxDistance(
                    eyePoint, target, mob.type().width, mob.type().height);
            if (reachDistance > net.zaminmc.torch.server.player.AntiCheat.maxReach(creative)) {
                attacker.violations().addReachViolation();
                LOGGER.warning(() -> "Reach violation for " + attacker.name() + " ("
                        + String.format(java.util.Locale.ROOT, "%.2f", reachDistance)
                        + " blocks, vl "
                        + String.format(java.util.Locale.ROOT, "%.0f",
                        attacker.violations().reachViolations()) + ")");
                if (attacker.violations().reachViolations()
                        >= net.zaminmc.torch.server.player.AntiCheat.REACH_KICK_VIOLATIONS) {
                    attacker.link().kick("Reach");
                }
                return; // the flag and the refusal ride together
            }
            if (horizontal > MELEE_REACH + mob.type().width * 0.5
                    || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            ItemStack held = attacker.inventory().held();
            float base = net.zaminmc.torch.server.item.Tools.attackDamageOf(held.type());
            // The vanilla crit gate (the reference's flag, lines 959-965):
            // falling with accumulated fall distance, airborne, not climbing,
            // not in water, not blind, unmounted, on a living target. The
            // blindness arm reads false until the potion slice exists (no
            // status-effect state yet — structurally the no-effect read).
            boolean crit = attacker.fallDistance() > 0.0F
                    && !attacker.onGround()
                    && !ladderAt(attacker.position().x(), attacker.position().y(),
                            attacker.position().z())
                    && !(fluidAt(attacker.position().x(), attacker.position().y(),
                            attacker.position().z())
                            || fluidAt(attacker.position().x(),
                                    attacker.position().y() + 1.0,
                                    attacker.position().z()))
                    && !attacker.ridingAny();
            // The multiply rides the BASE damage only, before the enchantment
            // family joins (the reference's `f *= 1.5F` then `f += f1`,
            // lines 966-970).
            if (crit && base > 0.0F) {
                base *= 1.5F;
            }
            float enchantDamage = EnchantmentHelper.modifyDamage(held,
                    mob.type().damageCategory());
            float damage = base + enchantDamage;
            // The reference attack walk (reference/1.8.8 PlayerEntity.attack
            // lines 941-1046): the damage-family enchantments ride the mob's
            // damage category (Smite on the undead, Bane on the spider), the
            // Knockback level joins and sprinting adds one more level.
            int knockback = EnchantmentHelper.knockbackLevel(held);
            if (attacker.sprinting()) {
                knockback++;
            }
            // Fire Aspect pre-set: one second before the hit (the reference's
            // flag1 arm) — a landed hit re-arms to level * 4 seconds, a
            // refused one extinguishes the pre-set (the legacy quirk).
            int fireLevel = EnchantmentHelper.fireAspectLevel(held);
            boolean preIgnited = false;
            if (fireLevel > 0 && !mob.burning()) {
                preIgnited = true;
                mob.ignite(FIRE_ASPECT_PRESET_TICKS);
            }
            // Knockback direction: attacker -> mob (the historical feel).
            double kbYaw = Math.toDegrees(Math.atan2(-dx, dz));
            // The killer's Looting rides the death loot walk (the reference's
            // die() reading getLootingLevel off the killer entity).
            mob.setLootingLevel(EnchantmentHelper.lootingLevel(held));
            // The enderman's player-hand marker (Slice 19d — takeDamage
            // lines 291-296): the melee wound's own anger carrier; the
            // creative hand calms instead (the reference's own branch).
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.ENDERMAN) {
                if (attacker.gamemode() == GameMode.CREATIVE) {
                    mob.setEndermanAngry(false);
                } else {
                    mob.setEndermanAttackedByPlayer(true);
                }
            }
            boolean landed = mobManager.hurt(mob, damage, kbYaw);
            if (landed) {
                // The wound stats (EntityLivingBase.damageEntity's own rows):
                // the dealt damage counts in tenths and the body remembers
                // its player attacker for the death's kill credit.
                attacker.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.DAMAGE_DEALT,
                        Math.round(damage * 10.0f));
                mob.setPlayerKiller(attacker);
                // LivingEntity.takeDamage lines 678-680: a player's landed
                // wound arms the 100-tick hit window (dropRareItem's gate).
                mob.markPlayerHit();
                // Slice 17a — GuardianEntity.takeDamage's spike reflect: a
                // still body's thorns wound the melee attacker (2.0F with
                // the damage.thorns sound at 0.5 — the reference's own
                // living-source check; the magic and explosive families and
                // a swimming body never spike).
                if (mob.guardianSpikesMeleeAttacker()) {
                    // The attacker's own takeDamage walk (the reference's
                    // livingentity.takeDamage pair — the same tick, the
                    // same walk) with the thorns sound.
                    damageOnTick(attacker, mob.guardianThornsDamage(), true,
                            "was slain by " + titledMobName(mob), 0.0f,
                            net.zaminmc.torch.event.DamageCause.MAGIC);
                    fxManager.sound(attacker.position(), "damage.thorns", 0.5f, 1.0f);
                }
                // Slice 16d — ZombiePigmanEntity.takeDamage's getAngryTo:
                // a player's hit angers the pigman and its whole nearby
                // group (the manager's propagation walk).
                if (mob.type() == net.zaminmc.torch.server.entity.MobType.ZOMBIE_PIGMAN) {
                    mobManager.pigmenBecomeAngryAt(mob, attacker.name());
                }
                // Slice 16b — OwnerHurtGoal: the attacker's standing tamed
                // wolves join the fight on the victim (the owner's own
                // getLastAttackedMob + getLastAttackTime change, the event-site
                // arm).
                mobManager.ownerWolvesJoinFight(attacker.name(), mob);
                // The knockback-enchantment extra rides the attacker's look
                // yaw (the reference's addVelocity arm), and the attacker's
                // motion decays to 60% with the sprint wiped (lines 983-992).
                if (knockback > 0) {
                    double yawRadians = Math.toRadians(attacker.rotation().yaw());
                    mob.addVelocity(
                            -Math.sin(yawRadians) * knockback * 0.5,
                            0.1,
                            Math.cos(yawRadians) * knockback * 0.5);
                    attacker.setMotion(attacker.motionX() * 0.6, attacker.motionY(),
                            attacker.motionZ() * 0.6);
                    attacker.setSprinting(false);
                }
                // The landed hit re-arms the pre-set to level * 4 seconds
                // (the target's own Fire Protection would shorten it; mobs
                // carry no equipment, the historical bare-mob read).
                if (fireLevel > 0) {
                    mob.ignite(fireLevel * FIRE_ASPECT_SECONDS_PER_LEVEL * TICKS_PER_SECOND);
                }
                // The crit bursts ride the landed hit (the reference's
                // addCritParticles on the crit flag, lines 1002-1004, and the
                // enchanted-hit burst on f1 > 0, lines 1006-1008 — both
                // broadcast EntityAnimationS2CPacket over the attacker's
                // tracking set).
                if (crit) {
                    publishMobAnimation(mob, ANIMATION_CRIT);
                }
                if (enchantDamage > 0.0F) {
                    publishMobAnimation(mob, ANIMATION_MAGIC_CRIT);
                }
            } else if (preIgnited) {
                mob.extinguish(); // the reference's missed-swing quirk
            }
            if (landed) {
                attacker.addExhaustion(ATTACK_EXHAUSTION);
                // Tool durability: the historical wear on a living-entity hit.
                if (net.zaminmc.torch.server.item.Tools.specOf(held.type()).isPresent()) {
                    int wearBefore = held.damage();
                    attacker.inventory().damageHeld(1, gameplayRandom);
                    // The broken-tool stat (ItemStack.damageItem's own row):
                    // the wear passing the durability empties the slot.
                    if (attacker.inventory().held().isEmpty()
                            && wearBefore + 1 >= held.type().maxDurability()) {
                        attacker.stats().increment(
                                net.zaminmc.torch.server.stat.EngineStats.breakItemOf(
                                        held.type().identifier()), 1);
                    }
                    publishInventoryChanged(attacker);
                }
            }
        });
    }

    /**
     * A player attacked another player (Use Entity 0x02, mouse=1, resolved
     * through the attacker's observer id space): the same melee verdicts as
     * the mob path — reach, held-item damage, exhaustion, tool wear — plus
     * the historical hurt invulnerability window (a weaker hit inside the
     * window is absorbed; a stronger one out-damages it) and the knockback
     * velocity the victim's own client simulates. The victim's client gets
     * the hurt status, the health re-sync and the velocity; every client
     * that can see the victim gets the hurt flash. Safe from any thread;
     * the application runs on the tick thread.
     */
    public void attackPlayer(PlayerSession attacker, PlayerSession victim) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        ticker.submit(() -> {
            if (!config.pvp() || attacker == victim
                    || attacker.state() != PlayerState.PLAYING || attacker.dead()
                    || victim.state() != PlayerState.PLAYING || victim.dead()) {
                return;
            }
            Position eye = attacker.position();
            Position target = victim.position();
            double dx = target.x() - eye.x();
            double dy = target.y() - eye.y();
            double dz = target.z() - eye.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            // The reach check (the Acid-adopted shape; the PvP victim box is
            // the 0.6 x 1.8 player body) runs before the refusal so far hits
            // flag — the ladder kicks at 20 with the per-second decay.
            boolean creative = attacker.gamemode() == net.zaminmc.torch.GameMode.CREATIVE;
            Position eyePoint = new Position(eye.x(),
                    eye.y() + (attacker.sneaking()
                            ? net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_SNEAKING
                            : net.zaminmc.torch.server.player.AntiCheat.EYE_HEIGHT_STANDING),
                    eye.z());
            double reachDistance = net.zaminmc.torch.server.player.AntiCheat.eyeToBoxDistance(
                    eyePoint, target, 0.6, 1.8);
            if (reachDistance > net.zaminmc.torch.server.player.AntiCheat.maxReach(creative)) {
                attacker.violations().addReachViolation();
                LOGGER.warning(() -> "Reach violation for " + attacker.name() + " against "
                        + victim.name() + " ("
                        + String.format(java.util.Locale.ROOT, "%.2f", reachDistance)
                        + " blocks, vl "
                        + String.format(java.util.Locale.ROOT, "%.0f",
                        attacker.violations().reachViolations()) + ")");
                if (attacker.violations().reachViolations()
                        >= net.zaminmc.torch.server.player.AntiCheat.REACH_KICK_VIOLATIONS) {
                    attacker.link().kick("Reach");
                }
                return; // the flag and the refusal ride together
            }
            // The victim's bounding box adds 0.3 to the reach like a mob's width.
            if (horizontal > MELEE_REACH + 0.3 || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            ItemStack held = attacker.inventory().held();
            float base = net.zaminmc.torch.server.item.Tools.attackDamageOf(held.type());
            // The vanilla crit gate (the reference's flag): falling, airborne,
            // not climbing, not in water, not blind, unmounted — the same
            // arms as the mob path; the target here is a living player.
            boolean crit = attacker.fallDistance() > 0.0F
                    && !attacker.onGround()
                    && !ladderAt(attacker.position().x(), attacker.position().y(),
                            attacker.position().z())
                    && !(fluidAt(attacker.position().x(), attacker.position().y(),
                            attacker.position().z())
                            || fluidAt(attacker.position().x(),
                                    attacker.position().y() + 1.0,
                                    attacker.position().z()))
                    && !attacker.ridingAny();
            if (crit && base > 0.0F) {
                base *= 1.5F;
            }
            // The damage-family enchantments join after the crit multiply
            // (players are the reference's UNDEFINED category — only
            // Sharpness lands on a player), the Knockback level joins and
            // sprinting adds one more level.
            float enchantDamage = EnchantmentHelper.modifyDamage(held, 0);
            float damage = base + enchantDamage;
            int knockback = EnchantmentHelper.knockbackLevel(held);
            if (attacker.sprinting()) {
                knockback++;
            }
            // Fire Aspect pre-set: one second on the victim before the hit,
            // the victim's own Fire Protection shortening the clock (the
            // reference's setOnFireFor equipment read).
            int fireLevel = EnchantmentHelper.fireAspectLevel(held);
            boolean preIgnited = false;
            if (fireLevel > 0 && !victim.burning()) {
                preIgnited = true;
                igniteWithFireProtection(victim, FIRE_ASPECT_PRESET_TICKS / TICKS_PER_SECOND);
            }
            if (victim.hurtInvulnerable()) {
                if (damage <= victim.lastHurtDamage()) {
                    if (preIgnited) {
                        victim.extinguish(); // the absorbed swing undoes the pre-set
                    }
                    return; // absorbed by the hurt window
                }
                damage -= victim.lastHurtDamage(); // the historical out-damage rule
            }
            final float rawDamage = damage;
            damage = applyArmor(victim, damage); // the armor envelope (the 1.8 formula)
            damage = applyEnchantProtection(victim, damage, DamageKind.MELEE); // the protection step
            final float applied = damage;
            victim.beginHurtInvulnerability(rawDamage);
            victim.hurt(damage);
            // The knockback-enchantment extra rides the attacker's look yaw
            // on top of the base impulse (applied in the knockback block
            // below, together with the attacker's 60% decay + sprint wipe).
            double nvxExtra = 0.0;
            double nvyExtra = 0.0;
            double nvzExtra = 0.0;
            if (knockback > 0) {
                double yawRadians = Math.toRadians(attacker.rotation().yaw());
                nvxExtra = -Math.sin(yawRadians) * knockback * 0.5;
                nvyExtra = 0.1;
                nvzExtra = Math.cos(yawRadians) * knockback * 0.5;
            }
            attacker.addExhaustion(ATTACK_EXHAUSTION);
            if (net.zaminmc.torch.server.item.Tools.specOf(held.type()).isPresent()) {
                int wearBefore = held.damage();
                attacker.inventory().damageHeld(1, gameplayRandom);
                // The broken-tool stat (the melee arm's own row).
                if (attacker.inventory().held().isEmpty()
                        && wearBefore + 1 >= held.type().maxDurability()) {
                    attacker.stats().increment(
                            net.zaminmc.torch.server.stat.EngineStats.breakItemOf(
                                    held.type().identifier()), 1);
                }
                publishInventoryChanged(attacker);
            }
            // Knockback — the vanilla applyKnockback (reference/1.8.8
            // LivingEntity lines 778-793): the server-known motion halves on
            // every axis, then the impulse rides the normalized away-
            // direction (the 1e-4 jitter when the attacker stands inside the
            // victim), +0.4 up capped at 0.4. The victim's client owns its
            // physics, so the new motion rides the wire as a velocity set.
            double kbdx = target.x() - eye.x();
            double kbdz = target.z() - eye.z();
            for (double d1 = kbdx, d0 = kbdz; d1 * d1 + d0 * d0 < 1.0E-4; ) {
                d1 = (fxRandom.nextDouble() - fxRandom.nextDouble()) * 0.01;
                d0 = (fxRandom.nextDouble() - fxRandom.nextDouble()) * 0.01;
                kbdx = d1;
                kbdz = d0;
            }
            float f = (float) Math.sqrt(kbdx * kbdx + kbdz * kbdz);
            double nvx = victim.motionX() / 2.0 + kbdx / f * 0.4;
            double nvy = Math.min(victim.motionY() / 2.0 + 0.4, 0.4);
            double nvz = victim.motionZ() / 2.0 + kbdz / f * 0.4;
            // The knockback-enchantment extra (the reference's addVelocity
            // arm) rides the same velocity set; the attacker's motion decays
            // to 60% on the horizontal axes with the sprint wiped.
            nvx += nvxExtra;
            nvy += nvyExtra;
            nvz += nvzExtra;
            if (knockback > 0) {
                attacker.setMotion(attacker.motionX() * 0.6, attacker.motionY(),
                        attacker.motionZ() * 0.6);
                attacker.setSprinting(false);
            }
            // The landed hit re-arms the pre-set to level * 4 seconds.
            if (fireLevel > 0) {
                igniteWithFireProtection(victim,
                        fireLevel * FIRE_ASPECT_SECONDS_PER_LEVEL);
            }
            // The crit bursts ride the landed hit (the reference's
            // addCritParticles / addEnchantedCritParticles order: right after
            // the velocity arm, lines 1002-1008).
            if (crit) {
                publishPlayerAnimation(victim, ANIMATION_CRIT);
            }
            if (enchantDamage > 0.0F) {
                publishPlayerAnimation(victim, ANIMATION_MAGIC_CRIT);
            }
            victim.setMotion(nvx, nvy, nvz);
            publishPlayerHurt(victim);
            publishKnockback(victim, nvx, nvy, nvz);
            // The thorns wildcard rides the landed hit in the reference
            // order (PlayerEntity.attack line 1016: after the crit
            // particles, before the victim's own walk concludes); the
            // attacker's body bites back through the victim's worn pieces.
            applyThornsWildcard(victim, victim.inventory().armorStacks(),
                    new ThornsTarget() {
                        @Override
                        public void takeThornsDamage(int amount, PlayerSession wearer) {
                            damageOnTick(attacker,
                                    applyEnchantProtection(attacker,
                                            applyArmor(attacker, amount),
                                            DamageKind.MELEE),
                                    "was slain by " + wearer.name(), DamageKind.MELEE);
                        }

                        @Override
                        public Position position() {
                            return attacker.position();
                        }
                    });
            if (victim.health() <= 0) {
                dieOnTick(victim, "was slain by " + attacker.name());
            } else {
                publishBodyChanged(victim);
            }
            LOGGER.fine(() -> attacker.name() + " hit " + victim.name()
                    + " for " + applied + " (health " + victim.health() + ")");
        });
    }

    private void publishPlayerHurt(PlayerSession victim) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onPlayerHurt(victim);
        }
    }

    /**
     * The 1.8 armor envelope: a physical hit passes through the equipped
     * pieces (the {@code Armor.reduce} formula), the pieces take their wear
     * and any change re-syncs the client. Environmental damage (fall, drown,
     * void, starvation) never calls here — armor does not absorb it, the
     * historical rule. Tick-thread context.
     */
    private float applyArmor(PlayerSession victim, float damage) {
        int points = victim.inventory().totalArmorPoints();
        float reduced = net.zaminmc.torch.server.item.Armor.reduce(points, damage);
        if (points > 0 && reduced < damage
                && victim.inventory().wearArmor(damage, gameplayRandom)) {
            publishInventoryChanged(victim); // worn or broken pieces re-sync
        }
        return reduced;
    }

    /**
     * The vanilla {@code getDamageAfterEffectsAndEnchantments} enchantment
     * half (reference/1.8.8 LivingEntity lines 879-907): the victim's armor
     * pieces' protection sum — clamped 0..25, then the legacy half-to-full
     * roll — capped at 20 and folded through the {@code (25 - k) / 25}
     * envelope. Unblockable sources skip the whole half (the reference's
     * early return; starvation is the one the engine emits).
     */
    private float applyEnchantProtection(PlayerSession victim, float damage, DamageKind kind) {
        if (kind.isUnblockable()) {
            return damage;
        }
        int k = EnchantmentHelper.modifyProtection(
                victim.inventory().armorStacks(), kind, gameplayRandom);
        if (k > 20) {
            k = 20;
        }
        if (k > 0 && k <= 20) {
            damage = damage * (25 - k) / 25.0F;
        }
        return damage;
    }

    /**
     * The reference {@code Entity.setOnFireFor}: seconds in, the victim's
     * own Fire Protection shortens the tick count
     * ({@code ticks -= floor(ticks * level * 0.15)}), and a shorter clock
     * never lengthens an existing burn ({@code ignite}'s at-least rule).
     */
    private void igniteWithFireProtection(PlayerSession victim, int seconds) {
        int ticks = EnchantmentHelper.modifyOnFireTimer(
                victim.inventory().armorStacks(), seconds * TICKS_PER_SECOND);
        if (ticks > 0) {
            victim.ignite(ticks);
        }
    }

    /**
     * The thorns retaliation target: the living body the wearer's thorns
     * bites back at (the reference wildcard's {@code target} — the melee
     * attacker, the mob, or the arrow's shooter).
     */
    private interface ThornsTarget {
        /** The reference's {@code target.takeDamage(DamageSource.thorns(attacker), amount)}. */
        void takeThornsDamage(int amount, PlayerSession thornsWearer);

        /** The sound position (the reference plays at {@code target}). */
        Position position();
    }

    /**
     * The historical {@code EnchantmentHelper.applyProtectionWildcard}
     * (reference/1.8.8 EnchantmentHelper lines 152-161) over the victim's
     * armor row, the walk the reference fires when a living attacker lands
     * a hit (PlayerEntity.attack line 1016, Entity.damageEntity lines
     * 1943-1947, ArrowEntity line 264): every piece carrying Thorns rolls
     * the 15%-per-level proc independently (ThornsEnchantment
     * applyProtectionWildcard lines 38-53); a proc deals the roll damage
     * (1-4, or level-10 past level 10) through the target's own damage
     * walk — thorns is an ordinary entity source, so armor and the
     * protection family still apply — and plays {@code damage.thorns} at
     * 0.5F/1.0F; the wear (3 on a proc, 1 on a miss, accumulating per
     * visited piece) rides the FIRST thorns stack
     * ({@code getEquipmentWithEnchantment}). The row is captured by the
     * caller: the reference fires the wildcard in the attacker's walk
     * before the victim's takeDamage runs, so a killing blow still
     * retaliates through the worn pieces. Mob victims wear no enchanted
     * armor yet — the walk reads only the player rows, the structural zero
     * the ledger records (the same shape as the crit walk's blindness arm).
     * Tick-thread context.
     */
    private void applyThornsWildcard(PlayerSession victim, ItemStack[] armorRow,
                                     ThornsTarget target) {
        ItemStack first = EnchantmentHelper.equipmentWithEnchantment(
                Enchantments.THORNS.id, armorRow);
        int wear = 0;
        int firstSlot = -1;
        if (first != null) {
            for (int i = 0; i < armorRow.length; i++) {
                if (armorRow[i] == first) {
                    firstSlot = i;
                    break;
                }
            }
        }
        for (ItemStack piece : armorRow) {
            int level = EnchantmentHelper.level(piece, Enchantments.THORNS.id);
            if (level <= 0) {
                continue;
            }
            if (EnchantmentHelper.thornsShouldDamage(level, gameplayRandom)) {
                wear += 3;
                target.takeThornsDamage(
                        EnchantmentHelper.thornsDamage(level, gameplayRandom), victim);
                fxManager.sound(target.position(), "damage.thorns", 0.5f, 1.0f);
            } else {
                wear += 1;
            }
        }
        if (first != null && wear > 0 && firstSlot >= 0
                && victim.gamemode() != GameMode.CREATIVE) {
            if (victim.inventory().wearArmorStack(firstSlot, wear, gameplayRandom)) {
                publishInventoryChanged(victim); // worn or broken pieces re-sync
            }
        }
    }

    private void publishKnockback(PlayerSession victim, double vx, double vy, double vz) {
        // The knockback flight opens the guard's grace window: the client's
        // next proposals ride the launch burst legitimately.
        victim.setGraceTicks(10);
        for (SurvivalListener listener : survivalListeners) {
            listener.onKnockback(victim, vx, vy, vz);
        }
    }

    private void damageOnTick(PlayerSession session, float amount) {
        damageOnTick(session, amount, false, null, 0.3f,
                net.zaminmc.torch.event.DamageCause.MELEE);
    }

    private void damageOnTick(PlayerSession session, float amount, String causeMessage,
                              DamageKind kind) {
        damageOnTick(session, amount, false, causeMessage, 0.3f, publicCause(kind));
    }

    private void damageOnTick(PlayerSession session, float amount, boolean bypassProtection,
                              String causeMessage, DamageKind kind) {
        damageOnTick(session, amount, bypassProtection, causeMessage, 0.3f, publicCause(kind));
    }

    /**
     * The exhaustion-carrying entry: {@code exhaustionCharge} is the vanilla
     * {@code source.getExhaustion()} — 0.3 for the ordinary sources (melee,
     * projectile, explosion, in-fire, cactus), 0.0 for the bypass family
     * (fall, drown, starve, the on-fire residual, out-of-world).
     */
    private void damageOnTick(PlayerSession session, float amount, float exhaustionCharge,
                              String causeMessage, DamageKind kind) {
        damageOnTick(session, amount, false, causeMessage, exhaustionCharge, publicCause(kind));
    }

    /**
     * The semantic damage entry, with the mode guard: creative and spectator
     * bodies are invulnerable (the historical rule) — the only bypass is the
     * void, which consumes even creative bodies past the kill plane. The
     * public PlayerDamageEvent (§27.7) gates the walk after the guards: a
     * cancelled hit lands nothing (no health loss, no exhaustion, no death
     * check) and the mutable amount drives the rest.
     */
    private void damageOnTick(PlayerSession session, float amount, boolean bypassProtection,
                              String causeMessage, float exhaustionCharge,
                              net.zaminmc.torch.event.DamageCause cause) {
        if (session.dead() || session.state() != PlayerState.PLAYING) {
            return;
        }
        if (!bypassProtection && (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR)) {
            return;
        }
        // §27.7: the public damage gate (the post-armor, post-enchantment
        // value the health walk is about to apply).
        net.zaminmc.torch.event.player.PlayerDamageEvent damageEvent =
                new net.zaminmc.torch.event.player.PlayerDamageEvent(
                        session, amount, cause);
        eventBus.fire(damageEvent);
        if (damageEvent.cancelled()) {
            return;
        }
        amount = damageEvent.amount();
        if (amount <= 0) {
            return;
        }
        session.hurt(amount);
        // The damage stat (EntityLivingBase.damageEntity's own row): the
        // taken wound counts in tenths — Math.round(amount * 10).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.DAMAGE_TAKEN,
                Math.round(amount * 10.0f));
        // The vanilla damage exhaustion (EntityLivingBase: addFatigue(source.
        // getExhaustion()) when the damage lands).
        if (exhaustionCharge > 0.0f) {
            session.addExhaustion(exhaustionCharge);
        }
        if (session.health() <= 0) {
            dieOnTick(session, causeMessage != null ? causeMessage : "died");
        } else {
            publishBodyChanged(session);
        }
    }

    /** The DamageKind → public DamageCause join (identical constant names). */
    private static net.zaminmc.torch.event.DamageCause publicCause(DamageKind kind) {
        return net.zaminmc.torch.event.DamageCause.valueOf(kind.name());
    }

    /**
     * Death: carried window state returns, the whole inventory (and cursor)
     * scatters at the body with the historical pop, the body marks dead. The
     * client learns through the survival listener (combat event + health 0).
     * Tick-thread context.
     */
    private void dieOnTick(PlayerSession session) {
        dieOnTick(session, "died");
    }

    /**
     * Death: carried window state returns, the whole inventory (and cursor)
     * scatters at the body with the historical pop, the body marks dead. The
     * client learns through the survival listener (combat event + health 0).
     * The cause-specific vanilla-style message is broadcast to every online
     * player (the historical death chat) and logged. Tick-thread context.
     */
    private void dieOnTick(PlayerSession session, String causeMessage) {
        session.markDead();
        // The death stats (PlayerEntity.onDeath's own rows): the death
        // counter advances and the since-death clock resets.
        session.stats().increment(net.zaminmc.torch.server.stat.EngineStats.DEATHS, 1);
        session.stats().noteDeath();
        // The dead body's effects clear (the reference's death walk).
        potionSystem.clear(session);
        // The seat opens on death (the vanilla dismount rule).
        if (vehicleManager != null && session.ridingVehicleId() >= 0) {
            var ridden = vehicleManager.byId(session.ridingVehicleId());
            if (ridden != null) {
                vehicleManager.dismount(ridden);
            }
        }
        if (mobManager != null && session.ridingMobId() >= 0) {
            MobEntity riddenMob = mobManager.byId(session.ridingMobId());
            if (riddenMob != null) {
                mobManager.dismountMob(riddenMob, false);
            }
        }
        returnWindowCarriedItems(session, true);
        // §27.7: the public death observation with the keep-inventory arm —
        // the event fires after the body dies (effects cleared, seat open)
        // but BEFORE the scatter; a cancelled event keeps the experience and
        // the whole inventory on the body (the historical keepInventory rule).
        net.zaminmc.torch.event.player.PlayerDeathEvent deathEvent =
                new net.zaminmc.torch.event.player.PlayerDeathEvent(
                        session, session.name() + " " + causeMessage);
        eventBus.fire(deathEvent);
        // keepInventory (Slice 24a — PlayerEntity.onDeath line 636's own
        // gate): the rule and a cancelled event compose — either keeps
        // the experience and the whole inventory on the body; the death
        // still resolves (the respawn walk stays).
        if (!deathEvent.cancelled()
                && !gameRules.getBoolean("keepInventory")) {
            // The death XP scatter (the historical 7-per-level rule, capped at
            // 100) lands at the body before the total resets — vanilla drops the
            // orbs where the player fell, and the newcomer starts from zero.
            int xpScatter = ExperienceMath.xpDroppedOnDeath(session.experienceLevel());
            if (xpScatter > 0) {
                experienceOrbs.spawnBurst(session.position(), xpScatter, 5);
            }
            session.setTotalXp(0);
            publishExperienceChanged(session);
            var slots = session.inventory().snapshot();
            for (int slot = 0; slot < slots.size(); slot++) {
                ItemStack dropped = session.inventory().dropFromSlot(slot, true);
                spawnDeathDrop(session, dropped);
            }
            // The armor row scatters with the inventory (the historical death drop).
            for (int armorSlot = 0; armorSlot < PlayerInventory.ARMOR_SLOTS; armorSlot++) {
                ItemStack piece = session.inventory().armorAt(armorSlot);
                if (!piece.isEmpty()) {
                    spawnDeathDrop(session, piece);
                }
                session.inventory().setArmor(armorSlot, ItemStack.EMPTY);
            }
            spawnDeathDrop(session, session.inventory().takeCursor());
            publishInventoryChanged(session);
        }
        publishBodyChanged(session);
        publishDied(session);
        // showDeathMessages (Slice 24a — PlayerEntity.onDeath line 659:
        // the broadcast skips when the rule is off; the null-suppress —
        // a plugin's silent death — composes with it).
        String deathLine = gameRules.getBoolean("showDeathMessages")
                ? deathEvent.deathMessage() : null;
        if (deathLine != null) {
            for (PlayerSession online : players.all()) {
                systemMessage(online, deathLine);
            }
            LOGGER.info(deathLine);
        }
    }

    /** Death drops scatter around the body with a small random pop. */
    private void spawnDeathDrop(PlayerSession session, ItemStack stack) {
        if (stack.isEmpty() || itemEntities == null) {
            return;
        }
        var origin = session.position();
        ItemEntity entity = itemEntities.spawnThrown(
                new net.zaminmc.torch.util.Position(origin.x(), origin.y() + 1.0, origin.z()), stack);
        if (entity != null) { // the §27.7 gate may withhold the throw
            entity.setVelocity((Math.random() - 0.5) * 0.2, 0.2, (Math.random() - 0.5) * 0.2);
        }
    }

    private void publishInventoryChanged(PlayerSession player) {
        for (InventoryListener listener : inventoryListeners) {
            listener.onInventoryChanged(player);
        }
    }

    /**
     * Applies a validated held-slot change. The owning channel loop provides
     * ordering; the mutation itself is simulation-confined. Safe from any thread.
     * Switching slots cancels an in-progress eat (the historical rule).
     */
    public void heldItemChange(PlayerSession session, int hotbarSlot) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            try {
                session.inventory().selectHotbarSlot(hotbarSlot);
                session.cancelEating();
            } catch (IllegalArgumentException invalidSlot) {
                LOGGER.fine(() -> "Rejected held-slot change " + hotbarSlot + " from "
                        + session.name());
                return;
            }
            // The hotbar switch: vanilla confirms with Held Item Change 0x09
            // to the owner and re-renders the new held item on the observers
            // through Entity Equipment — no Window Items re-sync (the client
            // predicts its own swap).
            publishHeldSlotChanged(session);
        });
    }

    /** The owner's held-slot change (the Held Item Change 0x09 confirm). */
    public interface HeldSlotListener {
        void onHeldSlotChanged(PlayerSession player);
    }

    private final java.util.List<HeldSlotListener> heldSlotListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers the held-slot observer (the protocol adapter's confirm). */
    public void addHeldSlotListener(HeldSlotListener listener) {
        heldSlotListeners.add(listener);
    }

    private void publishHeldSlotChanged(PlayerSession player) {
        for (HeldSlotListener listener : heldSlotListeners) {
            listener.onHeldSlotChanged(player);
        }
    }

    /** The chest lid state changed (the Block Action 0x24 fan-out). */
    public interface ChestLidListener {
        void onChestLid(BlockPosition position, PlayerSession opener, boolean open);
    }

    private final java.util.List<ChestLidListener> chestLidListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers the chest-lid observer (the protocol adapter's Block Action). */
    public void addChestLidListener(ChestLidListener listener) {
        chestLidListeners.add(listener);
    }

    private void publishChestLid(BlockPosition position, PlayerSession opener, boolean open) {
        for (ChestLidListener listener : chestLidListeners) {
            listener.onChestLid(position, opener, open);
        }
    }

    // -------------------------------------------------------------- using + respawn

    /** Full-draw charge in ticks: the historical 1 second to maximum power. */
    static final int BOW_FULL_CHARGE_TICKS = 20;
    /** Full-draw arrow launch speed (the historical 3.0 blocks/tick). */
    static final double BOW_MAX_SPEED = 3.0;
    /** Shard (snowball/egg) launch speed (the historical 1.5). */
    static final double SHARD_SPEED = 1.5;
    /** The potion's throw power (PotionEntity.getPower line 46-48: 0.5). */
    private static final double POTION_THROW_POWER = 0.5;
    /** The Flame bow's arrow burn (the reference's setOnFireFor(100) = 100 s). */
    static final int FLAME_ARROW_TICKS = 2_000;
    /** The burning arrow's hit ignite (the reference's setOnFireFor(5) = 5 s). */
    static final int PROJECTILE_FLAME_TARGET_TICKS = 100;

    /**
     * A right-click use in the air: the held item decides the semantics —
     * the bow begins its draw (ammunition checked in survival), the snowball
     * and egg throw on the press, food starts the 32-tick eat timer when the
     * player is hungry; everything else is a no-op this slice. Safe from any
     * thread.
     */
    @Override
    public void useItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.dead() || session.state() != PlayerState.PLAYING) {
                return;
            }
            ItemStack held = session.inventory().held();
            if (held.isEmpty()) {
                return;
            }
            // Ranged: the bow starts its draw (the release gesture fires).
            if (held.type().equals(BuiltinItems.BOW)) {
                if (session.bowCharging()) {
                    return; // already drawn
                }
                boolean creative = session.gamemode() == GameMode.CREATIVE;
                if (!creative && session.inventory().countOf(BuiltinItems.ARROW) == 0) {
                    return; // no ammunition: the historical refusal
                }
                session.beginBowCharge();
                return;
            }
            // Shards: the snowball and egg throw on the press (historical timing).
            if (held.type().equals(BuiltinItems.SNOWBALL)
                    || held.type().equals(BuiltinItems.EGG)) {
                throwShard(session, held.type());
                return;
            }
            // The ender pearl (Slice 17c — EnderPearlItem.startUsing): the
            // stack-16 throwable — the pearl throws on the press like the
            // shards (the reference's own same-tick startUsing shape).
            if (held.type().equals(BuiltinItems.ENDER_PEARL)) {
                throwEnderPearl(session);
                return;
            }
            // The bottle o' enchanting (Slice 65 —
            // ExperienceBottleItem.startUsing lines 12-24): the stack-64
            // throwable that throws on the press, lobbed (the -20-degree
            // start-pitch offset) and bursting into experience orbs.
            if (held.type().equals(BuiltinItems.EXPERIENCE_BOTTLE)) {
                throwExperienceBottle(session);
                return;
            }
            // Potions (Slice 11b — PotionItem.startUsing lines 109-126): the
            // splash throws on the press; the drinkable starts its own
            // 32-tick clock (no hunger gate — the reference's own rule).
            if (held.type().equals(BuiltinItems.POTION)) {
                if (net.zaminmc.torch.server.potion.PotionHelper.isSplashPotion(held.damage())) {
                    throwSplashPotion(session, held.damage());
                } else {
                    session.beginDrinking();
                }
                return;
            }
            // The spawn egg's in-air use (Slice 20a — SpawnEggItem.
            // startUsing lines 94-131): the crosshair's liquid target is
            // the only in-air spawn — the crosshair's solid target belongs
            // to the block-use path.
            if (held.type().equals(BuiltinItems.SPAWN_EGG)) {
                useSpawnEggInAir(session);
                return;
            }
            // The fishing rod (Slice 67 — FishingRodItem.startUsing):
            // a live bobber retrieves, an empty hand casts.
            if (held.type().equals(BuiltinItems.FISHING_ROD)) {
                useFishingRod(session);
                return;
            }
            // Food: the eat timer.
            if (Foods.nutritionOf(held.type(), held.damage()).isEmpty()) {
                return;
            }
            if (session.food() >= net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD) {
                return; // not hungry: the historical refusal
            }
            session.beginEating();
        });
    }

    /**
     * The client released a use (dig status 5): an unfinished eat cancels; a
     * finished one was already applied by the tick timer; a drawn bow fires.
     * Safe from any thread.
     */
    @Override
    public void releaseUsingItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.eating()) {
                if (session.eatingTicks() < Foods.EAT_TICKS) {
                    session.cancelEating(); // released early: the historical cancel
                }
                return;
            }
            if (session.bowCharging()) {
                releaseBow(session);
            }
        });
    }

    /**
     * The bow's release: power scales with the draw (clamped at one second),
     * the arrow launches from the eyes, one unit of ammunition and one unit of
     * durability leave in survival. Tick-thread context.
     */
    private void releaseBow(PlayerSession session) {
        int charge = session.bowChargeTicks();
        session.cancelBowCharge();
        // The reference charge curve (BowItem.stopUsing lines 24-33):
        // f = i/20 shaped by (f*f + f*2)/3 — the vanilla mid-draw ramp —
        // the 0.1 gate aborts (charge 2 shapes to 0.07, charge 3 to 0.1075),
        // the 1.0 clamp marks the full draw.
        float x = (float) charge / BOW_FULL_CHARGE_TICKS;
        float power = (x * x + x * 2.0F) / 3.0F;
        if (power < 0.1F) {
            return; // the flick: no shot, no wear
        }
        if (power > 1.0F) {
            power = 1.0F;
        }
        boolean fullDraw = power == 1.0F;
        ItemStack held = session.inventory().held();
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        // Infinity (the reference's flag arm line 22): creative or the
        // enchantment — no arrow leaves the quiver. The pickup=2 flag has
        // no arrow-retrieval surface yet (ledgered with that slice).
        boolean infinite = creative
                || EnchantmentHelper.level(held, Enchantments.INFINITY.id) > 0;
        if (!infinite && !session.inventory().consumeOne(BuiltinItems.ARROW)) {
            return; // the ammunition vanished mid-draw
        }
        ProjectileEntity arrow = launchProjectile(session,
                ProjectileEntity.Kind.ARROW, power * BOW_MAX_SPEED);
        // The enchantment arms (the reference lines 36-52): the full draw
        // sets the arrow's crit flag, Power feeds the damage multiplier
        // (j * 0.5 + 0.5), Punch rides the landed hit, Flame ignites the
        // arrow for 100 s (the whole flight).
        if (fullDraw) {
            arrow.setCritical(true);
        }
        int powerLevel = EnchantmentHelper.level(held, Enchantments.POWER.id);
        if (powerLevel > 0) {
            arrow.addBonusDamage(powerLevel * 0.5 + 0.5);
        }
        int punchLevel = EnchantmentHelper.level(held, Enchantments.PUNCH.id);
        if (punchLevel > 0) {
            arrow.setPunchLevel(punchLevel);
        }
        if (EnchantmentHelper.level(held, Enchantments.FLAME.id) > 0) {
            arrow.setOnFireFor(FLAME_ARROW_TICKS);
        }
        // The reference bow release sound (BowItem line 55): volume 1.0, the
        // pitch riding the shaped charge fraction (f * 0.5).
        fxManager.sound(mouthPosition(session), "random.bow", 1.0f,
                1.0f / (fxRandom.nextFloat() * 0.4f + 1.2f) + power * 0.5f);
        if (!creative && session.inventory().damageHeld(1, gameplayRandom)) {
            // The bow's snap: worn out, the client's held slot re-syncs.
            publishInventoryChanged(session);
        }
        publishInventoryChanged(session);
    }

    /**
     * The splash throw (PotionItem.startUsing lines 110-122): one unit
     * leaves, the potion body launches with the 0.5 power and the -20
     * degree pitch offset (ThrownEntity's own start arm), the bow throw
     * sound. Tick-thread context.
     */
    private void throwSplashPotion(PlayerSession session, int metadata) {
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative) {
            session.inventory().consumeHeld(1);
        }
        Position eye = mouthPosition(session);
        double yawRadians = Math.toRadians(session.rotation().yaw());
        Position spawn = new Position(
                eye.x() - Math.cos(yawRadians) * 0.16,
                eye.y() - 0.1F,
                eye.z() - Math.sin(yawRadians) * 0.16);
        ProjectileEntity potion = projectileManager.launch(ProjectileEntity.Kind.POTION,
                session.engineEntityId(), spawn, session.rotation().yaw(),
                session.rotation().pitch() - 20.0F, POTION_THROW_POWER);
        if (potion != null) {
            potion.setPotionDamage(metadata);
        }
        fxManager.sound(mouthPosition(session), "random.bow", 0.5f,
                0.4f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        publishInventoryChanged(session);
    }

    /**
     * The 32-tick drink timer (Slice 11b — PotionItem.finishUsing lines
     * 71-96): completion consumes the potion, applies the effect set (the
     * instant family at full strength, the timed family into the store),
     * and the hand keeps the glass bottle back. The drinking feedback is
     * the reference's own (PlayerEntity.tick lines 198-205: the
     * "random.drink" every four ticks through the last 25, and the
     * completion's onConsumeItem(16) plays the drink — not the food's
     * burp).
     */
    private void tickDrinking(PlayerSession session) {
        if (!session.drinking()) {
            return;
        }
        var held = session.inventory().held();
        if (held.isEmpty() || !held.type().equals(BuiltinItems.POTION)
                || net.zaminmc.torch.server.potion.PotionHelper.isSplashPotion(held.damage())) {
            session.cancelDrinking(); // the hand changed mid-drink
            return;
        }
        int ticks = session.advanceDrinking();
        if (ticks < Foods.EAT_TICKS) {
            // The reference's down-counter (32 -> 1) sounds at <= 25 and
            // every 4 — the up-counter's 8, 12, ..., 28.
            if (ticks >= 8 && ticks <= 28 && ticks % 4 == 0) {
                fxManager.sound(mouthPosition(session), "random.drink", 0.5f,
                        fxRandom.nextFloat() * 0.1f + 0.9f);
            }
            return;
        }
        session.cancelDrinking();
        fxManager.sound(mouthPosition(session), "random.drink", 0.5f,
                fxRandom.nextFloat() * 0.1f + 0.9f);
        int metadata = held.damage();
        session.inventory().consumeHeld(1);
        potionSystem.finishDrinking(session, metadata);
        if (session.gamemode() != GameMode.CREATIVE) {
            // The bottle swap (finishUsing lines 87-93): the empty bottle
            // returns to the inventory, or drops when the inventory is full.
            net.zaminmc.torch.item.ItemStack leftover = session.inventory().pickUp(
                    ItemStack.of(BuiltinItems.GLASS_BOTTLE, 1));
            if (!leftover.isEmpty()) {
                throwFromPlayer(session, leftover);
            }
        }
        publishInventoryChanged(session);
        publishBodyChanged(session);
    }

    /**
     * A shard throw: the projectile launches at the historical speed, one
     * unit leaves the hand in survival, the launch whoosh rides the wire.
     * Tick-thread context.
     */
    private void throwShard(PlayerSession session, net.zaminmc.torch.item.ItemType shard) {
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative) {
            if (session.inventory().held().type().equals(shard)) {
                session.inventory().consumeHeld(1);
            } else {
                return; // the hand changed between press and tick
            }
        }
        launchProjectile(session, shard.equals(BuiltinItems.EGG)
                ? ProjectileEntity.Kind.EGG : ProjectileEntity.Kind.SNOWBALL, SHARD_SPEED);
        // The shard throw sound (the reference SnowballItem/EggItem line 21):
        // volume 0.5, pitch 0.4 / (rand * 0.4 + 0.8).
        fxManager.sound(mouthPosition(session), "random.bow", 0.5f,
                0.4f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        publishInventoryChanged(session);
    }

    /**
     * The ender pearl throw (Slice 17c — EnderPearlItem.startUsing lines
     * 15-23): one unit leaves the hand (the stack-16 rule), the pearl
     * launches at the shard's own speed, and the release whoosh rides the
     * same volume/pitch recipe. Tick-thread context.
     */
    private void throwEnderPearl(PlayerSession session) {
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative) {
            if (session.inventory().held().type().equals(BuiltinItems.ENDER_PEARL)) {
                session.inventory().consumeHeld(1);
            } else {
                return; // the hand changed between press and tick
            }
        }
        launchProjectile(session, ProjectileEntity.Kind.ENDER_PEARL, SHARD_SPEED);
        fxManager.sound(mouthPosition(session), "random.bow", 0.5f,
                0.4f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        publishInventoryChanged(session);
    }

    /**
     * The bottle o' enchanting's throw (Slice 65 —
     * ExperienceBottleItem.startUsing lines 12-24 + the
     * ExperienceBottleEntity ctor over ThrownEntity lines 58-67): one unit
     * leaves the hand, the launch builds the reference's own f=0.4
     * direction — the horizontal pair over the plain pitch's cosine, the
     * vertical over {@code pitch + (-20)} — and the dispense walk
     * (normalize, gaussian 0.0075, scale by the power 0.7) carries it
     * (launchVector is that walk verbatim). The whoosh rides the same
     * volume/pitch recipe as the shard family. Tick-thread context.
     */
    private void throwExperienceBottle(PlayerSession session) {
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative) {
            if (session.inventory().held().type().equals(BuiltinItems.EXPERIENCE_BOTTLE)) {
                session.inventory().consumeHeld(1);
            } else {
                return; // the hand changed between press and tick
            }
        }
        if (projectileManager != null) {
            Position eye = mouthPosition(session);
            double yawRadians = Math.toRadians(session.rotation().yaw());
            Position spawn = new Position(
                    eye.x() - Math.cos(yawRadians) * 0.16,
                    eye.y() - 0.1f,
                    eye.z() - Math.sin(yawRadians) * 0.16);
            double pitchRadians = Math.toRadians(session.rotation().pitch());
            double dx = -Math.sin(yawRadians) * Math.cos(pitchRadians);
            double dz = Math.cos(yawRadians) * Math.cos(pitchRadians);
            // getStartPitchOffset's -20: the bottle lobs (the vertical
            // term alone carries the offset — the reference's own ctor).
            double dy = -Math.sin(pitchRadians - Math.toRadians(20.0));
            projectileManager.launchVector(ProjectileEntity.Kind.EXPERIENCE_BOTTLE,
                    session.engineEntityId(), spawn, dx, dy, dz, 0.7, 1.0);
            LOGGER.fine(() -> session.name() + " threw a bottle o' enchanting");
        }
        fxManager.sound(mouthPosition(session), "random.bow", 0.5f,
                0.4f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        publishInventoryChanged(session);
    }

    /**
     * The shared launch: spawn origin at the eyes pulled back per the
     * reference's living-thrower constructors (ThrownEntity lines 58-62 and
     * ArrowEntity lines 89-93 — the swapped-trig legacy: x -= cos(yaw)*0.16,
     * z -= sin(yaw)*0.16, y -= 0.1, NOT the look vector). The launch sound
     * is the caller's (the bow and the shards ride different volume/pitch
     * recipes). Tick-thread context.
     */
    private ProjectileEntity launchProjectile(PlayerSession session,
                                              ProjectileEntity.Kind kind, double speed) {
        if (projectileManager == null) {
            return null; // pre-boot guard (tests construct partial engines)
        }
        Position eye = mouthPosition(session);
        double yawRadians = Math.toRadians(session.rotation().yaw());
        Position spawn = new Position(
                eye.x() - Math.cos(yawRadians) * 0.16,
                eye.y() - 0.1F,
                eye.z() - Math.sin(yawRadians) * 0.16);
        ProjectileEntity projectile = projectileManager.launch(kind,
                session.engineEntityId(), spawn,
                session.rotation().yaw(), session.rotation().pitch(), speed);
        LOGGER.fine(() -> session.name() + " launched " + projectile.kind()
                + " (entity " + projectile.entityId() + ")");
        return projectile;
    }

    /**
     * The Entity Action stream (0x0B): the posture gestures the client's own
     * physics already animates — the server tracks them for the observers'
     * flags metadata, the sprint food cost and future movement validation.
     * Actions: 0 start-sneak, 1 stop-sneak, 2 start-sprint, 3 stop-sprint
     * (4 jump-with-horse and 5 leave-bed are posture-neutral here). Safe from
     * any thread.
     */
    public void entityAction(PlayerSession session, int action) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            boolean before = session.sneaking() || session.sprinting();
            switch (action) {
                case 0 -> session.setSneaking(true);
                case 1 -> session.setSneaking(false);
                case 2 -> {
                    // The historical sprint gate: hunger below 7 refuses sprint.
                    if (session.food() > 6) {
                        session.setSprinting(true);
                    }
                }
                case 3 -> session.setSprinting(false);
                default -> {
                    return;
                }
            }
            // The flags metadata is idempotent; observers re-sync regardless of
            // whether the posture actually flipped.
            publishPostureChanged(session);
        });
    }

    /**
     * The client asked to respawn after dying: the body resets (full health,
     * food, saturation) and the adapter re-anchors the wire at world spawn.
     * Safe from any thread.
     */
    /**
     * The client's statistics request (the Client Status 0x16 REQUEST_STATS
     * arm — ServerPlayNetworkHandler's own row): the session's dirty
     * counters travel through the Client Statistics 0x37 packet, the client
     * merging the update into its running copy. Safe from any thread.
     */
    public void requestStatistics(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.state() != PlayerState.PLAYING) {
                return;
            }
            session.link().sendStatistics(session.stats().takeDirty());
        });
    }

    public void performRespawn(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            // The credits' own arm FIRST (Slice 64 — the reference's
            // handleClientStatus lines 740-744: {@code if (leavingTheEnd)
            // respawn(player, 0, true)} — the alive respawn that keeps the
            // whole body; the death gate below never sees it).
            if (session.leavingTheEnd()) {
                endCreditsRespawnOnTick(session);
                return;
            }
            if (!session.dead()) {
                return; // respawn requests only answer deaths
            }
            session.resetBody();
            session.cancelEating();
            // The respawn's dimension reset (Slice 61 — the reference's
            // PlayerManager.respawn(player, 0, ...): every death respawn
            // lands in the OVERWORLD; a body that died in the nether or
            // the End returns home).
            if (session.dimension() != 0) {
                session.setDimension(0);
            }
            // The respawn destination: the bed spawn when the body slept,
            // otherwise the world spawn. The body moves here so the wire's
            // re-anchor reads the destination position.
            Position destination = session.bedSpawn() != null
                    ? session.bedSpawn() : world.spawnPosition();
            session.applyMovement(destination, session.rotation(), false);
            // The respawn re-anchor rides the guard's grace window: the client
            // teleports to spawn and its first proposals are position bursts.
            session.setGraceTicks(100);
            publishInventoryChanged(session);
            publishBodyChanged(session);
            publishRespawned(session, destination);
            LOGGER.info(() -> "Player respawned: " + session.name());
        });
    }

    /**
     * The credits' respawn (Slice 64 — the reference's handleClientStatus
     * line 742: {@code respawn(player, 0, true)} — PlayerManager.respawn's
     * alive arm with PlayerEntity.copyFrom's {@code comesFromTheEnd=true}
     * carry, lines 640-652): the body lands in the OVERWORLD at its bed or
     * the world spawn with the WHOLE body kept — health, food, saturation,
     * the XP bar, the inventory — no reset, no drop. Tick-thread context
     * (performRespawn's submit).
     */
    private void endCreditsRespawnOnTick(PlayerSession session) {
        session.clearLeavingTheEnd();
        session.cancelEating();
        // Every credits respawn lands in the overworld (the reference's
        // respawn(player, 0, ...) — dimension 0 unconditionally).
        if (session.dimension() != 0) {
            session.setDimension(0);
        }
        // The destination walk (PlayerManager.respawn's getSpawnPoint +
        // loadSpawnPoint): the bed spawn when the body slept, otherwise the
        // world spawn — the same destination law as the death respawn.
        Position destination = session.bedSpawn() != null
                ? session.bedSpawn() : world.spawnPosition();
        session.applyMovement(destination, session.rotation(), false);
        // The fall debt clears (a dimension walk is not a fall) and the
        // grace window rides the re-anchor like every respawn.
        session.consumeFallDistance();
        session.setGraceTicks(100);
        publishInventoryChanged(session); // the carried inventory re-syncs
        publishBodyChanged(session);      // the kept health/food re-syncs
        publishRespawned(session, destination);
        LOGGER.info(() -> "Player " + session.name()
                + " left the End (respawned at " + destination + ")");
    }

    /**
     * Semantic drop from the held slot (historical Q / Ctrl+Q): removes the
     * units, spawns a thrown item entity in the look direction. Safe from any thread.
     */
    public void dropHeld(PlayerSession session, boolean entireStack) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            net.zaminmc.torch.item.ItemStack dropped = session.inventory().dropHeld(entireStack);
            if (dropped.isEmpty()) {
                return;
            }
            // The drop stat (PlayerEntity.dropItem's own incrementStat).
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.DROPS, 1);
            throwFromPlayer(session, dropped);
            publishInventoryChanged(session);
        });
    }

    /** Historical throw: look direction, ~0.3 speed, small upward bias. */
    private void throwFromPlayer(PlayerSession session, net.zaminmc.torch.item.ItemStack dropped) {
        if (dropped == null || dropped.isEmpty()) {
            return; // nothing to throw: the inventory took it all (pickUp's empty leftover)
        }
        double yaw = Math.toRadians(session.rotation().yaw());
        double pitch = Math.toRadians(session.rotation().pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        double speed = 0.3;
        var itemEntitiesManager = itemEntities;
        if (itemEntitiesManager == null) {
            return;
        }
        var eye = session.position();
        ItemEntity entity = itemEntitiesManager.spawnThrown(
                new net.zaminmc.torch.util.Position(
                        eye.x() + dx * 0.4, eye.y() + 1.62 + dy * 0.4, eye.z() + dz * 0.4),
                dropped);
        entity.setVelocity(dx * speed, dy * speed + 0.1, dz * speed);
    }

    /**
     * A window click in an open window (window 0 = player inventory, or the
     * session's open container): the semantic operation runs on the tick
     * thread, the verdict goes back through {@code result}, and the affected
     * window re-syncs after every click so the client never keeps predicted
     * state. Safe from any thread.
     */
    @Override
    public void windowClick(PlayerSession session, int windowId, int wireSlot, int button,
                            int mode, java.util.function.Consumer<Boolean> result) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(result, "result");
        ticker.submit(() -> windowClickOnTick(session, windowId, wireSlot, button, mode, result));
    }

    private void windowClickOnTick(PlayerSession session, int windowId, int wireSlot, int button,
                                   int mode, java.util.function.Consumer<Boolean> result) {
        var inventory = session.inventory();
        boolean accepted = false;
        try {
            if (wireSlot == -999) {
                // Clicking outside the window throws the carried stack.
                if (!inventory.cursor().isEmpty()) {
                    net.zaminmc.torch.item.ItemStack carried = inventory.cursor();
                    throwFromPlayer(session, carried);
                    inventory.returnCursor();
                    accepted = true;
                }
            } else if (windowId == WIRE_WINDOW_PLAYER) {
                accepted = clickPlayerWindowOnTick(session, wireSlot, button, mode);
            } else if (windowId == session.openContainerWindowId()) {
                accepted = switch (session.openContainerKind()) {
                    case CRAFTING_TABLE -> clickContainerWindowOnTick(session, wireSlot, button, mode);
                    case FURNACE -> clickFurnaceWindowOnTick(session, wireSlot, button, mode);
                    case CHEST -> clickChestWindowOnTick(session, wireSlot, button, mode);
                    case CHEST_MINECART -> clickChestMinecartWindowOnTick(session, wireSlot, button, mode);
                    case HOPPER_MINECART -> clickHopperMinecartWindowOnTick(session, wireSlot, button, mode);
                    case HOPPER -> clickHopperWindowOnTick(session, wireSlot, button, mode);
                    case ENDER_CHEST -> clickEnderChestWindowOnTick(session, wireSlot, button, mode);
                    case BEACON -> clickBeaconWindowOnTick(session, wireSlot, button, mode);
                    case DISPENSER -> clickDispenserWindowOnTick(session, wireSlot, button, mode);
                    case BREWING_STAND -> clickBrewingStandWindowOnTick(session, wireSlot, button, mode);
                    case HORSE -> clickHorseWindowOnTick(session, wireSlot, button, mode);
                    case VILLAGER -> clickVillagerWindowOnTick(session, wireSlot, button, mode);
                    case ENCHANTING_TABLE -> clickEnchantingWindowOnTick(session, wireSlot, button, mode);
                    default -> false;
                };
            }
            // Unknown window ids: rejected per packet, the resync restores truth.
        } catch (IllegalArgumentException invalid) {
            accepted = false; // a broken click must not damage the session (§54)
        }
        try {
            result.accept(accepted);
        } catch (RuntimeException brokenCallback) {
            // The adapter's response path must never starve the tick queue or
            // corrupt session state (§54): the confirm/cursor sends are the
            // adapter's concern, the authoritative resync below still runs.
            LOGGER.log(java.util.logging.Level.WARNING,
                    "Window click callback failed for " + session.name(), brokenCallback);
        }
        publishInventoryChanged(session);
    }

    /** Click routing for the player inventory window (window 0). Tick-thread context. */
    private boolean clickPlayerWindowOnTick(PlayerSession session, int wireSlot, int button,
                                            int mode) {
        var inventory = session.inventory();
        var grid = session.crafting();
        int armorSlot = armorIndexForWireSlot(wireSlot);
        switch (mode) {
            case 0 -> {
                if (wireSlot == WIRE_SLOT_RESULT) {
                    // Historical result-slot behavior: both buttons craft once
                    // into the cursor; a refused take (mismatched or
                    // overflowing cursor) reverts the client's prediction.
                    return takeCraftingResult(session, grid, inventory, button);
                } else if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    grid.clickCell(wireSlot - WIRE_SLOT_CRAFT_FIRST, button, inventory);
                    return true;
                } else if (armorSlot >= 0) {
                    // Armor slots: kind-gated semantics (armor never stacks,
                    // the historical rule).
                    return clickArmorSlot(session, armorSlot);
                } else {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.clickSlot(engineSlot, button);
                        return true;
                    }
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == WIRE_SLOT_RESULT) {
                    craftAllIntoInventory(session, grid, inventory);
                    return true;
                } else if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    grid.quickMoveTo(wireSlot - WIRE_SLOT_CRAFT_FIRST, inventory);
                    return true;
                } else if (armorSlot >= 0) {
                    return inventory.quickMoveFromArmor(armorSlot);
                } else {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.quickMove(engineSlot);
                        return true;
                    }
                }
                return false;
            }
            case 2 -> {
                // Number-key swaps; armor slots swap against the hotbar too
                // (the historical ContainerPlayer mapping).
                if (armorSlot >= 0 && button >= 0 && button < 9) {
                    return inventory.swapArmorWithHotbar(armorSlot, button);
                }
                int engineSlot = engineSlotOf(wireSlot);
                if (engineSlot >= 0 && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                // Middle-click clone is a creative-only gesture: rejected in survival.
                return false;
            }
            case 4 -> {
                if (armorSlot >= 0) {
                    // Drop-click from an armor slot: one unit or the whole piece.
                    net.zaminmc.torch.item.ItemStack equipped = inventory.armorAt(armorSlot);
                    if (equipped.isEmpty()) {
                        return false;
                    }
                    net.zaminmc.torch.item.ItemStack dropped = button != 0
                            ? equipped
                            : equipped.split(1);
                    inventory.setArmor(armorSlot, button != 0
                            ? net.zaminmc.torch.item.ItemStack.EMPTY
                            : equipped);
                    throwFromPlayer(session, dropped);
                    return true;
                }
                if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = grid.dropFromCell(
                            wireSlot - WIRE_SLOT_CRAFT_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = engineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            case 5 -> {
                // Drag painting: rejected per packet; the end-of-drag resync
                // restores the authoritative state on the client.
                return false;
            }
            default -> {
                return false; // unknown mode: rejected
            }
        }
    }

    /** The armor index of a player-window wire slot (5-8), or -1. */
    private static int armorIndexForWireSlot(int wireSlot) {
        if (wireSlot >= WIRE_SLOT_ARMOR_FIRST && wireSlot <= WIRE_SLOT_ARMOR_LAST) {
            return wireSlot - WIRE_SLOT_ARMOR_FIRST;
        }
        return -1;
    }

    /**
     * The armor slots' left/right click: an empty cursor takes the piece, a
     * cursor piece of the matching kind swaps in, anything else is refused.
     * Tick-thread context.
     */
    private boolean clickArmorSlot(PlayerSession session, int armorSlot) {
        var inventory = session.inventory();
        net.zaminmc.torch.item.ItemStack equipped = inventory.armorAt(armorSlot);
        net.zaminmc.torch.item.ItemStack cursor = inventory.cursor();
        if (cursor.isEmpty()) {
            if (equipped.isEmpty()) {
                return false; // empty on empty: a no-op click
            }
            inventory.setArmor(armorSlot, net.zaminmc.torch.item.ItemStack.EMPTY);
            inventory.cursorBox().set(equipped);
            return true;
        }
        if (inventory.setArmor(armorSlot, cursor)) {
            inventory.cursorBox().set(equipped);
            return true;
        }
        return false; // wrong kind: the client reverts its prediction
    }

    /**
     * Click routing for the open crafting-table container window (protocol 47
     * 10-slot GUI: 0 result, 1-9 grid, 10-36 main inventory, 37-45 hotbar).
     * Tick-thread context.
     */
    private boolean clickContainerWindowOnTick(PlayerSession session, int wireSlot, int button,
                                               int mode) {
        var inventory = session.inventory();
        var grid = session.tableCrafting();
        switch (mode) {
            case 0 -> {
                if (wireSlot == TABLE_WIRE_SLOT_RESULT) {
                    return takeCraftingResult(session, grid, inventory, button);
                } else if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    grid.clickCell(wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, button, inventory);
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == TABLE_WIRE_SLOT_RESULT) {
                    craftAllIntoInventory(session, grid, inventory);
                    return true;
                } else if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    grid.quickMoveTo(wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, inventory);
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.quickMove(engineSlot);
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; grid and
                // result slots are rejected per packet.
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = grid.dropFromCell(
                            wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Wire slot -&gt; engine slot mapping for the player inventory window; -1 when out of engine scope.
     */
    private static int engineSlotOf(int wireSlot) {
        if (wireSlot >= 9 && wireSlot <= 35) {
            return wireSlot;                 // main inventory
        }
        if (wireSlot >= 36 && wireSlot <= 44) {
            return wireSlot - 36;            // hotbar
        }
        return -1;                           // craft/armor slots or outside
    }

    /**
     * Wire slot -&gt; engine slot inside a furnace container window (3-29 maps to
     * main inventory engine slots 9-35; 30-38 to hotbar 0-8); -1 for the
     * furnace's own three slots.
     */
    private static int furnaceEngineSlotOf(int wireSlot) {
        if (wireSlot >= FURNACE_WIRE_SLOT_MAIN_FIRST && wireSlot <= FURNACE_WIRE_SLOT_MAIN_LAST) {
            return wireSlot + 6;             // main inventory (engine 9-35)
        }
        if (wireSlot >= FURNACE_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= FURNACE_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - FURNACE_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the open furnace container window (protocol 47
     * community-verified GUI: 0 input, 1 fuel, 2 output, 3-29 main inventory,
     * 30-38 hotbar). Tick-thread context.
     */
    private boolean clickFurnaceWindowOnTick(PlayerSession session, int wireSlot, int button,
                                             int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var furnace = position == null ? null : furnaceManager.peek(position);
        if (furnace == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    return awardSmeltTakeXp(session, furnace, () -> {
                        furnaceManager.clickSlot(furnace, wireSlot, button, inventory);
                        return true;
                    });
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    return awardSmeltTakeXp(session, furnace,
                            () -> furnaceManager.quickMove(furnace, wireSlot, true, -1, inventory));
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    return furnaceManager.quickMove(furnace, -1, false, engineSlot, inventory);
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; furnace
                // slots are rejected per packet (the resync restores truth).
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            furnaceManager.dropFromSlot(furnace, wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                        awardSmeltStackXp(session, dropped); // the thrown take still pays
                    }
                    return true;
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * The furnace-take XP (the historical SlotFurnaceOutput.onTake rule):
     * whatever left the output slot by the wrapped click pays its recipe's
     * experience to the taking player, the fractional remainder rolling on
     * the gameplay random (a 0.35 recipe on two items floors to 0 with a
     * 70% chance of one bonus point). Tick-thread context.
     */
    private boolean awardSmeltTakeXp(PlayerSession session,
                                     net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace,
                                     java.util.function.BooleanSupplier click) {
        var before = furnace.output();
        int beforeCount = before.isEmpty() ? 0 : before.count();
        boolean accepted = click.getAsBoolean();
        var after = furnace.output();
        boolean sameType = !before.isEmpty() && !after.isEmpty()
                && after.type().equals(before.type());
        int taken = sameType ? beforeCount - after.count() : beforeCount;
        if (taken > 0 && !before.isEmpty()) {
            awardSmeltStackXp(session, before.withCount(taken));
        }
        return accepted;
    }

    /** Pays the recipe XP for a removed output stack (Set Experience syncs). */
    private void awardSmeltStackXp(PlayerSession session, net.zaminmc.torch.item.ItemStack taken) {
        double xp = net.zaminmc.torch.server.furnace.FurnaceRecipes.takeExperienceOf(taken.type())
                * taken.count();
        int points = (int) Math.floor(xp);
        double remainder = xp - Math.floor(xp);
        if (remainder > 0 && gameplayRandom.nextDouble() < remainder) {
            points++;
        }
        if (points > 0) {
            session.addExperience(points);
            publishExperienceChanged(session);
        }
    }

    /**
     * Wire slot -&gt; engine slot inside a chest container window (27-53 maps
     * to main inventory engine slots 9-35; 54-62 to hotbar 0-8); -1 for the
     * chest's own 27 slots.
     */
    private static int chestEngineSlotOf(int wireSlot) {
        if (wireSlot >= CHEST_WIRE_SLOT_MAIN_FIRST && wireSlot <= CHEST_WIRE_SLOT_MAIN_LAST) {
            return wireSlot - 18;            // main inventory (engine 9-35)
        }
        if (wireSlot >= CHEST_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= CHEST_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - CHEST_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Wire slot -&gt; engine slot inside a hopper window (the reference's
     * HopperMenu layout: 5-31 maps to main inventory engine slots 9-35;
     * 32-40 to hotbar 0-8); -1 for the hopper's own five slots.
     */
    private static int hopperEngineSlotOf(int wireSlot) {
        if (wireSlot >= HOPPER_WIRE_SLOT_MAIN_FIRST && wireSlot <= HOPPER_WIRE_SLOT_MAIN_LAST) {
            return wireSlot - HOPPER_WIRE_SLOT_MAIN_FIRST
                    + net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS;
        }
        if (wireSlot >= HOPPER_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= HOPPER_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - HOPPER_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the hopper window (the reference's HopperMenu —
     * five container slots, the plain InventorySlot semantics, no filters):
     * mode 0 the cursor walk, mode 1 the quick-move pair, mode 2 the
     * number-key exchange (the player's own slots only), mode 4 the
     * drop-click. Tick-thread context.
     */
    private boolean clickHopperWindowOnTick(PlayerSession session, int wireSlot, int button,
                                            int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var hopper = position == null ? null : hopperSystem.peek(position);
        if (hopper == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    hopper.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    hopper.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: the hopper has no slot filters (the
                    // reference's plain InventorySlots). Remainder returns.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = hopper.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // hopper full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the
                // hopper slots are rejected per packet (the resync restores).
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = hopper.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Click routing for the enchanting window (wire slots 0 item / 1 lapis,
     * 2-28 main, 29-37 hotbar). Mode 0 plays the menu's own PICKUP walk on the
     * shared cursor (the lapis gate, the item slot's max 1); the player tail
     * plays the shared inventory semantics. Mode 1 shift-clicks: the menu's
     * slots go to the inventory, lapis/first-empty-item go into the menu from
     * the player (the reference quickMoveItem arms). Mode 2 exchanges main
     * and hotbar only (the two menu slots are rejected per packet).
     * Tick-thread context.
     */
    private boolean clickEnchantingWindowOnTick(PlayerSession session, int wireSlot, int button,
                                                int mode) {
        var inventory = session.inventory();
        var menu = session.enchantingMenu();
        if (menu == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot == 0 || wireSlot == 1) {
                    menu.clickSlot(wireSlot, button, inventory.cursorBox());
                    return true;
                }
                int engineSlot = enchantingEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == 0 || wireSlot == 1) {
                    menu.quickMoveToInventory(wireSlot, inventory::pickUp);
                    return true;
                }
                int engineSlot = enchantingEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    if (moving.isEmpty()) {
                        return true; // an empty player slot: nothing to move, nothing changed
                    }
                    ItemStack leftover = menu.quickMoveFromPlayer(moving);
                    if (!leftover.isEmpty()) {
                        inventory.pickUp(leftover); // refused or partial: the rest stays
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the two
                // menu slots are rejected per packet (the historical rule).
                int engineSlot = enchantingEngineSlotOf(wireSlot);
                if (engineSlot >= PlayerInventory.HOTBAR_SLOTS && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    /** The enchanting window's player-tail wire slot to engine slot (main 2-28 → 9-35, hotbar 29-37 → 0-8). */
    private static int enchantingEngineSlotOf(int wireSlot) {
        if (wireSlot >= 2 && wireSlot <= 28) {
            return wireSlot + 7; // main inventory (engine 9-35)
        }
        if (wireSlot >= 29 && wireSlot <= 37) {
            return wireSlot - 29; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the open chest container window (protocol 47
     * community-verified GUI: 0-26 chest, 27-53 main inventory, 54-62 hotbar).
     * Tick-thread context.
     */
    private boolean clickChestWindowOnTick(PlayerSession session, int wireSlot, int button,
                                           int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var chest = position == null ? null : chestManager.peek(position);
        if (chest == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: a chest has no slot filters (the
                    // historical TileEntityChest). Remainder returns to the slot.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = chest.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // chest full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; chest
                // slots are rejected per packet (the resync restores truth).
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = chest.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Click routing for the open ender chest window (Slice 29a — the
     * reference's ContainerChest over the player's own EnderChestInventory):
     * the same 63-slot wire layout as the chest (0-26 the grid, 27-53 main,
     * 54-62 hotbar), the same click semantics — but the container slots
     * route to the SESSION's private grid, not the world. The liveness gate
     * is the block at the open position: a broken ender chest (the window
     * outlived its door) rejects every click until the close arrives.
     * Tick-thread context.
     */
    private boolean clickEnderChestWindowOnTick(PlayerSession session, int wireSlot, int button,
                                                int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        if (position == null
                || !net.zaminmc.torch.server.chest.EnderChestBlocks.isFamily(
                        world.getBlock(position))) {
            return false; // the door left (stale window): rejected, resync restores
        }
        var ender = session.enderChest();
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    ender.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    ender.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: the ender grid has no slot filters
                    // (the reference's own SimpleInventory shape). Remainder
                    // returns to the slot.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = ender.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // grid full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; grid
                // slots are rejected per packet (the resync restores truth).
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = ender.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Click routing for the beacon window (Slice 29b — the reference's
     * BeaconMenu): wire 0 is the payment slot (the item filter emerald/
     * diamond/gold ingot/iron ingot, one stack of one — the PaymentSlot's
     * own rules), wire 1-27 the main inventory, 28-36 the hotbar. The
     * payment takes any allowed stack whole (max stack 1); the reference's
     * shift-click arms ride the same layout. Tick-thread context.
     */
    private boolean clickBeaconWindowOnTick(PlayerSession session, int wireSlot, int button,
                                            int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var beacon = position == null ? null : beaconSystem.peek(position);
        if (beacon == null || !net.zaminmc.torch.server.beacon.BeaconSystem.isBeacon(
                world.getBlock(position))) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot == 0) {
                    // The payment slot: the reference's isItemAllowed + the
                    // max-stack-1 (the PaymentSlot's own pair) over the
                    // shared historical cursor semantics.
                    if (!beacon.payment().isEmpty()
                            || net.zaminmc.torch.server.beacon.BeaconBlockEntity
                                    .isPaymentItem(inventory.cursor().type())) {
                        net.zaminmc.torch.item.ItemStack[] box = {beacon.payment()};
                        net.zaminmc.torch.server.player.WindowClicks.click(box, 0, button,
                                inventory.cursorBox(), 1,
                                cursor -> net.zaminmc.torch.server.beacon.BeaconBlockEntity
                                        .isPaymentItem(cursor.type()));
                        beacon.setPayment(box[0]);
                        return true;
                    }
                    return false;
                }
                int engineSlot = beaconEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == 0) {
                    // Quick-move the payment back into the inventory.
                    if (beacon.payment().isEmpty()) {
                        return false;
                    }
                    net.zaminmc.torch.item.ItemStack payment = beacon.payment();
                    beacon.setPayment(net.zaminmc.torch.item.ItemStack.EMPTY);
                    var leftover = inventory.pickUp(payment);
                    if (!leftover.isEmpty()) {
                        beacon.setPayment(leftover); // no room: it stays
                    }
                    return true;
                }
                int engineSlot = beaconEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false;
                    }
                    if (beacon.payment().isEmpty()
                            && net.zaminmc.torch.server.beacon.BeaconBlockEntity
                                    .isPaymentItem(peek.type())) {
                        // One allowed stack moves into the empty payment slot.
                        net.zaminmc.torch.item.ItemStack moving =
                                inventory.dropFromSlot(engineSlot, true);
                        beacon.setPayment(moving);
                        return true;
                    }
                    return false;
                }
                return false;
            }
            case 4 -> {
                if (wireSlot == 0) {
                    if (beacon.payment().isEmpty()) {
                        return false;
                    }
                    throwFromPlayer(session, beacon.payment());
                    beacon.setPayment(net.zaminmc.torch.item.ItemStack.EMPTY);
                    return true;
                }
                int engineSlot = beaconEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // number keys / clone / drag: rejected
            }
        }
    }

    /**
     * Wire slot -> engine inventory slot inside the beacon window (1-27 the
     * main inventory engine 9-35, 28-36 the hotbar engine 0-8); -1 otherwise.
     */
    private static int beaconEngineSlotOf(int wireSlot) {
        if (wireSlot >= 1 && wireSlot <= 27) {
            return wireSlot + 8; // main inventory (engine 9-35)
        }
        if (wireSlot >= 28 && wireSlot <= 36) {
            return wireSlot - 28; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Wire slot -> engine inventory slot inside the dispenser window (9-35
     * maps to main inventory engine slots 9-35 — the layout aligns; 36-44 to
     * hotbar 0-8); -1 for the container's own slots.
     */
    private static int dispenserEngineSlotOf(int wireSlot) {
        if (wireSlot >= DISPENSER_WIRE_SLOT_MAIN_FIRST
                && wireSlot <= DISPENSER_WIRE_SLOT_MAIN_LAST) {
            return wireSlot;                       // main inventory (engine 9-35)
        }
        if (wireSlot >= DISPENSER_WIRE_SLOT_HOTBAR_FIRST
                && wireSlot <= DISPENSER_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - DISPENSER_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the dispenser/dropper window (Slice 11a): the same
     * shared-slot semantics as the chest — the container's nine slots on
     * wire 0-8, the inventory below. A dispenser has no slot filters (the
     * historical TileEntityDispenser's isValid always true). Tick-thread
     * context.
     */
    private boolean clickDispenserWindowOnTick(PlayerSession session, int wireSlot, int button,
                                               int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var container = position == null ? null : dispenserSystem.peek(position);
        if (container == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= DISPENSER_WIRE_SLOT_LAST) {
                    container.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = dispenserEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= DISPENSER_WIRE_SLOT_LAST) {
                    container.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = dispenserEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: a dispenser has no slot filters.
                    // Remainder returns to the slot.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = container.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the
                // container slots are rejected per packet.
                int engineSlot = dispenserEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= DISPENSER_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = container.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = dispenserEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * The brewing stand's window layout (Slice 11e — the reference's
     * BrewingStandMenu): wire 0-2 the potion bottles, 3 the ingredient,
     * 4-30 the main inventory (engine 9-35), 31-39 the hotbar (engine
     * 0-8); -1 for the container's own slots.
     */
    private static final int BREWING_WIRE_SLOT_LAST = 3;
    private static final int BREWING_WIRE_SLOT_MAIN_FIRST = 4;
    private static final int BREWING_WIRE_SLOT_MAIN_LAST = 30;
    private static final int BREWING_WIRE_SLOT_HOTBAR_FIRST = 31;

    /** Wire slot -> engine inventory slot inside the brewing window. */
    private static int brewingEngineSlotOf(int wireSlot) {
        if (wireSlot >= BREWING_WIRE_SLOT_MAIN_FIRST
                && wireSlot <= BREWING_WIRE_SLOT_MAIN_LAST) {
            return wireSlot + 5;                     // wire 4-30 -> engine 9-35
        }
        if (wireSlot >= BREWING_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= 39) {
            return wireSlot - BREWING_WIRE_SLOT_HOTBAR_FIRST; // hotbar 0-8
        }
        return -1;
    }

    /**
     * Click routing for the brewing stand window (Slice 11e): the
     * container's four filtered slots on wire 0-3 (the potion slots'
     * one-stack limit, the ingredient slot's ingredient gate), the
     * inventory below — the same shared semantics as the chest. The
     * shift-click into the stand follows the reference's content-based
     * arms (the ingredient into the empty slot 3, the bottles into 0-2
     * one per slot). Tick-thread context.
     */
    private boolean clickBrewingStandWindowOnTick(PlayerSession session, int wireSlot, int button,
                                                  int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var container = position == null ? null : brewingStands.peek(position);
        if (container == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= BREWING_WIRE_SLOT_LAST) {
                    container.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = brewingEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= BREWING_WIRE_SLOT_LAST) {
                    container.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = brewingEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // The content-based arms (quickMoveItem lines 71-90):
                    // an ingredient into the empty slot, a bottle into the
                    // potion slots; anything else never leaves.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = container.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // the overflow returns
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the
                // container slots are rejected per packet.
                int engineSlot = brewingEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= BREWING_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = container.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = brewingEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Wire slot -> engine inventory slot inside a container window (10-36 maps
     * to main inventory engine slots 9-35; 37-45 to hotbar 0-8); -1 for the
     * container's own result/grid slots.
     */
    private static int tableEngineSlotOf(int wireSlot) {
        if (wireSlot >= TABLE_WIRE_SLOT_GRID_LAST + 1 && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST + 27) {
            return wireSlot - 1;             // main inventory (engine 9-35)
        }
        if (wireSlot >= TABLE_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= TABLE_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - TABLE_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Result-slot pickup (mode 0 on wire slot 0) with the vanilla take rule:
     * left-click moves the whole result onto the cursor; right-click takes
     * (size+1)/2 — half the result stack. Either way one unit leaves every
     * non-empty grid cell (the vanilla CraftingResultSlot.onItemRemoved).
     * A refused cursor take rejects the click unchanged.
     */
    private boolean takeCraftingResult(PlayerSession session, net.zaminmc.torch.server.player.CraftingGrid grid,
                                       net.zaminmc.torch.server.player.PlayerInventory inventory, int button) {
        net.zaminmc.torch.item.ItemStack result = craftingResultOf(grid);
        if (result.isEmpty()) {
            return true; // nothing crafted: an accepted no-op, the resync realigns
        }
        net.zaminmc.torch.item.ItemStack wanted = result;
        if (button == 1) {
            // Vanilla pickup path: (size + 1) / 2 of the slot's stack.
            wanted = result.withCount((result.count() + 1) / 2);
        }
        if (!inventory.takeResultToCursor(wanted)) {
            return false;
        }
        grid.consumeOne();
        return true;
    }

    /** The preview over a grid of either shape (2x2 player window, 3x3 table). */
    private net.zaminmc.torch.item.ItemStack craftingResultOf(net.zaminmc.torch.server.player.CraftingGrid grid) {
        var snapshot = grid.snapshotArray();
        return (grid.cols() == 3
                        ? crafting.resultOf3x3(snapshot)
                        : crafting.resultOf(snapshot))
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * Craft-all (shift-click on the result): the vanilla semantics are the
     * onClickSlot mode-1 recursion — keep crafting while the result keeps
     * regenerating and the inventory keeps absorbing it, i.e. limited by
     * materials, not a magic cap. The loop below is that recursion with a
     * very high safety bound (a recipe whose result feeds its own grid can
     * never occur, but a broken one must not hang the tick).
     */
    private void craftAllIntoInventory(PlayerSession session, net.zaminmc.torch.server.player.CraftingGrid grid,
                                       net.zaminmc.torch.server.player.PlayerInventory inventory) {
        for (int craft = 0; craft < VANILLA_CRAFT_ALL_BOUND; craft++) {
            net.zaminmc.torch.item.ItemStack result = craftingResultOf(grid);
            if (result.isEmpty()) {
                return;
            }
            grid.consumeOne();
            // The crafted-item stat (ContainerWorkbench.onTake's own
            // incrementStat — the craftItem row over the result's id).
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.craftItemOf(
                            result.type().identifier()), result.count());
            net.zaminmc.torch.item.ItemStack remainder = inventory.pickUp(result);
            if (!remainder.isEmpty()) {
                throwFromPlayer(session, remainder);
                return;
            }
        }
    }

    /**
     * The crafting preview for wire sync (window slot 0). Call on the tick
     * thread or before a session's first grid mutation (both call sites of the
     * adapter qualify); the read is a pure match over a snapshot.
     */
    public net.zaminmc.torch.item.ItemStack craftingResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        return crafting.resultOf(session.crafting().snapshotArray())
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * The crafting-table preview for wire sync (container wire slot 0), empty
     * while no container is open. Same thread discipline as
     * {@link #craftingResult}.
     */
    public net.zaminmc.torch.item.ItemStack containerResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        if (session.openContainerWindowId() < 0) {
            return net.zaminmc.torch.item.ItemStack.EMPTY;
        }
        return crafting.resultOf3x3(session.tableCrafting().snapshotArray())
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * A right-click use on a block (§215 family): the engine decides on the
     * simulation context whether the target block opens a container window
     * (the crafting table) or the use degrades to a placement proposal. Safe
     * from any thread.
     */
    public void useItemOnBlock(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked, int face,
                               java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                               java.util.function.IntConsumer onTableOpened) {
        useItemOnBlock(session, clicked, face, creativeHeld, 0, onTableOpened);
    }

    @Override
    public void useItemOnBlock(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked, int face,
                               java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                               int cursorY,
                               java.util.function.IntConsumer onTableOpened) {
        useItemOnBlock(session, clicked, face, creativeHeld, 0.0f, cursorY / 16.0f, 0.0f,
                onTableOpened);
    }

    /**
     * The cursor-fraction overload (Slice 65 — the 1.8 C08's own three
     * cursor bytes): the hit point within the clicked face as the
     * 0..1 per-axis fractions the reference's useOn faceX/faceY/faceZ
     * consume (the firework's launch point, the placement family's
     * signals). Safe from any thread.
     */
    public void useItemOnBlock(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked, int face,
                               java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                               float cursorX, float cursorY, float cursorZ,
                               java.util.function.IntConsumer onTableOpened) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(creativeHeld, "creativeHeld");
        Objects.requireNonNull(onTableOpened, "onTableOpened");
        ticker.submit(() -> useItemOnBlockOnTick(session, clicked, face, creativeHeld,
                cursorX, cursorY, cursorZ, onTableOpened));
    }

    private void useItemOnBlockOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked,
                                      int face, java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                                      float cursorX, float cursorY, float cursorZ,
                                      java.util.function.IntConsumer onTableOpened) {
        net.zaminmc.torch.block.BlockType current = world.getBlock(clicked);
        if (current.identifier().equals(
                net.zaminmc.torch.server.block.BuiltinBlocks.REDSTONE_ORE.identifier())) {
            // RedstoneOreBlock.use lines 45-50 -> interact (lines 52-57):
            // the click lights the ore (the particles ride the client's
            // randomDisplayTick band) — the reference's use returns
            // super.use, so the click falls through to the placement
            // proposal too; no consumption here either.
            world.setBlock(clicked, net.zaminmc.torch.server.block.BuiltinBlocks.LIT_REDSTONE_ORE);
        }
        if (WorldSolidity.isFurnaceBlock(current.identifier())) {
            openFurnaceOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.CHEST.identifier())) {
            openChestOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.TRAPPED_CHEST.identifier())) {
            // ChestBlock(1).use lines 326-341: the same container GUI opens
            // (the viewer-count power rides the redstone system's probe).
            openChestOnTick(session, clicked, onTableOpened);
            redstoneSystem.updateNeighbors(clicked);
            return;
        }
        if (net.zaminmc.torch.server.chest.EnderChestBlocks.isFamily(current)) {
            // EnderChestBlock.use lines 73-92: the player's OWN inventory
            // opens (the block is the door, not the storage).
            openEnderChestOnTick(session, clicked, onTableOpened);
            return;
        }
        if (net.zaminmc.torch.server.beacon.BeaconSystem.isBeacon(current)) {
            // BeaconBlock.use lines 34-46: the beacon GUI opens over the
            // block entity's own payment slot.
            openBeaconOnTick(session, clicked, onTableOpened);
            return;
        }
        if (net.zaminmc.torch.server.dispenser.DispenserBlocks.isFamily(current)) {
            // DispenserBlock.use lines 78-94: the container opens (the
            // dropper keeps its own menu type on the wire).
            openDispenserOnTick(session, clicked, onTableOpened);
            return;
        }
        if (net.zaminmc.torch.server.noteblock.NoteBlockSystem.isNoteBlock(current)) {
            // NoteBlock.use lines 41-56: the tune ((note + 1) % 25) then the
            // play — the use consumed, no placement proposal follows.
            noteBlocks.useFromTick(clicked);
            // The tune stat (NoteBlock.use's own incrementStat).
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.NOTE_BLOCKS_TUNED, 1);
            return;
        }
        if (net.zaminmc.torch.server.jukebox.JukeboxBlocks.isFamily(current)) {
            // JukeboxBlock.use lines 30-38: a playing jukebox ejects its
            // record (the stop event, the drop, the HAS_RECORD flip); a
            // record-less one answers false — the click falls through to
            // the item arms (the disc's useOn insert) and the placement
            // proposal, the reference's own order.
            if (jukeboxes.useFromTick(clicked)) {
                return;
            }
        }
        if (net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.isFamily(current)) {
            // DaylightDetectorBlock.use lines 66-85: the invert toggle
            // keeping the POWER level, then the immediate re-update — the
            // use consumed, no placement proposal follows.
            if (daylightDetectors.useFromTick(clicked)) {
                return;
            }
        }
        if (net.zaminmc.torch.server.hopper.HopperBlocks.isHopper(current)) {
            // HopperBlock.use lines 135-146: the container menu opens.
            openHopperOnTick(session, clicked, onTableOpened);
            return;
        }
        if (net.zaminmc.torch.server.brewing.BrewingStandBlocks.isBrewingStand(current)) {
            // BrewingStandBlock.use lines 77-89: the container menu opens.
            openBrewingStandOnTick(session, clicked, onTableOpened);
            return;
        }
        if (net.zaminmc.torch.server.cauldron.CauldronBlocks.isCauldron(current)) {
            // CauldronBlock.use lines 77-160: the bucket/bottle arms consume
            // the use (the reference's true); anything else falls through to
            // the placement proposal exactly like the reference's false —
            // the armor/banner wash arms only consumed on colored pieces,
            // and the engine's armor carries no color model (hasColor
            // always false — the reference's own uncolored-leather answer).
            if (useCauldronOnTick(session, clicked, current)) {
                return;
            }
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.CRAFTING_TABLE.identifier())) {
            openCraftingTableOnTick(session, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.ENCHANTING_TABLE.identifier())) {
            openEnchantingTableOnTick(session, clicked, onTableOpened);
            return;
        }
        if (useBucketOnTick(session, clicked, face)) {
            return; // the bucket did its work; no placement proposal follows
        }
        // The glass bottle's water scoop (Slice 11b — GlassBottleItem.startUsing
        // lines 22-47): a bottle aimed at ANY water block fills (the material
        // gate — flowing water counts, unlike the bucket's source-only rule),
        // one unit swapping to the water bottle (metadata 0). The reference
        // plays no sound for the fill.
        net.zaminmc.torch.item.ItemStack bottleHeld = session.inventory().held();
        if (!bottleHeld.isEmpty()
                && bottleHeld.type().equals(BuiltinItems.GLASS_BOTTLE)) {
            BlockType aimed = world.getBlock(clicked);
            if (FluidBlocks.kindOf(aimed.identifier()) == FluidBlocks.Kind.WATER) {
                if (session.gamemode() != GameMode.CREATIVE) {
                    session.inventory().consumeHeld(1);
                    net.zaminmc.torch.item.ItemStack leftover = session.inventory().pickUp(
                            new ItemStack(BuiltinItems.POTION, 1, 0, null));
                    if (!leftover.isEmpty()) {
                        throwFromPlayer(session, leftover); // the full inventory drops it
                    }
                }
                publishInventoryChanged(session);
                return;
            }
            return; // the bottle against anything else never places
        }
        // The disc's insert arm (Slice 27b — MusicDiscItem.useOn lines
        // 28-43): a music disc against a record-less jukebox stores the
        // copy, flips the HAS_RECORD variant, broadcasts the world event
        // 1005 carrying the disc's item id, and consumes one from the hand
        // (the engine's own creative-free convention, the bottle arm's).
        // A disc against a playing jukebox never inserts (the useOn gate)
        // and falls through to the placement proposal.
        net.zaminmc.torch.item.ItemStack discHeld = session.inventory().held();
        if (!discHeld.isEmpty() && net.zaminmc.torch.server.jukebox.JukeboxBlocks.isMusicDisc(
                discHeld.type().identifier())) {
            if (jukeboxes.insertFromTick(clicked, discHeld)) {
                if (session.gamemode() != GameMode.CREATIVE) {
                    session.inventory().consumeHeld(1);
                }
                // The record stat (MusicDiscItem.useOn's own row).
                session.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.RECORDS_PLAYED, 1);
                publishInventoryChanged(session);
                return; // the disc consumed the use
            }
        }
        // The lead on a fence (Slice 11c — LeadItem.useOn lines 24-44): every
        // mob the player holds within the 7-cube ties to the knot at the
        // fence (creating it on the first taker) — the lead itself is NOT
        // consumed (the mob already paid it at the attach).
        net.zaminmc.torch.item.ItemStack leadHeld = session.inventory().held();
        if (!leadHeld.isEmpty() && leadHeld.type().equals(BuiltinItems.LEAD)
                && world.getBlock(clicked).identifier()
                        .equals(BuiltinBlocks.FENCE.identifier())) {
            leadSystem.useLeadOnFence(session, clicked);
            return; // the fence consumed the use (the reference's own true)
        }
        // The firework rocket (Slice 65 — FireworksItem.useOn lines 28-38):
        // a right-click on ANY block face launches the rocket AT the
        // clicked face's own point (pos + the cursor fractions), one unit
        // leaves the hand, and the use consumed — no placement proposal
        // follows. (The item has no startUsing: a right-click in the air
        // does nothing — the in-air path never sees it.)
        net.zaminmc.torch.item.ItemStack fireworksHeld = session.inventory().held();
        if (!fireworksHeld.isEmpty()
                && fireworksHeld.type().equals(BuiltinItems.FIREWORKS)) {
            if (fireworks != null) {
                fireworks.launch(new Position(
                        clicked.x() + cursorX, clicked.y() + cursorY,
                        clicked.z() + cursorZ),
                        net.zaminmc.torch.item.ItemStack.of(
                                BuiltinItems.FIREWORKS, 1));
                if (session.gamemode() != GameMode.CREATIVE) {
                    session.inventory().consumeHeld(1);
                }
                publishInventoryChanged(session);
            }
            return; // the launch consumed the use
        }
        if (cycleRepeaterDelayOnTick(clicked)) {
            return; // the repeater's use consumed the right-click (the delay cycle)
        }
        if (redstoneSystem.useComparator(clicked)) {
            // The mode cycle's click (ComparatorBlock.use lines 148-151): the
            // pitch answers the NEW mode — 0.55 subtract / 0.50 compare.
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "random.click", 0.3f,
                    net.zaminmc.torch.server.redstone.RedstoneBlocks.comparatorSubtract(
                            world.getBlock(clicked)) ? 0.55f : 0.5f);
            return; // the comparator's mode cycle consumed the right-click
        }
        if (redstoneSystem.useLever(clicked)) {
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "random.click", 0.3f,
                    net.zaminmc.torch.server.redstone.RedstoneBlocks.leverPowered(
                            world.getBlock(clicked)) ? 0.6f : 0.5f);
            return; // the lever's toggle consumed the right-click
        }
        if (redstoneSystem.useButton(clicked)) {
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "random.click", 0.3f, 0.6f);
            return; // the button's press consumed the right-click
        }
        if (useFlintOnTick(session, clicked, face)) {
            return; // the fire starter did its work; no placement follows
        }
        if (useReedOnTick(session, clicked, face, creativeHeld)) {
            return; // the reed/cactus placement override ran
        }
        if (useDoublePlantOnTick(session, clicked, face)) {
            return; // the double-plant pair placement was consumed
        }
        if (useCakeOnTick(session, clicked)) {
            return; // the cake's slice was eaten
        }
        // The dragon egg's use (Slice 55 — DragonEggBlock.use lines 75-79:
        // the right click teleports the egg away; the portal particles are
        // the client's own rendering over the block change).
        if (useDragonEggOnTick(clicked)) {
            return; // the egg's teleport consumed the right-click
        }
        if (useFlowerPotOnTick(session, clicked)) {
            return; // the pot's plant was potted
        }
        if (useFarmingOnTick(session, clicked, face)) {
            return; // the hoe/seed/bone-meal/cocoa use was consumed
        }
        // The spawn egg (Slice 20a — SpawnEggItem.useOn lines 63-92): the
        // damage field names the kind; against a spawner the egg re-types
        // the block entity, against anything else the body lands at the
        // clicked face's offset cell (the fence's UP face lifts it by its
        // own half-block). An unknown id never spawns — the reference's
        // containsKey gate — but the use is still consumed.
        net.zaminmc.torch.item.ItemStack eggHeld = session.inventory().held();
        if (!eggHeld.isEmpty() && eggHeld.type().equals(BuiltinItems.SPAWN_EGG)) {
            useSpawnEggOnBlock(session, clicked, face, eggHeld);
            return; // the egg consumed the use (the reference's own true)
        }
        net.zaminmc.torch.item.ItemStack heldNow = session.inventory().held();
        String heldId = heldNow.isEmpty() ? "" : heldNow.type().identifier().toString();
        // The skull (Slice 60 — SkullItem.useOn): the block + the tile +
        // the summon consult override the generic placement (the FACING
        // rule and the typed drop both need the tile).
        if (heldId.equals("minecraft:skull")
                || creativeHeld.map(t -> t.identifier().value().equals("skull")).orElse(false)) {
            placeSkullOnTick(session, clicked, face, creativeHeld);
            return;
        }
        if (heldId
                .equals("minecraft:sign")
                || creativeHeld.map(t -> SignManager.isSignType(t)).orElse(false)) {
            placeSignOnTick(session, clicked, face, creativeHeld);
            return; // the sign's facing overrides the generic placement
        }
        if (isBedHalf(current)) {
            sleepOnTick(session, clicked);
            return; // the bed consumes the right-click (the historical sleep use)
        }
        if (isDoorHalf(current)) {
            // The reference's use() (DoorBlock lines 124-141): clicking
            // EITHER half swings the pair (the upper redirects down to the
            // lower); the iron door's material refuses the swing but still
            // consumes the use (use returns true before the toggle).
            BlockPosition lower = WoodDoorFamily.isUpper(current)
                    ? clicked.offset(0, -1, 0) : clicked;
            if (WoodDoorFamily.isDoor(world.getBlock(lower))) {
                toggleDoorOnTick(lower);
            }
            return; // the swing consumes the right-click (the historical door
                    // use: a door swings regardless of what the hand holds)
        }
        if (net.zaminmc.torch.server.block.FenceGateBlocks.isGate(current)) {
            // The gate's own use() (FenceGateBlock lines 94-110): the
            // open/close flip with the far-side facing rewrite — the gate
            // swings regardless of what the hand holds (the reference's
            // own true before any item consult).
            toggleFenceGateOnTick(session, clicked, current);
            return;
        }
        if (net.zaminmc.torch.server.block.TrapdoorBlocks.isTrapdoor(current)) {
            // The trapdoor's own use() (TrapdoorBlock lines 107-116): the
            // wood flips OPEN (the doEvent 1003/1006 pair riding the fx
            // bus), the iron's material refusal still consumes the use
            // (the reference's return true).
            if (!net.zaminmc.torch.server.block.TrapdoorBlocks.isIron(
                    net.zaminmc.torch.server.block.TrapdoorBlocks.familyOf(current))) {
                boolean opening = !net.zaminmc.torch.server.block.TrapdoorBlocks.isOpen(current);
                world.setBlock(clicked, net.zaminmc.torch.server.block.TrapdoorBlocks
                        .flipOpen(current));
                fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                        opening ? "random.door_open" : "random.door_close", 0.8f, 1.0f);
            }
            return; // the swing consumed the right-click (either family)
        }
        if (net.zaminmc.torch.server.block.TrapdoorBlocks.isTrapdoorItem(heldId)
                || creativeHeld.map(t -> net.zaminmc.torch.server.block.TrapdoorBlocks
                        .isTrapdoorItem(t.identifier().value())).orElse(false)) {
            placeTrapdoorOnTick(session, clicked, face, creativeHeld, cursorY);
            return; // the wall-mount rule overrides the generic placement
        }
        if (WoodDoorFamily.isDoorItem(heldId)
                || creativeHeld.map(t -> WoodDoorFamily.isDoorItem(
                        t.identifier().value())).orElse(false)) {
            placeDoorOnTick(session, clicked, face, creativeHeld);
            return; // the two-half commit overrides the generic placement
        }
        if (net.zaminmc.torch.server.block.FenceGateBlocks.isGateItem(heldId)
                || creativeHeld.map(t -> net.zaminmc.torch.server.block.FenceGateBlocks
                        .isGateItem(t.identifier().value())).orElse(false)) {
            placeFenceGateOnTick(session, clicked, face, creativeHeld);
            return; // the look-facing gate overrides the generic placement
        }
        if (heldId
                .equals("minecraft:ladder")
                || creativeHeld.map(t -> t.identifier().value().equals("ladder")).orElse(false)) {
            placeLadderOnTick(session, clicked, face, creativeHeld);
            return; // the wall-facing ladder overrides the generic placement
        }
        if (heldId
                .equals("minecraft:bed")
                || creativeHeld.map(t -> t.identifier().value().equals("bed")).orElse(false)) {
            placeBedOnTick(session, clicked, face, creativeHeld);
            return; // the two-half bed overrides the generic placement
        }
        // The ender eye's frame arm (Slice 54 — EnderEyeItem.useOn lines
        // 22-70, 1:1): an empty frame takes the eye (the facing preserved
        // through the engine's flattened pair), the survival hand pays the
        // stack, the sixteen SMOKE_NORMAL particles ride the reference's
        // own 5/16..11/16 offset band, and the frame-complete walk may
        // fill the 3x3 interior with the portal block.
        if (heldId.equals("minecraft:ender_eye")
                || creativeHeld.map(t -> t.identifier().value().equals("ender_eye")).orElse(false)) {
            BlockType clickedBlock = world.getBlock(clicked);
            if (net.zaminmc.torch.server.block.EndPortalBlocks.isEmptyFrame(
                    clickedBlock.identifier())) {
                int frameFacing = net.zaminmc.torch.server.block.EndPortalBlocks
                        .frameFacingOf(clickedBlock.identifier());
                if (frameFacing >= 0
                        && distanceSquaredEyeToBlock(session.position(), clicked) <= 4.5 * 4.5) {
                    world.setBlock(clicked, blockRegistry.require(
                            net.zaminmc.torch.server.block.EndPortalBlocks
                                    .eyedFrameOf(clickedBlock.identifier()).orElseThrow()));
                    consumePlaced(session, creativeHeld);
                    // The reference's own sixteen-particle band: the
                    // 5/16..11/16 offsets at the frame's 0.8125 height.
                    for (int i = 0; i < 16; i++) {
                        double ox = (5.0 + gameplayRandom.nextFloat() * 6.0) / 16.0;
                        double oz = (5.0 + gameplayRandom.nextFloat() * 6.0) / 16.0;
                        fxManager.smoke(new Position(
                                clicked.x() + ox, clicked.y() + 0.8125, clicked.z() + oz));
                    }
                    // The frame-complete walk (the reference's own
                    // geometry over the just-eyed frame).
                    net.zaminmc.torch.server.block.EndPortalBlocks.tryFillPortal(
                            new net.zaminmc.torch.server.block.EndPortalBlocks.FrameWorld() {
                                @Override
                                public net.zaminmc.torch.block.BlockType getBlock(
                                        net.zaminmc.torch.block.BlockPosition at) {
                                    return world.getBlock(at);
                                }

                                @Override
                                public void setBlock(net.zaminmc.torch.block.BlockPosition at,
                                        net.zaminmc.torch.block.BlockType type) {
                                    world.setBlock(at, type);
                                }
                            }, clicked, frameFacing);
                }
                return; // the frame arm consumes the use either way
            }
        }
        // The anvil (Slice 53 — the facing-and-damage placement overrides
        // the generic arm).
        if (heldId.equals("minecraft:anvil")
                || creativeHeld.map(t -> net.zaminmc.torch.server.block.AnvilBlocks
                        .isAnvil(t.identifier())).orElse(false)) {
            placeAnvilOnTick(session, clicked, face, creativeHeld);
            return;
        }
        if (heldId.endsWith("_slab") || heldId.equals("stone_slab2")
                || creativeHeld.map(t -> isSlabIdentifier(t.identifier().value())).orElse(false)) {
            placeSlabOnTick(session, clicked, face, creativeHeld, cursorY);
            return; // the half/double rule overrides the generic placement
        }
        if (heldId.equals("minecraft:oak_stairs") || heldId.equals("minecraft:cobblestone_stairs")
                || creativeHeld.map(t -> isStairsIdentifier(t.identifier().value())).orElse(false)) {
            placeStairsOnTick(session, clicked, face, creativeHeld);
            return; // the ascending-facing rule overrides the generic placement
        }
        // The lily pad's own use (Slice 42 — LilyPadItem.startUsing lines
        // 26-51): the aimed cell must be a WATER SOURCE with air above —
        // the pad takes the surface cell (blockpos.up()).
        if (heldId.equals("minecraft:waterlily")
                || creativeHeld.map(t -> t.identifier().value().equals("waterlily")).orElse(false)) {
            BlockType aimed = world.getBlock(clicked);
            if (FluidBlocks.kindOf(aimed.identifier()) == FluidBlocks.Kind.WATER
                    && FluidBlocks.isSource(aimed.identifier())
                    && world.getBlock(clicked.offset(0, 1, 0)).equals(world.airType())
                    && distanceSquaredEyeToBlock(session.position(), clicked) <= 4.5 * 4.5) {
                if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                        && !session.inventory().held().isEmpty()) {
                    world.setBlock(clicked.offset(0, 1, 0), BuiltinBlocks.WATERLILY);
                    consumePlaced(session, creativeHeld);
                } else if (!creativeHeld.isEmpty() || session.gamemode() == GameMode.CREATIVE) {
                    world.setBlock(clicked.offset(0, 1, 0), BuiltinBlocks.WATERLILY);
                    consumePlaced(session, creativeHeld);
                }
            }
            return; // the pad's use consumed the right-click either way
        }
        if (heldId.equals("minecraft:rail")
                || net.zaminmc.torch.server.block.RailBlocks.isRailItem(heldId)
                || creativeHeld.map(t -> WorldSolidity.isRail(t.identifier())).orElse(false)) {
            placeRailOnTick(session, clicked, face, creativeHeld);
            return; // the rail's look orientation overrides the generic placement
        }
        if (heldId.equals("minecraft:redstone")
                || creativeHeld.map(t -> t.identifier().value().equals("redstone")).orElse(false)) {
            placeRedstoneDustOnTick(session, clicked, face, creativeHeld);
            return; // the dust's floor gate + the wire placement override
        }
        if (heldId.equals("minecraft:redstone_torch")
                || creativeHeld.map(t -> t.identifier().value().equals("redstone_torch")).orElse(false)) {
            placeRedstoneTorchOnTick(session, clicked, face, creativeHeld);
            return; // the torch's attachment face overrides the generic placement
        }
        if (heldId.equals("minecraft:repeater")
                || creativeHeld.map(t -> t.identifier().value().equals("repeater")).orElse(false)) {
            placeRepeaterOnTick(session, clicked, face, creativeHeld);
            return; // the look-facing rule overrides the generic placement
        }
        if (heldId.equals("minecraft:comparator")
                || creativeHeld.map(t -> t.identifier().value().equals("comparator")).orElse(false)) {
            placeComparatorOnTick(session, clicked, face, creativeHeld);
            return; // the look-facing rule overrides the generic placement
        }
        if (heldId.equals("minecraft:piston") || heldId.equals("minecraft:sticky_piston")
                || creativeHeld.map(t -> t.identifier().value().equals("piston")
                        || t.identifier().value().equals("sticky_piston")).orElse(false)) {
            placePistonOnTick(session, clicked, face, creativeHeld,
                    heldId.equals("minecraft:sticky_piston"));
            return; // the eye/look-facing rule overrides the generic placement
        }
        if (heldId.equals("minecraft:ender_chest")
                || creativeHeld.map(t -> t.identifier().value().startsWith("ender_chest_")).orElse(false)) {
            placeEnderChestOnTick(session, clicked, face, creativeHeld);
            return; // the player-facing rule overrides the generic placement
        }
        if (heldId.equals("minecraft:hopper")
                || creativeHeld.map(t -> t.identifier().value().equals("hopper")).orElse(false)) {
            placeHopperOnTick(session, clicked, face, creativeHeld);
            return; // the clicked-face-opposite rule overrides the generic placement
        }
        if (heldId.equals("minecraft:daylight_detector")
                || creativeHeld.map(t -> t.identifier().value().equals("daylight_detector")).orElse(false)) {
            placeDaylightDetectorOnTick(session, clicked, face, creativeHeld);
            return; // the detector's p0 state overrides the generic placement
        }
        if (heldId.equals("minecraft:dispenser") || heldId.equals("minecraft:dropper")
                || creativeHeld.map(t -> t.identifier().value().equals("dispenser")
                        || t.identifier().value().equals("dropper")).orElse(false)) {
            placeDispenserOnTick(session, clicked, face, creativeHeld,
                    heldId.equals("minecraft:dropper"));
            return; // the piston-style eye/look facing overrides the generic placement
        }
        if (heldId.equals("minecraft:string")
                || creativeHeld.map(t -> t.identifier().value().equals("string")).orElse(false)) {
            placeTripwireOnTick(session, clicked, face, creativeHeld);
            return; // the string places the wire (the reference's ItemBlock)
        }
        if (heldId.equals("minecraft:tripwire_hook")
                || creativeHeld.map(t -> t.identifier().value().equals("tripwire_hook")).orElse(false)) {
            placeTripwireHookOnTick(session, clicked, face, creativeHeld);
            return; // the hook's solid-wall rule overrides the generic placement
        }
        if (heldId.equals("minecraft:lever")
                || creativeHeld.map(t -> t.identifier().value().equals("lever")).orElse(false)) {
            placeLeverOnTick(session, clicked, face, creativeHeld);
            return; // the lever's attachment walk overrides the generic placement
        }
        if (heldId.equals("minecraft:stone_button") || heldId.equals("minecraft:wooden_button")
                || creativeHeld.map(t -> t.identifier().value().equals("stone_button")
                        || t.identifier().value().equals("wooden_button")).orElse(false)) {
            placeButtonOnTick(session, clicked, face, creativeHeld,
                    heldId.equals("minecraft:wooden_button"));
            return; // the button's clicked-face mounting overrides the generic placement
        }
        if (heldId.equals("minecraft:stone_pressure_plate")
                || heldId.equals("minecraft:wooden_pressure_plate")
                || creativeHeld.map(t -> t.identifier().value().equals("stone_pressure_plate")
                        || t.identifier().value().equals("wooden_pressure_plate")).orElse(false)) {
            placePlateOnTick(session, clicked, face, creativeHeld,
                    heldId.equals("minecraft:wooden_pressure_plate"));
            return; // the plate's floor gate overrides the generic placement
        }
        if (heldId.equals("minecraft:boat")
                || creativeHeld.map(t -> t.identifier().value().equals("boat")).orElse(false)) {
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, true, false);
            return; // the boat spawns at the water the use names
        }
        if (heldId.equals("minecraft:minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("minecart")).orElse(false)) {
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, false, false);
            return; // the minecart spawns on the rail the use names
        }
        if (heldId.equals("minecraft:chest_minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("chest_minecart")).orElse(false)) {
            // The chest minecart's own place arm (Slice 21a — MinecartItem
            // over the chest variant): the same rail gate, the chest body.
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, false, true);
            return;
        }
        if (heldId.equals("minecraft:furnace_minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("furnace_minecart")).orElse(false)) {
            // The furnace minecart's place arm (Slice 66 — MinecartItem
            // over the furnace variant).
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, false, false, true, false);
            return;
        }
        if (heldId.equals("minecraft:tnt_minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("tnt_minecart")).orElse(false)) {
            // The TNT minecart's place arm (Slice 66 — MinecartItem over
            // the TNT variant).
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, false, false, false, true);
            return;
        }
        if (heldId.equals("minecraft:hopper_minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("hopper_minecart")).orElse(false)) {
            // The hopper minecart's place arm (Slice 66 — MinecartItem
            // over the hopper variant).
            spawnVehicleUseOnTick(session, clicked, face, creativeHeld, false, false, false, false,
                    true);
            return;
        }
        if (heldId.equals("minecraft:command_block_minecart")
                || creativeHeld.map(t -> t.identifier().value().equals("command_block_minecart")).orElse(false)) {
            // The command-block minecart's place arm (Slice 66 —
            // MinecartItem over the command variant).
            spawnCommandBlockCartOnTick(session, clicked, face, creativeHeld);
            return;
        }
        if (heldId.equals("minecraft:pumpkin") || heldId.equals("minecraft:lit_pumpkin")
                || creativeHeld.map(t -> t.identifier().value().equals("pumpkin")
                        || t.identifier().value().equals("lit_pumpkin")).orElse(false)) {
            placePumpkinOnTick(session, clicked, face, creativeHeld,
                    heldId.equals("minecraft:lit_pumpkin"));
            return; // the golem-build check rides the placement (Slice 17d)
        }
        // The item frame's placement (Slice 28a — DecorationItem.useOn
        // lines 21-42 over ItemFrameEntity): the vertical faces refuse,
        // the body lands at the clicked face's offset cell, and the
        // survival gate (the solid wall + the clear box) refuses without
        // the consume. The frame's facing = the clicked face's own
        // direction (the art hangs ON that face, pointing away).
        if (heldId.equals("minecraft:item_frame")
                || creativeHeld.map(t -> t.identifier().value().equals("item_frame")).orElse(false)) {
            placeItemFrameOnTick(session, clicked, face, creativeHeld);
            return;
        }
        // The painting's placement (Slice 28b — the same DecorationItem
        // walk over PaintingEntity): the fit roll collects every motive
        // that survives the wall and one lands at random.
        if (heldId.equals("minecraft:painting")
                || creativeHeld.map(t -> t.identifier().value().equals("painting")).orElse(false)) {
            placePaintingOnTick(session, clicked, face, creativeHeld);
            return;
        }
        // The armor stand's placement (Slice 28c — ArmorStandItem.useOn
        // lines 22-68): the DOWN face refuses, the cell + headroom gate,
        // the entity box gate, the 45-degree yaw snap, the pose roll.
        if (heldId.equals("minecraft:armor_stand")
                || creativeHeld.map(t -> t.identifier().value().equals("armor_stand")).orElse(false)) {
            placeArmorStandOnTick(session, clicked, face, creativeHeld);
            return;
        }
        // The carpet family's placement (Slice 31 — CarpetBlock.canBePlaced
        // over canSurvive lines 91-97 + the ItemBlock damage walk): the
        // support gate refuses AIR below (water and torches carry it — the
        // reference's own `!world.isAir(pos.down())` shape, softer than the
        // plate's solid bed), and the held stack's damage names the color.
        if (heldId.equals("minecraft:carpet")
                || creativeHeld.map(t -> net.zaminmc.torch.server.block.DyeFamilyBlocks
                        .isCarpet(t.identifier())).orElse(false)) {
            placeCarpetOnTick(session, clicked, face, creativeHeld);
            return;
        }
        blockInteraction.placeFromUseOnTick(session, clicked, face, creativeHeld);
    }

    /**
     * The face code to the engine's byIdHorizontal facing (the clicked
     * face's own direction: 2=north(-Z), 3=south(+Z), 4=west(-X),
     * 5=east(+X) over the 0=south,1=west,2=north,3=east order).
     */
    private static int faceToFacingCode(int face) {
        return switch (face) {
            case 2 -> 2; // north
            case 3 -> 0; // south
            case 4 -> 1; // west
            case 5 -> 3; // east
            default -> -1;
        };
    }

    /**
     * The frame's placement arm (Slice 28a): the reach gate, the
     * horizontal-face gate, the survival gate, then the consume — the
     * refused placement still eats the click (DecorationItem.useOn's
     * own always-true).
     */
    private void placeItemFrameOnTick(PlayerSession session, BlockPosition clicked, int face,
                                      java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        int facing = faceToFacingCode(face);
        if (facing < 0) {
            return; // the vertical faces: the reference's own refusal
        }
        if (decorations.placeFrame(clicked, face, facing)) {
            consumePlaced(session, creativeHeld); // stack.size--: the body survived
        }
    }

    /**
     * The painting's placement arm (Slice 28b): the same walk with the
     * motive fit roll — a wall that fits nothing consumes the click but
     * spares the stack.
     */
    private void placePaintingOnTick(PlayerSession session, BlockPosition clicked, int face,
                                     java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        int facing = faceToFacingCode(face);
        if (facing < 0) {
            return;
        }
        if (decorations.placePainting(clicked, face, facing)) {
            consumePlaced(session, creativeHeld); // a landed painting consumes one unit
        }
    }

    /**
     * The armor stand's placement arm (Slice 28c): the reach gate, the
     * DOWN-face gate, the cell + headroom + entity-box gates, the yaw
     * snap off the player's own look, then the consume.
     */
    private void placeArmorStandOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld) {
        if (distanceSquaredEyeToBlock(session.position(), clicked) > 4.5 * 4.5) {
            return;
        }
        if (face == 0) {
            return; // DOWN: the reference's own refusal
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (armorStands.place(clicked, face, session.rotation().yaw(), gameplayRandom)) {
            consumePlaced(session, creativeHeld);
        }
    }

    /**
     * The pumpkin placement (Slice 17d — PumpkinBlock.onAdded +
     * trySpawnGolem lines 53-112): the block lands like any woody block
     * (the historical FACING rotation ledgers with the metadata slice —
     * the engine registers the unrotated form), then the build scan runs:
     * the snow pattern (two snow blocks under the head) builds the snow
     * golem, the iron cross (the 3-wide chest row + the base, the ~ air
     * guards at the head's sides and the base's sides) builds the iron
     * golem — player-created, both bodies landing at the pattern's own
     * spawn cell with the +0.05 hop. Tick-thread context.
     */
    private void placePumpkinOnTick(PlayerSession session, BlockPosition clicked, int face,
                                    java.util.Optional<BlockType> creativeHeld, boolean lit) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // PumpkinBlock.canBePlaced (lines 123-125): the replaceable material
        // gate + the solid top below.
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return;
        }
        world.setBlock(target, lit ? BuiltinBlocks.LIT_PUMPKIN : BuiltinBlocks.PUMPKIN);
        consumePlaced(session, creativeHeld);
        trySpawnGolem(target);
    }

    /**
     * The golem build scan (trySpawnGolem 1:1 — the snow column first, the
     * iron cross second, the body blocks clearing to air on the match):
     * the snow golem spawns at the bottom snow cell, the iron golem at the
     * base's center cell with the player-created flag.
     */
    private void trySpawnGolem(BlockPosition head) {
        // The snow pattern (getSnowGolemPattern): "^" / "#" / "#" — the
        // head over two snow blocks.
        boolean snowBody = isBlockAt(head.offset(0, -1, 0), BuiltinBlocks.SNOW)
                && isBlockAt(head.offset(0, -2, 0), BuiltinBlocks.SNOW);
        if (snowBody) {
            world.setBlock(head, world.airType());
            world.setBlock(head.offset(0, -1, 0), world.airType());
            world.setBlock(head.offset(0, -2, 0), world.airType());
            BlockPosition at = head.offset(0, -2, 0);
            mobManager.spawnAt(net.zaminmc.torch.server.entity.MobType.SNOW_GOLEM,
                    new Position(at.x() + 0.5, at.y() + 0.05, at.z() + 0.5));
            return;
        }
        // The iron pattern (getIronGolemPattern): "~^~" / "###" / "~#~" — the
        // head over the 3-wide chest row, the single base under the middle
        // — the reference's BlockPattern.find tries both horizontal axes.
        for (int axis = 0; axis < 2; axis++) {
            int dx = axis == 0 ? 1 : 0;
            int dz = axis == 0 ? 0 : 1;
            BlockPosition chest = head.offset(0, -1, 0);
            BlockPosition base = head.offset(0, -2, 0);
            boolean ironCross = isBlockAt(chest, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(chest.offset(dx, 0, dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(chest.offset(-dx, 0, -dz), BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(base, BuiltinBlocks.IRON_BLOCK)
                    && isBlockAt(head.offset(dx, 0, dz), world.airType())
                    && isBlockAt(head.offset(-dx, 0, -dz), world.airType())
                    && isBlockAt(base.offset(dx, 0, dz), world.airType())
                    && isBlockAt(base.offset(-dx, 0, -dz), world.airType());
            if (!ironCross) {
                continue;
            }
            world.setBlock(head, world.airType());
            world.setBlock(chest, world.airType());
            world.setBlock(chest.offset(dx, 0, dz), world.airType());
            world.setBlock(chest.offset(-dx, 0, -dz), world.airType());
            world.setBlock(base, world.airType());
            MobEntity golem = mobManager.spawnAt(
                    net.zaminmc.torch.server.entity.MobType.IRON_GOLEM,
                    new Position(base.x() + 0.5, base.y() + 0.05, base.z() + 0.5));
            if (golem != null) {
                golem.setPersistent(true); // setPlayerCreated: never despawns
            }
            return;
        }
    }

    /** @return whether the cell holds exactly the given block type. */
    private boolean isBlockAt(BlockPosition at, BlockType type) {
        return world.getBlock(at).equals(type);
    }

    /**
     * The redstone dust placement (the historical ItemRedstone): the wire
     * lands on a solid top (RedstoneWireBlock.canBePlaced lines 92-94 — a
     * solid block or glowstone under it), the cascade fills its power on
     * the same commit. Tick-thread context.
     */
    private void placeRedstoneDustOnTick(PlayerSession session, BlockPosition clicked, int face,
                                          java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return; // the wire needs its solid bed (the vanilla gate)
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.wireOfPower(0));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The repeater's use (RepeaterBlock.use lines 38-45): the right-click
     * steps the DELAY property through 1..4 and back to 1 — the state swap
     * preserves the facing and the powered pair. Returns whether the use
     * was consumed. Tick-thread context.
     */
    private boolean cycleRepeaterDelayOnTick(BlockPosition clicked) {
        BlockType current = world.getBlock(clicked);
        if (!net.zaminmc.torch.server.redstone.RedstoneBlocks.isRepeater(current)) {
            return false;
        }
        int delay = net.zaminmc.torch.server.redstone.RedstoneBlocks.repeaterDelay(current);
        int next = delay >= 4 ? 1 : delay + 1;
        world.setBlock(clicked, net.zaminmc.torch.server.redstone.RedstoneBlocks.repeaterWithDelay(current, next));
        fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                "random.click", 0.3f, 0.6f);
        return true;
    }

    /**
     * The redstone torch placement (TorchBlock.getPlacementState lines 76-88,
     * inherited by RedstoneTorchBlock): the FACING is the clicked face when
     * the attachment holds (the horizontal face's wall is solid, the top face
     * sits on a solid bed), else the first horizontal wall that holds. A
     * ceiling click never attaches (vanilla's DOWN arm has no attachment).
     * Tick-thread context.
     */
    private void placeRedstoneTorchOnTick(PlayerSession session, BlockPosition clicked, int face,
                                          java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // The clicked face's attachment (canAttach lines 69-73): a horizontal
        // face needs the solid wall behind it; the top face needs the solid
        // bed below. The FACING ids ride the face codes 1..5.
        int facing = switch (face) {
            case 1 -> world.getBlock(target.offset(0, -1, 0)) != null
                    && WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0))) ? 1 : -1;
            case 2 -> WorldSolidity.isSolid(world.getBlock(target.offset(0, 0, 1))) ? 2 : -1;
            case 3 -> WorldSolidity.isSolid(world.getBlock(target.offset(0, 0, -1))) ? 3 : -1;
            case 4 -> WorldSolidity.isSolid(world.getBlock(target.offset(1, 0, 0))) ? 4 : -1;
            case 5 -> WorldSolidity.isSolid(world.getBlock(target.offset(-1, 0, 0))) ? 5 : -1;
            default -> -1; // the ceiling arm never attaches
        };
        if (facing < 0) {
            // The fallback walk (lines 81-85): the first horizontal wall.
            if (WorldSolidity.isSolid(world.getBlock(target.offset(0, 0, 1)))) {
                facing = 2;
            } else if (WorldSolidity.isSolid(world.getBlock(target.offset(0, 0, -1)))) {
                facing = 3;
            } else if (WorldSolidity.isSolid(world.getBlock(target.offset(1, 0, 0)))) {
                facing = 4;
            } else if (WorldSolidity.isSolid(world.getBlock(target.offset(-1, 0, 0)))) {
                facing = 5;
            } else if (WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
                facing = 1;
            } else {
                return; // no attachment anywhere: the placement refuses
            }
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.torchOfFacing(facing, true));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The lever placement (LeverBlock.getPlacementState lines 104-117): the
     * clicked face holds the mount when its block is solid, else the first
     * horizontal wall, else the floor. The facing rides the player's look
     * axis on the up/down mounts (UP_X / DOWN_X vs UP_Z / DOWN_Z — the
     * engine keeps the Z variants, the look split's rendering nicety).
     * Tick-thread context.
     */
    private void placeLeverOnTick(PlayerSession session, BlockPosition clicked, int face,
                                  java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // The lever's facing keys by the clicked face (1..5; the ceiling arm
        // mounts DOWN, the X/Z split rides the look axis).
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        boolean lookingX = ((int) (yaw / 90.0) % 4) % 2 == 1; // W/E bands
        String key = switch (face) {
            case 1 -> lookingX ? "up_x" : "up_z";
            case 0 -> lookingX ? "down_x" : "down_z";
            case 2 -> "north";
            case 3 -> "south";
            case 4 -> "west";
            case 5 -> "east";
            default -> null;
        };
        if (key == null) {
            return;
        }
        // The clicked face's mount must be solid (canSurvive).
        int[] mountOffset = switch (face) {
            case 0 -> new int[]{0, 1, 0};  // ceiling: the block above
            case 1 -> new int[]{0, -1, 0}; // floor: the block below
            case 2 -> new int[]{0, 0, 1};  // north face: the block south of the target
            case 3 -> new int[]{0, 0, -1};
            case 4 -> new int[]{1, 0, 0};
            case 5 -> new int[]{-1, 0, 0};
            default -> null;
        };
        if (mountOffset == null
                || !WorldSolidity.isSolid(world.getBlock(target.offset(
                        mountOffset[0], mountOffset[1], mountOffset[2])))) {
            // The fallback walk: the first solid mount around the target.
            boolean placed = false;
            if (WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
                world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(5, false));
                placed = true;
            }
            if (!placed) {
                return; // no mount anywhere: the placement refuses
            }
        } else {
            world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(
                    leverFacingIdOf(key), false));
        }
        consumePlaced(session, creativeHeld);
    }

    /** The lever facing-id lookup (the reference's Facing.getId order). */
    private static int leverFacingIdOf(String key) {
        return switch (key) {
            case "down_x" -> 0;
            case "east" -> 1;
            case "west" -> 2;
            case "south" -> 3;
            case "north" -> 4;
            case "up_z" -> 5;
            case "up_x" -> 6;
            case "down_z" -> 7;
            default -> 5;
        };
    }

    /**
     * The button placement (ButtonBlock.getPlacementState lines 85-88): the
     * FACING is the clicked face when the mount holds, else DOWN (the
     * reference's fallback — a button facing down with no mount pops on
     * the first neighbor check). Tick-thread context.
     */
    private void placeButtonOnTick(PlayerSession session, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld, boolean wooden) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.buttonOf(face, false, wooden));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The plate placement (AbstractPressurePlateBlock.canBePlaced): the
     * solid top under the target. Tick-thread context.
     */
    private void placePlateOnTick(PlayerSession session, BlockPosition clicked, int face,
                                  java.util.Optional<BlockType> creativeHeld, boolean wooden) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return; // the plate needs its solid bed
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.plateOf(false, wooden));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The carpet family's placement (Slice 31 — CarpetBlock lines 91-97 +
     * the ItemBlock damage walk): the support gate refuses only AIR below
     * (a carpet over water or a torch stands — the reference's own
     * {@code !world.isAir(pos.down())}, softer than the plate's solid bed),
     * the body gate never applies (the 1/16 shape never clips a player),
     * and the held stack's damage names the family's color member.
     * Tick-thread context.
     */
    private void placeCarpetOnTick(PlayerSession session, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        // The color member: creative claims its block variant directly,
        // survival reads the held stack's damage field.
        BlockType carpet;
        if (creativeHeld.isPresent()) {
            carpet = creativeHeld.get();
        } else {
            net.zaminmc.torch.item.ItemStack held = session.inventory().held();
            if (held.isEmpty() || !held.type().identifier().value().equals("carpet")) {
                return;
            }
            java.util.Optional<BlockType> resolved = blockRegistry.lookup(
                    net.zaminmc.torch.server.block.DyeFamilyBlocks.carpetBlock(held.damage()));
            if (resolved.isEmpty()) {
                return;
            }
            carpet = resolved.get();
        }
        if (!net.zaminmc.torch.server.block.DyeFamilyBlocks.isCarpet(carpet.identifier())) {
            return; // the claimed block is not a family member
        }
        // The support gate: only AIR refuses (canSurvive lines 95-97).
        if (world.getBlock(target.offset(0, -1, 0)).equals(world.airType())) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() == GameMode.CREATIVE) {
            // a creative claim that resolved through the held stack
            world.setBlock(target, carpet);
            return;
        }
        world.setBlock(target, carpet);
        consumePlaced(session, creativeHeld);
    }

    /**
     * The repeater placement (DiodeBlock.getPlacementState lines 154-157):
     * FACING = the player's horizontal facing OPPOSITE — the diode's input
     * side faces the player, the signal flows away from the look. The solid
     * top gate rides canBePlaced (lines 29-31). Tick-thread context.
     */
    private void placeRepeaterOnTick(PlayerSession session, BlockPosition clicked, int face,
                                     java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return; // the diode needs its solid bed
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        int facing = net.zaminmc.torch.server.redstone.RedstoneBlocks.oppositeFacing(lookBand);
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.repeaterOf(facing, 1, false));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The comparator placement (ComparatorBlock.getPlacementState lines
     * 258-260 + DiodeBlock.canBePlaced lines 29-31): the same diode rule as
     * the repeater — the solid top bed, the look-facing FACING (the input
     * side faces the player), COMPARE mode, unpowered. Tick-thread context.
     */
    private void placeComparatorOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return; // the diode needs its solid bed
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        int facing = net.zaminmc.torch.server.redstone.RedstoneBlocks.oppositeFacing(lookBand);
        world.setBlock(target, net.zaminmc.torch.server.redstone.RedstoneBlocks.comparatorOf(
                facing, false, false));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The piston placement (PistonBaseBlock.getFacingForPlacement lines
     * 232-245 + getPlacementState lines 66-68): when the player stands close
     * (within two blocks on both horizontal axes) the facing follows the eye
     * height — above two blocks up, below the feet down — else the piston
     * faces the player's look opposite (the arm extends toward the player).
     * Tick-thread context.
     */
    private void placePistonOnTick(PlayerSession session, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld, boolean sticky) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // The eye-height walk when the player is close enough horizontally.
        double px = session.position().x();
        double pz = session.position().z();
        int facing;
        if (Math.abs((float) px - target.x()) < 2.0f && Math.abs((float) pz - target.z()) < 2.0f) {
            double eye = session.position().y() + 1.62;
            if (eye - target.y() > 2.0) {
                facing = 1; // UP
            } else if (target.y() - eye > 0.0) {
                facing = 0; // DOWN
            } else {
                facing = lookOppositeDirection(session);
            }
        } else {
            facing = lookOppositeDirection(session);
        }
        world.setBlock(target, net.zaminmc.torch.server.piston.PistonBlocks.pistonOf(
                facing, false, sticky));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The string's wire placement (the reference's ItemBlock over
     * TripwireBlock): the wire lands on the clicked face's cell, the
     * suspended bit resolving from the floor at the commit listener.
     */
    private void placeTripwireOnTick(PlayerSession session, BlockPosition clicked, int face,
                                      java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.TripwireBlocks.wireOf(
                false, false, false, false));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The hook's placement (TripwireHookBlock.getPlacementState lines
     * 92-100 + canBePlaced): a horizontal clicked face with a solid block
     * behind — the FACING points at the clicked face's direction (away
     * from the wall).
     */
    private void placeTripwireHookOnTick(PlayerSession session, BlockPosition clicked, int face,
                                         java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (!net.zaminmc.torch.server.redstone.TripwireSystem.hookPlacementSupported(
                world, target, face)) {
            return; // the solid-wall gate
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        world.setBlock(target, net.zaminmc.torch.server.redstone.TripwireBlocks.hookOf(
                face & 7, false, false));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The ender chest placement (Slice 29a — EnderChestBlock.
     * getPlacementState lines 63-65 + onPlaced lines 68-70): FACING = the
     * placing player's horizontal facing OPPOSITE (the front points at the
     * player, the chest family's own rule). Tick-thread context.
     */
    private void placeEnderChestOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        // The look's opposite in reference Direction ids: S->N(2), W->E(5),
        // N->S(3), E->W(4) — getHorizontalFacing().getOpposite().
        int facing = switch (lookBand) {
            case 0 -> 2; // looking south, the front faces north
            case 1 -> 5; // looking west, the front faces east
            case 2 -> 3; // looking north, the front faces south
            default -> 4; // looking east, the front faces west
        };
        world.setBlock(target,
                net.zaminmc.torch.server.chest.EnderChestBlocks.enderChestOf(facing));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The hopper placement (HopperBlock.getPlacementState lines 65-72): the
     * clicked face's OPPOSITE is the output facing — an UP outcome clamps to
     * DOWN (the FACING predicate excludes UP). The hopper lands ENABLED; a
     * neighbor signal flips the pair on the next re-arm. Tick-thread
     * context.
     */
    private void placeHopperOnTick(PlayerSession session, BlockPosition clicked, int face,
                                    java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        int facing = net.zaminmc.torch.server.hopper.HopperSystem.placementFacing(face);
        world.setBlock(target, net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(facing, true));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The daylight detector placement (Slice 27c — DaylightDetectorBlock's
     * defaultState: POWER 0): the flattened family's p0 state lands (the
     * 20-tick walk takes it from there). Tick-thread context.
     */
    private void placeDaylightDetectorOnTick(PlayerSession session, BlockPosition clicked,
                                              int face,
                                              java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        world.setBlock(target, net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.ofPower(0));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The dispenser/dropper placement (Slice 11a —
     * DispenserBlock.getPlacementState line 143 + PistonBaseBlock.
     * getFacingForPlacement lines 232-245): the facing follows the
     * piston's own walk — the eye-height rule when the player stands close
     * (within two blocks on both horizontal axes), else the look opposite.
     * The block lands UNTRIGGERED; a neighbor signal arms it on the next
     * bus pass. Tick-thread context.
     */
    private void placeDispenserOnTick(PlayerSession session, BlockPosition clicked, int face,
                                      java.util.Optional<BlockType> creativeHeld, boolean dropper) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // The eye-height walk when the player is close enough horizontally
        // (the shared piston rule — getFacingForPlacement).
        double px = session.position().x();
        double pz = session.position().z();
        int facing;
        if (Math.abs((float) px - target.x()) < 2.0f && Math.abs((float) pz - target.z()) < 2.0f) {
            double eye = session.position().y() + 1.62;
            if (eye - target.y() > 2.0) {
                facing = 1; // UP
            } else if (target.y() - eye > 0.0) {
                facing = 0; // DOWN
            } else {
                facing = lookOppositeDirection(session);
            }
        } else {
            facing = lookOppositeDirection(session);
        }
        world.setBlock(target, dropper
                ? net.zaminmc.torch.server.dispenser.DispenserBlocks.dropperOf(facing, false)
                : net.zaminmc.torch.server.dispenser.DispenserBlocks.dispenserOf(facing, false));
        // The placement re-orientation (DispenserBlock.onAdded lines 49-75):
        // a dispenser placed facing into an opaque wall flips to the open
        // side (the flag-1 placement walk).
        dispenserSystem.updateFacingOnPlacement(target);
        consumePlaced(session, creativeHeld);
    }

    /**
     * The player's look-opposite as a reference Direction id (0=down 1=up
     * 2=north 3=south 4=west 5=east) — the piston placement's facing.
     */
    private static int lookOppositeDirection(PlayerSession session) {
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        int look = switch (lookBand) {
            case 0 -> 3; // south
            case 1 -> 4; // west
            case 2 -> 2; // north
            default -> 5; // east
        };
        return switch (look) {
            case 2 -> 3; // north <-> south
            case 3 -> 2;
            case 4 -> 5; // west <-> east
            case 5 -> 4;
            default -> look;
        };
    }

    /**
     * The reed and cactus placement rules (the historical ItemBlock special
     * cases): a sugar cane plants on grass/dirt/sand with water beside the
     * soil, a cactus stacks on sand or cactus with nothing solid beside.
     * Survival consumes the held stack, creative places the claimed item.
     * Returns whether the use was consumed. Tick-thread context.
     */
    private boolean useReedOnTick(PlayerSession session, BlockPosition clicked, int face,
                                  java.util.Optional<BlockType> creativeHeld) {
        net.zaminmc.torch.item.ItemStack heldStack = session.inventory().held();
        String held = heldStack.isEmpty() ? ""
                : heldStack.type().identifier().toString();
        boolean wantsCane = held.equals("minecraft:sugar_cane")
                || creativeHeld.map(t -> WorldSolidity.isSugarCane(t.identifier())).orElse(false);
        boolean wantsCactus = held.equals("minecraft:cactus")
                || creativeHeld.map(t -> WorldSolidity.isCactus(t.identifier())).orElse(false);
        if (!wantsCane && !wantsCactus) {
            return false;
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || !world.getBlock(target).equals(world.airType())) {
            return false; // no open cell: the generic path's no-op
        }
        BlockType soil = world.getBlock(target.offset(0, -1, 0));
        boolean allowed;
        if (wantsCane) {
            allowed = (soil.identifier().equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                    || soil.identifier().equals(BuiltinBlocks.DIRT.identifier())
                    || soil.identifier().equals(BuiltinBlocks.SAND.identifier())
                    || WorldSolidity.isSugarCane(soil.identifier()))
                    && waterBesideSoil(target.offset(0, -1, 0));
        } else {
            allowed = (soil.identifier().equals(BuiltinBlocks.SAND.identifier())
                    || WorldSolidity.isCactus(soil.identifier()));
            if (allowed) {
                // Nothing solid may sit beside a cactus (the historical rule).
                for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (WorldSolidity.isSolid(world.getBlock(target.offset(dir[0], 0, dir[1])))) {
                        allowed = false;
                        break;
                    }
                }
            }
            if (allowed) {
                // The full-cube stand-in never places inside the body's own
                // cells (feet or head).
                Position body = session.position();
                int bx = (int) Math.floor(body.x());
                int by = (int) Math.floor(body.y());
                int bz = (int) Math.floor(body.z());
                if (target.x() == bx && target.z() == bz
                        && (target.y() == by || target.y() == by + 1)) {
                    allowed = false;
                }
            }
        }
        if (!allowed) {
            return false; // the soil refuses: the generic path's no-op
        }
        world.setBlock(target, wantsCane ? BuiltinBlocks.SUGAR_CANE : BuiltinBlocks.CACTUS);
        if (session.gamemode() == GameMode.SURVIVAL) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
        return true;
    }

    /** @return whether any horizontal neighbor of the soil cell holds a fluid. */
    private boolean waterBesideSoil(BlockPosition soil) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (FluidBlocks.isFluid(world.getBlock(soil.offset(dir[0], 0, dir[1])).identifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cauldron's use arms (CauldronBlock.use lines 77-160): the water
     * bucket fills any non-full cauldron to 3 (the bucket empties in the
     * hand, survival only), the glass bottle drains one level into the
     * water potion (metadata 0 — the bottle pays one unit, survival
     * only). Both arms consume the use whatever the level (the
     * reference's true); anything else answers false — the placement
     * proposal follows. Tick-thread context.
     */
    private boolean useCauldronOnTick(PlayerSession session, BlockPosition clicked,
                                      BlockType cauldron) {
        net.zaminmc.torch.item.ItemStack held = session.inventory().held();
        if (held.isEmpty()) {
            // The reference's bare-hand true: the use consumed, nothing
            // happens (lines 82-85).
            return true;
        }
        int level = net.zaminmc.torch.server.cauldron.CauldronBlocks.levelOf(cauldron);
        if (held.type().equals(BuiltinItems.WATER_BUCKET)) {
            // Lines 89-99: the fill to 3, the bucket swap (survival).
            if (level < 3) {
                if (session.gamemode() != GameMode.CREATIVE) {
                    session.inventory().consumeHeld(1);
                    throwOverflow(session, java.util.List.of(session.inventory().pickUp(
                            ItemStack.of(BuiltinItems.BUCKET, 1))));
                }
                setCauldronLevel(clicked, 3);
                // The fill stat (CauldronBlock.use's own incrementStat).
                session.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.CAULDRONS_FILLED, 1);
                publishInventoryChanged(session);
            }
            return true;
        }
        if (held.type().equals(BuiltinItems.GLASS_BOTTLE)) {
            // Lines 100-120: the drain of one level into the water potion
            // (metadata 0); the bottle pays one unit (survival); a full
            // inventory drops the potion at the cauldron's rim.
            if (level > 0) {
                if (session.gamemode() != GameMode.CREATIVE) {
                    session.inventory().consumeHeld(1);
                    net.zaminmc.torch.item.ItemStack leftover = session.inventory().pickUp(
                            new ItemStack(BuiltinItems.POTION, 1, 0));
                    if (!leftover.isEmpty()) {
                        throwFromPlayer(session, leftover); // the rim drop (line 105)
                    }
                }
                setCauldronLevel(clicked, level - 1);
                // The drain stat (the same use row's CAULDRONS_USED).
                session.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.CAULDRONS_USED, 1);
                publishInventoryChanged(session);
            }
            return true;
        }
        // The armor/banner wash arms (lines 122-159): the engine's armor
        // carries no color model and no banner item exists — hasColor
        // answers false exactly like the reference's uncolored leather, and
        // the use falls through to the placement proposal.
        return false;
    }

    /**
     * The cauldron's level write (setLevel lines 162-165): the state swap
     * with the flag-2 shape (no neighbor re-light, the comparators woken).
     * Tick-thread context.
     */
    private void setCauldronLevel(BlockPosition position, int level) {
        world.setBlock(position,
                net.zaminmc.torch.server.cauldron.CauldronBlocks.of(level));
        redstoneSystem.wakeComparators(position);
    }

    /**
     * The flint and steel use (the historical ItemFlintAndSteel right-click):
     * fire lands against the clicked face when that cell is open or replacable
     * flora, and the steel wears one use. Returns whether the use was
     * consumed. Tick-thread context.
     */
    private boolean useFlintOnTick(PlayerSession session, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemStack heldStack = session.inventory().held();
        if (heldStack.isEmpty()
                || !heldStack.type().identifier().toString()
                        .equals("minecraft:flint_and_steel")) {
            return false;
        }
        // The TNT ignite arm (TntBlock.use lines 96-113): the steel clicked
        // on the TNT block itself primes it — the block leaves, the fuse
        // starts, the flint wears one use.
        if (tntSystem.useIgnite(clicked)) {
            if (session.gamemode() == GameMode.SURVIVAL
                    || session.gamemode() == GameMode.ADVENTURE) {
                session.inventory().damageHeld(1, gameplayRandom);
                publishInventoryChanged(session);
            }
            return true;
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return false;
        }
        BlockType at = world.getBlock(target);
        boolean replaceable = at.equals(world.airType())
                || at.identifier().equals(BuiltinBlocks.TALL_GRASS.identifier())
                || at.identifier().equals(BuiltinBlocks.DEAD_BUSH.identifier());
        if (!replaceable) {
            return false; // no open cell: the steel sparks at nothing
        }
        world.setBlock(target, BuiltinBlocks.FIRE);
        // The reference's FireBlock.onAdded arm (reference/1.8.8 block/
        // FireBlock.java lines 317-321): the placed fire first tries the
        // portal build — a valid obsidian frame replaces the fire cell with
        // the portal interior; the failed scan leaves the ordinary fire.
        net.zaminmc.torch.server.world.PortalFrameBuilder.createAt(world, target);
        fxManager.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                "fire.ignite", 1.0f, 0.9f + fxRandom.nextFloat() * 0.2f);
        if (session.gamemode() == GameMode.SURVIVAL
                || session.gamemode() == GameMode.ADVENTURE) {
            session.inventory().damageHeld(1, gameplayRandom);
            publishInventoryChanged(session);
        }
        return true;
    }

    /**
     * The spawn egg's block use (SpawnEggItem.useOn lines 63-92): against a
     * spawner the egg re-types the block entity (the reference's
     * {@code mobspawner.setType(Entities.getKey(metadata))} — an unknown id
     * clears the type the same way the reference's null key does) and the
     * tile republishes for the next chunk send; against anything else the
     * body spawns at the clicked face's offset cell center — the fence's UP
     * face lifts the spawn by its own half-block (the reference's
     * FenceBlock quirk, useOn lines 78-81). The stack pays one unit in
     * survival; an unregistered id never spawns (the containsKey gate).
     * Tick-thread context.
     */
    private void useSpawnEggOnBlock(PlayerSession session, BlockPosition clicked, int face,
                                    net.zaminmc.torch.item.ItemStack held) {
        net.zaminmc.torch.server.entity.MobType kind =
                net.zaminmc.torch.server.item.SpawnEggs.typeOf(held.damage());
        EngineWorld useWorld = worldFor(session.dimension());
        net.zaminmc.torch.block.BlockType block = useWorld.getBlock(clicked);
        if (block.identifier().equals(BuiltinBlocks.MOB_SPAWNER.identifier())) {
            // The spawner arm (useOn lines 68-82): the re-type lands even
            // for an id the spawn path refuses — the reference's own null
            // key. The delay keeps its clock (setType never touches it).
            spawnerManager.getOrCreate(session.dimension(), clicked).setType(kind);
            spawnerManager.refreshWireTiles();
            if (session.gamemode() != GameMode.CREATIVE) {
                session.inventory().consumeHeld(1);
                publishInventoryChanged(session);
            }
            return;
        }
        if (kind == null) {
            return; // the unknown id: no body, no payment (spawnEntity's gate)
        }
        // The spawn cell: the clicked face's offset (useOn line 82), with
        // the fence's UP face adding its 0.5 hull (lines 78-81).
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return; // a malformed face index: nothing to offset
        }
        double lift = 0.0;
        if (face == 1 && block.identifier().equals(BuiltinBlocks.FENCE.identifier())) {
            lift = 0.5;
        }
        mobManager.spawnFromEgg(kind, new Position(
                target.x() + 0.5, target.y() + lift, target.z() + 0.5));
        if (session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /**
     * The spawn egg's in-air use (SpawnEggItem.startUsing lines 94-131): the
     * crosshair's own liquid target — the 5.0-block liquid ray — is the
     * ONLY in-air spawn (a solid hit is the reference's quiet no-op: the
     * block-use path already owned it). The body lands at the liquid cell's
     * center and the stack pays one unit in survival. Tick-thread context.
     */
    private void useSpawnEggInAir(PlayerSession session) {
        BlockPosition liquidCell = rayTraceBlocks(session, true);
        if (liquidCell == null) {
            return; // the clean miss: no body, no payment
        }
        EngineWorld useWorld = worldFor(session.dimension());
        BlockType at = useWorld.getBlock(liquidCell);
        if (FluidBlocks.kindOf(at.identifier()) == null) {
            return; // the solid hit: the reference's startUsing only answers liquids
        }
        net.zaminmc.torch.server.entity.MobType kind = net.zaminmc.torch.server.item.SpawnEggs
                .typeOf(session.inventory().held().damage());
        if (kind == null) {
            return; // the unknown id: no body, no payment
        }
        mobManager.spawnFromEgg(kind, new Position(
                liquidCell.x() + 0.5, liquidCell.y() + 0.5, liquidCell.z() + 0.5));
        if (session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /**
     * The item's own use target ({@code Item.getUseTarget} lines 262-279 +
     * {@code World.rayTrace} lines 639-745): the eye's look vector walked
     * 5.0 blocks over the block grid, the first cell that canRayTrace
     * answers — a liquid SOURCE counts only when liquids are allowed
     * ({@code LiquidBlock.canRayTrace} line 88: the flowing cells pass),
     * every other non-air block always counts ({@code AirBlock} line 30:
     * air never does).
     *
     * @return the hittable cell, or null when the ray runs its 5.0 blocks
     *         clean (the reference's miss)
     */
    private BlockPosition rayTraceBlocks(PlayerSession session, boolean allowLiquids) {
        EngineWorld useWorld = worldFor(session.dimension());
        Position eye = new Position(session.position().x(),
                session.position().y() + 1.62, session.position().z());
        double yaw = Math.toRadians(session.rotation().yaw());
        double pitch = Math.toRadians(session.rotation().pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        double reach = 5.0;
        // The Amanatides-Woo walk (World.rayTrace's own boundary ladder
        // under its 200-step cap): each axis steps to its next grid
        // boundary, the smallest parametric t advances the cell.
        int x = (int) Math.floor(eye.x());
        int y = (int) Math.floor(eye.y());
        int z = (int) Math.floor(eye.z());
        double tDeltaX = dx == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dx);
        double tDeltaY = dy == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dy);
        double tDeltaZ = dz == 0 ? Double.MAX_VALUE : Math.abs(1.0 / dz);
        int stepX = dx > 0 ? 1 : -1;
        int stepY = dy > 0 ? 1 : -1;
        int stepZ = dz > 0 ? 1 : -1;
        double tMaxX = dx == 0 ? Double.MAX_VALUE
                : ((dx > 0 ? x + 1.0 - eye.x() : eye.x() - x) / Math.abs(dx));
        double tMaxY = dy == 0 ? Double.MAX_VALUE
                : ((dy > 0 ? y + 1.0 - eye.y() : eye.y() - y) / Math.abs(dy));
        double tMaxZ = dz == 0 ? Double.MAX_VALUE
                : ((dz > 0 ? z + 1.0 - eye.z() : eye.z() - z) / Math.abs(dz));
        for (int step = 0; step < 200; step++) {
            BlockType at = useWorld.getBlock(new BlockPosition(x, y, z));
            if (FluidBlocks.kindOf(at.identifier()) != null) {
                if (allowLiquids && FluidBlocks.isSource(at.identifier())) {
                    return new BlockPosition(x, y, z);
                }
                // the flowing cell passes the ray through (canRayTrace's gate)
            } else if (!at.equals(useWorld.airType())) {
                return new BlockPosition(x, y, z);
            }
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                if (tMaxX > reach) {
                    return null;
                }
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                if (tMaxY > reach) {
                    return null;
                }
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                if (tMaxZ > reach) {
                    return null;
                }
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
        }
        return null;
    }

    /**
     * The farming uses (the historical ItemHoe/ItemSeeds/ItemDye right-click
     * rules): a hoe tills grass or dirt into farmland (the cell above must be
     * open, the tool wears one use); seeds plant a crop on farmland; bone
     * meal jumps a growing crop forward 2-4 stages. Returns whether the use
     * was consumed. Tick-thread context.
     */
    /**
     * The cake's eat arm (Slice 46 — CakeBlock.use lines 57-60 +
     * startMining lines 62-66 + the private eat lines 68-84): a right or
     * left click on the cake eats one slice when the body is not full —
     * canEat(false), the HungerManager.add(2, 0.1F) write, the BITES + 1
     * state advance (or the remove at the seventh bite). Tick-thread
     * context.
     */
    private boolean useCakeOnTick(PlayerSession session, BlockPosition clicked) {
        if (!net.zaminmc.torch.server.block.CakeBlocks.isCake(
                world.getBlock(clicked).identifier())) {
            return false; // not a cake cell: the generic path's own
        }
        eatCakeSlice(session, clicked);
        return true; // the cake's use consumed the click either way
    }

    /**
     * The one-slice eat (CakeBlock.eat lines 68-84): the canEat(false)
     * gate — a full body never bites; the nutrition write
     * {@code add(2, 0.1F)} (HungerManager lines 449-458: foodLevel
     * min(20, +2), saturation min(foodLevel, +0.1)); the BITES advance or
     * the removal at the seventh bite. Tick-thread context.
     */
        /**
     * The dragon egg's use arm (Slice 55 — DragonEggBlock.use): the
     * right-click teleport. Tick-thread context.
     */
    private boolean useDragonEggOnTick(BlockPosition clicked) {
        if (!net.zaminmc.torch.server.block.DragonEggBlocks.isDragonEgg(
                world.getBlock(clicked).identifier())) {
            return false;
        }
        return net.zaminmc.torch.server.block.DragonEggBlocks.tryTeleport(
                dragonEggWorld(), gameplayRandom, clicked);
    }

    /** The dragon egg's punch arm (the startMining teleport). */
    private void teleportDragonEgg(BlockPosition at) {
        if (net.zaminmc.torch.server.block.DragonEggBlocks.isDragonEgg(
                world.getBlock(at).identifier())) {
            net.zaminmc.torch.server.block.DragonEggBlocks.tryTeleport(
                    dragonEggWorld(), gameplayRandom, at);
        }
    }

    /** The egg walk's world adapter. */
    private net.zaminmc.torch.server.block.DragonEggBlocks.EggWorld dragonEggWorld() {
        return new net.zaminmc.torch.server.block.DragonEggBlocks.EggWorld() {
            @Override
            public net.zaminmc.torch.block.BlockType getBlock(BlockPosition at) {
                return world.getBlock(at);
            }

            @Override
            public void setBlock(BlockPosition at, net.zaminmc.torch.block.BlockType type) {
                world.setBlock(at, type);
            }

            @Override
            public net.zaminmc.torch.block.BlockType air() {
                return world.airType();
            }
        };
    }

    private void eatCakeSlice(PlayerSession session, BlockPosition at) {
        BlockType cake = world.getBlock(at);
        if (!net.zaminmc.torch.server.block.CakeBlocks.isCake(cake.identifier())) {
            return; // not a cake cell (a stale start report)
        }
        if (session.food() >= net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD) {
            return; // canEat(false): a full body never bites
        }
        int bites = net.zaminmc.torch.server.block.CakeBlocks.bitesOf(cake.identifier());
        // The bite counts (CakeBlock.eat's own incrementStat).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.CAKE_SLICES_EATEN, 1);
        int newFood = Math.min(net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD,
                session.food() + 2);
        float newSaturation = Math.min(newFood, session.saturation() + 0.1f);
        session.setBody(session.health(), newFood, newSaturation);
        if (bites < net.zaminmc.torch.server.block.CakeBlocks.MAX_BITES) {
            world.setBlock(at, blockRegistry.require(
                    net.zaminmc.torch.server.block.CakeBlocks.stateId(bites + 1)));
        } else {
            world.setBlock(at, world.airType());
        }
    }

    /**
     * The flower-pot's planting arm (Slice 48 — FlowerPotBlock.use lines
     * 66-96): a right click on the EMPTY pot with a pottable item plants
     * it — the TE's setPlant in the engine's flattened form (the potted
     * state write); the item consumes unless the hand is creative.
     * Tick-thread context.
     */
    private boolean useFlowerPotOnTick(PlayerSession session, BlockPosition clicked) {
        BlockType pot = world.getBlock(clicked);
        if (!net.zaminmc.torch.server.block.FlowerPotBlocks.isPot(pot.identifier())
                || !net.zaminmc.torch.server.block.FlowerPotBlocks.isEmptyPot(pot.identifier())) {
            return false; // not an empty pot: the generic path's own
        }
        net.zaminmc.torch.item.ItemStack held = session.inventory().held();
        if (held.isEmpty()
                || !net.zaminmc.torch.server.block.FlowerPotBlocks.isPottable(
                        held.type().identifier(), held.damage())) {
            return false; // nothing pottable: the use falls through
        }
        net.zaminmc.torch.util.Identifier potted = net.zaminmc.torch.server.block
                .FlowerPotBlocks.pottedStateOf(held.type().identifier(), held.damage())
                .orElse(null);
        if (potted == null) {
            return false;
        }
        world.setBlock(clicked, blockRegistry.require(potted));
        if (session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
        }
        // The planting counts (FlowerPotBlock.use's own incrementStat).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.FLOWERS_POTTED, 1);
        publishInventoryChanged(session);
        return true;
    }

    /**
     * The double-plant pair placement (Slice 45 — DoublePlantBlock 1:1 over
     * ItemColored's own BlockItem consult): the family item's damage names
     * the variant; the placement requires the soil (PlantBlock's
     * grass/dirt/farmland band) AND the headroom (DoublePlantBlock.canBePlaced
     * lines 75-78 — {@code super.canBePlaced && world.isAir(pos.up())}), and
     * commits BOTH cells in one placement (onPlaced lines 121-123: the
     * upper written over the lower with flag 2's own band). Tick-thread
     * context; returns whether the use was consumed.
     */
    private boolean useDoublePlantOnTick(PlayerSession session, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemStack heldStack = session.inventory().held();
        if (heldStack.isEmpty() || !heldStack.type().identifier()
                .equals(net.zaminmc.torch.server.item.BuiltinItems.DOUBLE_PLANT.identifier())) {
            return false; // not the family item: the generic path's own
        }
        // The variant from the damage domain (Variant.byId's clamp to the
        // sunflower on out-of-band damage).
        int variant = heldStack.damage() < 0 || heldStack.damage() > 5 ? 0 : heldStack.damage();
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || !world.getBlock(target).equals(world.airType())) {
            return false; // no open cell: the generic path's no-op
        }
        // The two-cell gate: the soil carries AND the headroom is open.
        if (!net.zaminmc.torch.server.block.DoublePlantBlocks.isSupported(world, target)
                || !net.zaminmc.torch.server.block.DoublePlantBlocks.hasHeadroom(world, target)) {
            return false; // the pair refuses: the generic path's no-op
        }
        // The pair commit: the lower, then the upper over it (onPlaced's
        // own write — the upper rides the lower's placement, flag 2).
        world.setBlock(target, blockRegistry.require(
                net.zaminmc.torch.server.block.DoublePlantBlocks.lowerId(variant)));
        world.setBlock(target.offset(0, 1, 0), blockRegistry.require(
                net.zaminmc.torch.server.block.DoublePlantBlocks.upperId(variant)));
        if (session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
        }
        fxManager.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                "dig.grass", 0.6f, 1.0f);
        publishInventoryChanged(session);
        return true;
    }

    /**
     * The double-plant pair break (Slice 45 —
     * DoublePlantBlock.beforeMinedByPlayer lines 124-153): the UPPER's
     * break takes its lower with it — creative silently
     * (removeBlock's own write, lines 151-152), survival through the
     * lower's drop walk (breakBlock(below, true), lines 137-147) with the
     * shears arm's tall-grass item over the grass and fern variants (the
     * onMinedByPlayer walk, lines 155-166). Tick-thread context.
     */
    private void doublePlantBreakReport(PlayerSession session, BlockPosition at,
            BlockType previous, boolean creative) {
        if (!net.zaminmc.torch.server.block.DoublePlantBlocks.isDoublePlant(previous)
                || !net.zaminmc.torch.server.block.DoublePlantBlocks.isUpper(previous.identifier())) {
            return; // not an upper cell: the neighbor bus owns the rest
        }
        net.zaminmc.torch.block.BlockPosition belowCell = at.offset(0, -1, 0);
        BlockType below = world.getBlock(belowCell);
        if (!net.zaminmc.torch.server.block.DoublePlantBlocks.isDoublePlant(below)
                || net.zaminmc.torch.server.block.DoublePlantBlocks.isUpper(below.identifier())) {
            return; // the lower is already gone (a pair in flight): no-op
        }
        world.setBlock(belowCell, world.airType());
        if (creative) {
            return; // the creative remove is silent
        }
        // The survival walk: the shears arm first (onMinedByPlayer lines
        // 155-166 — the tall-grass item with the TYPE damage over the
        // grass and fern variants), the lower's own walk otherwise.
        net.zaminmc.torch.item.ItemStack held = session.inventory().held();
        boolean shears = !held.isEmpty() && held.type().identifier()
                .equals(Identifier.parse("minecraft:shears"));
        int variant = net.zaminmc.torch.server.block.DoublePlantBlocks.variantOf(below.identifier());
        net.zaminmc.torch.item.ItemStack drop;
        if (shears && (variant == 2 || variant == 3)) {
            drop = new net.zaminmc.torch.item.ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.TALL_GRASS, 1,
                    variant == 2 ? 1 : 2);
        } else {
            drop = net.zaminmc.torch.server.block.DoublePlantBlocks.lowerDrop(
                    below, gameplayRandom);
        }
        if (drop != null) {
            itemEntities.spawnDropAtBlock(
                    new Position(belowCell.x(), belowCell.y(), belowCell.z()),
                    drop, net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
    }

    private boolean useFarmingOnTick(PlayerSession session, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemStack heldStack = session.inventory().held();
        if (heldStack.isEmpty()) {
            return false; // a bare hand never tills, plants or dusts
        }
        net.zaminmc.torch.item.ItemType heldType = heldStack.type();
        String held = heldType.identifier().toString();
        if (held.endsWith("_hoe")) {
            BlockType soil = world.getBlock(clicked);
            boolean tillable = soil.identifier().equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                    || soil.identifier().equals(BuiltinBlocks.DIRT.identifier());
            if (!tillable || !world.getBlock(clicked.offset(0, 1, 0)).equals(world.airType())) {
                return false; // nothing to till (or no headroom): the no-op
            }
            world.setBlock(clicked, BuiltinBlocks.FARMLAND);
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "dig.grass", 0.8f, 1.0f);
            if (net.zaminmc.torch.server.item.Tools.specOf(heldType).isPresent()) {
                session.inventory().damageHeld(1, gameplayRandom);
                publishInventoryChanged(session);
            }
            return true;
        }
        if (held.equals("minecraft:wheat_seeds") || held.equals("minecraft:pumpkin_seeds")
                || held.equals("minecraft:melon_seeds")) {
            BlockType soil = world.getBlock(clicked);
            boolean farmland = soil.identifier().equals(BuiltinBlocks.FARMLAND.identifier())
                    || soil.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier());
            if (!farmland || !world.getBlock(clicked.offset(0, 1, 0)).equals(world.airType())) {
                return false; // seeds only take on open farmland (the historical gate)
            }
            // The seeds name their crop (WheatSeedsItem's own block field —
            // Slice 41 extends the walk to the stems).
            BlockType crop = held.equals("minecraft:pumpkin_seeds")
                    ? net.zaminmc.torch.server.block.MelonBlocks.stemStage("pumpkin_stem", 0)
                    : held.equals("minecraft:melon_seeds")
                    ? net.zaminmc.torch.server.block.MelonBlocks.stemStage("melon_stem", 0)
                    : BuiltinBlocks.WHEAT_STAGE0;
            world.setBlock(clicked.offset(0, 1, 0), crop);
            session.inventory().consumeHeld(1);
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 1.5, clicked.z() + 0.5),
                    "dig.grass", 0.6f, 1.1f);
            publishInventoryChanged(session);
            return true;
        }
        // The cocoa planting (Slice 43 — DyeItem.useOn lines 54-71: the
        // BROWN dye — the cocoa beans, damage 12 — on a JUNGLE LOG's
        // horizontal face plants the age-0 pod in the offset cell).
        if (held.equals("minecraft:dye") && heldStack.damage()
                == net.zaminmc.torch.server.block.CocoaBlocks.BROWN_DYE_DAMAGE) {
            BlockType log = world.getBlock(clicked);
            if (log.identifier().equals(
                    net.zaminmc.torch.server.block.CocoaBlocks.JUNGLE_LOG)
                    && face >= 2 && face <= 5) {
                BlockPosition pod = clicked.offset(
                        face == 4 ? -1 : face == 5 ? 1 : 0, 0,
                        face == 2 ? -1 : face == 3 ? 1 : 0);
                if (world.getBlock(pod).equals(world.airType())
                        && distanceSquaredEyeToBlock(session.position(), pod) <= 4.5 * 4.5) {
                    world.setBlock(pod, net.zaminmc.torch.server.block.CocoaBlocks
                            .placementState(face));
                    session.inventory().consumeHeld(1);
                    fxManager.sound(new Position(pod.x() + 0.5, pod.y() + 0.5, pod.z() + 0.5),
                            "dig.grass", 0.6f, 1.0f);
                    publishInventoryChanged(session);
                }
                return true; // the brown dye's use consumed the click either way
            }
            return false; // the other brown-dye arms (the crafting rides elsewhere)
        }
        if (held.equals("minecraft:bone_meal")) {
            // Slice 12b: the whole crop family (the reference's BlockCrops
            // gate — the wheat, the carrots and the potatoes all grow fully).
            int stage = net.zaminmc.torch.server.block.WorldSolidity.cropStageOf(
                    world.getBlock(clicked).identifier());
            if (stage < 0 || stage >= 7) {
                return false; // not a growing crop: the historical no-op
            }
            // WheatBlock.growFully lines 47-53: stage + nextInt(2..5
            // inclusive) — MathHelper.nextInt(random, 2, 5) = 2 + nextInt(4).
            int grown = Math.min(7, stage + 2 + gameplayRandom.nextInt(4));
            world.setBlock(clicked, RandomTickSystem.cropStage(
                    world.getBlock(clicked).identifier(), grown));
            session.inventory().consumeHeld(1);
            fxManager.itemShatter(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    session.inventory().held());
            publishInventoryChanged(session);
            return true;
        }
        return false;
    }

    /**
     * The bucket flow: an empty bucket scoops a fluid source out of the
     * aimed cell; a filled bucket pours its source against the clicked face.
     * Both swap the held stack the historical way (one bucket out, one in).
     * Returns whether the use was consumed. Tick-thread context.
     */
    private boolean useBucketOnTick(PlayerSession session, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemStack heldStack = session.inventory().held();
        if (heldStack.isEmpty()) {
            return false; // a bare hand never scoops or pours
        }
        net.zaminmc.torch.item.ItemType heldType = heldStack.type();
        String held = heldType.identifier().toString();

        if (held.equals("minecraft:bucket")) {
            // Scoop: the aimed cell itself must be a source (the client's
            // fluid ray targets the fluid block directly).
            BlockType at = world.getBlock(clicked);
            FluidBlocks.Kind kind = FluidBlocks.kindOf(at.identifier());
            if (kind == null || !FluidBlocks.isSource(at.identifier())) {
                return false; // not a source: the historical no-op
            }
            ItemType full = kind == FluidBlocks.Kind.WATER
                    ? net.zaminmc.torch.server.item.BuiltinItems.WATER_BUCKET
                    : net.zaminmc.torch.server.item.BuiltinItems.LAVA_BUCKET;
            session.inventory().consumeHeld(1);
            throwOverflow(session, java.util.List.of(
                    session.inventory().pickUp(ItemStack.of(full, 1))));
            world.setBlock(clicked, world.airType()); // the commit wakes the fluid neighbors
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "random.splash", 0.4f, 1.0f);
            return true;
        }

        if (held.equals("minecraft:water_bucket") || held.equals("minecraft:lava_bucket")) {
            // Pour: the source lands against the clicked face; the bucket
            // empties into the hand the historical way.
            BlockPosition target = offsetByFace(clicked, face);
            if (target == null || !world.getBlock(target).equals(world.airType())) {
                return false; // no open cell: the pour stays in the bucket
            }
            FluidBlocks.Kind kind = held.endsWith("water_bucket")
                    ? FluidBlocks.Kind.WATER : FluidBlocks.Kind.LAVA;
            world.setBlock(target, FluidBlocks.sourceOf(kind));
            session.inventory().consumeHeld(1);
            throwOverflow(session, java.util.List.of(session.inventory().pickUp(
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.BUCKET, 1))));
            fxManager.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                    "random.splash", 0.4f, kind == FluidBlocks.Kind.WATER ? 1.0f : 0.6f);
            return true;
        }
        return false;
    }

    /**
     * Breaking one half removes the other (the two-block block dies as a
     * unit; one door item drops from the broken half's behavior table). The
     * break has already committed, so the survivor is whichever door half
     * sits directly above or below the air — the pair must belong to the
     * same family (DoorBlock.neighborChanged's own blockstate.getBlock()
     * != this test). Tick-thread context.
     */
    private void doorSiblingCleanup(BlockPosition broken) {
        BlockType below = world.getBlock(broken.offset(0, -1, 0));
        BlockType above = world.getBlock(broken.offset(0, 1, 0));
        boolean belowDoor = isDoorHalf(below);
        boolean aboveDoor = isDoorHalf(above);
        if (!belowDoor && !aboveDoor) {
            return; // not a door cell: nothing to clean
        }
        if (aboveDoor && WoodDoorFamily.isUpper(above)) {
            world.setBlock(broken.offset(0, 1, 0), world.airType()); // lower broke
        } else if (belowDoor && !WoodDoorFamily.isUpper(below)) {
            world.setBlock(broken.offset(0, -1, 0), world.airType()); // upper broke
        }
    }

    /**
     * The bed-spawn invalidation (the vanilla rule): a sleeper whose anchor
     * cell no longer holds a bed half loses the spawn — the anchor half broke,
     * or the sibling cleanup removed the pair. Tick-thread context.
     */
    private void bedSpawnCleanup(BlockPosition broken) {
        for (PlayerSession player : players.all()) {
            Position anchor = player.bedSpawn();
            if (anchor == null) {
                continue;
            }
            BlockPosition bedCell = new BlockPosition(
                    (int) Math.floor(anchor.x()), (int) Math.floor(anchor.y()) - 1,
                    (int) Math.floor(anchor.z()));
            if (!isBedHalf(world.getBlock(bedCell))) {
                player.setBedSpawn(null); // the bed is gone: spawn resets
            }
        }
    }

    /**
     * The bed pair break (the vanilla unit rule): breaking one half removes
     * the other — a foot takes its head, a head takes its foot. The sibling
     * is the horizontal neighbor of the opposite half class (two adjacent
     * beds never share a class across the pair line). Tick-thread context.
     */
    private void bedSiblingCleanup(BlockPosition broken, BlockType brokenType) {
        if (!isBedHalf(brokenType)) {
            return;
        }
        boolean brokeHead = brokenType.identifier().value().startsWith("bed_head");
        for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockPosition neighbor = broken.offset(direction[0], 0, direction[1]);
            BlockType at = world.getBlock(neighbor);
            if (!isBedHalf(at)) {
                continue;
            }
            boolean neighborIsHead = at.identifier().value().startsWith("bed_head");
            if (neighborIsHead != brokeHead) {
                world.setBlock(neighbor, world.airType()); // the pair dies as a unit
                return;
            }
        }
    }

    /**
     * The door swing (DoorBlock.use lines 124-141): the lower's open bit
     * flips, the upper mirrors the swing with its hinge and POWERED bits
     * preserved (use() never writes POWERED — a signal-held door manually
     * closed stays powered until the signal departs), and the historical
     * open/close sound plays at the hinge. The iron door never reaches
     * here (the use gate's material refusal). Tick-thread context.
     */
    private void toggleDoorOnTick(BlockPosition lower) {
        BlockType type = world.getBlock(lower);
        String family = WoodDoorFamily.familyOf(type);
        if (family == null || WoodDoorFamily.isIron(family)) {
            return; // not a swinging door half
        }
        BlockPosition upper = lower.offset(0, 1, 0);
        BlockType upperType = world.getBlock(upper);
        if (!WoodDoorFamily.isDoor(upperType)
                || !family.equals(WoodDoorFamily.familyOf(upperType))) {
            return; // a half door: refuse the swing rather than desync it
        }
        boolean opening = !WoodDoorFamily.isOpen(type);
        boolean rightHinge = WoodDoorFamily.isRightHinge(upperType);
        boolean powered = WoodDoorFamily.isPowered(upperType);
        world.setBlock(lower, WoodDoorFamily.flipLower(type));
        world.setBlock(upper, WoodDoorFamily.upper(family, rightHinge, opening, powered));
        fxManager.sound(new Position(lower.x() + 0.5, lower.y() + 0.5, lower.z() + 0.5),
                opening ? "random.door_open" : "random.door_close", 0.8f, 1.0f);
    }

    /** Whether the block type is any door half (either family, either half). */
    public static boolean isDoorHalf(net.zaminmc.torch.block.BlockType type) {
        return WoodDoorFamily.isDoor(type);
    }

    /**
     * The gate's use() swing (FenceGateBlock.use lines 94-110, 1:1): the
     * open arm first flips the facing when the player looks from the far
     * side ({@code state.get(FACING) == direction.getOpposite()} — the
     * walk-through direction follows the opener), then OPEN flips with
     * facing and POWERED kept; the close arm drops OPEN only. The doEvent
     * 1003/1006 pair rides the fx sound bus (the door's own precedent —
     * the client's event handler plays the same open/close sounds).
     * Tick-thread context.
     */
    private void toggleFenceGateOnTick(PlayerSession session, BlockPosition pos,
                                       BlockType gate) {
        int facing = net.zaminmc.torch.server.block.FenceGateBlocks.facingOf(gate);
        boolean open = net.zaminmc.torch.server.block.FenceGateBlocks.isOpen(gate);
        int lookBand;
        if (open) {
            lookBand = facing; // the close arm never rewrites the facing
        } else {
            // Direction.byRotation(player.yaw): the player's own horizontal
            // facing band (0=S, 1=W, 2=N, 3=E — the engine's own band).
            double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
            int playerFacing = (int) (yaw / 90.0) % 4;
            // The opposite-of-facing rewrite: opening from the far side
            // turns the gate to face the opener.
            lookBand = facing == oppositeBand(playerFacing) ? playerFacing : facing;
        }
        boolean opening = !open;
        world.setBlock(pos, net.zaminmc.torch.server.block.FenceGateBlocks.with(
                gate, lookBand, opening));
        fxManager.sound(new Position(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                opening ? "random.door_open" : "random.door_close", 0.8f, 1.0f);
    }

    /** The horizontal band's opposite (0<->2 south/north, 1<->3 west/east). */
    private static int oppositeBand(int band) {
        return (band + 2) & 3;
    }

    /**
     * The trapdoor's placement (TrapdoorBlock.getPlacementState +
     * canBePlaced lines 145-158, 1:1): only a HORIZONTAL clicked face
     * mounts (the trapdoor hangs on the wall the use names), the facing is
     * the clicked face's own direction (the panel sits against the wall),
     * the half rides the cursor's upper band ({@code dy > 0.5F}), and the
     * wall block must be a valid mount (canBePlacedOn: a solid cube, the
     * glowstone, a slab, or a stairs). Tick-thread context.
     */
    private void placeTrapdoorOnTick(PlayerSession session, BlockPosition clicked, int face,
                                      java.util.Optional<BlockType> creativeHeld, float cursorY) {
        if (face < 2 || face > 5) {
            return; // only a horizontal face mounts (the verticals refuse)
        }
        String held = heldIdentifierOf(session, creativeHeld);
        String family = held != null && held.contains(":")
                ? held.substring(held.indexOf(':') + 1) : held;
        if (family == null
                || !net.zaminmc.torch.server.block.TrapdoorBlocks.isTrapdoorItem(family)) {
            return;
        }
        // The facing band (the reference's getFacing: the face direction
        // over the 0-3 order N, S, W, E).
        int facing = switch (face) {
            case 2 -> 0; // north
            case 3 -> 1; // south
            case 4 -> 2; // west
            default -> 3; // east
        };
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // canBePlacedOn (line 188-190): the wall block — a solid cube, the
        // glowstone, a slab, or a stairs family member.
        if (!isTrapdoorMount(world.getBlock(clicked))) {
            return;
        }
        boolean top = cursorY >= 0.5f; // dy > 0.5F (the cursor fraction, Slice 65's unified form)
        world.setBlock(target, net.zaminmc.torch.server.block.TrapdoorBlocks
                .state(family, facing, false, top));
        consumePlaced(session, creativeHeld);
    }

    /** canBePlacedOn's own set (TrapdoorBlock lines 188-190): a solid
     *  full cube (the engine's isSolid minus the known partial shapes —
     *  the fences, the gates, the doors, the rails, the chest's 15/16
     *  box), the glowstone, the slabs, and the stairs. */
    private boolean isTrapdoorMount(BlockType block) {
        if (block.identifier().equals(BuiltinBlocks.GLOWSTONE.identifier())) {
            return true;
        }
        String value = block.identifier().value();
        if (isSlabIdentifier(value) || isStairsIdentifier(value)) {
            return true; // the slab and stairs families (instanceof rows)
        }
        return WorldSolidity.isSolid(block)
                && !value.startsWith("minecraft:fence")
                && !net.zaminmc.torch.server.block.FenceGateBlocks.isGateItem(value)
                && !value.contains("_door") && !value.contains("trapdoor")
                && !WorldSolidity.isRail(block.identifier())
                && !net.zaminmc.torch.server.block.RailBlocks.isRailItem(value)
                && !value.equals("chest") && !value.startsWith("chest_");
    }

    /**
     * The gate's placement (FenceGateBlock.getPlacementState +
     * canBePlaced lines 47-49, 89-91): the facing is the placer's own
     * horizontal facing (getHorizontalFacing — the walk-through axis
     * follows the placer's look), OPEN and POWERED start clear, and the
     * floor must be solid ({@code getMaterial().isSolid()}). Tick-thread.
     */
    /**
     * The anvil's placement (Slice 53 — AnvilBlock.getPlacementState 1:1):
     * the facing is the placer's own horizontal facing turned clockwise
     * (entity.getHorizontalFacing().clockwiseY()), the DAMAGE the held
     * stack's damage field (the reference's metadata >> 2 read over the
     * AnvilItem's damage domain). Tick-thread context.
     */
    private void placeAnvilOnTick(PlayerSession session, BlockPosition clicked, int face,
                                  java.util.Optional<BlockType> creativeHeld) {
        String held = heldIdentifierOf(session, creativeHeld);
        if (held == null || !held.equals("minecraft:anvil")) {
            return;
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // The look band (0=S,1=W,2=N,3=E) turned clockwise: the anvil's
        // FACING band is the placer's own horizontal facing clockwiseY.
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4;
        int facing = (lookBand + 1) % 4; // clockwiseY: S->W, W->N, N->E, E->S
        // The DAMAGE: the held stack's damage field (the AnvilItem's domain,
        // the creative menu's own three stacks).
        int damage = 0;
        if (creativeHeld.isEmpty() && !session.inventory().held().isEmpty()) {
            damage = net.zaminmc.torch.server.block.AnvilBlocks
                    .placementDamageOf(session.inventory().held().damage());
        }
        world.setBlock(target, blockRegistry.require(
                net.zaminmc.torch.server.block.AnvilBlocks.stateId(facing, damage)));
        consumePlaced(session, creativeHeld);
    }

    private void placeFenceGateOnTick(PlayerSession session, BlockPosition clicked, int face,
                                      java.util.Optional<BlockType> creativeHeld) {
        String held = heldIdentifierOf(session, creativeHeld);
        String family = held == null ? null
                : (held.contains(":")
                        ? held.substring(held.indexOf(':') + 1) : held);
        if (family == null || !net.zaminmc.torch.server.block.FenceGateBlocks.isGateItem(family)) {
            return;
        }
        String wood = family.substring(0, family.length() - "_fence_gate".length());
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // canBePlaced: the solid floor (the gate never floats onto flora).
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return;
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E
        world.setBlock(target, net.zaminmc.torch.server.block.FenceGateBlocks
                .placementState(wood, lookBand));
        consumePlaced(session, creativeHeld);
    }

    /**
     * The ladder placement (the historical ItemLadder): the ladder hangs on
     * the clicked wall — its facing is the OPPOSITE of the clicked face's
     * direction (a north-face click mounts it on the south wall of the
     * neighbor cell), metadata N=2, S=3, W=4, E=5. Tick-thread context.
     */
    private void placeLadderOnTick(PlayerSession session, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // Face 2=-Z (north wall of the neighbor): ladder faces south. The
        // vanilla mapping: N=2, S=3, W=4, E=5 (the side the ladder hugs).
        BlockType facing = switch (face) {
            case 2 -> BuiltinBlocks.LADDER_SOUTH;
            case 3 -> BuiltinBlocks.LADDER_NORTH;
            case 4 -> BuiltinBlocks.LADDER_EAST;
            case 5 -> BuiltinBlocks.LADDER_WEST;
            default -> null; // up/down clicks never mount a ladder
        };
        if (facing == null) {
            return;
        }
        world.setBlock(target, facing);
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /** Whether the block type is a bed half (either foot or head). */
    public static boolean isBedHalf(net.zaminmc.torch.block.BlockType type) {
        String id = type.identifier().value();
        return type.identifier().namespace().equals("minecraft")
                && (id.equals("bed") || id.startsWith("bed_"));
    }

    /**
     * The bed placement (the historical ItemBed): the foot at the target and
     * the head one cell along the placer's look (the head points where the
     * player faces), both cells open, one item spent in survival.
     * Tick-thread context.
     */
    private void placeBedOnTick(PlayerSession session, BlockPosition clicked, int face,
                                java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        // The head cell: one along the look's cardinal (the sign band order
        // S/W/N/E with offsets +Z/-X/-Z/+X for the look yaw bands).
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        int[][] headOffsets = {{0, 0, 1}, {-1, 0, 0}, {0, 0, -1}, {1, 0, 0}};
        int[] headOffset = headOffsets[lookBand];
        BlockPosition head = target.offset(headOffset[0], headOffset[1], headOffset[2]);
        if (!world.getBlock(head).equals(world.airType())) {
            return; // no room for the head half
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        BlockType[] foots = {BuiltinBlocks.BED_FOOT_SOUTH, BuiltinBlocks.BED_FOOT_WEST,
                BuiltinBlocks.BED_FOOT_NORTH, BuiltinBlocks.BED_FOOT_EAST};
        world.setBlock(target, foots[lookBand]);
        BlockType[] heads = {BuiltinBlocks.BED_HEAD_SOUTH, BuiltinBlocks.BED_HEAD_WEST,
                BuiltinBlocks.BED_HEAD_NORTH, BuiltinBlocks.BED_HEAD_EAST};
        world.setBlock(head, heads[lookBand]);
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /**
     * The sleep flow (the historical EntityPlayer.sleepInBedAt): outside the
     * night window the bed refuses with the vanilla line; inside it, the
     * body's bed spawn is set, the world jumps to morning, and the weather
     * clears — the single-player sleep contract this server makes.
     * Tick-thread context.
     */
    private void sleepOnTick(PlayerSession session, BlockPosition clicked) {
        if (session.state() != PlayerState.PLAYING || session.dead()) {
            return;
        }
        long time = world.timeOfDay();
        boolean night = time >= MobManager.NIGHT_START && time < MobManager.NIGHT_END;
        if (!night) {
            systemMessage(session, "You can only sleep at night");
            return;
        }
        session.setBedSpawn(new Position(clicked.x() + 0.5, clicked.y() + 1.0,
                clicked.z() + 0.5));
        systemMessage(session, "Your spawn point has been set");
        // The morning jump (the historical wake-up at 0) with the cycle sync.
        world.setTimeOfDay(0);
        publishTimeChanged();
        // Sleeping clears the weather (ServerWorld.wakeSleepingPlayers →
        // clearWeather lines 248-256: both flags drop, both clocks zero —
        // the auto-cycle rerolls fresh).
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        if (cycle != null) {
            cycle.clearWeather();
        }
        LOGGER.info(() -> session.name() + " slept through the night");
    }

    /**
     * The door placement (DoorItem.useOn lines 20-62, 1:1): the UP face
     * only, a replaceable clicked cell takes the door in place, the cell
     * and its headroom must both be free under the height cap with a solid
     * top below, the facing is the player's own yaw band
     * (Direction.byRotation — the metadata the client decodes), and the
     * hinge side comes from the neighbor solids (DoorItem.place's own
     * clockwise/counterclockwise count with the same-family adjacency
     * preference). One item spends in survival. Tick-thread context.
     */
    private void placeDoorOnTick(PlayerSession session, BlockPosition clicked, int face,
                                 java.util.Optional<BlockType> creativeHeld) {
        if (face != 1) {
            return; // DoorItem.useOn: only the UP face places
        }
        // A replaceable clicked cell (the tall-grass rule) takes the door
        // itself; anything else offsets up.
        BlockPosition target = WorldSolidity.isReplaceable(world.getBlock(clicked).identifier())
                ? clicked : clicked.offset(0, 1, 0);
        if (target.y() >= 255) {
            return; // canBePlaced's own cap (pos.getY() < 255)
        }
        if (!world.getBlock(target).equals(world.airType())
                && !WorldSolidity.isReplaceable(world.getBlock(target).identifier())) {
            return;
        }
        BlockType above = world.getBlock(target.offset(0, 1, 0));
        if (!above.equals(world.airType())
                && !WorldSolidity.isReplaceable(above.identifier())) {
            return; // the upper half's headroom
        }
        if (!WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
            return; // World.hasSolidTop: no floor, no door
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        String family = heldDoorFamily(session, creativeHeld);
        if (family == null) {
            return;
        }
        // Facing: the player's own look band (Direction.byRotation), the
        // metadata the client decodes — the historical clockwise horizontal
        // id (S/W/N/E = 0/1/2/3 of the facing turned one step clockwise,
        // which the shipped band maps to the four closed states exactly).
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E of the look
        int[] facingOffset = lookFacingOffset(lookBand);
        // The hinge rule (DoorItem.place lines 44-54): the solid count on
        // the clockwise side versus the counter-clockwise, with a
        // same-family neighbor (or its headroom) preferring its side.
        int[] clockwise = clockwiseOf(facingOffset);
        int[] counter = counterClockwiseOf(facingOffset);
        BlockPosition cw = target.offset(clockwise[0], 0, clockwise[2]);
        BlockPosition ccw = target.offset(counter[0], 0, counter[2]);
        int i = solidDoorNeighbor(ccw) + solidDoorNeighbor(ccw.offset(0, 1, 0));
        int j = solidDoorNeighbor(cw) + solidDoorNeighbor(cw.offset(0, 1, 0));
        boolean flag = sameFamilyDoorAt(family, ccw) || sameFamilyDoorAt(family, ccw.offset(0, 1, 0));
        boolean flag1 = sameFamilyDoorAt(family, cw) || sameFamilyDoorAt(family, cw.offset(0, 1, 0));
        boolean rightHinge = flag && !flag1 || j > i;
        // DoorItem.place's flag-2 pair commit: both halves stand before any
        // neighbor hears anything (the reference notifies the neighbors
        // explicitly afterwards — a half-alone commit is never observable).
        redstoneSystem.runDoorPairPlacement(() -> {
            world.setBlock(target,
                    WoodDoorFamily.lower(family, lowerMetaBand(lookBand), false));
            world.setBlock(target.offset(0, 1, 0),
                    WoodDoorFamily.upper(family, rightHinge, false, false));
        });
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /** The door family of the held item ("oak".."dark_oak", "iron"). */
    private static String heldDoorFamily(PlayerSession session,
                                         java.util.Optional<BlockType> creativeHeld) {
        String value = creativeHeld
                .map(t -> t.identifier().value())
                .orElseGet(() -> session.inventory().held().isEmpty()
                        ? "" : session.inventory().held().type().identifier().value());
        for (String family : WoodDoorFamily.FAMILIES) {
            if (value.equals(family + "_door")) {
                return family;
            }
        }
        return null;
    }

    /** The look band's facing offset (S=+Z, W=-X, N=-Z, E=+X). */
    private static int[] lookFacingOffset(int lookBand) {
        return switch (lookBand) {
            case 0 -> new int[] {0, 0, 1};   // south
            case 1 -> new int[] {-1, 0, 0};  // west
            case 2 -> new int[] {0, 0, -1};  // north
            default -> new int[] {1, 0, 0};  // east
        };
    }

    /** clockwiseY of a horizontal facing (N→E→S→W viewed from above). */
    private static int[] clockwiseOf(int[] facing) {
        return new int[] {-facing[2], 0, facing[0]};
    }

    /** counterClockwiseY of a horizontal facing. */
    private static int[] counterClockwiseOf(int[] facing) {
        return new int[] {facing[2], 0, -facing[0]};
    }

    /** Whether the cell holds a solid block (DoorItem.place's own isSolid). */
    private int solidDoorNeighbor(BlockPosition at) {
        return WorldSolidity.isSolid(world.getBlock(at)) ? 1 : 0;
    }

    /** Whether the cell holds a door half of the given family. */
    private boolean sameFamilyDoorAt(String family, BlockPosition at) {
        BlockType type = world.getBlock(at);
        return WoodDoorFamily.isDoor(type) && family.equals(WoodDoorFamily.familyOf(type));
    }

    /**
     * The lower's metadata band for a look direction (DoorItem.place →
     * getMetadataFromState: the facing turned clockwise into its horizontal
     * id — look S→1, W→2, N→3, E→0 — the exact bytes the shipped oak band
     * carried and the 1.8.8 client decodes).
     */
    private static int lowerMetaBand(int lookBand) {
        return switch (lookBand) {
            case 0 -> 1; // looking south
            case 1 -> 2; // looking west
            case 2 -> 3; // looking north
            default -> 0; // looking east
        };
    }

    /** @return whether the identifier value belongs to the slab family. */
    private static boolean isSlabIdentifier(String value) {
        return value.endsWith("_slab") || value.endsWith("_slab_top")
                || value.equals("stone_slab2") || value.equals("stone_slab2_top");
    }

    /** @return whether the identifier value belongs to the stairs family. */
    private static boolean isStairsIdentifier(String value) {
        return WoodShapeBlocks.isStairsItem(value) || WoodShapeBlocks.isStairsState(value)
                || value.startsWith("oak_stairs") || value.startsWith("cobblestone_stairs")
                || value.startsWith("sandstone_stairs") || value.startsWith("nether_brick_stairs")
                || value.startsWith("red_sandstone_stairs");
    }

    /**
     * The slab family of a slab identifier (item or per-state block):
     * {bottom half, top half, the double-slab block} — the community
     * doubles are the parent materials (oak double slab = planks, the
     * stone-family doubles = their cubes).
     */
    private net.zaminmc.torch.block.BlockType[] slabFamilyOf(String value) {
        if (value.startsWith("oak_slab")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.OAK_SLAB, BuiltinBlocks.OAK_SLAB_TOP,
                    BuiltinBlocks.DOUBLE_WOODEN_SLAB};
        }
        if (value.startsWith("stone_slab")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.STONE_SLAB, BuiltinBlocks.STONE_SLAB_TOP, BuiltinBlocks.STONE};
        }
        if (value.startsWith("cobblestone_slab")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.COBBLESTONE_SLAB, BuiltinBlocks.COBBLESTONE_SLAB_TOP,
                    BuiltinBlocks.COBBLESTONE};
        }
        if (value.startsWith("sandstone_slab")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.SANDSTONE_SLAB, BuiltinBlocks.SANDSTONE_SLAB_TOP,
                    BuiltinBlocks.SANDSTONE};
        }
        // The red sandstone slab pair (Slice 52 — the reference's own
        // stone_slab2 family: the doubles carry the SEAMLESS bit's plain
        // form, the family's own 181 double).
        if (value.equals("stone_slab2") || value.equals("stone_slab2_top")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.STONE_SLAB2, BuiltinBlocks.STONE_SLAB2_TOP,
                    BuiltinBlocks.DOUBLE_STONE_SLAB2};
        }
        // The wooden-slab families (Slice 36 — the reference's own id 126
        // carrying the variant nibble): each wood's pair doubles into its
        // OWN 125 double (Slice 58 — the proper DoubleWoodenSlabBlock
        // form, no longer the planks stand-in).
        for (int variant = 1; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            String wood = WoodVariantBlocks.WOODS.get(variant);
            if (value.startsWith(wood + "_slab")) {
                return new net.zaminmc.torch.block.BlockType[]{
                        WoodShapeBlocks.slabStates(variant).get(0),
                        WoodShapeBlocks.slabStates(variant).get(1),
                        WoodShapeBlocks.doubleSlabState(variant)};
            }
        }
        return null;
    }

    /**
     * The stairs family of a stairs identifier: the four ascending states
     * in the look-band order {south, west, north, east} (the bed band).
     */
    private net.zaminmc.torch.block.BlockType[] stairsFamilyOf(String value) {
        if (value.startsWith("oak_stairs")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.OAK_STAIRS_SOUTH, BuiltinBlocks.OAK_STAIRS_WEST,
                    BuiltinBlocks.OAK_STAIRS_NORTH, BuiltinBlocks.OAK_STAIRS_EAST};
        }
        if (value.startsWith("cobblestone_stairs")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.COBBLESTONE_STAIRS_SOUTH, BuiltinBlocks.COBBLESTONE_STAIRS_WEST,
                    BuiltinBlocks.COBBLESTONE_STAIRS_NORTH, BuiltinBlocks.COBBLESTONE_STAIRS_EAST};
        }
        // The red sandstone stairs (Slice 52 — the family's own four
        // states over the Bukkit band).
        if (value.startsWith("red_sandstone_stairs")) {
            return new net.zaminmc.torch.block.BlockType[]{
                    BuiltinBlocks.RED_SANDSTONE_STAIRS_SOUTH, BuiltinBlocks.RED_SANDSTONE_STAIRS_WEST,
                    BuiltinBlocks.RED_SANDSTONE_STAIRS_NORTH, BuiltinBlocks.RED_SANDSTONE_STAIRS_EAST};
        }
        // The new stair families (Slice 36 — the eight over the foundation
        // and the masonry set): the four facings resolve through the
        // registry (the states' own registration).
        if (WoodShapeBlocks.isStairsItem(value) || WoodShapeBlocks.isStairsState(value)) {
            String bare = value.contains(":")
                    ? value.substring(value.indexOf(':') + 1) : value;
            String family = bare.substring(0, bare.indexOf("_stairs"));
            net.zaminmc.torch.block.BlockType[] states = new net.zaminmc.torch.block.BlockType[4];
            String[] facings = {"south", "west", "north", "east"};
            for (int i = 0; i < 4; i++) {
                states[i] = blockRegistry.require(Identifier.parse(
                        "minecraft:" + family + "_stairs_" + facings[i]));
            }
            return states;
        }
        return null;
    }

    /**
     * The slab placement (the historical ItemStep): the half rides the
     * clicked face — DOWN hangs the top half, UP lays the bottom, the
     * sides split on the cursor byte (the hit offset within the face,
     * 16ths — the upper half of the face means the top slab). A use
     * against the same family's slab doubles it into the parent block.
     * Tick-thread context.
     */
    private void placeSlabOnTick(PlayerSession session, BlockPosition clicked, int face,
                                 java.util.Optional<BlockType> creativeHeld, float cursorY) {
        String held = heldIdentifierOf(session, creativeHeld);
        net.zaminmc.torch.block.BlockType[] family = held == null ? null : slabFamilyOf(held);
        if (family == null) {
            return;
        }
        // The vanilla double (the historical ItemBlock.onItemUse): the use
        // lands on the family's own half — the full block fills the clicked
        // cell whatever face the cursor says.
        String stem = family[0].identifier().toString().substring("minecraft:".length());
        BlockType clickedBlock = world.getBlock(clicked);
        if (isSlabIdentifier(clickedBlock.identifier().value())
                && clickedBlock.identifier().value().startsWith(stem)) {
            if (distanceSquaredEyeToBlock(session.position(), clicked) > 4.5 * 4.5) {
                return;
            }
            world.setBlock(clicked, family[2]);
            consumePlaced(session, creativeHeld);
            return;
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())) {
            return;
        }
        BlockType existing = world.getBlock(target);
        if (!existing.equals(world.airType())
                || distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        // Glowstone's BlockStep convention: DOWN face hangs the top half,
        // UP lays the bottom, the sides split on the cursor's upper half.
        boolean top = face == 0 || (face != 1 && cursorY >= 0.5f);
        world.setBlock(target, top ? family[1] : family[0]);
        consumePlaced(session, creativeHeld);
    }

    /**
     * The stairs placement (the historical ItemStairs): the ascending
     * direction follows the placer's cardinal look — the walk-up feel —
     * with the Bukkit band (E=0 W=1 S=2 N=3). Tick-thread context.
     */
    private void placeStairsOnTick(PlayerSession session, BlockPosition clicked, int face,
                                   java.util.Optional<BlockType> creativeHeld) {
        String held = heldIdentifierOf(session, creativeHeld);
        net.zaminmc.torch.block.BlockType[] family = held == null ? null : stairsFamilyOf(held);
        if (family == null) {
            return;
        }
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        world.setBlock(target, family[(int) (yaw / 90.0) % 4]);
        consumePlaced(session, creativeHeld);
    }

    /**
     * The rail placement (the historical ItemRail): the flat orientation
     * follows the placer's look (north-south or east-west), the cell must
     * be open with a solid floor beneath (the vanilla rail gate —
     * AbstractRailBlock.canBePlaced's World.hasSolidTop). The Slice 38
     * trio families place their own state through the same walk.
     * Tick-thread.
     */
    private void placeRailOnTick(PlayerSession session, BlockPosition clicked, int face,
                                 java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null || world.getBlock(clicked).equals(world.airType())
                || !world.getBlock(target).equals(world.airType())) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5
                || intersectsPlayerBox(session.position(), target)) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        BlockType floor = world.getBlock(target.offset(0, -1, 0));
        if (!WorldSolidity.isSolid(floor)) {
            return; // the rail needs its solid bed (the vanilla gate)
        }
        double yaw = ((session.rotation().yaw() % 360.0) + 360.0 + 45.0) % 360.0;
        int lookBand = (int) (yaw / 90.0) % 4; // 0=S,1=W,2=N,3=E
        // The look along an axis lays the track along it: S/N -> north-south.
        boolean northSouth = lookBand == 0 || lookBand == 2;
        String held = heldIdentifierOf(session, creativeHeld);
        String railFamily = net.zaminmc.torch.server.block.RailBlocks.isRailItem(held)
                ? (held.contains(":") ? held.substring(held.indexOf(':') + 1) : held) : null;
        if (railFamily != null) {
            world.setBlock(target, net.zaminmc.torch.server.block.RailBlocks
                    .placementState(railFamily, !northSouth));
        } else {
            world.setBlock(target, northSouth ? BuiltinBlocks.RAIL : BuiltinBlocks.RAIL_EW);
        }
        consumePlaced(session, creativeHeld);
    }

    /**
     * The vehicle spawn use (the historical ItemBoat/ItemMinecart): a boat
     * spawns at the water the use names (or on the open ground — the 1.8
     * boats paddle ashore); a minecart only spawns on a rail. Tick-thread.
     */
    private void spawnVehicleUseOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld, boolean boat,
                                       boolean chestCart) {
        spawnVehicleUseOnTick(session, clicked, face, creativeHeld, boat, chestCart, false, false);
    }

    private void spawnVehicleUseOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld, boolean boat,
                                       boolean chestCart, boolean furnaceCart, boolean tntCart) {
        spawnVehicleUseOnTick(session, clicked, face, creativeHeld, boat, chestCart,
                furnaceCart, tntCart, false);
    }

    private void spawnVehicleUseOnTick(PlayerSession session, BlockPosition clicked, int face,
                                       java.util.Optional<BlockType> creativeHeld, boolean boat,
                                       boolean chestCart, boolean furnaceCart, boolean tntCart,
                                       boolean hopperCart) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (vehicleManager == null) {
            return;
        }
        Position spawnAt;
        if (boat) {
            // The 1.8 boat raytrace stops AT fluids: the use usually names
            // the water cell itself (face 1 on its surface), so the clicked
            // cell wins, then the face-offset cell, then the shore rule.
            BlockType atClicked = world.getBlock(clicked);
            BlockType atTarget = world.getBlock(target);
            if (FluidBlocks.kindOf(atClicked.identifier()) != null) {
                spawnAt = new Position(clicked.x() + 0.5, clicked.y() + 0.4, clicked.z() + 0.5);
            } else if (FluidBlocks.kindOf(atTarget.identifier()) != null) {
                spawnAt = new Position(target.x() + 0.5, target.y() + 0.4, target.z() + 0.5);
            } else if (atTarget.equals(world.airType())
                    && WorldSolidity.isSolid(world.getBlock(target.offset(0, -1, 0)))) {
                spawnAt = new Position(target.x() + 0.5, target.y(), target.z() + 0.5);
            } else {
                return; // no water, no open ground: the vanilla refusal
            }
        } else {
            // The minecart's rail: the clicked cell first (the client aims
            // at the rail's top face, so the offset cell sits above it).
            BlockPosition railCell = WorldSolidity.isRail(world.getBlock(target).identifier())
                    ? target
                    : WorldSolidity.isRail(world.getBlock(clicked).identifier()) ? clicked : null;
            if (railCell == null) {
                return; // minecarts only spawn on rails (the vanilla gate)
            }
            spawnAt = new Position(railCell.x() + 0.5, railCell.y() + 0.06, railCell.z() + 0.5);
        }
        if (boat) {
            if (vehicleManager.spawnBoat(spawnAt) == null) {
                return; // the §27.7 gate withheld the boat — the item stays
            }
        } else if (chestCart) {
            if (vehicleManager.spawnChestMinecart(spawnAt) == null) {
                return; // the §27.7 gate withheld the cart — the item stays
            }
        } else if (furnaceCart) {
            if (vehicleManager.spawnFurnaceMinecart(spawnAt) == null) {
                return; // the §27.7 gate withheld the cart — the item stays
            }
        } else if (tntCart) {
            var tntCartEntity = vehicleManager.spawnTntMinecart(spawnAt);
            if (tntCartEntity == null) {
                return; // the §27.7 gate withheld the cart — the item stays
            }
            // The detonation sink (the engine's blast walk with fire —
            // the reference's world.explode(this, x, y, z, power, true)):
            // the 8*power+1 center wound the fireball family rides, the
            // destructive flag under mobGriefing.
            if (tntCartEntity instanceof net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity tnt) {
                tnt.setBlastSink((x, y, z, power) -> detonateAt(
                        x, y, z, power, 8.0f * power + 1.0f,
                        gameRules.getBoolean("mobGriefing")));
            }
        } else if (hopperCart) {
            // The transfer host (Slice 66): the engine's hopper system —
            // the reference's HopperBlockEntity statics over the cart's
            // own mouth.
            if (hopperSystem == null
                    || vehicleManager.spawnHopperMinecart(
                            spawnAt, hopperSystem::hopperCartTransfer) == null) {
                return; // the §27.7 gate withheld the cart — the item stays
            }
        } else {
            if (vehicleManager.spawnMinecart(spawnAt) == null) {
                return; // the §27.7 gate withheld the cart — the item stays
            }
        }
        consumePlaced(session, creativeHeld);
    }

    /**
     * The command cart's run surface (Slice 66 — CommandExecutor.run
     * lines 79-100 over the engine's CommandService): the dispatch with
     * the executor's own sender — op level 2 (canUseCommand line 105),
     * the name "@", the feedback landing in the cart's last output. The
     * reference's areCommandBlocksEnabled gate rides the config surface
     * the engine does not carry yet (ledgered with the config slice).
     */
    private net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity.CommandHost
            commandCartHost() {
        return this::runCommandCart;
    }

    /**
     * One command-cart execution (public — the engine's own tests and
     * the future summon paths): the CommandService dispatch with the
     * executor's own sender (op level 2, the name "@", the feedback
     * landing in the cart's last output).
     */
    public int runCommandCart(
            net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity cart,
            String command) {
        if (command == null || command.isBlank()) {
            return 0;
        }
        String line = command.startsWith("/") ? command : "/" + command;
        String name = line.substring(1).split("\\s+")[0];
        // The engine's own command table (the deferred field read — the
        // host runs post-boot).
        var service = commands();
        boolean known = service.lookup(name) != null;
        service.dispatch(new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() {
                return "@"; // CommandExecutor's own default
            }

            @Override public void sendMessage(String message) {
                cart.recordOutput(message);
            }

            @Override public int opLevel() {
                return 2; // canUseCommand: permissionLevel <= 2
            }

            @Override public PlayerSession player() {
                return null;
            }
        }, line);
        return known ? 1 : 0;
    }

    /**
     * The command-block minecart's place arm (Slice 66 — MinecartItem
     * over the command variant): the same rail gate as the family, the
     * command host wired at spawn.
     */
    private void spawnCommandBlockCartOnTick(PlayerSession session, BlockPosition clicked,
                                             int face,
                                             java.util.Optional<BlockType> creativeHeld) {
        BlockPosition target = offsetByFace(clicked, face);
        if (target == null) {
            return;
        }
        if (distanceSquaredEyeToBlock(session.position(), target) > 4.5 * 4.5) {
            return;
        }
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE
                && session.inventory().held().isEmpty()) {
            return;
        }
        if (vehicleManager == null) {
            return;
        }
        BlockPosition railCell = WorldSolidity.isRail(world.getBlock(target).identifier())
                ? target
                : WorldSolidity.isRail(world.getBlock(clicked).identifier()) ? clicked : null;
        if (railCell == null) {
            return; // minecarts only spawn on rails (the vanilla gate)
        }
        Position spawnAt = new Position(railCell.x() + 0.5, railCell.y() + 0.06, railCell.z() + 0.5);
        if (vehicleManager.spawnCommandBlockMinecart(spawnAt, commandCartHost()) == null) {
            return; // the §27.7 gate withheld the cart — the item stays
        }
        consumePlaced(session, creativeHeld);
    }

    /**
     * The command-block minecart's edit (Slice 66 — the MC|AdvCdm
     * custom payload's minecart arm, ServerPlayNetworkHandler lines
     * 1036-1051): the op-2 creative player writes the command and the
     * track flag; an untracked cart clears its last output. The edit
     * feed ACKs with the reference's advMode.setCommand.success. Safe
     * from any thread; the write runs on the tick lane.
     */
    public void updateCommandBlockMinecart(PlayerSession player, int entityId,
                                           String command, boolean trackOutput) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || vehicleManager == null) {
                return;
            }
            var vehicle = vehicleManager.byId(entityId);
            if (!(vehicle instanceof net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity cart)) {
                return;
            }
            cart.setCommand(command);
            cart.setTrackOutput(trackOutput);
            if (!trackOutput) {
                cart.setLastOutput(null);
            }
        });
    }

    /** The held identifier: the creative claim first, else the real stack. */
    private String heldIdentifierOf(PlayerSession session, java.util.Optional<BlockType> creativeHeld) {
        if (creativeHeld.isPresent()) {
            return creativeHeld.get().identifier().value();
        }
        return session.inventory().held().isEmpty()
                ? null
                : session.inventory().held().type().identifier().value();
    }

    /** The survival consume of one placed item (the shared placement tail). */
    private void consumePlaced(PlayerSession session, java.util.Optional<BlockType> creativeHeld) {
        if (creativeHeld.isEmpty() && session.gamemode() != GameMode.CREATIVE) {
            session.inventory().consumeHeld(1);
            publishInventoryChanged(session);
        }
    }

    /** The ladder query for the movement guard: climbing bodies hover honestly. */
    private boolean ladderAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return WorldSolidity.isLadder(world.getBlock(blockAt(x, y, z)).identifier());
    }

    /** Eye-to-block-center distance squared, the survival reach gate's shape. */
    private static double distanceSquaredEyeToBlock(Position playerPosition, BlockPosition target) {
        double dx = (target.x() + 0.5) - playerPosition.x();
        double dy = (target.y() + 0.5) - (playerPosition.y() + 1.62);
        double dz = (target.z() + 0.5) - playerPosition.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Whether the block AABB intersects the player's bounding box. */
    private static boolean intersectsPlayerBox(Position p, BlockPosition block) {
        return block.x() + 1 > p.x() - 0.3 && block.x() < p.x() + 0.3
                && block.y() + 1 > p.y() && block.y() < p.y() + 1.8
                && block.z() + 1 > p.z() - 0.3 && block.z() < p.z() + 0.3;
    }

    /** The 1.8 face-to-offset table (0=-Y, 1=+Y, 2=-Z, 3=+Z, 4=-X, 5=+X). */
    private static BlockPosition offsetByFace(BlockPosition clicked, int face) {
        return switch (face) {
            case 0 -> clicked.offset(0, -1, 0);
            case 1 -> clicked.offset(0, 1, 0);
            case 2 -> clicked.offset(0, 0, -1);
            case 3 -> clicked.offset(0, 0, 1);
            case 4 -> clicked.offset(-1, 0, 0);
            case 5 -> clicked.offset(1, 0, 0);
            default -> null;
        };
    }

    /**
     * The mounted player's steering (Steer Vehicle 0x0C): the input feeds
     * the ridden vehicle's next tick; the jump bit's unmount flag (0x02)
     * opens the seat. Safe from any thread; the application runs on the
     * tick thread.
     */
    public void steerVehicle(PlayerSession player, float sideways, float forward,
                             boolean jump, boolean unmount) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || player.dead()) {
                return;
            }
            if (vehicleManager != null) {
                var vehicle = vehicleManager.byId(player.ridingVehicleId());
                if (vehicle != null) {
                    if (unmount) {
                        vehicleManager.dismount(vehicle);
                        return;
                    }
                    vehicle.steer(new net.zaminmc.torch.server.entity.vehicle.VehicleEntity.SteerInput(
                            sideways, forward, jump, false));
                    return;
                }
            }
            // The mob mounts (the horse/pig slice): the seat answers the
            // reins through the manager — the control gate is the manager's
            // (saddle for the horse, carrot on a stick for the pig).
            if (mobManager != null) {
                MobEntity mount = mobManager.byId(player.ridingMobId());
                if (mount != null) {
                    if (unmount) {
                        mobManager.dismountMob(mount, false);
                        return;
                    }
                    boolean controls = mount.type() == MobType.HORSE
                            ? mount.saddled()
                            : player.inventory().held().type().identifier()
                                    .toString().equals("minecraft:carrot_on_a_stick");
                    mobManager.steerRidden(mount, sideways, forward, jump, controls);
                }
            }
        });
    }

    /**
     * The interact-at arm (Use Entity 0x02 mouse=2, the offset-carrying
     * click — Slice 28c): the armor stand's equipment swap ladder reads
     * the hit's own height to name the worn piece. The client follows
     * with the plain INTERACT (a no-op for the stands — the reference's
     * own interactAt-then-interact pair).
     */
    public void interactAtEntity(PlayerSession player, int targetEntityId,
                                 double offsetX, double offsetY, double offsetZ) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || player.dead()
                    || armorStands == null) {
                return;
            }
            net.zaminmc.torch.server.armorstand.ArmorStand stand =
                    armorStands.byId(targetEntityId);
            if (stand == null) {
                return; // only the stands read the offset
            }
            double dx = stand.x() - player.position().x();
            double dy = stand.y() - player.position().y();
            double dz = stand.z() - player.position().z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > MELEE_REACH + 1.0 || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            PlayerInventory inventory = player.inventory();
            int heldSlot = inventory.heldSlot();
            if (armorStands.interactAt(stand, inventory.held(), offsetY,
                    player.gamemode() == GameMode.CREATIVE,
                    replacement -> inventory.setSlot(heldSlot, replacement))) {
                publishInventoryChanged(player);
            }
        });
    }

    /**
     * A player right-clicked a mob or vehicle (Use Entity 0x02, mouse 0/2):
     * a vehicle within reach takes the seat (the mount); a horse or pig in
     * reach takes the saddle/armor/food or the rider; a wooly mob with
     * shears in hand loses its coat. Safe from any thread; the application
     * runs on the tick thread.
     */
    public void interactEntity(PlayerSession player, int targetEntityId) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || player.dead() || mobManager == null) {
                return;
            }
            // The mount: a vehicle within reach takes the rider (one seat).
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle =
                    vehicleManager != null ? vehicleManager.byId(targetEntityId) : null;
            if (vehicle != null) {
                double dx = vehicle.position().x() - player.position().x();
                double dy = vehicle.position().y() - player.position().y();
                double dz = vehicle.position().z() - player.position().z();
                double horizontal = Math.sqrt(dx * dx + dz * dz);
                if (horizontal > MELEE_REACH + 0.7 || dy < -2.0 || dy > 4.0) {
                    return; // out of reach: the server-side refusal
                }
                // The chest minecart's interact opens the chest menu (the
                // reference's InventoryMinecartEntity.interact — never the
                // seat; Slice 21a).
                if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart) {
                    openChestMinecartInventoryOnTick(player, cart);
                    return;
                }
                // The hopper minecart's interact (Slice 66 —
                // HopperMinecartEntity.interact lines 54-60):
                // openChestMenu over the cart's five slots — never the
                // seat.
                if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity hopperCart) {
                    openHopperMinecartInventoryOnTick(player, hopperCart);
                    return;
                }
                // The command cart's interact (Slice 66 —
                // CommandBlockMinecartEntity.interact lines 107-110 over
                // CommandExecutor.openScreen lines 160-174): the creative
                // GUI opens client-side (the server sends nothing — the
                // synced slots 23/24 already carry the data); the
                // non-creative click answers false. Either way the cart
                // never takes a seat.
                if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity) {
                    return;
                }
                // The furnace minecart's interact (Slice 66 —
                // FurnaceMinecartEntity.interact lines 106-117): a coal in
                // hand feeds 3600 ticks (the survival consume, the creative
                // spare), and the push vector always re-aims away from the
                // player — the cart never takes a seat.
                if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.FurnaceMinecartEntity furnaceCart) {
                    boolean fed = false;
                    if (player.inventory().held().type().equals(BuiltinItems.COAL)) {
                        fed = true;
                        if (player.gamemode() != GameMode.CREATIVE) {
                            player.inventory().consumeHeld(1);
                            publishInventoryChanged(player);
                        }
                    }
                    furnaceCart.feedAndAim(player.position().x(), player.position().z(), fed);
                    return;
                }
                vehicleManager.mount(vehicle, player.engineEntityId());
                return;
            }
            MobEntity mob = mobManager.byId(targetEntityId);
            if (mob == null || mob.dead()) {
                // The item frame's interact (Slice 28a — ItemFrameEntity.
                // interact lines 180-194): the insert (a copy of the held
                // stack, one unit off the hand) or the rotation turn.
                net.zaminmc.torch.server.decoration.ItemFrame frame = decorations != null
                        ? decorations.frameById(targetEntityId) : null;
                if (frame != null) {
                    double fdx = frame.hangingPosition().x() - player.position().x();
                    double fdy = frame.hangingPosition().y() - player.position().y();
                    double fdz = frame.hangingPosition().z() - player.position().z();
                    double fHorizontal = Math.sqrt(fdx * fdx + fdz * fdz);
                    if (fHorizontal <= MELEE_REACH + 1.0 && fdy > -2.0 && fdy < 4.0) {
                        if (decorations.interactFrame(frame, player.inventory().held())
                                == net.zaminmc.torch.server.decoration.DecorationSystem
                                        .FrameInteract.INSERTED
                                && player.gamemode() != GameMode.CREATIVE) {
                            // The insert's one unit off the hand (the
                            // reference's size--; creative spares).
                            player.inventory().consumeHeld(1);
                            publishInventoryChanged(player);
                        }
                    }
                    return;
                }
                // The painting has no interact arm (the reference's
                // PaintingEntity inherits the empty one) — the click
                // falls through to nothing; the knot arm runs next.
                // The leash knot (Slice 11c): its own interact — the tie with
                // a lead in hand, the removal without one (creative releases
                // the held without the drop).
                var knot = leadSystem != null
                        ? leadSystem.allKnots().stream()
                                .filter(k -> k.entityId() == targetEntityId).findFirst().orElse(null)
                        : null;
                if (knot != null) {
                    double kdx = knot.position().x() - player.position().x();
                    double kdy = knot.position().y() - player.position().y();
                    double kdz = knot.position().z() - player.position().z();
                    double kHorizontal = Math.sqrt(kdx * kdx + kdz * kdz);
                    if (kHorizontal <= MELEE_REACH + 1.0 && kdy > -2.0 && kdy < 4.0) {
                        leadSystem.interactKnot(player, knot);
                    }
                }
                return;
            }
            // The leash arms first (the reference's MobEntity.interact order:
            // the held-mob release, then the lead's attach + consume).
            if (leadSystem != null) {
                double ldx = mob.position().x() - player.position().x();
                double ldy = mob.position().y() - player.position().y();
                double ldz = mob.position().z() - player.position().z();
                double lHorizontal = Math.sqrt(ldx * ldx + ldz * ldz);
                if (lHorizontal <= MELEE_REACH + mob.type().width * 0.5
                        && ldy > -2.0 && ldy < 4.0
                        && leadSystem.interactMobWithLead(player, mob)) {
                    return;
                }
            }
            // The zombie-villager cure (Slice 25a — ZombieEntity.interactMob
            // lines 457-480): the plain golden apple on a WEAKENED
            // villager-skinned zombie arms the conversion. The weakness
            // gates first (a splash potion's 1.5-minute window is the
            // famous recipe), the apple's own plain form (the notched
            // apple is its own item id in the engine's registry).
            if (mob.type() == MobType.ZOMBIE && mob.zombieVillager()) {
                ItemStack heldCure = player.inventory().held();
                String heldCureId = heldCure.type() == null
                        ? "" : heldCure.type().identifier().toString();
                if (heldCureId.equals("minecraft:golden_apple")
                        && potionSystem != null
                        && potionSystem.hasEffect(mob,
                                net.zaminmc.torch.server.potion.StatusEffects.WEAKNESS)
                        && mobManager.cureZombieVillager(mob)) {
                    eatFromHand(player); // the reference's own size-- (creative spares)
                }
                return; // no other arm runs on the zombie villager
            }
            // The villager branch: right-click opens the trading window.
            if (mob.isTrader()) {
                interactVillagerOnTick(player, mob);
                return;
            }
            // The horse/pig branch: saddle, armor, feed, inventory, seat.
            if (mob.isMountable()) {
                interactMountOnTick(player, mob);
                return;
            }
            // The wolf branch (Slice 15d — WolfEntity.interactMob's own
            // ladder): the tame, the feed, the sit toggle.
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.WOLF) {
                interactWolfOnTick(player, mob);
                return;
            }
            // The ocelot branch (Slice 15e — OcelotEntity.interactMob's own
            // ladder): the fish tame, the sit toggle.
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.OCELOT) {
                interactOcelotOnTick(player, mob);
                return;
            }
            // The animal branch: the breeding foods (the vanilla interactMob
            // feeding arm — love for adults, growth for babies) plus the
            // sheep's shears (the interactMob order: shears first).
            if (isAnimal(mob.type())) {
                interactAnimalOnTick(player, mob);
                return;
            }
        });
    }

    /** @return whether the kind breeds and feeds like an animal. */
    private static boolean isAnimal(MobType type) {
        return type == MobType.PIG || type == MobType.COW || type == MobType.SHEEP
                || type == MobType.CHICKEN || type == MobType.HORSE || type == MobType.RABBIT
                || type == MobType.MOOSHROOM
                || type == MobType.WOLF; // the wolf feeds through its own ladder, breeds here
    }

    /**
     * The vanilla AnimalEntity.isBreedingItem table (the per-kind override):
     * the pig takes the carrot, the cow and sheep the wheat, the chicken the
     * seeds; the horse family feeds through its own golden table instead.
     * The rabbit's own family (RabbitEntity.isTempting): the carrot, the
     * golden carrot and the dandelion (the reference's yellow flower).
     */
    private static boolean isBreedingItem(MobType type, String heldId) {
        return switch (type) {
            case PIG -> heldId.equals("minecraft:carrot");
            case COW, SHEEP, MOOSHROOM -> heldId.equals("minecraft:wheat");
            case CHICKEN -> heldId.equals("minecraft:wheat_seeds");
            case RABBIT -> heldId.equals("minecraft:carrot")
                    || heldId.equals("minecraft:golden_carrot")
                    || heldId.equals("minecraft:dandelion");
            // WolfEntity.isBreedingItem: the FoodItem.canBeCooked() family —
            // the meats (the fish pair + the mutton ride their item slices).
            case WOLF -> heldId.equals("minecraft:porkchop")
                    || heldId.equals("minecraft:cooked_porkchop")
                    || heldId.equals("minecraft:beef")
                    || heldId.equals("minecraft:cooked_beef")
                    || heldId.equals("minecraft:chicken")
                    || heldId.equals("minecraft:cooked_chicken")
                    || heldId.equals("minecraft:rabbit")
                    || heldId.equals("minecraft:cooked_rabbit");
            // OcelotEntity.isBreedingItem: the raw fish only.
            case OCELOT -> heldId.equals("minecraft:fish");
            default -> false;
        };
    }

    /**
     * The animal right-click dispatch (the vanilla interactMob order for the
     * non-mount kinds): the sheep's shears arm first, then the breeding food.
     * Tick-thread context.
     */
    private void interactAnimalOnTick(PlayerSession player, MobEntity mob) {
        double dx = mob.position().x() - player.position().x();
        double dy = mob.position().y() - player.position().y();
        double dz = mob.position().z() - player.position().z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > MELEE_REACH + mob.type().width * 0.5 || dy < -2.0 || dy > 4.0) {
            return; // out of reach: the server-side refusal
        }
        var inventory = player.inventory();
        ItemStack held = inventory.held();
        String heldId = held.type() == null ? "" : held.type().identifier().toString();
        // The mooshroom's own ladder (MooshroomEntity.interactMob): the
        // bowl fills with the mushroom stew (the adult only), the shears
        // shear it INTO a plain cow with five red mushrooms — both arms
        // before the feeding fallback (the cow's wheat).
        if (mob.type() == MobType.MOOSHROOM && !mob.isBaby()
                && heldId.equals("minecraft:bowl")) {
            // The bowl-to-stew swap (MooshroomEntity.interactMob lines
            // 19-31): a single bowl becomes the stew in the hand; a
            // stacked one gives the stew through the pickup with one
            // bowl consumed (the creative hand keeps the stack — the
            // engine's survival inventory walks the reference's pair).
            if (player.gamemode() == GameMode.CREATIVE) {
                player.inventory().pickUp(ItemStack.of(BuiltinItems.MUSHROOM_STEW, 1));
            } else if (held.count() == 1) {
                player.inventory().setSlot(player.inventory().heldSlot(),
                        ItemStack.of(BuiltinItems.MUSHROOM_STEW, 1));
            } else {
                player.inventory().pickUp(ItemStack.of(BuiltinItems.MUSHROOM_STEW, 1));
                player.inventory().consumeHeld(1);
            }
            publishInventoryChanged(player);
            return;
        }
        if (mob.type() == MobType.MOOSHROOM && !mob.isBaby()
                && heldId.equals("minecraft:shears")) {
            // The shear-into-a-cow (MooshroomEntity.interactMob lines
            // 33-56): the body replaces itself with a plain cow at the
            // same position and health, five red mushrooms pop, the tool
            // wears one use, and the shear sound lands.
            float health = mob.health();
            Position at = mob.position();
            float yaw = mob.bodyYawLive();
            mobManager.remove(mob, "sheared");
            MobEntity cow = mobManager.spawnAt(MobType.COW, at);
            if (cow != null) {
                cow.setBodyYaw(yaw);
                cow.setHealthCarried(health);
            }
            for (int i = 0; i < 5; i++) {
                itemEntities.spawnDropAtBlock(new Position(at.x(), at.y() + mob.type().height, at.z()),
                        ItemStack.of(BuiltinItems.RED_MUSHROOM, 1),
                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            player.inventory().damageHeld(1, gameplayRandom); // the shears wear
            publishInventoryChanged(player);
            fxManager.sound(at, "mob.sheep.shear", 1.0f, 1.0f);
            return;
        }
        // The sheep's shears (EntitySheep.interactMob: the shears arm runs
        // before the feeding arm).
        if (mob.type() == MobType.SHEEP && heldId.equals("minecraft:shears")) {
            if (mobManager.shear(mob) > 0) {
                player.inventory().damageHeld(1, gameplayRandom); // the shears wear one use
                publishInventoryChanged(player);
            }
            return;
        }
        if (mob.type() == MobType.HORSE) {
            return; // the horse family feeds through the mount branch only
        }
        // The item's own interact consult (PlayerEntity.interact line 898:
        // the mob's interactMob first, then the held item's arm). The feed
        // is the mob's arm; the dye is the item's (ItemDye.interact lines
        // 124-138) — an unsheared sheep of a different color takes the dye
        // (one unit consumed, no creative guard — the reference's own
        // shape), and ANY dye-on-sheep use is consumed by the arm.
        boolean fed = feedAnimal(player, mob);
        if (!fed && mob.type() == MobType.SHEEP && heldId.equals("minecraft:dye")) {
            int dyeMetadata = held.damage() & 0xF;
            int dyeColorId = 15 - dyeMetadata; // byMetadata → getId
            if (!mob.sheared() && mob.sheepFleeceColor() != dyeColorId) {
                mob.setSheepFleeceColor(dyeColorId);
                player.inventory().consumeHeld(1);
                publishInventoryChanged(player);
                publishSheepFleece(mob);
            }
        }
    }

    /**
     * The vanilla AnimalEntity feeding arm: the breeding item on an adult
     * (age 0, not already in love) starts the 600-tick love window; on a
     * baby it shrinks the childhood by {@code (int)(-age / 20 * 0.1F)}
     * units. The food is eaten in both arms (the creative hand spares the
     * stack — the vanilla eat() rule). @return whether the food was taken.
     */
    private boolean feedAnimal(PlayerSession player, MobEntity mob) {
        ItemStack held = player.inventory().held();
        String heldId = held.type() == null ? "" : held.type().identifier().toString();
        if (!isBreedingItem(mob.type(), heldId)) {
            return false;
        }
        if (mob.isBaby()) {
            mob.growUp((int) (-mob.breedingAge() / 20 * 0.1f));
            eatFromHand(player);
            return true;
        }
        if (mob.breedingAge() == 0 && !mob.isInLove()) {
            mob.enterLove(player.engineEntityId());
            eatFromHand(player);
            return true;
        }
        return false;
    }

    /**
     * The wolf's right-click ladder (WolfEntity.interactMob, the reference's
     * own order): a TAMED body takes the meat heal under 20 health, then the
     * owner's empty hand toggles the sit; an UNTAMED body takes the bone —
     * the 1-in-3 tame roll with the entity-event verdict (7 hearts, 6
     * smoke) — and the breeding food falls through to the shared animal
     * feed. Tick-thread context.
     */
    private void interactWolfOnTick(PlayerSession player, MobEntity mob) {
        double dx = mob.position().x() - player.position().x();
        double dy = mob.position().y() - player.position().y();
        double dz = mob.position().z() - player.position().z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > MELEE_REACH + mob.type().width * 0.5 || dy < -2.0 || dy > 4.0) {
            return; // out of reach: the server-side refusal
        }
        var inventory = player.inventory();
        ItemStack held = inventory.held();
        String heldId = held.type() == null ? "" : held.type().identifier().toString();

        if (mob.wolfTamed()) {
            // The meat heal (interactMob lines 300-313): the canBeCooked
            // family under the tamed maximum.
            int heal = wolfFeedHeal(heldId);
            if (heal > 0 && mob.health() < mob.maxHealth()) {
                mob.heal(heal);
                eatFromHand(player);
                return;
            }
            // The dye arm (interactMob lines 314-324): a different color
            // takes the collar (one unit consumed in survival — the
            // reference's own {@code !creativeMode} guard); the same color
            // falls through to the sit toggle below, the reference's own
            // ladder.
            if (heldId.equals("minecraft:dye")) {
                int dyeMetadata = held.damage() & 0xF;
                if (mob.wolfCollarColor() != dyeMetadata) {
                    mob.setWolfCollarColor(dyeMetadata);
                    if (player.gamemode() != GameMode.CREATIVE) {
                        player.inventory().consumeHeld(1);
                        publishInventoryChanged(player);
                    }
                    publishWolfState(mob);
                    return;
                }
            }
            // The owner's empty-hand verb (lines 327-332): the sit toggle —
            // a held breeding food feeds through the fall-through below
            // instead (the reference's own isBreedingItem gate).
            if (mob.ownerName().equals(player.name())
                    && !isBreedingItem(mob.type(), heldId)) {
                mob.toggleWolfSitting();
                publishWolfState(mob);
                return;
            }
        } else if (heldId.equals("minecraft:bone") && !mob.wolfAngry()) {
            // The tame roll (lines 333-359): the bone is always eaten; the
            // 1-in-3 roll tames (the full flip: the 20-health tamed body,
            // the owner, the sitting start, the hearts) or smokes.
            eatFromHand(player);
            if (gameplayRandom.nextInt(3) == 0) {
                mob.tameWolf(player.name());
                publishWolfState(mob);
                sendMobStatusToAll(mob, 7); // the taming hearts
            } else {
                sendMobStatusToAll(mob, 6); // the taming smoke
            }
            return;
        }
        // The fall-through: the shared animal feed (the tamed-only breed
        // arm — the wolf's isBreedingItem is the meat family).
        feedAnimal(player, mob);
    }

    /**
     * The ocelot's right-click ladder (OcelotEntity.interactMob): a TAMED
     * body takes the owner's empty-hand sit toggle; an UNTAMED body within
     * 3 blocks takes the raw fish — the 1-in-3 tame roll with the coat's
     * own {@code 1 + nextInt(3)} variant and the entity-event verdict (7
     * hearts, 6 smoke). The reference's tempt-gate (the slow approach with
     * the fish) and the creep-flee family ledger with the player-velocity
     * wave — the distance gate carries the tame's reach here. The breeding
     * (the raw fish) falls through to the shared animal feed.
     */
    private void interactOcelotOnTick(PlayerSession player, MobEntity mob) {
        double dx = mob.position().x() - player.position().x();
        double dy = mob.position().y() - player.position().y();
        double dz = mob.position().z() - player.position().z();
        if (dx * dx + dy * dy + dz * dz >= 9.0) {
            return; // squaredDistanceTo >= 9: out of the reference's own reach
        }
        var inventory = player.inventory();
        ItemStack held = inventory.held();
        String heldId = held.type() == null ? "" : held.type().identifier().toString();

        if (mob.wolfTamed()) {
            // The owner's empty-hand verb: the sit toggle (a held breeding
            // fish feeds through the fall-through instead).
            if (mob.ownerName().equals(player.name())
                    && !isBreedingItem(mob.type(), heldId)) {
                mob.toggleWolfSitting();
                publishWolfState(mob);
                return;
            }
        } else if (heldId.equals("minecraft:fish") && mob.temptActive()) {
            // The tame roll (interactMob lines 158-176): the reference's own
            // gate — the tempt goal must be live (the cat crept to the fish)
            // AND the player still within the 3-block reach. The fish is
            // eaten; the 1-in-3 lands the flag, the coat, the owner, the sit.
            eatFromHand(player);
            if (gameplayRandom.nextInt(3) == 0) {
                mob.tameOcelot(player.name(), 1 + gameplayRandom.nextInt(3));
                publishWolfState(mob);
                sendMobStatusToAll(mob, 7); // the taming hearts
            } else {
                sendMobStatusToAll(mob, 6); // the taming smoke
            }
            return;
        }
        feedAnimal(player, mob); // the raw-fish breed arm
    }

    /** @return the wolf feed's heal value (the FoodItem hunger points, 0 when not meat). */
    private static int wolfFeedHeal(String itemId) {
        return switch (itemId) {
            case "minecraft:porkchop", "minecraft:beef", "minecraft:rabbit" -> 3;
            case "minecraft:cooked_porkchop", "minecraft:cooked_beef" -> 8;
            case "minecraft:chicken" -> 2;
            case "minecraft:cooked_chicken" -> 6;
            case "minecraft:cooked_rabbit" -> 5;
            default -> 0; // the fish pair + the mutton ride their item slices
        };
    }

    /** Rings the mob-status entity event to every tracking connection (doEntityEvent). */
    private void sendMobStatusToAll(MobEntity mob, int status) {
        for (MobManager.Listener listener : mobListeners) {
            listener.onMobStatusEvent(mob, status);
        }
    }

    /** The wolf's state fan-out (the metadata update seam). */
    private void publishWolfState(MobEntity mob) {
        for (MobManager.Listener listener : mobListeners) {
            listener.onWolfStateChanged(mob);
        }
    }

    /**
     * The fleece color moved (the dye arm's write): the observers re-send
     * the index-16 nibble (the color | the sheared bit's byte).
     */
    private void publishSheepFleece(MobEntity mob) {
        for (MobManager.Listener listener : mobListeners) {
            listener.onSheepFleeceChanged(mob);
        }
    }

    /** The vanilla eat(): the creative hand keeps the stack. */
    private void eatFromHand(PlayerSession player) {
        if (player.gamemode() != GameMode.CREATIVE) {
            player.inventory().consumeHeld(1);
            publishInventoryChanged(player);
        }
    }

    /** @return the horse food's heal value f (0 when not food). */
    private static float horseFoodHeal(String itemId) {
        return switch (itemId) {
            case "minecraft:wheat" -> 2.0f;
            case "minecraft:sugar" -> 1.0f;
            case "minecraft:hay_block" -> 20.0f;
            case "minecraft:apple" -> 3.0f;
            case "minecraft:golden_carrot" -> 4.0f;
            case "minecraft:golden_apple" -> 10.0f;
            default -> 0.0f;
        };
    }

    /** @return the horse food's growth value j (20-tick units). */
    private static int horseFoodGrow(String itemId) {
        return switch (itemId) {
            case "minecraft:wheat" -> 20;
            case "minecraft:sugar" -> 30;
            case "minecraft:hay_block" -> 180;
            case "minecraft:apple" -> 60;
            case "minecraft:golden_carrot" -> 60;
            case "minecraft:golden_apple" -> 240;
            default -> 0;
        };
    }

    /** @return the horse food's temper value k. */
    private static int horseFoodTemper(String itemId) {
        return switch (itemId) {
            case "minecraft:wheat" -> 3;
            case "minecraft:sugar" -> 3;
            case "minecraft:apple" -> 3;
            case "minecraft:golden_carrot" -> 5;
            case "minecraft:golden_apple" -> 10;
            default -> 0;
        };
    }

    /**
     * The horse/pig right-click dispatch (the vanilla rule ladder): the
     * saddle equips either kind; horse armor equips a tamed horse; the
     * temper foods feed a horse; the sneak-open reads the mount's inventory;
     * everything else is a mount attempt (temper flow for the wild horse).
     * Tick-thread context.
     */
    private void interactMountOnTick(PlayerSession player, MobEntity mob) {
        double dx = mob.position().x() - player.position().x();
        double dy = mob.position().y() - player.position().y();
        double dz = mob.position().z() - player.position().z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > MELEE_REACH + mob.type().width * 0.5 || dy < -2.0 || dy > 4.0) {
            return; // out of reach: the server-side refusal
        }
        var inventory = player.inventory();
        ItemStack held = inventory.held();
        // The empty hand carries a null type (the canonical EMPTY record) —
        // the bare-hand mount path must stay alive (the dev.9 NPE lesson).
        String heldId = held.type() == null ? "" : held.type().identifier().toString();

        // The saddle equips horses and pigs (the vanilla right-click rule).
        if (heldId.equals("minecraft:saddle") && !mob.saddled()) {
            mob.applySaddle();
            inventory.consumeHeld(1);
            publishInventoryChanged(player);
            publishHorseFlags(mob);
            fxManager.sound(mob.position(), "mob.horse.armor", 0.8f, 1.0f);
            return;
        }
        // The horse armors: tamed horses only (the vanilla gate).
        if (mob.type() == MobType.HORSE && mob.tamed()
                && heldId.endsWith("_horse_armor") && mob.armorType() == MobEntity.HORSE_ARMOR_NONE) {
            int row = heldId.contains("diamond") ? MobEntity.HORSE_ARMOR_DIAMOND
                    : heldId.contains("gold") ? MobEntity.HORSE_ARMOR_GOLD
                    : MobEntity.HORSE_ARMOR_IRON;
            mob.applyArmor(row);
            inventory.consumeHeld(1);
            publishInventoryChanged(player);
            publishHorseFlags(mob);
            fxManager.sound(mob.position(), "mob.horse.armor", 0.8f, 1.0f);
            return;
        }
        // The horse food table (HorseBaseEntity.interactMob): heal f, grow j
        // (20-tick units), temper k; the golden pair also love a tamed adult
        // (the love needs the tame bit, the temper band never does). Untamed
        // bodies still gain the temper — the historical feeding taming aid.
        if (mob.type() == MobType.HORSE && !heldId.isEmpty()
                && horseFoodHeal(heldId) > 0.0f) {
            boolean flag = false;
            float heal = horseFoodHeal(heldId);
            int grow = horseFoodGrow(heldId);
            int temper = horseFoodTemper(heldId);
            if (mob.health() < mob.maxHealth()) {
                mob.heal(heal);
                flag = true;
            }
            if (mob.isBaby() && grow > 0) {
                mob.growUp(grow);
                flag = true;
            }
            boolean golden = heldId.equals("minecraft:golden_carrot")
                    || heldId.equals("minecraft:golden_apple");
            if (golden && mob.tamed() && mob.breedingAge() == 0 && !mob.isInLove()) {
                mob.enterLove(player.engineEntityId());
                flag = true;
            }
            if (temper > 0 && (flag || !mob.tamed())) {
                mob.feedTemper(temper);
                flag = true;
            }
            if (flag) {
                mob.flashEating();
                fxManager.sound(mob.position(), "eating", 1.0f, 1.0f);
                if (player.gamemode() != GameMode.CREATIVE) {
                    inventory.consumeHeld(1);
                    publishInventoryChanged(player);
                }
                publishHorseFlags(mob);
                return;
            }
            // No arm applied (a fed full-health tamed adult): the fall-through
            // is the vanilla seat attempt, exactly the reference flow.
        }
        // The pig's carrot (EntityPig.interactMob: love for adults, growth
        // for babies — no tame gate; the pig breeds as it is).
        if (mob.type() == MobType.PIG && heldId.equals("minecraft:carrot")) {
            if (feedAnimal(player, mob)) {
                return;
            }
        }
        // The chest equips a tamed donkey or mule (the HorseBaseEntity chest
        // arm: canHaveChest = subtype 1/2, the plop sound rides along).
        if (mob.type() == MobType.HORSE && mob.tamed() && !mob.chested()
                && (mob.horseSubtype() == MobEntity.HORSE_SUBTYPE_DONKEY
                || mob.horseSubtype() == MobEntity.HORSE_SUBTYPE_MULE)
                && heldId.equals("minecraft:chest")) {
            mob.setChested(true);
            fxManager.sound(mob.position(), "mob.chickenplop", 1.0f, 1.0f);
            publishHorseFlags(mob);
            if (player.gamemode() != GameMode.CREATIVE) {
                inventory.consumeHeld(1);
                publishInventoryChanged(player);
            }
            return;
        }
        // The sneak-open reads the mount's inventory (the vanilla GUI).
        if (player.sneaking() && mob.type() == MobType.HORSE) {
            openHorseInventoryOnTick(player, mob);
            return;
        }
        // The seat: already seated players swap nothing (the one-body rule).
        if (player.ridingAny()) {
            return;
        }
        mobManager.mountMob(mob, player.engineEntityId());
    }

    /** Re-publishes the index-16 horse flags to the observers (deltas ride the 0x1C). */
    private void publishHorseFlags(MobEntity mob) {
        for (MobManager.Listener listener : mobListeners) {
            listener.onHorseFlagsChanged(mob);
        }
    }

    /**
     * Opens the villager's trading window ("minecraft:villager", 3 GUI
     * slots; the offers ride the MC|TrList plugin message behind it).
     * Tick-thread context.
     */
    private void interactVillagerOnTick(PlayerSession player, MobEntity villager) {
        double dx = villager.position().x() - player.position().x();
        double dy = villager.position().y() - player.position().y();
        double dz = villager.position().z() - player.position().z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > MELEE_REACH + villager.type().width * 0.5 || dy < -2.0 || dy > 4.0) {
            return; // out of reach: the server-side refusal
        }
        closeOpenContainerOnTick(player);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        player.openVillagerWindow(windowId, villager.entityId());
        // The adapter must send Open Window then the MC|TrList plugin message
        // (the crafting-table order rule; the 1.8 client renders the offers).
        for (MobManager.Listener listener : mobListeners) {
            listener.onVillagerTradeOpened(player, villager, windowId);
        }
    }

    /**
     * The MC|TrSel landing: the client picked a trade row. The result slot
     * previews the offer (the Set Slot), the execution happens on the slot-2
     * click. Safe from any thread; the application runs on the tick thread.
     */
    /**
     * The MC|Beacon confirm (Slice 29b — ServerPlayNetworkHandler lines
     * 1062-1080): only with a payment in the open beacon's slot — one unit
     * consumes, then the two effect choices land through the reference's
     * own gate. Safe from any thread.
     */
    public void beaconSelect(PlayerSession session, int primary, int secondary) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.openContainerKind() != PlayerSession.ContainerKind.BEACON
                    || session.openContainerPosition() == null) {
                return;
            }
            beaconSystem.applySelection(session.openContainerPosition(), primary, secondary);
            publishInventoryChanged(session);
        });
    }

    public void tradeSelect(PlayerSession player, int offerIndex) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || player.dead()
                    || player.openContainerKind() != PlayerSession.ContainerKind.VILLAGER) {
                return;
            }
            MobEntity villager = mobManager.byId(player.containerMountId());
            if (villager == null || !villager.isTrader()
                    || offerIndex < 0 || offerIndex >= villager.offers().length) {
                return; // stale window or a rogue index: the preview stays empty
            }
            player.setSelectedTrade(offerIndex);
            ItemStack result = villager.offers()[offerIndex].result();
            for (MobManager.Listener listener : mobListeners) {
                listener.onTradeSelected(player, player.openContainerWindowId(), result);
            }
        });
    }

    /**
     * The villager window's clicks: slot 2 executes the selected trade (the
     * buys validate out of the player's own inventory — the 1.8 merchant
     * rule —, the result pays out, the use counter charges). Tick-thread.
     */
    private boolean clickVillagerWindowOnTick(PlayerSession session, int wireSlot,
                                              int button, int mode) {
        if (mode != 0 || wireSlot != 2) {
            return false; // only a plain left-click on the result slot trades
        }
        MobEntity villager = mobManager.byId(session.containerMountId());
        if (villager == null || !villager.isTrader()) {
            return false; // stale window (the trader gone): rejected, resync restores
        }
        int index = session.selectedTrade();
        TradeOffer[] offers = villager.offers();
        if (index < 0 || index >= offers.length || !villager.offerAvailable(index)) {
            return false; // nothing selected or the budget spent out (greyed)
        }
        TradeOffer offer = offers[index];
        var inventory = session.inventory();
        // The buys validate against the player's own inventory (the 1.8
        // merchant GUI's pseudo-slots are display-only).
        if (inventory.countOf(offer.buy1().type()) < offer.buy1().count()) {
            return false;
        }
        if (offer.hasSecondBuy()
                && inventory.countOf(offer.buy2().type()) < offer.buy2().count()) {
            return false;
        }
        inventory.removeItems(offer.buy1().type(), offer.buy1().count());
        if (offer.hasSecondBuy()) {
            inventory.removeItems(offer.buy2().type(), offer.buy2().count());
        }
        ItemStack remainder = inventory.pickUp(offer.result());
        if (!remainder.isEmpty()) {
            throwFromPlayer(session, remainder); // a full inventory drops it (nothing lost)
        }
        // VillagerEntity.trade (lines 604-633): the use charges with the
        // level-up arm's own bookkeeping, the yes sound at the body, and
        // the XP orb the reference spawns beside it.
        int xp = villager.villagerTrade(index);
        fxManager.sound(villager.position(), "mob.villager.yes", 1.0f,
                1.0f / (gameplayRandom.nextFloat() * 0.4f + 0.8f));
        experienceOrbs.spawnOrb(new Position(villager.position().x(),
                villager.position().y() + 0.5, villager.position().z()), xp);
        return true;
    }

    /**
     * Opens the horse inventory ("EntityHorse" window: saddle + armor slots
     * on the mount, the player inventory below). Tick-thread context.
     */
    private void openHorseInventoryOnTick(PlayerSession session, MobEntity mob) {
        closeOpenContainerOnTick(session);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openHorseWindow(windowId, mob.entityId());
        // The adapter must send Open Window (with the trailing mount id)
        // before any slot data — the crafting-table order rule.
        for (MobManager.Listener listener : mobListeners) {
            listener.onHorseInventoryOpened(session, mob, windowId);
        }
    }

    /**
     * Opens the chest minecart's chest menu (Slice 21a — the reference's
     * InventoryMinecartEntity.interact -> openChestMenu: the "minecraft:chest"
     * window over the cart's own 27 slots). Tick-thread context.
     */
    private void openChestMinecartInventoryOnTick(
            PlayerSession session,
            net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openChestMinecartWindow(windowId, cart.entityId());
        for (VehicleListener listener : vehicleListeners) {
            listener.onChestMinecartWindowOpened(session, cart, windowId);
        }
    }

    /**
     * The chest minecart window's clicks (Slice 21a): the same chest-menu
     * semantics over the cart's own 27 slots (the reference's ChestMenu over
     * the cart — no slot filters, the shared cursor walk). The validity gate
     * is the reference's own isValid: a gone cart or a player beyond 8
     * blocks (squaredDistanceTo > 64.0) refuses the click. Tick-thread
     * context.
     */
    private boolean clickChestMinecartWindowOnTick(PlayerSession session, int wireSlot,
                                                   int button, int mode) {
        var inventory = session.inventory();
        var cart = session.containerMountId() >= 0 && vehicleManager != null
                ? vehicleManager.byId(session.containerMountId()) : null;
        if (!(cart instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity chestCart)) {
            return false; // stale window (cart gone): rejected, resync restores
        }
        // The reference's isValid reach gate (InventoryMinecartEntity lines
        // 83-85): removed carts already failed the lookup above.
        double dx = cart.position().x() - session.position().x();
        double dy = cart.position().y() - session.position().y();
        double dz = cart.position().z() - session.position().z();
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            return false; // the cart rolled away: the click refuses
        }
        var chest = chestCart.chest();
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = chest.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // cart full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the
                // cart's slots are rejected per packet (the resync restores
                // truth).
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = chest.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * The hopper minecart's window open (Slice 66 — the reference's
     * HopperMinecartEntity.interact lines 54-60 + getMenuType
     * "minecraft:hopper"): the hopper GUI over the cart's own five
     * slots. Tick-thread context.
     */
    private void openHopperMinecartInventoryOnTick(
            PlayerSession session,
            net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity cart) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openHopperMinecartWindow(windowId, cart.entityId());
        for (VehicleListener listener : vehicleListeners) {
            listener.onHopperMinecartWindowOpened(session, cart, windowId);
        }
    }

    /**
     * The hopper minecart window's clicks (Slice 66): the same hopper-menu
     * semantics over the cart's own five slots (the reference's HopperMenu
     * over the cart — no slot filters, the shared cursor walk). The
     * validity gate is the reference's own isValid: a gone cart or a
     * player beyond 8 blocks refuses the click. Tick-thread context.
     */
    private boolean clickHopperMinecartWindowOnTick(PlayerSession session, int wireSlot,
                                                    int button, int mode) {
        var inventory = session.inventory();
        var cart = session.containerMountId() >= 0 && vehicleManager != null
                ? vehicleManager.byId(session.containerMountId()) : null;
        if (!(cart instanceof net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity hopperCart)) {
            return false; // stale window (cart gone): rejected, resync restores
        }
        // The reference's isValid reach gate (InventoryMinecartEntity lines
        // 83-85): removed carts already failed the lookup above.
        double dx = cart.position().x() - session.position().x();
        double dy = cart.position().y() - session.position().y();
        double dz = cart.position().z() - session.position().z();
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            return false; // the cart rolled away: the click refuses
        }
        var hopper = hopperCart.hopper();
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    hopper.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    hopper.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: the hopper has no slot filters
                    // (the reference's plain InventorySlots). Remainder
                    // returns.
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = hopper.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // cart full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; the
                // cart's slots are rejected per packet (the resync restores).
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= HOPPER_WIRE_SLOT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = hopper.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = hopperEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * The horse window's clicks: slot 0 is the saddle, slot 1 the armor row
     * (one-item kind-gated swaps, the vanilla rule); a chested mount rides
     * the 15-slot chest grid on 2-16 (the shared cursor semantics); the
     * player slots route through the shared inventory click semantics.
     * Tick-thread context.
     */
    private boolean clickHorseWindowOnTick(PlayerSession session, int wireSlot, int button, int mode) {
        var inventory = session.inventory();
        MobEntity mount = mobManager.byId(session.containerMountId());
        if (mount == null) {
            return false; // stale window (the mount died): rejected, resync restores
        }
        if (mode == 0 && wireSlot == 0) { // the saddle slot
            return clickHorseSaddleSlot(session, mount, button);
        }
        if (mode == 0 && wireSlot == 1) { // the armor slot
            return clickHorseArmorSlot(session, mount, button);
        }
        if (mode == 1 && (wireSlot == 0 || wireSlot == 1)) {
            return false; // shift-click out of a gated slot: refused (the resync restores)
        }
        // The chest grid (HorseMenu: inventory rows 2-16 on a chested
        // donkey/mule — the 3x5 grid plays the shared click semantics).
        if (wireSlot >= 2 && wireSlot <= 1 + MobEntity.CHEST_SLOT_COUNT) {
            if (!mount.chested() || mode != 0) {
                return false; // a bare donkey has no grid; shift-click refused
            }
            var grid = mount.chestGrid();
            if (grid == null) {
                return false;
            }
            WindowClicks.click(grid, wireSlot - 2, button, inventory.cursorBox());
            return true;
        }
        // The player inventory tail.
        int engineSlot = mount.chested()
                ? chestedHorsePlayerSlotOf(wireSlot) : horsePlayerSlotOf(wireSlot);
        if (engineSlot >= 0) {
            if (mode == 0) {
                inventory.clickSlot(engineSlot, button);
                return true;
            }
        }
        return false;
    }

    /** The saddle slot: cursor-saddle in, saddle-out to the cursor. */
    private boolean clickHorseSaddleSlot(PlayerSession session, MobEntity mount, int button) {
        var inventory = session.inventory();
        ItemStack cursor = inventory.cursor();
        boolean on = mount.saddled();
        String cursorId = cursor.type() == null ? "" : cursor.type().identifier().toString();
        if (!on && cursorId.equals("minecraft:saddle")) {
            inventory.takeCursor(); // the saddle leaves the cursor onto the mount
            mount.applySaddle();
            publishHorseFlags(mount);
            return true;
        }
        if (on && cursor.isEmpty()) {
            // The saddle jumps onto the cursor (the vanilla GUI take).
            inventory.placeOnCursor(ItemStack.of(BuiltinItems.SADDLE, 1));
            publishHorseFlags(mount);
            return true;
        }
        return false;
    }

    /** The armor slot: cursor-armor in (kind-gated), armor-out to the cursor. */
    private boolean clickHorseArmorSlot(PlayerSession session, MobEntity mount, int button) {
        var inventory = session.inventory();
        ItemStack cursor = inventory.cursor();
        int row = mount.armorType();
        String cursorId = cursor.type() == null ? "" : cursor.type().identifier().toString();
        int cursorRow = horseArmorRowOf(cursorId);
        if (row == MobEntity.HORSE_ARMOR_NONE && cursorRow > 0) {
            inventory.takeCursor(); // the armor leaves the cursor onto the mount
            mount.applyArmor(cursorRow);
            publishHorseFlags(mount);
            return true;
        }
        if (row != MobEntity.HORSE_ARMOR_NONE && cursor.isEmpty()) {
            inventory.placeOnCursor(ItemStack.of(horseArmorItemOf(row), 1));
            mount.applyArmor(MobEntity.HORSE_ARMOR_NONE);
            publishHorseFlags(mount);
            return true;
        }
        return false;
    }

    /** @return the armor row of a horse-armor item id, or 0. */
    private static int horseArmorRowOf(String itemId) {
        if (itemId.equals("minecraft:diamond_horse_armor")) {
            return MobEntity.HORSE_ARMOR_DIAMOND;
        }
        if (itemId.equals("minecraft:golden_horse_armor")) {
            return MobEntity.HORSE_ARMOR_GOLD;
        }
        if (itemId.equals("minecraft:iron_horse_armor")) {
            return MobEntity.HORSE_ARMOR_IRON;
        }
        return MobEntity.HORSE_ARMOR_NONE;
    }

    /** @return the item type of an armor row (the undo path). */
    private static net.zaminmc.torch.item.ItemType horseArmorItemOf(int row) {
        return switch (row) {
            case MobEntity.HORSE_ARMOR_DIAMOND -> BuiltinItems.DIAMOND_HORSE_ARMOR;
            case MobEntity.HORSE_ARMOR_GOLD -> BuiltinItems.GOLDEN_HORSE_ARMOR;
            default -> BuiltinItems.IRON_HORSE_ARMOR;
        };
    }

    /**
     * @return the engine inventory slot of the horse window's player tail
     * (main 2-28 map to 9-35, hotbar 29-37 map to 0-8), or -1.
     */
    private static int horsePlayerSlotOf(int wireSlot) {
        if (wireSlot >= 2 && wireSlot <= 28) {
            return wireSlot - 2 + 9;
        }
        if (wireSlot >= 29 && wireSlot <= 37) {
            return wireSlot - 29;
        }
        return -1;
    }

    /**
     * @return the engine inventory slot of the chested horse window's player
     * tail (the chest grid shifts it by the 15 grid slots: main 17-43 map to
     * 9-35, hotbar 44-52 map to 0-8), or -1.
     */
    private static int chestedHorsePlayerSlotOf(int wireSlot) {
        if (wireSlot >= 2 + MobEntity.CHEST_SLOT_COUNT
                && wireSlot <= 28 + MobEntity.CHEST_SLOT_COUNT) {
            return wireSlot - 2 - MobEntity.CHEST_SLOT_COUNT + 9;
        }
        if (wireSlot >= 29 + MobEntity.CHEST_SLOT_COUNT
                && wireSlot <= 37 + MobEntity.CHEST_SLOT_COUNT) {
            return wireSlot - 29 - MobEntity.CHEST_SLOT_COUNT;
        }
        return -1;
    }

    /**
     * Opens the crafting-table container: assigns the wire window id, returns
     * any carried window state (the historical container change closes the
     * previous window; the one cursor carries over — one mouse), and reports
     * the id so the adapter can send Open Window followed by the authoritative
     * window contents. Tick-thread context.
     */
    private void openCraftingTableOnTick(PlayerSession session,
                                         java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        // Opening a container closes the player inventory window (historical):
        // the 2x2 grid returns to the inventory, nothing is lost.
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The workbench stat (CraftingTableBlock.use's own incrementStat).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.CRAFTING_TABLE_INTERACTIONS, 1);

        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.CRAFTING_TABLE, null);
        // The adapter must send Open Window before any slot data: the client
        // ignores Window Items for a window id it does not know yet.
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the enchanting table container at the clicked block: assigns the
     * wire window id, creates the per-open menu (seed read at open, the exact
     * reference ctor), closes any carried window first, and reports the id for
     * the adapter's Open Window + slot/property sync. The menu's two slots are
     * transient — closing drops both to the world (the reference close).
     * Tick-thread context.
     */
    private void openEnchantingTableOnTick(PlayerSession session,
                                           net.zaminmc.torch.block.BlockPosition position,
                                           java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops

        var menu = new net.zaminmc.torch.server.enchantment.EnchantingMenu(
                session.enchantingSeed(), position);
        menu.attach(world::getBlock); // the scan reads the real neighborhood from here on
        menu.onContentsChanged(null); // the open-time recompute (the reference markDirty on load)
        session.setEnchantingMenu(menu);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.ENCHANTING_TABLE, position);
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the furnace container at the clicked block: assigns the wire
     * window id, lazily creates the block-entity state, closes any carried
     * window state first, and reports the id for the adapter's Open Window +
     * slot sync. The furnace's slots live in the world — closing the window
     * later leaves them inside (the historical container behavior).
     * Tick-thread context.
     */
    private void openFurnaceOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
                                   java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The container's own interaction stat (the reference's use rows).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.FURNACE_INTERACTIONS, 1);

        FurnaceBlockEntity furnace = furnaceManager.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.FURNACE, position);
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the chest container at the clicked block: assigns the wire window
     * id, lazily creates the block-entity state, closes any carried window
     * state first, and reports the id for the adapter's Open Window + slot
     * sync. The chest's 27 slots live in the world — closing the window later
     * leaves them inside (the historical container behavior).
     * Tick-thread context.
     */
    private void openChestOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
                                 java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops

        chestManager.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.CHEST, position);
        onTableOpened.accept(windowId);
        // The lid swings for every other observer (the historical Block
        // Action 1; the opener's client animates its own view).
        publishChestLid(position, session, true);
        // Slice 49b: the trapped chest's viewer count changed — the
        // neighbors re-evaluate (ChestBlockEntity's own onOpen notify).
        if (world.getBlock(position).identifier()
                .equals(net.zaminmc.torch.server.block.BuiltinBlocks.TRAPPED_CHEST.identifier())) {
            redstoneSystem.updateNeighbors(position);
            // Stats.java's own pair: the trapped chest counts its own row
            // (ChestBlock(1).use's incrementStat), never CHESTS_OPENED.
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.TRAPPED_CHESTS_TRIGGERED, 1);
        } else {
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.CHESTS_OPENED, 1);
        }
        // §27.7: the post-commit chest-open observation — the window is
        // assigned and the lid swings; handlers observe, they do not veto.
        eventBus.fire(new net.zaminmc.torch.event.block.ChestOpenEvent(
                world.name(), position, session.uuid(), session.name()));
    }

    /**
     * Opens the player's own ender chest through the clicked block (Slice
     * 29a — EnderChestBlock.use lines 73-92): the solid-above gate (line 80
     * — a solid block on top refuses the open), then the session's private
     * 27-slot grid opens as the chest GUI. The block itself carries no
     * state — the position records only which door opened (the close's lid
     * swing and the stale-window gate). Tick-thread context.
     */
    private void openEnderChestOnTick(PlayerSession session,
                                      net.zaminmc.torch.block.BlockPosition position,
                                      java.util.function.IntConsumer onTableOpened) {
        // Line 80: the open gate — a solid block above refuses.
        if (WorldSolidity.isSolid(world.getBlock(position.offset(0, 1, 0)))) {
            return; // the use consumed, nothing opens
        }
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // EnderChestBlock.use's own incrementStat (Stats.ENDER_CHESTS_OPENED).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.ENDER_CHESTS_OPENED, 1);

        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.ENDER_CHEST, position);
        onTableOpened.accept(windowId);
        // The lid swings for every other observer (the BE's own onOpen
        // broadcast — the Block Action 1 over legacy 130).
        publishChestLid(position, session, true);
    }

    /**
     * Opens the beacon GUI at the clicked block (Slice 29b —
     * BeaconBlock.use lines 34-46): the window assigns over the block
     * entity's own payment slot; the three properties (levels/primary/
     * secondary) ride the open's window sync. Tick-thread context.
     */
    private void openBeaconOnTick(PlayerSession session,
                                  net.zaminmc.torch.block.BlockPosition position,
                                  java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The container's own interaction stat (the reference's use rows).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.BEACON_INTERACTIONS, 1);

        beaconSystem.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.BEACON, position);
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the hopper container at the clicked block (the reference's
     * HopperBlock.use lines 135-146 + HopperMenu): assigns the wire window
     * id, lazily creates the block-entity state, closes any carried window
     * state first, and reports the id for the adapter's Open Window + slot
     * sync. The five slots live in the world — closing the window later
     * leaves them inside (the historical container behavior, the same
     * ownership as the chest). Tick-thread context.
     */
    private void openHopperOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
                                  java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The container's own interaction stat (the reference's use rows).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.HOPPERS_INSPECTED, 1);

        hopperSystem.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.HOPPER, position);
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the dispenser/dropper container at the clicked block (Slice 11a —
     * DispenserBlock.use lines 78-94): assigns the wire window id, lazily
     * creates the block-entity state, closes any carried window state first,
     * and reports the id for the adapter's Open Window + slot sync. The nine
     * slots live in the world — closing the window later leaves them inside
     * (the historical container behavior, the same ownership as the chest).
     * Tick-thread context.
     */
    private void openDispenserOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
                                     java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The container's own interaction stat (the reference's use rows).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.DISPENSERS_INSPECTED, 1);

        // The dropper's own row (the reference's separate stat): the family
        // shares the container but not the counter.
        if (world.getBlock(position).identifier()
                .equals(net.zaminmc.torch.util.Identifier.parse("minecraft:dropper"))) {
            session.stats().increment(
                    net.zaminmc.torch.server.stat.EngineStats.DROPPERS_INSPECTED, 1);
        }
        dispenserSystem.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.DISPENSER, position);
        onTableOpened.accept(windowId);
    }

    /**
     * The brewing stand's open (Slice 11e — BrewingStandBlock.use lines
     * 77-89): the state materializes on first use, the window opens with
     * the world-resident slots. Tick-thread context.
     */
    private void openBrewingStandOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
                                        java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        dropGridToWorld(session, session.crafting()); // the vanilla close: the 2x2 grid drops
        // The container's own interaction stat (the reference's use rows).
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.BREWING_STAND_INTERACTIONS, 1);

        brewingStands.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.BREWING_STAND, position);
        onTableOpened.accept(windowId);
    }

    /**
     * A stale open container (re-open without a close) releases what it
     * carries: the crafting table's 3x3 grid returns to the inventory; a
     * furnace keeps its slots in the world. Tick-thread context.
     */
    private void closeOpenContainerOnTick(PlayerSession session) {
        if (session.openContainerWindowId() < 0) {
            return;
        }
        if (session.openContainerKind() == PlayerSession.ContainerKind.CRAFTING_TABLE) {
            dropGridToWorld(session, session.tableCrafting()); // the vanilla close: the grid drops
            session.closeContainerWindow();
        } else if (session.openContainerKind() == PlayerSession.ContainerKind.ENCHANTING_TABLE) {
            closeEnchantingMenuOnTick(session); // the menu's two slots drop (the reference close)
        } else {
            session.closeContainerWindow();
        }
    }

    /** Throws each overflow stack into the world at the player (nothing is lost). */
    private void throwOverflow(PlayerSession session, java.util.List<net.zaminmc.torch.item.ItemStack> overflow) {
        for (net.zaminmc.torch.item.ItemStack stack : overflow) {
            throwFromPlayer(session, stack);
        }
    }

    /**
     * The client closed a window: carried window state returns to the
     * inventory; remainders are thrown into the world so nothing is lost.
     * Safe from any thread.
     */
    @Override
    public void closeWindow(PlayerSession session, int windowId) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (windowId == WIRE_WINDOW_PLAYER) {
                returnWindowCarriedItems(session, false);
            } else if (windowId == session.openContainerWindowId()) {
                if (session.openContainerKind() == PlayerSession.ContainerKind.CHEST
                        && session.openContainerPosition() != null) {
                    publishChestLid(session.openContainerPosition(), session, false);
                }
                if (session.openContainerKind() == PlayerSession.ContainerKind.ENDER_CHEST
                        && session.openContainerPosition() != null) {
                    // The ender chest's own onClose broadcast (the Block
                    // Action 1 over legacy 130 — the lid closes for the
                    // other observers).
                    publishChestLid(session.openContainerPosition(), session, false);
                }
                if (session.openContainerKind() == PlayerSession.ContainerKind.BEACON
                        && session.openContainerPosition() != null) {
                    // BeaconMenu.close lines 46-54: the payment drops into
                    // the WORLD at the player (never back into the slot).
                    var beacon = beaconSystem.peek(session.openContainerPosition());
                    if (beacon != null && !beacon.payment().isEmpty()) {
                        throwFromPlayer(session, beacon.payment());
                        beacon.setPayment(net.zaminmc.torch.item.ItemStack.EMPTY);
                    }
                }
                returnWindowCarriedItems(session, true);
                session.closeContainerWindow();
            }
            publishInventoryChanged(session);
        });
    }

    /**
     * Cursor + crafting grids release per the vanilla close rule
     * (ContainerPlayer/ContainerWorkbench onMenuClose): the cursor and every
     * grid stack DROP to the world at the player — they do not return to the
     * inventory. With {@code containerToo} the open container releases its
     * carried state as well, while a furnace's three slots stay inside the
     * furnace (historical container behavior) and persist with the world.
     * Tick-thread context.
     */
    private void returnWindowCarriedItems(PlayerSession session, boolean containerToo) {
        net.zaminmc.torch.item.ItemStack leftover = session.inventory().returnCursor();
        if (!leftover.isEmpty()) {
            throwFromPlayer(session, leftover);
        }
        dropGridToWorld(session, session.crafting());
        if (containerToo && session.openContainerWindowId() >= 0) {
            if (session.openContainerKind() == PlayerSession.ContainerKind.CRAFTING_TABLE) {
                dropGridToWorld(session, session.tableCrafting());
                session.closeContainerWindow();
            } else if (session.openContainerKind() == PlayerSession.ContainerKind.ENCHANTING_TABLE) {
                closeEnchantingMenuOnTick(session); // the menu's two slots drop; the window state clears
            } else {
                session.closeContainerWindow();
            }
        }
    }

    /**
     * The Enchant Item walk (the reference {@code onButtonClick} on the
     * simulation context): the menu's gate ladder, the level payment, the
     * lapis consumption, and the recompute ordering; a landed enchant also
     * re-syncs the XP bar (the level cost) and the slots. Tick-thread context.
     */
    @Override
    public void enchantItem(PlayerSession session, int windowId, int button,
                            java.util.function.Consumer<Boolean> result) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(result, "result");
        ticker.submit(() -> {
            boolean accepted = false;
            var menu = session.enchantingMenu();
            if (menu != null && windowId == session.openContainerWindowId()) {
                net.zaminmc.torch.server.enchantment.EnchantingMenu.Enchanter enchanter =
                        new net.zaminmc.torch.server.enchantment.EnchantingMenu.Enchanter() {
                            @Override
                            public int xpLevel() {
                                return session.experienceLevel();
                            }

                            @Override
                            public boolean creative() {
                                return session.gamemode() == net.zaminmc.torch.GameMode.CREATIVE;
                            }

                            @Override
                            public void applyEnchantmentCosts(int cost) {
                                session.applyEnchantmentCosts(cost);
                            }

                            @Override
                            public int enchantingSeed() {
                                return session.enchantingSeed();
                            }
                        };
                accepted = menu.enchantButton(enchanter, button, world::getBlock);
                if (accepted) {
                    // The enchant stat (ContainerEnchant.onButtonClick's own
                    // incrementStat — one row per enchanted item).
                    session.stats().increment(
                            net.zaminmc.torch.server.stat.EngineStats.ITEMS_ENCHANTED, 1);
                    publishExperienceChanged(session);
                }
            }
            try {
                result.accept(accepted);
            } catch (RuntimeException brokenCallback) {
                LOGGER.log(java.util.logging.Level.WARNING,
                        "Enchant callback failed for " + session.name(), brokenCallback);
            }
            publishInventoryChanged(session);
        });
    }

    /** The vanilla grid close: every stack drops to the world at the player. */
    private void dropGridToWorld(PlayerSession session, net.zaminmc.torch.server.player.CraftingGrid grid) {
        for (net.zaminmc.torch.item.ItemStack stack : grid.dropAll()) {
            throwFromPlayer(session, stack);
        }
    }

    /** Fans item-entity events out to registered observers (tick-thread context). */
    private final class ItemEventDispatch implements ItemEntityManager.Listener {
        @Override
        public void onItemSpawned(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemSpawned(entity);
            }
        }

        @Override
        public void onItemMoved(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemMoved(entity);
            }
        }

        @Override
        public void onItemCollected(ItemEntity entity, PlayerSession collector, int collectedCount) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemCollected(entity, collector, collectedCount);
            }
            publishInventoryChanged(collector); // pickup changed the inventory (§433 sync)
        }

        @Override
        public void onItemStackChanged(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemStackChanged(entity);
            }
        }

        @Override
        public void onItemRemoved(ItemEntity entity, String reason) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemRemoved(entity, reason);
            }
        }
    }

    /**
     * Fan-out from the simulation-owned falling-block system to the observer
     * list — the same pattern the item and mob systems use (§448): the engine
     * decides what happened, the listeners decide how it reaches clients. The
     * landing's block change itself rides the world listeners.
     */
    private final class FallingEventDispatch implements FallingBlockEntityManager.Listener {
        @Override
        public void onFallingSpawned(FallingBlockEntity entity) {
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingSpawned(entity);
            }
        }

        @Override
        public void onFallingMoved(FallingBlockEntity entity) {
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingMoved(entity);
            }
        }

        @Override
        public void onFallingEnded(FallingBlockEntity entity, boolean becameBlock) {
            // The anvil's land sound (Slice 53 — the reference's
            // onTickFallingBlockEntity: world event 1022 at the landing
            // cell, fired only on the successful placement — the client
            // answers random.anvil_land).
            if (becameBlock && net.zaminmc.torch.server.block.AnvilBlocks
                    .isAnvil(entity.blockType().identifier())) {
                fxManager.sound(new Position(
                        entity.position().x(), entity.position().y(), entity.position().z()),
                        "random.anvil_land", 1.0f, 1.0f);
            }
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingEnded(entity, becameBlock);
            }
        }
    }

    /** Observers of the projectile system (the protocol adapter's wire sync). */
    public interface ProjectileListener {
        void onProjectileSpawned(ProjectileEntity projectile);

        void onProjectileMoved(ProjectileEntity projectile);

        void onProjectileLanded(ProjectileEntity projectile);

        void onProjectileRemoved(ProjectileEntity projectile, String reason);

        /**
         * A player's punch deflected a fireball (Slice 25b — the velocity
         * and the shooter both flip; the wire re-publishes the velocity).
         */
        default void onProjectileDeflected(ProjectileEntity projectile) {
        }

        /**
         * The reference's own world event from a projectile's impact
         * (Slice 65 — the bottle o' enchanting's doEvent(2002, pos, 0):
         * the splash-particle Effect packet, the color value 0).
         */
        default void onWorldEventAt(Position position, int eventId) {
        }
    }

    private final java.util.List<ProjectileListener> projectileListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an internal projectile observer (the protocol adapter). */
    public void addProjectileListener(ProjectileListener listener) {
        projectileListeners.add(listener);
    }

    /** Fan-out from the projectile system to the observer list (§448 pattern). */
    private final class ProjectileEventDispatch implements ProjectileManager.Listener {
        @Override
        public void onProjectileSpawned(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileSpawned(projectile);
            }
        }

        @Override
        public void onProjectileMoved(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileMoved(projectile);
            }
        }

        @Override
        public void onProjectileLanded(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileLanded(projectile);
            }
        }

        @Override
        public void onProjectileRemoved(ProjectileEntity projectile, String reason) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileRemoved(projectile, reason);
            }
        }

        @Override
        public void onProjectileDeflected(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileDeflected(projectile);
            }
        }
    }
    /** Observers of the vehicle system (the protocol adapter's wire sync). */
    public interface VehicleListener {
        void onVehicleSpawned(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle);

        void onVehicleMoved(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                            PlayerSession rider);

        void onVehicleHurt(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle);

        void onVehicleMounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                              PlayerSession rider);

        void onVehicleDismounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                 PlayerSession rider);

        void onVehicleBroken(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle);

        /**
         * The chest minecart's chest menu opened (Slice 21a): the adapter
         * unicasts Open Window + the window items to the opener.
         */
        default void onChestMinecartWindowOpened(PlayerSession opener,
                net.zaminmc.torch.server.entity.vehicle.VehicleEntity cart,
                int windowId) {
        }

        /**
         * The hopper minecart's hopper menu opened (Slice 66 —
         * HopperMinecartEntity.interact lines 54-60: openChestMenu over
         * the cart's five slots, never the seat): the adapter unicasts
         * Open Window + the window items to the opener.
         */
        default void onHopperMinecartWindowOpened(PlayerSession opener,
                net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity cart,
                int windowId) {
        }

        /**
         * The TNT cart's fuse armed (Slice 66 — the prime's own wire: the
         * Entity Status 10 + the game.tnt.primed sound).
         */
        default void onVehiclePrimed(
                net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity cart) {
        }

        /**
         * A vehicle's tracked state flipped (Slice 66 — the furnace cart's
         * lit byte at the synced slot 16).
         */
        default void onVehicleStateChanged(
                net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        }

        /**
         * The spawner cart's cycle fired a spawn (Slice 66 — MobSpawner
         * line 108's doEvent(2004, blockpos, 0) at the cart's cell: the
         * flame burst the client draws).
         */
        default void onSpawnerCartFlames(
                net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart,
                double x, double y, double z) {
        }

        /**
         * The spawner cart's delay re-rolled (Slice 66 — delay line 187's
         * broacastEvent(1) over the cart's doEntityEvent: the Entity
         * Status 1 spin reset).
         */
        default void onSpawnerCartSpinReset(
                net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart) {
        }
    }

    private final java.util.List<VehicleListener> vehicleListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an internal vehicle observer (the protocol adapter). */
    public void addVehicleListener(VehicleListener listener) {
        vehicleListeners.add(listener);
    }

    /** Registers a fishing bobber observer (the protocol adapter). */
    public void addFishingListener(net.zaminmc.torch.server.fishing.FishingSystem.Listener listener) {
        if (fishing != null) {
            fishing.addListener(listener);
        }
    }

    /** @return the vehicle system (diagnostics and tests). */
    /** @return the fishing system (diagnostics and tests). */
    public net.zaminmc.torch.server.fishing.FishingSystem fishing() {
        return fishing;
    }

    public net.zaminmc.torch.server.entity.vehicle.VehicleManager vehicles() {
        return vehicleManager;
    }

    /** Fan-out from the vehicle system to the observer list (§448 pattern). */
    private final class VehicleEventDispatch
            implements net.zaminmc.torch.server.entity.vehicle.VehicleManager.Listener {
        @Override
        public void onVehicleSpawned(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleSpawned(vehicle);
            }
        }

        @Override
        public void onVehicleMoved(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
            // The listener signature carries the vehicle only; the engine
            // resolves the rider session (null for mobs/unloaded).
            PlayerSession rider = vehicle.hasPassenger()
                    ? sessionByEngineId(vehicle.passengerId()) : null;
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleMoved(vehicle, rider);
            }
        }

        @Override
        public void onVehicleHurt(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleHurt(vehicle);
            }
        }

        @Override
        public void onVehicleMounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                     int riderEngineId) {
            PlayerSession rider = sessionByEngineId(riderEngineId);
            if (rider != null) {
                rider.setRidingVehicleId(vehicle.entityId());
            }
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleMounted(vehicle, rider);
            }
        }

        @Override
        public void onVehicleDismounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                        int riderEngineId, Position riderExit) {
            PlayerSession rider = sessionByEngineId(riderEngineId);
            if (rider != null) {
                rider.setRidingVehicleId(-1);
                // The body steps out beside the vehicle (the re-anchor rides
                // the wire listener's position-and-look).
                rider.applyMovement(riderExit, rider.rotation(), true);
                rider.setGraceTicks(40);
            }
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleDismounted(vehicle, rider);
            }
        }

        @Override
        public void onVehicleBroken(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleBroken(vehicle);
            }
        }

        /** The TNT cart's prime (Slice 66 — the sound + the status 10). */
        @Override
        public void onVehiclePrimed(
                net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity cart) {
            // prime lines 148-152: the game.tnt.primed sound at volume 1.0
            // (the world's every listener).
            fxManager.sound(cart.position(), "game.tnt.primed", 1.0f, 1.0f);
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehiclePrimed(cart);
            }
        }

        /** The tracked-state flip (Slice 66 — the furnace's lit byte). */
        @Override
        public void onVehicleStateChanged(
                net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onVehicleStateChanged(vehicle);
            }
        }

        /** The spawner cart's per-spawn flame burst (Slice 66). */
        @Override
        public void onSpawnerCartFlames(
                net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart,
                double x, double y, double z) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onSpawnerCartFlames(cart, x, y, z);
            }
        }

        /** The spawner cart's delay reroll (Slice 66 — the Entity Status 1). */
        @Override
        public void onSpawnerCartSpinReset(
                net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart) {
            for (VehicleListener listener : vehicleListeners) {
                listener.onSpawnerCartSpinReset(cart);
            }
        }
    }

    /** @return the session whose engine id matches, or null. */
    private PlayerSession sessionByEngineId(int engineId) {
        for (PlayerSession player : players.all()) {
            if (player.engineEntityId() == engineId) {
                return player;
            }
        }
        return null;
    }

    /** @return the playing session whose engine id matches, or null (the adapter's mount lookups). */
    public PlayerSession playerByEngineId(int engineId) {
        return sessionByEngineId(engineId);
    }

    /** The break drop of a vehicle (the vanilla item forms). */
    private net.zaminmc.torch.item.ItemStack vehicleDropStack(
            net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        return net.zaminmc.torch.item.ItemStack.of(
                vehicle.kind() == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.BOAT
                        ? BuiltinItems.BOAT
                        : BuiltinItems.MINECART,
                1);
    }

    /**
     * The mounted rider's body rides the seat: the boat's heading follows
     * the rider's look (the look-steer), and the session position tracks
     * the vehicle (the wire listener re-anchors the client).
     */
    private void tickVehicleRiders(net.zaminmc.torch.server.entity.vehicle.VehicleManager vehicles) {
        for (net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle : vehicles.all()) {
            if (!vehicle.hasPassenger()) {
                continue;
            }
            PlayerSession rider = sessionByEngineId(vehicle.passengerId());
            if (rider == null || rider.dead()) {
                continue;
            }
            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.BoatEntity boat) {
                boat.followRiderLook(rider.rotation());
            }
            rider.applyMovement(vehicle.position(), rider.rotation(), vehicle.onGround());
        }
    }

    /**
     * The fishing walk (Slice 67): the held-rod gate consult (the angler
     * gone, the rod un-held, or the leash broken ends the cast) then the
     * bobbers' own tick (the flight, the float, the catchable cycle).
     * Tick-thread context.
     */
    private void tickFishing() {
        if (fishing == null) {
            return;
        }
        for (PlayerSession angler : players.all()) {
            boolean heldRod = !angler.inventory().held().isEmpty()
                    && angler.inventory().held().type().equals(BuiltinItems.FISHING_ROD);
            fishing.noteAnglerState(angler.engineEntityId(),
                    angler.state() == PlayerState.PLAYING && !angler.dead(),
                    heldRod, angler.position());
        }
        fishing.tick();
    }

    /**
     * The mob-mount riders: the body rides the seat (the mount's position
     * plus the saddle height — the camera sits on the mount's back, the
     * vanilla seat anchor). The wire listener re-anchors each client.
     */
    private void tickMobRiders() {
        for (MobEntity mob : mobManager.all()) {
            if (!mob.hasRider()) {
                continue;
            }
            PlayerSession rider = sessionByEngineId(mob.riderId());
            if (rider == null || rider.dead()) {
                continue;
            }
            Position seat = new Position(mob.position().x(),
                    mob.position().y() + mob.seatHeight(), mob.position().z());
            rider.applyMovement(seat, rider.rotation(), mob.onGround());
        }
    }

    /** The vehicle world query: the shape-aware solids plus rails. */
    private final class VehicleWorldQuery
            implements net.zaminmc.torch.server.entity.vehicle.VehicleEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return solidAt(x, y, z);
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return solidAtPoint(x, y, z);
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return fluidAt(x, y, z);
        }

        @Override
        public int railAxisAt(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return -1;
            }
            Identifier id = world.getBlock(blockAt(x, y, z)).identifier();
            if (id.equals(BuiltinBlocks.RAIL.identifier())) {
                return 0; // the north-south rail: travel along Z
            }
            if (id.equals(BuiltinBlocks.RAIL_EW.identifier())) {
                return 1; // the east-west rail: travel along X
            }
            // The Slice 38 trio: the shape names the travel axis (the six
            // straight shapes — north_south Z, east_west X, the east/west
            // ascents X, the north/south ascents Z).
            String value = id.value();
            if (id.namespace().equals("minecraft")
                    && (value.startsWith("golden_rail") || value.startsWith("detector_rail")
                    || value.startsWith("activator_rail"))) {
                int shape = net.zaminmc.torch.server.block.RailBlocks.shapeOf(
                        world.getBlock(blockAt(x, y, z)));
                return net.zaminmc.torch.server.block.RailBlocks.axisIsX(shape) ? 1 : 0;
            }
            return -1;
        }

        @Override
        public int railPowerAt(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return 0;
            }
            BlockType rail = world.getBlock(blockAt(x, y, z));
            String family = net.zaminmc.torch.server.block.RailBlocks.familyOf(rail);
            if (family == null || family.equals("detector_rail")) {
                return 0; // the detector family emits, it never boosts
            }
            return net.zaminmc.torch.server.block.RailBlocks.isPowered(rail) ? 1 : -1;
        }
    }

    /**
     * Resolves what a projectile is inside of: the same boxes the melee
     * checks use (mob type width/height; the 0.6 × 1.8 player box). Tick-thread.
     */
    /** The player body at a point (the projectile hull's own 0.3x1.8 band). */
    private PlayerSession playerBodyAt(Position point) {
        for (PlayerSession player : players.all()) {
            if (player.state() != PlayerState.PLAYING || player.dead()) {
                continue;
            }
            Position p = player.position();
            double dx = point.x() - p.x();
            double dy = point.y() - p.y();
            double dz = point.z() - p.z();
            if (Math.abs(dx) <= 0.3 && Math.abs(dz) <= 0.3 && dy >= -0.2 && dy <= 1.8) {
                return player;
            }
        }
        return null;
    }

    /** The angler's held-rod enchant level (the fishing tables' own reads). */
    private int heldRodLevel(int throwerId, int enchantId) {
        PlayerSession angler = sessionByEngineId(throwerId);
        if (angler == null) {
            return 0;
        }
        ItemStack held = angler.inventory().held();
        if (held.isEmpty() || !held.type().equals(BuiltinItems.FISHING_ROD)) {
            return 0;
        }
        return EnchantmentHelper.level(held, enchantId);
    }

    /**
     * The fishing rod's use (Slice 67 — FishingRodItem.startUsing lines
     * 16-31): a live bobber retrieves (the wear = the result code); an
     * empty hand casts (the random.bow sound at 0.5 volume, the shaped
     * pitch). Tick-thread context.
     */
    private void useFishingRod(PlayerSession session) {
        if (fishing == null) {
            return;
        }
        var live = fishing.bobberOf(session.engineEntityId());
        if (live != null) {
            int result = fishing.retrieve(session.engineEntityId(), session.position());
            if (session.gamemode() != GameMode.CREATIVE && result > 0) {
                // takeDamageAndBreak(i): the wear = the result (1 fish,
                // 2 grounded, 3 hooked; a bare retrieve wears nothing).
                if (session.inventory().damageHeld(result, gameplayRandom)) {
                    publishInventoryChanged(session);
                }
            }
            return;
        }
        // The cast sound (line 25): 0.5 volume, the 0.4/(rand*0.4+0.8) pitch.
        fxManager.sound(mouthPosition(session), "random.bow", 0.5f,
                0.4f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        fishing.cast(session.engineEntityId(), mouthPosition(session),
                session.rotation().yaw(), session.rotation().pitch());
    }

    private final class ProjectileHitResolver implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point) {
            return mobManager.mobAt(point);
        }

        @Override
        public PlayerSession playerAt(Position point) {
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()) {
                    continue;
                }
                Position p = player.position();
                double dx = point.x() - p.x();
                double dy = point.y() - p.y();
                double dz = point.z() - p.z();
                if (Math.abs(dx) <= 0.3 && Math.abs(dz) <= 0.3 && dy >= -0.2 && dy <= 1.8) {
                    return player;
                }
            }
            return null;
        }
    }

    /**
     * The public §27.7 post-commit observation bridge (Slice 14a): every
     * committed block change in a booted world — whatever caused it —
     * fires {@link net.zaminmc.torch.event.block.BlockChangedEvent}
     * through the engine's event bus. Runs on the committing owner
     * context; the bus isolates handler exceptions.
     */
    private final class PublicBlockChangeBridge
            implements net.zaminmc.torch.server.world.WorldChangeListener {
        @Override
        public void onBlockChanged(EngineWorld world, BlockPosition position, BlockType newType) {
            // The combined pair carries the event (onBlockReplaced).
        }

        @Override
        public void onBlockReplaced(EngineWorld world, BlockPosition position,
                                    BlockType oldType, BlockType newType) {
            eventBus.fire(new net.zaminmc.torch.event.block.BlockChangedEvent(
                    world.name(), position,
                    oldType == null ? null : oldType.identifier().toString(),
                    newType.identifier().toString()));
        }
    }

    /**
     * Resolves a splash's thrower body from the engine id (the
     * {@code DamageSource.magic(potion, thrower)} attacker): a live mob,
     * a playing player, or null for dispensers and removed throwers.
     */
    private Object resolveSplashThrower(int throwerId) {
        if (throwerId < 0) {
            return null; // the dispenser arm (the no-thrower constructor)
        }
        net.zaminmc.torch.server.entity.MobEntity mob = mobManager.byId(throwerId);
        if (mob != null) {
            return mob;
        }
        for (PlayerSession player : players.all()) {
            if (player.engineEntityId() == throwerId
                    && player.state() == PlayerState.PLAYING) {
                return player;
            }
        }
        return null;
    }

    /**
     * The potion bodies (Slice 11b — the PotionSystem's application sink):
     * the engine's own model fanned through the narrow interface. The
     * players and the mobs stay the engine's; the splash scans them by
     * their bounding boxes, the magic damage bypasses the knockback, and
     * the effect-store keys ride the engine entity ids.
     */
    private final class PotionBodies implements net.zaminmc.torch.server.potion.PotionSystem.Bodies {
        @Override
        public java.util.List<Object> bodiesIn(double minX, double minY, double minZ,
                                               double maxX, double maxY, double maxZ) {
            java.util.List<Object> found = new java.util.ArrayList<>();
            for (MobEntity mob : mobManager.all()) {
                Position at = mob.position();
                if (at.x() >= minX - 0.6 && at.x() <= maxX + 0.6
                        && at.z() >= minZ - 0.6 && at.z() <= maxZ + 0.6
                        && at.y() >= minY && at.y() <= maxY + 1.8) {
                    found.add(mob);
                }
            }
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        || player.gamemode() == GameMode.SPECTATOR) {
                    continue;
                }
                Position at = player.position();
                if (at.x() >= minX - 0.3 && at.x() <= maxX + 0.3
                        && at.z() >= minZ - 0.3 && at.z() <= maxZ + 0.3
                        && at.y() >= minY - 0.2 && at.y() <= maxY + 1.8) {
                    found.add(player);
                }
            }
            return found;
        }

        @Override
        public double squaredDistanceTo(Object body, double x, double y, double z) {
            Position at = body instanceof PlayerSession player ? player.position()
                    : ((MobEntity) body).position();
            double dx = at.x() - x;
            double dy = at.y() - y;
            double dz = at.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void hurt(Object body, float amount, String cause, Object attacker,
                         boolean magic) {
            if (body instanceof PlayerSession player) {
                // The magic damage: no knockback, no protection shave (the
                // reference's DamageSource.MAGIC pierces armor).
                damageOnTick(player, amount, true, cause, 0.0f,
                        net.zaminmc.torch.event.DamageCause.MAGIC);
            } else {
                MobEntity mob = (MobEntity) body;
                if (mob.type() == net.zaminmc.torch.server.entity.MobType.WITCH) {
                    // WitchEntity.getDamageAfterEffectsAndEnchantments
                    // (lines 163-175): the witch's own splash never lands
                    // (attacker == self), and every magic source pays the
                    // 85% shave — poison and other witches' potions alike.
                    if (attacker == mob) {
                        amount = 0.0f;
                    }
                    if (magic) {
                        amount = (float) (amount * 0.15);
                    }
                }
                mobManager.hurtWithoutKnockback(mob, amount);
            }
        }

        @Override
        public void heal(Object body, float amount) {
            if (body instanceof PlayerSession player) {
                float healed = Math.min(net.zaminmc.torch.server.player.PlayerSession.MAX_HEALTH,
                        player.health() + amount);
                player.setBody(healed, player.food(), player.saturation());
                publishBodyChanged(player);
            } else {
                ((MobEntity) body).heal(amount);
            }
        }

        @Override
        public void exhaust(Object body, float amount) {
            if (body instanceof PlayerSession player) {
                player.addExhaustion(amount);
            }
        }

        @Override
        public void feed(Object body, int food, float saturation) {
            if (body instanceof PlayerSession player) {
                int newFood = Math.min(net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD,
                        player.food() + food);
                float newSaturation = Math.min(newFood, player.saturation() + saturation);
                player.setBody(player.health(), newFood, newSaturation);
                publishBodyChanged(player);
            }
        }

        @Override
        public float healthOf(Object body) {
            return body instanceof PlayerSession player ? player.health()
                    : ((MobEntity) body).health();
        }

        @Override
        public boolean fireResistant(Object body) {
            return potionSystem.hasEffect(body,
                    net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE);
        }

        @Override
        public boolean undead(Object body) {
            if (body instanceof PlayerSession) {
                return false; // the players are never undead (no totem/zombie arm)
            }
            // The reference's UNDEAD MobType set (the smite family —
            // getMobType's overrides): the zombie and its pigman child,
            // the skeleton pair, and the wither (Slice 59's audit: the
            // earlier consult missed the pigman and the boss).
            var type = ((MobEntity) body).type();
            return type == net.zaminmc.torch.server.entity.MobType.ZOMBIE
                    || type == net.zaminmc.torch.server.entity.MobType.ZOMBIE_PIGMAN
                    || type == net.zaminmc.torch.server.entity.MobType.SKELETON
                    || type == net.zaminmc.torch.server.entity.MobType.WITHER;
        }

        @Override
        public boolean refusesEffects(Object body) {
            // WitherEntity.addStatusEffect's empty override (Slice 59):
            // the boss takes no effect from any source.
            return body instanceof MobEntity mob
                    && mob.type() == net.zaminmc.torch.server.entity.MobType.WITHER;
        }

        @Override
        public boolean isPlayer(Object body) {
            return body instanceof PlayerSession;
        }

        @Override
        public int keyOf(Object body) {
            return body instanceof PlayerSession player ? player.engineEntityId()
                    : ((MobEntity) body).entityId();
        }
    }

    /** The mobs' potion effects advance with the mob tick (Slice 11b). */
    /**
     * The populate-time generation drain (Slice 12b — the reference's
     * structure addEntity + placeChestWithLoot): every loaded chunk's
     * pending records materialize exactly once. The generator wrote the
     * records while the chunk was detached; the publication through the
     * world's chunk map is the safe-publication barrier, and the drain's
     * take-and-clear on the empty-check fast path costs nothing on the
     * steady tick. Tick-thread context.
     */
    private void drainGenerationAttachments() {
        // The drained worlds with their REAL dimension ids (0 the overworld,
        // -1 the nether, 1 the End) — the spawner's own world stamp rides
        // the value (Slice 62's fix: the pre-62 loop stamped the nether 1,
        // the End's own id, sending every fortress spawner's cell gate and
        // tile replay at the overworld).
        EngineWorld[] drained = {this.world, netherWorld, endWorld};
        int[] dimensions = {0, -1, 1};
        for (int w = 0; w < drained.length; w++) {
            EngineWorld world = drained[w];
            if (world == null) {
                continue;
            }
            int worldDimension = dimensions[w];
            for (net.zaminmc.torch.server.world.EngineChunk chunk : world.loadedChunks()) {
                if (!chunk.pendingVillagers().isEmpty()) {
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingVillager pending
                            : chunk.pendingVillagers()) {
                        // The restart idempotence guard: the deterministic
                        // regeneration re-records the same spawn row; a
                        // villager already standing there (the mob store's
                        // restore) means a previous life already drained it.
                        if (mobManager.villagerNear(pending.x() + 0.5, pending.y(),
                                pending.z() + 0.5, 1.0)) {
                            continue;
                        }
                        Position at = new Position(pending.x() + 0.5, pending.y(), pending.z() + 0.5);
                        MobEntity villager = mobManager.spawnAt(
                                net.zaminmc.torch.server.entity.MobType.VILLAGER, at);
                        if (villager != null && pending.profession() >= 0) {
                            villager.setVillagerProfession(pending.profession());
                        }
                    }
                    chunk.pendingVillagers().clear();
                }
                if (!chunk.pendingAnimals().isEmpty()) {
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingAnimal pending
                            : chunk.pendingAnimals()) {
                        // The restart idempotence guard (the villagers' own
                        // pattern, widened to the pack-cohesion radius — a
                        // group stays within a few blocks of its records).
                        if (mobManager.mobOfKindNear(pending.type(),
                                pending.x(), pending.y(), pending.z(), 8.0)) {
                            continue;
                        }
                        MobEntity animal = mobManager.spawnAt(pending.type(),
                                new Position(pending.x(), pending.y(), pending.z()));
                        if (animal != null) {
                            animal.setBodyYaw(pending.yaw());
                            if (pending.type() == net.zaminmc.torch.server.entity.MobType.HORSE) {
                                animal.setHorseSubtype(pending.horseSubtype());
                                animal.setHorseVariant(pending.horseVariant());
                            }
                            if (pending.baby()) {
                                animal.setBreedingAge(-24_000);
                            }
                        }
                    }
                    chunk.pendingAnimals().clear();
                }
                if (!chunk.pendingGenChests().isEmpty()) {
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingGenChest pending
                            : chunk.pendingGenChests()) {
                        net.zaminmc.torch.block.BlockPosition position =
                                new net.zaminmc.torch.block.BlockPosition(
                                        pending.x(), pending.y(), pending.z());
                        net.zaminmc.torch.server.chest.ChestBlockEntity chest =
                                chestManager.getOrCreate(position);
                        // The same guard for the loot: a chest with contents
                        // (the ZCD store's restore) was already filled by a
                        // previous life — only an untouched chest takes the
                        // rolls (the emptied-and-saved edge ledgers with
                        // the slice's own note).
                        if (chestHasContents(chest)) {
                            continue;
                        }
                        for (net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot slot
                                : pending.slots()) {
                            java.util.Optional<net.zaminmc.torch.item.ItemType> type =
                                    BuiltinItems.lookup(
                                            net.zaminmc.torch.util.Identifier.parse(slot.itemId()));
                            type.ifPresent(itemType -> {
                                ItemStack stack = new ItemStack(itemType, slot.count(), slot.damage());
                                // The enchanted-book arm (Slice 12c): the
                                // rolled enchantment rides the stack.
                                if (slot.enchantments() != null && !slot.enchantments().isEmpty()) {
                                    stack = stack.withEnchantments(slot.enchantments());
                                }
                                chest.setSlot(slot.slot(), stack);
                            });
                        }
                    }
                    chunk.pendingGenChests().clear();
                }
                if (!chunk.pendingGenDispensers().isEmpty()) {
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingGenDispenser pending
                            : chunk.pendingGenDispensers()) {
                        net.zaminmc.torch.block.BlockPosition position =
                                new net.zaminmc.torch.block.BlockPosition(
                                        pending.x(), pending.y(), pending.z());
                        net.zaminmc.torch.server.dispenser.DispenserBlockEntity dispenser =
                                dispenserSystem.getOrCreate(position);
                        // The same guard as the chest: a dispenser with
                        // contents (the ZCD store's restore) skips the
                        // re-roll.
                        boolean hasContents = false;
                        for (ItemStack stack : dispenser.snapshotSlots()) {
                            if (stack != null && !stack.isEmpty()) {
                                hasContents = true;
                                break;
                            }
                        }
                        if (hasContents) {
                            continue;
                        }
                        for (net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot slot
                                : pending.slots()) {
                            java.util.Optional<net.zaminmc.torch.item.ItemType> type =
                                    BuiltinItems.lookup(
                                            net.zaminmc.torch.util.Identifier.parse(slot.itemId()));
                            type.ifPresent(itemType -> dispenser.setItem(slot.slot(),
                                    ItemStack.of(itemType, slot.count())));
                        }
                    }
                    chunk.pendingGenDispensers().clear();
                }
                if (!chunk.pendingChestMinecarts().isEmpty()) {
                    // Slice 21a — the mineshaft's loot-carrying chest minecart
                    // (the reference's placeChestWithLoot over the cart): the
                    // cart materializes into the vehicle manager with the
                    // rolled contents installed; the restart idempotence guard
                    // is the villagers' own pattern (a cart already standing
                    // on the record's cell means a previous life drained it).
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingChestMinecart pending
                            : chunk.pendingChestMinecarts()) {
                        boolean alreadyThere = false;
                        for (net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle
                                : vehicleManager.all()) {
                            if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity
                                    && Math.abs(vehicle.position().x() - (pending.x() + 0.5)) < 1.0
                                    && Math.abs(vehicle.position().y() - (pending.y() + 0.5)) < 1.0
                                    && Math.abs(vehicle.position().z() - (pending.z() + 0.5)) < 1.0) {
                                alreadyThere = true;
                                break;
                            }
                        }
                        if (alreadyThere) {
                            continue;
                        }
                        net.zaminmc.torch.server.entity.vehicle.VehicleEntity spawned =
                                vehicleManager.spawnChestMinecart(new Position(
                                        pending.x() + 0.5, pending.y() + 0.5, pending.z() + 0.5));
                        if (spawned instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart) {
                            for (net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot slot
                                    : pending.slots()) {
                                java.util.Optional<net.zaminmc.torch.item.ItemType> type =
                                        BuiltinItems.lookup(
                                                net.zaminmc.torch.util.Identifier.parse(slot.itemId()));
                                type.ifPresent(itemType -> {
                                    ItemStack stack = new ItemStack(itemType, slot.count(), slot.damage());
                                    if (slot.enchantments() != null && !slot.enchantments().isEmpty()) {
                                        stack = stack.withEnchantments(slot.enchantments());
                                    }
                                    cart.chest().setSlot(slot.slot(), stack);
                                });
                            }
                        }
                    }
                    chunk.pendingChestMinecarts().clear();
                }
                if (!chunk.pendingMobSpawners().isEmpty()) {
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingMobSpawner pending
                            : chunk.pendingMobSpawners()) {
                        net.zaminmc.torch.block.BlockPosition position =
                                new net.zaminmc.torch.block.BlockPosition(
                                        pending.x(), pending.y(), pending.z());
                        net.zaminmc.torch.server.spawner.SpawnerManager.MobSpawnerBlockEntity spawner =
                                spawnerManager.getOrCreate(worldDimension, position);
                        // The same guard: a typed spawner (the ZSD-less
                        // in-memory model — a re-publish re-types it)
                        // skips the re-set.
                        if (spawner.type() == null) {
                            spawner.setType(pending.mobType());
                            // The wire snapshot refresh (the 18e tile view
                            // carries the type; the rebuild rides the getOrCreate
                            // path's publication).
                            spawnerManager.refreshWireTiles();
                        }
                    }
                    chunk.pendingMobSpawners().clear();
                }
                if (!chunk.pendingElderGuardians().isEmpty()) {
                    // Slice 17a — the monument's own three (the reference's
                    // spawnElderGuardian): the elder flip arms the 1.9975
                    // hull, the 80 health, the 8.0 melee and the persistent
                    // never-despawn rule; the restart idempotence guard is
                    // the villagers' own pattern (a guard already standing
                    // there means a previous life drained it).
                    for (net.zaminmc.torch.server.world.EngineChunk.PendingElderGuardian pending
                            : chunk.pendingElderGuardians()) {
                        if (mobManager.mobOfKindNear(
                                net.zaminmc.torch.server.entity.MobType.GUARDIAN,
                                pending.x() + 0.5, pending.y(), pending.z() + 0.5, 2.0)) {
                            continue;
                        }
                        MobEntity elder = mobManager.spawnAt(
                                net.zaminmc.torch.server.entity.MobType.GUARDIAN,
                                new Position(pending.x() + 0.5, pending.y(),
                                        pending.z() + 0.5));
                        if (elder != null) {
                            elder.setGuardianElder(true); // the setElder + heal pair
                        }
                    }
                    chunk.pendingElderGuardians().clear();
                }
                if (!chunk.pendingEnderCrystals().isEmpty()) {
                    // Slice 62 — the pillar tops (EndPillarFeature lines
                    // 47-49): the crystal record drains exactly once per
                    // chunk publish; the destroyed-pillar marks (the ZEC
                    // store's restore) and the standing-body idempotence
                    // guard both live in the system's own spawn walk.
                    if (enderCrystals != null) {
                        for (net.zaminmc.torch.server.world.EngineChunk.PendingEnderCrystal pending
                                : chunk.pendingEnderCrystals()) {
                            enderCrystals.spawnFromGeneration(
                                    pending.x(), pending.y(), pending.z(), pending.yaw());
                        }
                    }
                    chunk.pendingEnderCrystals().clear();
                }
                var pendingDragon = chunk.pendingEnderDragon();
                if (pendingDragon != null) {
                    // Slice 63 — the chunk-(0,0) dragon (EndIslandDecorator
                    // lines 21-25): the (0, 128, 0) birth with the rolled
                    // yaw, under the two guards — a live dragon already in
                    // the End (this session's), and the ZDR defeated mark
                    // (a killed dragon never returns; vanilla's entity
                    // ledger keeps the kill, the deterministic regenerate
                    // would not).
                    chunk.clearPendingEnderDragon();
                    if (!dragonDefeated && !dragonAliveInEnd()) {
                        MobEntity dragon = mobManager.spawnAt(
                                net.zaminmc.torch.server.entity.MobType.ENDER_DRAGON,
                                new Position(0.0, 128.0, 0.0));
                        if (dragon != null) {
                            dragon.setDimension(1);
                            dragon.setBodyYaw(pendingDragon.yaw());
                        }
                    }
                }
            }
        }
    }

    /**
     * Whether a dragon already lives in the End (the chunk-(0,0) drain's
     * own guard — a re-publish or a second generate in one session never
     * doubles the boss).
     */
    private boolean dragonAliveInEnd() {
        for (MobEntity mob : mobManager.all()) {
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.ENDER_DRAGON
                    && !mob.dead()) {
                return true;
            }
        }
        return false;
    }

    /** @return whether any of the chest's 27 slots carries a stack. */
    private static boolean chestHasContents(
            net.zaminmc.torch.server.chest.ChestBlockEntity chest) {
        for (ItemStack stack : chest.snapshotSlots()) {
            if (stack != null && !stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void tickMobPotions() {
        for (MobEntity mob : mobManager.all()) {
            potionSystem.tick(mob);
        }
    }

    /** The leash walk + the knots' survival poll (Slice 11c). */
    private void tickLeashes() {
        leadSystem.tick(mobManager.all(), player -> player.position());
    }

    /** The leash system's mob surface (the tie scan + the lead drop). */
    private final class LeadSurface implements net.zaminmc.torch.server.leash.LeadSystem.Mobs {
        @Override
        public java.util.List<MobEntity> mobsIn(double minX, double minY, double minZ,
                                                 double maxX, double maxY, double maxZ) {
            java.util.List<MobEntity> found = new java.util.ArrayList<>();
            for (MobEntity mob : mobManager.all()) {
                Position at = mob.position();
                if (at.x() >= minX - mob.type().width
                        && at.x() <= maxX + mob.type().width
                        && at.z() >= minZ - mob.type().width
                        && at.z() <= maxZ + mob.type().width
                        && at.y() >= minY && at.y() <= maxY + mob.type().height) {
                    found.add(mob);
                }
            }
            return found;
        }

        @Override
        public void dropLead(Position at) {
            itemEntities.spawnDropAtBlock(at, ItemStack.of(BuiltinItems.LEAD, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
    }

    /** The decorations' survival poll (Slice 28a/28b — the 100-tick walk). */
    private void tickDecorations() {
        decorations.tick();
        armorStands.tick(); // the stands' fire burn-down (Slice 28c)
        if (enderCrystals != null) {
            enderCrystals.tick(); // the crystals' spin + the feet fire (Slice 62)
        }
        if (fireworks != null) {
            fireworks.tick(); // the rockets' flight clock + the burst (Slice 65)
        }
    }

    /**
     * The fireworks' engine surface (Slice 65): the age-0 launch sound —
     * the world-scope 3.0-volume whoosh every listener hears.
     */
    private final class FireworksSurface
            implements net.zaminmc.torch.server.fireworks.FireworksSystem.Surface {
        @Override
        public void launchSound(Position at) {
            fxManager.sound(at, "fireworks.launch",
                    net.zaminmc.torch.server.fireworks.FireworksEntity.LAUNCH_VOLUME,
                    net.zaminmc.torch.server.fireworks.FireworksEntity.LAUNCH_PITCH);
        }
    }

    /**
     * The ender crystals' engine surface (Slice 62): the End world's own
     * reads/writes — the dimension gate is structural (the surface IS
     * dimension 1's world; the overworld never sees a crystal).
     */
    private final class EnderCrystalSurface
            implements net.zaminmc.torch.server.endcrystal.EnderCrystalSystem.Surface {
        @Override
        public String blockAt(int x, int y, int z) {
            return endWorld.getBlock(new BlockPosition(x, y, z)).identifier().value();
        }

        @Override
        public void setFire(int x, int y, int z) {
            // EnderCrystalEntity.tick line 49: the plain FIRE write at the
            // feet cell (anything that is not fire becomes fire — the
            // reference's own replace, the bedrock cap included).
            BlockType fire = blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:fire"));
            endWorld.setBlock(new BlockPosition(x, y, z), fire);
        }

        @Override
        public void explode(double x, double y, double z) {
            // takeDamage line 77: the 6.0F destructive blast in the End's
            // own world (the reference's six-arg explode — createFire
            // false, destructive true).
            detonateAtIn(endWorld, endExplosionService, 1,
                    x, y, z,
                    net.zaminmc.torch.server.endcrystal.EnderCrystalSystem.BLAST_POWER,
                    net.zaminmc.torch.server.endcrystal.EnderCrystalSystem.BLAST_MAX_DAMAGE,
                    true);
        }

        @Override
        public long now() {
            return endWorld.timeOfDay();
        }
    }

    /** The armor stands' engine surface (the world gates + the drops). */
    private final class ArmorStandSurface
            implements net.zaminmc.torch.server.armorstand.ArmorStandSystem.Surface {
        @Override
        public boolean replaceableAt(net.zaminmc.torch.block.BlockPosition pos) {
            return WorldSolidity.isReplaceable(world.getBlock(pos).identifier());
        }

        @Override
        public boolean airAt(net.zaminmc.torch.block.BlockPosition pos) {
            return world.getBlock(pos).equals(world.airType());
        }

        @Override
        public void dropItem(Position at, ItemStack stack) {
            if (!stack.isEmpty()) {
                itemEntities.spawnDropAtBlock(at, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }

        @Override
        public boolean entityOccupies(double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
            for (PlayerSession player : players.all()) {
                Position p = player.position();
                if (p.x() >= minX - 0.3 && p.x() <= maxX + 0.3
                        && p.y() >= minY && p.y() <= maxY
                        && p.z() >= minZ - 0.3 && p.z() <= maxZ + 0.3) {
                    return true;
                }
            }
            for (MobEntity mob : mobManager.all()) {
                Position p = mob.position();
                if (p.x() >= minX - mob.type().width * 0.5
                        && p.x() <= maxX + mob.type().width * 0.5
                        && p.y() >= minY && p.y() <= maxY + mob.type().height
                        && p.z() >= minZ - mob.type().width * 0.5
                        && p.z() <= maxZ + mob.type().width * 0.5) {
                    return true;
                }
            }
            for (net.zaminmc.torch.server.armorstand.ArmorStand other : armorStands.allStands()) {
                if (other.x() >= minX && other.x() <= maxX
                        && other.z() >= minZ && other.z() <= maxZ
                        && other.y() >= minY - 1.975 && other.y() <= maxY) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public long now() {
            return world.timeOfDay();
        }
    }

    /** The decoration system's engine surface (the world + the drops). */
    private final class DecorationSurface
            implements net.zaminmc.torch.server.decoration.DecorationSystem.Surface {
        @Override
        public World world() {
            return world;
        }

        @Override
        public void dropItem(Position at, ItemStack stack) {
            if (!stack.isEmpty()) {
                itemEntities.spawnDropAtBlock(at, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }

        @Override
        public boolean doEntityDrops() {
            return gameRules.getBoolean("doEntityDrops");
        }

        @Override
        public void wakeComparators(net.zaminmc.torch.block.BlockPosition cell) {
            redstoneSystem.wakeComparators(cell);
        }
    }

    /** The natural spawn cycle's mob surface (Slice 11d). */
    private record SpawnSurface(MobManager mobs, boolean nether)
            implements net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Mobs {
        @Override
        public void spawn(net.zaminmc.torch.server.entity.MobType type, Position at,
                          boolean baby, float yaw) {
            if (nether) {
                // Slice 18d — the nether's own initialize walk: the
                // skeletons' wither roll lives at the manager's nether arm
                // (SkeletonEntity.initialize lines 215-226).
                mobs.spawnNaturalNether(type, at, baby, yaw);
            } else {
                mobs.spawnNatural(type, at, baby, yaw);
            }
        }

        @Override
        public int countOf(net.zaminmc.torch.server.spawn.NaturalSpawnSystem.Category category) {
            return mobs.countByCategory(category);
        }
    }

    /** The natural spawn cycle's world read (Slice 11d). */
    private final class SpawnWorldQuery
            implements net.zaminmc.torch.server.spawn.NaturalSpawnSystem.WorldQuery {
        private final EngineWorld target;
        private final boolean nether;
        /**
         * The monument bounds consult (Slice 17a — the guardian's spawn
         * rows): deterministic from the world seed, the same placement
         * math the world generator's own monuments ride.
         */
        private final net.zaminmc.torch.server.world.structure.OceanMonumentStructure monuments =
                new net.zaminmc.torch.server.world.structure.OceanMonumentStructure(worldSeed);
        /**
         * The monument consult's biome view: the pure fbm answer the
         * placement gates read (the generator's own biomeAt — never the
         * chunk array, so the bounds check stays deterministic even over
         * unloaded chunks).
         */
        private final net.zaminmc.torch.server.world.structure.StructureWorld monumentBiomes =
                new net.zaminmc.torch.server.world.structure.StructureWorld() {
                    @Override
                    public net.zaminmc.torch.block.BlockType blockAt(int x, int y, int z) {
                        return target.getBlock(new BlockPosition(x, y, z));
                    }

                    @Override
                    public boolean isLiquid(net.zaminmc.torch.block.BlockType type) {
                        return net.zaminmc.torch.server.block.FluidBlocks.isFluid(type.identifier());
                    }

                    @Override
                    public void setBlock(int x, int y, int z, net.zaminmc.torch.block.BlockType type) {
                        // the bounds consult never writes
                    }

                    @Override
                    public int seaLevel() {
                        return 63;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.structure.StructurePalette palette() {
                        throw new UnsupportedOperationException(
                                "the monument bounds consult reads no palette");
                    }

                    @Override
                    public boolean isOpaque(net.zaminmc.torch.block.BlockType type) {
                        return false;
                    }

                    @Override
                    public net.zaminmc.torch.server.world.Biome biomeAt(int x, int z) {
                        var generator = target.generator();
                        if (generator instanceof
                                net.zaminmc.torch.server.world.NormalWorldGenerator normal) {
                            return normal.biomeAt(x, z); // the pure fbm answer
                        }
                        return net.zaminmc.torch.server.world.Biome.PLAINS;
                    }
                };

        SpawnWorldQuery(EngineWorld target, boolean nether) {
            this.target = target;
            this.nether = nether;
        }

        @Override
        public Object blockAt(BlockPosition position) {
            return target.getBlock(position);
        }

        @Override
        public boolean insideMonument(int x, int y, int z) {
            if (nether) {
                return false; // the nether carries no monuments
            }
            return monuments.insideBounds(monumentBiomes, x, y, z);
        }

        /**
         * The fortress consult (Slice 18a —
         * NetherChunkGenerator.getSpawnEntries lines 383-392): the strict
         * piece-bounds arm, or the start's envelope when the cell stands
         * on nether bricks (the reference's own second clause). The
         * consult's world view is the same generic adapter the monument
         * consult rides — the fortress start's creation never reads it
         * (the fortress's placement math is pure (seed, region), unlike
         * the monument's biome gates).
         */
        @Override
        public boolean insideFortress(int x, int y, int z) {
            if (!nether) {
                return false; // the overworld carries no fortresses
            }
            net.zaminmc.torch.server.world.structure.FortressStructure fortresses =
                    new net.zaminmc.torch.server.world.structure.FortressStructure(worldSeed);
            if (fortresses.insideBounds(monumentBiomes, x, y, z, true)) {
                return true; // the piece walk
            }
            if (fortresses.insideBounds(monumentBiomes, x, y, z, false)) {
                // The floor gate: the cell under the feet is nether brick
                // (the reference's exact-block getBlockState(pos.down())
                // read — the fence and the stairs never carry the rows).
                Object below = target.getBlock(new BlockPosition(x, y - 1, z));
                return below != null
                        && below.toString().contains("[minecraft:nether_bricks]");
            }
            return false;
        }

        @Override
        public int surfaceHeight(int x, int z) {
            if (nether) {
                return netherSurfaceY(x, z);
            }
            int at = surfaceY(x, z);
            return at < 0 ? 0 : at;
        }

        @Override
        public int blockLightAt(BlockPosition position) {
            return target.blockLightAt(position);
        }

        @Override
        public int skyLightAt(BlockPosition position) {
            return nether ? 0 : target.skyLightAt(position); // the roofed dimension is always dark
        }

        @Override
        public Position spawnPoint() {
            return target.spawnPosition();
        }

        @Override
        public boolean isChunkLoaded(int chunkX, int chunkZ) {
            // The loaded-box gate (the border's engine stand-in): the spawn
            // walk never forces a generation — an unloaded chunk simply
            // joins the count without ever hosting a pack.
            return target.isChunkLoaded(
                    new net.zaminmc.torch.block.ChunkPosition(chunkX, chunkZ));
        }

        @Override
        public long worldSeed() {
            return worldSeed;
        }
    }

    /**
     * The nether's floor scan (Slice 16d): below the bedrock roof, the
     * first solid cell with two air cells above it — a standable spawn
     * cell (the engine's stand-in for the reference's own
     * getTopSolidOrLiquidBlock under a roofed dimension — the vanilla
     * roof-band spawn quirk ledgered with the worldgen slice).
     */
    private int netherSurfaceY(int x, int z) {
        EngineChunk chunk = netherWorld.peek(
                new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
        if (chunk == null) {
            chunk = netherWorld.getOrGenerate(
                    new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
        }
        for (int y = 120; y >= 31; y--) {
            if (!chunk.getBlock(x & 15, y, z & 15).equals(netherWorld.airType())
                    && chunk.getBlock(x & 15, y + 1, z & 15).equals(netherWorld.airType())
                    && chunk.getBlock(x & 15, y + 2, z & 15).equals(netherWorld.airType())) {
                return y;
            }
        }
        return -1;
    }

    /**
     * Registers the leash observer (the protocol adapter's Spawn Object 77
     * + the Attach Entity pair + the Destroy Entities fan-out).
     */
    public void addLeadListener(net.zaminmc.torch.server.leash.LeadSystem.Listener listener) {
        if (leadSystem == null) {
            throw new IllegalStateException("the leash system boots with the world");
        }
        leadSystem.addListener(listener);
    }

    /**
     * Registers the decoration observer (the protocol adapter's Spawn
     * Object 71 + the Spawn Painting + the Set Entity Metadata pair +
     * the Destroy Entities fan-out).
     */
    public void addDecorationListener(
            net.zaminmc.torch.server.decoration.DecorationSystem.Listener listener) {
        if (decorations == null) {
            throw new IllegalStateException("the decoration system boots with the world");
        }
        decorations.addListener(listener);
    }

    /** The decoration system (§27.3's surface — the frames + the paintings). */
    public net.zaminmc.torch.server.decoration.DecorationSystem decorations() {
        return decorations;
    }

    /** The armor stand system (§27.3's surface). */
    public net.zaminmc.torch.server.armorstand.ArmorStandSystem armorStands() {
        return armorStands;
    }

    /**
     * Registers the armor stand observer (the protocol adapter's Spawn
     * Object 78 + the metadata + the Entity Equipment family + the
     * Destroy fan-out).
     */
    public void addArmorStandListener(
            net.zaminmc.torch.server.armorstand.ArmorStandSystem.Listener listener) {
        if (armorStands == null) {
            throw new IllegalStateException("the armor stand system boots with the world");
        }
        armorStands.addListener(listener);
    }

    /** The ender-crystal system (§27.3's surface — the End pillar tops). */
    public net.zaminmc.torch.server.endcrystal.EnderCrystalSystem enderCrystals() {
        return enderCrystals;
    }

    /** The firework-rocket system (§27.3's surface — the launch + the burst). */
    public net.zaminmc.torch.server.fireworks.FireworksSystem fireworks() {
        return fireworks;
    }

    /**
     * Registers the ender-crystal observer (the protocol adapter's Spawn
     * Object 200 + the slot-8 Int metadata + the Destroy fan-out).
     */
    public void addEnderCrystalListener(
            net.zaminmc.torch.server.endcrystal.EnderCrystalSystem.Listener listener) {
        if (enderCrystals == null) {
            throw new IllegalStateException("the ender-crystal system boots with the world");
        }
        enderCrystals.addListener(listener);
    }

    /**
     * The projectile combat rules: arrows wound (speed-scaled), shards bruise
     * (knockback without damage — the historical snowball). Both ride the
     * same hurt-window, status and knockback packets as the melee paths.
     */
    private final class ProjectileCombatSink implements ProjectileManager.CombatSink {
        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            // The shooter's Looting rides the death loot walk (the reference's
            // die() gate: entity instanceof PlayerEntity — mob shooters
            // contribute zero).
            int looting = 0;
            boolean playerShooter = false;
            PlayerSession shooter = null;
            if (shooterId > 0) {
                for (PlayerSession candidate : players.all()) {
                    if (candidate.engineEntityId() == shooterId) {
                        looting = EnchantmentHelper.lootingLevel(candidate.inventory().held());
                        playerShooter = true;
                        shooter = candidate;
                        break;
                    }
                }
            }
            mob.setLootingLevel(looting);
            // The enderman's projectile dodge (EndermanEntity.takeDamage
            // lines 299-309): the projectile NEVER lands — 64 teleport
            // attempts instead, and no damage either way.
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.ENDERMAN) {
                for (int i = 0; i < 64; i++) {
                    if (mob.teleportRandomly()) {
                        break;
                    }
                }
                return false;
            }
            boolean landedArrow = mobManager.hurt(mob, damage, kbYaw,
                    net.zaminmc.torch.event.DamageCause.PROJECTILE);
            if (landedArrow && playerShooter) {
                // The wound stats (the melee arm's own rows over the arrow):
                // the dealt tenths and the kill-credit carrier.
                shooter.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.DAMAGE_DEALT,
                        Math.round(damage * 10.0f));
                mob.setPlayerKiller(shooter);
                // takeDamage lines 678-680: the player shooter's landed
                // arrow arms the 100-tick hit window.
                mob.markPlayerHit();
            }
            return landedArrow;
        }

        @Override
        public boolean mobHit(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile,
                              MobEntity mob, float damage, double kbYaw) {
            // The wither's own gates (Slice 59 — takeDamage lines 408-438)
            // read the source: the half-health shield is ARROW-only (the
            // source must be an ArrowEntity — fireballs and snowballs
            // still land), and a skull from another wither never lands.
            boolean arrow = projectile.kind()
                    == net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW;
            MobEntity shooterMob = null;
            if (projectile.throwerId() > 0) {
                for (MobEntity candidate : mobManager.all()) {
                    if (!candidate.dead()
                            && candidate.entityId() == projectile.throwerId()) {
                        shooterMob = candidate;
                        break;
                    }
                }
            }
            // The enderman's dodge and the wither's gates share the walk:
            // the dodge (the legacy path above) consults no source; the
            // wither's gates run inside the manager's full overload with
            // the source pair (arrow, shooter body).
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.WITHER) {
                return mobManager.hurt(mob, damage, kbYaw,
                        net.zaminmc.torch.event.DamageCause.PROJECTILE,
                        false, arrow, shooterMob);
            }
            return mobHit(mob, damage, kbYaw, projectile.throwerId());
        }

        @Override
        public void skullKillHeal(int shooterId) {
            // WitherSkullEntity.onHit lines 58-64: the victim died — the
            // shooting wither heals 5.0.
            for (MobEntity candidate : mobManager.all()) {
                if (!candidate.dead() && candidate.entityId() == shooterId) {
                    candidate.heal(5.0f);
                    return;
                }
            }
        }

        @Override
        public void skullWitherCurse(MobEntity victim) {
            // onHit lines 69-80: WITHER II — 10 s on NORMAL, 40 s on HARD,
            // nothing below (the difficulty band the sink resolves).
            int seconds = difficulty() == 2 ? 10 : difficulty() == 3 ? 40 : 0;
            if (seconds > 0 && potionSystem != null) {
                potionSystem.add(victim,
                        new net.zaminmc.torch.server.potion.StatusEffectInstance(
                                net.zaminmc.torch.server.potion.StatusEffects.WITHER,
                                seconds * 20, 1));
            }
        }

        @Override
        public void skullWitherCurse(PlayerSession victim) {
            // The player arm — the same band.
            int seconds = difficulty() == 2 ? 10 : difficulty() == 3 ? 40 : 0;
            if (seconds > 0 && potionSystem != null) {
                potionSystem.add(victim,
                        new net.zaminmc.torch.server.potion.StatusEffectInstance(
                                net.zaminmc.torch.server.potion.StatusEffects.WITHER,
                                seconds * 20, 1));
            }
        }

        @Override
        public void mobIgniteFromProjectile(MobEntity mob) {
            // The burning arrow's hit (the reference's setOnFireFor(5)):
            // the mob carries no equipment, the plain 5-second burn.
            mob.ignite(PROJECTILE_FLAME_TARGET_TICKS);
        }

        @Override
        public void playerIgniteFromProjectile(PlayerSession victim) {
            // The player arm: the victim's own Fire Protection shortens the
            // clock (the reference's setOnFireFor equipment read).
            igniteWithFireProtection(victim, PROJECTILE_FLAME_TARGET_TICKS / TICKS_PER_SECOND);
        }

        @Override
        public void playerHit(PlayerSession victim, float damage, double kbYaw) {
            projectileHitPlayerOnTick(null, victim, damage, kbYaw, -1);
        }

        @Override
        public void playerHit(ProjectileEntity projectile, PlayerSession victim,
                              float damage, double kbYaw) {
            projectileHitPlayerOnTick(projectile, victim, damage, kbYaw,
                    projectile.throwerId());
        }

        @Override
        public void chickenHatch(Position position) {
            mobManager.spawnAt(MobType.CHICKEN, position);
            fxManager.sound(position, "mob.chicken.say", 0.5f, 1.0f);
            LOGGER.fine(() -> "An egg hatched a chick at " + position);
        }
    }

    /**
     * The zero-reach projectile hit on a player: the projectile already
     * traveled, so no reach check — but the hurt window, knockback and death
     * path stay identical to melee PvP. Tick-thread context.
     */
    private void projectileHitPlayerOnTick(ProjectileEntity projectile, PlayerSession victim,
                                            float damage, double kbYaw, int shooterId) {
        if (!config.pvp() || victim.dead() || victim.state() != PlayerState.PLAYING) {
            return;
        }
        // The armor envelope eats its share of a physical hit before the
        // invulnerability bookkeeping (the historical order), then the
        // enchantment-protection step (an arrow is a projectile source).
        damage = applyEnchantProtection(victim,
                applyArmor(victim, damage), DamageKind.PROJECTILE);
        // §27.7: the public damage gate rides the same place the melee
        // family does — after the protection math, before any state moves.
        // A cancelled hit lands nothing (no bruise window, no hurt, no
        // knockback); the mutable amount drives the window math onward.
        net.zaminmc.torch.event.player.PlayerDamageEvent damageEvent =
                new net.zaminmc.torch.event.player.PlayerDamageEvent(
                        victim, damage, net.zaminmc.torch.event.DamageCause.PROJECTILE);
        eventBus.fire(damageEvent);
        if (damageEvent.cancelled()) {
            return;
        }
        damage = damageEvent.amount();
        if (damage <= 0) {
            return;
        }
        if (victim.hurtInvulnerable() && damage <= victim.lastHurtDamage()) {
            return; // absorbed by the hurt window (a bruise out-damages nothing)
        }
        float applied = victim.hurtInvulnerable() ? Math.max(0.0f, damage - victim.lastHurtDamage())
                : damage;
        victim.beginHurtInvulnerability(Math.max(damage, 0.01f));
        if (applied > 0) {
            victim.hurt(applied);
        }
        // Knockback — the same vanilla applyKnockback recipe the melee path
        // rides (vanilla projectiles knock back through the damage path,
        // not their own rule): the motion halves, the impulse rides the
        // impact direction, the rise caps at 0.4.
        double kbdx = -Math.sin(kbYaw);
        double kbdz = Math.cos(kbYaw);
        double nvx = victim.motionX() / 2.0 + kbdx * 0.4;
        double nvy = Math.min(victim.motionY() / 2.0 + 0.4, 0.4);
        double nvz = victim.motionZ() / 2.0 + kbdz * 0.4;
        // The Punch extra rides the same velocity set on top (the reference's
        // addVelocity arm after the landed takeDamage, ArrowEntity lines
        // 255-260): the arrow's horizontal direction scaled by level * 0.6
        // over the horizontal speed, plus the 0.1 rise.
        if (projectile != null && projectile.punchLevel() > 0) {
            double horizontal = Math.sqrt(projectile.velocityX() * projectile.velocityX()
                    + projectile.velocityZ() * projectile.velocityZ());
            if (horizontal > 0.0) {
                nvx += projectile.velocityX() * projectile.punchLevel() * 0.6 / horizontal;
                nvy += 0.1;
                nvz += projectile.velocityZ() * projectile.punchLevel() * 0.6 / horizontal;
            }
        }
        victim.setMotion(nvx, nvy, nvz);
        publishPlayerHurt(victim);
        publishKnockback(victim, nvx, nvy, nvz);
        // The arrow site of the thorns wildcard (the reference's ArrowEntity
        // line 264: the victim's worn pieces bite back at the shooter when
        // the shooter is a living body — the skeleton shooter takes its
        // share like a player does). The shooter is resolved by engine id:
        // the player band first, then the mob band; a missing shooter (the
        // thrower left) skips the retaliation, the reference's null-shooter
        // guard. The row is read before the death check: a killing blow
        // still retaliates before death scatters the armor.
        applyThornsWildcard(victim, victim.inventory().armorStacks(),
                new ThornsTarget() {
                    @Override
                    public void takeThornsDamage(int amount, PlayerSession wearer) {
                        if (shooterId > 0) {
                            for (PlayerSession candidate : players.all()) {
                                if (candidate.engineEntityId() == shooterId) {
                                    damageOnTick(candidate,
                                            applyEnchantProtection(candidate,
                                                    applyArmor(candidate, amount),
                                                    DamageKind.MELEE),
                                            "was slain by " + wearer.name(), DamageKind.MELEE);
                                    return;
                                }
                            }
                            if (mobManager != null) {
                                for (MobEntity mob : mobManager.all()) {
                                    if (mob.entityId() == shooterId) {
                                        mob.setLootingLevel(
                                                EnchantmentHelper.lootingLevel(
                                                        wearer.inventory().held()));
                                        if (mobManager.hurt(mob, amount,
                                                wearer.rotation().yaw())) {
                                            // The thorns wound's attacker is the
                                            // wearing player — the hit window arms
                                            // (takeDamage lines 678-680).
                                            mob.markPlayerHit();
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public Position position() {
                        // The sound rides the shooter's last known body: the
                        // player band, then the mob band, else the victim.
                        if (shooterId > 0) {
                            for (PlayerSession candidate : players.all()) {
                                if (candidate.engineEntityId() == shooterId) {
                                    return candidate.position();
                                }
                            }
                            if (mobManager != null) {
                                for (MobEntity mob : mobManager.all()) {
                                    if (mob.entityId() == shooterId) {
                                        return mob.position();
                                    }
                                }
                            }
                        }
                        return victim.position();
                    }
                });
        if (victim.health() <= 0) {
            dieOnTick(victim, "was pummeled by " + throwerName(shooterId));
        } else {
            publishBodyChanged(victim);
        }
    }

    /**
     * Resolves a projectile's thrower engine id to a display name for the
     * death chat: player band first, then the mob band, else "a projectile".
     * Tick-thread context.
     */
    private String throwerName(int throwerId) {
        if (throwerId > 0) {
            for (PlayerSession candidate : players.all()) {
                if (candidate.engineEntityId() == throwerId) {
                    return candidate.name();
                }
            }
            if (mobManager != null) {
                for (MobEntity mob : mobManager.all()) {
                    if (mob.entityId() == throwerId) {
                        return titledMobName(mob);
                    }
                }
            }
        }
        return "a projectile";
    }

    /** The historical chat display name of a mob kind ("Zombie", "Creeper"). */
    private static String titledMobName(MobEntity mob) {
        String raw = mob.type().name();
        return raw.charAt(0) + raw.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Fan-out from the simulation-owned mob system to the observer list —
     * the same pattern the item-entity system uses (§448): the engine decides
     * what happened, the listeners decide how it reaches clients. Zombie
     * melee lands through the survival damage path (same thread, one owner).
     */
    private final class MobEventDispatch implements MobManager.Listener {
        @Override
        public void onMobSpawned(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSpawned(mob);
            }
        }

        @Override
        public void onMobMoved(MobEntity mob, PlayerSession rider) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobMoved(mob, rider);
            }
        }

        @Override
        public void onMobHurt(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobHurt(mob);
            }
        }

        @Override
        public void onMobDied(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobDied(mob);
            }
            // The kill credit (LivingEntity.die's own rows over the damage
            // source's player): the mob counter and the per-type pair, the
            // entity key the reference's own band (the zombie pigman's
            // "PigZombie", the ocelot's "Ozelot").
            net.zaminmc.torch.server.player.PlayerSession killer = mob.playerKiller();
            if (killer != null && killer.state() == PlayerState.PLAYING) {
                killer.stats().increment(
                        net.zaminmc.torch.server.stat.EngineStats.MOBS_KILLED, 1);
                String entityKey = net.zaminmc.torch.server.stat.EngineStats
                        .entityKeyOf(mob.type().name());
                if (entityKey != null) {
                    killer.stats().increment(
                            net.zaminmc.torch.server.stat.EngineStats.killEntityOf(entityKey), 1);
                }
            }
            // §27.7: the public death observation — the health reached zero
            // and the death animation starts; the loot and the experience
            // release right after. Not cancellable (the death is already
            // authoritative); the observation point for kill counters.
            eventBus.fire(new net.zaminmc.torch.event.entity.MobDeathEvent(mob));
            // doMobLoot (Slice 24a — LivingEntity.die's own XP gate): the
            // kill reward skips when the rule is off; the death itself
            // still resolves.
            if (!gameRules.getBoolean("doMobLoot")) {
                return;
            }
            // The kill reward (the historical getExperienceValue): the body
            // releases its XP band at the death point; orbs wait out their
            // pickup delay while the death animation plays. The slime's own
            // band is its size (SlimeEntity.setSize's xpDrop = size).
            int amount = mob.type().slimeFamily()
                    ? mob.slimeSize()
                    : mob.type() == net.zaminmc.torch.server.entity.MobType.GHAST
                    ? 5 // GhastEntity's own xpDrop
                    : mob.type() == net.zaminmc.torch.server.entity.MobType.GUARDIAN
                    ? 10 // GuardianEntity's own xpDrop (line 52)
                    : mob.type() == net.zaminmc.torch.server.entity.MobType.BLAZE
                    ? 10 // BlazeEntity's own xpDrop (line 29)
                    : mob.type() == net.zaminmc.torch.server.entity.MobType.WITHER
                    ? 50 // WitherEntity's own xpDrop (the ctor line 67)
                    : ExperienceAwards.forMobKill(mob.type().hostile, gameplayRandom);
            experienceOrbs.spawnBurst(mob.position(), amount, 3);
        }

        @Override
        public void onMobRemoved(MobEntity mob, String reason) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobRemoved(mob, reason);
            }
        }

        @Override
        public void onMobBurningChanged(MobEntity mob, boolean burning) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobBurningChanged(mob, burning);
            }
        }

        @Override
        public void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobAttackedPlayer(mob, target, damage);
            }
            // Slice 18b — the cave spider's venomous bite
            // (CaveSpiderEntity.tryDamage lines 25-45): a landed hit
            // poisons by the difficulty — NORMAL the 7-second curse, HARD
            // the 15-second, the weaker rows nothing.
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.CAVE_SPIDER) {
                int poisonSeconds = difficulty() == 2 ? 7 : difficulty() == 3 ? 15 : 0;
                if (poisonSeconds > 0) {
                    potionSystem.add(target,
                            new net.zaminmc.torch.server.potion.StatusEffectInstance(
                                    net.zaminmc.torch.server.potion.StatusEffects.POISON,
                                    poisonSeconds * 20, 0));
                }
            }
            // Slice 18d — the wither skeleton's cursed blade
            // (SkeletonEntity.tryDamage lines 96-106): the variant's landed
            // melee hit applies the flat 10-second WITHER — no amplifier,
            // no difficulty gate (the reference's own shape).
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.SKELETON
                    && mob.skeletonWither()) {
                potionSystem.add(target,
                        new net.zaminmc.torch.server.potion.StatusEffectInstance(
                                net.zaminmc.torch.server.potion.StatusEffects.WITHER,
                                mob.skeletonWitherEffectTicks(), 0));
            }
            // Slice 16b — AttackWithOwnerGoal: the victim's standing tamed
            // wolves join the fight on the attacker (the owner's own
            // getAttacker + getLastAttackedTime change, the event-site arm).
            if (mobManager != null && !mob.dead()) {
                mobManager.ownerWolvesJoinFight(target.name(), mob);
            }
            // The same-thread survival damage path; the victim's armor eats
            // its share first (the 1.8 envelope), then the enchantment-
            // protection step (the reference's effects-and-enchantments half).
            // The victim's armor row is captured before the walk: the
            // reference fires the thorns wildcard in the attacker's walk
            // before the victim's takeDamage runs (Entity.damageEntity lines
            // 1943-1947), so a killing blow still retaliates through the
            // worn pieces before death scatters them.
            ItemStack[] thornsRow = target.inventory().armorStacks();
            damageOnTick(target,
                    applyEnchantProtection(target, applyArmor(target, damage),
                            DamageKind.MELEE),
                    "was slain by " + titledMobName(mob), DamageKind.MELEE);
            applyThornsWildcard(target, thornsRow, new ThornsTarget() {
                @Override
                public void takeThornsDamage(int amount, PlayerSession wearer) {
                    // The mob's knockback rides the wearer's look yaw — the
                    // away-direction the melee walk uses for its impulse.
                    if (mobManager != null) {
                        mob.setLootingLevel(EnchantmentHelper.lootingLevel(
                                wearer.inventory().held()));
                        if (mobManager.hurt(mob, amount, wearer.rotation().yaw())) {
                            // The thorns wound's attacker is the wearing
                            // player — the hit window arms (takeDamage
                            // lines 678-680).
                            mob.markPlayerHit();
                        }
                    }
                }

                @Override
                public Position position() {
                    return mob.position();
                }
            });
        }

        @Override
        public void onMobSound(MobEntity mob, String soundName) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSound(mob, soundName);
            }
        }

        @Override
        public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
            // The witch's throw (WitchEntity.doRangedAttack lines 197-216):
            // the splash potion with the reference's own choice ladder.
            if (mob.type() == net.zaminmc.torch.server.entity.MobType.WITCH) {
                witchRangedAttack(mob, aimPoint);
                return;
            }
            // The skeleton loosed: launch the arrow through the projectile
            // system (the same physics and hit resolution player arrows ride).
            Position origin = mob.eyePosition();
            double dx = aimPoint.x() - origin.x();
            double dy = (aimPoint.y() + 1.0) - origin.y(); // aim at the torso
            double dz = aimPoint.z() - origin.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            projectileManager.launch(ProjectileEntity.Kind.ARROW, mob.entityId(),
                    origin, yaw, pitch, MobEntity.SKELETON_ARROW_SPEED);
            fxManager.sound(mob.position(), "random.bow", 1.0f, 1.0f);
            publishMobSwing(mob); // the arm swing rides the animation packet
        }

        /**
         * The ghast loosed (ShootFireballGoal lines 302-314): the fireball
         * spawns 4 blocks forward of the eye (the look = the target grip),
         * aimed at the target's torso; the 1008 launch sound rides it.
         */
        @Override
        public void onMobGhastFireball(MobEntity mob, Position target) {
            // The spawn point: the eye band (y + height/2 + 0.5) pushed 4
            // blocks along the facing.
            double eyeY = mob.position().y() + mob.bodyHeight() / 2.0 + 0.5;
            double radians = Math.toRadians(mob.headYaw());
            double lookX = -Math.sin(radians);
            double lookZ = Math.cos(radians);
            Position origin = new Position(
                    mob.position().x() + lookX * 4.0,
                    eyeY,
                    mob.position().z() + lookZ * 4.0);
            // The direction: from the spawn point to the target's torso
            // (the reference's own d2/d3/d4 walk).
            double dx = target.x() - origin.x();
            double dy = (target.y() + 0.9) - origin.y(); // the player torso
            double dz = target.z() - origin.z();
            projectileManager.launchGhastFireball(mob.entityId(), origin, dx, dy, dz);
            fxManager.sound(mob.position(), "mob.ghast.fireball", 10.0f, 1.0f);
        }

        /**
         * The blaze loosed one volley fireball (BlazeEntity.AttackGoal
         * lines 230-244): the small fireball spawns at the blaze's own
         * mid band + 0.5 (the reference's own spawn write), aimed at the
         * target's mid band with the horizontal-only gaussian spread
         * (sigma {@code sqrt(sqrt(d0)) * 0.5}) and the ctor's own 0.4
         * per-axis tilt; the shot rides world event 1009 — the client
         * plays {@code mob.ghast.fireball} at volume 2.0 (the reference's
         * own event, the blaze's shared shot sound).
         */
        @Override
        public void onMobBlazeFireball(MobEntity mob) {
            // The target this tick: the nearest player the volley aimed at.
            PlayerSession target = null;
            double nearest = Double.MAX_VALUE;
            for (PlayerSession player : players.all()) {
                if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                        || player.dead()) {
                    continue;
                }
                double dx = player.position().x() - mob.position().x();
                double dy = player.position().y() - mob.position().y();
                double dz = player.position().z() - mob.position().z();
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared < nearest) {
                    nearest = squared;
                    target = player;
                }
            }
            if (target == null || nearest >= 256.0) {
                return; // the target slipped beyond the volley band
            }
            // AttackGoal lines 214-216: the aim toward the target's MID
            // band (the shape's minY + height/2 against the blaze's own).
            double dx = target.position().x() - mob.position().x();
            double dy = target.position().y() + 1.8 / 2.0
                    - (mob.position().y() + mob.bodyHeight() / 2.0);
            double dz = target.position().z() - mob.position().z();
            // The reference's own spawn write: y = blaze.y + height/2 + 0.5.
            Position origin = new Position(mob.position().x(),
                    mob.position().y() + mob.bodyHeight() / 2.0 + 0.5,
                    mob.position().z());
            projectileManager.launchBlazeFireball(mob.entityId(), origin,
                    dx, dy, dz, mob.blazeFireballSigma(nearest));
            // World event 1009 (WorldRenderer line 1939): the shot the
            // clients answer with mob.ghast.fireball at volume 2.0.
            fxManager.sound(mob.position(), "mob.ghast.fireball", 2.0f,
                    (gameplayRandom.nextFloat() - gameplayRandom.nextFloat()) * 0.2f + 1.0f);
        }

        // ------------------------------------------------------------------
        // The wither's own band (Slice 59 — WitherEntity's event family)
        // ------------------------------------------------------------------

        /**
         * The blue window's end (mobAiTick lines 214-219): the 7.0F birth
         * explosion at the body's eye height (the reference's
         * {@code world.explode(this, x, y + getEyeHeight(), z, 7.0F, false,
         * mobGriefing)}) — wounds and knocks but never breaks blocks (the
         * fire flag false) — and the GLOBAL 1013 spawn sound rides the
         * forward below (every connected player, every dimension).
         */
        @Override
        public void onWitherBirth(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWitherBirth(mob);
            }
            double eye = mob.eyeHeightForQuery();
            detonateAt(mob.position().x(), mob.position().y() + eye, mob.position().z(),
                    WITHER_BIRTH_POWER, WITHER_BIRTH_POWER * 2.0f, false);
        }

        /**
         * A head loosed its skull (shootSkullAt lines 383-400): the head's
         * own mount point (the center head at +3.0 above the feet, the
         * side heads at the plus/minus 1.3 flank at +2.2 — the body's own
         * yaw shapes the flank) aims the raw delta at the target cell; the
         * ctor's gaussian tilt and the 0.1 acceleration ride the
         * projectile system's launch.
         */
        @Override
        public void onWitherSkull(net.zaminmc.torch.server.entity.MobEntity mob, int head,
                                  double targetX, double targetY, double targetZ,
                                  boolean charged) {
            ProjectileManager liveProjectiles = projectileManager;
            if (liveProjectiles == null) {
                return; // the projectile system is not wired (the test rig)
            }
            // getHeadX/getHeadY/getHeadZ (lines 342-364).
            double headX;
            double headY;
            double headZ;
            if (head <= 1) {
                // The reference's headIndex <= 0 reads the body's own x/z
                // (the center head); the engine's head 1 is the center.
                headX = mob.position().x();
                headY = mob.position().y() + 3.0;
                headZ = mob.position().z();
            } else {
                double angle = Math.toRadians(mob.bodyYawLive() + 180 * (head - 2));
                headX = mob.position().x() + Math.cos(angle) * 1.3;
                headY = mob.position().y() + 2.2;
                headZ = mob.position().z() + Math.sin(angle) * 1.3;
            }
            liveProjectiles.launchWitherSkull(mob.entityId(),
                    new Position(headX, headY, headZ),
                    targetX - headX, targetY - headY, targetZ - headZ, charged);
        }

        /**
         * The sweep (mobAiTick lines 285-312): the 3x3x4 around the feet —
         * x/z plus/minus one, y from the feet up four — every non-air
         * destructible block breaks with its drops ({@code breakBlock(pos,
         * true)} — bedrock, the end portal and its frame, the command
         * block, and the barrier excepted, canDestroy lines 320-326); the
         * mobGriefing rule gates the whole walk (line 287) and the 1012
         * break sound rides the mark when at least one fell.
         */
        @Override
        public void onWitherSweep(net.zaminmc.torch.server.entity.MobEntity mob) {
            if (!gameRules.getBoolean("mobGriefing")) {
                return; // the no-grief world never breaks a block
            }
            int feetY = (int) Math.floor(mob.position().y());
            int centerX = (int) Math.floor(mob.position().x());
            int centerZ = (int) Math.floor(mob.position().z());
            boolean anyFell = false;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 3; dy++) {
                        BlockPosition at = new BlockPosition(
                                centerX + dx, feetY + dy, centerZ + dz);
                        var type = world.getBlock(at);
                        if (type.equals(world.airType()) || !witherCanDestroy(type)) {
                            continue;
                        }
                        world.setBlock(at, world.airType());
                        anyFell = true;
                        // breakBlock(pos, true): the block's own no-tool
                        // drop walk under the doTileDrops rule.
                        if (gameRules.getBoolean("doTileDrops")
                                && witherSweepDrops != null) {
                            for (net.zaminmc.torch.item.ItemStack drop
                                    : witherSweepDrops.dropsFor(type, null)) {
                                itemEntities.spawnDropAtBlock(new Position(
                                        at.x() + 0.5, at.y() + 0.5, at.z() + 0.5),
                                        drop, net.zaminmc.torch.server.entity.ItemEntity
                                                .PICKUP_DELAY_DROP_TICKS);
                            }
                        }
                    }
                }
            }
            if (anyFell) {
                mob.markWitherBlockBreakSound(); // line 308's flag
            }
        }

        /** canDestroy (lines 320-326): the unbreakable band excepts five. */
        private boolean witherCanDestroy(net.zaminmc.torch.block.BlockType type) {
            String id = type.identifier().toString();
            return !id.equals("minecraft:bedrock")
                    && !id.equals("minecraft:end_portal")
                    && !id.equals("minecraft:end_portal_frame")
                    && !id.equals("minecraft:command_block")
                    && !id.equals("minecraft:barrier");
        }

        /** The head Ints' dirty resend (the wire's own listener). */
        @Override
        public void onWitherMetadataChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWitherMetadataChanged(mob);
            }
        }

        /** The 1012 chunk break sound. */
        @Override
        public void onWitherBlockBreakSound(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWitherBlockBreakSound(mob);
            }
        }

        /** The 1014 launch sound at the body. */
        @Override
        public void onWitherShootSound(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWitherShootSound(mob);
            }
        }

        /** The global world events (the 1013's own broadcast family). */
        @Override
        public void onWorldGlobalEventAt(Position position, int eventId) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWorldGlobalEventAt(position, eventId);
            }
        }

        // ------------------------------------------------------------------
        // The ender dragon's engine arms (Slice 63 — the contact sweep, the
        // block sweep, the death walk's events)
        // ------------------------------------------------------------------

        /**
         * The contact arms (mobTick lines 264-268 + flingMobs/damageMobs
         * lines 355-377): the wing hulls grown 4/2/4 moved -2y fling every
         * living body of the dragon's dimension (the {@code d/d4 * 4}
         * radial push with the 0.2 lift), the head hull grown 1 damages
         * them (the flat 10.0 through the engine's own arms).
         */
        @Override
        public void onDragonContact(net.zaminmc.torch.server.entity.MobEntity mob) {
            var head = mob.dragonHead();
            var body = mob.dragonParts()[1];
            var rightWing = mob.dragonParts()[5];
            var leftWing = mob.dragonParts()[6];
            if (head == null) {
                return;
            }
            int dimension = mob.dimension();
            // flingMobs: the body-center radial (the reference's own
            // minX+maxX/2 anchor), the d2²+d3²-scaled push.
            double centerX = body.x();
            double centerZ = body.z();
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        || player.dimension() != dimension) {
                    continue;
                }
                Position p = player.position();
                if (wingHullContains(rightWing, p) || wingHullContains(leftWing, p)) {
                    double dx = p.x() - centerX;
                    double dz = p.z() - centerZ;
                    double d4 = dx * dx + dz * dz;
                    if (d4 > 0.0001) {
                        publishKnockback(player, dx / d4 * 4.0, 0.2, dz / d4 * 4.0);
                    }
                }
                if (partHullContains(head, p, 1.0)) {
                    damageOnTick(player, 10.0f, "was slain by Ender Dragon",
                            DamageKind.MELEE);
                }
            }
            for (MobEntity other : mobManager.all()) {
                if (other == mob || other.dead() || other.dimension() != dimension) {
                    continue;
                }
                Position p = other.position();
                if (wingHullContains(rightWing, p) || wingHullContains(leftWing, p)) {
                    double dx = p.x() - centerX;
                    double dz = p.z() - centerZ;
                    double d4 = dx * dx + dz * dz;
                    if (d4 > 0.0001) {
                        other.addVelocity(dx / d4 * 4.0, 0.2, dz / d4 * 4.0);
                    }
                }
                if (partHullContains(head, p, 1.0)) {
                    mobManager.hurt(other, 10.0f, mob.rotation().yaw(),
                            net.zaminmc.torch.event.DamageCause.MELEE);
                }
            }
        }

        /** The wing hull (grown 4/2/4, moved -2y — lines 265-266). */
        private boolean wingHullContains(
                net.zaminmc.torch.server.entity.MobEntity.DragonPart wing, Position p) {
            return partHullContains(wing, p, 4.0, 2.0, 4.0, -2.0);
        }

        /** The plain part hull with the uniform grow. */
        private boolean partHullContains(
                net.zaminmc.torch.server.entity.MobEntity.DragonPart part, Position p,
                double grow) {
            return partHullContains(part, p, grow, grow, grow, 0.0);
        }

        /** The grown/moved hull containment (Box.grown + moved). */
        private boolean partHullContains(
                net.zaminmc.torch.server.entity.MobEntity.DragonPart part, Position p,
                double growX, double growY, double growZ, double moveY) {
            double half = part.width / 2.0;
            return p.x() >= part.x() - half - growX && p.x() <= part.x() + half + growX
                    && p.y() >= part.y() - growY + moveY
                    && p.y() <= part.y() + part.height + growY + moveY
                    && p.z() >= part.z() - half - growZ && p.z() <= part.z() + half + growZ;
        }

        /**
         * The block sweep (destroyBlocks lines 417-456): the head and body
         * hulls over the End world — everything but the immune five breaks
         * under mobGriefing; an immune block met restricts the flight.
         */
        @Override
        public void onDragonSweep(net.zaminmc.torch.server.entity.MobEntity mob) {
            boolean restricted = false;
            if (gameRules.getBoolean("mobGriefing")) {
                restricted = dragonDestroyBlocks(mob.dragonHead())
                        | dragonDestroyBlocks(mob.dragonParts()[1]);
            } else {
                // The no-grief world still reads the immune blocks (the
                // reference's walk breaks nothing but the flag still lands).
                restricted = dragonTouchesImmune(mob.dragonHead())
                        | dragonTouchesImmune(mob.dragonParts()[1]);
            }
            mob.dragonSetRestrictMovement(restricted);
        }

        /** One hull's destruction walk (destroyBlocks' own shape). */
        private boolean dragonDestroyBlocks(
                net.zaminmc.torch.server.entity.MobEntity.DragonPart part) {
            if (part == null) {
                return false;
            }
            int minX = (int) Math.floor(part.x() - part.width / 2.0);
            int minY = (int) Math.floor(part.y());
            int minZ = (int) Math.floor(part.z() - part.width / 2.0);
            int maxX = (int) Math.floor(part.x() + part.width / 2.0);
            int maxY = (int) Math.floor(part.y() + part.height);
            int maxZ = (int) Math.floor(part.z() + part.width / 2.0);
            boolean immune = false;
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPosition at = new BlockPosition(x, y, z);
                        var type = endWorld.getBlock(at);
                        if (type.equals(endWorld.airType())) {
                            continue;
                        }
                        if (dragonImmune(type)) {
                            immune = true;
                        } else {
                            endWorld.setBlock(at, endWorld.airType());
                        }
                    }
                }
            }
            return immune;
        }

        /** The no-grief immune probe (the flag without the break). */
        private boolean dragonTouchesImmune(
                net.zaminmc.torch.server.entity.MobEntity.DragonPart part) {
            if (part == null) {
                return false;
            }
            int minX = (int) Math.floor(part.x() - part.width / 2.0);
            int minY = (int) Math.floor(part.y());
            int minZ = (int) Math.floor(part.z() - part.width / 2.0);
            int maxX = (int) Math.floor(part.x() + part.width / 2.0);
            int maxY = (int) Math.floor(part.y() + part.height);
            int maxZ = (int) Math.floor(part.z() + part.width / 2.0);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (dragonImmune(endWorld.getBlock(new BlockPosition(x, y, z)))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        /** destroyBlocks' immune five (lines 433-437). */
        private boolean dragonImmune(BlockType type) {
            String id = type.identifier().toString();
            return id.equals("minecraft:barrier")
                    || id.equals("minecraft:obsidian")
                    || id.equals("minecraft:end_stone")
                    || id.equals("minecraft:bedrock")
                    || id.equals("minecraft:command_block");
        }

        /** The crystal-pop wound's hurt fan (the wire's own listener). */
        @Override
        public void onDragonCrystalWound(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobHurt(mob);
            }
        }

        /** The death walk's first tick: the GLOBAL 1018. */
        @Override
        public void onDragonDeathSound(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onWorldGlobalEventAt(mob.position(), 1018);
            }
        }

        /** The XP batch (the orb burst at the body). */
        @Override
        public void onDragonXpBatch(net.zaminmc.torch.server.entity.MobEntity mob, int amount) {
            if (experienceOrbs != null) {
                experienceOrbs.spawnBurst(mob.position(), amount, Integer.MAX_VALUE);
            }
        }

        /**
         * The death walk's end (createPortal lines 548-585 + remove): the
         * bedrock fountain with the exit portal floor and the egg, then
         * the body leaves the world (the XP batch of the same tick lands
         * first — the reference's own order).
         */
        @Override
        public void onDragonPortal(net.zaminmc.torch.server.entity.MobEntity mob) {
            int baseX = (int) Math.floor(mob.position().x());
            int baseZ = (int) Math.floor(mob.position().z());
            int baseY = 64; // the reference's own fixed anchor (line 540)
            BlockType bedrock = blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:bedrock"));
            BlockType air = endWorld.airType();
            BlockType endPortal = blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:end_portal"));
            BlockType torch = blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:torch"));
            BlockType egg = blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:dragon_egg"));
            for (int dy = -1; dy <= 32; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        int d2 = dx * dx + dz * dz;
                        if (d2 > 12.25) {
                            continue;
                        }
                        BlockPosition at = new BlockPosition(baseX + dx, baseY + dy, baseZ + dz);
                        if (dy < 0) {
                            if (d2 <= 6.25) {
                                endWorld.setBlock(at, bedrock);
                            }
                        } else if (dy > 0) {
                            endWorld.setBlock(at, air);
                        } else if (d2 > 6.25) {
                            endWorld.setBlock(at, bedrock);
                        } else {
                            endWorld.setBlock(at, endPortal);
                        }
                    }
                }
            }
            // The center pillar (lines 575-584): bedrock y0..y3 with the
            // four torches at y2 and the egg on top. (The reference's torch
            // FACING states face INWARD; the engine's single-state torch
            // carries the wall-mount metadata in a later slice — ledgered.)
            BlockPosition center = new BlockPosition(baseX, baseY, baseZ);
            endWorld.setBlock(center, bedrock);
            endWorld.setBlock(center.offset(0, 1, 0), bedrock);
            endWorld.setBlock(center.offset(0, 2, 0), bedrock);
            endWorld.setBlock(center.offset(-1, 2, 0), torch);
            endWorld.setBlock(center.offset(1, 2, 0), torch);
            endWorld.setBlock(center.offset(0, 2, -1), torch);
            endWorld.setBlock(center.offset(0, 2, 1), torch);
            endWorld.setBlock(center.offset(0, 3, 0), bedrock);
            endWorld.setBlock(center.offset(0, 4, 0), egg);
            // The dragon never comes back (the defeated mark — the
            // deterministic regenerate would re-spawn it every boot).
            dragonDefeated = true;
            if (dragonStore != null) {
                dragonStore.save(true);
            }
            mobManager.remove(mob, "died");
        }

        /**
         * The silverfish's wake-up scan (Slice 18c —
         * WakeUpFriendsGoal.tick lines 210-236): the reference's own
         * interleaved box walk (the vertical ±5 pair, the horizontal ±10
         * pair — the (0,0,0) center first) breaking every monster egg it
         * finds; each break spawns its egg's silverfish, and the walk
         * stops early on the reference's own 1-in-2 roll per egg.
         */
        @Override
        public void onSilverfishWake(net.zaminmc.torch.server.entity.MobEntity mob) {
            int cx = (int) Math.floor(mob.position().x());
            int cy = (int) Math.floor(mob.position().y());
            int cz = (int) Math.floor(mob.position().z());
            for (int dy = 0; dy <= 5 && dy >= -5; dy = dy <= 0 ? 1 - dy : -dy) {
                for (int dx = 0; dx <= 10 && dx >= -10; dx = dx <= 0 ? 1 - dx : -dx) {
                    for (int dz = 0; dz <= 10 && dz >= -10; dz = dz <= 0 ? 1 - dz : -dz) {
                        BlockPosition at = new BlockPosition(cx + dx, cy + dy, cz + dz);
                        var type = world.getBlock(at);
                        if (net.zaminmc.torch.server.block.InfestedBlocks.isInfested(
                                type.identifier())) {
                            // mobGriefing (Slice 24a — WakeUpFriendsGoal.tick
                            // lines 222-227): under the rule the egg breaks
                            // (its silverfish bursts with it); a no-grief
                            // world SILENTLY converts the egg back to its
                            // host stone — no break, no fish — the walk may
                            // still stop on the coin.
                            if (!gameRules.getBoolean("mobGriefing")) {
                                var host = net.zaminmc.torch.server.block.InfestedBlocks
                                        .hostOf(type.identifier());
                                if (host != null) {
                                    world.setBlock(at, blockRegistry.require(host));
                                }
                                if (gameplayRandom.nextBoolean()) {
                                    return; // the reference's early stop
                                }
                                continue;
                            }
                            // breakBlock(pos, true): the egg leaves (its
                            // silverfish bursts with it), the walk may stop.
                            // The world's own commit path carries the wire
                            // resync and the dig feedback.
                            world.setBlock(at, world.airType());
                            spawnSilverfishAt(new Position(at.x() + 0.5, at.y(), at.z() + 0.5));
                            if (gameplayRandom.nextBoolean()) {
                                return; // the reference's early stop
                            }
                        }
                    }
                }
            }
        }

        /**
         * The silverfish's infest (Slice 18c — InfestStoneGoal.start lines
         * 171-187): the adjacent host becomes its egg and the body leaves
         * the world (the reference's animateSpawn + remove; the client's
         * own smoke burst rides the entity's removal).
         */
        @Override
        public void onSilverfishInfest(net.zaminmc.torch.server.entity.MobEntity mob) {
            BlockPosition at = new BlockPosition(mob.silverfishInfestX(),
                    mob.silverfishInfestY(), mob.silverfishInfestZ());
            var type = world.getBlock(at);
            net.zaminmc.torch.util.Identifier egg =
                    net.zaminmc.torch.server.block.InfestedBlocks.eggFor(type.identifier());
            if (egg == null) {
                return; // the host changed under the roll: no infest
            }
            world.setBlock(at, blockRegistry.require(egg));
            if (mobManager != null) {
                mobManager.remove(mob, "infested");
            }
        }

        /**
         * The enderman's pickup (Slice 19d — PickUpBlockGoal.tick lines
         * 452-465): the world's block leaves for the body's hands — the
         * write lands, the carry completes, the wire's state flips. The
         * host re-check keeps the intent honest (the roll's read and the
         * write share the same tick lane, but a crossed write rolls
         * empty-handed).
         */
        @Override
        public void onEndermanPickup(net.zaminmc.torch.server.entity.MobEntity mob) {
            BlockPosition at = new BlockPosition(mob.endermanPickupX(),
                    mob.endermanPickupY(), mob.endermanPickupZ());
            var type = world.getBlock(at);
            if (type.identifier().value().equals("air")) {
                return; // the cell changed under the roll
            }
            mob.completeEndermanPickup(type);
            world.setBlock(at, blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
        }

        /**
         * The enderman's place (Slice 19d — PlaceBlockGoal.tick lines
         * 483-496): the carried block lands at the intent's cell — the
         * re-check keeps the air-and-support gate honest.
         */
        @Override
        public void onEndermanPlace(net.zaminmc.torch.server.entity.MobEntity mob) {
            BlockPosition at = new BlockPosition(mob.endermanPlaceX(),
                    mob.endermanPlaceY(), mob.endermanPlaceZ());
            var type = world.getBlock(at);
            var below = world.getBlock(at.offset(0, -1, 0));
            if (!type.identifier().value().equals("air")
                    || below.identifier().value().equals("air")) {
                return; // the cell changed under the roll
            }
            var carried = mob.endermanCarried();
            if (carried == null) {
                return;
            }
            world.setBlock(at, carried);
            mob.completeEndermanPlace();
        }

        /**
         * The enderman's state flip (Slice 19d): the carried short + the
         * screaming byte republish through the adapter's fan-out.
         */
        @Override
        public void onEndermanStateChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onEndermanStateChanged(mob);
            }
        }

        /**
         * The rabbit's garden eat (Slice 21d — RaidFarmGoal.tick lines
         * 566-579): the age-7 carrots vanish (the air write with the
         * neighbor updates — the reference's own setBlockState(air, 2) +
         * breakBlock pair), the block-dust burst pops at the eaten cell
         * (onEatFromGarden's BLOCK_DUST over the carrot state), and the
         * hunger clock refills to 100.
         */
        @Override
        public void onRabbitFarmEat(net.zaminmc.torch.server.entity.MobEntity rabbit) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onRabbitFarmEat(rabbit);
            }
            BlockPosition at = new BlockPosition(rabbit.rabbitFarmEatX(),
                    rabbit.rabbitFarmEatY(), rabbit.rabbitFarmEatZ());
            var type = world.getBlock(at);
            if (!type.identifier().value().equals("carrots_stage7")) {
                rabbit.completeRabbitFarmEat(); // the cell changed: the
                // clock still refills (the reference's own tick order —
                // the eat landed, the world just moved on)
                return;
            }
            world.setBlock(at, blockRegistry.require(
                    net.zaminmc.torch.util.Identifier.parse("minecraft:air")));
            // onEatFromGarden's particle burst (the carrot block's own
            // dust at the body's band).
            fxManager.blockShatter(new Position(at.x() + 0.5, at.y() + 0.5, at.z() + 0.5),
                    blockRegistry.require(
                            net.zaminmc.torch.util.Identifier.parse("minecraft:carrots_stage7")));
            rabbit.completeRabbitFarmEat();
        }

        /**
         * The snow golem's volley (Slice 17d — SnowGolemEntity.
         * doRangedAttack lines 89-96): the snowball launches from the
         * golem's own height, aimed at the target's eye band pulled to the
         * 1.1 mark — the reference's own {@code dispense(d1, d2 + f, d3,
         * 1.6F, 12.0F)} with the horizontal-distance rise.
         */
        @Override
        public void onMobRangedAttackMob(MobEntity shooter, MobEntity target) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobRangedAttackMob(shooter, target);
            }
            Position origin = new Position(shooter.position().x(),
                    shooter.position().y() + 1.7, shooter.position().z());
            double d0 = target.position().y() + target.bodyHeight() - 1.1;
            double d1 = target.position().x() - origin.x();
            double d2 = d0 - origin.y();
            double d3 = target.position().z() - origin.z();
            double horizontal = Math.sqrt(d1 * d1 + d3 * d3);
            double rise = horizontal * 0.2; // the dispense f term
            double dx = d1;
            double dy = d2 + rise;
            double dz = d3;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            projectileManager.launch(ProjectileEntity.Kind.SNOWBALL,
                    shooter.entityId(), origin, yaw, pitch, 1.6);
            fxManager.sound(shooter.position(), "random.bow", 1.0f,
                    1.0f / (fxRandom.nextFloat() * 0.4f + 0.8f));
        }

        /**
         * The guardian's laser completed (Slice 17a — the charged beam's
         * hit, GuardianAttackEntityGoal.tick lines 508-522): the magic half
         * pierces armor with no knockback ({@code DamageSource.magic}), the
         * melee half walks the full armor/enchantment ladder
         * ({@code DamageSource.mob} — the attack-damage attribute's own).
         */
        @Override
        public void onMobGuardianLaserHit(MobEntity mob, PlayerSession target,
                                           float magicDamage, float meleeDamage) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobGuardianLaserHit(mob, target, magicDamage, meleeDamage);
            }
            String cause = "was slain by " + titledMobName(mob);
            // The hard-difficulty bump (+2.0F on the magic half — the
            // reference's own arm; the difficulty id: 3 = hard).
            float magic = magicDamage
                    + (difficulty() == 3 ? GUARDIAN_MAGIC_HARD_BONUS : 0.0f);
            if (magic > 0.0f) {
                // The magic pierce: no knockback, no armor shave (the
                // potion family's own arm).
                damageOnTick(target, magic, true, cause, 0.0f,
                        net.zaminmc.torch.event.DamageCause.MAGIC);
            }
            // The melee half: the armor + enchantment ladder, the melee
            // exhaustion — the onMobAttackedPlayer walk's own shape.
            ItemStack[] thornsRow = target.inventory().armorStacks();
            damageOnTick(target,
                    applyEnchantProtection(target, applyArmor(target, meleeDamage),
                            DamageKind.MELEE),
                    cause, DamageKind.MELEE);
            applyThornsWildcard(target, thornsRow, new ThornsTarget() {
                @Override
                public void takeThornsDamage(int amount, PlayerSession wearer) {
                    if (mobManager != null) {
                        mob.setLootingLevel(EnchantmentHelper.lootingLevel(
                                wearer.inventory().held()));
                        if (mobManager.hurt(mob, amount, wearer.rotation().yaw())) {
                            // The thorns wound's attacker is the wearing
                            // player — the hit window arms (takeDamage
                            // lines 678-680).
                            mob.markPlayerHit();
                        }
                    }
                }

                @Override
                public Position position() {
                    return mob.position();
                }
            });
        }

        /**
         * The elder's aura cycle (Slice 17a — mobAiTick lines 354-374): the
         * survival players within 50 blocks take the Mining Fatigue III
         * curse (6000 ticks) — only the weaker rows (amplifier under 2 or
         * duration under 1200) — each preceded by the ghost-curse game
         * state (event 10).
         */
        @Override
        public void onMobGuardianAura(MobEntity elder) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobGuardianAura(elder);
            }
            if (potionSystem == null) {
                return;
            }
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        || player.gamemode() != GameMode.SURVIVAL) {
                    continue; // the reference's interactionManager.isSurvival gate
                }
                double dx = player.position().x() - elder.position().x();
                double dy = player.position().y() - elder.position().y();
                double dz = player.position().z() - elder.position().z();
                if (dx * dx + dy * dy + dz * dz >= ELDER_AURA_RADIUS_SQUARED) {
                    continue;
                }
                var existing = potionSystem.get(player,
                        net.zaminmc.torch.server.potion.StatusEffects.MINING_FATIGUE);
                if (existing != null && existing.amplifier() >= ELDER_AURA_AMPLIFIER
                        && existing.duration() >= ELDER_AURA_REFRESH_BELOW) {
                    continue; // the stronger row stays
                }
                // GameEventS2CPacket(10, 0.0F): the elder-guardian ghost
                // curse overlay the client renders.
                var link = player.link();
                if (link != null) {
                    link.playGameState(GAME_STATE_ELDER_GUARDIAN_CURSE, 0.0f);
                }
                potionSystem.add(player, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                        net.zaminmc.torch.server.potion.StatusEffects.MINING_FATIGUE,
                        ELDER_AURA_DURATION, ELDER_AURA_AMPLIFIER));
            }
        }

        @Override
        public void onMobFuseChanged(MobEntity mob, boolean priming) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobFuseChanged(mob, priming);
            }
        }

        @Override
        public void onMobSheared(MobEntity mob, int woolCount) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSheared(mob, woolCount);
            }
        }

        @Override
        public void onMobCoatRegrown(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobCoatRegrown(mob);
            }
        }

        @Override
        public void onMobLoveBurst(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobLoveBurst(mob);
            }
        }

        @Override
        public void onMobAmbientHearts(MobEntity mob, Position at,
                                       double vx, double vy, double vz) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobAmbientHearts(mob, at, vx, vy, vz);
            }
        }

        @Override
        public void onMobGrewUp(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobGrewUp(mob);
            }
        }

        @Override
        public void onMobBred(MobEntity parent, MobEntity mate, MobEntity baby) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobBred(parent, mate, baby);
            }
            // The breed reward (AnimalBreedGoal.breed under doMobLoot —
            // Slice 24a: the rule gates this burst too):
            // random.nextInt(7) + 1 orbs at the parent.
            if (!gameRules.getBoolean("doMobLoot")) {
                return;
            }
            experienceOrbs.spawnBurst(parent.position(),
                    1 + gameplayRandom.nextInt(7), 1);
        }

        @Override
        public void onMobExploded(MobEntity mob) {
            explodeOnTick(mob); // the blast is the engine's: blocks, damage, wire
        }

        @Override
        public void onMobMounted(MobEntity mob, int riderEngineId) {
            PlayerSession rider = sessionByEngineId(riderEngineId);
            if (rider != null) {
                rider.setRidingMobId(mob.entityId());
            }
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobMounted(mob, riderEngineId);
            }
        }

        @Override
        public void onMobDismounted(MobEntity mob, int riderEngineId,
                                    Position exit, boolean thrown) {
            PlayerSession rider = sessionByEngineId(riderEngineId);
            if (rider != null) {
                rider.setRidingMobId(-1);
                // The body steps out beside the mount (the re-anchor rides
                // the wire listener's position-and-look; the buck throw pops).
                rider.applyMovement(exit, rider.rotation(), true);
                rider.setGraceTicks(40);
            }
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobDismounted(mob, riderEngineId, exit, thrown);
            }
        }

        @Override
        public void onHorseFlagsChanged(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onHorseFlagsChanged(mob);
            }
        }
    }

    /**
     * The witch's ranged attack (WitchEntity.doRangedAttack lines 194-215):
     * the splash potion with the reference's own choice ladder — the far
     * target without slowness gets slowness, the healthy target without
     * poison gets poison, the close target without weakness may get
     * weakness, everything else gets the strong harming default. The
     * trajectory is the reference's own {@code dispense(d1, d2 + f * 0.2F,
     * d3, 0.75F, 8.0F)}: the direction triple measures from the witch's
     * FEET (d0 - this.y), the spawn point is the reference's own
     * ThrownEntity constructor seat (the eye height 1.62, the 0.1 dip,
     * the 0.16 perpendicular hand offset — the vanilla quirk the
     * construction applies along the yaw). The throw itself is silent —
     * the reference plays no bow draw for the witch (the splash's 2002
     * burst is the tell). The target's velocity lead-ahead (target.x +
     * velocityX) rides the engine's client-authoritative movement model:
     * the session carries no velocity read (a documented difference).
     */
    private void witchRangedAttack(MobEntity mob, Position aimPoint) {
        if (mob.witchDrinking()) {
            return; // the reference's gate: no throw while drinking
        }
        // The spawn seat (ThrownEntity's thrower constructor): the eye
        // height 1.62 (WitchEntity's own), dipped 0.1, the 0.16 hand
        // offset along the yaw's cosine/sine — the vanilla perpendicular.
        double yawRadians = Math.toRadians(mob.yaw());
        Position feet = mob.position();
        Position origin = new Position(
                feet.x() - Math.cos(yawRadians) * 0.16,
                feet.y() + 1.62 - 0.1,
                feet.z() - Math.sin(yawRadians) * 0.16);
        // The target resolves as the nearest player to the AI's aim point
        // (the AI ticks the aim at the target's own position).
        PlayerSession target = null;
        double best = 4.0; // within two blocks of the aim
        for (PlayerSession candidate : players.all()) {
            if (candidate.state() != net.zaminmc.torch.entity.PlayerState.PLAYING
                    || candidate.dead()) {
                continue;
            }
            double distance = candidate.position().distanceSquared(aimPoint);
            if (distance < best) {
                best = distance;
                target = candidate;
            }
        }
        // The reference's choice ladder (lines 204-212): the horizontal
        // measures feet-to-feet exactly like the reference's own d1/d3.
        int metadata = 32732; // the strong harming default
        double d1 = aimPoint.x() - feet.x();
        double d2 = (aimPoint.y() + 1.62 - 1.1) - feet.y(); // the chest aim
        double d3 = aimPoint.z() - feet.z();
        float horizontal = (float) Math.sqrt(d1 * d1 + d3 * d3);
        if (target != null) {
            if (horizontal >= 8.0F && !potionSystem.hasEffect(target,
                    net.zaminmc.torch.server.potion.StatusEffects.SLOWNESS)) {
                metadata = 32698; // the slowness splash
            } else if (target.health() >= 8.0F && !potionSystem.hasEffect(target,
                    net.zaminmc.torch.server.potion.StatusEffects.POISON)) {
                metadata = 32660; // the poison splash
            } else if (horizontal <= 3.0F && !potionSystem.hasEffect(target,
                    net.zaminmc.torch.server.potion.StatusEffects.WEAKNESS)
                    && mobManager.random().nextFloat() < 0.25F) {
                metadata = 32696; // the weakness splash
            }
        }
        ProjectileEntity potion = projectileManager.launchVector(
                ProjectileEntity.Kind.POTION, mob.entityId(), origin,
                d1, d2 + horizontal * 0.2F, d3, 0.75, 8.0);
        if (potion != null) {
            potion.setPotionDamage(metadata);
        }
    }

    private void publishMobSwing(MobEntity mob) {
        for (MobSwingObserver observer : swingObservers) {
            observer.onMobSwing(mob);
        }
    }

    /** The wire-side animation observer (the adapter registers one). */
    public interface MobSwingObserver {
        void onMobSwing(MobEntity mob);
    }

    /** Registers an internal swing-animation observer (the protocol adapter). */
    public void addMobSwingObserver(MobSwingObserver observer) {
        swingObservers.add(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * The entity-animation codes the attack walk broadcasts: the crit burst
     * and the magic-crit burst (the reference's EntityAnimationS2CPacket
     * codes 4 and 5 — ServerPlayerEntity.addCritParticles line 795 and
     * addEnchantedCritParticles line 800). Kept in the engine because the
     * attack verdicts decide when they fire; the adapter maps them to the
     * wire.
     */
    public static final int ANIMATION_CRIT = 4;
    public static final int ANIMATION_MAGIC_CRIT = 5;

    /** The attack crit bursts: the adapter renders them as Animation 0x0B. */
    public interface EntityAnimationObserver {
        /** The burst on a mob target (the wire-global mob id). */
        void onMobAnimation(MobEntity mob, int animation);

        /** The burst on a player target (resolved per observer id space). */
        void onPlayerAnimation(PlayerSession player, int animation);
    }

    private final java.util.List<EntityAnimationObserver> entityAnimationObservers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an internal entity-animation observer (the protocol adapter). */
    public void addEntityAnimationObserver(EntityAnimationObserver observer) {
        entityAnimationObservers.add(Objects.requireNonNull(observer, "observer"));
    }

    /** Broadcasts a crit-family burst on a mob target (tick thread). */
    private void publishMobAnimation(MobEntity mob, int animation) {
        for (EntityAnimationObserver observer : entityAnimationObservers) {
            observer.onMobAnimation(mob, animation);
        }
    }

    /** Broadcasts a crit-family burst on a player target (tick thread). */
    private void publishPlayerAnimation(PlayerSession player, int animation) {
        for (EntityAnimationObserver observer : entityAnimationObservers) {
            observer.onPlayerAnimation(player, animation);
        }
    }

    /**
     * The world queries a mob's mind and physics need, answered from the
     * world the tick thread owns. Called only on the simulation context.
     */
    private final class MobWorldQuery implements MobEntity.WorldQuery {
        /**
         * The active dimension (Slice 25d — the reference's Entity.world:
         * every body reads its own world). The manager's tick loop flips
         * this before each body's walk; the reads resolve through it.
         * Tick-lane only (the manager's own context).
         */
        private int activeDimension = 0;

        /** The dimension context switch (the manager's per-body flip). */
        @Override
        public void setActiveDimension(int dimension) {
            this.activeDimension = dimension;
        }

        /** The world the active dimension lives in (the nether's stand-in: the overworld when unbooted). */
        private EngineWorld activeWorld() {
            return activeDimension == -1 && netherWorld != null ? netherWorld
                    : activeDimension == 1 && endWorld != null ? endWorld : world;
        }

        /** The dimension-aware solidity (the overworld helpers stay for the dimension-blind systems). */
        private boolean solidInDimension(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return false;
            }
            return WorldSolidity.isSolid(activeWorld().getBlock(blockAt(x, y, z)));
        }

        private boolean solidAtPointInDimension(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return false;
            }
            return WorldSolidity.isSolidAt(activeWorld().getBlock(blockAt(x, y, z)),
                    x, y, z, dimensionProbe(x, y, z));
        }

        private double supportYInDimension(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return Double.NEGATIVE_INFINITY;
            }
            return WorldSolidity.supportY(activeWorld().getBlock(blockAt(x, y, z)),
                    x, y, z, dimensionProbe(x, y, z));
        }

        /**
         * The neighbour probe the connection families resolve against (the
         * collision-shape slice): the active dimension's own block reads at
         * the offsets from the queried cell.
         */
        private CollisionShapes.NeighborProbe dimensionProbe(double x, double y, double z) {
            net.zaminmc.torch.block.BlockPosition origin = blockAt(x, y, z);
            return (dx, dy, dz) -> {
                net.zaminmc.torch.block.BlockPosition at = origin.offset(dx, dy, dz);
                if (at.y() < net.zaminmc.torch.block.BlockPosition.MIN_Y
                        || at.y() > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                    return null;
                }
                return activeWorld().getBlock(at);
            };
        }

        private net.zaminmc.torch.block.BlockType blockInDimensionBoundsSafe(
                double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return world.airType();
            }
            return activeWorld().getBlock(blockAt(x, y, z));
        }

        /** The dimension-aware fluid read. */
        private boolean fluidInDimension(double x, double y, double z) {
            if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return false;
            }
            return FluidBlocks.kindOf(
                    activeWorld().getBlock(blockAt(x, y, z)).identifier()) != null;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return solidInDimension(x, y, z);
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return solidAtPointInDimension(x, y, z);
        }

        @Override
        public double supportY(double x, double y, double z) {
            return supportYInDimension(x, y, z);
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return fluidInDimension(x, y, z);
        }

        /**
         * The rain read (Slice 30d — World.isRainingAt's shape): the rain
         * flag up AND the point above the precipitation height at its
         * column (the first free cell above the surface, the lightning
         * seat's own consult). The nether never rains (no weather system
         * owns it — the rain flag lives on the overworld).
         */
        @Override
        public boolean isRainingAt(double x, double y, double z) {
            net.zaminmc.torch.server.weather.WeatherSystem weatherNow = weather;
            if (weatherNow == null || !weatherNow.raining() || activeWorld() != world) {
                return false;
            }
            int height = surfaceY((int) Math.floor(x), (int) Math.floor(z)) + 1;
            return y >= height;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return FluidBlocks.isLava(blockInDimensionBoundsSafe(x, y, z).identifier());
        }

        @Override
        public boolean inFire(double x, double y, double z) {
            return WorldSolidity.isFire(blockInDimensionBoundsSafe(x, y, z).identifier());
        }

        /**
         * The mobGriefing rule's live view (Slice 24a): the enderman's
         * carry pair and the rabbit's garden raid read it through the
         * body's own world query, exactly the reference's consult.
         */
        @Override
        public boolean mobGriefing() {
            return gameRules.getBoolean("mobGriefing");
        }

        /**
         * The zombie villager's cure accelerator (Slice 25a —
         * getTicksUntilConversionDecrement lines 549-559): the iron bars
         * and bed cells the 1%-roll scan counts, read from the body's own
         * dimension.
         */
        @Override
        public boolean isIronBarsOrBed(int x, int y, int z) {
            net.zaminmc.torch.util.Identifier id = blockInDimensionBoundsSafe(
                    x + 0.5, y + 0.5, z + 0.5).identifier();
            return id.toString().equals("minecraft:iron_bars")
                    || id.toString().equals("minecraft:bed");
        }

        @Override
        public net.zaminmc.torch.server.entity.ai.pathing.CellMaterial pathMaterialAt(int x, int y, int z) {
            return net.zaminmc.torch.server.entity.ai.pathing.PathBlocks.materialOf(
                    blockInDimensionBoundsSafe(x + 0.5, y + 0.5, z + 0.5));
        }

        /**
         * The block identity of the cell (Slice 18c — the silverfish's
         * InfestStone roll reads the adjacent block).
         */
        @Override
        public net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            return blockInDimensionBoundsSafe(x + 0.5, y + 0.5, z + 0.5).identifier();
        }

        @Override
        public boolean touchingCactus(double x, double y, double z) {
            int by = (int) Math.floor(y);
            return WorldSolidity.isCactus(blockInDimensionBoundsSafe(x, y, z).identifier())
                    || WorldSolidity.isCactus(
                            blockInDimensionBoundsSafe(x, by - 1.0, z).identifier());
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            // March the segment at half-block steps; one solid sample blocks
            // the shot (the cheap shooter's ray, exact enough for 1.8 eyes).
            double dx = to.x() - from.x();
            double dy = to.y() - from.y();
            double dz = to.z() - from.z();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = (int) Math.ceil(length * 2.0);
            for (int i = 1; i < steps; i++) {
                double t = i / (double) steps;
                if (isSolid(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            PlayerSession best = null;
            double bestDistance = range * range;
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        // Slice 25d: the body reads its own world's players
                        // (the same coords exist in both dimensions).
                        || player.dimension() != activeDimension) {
                    continue;
                }
                Position p = player.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best == null ? null : best.position();
        }

        /**
         * The nearest live mob of the kind (ActiveTargetGoal's scan /
         * FleeEntityGoal's scare read, Slice 16b): the mob-vs-mob target
         * family's world walk over the manager's live list.
         */
        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range,
                                           net.zaminmc.torch.server.entity.MobType kind) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobManager.all()) {
                if (mob.dead() || mob.type() != kind
                        // Slice 25d: the scan reads its own dimension's mobs.
                        || mob.dimension() != activeDimension) {
                    continue;
                }
                Position p = mob.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }

        /**
         * The golem family's hostile scan (Slice 17d —
         * TrackIronGolemTargetGoal + the snow golem's Monster filter): the
         * nearest live hostile mob, the golem's own kind excluded from the
         * golem family itself (a snow golem never hunts another golem —
         * GolemEntity is not a Monster in the reference).
         */
        @Override
        public MobEntity nearestHostileMob(double x, double y, double z, double range) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobManager.all()) {
                if (mob.dead() || !mob.type().hostile
                        // Slice 25d: the scan reads its own dimension's mobs.
                        || mob.dimension() != activeDimension) {
                    continue;
                }
                Position p = mob.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }

        /**
         * The hot-biome read (Slice 17d — the snow golem's melt): the
         * engine's biome family carries exactly one hot biome (the desert,
         * the reference's temperature 2.0 > 1.0).
         */
        @Override
        public boolean biomeHotAt(double x, double y, double z) {
            return world.biomeAt(new BlockPosition(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)))
                    .equals("desert");
        }

        /**
         * The awareness goals' player view (TemptGoal's own read): the
         * nearest live player's position, rotation, and held item.
         */
        @Override
        public net.zaminmc.torch.server.entity.MobEntity.PlayerView nearestPlayerView(
                double x, double y, double z, double range) {
            PlayerSession best = null;
            double bestDistance = range * range;
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        // Slice 25d: the awareness goals read their own
                        // dimension's players.
                        || player.dimension() != activeDimension) {
                    continue;
                }
                Position p = player.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            if (best == null) {
                return null;
            }
            PlayerSession session = best;
            net.zaminmc.torch.item.ItemStack held = session.heldItem();
            String heldId = held.isEmpty() || held.type() == null
                    ? null : held.type().identifier().toString();
            return new net.zaminmc.torch.server.entity.MobEntity.PlayerView() {
                @Override public Position position() {
                    return session.position();
                }

                @Override public float yaw() {
                    return session.rotation().yaw();
                }

                @Override public float pitch() {
                    return session.rotation().pitch();
                }

                @Override public String heldItem() {
                    return heldId;
                }

                @Override public String helmetItem() {
                    // The armor head slot's identity (isLookedAtBy's pumpkin
                    // gate — the engine's armor order mirrors the
                    // historical inventory.armor[3]).
                    var helmet = session.inventory().armorAt(0);
                    return helmet == null || helmet.isEmpty() || helmet.type() == null
                            ? null : helmet.type().identifier().toString();
                }
            };
        }

        // --------------------------------------------------------------
        // The wither's quarry family (Slice 59 — the three-head model's
        // World.getEntity reads over the players and the mobs)
        // --------------------------------------------------------------

        /** The live player carrying the engine id, or null. */
        private PlayerSession playerById(int entityId) {
            for (PlayerSession player : players.all()) {
                if (player.state() == PlayerState.PLAYING && !player.dead()
                        && player.engineEntityId() == entityId) {
                    return player;
                }
            }
            return null;
        }

        /** The live mob carrying the engine id, or null. */
        private MobEntity mobById(int entityId) {
            for (MobEntity mob : mobManager.all()) {
                if (!mob.dead() && mob.entityId() == entityId) {
                    return mob;
                }
            }
            return null;
        }

        @Override
        public Integer entityById(int entityId) {
            // World.getEntity over the shared id band: null when no live
            // body carries the id (the callers treat null and dead alike).
            if (playerById(entityId) != null || mobById(entityId) != null) {
                return entityId;
            }
            return null;
        }

        @Override
        public Position entityPositionById(int entityId) {
            PlayerSession player = playerById(entityId);
            if (player != null) {
                return player.position();
            }
            MobEntity mob = mobById(entityId);
            return mob == null ? null : mob.position();
        }

        @Override
        public double eyeHeightOf(int entityId) {
            PlayerSession player = playerById(entityId);
            if (player != null) {
                return 1.62; // PlayerEntity.getEyeHeight
            }
            MobEntity mob = mobById(entityId);
            if (mob == null) {
                return 0.0;
            }
            // The mob's own eye model (the body's kind law).
            return mob.eyeHeightForQuery();
        }

        @Override
        public boolean entityInvulnerablePlayer(int entityId) {
            PlayerSession player = playerById(entityId);
            return player != null
                    && player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE;
        }

        @Override
        public int[] playerIds() {
            // chooseTarget lines 380-388: the world's players — the
            // engine's answer names every PLAYING body of the ACTIVE
            // dimension (the dragon's own world; the ambient dimension
            // tracks the dragon's walk).
            java.util.List<Integer> ids = new java.util.ArrayList<>();
            for (PlayerSession player : players.all()) {
                if (player.state() == PlayerState.PLAYING && !player.dead()
                        && player.dimension() == activeDimension) {
                    ids.add(player.engineEntityId());
                }
            }
            int[] out = new int[ids.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = ids.get(i);
            }
            return out;
        }

        @Override
        public boolean enderCrystalAliveAt(double x, double y, double z) {
            if (enderCrystals == null) {
                return false;
            }
            // The link's liveness probe: a standing crystal within the
            // half-block of the linked position (the engine's crystals are
            // position-keyed; the reference's id-keyed link reads the same
            // verdict through removed).
            for (var crystal : enderCrystals.all()) {
                if (Math.abs(crystal.x() - x) <= 0.5
                        && Math.abs(crystal.y() - y) <= 0.5
                        && Math.abs(crystal.z() - z) <= 0.5) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public double[] nearestEnderCrystal(double x, double y, double z, double range) {
            if (enderCrystals == null) {
                return null;
            }
            // tickEndCrystalInteraction lines 337-352: the grown box's
            // nearest walk.
            double[] best = null;
            double bestSquared = Double.MAX_VALUE;
            for (var crystal : enderCrystals.all()) {
                double dx = crystal.x() - x;
                double dy = crystal.y() - y;
                double dz = crystal.z() - z;
                if (Math.abs(dx) > range || Math.abs(dy) > range
                        || Math.abs(dz) > range) {
                    continue;
                }
                double squared = dx * dx + dy * dy + dz * dz;
                if (squared < bestSquared) {
                    bestSquared = squared;
                    best = new double[]{crystal.x(), crystal.y(), crystal.z()};
                }
            }
            return best;
        }

        @Override
        public int[] livingNonUndeadIdsIn(double x, double y, double z,
                                          double halfX, double halfY, int excludeId) {
            java.util.List<Integer> found = new java.util.ArrayList<>();
            // The players (never undead; the dimension gates the scan).
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        || player.dimension() != activeDimension
                        || player.engineEntityId() == excludeId) {
                    continue;
                }
                Position p = player.position();
                if (Math.abs(p.x() - x) <= halfX && Math.abs(p.y() - y) <= halfY
                        && Math.abs(p.z() - z) <= halfX) {
                    found.add(player.engineEntityId());
                }
            }
            // The mobs (the UNDEAD family leaves — the smite set: the
            // zombie pair, the skeleton pair, the wither itself).
            for (MobEntity mob : mobManager.all()) {
                if (mob.dead() || mob.dimension() != activeDimension
                        || mob.entityId() == excludeId
                        || mob.type().damageCategory() == 1) {
                    continue;
                }
                Position p = mob.position();
                if (Math.abs(p.x() - x) <= halfX && Math.abs(p.y() - y) <= halfY
                        && Math.abs(p.z() - z) <= halfX) {
                    found.add(mob.entityId());
                }
            }
            int[] ids = new int[found.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = found.get(i);
            }
            return ids;
        }

        @Override
        public int nearestVisibleEntityId(Position target) {
            // The main head's quarry: the nearest live body to the hunt's
            // own resolved point (the players first — the hunt reads the
            // players — then the mobs for the revenge family).
            PlayerSession player = playerByIdNearestTo(target, 4.0);
            if (player != null) {
                return player.engineEntityId();
            }
            MobEntity mob = mobByIdNearestTo(target, 4.0);
            return mob == null ? 0 : mob.entityId();
        }

        /** The nearest live player within {@code radius} of the point. */
        private PlayerSession playerByIdNearestTo(Position at, double radius) {
            PlayerSession best = null;
            double bestDistance = radius * radius;
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()
                        || player.dimension() != activeDimension) {
                    continue;
                }
                Position p = player.position();
                double dx = p.x() - at.x();
                double dy = p.y() - at.y();
                double dz = p.z() - at.z();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best;
        }

        /** The nearest live mob within {@code radius} of the point. */
        private MobEntity mobByIdNearestTo(Position at, double radius) {
            MobEntity best = null;
            double bestDistance = radius * radius;
            for (MobEntity mob : mobManager.all()) {
                if (mob.dead() || mob.dimension() != activeDimension) {
                    continue;
                }
                Position p = mob.position();
                double dx = p.x() - at.x();
                double dy = p.y() - at.y();
                double dz = p.z() - at.z();
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public boolean difficultyAtLeastNormal() {
            // The engine's own difficulty id (0 peaceful .. 3 hard).
            return difficulty >= 2;
        }

        @Override
        public boolean hasLineOfSight(double x1, double y1, double z1,
                                      double x2, double y2, double z2) {
            // Entity.canSee's eye-to-eye ray — the same half-block march
            // the shooter's line rides.
            return clearLine(new Position(x1, y1, z1), new Position(x2, y2, z2));
        }
    }

    /** The world adapter the fluid system reads and commits through. */
    private final class FluidWorld implements FluidSystem.World {
        @Override
        public BlockType getBlock(BlockPosition position) {
            return world.getBlock(position);
        }

        @Override
        public void setBlock(BlockPosition position, BlockType type) {
            world.setBlock(position, type);
        }

        @Override
        public BlockType airType() {
            return world.airType();
        }

        @Override
        public long totalTicks() {
            return world.totalTicks();
        }
    }

    /** The fluid feedback: torch wash-out drops and the fizz sound. */
    private final class FluidSink implements FluidSystem.Sink {
        private final ItemEntityManager items;

        FluidSink(ItemEntityManager items) {
            this.items = items;
        }

        @Override
        public void popItem(Position at, ItemStack stack) {
            items.spawnDropAtBlock(at, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }

        @Override
        public void sound(Position at, String name, float volume, float pitch) {
            fxManager.sound(at, name, volume, pitch);
        }
    }

    /**
     * The explosion's world adapter — parameterized by the TARGET world
     * (Slice 62: the End's crystals blast through the End's own chunk map;
     * the overworld bindings pass their own world).
     */
    private final class BlastWorld implements ExplosionService.World {
        private final EngineWorld target;

        BlastWorld(EngineWorld target) {
            this.target = Objects.requireNonNull(target, "target");
        }

        @Override
        public BlockType getBlock(BlockPosition position) {
            return target.getBlock(position);
        }

        @Override
        public boolean setBlock(BlockPosition position, BlockType type) {
            return target.setBlock(position, type);
        }
    }

    /** The wire-side explosion observer (the adapter registers one). */
    public interface ExplosionListener {
        void onExplosion(ExplosionEvent event);
    }

    /** One detonation for the wire: the center, the wire radius, the removed
     * block offsets (relative i8 triples for the packet), each hit player's
     * own knockback vector (the historical per-observer motion), and the
     * dimension the blast lives in (Slice 62 — the adapter's own observer
     * gate: a nether body never sees an overworld blast at the same
     * coordinates). */
    public record ExplosionEvent(double x, double y, double z, float radius,
                                 java.util.List<int[]> blockOffsets,
                                 java.util.Map<UUID, double[]> playerMotion,
                                 int dimension) {
    }

    /** Registers an internal explosion observer (the protocol adapter). */
    public void addExplosionListener(ExplosionListener listener) {
        explosionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Historical creeper power (the explosion radius in blocks). */
    static final float CREEPER_POWER = 3.0f;
    /** The wither's birth explosion (WitherEntity.mobAiTick line 217: 7.0F). */
    static final float WITHER_BIRTH_POWER = 7.0f;
    /** The blast's injury radius: double the power (the historical falloff span). */
    static final double BLAST_INJURY_RADIUS = CREEPER_POWER * 2.0;
    /** Point-blank explosion damage on easy difficulty (the historical ~24). */
    static final float BLAST_MAX_DAMAGE = 24.0f;

    /**
     * A primed creeper went off: destroy the blast sphere, roll the block
     * drops, wound players and mobs with distance falloff, and hand the
     * result to the wire (the Explosion packet carries each player's own
     * knockback — the client applies it, the historical 1.8 explosion path).
     * Tick-thread context.
     */
    private void explodeOnTick(MobEntity creeper) {
        Position center = creeper.position();
        // mobGriefing (Slice 24a — CreeperEntity.explode line 208): the
        // destructive flag rides the rule; a no-grief world's blast
        // wounds and knocks but never breaks blocks.
        detonateAt(center.x(), center.y() + 0.5, center.z(), CREEPER_POWER, BLAST_MAX_DAMAGE,
                gameRules.getBoolean("mobGriefing"));
    }

    /**
     * One detonation in the OVERWORLD (the generalized walk's own dimension-0
     * binding — the creeper, the TNT, the fireballs, the wither).
     */
    private void detonateAt(double cx, double cy, double cz, float power, float maxDamage,
                            boolean destructive) {
        detonateAtIn(world, explosionService, 0, cx, cy, cz, power, maxDamage, destructive);
    }

    /**
     * One detonation in the named world (the generalized explosion walk —
     * the creeper and the primed TNT ride the overworld binding, Slice
     * 62's ender crystal rides the End one): the destruction sphere with
     * the per-power drop roll, the wound falloff to double the power with
     * the caller's easy-difficulty cap, and the wire fan-out. The wounds
     * and the wire carry the blast's OWN dimension — a body in another
     * world never takes a same-coordinate blast. Tick-thread context.
     */
    private void detonateAtIn(EngineWorld blastWorld, ExplosionService service,
                              int dimension, double cx, double cy, double cz,
                              float power, float maxDamage, boolean destructive) {
        BlockPosition blockCenter = new BlockPosition(
                (int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz));

        // §27.7: the public explosion gate BEFORE any destruction — the
        // blocks still stand, nobody is wounded, no knockback is computed.
        // A cancelled blast never tears the world (the priming body itself
        // already left at the fuse's end — only its consequence is undone).
        net.zaminmc.torch.event.world.ExplosionEvent gate =
                new net.zaminmc.torch.event.world.ExplosionEvent(
                        blastWorld, new Position(cx, cy, cz), power);
        eventBus.fire(gate);
        if (gate.cancelled()) {
            return; // nothing destroyed, nobody hurt, no packet
        }

        // The destruction (each commit wakes the fluid and neighbor systems).
        // The blast rides the boundary scope (the Phase 4 spatial activation
        // — the design §10.3's "explosions and area effects spanning
        // multiple domains"): the sphere spans region seams, and the
        // reference's detonation is one same-tick walk, so the whole walk
        // holds every region the blast radius touches (power*2 + margin) —
        // the destruction stays atomic across the seam. The one-region
        // default makes the scope a single-domain enter/exit. The box clamps
        // to the world's vertical bounds (a ground-level blast would
        // otherwise walk the scope off the bottom — the bedrock floor
        // stops the rays, the scope must not crash first).
        // The destructive flag (Slice 24a — Explosion.damageBlocks line
        // 156's own gate): a false flag (mobGriefing off) skips the whole
        // block walk — the wounds, the knockback, and the packet still
        // run below, exactly the reference's shape.
        int blastReach = (int) Math.ceil(power * 2.0) + 2;
        BlockPosition scopeMin = new BlockPosition(
                blockCenter.x() - blastReach,
                Math.max(BlockPosition.MIN_Y, blockCenter.y() - blastReach),
                blockCenter.z() - blastReach);
        BlockPosition scopeMax = new BlockPosition(
                blockCenter.x() + blastReach,
                Math.min(BlockPosition.MAX_Y, blockCenter.y() + blastReach),
                blockCenter.z() + blastReach);
        java.util.List<ExplosionService.Destroyed> destroyed = new java.util.ArrayList<>();
        if (destructive) {
            blastWorld.executeInRegions(scopeMin, scopeMax,
                    () -> destroyed.addAll(service.detonate(blockCenter, power)));
        }
        // The historical drop roll: one block in `power` survives as loot.
        java.util.List<int[]> blockOffsets = new java.util.ArrayList<>();
        for (ExplosionService.Destroyed removed : destroyed) {
            BlockPosition b = removed.position();
            blockOffsets.add(new int[]{
                    b.x() - blockCenter.x(), b.y() - blockCenter.y(), b.z() - blockCenter.z()});
            if (removed.type().identifier().value().equals("tnt")) {
                // The chain reaction (TntBlock.onExploded lines 58-65): a
                // consumed TNT primes a short-fuse body instead of dropping.
                // (Slice 62 note: the overworld's own TNT system — the End
                // carries no ignition listener yet, the tnt-in-End gap
                // ledgered with the slice.)
                if (tntSystem != null && dimension == 0) {
                    tntSystem.primeFromBlast(b);
                }
                continue;
            }
            if (gameplayRandom.nextInt(Math.max(1, (int) power)) == 0) {
                net.zaminmc.torch.item.ItemType item = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        removed.type().identifier()).orElse(null);
                if (item != null) {
                    itemEntities.spawnDropAtBlock(new Position(b.x() + 0.5, b.y() + 0.5, b.z() + 0.5),
                            ItemStack.of(item, 1), ItemEntity.PICKUP_DELAY_DROP_TICKS);
                }
            }
        }
        // §27.7: the per-block post-commit observation — the cells are
        // already air and the drop rolls resolved; handlers observe the
        // destruction list, they do not veto it (the pre-commit gate
        // above is the veto point).
        if (!destroyed.isEmpty()) {
            java.util.List<net.zaminmc.torch.event.world.ExplosionDestroyEvent.DestroyedCell>
                    cells = new java.util.ArrayList<>(destroyed.size());
            for (ExplosionService.Destroyed removed : destroyed) {
                cells.add(new net.zaminmc.torch.event.world.ExplosionDestroyEvent.DestroyedCell(
                        removed.position(), removed.type().identifier().toString()));
            }
            eventBus.fire(new net.zaminmc.torch.event.world.ExplosionDestroyEvent(
                    blastWorld, new Position(cx, cy, cz), power, cells));
        }

        // Player wounds: distance falloff to double the power, then the
        // historical hurt window and the per-observer knockback vector.
        // The blast's OWN dimension gates the body (Slice 62 — the same
        // coordinates exist in every world; only the world's bodies take
        // the blast).
        double injuryRadius = power * 2.0;
        java.util.Map<UUID, double[]> motion = new java.util.HashMap<>();
        for (PlayerSession player : players.all()) {
            if (player.state() != PlayerState.PLAYING
                    || player.dimension() != dimension) {
                continue;
            }
            Position p = player.position();
            double dx = p.x() - cx;
            double dy = (p.y() + 0.9) - cy;
            double dz = p.z() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > injuryRadius) {
                continue;
            }
            float damage = (float) ((1 - dist / injuryRadius) * maxDamage);
            if (damage <= 0) {
                continue;
            }
            // The difficulty's own scaling (Slice 19e — PlayerEntity.
            // takeDamage lines 786-797 over the reference's scaled
            // sources: the explosion family carries the whole ladder —
            // peaceful never wounds, easy halves plus one, hard lifts a
            // half — before the protection math sees the number.
            if (difficulty == 0) {
                continue; // the peaceful blast never wounds
            }
            if (difficulty == 1) {
                damage = damage / 2.0f + 1.0f;
            } else if (difficulty == 3) {
                damage = damage * 1.5f;
            }
            // The reference Explosion walk: the victim's Blast Protection
            // shaves floor(damage * level * 0.15) off the raw blast number
            // before the ordinary pipeline (armor, protection) sees it.
            damage = (float) EnchantmentHelper.modifyExplosionDamage(
                    player.inventory().armorStacks(), damage);
            damage = applyArmor(player, damage); // armor eats its share of the blast
            damage = applyEnchantProtection(player, damage, DamageKind.EXPLOSION);
            // §27.7: the public damage gate on the blast arm — after the
            // protection math, before any state moves. A cancelled blast
            // lands nothing; the mutable amount drives the window math.
            net.zaminmc.torch.event.player.PlayerDamageEvent blastEvent =
                    new net.zaminmc.torch.event.player.PlayerDamageEvent(
                            player, damage, net.zaminmc.torch.event.DamageCause.EXPLOSION);
            eventBus.fire(blastEvent);
            if (blastEvent.cancelled()) {
                motion.put(player.uuid(), new double[]{0, 0, 0});
                continue;
            }
            damage = blastEvent.amount();
            if (damage <= 0) {
                motion.put(player.uuid(), new double[]{0, 0, 0});
                continue;
            }
            double scale = (1 - dist / injuryRadius) * 1.6;
            double mx = dist < 0.001 ? 0 : dx / dist * scale;
            double mz = dist < 0.001 ? 0 : dz / dist * scale;
            if (!player.hurtInvulnerable() || damage > player.lastHurtDamage()) {
                float applied = player.hurtInvulnerable()
                        ? Math.max(0.0f, damage - player.lastHurtDamage()) : damage;
                player.beginHurtInvulnerability(damage);
                if (applied > 0) {
                    player.hurt(applied);
                }
                publishPlayerHurt(player);
                publishKnockback(player, mx, 0.4, mz);
                if (player.health() <= 0) {
                    dieOnTick(player, "was blown up");
                } else {
                    publishBodyChanged(player);
                }
            }
            motion.put(player.uuid(), new double[]{mx, 0.4, mz});
        }

        // Mob wounds: the same falloff, through the manager's hurt path,
        // in the blast's own dimension (the mob's dimension field — the
        // nether bodies never take an overworld blast at the same spot).
        for (MobEntity mob : mobManager.all()) {
            if (mob.dimension() != dimension) {
                continue;
            }
            Position p = mob.position();
            double dx = p.x() - cx;
            double dy = (p.y() + mob.type().height * 0.5) - cy;
            double dz = p.z() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > injuryRadius) {
                continue;
            }
            float damage = (float) ((1 - dist / injuryRadius) * maxDamage);
            if (damage <= 0) {
                continue;
            }
            mobManager.hurt(mob, damage,
                    Math.toDegrees(Math.atan2(-dx, dz)), // knockback away from the blast
                    net.zaminmc.torch.event.DamageCause.EXPLOSION);
        }

        // The wire: one Explosion packet per observer, motion included —
        // the adapter's own dimension gate reads the carried field.
        ExplosionEvent event = new ExplosionEvent(cx, cy, cz, power,
                java.util.List.copyOf(blockOffsets), java.util.Map.copyOf(motion), dimension);
        for (ExplosionListener listener : explosionListeners) {
            listener.onExplosion(event);
        }
        LOGGER.fine(() -> "Explosion at " + blockCenter + " removed " + destroyed.size() + " blocks");
    }

    /**
     * Topmost solid block y of a column for population rolls (-1 when the
     * chunk is absent). All callers run on the simulation context, so a
     * missing chunk can be generated safely.
     */
    private int surfaceY(int x, int z) {
        EngineChunk chunk = world.peek(new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
        if (chunk == null) {
            chunk = world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
        }
        for (int y = EngineChunk.SECTION_COUNT * 16 - 1; y >= 0; y--) {
            if (!chunk.getBlock(x & 15, y, z & 15).equals(world.airType())) {
                return y;
            }
        }
        return -1;
    }

    /** Registers an internal chat delivery observer (e.g. the protocol adapter). */
    public void addChatListener(ChatListener listener) {
        chatListeners.add(listener);
    }

    /**
     * Spawns the egg-burst silverfish (Slice 18c —
     * InfestedBlock.dropItems lines 79-88): the body lands at the block's
     * center with the reference's own zero yaw. Tick-thread context (the
     * break path's own lane).
     */
    private void spawnSilverfishAt(Position at) {
        if (mobManager != null) {
            mobManager.spawnAt(net.zaminmc.torch.server.entity.MobType.SILVERFISH, at);
        }
    }

    private void registerBuiltinCommands(CommandService commands) {
        commands.register(new CommandService.Command("help", "List commands",
                (sender, args) -> {
                    StringBuilder text = new StringBuilder("Commands:");
                    for (CommandService.Command command : commands.all()) {
                        if (sender.opLevel() >= command.requiredLevel()) {
                            text.append(" /").append(command.name())
                                    .append(" (").append(command.description()).append(")");
                        }
                    }
                    return text.toString();
                }));
        commands.register(new CommandService.Command("ping", "Check server responsiveness",
                (sender, args) -> "pong"));
        commands.register(new CommandService.Command("give",
                "Give yourself an item: /give <name> [count] [metadata]",
                playerCommand(this::giveCommand)));
        commands.register(new CommandService.Command("gamerule",
                "Query or set a game rule: /gamerule <rule> [true|false|value]",
                2, this::gameruleCommand));
        commands.register(new CommandService.Command("time",
                "Query or set the day: /time query | /time set <day|noon|night|midnight|ticks>",
                this::timeCommand));
        commands.register(new CommandService.Command("rename",
                "Rename the held item: /rename <name...> (no name clears it)",
                0, playerCommand(this::renameCommand)));
        commands.register(new CommandService.Command("spawnmob",
                "Spawn mobs near you: /spawnmob <pig|cow|chicken|zombie|creeper|skeleton|sheep|spider|horse|villager> [count]",
                playerCommand(this::spawnMobCommand)));
        // The vanilla summon (Slice 67 — SummonCommand 1:1, the op-2
        // surface): the 1.8.8 entity names, the ~-relative coordinates,
        // the LightningBolt special, the chunk-loaded gate. The NBT arm
        // rides the engine's no-item-NBT envelope (ledgered).
        commands.register(new CommandService.Command("summon",
                "Summon an entity: /summon <name> [x y z]",
                2, playerCommand(this::summonCommand)));
        // The Paper operator set.
        commands.register(new CommandService.Command("gamemode",
                "Change a game mode: /gamemode <survival|creative|adventure|spectator> [player]",
                2, this::gamemodeCommand));
        commands.register(new CommandService.Command("op", "Grant operator: /op <player>",
                3, this::opCommand));
        commands.register(new CommandService.Command("deop", "Revoke operator: /deop <player>",
                3, this::deopCommand));
        commands.register(new CommandService.Command("list", "List online players",
                (sender, args) -> "There are " + players.all().size() + " of a max of "
                        + config.maxPlayers() + " players online: "
                        + players.all().stream().map(PlayerSession::name)
                                .sorted().reduce((a, b) -> a + ", " + b).orElse("(none)")));
        commands.register(new CommandService.Command("kick", "Kick a player: /kick <player> [reason]",
                2, this::kickCommand));
        commands.register(new CommandService.Command("say", "Broadcast a message: /say <message...>",
                2, this::sayCommand));
        commands.register(new CommandService.Command("save", "Save all worlds",
                4, (sender, args) -> {
                    saveAllNow();
                    return "Saved the game";
                }));
        commands.register(new CommandService.Command("stop", "Stop the server",
                4, (sender, args) -> {
                    LOGGER.info("Stop requested by " + sender.name());
                    new Thread(() -> shutdown(null), "zamin-command-stop").start();
                    return "Stopping the server";
                }));
        // Community-style single-letter aliases of /gamemode.
        commands.register(new CommandService.Command("gms", "Shortcut: /gamemode survival",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "survival"))));
        commands.register(new CommandService.Command("gmc", "Shortcut: /gamemode creative",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "creative"))));
        commands.register(new CommandService.Command("gma", "Shortcut: /gamemode adventure",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "adventure"))));
        commands.register(new CommandService.Command("gmsp", "Shortcut: /gamemode spectator",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "spectator"))));
        // The Paper movement / survival operator set.
        commands.register(new CommandService.Command("tp",
                "Teleport: /tp <x> <y> <z> | /tp <player> | /tp <from> <to>",
                2, this::tpCommand));
        commands.register(new CommandService.Command("kill",
                "Kill a player: /kill [player]",
                2, this::killCommand));
        commands.register(new CommandService.Command("heal",
                "Restore health and hunger: /heal [player]",
                2, this::healCommand));
        commands.register(new CommandService.Command("feed",
                "Restore hunger: /feed [player]",
                2, this::feedCommand));
        commands.register(new CommandService.Command("spawn",
                "Teleport to the world spawn point",
                0, playerCommand(this::spawnTpCommand)));
        commands.register(new CommandService.Command("setspawn",
                "Move the world spawn to where you stand",
                2, playerCommand(this::setSpawnCommand)));
        commands.register(new CommandService.Command("fly",
                "Toggle personal flight: /fly [on|off]",
                2, playerCommand(this::flyCommand)));
        commands.register(new CommandService.Command("clear",
                "Empty a player's inventory: /clear [player]",
                2, this::clearCommand));
        commands.register(new CommandService.Command("ban",
                "Ban a player: /ban <player> [reason...]",
                3, this::banCommand));
        commands.register(new CommandService.Command("pardon",
                "Lift a ban: /pardon <player>",
                3, this::pardonCommand));
        commands.register(new CommandService.Command("banlist",
                "List the banned players",
                3, this::banlistCommand));
        commands.register(new CommandService.Command("whitelist",
                "Whitelist control: /whitelist on|off|add|remove|list",
                3, this::whitelistCommand));
        commands.register(new CommandService.Command("weather",
                "Set the weather: /weather <clear|rain> [seconds]",
                2, this::weatherCommand));
        // The wider Paper operator set.
        commands.register(new CommandService.Command("me",
                "Broadcast an action: /me <action...>",
                0, playerCommand(this::meCommand)));
        commands.register(new CommandService.Command("tell",
                "Private message a player: /tell <player> <message...>",
                0, playerCommand(this::tellCommand)));
        commands.register(new CommandService.Command("msg",
                "Alias: /msg <player> <message...>",
                0, playerCommand(this::tellCommand)));
        commands.register(new CommandService.Command("w",
                "Alias: /w <player> <message...>",
                0, playerCommand(this::tellCommand)));
        commands.register(new CommandService.Command("reply",
                "Reply to the last private message: /reply <message...>",
                0, playerCommand(this::replyCommand)));
        commands.register(new CommandService.Command("ban-ip",
                "Ban an IP address: /ban-ip <ip|player> [reason...]",
                3, this::banIpCommand));
        commands.register(new CommandService.Command("pardon-ip",
                "Lift an IP ban: /pardon-ip <ip>",
                3, this::pardonIpCommand));
        commands.register(new CommandService.Command("xp",
                "Grant experience: /xp <amount> [player] [L] (L = levels)",
                2, this::xpCommand));
        commands.register(new CommandService.Command("difficulty",
                "Set the difficulty: /difficulty <peaceful|easy|normal|hard>",
                2, this::difficultyCommand));
        commands.register(new CommandService.Command("defaultgamemode",
                "Set the default game mode for new players: /defaultgamemode <mode>",
                2, this::defaultGamemodeCommand));
        commands.register(new CommandService.Command("seed",
                "Print the world seed",
                0, (sender, args) -> "Seed: [" + worldSeed + "]"));
        commands.register(new CommandService.Command("tps",
                "Print the recent tick rates",
                2, (sender, args) -> String.format(java.util.Locale.ROOT,
                        "TPS from last 1m, 5m, 15m: %.1f, %.1f, %.1f",
                        ticker.averageTps(60),
                        ticker.averageTps(Math.min(60, 300)),
                        ticker.averageTps(Math.min(60, 900)))));
        commands.register(new CommandService.Command("plugins",
                "List the installed plugins",
                0, (sender, args) -> {
                    java.io.File dir = new java.io.File(config.dataDir(), "plugins");
                    java.io.File[] jars = dir.listFiles((d, n) ->
                            n.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
                    if (jars == null || jars.length == 0) {
                        return "Plugins (0): server ships no third-party plugins";
                    }
                    return "Plugins (" + jars.length + "): "
                            + java.util.Arrays.stream(jars).map(f ->
                                    f.getName().replaceAll("\\.jar$", ""))
                                    .sorted().reduce((a, b) -> a + ", " + b).orElse("");
                }));
        commands.register(new CommandService.Command("version",
                "Print the engine version",
                0, (sender, args) -> "This server is running ZaminTorch version " + engineVersion()));
    }

    /** The build version (the fat-jar manifest; "dev" inside IDEs). */
    private static String engineVersion() {
        Package pkg = EngineServer.class.getPackage();
        String fromManifest = pkg == null ? null : pkg.getImplementationVersion();
        return fromManifest == null ? "dev" : fromManifest;
    }

    private static String[] prepend(String[] args, String first) {
        String[] all = new String[args.length + 1];
        all[0] = first;
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }

    // -------------------------------------------------------------- movement / body commands

    /**
     * /tp: the operator teleport set — coordinates, a named target, or a
     * from→to move. The re-anchor rides the respawn wire path (chunk tracker
     * reset + Position and Look) with a guard grace window.
     */
    private String tpCommand(CommandSender sender, String[] args) {
        if (args.length == 3) {
            PlayerSession self = sender.player();
            if (self == null) {
                return "Console must name a player: /tp <player> <x> <y> <z> is a later form.";
            }
            double x, y, z;
            try {
                x = Double.parseDouble(args[0]);
                y = Double.parseDouble(args[1]);
                z = Double.parseDouble(args[2]);
            } catch (NumberFormatException malformed) {
                return "Coordinates must be numbers: /tp <x> <y> <z>";
            }
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return "Coordinates out of bounds";
            }
            teleportOnTick(self, new Position(x, y, z));
            return String.format(java.util.Locale.ROOT, "Teleported %s to %.1f %.1f %.1f",
                    self.name(), x, y, z);
        }
        if (args.length == 1) {
            PlayerSession self = sender.player();
            if (self == null) {
                return "Console must name two players: /tp <from> <to>";
            }
            PlayerSession target = players.byName(args[0]).orElse(null);
            if (target == null) {
                return "No online player named " + args[0];
            }
            teleportOnTick(self, target.position());
            return "Teleported you to " + target.name();
        }
        if (args.length == 2) {
            PlayerSession from = players.byName(args[0]).orElse(null);
            PlayerSession to = players.byName(args[1]).orElse(null);
            if (from == null || to == null) {
                return "No online player named " + (from == null ? args[0] : args[1]);
            }
            teleportOnTick(from, to.position());
            return "Teleported " + from.name() + " to " + to.name();
        }
        return "Usage: /tp <x> <y> <z> | /tp <player> | /tp <from> <to>";
    }

    /**
     * The engine-side teleport: re-anchors the session on the tick thread,
     * opens the guard's grace window and reuses the respawn wire path (the
     * chunk tracker re-anchors and Position and Look re-syncs the client).
     * Safe from any thread.
     */
    public void teleportPlayer(PlayerSession session, Position to) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(to, "to");
        teleportOnTick(session, to);
    }

    private void teleportOnTick(PlayerSession session, Position to) {
        ticker.submit(() -> {
            if (session.state() != PlayerState.PLAYING || session.dead()) {
                return;
            }
            session.applyMovement(to, session.rotation(), false);
            session.setGraceTicks(100);
            session.consumeFallDistance(); // the teleport cancels fall debt
            publishRespawned(session, to);
            LOGGER.fine(() -> "Teleported " + session.name() + " to " + to);
        });
    }

    /** @return the world a session's dimension walks (0 the overworld, -1 the
     * nether, 1 the End — Slice 61). */
    public EngineWorld worldFor(int dimension) {
        if (dimension == -1) {
            return java.util.Objects.requireNonNull(netherWorld, "nether world");
        }
        if (dimension == 1) {
            return java.util.Objects.requireNonNull(endWorld, "end world");
        }
        return world;
    }

    /** The End's arrival cell (TheEndDimension.getForcedSpawnPoint line 74:
     * the platform position, never a generator product). */
    public static final int END_ARRIVAL_X = 100;
    public static final int END_ARRIVAL_Y = 50;
    public static final int END_ARRIVAL_Z = 0;
    /** The End arrival's fixed yaw (PlayerManager.changeDimension line 481:
     * the 90.0F in the End reposition arm). */
    public static final float END_ARRIVAL_YAW = 90.0f;

    /**
     * The changeDimension walk (the reference's PlayerManager.changeDimension,
     * reference/1.8.8 net/minecraft/server/PlayerManager.java lines 424-447 +
     * the Entity overload 449-499): the dimension swap, the 8:1 coordinate
     * walk (÷8 into the nether, ×8 back, the ±29999872 placing clamp), the
     * destination neighborhood loaded before the portal search reads it (the
     * reference's getBlockState would load every searched chunk synchronously
     * — the same stall, bounded to the search's full 17x17 chunk ring), the
     * destination PortalForcer's find-or-generate, the body's arrival, and
     * the wire's respawn sequence re-pointed at the new dimension. Tick-thread
     * context (the portal stand clock's crossing arm and the tests call it
     * there); safe from any thread through the tick queue.
     */
    public void changeDimension(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        Runnable walk = () -> changeDimensionOnTick(session);
        if (java.lang.Thread.currentThread() == ticker.ownerThread()) {
            walk.run();
        } else {
            ticker.submit(walk);
        }
    }

    /**
     * The End entry walk (Slice 61 — the reference's PlayerManager
     * .changeDimension Entity-overload lines 468-485, the End arm): the
     * arrival at the dimension's FORCED spawn (TheEndDimension
     * .getForcedSpawnPoint's 100/50/0), the fixed 90-degree yaw, NO
     * portal-forcer walk (the reference's {@code dimension != 1} gate
     * skips the placing phase for the End), and the wire's respawn
     * sequence re-pointed at dimension 1.
     *
     * <p>The obsidian arrival platform: the reference tree carries no
     * platform-creation code anywhere (the decompilation gap this slice
     * ledgers) — vanilla arrivals land standing on a 5x5 obsidian
     * platform. The engine adaptation writes the platform with the
     * arrival (floor at y=49 — the body's feet at 50 — x 98..102,
     * z -2..2, air carved 50..52), a standing law rather than a
     * generation law so the platform follows every arrival like
     * vanilla's does.</p>
     *
     * <p>Tick-thread context (the end portal's instant arm).</p>
     */
    private void enterEndOnTick(PlayerSession session) {
        if (session.state() != PlayerState.PLAYING || session.dead()) {
            return;
        }
        if (session.dimension() == 1) {
            return; // already there
        }
        EngineWorld toWorld = worldFor(1);
        // The arrival neighborhood loads BEFORE the platform writes read
        // it (the nether walk's own bounded precedent — here the 3x3 chunk
        // ring around the arrival cell).
        int centerChunkX = END_ARRIVAL_X >> 4;
        int centerChunkZ = END_ARRIVAL_Z >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                toWorld.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(
                        centerChunkX + dx, centerChunkZ + dz));
            }
        }
        // The platform adaptation (the ledgered decompilation gap): the
        // 5x5 obsidian floor + the air pocket above it.
        BlockType obsidian = blockRegistry.require(
                net.zaminmc.torch.util.Identifier.parse("minecraft:obsidian"));
        for (int x = END_ARRIVAL_X - 2; x <= END_ARRIVAL_X + 2; x++) {
            for (int z = END_ARRIVAL_Z - 2; z <= END_ARRIVAL_Z + 2; z++) {
                toWorld.setBlock(new BlockPosition(x, END_ARRIVAL_Y - 1, z), obsidian);
                for (int y = END_ARRIVAL_Y; y <= END_ARRIVAL_Y + 2; y++) {
                    toWorld.setBlock(new BlockPosition(x, y, z), toWorld.airType());
                }
            }
        }
        // The arrival (the reference's setPositionAndAngles at the forced
        // spawn, the 90-degree yaw; the fall debt clears — the crossing is
        // not a fall).
        Position arrival = new Position(END_ARRIVAL_X, END_ARRIVAL_Y, END_ARRIVAL_Z);
        session.applyDimensionArrival(1, arrival, END_ARRIVAL_YAW,
                session.rotation().pitch());
        session.setGraceTicks(100);
        session.consumeFallDistance();
        publishDimensionChanged(session, arrival, 1);
        LOGGER.info(() -> "Player " + session.name()
                + " crossed to the End (arrival " + arrival + ")");
    }

    /**
     * The End exit walk (Slice 64 — the reference's ServerPlayerEntity
     * .changeDimension lines 444-457, the {@code dimension == 1 &&
     * this.dimension == 1} arm): the exit fountain's cell removes the body
     * from the End world, arms the credits state, and sends the GameEvent-4
     * win screen. The actual overworld landing waits for the client's
     * respawn confirmation ({@link #performRespawn} — the reference's
     * handleClientStatus {@code leavingTheEnd} arm, the alive respawn).
     *
     * <p>The achievement (LEAVE_THE_END — {@code incrementStat(
     * Achievements.LEAVE_THE_END)}) rides the achievement family's own
     * slice; the engine's stat store has no achievement rows yet. The
     * non-player entity arm (Entity.changeDimension's old-1+new-1 redirect
     * to the overworld spawn surface) needs the mob portal-collision
     * simulation first — mobs do not yet collide with portal cells;
     * ledgered.</p>
     *
     * <p>Tick-thread context (the portal stand's instant arm).</p>
     */
    private void leaveEndOnTick(PlayerSession session) {
        if (session.state() != PlayerState.PLAYING || session.dead()) {
            return;
        }
        // The removed-from-the-world guard: the reference's removeEntity
        // stops the collision from re-firing; the engine's session keeps
        // ticking, so the flag itself is the guard.
        if (session.leavingTheEnd()) {
            return;
        }
        // The reference's order (lines 446-449): the stat, the world
        // removal, the flag, the win screen.
        session.markLeavingTheEnd();
        publishEndCredits(session); // the removeEntity wire (the observers)
        session.link().playGameState(GAME_STATE_END_CREDITS, 0.0f);
        LOGGER.info(() -> "Player " + session.name()
                + " left the End (the credits walk began)");
    }

    private void changeDimensionOnTick(PlayerSession session) {
        if (session.state() != PlayerState.PLAYING || session.dead()) {
            return;
        }
        int from = session.dimension();
        int to = from == 0 ? -1 : 0;
        EngineWorld toWorld = worldFor(to);

        // The 8:1 walk (the reference's Entity overload: ÷8 into the nether,
        // ×8 back; the placing phase clamps the int coordinates to the
        // ±29999872 border band — the engine has no border yet, the band kept).
        double scale = from == 0 ? 1.0 / 8.0 : 8.0;
        int destX = clampCoordinate((int) Math.floor(session.position().x() * scale));
        int destZ = clampCoordinate((int) Math.floor(session.position().z() * scale));

        // The destination neighborhood loads BEFORE the search reads it: the
        // reference's getBlockState would load every searched chunk
        // synchronously — the same stall, here bounded to the search's full
        // 17x17 chunk ring (257x257 blocks).
        int centerChunkX = destX >> 4;
        int centerChunkZ = destZ >> 4;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                toWorld.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(
                        centerChunkX + dx, centerChunkZ + dz));
            }
        }

        // The destination-side portal walk (the reference's
        // toWorld.getPortalForcer().onDimensionChanged(entity, yaw)).
        PortalForcer forcer = to == -1 ? netherPortalForcer : worldPortalForcer;
        PortalForcer.Arrival arrival = forcer.onDimensionChanged(
                destX + 0.5, session.position().y(), destZ + 0.5,
                session.rotation().yaw(), 0.0, 0.0, session.portalMemory());

        // The body's arrival (the reference's setPositionAndAngles + the
        // world swap; the fall debt clears — a portal walk is not a fall).
        Position arrivalPosition = new Position(arrival.x(), arrival.y(), arrival.z());
        session.applyDimensionArrival(to, arrivalPosition,
                arrival.yaw(), session.rotation().pitch());
        session.setGraceTicks(100);

        // The wire's respawn sequence re-points at the new dimension (the
        // reference's PlayerRespawnS2CPacket + the teleport + the world info
        // resend).
        publishDimensionChanged(session, arrivalPosition, to);
        LOGGER.info(() -> "Player " + session.name() + " crossed to dimension " + to
                + " (arrival " + arrivalPosition + ")");
    }

    /** The reference's placing-phase border clamp (the ±29999872 band). */
    private static int clampCoordinate(int value) {
        return Math.max(-29_999_872, Math.min(29_999_872, value));
    }

    private void publishDimensionChanged(PlayerSession player, Position destination, int dimension) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onDimensionChanged(player, destination, dimension);
        }
    }

    /** /kill [player]: the void-equivalent death (pierces protection, historical rule). */
    private String killCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /kill [player]";
        }
        ticker.submit(() -> {
            if (target.state() == PlayerState.PLAYING && !target.dead()) {
                dieOnTick(target);
            }
        });
        return "Killed " + target.name();
    }

    /** /heal [player]: full hearts, full hunger, the historical saturation reset. */
    private String healCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /heal [player]";
        }
        ticker.submit(() -> {
            if (target.state() != PlayerState.PLAYING || target.dead()) {
                return;
            }
            target.setBody(PlayerSession.MAX_HEALTH, PlayerSession.MAX_FOOD,
                    PlayerSession.DEFAULT_SATURATION);
            target.setExhaustion(0);
            target.resetFallDistance();
            publishBodyChanged(target);
        });
        return "Healed " + target.name();
    }

    /** /feed [player]: full hunger bar with the default saturation. */
    private String feedCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /feed [player]";
        }
        ticker.submit(() -> {
            if (target.state() != PlayerState.PLAYING || target.dead()) {
                return;
            }
            target.setBody(target.health(), PlayerSession.MAX_FOOD,
                    PlayerSession.DEFAULT_SATURATION);
            target.setExhaustion(0);
            publishBodyChanged(target);
        });
        return "Fed " + target.name();
    }

    /** The command target resolver: an explicit name, or the sending player. */
    private PlayerSession resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            PlayerSession named = players.byName(args[0]).orElse(null);
            if (named == null) {
                return null;
            }
            return named;
        }
        return sender.player();
    }

    /** /spawn: the player's trip back to the world spawn anchor. */
    private String spawnTpCommand(PlayerSession sender, String[] args) {
        Position spawn = world.spawnPosition();
        teleportOnTick(sender, spawn);
        return "Teleported you to the world spawn";
    }

    /** /setspawn: moves the world spawn anchor and persists world/data/spawn.json. */
    private String setSpawnCommand(PlayerSession sender, String[] args) {
        Position at = sender.position();
        world.setSpawnPosition(at);
        saveSpawnAnchor(at);
        LOGGER.info(() -> "World spawn moved to " + at + " by " + sender.name());
        return String.format(java.util.Locale.ROOT, "World spawn set to %.1f %.1f %.1f",
                at.x(), at.y(), at.z());
    }

    /** The persisted spawn anchor (world/data/spawn.json: x, y, z). */
    private java.util.Optional<Position> loadSpawnAnchor() {
        java.nio.file.Path file = config.worldDataDir().resolve("spawn.json");
        if (!java.nio.file.Files.exists(file)) {
            return java.util.Optional.empty();
        }
        try {
            String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            double x = Double.parseDouble(fieldOf(json, "x"));
            double y = Double.parseDouble(fieldOf(json, "y"));
            double z = Double.parseDouble(fieldOf(json, "z"));
            return java.util.Optional.of(new Position(x, y, z));
        } catch (Exception e) {
            LOGGER.warning(() -> "Unreadable spawn.json ignored: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private void saveSpawnAnchor(Position at) {
        java.nio.file.Path file = config.worldDataDir().resolve("spawn.json");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file,
                    String.format(java.util.Locale.ROOT,
                            "{\"x\": %.4f, \"y\": %.4f, \"z\": %.4f}%n",
                            at.x(), at.y(), at.z()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOGGER.warning(() -> "spawn.json write failed: " + e.getMessage());
        }
    }

    private static String fieldOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** /fly [on|off]: toggles personal flight (an admin tool; creative flies anyway). */
    private String flyCommand(PlayerSession sender, String[] args) {
        boolean enable = args.length >= 1
                ? args[0].equalsIgnoreCase("on")
                : !sender.allowedToFly();
        sender.setAllowedToFly(enable);
        if (!enable) {
            sender.setFlying(false);
            sender.clearHoverTicks();
        }
        sender.link().updateAbilities(abilitiesFlagsFor(sender));
        return enable ? "Flight enabled" : "Flight disabled";
    }

    /** The protocol-47 abilities flags for a session (the /fly + mode map). */
    private static int abilitiesFlagsFor(PlayerSession session) {
        int flags = 0;
        if (session.gamemode() == GameMode.CREATIVE || session.gamemode() == GameMode.SPECTATOR) {
            flags |= 0x01 | 0x04 | 0x08; // invulnerable + may-fly + instant build
        }
        if (session.allowedToFly()) {
            flags |= 0x04; // may-fly for the /fly grant
        }
        if (session.flying() && session.allowedToFly()) {
            flags |= 0x02; // keep the announced flight state
        }
        return flags;
    }

    /** /clear [player]: empties the inventory (the historical op 2 cleanup). */
    private String clearCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /clear [player]";
        }
        ticker.submit(() -> {
            target.inventory().clear();
            publishInventoryChanged(target);
        });
        return "Cleared " + target.name() + "'s inventory";
    }

    // -------------------------------------------------------------- ban / whitelist / weather

    /** /ban &lt;player&gt; [reason...]: bans and kicks (the historical op 3 gate). */
    private String banCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /ban <player> [reason...]";
        }
        String name = args[0];
        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : null;
        java.util.UUID uuid = players.byName(name)
                .map(PlayerSession::uuid)
                .orElseGet(() -> java.util.UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        banStore.ban(uuid, name, sender.name(), reason);
        players.byName(name).ifPresent(online ->
                online.link().kick("You are banned from this server.\nReason: "
                        + (reason == null ? "Banned by an operator" : reason)));
        LOGGER.info(() -> "Banned " + name + " (by " + sender.name() + ")");
        return "Banned " + name;
    }

    /** /pardon &lt;player&gt;: lifts the ban by name. */
    private String pardonCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /pardon <player>";
        }
        return banStore.pardon(args[0])
                ? "Unbanned " + args[0]
                : "No ban found for " + args[0];
    }

    /** /banlist: the entries of banned-players.json (the historical output). */
    private String banlistCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("ips")) {
            var ips = ipBanStore.entries();
            if (ips.isEmpty()) {
                return "There are no IP bans";
            }
            return "There are " + ips.size() + " IP ban(s): "
                    + ips.stream().map(e -> e.ip())
                            .sorted().reduce((a, b) -> a + ", " + b).orElse("(none)");
        }
        var entries = banStore.entries();
        if (entries.isEmpty()) {
            return "There are no bans";
        }
        return "There are " + entries.size() + " ban(s): "
                + entries.stream()
                        .map(e -> e.name() + (e.permanent() ? "" : " (until " + e.expires() + ")"))
                        .sorted()
                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
    }

    /** /whitelist on|off|add|remove|list: the roster + the runtime enforcement flag. */
    private String whitelistCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /whitelist on|off|add|remove|list";
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "on" -> {
                whitelistEnforced = true;
                yield "Whitelist is now enforced";
            }
            case "off" -> {
                whitelistEnforced = false;
                yield "Whitelist is now off";
            }
            case "add" -> {
                if (args.length < 2) {
                    yield "Usage: /whitelist add <player>";
                }
                java.util.UUID uuid = players.byName(args[1])
                        .map(PlayerSession::uuid)
                        .orElseGet(() -> java.util.UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + args[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                whitelistStore.add(uuid, args[1]);
                yield "Added " + args[1] + " to the whitelist";
            }
            case "remove" -> {
                if (args.length < 2) {
                    yield "Usage: /whitelist remove <player>";
                }
                yield whitelistStore.remove(args[1])
                        ? "Removed " + args[1] + " from the whitelist"
                        : "No whitelist entry for " + args[1];
            }
            case "list" -> {
                var entries = whitelistStore.entries();
                yield entries.isEmpty() ? "The whitelist is empty"
                        : "There are " + entries.size() + " whitelisted player(s): "
                                + entries.stream().map(e -> e.name()).sorted()
                                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
            }
            default -> "Usage: /whitelist on|off|add|remove|list";
        };
    }

    // -------------------------------------------------------------- social commands

    /** /me &lt;action...&gt;: the historical emote broadcast. */
    private String meCommand(PlayerSession sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /me <action...>";
        }
        String line = "* " + sender.name() + " " + String.join(" ", args);
        for (PlayerSession player : players.all()) {
            systemMessage(player, line);
        }
        LOGGER.info(line);
        return null;
    }

    /** /tell|/msg|/w &lt;player&gt; &lt;message...&gt;: the private message pair. */
    private String tellCommand(PlayerSession sender, String[] args) {
        if (args.length < 2) {
            return "Usage: /tell <player> <message...>";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            return "No online player named " + args[0];
        }
        String content = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        systemMessage(sender, "You whisper to " + target.name() + ": " + content);
        systemMessage(target, sender.name() + " whispers to you: " + content);
        target.noteMessagedBy(sender.name());
        LOGGER.info(() -> "[" + sender.name() + " -> " + target.name() + "] " + content);
        return null;
    }

    /** /reply &lt;message...&gt;: the /tell directed at the last whisperer. */
    private String replyCommand(PlayerSession sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /reply <message...>";
        }
        String targetName = sender.lastMessagedBy();
        if (targetName == null) {
            return "Nobody has messaged you yet";
        }
        return tellCommand(sender, prepend(args, targetName));
    }

    // -------------------------------------------------------------- operator commands (wide set)

    /** /ban-ip &lt;ip|player&gt; [reason...]: the banned-ips.json registry. */
    private String banIpCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /ban-ip <ip|player> [reason...]";
        }
        String ip = args[0];
        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Banned by an operator";
        // A player name resolves to that online player's current address.
        PlayerSession online = players.byName(ip).orElse(null);
        String bannedName = "unknown";
        String bannedUuid = "unknown";
        if (online != null) {
            String remote = online.link().remoteIp();
            if (!remote.isBlank()) {
                ip = remote;
            }
            bannedName = online.name();
            bannedUuid = online.uuid().toString();
        }
        if (ip.isBlank()) {
            return "Cannot resolve an IP from " + args[0];
        }
        String finalIp = ip;
        ipBanStore.ban(finalIp, bannedUuid, bannedName, sender.name(), reason);
        // Connected bodies from that address leave immediately.
        final int kicked;
        int kickedCount = 0;
        for (PlayerSession player : players.all()) {
            if (player.link().remoteIp().equals(finalIp)) {
                player.link().kick("Your IP address is banned from this server.\nReason: " + reason);
                kickedCount++;
            }
        }
        kicked = kickedCount;
        LOGGER.info(() -> "IP " + finalIp + " banned by " + sender.name()
                + (kicked > 0 ? " (" + kicked + " online player(s) kicked)" : ""));
        return "Banned IP: " + finalIp;
    }

    /** /pardon-ip &lt;ip&gt;: lifts the address ban. */
    private String pardonIpCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /pardon-ip <ip>";
        }
        return ipBanStore.pardon(args[0]) ? "Unbanned IP: " + args[0]
                : "No IP ban found for " + args[0];
    }

    /** /xp &lt;amount&gt;[L] [player]: the vanilla 1.8 grant shape (L = levels). */
    private String xpCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /xp <amount>[L] [player]";
        }
        String raw = args[0];
        boolean levels = raw.toUpperCase(java.util.Locale.ROOT).endsWith("L");
        int amount;
        try {
            amount = Integer.parseInt(levels ? raw.substring(0, raw.length() - 1) : raw);
        } catch (NumberFormatException malformed) {
            return "Not an amount: " + raw;
        }
        if (amount == 0) {
            return "Amount must not be zero";
        }
        PlayerSession target;
        if (args.length >= 2) {
            target = players.byName(args[1]).orElse(null);
        } else {
            target = sender.player(); // the console must name a player
        }
        if (target == null) {
            return "No online player named " + (args.length >= 2 ? args[1] : "(console: name a player)");
        }
        if (levels) {
            int current = target.experienceLevel();
            target.setTotalXp(net.zaminmc.torch.server.experience.ExperienceMath
                    .totalXpForLevel(Math.max(0, current + amount)));
        } else {
            long sign = amount > 0 ? 1 : -1;
            target.setTotalXp(Math.max(0, target.totalXp() + sign * Math.abs(amount)));
        }
        target.link().updateXp(); // the XP bar re-sync (Set Experience 0x1F)
        return "Given " + amount + (levels ? " level(s)" : " experience") + " to " + target.name();
    }

    /** /difficulty &lt;peaceful|easy|normal|hard&gt;: the runtime difficulty id. */
    private String difficultyCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /difficulty <peaceful|easy|normal|hard>";
        }
        Integer id = switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "peaceful" -> 0;
            case "easy" -> 1;
            case "normal" -> 2;
            case "hard" -> 3;
            default -> null;
        };
        if (id == null) {
            return "Usage: /difficulty <peaceful|easy|normal|hard>";
        }
        difficulty = id;
        if (id == 0) {
            // The historical peaceful rule: hostiles leave the world.
            MobManager mobs = mobManager;
            if (mobs != null) {
                mobs.removeHostiles();
            }
        }
        LOGGER.info(() -> "Difficulty set to " + args[0] + " by " + sender.name());
        return "Set difficulty to " + args[0];
    }

    /** /defaultgamemode &lt;mode&gt;: the join default, persisted world/data. */
    private String defaultGamemodeCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /defaultgamemode <survival|creative|adventure|spectator>";
        }
        GameMode mode;
        try {
            mode = GameMode.parse(args[0]);
        } catch (IllegalArgumentException unknown) {
            return "Unknown game mode: " + args[0];
        }
        defaultGamemodeId = mode.legacyId();
        saveDefaultGamemode(mode.legacyId());
        return "The default game mode is now " + mode.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The persisted join default (world/data/defaultgamemode.json: id). */
    private int loadDefaultGamemode() {
        java.nio.file.Path file = config.worldDataDir().resolve("defaultgamemode.json");
        if (!java.nio.file.Files.exists(file)) {
            return 0;
        }
        try {
            String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            return Integer.parseInt(fieldOf(json, "id"));
        } catch (Exception e) {
            LOGGER.warning(() -> "Unreadable defaultgamemode.json ignored: " + e.getMessage());
            return 0;
        }
    }

    private void saveDefaultGamemode(int id) {
        java.nio.file.Path file = config.worldDataDir().resolve("defaultgamemode.json");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file,
                    String.format(java.util.Locale.ROOT, "{\"id\": %d}%n", id),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOGGER.warning(() -> "defaultgamemode.json write failed: " + e.getMessage());
        }
    }

    /** /weather &lt;clear|rain&gt; [seconds]: the Change Game State broadcast. */
    private String weatherCommand(CommandSender sender, String[] args) {
        // WeatherCommand.run lines 19-52: three arms over the shared
        // duration — the explicit seconds gate 1..1000000, the default
        // (300 + nextInt(600)) * 20 (5-15 minutes).
        if (args.length < 1 || args.length > 2
                || !(args[0].equalsIgnoreCase("clear")
                || args[0].equalsIgnoreCase("rain")
                || args[0].equalsIgnoreCase("thunder"))) {
            return "Usage: /weather <clear|rain|thunder> [seconds]";
        }
        int durationTicks = net.zaminmc.torch.server.weather.WeatherSystem
                .defaultDurationTicks();
        if (args.length >= 2) {
            try {
                int seconds = Integer.parseInt(args[1]);
                if (seconds < 1 || seconds > 1_000_000) {
                    return "Duration must be 1..1000000 seconds";
                }
                durationTicks = seconds * 20;
            } catch (NumberFormatException malformed) {
                return "Duration must be seconds: /weather <clear|rain|thunder> [seconds]";
            }
        }
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        if (cycle == null) {
            return "The world is not up yet";
        }
        String arm = args[0].toLowerCase(java.util.Locale.ROOT);
        cycle.setWeather(!arm.equals("clear"), arm.equals("thunder"), durationTicks);
        return arm.equals("clear") ? "Set the weather to clear"
                : arm.equals("rain") ? "Set the weather to rain"
                : "Set the weather to thunder";
    }

    /**
     * The weather cycle's own commit surface (the public engine API over
     * the 1.8.8 model — the pre-20b boolean setter kept as the delegate).
     * Safe from any thread; the tick owns the walk.
     */
    public void setWeather(boolean rain, long durationTicks) {
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        if (cycle != null) {
            cycle.setWeather(rain, false,
                    durationTicks < 0 ? net.zaminmc.torch.server.weather.WeatherSystem
                            .defaultDurationTicks() : (int) Math.min(Integer.MAX_VALUE, durationTicks));
        }
    }

    /** @return whether the rain flag is set (the fire environment's read). */
    public boolean raining() {
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        return cycle != null && cycle.raining();
    }

    /** @return the overworld's weather cycle (present once the world is up). */
    public net.zaminmc.torch.server.weather.WeatherSystem weather() {
        return weather;
    }

    /** The weather walk (the tick handler drives the whole cycle). */
    private void tickWeather() {
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        if (cycle != null) {
            cycle.tick();
        }
    }

    /** Adapter for gameplay commands that only an in-world player may use. */
    private static java.util.function.BiFunction<CommandSender, String[], String> playerCommand(
            java.util.function.BiFunction<PlayerSession, String[], String> executor) {
        return (sender, args) -> {
            PlayerSession player = sender.player();
            if (player == null) {
                return "This command must be run by a player.";
            }
            return executor.apply(player, args);
        };
    }

    /**
     * /gamemode &lt;mode&gt; [player]: switches the per-player game mode.
     * The target learns through Change Game State 3 + Player Abilities; the
     * server-side interaction rules follow the session's mode from now on.
     */
    private String gamemodeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /gamemode <survival|creative|adventure|spectator> [player]";
        }
        GameMode mode;
        try {
            mode = GameMode.parse(args[0]);
        } catch (IllegalArgumentException unknown) {
            return "Unknown game mode: " + args[0];
        }
        PlayerSession target;
        if (args.length >= 2) {
            target = players.byName(args[1]).orElse(null);
            if (target == null) {
                return "No online player named " + args[1];
            }
        } else {
            if (sender.player() == null) {
                return "Console must name a player: /gamemode <mode> <player>";
            }
            target = sender.player();
        }
        setGamemode(target, mode);
        return "Set " + target.name() + "'s game mode to "
                + mode.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** /op &lt;player&gt;: grants the operator level and persists ops.json. */
    private String opCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /op <player>";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            return "No online player named " + args[0];
        }
        opStore.op(target.uuid(), target.name(), 4);
        target.setOpLevel(4);
        systemMessage(target, "You are now op!");
        LOGGER.info("Made " + target.name() + " a server operator (by " + sender.name() + ")");
        return "Made " + target.name() + " a server operator";
    }

    /** /deop &lt;player&gt;: revokes the operator level and persists ops.json. */
    private String deopCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /deop <player>";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            // Offline revoke: ops.json may still carry the name.
            return opStore.deop(args[0]) ? "Revoked operator status of " + args[0]
                    : "No online player named " + args[0];
        }
        boolean removed = opStore.deop(target.name());
        target.setOpLevel(0);
        systemMessage(target, "You are no longer op!");
        LOGGER.info("Revoked operator status of " + target.name() + " (by " + sender.name() + ")");
        return removed ? "Revoked operator status of " + target.name()
                : target.name() + " was not an operator";
    }

    /** /kick &lt;player&gt; [reason...]: the disconnect with the operator's words. */
    private String kickCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /kick <player> [reason...]";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            return "No online player named " + args[0];
        }
        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Kicked by an operator.";
        target.link().kick(reason);
        return "Kicked " + target.name() + ": " + reason;
    }

    /** /say &lt;message...&gt;: the historical broadcast with the [Server] tag. */
    private String sayCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /say <message...>";
        }
        String line = "[" + sender.name() + "] " + String.join(" ", args);
        for (PlayerSession player : players.all()) {
            systemMessage(player, line);
        }
        LOGGER.info(line);
        return null; // the broadcast itself is the feedback
    }

    /**
     * Pushes a system line to one player (command feedback, /say, op notes).
     * Safe from any thread; delivery rides the tick thread like chat.
     */
    public void systemMessage(PlayerSession recipient, String content) {
        ticker.submit(() -> {
            for (ChatListener listener : chatListeners) {
                listener.onSystemMessage(recipient, content);
            }
        });
    }

    /**
     * Switches a player's game mode (the /gamemode path): the session state
     * flips on the tick thread, then Change Game State 3 + Player Abilities
     * tell the client, and the body display re-syncs.
     */
    public void setGamemode(PlayerSession session, GameMode mode) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(mode, "mode");
        ticker.submit(() -> setGamemodeOnTick(session, mode));
    }

    private void setGamemodeOnTick(PlayerSession session, GameMode mode) {
        if (session.state() != PlayerState.PLAYING) {
            return;
        }
        session.setGamemode(mode);
        session.link().updateGamemode(mode.legacyId());
        // The protocol-47 abilities map: 0x01 invulnerable, 0x02 flying,
        // 0x04 may-fly, 0x08 instant build — the historical grant. Flight
        // rights follow the mode (the movement guard reads the same flag).
        int flags = 0;
        boolean mayFly = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        if (mayFly) {
            flags |= 0x01 | 0x04 | 0x08;
        }
        session.setAllowedToFly(mayFly);
        if (!mayFly) {
            session.setFlying(false); // leaving flight: the client re-announces
            session.clearHoverTicks();
        } else {
            session.setGraceTicks(40); // the mode-switch re-anchor burst
        }
        session.link().updateAbilities(flags);
        publishBodyChanged(session);
        LOGGER.info("Game mode of " + session.name() + " set to "
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Set Creative Slot (0x10): the creative inventory's authoritative write.
     * Creative sessions only; the client-claimed stack lands in the mapped
     * engine slot (window 0's historical ContainerPlayer mapping). Safe from
     * any thread; the write runs on the tick thread.
     */
    public void setCreativeSlot(PlayerSession session, int wireSlot,
                                net.zaminmc.torch.item.ItemStack stack) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(stack, "stack");
        ticker.submit(() -> setCreativeSlotOnTick(session, wireSlot, stack));
    }

    private void setCreativeSlotOnTick(PlayerSession session, int wireSlot,
                                       net.zaminmc.torch.item.ItemStack stack) {
        if (session.state() != PlayerState.PLAYING
                || session.gamemode() != GameMode.CREATIVE) {
            return; // the vanilla guard against faked creative packets
        }
        if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
            session.crafting().setCell(wireSlot - WIRE_SLOT_CRAFT_FIRST, stack);
        } else if (wireSlot >= WIRE_SLOT_CRAFT_LAST + 1 && wireSlot <= WIRE_SLOT_CRAFT_LAST + 4) {
            return; // armor cells ride the window clicks, not the creative menu
        } else {
            int engineSlot = engineSlotOf(wireSlot);
            if (engineSlot < 0) {
                return; // result slot and unknown cells: not a creative write
            }
            session.inventory().setSlot(engineSlot, stack);
        }
        publishInventoryChanged(session);
    }

    /**
     * The console command line routed through the same dispatcher the chat
     * slash commands use (ConsoleSender, full operator level). Safe from any
     * thread; the dispatch runs on the tick thread like player commands.
     */
    public void consoleCommand(String raw) {
        // The historical console types commands bare ("weather rain 30");
        // the dispatcher speaks slash intents, so the console form gets one.
        String line = raw.startsWith("/") ? raw : "/" + raw;
        ticker.submit(() -> chatService.dispatchCommand(new ConsoleSender(), line));
    }

    /**
     * The {@code /gamerule} command (Slice 24a — GameRuleCommand 1:1):
     * the bare form lists every rule in the reference's listArgs shape
     * ({@code "a, b, c and d"}), the one-arg form answers the query
     * line, and the set arm validates boolean rules before the write.
     * Level 2 (the reference's own permission). Runs on the tick thread
     * through chat dispatch — the same lane the engine's own reads use.
     */
    private String gameruleCommand(CommandSender sender, String[] args) {
        net.zaminmc.torch.server.gamerule.GameRules rules = gameRules;
        switch (args.length) {
            case 0 -> {
                // listArgs (AbstractCommand lines 370-382): comma-separated
                // with " and " before the last name.
                StringBuilder list = new StringBuilder();
                var names = rules.names();
                int index = 0;
                for (String name : names) {
                    if (index > 0) {
                        list.append(index == names.size() - 1 ? " and " : ", ");
                    }
                    list.append(name);
                    index++;
                }
                return list.toString();
            }
            case 1 -> {
                if (!rules.contains(args[0])) {
                    return "No game rule called '" + args[0] + "' exists";
                }
                return args[0] + " = " + rules.get(args[0]);
            }
            default -> {
                // parseString(args, 1): the value is the remaining words
                // joined — the engine's command splitter already hands the
                // raw tail.
                String value = String.join(" ", java.util.Arrays.asList(args)
                        .subList(1, args.length));
                if (rules.isType(args[0],
                        net.zaminmc.torch.server.gamerule.GameRules.Type.BOOLEAN_VALUE)
                        && !"true".equals(value) && !"false".equals(value)) {
                    return "'" + value + "' is not true or false";
                }
                rules.set(args[0], value);
                return "Game rule " + args[0] + " has been updated to: " + value;
            }
        }
    }

    /**
     * The {@code /time} command's own walk: the query line, the add arm,
     * and the set arm over the named stops. The clock lands on the
     * tick thread through chat dispatch; every client hears the new time
     * through the time listeners.
     */
    private String timeCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("query")) {
            return "Time: " + world.timeOfDay() + " / " + MobManager.DAY_LENGTH
                    + " (total " + world.totalTicks() + ")";
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("add")) {
            // The reference's addToTimeOfDay arm (TimeCommand lines 85-95):
            // the shift lands on EVERY world server.
            long add;
            try {
                add = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                return "Not a time: " + args[1];
            }
            world.setTimeOfDay(world.timeOfDay() + add);
            if (netherWorld != null) {
                netherWorld.setTimeOfDay(netherWorld.timeOfDay() + add);
            }
            publishTimeChanged();
            return "Added " + add + " to the time";
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("set")) {
            return "Usage: /time query | set <day|noon|night|midnight|ticks> | add <ticks>";
        }
        long timeOfDay;
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> timeOfDay = 1_000;
            case "noon" -> timeOfDay = 6_000;
            case "night" -> timeOfDay = MobManager.NIGHT_START;
            case "midnight" -> timeOfDay = 18_000;
            default -> {
                try {
                    timeOfDay = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    return "Not a time: " + args[1];
                }
            }
        }
        if (timeOfDay < 0 || timeOfDay >= MobManager.DAY_LENGTH) {
            return "Time must be 0.." + (MobManager.DAY_LENGTH - 1);
        }
        // The reference's setTimeOfDay arm (TimeCommand lines 86-90): the
        // clock lands on EVERY world server — the nether's clock walks too.
        world.setTimeOfDay(timeOfDay);
        if (netherWorld != null) {
            netherWorld.setTimeOfDay(timeOfDay);
        }
        publishTimeChanged();
        return "Time set to " + timeOfDay;
    }

    /**
     * /spawnmob &lt;pig|cow|chicken|zombie&gt; [count]: spawns a small group of
     * the kind around the sender on the surface — the controlled entry point
     * for real-client validation. Runs on the tick thread through chat dispatch.
     */
    /**
     * /rename &lt;name...&gt;: renames the held stack (the historical
     * tag.display.Name — the anvil rename without the anvil). Joins arguments
     * with spaces; no arguments clears the name. The wire carries it as the
     * slot encoding's NBT compound, so every container, drop and pickup
     * preserves it. Runs on the tick thread through chat dispatch.
     */
    private String renameCommand(PlayerSession sender, String[] args) {
        if (sender.inventory().held().isEmpty()) {
            return "Hold an item to rename it";
        }
        String name = args.length == 0 ? "" : String.join(" ", args);
        if (name.length() > net.zaminmc.torch.item.ItemStack.MAX_NAME_LENGTH) {
            return "Name too long (max " + net.zaminmc.torch.item.ItemStack.MAX_NAME_LENGTH + ")";
        }
        sender.inventory().renameHeld(name);
        publishInventoryChanged(sender);
        return name.isEmpty() ? "Name cleared" : "Renamed to " + name;
    }

    /**
     * The vanilla summon (Slice 67 — SummonCommand.run lines 37-92, the
     * engine's envelope): the entity name (the 1.8.8 registration names,
     * case-insensitive — "Zombie", "PigZombie", "Giant" — plus the engine
     * kind names), the optional ~-relative coordinates from the sender's
     * feet, the LightningBolt special (the engine's strike verb with the
     * difficulty fire gate), the chunk-loaded refusal, and the vanilla
     * feedback strings. Runs on the tick thread through chat dispatch.
     */
    private String summonCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /summon <entity> [x y z]";
        }
        String name = args[0];
        double x = sender.position().x();
        double y = sender.position().y();
        double z = sender.position().z();
        if (args.length >= 4) {
            try {
                x = parseRelativeCoordinate(x, args[1]);
                y = parseRelativeCoordinate(y, args[2]);
                z = parseRelativeCoordinate(z, args[3]);
            } catch (NumberFormatException bad) {
                return "Invalid coordinate";
            }
        }
        var position = new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (!world.isChunkLoadedAt(position)) {
            return "The target position is not loaded";
        }
        // The LightningBolt special (lines 60-63: addGlobalEntity).
        if ("LightningBolt".equals(name)) {
            lightning.strike((int) Math.floor(x), y, (int) Math.floor(z),
                    true);
            return "Object successfully summoned";
        }
        MobType type = MobType.byVanillaName(name);
        if (type == null) {
            type = MobType.byName(name);
        }
        if (type == null) {
            return "Unable to summon object";
        }
        MobEntity spawned = mobManager.spawnAt(type, new Position(x, y, z));
        return spawned != null
                ? "Object successfully summoned" : "Unable to summon object";
    }

    /**
     * The vanilla coordinate parse (CommandBase.parseCoordinate): the
     * bare {@code ~} keeps the source value, {@code ~n} offsets it, a
     * plain number replaces it.
     */
    private static double parseRelativeCoordinate(double source, String token) {
        if (token.startsWith("~")) {
            if (token.length() == 1) {
                return source;
            }
            return source + Double.parseDouble(token.substring(1));
        }
        return Double.parseDouble(token);
    }

    private String spawnMobCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /spawnmob <pig|cow|chicken|sheep|zombie|skeleton|creeper|spider|horse|villager> [count]";
        }
        MobType type = MobType.byName(args[0]);
        if (type == null) {
            return "Unknown mob: " + args[0];
        }
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return "Not a count: " + args[1];
            }
            if (count < 1 || count > 10) {
                return "Count must be 1..10";
            }
        }
        int spawned = mobManager.spawnGroup(type, sender.position(), count).size();
        return "Spawned " + spawned + " x " + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * /give &lt;name&gt; [count] [metadata]: grants the item into the player's
     * inventory (fill order as pickup). The optional metadata is the
     * historical damage field's variant role — charcoal is {@code /give coal 1
     * 1} (community items.json variant, the client names it from the value).
     * Runs on the tick thread through chat dispatch.
     */
    private String giveCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /give <item> [count] [metadata]";
        }
        String rawName = args[0];
        String name = rawName.contains(":") ? rawName : "minecraft:" + rawName;
        net.zaminmc.torch.item.ItemType type = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(name)).orElse(null);
        if (type == null) {
            return "Unknown item: " + rawName;
        }
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return "Not a count: " + args[1];
            }
            if (count < 1 || count > type.maxStackSize()) {
                return "Count must be 1.." + type.maxStackSize() + " for " + type.displayName();
            }
        }
        int metadata = 0;
        if (args.length >= 3) {
            try {
                metadata = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                return "Not a metadata value: " + args[2];
            }
            if (metadata < 0 || metadata > net.zaminmc.torch.item.ItemStack.MAX_DAMAGE) {
                return "Metadata must be 0.." + net.zaminmc.torch.item.ItemStack.MAX_DAMAGE;
            }
        }
        net.zaminmc.torch.item.ItemStack granted;
        try {
            granted = net.zaminmc.torch.item.ItemStack.of(type, count).withDamage(metadata);
        } catch (IllegalArgumentException invalidVariant) {
            return "Cannot grant that metadata: " + invalidVariant.getMessage();
        }
        net.zaminmc.torch.item.ItemStack remainder = sender.inventory().pickUp(granted);
        publishInventoryChanged(sender);
        int given = count - remainder.count();
        String variantName = metadata != 0 ? " (metadata " + metadata + ")" : "";
        return remainder.isEmpty()
                ? "Given " + given + " x " + type.displayName() + variantName
                : "Inventory full: gave " + given + " of " + count;
    }

    private void publishChat(ChatService.ChatEvent event) {
        for (ChatListener listener : chatListeners) {
            if (event instanceof ChatService.PublicChat publicChat) {
                listener.onChatMessage(publicChat.sender(), publicChat.content());
            } else if (event instanceof ChatService.SystemToPlayer system) {
                listener.onSystemMessage(system.recipient(), system.content());
            }
        }
    }

    /**
     * Persists world state on the owning tick thread (consistent snapshot) and
     * waits for durability. Safe from any thread; no-op if the world is absent.
     */
    public void saveAllNow() {
        if (world == null || ticker == null) {
            return;
        }
        java.util.concurrent.CountDownLatch saved = new java.util.concurrent.CountDownLatch(1);
        ticker.submit(() -> {
            try {
                // Mid-fall blocks land now: blocks persist, entities do not.
                if (fallingEntities != null) {
                    fallingEntities.finishAllFalls();
                }
                worldStorage.save(world.snapshotDeltas());
                if (netherStorage != null && netherWorld != null) {
                    netherStorage.save(netherWorld.snapshotDeltas());
                }
                if (furnaceStore != null && furnaceManager != null) {
                    furnaceStore.save(furnaceManager.snapshot());
                }
                if (chestStore != null && chestManager != null) {
                    chestStore.save(chestManager.snapshot());
                }
                if (hopperStore != null && hopperSystem != null) {
                    hopperStore.save(hopperSystem.snapshot());
                }
                if (noteStore != null && noteBlocks != null) {
                    noteStore.save(noteBlocks.snapshotTiles());
                }
                if (jukeboxStore != null && jukeboxes != null) {
                    jukeboxStore.save(jukeboxes.snapshotRecords());
                }
                if (decorationStore != null && decorations != null) {
                    net.zaminmc.torch.server.decoration.DecorationSystem.Snapshot decorationSaved =
                            decorations.snapshot();
                    decorationStore.save(decorationSaved.frames(), decorationSaved.paintings());
                }
                if (armorStandStore != null && armorStands != null) {
                    armorStandStore.save(armorStands.snapshot());
                }
                if (enderCrystalStore != null && enderCrystals != null) {
                    // Slice 62 — the destroyed-pillar marks (the crystals
                    // themselves rebuild from the deterministic decorate).
                    enderCrystalStore.save(enderCrystals.destroyedCellsSnapshot());
                }
                if (dispenserStore != null && dispenserSystem != null) {
                    dispenserStore.save(dispenserSystem.snapshot());
                }
                if (brewingStore != null && brewingStands != null) {
                    brewingStore.save(brewingStands.snapshot());
                }
                if (beaconStore != null && beaconSystem != null) {
                    beaconStore.save(beaconSystem.snapshot());
                }
                if (signStore != null && signManager != null) {
                    signStore.save(signManager.snapshot());
                }
                if (weatherStore != null && weather != null) {
                    weatherStore.save(weather.snapshot());
                }
                if (gameRuleStore != null && gameRules != null) {
                    gameRuleStore.save(gameRules.entries());
                }
                if (mobStore != null && mobManager != null) {
                    mobStore.save(mobManager.snapshot());
                }
                if (vehicleStore != null && vehicleManager != null) {
                    vehicleStore.save(vehicleManager.snapshot());
                }
            } finally {
                saved.countDown();
            }
        });
        try {
            if (!saved.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warning("World save did not complete within 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Registers an internal world observer (e.g. the protocol adapter's sync). */
    public void addWorldListener(WorldChangeListener listener) {
        if (world == null) {
            throw new IllegalStateException("World listener registration requires the booted world");
        }
        world.addChangeListener(listener);
    }

    // World-change fan-out lives on the world itself (§208): every committed
    // mutation fires the listeners once, whether a player or a rule drove it.

    // ------------------------------------------------------------------ EngineBridge

    @Override
    public JoinResult joinRequest(ClientLink link, String username, UUID offlineUuid) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(offlineUuid, "offlineUuid");

        if (state.get() != ServerState.RUNNING) {
            return new EngineBridge.Rejected("Server is not accepting players");
        }
        if (!VALID_NAME.matcher(username).matches()) {
            return new EngineBridge.Rejected("Invalid username");
        }
        // The ban gate (the historical banned-players.json check, offline-uuid
        // keyed): a banned name stays banned across relogins.
        BanStore.Entry ban = banStore.banOf(offlineUuid, username);
        if (ban != null) {
            return new EngineBridge.Rejected("You are banned from this server.\nReason: "
                    + ban.reason());
        }
        // The IP ban gate (banned-ips.json): a banned address stays out
        // regardless of the name it arrives under.
        IpBanStore.Entry ipBan = ipBanStore.banOf(link.remoteIp());
        if (ipBan != null) {
            return new EngineBridge.Rejected("Your IP address is banned from this server.\nReason: "
                    + ipBan.reason());
        }
        // The whitelist gate: only when enforcement is on (server.properties
        // boot flag, or /whitelist on at runtime).
        if (whitelistEnforced && !whitelistStore.contains(offlineUuid, username)) {
            return new EngineBridge.Rejected("You are not whitelisted on this server!");
        }
        synchronized (players) {
            if (players.size() >= config.maxPlayers()) {
                return new EngineBridge.Rejected("Server is full");
            }
            if (players.isNameTaken(username)) {
                return new EngineBridge.Rejected("You are already connected");
            }
            Optional<PlayerSession> existing = players.byUuid(offlineUuid);
            if (existing.isPresent()) {
                return new EngineBridge.Rejected("You are already connected");
            }
            // Returning players re-enter at their saved spot (§407 spirit: restart
            // survival). The small per-player file read happens on the joining
            // channel's event loop - one seek-and-read, no gameplay lock held.
            Optional<PlayerSnapshot> saved = playerStore == null
                    ? Optional.empty() : playerStore.load(offlineUuid);
            PlayerSession session = new PlayerSession(offlineUuid, username, link);
            // The API's give() re-syncs the wire exactly like the engine's own
            // pickup paths (§27.5: the handle resolves to live behavior).
            session.setInventorySyncHook(this::publishInventoryChanged);
            // The engine-global identity (the projectile thrower band) rides in
            // from here; the wire-local ids remain the adapter's business.
            session.assignEngineEntityId(nextPlayerEntityId++);
            players.register(session);
            session.authenticate();
            // The personal game mode survives restarts (ZPD v4); a fresh player
            // inherits the server default (server.properties, overridden by
            // /defaultgamemode's persisted choice).
            session.setGamemode(saved.map(PlayerSnapshot::gamemodeId)
                    .filter(id -> id >= 0)
                    .map(GameMode::byLegacyId)
                    .filter(java.util.Objects::nonNull)
                    .orElse(GameMode.byLegacyId(defaultGamemodeId) != null
                            ? GameMode.byLegacyId(defaultGamemodeId) : config.gamemode()));
            session.setOpLevel(opStore.level(offlineUuid));
            Position spawn = saved.map(PlayerSnapshot::position)
                    .orElseGet(world::spawnPosition);
            session.beginJoin(world, spawn);
            // The join burst (spawn chunks, position re-anchor) rides grace.
            session.setGraceTicks(100);
            // Flight rights follow the mode: creative and spectator may fly.
            session.setAllowedToFly(session.gamemode() == GameMode.CREATIVE
                    || session.gamemode() == GameMode.SPECTATOR);
            saved.ifPresent(snapshot -> restorePlayer(session, snapshot));
            // §27.7: the public join observation + the vanilla yellow
            // broadcast (PlayerManager lines 136-145: multiplayer.player.
            // joined in yellow — the broadcast runs BEFORE the reference's
            // add(player), so the newcomer never sees their own line; the
            // engine's early register skips the joiner the same way).
            // The event fires after the session is fully constructed but
            // before the broadcast leaves — the mutable line is what the
            // players see; null suppresses it entirely.
            net.zaminmc.torch.event.player.PlayerJoinEvent joinEvent =
                    new net.zaminmc.torch.event.player.PlayerJoinEvent(
                            session, session.name() + " joined the game");
            eventBus.fire(joinEvent);
            String joinLine = joinEvent.joinMessage();
            if (joinLine != null) {
                for (PlayerSession online : players.all()) {
                    if (online == session) {
                        continue; // the joiner never sees their own line (1.8.8)
                    }
                    systemMessage(online, "\u00a7e" + joinLine);
                }
            }
            LOGGER.info(() -> "Player joined: " + username + " (" + offlineUuid + ")"
                    + saved.map(s -> " [restored]").orElse(""));
            return new EngineBridge.Accepted(session);
        }
    }

    /**
     * Applies a saved snapshot to a joining session: look, inventory, held slot.
     * Position already came through beginJoin. Saved items the current registry
     * cannot resolve are dropped with a warning - the world moved on, the rest
     * of the survival state stays intact.
     */
    private void restorePlayer(PlayerSession session, PlayerSnapshot snapshot) {
        session.applyMovement(session.position(), snapshot.rotation(), true);
        // ZPD v6: the experience total rides the file tail (0 in older files).
        session.setTotalXp(snapshot.totalXp());
        // ZPD v7: the bed spawn returns with the player (null in older files).
        session.setBedSpawn(snapshot.bedSpawn());
        java.util.List<net.zaminmc.torch.item.ItemStack> restored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(net.zaminmc.torch.server.player.PlayerInventory.TOTAL_SLOTS,
                        net.zaminmc.torch.item.ItemStack.EMPTY));
        java.util.List<net.zaminmc.torch.item.ItemStack> armorRestored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(net.zaminmc.torch.server.player.PlayerInventory.ARMOR_SLOTS,
                        net.zaminmc.torch.item.ItemStack.EMPTY));
        for (PlayerSnapshot.SlotStack saved : snapshot.slots()) {
            Optional<net.zaminmc.torch.item.ItemType> type =
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(saved.item());
            if (type.isEmpty()) {
                LOGGER.warning(() -> "Saved item no longer registered, dropped: " + saved.item());
                continue;
            }
            try {
                net.zaminmc.torch.item.ItemStack stack = net.zaminmc.torch.item.ItemStack.of(type.get(), saved.count())
                        .withDamage(saved.damage());
                if (saved.slot() >= net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE) {
                    // ZPD v5 namespace: slots 36-39 are the armor row (head, chest, legs, feet).
                    armorRestored.set(saved.slot()
                            - net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE, stack);
                } else {
                    restored.set(saved.slot(), stack);
                }
            } catch (IllegalArgumentException invalid) {
                LOGGER.warning(() -> "Saved slot dropped (invalid values): " + saved + " - "
                        + invalid.getMessage());
            }
        }
        try {
            session.inventory().restore(restored, snapshot.heldSlot());
            session.inventory().restoreArmor(armorRestored);
        } catch (IllegalArgumentException invalid) {
            LOGGER.warning("Inventory restore rejected for " + session.name() + ": "
                    + invalid.getMessage());
        }
        // ZPD v8: the ender chest grid returns with the player (the
        // reference's EnderItems — an empty grid in older files).
        java.util.List<net.zaminmc.torch.item.ItemStack> enderRestored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(
                        net.zaminmc.torch.server.chest.ChestBlockEntity.SLOT_COUNT,
                        net.zaminmc.torch.item.ItemStack.EMPTY));
        for (PlayerSnapshot.SlotStack saved : snapshot.enderSlots()) {
            Optional<net.zaminmc.torch.item.ItemType> type =
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(saved.item());
            if (type.isEmpty()) {
                LOGGER.warning(() -> "Saved ender item no longer registered, dropped: "
                        + saved.item());
                continue;
            }
            enderRestored.set(saved.slot(), net.zaminmc.torch.item.ItemStack.of(
                    type.get(), saved.count()).withDamage(saved.damage()));
        }
        session.restoreEnderChest(enderRestored);
        // ZPD v9: the statistics return with the player (the stats slice —
        // an empty map in older files).
        session.stats().load(snapshot.stats());
        session.setBody(snapshot.health(), snapshot.food(), snapshot.saturation());
    }

    /** The persistable view of a live session (position is volatile-read, inventory snapshotted). */
    private PlayerSnapshot snapshotOf(PlayerSession session) {
        java.util.List<PlayerSnapshot.SlotStack> filled = new java.util.ArrayList<>();
        java.util.List<net.zaminmc.torch.item.ItemStack> slots = session.inventory().snapshot();
        for (int i = 0; i < slots.size(); i++) {
            net.zaminmc.torch.item.ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            filled.add(new PlayerSnapshot.SlotStack(i, stack.type().identifier(),
                    stack.count(), stack.damage(), stack.displayName()));
        }
        // ZPD v5: the armor row rides the slot namespace at 36-39 (head, chest, legs, feet).
        java.util.List<net.zaminmc.torch.item.ItemStack> armor = session.inventory().armorSnapshot();
        for (int i = 0; i < armor.size(); i++) {
            net.zaminmc.torch.item.ItemStack piece = armor.get(i);
            if (piece.isEmpty()) {
                continue;
            }
            filled.add(new PlayerSnapshot.SlotStack(
                    net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE + i,
                    piece.type().identifier(), piece.count(), piece.damage(), piece.displayName()));
        }
        // ZPD v8: the ender chest tail (the player's private 27-slot grid).
        java.util.List<PlayerSnapshot.SlotStack> ender = new java.util.ArrayList<>();
        net.zaminmc.torch.item.ItemStack[] enderSlots = session.enderChest().snapshotSlots();
        for (int i = 0; i < enderSlots.length; i++) {
            net.zaminmc.torch.item.ItemStack stack = enderSlots[i];
            if (stack.isEmpty()) {
                continue;
            }
            ender.add(new PlayerSnapshot.SlotStack(i, stack.type().identifier(),
                    stack.count(), stack.damage(), stack.displayName()));
        }
        return new PlayerSnapshot(session.uuid(), session.name(), session.position(),
                session.rotation(), session.inventory().heldSlot(), filled,
                session.health(), session.food(), session.saturation(),
                session.gamemode().legacyId(), session.totalXp(), session.bedSpawn(), ender,
                session.stats().snapshot());
    }

    /**
     * Persists one player's state. Runs on the tick thread when called through
     * {@link #clientDisconnected} (ordered after any pending inventory work);
     * direct calls from shutdown are safe because the tick loop no longer
     * mutates player state at that point.
     */
    private void persistPlayer(PlayerSession session) {
        if (playerStore == null) {
            return;
        }
        try {
            playerStore.save(snapshotOf(session));
        } catch (RuntimeException e) {
            LOGGER.log(java.util.logging.Level.WARNING,
                    "Player save failed for " + session.name(), e);
        }
    }

    /**
     * Shutdown variant: submits the cursor-return + persist onto the tick thread
     * and waits, so the tick loop's ordering guarantees hold and the save is
     * durable before the loop stops.
     */
    private void persistPlayerNow(PlayerSession session) {
        if (playerStore == null || ticker == null) {
            return;
        }
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        ticker.submit(() -> {
            try {
                returnWindowCarriedItems(session, true);
                persistPlayer(session);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warning("Player save did not complete within 2s for " + session.name());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void joinCompleted(PlayerSession session) {
        session.markPlaying();
        // The join learns the sky state (PlayerManager's own join pair:
        // GameEventS2CPacket(7, rain) + (8, thunder) — the live gradients,
        // never the boolean).
        net.zaminmc.torch.server.weather.WeatherSystem cycle = weather;
        if (cycle != null && session.dimension() == 0) {
            session.link().playGameState(7, cycle.rainGradient());
            session.link().playGameState(8, cycle.thunderGradient());
        }
        LOGGER.info(() -> "Player in play state: " + session.name());
    }

    @Override
    public void movementProposal(PlayerSession session, Position position, Rotation rotation, boolean onGround) {
        // The credits gate (Slice 64 — the reference's handlePlayerMove
        // line 198: {@code !this.player.leavingTheEnd} — the removed body
        // answers no movement while the win screen plays).
        if (session.leavingTheEnd()) {
            return;
        }
        // The riding body is the vehicle's: the client sends no position
        // while mounted, so any stray proposal is discarded (the seat owns
        // the coordinates — the historical server-authoritative vehicle).
        if (session.ridingAny()) {
            return;
        }
        // The anti-cheat baseline (the mango-adopted shape): proposals outside
        // the historical physics envelope are dropped and the client is
        // snapped back to the authoritative position — the historical
        // "moved wrongly" behavior. The grace window (teleports, knockback,
        // join bursts) suspends the speed caps for honest bursts.
        Position from = session.position();
        if (!MovementGuard.permits(session, from, position, onGround,
                fluidAt(from.x(), from.y(), from.z())
                        || ladderAt(from.x(), from.y(), from.z()))) {
            session.link().resyncPosition();
            LOGGER.fine(() -> "Movement rejected for " + session.name()
                    + " (" + from + " -> " + position + ")");
            return;
        }
        // Per-channel ordering (transport guarantee) makes this safe.
        session.applyMovement(position, rotation, onGround);
    }

    @Override
    public void clientDisconnected(PlayerSession session, String reason) {
        if (session.state() == PlayerState.DISCONNECTED) {
            return;
        }
        // The seat opens on disconnect (the vehicle never holds a ghost).
        if (vehicleManager != null && session.ridingVehicleId() >= 0) {
            var ridden = vehicleManager.byId(session.ridingVehicleId());
            if (ridden != null) {
                vehicleManager.dismount(ridden);
            }
        }
        // The mob seat opens too (the horse never holds a ghost).
        if (mobManager != null && session.ridingMobId() >= 0) {
            MobEntity riddenMob = mobManager.byId(session.ridingMobId());
            if (riddenMob != null) {
                mobManager.dismountMob(riddenMob, false);
            }
        }
        session.markDisconnecting();
        session.markDisconnected();
        // The leave stat (ServerPlayNetworkHandler.onDisconnect's own
        // incrementStat) — before the persistence walk so the save carries it.
        session.stats().increment(
                net.zaminmc.torch.server.stat.EngineStats.GAMES_LEFT, 1);
        // §27.7: the public quit observation + the vanilla yellow broadcast
        // (ServerPlayNetworkHandler.onDisconnect lines 573-576:
        // multiplayer.player.left in yellow, sent BEFORE the registry
        // removes the player). The event fires while the handle still
        // resolves; the mutable line broadcasts to the players who stay.
        net.zaminmc.torch.event.player.PlayerQuitEvent quitEvent =
                new net.zaminmc.torch.event.player.PlayerQuitEvent(
                        session, session.name() + " left the game");
        eventBus.fire(quitEvent);
        String quitLine = quitEvent.quitMessage();
        players.unregister(session);
        if (quitLine != null) {
            for (PlayerSession online : players.all()) {
                systemMessage(online, "\u00a7e" + quitLine);
            }
        }
        // Persist on the tick thread so the save is ordered after any queued
        // inventory work for this player (pickup, wear, give). A carried cursor
        // stack goes back into the inventory first; a remainder is thrown so
        // nothing is lost (historical behavior for leaving with a held stack).
        if (ticker != null) {
            ticker.submit(() -> {
                returnWindowCarriedItems(session, true);
                persistPlayer(session);
            });
        }
        LOGGER.info(() -> "Player disconnected: " + session.name() + " (" + reason + ")");
    }

    /** Checks whether the chunk containing the position is available read-only. */
    public EngineChunk peekChunk(net.zaminmc.torch.block.ChunkPosition position) {
        return world.peek(position);
    }

    /** Floor-to-block-position helper for double-space queries. */
    private static net.zaminmc.torch.block.BlockPosition blockAt(double x, double y, double z) {
        return new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * Bounds-safe solidity for entity physics: the void below the world and
     * the sky above it hold no blocks (the 1.8 rule — bodies fall through
     * below y=0 until the kill plane instead of crashing the column lookup).
     */
    private boolean solidAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return WorldSolidity.isSolid(world.getBlock(blockAt(x, y, z)));
    }

    /**
     * The shape-aware point test (the collision-shape slice): the same
     * bounds guard, answered by the block's collision boxes at the point —
     * the connection families resolving their neighbours through the
     * overworld probe.
     */
    private boolean solidAtPoint(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return WorldSolidity.isSolidAt(world.getBlock(blockAt(x, y, z)),
                x, y, z, overworldProbe(x, y, z));
    }

    /**
     * The shape's top surface in the point's cell (the support bodies rest
     * on); outside the world column there is no support.
     */
    private double supportYAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return Double.NEGATIVE_INFINITY;
        }
        return WorldSolidity.supportY(world.getBlock(blockAt(x, y, z)),
                x, y, z, overworldProbe(x, y, z));
    }

    /**
     * The neighbour probe the connection families resolve against (the
     * collision-shape slice): the overworld's own block reads at the
     * offsets from the queried cell.
     */
    private CollisionShapes.NeighborProbe overworldProbe(double x, double y, double z) {
        net.zaminmc.torch.block.BlockPosition origin = blockAt(x, y, z);
        return (dx, dy, dz) -> {
            net.zaminmc.torch.block.BlockPosition at = origin.offset(dx, dy, dz);
            if (at.y() < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || at.y() > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return null;
            }
            return world.getBlock(at);
        };
    }

    /**
     * One adapter serves the three physics query interfaces (items, falling
     * blocks, projectiles): the boolean methods keep the full-cube rule for
     * existing callers, the shape methods answer the collision-shape slice.
     */
    private final class ShapeGround implements
            ItemEntity.Ground, FallingBlockEntity.Ground, ProjectileManager.SolidQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return solidAt(x, y, z);
        }

        @Override
        public boolean solid(double x, double y, double z) {
            return solidAt(x, y, z);
        }

        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return solidAtPoint(x, y, z);
        }

        @Override
        public boolean solidAt(double x, double y, double z) {
            return solidAtPoint(x, y, z);
        }

        @Override
        public double supportY(double x, double y, double z) {
            return supportYAt(x, y, z);
        }
    }

    /** Bounds-safe fluid lookup: nothing outside the world column is fluid. */
    /**
     * The out-of-column rule as a block lookup: y outside the strict column
     * reads as air (the 1.8 rule) instead of throwing — the mob contact
     * queries (fire, cactus) sample the cell below a bedrock stander, which
     * is legitimately out of bounds. Any thread.
     */
    private net.zaminmc.torch.block.BlockType blockAtBoundsSafe(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return world.airType();
        }
        return world.getBlock(blockAt(x, y, z));
    }

    private boolean fluidAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return FluidBlocks.kindOf(world.getBlock(blockAt(x, y, z)).identifier()) != null;
    }

    /**
     * Ensures a chunk is loaded without violating world ownership: generation is
     * scheduled onto the world's owner thread and the callback runs there.
     * Safe to call from any thread. If the chunk already exists, the callback
     * runs immediately on the caller's thread.
     */
    public void requestChunkLoad(net.zaminmc.torch.block.ChunkPosition position,
                                 java.util.function.Consumer<EngineChunk> onLoaded) {
        requestChunkLoad(0, position, onLoaded);
    }

    /**
     * The dimension-aware detached chunk load: the request reads and
     * generates against the DIMENSION's world, and the pending keys tag the
     * dimension (the same chunk coordinates exist in both worlds — an
     * untagged key would cross the streams).
     */
    public void requestChunkLoad(int dimension, net.zaminmc.torch.block.ChunkPosition position,
                                 java.util.function.Consumer<EngineChunk> onLoaded) {
        Objects.requireNonNull(onLoaded, "onLoaded");
        EngineWorld source = worldFor(dimension);
        long key = dimensionKey(dimension, position.packed());
        EngineChunk existing = source.peek(position);
        if (existing != null) {
            onLoaded.accept(existing);
            return;
        }
        // The permanent architecture's first live offload (the design §13
        // chunk contract): the detached terrain generation leaves the tick —
        // the compute pool generates over the immutable input (the chunk
        // position; the generator is a pure function of seed + coordinates),
        // and the owner installs at its drain boundary. The pending maps
        // deduplicate concurrent requests; a result for a chunk another path
        // already published is discarded (never overwrite newer state); the
        // installation and the callbacks run inside the owner's drain
        // context (the design's §4 sequence: snapshot compute, validate,
        // owner applies).
        java.util.Queue<java.util.function.Consumer<EngineChunk>> callbacks =
                pendingChunkLoads.computeIfAbsent(key,
                        k -> new java.util.concurrent.ConcurrentLinkedQueue<>());
        callbacks.add(onLoaded);
        if (!pendingChunkGenerations.add(key)) {
            return; // a job is already in flight; this callback rides its install
        }
        if (computeSubsystem == null) {
            // The no-compute boot shape (partial test engines): the legacy
            // synchronous path on the tick thread.
            pendingChunkGenerations.remove(key);
            pendingChunkLoads.remove(key);
            ticker.submit(() -> {
                EngineChunk chunk = source.getOrGenerate(position);
                onLoaded.accept(chunk);
            });
            return;
        }
        computeSubsystem.submit(simulationDomain, position,
                pos -> generateDetachedChunk(source, pos),
                (chunk, generation) -> installGeneratedChunk(dimension, position, chunk),
                () -> {
                    // The stale arm (an ownership transfer superseded the
                    // snapshot): the in-flight marker clears so a retry can
                    // regenerate; the waiting callbacks ride the next request
                    // (the single-domain live run never transfers mid-run).
                    pendingChunkGenerations.remove(key);
                });
    }

    /** The pending-map key: the dimension rides the high bits (the same chunk coordinates exist in both worlds). */
    private static long dimensionKey(int dimension, long packed) {
        return ((long) (dimension & 0xFFFF) << 48) | (packed & 0x0000FFFFFFFFFFL);
    }

    /** The pure detached generation over the immutable input (compute-worker context). */
    private EngineChunk generateDetachedChunk(net.zaminmc.torch.block.ChunkPosition position) {
        return generateDetachedChunk(world, position);
    }

    private EngineChunk generateDetachedChunk(EngineWorld source,
                                              net.zaminmc.torch.block.ChunkPosition position) {
        EngineChunk chunk = new EngineChunk(position, source.airType());
        source.generator().generate(chunk);
        return chunk;
    }

    /** The owner-side install at the drain boundary: publish, then fire the callbacks. */
    private void installGeneratedChunk(net.zaminmc.torch.block.ChunkPosition position,
                                       EngineChunk chunk) {
        installGeneratedChunk(0, position, chunk);
    }

    private void installGeneratedChunk(int dimension,
                                       net.zaminmc.torch.block.ChunkPosition position,
                                       EngineChunk chunk) {
        long key = dimensionKey(dimension, position.packed());
        pendingChunkGenerations.remove(key);
        java.util.Queue<java.util.function.Consumer<EngineChunk>> callbacks =
                pendingChunkLoads.remove(key);
        EngineChunk installed = worldFor(dimension).installGenerated(position, chunk);
        if (callbacks != null) {
            for (java.util.function.Consumer<EngineChunk> callback : callbacks) {
                callback.accept(installed);
            }
        }
    }
}
