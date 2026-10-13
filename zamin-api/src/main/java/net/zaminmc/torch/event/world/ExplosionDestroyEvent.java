package net.zaminmc.torch.event.world;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.Event;
import net.zaminmc.torch.util.Position;

import java.util.List;

/**
 * An explosion's block destruction committed (§27.7's post-commit
 * observation — the per-block companion of the pre-commit
 * {@link ExplosionEvent} gate). Fired after the blast tore its cells
 * and rolled the drops, before the wound falloff walk: the cells are
 * already air, the surviving loot may already lie on the ground. Not
 * cancellable — the destruction already happened; handlers observe and
 * react.
 *
 * <p>Threading: handlers run on the world's simulation context (the
 * detonation walk's own thread). Handlers must not block or mutate
 * world state (queue through the engine's own paths instead).</p>
 */
public final class ExplosionDestroyEvent implements Event {

    private final World world;
    private final Position center;
    private final float power;
    private final List<DestroyedCell> cells;

    /** One destroyed cell: where it stood and what it was. */
    public record DestroyedCell(
            /** The destroyed cell's position. */
            BlockPosition position,
            /** The type that stood there (an identifier string, e.g. "minecraft:stone"). */
            String previousType) {
    }

    public ExplosionDestroyEvent(World world, Position center, float power,
                                 List<DestroyedCell> cells) {
        this.world = world;
        this.center = center;
        this.power = power;
        this.cells = List.copyOf(cells);
    }

    /** The world the blast tore. */
    public World world() {
        return world;
    }

    /** The blast's center. */
    public Position center() {
        return center;
    }

    /** The blast's power (the destruction radius in blocks). */
    public float power() {
        return power;
    }

    /**
     * @return the destroyed cells (immutable, in the detonation walk's
     *         own order) — empty when the blast found nothing to tear
     */
    public List<DestroyedCell> cells() {
        return cells;
    }

    @Override
    public String eventName() {
        return "ExplosionDestroy";
    }
}
