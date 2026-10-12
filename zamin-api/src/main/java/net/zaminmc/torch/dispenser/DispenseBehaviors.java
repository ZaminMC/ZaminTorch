package net.zaminmc.torch.dispenser;

import net.zaminmc.torch.item.ItemType;

import java.util.Optional;

/**
 * The dispenser's behavior registry (§27.3's registration surface over
 * the engine's per-item behavior table — the public stand-in for the
 * reference's {@code Bootstrap.registerDispenseBehaviors}):
 *
 * <ul>
 *   <li>{@link #register} overrides the built-in behavior for an item (or
 *       adds one where only the default item drop answered);</li>
 *   <li>{@link #reset} returns an item to its built-in answer (or to the
 *       default drop when it had none) — the replace verb's way back;</li>
 *   <li>{@link #behaviorOf} inspects the effective custom registration
 *       (empty when the item answers to a built-in or the default drop —
 *       the built-ins are not addressable as public objects).</li>
 * </ul>
 *
 * <p><b>Threading (§27.6 — the explicit command protocol):</b>
 * {@code register} and {@code reset} are safe from any thread: each is
 * admitted onto the simulation owner's lane and lands between ticks,
 * never inside a dispense walk. The verbs carry no synchronous verdict —
 * {@link #behaviorOf} is the confirmation channel. {@code behaviorOf}
 * itself is a point-in-time read, safe from any thread.</p>
 */
public interface DispenseBehaviors {

    /**
     * Registers the behavior the item dispenses with. A registration for
     * an item with a built-in behavior overrides it; a registration for
     * any other item replaces the default item drop for that item.
     * Admitted onto the owner lane — see the interface contract.
     */
    void register(ItemType item, DispenseBehavior behavior);

    /**
     * Returns the item to its built-in behavior (the projectile family,
     * the buckets, the TNT…) or to the default item drop when it had
     * none — the way back from an override. Admitted onto the owner lane.
     */
    void reset(ItemType item);

    /**
     * The custom behavior registered for the item, when one stands.
     * Empty when the item answers to a built-in behavior or to the
     * default item drop — the built-in behaviors are engine-internal
     * and not addressable as public objects (ledgered omission; the
     * wrap-composition verb rides a later wave). Point-in-time read;
     * safe from any thread.
     */
    Optional<DispenseBehavior> behaviorOf(ItemType item);
}
