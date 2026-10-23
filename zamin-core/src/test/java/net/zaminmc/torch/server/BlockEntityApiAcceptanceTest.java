package net.zaminmc.torch.server;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The block-entity API wave beyond the containers (§27.3's read surface
 * over {@link World#blockEntities()}): the spawner and sign lookups and
 * their live views — the engine's own block entities behind the public
 * handles (§27.5), the spawner answered only on the dimension it lives
 * in, the reads exercised both on the owner lane and from a foreign
 * thread (the best-effort lookup contract).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockEntityApiAcceptanceTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    @Test
    void theSpawnerReadsThroughThePublicView() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition at = new BlockPosition(21, 40, 21);
            server.ticker().submit(() -> {
                server.world().setBlock(at, BuiltinBlocks.MOB_SPAWNER);
                server.spawnerManager().getOrCreate(0, at).setType(MobType.ZOMBIE);
                server.spawnerManager().refreshWireTiles();
            });
            awaitSettled(server);
            World world = server.world();
            Optional<net.zaminmc.torch.blockentity.BlockEntities.MobSpawner> spawner =
                    world.blockEntities().spawnerAt(at);
            assertTrue(spawner.isPresent(), "the placed spawner answers the lookup");
            assertEquals(at, spawner.orElseThrow().position());
            assertEquals(Identifier.parse("minecraft:zombie"), spawner.orElseThrow().spawnedMob(),
                    "the set kind through the engine");
            assertTrue(spawner.orElseThrow().spawnDelayTicks() >= 0,
                    "the live delay reads (the initial 20 or the walked value)");

            // The empty world's misses: no spawner block entity at the position,
            // and a spawner of ANOTHER dimension never answers this world's view.
            assertTrue(world.blockEntities().spawnerAt(new BlockPosition(0, 90, 0)).isEmpty(),
                    "no spawner at the empty position");
            BlockPosition fortressAt = new BlockPosition(21, 40, 22);
            server.ticker().submit(() -> {
                net.zaminmc.torch.server.world.EngineWorld nether = server.netherWorld();
                nether.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(1, 1));
                nether.setBlock(fortressAt, BuiltinBlocks.MOB_SPAWNER);
                server.spawnerManager().getOrCreate(-1, fortressAt).setType(MobType.BLAZE);
                server.spawnerManager().refreshWireTiles();
            });
            awaitSettled(server);
            assertTrue(world.blockEntities().spawnerAt(fortressAt).isEmpty(),
                    "a nether spawner never answers the overworld's view");
            World nether = null;
            for (World candidate : server.worlds()) {
                if (candidate.name().endsWith("_nether")) {
                    nether = candidate;
                }
            }
            assertTrue(nether != null, "the nether booted");
            Optional<net.zaminmc.torch.blockentity.BlockEntities.MobSpawner> netherSpawner =
                    nether.blockEntities().spawnerAt(fortressAt);
            assertTrue(netherSpawner.isPresent(), "the nether's own view answers its spawner");
            assertEquals(Identifier.parse("minecraft:blaze"),
                    netherSpawner.orElseThrow().spawnedMob());

            // The un-typed spawner answers with a null kind (the fresh getOrCreate
            // before the first re-type).
            BlockPosition fresh = new BlockPosition(23, 40, 23);
            server.ticker().submit(() -> {
                server.world().setBlock(fresh, BuiltinBlocks.MOB_SPAWNER);
                server.spawnerManager().getOrCreate(0, fresh);
            });
            awaitSettled(server);
            Optional<net.zaminmc.torch.blockentity.BlockEntities.MobSpawner> freshView =
                    world.blockEntities().spawnerAt(fresh);
            assertTrue(freshView.isPresent(), "the un-typed spawner still answers");
            assertNull(freshView.orElseThrow().spawnedMob(), "no kind carried yet");

            // The foreign-thread lookup (the best-effort contract).
            AtomicReference<Optional<net.zaminmc.torch.blockentity.BlockEntities.MobSpawner>> foreign =
                    new AtomicReference<>(Optional.empty());
            Thread offLane = new Thread(() -> foreign.set(world.blockEntities().spawnerAt(at)));
            offLane.start();
            offLane.join();
            assertTrue(foreign.get().isPresent(), "the off-lane lookup finds the spawner");
            assertEquals(Identifier.parse("minecraft:zombie"),
                    foreign.get().orElseThrow().spawnedMob());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theSignReadsThroughThePublicView() throws Exception {
        EngineServer server = boot();
        try {
            BlockPosition at = new BlockPosition(25, 40, 25);
            server.ticker().submit(() -> {
                server.world().setBlock(at, BuiltinBlocks.SIGN_SOUTH);
                server.signs().set(at, new String[]{"Torch", "was", "here"});
            });
            awaitSettled(server);
            World world = server.world();
            Optional<net.zaminmc.torch.blockentity.BlockEntities.Sign> sign =
                    world.blockEntities().signAt(at);
            assertTrue(sign.isPresent(), "the placed sign answers the lookup");
            assertEquals(at, sign.orElseThrow().position());
            assertEquals(4, sign.orElseThrow().lineCount(), "the vanilla sign's four lines");
            assertEquals("Torch", sign.orElseThrow().line(0));
            assertEquals("here", sign.orElseThrow().line(2));
            assertEquals("", sign.orElseThrow().line(3), "the unset line answers empty");
            assertEquals("", sign.orElseThrow().line(99), "the out-of-range line answers empty");

            String[] lines = sign.orElseThrow().lines();
            assertEquals(4, lines.length, "the copy carries the four lines");
            assertEquals("was", lines[1]);

            // The lines() copy is a copy — mutating it never touches the engine.
            lines[0] = "mutated";
            assertEquals("Torch", sign.orElseThrow().line(0), "the engine text is untouched");

            // The empty position misses.
            assertTrue(world.blockEntities().signAt(new BlockPosition(0, 90, 0)).isEmpty(),
                    "no sign at the empty position");
        } finally {
            server.shutdown(null);
        }
    }

    /** The submit drains at the head of the next tick (<= 100 ms). */
    private void awaitSettled(EngineServer server) throws InterruptedException {
        EngineAwaits.awaitTicks(server.world(), 6, "the submit drains at the head of the next tick");
    }
}
