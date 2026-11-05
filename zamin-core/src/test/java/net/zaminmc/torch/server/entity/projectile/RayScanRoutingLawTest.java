package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.endcrystal.EnderCrystal;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PE-19 — the entity scan's own law seats (the manager level): the
 * exact {@code Box.clip} port's math (the reference's Box.java lines
 * 204-280 + Vec3d.intermediateWithX/Y/Z lines 94-154), and the struck
 * body's family routing over the segment consult (the crystal pop, the
 * dragon-part wound, the pearl's owner pass-through) — the arms the
 * engine-level seat drives through the real census.
 */
class RayScanRoutingLawTest {

    // ------------------------------------------------------------------
    // The clip math — the reference's Box.clip, ported 1:1
    // ------------------------------------------------------------------

    /**
     * The axis-aligned entry: a segment flying +Z into a box crosses
     * the z-min face — the intercept ON that face, the squared distance
     * the z-travel alone.
     */
    @Test
    void theClipAnswersTheEntryFace() {
        ProjectileManager.SegmentClip clip = ProjectileManager.clipSegment(
                new Position(0.0, 0.0, 0.0), new Position(0.0, 0.0, 4.0),
                -1.0, -1.0, 2.0, 1.0, 1.0, 3.0);
        assertNotNull(clip, "the segment enters the box's z-min face");
        assertEquals(2.0, clip.at().z(), 1.0E-9, "the intercept lies ON the face");
        assertEquals(0.0, clip.at().x(), 1.0E-9);
        assertEquals(0.0, clip.at().y(), 1.0E-9);
        assertEquals(4.0, clip.squaredDistance(), 1.0E-9,
                "the d2 is the squared z-travel (the x/y unchanged)");
    }

    /**
     * The graze boundary is INCLUSIVE (the reference's containsYZ/XZ/XY
     * use {@code >=} and {@code <=}): a segment crossing the box's
     * z-min face exactly ON the x edge still lands the hit.
     */
    @Test
    void theGrazeBoundaryIsInclusive() {
        // The segment at x == maxX exactly (the box's eastern edge).
        ProjectileManager.SegmentClip clip = ProjectileManager.clipSegment(
                new Position(1.0, 0.0, 0.0), new Position(1.0, 0.0, 4.0),
                -1.0, -1.0, 2.0, 1.0, 1.0, 3.0);
        assertNotNull(clip,
                "the crossing exactly ON the box's x edge counts (the inclusive bounds)");
        assertEquals(2.0, clip.at().z(), 1.0E-9);
        // One ten-millionth further out and the crossing misses the face.
        assertNull(ProjectileManager.clipSegment(
                new Position(1.0000001, 0.0, 0.0), new Position(1.0000001, 0.0, 4.0),
                -1.0, -1.0, 2.0, 1.0, 1.0, 3.0),
                "the crossing past the edge never counts");
    }

    /**
     * The origin-inside law (the reference's own): a segment STARTING
     * inside the box crosses the EXIT face — the body IS a hit
     * candidate (the grown hull's inner band), unlike the block walk's
     * entry test.
     */
    @Test
    void theOriginInsideCrossesTheExitFace() {
        ProjectileManager.SegmentClip clip = ProjectileManager.clipSegment(
                new Position(0.0, 0.0, 0.5), new Position(0.0, 0.0, 4.0),
                -1.0, -1.0, 0.0, 1.0, 1.0, 3.0);
        assertNotNull(clip, "the origin-inside segment crosses the exit face");
        assertEquals(3.0, clip.at().z(), 1.0E-9, "the intercept is the EXIT (z-max) face");
        assertEquals(6.25, clip.squaredDistance(), 1.0E-9,
                "the d2 runs from the origin to the exit face");
    }

    /**
     * The parallel-outside reject: a segment whose x never moves lies
     * outside the box's x band — no face crossing ever qualifies.
     */
    @Test
    void theParallelOutsideNeverCrosses() {
        assertNull(ProjectileManager.clipSegment(
                new Position(5.0, 0.0, 0.0), new Position(5.0, 0.0, 4.0),
                -1.0, -1.0, 2.0, 1.0, 1.0, 3.0),
                "the segment parallel to the x planes, outside the band, never enters");
        assertNull(ProjectileManager.clipSegment(
                new Position(0.0, 0.0, 5.0), new Position(0.0, 0.0, 9.0),
                -1.0, -1.0, 2.0, 1.0, 1.0, 3.0),
                "the segment ends short of the box");
    }

    /**
     * The closest face wins: a diagonal segment crossing two faces'
     * planes picks the nearer crossing (the squared-distance compare).
     */
    @Test
    void theClosestFaceCrossingWins() {
        // The segment enters through the z-min face at t=0.25 (z=1.0)
        // and would exit through the x-max plane at t=0.5 — the entry
        // is closer to the origin.
        ProjectileManager.SegmentClip clip = ProjectileManager.clipSegment(
                new Position(0.0, 0.0, 0.0), new Position(2.0, 0.0, 4.0),
                -1.0, -1.0, 1.0, 1.0, 1.0, 3.0);
        assertNotNull(clip);
        assertEquals(1.0, clip.at().z(), 1.0E-9, "the z-min entry (t=0.25) wins");
    }

    // ------------------------------------------------------------------
    // The struck body's routing — the rigged scan over the manager
    // ------------------------------------------------------------------

    /** The no-body hit resolver (the scan is the rigged consult). */
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

    /** The minimal combat sink (no mob/player arm runs in these seats). */
    private static final class NoCombat implements ProjectileManager.CombatSink {
        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            return false;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The all-air world (no block ever consults solid). */
    private static final class AirWorld implements ProjectileManager.SolidQuery {
        @Override
        public boolean solid(double x, double y, double z) {
            return false;
        }
    }

    private static MobEntity dragon() {
        return new MobEntity(50, MobType.ENDER_DRAGON, new Position(100.5, 60.0, 100.5),
                new Random(7), new MobEntity.WorldQuery() {
                    @Override
                    public boolean isSolid(double x, double y, double z) {
                        return false;
                    }

                    @Override
                    public Position nearestPlayer(double x, double y, double z, double range) {
                        return null;
                    }
                });
    }

    /**
     * THE CRYSTAL ROUTING: the scan's crystal arm routes the pop — the
     * sink receives the BODY whole (the grown hull's intercept lies
     * outside the ungrown box the old point consult read), the arrow
     * consumed with the bowhit (VT-018's landed-hit law: the pop IS a
     * landed hit).
     */
    @Test
    void theCrystalArmRoutesTheBodyWhole() {
        FxManager fx = new FxManager();
        List<String> sounds = new ArrayList<>();
        fx.addListener(event -> {
            if (event instanceof FxManager.Sound sound) {
                sounds.add(sound.name());
            }
        });
        EnderCrystal crystal = new EnderCrystal(77, 100.5, 64.0, 100.5, 0.0f, 0);
        List<EnderCrystal> popped = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(
                new AirWorld(), new NoHits(), new NoCombat(), fx, new Random(1), 1_000);
        manager.setCrystalSink(popped::add);
        // The rigged scan: the crystal's grown hull struck at its
        // z-min face (a graze the ungrown box never contained).
        manager.setBodyScan((from, to, selfId, throwerId, airTicks, immunityTicks, dimension)
                -> ProjectileManager.StruckBody.crystal(crystal, new Position(100.5, 64.0, 99.2)));
        ProjectileEntity arrow = manager.launch(ProjectileEntity.Kind.ARROW, -1,
                new Position(100.5, 64.5, 90.5), 0.0, 0.0, 2.0, 1);
        assertNotNull(arrow, "the arrow launched");

        manager.tick(List.of());

        assertEquals(1, popped.size(), "the crystal pop routed through the body form");
        assertSame(crystal, popped.get(0), "the sink receives the struck body whole");
        assertNull(manager.byId(arrow.entityId()), "the arrow consumed at the crystal");
        assertTrue(sounds.contains("random.bowhit"),
                "the pop's landed verdict plays the bowhit (VT-018's law)");
    }

    /**
     * THE DRAGON-PART ROUTING: the scan's dragon arm carries the struck
     * dragon + part whole — the sink's wound walk receives them with
     * the intercept, the per-kind damage, and the explosive/shooter
     * gates (the fireball family's own flags).
     */
    @Test
    void theDragonPartArmCarriesTheStruckPart() {
        MobEntity dragon = dragon();
        MobEntity.DragonPart head = dragon.dragonParts()[0];
        List<String> wounds = new ArrayList<>();
        ProjectileManager manager = new ProjectileManager(
                new AirWorld(), new NoHits(), new NoCombat(), new FxManager(),
                new Random(1), 2_000);
        manager.setDragonSink((struckDragon, part, at, damage, explosive, shooterId) -> {
            wounds.add(struckDragon.entityId() + ":" + part.name + ":" + at.z()
                    + ":" + damage + ":" + explosive + ":" + shooterId);
            return true;
        });
        manager.setBodyScan((from, to, selfId, throwerId, airTicks, immunityTicks, dimension)
                -> ProjectileManager.StruckBody.dragonPart(dragon, head,
                        new Position(100.5, 63.0, 97.0)));
        // A WITHER SKULL (the explosive arm true) thrown by engine id 9.
        ProjectileEntity skull = manager.launchWitherSkull(9,
                new Position(100.5, 63.0, 90.5), 0.0, 1.0, 0.0, false, 1);
        assertNotNull(skull, "the skull launched");

        manager.tick(List.of());

        assertEquals(1, wounds.size(), "the dragon-part wound routed once");
        assertTrue(wounds.get(0).startsWith("50:head:97.0:"),
                "the sink receives the dragon + part + intercept + the flat 8.0"
                        + " skull damage — observed " + wounds.get(0));
        assertTrue(wounds.get(0).endsWith(":true:9"),
                "the explosive flag and the shooter id ride (the fireball family's"
                        + " gates) — observed " + wounds.get(0));
        assertNull(manager.byId(skull.entityId()), "the skull consumed at the part");
    }

    /**
     * THE PEARL'S OWNER PASS-THROUGH: the struck owner consumes the
     * tick's collision work WITHOUT a removal (EnderPearlEntity.
     * onCollision lines 33-35's early return) — the pearl keeps flying,
     * the block arm never consults.
     */
    @Test
    void thePearlsOwnerPassThroughConsumesWithoutRemoval() {
        PlayerSession owner = new PlayerSession(UUID.randomUUID(), "owner",
                new LinkStub());
        owner.assignEngineEntityId(42); // the engine registration's own id
        ProjectileManager manager = new ProjectileManager(
                new AirWorld(), new NoHits(), new NoCombat(), new FxManager(),
                new Random(1), 3_000);
        List<Position> teleports = new ArrayList<>();
        manager.setPearlSink((throwerId, at) -> teleports.add(at));
        // The rigged scan answers the OWNER every tick.
        manager.setBodyScan((from, to, selfId, throwerId, airTicks, immunityTicks, dimension)
                -> ProjectileManager.StruckBody.player(owner, from));
        ProjectileEntity pearl = manager.launch(ProjectileEntity.Kind.ENDER_PEARL, 42,
                new Position(10.5, 64.0, 10.5), 0.0, -30.0, 1.5, 0);
        assertNotNull(pearl, "the pearl launched (well past the 5-tick window)");

        // Age the pearl past the shooter's window (5 ticks), then walk
        // the owner-striking ticks: the pearl must survive them whole.
        for (int i = 0; i < 10; i++) {
            manager.tick(List.of());
        }
        assertNotNull(manager.byId(pearl.entityId()),
                "the owner pass-through never removes the pearl (the early return)");
        assertTrue(teleports.isEmpty(),
                "the owner pass-through never teleports (no wound, no teleport)");
        assertFalse(pearl.inGround(), "the pass-through never sticks the pearl");
    }

    private static final class LinkStub implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        assertTrue(expected == actual, message);
    }
}
