package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.PortalFrameBuilder;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The fire family 1:1 (reference/1.8.8 net/minecraft/block/FireBlock.java):
 * the AGE band, the per-block flammability/burn tables, and the scheduled
 * block-tick arm — the first family migrated from the block-update system's
 * own adapted fire model onto the reference's {@link ScheduledTickSystem}
 * surface (the reference's ServerWorld.tickUpdates queue every fire ride).
 *
 * <p><b>The AGE band</b> (FireBlock.AGE 0-15, {@code
 * getStateFromMetadata}/{@code getMetadataFromState} lines 402-409 — the
 * metadata nibble IS the age): the engine's flattened state model carries
 * the band as derived identifiers — {@code minecraft:fire} IS the age-0
 * state (the placed flame), {@code minecraft:fire_age1} .. {@code _age15}
 * the rest, each {@link EngineBlockType} with the age whole in its legacy
 * metadata nibble (wire 51:meta). Every fire-membership read in the engine
 * goes through {@link #isFire} — the whole band, never the bare id.</p>
 *
 * <p><b>The flammability/burn tables</b> (FireBlock.init lines 75-111, the
 * exact per-block rows): flammability drives the SPREAD chance
 * ({@link #getSpreadChance} — the {@code getFlammability} side), burnChance
 * drives the DESTROY chance ({@link #burnNeighbor} — the {@code
 * getBurnChance} side). The engine's earlier flat flammable set (planks,
 * logs, leaves and the wooden furniture — the block-update system's adapted
 * model) is retired by this class: the reference's rows are keyed per
 * BLOCK, so in the flattened engine each family's every registered state
 * carries the family row (the same rule the reference's one-Block-per-row
 * table gives every metadata state of that block).</p>
 *
 * <p><b>The tick</b> (FireBlock.tick lines 144-226, 1:1): the doFireTick
 * gate wraps EVERYTHING including the re-schedule — a false rule freezes
 * the flame inert after its pending entry pops (a new placement re-arms
 * it once, the reference's own onAdded path). The age progression
 * {@code i + random.nextInt(3) / 2} (0 or 1 per tick under 15); the
 * re-arm {@code getTickRate() + random.nextInt(10)} = 30-39; the eternal
 * netherrack/End-bedrock flame; the rain douse over the open sky; the
 * no-fuel death ({@code i > 3} on a bare floor); the max-age 1/4 roll;
 * the six burnNeighbor walks (down/up 250, sides 300); the 3x3x6 spread
 * box with the {@code j1} distance ladder and the difficulty term; and
 * the TNT prime (burnNeighbor's {@code Blocks.TNT.onBroken(EXPLODE)} arm —
 * the primed body at the full 80-tick fuse, the burn arm itself already
 * replaced the cell).</p>
 *
 * <p><b>Named envelopes</b> (the engine surfaces the reference reads that
 * do not exist yet): {@code World.isHumid} (biome-based; the engine has no
 * biome surface — the humid arm answers false, the common case: no -50
 * burn discount, no spread halving); {@code World.isRaining(pos)} (the
 * per-position rain — the engine models the open-sky column walk, no
 * biome/precipitation-height surface); {@code World.hasSolidTop} (the
 * material-solid-cube common case through {@link WorldSolidity#isSolid},
 * plus the stairs/slab top halves, the hopper — the engine's snow layer
 * is the single 1-layer state, so the 7-layer arm never fires); {@code
 * setBlockState(pos, state, 4)} (the engine's commit path always
 * publishes — the age bump syncs, the reference's no-sync quirk named);
 * {@code NETHER_PORTAL.create} in onAdded (the reference's overworld-only
 * gate — {@link #overworld} carries the dimension check).</p>
 */
public final class FireBlocks {

    /** FireBlock.AGE's band (0-15, the reference's IntegerProperty.of("age", 0, 15)). */
    public static final int MAX_AGE = 15;

    /** The wire id (Block.java's own row — fire 51). */
    public static final int LEGACY_ID = 51;

    private static final String AGE_PREFIX = "minecraft:fire_age";

    private static final Identifier NETHERRACK = Identifier.parse("minecraft:netherrack");
    private static final Identifier BEDROCK = Identifier.parse("minecraft:bedrock");
    private static final Identifier TNT = Identifier.parse("minecraft:tnt");

    // ------------------------------------------------------------------
    // The AGE band
    // ------------------------------------------------------------------

    private static final Map<Identifier, EngineBlockType> TYPES = buildTypes();

    private static Map<Identifier, EngineBlockType> buildTypes() {
        Map<Identifier, EngineBlockType> types = new HashMap<>();
        // minecraft:fire IS the age-0 state (BuiltinBlocks registers it);
        // the band adds the fifteen derived states, the age whole in the
        // legacy metadata nibble (the 1.8 wire's 51:meta).
        for (int age = 1; age <= MAX_AGE; age++) {
            Identifier id = Identifier.parse(AGE_PREFIX + age);
            types.put(id, new EngineBlockType(id, "Fire", age));
        }
        return Map.copyOf(types);
    }

    /** The age state's identifier: age 0 keeps the plain name (the cake-bites convention). */
    public static Identifier stateId(int age) {
        return age <= 0
                ? Identifier.parse("minecraft:fire")
                : Identifier.parse(AGE_PREFIX + Math.min(age, MAX_AGE));
    }

    /** The age state's type (the registered band member). */
    public static EngineBlockType stateOf(int age) {
        if (age <= 0) {
            return net.zaminmc.torch.server.block.BuiltinBlocks.FIRE;
        }
        return TYPES.get(stateId(age));
    }

    /** The fifteen derived states (registration; BuiltinBlocks owns the age-0 member). */
    public static List<EngineBlockType> familyBlocks() {
        List<EngineBlockType> blocks = new ArrayList<>();
        for (int age = 1; age <= MAX_AGE; age++) {
            blocks.add(TYPES.get(stateId(age)));
        }
        return List.copyOf(blocks);
    }

    /** Registers the derived states into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : TYPES.values()) {
            builder.register(type);
        }
        return builder;
    }

    /** @return whether the identifier is any of the sixteen fire states. */
    public static boolean isFire(Identifier id) {
        if (id == null) {
            return false;
        }
        if (id.namespace().equals("minecraft")) {
            if (id.value().equals("fire")) {
                return true;
            }
            return id.value().startsWith("fire_age") && ageOf(id) >= 0;
        }
        return false;
    }

    /** @return the state's AGE (0-15), or -1 for a non-member. */
    public static int ageOf(Identifier id) {
        if (id == null || !id.namespace().equals("minecraft")) {
            return -1;
        }
        String value = id.value();
        if (value.equals("fire")) {
            return 0;
        }
        if (!value.startsWith("fire_age")) {
            return -1;
        }
        String digits = value.substring("fire_age".length());
        if (digits.isEmpty() || digits.length() > 2) {
            return -1;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (!Character.isDigit(digits.charAt(i))) {
                return -1;
            }
        }
        int age = Integer.parseInt(digits);
        return age >= 1 && age <= MAX_AGE ? age : -1;
    }

    // ------------------------------------------------------------------
    // The flammability/burn tables (FireBlock.init lines 75-111, 1:1)
    // ------------------------------------------------------------------

    private static final Map<Identifier, int[]> TABLES = buildTables();

    /**
     * Builds the reference's rows. Each entry carries {flammability,
     * burnChance} exactly as {@code setFlammable(block, a, b)} registered
     * them; in the flattened engine the row is enumerated over the family's
     * every registered state (the reference's one row covers every
     * metadata state of the one BLOCK).
     */
    private static Map<Identifier, int[]> buildTables() {
        Map<Identifier, int[]> tables = new HashMap<>();
        // PLANKS 5, 20 — the engine's planks inventory (oak).
        put(tables, "minecraft:oak_planks", 5, 20);
        // DOUBLE_WOODEN_SLAB 5, 20 — the six woods' double band (the oak
        // carries the wire's own double_wooden_slab name).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            put(tables, WoodShapeBlocks.doubleSlabState(variant), 5, 20);
        }
        // WOODEN_SLAB 5, 20 — every wood's single-slab pair (the bottom
        // and the top).
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            for (EngineBlockType slab : WoodShapeBlocks.slabStates(variant)) {
                put(tables, slab.identifier(), 5, 20);
            }
        }
        // FENCE_GATE 5, 20 — every wood's gate (FenceGateBlocks' band).
        for (EngineBlockType gate : FenceGateBlocks.familyBlocks()) {
            put(tables, gate.identifier(), 5, 20);
        }
        // FENCE 5, 20 — the wooden fence family: the oak plus WoodShape
        // blocks' five woods (the nether brick fence is NOT in the
        // reference's table).
        put(tables, "minecraft:oak_fence", 5, 20);
        for (String wood : WoodShapeBlocks.FENCES.keySet()) {
            put(tables, WoodShapeBlocks.fenceState(wood).identifier(), 5, 20);
        }
        // OAK_STAIRS 5, 20 — the reference's own four stair rows: the oak
        // band (BuiltinBlocks' eight states), WoodShape blocks' spruce,
        // birch and jungle pairs. The stone families, the brick, the
        // acacia and the dark oak are NOT in the reference's table —
        // left out exactly as registered.
        for (String state : new String[] {
                "oak_stairs_east", "oak_stairs_west", "oak_stairs_south", "oak_stairs_north",
                "oak_stairs_east_top", "oak_stairs_west_top", "oak_stairs_south_top",
                "oak_stairs_north_top"}) {
            put(tables, "minecraft:" + state, 5, 20);
        }
        for (String family : new String[] {"spruce", "birch", "jungle"}) {
            for (EngineBlockType stair : WoodShapeBlocks.stairsStates(family)) {
                put(tables, stair.identifier(), 5, 20);
            }
            for (EngineBlockType stair : WoodShapeBlocks.stairsTopStates(family)) {
                put(tables, stair.identifier(), 5, 20);
            }
        }
        // LOG 5, 5 + LOG2 5, 5 — every wood's log.
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            put(tables, WoodVariantBlocks.logBlock(variant), 5, 5);
        }
        // LEAVES 30, 60 + LEAVES2 30, 60 — every wood's leaves.
        for (int variant = 0; variant < WoodVariantBlocks.WOODS.size(); variant++) {
            put(tables, WoodVariantBlocks.leavesBlock(variant), 30, 60);
        }
        // BOOKSHELF 30, 20.
        put(tables, "minecraft:bookshelf", 30, 20);
        // TNT 15, 100.
        put(tables, TNT, 15, 100);
        // TALLGRASS 60, 100.
        put(tables, "minecraft:tall_grass", 60, 100);
        // DOUBLE_PLANT 60, 100 — the whole band (the lower and the upper).
        for (EngineBlockType plant : DoublePlantBlocks.familyBlocks()) {
            put(tables, plant.identifier(), 60, 100);
        }
        // YELLOW_FLOWER 60, 100 — the dandelion.
        put(tables, "minecraft:dandelion", 60, 100);
        // RED_FLOWER 60, 100 — the nine-flower band (the poppy through the
        // oxeye daisy); the poppy keeps its shipped plain row outside
        // FlowerBlocks' familyBlocks (the type-0 convention).
        put(tables, "minecraft:poppy", 60, 100);
        for (EngineBlockType flower : FlowerBlocks.familyBlocks()) {
            put(tables, flower.identifier(), 60, 100);
        }
        // DEADBUSH 60, 100.
        put(tables, "minecraft:dead_bush", 60, 100);
        // WOOL 30, 60 — the engine's wool.
        put(tables, "minecraft:wool", 30, 60);
        // VINE 15, 100.
        put(tables, "minecraft:vine", 15, 100);
        // COAL_BLOCK 5, 5.
        put(tables, "minecraft:coal_block", 5, 5);
        // HAY 60, 20.
        put(tables, "minecraft:hay_block", 60, 20);
        // CARPET 60, 20 — the dye family's carpet band.
        for (int color = 0; color < 16; color++) {
            put(tables, DyeFamilyBlocks.carpetBlock(color), 60, 20);
        }
        return Map.copyOf(tables);
    }

    private static void put(Map<Identifier, int[]> tables, String name, int flammability, int burnChance) {
        put(tables, Identifier.parse(name), flammability, burnChance);
    }

    private static void put(Map<Identifier, int[]> tables, Identifier id, int flammability, int burnChance) {
        tables.put(id, new int[] {flammability, burnChance});
    }

    private static void put(Map<Identifier, int[]> tables, EngineBlockType type, int flammability, int burnChance) {
        tables.put(type.identifier(), new int[] {flammability, burnChance});
    }

    /** The reference's getFlammability (lines 246-249): 0 for a non-member. */
    public static int flammabilityOf(Identifier id) {
        int[] row = TABLES.get(id);
        return row == null ? 0 : row[0];
    }

    /** The reference's getBurnChance (lines 241-244): 0 for a non-member. */
    public static int burnChanceOf(Identifier id) {
        int[] row = TABLES.get(id);
        return row == null ? 0 : row[1];
    }

    /** The reference's isFlammable (lines 301-303): flammability > 0. */
    public static boolean isFlammable(Identifier id) {
        return flammabilityOf(id) > 0;
    }

    // ------------------------------------------------------------------
    // The reference's support/rain predicates
    // ------------------------------------------------------------------

    /**
     * The reference's World.hasSolidTop (World.java lines 1740-1750, 1:1
     * over the engine's surfaces): the full-cube common case through
     * {@link WorldSolidity#isSolid} (the engine's own solid predicate —
     * the reference's material.isSolidBlocking + isCube pair has no
     * separate engine form, named), with the partial families carrying
     * their own arms BEFORE it (the reference's StairsBlock/SlabBlock
     * else-chain): the stairs TOP half alone (a bottom stair is not),
     * the single slab's TOP half alone (a double slab IS a full cube —
     * the fallthrough answers it), the hopper, and the never-solid-tops
     * the reference's isCube gate refuses (fences, gates, walls, chests,
     * the 1-layer snow). The 7-layer snow arm never fires: the engine's
     * snow layer is the single 1-layer state.
     */
    public static boolean hasSolidTop(EngineWorld world, BlockPosition pos) {
        BlockType type = world.getBlock(pos);
        Identifier id = type.identifier();
        if (!id.namespace().equals("minecraft")) {
            return false;
        }
        String value = id.value();
        // StairsBlock: the TOP half alone.
        if (value.contains("_stairs_")) {
            return value.endsWith("_top");
        }
        // SlabBlock: the single slab's TOP half alone; a double slab is a
        // full cube (the fallthrough).
        if (value.endsWith("_slab_top")) {
            return true;
        }
        if (value.endsWith("_slab") && !value.startsWith("double_")) {
            return false;
        }
        // HopperBlock — every facing/enabled state (the engine's
        // {@code hopper_*} band).
        if (value.startsWith("hopper_")) {
            return true;
        }
        // The never-solid-tops (the reference's isCube gate): the fences,
        // the gates, the walls, the chests, the 1-layer snow.
        if (value.endsWith("_fence") || value.endsWith("_fence_gate")
                || value.endsWith("_wall") || value.contains("chest")
                || value.equals("snow_layer")) {
            return false;
        }
        // The full-cube common case (stone, dirt, grass, planks, logs,
        // leaves, tnt, netherrack, bedrock — and the doubles above).
        return WorldSolidity.isSolid(type);
    }

    /**
     * The engine's {@code World.isRaining(pos)} envelope: the rain flag
     * AND the open-sky column (any solid block above blocks the rain).
     * The reference's biome/precipitation-height surface has no engine
     * form — the common-case walk (named).
     */
    public static boolean isRainingAt(EngineWorld world, BlockPosition pos) {
        if (!isRaining()) {
            return false;
        }
        for (int y = pos.y() + 1; y <= net.zaminmc.torch.block.BlockPosition.MAX_Y; y++) {
            if (WorldSolidity.isSolid(world.getBlock(new BlockPosition(pos.x(), y, pos.z())))) {
                return false;
            }
        }
        return true;
    }

    /** The reference's shouldExtinguish (lines 228-234): the cell or any horizontal neighbor. */
    public static boolean shouldExtinguish(EngineWorld world, BlockPosition pos) {
        return isRainingAt(world, pos)
                || isRainingAt(world, pos.offset(1, 0, 0))
                || isRainingAt(world, pos.offset(-1, 0, 0))
                || isRainingAt(world, pos.offset(0, 0, -1))
                || isRainingAt(world, pos.offset(0, 0, 1));
    }

    /** The reference's hasFlammableNeighbor (lines 272-280): any of the six. */
    public static boolean hasFlammableNeighbor(EngineWorld world, BlockPosition pos) {
        return isFlammable(world.getBlock(pos.offset(1, 0, 0)).identifier())
                || isFlammable(world.getBlock(pos.offset(-1, 0, 0)).identifier())
                || isFlammable(world.getBlock(pos.offset(0, 1, 0)).identifier())
                || isFlammable(world.getBlock(pos.offset(0, -1, 0)).identifier())
                || isFlammable(world.getBlock(pos.offset(0, 0, 1)).identifier())
                || isFlammable(world.getBlock(pos.offset(0, 0, -1)).identifier());
    }

    /**
     * The reference's canBePlaced (lines 306-308): the solid top below OR
     * a flammable neighbor.
     */
    public static boolean canBePlaced(EngineWorld world, BlockPosition pos) {
        return hasSolidTop(world, pos.offset(0, -1, 0))
                || hasFlammableNeighbor(world, pos);
    }

    /**
     * The reference's getSpreadChance (lines 282-294): air only, then the
     * max flammability of the six neighbors.
     */
    public static int getSpreadChance(EngineWorld world, BlockPosition pos) {
        if (!world.getBlock(pos).identifier().equals(world.airType().identifier())) {
            return 0;
        }
        int max = 0;
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(1, 0, 0)).identifier()), max);
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(-1, 0, 0)).identifier()), max);
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(0, 1, 0)).identifier()), max);
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(0, -1, 0)).identifier()), max);
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(0, 0, 1)).identifier()), max);
        max = Math.max(flammabilityOf(world.getBlock(pos.offset(0, 0, -1)).identifier()), max);
        return max;
    }

    /**
     * The reference's neighborChanged (lines 311-315, 1:1): a fire whose
     * support and fuel both vanished dies immediately on the neighbor
     * update path. Returns whether the fire was removed.
     */
    public static boolean neighborChanged(EngineWorld world, BlockPosition pos) {
        if (!hasSolidTop(world, pos.offset(0, -1, 0)) && !hasFlammableNeighbor(world, pos)) {
            world.setBlock(pos, world.airType());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The seams (wired once at boot; read lazily at tick time)
    // ------------------------------------------------------------------

    /** The rain flag (World.isRaining — the weather system's own state). */
    private static volatile BooleanSupplier raining = () -> false;

    /** The doFireTick game rule (the gate at the top of the reference's tick). */
    private static volatile BooleanSupplier fireTickRule = () -> true;

    /** The runtime difficulty id (0 peaceful .. 3 hard — the spread term). */
    private static volatile IntSupplier difficultyId = () -> 1;

    /**
     * The TNT prime arm (burnNeighbor's onBroken row — the primed body):
     * the acting world rides the prime (Wave FL4-F7 — the reference's
     * {@code ignite(World world, ...)}), so a burned nether TNT primes a
     * body carrying the NETHER's dimension, never the overworld's.
     */
    public interface TntPrimeArm {
        void prime(EngineWorld actingWorld, BlockPosition pos);
    }

    private static volatile TntPrimeArm tntPrime = (actingWorld, pos) -> { };

    /** The onAdded rolls (the reference's world.random). */
    private static volatile Random onAddedRolls = new Random();

    /**
     * Wires the seams once at boot (Wave FL4-F2: the per-dimension
     * scheduled queues dropped — every schedule consult now rides the
     * CALLING world's own {@link EngineWorld#scheduledTicks()}, the
     * reference's {@code world.scheduleTick} law; the portal arm reads
     * the changed world's own {@link EngineWorld#dimension()} row — the
     * reference's {@code getId() > 0} gate, the overworld AND the nether
     * both attempting the frame build — and the End's eternal-bedrock
     * check the same way).
     */
    public static void wire(BooleanSupplier raining, BooleanSupplier fireTickRule,
                            IntSupplier difficultyId, TntPrimeArm tntPrime,
                            Random onAddedRolls) {
        FireBlocks.raining = java.util.Objects.requireNonNull(raining, "raining");
        FireBlocks.fireTickRule = java.util.Objects.requireNonNull(fireTickRule, "fireTickRule");
        FireBlocks.difficultyId = java.util.Objects.requireNonNull(difficultyId, "difficultyId");
        FireBlocks.tntPrime = java.util.Objects.requireNonNull(tntPrime, "tntPrime");
        FireBlocks.onAddedRolls = java.util.Objects.requireNonNull(onAddedRolls, "onAddedRolls");
    }

    static boolean isRaining() {
        return raining.getAsBoolean();
    }

    /**
     * The doFireTick gate's own read (the game-rule consult the lava
     * source's random-tick fire-start shares — LiquidSourceBlock.tick
     * line 35's own gate).
     */
    static boolean fireTickEnabled() {
        return fireTickRule.getAsBoolean();
    }

    static int difficultyId() {
        return difficultyId.getAsInt();
    }

    // ------------------------------------------------------------------
    // The tick (FireBlock.tick lines 144-226, 1:1) — dispatched through
    // the ScheduledTickSystem's registered ticker, one per fire state.
    // ------------------------------------------------------------------

    /** The fire family's tick arm — registered for every band member. */
    public static void tick(EngineWorld world, BlockPosition pos, BlockType state, Random random) {
        // doFireTick (line 145): the gate wraps EVERYTHING — no age bump,
        // no douse, no burn, no spread, and crucially no re-schedule: the
        // flame freezes inert once its pending entry pops (a placement
        // re-arms it once, the reference's own onAdded path).
        if (!fireTickRule.getAsBoolean()) {
            return;
        }
        // Lines 146-148: an unplaceable flame leaves immediately.
        if (!canBePlaced(world, pos)) {
            world.setBlock(pos, world.airType());
        }
        // Lines 150-154: the eternal flames — netherrack below, and the
        // End's bedrock (the dimension row — the reference's
        // {@code world.dimension instanceof TheEndDimension} gate).
        BlockType below = world.getBlock(pos.offset(0, -1, 0));
        Identifier belowId = below.identifier();
        boolean eternal = belowId.equals(NETHERRACK)
                || (world.dimension() == 1 && belowId.equals(BEDROCK));
        // Lines 156-157: rain douses the exposed flame.
        if (!eternal && isRaining() && shouldExtinguish(world, pos)) {
            world.setBlock(pos, world.airType());
            return;
        }
        // Lines 159-163: the age progression (i + rand(3)/2 — 0 or 1).
        int i = ageOf(state.identifier());
        if (i < 0) {
            i = 0;
        }
        if (i < MAX_AGE) {
            int bump = random.nextInt(3) / 2;
            world.setBlock(pos, stateOf(Math.min(MAX_AGE, i + bump)));
        }
        // Line 165: the re-arm — 30 + rand(10) (getTickRate 30). The
        // family id is the CURRENT state's identifier (the flattened
        // band's member — the age bump above already re-named it). The
        // queue is the CALLING world's own (Wave FL4-F2 — the reference's
        // world.scheduleTick law; the pre-fix static seam armed every
        // dimension's flame into the overworld's queue).
        ScheduledTickSystem ticks = world.scheduledTicks();
        if (ticks != null) {
            ticks.scheduleTick(pos, world.getBlock(pos).identifier(), 30 + random.nextInt(10));
        }
        // Lines 166-179: the fuel consults (an eternal flame skips them).
        if (!eternal) {
            if (!hasFlammableNeighbor(world, pos)) {
                // Lines 168-170: no fuel anywhere — a bare floor holds
                // only the young flames (i <= 3), the old ones die.
                if (!hasSolidTop(world, pos.offset(0, -1, 0)) || i > 3) {
                    world.setBlock(pos, world.airType());
                }
                return;
            }
            // Lines 175-178: the max-age flame on a non-flammable floor
            // has the 1/4 death roll.
            if (!isFlammable(belowId) && i == MAX_AGE && random.nextInt(4) == 0) {
                world.setBlock(pos, world.airType());
                return;
            }
        }
        // Lines 181-185: the humidity discount (the engine has no biome
        // surface — the envelope answers false, the common case: j = 0).
        int j = 0;
        // Lines 187-192: the six burn walks — the sides 300, down/up 250.
        burnNeighbor(world, pos.offset(1, 0, 0), 300 + j, random, i);
        burnNeighbor(world, pos.offset(-1, 0, 0), 300 + j, random, i);
        burnNeighbor(world, pos.offset(0, -1, 0), 250 + j, random, i);
        burnNeighbor(world, pos.offset(0, 1, 0), 250 + j, random, i);
        burnNeighbor(world, pos.offset(0, 0, -1), 300 + j, random, i);
        burnNeighbor(world, pos.offset(0, 0, 1), 300 + j, random, i);
        // Lines 194-223: the spread box — the 3x3 column four up.
        for (int k = -1; k <= 1; k++) {
            for (int l = -1; l <= 1; l++) {
                for (int i1 = -1; i1 <= 4; i1++) {
                    if (k == 0 && i1 == 0 && l == 0) {
                        continue;
                    }
                    int j1 = 100;
                    if (i1 > 1) {
                        j1 += (i1 - 1) * 100;
                    }
                    BlockPosition blockpos = pos.offset(k, i1, l);
                    if (blockpos.y() < net.zaminmc.torch.block.BlockPosition.MIN_Y
                            || blockpos.y() > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                        continue;
                    }
                    int k1 = getSpreadChance(world, blockpos);
                    if (k1 > 0) {
                        // Line 206: the difficulty term (peaceful 0 .. hard 3).
                        int l1 = (k1 + 40 + difficultyId() * 7) / (i + 30);
                        // Lines 207-209: the humid halving (the envelope
                        // answers false — no halving).
                        if (l1 > 0 && random.nextInt(j1) <= l1
                                && (!isRaining() || !shouldExtinguish(world, blockpos))) {
                            // Lines 212-217: the new flame's age i + rand(5)/4.
                            int i2 = i + random.nextInt(5) / 4;
                            if (i2 > MAX_AGE) {
                                i2 = MAX_AGE;
                            }
                            world.setBlock(blockpos, stateOf(i2));
                        }
                    }
                }
            }
        }
    }

    /**
     * The reference's burnNeighbor (lines 251-270, 1:1): the destroy roll
     * over the burnChance table; on success the fuel either becomes fire
     * (the young-flame arm, rand(metadata + 10) < 5, no rain) or is
     * consumed outright — and a burned TNT primes at the full fuse.
     */
    private static void burnNeighbor(EngineWorld world, BlockPosition pos, int chance, Random random, int metadata) {
        BlockType targetType = world.getBlock(pos);
        Identifier targetId = targetType.identifier();
        int burn = burnChanceOf(targetId);
        if (random.nextInt(chance) < burn) {
            boolean wasTnt = targetId.equals(TNT);
            if (random.nextInt(metadata + 10) < 5 && !isRainingAt(world, pos)) {
                int age = metadata + random.nextInt(5) / 4;
                if (age > MAX_AGE) {
                    age = MAX_AGE;
                }
                world.setBlock(pos, stateOf(age));
            } else {
                world.setBlock(pos, world.airType());
            }
            // Lines 266-268: the burned TNT primes (onBroken's EXPLODE
            // arm — the primed body at the full 80-tick fuse; the burn
            // write above already replaced the cell, the reference's own
            // ignite never removes).
            if (wasTnt) {
                tntPrime.prime(world, pos);
            }
        }
    }

    // ------------------------------------------------------------------
    // The placement listener (FireBlock.onAdded lines 318-326, 1:1)
    // ------------------------------------------------------------------

    /**
     * The fire family's onAdded arm, registered as a world change listener
     * on every dimension: a fresh flame (the old cell was NOT fire — an
     * age bump is the reference's flag-4 write, which runs no onAdded)
     * tries the overworld's portal build, then the support check — an
     * unsupported flame leaves, a supported one schedules its first tick
     * 30 + rand(10) out.
     */
    public static final class PlacementListener
            implements net.zaminmc.torch.server.world.WorldChangeListener {

        @Override
        public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
            // no arm — the onAdded consult rides onBlockReplaced alone (the
            // interface's own required method).
        }

        @Override
        public void onBlockReplaced(EngineWorld changedWorld, BlockPosition position,
                                    BlockType oldType, BlockType newType) {
            Identifier newId = newType.identifier();
            if (!isFire(newId)) {
                return;
            }
            if (isFire(oldType.identifier())) {
                return; // an age bump or same-family swap — no onAdded
            }
            // Lines 318-319: the portal attempt — the reference's own gate
            // is {@code world.dimension.getId() > 0} (the OVERWORLD AND
            // THE NETHER both attempt the frame build; the End alone
            // skips). The pre-fix engine gated on the overworld alone — a
            // nether-side frame's ignite never built its portal.
            if (changedWorld.dimension() <= 0
                    && PortalFrameBuilder.createAt(changedWorld, position)) {
                return;
            }
            // Lines 320-325: the support gate — remove or schedule.
            if (!hasSolidTop(changedWorld, position.offset(0, -1, 0))
                    && !hasFlammableNeighbor(changedWorld, position)) {
                changedWorld.setBlock(position, changedWorld.airType());
                return;
            }
            // The schedule rides the CHANGED WORLD'S OWN queue (Wave
            // FL4-F2): the reference's {@code world.scheduleTick(pos,
            // this, ...)} — the pre-fix static seam armed a nether/End
            // flame into the OVERWORLD's queue, where it either froze
            // inert (the foreign world's cells) or wrote the wrong
            // dimension's terrain.
            ScheduledTickSystem ticks = changedWorld.scheduledTicks();
            if (ticks != null) {
                ticks.scheduleTick(position, newId, 30 + onAddedRolls.nextInt(10));
            }
        }
    }

}
