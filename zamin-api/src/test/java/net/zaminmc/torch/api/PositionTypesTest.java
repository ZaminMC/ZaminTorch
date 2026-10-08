package net.zaminmc.torch.api;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PositionTypesTest {

    @Test
    void chunkPositionFloorsNegativeBlockCoordinates() {
        assertEquals(new ChunkPosition(-1, -1), ChunkPosition.ofBlock(-1, -1));
        assertEquals(new ChunkPosition(-1, 0), ChunkPosition.ofBlock(-16, 15));
        assertEquals(new ChunkPosition(0, 0), ChunkPosition.ofBlock(0, 0));
        assertEquals(new ChunkPosition(1, 2), ChunkPosition.ofBlock(16, 32));
    }

    @Test
    void packedPositionRoundTrips() {
        ChunkPosition pos = new ChunkPosition(-12345, 67890);
        assertEquals(pos, ChunkPosition.unpack(pos.packed()));
    }

    @Test
    void blockPositionRejectsInvalidY() {
        assertThrows(IllegalArgumentException.class, () -> new BlockPosition(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlockPosition(0, 256, 0));
    }

    @Test
    void localCoordinatesAreAlwaysWithinChunk() {
        assertEquals(15, new BlockPosition(-1, 0, -1).localX());
        assertEquals(15, new BlockPosition(-1, 0, -1).localZ());
        assertEquals(0, new BlockPosition(16, 0, 32).localX());
        assertEquals(0, new BlockPosition(16, 0, 32).localZ());
    }

    @Test
    void finitePositionDetection() {
        assertEquals(true, new Position(0, 0, 0).isFinite());
        assertEquals(false, new Position(Double.NaN, 0, 0).isFinite());
        assertEquals(false, new Position(0, Double.POSITIVE_INFINITY, 0).isFinite());
    }
}
