package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.auth.GameProfile;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave ON-1b's parser pins — the hasJoined body's whole grammar: the
 * undashed id, the name, the optional properties with their optional
 * signatures, the string escapes, and the strict rejections (a login is
 * never accepted on a half-parsed profile).
 */
class SessionProfileParserTest {

    private static final String NOTCH_ID = "069a79f444e94726a5befca90e38aaf5";

    @Test
    void theWholeBodyParsesWithProperties() {
        String body = "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"Notch\","
                + "\"properties\":[{\"name\":\"textures\",\"value\":\"aGVsbG8=\","
                + "\"signature\":\"c2lnbmF0dXJl\"}]}";
        GameProfile profile = SessionProfileParser.parse(body);
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), profile.id());
        assertEquals("Notch", profile.name());
        assertEquals(1, profile.properties().size());
        GameProfile.Property property = profile.properties().get(0);
        assertEquals("textures", property.name());
        assertEquals("aGVsbG8=", property.value());
        assertEquals("c2lnbmF0dXJl", property.signature());
        assertTrue(property.hasSignature());
    }

    @Test
    void thePropertiesArrayIsOptional() {
        String body = "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"Notch\"}";
        GameProfile profile = SessionProfileParser.parse(body);
        assertEquals(0, profile.properties().size());
    }

    @Test
    void theSignatureIsOptionalPerProperty() {
        String body = "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"Notch\","
                + "\"properties\":[{\"name\":\"textures\",\"value\":\"dmFsdWU=\"}]}";
        GameProfile profile = SessionProfileParser.parse(body);
        GameProfile.Property property = profile.properties().get(0);
        assertEquals("textures", property.name());
        assertEquals("dmFsdWU=", property.value());
        assertNull(property.signature());
        assertTrue(!property.hasSignature());
    }

    @Test
    void whitespaceAndUnknownMembersRideAlong() {
        // The endpoint's real bodies carry whitespace and may grow members
        // (the agent member, the profile actions) — unknown values are
        // read whole and dropped, never a rejection.
        String body = "  {\n \"id\" : \"" + NOTCH_ID + "\" ,\n \"name\":\"Notch\","
                + " \"agent\":\"minecraft\", \"deprecated\": false,"
                + " \"nested\": {\"a\":[1,2,{\"b\":\"c\"}]},"
                + " \"properties\":[ ] }  ";
        GameProfile profile = SessionProfileParser.parse(body);
        assertEquals("Notch", profile.name());
        assertEquals(0, profile.properties().size());
    }

    @Test
    void theStringEscapesParse() {
        String body = "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"N\\u00f6tch\\\\ \\\"q\\\"\","
                + "\"properties\":[{\"name\":\"te\\nx\\ttures\",\"value\":\"v\\n\","
                + "\"signature\":\"s\\/g\"}]}";
        GameProfile profile = SessionProfileParser.parse(body);
        assertEquals("N\u00f6tch\\ \"q\"", profile.name());
        assertEquals("te\nx\ttures", profile.properties().get(0).name());
        assertEquals("v\n", profile.properties().get(0).value());
        assertEquals("s/g", profile.properties().get(0).signature());
    }

    @Test
    void aTruncatedBodyIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":\"" + NOTCH_ID + "\",\"name\":"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":\"" + NOTCH_ID + "\""));
    }

    @Test
    void aWrongMemberTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":42,\"name\":\"Notch\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse(
                        "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"Notch\",\"properties\":\"x\"}"));
    }

    @Test
    void aBadIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":\"not-hex\",\"name\":\"Notch\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse(
                        "{\"id\":\"069a79f444e94726a5befca90e38aaf\",\"name\":\"Notch\"}"));
    }

    @Test
    void trailingContentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse(
                        "{\"id\":\"" + NOTCH_ID + "\",\"name\":\"Notch\"} trailing"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{}{}"));
    }

    @Test
    void aPropertyRowWithoutItsNameOrValueIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":\"" + NOTCH_ID + "\",\"name\":\"N\","
                        + "\"properties\":[{\"value\":\"v\"}]}"));
        assertThrows(IllegalArgumentException.class,
                () -> SessionProfileParser.parse("{\"id\":\"" + NOTCH_ID + "\",\"name\":\"N\","
                        + "\"properties\":[{\"name\":\"textures\"}]}"));
    }

    @Test
    void theUndashedIdLaw() {
        // 32 hex chars, both cases, to the dashed UUID form.
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                SessionProfileParser.dashedUuid(NOTCH_ID));
        assertEquals(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"),
                SessionProfileParser.dashedUuid(NOTCH_ID.toUpperCase()));
    }
}
