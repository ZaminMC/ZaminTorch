package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animal husbandry slice against the reference numbers (AnimalEntity,
 * AnimalBreedGoal, HorseBaseEntity, HorseMenu): the 600-tick love window,
 * the damage-clears-love rule, the off-age love clear, the breed landing
 * (60 ticks inside the 3-block band, 6000-tick cooldown, -24000 childhood),
 * the horse family's type table (horse+donkey makes the sterile mule), the
 * full-health tame gate on the horse's canBreed, and the donkey chest's
 * 15-slot grid with its death spill.
 */
class BreedingAndDonkeyChestTest {

    /** Solid everywhere below y=4 (the flat test world). */
    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static final MobManager.LootSink NO_LOOT = (position, stack) -> {
    };

    private record Rig(MobManager manager, Events events) {
    }

    private static final class Events implements MobManager.Listener {
        MobEntity lastBaby;
        int breeds;
        int loveBursts;
        int grewUps;

        @Override public void onMobSpawned(MobEntity mob) { }
        @Override public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(MobEntity mob) { }
        @Override public void onMobDied(MobEntity mob) { }
        @Override public void onMobRemoved(MobEntity mob, String reason) { }
        @Override public void onMobAttackedPlayer(MobEntity mob,
                net.zaminmc.torch.server.player.PlayerSession target, float damage) { }
        @Override public void onMobSound(MobEntity mob, String soundName) { }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { }
        @Override public void onMobCoatRegrown(MobEntity mob) { }
        @Override public void onMobExploded(MobEntity mob) { }
        @Override public void onMobBred(MobEntity parent, MobEntity mate, MobEntity baby) {
            lastBaby = baby;
            breeds++;
        }
        @Override public void onMobLoveBurst(MobEntity mob) {
            loveBursts++;
        }
        @Override public void onMobGrewUp(MobEntity mob) {
            grewUps++;
        }
    }

    private Rig rig(long seed) {
        Events events = new Events();
        MobManager manager = new MobManager(worldAt(), new Random(seed), NO_LOOT,
                2_000_000, (x, z) -> 4);
        manager.addListener(events);
        return new Rig(manager, events);
    }

    private static MobEntity cow(MobManager manager, double x, double z) {
        return manager.spawnAt(MobType.COW, new Position(x, 4.0, z));
    }

    // ------------------------------------------------ the love window

    @Test
    void feedEntersLoveForTheHistorical600Ticks() {
        Rig rig = rig(1);
        MobEntity cow = cow(rig.manager(), 100.5, 100.5);
        assertFalse(cow.isInLove(), "fresh cows are indifferent");
        cow.enterLove(7);
        assertTrue(cow.isInLove(), "the wheat landed");
        assertEquals(600, cow.loveTicksLeft(), "the vanilla love window");
        assertEquals(7, cow.lovePlayerId(), "the feeder rides the state");
        // The window drains one tick at a time (the manager's clock walk).
        rig.manager().tick(List.of(), 1000L);
        assertEquals(599, cow.loveTicksLeft(), "one drain per tick");
    }

    @Test
    void aHitClearsTheLove() {
        Rig rig = rig(2);
        MobEntity cow = cow(rig.manager(), 100.5, 100.5);
        cow.enterLove(7);
        cow.hurt(1.0f);
        assertFalse(cow.isInLove(), "AnimalEntity.takeDamage resets the love");
        // And the love never returns on its own.
        rig.manager().tick(List.of(), 1000L);
        assertFalse(cow.isInLove(), "the clear sticks");
    }

    @Test
    void offAgeBodiesCannotHoldLove() {
        Rig rig = rig(3);
        MobEntity cow = cow(rig.manager(), 100.5, 100.5);
        cow.enterLove(7);
        cow.setBreedingAge(MobEntity.ADULT_BREED_COOLDOWN_TICKS);
        rig.manager().tick(List.of(), 1000L);
        assertFalse(cow.isInLove(), "AnimalEntity.mobTick: love dies off-age");
        assertTrue(cow.isBaby() || cow.breedingAge() > 0, "the cooldown is ticking");
    }

    @Test
    void feedingABabyShrinksItsChildhoodByATenth() {
        Rig rig = rig(4);
        MobEntity cow = cow(rig.manager(), 100.5, 100.5);
        cow.setBreedingAge(MobEntity.CHILDHOOD_TICKS);
        // The vanilla baby feed: growUp((int)(-age / 20 * 0.1F)) — the
        // (int) cast truncates, the unit is 20 ticks.
        int units = (int) (-cow.breedingAge() / 20 * 0.1f);
        cow.growUp(units);
        assertEquals(-24000 + 2400, cow.breedingAge(),
                "one feed removes exactly one tenth of the childhood");
    }

    @Test
    void babiesGrowOneTickAtATimeIntoAdulthood() {
        Rig rig = rig(5);
        MobEntity cow = cow(rig.manager(), 100.5, 100.5);
        cow.setBreedingAge(-2);
        rig.manager().tick(List.of(), 1000L);
        assertEquals(-1, cow.breedingAge(), "the first walk");
        assertEquals(0, rig.events().grewUps, "still a child");
        rig.manager().tick(List.of(), 1000L);
        assertEquals(0, cow.breedingAge(), "the crossing tick");
        assertEquals(1, rig.events().grewUps, "the wire hears the grew-up flip");
        assertFalse(cow.isBaby(), "an adult now");
    }

    // ------------------------------------------------ the breed landing

    @Test
    void proximityBreedLandsBabyCooldownAndCleanSlate() {
        Rig rig = rig(6);
        MobEntity a = cow(rig.manager(), 100.5, 100.5);
        MobEntity b = cow(rig.manager(), 102.0, 100.5);
        a.enterLove(7);
        b.enterLove(8);
        // The goal counts 60 ticks of proximity before the breed lands.
        for (int i = 0; i < 59; i++) {
            rig.manager().tick(List.of(), 1000L);
        }
        assertNull(rig.events().lastBaby, "the window is not up yet");
        rig.manager().tick(List.of(), 1000L);
        MobEntity baby = rig.events().lastBaby;
        assertNotNull(baby, "the 60th proximity tick lands the breed");
        assertEquals(MobType.COW, baby.type(), "cows make cows");
        assertEquals(MobEntity.CHILDHOOD_TICKS, baby.breedingAge(), "the child starts owing 24000");
        assertEquals(MobEntity.ADULT_BREED_COOLDOWN_TICKS, a.breedingAge(), "the parent cooldown");
        assertEquals(MobEntity.ADULT_BREED_COOLDOWN_TICKS, b.breedingAge(), "both parents");
        assertFalse(a.isInLove() || b.isInLove(), "the love clears on the breed");
        assertTrue(rig.events().loveBursts > 0, "the celebration hearts fired");
    }

    // ------------------------------------------------ the horse family rules

    @Test
    void horseBreedingNeedsTameFullHealthAndNoRider() {
        Rig rig = rig(7);
        MobEntity a = rig.manager().spawnAt(MobType.HORSE, new Position(100.5, 4.0, 100.5));
        MobEntity b = rig.manager().spawnAt(MobType.HORSE, new Position(101.5, 4.0, 100.5));
        a.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        b.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        a.enterLove(1);
        b.enterLove(2);
        assertFalse(a.canBreedWith(b), "untamed horses never breed");
        a.tame("a");
        b.tame("b");
        assertTrue(a.canBreedWith(b), "tamed full-health adults in love qualify");
    }

    @Test
    void hurtHorsesAreBarrenUntilHealed() {
        MobEntity a = new MobEntity(1, MobType.HORSE, new Position(0.5, 4.0, 0.5),
                new Random(1), worldAt());
        MobEntity b = new MobEntity(2, MobType.HORSE, new Position(1.5, 4.0, 0.5),
                new Random(2), worldAt());
        tameAndLove(a);
        tameAndLove(b);
        a.hurt(1.0f); // 21/22: no longer full health (and the hit clears love)
        assertFalse(a.isInLove(), "the hit clears the love (AnimalEntity.takeDamage)");
        assertFalse(a.canBreedWith(b),
                "HorseBaseEntity.canBreed requires health >= maxHealth");
        a.heal(1.0f);
        a.enterLove(1); // re-loved: the feeding flow would do this
        assertTrue(a.canBreedWith(b), "healed back to the ceiling, the gate opens");
    }

    @Test
    void horseDonkeyCrossMakesTheSterileMule() {
        Rig rig = rig(8);
        MobEntity horse = rig.manager().spawnAt(MobType.HORSE, new Position(100.5, 4.0, 100.5));
        MobEntity donkey = rig.manager().spawnAt(MobType.HORSE, new Position(101.0, 4.0, 100.5));
        horse.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        donkey.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        tameAndLove(horse);
        tameAndLove(donkey);
        assertTrue(horse.canBreedWith(donkey), "the 0/1 cross is legal");
        // Drive the manager's breed through the goal landing.
        for (int i = 0; i < MobEntity.BREED_PROXIMITY_TICKS; i++) {
            rig.manager().tick(List.of(), 1000L);
        }
        MobEntity baby = rig.events().lastBaby;
        assertNotNull(baby, "the cross produced a child");
        assertEquals(MobEntity.HORSE_SUBTYPE_MULE, baby.horseSubtype(),
                "makeChild's type table: 0x1 makes the mule");
        assertTrue(baby.isMule(), "the baby is the sterile kind");
        // The mule never breeds, even when its love state is forced.
        baby.enterLove(3);
        assertFalse(baby.canBreedWith(horse), "mules are barren (noLove)");
        assertFalse(horse.canBreedWith(baby), "the gate is symmetric");
    }

    @Test
    void donkeyDonkeyMakesDonkeys() {
        Rig rig = rig(9);
        MobEntity a = rig.manager().spawnAt(MobType.HORSE, new Position(100.5, 4.0, 100.5));
        MobEntity b = rig.manager().spawnAt(MobType.HORSE, new Position(101.0, 4.0, 100.5));
        a.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        b.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        tameAndLove(a);
        tameAndLove(b);
        for (int i = 0; i < MobEntity.BREED_PROXIMITY_TICKS; i++) {
            rig.manager().tick(List.of(), 1000L);
        }
        MobEntity baby = rig.events().lastBaby;
        assertNotNull(baby, "donkeys breed");
        assertEquals(MobEntity.HORSE_SUBTYPE_DONKEY, baby.horseSubtype(),
                "same-type parents pass the subtype on");
    }

    private static void tameAndLove(MobEntity horse) {
        // The taming slice's tame bit, then the love.
        horse.tame("tester");
        horse.setBreedingAge(0);
        horse.enterLove(1);
    }

    // ------------------------------------------------ the donkey chest

    @Test
    void chestEquipsOnlyTheLongEaredKinds() {
        MobEntity donkey = new MobEntity(1, MobType.HORSE, new Position(0.5, 4.0, 0.5),
                new Random(1), worldAt());
        donkey.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        assertFalse(donkey.chested(), "bare donkey");
        donkey.setChested(true);
        assertTrue(donkey.chested(), "the chest rides");
        assertTrue((donkey.horseFlagsRaw() & MobEntity.HORSE_FLAG_CHESTED) != 0,
                "the flag bit the client renders");
        assertEquals(MobEntity.CHEST_SLOT_COUNT, donkey.chestGrid().length,
                "the vanilla 15-slot grid");
        // The plain horse never takes one (canHaveChest: type 1/2 only).
        MobEntity horse = new MobEntity(2, MobType.HORSE, new Position(5.5, 4.0, 0.5),
                new Random(2), worldAt());
        horse.setHorseSubtype(MobEntity.HORSE_SUBTYPE_HORSE);
        horse.setChested(true); // the gate lives in the interaction layer; the body stores
        assertFalse(horse.isMule(), "sanity: the plain horse is not a mule");
    }

    @Test
    void chestCarriesStacksAndSpillsOnDeath() {
        MobEntity donkey = new MobEntity(1, MobType.HORSE, new Position(0.5, 4.0, 0.5),
                new Random(1), worldAt());
        donkey.setHorseSubtype(MobEntity.HORSE_SUBTYPE_DONKEY);
        donkey.tame("tester");
        donkey.applySaddle();
        donkey.setChested(true);
        donkey.setChestSlot(0, ItemStack.of(BuiltinItems.WHEAT, 32));
        donkey.setChestSlot(14, ItemStack.of(BuiltinItems.DIAMOND, 3));
        assertEquals(32, donkey.chestSlot(0).count(), "the grid holds stacks");
        assertTrue(donkey.chestSlot(1).isEmpty(), "empty slots stay empty");
        assertTrue(donkey.chestSlot(-1).isEmpty(), "the read guards the index");
        assertTrue(donkey.chestSlot(15).isEmpty(), "15 slots only");

        List<ItemStack> spilled = donkey.spillMountGear();
        // Saddle + chest + the two stacks, in the dropInventoryAndChest order.
        assertEquals(4, spilled.size(), "saddle, chest, and the contents");
        assertEquals(BuiltinItems.SADDLE, spilled.get(0).type());
        assertEquals(BuiltinItems.CHEST, spilled.get(1).type());
        assertEquals(BuiltinItems.WHEAT, spilled.get(2).type());
        assertEquals(32, spilled.get(2).count());
        assertEquals(BuiltinItems.DIAMOND, spilled.get(3).type());
        assertFalse(donkey.chested(), "the chest is gone");
        assertNull(donkey.chestGrid(), "the grid resets with it");
        assertFalse(donkey.saddled(), "the saddle is gone too");
    }

    @Test
    void pigSpillIsTheSaddleOnly() {
        MobEntity pig = new MobEntity(1, MobType.PIG, new Position(0.5, 4.0, 0.5),
                new Random(1), worldAt());
        pig.applySaddle();
        List<ItemStack> spilled = pig.spillMountGear();
        assertEquals(1, spilled.size(), "the pig carries nothing else");
        assertEquals(BuiltinItems.SADDLE, spilled.get(0).type());
        assertFalse(pig.saddled(), "the saddle leaves with the drop");
    }
}
