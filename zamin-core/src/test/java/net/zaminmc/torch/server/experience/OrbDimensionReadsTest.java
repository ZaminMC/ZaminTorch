package net.zaminmc.torch.server.experience;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave TF-10 — the XP-orb family's dimension reads (the reference's
 * Entity.world, the item family's own TF-9 shape): the orbs carry their
 * own dimension, every spawn walk stamps it, the shared ground flips
 * before each body's tick, and only the body's own dimension's players
 * collect it.
 */
class OrbDimensionReadsTest {

    /**
     * The recording ground: the solid read resolves through the ACTIVE
     * dimension the manager last flipped to (the surface below y=4
     * exists in the OVERWORLD alone — the nether reads empty space).
     */
    private static final class DimensionGround implements ExperienceOrbEntity.Ground {
        int lastDimension = Integer.MIN_VALUE;
        int switches;

        @Override
        public void setActiveDimension(int dimension) {
            this.lastDimension = dimension;
            this.switches++;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return lastDimension == 0 && y < 4.0;
        }
    }

    private PlayerSession playingPlayer(String name, Position at, int dimension) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.setDimension(dimension);
        session.applyMovement(at, Rotation.ZERO, true);
        return session;
    }

    /** Minimal world stub: sessions need a World reference only. */
    private net.zaminmc.torch.World stubWorld() {
        return new net.zaminmc.torch.World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(net.zaminmc.torch.block.BlockPosition p, net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        };
    }

    @Test
    void theSpawnWalksStampTheDimension() {
        ExperienceOrbManager manager = new ExperienceOrbManager(
                new DimensionGround(), new Random(31), 1);
        ExperienceOrbEntity burst = manager.spawnBurst(
                new Position(1.5, 6.0, 1.5), 7, 3, -1).get(0);
        ExperienceOrbEntity single = manager.spawnOrb(
                new Position(2.5, 6.0, 2.5), 5, -1);
        ExperienceOrbEntity split = manager.spawnSplitBurst(
                new Position(3.5, 6.0, 3.5), 20).get(0);
        ExperienceOrbEntity restored = manager.spawnRestored(
                new Position(4.5, 6.0, 4.5), 9, 100, 1);
        ExperienceOrbEntity plain = manager.spawnBurst(
                new Position(5.5, 6.0, 5.5), 7, 3).get(0);
        assertEquals(-1, burst.dimension(), "the stamped burst lives in the nether");
        assertEquals(-1, single.dimension(), "the stamped single lives in the nether");
        assertEquals(0, split.dimension(), "the three-arg split keeps the overworld 0");
        assertEquals(1, restored.dimension(), "the stamped restore lives in the end");
        assertEquals(0, plain.dimension(), "the three-arg burst keeps the overworld 0");
    }

    @Test
    void theTickWalkFlipsTheGroundPerBody() {
        DimensionGround ground = new DimensionGround();
        ExperienceOrbManager manager = new ExperienceOrbManager(ground,
                new Random(32), 1);
        ExperienceOrbEntity overworld = manager.spawnOrb(
                new Position(0.5, 6.0, 0.5), 5, 0);
        ExperienceOrbEntity nether = manager.spawnOrb(
                new Position(2.5, 6.0, 2.5), 5, -1);

        ground.switches = 0;
        for (int i = 0; i < 40; i++) {
            manager.tick(List.of());
        }
        assertTrue(ground.switches >= 2,
                "both bodies' tick walks flipped the shared ground (got " + ground.switches + ")");
        assertEquals(-1, ground.lastDimension,
                "the walk ends on the last body's dimension (the list's own order)");

        // THE READ ITSELF: the overworld orb settles on the surface at
        // y=4; the nether orb NEVER lands — its world holds nothing at
        // these coordinates.
        assertTrue(overworld.onGround(), "the overworld orb rests on the overworld's surface");
        assertFalse(nether.onGround(),
                "the nether orb's read answers from the nether alone — no same-coordinate floor");
    }

    @Test
    void thePickupWalkGatesOnTheCollectorDimension() {
        // The orb-toucher rule runs inside the orb's own world: a player
        // in another dimension never collects an orb at the same
        // coordinates.
        ExperienceOrbManager manager = new ExperienceOrbManager(
                (x, y, z) -> false, new Random(33), 1);
        manager.spawnOrb(new Position(2.5, 4.0, 2.5), 9, 0);
        PlayerSession netherPlayer = playingPlayer("NetherCollector",
                new Position(2.5, 4.0, 2.5), -1);
        PlayerSession overworldPlayer = playingPlayer("Collector",
                new Position(2.5, 4.0, 2.5), 0);

        // The pickup delay (10 ticks) runs out while the cross-dimension
        // collector stands ON the orb's coordinates — never collecting it.
        for (int i = 0; i < 11; i++) {
            manager.tick(List.of(netherPlayer));
        }
        assertEquals(1, manager.size(), "the cross-dimension collector never picks up");
        manager.tick(List.of(netherPlayer, overworldPlayer));
        assertEquals(0, manager.size(), "the own-dimension collector collects");
        assertEquals(9, overworldPlayer.totalXp(),
                "the payout landed on the own-dimension collector");
        assertEquals(0, netherPlayer.totalXp(),
                "the cross-dimension collector's total stayed zero");
    }
}
