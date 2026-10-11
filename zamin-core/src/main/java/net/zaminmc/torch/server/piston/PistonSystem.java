package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockBehaviorTable;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The piston subsystem (Slice 9e) — the reference port of the 1.8.8 piston:
 * block/PistonBaseBlock.java (the power walks, the block-event state
 * machine, the move write sequence), block/PistonHeadBlock.java (the head's
 * break/pop rules), block/MovingBlock.java + block/entity/
 * MovingBlockEntity.java (the two-tick in-flight carrier) and the
 * server/world/ServerWorld.java block-event queue (the two-buffer swap —
 * lines 96, 207, 808-840).
 *
 * <h2>The state machine (PistonBaseBlock lines 70-171)</h2>
 *
 * <ul>
 *   <li><b>checkExtended</b> (neighborChanged/onPlaced): shouldExtend arms
 *       event 0 (extend) when a retracted piston finds power; event 1
 *       (retract) when an extended one loses it — the EXTENDED pair flips
 *       off BEFORE the retract event.</li>
 *   <li><b>shouldExtend</b> (lines 83-103): the piston's own power walks —
 *       the six neighbors except the FACING side (the front face is
 *       immune), the piston's own position (inert — the reference's
 *       isCube-false makes the piston neither re-radiate nor emit), and
 *       the quasi-connectivity walk around the position ABOVE the
 *       piston.</li>
 *   <li><b>doEvent 0 (extend)</b>: the resolver must hold, the move writes
 *       the moving blocks + the head, the EXTENDED pair flips on, the
 *       "tile.piston.out" sound.</li>
 *   <li><b>doEvent 1 (retract)</b>: the piston itself becomes a moving
 *       block (the retracting body), a sticky piston pulls the block chain
 *       beyond the head, a plain piston removes its head, the
 *       "tile.piston.in" sound.</li>
 * </ul>
 *
 * <h2>The moving blocks (MovingBlockEntity lines 88-115)</h2>
 *
 * One record per in-flight position: the carried state, the facing, the
 * extending flag, the progress. The progress advances 0.5 per tick; at 1.0
 * the carried state lands (the reference's flag-3 write + the
 * neighborChanged ring, both carried by the engine's commit dispatch).
 *
 * <h2>Engine adaptations, all ledgered</h2>
 *
 * <ul>
 *   <li>The block events run after the redstone queue within the same
 *       tick (the reference's doScheduledTicks -> ... -> doBlockEvents
 *       order); the moving blocks advance before the event drain (the
 *       reference's entity-phase BE tick).</li>
 *   <li>The entity-displacement arm of the reference's moveEntities (the
 *       push of bodies standing in the flight path) rides the
 *       entity-collision slice — the engine's players are
 *       client-authoritative.</li>
 *   <li>The client animation (the Block Action 0x24 fan-out with the
 *       moving BE) rides the protocol slice; the server-side end states
 *       are exact.</li>
 *   <li>The world border arm of canMoveBlock is absent (the engine has no
 *       border enforcement yet).</li>
 * </ul>
 */
public final class PistonSystem implements WorldChangeListener {

    /** The six directions in the reference's Direction id order. */
    static final int[][] OFFSETS = {
            {0, -1, 0}, // 0 DOWN
            {0, 1, 0},  // 1 UP
            {0, 0, -1}, // 2 NORTH
            {0, 0, 1},  // 3 SOUTH
            {-1, 0, 0}, // 4 WEST
            {1, 0, 0},  // 5 EAST
    };

    /** The material piston behaviors (the reference's Material.pistonMoveBehavior). */
    static final int BEHAVIOR_PUSH = 0;
    static final int BEHAVIOR_DESTROY = 1;
    static final int BEHAVIOR_BLOCK = 2;

    /** One pending block event (the reference's ServerWorld.BlockEvent). */
    private record BlockEvent(BlockPosition position, int type, int data) {
    }

    /** One in-flight moving block (the MovingBlockEntity port). */
    private record MovingBlock(BlockType movedType, int facing, boolean extending, float progress) {
    }

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final ItemEntityManager itemEntities;
    private final FxManager fx;
    private final Random random;

    /** The two-queue block-event swap (ServerWorld lines 96 + 808-840). */
    private final List<BlockEvent>[] blockEvents = new ArrayList[]{new ArrayList<>(), new ArrayList<>()};
    private int nextBlockEventQueueIndex;

    /** The in-flight moving blocks keyed by their world position. */
    private final Map<BlockPosition, MovingBlock> movingBlocks = new HashMap<>();

    public PistonSystem(EngineWorld world, RedstoneSystem redstone, ItemEntityManager itemEntities,
                        FxManager fx, Random random) {
        this.world = world;
        this.redstone = redstone;
        this.itemEntities = itemEntities;
        this.fx = fx;
        this.random = random;
    }

    EngineWorld world() {
        return world;
    }

    // ------------------------------------------------------------------
    // The change listener
    // ------------------------------------------------------------------

    /**
     * The family dispatch (the reference's flag-1 walk over the family's
     * neighborChanged + onAdded arms): a piston base re-checks its power,
     * and every neighbor of the committed change re-checks too — a head
     * without its base pops, a piston hearing a change re-arms.
     */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (PistonBlocks.isPiston(newType)) {
            // PistonBaseBlock.onAdded lines 59-63 (the engine folds the
            // onPlaced re-check into the arrival — the engine has no BE
            // at a piston base, so the gate always holds).
            checkExtended(position, newType);
        }
        for (int[] offset : OFFSETS) {
            neighborChanged(position.offset(offset[0], offset[1], offset[2]));
        }
    }

    /**
     * The removal arm: the head's departure drops and removes the extended
     * base behind it (PistonHeadBlock.onRemoved lines 50-59), and an
     * in-flight moving block replaced by something else drops its record
     * (the MovingBlock.onRemoved BE finish is the landing path — here the
     * new block won the position).
     */
    @Override
    public void onBlockRemoved(EngineWorld changedWorld, BlockPosition position, BlockType oldType) {
        if (PistonBlocks.isPistonHead(oldType)) {
            int[] away = OFFSETS[PistonBlocks.headFacing(oldType)];
            BlockPosition base = position.offset(-away[0], -away[1], -away[2]);
            BlockType baseType = world.getBlock(base);
            if (PistonBlocks.isPiston(baseType) && PistonBlocks.pistonExtended(baseType)) {
                dropBlock(base, baseType);
                world.setBlock(base, world.airType());
            }
            return;
        }
        if (PistonBlocks.isMovingPiston(oldType)
                && !PistonBlocks.isMovingPiston(world.getBlock(position))) {
            movingBlocks.remove(position);
        }
    }

    /** The per-family neighborChanged dispatch. */
    private void neighborChanged(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (PistonBlocks.isPiston(type)) {
            checkExtended(pos, type);
            return;
        }
        if (PistonBlocks.isPistonHead(type)) {
            // PistonHeadBlock.neighborChanged lines 154-163: no base
            // behind -> pop; a base behind -> forward the change to it.
            int facing = PistonBlocks.headFacing(type);
            int[] away = OFFSETS[facing];
            BlockPosition base = pos.offset(-away[0], -away[1], -away[2]);
            BlockType baseType = world.getBlock(base);
            if (!PistonBlocks.isPiston(baseType)) {
                world.setBlock(pos, world.airType());
            } else {
                checkExtended(base, baseType);
            }
        }
    }

    // ------------------------------------------------------------------
    // The power walks (PistonBaseBlock.shouldExtend lines 83-103)
    // ------------------------------------------------------------------

    /**
     * The piston's power model: the six neighbors except the FACING side
     * (the front face is immune), the piston's own position (inert for the
     * engine's signal model — the reference's piston is neither
     * re-radiating nor emitting), and the quasi-connectivity walk around
     * the position above the piston (any signal around it extends the
     * piston even without adjacent power).
     */
    private boolean shouldExtend(BlockPosition pos, int facing) {
        for (int direction = 0; direction < 6; direction++) {
            if (direction != facing) {
                int[] o = OFFSETS[direction];
                if (redstone.signal(pos.offset(o[0], o[1], o[2]), direction) > 0) {
                    return true;
                }
            }
        }
        // The piston's own position: the reference reads hasSignal(pos,
        // DOWN) — the piston is not signal-solid (isCube false), so the
        // read answers its own emission: always 0. Kept 1:1.
        if (redstone.signal(pos, 0 /* DOWN */) > 0) {
            return true;
        }
        BlockPosition above = pos.offset(0, 1, 0);
        for (int direction = 1 /* skip DOWN = the piston itself */; direction < 6; direction++) {
            int[] o = OFFSETS[direction];
            if (redstone.signal(above.offset(o[0], o[1], o[2]), direction) > 0) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The extend/retract state machine (checkExtended + doEvent)
    // ------------------------------------------------------------------

    /** The arming (PistonBaseBlock.checkExtended lines 70-81). */
    private void checkExtended(BlockPosition pos, BlockType type) {
        int facing = PistonBlocks.pistonFacing(type);
        boolean powered = shouldExtend(pos, facing);
        if (powered && !PistonBlocks.pistonExtended(type)) {
            if (new PistonStructureResolver(this, pos, facing, true).resolve()) {
                addBlockEvent(pos, 0, facing);
            }
        } else if (!powered && PistonBlocks.pistonExtended(type)) {
            world.setBlock(pos, PistonBlocks.pistonOfState(type, false));
            addBlockEvent(pos, 1, facing);
        }
    }

    /**
     * The event queue add (ServerWorld.addBlockEvent lines 808-818): the
     * duplicate check scans the CURRENT queue — one (position, type, data)
     * pending at a time.
     */
    private void addBlockEvent(BlockPosition pos, int type, int data) {
        for (BlockEvent existing : blockEvents[nextBlockEventQueueIndex]) {
            if (existing.position().equals(pos) && existing.type() == type && existing.data() == data) {
                return;
            }
        }
        blockEvents[nextBlockEventQueueIndex].add(new BlockEvent(pos, type, data));
    }

    /**
     * The event execution (PistonBaseBlock.doEvent lines 106-171) — the
     * server-side re-check first (the world may have changed since the
     * arming), then the extend or the retract.
     */
    private boolean doBlockEvent(BlockEvent event) {
        BlockType type = world.getBlock(event.position());
        if (!PistonBlocks.isPiston(type)) {
            return false; // the piston departed: the stale event drops
        }
        int facing = event.data();
        boolean powered = shouldExtend(event.position(), facing);
        if (powered && event.type() == 1) {
            // The re-armed retract cancels: the piston flips back to
            // extended without moving anything (lines 110-113).
            world.setBlock(event.position(), PistonBlocks.pistonOfState(type, true));
            return false;
        }
        if (!powered && event.type() == 0) {
            return false; // the extend cancels (lines 115-117)
        }

        if (event.type() == 0) {
            if (!move(event.position(), facing, true)) {
                return false;
            }
            world.setBlock(event.position(), PistonBlocks.pistonOfState(type, true));
            pistonSound(event.position(), "tile.piston.out", 0.25f);
        } else if (event.type() == 1) {
            BlockPosition ahead = event.position().offset(OFFSETS[facing][0],
                    OFFSETS[facing][1], OFFSETS[facing][2]);
            MovingBlock inFlight = movingBlocks.get(ahead);
            if (inFlight != null) {
                // doEvent lines 128-131: finish a mid-flight block at the
                // head position before the body follows.
                finish(ahead, inFlight);
            }
            boolean sticky = PistonBlocks.pistonSticky(type);
            world.setBlock(event.position(), PistonBlocks.movingOf(facing, sticky));
            movingBlocks.put(event.position(), new MovingBlock(
                    PistonBlocks.pistonOf(facing, false, sticky), facing, false, 0.0f));
            if (sticky) {
                BlockPosition beyond = event.position().offset(OFFSETS[facing][0] * 2,
                        OFFSETS[facing][1] * 2, OFFSETS[facing][2] * 2);
                BlockType beyondType = world.getBlock(beyond);
                boolean finished = false;
                if (PistonBlocks.isMovingPiston(beyondType)) {
                    MovingBlock moving = movingBlocks.get(beyond);
                    if (moving != null && moving.facing() == facing && moving.extending()) {
                        finish(beyond, moving);
                        finished = true;
                    }
                }
                if (!finished && !isAir(beyondType)
                        && canMoveBlock(beyondType, beyond, opposite(facing), false)
                        && (pistonMoveBehavior(beyondType) == BEHAVIOR_PUSH
                            || PistonBlocks.isPiston(beyondType))) {
                    move(event.position(), facing, false);
                }
            } else {
                BlockPosition head = event.position().offset(OFFSETS[facing][0],
                        OFFSETS[facing][1], OFFSETS[facing][2]);
                world.setBlock(head, world.airType());
            }
            pistonSound(event.position(), "tile.piston.in", 0.15f);
        }
        return true;
    }

    /**
     * The move (PistonBaseBlock.move lines 287-349): the resolver, the
     * break-list drops, the moving-block writes (the shifted chain + the
     * growing head), then the neighbor rings in the reference's order.
     */
    private boolean move(BlockPosition pos, int facing, boolean extend) {
        if (!extend) {
            BlockPosition head = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1], OFFSETS[facing][2]);
            world.setBlock(head, world.airType()); // the head departs first
        }

        PistonStructureResolver resolver = new PistonStructureResolver(this, pos, facing, extend);
        List<BlockPosition> toMove = resolver.getToMove();
        List<BlockPosition> toBreak = resolver.getToBreak();
        if (!resolver.resolve()) {
            return false;
        }
        boolean sticky = PistonBlocks.pistonSticky(world.getBlock(pos));

        for (int i = toBreak.size() - 1; i >= 0; i--) {
            BlockPosition at = toBreak.get(i);
            BlockType broken = world.getBlock(at);
            dropBlock(at, broken);
            world.setBlock(at, world.airType());
        }

        int direction = extend ? facing : opposite(facing);
        for (int i = toMove.size() - 1; i >= 0; i--) {
            BlockPosition at = toMove.get(i);
            BlockType movedType = world.getBlock(at);
            world.setBlock(at, world.airType());
            BlockPosition target = at.offset(OFFSETS[direction][0], OFFSETS[direction][1],
                    OFFSETS[direction][2]);
            world.setBlock(target, PistonBlocks.movingOf(facing, false));
            movingBlocks.put(target, new MovingBlock(movedType, facing, extend, 0.0f));
        }

        if (extend) {
            BlockPosition head = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1], OFFSETS[facing][2]);
            world.setBlock(head, PistonBlocks.movingOf(facing, sticky));
            movingBlocks.put(head, new MovingBlock(
                    PistonBlocks.headOf(facing, sticky), facing, true, 0.0f));
        }

        for (int i = toBreak.size() - 1; i >= 0; i--) {
            updateNeighbors(toBreak.get(i));
        }
        for (int i = toMove.size() - 1; i >= 0; i--) {
            updateNeighbors(toMove.get(i));
        }
        if (extend) {
            updateNeighbors(pos.offset(OFFSETS[facing][0], OFFSETS[facing][1], OFFSETS[facing][2]));
            updateNeighbors(pos);
        }
        return true;
    }

    /** The reference's world.updateNeighbors(pos, block): the six neighbors hear it. */
    private void updateNeighbors(BlockPosition pos) {
        for (int[] offset : OFFSETS) {
            neighborChanged(pos.offset(offset[0], offset[1], offset[2]));
        }
    }

    // ------------------------------------------------------------------
    // The moving blocks (MovingBlockEntity.tick lines 96-115)
    // ------------------------------------------------------------------

    /**
     * The per-tick advance: the moving blocks progress 0.5 each, landing at
     * 1.0 (two ticks of flight); then the block-event queues drain (the
     * reference's entity-phase BE tick before the doBlockEvents walk).
     */
    public void tick() {
        if (!movingBlocks.isEmpty()) {
            for (Map.Entry<BlockPosition, MovingBlock> entry : List.copyOf(
                    new ArrayList<>(movingBlocks.entrySet()))) {
                MovingBlock block = entry.getValue();
                if (block.progress() >= 1.0f) {
                    // already landed through finish(): the record lingers only
                    // if the landing write was displaced
                    movingBlocks.remove(entry.getKey());
                    continue;
                }
                float progress = Math.min(block.progress() + 0.5f, 1.0f);
                if (progress >= 1.0f) {
                    finish(entry.getKey(), new MovingBlock(block.movedType(), block.facing(),
                            block.extending(), progress));
                } else {
                    movingBlocks.put(entry.getKey(), new MovingBlock(block.movedType(),
                            block.facing(), block.extending(), progress));
                }
            }
        }
        while (!blockEvents[nextBlockEventQueueIndex].isEmpty()) {
            int queue = nextBlockEventQueueIndex;
            nextBlockEventQueueIndex ^= 1;
            for (BlockEvent event : blockEvents[queue]) {
                doBlockEvent(event);
            }
            blockEvents[queue].clear();
        }
    }

    /**
     * The landing (MovingBlockEntity.finish lines 87-96 + the tick end):
     * the carried state commits, the record departs.
     */
    private void finish(BlockPosition pos, MovingBlock block) {
        movingBlocks.remove(pos);
        if (PistonBlocks.isMovingPiston(world.getBlock(pos))) {
            world.setBlock(pos, block.movedType());
        }
    }

    // ------------------------------------------------------------------
    // The move rules (PistonBaseBlock.canMoveBlock lines 247-285)
    // ------------------------------------------------------------------

    /**
     * The pushability test: obsidian never moves; the world's vertical
     * bounds clamp; an extended piston base never moves; unbreakable
     * blocks (the -1 hardness arm) and BLOCK-material blocks never move;
     * DESTROY blocks move only when breaking is allowed (the resolver's
     * start gate vs. the forward walk); block-entity-backed containers
     * (the chest, the furnace, the sign) never move.
     */
    boolean canMoveBlock(BlockType type, BlockPosition pos, int dir, boolean allowBreaking) {
        String value = type.identifier().value();
        if (value.equals("obsidian")) {
            return false;
        }
        int y = pos.y();
        if (y < 0 || (dir == 0 && y == 0)) {
            return false;
        }
        if (y > 255 || (dir == 1 && y == 255)) {
            return false;
        }
        if (PistonBlocks.isPiston(type)) {
            return !PistonBlocks.pistonExtended(type);
        }
        var behavior = BlockBehaviorTable.of(type.identifier());
        if (behavior.isPresent() && behavior.get().hardness() < 0.0) {
            return false; // the unbreakables (bedrock, the moving block)
        }
        int moveBehavior = pistonMoveBehavior(type);
        if (moveBehavior == BEHAVIOR_BLOCK) {
            return false;
        }
        if (moveBehavior == BEHAVIOR_DESTROY) {
            return allowBreaking;
        }
        return !isBlockEntityBacked(type);
    }

    /**
     * The material's piston behavior over the engine's blocks (the
     * reference's Material.setDestroyOnPistonMove / setBlocksPistonMove
     * tables): the redstone decoration family and the plants break, the
     * portal family and the piston head block.
     */
    int pistonMoveBehavior(BlockType type) {
        String value = type.identifier().value();
        // The BLOCK family: the portal, the piston head, the moving block
        // (the carrier is also hardness -1 in the behavior table — the
        // reference's own double gate), the barrier.
        if (value.startsWith("nether_portal") || PistonBlocks.isPistonHead(type)
                || PistonBlocks.isMovingPiston(type)) {
            return BEHAVIOR_BLOCK;
        }
        // The DESTROY family: Material.DECORATION (every redstone
        // component incl. the comparator), the plants, the leaves, the
        // liquids, the fragile gourds.
        if (value.equals("torch")
                || value.startsWith("redstone_wire")
                || value.startsWith("redstone_torch")
                || value.startsWith("unlit_redstone_torch")
                || value.startsWith("repeater_")
                || value.startsWith("powered_repeater_")
                || value.startsWith("comparator_")
                || value.startsWith("powered_comparator_")
                || value.startsWith("lever_")
                || value.startsWith("stone_button_")
                || value.startsWith("wooden_button_")
                || value.startsWith("stone_pressure_plate")
                || value.startsWith("wooden_pressure_plate")
                || value.startsWith("sapling")
                || value.startsWith("tall_grass")
                || value.startsWith("grass")   // the grass tuft variants
                || value.startsWith("fern")
                || value.startsWith("flower")
                || value.startsWith("dandelion")
                || value.startsWith("poppy")
                || value.startsWith("blue_orchid")
                || value.startsWith("allium")
                || value.startsWith("houstonia")
                || value.startsWith("red_tulip")
                || value.startsWith("orange_tulip")
                || value.startsWith("white_tulip")
                || value.startsWith("pink_tulip")
                || value.startsWith("oxeye")
                || value.equals("wheat")
                || value.equals("sugar_cane")
                || value.equals("cactus")
                || value.equals("pumpkin")
                || value.equals("snow_layer")
                || value.equals("fire")
                || value.startsWith("water")
                || value.startsWith("lava")
                || value.startsWith("oak_leaves")
                || value.startsWith("leaves")
                || value.equals("cake")
                || value.equals("egg")) {
            return BEHAVIOR_DESTROY;
        }
        return BEHAVIOR_PUSH;
    }

    /**
     * The engine's block-entity-backed blocks (the reference's
     * {@code !(block instanceof BlockEntityProvider)} arm): the containers
     * whose state lives beside the world blocks.
     */
    private static boolean isBlockEntityBacked(BlockType type) {
        String value = type.identifier().value();
        return value.equals(BuiltinBlocks.CHEST.identifier().value())
                || value.equals(BuiltinBlocks.FURNACE.identifier().value())
                || value.equals(BuiltinBlocks.FURNACE_LIT.identifier().value())
                || value.equals("sign")
                || value.startsWith("sign_");
    }

    /** Whether the type is the engine's air. */
    boolean isAir(BlockType type) {
        return type.equals(world.airType());
    }

    /** Whether the type is a slime block (none exist yet — the arm is dormant). */
    boolean isSlimeBlock(BlockType type) {
        return type.identifier().namespace().equals("minecraft")
                && type.identifier().value().equals("slime");
    }

    // ------------------------------------------------------------------
    // Drops + sound
    // ------------------------------------------------------------------

    /** The break drop (the reference's block.dropItems): the behavior table's rolls. */
    private void dropBlock(BlockPosition pos, BlockType type) {
        var behavior = BlockBehaviorTable.of(type.identifier());
        if (behavior.isEmpty()) {
            return;
        }
        for (var drop : behavior.get().drops()) {
            if (random.nextDouble() >= drop.chance()) {
                continue; // the roll failed: the entry yields nothing
            }
            var item = net.zaminmc.torch.server.item.BuiltinItems.lookup(drop.item());
            if (item.isEmpty()) {
                continue;
            }
            itemEntities.spawnDropAtBlock(
                    new Position(pos.x(), pos.y(), pos.z()),
                    ItemStack.of(item.get(), drop.count()),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            break; // the first successful roll wins
        }
    }

    /** The piston's sounds (the reference's random pitch bands). */
    private void pistonSound(BlockPosition pos, String name, float spread) {
        fx.sound(new Position(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5),
                name, 0.5f, random.nextFloat() * spread + 0.6f);
    }

    /** The opposite direction id. */
    static int opposite(int direction) {
        return PistonStructureResolver.oppositeOf(direction);
    }

    /** The piston family's registry hook (the boot's block table). */
    public static BlockRegistryBuilder registerBlocks(BlockRegistryBuilder builder) {
        return PistonBlocks.registerAll(builder);
    }
}
