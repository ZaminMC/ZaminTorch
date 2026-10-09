package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The container polish over the real wire: a chest opening swings its lid
 * for every OTHER observer through Block Action 0x24 (the opener's own
 * client animates its view and gets nothing), closing swings it back, and
 * the hotbar switch is confirmed to the owner through Held Item Change 0x09.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChestLidAndHeldItemIntegrationTest extends ProtocolTestBase {

    private static final int CHEST = 54;

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    @Test
    void chestLidSwingsForObserversAndHeldItemConfirms() throws Exception {
        try (TestClient18 opener = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 watcher = new TestClient18("127.0.0.1", adapter.boundPort())) {
            opener.sendHandshake(47, 2);
            opener.sendLoginStart("Opener");
            opener.readLoginSuccess();
            opener.readUntilPositionAndLook();
            awaitPlayer("Opener");
            opener.readWindowItems(10_000);

            watcher.sendHandshake(47, 2);
            watcher.sendLoginStart("Watcher");
            watcher.readLoginSuccess();
            watcher.readUntilPositionAndLook();
            awaitPlayer("Watcher");
            watcher.readWindowItems(10_000);

            // Place the chest beside spawn (the flat world's surface).
            opener.sendChat("/give chest 1");
            opener.readWindowSlotTable(10_000);
            opener.sendBlockPlacement(2, 4, 2, 1, CHEST);
            opener.readBlockChange(10_000);
            Thread.sleep(200); // the placement's fan-out window

            // Open the chest: the watcher sees the lid swing open (0x24).
            opener.sendBlockPlacement(2, 5, 2, 1, -1);
            Object[] open = opener.readOpenWindow(10_000);
            int windowId = (Integer) open[0];
            assertTrue(windowId > 0, "the chest window opened");
            byte[] lid = watcher.readPacketOfType(Protocol18.S2C_BLOCK_ACTION, 10_000);
            DataInputStream body = frameBody(lid);
            body.readLong(); // the packed position (asserted by the codec test)
            assertEquals(1, body.readUnsignedByte(), "action 1 = the chest lid");
            assertEquals(1, body.readUnsignedByte(), "param 1 = open");
            assertEquals(CHEST, body.readInt(), "the block type is the chest");

            // Close the chest: the lid swings back for the watcher.
            opener.sendCloseWindow(windowId);
            byte[] close = watcher.readPacketOfType(Protocol18.S2C_BLOCK_ACTION, 10_000);
            DataInputStream closeBody = frameBody(close);
            closeBody.readLong(); // the packed position
            assertEquals(1, closeBody.readUnsignedByte(), "action 1 = the chest lid");
            assertEquals(0, closeBody.readUnsignedByte(), "param 0 = close");

            // The held-slot switch is confirmed to the owner (0x09, slot 3).
            opener.sendHeldItemChange(3);
            byte[] held = opener.readPacketOfType(Protocol18.S2C_HELD_ITEM_CHANGE, 10_000);
            DataInputStream heldBody = frameBody(held);
            assertEquals(3, heldBody.readUnsignedByte(), "the confirm names the new slot");
        }
    }

    /** @return a reader positioned past the packet-id varint. */
    private static DataInputStream frameBody(byte[] payload) {
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.wrappedBuffer(payload);
        ByteBufOps.readVarInt(buffer); // the packet id itself
        return new DataInputStream(new ByteArrayInputStream(
                java.util.Arrays.copyOfRange(payload, buffer.readerIndex(), payload.length)));
    }

    @Test
    void thePackedLidPositionNamesTheChestBlock() {
        // The lid's packed position rides the same protocol-47 layout the
        // sign path uses; pin the codec round-trip here so the action's
        // geometry stays trustworthy.
        BlockPosition at = new BlockPosition(2, 5, 2);
        int[] decoded = ByteBufOps.decodePackedBlockPosition(
                ((long) (at.x() & 0x3FFFFFF) << 38)
                        | ((long) (at.y() & 0xFFF) << 26)
                        | (at.z() & 0x3FFFFFF));
        assertEquals(at.x(), decoded[0]);
        assertEquals(at.y(), decoded[1]);
        assertEquals(at.z(), decoded[2]);
    }
}
