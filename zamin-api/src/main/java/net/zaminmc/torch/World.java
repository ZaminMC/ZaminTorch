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
}
