package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.auth.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The hasJoined response parser (Wave ON-1b) — a strict, whole-document
 * JSON object reader for the session service's one fixed shape:
 *
 * <pre>{@code
 * {"id":"069a79f444e94726a5befca90e38aaf5","name":"Notch",
 *  "properties":[{"name":"textures","value":"<base64>","signature":"<base64>"}]}
 * }</pre>
 *
 * <p>The Mojang id is the UNDASHED 32-hex form (the reference
 * GameProfileSerializer's own convention on the wire); the properties
 * array is optional (absent for property-less profiles); the signature
 * member is optional per property (the textures row always carries it,
 * but the shape permits its absence — the engine's hasSignature law).
 * Anything else — a truncated document, a wrong member type, a bad id
 * hex, a stray trailing token — is an {@link IllegalArgumentException},
 * never a best-effort guess: the session service's answer is either the
 * whole profile or a failure, and a login is never accepted on a
 * half-parsed one.</p>
 */
final class SessionProfileParser {

    private SessionProfileParser() {
    }

    /** Parses the whole hasJoined body into the profile. */
    static GameProfile parse(String body) {
        Cursor cursor = new Cursor(body);
        cursor.skipWhitespace();
        cursor.expect('{');
        UUID id = null;
        String name = null;
        List<GameProfile.Property> properties = List.of();
        cursor.skipWhitespace();
        if (cursor.peek() != '}') {
            while (true) {
                cursor.skipWhitespace();
                String member = cursor.readString();
                cursor.skipWhitespace();
                cursor.expect(':');
                cursor.skipWhitespace();
                switch (member) {
                    case "id" -> id = dashedUuid(cursor.readString());
                    case "name" -> name = cursor.readString();
                    case "properties" -> properties = readProperties(cursor);
                    default -> skipValue(cursor); // unknown members are read whole and dropped
                }
                cursor.skipWhitespace();
                if (cursor.peek() == ',') {
                    cursor.advance();
                    continue;
                }
                break;
            }
        }
        cursor.skipWhitespace();
        cursor.expect('}');
        cursor.skipWhitespace();
        if (!cursor.atEnd()) {
            throw cursor.fail("trailing content after the profile object");
        }
        if (id == null) {
            throw cursor.fail("the profile carries no id");
        }
        if (name == null) {
            throw cursor.fail("the profile carries no name");
        }
        return new GameProfile(id, name, properties);
    }

    private static List<GameProfile.Property> readProperties(Cursor cursor) {
        cursor.expect('[');
        cursor.skipWhitespace();
        List<GameProfile.Property> properties = new ArrayList<>();
        if (cursor.peek() != ']') {
            while (true) {
                cursor.skipWhitespace();
                cursor.expect('{');
                String propName = null;
                String value = null;
                String signature = null;
                cursor.skipWhitespace();
                if (cursor.peek() != '}') {
                    while (true) {
                        cursor.skipWhitespace();
                        String member = cursor.readString();
                        cursor.skipWhitespace();
                        cursor.expect(':');
                        cursor.skipWhitespace();
                        switch (member) {
                            case "name" -> propName = cursor.readString();
                            case "value" -> value = cursor.readString();
                            case "signature" -> signature = cursor.readString();
                            default -> skipValue(cursor);
                        }
                        cursor.skipWhitespace();
                        if (cursor.peek() == ',') {
                            cursor.advance();
                            continue;
                        }
                        break;
                    }
                }
                cursor.skipWhitespace();
                cursor.expect('}');
                if (propName == null || value == null) {
                    throw cursor.fail("a property row without its name or value");
                }
                properties.add(new GameProfile.Property(propName, value, signature));
                cursor.skipWhitespace();
                if (cursor.peek() == ',') {
                    cursor.advance();
                    continue;
                }
                break;
            }
        }
        cursor.skipWhitespace();
        cursor.expect(']');
        return properties;
    }

    /** The undashed 32-hex id to the dashed UUID form. */
    static UUID dashedUuid(String undashed) {
        if (undashed.length() != 32 || !undashed.matches("[0-9a-fA-F]{32}")) {
            throw new IllegalArgumentException(
                    "the profile id is not the 32-hex undashed form: " + undashed);
        }
        return UUID.fromString(undashed.substring(0, 8) + "-" + undashed.substring(8, 12)
                + "-" + undashed.substring(12, 16) + "-" + undashed.substring(16, 20)
                + "-" + undashed.substring(20, 32));
    }

    private static void skipValue(Cursor cursor) {
        char c = cursor.peek();
        if (c == '"') {
            cursor.readString();
        } else if (c == '{' || c == '[') {
            char open = c;
            char close = open == '{' ? '}' : ']';
            int depth = 0;
            while (!cursor.atEnd()) {
                char current = cursor.advance();
                if (current == '"') {
                    cursor.readStringTail(); // the opening quote is consumed; read to the closing one
                } else if (current == open) {
                    depth++;
                } else if (current == close) {
                    depth--;
                    if (depth == 0) {
                        return;
                    }
                }
            }
        } else {
            while (!cursor.atEnd() && cursor.peek() != ',' && cursor.peek() != '}'
                    && cursor.peek() != ']') {
                cursor.advance();
            }
        }
    }

    /** The cursor over the body — one position, the whole grammar on top. */
    private static final class Cursor {
        private final String body;
        private int position;

        Cursor(String body) {
            this.body = body;
        }

        char peek() {
            if (atEnd()) {
                throw fail("unexpected end of the profile body");
            }
            return body.charAt(position);
        }

        char advance() {
            char c = peek();
            position++;
            return c;
        }

        boolean atEnd() {
            return position >= body.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(body.charAt(position))) {
                position++;
            }
        }

        void expect(char expected) {
            if (atEnd() || body.charAt(position) != expected) {
                throw fail("expected '" + expected + "' at position " + position);
            }
            position++;
        }

        String readString() {
            expect('"');
            return readStringTail();
        }

        /**
         * Reads a string's tail — the opening quote already consumed
         * (the skipValue walk's own need): reads to the closing quote,
         * the escape grammar whole.
         */
        String readStringTail() {
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw fail("unterminated string");
                }
                char c = body.charAt(position++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw fail("unterminated escape");
                    }
                    char escape = body.charAt(position++);
                    switch (escape) {
                        case '"' -> out.append('"');
                        case '\\' -> out.append('\\');
                        case '/' -> out.append('/');
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (position + 4 > body.length()) {
                                throw fail("truncated unicode escape");
                            }
                            String hex = body.substring(position, position + 4);
                            try {
                                out.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException e) {
                                throw fail("bad unicode escape \\u" + hex);
                            }
                            position += 4;
                        }
                        default -> throw fail("bad escape '\\" + escape + "'");
                    }
                } else {
                    out.append(c);
                }
            }
        }

        IllegalArgumentException fail(String message) {
            return new IllegalArgumentException(
                    message + " (at position " + position + " of: " + body + ")");
        }
    }
}
