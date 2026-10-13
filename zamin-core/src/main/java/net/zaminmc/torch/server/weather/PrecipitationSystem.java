package net.zaminmc.torch.server.weather;

import net.zaminmc.torch.block.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * The ice-and-snow precipitation arm ({@code ServerWorld.tickChunks} lines
 * 325-341, Slice 21c): the 1-in-16 roll per ticking chunk, the
 * {@code randomTickLCG}-derived in-chunk pick, the precipitation height,
 * then the reference's own gate chain —
 *
 * <ul>
 *   <li>the freeze ({@code canFreezeNaturally} -> {@code canFreeze(pos, true)},
 *       World lines 1927-1954): NO rain gate — ice forms in the cold on dry
 *       nights; temperature ≤ 0.15, block light &lt; 10, the WATER SOURCE
 *       (level 0), and NOT water on all four horizontal sides;</li>
 *   <li>the snow ({@code isRaining() && canSnowFall(pos, true)}, lines
 *       1960-1978): the rain gate rides this arm alone; temperature ≤ 0.15,
 *       block light &lt; 10, air, and {@code SNOW_LAYER.canBePlaced} (the
 *       below block solid — the reference's own ice/packed-ice/leaves
 *       exclusions keep their envelope note).</li>
 * </ul>
 *
 * <p>The third arm ({@code randomPrecipitationTick} — the cauldron's rain
 * fill) ledgers with the cauldron family: the engine registers none. The
 * engine's biome set answers warm everywhere (the ocean pair's 0.5 is the
 * coldest — Biome's own registration table), so both arms stay no-ops on
 * generated terrain exactly like vanilla plains; the frozen families the
 * generator adds later arm the same walk without a change here.</p>
 *
 * <p>Stream envelope (the 20c shape): the system owns its Random + LCG —
 * the reference shares one stream with the lightning and random-tick arms,
 * the engine's split systems each advance their own (the divergence the
 * lightning slice documented). The roll order inside one chunk's walk
 * matches the reference: the strike arm runs first in the engine's per-chunk
 * loop, this arm second.</p>
 */
public final class PrecipitationSystem {

    /** The engine queries the freeze/snow walks need. */
    public interface Surface {

        /** The precipitation height ({@code getPrecipitationHeight}). */
        int precipitationHeight(int x, int z);

        /** The block at the cell (the freeze's water-source gate). */
        BlockType blockAt(int x, int y, int z);

        /** The block light at the cell (both arms' &lt; 10 gate). */
        int blockLightAt(int x, int y, int z);

        /** Whether the block is the still water source (the level-0 gate). */
        boolean isWaterSource(BlockType type);

        /** Whether the cell holds air (the snow's first gate). */
        boolean isAirAt(int x, int y, int z);

        /**
         * Whether the snow layer can rest on the cell below ({@code
         * SNOW_LAYER.canBePlaced} — the solid-support stand-in).
         */
        boolean isSolidAt(int x, int y, int z);

        /**
         * The column's biome temperature ({@code biome.getTemperature(pos)} —
         * the registration value; the y>64 noise shave ledgers with the
         * noise slice).
         */
        float temperatureAt(int x, int z);

        /** Writes the ice (the freeze's commit). */
        void placeIceAt(int x, int y, int z);

        /** Writes the snow layer (the snow's commit). */
        void placeSnowAt(int x, int y, int z);
    }

    private final Surface surface;
    private final Random random;
    private final List<int[]> freezes = new ArrayList<>();
    private final List<int[]> snows = new ArrayList<>();
    /** The reference's shared pick LCG ({@code randomTickLCG}). */
    private int pickLcg;

    public PrecipitationSystem(Surface surface, Random random) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.random = Objects.requireNonNull(random, "random");
    }

    /** @return the freeze commits so far (diagnostics + tests). */
    public List<int[]> freezes() {
        return List.copyOf(freezes);
    }

    /** @return the snow commits so far (diagnostics + tests). */
    public List<int[]> snows() {
        return List.copyOf(snows);
    }

    /**
     * The per-chunk arm ({@code ServerWorld.tickChunks} lines 325-341): the
     * 1-in-16 roll runs BEFORE the rain gates (the shared stream advances
     * every tick whatever the sky holds — the reference's own order), then
     * the LCG-derived pick, the precipitation height, the freeze at the
     * cell below (no rain gate), the snow at the surface cell (rain gate).
     * Tick-thread context.
     */
    public void tryIceAndSnowAtChunk(int chunkX, int chunkZ, boolean raining) {
        // Line 326: the roll first.
        if (random.nextInt(16) != 0) {
            return;
        }
        // Lines 327-329: the LCG's walk, the in-chunk pick.
        pickLcg = pickLcg * 3 + 1013904223;
        int pick = pickLcg >> 2;
        int x = chunkX * 16 + (pick & 15);
        int z = chunkZ * 16 + (pick >> 8 & 15);
        // Line 329: the precipitation height — the cell the snow lands on.
        int surfaceY = surface.precipitationHeight(x, z);
        // The freeze: the cell BELOW the surface (the water under the open
        // sky).
        int freezeY = surfaceY - 1;
        if (canFreezeNaturally(x, freezeY, z)) {
            surface.placeIceAt(x, freezeY, z);
            freezes.add(new int[]{x, freezeY, z});
        }
        // The snow: the surface cell itself, only while raining.
        if (raining && canSnowFall(x, surfaceY, z)) {
            surface.placeSnowAt(x, surfaceY, z);
            snows.add(new int[]{x, surfaceY, z});
        }
        // The randomPrecipitationTick arm (the cauldron's rain fill)
        // ledgers with the cauldron family — the engine registers none.
    }

    /**
     * {@code canFreezeNaturally} -> {@code canFreeze(pos, true)} (World
     * lines 1927-1954): the temperature gate, the y window + the block
     * light, the water source (level 0), and the natural arm's own
     * surrounded-by-water refusal — a cell with water on ALL four
     * horizontal sides never freezes (the shore rule).
     */
    private boolean canFreezeNaturally(int x, int y, int z) {
        if (surface.temperatureAt(x, z) > 0.15f) {
            return false;
        }
        if (y >= 0 && y < 256 && surface.blockLightAt(x, y, z) < 10) {
            BlockType block = surface.blockAt(x, y, z);
            if (surface.isWaterSource(block)) {
                // The natural arm's fourth gate (lines 1946-1949): water on
                // every side refuses — only the shore's edge freezes.
                boolean surrounded = surface.isWaterSource(surface.blockAt(x - 1, y, z))
                        && surface.isWaterSource(surface.blockAt(x + 1, y, z))
                        && surface.isWaterSource(surface.blockAt(x, y, z - 1))
                        && surface.isWaterSource(surface.blockAt(x, y, z + 1));
                return !surrounded;
            }
        }
        return false;
    }

    /**
     * {@code canSnowFall(pos, true)} (World lines 1960-1978): the
     * temperature gate, then (checkLight) the y window + the block light +
     * air + the snow layer's own support gate.
     */
    private boolean canSnowFall(int x, int y, int z) {
        if (surface.temperatureAt(x, z) > 0.15f) {
            return false;
        }
        if (y >= 0 && y < 256 && surface.blockLightAt(x, y, z) < 10
                && surface.isAirAt(x, y, z)
                && surface.isSolidAt(x, y - 1, z)) {
            return true;
        }
        return false;
    }
}
