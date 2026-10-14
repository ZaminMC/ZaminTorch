package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dye family (Slice 30b — SheepEntity/DyeItem/WolfEntity's color arms,
 * 1:1): the natural fleece roll's exact bands, the offspring mixing ladder
 * through the crafting consult (the reference's own two-dye CraftingManager
 * walk), the shear's colored wool drops with the lamb gate, the death
 * drops' color carry, and the ZMD dye byte's round trip for both domains
 * (the fleece id and the collar metadata).
 */
class SheepDyeFamilyTest {

    private static MobEntity.WorldQuery flatWorld() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }
            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 4;

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new java.util.ArrayList<>();
        @Override public void spawnLootDrop(Position position, ItemStack stack) {
            drops.add(stack);
        }
    }

    private static final class Events implements MobManager.Listener {
        int wool;
        @Override public void onMobSpawned(MobEntity mob) { }
        @Override public void onMobMoved(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession rider) { }
        @Override public void onMobHurt(MobEntity mob) { }
        @Override public void onMobDied(MobEntity mob) { }
        @Override public void onMobRemoved(MobEntity mob, String reason) { }
        @Override public void onMobAttackedPlayer(MobEntity mob, net.zaminmc.torch.server.player.PlayerSession target,
                                                  float damage) { }
        @Override public void onMobSound(MobEntity mob, String soundName) { }
        @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) { }
        @Override public void onMobFuseChanged(MobEntity mob, boolean priming) { }
        @Override public void onMobSheared(MobEntity mob, int woolCount) { wool += woolCount; }
        @Override public void onMobCoatRegrown(MobEntity mob) { }
        @Override public void onMobExploded(MobEntity mob) { }
    }

    private record Rig(MobManager manager, Events events, Loot loot) {
    }

    private Rig rig(long seed) {
        Loot loot = new Loot();
        Events events = new Events();
        MobManager manager = new MobManager(flatWorld(), new Random(seed), loot,
                1_100_000, SURFACE);
        manager.setCraftingConsult(CraftingService.builtin());
        manager.addListener(events);
        return new Rig(manager, events, loot);
    }

    private MobEntity sheepAt(Rig rig, double x, double z, int fleece) {
        MobEntity sheep = rig.manager().spawnAt(MobType.SHEEP, new Position(x, 4.0, z));
        sheep.setSheepFleeceColor(fleece);
        return sheep;
    }

    @Test
    void theNaturalFleeceRollCarriesTheReferenceBands() {
        // SheepEntity.pickColor lines 225-238: 5/100 black, 5 gray, 5
        // silver, 3 brown, 1/500 pink, else white — 10k seeded rolls.
        Random roll = new Random(20261015L);
        int black = 0, gray = 0, silver = 0, brown = 0, pink = 0, white = 0;
        for (int i = 0; i < 10_000; i++) {
            int color = MobManager.pickSheepColor(roll);
            switch (color) {
                case 15 -> black++;
                case 7 -> gray++;
                case 8 -> silver++;
                case 12 -> brown++;
                case 6 -> pink++;
                case 0 -> white++;
                default -> throw new AssertionError("outside the roll table: " + color);
            }
        }
        assertEquals(500, black, 120, "5% black");
        assertEquals(500, gray, 120, "5% gray");
        assertEquals(500, silver, 120, "5% silver");
        assertEquals(300, brown, 90, "3% brown");
        assertEquals(20, pink, 25, "1/500 pink");
        assertTrue(white > 7_500, "the rest white: " + white);
    }

    @Test
    void theSpawnedSheepCarriesANaturalColor() {
        Rig rig = rig(7);
        for (int i = 0; i < 200; i++) {
            MobEntity sheep = rig.manager().spawnAt(MobType.SHEEP, new Position(0.5, 4.0, 0.5));
            int color = sheep.sheepFleeceColor();
            assertTrue(color == 0 || color == 6 || color == 7 || color == 8
                    || color == 12 || color == 15,
                    "spawn colors stay in the pick table: " + color);
            rig.manager().remove(sheep, "test");
        }
    }

    @Test
    void theOffspringMixRidesTheCraftingConsult() {
        Rig rig = rig(11);
        // Red (id 14, dye damage 1) + yellow (id 4, dye damage 11) mix to
        // orange (dye metadata 14 → the fleece id 15 - 14 = 1) — the
        // reference's own registerShapeless pair.
        MobEntity red = sheepAt(rig, 0.5, 0.5, 14);
        MobEntity yellow = sheepAt(rig, 2.5, 0.5, 4);
        assertEquals(1, rig.manager().offspringSheepColor(red, yellow),
                "red + yellow = orange through the mix table");
        // Black (15, damage 0) + white (0, damage 15) = gray (meta 8, id 7).
        MobEntity black = sheepAt(rig, 0.5, 2.5, 15);
        MobEntity white = sheepAt(rig, 0.5, 4.5, 0);
        assertEquals(7, rig.manager().offspringSheepColor(black, white),
                "black + white = gray");
        // The unmatched pair: pink (6, damage 9) + green (13, damage 2) has
        // no mix row — the parent coin (SheepEntity line 272).
        MobEntity pink = sheepAt(rig, 0.5, 6.5, 6);
        MobEntity green = sheepAt(rig, 0.5, 8.5, 13);
        int coin = rig.manager().offspringSheepColor(pink, green);
        assertTrue(coin == 6 || coin == 13,
                "the unmatched pair falls to a parent: " + coin);
    }

    @Test
    void theShearDropsCarryTheColorAndTheLambGateHolds() {
        Rig rig = rig(13);
        MobEntity sheep = sheepAt(rig, 0.5, 0.5, 15); // black
        rig.manager().shear(sheep);
        assertEquals(1, rig.events().wool > 0 ? 1 : 0, "the shear reported");
        int woolDrops = 0;
        for (ItemStack drop : rig.loot().drops) {
            if (drop.type() == BuiltinItems.WOOL) {
                woolDrops++;
                assertEquals(15, drop.damage(), "the wool carries the fleece's DyeColor id");
                assertEquals(1, drop.count(), "every unit its own single stack");
            }
        }
        assertTrue(woolDrops >= 1 && woolDrops <= 3, "1-3 wool (the reference's roll): " + woolDrops);

        // The lamb gate (interactMob line 149): a baby never shears — the
        // coat bit stays, no second drops, the report is 0.
        MobEntity lamb = rig.manager().spawnAt(MobType.SHEEP, new Position(2.5, 4.0, 0.5));
        lamb.setBreedingAge(-24000);
        assertEquals(0, rig.manager().shear(lamb), "the lamb never shears");
        assertFalse(lamb.sheared(), "the coat bit never landed on the lamb");
        int dropsAfterLamb = rig.loot().drops.size();
        assertEquals(0, rig.manager().shear(lamb));
        assertEquals(dropsAfterLamb, rig.loot().drops.size(), "no drops the second try either");
    }

    @Test
    void theDeathDropsCarryTheColorAndTheShearedSkip() {
        Rig rig = rig(17);
        // Unsheared: one wool of the body's own color + the mutton walk.
        MobEntity sheep = sheepAt(rig, 0.5, 0.5, 6); // pink
        rig.manager().hurt(sheep, 999.0f, 0.0f);
        for (int i = 0; i < MobEntity.DEATH_TICKS + 1; i++) {
            rig.manager().tick(List.of(), MobManager.NIGHT_START);
        }
        int pinkWool = 0, mutton = 0;
        for (ItemStack drop : rig.loot().drops) {
            if (drop.type() == BuiltinItems.WOOL) {
                pinkWool++;
                assertEquals(6, drop.damage(), "the death wool is the body's pink");
            }
            if (drop.type() == BuiltinItems.MUTTON || drop.type() == BuiltinItems.COOKED_MUTTON) {
                mutton++;
            }
        }
        assertEquals(1, pinkWool, "the unsheared body carries one wool (line 99's exact 1)");
        assertTrue(mutton >= 1 && mutton <= 2, "the mutton walk 1+0-1: " + mutton);

        // Sheared: the death wool skips, the mutton still rolls.
        rig = rig(19);
        MobEntity shorn = sheepAt(rig, 0.5, 0.5, 6);
        rig.manager().shear(shorn);
        rig.loot().drops.clear();
        rig.manager().hurt(shorn, 999.0f, 0.0f);
        for (int i = 0; i < MobEntity.DEATH_TICKS + 1; i++) {
            rig.manager().tick(List.of(), MobManager.NIGHT_START);
        }
        for (ItemStack drop : rig.loot().drops) {
            assertFalse(drop.type() == BuiltinItems.WOOL, "a shorn body carries no death wool");
        }
    }

    @Test
    void theDyeByteRoundTripsForBothDomains(@TempDir Path tempDir) {
        // SheepEntity's always-written Color NBT and WolfEntity's
        // CollarColor: the store shifts by one so 0 stays the absent
        // marker — a black collar (metadata 0) must survive.
        Path file = tempDir.resolve("mobs.bin");
        MobDataStore store = new MobDataStore(file);
        List<MobManager.MobSnapshot> saved = List.of(
                new MobManager.MobSnapshot("SHEEP", 1.5, 4.0, 1.5, 0.0f, 8.0f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, 0, false, 1, 0, 6 + 1)),
                new MobManager.MobSnapshot("WOLF", 3.5, 4.0, 1.5, 0.0f, 8.0f,
                        new MobManager.MobStateExtras(false, 0, 0, 0, 0, 0,
                                0, 0, false, 1, 0, 0 + 1)));
        store.save(saved);
        List<MobManager.MobSnapshot> loaded = store.load();
        assertEquals(6 + 1, loaded.get(0).extras().dyeColor(), "the pink fleece's byte");
        assertEquals(0 + 1, loaded.get(1).extras().dyeColor(), "the black collar's byte");

        // The restore lands on the bodies.
        Rig rig = rig(23);
        rig.manager().restoreAll(loaded);
        MobEntity sheep = rig.manager().all().stream()
                .filter(m -> m.type() == MobType.SHEEP).findFirst().orElseThrow();
        MobEntity wolf = rig.manager().all().stream()
                .filter(m -> m.type() == MobType.WOLF).findFirst().orElseThrow();
        assertEquals(6, sheep.sheepFleeceColor(), "the pink fleece restored");
        assertEquals(0, wolf.wolfCollarColor(), "the black collar restored");
    }
}
