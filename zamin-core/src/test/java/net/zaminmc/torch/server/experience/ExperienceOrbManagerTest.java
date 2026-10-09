package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.net.ClientLink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orb system's behavior: bursts split the bundle, ground physics rests
 * the orb, the nearest playing body inside the pickup box absorbs the points
 * into its total, and despawn/void remove orbs without payout.
 */
class ExperienceOrbManagerTest {

    private static final class FlatGround implements ExperienceOrbEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 64.0; // a floor at y=64
        }
    }

    /** An open shaft: no solid block anywhere (the void column). */
    private static final ExperienceOrbEntity.Ground BOTTOMLESS = (x, y, z) -> false;

    private static PlayerSession player(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        return session;
    }

    /** Minimal world stub: sessions need a World reference only. */
    private static net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public net.zaminmc.torch.util.Position spawnPosition() {
                return net.zaminmc.torch.util.Position.ZERO;
            }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
        };
    }

    @Test
    void burstsSplitTheBundleIntoBoundedOrbs() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        List<ExperienceOrbEntity> orbs = manager.spawnBurst(new Position(0, 65, 0), 10, 3);
        assertEquals(3, orbs.size(), "ten points split across three orbs");
        int total = orbs.stream().mapToInt(ExperienceOrbEntity::amount).sum();
        assertEquals(10, total, "the split conserves the bundle");
        assertTrue(manager.all().size() == 3, "all orbs live in the manager");
    }

    @Test
    void nonPositiveAmountsSpawnNothing() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        assertEquals(0, manager.spawnBurst(new Position(0, 65, 0), 0, 3).size());
        assertEquals(0, manager.spawnBurst(new Position(0, 65, 0), -4, 3).size());
    }

    @Test
    void orbsFallAndRestOnTheGround() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        manager.spawnOrb(new Position(0.5, 70, 0.5), 5);
        for (int i = 0; i < 200; i++) {
            manager.tick(List.of());
        }
        ExperienceOrbEntity orb = manager.all().get(0);
        assertTrue(orb.onGround(), "the orb rests on the floor");
        assertEquals(64.0 + ExperienceOrbEntity.HALF_HEIGHT, orb.position().y(), 0.01,
                "the orb's bottom sits on the block surface");
    }

    @Test
    void pickupMovesTheBundleIntoTheBody() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        manager.spawnOrb(new Position(0.5, 64.4, 0.5), 7);
        PlayerSession collector = player("Collector");
        collector.applyMovement(new Position(0.5, 64.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        for (int i = 0; i < ExperienceOrbEntity.PICKUP_DELAY_TICKS + 2; i++) {
            manager.tick(List.of(collector));
        }
        assertEquals(0, manager.size(), "the orb is gone after the pickup");
        assertEquals(7, collector.totalXp(), "the points rode into the total");
    }

    @Test
    void orbsWaitOutThePickupDelay() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        manager.spawnOrb(new Position(0.5, 64.4, 0.5), 7);
        PlayerSession collector = player("Eager");
        collector.applyMovement(new Position(0.5, 64.0, 0.5),
                net.zaminmc.torch.util.Rotation.ZERO, true);
        // One tick less than the delay: still sitting there.
        manager.tick(List.of(collector));
        assertEquals(1, manager.size(), "fresh orbs are not collectible yet");
    }

    @Test
    void voidOrbsVanishWithoutPayout() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                BOTTOMLESS, new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnOrb(new Position(0.5, 10.4, 0.5), 7);
        // The shaft never blocks: the orb falls through the kill plane.
        for (int i = 0; i < 300; i++) {
            manager.tick(List.of());
            if (manager.size() == 0) {
                break;
            }
        }
        assertEquals(0, manager.size(), "the void consumed the orb");
    }

    @Test
    void expiredOrbsLeaveWithoutPayout() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new FlatGround(), new Random(42), 1);
        ExperienceOrbEntity orb = manager.spawnOrb(new Position(0.5, 64.4, 0.5), 7);
        List<String> reasons = new ArrayList<>();
        manager.addListener(new ExperienceOrbManager.Listener() {
            @Override public void onOrbSpawned(ExperienceOrbEntity e) { }
            @Override public void onOrbMoved(ExperienceOrbEntity e) { }
            @Override public void onOrbCollected(ExperienceOrbEntity e, PlayerSession p) { }
            @Override public void onOrbRemoved(ExperienceOrbEntity e, String reason) {
                reasons.add(reason);
            }
        });
        for (int i = 0; i < ExperienceOrbEntity.DESPAWN_TICKS + 1; i++) {
            manager.tick(List.of());
        }
        assertEquals(0, manager.size(), "the orb aged out");
        assertEquals("despawned", reasons.get(reasons.size() - 1));
        assertTrue(orb.amount() > 0);
    }
}
