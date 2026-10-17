package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The absorption model (Slice 92 — the base-defaults envelope's first
 * landing): the reference's {@code AbsorptionStatusEffect} modifier pair
 * ({@code ±4 * (amplifier + 1)}) over the engine's mob body, the hurt
 * walk's own absorb step (LivingEntity.applyDamage lines 913-921 — the
 * eat-first order, the fully-absorbed skip, the double-shave quirk), and
 * the {@code AbsorptionAmount} NBT row into the model.
 */
class AbsorptionModelTest {

    /** The minimal world (the WorldQuery defaults answer everything else). */
    private static final class StubWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return false;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    /** The bodies seam with the absorption arm the engine's PotionBodies carries. */
    private static final class AbsorbingBodies implements PotionSystem.Bodies {
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
        public void hurt(Object body, float amount, String cause, Object attacker, boolean magic) {
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
            return ((MobEntity) body).health();
        }

        @Override
        public boolean fireResistant(Object body) {
            return false;
        }

        @Override
        public boolean undead(Object body) {
            return false;
        }

        @Override
        public boolean isPlayer(Object body) {
            return false;
        }

        @Override
        public int keyOf(Object body) {
            return ((MobEntity) body).entityId();
        }

        @Override
        public void absorb(Object body, float delta) {
            ((MobEntity) body).setAbsorption(((MobEntity) body).absorption() + delta);
        }
    }

    private static MobEntity zombie() {
        MobEntity mob = new MobEntity(7, MobType.ZOMBIE,
                new Position(1.5, 64.0, 2.5), new Random(11), new StubWorld());
        mob.setHealthCarried(20.0f);
        return mob;
    }

    private static PotionSystem potions() {
        return new PotionSystem(new AbsorbingBodies());
    }

    @Test
    void aFreshEffectGrantsFourHeartsPerAmplifierStep() {
        // AbsorptionStatusEffect.addModifiers: +4 * (amplifier + 1) — the
        // golden apple's own band (2400 ticks at amplifier 0 = 4 hearts).
        MobEntity mob = zombie();
        PotionSystem potions = potions();
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        assertEquals(4.0f, mob.absorption(), 1e-6f);

        MobEntity stronger = zombie();
        potions.add(stronger, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1));
        assertEquals(8.0f, stronger.absorption(), 1e-6f);
    }

    @Test
    void aFullyAbsorbedHitLandsNoHealthChange() {
        // applyDamage lines 913-916: the absorption eats first; the
        // {@code if (damage != 0.0F)} guard skips the whole health walk —
        // a 4-heart body absorbs a 3-damage hit whole, one heart left.
        MobEntity mob = zombie();
        mob.setAbsorption(4.0f);
        float health = mob.health();
        mob.hurt(3.0f);
        assertEquals(health, mob.health(), 1e-6f);
        assertEquals(1.0f, mob.absorption(), 1e-6f);
        assertTrue(!mob.dead());
    }

    @Test
    void aRemainingHitShavesTheStoreTwice() {
        // The double-shave quirk (lines 913-921): the eaten part shaves
        // the store, then the LANDED damage shaves it AGAIN — a 7-damage
        // hit over 4 hearts eats all four (the clamp) and lands 3 on
        // the health.
        MobEntity mob = zombie();
        mob.setAbsorption(4.0f);
        float health = mob.health();
        mob.hurt(7.0f);
        assertEquals(health - 3.0f, mob.health(), 1e-6f);
        assertEquals(0.0f, mob.absorption(), 1e-6f);
    }

    @Test
    void theUpgradeWalkRestoresTheEatenHearts() {
        // onStatusEffectUpgraded(merged, true) — the combine path's
        // remove+add pair at the MERGED amplifier: the remove dips
        // negative (clamped zero), the add stacks the full band back —
        // a re-applied absorption RESTORES the eaten hearts (the
        // reference's own quirk).
        MobEntity mob = zombie();
        PotionSystem potions = potions();
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        mob.hurt(3.0f); // one heart left
        assertEquals(1.0f, mob.absorption(), 1e-6f);
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        assertEquals(4.0f, mob.absorption(), 1e-6f);
    }

    @Test
    void aStrongerReapplyGrowsTheBand() {
        // The upgrade I -> II: the merged amplifier rides the pair —
        // remove(1) dips -8 (clamped), add(1) stacks +8; the body's own
        // remaining hearts ride the clamp, the band lands at the full 8.
        MobEntity mob = zombie();
        PotionSystem potions = potions();
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        mob.hurt(3.0f); // one heart left of the 4
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1));
        assertEquals(8.0f, mob.absorption(), 1e-6f);
    }

    @Test
    void theRemovalTakesTheBandBack() {
        // onStatusEffectRemoved — removeModifiers: the band goes back at
        // the amplifier it was granted (the eaten remainder stays eaten).
        MobEntity mob = zombie();
        PotionSystem potions = potions();
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 1));
        assertEquals(8.0f, mob.absorption(), 1e-6f);
        mob.hurt(5.0f); // three hearts left of the 8
        potions.remove(mob, StatusEffects.ABSORPTION);
        assertEquals(0.0f, mob.absorption(), 1e-6f);
    }

    @Test
    void theClearWalksEveryBandBack() {
        // clearStatusEffects: every effect walks the removal arm.
        MobEntity mob = zombie();
        PotionSystem potions = potions();
        potions.add(mob, new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        potions.add(mob, new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
        potions.clear(mob);
        assertEquals(0.0f, mob.absorption(), 1e-6f);
    }

    @Test
    void theNbtRowRoundTripsIntoTheModel() {
        // writeCustomNbt/readCustomNbt: the row carries the model's own
        // hearts both ways (writeNbt line 389, readNbt line 418 — the
        // read installs FIRST, before the ActiveEffects rows ride put).
        MobEntity saved = zombie();
        saved.setAbsorption(6.5f);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(saved, written, potions());
        assertEquals(6.5f, NbtOps.getFloat(written, "AbsorptionAmount"), 1e-6f);

        MobEntity loaded = zombie();
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(6.5f, loaded.absorption(), 1e-6f);
    }
}
