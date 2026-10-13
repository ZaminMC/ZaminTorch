package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.container.Containers;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.brewing.BrewingStandBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The brewing stand's public API surface (§27.3's container-family wave,
 * the former NOT AUDITED row): the position-keyed lookup through
 * {@link World#containers()} — the live 4-slot view (the three potion
 * bottles + the ingredient), the brew-timer read, the §27.6 owner-context
 * write with the reference's own slot gates (isItemAllowed — the vanilla
 * refusal a plugin must learn), and the diagnosable off-lane violation.
 */
class BrewingStandApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bsa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    /**
     * The stand placed with the public block write + the entity behind it
     * (the engine's own lazy materialization — the reference's
     * getOrCreate path, the same setup the chest/furnace/hopper API
     * suites ride).
     */
    private BlockPosition placeStand() {
        BlockPosition at = new BlockPosition(24, 40, 24);
        server.ticker().submit(() -> {
            server.world().setBlock(at, BrewingStandBlocks.of(false, false, false));
            server.brewingStands().getOrCreate(at);
        });
        return at;
    }

    private void awaitStand(BlockPosition at) throws InterruptedException {
        await(() -> server.brewingStands().peek(at) != null,
                "the stand's state materialized");
    }

    @Test
    void theStandAnswersThePublicLookupWithItsLiveView() throws Exception {
        BlockPosition at = placeStand();
        awaitStand(at);
        // The stand's state materializes on the first consult (the engine's
        // own getOrCreate path rides the hopper lookups + the open path —
        // here the API read itself materializes it through the view).
        World world = server.world();
        Optional<Containers.BrewingStand> stand = world.containers().brewingStandAt(at);
        assertTrue(stand.isPresent(), "the placed stand answers the lookup");
        Containers.BrewingStand view = stand.orElseThrow();
        assertEquals(at, view.position(), "the stand's own position");
        assertEquals(4, view.slotCount(), "the vanilla stand's four slots");
        assertTrue(view.ingredient().isEmpty(), "the fresh stand's ingredient slot");
        for (int slot = 0; slot < 4; slot++) {
            assertTrue(view.slot(slot).isEmpty(), "the fresh stand's slots are empty");
        }
        // The miss arms: no stand at the empty position, out-of-range slot.
        assertTrue(world.containers().brewingStandAt(new BlockPosition(0, 90, 0)).isEmpty(),
                "no stand at the empty position");
        assertTrue(view.slot(99).isEmpty(), "the out-of-range slot answers empty");

        // The foreign-thread lookup (the best-effort contract, §27.6).
        AtomicReference<Optional<Containers.BrewingStand>> foreign =
                new AtomicReference<>(Optional.empty());
        Thread offLane = new Thread(() -> foreign.set(world.containers().brewingStandAt(at)));
        offLane.start();
        offLane.join();
        assertTrue(foreign.get().isPresent(), "the off-lane lookup finds the stand");
        assertEquals(4, foreign.get().orElseThrow().slotCount());
    }

    @Test
    void theOwnerContextWritesLandWithTheReferenceSlotGates() throws Exception {
        BlockPosition at = placeStand();
        awaitStand(at);
        World world = server.world();
        Optional<Containers.BrewingStand> stand = world.containers().brewingStandAt(at);
        assertTrue(stand.isPresent(), "the placed stand answers the lookup");
        Containers.BrewingStand view = stand.orElseThrow();

        // The off-lane write first: the diagnosable §27.6 violation.
        AtomicReference<Throwable> caught = new AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                view.setSlot(0, new ItemStack(BuiltinItems.POTION, 1, 0));
            } catch (Throwable t) {
                caught.set(t);
            }
        });
        stranger.start();
        stranger.join();
        assertInstanceOf(IllegalStateException.class, caught.get(),
                "the off-lane stand write fails diagnosably");

        // The on-lane writes: three water bottles + the nether wart.
        server.world().execute(() -> {
            for (int slot = 0; slot < 3; slot++) {
                view.setSlot(slot, new ItemStack(BuiltinItems.POTION, 1, 0));
            }
            view.setSlot(3, ItemStack.of(BuiltinItems.NETHER_WART, 2));
        });
        await(() -> !view.slot(0).isEmpty(), "the bottle write landed");
        assertEquals(BuiltinItems.POTION, view.slot(0).type(), "the water bottle");
        assertEquals(0, view.slot(0).damage(), "the plain water bottle's damage");
        assertEquals(BuiltinItems.NETHER_WART, view.ingredient().type(),
                "the ingredient slot reads back");
        assertEquals(2, view.ingredient().count(), "the ingredient's count");

        // The slots() copy: a consistent multi-slot read, isolated from
        // the engine's own array.
        ItemStack[] slots = view.slots();
        assertEquals(4, slots.length, "the four slots in the copy");
        assertEquals(BuiltinItems.POTION, slots[0].type());
        assertEquals(BuiltinItems.NETHER_WART, slots[3].type());
        slots[0] = ItemStack.EMPTY;
        assertFalse(view.slot(0).isEmpty(), "the copy is isolated from the live state");

        // The reference's slot gates (isItemAllowed): a diamond refuses
        // the potion slot, a potion refuses the ingredient slot.
        AtomicReference<Throwable> potionRefused = new AtomicReference<>();
        server.world().execute(() -> {
            try {
                view.setSlot(0, ItemStack.of(BuiltinItems.DIAMOND, 1));
            } catch (Throwable t) {
                potionRefused.set(t);
            }
        });
        await(() -> potionRefused.get() != null, "the diamond's refusal landed");
        assertInstanceOf(IllegalArgumentException.class, potionRefused.get(),
                "the reference's isItemAllowed gate answers the illegal write");
        AtomicReference<Throwable> ingredientRefused = new AtomicReference<>();
        server.world().execute(() -> {
            try {
                view.setSlot(3, new ItemStack(BuiltinItems.POTION, 1, 0));
            } catch (Throwable t) {
                ingredientRefused.set(t);
            }
        });
        await(() -> ingredientRefused.get() != null, "the potion's refusal landed");
        assertInstanceOf(IllegalArgumentException.class, ingredientRefused.get(),
                "the ingredient slot refuses the non-ingredient");
        // The empty stack clears (the empty write never gates).
        server.world().execute(() -> view.setSlot(3, ItemStack.EMPTY));
        await(() -> view.ingredient().isEmpty(), "the empty write cleared the ingredient");
    }

    @Test
    void theBrewCycleRidesThePublicTimerRead() throws Exception {
        BlockPosition at = placeStand();
        awaitStand(at);
        World world = server.world();
        Optional<Containers.BrewingStand> stand = world.containers().brewingStandAt(at);
        assertTrue(stand.isPresent(), "the placed stand answers the lookup");
        Containers.BrewingStand view = stand.orElseThrow();

        // Arm the cycle through the public write (the §27.6 owner path).
        server.world().execute(() -> {
            for (int slot = 0; slot < 3; slot++) {
                view.setSlot(slot, new ItemStack(BuiltinItems.POTION, 1, 0));
            }
            view.setSlot(3, ItemStack.of(BuiltinItems.NETHER_WART, 1));
        });
        await(() -> view.brewTicksRemaining() > 300,
                "the brew armed the 400-tick window (read through the API)");
        assertTrue(view.brewTicksRemaining() <= 400,
                "the timer stays inside the reference's 400-tick cycle");

        // The cancellation gate through the public write: the ingredient
        // leaves, the cycle cancels (the reference's first gate).
        server.world().execute(() -> view.setSlot(3, ItemStack.EMPTY));
        await(() -> view.brewTicksRemaining() == 0,
                "the missing ingredient cancelled the cycle");
    }
}
