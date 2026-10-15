package net.zaminmc.torch.server.potion;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The potion effect system (Slice 11b — the reference port of
 * entity/living/LivingEntity's status-effect arms + PotionItem +
 * PotionEntity): the per-body effect stores (the vanilla merge on
 * re-application), the per-tick countdown with the cadenced periodic
 * application (regeneration heals, poison stops one short of dead, wither
 * hurts freely, hunger exhausts), the instant family's smite-aware
 * health swing, the splash's proximity-scaled area application, and the
 * drink's effect set with the glass-bottle swap.
 *
 * <p>The bodies stay the engine's own (players and mobs); this system
 * owns the effect state and the application math. The Entity Effect wire
 * packets fan through the listener (the client HUD and the client's own
 * movement modifiers ride them — 1.8.8's client applies the speed/
 * slowness/jump attribute modifiers itself from the effect, so sending
 * the packet IS the faithful port for the client-authoritative walk).</p>
 */
public final class PotionSystem {

    private static final int MAX_EFFECTS = 32;

    /** The engine's body surface (the application sink — the CombatSink pattern). */
    public interface Bodies {
        /** Every living body inside the box (the splash scan). */
        List<Object> bodiesIn(double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ);

        /** A body's squared distance to a point (the proximity falloff). */
        double squaredDistanceTo(Object body, double x, double y, double z);

        /**
         * The damage arm (DamageSource.MAGIC — poison never kills,
         * wither does, the death cause carries). The attacker rides the
         * splash's {@code DamageSource.magic(potion, thrower)} shape (null
         * for the periodic effects and dispenser-launched potions); the
         * magic flag separates the magic family from the wither's own
         * source — the witch's 85% magic shave keys on it.
         */
        void hurt(Object body, float amount, String cause, Object attacker, boolean magic);

        /** The heal arm. */
        void heal(Object body, float amount);

        /** The hunger effect's exhaustion (players only). */
        void exhaust(Object body, float amount);

        /** The saturation effect's food+saturation gain (players only). */
        void feed(Object body, int food, float saturation);

        /** The body's current health (the poison gate: one short of dead). */
        float healthOf(Object body);

        /** The fire-resistance gate: the burn paths skip a protected body. */
        boolean fireResistant(Object body);

        /** The undead family (the smite-aware instant pair). */
        boolean undead(Object body);

        /**
         * The blanket effect refusal (Slice 59 — WitherEntity
         * .addStatusEffect's empty override): the boss takes NO effect
         * from any source, potion or skull.
         */
        default boolean refusesEffects(Object body) {
            return false;
        }

        /** Whether the body is a player session (the effect-store keying). */
        boolean isPlayer(Object body);

        /** The body's effect-store key (the engine entity id). */
        int keyOf(Object body);
    }

    /** Events the protocol adapter translates into Entity Effect packets. */
    public interface Listener {
        default void onEffectAdded(PlayerSession player, StatusEffectInstance effect) { }

        default void onEffectRemoved(PlayerSession player, StatusEffectInstance effect) { }

        /** The 600-tick re-sync (tickStatusEffects' {@code % 600 == 0} arm):
         * the client's timer gets a fresh packet. */
        default void onEffectResynced(PlayerSession player, StatusEffectInstance effect) { }
    }

    private final Bodies bodies;
    private final List<Listener> listeners = new ArrayList<>();
    /** The per-body effect stores, keyed by engine entity id. */
    private final Map<Integer, Map<Integer, StatusEffectInstance>> effects = new LinkedHashMap<>();

    public PotionSystem(Bodies bodies) {
        this.bodies = bodies;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    // ------------------------------------------------------------------
    // The effect stores (LivingEntity.addStatusEffect lines 555-566)
    // ------------------------------------------------------------------

    /**
     * Adds an effect to a body (the vanilla merge): the undead refuse
     * regeneration and poison outright (canHaveStatusEffect), a matching
     * id combines (the stronger amplifier resets the duration, the equal
     * keeps the longer), a fresh id installs.
     */
    public void add(Object body, StatusEffectInstance instance) {
        if (bodies.refusesEffects(body)) {
            return; // WitherEntity.addStatusEffect: the boss's empty override
        }
        int id = instance.id();
        if ((id == StatusEffects.REGENERATION || id == StatusEffects.POISON)
                && bodies.undead(body)) {
            return; // canHaveStatusEffect: the undead heal-and-rot refusal
        }
        int key = bodies.keyOf(body);
        Map<Integer, StatusEffectInstance> store = effects.computeIfAbsent(
                key, k -> new LinkedHashMap<>());
        StatusEffectInstance existing = store.get(id);
        if (existing != null) {
            existing.combine(instance);
        } else {
            store.put(id, instance);
        }
        if (bodies.isPlayer(body)) {
            for (Listener listener : listeners) {
                listener.onEffectAdded((PlayerSession) body, store.get(id));
            }
        }
    }

    /** @return the live effect at the id on the body, or null. */
    public StatusEffectInstance get(Object body, int id) {
        Map<Integer, StatusEffectInstance> store = effects.get(bodies.keyOf(body));
        return store == null ? null : store.get(id);
    }

    /** @return the body's whole effect store (the diagnostics read). */
    public Map<Integer, StatusEffectInstance> allOf(Object body) {
        Map<Integer, StatusEffectInstance> store = effects.get(bodies.keyOf(body));
        return store == null ? Map.of() : store;
    }

    /** @return whether the body carries an active fire-resistance (the burn gates). */
    public boolean hasEffect(Object body, int id) {
        StatusEffectInstance instance = get(body, id);
        return instance != null && instance.duration() > 0;
    }

    /** Clears one effect (the milk arm's future surface). */
    public void remove(Object body, int id) {
        Map<Integer, StatusEffectInstance> store = effects.get(bodies.keyOf(body));
        if (store == null) {
            return;
        }
        StatusEffectInstance removed = store.remove(id);
        if (removed != null && bodies.isPlayer(body)) {
            for (Listener listener : listeners) {
                listener.onEffectRemoved((PlayerSession) body, removed);
            }
        }
    }

    /** Clears every effect on the body (death, the dimension rules' surface). */
    public void clear(Object body) {
        Map<Integer, StatusEffectInstance> store = effects.remove(bodies.keyOf(body));
        if (store != null && bodies.isPlayer(body)) {
            for (StatusEffectInstance instance : store.values()) {
                for (Listener listener : listeners) {
                    listener.onEffectRemoved((PlayerSession) body, instance);
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // The per-tick walk (LivingEntity.tick's effect stage)
    // ------------------------------------------------------------------

    /** Advances every effect on one body: the cadenced application, the
     * expiry, and the 600-tick client re-sync (the reference's
     * {@code duration % 600 == 0} arm — the packet refresh). */
    public void tick(Object body) {
        Map<Integer, StatusEffectInstance> store = effects.get(bodies.keyOf(body));
        if (store == null || store.isEmpty()) {
            return;
        }
        List<Integer> expired = null;
        for (Map.Entry<Integer, StatusEffectInstance> entry : store.entrySet()) {
            StatusEffectInstance instance = entry.getValue();
            boolean alive = instance.tick(thisInstance ->
                    applyPeriodic(body, thisInstance));
            if (!alive) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(entry.getKey());
            } else if (instance.duration() % 600 == 0 && bodies.isPlayer(body)) {
                for (Listener listener : listeners) {
                    listener.onEffectResynced((PlayerSession) body, instance);
                }
            }
        }
        if (expired != null) {
            for (int id : expired) {
                remove(body, id);
            }
        }
    }

    /**
     * The periodic application (StatusEffect.apply lines 140-163):
     * regeneration heals one (only under the max), poison stops one short
     * of dead (the {@code health > 1.0} gate — the reference's own rule,
     * poison never kills), wither hurts freely, hunger exhausts 0.025 per
     * amplifier step, saturation feeds — the rest carry no periodic arm
     * (their behavior rides the attribute modifiers the client applies
     * from the effect packet).
     */
    private void applyPeriodic(Object body, StatusEffectInstance instance) {
        switch (instance.id()) {
            case StatusEffects.REGENERATION -> bodies.heal(body, 1.0f);
            case StatusEffects.POISON -> {
                if (bodies.healthOf(body) > 1.0f) {
                    bodies.hurt(body, 1.0f, "was killed by magic", null, true);
                }
            }
            case StatusEffects.WITHER -> bodies.hurt(body, 1.0f, "withered away", null, false);
            case StatusEffects.HUNGER -> bodies.exhaust(body, 0.025f * (instance.amplifier() + 1));
            case StatusEffects.SATURATION -> bodies.feed(body, instance.amplifier() + 1, 1.0f);
            default -> { /* the attribute family: the packet is the behavior */ }
        }
    }

    // ------------------------------------------------------------------
    // The instant family (StatusEffect.apply's smite pair + affectHealth)
    // ------------------------------------------------------------------

    /**
     * The instant application with the proximity scaling
     * (StatusEffect.affectHealth lines 166-180 — the splash's
     * direct-hit 1.0 and the falloff): instant health heals the living
     * and damages the undead ({@code 4 << amplifier}), instant damage the
     * mirror — each scaled by the proximity and rounded.
     */
    public void applyInstant(Object body, int id, int amplifier, double proximity) {
        if (id == StatusEffects.INSTANT_HEALTH || id == StatusEffects.INSTANT_DAMAGE) {
            boolean undead = bodies.undead(body);
            boolean heals = (id == StatusEffects.INSTANT_HEALTH) != undead;
            // The reference's smite pair: health heals the living and
            // damages the undead; damage the mirror.
            if (heals) {
                bodies.heal(body, (int) (proximity * (4 << amplifier) + 0.5));
            } else {
                bodies.hurt(body, (int) (proximity * (6 << amplifier) + 0.5),
                        "was killed by magic", null, true);
            }
        }
    }

    /**
     * The plain instant application at full strength (the drink path —
     * finishUsing's addStatusEffect walk lands the instant effects
     * through the same math at proximity 1.0).
     */
    public void applyInstant(Object body, StatusEffectInstance instance) {
        applyInstant(body, instance.id(), instance.amplifier(), 1.0);
    }

    // ------------------------------------------------------------------
    // The drink (PotionItem.finishUsing lines 71-96)
    // ------------------------------------------------------------------

    /**
     * The drink completion: every effect in the set lands (the timed
     * ones install into the store, the instant ones apply), and the hand
     * keeps a glass bottle back (the reference's addItem).
     */
    public void finishDrinking(Object body, int metadata) {
        for (StatusEffectInstance instance : PotionHelper.getStatusEffects(metadata)) {
            if (StatusEffects.isInstant(instance.id())) {
                applyInstant(body, instance);
            } else {
                add(body, instance.copy());
            }
        }
    }

    // ------------------------------------------------------------------
    // The splash (PotionEntity.onCollision lines 72-106)
    // ------------------------------------------------------------------

    /**
     * The splash application at an impact point: every living body in the
     * 4x2x4 box around the potion takes the effect set scaled by the
     * proximity {@code 1 - sqrt(d2)/4} inside the 16-block reach (the
     * direct-hit body answers 1.0), the instant family through the smite
     * pair, the timed family at {@code d1 * duration + 0.5} ticks with
     * the 20-tick floor.
     *
     * @param directHit the body the potion struck (or null — a block hit).
     * @param thrower   the throwing body (or null — a dispenser or a
     *                  removed thrower): rides the instant-damage arm as
     *                  the {@code DamageSource.magic} attacker — the
     *                  witch's own-splash immunity keys on it.
     */
    public void splash(double x, double y, double z, int metadata, Object directHit,
                       Object thrower) {
        List<StatusEffectInstance> set = PotionHelper.getStatusEffects(metadata);
        if (!set.isEmpty()) {
            // The scan box (onCollision lines 74-75): the potion's own
            // 0.25 cube grown (4, 2, 4) — the half-width 0.125 rides every
            // face of the point box.
            List<Object> nearby = bodies.bodiesIn(x - 4.125, y - 2.125, z - 4.125,
                    x + 4.125, y + 2.125, z + 4.125);
            for (Object body : nearby) {
                double squared = bodies.squaredDistanceTo(body, x, y, z);
                if (squared >= 16.0) {
                    continue;
                }
                double proximity = 1.0 - Math.sqrt(squared) / 4.0;
                if (body == directHit) {
                    proximity = 1.0;
                }
                for (StatusEffectInstance instance : set) {
                    if (StatusEffects.isInstant(instance.id())) {
                        applySplashInstant(body, instance, proximity, thrower);
                    } else {
                        int duration = (int) (proximity * instance.duration() + 0.5);
                        if (duration > 20) {
                            add(body, new StatusEffectInstance(instance.id(), duration,
                                    instance.amplifier(), true));
                        }
                    }
                }
            }
        }
    }

    /**
     * The splash's instant arm (StatusEffect.affectHealth lines 166-180 —
     * the three-arg shape {@code affectHealth(potion, thrower, target)}):
     * the damage flows as {@code DamageSource.magic(potion, thrower)} so
     * the witch's own-splash immunity (attacker == self) applies; the
     * heal flows directly.
     */
    private void applySplashInstant(Object body, StatusEffectInstance instance,
                                    double proximity, Object thrower) {
        int id = instance.id();
        boolean undead = bodies.undead(body);
        boolean heals = (id == StatusEffects.INSTANT_HEALTH) != undead;
        if (heals) {
            bodies.heal(body, (int) (proximity * (4 << instance.amplifier()) + 0.5));
        } else {
            bodies.hurt(body, (int) (proximity * (6 << instance.amplifier()) + 0.5),
                    "was killed by magic", thrower, true);
        }
    }
}
