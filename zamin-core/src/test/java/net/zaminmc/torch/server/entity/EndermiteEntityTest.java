package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endermite (Slice 17c — EndermiteEntity): the pearl's passenger —
 * the kind's identity, the ARTHROPOD damage category, and the lifetime
 * despawn clock.
 */
class EndermiteEntityTest {

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

    private static MobEntity endermiteAt(long seed) {
        return new MobEntity(997, MobType.ENDERMITE, new Position(0.5, 8.0, 0.5),
                new Random(seed), new ScriptedWorld());
    }

    @Test
    void theEndermiteCarriesTheIdentity() {
        // Entities.java line 272: the endermite 67.
        assertEquals(67, MobType.ENDERMITE.legacyTypeId);
        assertEquals(0.4, MobType.ENDERMITE.width, 1e-9);
        assertEquals(0.3, MobType.ENDERMITE.height, 1e-9);
        assertEquals(8.0f, MobType.ENDERMITE.maxHealth, 1e-6f);
        assertTrue(MobType.ENDERMITE.hostile);
        assertEquals(2.0f, MobType.ENDERMITE.attackDamage, 1e-6f);
        // The silverfish's own sound family (lines 64-75).
        assertEquals("mob.silverfish.say", MobType.ENDERMITE.idleSound);
        assertEquals("mob.silverfish.hit", MobType.ENDERMITE.hurtSound);
        assertEquals("mob.silverfish.kill", MobType.ENDERMITE.deathSound);
        // getDropItem's null: no loot rows at all.
        assertTrue(MobType.ENDERMITE.loot.isEmpty());
        // The ARTHROPOD damage category (getMobType line 153): Bane of
        // Arthropods lands on the family — the cave spider and the
        // silverfish ride the same category in the reference.
        assertEquals(2, MobType.ENDERMITE.damageCategory());
        assertEquals(2, MobType.SILVERFISH.damageCategory());
        assertEquals(2, MobType.CAVE_SPIDER.damageCategory());
    }

    @Test
    void theEyeIsTheReferenceFlat() {
        MobEntity endermite = endermiteAt(1L);
        // getEyeHeight line 57: the flat 0.1F.
        assertEquals(0.1, endermite.eyePosition().y() - endermite.position().y(), 1e-9);
    }

    @Test
    void theLifetimeWallRemovesTheBody() {
        MobEntity endermite = endermiteAt(2L);
        // The clock only counts the non-persistent body (mobTick lines
        // 120-123: the isPersistent gate).
        endermite.setPersistent(true);
        for (int i = 0; i < 2500; i++) {
            endermite.tick(true);
        }
        assertFalse(endermite.consumeEndermiteExpire(),
                "the persistent body never expires");

        endermite.setPersistent(false);
        // 2400 ticks: the wall (mobTick lines 125-127).
        for (int i = 0; i < 2399; i++) {
            endermite.tick(true);
        }
        assertFalse(endermite.consumeEndermiteExpire(), "one tick short");
        endermite.tick(true);
        assertTrue(endermite.consumeEndermiteExpire(), "the 2400-tick wall");
    }
}
