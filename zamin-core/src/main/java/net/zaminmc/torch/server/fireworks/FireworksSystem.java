package net.zaminmc.torch.server.fireworks;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The firework-rocket system (Slice 65 — the reference port of
 * entity/FireworksEntity.java): the launch (the right-click-on-block arm
 * of FireworksItem.useOn), the flight clock, and the burst.
 *
 * <p><b>The launch (the FireworksEntity ctor, lines 55-73):</b> the body
 * starts at the clicked face's own point (the useOn cursor fractions),
 * the velocity is the near-zero drift (gaussian 0.001 horizontal, the
 * 0.05 lift), and the fuse rolls {@code 10 + rand(6) + rand(7)} — the
 * plain i = 1 band (the item's Flight NBT rides the item-NBT slice,
 * ledgered). The launch sound plays at age 0 through the engine's fx
 * lane.</p>
 *
 * <p><b>The wire (EntityMap lines 90-91: the fireworks tracker's own
 * (64, 10, syncVelocity) registration):</b> the spawn reaches the
 * observers as Spawn Object 76 + the synced slot-8 item stack, the
 * velocity rides the spawn and re-syncs at the tracker's own 10-tick
 * cadence (the client integrates its own copy between the syncs), and
 * the burst is the Entity Status byte 17 followed by the removal.</p>
 *
 * <p><b>Ownership:</b> the registry lives on the engine's tick thread
 * with the projectiles' own confinement — launch, tick, and burst all
 * run on the simulation lane; the listeners fan the wire packets
 * out.</p>
 */
public final class FireworksSystem {

    /** Events the protocol adapter translates into the wire form. */
    public interface Listener {
        /** A rocket launched: Spawn Object 76 + the slot-8 item + the
         * spawn velocity (the tracker's syncVelocity arm). */
        default void onFireworksLaunched(FireworksEntity rocket) { }

        /** The tracker's 10-tick velocity re-sync (EntityMap's interval
         * 10 with syncVelocity — the client's own integration stays
         * aligned with the 1.15/0.04 law). */
        default void onFireworksVelocity(FireworksEntity rocket) { }

        /** The fuse burned out: the Entity Status byte 17 at the rocket
         * (the client renders the burst reading the synced item). */
        default void onFireworksBurst(FireworksEntity rocket) { }

        /** The body left the world (the Destroy Entities). */
        default void onFireworksRemoved(FireworksEntity rocket) { }
    }

    /** The engine surface the system reads (the launch sound at age 0). */
    public interface Surface {
        /** The world-scope sound (the reference's playSound at volume 3.0). */
        void launchSound(Position at);
    }

    private final Surface surface;
    private final Random random;
    private final List<Listener> listeners = new ArrayList<>();
    private final List<FireworksEntity> rockets = new ArrayList<>();
    private final int firstId;
    private int nextId;

    public FireworksSystem(Surface surface, Random random, int firstId) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
        this.firstId = firstId;
        this.nextId = firstId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** The id band the rockets live in. */
    public int firstId() {
        return firstId;
    }

    /** Every live rocket (the join view's walk). */
    public List<FireworksEntity> all() {
        return new ArrayList<>(rockets);
    }

    /** The live count (diagnostics/tests). */
    public int size() {
        return rockets.size();
    }

    /**
     * The launch (FireworksItem.useOn lines 28-38 + the ctor lines 55-73):
     * the rocket starts at the clicked face's point, the fuse rolls, and
     * the spawn's near-zero velocity carries the gaussian drift. The item
     * payload rides the synced slot 8 — the client's own burst reads it.
     * Tick-thread context.
     */
    public FireworksEntity launch(Position at, ItemStack payload) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(payload, "payload");
        FireworksEntity rocket = new FireworksEntity(nextId++, at,
                random.nextGaussian() * FireworksEntity.LAUNCH_DRIFT_SIGMA,
                FireworksEntity.LAUNCH_LIFT,
                random.nextGaussian() * FireworksEntity.LAUNCH_DRIFT_SIGMA,
                rollFuse(), payload);
        rockets.add(rocket);
        for (Listener listener : listeners) {
            listener.onFireworksLaunched(rocket);
        }
        return rocket;
    }

    /**
     * The fuse roll (the ctor line 73): {@code 10 * i + rand(6) +
     * rand(7)} with the plain i = 1 — the item's Flight NBT byte rides
     * the item-NBT slice (no engine model yet; ledgered).
     */
    private int rollFuse() {
        return FireworksEntity.FUSE_BASE_TICKS
                + random.nextInt(6) + random.nextInt(7);
    }

    /**
     * The restored launch (the anvil load path — the reference's
     * FireworksEntity.readCustomNbt, lines 151-163): the SAVED Life
     * resumes the age clock (the loaded rocket bursts after the
     * REMAINING ticks, never a fresh roll — the reference's readNbt
     * never re-derives the fuse), the LifeTime row carries the whole
     * fuse, the loaded Motion lands whole (no fresh gaussian drift), and
     * the FireworksItem row becomes the payload (an absent row leaves
     * the empty stack — the reference's null synced slot 8, counted
     * ledger note). The listeners fire the same launch publications the
     * live path carries (the Spawn Object 76 + the slot-8 item). The
     * launch sound's age-0 gate in the tick owns the restored form's
     * own quirk: a loaded rocket at Life &gt; 0 never sounds — the
     * reference's own age==0 check, 1:1. Tick-thread context.
     */
    public FireworksEntity launchRestored(Position at, double vx, double vy, double vz,
                                          int restoredAge, int lifeTime, ItemStack payload) {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(payload, "payload");
        FireworksEntity rocket = new FireworksEntity(nextId++, at, vx, vy, vz,
                restoredAge, lifeTime, payload);
        rockets.add(rocket);
        for (Listener listener : listeners) {
            listener.onFireworksLaunched(rocket);
        }
        return rocket;
    }

    /**
     * One flight step over every rocket (tick lines 76-127): the launch
     * sound at age 0, the velocity growth + the move, the tracker's
     * 10-tick velocity re-sync, and the burst (the entity event 17) with
     * the removal when the fuse burns out. Tick-thread context.
     */
    public void tick() {
        Iterator<FireworksEntity> iterator = rockets.iterator();
        while (iterator.hasNext()) {
            FireworksEntity rocket = iterator.next();
            if (rocket.age() == 0) {
                surface.launchSound(rocket.position());
            }
            rocket.advance();
            if (rocket.age() % 10 == 0) {
                // The tracker's own cadence (EntityMap's interval 10): the
                // velocity re-sync keeps the client's integration honest.
                for (Listener listener : listeners) {
                    listener.onFireworksVelocity(rocket);
                }
            }
            if (rocket.expired()) {
                for (Listener listener : listeners) {
                    listener.onFireworksBurst(rocket);
                    listener.onFireworksRemoved(rocket);
                }
                iterator.remove();
            }
        }
    }
}
