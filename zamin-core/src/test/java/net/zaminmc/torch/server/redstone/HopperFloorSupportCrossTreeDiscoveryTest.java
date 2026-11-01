package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.hopper.HopperBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hopper family arm's regression pin — the HOPPER row of
 * {@code World.hasSolidTop} (World.java lines 1740-1752, the FOURTH arm:
 * {@code block instanceof HopperBlock} — every state of the block answers
 * TRUE): a standing torch, a floor lever, a wire, a plate and a repeater
 * each SURVIVE on a hopper in vanilla.
 *
 * <p>THE PIN'S HISTORY: the row first surfaced as the #138 carrier's DEAD
 * fourth arm ({@code value.equals("hopper")} ALONE — the engine registers
 * NO bare {@code minecraft:hopper} block, only the
 * {@code hopper_<facing>} / {@code hopper_<facing>_disabled} states,
 * HopperBlocks.buildStates), the cross-tree evidence of the forty-seventh
 * pass (https://github.com/ZaminMC/ZaminTorch/pull/138#issuecomment-6556729971).
 * The merged #139 carrier answered it with the family form
 * ({@code value.startsWith("hopper_")}); this seat now PINS the row
 * through every consult that reads it — the F-SP1 mount walks
 * ({@code torchSupported}/{@code leverSupported} over
 * {@code SupportConsult.hasSolidTop}) and the VT-029 floor walks
 * ({@code wireFloorHolds}/{@code plateFloorHolds}/{@code diodeFloorHolds}
 * over the same consult): the five members each hold on the hopper's own
 * states, the dead-arm regression caught at the neighbor update where
 * vanilla holds them.</p>
 */
class HopperFloorSupportCrossTreeDiscoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void theFamiliesSurviveOnTheHopper() throws Exception {
        EngineServer server = boot();
        try {
            int x = 500, z = 500, y = 40;
            BlockType hopper = HopperBlocks.hopperOf(0, true);
            BlockType torch = RedstoneBlocks.torchOfFacing(1, true);
            BlockType lever = RedstoneBlocks.leverOf(5, false);
            BlockType wire = RedstoneBlocks.wireOfPower(0);
            BlockType plate = RedstoneBlocks.plateOf(false, false);
            BlockType repeater = RedstoneBlocks.repeaterOf(0, 1, false);
            // Five hoppers side by side, each carrying one family member.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), hopper);
                server.world().setBlock(new BlockPosition(x, y, z), torch);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 3), hopper);
                server.world().setBlock(new BlockPosition(x, y, z + 3), lever);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 6), hopper);
                server.world().setBlock(new BlockPosition(x, y, z + 6), wire);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 9), hopper);
                server.world().setBlock(new BlockPosition(x, y, z + 9), plate);
                server.world().setBlock(new BlockPosition(x, y - 1, z + 12), hopper);
                server.world().setBlock(new BlockPosition(x, y, z + 12), repeater);
            });
            await(() -> torch.equals(typeAt(server, x, y, z)),
                    "the torch placed on its hopper floor");
            await(() -> lever.equals(typeAt(server, x, y, z + 3)),
                    "the lever placed on its hopper floor");
            await(() -> wire.equals(typeAt(server, x, y, z + 6)),
                    "the wire placed on its hopper floor");
            await(() -> plate.equals(typeAt(server, x, y, z + 9)),
                    "the plate placed on its hopper floor");
            await(() -> repeater.equals(typeAt(server, x, y, z + 12)),
                    "the repeater placed on its hopper floor");

            // The trigger: a stone arriving above each family cell wakes
            // every survival consult (the neighborChanged arms re-read
            // their floors).
            server.ticker().submit(() -> {
                for (int dz = 0; dz <= 12; dz += 3) {
                    server.world().setBlock(new BlockPosition(x, y + 1, z + dz),
                            BuiltinBlocks.STONE);
                }
            });
            sleep(1500);

            // Vanilla (hasSolidTop's fourth arm — every HopperBlock
            // state): ALL FIVE survive. The #138 carrier: the dead arm
            // fells each of them.
            assertTrue(torch.equals(typeAt(server, x, y, z)),
                    "HOPPER ROW: the standing torch survives on the hopper "
                            + "(hasSolidTop's HopperBlock arm)");
            assertTrue(lever.equals(typeAt(server, x, y, z + 3)),
                    "HOPPER ROW: the floor lever survives on the hopper");
            assertTrue(wire.equals(typeAt(server, x, y, z + 6)),
                    "HOPPER ROW: the wire survives on the hopper");
            assertTrue(plate.equals(typeAt(server, x, y, z + 9)),
                    "HOPPER ROW: the plate survives on the hopper");
            assertTrue(repeater.equals(typeAt(server, x, y, z + 12)),
                    "HOPPER ROW: the repeater survives on the hopper");
        } finally {
            server.shutdown(() -> { });
        }
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "scp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
