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

    /**
     * The liquid family's random-tick arm (the Wave VI fluids migration):
     * the flowing forms' whole flow tick and the lava source's fire-start,
     * dispatched through the fluid system's own tick (the reference's
     * randomTick → tick delegation). Null until wired.
     */
    private volatile java.util.function.BiConsumer<BlockPosition, BlockType> liquidTicker;

    /** The leaf-decay drops' seams (the VIII3 slice): the item bodies the
     *  decayed cells' drops land through, and the drop walk itself. */
    private volatile net.zaminmc.torch.server.entity.ItemEntityManager leafDecayItems;
    private volatile net.zaminmc.torch.server.interaction.DropService leafDecayDrops;

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

    /** Wires the liquid family's random-tick arm (the boot wiring — the fluid system's own dispatch). */
    public void setLiquidTicker(java.util.function.BiConsumer<BlockPosition, BlockType> liquidTicker) {
        this.liquidTicker = java.util.Objects.requireNonNull(liquidTicker, "liquidTicker");
    }

    /**
     * The nether portal's pigman spawn consult (Slice TF-7e — the
     * reference's PortalBlock.tick whole walk, dispatched through the
     * engine's own wiring): the doMobSpawning rule, the
     * {@code nextInt(2000) < difficulty} roll, the ground search below
     * the portal, and the pigman's own spawn at the found ground. The
     * sink owns the world reads and the spawn; the system's own random
     * drives the roll (the reference's {@code random} parameter — the
     * same random-tick walk's own stream).
     */
    public interface PortalPigmanSink {
        /**
         * Runs the spawn walk for the portal cell.
         *
         * @return true when a pigman landed (the test surface's read)
         */
        boolean trySpawnAtPortal(BlockPosition portal, Random random);
    }

    /** The portal pigman's spawn consult (null until the boot wires it). */
    private volatile PortalPigmanSink portalPigmanSink;

    /** Wires the portal pigman's spawn consult (the boot wiring). */
    public void setPortalPigmanSink(PortalPigmanSink sink) {
        this.portalPigmanSink = java.util.Objects.requireNonNull(sink, "sink");
    }

    /** Wires the leaf-decay drop seams (EngineServer's own bodies — the
     *  decayed cells' sapling/apple rolls land through the engine's own
     *  walk, the same one a hand break rolls). */
    public void setLeafDecayDrops(net.zaminmc.torch.server.entity.ItemEntityManager itemEntities,
                                  net.zaminmc.torch.server.interaction.DropService drops) {
        this.leafDecayItems = java.util.Objects.requireNonNull(itemEntities, "itemEntities");
        this.leafDecayDrops = java.util.Objects.requireNonNull(drops, "drops");
    }

    /**
     * The plant-pop drop seam (the farming growth pass): the body the
     * random-tick support consults' pops land through — the same item
     * manager the neighbour bus's plant-pop arms use, wired at the same
     * boot site (the cane's water-loss pop and the cactus's new-cell pop
     * ride it; null keeps the pops drop-less, the direct-driven seats'
     * own shape).
     */
    private volatile net.zaminmc.torch.server.entity.ItemEntityManager popItems;

    /** Wires the plant-pop drop seam (the boot wiring — EngineServer's own item bodies). */
    public void setPlantPopItems(net.zaminmc.torch.server.entity.ItemEntityManager itemEntities) {
        this.popItems = java.util.Objects.requireNonNull(itemEntities, "itemEntities");
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
        // The liquid family's random ticks (LiquidBlock's own constructor
        // band — setTicksRandomly(true) for every liquid, the source
        // block's false-then-lava-true override; the reference's
        // randomTick default delegates to the tick body, Block lines
        // 434-436): the flowing forms re-run the whole flow tick, the
        // lava source/still forms run the fire-start (the water source
        // never ticks — LiquidSourceBlock's own water arm).
        if (FluidBlocks.isFluid(type.identifier())) {
            if (FluidBlocks.formOf(type.identifier()) == FluidBlocks.Form.FLOWING
                    || FluidBlocks.kindOf(type.identifier()) == FluidBlocks.Kind.LAVA) {
                java.util.function.BiConsumer<BlockPosition, BlockType> arm = liquidTicker;
                if (arm != null) {
                    arm.accept(position, type);
                }
            }
            return;
        }
        // The nether portal's pigman spawn (Slice TF-7e — PortalBlock.tick
        // lines 34-53 over the reference's randomTick → tick delegation):
        // the overworld's own system (the nether carries no random-tick
        // family — the isNatural gate inherent), the whole spawn walk
        // dispatched through the engine's consult.
        if (WorldSolidity.isNetherPortal(type.identifier())) {
            PortalPigmanSink sink = portalPigmanSink;
            if (sink != null) {
                sink.trySpawnAtPortal(position, random);
            }
            return;
        }
        // The tree-growth family (the VIII2/VIII3 slice — the reference's
        // randomTick → tick delegation, Block lines 434-436): the leaves'
        // decay poll (the CHECK_DECAY && DECAYABLE gate owns the BFS) and
        // the saplings' grow roll (the light gate + the 1-in-7).
        if (LeafDecayBlocks.isLeafState(type.identifier())) {
            net.zaminmc.torch.server.entity.ItemEntityManager items = leafDecayItems;
            net.zaminmc.torch.server.interaction.DropService drops = leafDecayDrops;
            if (items != null && drops != null) {
                LeafDecayBlocks.decayTick(world, position, type, random, items, drops);
            }
            return;
        }
        if (WoodVariantBlocks.isSapling(type.identifier())) {
            SaplingBlocks.randomTick(world, position, type, random);
            return;
        }
        // The mushroom pair's spread (VIII8 — MushroomPlantBlock.tick lines
        // 43-68, the reference's randomTick → tick delegation): the 1-in-25
        // pick, the 9x3x9 same-family density gate, and the four-step walk
        // that plants a copy on surviving ground.
        if (MushroomBlocks.isMushroomPlant(type)) {
            MushroomBlocks.spreadTick(world, position, type, random);
            return;
        }
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
     * stage advances one age when the light above is at least 9, at the
     * reference's own {@code 25 / growthSpeed} cadence (WheatBlock.tick
     * lines 34-45: {@code random.nextInt((int)(25.0F / f) + 1) == 0}).
     *
     * <p>THE F SPEED (the reference's {@code getGrowthSpeed} lines 56-98,
     * ported whole — the pre-audit engine's flat {@code nextInt(3)} wet /
     * {@code nextInt(6)} dry pair grew 2.33x/2.17x the reference's pace and
     * skipped both the neighbour-ring acceleration and the row half): the
     * 3x3 soil ring under the crop contributes {@code +1.0} per farmland
     * cell — {@code +3.0} when its moisture is above zero (the engine's
     * {@code farmland_wet} pair, the documented 0..7 flattening) — with the
     * eight non-center cells divided by 4; a crop ringed by eight wet
     * farmlands grows at 1-in-3 where a lone wet crop takes 1-in-7 and a
     * dry one 1-in-13. The same-crop row pattern then halves {@code f}: a
     * crop with its own kind both north/south and east/west, or in the four
     * corners of that cross (lines 79-95). THE STEM ROLL GATE
     * (StemBlock.tick lines 57-82): a mature stem attempts its fruit walk
     * ONLY on a passing roll — the pre-audit engine attempted it on every
     * random tick, ~7x the reference's fruit cadence.</p>
     */
    private void tickCropGrowth(BlockPosition position, BlockType type) {
        if (lightAt(position.offset(0, 1, 0)) < 9) {
            return;
        }
        float f = cropGrowthSpeed(world, position, type.identifier());
        if (random.nextInt((int) (25.0f / f) + 1) != 0) {
            return; // the failed roll: no age advance, no fruit attempt
        }
        int stage = WorldSolidity.cropStageOf(type.identifier());
        if (stage >= 7) {
            // The stems' fruit arm (Slice 41 — StemBlock.tick lines 66-79):
            // a passing roll reaches the walk; the engine's crop model
            // carries the same random-tick gate. The walk itself is 1:1
            // (MelonBlocks.trySpawnFruit — the existing-fruit consult, one
            // random horizontal, air over farmland/dirt/grass).
            String stemFamily = MelonBlocks.stemFamilyOf(type.identifier());
            if (stemFamily != null) {
                MelonBlocks.trySpawnFruit(world, position, stemFamily, random);
            }
            return; // fully grown: nothing to advance
        }
        world.setBlock(position, cropStage(type.identifier(), stage + 1));
    }

    /**
     * The reference's growth-speed consult (WheatBlock.getGrowthSpeed
     * lines 56-98), 1:1: the 3x3 soil ring's farmland contributions with the
     * wet moisture bonus ({@code MOISTURE > 0}, line 66 — any of the six
     * drying levels grows at the wet pace in the reference; the engine's
     * binary farmland answers the pair through its {@code farmland_wet}
     * form) and the non-center /4, then the same-crop row halving
     * (lines 79-95 — the neighbour consult compares the crop's OWN kind:
     * wheat against wheat, a melon stem against a melon stem, never the
     * cross pair).
     *
     * <p>Package-private static: the parity seats pin the computed speed
     * and its derived roll band for every farmland shape without sampling
     * randomness.</p>
     */
    static float cropGrowthSpeed(net.zaminmc.torch.server.world.EngineWorld world,
            BlockPosition position, net.zaminmc.torch.util.Identifier cropId) {
        float f = 1.0f;
        BlockPosition soilRow = position.offset(0, -1, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float contribution = 0.0f;
                String soil = world.getBlock(soilRow.offset(dx, 0, dz)).identifier().value();
                if (soil.equals("farmland") || soil.equals("farmland_wet")) {
                    contribution = 1.0f;
                    if (soil.equals("farmland_wet")) {
                        contribution = 3.0f; // MOISTURE > 0 (line 66)
                    }
                }
                if (dx != 0 || dz != 0) {
                    contribution /= 4.0f;
                }
                f += contribution;
            }
        }
        // The row half (lines 79-95): the same-crop pair on both axes, or
        // the same crop in one of the four corner cells of that cross.
        boolean sameX = isSameCrop(world, position.offset(-1, 0, 0), cropId)
                || isSameCrop(world, position.offset(1, 0, 0), cropId);
        boolean sameZ = isSameCrop(world, position.offset(0, 0, -1), cropId)
                || isSameCrop(world, position.offset(0, 0, 1), cropId);
        if (sameX && sameZ) {
            f /= 2.0f;
        } else if (isSameCrop(world, position.offset(-1, 0, -1), cropId)
                || isSameCrop(world, position.offset(1, 0, -1), cropId)
                || isSameCrop(world, position.offset(1, 0, 1), cropId)
                || isSameCrop(world, position.offset(-1, 0, 1), cropId)) {
            f /= 2.0f;
        }
        return f;
    }

    /**
     * @return whether the crop at the position is the SAME kind as the
     *         reference's {@code block == this} consult (the family match:
     *         wheat against wheat, a melon stem against a melon stem).
     */
    private static boolean isSameCrop(net.zaminmc.torch.server.world.EngineWorld world,
            BlockPosition position, net.zaminmc.torch.util.Identifier cropId) {
        net.zaminmc.torch.util.Identifier other = world.getBlock(position).identifier();
        if (!other.namespace().equals(cropId.namespace())) {
            return false;
        }
        String mine = cropId.value();
        String theirs = other.value();
        // The engine's crop families: wheat_stage*, carrots_stage*,
        // potatoes_stage*, and the stems' *_stem_stage* — the stage suffix
        // stripped, the family compared (the reference's own class
        // identity: melon_stem never matches pumpkin_stem).
        int mineCut = mine.lastIndexOf("_stage");
        int theirsCut = theirs.lastIndexOf("_stage");
        if (mineCut < 0 || theirsCut < 0) {
            return false;
        }
        return mine.substring(0, mineCut).equals(theirs.substring(0, theirsCut));
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
     * The reed's growth (the reference's SugarCaneBlock.tick lines 28-49,
     * 1:1 — the pre-audit engine grew at a flat 1-in-3 where the reference
     * rides the AGE 0-15 accumulator's 1-in-16 band, and re-consulted the
     * BASE's water for every stacked block where the reference's stacked
     * canes short-circuit at line 30):
     *
     * <p>THE SUPPORT CONSULT (line 30): a cane standing on another cane
     * skips it; the BASE re-validates itself — and this random-tick
     * consult is the law that pops a cane whose water departed, because
     * the water cell is DIAGONAL to the cane (never a neighbour-bus
     * notification): the failed consult DROPS the block with its item and
     * removes it (canSurviveOrBreak lines 76-84), felling the column as
     * the bus carries each upper cane's own departure. A stacked cane
     * over a base whose water left still GROWS until the base's own tick
     * pops it — the reference's own short-circuit order.</p>
     *
     * <p>THE CADENCE: only a block with air above accumulates; the column
     * (the ticked block and every cane below it) must be under three; the
     * AGE accumulator's 16-tick prelude is the growth time vanilla
     * players know ({@code nextInt(16) == 0} — statistically the
     * accumulator itself; the AGE nibble is client-invisible and rides
     * the documented single-type flattening).</p>
     */
    private void tickCaneGrowth(BlockPosition position) {
        // Line 30: the stacked short-circuit; the base's own consult pops
        // on failure (the drop rides the family's pop seam).
        if (!WorldSolidity.isSugarCane(
                world.getBlock(position.offset(0, -1, 0)).identifier())) {
            if (!canCaneSurviveOrPop(position)) {
                return; // dropped and removed by the consult
            }
        }
        if (!world.getBlock(position.offset(0, 1, 0)).equals(world.airType())) {
            return; // something is on top: only the topmost reed accumulates
        }
        int column = 1; // the ticked block itself
        BlockPosition cursor = position;
        while (WorldSolidity.isSugarCane(
                world.getBlock(cursor.offset(0, -1, 0)).identifier())) {
            column++;
            cursor = cursor.offset(0, -1, 0);
        }
        if (column >= 3) {
            return; // the historical three-block cap (line 38's i < 3)
        }
        if (random.nextInt(16) == 0) { // the AGE-15 accumulator's band
            world.setBlock(position.offset(0, 1, 0), BuiltinBlocks.SUGAR_CANE);
        }
    }

    /**
     * The base cane's own support consult (SugarCaneBlock.canBePlaced
     * lines 52-69 over canSurviveOrBreak lines 76-84): the soil below is
     * grass, dirt or sand, and WATER (the WATER fluid kind alone —
     * Material.WATER at line 63, LAVA never hydrates a reed; the
     * pre-audit engine's isFluid consult admitted lava) sits beside the
     * soil cell. A failure drops the block with its item and removes it.
     */
    private boolean canCaneSurviveOrPop(BlockPosition position) {
        BlockType soil = world.getBlock(position.offset(0, -1, 0));
        String soilName = soil.identifier().value();
        boolean soilOk = soilName.equals("grass_block")
                || soilName.equals("dirt") || soilName.equals("sand");
        if (soilOk && waterBeside(position.offset(0, -1, 0))) {
            return true;
        }
        world.setBlock(position, world.airType());
        if (popItems != null) {
            popItems.spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(position.x(), position.y(), position.z()),
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.SUGAR_CANE, 1),
                    net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }
        return false;
    }

    /**
     * @return whether any horizontal neighbour of the SOIL cell below the
     *         cane carries the WATER fluid kind (the reference's
     *         {@code pos.offset(direction).down()} walk, Material.WATER
     *         alone — SugarCaneBlock lines 62-68).
     */
    private boolean waterBeside(BlockPosition soil) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            BlockType side = world.getBlock(soil.offset(dir[0], 0, dir[1]));
            if (FluidBlocks.kindOf(side.identifier()) == FluidBlocks.Kind.WATER) {
                return true;
            }
        }
        return false;
    }

    /**
     * The cactus's growth (the reference's CactusBlock.tick lines 28-49,
     * 1:1 — the pre-audit engine's flat 1-in-3 was 5.33x the AGE-15
     * accumulator's band, and its consult read the CURRENT top's
     * horizontals where the reference consults the NEW cell's):
     *
     * <p>THE CADENCE: only a block with air above accumulates, the column
     * under three grows, the AGE accumulator's band is
     * {@code nextInt(16) == 0}.</p>
     *
     * <p>THE NEW-CELL CONSULT (line 43): the spawn places the new block
     * with no neighbour fanout (the reference's flag 4) and THEN runs the
     * new cell's own support consult — {@code canSurvive} lines 85-94: the
     * cell below is cactus or sand, no material-solid block beside it. A
     * solid beside the new cell pops it WITH its item, same tick — the
     * engine's setBlock fans the placement out one extra wake (the
     * engine's own bus shape, ledgered) before the pop consult runs.
     * The solidity model at this site is the engine's standing
     * {@link WorldSolidity#isSolid} (the material-model unification
     * rides the sign family's own class when that pass merges — the
     * seat pins the members where the two models agree).</p>
     */
    private void tickCactusGrowth(BlockPosition position) {
        BlockPosition above = position.offset(0, 1, 0);
        if (!world.getBlock(above).equals(world.airType())) {
            return; // only the topmost cactus accumulates (line 30)
        }
        int column = 1; // the ticked block itself
        BlockPosition cursor = position;
        while (WorldSolidity.isCactus(world.getBlock(cursor.offset(0, -1, 0)).identifier())) {
            column++;
            cursor = cursor.offset(0, -1, 0);
        }
        if (column >= 3) {
            return; // the three-block cap (line 37's i < 3)
        }
        if (random.nextInt(16) != 0) { // the AGE-15 accumulator's band
            return;
        }
        world.setBlock(above, BuiltinBlocks.CACTUS);
        // Line 43: the new cell's own consult, after the placement — a
        // solid beside the new cell pops it WITH its drop, same tick.
        boolean supported = WorldSolidity.isCactus(world.getBlock(above.offset(0, -1, 0)).identifier())
                || world.getBlock(above.offset(0, -1, 0)).identifier().value().equals("sand")
                || world.getBlock(above.offset(0, -1, 0)).identifier().value().equals("red_sand");
        if (!supported) {
            popCactusAt(above);
            return;
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (WorldSolidity.isSolid(world.getBlock(above.offset(dir[0], 0, dir[1])))) {
                popCactusAt(above); // canSurvive lines 85-94, neighborChanged line 79-83
                return;
            }
        }
    }

    /** The pop the failed consult carries (breakBlock's drop, the family's own pop law). */
    private void popCactusAt(BlockPosition position) {
        world.setBlock(position, world.airType());
        if (popItems != null) {
            popItems.spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(position.x(), position.y(), position.z()),
                    net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.CACTUS, 1),
                    net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS);
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
