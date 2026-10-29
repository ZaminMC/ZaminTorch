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

    /**
     * The wall sign's support-cell offset (WallSignBlock's FACING
     * opposite): the sign faces north (metadata 2) hanging on the block to
     * its SOUTH, and so on around the compass.
     */
    private static int[] wallSignSupportOffset(String value) {
        if (value.equals("wall_sign")) {
            return new int[]{0, 1};     // faces north: the wall behind is south (+z)
        }
        if (value.equals("wall_sign_south")) {
            return new int[]{0, -1};    // faces south: the wall behind is north (-z)
        }
        if (value.equals("wall_sign_west")) {
            return new int[]{1, 0};     // faces west: the wall behind is east (+x)
        }
        return new int[]{-1, 0};          // wall_sign_east: the wall behind is west (-x)
    }

    private static final Identifier TORCH = Identifier.parse("minecraft:torch");
    private static final Identifier GRASS = Identifier.parse("minecraft:grass_block");
    private static final Identifier NETHER_PORTAL = Identifier.parse("minecraft:nether_portal");
    private static final Identifier NETHER_PORTAL_Z = Identifier.parse("minecraft:nether_portal_z");

    private static final Identifier SUGAR_CANE = Identifier.parse("minecraft:sugar_cane");
    private static final Identifier CACTUS = Identifier.parse("minecraft:cactus");

    private final EngineWorld world;
    private final ItemEntityManager itemEntities;
    private final FallingBlockEntityManager fallingEntities;

    /** The flora rolls (the 1/8 seed roll, the double-plant drops — tick-thread confined). */
    private final java.util.Random random = new java.util.Random();

    /** One pending scheduled update. */
    private record Scheduled(BlockPosition position, long dueTick) {
    }

    /** One pending vanilla fall check ({@code scheduleTick(pos, FallingBlock, 2)}). */
    private record FallCheck(BlockPosition position, long dueTick) {
    }

    private final ArrayDeque<Scheduled> queue = new ArrayDeque<>();
    private final Set<Long> pending = new HashSet<>();

    /** The vanilla fuse kept for the unwired window ({@code FallingBlock.getTickRate} is 2 game ticks); the wired seam reads GravityFalls.DELAY. */
    static final int FALL_CHECK_DELAY = 2;
    private final ArrayDeque<FallCheck> fallChecks = new ArrayDeque<>();
    private final Set<Long> pendingFallChecks = new HashSet<>();

    public BlockUpdateSystem(EngineWorld world, ItemEntityManager itemEntities,
                             FallingBlockEntityManager fallingEntities) {
        this.world = Objects.requireNonNull(world, "world");
        this.itemEntities = Objects.requireNonNull(itemEntities, "itemEntities");
        this.fallingEntities = Objects.requireNonNull(fallingEntities, "fallingEntities");
    }

    /**
     * The scheduled-tick seam (the gravity fuse's migration, GravityFalls):
     * when wired (the engine's boot always wires it), the fuse rides the
     * ScheduledTickSystem — the reference's own scheduleTick(pos, this, 2/5).
     * A standalone-constructed system keeps its own queue with the same
     * delays and the same tryFall (the unwired window, named in
     * GravityFalls' javadoc — not a second behavior).
     */
    private volatile ScheduledTickSystem scheduledTicks;

    /** Wires the gravity fuse onto the scheduled-tick surface. */
    public void setScheduledTicks(ScheduledTickSystem scheduledTicks) {
        this.scheduledTicks = java.util.Objects.requireNonNull(scheduledTicks, "scheduledTicks");
    }

    /**
     * The banner tile seam: the support-drop arms read the tile for the
     * tagged item (the base + the patterns ride the drop). Null until the
     * engine wires it (the standalone-constructed test rig drops the plain
     * item — the tile-less fallback walk).
     */
    private volatile net.zaminmc.torch.server.banner.BannerManager banners;

    /** Wires the banner tile read onto the support-drop arms. */
    public void setBanners(net.zaminmc.torch.server.banner.BannerManager banners) {
        this.banners = banners;
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
        // THE RAIL FAMILIES' OWN-COMMIT EXEMPTION (the thirteenth verifier
        // pass — the reference's dispatch law, World.updateNeighbors lines
        // 342-349 over setBlockState's flag-1 walk): a block's own change
        // notifies its SIX NEIGHBOURS alone; the changed cell ITSELF never
        // receives neighborChanged — the placed rail's own commit runs
        // onAdded's connection walk with NO support consult
        // (AbstractRailBlock.onAdded lines 76-83, the consult the item
        // path's canBePlaced arm carries), so a directly-written rail (the
        // plugin path, the test harness, a world import) FLOATS exactly
        // like vanilla's until a NEIGHBOUR changes; the support pops ride
        // the neighbour wakes alone. The {0,0,0} self-offset stays for
        // the other families (the torch/carpet/sign arms' own established
        // convention — their own audits' rows).
        boolean changedCellIsRail = RailBlocks.isRailFamily(newType);
        for (int[] offset : NEIGHBOR_OFFSETS) {
            if (changedCellIsRail && offset[0] == 0 && offset[1] == 0 && offset[2] == 0) {
                continue; // the rail's own commit never wakes itself
            }
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

    // ------------------------------------------------------------------
    // The public §27.3 scheduled-tick surface (the query/cancel/request
    // arms the API's ScheduledUpdates view rides). The queries copy the
    // tick-confined structures; the mutations run on the world's own
    // authoritative context (the view asserts it).
    // ------------------------------------------------------------------

    /** @return a copy of the pending neighbor updates in queue order. */
    public java.util.List<ScheduledTickView> pendingUpdates() {
        java.util.List<ScheduledTickView> copy = new java.util.ArrayList<>(queue.size());
        for (Scheduled entry : queue) {
            copy.add(new ScheduledTickView(entry.position(), entry.dueTick()));
        }
        return copy;
    }

    /** @return a copy of the pending gravity fuses — the seam's scheduled entries when wired (the reference's own surface), the unwired window's own queue otherwise. */
    public java.util.List<ScheduledTickView> pendingFallChecksView() {
        ScheduledTickSystem seam = scheduledTicks;
        if (seam != null) {
            java.util.List<ScheduledTickView> copy = new java.util.ArrayList<>();
            for (ScheduledTickEntry entry : seam.pending()) {
                if (GravityFalls.isGravityFamily(entry.block())) {
                    copy.add(new ScheduledTickView(entry.pos(), entry.time()));
                }
            }
            return copy;
        }
        java.util.List<ScheduledTickView> copy = new java.util.ArrayList<>(fallChecks.size());
        for (FallCheck entry : fallChecks) {
            copy.add(new ScheduledTickView(entry.position(), entry.dueTick()));
        }
        return copy;
    }

    /** @return the pending neighbor update for the position, or null. */
    public ScheduledTickView pendingUpdateAt(BlockPosition position) {
        for (Scheduled entry : queue) {
            if (entry.position().equals(position)) {
                return new ScheduledTickView(entry.position(), entry.dueTick());
            }
        }
        return null;
    }

    /**
     * Cancels the position's pending neighbor update (tick-thread
     * context — the public view asserts the world's ownership).
     *
     * @return whether a pending update was cancelled
     */
    public boolean cancelUpdate(BlockPosition position) {
        if (pending.remove(key(position))) {
            queue.removeIf(entry -> entry.position().equals(position));
            return true;
        }
        return false;
    }

    /**
     * Cancels the position's pending gravity fuse — the seam's scheduled
     * entries when wired (the reference's own surface), the unwired
     * window's own queue otherwise.
     *
     * @return whether a pending fuse was cancelled
     */
    public boolean cancelFallCheck(BlockPosition position) {
        ScheduledTickSystem seam = scheduledTicks;
        if (seam != null) {
            boolean removed = false;
            for (ScheduledTickEntry entry : seam.pending()) {
                if (entry.pos().equals(position) && GravityFalls.isGravityFamily(entry.block())) {
                    removed |= seam.cancelAt(position, entry.block());
                }
            }
            return removed;
        }
        if (pendingFallChecks.remove(key(position))) {
            fallChecks.removeIf(entry -> entry.position().equals(position));
            return true;
        }
        return false;
    }

    /**
     * Requests a neighbor update for the position due the current tick
     * (the engine's own schedule arm — a cell already pending keeps its
     * earlier due tick).
     */
    public void requestUpdate(BlockPosition position) {
        schedule(position, world.totalTicks());
    }

    /** The immutable public view of one pending scheduled tick. */
    public record ScheduledTickView(BlockPosition position, long dueTick) {
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
     * The vanilla {@code FallingBlock.tryFall} (GravityFalls.tryFall — the
     * reference's lines 37-59, the engine's verified Slice-470 shape): the
     * block converts when the cell below can be fallen through (air, water,
     * lava, fire — sand stacked over a pond collapses into it) and the block
     * itself still occupies the cell. Tick-thread context.
     */
    private void runFallCheck(BlockPosition position) {
        GravityFalls.tryFall(world, position, fallingEntities);
    }

    /** Applies the rule set to one position. Tick-thread context. */
    private void runUpdate(BlockPosition position) {
        BlockType type = world.getBlock(position);
        if (type.equals(world.airType())) {
            return;
        }
        Identifier identifier = type.identifier();
        if (GravityFalls.isGravityFamily(identifier)) {
            // The vanilla wake (FallingBlock.onAdded/onNeighborChanged →
            // DragonEggBlock's own): the tryFall check schedules the family's
            // own delay out — 2 for the sand/anvil families, the egg's own 5.
            // The seam rides the scheduled-tick surface (the reference's
            // scheduleTick); the unwired window keeps this system's own
            // queue with the same delay.
            int delay = GravityFalls.delayOf(identifier);
            ScheduledTickSystem seam = scheduledTicks;
            if (seam != null) {
                seam.scheduleTick(position, identifier, delay);
            } else {
                scheduleFallCheck(position, world.totalTicks() + delay);
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
        // The standing sign's support rule (the twelfth verifier pass —
        // StandingSignBlock.neighborChanged lines 22-29): the floor below
        // must be MATERIAL-solid (Material.isSolid, not the collision
        // model — a sign on a torch, a carpet, tall grass or a snow layer
        // pops, a sign on leaves or a cobweb stands), the drop the sign
        // item through the standard block-drop delay.
        if (identifier.namespace().equals("minecraft")
                && (identifier.value().equals("sign")
                    || identifier.value().startsWith("sign_"))) {
            if (MaterialSolidity.isSolid(world.getBlock(position.offset(0, -1, 0)).identifier())) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SIGN, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The wall sign's support rule (WallSignBlock.neighborChanged lines
        // 43-51): the block BEHIND the sign — the cell it hangs on, the
        // facing's own opposite — must be material-solid, else the sign
        // pops the same way. The per-state facing: metadata 2-5 name the
        // direction the sign faces (away from its wall), so the support
        // cell sits at the facing's opposite offset.
        if (identifier.namespace().equals("minecraft")
                && (identifier.value().equals("wall_sign")
                    || identifier.value().startsWith("wall_sign_"))) {
            int[] behind = wallSignSupportOffset(identifier.value());
            if (MaterialSolidity.isSolid(
                    world.getBlock(position.offset(behind[0], 0, behind[1])).identifier())) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.SIGN, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The rail families' support rule (the thirteenth verifier pass —
        // AbstractRailBlock.neighborChanged lines 86-111, all four families
        // through the isRailFamily consult): the floor below must carry a
        // solid TOP (the has-solid-top family — a bottom slab, a fence, a
        // wall or the thin snow is not a rail bed), and each ASCENDING
        // shape's own run cell (the ascent's horizontal direction) must
        // too; a violation pops the rail with its family item's drop
        // (dropItems + removeBlock, lines 104-106). No re-walk rides
        // this arm — the reference's neighborChanged re-walks only on a
        // redstone signal (RailBlock lines 19-23 — the honest tail: the
        // redstone family's row).
        if (RailBlocks.isRailFamily(type)) {
            if (RailConnections.survivesAt(world, position)) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            RailBlocks.familyItemOf(type)).orElseThrow(), 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }

        // The banner family's support rules (BannerBlock.Standing /
        // .Wall neighborChanged, lines 128-135 + 184-192 — the sign
        // family's twins): the standing banner consults the floor's
        // MATERIAL solidity (Material.isSolid via MaterialSolidity, not
        // the collision model — a banner on a torch, a carpet, tall grass
        // or a snow layer pops, a banner on leaves, a cobweb, a sign or
        // another banner stands), the wall banner the cell OPPOSITE its
        // facing (the wall it hangs on) the same material consult; the
        // departure drops the TAGGED item through the tile read
        // (dropItems' own walk) and clears the cell.
        if (net.zaminmc.torch.server.block.BannerBlocks.isBanner(identifier)) {
            boolean supported;
            if (net.zaminmc.torch.server.block.BannerBlocks.isStanding(identifier)) {
                supported = MaterialSolidity.isSolid(
                        world.getBlock(position.offset(0, -1, 0)).identifier());
            } else {
                int facing = net.zaminmc.torch.server.block.BannerBlocks.facingOf(type);
                // The wall sits OPPOSITE the facing (the banner looks away
                // from its wall): facing north (-Z) hangs on the south
                // wall (+Z), and so on.
                int[] away = switch (facing) {
                    case 2 -> new int[]{0, 0, 1};  // faces north: the wall is south
                    case 3 -> new int[]{0, 0, -1}; // faces south: the wall is north
                    case 4 -> new int[]{1, 0, 0};  // faces west: the wall is east
                    default -> new int[]{-1, 0, 0}; // faces east: the wall is west
                };
                supported = MaterialSolidity.isSolid(
                        world.getBlock(position.offset(away[0], away[1], away[2])).identifier());
            }
            if (supported) {
                return;
            }
            net.zaminmc.torch.server.banner.BannerManager bannerStore = banners;
            net.zaminmc.torch.server.banner.BannerManager.BannerTile tile =
                    bannerStore != null ? bannerStore.remove(position) : null;
            ItemStack drop = tile != null
                    ? net.zaminmc.torch.server.banner.BannerManager.dropStack(tile)
                    : net.zaminmc.torch.server.banner.BannerManager.dropStackWithoutTile();
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    drop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The carpet family's support rule (Slice 31 — CarpetBlock's
        // canSurviveOrBreak lines 79-89 over neighborChanged): only AIR
        // below breaks it (water and torches carry it), the drop carrying
        // the family item with the color damage (dropItems' own metadata
        // walk) and the cell clearing to air.
        if (net.zaminmc.torch.server.block.DyeFamilyBlocks.isCarpet(identifier)) {
            if (!world.getBlock(position.offset(0, -1, 0)).equals(world.airType())) {
                return; // supported
            }
            int color = net.zaminmc.torch.server.block.DyeFamilyBlocks.colorOf(identifier);
            net.zaminmc.torch.item.ItemStack drop = new net.zaminmc.torch.item.ItemStack(
                    net.zaminmc.torch.server.item.BuiltinItems.CARPET, 1, color);
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    drop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The cocoa's support rule (Slice 43 — CocoaBlock.neighborChanged
        // lines 110-114 + breakCocoa lines 116-120 + dropItems lines
        // 137-145: the FACING cell must be the jungle log; a departed log
        // pops the pod with the AGE band's dye rolls — 1 below age 2, 3 at
        // it — the brown dye's own damage 12).
        if (identifier.value().equals("cocoa") || identifier.value().startsWith("cocoa_")) {
            int facing = CocoaBlocks.facingOf(world.getBlock(position));
            if (CocoaBlocks.isSupported(world, position, facing)) {
                return;
            }
            int age = CocoaBlocks.ageOf(world.getBlock(position));
            world.setBlock(position, world.airType());
            int count = age >= 2 ? 3 : 1;
            for (int i = 0; i < count; i++) {
                itemEntities.spawnDropAtBlock(
                        new Position(position.x(), position.y(), position.z()),
                        new ItemStack(net.zaminmc.torch.server.item.BuiltinItems.INK_SAC, 1,
                                CocoaBlocks.BROWN_DYE_DAMAGE),
                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            return;
        }
        // The lily pad's support rule (Slice 42 — LilyPadBlock.canSurvive
        // lines 51-57: the block below must be WATER at LEVEL 0 — the
        // reference's own LEVEL-property consult, never the strict source
        // id: the still-to-flowing conversion keeps a true source at
        // LEVEL 0 in its flowing form, and the pad stays put on both).
        if (identifier.value().equals("waterlily")) {
            BlockType below = world.getBlock(position.offset(0, -1, 0));
            if (FluidBlocks.kindOf(below.identifier()) == FluidBlocks.Kind.WATER
                    && FluidBlocks.levelOf(below.identifier()) == 0) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.WATERLILY, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The flora support rules (Slice 44 — PlantBlock.canSurviveOrBreak
        // lines 60-71 over neighborChanged: every flower, the tall grass
        // and the dead bush re-read their soil on the neighbor bus and pop
        // with their dropItems walk when it departs — the flowers yield
        // their own items (getDropItemMetadata's TYPE damage for the
        // family item), the tall grass rolls the 1/8 seed walk, the dead
        // bush drops nothing).
        String floraName = identifier.value();
        if (FlowerBlocks.isFlowerIdentifier(identifier)
                || floraName.equals("tall_grass") || floraName.equals("dead_bush")) {
            BlockType floraSoil = world.getBlock(position.offset(0, -1, 0));
            boolean supported;
            if (floraName.equals("dead_bush")) {
                // DeadBushBlock.canBePlacedOn lines 27-31: the sand, the
                // hardened/stained clay or the dirt.
                Identifier soilId = floraSoil.identifier();
                String soilName = soilId.value();
                supported = soilName.equals("sand")
                        || soilName.equals("hardened_clay")
                        || soilName.equals("stained_hardened_clay")
                        || soilName.startsWith("stained_hardened_clay_")
                        || soilName.equals("dirt");
            } else {
                // PlantBlock.canBePlacedOn lines 38-41 (the flowers and the
                // tall grass's own inheritance): the grass, the dirt, the
                // farmland.
                supported = FlowerBlocks.isSupported(world, position);
            }
            if (supported) {
                return;
            }
            world.setBlock(position, world.airType());
            ItemStack drop;
            if (floraName.equals("tall_grass")) {
                // TallPlantBlock.getDropItem lines 55-58: the 1/8 seed
                // roll, nothing otherwise.
                drop = random.nextInt(8) == 0
                        ? ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.WHEAT_SEEDS, 1)
                        : null;
            } else if (floraName.equals("dead_bush")) {
                drop = null; // DeadBushBlock.getDropItem line 40: null
            } else if (floraName.equals("poppy")) {
                drop = ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.POPPY, 1);
            } else if (floraName.equals("dandelion")) {
                drop = ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.DANDELION, 1);
            } else {
                // The eight family variants: the family item with the TYPE
                // id in the damage field (FlowerBlock.getDropItemMetadata).
                drop = new ItemStack(net.zaminmc.torch.server.item.BuiltinItems.RED_FLOWER, 1,
                        Math.max(0, FlowerBlocks.typeOf(type)));
            }
            if (drop != null) {
                itemEntities.spawnDropAtBlock(
                        new Position(position.x(), position.y(), position.z()),
                        drop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            return;
        }
        // The mushroom pair's survival read (VIII8 — MushroomPlantBlock's
        // canSurviveOrBreak over PlantBlock.neighborChanged lines 60-71, the
        // same bus the flora family rides): the plant re-reads its own
        // canSurvive — the mycelium bypass, the podzol row (unshipped — the
        // counted note), the light-under-13-and-opaque-below law — and pops
        // with its own item when it fails (PlantBlock.dropItems' default
        // ItemMultiplied's getItemFromBlock form: the plant item itself).
        if (MushroomBlocks.isMushroomPlant(type)) {
            if (MushroomBlocks.canPlantSurviveAt(world, position)) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            net.zaminmc.torch.item.ItemStack plantDrop = new net.zaminmc.torch.item.ItemStack(
                    type.identifier().value().equals("brown_mushroom")
                            ? net.zaminmc.torch.server.item.BuiltinItems.BROWN_MUSHROOM
                            : net.zaminmc.torch.server.item.BuiltinItems.RED_MUSHROOM,
                    1);
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    plantDrop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The nether wart's support rule (the planting-arms pass —
        // NetherWartBlock.canSurvive lines 33-35 over PlantBlock's
        // neighborChanged lines 40-43 -> canSurviveOrBreak lines 50-55):
        // the cell below must be SOUL_SAND; the departure pops the block
        // with its item, the drop the reference's own age band
        // (NetherWartBlock.dropItems lines 49-63: age 3 -> 2 + nextInt(3),
        // else exactly one).
        if (type.identifier().value().startsWith("nether_wart_stage")) {
            if (world.getBlock(position.offset(0, -1, 0)).identifier().value()
                    .equals("soul_sand")) {
                return; // supported
            }
            int age = type.identifier().value().charAt("nether_wart_stage".length()) - '0';
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.NETHER_WART,
                            age >= 3 ? 2 + random.nextInt(3) : 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            return;
        }
        // The double-plant family's support rules (Slice 45 —
        // DoublePlantBlock.canSurviveOrBreak lines 65-88: the UPPER pops
        // to air when the cell below stops being the family's own lower
        // (no drop — getDropItem's UPPER null); the LOWER pops BOTH cells
        // with the lower's own drop walk when the soil departs or the
        // upper is gone (the reference's blockpos/blockpos1 pair writes).
        if (DoublePlantBlocks.isDoublePlantIdentifier(identifier)) {
            if (DoublePlantBlocks.isUpper(identifier)) {
                // The UPPER's read (canSurvive lines 96-102): the cell
                // below must be the family's own lower.
                if (DoublePlantBlocks.isDoublePlantIdentifier(
                        world.getBlock(position.offset(0, -1, 0)).identifier())
                        && !DoublePlantBlocks.isUpper(
                        world.getBlock(position.offset(0, -1, 0)).identifier())) {
                    return; // supported
                }
                world.setBlock(position, world.airType());
                return; // the UPPER never drops
            }
            // The LOWER's read: the soil AND the upper must both carry.
            if (DoublePlantBlocks.lowerSurvives(world, position)) {
                return; // supported
            }
            // The pair dies: the upper clears first (flag 2's own write —
            // no drop from it), then the lower with its drop walk.
            net.zaminmc.torch.block.BlockPosition upperCell = position.offset(0, 1, 0);
            if (DoublePlantBlocks.isUpper(world.getBlock(upperCell).identifier())) {
                world.setBlock(upperCell, world.airType());
            }
            world.setBlock(position, world.airType());
            net.zaminmc.torch.item.ItemStack drop = DoublePlantBlocks.lowerDrop(
                    type, random);
            if (drop != null) {
                itemEntities.spawnDropAtBlock(
                        new Position(position.x(), position.y(), position.z()),
                        drop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            return;
        }
        // The flower-pot's support rule (Slice 48 —
        // FlowerPotBlock.neighborChanged lines 122-128: the block below
        // must have a SOLID TOP; a departing support pops the pot WITH its
        // contents (dropItems the pot + onRemoved's plant item).
        if (FlowerPotBlocks.isPot(identifier)) {
            if (FlowerPotBlocks.isSupported(world, position)) {
                return; // supported
            }
            int content = FlowerPotBlocks.contentOf(identifier);
            world.setBlock(position, world.airType());
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.FLOWER_POT, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            net.zaminmc.torch.util.Identifier plant = FlowerPotBlocks.plantItemOf(content);
            if (plant != null) {
                net.zaminmc.torch.item.ItemStack plantDrop = new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.lookup(plant).orElseThrow(),
                        1, FlowerPotBlocks.plantDamageOf(content));
                itemEntities.spawnDropAtBlock(
                        new Position(position.x(), position.y(), position.z()),
                        plantDrop, ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            return;
        }
        // The cake's support rule (Slice 46 — CakeBlock.neighborChanged
        // lines 88-91 + canSurvive lines 92-94: the block below must be
        // SOLID; a departing support removes the cake with NO drop —
        // getDropItem's own null, the slices come only from the eat arm).
        if (CakeBlocks.isCake(identifier)) {
            if (CakeBlocks.isSupported(world, position)) {
                return; // supported
            }
            world.setBlock(position, world.airType());
            return;
        }
        if (FireBlocks.isFire(identifier)) {
            // The reference's FireBlock.neighborChanged (lines 311-315,
            // 1:1): a fire whose support and fuel both vanished dies
            // immediately on this path — the fire's own scheduled life
            // (the age/spread/burn walks) rides the ScheduledTickSystem
            // surface now, FireBlocks.tick (the first family migrated
            // off this system's own adapted arm).
            FireBlocks.neighborChanged(world, position);
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
        // The farmland crops' support rule (the fall-path family's own
        // find — PlantBlock.neighborChanged lines 40-43 over
        // canSurviveOrBreak lines 50-55 with WheatBlock.canBePlacedOn
        // lines 23-25: FARMLAND alone): the wheat, the carrots and the
        // potatoes (the WheatBlock class whole — both subclasses) and
        // both stems pop off a reverted field with the reference's own
        // drop laws (fortune 0, the pop's case): the base drop 1 x
        // getDropItem (age 7 the plant item, below the seed — the carrot
        // family's own pair folds to the carrot both ways), the age-7
        // extra three rolls of nextInt(15) <= 7 each one seed
        // (WheatBlock.dropItems lines 114-127), the potato's 1-in-50
        // poison twin (PotatoesBlock lines 28-34), the stems' three
        // rolls at every age (StemBlock lines 121-134). The farmland
        // trample's reversion and the solid-above arm's both carry the
        // crop's pop off this arm.
        String cropName = identifier.value();
        boolean farmlandCrop = cropName.startsWith("wheat_stage")
                || cropName.startsWith("carrots_stage")
                || cropName.startsWith("potatoes_stage")
                || cropName.startsWith("melon_stem_stage")
                || cropName.startsWith("pumpkin_stem_stage");
        if (farmlandCrop) {
            var floor = world.getBlock(position.offset(0, -1, 0)).identifier().value();
            if (floor.equals("farmland") || floor.equals("farmland_wet")) {
                return; // supported (canBePlacedOn: FARMLAND alone)
            }
            int stage = cropName.charAt(cropName.lastIndexOf("_stage") + 6) - '0';
            net.zaminmc.torch.server.item.EngineItemType seed;
            net.zaminmc.torch.server.item.EngineItemType plant = null;
            if (cropName.startsWith("wheat")) {
                seed = net.zaminmc.torch.server.item.BuiltinItems.WHEAT_SEEDS;
                plant = net.zaminmc.torch.server.item.BuiltinItems.WHEAT;
            } else if (cropName.startsWith("carrots")) {
                seed = net.zaminmc.torch.server.item.BuiltinItems.CARROT;
                plant = net.zaminmc.torch.server.item.BuiltinItems.CARROT;
            } else if (cropName.startsWith("potatoes")) {
                seed = net.zaminmc.torch.server.item.BuiltinItems.POTATO;
                plant = net.zaminmc.torch.server.item.BuiltinItems.POTATO;
            } else if (cropName.startsWith("melon")) {
                seed = net.zaminmc.torch.server.item.BuiltinItems.MELON_SEEDS;
            } else {
                seed = net.zaminmc.torch.server.item.BuiltinItems.PUMPKIN_SEEDS;
            }
            world.setBlock(position, world.airType());
            // The base drop (getDropItem): the plant at age 7, the seed
            // below; the stems carry the seed alone (their own pair).
            boolean stem = plant == null;
            boolean ripe = stage >= 7;
            net.zaminmc.torch.server.item.EngineItemType base =
                    ripe && plant != null ? plant : seed;
            itemEntities.spawnDropAtBlock(
                    new Position(position.x(), position.y(), position.z()),
                    ItemStack.of(base, 1),
                    ItemEntity.PICKUP_DELAY_DROP_TICKS);
            // The extra rolls: the crops at age 7 alone (three rolls of
            // nextInt(15) <= 7), the stems at every age (<= stage).
            int rollBound = stem ? stage : (ripe ? 7 : -1);
            for (int j = 0; j < 3; j++) {
                if (rollBound >= 0 && random.nextInt(15) <= rollBound) {
                    itemEntities.spawnDropAtBlock(
                            new Position(position.x(), position.y(), position.z()),
                            ItemStack.of(seed, 1),
                            ItemEntity.PICKUP_DELAY_DROP_TICKS);
                }
            }
            // The potato's poison twin (PotatoesBlock lines 28-34): the
            // 1-in-50 roll at age 7 alone.
            if (cropName.startsWith("potatoes") && ripe && random.nextInt(50) == 0) {
                itemEntities.spawnDropAtBlock(
                        new Position(position.x(), position.y(), position.z()),
                        ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.POISONOUS_POTATO, 1),
                        ItemEntity.PICKUP_DELAY_DROP_TICKS);
            }
            return;
        }
        // The farmland's solid-above law (the farmland state pass —
        // FarmlandBlock.neighborChanged lines 88-94): a material-SOLID
        // block arriving on the farmland turns it to DIRT (the crop above
        // popping through its own support arm). The solidity consult is
        // the MATERIAL model (getMaterial().isSolid() — MaterialSolidity,
        // the sign family's twelfth-pass class, merged since the audit
        // was written): a torch or a flower above never reverts the
        // field, a sign or a banner above DOES (their WOOD material is
        // solid where the collision model answers null).
        if (identifier.value().equals("farmland")
                || identifier.value().equals("farmland_wet")) {
            if (MaterialSolidity.isSolid(
                    world.getBlock(position.offset(0, 1, 0)).identifier())) {
                world.setBlock(position, BuiltinBlocks.DIRT);
            }
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
                || below.identifier().equals(BuiltinBlocks.RED_SAND.identifier())
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
