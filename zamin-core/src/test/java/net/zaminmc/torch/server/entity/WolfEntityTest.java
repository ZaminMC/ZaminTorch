package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wolf's own mechanics (Slice 15d — the reference's WolfEntity): the
 * kind identity, the tame's full flip (the flags, the owner, the 20-health
 * tamed body, the 4.0 attack damage, the sitting start), the sitting
 * toggle, the angry posture's own clear, the breed gates (both tamed,
 * neither sitting), and the despawn rules (the wild one moves on past
 * 2400 ticks, the tamed one never does).
 */
class WolfEntityTest {

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

    private static MobEntity wolf(long seed) {
        return new MobEntity(500, MobType.WOLF, new Position(0.5, 8.0, 0.5),
                new Random(seed), worldAt());
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(95, MobType.WOLF.legacyTypeId);
        assertEquals(0.6, MobType.WOLF.width, 1e-9); // setSize(0.6F, 0.8F)
        assertEquals(0.8, MobType.WOLF.height, 1e-9);
        assertEquals(8.0f, MobType.WOLF.maxHealth, 1e-6f); // the wild ceiling
        assertFalse(MobType.WOLF.hostile); // an untamed wolf hunts sheep, not players
        assertTrue(MobType.WOLF.loot.isEmpty()); // Item.byId(-1)
        assertEquals("mob.wolf.bark", MobType.WOLF.idleSound);
        assertEquals("mob.wolf.hurt", MobType.WOLF.hurtSound);
        assertEquals("mob.wolf.death", MobType.WOLF.deathSound);
        assertEquals("minecraft:wolf", MobType.WOLF.identifier().toString());
    }

    @Test
    void theTameFlipsTheWholeBody() {
        MobEntity wolf = wolf(11L);
        assertFalse(wolf.wolfTamed());
        assertEquals(8.0f, wolf.maxHealth(), 1e-6f);
        assertEquals(2.0f, wolf.attackDamage(), 1e-6f); // the wild kind default
        assertTrue(wolf.ownerName().isEmpty());

        wolf.tameWolf("Owner");
        assertTrue(wolf.wolfTamed());
        assertEquals("Owner", wolf.ownerName());
        assertEquals(20.0f, wolf.health(), 1e-6f); // setHealth(20) under the raised ceiling
        assertEquals(20.0f, wolf.maxHealth(), 1e-6f); // setTamed's attribute recompute
        assertEquals(4.0f, wolf.attackDamage(), 1e-6f); // setTamed's damage recompute
        assertTrue(wolf.wolfSitting()); // sitGoal.setEnabledWithOwner(true)

        // The collar's default is the reference's own red (DyeColor.RED).
        assertEquals(1, wolf.wolfCollarColor());
        // The flags byte: sitting 0x1 | tamed 0x4.
        assertEquals(0x5, wolf.wolfFlagsRaw());
    }

    @Test
    void theTamedBodyHealsToItsOwnCeiling() {
        MobEntity wolf = wolf(12L);
        wolf.tameWolf("Owner");
        // Hurt below the tamed ceiling, heal back to 20 (not the wild 8).
        wolf.hurt(6.0f);
        assertEquals(14.0f, wolf.health(), 1e-6f);
        wolf.heal(10.0f);
        assertEquals(20.0f, wolf.health(), 1e-6f);
    }

    @Test
    void theSitToggleAndTheAngryClear() {
        MobEntity wolf = wolf(13L);
        wolf.tameWolf("Owner");
        assertTrue(wolf.wolfSitting());
        wolf.toggleWolfSitting();
        assertFalse(wolf.wolfSitting());
        wolf.toggleWolfSitting();
        assertTrue(wolf.wolfSitting());

        // The angry posture stands until the targetless clear (mobTick's
        // own walk: no attack target -> not angry).
        wolf.setWolfAngry(true);
        assertTrue(wolf.wolfAngry());
        for (int i = 0; i < 3; i++) {
            wolf.tick(false); // the targetless mind: the angry bit clears
        }
        assertFalse(wolf.wolfAngry());
    }

    @Test
    void theBreedGatesNeedTwoTamedStandingBodies() {
        MobEntity a = wolf(14L);
        MobEntity b = wolf(15L);
        // Wild bodies never breed (WolfEntity.canBreedWith's tamed gates).
        a.enterLove(1);
        b.enterLove(2);
        assertFalse(a.canBreedWith(b));

        a.tameWolf("Owner");
        b.tameWolf("Owner");
        // The tame starts both sitting (sitGoal.setEnabledWithOwner(true))
        // — the sitting gate refuses while they sit.
        assertTrue(a.wolfSitting());
        assertFalse(a.canBreedWith(b));
        a.toggleWolfSitting();
        b.toggleWolfSitting();
        assertFalse(a.wolfSitting());
        assertFalse(b.wolfSitting());
        a.enterLove(1);
        b.enterLove(2);
        assertTrue(a.canBreedWith(b));

        // A sitting body stands down (the reference's own !isSitting gate).
        a.toggleWolfSitting();
        assertTrue(a.wolfSitting());
        assertFalse(a.canBreedWith(b)); // the sitting gate
    }

    @Test
    void theWildWolfMovesOnButTheTamedOneStays() {
        MobEntity wild = wolf(16L);
        // The fresh wild wolf: not yet past the 2400-tick window.
        assertFalse(wild.canDespawn());
        for (int i = 0; i < 2401; i++) {
            wild.tick(false);
        }
        assertTrue(wild.canDespawn(), "the wild wolf despawns past 2400 ticks");
        assertFalse(wild.persistent(), "the wild wolf is not persistent");

        MobEntity tamed = wolf(17L);
        tamed.tameWolf("Owner");
        for (int i = 0; i < 3000; i++) {
            tamed.tick(false);
        }
        assertFalse(tamed.canDespawn(), "the tamed wolf never despawns");
        assertTrue(tamed.persistent(), "the tamed wolf is persistent");
    }
}
