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
 * <p><b>The base-default rows LANDED</b> (Slice 92 — each into its own
 * behavioral model): the absorption hearts (the hurt walk's eat-first
 * step, the ABSORPTION effect's ±4 band through the potion system's
 * absorb hook), the fall accumulator (checkFallDamage's accumulate /
 * spend / water-zero walk, takeFallDamage's ceil(distance - 3 - jump
 * boost) wound with the empty-override kinds), the breath store (the
 * eye-level drain, the -20 drown rhythm, the water-breathers, the
 * WATER_BREATHING shield), the portal cooldown (the decrement), the
 * hurt timestamp (the age stamp the hurt walk writes).</p>
 *
 * <p><b>The loot rows LANDED</b> (Slice 92 p3): CanPickUpLoot (the
 * spawn roll's flag, the villager's always-true, the BYTE-gated read)
 * and the DropChances quintet (the death walk's own gate — a picked-up
 * slot carries the 2.0 always-drop marker) into the engine's model. The
 * item-entity thrower field (the GIVE_DIAMOND arm's own gate) stays the
 * engine's standing envelope.</p>
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
        // Slice 92 — the base-default rows into the engine's own models:
        // the fall accumulator, the breath store, the portal cooldown.
        NbtOps.putFloat(nbt, "FallDistance", mob.fallDistance());
        NbtOps.putShort(nbt, "Fire", (short) mob.fireTicks());
        NbtOps.putShort(nbt, "Air", (short) mob.air());
        NbtOps.putBoolean(nbt, "OnGround", mob.onGround());
        NbtOps.putInt(nbt, "Dimension", mob.dimension());
        NbtOps.putBoolean(nbt, "Invulnerable", mob.invulnerable());
        NbtOps.putInt(nbt, "PortalCooldown", mob.portalCooldown());
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
        // Slice 92 — the base-default rows into the models (the
        // reference's own ungated reads).
        mob.setFallDistance(NbtOps.getFloat(nbt, "FallDistance"));
        mob.setFireTicks(NbtOps.getShort(nbt, "Fire"));
        mob.setAir(NbtOps.getShort(nbt, "Air"));
        mob.setPortalCooldown(NbtOps.getInt(nbt, "PortalCooldown"));
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
        NbtOps.putInt(nbt, "HurtByTimestamp", mob.hurtByTimestamp());
        NbtOps.putShort(nbt, "DeathTime", (short) mob.deathTime());
        NbtOps.putFloat(nbt, "AbsorptionAmount", mob.absorption());
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
        NbtOps.putBoolean(nbt, "CanPickUpLoot", mob.canPickUpLoot());
        NbtOps.putBoolean(nbt, "PersistenceRequired", mob.rawPersistent());
        List<Snbt.Tag> equipment = new ArrayList<>();
        // [0] the held slot; [1..4] the armor family (VIII4 — the
        // reference's five-entry Equipment list, writeNbt lines 255-262:
        // every slot writes its compound, an empty slot the air row).
        equipment.add(Snbt.Tag.ofCompound(
                ItemNbt.slotRows(0, mob.heldEquipment())));
        for (int slot = 1; slot <= 4; slot++) {
            equipment.add(Snbt.Tag.ofCompound(
                    ItemNbt.slotRows(slot, mob.equipment(slot))));
        }
        NbtOps.putList(nbt, "Equipment", equipment);
        float[] chances = mob.inventoryDropChances();
        NbtOps.putList(nbt, "DropChances",
                NbtOps.floatList(chances[0], chances[1], chances[2], chances[3], chances[4]));
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
        writeTypeCustomNbt(mob, nbt);
    }

    /**
     * The per-type custom rows (Slice 89 p3 — the reference's per-type
     * writeCustomNbt/readCustomNbt over the engine's own state): the
     * sheep's Sheared/Color pair, the pig's Saddle, the horse's
     * Type/Variant/Temper/Tame/ArmorItem/SaddleItem family, the villager's
     * Profession, the wolf's Tame/Sitting/Angry/CollarColor family, the
     * zombie's IsVillager/IsBaby/ConversionTime triple.
     *
     * <p><b>The engine's standing envelopes</b> (written with the
     * reference's own defaults, read into no model — ledgered): the
     * horse's OwnerUUID (the engine's tamer is a name, not a uuid),
     * EatingHaystack/ChestedHorse/HasReproduced/Bred (the engine models
     * none as persistent state), the wolf's OwnerUUID (same as the
     * horse's), the creeper's powered/ExplosionRadius/ignited LANDED into the
     * model (Slice 91 Wave C p2 — the charged flag doubles the blast, the
     * permanent ignite never cools, the Fuse/ExplosionRadius rows steer
     * the swell and the blast), the skeleton's
     * SkeletonType and the ocelot's CatType LANDED too (Slice 91 Wave C
     * p4 — the wither-variant byte and the coat row over the engine's own
     * fields).</p>
     */
    private static void writeTypeCustomNbt(MobEntity mob, Map<String, Snbt.Tag> nbt) {
        switch (mob.type()) {
            case SHEEP -> {
                NbtOps.putBoolean(nbt, "Sheared", mob.sheared());
                NbtOps.putByte(nbt, "Color", (byte) mob.sheepFleeceColor());
            }
            case PIG -> NbtOps.putBoolean(nbt, "Saddle", mob.pigSaddled());
            case HORSE -> {
                NbtOps.putBoolean(nbt, "EatingHaystack", mob.eating());
                NbtOps.putBoolean(nbt, "ChestedHorse", false);
                NbtOps.putBoolean(nbt, "HasReproduced", mob.horseHasReproduced());
                NbtOps.putBoolean(nbt, "Bred", mob.horseBred());
                NbtOps.putInt(nbt, "Type", mob.horseSubtype());
                NbtOps.putInt(nbt, "Variant", mob.variant());
                NbtOps.putInt(nbt, "Temper", mob.temper());
                NbtOps.putBoolean(nbt, "Tame", mob.tamed());
                NbtOps.putString(nbt, "OwnerUUID", mob.ownerUuid());
                // The armor/saddle as the reference's own ITEM rows: the
                // engine's armorType int and saddled flag through the item
                // forms (iron/gold/diamond_horse_armor, the saddle).
                if (mob.armorType() > 0) {
                    Map<String, Snbt.Tag> armor = new java.util.LinkedHashMap<>();
                    ItemNbt.write(net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                    net.zaminmc.torch.util.Identifier.parse(
                                            switch (mob.armorType()) {
                                                case 1 -> "minecraft:iron_horse_armor";
                                                case 2 -> "minecraft:golden_horse_armor";
                                                default -> "minecraft:diamond_horse_armor";
                                            })).orElseThrow(), 1), armor);
                    NbtOps.putCompound(nbt, "ArmorItem", armor);
                }
                if (mob.saddled()) {
                    Map<String, Snbt.Tag> saddle = new java.util.LinkedHashMap<>();
                    ItemNbt.write(net.zaminmc.torch.item.ItemStack.of(
                            net.zaminmc.torch.server.item.BuiltinItems.lookup(
                                    net.zaminmc.torch.util.Identifier.parse("minecraft:saddle"))
                                    .orElseThrow(), 1), saddle);
                    NbtOps.putCompound(nbt, "SaddleItem", saddle);
                }
            }
            case VILLAGER -> NbtOps.putInt(nbt, "Profession", mob.profession());
            case WOLF -> {
                // The reference's wolf write (WolfEntity 122-126 +
                // TameableEntity 30-38): OwnerUUID + Sitting + Angry +
                // CollarColor — NO Tame row (the tamed state derives from
                // the owner row at load: a non-empty OwnerUUID tames).
                NbtOps.putString(nbt, "OwnerUUID", mob.ownerUuid());
                NbtOps.putBoolean(nbt, "Sitting", mob.wolfSitting());
                NbtOps.putBoolean(nbt, "Angry", mob.wolfAngry());
                NbtOps.putByte(nbt, "CollarColor", (byte) mob.wolfCollarColor());
            }
            case ZOMBIE -> {
                // The reference's own gates: only a villager skin or a
                // baby writes its row; the conversion time only when
                // converting.
                if (mob.zombieVillager()) {
                    NbtOps.putBoolean(nbt, "IsVillager", true);
                }
                if (mob.isBaby()) {
                    NbtOps.putBoolean(nbt, "IsBaby", true);
                }
                if (mob.zombieConverting()) {
                    NbtOps.putInt(nbt, "ConversionTime", mob.zombieConversionTicks());
                } else {
                    NbtOps.putInt(nbt, "ConversionTime", -1);
                }
                NbtOps.putBoolean(nbt, "CanBreakDoors", false);
            }
            case CREEPER -> {
                // writeCustomNbt lines 73-82: powered only when charged
                // (the syncedData-17 gate), Fuse + ExplosionRadius always,
                // ignited the flag's own value.
                if (mob.creeperCharged()) {
                    NbtOps.putBoolean(nbt, "powered", true);
                }
                NbtOps.putShort(nbt, "Fuse", (short) mob.creeperFuseTime());
                NbtOps.putByte(nbt, "ExplosionRadius", (byte) mob.creeperExplosionRadius());
                NbtOps.putBoolean(nbt, "ignited", mob.creeperIgnited());
            }
            case SKELETON -> NbtOps.putByte(nbt, "SkeletonType",
                    (byte) (mob.skeletonWither() ? 1 : 0));
            case OCELOT -> {
                // The reference's ocelot (OcelotEntity + TameableEntity
                // 30-38): the owner pair + the sitting flag + the coat.
                NbtOps.putString(nbt, "OwnerUUID", mob.ownerUuid());
                NbtOps.putBoolean(nbt, "Sitting", mob.wolfSitting());
                NbtOps.putInt(nbt, "CatType", mob.catVariant());
            }
            default -> { }
        }
    }

    /** The per-type read (the mirror of {@link #writeTypeCustomNbt}). */
    private static void readTypeCustomNbt(MobEntity mob, Map<String, Snbt.Tag> nbt) {
        switch (mob.type()) {
            case SHEEP -> {
                mob.setSheared(NbtOps.getBoolean(nbt, "Sheared"));
                mob.setSheepFleeceColor(NbtOps.getByte(nbt, "Color"));
            }
            case PIG -> mob.setSaddled(NbtOps.getBoolean(nbt, "Saddle"));
            case HORSE -> {
                // The reference's own gates: each row reads only when
                // present-and-typed (the ungated setters keep the
                // engine's state where the row is absent).
                if (NbtOps.contains(nbt, "Type", Snbt.Tag.Type.INT)) {
                    mob.setHorseSubtype(NbtOps.getInt(nbt, "Type"));
                }
                if (NbtOps.contains(nbt, "Variant", Snbt.Tag.Type.INT)) {
                    mob.setHorseVariant(NbtOps.getInt(nbt, "Variant"));
                }
                if (NbtOps.contains(nbt, "Temper", Snbt.Tag.Type.INT)) {
                    mob.setHorseTemper(NbtOps.getInt(nbt, "Temper"));
                }
                mob.setTamedFlag(NbtOps.getBoolean(nbt, "Tame"));
                // The breeding rows (HorseBaseEntity.readCustomNbt lines
                // 1133-1137): the graze flash re-arms when the row says
                // so; ChestedHorse parses to nowhere (the engine's
                // no-donkey/mule envelope — no chested kinds exist);
                // the bred pair lands on the markers (the breeding-gate
                // consults are the engine's no-horse-breeding envelope).
                if (NbtOps.getBoolean(nbt, "EatingHaystack")) {
                    mob.flashEating();
                }
                mob.setHorseBred(NbtOps.getBoolean(nbt, "Bred"));
                mob.setHorseHasReproduced(NbtOps.getBoolean(nbt, "HasReproduced"));
                // The owner walk (lines 1142-1153): the STRING-gated
                // OwnerUUID row (else the legacy Owner name), a non-empty
                // read lands the owner — the horse does NOT auto-tame
                // (the Tame row carries that).
                readOwnerRows(mob, nbt);
                mob.setSaddled(NbtOps.contains(nbt, "SaddleItem", Snbt.Tag.Type.COMPOUND));
                net.zaminmc.torch.item.ItemStack armor =
                        ItemNbt.read(NbtOps.getCompound(nbt, "ArmorItem"));
                if (armor != null) {
                    String id = armor.type().identifier().value();
                    mob.applyArmor(switch (id) {
                        case "iron_horse_armor" -> 1;
                        case "golden_horse_armor" -> 2;
                        case "diamond_horse_armor" -> 3;
                        default -> 0;
                    });
                }
            }
            case VILLAGER -> mob.setVillagerProfession(NbtOps.getInt(nbt, "Profession"));
            case WOLF -> {
                // The TameableEntity read (lines 43-58): a non-empty owner
                // row TAMES the body — the reference's own load gate; the
                // engine's Tame parse rides beside it (the engine's own
                // belt: a tamed-owner-less edge the reference cannot make).
                readOwnerRows(mob, nbt);
                if (!mob.ownerUuid().isEmpty() || !mob.ownerName().isEmpty()) {
                    mob.setTamedFlag(true);
                }
                // The engine's own belt (a Tame row the reference's wolf
                // never writes): a PRESENT row applies — an absent row
                // never clobbers the owner walk's own verdict.
                if (NbtOps.contains(nbt, "Tame", Snbt.Tag.Type.BYTE)) {
                    mob.setTamedFlag(NbtOps.getBoolean(nbt, "Tame"));
                }
                mob.setWolfSitting(NbtOps.getBoolean(nbt, "Sitting"));
                mob.setWolfAngry(NbtOps.getBoolean(nbt, "Angry"));
                mob.setWolfCollarColor(NbtOps.getByte(nbt, "CollarColor"));
            }
            case ZOMBIE -> {
                // The reference's own gates: only a TRUE row sets.
                if (NbtOps.getBoolean(nbt, "IsBaby")) {
                    mob.setBreedingAge(-24000);
                }
                if (NbtOps.getBoolean(nbt, "IsVillager")) {
                    mob.setZombieVillager(true);
                }
                // The numeric gate + the reference's own > -1 filter: only
                // a positive-or-zero time arms — a -1 row (the write's own
                // "not converting" marker) is ignored, the countdown keeps
                // whatever it held.
                if (NbtOps.containsNumeric(nbt, "ConversionTime")
                        && NbtOps.getInt(nbt, "ConversionTime") > -1) {
                    mob.setZombieConversionTime(NbtOps.getInt(nbt, "ConversionTime"));
                }
                // CanBreakDoors: the engine models no door-breaking (ledgered).
            }
            case CREEPER -> {
                // readCustomNbt lines 85-98: powered reads the boolean
                // (absent = false), Fuse + ExplosionRadius ride the
                // contains(key,99) numeric gates, ignited only SETS (the
                // reference's own one-way arm — a false row never un-primes).
                mob.setCreeperCharged(NbtOps.getBoolean(nbt, "powered"));
                if (NbtOps.containsNumeric(nbt, "Fuse")) {
                    mob.setCreeperFuseTime(NbtOps.getShort(nbt, "Fuse"));
                }
                if (NbtOps.containsNumeric(nbt, "ExplosionRadius")) {
                    mob.setCreeperExplosionRadius(NbtOps.getByte(nbt, "ExplosionRadius"));
                }
                if (NbtOps.getBoolean(nbt, "ignited")) {
                    mob.setCreeperIgnited(true);
                }
            }
            case SKELETON -> {
                // readCustomNbt lines 288-295: the numeric contains gate —
                // an absent row keeps the variant; 1 arms the wither hull
                // (the melee mind, the fire immunity, the taller body).
                if (NbtOps.containsNumeric(nbt, "SkeletonType")) {
                    mob.setSkeletonWither(NbtOps.getByte(nbt, "SkeletonType") != 0);
                }
            }
            case OCELOT -> {
                // The reference's ocelot (TameableEntity 43-58 + the
                // ungated CatType): the owner walk tames, the coat resets
                // on an absent row.
                readOwnerRows(mob, nbt);
                if (!mob.ownerUuid().isEmpty() || !mob.ownerName().isEmpty()) {
                    mob.setTamedFlag(true);
                }
                mob.setWolfSitting(NbtOps.getBoolean(nbt, "Sitting"));
                mob.setCatVariant(NbtOps.getInt(nbt, "CatType"));
            }
            default -> { }
        }
    }

    /**
     * The tameable family's owner walk (TameableEntity.readCustomNbt lines
     * 44-56 / HorseBaseEntity lines 1142-1153): the STRING-gated
     * OwnerUUID row, else the legacy Owner NAME row (the pre-1.8 shape —
     * the engine reads it straight, the UserConverter's own conversion
     * arm); a non-empty read lands the uuid on the model and the legacy
     * name on the name slot.
     */
    private static void readOwnerRows(MobEntity mob, Map<String, Snbt.Tag> nbt) {
        String owner;
        if (NbtOps.contains(nbt, "OwnerUUID", Snbt.Tag.Type.STRING)) {
            owner = NbtOps.getString(nbt, "OwnerUUID");
        } else {
            owner = NbtOps.getString(nbt, "Owner");
        }
        if (owner != null && !owner.isEmpty()) {
            // The reference stores the one string in the owner slot; the
            // engine splits: a uuid-shaped string lands the uuid slot, any
            // other lands the name (the legacy row's own shape).
            if (owner.length() == 36 && owner.charAt(8) == '-'
                    && owner.charAt(13) == '-' && owner.charAt(18) == '-'
                    && owner.charAt(23) == '-') {
                mob.setOwnerUuid(owner);
            } else {
                mob.setCustomOwnerName(owner);
            }
        }
    }

    /** The test bridge over the per-type read (the row-level unit rows). */
    static void readTypeCustomNbtForTest(MobEntity mob, Map<String, Snbt.Tag> nbt) {
        readTypeCustomNbt(mob, nbt);
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
        // LivingEntity.readCustomNbt (line 418 — the FIRST row the walk
        // reads): the absorption hearts install ungated; the
        // ActiveEffects rows below ride put (no modifier walk) — the
        // hearts come from the ROW, not the effect's re-apply.
        mob.setAbsorption(NbtOps.getFloat(nbt, "AbsorptionAmount"));
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
        mob.setHurtByTimestamp(NbtOps.getInt(nbt, "HurtByTimestamp"));
        // MobEntity.readCustomNbt — the pickup flag's own BYTE gate
        // (contains("CanPickUpLoot", 1)): an absent row keeps the spawn
        // roll's verdict.
        if (NbtOps.contains(nbt, "CanPickUpLoot", Snbt.Tag.Type.BYTE)) {
            mob.setCanPickUpLoot(NbtOps.getBoolean(nbt, "CanPickUpLoot"));
        }
        if (mob.type() == net.zaminmc.torch.server.entity.MobType.VILLAGER) {
            // VillagerEntity.readAdditional line 464: the belt-and-suspenders
            // force-true AFTER the row read — a false row never disarms the
            // villager (the constructor's own arm stands).
            mob.setCanPickUpLoot(true);
        }
        mob.setPersistent(NbtOps.getBoolean(nbt, "PersistenceRequired"));
        List<Snbt.Tag> equipment = NbtOps.getList(nbt, "Equipment", Snbt.Tag.Type.COMPOUND);
        if (!equipment.isEmpty()) {
            // MobEntity.readCustomNbt lines 301-307: the list's own index
            // IS the slot — the reference reads five, an empty row SETTING
            // its slot back to empty (the strip over the spawn dice's own
            // roll rides the same arm); a short list leaves the unlisted
            // slots untouched. The wire's re-publish wake is the caller's
            // (Slice 78b's convention) — it fires when any row landed or
            // stripped, never for a null-over-null no-op.
            boolean equipmentChangedNow = false;
            for (int slot = 0; slot <= 4 && slot < equipment.size(); slot++) {
                net.zaminmc.torch.item.ItemStack stack =
                        ItemNbt.read(NbtOps.listCompound(equipment, slot));
                if (stack == null && mob.equipment(slot) == null) {
                    continue; // the empty row over an empty slot: nothing lands
                }
                mob.setEquipment(slot, stack);
                equipmentChangedNow = true;
            }
            if (equipmentChangedNow && equipmentChanged != null) {
                equipmentChanged.run();
            }
        }
        // DropChances (contains("DropChances", 9) + getList(key, 5)):
        // the per-slot assignment bounded by the row's own list size.
        List<Snbt.Tag> dropChances = NbtOps.getList(nbt, "DropChances", Snbt.Tag.Type.FLOAT);
        if (!dropChances.isEmpty()) {
            float[] read = new float[dropChances.size()];
            for (int i = 0; i < dropChances.size(); i++) {
                read[i] = (float) dropChances.get(i).asDouble();
            }
            mob.setInventoryDropChances(read);
        }
        mob.applyLeashRows(NbtOps.getBoolean(nbt, "Leashed"), nbt);
        mob.setNoAi(NbtOps.getBoolean(nbt, "NoAI"));
        readTypeCustomNbt(mob, nbt);
    }
}
