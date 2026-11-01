package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-011's parity pin (the correction rides this branch) — the anvil
 * import loads the reference's OCCUPIED bed states. The reference PERSISTS an occupied bed
 * head at metas 12-15 (`BedBlock.getMetadataFromState` lines 220-231: the
 * facing bits 0-1, the head bit 8, and bit 2 written for an OCCUPIED HEAD
 * alone — a world that saves while a player sleeps carries the pair as
 * foot meta 0-3 + head meta 12-15) and READS every one of the sixteen
 * nibbles back as a valid bed state (`getStateFromMetadata` lines
 * 200-205: the occupied bit is read on the head arm; metas 4-7 load as
 * ordinary feet with bit 2 ignored). The engine's
 * {@code AnvilBlockIds} 26-rows (lines 234-241) map ONLY metas 0-3 and
 * 8-11 — every other nibble falls to the counted-isolation AIR path
 * ({@code AnvilChunkReader} lines 316-323), so an imported world saved
 * mid-sleep loses its occupied head as air (a dangling foot where the
 * reference loads the pair whole), and an editor-corrupted foot (bit 2
 * on a foot — unwritable by the reference's own save path) dies the same
 * way.
 *
 * <p>THE PRE-FIX RECORD (the discovery seats, RED on develop with the
 * import's own ledger counting them): the occupied head imported AS AIR
 * (`unknown block states (imported as air): id 26 meta 12 x1`) — the
 * pair loaded as a dangling foot — and the editor-corrupted foot the
 * same (`id 26 meta 5 x1`). THE FIX covers 26's residual band with the
 * reference's own read law: 12-15 → the head ids by facing bits, 4-7 →
 * the foot ids by facing bits (bit 2 masked; the occupied bit is above
 * the engine's sleep model — occupancy is the sleeping session, not
 * the block state).</p>
 *
 * <p>THE SEAT builds a vanilla-shaped region (the fixture's own
 * reference-ported writer): a bed pair saved MID-SLEEP (foot meta 0 +
 * occupied head meta 12) and an editor-corrupted pair (foot meta 5 +
 * head meta 9), imports it through the real {@code anvilimport} command,
 * and asserts the REFERENCE's answers — every one of the sixteen nibbles
 * loads a bed state.</p>
 */
class BedOccupiedImportParityTest {

    @TempDir
    Path dir;

    private net.zaminmc.torch.server.EngineServer engine;

    @AfterEach
    void stop() {
        if (engine != null) {
            engine.shutdown(null);
        }
    }

    /**
     * The vanilla mid-sleep save: the foot at meta 0 (facing south, the
     * head cell at +z) with the head at meta 12 (the same facing, the
     * head bit, AND the occupied bit — {@code getMetadataFromState}
     * lines 220-231 writes {@code 0 | 8 | 4} for an occupied south-facing
     * head). The reference loads the pair WHOLE
     * ({@code getStateFromMetadata} lines 200-205 reads the occupied bit
     * on the head arm alone).
     */
    @Test
    void theOccupiedBedHeadImportsWholeNotAsAir() throws Exception {
        Path vault = dir.resolve("vault");
        java.util.Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 77L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 129);
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L);
        level.put("LevelName", "Vault");
        AnvilFixture.writeLevelDat(vault, level);

        // The mid-sleep pair: foot (129,1,129) meta 0, head (129,1,130) meta 12.
        byte[] blocks = new byte[4096];
        byte[] nibbles = new byte[2048];
        int footIndex = AnvilFixture.cellIndex(1, 1, 1);
        int headIndex = AnvilFixture.cellIndex(1, 1, 2);
        blocks[footIndex] = 26; // the bed foot
        AnvilFixture.nibble(nibbles, footIndex, 0);  // facing south, no bits
        blocks[headIndex] = 26; // the bed head
        AnvilFixture.nibble(nibbles, headIndex, 12); // south | head | OCCUPIED

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, nibbles, null)), null,
                List.of(), List.of()));
        AnvilFixture.writeRegionFile(vault.resolve("region").resolve("r.0.0.mca"),
                List.of(new AnvilFixture.Entry(8, 8, AnvilRegionFile.SCHEME_ZLIB,
                        AnvilFixture.zlib(chunkNbt))));

        String report = runImport(vault);
        var world = engine.world();
        var footCell = world.getBlock(new net.zaminmc.torch.block.BlockPosition(129, 1, 129));
        var headCell = world.getBlock(new net.zaminmc.torch.block.BlockPosition(129, 1, 130));
        // The control first: the foot nibble 0 is one of the table's rows —
        // it imports on BOTH trees (the divergence is the head).
        assertTrue(net.zaminmc.torch.server.EngineServer.isBedHalf(footCell),
                "the foot imports (the table's (26,0) row — the control; report: " + report + ")");
        // THE DIVERGENCE: the reference loads the occupied head whole —
        // the engine's table has no (26,12) row and imports AIR.
        assertTrue(net.zaminmc.torch.server.EngineServer.isBedHalf(headCell),
                "VT-011: the OCCUPIED head imports whole (the reference's getStateFromMetadata "
                        + "lines 200-205 read every one of the sixteen nibbles; the engine's "
                        + "table maps only 0-3 and 8-11 and imports AIR here — the pair loads "
                        + "as a dangling foot; report: " + report + ")");
        assertEquals("bed_head", headCell.identifier().value(),
                "the occupied south head resolves to the plain head id (the occupied bit is "
                        + "above the engine's sleep model; report: " + report + ")");
    }

    /**
     * The editor-corrupted foot: bit 2 on a FOOT (meta 5 = west facing |
     * the stray bit). The reference's own save path never writes it
     * (`getMetadataFromState` writes bit 2 for heads alone), but external
     * editors produce it, and the reference loads it as an ordinary foot
     * facing west (`getStateFromMetadata` lines 200-205: the occupied bit
     * is read on the HEAD arm alone — the foot's bits 0-1 are the whole
     * answer).
     */
    @Test
    void theCorruptedFootNibbleLoadsAsAFootNotAsAir() throws Exception {
        Path vault = dir.resolve("vault");
        java.util.Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 77L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 129);
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L);
        level.put("LevelName", "Vault");
        AnvilFixture.writeLevelDat(vault, level);

        // The corrupted pair: foot (133,1,133) meta 5 (west | bit 2), head
        // (132,1,133) meta 9 (west | head — the table's own row, the control).
        byte[] blocks = new byte[4096];
        byte[] nibbles = new byte[2048];
        int footIndex = AnvilFixture.cellIndex(5, 1, 5);
        int headIndex = AnvilFixture.cellIndex(4, 1, 5);
        blocks[footIndex] = 26;
        AnvilFixture.nibble(nibbles, footIndex, 5); // west + the stray bit
        blocks[headIndex] = 26;
        AnvilFixture.nibble(nibbles, headIndex, 9); // west head — a table row

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, nibbles, null)), null,
                List.of(), List.of()));
        AnvilFixture.writeRegionFile(vault.resolve("region").resolve("r.0.0.mca"),
                List.of(new AnvilFixture.Entry(8, 8, AnvilRegionFile.SCHEME_ZLIB,
                        AnvilFixture.zlib(chunkNbt))));

        String report = runImport(vault);
        var world = engine.world();
        var footCell = world.getBlock(new net.zaminmc.torch.block.BlockPosition(133, 1, 133));
        var headCell = world.getBlock(new net.zaminmc.torch.block.BlockPosition(132, 1, 133));
        // The control: the head nibble 9 is one of the table's rows.
        assertTrue(net.zaminmc.torch.server.EngineServer.isBedHalf(headCell),
                "the west head imports (the table's (26,9) row — the control; report: " + report + ")");
        // THE DIVERGENCE: the reference loads the corrupted foot as an
        // ordinary west-facing foot — the engine's table has no (26,5) row.
        assertTrue(net.zaminmc.torch.server.EngineServer.isBedHalf(footCell),
                "VT-011: the corrupted foot nibble loads as a foot (the reference's foot arm "
                        + "ignores bit 2 — bits 0-1 alone are the answer; the engine's table "
                        + "imports AIR here; report: " + report + ")");
        assertEquals("bed_west", footCell.identifier().value(),
                "the corrupted west foot resolves to the plain west foot id (report: " + report + ")");
    }

    /**
     * Boots the engine, runs the real import command, awaits the finish
     * report, and answers it (the acceptance test's own console-capture
     * pattern — the report carries the isolation ledger's own summary,
     * the counted evidence this finding's record cites).
     */
    private String runImport(Path vault) throws Exception {
        List<String> console = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) {
                console.add(record.getMessage());
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        Logger consoleLogger = Logger.getLogger(
                net.zaminmc.torch.server.chat.ConsoleSender.class.getName());
        consoleLogger.addHandler(capture);
        consoleLogger.setLevel(Level.INFO);
        consoleLogger.setUseParentHandlers(false);

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-bed", "it",
                20, 2, 20, dir.toString());
        engine = new net.zaminmc.torch.server.EngineServer(config);
        engine.start();
        try {
            engine.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                EngineAwaits.awaitTicks(engine.world(), 1, "engine progress (a 50 wall settle)");
            }
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");
            return report;
        } finally {
            consoleLogger.removeHandler(capture);
        }
    }
}
