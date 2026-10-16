package net.zaminmc.torch.server.commandblock;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.EngineWorld;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns every placed command block's executor state, keyed by position —
 * the block-entity shape of the sign/skull managers (simulation-thread
 * confined, tick sweep discards orphaned state).
 *
 * <p><b>The reference (CommandBlockBlockEntity over CommandExecutor):</b>
 * the four executor rows (Command/SuccessCount/TrackOutput/LastOutput);
 * the edit lands through MC|AdvCdm type 0 (position → setCommand +
 * setTrackOutput, the untracked executor clearing its last output —
 * setCommand line 107 resets the success count); the run is the redstone
 * walk — {@code CommandBlock.neighborChanged}: a rising edge flips the
 * TRIGGERED bit and schedules the block's own tick (rate 1), the tick
 * runs the executor (op level 2, the name "@"), the success count
 * becomes the block's analog signal ({@code getAnalogSignal}), and the
 * falling edge clears the bit (the next pulse re-runs).</p>
 */
public final class CommandBlockManager
        implements net.zaminmc.torch.server.world.WorldChangeListener {

    /** One executor's state (CommandExecutor's own rows, 1:1). */
    public static final class CommandState {
        private String command = "";
        private int successCount;
        private boolean trackOutput = true;
        private String lastOutput;

        /** The stored command (no leading slash). */
        public String command() {
            return command;
        }

        /** The accumulated success count (the analog signal's source). */
        public int successCount() {
            return successCount;
        }

        /** Whether output tracking is on. */
        public boolean trackOutput() {
            return trackOutput;
        }

        /** The last recorded output (the last-output row's text), or null. */
        public String lastOutput() {
            return lastOutput;
        }

        /** The edit's landing (setCommand line 107: the count resets). */
        void setCommand(String command) {
            this.command = command == null ? "" : command;
            this.successCount = 0;
        }

        void setTrackOutput(boolean trackOutput) {
            this.trackOutput = trackOutput;
            if (!trackOutput) {
                this.lastOutput = null;
            }
        }

        public void recordOutput(String message) {
            this.lastOutput = message;
        }

        void setSuccessCount(int successCount) {
            this.successCount = successCount;
        }
    }

    /** The engine's run surface (the CommandService dispatch, the engine's own wiring). */
    public interface CommandHost {

        /**
         * One execution (CommandExecutor.run): @return the success count
         * (the handler's own return; the feedback lines land in the
         * executor's last output through the host's sendMessage walk).
         */
        int run(CommandState executor, String command);
    }

    /** Events the protocol adapter translates into the 0x35 tile packet. */
    public interface Listener {

        /** A command tile landed (or its rows changed): the tile re-publishes. */
        default void onCommandTileChanged(BlockPosition position, CommandState tile) {
        }

        /** A command tile left (the break). */
        default void onCommandTileRemoved(BlockPosition position) {
        }
    }

    private final Map<BlockPosition, CommandState> blocks = new LinkedHashMap<>();
    private final List<Listener> listeners = new ArrayList<>();
    private final CommandHost host;
    /** The track-output default (the sendCommandFeedback gamerule — onPlaced lines 86-88). */
    private java.util.function.BooleanSupplier trackDefault = () -> true;

    public CommandBlockManager(CommandHost host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    /** The adapter's tile subscription (the 0x35 translation). */
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Wires the placement's track default (the sendCommandFeedback gamerule). */
    public void setTrackDefault(java.util.function.BooleanSupplier trackDefault) {
        this.trackDefault = Objects.requireNonNull(trackDefault, "trackDefault");
    }

    /** @return the live executor at the position, or null when none is placed. */
    public CommandState at(BlockPosition position) {
        return blocks.get(position);
    }

    /**
     * The placement's tile (onPlaced line 86-88: the sendCommandFeedback
     * gamerule is the track-output default).
     */
    public CommandState createAt(BlockPosition position, boolean trackOutputDefault) {
        CommandState executor = blocks.computeIfAbsent(position, at -> new CommandState());
        executor.setTrackOutput(trackOutputDefault);
        return executor;
    }

    /** One restored row (the ZVD persistence's landing — the boot restore). */
    public static CommandState restoredState(String command, int successCount,
                                              boolean trackOutput, String lastOutput) {
        CommandState state = new CommandState();
        state.command = command == null ? "" : command;
        state.successCount = successCount;
        state.trackOutput = trackOutput;
        state.lastOutput = lastOutput;
        return state;
    }

    /** The break's cleanup (no drop — the reference's own empty override). */
    public void remove(BlockPosition position) {
        blocks.remove(position);
    }

    /** The edit (MC|AdvCdm type 0): setCommand + setTrackOutput, the untracked clearing output. */
    public void edit(BlockPosition position, String command, boolean trackOutput) {
        CommandState executor = blocks.get(position);
        if (executor == null) {
            return; // no tile at the position — the payload's silent skip
        }
        executor.setCommand(command);
        executor.setTrackOutput(trackOutput);
        publish(position, executor);
    }

    /** The re-publish (the rows changed: the tile's 0x35 answer). */
    private void publish(BlockPosition position, CommandState executor) {
        for (Listener listener : listeners) {
            listener.onCommandTileChanged(position, executor);
        }
    }

    /** The redstone run (CommandBlock.tick): the executor's own walk. */
    public int run(BlockPosition position) {
        CommandState executor = blocks.get(position);
        if (executor == null) {
            return 0;
        }
        String command = executor.command();
        if (command.isBlank()) {
            return 0;
        }
        int successes = host.run(executor, command);
        executor.setSuccessCount(successes);
        publish(position, executor); // the count feeds the comparator + the GUI
        return successes;
    }

    /** The analog signal (getAnalogSignal: the success count, 0 without a tile). */
    public int analogSignal(BlockPosition position) {
        CommandState executor = blocks.get(position);
        return executor == null ? 0 : executor.successCount();
    }

    /** @return a defensive copy of the whole map (the save snapshot). */
    public Map<BlockPosition, CommandState> snapshot() {
        return new LinkedHashMap<>(blocks);
    }

    /** Restores persisted state after boot (world deltas already applied). */
    public void restoreAll(Map<BlockPosition, CommandState> restored) {
        blocks.putAll(restored);
    }

    /** @return every command tile within one chunk column (the chunk-send replay). */
    public List<Map.Entry<BlockPosition, CommandState>> inChunk(ChunkPosition chunk) {
        List<Map.Entry<BlockPosition, CommandState>> found = new ArrayList<>();
        for (Map.Entry<BlockPosition, CommandState> entry : blocks.entrySet()) {
            if (entry.getKey().chunkPosition().equals(chunk)) {
                found.add(entry);
            }
        }
        return found;
    }

    /**
     * Discards state whose block is no longer a command block (the break
     * path removes the block). Runs once per tick on the world owner.
     */
    public void tick(EngineWorld world) {
        blocks.keySet().removeIf(position ->
                !net.zaminmc.torch.server.block.BuiltinBlocks.isCommandBlock(
                        world.getBlock(position)));
    }

    // ------------------------------------------------------------------
    // The world-change listener (the placement creates the tile — the
    // dispenser pattern; the break sweeps it).
    //
    // The PAIRED arm is the correct port surface: the TRIGGERED flip is
    // a same-block state change in the reference (one block, one
    // property), and the block entity survives its own property flips —
    // the removal arm must fire only when the block TRULY leaves, which
    // only the old/new pair can decide.
    // ------------------------------------------------------------------

    @Override
    public void onBlockChanged(EngineWorld world, BlockPosition position, BlockType newType) {
        // The paired arm below carries the whole law (the TRIGGERED flip
        // needs the old type); the single-type arm answers nothing.
    }

    @Override
    public void onBlockReplaced(EngineWorld world, BlockPosition position,
                                 BlockType oldType, BlockType newType) {
        boolean wasCommand = net.zaminmc.torch.server.block.BuiltinBlocks.isCommandBlock(oldType);
        boolean isCommand = net.zaminmc.torch.server.block.BuiltinBlocks.isCommandBlock(newType);
        if (wasCommand && !isCommand) {
            // The block left: the tile follows (the reference's own
            // removal — the empty drop override rides the drop service).
            remove(position);
            for (Listener listener : listeners) {
                listener.onCommandTileRemoved(position);
            }
        } else if (!wasCommand && isCommand && !blocks.containsKey(position)) {
            // onPlaced lines 86-88: the sendCommandFeedback gamerule is
            // the track-output default.
            createAt(position, trackDefault.getAsBoolean());
        }
        // wasCommand && isCommand: the TRIGGERED flip — the tile
        // survives exactly like the reference's own block entity.
    }
}
