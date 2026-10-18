package net.zaminmc.torch.server.block;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.Objects;
import java.util.Random;

/**
 * Random block ticks (§471 "random tick + neighbor state + scheduled
 * behavior"), the historical growth driver: every tick, each loaded chunk
 * within the historical 128-block player range samples three random positions
 * per section ({@code randomTickSpeed} 3), and only a few block kinds carry
 * random-tick rules — this slice: grass.
 *
 * <p>Grass rules are the historical 1.8 {@code BlockGrass.updateTick}: decay
 * to dirt when its own light drops below 4; otherwise up to four spread
 * attempts pick a nearby dirt cell (±1 x/z, −3..+1 y) and grow grass into it
 * when its light is at least 9 — the historical light gates, now served by
 * the real light storage (block light + skylight, §475) the LightEngine
 * maintains. This upgrades the earlier "nothing opaque above" approximation
 * with the same semantics for covered cells and the added torch-based spread
 * indoors (cave farms light their grass like the historical game).</p>
 */
public final class RandomTickSystem {

    /** Historical {@code randomTickSpeed} default: three picks per section. */
    public static final int PICKS_PER_SECTION = 3;
    /** Historical range: chunks within this distance of a player receive random ticks. */
    public static final double PLAYER_RANGE = 128.0;
    /** Historical spread-attempt count per randomly-ticked grass block. */
    private static final int SPREAD_ATTEMPTS = 4;
    private static final int SECTIONS = 16;

    private final EngineWorld world;
    /**
     * The {@code randomTickSpeed} read (Slice 24a — ServerWorld.tickChunks
     * line 343: {@code getInt("randomTickSpeed")} per tick walk; the
     * picks-per-section band rides the rule). Null keeps the historical
     * default three (the rule's own default value).
     */
    private volatile java.util.function.IntSupplier randomTickSpeedRead;

    private final Random random;

    /**
     * The lane's parallel executor (the Phase-6 slice): null keeps the
     * sequential walk — the picks computed inline on the tick lane.
     */
    private volatile net.zaminmc.torch.server.concurrent.DomainWorkerPool lanePool;

    /**
     * The lane's OWN tick counter (the stream derivation's time axis):
     * incremented once per {@link #tick} call, NOT read from the world's
     * tick — the direct-driven tests call {@code tick()} without the
     * ticker (the world's counter never moves), and a frozen time axis
     * would freeze the derived stream with it (the same picks every
     * call — the statistical guard loops' own death). The live wiring
     * advances both counters together (one {@code tick()} per world
     * tick), so the lane's counter IS the tick the picks belong to.
     */
    private long laneTick;

    /**
     * The lane's engagement evidence (§28.9 observability): the count of
     * tick walks that actually rode the pool dispatch (the multi-region
     * gate passed AND the pool armed). The lane's equivalence test
     * asserts this is > 0 armed and == 0 unarmed — the proof the walk
     * exercised the lane, never a silent sequential fallback.
     */
    private volatile long parallelDispatchTicks;

    public RandomTickSystem(EngineWorld world, Random random) {
        this.world = Objects.requireNonNull(world, "world");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Arms the parallel lane (the Phase-6 gate): the per-region pick
     * computation dispatches onto the pool when armed — the derived
     * streams keep the picks IDENTICAL to the sequential walk (the
     * equivalence the lane's own proof carries), and the apply side
     * never leaves the tick lane (§6.2's one authoritative writer).
     */
    public void setLanePool(net.zaminmc.torch.server.concurrent.DomainWorkerPool pool) {
        this.lanePool = pool;
    }

    /**
     * Attaches the {@code randomTickSpeed} read (the game-rule table's
     * picks-per-section band — Slice 24a).
     */
    public void setRandomTickSpeedRead(java.util.function.IntSupplier read) {
        this.randomTickSpeedRead = java.util.Objects.requireNonNull(read, "read");
    }

    /**
     * The lane's engagement evidence: how many tick walks rode the pool
     * dispatch (the observability the lane's own proof asserts on —
     * 0 means every walk ran the sequential compute, whether unarmed
     * or single-region).
     */
    public long parallelDispatchTicks() {
        return parallelDispatchTicks;
    }

    /**
     * Advances one tick of random growth. Tick-thread context.
     *
     * <p>THE LANE (the Phase-6 slice): the pick COMPUTATION rides the
     * per-region derived streams ({@link RandomTickPicks}) — the
     * sequential walk below and the parallel dispatch the lane caller
     * drives produce IDENTICAL picks per (tick, region) — and every
     * world write stays on THIS thread (§6.2's one authoritative
     * writer: the apply side never leaves the tick lane).</p>
     */
    public void tick(Iterable<PlayerSession> players) {
        // randomTickSpeed (Slice 24a — ServerWorld.tickChunks line 343):
        // the rule answers the per-section pick count, read once per
        // walk; 0 stops the walk entirely (the reference's own l2 > 0
        // gate).
        java.util.function.IntSupplier speedRead = randomTickSpeedRead;
        int picks = speedRead != null ? speedRead.getAsInt() : PICKS_PER_SECTION;
        if (picks <= 0) {
            return;
        }
        laneTick++;
        // THE LANE (the Phase-6 slice — the compute-then-apply region
        // lane): the in-range chunks group by their region, each
        // region's picks derive from its OWN stream
        // (RandomTickPicks.streamFor(baseSeed, tick, regionId)) — so
        // the sequential walk and the pool dispatch produce IDENTICAL
        // picks — and every world write applies on THIS thread (the
        // apply side never leaves the tick lane, §6.2's one writer).
        java.util.Map<Long, java.util.List<long[]>> byRegion =
                new java.util.TreeMap<>();
        for (EngineChunk chunk : world.loadedChunks()) {
            if (!withinPlayerRange(chunk.position(), players)) {
                continue;
            }
            net.zaminmc.torch.server.concurrent.RegionPartition partition =
                    world.regionPartition();
            long regionId = partition != null
                    ? partition.regionId(chunk.position().x(), chunk.position().z()) : 0L;
            byRegion.computeIfAbsent(regionId, key -> new java.util.ArrayList<>())
                    .add(new long[]{chunk.position().x(), chunk.position().z()});
        }
        if (byRegion.isEmpty()) {
            return;
        }
        long tick = laneTick;
        long seed = world.generatorSeed();
        java.util.List<java.util.Map.Entry<Long, java.util.List<long[]>>> regions =
                new java.util.ArrayList<>(byRegion.entrySet());
        java.util.List<java.util.List<long[]>> computed =
                new java.util.ArrayList<>(java.util.Collections.nCopies(
                        regions.size(), null));
        net.zaminmc.torch.server.concurrent.DomainWorkerPool pool = lanePool;
        if (pool != null && !pool.isShutdown() && regions.size() > 1) {
            // The parallel compute: one job per region, the results by
            // stable index; the join happens HERE (the tick edge waits).
            java.util.List<Runnable> jobs = new java.util.ArrayList<>();
            for (int index = 0; index < regions.size(); index++) {
                final int slot = index;
                var entry = regions.get(index);
                jobs.add(() -> computed.set(slot,
                        net.zaminmc.torch.server.concurrent.RandomTickPicks.picksForRegion(
                                seed, tick, entry.getKey(), entry.getValue(), picks)));
            }
            pool.dispatchAndJoin(jobs);
            parallelDispatchTicks++;
        } else {
            for (int index = 0; index < regions.size(); index++) {
                var entry = regions.get(index);
                computed.set(index,
                        net.zaminmc.torch.server.concurrent.RandomTickPicks.picksForRegion(
                                seed, tick, entry.getKey(), entry.getValue(), picks));
            }
        }
        // The apply: in the stable region order, on the tick lane.
        for (java.util.List<long[]> regionPicks : computed) {
            applyPicks(regionPicks);
        }
    }

    /**
     * The lane's apply side: the per-region picks (computed inline or
     * on the worker pool — the streams make them identical) applied
     * IN ORDER on the tick lane. Public: the lane's equivalence test
     * drives the same picks through both paths.
     */
    public void applyPicks(java.util.List<long[]> picks) {
        for (long[] cell : picks) {
            randomTick(new BlockPosition((int) cell[0], (int) cell[1], (int) cell[2]));
        }
    }

    /**
     * The per-cell rule the per-section sampling drives. Public: the growth
     * acceptance tests probe named cells deterministically (the sampler's
     * 3-of-4096 odds make seeded end-to-end assertions flaky).
     */
    public void randomTick(BlockPosition position) {
        BlockType type = world.getBlock(position);
        // The lit redstone ore's revert (Slice 33 — RedstoneOreBlock.tick
        // lines 59-63 over the default Block.randomTick delegation lines
        // 434-436): the LIT variant is setTicksRandomly(true), so the
        // random tick carries it back to the plain ore (no scheduled
        // delay in the reference — the glow's duration IS the random-tick
        // band).
        if (type.identifier().equals(BuiltinBlocks.LIT_REDSTONE_ORE.identifier())) {
            world.setBlock(position, BuiltinBlocks.REDSTONE_ORE);
            return;
        }
        if (WorldSolidity.isCropStage(type.identifier())) {
            tickCropGrowth(position, type);
            return;
        }
        if (WorldSolidity.isSugarCane(type.identifier())) {
            tickCaneGrowth(position);
            return;
        }
        if (net.zaminmc.torch.server.block.CocoaBlocks.isCocoa(type)) {
            // The cocoa's growth (Slice 43 — CocoaBlock.tick lines 44-50:
            // the 1-in-5 AGE advance to 2; the support break rides the
            // neighbor bus like every mounted family).
            if (random.nextInt(5) == 0) {
                int age = CocoaBlocks.ageOf(type);
                if (age < 2) {
                    world.setBlock(position, CocoaBlocks.withAge(type, age + 1));
                }
            }
            return;
        }
        if (WorldSolidity.isCactus(type.identifier())) {
            tickCactusGrowth(position);
            return;
        }
        if (type.identifier().equals(BuiltinBlocks.FARMLAND.identifier())
                || type.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier())) {
            tickFarmlandHydration(position, type);
            return;
        }
        if (type.identifier().equals(BuiltinBlocks.MYCELIUM.identifier())) {
            // The mycelium's own walk (Slice 42 — MyceliumBlock.tick lines
            // 44-67, 1:1): the decay under darkness (brightness < 4 with
            // an opaque lid) and the four-attempt dirt conversion at
            // brightness >= 9 with the target's >= 4 gate.
            if (lightAt(position.offset(0, 1, 0)) < 4
                    && BlockLightTable.filter(world.getBlock(
                            position.offset(0, 1, 0))) > 2) {
                world.setBlock(position, BuiltinBlocks.DIRT);
                return;
            }
            if (lightAt(position.offset(0, 1, 0)) >= 9) {
                for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
                    BlockPosition target = position.offset(
                            random.nextInt(3) - 1,
                            random.nextInt(5) - 3,
                            random.nextInt(3) - 1);
                    if (target.y() < BlockPosition.MIN_Y || target.y() > BlockPosition.MAX_Y) {
                        continue;
                    }
                    if (!world.getBlock(target).identifier().toString().equals("minecraft:dirt")) {
                        continue;
                    }
                    if (lightAt(target.offset(0, 1, 0)) < 4
                            || BlockLightTable.filter(world.getBlock(
                                    target.offset(0, 1, 0))) > 2) {
                        continue;
                    }
                    world.setBlock(target, BuiltinBlocks.MYCELIUM);
                }
            }
            return;
        }
        if (!type.identifier().toString().equals("minecraft:grass_block")) {
            return;
        }
        // The historical light gates, sampled at the cell ABOVE (vanilla
        // getLightFromNeighborsFor(pos.up()) — opaque cells store no light,
        // so the gates read the air that would host the sapling/grass top).
        if (lightAt(position.offset(0, 1, 0)) < 4) {
            world.setBlock(position, BuiltinBlocks.DIRT); // the historical decay
            return;
        }
        for (int attempt = 0; attempt < SPREAD_ATTEMPTS; attempt++) {
            BlockPosition target = position.offset(
                    random.nextInt(3) - 1,
                    random.nextInt(5) - 3,
                    random.nextInt(3) - 1);
            if (target.y() < BlockPosition.MIN_Y || target.y() > BlockPosition.MAX_Y) {
                continue;
            }
            if (!world.getBlock(target).identifier().toString().equals("minecraft:dirt")) {
                continue;
            }
            if (lightAt(target.offset(0, 1, 0)) < 9) {
                continue;
            }
            world.setBlock(target, BuiltinBlocks.GRASS_BLOCK);
        }
    }

    /** The historical grass light: the brighter of block light and skylight at the cell. */
    private int lightAt(BlockPosition position) {
        return Math.max(world.blockLightAt(position), world.skyLightAt(position));
    }

    /**
     * The historical BlockCrops growth (Slice 12b generalized to the whole
     * family — the wheat, the carrots and the potatoes all extend the
     * reference's BlockCrops with the same 0..7 age band): a randomly-ticked
     * stage advances one age when the light above is at least 9, twice as
     * likely over hydrated farmland.
     */
    private void tickCropGrowth(BlockPosition position, BlockType type) {
        if (lightAt(position.offset(0, 1, 0)) < 9) {
            return;
        }
        int stage = WorldSolidity.cropStageOf(type.identifier());
        if (stage >= 7) {
            // The stems' fruit arm (Slice 41 — StemBlock.tick lines 60-84):
            // a fully-grown stem tries to spawn its fruit beside it (the
            // engine's crop model carries the same random-tick gate).
            String stemFamily = MelonBlocks.stemFamilyOf(type.identifier());
            if (stemFamily != null) {
                MelonBlocks.trySpawnFruit(world, position, stemFamily, random);
            }
            return; // fully grown: nothing to advance
        }
        BlockType soil = world.getBlock(position.offset(0, -1, 0));
        boolean hydrated = soil.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier());
        if (random.nextInt(hydrated ? 3 : 6) == 0) {
            world.setBlock(position, cropStage(type.identifier(), stage + 1));
        }
    }

    /** @return the wheat stage (0..7) at the position, -1 when not a crop. */
    public static int wheatStageAt(net.zaminmc.torch.server.world.EngineWorld world, BlockPosition position) {
        BlockType type = world.getBlock(position);
        return WorldSolidity.isWheatCrop(type.identifier())
                ? type.identifier().value().charAt("wheat_stage".length()) - '0'
                : -1;
    }

    private int wheatStageAt(BlockPosition position) {
        return wheatStageAt(world, position);
    }

    /** The block type of the crop family's stage 0..7 (the family of the source identifier). */
    public static net.zaminmc.torch.block.BlockType cropStage(net.zaminmc.torch.util.Identifier cropId, int stage) {
        String family = WorldSolidity.cropFamilyOf(cropId);
        if (family == null) {
            family = "wheat";
        }
        return switch (family) {
            case "carrots" -> carrotStage(stage);
            case "potatoes" -> potatoStage(stage);
            case "pumpkin_stem" -> MelonBlocks.stemStage("pumpkin_stem", stage);
            case "melon_stem" -> MelonBlocks.stemStage("melon_stem", stage);
            default -> wheatStage(stage);
        };
    }

    /** The block type of wheat stage 0..7. */
    public static net.zaminmc.torch.block.BlockType wheatStage(int stage) {
        return switch (stage) {
            case 0 -> BuiltinBlocks.WHEAT_STAGE0;
            case 1 -> BuiltinBlocks.WHEAT_STAGE1;
            case 2 -> BuiltinBlocks.WHEAT_STAGE2;
            case 3 -> BuiltinBlocks.WHEAT_STAGE3;
            case 4 -> BuiltinBlocks.WHEAT_STAGE4;
            case 5 -> BuiltinBlocks.WHEAT_STAGE5;
            case 6 -> BuiltinBlocks.WHEAT_STAGE6;
            default -> BuiltinBlocks.WHEAT_STAGE7;
        };
    }

    /** The block type of a carrot stage 0..7. */
    public static net.zaminmc.torch.block.BlockType carrotStage(int stage) {
        return switch (stage) {
            case 0 -> BuiltinBlocks.CARROTS_STAGE0;
            case 1 -> BuiltinBlocks.CARROTS_STAGE1;
            case 2 -> BuiltinBlocks.CARROTS_STAGE2;
            case 3 -> BuiltinBlocks.CARROTS_STAGE3;
            case 4 -> BuiltinBlocks.CARROTS_STAGE4;
            case 5 -> BuiltinBlocks.CARROTS_STAGE5;
            case 6 -> BuiltinBlocks.CARROTS_STAGE6;
            default -> BuiltinBlocks.CARROTS_STAGE7;
        };
    }

    /** The block type of a potato stage 0..7. */
    public static net.zaminmc.torch.block.BlockType potatoStage(int stage) {
        return switch (stage) {
            case 0 -> BuiltinBlocks.POTATOES_STAGE0;
            case 1 -> BuiltinBlocks.POTATOES_STAGE1;
            case 2 -> BuiltinBlocks.POTATOES_STAGE2;
            case 3 -> BuiltinBlocks.POTATOES_STAGE3;
            case 4 -> BuiltinBlocks.POTATOES_STAGE4;
            case 5 -> BuiltinBlocks.POTATOES_STAGE5;
            case 6 -> BuiltinBlocks.POTATOES_STAGE6;
            default -> BuiltinBlocks.POTATOES_STAGE7;
        };
    }

    /**
     * The reed's growth (the historical BlockReed shape): only the top of a
     * column under three grows, on a wet base (water beside the soil), at
     * the same 1-in-3 pace hydrated crops use.
     */
    private void tickCaneGrowth(BlockPosition position) {
        if (!world.getBlock(position.offset(0, 1, 0)).equals(world.airType())) {
            return; // something is on top: only the topmost reed grows
        }
        int height = reedColumnHeight(position);
        if (height >= 3) {
            return; // the historical three-block cap
        }
        BlockPosition base = position.offset(0, -(height - 1), 0);
        BlockType soil = world.getBlock(base.offset(0, -1, 0));
        boolean supported = soil.identifier().equals(BuiltinBlocks.GRASS_BLOCK.identifier())
                || soil.identifier().equals(BuiltinBlocks.DIRT.identifier())
                || soil.identifier().equals(BuiltinBlocks.SAND.identifier());
        if (!supported || !waterBeside(base.offset(0, -1, 0))) {
            return; // the base lost its wet soil: no growth (the support rule breaks it)
        }
        if (random.nextInt(3) == 0) {
            world.setBlock(position.offset(0, 1, 0), BuiltinBlocks.SUGAR_CANE);
        }
    }

    /** @return the reed column height counting down from (and including) the ticked block. */
    private int reedColumnHeight(BlockPosition position) {
        int height = 1;
        BlockPosition cursor = position;
        while (height < 3
                && WorldSolidity.isSugarCane(world.getBlock(cursor.offset(0, -1, 0)).identifier())) {
            cursor = cursor.offset(0, -1, 0);
            height++;
        }
        return height;
    }

    /** @return whether any horizontal neighbor of the soil cell holds a fluid. */
    private boolean waterBeside(BlockPosition soil) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockType side = world.getBlock(soil.offset(dir[0], 0, dir[1]));
            if (FluidBlocks.isFluid(side.identifier())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cactus's growth (the historical BlockCactus shape): only the top
     * of a column under three on sand grows, and never beside a solid block
     * (the same rule the neighbor update enforces).
     */
    private void tickCactusGrowth(BlockPosition position) {
        if (!world.getBlock(position.offset(0, 1, 0)).equals(world.airType())) {
            return;
        }
        int height = 1;
        BlockPosition cursor = position;
        while (height < 3
                && WorldSolidity.isCactus(world.getBlock(cursor.offset(0, -1, 0)).identifier())) {
            cursor = cursor.offset(0, -1, 0);
            height++;
        }
        if (height >= 3) {
            return;
        }
        BlockType soil = world.getBlock(cursor.offset(0, -1, 0));
        if (!soil.identifier().equals(BuiltinBlocks.SAND.identifier())
                && !WorldSolidity.isCactus(soil.identifier())) {
            return;
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (WorldSolidity.isSolid(world.getBlock(position.offset(dir[0], 0, dir[1])))) {
                return; // a solid neighbor blocks the growth (the historical rule)
            }
        }
        if (random.nextInt(3) == 0) {
            world.setBlock(position.offset(0, 1, 0), BuiltinBlocks.CACTUS);
        }
    }

    /**
     * The historical BlockFarmland hydration refresh: farmland with water
     * within four blocks (the same row and one above/below, the historical
     * scan box) turns wet; wet farmland without water dries back out.
     */
    private void tickFarmlandHydration(BlockPosition position, BlockType type) {
        boolean wet = type.identifier().equals(BuiltinBlocks.FARMLAND_WET.identifier());
        boolean waterNear = waterWithinFour(position);
        if (waterNear && !wet) {
            world.setBlock(position, BuiltinBlocks.FARMLAND_WET);
        } else if (!waterNear && wet) {
            world.setBlock(position, BuiltinBlocks.FARMLAND);
        }
    }

    /** The historical hydration scan: dx in [-4,4], dz in [-4,4], dy in [-1,1]. */
    private boolean waterWithinFour(BlockPosition position) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    int y = position.y() + dy;
                    if (y < BlockPosition.MIN_Y || y > BlockPosition.MAX_Y) {
                        continue;
                    }
                    BlockType at = world.getBlock(new BlockPosition(
                            position.x() + dx, y, position.z() + dz));
                    if (FluidBlocks.isFluid(at.identifier())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean withinPlayerRange(ChunkPosition chunk, Iterable<PlayerSession> players) {
        double centerX = chunk.x() * 16 + 8.0;
        double centerZ = chunk.z() * 16 + 8.0;
        boolean anyPlaying = false;
        for (PlayerSession player : players) {
            if (player.state() != net.zaminmc.torch.entity.PlayerState.PLAYING) {
                continue;
            }
            anyPlaying = true;
            double dx = player.position().x() - centerX;
            double dz = player.position().z() - centerZ;
            if (dx * dx + dz * dz <= PLAYER_RANGE * PLAYER_RANGE) {
                return true;
            }
        }
        // Nobody watching: the gate collapses and every loaded chunk ticks.
        // The historical rule exists to bound work at scale, not for semantics;
        // the slice-scale cost of an idle world is trivial and the world stays
        // alive for the next visitor. Revisit with the chunk-lifecycle slice.
        return !anyPlaying;
    }
}
