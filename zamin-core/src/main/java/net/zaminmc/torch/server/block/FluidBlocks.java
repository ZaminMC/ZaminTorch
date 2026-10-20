package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The fluid state band of the engine, as block types — the reference's
 * {@code LiquidBlock.LEVEL} 0-15 whole (reference/1.8.8
 * net/minecraft/block/LiquidBlock.java line 22, the Wave VI fluids
 * migration).
 *
 * <p><b>The reference's two block forms</b> (the wire's four ids): the
 * SOURCE block ({@code water} 9 / {@code lava} 11 — {@code
 * LiquidSourceBlock}, the settled still form) and the FLOWING block
 * ({@code flowing_water} 8 / {@code flowing_lava} 10 — {@code
 * FlowingLiquidBlock}, the animated form). The LEVEL nibble carries the
 * state on both: 0 is a true source (the bucket-fill level), 1-7 the
 * increasingly shallow flow, 8-15 the falling column (the bit 8 set,
 * with the low three bits naming the depth of the liquid ABOVE — the
 * reference's own {@code getLiquidState} javadoc: "If the liquid above
 * is also falling, the depth is always 0").</p>
 *
 * <p><b>The engine's flattened naming</b> (the identifier IS the state,
 * the wire adapter folds them back onto the two-form pairs through
 * {@code metadataOf}): {@code water}/{@code lava} — the true source
 * (LEVEL 0 on the source form); {@code still_water_N}/{@code
 * still_lava_N} — the SOURCE-block form at a settled level N (the
 * {@code convertToSource} write keeps the level — the still water a
 * settled stream becomes); {@code flowing_water}/{@code flowing_lava} —
 * the flowing form at LEVEL 0 (the bucket's own pour state, transient:
 * its tick converts to the source); {@code flowing_*_1..7} — the
 * shallow flow; {@code falling_water}/{@code falling_lava} — the plain
 * falling column (LEVEL 8); {@code falling_water_N}/{@code
 * falling_lava_N} — the falling column carrying the above-depth N
 * (LEVEL 9-15, the demand-named band).</p>
 *
 * <p><b>The cadence band</b> ({@code LiquidBlock.getTickRate} lines
 * 178-186): water 5; lava 30 in a skied dimension, 10 in a no-sky
 * dimension (the nether's own lava sea moves three times faster). The
 * DECAY band is the tick's own {@code j} consult (water 1; lava 2 in a
 * dimension that keeps water, 1 where water vaporizes — the nether's
 * lava spreads at full seven-block reach): both consults live in
 * {@link FluidSystem}'s tick over the {@link FluidSystem.World}
 * dimension profile, 1:1 with the reference's {@code
 * world.dimension.yeetsWater()}/{@code hasNoSky()} reads.</p>
 */
public final class FluidBlocks {

    public static final int WATER_CADENCE_TICKS = 5;
    public static final int LAVA_CADENCE_TICKS = 30;
    /** The nether's own lava cadence ({@code getTickRate}'s no-sky band). */
    public static final int LAVA_CADENCE_NO_SKY_TICKS = 10;
    /** The LEVEL nibble's full band: 0-7 the flow depth, 8-15 the falling column. */
    public static final int MAX_LEVEL = 15;
    /** The falling bit: levels 8-15 are falling, the low three bits the above-depth. */
    public static final int FALLING_BIT = 8;

    public enum Kind {
        WATER,
        LAVA
    }

    public static final Identifier WATER_SOURCE = Identifier.parse("minecraft:water");
    public static final Identifier LAVA_SOURCE = Identifier.parse("minecraft:lava");
    public static final Identifier WATER_FALLING = Identifier.parse("minecraft:falling_water");
    public static final Identifier LAVA_FALLING = Identifier.parse("minecraft:falling_lava");

    /** The block-form families (the reference's four registrations). */
    public enum Form {
        /** The source BLOCK ({@code LiquidSourceBlock}) — the settled still form, any level. */
        SOURCE,
        /** The flowing BLOCK ({@code FlowingLiquidBlock}) — the animated form, any level. */
        FLOWING
    }

    private static final String FLOWING_WATER_PREFIX = "minecraft:flowing_water_";
    private static final String FLOWING_LAVA_PREFIX = "minecraft:flowing_lava_";
    private static final String STILL_WATER_PREFIX = "minecraft:still_water_";
    private static final String STILL_LAVA_PREFIX = "minecraft:still_lava_";
    private static final String FALLING_WATER_PREFIX = "minecraft:falling_water_";
    private static final String FALLING_LAVA_PREFIX = "minecraft:falling_lava_";

    /** The value-only prefixes (the namespace-free {@link #levelOf} walks). */
    private static final String V_FLOWING_WATER = "flowing_water_";
    private static final String V_FLOWING_LAVA = "flowing_lava_";
    private static final String V_STILL_WATER = "still_water_";
    private static final String V_STILL_LAVA = "still_lava_";
    private static final String V_FALLING_WATER = "falling_water_";
    private static final String V_FALLING_LAVA = "falling_lava_";

    /** source/still/flowing/falling -> kind. */
    private static final Map<Identifier, Kind> FLUID_KINDS = buildKinds();

    /** Every fluid block type keyed by identifier (registry population). */
    private static final Map<Identifier, EngineBlockType> ALL_TYPES = buildTypes();

    /** The ids in band order (the ticker registration + census walk). */
    private static final List<Identifier> FAMILY_IDS = buildFamilyIds();

    private static Map<Identifier, Kind> buildKinds() {
        Map<Identifier, Kind> kinds = new HashMap<>();
        kinds.put(WATER_SOURCE, Kind.WATER);
        kinds.put(LAVA_SOURCE, Kind.LAVA);
        kinds.put(Identifier.parse("minecraft:flowing_water"), Kind.WATER);
        kinds.put(Identifier.parse("minecraft:flowing_lava"), Kind.LAVA);
        kinds.put(WATER_FALLING, Kind.WATER);
        kinds.put(LAVA_FALLING, Kind.LAVA);
        for (int level = 1; level <= 7; level++) {
            // The shallow flow band (LEVEL 1-7) and the falling-with-level
            // band (LEVEL 9-15 — the above-depth in the low three bits).
            kinds.put(Identifier.parse(FLOWING_WATER_PREFIX + level), Kind.WATER);
            kinds.put(Identifier.parse(FLOWING_LAVA_PREFIX + level), Kind.LAVA);
            kinds.put(Identifier.parse(FALLING_WATER_PREFIX + level), Kind.WATER);
            kinds.put(Identifier.parse(FALLING_LAVA_PREFIX + level), Kind.LAVA);
        }
        for (int level = 1; level <= MAX_LEVEL; level++) {
            // The settled still band (the source block at LEVEL 1-15).
            kinds.put(Identifier.parse(STILL_WATER_PREFIX + level), Kind.WATER);
            kinds.put(Identifier.parse(STILL_LAVA_PREFIX + level), Kind.LAVA);
        }
        return Map.copyOf(kinds);
    }

    private static Map<Identifier, EngineBlockType> buildTypes() {
        Map<Identifier, EngineBlockType> types = new HashMap<>();
        types.put(WATER_SOURCE, new EngineBlockType(WATER_SOURCE, "Water", 0));
        types.put(LAVA_SOURCE, new EngineBlockType(LAVA_SOURCE, "Lava", 0));
        types.put(Identifier.parse("minecraft:flowing_water"),
                new EngineBlockType(Identifier.parse("minecraft:flowing_water"), "Water", 0));
        types.put(Identifier.parse("minecraft:flowing_lava"),
                new EngineBlockType(Identifier.parse("minecraft:flowing_lava"), "Lava", 0));
        types.put(WATER_FALLING, new EngineBlockType(WATER_FALLING, "Water", FALLING_BIT));
        types.put(LAVA_FALLING, new EngineBlockType(LAVA_FALLING, "Lava", FALLING_BIT));
        for (int level = 1; level <= 7; level++) {
            // The shallow flow band (LEVEL 1-7) and the falling-with-level
            // band (LEVEL 9-15 — the LEVEL nibble carries the whole state).
            types.put(Identifier.parse(FLOWING_WATER_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FLOWING_WATER_PREFIX + level),
                            "Water", level));
            types.put(Identifier.parse(FLOWING_LAVA_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FLOWING_LAVA_PREFIX + level),
                            "Lava", level));
            types.put(Identifier.parse(FALLING_WATER_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FALLING_WATER_PREFIX + level),
                            "Water", FALLING_BIT + level));
            types.put(Identifier.parse(FALLING_LAVA_PREFIX + level),
                    new EngineBlockType(Identifier.parse(FALLING_LAVA_PREFIX + level),
                            "Lava", FALLING_BIT + level));
        }
        for (int level = 1; level <= MAX_LEVEL; level++) {
            types.put(Identifier.parse(STILL_WATER_PREFIX + level),
                    new EngineBlockType(Identifier.parse(STILL_WATER_PREFIX + level),
                            "Water", level));
            types.put(Identifier.parse(STILL_LAVA_PREFIX + level),
                    new EngineBlockType(Identifier.parse(STILL_LAVA_PREFIX + level),
                            "Lava", level));
        }
        return Map.copyOf(types);
    }

    private static List<Identifier> buildFamilyIds() {
        List<Identifier> ids = new ArrayList<>(ALL_TYPES.size());
        for (int level = 0; level <= MAX_LEVEL; level++) {
            for (Kind kind : Kind.values()) {
                ids.add(levelId(kind, Form.SOURCE, level));
                ids.add(levelId(kind, Form.FLOWING, level));
            }
        }
        return List.copyOf(ids);
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

    // ------------------------------------------------------------------
    // The state consults (the reference's LEVEL reads)
    // ------------------------------------------------------------------

    /**
     * @return the identifier of a kind's form at a LEVEL 0-15 (the
     * reference's {@code defaultState().set(LEVEL, level)} — the flattened
     * band's own member).
     */
    public static Identifier levelId(Kind kind, Form form, int level) {
        if (level < 0 || level > MAX_LEVEL) {
            throw new IllegalArgumentException("level out of the 0-15 band: " + level);
        }
        String prefix;
        if (level == 0) {
            return form == Form.SOURCE
                    ? (kind == Kind.WATER ? WATER_SOURCE : LAVA_SOURCE)
                    : (kind == Kind.WATER
                            ? Identifier.parse("minecraft:flowing_water")
                            : Identifier.parse("minecraft:flowing_lava"));
        }
        if (form == Form.SOURCE) {
            prefix = kind == Kind.WATER ? STILL_WATER_PREFIX : STILL_LAVA_PREFIX;
            return Identifier.parse(prefix + level);
        }
        if (level <= 7) {
            prefix = kind == Kind.WATER ? FLOWING_WATER_PREFIX : FLOWING_LAVA_PREFIX;
            return Identifier.parse(prefix + level);
        }
        if (level == FALLING_BIT) {
            return kind == Kind.WATER ? WATER_FALLING : LAVA_FALLING;
        }
        prefix = kind == Kind.WATER ? FALLING_WATER_PREFIX : FALLING_LAVA_PREFIX;
        return Identifier.parse(prefix + (level - FALLING_BIT));
    }

    /** @return the block type of a kind's form at a LEVEL 0-15 (registry-resolved). */
    public static EngineBlockType typeOf(Kind kind, Form form, int level) {
        EngineBlockType type = ALL_TYPES.get(levelId(kind, form, level));
        if (type == null) {
            throw new IllegalStateException("Fluid state unregistered: "
                    + levelId(kind, form, level));
        }
        return type;
    }

    /** @return the true source block type of a fluid kind (LEVEL 0, the source form). */
    public static EngineBlockType sourceOf(Kind kind) {
        return typeOf(kind, Form.SOURCE, 0);
    }

    /**
     * @return the LEVEL the identifier carries on its form: 0 for the
     * sources and the flowing form's own zero level, 1-15 otherwise, -1
     * for non-fluids (the reference's {@code getLiquidState} shape).
     */
    public static int levelOf(Identifier identifier) {
        Kind kind = kindOf(identifier);
        if (kind == null) {
            return -1;
        }
        String value = identifier.value();
        if (identifier.equals(WATER_SOURCE) || identifier.equals(LAVA_SOURCE)) {
            return 0;
        }
        if (value.equals("flowing_water") || value.equals("flowing_lava")) {
            return 0;
        }
        if (identifier.equals(WATER_FALLING) || identifier.equals(LAVA_FALLING)) {
            return FALLING_BIT;
        }
        if (kind == Kind.WATER) {
            if (value.startsWith(V_STILL_WATER)) {
                return suffix(value, V_STILL_WATER);
            }
            if (value.startsWith(V_FLOWING_WATER)) {
                return suffix(value, V_FLOWING_WATER);
            }
            if (value.startsWith(V_FALLING_WATER)) {
                return FALLING_BIT + suffix(value, V_FALLING_WATER);
            }
        } else {
            if (value.startsWith(V_STILL_LAVA)) {
                return suffix(value, V_STILL_LAVA);
            }
            if (value.startsWith(V_FLOWING_LAVA)) {
                return suffix(value, V_FLOWING_LAVA);
            }
            if (value.startsWith(V_FALLING_LAVA)) {
                return FALLING_BIT + suffix(value, V_FALLING_LAVA);
            }
        }
        return -1;
    }

    /** @return the integer suffix after the prefix (the band's own parse). */
    private static int suffix(String value, String prefix) {
        return Integer.parseInt(value.substring(prefix.length()));
    }

    /** @return the block form the identifier rides (the wire's 9/11 vs 8/10 pair). */
    public static Form formOf(Identifier identifier) {
        Kind kind = kindOf(identifier);
        if (kind == null) {
            throw new IllegalArgumentException("not a fluid: " + identifier);
        }
        String value = identifier.value();
        return value.equals("water") || value.equals("lava")
                || value.startsWith("still_water") || value.startsWith("still_lava")
                ? Form.SOURCE : Form.FLOWING;
    }

    /** @return whether the identifier is any fluid state. */
    public static boolean isFluid(Identifier identifier) {
        return FLUID_KINDS.containsKey(identifier);
    }

    /** @return the fluid kind, or null for non-fluids. */
    public static Kind kindOf(Identifier identifier) {
        return FLUID_KINDS.get(identifier);
    }

    /**
     * @return whether the identifier is the source-BLOCK form at level 0 —
     * the true source (the bucket-fill level, the reference's {@code LEVEL
     * == 0} on the source form; a settled {@code still_*} level is NOT a
     * source).
     */
    public static boolean isSource(Identifier identifier) {
        return identifier.equals(WATER_SOURCE) || identifier.equals(LAVA_SOURCE);
    }

    /**
     * @return whether the identifier rides the source BLOCK form (any
     * level — the wire's 9/11 pair, the settled still form).
     */
    public static boolean isSourceForm(Identifier identifier) {
        return isFluid(identifier) && formOf(identifier) == Form.SOURCE;
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
     * @return the cadence (ticks between fluid updates) of the kind in the
     * dimension — water's flat 5; lava's 30/10 band over the no-sky
     * consult (the reference's {@code getTickRate} lines 178-186).
     */
    public static int cadenceOf(Kind kind, boolean hasNoSky) {
        if (kind == Kind.WATER) {
            return WATER_CADENCE_TICKS;
        }
        return hasNoSky ? LAVA_CADENCE_NO_SKY_TICKS : LAVA_CADENCE_TICKS;
    }

    /** Every fluid state identifier, band order (the registration walks). */
    public static List<Identifier> familyIds() {
        return FAMILY_IDS;
    }
}
