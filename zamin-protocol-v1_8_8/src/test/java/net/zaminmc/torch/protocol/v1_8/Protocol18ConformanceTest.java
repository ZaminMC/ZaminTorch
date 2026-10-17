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
        m.put("S2C_HELD_ITEM_CHANGE", 0x09);
        m.put("S2C_ANIMATION", 0x0B);
        m.put("S2C_NAMED_SPAWN", 0x0C);
        m.put("S2C_COLLECT_ITEM", 0x0D);
        m.put("S2C_SPAWN_ENTITY", 0x0E);
        m.put("S2C_ENTITY_EQUIPMENT", 0x04);
        m.put("S2C_SPAWN_MOB", 0x0F);
        m.put("S2C_SPAWN_PAINTING", 0x10);
        m.put("S2C_SPAWN_XP_ORB", 0x11);
        m.put("S2C_ENTITY_VELOCITY", 0x12);
        m.put("S2C_DESTROY_ENTITIES", 0x13);
        m.put("S2C_REL_ENTITY_MOVE", 0x15);
        m.put("S2C_ENTITY_LOOK", 0x16);
        m.put("S2C_REL_ENTITY_MOVE_LOOK", 0x17);
        m.put("S2C_ENTITY_TELEPORT", 0x18);
        m.put("S2C_ENTITY_HEAD_LOOK", 0x19);
        m.put("S2C_ENTITY_STATUS", 0x1A);
        m.put("S2C_ENTITY_METADATA", 0x1C);
        m.put("S2C_ENTITY_EFFECT", 0x1D);
        m.put("S2C_REMOVE_ENTITY_EFFECT", 0x1E);
        m.put("S2C_ATTACH_ENTITY", 0x1B);
        m.put("S2C_SET_EXPERIENCE", 0x1F);
        m.put("S2C_CHUNK_DATA", 0x21);
        m.put("S2C_BLOCK_CHANGE", 0x23);
        m.put("S2C_BLOCK_ACTION", 0x24);
        // Block Break Animation: #37 in the 1.8.8 client's own
        // NetworkProtocol registration order (reference/1.8.8 — the
        // decoder table the real client resolves ids through). This row
        // sat at 0x28 for three slices — the Effect packet's slot —
        // because the write-side payload happened to parse when the
        // client skipped it; the reference's registration order is the
        // authority (the drift this table exists to catch, caught late).
        m.put("S2C_BLOCK_MINING_PROGRESS", 0x25);
        m.put("S2C_EXPLOSION", 0x27);
        // Effect / world event: #40 in the same registration order — the
        // 2002 potion splash rides it.
        m.put("S2C_WORLD_EVENT", 0x28);
        m.put("S2C_NAMED_SOUND_EFFECT", 0x29);
        m.put("S2C_WORLD_PARTICLES", 0x2A);
        m.put("S2C_CHANGE_GAME_STATE", 0x2B);
        // Spawn Global Entity: #42 in the registration order (right after
        // GameEvent) — the lightning bolt's spawn (Slice 20c).
        m.put("S2C_SPAWN_GLOBAL_ENTITY", 0x2C);
        m.put("S2C_PLAYER_ABILITIES", 0x39);
        m.put("S2C_TAB_COMPLETE", 0x3A);
        m.put("S2C_PLUGIN_MESSAGE", 0x3F);
        m.put("S2C_UPDATE_SIGN", 0x33);
        // Update Block Entity: #54 in the same registration order
        // (NetworkProtocol line 173 — after MapData 0x34, before
        // OpenSignEditor 0x36): the spawner's spinning body rides it.
        m.put("S2C_UPDATE_BLOCK_ENTITY", 0x35);
        m.put("S2C_OPEN_WINDOW", 0x2D);
        m.put("S2C_CLOSE_WINDOW", 0x2E);
        m.put("S2C_SET_SLOT", 0x2F);
        m.put("S2C_WINDOW_ITEMS", 0x30);
        m.put("S2C_WINDOW_PROPERTY", 0x31);
        m.put("S2C_CONFIRM_TRANSACTION", 0x32);
        // Client Statistics 0x37 (Slice 51's stats slice): the stats-family
        // wire row the Slice 51 commit left out of the conformance table.
        m.put("S2C_CLIENT_STATISTICS", 0x37);
        // Player Info 0x38 (Slice 84 — the tab-list family): the protocol-47
        // id of PlayerInfoS2CPacket (0x39 is Player Abilities, 0x3A Tab-Complete).
        m.put("S2C_PLAYER_INFO", 0x38);
        // The scoreboard wire family (Slice 85): Scoreboard Objective 0x3B,
        // Update Score 0x3C, Display Scoreboard 0x3D, Teams 0x3E (0x3F is
        // Plugin Message — the family's own right-hand neighbor).
        m.put("S2C_SCOREBOARD_OBJECTIVE", 0x3B);
        m.put("S2C_UPDATE_SCORE", 0x3C);
        m.put("S2C_DISPLAY_SCOREBOARD", 0x3D);
        m.put("S2C_TEAMS", 0x3E);
        // World Border 0x44 (Slice 86): after Camera 0x43, before Title 0x45
        // (the reference's WorldBorderS2CPacket registration slot).
        m.put("S2C_WORLD_BORDER", 0x44);
        // Title 0x45 (Slice 87 — the titles wave): TitlesS2CPacket's own
        // slot (0x46 is Collect Item between them).
        m.put("S2C_TITLE", 0x45);
        // Player List Header/Footer 0x47 (Slice 87): TabListS2CPacket's slot
        // after Collect Item 0x46.
        m.put("S2C_PLAYER_LIST_HEADER_FOOTER", 0x47);
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
     * the only constant that may claim it is the entity effect itself
     * (the 1.9-era Unload Chunk that killed real clients).
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
            if (field.getInt(null) == 0x1D) {
                assertEquals("S2C_ENTITY_EFFECT", field.getName(),
                        "only the Entity Effect constant may claim 0x1D — the real client"
                        + " dies decoding anything else there (the Unload Chunk crash"
                        + " of v0.2.0-dev.4)");
            }
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
