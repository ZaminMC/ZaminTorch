package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stats family (the stats slice — Stats/PlayerStats 1:1): the key
 * vocabulary over the reference's own registration rows (the general band
 * verbatim, the item and block families over the dotted identifier scheme,
 * the entity kill pair over the old capitalized Entities keys, the merge
 * table keeping the literal forms on their family's base member), and the
 * per-session counter model the hooks drive (increment/set/get, the dirty
 * take, the snapshot/load round trip the persistence walk carries).
 */
class StatsTest {

    @Test
    void theGeneralBandCarriesTheReferenceKeys() {
        // The four ledgered rows ride the same band: the cake, the pot, the
        // trapped chest, the ender chest.
        assertEquals("stat.cakeSlicesEaten", EngineStats.CAKE_SLICES_EATEN);
        assertEquals("stat.flowerPotted", EngineStats.FLOWERS_POTTED);
        assertEquals("stat.trappedChestTriggered", EngineStats.TRAPPED_CHESTS_TRIGGERED);
        assertEquals("stat.enderchestOpened", EngineStats.ENDER_CHESTS_OPENED);
        // The counters the client's screen reads (the reference's own rows).
        assertEquals("stat.chestOpened", EngineStats.CHESTS_OPENED);
        assertEquals("stat.workbenchInteraction", EngineStats.CRAFTING_TABLE_INTERACTIONS);
        assertEquals("stat.furnaceInteraction", EngineStats.FURNACE_INTERACTIONS);
        assertEquals("stat.brewingstandInteraction", EngineStats.BREWING_STAND_INTERACTIONS);
        assertEquals("stat.beaconInteraction", EngineStats.BEACON_INTERACTIONS);
        assertEquals("stat.noteblockPlayed", EngineStats.NOTE_BLOCKS_PLAYED);
        assertEquals("stat.noteblockTuned", EngineStats.NOTE_BLOCKS_TUNED);
        assertEquals("stat.recordPlayed", EngineStats.RECORDS_PLAYED);
        assertEquals("stat.itemEnchanted", EngineStats.ITEMS_ENCHANTED);
        assertEquals("stat.drop", EngineStats.DROPS);
        assertEquals("stat.damageDealt", EngineStats.DAMAGE_DEALT);
        assertEquals("stat.damageTaken", EngineStats.DAMAGE_TAKEN);
        assertEquals("stat.deaths", EngineStats.DEATHS);
        assertEquals("stat.mobKills", EngineStats.MOBS_KILLED);
        assertEquals("stat.playOneMinute", EngineStats.MINUTES_PLAYED);
        assertEquals("stat.leaveGame", EngineStats.GAMES_LEFT);
        assertEquals("stat.timeSinceDeath", EngineStats.TIME_SINCE_DEATH);
        assertEquals("stat.cauldronFilled", EngineStats.CAULDRONS_FILLED);
        assertEquals("stat.cauldronUsed", EngineStats.CAULDRONS_USED);
        assertEquals("stat.dropperInspected", EngineStats.DROPPERS_INSPECTED);
        assertEquals("stat.hopperInspected", EngineStats.HOPPERS_INSPECTED);
        assertEquals("stat.dispenserInspected", EngineStats.DISPENSERS_INSPECTED);
        // The band carries every key exactly once.
        assertEquals(48, EngineStats.GENERAL.size());
        assertEquals(EngineStats.GENERAL.size(),
                EngineStats.GENERAL.stream().distinct().count());
    }

    @Test
    void theItemFamiliesComposeOverTheDottedScheme() {
        // Stats.translationKey: the identifier with the colon flattened.
        assertEquals("stat.mineBlock.minecraft.stone",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:stone")));
        assertEquals("stat.craftItem.minecraft.planks",
                EngineStats.craftItemOf(Identifier.parse("minecraft:oak_planks")));
        assertEquals("stat.useItem.minecraft.bucket",
                EngineStats.useItemOf(Identifier.parse("minecraft:bucket")));
        assertEquals("stat.breakItem.minecraft.iron_pickaxe",
                EngineStats.breakItemOf(Identifier.parse("minecraft:iron_pickaxe")));
        // Slice L4 — the state families fold onto their registry Block's
        // row (the old "state keeps its own row" claim was wrong: the
        // 1.8.8 client DROPS unknown stat keys, StatList.getOneStat's
        // null walk).
        assertEquals("stat.mineBlock.minecraft.oak_stairs",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:oak_stairs_east")));
    }

    @Test
    void theRegistrySchemeKeepsEveryBlockOnItsOwnRow() {
        // Slice L4 — StatList.initMineStats 1:1: one row per registry
        // BLOCK, keyed by the 1.8.8 registry name. The old merge table
        // (the flowing fluids on the source, the lit/powered forms on
        // their plain forms, the grass/farmland on dirt, the doubles on
        // their singles) was the engine's own simplification — vanilla
        // keeps a row for EVERY registry Block, and the 1.8.8 client
        // drops any key outside its own registry.
        assertEquals("stat.mineBlock.minecraft.flowing_water",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:flowing_water")));
        assertEquals("stat.mineBlock.minecraft.flowing_water",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:flowing_water_2")));
        assertEquals("stat.mineBlock.minecraft.flowing_lava",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:flowing_lava")));
        // The lit and powered forms on their OWN registry rows.
        assertEquals("stat.mineBlock.minecraft.lit_furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_lit")));
        assertEquals("stat.mineBlock.minecraft.furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_north")));
        assertEquals("stat.mineBlock.minecraft.lit_furnace",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:furnace_lit_west")));
        // The chest pair's facing states (Slice L2/L3's folds ride the
        // strip now — the plain family on the chest row).
        assertEquals("stat.mineBlock.minecraft.chest",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:chest_north")));
        assertEquals("stat.mineBlock.minecraft.trapped_chest",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:trapped_chest_east")));
        assertEquals("stat.mineBlock.minecraft.lit_redstone_ore",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:lit_redstone_ore")));
        assertEquals("stat.mineBlock.minecraft.powered_repeater",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:powered_repeater")));
        assertEquals("stat.mineBlock.minecraft.powered_comparator",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:powered_comparator")));
        assertEquals("stat.mineBlock.minecraft.lit_redstone_lamp",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:lit_redstone_lamp")));
        assertEquals("stat.mineBlock.minecraft.lit_pumpkin",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:lit_pumpkin")));
        // The grass and the farmland on their own rows, the doubles on
        // their own (43/125 are registry Blocks in their own right).
        assertEquals("stat.mineBlock.minecraft.grass",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:grass_block")));
        assertEquals("stat.mineBlock.minecraft.farmland",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:farmland")));
        assertEquals("stat.mineBlock.minecraft.double_stone_slab",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:double_stone_slab")));
        assertEquals("stat.mineBlock.minecraft.double_wooden_slab",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:double_wooden_slab")));
        // The redstone torch on its own row (76 is the lit form's own
        // registry name — the engine's redstone_torch id IS the lit one).
        assertEquals("stat.mineBlock.minecraft.redstone_torch",
                EngineStats.mineBlockOf(Identifier.parse("minecraft:redstone_torch")));
    }

    @Test
    void theEntityKeysCarryTheReferenceBand() {
        // The old capitalized Entities keys (the quirky rows verbatim).
        assertEquals("PigZombie", EngineStats.entityKeyOf("ZOMBIE_PIGMAN"));
        assertEquals("Ozelot", EngineStats.entityKeyOf("OCELOT"));
        assertEquals("MushroomCow", EngineStats.entityKeyOf("MOOSHROOM"));
        assertEquals("EntityHorse", EngineStats.entityKeyOf("HORSE"));
        assertEquals("LavaSlime", EngineStats.entityKeyOf("MAGMA_CUBE"));
        assertEquals("Zombie", EngineStats.entityKeyOf("ZOMBIE"));
        assertEquals("Creeper", EngineStats.entityKeyOf("CREEPER"));
        // The kill pair's composition.
        assertEquals("stat.killEntity.Zombie", EngineStats.killEntityOf("Zombie"));
        assertEquals("stat.entityKilledBy.Creeper", EngineStats.killedByEntityOf("Creeper"));
        // An unknown type answers nothing; the golem pair carries no kill
        // rows at all (the reference's spawn-egg-only registration band).
        assertEquals(null, EngineStats.entityKeyOf("UNKNOWN"));
        assertEquals(null, EngineStats.entityKeyOf("IRON_GOLEM"));
        assertEquals(null, EngineStats.entityKeyOf("SNOW_GOLEM"));
    }

    @Test
    void theCountersCarryThePlayerStatsModel() {
        PlayerStats stats = new PlayerStats();
        // The never-written counter reads 0.
        assertEquals(0, stats.get(EngineStats.CHESTS_OPENED));
        // increment/set/get 1:1.
        stats.increment(EngineStats.CHESTS_OPENED, 1);
        stats.increment(EngineStats.CHESTS_OPENED, 1);
        stats.increment(EngineStats.DAMAGE_TAKEN, 7);
        assertEquals(2, stats.get(EngineStats.CHESTS_OPENED));
        assertEquals(7, stats.get(EngineStats.DAMAGE_TAKEN));
        stats.set(EngineStats.CHESTS_OPENED, 10);
        assertEquals(10, stats.get(EngineStats.CHESTS_OPENED));
        // takeDirty 1:1: the changed counters, then the set clears.
        Map<String, Integer> dirty = stats.takeDirty();
        assertEquals(2, dirty.size());
        assertEquals(10, dirty.get(EngineStats.CHESTS_OPENED));
        assertTrue(stats.takeDirty().isEmpty(), "the second take answers nothing");
        // The increments after a take dirty only the new writes.
        stats.increment(EngineStats.DEATHS, 1);
        assertEquals(1, stats.takeDirty().size());
        // The snapshot/load round trip (the persistence walk).
        Map<String, Integer> saved = stats.snapshot();
        PlayerStats restored = new PlayerStats();
        restored.load(saved);
        assertEquals(stats.get(EngineStats.CHESTS_OPENED), restored.get(EngineStats.CHESTS_OPENED));
        assertEquals(stats.get(EngineStats.DEATHS), restored.get(EngineStats.DEATHS));
        assertEquals(stats.get(EngineStats.DAMAGE_TAKEN), restored.get(EngineStats.DAMAGE_TAKEN));
        // The death clock: ticks accumulate until a death resets.
        stats.noteDeath();
        assertEquals(0, stats.get(EngineStats.TIME_SINCE_DEATH));
        stats.tickTimeSinceDeath();
        stats.tickTimeSinceDeath();
        assertEquals(2, stats.get(EngineStats.TIME_SINCE_DEATH));
        stats.noteDeath();
        assertEquals(0, stats.get(EngineStats.TIME_SINCE_DEATH));
        // Null and zero writes never land (the death clock's own writes
        // cleared first — its set() dirties like every write).
        stats.takeDirty();
        stats.increment(null, 1);
        stats.increment(EngineStats.DROPS, 0);
        assertTrue(stats.takeDirty().isEmpty());
    }
}
