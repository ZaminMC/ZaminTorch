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
    // The stronghold set (Slice 12c).
    private final BlockType stoneBricks;
    private final BlockType mossyStoneBricks;
    private final BlockType crackedStoneBricks;
    private final BlockType infestedStoneBricks;
    private final BlockType stoneBrickSlab;
    private final BlockType mobSpawner;
    private final BlockType[] ironDoors = new BlockType[4];
    private final BlockType ironDoorUpper;
    private final BlockType[] endPortalFrames = new BlockType[8];
    // The fortress set (Slice 12d).
    private final BlockType netherBricks;
    private final BlockType netherBrickFence;
    private final BlockType[] netherBrickStairs = new BlockType[4];
    private final BlockType[] netherWart = new BlockType[4];
    private final BlockType soulSand;
    // The monument set (Slice 12e).
    private final BlockType prismarineRough;
    private final BlockType prismarineBricks;
    private final BlockType prismarineDark;
    private final BlockType seaLantern;
    private final BlockType sponge;
    private final BlockType wetSponge;
    private final BlockType goldBlock;

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
        // The stronghold set (Slice 12c): the stone-brick picker's family,
        // the spawner, the iron door's four lower facings (the engine's
        // W/N/E/S array band) and the end portal frame's eight states
        // (the reference's facing-id band | eye bit, indexed by the wire
        // metadata 0..7).
        this.stoneBricks = BuiltinBlocks.STONE_BRICKS;
        this.mossyStoneBricks = BuiltinBlocks.MOSSY_STONE_BRICKS;
        this.crackedStoneBricks = BuiltinBlocks.CRACKED_STONE_BRICKS;
        this.infestedStoneBricks = BuiltinBlocks.INFESTED_STONE_BRICKS;
        this.stoneBrickSlab = BuiltinBlocks.STONE_BRICK_SLAB;
        this.mobSpawner = BuiltinBlocks.MOB_SPAWNER;
        this.ironDoors[0] = BuiltinBlocks.IRON_DOOR_LOWER_W;
        this.ironDoors[1] = BuiltinBlocks.IRON_DOOR_LOWER_N;
        this.ironDoors[2] = BuiltinBlocks.IRON_DOOR_LOWER_E;
        this.ironDoors[3] = BuiltinBlocks.IRON_DOOR_LOWER_S;
        this.ironDoorUpper = BuiltinBlocks.IRON_DOOR_UPPER;
        this.endPortalFrames[0] = BuiltinBlocks.END_PORTAL_FRAME_S;
        this.endPortalFrames[1] = BuiltinBlocks.END_PORTAL_FRAME_W;
        this.endPortalFrames[2] = BuiltinBlocks.END_PORTAL_FRAME_N;
        this.endPortalFrames[3] = BuiltinBlocks.END_PORTAL_FRAME_E;
        this.endPortalFrames[4] = BuiltinBlocks.END_PORTAL_FRAME_EYE_S;
        this.endPortalFrames[5] = BuiltinBlocks.END_PORTAL_FRAME_EYE_W;
        this.endPortalFrames[6] = BuiltinBlocks.END_PORTAL_FRAME_EYE_N;
        this.endPortalFrames[7] = BuiltinBlocks.END_PORTAL_FRAME_EYE_E;
        // The fortress set (Slice 12d): the nether-brick family with the
        // stairs' ascending band and the wart's four ages.
        this.netherBricks = BuiltinBlocks.NETHER_BRICKS;
        this.netherBrickFence = BuiltinBlocks.NETHER_BRICK_FENCE;
        this.netherBrickStairs[0] = BuiltinBlocks.NETHER_BRICK_STAIRS_EAST;
        this.netherBrickStairs[1] = BuiltinBlocks.NETHER_BRICK_STAIRS_WEST;
        this.netherBrickStairs[2] = BuiltinBlocks.NETHER_BRICK_STAIRS_SOUTH;
        this.netherBrickStairs[3] = BuiltinBlocks.NETHER_BRICK_STAIRS_NORTH;
        this.netherWart[0] = BuiltinBlocks.NETHER_WART_STAGE0;
        this.netherWart[1] = BuiltinBlocks.NETHER_WART_STAGE1;
        this.netherWart[2] = BuiltinBlocks.NETHER_WART_STAGE2;
        this.netherWart[3] = BuiltinBlocks.NETHER_WART_STAGE3;
        this.soulSand = BuiltinBlocks.SOUL_SAND;
        // The monument set (Slice 12e): the reference's own block palette
        // (BASE rough, BASE_LIGHT bricks, BASE_DARK dark, LIGHT_SOURCE the
        // sea lantern, FILLER water, the wet sponge of the top rooms, the
        // gold block of the core room).
        this.prismarineRough = BuiltinBlocks.PRISMARINE_ROUGH;
        this.prismarineBricks = BuiltinBlocks.PRISMARINE_BRICKS;
        this.prismarineDark = BuiltinBlocks.PRISMARINE_DARK;
        this.seaLantern = BuiltinBlocks.SEA_LANTERN;
        this.sponge = BuiltinBlocks.SPONGE;
        this.wetSponge = BuiltinBlocks.WET_SPONGE;
        this.goldBlock = BuiltinBlocks.GOLD_BLOCK;
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

    // ------------------------------------------------------- the stronghold set

    public BlockType stoneBricks() {
        return stoneBricks;
    }

    public BlockType mossyStoneBricks() {
        return mossyStoneBricks;
    }

    public BlockType crackedStoneBricks() {
        return crackedStoneBricks;
    }

    /** The silverfish stone-brick monster egg (metadata 4). */
    public BlockType infestedStoneBricks() {
        return infestedStoneBricks;
    }

    /** The smooth stone-brick slab (legacy 44 metadata 5). */
    public BlockType stoneBrickSlab() {
        return stoneBrickSlab;
    }

    /** The mob spawner (the block entity rides its own slice). */
    public BlockType mobSpawner() {
        return mobSpawner;
    }

    /** The iron door's lower half for the reference's horizontal id (S=0 W=1 N=2 E=3). */
    public BlockType ironDoorLower(int horizontalId) {
        // The reference band over the engine's W/N/E/S array (the oak pair's walk).
        return switch (Math.abs(horizontalId % 4)) {
            case 0 -> ironDoors[3]; // S
            case 1 -> ironDoors[0]; // W
            case 2 -> ironDoors[1]; // N
            default -> ironDoors[2]; // E
        };
    }

    /** The iron door's upper half (metadata 8). */
    public BlockType ironDoorUpper() {
        return ironDoorUpper;
    }

    /**
     * The end portal frame for the reference's wire metadata (the facing
     * id S=0 W=1 N=2 E=3 | the eye bit 4) — the eight flattened states.
     */
    public BlockType endPortalFrame(int metadata) {
        return endPortalFrames[Math.abs(metadata % 8)];
    }

    // ------------------------------------------------------- the fortress set

    public BlockType netherBricks() {
        return netherBricks;
    }

    public BlockType netherBrickFence() {
        return netherBrickFence;
    }

    /** The nether-brick stairs for the ascending metadata (E/W/S/N = 0..3). */
    public BlockType netherBrickStairs(int metadata) {
        return netherBrickStairs[metadata & 3];
    }

    /** The nether wart for an age 0..3 (the farm plants age 0). */
    public BlockType netherWart(int age) {
        return netherWart[Math.max(0, Math.min(3, age))];
    }

    /** The soul sand (the farm's planting beds). */
    public BlockType soulSand() {
        return soulSand;
    }

    // ------------------------------------------------------ the monument set

    /** The reference's BASE: the rough prismarine. */
    public BlockType prismarineRough() {
        return prismarineRough;
    }

    /** The reference's BASE_LIGHT: the prismarine bricks. */
    public BlockType prismarineBricks() {
        return prismarineBricks;
    }

    /** The reference's BASE_DARK: the dark prismarine. */
    public BlockType prismarineDark() {
        return prismarineDark;
    }

    /** The reference's LIGHT_SOURCE: the sea lantern. */
    public BlockType seaLantern() {
        return seaLantern;
    }

    public BlockType sponge() {
        return sponge;
    }

    /** The wet sponge (the top rooms' planted pair). */
    public BlockType wetSponge() {
        return wetSponge;
    }

    public BlockType goldBlock() {
        return goldBlock;
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
