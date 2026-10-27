package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The zombie's attributes (VIII10 — ZombieEntity's own rows): the
 * spawn-time dice (the door-break arming roll + the leader roll —
 * initialize lines 430/447-451, the seeded walks against their verbatim
 * reference shadows), the door-break goal's own gates (BreakDoorGoal +
 * AbstractDoorInteractGoal: the wooden-closed-family consult, the
 * mobGriefing gate, the sticky 240-tick walk, the HARD-alone completion),
 * and the reinforcement caller (takeDamage lines 199-227: the HARD gate,
 * the flank spawn, the charges, the census cap, the baby refusal).
 */
class ZombieAttributesTest {

    /** The flat world with a door cell, the difficulty/mobGriefing pair, and the dark band. */
    private static class DoorWorld implements MobEntity.WorldQuery {
        boolean hard = true;
        boolean griefing = true;
        boolean dark = true;
        /** The door half's identifier at the cell (0, 5, 0), null when none. */
        Identifier upperAt;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean difficultyHard() {
            return hard;
        }

        @Override
        public boolean mobGriefing() {
            return griefing;
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return dark ? 0 : 15;
        }

        @Override
        public Identifier blockIdAt(int x, int y, int z) {
            if (upperAt != null && x == 0 && y == 5 && z == 0) {
                return upperAt;
            }
            return null;
        }
    }

    private static MobEntity zombie(DoorWorld world, long seed) {
        return new MobEntity(990_000, MobType.ZOMBIE, new Position(0.5, 4.0, 0.5),
                new Random(seed), world);
    }

    // ------------------------------------------------------------- the spawn dice

    /**
     * The dice at a seeded random (initialize line 430 + lines 447-451):
     * the test keeps its OWN verbatim transcription of the reference's
     * walk — the canBreakDoors roll ({@code nextFloat() < f * 0.1F}),
     * the leader roll ({@code nextFloat() < f * 0.05F}) with the
     * reinforcement bonus ({@code nextDouble() * 0.25 + 0.5}), the
     * MAX_HEALTH multiply ({@code nextDouble() * 3.0 + 1.0} over the
     * plain 20), and the forced door-break — and the engine's roll must
     * land exactly the same state off the same seed.
     */
    @Test
    void theSpawnDiceMatchTheReferenceWalk() {
        long[] seeds = {20261021L, 42L, 777L, 123456789L, 55L, 909L};
        for (long seed : seeds) {
            for (float multiplier : new float[] {0.0F, 0.5F, 1.0F}) {
                DoorWorld world = new DoorWorld();
                MobEntity mob = zombie(world, seed + 1);
                MobManager.rollZombieCanBreakDoors(mob, new Random(seed), multiplier);
                // The reference's line 430, transcribed verbatim.
                boolean expectedCanBreak = new Random(seed).nextFloat() < multiplier * 0.1F;
                assertEquals(expectedCanBreak, mob.zombieCanBreakDoors(),
                        "the door arming at seed " + seed + " multiplier " + multiplier);

                mob = zombie(world, seed + 1);
                // The ctor's own base roll answers inside the leader shadow.
                double base = mob.zombieReinforcementChance();
                MobManager.rollZombieLeader(mob, new Random(seed), multiplier);
                // The reference's lines 447-451, transcribed verbatim.
                Random ref = new Random(seed);
                boolean leader = ref.nextFloat() < multiplier * 0.05F;
                if (leader) {
                    double bonus = ref.nextDouble() * 0.25 + 0.5;
                    double healthTerm = ref.nextDouble() * 3.0 + 1.0;
                    assertEquals(base + bonus, mob.zombieReinforcementChance(), 1e-9,
                            "the leader's reinforcement bonus at seed " + seed);
                    assertEquals(20.0 * (1.0 + healthTerm), mob.maxHealth(), 1e-6,
                            "the leader's 40..100 health band at seed " + seed);
                    assertEquals(mob.maxHealth(), mob.health(), 1e-6f,
                            "the leader spawns full");
                    assertTrue(mob.zombieCanBreakDoors(),
                            "the leader always breaks doors");
                } else {
                    assertEquals(base, mob.zombieReinforcementChance(), 1e-9,
                            "a non-leader keeps the plain base at seed " + seed);
                }
            }
        }
    }

    /** The leader's health band bounds (initialize line 449's 40..100). */
    @Test
    void theLeaderHealthCarriesTheReferenceBand() {
        DoorWorld world = new DoorWorld();
        MobEntity mob = zombie(world, 3L);
        mob.applyZombieLeaderHealth(1.0);
        assertEquals(40.0f, mob.maxHealth(), 1e-6f, "the minimum term answers 40");
        assertEquals(40.0f, mob.health(), 1e-6f);
        mob = zombie(world, 4L);
        mob.applyZombieLeaderHealth(4.0);
        assertEquals(100.0f, mob.maxHealth(), 1e-6f, "the maximum term answers 100");
        assertFalse(mob.zombieCanBreakDoors(), "the health arm alone never arms the door");
    }

    // ------------------------------------------------------------- the door-break goal

    /**
     * The goal's own gates (AbstractDoorInteractGoal.getDoorBlock +
     * BreakDoorGoal.canStart): the arm refuses every wrong shape — a
     * non-zombie, the unarmed flag, the griefing gate, an open door, the
     * iron family — and answers the closed wooden upper.
     */
    @Test
    void theDoorBreakArmsOnlyOnTheClosedWoodenFamily() {
        // The wrong kinds and gates.
        DoorWorld world = new DoorWorld();
        world.upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity pig = new MobEntity(990_001, MobType.PIG, new Position(0.5, 4.0, 0.5),
                new Random(1L), world);
        pig.setZombieCanBreakDoors(true); // the setter itself gates the kind
        assertFalse(pig.armZombieDoorBreak(), "a pig never breaks doors");
        assertFalse(pig.zombieCanBreakDoors(), "the flag never arms on a non-zombie");

        MobEntity unarmed = zombie(world, 2L);
        assertFalse(unarmed.armZombieDoorBreak(), "the unarmed flag refuses");

        world.griefing = false;
        MobEntity griefless = zombie(world, 3L);
        griefless.setZombieCanBreakDoors(true);
        assertFalse(griefless.armZombieDoorBreak(), "the mobGriefing gate refuses");
        world.griefing = true;

        world.upperAt = Identifier.parse("minecraft:oak_door_upper_open");
        MobEntity open = zombie(world, 5L);
        open.setZombieCanBreakDoors(true);
        assertFalse(open.armZombieDoorBreak(), "an OPEN door never arms the walk");

        world.upperAt = Identifier.parse("minecraft:iron_door_upper");
        MobEntity iron = zombie(world, 6L);
        iron.setZombieCanBreakDoors(true);
        assertFalse(iron.armZombieDoorBreak(), "the iron family never arms the walk");

        // The closed wooden upper arms (the fallback cell above the body).
        world.upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity armed = zombie(world, 7L);
        armed.setZombieCanBreakDoors(true);
        assertTrue(armed.armZombieDoorBreak(), "the closed wooden upper arms");
        assertTrue(armed.goalZombieBreakDoorCanUse(), "the sticky walk holds");
    }

    /**
     * The 240-tick hardness walk (BreakDoorGoal.tick lines 40-57): the
     * stage band ({@code progress / 240 * 10} published on change), the
     * 1-in-20 knock, and the HARD-alone completion (line 53) — the
     * pending break + the wire's -1 clear; the NORMAL world runs the
     * whole clock and never breaks, the goal stopping at 241 with the
     * -1 clear of its own.
     */
    @Test
    void theDoorBreakWalkPublishesTheStagesAndBreaksOnHardAlone() {
        DoorWorld world = new DoorWorld();
        world.upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity zombie = zombie(world, 8L);
        zombie.setZombieCanBreakDoors(true);
        assertTrue(zombie.armZombieDoorBreak());

        // The stage walk: 240 ticks, the stage advancing every 24 — the
        // band 0..9 plus the completion tick's own stage 10 (the
        // reference's (int)(240/240*10) — the last word before the break).
        int lastStage = -1;
        List<Integer> stages = new ArrayList<>();
        int knocks = 0;
        for (int tick = 1; tick <= 240; tick++) {
            assertTrue(zombie.goalZombieBreakDoorCanUse(), "the walk holds inside 240");
            zombie.goalZombieBreakDoorTick();
            int stage = zombie.consumeZombieDoorStage();
            if (stage != -2) {
                assertEquals(lastStage + 1, stage, "the stages walk one at a time");
                lastStage = stage;
                stages.add(stage);
            }
            if (zombie.consumeZombieDoorKnock()) {
                knocks++;
            }
            if (tick < 240) {
                assertFalse(zombie.consumeZombieDoorBroken(), "the break waits for 240");
            }
        }
        assertEquals(11, stages.size(), "the 0..10 band publishes exactly eleven times");
        assertEquals(10, lastStage, "the 240th tick publishes stage 10");

        // The completion on HARD: the break lands; the stage-10 stands as
        // the wire's last word (the reference sends no extra clear).
        assertTrue(zombie.consumeZombieDoorBroken(), "the 240th tick breaks the door on HARD");
        assertEquals(-2, zombie.consumeZombieDoorStage(), "no clear after the break");
        assertTrue(knocks >= 1, "the 1-in-20 knock fired through the walk");

        // The NORMAL world: the whole clock, never the break, the 241st
        // tick the last live one — the 242nd stops the walk with the -1.
        DoorWorld normal = new DoorWorld();
        normal.hard = false;
        normal.upperAt = Identifier.parse("minecraft:oak_door_upper");
        MobEntity banger = zombie(normal, 9L);
        banger.setZombieCanBreakDoors(true);
        assertTrue(banger.armZombieDoorBreak());
        for (int tick = 1; tick <= 241; tick++) {
            assertTrue(banger.goalZombieBreakDoorCanUse(),
                    "the walk holds through the 241st tick");
            banger.goalZombieBreakDoorTick();
            banger.consumeZombieDoorStage();
            banger.consumeZombieDoorKnock();
            assertFalse(banger.consumeZombieDoorBroken(), "NORMAL never breaks the door");
        }
        assertFalse(banger.goalZombieBreakDoorCanUse(), "the 242nd tick stops the walk");
        assertEquals(-1, banger.consumeZombieDoorStage(), "stop() publishes the clear");
    }

    // ------------------------------------------------------------- the reinforcement caller

    /** The rig: the flat dark world + the seeded manager on HARD. */
    private static final class Rig {
        final DoorWorld world = new DoorWorld();
        final MobManager manager;
        final List<MobEntity> spawned = new ArrayList<>();

        Rig(long seed) {
            manager = new MobManager(world, new Random(seed), loot, 1_100_000,
                    (x, z) -> 4);
            manager.setDifficultyRead(() -> world.hard ? 3 : 2);
            manager.addListener(new MobManager.Listener() {
                @Override public void onMobSpawned(MobEntity mob) { spawned.add(mob); }
                @Override public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) { }
                @Override public void onMobHurt(MobEntity mob) { }
                @Override public void onMobDied(MobEntity mob) { }
                @Override public void onMobRemoved(MobEntity mob, String reason) { }
                @Override public void onMobAttackedPlayer(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
                @Override public void onMobSound(MobEntity mob, String soundName) { }
                @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
                @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
                @Override public void onMobSheared(MobEntity mob, int woolCount) { }
                @Override public void onMobCoatRegrown(MobEntity mob) { }
                @Override public void onMobExploded(MobEntity mob) { }
            });
        }

        MobEntity spawn(MobType type, double x, double z) {
            return manager.spawnAt(type, new Position(x, 4.0, z));
        }

        int zombieCount() {
            int count = 0;
            for (MobEntity mob : manager.all()) {
                if (mob.type() == MobType.ZOMBIE && !mob.dead()) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final MobManager.LootSink loot = new MobManager.LootSink() {
        @Override
        public void spawnLootDrop(Position position, net.zaminmc.torch.item.ItemStack stack, int dimension) {
        }
    };

    /**
     * The reinforcement roll (takeDamage lines 199-227): a landed wound on
     * HARD under the full chance spawns ONE callee at the flank (the
     * 7..40 band with the sign walk — the reference's own shape), both
     * sides pay the 0.05 charge, and the callee takes the caller's own
     * target (the reference's setAttackTarget read of the attack
     * target first, then the wound's attacker).
     */
    @Test
    void theReinforcementRollSpawnsAtTheFlankAndChargesBothSides() {
        Rig rig = new Rig(42L);
        MobEntity caller = rig.spawn(MobType.ZOMBIE, 0.5, 0.5);
        assertNotNull(caller);
        double base = caller.zombieReinforcementChance();
        assertTrue(base <= 0.1, "the base attribute band is nextDouble() * 0.1");
        // 0.9 over the <= 0.1 base reaches at most 1.0 — the roll always
        // passes, and the charge stays BELOW the accessor's 1.0 clamp so
        // the assertion reads the true value.
        caller.addZombieReinforcementModifier(0.9);

        // The attacker: a skeleton (the census counts the ZOMBIE family
        // alone — the reference's own family read).
        MobEntity attacker = rig.spawn(MobType.SKELETON, 2.5, 0.5);
        assertNotNull(attacker);

        boolean landed = rig.manager.hurt(caller, 1.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE,
                false, false, attacker, null);
        assertTrue(landed);
        assertEquals(2, rig.zombieCount(), "the callee joined the pack");
        assertEquals(base + 0.9 - 0.05, caller.zombieReinforcementChance(), 1e-9,
                "the caller paid the charge");

        // The callee: the flank band + the target + its own charge.
        MobEntity callee = null;
        for (MobEntity mob : rig.manager.all()) {
            if (mob != caller && mob.type() == MobType.ZOMBIE) {
                callee = mob;
            }
        }
        assertNotNull(callee, "the callee spawned");
        double dx = callee.position().x() - caller.position().x();
        double dz = callee.position().z() - caller.position().z();
        double distance = Math.sqrt(dx * dx + dz * dz);
        assertTrue(distance <= Math.sqrt(2) * 40.0 + 1.0,
                "the callee lands inside the flank band");
        assertTrue(callee.zombieReinforcementChance() <= 0.1 - 0.05 + 1e-9,
                "the callee paid its own charge (the base band 0..0.1 minus 0.05)");
        assertEquals(attacker, callee.mobAttackTarget(),
                "the callee chases the caller's fight");
        // A plain adult zombie: the callee never rolls the baby/villager
        // forms (the standing shape the PR names).
        assertFalse(callee.isBaby());
        assertFalse(callee.zombieVillager());
    }

    /** The gates: NORMAL never rolls; a baby never calls; a capped pack refuses. */
    @Test
    void theReinforcementRollRefusesTheGates() {
        // The NORMAL world.
        Rig rig = new Rig(43L);
        rig.world.hard = false;
        MobEntity caller = rig.spawn(MobType.ZOMBIE, 0.5, 0.5);
        caller.addZombieReinforcementModifier(1.0);
        MobEntity attacker = rig.spawn(MobType.SKELETON, 2.5, 0.5);
        rig.manager.hurt(caller, 1.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, attacker, null);
        assertEquals(1, rig.zombieCount(), "NORMAL never calls reinforcements");

        // The baby caller (the task's named refusal).
        rig = new Rig(44L);
        MobEntity baby = rig.spawn(MobType.ZOMBIE, 0.5, 0.5);
        baby.setBreedingAge(-24000);
        baby.addZombieReinforcementModifier(1.0);
        attacker = rig.spawn(MobType.SKELETON, 2.5, 0.5);
        rig.manager.hurt(baby, 1.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, attacker, null);
        assertEquals(1, rig.zombieCount(), "a baby zombie never calls");

        // The capped pack (the task's named <=10 census).
        rig = new Rig(45L);
        MobEntity pressed = rig.spawn(MobType.ZOMBIE, 0.5, 0.5);
        pressed.addZombieReinforcementModifier(1.0);
        for (int i = 0; i < 11; i++) {
            assertNotNull(rig.spawn(MobType.ZOMBIE, 10.0 + i, 10.0),
                    "the pack spawns within the census band");
        }
        assertEquals(12, rig.zombieCount());
        attacker = rig.spawn(MobType.SKELETON, 2.5, 0.5);
        rig.manager.hurt(pressed, 1.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false, false, attacker, null);
        assertEquals(12, rig.zombieCount(), "the >10 pack cap refuses the call");
    }

    /**
     * VIII4's iron-tool arm is ALREADY landed and pinned by
     * MobArmorFamilyTest's seeded shadow — the audit this slice owes (no
     * duplication here, the reference's addRandomEquipment lines 305-316
     * coverage named in the PR).
     */
    @Test
    void theIronToolArmAuditStaysWithMobArmorFamilyTest() {
        // The arm's seeded shadow lives at MobArmorFamilyTest.
        // theSpawnDiceMatchTheReferenceWalkAtASeededRandom — the walk
        // (the 1%-or-5%-by-HARD gate, the sword one third) is pinned
        // there over MobManager.rollSpawnEquipment's tail; this test
        // asserts the arm EXISTS on the zombie path only (the held dice
        // reach the zombie family through the same walk).
        DoorWorld world = new DoorWorld();
        MobEntity mob = zombie(world, 99L);
        MobManager.rollSpawnEquipment(mob, new Random(99L), 0.0f, 3);
        assertNull(mob.heldEquipment(), "the zero multiplier never equips");
        assertEquals(MobType.ZOMBIE, mob.type());
        assertTrue(BuiltinItems.IRON_SWORD != null, "the tool family registered");
    }
}
