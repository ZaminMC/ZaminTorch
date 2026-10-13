package net.zaminmc.torch.blockentity;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;

import java.util.Optional;

/**
 * The position-keyed block-entity family beyond the containers (§27.3's
 * block-entity read surface): the mob spawners and the signs of one
 * world, reached through {@link World#blockEntities()}.
 *
 * <p><b>Threading (§27.6):</b> the lookups are best-effort from any
 * thread (a block entity that does not exist answers empty); the
 * returned views are LIVE handles over the engine's own block entities —
 * reads are owner-context consistent. A plugin reading off the
 * simulation lane wraps the read in {@link World#execute} for a
 * consistent snapshot.</p>
 */
public interface BlockEntities {

    /** The empty world's answer (every lookup empty). */
    BlockEntities EMPTY = new BlockEntities() {
        @Override public Optional<MobSpawner> spawnerAt(BlockPosition position) {
            return Optional.empty();
        }

        @Override public Optional<Sign> signAt(BlockPosition position) {
            return Optional.empty();
        }
    };

    /**
     * @return the mob spawner at the position (a live handle), or empty
     *         when no spawner block entity stands there. A spawner
     *         answers only on the world (dimension) it lives in.
     */
    Optional<MobSpawner> spawnerAt(BlockPosition position);

    /**
     * @return the sign at the position (a live handle), or empty when
     *         no sign text stands there.
     */
    Optional<Sign> signAt(BlockPosition position);

    /** A mob spawner's live read surface (the kind + the delay walk). */
    interface MobSpawner {

        /** @return the spawner's own grid position. */
        BlockPosition position();

        /**
         * @return the kind the spawner spawns ({@code minecraft:zombie}),
         *         or null when the spawner carries no type yet (a fresh
         *         {@code getOrCreate} before the first re-type).
         */
        Identifier spawnedMob();

        /**
         * @return the remaining spawn-delay ticks — the reference's own
         *         walk (the 200..800 re-roll band after every fired
         *         cycle, counted down once per tick).
         */
        int spawnDelayTicks();
    }

    /** A sign's live read surface (the four editable lines). */
    interface Sign {

        /** @return the sign's own grid position. */
        BlockPosition position();

        /** @return the editable line count (the vanilla sign's 4). */
        int lineCount();

        /**
         * @return a copy of the lines (short lines as empty strings —
         *         owner-context consistent off the lane).
         */
        String[] lines();

        /** @return one line (empty when out of range or unset). */
        String line(int index);
    }
}
