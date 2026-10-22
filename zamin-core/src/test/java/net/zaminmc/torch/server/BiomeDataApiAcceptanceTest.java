package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BiomeData;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The biome registration table's public API surface (§27.3 — the VIII14
 * slice-1 exposure, the §27.10 capability test): the 61-row census with
 * its registration datums — the display names, the temperature/downfall
 * pairs, the height pairs, and the isRainy read law — plus the
 * by-position lookup, all exercised over the LIVE engine through the api
 * types alone (no cast to the engine's registration table): {@code
 * World.biomes()} enumerates the census and {@code World.biomeDataAt}
 * rides the same chunk-stored-row resolution {@code World.biomeAt}
 * rides.
 *
 * <p>The scene's deterministic column law: the flat world's generator
 * never writes a biome row (the chunk constructor's own fill is plains),
 * so the loaded path and the unloaded implicit default agree on the
 * plains row — and the two exotic columns (the frozen ocean, the cold
 * taiga), placed through the engine-public chunk surface (the test's
 * scene setup, the village test's own discipline — the READS under test
 * stay api-only), prove the read is the chunk's OWN stored row, never a
 * constant. (The mutated band's ids exceed the signed byte the chunk
 * array stores — the engine's shared read path widens them negative —
 * so the by-position proof rides the positive band; the mutated rows'
 * datums answer through the census reads.)</p>
 */
class BiomeDataApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private World world;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bda" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        world = server.world();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * The whole 61-row census through the public surface: the reference's
     * own count and id order (the 40 base ids 0-39 + the 21 mutated ids
     * 129-167), both name keys unique, and the pinned registration rows —
     * the temperature/downfall pairs, the height pairs (the standard
     * mutation's +0.1/+0.2 bump and the four custom shapes' own laws),
     * and the display names byte-exact.
     */
    @Test
    void theCensusAnswersTheReferenceRegistration() {
        List<BiomeData> census = world.biomes();
        assertNotNull(census, "the api surface is wired");
        assertEquals(61, census.size(), "the reference's 61-row census");

        // The id order: the 40 base rows then the 21 mutated rows, the
        // reference's own registration sequence.
        int[] expectedIds = {
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30,
                31, 32, 33, 34, 35, 36, 37, 38, 39,
                129, 130, 131, 132, 133, 134, 140, 149, 151, 155, 156,
                157, 158, 160, 161, 162, 163, 164, 165, 166, 167};
        assertEquals(61, expectedIds.length, "the expected census's own size");
        for (int i = 0; i < expectedIds.length; i++) {
            assertEquals(expectedIds[i], census.get(i).legacyId(),
                    "census row " + i + " carries the reference's id order");
        }

        // Both keys stay unique (the stable name is what biomeAt answers;
        // the display name is the reference's BY_NAME key).
        Set<String> names = new HashSet<>();
        Set<String> displayNames = new HashSet<>();
        for (BiomeData row : census) {
            names.add(row.name());
            displayNames.add(row.displayName());
        }
        assertEquals(61, names.size(), "the stable names identify one row each");
        assertEquals(61, displayNames.size(), "the display names identify one row each");

        // The pinned rows (Biome.java's own table — the registration
        // datum the ctor chain sets, verified against the reference).
        pin("Plains", 1, "plains", 0.8f, 0.4f, 0.125f, 0.05f, false, true);
        pin("Desert", 2, "desert", 2.0f, 0.0f, 0.125f, 0.05f, false, false);
        pin("Swampland", 6, "swampland", 0.8f, 0.9f, -0.2f, 0.1f, false, true);
        pin("Extreme Hills+", 34, "extreme_hills_plus", 0.2f, 0.3f, 1.0f, 0.5f,
                false, true);
        // The frozen family: the snowy flag set, the byte-exact "FrozenOcean"
        // without the space.
        pin("FrozenOcean", 10, "frozen_ocean", 0.0f, 0.5f, -1.0f, 0.1f, true, false);
        // The mutation law's custom shapes: the fresh-ctor Sunflower Plains
        // (the plains pair whole, NO bump), the ice spikes' own +0.3/+0.4
        // height overwrite (the reference's dead setHeight quirk), the
        // fresh variant-2 mega spruce taiga (0.25 against the base's 0.3),
        // and the savanna's shattered temperature/height laws.
        pin("Sunflower Plains", 129, "sunflower_plains", 0.8f, 0.4f, 0.125f,
                0.05f, false, true);
        pin("Ice Plains Spikes", 140, "ice_plains_spikes", 0.0f, 0.5f, 0.425f,
                0.45f, true, false);
        pin("Mega Spruce Taiga", 160, "mega_spruce_taiga", 0.25f, 0.8f, 0.2f,
                0.2f, false, true);
        pin("Savanna M", 163, "savanna_m", 1.1f, 0.0f, 0.3625f, 1.225f,
                false, false);
        // The standard mutation's +0.1/+0.2 height bump over the desert.
        pin("Desert M", 130, "desert_m", 2.0f, 0.0f, 0.225f, 0.25f, false, false);
    }

    /**
     * The by-position lookup rides the SAME resolution the stable name
     * rides: the loaded flat column answers the ocean row (the
     * generator's unwritten id-0 default), the unloaded column answers
     * the implicit plains default's OWN datums (never a gap), and a
     * column the chunk stores as an exotic row answers that row — the
     * row's name always the string {@code biomeAt} answers at the same
     * position.
     */
    @Test
    void theByPositionLookupRidesTheSameResolutionAsBiomeAt() throws Exception {
        BlockPosition cell = new BlockPosition(2, 40, 2);
        server.requestChunkLoad(cell.chunkPosition(), chunk -> { });
        await(() -> world.isChunkLoaded(cell.chunkPosition()), "the scene's chunk loaded");

        // The loaded flat column: the generator never writes a biome row,
        // and the chunk's own constructor fills plains — so the loaded
        // path and the unloaded default below agree on the same row (the
        // two resolution paths answering one another; the exotic columns
        // further down prove the read is the chunk's OWN stored row, never
        // a constant).
        BiomeData loaded = world.biomeDataAt(cell);
        assertNotNull(loaded, "the loaded column's row answers");
        assertEquals("plains", world.biomeAt(cell), "the flat column's stored row");
        assertEquals("plains", loaded.name(), "the row's name is the stable name");
        assertEquals("Plains", loaded.displayName(), "the display name");
        assertEquals(1, loaded.legacyId(), "the constructor-filled plains id");
        assertEquals(0.8f, loaded.temperature(), "the plains' registration temperature");
        assertEquals(0.4f, loaded.downfall(), "the plains' registration downfall");
        assertEquals(0.125f, loaded.baseHeight(), "the plains height band's base");
        assertEquals(0.05f, loaded.heightVariation(), "the plains height band's variation");
        assertTrue(loaded.rainy(), "the plains column can rain");
        assertTrue(world.biomes().contains(loaded),
                "the by-position row IS a census row");

        // The unloaded column: the implicit default's own datums — the
        // plains row the "plains" stable name comes from, never a gap.
        BlockPosition far = new BlockPosition(3_000, 40, 3_000);
        assertEquals("plains", world.biomeAt(far), "the unloaded column's implicit default");
        BiomeData plains = world.biomeDataAt(far);
        assertNotNull(plains, "the default answers a row, never null");
        assertEquals("plains", plains.name(), "the default's stable name");
        assertEquals("Plains", plains.displayName(), "the default's display name");
        assertEquals(1, plains.legacyId(), "the default's own id");
        assertEquals(0.8f, plains.temperature(), "the plains' registration temperature");
        assertEquals(0.4f, plains.downfall(), "the plains' registration downfall");
        assertEquals(0.125f, plains.baseHeight(), "the plains height band's base");
        assertEquals(0.05f, plains.heightVariation(), "the plains height band's variation");

        // The exotic columns: the chunk's own stored rows — two positive-band
        // rows (the frozen ocean, the negative-temperature cold taiga) —
        // read back through the same resolution, both agreeing with the
        // stable name at the same position. (The mutated band's ids exceed
        // the signed byte the chunk array stores; the census reads below
        // and the census test carry those rows' datums.)
        var chunk = server.peekChunk(cell.chunkPosition());
        assertNotNull(chunk, "the loaded chunk peeks");
        world.execute(() -> {
            chunk.setBiome(2, 2, (byte) 10);
            chunk.setBiome(3, 2, (byte) 30);
        });
        await(() -> "frozen_ocean".equals(world.biomeAt(cell)),
                "the stored frozen row reads back through the stable name");
        BiomeData frozen = world.biomeDataAt(cell);
        assertEquals("frozen_ocean", frozen.name(), "the frozen row's stable name");
        assertEquals("FrozenOcean", frozen.displayName(),
                "the frozen row's byte-exact display name");
        assertEquals(10, frozen.legacyId(), "the frozen row's own id");
        assertEquals(0.0f, frozen.temperature(), "the frozen row's temperature");
        assertTrue(frozen.snowy(), "the frozen row's snowy flag");
        assertFalse(frozen.rainy(), "the frozen row's rain read refuses (the law)");

        BlockPosition cold = new BlockPosition(3, 40, 2);
        assertEquals("cold_taiga", world.biomeAt(cold),
                "the stored cold row reads back");
        BiomeData taiga = world.biomeDataAt(cold);
        assertEquals("Cold Taiga", taiga.displayName(), "the cold row's display name");
        assertEquals(30, taiga.legacyId(), "the cold row's own id");
        assertEquals(-0.5f, taiga.temperature(), "the cold row's negative temperature band");
        assertTrue(taiga.snowy(), "the cold row's snowy flag");
        assertFalse(taiga.rainy(), "the cold row's rain read refuses (the law)");
        assertEquals(taiga, world.biomeDataAt(cold),
                "the immutable table answers the same row every read");
    }

    /**
     * The isRainy read law's own rows (the reference's {@code isRainy()}
     * lines 393-395 — {@code !isCold() && rainy}): the frozen family
     * NEVER reports rain (the field stays true, the READ refuses), the
     * {@code disableRain} family refuses everywhere, and the rainy rows
     * answer — with the law's shape holding over the whole census (no
     * snowy row ever reports rain).
     */
    @Test
    void theIsRainyReadLawAnswersTheReferenceRows() {
        // The frozen family (Biome.java's javadoc's own rows): the frozen
        // ocean and river, the ice plains pair, the cold beach, the cold
        // taiga pair.
        for (String frozen : List.of("FrozenOcean", "FrozenRiver", "Ice Plains",
                "Ice Plains Spikes", "Cold Beach", "Cold Taiga", "Cold Taiga M")) {
            BiomeData row = row(frozen);
            assertTrue(row.snowy(), frozen + " carries the snowy flag");
            assertFalse(row.rainy(), frozen + " never reports rain (the read law)");
        }
        // The disableRain family: the deserts, hell, the end, the savanna
        // pair, the mesa family — refuses everywhere, snowy or not.
        for (String dry : List.of("Desert", "Desert M", "Hell", "The End",
                "Savanna", "Savanna Plateau", "Savanna M", "Mesa", "Mesa (Bryce)")) {
            BiomeData row = row(dry);
            assertFalse(row.rainy(), dry + " never reports rain (disableRain)");
        }
        // The rainy rows: the read answers true where the weather rains.
        for (String wet : List.of("Ocean", "Plains", "Swampland", "Jungle",
                "MushroomIsland")) {
            assertTrue(row(wet).rainy(), wet + " reports rain");
        }
        // The law's shape over the whole census: no snowy row reports
        // rain (the read's own left operand refuses it).
        for (BiomeData row : world.biomes()) {
            assertFalse(row.snowy() && row.rainy(),
                    row.displayName() + " never carries snowy AND rainy");
        }
    }

    /**
     * The §27.6 any-thread contract: the registration table is immutable,
     * so the census and the by-position reads stay consistent from a
     * foreign thread against the live tick lane — the same rows, the
     * same datums, every read.
     */
    @Test
    void theCensusReadsFromAForeignThread() throws Exception {
        BlockPosition cell = new BlockPosition(2, 40, 2);
        server.requestChunkLoad(cell.chunkPosition(), chunk -> { });
        await(() -> world.isChunkLoaded(cell.chunkPosition()), "the scene's chunk loaded");
        BiomeData pinned = world.biomeDataAt(cell);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        Thread hammer = new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    List<BiomeData> census = world.biomes();
                    if (census.size() != 61) {
                        throw new IllegalStateException("a torn census: " + census.size());
                    }
                    BiomeData at = world.biomeDataAt(cell);
                    if (!pinned.equals(at)) {
                        throw new IllegalStateException("a torn row: " + at);
                    }
                    world.biomeDataAt(new BlockPosition(3_000, 40, 3_000));
                }
            } catch (RuntimeException e) {
                failure.set(e);
            } finally {
                done.countDown();
            }
        }, "biome-census-hammer");
        hammer.start();
        // Let the tick lane work against the hammer's reads.
        EngineAwaits.awaitTicks(server.world(), 15, "engine progress (a 750 wall settle)");
        hammer.join(30_000);
        assertTrue(done.getCount() == 0, "the hammer finished");
        RuntimeException caught = failure.get();
        if (caught != null) {
            throw caught;
        }
        // The census stays intact after the hammer (the same immutable
        // rows answer on the engine's own threads too).
        assertEquals(61, world.biomes().size(), "the census intact after the hammer");
    }

    // ------------------------------------------------------------- helpers

    /** The census row for a display name (the reference's own key). */
    private BiomeData row(String displayName) {
        for (BiomeData candidate : world.biomes()) {
            if (candidate.displayName().equals(displayName)) {
                return candidate;
            }
        }
        throw new AssertionError("No census row for display name: " + displayName);
    }

    /** One pinned registration row (every datum against the table). */
    private void pin(String displayName, int legacyId, String name,
            float temperature, float downfall, float baseHeight,
            float heightVariation, boolean snowy, boolean rainy) {
        BiomeData row = row(displayName);
        assertEquals(legacyId, row.legacyId(), displayName + "'s id");
        assertEquals(name, row.name(), displayName + "'s stable name");
        assertEquals(displayName, row.displayName(), displayName + "'s display name");
        assertEquals(temperature, row.temperature(), displayName + "'s temperature");
        assertEquals(downfall, row.downfall(), displayName + "'s downfall");
        assertEquals(baseHeight, row.baseHeight(), displayName + "'s base height");
        assertEquals(heightVariation, row.heightVariation(),
                displayName + "'s height variation");
        assertEquals(snowy, row.snowy(), displayName + "'s snowy flag");
        assertEquals(rainy, row.rainy(), displayName + "'s rain read");
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
