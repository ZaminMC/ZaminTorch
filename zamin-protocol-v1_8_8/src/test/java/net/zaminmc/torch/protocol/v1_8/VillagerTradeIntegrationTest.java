package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trading contract over the real wire: the villager spawns with its
 * profession Int (index 16), the right-click opens the "minecraft:villager"
 * window and carries the offers on MC|TrList (no dedicated Trade List
 * packet on 47), the MC|TrSel selects a row (Set Slot on the result), and
 * the slot-2 click executes: buys out of the player's inventory, the result
 * in, the use counter charged.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VillagerTradeIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    @Test
    void villagerOpensTradesSelectsAndExecutesOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Trader");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();

            // The buy stock FIRST-pass removed: the 1:1 career model
            // (Slice 21g) rolls any career of any profession at the first
            // open, so the actually-needed currency is stocked after the
            // list arrives (below) — five stacks cover the worst band
            // (the cleric's flesh 40 x 7 = 280).

            // One villager near the anchor: the profession and the career
            // both roll at the spawn and the first open — the actually-
            // rolled career's currency is stocked after the open (the
            // buys validate at execution, not at the open).
            client.sendChat("/spawnmob villager 1");
            int villagerId = -1;
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline) {
                var near = server.mobs().all().stream()
                        .filter(m -> m.type() == net.zaminmc.torch.server.entity.MobType.VILLAGER
                                && m.position().x() * m.position().x()
                                + m.position().z() * m.position().z() < 20.0)
                        .findFirst();
                if (near.isPresent()) {
                    villagerId = near.get().entityId();
                    break;
                }
                Thread.sleep(100);
            }
            assertTrue(villagerId > 0, "the commanded villager registered near the anchor");

            // The spawn packet: the profession Int rides index 16.
            Object[] spawnFull = client.readSpawnMobWithWatcher(20_000, 120);
            int[][] watcher = (int[][]) spawnFull[1];
            boolean professionInt = false;
            for (int[] row : watcher) {
                if (row[0] == 16 && row[1] == Protocol18.METADATA_TYPE_INT) {
                    professionInt = true;
                }
            }
            assertTrue(professionInt, "the profession Int rides index 16");

            // The open: Open Window "minecraft:villager" (3 slots), then the
            // MC|TrList plugin message with the offers.
            client.sendUseEntity(villagerId, Protocol18.USE_ENTITY_INTERACT);
            Object[] window = client.readOpenWindow(20_000);
            int windowId = (Integer) window[0];
            assertEquals("minecraft:villager", window[1], "the merchant window type");
            assertEquals(3, (Integer) window[2], "the merchant GUI's 3 slots");

            int[][] offers = readTradeList(client, windowId);
            // The 1:1 career model (Slice 21g): the first open rolls the
            // career and its opening level — the list carries that
            // career's level-1 rows (2-4 for the farmer's careers).
            var villager = server.mobs().byId(villagerId);
            assertNotNull(villager, "the trader lives");
            var stock = villager.offers();
            assertEquals(stock.length, offers.length,
                    "the rolled career's level-1 rows on the list");

            // The actually-rolled currency (the buys validate at the
            // execution, not at the open): five stacks cover the worst
            // band and the inventory keeps room for the payout.
            var player = server.playerRegistry().byName("Trader").orElseThrow();
            var firstOffer = stock[0];
            String currency = firstOffer.buy1().type().identifier().toString();
            for (int round = 0; round < 5; round++) {
                client.sendChat("/give " + currency + " 64");
                Thread.sleep(120);
            }
            assertTrue(player.inventory().countOf(firstOffer.buy1().type())
                    >= firstOffer.buy1().count(),
                    "the rolled career's currency stocked (" + currency + ")");

            // Select the first row: the result slot previews (Set Slot 2).
            int offerIndex = 0;
            sendTradeSelect(client, offerIndex);
            int[] result = readSetSlotOfWindow(client, windowId, 2, 20_000);
            assertTrue(result[0] != -1, "the result slot previews the offer's payout");

            // The execution: a plain left-click on slot 2 pays out (the
            // stocked currency satisfies the buy).
            var offer = stock[offerIndex];

            client.sendWindowClick(windowId, 2, 0, 0, 1);
            awaitCondition(() -> villager.offerUses(offerIndex) == 1,
                    "the trade charged one use");
            assertTrue(player.inventory().countOf(offer.result().type()) >= offer.result().count(),
                    "the result paid into the inventory");

            // The budget: charge the rest out, the row greys on the next list.
            // The open window holds the level-up countdown (the customer
            // gate), so the budget stays the fresh 7 through the loop.
            for (int i = 1; i < villager.offerMaxUses(offerIndex); i++) {
                client.sendWindowClick(windowId, 2, 0, 0, 2 + i);
                Thread.sleep(60);
            }
            awaitCondition(() -> villager.offerUses(offerIndex) == villager.offerMaxUses(offerIndex),
                    "the budget spent out");
            assertFalse2(villager.offerAvailable(offerIndex), "the spent-out offer refuses");
        }
    }

    private static void assertFalse2(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Reads the MC|TrList plugin message (0x3F) and returns the offer table
     * as {buy1Id, resultId, disabled} rows (the ids simplified for asserts).
     */
    private int[][] readTradeList(TestClient18 client, int expectedWindowId) throws IOException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (true) {
            byte[] payload = client.readPacketOfType(Protocol18.S2C_PLUGIN_MESSAGE,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buf); // packet id
            String channel = ByteBufOps.readString(buf, 64);
            if (!Protocol18.PLUGIN_CHANNEL_TRADE_LIST.equals(channel)) {
                continue; // another plugin message: keep waiting
            }
            assertEquals(expectedWindowId, buf.readInt(), "the payload's plain-int window id");
            int count = buf.readUnsignedByte();
            int[][] rows = new int[count][3];
            for (int i = 0; i < count; i++) {
                rows[i][0] = slotItemId(buf);
                rows[i][1] = slotItemId(buf);
                boolean hasSecond = buf.readBoolean();
                if (hasSecond) {
                    slotItemId(buf);
                }
                rows[i][2] = buf.readBoolean() ? 1 : 0;
                buf.readInt(); // uses
                buf.readInt(); // maxUses
            }
            return rows;
        }
    }

    /** Parses one 1.8 slot, returning the legacy item id (-1 empty). */
    private static int slotItemId(ByteBuf buf) throws IOException {
        int id = buf.readShort();
        if (id == -1) {
            return -1;
        }
        buf.readByte();  // count
        buf.readShort(); // damage
        SlotNbt.skip(buf);
        return id;
    }

    /** Sends MC|TrSel (the selected trade row: one plain i32). */
    private static void sendTradeSelect(TestClient18 client, int offerIndex) throws IOException {
        ByteBuf payload = Unpooled.buffer(8);
        ByteBufOps.writeString(payload, Protocol18.PLUGIN_CHANNEL_TRADE_SELECT);
        payload.writeInt(offerIndex);
        ByteBuf body = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(body, Protocol18.C2S_PLUGIN_MESSAGE);
        body.writeBytes(payload);
        client.sendPacket(bodyToBytes(body));
    }

    private static byte[] bodyToBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /** Reads a Set Slot for the window/slot pair, returning {itemId, count}. */
    private int[] readSetSlotOfWindow(TestClient18 client, int windowId, int slot,
                                      long timeoutMs) throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            byte[] payload = client.readPacketOfType(Protocol18.S2C_SET_SLOT,
                    Math.max(1, deadline - System.currentTimeMillis()));
            ByteBuf buf = Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(buf); // packet id
            int readWindow = buf.readByte() & 0xFF;
            int readSlot = buf.readShort();
            int id = buf.readShort();
            int count = id == -1 ? 0 : buf.readByte();
            if (readWindow == windowId && readSlot == slot) {
                return new int[]{id, count};
            }
        }
        throw new IOException("Timed out waiting for Set Slot " + slot + " of window " + windowId);
    }
}
