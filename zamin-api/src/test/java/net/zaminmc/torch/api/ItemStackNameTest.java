package net.zaminmc.torch.api;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item NBT slice (§426 spirit): the custom display name — the historical
 * {@code tag.display.Name} anvil rename — rides the stack value, participates
 * in identity (a named sword never merges into an unnamed one), survives every
 * value transformation, and sanitizes itself (control characters out, length
 * bounded, blank means none).
 */
class ItemStackNameTest {

    /** Minimal local item types (the api module has no registry dependency). */
    private static ItemType item(String name, int maxStackSize, int maxDurability) {
        Identifier id = Identifier.parse("minecraft:" + name);
        return new ItemType() {
            @Override public Identifier identifier() { return id; }
            @Override public int maxStackSize() { return maxStackSize; }
            @Override public int maxDurability() { return maxDurability; }
        };
    }

    private static final ItemType STICK = item("stick", 64, 0);
    private static final ItemType SWORD = item("iron_sword", 1, 250);

    @Test
    void nameRidesEveryValueTransformation() {
        ItemStack named = new ItemStack(STICK, 32, 0, "Excalibur");
        assertEquals("Excalibur", named.withCount(7).displayName());
        assertEquals("Excalibur", named.withDamage(0).displayName());
        assertEquals("Excalibur", named.split(4).displayName());
        assertEquals("Excalibur", named.split(4).withCount(2).displayName());
        assertEquals(32, named.count(), "transformations never mutate the source");
    }

    @Test
    void mergeableRespectsTheName() {
        ItemStack named = new ItemStack(STICK, 1, 0, "Excalibur");
        ItemStack plain = new ItemStack(STICK, 1);
        assertFalse(ItemStack.mergeable(named, plain),
                "a named sword never merges into an unnamed one");
        assertTrue(ItemStack.mergeable(named, named.withCount(31)),
                "same name, same damage: mergeable");
        assertFalse(ItemStack.mergeable(named, new ItemStack(STICK, 1, 0, "Other")),
                "different names never merge");
        // The damage field still participates (the worn/fresh rule).
        assertFalse(ItemStack.mergeable(new ItemStack(SWORD, 1, 5, "Named"),
                new ItemStack(SWORD, 1, 6, "Named")));
    }

    @Test
    void namesSanitizeOnConstruction() {
        assertEquals("clean name", new ItemStack(STICK, 1, 0, "\u0007clean\u001B name\u0000")
                .displayName(), "control characters are stripped");
        assertNull(new ItemStack(STICK, 1, 0, "   ").displayName(), "blank means no name");
        assertNull(new ItemStack(STICK, 1, 0, "").displayName());
        String longName = "x".repeat(ItemStack.MAX_NAME_LENGTH + 10);
        assertEquals(ItemStack.MAX_NAME_LENGTH,
                new ItemStack(STICK, 1, 0, longName).displayName().length(),
                "overlong names are bounded, not rejected");
        assertTrue(new ItemStack(STICK, 1, 0, "§aGreen").displayName().contains("§a"),
                "format codes survive (the historical rename could carry them)");
    }

    @Test
    void withNameClearsAndSets() {
        ItemStack named = new ItemStack(STICK, 5, 0, "Excalibur");
        assertNull(named.withName(null).displayName(), "null clears");
        assertNull(named.withName("  ").displayName(), "blank clears");
        assertEquals("Stick of Truth", named.withName("Stick of Truth").displayName());
        assertEquals(5, named.withName(null).count(), "clearing keeps the rest");
    }

    @Test
    void emptyStacksRejectNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new ItemStack(null, 0, 0, "Excalibur"),
                "an empty stack stays canonical (§427)");
    }
}
