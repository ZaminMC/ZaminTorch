package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wire-id conformance guard: every clientbound play-state constant this
 * adapter declares must equal the community-verified protocol 47 mapping
 * (PrismarineJS/minecraft-data pc/1.8 protocol.json, cross-checked against
 * the real 1.8.8 client's decoder).
 *
 * <p>This test exists because ids drifted twice: an Unload Chunk was
 * written at 0x1D — which the real client decodes as Entity Effect, and it
 * died with "packet 29 (ib), 3 bytes extra" the moment a chunk left the
 * view distance — and World Particles sat at 0x2B (Game State Change) where
 * the lenient mineflayer parser never noticed. The mapping table below is
 * the client's own decoder table; if a constant drifts again, this file
 * fails before a player does.</p>
 *
 * <p>New clientbound packets: add the constant AND its row here, or the
 * build breaks (that is the point — the row is the verification step).</p>
 */
class Protocol18ConformanceTest {

    /** The protocol 47 clientbound play mapping, community-verified. */
    private static final Map<String, Integer> EXPECTED = buildExpected();

    private static Map<String, Integer> buildExpected() {
        Map<String, Integer> m = new HashMap<>();
        m.put("S2C_KEEP_ALIVE", 0x00);
        m.put("S2C_JOIN_GAME", 0x01);
        m.put("S2C_CHAT", 0x02);
        m.put("S2C_TIME_UPDATE", 0x03);
        m.put("S2C_SPAWN_POSITION", 0x05);
        m.put("S2C_UPDATE_HEALTH", 0x06);
        m.put("S2C_RESPAWN", 0x07);
        m.put("S2C_PLAYER_POSITION_AND_LOOK", 0x08);
        m.put("S2C_ANIMATION", 0x0B);
        m.put("S2C_NAMED_SPAWN", 0x0C);
        m.put("S2C_COLLECT_ITEM", 0x0D);
        m.put("S2C_SPAWN_ENTITY", 0x0E);
        m.put("S2C_ENTITY_EQUIPMENT", 0x04);
        m.put("S2C_SPAWN_MOB", 0x0F);
        m.put("S2C_ENTITY_VELOCITY", 0x12);
        m.put("S2C_DESTROY_ENTITIES", 0x13);
        m.put("S2C_REL_ENTITY_MOVE", 0x15);
        m.put("S2C_ENTITY_LOOK", 0x16);
        m.put("S2C_REL_ENTITY_MOVE_LOOK", 0x17);
        m.put("S2C_ENTITY_TELEPORT", 0x18);
        m.put("S2C_ENTITY_HEAD_LOOK", 0x19);
        m.put("S2C_ENTITY_STATUS", 0x1A);
        m.put("S2C_ENTITY_METADATA", 0x1C);
        m.put("S2C_CHUNK_DATA", 0x21);
        m.put("S2C_BLOCK_CHANGE", 0x23);
        m.put("S2C_EXPLOSION", 0x27);
        m.put("S2C_NAMED_SOUND_EFFECT", 0x29);
        m.put("S2C_WORLD_PARTICLES", 0x2A);
        m.put("S2C_CHANGE_GAME_STATE", 0x2B);
        m.put("S2C_PLAYER_ABILITIES", 0x39);
        m.put("S2C_TAB_COMPLETE", 0x3A);
        m.put("S2C_OPEN_WINDOW", 0x2D);
        m.put("S2C_CLOSE_WINDOW", 0x2E);
        m.put("S2C_SET_SLOT", 0x2F);
        m.put("S2C_WINDOW_ITEMS", 0x30);
        m.put("S2C_WINDOW_PROPERTY", 0x31);
        m.put("S2C_CONFIRM_TRANSACTION", 0x32);
        m.put("S2C_DISCONNECT", 0x40);
        m.put("S2C_COMBAT_EVENT", 0x42);
        // Not play-state packets, same constant class: the login state's
        // disconnect/success (0x02 is Chat in play — states differ) and the
        // status state's response/pong.
        m.put("S2C_LOGIN_SUCCESS", 0x02);
        m.put("S2C_LOGIN_DISCONNECT", 0x00);
        m.put("S2C_STATUS_RESPONSE", 0x00);
        m.put("S2C_STATUS_PONG", 0x01);
        return Map.copyOf(m);
    }

    @Test
    void everyDeclaredClientboundConstantMatchesTheRealClientMapping() throws Exception {
        Map<String, Field> constants = declaredS2CConstants();
        for (Map.Entry<String, Integer> expected : EXPECTED.entrySet()) {
            Field field = constants.get(expected.getKey());
            if (field == null) {
                // The row exists but the constant was renamed: the table is stale.
                throw new AssertionError("Conformance table row " + expected.getKey()
                        + " has no matching constant in Protocol18 — drop the stale row.");
            }
            int actual = field.getInt(null);
            assertEquals(expected.getValue().intValue(), actual,
                    expected.getKey() + " must stay at 0x" + Integer.toHexString(expected.getValue())
                    + " (the 1.8.8 client's decoder table)");
        }
        // Every constant the adapter declares must be in the table (no
        // unwired ids can reach the wire unnamed).
        for (String declared : constants.keySet()) {
            assertTrue(EXPECTED.containsKey(declared),
                    declared + " is declared in Protocol18 but absent from the conformance"
                    + " table — add the 1.8.8-verified id row (the Unload Chunk class of bug).");
        }
    }

    /**
     * The specific regression: 0x1D is Entity Effect on this protocol —
     * no clientbound constant may claim it (the 1.9-era Unload Chunk).
     */
    @Test
    void noConstantClaimsTheEntityEffectSlot() throws Exception {
        for (Field field : Protocol18.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || field.getType() != int.class
                    || !field.getName().startsWith("S2C_")) {
                continue;
            }
            field.setAccessible(true);
            assertFalse(field.getInt(null) == 0x1D,
                    field.getName() + " claims 0x1D — that slot is Entity Effect on protocol 47;"
                    + " the real client dies decoding it (the Unload Chunk crash of v0.2.0-dev.4)");
        }
    }

    private static Map<String, Field> declaredS2CConstants() {
        Map<String, Field> result = new HashMap<>();
        for (Field field : Protocol18.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == int.class
                    && field.getName().startsWith("S2C_")) {
                field.setAccessible(true);
                result.put(field.getName(), field);
            }
        }
        return result;
    }
}
