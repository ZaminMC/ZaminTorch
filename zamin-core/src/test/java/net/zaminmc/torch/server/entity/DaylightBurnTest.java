package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
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
 * The undead family's daylight burn, 1:1 (ZombieEntity.mobTick lines
 * 160-181 with the baby gate, SkeletonEntity.mobTick lines 115-136 without
 * it): the sunny/brightness/roll/sky gates, the helmet's block-and-wear,
 * and the family's own split — the pigman and the wither variant ride
 * their parents' walks and burn VISUALLY under fire immunity (the ML-100
 * pigman tail: overworld pigmen exist since the portal spawn lane, and
 * the reference's law lights them exactly like their zombie parent).
 */
class DaylightBurnTest {

    /** The stub sun: bright, open, sunny — every gate passable. */
    private static final class SunWorld implements MobEntity.WorldQuery {
        boolean sunny = true;
        boolean allCovered;
        float brightness = 1.0f;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean isSunny() {
            return sunny;
        }

        @Override
        public float brightness(int x, int y, int z) {
            return brightness;
        }

        @Override
        public boolean hasSkyAccess(int x, int y, int z) {
            return !allCovered;
        }
    }

    private static MobEntity mobIn(SunWorld world, MobType type, long seed) {
        return new MobEntity(700, type, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    /** @return whether the body burned at any tick of the window. */
    private static boolean burnedWithin(MobEntity mob, int ticks) {
        boolean ever = false;
        for (int i = 0; i < ticks; i++) {
            mob.tick(true);
            ever |= mob.burning();
        }
        return ever;
    }

    @Test
    void thePigmanBurnsVisuallyButNeverWounds() {
        // THE ML-100 TAIL (ZombiePigmanEntity rides ZombieEntity's walk,
        // no override): the overworld pigman catches the sun exactly like
        // its zombie parent — the flames visual, the fire immunity holding
        // the wounds off forever (Entity.update lines 322-327's own 4x
        // drain, never a damage tick).
        SunWorld world = new SunWorld();
        MobEntity pigman = mobIn(world, MobType.ZOMBIE_PIGMAN, 21L);
        assertTrue(burnedWithin(pigman, 600),
                "the sunny roll lit the pigman (the zombie parent's own walk)");
        assertEquals(20.0f, pigman.health(), 1e-6f,
                "the immune body never takes a fire wound");
        assertNotNull(pigman, "the pigman stands in its element");
        assertTrue(pigman.health() > 0.0f, "the visual burn never kills");
    }

    @Test
    void theWitherVariantBurnsVisuallyWhileTheArcherWounds() {
        // The family's split (SkeletonEntity.mobTick serves both variants
        // — setType line 279's immuneToFire = type == 1): the wither body
        // burns without a wound, the plain archer pays the fire clock.
        SunWorld world = new SunWorld();
        MobEntity witherSkeleton = mobIn(world, MobType.SKELETON, 22L);
        witherSkeleton.setSkeletonWither(true);
        assertTrue(burnedWithin(witherSkeleton, 600),
                "the wither variant rides the skeleton's burn walk");
        assertEquals(20.0f, witherSkeleton.health(), 1e-6f,
                "the wither variant's fire immunity holds every wound");

        SunWorld archerWorld = new SunWorld();
        MobEntity archer = mobIn(archerWorld, MobType.SKELETON, 23L);
        assertTrue(burnedWithin(archer, 600), "the plain skeleton burns too");
        assertTrue(archer.health() < 20.0f,
                "the plain skeleton pays the fire clock (got " + archer.health() + ")");
    }

    @Test
    void theHelmetBlocksTheBurnAndWears() {
        // getEquipment(4)'s arm: any worn head blocks the ignition; a
        // damageable one pays +nextInt(2) per gated tick (the roll's own
        // cadence — the wear lands only on the ticks that would have
        // burned the bare body).
        SunWorld world = new SunWorld();
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 24L);
        zombie.setEquipmentAt(4, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1));
        assertFalse(burnedWithin(zombie, 300),
                "the worn head blocks every ignition");
        ItemStack worn = zombie.equipmentAt(4);
        assertNotNull(worn, "the cap survives the window (56 max)");
        assertTrue(worn.damage() > 0,
                "the gated ticks wore the cap (damage " + worn.damage() + ")");
        assertTrue(worn.damage() < 56, "the wear never passed the max in the window");
    }

    @Test
    void theHelmetShattersAndTheBurnFollows() {
        // The shatter at the durability max (the reference's own arc):
        // the cap at 55 of 56 breaks on the first gated tick, the slot
        // clears, onBrokenItem's pair rides the manager's drain (the
        // "random.break" sound at 0.8F with its pitch jitter, the
        // ITEM_CRACK crumbs over the broken stack) — and the now-bare
        // head catches the sun after it.
        SunWorld world = new SunWorld();
        List<String> sounds = new ArrayList<>();
        List<ItemStack> crumbs = new ArrayList<>();
        List<Float> pitches = new ArrayList<>();
        MobManager manager = new MobManager(world, new Random(31L),
                (position, stack) -> { }, 1_100_000, (x, z) -> 8);
        manager.addListener(new MobManager.Listener() {
            @Override public void onMobSpawned(MobEntity mob) { }
            @Override public void onMobMoved(MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession rider) { }
            @Override public void onMobHurt(MobEntity mob) { }
            @Override public void onMobDied(MobEntity mob) { }
            @Override public void onMobRemoved(MobEntity mob, String reason) { }
            @Override public void onMobAttackedPlayer(MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
            @Override public void onMobSound(MobEntity mob, String soundName) {
                sounds.add(soundName);
            }
            @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
            @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
            @Override public void onMobSheared(MobEntity mob, int woolCount) { }
            @Override public void onMobCoatRegrown(MobEntity mob) { }
            @Override public void onMobExploded(MobEntity mob) { }

            @Override
            public void onMobSoundAt(MobEntity mob, String soundName,
                                     float volume, float pitch) {
                sounds.add(soundName);
                pitches.add(pitch);
            }

            @Override
            public void onMobItemShattered(MobEntity mob, ItemStack broken) {
                crumbs.add(broken);
            }
        });
        MobEntity zombie = manager.spawnAt(MobType.ZOMBIE, new Position(0.5, 8.0, 0.5));
        zombie.setEquipmentAt(4, new ItemStack(BuiltinItems.LEATHER_HELMET, 1, 55));
        boolean broke = false;
        boolean burnedAfter = false;
        for (int i = 0; i < 500 && !burnedAfter; i++) {
            manager.tick(List.of(), 1_000L);
            if (zombie.equipmentAt(4).isEmpty()) {
                broke = true;
            }
            if (broke) {
                burnedAfter = zombie.burning();
            }
        }
        assertTrue(broke, "the cap at 55 of 56 shattered inside the window");
        assertNull(zombie.equipment(4), "the slot cleared at the break");
        assertTrue(sounds.contains("random.break"),
                "onBrokenItem's sound rode the drain");
        assertTrue(crumbs.size() >= 1
                        && crumbs.get(0).type().equals(BuiltinItems.LEATHER_HELMET),
                "the crumbs carried the broken cap");
        assertTrue(pitches.get(0) >= 0.8f && pitches.get(0) < 1.2f,
                "the break sound's pitch jitter band [0.8, 1.2)");
        assertTrue(burnedAfter,
                "the bare head caught the sun after the break (the arc's own end)");
    }

    @Test
    void thePumpkinBlocksForeverWithoutWearing() {
        // The non-damageable head (a carved pumpkin, maxDurability 0 —
        // isDamageable line 163's own gate): the burn never lands, the
        // stack never pays a point of wear.
        SunWorld world = new SunWorld();
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 25L);
        zombie.setEquipmentAt(4, ItemStack.of(BuiltinItems.PUMPKIN, 1));
        assertFalse(burnedWithin(zombie, 300), "the pumpkin holds the sun off");
        ItemStack worn = zombie.equipmentAt(4);
        assertNotNull(worn, "the pumpkin never breaks");
        assertEquals(0, worn.damage(), "the pumpkin never wears");
    }

    @Test
    void theBabyZombieNeverBurns() {
        // The zombie family's own baby gate (ZombieEntity line 160's
        // !isBaby() — the skeleton's arm carries no gate).
        SunWorld world = new SunWorld();
        MobEntity baby = mobIn(world, MobType.ZOMBIE, 26L);
        baby.setBreedingAge(-2_400);
        assertTrue(baby.isBaby(), "the fixture holds a baby");
        assertFalse(burnedWithin(baby, 300), "the baby stands the sun unburnt");
        assertTrue(baby.health() > 0.0f, "the baby lives");
    }

    @Test
    void theDarkCellNeverBurns() {
        // getBrightness(1.0F)'s gate: the eye cell under 0.5 answers no
        // ignition whatever the sky above it.
        SunWorld world = new SunWorld();
        world.brightness = 0.3f;
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 27L);
        assertFalse(burnedWithin(zombie, 300), "the dark cell never ignites");
    }

    @Test
    void theCoveredCellNeverBurns() {
        // hasSkyAccess's gate: the roof between the feet cell and the
        // sky column answers no ignition however bright the day.
        SunWorld world = new SunWorld();
        world.allCovered = true;
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 28L);
        assertFalse(burnedWithin(zombie, 300), "the covered cell never ignites");
    }

    @Test
    void theUnsunnyWorldNeverRolls() {
        // isSunny's gate — the rain-darkened sky, the dusk bands, the
        // roofed nether: the walk never even rolls.
        SunWorld world = new SunWorld();
        world.sunny = false;
        MobEntity zombie = mobIn(world, MobType.ZOMBIE, 29L);
        assertFalse(burnedWithin(zombie, 300), "the unsunny world never rolls");
    }
}
