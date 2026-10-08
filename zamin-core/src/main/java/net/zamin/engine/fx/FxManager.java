package net.zamin.engine.fx;

import net.zamin.api.Position;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The game-feedback bus: semantic sound and particle events the engine's
 * systems emit, fanned out to the protocol adapter for the wire.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li><b>Engine-side, semantic:</b> the engine says "a stone block broke at
 *       (x,y,z)" through {@link #blockCrack}; the 1.8 adapter decides that this
 *       means the World Particles 0x2B packet {@code blockcrack} with the
 *       block's legacy state id, and the dig sound rides the block-sound map.
 *       Version-neutral core, version-bound translation.</li>
 *   <li><b>Fire-and-forget:</b> FX are cosmetic; a copy-on-write listener list
 *       keeps the bus lock-free, and every event is best-effort — nothing in
 *       the simulation depends on the wire having rendered it.</li>
 *   <li><b>Named, not positioned-palette:</b> 1.8 resolves sound names to
 *       resources client-side, so the engine carries the historical 1.8 sound
 *       name strings verbatim (the same choice the mob chatter made).</li>
 * </ul>
 */
public final class FxManager {

    private static final Logger LOGGER = Logger.getLogger(FxManager.class.getName());

    /** One broadcast unit: either a sound or a particle burst. */
    public sealed interface FxEvent permits Sound, Particles {
    }

    /** A named sound at a position (historical 1.8 resource name). */
    public record Sound(Position position, String name, float volume, float pitch)
            implements FxEvent {
        public Sound {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * A particle burst. {@code id} is the protocol's numeric particle id —
     * a wire-specific datum, but the simplest honest shape: the engine owns a
     * handful of symbolic constants (declared below) and the adapter
     * validates its version knows each one before writing. {@code data}
     * carries the per-particle extra payload (block/item ids for the crack
     * families); empty for the self-colored kinds.
     */
    public record Particles(Position position, int id, boolean longDistance,
                            float offsetX, float offsetY, float offsetZ,
                            float speed, int count, int[] data)
            implements FxEvent {
        /** The white puff of a projectile or snowball shattering. */
        public static final int SNOWBALL_POOF = 31;
        /** The red-flecked crit starburst. */
        public static final int CRIT = 9;
        /** The floating heart (regeneration, taming). */
        public static final int HEART = 34;
        /** Item break crumbs: data = [itemId, itemMeta]. */
        public static final int ICON_CRACK = 36;
        /** Block break shards: data = [blockStateId]. */
        public static final int BLOCK_CRACK = 37;
        /** The gray smoke wisp (torch snuff, failed spawn). */
        public static final int SMOKE = 11;

        public Particles {
            Objects.requireNonNull(position, "position");
            data = data != null ? data.clone() : new int[0];
        }
    }

    /** Receives every FX event. The adapter registers one; any thread. */
    public interface Listener {
        void onFx(FxEvent event);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /** Registers the wire observer. Idempotent per listener instance. */
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Emits a named sound at a position. Any thread; best-effort. */
    public void sound(Position position, String name, float volume, float pitch) {
        emit(new Sound(position, name, volume, pitch));
    }

    /** Emits a sound at full volume, default pitch. Any thread. */
    public void sound(Position position, String name) {
        emit(new Sound(position, name, 1.0f, 1.0f));
    }

    /** Emits a block-crack burst for a block state (legacy id | meta &lt;&lt; 12). */
    public void blockCrack(Position position, int blockStateId) {
        emit(new Particles(position, Particles.BLOCK_CRACK, false,
                0.4f, 0.4f, 0.4f, 0.3f, 20, new int[]{blockStateId}));
    }

    /** Emits an item-crack burst for an item (legacy id + variant meta). */
    public void itemCrack(Position position, int itemId, int itemMeta) {
        emit(new Particles(position, Particles.ICON_CRACK, false,
                0.3f, 0.3f, 0.3f, 0.15f, 8, new int[]{itemId, itemMeta}));
    }

    /** Emits the snowball shatter puff. */
    public void snowballPoof(Position position) {
        emit(new Particles(position, Particles.SNOWBALL_POOF, false,
                0.2f, 0.2f, 0.2f, 0.2f, 8, new int[0]));
    }

    private void emit(FxEvent event) {
        for (Listener listener : listeners) {
            try {
                listener.onFx(event);
            } catch (Throwable t) {
                // FX must never take the simulation down: log and keep going.
                LOGGER.warning("FX listener failed: " + t);
            }
        }
    }
}
