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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Reproduces the live-join symptom offline: full engine boot + adapter, a
 * scripted join, capture of the ground-up chunk packet for (0,0), and a
 * byte-comparison against a direct serialize of the same chunk. Diagnostic
 * harness for the chunk-payload investigation (run explicitly).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoinChunkReproTest {

    @TempDir
    static Path dataDir;

    @Test
    void joinChunkMatchesDirectSerialization() throws Exception {
        // The launcher's exact config source: zamin.properties with data-dir=.
        // The launcher's exact config source, with an ephemeral port for the test.
        EngineConfig launcherConfig = net.zaminmc.torch.server.config.ConfigLoader.loadOrDefault(
                Path.of("/home/z/my-project/ZaminTorch/run/zamin.properties"));
        EngineConfig config = new EngineConfig("127.0.0.1", 0, launcherConfig.worldName(),
                launcherConfig.motd(), launcherConfig.maxPlayers(), launcherConfig.viewDistance(),
                launcherConfig.tickRateHz(), launcherConfig.dataDir(), launcherConfig.gamemode());
        EngineServer server = new EngineServer(config);
        server.start();
        V18ProtocolServer adapter = new V18ProtocolServer(server, 250_000);
        adapter.start(server);

        LinkedBlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        // Raw tap: capture every 0x21 body the adapter writes for (0,0).
        // (Interception via the TestClient's socket read path.)
        // Join a real scripted client and capture the ground-up chunk (0,0)
        // exactly as it goes over the wire, then compare with a direct
        // serialization of the same chunk from the world.
        byte[] wireChunk = null;
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Repro");
            client.readLoginSuccess();
            // Drain login through the chunk wave, capturing the (0,0) chunk.
            long deadline = System.currentTimeMillis() + 20_000;
            boolean sawLook = false;
            while (System.currentTimeMillis() < deadline) {
                byte[] raw = client.readPacketForDiagnostics();
                ByteBuf frame = Unpooled.wrappedBuffer(raw);
                int id = ByteBufOps.readVarInt(frame);
                if (id == Protocol18.S2C_CHUNK_DATA) {
                    int cx = frame.readInt();
                    int cz = frame.readInt();
                    if (cx == 0 && cz == 0 && wireChunk == null) {
                        wireChunk = raw;
                    }
                } else if (id == Protocol18.S2C_PLAYER_POSITION_AND_LOOK) {
                    sawLook = true;
                }
                if (sawLook && wireChunk != null) {
                    break;
                }
            }
        }
        ByteBuf packet = Unpooled.wrappedBuffer(wireChunk);
        ByteBufOps.readVarInt(packet); // the captured frame INCLUDES the packet id
        int x = packet.readInt();
        int z = packet.readInt();
        boolean groundUp = packet.readBoolean();
        short mask = packet.readShort();
        int length = ByteBufOps.readVarInt(packet);
        byte[] wirePayload = new byte[length];
        packet.readBytes(wirePayload);
        StringBuilder head = new StringBuilder();
        for (int i = 0; i < Math.min(24, wireChunk.length); i++) {
            head.append(String.format("%02x ", wireChunk[i]));
        }
        System.out.println("join chunk (" + x + "," + z + ") groundUp=" + groundUp
                + " mask=" + Integer.toBinaryString(mask & 0xFFFF) + " len=" + length
                + " frameLen=" + wireChunk.length + " head=" + head);

        Thread.sleep(800); // a few ticks: the live loop runs (random ticks etc.)
        var world = server.world();
        var chunk = world.peek(new net.zaminmc.torch.block.ChunkPosition(0, 0));
        System.out.println("server-view (0,4,0) = " + world.getBlock(
                new net.zaminmc.torch.block.BlockPosition(0, 4, 0)).identifier());
        System.out.println("server-view (0,1,0) = " + world.getBlock(
                new net.zaminmc.torch.block.BlockPosition(0, 1, 0)).identifier());
        byte[] payload = ChunkSerializer18.serialize(chunk, groundUp);
        int directMask = ChunkSerializer18.sectionBitmask(chunk);
        System.out.println("direct mask=" + Integer.toBinaryString(directMask & 0xFFFF)
                + " len=" + payload.length);
        int idx = (1 << 8); // (0,1,0)
        System.out.println("wire (0,1,0) state="
                + (wirePayload[idx * 2] & 0xFF | (wirePayload[idx * 2 + 1] & 0xFF) << 8));
        System.out.println("direct (0,1,0) state="
                + (payload[idx * 2] & 0xFF | (payload[idx * 2 + 1] & 0xFF) << 8));
        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, wirePayload,
                "the join chunk must equal a direct serialization");
        server.shutdown(adapter::shutdown);
    }
}
