package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.DyeFamilyBlocks;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.server.block.FenceGateBlocks;
import net.zaminmc.torch.server.block.TrapdoorBlocks;
import net.zaminmc.torch.server.block.WallBlocks;
import net.zaminmc.torch.server.block.WoodDoorFamily;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-SP2's behavioral seat (the floor-consult family): the SIX survival
 * walks the redstone dispatch gates on the one collision-solid question —
 * {@code RedstoneSystem.neighborChanged}'s wire arm (line 864), the
 * repeater arm (line 888), the comparator arm (line 903), the weighted
 * plate arm (line 963), the plate arm (line 974), and the door's
 * {@code doorNeighborChanged} (line 1047) — all read
 * {@code RedstoneSystem.hasSupportBelow} (line 2297:
 * {@code WorldSolidity.isSolid(world.getBlock(pos.down()))}) or its inline
 * twin. The five placement gates
 * ({@code EngineServer.placeRedstoneDustOnTick} line 13106,
 * {@code placePlateOnTick} line 13308, {@code placeRepeaterOnTick} line
 * 13392, {@code placeComparatorOnTick} line 13432,
 * {@code placeDoorOnTick} line 15770) ride the same consult inline — the
 * same rows, amplified at placement time (their census rides the
 * independent pass, the F-SP1 precedent).
 *
 * <p>The reference (reference/1.8.8) answers FOUR laws over ONE
 * structured question:</p>
 * <ul>
 *   <li><b>The wire</b> — {@code RedstoneWireBlock.canBePlaced} lines
 *       92-94: {@code World.hasSolidTop(world, pos.down()) ||
 *       world.getBlockState(pos.down()).getBlock() == Blocks.GLOWSTONE}
 *       — the structured law PLUS an explicit glowstone row (the
 *       survival walk, {@code neighborChanged} lines 235-244, re-consults
 *       {@code canBePlaced} whole).</li>
 *   <li><b>The diodes</b> (the repeater AND the comparator — both extend
 *       {@code DiodeBlock}) — {@code canSurvive} lines 33-34:
 *       {@code World.hasSolidTop(world, pos.down())} (the survival walk,
 *       lines 80-91, re-consults it).</li>
 *   <li><b>The plates</b> (the plain pair AND the weighted pair — both
 *       extend {@code AbstractPressurePlateBlock}) —
 *       {@code canSitOnTop} lines 84-85: {@code World.hasSolidTop(world,
 *       pos) || world.getBlockState(pos).getBlock() instanceof
 *       FenceBlock} (the survival walk, lines 77-82, re-consults it —
 *       the {@code canBePlaced} gate lines 72-74 delegates the same law).
 *       {@code FenceGateBlock extends HorizontalFacingBlock} (line 16) —
 *       NOT a fence; {@code nether_brick_fence} IS a {@code FenceBlock}
 *       (Block.java line 1235).</li>
 *   <li><b>The door</b> — {@code DoorBlock.canBePlaced} lines 213-214
 *       and the survival walk's floor consult (line 175):
 *       {@code World.hasSolidTop(world, pos.down())}.</li>
 * </ul>
 *
 * <p>{@code World.hasSolidTop} (lines 1740-1752): {@code material
 * .isSolidBlocking() && isCube()} — the translucent materials (LEAVES,
 * GLASS — glass, stained glass AND glowstone —, TNT, ICE, SNOW_LAYER,
 * CACTUS; Material.java lines 13/22/24/26/28/30) never answer — else the
 * stairs/slab TOP-half arms, else the hopper, else the 7-layer snow. The
 * non-cubes (bottom slabs and stairs, fences, walls, panes, bars,
 * chests, trapdoors, gates — FenceBlock.java line 119, WallBlock.java
 * line 52, PaneBlock.java line 52, ChestBlock.java line 46,
 * TrapdoorBlock.java line 42, FenceGateBlock.java line 79) refuse through
 * the first arm.</p>
 *
 * <p>Every row below drives the REAL survival walk: the attachment is
 * placed on a valid stone floor, the floor cell is then REPLACED with
 * the row's block (one {@code world.setBlock}, whose change dispatch
 * fires the family's {@code neighborChanged} arm on the attachment's
 * cell synchronously), and the seat records whether the attachment
 * survived. The assertion compares the whole table against the
 * reference's law — on the unfixed baseline the divergent rows answer
 * the opposite value and the failure message carries the COMPLETE row
 * table (the RED evidence); the parity pin rides the fix branch.</p>
 *
 * <p>The rail family is NOT seated here: its survival walk is carried by
 * {@code BlockUpdateSystem.runUpdate}'s rail arm
 * ({@code RailConnections.survivesAt} — the structured
 * {@code FireBlocks.hasSolidTop} consult, the thirteenth pass's
 * correction), which dominates the RedstoneSystem trio arm's laxer
 * collision consult on every row — the rail's observable divergence
 * rides F-SP3's two defects in the shared port, not this family's
 * consult (the fortieth pass's census correction, recorded in the
 * report).</p>
 */
class FloorConsultParityDiscoveryTest {

    @TempDir
    Path dataDir;

    /** The row cursor: every row takes fresh coordinates inside one server. */
    private int cursor = 0;

    private int nextX() {
        cursor += 4;
        return -50 + (cursor % 100);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the wire's floor consult (hasSolidTop || GLOWSTONE)
    // ------------------------------------------------------------------

    /**
     * The wire ({@code redstone_wire}, power 0) survives only while its
     * floor answers {@code hasSolidTop(pos.down()) || GLOWSTONE}: the
     * structured law's rows plus the explicit glowstone row. The controls
     * (stone, the double slab, the top halves, packed ice, the hopper,
     * glowstone) hold; every translucent material and every non-cube
     * pops the wire.
     */
    @Test
    void theWirePopsWhenTheFloorStopsAnsweringHasSolidTopOrGlowstone() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            wireRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone is solid-blocking AND a cube — the control");
            wireRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE (SlabBlock.isCube = isDouble) — the control");
            wireRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            wireRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            wireRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent (Material.PACKED_ICE line 27) and a cube — the control");
            wireRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm (World.java line 1750) — the control");
            wireRow(server, rows, BuiltinBlocks.GLOWSTONE, true,
                    "glowstone's material is GLASS (Block.java line 1191) — hasSolidTop REFUSES, "
                            + "but the wire's OWN explicit row (canBePlaced line 93) hosts it");
            wireRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP (World.java "
                            + "lines 1748-1750) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the red-sandstone BOTTOM single (stone_slab2): the SlabBlock arm requires "
                            + "HALF==TOP — the wire pops");
            wireRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair: the StairsBlock arm requires HALF==TOP (World.java "
                            + "lines 1745-1746) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT (Material.GLASS line 22) — isSolidBlocking refuses; "
                            + "the wire pops");
            wireRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT (Material.GLASS, Block.java line 1197) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT (Material.LEAVES line 13) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT (Material.TNT line 24) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT (Material.ICE line 26) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow's material is TRANSLUCENT (Material.SNOW_LAYER line 28); "
                            + "the LAYERS==7 arm (line 1750) needs 7 layers — the wire pops");
            wireRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE (FenceBlock.isCube false, line 119) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence is a NON-CUBE (a FenceBlock, Block.java line 1235) "
                            + "— the wire pops (the fence row is the PLATES' alone)");
            wireRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE (WallBlock.isCube false, line 52) — "
                            + "the wire pops");
            wireRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and a NON-CUBE (PaneBlock.isCube false, "
                            + "line 52) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE (PaneBlock.isCube false, line 52) — the wire pops");
            wireRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE (ChestBlock.isCube false, line 46) — the wire pops");
            wireRow(server, rows, TrapdoorBlocks.state("trapdoor", 0, false, false), false,
                    "the closed trapdoor is a NON-CUBE (TrapdoorBlock.isCube false, line 42) "
                            + "— the wire pops");
            wireRow(server, rows, FenceGateBlocks.state("oak", 0, false, false), false,
                    "the closed fence gate is a NON-CUBE (FenceGateBlock.isCube false, line 79) "
                            + "— the wire pops");
            assertVanilla("seat 1 — the wire's floor consult (hasSolidTop || GLOWSTONE)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the diodes' floor consult (hasSolidTop; the repeater and
    // the comparator — DiodeBlock's two children)
    // ------------------------------------------------------------------

    /**
     * The repeater and the comparator (both {@code DiodeBlock} children)
     * survive only while the floor answers {@code hasSolidTop} — NO
     * glowstone row (the wire's alone), NO fence row (the plates' alone).
     */
    @Test
    void theDiodesPopWhenTheFloorStopsAnsweringHasSolidTop() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            diodeRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone is solid-blocking AND a cube — the control");
            diodeRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE — the control");
            diodeRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            diodeRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            diodeRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent and a cube — the control");
            diodeRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm — the control");
            diodeRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS (Block.java line 1191) — hasSolidTop REFUSES "
                            + "and the diode carries NO glowstone row (the wire's alone) — "
                            + "the diode pops");
            diodeRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the red-sandstone BOTTOM single: the SlabBlock arm requires HALF==TOP — "
                            + "the diode pops");
            diodeRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair: the StairsBlock arm requires HALF==TOP — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — the diode pops");
            diodeRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow: TRANSLUCENT material, the LAYERS==7 arm needs 7 — "
                            + "the diode pops");
            diodeRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — the diode pops (the fence row is the PLATES' alone)");
            diodeRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence is a NON-CUBE — the diode pops");
            diodeRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and a NON-CUBE — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE — the diode pops");
            diodeRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — the diode pops");
            diodeRow(server, rows, TrapdoorBlocks.state("trapdoor", 0, false, false), false,
                    "the closed trapdoor is a NON-CUBE — the diode pops");
            diodeRow(server, rows, FenceGateBlocks.state("oak", 0, false, false), false,
                    "the closed fence gate is a NON-CUBE — the diode pops");
            assertVanilla("seat 2 — the diodes' floor consult (hasSolidTop, no explicit rows)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the plates' floor consult (hasSolidTop || FenceBlock; the
    // plain pair and the weighted pair — the two dispatch arms)
    // ------------------------------------------------------------------

    /**
     * The stone plate ({@code RedstoneBlocks.isPlate}'s arm) and the
     * light weighted plate ({@code WeightedPlateBlocks}'s arm) survive
     * only while the floor answers {@code canSitOnTop} —
     * {@code hasSolidTop || instanceof FenceBlock}: BOTH fence kinds
     * host (the nether fence IS a FenceBlock), the gate does NOT
     * ({@code FenceGateBlock extends HorizontalFacingBlock}), and
     * glowstone does NOT (no glowstone row — the wire's alone).
     */
    @Test
    void thePlatesPopWhenTheFloorStopsAnsweringHasSolidTopOrFence() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            plateRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone is solid-blocking AND a cube — the control");
            plateRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE — the control");
            plateRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            plateRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            plateRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent and a cube — the control");
            plateRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm — the control");
            plateRow(server, rows, BuiltinBlocks.FENCE, true,
                    "the fence answers the plates' OWN FenceBlock row (canSitOnTop line 85) "
                            + "— the plate holds");
            plateRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, true,
                    "the nether brick fence IS a FenceBlock (Block.java line 1235) — the "
                            + "plate holds");
            plateRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS — hasSolidTop REFUSES and the plate carries "
                            + "NO glowstone row — the plate pops");
            plateRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP — the plate pops");
            plateRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the red-sandstone BOTTOM single: the SlabBlock arm requires HALF==TOP — "
                            + "the plate pops");
            plateRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair: the StairsBlock arm requires HALF==TOP — the plate pops");
            plateRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the plate pops");
            plateRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the plate pops");
            plateRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the plate pops");
            plateRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow: TRANSLUCENT material, the LAYERS==7 arm needs 7 — "
                            + "the plate pops");
            plateRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, TrapdoorBlocks.state("trapdoor", 0, false, false), false,
                    "the closed trapdoor is a NON-CUBE and NOT a FenceBlock — the plate pops");
            plateRow(server, rows, FenceGateBlocks.state("oak", 0, false, false), false,
                    "the closed fence gate is a NON-CUBE and NOT a FenceBlock "
                            + "(FenceGateBlock extends HorizontalFacingBlock, line 16) — "
                            + "the plate pops");
            assertVanilla("seat 3 — the plates' floor consult (hasSolidTop || FenceBlock)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the door's floor consult (hasSolidTop; the pair's walk)
    // ------------------------------------------------------------------

    /**
     * The door pair ({@code WoodDoorFamily}'s oak lower + upper) survives
     * only while the floor below the LOWER answers {@code hasSolidTop}
     * (DoorBlock's survival walk, line 175): the pair dies WHOLE when the
     * floor refuses (both halves removed, one drop — the seat reads both
     * cells). NO glowstone row, NO fence row.
     */
    @Test
    void theDoorPopsWhenTheFloorStopsAnsweringHasSolidTop() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            doorRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone is solid-blocking AND a cube — the control");
            doorRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE — the control");
            doorRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            doorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            doorRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent and a cube — the control");
            doorRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm — the control");
            doorRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS — hasSolidTop REFUSES and the door carries "
                            + "NO glowstone row — the pair pops");
            doorRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP — the pair pops");
            doorRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the red-sandstone BOTTOM single: the SlabBlock arm requires HALF==TOP — "
                            + "the pair pops");
            doorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair: the StairsBlock arm requires HALF==TOP — the pair pops");
            doorRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — the pair pops");
            doorRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the pair pops");
            doorRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the pair pops");
            doorRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the pair pops");
            doorRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the pair pops");
            doorRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow: TRANSLUCENT material, the LAYERS==7 arm needs 7 — "
                            + "the pair pops");
            doorRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — the pair pops (the fence row is the PLATES' alone)");
            doorRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence is a NON-CUBE — the pair pops");
            doorRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — the pair pops");
            doorRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and a NON-CUBE — the pair pops");
            doorRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE — the pair pops");
            doorRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — the pair pops");
            doorRow(server, rows, TrapdoorBlocks.state("trapdoor", 0, false, false), false,
                    "the closed trapdoor is a NON-CUBE — the pair pops");
            doorRow(server, rows, FenceGateBlocks.state("oak", 0, false, false), false,
                    "the closed fence gate is a NON-CUBE — the pair pops");
            assertVanilla("seat 4 — the door's floor consult (hasSolidTop, the pair's walk)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Row drivers (the F-SP1 seat shape)
    // ------------------------------------------------------------------

    /**
     * One wire row: the stone floor below the wire, the wire placed, the
     * floor REPLACED — the survival walk consults the floor through the
     * wire arm's {@code hasSupportBelow}.
     */
    private void wireRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                driveSurvivalWalk(server, new BlockPosition(x, y - 1, z),
                        new BlockPosition(x, y, z), RedstoneBlocks.wireOfPower(0), mount), law));
    }

    /**
     * One diode row: TWO cells — the repeater and the comparator, each
     * with its own stone floor — both floors REPLACED; the row holds only
     * when BOTH survive (both dispatch arms ride the same consult).
     */
    private void diodeRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        boolean repeaterHolds = driveSurvivalWalk(server, new BlockPosition(x, y - 1, z),
                new BlockPosition(x, y, z), RedstoneBlocks.repeaterOf(0, 1, false), mount);
        boolean comparatorHolds = driveSurvivalWalk(server, new BlockPosition(x + 1, y - 1, z),
                new BlockPosition(x + 1, y, z), RedstoneBlocks.comparatorOf(0, false, false), mount);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                repeaterHolds && comparatorHolds, law));
    }

    /**
     * One plate row: TWO cells — the stone plate (the {@code isPlate}
     * dispatch arm) and the light weighted plate (the
     * {@code WeightedPlateBlocks} dispatch arm), each with its own stone
     * floor — both floors REPLACED; the row holds only when BOTH survive.
     * The wooden plate and the heavy weighted twin ride the same two arms.
     */
    private void plateRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        boolean stonePlateHolds = driveSurvivalWalk(server, new BlockPosition(x, y - 1, z),
                new BlockPosition(x, y, z), RedstoneBlocks.plateOf(false, false), mount);
        boolean weightedPlateHolds = driveSurvivalWalk(server, new BlockPosition(x + 1, y - 1, z),
                new BlockPosition(x + 1, y, z), WeightedPlateBlocks.state(0, 0), mount);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                stonePlateHolds && weightedPlateHolds, law));
    }

    /**
     * One door row: the FULL oak pair (the lower then the upper, one
     * commit — the pair's own placement order), the floor below the lower
     * REPLACED — the lower's walk consults the floor; the pair dies WHOLE
     * (the upper removed silently, the lower popped with its drop — the
     * reference's own shape). The row holds only when BOTH halves survive.
     */
    private void doorRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 40;
        int z = 60 + (cursor % 20);
        BlockPosition floorCell = new BlockPosition(x, y - 1, z);
        BlockPosition lowerCell = new BlockPosition(x, y, z);
        BlockPosition upperCell = new BlockPosition(x, y + 1, z);
        BlockType lower = WoodDoorFamily.lower("oak", 0, false);
        BlockType upper = WoodDoorFamily.upper("oak", false, false, false);

        // 1. the valid pair: the stone floor, then the lower, then the
        //    upper (the upper's commit wakes the lower's walk — the pair
        //    check passes with the floor in place).
        server.ticker().submit(() -> {
            server.world().setBlock(floorCell, BuiltinBlocks.STONE);
            server.world().setBlock(lowerCell, lower);
            server.world().setBlock(upperCell, upper);
        });
        assertEquals(lower, typeAt(server, lowerCell),
                "the door pair landed on the stone floor (the row's precondition)");
        assertEquals(upper, typeAt(server, upperCell),
                "the door pair landed whole (the row's precondition)");

        // 2. the floor swap — the lower's walk consults the new floor.
        server.ticker().submit(() -> server.world().setBlock(floorCell, mount));

        // 3. the settle window, then the read of BOTH halves.
        Thread.sleep(150);
        BlockType lowerOutcome = typeAt(server, lowerCell);
        BlockType upperOutcome = typeAt(server, upperCell);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                lower.equals(lowerOutcome) && upper.equals(upperOutcome), law));
    }

    /**
     * The shared row driver: place the stone mount and the attachment (a
     * valid pair), verify the attachment landed, REPLACE the mount with the
     * row's block (one setBlock whose change dispatch fires the attachment
     * cell's neighborChanged synchronously), then read the attachment cell
     * and return whether it SURVIVED. The hold rows settle a window before
     * the read so a deferred pop cannot pass silently.
     */
    private boolean driveSurvivalWalk(EngineServer server, BlockPosition mountCell,
            BlockPosition attachmentCell, BlockType attachment, BlockType mount)
            throws Exception {
        // 1. the valid pair: the mount first, then the attachment.
        server.ticker().submit(() -> {
            server.world().setBlock(mountCell, BuiltinBlocks.STONE);
            server.world().setBlock(attachmentCell, attachment);
        });
        assertEquals(attachment, typeAt(server, attachmentCell),
                "the attachment landed on the stone mount (the row's precondition)");

        // 2. the mount swap — the survival walk consults the new mount
        //    (the walk runs synchronously inside the change dispatch).
        server.ticker().submit(() -> server.world().setBlock(mountCell, mount));

        // 3. the settle window, then the read through the tick thread (a
        //    deferred pop must not pass silently).
        Thread.sleep(150);
        BlockType outcome = typeAt(server, attachmentCell);
        return attachment.equals(outcome);
    }

    /**
     * The table assertion: every row's engine outcome must equal the
     * reference's law. The failure message carries the COMPLETE row table
     * — the RED evidence — with the divergent rows listed first.
     */
    private static void assertVanilla(String seat, List<RowOutcome> rows) {
        StringBuilder divergent = new StringBuilder();
        StringBuilder table = new StringBuilder();
        for (RowOutcome row : rows) {
            String line = String.format("%-24s vanilla=%-4s engine=%-4s — %s%n",
                    row.mount, row.vanillaHolds ? "hold" : "pop",
                    row.engineHolds ? "hold" : "pop", row.law);
            table.append(line);
            if (row.vanillaHolds != row.engineHolds) {
                divergent.append(line);
            }
        }
        assertTrue(divergent.length() == 0,
                seat + " — the divergent rows (the reference's law vs the engine's answer):"
                        + System.lineSeparator() + divergent
                        + "The complete row table:" + System.lineSeparator() + table);
    }

    /** One battery row's outcome: the mount, the reference's answer, the
     *  engine's answer, and the law's citation. */
    private static final class RowOutcome {
        final String mount;
        final boolean vanillaHolds;
        final boolean engineHolds;
        final String law;

        RowOutcome(String mount, boolean vanillaHolds, boolean engineHolds, String law) {
            this.mount = mount;
            this.vanillaHolds = vanillaHolds;
            this.engineHolds = engineHolds;
            this.law = law;
        }
    }

    private static BlockType stainedGlass() {
        // the white member (color 0) of the flat stained-glass family —
        // the reference's single StainedGlassBlock instance (id 95)
        return new EngineBlockType(
                DyeFamilyBlocks.stainedGlassBlock(0), "Stained Glass", 0);
    }

    // ------------------------------------------------------------------
    // Harness (the SupportConsultParityDiscoveryTest shape)
    // ------------------------------------------------------------------

    /**
     * Reads the block type through the tick thread (the submit queue's
     * synchronization gives the happens-before edge the plain chunk arrays
     * don't — a test-thread read races the cascade's writes).
     */
    private static BlockType typeAt(EngineServer server, BlockPosition pos) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(server.world().getBlock(pos)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result.get();
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "sp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
