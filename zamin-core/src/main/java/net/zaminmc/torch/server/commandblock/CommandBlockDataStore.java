package net.zaminmc.torch.server.commandblock;

import net.zaminmc.torch.block.BlockPosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * The command-block tile persistence (Slice 72 — the sign store's own
 * envelope): the executor's four rows per tile (the reference's
 * writeNbt Command/SuccessCount/TrackOutput/LastOutput) plus the v2
 * CommandStats tail (Slice 90 — the reference's CommandResultStats
 * writeNbt compound: the bound types' <Name>Name/<Name>Objective pairs,
 * absent in v1 files), the atomic temp-file swap, the
 * corrupt-file-preserving load (a corrupt or missing file reads as
 * empty, the established store behavior).
 */
public final class CommandBlockDataStore {

    private static final Logger LOGGER = Logger.getLogger(CommandBlockDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'C';
    private static final int MAGIC_2 = 'B';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public CommandBlockDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full tile map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, CommandBlockManager.CommandState> blocks) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, blocks.size());
                for (Map.Entry<BlockPosition, CommandBlockManager.CommandState> entry
                        : blocks.entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    CommandBlockManager.CommandState executor = entry.getValue();
                    writeString(out, executor.command());
                    out.writeInt(executor.successCount());
                    out.writeBoolean(executor.trackOutput());
                    writeString(out, executor.lastOutput() == null
                            ? "" : executor.lastOutput());
                    // The v2 CommandStats tail (Slice 90 — CommandBlockBlockEntity's
                    // writeNbt carrying the executor's CommandResultStats): the bound
                    // pairs flattened from the CommandStats compound.
                    writeCommandStats(out, executor.commandResults());
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            LOGGER.severe("Command block save failed: " + e.getMessage());
        }
    }

    /**
     * Loads the tile map; a corrupt or missing file reads as empty (a
     * corrupt file is preserved beside the storage path for manual
     * recovery, the established store behavior).
     */
    public Map<BlockPosition, CommandBlockManager.CommandState> load() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                throw new IllegalArgumentException("bad header");
            }
            int version = in.readUnsignedByte();
            if (version != 1 && version != 2) {
                throw new IllegalArgumentException("bad version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, CommandBlockManager.CommandState> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                String command = readString(in);
                int successCount = in.readInt();
                boolean trackOutput = in.readBoolean();
                String lastOutput = readString(in);
                CommandBlockManager.CommandState state = CommandBlockManager.restoredState(
                        command, successCount, trackOutput,
                        lastOutput.isEmpty() ? null : lastOutput);
                if (version >= 2) {
                    readCommandStats(in, state.commandResults());
                }
                loaded.put(new BlockPosition(x, y, z), state);
            }
            return loaded;
        } catch (Exception cause) {
            LOGGER.severe("Command block store corrupt, loading empty: "
                    + cause.getMessage());
            preserveCorrupt();
            return new LinkedHashMap<>();
        }
    }

    /**
     * The CommandStats tail's write (the reference's compound flattened):
     * the pair count then the key/value strings — every bound type's
     * <Name>Name/<Name>Objective pair.
     */
    private static void writeCommandStats(DataOutputStream out,
            net.zaminmc.torch.server.command.EngineCommandResults results)
            throws java.io.IOException {
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
        results.writeNbt(envelope);
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats =
                net.zaminmc.torch.server.nbt.NbtOps.getCompound(envelope, "CommandStats");
        writeVarInt(out, stats.size());
        for (Map.Entry<String, net.zaminmc.torch.server.nbt.Snbt.Tag> pair : stats.entrySet()) {
            writeString(out, pair.getKey());
            writeString(out, net.zaminmc.torch.server.nbt.NbtOps.getString(stats, pair.getKey()));
        }
    }

    /** The CommandStats tail's read: the pairs back into the compound, the
     * same readNbt walk /stats set rides. */
    private static void readCommandStats(DataInputStream in,
            net.zaminmc.torch.server.command.EngineCommandResults results)
            throws java.io.IOException {
        int pairs = readVarInt(in);
        if (pairs < 0 || pairs > 64) {
            throw new IllegalArgumentException("command-stats pair count out of range: " + pairs);
        }
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> envelope = new LinkedHashMap<>();
        java.util.Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> stats = new LinkedHashMap<>();
        for (int i = 0; i < pairs; i++) {
            String key = readString(in);
            String value = readString(in);
            net.zaminmc.torch.server.nbt.NbtOps.putString(stats, key, value);
        }
        if (!stats.isEmpty()) {
            net.zaminmc.torch.server.nbt.NbtOps.putCompound(envelope, "CommandStats", stats);
            results.readNbt(envelope);
        }
    }

    /** The corrupt file's beside-the-path copy (manual recovery). */
    private void preserveCorrupt() {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception bestEffort) {
            // the load already failed empty; the copy is best-effort
        }
    }

    private static void writeString(DataOutputStream out, String value) throws java.io.IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws java.io.IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 1 << 16) {
            throw new IllegalArgumentException("string length out of range: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws java.io.IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws java.io.IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IllegalArgumentException("VarInt too large");
            }
        }
    }
}
