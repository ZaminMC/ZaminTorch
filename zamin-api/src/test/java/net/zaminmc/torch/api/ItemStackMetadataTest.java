package net.zaminmc.torch.api;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The damage field's dual role (the historical 1.8 {@code Damage} value):
 * durability wear on durable items, variant metadata on everything else —
 * charcoal is coal with damage 1, red sand is sand with damage 1.
 */
class ItemStackMetadataTest {

    /** Minimal local item types (the api module has no registry dependency). */
    private static ItemType item(String name, int maxStackSize, int maxDurability) {
        Identifier id = Identifier.parse("minecraft:" + name);
        return new ItemType() {
            @Override
            public Identifier identifier() {
                return id;
            }

            @Override
            public int maxStackSize() {
                return maxStackSize;
            }

            @Override
            public int maxDurability() {
                return maxDurability;
            }
        };
    }

    private static final ItemType COAL = item("coal", 64, 0);
    private static final ItemType SAND = item("sand", 64, 0);
    private static final ItemType WOODEN_PICKAXE = item("wooden_pickaxe", 1, 59);

    @Test
    void nonDurableItemsCarryVariantMetadata() {
        ItemStack charcoal = new ItemStack(COAL, 1, 1);
        assertEquals(1, charcoal.damage(), "charcoal is coal with metadata 1");
        ItemStack redSand = new ItemStack(SAND, 3, 1);
        assertEquals(1, redSand.damage(), "red sand is sand with metadata 1");
    }

    @Test
    void metadataIsI16BoundedAndNonNegative() {
        assertThrows(IllegalArgumentException.class, () -> new ItemStack(COAL, 1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStack(COAL, 1, ItemStack.MAX_DAMAGE + 1));
        // The wire ceiling itself is representable.
        assertEquals(ItemStack.MAX_DAMAGE,
                new ItemStack(COAL, 1, ItemStack.MAX_DAMAGE).damage());
    }

    @Test
    void durableItemsStayDurabilityBounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStack(WOODEN_PICKAXE, 1, 60),
                "wear beyond max durability is still refused");
        assertEquals(59, new ItemStack(WOODEN_PICKAXE, 1, 59).damage());
    }

    @Test
    void variantStacksAreDistinctValues() {
        assertNotEquals(ItemStack.of(COAL, 1), new ItemStack(COAL, 1, 1),
                "coal and charcoal are different stacks");
        assertNotEquals(ItemStack.of(SAND, 1), new ItemStack(SAND, 1, 1),
                "sand and red sand are different stacks");
    }

    @Test
    void variantsKeepCountAndSplitSemantics() {
        ItemStack charcoal = new ItemStack(COAL, 8, 1);
        ItemStack split = charcoal.split(3);
        assertEquals(1, split.damage(), "splitting preserves the variant");
        assertEquals(3, split.count());
        ItemStack reduced = charcoal.withCount(5);
        assertEquals(1, reduced.damage(), "count changes keep the variant");
        assertEquals(5, reduced.count());
        assertTrue(charcoal.withCount(0).isEmpty());
    }
}
