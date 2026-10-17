package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The entity NBT projections (Slice 89 — the reference's
 * writeNbtWithoutId/readNbt + the custom walks over the engine's mob
 * model): the base rows, the Motion clamp, the health family's own
 * quirk, the Equipment held slot, the ActiveEffects rows, the Attributes
 * rows, the leash stash, and the CommandStats ride.
 */
class EntityNbtTest {

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

    /** The minimal bodies seam (the potion store's keying alone matters). */
    private static final class StubBodies implements PotionSystem.Bodies {
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
    }

    private static MobEntity zombie() {
        return new MobEntity(7, MobType.ZOMBIE,
                new Position(1.5, 64.0, 2.5), new Random(11), new StubWorld());
    }

    @Test
    void theBaseRowsRoundTrip() {
        MobEntity mob = zombie();
        mob.setCustomName("Boss");
        mob.setCustomNameVisible(true);
        mob.setSilent(true);
        mob.setNoAi(true);
        mob.setInvulnerable(true);
        mob.setPersistent(true);
        mob.setFireTicks(120);
        mob.setHurtTime(10);
        mob.setDeathTime(0);
        mob.setOnGround(true);
        mob.setVelocity(0.25, -0.05, 0.75);
        mob.setRotation(90.0f, 15.0f);
        mob.setHealthCarried(13.5f);

        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());

        // The written forms (the reference's own rows).
        assertEquals(3, NbtOps.getList(written, "Pos", Snbt.Tag.Type.DOUBLE).size());
        assertEquals(1.5, NbtOps.listDouble(
                NbtOps.getList(written, "Pos", Snbt.Tag.Type.DOUBLE), 0), 1e-9);
        assertEquals(0.25, NbtOps.listDouble(
                NbtOps.getList(written, "Motion", Snbt.Tag.Type.DOUBLE), 0), 1e-9);
        assertEquals(90.0f, NbtOps.listFloat(
                NbtOps.getList(written, "Rotation", Snbt.Tag.Type.FLOAT), 0), 1e-6f);
        assertEquals(15.0f, NbtOps.listFloat(
                NbtOps.getList(written, "Rotation", Snbt.Tag.Type.FLOAT), 1), 1e-6f);
        assertEquals(120, NbtOps.getShort(written, "Fire"));
        assertEquals(300, NbtOps.getShort(written, "Air"));
        assertTrue(NbtOps.getBoolean(written, "OnGround"));
        assertTrue(NbtOps.getBoolean(written, "Invulnerable"));
        assertEquals(mob.getUuid().getMostSignificantBits(),
                NbtOps.getLong(written, "UUIDMost"));
        assertEquals("Boss", NbtOps.getString(written, "CustomName"));
        assertTrue(NbtOps.getBoolean(written, "CustomNameVisible"));
        assertTrue(NbtOps.getBoolean(written, "Silent"));
        assertTrue(NbtOps.getBoolean(written, "NoAI"));
        assertTrue(NbtOps.getBoolean(written, "PersistenceRequired"));
        assertEquals(13.5f, NbtOps.getFloat(written, "HealF"), 1e-6f);
        assertEquals(14, NbtOps.getShort(written, "Health"), "ceil(13.5) as a short");
        assertEquals(10, NbtOps.getShort(written, "HurtTime"));
        // The name row is absent when the body carries none.
        MobEntity unnamed = zombie();
        Map<String, Snbt.Tag> unnamedRows = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(unnamed, unnamedRows, potions());
        assertNull(NbtOps.get(unnamedRows, "CustomName"));
        assertNull(NbtOps.get(unnamedRows, "Silent"));
        assertNull(NbtOps.get(unnamedRows, "NoAI"));

        // The read onto a fresh body: every row lands.
        MobEntity loaded = zombie();
        java.util.UUID writtenUuid = mob.getUuid();
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(1.5, loaded.position().x(), 1e-9);
        assertEquals(64.0, loaded.position().y(), 1e-9);
        assertEquals(2.5, loaded.position().z(), 1e-9);
        assertEquals(0.25, loaded.velocityX(), 1e-9);
        assertEquals(90.0f, loaded.yaw(), 1e-6f);
        assertEquals(15.0f, loaded.pitch(), 1e-6f);
        assertEquals(90.0f, loaded.headYaw(), 1e-6f, "the head yaw follows the yaw");
        assertEquals(120, loaded.fireTicks());
        assertTrue(loaded.onGround());
        assertTrue(loaded.invulnerable());
        assertTrue(loaded.isSilent());
        assertTrue(loaded.noAi());
        assertTrue(loaded.rawPersistent());
        assertEquals(writtenUuid, loaded.getUuid());
        assertEquals("Boss", loaded.customName());
        assertTrue(loaded.customNameVisible());
        assertEquals(13.5f, loaded.health(), 1e-6f);
        assertEquals(10, loaded.hurtTime());
    }

    @Test
    void theMotionClampIsTheReferenceOwnWalk() {
        MobEntity mob = zombie();
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{Motion:[15.0d,-0.5d,11.0d]}");
        EntityNbt.readNbt(mob, rows, potions(), null);
        // A component beyond ±10 reads as 0 (the reference's lines 1156-1166).
        assertEquals(0.0, mob.velocityX(), 1e-9);
        assertEquals(-0.5, mob.velocityY(), 1e-9);
        assertEquals(0.0, mob.velocityZ(), 1e-9);
    }

    @Test
    void theHealthFamilyCarriesTheReferenceQuirk() {
        PotionSystem potions = potions();
        // HealF applies on ANY numeric row (the contains(key,99) gate) —
        // an INT row lands.
        MobEntity byHealF = zombie();
        EntityNbt.readNbt(byHealF, Snbt.parseCompound("{HealF:7}"), potions, null);
        assertEquals(7.0f, byHealF.health(), 1e-6f);
        // The Health row applies as FLOAT or SHORT only — an INT row (the
        // SNBT default for a bare number) is IGNORED, a MISSING row resets
        // to the ceiling.
        MobEntity byIntHealth = zombie();
        EntityNbt.readNbt(byIntHealth, Snbt.parseCompound("{Health:7}"), potions, null);
        assertEquals(MobType.ZOMBIE.maxHealth, byIntHealth.health(), 1e-6f,
                "an INT Health row is ignored, the ceiling stays");
        MobEntity byShortHealth = zombie();
        EntityNbt.readNbt(byShortHealth, Snbt.parseCompound("{Health:7s}"), potions, null);
        assertEquals(7.0f, byShortHealth.health(), 1e-6f);
        MobEntity byFloatHealth = zombie();
        EntityNbt.readNbt(byFloatHealth, Snbt.parseCompound("{Health:7.0f}"), potions, null);
        assertEquals(7.0f, byFloatHealth.health(), 1e-6f);
        MobEntity missing = zombie();
        missing.setHealthCarried(3.0f);
        EntityNbt.readNbt(missing, Snbt.parseCompound("{}"), potions, null);
        assertEquals(MobType.ZOMBIE.maxHealth, missing.health(), 1e-6f,
                "a missing Health row resets to the ceiling");
    }

    @Test
    void theEquipmentHeldSlotLandsAndWakes() {
        PotionSystem potions = potions();
        boolean[] fired = {false};
        MobEntity mob = zombie();
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{Equipment:[{Slot:0b,id:\"minecraft:iron_sword\",Count:1b,Damage:40s},{}]}");
        EntityNbt.readNbt(mob, rows, potions, () -> fired[0] = true);
        assertTrue(fired[0], "the equipment-changed wake fired once");
        assertNotNull(mob.heldEquipment());
        assertEquals("minecraft:iron_sword",
                mob.heldEquipment().type().identifier().toString());
        assertEquals(40, mob.heldEquipment().damage());
        // The written form carries the slot row (the reference's own shape).
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        List<Snbt.Tag> equipment = NbtOps.getList(written, "Equipment", Snbt.Tag.Type.COMPOUND);
        assertEquals(5, equipment.size(), "the five-slot list");
        Map<String, Snbt.Tag> held = NbtOps.listCompound(equipment, 0);
        assertEquals(0, NbtOps.getByte(held, "Slot"));
        assertEquals("minecraft:iron_sword", NbtOps.getString(held, "id"));
        assertEquals(1, NbtOps.getByte(held, "Count"));
        // The armor slots write the empty rows (the engine's no-mob-armor
        // envelope).
        assertEquals("minecraft:air", NbtOps.getString(NbtOps.listCompound(equipment, 1), "id"));
    }

    @Test
    void theActiveEffectsRowsRoundTrip() {
        PotionSystem potions = potions();
        MobEntity mob = zombie();
        potions.putFromNbt(mob, new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
        potions.get(mob, StatusEffects.SPEED).setAmbient(true);
        potions.get(mob, StatusEffects.SPEED).setParticles(false);

        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions);
        List<Snbt.Tag> effects = NbtOps.getList(written, "ActiveEffects", Snbt.Tag.Type.COMPOUND);
        assertEquals(1, effects.size());
        Map<String, Snbt.Tag> row = NbtOps.listCompound(effects, 0);
        assertEquals(StatusEffects.SPEED, NbtOps.getByte(row, "Id"));
        assertEquals(1, NbtOps.getByte(row, "Amplifier"));
        assertEquals(400, NbtOps.getInt(row, "Duration"));
        assertTrue(NbtOps.getBoolean(row, "Ambient"));
        assertFalse(NbtOps.getBoolean(row, "ShowParticles"));

        // The read installs the rows on a fresh body (the replace arm).
        MobEntity loaded = zombie();
        EntityNbt.readNbt(loaded, written, potions, null);
        StatusEffectInstance speed = potions.get(loaded, StatusEffects.SPEED);
        assertNotNull(speed);
        assertEquals(1, speed.amplifier());
        assertEquals(400, speed.duration());
        assertTrue(speed.isAmbient());
        assertFalse(speed.hasParticles());
        // A body carrying nothing writes no row at all.
        Map<String, Snbt.Tag> bare = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(loaded, bare, potions());
        // The effects row DOES exist here (the read installed one) — the
        // empty gate is the no-effects case:
        MobEntity fresh = zombie();
        Map<String, Snbt.Tag> freshRows = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(fresh, freshRows, potions());
        assertNull(NbtOps.get(freshRows, "ActiveEffects"));
    }

    @Test
    void theAttributesRowsRoundTrip() {
        PotionSystem potions = potions();
        MobEntity mob = zombie();
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{Attributes:[{Name:\"generic.movementSpeed\",Base:0.35d},"
                        + "{Name:\"generic.maxHealth\",Base:40.0d}]}");
        EntityNbt.readNbt(mob, rows, potions, null);
        assertTrue(mob.hasMovementSpeedOverride());
        assertEquals(0.35, mob.walkSpeed(), 1e-9);
        // The unknown name parses but lands nowhere (the engine's
        // per-type attribute envelope, ledgered).
        assertFalse(mob.hasAttackDamageOverride());

        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        List<Snbt.Tag> attributes = NbtOps.getList(written, "Attributes", Snbt.Tag.Type.COMPOUND);
        assertEquals(1, attributes.size(), "only the honored names write");
        Map<String, Snbt.Tag> row = NbtOps.listCompound(attributes, 0);
        assertEquals("generic.movementSpeed", NbtOps.getString(row, "Name"));
        assertEquals(0.35, NbtOps.getDouble(row, "Base"), 1e-9);
    }

    @Test
    void theLeashRowsStashUnresolved() {
        PotionSystem potions = potions();
        MobEntity mob = zombie();
        Map<String, Snbt.Tag> rows = Snbt.parseCompound(
                "{Leashed:true,Leash:{X:10,Y:64,Z:-20}}");
        EntityNbt.readNbt(mob, rows, potions, null);
        assertTrue(mob.isLeashed());
        // The compound stashes unresolved (the reference's leashNbt); the
        // resolver takes it once.
        Map<String, Snbt.Tag> stash = mob.takeLeashNbt();
        assertNotNull(stash);
        assertEquals(10, NbtOps.getInt(stash, "X"));
        assertEquals(64, NbtOps.getInt(stash, "Y"));
        assertEquals(-20, NbtOps.getInt(stash, "Z"));
        assertNull(mob.takeLeashNbt(), "the stash is consumed once");
        // A false Leashed row clears outright.
        EntityNbt.readNbt(mob, Snbt.parseCompound("{Leashed:false}"), potions, null);
        assertFalse(mob.isLeashed());
        assertNull(mob.takeLeashNbt());
    }

    @Test
    void theCommandStatsRideRoundTrips() {
        PotionSystem potions = potions();
        MobEntity mob = zombie();
        mob.commandResults().set(
                net.zaminmc.torch.server.command.EngineCommandResults.Type.AFFECTED_BLOCKS,
                "Zombie", "blocks");
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        Map<String, Snbt.Tag> stats = NbtOps.getCompound(written, "CommandStats");
        assertEquals("Zombie", NbtOps.getString(stats, "AffectedBlocksName"));
        assertEquals("blocks", NbtOps.getString(stats, "AffectedBlocksObjective"));

        MobEntity loaded = zombie();
        EntityNbt.readNbt(loaded, written, potions, null);
        assertEquals("Zombie", loaded.commandResults().sourceOf(
                net.zaminmc.torch.server.command.EngineCommandResults.Type.AFFECTED_BLOCKS));
        assertEquals("blocks", loaded.commandResults().objectiveOf(
                net.zaminmc.torch.server.command.EngineCommandResults.Type.AFFECTED_BLOCKS));
    }

    private static PotionSystem potions() {
        return new PotionSystem(new StubBodies());
    }

    @Test
    void theSheepAndPigRowsRoundTrip() {
        PotionSystem potions = potions();
        MobEntity sheep = new MobEntity(11, MobType.SHEEP,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        sheep.setSheared(true);
        sheep.setSheepFleeceColor(6); // DyeColor id 6
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(sheep, written, potions);
        assertTrue(NbtOps.getBoolean(written, "Sheared"));
        assertEquals(6, NbtOps.getByte(written, "Color"));

        MobEntity loaded = new MobEntity(12, MobType.SHEEP,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        EntityNbt.readNbt(loaded, written, potions, null);
        assertTrue(loaded.sheared());
        assertEquals(6, loaded.sheepFleeceColor());

        MobEntity pig = new MobEntity(13, MobType.PIG,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        Map<String, Snbt.Tag> pigRows = Snbt.parseCompound("{Saddle:true}");
        EntityNbt.readNbt(pig, pigRows, potions, null);
        assertTrue(pig.pigSaddled());
        Map<String, Snbt.Tag> pigWritten = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(pig, pigWritten, potions);
        assertTrue(NbtOps.getBoolean(pigWritten, "Saddle"));
    }

    @Test
    void theHorseRowsRoundTripThroughTheItemForms() {
        PotionSystem potions = potions();
        MobEntity horse = new MobEntity(14, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        horse.setTamedFlag(true);
        horse.applySaddle();
        horse.applyArmor(2); // golden
        horse.setHorseVariant(4 | 2 << 8);
        horse.setHorseTemper(55);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(horse, written, potions);
        assertTrue(NbtOps.getBoolean(written, "Tame"));
        assertTrue(NbtOps.contains(written, "SaddleItem", Snbt.Tag.Type.COMPOUND));
        assertEquals("minecraft:saddle",
                NbtOps.getString(NbtOps.getCompound(written, "SaddleItem"), "id"));
        assertEquals("minecraft:golden_horse_armor",
                NbtOps.getString(NbtOps.getCompound(written, "ArmorItem"), "id"));
        assertEquals(4 | 2 << 8, NbtOps.getInt(written, "Variant"));
        assertEquals(55, NbtOps.getInt(written, "Temper"));

        MobEntity loaded = new MobEntity(15, MobType.HORSE,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        EntityNbt.readNbt(loaded, written, potions, null);
        assertTrue(loaded.tamed());
        assertTrue(loaded.saddled());
        assertEquals(2, loaded.armorType(), "the ArmorItem row's id landed as the armor type");
        assertEquals(4 | 2 << 8, loaded.variant());
        assertEquals(55, loaded.temper());
    }

    @Test
    void theVillagerWolfZombieRowsRoundTrip() {
        PotionSystem potions = potions();
        MobEntity villager = new MobEntity(16, MobType.VILLAGER,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        Map<String, Snbt.Tag> villagerRows = Snbt.parseCompound("{Profession:3}");
        EntityNbt.readNbt(villager, villagerRows, potions, null);
        assertEquals(3, villager.profession());

        MobEntity wolf = new MobEntity(17, MobType.WOLF,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        Map<String, Snbt.Tag> wolfRows = Snbt.parseCompound(
                "{Tame:true,Sitting:true,Angry:false,CollarColor:5b}");
        EntityNbt.readNbt(wolf, wolfRows, potions, null);
        assertTrue(wolf.wolfTamed());
        assertTrue(wolf.wolfSitting());
        assertFalse(wolf.wolfAngry());
        assertEquals(5, wolf.wolfCollarColor());

        MobEntity zombie = new MobEntity(18, MobType.ZOMBIE,
                new Position(0.5, 64.0, 0.5), new Random(3), new StubWorld());
        Map<String, Snbt.Tag> zombieRows = Snbt.parseCompound(
                "{IsVillager:true,IsBaby:true,ConversionTime:120}");
        EntityNbt.readNbt(zombie, zombieRows, potions, null);
        assertTrue(zombie.zombieVillager());
        assertTrue(zombie.isBaby());
        assertTrue(zombie.zombieConverting());
        assertEquals(120, zombie.zombieConversionTicks());
        // The written form: the reference's own gates (IsVillager/IsBaby
        // only when true, the conversion time always with -1 for off).
        Map<String, Snbt.Tag> zombieWritten = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(zombie, zombieWritten, potions);
        assertTrue(NbtOps.getBoolean(zombieWritten, "IsVillager"));
        assertTrue(NbtOps.getBoolean(zombieWritten, "IsBaby"));
        assertEquals(120, NbtOps.getInt(zombieWritten, "ConversionTime"));
        // The reference's own read gate: a ConversionTime row arms only
        // when > -1 — a -1 row (the write's "not converting" marker) is
        // IGNORED, the countdown keeps whatever it held.
        EntityNbt.readNbt(zombie, Snbt.parseCompound("{ConversionTime:-1}"), potions, null);
        assertTrue(zombie.zombieConverting(), "a -1 row does not cancel (the reference's own gate)");
        assertEquals(120, zombie.zombieConversionTicks());
    }
}
