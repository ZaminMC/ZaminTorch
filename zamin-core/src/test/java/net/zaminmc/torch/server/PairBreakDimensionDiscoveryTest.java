package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-009 (the twenty-seventh pass's discovery) — the block-broken
 * listener's PAIR-DEATH WALKS are overworld-bound: {@code
 * EngineServer.doorSiblingCleanup} (lines 14639-14652) and {@code
 * EngineServer.bedSiblingCleanup} (lines 14680-14697) both read and write
 * the {@code world} FIELD — the overworld — regardless of which world the
 * break committed in. The listener they ride ({@code
 * BlockInteractionService.setBlockBrokenListener}, a {@code
 * BiConsumer<BlockPosition, BlockType>}) carries no world, so a break in
 * the NETHER or the END consults the OVERWORLD's cells at the same
 * coordinates for the sibling.
 *
 * <p>THE VANILLA LAW (DoorBlock.neighborChanged lines 156-198,
 * BedBlock.neighborChanged lines 156-170): the surviving half's own
 * {@code neighborChanged} fires IN THE WORLD THE PAIR STANDS IN (the
 * per-world neighbor dispatch — a wrong-world break never reaches it),
 * removing the surviving half there. A bed or door pair broken in the
 * nether dies AS A UNIT in the nether; the overworld at the same
 * coordinates is a DIFFERENT world and is never part of it.</p>
 *
 * <p>THE ENGINE'S TWO WRONG ANSWERS: the nether survivor ORPHANS (its
 * sibling walk looked at another world's cells), and an overworld cell at
 * the sibling's coordinates — an opposite-class half for beds, an upper
 * for doors — is DESTROYED by the nether break: persistent cross-dimension
 * corruption through the ordinary dig wire, the same class as the
 * dig-path family PR #90 fixed (its own record names this listener's
 * cleanups as the remainder).</p>
 *
 * <p>THE SEATS drive the REAL creative break wire ({@code
 * submitCreativeBreak} — the listener fires for creative exactly as for
 * survival): a nether bed pair and a nether door pair, each with an
 * overworld probe at the exact sibling cell the overworld-bound walk
 * consults. Vanilla law: the nether pair dies whole, the overworld probe
 * stands.</p>
 */
class PairBreakDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;
    private EngineWorld nether;
    private EngineWorld over;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "pairbreak" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        nether = server.worldFor(-1);
        over = server.world();
        assertEquals(-1, nether.dimension(), "the nether world carries its dimension row");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession joinCreative(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        server.ticker().submit(() -> session.setGamemode(GameMode.CREATIVE));
        return session;
    }

    /**
     * THE BED SEAT: the nether bed pair's creative break — the foot at
     * (64,101,64), the head south. The overworld probe: a BED HEAD at the
     * HEAD cell's coordinates (64,101,65) — the opposite-class half the
     * overworld-bound sibling walk consults; the overworld glass marker at
     * the broken cell's own coordinates (the routed-dig control). Vanilla:
     * the pair dies WHOLE in the nether (the head's own neighborChanged),
     * the overworld's cells are not part of a nether break.
     */
    @Test
    void theNetherBedBreakKillsItsOwnPairInTheNether() throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(new BlockPosition(64, 101, 64), BuiltinBlocks.BED_FOOT_SOUTH);
            nether.setBlock(new BlockPosition(64, 101, 65), BuiltinBlocks.BED_HEAD_SOUTH);
            over.setBlock(new BlockPosition(64, 101, 65), BuiltinBlocks.BED_HEAD_NORTH);
            over.setBlock(new BlockPosition(64, 101, 64), BuiltinBlocks.GLASS);
        });
        BlockPosition foot = new BlockPosition(64, 101, 64);
        BlockPosition head = new BlockPosition(64, 101, 65);
        EngineAwaits.awaitCondition(over,
                () -> EngineServer.isBedHalf(nether.getBlock(head))
                        && EngineServer.isBedHalf(over.getBlock(head)),
                80, "the nether pair and the overworld probe landed");

        PlayerSession breaker = joinCreative("NetherBedBreaker",
                new Position(66.5, 101.0, 66.5), -1);
        EngineAwaits.awaitCondition(over,
                () -> breaker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the breaker reaches PLAYING");

        server.blockInteraction().submitCreativeBreak(breaker, foot);
        EngineAwaits.awaitCondition(over,
                () -> nether.getBlock(foot).equals(nether.airType()),
                40, "the nether foot's break committed");
        EngineAwaits.awaitTicks(over, 6, "the pair-death walk settles");

        net.zaminmc.torch.block.BlockType netherHead = nether.getBlock(head);
        net.zaminmc.torch.block.BlockType overProbe = over.getBlock(head);
        assertAll(
                () -> assertEquals(nether.airType(), netherHead,
                        "VT-009: the nether pair dies AS A UNIT — the sibling walk reads the dig's own world (BedBlock.neighborChanged's per-world dispatch)"),
                () -> assertEquals(BuiltinBlocks.BED_HEAD_NORTH, overProbe,
                        "VT-009: the overworld's cell at the sibling's coordinates is NOT part of a nether break (the pre-fix walk destroys it there)"),
                () -> assertEquals(BuiltinBlocks.GLASS, over.getBlock(foot),
                        "the routed dig's control: the overworld cell at the broken cell's coordinates stands"),
                () -> assertEquals(-1, breaker.dimension(),
                        "the breaker still stands in the nether"));
    }

    /**
     * THE DOOR SEAT: the nether oak door pair's creative break — the lower
     * at (64,101,64), the upper above. The overworld probe: a COMPLETE
     * oak door pair at the same coordinates — the upper cell (64,102,64)
     * is the one the overworld-bound walk consults. Vanilla: the nether
     * pair dies WHOLE (the upper's neighborChanged pop), the overworld's
     * pair stands untouched.
     */
    @Test
    void theNetherDoorBreakKillsItsOwnPairInTheNether() throws Exception {
        server.ticker().submit(() -> {
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -7; dz <= 7; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                    for (int y = 101; y <= 107; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
            nether.setBlock(new BlockPosition(64, 101, 64), BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_S);
            nether.setBlock(new BlockPosition(64, 102, 64), BuiltinBlocks.OAK_DOOR_UPPER);
            // The overworld probe is a COMPLETE pair at the same coordinates
            // standing on its own dirt floor (an orphan upper pops by the
            // door orphan law, and a lower with air below dies by the
            // support-loss law — the engine's own door neighbor walks).
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    over.setBlock(new BlockPosition(64 + dx, 100, 64 + dz),
                            BuiltinBlocks.DIRT);
                }
            }
            over.setBlock(new BlockPosition(64, 101, 64), BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_S);
            over.setBlock(new BlockPosition(64, 102, 64), BuiltinBlocks.OAK_DOOR_UPPER);
        });
        BlockPosition lower = new BlockPosition(64, 101, 64);
        BlockPosition upper = new BlockPosition(64, 102, 64);
        EngineAwaits.awaitCondition(over,
                () -> net.zaminmc.torch.server.block.WoodDoorFamily.isDoor(nether.getBlock(upper))
                        && net.zaminmc.torch.server.block.WoodDoorFamily.isDoor(over.getBlock(upper)),
                80, "the nether door and the overworld probe landed");

        PlayerSession breaker = joinCreative("DoorBreaker",
                new Position(66.5, 101.0, 66.5), -1);
        EngineAwaits.awaitCondition(over,
                () -> breaker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the breaker reaches PLAYING");

        server.blockInteraction().submitCreativeBreak(breaker, lower);
        EngineAwaits.awaitCondition(over,
                () -> nether.getBlock(lower).equals(nether.airType()),
                40, "the nether lower's break committed");
        EngineAwaits.awaitTicks(over, 6, "the pair-death walk settles");

        net.zaminmc.torch.block.BlockType netherUpper = nether.getBlock(upper);
        net.zaminmc.torch.block.BlockType overProbe = over.getBlock(upper);
        assertAll(
                () -> assertEquals(nether.airType(), netherUpper,
                        "VT-009: the nether door pair dies AS A UNIT — the sibling walk reads the dig's own world (DoorBlock.neighborChanged's per-world dispatch)"),
                () -> assertEquals(BuiltinBlocks.OAK_DOOR_UPPER, overProbe,
                        "VT-009: the overworld's upper at the same coordinates is NOT part of a nether break (the pre-fix walk destroys it there)"));
    }

    /**
     * THE OVERWORLD GUARD: the same pair death in the OVERWORLD — the
     * walk's own home world — must hold (the bed's head falls with its
     * foot, the foreign pair untouched). Pins the overworld arm against
     * regressions the fix could introduce.
     */
    @Test
    void theOverworldBedBreakStillKillsThePair() throws Exception {
        BlockPosition foot = new BlockPosition(20, 5, 0);
        BlockPosition head = new BlockPosition(20, 5, 1);
        server.ticker().submit(() -> {
            over.setBlock(foot, BuiltinBlocks.BED_FOOT_SOUTH);
            over.setBlock(head, BuiltinBlocks.BED_HEAD_SOUTH);
        });
        EngineAwaits.awaitCondition(over,
                () -> EngineServer.isBedHalf(over.getBlock(head)),
                80, "the overworld pair landed");

        PlayerSession breaker = joinCreative("HomeBedBreaker",
                new Position(20.5, 5.0, 3.5), 0);
        EngineAwaits.awaitCondition(over,
                () -> breaker.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the breaker reaches PLAYING");

        server.blockInteraction().submitCreativeBreak(breaker, foot);
        EngineAwaits.awaitCondition(over,
                () -> over.getBlock(foot).equals(over.airType()),
                40, "the foot's break committed");
        EngineAwaits.awaitTicks(over, 6, "the pair-death walk settles");

        assertTrue(over.getBlock(head).equals(over.airType()),
                "the overworld pair dies as a unit (the guard: the fix must not touch the home-world arm)");
    }
}
