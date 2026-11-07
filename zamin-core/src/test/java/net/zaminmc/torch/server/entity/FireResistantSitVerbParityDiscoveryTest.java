package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave FR — THE FIRE-RESISTANT SITTING PET'S VERB CORNER (the re-pin's
 * fourth row): the reference's takeDamage OVERRIDE clears the sit verb
 * at its HEAD (WolfEntity.takeDamage line 265 / OcelotEntity.takeDamage
 * line 154), BEFORE the super's own gates — the FIRE_RESISTANCE consult
 * included (LivingEntity lines 645-647 over the isFire source family):
 * a fire-resistant pet standing in fire or lava RISES (the verb stood
 * down at the burn tick's own takeDamage call) while the wound itself
 * is refused. The engine's fire family consulted the resistance BEFORE
 * the wound walk, so the resistant pet never stood the verb down — the
 * pet sat through its own burning, the pre-fix engine's corner.
 */
class FireResistantSitVerbParityDiscoveryTest {

    /** The flat stub world with the lava flag the lava seat arms. */
    private static final class FireWorld implements MobEntity.WorldQuery {
        boolean lava;

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
            return lava;
        }
    }

    private static MobManager managerIn(FireWorld world, long seed) {
        class NoLoot implements MobManager.LootSink {
            @Override public void spawnLootDrop(Position position,
                    net.zaminmc.torch.item.ItemStack stack, int dimension) { }
        }
        return new MobManager(world, new Random(seed), new NoLoot(),
                997_500, (x, z) -> 8);
    }

    /**
     * The seated pet (the TS2 seat's own settle-first rig): settled,
     * tamed through the real walk, the claim pass seating the posture.
     */
    private static MobEntity seatedPet(MobManager manager) {
        MobEntity pet = manager.spawnAt(MobType.WOLF,
                new Position(0.5, 8.0, 0.5));
        assertNotNull(pet, "the spawn never cancels here");
        pet.tick(false); // the settle (the fresh body grounds itself)
        pet.tameWolf("Owner", "owner-1");
        pet.tick(false); // the claim pass seats the posture
        assertTrue(pet.wolfSitting(), "the seat's own precondition");
        return pet;
    }

    // ------------------------------------------------------------------
    // Seat 1 — the resistant burning pet rises, never wounded
    // ------------------------------------------------------------------

    /**
     * A fire-resistant sitting pet burning outside any lava: the burn
     * clock's cadence arm calls the wound walk's head — the verb STANDS
     * DOWN (the pet rises at the goal's next pass) — while the
     * resistance consult refuses every wound (the health never moves).
     * RED on the pre-fix engine (the resistance gate sat before the
     * wound walk: the verb stayed armed, the pet sat through its own
     * burning).
     */
    @Test
    void theFireResistantBurningPetRisesUnwounded() {
        FireWorld world = new FireWorld();
        MobManager manager = managerIn(world, 99L);
        MobEntity pet = seatedPet(manager);
        pet.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.FIRE_RESISTANCE ? 1 : 0);
        pet.ignite(MobEntity.FIRE_TICKS);
        float health = pet.health();

        for (int tick = 0; tick < 60; tick++) {
            pet.tick(false);
        }
        assertFalse(pet.sitVerb(),
                "the burn tick's takeDamage call stands the verb down"
                        + " BEFORE the resistance consult — the resistant"
                        + " pet RISES (the pre-fix engine kept it seated)");
        assertEquals(health, pet.health(), 1e-6f,
                "the resistance refuses every wound — the health never"
                        + " moves");
        assertTrue(pet.fireTicks() >= 0, "the burn ran its visual course");
    }

    // ------------------------------------------------------------------
    // Seat 2 — the resistant pet in LAVA rises, never wounded
    // ------------------------------------------------------------------

    /**
     * The same corner over the lava walk (the every-tick takeDamage(LAVA,
     * 4.0) call): the verb stands down at the call's head, the resistance
     * refuses the wound — the pet rises from the lava's edge unwounded.
     */
    @Test
    void theFireResistantPetInLavaRisesUnwounded() {
        FireWorld world = new FireWorld();
        world.lava = true;
        MobManager manager = managerIn(world, 100L);
        MobEntity pet = seatedPet(manager);
        pet.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.FIRE_RESISTANCE ? 1 : 0);
        float health = pet.health();

        for (int tick = 0; tick < 30; tick++) {
            pet.tick(false);
        }
        assertFalse(pet.sitVerb(),
                "the lava tick's takeDamage call stands the verb down"
                        + " BEFORE the resistance consult (the pre-fix"
                        + " engine kept the resistant pet seated)");
        assertEquals(health, pet.health(), 1e-6f,
                "the resistance refuses the lava's every wound");
        assertTrue(pet.fireTicks() > 0,
                "the lava's ignite arms the visual burn");
    }

    // ------------------------------------------------------------------
    // Seat 3 — the unprotected control: the burning pet rises AND wounds
    // ------------------------------------------------------------------

    /**
     * The control: an UNPROTECTED sitting pet burning — the verb stands
     * down AND the wounds land at the per-second rhythm (the fresh-arm
     * cadence the LavaWalk family already pins — this seat's own
     * contrast for the corner's two halves).
     */
    @Test
    void theUnprotectedBurningPetRisesAndWounds() {
        FireWorld world = new FireWorld();
        MobManager manager = managerIn(world, 101L);
        MobEntity pet = seatedPet(manager);
        pet.ignite(MobEntity.FIRE_TICKS);
        float health = pet.health();

        for (int tick = 0; tick < 60; tick++) {
            pet.tick(false);
        }
        assertFalse(pet.sitVerb(),
                "the wound's verb stand-down (the TS2 law, unchanged)");
        assertTrue(pet.health() < health,
                "the unprotected burn wounds at the per-second rhythm");
        assertDoesNotThrow(() -> pet.tick(false),
                "the body keeps ticking");
    }

    private static void assertEquals(float expected, float actual, double eps,
            String message) {
        assertTrue(Math.abs(expected - actual) <= eps, message
                + " (expected " + expected + ", got " + actual + ")");
    }
}
