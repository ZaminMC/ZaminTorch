package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;

/**
 * The fish item family (Slice 11i — the reference port of
 * {@code item/FishItem.java}): the raw fish (legacy 349) and the cooked
 * fish (legacy 350) whose four variants ride the damage field — 0 cod,
 * 1 salmon, 2 clownfish, 3 pufferfish — exactly like the reference's
 * {@code FishItem.Type} dispatch.
 *
 * <p>The reference's own quirks are kept 1:1: {@code byId} answers COD for
 * an unknown metadata (the null guard), the cooked item of a
 * non-cookable variant answers the RAW values ({@code cooked &&
 * canBeCooked()} both gate), and the type dispatch reads the metadata of
 * EITHER fish item — a cooked fish with pufferfish metadata eats (and
 * poisons) like a raw pufferfish.</p>
 */
public final class FishItems {

    private FishItems() {
    }

    /** The raw fish item (the reference's Item.java line 635: 349). */
    public static final Identifier FISH = Identifier.parse("minecraft:fish");
    /** The cooked fish item (the reference's Item.java line 636: 350). */
    public static final Identifier COOKED_FISH = Identifier.parse("minecraft:cooked_fish");

    /**
     * The four variants (the reference's own table): raw/cooked hunger and
     * saturation, with the clownfish and the pufferfish non-cookable
     * (the reference's own two-field constructor).
     */
    public enum Type {
        COD(0, 2, 0.1f, 5, 0.6f),
        SALMON(1, 2, 0.1f, 6, 0.8f),
        CLOWNFISH(2, 1, 0.1f, 0, 0.0f),
        PUFFERFISH(3, 1, 0.1f, 0, 0.0f);

        final int id;
        final int hungerRaw;
        final float saturationRaw;
        final int hungerCooked;
        final float saturationCooked;
        final boolean cookable;

        Type(int id, int hungerRaw, float saturationRaw,
             int hungerCooked, float saturationCooked) {
            this.id = id;
            this.hungerRaw = hungerRaw;
            this.saturationRaw = saturationRaw;
            this.hungerCooked = hungerCooked;
            this.saturationCooked = saturationCooked;
            this.cookable = hungerCooked > 0 || saturationCooked > 0.0f;
        }
    }

    /** @return whether the identifier is one of the two fish items. */
    public static boolean isFishFamily(Identifier identifier) {
        return FISH.equals(identifier) || COOKED_FISH.equals(identifier);
    }

    /**
     * The variant lookup ({@code Type.byId}): an unknown metadata answers
     * COD — the reference's own null guard.
     */
    public static Type byId(int metadata) {
        for (Type type : Type.values()) {
            if (type.id == metadata) {
                return type;
            }
        }
        return Type.COD;
    }

    /**
     * The nutrition dispatch (getHungerPoints/getSaturation lines 21-30):
     * the cooked item of a cookable variant answers the cooked values,
     * everything else the raw values — the raw item never sees the cooked
     * arm, and a cooked clownfish/pufferfish falls back to the raw pair.
     */
    public static Foods.Nutrition nutritionOf(Identifier item, int metadata) {
        Type type = byId(metadata);
        boolean cooked = COOKED_FISH.equals(item);
        if (cooked && type.cookable) {
            return new Foods.Nutrition(type.hungerCooked, type.saturationCooked);
        }
        return new Foods.Nutrition(type.hungerRaw, type.saturationRaw);
    }

    /**
     * The pufferfish arm ({@code Type.byItem(stack) == PUFFERFISH}): the
     * metadata-3 variant on EITHER fish item — the reference's dispatch
     * reads the metadata of any FishItem, cooked or raw.
     */
    public static boolean isPufferfish(Identifier item, int metadata) {
        return isFishFamily(item) && byId(metadata) == Type.PUFFERFISH;
    }
}
