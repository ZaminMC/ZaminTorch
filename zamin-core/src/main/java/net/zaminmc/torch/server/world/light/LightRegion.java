package net.zaminmc.torch.server.world.light;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockLightTable;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;

/**
 * THE LIGHTING WORKLOAD'S IMMUTABLE-INPUT CONTRACT (the §22 gate's first
 * part, Phase 5's §6.5 discipline): the initial per-chunk compute's whole
 * read set, captured on the owning lane as one immutable snapshot — the
 * fresh chunk's own material grid plus, for every already-loaded chunk in
 * the fresh chunk's 3x3 neighborhood, that chunk's material grid and its
 * current light. The detached off-lane compute reads ONLY this (§6.4: no
 * live reference crosses the owner boundary); no world reference rides
 * with it.
 *
 * <p>WHY THE WHOLE 3x3 (the bound derivation): every consult the algorithm
 * can make outside the fresh chunk sits within 14 BFS steps of a seed, and
 * every seed sits on the fresh chunk's edge or on a loaded neighbor's
 * border strip facing it. A seed's BFS pushes cells only while their level
 * exceeds 1 — at most 13 pushes deep — consulting one further ring; light
 * never reaches 15 steps, and a neighbor chunk is 16 wide, so the walk
 * never crosses a captured neighbor into the ring beyond. A 16-deep band
 * per side would therefore cover every consult — and 16 of 16 columns is
 * the whole neighbor chunk, so the capture simply takes each loaded
 * neighbor in full. The diagonal chunks are covered for the same reason:
 * the fresh chunk's corner cells consult the diagonal's corner column, and
 * a border-strip BFS can step around the corner into it (the same 14-step
 * cap keeps the walk inside it). The equivalence test proves the bound
 * empirically over the shadow and emitter stress scenes.</p>
 *
 * <p>STORAGE (the pathfinding gate's own lesson — the eager per-cell walk
 * priced 24-73x the work it fed): per SECTION, not per cell in the wide.
 * A present block section maps ONCE per cell into one packed byte
 * (filter | emission << 4); an absent block section is all-air and stores
 * nothing. A materialized light section clones its two wire-packed nibble
 * arrays (block, sky — the same 2048-byte layout {@link LightSection} and
 * the protocol serializer use); an absent light section is the implicit
 * default (block 0 / sky 15) and stores nothing. On the flat surface world
 * a captured neighbor costs one 4096-cell material walk plus two 2KB array
 * copies — the sections above the terrain are all-absent and free.</p>
 */
public final class LightRegion {

    private static final int SECTION_VOLUME = 16 * 16 * 16;

    /** The 3x3 slot index of a chunk offset: slot 4 is the fresh chunk itself. */
    static int slot(int dx, int dz) {
        return (dx + 1) * 3 + (dz + 1);
    }

    private final ChunkPosition freshPosition;
    private final int maxY;
    private final byte[][] freshMaterials;  // per section: packed (filter | emission<<4) or null = all-air
    private final CapturedChunk[] neighbors = new CapturedChunk[9]; // slot -> capture; [4] unused

    private LightRegion(ChunkPosition freshPosition, int maxY, byte[][] freshMaterials) {
        this.freshPosition = freshPosition;
        this.maxY = maxY;
        this.freshMaterials = freshMaterials;
    }

    /**
     * Captures the initial compute's whole read set on the caller's (owning)
     * lane. The fresh chunk is the still-unpublished, fully-sculpted chunk
     * about to be lit; the neighbors are every already-loaded chunk in its
     * 3x3 (peek, never generate — the re-entrancy guard).
     *
     * @param world the live world — read ONLY here, on the owning lane
     * @param fresh the detached fresh chunk (generated, sculpted, unlit)
     * @return the immutable snapshot the off-lane compute reads
     */
    public static LightRegion capture(EngineWorld world, EngineChunk fresh) {
        ChunkPosition at = fresh.position();
        LightRegion region = new LightRegion(at, highestLightRelevantY(fresh), captureMaterials(fresh));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                EngineChunk neighbor = world.peek(new ChunkPosition(at.x() + dx, at.z() + dz));
                if (neighbor == null) {
                    continue;
                }
                region.neighbors[slot(dx, dz)] = new CapturedChunk(
                        neighbor.position(), captureMaterials(neighbor),
                        captureLight(neighbor, true), captureLight(neighbor, false));
            }
        }
        return region;
    }

    /** The fresh chunk's position. */
    public ChunkPosition freshPosition() {
        return freshPosition;
    }

    /** The fresh chunk's highest light-relevant Y (the compute's working span). */
    public int maxY() {
        return maxY;
    }

    /** The captured neighbor at the 3x3 slot, or null when none was loaded. */
    public CapturedChunk neighbor(int slot) {
        return neighbors[slot];
    }

    /** @return how many of the 3x3 neighbors were loaded and captured. */
    public int capturedNeighborCount() {
        int count = 0;
        for (CapturedChunk neighbor : neighbors) {
            if (neighbor != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * The packed material byte (filter | emission << 4) of any cell in the
     * 3x3, by world coordinates; 0 outside the captured set (the unloaded
     * answer — the live routing's own air default).
     */
    int materialAt(int x, int y, int z) {
        int dx = (x >> 4) - freshPosition.x();
        int dz = (z >> 4) - freshPosition.z();
        byte[] section;
        if (dx == 0 && dz == 0) {
            section = freshMaterials[y >> 4];
        } else if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
            CapturedChunk neighbor = neighbors[slot(dx, dz)];
            if (neighbor == null) {
                return 0; // unloaded: air (filter 0, emission 0)
            }
            section = neighbor.materials[y >> 4];
        } else {
            return 0; // outside the 3x3: never consulted (the bound derivation)
        }
        if (section == null) {
            return 0; // absent section: all air
        }
        return section[((y & 0xF) << 8) | ((z & 0xF) << 4) | (x & 0xF)] & 0xFF;
    }

    // ------------------------------------------------------------------ capture internals

    /** One captured neighbor: its position, material grid, and current light. */
    static final class CapturedChunk {
        final ChunkPosition position;
        final byte[][] materials;   // per section: packed or null = all-air
        final byte[][] blockLight;  // per section: wire-packed clone or null = implicit 0
        final byte[][] skyLight;    // per section: wire-packed clone or null = implicit 15

        CapturedChunk(ChunkPosition position, byte[][] materials,
                      byte[][] blockLight, byte[][] skyLight) {
            this.position = position;
            this.materials = materials;
            this.blockLight = blockLight;
            this.skyLight = skyLight;
        }
    }

    /** Maps a chunk's present block sections into packed material bytes (absent = null). */
    private static byte[][] captureMaterials(EngineChunk chunk) {
        byte[][] materials = new byte[EngineChunk.SECTION_COUNT][];
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            var section = chunk.section(sectionY);
            if (section == null || section.isEmpty()) {
                continue; // absent or all-air: the implicit all-air answer
            }
            byte[] packed = new byte[SECTION_VOLUME];
            int index = 0;
            for (int localY = 0; localY < 16; localY++) {
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        var type = chunk.getBlock(lx, (sectionY << 4) | localY, lz);
                        packed[index++] = (byte) (BlockLightTable.filter(type)
                                | (BlockLightTable.emission(type) << 4));
                    }
                }
            }
            materials[sectionY] = packed;
        }
        return materials;
    }

    /** Clones a chunk's materialized light sections (absent = null = implicit defaults). */
    private static byte[][] captureLight(EngineChunk chunk, boolean blockChannel) {
        byte[][] light = new byte[EngineChunk.SECTION_COUNT][];
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            LightSection section = chunk.lightSection(sectionY);
            if (section == null) {
                continue; // implicit default: block 0 / sky 15
            }
            byte[] clone = new byte[LightSection.ARRAY_BYTES];
            System.arraycopy(blockChannel ? section.blockLightArray() : section.skyLightArray(),
                    0, clone, 0, LightSection.ARRAY_BYTES);
            light[sectionY] = clone;
        }
        return light;
    }

    private static int highestLightRelevantY(EngineChunk chunk) {
        int highest = 0;
        for (int sectionY = 0; sectionY < EngineChunk.SECTION_COUNT; sectionY++) {
            var section = chunk.section(sectionY);
            if ((section != null && !section.isEmpty()) || chunk.sectionNeedsLightWire(sectionY)) {
                highest = Math.max(highest, (sectionY << 4) + 15);
            }
        }
        return Math.min(highest + 1, BlockPosition.MAX_Y);
    }
}
