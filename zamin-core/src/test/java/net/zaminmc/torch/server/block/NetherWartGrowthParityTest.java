package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The nether wart's growth seats (the verifier's fourteenth pass, the
 * planting-arms restoration): the reference's own laws over the engine's
 * new random-tick arm —
 *
 * <p>THE GROWTH (NetherWartBlock.tick lines 38-46): {@code age < 3 &&
 * random.nextInt(10) == 0} — the 1-in-10 band with NO LIGHT GATE (the wart
 * grows in the dark; the nether carries no sky), then PlantBlock.tick's
 * own support consult (lines 46-48 -> canSurviveOrBreak 50-55): the
 * soul-sand read, the failed check DROPPING the block with its item
 * (NetherWartBlock.dropItems lines 49-63: age 3 -> 2 + nextInt(3), else
 * exactly one). The pre-audit engine had NO arm at all: a planted or
 * imported wart never grew and never popped.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NetherWartGrowthParityTest {

    private EngineWorld world;
    private RandomTickSystem ticks;
    private net.zaminmc.torch.server.entity.ItemEntityManager popItems;

    @BeforeAll
    void setUp() {
        FrozenBlockRegistry registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        world = new EngineWorld("wart-parity", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        ticks = new RandomTickSystem(world, new Random(0x0BEEF1L));
        popItems = new net.zaminmc.torch.server.entity.ItemEntityManager(
                (x, y, z) -> false, new Random(0x0D0DL), 12000);
        ticks.setPlantPopItems(popItems);
    }

    /** The wart grows under a STONE lid — no light gate, the 1-in-10 band. */
    @Test
    void theWartGrowsAtOneInTenWithNoLightGate() {
        BlockPosition sand = new BlockPosition(8, 4, 8);
        BlockPosition wart = sand.offset(0, 1, 0);
        world.setBlock(sand, BuiltinBlocks.SOUL_SAND);
        world.setBlock(wart, BuiltinBlocks.NETHER_WART_STAGE0);
        // The stone lid: the cell above the wart is solid — the light
        // under it is dark (the reference's own nether farm shape).
        world.setBlock(wart.offset(0, 1, 0), BuiltinBlocks.STONE);

        int drives = 0;
        while (world.getBlock(wart).equals(BuiltinBlocks.NETHER_WART_STAGE0)
                && drives < 400) {
            ticks.randomTick(wart);
            drives++;
        }
        assertTrue(!world.getBlock(wart).equals(BuiltinBlocks.NETHER_WART_STAGE0),
                "the wart advanced under the stone lid (NO light gate — the mean 10, "
                        + "observed " + drives + ")");
        assertTrue(drives >= 3 && drives <= 40,
                "the 1-in-10 band's own spread (observed " + drives
                        + "; the pre-audit engine never grew a wart)");
        world.setBlock(wart.offset(0, 1, 0), world.airType());
    }

    /** The AGE stops at 3: the mature wart never becomes a fourth stage. */
    @Test
    void theWartStopsAtStageThree() {
        BlockPosition sand = new BlockPosition(16, 4, 8);
        BlockPosition wart = sand.offset(0, 1, 0);
        world.setBlock(sand, BuiltinBlocks.SOUL_SAND);
        world.setBlock(wart, BuiltinBlocks.NETHER_WART_STAGE3);
        for (int i = 0; i < 300; i++) {
            ticks.randomTick(wart);
        }
        assertEquals(BuiltinBlocks.NETHER_WART_STAGE3, world.getBlock(wart),
                "the age 3 cap (the reference's own i < 3 gate)");
    }

    /**
     * The tick-side support consult (PlantBlock.tick -> canSurviveOrBreak):
     * a wart whose soul sand left pops with its item — the stage-3 band
     * answers 2-4 (2 + nextInt(3)); the younger stages exactly one.
     */
    @Test
    void theWartPopsWithTheAgeBandDrop() {
        // A stage-3 wart floating over dirt: the pop's item is 2-4 warts.
        BlockPosition floor = new BlockPosition(24, 4, 8);
        BlockPosition wart = floor.offset(0, 1, 0);
        world.setBlock(floor, BuiltinBlocks.DIRT);
        world.setBlock(wart, BuiltinBlocks.NETHER_WART_STAGE3);
        long before = popItems.size();
        ticks.randomTick(wart);
        assertEquals(BuiltinBlocks.AIR, world.getBlock(wart),
                "the pop (the soul-sand consult failed)");
        assertEquals(before + 1, popItems.size(), "the popped wart's item landed");
        var dropped = popItems.all().get(popItems.all().size() - 1).stack();
        int count = dropped.count();
        assertTrue(count >= 2 && count <= 4,
                "the stage-3 band 2 + nextInt(3) (observed " + count + ")");
        assertEquals("minecraft:nether_wart", dropped.type().identifier().toString());

        // A stage-1 wart: exactly one.
        BlockPosition floor2 = new BlockPosition(32, 4, 8);
        BlockPosition wart2 = floor2.offset(0, 1, 0);
        world.setBlock(floor2, BuiltinBlocks.DIRT);
        world.setBlock(wart2, BuiltinBlocks.NETHER_WART_STAGE1);
        long before2 = popItems.size();
        ticks.randomTick(wart2);
        assertEquals(BuiltinBlocks.AIR, world.getBlock(wart2), "the pop");
        assertEquals(before2 + 1, popItems.size(), "the item landed");
        assertEquals(1, popItems.all().get(popItems.all().size() - 1).stack().count(),
                "the younger stage's exactly-one law");
    }
}
