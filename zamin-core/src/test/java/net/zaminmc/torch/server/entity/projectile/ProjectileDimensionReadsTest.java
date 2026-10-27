package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave TF-9 — the projectile family's dimension reads (the reference's
 * Entity.world, the mob query's own Slice-25d shape generalized): the
 * launch walks stamp the body's dimension, the shared solid query flips
 * before every collision walk, the hit resolver and the arrow pickup walk
 * consult it, and the family's sinks carry it.
 */
class ProjectileDimensionReadsTest {

    /**
     * The recording solid query: the block read resolves through the
     * ACTIVE dimension the manager last flipped to (the overworld's
     * ceiling at y=10; the nether's own floor at y<=2 — the same
     * coordinates answer DIFFERENTLY per world, the read the flip owns).
     */
    private static final class DimensionSolid implements ProjectileManager.SolidQuery {
        int lastDimension = Integer.MIN_VALUE;
        int switches;

        @Override
        public void setActiveDimension(int dimension) {
            this.lastDimension = dimension;
            this.switches++;
        }

        @Override
        public boolean solid(double x, double y, double z) {
            if (lastDimension == 0) {
                return y >= 10.0; // the overworld's ceiling
            }
            if (lastDimension == -1) {
                return y <= 2.0; // the nether's own floor
            }
            return false;
        }
    }

    /** The recording combat sink (the dimension-filtered hits land here). */
    private static final class RecordingCombat implements ProjectileManager.CombatSink {
        final List<MobEntity> mobHits = new ArrayList<>();
        final List<PlayerSession> playerHits = new ArrayList<>();

        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            mobHits.add(mob);
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
            playerHits.add(player);
        }

        @Override
        public void chickenHatch(Position position, int dimension) { }
    }

    /** The no-mob hit resolver (the dimension gates live in the consults). */
    private static final class NoHits implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return null;
        }

        @Override
        public PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    @Test
    void theLaunchWalksStampTheDimension() {
        DimensionSolid solid = new DimensionSolid();
        ProjectileManager manager = new ProjectileManager(solid, new NoHits(),
                new RecordingCombat(), new FxManager(), new Random(21), 1);
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 5, 0), 90, 0, 1.5, -1);
        ProjectileEntity charge = manager.launchFireball(new Position(2, 5, 2),
                1.0, 0.0, 0.0, -1);
        ProjectileEntity ghast = manager.launchGhastFireball(777,
                new Position(4, 5, 4), 1.0, 0.0, 0.0, -1);
        ProjectileEntity plain = manager.launchVector(ProjectileEntity.Kind.SNOWBALL, -1,
                new Position(6, 5, 6), 1.0, 0.0, 0.0, 1.0, 6.0, 0);
        assertEquals(-1, arrow.dimension(), "the stamped launch flies in the nether");
        assertEquals(-1, charge.dimension(), "the stamped fire charge flies in the nether");
        assertEquals(-1, ghast.dimension(), "the stamped ghast shot flies in the nether");
        assertEquals(0, plain.dimension(), "the unstamped launch keeps the overworld 0");
    }

    @Test
    void theCollisionWalkFlipsTheSolidQueryPerBody() {
        DimensionSolid solid = new DimensionSolid();
        ProjectileManager manager = new ProjectileManager(solid, new NoHits(),
                new RecordingCombat(), new FxManager(), new Random(22), 1);
        // Two arrows shot straight up: the overworld one meets the wall at
        // y=10 (the overworld's ceiling), the nether one NEVER does (the
        // same-coordinate wall exists in the overworld alone).
        ProjectileEntity overworld = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 5, 0), 90, -90, 1.0, 0);
        ProjectileEntity nether = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(2, 5, 2), 90, -90, 1.0, -1);

        solid.switches = 0;
        for (int i = 0; i < 8; i++) {
            manager.tick(List.of());
        }
        assertTrue(solid.switches >= 2,
                "both bodies' collision walks flipped the shared query (got " + solid.switches + ")");

        assertTrue(overworld.inGround(),
                "the overworld arrow stuck in the overworld's wall at y=10");
        assertFalse(nether.inGround(),
                "the nether arrow's read answers from the nether alone — no same-coordinate wall");
        assertTrue(nether.position().y() > 10.0,
                "the nether arrow flew clean through the overworld's ceiling coordinates");
    }

    @Test
    void theFireAndExplosionSinksCarryTheDimension() {
        DimensionSolid solid = new DimensionSolid();
        List<Integer> fireDimensions = new ArrayList<>();
        List<Integer> boomDimensions = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(solid, new NoHits(),
                new RecordingCombat(), new FxManager(), new Random(23), 1);
        manager.setFireSink((x, y, z, dimension) -> fireDimensions.add(dimension));
        manager.setExplosionSink((at, power, dimension) -> boomDimensions.add(dimension));

        // A small fireball and a ghast fireball shot straight DOWN from
        // y=5: the nether's own floor (y<=2) catches them — the overworld
        // ceiling (y>=10) never answers in the nether's context.
        manager.launchFireball(new Position(0, 5, 0), 0.0, -1.0, 0.0, -1);
        manager.launchGhastFireball(777, new Position(2, 5, 2), 0.0, -1.0, 0.0, -1);
        for (int i = 0; i < 30; i++) {
            manager.tick(List.of());
        }
        assertEquals(List.of(-1, -1), fireDimensions,
                "both nether fireballs' fire lands through the dimension-carrying sink");
        assertEquals(List.of(-1), boomDimensions,
                "the nether fireball's detonation carries its own dimension");
    }

    @Test
    void thePotionSplashWalkCarriesTheDimension() {
        DimensionSolid solid = new DimensionSolid();
        List<Integer> splashDimensions = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(solid, new NoHits(),
                new RecordingCombat(), new FxManager(), new Random(24), 1);
        manager.setPotionSink((x, y, z, damage, directHit, throwerId, dimension)
                -> splashDimensions.add(dimension));

        ProjectileEntity potion = manager.launchVector(ProjectileEntity.Kind.POTION, -1,
                new Position(0, 5, 0), 0.0, -1.0, 0.0, 1.0, 6.0, -1);
        if (potion != null) {
            potion.setPotionDamage(32732);
        }
        for (int i = 0; i < 30; i++) {
            manager.tick(List.of());
        }
        assertEquals(List.of(-1), splashDimensions,
                "the nether splash's bodies walk carries the potion's own dimension");
    }

    @Test
    void theGroundedArrowPickupGatesOnTheCollectorDimension() {
        DimensionSolid solid = new DimensionSolid();
        RecordingCombat combat = new RecordingCombat();
        ProjectileManager manager = new ProjectileManager(solid, new NoHits(),
                combat, new FxManager(), new Random(25), 1);
        // A collectable arrow stuck in the overworld's wall at (0, 10, 0).
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(0, 5, 0), 90, -90, 1.0, 0);
        arrow.setPickupStatus(1);
        for (int i = 0; i < 10; i++) {
            manager.tick(List.of());
        }
        assertTrue(arrow.inGround(), "the arrow stuck in the overworld's ceiling");

        // A dimension-blind stub session: the pickup walk needs the session
        // object alone (the walk reads player.dimension()); the stub world
        // keeps the same shape the item family's test carries.
        PlayerSession netherPlayer = stubSession("NetherArcher", new Position(0, 10, 0), -1);
        PlayerSession overworldPlayer = stubSession("Archer", new Position(0, 10, 0), 0);

        manager.tick(List.of(netherPlayer));
        assertTrue(manager.byId(arrow.entityId()) != null,
                "the cross-dimension collector never picks the arrow up");
        manager.tick(List.of(netherPlayer, overworldPlayer));
        assertTrue(manager.byId(arrow.entityId()) == null,
                "the own-dimension collector collects it");
    }

    private PlayerSession stubSession(String name, Position at, int dimension) {
        net.zaminmc.torch.server.net.ClientLink link =
                new net.zaminmc.torch.server.net.ClientLink() {
                    @Override public boolean isActive() { return true; }
                    @Override public void kick(String reason) { }
                };
        PlayerSession session = new PlayerSession(
                java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new net.zaminmc.torch.World() {
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
        }, Position.ZERO);
        session.markPlaying();
        session.setDimension(dimension);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }
}
