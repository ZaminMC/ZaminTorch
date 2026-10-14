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

    @Test
    void theAmbientLadderCarriesTheReferenceFamilies() {
        // Slice 30c — WolfEntity.getAmbientSound lines 138-146: the angry
        // body always growls; the 1/3 roll whines on a tamed body under 10
        // health and pants otherwise; the 2/3 barks.
        MobEntity angry = wolf(31L);
        angry.setWolfAngry(true);
        for (int i = 0; i < 20; i++) {
            assertEquals("mob.wolf.growl", angry.effectiveIdleSound(),
                    "the angry body always growls");
        }
        MobEntity weakTamed = wolf(32L);
        weakTamed.tameWolf("Owner");
        weakTamed.setHealthCarried(6.0f);
        boolean whined = false, barkedWeak = false;
        for (int i = 0; i < 600; i++) {
            String sound = weakTamed.effectiveIdleSound();
            switch (sound) {
                case "mob.wolf.whine" -> whined = true;
                case "mob.wolf.bark" -> barkedWeak = true;
                default -> throw new AssertionError("the weak tamed body never "
                        + "pants (the reference's ternary): " + sound);
            }
        }
        assertTrue(whined, "the 1/3 whine arm observed under 10 health");
        assertTrue(barkedWeak, "the 2/3 bark arm observed");
        MobEntity strongTamed = wolf(33L);
        strongTamed.tameWolf("Owner");
        strongTamed.setHealthCarried(20.0f);
        boolean panted = false, barkedStrong = false;
        for (int i = 0; i < 600; i++) {
            String sound = strongTamed.effectiveIdleSound();
            switch (sound) {
                case "mob.wolf.panting" -> panted = true;
                case "mob.wolf.bark" -> barkedStrong = true;
                default -> throw new AssertionError("a healthy body never "
                        + "whines: " + sound);
            }
        }
        assertTrue(panted, "the 1/3 panting arm observed on the healthy body");
        assertTrue(barkedStrong, "the 2/3 bark arm observed");
    }

    @Test
    void theWaterShakeCycleWalksTheReferenceLadder() {
        // Slice 30d — WolfEntity.tick lines 193-222 + mobTick's start gate
        // lines 171-176. A mutable water pocket: the coat's touch reads it
        // live, the flip leaves the water.
        boolean[] water = {true};
        MobEntity.WorldQuery pocket = new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }
            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
            @Override
            public boolean inFluid(double x, double y, double z) {
                return water[0] && y >= 8.0 && y < 9.0;
            }
        };
        MobEntity wolf = new MobEntity(500, MobType.WOLF, new Position(0.5, 8.0, 0.5),
                new Random(51L), pocket);
        // Ground the body first, DRY (the physics walk sets onGround at
        // rest; the reference's own wolf stands on the floor under the
        // puddle — the water is the coat's touch, not the seat).
        water[0] = false;
        for (int i = 0; i < 10 && !wolf.onGround(); i++) {
            wolf.tick(false);
        }
        assertTrue(wolf.onGround(), "the body rests on the floor");

        // Phase 1: the wet re-arm while water touches — the cycle never
        // starts IN water (tick lines 193-197 reset every pass).
        water[0] = true;
        wolf.tickWolfShake();
        assertTrue(wolf.wolfWet(), "the water marked the coat");
        assertFalse(wolf.wolfShaking(), "the cycle never starts in water");
        assertFalse(wolf.consumePendingWolfShakeEvent(), "no event while wet");
        wolf.tickWolfShake(); // a second wet pass holds
        assertTrue(wolf.wolfWet());
        assertFalse(wolf.wolfShaking());

        // Phase 2: out of the water — the start gate fires (event 8 + the
        // cycle-start sound at progress zero).
        water[0] = false;
        wolf.tickWolfShake();
        assertTrue(wolf.wolfShaking(), "the start gate fired out of water");
        assertTrue(wolf.consumePendingWolfShakeEvent(), "the event-8 broadcast armed");
        assertTrue(wolf.consumePendingWolfShakeSound(), "the cycle-start sound armed");
        assertFalse(wolf.consumePendingWolfShakeEvent(), "the event is once");
        assertFalse(wolf.consumePendingWolfShakeSound(), "the sound is once");

        // Phase 3: the +0.05 walk — 40 ticks to the 2.0 end, the splash
        // burst armed past the 0.4 bend (first around progress 0.45).
        int splashTicks = 0;
        boolean sawSplashes = false;
        for (int i = 0; i < 45; i++) { // the +0.05 walk needs 41 passes past
            // the start to carry the pre-increment value through 2.0
            wolf.tickWolfShake();
            int splashes = wolf.consumePendingWolfShakeSplashes();
            if (splashes > 0) {
                sawSplashes = true;
                assertTrue(splashes <= 7, "sin(..)*7 never exceeds 7: " + splashes);
                splashTicks++;
            }
        }
        assertTrue(sawSplashes, "the splash burst ran past the 0.4 bend");
        assertTrue(splashTicks >= 15, "the burst covers the sin band (floor >= 1 needs sin >= 1/7): " + splashTicks);
        assertFalse(wolf.wolfWet(), "the cycle clears the water at the end");
        assertFalse(wolf.wolfShaking(), "the cycle ends at the 2.0 walk");

        // Phase 4: a dry body never starts anything.
        MobEntity dry = new MobEntity(501, MobType.WOLF, new Position(2.5, 8.0, 0.5),
                new Random(52L), pocket);
        for (int i = 0; i < 5 && !dry.onGround(); i++) {
            dry.tick(false);
        }
        dry.tickWolfShake();
        assertFalse(dry.wolfWet(), "a dry body stays dry");
        assertFalse(dry.wolfShaking());
    }
}
