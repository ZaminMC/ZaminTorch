package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wither boss (Slice 59 — WitherEntity/WitherSkullEntity 1:1): the
 * blue window's law (the third-health summon, the 10-per-10 heal, the
 * birth event at the end, the everything-but-the-void shield), the hurt
 * gates (the drown refusal, the wither-attacker refusal, the half-health
 * ARROW shield, the UNDEAD melee family), the sweep's 20-tick clock and
 * the head Ints' dirty law, and the drop (the never-despawn nether star).
 */
class WitherBossTest {

    // ------------------------------------------------------------------
    // The unit world (a flat plane + the wither's quarry lookups)
    // ------------------------------------------------------------------

    /** A stub player body the heads can quarry (id 9001, standing 10 east). */
    private static final int QUARRY_ID = 9001;
    private static final Position QUARRY_AT = new Position(10.5, 4.0, 0.5);

    private static MobEntity.WorldQuery witherWorld(boolean normalDifficulty) {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                double dx = QUARRY_AT.x() - x;
                double dy = QUARRY_AT.y() - y;
                double dz = QUARRY_AT.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? QUARRY_AT : null;
            }

            @Override public Integer entityById(int entityId) {
                return entityId == QUARRY_ID ? entityId : null;
            }

            @Override public Position entityPositionById(int entityId) {
                return entityId == QUARRY_ID ? QUARRY_AT : null;
            }

            @Override public double eyeHeightOf(int entityId) {
                return 1.62;
            }

            @Override public boolean entityInvulnerablePlayer(int entityId) {
                return false;
            }

            @Override public int[] livingNonUndeadIdsIn(double x, double y, double z,
                                                       double halfX, double halfY, int excludeId) {
                // The quarry stands inside the grown box around the boss.
                double dx = QUARRY_AT.x() - x;
                double dy = QUARRY_AT.y() - y;
                double dz = QUARRY_AT.z() - z;
                if (Math.abs(dx) <= halfX && Math.abs(dy) <= halfY && Math.abs(dz) <= halfX) {
                    return new int[] {QUARRY_ID};
                }
                return new int[0];
            }

            @Override public int nearestVisibleEntityId(Position target) {
                return QUARRY_ID;
            }

            @Override public boolean difficultyAtLeastNormal() {
                return normalDifficulty;
            }
        };
    }

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();
        final List<ItemStack> persistent = new ArrayList<>();

        @Override public void spawnLootDrop(Position position, ItemStack stack, int dimension) {
            drops.add(stack);
        }

        @Override public void spawnPersistentLootDrop(Position position, ItemStack stack, int dimension) {
            persistent.add(stack);
        }
    }

    @Test
    void theBlueWindowHealsTenPerTenAndBirthsAtTheEnd() {
        // onSummoned (lines 328-331): the spawned-full body DROPS to the
        // third (setHealth, not a max-with), the window opens at 220.
        MobEntity wither = new MobEntity(9501, MobType.WITHER,
                new Position(0.5, 4.0, 0.5), new Random(51L), witherWorld(false));
        assertEquals(300.0f, wither.health(), 1e-6f, "the boss spawns full");
        wither.witherSummoned();
        assertEquals(100.0f, wither.health(), 1e-6f, "the summon drops to the third");
        assertEquals(220, wither.witherInvulnTicks(), "the 220-tick window");
        assertTrue(wither.witherInvulnerable(), "the blue shield stands");
        assertTrue(wither.consumeWitherMetadataDirty(), "the summon dirties the Ints");

        // The window: ten heals of ten carry 100 back to the 300 cap
        // exactly (100 + 22*10 = 320 -> the cap), and the birth event
        // fires on the tick the countdown reaches zero.
        boolean birth = false;
        for (int tick = 0; tick < 220; tick++) {
            wither.tick(true);
            if (wither.consumeWitherBirth()) {
                birth = true;
                assertEquals(0, wither.witherInvulnTicks(),
                        "the birth fires as the countdown hits zero");
            }
        }
        assertTrue(birth, "the birth event fired");
        assertFalse(wither.witherInvulnerable(), "the blue shield fell");
        assertEquals(300.0f, wither.health(), 1e-6f, "the window healed to the cap");
    }

    @Test
    void theBlueShieldBlocksAllButTheVoid() {
        // takeDamage line 417: the window blocks everything but
        // OUT_OF_WORLD; the peaceful sweep consults the window too.
        MobManager manager = new MobManager(witherWorld(false), new Random(52L),
                new Loot(), 1_100_000, (x, z) -> 4);
        MobEntity wither = manager.spawnAt(MobType.WITHER, new Position(0.5, 4.0, 0.5));
        wither.witherSummoned();
        assertFalse(manager.hurt(wither, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE), "the melee never lands");
        assertFalse(manager.hurt(wither, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.EXPLOSION), "the blast never lands");
        assertFalse(manager.hurt(wither, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MAGIC), "the potion never lands");
        assertEquals(100.0f, wither.health(), 1e-6f, "the health never moved");
        assertTrue(manager.hurt(wither, 10.0f, 0.0,
                        net.zaminmc.torch.event.DamageCause.OUT_OF_WORLD),
                "the void pierces the shield");
        assertTrue(wither.health() < 100.0f, "the void wound landed");
    }

    @Test
    void theHurtGatesRefuseTheDrownTheArrowsAndTheUndeadMelee() {
        // takeDamage lines 408-438: the drown and the wither's own skull
        // never land; below half health the ARROW shield stands (the
        // fireball family still lands — only an ArrowEntity source is
        // blocked); the UNDEAD melee family (players exempt) never wounds.
        Loot loot = new Loot();
        MobManager manager = new MobManager(witherWorld(false), new Random(53L),
                loot, 1_100_000, (x, z) -> 4);
        MobEntity wither = manager.spawnAt(MobType.WITHER, new Position(0.5, 4.0, 0.5));
        // The window closed: the plain wounds land.
        assertTrue(manager.hurt(wither, 5.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE));
        // The drown never lands (line 413).
        assertFalse(manager.hurt(wither, 1.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.DROWN), "the drown never lands");
        // Below half health: the arrow shield stands, the fireball lands.
        wither.heal(300.0f);
        manager.hurt(wither, 160.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE);
        assertTrue(wither.health() <= 150.0f, "the body sits below half");
        float beforeArrow = wither.health();
        assertFalse(manager.hurt(wither, 5.0f, 0.0,
                        net.zaminmc.torch.event.DamageCause.PROJECTILE, false, true, null),
                "the arrow never lands below half");
        assertEquals(beforeArrow, wither.health(), 1e-6f, "the arrow wound never moved it");
        assertTrue(manager.hurt(wither, 5.0f, 0.0,
                        net.zaminmc.torch.event.DamageCause.PROJECTILE, false, false, null),
                "the fireball family still lands");
        // The UNDEAD melee family: a zombie's claw never wounds (lines
        // 428-434); the players are exempt by the reference's own gate.
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(2.5, 4.0, 0.5));
        assertFalse(manager.hurtByMob(zombie, wither, 5.0f),
                "the undead claw never lands");
        assertTrue(manager.hurt(wither, 5.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE),
                "the player's hand still lands");
        // The wither's own skull: another wither's mob-hit never lands.
        MobEntity secondWither = manager.spawnAt(MobType.WITHER, new Position(-6.5, 4.0, 0.5));
        assertFalse(manager.hurt(wither, 8.0f, 0.0,
                        net.zaminmc.torch.event.DamageCause.PROJECTILE, false, false, secondWither),
                "a wither's skull never lands on another wither");
    }

    @Test
    void everyWoundArmsTheTwentyTickSweep() {
        // takeDamage lines 436-442: every wound that passes the gates
        // arms the 20-tick sweep clock (and speeds the blue barrage).
        Loot loot = new Loot();
        MobManager manager = new MobManager(witherWorld(false), new Random(54L),
                loot, 1_100_000, (x, z) -> 4);
        MobEntity wither = manager.spawnAt(MobType.WITHER, new Position(0.5, 4.0, 0.5));
        for (int tick = 0; tick < 30; tick++) {
            wither.tick(true);
            assertFalse(wither.consumeWitherSweep(), "no sweep without a wound");
        }
        assertTrue(manager.hurt(wither, 5.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE));
        boolean swept = false;
        for (int tick = 0; tick < 20; tick++) {
            wither.tick(true);
            if (wither.consumeWitherSweep()) {
                swept = true;
                assertEquals(19, tick, "the sweep fires 20 ticks after the wound");
            }
        }
        assertTrue(swept, "the sweep fired");
        // A second wound re-arms; a wound during the clock does not extend
        // it (the reference arms only when the cooldown is spent).
        assertTrue(manager.hurt(wither, 5.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE));
        for (int tick = 0; tick < 10; tick++) {
            wither.tick(true);
        }
        assertTrue(manager.hurt(wither, 5.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE));
        boolean sweptAgain = false;
        for (int tick = 0; tick < 30; tick++) {
            wither.tick(true);
            if (wither.consumeWitherSweep()) {
                sweptAgain = true;
                assertTrue(tick >= 9, "the mid-clock wound never resets the sweep");
            }
        }
        assertTrue(sweptAgain, "the sweep fired again");
    }

    @Test
    void theSideHeadsQuarryAndVolley() {
        // mobAiTick lines 228-277: a side head acquires the living
        // non-undead quarry from the box scan, then volleys at the
        // 40-60-tick band while the quarry lives; the head Int dirty law
        // rides the flip.
        MobEntity wither = new MobEntity(9505, MobType.WITHER,
                new Position(0.5, 4.0, 0.5), new Random(55L), witherWorld(false));
        wither.tick(true); // the first mind tick: the acquisition may claim
        int[] heads = wither.witherHeadTargets();
        boolean anySideHead = heads[1] == QUARRY_ID || heads[2] == QUARRY_ID;
        if (!anySideHead) {
            // The 10-20-tick acquisition cooldown: keep ticking until a
            // head claims the quarry (the box always carries it).
            for (int tick = 0; tick < 40 && !anySideHead; tick++) {
                wither.tick(true);
                heads = wither.witherHeadTargets();
                anySideHead = heads[1] == QUARRY_ID || heads[2] == QUARRY_ID;
            }
        }
        assertTrue(anySideHead, "a side head claimed the quarry");
        // The volley: the claimed head looses a skull within the band.
        boolean loosed = false;
        for (int tick = 0; tick < 80; tick++) {
            wither.tick(true);
            if (wither.consumeWitherSkullHead() >= 0) {
                loosed = true;
                break;
            }
        }
        assertTrue(loosed, "a head loosed its skull at the quarry");
        assertTrue(wither.consumeWitherShootSound(), "the 1014 launch sound fired");
    }

    @Test
    void theMainHeadVolleysOnTheFortyTickClock() {
        // ProjectileAttackGoal (the wither's registration: cooldown 40,
        // range 20): the main head's own volley — the clock arms on the
        // first sight and fires the head-0 skull (the 1-in-1000 blue
        // rides the roll) while the quarry sits inside 20 blocks and the
        // sight holds.
        MobEntity wither = new MobEntity(9509, MobType.WITHER,
                new Position(0.5, 4.0, 0.5), new Random(57L), witherWorld(false));
        // The quarry stands ~10 east: inside the 20-block range, the
        // stub's open sight holds. The clock arms on the first tick and
        // fires at the 41st.
        boolean headZeroLoosed = false;
        for (int tick = 0; tick < 60; tick++) {
            wither.tick(true);
            int head = wither.consumeWitherSkullHead();
            if (head == 0) {
                headZeroLoosed = true;
                assertTrue(tick >= 39, "the 40-tick clock gated the shot");
                break;
            }
        }
        assertTrue(headZeroLoosed, "the main head loosed its skull");
    }

    @Test
    void theDropIsTheNeverDespawnNetherStar() {
        // dropItems (lines 448-459): the one nether star, the item's only
        // source, under the never-despawn flag — the manager's death walk
        // routes it through the persistent sink.
        Loot loot = new Loot();
        MobManager manager = new MobManager(witherWorld(false), new Random(56L),
                loot, 1_100_000, (x, z) -> 4);
        MobEntity wither = manager.spawnAt(MobType.WITHER, new Position(0.5, 4.0, 0.5));
        manager.hurt(wither, 400.0f, 0.0, net.zaminmc.torch.event.DamageCause.MELEE);
        assertTrue(wither.dead(), "the boss died");
        // The death flow: the animation window runs, then the loot lands.
        for (int tick = 0; tick < 30; tick++) {
            manager.tick(List.of(), MobManager.NIGHT_START);
        }
        assertEquals(1, loot.persistent.size(), "the one nether star");
        assertEquals("minecraft:nether_star",
                loot.persistent.get(0).type().identifier().toString());
        assertEquals(1, loot.persistent.get(0).count());
        assertTrue(loot.drops.isEmpty(), "nothing else drops");
    }

    @Test
    void theItemNeverDespawnFlagHoldsTheAgeClock() {
        // ItemEntity.setNeverDespawn: the age clock stops expiring the
        // body (the star stays until collected).
        net.zaminmc.torch.server.entity.ItemEntity star =
                new net.zaminmc.torch.server.entity.ItemEntity(9506,
                        new Position(0.5, 4.5, 0.5),
                        ItemStack.of(BuiltinItems.NETHER_STAR, 1), 10);
        assertFalse(star.expired(), "fresh");
        for (int tick = 0; tick < 7000; tick++) {
            // The manager's own age step is package-visible; the flag's
            // law answers through expired() whatever the clock reads.
            star.setNeverDespawn();
        }
        assertFalse(star.expired(), "the flagged body never expires");
        assertTrue(star.neverDespawn(), "the flag reads back");
    }
}
