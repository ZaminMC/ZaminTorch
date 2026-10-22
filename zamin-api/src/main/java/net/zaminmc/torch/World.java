package net.zaminmc.torch;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.entity.DroppedItem;
import net.zaminmc.torch.entity.ExperienceOrb;
import net.zaminmc.torch.entity.FallingBlock;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.entity.Projectile;
import net.zaminmc.torch.entity.Vehicle;
import net.zaminmc.torch.util.Position;

import java.util.Collection;

/**
 * A world holds authoritative block, entity and simulation state under one name.
 *
 * <p>Threading contract (Slice #1): reads are safe from any thread; mutation
 * ({@link #setBlock}) must happen on the engine's simulation execution context.
 * The API deliberately avoids returning live internal collections.</p>
 */
public interface World {

    /** The world's stable name (for example {@code "world"}). */
    String name();

    /** Current world time in ticks (0..23999 for time of day). */
    long timeOfDay();

    /** Total ticks that have elapsed in this world since creation. */
    long totalTicks();

    /** The registered spawn position of this world. */
    Position spawnPosition();

    /** @return the block type at the position, never null (air is a block type). */
    BlockType getBlock(BlockPosition position);

    /**
     * Sets the block type at the position.
     *
     * @return true if the state changed, false if the position already had this type
     * @throws IllegalStateException if called outside the world's allowed execution context
     */
    boolean setBlock(BlockPosition position, BlockType type);

    /** @return whether the chunk containing this position is loaded for gameplay. */
    boolean isChunkLoaded(ChunkPosition position);

    /**
     * The live entities of this world. Simulation context (§27.6): the
     * collection reflects the population at call time; inspect it inside
     * {@link #execute}.
     */
    Collection<LivingEntity> entities();

    /**
     * The dropped items of this world (§27.3's entity family): a
     * point-in-time population view — simulation context, inspect it
     * inside {@link #execute}. Worlds without the family answer empty.
     */
    default Collection<DroppedItem> itemEntities() {
        return java.util.List.of();
    }

    /**
     * The projectiles in flight (§27.3's projectile family): a
     * point-in-time population view — simulation context, inspect it
     * inside {@link #execute}. Worlds without the family answer empty.
     */
    default Collection<Projectile> projectiles() {
        return java.util.List.of();
    }

    /**
     * The falling block bodies (§27.3's gravity family): a point-in-time
     * population view — simulation context, inspect it inside
     * {@link #execute}. Worlds without the family answer empty.
     */
    default Collection<FallingBlock> fallingBlocks() {
        return java.util.List.of();
    }

    /**
     * The vehicles (§27.3's mount family): a point-in-time population
     * view — simulation context, inspect it inside {@link #execute}.
     * Worlds without the family answer empty.
     */
    default Collection<Vehicle> vehicles() {
        return java.util.List.of();
    }

    /**
     * The position-keyed container family (§27.3's block-entity read
     * surface): the chests, furnaces, and hoppers of this world. The
     * lookups are best-effort from any thread; the returned views are
     * LIVE handles — owner-context consistent (wrap multi-slot reads in
     * {@link #execute} off the lane). Worlds without the family answer
     * empty lookups.
     */
    default net.zaminmc.torch.container.Containers containers() {
        return net.zaminmc.torch.container.Containers.EMPTY;
    }

    /**
     * The position-keyed block-entity family beyond the containers
     * (§27.3's block-entity read surface): the mob spawners and the
     * signs of this world. The lookups are best-effort from any thread;
     * the returned views are LIVE handles — owner-context consistent
     * (wrap multi-field reads in {@link #execute} off the lane). Worlds
     * without the family answer empty lookups.
     */
    default net.zaminmc.torch.blockentity.BlockEntities blockEntities() {
        return net.zaminmc.torch.blockentity.BlockEntities.EMPTY;
    }

    /**
     * The scheduled block-update surface (§27.3's scheduled-block-tick
     * family): the pending neighbor updates and gravity fuses of this
     * world — queryable, cancellable, requestable. The queries answer
     * best-effort from any thread; the mutations run on the
     * authoritative simulation context (§27.6). Worlds without the
     * family answer nothing pending.
     */
    default net.zaminmc.torch.block.ScheduledUpdates scheduledUpdates() {
        return net.zaminmc.torch.block.ScheduledUpdates.EMPTY;
    }

    /**
     * The scheduled block-tick surface (§27.3's family, the reference
     * 1.8.8's own pending TileTicks queue): the entries carrying a
     * position, the waking block family, a due time, and a priority —
     * queryable, cancellable, schedulable. The queries answer
     * best-effort from any thread; the mutations run on the
     * authoritative simulation context (§27.6). Worlds without the
     * family answer nothing pending.
     */
    default net.zaminmc.torch.block.ScheduledBlockTicks scheduledBlockTicks() {
        return net.zaminmc.torch.block.ScheduledBlockTicks.EMPTY;
    }

    /**
     * The redstone read surface (§27.3's redstone family): the power
     * walks a plugin may consult. Owner-context consistent reads (wrap
     * in {@link #execute} off the lane). Worlds without the family
     * answer nothing powered.
     */
    default net.zaminmc.torch.block.RedstoneSignals redstone() {
        return net.zaminmc.torch.block.RedstoneSignals.EMPTY;
    }

    /**
     * The growth-family outcome reads (§27.3's block-behavior family —
     * the VIII2/VIII7/VIII8 exposure): the crops'/stems' stage bands,
     * the sapling family's variant + readiness + light gate, the cocoa
     * pod's age band, and the mushroom plant's survival law — the same
     * consults the bonemeal arms themselves run, so a plugin can
     * predict the arms' outcomes. Type consults are pure (any thread);
     * position consults read the live world best-effort (§27.6).
     */
    default net.zaminmc.torch.block.Growth growth() {
        return net.zaminmc.torch.block.Growth.EMPTY;
    }

    /**
     * The primed TNT bodies in flight (§27.3's TNT family): a
     * point-in-time population view — simulation context, inspect it
     * inside {@link #execute}. Worlds without the family answer empty.
     */
    default Collection<net.zaminmc.torch.entity.PrimedTnt> primedTnt() {
        return java.util.List.of();
    }

    /**
     * The decoration census (§27.3's family — the README TODO's row 15,
     * the Wave IX exposure): the hanging bodies (the item frames with
     * their displayed stacks, rotations and comparator reads; the
     * paintings with their art) and the armor stands' family-specific
     * rows (the five equipment slots, the status flags, the six-part
     * pose) — the reference 1.8.8's own reads over the engine's
     * decoration and armor-stand systems (the compat ledger's Slice
     * 28a/28b/28c rows). Every answer is an immutable point-in-time
     * snapshot copied under the owning system's monitor — freely
     * consumable from any thread, never a torn mid-tick state (§27.6;
     * see {@link net.zaminmc.torch.decoration.Decorations} for the
     * snapshot law). Worlds without the family answer empty
     * enumerations.
     */
    default net.zaminmc.torch.decoration.Decorations decorations() {
        return net.zaminmc.torch.decoration.Decorations.EMPTY;
    }

    /**
     * The experience orbs (§27.3's XP family): a point-in-time
     * population view — simulation context, inspect it inside
     * {@link #execute}. Worlds without the family answer empty.
     */
    default Collection<ExperienceOrb> experienceOrbs() {
        return java.util.List.of();
    }

    /**
     * Runs work on the world's authoritative simulation context (§27.6 —
     * the owner path for plugins). Safe to call from any thread; the
     * runnable runs between ticks, ordered after already-submitted work.
     */
    void execute(Runnable work);

    /**
     * Sets the world's time of day (wrapped on the 24000-tick day). Owner
     * context: call through {@link #execute} from outside the simulation.
     * The clock lands on this world only — the engine's own {@code /time}
     * command walks every dimension; a plugin targeting each world calls
     * each world.
     *
     * @throws IllegalStateException if called outside the world's allowed execution context
     */
    void setTimeOfDay(long timeOfDay);

    /**
     * @return the block light level (0-15) at the position; 0 outside
     *         loaded chunks. Best-effort read, safe from any thread.
     */
    int blockLightAt(BlockPosition position);

    /**
     * @return the sky light level (0-15) at the position; 15 outside
     *         loaded chunks (open sky). Best-effort read, safe from any
     *         thread.
     */
    int skyLightAt(BlockPosition position);

    /**
     * @return the biome's stable name at the column (for example
     *         {@code "plains"}), or {@code "plains"} outside loaded
     *         chunks (the implicit default). Safe from any thread.
     */
    String biomeAt(BlockPosition position);

    /**
     * The biome registration table's read surface (§27.3's
     * world-generation family — the VIII14 slice-1 exposure): every
     * registered row's own datums as {@link
     * net.zaminmc.torch.block.BiomeData} — the reference 1.8.8's whole
     * 61-row census in id order (the 40 base ids 0-39 + the 21 mutated
     * ids 129-167): the display names, the temperature/downfall pairs,
     * the height pairs, and the isRainy read law. The table is immutable
     * and world-independent — every world answers the same census, safe
     * from any thread (§27.6: no owner context, no snapshot discipline).
     * The worldgen consults (the layout, the spawn tables, the surface
     * builders) stay worldgen-side — §27.2's layer separation. Worlds
     * without the family answer empty.
     */
    default java.util.List<net.zaminmc.torch.block.BiomeData> biomes() {
        return java.util.List.of();
    }

    /**
     * The registration row at the column (the §27.3 exposure beside
     * {@link #biomeAt}): the SAME resolution the stable name rides —
     * the chunk's own stored biome row, and the plains row outside
     * loaded chunks (the implicit default's own datums, not a gap) — so
     * the row's {@code name} is always the string {@code biomeAt}
     * answers at the same position. Best-effort read, safe from any
     * thread; the registration table behind it is immutable. Worlds
     * without the family answer null (the lookup family's own gap
     * shape).
     */
    default net.zaminmc.torch.block.BiomeData biomeDataAt(BlockPosition position) {
        return null;
    }
}
