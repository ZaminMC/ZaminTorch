package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE BLOCK-UPDATE FAMILY'S PER-DIMENSION STORY (Wave FL4-F8 — the FL4-F3
 * wave's newly-ledgered block-update tail): the reference's EVERY
 * WorldServer carries its own scheduled-update machinery ({@code
 * World.updateBlocks} per world — MinecraftServer's own per-world loop),
 * so the gravity fuse, the torch support and the grass rules run in EVERY
 * dimension. The pre-fix engine registered the OVERWORLD's
 * BlockUpdateSystem alone: a nether/End commit reached NO neighbor rules
 * at all — a nether sand column over air floated forever and an
 * unsupported nether torch stood.
 *
 * <p>THE SEATS drive the REAL commit wire (a {@code setBlock} through the
 * ticker — the same listener dispatch every player- and engine-driven
 * change rides): a nether sand whose support vanishes runs the two-tick
 * fuse and CONVERTS into a falling body IN THE NETHER (the overworld's
 * glass marker at the SAME coordinates surviving), and an unsupported
 * nether floor torch POPS as a dimension-stamped item drop in the nether.
 * The falling body's own landing consult (the falling family's physics
 * ground and landing commit) stays the falling family's own named
 * per-dimension row — this wave's ledgered boundary.</p>
 */
class NetherBlockUpdateRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "blockroute" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /**
     * THE GRAVITY FUSE CASE: the nether sand's support vanishes → the
     * two-tick fuse → the conversion (the cell empties IN THE NETHER and a
     * falling body exists), while the overworld's glass marker at the SAME
     * coordinates stands — the pre-fix nether commit woke no machinery and
     * the sand floated forever.
     */
    @Test
    void theNetherSandRunsTheGravityFuseInTheNether() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");

        // The nether platform at y=100 with a stone pillar and the sand on
        // top; the overworld's glass marker at the sand's SAME coordinates.
        int fy = 100;
        BlockPosition pillar = new BlockPosition(64, fy, 64);
        BlockPosition sand = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy - 1, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(pillar, BuiltinBlocks.STONE);
            nether.setBlock(sand, BuiltinBlocks.SAND);
            server.world().setBlock(sand, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.SAND.equals(nether.getBlock(sand))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(sand)),
                40, "the nether sand and the overworld marker landed");

        // The support vanishes (the reference's neighborChanged wake: the
        // sand cell hears the pillar's departure).
        server.ticker().submit(() -> nether.setBlock(pillar, nether.airType()));

        // The two-tick fuse, then the conversion: the sand cell EMPTIES in
        // the nether and a falling body exists.
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(sand).equals(nether.airType()),
                20, "the nether sand converted out of its cell");
        EngineAwaits.awaitCondition(server.world(),
                () -> !server.fallingEntities().all().isEmpty(),
                20, "the falling body exists");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(sand)),
                "the overworld's marker at the same coordinates survives the fuse");
    }

    /**
     * THE TORCH SUPPORT CASE: the nether floor torch's support vanishes →
     * the pop — the cell empties IN THE NETHER and the torch item drop
     * carries the NETHER's dimension stamp, while the overworld's glass
     * marker at the SAME coordinates stands.
     */
    @Test
    void theNetherTorchPopsInTheNether() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");

        int fy = 100;
        BlockPosition floor = new BlockPosition(64, fy, 64);
        BlockPosition torch = new BlockPosition(64, fy + 1, 64);
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 3; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(torch, BuiltinBlocks.TORCH);
            server.world().setBlock(torch, BuiltinBlocks.GLASS);
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.TORCH.equals(nether.getBlock(torch))
                        && BuiltinBlocks.GLASS.equals(server.world().getBlock(torch)),
                40, "the nether torch and the overworld marker landed");

        // The support vanishes: the torch's own wake pops it.
        server.ticker().submit(() -> nether.setBlock(floor, nether.airType()));

        // The pop: the torch cell EMPTIES in the nether and the drop
        // carries the NETHER's stamp (the pre-fix three-arg drop form
        // stranded every foreign pop's item in the overworld).
        EngineAwaits.awaitCondition(server.world(),
                () -> nether.getBlock(torch).equals(nether.airType()),
                20, "the nether torch popped out of its cell");
        EngineAwaits.awaitCondition(server.world(),
                () -> server.itemEntities().all().stream()
                        .anyMatch(item -> item.dimension() == -1
                                && item.stack().type().identifier().value().equals("torch")),
                20, "the torch drop landed with the nether's dimension stamp");
        boolean overworldDrop = server.itemEntities().all().stream()
                .anyMatch(item -> item.dimension() == 0
                        && (int) Math.floor(item.position().x()) == 64
                        && (int) Math.floor(item.position().z()) == 64);
        assertFalse(overworldDrop, "no pop drop stranded in the overworld");
        assertTrue(BuiltinBlocks.GLASS.equals(server.world().getBlock(torch)),
                "the overworld's marker at the same coordinates survives the pop");
    }
}
