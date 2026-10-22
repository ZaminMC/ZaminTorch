package net.zaminmc.torch.server.world;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The world-delta serialization job (the Phase-5 adoption's FIRST
 * workload — the persistence-preparation slice S1): the PURE function
 * from an immutable {@link WorldDeltaSnapshot} to the ZWD format's
 * exact bytes. Phase 5's immutable-input contract: the snapshot IN
 * (captured on the owner by the engine's own {@code snapshotDeltas}
 * walk — the authoritative read), the bytes OUT — no world read, no
 * shared state, no I/O anywhere in the function.
 *
 * <p>THE 1:1 SHAPE: the byte layout is the historical
 * {@code DeltaWorldStorage.save} walk verbatim — magic 'Z','W','D',1,
 * the two clocks, the identifier palette (first-seen order), the
 * chunk-keyed deltas (chunkKey long + entry count + (localIndex int +
 * paletteIndex varint))* — so the off-tick serialization and the
 * on-tick serialization produce BYTE-IDENTICAL files (the lane's own
 * proof obligation, the P6-lane equivalence shape). The function never
 * throws on a well-formed snapshot; the {@code IOException} of the
 * stream plumbing is wrapped — a ByteArrayOutputStream cannot fail
 * physically.</p>
 *
 * <p>THE SEPARATION OF CONCERNS: serialization (pure, CPU-bound —
 * this class) is distinct from publication (the file write, I/O-bound
 * — the storage's own walk with the save-sequence gate). The compute
 * side never touches the filesystem; the write side never re-reads
 * the world.</p>
 */
public final class WorldDeltaSerializer {

    private WorldDeltaSerializer() {
    }

    /** The ZWD format's own magic + version (DeltaWorldStorage's constants). */
    public static final int MAGIC_0 = 'Z';
    public static final int MAGIC_1 = 'W';
    public static final int MAGIC_2 = 'D';
    /**
     * v2 (VIII14): the raw day clock after the two wrapped clocks, and the
     * inhabited tail — {@code int count + (chunkKey long + value long)*} —
     * after the delta section. v1 files load through the storage's own
     * migration (raw = wrapped, no tail).
     */
    public static final int FORMAT_VERSION = 2;

    /**
     * Serializes the snapshot to the ZWD v2 bytes — the historical
     * {@code DeltaWorldStorage.save} body plus the VIII14 rows (the raw day
     * clock, the inhabited tail), pure. Byte-identical to the
     * on-tick path by construction (the same code, moved).
     */
    public static byte[] serialize(WorldDeltaSnapshot snapshot) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 16);
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            out.writeLong(snapshot.totalTicks());
            out.writeLong(snapshot.timeOfDay());
            out.writeLong(snapshot.rawTimeOfDay());

            Map<Identifier, Integer> paletteIds = new HashMap<>();
            for (Map<Integer, BlockType> entries : snapshot.deltas().values()) {
                for (BlockType type : entries.values()) {
                    paletteIds.computeIfAbsent(type.identifier(), id -> paletteIds.size());
                }
            }
            out.writeInt(paletteIds.size());
            Identifier[] palette = new Identifier[paletteIds.size()];
            for (Map.Entry<Identifier, Integer> entry : paletteIds.entrySet()) {
                palette[entry.getValue()] = entry.getKey();
            }
            for (Identifier identifier : palette) {
                writeString(out, identifier.toString());
            }

            out.writeInt(snapshot.deltas().size());
            for (Map.Entry<Long, Map<Integer, BlockType>> chunk : snapshot.deltas().entrySet()) {
                out.writeLong(chunk.getKey());
                out.writeInt(chunk.getValue().size());
                for (Map.Entry<Integer, BlockType> entry : chunk.getValue().entrySet()) {
                    out.writeInt(entry.getKey());
                    writeVarInt(out, paletteIds.get(entry.getValue().identifier()));
                }
            }
            // The inhabited tail (v2 — VIII14): the per-chunk accrued rows,
            // chunkKey long + value long each, the count an int like the
            // delta section's own head.
            out.writeInt(snapshot.inhabitedTimes().size());
            for (Map.Entry<Long, Long> row : snapshot.inhabitedTimes().entrySet()) {
                out.writeLong(row.getKey());
                out.writeLong(row.getValue());
            }
        } catch (IOException impossible) {
            // A ByteArrayOutputStream has no physical failure mode; the
            // checked exception is the stream API's own shape.
            throw new IllegalStateException("ZWD serialization failed", impossible);
        }
        return buffer.toByteArray();
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }
}
