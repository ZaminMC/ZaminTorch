package net.zamin.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdentifierTest {

    @Test
    void parsesNamespaceAndValue() {
        Identifier id = Identifier.parse("minecraft:stone");
        assertEquals("minecraft", id.namespace());
        assertEquals("stone", id.value());
        assertEquals("minecraft:stone", id.toString());
    }

    @Test
    void defaultsToMinecraftNamespace() {
        assertEquals("minecraft", Identifier.parse("dirt").namespace());
    }

    @Test
    void rejectsUppercaseAndIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> Identifier.parse("Minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> Identifier.parse("minecraft:Stone"));
        assertThrows(IllegalArgumentException.class, () -> Identifier.parse("minecraft:sto ne"));
        assertThrows(IllegalArgumentException.class, () -> Identifier.parse(":stone"));
        assertThrows(IllegalArgumentException.class, () -> Identifier.parse("minecraft:"));
    }

    @Test
    void ordersByNamespaceThenValue() {
        assertEquals(-1, Identifier.parse("a:x").compareTo(Identifier.parse("b:x")));
        assertEquals(-1, Identifier.parse("a:x").compareTo(Identifier.parse("a:y")));
    }
}
