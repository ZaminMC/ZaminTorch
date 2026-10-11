package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

/**
 * The structure block palette (Slice 12a): the block set the mineshaft
 * family places, resolved once per populate pass. The cobweb registers
 * with this slice (legacy 30); the rail's two flat orientations carry
 * the metadata pair (0 north-south, 1 east-west) exactly like the
 * reference's getStateFromMetadata.
 */
public final class StructurePalette {

    private final BlockType air;
    private final BlockType dirt;
    private final BlockType planks;
    private final BlockType fence;
    private final BlockType cobweb;
    private final BlockType torch;
    private final BlockType rail;
    private final BlockType railEastWest;

    private StructurePalette(BlockRegistry registry) {
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.planks = registry.require(Identifier.parse("minecraft:oak_planks"));
        this.fence = registry.require(Identifier.parse("minecraft:oak_fence"));
        this.cobweb = registry.require(Identifier.parse("minecraft:cobweb"));
        this.torch = registry.require(Identifier.parse("minecraft:torch"));
        this.rail = registry.require(Identifier.parse("minecraft:rail"));
        this.railEastWest = registry.require(Identifier.parse("minecraft:rail_ew"));
    }

    /** Resolves the palette (the registry is immutable after boot). */
    public static StructurePalette of(BlockRegistry registry) {
        return new StructurePalette(registry);
    }

    public BlockType air() {
        return air;
    }

    public BlockType dirt() {
        return dirt;
    }

    public BlockType planks() {
        return planks;
    }

    public BlockType fence() {
        return fence;
    }

    public BlockType cobweb() {
        return cobweb;
    }

    public BlockType torch() {
        return torch;
    }

    /** The rail for a metadata value (0 north-south, 1 east-west). */
    public BlockType rail(int metadata) {
        return metadata == 1 ? railEastWest : rail;
    }
}
