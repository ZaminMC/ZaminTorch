package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
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
 * The zombie villager (Slice 25a — ZombieEntity's own conversion family):
 * the infection (onKill's difficulty gate + the replacement walk), the
 * cure (interactMob's golden-apple arm + setConversionTime's effect pair),
 * the countdown (the iron-bars accelerator), the completion
 * (convertToVillager), and the despawn exemption. The persistence pair
 * rides Slice 25c's own suite.
 */
class ZombieVillagerConversionTest {

    /** A flat-world stub: solid below y=8, no players, the bars cage optional. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        final boolean barsEverywhere;

        ScriptedWorld(boolean barsEverywhere) {
            this.barsEverywhere = barsEverywhere;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean isIronBarsOrBed(int x, int y, int z) {
            return barsEverywhere;
        }
    }

    /** Records the wire-relevant events (the conversion family's own). */
    private static final class Events implements MobManager.Listener {
        int spawned;
        int statusEvents;
        final List<String> removals = new ArrayList<>();
        final List<int[]> worldEvents = new ArrayList<>(); // (eventId)
        final List<MobEntity> stateChanges = new ArrayList<>();

        @Override
        public void onMobSpawned(MobEntity mob) {
            spawned++;
        }

        @Override
        public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) {
        }

        @Override
        public void onMobHurt(MobEntity mob) {
        }

        @Override
        public void onMobDied(MobEntity mob) {
        }

        @Override
        public void onMobRemoved(MobEntity mob, String reason) {
            removals.add(reason);
        }

        @Override
        public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) {
        }

        @Override
        public void onMobSound(MobEntity mob, String soundName) {
        }

        @Override
        public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
        }

        @Override
        public void onMobFuseChanged(MobEntity mob, boolean priming) {
        }

        @Override
        public void onMobExploded(MobEntity mob) {
        }

        @Override
        public void onMobSheared(MobEntity mob, int woolCount) {
        }

        @Override
        public void onMobCoatRegrown(MobEntity mob) {
        }

        @Override
        public void onMobStatusEvent(MobEntity mob, int status) {
            statusEvents = status;
        }

        @Override
        public void onWorldEventAt(Position position, int eventId, int dimension) {
            worldEvents.add(new int[] {eventId, dimension});
        }

        @Override
        public void onZombieStateChanged(MobEntity mob) {
            stateChanges.add(mob);
        }
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 8;

    private static MobManager manager(long seed, MobEntity.WorldQuery world) {
        MobManager manager = new MobManager(world, new Random(seed),
                (position, stack) -> { }, 900, SURFACE);
        return manager;
    }

    private static PotionSystem potions(MobManager manager) {
        // The minimal body surface: only the store-keying pair matters here.
        PotionSystem potions = new PotionSystem(new PotionSystem.Bodies() {
            @Override
            public List<Object> bodiesIn(double minX, double minY, double minZ,
                                          double maxX, double maxY, double maxZ) {
                return List.of();
            }

            @Override
            public double squaredDistanceTo(Object body, double x, double y, double z) {
                return 0;
            }

            @Override
            public void hurt(Object body, float amount, String cause, Object attacker,
                             boolean magic) {
            }

            @Override
            public void heal(Object body, float amount) {
            }

            @Override
            public void exhaust(Object body, float amount) {
            }

            @Override
            public void feed(Object body, int food, float saturation) {
            }

            @Override
            public float healthOf(Object body) {
                return 20.0f;
            }

            @Override
            public boolean fireResistant(Object body) {
                return false;
            }

            @Override
            public boolean undead(Object body) {
                return body instanceof MobEntity mob && mob.type().undead();
            }

            @Override
            public boolean isPlayer(Object body) {
                return false;
            }

            @Override
            public int keyOf(Object body) {
                return body instanceof MobEntity mob ? mob.entityId() : 0;
            }
        });
        manager.setPotionSystem(potions);
        return potions;
    }

    @Test
    void theInfectionReplacesTheVillagerOnHard() {
        MobManager manager = manager(7L, new ScriptedWorld(false));
        Events events = new Events();
        manager.addListener(events);
        manager.setDifficultyRead(() -> 3); // HARD: every kill infects

        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        MobEntity villager = manager.spawnAt(MobType.VILLAGER, new Position(1.5, 8.0, 0.5));
        assertNotNull(zombie);
        assertNotNull(villager);
        float yaw = villager.bodyYawLive();

        assertTrue(manager.hurtByMob(zombie, villager, 40.0f));
        assertTrue(villager.dead(), "the killing blow landed");

        // The infection lands on the tick's close: the victim leaves, the
        // villager-skinned zombie takes its place (onKill lines 358-377).
        manager.tick(List.of(), 6000L);
        assertTrue(events.removals.contains("infected"));
        assertEquals(1, events.worldEvents.size());
        assertEquals(1016, events.worldEvents.get(0)[0]);

        MobEntity replacement = null;
        for (MobEntity mob : manager.all()) {
            if (mob != zombie) {
                replacement = mob;
            }
        }
        assertNotNull(replacement, "the infection spawned the replacement");
        assertEquals(MobType.ZOMBIE, replacement.type());
        assertTrue(replacement.zombieVillager(), "the replacement wears the villager skin");
        assertFalse(replacement.isBaby(), "an adult villager converts into an adult zombie");
        assertEquals(villager.position().x(), replacement.position().x(), 1e-9);
        assertEquals(villager.position().z(), replacement.position().z(), 1e-9);
        assertEquals(yaw, replacement.bodyYawLive(), 1e-6f);
        assertFalse(zombie.zombieVillager(), "the attacker keeps its own skin");
    }

    @Test
    void theBabyVillagerInfectsIntoABabyZombie() {
        MobManager manager = manager(9L, new ScriptedWorld(false));
        manager.setDifficultyRead(() -> 3);

        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        MobEntity villager = manager.spawnAt(MobType.VILLAGER, new Position(1.5, 8.0, 0.5));
        villager.setBreedingAge(-24_000);

        assertTrue(manager.hurtByMob(zombie, villager, 40.0f));
        manager.tick(List.of(), 6000L);

        for (MobEntity mob : manager.all()) {
            if (mob != zombie && mob.type() == MobType.ZOMBIE) {
                assertTrue(mob.zombieVillager());
                assertTrue(mob.isBaby(), "setBaby(true) — a baby for a baby (line 365)");
            }
        }
    }

    @Test
    void easyNeverInfects() {
        MobManager manager = manager(11L, new ScriptedWorld(false));
        Events events = new Events();
        manager.addListener(events);
        manager.setDifficultyRead(() -> 1); // EASY

        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        MobEntity villager = manager.spawnAt(MobType.VILLAGER, new Position(1.5, 8.0, 0.5));

        assertTrue(manager.hurtByMob(zombie, villager, 40.0f));
        manager.tick(List.of(), 6000L);

        assertFalse(events.removals.contains("infected"));
        assertTrue(events.worldEvents.isEmpty());
        // The villager still died the ordinary way (the death animation runs).
        assertTrue(villager.dead());
    }

    @Test
    void theCureArmsTheConversion() {
        MobManager manager = manager(13L, new ScriptedWorld(false));
        Events events = new Events();
        manager.addListener(events);
        PotionSystem potions = potions(manager);
        manager.setDifficultyRead(() -> 2); // NORMAL

        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        zombie.setZombieVillager(true);
        // The weakness window (the splash potion's famous recipe).
        potions.add(zombie, new StatusEffectInstance(StatusEffects.WEAKNESS, 9600, 0));
        assertTrue(potions.hasEffect(zombie, StatusEffects.WEAKNESS));

        assertTrue(manager.cureZombieVillager(zombie));
        assertTrue(zombie.zombieConverting(), "the countdown runs (the slot-14 byte)");
        assertTrue(zombie.zombieConversionTicks() >= 3600 && zombie.zombieConversionTicks() < 7200,
                "nextInt(2401) + 3600 — the 3-to-5-minute window");
        assertFalse(potions.hasEffect(zombie, StatusEffects.WEAKNESS),
                "setConversionTime clears the Weakness (line 485)");
        assertNotNull(potions.get(zombie, StatusEffects.STRENGTH));
        assertEquals(0, potions.get(zombie, StatusEffects.STRENGTH).amplifier(),
                "Math.min(difficultyId - 1, 0) — the reference's own 0");
        assertEquals(zombie.zombieConversionTicks(),
                potions.get(zombie, StatusEffects.STRENGTH).duration());
        assertEquals(16, events.statusEvents, "doEntityEvent(16) — the remedy sound");
        assertEquals(1, events.stateChanges.size(), "the wire pair republishes");
    }

    @Test
    void theCureRefusesThePlainZombieAndTheDeadBody() {
        MobManager manager = manager(15L, new ScriptedWorld(false));
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        assertFalse(manager.cureZombieVillager(zombie),
                "the plain zombie never carries the arm");
        zombie.setZombieVillager(true);
        zombie.hurt(100.0f);
        assertFalse(manager.cureZombieVillager(zombie),
                "a dead body cannot take the apple");
    }

    @Test
    void theConvertingZombieNeverDespawns() {
        MobEntity zombie = new MobEntity(960, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(3L),
                new ScriptedWorld(false));
        assertTrue(zombie.canDespawn(), "the ordinary zombie despawns");
        zombie.setZombieConversionTime(4000);
        assertFalse(zombie.canDespawn(),
                "canDespawn's own override — the cure outlives every window");
    }

    @Test
    void theCountdownCompletesIntoAVillager() {
        MobManager manager = manager(17L, new ScriptedWorld(false));
        Events events = new Events();
        manager.addListener(events);
        PotionSystem potions = potions(manager);

        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        zombie.setZombieVillager(true);
        zombie.setZombieConversionTime(5); // the test's own short window
        float yaw = zombie.bodyYawLive();

        manager.tick(List.of(), 6000L);
        manager.tick(List.of(), 6001L);
        manager.tick(List.of(), 6002L);
        manager.tick(List.of(), 6003L);
        manager.tick(List.of(), 6004L);
        manager.tick(List.of(), 6005L);
        manager.tick(List.of(), 6006L);

        assertTrue(events.removals.contains("converted"),
                "convertToVillager — the zombie leaves");
        boolean villagerSpawned = false;
        for (MobEntity mob : manager.all()) {
            if (mob.type() == MobType.VILLAGER) {
                villagerSpawned = true;
                assertEquals(yaw, mob.bodyYawLive(), 1e-6f,
                        "copyPositionAndRotationFrom");
                assertNotNull(potions.get(mob, StatusEffects.NAUSEA));
                assertEquals(200, potions.get(mob, StatusEffects.NAUSEA).duration(),
                        "the 200-tick nausea (line 536)");
                assertTrue(mob.profession() >= 0 && mob.profession() < 5,
                        "initialize rolls the profession (nextInt(5))");
            }
        }
        assertTrue(villagerSpawned, "the villager took the zombie's place");
        assertTrue(events.worldEvents.stream().anyMatch(e -> e[0] == 1017),
                "the un-infect event sounds (line 537)");
    }

    @Test
    void theIronBarsAccelerateTheCountdown() {
        // The decrement's own unit arm (getTicksUntilConversionDecrement),
        // probed black-box: arm a huge window on a zombie standing in a
        // world of iron bars, tick once, and measure the drop — the flat 1
        // leaves window-1, a fired scan leaves window-(1+extras). The seed
        // hunt walks candidates until the cage's 30%-per-cell rolls add at
        // least two extra decrements (the 14-cell cage clears 2 with
        // >99.9% odds, so a few candidates always answer) — the test stays
        // deterministic.
        long seed = -1;
        int drop = 0;
        for (long candidate = 0; candidate < 100_000; candidate++) {
            MobEntity probe = new MobEntity(962, MobType.ZOMBIE,
                    new Position(0.5, 8.0, 0.5), new Random(candidate),
                    new ScriptedWorld(true));
            probe.setZombieConversionTime(1_000_000);
            probe.tick(false);
            int probeDrop = 1_000_000 - probe.zombieConversionTicks();
            if (probeDrop >= 3) {
                seed = candidate;
                drop = probeDrop;
                break;
            }
        }
        assertTrue(seed >= 0, "the seed hunt found a deterministic accelerator window");

        MobEntity zombie = new MobEntity(961, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(seed),
                new ScriptedWorld(true));
        assertTrue(drop >= 3, "the cage's scan consumed beyond the flat 1: " + drop);
        zombie.setZombieConversionTime(drop + 3);
        zombie.tick(false);
        assertEquals(3, zombie.zombieConversionTicks(),
                "the scan's decrements consumed the window beyond the flat 1");

        // The empty world's control: the same seed in open air never
        // accelerates (no bars — the scan finds nothing, the flat 1 rules).
        MobEntity control = new MobEntity(963, MobType.ZOMBIE,
                new Position(0.5, 8.0, 0.5), new Random(101L),
                new ScriptedWorld(false));
        control.setZombieConversionTime(100);
        control.tick(false);
        assertEquals(99, control.zombieConversionTicks(),
                "the flat decrement is exactly 1 (the 99.99% case)");
    }

    @Test
    void theNaturalSpawnRollsTheVillagerSkin() {
        // The 5% Data roll (initialize line 401): with 500 seeded spawns a
        // deterministic seed bank answers both skins (the band check keeps
        // the flake out; the roll's own rate is the reference's).
        MobManager manager = manager(19L, new ScriptedWorld(false));
        int villagerSkinned = 0;
        for (int i = 0; i < 500; i++) {
            MobEntity zombie = manager.spawnNatural(MobType.ZOMBIE,
                    new Position(i, 8.0, 0.5), false, 0.0f);
            if (zombie.zombieVillager()) {
                villagerSkinned++;
            }
        }
        assertTrue(villagerSkinned > 5 && villagerSkinned < 70,
                "the 5% roll answers inside its band: " + villagerSkinned);
    }

    @Test
    void theEngineNeverInfectsThroughOtherKillers() {
        MobManager manager = manager(21L, new ScriptedWorld(false));
        manager.setDifficultyRead(() -> 3);
        Events events = new Events();
        manager.addListener(events);

        // A zombie pigman (its own kind in the engine) killing a villager
        // never infects — the reference's onKill is the zombie's own.
        MobEntity pigman = manager.spawnAt(MobType.ZOMBIE_PIGMAN, new Position(0.5, 8.0, 0.5));
        MobEntity villager = manager.spawnAt(MobType.VILLAGER, new Position(1.5, 8.0, 0.5));
        assertTrue(manager.hurtByMob(pigman, villager, 40.0f));
        manager.tick(List.of(), 6000L);
        assertFalse(events.removals.contains("infected"));
        assertTrue(events.worldEvents.isEmpty());
    }
}
