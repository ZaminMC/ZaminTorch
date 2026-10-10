package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * The scheduled block-update queue (§466): world changes request neighbor
 * updates, updates run on the simulation context, and each scheduled position
 * carries exactly one pending update (later requests coalesce, the historical
 * one-update-per-position rule).
 *
 * <p>Update rules implemented this slice, all historical:</p>
 * <ul>
 *   <li><b>Gravity blocks</b> (§470): sand and gravel whose support vanished
 *       convert into a falling block entity (block state → entity state →
 *       block state on landing), on the vanilla two-tick fuse — {@code
 *       FallingBlock.onAdded/onNeighborChanged} schedule the tryFall check
 *       {@code getTickRate(world) = 2} ticks out, so a wake-up block hovers
 *       for two ticks and a collapsing column staggers one level per two
 *       ticks, exactly the historical rhythm.</li>
 *   <li><b>Torch support</b>: a floor torch over air pops as an item.</li>
 *   <li><b>Grass decay</b>: grass with an opaque block above dies to dirt.</li>
 * </ul>
 *
 * <p>Positions are scheduled on commit (the engine registers this system as a
 * world change listener before the adapter, so client syncs and neighbor
 * updates observe every committed change in the same order). A chain — a sand
 * column losing its base — collapses one level per two ticks: every
 * conversion commits air, the air commit schedules the next sand cell's wake,
 * and each wake fires its own check two ticks later (the vanilla cadence).</p>
 */
public final class BlockUpdateSystem implements WorldChangeListener {

    private static final Logger LOGGER = Logger.getLogger(BlockUpdateSystem.class.getName());

    /** Offsets updated around a changed block: itself and the six neighbors. */
    private static final int[][] NEIGHBOR_OFFSETS = {
            {0, 0, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static final Identifier SAND = Identifier.parse("minecraft:sand");
    private static final Identifier GRAVEL = Identifier.parse("minecraft:gravel");
    private static final Identifier TORCH = Identifier.parse("minecraft:torch");
    private static final Identifier GRASS = Identifier.parse("minecraft:grass_block");
    private static final Identifier FIRE = Identifier.parse("minecraft:fire");
    private static final Identifier NETHER_PORTAL = Identifier.parse("minecraft:nether_portal");
    private static final Identifier NETHER_PORTAL_Z = Identifier.parse("minecraft:nether_portal_z");
    private static final Identifier SUGAR_CANE = Identifier.parse("minecraft:sugar_cane");
    private static final Identifier CACTUS = Identifier.parse("minecraft:cactus");

    /**
     * The flammable set (the historical {@code Blocks.fire.getFlammability}
     * members the engine registers): fire consumes these over time and only
     * survives near them. Ported from the Glowstone/vanilla fire model as the
     * engine's own rule set — planks, logs, leaves and the wooden furniture.
     */
    private static final java.util.Set<Identifier> FLAMMABLE = java.util.Set.of(
            Identifier.parse("minecraft:oak_planks"),
            Identifier.parse("minecraft:oak_log"),
            Identifier.parse("minecraft:oak_leaves"),
            Identifier.parse("minecraft:crafting_table"),
            Identifier.parse("minecraft:chest"),
            Identifier.parse("minecraft:oak_fence"),
            Identifier.parse("minecraft:ladder"),
            Identifier.parse("minecraft:ladder_south"),
            Identifier.parse("minecraft:ladder_west"),
            Identifier.parse("minecraft:ladder_east"),
            Identifier.parse("minecraft:sign"),
            Identifier.parse("minecraft:sign_west"),
            Identifier.parse("minecraft:sign_north"),
            Identifier.parse("minecraft:sign_east"),
            Identifier.parse("minecraft:tall_grass"),
            Identifier.parse("minecraft:dead_bush"));

    /** Fire's own update latency: 15-35 ticks between self-checks. */
    static final int FIRE_UPDATE_MIN = 15;
    static final int FIRE_UPDATE_SPREAD = 21;

    /** The rain state (rain extinguishes exposed fire, the historical rule). */
    private volatile java.util.function.BooleanSupplier rainingSource = () -> false;
    /** The fire rolls (tick-thread confined like every engine random). */
    private java.util.Random random = new java.util.Random();

    private final EngineWorld world;
    private final ItemEntityManager itemEntities;
    private final FallingBlockEntityManager fallingEntities;

    /** One pending scheduled update. */
    private record Scheduled(BlockPosition position, long dueTick) {
    }

    /** One pending vanilla fall check ({@code scheduleTick(pos, FallingBlock, 2)}). */
    private record FallCheck(BlockPosition position, long dueTick) {
    }

    private final ArrayDeque<Scheduled> queue = new ArrayDeque<>();
    private final Set<Long> pending = new HashSet<>();

    /** The vanilla fuse: {@code FallingBlock.getTickRate} is 2 game ticks. */
    static final int FALL_CHECK_DELAY = 2;
    private final ArrayDeque<FallCheck> fallChecks = new ArrayDeque<>();
    private final Set<Long> pendingFallChecks = new HashSet<>();

    public BlockUpdateSystem(EngineWorld world, ItemEntityManager itemEntities,
                             FallingBlockEntityManager fallingEntities) {
        this.world = Objects.requireNonNull(world, "world");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.fallingEntities = Objects.requireNonNull(fallingEntities, "fallingEntities");
    }

    /** Wires the rain state (the engine's weather flag) and the roll source. */
    public void setFireEnvironment(java.util.function.BooleanSupplier raining,
                                   java.util.Random random) {
        this.rainingSource = Objects.requireNonNull(raining, "raining");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * A block was committed: request updates for it and its neighbors (§466
     * "neighbor/update request"). Runs on the simulation thread through the
     * world-listener path.
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        Objects.requireNonNull(newType, "newType");
        long now = changedWorld.totalTicks();
        for (int[] offset : NEIGHBOR_OFFSETS) {
            schedule(position.offset(offset[0], offset[1], offset[2]), now);
        }
    }

    /** Schedules one update (one pending update per position; earliest wins). */
    private void schedule(BlockPosition position, long dueTick) {
        if (position.y() < BlockPosition.MIN_Y || position.y() > BlockPosition.MAX_Y) {
            return; // off-world neighbors have no rules to run
        }
        long key = key(position);
        if (!pending.add(key)) {
            return;
        }
        queue.add(new Scheduled(position, dueTick));
    }

    /**
     * Runs every update due this tick. Tick-thread context; called from the
     * engine's tick handler after the work queue drained (a commit schedules
     * and the same tick executes it — reaction latency stays at one tick).
     */
    public void tick() {
        long now = world.totalTicks();
        // Bounded drain (the same rule as the fluid system): a future-due
        // entry re-arms to the back and is re-checked on a later tick - an
        // unbounded drain would poll the same future-due entry forever.
        int entries = queue.size();
        for (int i = 0; i < entries; i++) {
            Scheduled entry = queue.poll();
            if (entry == null) {
                break; // drained everything reachable (defensive)
            }
            pending.remove(key(entry.position()));
            if (entry.dueTick() > now) {
                schedule(entry.position(), entry.dueTick()); // re-arm, due tick preserved
                continue;
            }
            runUpdate(entry.position());
        }
        // The vanilla fall checks due this tick (the two-tick fuse).
        int checks = fallChecks.size();
        for (int i = 0; i < checks; i++) {
            FallCheck entry = fallChecks.poll();
            if (entry == null) {
                break; // drained everything reachable (defensive)
            }
            pendingFallChecks.remove(key(entry.position()));
            if (entry.dueTick() > now) {
                if (pendingFallChecks.add(key(entry.position()))) {
                    fallChecks.add(entry); // re-arm, due tick preserved
                }
                continue;
            }
            runFallCheck(entry.position());
        }
    }

    /**
     * Schedules one vanilla fall check (one pending check per position;
     * earliest wins — a second wake while the fuse runs changes nothing,
     * matching the coalesced historical behavior for this rule).
     */
    private void scheduleFallCheck(BlockPosition position, long dueTick) {
        if (position.y() < BlockPosition.MIN_Y || position.y() > BlockPosition.MAX_Y) {
            return; // off-world cells hold no gravity blocks
        }
        long key = key(position);
        if (!pendingFallChecks.add(key)) {
            return;
        }
        fallChecks.add(new FallCheck(position, dueTick));
    }

    /**
     * The vanilla {@code FallingBlock.tryFall} (reference/1.8.8 FallingBlock
     * lines 40-59): the block converts when the cell below can be fallen
     * through (air, water, lava, fire — sand stacked over a pond collapses
     * into it) and the block itself still occupies the cell. Tick-thread
     * context.
     */
    private void runFallCheck(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (type.equals(world.airType())) {
            return;
        }
        Identifier identifier = type.identifier();
        if (!identifier.equals(SAND) && !identifier.equals(GRAVEL)) {
            return;
        }
        if (net.zaminmc.torch.server.entity.FallingBlockEntity.canFallThrough(
                net.zaminmc.torch.server.entity.FallingBlockEntity.blockOrAir(
                        world, position.offset(0, -1, 0)))) {
            world.setBlock(position, world.airType());
            fallingEntities.startFall(position, type);
        }
    }

    /** Applies the rule set to one position. Tick-thread context. */
    private void runUpdate(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (type.equals(world.airType())) {
            return;
        }
        Identifier identifier = type.identifier();
        if (identifier.equals(SAND) || identifier.equals(GRAVEL)) {
            // The vanilla wake (FallingBlock.onAdded/onNeighborChanged): the
            // tryFall check schedules two ticks out — the block hovers for
            // two ticks after its support vanishes before it converts.
            scheduleFallCheck(position, world.totalTicks() + FALL_CHECK_DELAY);
            return;
        }
        if (identifier.equals(TORCH)) {
            if (!world.getBlock(position.offset(0, -1, 0)).equals(world.airType())) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.TORCH, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        if (identifier.equals(FIRE)) {
            tickFire(position, world.totalTicks());
            return;
        }
        if (identifier.equals(NETHER_PORTAL) || identifier.equals(NETHER_PORTAL_Z)) {
            // The reference's PortalBlock.neighborChanged (block/PortalBlock
            // lines 101-117): the cell re-validates its own frame — an
            // invalid scan, or a scan finding fewer portal cells than the
            // frame's interior (a frame block broke), kills the cell to air
            // (the reference's setBlockState(pos, AIR)); the neighbor fan-out
            // then wakes the remaining cells.
            if (!net.zaminmc.torch.server.world.PortalFrameBuilder
                    .survivesNeighborChange(world, position)) {
                world.setBlock(position, world.airType());
            }
            return;
        }
        if (identifier.equals(SUGAR_CANE)) {
            tickCaneSupport(position);
            return;
        }
        if (identifier.equals(CACTUS)) {
            tickCactusSupport(position);
            return;
        }
        if (identifier.equals(GRASS)
                && isOpaque(world.getBlock(position.offset(0, 1, 0)))) {
            world.setBlock(position, BuiltinBlocks.DIRT);
        }
    }

    /**
     * The reed's support rule (the historical BlockReed.canPlace): a cane
     * stands on grass, dirt, sand or another cane; anything else (or nothing)
     * pops it as an item — the chain runs down the column one update at a
     * time, so breaking the base fells the whole reed.
     */
    private void tickCaneSupport(BlockPosition at) {
        BlockType below = world.getBlock(at.offset(0, -1, 0));
        Identifier belowId = below.identifier();
        boolean supported = belowId.equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                || belowId.equals(BuiltinBlocks.DIRT.identifier())
                || belowId.equals(BuiltinBlocks.SAND.identifier())
                || belowId.equals(SUGAR_CANE);
        if (supported) {
            return;
        }
        world.setBlock(at, world.airType());
        itemEntities.spawnDropAtBlock(
                new Position(at.x(), at.y(), at.z()),
                ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SUGAR_CANE, 1),
                ItemEntity.PICKUP_DELAY_DROP_TICKS);
    }

    /**
     * The cactus's support rule (the historical BlockCactus
     * onNeighborChanged): a cactus needs sand or cactus below and no solid
     * block beside it; a violation breaks it with its drop.
     */
    private void tickCactusSupport(BlockPosition at) {
        BlockType below = world.getBlock(at.offset(0, -1, 0));
        boolean supported = below.identifier().equals(BuiltinBlocks.SAND.identifier())
                || below.identifier().equals(CACTUS);
        boolean solidBeside = false;
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (WorldSolidity.isSolid(world.getBlock(at.offset(dir[0], 0, dir[1])))) {
                solidBeside = true;
                break;
            }
        }
        if (supported && !solidBeside) {
            return;
        }
        world.setBlock(at, world.airType());
        itemEntities.spawnDropAtBlock(
                new Position(at.x(), at.y(), at.z()),
                ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.CACTUS, 1),
                ItemEntity.PICKUP_DELAY_DROP_TICKS);
    }

    /**
     * The fire block's scheduled life (the historical BlockFire.updateTick
     * shape, adapted): rain kills exposed fire; fire without fuel or support
     * burns out; otherwise it consumes a flammable support below or beside it
     * (the burning-away) and re-arms its own update 15-35 ticks out. Every
     * conversion schedules the world's neighbor updates through the ordinary
     * commit path, so chains run on the same clock everything else uses.
     * Tick-thread context.
     */
    private void tickFire(BlockPosition at, long now) {
        BlockType below = world.getBlock(at.offset(0, -1, 0));
        boolean belowFlammable = isFlammable(below.identifier());
        boolean belowSupports = !below.equals(world.airType())
                && !isFlammable(below.identifier())
                && !FluidBlocks.isFluid(below.identifier())
                && !WorldSolidity.isFire(below.identifier());
        boolean neighborFuel = false;
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockType side = world.getBlock(at.offset(dir[0], 0, dir[1]));
            if (isFlammable(side.identifier())) {
                neighborFuel = true;
                break;
            }
        }
        boolean exposed = !isOpaque(world.getBlock(at.offset(0, 1, 0)));

        // Rain douses exposed fire; a floating flame with neither support nor
        // fuel burns itself out.
        if ((rainingSource.getAsBoolean() && exposed)
                || (!belowSupports && !belowFlammable && !neighborFuel)) {
            world.setBlock(at, world.airType());
            return;
        }

        // The burn-away: fuel below first (a fire on a plank floor eats
        // through it), otherwise a sideways flammable neighbor catches.
        if (belowFlammable && random.nextInt(3) == 0) {
            world.setBlock(at.offset(0, -1, 0), BuiltinBlocks.FIRE);
        } else if (neighborFuel && random.nextInt(4) == 0) {
            for (int attempt = 0; attempt < 4; attempt++) {
                int[] dir = new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}[
                        random.nextInt(4)];
                BlockPosition side = at.offset(dir[0], 0, dir[1]);
                if (isFlammable(world.getBlock(side).identifier())) {
                    world.setBlock(side, BuiltinBlocks.FIRE);
                    break;
                }
            }
        }

        // The flame lives on to try again (the scheduled re-arm).
        schedule(at, now + FIRE_UPDATE_MIN + random.nextInt(FIRE_UPDATE_SPREAD));
    }

    /** @return whether the identifier is one of the engine's flammable blocks. */
    public static boolean isFlammable(Identifier id) {
        return FLAMMABLE.contains(id);
    }

    /** The historical opaque set this slice models (glass and torch are not). */
    public static boolean isOpaque(BlockType type) {
        String name = type.identifier().toString();
        return !name.equals("minecraft:air")
                && !name.equals("minecraft:torch")
                && !name.equals("minecraft:glass");
    }

    /** Packs a position into one dedup key (the 1.8 coordinate space). */
    private static long key(BlockPosition position) {
        return ((long) (position.x() & 0x3FFFFFF) << 38)
                | ((long) (position.z() & 0x3FFFFFF) << 12)
                | (position.y() & 0xFFF);
    }
}
