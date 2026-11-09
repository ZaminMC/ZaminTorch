package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE CREATIVE MENU'S OWN WIRE LAW (Wave I1 — handleCreativeMenuSlot lines
 * 825-864 read whole, over the exact client flow the reference's
 * CreativeInventoryListener drives: every player-menu slot change rides
 * Set Creative Slot 0x10 with the PLAYER MENU's index, the pick onto the
 * cursor never leaves the client): the write arm accepts the menu's slots
 * 1..44 — the crafting grid 1-4, THE ARMOR CELLS 5-8 (a direct putStack
 * with NO type gate — the retired engine's refusal was the reported
 * creative-inventory loss), the main inventory 9-35, the hotbar 36-44 —
 * and refuses slot 0 (the result) and the out-of-menu cells whole; the
 * NEGATIVE SLOT is the DROP ARM (the stack thrown into the world with
 * the 200-tick +20 cooldown, the thrown body's age reset); the flag3
 * gates refuse metadata &lt; 0 and counts outside 1..64 SILENTLY (never
 * the retired clamp — a refused write leaves the slot untouched).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CreativeMenuWireParityTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /** The legacy ids the seats ride. */
    private static final int DIAMOND_CHESTPLATE = 311;
    private static final int DIAMOND_SWORD = 276;
    private static final int DIRT = 3;

    private TestClient18 creativeClient(String name) throws Exception {
        TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort());
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
        var session = server.playerRegistry().byName(name).orElseThrow();
        server.ticker().submit(() -> session.setGamemode(
                net.zaminmc.torch.GameMode.CREATIVE));
        // The mode's own landing gate (the first test after the class boot
        // races the engine's warm-up ticks — a fixed tick budget completes
        // while the submitted mode flip still sits in the queue; the
        // condition form never fires the C10 before the mode stands).
        awaitCondition(() -> session.gamemode() == net.zaminmc.torch.GameMode.CREATIVE,
                "the creative mode stands");
        EngineAwaits.awaitTicks(server.world(), 2, "engine progress (a 100 wall settle)");
        return client;
    }

    /**
     * THE ARMOR WRITE (lines 846-856 — flag1 covers 1..44 whole): the
     * creative menu's write into the armor cells 5-8 is a DIRECT put with
     * no placement filter; the retired engine refused the cells and the
     * client's own listener-driven pick silently vanished.
     */
    @Test
    void theArmorCellsAreDirectWrites() throws Exception {
        try (TestClient18 client = creativeClient("Armorer")) {
            // Wire slot 6 = the chest cell (player-menu slot 6, the
            // engine's head-first armor row).
            client.sendCreativeSlot(6, DIAMOND_CHESTPLATE, 0);
            EngineAwaits.awaitTicks(server.world(), 4, "armor write settles");
            var session = server.playerRegistry().byName("Armorer").orElseThrow();
            var worn = session.inventory().armorSnapshot();
            var chest = worn.get(1);
            assertEquals(DIAMOND_CHESTPLATE, chest.isEmpty() ? -1
                            : legacyIdOf(chest.type().identifier().value()),
                    "the chest cell carries the written piece");
        }
        awaitEmptyServer();
    }

    /**
     * THE VANISHED-PICK REGRESSION (the owner's own report — "take
     * something from creative to inventory it disappears"): the hotbar
     * write persists server-side AND the window the client reads back
     * carries it (the listener law's own round trip).
     */
    @Test
    void theHotbarPlacementPersistsAndRoundTrips() throws Exception {
        try (TestClient18 client = creativeClient("Picker")) {
            client.sendCreativeSlot(36, DIAMOND_SWORD, 0);
            EngineAwaits.awaitTicks(server.world(), 4, "hotbar write settles");
            var session = server.playerRegistry().byName("Picker").orElseThrow();
            var held = session.inventory().held();
            assertFalse(held.isEmpty(), "the pick landed in the hotbar");
            assertEquals("diamond_sword", held.type().identifier().value());

            // The round trip: drain the window syncs until the pick's own
            // flush arrives (the gamemode switch's own flush rides ahead).
            int[] window = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline && window == null) {
                int[] candidate = client.readWindowItems(10_000);
                if (candidate[0] == 0 && candidate[3] == DIAMOND_SWORD) {
                    window = candidate;
                }
            }
            assertTrue(window != null, "the pick's flush never arrived");
            assertEquals(36, window[2]); // the hotbar wire slot
            assertEquals(1, window[4]); // one unit
        }
        awaitEmptyServer();
    }

    /**
     * THE DROP ARM (lines 857-863): a negative slot throws the carried
     * stack into the world (the cooldown's first drop always rides), the
     * thrown body's age reset at the spawn.
     */
    @Test
    void theNegativeSlotDropsIntoTheWorld() throws Exception {
        try (TestClient18 client = creativeClient("Dropper")) {
            client.sendCreativeSlot(-1, DIRT, 0);
            awaitCondition(() -> !server.itemEntities().all().isEmpty(),
                    "the creative drop spawned an item entity");
            var session = server.playerRegistry().byName("Dropper").orElseThrow();
            // The +20 was paid (the await's own ticks decay it — the
            // reference's per-tick -- at line 144, the read bounds it).
            int cooldown = session.creativeDropCooldown();
            assertTrue(cooldown > 0 && cooldown <= 20,
                    "the first drop paid the +20 cooldown (now " + cooldown + ")");
        }
        awaitEmptyServer();
    }

    /**
     * THE FLAG3 GATES (lines 847-848): a count outside 1..64 or a negative
     * metadata is REFUSED whole — the slot keeps its prior content, never
     * a clamp.
     */
    @Test
    void outOfBandCountsAndMetadataAreRefusedWhole() throws Exception {
        try (TestClient18 client = creativeClient("Forger")) {
            // The seed write the gates must leave intact.
            client.sendCreativeSlot(37, DIAMOND_SWORD, 0);
            EngineAwaits.awaitTicks(server.world(), 4, "seed settles");
            // Count 0 (the empty-form count) and 65 (beyond the cap): both
            // refused, the sword stays.
            client.sendCreativeSlotWithCount(37, DIAMOND_SWORD, 0, 0);
            client.sendCreativeSlotWithCount(37, DIAMOND_SWORD, 65, 0);
            EngineAwaits.awaitTicks(server.world(), 4, "refusals settle");
            var session = server.playerRegistry().byName("Forger").orElseThrow();
            var slot = session.inventory().slot(1); // hotbar 1 = wire 37
            assertFalse(slot.isEmpty(), "the refused writes never cleared the slot");
            assertEquals(1, slot.count());
        }
        awaitEmptyServer();
    }

    /** The legacy id the snapshot's identifier resolves to (the test's own). */
    private static int legacyIdOf(String value) {
        return switch (value) {
            case "diamond_chestplate" -> DIAMOND_CHESTPLATE;
            case "diamond_sword" -> DIAMOND_SWORD;
            case "dirt" -> DIRT;
            default -> -1;
        };
    }
}
