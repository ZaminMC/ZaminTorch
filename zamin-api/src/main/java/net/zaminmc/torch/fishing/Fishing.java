package net.zaminmc.torch.fishing;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * The fishing system's plugin-facing surface (§27.3's mechanics family —
 * the Slice 67/69 exposure over {@code server/fishing/FishingSystem},
 * the reference port of {@code entity/FishingBobberEntity.java} +
 * {@code item/FishingRodItem.java}): the live bobber census, the
 * catch-context rows, and the §27.7 catch interception family — reached
 * through {@link Server#fishing()}.
 *
 * <p><b>What a plugin can OBSERVE:</b> the bobber census — whose bobber
 * is where in which state. Every angler holds at most one bobber (the
 * reference's own {@code player.fishingBobber} shape), and each census
 * row carries the reference's own state model: the position and velocity
 * (the flight's 0.92-drag walk and the water's buoyancy law), the
 * {@link Bobber#grounded() grounded} flag (the block raytrace's stuck
 * bobber — the 1200-tick timeout's own state), the {@link Bobber#hooked()
 * hooked} flag (a body on the line — the entity clip's first collidable
 * catch), and the {@link Bobber#biting() biting} flag (the splash opened
 * THE BITE WINDOW — the reference's 10..30-tick window inside which the
 * retrieve lands a catch). The cast and the retrieve themselves are the
 * use path's own verbs (a player uses a rod — the engine's
 * {@code FishingRodItem.startUsing} arm); a plugin reads the state the
 * walk produces, it does not fish for the player.</p>
 *
 * <p><b>What a plugin can QUERY:</b> the catch-context data the system
 * already computes — {@link CatchContext} is the same row the engine
 * hands its own interception walk: the angler, the bobber's position,
 * the loot band the roll landed in ({@link LootCategory} — the
 * reference's three: the 0.1-band junk eleven, the 0.05-band treasure
 * six, the fish remainder), the held rod's luck and lure levels (the
 * same enchant reads the vanilla roll sees), and the caught stack.</p>
 *
 * <p><b>What a plugin can INTERCEPT / REPLACE:</b> the engine's own
 * §27.7 seams, registered here exactly as the engine accepts them:
 * <ul>
 *   <li>{@link #addCatchInterceptor(CatchInterceptor)} — the catch hook:
 *       it runs between the loot roll and the drop, in registration
 *       order; each interceptor sees the incoming stack (an earlier
 *       interceptor's replacement included) and answers the outgoing
 *       one — return the incoming stack to pass through, another stack
 *       to replace (the drop and the XP ride the replacement), or
 *       {@link ItemStack#EMPTY} to cancel the whole catch (no item, no
 *       XP, and the retrieve answers the bare 0 — the rod does not
 *       wear, the reference's own {@code result > 0} gate).</li>
 *   <li>{@link #setLootSource(LootSource)} — the loot override (the
 *       replaceable strategy, the dispense behaviors' register/reset
 *       family): answers a stack per band, or {@code null} to fall
 *       through to the vanilla weighted roll for that band — the
 *       override composes with the reference's own law instead of
 *       silently replacing the whole walk. {@code setLootSource(null)}
 *       restores the vanilla tables.</li>
 * </ul></p>
 *
 * <p><b>Threading (§27.6):</b> the census reads answer immutable
 * point-in-time snapshots safe from any thread — each copy is taken
 * under the engine system's monitor, so an off-lane read reflects the
 * census at some instant between ticks, never a half-written walk. The
 * registration verbs are safe from any thread (the same monitor
 * serializes them against the walk — a registration lands between ticks
 * or between retrieves, never inside one). The hooks themselves fire on
 * the simulation owner's lane inside the retrieve — the same context
 * the engine's own walk runs on; a hook that blocks stalls the tick
 * lane (the §28 untrusted-caller contract). One instance registered
 * twice through the view is a no-op the second time (the removal's
 * symmetric answer); register distinct instances per behavior.</p>
 */
public interface Fishing {

    /** The empty answer (an unbooted server's view: no bobbers, and the
     *  registrations are dropped — the system the server boots carries
     *  its own; register after boot). */
    Fishing EMPTY = new Fishing() {
        @Override public List<Bobber> bobbers() {
            return List.of();
        }

        @Override public Optional<Bobber> bobberOf(int anglerId) {
            return Optional.empty();
        }

        @Override public void addCatchInterceptor(CatchInterceptor interceptor) {
        }

        @Override public boolean removeCatchInterceptor(CatchInterceptor interceptor) {
            return false;
        }

        @Override public void setLootSource(LootSource source) {
        }
    };

    /**
     * @return a point-in-time copy of every live bobber (the census's
     *         own order), each row an immutable snapshot of the
     *         reference's own state model — safe from any thread
     */
    List<Bobber> bobbers();

    /**
     * One angler's live bobber (the reference's one-bobber-per-player
     * shape).
     *
     * @param anglerId the angler's engine entity id
     * @return the bobber's snapshot row, or empty when the angler's
     *         line is not in the water (no cast, or the retrieve/leash
     *         already ended it)
     */
    Optional<Bobber> bobberOf(int anglerId);

    /**
     * Registers a catch interceptor (the §27.7 observe/replace/cancel
     * hook between the loot roll and the drop). Safe from any thread
     * (the monitor protocol — it lands between ticks, never inside a
     * retrieve); the hook fires on the simulation owner's lane. The
     * same instance is registered once (a second add is a no-op).
     *
     * @param interceptor the hook (EMPTY or {@code null} from the hook
     *                    cancels the whole catch: no drop, no XP, the
     *                    bare-0 retrieve — the rod never wears)
     */
    void addCatchInterceptor(CatchInterceptor interceptor);

    /**
     * Removes a catch interceptor.
     *
     * @param interceptor the instance registered through this view
     * @return whether it was registered
     */
    boolean removeCatchInterceptor(CatchInterceptor interceptor);

    /**
     * The loot override (the §27.7 replaceable strategy): the source
     * answers a stack per band before the vanilla weighted roll runs —
     * answer {@code null} to fall through to the band's own table, so
     * the override composes with the reference's law. Safe from any
     * thread (the monitor protocol).
     *
     * @param source the override, or {@code null} to restore the
     *               vanilla tables
     */
    void setLootSource(LootSource source);

    /**
     * One bobber's census row — the reference's own state model
     * ({@code FishingBobberEntity}'s own field set), an immutable
     * point-in-time snapshot.
     *
     * @param entityId   the bobber's engine entity id (the Spawn
     *                   Object 90 identity — the client draws the
     *                   line from its objectData)
     * @param anglerId   the angler's engine entity id (the bobber's
     *                   thrower — one bobber per angler, the
     *                   reference's {@code player.fishingBobber} shape)
     * @param at         the bobber's position (the flight's own walk —
     *                   the drag, the water's 5-band buoyancy)
     * @param velocityX  the velocity's x
     * @param velocityY  the velocity's y (the bite window's own bob —
     *                   the {@code vy -= 0.2} splash and the jitter)
     * @param velocityZ  the velocity's z
     * @param grounded   the block raytrace stuck the bobber in a block
     *                   (the 1200-tick timeout's own state — the
     *                   retrieve answers 2)
     * @param biting    the splash opened THE BITE WINDOW (the
     *                   reference's 10..30-tick window — the retrieve
     *                   inside it lands the catch)
     * @param hooked    a body rides the line (the entity clip's first
     *                   collidable catch — the retrieve drags it, and
     *                   the row stays anchored to the body)
     */
    record Bobber(int entityId, int anglerId, Position at,
                  double velocityX, double velocityY, double velocityZ,
                  boolean grounded, boolean biting, boolean hooked) {
    }

    /**
     * The loot band the roll landed in — the reference's own three
     * (getResult lines 487-506: the junk/treasure/fish walk).
     */
    enum LootCategory {
        /** The 0.1-band (the junk eleven: the 0.9-damaged boots and
         *  rod, the leather/bone/potion/string/bowl/stick/ink
         *  sac/tripwire hook/rotten flesh). */
        JUNK,
        /** The 0.05-band (the treasure six: the lily pad, name tag,
         *  saddle, the 0.25-damaged bow and rod, the book). */
        TREASURE,
        /** The fish remainder (the 60/25/2/13 four: cod, salmon,
         *  clownfish, pufferfish). */
        FISH
    }

    /**
     * The catch's own context — the same row the engine hands its
     * interception walk (the roll's outcome plus the angler's state).
     * Immutable; carries no engine references.
     *
     * @param anglerId  the angler's engine entity id
     * @param bobberAt  the bobber's position at the catch
     * @param category  the band the roll landed in
     * @param luckLevel the held rod's Luck of the Sea level (the roll's
     *                  own input — the junk cut, the treasure lift)
     * @param lureLevel the held rod's Lure level (the roll's own input
     *                  — the per-band lure cut; the approach arming's
     *                  own {@code 100..900 - Lure * 100} cut beside it)
     * @param caught    the incoming stack (the roll's answer, or an
     *                  earlier interceptor's replacement)
     */
    record CatchContext(int anglerId, Position bobberAt, LootCategory category,
                        int luckLevel, int lureLevel, ItemStack caught) {
    }

    /**
     * The catch intercept (§27.7's observe + replace/cancel hook): runs
     * on the simulation owner's lane between the loot roll and the
     * drop, in registration order — each interceptor sees the incoming
     * stack (an earlier interceptor's replacement included) and answers
     * the outgoing one.
     *
     * <ul>
     *   <li>return the incoming stack (or an equal one) — pass
     *       through;</li>
     *   <li>return another stack — replace (the drop and the XP ride
     *       the replacement; the XP amount is the vanilla roll);</li>
     *   <li>return {@link ItemStack#EMPTY} — cancel the catch: no item,
     *       no XP, and the retrieve answers the bare 0 (the rod does
     *       not wear — the reference's own {@code result > 0} gate).</li>
     * </ul>
     */
    interface CatchInterceptor {
        /** @return the outgoing stack, or EMPTY to cancel the catch. */
        ItemStack onCatch(CatchContext context);
    }

    /**
     * The loot override (§27.7's replaceable strategy): consulted per
     * band before the vanilla weighted roll, on the simulation owner's
     * lane inside the retrieve. Answering {@code null} falls through to
     * the reference's own table for that band — the override composes
     * with the vanilla law instead of silently replacing the whole
     * walk. The luck and lure levels are the held rod's own reads (the
     * same inputs the vanilla roll sees).
     */
    interface LootSource {
        /**
         * @param category the band the roll landed in
         * @param luck     the angler's Luck of the Sea level
         * @param lure     the angler's Lure level
         * @param random   the walk's own random (the seeded stream —
         *                 the override shares the engine's
         *                 reproducibility)
         * @return the caught stack, or {@code null} to use the band's
         *         vanilla weighted roll
         */
        ItemStack roll(LootCategory category, int luck, int lure, Random random);
    }
}
