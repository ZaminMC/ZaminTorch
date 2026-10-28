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
import net.zaminmc.torch.testing.EngineAwaits;

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
                    new BlockPosition(10, 40, 10), 0, 0, 0.0f, new java.util.Random(1))));
            await(server, () -> down.get() != null, "the DOWN face answered");
            assertFalse(down.get(), "the DOWN face never places");

            // The UP face lands the stand on top of the floor block.
            AtomicReference<Boolean> up = new AtomicReference<>();
            server.ticker().submit(() -> up.set(server.armorStands().place(
                    new BlockPosition(10, 39, 10), 0, 1, 10.0f, new java.util.Random(1))));
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
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(2))));
            await(server, () -> again.get() != null, "the second attempt answered");
            assertFalse(again.get(), "the occupied box refused the second stand");

            // No headroom: the cell above solid refuses — the stand
            // attempts the cell whose ceiling is blocked.
            placeBlock(server, 10, 45, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 45, 10))
                    .identifier().value().equals("stone"), "the ceiling placed");
            AtomicReference<Boolean> crushed = new AtomicReference<>();
            server.ticker().submit(() -> crushed.set(server.armorStands().place(
                    new BlockPosition(10, 44, 10), 0, 1, 0.0f, new java.util.Random(3))));
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
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(1)));
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
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(1)));
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
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(4)));
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
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(1)));
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
                    new BlockPosition(10, 39, 10), 0, 1, -90.0f, new java.util.Random(1)));
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

    @Test
    void theDisabledSlotsGatesTheSwapLadder() throws Exception {
        // VT-7g — the reference's three gate bands: the resolution bits
        // (1 << slot, lines 317-327 — the empty hand's band take redirects
        // to the hand), the take bits (1 << slot+8, line 346 — the worn
        // piece never leaves), and the place bits (1 << slot+16, line 347
        // — a bare slot never receives).
        EngineServer server = boot();
        try {
            place(server, 10, 39, 10, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(10, 39, 10))
                    .identifier().value().equals("stone"), "the floor placed");
            server.ticker().submit(() -> server.armorStands().place(
                    new BlockPosition(10, 39, 10), 0, 1, 0.0f, new java.util.Random(1)));
            await(server, () -> standAt(server, 10, 40, 10) != null, "the stand landed");
            ArmorStand stand = standAt(server, 10, 40, 10);
            List<ItemStack> hand = new ArrayList<>();

            // The take bit (1 << (4 + 8)): the worn helmet never leaves.
            hand.add(ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
            server.ticker().submit(() -> server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack)));
            await(server, () -> !stand.equipment(4).isEmpty(), "the helmet wore");
            stand.setDisabledSlots(1 << 12); // the head's take bit
            hand.set(0, ItemStack.EMPTY);
            AtomicReference<Boolean> take = new AtomicReference<>();
            server.ticker().submit(() -> take.set(server.armorStands().interactAt(
                    stand, ItemStack.EMPTY, 1.7, false, stack -> hand.set(0, stack))));
            await(server, () -> take.get() != null, "the take answered");
            assertTrue(take.get(), "the click consumed");
            assertTrue(hand.get(0).isEmpty(), "the take bit held the helmet on the stand");
            assertEquals(BuiltinItems.DIAMOND_HELMET, stand.equipment(4).type(),
                    "the worn piece never leaves");

            // The resolution bit (1 << 4): the empty hand's band take
            // redirects to the hand slot (0) — the helmet STAYS, nothing
            // lands in the hand (the bare hand slot's empty swap is a
            // no-op).
            stand.setDisabledSlots(1 << 4); // the head's resolution bit
            AtomicReference<Boolean> redirect = new AtomicReference<>();
            server.ticker().submit(() -> redirect.set(server.armorStands().interactAt(
                    stand, ItemStack.EMPTY, 1.7, false, stack -> hand.set(0, stack))));
            await(server, () -> redirect.get() != null, "the redirect answered");
            assertTrue(redirect.get(), "the click consumed");
            assertTrue(hand.get(0).isEmpty(), "the band take redirected to the bare hand slot");
            assertEquals(BuiltinItems.DIAMOND_HELMET, stand.equipment(4).type(),
                    "the helmet stays under the resolution bit");

            // The place bit (1 << (4 + 16)): a bare head slot never
            // receives the held helmet.
            stand.setDisabledSlots(0);
            hand.set(0, ItemStack.EMPTY);
            AtomicReference<Boolean> strip = new AtomicReference<>();
            server.ticker().submit(() -> strip.set(server.armorStands().interactAt(
                    stand, ItemStack.EMPTY, 1.7, false, stack -> hand.set(0, stack))));
            await(server, () -> strip.get() != null && stand.equipment(4).isEmpty(),
                    "the helmet left the slot");
            stand.setDisabledSlots(1 << 20); // the head's place bit
            hand.set(0, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
            AtomicReference<Boolean> place = new AtomicReference<>();
            server.ticker().submit(() -> place.set(server.armorStands().interactAt(
                    stand, hand.get(0), 1.0, false, stack -> hand.set(0, stack))));
            await(server, () -> place.get() != null, "the place answered");
            assertTrue(place.get(), "the click consumed");
            assertTrue(stand.equipment(4).isEmpty(),
                    "the place bit held the bare slot bare");
            assertEquals(1, hand.get(0).count(), "the held helmet never paid");

            // The v2 row rides the store: a nonzero bitfield round-trips.
            stand.setDisabledSlots((1 << 4) | (1 << 12) | (1 << 20));
            AtomicReference<List<ArmorStandSystem.StandRecord>> snapshot =
                    new AtomicReference<>();
            server.ticker().submit(() -> snapshot.set(server.armorStands().snapshot()));
            await(server, () -> snapshot.get() != null, "the snapshot taken");
            assertEquals((1 << 4) | (1 << 12) | (1 << 20),
                    snapshot.get().get(0).disabledSlots(),
                    "the bitfield row rides the record");
        } finally {
            server.shutdown(() -> { });
        }
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

    // ------------------------------------------------------------------
    // The named small gaps (the gravity walk, the minecart push, the
    // breaking particles)
    // ------------------------------------------------------------------

    @Test
    void theGravityWalkFallsAndLands() throws Exception {
        EngineServer server = boot();
        try {
            // The tower: a landing floor at 30, a cleared shaft 31..38, a
            // support at 39, the stand at 40. Removing the support drops
            // the stand onto the floor — the cleared shaft makes the
            // landing deterministic whatever the generator built there.
            place(server, 12, 30, 12, BuiltinBlocks.STONE);
            for (int y = 31; y <= 41; y++) {
                place(server, 12, y, 12, server.world().airType());
            }
            for (int y = 40; y <= 41; y++) {
                place(server, 14, y, 14, server.world().airType());
            }
            place(server, 12, 39, 12, BuiltinBlocks.STONE);
            place(server, 14, 39, 14, BuiltinBlocks.STONE);
            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(server.armorStands().place(
                    new BlockPosition(12, 39, 12), 0, 1, 0.0f, new java.util.Random(3))));
            await(server, () -> landed.get() != null, "the placement answered");
            assertTrue(landed.get(), "the stand landed at 40");
            ArmorStand stand = standAt(server, 12, 40, 12);
            assertNotNull(stand, "the stand stands at (12.5, 40, 12.5)");

            // The no-gravity twin: the same tower, the flag set — it never
            // walks (the reference's noGravity gate).
            AtomicReference<Boolean> flaggedLanded = new AtomicReference<>();
            server.ticker().submit(() -> flaggedLanded.set(server.armorStands().place(
                    new BlockPosition(14, 39, 14), 0, 1, 0.0f, new java.util.Random(4))));
            await(server, () -> flaggedLanded.get() != null, "the twin placed");
            ArmorStand flagged = standAt(server, 14, 40, 14);
            assertNotNull(flagged, "the twin stands at (14.5, 40, 14.5)");
            server.ticker().submit(() ->
                    flagged.setStatus((byte) (flagged.status() | ArmorStand.FLAG_NO_GRAVITY)));

            // Remove both supports: the flagged stand freezes midair, the
            // plain one falls onto the 30-floor and lands at 31.
            place(server, 12, 39, 12, server.world().airType());
            place(server, 14, 39, 14, server.world().airType());
            await(server, () -> stand != null && stand.onGround()
                    && stand.y() == 31.0, "the stand fell onto the landing floor");
            assertEquals(31.0, stand.y(), 1e-9, "the landed feet sit on the floor's top");
            assertEquals(0.0, stand.velocityY(), 1e-9, "the landing zeroed the fall");
            assertTrue(stand.onGround(), "the landing set the ground state");
            assertEquals(40.0, flagged.y(), 1e-9, "the flagged twin never walked");
            // The flagged twin keeps the ground state it earned while it
            // stood on its support (the reference's standing entities are
            // onGround too) — the flag freezes the walk, not the flag's
            // own past.
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMinecartPushesBothSidesApart() throws Exception {
        EngineServer server = boot();
        try {
            // A stand at (13.5, 40, 13.5) over a floor; a rideable cart at
            // (13.7, 40, 13.5) — inside the 0.2 squared band; a chest cart
            // at (13.9, 40, 13.5) — outside the consult's family; a far
            // rideable cart at (16.5, 40, 13.5) — outside the band.
            place(server, 13, 39, 13, BuiltinBlocks.STONE);
            place(server, 16, 39, 13, BuiltinBlocks.STONE);
            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(server.armorStands().place(
                    new BlockPosition(13, 39, 13), 0, 1, 0.0f, new java.util.Random(5))));
            await(server, () -> landed.get() != null, "the stand placed");
            ArmorStand stand = standAt(server, 13, 40, 13);
            assertNotNull(stand, "the stand stands at (13.5, 40, 13.5)");

            AtomicReference<Boolean> spawned = new AtomicReference<>();
            server.ticker().submit(() -> {
                server.vehicles().spawnMinecart(new net.zaminmc.torch.util.Position(13.7, 40.0, 13.5), 0);
                server.vehicles().spawnChestMinecart(new net.zaminmc.torch.util.Position(13.9, 40.0, 13.5), 0);
                server.vehicles().spawnMinecart(new net.zaminmc.torch.util.Position(16.5, 40.0, 13.5), 0);
                spawned.set(true);
            });
            await(server, () -> spawned.get() != null, "the carts spawned");
            await(server, () -> server.vehicles().all().size() == 3, "three carts live");

            // The push fires on the next stand tick: the near cart and the
            // stand get the 0.05-scale velocities apart; the chest cart and
            // the far cart stay untouched.
            await(server, () -> stand.velocityX() < 0.0, "the stand pushed away from the cart");
            var nearCart = server.vehicles().all().stream()
                    .filter(c -> c instanceof net.zaminmc.torch.server.entity.vehicle.MinecartEntity
                            && Math.abs(c.position().x() - 13.7) < 0.1)
                    .findFirst().orElseThrow();
            var chestCart = server.vehicles().all().stream()
                    .filter(c -> c instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                    .findFirst().orElseThrow();
            var farCart = server.vehicles().all().stream()
                    .filter(c -> c instanceof net.zaminmc.torch.server.entity.vehicle.MinecartEntity
                            && Math.abs(c.position().x() - 16.5) < 0.1)
                    .findFirst().orElseThrow();
            // The reference's push: d0 = stand.x - cart.x = -0.2 -> the
            // stand gets -0.05 (away from the cart, -x), the cart +0.05.
            assertTrue(nearCart.velocityX() > 0.0,
                    "the near rideable cart pushed away: " + nearCart.velocityX());
            assertEquals(0.0, chestCart.velocityX(), 1e-9,
                    "the chest family never rides the consult");
            assertEquals(0.0, farCart.velocityX(), 1e-9,
                    "the far cart sits outside the 0.2 band");
            // The stand slides (the ground friction decays it, never flips).
            double firstX = stand.x();
            await(server, () -> stand.x() < firstX, "the stand slid along the floor");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theBreakingParticlesBurstOnBothArms() throws Exception {
        EngineServer server = boot();
        try {
            List<net.zaminmc.torch.server.fx.FxManager.StandShatter> bursts =
                    new java.util.concurrent.CopyOnWriteArrayList<>();
            server.fx().addListener(event -> {
                if (event instanceof net.zaminmc.torch.server.fx.FxManager.StandShatter burst) {
                    bursts.add(burst);
                }
            });

            // The double-hit ladder: the wobble first (no burst), the
            // second hit breaks with the burst.
            place(server, 11, 39, 11, BuiltinBlocks.STONE);
            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(server.armorStands().place(
                    new BlockPosition(11, 39, 11), 0, 1, 0.0f, new java.util.Random(6))));
            await(server, () -> landed.get() != null, "the stand placed");
            ArmorStand stand = standAt(server, 11, 40, 11);
            assertNotNull(stand, "the stand stands at (11.5, 40, 11.5)");

            // The reference's clock base: a fresh world's first ticks are
            // inside the double-hit window of a never-hit stand (lastDamageTime
            // 0) — wait the clock past the window so the first hit is
            // deterministically the wobble.
            await(server, () -> server.world().totalTicks() > 10, "the clock advanced past the window");

            AtomicReference<Boolean> wobbled = new AtomicReference<>();
            server.ticker().submit(() -> wobbled.set(server.armorStands().attack(stand, false)));
            await(server, () -> wobbled.get() != null, "the first hit answered");
            assertTrue(wobbled.get(), "the first hit is consumed (the wobble)");
            assertTrue(bursts.isEmpty(), "the wobble never bursts");

            AtomicReference<Boolean> broken = new AtomicReference<>();
            server.ticker().submit(() -> broken.set(server.armorStands().attack(stand, false)));
            await(server, () -> broken.get() != null, "the second hit answered");
            assertTrue(broken.get(), "the second hit breaks the stand");
            assertEquals(1, bursts.size(), "the break bursts once");
            assertEquals(11.5, bursts.get(0).position().x(), 1e-9);
            assertEquals(40.0 + 1.975 / 1.5, bursts.get(0).position().y(), 1e-9,
                    "the burst rides the reference's height/1.5 offset");
            assertEquals(11.5, bursts.get(0).position().z(), 1e-9);
            assertNull(standAt(server, 11, 40, 11), "the stand is gone");

            // The creative arm bursts too (the reference's lines 416-419):
            // the same floor at 39 carries a second stand at 40 (the first
            // broke there; its cell is free again).
            AtomicReference<Boolean> creativeLanded = new AtomicReference<>();
            server.ticker().submit(() -> creativeLanded.set(server.armorStands().place(
                    new BlockPosition(11, 39, 11), 0, 1, 0.0f, new java.util.Random(7))));
            await(server, () -> creativeLanded.get() != null, "the creative stand placed");
            ArmorStand creative = standAt(server, 11, 40, 11);
            assertNotNull(creative, "the second stand stands at 40");
            AtomicReference<Boolean> creativeBroke = new AtomicReference<>();
            server.ticker().submit(() -> creativeBroke.set(server.armorStands().attack(creative, true)));
            await(server, () -> creativeBroke.get() != null, "the creative hit answered");
            assertTrue(creativeBroke.get(), "the creative hand consumed the hit");
            assertEquals(2, bursts.size(), "the creative arm bursts too");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theIceFamilySlipperinessCarriesTheSlide() throws Exception {
        EngineServer server = boot();
        try {
            // Two floors: ice under the first stand, stone under the
            // control. The same 0.05 push, the reference's slipperiness
            // families — the ice stand (Block.slipperiness 0.98) slides
            // far past the control (0.6).
            place(server, 13, 39, 13, BuiltinBlocks.ICE);
            place(server, 17, 39, 13, BuiltinBlocks.STONE);
            for (int y = 40; y <= 41; y++) {
                place(server, 13, y, 13, server.world().airType());
                place(server, 14, y, 13, server.world().airType());
                place(server, 17, y, 13, server.world().airType());
                place(server, 18, y, 13, server.world().airType());
            }
            AtomicReference<Boolean> iceLanded = new AtomicReference<>();
            server.ticker().submit(() -> iceLanded.set(server.armorStands().place(
                    new BlockPosition(13, 39, 13), 0, 1, 0.0f, new java.util.Random(8))));
            await(server, () -> iceLanded.get() != null, "the ice stand placed");
            ArmorStand iceStand = standAt(server, 13, 40, 13);
            assertNotNull(iceStand, "the ice stand stands");

            AtomicReference<Boolean> controlLanded = new AtomicReference<>();
            server.ticker().submit(() -> controlLanded.set(server.armorStands().place(
                    new BlockPosition(17, 39, 13), 0, 1, 0.0f, new java.util.Random(9))));
            await(server, () -> controlLanded.get() != null, "the control placed");
            ArmorStand control = standAt(server, 17, 40, 13);
            assertNotNull(control, "the control stands");

            AtomicReference<Boolean> spawned = new AtomicReference<>();
            server.ticker().submit(() -> {
                server.vehicles().spawnMinecart(new net.zaminmc.torch.util.Position(13.7, 40.0, 13.5), 0);
                server.vehicles().spawnMinecart(new net.zaminmc.torch.util.Position(17.7, 40.0, 13.5), 0);
                spawned.set(true);
            });
            await(server, () -> spawned.get() != null, "the push carts spawned");

            // Both stands get the same -x push; the friction families
            // differ. The ice stand passes 0.2 out; the control settles
            // under the common family's reach.
            double iceStart = 13.5;
            double controlStart = 17.5;
            await(server, () -> iceStand.x() < iceStart - 0.2,
                    "the ice stand slid past 0.2 (the 0.98 family)");
            assertTrue(control.x() > controlStart - 0.12,
                    "the control settles under the 0.6 family's reach: "
                            + (controlStart - control.x()));
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theMarkerStandRefusesDamageAndBreaks() throws Exception {
        EngineServer server = boot();
        try {
            // The reference's takeDamage gate (lines 376-380): the
            // hitbox-less marker takes no damage — no wobble, no
            // double-hit break, no explosion.
            place(server, 19, 39, 19, BuiltinBlocks.STONE);
            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(server.armorStands().place(
                    new BlockPosition(19, 39, 19), 0, 1, 0.0f, new java.util.Random(10))));
            await(server, () -> landed.get() != null, "the stand placed");
            ArmorStand stand = standAt(server, 19, 40, 19);
            assertNotNull(stand, "the stand stands");
            server.ticker().submit(() ->
                    stand.setStatus((byte) (stand.status() | ArmorStand.FLAG_MARKER)));
            await(server, () -> stand.marker(), "the marker flag set");

            AtomicReference<Boolean> firstHit = new AtomicReference<>();
            server.ticker().submit(() -> firstHit.set(server.armorStands().attack(stand, false)));
            await(server, () -> firstHit.get() != null, "the first hit answered");
            assertFalse(firstHit.get(), "the marker refuses the melee hit");
            EngineAwaits.awaitTicks(server.world(), 3, "past the five-tick window");
            AtomicReference<Boolean> secondHit = new AtomicReference<>();
            server.ticker().submit(() -> secondHit.set(server.armorStands().attack(stand, false)));
            await(server, () -> secondHit.get() != null, "the second hit answered");
            assertFalse(secondHit.get(), "the marker refuses the double-hit break too");
            assertNotNull(standAt(server, 19, 40, 19), "the marker still stands");

            server.ticker().submit(() -> server.armorStands().breakByExplosion(stand));
            await(server, () -> true, "the explosion arm ran");
            assertNotNull(standAt(server, 19, 40, 19), "the marker survives the explosion");
        } finally {
            server.shutdown(null);
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "astd" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    // ------------------------------------------------------------------
    // The EntityTag merge (Slice TF-7d — ArmorStandItem.useOn 55-61)
    // ------------------------------------------------------------------

    @Test
    void theEntityTagMergesOverTheFreshStand() throws Exception {
        EngineServer server = boot();
        try {
            place(server, 20, 39, 20, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(20, 39, 20))
                    .identifier().value().equals("stone"), "the floor placed");

            // The item's EntityTag rows (the boxed NBT form the wire parse
            // carries): the flag family, the Rotation override, and a
            // PARTIAL Pose (the head alone — the shallow merge's own
            // quirk: the other parts reset to the reference defaults).
            java.util.Map<String, Object> pose = new java.util.LinkedHashMap<>();
            pose.put("Head", List.of(10.0f, 20.0f, 30.0f));
            java.util.Map<String, Object> entityTag = new java.util.LinkedHashMap<>();
            entityTag.put("Small", (byte) 1);
            entityTag.put("ShowArms", (byte) 1);
            entityTag.put("NoBasePlate", (byte) 1);
            entityTag.put("Invisible", (byte) 1);
            entityTag.put("Rotation", List.of(90.0f));
            entityTag.put("Pose", pose);

            AtomicReference<Boolean> landed = new AtomicReference<>();
            server.ticker().submit(() -> landed.set(server.armorStands().place(
                    new BlockPosition(20, 39, 20), 0, 1, 0.0f,
                    new java.util.Random(21), entityTag)));
            await(server, () -> landed.get() != null, "the tagged placement answered");
            assertTrue(landed.get(), "the tagged stand landed");
            ArmorStand stand = standAt(server, 20, 40, 20);
            assertNotNull(stand, "the tagged stand stands");

            // The flags rode the merge (readCustomNbt's boolean rows).
            assertTrue(stand.small(), "Small rode the tag");
            assertTrue(stand.showArms(), "ShowArms rode the tag");
            assertTrue(stand.noBasePlate(), "NoBasePlate rode the tag");
            assertTrue(stand.invisible(), "Invisible rode the tag");
            // The Rotation override (the base row's yaw): 90 over the
            // placement's own snap.
            assertEquals(90.0f, stand.yaw(), 1e-6f, "the tag's Rotation overrode the snap");
            // The PARTIAL Pose quirk: the head from the tag, the other
            // parts at the reference DEFAULTS (never the roll).
            assertEquals(10.0f, stand.head().pitch(), 1e-6f);
            assertEquals(20.0f, stand.head().yaw(), 1e-6f);
            assertEquals(30.0f, stand.head().roll(), 1e-6f);
            assertEquals(ArmorStand.DEFAULT_LEFT_ARM.pitch(),
                    stand.leftArm().pitch(), 1e-6f,
                    "the shallow merge reset the absent pose parts to the defaults");
            assertEquals(ArmorStand.DEFAULT_BODY.yaw(), stand.body().yaw(), 1e-6f);

            // The ABSENT rows keep the stand's own state: a tag with the
            // Small flag alone keeps the placement's roll and snap.
            place(server, 22, 39, 20, BuiltinBlocks.STONE);
            await(server, () -> server.world().getBlock(new BlockPosition(22, 39, 20))
                    .identifier().value().equals("stone"), "the second floor placed");
            java.util.Map<String, Object> smallOnly = new java.util.LinkedHashMap<>();
            smallOnly.put("Small", (byte) 1);
            AtomicReference<Boolean> plain = new AtomicReference<>();
            server.ticker().submit(() -> plain.set(server.armorStands().place(
                    new BlockPosition(22, 39, 20), 0, 1, 10.0f,
                    new java.util.Random(22), smallOnly)));
            await(server, () -> plain.get() != null, "the small-only placement answered");
            assertTrue(plain.get(), "the small-only stand landed");
            ArmorStand rolled = standAt(server, 22, 40, 20);
            assertNotNull(rolled, "the small-only stand stands");
            assertTrue(rolled.small(), "the Small flag rode");
            // The snap kept (playerYaw 10 -> the -180 snap, the placement
            // test's own math) and the roll kept (the head pitch band).
            assertEquals(-180.0f, rolled.yaw(), 1e-6f, "the absent Rotation kept the snap");
            assertTrue(rolled.head().pitch() >= 0.0f && rolled.head().pitch() < 5.0f,
                    "the absent Pose kept the roll");
        } finally {
            server.shutdown(() -> { });
        }
    }

}
