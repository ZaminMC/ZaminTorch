package net.zaminmc.torch.server.weather;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ice/snow precipitation arm's own law (Slice 21c — the reference's
 * ServerWorld.tickChunks lines 325-341 + World.canFreeze lines 1927-1954 +
 * canSnowFall lines 1960-1978): the 1-in-16 roll with the LCG-derived pick,
 * the freeze without the rain gate (the cold freezes dry), the
 * surrounded-by-water shore rule, the light band, the snow's rain gate +
 * the air + the support, and the temperature's refusal on the warm set.
 *
 * <p>Determinism: the scripted {@link AlwaysHits} Random answers 0 for the
 * 1-in-16 roll, so every call lands and the pick follows the LCG's own
 * first advance — 1013904223 >> 2 = 253476055, (pick &amp; 15, pick >> 8
 * &amp; 15) = (7, 12) — the same arithmetic the reference derives.</p>
 */
class PrecipitationSystemTest {

    /** The roll-always Random (the 1-in-16 gate passes every call). */
    private static final class AlwaysHits extends Random {
        @Override
        public int nextInt(int bound) {
            return 0; // the roll lands; the LCG owns the pick
        }
    }

    /** The first landed pick inside chunk (0, 0): the LCG's own (7, 12). */
    private static final int PICK_X = 7;
    private static final int PICK_Z = 12;
    private static final int SURFACE_Y = 64;

    /** A cold column surface over a scripted world (the frozen-biome stub). */
    private static final class FrozenSurface implements PrecipitationSystem.Surface {
        final List<int[]> iceWrites = new ArrayList<>();
        final List<int[]> snowWrites = new ArrayList<>();
        /** The cells holding the water source (the freeze's target). */
        final List<Long> waterCells = new ArrayList<>();
        /** The cell with block light ≥ 10 (both arms' refusal). */
        Long litCell = null;
        /** The warm column (the temperature refusal). */
        boolean warm = false;
        /** The support gate's off switch (canBePlaced's refusal). */
        boolean supportless = false;

        private static long pack(int x, int y, int z) {
            return ((long) x << 42) | ((long) y << 21) | (long) z;
        }

        void water(int x, int y, int z) {
            waterCells.add(pack(x, y, z));
        }

        @Override
        public int precipitationHeight(int x, int z) {
            return SURFACE_Y;
        }

        @Override
        public BlockType blockAt(int x, int y, int z) {
            return waterCells.contains(pack(x, y, z))
                    ? FluidBlocks.sourceOf(FluidBlocks.Kind.WATER)
                    : BuiltinBlocks.STONE;
        }

        @Override
        public int blockLightAt(int x, int y, int z) {
            return litCell != null && litCell == pack(x, y, z) ? 12 : 0;
        }

        @Override
        public boolean isWaterSource(BlockType type) {
            return type.identifier().equals(FluidBlocks.WATER_SOURCE);
        }

        @Override
        public boolean isAirAt(int x, int y, int z) {
            return true; // the surface cell reads air (the snow's gate)
        }

        @Override
        public boolean isSolidAt(int x, int y, int z) {
            return !supportless;
        }

        @Override
        public float temperatureAt(int x, int z) {
            return warm ? 0.8f : 0.0f;
        }

        @Override
        public void placeIceAt(int x, int y, int z) {
            iceWrites.add(new int[]{x, y, z});
        }

        @Override
        public void placeSnowAt(int x, int y, int z) {
            snowWrites.add(new int[]{x, y, z});
        }
    }

    /**
     * The pick's own derivation: the roll lands, the LCG advances once, and
     * the surface's reads name the derived cell — (7, 12) over the chunk
     * origin (0, 0), the first LCG value 1013904223's own arithmetic.
     */
    @Test
    void thePickFollowsTheLcgDerivation() {
        // The LCG's first advance: 0 * 3 + 1013904223 = 0x3C6EF35F;
        // pick = 0x3C6EF35F >> 2 = 0x0F1BBCD7; x = 0xD7 & 0xF = 7;
        // z = (0x0F1BBCD7 >> 8) & 0xF = 0xC = 12.
        FrozenSurface surface = new FrozenSurface();
        PrecipitationSystem system = new PrecipitationSystem(surface, new AlwaysHits());
        // Dry air everywhere: the pick runs, no write can land.
        system.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(List.of(), surface.iceWrites);
        assertEquals(List.of(), surface.snowWrites);
        // The very same single call over the water cell at the derived
        // pick freezes it — the pick named (7, 12) exactly.
        FrozenSurface withWater = new FrozenSurface();
        withWater.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        PrecipitationSystem withWaterSystem = new PrecipitationSystem(withWater, new AlwaysHits());
        withWaterSystem.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(1, withWater.iceWrites.size(), "the derived pick froze its cell");
        assertEquals(PICK_X, withWater.iceWrites.get(0)[0]);
        assertEquals(SURFACE_Y - 1, withWater.iceWrites.get(0)[1]);
        assertEquals(PICK_Z, withWater.iceWrites.get(0)[2]);
    }

    /**
     * The freeze arm (canFreezeNaturally, lines 1927-1954): the water
     * source under the open sky freezes with NO rain falling (the cold
     * freezes dry — the reference's own arm), and the surrounded-by-water
     * shore rule refuses the mid-lake cells.
     */
    @Test
    void theFreezeRunsDryAndHonorsTheShoreRule() {
        // The shore's edge: stone neighbors on two sides — it freezes dry.
        FrozenSurface shore = new FrozenSurface();
        shore.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        shore.water(PICK_X - 1, SURFACE_Y - 1, PICK_Z); // one water neighbor
        PrecipitationSystem system = new PrecipitationSystem(shore, new AlwaysHits());
        system.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(1, shore.iceWrites.size(), "the shore's edge froze");
        assertEquals(SURFACE_Y - 1, shore.iceWrites.get(0)[1],
                "the freeze lands below the surface");

        // The lake's heart: water on all four horizontal sides — refused.
        FrozenSurface lake = new FrozenSurface();
        lake.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        lake.water(PICK_X - 1, SURFACE_Y - 1, PICK_Z);
        lake.water(PICK_X + 1, SURFACE_Y - 1, PICK_Z);
        lake.water(PICK_X, SURFACE_Y - 1, PICK_Z - 1);
        lake.water(PICK_X, SURFACE_Y - 1, PICK_Z + 1);
        PrecipitationSystem heart = new PrecipitationSystem(lake, new AlwaysHits());
        heart.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(0, lake.iceWrites.size(),
                "the surrounded water never freezes (the shore rule)");
    }

    /**
     * The gates: the warm temperature refuses both arms (the engine's whole
     * generated set — the vanilla-plains envelope), the light ≥ 10 refuses,
     * and the snow needs the rain (the freeze never does) plus the support.
     */
    @Test
    void theGatesRefuseWarmLightAndDrySnow() {
        // The warm column (plains' own 0.8): nothing writes, rain or not.
        FrozenSurface warm = new FrozenSurface();
        warm.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        warm.warm = true;
        PrecipitationSystem system = new PrecipitationSystem(warm, new AlwaysHits());
        system.tryIceAndSnowAtChunk(0, 0, true);
        assertEquals(0, warm.iceWrites.size(), "the warm column never freezes");
        assertEquals(0, warm.snowWrites.size(), "the warm column never snows");

        // The lit cell: light 12 refuses the freeze (both arms' < 10).
        FrozenSurface lit = new FrozenSurface();
        lit.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        lit.litCell = FrozenSurface.pack(PICK_X, SURFACE_Y - 1, PICK_Z);
        PrecipitationSystem litSystem = new PrecipitationSystem(lit, new AlwaysHits());
        litSystem.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(0, lit.iceWrites.size(), "the lit water never freezes");

        // The dry storm: no rain — the freeze lands, the snow never does.
        FrozenSurface dry = new FrozenSurface();
        dry.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        PrecipitationSystem drySystem = new PrecipitationSystem(dry, new AlwaysHits());
        drySystem.tryIceAndSnowAtChunk(0, 0, false);
        assertEquals(1, dry.iceWrites.size(), "the freeze runs dry");
        assertEquals(0, dry.snowWrites.size(), "the snow needs the rain");

        // The rain: the snow lands on the surface cell over the solid
        // support (the air + canBePlaced gates).
        FrozenSurface storm = new FrozenSurface();
        storm.water(PICK_X, SURFACE_Y - 1, PICK_Z);
        PrecipitationSystem stormSystem = new PrecipitationSystem(storm, new AlwaysHits());
        stormSystem.tryIceAndSnowAtChunk(0, 0, true);
        assertEquals(1, storm.iceWrites.size(), "the freeze still lands");
        assertEquals(1, storm.snowWrites.size(), "the rain brought the snow");
        assertEquals(SURFACE_Y, storm.snowWrites.get(0)[1],
                "the snow lands on the surface cell");

        // No support (canBePlaced's refusal): the snow stays away.
        FrozenSurface floating = new FrozenSurface();
        floating.supportless = true;
        PrecipitationSystem floatingSystem = new PrecipitationSystem(floating, new AlwaysHits());
        floatingSystem.tryIceAndSnowAtChunk(0, 0, true);
        assertEquals(0, floating.snowWrites.size(), "the unsupported snow never lands");
    }

    /**
     * The biome temperatures carry the reference's registration table
     * (Biome.java lines 70-172): the ocean pair's 0.5 is the engine's
     * coldest — every generated column refuses both arms, the
     * vanilla-plains envelope.
     */
    @Test
    void theBiomeTableCarriesTheReferenceTemperatures() {
        assertEquals(0.5f, net.zaminmc.torch.server.world.Biome.OCEAN.temperature());
        assertEquals(0.5f, net.zaminmc.torch.server.world.Biome.DEEP_OCEAN.temperature());
        assertEquals(0.8f, net.zaminmc.torch.server.world.Biome.PLAINS.temperature());
        assertEquals(2.0f, net.zaminmc.torch.server.world.Biome.DESERT.temperature());
        assertEquals(0.7f, net.zaminmc.torch.server.world.Biome.FOREST.temperature());
        assertEquals(0.95f, net.zaminmc.torch.server.world.Biome.JUNGLE.temperature());
        assertEquals(2.0f, net.zaminmc.torch.server.world.Biome.HELL.temperature());
        for (net.zaminmc.torch.server.world.Biome biome
                : net.zaminmc.torch.server.world.Biome.values()) {
            assertTrue(biome.temperature() > 0.15f,
                    "every generated biome refuses the frozen arms: " + biome);
        }
    }
}
