package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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
 * The mob armor family (VIII4 — the §29 Wave VIII row): the four armor
 * slots' model + the damage walk's armor consult (the reference's
 * getArmorProtection/getDamageAfterArmor — the per-piece sum, 4% per
 * point, the 20-point/80% cap), the zombie/skeleton spawn dice
 * (addRandomEquipment's difficulty-banded walk at a seeded random), the
 * five-slot drop-chance walk (the 0.085 default, the 2.0 picked-up
 * marker), the vanilla Equipment NBT rows (the /summon + anvil import
 * walk), and the ZVD store's v13 armor tail.
 */
class MobArmorFamilyTest {

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

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    private record Rig(MobManager manager, Loot loot) {
    }

    private Rig rig(long seed) {
        Loot loot = new Loot();
        MobManager manager = new MobManager(new StubWorld(), new Random(seed), loot,
                1_100_000, (x, z) -> 4);
        return new Rig(manager, loot);
    }

    private static MobEntity zombie() {
        return new MobEntity(7, MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5), new Random(11), new StubWorld());
    }

    // ------------------------------------------------ the damage consult

    /**
     * The reduction band (LivingEntity.getDamageAfterArmor lines 868-877):
     * {@code damage * (25 - points) / 25.0F} — a bare body takes it whole,
     * the full diamond set (20 points) answers 20% through (the 80% cap),
     * leather (7 points) 72%, and the reference's own quirk that a helmet
     * HELD in the hand contributes its points (the sum walks all five
     * slots, the instanceof ArmorItem gate alone).
     */
    @Test
    void theDamageWalkReadsTheEquippedArmorPoints() {
        MobEntity bare = zombie();
        bare.hurt(10.0f);
        assertEquals(10.0f, bare.health(), 1e-6,
                "a bare body takes the whole wound");

        MobEntity diamond = zombie();
        diamond.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        diamond.setEquipment(3, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        diamond.setEquipment(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1));
        diamond.setEquipment(1, ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
        assertEquals(20, diamond.armorProtectionPoints(),
                "the diamond set sums the reference's own 3+8+6+3");
        diamond.hurt(10.0f);
        assertEquals(20.0f - 10.0f * 5 / 25.0f, diamond.health(), 1e-6,
                "20 points cap the absorption at 80% (2.0 of the 10.0 lands)");

        MobEntity leather = zombie();
        leather.setEquipment(4, ItemStack.of(BuiltinItems.LEATHER_HELMET, 1));
        leather.setEquipment(3, ItemStack.of(BuiltinItems.LEATHER_CHESTPLATE, 1));
        leather.setEquipment(2, ItemStack.of(BuiltinItems.LEATHER_LEGGINGS, 1));
        leather.setEquipment(1, ItemStack.of(BuiltinItems.LEATHER_BOOTS, 1));
        assertEquals(7, leather.armorProtectionPoints(), "leather sums 1+3+2+1");
        leather.hurt(10.0f);
        assertEquals(20.0f - 10.0f * 18 / 25.0f, leather.health(), 1e-6,
                "each point absorbs its own 4% (7.2 of the 10.0 lands)");

        MobEntity heldHelmet = zombie();
        heldHelmet.setHeldEquipment(ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        assertEquals(3, heldHelmet.armorProtectionPoints(),
                "the reference's own quirk: a helmet in the HAND contributes");
        heldHelmet.hurt(10.0f);
        assertEquals(20.0f - 10.0f * 22 / 25.0f, heldHelmet.health(), 1e-6,
                "the held helmet's 3 points ride the envelope");
    }

    /**
     * The pumpkin answers no points (getArmorProtection's instanceof
     * ArmorItem gate — the head slot's costume contributes nothing).
     */
    @Test
    void thePumpkinOnTheHeadContributesNoPoints() {
        MobEntity costumed = zombie();
        costumed.setEquipment(4, ItemStack.of(BuiltinItems.PUMPKIN, 1));
        assertEquals(0, costumed.armorProtectionPoints(),
                "the pumpkin is no ArmorItem in the reference either");
        costumed.hurt(10.0f);
        assertEquals(10.0f, costumed.health(), 1e-6,
                "the costume body takes the whole wound");
    }

    /**
     * The bypass family (DamageSource's setBypassesArmor set — ON_FIRE,
     * DROWN, the void, magic): the burn clock, the drown wound and the
     * potion arm never consult the pieces.
     */
    @Test
    void theBypassingFamilySkipsTheConsult() {
        MobEntity burning = zombie();
        burning.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        burning.setEquipment(3, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        burning.setEquipment(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1));
        burning.setEquipment(1, ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
        burning.hurt(10.0f, true); // ON_FIRE / DROWN / void / magic — the bypass arm
        assertEquals(10.0f, burning.health(), 1e-6,
                "the bypassing family lands the wound whole through full diamond");
    }

    // ------------------------------------------------ the spawn dice

    /**
     * The dice at a seeded random (MobEntity.addRandomEquipment lines
     * 593-623 + ZombieEntity's own arm, lines 306-316): the test keeps its
     * OWN verbatim transcription of the reference's walk — the gate roll,
     * the nextInt(2) level, the three 0.095 bumps, the head-to-feet stop
     * rolls (HARD's own 0.1 against the 0.25), the level's material
     * column, the zombie's 1%-or-5% iron tool tail — and the engine's
     * roll must land exactly the same pieces off the same seed.
     */
    @Test
    void theSpawnDiceMatchTheReferenceWalkAtASeededRandom() {
        long[] seeds = {20261021L, 42L, 777L, 123456789L};
        for (long seed : seeds) {
            for (int difficultyId : new int[] {2, 3}) {
                // The engine's roll.
                MobEntity mob = zombie();
                MobManager.rollSpawnEquipment(mob, new Random(seed), 1.0f, difficultyId);
                // The reference's walk, transcribed verbatim.
                Random ref = new Random(seed);
                ItemStack[] expected = new ItemStack[5];
                if (ref.nextFloat() < 0.15F * 1.0F) {
                    int level = ref.nextInt(2);
                    float stop = difficultyId == 3 ? 0.1F : 0.25F;
                    if (ref.nextFloat() < 0.095F) {
                        level++;
                    }
                    if (ref.nextFloat() < 0.095F) {
                        level++;
                    }
                    if (ref.nextFloat() < 0.095F) {
                        level++;
                    }
                    for (int j = 3; j >= 0; j--) {
                        if (j < 3 && ref.nextFloat() < stop) {
                            break;
                        }
                        if (expected[j + 1] == null) {
                            var piece = referencePieceForSlot(j + 1, level);
                            if (piece != null) {
                                expected[j + 1] = ItemStack.of(piece, 1);
                            }
                        }
                    }
                }
                ItemStack expectedHeld = null;
                if (ref.nextFloat() < (difficultyId == 3 ? 0.05F : 0.01F)) {
                    expectedHeld = ItemStack.of(ref.nextInt(3) == 0
                            ? BuiltinItems.IRON_SWORD : BuiltinItems.IRON_SHOVEL, 1);
                }
                for (int slot = 1; slot <= 4; slot++) {
                    assertEquals(expected[slot] == null ? null : expected[slot].type(),
                            mob.equipment(slot) == null ? null : mob.equipment(slot).type(),
                            "slot " + slot + " at seed " + seed + " difficulty " + difficultyId);
                }
                assertEquals(expectedHeld == null ? null : expectedHeld.type(),
                        mob.heldEquipment() == null ? null : mob.heldEquipment().type(),
                        "the held tool arm at seed " + seed + " difficulty " + difficultyId);
            }
        }
    }

    /** The reference's getEquipmentForSlot ladder, verbatim (lines 646-698). */
    private static net.zaminmc.torch.item.ItemType referencePieceForSlot(int slot, int level) {
        return switch (slot) {
            case 4 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_HELMET;
                case 1 -> BuiltinItems.GOLDEN_HELMET;
                case 2 -> BuiltinItems.CHAINMAIL_HELMET;
                case 3 -> BuiltinItems.IRON_HELMET;
                case 4 -> BuiltinItems.DIAMOND_HELMET;
                default -> null;
            };
            case 3 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_CHESTPLATE;
                case 1 -> BuiltinItems.GOLDEN_CHESTPLATE;
                case 2 -> BuiltinItems.CHAINMAIL_CHESTPLATE;
                case 3 -> BuiltinItems.IRON_CHESTPLATE;
                case 4 -> BuiltinItems.DIAMOND_CHESTPLATE;
                default -> null;
            };
            case 2 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_LEGGINGS;
                case 1 -> BuiltinItems.GOLDEN_LEGGINGS;
                case 2 -> BuiltinItems.CHAINMAIL_LEGGINGS;
                case 3 -> BuiltinItems.IRON_LEGGINGS;
                case 4 -> BuiltinItems.DIAMOND_LEGGINGS;
                default -> null;
            };
            case 1 -> switch (level) {
                case 0 -> BuiltinItems.LEATHER_BOOTS;
                case 1 -> BuiltinItems.GOLDEN_BOOTS;
                case 2 -> BuiltinItems.CHAINMAIL_BOOTS;
                case 3 -> BuiltinItems.IRON_BOOTS;
                case 4 -> BuiltinItems.DIAMOND_BOOTS;
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * The zero-multiplier gate (a fresh or easy world — LocalDifficulty's
     * own clamp below 2.0 regional answers 0): the ARMOR dice never roll
     * at all. (The zombie's iron tool arm rolls on regardless — its own
     * band, the seeded shadow above pins it.)
     */
    @Test
    void theSpawnDiceNeverRollAtZeroMultiplier() {
        for (int seed = 1; seed <= 100; seed++) {
            MobEntity mob = zombie();
            MobManager.rollSpawnEquipment(mob, new Random(seed), 0.0f, 2);
            for (int slot = 1; slot <= 4; slot++) {
                assertNull(mob.equipment(slot),
                        "the gate 0.15 * 0 never opens (seed " + seed + ")");
            }
        }
    }

    /**
     * The spawn-site wiring: at a HARD world past the day-clamp's ceiling
     * (the engine's zeroed inhabited/moon terms cap the multiplier at 0.5)
     * the zombie family rolls the dice and the pigman family never does
     * (ZombiePigmanEntity.addRandomEquipment replaces the walk whole).
     */
    @Test
    void theSpawnSiteRollsTheDiceForTheZombieFamilyAlone() {
        Rig rig = rig(0xC0FFEE);
        rig.manager().setDifficultyRead(() -> 3);
        // Time past 1_512_000: the time term at its own 0.25 ceiling, the
        // HARD regional 3 * (0.75 + 0.25) = 3.0, the multiplier (3-2)/2 = 0.5.
        rig.manager().tick(List.of(), 1_600_000);
        int zombiesWithArmor = 0;
        for (int i = 0; i < 120; i++) {
            MobEntity zombie = rig.manager().spawnAt(
                    MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
            boolean any = false;
            for (int slot = 1; slot <= 4; slot++) {
                if (zombie.equipment(slot) != null) {
                    any = true;
                }
            }
            if (any) {
                zombiesWithArmor++;
            }
        }
        assertTrue(zombiesWithArmor > 0,
                "the HARD site rolls armor (the 0.15*0.5 gate, 120 bodies): "
                        + zombiesWithArmor);
        for (int i = 0; i < 120; i++) {
            MobEntity pigman = rig.manager().spawnAt(
                    MobType.ZOMBIE_PIGMAN, new Position(0.5, 4.0, 0.5));
            for (int slot = 1; slot <= 4; slot++) {
                assertNull(pigman.equipment(slot),
                        "the pigman's override never calls the armor walk");
            }
        }
    }

    /**
     * The nether's wither arm (SkeletonEntity.initialize's if/else, lines
     * 217-226): the variant body takes the stone sword and NEVER the dice
     * — the armor the shared spawn walk rolled strips back off 1:1 — while
     * the plain archer keeps its own roll.
     */
    @Test
    void theNetherWitherArmStripsTheDiceArmor() {
        Rig rig = rig(0xBEEF);
        rig.manager().setDifficultyRead(() -> 3);
        rig.manager().tick(List.of(), 1_600_000);
        int withers = 0;
        int archers = 0;
        for (int i = 0; i < 60; i++) {
            MobEntity skeleton = rig.manager().spawnNaturalNether(
                    MobType.SKELETON, new Position(0.5, 4.0, 0.5), false, 0.0f);
            if (skeleton.skeletonWither()) {
                withers++;
                for (int slot = 1; slot <= 4; slot++) {
                    assertNull(skeleton.equipment(slot),
                            "the wither arm replaces the whole dice walk");
                }
                assertEquals(BuiltinItems.STONE_SWORD, skeleton.heldEquipment().type(),
                        "the variant's own melee arm");
            } else {
                archers++;
                assertEquals(BuiltinItems.BOW, skeleton.heldEquipment().type(),
                        "the plain archer's bow");
            }
        }
        assertTrue(withers > 0 && archers > 0,
                "both arms of the 4-in-5 roll landed (" + withers + "/" + archers + ")");
    }

    // ------------------------------------------------ the drop walk

    /**
     * The per-slot drop chance (MobEntity.dropEquipment lines 569-591):
     * with the player-kill window open and every chance at 1.0 (a roll
     * that always clears) all five slots drop — HELD FIRST, then feet,
     * legs, chest, head — and every damageable piece rides the reference's
     * own durability shave (never pristine: the [26, max-25] band).
     */
    @Test
    void theDropWalkCoversAllFiveSlotsInTheReferenceOrder() {
        Rig rig = rig(31);
        MobEntity zombie = rig.manager().spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1));
        zombie.setEquipment(1, ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
        zombie.setEquipment(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1));
        zombie.setEquipment(3, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        zombie.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        for (int slot = 0; slot <= 4; slot++) {
            zombie.setInventoryDropChance(slot, 1.0f);
        }
        zombie.markPlayerHit(); // the player-kill window opens every gate
        rig.manager().hurt(zombie, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            rig.manager().tick(List.of(), MobManager.NIGHT_START);
        }
        List<String> equipmentDrops = new ArrayList<>();
        for (ItemStack drop : rig.loot().drops) {
            String id = drop.type().identifier().value();
            if (id.endsWith("_sword") || id.contains("diamond_")) {
                equipmentDrops.add(id);
                int max = drop.type().maxDurability();
                assertTrue(drop.damage() >= 26 && drop.damage() <= max - 25,
                        "the shave band [26, max-25] on " + id + ": " + drop.damage());
            }
        }
        assertEquals(List.of("iron_sword", "diamond_boots", "diamond_leggings",
                "diamond_chestplate", "diamond_helmet"), equipmentDrops,
                "the five slots drop in the reference's own order");
    }

    /**
     * The 2.0 picked-up marker (updateInventory line 401): the slot drops
     * with NO player-kill window at all, pristine (the marker's own stack
     * never takes the shave) — while the 0.085 default beside it never
     * drops without the window.
     */
    @Test
    void theTwoMarkerDropsWithoutTheWindowAndTheDefaultNeverDoes() {
        Rig rig = rig(32);
        MobEntity zombie = rig.manager().spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setEquipment(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1));
        zombie.setEquipment(3, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        zombie.setInventoryDropChance(2, 2.0f); // the picked-up marker
        // slot 3 keeps the 0.085 constructor default
        assertFalse(zombie.playerHitRecently(), "no player ever touched this body");
        rig.manager().hurt(zombie, 999.0f, 0.0);
        for (int t = 0; t < MobEntity.DEATH_TICKS + 1; t++) {
            rig.manager().tick(List.of(), MobManager.NIGHT_START);
        }
        List<String> armorDrops = new ArrayList<>();
        for (ItemStack drop : rig.loot().drops) {
            if (drop.type().identifier().value().startsWith("diamond_")) {
                armorDrops.add(drop.type().identifier().value());
                assertEquals(0, drop.damage(),
                        "the marker's own stack never takes the shave");
            }
        }
        assertEquals(List.of("diamond_leggings"), armorDrops,
                "the 2.0 marker alone drops; the 0.085 default stays gated");
    }

    // ------------------------------------------------ the vanilla NBT rows

    /** The minimal bodies seam (the potion store's keying alone matters). */
    private static final class StubBodies implements
            net.zaminmc.torch.server.potion.PotionSystem.Bodies {
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
        public void hurt(Object body, float amount, String cause, Object attacker,
                boolean magic) {
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

    /**
     * The vanilla Equipment rows (MobEntity.readCustomNbt lines 301-307 —
     * the /summon and anvil import walk): the list's own index IS the slot,
     * all five land (the reference's own no-Slot-row shape reads too), the
     * DropChances rows ride beside, and the write answers the five-row
     * list back (the reference's writeNbt shape).
     */
    @Test
    void theVanillaEquipmentRowsLandAllFiveSlots() {
        net.zaminmc.torch.server.potion.PotionSystem potions =
                new net.zaminmc.torch.server.potion.PotionSystem(new StubBodies());
        boolean[] woke = {false};
        MobEntity mob = zombie();
        Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> rows =
                net.zaminmc.torch.server.nbt.Snbt.parseCompound(
                        "{Equipment:[{id:\"minecraft:iron_sword\",Count:1b},"
                                + "{id:\"minecraft:diamond_boots\",Count:1b},"
                                + "{id:\"minecraft:diamond_leggings\",Count:1b},"
                                + "{id:\"minecraft:diamond_chestplate\",Count:1b},"
                                + "{id:\"minecraft:diamond_helmet\",Count:1b}],"
                                + "DropChances:[0.5f,2.0f,0.085f,0.085f,0.0f]}");
        net.zaminmc.torch.server.nbt.EntityNbt.readNbt(mob, rows, potions,
                () -> woke[0] = true);
        assertTrue(woke[0], "the equipment-change wake fired");
        assertEquals("minecraft:iron_sword",
                mob.heldEquipment().type().identifier().toString());
        assertEquals("minecraft:diamond_boots",
                mob.equipment(1).type().identifier().toString());
        assertEquals("minecraft:diamond_leggings",
                mob.equipment(2).type().identifier().toString());
        assertEquals("minecraft:diamond_chestplate",
                mob.equipment(3).type().identifier().toString());
        assertEquals("minecraft:diamond_helmet",
                mob.equipment(4).type().identifier().toString());
        assertEquals(20, mob.armorProtectionPoints(), "the four rows sum their points");
        assertEquals(0.5f, mob.inventoryDropChance(0), 1e-6);
        assertEquals(2.0f, mob.inventoryDropChance(1), 1e-6);
        assertEquals(0.085f, mob.inventoryDropChance(2), 1e-6);
        assertEquals(0.0f, mob.inventoryDropChance(4), 1e-6);

        // The write answers the five-row list back (the reference's shape).
        Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> written =
                new java.util.LinkedHashMap<>();
        net.zaminmc.torch.server.nbt.EntityNbt.writeNbtWithoutId(mob, written, potions);
        List<net.zaminmc.torch.server.nbt.Snbt.Tag> equipment =
                net.zaminmc.torch.server.nbt.NbtOps.getList(written, "Equipment",
                        net.zaminmc.torch.server.nbt.Snbt.Tag.Type.COMPOUND);
        assertEquals(5, equipment.size(), "the five-slot list");
        assertEquals("minecraft:diamond_boots",
                net.zaminmc.torch.server.nbt.NbtOps.getString(
                        net.zaminmc.torch.server.nbt.NbtOps.listCompound(equipment, 1), "id"));
        assertEquals("minecraft:diamond_helmet",
                net.zaminmc.torch.server.nbt.NbtOps.getString(
                        net.zaminmc.torch.server.nbt.NbtOps.listCompound(equipment, 4), "id"));

        // The re-read round trip on a fresh body lands the same five.
        MobEntity loaded = zombie();
        net.zaminmc.torch.server.nbt.EntityNbt.readNbt(loaded, written, potions, null);
        assertEquals(mob.equipment(3).type(), loaded.equipment(3).type());
        assertEquals(mob.equipment(4).type(), loaded.equipment(4).type());
    }

    /**
     * The empty-row strip (the reference reads five WHATEVER they hold —
     * an empty compound SETS its slot back to empty): dice-given armor
     * clears under an all-empty list, and the wake fires for the strip.
     */
    @Test
    void theEmptyEquipmentRowsStripTheSlots() {
        net.zaminmc.torch.server.potion.PotionSystem potions =
                new net.zaminmc.torch.server.potion.PotionSystem(new StubBodies());
        MobEntity mob = zombie();
        mob.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        mob.setEquipment(1, ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
        boolean[] woke = {false};
        Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> rows =
                net.zaminmc.torch.server.nbt.Snbt.parseCompound(
                        "{Equipment:[{},{},{},{},{}]}");
        net.zaminmc.torch.server.nbt.EntityNbt.readNbt(mob, rows, potions,
                () -> woke[0] = true);
        assertNull(mob.equipment(1), "the empty row set the feet slot back to empty");
        assertNull(mob.equipment(4), "the empty row set the head slot back to empty");
        assertTrue(woke[0], "the strip is wire-visible — the wake fired");
    }

    // ------------------------------------------------ the ZVD store

    /**
     * The v13 armor tail (the reference's Equipment slots 1..4 + the
     * DropChances rows): the four stacks AND the non-default chances
     * survive the file and land on the restored body; a bare body's
     * record stays bare (the tail's absent marker).
     */
    @Test
    void theZvdStoreCarriesTheArmorFamilyBothWays(@TempDir Path tempDir) throws Exception {
        Rig rig = rig(51);
        MobEntity zombie = rig.manager().spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setEquipment(1, ItemStack.of(BuiltinItems.DIAMOND_BOOTS, 1));
        zombie.setEquipment(2, ItemStack.of(BuiltinItems.DIAMOND_LEGGINGS, 1)
                .withName("Greaves"));
        zombie.setEquipment(3, ItemStack.of(BuiltinItems.DIAMOND_CHESTPLATE, 1));
        zombie.setEquipment(4, ItemStack.of(BuiltinItems.DIAMOND_HELMET, 1));
        zombie.setInventoryDropChances(
                new float[] {0.085f, 2.0f, 0.085f, 0.5f, 0.0f});

        MobDataStore store = new MobDataStore(tempDir.resolve("armor.zmd"));
        store.save(rig.manager().snapshot());

        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(1, loaded.size(), "the one body");
        MobManager.MobNbtExtras nbt = loaded.get(0).nbt();
        assertNotNull(nbt, "the armor rows keep the record alive");
        assertEquals("minecraft:diamond_boots", nbt.feetEquipment()
                .type().identifier().toString());
        assertEquals("Greaves", nbt.legsEquipment().displayName(),
                "the stack-row family rides the armor tail whole (the v6 name row)");
        assertEquals("minecraft:diamond_chestplate", nbt.chestEquipment()
                .type().identifier().toString());
        assertEquals("minecraft:diamond_helmet", nbt.headEquipment()
                .type().identifier().toString());
        assertNotNull(nbt.dropChances(), "the non-default chances ride the tail");
        assertEquals(2.0f, nbt.dropChances()[1], 1e-6);
        assertEquals(0.5f, nbt.dropChances()[3], 1e-6);
        assertEquals(0.0f, nbt.dropChances()[4], 1e-6);

        Rig restored = rig(52);
        assertTrue(restored.manager().restoreAll(loaded));
        MobEntity back = restored.manager().all().get(0);
        assertEquals("minecraft:diamond_boots",
                back.equipment(1).type().identifier().toString());
        assertEquals("Greaves", back.equipment(2).displayName());
        assertEquals(2.0f, back.inventoryDropChance(1), 1e-6);
        assertEquals(0.0f, back.inventoryDropChance(4), 1e-6);

        // The bare body (no armor, every chance at the default) writes no
        // tail — a pre-armor shape loads unchanged.
        Rig bareRig = rig(53);
        bareRig.manager().spawnAt(MobType.COW, new Position(0.5, 4.0, 0.5));
        MobDataStore bareStore = new MobDataStore(tempDir.resolve("bare.zmd"));
        bareStore.save(bareRig.manager().snapshot());
        List<MobManager.MobSnapshot> bareLoaded = bareStore.load();
        assertEquals(1, bareLoaded.size());
        assertNull(bareLoaded.get(0).nbt(), "the bare body carries no record");
    }

    // ------------------------------------------------ the anvil import

    /**
     * The anvil import's Equipment row (a vanilla region file's mob — the
     * reference's own save shape, the list's index IS the slot): all five
     * slots land whole through the /summon machinery, with the DropChances
     * rows beside them.
     */
    @Test
    void theAnvilImportLandsTheWholeEquipmentList(@TempDir Path tempDir) throws Exception {
        Path vault = tempDir.resolve("armorvault");
        Map<String, Object> level = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        level.put("RandomSeed", 77L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 129);
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L); // midnight: the zombie never burns
        level.put("LevelName", "ArmorVault");
        net.zaminmc.torch.server.world.anvil.AnvilFixture.writeLevelDat(vault, level);

        Map<String, Object> held = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        held.put("id", "minecraft:iron_sword");
        held.put("Count", (byte) 1);
        held.put("Damage", (short) 40);
        Map<String, Object> boots = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        boots.put("id", "minecraft:diamond_boots");
        boots.put("Count", (byte) 1);
        boots.put("Damage", (short) 0);
        Map<String, Object> legs = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        legs.put("id", "minecraft:golden_leggings");
        legs.put("Count", (byte) 1);
        legs.put("Damage", (short) 0);
        Map<String, Object> chest = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        chest.put("id", "minecraft:iron_chestplate");
        chest.put("Count", (byte) 1);
        chest.put("Damage", (short) 0);
        Map<String, Object> helmet = net.zaminmc.torch.server.world.anvil.AnvilFixture
                .compound();
        helmet.put("id", "minecraft:leather_helmet");
        helmet.put("Count", (byte) 1);
        helmet.put("Damage", (short) 0);
        Map<String, Object> zombie =
                net.zaminmc.torch.server.world.anvil.AnvilFixture.entityRow(
                        "Zombie", 132.5, 1.0, 132.5);
        zombie.put("CustomName", "Armored Zombie");
        // The reference's own Equipment shape: no Slot rows, the list's
        // index IS the slot.
        zombie.put("Equipment", List.of(held, boots, legs, chest, helmet));
        zombie.put("DropChances", List.of(1.0f, 2.0f, 0.085f, 0.085f, 0.5f));

        byte[] blocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks[net.zaminmc.torch.server.world.anvil.AnvilFixture
                        .cellIndex(x, 0, z)] = 1; // stone floor
            }
        }
        byte[] chunkNbt = net.zaminmc.torch.server.world.anvil.AnvilFixture.nbtDocument(
                net.zaminmc.torch.server.world.anvil.AnvilFixture.chunkNbt(8, 8,
                        List.of(net.zaminmc.torch.server.world.anvil.AnvilFixture
                                .section(0, blocks, new byte[2048], null)),
                        null, List.of(zombie), List.of()));
        net.zaminmc.torch.server.world.anvil.AnvilFixture.writeRegionFile(
                vault.resolve("region").resolve("r.0.0.mca"),
                List.of(new net.zaminmc.torch.server.world.anvil.AnvilFixture.Entry(
                        8, 8, net.zaminmc.torch.server.world.anvil.AnvilRegionFile.SCHEME_ZLIB,
                        net.zaminmc.torch.server.world.anvil.AnvilFixture.zlib(chunkNbt))));

        List<String> console = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.logging.Handler capture = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                console.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Logger consoleLogger = java.util.logging.Logger.getLogger(
                net.zaminmc.torch.server.chat.ConsoleSender.class.getName());
        consoleLogger.addHandler(capture);
        consoleLogger.setLevel(java.util.logging.Level.INFO);
        consoleLogger.setUseParentHandlers(false);

        net.zaminmc.torch.server.config.EngineConfig config =
                new net.zaminmc.torch.server.config.EngineConfig("127.0.0.1", 0,
                        "anvil-armor", "it", 20, 2, 20, tempDir.resolve("run").toString());
        net.zaminmc.torch.server.EngineServer server =
                new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        try {
            server.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream()
                            .noneMatch(line -> line.startsWith("Anvil import finished"))) {
                Thread.sleep(50);
            }
            assertTrue(console.stream()
                            .anyMatch(line -> line.startsWith("Anvil import finished")),
                    "the import finished (console: " + console + ")");
            var imported = server.mobs().all().stream()
                    .filter(mob -> "Armored Zombie".equals(mob.customName()))
                    .toList();
            assertEquals(1, imported.size(), "the named zombie imported");
            MobEntity body = imported.get(0);
            assertEquals("minecraft:iron_sword",
                    body.heldEquipment().type().identifier().toString());
            assertEquals(40, body.heldEquipment().damage(), "the held Damage row");
            assertEquals("minecraft:diamond_boots",
                    body.equipment(1).type().identifier().toString());
            assertEquals("minecraft:golden_leggings",
                    body.equipment(2).type().identifier().toString());
            assertEquals("minecraft:iron_chestplate",
                    body.equipment(3).type().identifier().toString());
            assertEquals("minecraft:leather_helmet",
                    body.equipment(4).type().identifier().toString());
            assertEquals(3 + 3 + 6 + 1, body.armorProtectionPoints(),
                    "the imported set sums its points (boots 3, legs 3, chest 6, helmet 1)");
            assertEquals(1.0f, body.inventoryDropChance(0), 1e-6);
            assertEquals(2.0f, body.inventoryDropChance(1), 1e-6);
            assertEquals(0.5f, body.inventoryDropChance(4), 1e-6);
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }
}
