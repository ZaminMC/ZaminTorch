package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.util.Identifier;

/**
 * The structure block palette (Slice 12a): the block set the structure
 * families place, resolved once per populate pass. The cobweb registers
 * with the mineshaft slice (legacy 30); the rail's two flat orientations
 * carry the metadata pair (0 north-south, 1 east-west) exactly like the
 * reference's getStateFromMetadata.
 *
 * <p>Slice 12b grows the palette with the village set: the well's
 * cobblestone/water/gravel column, the building shells (log, planks,
 * cobblestone, glass pane, iron bars, the stairs trio, the ladder's four
 * facings, the door's four, the slabs, the crafting table, furnace,
 * bookshelf, the white wool lamp, the sandstone desert swap, the
 * farmland/water farm plots with the three crop families' stage
 * dispatch, and the flowing-water/lava pair as the engine's source
 * blocks (the well and the blacksmith's pools never drain — no update
 * arms run at populate time, exactly like the reference's flag-2
 * structure writes).</p>
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
    // The village set (Slice 12b).
    private final BlockType cobblestone;
    private final BlockType log;
    private final BlockType gravel;
    private final BlockType sandstone;
    private final BlockType smoothSandstone;
    private final BlockType woolWhite;
    private final BlockType water;
    private final BlockType lava;
    private final BlockType stoneSlab;
    private final BlockType doubleStoneSlab;
    private final BlockType glassPane;
    private final BlockType ironBars;
    private final BlockType craftingTable;
    private final BlockType furnace;
    private final BlockType bookshelf;
    private final BlockType farmland;
    private final BlockType woodenPressurePlate;
    private final BlockType[] oakStairs = new BlockType[4];
    private final BlockType[] stoneStairs = new BlockType[4];
    private final BlockType[] sandstoneStairs = new BlockType[4];
    private final BlockType[] ladders = new BlockType[6];
    private final BlockType[] doors = new BlockType[4];
    private final BlockType doorUpper;

    private StructurePalette(BlockRegistry registry) {
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.dirt = registry.require(Identifier.parse("minecraft:dirt"));
        this.planks = registry.require(Identifier.parse("minecraft:oak_planks"));
        this.fence = registry.require(Identifier.parse("minecraft:oak_fence"));
        this.cobweb = registry.require(Identifier.parse("minecraft:cobweb"));
        this.torch = registry.require(Identifier.parse("minecraft:torch"));
        this.rail = registry.require(Identifier.parse("minecraft:rail"));
        this.railEastWest = registry.require(Identifier.parse("minecraft:rail_ew"));
        this.cobblestone = registry.require(Identifier.parse("minecraft:cobblestone"));
        this.log = registry.require(Identifier.parse("minecraft:oak_log"));
        this.gravel = registry.require(Identifier.parse("minecraft:gravel"));
        this.sandstone = registry.require(Identifier.parse("minecraft:sandstone"));
        this.smoothSandstone = registry.require(Identifier.parse("minecraft:smooth_sandstone"));
        this.woolWhite = registry.require(Identifier.parse("minecraft:wool"));
        this.water = registry.require(Identifier.parse("minecraft:water"));
        this.lava = registry.require(Identifier.parse("minecraft:lava"));
        this.stoneSlab = registry.require(Identifier.parse("minecraft:stone_slab"));
        this.doubleStoneSlab = registry.require(Identifier.parse("minecraft:double_stone_slab"));
        this.glassPane = registry.require(Identifier.parse("minecraft:glass_pane"));
        this.ironBars = registry.require(Identifier.parse("minecraft:iron_bars"));
        this.craftingTable = registry.require(Identifier.parse("minecraft:crafting_table"));
        this.furnace = registry.require(Identifier.parse("minecraft:furnace"));
        this.bookshelf = registry.require(Identifier.parse("minecraft:bookshelf"));
        this.farmland = registry.require(Identifier.parse("minecraft:farmland"));
        this.woodenPressurePlate = registry.require(Identifier.parse("minecraft:wooden_pressure_plate"));
        // The stairs trio's ascending band (E/W/S/N = 0..3, the reference's
        // own getStateFromMetadata band).
        this.oakStairs[0] = BuiltinBlocks.OAK_STAIRS_EAST;
        this.oakStairs[1] = BuiltinBlocks.OAK_STAIRS_WEST;
        this.oakStairs[2] = BuiltinBlocks.OAK_STAIRS_SOUTH;
        this.oakStairs[3] = BuiltinBlocks.OAK_STAIRS_NORTH;
        this.stoneStairs[0] = BuiltinBlocks.COBBLESTONE_STAIRS_EAST;
        this.stoneStairs[1] = BuiltinBlocks.COBBLESTONE_STAIRS_WEST;
        this.stoneStairs[2] = BuiltinBlocks.COBBLESTONE_STAIRS_SOUTH;
        this.stoneStairs[3] = BuiltinBlocks.COBBLESTONE_STAIRS_NORTH;
        this.sandstoneStairs[0] = BuiltinBlocks.SANDSTONE_STAIRS_EAST;
        this.sandstoneStairs[1] = BuiltinBlocks.SANDSTONE_STAIRS_WEST;
        this.sandstoneStairs[2] = BuiltinBlocks.SANDSTONE_STAIRS_SOUTH;
        this.sandstoneStairs[3] = BuiltinBlocks.SANDSTONE_STAIRS_NORTH;
        // The ladder's four facings (the Direction id band N=2 S=3 W=4 E=5,
        // the reference's ladder metadata).
        this.ladders[2] = BuiltinBlocks.LADDER_NORTH;
        this.ladders[3] = BuiltinBlocks.LADDER_SOUTH;
        this.ladders[4] = BuiltinBlocks.LADDER_WEST;
        this.ladders[5] = BuiltinBlocks.LADDER_EAST;
        // The door's four lower facings (the engine's W/N/E/S band 0..3).
        this.doors[0] = BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W;
        this.doors[1] = BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_N;
        this.doors[2] = BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_E;
        this.doors[3] = BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_S;
        this.doorUpper = BuiltinBlocks.OAK_DOOR_UPPER;
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

    // ------------------------------------------------------------ the village set

    public BlockType cobblestone() {
        return cobblestone;
    }

    public BlockType log() {
        return log;
    }

    public BlockType gravel() {
        return gravel;
    }

    public BlockType sandstone() {
        return sandstone;
    }

    /** The desert swap's planks (SandstoneBlock.Type.SMOOTH, metadata 2). */
    public BlockType smoothSandstone() {
        return smoothSandstone;
    }

    public BlockType woolWhite() {
        return woolWhite;
    }

    /** The well's water (the reference's FLOWING_WATER — the source stand-in). */
    public BlockType water() {
        return water;
    }

    /** The blacksmith's lava (the reference's FLOWING_LAVA — the source stand-in). */
    public BlockType lava() {
        return lava;
    }

    public BlockType stoneSlab() {
        return stoneSlab;
    }

    public BlockType doubleStoneSlab() {
        return doubleStoneSlab;
    }

    public BlockType glassPane() {
        return glassPane;
    }

    public BlockType ironBars() {
        return ironBars;
    }

    public BlockType craftingTable() {
        return craftingTable;
    }

    public BlockType furnace() {
        return furnace;
    }

    public BlockType bookshelf() {
        return bookshelf;
    }

    public BlockType farmland() {
        return farmland;
    }

    public BlockType woodenPressurePlate() {
        return woodenPressurePlate;
    }

    /** The oak stairs for the ascending metadata (E/W/S/N = 0..3). */
    public BlockType oakStairs(int metadata) {
        return oakStairs[metadata & 3];
    }

    /** The cobblestone ("stone") stairs for the ascending metadata (E/W/S/N = 0..3). */
    public BlockType stoneStairs(int metadata) {
        return stoneStairs[metadata & 3];
    }

    /** The sandstone stairs for the ascending metadata (E/W/S/N = 0..3). */
    public BlockType sandstoneStairs(int metadata) {
        return sandstoneStairs[metadata & 3];
    }

    /** The ladder for the reference's facing id (N=2 S=3 W=4 E=5). */
    public BlockType ladder(int facingId) {
        return ladders[Math.abs(facingId % 6)];
    }

    /** The door's lower half for the reference's horizontal id (S=0 W=1 N=2 E=3). */
    public BlockType doorLower(int horizontalId) {
        // The reference band S=0 W=1 N=2 E=3 over the engine's W/N/E/S array.
        return switch (Math.abs(horizontalId % 4)) {
            case 0 -> doors[3]; // S
            case 1 -> doors[0]; // W
            case 2 -> doors[1]; // N
            default -> doors[2]; // E
        };
    }

    /** The door's upper half (the hinge model's plain stand-in). */
    public BlockType doorUpper() {
        return doorUpper;
    }

    /** The wheat crop for a growth stage 0..7. */
    public BlockType wheat(int stage) {
        return switch (Math.max(0, Math.min(7, stage))) {
            case 0 -> BuiltinBlocks.WHEAT_STAGE0;
            case 1 -> BuiltinBlocks.WHEAT_STAGE1;
            case 2 -> BuiltinBlocks.WHEAT_STAGE2;
            case 3 -> BuiltinBlocks.WHEAT_STAGE3;
            case 4 -> BuiltinBlocks.WHEAT_STAGE4;
            case 5 -> BuiltinBlocks.WHEAT_STAGE5;
            case 6 -> BuiltinBlocks.WHEAT_STAGE6;
            default -> BuiltinBlocks.WHEAT_STAGE7;
        };
    }

    /** The carrot crop for a growth stage 0..7. */
    public BlockType carrots(int stage) {
        return switch (Math.max(0, Math.min(7, stage))) {
            case 0 -> BuiltinBlocks.CARROTS_STAGE0;
            case 1 -> BuiltinBlocks.CARROTS_STAGE1;
            case 2 -> BuiltinBlocks.CARROTS_STAGE2;
            case 3 -> BuiltinBlocks.CARROTS_STAGE3;
            case 4 -> BuiltinBlocks.CARROTS_STAGE4;
            case 5 -> BuiltinBlocks.CARROTS_STAGE5;
            case 6 -> BuiltinBlocks.CARROTS_STAGE6;
            default -> BuiltinBlocks.CARROTS_STAGE7;
        };
    }

    /** The potato crop for a growth stage 0..7. */
    public BlockType potatoes(int stage) {
        return switch (Math.max(0, Math.min(7, stage))) {
            case 0 -> BuiltinBlocks.POTATOES_STAGE0;
            case 1 -> BuiltinBlocks.POTATOES_STAGE1;
            case 2 -> BuiltinBlocks.POTATOES_STAGE2;
            case 3 -> BuiltinBlocks.POTATOES_STAGE3;
            case 4 -> BuiltinBlocks.POTATOES_STAGE4;
            case 5 -> BuiltinBlocks.POTATOES_STAGE5;
            case 6 -> BuiltinBlocks.POTATOES_STAGE6;
            default -> BuiltinBlocks.POTATOES_STAGE7;
        };
    }
}
