package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ZMD v1 round trips: the population snapshot survives the file, corrupt
 * files quarantine and load as absent, and unknown saved kinds drop loudly
 * while everything else restores.
 */
class MobDataStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void snapshotRoundTripsThroughTheFile() {
        Path file = tempDir.resolve("worlds/w/mobs.bin");
        MobDataStore store = new MobDataStore(file);
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("PIG", 1.5, 5.0, -2.25, 87.5f, 10.0f),
                new MobManager.MobSnapshot("ZOMBIE", 30.0, 6.0, 11.0, 180.0f, 12.5f));
        store.save(saved);
        assertEquals(saved, store.load(), "the full population round-trips");
    }

    @Test
    void emptyFileMeansNoMobsOnBothSides() {
        MobDataStore store = new MobDataStore(tempDir.resolve("fresh.bin"));
        assertEquals(List.of(), store.load(), "an absent file loads as a fresh world");
        store.save(List.of());
        assertEquals(List.of(), store.load(), "an explicitly empty population persists");
    }

    @Test
    void corruptFilesQuarantineAndLoadAsAbsent() throws Exception {
        Path file = tempDir.resolve("corrupt.bin");
        MobDataStore store = new MobDataStore(file);
        java.nio.file.Files.write(file, new byte[] {'Z', 'M', 'D', 1, 9, 9, 9});
        assertEquals(List.of(), store.load(), "corruption loads as absent");
        assertTrue(java.nio.file.Files.exists(file.resolveSibling("corrupt.bin.corrupt")),
                "the unreadable file is preserved beside the storage path");
    }

    @Test
    void unknownSavedKindsDropWithoutKillingTheRest() {
        // Manager-level: restoreAll skips unknown kinds and restores the rest.
        MobManager manager = new MobManager(
                new MobEntity.WorldQuery() {
                    @Override
                    public boolean isSolid(double x, double y, double z) {
                        return false;
                    }

                    @Override
                    public net.zaminmc.torch.util.Position nearestPlayer(double x, double y, double z,
                                                                double range) {
                        return null;
                    }
                },
                new java.util.Random(7), (position, stack) -> { }, 3000,
                (x, z) -> 4);
        List<MobManager.MobSnapshot> saved = List.of(
                // A kind that will never exist (Slice 63 added the very
                // real ENDER_DRAGON — the old fixture name).
                new MobManager.MobSnapshot("NOT_A_REAL_KIND", 0, 100, 0, 0f, 200f),
                new MobManager.MobSnapshot("COW", 2.0, 5.0, 3.0, 45f, 3.5f));
        assertTrue(manager.restoreAll(saved), "the known kind restored");
        assertEquals(1, manager.size());
        MobEntity cow = manager.all().get(0);
        assertEquals(MobType.COW, cow.type());
        assertEquals(3.5f, cow.health(), "the saved body restores");
        assertEquals(45.0f, cow.yaw(), "the saved facing restores");
    }

    @Test
    void theHeldStacksWaveGArmsSurviveTheStore() throws Exception {
        // The ZMD v7 tail (Slice 94): the held stack's Wave G rows — the
        // Unbreakable byte, the RepairCost varint, the HideFlags varint,
        // the Lore list — ride the equipment block through the file.
        MobManager manager = new MobManager(new StubWorld(), new java.util.Random(7),
                (position, stack) -> { }, 1_100_000, (x, z) -> 4);
        MobEntity zombie = manager.spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_SWORD, 1)
                .withUnbreakable(true)
                .withRepairCost(5)
                .withHideFlags(2)
                .withLore(List.of("Rusted but eternal")));

        MobDataStore store = new MobDataStore(tempDir.resolve("waveg.zmd"));
        store.save(manager.snapshot());

        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        net.zaminmc.torch.item.ItemStack held = loaded.get(0).nbt().heldEquipment();
        assertTrue(held != null && !held.isEmpty());
        assertTrue(held.unbreakable(), "the flag survived");
        assertEquals(5, held.repairCost());
        assertEquals(2, held.hideFlags());
        assertEquals(List.of("Rusted but eternal"), held.lore());

        // And the restored body carries them onto the engine.
        MobManager restored = new MobManager(new StubWorld(), new java.util.Random(8),
                (position, stack) -> { }, 1_200_000, (x, z) -> 4);
        restored.restoreAll(loaded);
        net.zaminmc.torch.item.ItemStack again = restored.all().get(0).heldEquipment();
        assertTrue(again.unbreakable());
        assertEquals(5, again.repairCost());
        assertEquals(2, again.hideFlags());
        assertEquals(List.of("Rusted but eternal"), again.lore());
    }

    @Test
    void theHeldStacksAttributeModifiersSurviveTheStore() throws Exception {
        // Wave J — the ZMD v11 tail: the whole modifier list as ONE
        // parseable-SNBT string row (the BlockEntityTag tail's pattern),
        // riding the held equipment block. A v10-era body loads with the
        // arm absent.
        MobManager manager = new MobManager(new StubWorld(), new java.util.Random(9),
                (position, stack) -> { }, 1_100_000, (x, z) -> 4);
        java.util.UUID uuid = java.util.UUID.fromString(
                "CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
        MobEntity zombie = manager.spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_SWORD, 1)
                .withAttributeModifiers(List.of(
                        new net.zaminmc.torch.item.AttributeModifier(
                                "generic.attackDamage", "Weapon modifier",
                                12.0, 0, uuid))));

        MobDataStore store = new MobDataStore(tempDir.resolve("wavej.zmd"));
        store.save(manager.snapshot());

        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        net.zaminmc.torch.item.ItemStack held = loaded.get(0).nbt().heldEquipment();
        assertTrue(held != null && held.attributeModifiers() != null,
                "the arm rode the v11 tail");
        assertEquals(1, held.attributeModifiers().size());
        net.zaminmc.torch.item.AttributeModifier row = held.attributeModifiers().get(0);
        assertEquals("generic.attackDamage", row.attributeName());
        assertEquals("Weapon modifier", row.name());
        assertEquals(12.0, row.amount(), 0.0);
        assertEquals(0, row.operation());
        assertEquals(uuid, row.uuid());
        // The equipment-better compare consult survives with it (the
        // tagged sword out-ranks the bare tier table).
        assertTrue(net.zaminmc.torch.server.item.Tools.heldDamageOf(held)
                        > net.zaminmc.torch.server.item.Tools.attackDamageOf(
                                net.zaminmc.torch.server.item.BuiltinItems.IRON_SWORD),
                "the fold outranks the bare table (13 vs 6)");
    }

    /** A flat world: solid below y=4, the surface at 4, no players near. */
    private static final class StubWorld implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override
        public double supportY(double x, double y, double z) {
            return 4.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    @Test
    void theStateBlockRoundTripsThroughTheV2File() {
        Path file = tempDir.resolve("worlds/w/mobs.bin");
        MobDataStore store = new MobDataStore(file);
        // The converting zombie villager (IsVillager + ConversionTime), the
        // traded villager (Profession/Career/CareerLevel/Riches), the
        // killer rabbit (the 99 coat), a baby (the negative Age), the
        // wither skeleton, and a size-4 slime — the reference's own NBT
        // fields, one record each.
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("ZOMBIE", 1.5, 8.0, 0.5, 90f, 18f,
                        new MobManager.MobStateExtras(true, 4321, 0, 0, 0, 0,
                                0, 0, false, 1)),
                new MobManager.MobSnapshot("VILLAGER", 3.5, 8.0, 0.5, 0f, 20f,
                        new MobManager.MobStateExtras(false, 0, 2, 1, 3, 12,
                                0, 0, false, 1)),
                new MobManager.MobSnapshot("RABBIT", -2.5, 8.0, -1.5, 0f, 10f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                99, -8000, false, 1)),
                new MobManager.MobSnapshot("SKELETON", 5.5, 8.0, 0.5, 0f, 20f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, 0, true, 1)),
                new MobManager.MobSnapshot("SLIME", 7.5, 8.0, 0.5, 0f, 16f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, 0, false, 4)));
        store.save(saved);
        assertEquals(saved, store.load(), "the whole state block round-trips");
    }

    @Test
    void theRestoredStateLandsOnTheEngineBodies() {
        MobManager manager = new MobManager(
                new MobEntity.WorldQuery() {
                    @Override
                    public boolean isSolid(double x, double y, double z) {
                        return false;
                    }

                    @Override
                    public net.zaminmc.torch.util.Position nearestPlayer(double x, double y, double z,
                                                                double range) {
                        return null;
                    }
                },
                new java.util.Random(7), (position, stack) -> { }, 3000,
                (x, z) -> 4);
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("ZOMBIE", 1.5, 8.0, 0.5, 90f, 18f,
                        new MobManager.MobStateExtras(true, 4321, 0, 0, 0, 0,
                                0, 0, false, 1)),
                new MobManager.MobSnapshot("VILLAGER", 3.5, 8.0, 0.5, 0f, 20f,
                        new MobManager.MobStateExtras(false, 0, 2, 1, 3, 12,
                                0, -6000, false, 1)));
        assertTrue(manager.restoreAll(saved));

        MobEntity zombie = manager.all().stream()
                .filter(m -> m.type() == MobType.ZOMBIE).findFirst().orElseThrow();
        assertTrue(zombie.zombieVillager(), "IsVillager restores (readCustomNbt line 340)");
        assertTrue(zombie.zombieConverting(), "ConversionTime > -1 arms the countdown");
        assertEquals(4321, zombie.zombieConversionTicks());

        MobEntity villager = manager.all().stream()
                .filter(m -> m.type() == MobType.VILLAGER).findFirst().orElseThrow();
        assertEquals(2, villager.profession());
        assertEquals(1, villager.villagerCareer());
        assertEquals(3, villager.villagerCareerLevel());
        assertEquals(12, villager.villagerRiches());
        assertTrue(villager.isBaby(), "the negative Age restores the childhood");
    }
    @Test
    void theHeldStacksSkullOwnerSurvivesTheStore() throws Exception {
        // Wave K — the ZMD v12 tail: the held player head's profile as
        // ONE parseable-SNBT string row.
        MobManager manager = new MobManager(new StubWorld(), new java.util.Random(15),
                (position, stack) -> { }, 1_100_000, (x, z) -> 4);
        MobEntity zombie = manager.spawnAt(
                MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.SKULL, 1, 3)
                .withSkullOwner(net.zaminmc.torch.item.SkullProfile.ofName("jeb_")));

        MobDataStore store = new MobDataStore(tempDir.resolve("wavek.zmd"));
        store.save(manager.snapshot());

        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        net.zaminmc.torch.item.ItemStack held = loaded.get(0).nbt().heldEquipment();
        assertTrue(held != null && held.skullOwner() != null,
                "the arm rode the v12 tail");
        assertEquals("jeb_", held.skullOwner().name());
        assertNull(held.skullOwner().id());
    }
}
