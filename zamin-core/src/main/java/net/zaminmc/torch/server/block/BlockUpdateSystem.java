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
 *       block state on landing).</li>
 *   <li><b>Torch support</b>: a floor torch over air pops as an item.</li>
 *   <li><b>Grass decay</b>: grass with an opaque block above dies to dirt.</li>
 * </ul>
 *
 * <p>Positions are scheduled on commit (the engine registers this system as a
 * world change listener before the adapter, so client syncs and neighbor
 * updates observe every committed change in the same order). A chain — a sand
 * column losing its base — collapses in one drain because every conversion
 * schedules its own neighbors; the historical engine schedules each level two
 * ticks apart, a visual nuance the falling entities render identically.</p>
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

    private final ArrayDeque<Scheduled> queue = new ArrayDeque<>();
    private final Set<Long> pending = new HashSet<>();

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
    }

    /** Applies the rule set to one position. Tick-thread context. */
    private void runUpdate(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (type.equals(world.airType())) {
            return;
        }
        Identifier identifier = type.identifier();
        if (identifier.equals(SAND) || identifier.equals(GRAVEL)) {
            if (world.getBlock(position.offset(0, -1, 0)).equals(world.airType())) {
                world.setBlock(position, world.airType());
                fallingEntities.startFall(position, type);
            }
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
        if (identifier.equals(GRASS)
                && isOpaque(world.getBlock(position.offset(0, 1, 0)))) {
            world.setBlock(position, BuiltinBlocks.DIRT);
        }
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
