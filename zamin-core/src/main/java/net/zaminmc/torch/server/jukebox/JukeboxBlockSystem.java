package net.zaminmc.torch.server.jukebox;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.WorldChangeListener;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * The jukebox family 1:1 (the reference's JukeboxBlock + its
 * JukeboxBlockEntity, Block.java line 1181's
 * {@code register(84, "jukebox", ...)} — strength 2.0, blast resistance 10.0,
 * STONE sounds): one record slot per placed jukebox (the BE's own
 * {@code RecordItem}), the four reference arms —
 *
 * <ul>
 *   <li><b>The insert</b> ({@code MusicDiscItem.useOn} lines 28-43): a disc
 *   right-clicked against a record-less jukebox stores the copy (count 1),
 *   flips the block to its HAS_RECORD variant (metadata 1), broadcasts the
 *   reference's world event 1005 carrying the disc's item id (the client's
 *   own {@code records.<name>} walk, WorldRenderer lines 1923-1929), and
 *   consumes one from the hand.</li>
 *   <li><b>The eject</b> ({@code JukeboxBlock.use} lines 30-38 over
 *   {@code removeRecord} lines 51-72): a use on the HAS_RECORD variant stops
 *   the record (the event 1005 with data 0 — the same event's stop arm),
 *   clears the slot, flips the block back, and drops the disc.</li>
 *   <li><b>The break</b> ({@code onRemoved} line 74-78 → the same
 *   removeRecord): a broken jukebox drops its held record first (the stop
 *   event fires too), then the block's own drop.</li>
 *   <li><b>The comparator read</b> ({@code getAnalogSignal} lines 97-108):
 *   {@code Item.getId(record) + 1 - Item.getId(RECORD_13)} — the disc's
 *   table index + 1 (13 is 1, wait is 12; no record answers 0).</li>
 * </ul>
 *
 * <p><b>The wire's two-packet rule:</b> the reference's doEvent(1005) IS the
 * whole broadcast — the same Effect packet carries both directions (the item
 * id plays, 0 stops). The record observers here hand the protocol adapter
 * the disc identifier (or null for the stop); the adapter resolves the
 * legacy item id at the boundary.</p>
 */
public final class JukeboxBlockSystem implements WorldChangeListener {

    private static final Logger LOGGER = Logger.getLogger(JukeboxBlockSystem.class.getName());

    /**
     * The disc table in the reference's own registration order (Item.java
     * lines 735-746): 13, cat, blocks, chirp, far, mall, mellohi, stal,
     * strad, ward, 11, wait — the analog signal is the index + 1.
     */
    public static final List<Identifier> DISC_ORDER = List.of(
            Identifier.parse("minecraft:record_13"),
            Identifier.parse("minecraft:record_cat"),
            Identifier.parse("minecraft:record_blocks"),
            Identifier.parse("minecraft:record_chirp"),
            Identifier.parse("minecraft:record_far"),
            Identifier.parse("minecraft:record_mall"),
            Identifier.parse("minecraft:record_mellohi"),
            Identifier.parse("minecraft:record_stal"),
            Identifier.parse("minecraft:record_strad"),
            Identifier.parse("minecraft:record_ward"),
            Identifier.parse("minecraft:record_11"),
            Identifier.parse("minecraft:record_wait"));

    /** The record slots by position (the BE's own RecordItem). */
    private final Map<BlockPosition, ItemStack> records = new ConcurrentHashMap<>();

    private final EngineWorld world;
    private final ItemEntityManager items;

    /** The record observers (the protocol adapter's Effect packet). */
    public interface RecordListener {

        /**
         * A record event at the jukebox (the reference's world event 1005):
         * the disc's identifier plays it, null stops it.
         */
        void onRecordEvent(BlockPosition position, Identifier disc);
    }

    private final List<RecordListener> recordListeners = new CopyOnWriteArrayList<>();

    /** Registers the wire observer (the protocol adapter). */
    public void addRecordListener(RecordListener listener) {
        recordListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public JukeboxBlockSystem(EngineWorld world, ItemEntityManager items) {
        this.world = Objects.requireNonNull(world, "world");
        this.items = Objects.requireNonNull(items, "items");
    }

    // ------------------------------------------------------------- the reads

    /** The held record at the position (the BE's getRecord — null = empty). */
    public ItemStack recordAt(BlockPosition position) {
        ItemStack held = records.get(position);
        return held == null || held.isEmpty() ? null : held;
    }

    /**
     * The comparator read (getAnalogSignal lines 97-108): the disc's table
     * index + 1 ({@code Item.getId(record) + 1 - Item.getId(RECORD_13)}),
     * 0 when no record sits in the slot.
     */
    public int analogSignal(BlockPosition position) {
        ItemStack held = recordAt(position);
        if (held == null) {
            return 0;
        }
        int index = DISC_ORDER.indexOf(held.type().identifier());
        return index < 0 ? 0 : index + 1;
    }

    /** The full snapshot (the persistence save's own). */
    public Map<BlockPosition, ItemStack> snapshotRecords() {
        return Map.copyOf(records);
    }

    // ------------------------------------------------------------- the lifecycle

    /** The placement arm: the fresh jukebox's BE starts with an empty slot. */
    @Override
    public void onBlockChanged(EngineWorld changedWorld, BlockPosition position, BlockType newType) {
        if (JukeboxBlocks.isFamily(newType)) {
            records.putIfAbsent(position, ItemStack.EMPTY);
        }
    }

    /**
     * The restore arm (the ZJD load at boot): the record lands back in the
     * slot and the block variant flips to HAS_RECORD when a disc is present
     * (the reference's readNbt "RecordItem" + the metadata's own bit).
     */
    public void restoreAll(Map<BlockPosition, ItemStack> stored) {
        for (Map.Entry<BlockPosition, ItemStack> entry : stored.entrySet()) {
            ItemStack record = entry.getValue();
            records.put(entry.getKey(), record == null ? ItemStack.EMPTY : record);
            if (record != null && !record.isEmpty()
                    && JukeboxBlocks.isFamily(world.getBlock(entry.getKey()))) {
                world.setBlock(entry.getKey(), JukeboxBlocks.variantOf(true));
            }
        }
    }

    /**
     * The break arm (onRemoved lines 74-78 → removeRecord): the held record
     * ejects with the stop event BEFORE the block's own drop. The engine's
     * break listener calls this after the block left the world (the same
     * net effect — both drops land, the stop event goes out).
     */
    public void onBlockBroken(BlockPosition position) {
        ItemStack held = records.remove(position);
        if (held == null || held.isEmpty()) {
            return;
        }
        stopRecord(position);
        dropRecord(position, held);
    }

    // ------------------------------------------------------------- the arms

    /**
     * The eject arm (JukeboxBlock.use lines 30-38): a use on the HAS_RECORD
     * variant stops the record, clears the slot, flips the block back, and
     * drops the disc (removeRecord lines 51-72). Returns false when the use
     * must fall through (a record-less jukebox never consumes the click —
     * the reference's own {@code false} answer).
     * Tick-thread context.
     */
    public boolean useFromTick(BlockPosition position) {
        if (!JukeboxBlocks.isRecordVariant(world.getBlock(position))) {
            return false;
        }
        ItemStack held = records.remove(position);
        // The reference's own order (use lines 31-35 over removeRecord
        // lines 51-72): the stop event and the drop first, then the
        // HAS_RECORD flip.
        stopRecord(position);
        if (held != null && !held.isEmpty()) {
            dropRecord(position, held);
        }
        world.setBlock(position, JukeboxBlocks.variantOf(false));
        return true;
    }

    /**
     * The insert arm (MusicDiscItem.useOn lines 28-43): a disc against a
     * record-less jukebox — the stored copy (count 1, the reference's own
     * {@code new ItemStack(item.getItem(), 1, item.getMetadata())}), the
     * HAS_RECORD flip (setRecord lines 41-49), the event 1005 carrying the
     * disc's id, and one consumed from the hand. Returns false when the
     * click must fall through (a playing jukebox never takes a second disc,
     * and a disc against anything else never inserts).
     * Tick-thread context.
     */
    public boolean insertFromTick(BlockPosition position, ItemStack held) {
        if (held == null || held.isEmpty() || !JukeboxBlocks.isMusicDisc(held.type().identifier())) {
            return false;
        }
        BlockType block = world.getBlock(position);
        if (!JukeboxBlocks.isFamily(block) || JukeboxBlocks.isRecordVariant(block)) {
            return false; // the useOn gate: a playing jukebox or a non-jukebox
        }
        ItemStack copy = ItemStack.of(held.type(), 1);
        if (held.displayName() != null) {
            copy = copy.withName(held.displayName());
        }
        records.put(position, copy);
        world.setBlock(position, JukeboxBlocks.variantOf(true));
        for (RecordListener listener : recordListeners) {
            try {
                listener.onRecordEvent(position, held.type().identifier());
            } catch (Throwable t) {
                LOGGER.warning("Record listener failed: " + t);
            }
        }
        return true;
    }

    // ------------------------------------------------------------- the events

    /** The stop event (the reference's event 1005 with data 0). */
    private void stopRecord(BlockPosition position) {
        for (RecordListener listener : recordListeners) {
            try {
                listener.onRecordEvent(position, null);
            } catch (Throwable t) {
                LOGGER.warning("Record listener failed: " + t);
            }
        }
    }

    /**
     * The disc's drop (removeRecord lines 61-68): the engine's own block-drop
     * verb — the spawn walk every container spill rides (the reference's
     * 0.7-band random offsets over the cell, the default pickup delay).
     */
    private void dropRecord(BlockPosition position, ItemStack record) {
        Position origin = new Position(position.x(), position.y(), position.z());
        items.spawnDropAtBlock(origin, record, ItemEntity.PICKUP_DELAY_DROP_TICKS);
    }
}
