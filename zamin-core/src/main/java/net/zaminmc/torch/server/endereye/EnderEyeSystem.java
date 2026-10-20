package net.zaminmc.torch.server.endereye;

import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The eye-of-ender family (the reference's EnderEyeEntity + the item's
 * startUsing arm, EnderEyeItem lines 118-136): the throw spawns the eye
 * AT the player's own position (the reference's {@code new
 * EnderEyeEntity(world, player.x, player.y, player.z)} — the eye's
 * launch velocity comes from the steering's first pass, never a throw
 * vector), the target rides the set (the 12-block clamp law), the
 * {@code random.bow} throw sound at 0.5 with the 0.4/(rand*0.4+0.8)
 * pitch follows, and the 80-tick flight ends in the 4-in-5 item drop
 * (the reference's {@code nextInt(5) > 0}) or the block event 2003.
 *
 * <p>The tracker's own cadence (the reference's EntityMap line 82: the
 * eye at (64, 4, true) — a 4-tick interval with the velocity sync)
 * rides the listener's velocity publications.</p>
 */
public final class EnderEyeSystem {

    /** Events the protocol adapter translates into the wire form. */
    public interface Listener {
        /** An eye launched: Spawn Object 23 + the velocity (the tracker's syncVelocity arm). */
        default void onEyeLaunched(EnderEyeEntity eye) { }

        /** The tracker's 4-tick velocity re-sync (EntityMap's interval 4). */
        default void onEyeVelocity(EnderEyeEntity eye) { }

        /** The flight ended in the item drop: the item spawn publication carries it. */
        default void onEyeDropped(EnderEyeEntity eye) { }

        /** The flight ended in the block event 2003 (the 1-in-5 form). */
        default void onEyeSmokeEnded(EnderEyeEntity eye) { }

        /** The body left the world (the Destroy Entities). */
        default void onEyeRemoved(EnderEyeEntity eye) { }
    }

    /** The engine surface the system reads. */
    public interface Surface {
        /** The throw sound (the reference's random.bow at 0.5, the 0.4/(rand*0.4+0.8) pitch). */
        void throwSound(Position at, Random random);

        /** The item drop at the eye's end (the fresh ENDER_EYE stack at the eye's position — the exact-position form; the item's own spawn publication flows through the item manager). */
        void dropEyeItem(Position at, net.zaminmc.torch.item.ItemStack stack);
    }

    private final Surface surface;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<EnderEyeEntity> eyes = new ArrayList<>();
    private final int firstId;
    private int nextId;

    public EnderEyeSystem(Surface surface, Random random, int firstId) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
        this.firstId = firstId;
        this.nextId = firstId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The id band the eyes live in. */
    public int firstId() {
        return firstId;
    }

    /** Every live eye (the join view's walk). */
    public List<EnderEyeEntity> all() {
        return new ArrayList<>(eyes);
    }

    /**
     * The throw (the item's startUsing arm, EnderEyeItem lines 118-136):
     * the eye spawns AT the caller's position, the target set rides the
     * stronghold answer (the 12-block clamp + the drops roll inside),
     * the throw sound follows, and the consume is the caller's own arm.
     * Tick-thread context.
     */
    public EnderEyeEntity launch(Position at, double targetX, double targetY, double targetZ) {
        Objects.requireNonNull(at, "at");
        EnderEyeEntity eye = new EnderEyeEntity(nextId++, at, 0.0, 0.0, 0.0);
        eye.setTarget(targetX, targetY, targetZ, random);
        eyes.add(eye);
        for (Listener listener : listeners) {
            listener.onEyeLaunched(eye);
        }
        surface.throwSound(at, random);
        return eye;
    }

    /**
     * The restored launch (the anvil load path — the reference's
     * readCustomNbt is EMPTY, EnderEyeEntity lines 165-169: the eye
     * carries no rows of its own; the base Entity.readNbt rows —
     * Pos/Rotation/Motion — are the only ones). The loaded eye's target
     * is the ORIGIN (the transient fields' default — the reference's
     * load quirk: a saved eye flies toward (0, 0, 0) until its 80 ticks
     * end) and its drops row stays false (the roll only rides the live
     * setTarget — a loaded eye never drops, the event 2003 always ends
     * it; the counted ledger note).
     */
    public EnderEyeEntity launchRestored(Position at, double vx, double vy, double vz) {
        Objects.requireNonNull(at, "at");
        EnderEyeEntity eye = new EnderEyeEntity(nextId++, at, vx, vy, vz);
        eyes.add(eye);
        for (Listener listener : listeners) {
            listener.onEyeLaunched(eye);
        }
        return eye;
    }

    /**
     * One flight step over every eye (the reference's tick): the
     * steering + the lifespan advance, the 4-tick velocity re-sync, and
     * the end (the drop or the event 2003) with the removal at 80.
     * Tick-thread context.
     */
    public void tick() {
        Iterator<EnderEyeEntity> iterator = eyes.iterator();
        while (iterator.hasNext()) {
            EnderEyeEntity eye = iterator.next();
            eye.advance();
            if (eye.lifespan() % 4 == 0) {
                // The tracker's own cadence (EntityMap's interval 4).
                for (Listener listener : listeners) {
                    listener.onEyeVelocity(eye);
                }
            }
            if (eye.expired()) {
                if (eye.dropsItem()) {
                    for (Listener listener : listeners) {
                        listener.onEyeDropped(eye);
                    }
                    surface.dropEyeItem(eye.position(), eye.dropStack());
                } else {
                    // The 1-in-5 end: the block event 2003 (the reference's
                    // world.doEvent(2003, pos, 0)) — the listener's own wire
                    // publication carries it.
                    for (Listener listener : listeners) {
                        listener.onEyeSmokeEnded(eye);
                    }
                }
                for (Listener listener : listeners) {
                    listener.onEyeRemoved(eye);
                }
                iterator.remove();
            }
        }
    }
}
