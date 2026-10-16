package net.zaminmc.torch.server.nbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The string NBT parser (Slice 75 — the port of the reference's
 * {@code nbt/SnbtParser.java} over the /summon NBT arm): one typed value
 * model and a recursive-descent reader of the same language — the
 * reference's string-scissors walk accepts exactly this grammar, and the
 * primitive typing is 1:1 (the suffix families, the true/false bytes, the
 * all-int {@code [1,2]} array, the quoted-string escapes, and the
 * fall-to-string shape for everything else).
 *
 * <p><b>The grammar (the reference's observable language):</b>
 * {@code {key:value, ...}} where a value is a nested compound, a list
 * {@code [v, v]}, an int array {@code [1, 2]}, or a primitive. The
 * primitive families: {@code 1.5d} double, {@code 1.5f} float,
 * {@code 1b} byte, {@code 1l} long, {@code 1s} short, {@code 1} int,
 * {@code 1.5} double, {@code true}/{@code false} byte 1/0, a quoted
 * string with {@code \"} escapes — and ANYTHING ELSE IS A STRING (the
 * reference's own fall-through, the {@code NumberFormatException} arm).</p>
 */
public final class Snbt {

    private Snbt() {
    }

    /** One typed value (the reference's NbtElement family, the engine's own shape). */
    public static final class Tag {

        /** The value's own family (the reference's NbtElement types). */
        public enum Type {
            BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, STRING,
            INT_ARRAY, LIST, COMPOUND
        }

        private final Type type;
        private final Object value;

        private Tag(Type type, Object value) {
            this.type = type;
            this.value = value;
        }

        static Tag ofByte(byte v) {
            return new Tag(Type.BYTE, v);
        }

        static Tag ofShort(short v) {
            return new Tag(Type.SHORT, v);
        }

        static Tag ofInt(int v) {
            return new Tag(Type.INT, v);
        }

        static Tag ofLong(long v) {
            return new Tag(Type.LONG, v);
        }

        static Tag ofFloat(float v) {
            return new Tag(Type.FLOAT, v);
        }

        static Tag ofDouble(double v) {
            return new Tag(Type.DOUBLE, v);
        }

        static Tag ofString(String v) {
            return new Tag(Type.STRING, v);
        }

        static Tag ofIntArray(int[] v) {
            return new Tag(Type.INT_ARRAY, v);
        }

        static Tag ofList(List<Tag> v) {
            return new Tag(Type.LIST, v);
        }

        static Tag ofCompound(Map<String, Tag> v) {
            return new Tag(Type.COMPOUND, v);
        }

        public Type type() {
            return type;
        }

        /** @return the string value, or null when the tag is not a string. */
        public String asString() {
            return type == Type.STRING ? (String) value : null;
        }

        /** @return the byte value, or 0 when the tag is not a byte. */
        public byte asByte() {
            return type == Type.BYTE ? (Byte) value : 0;
        }

        /** @return the numeric value widened to a double (0 for non-numbers). */
        public double asDouble() {
            return switch (type) {
                case BYTE -> (Byte) value;
                case SHORT -> (Short) value;
                case INT -> (Integer) value;
                case LONG -> (Long) value;
                case FLOAT -> (Float) value;
                case DOUBLE -> (Double) value;
                default -> 0.0;
            };
        }

        /** @return the numeric value as a float (0 for non-numbers). */
        public float asFloat() {
            return (float) asDouble();
        }

        /** @return the int-array rows, or null when the tag is not an int array. */
        public int[] asIntArray() {
            return type == Type.INT_ARRAY ? (int[]) value : null;
        }

        /** @return the list's tags, or null when the tag is not a list. */
        @SuppressWarnings("unchecked")
        public List<Tag> asList() {
            return type == Type.LIST ? (List<Tag>) value : null;
        }

        /** @return the compound's rows, or null when the tag is not a compound. */
        @SuppressWarnings("unchecked")
        public Map<String, Tag> asCompound() {
            return type == Type.COMPOUND ? (Map<String, Tag>) value : null;
        }

        @Override
        public String toString() {
            return type + ":" + value;
        }
    }

    /** The reference's PRIMITIVE_PATTERN (line 24): the all-int array form. */
    private static final Pattern PRIMITIVE_ARRAY = Pattern.compile("\\[[-+\\d|,\\s]+\\]");

    /**
     * Parses one compound tag (the reference's parse): the whole text is
     * the compound — no trailing rows.
     *
     * @throws IllegalArgumentException the NbtException port (the syntax
     *                                  errors' own messages)
     */
    public static Map<String, Tag> parseCompound(String text) {
        Objects.requireNonNull(text, "text");
        Cursor cursor = new Cursor(text);
        cursor.skipWhitespace();
        Map<String, Tag> compound = readCompound(cursor);
        cursor.skipWhitespace();
        if (!cursor.atEnd()) {
            throw error("Encountered multiple top tags, only one expected", cursor);
        }
        return compound;
    }

    // ------------------------------------------------------------------
    // The reader
    // ------------------------------------------------------------------

    private static final class Cursor {
        final String text;
        int pos;

        Cursor(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        char peek() {
            if (atEnd()) {
                throw error("Unexpected end of tag");
            }
            return text.charAt(pos);
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at: "
                    + text.substring(Math.min(pos, text.length())));
        }
    }

    private static IllegalArgumentException error(String message, Cursor cursor) {
        return cursor.error(message);
    }

    /** One compound: {@code {row, row, ...}} with the tolerated trailing comma. */
    private static Map<String, Tag> readCompound(Cursor cursor) {
        if (cursor.peek() != '{') {
            throw error("Invalid tag encountered, expected '{' as first char.", cursor);
        }
        cursor.pos++;
        Map<String, Tag> rows = new LinkedHashMap<>();
        cursor.skipWhitespace();
        if (!cursor.atEnd() && cursor.peek() == '}') {
            cursor.pos++;
            return rows;
        }
        while (true) {
            cursor.skipWhitespace();
            if (!cursor.atEnd() && cursor.peek() == '}') {
                // the tolerated empty row (a trailing comma's tail)
                cursor.pos++;
                return rows;
            }
            String key = readKey(cursor);
            cursor.skipWhitespace();
            if (cursor.atEnd() || cursor.peek() != ':') {
                throw error("Unable to locate name/value separator for string", cursor);
            }
            cursor.pos++;
            cursor.skipWhitespace();
            rows.put(key, readValue(cursor));
            cursor.skipWhitespace();
            if (cursor.atEnd()) {
                throw error("Unbalanced curly brackets", cursor);
            }
            char next = cursor.peek();
            cursor.pos++;
            if (next == ',') {
                continue;
            }
            if (next == '}') {
                return rows;
            }
            cursor.pos--;
            throw error("Unexpected token '" + next + "'", cursor);
        }
    }

    /** One key: a quoted string or the bare run to the separator. */
    private static String readKey(Cursor cursor) {
        if (!cursor.atEnd() && cursor.peek() == '"') {
            return readQuoted(cursor);
        }
        int start = cursor.pos;
        while (!cursor.atEnd()) {
            char c = cursor.text.charAt(cursor.pos);
            if (c == ':' || c == ',' || c == '}' || c == ']'
                    || Character.isWhitespace(c)) {
                break;
            }
            cursor.pos++;
        }
        return cursor.text.substring(start, cursor.pos).trim();
    }

    /** One value: the compound, the list, the int array, or the primitive. */
    private static Tag readValue(Cursor cursor) {
        char c = cursor.peek();
        if (c == '{') {
            return Tag.ofCompound(readCompound(cursor));
        }
        if (c == '[') {
            // The reference's own split (parseNbtEntry lines 115-117): a
            // bracket run matching the all-int pattern is a PRIMITIVE
            // (the int-array attempt), everything else is a list.
            int close = findMatchingClose(cursor);
            String bracketed = cursor.text.substring(cursor.pos, close + 1);
            if (PRIMITIVE_ARRAY.matcher(bracketed).matches()) {
                cursor.pos = close + 1;
                return readPrimitive(bracketed);
            }
            return readList(cursor);
        }
        return readPrimitive(readBareToken(cursor));
    }

    /** One list: {@code [value, value, ...]} with the tolerated trailing comma. */
    private static Tag readList(Cursor cursor) {
        cursor.pos++; // the '['
        List<Tag> values = new ArrayList<>();
        cursor.skipWhitespace();
        if (!cursor.atEnd() && cursor.peek() == ']') {
            cursor.pos++;
            return Tag.ofList(values);
        }
        while (true) {
            cursor.skipWhitespace();
            if (!cursor.atEnd() && cursor.peek() == ']') {
                // the tolerated empty element (a trailing comma's tail)
                cursor.pos++;
                return Tag.ofList(values);
            }
            values.add(readValue(cursor));
            cursor.skipWhitespace();
            if (cursor.atEnd()) {
                throw error("Unbalanced square brackets", cursor);
            }
            char next = cursor.peek();
            cursor.pos++;
            if (next == ',') {
                continue;
            }
            if (next == ']') {
                return Tag.ofList(values);
            }
            cursor.pos--;
            throw error("Unexpected token '" + next + "'", cursor);
        }
    }

    /** The bare token: the run to the top-level separator (quotes respected). */
    private static String readBareToken(Cursor cursor) {
        int start = cursor.pos;
        boolean quoted = false;
        while (!cursor.atEnd()) {
            char c = cursor.text.charAt(cursor.pos);
            if (c == '"' && (cursor.pos == start
                    || cursor.text.charAt(cursor.pos - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '}' || c == ']')) {
                break;
            }
            cursor.pos++;
        }
        return cursor.text.substring(start, cursor.pos).trim();
    }

    /** The quoted string with the reference's escape handling. */
    private static String readQuoted(Cursor cursor) {
        cursor.pos++; // the opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            if (cursor.atEnd()) {
                throw error("Unbalanced quotation", cursor);
            }
            char c = cursor.text.charAt(cursor.pos);
            if (c == '"') {
                cursor.pos++;
                return out.toString();
            }
            if (c == '\\' && cursor.pos + 1 < cursor.text.length()) {
                char next = cursor.text.charAt(cursor.pos + 1);
                if (next == '"' || next == '\\') {
                    out.append(next);
                    cursor.pos += 2;
                    continue;
                }
            }
            out.append(c);
            cursor.pos++;
        }
    }

    /**
     * The primitive typing (PrimitiveEntry.getValue lines 343-423, 1:1):
     * the suffix families in the reference's own order, then the
     * true/false bytes, then the int-array attempt, then the unquoted
     * string with the escape walk.
     */
    private static Tag readPrimitive(String raw) {
        // The number families (the reference's regex order).
        try {
            if (raw.length() > 1) {
                char suffix = raw.charAt(raw.length() - 1);
                String number = raw.substring(0, raw.length() - 1);
                switch (suffix) {
                    case 'd', 'D' -> {
                        if (isDecimal(number)) {
                            return Tag.ofDouble(Double.parseDouble(number));
                        }
                    }
                    case 'f', 'F' -> {
                        if (isDecimal(number)) {
                            return Tag.ofFloat(Float.parseFloat(number));
                        }
                    }
                    case 'b', 'B' -> {
                        if (isIntegral(number)) {
                            return Tag.ofByte(Byte.parseByte(number));
                        }
                    }
                    case 'l', 'L' -> {
                        if (isIntegral(number)) {
                            return Tag.ofLong(Long.parseLong(number));
                        }
                    }
                    case 's', 'S' -> {
                        if (isIntegral(number)) {
                            return Tag.ofShort(Short.parseShort(number));
                        }
                    }
                    default -> { }
                }
            }
            if (isIntegral(raw)) {
                return Tag.ofInt(Integer.parseInt(raw));
            }
            if (isDecimal(raw)) {
                return Tag.ofDouble(Double.parseDouble(raw));
            }
            if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
                return Tag.ofByte((byte) (Boolean.parseBoolean(raw) ? 1 : 0));
            }
        } catch (NumberFormatException outOfRange) {
            // The reference's own fall (line 372): an out-of-range number
            // is a STRING.
            return Tag.ofString(unescape(raw));
        }
        // The int array (lines 381-398): the bracket run split on commas;
        // a non-int element falls the WHOLE run to a string.
        if (raw.startsWith("[") && raw.endsWith("]")) {
            String inner = raw.substring(1, raw.length() - 1);
            String[] parts = inner.split(",");
            try {
                int[] rows = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    if (parts[i].isBlank()) {
                        throw new NumberFormatException("empty");
                    }
                    rows[i] = Integer.parseInt(parts[i].trim());
                }
                return Tag.ofIntArray(rows);
            } catch (NumberFormatException notInts) {
                return Tag.ofString(unescape(raw));
            }
        }
        return Tag.ofString(unescape(raw));
    }

    /** The reference's INT_PATTERN: {@code [-+]?[0-9]+}. */
    private static boolean isIntegral(String raw) {
        if (raw.isEmpty()) {
            return false;
        }
        int i = raw.charAt(0) == '-' || raw.charAt(0) == '+' ? 1 : 0;
        if (i >= raw.length()) {
            return false;
        }
        for (; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** The reference's DOUBLE_PATTERN: {@code [-+]?[0-9]*\.?[0-9]+}. */
    private static boolean isDecimal(String raw) {
        if (raw.isEmpty()) {
            return false;
        }
        int i = raw.charAt(0) == '-' || raw.charAt(0) == '+' ? 1 : 0;
        boolean seenDigit = false;
        boolean seenDot = false;
        for (; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '.') {
                if (seenDot) {
                    return false;
                }
                seenDot = true;
            } else if (Character.isDigit(c)) {
                seenDigit = true;
            } else {
                return false;
            }
        }
        return seenDigit;
    }

    /**
     * The string arm's escape walk (lines 404-420): the quoted form strips
     * its quotes, {@code \"} becomes {@code "}, then the double-backslash
     * pairs collapse to one.
     */
    private static String unescape(String raw) {
        String work = raw;
        if (work.startsWith("\"") && work.endsWith("\"") && work.length() >= 2) {
            work = work.substring(1, work.length() - 1);
        }
        work = work.replace("\\\"", "\"");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < work.length(); i++) {
            if (i < work.length() - 1 && work.charAt(i) == '\\'
                    && work.charAt(i + 1) == '\\') {
                out.append('\\');
                i++;
            } else {
                out.append(work.charAt(i));
            }
        }
        return out.toString();
    }

    /** The matching close bracket of the run at the cursor (quotes respected). */
    private static int findMatchingClose(Cursor cursor) {
        int depth = 0;
        boolean quoted = false;
        for (int i = cursor.pos; i < cursor.text.length(); i++) {
            char c = cursor.text.charAt(i);
            if (c == '"' && (i == 0 || cursor.text.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        throw error("Unbalanced square brackets", cursor);
    }
}
