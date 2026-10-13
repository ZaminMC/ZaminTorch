package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy id table's static-init integrity (the Slice 16c and 18d
 * lessons): a doubled Map.entry inside {@code Map.ofEntries} throws
 * IllegalArgumentException at the table's class initialization — a failure
 * that only surfaces when a join flow first serializes a chunk, killing
 * every integration test twenty seconds in. This test forces the static
 * init directly, so a duplicate row fails here in milliseconds with the
 * offending key in the message instead of deep inside a Netty callback.
 */
class LegacyBlockIdsIntegrityTest {

    @Test
    void theLegacyTablesInitializeWithoutADuplicateRow() {
        // Touching any member forces <clinit>; the duplicate key (if any)
        // names itself in the IllegalArgumentException.
        assertDoesNotThrow(() -> {
            for (Field field : LegacyBlockIds.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true);
                    field.get(null); // the read forces the initializer
                }
            }
            for (Method method : LegacyBlockIds.class.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 0) {
                    method.setAccessible(true);
                    method.invoke(null);
                }
            }
        }, "a doubled row or broken initializer must fail HERE, not in a join flow");
        // The table must answer at least the foundational members — the
        // map is live, not an empty fallback.
        assertTrue(LegacyBlockIds.legacyId(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone"))
                .isPresent(), "the stone row answers");
    }
}
