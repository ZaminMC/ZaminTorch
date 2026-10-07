package net.zamin.engine;

import net.zamin.api.BlockPosition;
import net.zamin.api.ChunkPosition;
import net.zamin.api.Identifier;
import net.zamin.engine.block.BlockRegistryBuilder;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zamin.engine.world.EngineChunk;
import net.zamin.engine.world.EngineWorld;
import net.zamin.engine.world.FlatWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldModelTest {

    private static FrozenBlockRegistry registry;
    private static FlatWorldGenerator generator;
    private static EngineWorld world;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        generator = new FlatWorldGenerator(registry, 4);
        // The current thread is the owner in these tests.
        world = new EngineWorld("test", registry, generator, Thread.currentThread());
    }

    @Test
    void flatWorldIsDeterministic() {
        EngineChunk chunk = world.getOrGenerate(new ChunkPosition(0, 0));
        assertEquals(BuiltinBlocks.BEDROCK, chunk.getBlock(0, 0, 0));
        assertEquals(BuiltinBlocks.DIRT, chunk.getBlock(0, 2, 0));
        assertEquals(BuiltinBlocks.GRASS_BLOCK, chunk.getBlock(15, 4, 15));
        assertEquals(BuiltinBlocks.AIR, chunk.getBlock(0, 5, 0));
    }

    @Test
    void generationIsAtomicAndIdempotent() {
        ChunkPosition position = new ChunkPosition(7, -7);
        EngineChunk first = world.getOrGenerate(position);
        EngineChunk second = world.getOrGenerate(position);
        assertSame(first, second, "second getOrGenerate must return the published chunk");
    }

    @Test
    void setBlockThenGetBlockReturnsCommittedState() {
        world.getOrGenerate(new ChunkPosition(20, 20));
        BlockPosition position = new BlockPosition(320, 10, 320); // chunk (20,20), local (0,10,0)
        assertTrue(world.setBlock(position, BuiltinBlocks.STONE));
        assertEquals(BuiltinBlocks.STONE, world.getBlock(position));
        // Writing the same state again is a no-op.
        assertEquals(false, world.setBlock(position, BuiltinBlocks.STONE));
    }

    @Test
    void missingChunkReadsAsAir() {
        assertEquals(BuiltinBlocks.AIR, world.getBlock(new BlockPosition(90_000, 3, 90_000)));
    }

    @Test
    void outOfBoundsYIsRejectedOnSet() {
        assertThrows(IllegalArgumentException.class,
                () -> world.setBlock(new BlockPosition(0, -1, 0), BuiltinBlocks.STONE));
        assertThrows(IllegalArgumentException.class,
                () -> world.setBlock(new BlockPosition(0, 256, 0), BuiltinBlocks.STONE));
    }

    @Test
    void foreignThreadsMayReadButNotMutate() throws Exception {
        // Ownership enforcement: mutation from a non-owner thread fails loudly (§46).
        java.util.concurrent.atomic.AtomicReference<Throwable> thrown = new java.util.concurrent.atomic.AtomicReference<>();
        Thread stranger = new Thread(() -> {
            try {
                world.setBlock(new BlockPosition(0, 200, 0), BuiltinBlocks.STONE);
            } catch (Throwable t) {
                thrown.set(t);
            }
        }, "world-stranger");
        stranger.start();
        stranger.join();
        assertTrue(thrown.get() instanceof IllegalStateException,
                "mutation from non-owner thread must fail loudly, got " + thrown.get());
        // Reads are fine from anywhere.
        assertNotNull(world.peek(new ChunkPosition(0, 0)));
        assertNull(world.peek(new ChunkPosition(999, 999)));
    }
}
