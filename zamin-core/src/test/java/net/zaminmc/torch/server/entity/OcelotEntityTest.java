package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ocelot's own mechanics (Slice 15e — the reference's OcelotEntity):
 * the kind identity, the fish tame's state flip (the tameable family's
 * bits, the coat roll, NO health flip — the cat keeps its 10 ceiling),
 * the breed gates (the shared tamed-and-standing law), and the despawn
 * pair (the tameable family's 2400-tick window).
 */
class OcelotEntityTest {

    /** A flat-world stub: solid below y=8, air above, no player. */
    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static MobEntity ocelot(long seed) {
        return new MobEntity(600, MobType.OCELOT, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt());
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(98, MobType.OCELOT.legacyTypeId);
        assertEquals(0.6, MobType.OCELOT.width, 1e-9); // setSize(0.6F, 0.7F)
        assertEquals(0.7, MobType.OCELOT.height, 1e-9);
        assertEquals(10.0f, MobType.OCELOT.maxHealth, 1e-6f); // initAttributes
        assertFalse(MobType.OCELOT.hostile);
        assertTrue(MobType.OCELOT.loot.isEmpty()); // dropItems' empty override
        assertEquals("", MobType.OCELOT.idleSound); // the untamed cat is silent
        assertEquals("mob.cat.hitt", MobType.OCELOT.hurtSound);
        assertEquals("mob.cat.hitt", MobType.OCELOT.deathSound);
        assertEquals(3.0f, MobType.OCELOT.attackDamage, 1e-6f); // tryDamage's override
        assertEquals("minecraft:ocelot", MobType.OCELOT.identifier().toString());
    }

    @Test
    void theFishTameFlipsTheStateButNotTheCeiling() {
        MobEntity cat = ocelot(21L);
        assertFalse(cat.wolfTamed());
        assertEquals(0, cat.catVariant()); // the wild coat
        assertEquals(10.0f, cat.health(), 1e-6f);

        // Hurt it a little first: the tame must NOT heal (the wolf's 20 flip
        // is its own — the cat keeps whatever it had under the same 10).
        cat.hurt(3.0f);
        assertEquals(7.0f, cat.health(), 1e-6f);
        cat.tameOcelot("Owner", 2);
        assertTrue(cat.wolfTamed());
        assertEquals("Owner", cat.ownerName());
        assertEquals(2, cat.catVariant()); // 1 + nextInt(3): the tamed coats
        assertTrue(cat.wolfSitting()); // sitGoal.setEnabledWithOwner(true)
        assertEquals(7.0f, cat.health(), 1e-6f); // NO health flip
        assertEquals(10.0f, cat.maxHealth(), 1e-6f); // the same ceiling
        assertEquals(3.0f, cat.attackDamage(), 1e-6f); // the same melee
    }

    @Test
    void theBreedGatesShareTheTameableLaw() {
        MobEntity a = ocelot(22L);
        MobEntity b = ocelot(23L);
        a.enterLove(1);
        b.enterLove(2);
        assertFalse(a.canBreedWith(b), "wild cats never breed");

        a.tameOcelot("Owner", 1);
        b.tameOcelot("Owner", 3);
        assertTrue(a.wolfSitting());
        assertFalse(a.canBreedWith(b), "the sitting gate");
        a.toggleWolfSitting();
        b.toggleWolfSitting();
        a.enterLove(1);
        b.enterLove(2);
        assertTrue(a.canBreedWith(b), "two tamed standing cats breed");

        // A wolf and an ocelot never breed (the kind gate).
        MobEntity dog = new MobEntity(601, MobType.WOLF, new Position(0.5, 8.0, 0.5),
                new Random(24L), worldAt());
        dog.tameWolf("Owner");
        dog.toggleWolfSitting();
        a.enterLove(1);
        assertFalse(a.canBreedWith(dog));
    }

    @Test
    void theWildCatMovesOnButTheTamedOneStays() {
        MobEntity wild = ocelot(25L);
        assertFalse(wild.canDespawn());
        for (int i = 0; i < 2401; i++) {
            wild.tick(false);
        }
        assertTrue(wild.canDespawn(), "the wild cat despawns past 2400 ticks");
        assertFalse(wild.persistent());

        MobEntity tamed = ocelot(26L);
        tamed.tameOcelot("Owner", 1);
        for (int i = 0; i < 3000; i++) {
            tamed.tick(false);
        }
        assertFalse(tamed.canDespawn(), "the tamed cat never despawns");
        assertTrue(tamed.persistent());
    }

    @Test
    void theTamedAmbientFamilyCarriesTheReferenceLadder() {
        // Slice 30c — OcelotEntity.getAmbientSound lines 111-121: the
        // untamed body stays silent; the tamed body purrs in love, else a
        // quarter purreow over the meow.
        MobEntity wild = ocelot(41L);
        for (int i = 0; i < 20; i++) {
            assertEquals("", wild.effectiveIdleSound(), "the wild cat is silent");
        }
        MobEntity cat = ocelot(42L);
        cat.tameWolf("Owner");
        cat.enterLove(9);
        for (int i = 0; i < 20; i++) {
            assertEquals("mob.cat.purr", cat.effectiveIdleSound(),
                    "the in-love body purrs");
        }
        cat.resetLove(); // the purr clears with the love window
        boolean meowed = false, purreowed = false;
        for (int i = 0; i < 800; i++) {
            String sound = cat.effectiveIdleSound();
            switch (sound) {
                case "mob.cat.meow" -> meowed = true;
                case "mob.cat.purreow" -> purreowed = true;
                default -> throw new AssertionError("outside the family: " + sound);
            }
        }
        assertTrue(meowed, "the meow arm observed");
        assertTrue(purreowed, "the 1/4 purreow arm observed");
    }
}
