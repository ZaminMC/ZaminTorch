package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.light.LightSection;
import net.zaminmc.torch.server.world.structure.StructureWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A 16x256x16 chunk column: identity + block state + light (§475).
 *
 * <p>Lifecycle (Slice #1): chunks are generated fully and then published, so a
 * visible chunk is always complete ({@code loaded} only). Unloading and persistence
 * states arrive with the world-storage slice.</p>
 *
 * <p>Light is derived state (recomputed from blocks, never persisted): each
 * section has a {@link LightSection} that materializes lazily on the first
 * non-default value. An absent section carries the implicit defaults — block
 * light 0, skylight 15 — which is also exactly what the 1.8 client assumes
 * for sections a chunk packet's bitmask omits, so the wire and the model
 * agree by construction.</p>
 */
public final class EngineChunk {

    public static final int SECTION_COUNT = 16;

    private final ChunkPosition position;
    private final ChunkSection[] sections;
    private final LightSection[] light = new LightSection[SECTION_COUNT];
    private final BlockType air;
    /** The 16x16 biome row (generation-derived state); plains until written. */
    private final byte[] biomes = new byte[16 * 16];

    /**
     * The populate-time generation attachments (Slice 12b — the village's
     * spawn/chest records): written by the generator thread while the chunk
     * is detached (it owns the chunk exclusively during generate), published
     * with the chunk itself, and drained exactly once by the engine's tick
     * thread after publication. The generator never touches the lists after
     * generate returns — the publish is the safe-publication barrier, so the
     * tick thread's drain needs no lock.
     */
    private final List<PendingVillager> pendingVillagers = new ArrayList<>();
    private final List<PendingGenChest> pendingGenChests = new ArrayList<>();

    /** One populate-time villager (the reference's structure addEntity). */
    public record PendingVillager(int x, int y, int z, int profession) {
    }

    /** One populate-time loot chest (the reference's placeChestWithLoot). */
    public record PendingGenChest(int x, int y, int z, List<StructureWorld.GenLootSlot> slots) {
    }

    public EngineChunk(ChunkPosition position, BlockType air) {
        this.position = Objects.requireNonNull(position, "position");
        this.air = Objects.requireNonNull(air, "air");
        this.sections = new ChunkSection[SECTION_COUNT];
        // The implicit biome default is plains (the 1.8 wire value 1) —
        // exactly what a chunk whose generator never wrote would ride.
        java.util.Arrays.fill(biomes, (byte) 1);
        // Empty sections exist implicitly as null; they materialize on first write.
    }

    public ChunkPosition position() {
        return position;
    }

    public BlockType getBlock(int localX, int y, int localZ) {
        int sectionY = y >> 4;
        ChunkSection section = sections[sectionY];
        return section == null ? air : section.get(localX, y & 0xF, localZ);
    }

    /** @return true if the stored type changed. */
    public boolean setBlock(int localX, int y, int localZ, BlockType type) {
        int sectionY = y >> 4;
        ChunkSection section = sections[sectionY];
        if (section == null) {
            if (type.equals(air)) {
                return false; // writing air into an implicitly-empty section changes nothing
            }
            section = new ChunkSection(air);
            sections[sectionY] = section;
        }
        return section.set(localX, y & 0xF, localZ, type);
    }

    /** @return the section for the given y range (0..15), or null when implicitly empty. */
    public ChunkSection section(int sectionY) {
        return sections[sectionY];
    }

    /** @return whether any section of this chunk contains non-air blocks. */
    public boolean isEmpty() {
        for (ChunkSection section : sections) {
            if (section != null && !section.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------- populate-time attachments (Slice 12b)

    /** Records one villager spawn (generator thread, during generate). */
    public void addPendingVillager(int x, int y, int z, int profession) {
        pendingVillagers.add(new PendingVillager(x, y, z, profession));
    }

    /** Records one loot chest (generator thread, during generate). */
    public void addPendingGenChest(int x, int y, int z, List<StructureWorld.GenLootSlot> slots) {
        pendingGenChests.add(new PendingGenChest(x, y, z, List.copyOf(slots)));
    }

    /** @return the pending villager records (empty when none). */
    public List<PendingVillager> pendingVillagers() {
        return pendingVillagers;
    }

    /** @return the pending chest records (empty when none). */
    public List<PendingGenChest> pendingGenChests() {
        return pendingGenChests;
    }

    // ------------------------------------------------------------------ biomes (§biomes)

    /**
     * Writes one column's biome (generation-time only; x = local 0-15, z =
     * local 0-15). The value is the 1.8 wire id.
     */
    public void setBiome(int localX, int localZ, byte legacyId) {
        biomes[(localZ << 4) | localX] = legacyId;
    }

    /** @return the column's biome wire id (plains when generation never wrote). */
    public byte biome(int localX, int localZ) {
        return biomes[(localZ << 4) | localX];
    }

    /** @return the full 16x16 biome row (x fastest, the wire's exact layout). */
    public byte[] biomeArray() {
        return biomes.clone();
    }

    // ------------------------------------------------------------------ light (§475)

    private LightSection lightSection(int sectionY, boolean materialize) {
        if (sectionY < 0 || sectionY >= SECTION_COUNT) {
            throw new IllegalArgumentException("Section y out of range: " + sectionY);
        }
        LightSection section = light[sectionY];
        if (section == null && materialize) {
            section = new LightSection();
            light[sectionY] = section;
        }
        return section;
    }

    public int blockLight(int localX, int y, int localZ) {
        LightSection section = light[y >> 4];
        return section == null ? LightSection.DEFAULT_BLOCK
                : section.blockLight(localX, y & 0xF, localZ);
    }

    public int skyLight(int localX, int y, int localZ) {
        LightSection section = light[y >> 4];
        return section == null ? LightSection.DEFAULT_SKY
                : section.skyLight(localX, y & 0xF, localZ);
    }

    public void setBlockLight(int localX, int y, int localZ, int level) {
        lightSection(y >> 4, true).setBlockLight(localX, y & 0xF, localZ, level);
    }

    public void setSkyLight(int localX, int y, int localZ, int level) {
        lightSection(y >> 4, true).setSkyLight(localX, y & 0xF, localZ, level);
    }

    /**
     * Clears this chunk's light storage (the initial recompute starts from the
     * implicit defaults). Owner thread only, like every light write.
     */
    public void clearLight() {
        java.util.Arrays.fill(light, null);
    }

    /** @return the light section for the given y range (0..15), or null when implicitly default. */
    public LightSection lightSection(int sectionY) {
        return light[sectionY];
    }

    /**
     * @return the raw block-light nibble array for a masked section (all
     *         default values when the section never materialized).
     */
    public byte[] blockLightArray(int sectionY) {
        LightSection section = light[sectionY];
        return section == null ? DEFAULT_BLOCK_ARRAY : section.blockLightArray();
    }

    /** Same as {@link #blockLightArray(int)} for skylight. */
    public byte[] skyLightArray(int sectionY) {
        LightSection section = light[sectionY];
        return section == null ? DEFAULT_SKY_ARRAY : section.skyLightArray();
    }

    private static final byte[] DEFAULT_BLOCK_ARRAY = new byte[LightSection.ARRAY_BYTES];
    private static final byte[] DEFAULT_SKY_ARRAY = filledSky();

    private static byte[] filledSky() {
        byte[] sky = new byte[LightSection.ARRAY_BYTES];
        java.util.Arrays.fill(sky, (byte) 0xFF);
        return sky;
    }

    /**
     * @return whether the section's light diverges from the implicit defaults
     *         (block 0 / sky 15) — a section like that must ride the wire even
     *         when it holds no blocks, or the client's assumed defaults would
     *         disagree with the server's state.
     */
    public boolean sectionNeedsLightWire(int sectionY) {
        LightSection section = light[sectionY];
        return section != null && section.hasNonDefaultLight();
    }
}
