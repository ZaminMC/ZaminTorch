package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.EngineChunk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * THE DETACHED INITIAL COMPUTE (the §22 gate's second half): the pure
 * off-lane run of the lighting workload over an immutable
 * {@link LightRegion} — the random-tick picks' own compute-then-apply shape.
 * The SAME static algorithms the live engine runs ({@code
 * LightEngine.computeInitial}) drive a {@code RegionAccess} state view; the
 * equivalence test proves the two runs agree write-for-write.
 *
 * <p>The working state: the fresh chunk's light starts CLEARED (absent
 * sections — block 0 / sky 15, materializing on write exactly like a real
 * chunk after {@code clearLight}); each captured neighbor's light starts
 * from the captured clones (absent sections keep the implicit defaults,
 * materializing on write exactly like the live chunk setters). Every write
 * is recorded in order; writes outside the captured 3x3 drop (the live
 * setter's own unloaded behavior).</p>
 */
public final class InitialLightCompute {

    private InitialLightCompute() {
    }

    /**
     * Runs the initial compute off-lane over the captured region.
     *
     * @param region the immutable snapshot (captured on the owning lane)
     * @return every write the live routing would have made, in order
     */
    public static InitialLightResult compute(LightRegion region) {
        RegionAccess access = new RegionAccess(region);
        LightEngine.computeInitial(access);
        return access.buildResult();
    }

    // ------------------------------------------------------------------ the region state view

    private static final class RegionAccess implements LightAccess {

        private final LightRegion region;
        private final int freshChunkX;
        private final int freshChunkZ;

        // The working light state (mutated by the compute; every mutation recorded).
        private final LightSection[] freshLight = new LightSection[EngineChunk.SECTION_COUNT];
        private final LightSection[][] neighborLight = new LightSection[9][]; // slot -> working

        // The write recorders.
        private int[] freshWrites = new int[256];
        private int freshWriteCount;
        private int[] neighborWrites = new int[64];
        private int neighborWriteCount;
        private final Set<Long> relight = new LinkedHashSet<>();

        private RegionAccess(LightRegion region) {
            this.region = region;
            this.freshChunkX = region.freshPosition().x();
            this.freshChunkZ = region.freshPosition().z();
            for (int slot = 0; slot < 9; slot++) {
                LightRegion.CapturedChunk captured = region.neighbor(slot);
                if (captured == null) {
                    continue;
                }
                neighborLight[slot] = cloneCapturedLight(captured);
            }
        }

        /** The working copy of a captured neighbor's light (clones in; implicit sections absent). */
        private static LightSection[] cloneCapturedLight(LightRegion.CapturedChunk captured) {
            LightSection[] working = new LightSection[EngineChunk.SECTION_COUNT];
            for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
                if (captured.blockLight[sectionY] == null) {
                    continue; // implicit default (block 0 / sky 15)
                }
                LightSection section = new LightSection();
                System.arraycopy(captured.blockLight[sectionY], 0, section.blockLightArray(), 0,
                        LightSection.ARRAY_BYTES);
                System.arraycopy(captured.skyLight[sectionY], 0, section.skyLightArray(), 0,
                        LightSection.ARRAY_BYTES);
                working[sectionY] = section;
            }
            return working;
        }

        // -------------------------------------------------- the routing

        private boolean inFresh(int x, int z) {
            return (x >> 4) == freshChunkX && (z >> 4) == freshChunkZ;
        }

        private int slotOf(int x, int z) {
            int dx = (x >> 4) - freshChunkX;
            int dz = (z >> 4) - freshChunkZ;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
                return -1; // outside the 3x3: unloaded space
            }
            return LightRegion.slot(dx, dz);
        }

        // -------------------------------------------------- the ops

        @Override
        public int filterAt(int x, int y, int z) {
            return region.materialAt(x, y, z) & 0xF;
        }

        @Override
        public int emissionAt(int x, int y, int z) {
            return (region.materialAt(x, y, z) >> 4) & 0xF;
        }

        @Override
        public int blockLightAt(int x, int y, int z) {
            if (inFresh(x, z)) {
                return read(freshLight, x, y, z, false);
            }
            int slot = slotOf(x, z);
            if (slot >= 0 && neighborLight[slot] != null) {
                return read(neighborLight[slot], x, y, z, false);
            }
            return LightSection.DEFAULT_BLOCK; // unloaded: the live peek-null answer
        }

        @Override
        public int skyLightAt(int x, int y, int z) {
            if (inFresh(x, z)) {
                return read(freshLight, x, y, z, true);
            }
            int slot = slotOf(x, z);
            if (slot >= 0 && neighborLight[slot] != null) {
                return read(neighborLight[slot], x, y, z, true);
            }
            return LightSection.DEFAULT_SKY; // unloaded: the live peek-null answer
        }

        @Override
        public boolean setBlockLight(int x, int y, int z, int level) {
            if (inFresh(x, z)) {
                recordFreshWrite(x, y, z, InitialLightResult.CHANNEL_BLOCK, level);
                write(freshLight, x, y, z, level, false);
                return true;
            }
            int slot = slotOf(x, z);
            if (slot >= 0 && neighborLight[slot] != null) {
                recordNeighborWrite(slot, x, y, z, InitialLightResult.CHANNEL_BLOCK, level);
                write(neighborLight[slot], x, y, z, level, false);
                return true;
            }
            return false; // unloaded — dropped (the live setter's own behavior)
        }

        @Override
        public boolean setSkyLight(int x, int y, int z, int level) {
            if (inFresh(x, z)) {
                recordFreshWrite(x, y, z, InitialLightResult.CHANNEL_SKY, level);
                write(freshLight, x, y, z, level, true);
                return true;
            }
            int slot = slotOf(x, z);
            if (slot >= 0 && neighborLight[slot] != null) {
                recordNeighborWrite(slot, x, y, z, InitialLightResult.CHANNEL_SKY, level);
                write(neighborLight[slot], x, y, z, level, true);
                return true;
            }
            return false; // unloaded — dropped (the live setter's own behavior)
        }

        @Override
        public void markRelight(int x, int z) {
            if (!inFresh(x, z)) {
                relight.add(ChunkPosition.ofBlock(x, z).packed());
            }
        }

        @Override
        public void clearFreshLight() {
            Arrays.fill(freshLight, null);
        }

        @Override
        public int highestRelevantY() {
            return region.maxY();
        }

        @Override
        public int freshBaseX() {
            return region.freshPosition().minBlockX();
        }

        @Override
        public int freshBaseZ() {
            return region.freshPosition().minBlockZ();
        }

        @Override
        public ChunkPosition neighborAt(int dx, int dz) {
            int slot = LightRegion.slot(dx, dz);
            LightRegion.CapturedChunk captured = region.neighbor(slot);
            return captured != null ? captured.position : null;
        }

        // -------------------------------------------------- storage helpers

        private static int read(LightSection[] sections, int x, int y, int z, boolean sky) {
            LightSection section = sections[y >> 4];
            if (section == null) {
                return sky ? LightSection.DEFAULT_SKY : LightSection.DEFAULT_BLOCK;
            }
            return sky ? section.skyLight(x & 0xF, y & 0xF, z & 0xF)
                    : section.blockLight(x & 0xF, y & 0xF, z & 0xF);
        }

        private static void write(LightSection[] sections, int x, int y, int z, int level, boolean sky) {
            int sectionY = y >> 4;
            LightSection section = sections[sectionY];
            if (section == null) {
                section = new LightSection(); // materializes with the implicit defaults
                sections[sectionY] = section;
            }
            if (sky) {
                section.setSkyLight(x & 0xF, y & 0xF, z & 0xF, level);
            } else {
                section.setBlockLight(x & 0xF, y & 0xF, z & 0xF, level);
            }
        }

        // -------------------------------------------------- the recorders

        private void recordFreshWrite(int x, int y, int z, int channel, int level) {
            int localIndex = (y << 8) | ((z & 0xF) << 4) | (x & 0xF);
            if (freshWriteCount == freshWrites.length) {
                freshWrites = Arrays.copyOf(freshWrites, freshWrites.length * 2);
            }
            freshWrites[freshWriteCount++] = (localIndex << 5) | (channel << 4) | level;
        }

        private void recordNeighborWrite(int slot, int x, int y, int z, int channel, int level) {
            int localIndex = (y << 8) | ((z & 0xF) << 4) | (x & 0xF);
            if (neighborWriteCount == neighborWrites.length) {
                neighborWrites = Arrays.copyOf(neighborWrites, neighborWrites.length * 2);
            }
            // The slot field spans FOUR bits (0..8 -- nine 3x3 slots around the
            // fresh chunk): a 3-bit mask would fold slot 8 (the (+1,+1)
            // diagonal) onto slot 0 (the (-1,-1) diagonal) and misapply its
            // writes to the opposite corner chunk (found by the gate's own
            // equivalence test).
            neighborWrites[neighborWriteCount++] = (slot << 21) | (localIndex << 5) | (channel << 4) | level;
            relight.add(ChunkPosition.ofBlock(x, z).packed());
        }

        private InitialLightResult buildResult() {
            ChunkPosition[] positions = new ChunkPosition[9];
            for (int slot = 0; slot < 9; slot++) {
                LightRegion.CapturedChunk captured = region.neighbor(slot);
                if (captured != null) {
                    positions[slot] = captured.position;
                }
            }
            List<ChunkPosition> columns = new ArrayList<>(relight.size());
            for (Long packed : relight) {
                columns.add(ChunkPosition.unpack(packed));
            }
            return new InitialLightResult(freshWrites, freshWriteCount,
                    neighborWrites, neighborWriteCount, positions, columns);
        }
    }
}
