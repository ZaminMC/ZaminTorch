package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemType;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Owns every falling block of one world. Simulation-thread confined, exactly
 * like the item and mob systems (§447): the {@code BlockUpdateSystem} requests
 * the block→entity transition, this manager runs the fall and commits the
 * entity→block (or entity→item) transition back on landing.
 *
 * <p>Landing rules are the vanilla 1.8.8 ones (ported from reference/1.8.8
 * FallingBlockEntity.tick lines 92-127): the block re-materializes when its
 * landing cell is replaceable (air, water, lava, fire and the replaceable
 * flora — the {@code canPlace} semantics, so sand settles INTO a pond and
 * replaces the water) AND the cell below holds it up ({@code
 * !FallingBlock.canFallThrough(world, blockpos1.down())}); anything else —
 * an occupied cell, a below-cell that cannot support — drops the stack as an
 * item entity instead (the historical {@code dropItem}); the vanilla
 * lifetime rule (600 ticks falling, or 100+ outside y 1..256) also ends in
 * a drop. The landing commit flows through the world's normal mutation
 * path, so persistence deltas and client syncs ride the existing
 * listeners.</p>
 */
public final class FallingBlockEntityManager {

    private static final Logger LOGGER = Logger.getLogger(FallingBlockEntityManager.class.getName());

    /** Where an occupied landing drops its stack (the engine's item system). */
    public interface DropSink {
        void spawnDropAtBlock(BlockPosition position, ItemStack stack);
    }

    /**
     * The anvil landing hook (Slice 53 — the server's wiring of the
     * reference's takeFallDamage walk): called on every anvil landing
     * before the placement consult. The hook applies the hurt, rolls the
     * DAMAGE progression, and answers the block to place — empty means the
     * brokeAnvil path (nothing placed, nothing dropped).
     */
    public interface AnvilLanding {
        java.util.Optional<BlockType> onAnvilLanding(
                FallingBlockEntity entity, BlockPosition landing);
    }

    /** Events the protocol adapter translates into wire updates. */
    public interface Listener {
        void onFallingSpawned(FallingBlockEntity entity);

        void onFallingMoved(FallingBlockEntity entity);

        /**
         * The entity ended (landing or void). {@code becameBlock} is true when
         * it re-materialized as a block; observers must destroy the entity on
         * the wire in both cases (the block change itself rides the world
         * listener, not this event).
         */
        void onFallingEnded(FallingBlockEntity entity, boolean becameBlock);
    }

    private final FallingBlockEntity.Ground ground;
    private final net.zaminmc.torch.server.world.EngineWorld world;
    private final DropSink dropSink;
    private final Random random;
    private final List<FallingBlockEntity> entities = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private AnvilLanding anvilLanding;
    private int nextEntityId;

    public FallingBlockEntityManager(FallingBlockEntity.Ground ground,
                                     net.zaminmc.torch.server.world.EngineWorld world,
                                     DropSink dropSink, Random random, int firstEntityId) {
        this.ground = Objects.requireNonNull(ground, "ground");
        this.world = Objects.requireNonNull(world, "world");
        this.dropSink = Objects.requireNonNull(dropSink, "dropSink");
        this.random = Objects.requireNonNull(random, "random");
        this.nextEntityId = firstEntityId;
    }

    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Wires the anvil landing hook (Slice 53). */
    public void setAnvilLanding(AnvilLanding hook) {
        this.anvilLanding = Objects.requireNonNull(hook, "hook");
    }

    /** @return an unmodifiable view of the live falling entities (diagnostics/tests). */
    public List<FallingBlockEntity> all() {
        return List.copyOf(entities);
    }

    public int size() {
        return entities.size();
    }

    /**
     * Starts the §470 transition: the block at {@code source} (already air in
     * the world) continues as a falling entity positioned exactly as vanilla
     * spawns it ({@code x + 0.5, y, z + 0.5} — X/Z centered, Y at the cell
     * bottom, the box-bottom convention). Tick-thread context.
     */
    public FallingBlockEntity startFall(BlockPosition source, BlockType blockType) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(blockType, "blockType");
        FallingBlockEntity entity = new FallingBlockEntity(nextEntityId++, blockType,
                new Position(source.x() + 0.5, source.y(), source.z() + 0.5));
        entities.add(entity);
        for (Listener listener : listeners) {
            listener.onFallingSpawned(entity);
        }
        return entity;
    }

    /** Advances every falling entity one tick: physics, landing, void removal. */
    public void tick() {
        tickAll(false);
    }

    /**
     * Lands every mid-fall entity immediately (the engine's save point): a
     * crash-restart must not lose the block that was already converted into an
     * entity, and the engine persists blocks, not entities. Tick-thread context.
     */
    public void finishAllFalls() {
        tickAll(true);
    }

    private void tickAll(boolean forceLand) {
        Iterator<FallingBlockEntity> iterator = entities.iterator();
        while (iterator.hasNext()) {
            FallingBlockEntity entity = iterator.next();
            FallingBlockEntity.Step step = forceLand ? FallingBlockEntity.Step.LANDED : entity.tick(ground);
            switch (step) {
                case MOVED -> {
                    for (Listener listener : listeners) {
                        listener.onFallingMoved(entity);
                    }
                }
                case VOID -> {
                    iterator.remove();
                    LOGGER.fine(() -> "Falling block " + entity.entityId()
                            + " ended by the vanilla lifetime rule");
                    dropAsItem(entity, entity.blockType());
                    for (Listener listener : listeners) {
                        listener.onFallingEnded(entity, false);
                    }
                }
                case LANDED -> {
                    iterator.remove();
                    boolean became = landAsBlock(entity);
                    for (Listener listener : listeners) {
                        listener.onFallingEnded(entity, became);
                    }
                }
            }
        }
    }

    /**
     * The vanilla landing gate (FallingBlockEntity.tick lines 99-101): the
     * cell accepts the block ({@code canPlace} — the replaceable set, so a
     * settling sand replaces the pond water or the flora it lands in) AND
     * the cell below holds it up ({@code !FallingBlock.canFallThrough(
     * world, blockpos1.down())} — no floating over air or water); every
     * other landing drops the stack as an item.
     */
    private boolean landAsBlock(FallingBlockEntity entity) {
        BlockPosition landing = entity.landingPosition();
        // The anvil's own landing walk (Slice 53): the hook applies the hurt
        // and the progression roll before the placement consult — empty
        // means brokeAnvil (the reference's ++j > 2 arm: nothing placed,
        // nothing dropped).
        BlockType toPlace = entity.blockType();
        if (anvilLanding != null
                && net.zaminmc.torch.server.block.AnvilBlocks.isAnvil(entity.blockType().identifier())) {
            java.util.Optional<BlockType> progressed = anvilLanding.onAnvilLanding(entity, landing);
            if (progressed.isEmpty()) {
                return false; // the brokeAnvil path: no block, no drop
            }
            toPlace = progressed.get();
        }
        // The 1.8 world band is y 0..255 (16 chunk sections); below-world
        // landings drop instead of indexing a nonexistent section (vanilla's
        // setBlockState fails there too).
        if (landing.y() >= 0 && landing.y() < net.zaminmc.torch.server.world.EngineChunk.SECTION_COUNT * 16
                && FallingBlockEntity.canBeReplacedOnLanding(world.getBlock(landing))
                && !FallingBlockEntity.canFallThrough(FallingBlockEntity.blockOrAir(
                        world, landing.offset(0, -1, 0)))) {
            world.setBlock(landing, toPlace);
            return true;
        }
        dropAsItem(entity, toPlace);
        return false;
    }

    /** The vanilla {@code dropItem}: the block's item pops at the entity. */
    private void dropAsItem(FallingBlockEntity entity, BlockType asState) {
        // The anvil family's item consult (Slice 53 — the reference's
        // getDropItemMetadata over the falling state): the item is the
        // family's, the damage the state's DAMAGE id — the progressed state
        // when the landing hook walked it forward.
        if (net.zaminmc.torch.server.block.AnvilBlocks.isAnvil(entity.blockType().identifier())) {
            net.zaminmc.torch.item.ItemType anvilItem =
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            net.zaminmc.torch.server.block.AnvilBlocks.FAMILY_ITEM).orElse(null);
            if (anvilItem != null) {
                BlockPosition at = dropAt(entity);
                dropSink.spawnDropAtBlock(at, new ItemStack(anvilItem, 1,
                        net.zaminmc.torch.server.block.AnvilBlocks.dropDamageOf(asState.identifier())));
            }
            return;
        }
        net.zaminmc.torch.item.ItemType itemType =
                net.zaminmc.torch.server.item.BuiltinItems.lookup(asState.identifier()).orElse(null);
        if (itemType != null) {
            dropSink.spawnDropAtBlock(dropAt(entity), ItemStack.of(itemType, 1));
        }
    }

    private BlockPosition dropAt(FallingBlockEntity entity) {
        return new BlockPosition((int) Math.floor(entity.position().x()),
                Math.max(0, (int) Math.floor(entity.position().y())),
                (int) Math.floor(entity.position().z()));
    }
}
