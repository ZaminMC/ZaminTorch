package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.Direction;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.hopper.HopperSystem;
import net.zaminmc.torch.server.redstone.RedstoneSystem;
import net.zaminmc.torch.server.tnt.TntSystem;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Position;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * The dispenser and dropper system (Slice 11a — the reference port of
 * block/DispenserBlock.java + block/DropperBlock.java +
 * block/dispenser/* + Bootstrap.registerDispenseBehaviors): the powered
 * activation (the TRIGGERED pair + the 4-tick reaction), the reservoir-sampled
 * slot pick, the dropper's container push, and the per-item behavior
 * registry the dispenser walks.
 *
 * <h2>The activation (DispenserBlock.neighborChanged lines 118-128)</h2>
 * <p>A neighbor signal — read at the block itself OR the cell above (the
 * reference's own {@code hasNeighborSignal(pos) || hasNeighborSignal(pos.up())}
 * arm, the through-the-top power quirk) — raises the TRIGGERED pair and
 * schedules the 4-tick reaction ({@code getTickRate} line 44). The signal
 * dropping clears the pair; a short pulse still dispenses (the scheduled
 * tick was already armed — the vanilla pulse-stretcher).</p>
 *
 * <h2>The dispense (DispenserBlock.dispense lines 96-112)</h2>
 * <p>The reservoir pick, then the behavior, then the slot takes the
 * remainder. An empty container answers with the empty click (world event
 * 1001 — {@code random.click} at pitch 1.2). The dropper overrides the
 * walk (DropperBlock.dispense lines 41-63): a container at the facing cell
 * takes ONE item through its receiving face — silently, the vanilla dropper
 * push — and only a container-less cell falls through to the item drop.</p>
 *
 * <h2>The behaviors (Bootstrap.registerDispenseBehaviors lines 63-420)</h2>
 * <p>The projectile family (arrow/egg/snowball — force 1.1, variation 6,
 * the launch sound {@code random.bow}), the fire charge (the small fireball
 * with the gaussian-tilted direction and the
 * {@code mob.ghast.fireball} shot), the TNT (a primed body at the target's
 * center), the bucket family (the pour and the scoop, with the empty-bucket
 * swap and the insert-else-drop arm), the flint and steel (the fire block,
 * or the TNT prime, with the one-use wear), the bone meal (the crop grow
 * {@code stage + nextInt(2..5)}, capped), and the default item drop (the
 * 0.7-out dispense position, the 0.125/0.15625 drop, the 0.2-and-gaussian
 * velocity family — DispenseItemBehavior.dispenseItem lines 29-45).</p>
 *
 * <p>Every dispense runs under the boundary scope covering the dispenser
 * and its facing cell (the Phase 4 spatial activation — a dispenser placing
 * fire across a region seam is vanilla-exact).</p>
 */
public final class DispenserSystem implements WorldChangeListener {

    private static final Logger LOGGER = Logger.getLogger(DispenserSystem.class.getName());

    /** The six directions in the reference's Direction id order. */
    private static final int[][] OFFSETS = {
            {0, -1, 0}, // 0 DOWN
            {0, 1, 0},  // 1 UP
            {0, 0, -1}, // 2 NORTH
            {0, 0, 1},  // 3 SOUTH
            {-1, 0, 0}, // 4 WEST
            {1, 0, 0},  // 5 EAST
    };

    /** The reaction latency (DispenserBlock.getTickRate line 44). */
    public static final int TICK_RATE = 4;

    /** The item-drop velocity scale (DispenseItemBehavior.spawnItem). */
    private static final double ITEM_SPREAD = 0.0075 * 6.0;

    private final EngineWorld world;
    private final RedstoneSystem redstone;
    private final ItemEntityManager itemEntities;
    private final ProjectileManager projectiles;
    private final TntSystem tnt;
    private final HopperSystem hoppers;
    private final FxManager fx;
    private final Random random;
    private final Map<BlockPosition, DispenserBlockEntity> containers = new LinkedHashMap<>();
    /** The public registration surface (built on first use; §27.3). */
    private DispenserBehaviorsSurface behaviors;

    /** One dispense behavior: consumes from the stack, returns the remainder. */
    private interface Behavior {
        /**
         * Dispenses one use of the stack at the position facing the given
         * direction. @return the slot's remainder (empty when consumed to
         * nothing).
         */
        ItemStack dispense(BlockPosition pos, int facing, ItemStack stack);
    }

    public DispenserSystem(EngineWorld world, RedstoneSystem redstone,
                           ItemEntityManager itemEntities, ProjectileManager projectiles,
                           TntSystem tnt, HopperSystem hoppers, FxManager fx, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.redstone = Objects.requireNonNull(redstone, "redstone");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.projectiles = Objects.requireNonNull(projectiles, "projectiles");
        this.tnt = Objects.requireNonNull(tnt, "tnt");
        this.hoppers = Objects.requireNonNull(hoppers, "hoppers");
        this.fx = Objects.requireNonNull(fx, "fx");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** The public §27.7 dispense gate (null until the server wires the bus). */
    private net.zaminmc.torch.server.event.EngineEventBus eventBus;

    /**
     * Wires the public dispense gate: every dispense (built-in and
     * custom behaviors alike) fires the cancellable pre-commit event
     * before the behavior walk. Called once by the server at boot.
     */
    public void setEventBus(net.zaminmc.torch.server.event.EngineEventBus eventBus) {
        this.eventBus = eventBus;
    }

    /** @return the block types the family registers at boot. */
    public static net.zaminmc.torch.server.block.BlockRegistryBuilder registerBlocks(
            net.zaminmc.torch.server.block.BlockRegistryBuilder builder) {
        return DispenserBlocks.registerAll(builder);
    }

    /** @return the container state at the position, creating it on first use. */
    public DispenserBlockEntity getOrCreate(BlockPosition position) {
        DispenserBlockEntity existing = containers.get(position);
        if (existing != null) {
            return existing;
        }
        DispenserBlockEntity created = new DispenserBlockEntity();
        containers.put(position, created);
        return created;
    }

    /** @return the live state at the position, or null when there is none. */
    public DispenserBlockEntity peek(BlockPosition position) {
        return containers.get(position);
    }

    /**
     * The public behavior registry (§27.3's registration surface): register
     * and reset per-item dispense behaviors — built once, on first use.
     */
    public net.zaminmc.torch.dispenser.DispenseBehaviors behaviors() {
        DispenserBehaviorsSurface surface = behaviors;
        if (surface == null) {
            surface = new DispenserBehaviorsSurface(world);
            behaviors = surface;
        }
        return surface;
    }

    /** @return the whole container map (the save snapshot). */
    public Map<BlockPosition, DispenserBlockEntity> snapshot() {
        return new LinkedHashMap<>(containers);
    }

    /** Restores persisted state after boot. */
    public void restoreAll(Map<BlockPosition, DispenserBlockEntity> restored) {
        containers.putAll(restored);
    }

    // ------------------------------------------------------------------
    // The activation (neighborChanged + the 4-tick reaction)
    // ------------------------------------------------------------------

    /**
     * The cross-system neighbor bus arm: the position that heard a change —
     * and its own six neighbors, the same defensive walk the hopper runs
     * (the notification can arrive at the stone a powered lever mounts on,
     * one hop short of the family block itself) — re-reads the trigger
     * state (the reference's neighborChanged).
     */
    public void neighborHeard(BlockPosition position) {
        updateTriggered(position);
        for (int[] offset : OFFSETS) {
            updateTriggered(position.offset(offset[0], offset[1], offset[2]));
        }
    }

    /**
     * The TRIGGERED pair flip (DispenserBlock.neighborChanged lines
     * 118-128): power at the block — or at the cell above, the reference's
     * own through-the-top read — arms the 4-tick reaction and raises the
     * pair; the power dropping clears it. A short pulse still dispenses:
     * the schedule was already armed and the tick never re-checks the pair.
     */
    public void updateTriggered(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!DispenserBlocks.isFamily(type)) {
            return;
        }
        boolean signal = hasNeighborSignal(pos) || hasNeighborSignal(pos.offset(0, 1, 0));
        boolean triggered = DispenserBlocks.triggered(type);
        if (signal && !triggered) {
            redstone.scheduleDispense(pos, TICK_RATE);
            world.setBlock(pos, DispenserBlocks.ofTriggerState(type, true));
        } else if (!signal && triggered) {
            world.setBlock(pos, DispenserBlocks.ofTriggerState(type, false));
        }
    }

    /** The reference's hasNeighborSignal: any of the six neighbors emits. */
    private boolean hasNeighborSignal(BlockPosition pos) {
        for (int direction = 0; direction < 6; direction++) {
            int[] o = OFFSETS[direction];
            if (redstone.signal(pos.offset(o[0], o[1], o[2]), direction) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * The scheduled reaction (DispenserBlock.tick lines 130-135): the
     * dispense itself, four ticks after the rising edge. The family was
     * re-checked at dispatch time; the walk here validates again.
     */
    public void scheduledTick(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!DispenserBlocks.isFamily(type)) {
            return; // the block changed under the tick: stale entry
        }
        dispense(pos, type);
    }

    // ------------------------------------------------------------------
    // The dispense (DispenserBlock.dispense + DropperBlock.dispense)
    // ------------------------------------------------------------------

    private void dispense(BlockPosition pos, BlockType type) {
        DispenserBlockEntity container = getOrCreate(pos);
        int slot = container.pickNonEmptySlot();
        if (slot < 0) {
            // The empty click (world event 1001): random.click at 1.2.
            fx.sound(centerOf(pos), "random.click", 1.0f, 1.2f);
            return;
        }
        int facing = DispenserBlocks.facing(type);
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        // The Phase 4 boundary scope: the dispenser and its facing cell —
        // the fire/fluid/TNT/bonemeal arms write at the target, and the
        // whole dispense stays one same-tick unit across the seam.
        world.executeInRegions(
                new BlockPosition(Math.min(pos.x(), target.x()), Math.min(pos.y(), target.y()),
                        Math.min(pos.z(), target.z())),
                new BlockPosition(Math.max(pos.x(), target.x()), Math.max(pos.y(), target.y()),
                        Math.max(pos.z(), target.z())),
                () -> {
                    ItemStack stack = container.getItem(slot);
                    if (stack.isEmpty()) {
                        return; // emptied between the pick and the walk
                    }
                    // §27.7: the public dispense gate BEFORE the behavior
                    // walk — the stack still sits in the reservoir, no item
                    // has left. A cancelled dispense keeps the stack where
                    // it is (not even the empty click sounds).
                    if (eventBus != null) {
                        net.zaminmc.torch.event.block.DispenseEvent gate =
                                new net.zaminmc.torch.event.block.DispenseEvent(
                                        world, pos, Direction.byId(facing), stack, target,
                                        DispenserBlocks.isDropper(type));
                        eventBus.fire(gate);
                        if (gate.cancelled()) {
                            return; // the stack stays untouched
                        }
                    }
                    ItemStack remainder = DispenserBlocks.isDropper(type)
                            ? dropperDispense(pos, facing, container, slot, stack)
                            : dispenserDispense(pos, facing, stack);
                    container.setItem(slot, remainder);
                });
    }

    /**
     * The dispenser's walk: the behavior registry answers for the item, and
     * the default item drop covers everything unregistered
     * (DefaultedRegistry's own default — Bootstrap line 34).
     */
    private ItemStack dispenserDispense(BlockPosition pos, int facing, ItemStack stack) {
        Behavior behavior = behaviorOf(stack);
        return behavior.dispense(pos, facing, stack);
    }

    /**
     * The dropper's walk (DropperBlock.dispense lines 41-63): a container
     * at the facing cell takes ONE item through its receiving face —
     * silently, the vanilla dropper push; a container-less cell falls
     * through to the default item drop (the dropper's own private
     * DispenseItemBehavior — never a per-item behavior, the override's
     * whole point).
     */
    private ItemStack dropperDispense(BlockPosition pos, int facing,
                                       DispenserBlockEntity container, int slot, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        if (hoppers.containerAt(target)) {
            // ONE item per push (the copy().split(1) arm); the whole stack
            // stays when nothing fit (the copy() restore).
            ItemStack one = ItemStack.of(stack.type(), 1);
            boolean fit = hoppers.pushOneIntoContainer(target, HopperSystem.opposite(facing), one);
            if (fit) {
                return stack.count() > 1 ? stack.withCount(stack.count() - 1) : ItemStack.EMPTY;
            }
            return stack;
        }
        return defaultItemDrop(pos, facing, stack);
    }

    // ------------------------------------------------------------------
    // The behavior registry (Bootstrap.registerDispenseBehaviors)
    // ------------------------------------------------------------------

    /** @return the behavior the item answers with (the default drop when none). */
    private Behavior behaviorOf(ItemStack stack) {
        // The registration surface's consult (§27.3): a registered custom
        // overrides the built-in switch — the public stand-in for the
        // reference's Bootstrap.registerDispenseBehaviors table.
        DispenserBehaviorsSurface surface = behaviors;
        if (surface != null) {
            net.zaminmc.torch.dispenser.DispenseBehavior custom =
                    surface.customFor(stack.type().identifier().value());
            if (custom != null) {
                return (pos, facing, s) -> {
                    Direction dir = Direction.byId(facing);
                    if (dir == null) {
                        // An out-of-range facing cannot reach a public
                        // behavior — the default drop keeps the slot honest.
                        return defaultItemDrop(pos, facing, s);
                    }
                    ItemStack remainder;
                    try {
                        remainder = custom.dispense(new DispenserBehaviorsSurface
                                .EngineDispenseContext(world, pos, dir, s));
                    } catch (RuntimeException error) {
                        // The public contract: a throwing behavior is logged
                        // and the stack stays in the slot.
                        LOGGER.warning("A registered dispense behavior threw for "
                                + s.type().identifier().value() + ": " + error);
                        return s;
                    }
                    return remainder == null ? s : remainder;
                };
            }
        }
        String item = stack.type().identifier().value();
        return switch (item) {
            case "arrow" -> projectileBehavior(ProjectileEntity.Kind.ARROW);
            case "egg" -> projectileBehavior(ProjectileEntity.Kind.EGG);
            case "snowball" -> projectileBehavior(ProjectileEntity.Kind.SNOWBALL);
            case "fire_charge" -> this::fireCharge;
            case "tnt" -> this::tntPrime;
            case "water_bucket", "lava_bucket" -> this::bucketPour;
            case "bucket" -> this::bucketScoop;
            case "flint_and_steel" -> this::flintIgnite;
            case "bone_meal" -> this::boneMeal;
            // The splash potion (Bootstrap lines 102-124): the projectile
            // with the half variation and the 1.25 force multiplier; the
            // drinkable falls through to the default drop.
            case "potion" -> this::potionSplash;
            default -> this::defaultItemDrop;
        };
    }

    /**
     * The potion's dispense arm (Bootstrap lines 102-124): only the splash
     * flies — force 1.1 * 1.25, variation 6 * 0.5 — carrying the metadata
     * payload; the drinkable drops as an item.
     */
    private ItemStack potionSplash(BlockPosition pos, int facing, ItemStack stack) {
        if (!net.zaminmc.torch.server.potion.PotionHelper.isSplashPotion(stack.damage())) {
            return defaultItemDrop(pos, facing, stack);
        }
        Position at = dispensePosition(pos, facing);
        ProjectileEntity potion = projectiles.launchVector(ProjectileEntity.Kind.POTION, -1, at,
                OFFSETS[facing][0], OFFSETS[facing][1] + 0.1, OFFSETS[facing][2],
                1.1 * 1.25, 6.0 * 0.5);
        if (potion != null) {
            potion.setPotionDamage(stack.damage());
        }
        fx.sound(centerOf(pos), "random.bow", 1.0f, 1.2f);
        return consumeOne(stack);
    }

    /**
     * The projectile family (Bootstrap lines 66-84, DispenseProjectileBehavior):
     * the direction is (dx, dy + 0.1, dz) normalized, tilted by the
     * gaussian (sigma 0.0075 * 6), scaled by the force 1.1 — the launch
     * sound {@code random.bow} at 1.2 (world event 1002).
     */
    private Behavior projectileBehavior(ProjectileEntity.Kind kind) {
        return (pos, facing, stack) -> {
            Position at = dispensePosition(pos, facing);
            ProjectileEntity launched = projectiles.launchVector(kind, -1, at,
                    OFFSETS[facing][0], OFFSETS[facing][1] + 0.1, OFFSETS[facing][2],
                    1.1, 6.0);
            if (launched != null && kind == ProjectileEntity.Kind.ARROW) {
                // Bootstrap line 70: the dispensed arrow is COLLECTABLE
                // (the bare positional ctor plus the explicit pickup = 1 —
                // unlike the mob shooters' arrows).
                launched.setPickupStatus(1);
            }
            fx.sound(centerOf(pos), "random.bow", 1.0f, 1.2f);
            return consumeOne(stack);
        };
    }

    /**
     * The fire charge (Bootstrap lines 159-181): the small fireball spawns
     * 0.3 out from the dispense position with the gaussian-tilted direction
     * (sigma 0.05 per axis — the ctor's own normalize-and-scale takes over),
     * and the shot is {@code mob.ghast.fireball} at volume 2.0 (world event
     * 1009).
     */
    private ItemStack fireCharge(BlockPosition pos, int facing, ItemStack stack) {
        Position at = dispensePosition(pos, facing);
        double x = at.x() + OFFSETS[facing][0] * 0.3;
        double y = at.y() + OFFSETS[facing][1] * 0.3;
        double z = at.z() + OFFSETS[facing][2] * 0.3;
        projectiles.launchFireball(new Position(x, y, z),
                OFFSETS[facing][0], OFFSETS[facing][1], OFFSETS[facing][2]);
        fx.sound(centerOf(pos), "mob.ghast.fireball", 2.0f,
                (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        return consumeOne(stack);
    }

    /**
     * The TNT arm (Bootstrap lines 328-338): a primed body at the facing
     * cell's center (x/z + 0.5, the cell's floor), the full 80-tick fuse,
     * the priming hiss — and the wrapper's own click (world event 1000).
     */
    private ItemStack tntPrime(BlockPosition pos, int facing, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        tnt.spawnPrimedAt(target.x() + 0.5, target.y(), target.z() + 0.5);
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
        return consumeOne(stack);
    }

    /**
     * The filled bucket (Bootstrap lines 232-233): the pour rides the
     * reference's own state — the FLOWING form at LEVEL 0 (BucketItem.place
     * writes {@code this.liquid.getDefaultState()}, the flowing block) —
     * whose tick settles it into the source and carries the spread's first
     * pass. The whole cell must be open (the reference's own place gate)
     * — and the stack becomes one empty bucket. A blocked pour falls
     * through to the default drop.
     */
    private ItemStack bucketPour(BlockPosition pos, int facing, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        FluidBlocks.Kind kind = stack.type().identifier().value().equals("water_bucket")
                ? FluidBlocks.Kind.WATER : FluidBlocks.Kind.LAVA;
        if (!world.getBlock(target).equals(world.airType())) {
            return defaultItemDrop(pos, facing, stack);
        }
        world.setBlock(target,
                FluidBlocks.typeOf(kind, FluidBlocks.Form.FLOWING, 0));
        fx.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                "random.splash", 0.4f, kind == FluidBlocks.Kind.WATER ? 1.0f : 0.6f);
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
        return ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.BUCKET, 1);
    }

    /**
     * The empty bucket (Bootstrap lines 234-265): a source at the facing
     * cell scoops away — the stack of one swaps in place, a larger stack
     * inserts the filled bucket into a free slot (else the default drop),
     * and the original loses one.
     */
    private ItemStack bucketScoop(BlockPosition pos, int facing, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        BlockType at = world.getBlock(target);
        FluidBlocks.Kind kind = FluidBlocks.kindOf(at.identifier());
        // The form-agnostic LEVEL-0 read (BucketItem's own consult — the
        // material + LEVEL == 0 pair).
        if (kind == null || FluidBlocks.levelOf(at.identifier()) != 0) {
            return defaultItemDrop(pos, facing, stack);
        }
        world.setBlock(target, world.airType());
        ItemStack filled = ItemStack.of(kind == FluidBlocks.Kind.WATER
                ? net.zaminmc.torch.server.item.BuiltinItems.WATER_BUCKET
                : net.zaminmc.torch.server.item.BuiltinItems.LAVA_BUCKET, 1);
        fx.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                "random.splash", 0.4f, 1.0f);
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
        if (stack.count() == 1) {
            // The in-place swap (the reference's item.setItem + size = 1).
            return filled;
        }
        DispenserBlockEntity container = getOrCreate(pos);
        if (container.insertItem(filled) < 0) {
            defaultItemDrop(pos, facing, filled);
        }
        return stack.withCount(stack.count() - 1);
    }

    /**
     * The flint and steel (Bootstrap lines 266-296): open air at the facing
     * cell takes the fire block and the stack wears one use; a TNT block
     * there primes away instead; anything else sparks at nothing (the
     * failure click, world event 1001).
     */
    private ItemStack flintIgnite(BlockPosition pos, int facing, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        BlockType at = world.getBlock(target);
        if (at.equals(world.airType())) {
            world.setBlock(target, BuiltinBlocks.FIRE);
            fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
            return wearOne(stack);
        }
        if (tnt.isTntBlock(at)) {
            tnt.ignite(target, 80);
            fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
            return wearOne(stack);
        }
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.2f);
        return stack;
    }

    /**
     * The bone meal (Bootstrap lines 297-327, the white-dye arm): a growing
     * crop at the facing cell takes the {@code stage + nextInt(2..5)}
     * growth (WheatBlock.growFully lines 47-53) and the stack loses one;
     * anything else falls through to the default drop (the reference's
     * super.dispenseItem for the non-white dyes and the non-crops).
     */
    private ItemStack boneMeal(BlockPosition pos, int facing, ItemStack stack) {
        BlockPosition target = pos.offset(OFFSETS[facing][0], OFFSETS[facing][1],
                OFFSETS[facing][2]);
        int stage = net.zaminmc.torch.server.block.WorldSolidity.cropStageOf(
                world.getBlock(target).identifier());
        if (stage < 0 || stage >= 7) {
            return defaultItemDrop(pos, facing, stack);
        }
        int grown = Math.min(7, stage + 2 + random.nextInt(4));
        world.setBlock(target, RandomTickSystem.cropStage(
                world.getBlock(target).identifier(), grown));
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
        return consumeOne(stack);
    }

    /**
     * The default item drop (DispenseItemBehavior.dispenseItem lines 29-45):
     * the item entity at the dispense position dropped by 0.125 (vertical
     * facings) / 0.15625 (horizontal), the velocity 0.2-and-out with the
     * gaussian family (sigma 0.0075 * 6), and the click (world event 1000).
     * The smoke puff (world event 2000) rides the particle slice (ledgered).
     */
    private ItemStack defaultItemDrop(BlockPosition pos, int facing, ItemStack stack) {
        Position at = dispensePosition(pos, facing);
        double y = at.y() - (facing <= 1 ? 0.125 : 0.15625);
        // The engine's ItemStack.split is a copy-out (the record model), so
        // the consumed unit builds explicitly and the slot takes the
        // remainder — the reference's in-place split decomposed.
        ItemStack one = stack.split(1);
        ItemEntity entity = itemEntities.spawnThrown(new Position(at.x(), y, at.z()), one);
        if (entity != null) { // the §27.7 gate may withhold the egress
            double out = random.nextDouble() * 0.1 + 0.2;
            entity.setVelocity(
                    OFFSETS[facing][0] * out + random.nextGaussian() * ITEM_SPREAD,
                    0.2 + random.nextGaussian() * ITEM_SPREAD,
                    OFFSETS[facing][2] * out + random.nextGaussian() * ITEM_SPREAD);
        }
        fx.sound(centerOf(pos), "random.click", 1.0f, 1.0f);
        return consumeOne(stack);
    }

    // ------------------------------------------------------------------
    // The helpers
    // ------------------------------------------------------------------

    /**
     * The dispense position (DispenserBlock.getDispensePos lines 169-175):
     * the block's center pushed 0.7 out along the facing — 0.2 past the
     * face.
     */
    private static Position dispensePosition(BlockPosition pos, int facing) {
        return new Position(
                pos.x() + 0.5 + 0.7 * OFFSETS[facing][0],
                pos.y() + 0.5 + 0.7 * OFFSETS[facing][1],
                pos.z() + 0.5 + 0.7 * OFFSETS[facing][2]);
    }

    /** The block's center (the event sounds' origin). */
    private static Position centerOf(BlockPosition pos) {
        return new Position(pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    /** One unit off the stack (the consumed-item family's remainder). */
    private static ItemStack consumeOne(ItemStack stack) {
        return stack.count() > 1 ? stack.withCount(stack.count() - 1) : ItemStack.EMPTY;
    }

    /**
     * The one-use wear (the reference's {@code item.takeDamage(1, random)}
     * — the flint and steel arm): the damage climbs one, the broken tool
     * leaves the slot empty.
     */
    private ItemStack wearOne(ItemStack stack) {
        int maxDurability = stack.type().maxDurability();
        if (maxDurability <= 0) {
            return consumeOne(stack); // a non-durable stack just spends one
        }
        // The Unbreakable gate (Slice 94/G1 — the reference's takeDamage
        // line 197: !isDamageable answers before any wear): a flagged
        // flint-and-steel never climbs, never breaks.
        if (stack.unbreakable()) {
            return stack;
        }
        int damage = stack.damage() + 1;
        return damage >= maxDurability ? ItemStack.EMPTY : stack.withDamage(damage);
    }

    // ------------------------------------------------------------------
    // The block arms
    // ------------------------------------------------------------------

    /**
     * The placement re-orientation (DispenserBlock.onAdded lines 49-75): a
     * dispenser placed facing INTO an opaque wall flips to the open side
     * (north-south first, then west-east) — the reference's own quirk, run
     * at placement only (the flag-1 walk; the TRIGGERED flips ride flag 4
     * and the piston pushes flag 2, neither re-orienting). The write
     * re-settles TRIGGERED false (the reference's own pair clear).
     */
    public void updateFacingOnPlacement(BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        if (!DispenserBlocks.isFamily(type)) {
            return;
        }
        int facing = DispenserBlocks.facing(type);
        boolean northOpaque = WorldSolidity.isSolid(world.getBlock(pos.offset(0, 0, -1)));
        boolean southOpaque = WorldSolidity.isSolid(world.getBlock(pos.offset(0, 0, 1)));
        if (facing == 2 && northOpaque && !southOpaque) { // NORTH into a wall
            facing = 3; // SOUTH
        } else if (facing == 3 && southOpaque && !northOpaque) { // SOUTH into a wall
            facing = 2; // NORTH
        } else {
            boolean westOpaque = WorldSolidity.isSolid(world.getBlock(pos.offset(-1, 0, 0)));
            boolean eastOpaque = WorldSolidity.isSolid(world.getBlock(pos.offset(1, 0, 0)));
            if (facing == 4 && westOpaque && !eastOpaque) { // WEST into a wall
                facing = 5; // EAST
            } else if (facing == 5 && eastOpaque && !westOpaque) { // EAST into a wall
                facing = 4; // WEST
            }
        }
        if (facing != DispenserBlocks.facing(type) || DispenserBlocks.triggered(type)) {
            world.setBlock(pos, DispenserBlocks.isDropper(type)
                    ? DispenserBlocks.dropperOf(facing, false)
                    : DispenserBlocks.dispenserOf(facing, false));
        }
    }

    /**
     * The placement wake (the reference's createBlockEntity at placement):
     * every committed family state creates its block-entity state — and
     * the comparator wake keeps the fullness read honest.
     */
    @Override
    public void onBlockChanged(EngineWorld changed, BlockPosition position, BlockType now) {
        if (DispenserBlocks.isFamily(now)) {
            DispenserBlockEntity container = getOrCreate(position);
            container.setContentsListener(() -> redstone.wakeComparators(position));
        }
    }

    /**
     * A survival break committed on the family block (DispenserBlock.onRemoved
     * lines 158-167): spill every slot into the world, then wake the
     * comparators. The creative path just discards (the per-tick sweep).
     */
    public void onBlockBroken(BlockPosition position) {
        DispenserBlockEntity container = containers.remove(position);
        if (container == null) {
            return;
        }
        Position origin = new Position(position.x(), position.y(), position.z());
        for (ItemStack stack : container.snapshotSlots()) {
            if (!stack.isEmpty()) {
                itemEntities.spawnDropAtBlock(origin, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
        }
        redstone.wakeComparators(position);
    }

    /**
     * The per-tick sweep: state whose block left the family discards — but
     * only on a LOADED chunk (an unloaded chunk reads as air; the vanilla
     * block entity freezes there, so restored state survives the restart).
     */
    public void tick() {
        containers.entrySet().removeIf(entry ->
                world.isChunkLoadedAt(entry.getKey())
                        && !DispenserBlocks.isFamily(world.getBlock(entry.getKey())));
    }
}
