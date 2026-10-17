package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobDataStore;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.potion.PotionSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The owner rows + the horse's breeding rows (Slice 92 — the C3 envelope's
 * second landing): the horse's OwnerUUID (the uuid string, the legacy
 * Owner name row), the wolf's Tame-less write (the reference's own shape —
 * the tamed state derives from the owner row at load), the ocelot's added
 * Tameable rows, the isOwner consult (the uuid arm first, the name the
 * legacy fallback), and the ZMD v6 tameable/horse block (the tamed wolf
 * survives the restart).
 */
class OwnerRowsModelTest {

    /** The minimal world (air everywhere, no players). */
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

    /** The minimal bodies seam. */
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

    private static MobEntity mob(MobType type) {
        return new MobEntity(7, type, new Position(1.5, 64.0, 2.5),
                new Random(11), new StubWorld());
    }

    private static PotionSystem potions() {
        return new PotionSystem(new StubBodies());
    }

    @Test
    void theHorseRowsRoundTripTheOwnerAndTheBredMarkers() {
        // HorseBaseEntity.writeCustomNbt 1098-1105 + readCustomNbt
        // 1133-1153: the uuid string, the bred pair, the graze flash.
        MobEntity saved = mob(MobType.HORSE);
        String uuid = UUID.randomUUID().toString();
        saved.tame("Abasing", uuid);
        saved.setHorseBred(true);
        saved.setHorseHasReproduced(true);

        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(saved, written, potions());
        assertEquals(uuid, NbtOps.getString(written, "OwnerUUID"));
        assertTrue(NbtOps.getBoolean(written, "Tame"));
        assertTrue(NbtOps.getBoolean(written, "Bred"));
        assertTrue(NbtOps.getBoolean(written, "HasReproduced"));

        MobEntity loaded = mob(MobType.HORSE);
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(uuid, loaded.ownerUuid());
        assertTrue(loaded.tamed());
        assertTrue(loaded.horseBred());
        assertTrue(loaded.horseHasReproduced());
    }

    @Test
    void theHorseGrazeRowRearmsTheFlash() {
        // EatingHaystack true re-arms the graze (the 20-tick flash the
        // engine's eating model carries).
        MobEntity loaded = mob(MobType.HORSE);
        Map<String, Snbt.Tag> nbt = new java.util.LinkedHashMap<>();
        NbtOps.putBoolean(nbt, "EatingHaystack", true);
        EntityNbt.readTypeCustomNbtForTest(loaded, nbt);
        assertTrue(loaded.eating());
    }

    @Test
    void theWolfWriteCarriesNoTameRow() {
        // The reference's wolf write (WolfEntity 122-126 + TameableEntity
        // 30-38): OwnerUUID + Sitting + Angry + CollarColor — the Tame row
        // does not exist (the tamed state derives from the owner at load).
        MobEntity saved = mob(MobType.WOLF);
        String uuid = UUID.randomUUID().toString();
        saved.tameWolf("Abasing", uuid);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(saved, written, potions());
        assertNull(NbtOps.get(written, "Tame"));
        assertEquals(uuid, NbtOps.getString(written, "OwnerUUID"));
        assertTrue(NbtOps.getBoolean(written, "Sitting"));
    }

    @Test
    void theWolfOwnerRowTamesAtLoad() {
        // TameableEntity.readCustomNbt lines 52-56: a non-empty owner row
        // sets the owner AND the tamed flag.
        MobEntity loaded = mob(MobType.WOLF);
        assertFalse(loaded.wolfTamed());
        Map<String, Snbt.Tag> nbt = new java.util.LinkedHashMap<>();
        NbtOps.putString(nbt, "OwnerUUID", UUID.randomUUID().toString());
        EntityNbt.readTypeCustomNbtForTest(loaded, nbt);
        assertTrue(loaded.wolfTamed());
        assertFalse(loaded.ownerUuid().isEmpty());
    }

    @Test
    void theLegacyOwnerNameRowLandsTheNameSlot() {
        // The pre-1.8 Owner NAME row: the name-keyed gates keep working.
        MobEntity loaded = mob(MobType.WOLF);
        Map<String, Snbt.Tag> nbt = new java.util.LinkedHashMap<>();
        NbtOps.putString(nbt, "Owner", "Abasing");
        EntityNbt.readTypeCustomNbtForTest(loaded, nbt);
        assertEquals("Abasing", loaded.ownerName());
        assertTrue(loaded.wolfTamed());
    }

    @Test
    void theOcelotCarriesTheTameableRows() {
        // The reference's ocelot inherits TameableEntity's walk: OwnerUUID
        // + Sitting beside the coat row.
        MobEntity saved = mob(MobType.OCELOT);
        String uuid = UUID.randomUUID().toString();
        saved.tameOcelot("Abasing", uuid, 2);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(saved, written, potions());
        assertEquals(uuid, NbtOps.getString(written, "OwnerUUID"));
        assertTrue(NbtOps.getBoolean(written, "Sitting"));
        assertEquals(2, NbtOps.getInt(written, "CatType"));

        MobEntity loaded = mob(MobType.OCELOT);
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertTrue(loaded.wolfTamed());
        assertEquals(uuid, loaded.ownerUuid());
        assertEquals(2, loaded.catVariant());
    }

    @Test
    void theOwnerConsultPrefersTheUuid() {
        // The reference's isOwner: the uuid string comparison; the name
        // arm stays the engine's legacy fallback.
        MobEntity wolf = mob(MobType.WOLF);
        UUID ownerUuid = UUID.randomUUID();
        wolf.tameWolf("SomeoneElse", ownerUuid.toString());
        // A player with a different name but the same uuid owns the wolf.
        assertTrue(wolf.ownerUuid().equals(ownerUuid.toString()));
        // (The full PlayerSession consult runs in the acceptance suites —
        // the unit row pins the split: the uuid slot holds the row's own
        // string, the name slot the engine's own.)
        assertEquals("SomeoneElse", wolf.ownerName());
        assertEquals(ownerUuid.toString(), wolf.ownerUuid());
    }

    @Test
    void theTamedWolfSurvivesTheRestart(@TempDir Path dir) throws Exception {
        // The ZMD v6 tameable/horse block: the owner pair + the tamed bit
        // + the family's sitting/angry round-trip through the store.
        MobManager manager = new MobManager(new StubWorld(), new Random(7),
                (position, stack) -> { }, 1_100_000, (x, z) -> 4);
        MobEntity wolf = manager.spawnAt(MobType.WOLF, new Position(0.5, 4.0, 0.5));
        String uuid = UUID.randomUUID().toString();
        wolf.tameWolf("Abasing", uuid);
        wolf.setWolfSitting(true);
        wolf.setWolfCollarColor(5);

        List<MobManager.MobSnapshot> snapshot = manager.snapshot();
        MobDataStore store = new MobDataStore(dir.resolve("mobs.zmd"));
        store.save(snapshot);

        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(1, loaded.size());
        assertNotNull(loaded.get(0).tame());
        assertEquals(uuid, loaded.get(0).tame().ownerUuid());
        assertTrue(loaded.get(0).tame().tamed());
        assertTrue(loaded.get(0).tame().sitting());

        MobManager restored = new MobManager(new StubWorld(), new Random(8),
                (position, stack) -> { }, 1_200_000, (x, z) -> 4);
        restored.restoreAll(loaded);
        MobEntity wolfAgain = restored.all().get(0);
        assertTrue(wolfAgain.wolfTamed());
        assertTrue(wolfAgain.wolfSitting());
        assertEquals(uuid, wolfAgain.ownerUuid());
        assertEquals("Abasing", wolfAgain.ownerName());
    }

    @Test
    void thePlainKindsCarryNoTameBlock() {
        MobManager manager = new MobManager(new StubWorld(), new Random(9),
                (position, stack) -> { }, 1_100_000, (x, z) -> 4);
        manager.spawnAt(MobType.COW, new Position(0.5, 4.0, 0.5));
        List<MobManager.MobSnapshot> snapshot = manager.snapshot();
        assertNull(snapshot.get(0).tame());
    }
}
