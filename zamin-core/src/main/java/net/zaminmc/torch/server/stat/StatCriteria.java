package net.zaminmc.torch.server.stat;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.crafting.CraftingRecipe;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.furnace.FurnaceRecipes;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.scoreboard.ScoreboardCriterion;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The stat-criteria registration walk (Stats.init 1:1 over the engine's
 * own registries): one {@link ScoreboardCriterion} per stat key, named by
 * the key itself — the reference's Stat constructor registers its
 * criterion into {@code ScoreboardCriterion.BY_NAME} as each stat is
 * declared, so {@code /scoreboard objectives add <name> <stat-key>}
 * resolves and the increment ride follows the criterion's objectives.
 *
 * <p>The bands, each exactly the reference's own gate: the general
 * table (Stats' declared rows), the 34 achievements (Achievements.ALL's
 * order), the entity kill pair (one pair per reference's spawn-egg band
 * key), the mined family (the reference: every block with an item form
 * and {@code hasStats()} — the engine walks its whole block registry
 * through the merge fold, the {@code hasStats} exclusions (the fluids,
 * the wheat stages, the barrier) ledgered at the boundary), the used
 * family (Item.REGISTRY — every item), the broken family
 * ({@code isDamageable} — maxDurability past zero), and the crafted
 * family (the reference: the crafting recipe results union the
 * smelting outputs).</p>
 */
public final class StatCriteria {

    private StatCriteria() {
    }

    /** The registered stat keys (the reference's Stats.BY_KEY view). */
    private static final Set<String> STAT_KEYS = new LinkedHashSet<>();

    private static volatile boolean registered;

    /**
     * The one-time walk (the engine's Stats.init): registers a criterion
     * for every stat key in the vocabulary. Idempotent — repeated calls
     * (test boots) no-op.
     */
    public static synchronized void registerAll(CraftingService crafting, BlockRegistry blocks) {
        if (registered) {
            return;
        }
        registered = true;
        // The general band (Stats' declared rows, registration order).
        for (String key : EngineStats.GENERAL) {
            add(key);
        }
        // The achievement band (Achievements.ALL's own order).
        for (EngineAchievements.Achievement achievement : EngineAchievements.ALL) {
            add(achievement.key());
        }
        // The entity kill pair (the reference's spawn-egg band).
        for (String entityKey : EngineStats.entityKeyBand()) {
            add(EngineStats.killEntityOf(entityKey));
            add(EngineStats.killedByEntityOf(entityKey));
        }
        // The mined family: every registered block through the merge fold.
        for (BlockType type : blocks.all()) {
            add(EngineStats.mineBlockOf(type.identifier()));
        }
        // The used family: every item (Item.REGISTRY's own walk).
        for (ItemType item : BuiltinItems.all()) {
            add(EngineStats.useItemOf(item.identifier()));
        }
        // The broken family: the damageable items (isDamageable's own gate).
        for (ItemType item : BuiltinItems.all()) {
            if (item.maxDurability() > 0) {
                add(EngineStats.breakItemOf(item.identifier()));
            }
        }
        // The crafted family: the recipe results union the smelting outputs
        // (Stats.initItemsCraftedStats' own set union).
        Set<Identifier> crafted = new LinkedHashSet<>();
        for (CraftingRecipe recipe : crafting.recipes()) {
            crafted.add(recipe.result().item());
        }
        for (FurnaceRecipes.SmeltResult smelt : FurnaceRecipes.smeltingResults()) {
            crafted.add(smelt.output());
        }
        for (Identifier item : crafted) {
            add(EngineStats.craftItemOf(item));
        }
    }

    /** Registers one key (Stats.BY_KEY + the criterion's own BY_NAME row). */
    private static void add(String key) {
        if (STAT_KEYS.add(key)) {
            ScoreboardCriterion.forStat(key);
        }
    }

    /** @return whether the key belongs to the stat vocabulary (Stats.byKey != null). */
    public static boolean isStatKey(String key) {
        return key != null && STAT_KEYS.contains(key);
    }

    /** @return the registered stat keys in registration order (Stats.ALL's view). */
    public static List<String> allKeys() {
        return Collections.unmodifiableList(new ArrayList<>(STAT_KEYS));
    }
}
