package net.zaminmc.torch.server.concurrent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spatial partition model over its correctness list: the mapping is
 * pure deterministic arithmetic (same chunk → same domain, every call,
 * every thread), the grid is seamless across the origin (floor division),
 * the region boundary splits neighboring chunks, region identity is stable
 * for the engine's whole run, and the domain walk is in stable name order.
 */
class RegionPartitionTest {

    @Test
    void theMappingIsPureDeterministicArithmetic() {
        RegionPartition partition = new RegionPartition("world", 8);
        OwnershipDomain first = partition.domainFor(3, -5);
        OwnershipDomain again = partition.domainFor(3, -5);
        assertSame(first, again, "the same chunk resolves to the same domain object");
        assertEquals(1, partition.regionCount());
    }

    @Test
    void chunksInTheSameRegionShareTheDomain() {
        RegionPartition partition = new RegionPartition("world", 8);
        OwnershipDomain corner = partition.domainFor(0, 0);
        OwnershipDomain inside = partition.domainFor(7, 7);
        assertSame(corner, inside, "chunks 0..7 share region (0,0) for R=8");
    }

    @Test
    void theRegionBoundarySplitsNeighboringChunks() {
        RegionPartition partition = new RegionPartition("world", 8);
        assertNotSame(partition.domainFor(7, 7), partition.domainFor(8, 7),
                "chunk 7 and 8 sit across the region edge");
        assertNotSame(partition.domainFor(7, 7), partition.domainFor(7, 8));
    }

    @Test
    void negativeCoordinatesFloorDivideSeamlessly() {
        RegionPartition partition = new RegionPartition("world", 8);
        // Floor division: chunks -8..-1 share region (-1,-1); -1 never
        // rounds toward zero into region 0.
        assertSame(partition.domainFor(-1, -1), partition.domainFor(-8, -8),
                "the grid is seamless across the world origin");
        assertNotSame(partition.domainFor(-1, -1), partition.domainFor(0, 0),
                "chunk -1 and 0 sit across the region edge");
        long id = partition.regionId(-1, -1);
        assertEquals(-1, RegionPartition.regionXOf(id));
        assertEquals(-1, RegionPartition.regionZOf(id));
    }

    @Test
    void regionNamesCarryTheWorldAndGridCoordinates() {
        RegionPartition partition = new RegionPartition("overworld", 4);
        OwnershipDomain domain = partition.domainFor(5, -3);
        assertEquals("region:overworld:1:-1", domain.name(),
                "the diagnostic identity encodes the grid cell");
    }

    @Test
    void theDomainWalkIsInStableNameOrder() {
        RegionPartition partition = new RegionPartition("world", 1);
        partition.domainFor(2, 0);  // region:world:2:0
        partition.domainFor(-1, 0); // region:world:-1:0
        partition.domainFor(0, 1);  // region:world:0:1
        List<OwnershipDomain> domains = partition.domains();
        assertEquals(3, domains.size());
        assertEquals("region:world:-1:0", domains.get(0).name());
        assertEquals("region:world:0:1", domains.get(1).name());
        assertEquals("region:world:2:0", domains.get(2).name(),
                "the stable name order is the deterministic walk order");
    }

    @Test
    void ownsChecksTheRegionAuthority() {
        RegionPartition partition = new RegionPartition("world", 8);
        OwnershipDomain owner = partition.domainFor(1, 1);
        OwnershipDomain other = partition.domainFor(100, 100);
        assertTrue(partition.owns(1, 1, owner));
        assertTrue(partition.owns(1, 1, owner));
        assertEquals(false, partition.owns(1, 1, other));
    }

    @Test
    void regionSizeIsAConstructorChoice() {
        RegionPartition fine = new RegionPartition("world", 1);
        assertNotSame(fine.domainFor(0, 0), fine.domainFor(1, 0),
                "R=1: every chunk is its own region");
        RegionPartition coarse = new RegionPartition("world", 32);
        assertSame(coarse.domainFor(0, 0), coarse.domainFor(31, 31),
                "R=32: the whole 32x32 block is one region");
        assertEquals(32, coarse.regionSizeChunks());
    }
}
