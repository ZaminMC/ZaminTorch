package net.zaminmc.torch.server.armorstand;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The armor stand family on the live engine (Slice 28c) — the reference
 * port of entity/living/ArmorStandEntity + item/ArmorStandItem: the
 * placement gates (the DOWN face, the cell + headroom, the entity box,
 * the 45-degree yaw snap, the pose roll), the equipment swap ladder
 * (the armor item's own slot, the empty hand's height bands, the
 * creative copy, the single swap, the big-stack carve, the occupied
 * refusal), the melee break ladder (the wobble, the double-hit break
 * with the drops, the creative instant remove), and the ZAD persistence
 * round trip.
 */
class ArmorStandSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The placement (ArmorStandItem.useOn lines 22-68)
    // ------------------------------------------------------------------

    @Test
    void thePlacementGatesAndPoseRollWalk() throws Exception {
        EngineServer server = boot();
        try {
            // A floor with open headroom: (10,40,10) air on stone.
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");

            // The DOWN face refuses outright.
            AtomicReference<Boolean> down = new AtomicReference<>();
            server.ticker().submit(() -> down.set(server.armorStands().place(
                    new BlockPosition(10, 40, 10), 0, 0.0f, new java.util.Random(1))));
            await(server, () -> down.get() != null, "the DOWN face answered");
            assertFalse(down.get(), "the DOWN face never places");

            // The UP face lands the stand on top of the floor block.
            AtomicReference<Boolean> up = new AtomicReference<>();
            server.ticker().submit(() -> up.set(server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 10.0f, new java.util.Random(1))));
            await(server, () -> up.get() != null, "the UP face answered");
            assertTrue(up.get(), "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            assertNotNull(stand, "the stand stands at (10.5, 40, 10.5)");
            assertEquals(10.5, stand.x(), 1e-9);
            assertEquals(40.0, stand.y(), 1e-9);
            assertEquals(10.5, stand.z(), 1e-9);
            // The yaw snap: playerYaw 10 - 180 = -170 -> wrapDegrees ->
            // 190 -> -170; floor((-170 + 22.5)/45)*45 = floor(-3.27)*45 =
            // -4*45 = -180.
            assertEquals(-180.0f, stand.yaw(), 1e-6, "the 45-degree snap");
            // The pose roll (the seeded random's head pitch +0-5, the
            // body yaw +-5) — the defaults shift.
            assertTrue(stand.head().pitch() >= 0.0f && stand.head().pitch() < 5.0f,
                    "the head pose rolled inside the reference's band");
            assertTrue(stand.body().yaw() > -5.0f && stand.body().yaw() < 5.0f,
                    "the body pose rolled inside the reference's band");

            // A second stand never lands in the same box (the entity gate).
            AtomicReference<Boolean> again = new AtomicReference<>();
            server.ticker().submit(() -> again.set(server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 0.0f, new java.util.Random(2))));
            await(server, () -> again.get() != null, "the second attempt answered");
            assertFalse(again.get(), "the occupied box refused the second stand");

            // No headroom: the cell above solid refuses — the stand
            // attempts the cell whose ceiling is blocked.
            placeBlock(server, 10, 45, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 45, 10))
                    .identifier().value().equals("stone"), "the ceiling placed");
            AtomicReference<Boolean> crushed = new AtomicReference<>();
            server.ticker().submit(() -> crushed.set(server.armorStands().place(
                    new BlockPosition(10, 44, 10), 1, 0.0f, new java.util.Random(3))));
            await(server, () -> crushed.get() != null, "the crushed attempt answered");
            assertFalse(crushed.get(), "the missing headroom refused the stand");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The equipment swap ladder (interactAt lines 273-342 + swapItem)
    // ------------------------------------------------------------------

    @Test
    void theEquipmentSwapLadderWalks() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 0.0f, new java.util.Random(1)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            List<ItemStack> hand = new ArrayList<>();

            // The armor item's own slot: a helmet claims the head.
            hand.clear();
            hand.add(ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
            AtomicReference<Boolean> helm = new AtomicReference<>();
            server.ticker().submit(() -> helm.set(server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack))));
            await(server, () -> helm.get() != null, "the helmet swap answered");
            assertTrue(helm.get());
            assertEquals(BuiltinItems.DIAMOND_HELMET, stand.equipment(4).type(),
                    "the helmet wears the head slot");
            assertTrue(hand.get(0).isEmpty(), "the single unit swapped fully");

            // The boots claim the feet slot.
            hand.clear();
            hand.add(ItemStack.of(BuiltinItems.IRON_BOOTS, 1));
            AtomicReference<Boolean> boots = new AtomicReference<>();
            server.ticker().submit(() -> boots.set(server.armorStands().interactAt(
                    stand, hand.get(0), 0.05, false, stack -> hand.set(0, stack))));
            await(server, () -> boots.get() != null, "the boots swap answered");
            assertTrue(boots.get());
            assertEquals(BuiltinItems.IRON_BOOTS, stand.equipment(1).type(),
                    "the boots wear the feet slot");

            // The empty hand at the head height takes the helmet back.
            hand.clear();
            hand.add(ItemStack.EMPTY);
            AtomicReference<Boolean> take = new AtomicReference<>();
            server.ticker().submit(() -> take.set(server.armorStands().interactAt(
                    stand, ItemStack.EMPTY, 1.7, false, stack -> hand.set(0, stack))));
            await(server, () -> take.get() != null, "the take answered");
            assertTrue(take.get());
            assertEquals(BuiltinItems.DIAMOND_HELMET, hand.get(0).type(),
                    "the empty hand took the helmet");
            assertTrue(stand.equipment(4).isEmpty(), "the head slot emptied");

            // The big stack carves one unit onto a bare slot (the pumpkin
            // stacks to 64 and claims the head — the armor's own max-1
            // never reaches the carve arm).
            hand.clear();
            hand.add(ItemStack.of(BuiltinItems.PUMPKIN, 3));
            AtomicReference<Boolean> carve = new AtomicReference<>();
            server.ticker().submit(() -> carve.set(server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack))));
            await(server, () -> carve.get() != null, "the carve answered");
            assertTrue(carve.get());
            assertEquals(1, stand.equipment(4).count(), "the carved unit wears the head");
            assertEquals(2, hand.get(0).count(), "the hand kept the rest");

            // The big stack onto an occupied slot never lands.
            hand.clear();
            hand.add(ItemStack.of(BuiltinItems.LIT_PUMPKIN, 5));
            AtomicReference<Boolean> occupied = new AtomicReference<>();
            server.ticker().submit(() -> occupied.set(server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack))));
            await(server, () -> occupied.get() != null, "the occupied attempt answered");
            assertTrue(occupied.get(), "the click consumed");
            assertEquals(BuiltinItems.PUMPKIN, stand.equipment(4).type(),
                    "the occupied slot kept its piece");

            // The creative hand places a copy without paying.
            hand.clear();
            hand.add(ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1));
            AtomicReference<Boolean> creative = new AtomicReference<>();
            server.ticker().submit(() -> creative.set(server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, true, stack -> hand.set(0, stack))));
            await(server, () -> creative.get() != null, "the creative attempt answered");
            assertTrue(creative.get());
            assertEquals(BuiltinItems.DIAMOND_LEGGINGS, stand.equipment(2).type());
            assertEquals(1, hand.get(0).count(), "the creative hand never pays");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The melee break ladder (takeDamage lines 412-429)
    // ------------------------------------------------------------------

    @Test
    void theMeleeBreakLadderWalks() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 0.0f, new java.util.Random(1)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            // Wear a helmet so the break's equipment drop is observable.
            List<ItemStack> hand = new ArrayList<>();
            hand.add(ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
            server.ticker().submit(() -> server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack)));
            await(server, () -> !stand.equipment(4).isEmpty(), "the helmet wore");

            // The first hit only stamps the clock (the wobble). The
            // world must be older than the five-tick window first (the
            // reference's own quirk: a first hit at world time <= 5
            // breaks instantly — the fresh-world edge).
            await(server, () -> server.world().timeOfDay() > 5,
                    "the world aged past the window");
            AtomicReference<Boolean> first = new AtomicReference<>();
            server.ticker().submit(() -> first.set(server.armorStands().attack(stand, false)));
            await(server, () -> first.get() != null, "the first hit answered");
            assertTrue(first.get());
            assertNotNull(server.armorStands().byId(stand.entityId()),
                    "the first hit never breaks the stand");
            int itemsBefore = countItems(server);

            // The second hit inside the window breaks it: the stand item
            // + the worn helmet both drop.
            AtomicReference<Boolean> second = new AtomicReference<>();
            server.ticker().submit(() -> second.set(server.armorStands().attack(stand, false)));
            await(server, () -> second.get() != null, "the second hit answered");
            assertTrue(second.get());
            assertNull(server.armorStands().byId(stand.entityId()), "the stand broke");
            final int base = itemsBefore;
            await(server, () -> server.itemEntities().all().size() >= base + 2,
                    "the stand item and the helmet dropped");

            // The creative hand removes without any drop.
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 0.0f, new java.util.Random(4)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand re-placed");
            ArmorStand creative = standAt(server, 10, 40, 10);
            int beforeCreative = countItems(server);
            server.ticker().submit(() -> server.armorStands().attack(creative, true));
            await(server, () -> server.armorStands().byId(creative.entityId()) == null,
                    "the creative remove swept the stand");
            assertEquals(beforeCreative, countItems(server), "the creative hand drops nothing");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The fire burn-down (takeDamage lines 388-400 + takeFireDamage)
    // ------------------------------------------------------------------

    @Test
    void theFireBurnBreaksTheStand() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, 0.0f, new java.util.Random(1)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            List<ItemStack> hand = new ArrayList<>();
            hand.add(ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
            server.ticker().submit(() -> server.armorStands().interactAt(
                    stand, hand.get(0), 0.05, false, stack -> hand.set(0, stack)));
            await(server, () -> !stand.equipment(1).isEmpty(), "the boots wore");

            // The ignite: 100 ticks of burn, 4.0 per burning tick to the
            // 0.5 floor — the stand breaks within six system ticks.
            server.ticker().submit(() -> server.armorStands().ignite(stand));
            await(server, () -> server.armorStands().byId(stand.entityId()) == null,
                    "the burn broke the stand");
            // The equipment dropped; the stand item did not (the burn's
            // own onBroken arm — only the equipment).
            boolean standItemDropped = server.itemEntities().all().stream()
                    .anyMatch(item -> item.stack().type().equals(BuiltinItems.ARMOR_STAND));
            boolean bootsDropped = server.itemEntities().all().stream()
                    .anyMatch(item -> item.stack().type().equals(BuiltinItems.IRON_BOOTS)
                            || item.stack().type().equals(BuiltinItems.DIAMOND_BOOTS));
            assertFalse(standItemDropped, "the burn never drops the stand item");
            assertTrue(bootsDropped, "the burn drops the worn equipment");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // The ZAD persistence round trip
    // ------------------------------------------------------------------

    @Test
    void theZadRoundTripSurvivesTheStore() throws Exception {
        EngineServer server = boot();
        Path store = dataDir.resolve("zad-roundtrip").resolve("armorstands.bin");
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 1, -90.0f, new java.util.Random(1)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            List<ItemStack> hand = new ArrayList<>();
            hand.add(ItemStack.of(BuiltinItems.GOLDEN_HELMET, 1));
            server.ticker().submit(() -> server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack)));
            await(server, () -> !stand.equipment(4).isEmpty(), "the helmet wore");

            AtomicReference<List<ArmorStandSystem.StandRecord>> snapshot =
                    new AtomicReference<>();
            server.ticker().submit(() -> snapshot.set(server.armorStands().snapshot()));
            await(server, () -> snapshot.get() != null, "the snapshot taken");
            new ArmorStandDataStore(store).save(snapshot.get());
            assertTrue(Files.exists(store), "the store wrote");
        } finally {
            server.shutdown(() -> { });
        }

        List<ArmorStandSystem.StandRecord> loaded = new ArmorStandDataStore(store).load();
        assertEquals(1, loaded.size());
        ArmorStandSystem.StandRecord record = loaded.get(0);
        assertEquals(10.5, record.x(), 1e-9);
        // The -90 placement snaps: -90 - 180 = -270 -> wraps to 90 ->
        // floor((90 + 22.5)/45)*45 = 90.
        assertEquals(90.0f, record.yaw(), 1e-6);
        assertEquals(BuiltinItems.GOLDEN_HELMET, record.equipment().get(4).type());
        assertEquals(0, record.status(), "the default stand's flags");
        assertNotNull(record.head(), "the pose persisted");
    }

    // ------------------------------------------------------------------
    // Helpers (the decoration test's own shape)
    // ------------------------------------------------------------------

    private static ArmorStand standAt(EngineServer server, int x, int y, int z) {
        for (ArmorStand stand : server.armorStands().allStands()) {
            if ((int) Math.floor(stand.x()) == x && (int) Math.floor(stand.y()) == y
                    && (int) Math.floor(stand.z()) == z) {
                return stand;
            }
        }
        return null;
    }

    private static int countItems(EngineServer server) throws InterruptedException {
        AtomicReference<Integer> count = new AtomicReference<>();
        server.ticker().submit(() -> count.set(server.itemEntities().all().size()));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && count.get() == null) {
            Thread.sleep(5);
        }
        return count.get() == null ? 0 : count.get();
    }

    private static void place(EngineServer server, int fx, int fy, int fz,
                              net.zaminmc.torch.block.BlockType floor) {
        server.ticker().submit(() ->
                server.world().setBlock(new BlockPosition(fx, fy, fz), floor));
    }

    private static void placeBlock(EngineServer server, int x, int y, int z,
                                   net.zaminmc.torch.block.BlockType type) {
        server.ticker().submit(() ->
                server.world().setBlock(new BlockPosition(x, y, z), type));
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "astd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
