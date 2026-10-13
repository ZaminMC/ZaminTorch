package net.zaminmc.torch.server.blockentity;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.blockentity.BlockEntities;
import net.zaminmc.torch.server.sign.SignManager;
import net.zaminmc.torch.server.spawner.SpawnerManager;
import net.zaminmc.torch.util.Identifier;

import java.util.Optional;

/**
 * The public block-entity views (§27.3's read surface beyond the
 * containers): the spawner and sign managers dressed as the
 * {@link BlockEntities} family — the engine's own block entities ARE the
 * handles (§27.5), read through live views (the owner-context
 * consistency contract of {@code World.blockEntities()}).
 *
 * <p><b>The spawner's dimension filter:</b> the spawner manager is one
 * global map (every dimension's spawners keyed by position), but each
 * spawner carries the dimension it lives in — this view answers only
 * the spawners of ITS own world (the overworld view never answers a
 * fortress spawner at the same coordinates, and the nether view never
 * answers the overworld's dungeon).</p>
 */
public final class BlockEntityViews implements BlockEntities {

    private final SpawnerManager spawners;
    private final int dimension;
    private final SignManager signs;

    /**
     * @param dimension the world this view answers for (0 the overworld,
     *                  -1 the nether) — the spawner filter.
     */
    public BlockEntityViews(SpawnerManager spawners, int dimension, SignManager signs) {
        this.spawners = spawners;
        this.dimension = dimension;
        this.signs = signs;
    }

    @Override
    public Optional<MobSpawner> spawnerAt(BlockPosition position) {
        SpawnerManager.MobSpawnerBlockEntity entity = spawners.peek(position);
        if (entity == null || entity.dimension() != dimension) {
            return Optional.empty();
        }
        return Optional.of(new MobSpawnerView(entity, position));
    }

    @Override
    public Optional<Sign> signAt(BlockPosition position) {
        String[] lines = signs.peek(position);
        if (lines == null) {
            return Optional.empty();
        }
        return Optional.of(new SignView(position));
    }

    /** The spawner's live view (the engine object behind the handle). */
    private static final class MobSpawnerView implements MobSpawner {
        private final SpawnerManager.MobSpawnerBlockEntity entity;
        private final BlockPosition position;

        MobSpawnerView(SpawnerManager.MobSpawnerBlockEntity entity, BlockPosition position) {
            this.entity = entity;
            this.position = position;
        }

        @Override public BlockPosition position() {
            return position;
        }

        @Override public Identifier spawnedMob() {
            return entity.type() == null ? null : entity.type().identifier();
        }

        @Override public int spawnDelayTicks() {
            return entity.delay();
        }
    }

    /** The sign's live view — every read re-resolves the live text. */
    private final class SignView implements Sign {
        private final BlockPosition position;

        SignView(BlockPosition position) {
            this.position = position;
        }

        @Override public BlockPosition position() {
            return position;
        }

        @Override public int lineCount() {
            return SignManager.LINES;
        }

        @Override public String[] lines() {
            return linesCopy();
        }

        @Override public String line(int index) {
            if (index < 0 || index >= SignManager.LINES) {
                return "";
            }
            String[] live = linesCopy();
            return live[index];
        }

        private String[] linesCopy() {
            String[] live = signs.peek(position);
            if (live == null) {
                return new String[SignManager.LINES];
            }
            String[] copy = new String[SignManager.LINES];
            for (int i = 0; i < SignManager.LINES; i++) {
                copy[i] = live[i] == null ? "" : live[i];
            }
            return copy;
        }
    }
}
