package net.zaminmc.torch.server.world.feature;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.TreeFeatures;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.Biome;

import java.util.List;

/**
 * The populate-time world view (the VIII14 decorators slice — the engine's
 * replacement for the reference's {@code World} parameter on the feature
 * paths, the structures' own {@code StructureWorld} shape): the
 * deterministic block surface one chunk's populate pass sees.
 *
 * <p>Reads inside the generating chunk answer the chunk's own array
 * (terrain + this pass's earlier writes); reads outside answer the
 * generator's pure pre-decoration terrain — the deterministic neighbor
 * answer, Slice 12b's own contract (a piece spanning chunks re-bases
 * identically in every chunk that touches it). Writes land only inside
 * the chunk (the reference's cross-border writes drop — the named
 * adaptation the region re-derivation window answers: every chunk runs
 * the walks of its {self, -x} x {self, -z} quadrant, so each foreign
 * cell's writer is exactly one pass, seed-pure per source chunk).</p>
 *
 * <p>The pending queues (the dungeon's spawner + the loot chest) ride the
 * chunk's own records and materialize when the chunk publishes — the
 * villagers' own pattern.</p>
 */
public interface FeatureWorld extends TreeFeatures.TreeWorld {

    /** The block at the world cell (the chunk's own, or the pure terrain). */
    BlockType blockAt(int x, int y, int z);

    /** Writes one cell (ignored outside the generating chunk). */
    void setBlockAt(int x, int y, int z, BlockType type);

    /** Whether the cell is air ({@code world.isAir}). */
    boolean isAirAt(int x, int y, int z);

    /** Whether the cell's material is a liquid ({@code Material#isLiquid}). */
    boolean isLiquidAt(int x, int y, int z);

    /** Whether the cell's material is solid ({@code Material#isSolid}). */
    boolean isSolidAt(int x, int y, int z);

    /** Whether the cell blocks light (the opaque consult). */
    boolean isOpaqueAt(int x, int y, int z);

    /**
     * The heightmap read ({@code World.getHeight} — the highest
     * light-blocking cell + 1: leaves and water raise the column, the
     * ground-cover plants never do). In-chunk the scan sees this pass's
     * earlier writes; outside the pure terrain answers.
     */
    int heightAt(int x, int z);

    /**
     * The movement-blocking read ({@code World.getSurfaceHeight}, World.java
     * lines 1132-1146): the highest cell whose material blocks movement and
     * is not leaves, + 1 — the clay/sand/gravel patch stage's own consult
     * (an ocean column answers just above its floor: water never blocks
     * movement).
     */
    int surfaceHeightAt(int x, int z);

    /** The world's sea level. */
    int seaLevel();

    /** The pure biome at the column. */
    Biome biomeAt(int x, int z);

    /**
     * The populate-time mob spawner (the dungeon's own record): the block
     * lands through the ordinary write path; the typed record rides the
     * chunk's pending queue.
     */
    void placeMobSpawner(int x, int y, int z, MobType mobType);

    /**
     * The loot-carrying chest (the dungeon's own record): the block lands
     * through the ordinary write path; the rolled contents ride the
     * chunk's pending record.
     *
     * @param slots the rolled contents (slot index 0..26, item id, count)
     */
    void placeGenChest(int x, int y, int z,
                       List<net.zaminmc.torch.server.world.structure.StructureWorld.GenLootSlot> slots);
}
