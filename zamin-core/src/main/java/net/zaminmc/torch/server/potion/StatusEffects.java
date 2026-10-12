package net.zaminmc.torch.server.potion;

import java.util.ArrayList;
import java.util.List;

/**
 * The status effect registry (Slice 11b — the reference port of
 * entity/living/effect/StatusEffect.java): the twenty-three effects of
 * 1.8.8 in id order, each carrying the harmful flag (the undead-style
 * negative set), the potion color (the splash tint and the particle
 * average), the duration multiplier (the reference's own
 * {@code setDurationMultiplier} arms — regeneration/poison/wither/nausea/
 * blindness run a quarter, haste 1.5, the harmful set a half), and the
 * instant flag (the health/damage/saturation family that applies once
 * and never rides a duration).
 *
 * <p>The periodic application cadences live here too
 * (StatusEffect.shouldApply lines 189-202): regeneration every
 * {@code 50 >> amplifier} ticks, poison every {@code 25 >> amplifier},
 * wither every {@code 40 >> amplifier}, hunger every tick — the durations
 * count DOWN in the engine's store, exactly the reference's countdown
 * clock the modulo reads.</p>
 */
public final class StatusEffects {

    private StatusEffects() {
    }

    /** One effect's static data (the reference's registered fields). */
    public record Effect(int id, String key, boolean harmful, int color,
                        double durationMultiplier, boolean instant) {
    }

    public static final int SPEED = 1;
    public static final int SLOWNESS = 2;
    public static final int HASTE = 3;
    public static final int MINING_FATIGUE = 4;
    public static final int STRENGTH = 5;
    public static final int INSTANT_HEALTH = 6;
    public static final int INSTANT_DAMAGE = 7;
    public static final int JUMP_BOOST = 8;
    public static final int NAUSEA = 9;
    public static final int REGENERATION = 10;
    public static final int RESISTANCE = 11;
    public static final int FIRE_RESISTANCE = 12;
    public static final int WATER_BREATHING = 13;
    public static final int INVISIBILITY = 14;
    public static final int BLINDNESS = 15;
    public static final int NIGHT_VISION = 16;
    public static final int HUNGER = 17;
    public static final int WEAKNESS = 18;
    public static final int POISON = 19;
    public static final int WITHER = 20;
    public static final int HEALTH_BOOST = 21;
    public static final int ABSORPTION = 22;
    public static final int SATURATION = 23;

    /** The BY_ID table (index 0 unused, the reference's own convention). */
    private static final Effect[] BY_ID = build();

    private static Effect[] build() {
        List<Effect> list = new ArrayList<>(32);
        for (int i = 0; i < 32; i++) {
            list.add(null);
        }
        register(list, new Effect(SPEED, "potion.moveSpeed", false, 8171462, 1.0, false));
        register(list, new Effect(SLOWNESS, "potion.moveSlowdown", true, 5926017, 0.5, false));
        register(list, new Effect(HASTE, "potion.digSpeed", false, 14270531, 1.5, false));
        register(list, new Effect(MINING_FATIGUE, "potion.digSlowDown", true, 4866583, 0.5, false));
        register(list, new Effect(STRENGTH, "potion.damageBoost", false, 9643043, 1.0, false));
        register(list, new Effect(INSTANT_HEALTH, "potion.heal", false, 16262179, 1.0, true));
        register(list, new Effect(INSTANT_DAMAGE, "potion.harm", true, 4393481, 0.5, true));
        register(list, new Effect(JUMP_BOOST, "potion.jump", false, 2293580, 1.0, false));
        register(list, new Effect(NAUSEA, "potion.confusion", true, 5578058, 0.25, false));
        register(list, new Effect(REGENERATION, "potion.regeneration", false, 13458603, 0.25, false));
        register(list, new Effect(RESISTANCE, "potion.resistance", false, 10044730, 1.0, false));
        register(list, new Effect(FIRE_RESISTANCE, "potion.fireResistance", false, 14981690, 1.0, false));
        register(list, new Effect(WATER_BREATHING, "potion.waterBreathing", false, 3035801, 1.0, false));
        register(list, new Effect(INVISIBILITY, "potion.invisibility", false, 8356754, 1.0, false));
        register(list, new Effect(BLINDNESS, "potion.blindness", true, 2039587, 0.25, false));
        register(list, new Effect(NIGHT_VISION, "potion.nightVision", false, 2039713, 1.0, false));
        register(list, new Effect(HUNGER, "potion.hunger", true, 5797459, 0.5, false));
        register(list, new Effect(WEAKNESS, "potion.weakness", true, 4738376, 0.5, false));
        register(list, new Effect(POISON, "potion.poison", true, 5149489, 0.25, false));
        register(list, new Effect(WITHER, "potion.wither", true, 3484199, 0.25, false));
        register(list, new Effect(HEALTH_BOOST, "potion.healthBoost", false, 16284963, 1.0, false));
        register(list, new Effect(ABSORPTION, "potion.absorption", false, 2445989, 1.0, false));
        register(list, new Effect(SATURATION, "potion.saturation", false, 16262179, 1.0, true));
        return list.toArray(new Effect[0]);
    }

    private static void register(List<Effect> list, Effect effect) {
        list.set(effect.id(), effect);
    }

    /** @return the effect at the id, or null (the reference's own gaps). */
    public static Effect byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
    }

    /** @return whether the id names a registered effect. */
    public static boolean exists(int id) {
        return byId(id) != null;
    }

    /**
     * The periodic application gate (StatusEffect.shouldApply lines
     * 189-202): the reference counts the duration DOWN and applies when
     * {@code duration % cadence == 0}.
     */
    public static boolean shouldApply(int id, int duration, int amplifier) {
        if (id == REGENERATION) {
            int cadence = 50 >> amplifier;
            return cadence <= 0 || duration % cadence == 0;
        }
        if (id == POISON) {
            int cadence = 25 >> amplifier;
            return cadence <= 0 || duration % cadence == 0;
        }
        if (id == WITHER) {
            int cadence = 40 >> amplifier;
            return cadence <= 0 || duration % cadence == 0;
        }
        return id == HUNGER;
    }

    /** @return whether the effect's duration multiplier halves it (the harmful set). */
    static double durationMultiplierOf(int id) {
        Effect effect = byId(id);
        return effect == null ? 1.0 : effect.durationMultiplier();
    }

    /** @return whether the effect is instant (no duration rides it). */
    static boolean isInstant(int id) {
        Effect effect = byId(id);
        return effect != null && effect.instant();
    }

    /** @return the effect's potion color (the splash tint average's input). */
    static int colorOf(int id) {
        Effect effect = byId(id);
        return effect == null ? 0 : effect.color();
    }
}
