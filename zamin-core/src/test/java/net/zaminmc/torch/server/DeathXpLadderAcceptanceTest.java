package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The death-XP ladder's own rows (MobEntity.getXpDrop + the per-type
 * xpDrop assignments, 1:1): MonsterEntity's shared 5, the endermite's own
 * 3 (EndermiteEntity's ctor line 26), and the baby zombie's lift
 * (ZombieEntity.getXpDrop lines 128-133: {@code (int) (xpDrop * 2.5F)} =
 * 12 over the shared 5).
 */
class DeathXpLadderAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dxl" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        // the rule is on by default; the kills below read the ladder alone
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private int killAndCollectXp(MobType type, Position at, boolean baby)
            throws InterruptedException {
        MobEntity mob = server.mobs().spawnAt(type, at);
        assertNotNull(mob, "the mob spawned");
        if (baby) {
            mob.setBreedingAge(-24_000);
        }
        int before = totalOrbXp();
        assertTrue(server.mobs().hurt(mob, 1000.0f, 0.0),
                "the killing wound landed");
        // the death animation (20 ticks) then the removal tick's burst
        EngineAwaits.awaitTicks(server.world(), 26, "the death resolves");
        return totalOrbXp() - before;
    }

    private int totalOrbXp() {
        int total = 0;
        for (var orb : server.world().experienceOrbs()) {
            total += orb.amount();
        }
        return total;
    }

    @Test
    void theLaddersOwnRows() throws InterruptedException {
        // The shared hostile band (MonsterEntity line 18: xpDrop = 5).
        assertEquals(5, killAndCollectXp(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5), false),
                "the plain zombie's shared 5");
        // The endermite's own row (EndermiteEntity ctor line 26: xpDrop = 3).
        assertEquals(3, killAndCollectXp(MobType.ENDERMITE, new Position(4.5, 8.0, 0.5), false),
                "the endermite's own 3");
        // The baby zombie's lift (ZombieEntity.getXpDrop: (int)(5 * 2.5) = 12).
        assertEquals(12, killAndCollectXp(MobType.ZOMBIE, new Position(8.5, 8.0, 0.5), true),
                "the baby zombie's lifted 12");
    }
}
