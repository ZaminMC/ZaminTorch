package net.zaminmc.torch.server.brewing;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.potion.PotionHelper;
import net.zaminmc.torch.util.Identifier;

import java.util.Map;

/**
 * The brewing ingredient surface (Slice 11e — the reference's
 * {@code Item.potionIngredient} field family, reference/1.8.8 item/Item
 * .java lines 568-676): every item that answers {@code isPotionIngredient}
 * with its own flag recipe string. The map is the reference's own
 * registration table, item for item.
 *
 * <p>The pufferfish (Slice 11i — {@code FishItem.asPotionIngredient} line
 * 33-35) answers by the metadata-3 variant on either fish item, the
 * reference's own Type dispatch.</p>
 */
public final class BrewIngredients {

    private static final Map<Identifier, String> RECIPES = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:gunpowder"), PotionHelper.GUNPOWDER),
            Map.entry(Identifier.parse("minecraft:redstone"), PotionHelper.REDSTONE),
            Map.entry(Identifier.parse("minecraft:glowstone_dust"), PotionHelper.GLOWSTONE),
            Map.entry(Identifier.parse("minecraft:sugar"), PotionHelper.SUGAR),
            Map.entry(Identifier.parse("minecraft:ghast_tear"), PotionHelper.GHAST_TEAR),
            // The nether wart's own recipe (Item.java line 658: "+4").
            Map.entry(Identifier.parse("minecraft:nether_wart"), "+4"),
            Map.entry(Identifier.parse("minecraft:spider_eye"), PotionHelper.SPIDER_EYE),
            Map.entry(Identifier.parse("minecraft:fermented_spider_eye"),
                    PotionHelper.FERMENTED_SPIDER_EYE),
            Map.entry(Identifier.parse("minecraft:blaze_powder"), PotionHelper.BLAZE_POWDER),
            Map.entry(Identifier.parse("minecraft:magma_cream"), PotionHelper.MAGMA_CREAM),
            Map.entry(Identifier.parse("minecraft:glistering_melon"),
                    PotionHelper.GLISTERING_MELON),
            Map.entry(Identifier.parse("minecraft:golden_carrot"), PotionHelper.GOLDEN_CARROT),
            Map.entry(Identifier.parse("minecraft:rabbit_foot"), PotionHelper.JUMP_BOOST));

    private BrewIngredients() {
    }

    /** The ingredient's recipe string (the reference's asPotionIngredient). */
    public static String recipeOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        // The pufferfish arm (FishItem.asPotionIngredient lines 33-35): the
        // metadata-3 variant of either fish item — Type.byItem reads the
        // metadata of any FishItem, cooked or raw.
        if (net.zaminmc.torch.server.item.FishItems.isPufferfish(
                stack.type().identifier(), stack.damage())) {
            return PotionHelper.PUFFERFISH;
        }
        return RECIPES.get(stack.type().identifier());
    }

    /** Whether the stack is a brewing ingredient (isPotionIngredient). */
    public static boolean isIngredient(ItemStack stack) {
        return recipeOf(stack) != null;
    }
}
