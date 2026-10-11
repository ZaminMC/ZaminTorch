package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.world.Biome;

import java.util.List;

/**
 * The piece's world view (Slice 12a — the engine's replacement for the
 * reference's {@code World} parameter on the structure paths): the
 * deterministic block surface one chunk's populate pass sees.
 *
 * <p>Reads inside the generating chunk answer the chunk's own array
 * (terrain + this pass's earlier writes); reads outside answer the
 * generator's pure pre-structure terrain function — the deterministic
 * equivalent of the reference's neighbor-chunk reads at populate time.
 * Writes land only inside the chunk (the clip box already guarantees
 * it; the view double-guards).</p>
 *
 * <p>Slice 12b adds the village's four probes: the pure terrain height
 * (updateHeight's average — answered from the generator's heightAt, never
 * the chunk array, so a piece spanning chunks re-bases identically in
 * every chunk that touches it), the biome function (the placement's
 * biome gate — the pure fbm answer, the deterministic equivalent of the
 * reference's biome-grid read), the villager spawner (the chunk's
 * pending-mob queue — the engine's populate-time entities materialize
 * when the chunk publishes) and the loot-carrying chest (the pending
 * record the same drain materializes into the chest manager).</p>
 */
public interface StructureWorld {

    /** The block at the world cell (the chunk's own, or the pure terrain). */
    BlockType blockAt(int x, int y, int z);

    /** Whether the block is a liquid (the reference's material test). */
    boolean isLiquid(BlockType type);

    /** Writes one cell (ignored outside the generating chunk). */
    void setBlock(int x, int y, int z, BlockType type);

    /** The world's sea level (moveBelowSeaLevel's anchor). */
    int seaLevel();

    /** The structure block palette (resolved once per world). */
    StructurePalette palette();

    /** Whether the block is air (the reference's Material.AIR test). */
    default boolean isAir(BlockType type) {
        return type.identifier().value().equals("air");
    }

    /** Whether the block is opaque (the corridor's rail-floor gate). */
    boolean isOpaque(BlockType type);

    /**
     * The pure terrain height at the column (the generator's heightAt,
     * never the chunk's array): the village piece's updateHeight average
     * stays identical across the chunks that touch one piece.
     */
    default int surfaceHeight(int x, int z) {
        return 64;
    }

    /** The pure biome at the column (the village placement's gate). */
    default Biome biomeAt(int x, int z) {
        return Biome.PLAINS;
    }

    /**
     * The populate-time villager spawn (the reference's
     * world.addEntity on the piece paths): the chunk's pending queue,
     * materialized by the engine when the chunk publishes.
     *
     * @param profession the 1.8 profession id (0 farmer, 1 librarian,
     *                   2 priest, 3 blacksmith, 4 butcher)
     */
    default void spawnVillager(int x, int y, int z, int profession) {
    }

    /**
     * The loot-carrying chest (the reference's placeChestWithLoot): the
     * block lands through the ordinary setBlock path; the rolled contents
     * ride the chunk's pending record and materialize into the chest
     * manager when the chunk publishes.
     *
     * @param slots the rolled contents (slot index 0..26, item id, count)
     */
    default void placeGenChest(int x, int y, int z, List<GenLootSlot> slots) {
    }

    /** One rolled chest slot (the loot walk's setItem record). */
    record GenLootSlot(int slot, String itemId, int count) {
    }
}
