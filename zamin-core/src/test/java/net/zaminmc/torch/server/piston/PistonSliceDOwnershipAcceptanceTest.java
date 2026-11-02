package net.zaminmc.torch.server.piston;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE PISTON FAMILY'S PER-DIMENSION STORY (FL4-GAP2 Slice D — the transient
 * families' row): the reference gives EVERY ServerWorld its own
 * ServerBlockEventList and its own piston machinery, so a piston runs on
 * ITS OWN world's power and changes, and its moving carrier sweeps only
 * ITS OWN world's bodies (MovingBlockEntity.moveEntities consults the
 * acting World's entity lists — a body in another dimension is not in the
 * carrier's sweep at all). The pre-Slice engine registered the OVERWORLD's
 * PistonSystem alone: a nether piston had NO system behind it (it never
 * ran on nether power at all), and the overworld carrier's push surface
 * scanned EVERY dimension's bodies — an overworld piston extending at
 * coordinates where a nether mob stood displaced it across the worlds.
 *
 * <p>THE SEATS: the nether piston running on nether power displacing only
 * the nether's bodies; the overworld piston never displacing a nether
 * body at the same coordinates; and the nether piston never arming on
 * overworld power (the neighbor dispatch never crosses worlds).</p>
 */
class PistonSliceDOwnershipAcceptanceTest {

    /** The reference direction id for EAST (the seats push along +X). */
    private static final int EAST = 5;

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pistonsliced" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * THE CORE SLICE D CASE: the nether piston runs on NETHER power (the
     * per-dimension family — before the slice there was no system behind
     * a nether piston at all), and its carrier displaces only the NETHER's
     * bodies: the overworld pig standing at the SAME coordinates on its
     * own platform never moves, and the overworld cells never see the
     * flight's writes.
     */
    @Test
    void theNetherPistonDisplacesOnlyTheNetherBodies() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        // Matching platforms in BOTH worlds at the same coordinates — each
        // pig stands on its own world's floor, so only the push can move
        // anything along X.
        platform(nether, cx, cz, fy);
        platform(server.world(), cx, cz, fy);

        BlockPosition piston = new BlockPosition(cx, fy + 1, cz);
        BlockPosition pushed = new BlockPosition(cx + 1, fy + 1, cz);
        BlockPosition lever = new BlockPosition(cx, fy + 3, cz);
        BlockPosition landing = new BlockPosition(cx + 2, fy + 1, cz);
        server.ticker().submit(() -> {
            nether.setBlock(piston, PistonBlocks.pistonOf(EAST, false, false));
            nether.setBlock(pushed, BuiltinBlocks.STONE);
            // The vanilla-valid mount (PistonBaseBlock.isCube() false — a
            // lever never rides the piston base): the stone above carries
            // the lever, the flip's strong power entering the stone
            // re-radiating to the piston below.
            nether.setBlock(new BlockPosition(cx, fy + 2, cz), BuiltinBlocks.STONE);
            nether.setBlock(lever, RedstoneBlocks.leverOf(5, false));
        });
        await(() -> PistonBlocks.isPiston(typeAt(nether, piston)), "the nether piston placed");

        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> netherPig =
                new AtomicReference<>();
        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> overworldPig =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            netherPig.set(server.mobs().spawnAt(MobType.PIG,
                    new Position(cx + 2.5, fy + 1, cz + 0.5)));
            netherPig.get().setDimension(-1); // the body lives in the nether
            overworldPig.set(server.mobs().spawnAt(MobType.PIG,
                    new Position(cx + 2.5, fy + 1, cz + 0.5)));
        });
        await(() -> netherPig.get() != null && overworldPig.get() != null,
                "the two pigs spawned at the same coordinates");
        double netherBefore = netherPig.get().position().x();
        double overworldBefore = overworldPig.get().position().x();

        // The NETHER's lever, on the NETHER's system — the flip arms the
        // nether's own piston family.
        server.ticker().submit(() -> server.redstoneFor(-1).useLever(lever));
        await(() -> PistonBlocks.pistonExtended(typeAt(nether, piston)),
                "the nether piston flipped to its extended pair (nether power arms it)");
        await(() -> BuiltinBlocks.STONE.equals(typeAt(nether, landing)),
                "the pushed stone landed one cell along in the nether");

        // The flight settles, then the discrimination: the nether pig rode
        // the carrier east, the overworld pig at the SAME coordinates
        // never moved (the push surface gates on the body's own world).
        EngineAwaits.awaitTicks(server.world(), 6, "the flight settles");
        assertTrue(netherPig.get().position().x() > netherBefore + 0.2,
                "the nether pig rode the flight east (before " + netherBefore
                        + ", after " + netherPig.get().position().x() + ")");
        assertEquals(overworldBefore, overworldPig.get().position().x(), 1.0E-9,
                "the overworld pig at the same coordinates never moved (before "
                        + overworldBefore + ", after "
                        + overworldPig.get().position().x() + ")");
        // The flight's writes never touched the overworld's cells: the
        // landing cell in the OVERWORLD is still the platform's air.
        assertEquals(server.world().airType(), typeAt(server.world(), landing),
                "the overworld's landing cell at the same coordinates is still air");
    }

    /**
     * THE MIRROR: the overworld piston's carrier never displaces a NETHER
     * body at the same coordinates — the push surface's dimension gate
     * cuts both ways.
     */
    @Test
    void theOverworldPistonNeverDisplacesTheNetherBody() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        platform(nether, cx, cz, fy);
        platform(server.world(), cx, cz, fy);

        BlockPosition piston = new BlockPosition(cx, fy + 1, cz);
        BlockPosition pushed = new BlockPosition(cx + 1, fy + 1, cz);
        BlockPosition lever = new BlockPosition(cx, fy + 3, cz);
        BlockPosition landing = new BlockPosition(cx + 2, fy + 1, cz);
        server.ticker().submit(() -> {
            server.world().setBlock(piston, PistonBlocks.pistonOf(EAST, false, false));
            server.world().setBlock(pushed, BuiltinBlocks.STONE);
            // The vanilla-valid mount (PistonBaseBlock.isCube() false — a
            // lever never rides the piston base): the stone above carries
            // the lever, the flip's strong power entering the stone
            // re-radiating to the piston below.
            server.world().setBlock(new BlockPosition(cx, fy + 2, cz), BuiltinBlocks.STONE);
            server.world().setBlock(lever, RedstoneBlocks.leverOf(5, false));
        });
        await(() -> PistonBlocks.isPiston(typeAt(server.world(), piston)),
                "the overworld piston placed");

        AtomicReference<net.zaminmc.torch.server.entity.MobEntity> netherPig =
                new AtomicReference<>();
        server.ticker().submit(() -> {
            netherPig.set(server.mobs().spawnAt(MobType.PIG,
                    new Position(cx + 2.5, fy + 1, cz + 0.5)));
            netherPig.get().setDimension(-1); // the body lives in the nether
        });
        await(() -> netherPig.get() != null, "the nether pig spawned in the flight path");
        double before = netherPig.get().position().x();

        server.ticker().submit(() -> server.redstone().useLever(lever));
        await(() -> PistonBlocks.pistonExtended(typeAt(server.world(), piston)),
                "the overworld piston flipped to its extended pair");
        await(() -> BuiltinBlocks.STONE.equals(typeAt(server.world(), landing)),
                "the pushed stone landed one cell along in the overworld");
        EngineAwaits.awaitTicks(server.world(), 6, "the flight settles");
        assertEquals(before, netherPig.get().position().x(), 1.0E-9,
                "the nether pig at the same coordinates never moved (before "
                        + before + ", after " + netherPig.get().position().x() + ")");
        // The flight's writes never touched the nether's cells.
        assertEquals(nether.airType(), typeAt(nether, landing),
                "the nether's landing cell at the same coordinates is still air");
    }

    /**
     * THE POWER ROUTING: the nether piston never arms on OVERWORLD power —
     * the lever in the overworld at the same coordinates flips, the nether
     * piston stays plain over the whole window (the neighbor dispatch
     * never crosses worlds), and a nether lever afterwards arms it (the
     * positive control).
     */
    @Test
    void theNetherPistonNeverArmsOnOverworldPower() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        platform(nether, cx, cz, fy);
        platform(server.world(), cx, cz, fy);

        BlockPosition piston = new BlockPosition(cx, fy + 1, cz);
        BlockPosition pushed = new BlockPosition(cx + 1, fy + 1, cz);
        BlockPosition overworldLever = new BlockPosition(cx, fy + 3, cz);
        server.ticker().submit(() -> {
            nether.setBlock(piston, PistonBlocks.pistonOf(EAST, false, false));
            nether.setBlock(pushed, BuiltinBlocks.STONE);
            server.world().setBlock(pushed, BuiltinBlocks.STONE);
            // The overworld lever's own vanilla-valid mount (the stone
            // above the same coordinates' air — the floating stone never
            // falls): the flip radiates OVERWORLD power only.
            server.world().setBlock(new BlockPosition(cx, fy + 2, cz), BuiltinBlocks.STONE);
            server.world().setBlock(overworldLever, RedstoneBlocks.leverOf(5, false));
        });
        await(() -> PistonBlocks.isPiston(typeAt(nether, piston)),
                "the nether piston placed");

        // The OVERWORLD lever flips at the same coordinates: the nether
        // piston must stay plain over the whole 40-tick window.
        server.ticker().submit(() -> server.redstone().useLever(overworldLever));
        EngineAwaits.awaitTicks(server.world(), 40, "the overworld-power window");
        assertFalse(PistonBlocks.pistonExtended(typeAt(nether, piston)),
                "the nether piston never extended on overworld power");
        assertEquals(nether.airType(), typeAt(nether, new BlockPosition(cx + 2, fy + 1, cz)),
                "the nether's landing cell is untouched (no flight ever ran)");

        // The positive control: a NETHER lever at the same coordinates arms
        // the nether's own family.
        BlockPosition netherLever = new BlockPosition(cx, fy + 3, cz);
        server.ticker().submit(() -> {
            nether.setBlock(netherLever, RedstoneBlocks.leverOf(5, false));
            nether.setBlock(new BlockPosition(cx, fy + 2, cz), BuiltinBlocks.STONE);
        });
        await(() -> RedstoneBlocks.isLever(typeAt(nether, netherLever)),
                "the nether lever placed on its support");
        server.ticker().submit(() -> server.redstoneFor(-1).useLever(netherLever));
        await(() -> PistonBlocks.pistonExtended(typeAt(nether, piston)),
                "the nether piston extended on nether power (the positive control)");
    }

    // ------------------------------------------------------------------
    // The harness
    // ------------------------------------------------------------------

    /** A stone floor at y=100 with cleared air above, in the given world. */
    private void platform(EngineWorld world, int cx, int cz, int fy) throws InterruptedException {
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 4; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    world.setBlock(new BlockPosition(cx + dx, fy, cz + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 5; y++) {
                        world.setBlock(new BlockPosition(cx + dx, y, cz + dz),
                                world.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(cx, fy, cz))),
                40, "the platform landed in " + world.dimension());
    }

    private BlockType typeAt(EngineWorld world, BlockPosition at) {
        return world.getBlock(at);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
