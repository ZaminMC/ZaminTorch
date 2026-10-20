package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.block.FluidFlowEvent;
import net.zaminmc.torch.event.block.PistonMoveEvent;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §27.7 interaction-events wave's piston + fluid arms (Slice 81):
 * the public pre-commit gates over the engine's own walks. The
 * PistonMoveEvent fires inside the movement's boundary scope with the
 * resolver's own prospective lists — cancelling prevents the whole
 * movement (the piston keeps its state, nothing moves or breaks, no
 * sound). The FluidFlowEvent fires per prospective landing cell —
 * cancelling behaves as blocked ground (the fluid never enters, the
 * walk's own rules continue around it).
 */
class PistonFluidEventAcceptanceTest {

    /** The reference direction id for EAST (the tests push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    @Test
    void theCancelledExtendPreventsTheWholeMovement() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");

            // The plugin arm: cancel every extend of THIS piston.
            EventSubscription sub = server.events().on(PistonMoveEvent.class, event -> {
                if (event.position().equals(new BlockPosition(x, y, z)) && event.extending()) {
                    event.cancel();
                }
            });

            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            Thread.sleep(400); // let the cancelled event land

            assertTrue(PistonBlocks.isPiston(typeAt(server, x, y, z))
                            && !PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the cancelled extend left the piston retracted");
            assertTrue(typeAt(server, x + 1, y, z).identifier().value().equals("stone"),
                    "the stone never moved");
            sub.unsubscribe();

            // Without the handler the lever cycle extends it: the lever is
            // still ON from the first flip, so walk it OFF then ON — the
            // re-armed extend goes through.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            Thread.sleep(200); // the OFF flip settles
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the extend goes through after unsubscribe");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theExtendObservationCarriesTheResolverLists() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, false));
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the piston placed");

            CopyOnWriteArrayList<PistonMoveEvent> seen = new CopyOnWriteArrayList<>();
            EventSubscription sub = server.events().on(PistonMoveEvent.class, seen::add);

            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> seen.stream().anyMatch(PistonMoveEvent::extending),
                    "the extend observation arrived");

            PistonMoveEvent extend = seen.stream()
                    .filter(PistonMoveEvent::extending).findFirst().orElseThrow();
            assertEquals(server.world().name(), extend.worldName());
            assertEquals(new BlockPosition(x, y, z), extend.position());
            assertEquals(net.zaminmc.torch.block.Direction.EAST, extend.facing());
            assertFalse(extend.sticky(), "a plain piston");
            assertTrue(extend.movedCells().contains(new BlockPosition(x + 1, y, z)),
                    "the push chain carries the stone's cell");
            sub.unsubscribe();
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCancelledRetractKeepsTheHeadAndThePulledBlock() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        PistonBlocks.pistonOf(EAST, false, true));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.leverOf(5, false));
            });
            await(() -> PistonBlocks.isPiston(typeAt(server, x, y, z)), "the sticky piston placed");
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            await(() -> PistonBlocks.pistonExtended(typeAt(server, x, y, z)),
                    "the piston extended");
            await(() -> PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the head landed ahead");
            // The block the sticky head will pull back sits one cell
            // beyond the head.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x + 2, y, z), BuiltinBlocks.STONE));
            await(() -> typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pull target placed");

            // The plugin arm: cancel every retract. The vanilla arming
            // already flipped the BASE to its retracted state before the
            // event ran — the observable of the cancel is that the HEAD
            // never departs and nothing gets pulled.
            EventSubscription sub = server.events().on(PistonMoveEvent.class, event -> {
                if (!event.extending()) {
                    event.cancel();
                }
            });

            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x, y + 1, z)));
            Thread.sleep(400);

            assertTrue(PistonBlocks.isPistonHead(typeAt(server, x + 1, y, z)),
                    "the cancelled retract left the head in place");
            assertTrue(typeAt(server, x + 2, y, z).identifier().value().equals("stone"),
                    "the pulled block never moved");
            sub.unsubscribe();
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theCancelledFlowBehavesAsBlockedGround() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            BlockPosition source = new BlockPosition(x, y, z);
            BlockPosition below = new BlockPosition(x, y - 1, z);
            // The bucket's own pour state (the Wave VI fluids migration:
            // BucketItem.place writes the FLOWING form at LEVEL 0 — the
            // reference's own pour; a directly-placed SOURCE is the
            // settled still form and stays inert until a neighbor
            // changes, exactly the reference's /setblock shape).
            server.ticker().submit(() -> server.world().setBlock(source,
                    FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0)));
            await(() -> FluidBlocks.isFluid(typeAt(server, x, y, z).identifier()),
                    "the water pour placed");

            // The plugin arm: cancel every flow into the cell below the
            // source — the down-pour is blocked, so the source must fall
            // back to its own sideways spread (the blocked-ground law).
            EventSubscription sub = server.events().on(FluidFlowEvent.class, event -> {
                if (event.to().equals(below)) {
                    event.cancel();
                }
            });

            await(() -> FluidBlocks.isFluid(typeAt(server, x, y - 1, z).identifier()) == false
                            && FluidBlocks.isFluid(typeAt(server, x + 1, y, z).identifier()),
                    "the blocked down-pour let the source spread sideways instead");

            AtomicBoolean sawTheCancelledTarget = new AtomicBoolean(false);
            EventSubscription witness = server.events().on(FluidFlowEvent.class, event -> {
                if (event.to().equals(below)) {
                    sawTheCancelledTarget.set(true);
                }
            });
            // The settle cycle (the 1:1 model's own rhythm): the source's
            // next flowing pass re-fires the cancelled down-pour (the
            // still pool re-awakens through the sideways write's neighbor
            // update) before both cells settle — the witness must span
            // that whole cycle.
            Thread.sleep(400);
            assertTrue(sawTheCancelledTarget.get(),
                    "the flow toward the cancelled cell was observed");
            sub.unsubscribe();
            witness.unsubscribe();

            // Without the handler the next wake pours down — and under the
            // 1:1 model the settled pool needs a NEIGHBOR CHANGE to re-arm
            // (the reference's own shape: the still source is inert until
            // one of its neighbors commits — LiquidSourceBlock's
            // neighborChanged → convertToFlowing). A stone placed beside
            // the pool is that wake.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x - 1, y, z), BuiltinBlocks.STONE));
            await(() -> FluidBlocks.isFluid(typeAt(server, x, y - 1, z).identifier()),
                    "the down-pour goes through on the next wake after unsubscribe");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFlowObservationCarriesTheFluidAndItsLevel() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            // The bucket's own pour state (the flowing form at LEVEL 0 —
            // the reference's pour; its tick settles the source and
            // carries the spread's first pass).
            server.ticker().submit(() -> server.world().setBlock(new BlockPosition(x, y, z),
                    FluidBlocks.typeOf(FluidBlocks.Kind.WATER, FluidBlocks.Form.FLOWING, 0)));
            await(() -> FluidBlocks.isFluid(typeAt(server, x, y, z).identifier()),
                    "the water pour placed");

            CopyOnWriteArrayList<FluidFlowEvent> seen = new CopyOnWriteArrayList<>();
            EventSubscription sub = server.events().on(FluidFlowEvent.class, seen::add);
            await(() -> !seen.isEmpty(), "at least one flow observed");

            FluidFlowEvent first = seen.get(0);
            assertEquals(server.world().name(), first.worldName());
            assertEquals("minecraft:water", first.fluid());
            assertEquals(8, first.level(), "the first flow of a source is the down-pour");
            assertEquals(new BlockPosition(x, y - 1, z), first.to());
            sub.unsubscribe();
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pfe" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
