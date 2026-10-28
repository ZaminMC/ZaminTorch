package net.zaminmc.torch.server.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The food nutrition fold audit's pins (the verifier's seventh pass): every
 * row of the food table re-pinned against the reference's own apply-time
 * law — HungerManager.add lines 20-23, {@code saturation += food x modifier
 * x 2}, the Item.java FoodItem registration's modifier folded by the hunger
 * manager at every bite. The pre-audit table had ridden one dataset
 * half-fold (the raw chicken's 0.6 where the reference folds 2 x 0.3 x 2 =
 * 1.2), six registered foods carried no row at all (the cookie, the melon,
 * the poisonous potato, the pumpkin pie, the mutton pair), and the fish
 * family answered the raw modifier where the Nutrition record's own
 * convention is the RESTORED saturation. The always-edible law rides beside
 * the table: the golden apple's single setAlwaysEdible registration
 * (Item.java line 604) against FoodItem.startUsing line 59's
 * canEat(alwaysEdible) gate.
 */
class FoodNutritionParityTest {

    private static void assertNutrition(net.zaminmc.torch.item.ItemType type,
                                        int hunger, float saturation, String law) {
        var nutrition = Foods.nutritionOf(type).orElseThrow();
        assertEquals(hunger, nutrition.foodPoints(), law + " — hunger");
        assertEquals(saturation, nutrition.saturation(), 0.0001f, law + " — the fold");
    }

    @Test
    void theMeatBandFoldsItsModifer() {
        // Item.java lines 649-666: beef 3/0.3F, cooked 8/0.8F; the pork
        // pair 3/0.3F and 8/0.8F; the chicken pair 2/0.3F and 6/0.6F; the
        // rotten flesh 4/0.1F.
        assertNutrition(BuiltinItems.BEEF, 3, 1.8f, "beef 3 x 0.3 x 2");
        assertNutrition(BuiltinItems.COOKED_BEEF, 8, 12.8f, "cooked beef 8 x 0.8 x 2");
        assertNutrition(BuiltinItems.PORKCHOP, 3, 1.8f, "porkchop 3 x 0.3 x 2");
        assertNutrition(BuiltinItems.COOKED_PORKCHOP, 8, 12.8f, "cooked porkchop 8 x 0.8 x 2");
        assertNutrition(BuiltinItems.RAW_CHICKEN, 2, 1.2f,
                "the raw chicken's corrected fold 2 x 0.3 x 2 (the dataset's own 0.6 rode a half-fold)");
        assertNutrition(BuiltinItems.COOKED_CHICKEN, 6, 7.2f, "cooked chicken 6 x 0.6 x 2");
        assertNutrition(BuiltinItems.ROTTEN_FLESH, 4, 0.8f, "rotten flesh 4 x 0.1 x 2");
    }

    @Test
    void theCropAndStewBandFoldsItsModifer() {
        // Item.java: bread 5/0.6F (line 297), the apple 4/0.3F (line 539),
        // the carrot 3/0.6F (line 391, CropItem), the potato pair 1/0.3F
        // and 5/0.6F (lines 392-393), the golden carrot 6/1.2F (line 698),
        // the golden apple 4/1.2F (line 604), the stews 6/0.6F and
        // 10/0.6F (StewItem's own constructor, lines 282/413), the spider
        // eye 2/0.8F (line 664).
        assertNutrition(BuiltinItems.BREAD, 5, 6.0f, "bread 5 x 0.6 x 2");
        assertNutrition(BuiltinItems.APPLE, 4, 2.4f, "apple 4 x 0.3 x 2");
        assertNutrition(BuiltinItems.CARROT, 3, 3.6f, "carrot 3 x 0.6 x 2");
        assertNutrition(BuiltinItems.POTATO, 1, 0.6f, "potato 1 x 0.3 x 2");
        assertNutrition(BuiltinItems.BAKED_POTATO, 5, 6.0f, "baked potato 5 x 0.6 x 2");
        assertNutrition(BuiltinItems.GOLDEN_CARROT, 6, 14.4f, "golden carrot 6 x 1.2 x 2");
        assertNutrition(BuiltinItems.GOLDEN_APPLE, 4, 9.6f, "golden apple 4 x 1.2 x 2");
        assertNutrition(BuiltinItems.SPIDER_EYE, 2, 3.2f, "spider eye 2 x 0.8 x 2");
        assertNutrition(BuiltinItems.RABBIT, 3, 1.8f, "rabbit 3 x 0.3 x 2");
        assertNutrition(BuiltinItems.COOKED_RABBIT, 5, 6.0f, "cooked rabbit 5 x 0.6 x 2");
        assertNutrition(BuiltinItems.RABBIT_STEW, 10, 12.0f, "rabbit stew 10 x 0.6 x 2");
        assertNutrition(BuiltinItems.MUSHROOM_STEW, 6, 7.2f, "mushroom stew 6 x 0.6 x 2");
    }

    @Test
    void theSixRowsTheTableNeverCarried() {
        // The completion arm: registered foods that answered UNEATABLE
        // before the audit — the cookie (line 643: FoodItem(2, 0.1F)), the
        // melon slice (line 360: FoodItem(2, 0.3F)), the poisonous potato
        // (line 394: FoodItem(2, 0.3F) — its 60% poison effect row had sat
        // unreachable in the effect table), the pumpkin pie (line 400:
        // FoodItem(8, 0.3F)), and the mutton pair (lines 727-728:
        // FoodItem(2, 0.3F) and FoodItem(6, 0.8F) — smeltable since Slice
        // 11i yet never eatable).
        assertNutrition(BuiltinItems.COOKIE, 2, 0.4f, "cookie 2 x 0.1 x 2");
        assertNutrition(BuiltinItems.MELON, 2, 1.2f, "melon 2 x 0.3 x 2");
        assertNutrition(BuiltinItems.POISONOUS_POTATO, 2, 1.2f, "poisonous potato 2 x 0.3 x 2");
        assertNutrition(BuiltinItems.PUMPKIN_PIE, 8, 4.8f, "pumpkin pie 8 x 0.3 x 2");
        assertNutrition(BuiltinItems.MUTTON, 2, 1.2f, "mutton 2 x 0.3 x 2");
        assertNutrition(BuiltinItems.COOKED_MUTTON, 6, 9.6f, "cooked mutton 6 x 0.8 x 2");
        // The poisonous potato's effect row now rides a reachable food.
        var effect = Foods.effectOf(BuiltinItems.POISONOUS_POTATO).orElseThrow();
        assertEquals(19, effect.effect(), "the poison effect id");
        assertEquals(0.6f, effect.chance(), 0.0001f, "the 60% roll");
    }

    @Test
    void theFishFamilyAnswersTheFoldedRestoration() {
        // FishItem.Type lines 65-68: the raw pair 2/0.1F and the cooked
        // pair 5/0.6F (cod), 2/0.1F and 6/0.8F (salmon), 1/0.1F both
        // (clownfish, pufferfish) — the hunger manager folds each at the
        // bite (HungerManager.add line 22). The fish keys by the item AND
        // the variant (FishItems' own dispatch — the one-arg table never
        // carried the family).
        assertEquals(2, net.zaminmc.torch.server.item.FishItems
                .nutritionOf(BuiltinItems.FISH.identifier(), 0).foodPoints());
        assertEquals(0.4f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.FISH.identifier(), 0).saturation(), 0.0001f,
                "raw cod's fold 2 x 0.1 x 2");
        assertEquals(0.4f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.FISH.identifier(), 1).saturation(), 0.0001f,
                "raw salmon's fold");
        assertEquals(0.2f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.FISH.identifier(), 2).saturation(), 0.0001f,
                "clownfish's fold");
        assertEquals(0.2f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.FISH.identifier(), 3).saturation(), 0.0001f,
                "pufferfish's fold");
        assertEquals(5, net.zaminmc.torch.server.item.FishItems
                .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 0).foodPoints());
        assertEquals(6.0f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 0).saturation(),
                0.0001f, "cooked cod's fold 5 x 0.6 x 2");
        assertEquals(9.6f, net.zaminmc.torch.server.item.FishItems
                        .nutritionOf(BuiltinItems.COOKED_FISH.identifier(), 1).saturation(),
                0.0001f, "cooked salmon's fold 6 x 0.8 x 2");
        // The damage-aware dispatch answers the same through Foods' own
        // two-arg consult (the eat path's own shape).
        var eaten = Foods.nutritionOf(BuiltinItems.FISH, 3).orElseThrow();
        assertEquals(1, eaten.foodPoints());
        assertEquals(0.2f, eaten.saturation(), 0.0001f, "the pufferfish through the eat consult");
    }

    @Test
    void theAlwaysEdibleLawRidesTheGoldenAppleAlone() {
        // Item.java line 604: the golden apple's setAlwaysEdible() — the
        // food family's single registration (FoodItem.startUsing line 59
        // consults canEat(this.alwaysEdible)).
        assertTrue(Foods.isAlwaysEdible(BuiltinItems.GOLDEN_APPLE));
        assertFalse(Foods.isAlwaysEdible(BuiltinItems.BREAD));
        assertFalse(Foods.isAlwaysEdible(BuiltinItems.COOKED_BEEF));
        assertFalse(Foods.isAlwaysEdible(BuiltinItems.MUSHROOM_STEW));
    }

    @Test
    void theNonFoodsAnswerEmpty() {
        // The else arms: the block items, the fuels and the filled buckets
        // never answer eatable (the milk bucket's drink is its own family).
        assertTrue(Foods.nutritionOf(BuiltinItems.DIRT).isEmpty());
        assertTrue(Foods.nutritionOf(BuiltinItems.COAL).isEmpty());
        assertTrue(Foods.nutritionOf(BuiltinItems.LAVA_BUCKET).isEmpty());
        assertTrue(Foods.nutritionOf(BuiltinItems.MILK_BUCKET).isEmpty());
        assertTrue(Foods.nutritionOf(BuiltinItems.CAKE).isEmpty(),
                "the cake bites by the block (CakeBlock.eat), never by the item");
    }
}
