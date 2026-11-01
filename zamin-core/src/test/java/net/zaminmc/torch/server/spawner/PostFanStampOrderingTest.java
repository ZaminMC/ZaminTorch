package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-013 — the remaining post-stamp precedents stamp AFTER the spawn
 * fan: the block spawner's, the dragon's, and the chicken egg's live
 * {@code onMobSpawned} publishes read the un-stamped Java-default 0 at
 * the event.
 *
 * <p>THE LAW (the reference, 1.8.8): Entity's constructor line 166
 * stamps {@code this.dimension = world.dimension.getId()} at BIRTH —
 * the dimension exists before any spawn event fires. VT-012's
 * dimension-aware {@code spawnAt(type, position, dimension)} carries
 * that law for its fourteen routed sites; the three precedents that
 * predate it still post-stamp after the 2-arg {@code spawnAt} returns,
 * and the listener fan fires INSIDE that call (MobManager's
 * {@code mobs.add(mob)} + the fan, before the return) — so the wire's
 * own {@code sendMobSpawn} Slice 25d gate
 * ({@code dimensionOfSession() != mob.dimension()}) evaluates the
 * un-stamped 0 at the event: a nether spawner's body never publishes
 * to the nether player standing beside it, while an overworld player
 * at the same chunk coordinates receives the phantom spawn.</p>
 *
 * <p>THE SEATS: the block-spawner member — the family's deterministic,
 * real-game member — driven twice: once at the unit harness (the
 * spawner's own walk with a listener on the bare MobManager) and once
 * through the full engine boot (the real listener fan the protocol
 * server rides). Both read the stamp AT THE EVENT, before the site's
 * own post-stamp can land. The dragon and the chicken-egg members
 * share the exact root cause (one line each, censused in the record);
 * the dragon spawns before any player tracks (its exposure is the
 * rejoin-only compensation), and the chicken egg's hatch is a 1-in-8
 * roll (a probabilistic seat would flake — the shared root cause and
 * the two spawner seats carry the demonstration).</p>
 */
class PostFanStampOrderingTest {

    // ------------------------------------------------------------------
    // MEMBER ONE — the unit harness: the spawner's own walk, the stamp
    // read at the event listener.
    // ------------------------------------------------------------------

    /** The recording player surface (one controllable player). */
    private static final class TestPlayers implements SpawnerManager.Players {
        final List<Position> positions = new ArrayList<>();

        @Override
        public List<Position> positions() {
            return positions;
        }
    }

    /** The flat-air world query (every spawn cell clears). */
    private static final MobEntity.WorldQuery AIR_WORLD = new MobEntity.WorldQuery() {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    };

    /** The stamp capture: the one-read listener (the event's own view). */
    private static final class SpawnStamp implements MobManager.Listener {
        volatile int atEvent = Integer.MIN_VALUE;

        @Override
        public void onMobSpawned(MobEntity mob) {
            if (atEvent == Integer.MIN_VALUE) {
                atEvent = mob.dimension();
            }
        }

        @Override
        public void onMobMoved(MobEntity mob, PlayerSession rider) {
        }

        @Override
        public void onMobHurt(MobEntity mob) {
        }

        @Override
        public void onMobDied(MobEntity mob) {
        }

        @Override
        public void onMobRemoved(MobEntity mob, String reason) {
        }

        @Override
        public void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage) {
        }

        @Override
        public void onMobSound(MobEntity mob, String soundName) {
        }

        @Override
        public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
        }

        @Override
        public void onMobFuseChanged(MobEntity mob, boolean priming) {
        }

        @Override
        public void onMobSheared(MobEntity mob, int woolCount) {
        }

        @Override
        public void onMobCoatRegrown(MobEntity mob) {
        }

        @Override
        public void onMobExploded(MobEntity mob) {
        }
    }

    @Test
    void theNetherSpawnersBodyIsBornStampedAtTheFan() {
        TestPlayers players = new TestPlayers();
        players.positions.add(new Position(8.5, 64.0, 8.5));
        MobManager mobs = new MobManager(AIR_WORLD, new Random(7L),
                (position, stack, dimension) -> {
                }, 1, (x, z) -> 64);
        SpawnStamp stamp = new SpawnStamp();
        mobs.addListener(stamp);
        SpawnerManager spawners = new SpawnerManager(new Random(1L), players,
                mobs, dimension -> null);
        BlockPosition pos = new BlockPosition(8, 64, 8);
        // A NETHER spawner (the fortress's own shape): the tile's
        // dimension row is -1, the body must be born with it.
        SpawnerManager.MobSpawnerBlockEntity spawner = spawners.getOrCreate(-1, pos);
        spawner.setType(MobType.VILLAGER);
        spawner.setDelay(0); // the cycle fires THIS tick

        spawners.tick();

        assertNotEquals(Integer.MIN_VALUE, stamp.atEvent,
                "the spawner's cycle spawned its body (the player stood in range, "
                        + "the delay was armed)");
        assertEquals(-1, stamp.atEvent,
                "the body born at the NETHER spawner carries the NETHER stamp AT THE "
                        + "SPAWN EVENT (Entity line 166's birth stamp; the post-fan "
                        + "setDimension the site lands today repairs the row but never "
                        + "the live publish — the wire's Slice 25d gate reads the "
                        + "un-stamped 0 at the event, the nether player beside the "
                        + "spawner never sees the body spawn)");
    }

    // ------------------------------------------------------------------
    // MEMBER TWO — the full engine boot: the real listener fan.
    // ------------------------------------------------------------------

    @TempDir
    Path dataDir;

    private net.zaminmc.torch.server.EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "postfan" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    @Test
    void theNetherSpawnersBodyCarriesItsStampThroughTheRealFan() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherWarden");
        player.setDimension(-1);

        // The nether platform: stone floor at y=100, three air layers
        // above (the spawner's spawn rolls land inside them).
        int fy = 100;
        BlockPosition spawnerAt = new BlockPosition(64, fy, 64);
        server.ticker().submit(() -> {
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    nether.setBlock(new BlockPosition(64 + dx, fy, 64 + dz),
                            net.zaminmc.torch.server.block.BuiltinBlocks.STONE);
                    for (int y = fy + 1; y <= fy + 4; y++) {
                        nether.setBlock(new BlockPosition(64 + dx, y, 64 + dz),
                                nether.airType());
                    }
                }
            }
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> net.zaminmc.torch.server.block.BuiltinBlocks.STONE.equals(
                        nether.getBlock(new BlockPosition(64, fy, 64))),
                40, "the nether platform landed");

        player.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        // The stamp capture on the REAL fan (the same fan the protocol
        // server's sendMobSpawn rides).
        SpawnStamp stamp = new SpawnStamp();
        server.addMobListener(stamp);

        server.ticker().submit(() -> {
            SpawnerManager.MobSpawnerBlockEntity spawner =
                    server.spawners().getOrCreate(-1, spawnerAt);
            spawner.setType(MobType.VILLAGER);
            spawner.setDelay(0); // the cycle fires the next tick
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> stamp.atEvent != Integer.MIN_VALUE, 40,
                "the nether spawner's cycle spawned its body");
        assertEquals(-1, stamp.atEvent,
                "the body born at the NETHER spawner carries the NETHER stamp at the "
                        + "real spawn fan (the nether player standing beside the "
                        + "spawner must receive the spawn packet at the event)");
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.ticker().submit(session::markPlaying);
        server.ticker().submit(() -> session.setOpLevel(2));
        return session;
    }
}
