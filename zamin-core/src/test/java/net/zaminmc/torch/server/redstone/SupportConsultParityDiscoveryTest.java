package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.DyeFamilyBlocks;
import net.zaminmc.torch.server.block.EngineBlockType;
import net.zaminmc.torch.server.block.TrapdoorBlocks;
import net.zaminmc.torch.server.block.WallBlocks;
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
 * F-SP1's behavioral seat (the support-consult family): the four mount
 * consults the redstone family's neighborChanged survival walks read —
 * {@code RedstoneSystem.torchSupported} (line 1685),
 * {@code RedstoneSystem.leverSupported} (line 2223),
 * {@code RedstoneSystem.buttonSupported} (line 2229) and
 * {@code RedstoneSystem.trapdoorMountHolds} (line 1349) — each drive
 * through the one collision-solid question {@code WorldSolidity.isSolid}.
 *
 * <p>The reference (reference/1.8.8) answers THREE different laws, one per
 * consult site:</p>
 * <ul>
 *   <li><b>The torch's WALL arm</b> — {@code TorchBlock.tryBreak} lines
 *       96-118: {@code world.isSolidBlockingCube(pos.offset(opposite), true)}
 *       = {@code material.isSolidBlocking() && isCube()} (World.java lines
 *       1753-1768). The translucent materials — LEAVES, GLASS (glass,
 *       stained glass, glowstone, glass panes), TNT, ICE, SNOW_LAYER, CACTUS
 *       (Material.java lines 13/22/24/26/28/30; glowstone and stained glass
 *       REGISTER with Material.GLASS, Block.java lines 1191/1197) — NEVER
 *       answer; the non-cubes (fences, slabs, stairs, panes, bars, walls,
 *       chests, hoppers) NEVER answer.</li>
 *   <li><b>The torch's FLOOR arm</b> — {@code TorchBlock.canSitOnTop} lines
 *       49-55: {@code World.hasSolidTop} (the five arms — the doubles, the
 *       TOP halves, the hoppers, the 7-layer snow) OR the explicit list
 *       {@code FenceBlock || GLASS || COBBLESTONE_WALL || STAINED_GLASS}.</li>
 *   <li><b>The lever/button consult</b> — {@code ButtonBlock.canSurvive}
 *       lines 70-73 ({@code LeverBlock.canSurvive} line 60 delegates): the
 *       DOWN arm (the FLOOR mount — FACING=UP, consult direction DOWN) reads
 *       {@code World.hasSolidTop}; every other side (the walls AND the
 *       ceiling) reads {@code Block.isSolid()} = {@code isSolidBlocking &&
 *       isCube() && !isSignalSource()} (Block.java line 274).</li>
 *   <li><b>The trapdoor's consult</b> — {@code TrapdoorBlock.canBePlacedOn}
 *       lines 188-190: {@code isSolidBlocking && isCube() || GLOWSTONE ||
 *       instanceof SlabBlock || instanceof StairsBlock} — ANY slab half, ANY
 *       stair state (no HALF condition), and no hopper arm.</li>
 * </ul>
 *
 * <p>Every row below drives the REAL survival walk: the attachment is placed
 * on a valid stone mount, the mount cell is then REPLACED with the row's
 * block (one {@code world.setBlock}, whose change dispatch fires the
 * per-family {@code neighborChanged} on the attachment's cell), and the seat
 * records whether the attachment survived. The assertion compares the whole
 * table against the reference's law — on the unfixed baseline the divergent
 * rows answer the opposite value and the failure message carries the
 * COMPLETE row table (the RED evidence); the parity pin rides the fix
 * branch.</p>
 */
class SupportConsultParityDiscoveryTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // Seat 1 — the torch's WALL consult (isSolidBlockingCube)
    // ------------------------------------------------------------------

    /**
     * The wall torch ({@code redstone_torch_west} — pointing west, mounted
     * on the block to its east) survives only while its mount answers
     * {@code isSolidBlockingCube}: non-translucent material AND a full
     * cube. Stone and the double slab answer true (the controls); every
     * translucent material and every non-cube answers false — the torch
     * pops (TorchBlock.tryBreak lines 108-110: drop + removeBlock).
     */
    @Test
    void theWallTorchPopsWhenTheMountStopsAnsweringIsSolidBlockingCube() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            wallTorchRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone wall holds the torch (isSolidBlocking && isCube — the control)");
            wallTorchRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the torch (SlabBlock.isCube = isDouble — the control)");
            wallTorchRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT (Material.GLASS line 22) — isSolidBlockingCube "
                            + "refuses; the torch pops");
            wallTorchRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT (Material.GLASS, Block.java line 1197) "
                            + "— the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone REGISTERS with Material.GLASS (Block.java line 1191) — "
                            + "translucent; the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT (Material.ICE line 26) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT (Material.TNT line 24) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT (Material.LEAVES line 13) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.CACTUS, false,
                    "cactus is TRANSLUCENT (Material.CACTUS line 30) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE (FenceBlock.isCube false, line 119) — "
                            + "isSolidBlockingCube refuses; the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence is a NON-CUBE (a FenceBlock, Block.java "
                            + "line 1235) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the single bottom slab is a NON-CUBE (SlabBlock.isCube false) "
                            + "— the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, false,
                    "the single top slab is a NON-CUBE — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the bottom stair is a NON-CUBE (StairsBlock.isCube false) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, false,
                    "the top stair is a NON-CUBE — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is translucent AND a NON-CUBE (PaneBlock) — the torch pops");
            wallTorchRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE (PaneBlock, Block.java line 1206) "
                            + "— the torch pops");
            wallTorchRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE (WallBlock.isCube false, line 52) "
                            + "— the torch pops (the FLOOR consult accepts it; the WALL "
                            + "consult refuses — different laws)");
            wallTorchRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE (ChestBlock) — the torch pops");
            wallTorchRow(server, rows, HopperBlocks.hopperOf(0, true), false,
                    "the hopper is a NON-CUBE (HopperBlock.isCube false, line 138) — "
                            + "the torch pops (the FLOOR consult accepts it — hasSolidTop's "
                            + "hopper arm; the WALL consult refuses)");
            assertVanilla("seat 1 — the torch's WALL consult (isSolidBlockingCube)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the torch's FLOOR consult (canSitOnTop)
    // ------------------------------------------------------------------

    /**
     * The standing torch ({@code redstone_torch}) survives while its floor
     * answers {@code canSitOnTop} — {@code hasSolidTop}'s five arms OR the
     * explicit FENCES/GLASS/COBBLESTONE_WALL/STAINED_GLASS list. The
     * controls pin the accept set (the doubles, the TOP halves, the
     * explicit list, the hoppers); every other mount pops.
     */
    @Test
    void theStandingTorchPopsWhenTheFloorLeavesCanSitOnTopAcceptSet() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            torchFloorRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone floor holds the torch (the control)");
            torchFloorRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the torch (hasSolidTop's first arm — the control)");
            torchFloorRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the top slab holds the torch (hasSolidTop's SlabBlock arm wants "
                            + "HALF == TOP — the control)");
            torchFloorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the top stair holds the torch (hasSolidTop's StairsBlock arm wants "
                            + "HALF == TOP — the control)");
            torchFloorRow(server, rows, BuiltinBlocks.GLASS, true,
                    "the glass floor holds the torch (canSitOnTop's EXPLICIT list, line 53 "
                            + "— the control: the FLOOR law accepts glass where the WALL law "
                            + "refuses it)");
            torchFloorRow(server, rows, stainedGlass(), true,
                    "the stained glass floor holds the torch (the explicit list — the control)");
            torchFloorRow(server, rows, BuiltinBlocks.FENCE, true,
                    "the fence floor holds the torch (canSitOnTop's instanceof FenceBlock "
                            + "— the control)");
            torchFloorRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, true,
                    "the nether brick fence floor holds the torch (a FenceBlock INSTANCE, "
                            + "Block.java lines 1235-1236 — the control)");
            torchFloorRow(server, rows, WallBlocks.state(0), true,
                    "the cobblestone wall floor holds the torch (the explicit list — the control)");
            torchFloorRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper floor holds the torch (hasSolidTop's HopperBlock arm — the control)");
            torchFloorRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM slab fails hasSolidTop (the SlabBlock arm wants HALF == TOP) "
                            + "and is not in the explicit list — the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair fails hasSolidTop (the StairsBlock arm wants HALF == TOP) "
                            + "— the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is not in the explicit list and fails hasSolidTop "
                            + "— the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are not in the explicit list and fail hasSolidTop "
                            + "— the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is translucent (hasSolidTop's first arm false) and not in the "
                            + "explicit list — the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is translucent and not in the explicit list — the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are translucent and not in the explicit list — the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.CACTUS, false,
                    "cactus is translucent and not in the explicit list — the torch pops");
            torchFloorRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is NOT in the explicit list (canSitOnTop names GLASS, not "
                            + "GLOWSTONE) and its Material.GLASS is translucent — the torch "
                            + "pops (the trapdoor's law is the one that names glowstone)");
            torchFloorRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE, not in the explicit list — the torch pops");
            assertVanilla("seat 2 — the torch's FLOOR consult (canSitOnTop)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the lever/button WALL consult (Block.isSolid)
    // ------------------------------------------------------------------

    /**
     * The wall lever ({@code minecraft:lever_east} — the east-facing lever,
     * mounted on the block to its west) and the wall button
     * ({@code minecraft:stone_button_east}) survive while their mount
     * answers {@code Block.isSolid()} — non-translucent material AND a
     * full cube AND not a signal source. Stone and the double slab answer
     * true (the controls); the translucents and the non-cubes answer false
     * — the lever and the button pop (LeverBlock.neighborChanged lines
     * 101-106, ButtonBlock.neighborChanged lines 89-95).
     */
    @Test
    void theWallLeverAndButtonPopWhenTheMountStopsAnsweringIsSolid() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            leverButtonWallRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone wall holds the lever and the button (isSolid — the control)");
            leverButtonWallRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the lever and the button (isCube true, rock "
                            + "material — the control)");
            leverButtonWallRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — Block.isSolid refuses; the lever and the button pop");
            leverButtonWallRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is TRANSLUCENT (Material.GLASS at registration) — the lever "
                            + "and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.CACTUS, false,
                    "cactus is TRANSLUCENT — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — Block.isSolid refuses; the lever and the "
                            + "button pop (the TORCH's floor law accepts fences; the "
                            + "lever/button laws never do)");
            leverButtonWallRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the single slab is a NON-CUBE — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the stair is a NON-CUBE — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is translucent AND a NON-CUBE — the lever and the button pop");
            leverButtonWallRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE — the lever and the button pop");
            leverButtonWallRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — the lever and the button pop "
                            + "(the TORCH's floor law names walls; the lever/button laws "
                            + "never do)");
            leverButtonWallRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — the lever and the button pop");
            leverButtonWallRow(server, rows, HopperBlocks.hopperOf(0, true), false,
                    "the hopper is a NON-CUBE — the lever and the button pop (the FLOOR "
                            + "consult accepts hoppers through hasSolidTop; the WALL consult "
                            + "refuses them)");
            assertVanilla("seat 3 — the lever/button WALL consult (Block.isSolid)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the lever/button FLOOR consult (the DOWN arm: hasSolidTop)
    // ------------------------------------------------------------------

    /**
     * The floor lever ({@code minecraft:lever_up_z} — FACING UP, the
     * consult direction DOWN) and the floor button
     * ({@code minecraft:stone_button_up}) ride the DOWN arm of
     * {@code ButtonBlock.canSurvive} — {@code World.hasSolidTop} — which is
     * NOT the torch floor's law: no fence exception, no glass exception, no
     * wall exception. The TOP halves and the hoppers hold (the controls);
     * the BOTTOM halves, the fences, the glass and the walls pop.
     */
    @Test
    void theFloorLeverAndButtonPopWhenTheFloorFailsHasSolidTop() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            leverButtonFloorRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone floor holds the lever and the button (the control)");
            leverButtonFloorRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the top slab holds the lever and the button (hasSolidTop's SlabBlock "
                            + "arm wants HALF == TOP — the control)");
            leverButtonFloorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the top stair holds the lever and the button (hasSolidTop's StairsBlock "
                            + "arm — the control)");
            leverButtonFloorRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the lever and the button (hasSolidTop's first "
                            + "arm — the control)");
            leverButtonFloorRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper floor holds the lever and the button (hasSolidTop's "
                            + "HopperBlock arm — the control)");
            leverButtonFloorRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM slab fails hasSolidTop — the lever and the button pop");
            leverButtonFloorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair fails hasSolidTop — the lever and the button pop");
            leverButtonFloorRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — hasSolidTop's first arm refuses AND the DOWN "
                            + "arm carries NO glass exception (the torch floor's explicit "
                            + "list is the TORCH's alone) — the lever and the button pop");
            leverButtonFloorRow(server, rows, stainedGlass(), false,
                    "stained glass fails hasSolidTop — the lever and the button pop");
            leverButtonFloorRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence fails hasSolidTop (non-cube) AND the DOWN arm carries NO "
                            + "fence exception — the lever and the button pop (the torch "
                            + "floor's instanceof FenceBlock is the TORCH's alone)");
            leverButtonFloorRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall fails hasSolidTop (non-cube) with no wall "
                            + "exception — the lever and the button pop");
            leverButtonFloorRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is TRANSLUCENT — hasSolidTop refuses — the lever and the "
                            + "button pop");
            leverButtonFloorRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest fails hasSolidTop (non-cube) — the lever and the button pop");
            assertVanilla("seat 4 — the lever/button FLOOR consult (the DOWN arm: hasSolidTop)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 5 — the lever/button CEILING consult (isSolid on the block above)
    // ------------------------------------------------------------------

    /**
     * The ceiling lever ({@code minecraft:lever_down_x} — FACING DOWN, the
     * consult direction UP) and the ceiling button
     * ({@code minecraft:stone_button_down}) ride the NOT-DOWN arm of
     * {@code ButtonBlock.canSurvive} — {@code Block.isSolid()} on the block
     * ABOVE. Stone and the double slab answer true (the controls); the
     * translucents and BOTH slab halves (isCube false, whatever the half)
     * answer false — the lever and the button drop.
     */
    @Test
    void theCeilingLeverAndButtonPopWhenTheBlockAboveStopsAnsweringIsSolid() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            leverButtonCeilingRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone ceiling holds the lever and the button (the control)");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the lever and the button (isCube true — the control)");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — Block.isSolid refuses — the lever and the "
                            + "button drop");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone is TRANSLUCENT — the lever and the button drop");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM slab is a NON-CUBE (isCube false, whatever the half) — "
                            + "the lever and the button drop");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, false,
                    "the TOP slab is a NON-CUBE — the lever and the button drop");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE — the lever and the button drop");
            leverButtonCeilingRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — the lever and the button drop");
            assertVanilla("seat 5 — the lever/button CEILING consult (isSolid on the block above)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 6 — the trapdoor's mount consult (canBePlacedOn)
    // ------------------------------------------------------------------

    /**
     * The wall-mounted trapdoor ({@code minecraft:trapdoor} — facing north,
     * mounted on the block to its south) survives while its mount answers
     * {@code canBePlacedOn}: {@code isSolidBlocking && isCube()} OR
     * GLOWSTONE OR {@code instanceof SlabBlock} OR {@code instanceof
     * StairsBlock}. The controls pin the whole accept set — ANY slab half
     * (no HALF condition, unlike hasSolidTop), ANY stair state, glowstone,
     * the doubles, stone; the translucents and the non-cubes outside the
     * instance arms pop — INCLUDING the fence (the engine's final arm
     * carries a dead carve: {@code value.startsWith("minecraft:fence")} is
     * tested against the BARE name, so it never fires) and the hopper (no
     * hopper arm in canBePlacedOn — unlike hasSolidTop).
     */
    @Test
    void theTrapdoorPopsWhenTheMountLeavesCanBePlacedOnAcceptSet() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            trapdoorRow(server, rows, BuiltinBlocks.STONE, true,
                    "the stone wall holds the trapdoor (the control)");
            trapdoorRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab holds the trapdoor (isSolidBlocking && isCube — the control)");
            trapdoorRow(server, rows, BuiltinBlocks.STONE_SLAB, true,
                    "the BOTTOM slab holds the trapdoor (canBePlacedOn's instanceof SlabBlock "
                            + "arm — ANY half, no HALF condition — the control)");
            trapdoorRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP slab holds the trapdoor (the same instance arm — the control)");
            trapdoorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, true,
                    "the BOTTOM stair holds the trapdoor (canBePlacedOn's instanceof "
                            + "StairsBlock arm — ANY state — the control)");
            trapdoorRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair holds the trapdoor (the same instance arm — the control)");
            trapdoorRow(server, rows, BuiltinBlocks.GLOWSTONE, true,
                    "glowstone holds the trapdoor (canBePlacedOn's EXPLICIT row — the "
                            + "control; the torch and lever/button laws refuse glowstone, "
                            + "the trapdoor's law names it)");
            trapdoorRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — canBePlacedOn's first arm refuses — "
                            + "the trapdoor pops");
            trapdoorRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.CACTUS, false,
                    "cactus is TRANSLUCENT — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is translucent AND a NON-CUBE — the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE outside the instance arms — "
                            + "the trapdoor pops");
            trapdoorRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE outside the instance arms — "
                            + "the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE outside the instance arms (the engine's own "
                            + "chest carve agrees) — the trapdoor pops");
            trapdoorRow(server, rows, HopperBlocks.hopperOf(0, true), false,
                    "the hopper is a NON-CUBE and canBePlacedOn carries NO hopper arm "
                            + "(hasSolidTop's hopper arm is the FLOOR consults' alone) — "
                            + "the trapdoor pops");
            trapdoorRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE outside the instance arms — canBePlacedOn "
                            + "refuses; the engine's final arm carries a DEAD carve "
                            + "(value.startsWith(\"minecraft:fence\") tested against the "
                            + "BARE name never fires) so the unfixed engine holds the "
                            + "trapdoor where vanilla pops it");
            assertVanilla("seat 6 — the trapdoor's mount consult (canBePlacedOn)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The row drivers — each row drives the real neighborChanged walk
    // ------------------------------------------------------------------

    /** One row's outcome: the mount block, the reference's law, the engine's answer. */
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

    /** The row cursor: every row takes fresh coordinates inside one server. */
    private int cursor = 0;

    private int nextX() {
        cursor += 4;
        return -50 + (cursor % 100);
    }

    /**
     * One wall-torch row: the stone mount east of the torch cell, the torch
     * placed pointing west, the mount REPLACED with the row's block — the
     * survival walk consults the mount through TorchBlock.tryBreak's
     * horizontal arm (isSolidBlockingCube).
     */
    private void wallTorchRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 40;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x + 1, y, z);
        BlockPosition torchCell = new BlockPosition(x, y, z);
        // the wall pair member pointing WEST (reference FACING id 4 — the
        // redstone_torch_west state), mounted on the block to its east
        BlockType torch = RedstoneBlocks.torchOfFacing(4, true);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                driveSurvivalWalk(server, mountCell, torchCell, torch, mount), law));
    }

    /**
     * One standing-torch row: the stone floor below the torch cell, the
     * standing torch placed, the floor REPLACED with the row's block — the
     * survival walk consults the floor through canSitOnTop.
     */
    private void torchFloorRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x, y - 1, z);
        BlockPosition torchCell = new BlockPosition(x, y, z);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                driveSurvivalWalk(server, mountCell, torchCell,
                        RedstoneBlocks.torchStandingLit(), mount), law));
    }

    /**
     * One lever/button wall row: the stone mount west of the cells, the
     * east-facing lever and button placed, the mount REPLACED — both
     * survival walks consult the mount through Block.isSolid. The row's
     * outcome is the AND of the two attachments' survivals.
     */
    private void leverButtonWallRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 40;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x - 1, y, z);
        boolean leverHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x, y, z),
                RedstoneBlocks.leverOf(1, false), mount);
        boolean buttonHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x, y + 1, z),
                RedstoneBlocks.buttonOf(5, false, false), mount);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                leverHolds && buttonHolds, law));
    }

    /**
     * One lever/button floor row: the stone floor below the cells, the
     * up-facing lever and button placed, the floor REPLACED — both survival
     * walks consult the floor through the DOWN arm (hasSolidTop).
     */
    private void leverButtonFloorRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x, y - 1, z);
        boolean leverHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x, y, z),
                RedstoneBlocks.leverOf(5, false), mount);
        boolean buttonHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x + 1, y, z),
                RedstoneBlocks.buttonOf(1, false, false), mount);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                leverHolds && buttonHolds, law));
    }

    /**
     * One lever/button ceiling row: the stone block above the cells, the
     * down-facing lever and button placed, the block above REPLACED — both
     * survival walks consult it through Block.isSolid.
     */
    private void leverButtonCeilingRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 41;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x, y + 1, z);
        boolean leverHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x, y, z),
                RedstoneBlocks.leverOf(0, false), mount);
        boolean buttonHolds = driveSurvivalWalk(server, mountCell, new BlockPosition(x + 1, y, z),
                RedstoneBlocks.buttonOf(0, false, false), mount);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                leverHolds && buttonHolds, law));
    }

    /**
     * One trapdoor row: the stone wall south of the trapdoor cell (the
     * north-facing trapdoor's mount), the trapdoor placed, the wall
     * REPLACED — the survival walk consults the wall through
     * canBePlacedOn.
     */
    private void trapdoorRow(EngineServer server, List<RowOutcome> rows, BlockType mount,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int y = 40;
        int z = 60 + (cursor % 20);
        BlockPosition mountCell = new BlockPosition(x, y, z + 1);
        rows.add(new RowOutcome(mount.identifier().value(), vanillaHolds,
                driveSurvivalWalk(server, mountCell, new BlockPosition(x, y, z),
                        TrapdoorBlocks.state("trapdoor", 0, false, false), mount), law));
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

    private static BlockType stainedGlass() {
        // the white member (color 0) of the flat stained-glass family —
        // the reference's single StainedGlassBlock instance (id 95)
        return new EngineBlockType(
                DyeFamilyBlocks.stainedGlassBlock(0), "Stained Glass", 0);
    }

    // ------------------------------------------------------------------
    // Harness (the RedstoneSystemTest shape)
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
