package net.zaminmc.torch.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

            // A farmer with a pen-free deterministic stock: the profession is
            // random per spawn, so force one through the seeded reroll — the
            // test spawns villagers until a farmer shows (bounded).
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

            // The buy stock: 20 wheat (farmer) covers whichever career — give
            // a spread of every buy currency the tables use.
            client.sendChat("/give minecraft:wheat 64");
            Thread.sleep(150);
            client.sendChat("/give minecraft:paper 64");
            Thread.sleep(150);
            client.sendChat("/give minecraft:beef 64");
            Thread.sleep(150);
            client.sendChat("/give minecraft:porkchop 64");
            Thread.sleep(150);
            client.sendChat("/give minecraft:iron_ingot 64");
            Thread.sleep(150);
            client.sendChat("/give minecraft:rotten_flesh 64");
            Thread.sleep(150);

            // The open: Open Window "minecraft:villager" (3 slots), then the
            // MC|TrList plugin message with the offers.
            client.sendUseEntity(villagerId, Protocol18.USE_ENTITY_INTERACT);
            Object[] window = client.readOpenWindow(20_000);
            int windowId = (Integer) window[0];
            assertEquals("minecraft:villager", window[1], "the merchant window type");
            assertEquals(3, (Integer) window[2], "the merchant GUI's 3 slots");

            int[][] offers = readTradeList(client, windowId);
            assertEquals(3, offers.length, "three offers on the list");

            // Select the first row: the result slot previews (Set Slot 2).
            int offerIndex = 0;
            sendTradeSelect(client, offerIndex);
            int[] result = readSetSlotOfWindow(client, windowId, 2, 20_000);
            assertTrue(result[0] != -1, "the result slot previews the offer's payout");

            // The execution: a plain left-click on slot 2 pays out. Find the
            // buy requirement server-side and satisfy it (the stock was given).
            var villager = server.mobs().byId(villagerId);
            var offer = villager.offers()[offerIndex];
            var player = server.playerRegistry().byName("Trader").orElseThrow();
            assertTrue(player.inventory().countOf(offer.buy1().type()) >= offer.buy1().count(),
                    "the test stocked the buy currency");

            client.sendWindowClick(windowId, 2, 0, 0, 1);
            awaitCondition(() -> villager.offerUses(offerIndex) == 1,
                    "the trade charged one use");
            assertTrue(player.inventory().countOf(offer.result().type()) >= offer.result().count(),
                    "the result paid into the inventory");

            // The budget: charge the rest out, the row greys on the next list.
            for (int i = 1; i < offer.maxUses(); i++) {
                client.sendWindowClick(windowId, 2, 0, 0, 2 + i);
                Thread.sleep(60);
            }
            awaitCondition(() -> villager.offerUses(offerIndex) == offer.maxUses(),
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
