package net.zamin.engine.block;

import net.zamin.api.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * The fluid kinds of the engine, as block types.
 *
 * <p>Data model (the historical 1.8 fluid state, flattened into type identity):
 * a fluid is one of a <b>source</b> ({@code minecraft:water}, {@code
 * minecraft:lava}), a <b>flowing level</b> ({@code minecraft:flowing_water_1}
 * .. {@code _7} — the number is the distance from the nearest source), or a
 * <b>falling column</b> ({@code minecraft:falling_water}). The identifiers
 * carry the state because the engine's block identity is the identifier; the
 * version adapter collapses them onto the 1.8 wire pairs (still 9/11 metadata
 * 0, flowing 8/10 metadata = level, falling = metadata 8). Sources only enter
 * the world through buckets this slice — infinite-source rules (the 2x2 pool)
 * are a documented non-goal.</p>
 *
 * <p>Spread constants are the historical ones: water advances one level per
 * block (seven blocks of reach) on a 5-tick cadence; lava advances two (three
 * blocks of reach) on a 30-tick cadence. Falling takes priority over
 * spreading — a fluid with an open cell below pours straight down, and only a
 * column with ground under it runs sideways.</p>
 *
 * <p>Behavior rules ported in shape from TogAr2/MinestomFluids (MIT) — see
 * COMMUNITY_REFERENCES.md; the implementation is the engine's own.</p>
 */
public final class FluidBlocks {

    public static final int WATER_CADENCE_TICKS = 5;
    public static final int LAVA_CADENCE_TICKS = 30;
    /** Highest flow level that still spreads (the seventh block of reach). */
    public static final int MAX_FLOW_LEVEL = 7;
    /** Lava loses two levels per block — three blocks of reach from a source. */
    public static final int LAVA_LEVEL_STEP = 2;

    public enum Kind {
        WATER,
        LAVA
    }

    public static final Identifier WATER_SOURCE = Identifier.parse("minecraft:water");
    public static final Identifier LAVA_SOURCE = Identifier.parse("minecraft:lava");
    public static final Identifier WATER_FALLING = Identifier.parse("minecraft:falling_water");
    public static final Identifier LAVA_FALLING = Identifier.parse("minecraft:falling_lava");

    private static final String FLOWING_WATER_PREFIX = "minecraft:flowing_water_";
    private static final String FLOWING_LAVA_PREFIX = "minecraft:flowing_lava_";

    /** source -> kind, flowing prefix -> kind, falling -> kind. */
    private static final Map<Identifier, Kind> FLUID_KINDS = buildKinds();

    /** Every fluid block type keyed by identifier (registry population). */
    private static final Map<Identifier, EngineBlockType> ALL_TYPES = buildTypes();

    private static Map<Identifier, Kind> buildKinds() {
        Map<Identifier, Kind> kinds = new HashMap<>();
        kinds.put(WATER_SOURCE, Kind.WATER);
        kinds.put(LAVA_SOURCE, Kind.LAVA);
        kinds.put(WATER_FALLING, Kind.WATER);
        kinds.put(LAVA_FALLING, Kind.LAVA);
        for (int level = 1; level <= MAX_FLOW_LEVEL; level++) {
            kinds.put(Identifier.parse(FLOWING_WATER_PREFIX + level), Kind.WATER);
            kinds.put(Identifier.parse(FLOWING_LAVA_PREFIX + level), Kind.LAVA);
        }
        return Map.copyOf(kinds);
    }

    private static Map<Identifier, EngineBlockType> buildTypes() {
        Map<Identifier, EngineBlockType> types = new HashMap<>();
        types.put(WATER_SOURCE, new EngineBlockType(WATER_SOURCE, "Water", 0));
        types.put(LAVA_SOURCE, new EngineBlockType(LAVA_SOURCE, "Lava", 0));
        types.put(WATER_FALLING, new EngineBlockType(WATER_FALLING, "Water", 8));
        types.put(LAVA_FALLING, new EngineBlockType(LAVA_FALLING, "Lava", 8));
        for (int level = 1; level <= MAX_FLOW_LEVEL; level++) {
            types.put(Identifier.parse(FLOWING_WATER_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FLOWING_WATER_PREFIX + level),
                            "Water", level));
            types.put(Identifier.parse(FLOWING_LAVA_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FLOWING_LAVA_PREFIX + level),
                            "Lava", level));
        }
        return Map.copyOf(types);
    }

    private FluidBlocks() {
    }

    /** Registers every fluid type into the block registry builder. */
    public static BlockRegistryBuilder registerAll(BlockRegistryBuilder builder) {
        for (EngineBlockType type : ALL_TYPES.values()) {
            builder.register(type);
        }
        return builder;
    }

    /** @return the fluid type of a kind at a flow level (0 = source, 8 = falling). */
    public static EngineBlockType typeOf(Kind kind, int level) {
        if (level == 0) {
            return ALL_TYPES.get(kind == Kind.WATER ? WATER_SOURCE : LAVA_SOURCE);
        }
        if (level == 8) {
            return ALL_TYPES.get(kind == Kind.WATER ? WATER_FALLING : LAVA_FALLING);
        }
        return ALL_TYPES.get(Identifier.parse(
                (kind == Kind.WATER ? FLOWING_WATER_PREFIX : FLOWING_LAVA_PREFIX) + level));
    }

    /** @return the source block type of a fluid kind. */
    public static EngineBlockType sourceOf(Kind kind) {
        return ALL_TYPES.get(kind == Kind.WATER ? WATER_SOURCE : LAVA_SOURCE);
    }

    /** @return whether the identifier is any fluid state. */
    public static boolean isFluid(Identifier identifier) {
        return FLUID_KINDS.containsKey(identifier);
    }

    /** @return the fluid kind, or null for non-fluids. */
    public static Kind kindOf(Identifier identifier) {
        return FLUID_KINDS.get(identifier);
    }

    /** @return whether the identifier is a source block (never the falling column). */
    public static boolean isSource(Identifier identifier) {
        return identifier.equals(WATER_SOURCE) || identifier.equals(LAVA_SOURCE);
    }

    /** @return whether the identifier is any water state. */
    public static boolean isWater(Identifier identifier) {
        return kindOf(identifier) == Kind.WATER;
    }

    /** @return whether the identifier is any lava state. */
    public static boolean isLava(Identifier identifier) {
        return kindOf(identifier) == Kind.LAVA;
    }

    /**
     * @return the flow distance the identifier carries: 0 for sources, 1..7
     * for flowing levels, 8 for the falling column, -1 for non-fluids.
     */
    public static int levelOf(Identifier identifier) {
        if (identifier.equals(WATER_SOURCE) || identifier.equals(LAVA_SOURCE)) {
            return 0;
        }
        if (identifier.equals(WATER_FALLING) || identifier.equals(LAVA_FALLING)) {
            return 8;
        }
        String name = identifier.toString();
        for (int level = 1; level <= MAX_FLOW_LEVEL; level++) {
            if (name.equals(FLOWING_WATER_PREFIX + level)
                    || name.equals(FLOWING_LAVA_PREFIX + level)) {
                return level;
            }
        }
        return -1;
    }

    /** @return the cadence (ticks between fluid updates) of the kind. */
    public static int cadenceOf(Kind kind) {
        return kind == Kind.WATER ? WATER_CADENCE_TICKS : LAVA_CADENCE_TICKS;
    }

    /** @return how many levels the kind loses per block of horizontal travel. */
    public static int decayOf(Kind kind) {
        return kind == Kind.WATER ? 1 : LAVA_LEVEL_STEP;
    }
}
