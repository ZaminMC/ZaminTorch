package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave G2 — THE GOLEM'S FLANK WALK (GoToEntityTargetGoal, IronGolemEntity
 * line 47's band-2 registration: {@code new GoToEntityTargetGoal(this,
 * 0.9, 32.0F)}): when NO path to the target exists (MeleeAttackGoal
 * .canStart line 57's findPath null — the unreachable target), the golem
 * walks to a position on the TARGET'S OWN SIDE picked by the toward-target
 * half-plane form of {@code TargetFinder.getTargetAwayFromPosition(entity,
 * 16, 7, targetPos)} — the decompiler name's red herring kept honest: the
 * away-FROM-POSITION offset is {@code start − mob}, so the half-plane
 * filter keeps the candidates that lie TOWARD the given position (the MCP
 * family's own MoveTowardsTarget law; the mirrored AWAY half-plane belongs
 * to the getTargetAwayFromENTITY form the flee family rides).
 *
 * <p>THE WALK'S OWN LAWS the seats pin: the 0.9 speed (the ctor parameter
 * over the 0.25 movement base — MovementControl.tick's {@code setSpeed
 * (speed × attribute)} multiplier law, 0.225 blocks per tick — the
 * pre-fix engine's chase band beelined at 0.275); the claim's 32 band
 * (canStart line 39 / shouldContinue line 45 — an unreachable target
 * beyond 32 NEVER moves the golem, the pre-fix beeline marched anyway);
 * and the pick's bounded reach (the route runs to a cell within the 16/7
 * band of the golem's own position, never the target's far cell).</p>
 */
class GolemFlankWalkParityDiscoveryTest {

    /**
     * The flat stub world with the WALL arm the unreachable seats need
     * (an impassable full-height band at a column, spanning every z —
     * the direct route's own refusal geometry).
     */
    private static final class WallWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        /** The impassable wall's column (x) — Integer.MIN_VALUE when no wall stands. */
        int wallX = Integer.MIN_VALUE;

        @Override
        public boolean isSolid(double x, double y, double z) {
            if (Math.floor(y) < 8.0) {
                return true;
            }
            return wallX != Integer.MIN_VALUE
                    && (int) Math.floor(x) == wallX
                    && Math.floor(y) < 13.0; // a 5-high wall, every z
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null; // no players in these seats
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }

        @Override
        public List<MobEntity> hostileMobsInBox(
                double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ) {
            List<MobEntity> found = new ArrayList<>();
            for (MobEntity mob : mobs) {
                if (mob.dead() || !mob.type().hostile) {
                    continue;
                }
                Position p = mob.position();
                double half = mob.type().width / 2.0;
                if (p.x() + half > minX && p.x() - half < maxX
                        && p.y() + mob.type().height > minY && p.y() < maxY
                        && p.z() + half > minZ && p.z() - half < maxZ) {
                    found.add(mob);
                }
            }
            return found;
        }
    }

    private static MobManager managerIn(WallWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                996_500, (x, z) -> 8);
    }

    // ------------------------------------------------------------------
    // Seat 1 — the walled-off target walks the flank, at the goal's 0.9
    // ------------------------------------------------------------------

    /**
     * THE WALL-FACE LOCAL MINIMUM: the golem stands AT the wall's face
     * with the target DEAD AHEAD beyond it — the A*'s own shape returns
     * the null route here (every walkable neighbour is EQUIDISTANT or
     * farther from the target, {@code pathnode == start} — PathFinder
     .buildPath's null arm), so MeleeAttackGoal.canStart fails and BAND
     * 2 CLAIMS. The flank pick: a toward-target cell (the half-plane)
     * whose z-offset rides the pick's own draw — the partial-path A*
     * (the best-effort route, PathFinder's partial arm) forms ALONG the
     * wall toward the pick's z — the golem SLIDES ALONG the wall at the
     * goal's 0.9 over the 0.25 base (0.225 blocks per tick, never the
     * chase band's 0.275), never east past the wall's face. RED on the
     * pre-fix engine: the beeline's dead-east heading walked the body
     * INTO the wall — the swept-box clip held it at ZERO movement
     * however long the target stood.
     */
    @Test
    void theWalledOffTargetWalksTheFlankAtTheGoalsOwnSpeed() {
        WallWorld world = new WallWorld();
        world.wallX = 8;
        MobManager manager = managerIn(world, 90L);
        // The golem AT the wall's face (its cell column 7, the wall at 8).
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM,
                new Position(7.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE,
                new Position(20.5, 8.0, 0.5));
        assert golem != null && zombie != null : "the spawns never cancel here";
        world.mobs.add(golem);
        world.mobs.add(zombie);
        zombie.setNoAi(true); // the target stands still, dead ahead
        golem.setMobAttackTarget(zombie);

        double maxStep = 0.0;
        int flankRateSteps = 0;
        Position before = golem.position();
        double alongWall = 0.0;
        for (int tick = 0; tick < 60; tick++) {
            manager.tick(List.of(), 6000L);
            Position now = golem.position();
            double step = Math.sqrt(
                    (now.x() - before.x()) * (now.x() - before.x())
                            + (now.z() - before.z()) * (now.z() - before.z()));
            maxStep = Math.max(maxStep, step);
            if (step > 0.2 && step < 0.24) {
                flankRateSteps++; // the goal's 0.9 band's own steps
            }
            alongWall = Math.max(alongWall, Math.abs(now.z() - 0.5));
            before = now;
        }
        // The flank's own rate: 0.25 attribute x the goal's 0.9 ctor
        // parameter (MovementControl.tick's multiplier law) = 0.225 per
        // tick. The melee goal's steal (its own claim once a partial
        // route forms from the flank's new z) walks its own band — the
        // engine's chase 1.1 — so the step census carries BOTH rates;
        // the flank's 0.9 law is the sub-0.24 steps alone.
        assertTrue(flankRateSteps >= 5,
                "the flank walk's own 0.9-band steps run (0.225/tick —"
                        + " measured " + flankRateSteps + " of them; the"
                        + " pre-fix engine's beeline stayed clipped at 0.0)");
        assertTrue(maxStep < 0.28,
                "no step exceeds the two legitimate bands (the flank's"
                        + " 0.225 and the chase's 0.275 — measured "
                        + maxStep + ")");
        assertTrue(alongWall > 1.0,
                "the golem slides ALONG the wall toward the pick's z (the"
                        + " partial-path A*'s own shape — measured "
                        + alongWall + ")");
        assertTrue(golem.position().x() < 8.0,
                "the wall's face holds the golem west of the target");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the unreachable target beyond 32 never moves the golem
    // ------------------------------------------------------------------

    /**
     * THE 32 BAND (canStart line 39: {@code squaredDistanceTo >
     * maxDistance * maxDistance} refuses the claim): an unreachable
     * target 40 cells east never arms the flank walk — and with no
     * direct route either, NO goal claims the body's MOVE (the stroll
     * family alone wanders within its own 10-block band at most). The
     * pre-fix engine beelined east at 0.275/tick regardless of distance
     * (RED: the golem crossed x=10 inside 60 ticks).
     */
    @Test
    void theUnreachableTargetBeyondThirtyTwoNeverMovesTheGolem() {
        WallWorld world = new WallWorld();
        world.wallX = 20;
        MobManager manager = managerIn(world, 91L);
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM,
                new Position(0.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE,
                new Position(45.5, 8.0, 0.5));
        assert golem != null && zombie != null : "the spawns never cancel here";
        world.mobs.add(golem);
        world.mobs.add(zombie);
        zombie.setNoAi(true);
        golem.setMobAttackTarget(zombie);

        for (int tick = 0; tick < 60; tick++) {
            manager.tick(List.of(), 6000L);
        }
        assertTrue(golem.position().x() < 10.0,
                "the unreachable target beyond the 32 band never moves the"
                        + " golem (the stroll family alone wanders — measured"
                        + " x=" + golem.position().x() + "; the pre-fix"
                        + " beeline crossed 16 blocks east)");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the reachable target rides the ordinary chase (the control)
    // ------------------------------------------------------------------

    /**
     * The control: with the direct route standing (no wall), the melee
     * goal claims — the ordinary chase at the chase band's own rate, the
     * golem closing to the melee window and the swing landing. The flank
     * never arms while a route exists.
     */
    @Test
    void theReachableTargetRidesTheOrdinaryChase() {
        WallWorld world = new WallWorld();
        MobManager manager = managerIn(world, 92L);
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM,
                new Position(0.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE,
                new Position(8.5, 8.0, 0.5));
        assert golem != null && zombie != null : "the spawns never cancel here";
        world.mobs.add(golem);
        world.mobs.add(zombie);
        zombie.setNoAi(true);
        golem.setMobAttackTarget(zombie);
        double zombieBefore = zombie.health();

        boolean closed = false;
        for (int tick = 0; tick < 60; tick++) {
            manager.tick(List.of(), 6000L);
            double dx = zombie.position().x() - golem.position().x();
            double dz = zombie.position().z() - golem.position().z();
            if (Math.sqrt(dx * dx + dz * dz) <= 2.6) {
                closed = true;
                break;
            }
        }
        assertTrue(closed, "the ordinary chase closes to the melee window");
        // The melee cadence lands on the window's own ticks (the swing
        // when the cooldown opens — the first window tick swings).
        for (int tick = 0; tick < 5; tick++) {
            manager.tick(List.of(), 6000L);
        }
        assertTrue(zombie.health() < zombieBefore || zombie.dead(),
                "the melee cadence lands the heavy hit once the window"
                        + " opens");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the steal's own cadence (the selector's setup-pass law)
    // ------------------------------------------------------------------

    /**
     * THE STEAL POLL'S CADENCE (GoalSelector.tick line 45's own law:
     * {@code this.ticks++ % this.constantThree == 0} with
     * {@code constantThree = 3} — the SETUP PASS the whole canStart
     * consult walk rides): while the flank claim runs, the melee goal's
     * canStart consult STILL RUNS at every third tick of the selector's
     * own counter ({@code canUse(melee)} passes over the live flank —
     * the melee's band-1 precedence beats the flank's band 2 and the
     * flank's {@code canStop()} defaults true, so the lower band never
     * blocks the consult), and the consult's {@code findPath} verdict
     * TRUE claims the body the very same pass (the melee registered
     * FIRST, its moveAlong replacing the flank's route; the flank's own
     * visit that pass sees canUse false — the active melee shares the
     * MOVE control bit at better precedence — and stops). The wall-face
     * local minimum arms the flank (seat 1's geometry), the claim runs
     * established, and then the wall OPENS — the direct route stands at
     * once. The steal lands at the next setup pass: within 3 ticks of
     * the opening, the chase's eastbound walk crossing the wall's line
     * within 2 more. RED on the pre-fix engine: the 20-tick countdown
     * poll left the golem sliding along the wall up to 20 ticks after
     * the route opened (measured: the crossing 10+ ticks late).
     */
    @Test
    void theStealLandsAtTheSelectorsOwnSetupPassCadence() {
        WallWorld world = new WallWorld();
        world.wallX = 8;
        // NIGHT (18000 — midnight): the target zombie stands in the open,
        // and a noon seat would burn it down mid-window (the undead
        // daylight walk) before the steal's own story finishes.
        MobManager manager = managerIn(world, 90L);
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM,
                new Position(7.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE,
                new Position(20.5, 8.0, 0.5));
        assert golem != null && zombie != null : "the spawns never cancel here";
        world.mobs.add(golem);
        world.mobs.add(zombie);
        zombie.setNoAi(true); // the target stands still, dead ahead
        golem.setMobAttackTarget(zombie);

        // The claim runs established: 10 ticks of the flank holding the
        // golem west of the wall's face.
        for (int tick = 0; tick < 10; tick++) {
            manager.tick(List.of(), 18000L);
            assertTrue(golem.position().x() < 8.0,
                    "the flank claim holds the golem west of the wall while"
                            + " the route stands (tick " + tick + ")");
        }
        // THE WALL OPENS — the direct route exists from this tick on.
        world.wallX = Integer.MIN_VALUE;
        int openTick = 10;
        int crossingTick = -1;
        for (int tick = openTick; tick < openTick + 40; tick++) {
            manager.tick(List.of(), 18000L);
            if (golem.position().x() >= 8.0) {
                crossingTick = tick;
                break;
            }
        }
        assertTrue(crossingTick != -1,
                "the opened route's chase crosses the wall's line (the steal"
                        + " claims — the golem measured at x="
                        + golem.position().x() + ")");
        assertTrue(crossingTick - openTick <= 5,
                "the steal lands at the selector's own setup-pass cadence —"
                        + " the direct-route probe within 3 ticks of the"
                        + " opening (MeleeAttackGoal.start adopting the"
                        + " consult's own route the landing pass), the"
                        + " eastbound walk crossing within 2 more (measured "
                        + (crossingTick - openTick) + " ticks; the pre-fix"
                        + " 20-tick countdown poll walked the stale flank"
                        + " route at the chase band for ~30 ticks past the"
                        + " opening)");
    }

}
