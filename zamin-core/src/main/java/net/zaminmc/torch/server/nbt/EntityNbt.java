package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The entity NBT projections (Slice 89 — the reference's
 * {@code Entity.writeNbtWithoutId}/{@code readNbt} +
 * {@code LivingEntity.writeCustomNbt}/{@code readCustomNbt} +
 * {@code MobEntity.writeCustomNbt}/{@code readCustomNbt} over the
 * engine's mob model, 1:1).
 *
 * <p><b>The base rows</b> (Entity lines 1109-1146 / 1148-1207): Pos,
 * Motion (the read's ±10 clamp per component), Rotation, FallDistance,
 * Fire, Air, OnGround, Dimension, Invulnerable, PortalCooldown,
 * UUIDMost/UUIDLeast (the LONG-typed gate, else the STRING "UUID" row),
 * CustomName + CustomNameVisible (the write's named gate), CommandStats
 * (the reference's own readNbt/writeNbt), Silent (the write's
 * silent-only gate).</p>
 *
 * <p><b>The engine's standing envelopes</b> (each row written with the
 * reference's own default, read into the engine's model where the model
 * carries the state — FallDistance 0.0f, Air 300s, PortalCooldown 0,
 * HurtByTimestamp 0, AbsorptionAmount 0.0f, CanPickUpLoot false, the
 * DropChances 0.085 quintet: the engine models none of these mechanics,
 * the rows round-trip only): ledgered.</p>
 *
 * <p><b>The living rows</b>: HealF (the any-numeric read gate) else the
 * Health row (the reference's own quirk — a FLOAT or SHORT row applies,
 * an INT row does NOT, a missing row resets to the ceiling), HurtTime,
 * DeathTime, Attributes (the {Name,Base} list — the engine honors
 * generic.movementSpeed and generic.attackDamage; the other names parse
 * but land nowhere), ActiveEffects (the reference's StatusEffectInstance
 * rows).</p>
 *
 * <p><b>The mob rows</b>: PersistenceRequired, Equipment (the five-slot
 * list — [0] the engine's held slot; [1..4] the armor family parses but
 * lands nowhere, the engine's no-mob-armor envelope), Leashed + Leash
 * (the knot's X/Y/Z or the holder's UUID pair; the read stashes the
 * compound unresolved — the reference's own leashNbt shape, the leash
 * tick resolves it), NoAI (the write's true-only gate).</p>
 */
public final class EntityNbt {

    private EntityNbt() {
    }

    /**
     * The reference's writeNbtWithoutId: the base rows, the CommandStats
     * ride, the Silent gate, then the custom walk (the living rows, the
     * mob rows).
     */
    public static void writeNbtWithoutId(MobEntity mob,
            Map<String, Snbt.Tag> nbt,
            PotionSystem potions) {
        Position at = mob.position();
        NbtOps.putList(nbt, "Pos", NbtOps.doubleList(at.x(), at.y(), at.z()));
        NbtOps.putList(nbt, "Motion",
                NbtOps.doubleList(mob.velocityX(), mob.velocityY(), mob.velocityZ()));
        NbtOps.putList(nbt, "Rotation", NbtOps.floatList(mob.yaw(), mob.pitch()));
        // The engine's standing envelopes (see the class doc): the
        // reference's own defaults where the engine models no state.
        NbtOps.putFloat(nbt, "FallDistance", 0.0f);
        NbtOps.putShort(nbt, "Fire", (short) mob.fireTicks());
        NbtOps.putShort(nbt, "Air", (short) 300);
        NbtOps.putBoolean(nbt, "OnGround", mob.onGround());
        NbtOps.putInt(nbt, "Dimension", mob.dimension());
        NbtOps.putBoolean(nbt, "Invulnerable", mob.invulnerable());
        NbtOps.putInt(nbt, "PortalCooldown", 0);
        UUID uuid = mob.getUuid();
        NbtOps.putLong(nbt, "UUIDMost", uuid.getMostSignificantBits());
        NbtOps.putLong(nbt, "UUIDLeast", uuid.getLeastSignificantBits());
        if (mob.customName() != null && !mob.customName().isEmpty()) {
            NbtOps.putString(nbt, "CustomName", mob.customName());
            NbtOps.putBoolean(nbt, "CustomNameVisible", mob.customNameVisible());
        }
        mob.commandResults().writeNbt(nbt);
        if (mob.isSilent()) {
            NbtOps.putBoolean(nbt, "Silent", true);
        }
        writeCustomNbt(mob, nbt, potions);
    }

    /**
     * The reference's readNbt: the base rows with the Motion ±10 clamp,
     * the position/rotation set, the UUID read (the LONG pair gate, else
     * the STRING row), then the custom walk.
     */
    public static void readNbt(MobEntity mob,
            Map<String, Snbt.Tag> nbt,
            PotionSystem potions,
            Runnable equipmentChanged) {
        List<Snbt.Tag> pos = NbtOps.getList(nbt, "Pos", Snbt.Tag.Type.DOUBLE);
        List<Snbt.Tag> motion = NbtOps.getList(nbt, "Motion", Snbt.Tag.Type.DOUBLE);
        List<Snbt.Tag> rotation = NbtOps.getList(nbt, "Rotation", Snbt.Tag.Type.FLOAT);
        double vx = NbtOps.listDouble(motion, 0);
        double vy = NbtOps.listDouble(motion, 1);
        double vz = NbtOps.listDouble(motion, 2);
        // The reference's own clamp (lines 1156-1166): a component beyond
        // ±10 reads as 0.
        if (Math.abs(vx) > 10.0) {
            vx = 0.0;
        }
        if (Math.abs(vy) > 10.0) {
            vy = 0.0;
        }
        if (Math.abs(vz) > 10.0) {
            vz = 0.0;
        }
        mob.setVelocity(vx, vy, vz);
        mob.teleport(new Position(
                NbtOps.listDouble(pos, 0),
                NbtOps.listDouble(pos, 1),
                NbtOps.listDouble(pos, 2)));
        float yaw = NbtOps.listFloat(rotation, 0);
        float pitch = NbtOps.listFloat(rotation, 1);
        mob.setRotation(yaw, pitch);
        // The engine's standing envelopes (see the class doc): FallDistance,
        // Air, PortalCooldown read into no model.
        mob.setFireTicks(NbtOps.getShort(nbt, "Fire"));
        mob.setOnGround(NbtOps.getBoolean(nbt, "OnGround"));
        mob.setDimension(NbtOps.getInt(nbt, "Dimension"));
        mob.setInvulnerable(NbtOps.getBoolean(nbt, "Invulnerable"));
        if (NbtOps.contains(nbt, "UUIDMost", Snbt.Tag.Type.LONG)
                && NbtOps.contains(nbt, "UUIDLeast", Snbt.Tag.Type.LONG)) {
            mob.setUuid(new UUID(
                    NbtOps.getLong(nbt, "UUIDMost"),
                    NbtOps.getLong(nbt, "UUIDLeast")));
        } else if (NbtOps.contains(nbt, "UUID", Snbt.Tag.Type.STRING)) {
            mob.setUuid(UUID.fromString(NbtOps.getString(nbt, "UUID")));
        }
        if (NbtOps.contains(nbt, "CustomName", Snbt.Tag.Type.STRING)
                && !NbtOps.getString(nbt, "CustomName").isEmpty()) {
            mob.setCustomName(NbtOps.getString(nbt, "CustomName"));
        }
        mob.setCustomNameVisible(NbtOps.getBoolean(nbt, "CustomNameVisible"));
        mob.commandResults().readNbt(nbt);
        mob.setSilent(NbtOps.getBoolean(nbt, "Silent"));
        readCustomNbt(mob, nbt, potions, equipmentChanged);
        // The reference's shouldSetPositionOnLoad arm: the position is set
        // again after the custom walk (the engine's plain form).
        mob.teleport(mob.position());
    }

    // ------------------------------------------------------------------
    // The custom walk (LivingEntity + MobEntity, the reference's order)
    // ------------------------------------------------------------------

    private static void writeCustomNbt(MobEntity mob,
            Map<String, Snbt.Tag> nbt,
            PotionSystem potions) {
        // LivingEntity.writeCustomNbt.
        NbtOps.putFloat(nbt, "HealF", mob.health());
        NbtOps.putShort(nbt, "Health", (short) Math.ceil(mob.health()));
        NbtOps.putShort(nbt, "HurtTime", (short) mob.hurtTime());
        NbtOps.putInt(nbt, "HurtByTimestamp", 0);
        NbtOps.putShort(nbt, "DeathTime", (short) mob.deathTime());
        NbtOps.putFloat(nbt, "AbsorptionAmount", 0.0f);
        List<Snbt.Tag> attributes = new ArrayList<>();
        if (mob.hasMovementSpeedOverride()) {
            attributes.add(attributeRows("generic.movementSpeed",
                    mob.movementSpeedOverride()));
        }
        if (mob.hasAttackDamageOverride()) {
            attributes.add(attributeRows("generic.attackDamage",
                    mob.attackDamageOverride()));
        }
        if (!attributes.isEmpty()) {
            NbtOps.putList(nbt, "Attributes", attributes);
        }
        Map<Integer, StatusEffectInstance> effects = potions.allOf(mob);
        if (!effects.isEmpty()) {
            List<Snbt.Tag> activeEffects = new ArrayList<>();
            for (StatusEffectInstance effect : effects.values()) {
                Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
                NbtOps.putByte(rows, "Id", (byte) effect.id());
                NbtOps.putByte(rows, "Amplifier", (byte) effect.amplifier());
                NbtOps.putInt(rows, "Duration", effect.duration());
                NbtOps.putBoolean(rows, "Ambient", effect.isAmbient());
                NbtOps.putBoolean(rows, "ShowParticles", effect.hasParticles());
                activeEffects.add(Snbt.Tag.ofCompound(rows));
            }
            NbtOps.putList(nbt, "ActiveEffects", activeEffects);
        }
        // MobEntity.writeCustomNbt.
        NbtOps.putBoolean(nbt, "CanPickUpLoot", false);
        NbtOps.putBoolean(nbt, "PersistenceRequired", mob.rawPersistent());
        List<Snbt.Tag> equipment = new ArrayList<>();
        // [0] the held slot; [1..4] the armor family — the engine's
        // no-mob-armor envelope writes the empty rows.
        equipment.add(Snbt.Tag.ofCompound(
                ItemNbt.slotRows(0, mob.heldEquipment())));
        for (int slot = 1; slot <= 4; slot++) {
            equipment.add(Snbt.Tag.ofCompound(
                    ItemNbt.slotRows(slot, null)));
        }
        NbtOps.putList(nbt, "Equipment", equipment);
        NbtOps.putList(nbt, "DropChances",
                NbtOps.floatList(0.085f, 0.085f, 0.085f, 0.085f, 0.085f));
        NbtOps.putBoolean(nbt, "Leashed", mob.isLeashed());
        if (mob.isLeashed()) {
            Map<String, Snbt.Tag> leash = new java.util.LinkedHashMap<>();
            if (mob.leashHolderPlayer() != null) {
                UUID holder = mob.leashHolderPlayer().uuid();
                NbtOps.putLong(leash, "UUIDMost", holder.getMostSignificantBits());
                NbtOps.putLong(leash, "UUIDLeast", holder.getLeastSignificantBits());
            } else if (mob.leashHolderKnot() != null) {
                var fence = mob.leashHolderKnot().fence();
                NbtOps.putInt(leash, "X", fence.x());
                NbtOps.putInt(leash, "Y", fence.y());
                NbtOps.putInt(leash, "Z", fence.z());
            }
            NbtOps.putCompound(nbt, "Leash", leash);
        }
        if (mob.noAi()) {
            NbtOps.putBoolean(nbt, "NoAI", true);
        }
    }

    private static Snbt.Tag attributeRows(String name, double base) {
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        NbtOps.putString(rows, "Name", name);
        NbtOps.putDouble(rows, "Base", base);
        return Snbt.Tag.ofCompound(rows);
    }

    private static void readCustomNbt(MobEntity mob,
            Map<String, Snbt.Tag> nbt,
            PotionSystem potions,
            Runnable equipmentChanged) {
        // LivingEntity.readCustomNbt.
        // Attributes (the reference's contains(key, 9) list gate): the
        // {Name, Base} walk — the engine honors the two consult names.
        List<Snbt.Tag> attributes = NbtOps.getList(nbt, "Attributes", Snbt.Tag.Type.COMPOUND);
        for (Snbt.Tag row : attributes) {
            Map<String, Snbt.Tag> compound = row != null ? row.asCompound() : null;
            if (compound == null) {
                continue;
            }
            String name = NbtOps.getString(compound, "Name");
            double base = NbtOps.getDouble(compound, "Base");
            if (name == null || name.isEmpty() || base <= 0.0) {
                continue;
            }
            if ("generic.movementSpeed".equals(name)) {
                mob.setMovementSpeedOverride(base);
            } else if ("generic.attackDamage".equals(name)) {
                mob.setAttackDamageOverride((float) base);
            }
        }
        // ActiveEffects (the reference's fromNbt: the BY_ID gate, the
        // ShowParticles BYTE-only gate).
        List<Snbt.Tag> activeEffects = NbtOps.getList(nbt, "ActiveEffects", Snbt.Tag.Type.COMPOUND);
        for (Snbt.Tag row : activeEffects) {
            Map<String, Snbt.Tag> compound = row != null ? row.asCompound() : null;
            if (compound == null) {
                continue;
            }
            int id = NbtOps.getByte(compound, "Id");
            StatusEffects.Effect type = StatusEffects.byId(id);
            if (type == null) {
                continue;
            }
            int amplifier = NbtOps.getByte(compound, "Amplifier");
            int duration = NbtOps.getInt(compound, "Duration");
            StatusEffectInstance effect =
                    new StatusEffectInstance(id, duration, amplifier);
            effect.setAmbient(NbtOps.getBoolean(compound, "Ambient"));
            if (NbtOps.contains(compound, "ShowParticles", Snbt.Tag.Type.BYTE)) {
                effect.setParticles(NbtOps.getBoolean(compound, "ShowParticles"));
            }
            potions.putFromNbt(mob, effect);
        }
        // The health family (the reference's own quirk): HealF applies on
        // any numeric row (contains("HealF", 99)); else the Health row
        // applies as FLOAT or SHORT only — an INT row (the SNBT default
        // for a bare number) is ignored, a MISSING row resets to the
        // ceiling.
        if (NbtOps.containsNumeric(nbt, "HealF")) {
            mob.setHealthCarried(NbtOps.getFloat(nbt, "HealF"));
        } else {
            Snbt.Tag health = NbtOps.get(nbt, "Health");
            if (health == null) {
                mob.setHealthCarried(mob.maxHealth());
            } else if (health.type() == Snbt.Tag.Type.FLOAT) {
                mob.setHealthCarried(health.asFloat());
            } else if (health.type() == Snbt.Tag.Type.SHORT) {
                mob.setHealthCarried((short) health.asDouble());
            }
        }
        mob.setHurtTime(NbtOps.getShort(nbt, "HurtTime"));
        mob.setDeathTime(NbtOps.getShort(nbt, "DeathTime"));
        // HurtByTimestamp / AbsorptionAmount: the engine's standing
        // envelopes (no model).
        // MobEntity.readCustomNbt.
        mob.setPersistent(NbtOps.getBoolean(nbt, "PersistenceRequired"));
        List<Snbt.Tag> equipment = NbtOps.getList(nbt, "Equipment", Snbt.Tag.Type.COMPOUND);
        if (!equipment.isEmpty()) {
            // [0] the held slot lands (the equipment-changed wake is the
            // caller's); [1..4] the armor family parses but lands nowhere.
            net.zaminmc.torch.item.ItemStack held =
                    ItemNbt.read(NbtOps.listCompound(equipment, 0));
            if (held != null) {
                mob.setHeldEquipment(held);
                if (equipmentChanged != null) {
                    equipmentChanged.run();
                }
            } else {
                mob.setHeldEquipment(null);
                if (equipmentChanged != null) {
                    equipmentChanged.run();
                }
            }
        }
        // DropChances: the engine's standing envelope (no model).
        mob.applyLeashRows(NbtOps.getBoolean(nbt, "Leashed"), nbt);
        mob.setNoAi(NbtOps.getBoolean(nbt, "NoAI"));
    }
}
