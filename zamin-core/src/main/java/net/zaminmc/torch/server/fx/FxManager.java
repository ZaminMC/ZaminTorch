package net.zaminmc.torch.server.fx;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

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
 *   <li><b>Engine-side, semantic:</b> the engine says "a stone block shattered
 *       at (x,y,z)" and the 1.8 adapter decides that this means the World
 *       Particles 0x2B packet {@code blockcrack} carrying the block's legacy
 *       state id, plus the dig sound from the block-sound map. The wire's
 *       numeric particle ids and legacy ids never cross into the engine.</li>
 *   <li><b>Named sounds, not palette ids:</b> 1.8 resolves sound names to
 *       resources client-side, so the engine carries the historical 1.8 sound
 *       name strings verbatim (the same choice the mob chatter made).</li>
 *   <li><b>Fire-and-forget:</b> FX are cosmetic; a copy-on-write listener list
 *       keeps the bus lock-free, and every event is best-effort — nothing in
 *       the simulation depends on the wire having rendered it.</li>
 * </ul>
 */
public final class FxManager {

    private static final Logger LOGGER = Logger.getLogger(FxManager.class.getName());

    /** One broadcast unit. The adapter translates each kind per its version. */
    public sealed interface FxEvent permits Sound, BlockShatter, ItemShatter, Poof {
    }

    /** A named sound at a position (historical 1.8 resource name). */
    public record Sound(Position position, String name, float volume, float pitch)
            implements FxEvent {
        public Sound {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(name, "name");
        }
    }

    /** A block shattered into shards (the mining break, the land impact). */
    public record BlockShatter(Position position, BlockType block) implements FxEvent {
        public BlockShatter {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }

    /** An item shattered into crumbs (the eat bite, the tool's death). */
    public record ItemShatter(Position position, ItemStack stack) implements FxEvent {
        public ItemShatter {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(stack, "stack");
        }
    }

    /** The white puff (the snowball's splat, the egg's miss). */
    public record Poof(Position position) implements FxEvent {
        public Poof {
            Objects.requireNonNull(position, "position");
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

    /** Emits a block-shatter burst. Any thread. */
    public void blockShatter(Position position, BlockType block) {
        emit(new BlockShatter(position, block));
    }

    /** Emits an item-shatter crumb burst. Any thread. */
    public void itemShatter(Position position, ItemStack stack) {
        emit(new ItemShatter(position, stack));
    }

    /** Emits the white shatter puff. Any thread. */
    public void poof(Position position) {
        emit(new Poof(position));
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
