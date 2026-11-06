package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave PP — THE MOB-MOB COLLISION PUSH FAMILY (LivingEntity.mobTick's
 * "push" block lines 1467-1476 + pushAwayCollidingEntities lines
 * 1478-1492 + Entity.push lines 969-1005 + IronGolemEntity.pushAway
 * lines 94-100): the entity-collision separation the pre-fix engine
 * carried NO walk for at all — crowded bodies never drifted apart, and
 * the golem's pushAway carrier (a pushed Monster, never a creeper, on a
 * 1-in-20 dice, becomes the golem's target) had nothing to ride. THE
 * GATHER: the pusher's own shape grown (0.2, 0.0, 0.2) — the horizontal
 * cushion alone — each candidate's OWN box intersecting STRICT both
 * sides, isPushable filtering (the bat's false, the ridden horse's
 * false, every other living body's !removed). THE PUSH: the
 * absMax-normalized axis, the sqrt re-normalization, the 1/d2 cap at
 * 1.0, the 0.05 scale, the mutual separation with each side's own
 * rider gate. THE HONEST TAIL this wave closes with the same law: the
 * EXCESS KILL'S SILENT DEATH VOICE (takeDamage lines 717-723 —
 * {@code if (flag && s != null)}: the death sound rides the flag gate
 * alone; the animation at die() line 772 runs regardless).
 */
class MobPushCollisionParityDiscoveryTest {

    /** A flat-world stub carrying the seats' own consults. */
    private static final class PushWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return false;
        }
    }

    private static MobManager managerIn(PushWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                995_000, (x, z) -> 8);
    }

    /** The pinned body (NoAI): the mind and the movement stand still, but
     *  the push walk still runs — the reference's push block carries no
     *  isLocallyControlled gate, and the seats read the velocity the walk
     *  writes with no goal noise on top. */
    private static MobEntity pinned(MobManager manager, MobType type, Position at) {
        MobEntity mob = manager.spawnAt(type, at);
        assert mob != null : "the spawn never cancels in this seat";
        mob.setNoAi(true);
        return mob;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the crowding separation (mutual, both walks)
    // ------------------------------------------------------------------

    /**
     * Two bodies 0.2 apart in one cell: after ONE tick both carry the
     * push — the LEFT body negative x-velocity, the RIGHT positive (the
     * mutual separation, each side's own walk adding once — the vanilla
     * doubling), the impulse's dominant-axis shape
     * {@code 2 * sqrt(0.2) * 0.05}. RED on the pre-fix engine (no walk —
     * the velocities stayed exactly zero however crowded).
     */
    @Test
    void crowdedBodiesSeparateMutually() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 80L);
        MobEntity left = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        MobEntity right = pinned(manager, MobType.COW, new Position(0.7, 8.0, 0.5));

        manager.tick(List.of(), 6000L);

        assertTrue(left.velocityX() < -0.02,
                "the left body drifts left (the pre-fix engine never moved"
                        + " a crowded body)");
        assertTrue(right.velocityX() > 0.02,
                "the right body drifts right");
        assertEquals(0.0, left.velocityZ(), 1e-9,
                "the push is horizontal alone (the axis pair is pure x)");
        // The exact impulse: absMax = 0.2, sqrt(0.2) = 0.44721, d3 clamped
        // at 1.0, x0.05, ONE add per walk, TWO walks (each body's own).
        // The tick-order decay the loop's own shape carries: the second
        // body's first impulse (from the first body's walk) decays once
        // inside its own tick — the reference's non-controlled arm
        // (LivingEntity lines 1415-1419, the engine's decayVelocity) —
        // before its own walk adds the second.
        double half = Math.sqrt(0.2) * 0.05;
        assertEquals(-2.0 * half, left.velocityX(), 1e-9,
                "Entity.push lines 981-993's exact axis shape (both halves"
                        + " land after the left body's own tick)");
        assertEquals(half * 1.98, right.velocityX(), 1e-9,
                "the mirrored impulse with the tick-order decay (the first"
                        + " half x0.98 + the second whole)");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the grown-0.2 cushion band (STRICT intersection)
    // ------------------------------------------------------------------

    /**
     * The gather's own edge: a body whose own box only TOUCHES the
     * pusher's grown box is never pushed (Box.intersects' STRICT both
     * sides); one block-cell inside the cushion is. The cow pair's
     * touch point sits at 0.45 + 0.45 + 0.2 = 1.1 from the pusher's
     * center — 1.09 gathers, 1.11 never does.
     */
    @Test
    void theGrownCushionBandGathersStrict() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 81L);
        MobEntity pusher = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        MobEntity inside = pinned(manager, MobType.COW, new Position(1.59, 8.0, 0.5));

        manager.tick(List.of(), 6000L);
        assertTrue(inside.velocityX() > 0.02,
                "1.09 inside the cushion: the box intersects and the push"
                        + " lands (the dominant axis's full 0.05 x2)");
        assertEquals(-0.1, pusher.velocityX(), 1e-9,
                "the pusher takes its own mirrored 0.05 x2 (absMax > 1: d3"
                        + " under the cap, the axis normalizes to exactly 1)");

        PushWorld far = new PushWorld();
        MobManager farManager = managerIn(far, 81L);
        pinned(farManager, MobType.COW, new Position(0.5, 8.0, 0.5));
        MobEntity outside = pinned(farManager, MobType.COW,
                new Position(1.61, 8.0, 0.5));

        farManager.tick(List.of(), 6000L);
        assertEquals(0.0, outside.velocityX(), 1e-9,
                "1.11 beyond the cushion: exactly touching never"
                        + " intersects — no push, no velocity, nothing");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the bat's empty carrier (a control, green by design)
    // ------------------------------------------------------------------

    /**
     * The bat never pushes (BatEntity lines 63-65's EMPTY
     * pushAwayCollidingEntities) and is never pushed (isPushable line
     * 54's false) — a cow and a bat may stand in one cell forever. A
     * CONTROL seat: the pre-fix engine was green here too (no walk at
     * all); the law pins the empty carrier against future walks.
     */
    @Test
    void theBatNeverPushesAndIsNeverPushed() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 82L);
        MobEntity cow = pinned(manager, MobType.COW, new Position(0.5, 8.0, 0.5));
        MobEntity bat = pinned(manager, MobType.BAT, new Position(0.7, 8.0, 0.5));

        manager.tick(List.of(), 6000L);

        assertEquals(0.0, cow.velocityX(), 1e-9,
                "the cow's walk never pushes the bat (the empty carrier)");
        assertEquals(0.0, bat.velocityX(), 1e-9,
                "the bat is never pushed (isPushable false)");
    }

    // ------------------------------------------------------------------
    // Seat 4 — the golem's pushAway carrier (the wave's own head)
    // ------------------------------------------------------------------

    /**
     * THE GOLEM'S OWN REFLEX (IronGolemEntity.pushAway lines 94-100): a
     * Monster crowding the golem rolls the 1-in-20 dice each tick, and
     * the winner becomes the golem's target — written on the
     * mob-target seat the defense consult owns, the mind chasing
     * whichever stands (the endermite here — a Monster the census also
     * hunts, but the census writes its OWN seat; the mob-target seat is
     * the pushAway arm's alone). RED on the pre-fix engine: the carrier
     * had nothing to ride — a crowded golem never turned on its
     * crowders.
     */
    @Test
    void theGolemTurnsOnItsCrowdingMonster() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 83L);
        MobEntity golem = pinned(manager, MobType.IRON_GOLEM,
                new Position(0.5, 8.0, 0.5));
        MobEntity endermite = pinned(manager, MobType.ENDERMITE,
                new Position(0.7, 8.0, 0.5));

        // The endermite is pinned at the golem's flank: the roll repeats
        // every tick, and the shared stream draws exactly the golem's own
        // nextInt(20) per tick (the pinned bodies carry no other shared
        // draws). The winner lands well inside 200 ticks.
        boolean landed = false;
        for (int i = 0; i < 200 && !landed; i++) {
            manager.tick(List.of(), 6000L);
            landed = golem.mobAttackTarget() == endermite;
        }
        assertTrue(landed,
                "the 1-in-20 dice crowns the crowding Monster as the"
                        + " golem's target (the pre-fix engine had no carrier"
                        + " — the golem never reacted to crowding)");
        assertEquals(MobType.ENDERMITE, golem.mobAttackTarget().type(),
                "the pushAway write rides the mob-target seat");
    }

    // ------------------------------------------------------------------
    // Seat 5 — the creeper exclusion (a control, green by design)
    // ------------------------------------------------------------------

    /**
     * THE CREEPER NEVER (IronGolemEntity.pushAway line 96's
     * {@code !(entity instanceof CreeperEntity)}): a crowding creeper
     * rolls nothing and is never written — the village's own law (the
     * golem escorts its explosive neighbour rather than feuding with
     * it). A CONTROL seat: green on the pre-fix engine too.
     */
    @Test
    void theCreeperCrowderIsNeverWritten() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 84L);
        MobEntity golem = pinned(manager, MobType.IRON_GOLEM,
                new Position(0.5, 8.0, 0.5));
        MobEntity creeper = pinned(manager, MobType.CREEPER,
                new Position(0.7, 8.0, 0.5));

        for (int i = 0; i < 200; i++) {
            manager.tick(List.of(), 6000L);
            assertNull(golem.mobAttackTarget(),
                    "the creeper exclusion holds on every tick");
        }
    }

    // ------------------------------------------------------------------
    // Seat 6 — the excess kill's SILENT death voice
    // ------------------------------------------------------------------

    /**
     * THE HONEST TAIL (takeDamage lines 717-723): a kill landed through
     * the hurt frame's EXCESS arm dies SILENT — the death voice rides
     * the flag gate alone ({@code if (flag && s != null)}), the
     * animation running regardless. A fresh killing blow stays VOICEFUL.
     */
    @Test
    void theExcessKillDiesSilentAndTheFreshKillVoiceful() {
        PushWorld world = new PushWorld();
        MobManager manager = managerIn(world, 85L);
        final List<Boolean> voiceVerdicts = new ArrayList<>();
        manager.addListener(new MobManager.Listener() {
            @Override public void onMobSpawned(MobEntity mob) { }
            @Override public void onMobMoved(MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession rider) { }
            @Override public void onMobHurt(MobEntity mob) { }
            @Override public void onMobDied(MobEntity mob) {
                voiceVerdicts.add(false); // the legacy voiceful form
            }
            @Override public void onMobDied(MobEntity mob, boolean voiceless) {
                voiceVerdicts.add(voiceless);
            }
            @Override public void onMobRemoved(MobEntity mob, String reason) { }
            @Override public void onMobAttackedPlayer(MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession target,
                    float damage) { }
            @Override public void onMobSound(MobEntity mob, String soundName) { }
            @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
            @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
            @Override public void onMobSheared(MobEntity mob, int woolCount) { }
            @Override public void onMobCoatRegrown(MobEntity mob) { }
            @Override public void onMobExploded(MobEntity mob) { }
        });

        // The excess kill: 8.0 fresh (10 -> 2 health, the frame standing
        // at 8.0), then 11.0 inside the window's first half pays its 3.0
        // excess alone — and the 2-health body dies by it.
        MobEntity byExcess = manager.spawnAt(MobType.COW,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(byExcess);
        assertTrue(manager.hurt(byExcess, 8.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE));
        assertTrue(manager.hurt(byExcess, 11.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE));
        assertTrue(byExcess.dead(), "the 3.0 excess kills the 2-health body");
        assertEquals(List.of(true), voiceVerdicts,
                "the excess kill dies SILENT — the death voice rides the"
                        + " flag gate alone (the pre-fix wire played it)");

        // The fresh kill: a full hit on a healthy body stays VOICEFUL.
        voiceVerdicts.clear();
        MobEntity byFresh = manager.spawnAt(MobType.COW,
                new Position(3.5, 8.0, 3.5));
        assertNotNull(byFresh);
        assertTrue(manager.hurt(byFresh, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE));
        assertTrue(byFresh.dead());
        assertEquals(List.of(false), voiceVerdicts,
                "the fresh killing blow keeps its death voice");
    }
}
