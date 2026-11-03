package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.stat.EngineAchievements;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.testing.EngineAwaits;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * VT-033's adjacent tails — the award walks' dimension gates (the
 * FL4-GAP2 Slice C landing's own ledgered residue, #146's review
 * notes): the wither-fall's kill row and the beacon's full-power
 * pyramid row walk the ACTING world's player list (the reference's
 * {@code world.getEntitiesOfType(PlayerEntity.class, box)} on the
 * body's/beacon's own world — a same-coordinate player in ANOTHER
 * dimension is not in the list).
 *
 * <p>The pre-fix walks were the un-dimensioned awardPlayersInBox form:
 * every dimension's fully-powered beacon awarded players in EVERY
 * dimension whose boxes intersected the same coordinates, and the
 * wither's fall the same — a nether fall's row landing on an overworld
 * watcher at the mirrored coordinates.</p>
 */
class AwardWalkDimensionDiscoveryTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "awardwalk" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        var accepted = server.joinRequest(new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        }, name, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.ticker().submit(session::markPlaying);
        try {
            EngineAwaits.awaitCondition(server.world(),
                    () -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    200, name + " playing");
            // The achievement command's own permission band (level 2).
            server.ticker().submit(() -> session.setOpLevel(2));
            EngineAwaits.awaitCondition(server.world(),
                    () -> session.opLevel() >= 2, 200, name + " opped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting: " + name, e);
        }
        return session;
    }

    private void await(BooleanSupplier condition, String what) {
        try {
            EngineAwaits.awaitCondition(server.world(), condition, 250, what);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted awaiting: " + what, e);
        }
    }

    private void grantParent(PlayerSession session) {
        server.chatService().submitChat(session,
                "/achievement give achievement.killWither " + session.name());
    }

    /**
     * THE WITHER FALL: a nether wither's death awards the kill row to
     * the players in the NETHER alone — an overworld player at the same
     * coordinates never earns it (WitherEntity.dropItems lines 452-458
     * over the wither's own world's player list).
     */
    @Test
    void theNetherWitherFallAwardsTheNetherWatchersAlone() throws Exception {
        PlayerSession netherWatcher = join("NetherWatcher");
        PlayerSession overworldBystander = join("OverBystander");
        try {
            grantParent(netherWatcher);
            grantParent(overworldBystander);
            await(() -> netherWatcher.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key())
                            && overworldBystander.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key()),
                    "the parents given");

            // The watcher crosses to the nether; the bystander stands in
            // the overworld AT THE SAME COORDINATES (the mirrored band).
            server.ticker().submit(() -> {
                netherWatcher.setDimension(-1);
                netherWatcher.applyMovement(new Position(64.5, 64.0, 64.5),
                        new Rotation(0, 0), true);
                overworldBystander.applyMovement(new Position(64.5, 64.0, 64.5),
                        new Rotation(0, 0), true);
            });
            await(() -> netherWatcher.dimension() == -1, "the watcher in the nether");

            // A wither spawns in the nether at the shared coordinates and
            // dies there (the fall's own award arm).
            AtomicReference<MobEntity> wither = new AtomicReference<>();
            server.ticker().submit(() -> wither.set(server.mobs().spawnAt(
                    MobType.WITHER, new Position(64.5, 64.0, 64.5), -1)));
            await(() -> wither.get() != null, "the nether wither spawned");
            await(() -> !wither.get().witherInvulnerable(), "the spawn window closed");
            server.ticker().submit(() ->
                    server.mobs().hurt(wither.get(), 1000.0f, 0.0f));
            await(() -> wither.get().dead(), "the nether wither fell");

            // THE PINS: the overworld bystander NEVER earns the fall's
            // row (the pre-fix un-dimensioned walk awarded every
            // intersecting box whatever the dimension).
            EngineAwaits.awaitTicks(server.world(), 2, "the award walk drained");
            // The KILL_WITHER achievement was already granted as the
            // parent — the fall's own observable is the STAT row the
            // death walk increments: killEntity.wither.
            int overworldKills = overworldBystander.stats().get(
                    net.zaminmc.torch.server.stat.EngineStats.killEntityOf("wither"));
            assertTrue(overworldKills == 0,
                    "VT-033's tail: the overworld bystander at the same coordinates "
                            + "never earns the nether fall's stat row (observed "
                            + overworldKills + ")");
        } finally {
            server.shutdown(null);
        }
    }

    /**
     * THE BEACON PYRAMID: a nether beacon's full power awards the
     * activation row to the players in the NETHER alone — an overworld
     * player at the same coordinates never earns it
     * (BeaconBlockEntity.update lines 162-167 over the beacon's own
     * world's player list).
     */
    @Test
    void theNetherBeaconFullPowerAwardsTheNetherBuildersAlone() throws Exception {
        PlayerSession netherBuilder = join("NetherBuilder");
        PlayerSession overworldSquatter = join("OverSquatter");
        try {
            grantParent(netherBuilder);
            grantParent(overworldSquatter);
            await(() -> netherBuilder.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key())
                            && overworldSquatter.stats().hasAchievement(
                            EngineAchievements.KILL_WITHER.key()),
                    "the parents given");

            // The builder crosses to the nether; the squatter stands in
            // the overworld INSIDE the mirrored grown box.
            server.ticker().submit(() -> {
                netherBuilder.setDimension(-1);
                netherBuilder.applyMovement(new Position(61, 60, 61),
                        new Rotation(0, 0), true);
                overworldSquatter.applyMovement(new Position(61, 60, 61),
                        new Rotation(0, 0), true);
            });
            await(() -> netherBuilder.dimension() == -1, "the builder in the nether");

            // The four-level iron pyramid in the NETHER's own cells. The
            // BEAM COLUMN above the beacon cleared to the bedrock roof
            // first (updateLevels lines 95-106: an opacity-15 block kills
            // the beam — the netherrack terrain above would leave the
            // beacon inactive and the pyramid census never runs; bedrock
            // alone is excepted, line 104 — the vanilla nether-beacon
            // shaft's own construction).
            EngineWorld nether = server.worldFor(-1);
            AtomicBoolean built = new AtomicBoolean();
            server.ticker().submit(() -> {
                int x = 60, y = 60, z = 60;
                for (int clearY = y + 1; clearY <= 126; clearY++) {
                    nether.setBlock(new BlockPosition(x, clearY, z),
                            nether.airType());
                }
                for (int level = 1; level <= 4; level++) {
                    int ringY = y - level;
                    for (int dx = -level; dx <= level; dx++) {
                        for (int dz = -level; dz <= level; dz++) {
                            nether.setBlock(new BlockPosition(x + dx, ringY, z + dz),
                                    BuiltinBlocks.IRON_BLOCK);
                        }
                    }
                }
                nether.setBlock(new BlockPosition(x, y, z),
                        net.zaminmc.torch.server.beacon.BeaconSystem.BEACON);
                built.set(true);
            });
            await(built::get, "the nether pyramid built");

            // THE PINS: the nether builder earns the row; the overworld
            // squatter at the same coordinates NEVER does.
            await(() -> netherBuilder.stats().hasAchievement(
                            EngineAchievements.ACTIVATE_MAX_BEACON.key()),
                    "the nether builder earned the pyramid row");
            assertFalse(overworldSquatter.stats().hasAchievement(
                            EngineAchievements.ACTIVATE_MAX_BEACON.key()),
                    "VT-033's tail: the overworld squatter at the same coordinates "
                            + "never earns the nether pyramid's row");
        } finally {
            server.shutdown(null);
        }
    }
}
