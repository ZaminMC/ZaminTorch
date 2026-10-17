package net.zaminmc.torch.server.text;

import java.util.ArrayList;
import java.util.List;

/**
 * The dependency-free JSON walk behind the 1.8.8 text component model (the
 * reference rides GSON — {@code Text.Serializer}'s registered tree model).
 *
 * <p>The engine carries no JSON dependency (the OpStore/Snbt convention), so
 * this is the GSON <em>tree</em> semantics ported over a small sealed AST:
 * objects keep insertion order and overwrite duplicate keys (GSON's
 * {@code JsonObject.add}), numbers keep their raw text so a round trip never
 * rewrites the caller's literal, and the writer emits GSON's default compact
 * form (no spaces, minimal string escapes, the JS separators \u2028/\u2029
 * always escaped).</p>
 *
 * <p>Parse failures throw {@link JsonFormatException} — the engine's stand-in
 * for GSON's {@code JsonParseException} (the /title and /tellraw arms catch
 * exactly this type to build the reference's "Invalid json" command error).</p>
 */
public final class Json {

    private Json() {
    }

    /** The GSON {@code JsonParseException} stand-in. */
    public static final class JsonFormatException extends RuntimeException {
        public JsonFormatException(String message) {
            super(message);
        }

        public JsonFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** One object member. */
    public record Member(String key, Value value) {
    }

    /** The tree: GSON's JsonElement family as one sealed type. */
    public sealed interface Value permits Obj, Arr, Str, Num, Bool, NullV {
    }

    /** GSON JsonObject: insertion-ordered, duplicate keys overwrite. */
    public record Obj(List<Member> members) implements Value {
        public static Obj obj() {
            return new Obj(new ArrayList<>());
        }

        public boolean has(String key) {
            return member(key) != null;
        }

        Member member(String key) {
            for (Member m : members) {
                if (m.key().equals(key)) {
                    return m;
                }
            }
            return null;
        }

        public Value get(String key) {
            Member m = member(key);
            return m == null ? null : m.value();
        }

        /** GSON's getAsJsonObject(String): the member cast, null when absent. */
        public Obj getObj(String key) {
            Value v = get(key);
            return v instanceof Obj o ? o : null;
        }

        /** GSON's getAsJsonArray(String): the member cast, null when absent. */
        public Arr getArr(String key) {
            Value v = get(key);
            return v instanceof Arr a ? a : null;
        }

        /** GSON's getAsJsonPrimitive(String): the member cast, null when absent. */
        public Value getPrim(String key) {
            Value v = get(key);
            return (v instanceof Str || v instanceof Num || v instanceof Bool) ? v : null;
        }

        /** GSON's add(key, element) — a duplicate key overwrites (last wins). */
        public void add(String key, Value value) {
            Member existing = member(key);
            if (existing != null) {
                members.set(members.indexOf(existing), new Member(key, value));
            } else {
                members.add(new Member(key, value));
            }
        }

        public void addString(String key, String text) {
            add(key, new Str(text));
        }

        public void addBool(String key, boolean value) {
            add(key, value ? Bool.TRUE : Bool.FALSE);
        }
    }

    /** GSON JsonArray. */
    public record Arr(List<Value> values) implements Value {
        public Arr() {
            this(new ArrayList<>());
        }

        public int size() {
            return values.size();
        }

        public Value get(int index) {
            return values.get(index);
        }

        public void add(Value value) {
            values.add(value);
        }
    }

    /** GSON JsonPrimitive(string). */
    public record Str(String value) implements Value {
        public String getAsString() {
            return value;
        }
    }

    /** GSON JsonPrimitive(number) — the raw literal is kept verbatim. */
    public record Num(String raw) implements Value {
        public String getAsString() {
            return raw;
        }

        public int getAsInt() {
            return Integer.parseInt(raw);
        }

        public long getAsLong() {
            return Long.parseLong(raw);
        }

        public double getAsDouble() {
            return Double.parseDouble(raw);
        }
    }

    /** GSON JsonPrimitive(boolean). */
    public record Bool(boolean value) implements Value {
        public static final Bool TRUE = new Bool(true);
        public static final Bool FALSE = new Bool(false);

        public boolean getAsBoolean() {
            return value;
        }
    }

    /** GSON JsonNull. */
    public record NullV() implements Value {
        public static final NullV NULL = new NullV();
    }

    // ------------------------------------------------------------------ parse

    /** Parses one JSON document; trailing content is an error (GSON's own). */
    public static Value parse(String text) {
        Reader r = new Reader(text);
        r.skipWhitespace();
        if (r.atEnd()) {
            throw new JsonFormatException("An empty JSON input");
        }
        Value v = r.value();
        r.skipWhitespace();
        if (!r.atEnd()) {
            throw new JsonFormatException("JSON document must end at the last value");
        }
        return v;
    }

    private static final class Reader {
        private final String s;
        private int i;

        Reader(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        char peek() {
            if (atEnd()) {
                throw new JsonFormatException("Unexpected end of JSON input");
            }
            return s.charAt(i);
        }

        void expect(char c) {
            if (atEnd() || s.charAt(i) != c) {
                throw new JsonFormatException(
                        "Expected '" + c + "' but was '" + (atEnd() ? "<end>" : s.charAt(i)) + "'");
            }
            i++;
        }

        boolean tryConsume(char c) {
            if (!atEnd() && s.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        Value value() {
            char c = peek();
            return switch (c) {
                case '{' -> {
                    i++;
                    skipWhitespace();
                    Obj o = Obj.obj();
                    if (tryConsume('}')) {
                        yield o;
                    }
                    while (true) {
                        String key = string();
                        skipWhitespace();
                        expect(':');
                        skipWhitespace();
                        o.add(key, value());
                        skipWhitespace();
                        if (tryConsume(',')) {
                            skipWhitespace();
                        } else {
                            expect('}');
                            yield o;
                        }
                    }
                }
                case '[' -> {
                    i++;
                    skipWhitespace();
                    Arr a = new Arr();
                    if (tryConsume(']')) {
                        yield a;
                    }
                    while (true) {
                        a.add(value());
                        skipWhitespace();
                        if (tryConsume(',')) {
                            skipWhitespace();
                        } else {
                            expect(']');
                            yield a;
                        }
                    }
                }
                case '"' -> new Str(string());
                case 't' -> {
                    literal("true");
                    yield (Value) Bool.TRUE;
                }
                case 'f' -> {
                    literal("false");
                    yield (Value) Bool.FALSE;
                }
                case 'n' -> {
                    literal("null");
                    yield (Value) NullV.NULL;
                }
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield number();
                    }
                    throw new JsonFormatException("Don't know how to read '" + c + "' as a JSON value");
                }
            };
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonFormatException("Unterminated string");
                }
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonFormatException("Unterminated escape");
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (i + 4 > s.length()) {
                                throw new JsonFormatException("Invalid \\u escape");
                            }
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new JsonFormatException("Invalid escape '\\" + e + "'");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Num number() {
            int start = i;
            if (!atEnd() && s.charAt(i) == '-') {
                i++;
            }
            if (atEnd() || !Character.isDigit(s.charAt(i))) {
                throw new JsonFormatException("Expected a number");
            }
            while (!atEnd() && Character.isDigit(s.charAt(i))) {
                i++;
            }
            if (!atEnd() && s.charAt(i) == '.') {
                i++;
                if (atEnd() || !Character.isDigit(s.charAt(i))) {
                    throw new JsonFormatException("Expected a digit after '.'");
                }
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            if (!atEnd() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
                i++;
                if (!atEnd() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                    i++;
                }
                if (atEnd() || !Character.isDigit(s.charAt(i))) {
                    throw new JsonFormatException("Expected a digit in the exponent");
                }
                while (!atEnd() && Character.isDigit(s.charAt(i))) {
                    i++;
                }
            }
            return new Num(s.substring(start, i));
        }

        void literal(String word) {
            if (!s.startsWith(word, i)) {
                throw new JsonFormatException("Expected '" + word + "'");
            }
            i += word.length();
        }
    }

    // ------------------------------------------------------------------ write

    /** Writes the compact GSON-default form. */
    public static String write(Value v) {
        StringBuilder sb = new StringBuilder();
        writeValue(v, sb);
        return sb.toString();
    }

    private static void writeValue(Value v, StringBuilder sb) {
        if (v == null || v instanceof NullV) {
            sb.append("null");
        } else if (v instanceof Obj o) {
            sb.append('{');
            for (int i = 0; i < o.members().size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeString(o.members().get(i).key(), sb);
                sb.append(':');
                writeValue(o.members().get(i).value(), sb);
            }
            sb.append('}');
        } else if (v instanceof Arr a) {
            sb.append('[');
            for (int i = 0; i < a.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(a.get(i), sb);
            }
            sb.append(']');
        } else if (v instanceof Str s) {
            writeString(s.value(), sb);
        } else if (v instanceof Num n) {
            sb.append(n.raw());
        } else if (v instanceof Bool b) {
            sb.append(b.value() ? "true" : "false");
        }
    }

    static void writeString(String text, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
