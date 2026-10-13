package net.zaminmc.torch.entity.ai;

import net.zaminmc.torch.entity.LivingEntity;

/**
 * One goal of a mob's prioritized mind — the public registration contract
 * (§27.3's mob-AI family: register and compose goals through the API).
 *
 * <p>Goals are stateless decisions over the live body: each mind tick the
 * selector walks the goal set by priority (lower numbers run first), lets
 * every willing additive goal contribute, and stops at the first willing
 * exclusive goal that claims the body. A goal that wants the body returns
 * {@code true} from {@link #canUse} and then drives it from
 * {@link #tick}.</p>
 *
 * <p><b>Threading (§27.6):</b> {@code canUse} and {@code tick} run on the
 * entity's simulation owner context, between the mind's own phases — the
 * same lane the engine's built-in goals run on. The body reference is the
 * live authoritative handle: every call through it resolves to current
 * state, and its mutation methods carry their own ownership contract (the
 * documented owner path is {@code World.execute}; the goal lane IS that
 * lane, so goal bodies may drive the mob directly). Goals themselves must
 * be stateless or internally synchronized; the engine never calls the same
 * goal concurrently with itself.</p>
 *
 * <p><b>Exception policy:</b> a throwing {@code canUse} or {@code tick} is
 * logged and skipped for that tick — the mind falls through to the next
 * willing goal and the simulation never dies.</p>
 */
public interface Goal {

    /**
     * The priority band: lower runs first. The built-in family occupies
     * 0 (float), 1 (panic), 2 (breed), 3 (the awareness band — beg, tempt,
     * flee_player: kind-gated to the wolf, the ocelot, and the rabbit,
     * idle on every other kind), and 4 (the rabbit's kind-gated garden
     * raid and the stroll); a custom goal that must outrank the stroll
     * joins band 3 (after the built-ins, in join order), one that yields
     * to every built-in sits at 5 or higher.
     */
    int priority();

    /**
     * Exclusive goals stop the selector's walk when they run; additive
     * goals contribute beside whichever exclusive goal (if any) claims
     * the body. The float goal is the reference's additive shape.
     */
    boolean exclusive();

    /**
     * @param mob  the live body handle (the engine object is the handle,
     *             §27.5 — every call resolves to authoritative state)
     * @param night whether the body's world is in its night band (the
     *             historic flag the reference's goal selectors pass)
     * @return whether this goal wants the body this tick
     */
    boolean canUse(LivingEntity mob, boolean night);

    /**
     * Drives the body for one tick. Runs only when {@link #canUse} answered
     * true on this same tick.
     */
    void tick(LivingEntity mob, boolean night);
}
