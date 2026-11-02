package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * VT-012 — the live spawn walks beyond the natural cycle never stamp the
 * body's dimension: every mob born through the non-natural entry points
 * carries the Java-default {@code dimension = 0} (the overworld) whatever
 * dimension it is born in.
 *
 * <p>THE REFERENCE'S LAW (1.8.8): every constructed entity receives the
 * ACTING world — the spawn egg's
 * {@code SpawnEggItem.spawnEntity(world, ...)} (the use wire's own
 * {@code world} parameter), the summon's
 * {@code source.getCommandSourceWorld()} (SummonCommand line 54), the
 * pearl's endermite {@code new EndermiteEntity(this.world)}
 * (EnderPearlEntity.onCollision line 52), the breeding calf
 * {@code new CowEntity(this.world)} (CowEntity.createChild line 110), the
 * split slime {@code new SlimeEntity(this.world)}
 * (SlimeEntity.getInstance line 138), the sheared mooshroom's replacement
 * cow (MooshroomEntity line 38), the zombie infection and the cure
 * (ZombieEntity lines 360 and 520), the skull-built wither
 * (SkullBlock line 185), the pumpkin golems (PumpkinBlock lines 57 and
 * 86), the infested burst's silverfish (InfestedBlock.dropItems), and the
 * spawner cart's {@code getWorld()} (SpawnerMinecartEntity line 19). A
 * body born in the NETHER lives in the Nether.</p>
 *
 * <p>THE ENGINE'S LAW where it already knows it: the natural nether cycle
 * stamps {@code setDimension(-1)} (spawnNaturalNether), the dragon stamps
 * 1, the block spawner stamps {@code spawner.dimension()}, the chicken
 * egg stamps its parameter, and the zombie reinforcement stamps the
 * caller — the walk exists; the family's other members never call it.</p>
 *
 * <p>THE OBSERVABLE DAMAGE (the wire's own gate, V18Connection
 * sendMobSpawn's Slice 25d filter): a wrong-stamped nether body never
 * publishes to the nether player standing beside it
 * ({@code dimensionOfSession() != mob.dimension()}), and an overworld
 * player at the SAME chunk coordinates receives the phantom spawn —
 * invisible mobs in the nether, ghosts in the overworld. The body's
 * physics, scans, and targeting read the OVERWORLD's cells at the
 * nether's coordinates (the shared query's
 * {@code setActiveDimension(mob.dimension())}), and the persistence
 * store saves the wrong row whole.</p>
 *
 * <p>THE SEAT drives the two highest-traffic members through their REAL
 * entry points — the spawn egg's use wire and the summon command — with
 * a player standing in the NETHER. Both bodies must carry the NETHER
 * stamp at the spawn event (the listener's one-tick read, the SpawnEggTest
 * precedent: the body's later life can despawn it before a poll).</p>
 */
class SpawnDimensionStampAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "spawndim" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
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

    /** The spawn-event dimension capture: the one-tick read at the event. */
    private static final class SpawnStamp {
        volatile int dimension = Integer.MIN_VALUE;
        volatile Position at;
    }

    private SpawnStamp listenFor(MobType kind) {
        SpawnStamp stamp = new SpawnStamp();
        server.addMobListener(new MobManager.Listener() {
            @Override public void onMobSpawned(MobEntity mob) {
                if (mob.type() == kind && stamp.dimension == Integer.MIN_VALUE) {
                    stamp.dimension = mob.dimension();
                    stamp.at = mob.position();
                }
            }
            @Override public void onMobMoved(MobEntity mob, PlayerSession rider) { }
            @Override public void onMobHurt(MobEntity mob) { }
            @Override public void onMobDied(MobEntity mob) { }
            @Override public void onMobRemoved(MobEntity mob, String reason) { }
            @Override public void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage) { }
            @Override public void onMobSound(MobEntity mob, String soundName) { }
            @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
            @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
            @Override public void onMobSheared(MobEntity mob, int woolCount) { }
            @Override public void onMobCoatRegrown(MobEntity mob) { }
            @Override public void onMobExploded(MobEntity mob) { }
        });
        return stamp;
    }

    /**
     * MEMBER ONE — THE SPAWN EGG (the everyday creative tool): the nether
     * player uses a pig egg on the nether stone floor's UP face
     * (SpawnEggItem.useOn line 82's face-offset cell). The spawned pig
     * must carry the NETHER stamp — the reference constructs it in the
     * use wire's own acting world.
     */
    @Test
    void theNetherEggSpawnsANetherBody() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherShepherd");
        player.setDimension(-1);

        // The nether platform: stone floor at y=100, air cleared above.
        int fy = 100;
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
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(64, fy, 64))),
                40, "the nether platform landed");

        player.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        server.chatService().submitChat(player, "/give " + player.name() + " spawn_egg 1 90");
        EngineAwaits.awaitCondition(server.world(),
                () -> !player.inventory().held().isEmpty(), 40, "the pig egg granted");

        SpawnStamp pig = listenFor(MobType.PIG);
        server.useItemOnBlock(player, new BlockPosition(64, fy, 64), 1,
                java.util.Optional.empty(), windowId -> { });

        EngineAwaits.awaitCondition(server.world(),
                () -> pig.dimension != Integer.MIN_VALUE, 40, "the pig spawned");
        assertEquals(-1, pig.dimension,
                "the pig born at the nether floor carries the NETHER stamp "
                        + "(the reference's use-wire world; the pre-fix engine stamped "
                        + "the overworld's 0 — the body invisible to the nether player "
                        + "standing beside it)");
        assertNotNull(pig.at, "the spawn position captured");
    }

    /**
     * MEMBER TWO — THE SUMMON COMMAND (the ops route): the nether player
     * runs {@code /summon Zombie}. The reference reads
     * {@code source.getCommandSourceWorld()} (SummonCommand line 54) —
     * the SENDER's own world. The body must carry the NETHER stamp.
     */
    @Test
    void theNetherSummonsIntoTheNether() throws Exception {
        var nether = server.worldFor(-1);
        PlayerSession player = join("NetherOp");
        player.setDimension(-1);

        int fy = 100;
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
        });
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(nether.getBlock(new BlockPosition(64, fy, 64))),
                40, "the nether platform landed");

        player.applyMovement(new Position(64.5, fy + 1, 65.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);
        EngineAwaits.awaitTicks(server.world(), 2, "the player settles");

        SpawnStamp zombie = listenFor(MobType.ZOMBIE);
        server.chatService().submitChat(player, "/summon Zombie");

        EngineAwaits.awaitCondition(server.world(),
                () -> zombie.dimension != Integer.MIN_VALUE, 40, "the zombie summoned");
        assertEquals(-1, zombie.dimension,
                "the summoned zombie carries the SENDER's dimension "
                        + "(SummonCommand line 54's getCommandSourceWorld; the pre-fix "
                        + "engine stamped the overworld's 0)");
    }

    /**
     * THE OVERWORLD CONTROL: the same two entry points in the overworld
     * stamp 0 — the family's fix must not disturb the overworld's own
     * spawns (the regression seat).
     */
    @Test
    void theOverworldSpawnsStayOverworld() throws Exception {
        PlayerSession player = join("GroundedShepherd");
        BlockPosition stone = new BlockPosition(2, 4, 2);
        server.world().execute(() -> server.world().setBlock(stone, BuiltinBlocks.STONE));
        EngineAwaits.awaitCondition(server.world(),
                () -> BuiltinBlocks.STONE.equals(server.world().getBlock(stone)),
                40, "the stone landed");
        player.applyMovement(new Position(2.5, 5.0, 4.5),
                new net.zaminmc.torch.util.Rotation(0, 0), true);

        server.chatService().submitChat(player, "/give " + player.name() + " spawn_egg 1 90");
        EngineAwaits.awaitCondition(server.world(),
                () -> !player.inventory().held().isEmpty(), 40, "the pig egg granted");

        SpawnStamp pig = listenFor(MobType.PIG);
        server.useItemOnBlock(player, stone, 1,
                java.util.Optional.empty(), windowId -> { });
        EngineAwaits.awaitCondition(server.world(),
                () -> pig.dimension != Integer.MIN_VALUE, 40, "the control pig spawned");
        assertEquals(0, pig.dimension, "the overworld pig keeps the overworld stamp");

        SpawnStamp zombie = listenFor(MobType.ZOMBIE);
        server.chatService().submitChat(player, "/summon Zombie");
        EngineAwaits.awaitCondition(server.world(),
                () -> zombie.dimension != Integer.MIN_VALUE, 40, "the control zombie summoned");
        assertEquals(0, zombie.dimension, "the overworld summon keeps the overworld stamp");
    }
}
