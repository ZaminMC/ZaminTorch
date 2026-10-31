package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-007 REMAINING-SLICE SEAT — the TNT cart family
 * (docs/vanilla-conformance-audit.md, the twenty-sixth pass): a TNT
 * minecart detonating in the nether carves the NETHER, never the
 * overworld.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): {@code TntMinecartEntity.
 * explode} line 92 — {@code this.world.explode(this, this.x, this.y,
 * this.z, power, true)} — the cart's OWN world, the destructive flag
 * hardcoded TRUE (the famous exception: a TNT cart craters even in a
 * no-grief world). {@code World.explode} constructs the {@code Explos
 * ion} over that one world: the ray fan reads it, the commits write
 * it, the drops land in it. A nether TNT cart craters the nether; the
 * overworld at the same coordinates never learns of it.</p>
 *
 * <p>THE ENGINE'S SHAPE (develop at the finding): the cart's
 * {@code BlastSink} carried NO dimension and the factory routed
 * through {@code detonateAtWithSource}'s hardcoded
 * {@code (world, explosionService, 0)} triple — the overworld binding
 * FL4-F7 retired for the TNT and F-EX11 retired for the mobs and the
 * projectiles, the cart the last call site. A nether TNT cart's blast
 * tore the OVERWORLD at the same coordinates.</p>
 *
 * <p>This seat is the stash discriminator's RED side on that develop
 * (FAILING UNFIXED at the nether await) and rides GREEN with the
 * routing. Do not re-pin it to the engine's unfixed answer; the
 * finding record owns the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TntCartBlastDimensionRoutingDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cartblast" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** The blast-probe coordinates: a clear column, away from the spawn pad. */
    private static final int PROBE_X = 60;
    private static final int PROBE_Y = 24;
    private static final int PROBE_Z = 60;

    /**
     * Builds the DIRT platform BOTH dimensions carry at the SAME
     * coordinates (9&times;9 at PROBE_Y) and clears the nether's
     * generated terrain above it (the headroom band — the nether's seed
     * drifts across code states, and the construction must not depend
     * on its terrain roll). DIRT's blast resistance (0.5) pays
     * {@code 0.24} per crossing — a resting cart's power-4 blast carves
     * it deep.
     */
    private void buildProbe(EngineWorld overworld, EngineWorld nether) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                overworld.setBlock(new BlockPosition(PROBE_X + dx, PROBE_Y, PROBE_Z + dz),
                        BuiltinBlocks.DIRT);
                nether.setBlock(new BlockPosition(PROBE_X + dx, PROBE_Y, PROBE_Z + dz),
                        BuiltinBlocks.DIRT);
            }
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 1; dy <= 7; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    nether.setBlock(new BlockPosition(PROBE_X + dx, PROBE_Y + dy, PROBE_Z + dz),
                            BuiltinBlocks.AIR);
                }
            }
        }
    }

    /** Counts the carved (air) cells inside the platform's center 5&times;5. */
    private int holedPlatformCells(EngineWorld world) {
        int holes = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (world.getBlock(new BlockPosition(PROBE_X + dx, PROBE_Y, PROBE_Z + dz))
                        .equals(BuiltinBlocks.AIR)) {
                    holes++;
                }
            }
        }
        return holes;
    }

    /**
     * VT-007 (the cart family — the remaining slice): a TNT minecart
     * resting on the nether platform detonates its power-4 blast into
     * the NETHER — the reference's {@code this.world.explode(this,
     * ..., true)} — and the overworld platform at the same coordinates
     * stands whole. The cart spawns through the manager's own factory
     * wiring (the engine's detonation walk, Slice 74's every-spawn
     * arming) and detonates deterministically through
     * {@code detonateAndDie()} (the fuse-free form the arrow and death
     * arms share).
     */
    @Test
    void theNetherTntCartBlastCarvesTheNetherPlatformNeverTheOverworld() throws Exception {
        EngineWorld overworld = server.world();
        EngineWorld nether = server.worldFor(-1);

        for (int cx = (PROBE_X - 8) >> 4; cx <= (PROBE_X + 8) >> 4; cx++) {
            for (int cz = (PROBE_Z - 8) >> 4; cz <= (PROBE_Z + 8) >> 4; cz++) {
                ChunkPosition at = new ChunkPosition(cx, cz);
                if (!nether.isChunkLoaded(at)) {
                    server.requestChunkLoad(nether.dimension(), at, chunk -> { });
                }
            }
        }
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.isChunkLoaded(new ChunkPosition(PROBE_X >> 4, PROBE_Z >> 4)),
                200, "the nether probe chunk loaded");

        server.ticker().submit(() -> buildProbe(overworld, nether));
        await(() -> BuiltinBlocks.DIRT.equals(overworld.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y, PROBE_Z)))
                        && BuiltinBlocks.DIRT.equals(nether.getBlock(
                        new BlockPosition(PROBE_X, PROBE_Y, PROBE_Z))),
                "both dimensions' platforms stand");

        // The nether cart: spawned through the manager (the factory's
        // own sink arming), the dimension stamped by the spawn walk.
        final net.zaminmc.torch.server.entity.vehicle.VehicleEntity[] holder =
                new net.zaminmc.torch.server.entity.vehicle.VehicleEntity[1];
        server.ticker().submit(() -> holder[0] = server.vehicles()
                .spawnTntMinecart(new Position(PROBE_X + 0.5, PROBE_Y + 1, PROBE_Z + 0.5), -1));
        await(() -> holder[0] != null, "the cart spawned");
        assertNotNull(holder[0], "the cart spawned through the factory wiring");
        assertTrue(holder[0].dimension() == -1, "the cart carries the nether stamp");

        // The deterministic detonation: the fuse-free form, the blast
        // synchronous inside the call (the cart's own type carries it).
        server.ticker().submit(() ->
                ((net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity) holder[0])
                        .detonateAndDie());

        // The blast lands in exactly one dimension.
        EngineAwaits.awaitCondition(server.world(),
                () -> holedPlatformCells(nether) > 0 || holedPlatformCells(overworld) > 0,
                300, "the cart detonated and the blast landed");

        // THE VANILLA ANSWER: the nether platform holed, the overworld
        // platform whole. A resting cart's power-4 blast against dirt
        // carves the standing surface around the body.
        int netherHoles = holedPlatformCells(nether);
        int overworldHoles = holedPlatformCells(overworld);
        assertTrue(netherHoles > 0,
                "VT-007 (the cart family): the nether platform takes the"
                        + " blast hole (the reference detonates in the cart's"
                        + " OWN world); observed nether=" + netherHoles
                        + " overworld=" + overworldHoles + " holed cells");
        assertTrue(overworldHoles == 0,
                "VT-007 (the cart family): the overworld platform at the"
                        + " same coordinates stands whole; observed "
                        + overworldHoles + " holed cells — the cross-dimension"
                        + " corruption");
    }
}
