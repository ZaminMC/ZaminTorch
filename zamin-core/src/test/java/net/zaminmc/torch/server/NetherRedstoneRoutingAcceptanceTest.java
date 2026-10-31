package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
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
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE REDSTONE FAMILY'S PER-DIMENSION STORY (Wave FL4-F10): the
 * reference's EVERY ServerWorld carries its own signal machinery — the
 * wire cascade rides the per-world commit listener, the torch's 2-tick
 * reaction and the repeater's delay ride the per-world scheduled-tick
 * queue, the plates' occupancy probes range the per-world entity lists
 * (World.getEntities walks the ACTING world's chunks alone), and the
 * comparator's stored output rows carry their world (the loaded
 * ComparatorBlockEntity answering its own world's stored strength). The
 * pre-fix engine registered the OVERWORLD's RedstoneSystem alone: a
 * nether lever's flip, a nether comparator's read, a nether repeater's
 * delay and a nether plate's scan had NO system behind them at all, and
 * the overworld's probes answered for bodies in EVERY dimension.
 *
 * <p>THE SEATS drive the real wires: the lever through the item-use
 * dispatch with a NETHER session (the use consult's own routing), the
 * repeater and the comparator through their systems' own consults on the
 * tick lane, the plate through the engine's mob walk, and the comparator's
 * stored output across a REAL restart (the ZCD v2 stamp's round trip).
 * The overworld regression lanes stay whole (the family's own suites).</p>
 */
class NetherRedstoneRoutingAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The restart seat's fixed world name (the store rides the world's own
     *  data dir — the two boots must name the SAME world). */
    private static final String WORLD_NAME = "rsroute";

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, int dimension) throws InterruptedException {
        var accepted = server.joinRequest(new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        }, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        session.setDimension(dimension);
        EngineAwaits.awaitCondition(server.world(),
                () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                40, "the player playing");
        return session;
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

    private static BlockType typeAt(EngineServer server, EngineWorld world,
                                    BlockPosition at) {
        java.util.concurrent.atomic.AtomicReference<BlockType> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.ticker().submit(() -> result.set(world.getBlock(at)));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result.get();
    }

    /** The deterministic chamber: a stone floor at y=100, cleared above, in the nether. */
    private void netherChamber(EngineWorld nether, int cx, int cz) throws InterruptedException {
        int fy = 100;
        server.ticker().submit(() -> {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    nether.setBlock(new BlockPosition(cx + dx, fy, cz + dz),
                            BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 4; y++) {
                        nether.setBlock(new BlockPosition(cx + dx, y, cz + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(cx, fy, cz))),
                40, "the nether chamber landed");
    }

    /**
     * THE USE-CONSULT SEAT: a nether lever's flip opens a nether door —
     * the item-use dispatch consults the ACTING dimension's system (the
     * pre-fix consult armed the overworld's cascade at nether
     * coordinates, and the nether's own door never heard the signal).
     */
    @Test
    void theNetherLeverFlipsThroughTheUseConsultAndOpensTheNetherDoor() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        netherChamber(nether, cx, cz);
        PlayerSession netherPlayer = join("NetherSwitch", -1);
        netherPlayer.applyMovement(new Position(cx + 0.5, fy + 1, cz + 0.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);

        // The lever on the floor, the door one cell east (a wall mount the
        // lever's weak arm reaches), both IN THE NETHER.
        BlockPosition lever = new BlockPosition(cx, fy + 1, cz);
        BlockPosition door = new BlockPosition(cx + 1, fy + 1, cz);
        server.ticker().submit(() -> {
            nether.setBlock(lever, RedstoneBlocks.leverOf(5, false));
            nether.setBlock(door, net.zaminmc.torch.server.block.WoodDoorFamily.lower(
                    "oak", 3, false));
            nether.setBlock(door.offset(0, 1, 0),
                    net.zaminmc.torch.server.block.WoodDoorFamily.upper(
                            "oak", false, false, false));
        });
        await(() -> RedstoneBlocks.isLever(typeAt(server, nether, lever)), "the lever placed");
        await(() -> isDoorLower(typeAt(server, nether, door)), "the door placed");

        // The flip through the REAL use wire: the nether session's consult.
        server.useItemOnBlock(netherPlayer, lever, 1, java.util.Optional.empty(),
                windowId -> { });

        await(() -> RedstoneBlocks.leverPowered(typeAt(server, nether, lever)),
                "the lever toggled powered through the use consult");
        // The door's signal arm heard the lever's weak 15: the lower half
        // stands OPEN (the pre-fix nether flip armed no system at all).
        await(() -> net.zaminmc.torch.server.block.WoodDoorFamily.isOpen(
                typeAt(server, nether, door)),
                "the nether door opened on the lever's signal");
        assertTrue(net.zaminmc.torch.server.block.WoodDoorFamily.isOpen(
                        typeAt(server, nether, door)),
                "the nether door's lower half stands open");
    }

    /**
     * THE PROBE-FILTER SEAT: a nether pressure plate under a nether mob's
     * feet triggers, and an OVERWORLD body at the SAME coordinates never
     * triggers it (the per-world entity-list law — the pre-fix probe
     * walked every dimension's bodies).
     */
    @Test
    void theNetherPlateTriggersForTheNetherMobAlone() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        netherChamber(nether, cx, cz);

        BlockPosition plate = new BlockPosition(cx, fy + 1, cz);
        server.ticker().submit(() ->
                nether.setBlock(plate, RedstoneBlocks.plateOf(false, false)));
        await(() -> RedstoneBlocks.isPlate(typeAt(server, nether, plate)), "the plate placed");

        // The overworld body standing at the SAME coordinates: the plate
        // stays cold (another world's body is absent from the walk).
        PlayerSession overworldBody = join("OverworldStand", 0);
        overworldBody.applyMovement(new Position(cx + 0.5, fy + 1, cz + 0.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 40, "the overworld body settles");
        assertFalse(RedstoneBlocks.platePowered(typeAt(server, nether, plate)),
                "the overworld body at the same coordinates never triggers the nether plate");

        // The nether mob's feet on the plate: it powers (the plate's own
        // re-compute rides the 20-tick debounce). The body's dimension
        // stamp rides the spawn (the nether blast seat's own form).
        var mob = new java.util.concurrent.atomic.AtomicReference<
                net.zaminmc.torch.server.entity.MobEntity>();
        server.ticker().submit(() -> {
            mob.set(server.mobs().spawnAt(MobType.PIG,
                    new Position(cx + 0.5, fy + 1, cz + 0.5)));
            mob.get().setDimension(-1); // the body lives in the nether
        });
        await(() -> mob.get() != null, "the nether mob spawned");
        // The mob's dimension stamp rides the spawn (the nether ground).
        await(() -> RedstoneBlocks.platePowered(typeAt(server, nether, plate)),
                "the nether mob's feet power the nether plate");
        assertTrue(RedstoneBlocks.platePowered(typeAt(server, nether, plate)),
                "the nether plate stands powered");
    }

    /**
     * THE SCHEDULED-TICK SEAT: a nether repeater carries its delay — the
     * nether system's own scheduled-tick queue drains (the pre-fix drain
     * ran the overworld's queue alone, so a nether repeater's output
     * never flipped).
     */
    @Test
    void theNetherRepeaterCarriesItsDelay() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        netherChamber(nether, cx, cz);

        // The classic source shape (the overworld seat's own topology, the
        // family's FACING constants): a lit standing torch strongly powers
        // the block above it, the block re-radiates to the dust, the dust
        // runs to the repeater's input face, the repeater FACING south
        // (input at +Z) carries its 2-tick delay.
        BlockPosition torch = new BlockPosition(cx, fy + 1, cz);
        BlockPosition fed = new BlockPosition(cx, fy + 2, cz);
        BlockPosition dust1 = new BlockPosition(cx, fy + 3, cz);
        BlockPosition repeater = new BlockPosition(cx, fy + 3, cz - 1);
        BlockPosition dust2 = new BlockPosition(cx, fy + 3, cz - 2);
        server.ticker().submit(() -> {
            nether.setBlock(torch, RedstoneBlocks.torchStandingLit());
            nether.setBlock(fed, BuiltinBlocks.STONE);
            nether.setBlock(dust1, RedstoneBlocks.wireOfPower(0));
            // The repeater FACING south (its input face at +Z = the dust's
            // cell), the 1-step delay (2 game ticks).
            nether.setBlock(repeater,
                    RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_SOUTH, 1, false));
            nether.setBlock(new BlockPosition(cx, fy + 2, cz - 1), BuiltinBlocks.STONE);
            nether.setBlock(new BlockPosition(cx, fy + 2, cz - 2), BuiltinBlocks.STONE);
            nether.setBlock(dust2, RedstoneBlocks.wireOfPower(0));
        });
        await(() -> RedstoneBlocks.isWire(typeAt(server, nether, dust1))
                && RedstoneBlocks.wirePower(typeAt(server, nether, dust1)) == 15,
                "the nether torch-fed dust reads 15");
        // The repeater arms its 2-tick reaction and swaps to the powered
        // pair — the delay drains on the NETHER system's own queue (the
        // pre-fix drain ran the overworld's queue alone: the repeater
        // never flipped).
        await(() -> RedstoneBlocks.repeaterPowered(typeAt(server, nether, repeater)),
                "the nether repeater turned on after its 2-tick delay");
        assertTrue(RedstoneBlocks.repeaterPowered(typeAt(server, nether, repeater)),
                "the nether repeater stands powered");
        // The output dust reads the repeater's strong 15.
        await(() -> RedstoneBlocks.wirePower(typeAt(server, nether, dust2)) == 15,
                "the output dust reads the repeater's strong 15");
    }

    /**
     * THE STORE-STAMP SEAT: a nether comparator's stored output survives
     * the restart — the ZCD v2 row carries the dimension, the restore
     * routes the row to the NETHER's system (a v1 row's overworld law
     * would have landed the strength on the overworld's system at the
     * same coordinates).
     */
    @Test
    void theNetherComparatorsStoredOutputSurvivesTheRestart() throws Exception {
        EngineWorld nether = server.worldFor(-1);
        int fy = 100;
        int cx = 64, cz = 64;
        netherChamber(nether, cx, cz);

        // The comparator's row: a stored output at a nether cell. The seat
        // plants the row through the system's own restore surface (the
        // loaded-BE shape the boot's walk itself uses), then saves through
        // the engine's own save point and reboots over the SAME data dir.
        BlockPosition comparator = new BlockPosition(cx, fy + 1, cz);
        server.ticker().submit(() -> {
            nether.setBlock(comparator, RedstoneBlocks.comparatorOf(3, false, false));
        });
        await(() -> RedstoneBlocks.isComparator(typeAt(server, nether, comparator)),
                "the nether comparator placed");
        server.ticker().submit(() -> server.redstoneFor(-1)
                .restoreComparatorOutputs(java.util.Map.of(comparator, 7)));
        server.shutdown(null);

        // The reboot over the same world: the nether comparator's stored
        // output answers 7 BEFORE any update re-arms it (the reference's
        // loaded BE carries the value straight into getWeakPower).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                WORLD_NAME, "it", 20, 4, 20, dataDir.toString());
        server = new EngineServer(config);
        server.start();
        EngineWorld netherAgain = server.worldFor(-1);
        assertEquals(7, server.redstoneFor(-1).comparatorSnapshot()
                        .getOrDefault(comparator, -1),
                "the nether comparator's stored output survived the restart");
        assertEquals(-1, (int) server.redstone().comparatorSnapshot()
                        .getOrDefault(comparator, -1),
                "the overworld's system never gained the nether row");
        // (The comparator BLOCK's own persistence rides the world-save
        // family's nether row — not this wave's store law. The stored
        // OUTPUT row is the ComparatorBlockEntity's own persistence, the
        // wave's exact scope.)
    }

    private static boolean isDoorLower(BlockType type) {
        String value = type.identifier().value();
        return value.contains("_door") && !value.endsWith("_upper") && !value.contains("upper");
    }
}
