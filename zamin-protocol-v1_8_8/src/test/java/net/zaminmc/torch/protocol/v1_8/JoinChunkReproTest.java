package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import net.zaminmc.torch.testing.EngineAwaits;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces the live-join symptom offline: full engine boot + adapter, a
 * scripted join, capture of the ground-up chunk packet for (0,0) — in
 * whatever frame form carried it (the single 0x21 or, since ML-019's
 * batching, the (0,0) column's slice of a 0x26 Map Chunk Bulk) — and a
 * byte-comparison against a direct serialize of the same chunk. Diagnostic
 * harness for the chunk-payload investigation (run explicitly).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoinChunkReproTest {

    @TempDir
    static Path dataDir;

    @Test
    void joinChunkMatchesDirectSerialization() throws Exception {
        // The launcher's property file shape, with an ephemeral port for the test.
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "world", "ZaminTorch test",
                20, 4, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        V18ProtocolServer adapter = new V18ProtocolServer(server, 250_000);
        adapter.start(server);

        // Raw tap: capture the (0,0) column the adapter writes — the single
        // 0x21 body OR the (0,0) slice inside a 0x26 bulk (ML-019: the join
        // batches the pregenerated columns).
        // Join a real scripted client and capture the ground-up chunk (0,0)
        // exactly as it goes over the wire, then compare with a direct
        // serialization of the same chunk from the world.
        byte[] wirePayload = null;
        int wireMask = 0;
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Repro");
            client.readLoginSuccess();
            // Drain login through the chunk wave, capturing the (0,0) chunk.
            long deadline = System.currentTimeMillis() + 20_000;
            boolean sawLook = false;
            while (System.currentTimeMillis() < deadline && (wirePayload == null || !sawLook)) {
                byte[] raw = client.readPacketForDiagnostics();
                ByteBuf frame = Unpooled.wrappedBuffer(raw);
                int id = ByteBufOps.readVarInt(frame);
                if (id == Protocol18.S2C_CHUNK_DATA) {
                    int cx = frame.readInt();
                    int cz = frame.readInt();
                    if (cx == 0 && cz == 0 && wirePayload == null) {
                        frame.readBoolean();          // ground-up: true on the join
                        wireMask = frame.readShort() & 0xFFFF;
                        int length = ByteBufOps.readVarInt(frame);
                        wirePayload = new byte[length];
                        frame.readBytes(wirePayload);
                    }
                } else if (id == Protocol18.S2C_CHUNK_BULK) {
                    // The bulk's two-loop form: every column's header first,
                    // then every column's array concatenated WITHOUT length
                    // prefixes — each array sized by its own mask (the client's
                    // own size law). Pull the (0,0) column's slice out.
                    frame.readBoolean();              // skylight present
                    int columns = ByteBufOps.readVarInt(frame);
                    int[] xs = new int[columns];
                    int[] zs = new int[columns];
                    int[] masks = new int[columns];
                    for (int i = 0; i < columns; i++) {
                        xs[i] = frame.readInt();
                        zs[i] = frame.readInt();
                        masks[i] = frame.readShort() & 0xFFFF;
                    }
                    for (int i = 0; i < columns; i++) {
                        int size = Integer.bitCount(masks[i]) * 12_288 + 256;
                        if (xs[i] == 0 && zs[i] == 0 && wirePayload == null) {
                            wireMask = masks[i];
                            wirePayload = new byte[size];
                            frame.readBytes(wirePayload);
                        } else {
                            frame.skipBytes(size);
                        }
                    }
                } else if (id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                    sawLook = true;
                }
            }
        }
        assertTrue(wirePayload != null, "the (0,0) column arrived on the wire");
        System.out.println("join chunk (0,0) mask=" + Integer.toBinaryString(wireMask & 0xFFFF)
                + " len=" + wirePayload.length);

        EngineAwaits.awaitTicks(server.world(), 16, "a few ticks: the live loop runs (random ticks etc.)");
        var world = server.world();
        var chunk = world.peek(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        System.out.println("server-view (0,4,0) = " + world.getBlock(
                new net.zaminmc.torch.block.BlockPosition(0, 4, 0)).identifier());
        System.out.println("server-view (0,1,0) = " + world.getBlock(
                new net.zaminmc.torch.block.BlockPosition(0, 1, 0)).identifier());
        byte[] payload = ChunkSerializer18.serialize(chunk, true);
        int directMask = ChunkSerializer18.sectionBitmask(chunk);
        System.out.println("direct mask=" + Integer.toBinaryString(directMask & 0xFFFF)
                + " len=" + payload.length);
        int idx = (1 << 8); // (0,1,0)
        System.out.println("wire (0,1,0) state="
                + (wirePayload[idx * 2] & 0xFF | (wirePayload[idx * 2 + 1] & 0xFF) << 8));
        System.out.println("direct (0,1,0) state="
                + (payload[idx * 2] & 0xFF | (payload[idx * 2 + 1] & 0xFF) << 8));
        org.junit.jupiter.api.Assertions.assertEquals(directMask, wireMask,
                "the join column's mask equals the direct serialization's");
        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, wirePayload,
                "the join chunk must equal a direct serialization");
        server.shutdown(adapter::shutdown);
    }
}
