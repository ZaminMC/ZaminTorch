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
 *   <li><b>The dimension rides the event (Wave TF-12 — the reference's
 *       "the World the sound is played on"):</b> vanilla's
 *       {@code World.playSound} iterates the listener list of ONE World —
 *       the listener resolves {@code this.world.dimension.getId()} and
 *       PlayerManager.sendPacket refuses every observer standing in another
 *       dimension (PlayerManager lines 630-640). The engine's bus is
 *       world-agnostic, so every event carries the emitting world's
 *       dimension and the wire's fan gates on it: a nether fireball's
 *       detonation never thunders in the overworld, an overworld jukebox
 *       never plays for a nether observer at the same coordinates. The
 *       no-dimension emit overloads stamp the overworld 0 — the
 *       overworld-bound block systems' own binding (the TF-9 dispenser
 *       convention: the family's world, ledged, never a silent wrong
 *       stamp).</li>
 * </ul>
 */
public final class FxManager {

    private static final Logger LOGGER = Logger.getLogger(FxManager.class.getName());

    /** One broadcast unit. The adapter translates each kind per its version. */
    public sealed interface FxEvent permits Sound, SoundExcept, BlockShatter, StandShatter, ItemShatter, Poof, Smoke, PotionSplash, WorldEvent {

        /**
         * The dimension the event plays in (Wave TF-12 — the reference's
         * {@code this.world.dimension.getId()} on the listener's send): the
         * wire's fan refuses every observer standing elsewhere.
         */
        int dimension();
    }

    /** A named sound at a position (historical 1.8 resource name). */
    public record Sound(Position position, String name, float volume, float pitch,
                        int dimension)
            implements FxEvent {
        public Sound {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * A named sound one session never hears — the reference's
     * {@code playSound(Entity except, String, float, float)} overload
     * (GiveCommand's pop at the target: everyone nearby except the
     * granted player). Same range law and packet as {@link Sound}; the
     * exempt session's own observer is skipped.
     */
    public record SoundExcept(Position position, String name, float volume, float pitch,
                              net.zaminmc.torch.server.player.PlayerSession exempt,
                              int dimension)
            implements FxEvent {
        public SoundExcept {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(exempt, "exempt");
        }
    }

    /** A block shattered into shards (the mining break, the land impact). */
    public record BlockShatter(Position position, BlockType block, int dimension)
            implements FxEvent {
        public BlockShatter {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }

    /**
     * The armor stand's breaking burst (the reference's addBreakingParticles:
     * BLOCK_DUST over the PLANKS state — count 10, the stand-box offsets
     * 0.125 × 0.494 × 0.125, speed 0.05, the position at
     * {@code y + 1.975 / 1.5}).
     */
    public record StandShatter(Position position, int dimension) implements FxEvent {
        public StandShatter {
            Objects.requireNonNull(position, "position");
        }
    }

    /** An item shattered into crumbs (the eat bite, the tool's death). */
    public record ItemShatter(Position position, ItemStack stack, int dimension)
            implements FxEvent {
        public ItemShatter {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(stack, "stack");
        }
    }

    /** The white puff (the snowball's splat, the egg's miss). */
    /** The splash potion's burst (the 2002 world event, data = the potion value). */
    public record PotionSplash(Position position, int potionData, int dimension)
            implements FxEvent {
        public PotionSplash {
            java.util.Objects.requireNonNull(position, "position");
        }
    }

    /**
     * The reference's own world event (World.doEvent over the 64-block
     * event radius): the wire's WorldEvent packet verbatim — the event id
     * and its data int (the dispense smoke's 2000 carrying the facing
     * index, the bone meal's 2005 burst with the 0 that the client turns
     * into fifteen happy-villager sparks).
     */
    public record WorldEvent(Position position, int eventId, int data, int dimension)
            implements FxEvent {
        public WorldEvent {
            Objects.requireNonNull(position, "position");
        }
    }

    public record Poof(Position position, int dimension) implements FxEvent {
        public Poof {
            Objects.requireNonNull(position, "position");
        }
    }

    /** The smoke burst (Slice 54 — the eye's insertion: SMOKE_NORMAL at
     * the frame, one particle per the reference's own sixteen-particle
     * band at the 5/16..11/16 offsets). */
    public record Smoke(Position position, int dimension) implements FxEvent {
        public Smoke {
            Objects.requireNonNull(position, "position");
        }
    }

    /** Receives every FX event. The adapter registers one; any thread. */
    public interface Listener {
        void onFx(FxEvent event);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * The overworld stamp the no-dimension emit overloads carry (Wave
     * TF-12): the engine's block-bound FX systems (the dispenser, the
     * redstone family, the fluids, the note blocks, the TNT, the block
     * layer's own break/place commits) are overworld-bound today — their
     * sounds are overworld sounds, the same ledged family binding the
     * TF-9 dispenser arms carry. Sites with a live context (sessions,
     * mobs, projectiles, vehicles, the explosion's dimension row) stamp
     * the real dimension through the carrying forms.
     */
    private static final int OVERWORLD = 0;

    /** Registers the wire observer. Idempotent per listener instance. */
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Emits a named sound at a position in the given dimension (the
     * reference's {@code World.playSound(x,y,z,...)} — the World the sound
     * plays on). Any thread; best-effort.
     */
    public void sound(Position position, String name, float volume, float pitch,
                      int dimension) {
        emit(new Sound(position, name, volume, pitch, dimension));
    }

    /**
     * Emits a named sound at a position. Any thread; best-effort.
     *
     * @see #sound(Position, String, float, float, int)
     */
    public void sound(Position position, String name, float volume, float pitch) {
        emit(new Sound(position, name, volume, pitch, OVERWORLD));
    }

    /** Emits a sound at full volume, default pitch. Any thread. */
    public void sound(Position position, String name) {
        emit(new Sound(position, name, 1.0f, 1.0f, OVERWORLD));
    }

    /**
     * Emits a named sound one session never hears (the reference's
     * playSound(Entity except, ...) overload) in the given dimension.
     * Any thread; best-effort.
     */
    public void soundExcept(Position position, String name, float volume, float pitch,
                            net.zaminmc.torch.server.player.PlayerSession exempt,
                            int dimension) {
        emit(new SoundExcept(position, name, volume, pitch, exempt, dimension));
    }

    /**
     * Emits a named sound one session never hears (the reference's
     * playSound(Entity except, ...) overload). Any thread; best-effort.
     *
     * @see #soundExcept(Position, String, float, float, PlayerSession, int)
     */
    public void soundExcept(Position position, String name, float volume, float pitch,
                            net.zaminmc.torch.server.player.PlayerSession exempt) {
        emit(new SoundExcept(position, name, volume, pitch, exempt, OVERWORLD));
    }

    /** Emits a block-shatter burst in the given dimension. Any thread. */
    public void blockShatter(Position position, BlockType block, int dimension) {
        emit(new BlockShatter(position, block, dimension));
    }

    /** Emits a block-shatter burst. Any thread.
     *
     * @see #blockShatter(Position, BlockType, int) */
    public void blockShatter(Position position, BlockType block) {
        emit(new BlockShatter(position, block, OVERWORLD));
    }

    /** Emits the armor stand's breaking burst (addBreakingParticles' own shape)
     * in the given dimension. Any thread. */
    public void standShatter(Position position, int dimension) {
        emit(new StandShatter(position, dimension));
    }

    /** Emits the armor stand's breaking burst (addBreakingParticles' own shape). Any thread.
     *
     * @see #standShatter(Position, int) */
    public void standShatter(Position position) {
        emit(new StandShatter(position, OVERWORLD));
    }

    /** Emits an item-shatter crumb burst in the given dimension. Any thread. */
    public void itemShatter(Position position, ItemStack stack, int dimension) {
        emit(new ItemShatter(position, stack, dimension));
    }

    /** Emits an item-shatter crumb burst. Any thread.
     *
     * @see #itemShatter(Position, ItemStack, int) */
    public void itemShatter(Position position, ItemStack stack) {
        emit(new ItemShatter(position, stack, OVERWORLD));
    }

    /** The white shatter puff. Any thread. */
    /**
     * The splash potion's burst (PotionEntity.onCollision's closing
     * {@code world.doEvent(2002, pos, statusEffect)}): the color-carrying
     * particle burst at the impact, in the splash's own dimension.
     */
    public void potionSplash(Position position, int potionData, int dimension) {
        emit(new PotionSplash(position, potionData, dimension));
    }

    /** The splash potion's burst (PotionEntity.onCollision's closing
     * {@code world.doEvent(2002, pos, statusEffect)}). Any thread.
     *
     * @see #potionSplash(Position, int, int) */
    public void potionSplash(Position position, int potionData) {
        emit(new PotionSplash(position, potionData, OVERWORLD));
    }

    /** Emits the reference's own world event (World.doEvent) in the given
     * dimension. Any thread. */
    public void worldEvent(Position position, int eventId, int data, int dimension) {
        emit(new WorldEvent(position, eventId, data, dimension));
    }

    /** Emits the reference's own world event (World.doEvent). Any thread.
     *
     * @see #worldEvent(Position, int, int, int) */
    public void worldEvent(Position position, int eventId, int data) {
        emit(new WorldEvent(position, eventId, data, OVERWORLD));
    }

    /** The white puff in the given dimension. Any thread. */
    public void poof(Position position, int dimension) {
        emit(new Poof(position, dimension));
    }

    /** The white shatter puff. Any thread.
     *
     * @see #poof(Position, int) */
    public void poof(Position position) {
        emit(new Poof(position, OVERWORLD));
    }

    /** The smoke puff at a position (SMOKE_NORMAL — the eye's insertion) in
     * the given dimension. Any thread. */
    public void smoke(Position position, int dimension) {
        emit(new Smoke(position, dimension));
    }

    /** The smoke puff at a position (SMOKE_NORMAL — the eye's insertion). Any thread.
     *
     * @see #smoke(Position, int) */
    public void smoke(Position position) {
        emit(new Smoke(position, OVERWORLD));
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
