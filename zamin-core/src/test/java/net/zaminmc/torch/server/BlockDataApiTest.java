package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockData;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block-data API (Slice 14b — the §27.3 block-state family read over
 * the live engine): the frozen registry's survival data through the public
 * {@link Server#blocks()} mount — hardness, the tool gates, the material,
 * the light pair, the silk gate, and the drop table — plus the pure
 * break-timing helper.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockDataApiTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "blockdata", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theRegistryMountCarriesTheFrozenSet() throws Exception {
        EngineServer server = boot();
        assertNotNull(server.blocks(), "the public registry mount exists");
        assertTrue(server.blocks().all().size() > 100, "the frozen set is populated");
        assertTrue(server.blocks().lookup(Identifier.parse("minecraft:stone")).isPresent());
        server.shutdown(null);
    }

    @Test
    void theStoneRowCarriesTheSurvivalData() throws Exception {
        EngineServer server = boot();
        BlockType stone = server.blocks().require(Identifier.parse("minecraft:stone"));
        BlockData data = server.blocks().dataOf(stone);
        assertEquals(1.5, data.hardness(), 1e-9);
        assertTrue(data.diggable());
        assertTrue(data.requiresTool(), "stone needs a pickaxe for drops");
        assertEquals("rock", data.material());
        assertEquals(1, data.harvestLevel());
        assertTrue(data.opaque(), "stone fully filters light");
        assertFalse(data.silkTouchable() == false, "stone IS silk touchable");
        assertEquals(1, data.drops().size());
        assertEquals(Identifier.parse("minecraft:cobblestone"), data.drops().get(0).item());
        server.shutdown(null);
    }

    @Test
    void theLightPairAndTheSilkGate() throws Exception {
        EngineServer server = boot();
        BlockData torch = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:torch")));
        assertEquals(14, torch.lightEmission(), "torch emits 14");
        assertEquals(0, torch.lightFilter(), "the torch never blocks light");
        assertTrue(torch.emitsLight());
        assertFalse(torch.opaque());
        assertFalse(torch.silkTouchable(), "the torch drops itself either way");

        BlockData glass = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:glass")));
        assertTrue(glass.silkTouchable(), "glass only survives under silk");
        server.shutdown(null);
    }

    @Test
    void theDropTableCarriesTheRollOrder() throws Exception {
        EngineServer server = boot();
        BlockData gravel = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:gravel")));
        assertEquals(2, gravel.drops().size(), "flint first, gravel otherwise");
        assertEquals(Identifier.parse("minecraft:flint"), gravel.drops().get(0).item());
        assertEquals(0.1, gravel.drops().get(0).chance(), 1e-9, "the 10% flint roll");
        assertEquals(Identifier.parse("minecraft:gravel"), gravel.drops().get(1).item());
        assertEquals(1.0, gravel.drops().get(1).chance(), 1e-9);

        BlockData air = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:air")));
        assertTrue(air.drops().isEmpty());
        assertFalse(air.diggable());
        server.shutdown(null);
    }

    @Test
    void theBreakTimingHelperMatchesTheHistoricalTable() throws Exception {
        EngineServer server = boot();
        BlockData stone = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:stone")));
        // The wiki-pinned timings: stone by hand 7.5s = 150 ticks; a
        // wooden pickaxe (speed 2, harvestable) 1.5*30/2 = 22.5 -> 23.
        assertEquals(150, stone.breakTicks(1.0, false));
        assertEquals(23, stone.breakTicks(2.0, true));

        BlockData dirt = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:dirt")));
        // Dirt (0.5, no tool): 0.5*30 = 15 ticks by hand — harvestable
        // without a tool.
        assertEquals(15, dirt.breakTicks(1.0, true));

        // The unbreakable answer: bedrock never completes.
        BlockData bedrock = server.blocks().dataOf(
                server.blocks().require(Identifier.parse("minecraft:bedrock")));
        assertEquals(Integer.MAX_VALUE, bedrock.breakTicks(1.0, true));

        // The read is total over the whole frozen set — no registered
        // type answers null or throws.
        for (BlockType type : server.blocks().all()) {
            BlockData data = server.blocks().dataOf(type);
            assertNotNull(data);
            assertNotNull(data.drops());
        }
        server.shutdown(null);
    }
}
