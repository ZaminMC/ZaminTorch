package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawner minecart's own law (Slice 66 — the reference port of
 * SpawnerMinecartEntity over MobSpawner): the "Pig" field default, the
 * tick walk's player gate + delay band + four-attempt cycle, the
 * per-spawn flame burst, the delay reroll's spin reset, and the
 * persistence payload.
 */
class SpawnerMinecartTest {

    @TempDir
    Path dataDir;

    /** The flat ground world. */
    private static final VehicleEntity.WorldQuery GROUND_WORLD =
            new VehicleEntity.WorldQuery() {
                @Override public boolean isSolid(double x, double y, double z) {
                    return y < 0.0;
                }
            };

    /** The recording host: a controllable spawn surface. */
    private static final class RecordingHost implements SpawnerMinecartEntity.SpawnHost {
        final AtomicInteger spawns = new AtomicInteger();
        volatile boolean playerNearby = true;
        volatile int nearbyCount;
        volatile boolean allowSpawn = true;

        @Override
        public boolean playerNearby(double x, double y, double z, double range) {
            return playerNearby;
        }

        @Override
        public int nearbyOfKind(MobType type, int cellX, int cellY, int cellZ, int range) {
            return nearbyCount;
        }

        @Override
        public boolean spawnAt(MobType type, double x, double y, double z) {
            spawns.incrementAndGet();
            return allowSpawn;
        }
    }

    @Test
    void theWireTypeIndexAndThePigFieldDefault() {
        RecordingHost host = new RecordingHost();
        SpawnerMinecartEntity cart = new SpawnerMinecartEntity(9900,
                new Position(10.5, 0.35, 10.5), new Random(91L), host);
        assertEquals(4, cart.minecartTypeIndex(),
                "the spawner cart carries objectData 4");
        assertEquals(VehicleEntity.Kind.SPAWNER_MINECART, cart.kind());
        assertEquals(net.zaminmc.torch.entity.VehicleKind.SPAWNER_MINECART,
                cart.vehicleKind());
        // MobSpawner line 19's own field initializer: "Pig" — a summoned
        // spawner cart with no NBT IS a pig spawner.
        assertEquals(MobType.PIG, cart.type(), "the reference's own Pig default");
        assertEquals(SpawnerMinecartEntity.INITIAL_DELAY, cart.delay(),
                "the 20-tick initial delay");
    }

    @Test
    void theCycleSpawnsFourAndRerollsTheDelay() {
        // tick lines 49-121: the 20-tick countdown, then the four attempts
        // (all spawn — the per-spawn flame bursts), then the reroll's
        // 200..800 band + the spin reset.
        RecordingHost host = new RecordingHost();
        SpawnerMinecartEntity cart = new SpawnerMinecartEntity(9901,
                new Position(10.5, 0.35, 10.5), new Random(92L), host);
        // The 20 decrements consume ticks 1-20; the cycle itself runs on
        // tick 21 (the reference's own shape: delay > 0 returns).
        for (int i = 0; i < SpawnerMinecartEntity.INITIAL_DELAY + 1; i++) {
            cart.tick(GROUND_WORLD);
        }
        assertEquals(4, host.spawns.get(), "the cycle's four attempts all spawned");
        assertEquals(4, cart.consumeFlameBursts(), "one flame burst per spawn");
        assertTrue(cart.delay() >= SpawnerMinecartEntity.MIN_SPAWN_DELAY
                        && cart.delay() < SpawnerMinecartEntity.MAX_SPAWN_DELAY,
                "the reroll's 200..800 band: " + cart.delay());
        assertEquals(1, cart.consumeSpinResets(), "the reroll's spin reset");
    }

    @Test
    void theNearbyCapAbortsTheWholeCycle() {
        // lines 77-85: at 6 nearby, the cycle re-arms and returns — no
        // spawn attempt runs.
        RecordingHost host = new RecordingHost();
        host.nearbyCount = SpawnerMinecartEntity.MAX_NEARBY_ENTITIES;
        SpawnerMinecartEntity cart = new SpawnerMinecartEntity(9902,
                new Position(10.5, 0.35, 10.5), new Random(93L), host);
        for (int i = 0; i < SpawnerMinecartEntity.INITIAL_DELAY + 1; i++) {
            cart.tick(GROUND_WORLD);
        }
        assertEquals(0, host.spawns.get(), "the cap aborted before any attempt");
        assertEquals(1, cart.consumeSpinResets(), "the abort's own reroll");
        assertTrue(cart.delay() >= SpawnerMinecartEntity.MIN_SPAWN_DELAY,
                "the abort re-armed the delay");
    }

    @Test
    void noPlayerNearbyFreezesTheWalk() {
        // lines 51-54: the 16-block gate — no player, no countdown.
        RecordingHost host = new RecordingHost();
        host.playerNearby = false;
        SpawnerMinecartEntity cart = new SpawnerMinecartEntity(9903,
                new Position(10.5, 0.35, 10.5), new Random(94L), host);
        for (int i = 0; i < 100; i++) {
            cart.tick(GROUND_WORLD);
        }
        assertEquals(0, host.spawns.get());
        assertEquals(SpawnerMinecartEntity.INITIAL_DELAY, cart.delay(),
                "the delay never burned without a player");
    }

    @Test
    void thePerCartCadenceRowsDriveTheWalk() {
        // The Wave VI per-cart surface (MobSpawnerBaseLogic's own FIELDS —
        // writeNbt's six rows): the imported/persisted rows override the
        // reference's defaults PER CART. A narrow band, two attempts, a
        // two-mob cap, and a 0-range roll — every band observable in one
        // walk.
        RecordingHost host = new RecordingHost();
        host.nearbyCount = 2; // the custom cap's edge: the abort fires
        SpawnerMinecartEntity cart = new SpawnerMinecartEntity(9905,
                new Position(10.5, 0.35, 10.5), new Random(96L), host);
        cart.setMinSpawnDelay(30);
        cart.setMaxSpawnDelay(31);
        cart.setSpawnCount(2);
        cart.setMaxNearbyEntities(2);
        cart.setRequiredPlayerRange(16);
        cart.setSpawnRange(4);
        for (int i = 0; i < SpawnerMinecartEntity.INITIAL_DELAY + 1; i++) {
            cart.tick(GROUND_WORLD);
        }
        // The custom cap (2) aborted the cycle before any attempt — the
        // reference's own >= band against the cart's OWN row.
        assertEquals(0, host.spawns.get(), "the cart's own two-mob cap aborted the cycle");
        assertTrue(cart.delay() == 30, "the cart's own 30..31 band: " + cart.delay());
        // The wide-open shape: the custom rows let the cycle spawn.
        host.nearbyCount = 0;
        cart.setDelay(0);
        cart.tick(GROUND_WORLD);
        assertEquals(2, host.spawns.get(), "the cart's own two-attempt cycle spawned both");
        assertEquals(2, cart.consumeFlameBursts(), "the per-spawn bursts");
    }

    @Test
    void thePersistencePayloadCarriesTheCadenceRows() {
        // The ZVD v5 roundtrip: the spawner's six cadence rows.
        java.nio.file.Path file = dataDir.resolve("vehicles.bin");
        net.zaminmc.torch.server.entity.vehicle.VehicleDataStore store =
                new net.zaminmc.torch.server.entity.vehicle.VehicleDataStore(file);
        VehicleManager manager = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(9), 9700);
        var spawned = (SpawnerMinecartEntity) manager.spawnSpawnerMinecart(
                new Position(12.5, 0.35, 12.5), new RecordingHost(), 0);
        spawned.setMinSpawnDelay(11);
        spawned.setMaxSpawnDelay(22);
        spawned.setSpawnCount(3);
        spawned.setMaxNearbyEntities(4);
        spawned.setRequiredPlayerRange(8);
        spawned.setSpawnRange(2);
        store.save(manager.snapshot());
        VehicleManager restored = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(10), 9800);
        restored.restoreAll(store.load(), null, () -> new RecordingHost());
        var back = (SpawnerMinecartEntity) restored.byId(restored.all().get(0).entityId());
        assertEquals(11, back.minSpawnDelay(), "the MinSpawnDelay row");
        assertEquals(22, back.maxSpawnDelay(), "the MaxSpawnDelay row");
        assertEquals(3, back.spawnCount(), "the SpawnCount row");
        assertEquals(4, back.maxNearbyEntities(), "the MaxNearbyEntities row");
        assertEquals(8, back.requiredPlayerRange(), "the RequiredPlayerRange row");
        assertEquals(2, back.spawnRange(), "the SpawnRange row");
    }

    @Test
    void thePersistencePayloadCarriesTheTypeAndDelay() {
        // The ZVD v2 roundtrip: the spawner's EntityId/Delay rows.
        java.nio.file.Path file = dataDir.resolve("vehicles.bin");
        net.zaminmc.torch.server.entity.vehicle.VehicleDataStore store =
                new net.zaminmc.torch.server.entity.vehicle.VehicleDataStore(file);
        VehicleManager manager = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(7), 9500);
        var spawned = (SpawnerMinecartEntity) manager.spawnSpawnerMinecart(
                new Position(12.5, 0.35, 12.5), new RecordingHost(), 0);
        spawned.setType(MobType.ZOMBIE);
        spawned.setDelay(137);
        store.save(manager.snapshot());
        List<VehicleManager.VehicleSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        VehicleManager.VehicleSnapshot row = loaded.get(0);
        assertEquals(VehicleEntity.Kind.SPAWNER_MINECART.name(), row.kind());
        assertEquals(MobType.ZOMBIE.name(), row.spawnerType(), "the EntityId row");
        assertEquals(137, row.spawnerDelay(), "the Delay row");
        // The restore: the rows land on the fresh body.
        VehicleManager restored = new VehicleManager(GROUND_WORLD,
                vehicle -> { }, new Random(8), 9600);
        restored.restoreAll(store.load(), null, () -> new RecordingHost());
        var back = (SpawnerMinecartEntity) restored.byId(restored.all().get(0).entityId());
        assertEquals(MobType.ZOMBIE, back.type());
        assertEquals(137, back.delay());
    }

    @Test
    void theLiveEngineSpawnsPigsBesideTheRollingSpawner() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession session = join(server, "spawnerwatcher");
            await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");
            // The cart beside the player (inside the 16-block gate).
            var at = session.position();
            server.ticker().submit(() -> {
                server.world().setBlock(
                        new BlockPosition((int) Math.floor(at.x()) + 2, 40, 10),
                        BuiltinBlocks.RAIL);
                server.vehicles().spawnSpawnerMinecart(
                        new Position(Math.floor(at.x()) + 2.5, 40.06, 10.5),
                        new SpawnerMinecartEntity.SpawnHost() {
                            @Override public boolean playerNearby(double x, double y, double z, double range) {
                                return true; // the session stands beside the cart
                            }

                            @Override public int nearbyOfKind(MobType type, int cellX,
                                    int cellY, int cellZ, int range) {
                                int nearby = 0;
                                for (var mob : server.mobs().all()) {
                                    if (mob.type() == type) {
                                        Position mobAt = mob.position();
                                        if (Math.abs(mobAt.x() - (cellX + 0.5)) <= 4.0
                                                && Math.abs(mobAt.y() - (cellY + 0.5)) <= 4.0
                                                && Math.abs(mobAt.z() - (cellZ + 0.5)) <= 4.0) {
                                            nearby++;
                                        }
                                    }
                                }
                                return nearby;
                            }

                            @Override public boolean spawnAt(MobType type, double x,
                                    double y, double z) {
                                if (!net.zaminmc.torch.server.spawner.SpawnerManager
                                        .canSpawnAt(server.world(), type, x, y, z)) {
                                    return false;
                                }
                                return server.mobs().spawnAt(type, new Position(x, y, z)) != null;
                            }
                        }, 0);
            });
            await(() -> !server.vehicles().all().isEmpty(), "the cart spawned");
            // The 20-tick initial delay, then pigs (the field default).
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.PIG),
                    "the rolling spawner spawned its pigs (the Pig field default)");
            var cart = (SpawnerMinecartEntity) server.vehicles().all().get(0);
            assertTrue(cart.delay() >= SpawnerMinecartEntity.MIN_SPAWN_DELAY,
                    "the cycle re-armed: " + cart.delay());
        } finally {
            server.shutdown(null);
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "smct" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(), GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private static PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
