package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.potion.PotionSystem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loot-pickup model (Slice 92 p3 — the base-defaults envelope's
 * third landing), driven through the REAL manager tick: the pickup
 * flag's spawn roll (the local difficulty's own multiplier — the
 * peaceful never arms, the hard band's roll does) and the villager's
 * always-true; the equip walk's better-item comparison
 * (MobEntity.updateInventory lines 349-403) over the grown box; the
 * NoAI pin's gate (the reference's isLocallyControlled over the ai
 * section); the always-drop marker (2.0) and the persistence pin; the
 * death walk's equipment drop (dropEquipment lines 569-589) beside the
 * kind's loot; and the two NBT rows (CanPickUpLoot's BYTE gate,
 * DropChances' per-slot list).
 */
class LootPickupModelTest {

    /** The flat world: solid below y=4, no players. */
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

    /** The minimal bodies seam (the potion store's keying alone matters). */
    private static final class StubBodies implements PotionSystem.Bodies {
        @Override
        public List<Object> bodiesIn(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ, int dimension) {
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

    /** The item-entity ground: solid below y=4. */
    private static final class FlatGround implements ItemEntity.Ground {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < 4.0;
        }

        @Override
        public double supportY(double x, double y, double z) {
            return 4.0;
        }
    }

    private static final class RecordingSink implements MobManager.LootSink {
        final List<ItemStack> drops = new java.util.ArrayList<>();

        @Override
        public void spawnLootDrop(Position position, ItemStack stack, int dimension) {
            drops.add(stack);
        }
    }

    private record Rig(MobManager manager, ItemEntityManager items, RecordingSink loot) {
    }

    /** The flat rig: the manager with the pickup world wired, griefing on, loot on. */
    private static Rig rig(long seed) {
        RecordingSink loot = new RecordingSink();
        MobManager manager = new MobManager(new StubWorld(), new Random(seed), loot,
                1_100_000, (x, z) -> 4);
        manager.setMobLootRead(() -> true);
        ItemEntityManager items = new ItemEntityManager(new FlatGround(),
                new Random(seed + 1), 2_000_000);
        manager.setItemEntitiesForPickup(items);
        manager.setMobGriefingRead(() -> true);
        return new Rig(manager, items, loot);
    }

    /** The item seeded inside the mob's grown box, delay drained. */
    private static ItemEntity seedItem(Rig rig, ItemStack stack, double x, double z) {
        ItemEntity item = rig.items().spawnDropAtBlock(new Position(x, 4.0, z), stack, 0);
        rig.items().tick(List.of());
        return item;
    }

    private static PotionSystem potions() {
        return new PotionSystem(new StubBodies());
    }

    @Test
    void theSpawnRollNeverArmsOnPeaceful() {
        // The local difficulty's multiplier reads 0 on peaceful — the
        // reference's own LocalDifficulty.calculate gate: nextFloat() < 0
        // never passes, however long the world has run.
        Rig rig = rig(3);
        rig.manager().setDifficultyRead(() -> 0);
        for (int i = 0; i < 60; i++) {
            rig.manager().tick(List.of(), 10_000_000L);
        }
        for (int i = 0; i < 100; i++) {
            MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                    new Position(0.5, 4.0, 0.5));
            assertFalse(zombie.canPickUpLoot(), "the peaceful never arms");
        }
    }

    @Test
    void theSpawnRollArmsOnTheHardBand() {
        // HARD + a long-running world (the time term maxed): the
        // multiplier reads 0.5, the roll 0.55 * 0.5 = 27.5% per spawn —
        // over 200 spawns the band lands with certainty.
        Rig rig = rig(4);
        rig.manager().setDifficultyRead(() -> 3);
        rig.manager().tick(List.of(), 10_000_000L); // the time term arms
        int armed = 0;
        for (int i = 0; i < 200; i++) {
            MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                    new Position(0.5, 4.0, 0.5));
            if (zombie.canPickUpLoot()) {
                armed++;
            }
        }
        assertTrue(armed > 0, "the hard band's roll arms some bodies, got " + armed);
    }

    @Test
    void theVillagerAlwaysPicksUp() {
        // VillagerEntity's constructor arm (line 322): whatever the
        // difficulty reads.
        Rig rig = rig(5);
        rig.manager().setDifficultyRead(() -> 0);
        MobEntity villager = rig.manager().spawnAt(MobType.VILLAGER,
                new Position(0.5, 4.0, 0.5));
        assertTrue(villager.canPickUpLoot());
    }

    @Test
    void theEquipWalkTakesTheBetterSword() {
        // updateInventory's slot-0 comparison: the diamond 7 over the
        // iron 6 — the equip, the 2.0 always-drop marker, the persistence
        // pin, the collect.
        Rig rig = rig(6);
        rig.manager().setDifficultyRead(() -> 3);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setCanPickUpLoot(true);
        zombie.setHeldEquipment(ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.IRON_SWORD, 1));
        ItemEntity diamond = seedItem(rig, ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1), 1.0, 1.0);
        assertTrue(rig.items().all().contains(diamond));

        rig.manager().tick(List.of(), 0L);

        assertEquals("diamond_sword",
                zombie.heldEquipment().type().identifier().value());
        assertFalse(rig.items().all().contains(diamond)); // collected
        assertEquals(2.0f, zombie.inventoryDropChance(0), 1e-6f); // the always marker
        assertTrue(zombie.rawPersistent()); // the persistence pin
    }

    @Test
    void theEquipWalkRefusesTheWorseSword() {
        Rig rig = rig(7);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setCanPickUpLoot(true);
        zombie.setHeldEquipment(ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1));
        ItemEntity wood = seedItem(rig, ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.WOODEN_SWORD, 1), 1.0, 1.0);
        rig.manager().tick(List.of(), 0L);
        assertEquals("diamond_sword",
                zombie.heldEquipment().type().identifier().value());
        assertTrue(rig.items().all().contains(wood)); // left on the ground
    }

    @Test
    void aPinnedBodyNeverPicksUp() {
        // The reference's isLocallyControlled gate over the ai section:
        // the scan rides mobTick — a NoAI body never picks up.
        Rig rig = rig(8);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setCanPickUpLoot(true);
        zombie.setNoAi(true);
        seedItem(rig, ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1), 1.0, 1.0);
        rig.manager().tick(List.of(), 0L);
        assertNull(zombie.heldEquipment());
        assertEquals(1, rig.items().all().size());
    }

    @Test
    void theOutOfBandItemNeverPicksUp() {
        // The grown box: 1.0 horizontal growth over the zombie's own
        // 0.6-width box — an item six blocks out stays untouched.
        Rig rig = rig(9);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setCanPickUpLoot(true);
        seedItem(rig, ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1), 7.0, 9.0);
        rig.manager().tick(List.of(), 0L);
        assertNull(zombie.heldEquipment());
        assertEquals(1, rig.items().all().size());
    }

    @Test
    void theDeathWalkDropsTheAlwaysMarkedHand() {
        // dropEquipment: the 2.0 marker (a picked-up hand) drops with NO
        // player hit — the plain kind's loot walks beside it.
        Rig rig = rig(10);
        rig.manager().setDifficultyRead(() -> 3);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1));
        zombie.setInventoryDropChance(0, 2.0f);
        assertFalse(zombie.playerHitRecently());
        zombie.hurt(100.0f); // the death
        for (int i = 0; i < 30 && rig.manager().size() > 0; i++) {
            rig.manager().tick(List.of(), 0L); // the animation finishes
        }
        boolean dropped = rig.loot().drops.stream().anyMatch(stack ->
                stack.type().identifier().value().equals("diamond_sword"));
        assertTrue(dropped, "the always-marked hand dropped with no player hit");
    }

    @Test
    void theDeathWalkHoldsTheUnmarkedHandWithoutAPlayerHit() {
        // The plain 0.085 hand needs the player-kill window — a mob that
        // died by the environment keeps its sword.
        Rig rig = rig(11);
        MobEntity zombie = rig.manager().spawnAt(MobType.ZOMBIE,
                new Position(0.5, 4.0, 0.5));
        zombie.setHeldEquipment(ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1));
        zombie.hurt(100.0f);
        for (int i = 0; i < 30 && rig.manager().size() > 0; i++) {
            rig.manager().tick(List.of(), 0L);
        }
        // The zombie's own loot (rotten flesh) may land; the sword must not.
        boolean swordDropped = rig.loot().drops.stream().anyMatch(stack ->
                stack.type().identifier().value().equals("diamond_sword"));
        assertFalse(swordDropped, "the unmarked hand held without a player hit");
    }

    @Test
    void theNbtRoundTripCarriesThePickupHand() {
        // A picked-up hand survives the restart: the Equipment row (the
        // held stack) + the DropChances row (the 2.0 marker) + the flag's
        // BYTE-gated read.
        MobEntity saved = new MobEntity(7, MobType.ZOMBIE,
                new Position(1.5, 64.0, 2.5), new Random(11), new StubWorld());
        saved.setCanPickUpLoot(true);
        saved.setHeldEquipment(ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_SWORD, 1));
        saved.setInventoryDropChance(0, 2.0f);
        saved.setInventoryDropChance(3, 0.5f);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(saved, written, potions());
        assertTrue(NbtOps.getBoolean(written, "CanPickUpLoot"));
        List<Snbt.Tag> chances = NbtOps.getList(written, "DropChances", Snbt.Tag.Type.FLOAT);
        assertEquals(5, chances.size());
        assertEquals(2.0f, (float) chances.get(0).asDouble(), 1e-6f);

        MobEntity loaded = new MobEntity(8, MobType.ZOMBIE,
                new Position(1.5, 64.0, 2.5), new Random(12), new StubWorld());
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertNotNull(loaded.heldEquipment());
        assertEquals("diamond_sword", loaded.heldEquipment().type().identifier().value());
        assertEquals(2.0f, loaded.inventoryDropChance(0), 1e-6f);
        assertEquals(0.5f, loaded.inventoryDropChance(3), 1e-6f);
        assertTrue(loaded.canPickUpLoot());
    }

    @Test
    void anAbsentPickupRowKeepsTheSpawnRoll() {
        // The BYTE gate: a row-less compound never disarms the body.
        MobEntity mob = new MobEntity(7, MobType.ZOMBIE,
                new Position(1.5, 64.0, 2.5), new Random(11), new StubWorld());
        mob.setCanPickUpLoot(true);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        written.remove("CanPickUpLoot");
        EntityNbt.readNbt(mob, written, potions(), null);
        assertTrue(mob.canPickUpLoot());
    }
}
