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
        if (identifier.equals(GRASS)
                && isOpaque(world.getBlock(position.offset(0, 1, 0)))) {
            world.setBlock(position, BuiltinBlocks.DIRT);
        }
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
