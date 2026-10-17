package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The player's NBT projection (Slice 92 — the reference's
 * {@code Entity.writeNbtWithoutId} + {@code LivingEntity.writeCustomNbt}
 * + {@code PlayerEntity.writeCustomNbt} (lines 740-765) +
 * {@code HungerManager.writeNbt} (lines 71-76) +
 * {@code PlayerAbilities.writeNbt} (lines 14-24) +
 * {@code ServerPlayerEntity}'s {@code playerGameType} over the engine's
 * player model): the /testfor dataTag's subset match reads these rows.
 *
 * <p><b>The write-only surface</b>: the engine's own player persistence
 * rides the ZPD store (its own format); this projection exists for the
 * command surface — a {@code /testfor <player> {XpLevel:30}} match reads
 * the same rows the reference's matcher walks.</p>
 *
 * <p><b>The engine's standing envelopes</b> (written with the reference's
 * own defaults, no model behind them): FallDistance 0, HurtTime/HurtTime
 * stamps 0, AbsorptionAmount 0 (the player's absorption model), the XpP
 * within-level progress 0 (the engine models the level and the total),
 * the Score 0 (the death-score model), foodTickTimer 0, Sleeping false,
 * SleepTimer 0, SpawnForced false, the abilities' invulnerable false.</p>
 */
public final class PlayerNbt {

    private PlayerNbt() {
    }

    /**
     * The reference's writeNbtWithoutId over the player: the base rows,
     * the living rows, the player's own rows, the hunger pair, the
     * abilities compound, and the server's game-type row.
     */
    public static void writeNbtWithoutId(PlayerSession player,
            Map<String, Snbt.Tag> nbt,
            PotionSystem potions) {
        Position at = player.position();
        // The base rows (Entity.writeNbtWithoutId lines 1113-1141).
        NbtOps.putList(nbt, "Pos", NbtOps.doubleList(at.x(), at.y(), at.z()));
        NbtOps.putList(nbt, "Motion",
                NbtOps.doubleList(player.motionX(), player.motionY(), player.motionZ()));
        NbtOps.putList(nbt, "Rotation",
                NbtOps.floatList(player.rotation().yaw(), player.rotation().pitch()));
        NbtOps.putFloat(nbt, "FallDistance", 0.0f);
        NbtOps.putShort(nbt, "Fire", (short) player.fireTicks());
        NbtOps.putShort(nbt, "Air", (short) player.airTicks());
        NbtOps.putBoolean(nbt, "OnGround", player.onGround());
        NbtOps.putInt(nbt, "Dimension", player.dimension());
        NbtOps.putBoolean(nbt, "Invulnerable", false);
        NbtOps.putInt(nbt, "PortalCooldown", player.portalCooldown());
        UUID uuid = player.uuid();
        NbtOps.putLong(nbt, "UUIDMost", uuid.getMostSignificantBits());
        NbtOps.putLong(nbt, "UUIDLeast", uuid.getLeastSignificantBits());
        player.commandResults().writeNbt(nbt);
        // The living rows (LivingEntity.writeCustomNbt lines 382-405) —
        // the engine's player model carries the health and the effects;
        // the stamps stay the reference's defaults (the envelope).
        NbtOps.putFloat(nbt, "HealF", player.health());
        NbtOps.putShort(nbt, "Health", (short) Math.ceil(player.health()));
        NbtOps.putShort(nbt, "HurtTime", (short) 0);
        NbtOps.putInt(nbt, "HurtByTimestamp", 0);
        NbtOps.putShort(nbt, "DeathTime", (short) 0);
        NbtOps.putFloat(nbt, "AbsorptionAmount", 0.0f);
        Map<Integer, StatusEffectInstance> effects = potions.allOf(player);
        if (!effects.isEmpty()) {
            List<Snbt.Tag> activeEffects = new ArrayList<>();
            for (StatusEffectInstance effect : effects.values()) {
                activeEffects.add(effectRows(effect));
            }
            NbtOps.putList(nbt, "ActiveEffects", activeEffects);
        }
        // The player's own rows (PlayerEntity.writeCustomNbt 740-765).
        List<Snbt.Tag> inventory = new ArrayList<>();
        ItemStack[] slots = player.inventory().slots();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null && !slots[i].isEmpty()) {
                inventory.add(Snbt.Tag.ofCompound(ItemNbt.slotRows(i, slots[i])));
            }
        }
        ItemStack[] armor = player.inventory().armorStacks();
        for (int i = 0; i < armor.length; i++) {
            if (armor[i] != null && !armor[i].isEmpty()) {
                // The reference's armor band (Inventory.writeNbt): the
                // armor slots ride 100-103.
                inventory.add(Snbt.Tag.ofCompound(ItemNbt.slotRows(100 + i, armor[i])));
            }
        }
        NbtOps.putList(nbt, "Inventory", inventory);
        NbtOps.putInt(nbt, "SelectedItemSlot", player.heldSlot());
        NbtOps.putBoolean(nbt, "Sleeping", false);
        NbtOps.putShort(nbt, "SleepTimer", (short) 0);
        NbtOps.putFloat(nbt, "XpP", 0.0f);
        NbtOps.putInt(nbt, "XpLevel", player.experienceLevel());
        NbtOps.putInt(nbt, "XpTotal", (int) Math.min(Integer.MAX_VALUE, player.totalXp()));
        NbtOps.putInt(nbt, "XpSeed", player.enchantingSeed());
        NbtOps.putInt(nbt, "Score", 0);
        Position spawn = player.bedSpawn();
        if (spawn != null) {
            NbtOps.putInt(nbt, "SpawnX", (int) Math.floor(spawn.x()));
            NbtOps.putInt(nbt, "SpawnY", (int) Math.floor(spawn.y()));
            NbtOps.putInt(nbt, "SpawnZ", (int) Math.floor(spawn.z()));
            NbtOps.putBoolean(nbt, "SpawnForced", false);
        }
        // The hunger rows (HungerManager.writeNbt 71-76).
        NbtOps.putInt(nbt, "foodLevel", player.food());
        NbtOps.putInt(nbt, "foodTickTimer", 0);
        NbtOps.putFloat(nbt, "foodSaturationLevel", player.saturation());
        NbtOps.putFloat(nbt, "foodExhaustionLevel", player.exhaustion());
        // The abilities compound (PlayerAbilities.writeNbt 14-24).
        Map<String, Snbt.Tag> abilities = new java.util.LinkedHashMap<>();
        NbtOps.putBoolean(abilities, "invulnerable", false);
        NbtOps.putBoolean(abilities, "flying", player.flying());
        NbtOps.putBoolean(abilities, "mayfly", player.allowedToFly());
        NbtOps.putBoolean(abilities, "instabuild",
                player.gamemode() == net.zaminmc.torch.GameMode.CREATIVE);
        NbtOps.putBoolean(abilities, "mayBuild",
                player.gamemode() != net.zaminmc.torch.GameMode.ADVENTURE
                        && player.gamemode() != net.zaminmc.torch.GameMode.SPECTATOR);
        NbtOps.putFloat(abilities, "flySpeed", 0.05f);
        NbtOps.putFloat(abilities, "walkSpeed", 0.1f);
        NbtOps.putCompound(nbt, "abilities", abilities);
        // The ender chest rows (the EnderItems list).
        List<Snbt.Tag> ender = new ArrayList<>();
        ItemStack[] enderSlots = player.enderChest().snapshotSlots();
        for (int i = 0; i < enderSlots.length; i++) {
            if (enderSlots[i] != null && !enderSlots[i].isEmpty()) {
                ender.add(Snbt.Tag.ofCompound(ItemNbt.slotRows(i, enderSlots[i])));
            }
        }
        NbtOps.putList(nbt, "EnderItems", ender);
        // The held stack (the SelectedItem row).
        ItemStack held = player.inventory().held();
        if (held != null && !held.isEmpty()) {
            Map<String, Snbt.Tag> heldRows = new java.util.LinkedHashMap<>();
            ItemNbt.write(held, heldRows);
            NbtOps.putCompound(nbt, "SelectedItem", heldRows);
        }
        // The server's own row (ServerPlayerEntity's playerGameType).
        NbtOps.putInt(nbt, "playerGameType", player.gamemode().legacyId());
    }

    private static Snbt.Tag effectRows(StatusEffectInstance effect) {
        Map<String, Snbt.Tag> rows = new java.util.LinkedHashMap<>();
        NbtOps.putByte(rows, "Id", (byte) effect.id());
        NbtOps.putByte(rows, "Amplifier", (byte) effect.amplifier());
        NbtOps.putInt(rows, "Duration", effect.duration());
        NbtOps.putBoolean(rows, "Ambient", effect.isAmbient());
        NbtOps.putBoolean(rows, "ShowParticles", effect.hasParticles());
        return Snbt.Tag.ofCompound(rows);
    }
}
