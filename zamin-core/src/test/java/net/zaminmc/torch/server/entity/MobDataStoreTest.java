package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                new MobManager.MobSnapshot("ENDER_DRAGON", 0, 100, 0, 0f, 200f),
                new MobManager.MobSnapshot("COW", 2.0, 5.0, 3.0, 45f, 3.5f));
        assertTrue(manager.restoreAll(saved), "the known kind restored");
        assertEquals(1, manager.size());
        MobEntity cow = manager.all().get(0);
        assertEquals(MobType.COW, cow.type());
        assertEquals(3.5f, cow.health(), "the saved body restores");
        assertEquals(45.0f, cow.yaw(), "the saved facing restores");
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
}
