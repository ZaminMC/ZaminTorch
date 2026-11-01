package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import net.zaminmc.torch.testing.EngineAwaits;
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
 * F-SP3's behavioral seat (the hasSolidTop port's own defects): the fire
 * family's consults and the rail's survival walk all read
 * {@code FireBlocks.hasSolidTop} (line 325) — the engine's own structured
 * port — whose full-cube common case FALLS THROUGH to the collision
 * question {@code WorldSolidity.isSolid} (line 358) where the reference's
 * arm 1 requires {@code material.isSolidBlocking() && isCube()}
 * (World.java lines 1740-1752), and whose slab arm
 * {@code value.endsWith("_slab")} (lines 338-343) misses the second slab
 * family's flat ids ({@code stone_slab2} / {@code stone_slab2_top} —
 * BuiltinBlocks lines 90-92: "slab2" matches neither suffix).
 *
 * <p>The consumers this seat drives (the port's own census — the
 * wire/diode/plate/door consults ride F-SP2's own lane, PR #141 the
 * carrier):</p>
 * <ul>
 *   <li><b>The fire's placement-time support gate</b> — the reference's
 *       {@code FireBlock.onAdded} lines 318-325 ({@code !hasSolidTop(
 *       pos.down()) && !hasFlammableNeighbor(pos) -> removeBlock}); the
 *       engine's {@code FireBlocks.PlacementListener.onBlockReplaced} (the
 *       same law, line 688).</li>
 *   <li><b>The fire's neighbor-update survival walk</b> — the reference's
 *       {@code FireBlock.neighborChanged} lines 311-315; the engine's
 *       {@code BlockUpdateSystem.runUpdate} fire arm (line 855 ->
 *       {@code FireBlocks.neighborChanged}).</li>
 *   <li><b>The fire's scheduled tick re-check</b> — the reference's
 *       {@code FireBlock.tick} lines 146-148 ({@code !canBePlaced ->
 *       removeBlock} — the FIRST thing every scheduled fire tick does)
 *       and the no-fuel walk lines 166-170; the engine's
 *       {@code FireBlocks.tick} (the same two consults).</li>
 *   <li><b>The rail's survival walk</b> — the reference's
 *       {@code AbstractRailBlock.neighborChanged} lines 86-102 (the floor
 *       consult through {@code World.hasSolidTop}); the engine's
 *       {@code RailConnections.survivesAt} (lines 100-110) through the
 *       update bus's rail arm ({@code BlockUpdateSystem} line 508).</li>
 * </ul>
 *
 * <p>The reference's law over the divergent rows: the TRANSLUCENT
 * materials — glass, stained glass (Material.GLASS, Block.java lines
 * 1191/1197), glowstone, the leaves (Material.LEAVES line 13), TNT (line
 * 24), ice (line 26), cactus (line 30) — never answer arm 1
 * ({@code isSolidBlocking() = !translucent && blocksMovement()},
 * Material.java lines 100-102); the NON-CUBE collision-solid shapes the
 * port's carve list misses — the glass pane and the iron bars
 * (PaneBlock.isCube false, line 52), the closed trapdoor
 * (TrapdoorBlock.isCube false, line 42) — refuse through arm 1 too; and
 * the second slab family's BOTTOM single ({@code stone_slab2}) refuses
 * through arm 3 (SlabBlock HALF==TOP alone). The fire consults' SECOND
 * disjunct ({@code hasFlammableNeighbor}) rescues the flammable
 * translucents — the leaves (30/60) and TNT (15/100) HOLD in vanilla
 * through the fuel arm exactly as in the engine (the agreement-by-arm
 * rows) — while the RAIL consult carries no fuel disjunct: the same two
 * blocks POP in vanilla ({@code AbstractRailBlock.canBePlaced} line 70-72
 * is {@code hasSolidTop} ALONE) and the engine holds them — the seat
 * family's own discrimination.</p>
 *
 * <p>Every row drives the REAL walk: the attachment is placed over a
 * valid stone floor, the floor cell is then REPLACED with the row's block
 * (one {@code world.setBlock} whose six-neighbor dispatch wakes the
 * attachment cell's arm synchronously — the F-SP2 seat construction), and
 * the seat records whether the attachment survived. The placement-gate
 * seat places the flame directly over the row's floor (the onAdded gate
 * answers inside the commit). The tick seat places the flames and awaits
 * the engine's own tick clock past the first scheduled fire tick (30-39
 * ticks out). The assertion compares the whole table against the
 * reference's law — on the unfixed baseline the divergent rows answer the
 * opposite value and the failure message carries the COMPLETE row table
 * (the RED evidence); the parity pin rides the fix branch.</p>
 *
 * <p>THE SEAT'S OWN FINDS (the first run's corrections — the rows the
 * flammability map and the probe retired):
 * <ul>
 *   <li><b>The FLAMMABLE woods answer through the fuel arm in BOTH</b> —
 *       the bottom OAK stair and the OAK fence fail hasSolidTop in both
 *       worlds (the port's stairs arm and fence carve are correct for
 *       them) but the reference's flammability map carries them
 *       ({@code setFlammable(Blocks.OAK_STAIRS, 5, 20)} line 84,
 *       {@code setFlammable(Blocks.FENCE, 5, 20)} line 85 — the engine's
 *       own table the same rows) — the flame STANDS on both in vanilla
 *       through the fuel disjunct. The first draft's pop expectation for
 *       them was wrong; the rows now pin the discrimination (the
 *       flame's fuel arm vs the RAIL's pure consult — the same two
 *       blocks hold the flame and pop the rail). The stone family
 *       members ({@code cobblestone_stairs_east},
 *       {@code nether_brick_fence} — NOT in the map) carry the pure
 *       hasSolidTop refusal rows.</li>
 *   <li><b>The cactus and trapdoor rows are CONFOUNDED and do not sit in
 *       the batteries</b> — each block's OWN support law removes it in
 *       the seat's stone terrain (the cactus needs sand below and no
 *       solid beside — {@code BlockUpdateSystem.tickCactusSupport}; the
 *       trapdoor's mount walk the F-SP1 law), so the seat would measure
 *       those families' fate, not this port's answer. The PROBE
 *       (the diagnostic run of this pass) verified the port's answers
 *       at the source level instead: {@code FireBlocks.hasSolidTop}
 *       answers TRUE for BOTH ({@code cactus}: the fallthrough —
 *       {@code WorldSolidity.isSolid} TRUE; {@code trapdoor}: the
 *       fallthrough, no carve) where the reference's arm 1 refuses both
 *       (cactus: Material.CACTUS translucent line 30; the trapdoor:
 *       TrapdoorBlock.isCube false line 42) — the same two defects, the
 *       census rows kept, the seats carried by the stable members of
 *       each class (the translucents glass/stained glass/glowstone/ice;
 *       the non-cubes glass_pane/iron_bars).</li>
 * </ul></p>
 */
class FireSolidTopParityDiscoveryTest {

    @TempDir
    Path dataDir;

    /** The row cursor: every row takes fresh coordinates inside one server.
     * The wrap-safe scheme (the F-SP2 rig's x-cycle holds 25 rows — this
     * battery's 26 would reuse row 1's cell at cursor 104, the stone
     * row's still-burning flame then answering the iron_bars row's read
     * through the age-swap guard): when x cycles, z shifts by 8 — the
     * first coordinate collision needs 125 rows. */
    private int cursor = 0;

    private int nextX() {
        cursor += 4;
        return -50 + (cursor % 100);
    }

    private int nextZ() {
        return 60 + ((cursor / 100) * 8 + cursor % 20) % 20;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the fire's placement-time support gate (onAdded lines
    // 318-325; the PlacementListener's consult)
    // ------------------------------------------------------------------

    /**
     * A flame committed over the row's floor survives the placement gate
     * only while the floor answers {@code hasSolidTop || a flammable
     * neighbor}: the translucent non-fuels (glass, stained glass,
     * glowstone, ice) and the second slab family's bottom single REFUSE
     * the flame in vanilla — the engine's fallthrough HOLDS them all; the
     * flammable woods (the leaves, TNT, the bottom OAK stair, the OAK
     * fence) hold in BOTH through the fuel disjunct; the carved
     * non-cubes (the first-family bottom slab, the stone stair and
     * fence members, the walls, the chests, the snow) pop in BOTH.
     */
    @Test
    void theFlamesPlacementGateFollowsTheHasSolidTopLaw() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            flamePlaceRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone is solid-blocking AND a cube — the control");
            flamePlaceRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE (SlabBlock.isCube = isDouble) — the control");
            flamePlaceRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            flamePlaceRow(server, rows, BuiltinBlocks.STONE_SLAB2_TOP, true,
                    "the second family's TOP single answers the SlabBlock HALF==TOP arm — "
                            + "the control (the engine agrees by the fallthrough accident)");
            flamePlaceRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            flamePlaceRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm (World.java line 1750) "
                            + "— the control");
            flamePlaceRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent (Material.PACKED_ICE line 27) and a cube "
                            + "— the control");
            flamePlaceRow(server, rows, BuiltinBlocks.NETHERRACK, true,
                    "netherrack is solid-blocking AND a cube — the control (the eternal row)");
            flamePlaceRow(server, rows, BuiltinBlocks.OAK_LEAVES, true,
                    "leaves are TRANSLUCENT (hasSolidTop refuses) BUT flammable (30/60, "
                            + "FireBlock line 97) — the fuel disjunct holds the flame in BOTH");
            flamePlaceRow(server, rows, BuiltinBlocks.TNT, true,
                    "TNT is TRANSLUCENT BUT flammable (15/100, FireBlock line 100) — the fuel "
                            + "disjunct holds the flame in BOTH");
            flamePlaceRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, true,
                    "the bottom OAK stair fails hasSolidTop in BOTH (the port's stairs arm) "
                            + "BUT is FLAMMABLE (5/20, FireBlock line 84) — the fuel disjunct "
                            + "holds the flame in BOTH (the wood-stair family's row — the "
                            + "first draft's pop expectation was the seat's own find)");
            flamePlaceRow(server, rows, BuiltinBlocks.FENCE, true,
                    "the OAK fence fails hasSolidTop in BOTH (the port's fence carve) BUT is "
                            + "FLAMMABLE (5/20, FireBlock line 85) — the fuel disjunct holds "
                            + "the flame in BOTH");
            flamePlaceRow(server, rows, BuiltinBlocks.COBBLESTONE_STAIRS_EAST, false,
                    "the bottom COBBLESTONE stair fails hasSolidTop in BOTH and is NOT "
                            + "flammable (the stone family — not in the map) — the pure "
                            + "stairs-arm refusal row");
            flamePlaceRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence fails hasSolidTop in BOTH (the port's fence "
                            + "carve) and is NOT flammable — the pure fence refusal row");
            flamePlaceRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP — pops "
                            + "in BOTH (the engine's _slab arm is correct for the FIRST family)");
            flamePlaceRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — pops in BOTH (the port's carve list)");
            flamePlaceRow(server, rows, BuiltinBlocks.CHEST, false,
                    "the chest is a NON-CUBE — pops in BOTH (the port's carve list)");
            flamePlaceRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow: TRANSLUCENT material, the LAYERS==7 arm needs 7 — "
                            + "pops in BOTH");
            flamePlaceRow(server, rows, server.world().airType(), false,
                    "air never answers — pops in BOTH (the bare-cell row)");
            flamePlaceRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT (Material.GLASS line 22) — isSolidBlocking refuses; "
                            + "not flammable — the flame is REFUSED at placement in vanilla");
            flamePlaceRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT (Material.GLASS, Block.java line 1197) — "
                            + "the flame is REFUSED at placement in vanilla");
            flamePlaceRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS (Block.java line 1191) — hasSolidTop "
                            + "REFUSES and fire carries NO glowstone row (the wire's alone, "
                            + "RedstoneWireBlock line 93) — the flame is REFUSED in vanilla");
            flamePlaceRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT (Material.ICE line 26) — the flame is REFUSED in "
                            + "vanilla");
            flamePlaceRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the second slab family's BOTTOM single (stone_slab2 — the red "
                            + "sandstone): the SlabBlock arm requires HALF==TOP — the flame "
                            + "is REFUSED in vanilla (the port's endsWith(\"_slab\") arm "
                            + "misses the \"slab2\" id)");
            flamePlaceRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the glass pane is TRANSLUCENT and a NON-CUBE (PaneBlock.isCube false, "
                            + "line 52) — arm 1 refuses in vanilla; the port's carve list "
                            + "misses it and the fallthrough HOLDS it");
            flamePlaceRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the iron bars are a NON-CUBE (PaneBlock.isCube false) — arm 1 refuses "
                            + "in vanilla; the fallthrough HOLDS them");
            assertVanilla("seat 1 — the fire's placement gate "
                    + "(onAdded lines 318-325: hasSolidTop || flammable neighbor)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 — the fire's neighbor-update survival walk
    // (neighborChanged lines 311-315; the update bus's fire arm)
    // ------------------------------------------------------------------

    /**
     * A flame standing on stone whose floor is REPLACED with the row's
     * block dies on the neighbor update only when the new floor fails
     * BOTH the solid-top and the fuel consults: the translucent
     * non-fuels kill the flame in vanilla — the engine's fallthrough
     * keeps it burning; the flammable woods keep it in BOTH (the fuel
     * arm).
     */
    @Test
    void theFlamesNeighborUpdateWalkFollowsTheHasSolidTopLaw() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            flameSwapRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone stays a solid top — the control (the swap is a no-op for the law)");
            flameSwapRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE — the control");
            flameSwapRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            flameSwapRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            flameSwapRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm — the control");
            flameSwapRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent and a cube — the control");
            flameSwapRow(server, rows, BuiltinBlocks.OAK_LEAVES, true,
                    "leaves are TRANSLUCENT BUT flammable — the fuel disjunct keeps the "
                            + "flame in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.TNT, true,
                    "TNT is TRANSLUCENT BUT flammable — the fuel disjunct keeps the flame "
                            + "in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, true,
                    "the bottom OAK stair fails hasSolidTop in BOTH BUT is FLAMMABLE (5/20) "
                            + "— the fuel disjunct keeps the flame in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.FENCE, true,
                    "the OAK fence fails hasSolidTop in BOTH BUT is FLAMMABLE (5/20) — the "
                            + "fuel disjunct keeps the flame in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.COBBLESTONE_STAIRS_EAST, false,
                    "the bottom COBBLESTONE stair fails both consults in BOTH (not "
                            + "flammable — the stone family) — the flame dies in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.NETHER_BRICK_FENCE, false,
                    "the nether brick fence fails both consults in BOTH — the flame dies in "
                            + "BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab fails both consults — the flame dies in BOTH");
            flameSwapRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall fails both consults — the flame dies in BOTH");
            flameSwapRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow fails both consults — the flame dies in BOTH");
            flameSwapRow(server, rows, server.world().airType(), false,
                    "air fails both consults — the flame dies in BOTH (the support-vanished "
                            + "row, FireBlocksTest's own law)");
            flameSwapRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT and not flammable — neighborChanged REMOVES the "
                            + "flame in vanilla; the engine's fallthrough keeps it burning");
            flameSwapRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT and not flammable — the flame dies in "
                            + "vanilla");
            flameSwapRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS — the flame dies in vanilla (fire carries "
                            + "NO glowstone row)");
            flameSwapRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the flame dies in vanilla");
            flameSwapRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the second family's BOTTOM single fails both consults in vanilla — the "
                            + "port's slab arm misses the id and the fallthrough keeps the "
                            + "flame");
            flameSwapRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the pane is TRANSLUCENT and a NON-CUBE — the flame dies in vanilla; "
                            + "the fallthrough keeps it");
            flameSwapRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the bars are a NON-CUBE — the flame dies in vanilla; the fallthrough "
                            + "keeps it");
            assertVanilla("seat 2 — the fire's neighbor-update walk "
                    + "(neighborChanged lines 311-315)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 — the fire's scheduled tick re-check (tick lines 144-170)
    // ------------------------------------------------------------------

    /**
     * The scheduled fire tick's FIRST act in the reference is the
     * placement re-check (lines 146-148: {@code !canBePlaced ->
     * removeBlock}) — a young flame on a translucent non-fuel floor is
     * removed at its FIRST tick in vanilla. The engine's re-check reads
     * the same defective port: the young flame on glass SURVIVES its
     * ticks (the no-fuel walk's {@code !hasSolidTop || i > 3} never fires
     * while the port answers TRUE) and burns on until the age law alone
     * retires it — the timing divergence. The controls: the same young
     * flame on stone burns in BOTH; the age-4 flame dies at its first
     * tick on BOTH floors (the age law, and the proof the scheduled
     * queue processes glass-floored flames — the young-flame survival is
     * the consult's answer alone).
     */
    @Test
    void theYoungFlameOnATranslucentFloorDiesAtItsOwnFirstTick() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            // The four flames at fresh coordinates, one commit each.
            BlockPosition glassYoung = tickFlame(server, BuiltinBlocks.GLASS, 0);
            BlockPosition stoneYoung = tickFlame(server, BuiltinBlocks.STONE, 0);
            BlockPosition glassOld = tickFlame(server, BuiltinBlocks.GLASS, 4);
            BlockPosition stoneOld = tickFlame(server, BuiltinBlocks.STONE, 4);
            // Past the first scheduled fire tick (30-39 ticks out) with
            // margin — the engine's own tick clock is the honest clock.
            EngineAwaits.awaitTicks(server.world(), 100,
                    "the first fire tick window for all four flames");
            rows.add(new RowOutcome("glass + age 0", false,
                    isFireAt(server, glassYoung),
                    "the young flame on glass: the tick's canBePlaced re-check (line 146) "
                            + "removes it at its FIRST tick in vanilla — the engine's "
                            + "defective port keeps it burning"));
            rows.add(new RowOutcome("stone + age 0", true,
                    isFireAt(server, stoneYoung),
                    "the young flame on stone: canBePlaced answers TRUE through the solid "
                            + "top in BOTH — the control"));
            rows.add(new RowOutcome("glass + age 4", false,
                    isFireAt(server, glassOld),
                    "the old flame on glass: dies in BOTH (vanilla at line 146, the engine "
                            + "at the age law i > 3) — the agreement proving the queue "
                            + "processes glass-floored flames"));
            rows.add(new RowOutcome("stone + age 4", false,
                    isFireAt(server, stoneOld),
                    "the old flame on stone: the no-fuel law (i > 3) dies at the first tick "
                            + "in BOTH — the PerDimensionFire precedent row"));
            assertVanilla("seat 3 — the fire's scheduled tick re-check "
                    + "(tick lines 144-170)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 — the rail's survival walk (AbstractRailBlock
    // .neighborChanged lines 86-102; the update bus's rail arm)
    // ------------------------------------------------------------------

    /**
     * A rail standing on stone whose floor is REPLACED with the row's
     * block survives only while the new floor answers hasSolidTop — NO
     * fuel disjunct rescues anything here: the leaves and TNT that keep
     * the FLAME alive POP the rail in vanilla (the rail's consult is
     * hasSolidTop ALONE) — and the engine's fallthrough holds them too.
     * The cactus row does not sit in this battery (its own support law
     * pops it in the stone terrain — the class Javadoc's confound note;
     * the port's TRUE answer for it is the probe's record).
     */
    @Test
    void theRailsSurvivalWalkFollowsTheHasSolidTopLaw() throws Exception {
        EngineServer server = boot();
        try {
            List<RowOutcome> rows = new ArrayList<>();
            railSwapRow(server, rows, BuiltinBlocks.STONE, true,
                    "stone stays a solid top — the control");
            railSwapRow(server, rows, BuiltinBlocks.DOUBLE_STONE_SLAB, true,
                    "the double slab is a FULL CUBE — the control");
            railSwapRow(server, rows, BuiltinBlocks.STONE_SLAB_TOP, true,
                    "the TOP single slab answers the SlabBlock HALF==TOP arm — the control");
            railSwapRow(server, rows, BuiltinBlocks.STONE_SLAB2_TOP, true,
                    "the second family's TOP single answers the SlabBlock HALF==TOP arm — "
                            + "the control");
            railSwapRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST_TOP, true,
                    "the TOP stair answers the StairsBlock HALF==TOP arm — the control");
            railSwapRow(server, rows, HopperBlocks.hopperOf(0, true), true,
                    "the hopper answers the explicit HopperBlock arm — the control");
            railSwapRow(server, rows, BuiltinBlocks.PACKED_ICE, true,
                    "packed ice is NOT translucent and a cube — the control");
            railSwapRow(server, rows, BuiltinBlocks.STONE_SLAB, false,
                    "the BOTTOM single slab: the SlabBlock arm requires HALF==TOP — the "
                            + "rail pops in BOTH (with its item drop)");
            railSwapRow(server, rows, BuiltinBlocks.OAK_STAIRS_EAST, false,
                    "the BOTTOM stair: the StairsBlock arm requires HALF==TOP — the rail "
                            + "pops in BOTH");
            railSwapRow(server, rows, BuiltinBlocks.FENCE, false,
                    "the fence is a NON-CUBE — the rail pops in BOTH");
            railSwapRow(server, rows, WallBlocks.state(0), false,
                    "the cobblestone wall is a NON-CUBE — the rail pops in BOTH");
            railSwapRow(server, rows, BuiltinBlocks.SNOW_LAYER, false,
                    "the 1-layer snow — the rail pops in BOTH");
            railSwapRow(server, rows, server.world().airType(), false,
                    "air — the rail pops in BOTH (the floor-vanished row)");
            railSwapRow(server, rows, BuiltinBlocks.GLASS, false,
                    "glass is TRANSLUCENT — the rail pops in vanilla (dropItems + "
                            + "removeBlock, lines 104-106); the engine's fallthrough keeps "
                            + "it standing");
            railSwapRow(server, rows, stainedGlass(), false,
                    "stained glass is TRANSLUCENT — the rail pops in vanilla");
            railSwapRow(server, rows, BuiltinBlocks.GLOWSTONE, false,
                    "glowstone's material is GLASS — the rail pops in vanilla");
            railSwapRow(server, rows, BuiltinBlocks.ICE, false,
                    "ice is TRANSLUCENT — the rail pops in vanilla");
            railSwapRow(server, rows, BuiltinBlocks.OAK_LEAVES, false,
                    "leaves are TRANSLUCENT — the RAIL's consult has NO fuel disjunct: the "
                            + "rail pops in vanilla while the FLAME on the same floor "
                            + "stayed (the family discrimination)");
            railSwapRow(server, rows, BuiltinBlocks.TNT, false,
                    "TNT is TRANSLUCENT — the rail pops in vanilla (the flame's fuel arm "
                            + "is the FIRE consults' alone)");
            railSwapRow(server, rows, BuiltinBlocks.STONE_SLAB2, false,
                    "the second family's BOTTOM single: the SlabBlock arm requires "
                            + "HALF==TOP — the rail pops in vanilla (the port's suffix arm "
                            + "misses the id)");
            railSwapRow(server, rows, BuiltinBlocks.GLASS_PANE, false,
                    "the pane is TRANSLUCENT and a NON-CUBE — the rail pops in vanilla; "
                            + "the fallthrough keeps it");
            railSwapRow(server, rows, BuiltinBlocks.IRON_BARS, false,
                    "the bars are a NON-CUBE — the rail pops in vanilla; the fallthrough "
                            + "keeps it");
            assertVanilla("seat 4 — the rail's survival walk "
                    + "(AbstractRailBlock.neighborChanged lines 86-102)", rows);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The row drivers (the FloorConsultParityDiscoveryTest rig)
    // ------------------------------------------------------------------

    /**
     * One placement-gate row: the row's floor is laid, the flame is
     * committed above it (the onAdded gate answers INSIDE the commit),
     * the settle window passes, and the row records whether the flame
     * stayed. The first scheduled fire tick lands 30-39 ticks out —
     * far beyond the window — so the read is the placement gate alone.
     */
    private void flamePlaceRow(EngineServer server, List<RowOutcome> rows, BlockType floor,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int z = nextZ();
        int y = 40;
        BlockPosition floorCell = new BlockPosition(x, y, z);
        BlockPosition flameCell = new BlockPosition(x, y + 1, z);
        server.ticker().submit(() -> {
            server.world().setBlock(floorCell, floor);
            server.world().setBlock(flameCell, FireBlocks.stateOf(0));
        });
        Thread.sleep(150);
        boolean engineHolds = isFireAt(server, flameCell);
        rows.add(new RowOutcome(floor.identifier().value(), vanillaHolds, engineHolds, law));
    }

    /**
     * One neighbor-update row: the flame is committed over a VALID stone
     * floor (the placement gate passes, the first tick armed), the floor
     * is then REPLACED with the row's block — the swap's six-neighbor
     * dispatch wakes the flame cell's fire arm synchronously — and the
     * row records whether the flame survived the walk.
     */
    private void flameSwapRow(EngineServer server, List<RowOutcome> rows, BlockType floor,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int z = nextZ();
        int y = 40;
        BlockPosition floorCell = new BlockPosition(x, y, z);
        BlockPosition flameCell = new BlockPosition(x, y + 1, z);
        // 1. the valid pair: the stone floor, then the flame (the onAdded
        //    gate passes on stone).
        server.ticker().submit(() -> {
            server.world().setBlock(floorCell, BuiltinBlocks.STONE);
            server.world().setBlock(flameCell, FireBlocks.stateOf(0));
        });
        assertTrue(isFireAt(server, flameCell),
                "the flame landed on the stone floor (the row's precondition)");
        // 2. the floor swap — the flame's walk consults the new floor.
        server.ticker().submit(() -> server.world().setBlock(floorCell, floor));
        // 3. the settle window, then the read.
        Thread.sleep(150);
        boolean engineHolds = isFireAt(server, flameCell);
        rows.add(new RowOutcome(floor.identifier().value(), vanillaHolds, engineHolds, law));
    }

    /**
     * One tick row: the row's floor is laid and the flame of the given
     * age committed above it; the caller awaits the tick window and
     * reads the cell. Answers the flame's cell for the later read.
     */
    private BlockPosition tickFlame(EngineServer server, BlockType floor, int age) {
        int x = nextX();
        int z = nextZ();
        int y = 40;
        BlockPosition floorCell = new BlockPosition(x, y, z);
        BlockPosition flameCell = new BlockPosition(x, y + 1, z);
        server.ticker().submit(() -> {
            server.world().setBlock(floorCell, floor);
            server.world().setBlock(flameCell, FireBlocks.stateOf(age));
        });
        return flameCell;
    }

    /**
     * One rail row: the rail is placed over a VALID stone floor (the
     * default flat NORTH_SOUTH state, the placement site's own commit
     * order — updateShape first), the floor is then REPLACED with the
     * row's block — the swap's dispatch wakes the rail cell's rail arm
     * (RailConnections.survivesAt) — and the row records whether the
     * rail survived (a refusal pops it with its item drop).
     */
    private void railSwapRow(EngineServer server, List<RowOutcome> rows, BlockType floor,
            boolean vanillaHolds, String law) throws Exception {
        int x = nextX();
        int z = nextZ();
        int y = 40;
        BlockPosition floorCell = new BlockPosition(x, y, z);
        BlockPosition railCell = new BlockPosition(x, y + 1, z);
        // 1. the valid pair: the stone floor, then the rail (the
        //    placement site's own order — the connection walk after the
        //    commit).
        server.ticker().submit(() -> {
            server.world().setBlock(floorCell, BuiltinBlocks.STONE);
            server.world().setBlock(railCell, BuiltinBlocks.RAIL);
            RailConnections.updateShape(server.world(), railCell, true);
        });
        assertEquals(BuiltinBlocks.RAIL, typeAt(server, railCell),
                "the rail landed on the stone floor (the row's precondition)");
        // 2. the floor swap — the rail arm consults the new floor.
        server.ticker().submit(() -> server.world().setBlock(floorCell, floor));
        // 3. the settle window, then the read.
        Thread.sleep(150);
        boolean engineHolds = BuiltinBlocks.RAIL.equals(typeAt(server, railCell));
        rows.add(new RowOutcome(floor.identifier().value(), vanillaHolds, engineHolds, law));
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
    // Harness (the FloorConsultParityDiscoveryTest shape)
    // ------------------------------------------------------------------

    private boolean isFireAt(EngineServer server, BlockPosition pos) {
        BlockType type = typeAt(server, pos);
        return type != null && FireBlocks.isFire(type.identifier());
    }

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
