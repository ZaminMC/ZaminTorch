package net.zaminmc.torch.server.world.anvil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The region file reader (the Anvil canvas' storage leg) against the
 * fixture writer's reference-shaped bytes: the header parse, the
 * occupied/unoccupied entries, the multi-sector payloads, every §31.6
 * isolation branch — a truncated file, an entry past the file's end, an
 * oversized length, a bad scheme, a corrupt compressed payload — each
 * answered with the typed reason instead of the reference's silent null.
 */
class AnvilRegionFileTest {

    @TempDir
    Path dir;

    /** One small valid chunk document (the walk's smallest real payload). */
    private byte[] smallNbt() {
        Map<String, Object> level = AnvilFixture.compound();
        level.put("xPos", 0);
        level.put("zPos", 0);
        Map<String, Object> root = AnvilFixture.compound();
        root.put("Level", level);
        return AnvilFixture.nbtDocument(root);
    }

    private Path regionFile(String name, List<AnvilFixture.Entry> entries) throws IOException {
        Path file = dir.resolve(name);
        AnvilFixture.writeRegionFile(file, entries);
        return file;
    }

    @Test
    void theHeaderParsesAndTheTimestampsAreReadable() throws IOException {
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(3, 5, AnvilRegionFile.SCHEME_ZLIB, smallNbt())));
        AnvilRegionFile region = AnvilRegionFile.open(file);
        assertTrue(region.isOccupied(3, 5));
        assertTrue(!region.isOccupied(0, 0), "the untouched entry stays unoccupied");
        assertEquals(1_700_000_000, region.timestampOf(3, 5));
        assertEquals(0, region.timestampOf(0, 0));
    }

    @Test
    void anUnoccupiedEntryAnswersTheFormatsOwnEmpty() throws IOException {
        AnvilRegionFile region = AnvilRegionFile.open(
                regionFile("r.0.0.mca", List.of()));
        assertEquals(0, region.locationOf(0, 0));
        assertTrue(region.readChunk(0, 0) instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("unoccupied"),
                "the zero entry is the format's own no-chunk answer, not corruption");
    }

    @Test
    void aGzipPayloadRoundTrips() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_GZIP, AnvilFixture.gzip(nbt))));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.ChunkPayload, reasonOf(read));
        assertArrayEquals(nbt, ((AnvilRegionFile.ChunkPayload) read).nbtBytes());
    }

    @Test
    void aZlibPayloadRoundTrips() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(31, 31, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(nbt))));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(31, 31);
        assertTrue(read instanceof AnvilRegionFile.ChunkPayload, reasonOf(read));
        assertArrayEquals(nbt, ((AnvilRegionFile.ChunkPayload) read).nbtBytes());
    }

    @Test
    void aRawPayloadRoundTrips() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(7, 9, AnvilRegionFile.SCHEME_RAW, nbt)));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(7, 9);
        assertTrue(read instanceof AnvilRegionFile.ChunkPayload, reasonOf(read));
        assertArrayEquals(nbt, ((AnvilRegionFile.ChunkPayload) read).nbtBytes());
    }

    @Test
    void aMultiSectorPayloadRoundTrips() throws IOException {
        // A payload far beyond one sector: a big Blocks row (4096 bytes is
        // one sector alone; the whole document with sections spans three).
        byte[] blocks = new byte[4096];
        blocks[0] = 1; // stone
        byte[] data = new byte[2048];
        byte[] bigNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null));
        assertTrue(bigNbt.length > AnvilRegionFile.SECTOR_BYTES,
                "the document must actually span multiple sectors");
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(1, 2, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(bigNbt))));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(1, 2);
        assertTrue(read instanceof AnvilRegionFile.ChunkPayload, reasonOf(read));
        assertArrayEquals(bigNbt, ((AnvilRegionFile.ChunkPayload) read).nbtBytes());
    }

    @Test
    void severalChunksInOneFileAllRoundTrip() throws IOException {
        byte[] a = smallNbt();
        Map<String, Object> levelB = AnvilFixture.compound();
        levelB.put("xPos", 5);
        levelB.put("zPos", -3);
        Map<String, Object> rootB = AnvilFixture.compound();
        rootB.put("Level", levelB);
        byte[] b = AnvilFixture.nbtDocument(rootB);
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_GZIP, AnvilFixture.gzip(a)),
                new AnvilFixture.Entry(15, 15, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(b))));
        AnvilRegionFile region = AnvilRegionFile.open(file);
        AnvilRegionFile.ChunkRead first = region.readChunk(0, 0);
        AnvilRegionFile.ChunkRead second = region.readChunk(15, 15);
        assertTrue(first instanceof AnvilRegionFile.ChunkPayload, reasonOf(first));
        assertTrue(second instanceof AnvilRegionFile.ChunkPayload, reasonOf(second));
        assertArrayEquals(a, ((AnvilRegionFile.ChunkPayload) first).nbtBytes());
        assertArrayEquals(b, ((AnvilRegionFile.ChunkPayload) second).nbtBytes());
    }

    // ---------------------------------------------------------- the §31.6 isolation branches

    @Test
    void aFileShorterThanTheHeaderIsRefusedWhole() throws IOException {
        Path file = dir.resolve("r.0.0.mca");
        Files.write(file, new byte[100]);
        IOException refused = assertThrows(IOException.class, () -> AnvilRegionFile.open(file));
        assertTrue(refused.getMessage().contains("8 KiB header"),
                "the refusal names the header rule: " + refused.getMessage());
        assertTrue(refused.getMessage().contains("r.0.0.mca"),
                "the refusal names the file: " + refused.getMessage());
    }

    @Test
    void anEntryWhoseSectorsRunPastTheFileIsIsolated() throws IOException {
        // A valid file, then an entry pointing past its end (a truncated
        // tail: the location table claims sectors the file no longer has).
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, nbt)));
        byte[] whole = Files.readAllBytes(file);
        // Point the (0,0) entry at offset 100 with 100 sectors — far past a
        // small file's end (the 4-byte location int: offset<<8 | count).
        whole[0] = 0;
        whole[1] = 0;
        whole[2] = (byte) 100;
        whole[3] = (byte) 100;
        Path truncated = dir.resolve("r.0.0-truncated.mca");
        Files.write(truncated, whole);
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(truncated).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("run past the file's end"),
                "the reason names the bounds rule: " +
                        (read instanceof AnvilRegionFile.Isolated i ? i.reason() : read));
    }

    @Test
    void anOversizedLengthIsIsolated() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, nbt)));
        byte[] whole = Files.readAllBytes(file);
        int location = ((whole[0] & 0xFF) << 24) | ((whole[1] & 0xFF) << 16)
                | ((whole[2] & 0xFF) << 8) | (whole[3] & 0xFF);
        int payloadAt = (location >> 8) * AnvilRegionFile.SECTOR_BYTES;
        // Claim a length far beyond the entry's sector budget (the
        // reference's own l > 4096 * k refusal).
        whole[payloadAt] = 0;
        whole[payloadAt + 1] = 0;
        whole[payloadAt + 2] = (byte) 0x40; // 16384 with one sector budgeted
        whole[payloadAt + 3] = 0;
        Path oversized = dir.resolve("r.0.0-oversized.mca");
        Files.write(oversized, whole);
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(oversized).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("exceeds its"),
                "the reason names the sector budget: " + reasonOf(read));
    }

    @Test
    void aDiskTruncatedTailIsolatesItsEntriesLoudly() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, nbt)));
        byte[] whole = Files.readAllBytes(file);
        // A real-world truncated tail: the last sector cut in half. The
        // entry's read must isolate with a named reason (the sector
        // bounds or the payload bounds — whichever arm fires first),
        // never a crash and never a silent empty chunk.
        Path truncated = dir.resolve("r.0.0-trunc.mca");
        Files.write(truncated, java.util.Arrays.copyOf(whole, whole.length - 2048));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(truncated).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.Isolated,
                "a truncated tail isolates, it does not guess: " + reasonOf(read));
        String reason = reasonOf(read);
        assertTrue(reason.contains("past the file's end") || reason.contains("truncated"),
                "the reason names the bounds rule: " + reason);
    }

    @Test
    void anUnknownSchemeIsIsolated() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, 9, nbt)));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("unknown compression scheme 9"),
                "the reason names the scheme byte: " + reasonOf(read));
    }

    @Test
    void aCorruptCompressedPayloadIsIsolated() throws IOException {
        // Valid gzip bytes, then corrupted: the inflate fails and the
        // reason names the payload, never a crash.
        byte[] gzipped = AnvilFixture.gzip(smallNbt());
        gzipped[gzipped.length / 2] ^= 0x55; // flip payload bits
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(2, 2, AnvilRegionFile.SCHEME_GZIP, gzipped)));
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(file).readChunk(2, 2);
        assertTrue(read instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("corrupt compressed payload"),
                "the reason names the corruption: " + reasonOf(read));
    }

    @Test
    void anEntryOutsideTheGridIsIsolated() throws IOException {
        AnvilRegionFile region = AnvilRegionFile.open(
                regionFile("r.0.0.mca", List.of()));
        assertTrue(region.readChunk(-1, 0) instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("outside the 32x32"));
        assertTrue(region.readChunk(0, 32) instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("outside the 32x32"));
    }

    @Test
    void aZeroSectorCountWithANonzeroOffsetIsIsolated() throws IOException {
        byte[] nbt = smallNbt();
        Path file = regionFile("r.0.0.mca", List.of(
                new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, nbt)));
        byte[] whole = Files.readAllBytes(file);
        // Keep the offset, zero the count byte (the location int's low byte).
        whole[3] = 0;
        Path zeroCount = dir.resolve("r.0.0-zerocount.mca");
        Files.write(zeroCount, whole);
        AnvilRegionFile.ChunkRead read = AnvilRegionFile.open(zeroCount).readChunk(0, 0);
        assertTrue(read instanceof AnvilRegionFile.Isolated isolated
                && isolated.reason().contains("zero sectors"),
                "the reason names the zero-count rule: " + reasonOf(read));
    }

    private static String reasonOf(AnvilRegionFile.ChunkRead read) {
        return read instanceof AnvilRegionFile.Isolated isolated ? isolated.reason() : "?";
    }
}
