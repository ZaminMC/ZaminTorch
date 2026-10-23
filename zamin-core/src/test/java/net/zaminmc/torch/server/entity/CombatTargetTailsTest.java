package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 16b combat row's ledgered tails — the five family slices:
 *
 * <ul>
 *   <li>the iron golem's target law (the creeper exclusion, the revenge,
 *       the player-created player gate);</li>
 *   <li>the spider's day-neutral gates (the brightness law — a NEW hunt
 *       starts only in darkness, a live hunt keeps running until the
 *       1-in-100 daylight drop roll lands — and the golem feud arm);</li>
 *   <li>the skeleton's revenge-on-mob (the always-on golem feud, the
 *       ranged-on-mob arm, the mob-shot wound's shooter identity);</li>
 *   <li>the scoreboard-team gates (the hunt scans, the revenge, the
 *       call-others, the player hunt);</li>
 *   <li>the explosion's attacker identity (the wolf's shave and the
 *       victim's revenge on the blast's living source).</li>
 * </ul>
 */
class CombatTargetTailsTest {

    /**
     * The flat stub world carrying the target family's own consults: the
     * mob list (the kind/team-aware scans re-express the live query's
     * law), the brightness gate, the team registry, and the player seat.
     */
    private static final class TargetWorld implements MobEntity.WorldQuery {
        final List<MobEntity> mobs = new ArrayList<>();
        /** The member-keyed teams (the reference's law: players by NAME, mobs by uuid string). */
        final Map<String, String> teams = new HashMap<>();
        /** The brightness every cell answers (the spider's gate reads it at the eye). */
        float brightness = 1.0f;
        /** Whether the line of sight is clear (the golem feud's visibility consult). */
        boolean clearLine = true;
        /** The one player position the nearest scan answers, and her team. */
        Position playerAt;
        String playerTeam;
        /** The stub player's engine id (the forced hunt's position read). */
        int playerEntityId = -1;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return inRange(playerAt, x, y, z, range);
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range,
                                      String excludeTeam) {
            Position found = nearestPlayer(x, y, z, range);
            if (found != null && excludeTeam != null && excludeTeam.equals(playerTeam)) {
                return null; // isTarget's same-team refusal
            }
            return found;
        }

        @Override
        public String teamNameOf(String member) {
            return teams.get(member);
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != kind) {
                    continue;
                }
                double d = distanceSquared(mob.position(), x, y, z);
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public MobEntity nearestMobOfKind(double x, double y, double z, double range,
                                          MobType kind, String excludeTeam) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || mob.type() != kind) {
                    continue;
                }
                String team = teams.get(mob.getUuid().toString());
                if (excludeTeam != null && excludeTeam.equals(team)) {
                    continue; // isTarget's same-team refusal
                }
                double d = distanceSquared(mob.position(), x, y, z);
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public MobEntity nearestHostileMob(double x, double y, double z, double range) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || !mob.type().hostile) {
                    continue;
                }
                double d = distanceSquared(mob.position(), x, y, z);
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public MobEntity nearestHostileMob(double x, double y, double z, double range,
                                           MobType excludeKind, String excludeTeam) {
            MobEntity best = null;
            double bestDistance = range * range;
            for (MobEntity mob : mobs) {
                if (mob.dead() || !mob.type().hostile
                        || (excludeKind != null && mob.type() == excludeKind)) {
                    continue;
                }
                String team = teams.get(mob.getUuid().toString());
                if (excludeTeam != null && excludeTeam.equals(team)) {
                    continue;
                }
                double d = distanceSquared(mob.position(), x, y, z);
                if (d <= bestDistance) {
                    bestDistance = d;
                    best = mob;
                }
            }
            return best;
        }

        @Override
        public float brightness(int x, int y, int z) {
            return brightness;
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            return clearLine;
        }

        @Override
        public Position entityPositionById(int entityId) {
            return entityId == playerEntityId ? playerAt : null;
        }
    }

    private static double distanceSquared(Position p, double x, double y, double z) {
        double dx = p.x() - x;
        double dy = p.y() - y;
        double dz = p.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static Position inRange(Position p, double x, double y, double z, double range) {
        if (p == null || distanceSquared(p, x, y, z) > range * range) {
            return null;
        }
        return p;
    }

    private static MobEntity spawn(TargetWorld world, int id, MobType type, Position at, long seed) {
        MobEntity mob = new MobEntity(id, type, at, new Random(seed), world);
        world.mobs.add(mob);
        return mob;
    }

    // ------------------------------------------------------------------
    // Family 1 — the iron golem's target law
    // ------------------------------------------------------------------

    @Test
    void theGolemSkipsCreepersAndWalksDownTheNextMonster() {
        TargetWorld world = new TargetWorld();
        MobEntity golem = spawn(world, 900, MobType.IRON_GOLEM, new Position(0.5, 8.0, 0.5), 71L);
        // The creeper is the NEARER hostile; the zombie stands behind it.
        MobEntity creeper = spawn(world, 901, MobType.CREEPER, new Position(3.5, 8.0, 0.5), 72L);
        MobEntity zombie = spawn(world, 902, MobType.ZOMBIE, new Position(6.5, 8.0, 0.5), 73L);

        golem.tick(true);
        assertSame(zombie, golem.golemTarget(),
                "the golem's own predicate excludes the creeper (lines 256-258) — the zombie behind it wins");
        assertEquals(MobEntity.Mode.CHASE, golem.mode(), "the golem walks its monster down");

        // The same-team refusal rides the same consult: a teamed golem
        // never hunts its kin.
        world.teams.put(golem.getUuid().toString(), "red");
        world.teams.put(zombie.getUuid().toString(), "red");
        golem.tick(true);
        assertNull(golem.golemTarget(), "a same-team hostile is refused (isTarget lines 102-106)");
        golem.setMobAttackTarget(null);
    }

    @Test
    void theGolemReivengesOnItsMobAttacker() {
        TargetWorld world = new TargetWorld();
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) { }
        }
        MobManager manager = new MobManager(world, new Random(80L), new NoLoot(), 950_000, surface);
        MobEntity golem = manager.spawnAt(MobType.IRON_GOLEM, new Position(0.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(2.5, 8.0, 0.5));

        assertTrue(manager.hurtByMob(zombie, golem, 5.0f));
        assertSame(zombie, golem.mobAttackTarget(),
                "RevengeGoal(this, false) — targetSelector 2, line 55: the golem avenges on its attacker");
    }

    @Test
    void aVillageGolemReivengesOnItsPlayerAttackerABuiltOneRefuses() {
        TargetWorld world = new TargetWorld();
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) { }
        }
        MobManager manager = new MobManager(world, new Random(81L), new NoLoot(), 960_000, surface);
        MobEntity village = manager.spawnAt(MobType.IRON_GOLEM, new Position(0.5, 8.0, 0.5));
        MobEntity built = manager.spawnAt(MobType.IRON_GOLEM, new Position(30.5, 8.0, 0.5));
        built.setGolemPlayerCreated(true);
        PlayerSession attacker = stubPlayer("Attacker", new Position(2.5, 8.0, 0.5));
        world.playerEntityId = attacker.engineEntityId();
        world.playerAt = attacker.position();

        assertTrue(manager.hurt(village, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, null, attacker));
        assertNotNull(village.forcedHuntPosition(),
                "the village golem's RevengeGoal targets the player (canAttack line 137 lets it)");

        assertTrue(manager.hurt(built, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, null, attacker));
        assertNull(built.forcedHuntPosition(),
                "canAttack line 137: a player-built golem never attacks players");

        // The team gate rides the same seat: a teamed golem never avenges
        // on its own kin.
        village.setForcedHuntPlayer(-1); // the prior scene's write clears
        world.teams.put(village.getUuid().toString(), "red");
        world.teams.put("Attacker", "red"); // the player's NAME key (line 1634)
        assertTrue(manager.hurt(village, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, null, attacker));
        assertNull(village.forcedHuntPosition(), "isTarget's same-team refusal");
    }

    // ------------------------------------------------------------------
    // Family 2 — the spider's day-neutral gates
    // ------------------------------------------------------------------

    @Test
    void theSpiderHuntsThePlayerOnlyInDarkness() {
        TargetWorld world = new TargetWorld();
        MobEntity spider = spawn(world, 910, MobType.SPIDER, new Position(0.5, 8.0, 0.5), 82L);
        world.playerAt = new Position(6.5, 8.0, 0.5);

        // TargetGoal.canStart lines 222-226: brightness >= 0.5 refuses the
        // hunt — the clock never enters the law (tick(true) is a night tick).
        world.brightness = 0.6f;
        spider.tick(true);
        assertFalse(spider.mode() == MobEntity.Mode.CHASE,
                "a bright spider never starts the hunt, night or not");

        world.brightness = 0.3f;
        spider.tick(true);
        assertEquals(MobEntity.Mode.CHASE, spider.mode(), "the dark arms the hunt");
    }

    @Test
    void theDaylightDropRollEndsARunningHunt() {
        // SpiderAttackGoal.shouldContinue lines 200-208: in the brightness
        // a live hunt KEEPS running (the plain continue) until the 1-in-100
        // roll lands — the quirk that gives a sunrise spider its ~100-tick
        // carryover. Deterministic under the fixed seed; the class reruns
        // twice (the repo's timing-sensitive discipline).
        TargetWorld world = new TargetWorld();
        MobEntity spider = spawn(world, 911, MobType.SPIDER, new Position(0.5, 8.0, 0.5), 83L);
        world.playerAt = new Position(6.5, 8.0, 0.5);
        world.brightness = 0.3f;
        spider.tick(true);
        assertEquals(MobEntity.Mode.CHASE, spider.mode());

        world.brightness = 0.6f; // the day breaks
        int stillHunting = 0;
        for (int i = 0; i < 5; i++) {
            spider.tick(true);
            if (spider.mode() == MobEntity.Mode.CHASE) {
                stillHunting++;
            }
        }
        assertTrue(stillHunting > 0,
                "the brightness alone does not end the hunt — the roll does (the plain continue)");

        int dropTick = -1;
        for (int i = 0; i < 2000 && dropTick < 0; i++) {
            spider.tick(true);
            if (spider.mode() != MobEntity.Mode.CHASE) {
                dropTick = i;
            }
        }
        assertTrue(dropTick >= 0, "the 1-in-100 roll lands and the hunt ends");
        // And it stays ended: no new hunt starts in the light.
        for (int i = 0; i < 50; i++) {
            spider.tick(true);
        }
        assertFalse(spider.mode() == MobEntity.Mode.CHASE, "no re-hunt in the brightness");
    }

    @Test
    void theSpiderFeudsWithIronGolemsInTheDarkOnly() {
        TargetWorld world = new TargetWorld();
        MobEntity spider = spawn(world, 912, MobType.SPIDER, new Position(0.5, 8.0, 0.5), 84L);
        MobEntity golem = spawn(world, 913, MobType.IRON_GOLEM, new Position(6.5, 8.0, 0.5), 85L);

        world.brightness = 0.3f;
        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = spider.tickMobTargeting();
        }
        assertSame(golem, found, "targetSelector 3 — the spider hunts the iron golem in the dark");

        spider.setMobAttackTarget(null);
        world.brightness = 0.6f;
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = spider.tickMobTargeting();
        }
        assertNull(found, "the same brightness gate refuses the golem arm");

        // The visibility consult (TargetGoal over ActiveTargetGoal's
        // checkVisibility): no line of sight, no feud.
        world.brightness = 0.3f;
        world.clearLine = false;
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = spider.tickMobTargeting();
        }
        assertNull(found, "the walled-off golem is never targeted");
    }

    // ------------------------------------------------------------------
    // Family 3 — the skeleton's revenge-on-mob
    // ------------------------------------------------------------------

    @Test
    void theSkeletonFeudsWithIronGolemsAlways() {
        TargetWorld world = new TargetWorld();
        MobEntity skeleton = spawn(world, 920, MobType.SKELETON, new Position(0.5, 8.0, 0.5), 86L);
        MobEntity golem = spawn(world, 921, MobType.IRON_GOLEM, new Position(6.5, 8.0, 0.5), 87L);

        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = skeleton.tickMobTargeting();
        }
        assertSame(golem, found,
                "targetSelector 3 — ActiveTargetGoal(IronGolemEntity, true), line 57: always on");

        // The visibility consult (checkVisibility = true).
        world.clearLine = false;
        skeleton.setMobAttackTarget(null);
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = skeleton.tickMobTargeting();
        }
        assertNull(found, "the unseen golem is never targeted");
    }

    @Test
    void theSkeletonShootsAtItsMobTargetInsteadOfMeleeing() {
        TargetWorld world = new TargetWorld();
        MobEntity skeleton = spawn(world, 922, MobType.SKELETON, new Position(0.5, 8.0, 0.5), 88L);
        MobEntity golem = spawn(world, 923, MobType.IRON_GOLEM, new Position(10.5, 8.0, 0.5), 89L);

        // The revenge write (or the feud scan's) hands the golem over: the
        // ranged arm owns it — ProjectileAttackGoal attacks ANY attackTarget
        // (lines 40-48); the skeleton's melee goal is Player-only (lines
        // 43-44).
        skeleton.setMobAttackTarget(golem);
        skeleton.tick(true);

        assertEquals(MobEntity.Mode.STRAFE, skeleton.mode(),
                "the mob target rides the shooting band (the 6-12 hold), not the melee chase");
        assertTrue(skeleton.consumePendingRangedShot(), "the bow is drawn at the golem");
        assertEquals(golem.position(), skeleton.rangedTarget(),
                "the shot aims at the mob target's own position");
        assertFalse(skeleton.consumePendingMeleeAttack(),
                "the melee swing never arms against a mob (the Player-only goal)");
    }

    @Test
    void theMobShotWoundCarriesTheShooterIdentity() {
        TargetWorld world = new TargetWorld();
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) { }
        }
        MobManager manager = new MobManager(world, new Random(90L), new NoLoot(), 970_000, surface);
        MobEntity skeleton = manager.spawnAt(MobType.SKELETON, new Position(0.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(2.5, 8.0, 0.5));
        MobEntity wolf = manager.spawnAt(MobType.WOLF, new Position(4.5, 8.0, 0.5));

        // The arrow-shaped wound through the walk's identity seat
        // (ProjectileDamageSource.getAttacker — the SHOOTER, lines 24-26):
        // the victim's RevengeGoal answers the archer.
        assertTrue(manager.hurt(zombie, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.PROJECTILE, false, false, skeleton));
        assertSame(skeleton, zombie.mobAttackTarget(), "the shot zombie avenges on the archer");

        // The wolf's shave rounds a mob's arrow up to half.
        assertEquals(8.0f, wolf.health(), 1e-6f);
        assertTrue(manager.hurt(wolf, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.PROJECTILE, false, false, skeleton));
        assertEquals(5.0f, wolf.health(), 1e-6f, "(5 + 1) / 2 — the mob-shot arrow shaves");
        assertSame(skeleton, wolf.mobAttackTarget(), "the shot wolf avenges on the archer");
    }

    // ------------------------------------------------------------------
    // Family 4 — the scoreboard-team gates
    // ------------------------------------------------------------------

    @Test
    void theSameTeamRefusalGatesTheHuntScan() {
        TargetWorld world = new TargetWorld();
        MobEntity zombie = spawn(world, 930, MobType.ZOMBIE, new Position(0.5, 8.0, 0.5), 91L);
        MobEntity villager = spawn(world, 931, MobType.VILLAGER, new Position(6.5, 8.0, 0.5), 92L);

        world.teams.put(zombie.getUuid().toString(), "red");
        world.teams.put(villager.getUuid().toString(), "red");
        MobEntity found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = zombie.tickMobTargeting();
        }
        assertNull(found, "a teamed body never hunts its own kin (isTarget lines 102-106)");

        world.teams.put(villager.getUuid().toString(), "blue");
        found = null;
        for (int i = 0; i < 400 && found == null; i++) {
            found = zombie.tickMobTargeting();
        }
        assertSame(villager, found, "the other team's villager is fair game");
    }

    @Test
    void theRevengeAndCallOthersCarryTheTeamGates() {
        TargetWorld world = new TargetWorld();
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) { }
        }
        MobManager manager = new MobManager(world, new Random(92L), new NoLoot(), 980_000, surface);
        MobEntity wolf = manager.spawnAt(MobType.WOLF, new Position(0.5, 8.0, 0.5));
        MobEntity kin = manager.spawnAt(MobType.WOLF, new Position(2.5, 8.0, 0.5));
        MobEntity kinOnAttackerTeam = manager.spawnAt(MobType.WOLF, new Position(4.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(6.5, 8.0, 0.5));

        // The call-others' gate (RevengeGoal.start line 39): kin on the
        // ATTACKER's team never join; the teamless kin do.
        world.teams.put(zombie.getUuid().toString(), "red");
        world.teams.put(kinOnAttackerTeam.getUuid().toString(), "red");

        assertTrue(manager.hurtByMob(zombie, wolf, 5.0f));
        assertSame(zombie, wolf.mobAttackTarget(), "the teamless victim avenges");
        assertSame(zombie, kin.mobAttackTarget(), "the teamless kin joins the call");
        assertNull(kinOnAttackerTeam.mobAttackTarget(),
                "the kin on the attacker's team never joins (isInSameTeam)");

        // The direct refusal: a same-team attacker never angers its kin.
        MobEntity teamedWolf = manager.spawnAt(MobType.WOLF, new Position(8.5, 8.0, 0.5));
        world.teams.put(teamedWolf.getUuid().toString(), "red");
        assertTrue(manager.hurtByMob(zombie, teamedWolf, 5.0f));
        assertNull(teamedWolf.mobAttackTarget(), "isTarget's same-team refusal on the revenge");
    }

    @Test
    void thePlayerHuntRefusesSameTeamPlayers() {
        TargetWorld world = new TargetWorld();
        MobEntity zombie = spawn(world, 940, MobType.ZOMBIE, new Position(0.5, 8.0, 0.5), 93L);
        world.playerAt = new Position(6.5, 8.0, 0.5);

        zombie.tick(true);
        assertEquals(MobEntity.Mode.CHASE, zombie.mode(), "the teamless zombie hunts the player");

        world.teams.put(zombie.getUuid().toString(), "red");
        world.playerTeam = "red";
        zombie.tick(true);
        assertFalse(zombie.mode() == MobEntity.Mode.CHASE,
                "MobEntityPlayerTargetGoal lines 90-94: the same-team player is never hunted");
    }

    // ------------------------------------------------------------------
    // Family 5 — the explosion's attacker identity
    // ------------------------------------------------------------------

    @Test
    void theExplosionWoundCarriesTheBlastAttacker() {
        TargetWorld world = new TargetWorld();
        java.util.function.BiFunction<Integer, Integer, Integer> surface = (x, z) -> 8;
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack) { }
        }
        MobManager manager = new MobManager(world, new Random(94L), new NoLoot(), 990_000, surface);
        MobEntity creeper = manager.spawnAt(MobType.CREEPER, new Position(0.5, 8.0, 0.5));
        MobEntity wolf = manager.spawnAt(MobType.WOLF, new Position(2.5, 8.0, 0.5));
        MobEntity sheep = manager.spawnAt(MobType.SHEEP, new Position(4.5, 8.0, 0.5));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(6.5, 8.0, 0.5));

        // The blast's mob wound through the walk's attacker seat
        // (DamageSource.explosion — the EntityDamageSource over
        // Explosion.getSource(), the creeper itself): the wolf's shave
        // rounds up to half and the revenge answers the blast's author.
        assertEquals(8.0f, wolf.health(), 1e-6f);
        assertTrue(manager.hurt(wolf, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.EXPLOSION, false, false, creeper));
        assertEquals(5.0f, wolf.health(), 1e-6f, "(5 + 1) / 2 — the creeper's blast shaves the wolf");
        assertSame(creeper, wolf.mobAttackTarget(), "the blasted wolf avenges on the creeper");

        // The sheep takes the whole blast (the shave is the wolf's own).
        assertTrue(manager.hurt(sheep, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.EXPLOSION, false, false, creeper));
        assertEquals(sheep.maxHealth() - 5.0f, sheep.health(), 1e-6f);

        // A hostile victim revenges too (RevengeGoal reads the attacker
        // the explosion's damage source carried).
        assertTrue(manager.hurt(zombie, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.EXPLOSION, false, false, creeper));
        assertSame(creeper, zombie.mobAttackTarget(), "the blasted zombie avenges on the creeper");

        // An ownerless blast (TNT by redstone, a fireball) carries no
        // identity: no shave's attacker, no revenge.
        MobEntity ownerless = manager.spawnAt(MobType.ZOMBIE, new Position(8.5, 8.0, 0.5));
        assertTrue(manager.hurt(ownerless, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.EXPLOSION, false, false, null));
        assertNull(ownerless.mobAttackTarget(), "the plain explosion carries no attacker");
    }

    // ------------------------------------------------------------------
    // The player stub (the ItemThrowerPickupTest harness's shape)
    // ------------------------------------------------------------------

    private PlayerSession stubPlayer(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                java.util.UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
                return java.util.List.of();
            }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(
                    net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(
                    net.zaminmc.torch.block.BlockPosition p) { return "plains"; }
        }, Position.ZERO);
        session.markPlaying();
        session.applyMovement(at, Rotation.ZERO, true);
        session.assignEngineEntityId(9_000);
        return session;
    }
}
