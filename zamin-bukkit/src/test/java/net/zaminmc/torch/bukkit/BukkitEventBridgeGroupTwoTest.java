package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.piston.PistonBlocks;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The event bridge's second group over the live engine (§28.7's inventory:
 * the seven constructible families of this slice, every one verified against
 * the pinned API's own constructor shapes): PlayerDamage (cancellable both
 * ways + the mutable amount both ways), PlayerDeath (the message applied +
 * the legacy keepInventory arm onto the native cancel), FluidFlow
 * (cancellable both ways through the world state itself), PistonMove (both
 * the extend and retract forms), SignChange (cancellable + the rewrite
 * applied), the furnace pair (the ignite observation + the smelt
 * cancellation), and the honest-gap tripwires — the entity-backed families
 * the adapter does not model views for stay UNBRIDGED, and the tripwires
 * prove no fake bridge ever fired while the engine's own surfaces worked.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Order(6)
class BukkitEventBridgeGroupTwoTest {

    private static BukkitTestHarness harness() throws Exception {
        BukkitTestHarness harness = BukkitTestHarness.harness();
        harness.ensureProbeEnabled(); // the class's entry state (order-independent)
        return harness;
    }

    @Test
    @Order(1)
    void thePlayerDamageBridgeCarriesCauseAmountAndCancelsBothWays() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession tank = joinPlaying(harness, "TankProbe");
        setSurvival(harness, tank);
        // The cause translation: the engine's semantic entry (MELEE) is the
        // 1.8.8 ENTITY_ATTACK, and the mutable amount rides both ways.
        harness.server.damage(tank, 3.0f);
        harness.await(() -> harness.probeLogContains(
                "event:damage player=TankProbe cause=ENTITY_ATTACK amount=3.0"),
                "the bridged damage event carried the cause and the amount");
        harness.await(() -> tank.health() == 19.0f,
                "the probe's setDamage(1.0) mapped back onto the engine's amount");
        assertTrue(harness.probeLogContains("modify:damage-amount"),
                "the probe's rewrite arm ran");
        assertTrue(harness.probeLogContains("event:damage player=TankProbe"
                        + " cause=ENTITY_ATTACK amount=3.0 primary=true"),
                "the dispatch ran on the owner context (isPrimaryThread)");
        // The cancellable-both-ways arm: the legacy cancel maps back onto
        // the engine's own cancellable damage event.
        harness.server.damage(tank, 20.0f);
        harness.await(() -> harness.probeLogContains("cancel:damage"),
                "the legacy listener cancelled the big hit");
        EngineAwaits.awaitTicks(harness.server.world(), 6, "the cancelled hit certainly settled");
        assertEquals(19.0f, tank.health(), 1e-6f,
                "the cancelled damage landed nothing (the mapped-back cancel)");
    }

    @Test
    @Order(2)
    void thePlayerDeathBridgeAppliesTheMessageAndTheKeepInventoryArm() throws Exception {
        BukkitTestHarness harness = harness();
        PlayerSession keeper = joinPlaying(harness, "KeeperDeath");
        setSurvival(harness, keeper);
        giveStone(harness, keeper, 10);
        // The native MONITOR observer: what the engine's own later handlers
        // see after the bridge mapped the legacy mutations back.
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<Boolean> cancelled = new AtomicReference<>();
        EventSubscription monitor = harness.server.events().on(
                net.zaminmc.torch.event.player.PlayerDeathEvent.class,
                net.zaminmc.torch.event.EventPriority.MONITOR,
                event -> {
                    message.set(event.deathMessage());
                    cancelled.set(event.cancelled());
                });
        harness.server.damage(keeper, 1000.0f);
        harness.await(() -> keeper.dead(), "the body died");
        assertNotNull(message.get(), "the native MONITOR observed the death");
        assertTrue(message.get().endsWith(" [bridged]"),
                "the legacy setDeathMessage mapped back onto the engine's"
                        + " mutable broadcast line: " + message.get());
        assertEquals(Boolean.TRUE, cancelled.get(),
                "the legacy setKeepInventory(true) mapped onto the native cancel arm");
        monitor.unsubscribe();
        assertTrue(harness.probeLogContains("event:death player=KeeperDeath"),
                "the legacy death observation dispatched");
        assertTrue(harness.probeLogContains("event:death-keep"),
                "the probe's keepInventory arm ran");
        assertTrue(harness.probeLogContains("drops=0"),
                "the drops view is the honest empty shim (no inventory view modeled)");
        assertTrue(keeper.countOf(BuiltinItems.lookup(
                        Identifier.parse("minecraft:stone")).orElseThrow()) >= 10,
                "the mapped-back cancel kept the inventory on the body");
    }

    @Test
    @Order(3)
    void theFluidFlowBridgeCancelsBothWaysThroughTheWorldState() throws Exception {
        BukkitTestHarness harness = harness();
        BlockPosition pour = new BlockPosition(30, 40, 30);
        BlockPosition below = new BlockPosition(30, 39, 30);
        BlockPosition sideways = new BlockPosition(31, 40, 30);
        // The bucket's own pour state (the reference's own shape: a directly
        // placed SOURCE stays inert until a neighbor changes).
        harness.server.ticker().submit(() -> harness.server.world().setBlock(pour,
                FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0)));
        harness.await(() -> harness.probeLogContains("event:fromto from=(30,40,30)"),
                "the bridged flow events dispatched");
        harness.await(() -> harness.probeLogContains("cancel:flow-below"),
                "the legacy listener cancelled the down-pour");
        // The blocked-ground law made visible: the cancelled down-pour never
        // lands (the cell below stays dry), and the source falls back to its
        // sideways spread (the neighbor takes the water).
        harness.await(() -> FluidBlocks.isFluid(blockType(harness, sideways).identifier()),
                "the sideways spread landed (the cancelled cell behaved as blocked ground)");
        EngineAwaits.awaitTicks(harness.server.world(), 6,
                "the whole settle cycle, then the standing assertion");
        assertFalse(FluidBlocks.isFluid(blockType(harness, below).identifier()),
                "the legacy cancel kept the below cell dry");
        assertTrue(harness.probeLogContains("cancel:flow-below"),
                "the legacy listener's cancel arm ran");
        assertTrue(harness.probeLogContains(
                "event:fromto from=(30,40,30) to=(30,39,30) toType=AIR"),
                "the legacy event's own block view named the dry air cell");
    }

    @Test
    @Order(4)
    void thePistonBridgeObservesBothFormsWithFacesAndChains() throws Exception {
        BukkitTestHarness harness = harness();
        // PART 1 — the extend with a push chain (moved=1): the sticky piston
        // pushes the stone ahead of it, the legacy form carrying the
        // engine's own facing and the resolver's chain. (The pushed
        // stone's flight arms the engine's own re-check storm afterward —
        // the observation's cells are done once the extend landed.)
        BlockPosition piston = new BlockPosition(20, 40, 20);
        BlockPosition stone = new BlockPosition(21, 40, 20);
        BlockPosition lever = new BlockPosition(20, 41, 20);
        harness.server.ticker().submit(() -> {
            harness.server.world().setBlock(piston, PistonBlocks.pistonOf(5, false, true));
            harness.server.world().setBlock(stone, BuiltinBlocks.STONE);
            // The core suite's own lever shape (up_z — mounted on the block
            // below, the piston itself: solid, no pop).
            harness.server.world().setBlock(lever, RedstoneBlocks.leverOf(5, false));
        });
        harness.await(() -> PistonBlocks.isPiston(blockType(harness, piston)),
                "the sticky piston placed");
        harness.server.ticker().submit(() -> harness.server.redstone().useLever(lever));
        harness.await(() -> harness.probeLogContains(
                "event:piston-extend at=(20,40,20) face=EAST moved=1"),
                "the extend form dispatched with the face and the chain");
        harness.await(() -> PistonBlocks.pistonExtended(blockType(harness, piston)),
                "the piston extended (the observation never cancels)");
        // PART 2 — the retract with a pull chain (moved=1), the core suite's
        // own arrangement: a FRESH sticky piston extended against AIR, the
        // pull target placed directly beyond the settled head (no flight
        // storm — the retract arms only at the lever's own OFF).
        BlockPosition piston2 = new BlockPosition(20, 40, 23);
        BlockPosition lever2 = new BlockPosition(20, 41, 23);
        BlockPosition pullTarget = new BlockPosition(22, 40, 23);
        harness.server.ticker().submit(() -> {
            harness.server.world().setBlock(piston2, PistonBlocks.pistonOf(5, false, true));
            harness.server.world().setBlock(lever2, RedstoneBlocks.leverOf(5, false));
        });
        harness.await(() -> PistonBlocks.isPiston(blockType(harness, piston2)),
                "the second sticky piston placed");
        harness.server.ticker().submit(() -> harness.server.redstone().useLever(lever2));
        harness.await(() -> PistonBlocks.pistonExtended(blockType(harness, piston2)),
                "the second piston extended (against air)");
        harness.await(() -> PistonBlocks.isPistonHead(blockType(harness,
                new BlockPosition(21, 40, 23))), "the second head landed ahead");
        harness.server.ticker().submit(() ->
                harness.server.world().setBlock(pullTarget, BuiltinBlocks.STONE));
        harness.await(() -> "minecraft:stone".equals(blockId(harness, pullTarget)),
                "the pull target placed one cell beyond the head");
        harness.server.ticker().submit(() -> harness.server.redstone().useLever(lever2));
        // The retract form carries the engine's own face and the
        // movedCells AS THE ENGINE ANSWERS THEM — the resolver now reads
        // the post-departure world (the retract arm removes the head
        // BEFORE its own resolution runs, the reference's move lines
        // 288-290, so the gate's fresh resolution mirrors it): the pull
        // chain resolves as the event's javadoc intends, the stone's
        // cell leading the moved list (the engine-side retract-event
        // follow-up the bukkit slice-2 wave recorded and pinned — the
        // legacy form carries that row through 1:1). The sticky PULL
        // itself lands as before: the retract arm's own direct read of
        // the block beyond the head moves the stone home.
        harness.await(() -> harness.probeLogContains(
                "event:piston-retract at=(20,40,23) face=EAST moved=1"),
                "the retract form dispatched with the engine's own face"
                + " and the resolved pull chain");
        harness.await(() -> !PistonBlocks.pistonExtended(blockType(harness, piston2)),
                "the piston retracted");
        harness.await(() -> "minecraft:stone".equals(blockId(harness,
                new BlockPosition(21, 40, 23))),
                "the sticky pull brought the stone home");
    }

    @Test
    @Order(5)
    void theSignChangeBridgeCancelsAndAppliesTheRewrite() throws Exception {
        BukkitTestHarness harness = harness();
        // The sign stands next to the spawn (the engine's own anti-grief
        // reach guard: the editor must be within 8 blocks of the sign).
        BlockPosition sign = new BlockPosition(1, 5, 1);
        PlayerSession editor = joinPlaying(harness, "SignEditor");
        harness.server.ticker().submit(() -> {
            harness.server.world().setBlock(sign, BuiltinBlocks.SIGN_SOUTH);
            harness.server.signs().set(sign, new String[] {"Old", "Text", "Stands", ""});
        });
        harness.await(() -> harness.server.signs().peek(sign) != null, "the sign staged");
        // The rewrite arm: the legacy setLine rows map onto the native
        // event's own rewrite (the walk applies the final array).
        harness.server.editSign(editor, sign,
                new String[] {"plain", "second", "third", "fourth"});
        harness.await(() -> harness.probeLogContains(
                "event:signchange editor=SignEditor line0=plain primary=true"),
                "the bridged sign edit dispatched");
        harness.await(() -> "PLAIN".equals(harness.server.signs().peek(sign)[0]),
                "the legacy rewrite applied through the native arm");
        // The cancellation: the old text stands.
        harness.server.editSign(editor, sign,
                new String[] {"secret", "no", "dice", "here"});
        harness.await(() -> harness.probeLogContains("cancel:sign"),
                "the legacy listener cancelled the edit");
        assertEquals("PLAIN", harness.server.signs().peek(sign)[0],
                "the cancelled edit kept the old line");
        assertEquals("second", harness.server.signs().peek(sign)[1],
                "the untouched lines carried");
    }

    @Test
    @Order(6)
    void theFurnacePairBridgesWithTheCancellation() throws Exception {
        BukkitTestHarness harness = harness();
        BlockPosition burnAt = new BlockPosition(50, 40, 50);
        BlockPosition smeltAt = new BlockPosition(50, 40, 52);
        BlockPosition goldAt = new BlockPosition(50, 40, 54);
        setCell(harness, burnAt, "minecraft:furnace");
        setCell(harness, smeltAt, "minecraft:furnace");
        setCell(harness, goldAt, "minecraft:furnace");
        // The ignite observation: the fuel's own burn value rides (one stick
        // = 100 ticks, the engine's own table).
        loadFurnace(harness, burnAt, "minecraft:iron_ore", "minecraft:stick");
        harness.await(() -> harness.probeLogContains(
                "event:burn at=(50,40,50) fuel=STICK ticks=100"),
                "the bridged ignite carried the fuel view and the burn value");
        // The smelt commit: the input/result views ride, the commit lands
        // (the 200-tick cook cycle needs the long await).
        loadFurnace(harness, smeltAt, "minecraft:iron_ore", "minecraft:coal");
        awaitLong(() -> harness.probeLogContains(
                "event:smelt at=(50,40,52) source=IRON_ORE result=IRON_INGOT"),
                "the bridged smelt carried the input and result views");
        awaitLong(() -> {
            FurnaceBlockEntity furnace = harness.server.furnaceManager().at(smeltAt);
            return furnace != null && !furnace.output().isEmpty()
                    && furnace.output().type().identifier().value().equals("iron_ingot");
        }, "the uncancelled smelt committed the ingot");
        // The cancelled twin: the gold smelt refuses — the output stays
        // empty while the fuel keeps burning (the engine's own law).
        loadFurnace(harness, goldAt, "minecraft:gold_ore", "minecraft:coal");
        awaitLong(() -> harness.probeLogContains(
                "event:smelt at=(50,40,54) source=GOLD_ORE result=GOLD_INGOT"),
                "the gold smelt event dispatched");
        harness.await(() -> harness.probeLogContains("cancel:smelt-gold"),
                "the legacy listener cancelled the gold smelt");
        awaitLong(() -> {
            FurnaceBlockEntity furnace = harness.server.furnaceManager().at(goldAt);
            return furnace == null || furnace.output().isEmpty();
        }, "the cancelled smelt never commits an output");
    }

    @Test
    @Order(7)
    void theEntityBackedFamiliesNowBridgeThroughTheViews() throws Exception {
        BukkitTestHarness harness = harness();
        // The slice-3 flip: the entity-adapter family landed, and the five
        // families the slice-2 record demand-named now dispatch their
        // legacy forms exactly when the engine's own surfaces run (the
        // tripwire pattern kept, positive form — the probe's listeners
        // for every family fire, and the engine's own spawn/damage/drop/
        // launch/boat verdicts all still answer honestly).
        var pig = harness.server.mobs().spawnAt(MobType.PIG, new Position(60, 40, 60));
        assertNotNull(pig, "the pig spawned through the engine's own surface");
        harness.await(() -> harness.probeLogContains(
                "event:spawn type=PIG reason=DEFAULT at=(60.0,40.0,60.0)"),
                "the wrapped pending view carried the spawn facts");
        float full = pig.health();
        assertTrue(harness.server.mobs().hurt(pig, 2.0f, 0.0),
                "the mob damage landed through the engine's own surface");
        harness.await(() -> harness.probeLogContains(
                "event:mobdamage type=PIG cause=ENTITY_ATTACK amount=2.0"),
                "the wrapped live view carried the damage facts");
        assertEquals(full - 2.0f, pig.health(), 1e-6f);
        harness.server.ticker().submit(() -> harness.server.itemEntities().spawnDropAtBlock(
                new Position(62, 40, 60),
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.DIAMOND, 1), 10));
        harness.await(() -> harness.probeLogContains(
                "event:itemspawn item=DIAMOND amount=1"),
                "the wrapped pending item view carried the stack facts");
        assertNotNull(harness.server.projectiles().launch(
                net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.ARROW,
                -1, new Position(64, 40, 60), 0.0, 0.0, 1.5, 0),
                "the arrow launched through the engine's own surface");
        harness.await(() -> harness.probeLogContains(
                "event:launch type=ARROW shooter=none"),
                "the wrapped pending projectile view carried the launch facts");
        assertNotNull(harness.server.vehicles().spawnBoat(new Position(66, 40, 60), 0),
                "the boat spawned through the engine's own surface");
        harness.await(() -> harness.probeLogContains(
                "event:vehicle type=BOAT at=(66.0,40.0,60.0)"),
                "the wrapped pending vehicle view carried the spawn facts");
        // The cancelled twin — the diamond is withheld through the engine's
        // own gate (the legacy cancel mapped back), so the drop returns
        // null and no body lands.
        harness.await(() -> harness.probeLogContains("cancel:itemspawn"),
                "the legacy listener's cancel arm ran");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * The full join walk (the core suites' own shape): the engine's
     * joinRequest PLUS the wire-handshake completion — the damage, sign
     * and death families' own state gate (PLAYING) needs it, and the
     * harness's plain join stops at the ACCEPTED window (the slice-1
     * tests' chat/break arms never needed the state).
     */
    private static PlayerSession joinPlaying(BukkitTestHarness harness, String name)
            throws InterruptedException {
        PlayerSession session = harness.join(name);
        harness.server.joinCompleted(session); // the wire's handshake completes in the harness
        harness.await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the session reaches PLAYING (the state gate's own law)");
        return session;
    }

    private static void setSurvival(BukkitTestHarness harness, PlayerSession session)
            throws InterruptedException {
        harness.server.ticker().submit(() ->
                session.setGamemode(GameMode.SURVIVAL));
        harness.await(() -> session.gamemode() == GameMode.SURVIVAL,
                "the session reached survival (the damage family's own gate)");
    }

    private static void giveStone(BukkitTestHarness harness, PlayerSession session, int count)
            throws InterruptedException {
        harness.server.ticker().submit(() -> session.give(
                net.zaminmc.torch.item.ItemStack.of(BuiltinItems.lookup(
                        Identifier.parse("minecraft:stone")).orElseThrow(), count)));
        harness.await(() -> session.countOf(BuiltinItems.lookup(
                        Identifier.parse("minecraft:stone")).orElseThrow()) >= count,
                "the items are on the body");
    }

    private static void setCell(BukkitTestHarness harness, BlockPosition at, String id)
            throws InterruptedException {
        var type = harness.server.blocks().byKey(id).orElseThrow();
        harness.server.ticker().submit(() ->
                harness.server.world().setBlock(at, type));
        harness.await(() -> id.equals(blockId(harness, at)), "the cell is " + id);
    }

    private static String blockId(BukkitTestHarness harness, BlockPosition at) {
        var type = blockType(harness, at);
        return type == null ? null : type.identifier().toString();
    }

    private static net.zaminmc.torch.block.BlockType blockType(
            BukkitTestHarness harness, BlockPosition at) {
        return harness.server.world().getBlock(at);
    }

    private static void loadFurnace(BukkitTestHarness harness, BlockPosition at,
            String inputId, String fuelId) throws InterruptedException {
        harness.server.ticker().submit(() -> {
            FurnaceBlockEntity furnace = harness.server.furnaceManager().getOrCreate(at);
            furnace.quickMoveIn(net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.lookup(Identifier.parse(inputId)).orElseThrow(), 1));
            furnace.quickMoveIn(net.zaminmc.torch.item.ItemStack.of(
                    BuiltinItems.lookup(Identifier.parse(fuelId)).orElseThrow(), 1));
        });
        harness.await(() -> {
            FurnaceBlockEntity furnace = harness.server.furnaceManager().at(at);
            return furnace != null && !furnace.input().isEmpty();
        }, "the furnace loaded (" + inputId + " + " + fuelId
                + ") — the INPUT is the stable load signal (the fuel may"
                + " already have ignited and been consumed, the core suite's"
                + " own law)");
    }

    /** The longer await (the smelt cycle is 200 real-time ticks). */
    private static void awaitLong(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 40_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private static int countOf(List<String> lines, String needle) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }
}
