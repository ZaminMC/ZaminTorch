package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The mooshroom (Slice 17b — MooshroomEntity over the cow): the kind's
 * identity, the wheat breeding family, the child's own kind, and the
 * shear conversion's health carry.
 */
class MooshroomEntityTest {

    /** A flat-world stub. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static MobEntity mooshroomAt(Position at, long seed) {
        return new MobEntity(995, MobType.MOOSHROOM, at, new Random(seed),
                new ScriptedWorld());
    }

    @Test
    void theMooshroomCarriesTheCowsIdentity() {
        // Entities.java line 280: the "MushroomCow" 96.
        assertEquals(96, MobType.MOOSHROOM.legacyTypeId);
        assertEquals(0.9, MobType.MOOSHROOM.width, 1e-9);
        assertEquals(1.3, MobType.MOOSHROOM.height, 1e-9);
        assertEquals(10.0f, MobType.MOOSHROOM.maxHealth, 1e-6f);
        assertFalse(MobType.MOOSHROOM.hostile);
        assertEquals(2.0f, MobType.MOOSHROOM.attackDamage, 1e-6f,
                "the interact-only body carries the flat melee");
        // The cow's sound family (the reference inherits them).
        assertEquals("mob.cow.say", MobType.MOOSHROOM.idleSound);
        assertEquals("mob.cow.hurt", MobType.MOOSHROOM.hurtSound);
        // The leather + raw beef loot (the cow's own).
        assertEquals(2, MobType.MOOSHROOM.loot.size());
    }

    @Test
    void theShearConversionCarriesTheHealth() {
        MobEntity mooshroom = mooshroomAt(new Position(0.5, 8.0, 0.5), 1L);
        mooshroom.hurt(4.0f);
        assertEquals(6.0f, mooshroom.health(), 1e-6f);

        // The shear-into-a-cow's health carry (the reference's
        // setHealth(getHealth()) on the new body): the clamp keeps the
        // value inside the cow's own ceiling.
        MobEntity cow = new MobEntity(996, MobType.COW,
                mooshroom.position(), new Random(2L), new ScriptedWorld());
        cow.setHealthCarried(mooshroom.health());
        assertEquals(6.0f, cow.health(), 1e-6f);
        // The clamp: a value over the ceiling saturates.
        cow.setHealthCarried(99.0f);
        assertEquals(10.0f, cow.health(), 1e-6f);
    }

    @Test
    void theBabyIsAMooshroom() {
        // makeChild (line 71): the mooshroom breeds its own kind — the
        // engine's childTypeOf answers the parent's kind, so the family
        // stays pure through the wheat breeding.
        MobEntity baby = mooshroomAt(new Position(0.5, 8.0, 0.5), 3L);
        baby.setBreedingAge(-24_000);
        assertTrue(baby.isBaby());
        assertEquals(MobType.MOOSHROOM, baby.type());
    }
}
