package net.zamin.protocol.v1_8;

import io.netty.buffer.Unpooled;
import net.zamin.api.BlockPosition;
import net.zamin.api.ChunkPosition;
import net.zamin.engine.block.BlockRegistryBuilder;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.world.EngineWorld;
import net.zamin.engine.world.FlatWorldGenerator;
import net.zamin.engine.world.light.LightEngine;

import java.io.DataOutputStream;
import java.io.FileOutputStream;

/**
 * Dumps the exact wire bytes of a chunk re-send after a dig, so the layout can
 * be hand-verified against mineflayer's parser. One-off diagnostic (run
 * manually): java -cp ... DumpChunkWire /tmp/chunk-dump.bin
 */
public final class DumpChunkWire {

    public static void main(String[] args) throws Exception {
        var registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        var world = new EngineWorld("dump", registry, new FlatWorldGenerator(registry, 4),
                Thread.currentThread());
        var light = new LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new ChunkPosition(0, 0));
        // Commit a dig like the probe did (the committed change fires the
        // engine's relight), then serialize the chunk the resend would carry.
        world.setBlock(new BlockPosition(0, 4, 0), world.airType());
        var chunk = world.peek(new ChunkPosition(0, 0));
        byte[] payload = ChunkSerializer18.serialize(chunk, false);
        int mask = ChunkSerializer18.sectionBitmask(chunk);

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(args[0]))) {
            out.writeInt(mask);
            out.writeInt(payload.length);
            out.write(payload);
        }
        System.out.println("mask=0b" + Integer.toBinaryString(mask) + " payload=" + payload.length);
        // Hand-parse: section 0's block array, verify the dug cell + neighbors.
        int off = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 2; z++) {
                for (int x = 0; x < 2; x++) {
                    int state = (payload[off + (y * 16 + z) * 16 * 2 + x * 2] & 0xFF)
                            | (payload[off + (y * 16 + z) * 16 * 2 + x * 2 + 1] & 0xFF) << 8;
                    if (x == 0 && z == 0 && y < 8) {
                        System.out.println("cell(0," + (y) + ",0) state=" + state
                                + " id=" + (state >> 4));
                    }
                }
            }
        }
        int lightOff = Integer.bitCount(mask) * 8192;
        int skyOff = lightOff + Integer.bitCount(mask) * 2048;
        int idx = (4 << 8) | (0 << 4) | 0; // (x=0,y=4,z=0)
        int skyByte = payload[skyOff + (idx >> 1)] & 0xFF;
        int sky = (idx & 1) == 0 ? skyByte & 0xF : (skyByte >> 4) & 0xF;
        System.out.println("sky(0,4,0)=" + sky + " (0 expected: the dug cell is now open... "
                + "it refilled to 15)");
        int idxAbove = (5 << 8) | 0;
        int skyByteAbove = payload[skyOff + (idxAbove >> 1)] & 0xFF;
        System.out.println("sky(0,5,0)="
                + (((idxAbove & 1) == 0 ? skyByteAbove & 0xF : (skyByteAbove >> 4) & 0xF)));
    }
}
