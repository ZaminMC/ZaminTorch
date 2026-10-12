package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The PortalForcer's contracts (the 8b-iii-a slice, ported from
 * reference/1.8.8 net/minecraft/server/world/PortalForcer.java + the
 * PortalBlock.findPortalShape match): the shape match reads the frame's
 * geometry (the 2x3 interior of a generated 4x5 frame, the top-left-front
 * corner on the rightmost interior column, the emptier side is the
 * forward), the find walk lands the body inside a hand-built frame with
 * the reference's placement math (the width/height inverse-lerp shares,
 * the clockwise-y width shift, the facing-traded yaw), the per-position
 * cache answers identically on the second ask, the generation arm builds
 * a 4x5 frame (14 obsidian + 6 portal cells) whose interior re-matches,
 * and the 300-second eviction makes a broken portal re-searchable as
 * nothing.
 */
class PortalForcerTest {

    private static FrozenBlockRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    private static EngineWorld world(String name) {
        EngineWorld world = new EngineWorld(name, registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        OwnershipDomain domain = OwnershipDomain.create("simulation:" + name);
        world.attachDomain(domain);
        domain.enter();
        return world;
    }

    /** Builds a 4-wide X-plane frame: obsidian columns at x and x+3, portal interior between. */
    private static void buildXFrame(EngineWorld world, int x, int y, int z) {
        for (int w = 0; w < 4; w++) {
            for (int h = -1; h < 4; h++) {
                boolean frame = w == 0 || w == 3 || h == -1 || h == 3;
                world.setBlock(new BlockPosition(x + w, y + h, z),
                        frame ? BuiltinBlocks.OBSIDIAN : BuiltinBlocks.NETHER_PORTAL);
            }
        }
    }

    @Test
    void shapeMatchReadsTheFrameGeometry() {
        EngineWorld world = world("matched");
        try {
            world.getOrGenerate(new ChunkPosition(0, 0));
            // The 4x5 frame at x 10..13, y 29..33 (interior 11..12 x 30..32), z=10.
            buildXFrame(world, 10, 30, 10);

            PortalFrameBuilder.PortalShapeMatch match =
                    PortalFrameBuilder.matchAt(world, new BlockPosition(11, 30, 10));
            // The interior is 2 wide, 3 tall (the 8a scan's replaceable-cell walk).
            assertEquals(2, match.width());
            assertEquals(3, match.height());
            // The X-plane portal's forward rides the Z axis; both sides read
            // air in the empty flat world — the tie goes POSITIVE (SOUTH).
            assertEquals(PortalFrameBuilder.Axis.Z, match.forwardAxis());
            assertTrue(match.forwardPositive(), "the tie picks the positive side");
            // The top-left-front: the scan's bottomLeft is the RIGHTMOST
            // interior column (the reference's right = WEST walk), climbed
            // to the interior's top row.
            assertEquals(new BlockPosition(12, 32, 10), match.topLeftFront());
            assertEquals(0, match.forwardHorizontalId(), "SOUTH carries the id 0");
        } finally {
            world.domain().exit();
        }
    }

    @Test
    void findWalkLandsInsideAHandBuiltFrame() {
        EngineWorld world = world("found");
        try {
            world.getOrGenerate(new ChunkPosition(0, 0));
            buildXFrame(world, 10, 30, 10);

            PortalForcer forcer = new PortalForcer(world, 1234L, 128);
            PortalForcer.Memory memory = new PortalForcer.Memory();
            memory.offsetAlongWidth = 0.5;
            memory.offsetUp = 0.5;
            memory.lastFacingHorizontalId = 0; // entered facing SOUTH

            // The body at (12.5, 30.5, 12.5): the nearest portal cell is
            // (12, 30, 10) — the squared distance 4 beats (11,30,10)'s 5.
            Optional<PortalForcer.Arrival> arrival = forcer.findNetherPortal(
                    12.5, 30.5, 12.5, 0.0f, 0.0, 0.0, memory);
            assertTrue(arrival.isPresent(), "the search finds the frame");
            // The placement math: destY = top + 1 - offsetUp * height
            // (32 + 1 - 1.5 = 31.5), destX = topLeft.x + (1 - offset) * width
            // * clockwiseYSign (12 + 0.5*2*(-1) = 11.0), destZ centers on the
            // plane (10.5); the yaw trades the entered facing for the found
            // forward (SOUTH -> SOUTH: unchanged).
            assertEquals(11.0, arrival.get().x(), 1.0e-9);
            assertEquals(31.5, arrival.get().y(), 1.0e-9);
            assertEquals(10.5, arrival.get().z(), 1.0e-9);
            assertEquals(0.0f, arrival.get().yaw(), 1.0e-6);
        } finally {
            world.domain().exit();
        }
    }

    @Test
    void cacheAnswersTheSecondAskIdentically() {
        EngineWorld world = world("cached");
        try {
            world.getOrGenerate(new ChunkPosition(0, 0));
            buildXFrame(world, 10, 30, 10);

            PortalForcer forcer = new PortalForcer(world, 1234L, 128);
            PortalForcer.Memory memory = new PortalForcer.Memory();
            memory.offsetAlongWidth = 0.25;
            memory.offsetUp = 0.75;
            Optional<PortalForcer.Arrival> first = forcer.findNetherPortal(
                    12.5, 30.5, 12.5, 45.0f, 0.0, 0.0, memory);
            Optional<PortalForcer.Arrival> second = forcer.findNetherPortal(
                    12.5, 30.5, 12.5, 45.0f, 0.0, 0.0, memory);
            assertTrue(first.isPresent());
            assertEquals(first.get(), second.get(),
                    "the cached hit lands exactly where the search landed");
        } finally {
            world.domain().exit();
        }
    }

    @Test
    void generationBuildsAFrameTheMatchReads() {
        EngineWorld world = world("generated");
        try {
            // The flat world's grass floor at y=3 holds the frame's footing;
            // the build lands its base at y=4 (the first air row). The build
            // site's chunk loads first (the search reads unloaded cells as
            // air — an unloaded floor never validates).
            world.getOrGenerate(new ChunkPosition(62, 62));
            PortalForcer forcer = new PortalForcer(world, 1234L, 128);
            assertTrue(forcer.generateNetherPortal(1000.5, 60.5, 1000.5));

            int portalCells = 0;
            int obsidianCells = 0;
            BlockPosition firstPortal = null;
            for (int x = 995; x <= 1005; x++) {
                for (int y = 2; y <= 9; y++) {
                    for (int z = 995; z <= 1005; z++) {
                        var id = world.getBlock(new BlockPosition(x, y, z)).identifier();
                        if (id.equals(BuiltinBlocks.NETHER_PORTAL.identifier())
                                || id.equals(BuiltinBlocks.NETHER_PORTAL_Z.identifier())) {
                            portalCells++;
                            if (firstPortal == null) {
                                firstPortal = new BlockPosition(x, y, z);
                            }
                        } else if (id.equals(BuiltinBlocks.OBSIDIAN.identifier())) {
                            obsidianCells++;
                        }
                    }
                }
            }
            // The 4x5 rectangle: 14 obsidian frame cells + the 2x3 interior.
            assertEquals(6, portalCells, "the 2x3 interior fills with portal cells");
            assertEquals(14, obsidianCells, "the 4x5 frame carries 14 obsidian cells");
            assertTrue(firstPortal != null, "a portal cell exists to re-match");

            PortalFrameBuilder.PortalShapeMatch match =
                    PortalFrameBuilder.matchAt(world, firstPortal);
            assertEquals(2, match.width(), "the generated interior re-matches 2 wide");
            assertEquals(3, match.height(), "the generated interior re-matches 3 tall");
        } finally {
            world.domain().exit();
        }
    }

    @Test
    void evictionMakesABrokenPortalUnfindable() {
        EngineWorld world = world("evicted");
        try {
            world.getOrGenerate(new ChunkPosition(0, 0));
            buildXFrame(world, 10, 30, 10);

            PortalForcer forcer = new PortalForcer(world, 1234L, 128);
            PortalForcer.Memory memory = new PortalForcer.Memory();
            assertTrue(forcer.findNetherPortal(12.5, 30.5, 12.5, 0.0f, 0.0, 0.0, memory).isPresent(),
                    "the first search finds the frame");

            // The eviction walk: past the 300-second stale band the cached
            // entry drops, and the next ask re-searches the world.
            forcer.tick(400);

            // Break every portal cell — the frame survives, the portal gone.
            for (int w = 1; w <= 2; w++) {
                for (int h = 0; h < 3; h++) {
                    world.setBlock(new BlockPosition(10 + w, 30 + h, 10),
                            world.airType());
                }
            }
            Optional<PortalForcer.Arrival> gone = forcer.findNetherPortal(
                    12.5, 30.5, 12.5, 0.0f, 0.0, 0.0, memory);
            assertFalse(gone.isPresent(), "the broken portal answers nothing");
        } finally {
            world.domain().exit();
        }
    }
}
