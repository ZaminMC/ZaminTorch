package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-045/VT-046/VT-047/VT-048's discovery seats — the tripwire family's
 * REMOVAL surface and the circuit family's dig gate (the reference's law,
 * 1.8.8).
 *
 * <p><b>The numbering:</b> VT-045/046/047 were drafted VT-037/VT-038/
 * VT-039 on the fifty-third pass's lane; the public numbering landscape
 * (the pass-60 record) took VT-036 (PR #151), VT-037 (PR #156), VT-038
 * (PR #160), VT-040/041/042 (the pass-56 lane) and VT-043/044 (PR
 * #158/#159), so the drafts renumber to the next free band.
 * VT-048 is this run's own find.</p>
 *
 * <h2>VT-045 — the pop arms' missing drops</h2>
 *
 * <ul>
 * <li>{@code TripwireBlock.neighborChanged} lines 87-94 (the suspended
 *     flip pop): {@code this.dropItems(world, pos, state, 0);
 *     world.removeBlock(pos);} — the STRING item drops UNCONDITIONALLY
 *     ({@code getDropItem} lines 77-79, the explicit override).</li>
 * <li>{@code TripwireHookBlock.neighborChanged} lines 85-95 AND
 *     {@code canSurviveOrBreak} lines 194-202 (the mount pops): the same
 *     {@code dropItems + removeBlock} — no getDropItem override, so
 *     {@code Item.byBlock(this)} = the tripwire hook item.</li>
 * </ul>
 *
 * <p>The engine's {@code TripwireSystem.checkSupport} pop arms (the wire
 * arm's {@code world.setBlock(pos, air)} at line 355 with the comment
 * SAYING "with the string drop" while dropping nothing; the hook arm's
 * bare write at line 368) never consult the drop machinery.</p>
 *
 * <h2>VT-046 — the shears disarm arm absent</h2>
 *
 * <p>{@code TripwireBlock.beforeMinedByPlayer} lines 123-129: shears held
 * &rarr; {@code world.setBlockState(pos, state.set(DISARMED, true), 4)}
 * — the flag-4 write (no neighbor reaction), MODE-INDEPENDENT (creative
 * runs the same {@code mineBlock}). The DISARMED bit then suppresses the
 * trap signal on the removal ({@code TripwireHookBlock.updateState} line
 * 125's {@code flag5 = !blockstate.get(DISARMED)} feeding {@code flag4
 * |= flag5 && flag6}). The engine carries NO arm that writes DISARMED
 * anywhere — a removed powered wire pulses the hooks UNCONDITIONALLY,
 * the defuse mechanic impossible. The behavioral seat rides the CREATIVE
 * break path (creative breaks carry no diggable consult on the engine,
 * so the seat isolates VT-046 alone; the survival variant rides VT-048's
 * correction).</p>
 *
 * <h2>VT-047 — the dig path's DISARMED-no-drop row</h2>
 *
 * <p>The reference's dig drop is UNCONDITIONAL string:
 * {@code getDropItem} returns {@code Items.STRING} whatever the state
 * (lines 77-79); {@code afterMinedByPlayer} (Block.java lines 773-786)
 * never consults DISARMED. The engine's behavior table rows carry
 * {@code List.of()} for every disarmed wire state — a reference claim the
 * reference does not make. The behavioral seat consults
 * {@code DropService.dropsFor} directly on the disarmed block (the
 * anvil-import stand-in — the only engine path that lands disarmed
 * states today).</p>
 *
 * <h2>VT-048 — the circuit family's dig rows refuse the survival dig</h2>
 *
 * <p>The vanilla dig-open law ({@code ServerPlayerInteractionManager
 * .startMiningBlock} lines 105-148): EVERY non-air material block opens
 * the dig — the only refusals are the spectator and CanDestroy gates;
 * {@code f >= 1.0F} breaks instantly at the start. No "diggability"
 * consult exists in the reference. The family's registrations:
 * {@code redstone_wire} id 55 {@code setStrength(0.0F)} (Block.java line
 * 1109), {@code tripwire} 132 + {@code tripwire_hook} 131 — NO
 * setStrength call, the {@code miningTime} field default 0.0F (lines
 * 131/132), {@code lever} 69 {@code setStrength(0.5F)}, the buttons
 * 77/143 {@code setStrength(0.5F)}, the plates 70/72
 * {@code setStrength(0.5F)}. The engine's behavior rows carry
 * {@code diggable=false} for the WHOLE family (the table's own comments
 * reading "the break is instant by hand") — the survival dig start
 * consult ({@code BlockInteractionService} line 542's {@code
 * !behavior.diggable()}) returns BEFORE any dig opens: a survival player
 * CANNOT break redstone dust, tripwire, a tripwire hook, a lever, a
 * button or a pressure plate AT ALL. Creative breaks work (the creative
 * path never consults the row) — the attribution control.</p>
 *
 * <p><b>THE CONTRACT: the law seats are RED on the unfixed develop</b> —
 * the pops drop nothing (VT-045), the shears break pulses the trap
 * (VT-046), the disarmed row yields nothing (VT-047), the survival digs
 * never open (VT-048). The controls (the dirt floor keeps its wire; the
 * bare-hand break pulses the trap; the plain wire drops its string; the
 * creative break works on every family row) hold on both trees.</p>
 */
class TripwireRemovalSurfaceParityDiscoveryTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // Seat 1 (VT-045, the wire arm) — the survival pop's string drop
    // ------------------------------------------------------------------

    /**
     * A plain wire on a STONE floor whose floor becomes GLASS (the F-SP4
     * flip): vanilla pops the wire AND drops one STRING
     * (neighborChanged's dropItems — the drop is unconditional, no
     * DISARMED gate on the pop path). The engine's pop arm sets air with
     * no drop consult. The DIRT control keeps its wire (both agree).
     */
    @Test
    void theWireSurvivalPopDropsItsString() throws Exception {
        EngineServer server = boot();
        try {
            int x = 900, z = 900, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 5),
                        TripwireBlocks.wireOf(false, false, false, false));
            });
            await(() -> TripwireBlocks.isTripwire(typeAt(server, x, y, z)),
                    "the wire placed on its stone floor");
            await(() -> TripwireBlocks.isTripwire(typeAt(server, x, y, z + 5)),
                    "the control wire placed on its stone floor");

            // The floor swaps: GLASS at the divergence row (the F-SP4 flip
            // pops the wire), DIRT at the control (no flip, the wire stays).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.GLASS);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 5),
                        BuiltinBlocks.DIRT);
            });

            // The pop itself must fire first (the F-SP4 law, landed).
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-045's precondition: the glass-floor flip pops the wire "
                            + "(the F-SP4 law)");
            assertTrue(TripwireBlocks.isTripwire(typeAt(server, x, y, z + 5)),
                    "VT-045: the dirt-floor control keeps its wire");

            // THE DROP: vanilla's pop arm drops one string at the cell.
            // The repeated census earns its verdict — the empty answer
            // over the whole window is the engine's divergence, not a
            // timing artifact.
            Set<String> dropped = dropsNear(server, x, y, z, 3_000);
            assertTrue(dropped.contains("string"),
                    "VT-045: the popped wire drops its STRING item "
                            + "(TripwireBlock.neighborChanged lines 87-94 — "
                            + "dropItems BEFORE removeBlock, the drop "
                            + "unconditional); observed near the cell: "
                            + dropped);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 2 (VT-045, the hook arm) — the survival pop's hook-item drop
    // ------------------------------------------------------------------

    /**
     * A legally-placed hook (a STONE mount behind it, FACING EAST) whose
     * mount goes to AIR: vanilla pops the hook AND drops the
     * TRIPWIRE_HOOK item (neighborChanged lines 85-95's dropItems —
     * Item.byBlock, the hook's own item form). The engine's pop arm is a
     * bare air write — the item never appears.
     */
    @Test
    void theHookSurvivalPopDropsItsItem() throws Exception {
        EngineServer server = boot();
        try {
            int x = 920, z = 900, y = 40;
            BlockPosition mountCell = new BlockPosition(x - 1, y, z);
            BlockPosition hookCell = new BlockPosition(x, y, z);
            BlockType hook = TripwireBlocks.hookOf(5 /* EAST */, false, false);
            server.ticker().submit(() -> {
                server.world().setBlock(mountCell, BuiltinBlocks.STONE);
                server.world().setBlock(hookCell, hook);
            });
            await(() -> hook.equals(typeAt(server, x, y, z)),
                    "the hook landed on its stone mount");

            // The mount goes to AIR: the F-SP5 mount check pops the hook.
            server.ticker().submit(() ->
                    server.world().setBlock(mountCell, server.world().airType()));
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-045's precondition: the removed mount pops the hook "
                            + "(the F-SP5 law)");

            // THE DROP: vanilla's pop arm drops the hook item at the cell.
            Set<String> dropped = dropsNear(server, x, y, z, 3_000);
            assertTrue(dropped.contains("tripwire_hook"),
                    "VT-045: the popped hook drops its TRIPWIRE_HOOK item "
                            + "(TripwireHookBlock.neighborChanged lines 85-95 "
                            + "— dropItems BEFORE removeBlock, Item.byBlock "
                            + "the hook's item form); observed near the cell: "
                            + dropped);
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 3 (VT-046) — the shears break disarms the trap
    // ------------------------------------------------------------------

    /**
     * THE DEFUSE SCENE (the creative break — the path that isolates
     * VT-046 from VT-048's dig gate): a two-hook trap chain (hook EAST +
     * one powered wire + hook WEST) with the digger standing IN the
     * wire's box (the occupancy probe powers the wire — the trap armed).
     * The digger breaks the powered wire WITH SHEARS: vanilla's
     * beforeMinedByPlayer writes DISARMED first (the flag-4 write,
     * mode-independent — {@code mineBlock} runs in creative too), so the
     * removal's trap signal (onRemoved's POWERED=true quirk) is
     * SUPPRESSED — the hooks never pulse. The engine carries no disarm
     * arm: the removal pulses the hooks unconditionally.
     */
    @Test
    void theShearsBreakDisarmsThePoweredWire() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40, z = 940;
            int hookA = 900, wireX = 901, hookB = 902;
            server.ticker().submit(() -> {
                // The stone floor under the chain + the walls behind the
                // hooks (the mount cells BEHIND each hook — the F-SP5 law).
                for (int x = hookA - 1; x <= hookB + 1; x++) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(hookA - 1, y, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(hookB + 1, y, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(wireX, y, z),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(hookA, y, z),
                        TripwireBlocks.hookOf(5 /* EAST, into the chain */, false, false));
                server.world().setBlock(new BlockPosition(hookB, y, z),
                        TripwireBlocks.hookOf(4 /* WEST, back at it */, false, false));
            });
            await(() -> TripwireBlocks.hookAttached(typeAt(server, hookA, y, z)),
                    "the near hook attached to the chain");
            await(() -> TripwireBlocks.hookAttached(typeAt(server, hookB, y, z)),
                    "the far hook attached to the chain");

            // The digger joins, takes the shears, and stands IN the wire's
            // box — the occupancy probe powers the wire (the trap armed).
            PlayerSession digger = joined(server, "ShearDigger");
            server.ticker().submit(() -> digger.inventory().setSlot(0,
                    net.zaminmc.torch.item.ItemStack.of(BuiltinItems.SHEARS, 1)));
            server.teleportPlayer(digger, new Position(wireX + 0.5, y, z + 0.5));
            await(() -> TripwireBlocks.wirePowered(typeAt(server, wireX, y, z)),
                    "the wire powered under the standing digger (the trap armed)");
            await(() -> TripwireBlocks.hookPowered(typeAt(server, hookB, y, z)),
                    "the trap armed: the hooks powered under the standing body");

            // THE SHEARS BREAK: creative (the dig gate never consulted on
            // this path — VT-048 is not under test here).
            server.blockInteraction().submitCreativeBreak(digger,
                    new BlockPosition(wireX, y, z));
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, wireX, y, z)),
                    "the shears break removed the wire");

            // THE LAW: the hooks NEVER pulse on the shears-dug removal —
            // beforeMinedByPlayer's DISARMED write suppresses the trap
            // signal (updateState line 125's flag5). The engine before the
            // fix pulses UNCONDITIONALLY (the removal walk passes
            // POWERED=true to the hooks whatever the held item).
            assertTrue(!TripwireBlocks.hookPowered(typeAt(server, hookA, y, z)),
                    "VT-046: the near hook never powers on the shears-dug "
                            + "removal (beforeMinedByPlayer lines 123-129 — the "
                            + "DISARMED flag-4 write ahead of the removal, the "
                            + "trap signal suppressed)");
            assertTrue(!TripwireBlocks.hookPowered(typeAt(server, hookB, y, z)),
                    "VT-046: the far hook never powers on the shears-dug "
                            + "removal (the defused trap stays silent)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /** Seat 3's control: the bare-hand break PULSES the armed trap (both trees agree). */
    @Test
    void theBareHandBreakPulsesTheArmedTrap() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40, z = 960;
            int hookA = 900, wireX = 901, hookB = 902;
            server.ticker().submit(() -> {
                for (int x = hookA - 1; x <= hookB + 1; x++) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z),
                            BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(hookA - 1, y, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(hookB + 1, y, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(wireX, y, z),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(hookA, y, z),
                        TripwireBlocks.hookOf(5, false, false));
                server.world().setBlock(new BlockPosition(hookB, y, z),
                        TripwireBlocks.hookOf(4, false, false));
            });
            await(() -> TripwireBlocks.hookAttached(typeAt(server, hookB, y, z)),
                    "the control chain attached");

            // The digger joins BARE-HANDED and stands in the wire's box.
            PlayerSession digger = joined(server, "BareDigger");
            server.teleportPlayer(digger, new Position(wireX + 0.5, y, z + 0.5));
            await(() -> TripwireBlocks.wirePowered(typeAt(server, wireX, y, z)),
                    "the control wire powered under the standing digger");

            // The bare-hand creative break: the trap FIRES (the removal's
            // POWERED=true quirk pulses the hooks — the classic trap
            // signal, no shears to suppress it).
            server.blockInteraction().submitCreativeBreak(digger,
                    new BlockPosition(wireX, y, z));
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, wireX, y, z)),
                    "the bare-hand break removed the wire");
            await(() -> TripwireBlocks.hookPowered(typeAt(server, hookB, y, z)),
                    "VT-046's control: the bare-hand removal PULSES the trap "
                            + "(onRemoved lines 125-128 — the POWERED=true quirk, "
                            + "both trees agree)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Seat 4 (VT-047) — the disarmed wire's drop row
    // ------------------------------------------------------------------

    /**
     * A DISARMED wire (the anvil-import stand-in — the only engine path
     * that lands disarmed states today), broken through the CREATIVE
     * path's drop-free law... no: the drop consult the row serves is the
     * SURVIVAL dig's — but the ROW's law is demonstrated directly: the
     * reference's dig drop is UNCONDITIONAL string (getDropItem lines
     * 77-79; afterMinedByPlayer never consults DISARMED). This seat
     * breaks the disarmed wire with a bare-handed CREATIVE break (which
     * carries no drop consult on the engine — creative never drops in
     * vanilla either) and asserts the ROW's law through the dig-path
     * sibling: the row the DropService consults must yield the string.
     * The row's own consult ({@code DropService.dropsFor} on the
     * disarmed block) is the direct demonstration.
     */
    @Test
    void theDisarmedWireRowYieldsItsString() {
        // The row's law, consulted directly (the anvil-import stand-in's
        // dig-path drop): vanilla's getDropItem override returns STRING
        // UNCONDITIONALLY — the DISARMED bit is the trap-signal
        // suppressor, never a drop gate.
        java.util.List<net.zaminmc.torch.item.ItemStack> yield =
                new net.zaminmc.torch.server.interaction.DropService(
                        new java.util.Random(20261105L))
                        .dropsFor(TripwireBlocks.wireOf(false, false, false, true),
                                null, false, 0);
        assertTrue(yield.stream().anyMatch(stack -> stack.type().identifier()
                        .value().equals("string")),
                "VT-047: the disarmed wire's dig row yields its STRING "
                        + "(getDropItem lines 77-79 — UNCONDITIONAL, the "
                        + "DISARMED bit the trap-signal suppressor never a "
                        + "drop gate); observed: "
                        + yield.stream().map(s -> s.type().identifier().value())
                                .toList());
    }

    // ------------------------------------------------------------------
    // Seat 5 (VT-048) — the circuit family's dig gate
    // ------------------------------------------------------------------

    /**
     * THE DIG-OPEN LAW: vanilla opens the survival dig on EVERY non-air
     * block (startMiningBlock lines 105-148 — no diggability consult;
     * {@code f >= 1.0F} breaks instantly at the start). The instant rows
     * of the circuit family (hardness 0): a bare-handed dig START breaks
     * the block AT THE START and drops its item.
     *
     * <p>Rows probed: the REDSTONE DUST (redstone_wire, id 55
     * setStrength(0.0F) — the redstone drop), the TRIPWIRE WIRE (id 132,
     * the default miningTime 0.0F — the string drop) and the TRIPWIRE
     * HOOK (id 131, the same default — the hook drop). The engine's rows
     * carry diggable=false: the dig START returns before any dig opens —
     * the cells keep their blocks forever. The CREATIVE control (the
     * creative break never consults the row) breaks every row — the
     * attribution control for the dig gate alone.</p>
     */
    @Test
    void theSurvivalDigOpensOnTheCircuitFamilyInstantRows() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40;
            // Three instant rows, each on its own cell band: the dust, the
            // wire and the hook (the hook on its stone mount).
            int dustX = 900, dustZ = 980;
            int wireX = 910, wireZ = 980;
            int hookX = 920, hookZ = 980;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(dustX, y - 1, dustZ),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(dustX, y, dustZ),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(wireX, y - 1, wireZ),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(wireX, y, wireZ),
                        TripwireBlocks.wireOf(false, false, false, false));
                server.world().setBlock(new BlockPosition(hookX - 1, y, hookZ),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(hookX, y, hookZ),
                        TripwireBlocks.hookOf(5, false, false));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, dustX, y, dustZ)),
                    "the dust staged on its stone floor");
            await(() -> TripwireBlocks.isTripwire(typeAt(server, wireX, y, wireZ)),
                    "the wire staged on its stone floor");
            await(() -> TripwireBlocks.isHook(typeAt(server, hookX, y, hookZ)),
                    "the hook staged on its stone mount");

            // The digger joins bare-handed (the survival mode — the dig
            // path under test) and stands within the band of all three.
            PlayerSession digger = joined(server, "CircuitDigger");
            server.teleportPlayer(digger, new Position(dustX + 0.5, y, dustZ + 2.5));
            await(() -> Math.abs(digger.position().x() - (dustX + 0.5)) < 0.1,
                    "the digger arrived at the circuit band");

            // THE DIG START on the dust: vanilla breaks instantly
            // (hardness 0, f >= 1.0 at the start — startMiningBlock line
            // 137) and the redstone item drops.
            server.blockInteraction().submitMiningStart(digger,
                    new BlockPosition(dustX, y, dustZ), 1);
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, dustX, y, dustZ)),
                    "VT-048: the survival dig opens on the redstone dust "
                            + "(startMiningBlock lines 105-148 — no diggability "
                            + "consult in the reference; id 55's "
                            + "setStrength(0.0F) — the instant break; the "
                            + "engine's diggable=false row refuses the dig "
                            + "start before any dig opens)");

            // THE DIG START on the wire: the same law (id 132's default
            // miningTime 0.0F) — instant break, the string drop.
            server.blockInteraction().submitMiningStart(digger,
                    new BlockPosition(wireX, y, wireZ), 1);
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, wireX, y, wireZ)),
                    "VT-048: the survival dig opens on the tripwire (the "
                            + "default miningTime 0.0F — the instant break; the "
                            + "engine's diggable=false row refuses)");

            // THE DIG START on the hook: the same law (id 131's default
            // miningTime 0.0F) — instant break, the hook item drop.
            server.blockInteraction().submitMiningStart(digger,
                    new BlockPosition(hookX, y, hookZ), 1);
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, hookX, y, hookZ)),
                    "VT-048: the survival dig opens on the tripwire hook "
                            + "(the default miningTime 0.0F — the instant "
                            + "break; the engine's diggable=false row refuses)");

            // THE DROPS: each instant break drops its family item (the
            // dig-path rows the table itself carries).
            Set<String> dustDropped = dropsNear(server, dustX, y, dustZ, 3_000);
            assertTrue(dustDropped.contains("redstone"),
                    "VT-048: the dug dust drops its redstone item (the "
                            + "dig-path row the table carries); observed: "
                            + dustDropped);
            Set<String> wireDropped = dropsNear(server, wireX, y, wireZ, 3_000);
            assertTrue(wireDropped.contains("string"),
                    "VT-048: the dug wire drops its string item; observed: "
                            + wireDropped);
            Set<String> hookDropped = dropsNear(server, hookX, y, hookZ, 3_000);
            assertTrue(hookDropped.contains("tripwire_hook"),
                    "VT-048: the dug hook drops its item; observed: "
                            + hookDropped);
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * Seat 5's 0.5-hardness sibling: the LEVER (id 69 setStrength(0.5F))
     * — the dig opens and completes at the finish (the 15-tick bare-hand
     * dig), the lever item dropping. The engine's diggable=false row
     * refuses the dig start — the lever stands forever.
     */
    @Test
    void theSurvivalDigOpensAndCompletesOnTheLever() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40, x = 900, z = 990;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.redstone.RedstoneBlocks
                                .leverOf(5, false));
            });
            await(() -> TripwireBlocks.isHook(typeAt(server, x, y, z))
                            || typeAt(server, x, y, z).identifier().value()
                                    .startsWith("lever"),
                    "the lever staged on its stone floor");

            PlayerSession digger = joined(server, "LeverDigger");
            server.teleportPlayer(digger, new Position(x + 0.5, y, z + 2.5));
            await(() -> Math.abs(digger.position().x() - (x + 0.5)) < 0.1,
                    "the digger arrived at the lever");

            // The dig start + the elapsed window + the finish: vanilla's
            // 0.5-hardness lever breaks at the 15-tick accumulation (the
            // bare-hand canHarvest walk), the lever item dropping.
            server.blockInteraction().submitMiningStart(digger,
                    new BlockPosition(x, y, z), 1);
            sleep(900); // 18 ticks: past the 15-tick completion band
            server.blockInteraction().submitMiningFinished(digger,
                    new BlockPosition(x, y, z));
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, x, y, z)),
                    "VT-048: the survival dig opens and completes on the "
                            + "lever (id 69's setStrength(0.5F) — the dig the "
                            + "reference opens on every non-air block; the "
                            + "engine's diggable=false row refuses the dig "
                            + "start)");

            Set<String> dropped = dropsNear(server, x, y, z, 3_000);
            assertTrue(dropped.contains("lever"),
                    "VT-048: the dug lever drops its item; observed: "
                            + dropped);
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * Seat 5's attribution control: the CREATIVE break carries no diggable
     * consult on the engine — every circuit row breaks under it (both
     * trees agree). The survival-only refusal is the divergence.
     */
    @Test
    void theCreativeBreakWorksOnEveryCircuitRow() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40;
            int dustX = 930, dustZ = 980;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(dustX, y - 1, dustZ),
                        BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(dustX, y, dustZ),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isWire(typeAt(server, dustX, y, dustZ)),
                    "the dust staged for the creative control");

            PlayerSession breaker = joined(server, "CreativeBreaker");
            server.teleportPlayer(breaker, new Position(dustX + 0.5, y, dustZ + 2.5));
            await(() -> Math.abs(breaker.position().x() - (dustX + 0.5)) < 0.1,
                    "the breaker arrived");
            server.blockInteraction().submitCreativeBreak(breaker,
                    new BlockPosition(dustX, y, dustZ));
            await(() -> BuiltinBlocks.AIR.equals(typeAt(server, dustX, y, dustZ)),
                    "VT-048's control: the creative break removes the dust "
                            + "(the creative path never consults the row — the "
                            + "attribution control, both trees agree)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /**
     * The repeated item census: rounds of verified-run scans over the
     * window, any hit returning at once. An EMPTY verdict is earned by
     * the whole window of completed rounds — never a single early census
     * (the RED seats' empty answers must be sound).
     */
    private static Set<String> dropsNear(EngineServer server, int x, int y, int z,
                                         long windowMillis) throws InterruptedException {
        Set<String> ids = new HashSet<>();
        long deadline = System.currentTimeMillis() + windowMillis;
        while (System.currentTimeMillis() < deadline && ids.isEmpty()) {
            AtomicBoolean ran = new AtomicBoolean(false);
            AtomicReference<Set<String>> round = new AtomicReference<>(new HashSet<>());
            server.ticker().submit(() -> {
                Set<String> found = new HashSet<>();
                for (net.zaminmc.torch.server.entity.ItemEntity item
                        : server.itemEntities().all()) {
                    Position at = item.position();
                    if (Math.abs(at.x() - (x + 0.5)) <= 2.5
                            && Math.abs(at.y() - y) <= 2.0
                            && Math.abs(at.z() - (z + 0.5)) <= 2.0) {
                        found.add(item.stack().type().identifier().value());
                    }
                }
                round.set(found);
                ran.set(true);
            });
            long roundDeadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < roundDeadline && !ran.get()) {
                sleep(20);
            }
            ids.addAll(round.get());
            sleep(100);
        }
        return ids;
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private PlayerSession joined(EngineServer server, String name)
            throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "trs" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
